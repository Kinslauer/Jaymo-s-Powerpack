package com.jaymoskingdoms.spirits;

import com.jaymoskingdoms.Factions;
import com.jaymoskingdoms.Folk;
import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.Mana;
import com.jaymoskingdoms.PowersCompat;
import com.jaymoskingdoms.Thralls;
import com.jaymoskingdoms.Worker;
import com.jaymoskingdoms.Arcana;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.hurtingprojectile.AbstractHurtingProjectile;
import net.minecraft.world.entity.projectile.throwableitemprojectile.AbstractThrownPotion;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.EnderChestBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.245) The mage hands every spirit shares. Andy: "a mage hand type of ability that mimics the way stands work but not
 * their presentation", and the point of them is "manipulating items from way farther away than you could naturally". Every
 * spirit's move list starts with these seven:
 * <ol>
 * <li><b>Far hand</b>: opens the chest, throws the lever, swings the door in sight, from as far as the hands reach
 * ({@link #REACH}, more with every level); or pulls a dropped item to its keeper.</li>
 * <li><b>Grab and throw</b>: seizes the mob or item in sight and holds it ahead of its keeper, then hurls it.</li>
 * <li><b>Catch</b>: for a moment the arms snatch every arrow, fireball and potion that flies at its keeper.</li>
 * <li><b>Shove</b>: a push that throws the foes in front of its keeper back.</li>
 * <li><b>Hold the line</b>: holds the mob in sight fast where it stands.</li>
 * <li><b>Second pair of hands</b>: holds its keeper's shield (or torch) for them, so a shield turns blows even with both
 * hands full.</li>
 * <li><b>Leave a hand</b> (0.7.275): a hand stays at the chest in sight to gather, guard and tend crops ({@link TendedHand}).</li>
 * </ol>
 * None of it ever harms an ally: a player's own tamed animals, their thralls, the folk of their own kingdom ({@link #ally}).
 * The per-tick work of seizing and pinning is in {@link HandGrip}.
 */
public final class HandMoves {
	/** How far the hands reach (blocks), and how much farther for each level after the first (Andy: within 20 blocks). */
	public static final double REACH = 20.0, REACH_PER_LEVEL = 0.0;
	/** The chance a cast is spoken. */
	public static final float MOVE_SAY = 0.15F;
	/** A shove: how hard it throws, how far it reaches (blocks), and the cone's half-angle as a dot product (90 degrees wide). */
	static final double SHOVE_POWER = 1.6, SHOVE_RANGE = 5.0, SHOVE_CONE = 0.7071;
	/** Catch: how long the arms snatch (ticks), how near a shot must be (blocks), and what the spirit's own catch costs. */
	static final int CATCH_TICKS = 30;
	static final double CATCH_RANGE = 3.0;
	static final float CATCH_AUTO_COST = 6.0F;
	/** The second pair of hands: mana a second, and how much of the front it covers (dot product: 0 is the whole front half). */
	static final float HOLD_COST_PER_SECOND = 2.0F;
	static final double HOLD_ARC = 0.0;
	/** The torch's night vision lasts this long (ticks) and is renewed every second. */
	static final int TORCH_SIGHT = 260;
	/** The shove's screen shake. */
	static final float SHOVE_SHAKE = 0.3F;

	/** The shared moves, in the order they're cycled. */
	public static final List<Move> SHARED = new ArrayList<>();

	/** Game tick the keeper's catching ends. */
	private static final Map<UUID, Integer> CATCHING = new HashMap<>();
	/** Keepers whose spirit is holding their shield or torch. */
	private static final Map<UUID, Boolean> HOLDING = new HashMap<>();
	/** Keepers whose far hand opened a container from afar (their reach is stretched until it closes). */
	private static final Map<UUID, Boolean> FAR_MENU = new HashMap<>();

	private static final net.minecraft.resources.Identifier FAR_REACH = JaymosKingdoms.id("spirit_far_hand");

	private static final Hand FAR_HAND = new Hand("far_hand", 4.0F, 10, 1, Poses.REACH, Mana.WILL) {
		@Override
		boolean act(ServerPlayer player, SpiritState state, int charge) {
			return farHand(player, state, this);
		}
	};
	private static final Hand GRAB = new Hand("grab", 12.0F, 60, 2, Poses.GRAB, Mana.WILL) {
		@Override
		public boolean charges() {
			return true;
		}

		@Override
		boolean act(ServerPlayer player, SpiritState state, int charge) {
			return grab(player, state, charge, this);
		}
	};
	private static final Hand CATCH = new Hand("catch", 6.0F, 40, 3, Poses.CATCH, Mana.WARD) {
		@Override
		boolean act(ServerPlayer player, SpiritState state, int charge) {
			CATCHING.put(player.getUUID(), tick(player) + CATCH_TICKS);
			player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.8F, 1.2F);
			return true;
		}
	};
	private static final Hand SHOVE = new Hand("shove", 8.0F, 80, 1, Poses.SHOVE, Mana.HARM) {
		@Override
		boolean act(ServerPlayer player, SpiritState state, int charge) {
			return shove(player);
		}
	};
	private static final Hand PIN = new Hand("pin", 10.0F, 120, 4, Poses.PIN, Mana.WILL) {
		@Override
		boolean act(ServerPlayer player, SpiritState state, int charge) {
			return pin(player, state);
		}
	};
	private static final Hand HOLD = new Hand("hold", HOLD_COST_PER_SECOND, 20, 5, Poses.HOLD, Mana.WARD) {
		@Override
		boolean act(ServerPlayer player, SpiritState state, int charge) {
			return hold(player);
		}
	};

	/** (0.7.275) Leave a hand: one of the spirit's hands stays at the container in sight and works it ({@link TendedHand}). */
	private static final Hand LEAVE_HAND = new Hand("leave_hand", 15.0F, 40, 6, Poses.REACH, Mana.WILL) {
		@Override
		boolean act(ServerPlayer player, SpiritState state, int charge) {
			return leaveHand(player, state);
		}
	};

	static {
		SHARED.add(FAR_HAND);
		SHARED.add(SHOVE);
		SHARED.add(GRAB);
		SHARED.add(CATCH);
		SHARED.add(PIN);
		SHARED.add(HOLD);
		SHARED.add(LEAVE_HAND);
	}

	private HandMoves() {
	}

	/**
	 * One of the shared moves. The pose and cooldown can be set for the cast that is just happening ({@link CastHints}, read
	 * once by the dispatcher): the grab's first tap starts no cooldown so the second can throw, and a throw shows the
	 * throwing pose.
	 */
	private abstract static class Hand implements Move {
		private final String id;
		private final float cost;
		private final int cooldown, level, pose, intent;

		Hand(String id, float cost, int cooldown, int level, int pose, int intent) {
			this.id = id;
			this.cost = cost;
			this.cooldown = cooldown;
			this.level = level;
			this.pose = pose;
			this.intent = intent;
		}

		abstract boolean act(ServerPlayer player, SpiritState state, int charge);

		@Override
		public boolean cast(ServerPlayer player, SpiritState state, int charge) {
			boolean did = act(player, state, charge);
			if (did && player.getRandom().nextFloat() < MOVE_SAY) {
				SpiritVoice.say(player, "move");
			}
			return did;
		}

		@Override
		public String id() {
			return id;
		}

		@Override
		public float cost() {
			return cost;
		}

		@Override
		public int cooldown() {
			return cooldown;
		}

		@Override
		public int level() {
			return level;
		}

		@Override
		public int pose() {
			return pose;
		}

		@Override
		public int intent() {
			return intent;
		}
	}

	/** Adds {@link #SHARED} to {@link Moves}, and hooks the server tick and damage the hands work through. */
	public static void register() {
		for (Move move : SHARED) {
			Moves.register(move);
		}
		TendedHand.register();
		ServerTickEvents.END_SERVER_TICK.register(HandMoves::tick);
		ServerLivingEntityEvents.ALLOW_DAMAGE.register(HandMoves::allowDamage);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> HandGrip.letGo());
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> HandGrip.landed(entity));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			CATCHING.clear();
			HOLDING.clear();
			FAR_MENU.clear();
			HandGrip.clear();
		});
	}

	// ---- who may be touched -----------------------------------------------------------------------------------------

	/** The farthest the hands of {@code state}'s spirit reach (blocks). */
	public static double reach(SpiritState state) {
		return REACH + REACH_PER_LEVEL * (Math.max(1, state.level()) - 1);
	}

	/** Whether {@code e} is on {@code player}'s side: themselves, their tamed animals and thralls, their allies, their kingdom's folk. */
	public static boolean ally(ServerPlayer player, Entity e) {
		if (e == player) {
			return true;
		}
		if (e instanceof TamableAnimal pet && pet.isOwnedBy(player)) {
			return true;
		}
		if (e instanceof net.minecraft.world.entity.OwnableEntity owned && owned.getOwnerReference() != null) {
			return true;
		}
		Thralls.Tag tag = Thralls.tag(e);
		if (tag != null && player.getUUID().equals(tag.master())) {
			return true;
		}
		if (e instanceof Player other) {
			return other.isAlliedTo(player) || other.isCreative() || other.isSpectator();
		}
		MinecraftServer server = player.level().getServer();
		if (server != null && (e instanceof Folk || e instanceof Worker)) {
			var faction = Factions.of(server, player.getUUID());
			if (faction != null) {
				int id = e instanceof Folk folk ? folk.factionId() : ((Worker)e).factionId();
				return id == faction.id();
			}
		}
		return false;
	}

	/** Whether {@code e} is fair game for a shove or a throw's landing: a monster, or something coming for {@code player}. Never a player. */
	public static boolean foe(ServerPlayer player, LivingEntity e) {
		if (!e.isAlive() || e.isSpectator() || e instanceof Player || ally(player, e)) {
			return false;
		}
		return e instanceof Enemy || e instanceof Mob mob && mob.getTarget() == player;
	}

	private static int tick(Entity e) {
		MinecraftServer server = e.level().getServer();
		return server == null ? 0 : server.getTickCount();
	}

	// ---- far hand ---------------------------------------------------------------------------------------------------

	private static boolean reachable(BlockState state) {
		Block block = state.getBlock();
		return block instanceof ChestBlock || block instanceof BarrelBlock || block instanceof EnderChestBlock || block instanceof ShulkerBoxBlock
			|| block instanceof DoorBlock || block instanceof TrapDoorBlock || block instanceof FenceGateBlock || block instanceof LeverBlock
			|| block instanceof ButtonBlock || block instanceof LecternBlock || state.is(BlockTags.DOORS) || state.is(BlockTags.TRAPDOORS)
			|| state.is(BlockTags.FENCE_GATES) || state.is(BlockTags.BUTTONS);
	}

	/** A line of motes from {@code player}'s hand to {@code to}, seen by the attuned. */
	private static void line(ServerLevel level, ServerPlayer player, Vec3 to) {
		Vec3 from = player.getEyePosition().add(0.0, -0.4, 0.0);
		Vec3 step = to.subtract(from);
		int n = Math.min(40, Math.max(2, (int)(step.length() * 1.5)));
		for (int i = 1; i <= n; i++) {
			Vec3 p = from.add(step.scale(i / (double)n));
			Mana.sendSeen(level, Mana.MOTE, p.x, p.y, p.z, 1, 0.03, 0.03, 0.03, 0.0);
		}
	}

	private static boolean farHand(ServerPlayer player, SpiritState state, Hand move) {
		if (!(player.level() instanceof ServerLevel level)) {
			return false;
		}
		double reach = reach(state);
		Vec3 eye = player.getEyePosition();
		HitResult hit = player.pick(reach, 0.0F, false);
		Vec3 end = hit.getType() == HitResult.Type.MISS ? eye.add(player.getLookAngle().scale(reach)) : hit.getLocation();
		AABB box = player.getBoundingBox().expandTowards(end.subtract(eye)).inflate(1.0);
		EntityHitResult found = ProjectileUtil.getEntityHitResult(player, eye, end, box,
			e -> e.isAlive() && (e instanceof ItemEntity || e instanceof ItemFrame), reach * reach);
		if (found != null) {
			Entity target = found.getEntity();
			if (target instanceof ItemEntity item) {
				HandGrip.pull(player, item);
				line(level, player, item.position());
				CastHints.setPose(player, Poses.GRAB);
				CastHints.setReach(player, eye.distanceTo(item.position()));
				return true;
			}
			if (target instanceof ItemFrame frame) {
				InteractionResult result = frame.interact(player, InteractionHand.MAIN_HAND, found.getLocation().subtract(frame.position()));
				if (result.consumesAction()) {
					line(level, player, frame.position());
					CastHints.setReach(player, eye.distanceTo(frame.position()));
					return true;
				}
			}
		}
		if (hit instanceof BlockHitResult block && hit.getType() == HitResult.Type.BLOCK) {
			BlockState there = level.getBlockState(block.getBlockPos());
			if (!reachable(there)) {
				return false;
			}
			InteractionResult result = there.useWithoutItem(level, player, block);
			if (!result.consumesAction()) {
				return false;
			}
			line(level, player, hit.getLocation());
			CastHints.setReach(player, eye.distanceTo(hit.getLocation()));
			if (player.containerMenu != player.inventoryMenu) {
				stretch(player, eye.distanceTo(hit.getLocation()) + 2.0);
			}
			return true;
		}
		return false;
	}

	/** (0.7.275) Leave a hand at the container in sight within reach, or call home the one already there. */
	private static boolean leaveHand(ServerPlayer player, SpiritState state) {
		if (!(player.level() instanceof ServerLevel level)) {
			return false;
		}
		HitResult hit = player.pick(reach(state), 0.0F, false);
		if (!(hit instanceof BlockHitResult block) || hit.getType() != HitResult.Type.BLOCK
			|| !TendedHand.leave(player, state, block.getBlockPos())) {
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.spirit.hand_no_chest").withStyle(ChatFormatting.GRAY));
			return false;
		}
		line(level, player, hit.getLocation());
		CastHints.setReach(player, player.getEyePosition().distanceTo(hit.getLocation()));
		return true;
	}

	/** A container opened from afar closes itself unless its keeper is in range: stretch their block reach until it is shut. */
	private static void stretch(ServerPlayer player, double blocks) {
		AttributeInstance range = player.getAttribute(Attributes.BLOCK_INTERACTION_RANGE);
		if (range != null) {
			range.addOrUpdateTransientModifier(new AttributeModifier(FAR_REACH, blocks, AttributeModifier.Operation.ADD_VALUE));
			FAR_MENU.put(player.getUUID(), true);
		}
	}

	private static void unstretch(ServerPlayer player) {
		AttributeInstance range = player.getAttribute(Attributes.BLOCK_INTERACTION_RANGE);
		if (range != null) {
			range.removeModifier(FAR_REACH);
		}
	}

	// ---- grab and throw ---------------------------------------------------------------------------------------------

	private static boolean grab(ServerPlayer player, SpiritState state, int charge, Hand move) {
		if (HandGrip.holding(player)) {
			if (!HandGrip.hurl(player, charge)) {
				return false;
			}
			CastHints.setPose(player, Poses.THROW);
			return true;
		}
		double reach = reach(state);
		Entity target = Arcana.sighted(player);
		if (target != null && (player.distanceTo(target) > reach || !seizable(player, state, target))) {
			target = null;
		}
		if (target == null) {
			Vec3 eye = player.getEyePosition();
			Vec3 end = eye.add(player.getLookAngle().scale(reach));
			EntityHitResult found = ProjectileUtil.getEntityHitResult(player, eye, end,
				player.getBoundingBox().expandTowards(end.subtract(eye)).inflate(1.0), e -> e instanceof ItemEntity && e.isAlive(), reach * reach);
			target = found == null ? null : found.getEntity();
		}
		if (target == null) {
			return false;
		}
		HandGrip.seize(player, target);
		CastHints.setReach(player, player.getEyePosition().distanceTo(target.position().add(0.0, target.getBbHeight() * 0.5, 0.0)));
		if (player.level() instanceof ServerLevel level) {
			line(level, player, target.position().add(0.0, target.getBbHeight() * 0.5, 0.0));
		}
		// no cooldown yet: the second tap throws; the hold's end (or the throw) starts it
		CastHints.setCooldown(player, 0);
		return true;
	}

	private static boolean seizable(ServerPlayer player, SpiritState state, Entity e) {
		if (ally(player, e) || e == player.getVehicle() || e.getBbHeight() > HandGrip.MAX_HEIGHT || e instanceof Mob mob && HandGrip.pinned(mob)) {
			return false;
		}
		return !(e instanceof Player) || e.getBbHeight() < Spirits.of(state.kind()).size() * 2.0F;
	}

	// ---- catch ------------------------------------------------------------------------------------------------------

	private static boolean catchable(ServerPlayer player, Projectile shot) {
		if (!(shot instanceof AbstractArrow || shot instanceof AbstractHurtingProjectile || shot instanceof AbstractThrownPotion)) {
			return false;
		}
		Entity owner = shot.getOwner();
		if (owner != null && ally(player, owner)) {
			return false;
		}
		Vec3 motion = shot.getDeltaMovement();
		return motion.lengthSqr() > 0.01 && motion.dot(player.getBoundingBox().getCenter().subtract(shot.position())) > 0.0;
	}

	/** The arms snatch {@code shot}: arrows go to the keeper's hand or the ground, the rest are gone in a puff. */
	private static void snatch(ServerPlayer player, Projectile shot) {
		if (!(player.level() instanceof ServerLevel level)) {
			return;
		}
		if (shot instanceof AbstractArrow arrow && arrow.pickup == AbstractArrow.Pickup.ALLOWED) {
			ItemStack stack = arrow.getPickupItemStackOrigin().copy();
			if (!stack.isEmpty()) {
				if (player.getMainHandItem().isEmpty()) {
					player.setItemInHand(InteractionHand.MAIN_HAND, stack);
				} else if (!player.getInventory().add(stack)) {
					player.spawnAtLocation(level, stack);
				}
			}
		}
		level.sendParticles(ParticleTypes.END_ROD, shot.getX(), shot.getY(), shot.getZ(), 6, 0.1, 0.1, 0.1, 0.05);
		level.playSound(null, shot.getX(), shot.getY(), shot.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.8F, 1.6F);
		shot.discard();
	}

	/**
	 * For a spirit's own {@code hurt} hook: if {@code source} is a shot the arms could snatch, snatch it and say so (true: cancel
	 * the hit). In a catch the keeper cast it always works; otherwise the spirit does it on its own ({@link Spirits#act}: bond,
	 * mana to spare), when it knows the move and the move is off cooldown, which then starts.
	 */
	public static boolean catchIfReady(ServerPlayer player, SpiritState state, DamageSource source) {
		if (!state.out() || !(source.getDirectEntity() instanceof Projectile shot) || !catchable(player, shot)) {
			return false;
		}
		// (a cast catch is handled first, in allowDamage; this is the spirit catching on its own)
		long now = player.level().getGameTime();
		if (state.level() < CATCH.level() || Moves.wait(player, CATCH, now) > 0L
			|| !Spirits.act(player, state.kind(), Spirits.FLASH_WARD, CATCH_AUTO_COST)) {
			return false;
		}
		Moves.start(player, CATCH, now);
		snatch(player, shot);
		Spirits.show(player, Poses.CATCH);
		return true;
	}

	private static boolean catching(ServerPlayer player) {
		Integer until = CATCHING.get(player.getUUID());
		return until != null && until > tick(player);
	}

	// ---- shove ------------------------------------------------------------------------------------------------------

	private static boolean shove(ServerPlayer player) {
		if (!(player.level() instanceof ServerLevel level)) {
			return false;
		}
		Vec3 look = player.getLookAngle();
		Vec3 eye = player.getEyePosition();
		DamageSource source = level.damageSources().indirectMagic(player, player);
		boolean any = false;
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(SHOVE_RANGE), e -> foe(player, e))) {
			Vec3 to = e.getBoundingBox().getCenter().subtract(eye);
			if (to.length() > SHOVE_RANGE || to.normalize().dot(look) < SHOVE_CONE) {
				continue;
			}
			e.knockback(SHOVE_POWER, player.getX() - e.getX(), player.getZ() - e.getZ(), source, 0.0F);
			e.syncVelocity = true;
			any = true;
		}
		if (!any) {
			return false;
		}
		Vec3 front = eye.add(look.scale(2.0));
		Mana.sendSeen(level, Mana.MOTE, front.x, front.y - 0.3, front.z, 14, 0.8, 0.5, 0.8, 0.05);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 1.0F, 0.8F);
		PowersCompat.shake(player, SHOVE_SHAKE);
		return true;
	}

	// ---- hold the line ----------------------------------------------------------------------------------------------

	private static boolean pin(ServerPlayer player, SpiritState state) {
		LivingEntity target = Arcana.sighted(player);
		if (!(target instanceof Mob mob) || player.distanceTo(mob) > reach(state) || ally(player, mob) || HandGrip.pinned(mob)) {
			return false;
		}
		HandGrip.pin(player, mob);
		CastHints.setReach(player, player.getEyePosition().distanceTo(mob.position().add(0.0, mob.getBbHeight() * 0.5, 0.0)));
		if (player.level() instanceof ServerLevel level) {
			line(level, player, mob.position().add(0.0, mob.getBbHeight() * 0.5, 0.0));
		}
		return true;
	}

	// ---- the second pair of hands -----------------------------------------------------------------------------------

	private static boolean torch(ItemStack stack) {
		return stack.is(Items.TORCH) || stack.is(Items.SOUL_TORCH) || stack.is(Items.LANTERN) || stack.is(Items.SOUL_LANTERN);
	}

	private static boolean shield(ItemStack stack) {
		return stack.has(DataComponents.BLOCKS_ATTACKS);
	}

	/** The spirit is put away: {@code id}'s catch, hold and far-hand menu end. */
	static void forgetKeeper(UUID id) {
		CATCHING.remove(id);
		HOLDING.remove(id);
		FAR_MENU.remove(id);
	}

	private static boolean hold(ServerPlayer player) {
		if (HOLDING.remove(player.getUUID()) != null) {
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.spirit.hold.off").withStyle(ChatFormatting.GRAY));
			return false;
		}
		ItemStack off = player.getOffhandItem();
		if (!shield(off) && !torch(off)) {
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.spirit.hold.nothing").withStyle(ChatFormatting.GRAY));
			return false;
		}
		HOLDING.put(player.getUUID(), true);
		player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.spirit.hold.on").withStyle(ChatFormatting.BLUE));
		return true;
	}

	/** Cancels a blow from the front while the spirit holds the shield, and a shot the catch is snatching. */
	private static boolean allowDamage(LivingEntity victim, DamageSource source, float amount) {
		if (!(victim instanceof ServerPlayer player) || !Spirits.out(player)) {
			return true;
		}
		SpiritState state = Spirits.state(player);
		if (state != null && catching(player) && source.getDirectEntity() instanceof Projectile shot && catchable(player, shot)) {
			snatch(player, shot);
			Spirits.show(player, Poses.CATCH);
			return false;
		}
		if (HOLDING.containsKey(player.getUUID()) && shield(player.getOffhandItem()) && !source.is(DamageTypeTags.BYPASSES_SHIELD)) {
			Vec3 from = source.getSourcePosition();
			if (from != null) {
				Vec3 to = new Vec3(from.x - player.getX(), 0.0, from.z - player.getZ());
				Vec3 look = new Vec3(player.getLookAngle().x, 0.0, player.getLookAngle().z);
				if (to.lengthSqr() > 1.0E-6 && look.lengthSqr() > 1.0E-6 && to.normalize().dot(look.normalize()) > HOLD_ARC) {
					if (amount >= 3.0F) {
						player.getOffhandItem().hurtAndBreak(1 + (int)amount, player, EquipmentSlot.OFFHAND);
					}
					player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 1.0F, 1.0F);
					Spirits.show(player, Poses.HOLD);
					return false;
				}
			}
		}
		return true;
	}

	// ---- every tick -------------------------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		HandGrip.tick(server);
		int now = server.getTickCount();
		if (!CATCHING.isEmpty()) {
			tickCatching(server, now);
		}
		if (!HOLDING.isEmpty()) {
			tickHolding(server, now);
		}
		if (!FAR_MENU.isEmpty()) {
			for (Iterator<UUID> it = FAR_MENU.keySet().iterator(); it.hasNext();) {
				ServerPlayer owner = server.getPlayerList().getPlayer(it.next());
				if (owner == null || owner.containerMenu == owner.inventoryMenu) {
					if (owner != null) {
						unstretch(owner);
					}
					it.remove();
				}
			}
		}
	}

	private static void tickCatching(MinecraftServer server, int now) {
		for (Iterator<Map.Entry<UUID, Integer>> it = CATCHING.entrySet().iterator(); it.hasNext();) {
			Map.Entry<UUID, Integer> entry = it.next();
			ServerPlayer owner = server.getPlayerList().getPlayer(entry.getKey());
			if (owner == null || !owner.isAlive() || now >= entry.getValue() || !Spirits.out(owner)) {
				it.remove();
				continue;
			}
			for (Projectile shot : owner.level().getEntitiesOfClass(Projectile.class, owner.getBoundingBox().inflate(CATCH_RANGE),
				s -> s.isAlive() && catchable(owner, s))) {
				snatch(owner, shot);
				Spirits.show(owner, Poses.CATCH);
			}
		}
	}

	private static void tickHolding(MinecraftServer server, int now) {
		for (Iterator<UUID> it = HOLDING.keySet().iterator(); it.hasNext();) {
			ServerPlayer owner = server.getPlayerList().getPlayer(it.next());
			if (owner == null || !owner.isAlive() || !Spirits.out(owner)) {
				it.remove();
				continue;
			}
			ItemStack off = owner.getOffhandItem();
			if (!shield(off) && !torch(off)) {
				it.remove();
				owner.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.spirit.hold.off").withStyle(ChatFormatting.GRAY));
				continue;
			}
			if (now % 20 == 0) {
				if (!Spirits.upkeep(owner, HOLD_COST_PER_SECOND)) {
					it.remove();
					owner.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.spirit.hold.off").withStyle(ChatFormatting.GRAY));
					continue;
				}
				if (torch(off)) {
					owner.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, TORCH_SIGHT, 0, true, false, false));
				}
			}
			SpiritState state = Spirits.state(owner);
			if (now % 12 == 0 && state != null && (state.pose() == Poses.IDLE || state.pose() == Poses.HOLD)) {
				Spirits.show(owner, Poses.HOLD);
			}
		}
	}
}
