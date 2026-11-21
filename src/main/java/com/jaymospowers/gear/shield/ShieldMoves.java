package com.jaymospowers.gear.shield;

import com.jaymospowers.combat.Guard;
import com.jaymospowers.core.fx.MagicFx;
import com.jaymospowers.gear.ThrownGear;
import com.jaymospowers.power.darktide.DarktideStatus;
import com.jaymospowers.stamina.Feathers;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Shield moves for anyone holding a shield up (not a weapon's guard), on the guard's own inputs and without touching how the
 * guard or the parry work ({@link Guard#raised}):
 * <ul>
 * <li><b>Throw</b> (attack, shield up, main hand not a weapon, which the shield bang owns): the shield flies at the nearest foe in
 * front, ricochets to up to {@link #HOPS} of them and comes back. A round shield throws best.</li>
 * <li><b>Slam</b> (sneak + attack, shield up): brought down on the ground, it stuns and launches everyone round you.</li>
 * <li><b>Charge</b> (sprint, shield up): a short rush behind the shield that knocks aside whoever it meets.</li>
 * <li><b>Slam from the air</b> (sneak + attack, shield up, off the ground): you drive down, and the slam lands on impact, wider and
 * harder the farther you fell ({@link #AIR_SLAM_PER_BLOCK}, capped).</li>
 * <li><b>Rising uppercut</b> (jump + attack, shield up, not sneaking): you launch up carrying the foes within {@link #RISE_REACH} in
 * front of you, then they drop. Players are only lifted a little ({@link #RISE_PLAYER_UP}).</li>
 * <li><b>Ride the shield down</b> (shield up, looking steeply down as you land): the shield takes {@link #FALL_SHARE} of the fall.</li>
 * <li><b>Brace</b> (stand still, shield up, {@link #BRACE_TICKS}): nothing from the front moves you; arrows are turned back, and a
 * blow lands for {@link #BRACE_MELEE_SHARE} of its weight.</li>
 * </ul>
 */
public final class ShieldMoves {
	// ---- tuning ----
	/** Throw: foes it reaches, how far it looks for the first and for each hop (blocks), speed (blocks/tick), the wait before the next throw (ticks), feathers. */
	public static final int HOPS = 3, THROW_COOLDOWN = 80, THROW_FEATHERS = 2;
	public static final double THROW_RANGE = 14.0, HOP_RANGE = 8.0, THROW_SPEED = 1.3, THROW_FRONT_DOT = 0.5, OUT_REACH = 8.0;
	public static final float THROW_DAMAGE = 5.0F, ROUND_BONUS = 1.5F;
	public static final int THROW_STUN = 15, THROW_LOST_AFTER = 120;
	/** Slam: reach (blocks), harm, stun (ticks), outward and upward launch, the wait, feathers, shield wear. */
	public static final double SLAM_RADIUS = 3.5, SLAM_OUT = 0.9, SLAM_UP = 0.7;
	public static final float SLAM_DAMAGE = 3.0F;
	public static final int SLAM_STUN = 40, SLAM_COOLDOWN = 60, SLAM_FEATHERS = 3, SLAM_WEAR = 2;
	/** Slam from the air: extra harm and reach per block fallen, the most extra of each, the dive's speed, how long it may last (ticks). */
	public static final float AIR_SLAM_PER_BLOCK = 0.5F, AIR_SLAM_MAX = 10.0F;
	public static final double AIR_SLAM_WIDE_PER_BLOCK = 0.12, AIR_SLAM_WIDE_MAX = 2.5, AIR_DIVE_SPEED = -1.8;
	public static final int AIR_DIVE_TICKS = 80, AIR_SLAM_MIN_FALL = 2;
	/** Rising uppercut: reach in front (blocks), the lift of you and of foes, a player's lift, harm, stun, the wait, feathers, shield wear. */
	public static final double RISE_REACH = 2.5, RISE_UP = 0.9, RISE_FOE_UP = 1.0, RISE_PLAYER_UP = 0.35, RISE_FRONT_DOT = 0.3;
	public static final float RISE_DAMAGE = 2.0F;
	public static final int RISE_STUN = 25, RISE_COOLDOWN = 70, RISE_FEATHERS = 3, RISE_WEAR = 1, RISE_GROUND_GRACE = 6;
	/** Ride the shield down: the pitch past which you look steeply down, the share of the fall the shield takes, the least it ever leaves, wear per point taken. */
	public static final float FALL_PITCH = 60.0F, FALL_SHARE = 0.5F, FALL_FLOOR = 3.0F;
	public static final int FALL_WEAR_PER = 2;
	/** Charge: ticks it lasts, reach in front (blocks), harm, stun, the shove, the wait, feathers. */
	public static final int CHARGE_TICKS = 12, CHARGE_COOLDOWN = 80, CHARGE_FEATHERS = 4, CHARGE_STUN = 20;
	public static final double CHARGE_REACH = 1.7, CHARGE_SHOVE = 1.0, CHARGE_LIFT = 0.3;
	public static final float CHARGE_DAMAGE = 3.0F;
	/** Brace: ticks standing still before it takes hold, how wide in front it holds (degrees each side), the share of a blow that lands, the biggest blow it turns. */
	public static final int BRACE_TICKS = 20;
	public static final double BRACE_ARC = 75.0, STILL = 0.0004;
	public static final float BRACE_MELEE_SHARE = 0.25F, BRACE_CAP = 14.0F;
	/** What the brace asks of the shield: a point of wear per this much it turns aside. */
	public static final float BRACE_WEAR_PER = 3.0F;

	private static final Identifier BRACE_ID = Identifier.fromNamespaceAndPath("jaymospowers", "shield_brace");

	/** What kind of move the client asks for. */
	public static final int THROW = 0, SLAM = 1, CHARGE = 2, RISE = 3;

	private static final class State {
		Vec3 last = Vec3.ZERO;
		int still;
		boolean braced;
		long throwReady, slamReady, chargeReady, chargeEnd, riseReady, groundTick, diveEnd;
		double peakY;
		boolean diving;
		final Set<UUID> charged = new HashSet<>();
	}

	private static final Map<UUID, State> STATES = new HashMap<>();
	private static boolean redealing;

	private ShieldMoves() {
	}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				tick(player);
			}
		});
		ServerLivingEntityEvents.ALLOW_DAMAGE.register(ShieldMoves::allowDamage);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> STATES.remove(handler.player.getUUID()));
	}

	private static State state(ServerPlayer player) {
		return STATES.computeIfAbsent(player.getUUID(), id -> new State());
	}

	/** Whether a shield (not a weapon's guard) is up in {@code player}'s hands. */
	public static boolean shieldUp(ServerPlayer player) {
		return !player.isSpectator() && Guard.raised(player) && !Guard.weaponGuard(player.getUseItem());
	}

	/** Round shields throw best: the Armory's and any other mod's by name. */
	public static boolean round(ItemStack shield) {
		return BuiltInRegistries.ITEM.getKey(shield.getItem()).getPath().contains("round_shield");
	}

	/** A move the client asked for. */
	public static void move(ServerPlayer player, int kind) {
		if (!shieldUp(player)) {
			return;
		}
		State state = state(player);
		long now = player.level().getGameTime();
		// (Andy's skill-tree locks) the throw is the Shield Throw perk's; the slam and the charge Shieldbearer's
		if (!com.jaymospowers.core.TreeLocks.check(player, kind == THROW ? com.jaymospowers.core.TreeLocks.SHIELD_THROW : com.jaymospowers.core.TreeLocks.SHIELD_MOVES)) {
			return;
		}
		switch (kind) {
			case THROW -> {
				if (now >= state.throwReady && Feathers.spend(player, THROW_FEATHERS) && throwShield(player)) {
					state.throwReady = now + THROW_COOLDOWN;
				}
			}
			case SLAM -> {
				if (now >= state.slamReady && Feathers.spend(player, SLAM_FEATHERS)) {
					state.slamReady = now + SLAM_COOLDOWN;
					if (airborne(player)) {
						dive(player, state, now);
					} else {
						slam(player, 0.0);
					}
				}
			}
			case RISE -> {
				if (now >= state.riseReady && (player.onGround() || now - state.groundTick <= RISE_GROUND_GRACE) && Feathers.spend(player, RISE_FEATHERS)) {
					state.riseReady = now + RISE_COOLDOWN;
					rise(player);
				}
			}
			case CHARGE -> {
				if (now >= state.chargeReady && Feathers.spend(player, CHARGE_FEATHERS)) {
					state.chargeReady = now + CHARGE_COOLDOWN;
					state.chargeEnd = now + CHARGE_TICKS;
					state.charged.clear();
					player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 1.0F, 0.7F);
				}
			}
			default -> {
			}
		}
	}

	private static boolean foe(ServerPlayer player, LivingEntity e) {
		return e != player && e.isAlive() && !e.isSpectator() && !player.isAlliedTo(e)
			&& (e instanceof Enemy || e instanceof Mob mob && mob.getTarget() == player);
	}

	// ---- throw ----

	private static boolean throwShield(ServerPlayer player) {
		ServerLevel level = player.level();
		InteractionHand hand = player.getUsedItemHand();
		ItemStack shield = player.getUseItem();
		Vec3 look = player.getLookAngle();
		LivingEntity first = nearest(level, player, player.getEyePosition(), THROW_RANGE, look, Set.of());
		float damage = THROW_DAMAGE * (round(shield) ? ROUND_BONUS : 1.0F);
		ItemStack thrown = shield.copy();
		player.stopUsingItem();
		player.setItemInHand(hand, ItemStack.EMPTY);
		ThrownGear gear = new ThrownGear(level, player, thrown);
		Vec3 eye = player.getEyePosition();
		gear.setPos(eye.x, eye.y - 0.3, eye.z);
		gear.follow(new Flight(thrown, hand, first, eye.add(look.scale(OUT_REACH)), damage));
		level.addFreshEntity(gear);
		level.playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.TRIDENT_THROW.value(), SoundSource.PLAYERS, 1.0F, 0.8F);
		player.swing(hand, SwingAnimation.DEFAULT, true);
		return true;
	}

	private static @Nullable LivingEntity nearest(ServerLevel level, ServerPlayer player, Vec3 from, double range, @Nullable Vec3 facing, Set<UUID> not) {
		LivingEntity best = null;
		double bestDistance = range * range;
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(from, from).inflate(range), e -> foe(player, e) && !not.contains(e.getUUID()))) {
			Vec3 to = e.getBoundingBox().getCenter().subtract(from);
			double distance = to.lengthSqr();
			if (distance >= bestDistance || facing != null && to.normalize().dot(facing) < THROW_FRONT_DOT) {
				continue;
			}
			if (!level.clip(new net.minecraft.world.level.ClipContext(from, e.getBoundingBox().getCenter(), net.minecraft.world.level.ClipContext.Block.COLLIDER,
				net.minecraft.world.level.ClipContext.Fluid.NONE, player)).getType().equals(net.minecraft.world.phys.HitResult.Type.MISS)) {
				continue;
			}
			best = e;
			bestDistance = distance;
		}
		return best;
	}

	/** The thrown shield's path: out to a foe, on to the next, then home. */
	private static final class Flight implements ThrownGear.Flight {
		private final ItemStack shield;
		private final InteractionHand hand;
		private final float damage;
		private @Nullable LivingEntity target;
		private @Nullable Vec3 point;
		private boolean homing;
		private final Set<UUID> struck = new HashSet<>();

		Flight(ItemStack shield, InteractionHand hand, @Nullable LivingEntity first, Vec3 point, float damage) {
			this.shield = shield;
			this.hand = hand;
			this.target = first;
			this.point = point;
			this.damage = damage;
		}

		@Override
		public void tick(ThrownGear gear) {
			if (!(gear.level() instanceof ServerLevel level)) {
				return;
			}
			if (!(gear.getOwner() instanceof ServerPlayer owner) || !owner.isAlive() || owner.level() != level) {
				drop(gear, level);
				return;
			}
			if (gear.tickCount > THROW_LOST_AFTER) {
				this.homing = true;
			}
			Vec3 goal;
			if (this.homing) {
				goal = owner.getEyePosition().add(0.0, -0.4, 0.0);
			} else if (this.target != null && this.target.isAlive()) {
				goal = this.target.getBoundingBox().getCenter();
			} else {
				this.target = null;
				goal = this.point;
			}
			Vec3 to = goal.subtract(gear.position());
			double distance = to.length();
			if (distance > THROW_SPEED) {
				Vec3 step = to.scale(THROW_SPEED / distance);
				gear.setPos(gear.position().add(step));
				gear.setDeltaMovement(step);
				gear.setYRot(gear.getYRot() + 40.0F);
				return;
			}
			gear.setPos(goal);
			if (this.homing) {
				catchHome(gear, level, owner);
			} else if (this.target != null) {
				strike(gear, level, owner, this.target);
			} else {
				this.homing = true;
			}
		}

		private void strike(ThrownGear gear, ServerLevel level, ServerPlayer owner, LivingEntity hit) {
			hit.hurtServer(level, level.damageSources().playerAttack(owner), this.damage);
			DarktideStatus.stun(hit, THROW_STUN);
			this.shield.hurtWithoutBreaking(1, owner);
			this.struck.add(hit.getUUID());
			Vec3 at = hit.getBoundingBox().getCenter();
			level.playSound(null, at.x, at.y, at.z, SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 1.2F, 0.7F);
			MagicFx.blast(level, at, 0.9F, 0xD8D0B0);
			LivingEntity next = this.struck.size() < HOPS ? nearest(level, owner, at, HOP_RANGE, null, this.struck) : null;
			if (next != null) {
				MagicFx.beam(level, at, next.getBoundingBox().getCenter(), 0.03F, 0xD8D0B0, 4);
			}
			this.target = next;
			this.homing = next == null;
		}

		private void catchHome(ThrownGear gear, ServerLevel level, ServerPlayer owner) {
			ItemStack back = this.shield;
			if (owner.getItemInHand(this.hand).isEmpty()) {
				owner.setItemInHand(this.hand, back);
			} else if (!owner.getInventory().add(back)) {
				gear.spawnAtLocation(level, back);
			}
			level.playSound(null, owner.getX(), owner.getY(), owner.getZ(), SoundEvents.TRIDENT_RETURN, SoundSource.PLAYERS, 0.8F, 1.1F);
			gear.discard();
		}

		private void drop(ThrownGear gear, ServerLevel level) {
			gear.spawnAtLocation(level, this.shield);
			gear.discard();
		}
	}

	// ---- slam ----

	private static boolean airborne(ServerPlayer player) {
		return !player.onGround() && !player.isInWater() && !player.isFallFlying() && !player.isPassenger() && !player.getAbilities().flying;
	}

	/** Started in the air: drive down; the slam lands in {@link #tick}. */
	private static void dive(ServerPlayer player, State state, long now) {
		state.diving = true;
		state.diveEnd = now + AIR_DIVE_TICKS;
		Vec3 v = player.getDeltaMovement();
		player.setDeltaMovement(v.x * 0.3, Math.min(v.y, AIR_DIVE_SPEED), v.z * 0.3);
		player.syncVelocity = true;
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 1.0F, 0.5F);
	}

	/** The slam, harder and wider by {@code fall} blocks fallen (0 for the ground slam). */
	private static void slam(ServerPlayer player, double fall) {
		float extra = (float) Math.min(AIR_SLAM_MAX, Math.max(0.0, fall - AIR_SLAM_MIN_FALL) * AIR_SLAM_PER_BLOCK);
		double radius = SLAM_RADIUS + Math.min(AIR_SLAM_WIDE_MAX, Math.max(0.0, fall - AIR_SLAM_MIN_FALL) * AIR_SLAM_WIDE_PER_BLOCK);
		ServerLevel level = player.level();
		Vec3 at = player.position();
		player.swing(player.getUsedItemHand(), SwingAnimation.DEFAULT, true);
		player.getUseItem().hurtWithoutBreaking(SLAM_WEAR, player);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 1.0F, 0.7F);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 1.4F, 0.5F);
		MagicFx.ring(level, at.add(0.0, 0.1, 0.0), (float) radius, 0xC8B070);
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(radius, 1.5, radius),
			e -> e != player && e.isAlive() && !e.isSpectator() && !player.isAlliedTo(e))) {
			Vec3 away = e.position().subtract(at);
			Vec3 flat = new Vec3(away.x, 0.0, away.z);
			if (flat.length() > radius) {
				continue;
			}
			flat = flat.lengthSqr() < 1.0E-4 ? Vec3.directionFromRotation(0.0F, player.getYRot()) : flat.normalize();
			e.hurtServer(level, level.damageSources().playerAttack(player), SLAM_DAMAGE + extra);
			DarktideStatus.stun(e, SLAM_STUN);
			e.setDeltaMovement(flat.x * SLAM_OUT, SLAM_UP, flat.z * SLAM_OUT);
			e.syncVelocity = true;
		}
	}

	// ---- rising uppercut ----

	private static void rise(ServerPlayer player) {
		ServerLevel level = player.level();
		Vec3 look = Vec3.directionFromRotation(0.0F, player.getYRot());
		Vec3 front = player.position().add(look.scale(RISE_REACH * 0.5));
		player.swing(player.getUsedItemHand(), SwingAnimation.DEFAULT, true);
		player.getUseItem().hurtWithoutBreaking(RISE_WEAR, player);
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(front, front).inflate(RISE_REACH * 0.6, 1.2, RISE_REACH * 0.6),
			e -> e != player && e.isAlive() && !e.isSpectator() && !player.isAlliedTo(e)
				&& !(e instanceof net.minecraft.world.entity.TamableAnimal pet && pet.isOwnedBy(player)))) {
			Vec3 to = e.position().subtract(player.position());
			Vec3 flat = new Vec3(to.x, 0.0, to.z);
			if (flat.length() > RISE_REACH || flat.lengthSqr() > 1.0E-4 && flat.normalize().dot(look) < RISE_FRONT_DOT) {
				continue;
			}
			boolean human = e instanceof ServerPlayer;
			if (!human) {
				e.hurtServer(level, level.damageSources().playerAttack(player), RISE_DAMAGE);
				DarktideStatus.stun(e, RISE_STUN);
			}
			e.setDeltaMovement(e.getDeltaMovement().x, human ? RISE_PLAYER_UP : RISE_FOE_UP, e.getDeltaMovement().z);
			e.syncVelocity = true;
		}
		Vec3 v = player.getDeltaMovement();
		player.setDeltaMovement(v.x, RISE_UP, v.z);
		player.syncVelocity = true;
		Vec3 at = player.position();
		level.playSound(null, at.x, at.y, at.z, SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 1.2F, 0.9F);
		MagicFx.blast(level, at.add(look.scale(1.0)).add(0.0, 0.8, 0.0), 0.9F, 0xC8B070);
	}

	// ---- per tick: charge and brace ----

	private static void tick(ServerPlayer player) {
		State state = state(player);
		ServerLevel level = player.level();
		long now = level.getGameTime();
		Vec3 pos = player.position();
		Vec3 moved = pos.subtract(state.last).multiply(1.0, 0.0, 1.0);
		state.last = pos;
		boolean up = shieldUp(player);
		if (state.diving) {
			if (player.onGround()) {
				state.diving = false;
				slam(player, state.peakY - pos.y);
			} else if (now > state.diveEnd || player.isInWater() || player.isFallFlying()) {
				state.diving = false;
			}
		}
		if (player.onGround()) {
			state.groundTick = now;
			state.peakY = pos.y;
		} else {
			state.peakY = Math.max(state.peakY, pos.y);
		}
		if (up && now < state.chargeEnd) {
			ram(player, state, level);
		}
		// (Andy's skill-tree locks) bracing is Shieldbearer's
		boolean still = up && player.onGround() && !player.isSprinting() && (moved.lengthSqr() < STILL || player.isShiftKeyDown()) && now >= state.chargeEnd
			&& com.jaymospowers.core.TreeLocks.has(player, com.jaymospowers.core.TreeLocks.SHIELD_MOVES);
		state.still = still ? state.still + 1 : 0;
		boolean braced = state.still >= BRACE_TICKS;
		if (braced != state.braced) {
			state.braced = braced;
			AttributeInstance knockback = player.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
			if (knockback != null) {
				if (braced) {
					knockback.addOrReplacePermanentModifier(new AttributeModifier(BRACE_ID, 1.0, AttributeModifier.Operation.ADD_VALUE));
				} else {
					knockback.removeModifier(BRACE_ID);
				}
			}
			if (braced) {
				player.sendOverlayMessage(Component.translatable("message.jaymospowers.shield.braced").withStyle(ChatFormatting.GOLD));
				level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 0.5F, 0.6F);
			}
		}
	}

	private static void ram(ServerPlayer player, State state, ServerLevel level) {
		Vec3 look = Vec3.directionFromRotation(0.0F, player.getYRot());
		Vec3 front = player.position().add(look.scale(CHARGE_REACH * 0.5));
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(front, front).inflate(CHARGE_REACH * 0.8, 1.0, CHARGE_REACH * 0.8),
			e -> e != player && e.isAlive() && !e.isSpectator() && !player.isAlliedTo(e) && !state.charged.contains(e.getUUID()))) {
			Vec3 to = e.position().subtract(player.position());
			if (new Vec3(to.x, 0.0, to.z).normalize().dot(look) < 0.3) {
				continue;
			}
			state.charged.add(e.getUUID());
			e.hurtServer(level, level.damageSources().playerAttack(player), CHARGE_DAMAGE);
			DarktideStatus.stun(e, CHARGE_STUN);
			e.setDeltaMovement(look.x * CHARGE_SHOVE, CHARGE_LIFT, look.z * CHARGE_SHOVE);
			e.syncVelocity = true;
			player.getUseItem().hurtWithoutBreaking(1, player);
			level.playSound(null, e.getX(), e.getY(), e.getZ(), SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 1.2F, 0.8F);
		}
	}

	// ---- brace ----

	private static boolean allowDamage(LivingEntity victim, DamageSource source, float amount) {
		if (redealing || !(victim instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) {
			return true;
		}
		if (source.is(net.minecraft.tags.DamageTypeTags.IS_FALL) && amount > FALL_FLOOR && player.getXRot() > FALL_PITCH && shieldUp(player)
			&& com.jaymospowers.core.TreeLocks.has(player, com.jaymospowers.core.TreeLocks.SHIELD_MOVES)) {
			// ridden down on the shield: it takes its share of the fall, and never leaves less than a short fall's worth
			float lands = Math.max(amount * (1.0F - FALL_SHARE), FALL_FLOOR);
			player.getUseItem().hurtWithoutBreaking(Math.max(1, (int) ((amount - lands) * FALL_WEAR_PER)), player);
			Vec3 at = player.position();
			level.playSound(null, at.x, at.y, at.z, SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 1.2F, 0.5F);
			level.playSound(null, at.x, at.y, at.z, SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.5F, 0.6F);
			level.sendParticles(net.minecraft.core.particles.ParticleTypes.POOF, at.x, at.y + 0.1, at.z, 14, 0.8, 0.05, 0.8, 0.04);
			com.jaymospowers.core.Shakes.felt(player, at, 6.0, 1.2F, com.jaymospowers.core.Shakes.ticksFor(1.2F));
			redealing = true;
			try {
				player.hurtServer(level, source, lands);
			} finally {
				redealing = false;
			}
			return false;
		}
		State state = STATES.get(player.getUUID());
		if (state == null || !state.braced || !shieldUp(player)) {
			return true;
		}
		Vec3 from = source.getSourcePosition();
		if (from == null) {
			return true;
		}
		Vec3 to = from.subtract(player.position());
		Vec3 look = Vec3.directionFromRotation(0.0F, player.getYRot());
		Vec3 flat = new Vec3(to.x, 0.0, to.z);
		if (flat.lengthSqr() > 1.0E-4 && flat.normalize().dot(look) < Math.cos(Math.toRadians(BRACE_ARC))) {
			return true;
		}
		ItemStack shield = player.getUseItem();
		if (source.getDirectEntity() instanceof Projectile shot) {
			// turned aside and sent back the way it came
			shot.setDeltaMovement(shot.getDeltaMovement().scale(-0.6));
			shot.setOwner(player);
			shield.hurtWithoutBreaking(1, player);
			level.playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 1.2F, 1.2F);
			return false;
		}
		if (amount > BRACE_CAP) {
			return true;
		}
		shield.hurtWithoutBreaking(Math.max(1, (int) (amount / BRACE_WEAR_PER)), player);
		level.playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 1.0F, 0.6F);
		float lands = amount * BRACE_MELEE_SHARE;
		redealing = true;
		try {
			// the rest of the blow lands as a lighter one, through the normal guard
			player.hurtServer(level, source, lands);
		} finally {
			redealing = false;
		}
		return false;
	}
}
