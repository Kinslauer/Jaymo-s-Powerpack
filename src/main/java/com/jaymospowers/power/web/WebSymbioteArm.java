package com.jaymospowers.power.web;

import com.jaymospowers.JaymosPowers;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.Containers;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.cubemob.AbstractCubeMob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * What the arm does without being asked (patch 4 of the 1.1.3 picks). It works for a bonded host whose arm is open
 * ({@link WebSymbioteBody#armUnlocked}) and who isn't holding it back (the Hide order, {@link WebSymbioteMind#hiding}).
 * <ul>
 * <li><b>The arm fights</b> ({@link #fight}): from the Cooperative stage, while you are in a fight, a weapon stored in
 * the arm swings at a foe within {@link #FIGHT_REACH} every {@link #SWING_EVERY} ticks, at that weapon's damage (its
 * enchantments too), wearing it down.</li>
 * <li><b>It helps itself</b>: when you have left it hungry for {@link #FEED_WAIT} ticks it takes meat from your bag
 * ({@link #feedItself}); it swats silverfish, endermites and the smallest slimes that come close ({@link #swat}); and it
 * digs an exposed ore within {@link #ORE_RANGE} if the arm holds a tool that would win it ({@link #dig}).</li>
 * <li><b>It keeps a good tool</b> ({@link #keep}): now and then it takes a fine tool or weapon out of your bag into a free
 * slot of the arm, and gives it back once its trust has risen {@link #KEEP_RISE} (and at least {@link #KEEP_MIN} ticks
 * have passed). Taking it back before then ({@link #pried}, from the Arm key and the Arm screen) angers it: a
 * {@link WebSymbioteMemory#PRIED} memory, trust and calm lost.</li>
 * <li><b>A torch for your eyes</b> ({@link #torch}): in the dark, with torches in your bag, it sets one down.</li>
 * </ul>
 * Its lines respect the Settings screen: {@link WebSymbioteSettings.Setting#FIDGETS} off keeps it quiet about the small
 * things it does.
 */
public final class WebSymbioteArm {
	// ---- tuning: the fight ----
	/** The stage (index into {@link WebSymbioteMind#STAGE_AT}) the arm starts fighting at: Cooperative. */
	public static final int FIGHT_STAGE = 2;
	/** How far from you a foe can be and still get the arm's blow (blocks), the ticks between blows, and how long after your last blow or hurt you count as fighting. */
	static final double FIGHT_REACH = 3.6;
	static final int SWING_EVERY = 14, FIGHT_WINDOW = 160;
	/** A stored thing is a weapon if its attack damage (a fist is 1) is at least this. */
	static final double WEAPON_DAMAGE = 3.0;
	/** Foes counted around you when the fight is judged (blocks). */
	static final double FOE_RANGE = 8.0;

	// ---- tuning: helping itself ----
	/** The stage helping itself begins at (Integrated), and how often it looks (ticks). */
	static final int HELP_STAGE = 1, LOOK_EVERY = 20;
	/** Satiety under this for this many ticks and it takes its own meal; then not again for {@link #FEED_AGAIN}. */
	static final int FEED_BELOW = 30, FEED_WAIT = 1200, FEED_AGAIN = 2400;
	/** Pests within this many blocks are swatted (at most {@link #SWAT_MAX} a look), for this much damage. */
	static final double SWAT_RANGE = 4.0;
	static final int SWAT_MAX = 2;
	static final float SWAT_DAMAGE = 6.0F;
	/** Ore it spots: this far (blocks, across and up and down), a look this often (ticks), at most this long a wait after each. */
	static final int ORE_RANGE = 5, ORE_UP = 3, ORE_EVERY = 3;
	static final int ORE_GAP = 100;

	// ---- tuning: keeping a good tool ----
	/** It looks this often (ticks) and takes with this chance; the tool must be enchanted or last at least this long. */
	static final int KEEP_EVERY = 400;
	static final float KEEP_CHANCE = 0.3F;
	static final int FINE_DURABILITY = 1500;
	/** It gives the tool back after trust has risen this much (to at most full) and this long has passed (ticks). */
	static final int KEEP_RISE = 6, KEEP_MIN = 3600;
	/** Prying it out costs this much trust and calm. */
	static final int PRY_TRUST = 4, PRY_STRESS = 10;
	/** How long its anger shows on the screen's edge (ticks). */
	static final int PRY_FLARE = 160;

	// ---- tuning: the torch ----
	/** Dark enough for a torch (light where you stand), the ticks between torches, and how far it looks for a spot (blocks). */
	static final int TORCH_LIGHT = 3, TORCH_GAP = 200, TORCH_REACH = 2;

	static final int LINES = 3;

	/** The good tool it is keeping: which arm slot, the trust it was taken at, and when. Saved, kept through death. */
	public record Kept(int slot, int trust, long at) {
		public static final Codec<Kept> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.INT.fieldOf("slot").forGetter(Kept::slot),
			Codec.INT.fieldOf("trust").forGetter(Kept::trust),
			Codec.LONG.fieldOf("at").forGetter(Kept::at)).apply(i, Kept::new));
	}

	public static final AttachmentType<Kept> KEPT = AttachmentRegistry.create(JaymosPowers.id("web_symbiote_kept"),
		builder -> builder.persistent(Kept.CODEC).copyOnDeath());

	private static final Map<UUID, Long> NEXT_SWING = new HashMap<>();
	private static final Map<UUID, Integer> HUNGRY_FOR = new HashMap<>();
	private static final Map<UUID, Long> FED_AT = new HashMap<>();
	private static final Map<UUID, Long> ORE_AT = new HashMap<>();
	private static final Map<UUID, Long> TORCH_AT = new HashMap<>();
	private static final Map<UUID, Long> LAST_LINE = new HashMap<>();

	private WebSymbioteArm() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				tick(player);
			}
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			UUID id = handler.player.getUUID();
			NEXT_SWING.remove(id);
			HUNGRY_FOR.remove(id);
			FED_AT.remove(id);
			ORE_AT.remove(id);
			TORCH_AT.remove(id);
			LAST_LINE.remove(id);
		});
	}

	private static void tick(ServerPlayer player) {
		WebSymbioteState state = WebSymbiote.state(player);
		if (!state.bonded() || !WebSymbioteBody.armUnlocked(player) || !WebSymbioteBody.ready(player) || WebSymbioteMind.hiding(player)) {
			return;
		}
		long now = player.level().getGameTime();
		int stage = WebSymbioteMind.stage(state.bond());
		if (stage >= FIGHT_STAGE) {
			fight(player, now);
		}
		if (player.tickCount % LOOK_EVERY != 0 || stage < HELP_STAGE) {
			return;
		}
		feedItself(player, state, now);
		swat(player);
		torch(player, now);
		keep(player, state, now);
		if ((now / LOOK_EVERY) % ORE_EVERY == 0) {
			dig(player, now);
		}
	}

	/** A line from {@code base.1..LINES}, unless the Settings screen has switched the small talk off (and never twice within a few seconds). */
	private static void say(ServerPlayer player, String base) {
		if (WebSymbioteSettings.off(player, WebSymbioteSettings.Setting.FIDGETS) || !WebSymbioteBody.due(LAST_LINE, player, 100)) {
			return;
		}
		WebSymbiote.whisper(player, base + "." + (1 + player.getRandom().nextInt(LINES)));
	}

	private static void out(ServerPlayer player, long now) {
		player.setAttached(WebSymbiote.TENDRILS_OUT, now + WebSymbioteBody.ARM_OUT_TICKS);
	}

	// ---- the arm fights -----------------------------------------------------------------------------------------------

	/** A stored thing swings like a weapon. */
	static boolean weapon(ItemStack stack) {
		if (stack.isEmpty()) {
			return false;
		}
		return stack.has(DataComponents.WEAPON) || damage(stack) >= WEAPON_DAMAGE;
	}

	/** The attack damage a stack gives in a hand (a bare fist is 1). */
	private static float damage(ItemStack stack) {
		ItemAttributeModifiers modifiers = stack.get(DataComponents.ATTRIBUTE_MODIFIERS);
		return modifiers == null ? 1.0F : (float)modifiers.compute(Attributes.ATTACK_DAMAGE, 1.0, EquipmentSlot.MAINHAND);
	}

	/** Enemies that are on you or that you have been hitting (within {@link #FOE_RANGE}). */
	static List<LivingEntity> foes(ServerPlayer player, double range) {
		LivingEntity mine = player.getLastHurtMob();
		return player.level().getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(range), e -> e != player && e.isAlive()
			&& e instanceof Enemy && !(e instanceof net.minecraft.world.entity.player.Player)
			&& (e == mine || e instanceof Mob mob && mob.getTarget() == player));
	}

	private static boolean fighting(ServerPlayer player) {
		int now = player.tickCount;
		return now - player.getLastHurtMobTimestamp() < FIGHT_WINDOW || now - player.getLastHurtByMobTimestamp() < FIGHT_WINDOW;
	}

	private static void fight(ServerPlayer player, long now) {
		Long next = NEXT_SWING.get(player.getUUID());
		if (next != null && now < next) {
			return;
		}
		WebSymbioteBody.Arm arm = WebSymbioteBody.arm(player);
		int slot = -1;
		for (int i = 0; i < WebSymbioteBody.ARM_SLOTS; i++) {
			int at = (arm.selected() + i) % WebSymbioteBody.ARM_SLOTS;
			if (weapon(arm.get(at)) && !(arm.using() && at == arm.selected())) {
				slot = at;
				break;
			}
		}
		if (slot < 0 || !fighting(player)) {
			return;
		}
		LivingEntity target = null;
		double best = FIGHT_REACH * FIGHT_REACH;
		for (LivingEntity foe : foes(player, FIGHT_REACH + 1.0)) {
			double d = foe.distanceToSqr(player);
			if (d < best) {
				best = d;
				target = foe;
			}
		}
		if (target == null) {
			return;
		}
		ServerLevel level = player.level();
		NEXT_SWING.put(player.getUUID(), now + SWING_EVERY);
		ItemStack stack = arm.get(slot).copy();
		DamageSource source = level.damageSources().playerAttack(player);
		float amount = EnchantmentHelper.modifyDamage(level, stack, target, source, damage(stack));
		target.hurtServer(level, source, amount);
		Vec3 away = target.position().subtract(player.position()).multiply(1.0, 0.0, 1.0);
		if (away.lengthSqr() > 1.0E-4) {
			away = away.normalize().scale(0.25);
			target.push(away.x, 0.08, away.z);
		}
		stack.hurtAndBreak(1, level, player, broken -> level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ITEM_BREAK.value(),
			SoundSource.PLAYERS, 0.8F, 0.9F));
		WebSymbioteBody.Arm current = WebSymbioteBody.arm(player);
		WebSymbioteBody.setArm(player, current.with(slot, stack));
		out(player, now);
		level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.6F, 1.3F);
		level.sendParticles(ParticleTypes.SWEEP_ATTACK, target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(), 1, 0.0, 0.0, 0.0, 0.0);
	}

	// ---- it helps itself ----------------------------------------------------------------------------------------------

	/** Hungry too long: it takes meat from your bag (raw first). */
	private static void feedItself(ServerPlayer player, WebSymbioteState state, long now) {
		UUID id = player.getUUID();
		if (state.satiety() >= FEED_BELOW) {
			HUNGRY_FOR.remove(id);
			return;
		}
		int hungry = HUNGRY_FOR.merge(id, LOOK_EVERY, Integer::sum);
		Long fed = FED_AT.get(id);
		if (hungry < FEED_WAIT || fed != null && now - fed < FEED_AGAIN) {
			return;
		}
		var inventory = player.getInventory();
		int pick = -1;
		for (int pass = 0; pass < 2 && pick < 0; pass++) {
			for (int i = 0; i < inventory.getContainerSize(); i++) {
				ItemStack stack = inventory.getItem(i);
				boolean raw = WebSymbiote.RAW_MEAT.contains(stack.getItem());
				boolean cooked = WebSymbioteBody.COOKED_MEAT.contains(stack.getItem());
				if ((pass == 0 ? raw : cooked || WebSymbioteBody.isMeat(stack) && !raw) && !WebSymbioteBody.refuses(player, stack)) {
					pick = i;
					break;
				}
			}
		}
		if (pick < 0) {
			return;
		}
		FED_AT.put(id, now);
		HUNGRY_FOR.remove(id);
		if (WebSymbioteBody.eatMeat(player, inventory.getItem(pick), null)) {
			inventory.setChanged();
			WebSymbiote.whisper(player, "help.feed." + (1 + player.getRandom().nextInt(LINES)));
		}
	}

	/** Silverfish, endermites and the smallest slimes that come close. */
	private static boolean pest(LivingEntity entity) {
		return entity instanceof net.minecraft.world.entity.monster.Silverfish || entity instanceof net.minecraft.world.entity.monster.Endermite
			|| entity instanceof AbstractCubeMob cube && cube.getSize() <= 1;
	}

	private static void swat(ServerPlayer player) {
		ServerLevel level = player.level();
		int swatted = 0;
		for (LivingEntity pest : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(SWAT_RANGE), e -> e.isAlive() && pest(e))) {
			if (swatted >= SWAT_MAX) {
				break;
			}
			pest.hurtServer(level, level.damageSources().playerAttack(player), SWAT_DAMAGE);
			level.sendParticles(WebSymbiote.dust(0.7F), pest.getX(), pest.getY() + 0.2, pest.getZ(), 6, 0.1, 0.1, 0.1, 0.0);
			level.playSound(null, pest.getX(), pest.getY(), pest.getZ(), SoundEvents.SLIME_SQUISH_SMALL, SoundSource.PLAYERS, 0.5F, 1.6F);
			out(player, level.getGameTime());
			swatted++;
		}
		if (swatted > 0) {
			say(player, "help.swat");
		}
	}

	/** An exposed ore it can see, broken by a tool in the arm that is the right one for it. */
	private static void dig(ServerPlayer player, long now) {
		Long last = ORE_AT.get(player.getUUID());
		if (last != null && now - last < ORE_GAP || fighting(player)) {
			return;
		}
		WebSymbioteBody.Arm arm = WebSymbioteBody.arm(player);
		ServerLevel level = player.level();
		BlockPos base = player.blockPosition();
		Vec3 eye = player.getEyePosition();
		BlockPos found = null;
		int slot = -1;
		double best = Double.MAX_VALUE;
		for (BlockPos pos : BlockPos.betweenClosed(base.offset(-ORE_RANGE, -ORE_UP, -ORE_RANGE), base.offset(ORE_RANGE, ORE_UP, ORE_RANGE))) {
			double d = pos.distToCenterSqr(eye);
			if (d >= best) {
				continue;
			}
			BlockState state = level.getBlockState(pos);
			if (!state.is(BlockTags.ORES)) {
				continue;
			}
			int tool = toolFor(arm, state);
			if (tool < 0 || !WebSymbioteChests.allowed(level, player, pos)) {
				continue;
			}
			if (!seen(level, player, eye, pos)) {
				continue;
			}
			best = d;
			found = pos.immutable();
			slot = tool;
		}
		if (found == null) {
			return;
		}
		ORE_AT.put(player.getUUID(), now);
		BlockState state = level.getBlockState(found);
		ItemStack tool = arm.get(slot);
		BlockPos at = found;
		boolean[] broke = new boolean[1];
		ItemStack left = WebSymbioteBody.asHeld(player, tool, held -> broke[0] = player.gameMode.destroyBlock(at));
		WebSymbioteBody.Arm current = WebSymbioteBody.arm(player);
		WebSymbioteBody.setArm(player, current.with(slot, left));
		if (broke[0]) {
			player.causeFoodExhaustion(WebSymbioteBody.BREAK_EXHAUSTION);
			out(player, now);
			level.playSound(null, found, state.getSoundType().getBreakSound(), SoundSource.BLOCKS, 0.7F, 0.8F);
			say(player, "help.ore");
		}
	}

	/** The arm slot holding a tool that wins this block, or -1. */
	private static int toolFor(WebSymbioteBody.Arm arm, BlockState state) {
		for (int i = 0; i < WebSymbioteBody.ARM_SLOTS; i++) {
			ItemStack stack = arm.get(i);
			if (stack.has(DataComponents.TOOL) && stack.isCorrectToolForDrops(state) && !(arm.using() && i == arm.selected())) {
				return i;
			}
		}
		return -1;
	}

	/** Whether a block can be seen from the eyes: its own face is the first thing a look at it hits. */
	private static boolean seen(ServerLevel level, ServerPlayer player, Vec3 eye, BlockPos pos) {
		var hit = level.clip(new ClipContext(eye, Vec3.atCenterOf(pos), ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
		return hit.getType() == HitResult.Type.MISS || hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(pos);
	}

	// ---- it keeps a good tool -----------------------------------------------------------------------------------------

	/** A fine tool or weapon: enchanted, or long-lasting. */
	private static boolean fine(ItemStack stack) {
		return !stack.isEmpty() && (stack.has(DataComponents.TOOL) || weapon(stack)) && stack.isDamageableItem()
			&& (stack.isEnchanted() || stack.getMaxDamage() >= FINE_DURABILITY);
	}

	private static void keep(ServerPlayer player, WebSymbioteState state, long now) {
		Kept kept = player.getAttached(KEPT);
		WebSymbioteBody.Arm arm = WebSymbioteBody.arm(player);
		if (kept != null) {
			if (arm.get(kept.slot()).isEmpty()) {
				// worn out (or gone another way): nothing left to give back
				player.removeAttached(KEPT);
			} else if (now - kept.at() >= KEEP_MIN && WebSymbioteMind.mind(player).trust() >= Math.min(WebSymbioteMind.TRUST_MAX, kept.trust() + KEEP_RISE)) {
				giveBack(player, kept, arm);
			}
			return;
		}
		if (player.tickCount % KEEP_EVERY != 0 || player.getRandom().nextFloat() >= KEEP_CHANCE) {
			return;
		}
		int free = -1;
		for (int i = 0; i < WebSymbioteBody.ARM_SLOTS && free < 0; i++) {
			if (arm.get(i).isEmpty()) {
				free = i;
			}
		}
		if (free < 0) {
			return;
		}
		var inventory = player.getInventory();
		List<Integer> picks = new ArrayList<>();
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			if (i != inventory.getSelectedSlot() && fine(inventory.getItem(i))) {
				picks.add(i);
			}
		}
		if (picks.isEmpty()) {
			return;
		}
		int from = picks.get(player.getRandom().nextInt(picks.size()));
		ItemStack taken = inventory.getItem(from).copy();
		inventory.setItem(from, ItemStack.EMPTY);
		WebSymbioteBody.setArm(player, arm.with(free, taken));
		player.setAttached(KEPT, new Kept(free, WebSymbioteMind.mind(player).trust(), now));
		out(player, now);
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SLIME_SQUISH, SoundSource.PLAYERS, 0.7F, 0.8F);
		WebSymbiote.whisper(player, "keep.take." + (1 + player.getRandom().nextInt(LINES)), taken.getHoverName());
	}

	private static void giveBack(ServerPlayer player, Kept kept, WebSymbioteBody.Arm arm) {
		ItemStack stack = arm.get(kept.slot()).copy();
		player.removeAttached(KEPT);
		WebSymbioteBody.setArm(player, arm.with(kept.slot(), ItemStack.EMPTY));
		if (!player.getInventory().add(stack) && !stack.isEmpty()) {
			Containers.dropItemStack(player.level(), player.getX(), player.getY(), player.getZ(), stack);
		}
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SLIME_SQUISH_SMALL, SoundSource.PLAYERS, 0.6F, 1.3F);
		WebSymbiote.whisper(player, "keep.back." + (1 + player.getRandom().nextInt(LINES)), stack.getHoverName());
	}

	/** It is keeping a thing in this slot (the chests' thief marks its find the same way). */
	static void keeping(ServerPlayer player, int slot) {
		player.setAttached(KEPT, new Kept(slot, WebSymbioteMind.mind(player).trust(), player.level().getGameTime()));
	}

	/**
	 * What is in this arm slot is being taken out (the Arm key, the Arm screen): if it is the thing the symbiote is keeping,
	 * that is prying it out of its grip. It remembers ({@link WebSymbioteMemory#PRIED}), trusts you less, is shaken, and
	 * its edge flares.
	 */
	static void pried(ServerPlayer player, int slot) {
		Kept kept = player.getAttached(KEPT);
		if (kept == null || kept.slot() != slot || WebSymbioteBody.arm(player).get(slot).isEmpty()) {
			return;
		}
		player.removeAttached(KEPT);
		WebSymbioteMemory.remember(player, WebSymbioteMemory.PRIED);
		WebSymbioteMind.addTrust(player, -PRY_TRUST);
		WebSymbioteMind.addStress(player, PRY_STRESS);
		WebSymbioteMind.flare(player, player.level().getGameTime() + PRY_FLARE);
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_HURT, SoundSource.PLAYERS, 0.6F, 1.5F);
		WebSymbiote.whisper(player, "keep.pried." + (1 + player.getRandom().nextInt(LINES)));
	}

	// ---- a torch for your eyes ----------------------------------------------------------------------------------------

	private static void torch(ServerPlayer player, long now) {
		ServerLevel level = player.level();
		BlockPos feet = player.blockPosition();
		Long last = TORCH_AT.get(player.getUUID());
		if (last != null && now - last < TORCH_GAP || player.isSleeping() || player.isInWater() || level.getMaxLocalRawBrightness(feet) > TORCH_LIGHT) {
			return;
		}
		var inventory = player.getInventory();
		int slot = -1;
		for (int i = 0; i < inventory.getContainerSize() && slot < 0; i++) {
			ItemStack stack = inventory.getItem(i);
			if (stack.is(Items.TORCH) || stack.is(Items.SOUL_TORCH)) {
				slot = i;
			}
		}
		if (slot < 0) {
			return;
		}
		ItemStack stack = inventory.getItem(slot);
		BlockState torch = (stack.is(Items.SOUL_TORCH) ? Blocks.SOUL_TORCH : Blocks.TORCH).defaultBlockState();
		BlockPos spot = null;
		double best = Double.MAX_VALUE;
		for (BlockPos pos : BlockPos.betweenClosed(feet.offset(-TORCH_REACH, 0, -TORCH_REACH), feet.offset(TORCH_REACH, 1, TORCH_REACH))) {
			double d = pos.distSqr(feet);
			if (d < best && !pos.equals(feet) && !pos.equals(feet.above()) && level.getBlockState(pos).isAir() && torch.canSurvive(level, pos)
				&& WebSymbioteBody.mayWork(player, pos)) {
				best = d;
				spot = pos.immutable();
			}
		}
		if (spot == null) {
			return;
		}
		TORCH_AT.put(player.getUUID(), now);
		level.setBlock(spot, torch, 3);
		if (!player.isCreative()) {
			stack.shrink(1);
		}
		out(player, now);
		level.playSound(null, spot, SoundEvents.WOOD_PLACE, SoundSource.BLOCKS, 0.8F, 1.0F);
		level.sendParticles(WebSymbiote.dust(0.7F), spot.getX() + 0.5, spot.getY() + 0.5, spot.getZ() + 0.5, 5, 0.15, 0.15, 0.15, 0.0);
		say(player, "help.torch");
	}
}
