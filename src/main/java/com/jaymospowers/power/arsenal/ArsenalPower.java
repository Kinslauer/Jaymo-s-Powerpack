package com.jaymospowers.power.arsenal;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.api.Power;
import com.jaymospowers.api.PowerKey;
import com.jaymospowers.core.Powers;
import com.jaymospowers.power.arsenal.entity.ArsenalEntities;
import com.jaymospowers.power.arsenal.entity.RiftBlade;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Prediction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Pocket Arsenal: reach through a rift into other dimensions and pull out whatever weapon answers (Balkon's WeaponMod
 * weapons plus vanilla ones). Pulled weapons are temporary: they carry the {@link Summoned} component and go back when
 * their time is up, when dropped, or when their holder dies.
 *
 * <ul>
 * <li>R - Reach Through: open a rift, pull a random weapon (+ ammo) into your hand.</li>
 * <li>G - Return: send the held summoned weapon back; refunds part of Reach's cooldown. A standing Sword of Damocles is called back first.</li>
 * <li>V - Barrage: 3-5 rifts open around you and fire weapons at what you're looking at.</li>
 * <li>B - Rift Chains ({@link ArsenalChains}): chains out of rifts hold what you aim at; B again yanks it to you.
 * Corrupted: Sword of Damocles ({@link ArsenalDamocles}).</li>
 * <li>Z - Pocket Stash: your personal 27-slot pocket dimension.</li>
 * <li>X - Cannon Battery ({@link ArsenalCannons}): hold to grow a fan of rift cannons, let go to fire them all.</li>
 * </ul>
 */
public final class ArsenalPower implements Power {
	public static final ArsenalPower INSTANCE = new ArsenalPower();
	public static final Identifier ID = JaymosPowers.id("arsenal");

	// tuning (ticks)
	public static final int REACH_COOLDOWN = 160;
	public static final int SUMMON_TICKS = 1500;
	public static final int MAX_HELD = 3;
	/** Return refunds this share of Reach's cooldown for a weapon sent back fresh, less the longer it was out. */
	public static final float RETURN_REFUND_MAX = 0.9F;
	public static final int BARRAGE_COOLDOWN = 600;
	public static final int BARRAGE_RANGE = 48;
	public static final float BARRAGE_DAMAGE = 7.0F;
	/**
	 * The Barrage's rifts open one after another, this many ticks apart; each weapon slides out of its rift and hangs
	 * there for {@code BARRAGE_HOVER} ticks (so you see it come out, as with Reach) before it fires.
	 */
	static final int BARRAGE_GAP = 10;
	static final int BARRAGE_HOVER = 18;

	/** A Barrage blade still to come out of its rift. */
	private record QueuedBlade(long at, Vec3 pos, Vec3 aim, int targetId, ItemStack dress, boolean corrupted, float damage) {
	}

	private static final Map<UUID, List<QueuedBlade>> BARRAGES = new HashMap<>();
	/** A dropped summoned weapon lies on the ground this long before it dissolves. */
	public static final int DROPPED_TICKS = 60;

	/** When each ability is ready again (game time). Saved, and synced to its owner for the HUD. */
	public record Cooldowns(long reach, long barrage, long ultimate, long damocles, long cannons, long chains) {
		public static final Cooldowns NONE = new Cooldowns(0, 0, 0, 0, 0, 0);
		public static final Codec<Cooldowns> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.LONG.fieldOf("reach").forGetter(Cooldowns::reach),
				Codec.LONG.fieldOf("barrage").forGetter(Cooldowns::barrage),
				Codec.LONG.optionalFieldOf("ultimate", 0L).forGetter(Cooldowns::ultimate),
				Codec.LONG.optionalFieldOf("damocles", 0L).forGetter(Cooldowns::damocles),
				Codec.LONG.optionalFieldOf("cannons", 0L).forGetter(Cooldowns::cannons),
				Codec.LONG.optionalFieldOf("chains", 0L).forGetter(Cooldowns::chains))
			.apply(i, Cooldowns::new));
		public static final StreamCodec<ByteBuf, Cooldowns> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_LONG, Cooldowns::reach,
			ByteBufCodecs.VAR_LONG, Cooldowns::barrage,
			ByteBufCodecs.VAR_LONG, Cooldowns::ultimate,
			ByteBufCodecs.VAR_LONG, Cooldowns::damocles,
			ByteBufCodecs.VAR_LONG, Cooldowns::cannons,
			ByteBufCodecs.VAR_LONG, Cooldowns::chains,
			Cooldowns::new);

		public Cooldowns withReach(long ready) {
			return new Cooldowns(ready, barrage, ultimate, damocles, cannons, chains);
		}

		public Cooldowns withBarrage(long ready) {
			return new Cooldowns(reach, ready, ultimate, damocles, cannons, chains);
		}

		public Cooldowns withUltimate(long ready) {
			return new Cooldowns(reach, barrage, ready, damocles, cannons, chains);
		}

		public Cooldowns withDamocles(long ready) {
			return new Cooldowns(reach, barrage, ultimate, ready, cannons, chains);
		}

		public Cooldowns withCannons(long ready) {
			return new Cooldowns(reach, barrage, ultimate, damocles, ready, chains);
		}

		public Cooldowns withChains(long ready) {
			return new Cooldowns(reach, barrage, ultimate, damocles, cannons, ready);
		}
	}

	public static final AttachmentType<Cooldowns> COOLDOWNS = AttachmentRegistry.create(JaymosPowers.id("arsenal_cooldowns"),
		builder -> builder.persistent(Cooldowns.CODEC).copyOnDeath().syncWith(Cooldowns.STREAM_CODEC, AttachmentSyncPredicate.targetOnly()));

	/** A rift being reached into: when it opened, and what's coming out. Server memory only. */
	private record Pending(long start, List<ItemStack> stacks, int life) {
	}

	/** Rifts that are open: player → the reach in progress. */
	private static final Map<UUID, Pending> PENDING = new HashMap<>();

	private ArsenalPower() {
	}

	/** Called once from {@link JaymosPowers#onInitialize()}: register the power and everything it needs. */
	public static void init() {
		ArsenalRelics.init();
		ArsenalReach.init();
		Summoned.TYPE = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, JaymosPowers.id("arsenal_summoned"),
			DataComponentType.<Summoned>builder().persistent(Summoned.CODEC).networkSynchronized(Summoned.STREAM_CODEC).build());
		ArsenalEntities.init();
		ArsenalStash.init();
		ArsenalPocket.init();
		ArsenalArmory.init();
		HundredBlades.init();
		ArsenalDamocles.init();
		ArsenalCannons.init();
		ArsenalChains.init();
		ArsenalVoid.init();
		SwordStone.register();
		RelicWeapons.register();
		VoidCrown.register();
		ArsenalFlintlock.register();
		ArsenalItems.init();
		ArsenalDebugCommand.init();
		Powers.register(INSTANCE);
		ServerTickEvents.END_SERVER_TICK.register(ArsenalPower::serverTick);
	}

	@Override
	public Identifier id() {
		return ID;
	}

	@Override
	public int color() {
		return 0x9B5CFF;
	}

	@Override
	public ItemStack icon() {
		return new ItemStack(Items.ENDER_CHEST);
	}

	public static Cooldowns cooldowns(net.minecraft.world.entity.player.Player player) {
		return player.getAttachedOrElse(COOLDOWNS, Cooldowns.NONE);
	}

	// ---------------------------------------------------------------- keys

	@Override
	public void onKey(ServerPlayer player, PowerKey key, boolean down) {
		boolean voided = com.jaymospowers.core.Fate.isCorrupted(player) && !com.jaymospowers.core.Mastery.starterOnly(player);
		if (key == PowerKey.ABILITY_3 && voided) {
			// the Void Arsenal's V: gates instead of Barrage (held: a gate cast out ahead)
			ArsenalGates.key(player, down);
			return;
		}
		if (!down) {
			return;
		}
		if (com.jaymospowers.core.Mastery.starterOnly(player)) {
			// a fresh awakening can only rummage
			if (key == PowerKey.ABILITY_1) {
				rummage(player);
			}
			return;
		}
		switch (key) {
			case ABILITY_1 -> reach(player);
			case ABILITY_2 -> {
				// a standing Sword of Damocles is called back first
				if (!ArsenalDamocles.recall(player)) {
					sendBack(player);
				}
			}
			case ABILITY_3 -> barrage(player);
			// B: Rift Chains; the Void Arsenal's drops the Sword of Damocles instead (the pocket lives on Z)
			case ABILITY_4 -> {
				if (voided) {
					ArsenalDamocles.cast(player);
				} else {
					ArsenalChains.key(player);
				}
			}
		}
	}

	/** Z: the Pocket Dimension, once B (the ability it used to be) is unlocked. */
	@Override
	public void onMode(ServerPlayer player, boolean down) {
		if (down && slotUnlocked(player, "mode")) {
			ArsenalPocket.toggle(player);
		}
	}

	@Override
	public boolean slotUnlocked(net.minecraft.world.entity.player.Player player, String slot) {
		// Z (the Pocket Stash) and X (Cannon Battery) both come with B
		return !"mode".equals(slot) && !"special".equals(slot) || ArsenalCannons.unlocked(player);
	}

	@Override
	public void onUltimate(ServerPlayer player, boolean down) {
		if (down && player.isAlive()) {
			HundredBlades.cast(player);
		}
	}

	/** Out of the void: its gates close. */
	@Override
	public void onPurified(ServerPlayer player) {
		ArsenalGates.close(player);
	}

	@Override
	public void onCorrupted(ServerPlayer player) {
		// the rift opens onto the void now
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENDERMAN_SCREAM, SoundSource.PLAYERS, 1.0F, 0.5F);
		player.level().sendParticles(ParticleTypes.REVERSE_PORTAL, player.getX(), player.getY() + 1.0, player.getZ(), 80, 0.6, 1.0, 0.6, 0.1);
	}

	/** (0.15.21) Downed: what was running ends. */
	@Override
	public void onDowned(ServerPlayer player) {
		// the gates close and the chains let go of whoever they bound
		ArsenalGates.close(player);
		ArsenalChains.onLost(player);
	}

	@Override
	public void onLost(ServerPlayer player) {
		ArsenalGates.close(player);
		ArsenalArmory.onLost(player);
		HundredBlades.onLost(player);
		ArsenalDamocles.onLost(player);
		ArsenalCannons.onLost(player);
		ArsenalChains.onLost(player);
		PENDING.remove(player.getUUID());
		BARRAGES.remove(player.getUUID());
		player.removeAttached(ArsenalReach.REACH);
		// the doorway closes: everything this player summoned goes home
		dissolveWhere(player, s -> s.ownedBy(player.getUUID()), true);
	}

	@Override
	public void tick(ServerPlayer player) {
		tickBarrage(player);
		if (com.jaymospowers.core.Fate.isCorrupted(player)) {
			ArsenalGates.tick(player);
		}
		ArsenalVoid.tick(player);
		ArsenalRelics.tick(player);
		HundredBlades.tick(player);
		ArsenalDamocles.tick(player);
		ArsenalCannons.tick(player);
		ArsenalFlintlock.tickTacz(player);
		Pending pending = PENDING.get(player.getUUID());
		if (pending == null) {
			return;
		}
		ServerLevel level = player.level();
		if (!player.isAlive()) {
			PENDING.remove(player.getUUID());
			player.removeAttached(ArsenalReach.REACH);
			return;
		}
		// the portal itself is drawn by every client (see ArsenalReach); the server adds the motes and the timing
		long t = level.getGameTime() - pending.start();
		Vec3 rift = ArsenalReach.portalCenter(player, 1.0F);
		if (t < ArsenalReach.PULL_END && t % 3 == 0) {
			level.sendParticles(ParticleTypes.PORTAL, rift.x, rift.y, rift.z, 3, 0.2, 0.2, 0.2, 0.4);
		}
		if (t == ArsenalReach.REACH_END) {
			level.playSound(null, rift.x, rift.y, rift.z, SoundEvents.PORTAL_AMBIENT, SoundSource.PLAYERS, 0.35F, 1.6F);
		}
		if (t == ArsenalReach.PULL_END) {
			pull(player, rift, pending.stacks(), pending.life());
		}
		if (t >= ArsenalReach.CLOSE_END) {
			PENDING.remove(player.getUUID());
			player.removeAttached(ArsenalReach.REACH);
			level.sendParticles(ParticleTypes.REVERSE_PORTAL, rift.x, rift.y, rift.z, 16, 0.1, 0.1, 0.1, 0.12);
			level.playSound(null, rift.x, rift.y, rift.z, SoundEvents.ENDER_EYE_DEATH, SoundSource.PLAYERS, 0.5F, 1.5F);
		}
	}

	// ---------------------------------------------------------------- R: Reach Through

	private static void reach(ServerPlayer player) {
		ServerLevel level = player.level();
		long now = level.getGameTime();
		if (PENDING.containsKey(player.getUUID())) {
			return;
		}
		if (ArsenalArmory.tryPin(player)) {
			return;
		}
		Cooldowns cd = cooldowns(player);
		if (now < cd.reach()) {
			denied(player, "message.jaymospowers.arsenal.reach_recharging", cd.reach() - now);
			return;
		}
		player.setAttached(COOLDOWNS, cd.withReach(now + REACH_COOLDOWN));
		// decided now, so the weapon everyone sees coming out of the portal is the one you get
		// the first of the three you can hold: your pinned weapon, or else a relic from the Pocket Stash
		boolean first = heldBatches(player) == 0;
		ItemStack relic = first && ArsenalArmory.pinned(player) == null ? ArsenalRelics.takeFromStash(player) : ItemStack.EMPTY;
		List<ItemStack> stacks;
		if (!relic.isEmpty()) {
			// the relic itself, with whatever it fires (arrows for a bow, say)
			stacks = new ArrayList<>(ArsenalPool.stacksFor(level, player.getRandom(), relic.getItem()));
			stacks.set(0, relic);
		} else {
			List<ItemStack> pair = ArsenalFlintlock.pairFor(player, player.getRandom());
			stacks = pair != null ? pair : new ArrayList<>(ArsenalArmory.rollFor(player, level, player.getRandom(), first));
		}
		if (com.jaymospowers.core.Fate.isCorrupted(player)) {
			stacks.forEach(ArsenalVoid::voidTouched);
		}
		PENDING.put(player.getUUID(), new Pending(now, stacks, SUMMON_TICKS));
		player.setAttached(ArsenalReach.REACH, new ArsenalReach.Reach(now, stacks.isEmpty() ? ItemStack.EMPTY : stacks.getFirst().copy()));
		Vec3 rift = ArsenalReach.portalCenter(player, 1.0F);
		level.playSound(null, rift.x, rift.y, rift.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.7F, 0.55F);
		level.playSound(null, rift.x, rift.y, rift.z, SoundEvents.PORTAL_TRIGGER, SoundSource.PLAYERS, 0.15F, 1.8F);
	}

	/** The starter, on R: Rummage. */
	@Override
	public PowerKey starterKey() {
		return PowerKey.ABILITY_1;
	}

	/** Rummage's finds: mostly odds and ends; now and then ({@link #RUMMAGE_WEAPON_CHANCE}) a stone weapon. */
	private static final net.minecraft.world.item.Item[] RUMMAGE_JUNK = {
		net.minecraft.world.item.Items.STICK, net.minecraft.world.item.Items.FLINT, net.minecraft.world.item.Items.ARROW,
		net.minecraft.world.item.Items.WOODEN_SWORD, net.minecraft.world.item.Items.WOODEN_AXE, net.minecraft.world.item.Items.BONE,
		net.minecraft.world.item.Items.WOODEN_SHOVEL};
	private static final net.minecraft.world.item.Item[] RUMMAGE_WEAPONS = {
		net.minecraft.world.item.Items.STONE_SWORD, net.minecraft.world.item.Items.STONE_AXE, net.minecraft.world.item.Items.STONE_SPEAR};
	static final float RUMMAGE_WEAPON_CHANCE = 0.1F;
	/** Now and then ({@link #RUMMAGE_TOOL_CHANCE}) something useful instead. */
	private static final net.minecraft.world.item.Item[] RUMMAGE_TOOLS = {
		net.minecraft.world.item.Items.FLINT_AND_STEEL, net.minecraft.world.item.Items.FISHING_ROD, net.minecraft.world.item.Items.SHEARS,
		net.minecraft.world.item.Items.COMPASS, net.minecraft.world.item.Items.CLOCK, net.minecraft.world.item.Items.SPYGLASS,
		net.minecraft.world.item.Items.BUCKET, net.minecraft.world.item.Items.LEAD, net.minecraft.world.item.Items.IRON_PICKAXE,
		net.minecraft.world.item.Items.TORCH};
	static final float RUMMAGE_TOOL_CHANCE = 0.25F;
	/** How long a rummaged thing lasts, and Rummage's cooldown. */
	static final int RUMMAGE_LIFE = 600;
	static final int RUMMAGE_COOLDOWN = 600;

	/**
	 * The starter, Rummage: reach a little way into the pocket dimension (the same rift, smaller) and pull out whatever
	 * your hand closes on: usually junk, sometimes a stone weapon. It dissolves after {@link #RUMMAGE_LIFE}.
	 */
	private static void rummage(ServerPlayer player) {
		ServerLevel level = player.level();
		long now = level.getGameTime();
		if (PENDING.containsKey(player.getUUID())) {
			return;
		}
		Cooldowns cd = cooldowns(player);
		if (now < cd.reach()) {
			denied(player, "message.jaymospowers.arsenal.reach_recharging", cd.reach() - now);
			return;
		}
		player.setAttached(COOLDOWNS, cd.withReach(now + RUMMAGE_COOLDOWN));
		var random = player.getRandom();
		float roll = random.nextFloat();
		net.minecraft.world.item.Item item = roll < RUMMAGE_WEAPON_CHANCE ? RUMMAGE_WEAPONS[random.nextInt(RUMMAGE_WEAPONS.length)]
			: roll < RUMMAGE_WEAPON_CHANCE + RUMMAGE_TOOL_CHANCE ? RUMMAGE_TOOLS[random.nextInt(RUMMAGE_TOOLS.length)]
			: RUMMAGE_JUNK[random.nextInt(RUMMAGE_JUNK.length)];
		List<ItemStack> stacks = new java.util.ArrayList<>(List.of(new ItemStack(item)));
		PENDING.put(player.getUUID(), new Pending(now, stacks, RUMMAGE_LIFE));
		player.setAttached(ArsenalReach.REACH, new ArsenalReach.Reach(now, stacks.getFirst().copy()));
		Vec3 rift = ArsenalReach.portalCenter(player, 1.0F);
		level.playSound(null, rift.x, rift.y, rift.z, SoundEvents.BUNDLE_DROP_CONTENTS, SoundSource.PLAYERS, 0.6F, 0.8F);
		level.playSound(null, rift.x, rift.y, rift.z, SoundEvents.PORTAL_TRIGGER, SoundSource.PLAYERS, 0.08F, 2.0F);
	}

	private static Vec3 riftPos(ServerPlayer player) {
		Vec3 look = player.getLookAngle();
		Vec3 right = rightOf(look);
		return player.getEyePosition().add(look.scale(1.3)).add(right.scale(0.35)).add(0, -0.25, 0);
	}

	private static Vec3 rightOf(Vec3 look) {
		Vec3 right = look.cross(new Vec3(0, 1, 0));
		return right.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : right.normalize();
	}

	/** A spinning ring of portal particles facing the player. */
	private static void riftRing(ServerLevel level, ServerPlayer player, Vec3 center, double radius) {
		Vec3 look = player.getLookAngle();
		Vec3 right = rightOf(look);
		Vec3 up = right.cross(look).normalize();
		double spin = level.getGameTime() * 0.35;
		for (int i = 0; i < 8; i++) {
			double a = spin + i * Math.PI / 4;
			Vec3 p = center.add(right.scale(Math.cos(a) * radius)).add(up.scale(Math.sin(a) * radius));
			level.sendParticles(ParticleTypes.REVERSE_PORTAL, p.x, p.y, p.z, 1, 0, 0, 0, 0);
		}
		level.sendParticles(ParticleTypes.PORTAL, center.x, center.y, center.z, 4, 0.1, 0.1, 0.1, 0.6);
	}

	private static void pull(ServerPlayer player, Vec3 rift, List<ItemStack> stacks, int life) {
		ServerLevel level = player.level();
		long now = level.getGameTime();
		makeRoom(player, MAX_HELD - 1);

		Summoned tag = new Summoned(player.getUUID(), now + life, player.getRandom().nextInt());
		Inventory inventory = player.getInventory();
		for (int i = 0; i < stacks.size(); i++) {
			ItemStack stack = stacks.get(i);
			stack.set(Summoned.TYPE, tag);
			if (i == 0 && player.getMainHandItem().isEmpty()) {
				player.setItemInHand(InteractionHand.MAIN_HAND, stack);
			} else {
				// into the inventory; at your feet if it's full (where it dissolves after a few seconds)
				inventory.placeItemBackInInventory(stack, Prediction.SERVER_ONLY);
			}
		}

		level.sendParticles(ParticleTypes.END_ROD, rift.x, rift.y, rift.z, 14, 0.15, 0.15, 0.15, 0.08);
		level.sendParticles(ParticleTypes.REVERSE_PORTAL, rift.x, rift.y, rift.z, 30, 0.25, 0.25, 0.25, 0.05);
		level.playSound(null, rift.x, rift.y, rift.z, SoundEvents.ENDER_EYE_DEATH, SoundSource.PLAYERS, 0.9F, 0.8F);
		level.playSound(null, rift.x, rift.y, rift.z, SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.6F, 0.6F);
		if (!stacks.isEmpty()) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.arsenal.pulled", stacks.getFirst().getHoverName())
				.withStyle(ChatFormatting.LIGHT_PURPLE));
		}
		// a full Reach (not Rummage) into the void: sometimes something else follows the weapon out
		if (life == SUMMON_TICKS && com.jaymospowers.core.Fate.isCorrupted(player)) {
			VoidCrown.maybeFind(player, rift);
		}
	}

	/** A summon came back into the player's hands (a Barrage blade picked up): keep to the cap of {@link #MAX_HELD}. */
	public static void capSummons(ServerPlayer player) {
		makeRoom(player, MAX_HELD);
	}

	/** Dissolves this player's oldest summons until at most {@code keep} batches are left. */
	private static void makeRoom(ServerPlayer player, int keep) {
		UUID id = player.getUUID();
		while (true) {
			Map<Integer, Long> batches = new HashMap<>();
			forEachHeld(player, (stack, setter) -> {
				Summoned s = Summoned.of(stack);
				if (s != null && s.ownedBy(id)) {
					batches.merge(s.batch(), s.expires(), Math::min);
				}
			});
			if (batches.size() <= keep) {
				return;
			}
			int oldest = batches.entrySet().stream().min(Map.Entry.comparingByValue()).orElseThrow().getKey();
			dissolveWhere(player, s -> s.batch() == oldest && s.ownedBy(id), true);
		}
	}

	// ---------------------------------------------------------------- G: Return

	private static void sendBack(ServerPlayer player) {
		ItemStack held = player.getMainHandItem();
		Summoned s = Summoned.of(held);
		if (s == null) {
			held = player.getOffhandItem();
			s = Summoned.of(held);
		}
		if (s == null) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.arsenal.nothing_to_return").withStyle(ChatFormatting.GRAY));
			player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.DISPENSER_FAIL, SoundSource.PLAYERS, 0.4F, 1.4F);
			return;
		}
		Summoned sent = s;
		dissolveWhere(player, x -> x.batch() == sent.batch() && x.owner().equals(sent.owner()), true);
		if (sent.ownedBy(player.getUUID())) {
			long now = player.level().getGameTime();
			Cooldowns cd = cooldowns(player);
			float left = net.minecraft.util.Mth.clamp((sent.expires() - now) / (float)SUMMON_TICKS, 0.0F, 1.0F);
			int refund = Math.round(REACH_COOLDOWN * RETURN_REFUND_MAX * left);
			player.setAttached(COOLDOWNS, cd.withReach(Math.max(now, cd.reach() - refund)));
		}
		player.sendOverlayMessage(Component.translatable("message.jaymospowers.arsenal.returned").withStyle(ChatFormatting.LIGHT_PURPLE));
	}

	// ---------------------------------------------------------------- V: Barrage

	private static void barrage(ServerPlayer player) {
		ServerLevel level = player.level();
		long now = level.getGameTime();
		Cooldowns cd = cooldowns(player);
		if (now < cd.barrage()) {
			denied(player, "message.jaymospowers.arsenal.barrage_recharging", cd.barrage() - now);
			return;
		}
		player.setAttached(COOLDOWNS, cd.withBarrage(now + BARRAGE_COOLDOWN));

		// what are we looking at?
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getLookAngle();
		Vec3 end = eye.add(look.scale(BARRAGE_RANGE));
		BlockHitResult blockHit = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
		if (blockHit.getType() != HitResult.Type.MISS) {
			end = blockHit.getLocation();
		}
		AABB search = player.getBoundingBox().expandTowards(look.scale(BARRAGE_RANGE)).inflate(1.5);
		EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(player, eye, end, search.inflate(0.0),
			e -> e.isPickable() && !e.isSpectator() && e.isAlive() && e != player, eye.distanceToSqr(end));
		@Nullable Entity target = entityHit == null ? null : entityHit.getEntity();
		Vec3 aim = target != null ? target.getBoundingBox().getCenter() : end;

		int count = 3 + player.getRandom().nextInt(3);
		Vec3 flat = new Vec3(look.x, 0, look.z);
		flat = flat.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : flat.normalize();
		Vec3 right = rightOf(flat);
		List<QueuedBlade> queue = new ArrayList<>();
		boolean corrupted = com.jaymospowers.core.Fate.isCorrupted(player);
		for (int i = 0; i < count; i++) {
			// left to right, one rift at a time
			double t = count == 1 ? 0 : (i / (count - 1.0)) * 2 - 1; // -1..1, left to right
			Vec3 offset = right.scale(t * 1.9).add(flat.scale(-0.7 + 0.4 * Math.abs(t))).add(0, 2.3 - 0.6 * Math.abs(t), 0);
			ItemStack dress = ArsenalPool.randomBlade(player.getRandom());
			queue.add(new QueuedBlade(now + (long)i * BARRAGE_GAP, offset, aim, target == null ? -1 : target.getId(),
				corrupted ? ArsenalVoid.voidTouched(dress) : dress, corrupted, BARRAGE_DAMAGE * (corrupted ? ArsenalVoid.DAMAGE_MULTIPLIER : 1.0F)));
		}
		BARRAGES.put(player.getUUID(), queue);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ILLUSIONER_CAST_SPELL, SoundSource.PLAYERS, 1.0F, 0.9F);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.6F, 0.5F);
	}

	/** The Barrage's rifts, opening in turn (each blade placed where it was meant to be, relative to you now). */
	private static void tickBarrage(ServerPlayer player) {
		List<QueuedBlade> queue = BARRAGES.get(player.getUUID());
		if (queue == null) {
			return;
		}
		ServerLevel level = player.level();
		if (!player.isAlive()) {
			BARRAGES.remove(player.getUUID());
			return;
		}
		long now = level.getGameTime();
		for (java.util.Iterator<QueuedBlade> it = queue.iterator(); it.hasNext(); ) {
			QueuedBlade q = it.next();
			if (now < q.at()) {
				break;
			}
			it.remove();
			Vec3 pos = player.position().add(q.pos());
			Entity target = q.targetId() < 0 ? null : level.getEntity(q.targetId());
			RiftBlade blade = RiftBlade.create(level, player, q.dress(), pos, q.aim(), target, BARRAGE_HOVER, q.damage());
			if (q.corrupted()) {
				blade.makeVoid();
			}
			level.addFreshEntity(blade);
			level.sendParticles(ParticleTypes.REVERSE_PORTAL, pos.x, pos.y, pos.z, 16, 0.25, 0.25, 0.25, 0.03);
			level.sendParticles(ParticleTypes.PORTAL, pos.x, pos.y, pos.z, 10, 0.1, 0.1, 0.1, 0.5);
			level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.35F, 0.6F + level.getRandom().nextFloat() * 0.3F);
		}
		if (queue.isEmpty()) {
			BARRAGES.remove(player.getUUID());
		}
	}

	private static void denied(ServerPlayer player, String key, long ticks) {
		player.sendOverlayMessage(Component.translatable(key, String.format("%.1f", ticks / 20.0)).withStyle(ChatFormatting.GRAY));
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.DISPENSER_FAIL, SoundSource.PLAYERS, 0.3F, 1.6F);
	}

	// ---------------------------------------------------------------- expiry

	private interface HeldVisitor {
		void visit(ItemStack stack, java.util.function.Consumer<ItemStack> setter);
	}

	/** Every stack the player holds: inventory, armour, offhand and the stack on their cursor. */
	private static void forEachHeld(ServerPlayer player, HeldVisitor visitor) {
		Inventory inventory = player.getInventory();
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			int s = slot;
			visitor.visit(inventory.getItem(slot), stack -> inventory.setItem(s, stack));
		}
		visitor.visit(player.containerMenu.getCarried(), stack -> player.containerMenu.setCarried(stack));
	}

	/** How many separate summons (batches) of the player's own they're holding right now. */
	private static int heldBatches(ServerPlayer player) {
		java.util.Set<Integer> batches = new java.util.HashSet<>();
		forEachHeld(player, (stack, setter) -> {
			Summoned s = Summoned.of(stack);
			if (s != null && s.ownedBy(player.getUUID())) {
				batches.add(s.batch());
			}
		});
		return batches.size();
	}

	/** Removes every held summoned stack matching {@code which}; returns how many went. */
	private static int dissolveWhere(ServerPlayer player, java.util.function.Predicate<Summoned> which, boolean effects) {
		List<ItemStack> gone = new ArrayList<>();
		forEachHeld(player, (stack, setter) -> {
			Summoned s = Summoned.of(stack);
			if (s != null && which.test(s)) {
				gone.add(stack.copy());
				ArsenalRelics.reclaim(player.level().getServer(), stack);
				setter.accept(ItemStack.EMPTY);
			}
		});
		if (!gone.isEmpty() && effects) {
			Summoned.dissolveEffects(player.level(), player.position().add(0, 1.0, 0));
		}
		return gone.size();
	}

	/** Once a second: everything summoned whose time is up goes home, whoever holds it (power or not). */
	private static void serverTick(MinecraftServer server) {
		if (server.getTickCount() % 20 != 0) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			long now = player.level().getGameTime();
			List<Component> names = new ArrayList<>();
			forEachHeld(player, (stack, setter) -> {
				Summoned s = Summoned.of(stack);
				if (s != null && now >= s.expires()) {
					if (names.isEmpty()) {
						names.add(stack.getHoverName());
					}
					ArsenalRelics.reclaim(server, stack);
					setter.accept(ItemStack.EMPTY);
				}
			});
			if (!names.isEmpty()) {
				Summoned.dissolveEffects(player.level(), player.position().add(0, 1.0, 0));
				player.sendOverlayMessage(Component.translatable("message.jaymospowers.arsenal.expired", names.getFirst())
					.withStyle(ChatFormatting.DARK_PURPLE));
			}
		}
	}

	/** Called by {@code PlayerMixin} before a dying player's inventory drops: summoned weapons never become loot. */
	public static void stripOnDeath(ServerPlayer player) {
		dissolveWhere(player, s -> true, true);
	}
}
