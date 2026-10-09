package com.jaymospowers.power.gravity;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.api.Power;
import com.jaymospowers.api.PowerKey;
import com.jaymospowers.core.Fate;
import com.jaymospowers.core.PowerInput;
import com.jaymospowers.core.Powers;
import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Gravity: taking weight away from things, and piling it on. Everything is decided here, on the server; the targets'
 * weight is their {@code minecraft:gravity} attribute (transient modifiers, so nothing sticks if the server stops).
 * <ul>
 * <li>R, Lift (hold): the creature under the crosshair loses its weight and floats where you point. Let go and it falls
 * (fall damage is real). Works on players too.</li>
 * <li>V, Weightless (toggle): your own gravity drops to a fraction; you jump high and fall slow (and land lighter). It
 * costs hunger and runs out.</li>
 * <li>G, Gravity Well: a point where you aim pulls everything nearby into it for a few seconds.</li>
 * <li>B, Crush (hold to charge): everything around where you aim gets many times heavier: slammed down, hurt and pinned.</li>
 * </ul>
 * 0.2.0 fate: creatures that die to the weight you gave them (falls after Lift, Crush, the Singularity) count toward
 * <b>ascension</b> (Event Horizon: unmovable, projectiles bend away; C is the {@code Singularity}); the same deaths, and
 * long weightlessness, <b>corrupt</b> it (Collapse: you grow heavy, everything loose drifts toward you, the power hits
 * harder). The cure: be lifted by something other than yourself, a shulker's levitation, for long enough.
 */
public final class GravityPower implements Power {
	public static final GravityPower INSTANCE = new GravityPower();
	public static final Identifier ID = JaymosPowers.id("gravity");

	public static final int LIFT = 0;
	public static final int WELL = 1;
	public static final int WEIGHTLESS = 2;
	public static final int CRUSH = 3;
	public static final int SINGULARITY = 4;

	// ---- Lift (R) ----
	public static final double LIFT_RANGE = 30.0;
	/** Lift's reach grows with the power: {@code LIFT_RANGE_BASE} plus this per ability mastered, up to {@code LIFT_RANGE}. */
	static final double LIFT_RANGE_BASE = 14.0, LIFT_RANGE_PER_LEVEL = 4.0;
	/** Held distance is clamped to this range (blocks from the eyes). */
	static final double LIFT_MIN_HOLD = 3.0, LIFT_MAX_HOLD = 14.0;
	/** How hard a lifted creature is steered toward the held point, and its top speed (b/t). */
	static final double LIFT_STEER = 0.22, LIFT_MAX_SPEED = 0.7;
	/** Longest a lift lasts, and the cooldown after it ends. */
	public static final int LIFT_MAX_TICKS = 120;
	public static final int LIFT_COOLDOWN = 160;
	static final float LIFT_EXHAUSTION = 0.012F;

	// ---- Gravity Well (G) ----
	static final double WELL_RANGE = 24.0;
	static final double WELL_RADIUS = 7.0;
	static final double WELL_PULL = 0.09;
	static final int WELL_TICKS = 80;
	public static final int WELL_COOLDOWN = 280;

	// ---- Weightless (V) ----
	/** Your gravity while weightless (fraction removed), and how much lighter you land. */
	static final double WEIGHTLESS_GRAVITY = -0.7;
	static final double WEIGHTLESS_FALL = -0.5;
	public static final int WEIGHTLESS_MAX_TICKS = 600;
	public static final int WEIGHTLESS_COOLDOWN = 200;
	/** Switched off early with V (not run out), the cooldown is only this. */
	public static final int WEIGHTLESS_EARLY_COOLDOWN = 40;
	static final float WEIGHTLESS_EXHAUSTION = 0.015F;

	/**
	 * While you're weightless, everything else near you gets heavier: creatures and players within
	 * {@code HEAVY_AURA_RADIUS} weigh {@code HEAVY_AURA_WEIGHT} times more (falling hard, jumping low) and are slowed,
	 * refreshed as you move, like Chrono's Overclock.
	 */
	static final double HEAVY_AURA_RADIUS = 6.0;
	static final double HEAVY_AURA_WEIGHT = 1.5;
	static final double HEAVY_AURA_JUMP = -0.35;
	static final int HEAVY_AURA_REFRESH = 5;

	// ---- Crush (B, charged) ----
	static final double CRUSH_RANGE = 24.0;
	public static final int CRUSH_FULL_CHARGE = 60;
	static final double CRUSH_RADIUS_MIN = 2.5, CRUSH_RADIUS_MAX = 5.5;
	static final float CRUSH_DAMAGE_MIN = 4.0F, CRUSH_DAMAGE_MAX = 16.0F;
	/** Crushed creatures weigh this many times more (gravity multiplier) for {@code CRUSH_PIN_TICKS}. */
	static final double CRUSH_WEIGHT = 3.0;
	static final int CRUSH_PIN_TICKS = 60;
	static final double CRUSH_SLAM = 1.6;
	static final int CRUSH_COOLDOWN_MIN = 120, CRUSH_COOLDOWN_MAX = 400;

	// ---- Choke and Slam (right click while lifting, once R is unlocked) ----
	/** The choke holds it this far out, its head at your hand; the squeeze's damage, every so often. */
	static final double CHOKE_DISTANCE = 0.75;
	/** Right click on a held dropped item: it's drawn to you this fast and into your inventory (as much as fits). */
	static final double DRAW_SPEED = 1.1, DRAW_REACH = 1.3;
	/** Choked, it's held up this far above your eyes, the arm raised. */
	static final double CHOKE_RAISE = 0.7;
	static final float CHOKE_DAMAGE = 1.0F;
	static final int CHOKE_DAMAGE_EVERY = 20;
	/** The slam: straight down this fast, under this much extra weight (times its gravity), for at most this long. */
	static final double SLAM_SPEED = 2.2;
	/**
	 * Before it comes down, it's hauled up: for {@code SLAM_RISE_TICKS} (fewer per ability mastered, at least
	 * {@code SLAM_RISE_MIN}) at {@code SLAM_RISE_SPEED}; then down at {@code SLAM_SPEED} plus {@code SLAM_SPEED_PER_LEVEL}
	 * per ability (and once more ascended). The more mastered, the faster the whole thing.
	 */
	static final int SLAM_RISE_TICKS = 10, SLAM_RISE_MIN = 4;
	static final double SLAM_RISE_SPEED = 0.75, SLAM_SPEED_PER_LEVEL = 0.7;
	static final double SLAM_WEIGHT_BASE = 2.0, SLAM_WEIGHT_PER_LEVEL = 1.0;
	static final int SLAM_MAX_TICKS = 30;
	/** Width × height (blocks) past which a slammed creature counts as heavy (a cow is 0.9 × 1.4). */
	static final float HEAVY_SLAM_SIZE = 1.27F;
	/**
	 * A slam buries what it hits: one block deep, two, or three. The chance of three is {@code BURY_3_BASE} plus
	 * {@code BURY_3_PER_LEVEL} per ability mastered (and once more ascended); of what's left, half the time it's two.
	 * Blocks up to {@code BURY_HARDNESS} (stone, dirt) give way always, harder ones only as the slam's break strength allows.
	 */
	static final float BURY_3_BASE = 0.1F, BURY_3_PER_LEVEL = 0.2F;
	static final float BURY_HARDNESS = 1.5F;
	/** The ring of ground around the impact that kicks up debris. */
	static final int IMPACT_RING = 2;
	/** How big the debris puffs are (a broken block's, and the ring's). */
	static final float IMPACT_DEBRIS = 2.2F, BURY_DEBRIS = 2.8F;
	/** Damage: base, per ability unlocked, more once ascended. */
	static final float SLAM_DAMAGE_BASE = 4.0F, SLAM_DAMAGE_PER_LEVEL = 3.0F, SLAM_DAMAGE_ASCENDED = 4.0F;
	/** The block it hits breaks once the power has grown: the hardest it can break at 3 and 4 abilities, and ascended (stone is 1.5). */
	static final float SLAM_BREAK_LEVEL_3 = 1.5F, SLAM_BREAK_LEVEL_4 = 3.0F, SLAM_BREAK_ASCENDED = 5.0F;

	private static final Identifier SLAM_ID = JaymosPowers.id("gravity_slam");
	private static final Identifier HEAVY_AURA_ID = JaymosPowers.id("gravity_heavy_aura");
	private static final Identifier HEAVY_AURA_JUMP_ID = JaymosPowers.id("gravity_heavy_aura_jump");
	/** Who's weighed down by someone's weightlessness, and until when (game time). */
	private record Weighed(ResourceKey<Level> dimension, UUID entity, long until) {
	}

	private static final java.util.Map<UUID, Weighed> WEIGHED = new java.util.HashMap<>();

	// ---- fate ----
	/** Deaths by your weight (a fall after Lift, Crush, the Singularity) within this long of your touch count. */
	static final int TOUCH_MEMORY_TICKS = 200;
	/** Deaths by weight needed to ascend. */
	public static final int ASCEND_KILLS = 30;
	/** Corruption per death by weight, and per this many ticks spent weightless. */
	static final int CORRUPT_PER_KILL = 2;
	static final int CORRUPT_WEIGHTLESS_TICKS = 400;
	/** Collapse: your own weight, landing harder, harder hits, and the pull on everything loose. */
	static final double COLLAPSE_GRAVITY = 0.35;
	static final double COLLAPSE_FALL = 0.25;
	static final float COLLAPSE_DAMAGE = 1.6F;
	static final double COLLAPSE_RADIUS_BONUS = 1.0;
	static final double COLLAPSE_FLING = 1.5;
	static final double COLLAPSE_DRIFT_RADIUS = 6.0;
	static final double COLLAPSE_DRIFT = 0.035;
	/** The cure: this long levitating (from something else) while collapsed. */
	public static final int CURE_LEVITATION_TICKS = 200;
	/** Event Horizon: projectiles this close are bent away. */
	static final double HORIZON_DEFLECT_RADIUS = 3.0;

	// ---- Singularity (C, ascended) ----
	static final double SINGULARITY_RANGE = 32.0;
	static final double SINGULARITY_PULL_RADIUS = 10.0;
	static final double SINGULARITY_PULL = 0.07;
	static final double SINGULARITY_CORE = 4.0;
	static final int SINGULARITY_TICKS = 60;
	static final int SINGULARITY_HIT_EVERY = 10;
	static final float SINGULARITY_HIT = 3.0F;
	static final float SINGULARITY_BURST = 6.0F;
	public static final int SINGULARITY_COOLDOWN = 500;

	// ---- the starter (kept for good): the pull of small things ----
	/** Loose items this close drift toward you, this hard (b/t per tick); and your jump is this much stronger. */
	static final double PULL_RADIUS = 4.0;
	static final double PULL_STRENGTH = 0.035;
	static final double PULL_JUMP = 0.06;

	private static final Identifier PULL_JUMP_ID = JaymosPowers.id("gravity_pull_jump");
	private static final Identifier LIFT_ID = JaymosPowers.id("gravity_lift");
	private static final Identifier CRUSH_ID = JaymosPowers.id("gravity_crush");
	private static final Identifier WEIGHTLESS_ID = JaymosPowers.id("gravity_weightless");
	private static final Identifier WEIGHTLESS_FALL_ID = JaymosPowers.id("gravity_weightless_fall");
	private static final Identifier COLLAPSE_ID = JaymosPowers.id("gravity_collapse");
	private static final Identifier COLLAPSE_FALL_ID = JaymosPowers.id("gravity_collapse_fall");
	private static final Identifier HORIZON_ID = JaymosPowers.id("gravity_horizon");

	/** When each cooldown ends. Synced to its owner for the HUD. */
	public static final AttachmentType<GravityCooldowns> COOLDOWNS = AttachmentRegistry.create(JaymosPowers.id("gravity_cooldowns"),
		builder -> builder.syncWith(GravityCooldowns.STREAM_CODEC, AttachmentSyncPredicate.targetOnly()));
	/** On a player: weightless right now (everyone sees the drift). */
	public static final AttachmentType<Boolean> WEIGHTLESS_ON = AttachmentRegistry.create(JaymosPowers.id("gravity_weightless"),
		builder -> builder.syncWith(ByteBufCodecs.BOOL, AttachmentSyncPredicate.all()));
	/** On a gravity user: what their Lift holds (entity id), so everyone sees the arm reach for it. */
	public static final AttachmentType<Integer> GRIP = AttachmentRegistry.create(JaymosPowers.id("gravity_grip"),
		builder -> builder.syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.all()));
	/** On a gravity user: a slam under way (when, how long the haul up, when it hit), so everyone sees the arm come down. */
	public record SlamPose(long start, int rise, long landed, boolean heavy) {
		public static final net.minecraft.network.codec.StreamCodec<io.netty.buffer.ByteBuf, SlamPose> STREAM_CODEC =
			net.minecraft.network.codec.StreamCodec.composite(ByteBufCodecs.VAR_LONG, SlamPose::start, ByteBufCodecs.VAR_INT, SlamPose::rise,
				ByteBufCodecs.VAR_LONG, SlamPose::landed, ByteBufCodecs.BOOL, SlamPose::heavy, SlamPose::new);
	}

	public static final AttachmentType<SlamPose> SLAM_POSE = AttachmentRegistry.create(JaymosPowers.id("gravity_slam_pose"),
		builder -> builder.syncWith(SlamPose.STREAM_CODEC, AttachmentSyncPredicate.all()));
	/** On a creature: held by someone's Lift (clients let it hang limp). */
	public static final AttachmentType<Boolean> LIFTED = AttachmentRegistry.create(JaymosPowers.id("gravity_lifted"),
		builder -> builder.syncWith(ByteBufCodecs.BOOL, AttachmentSyncPredicate.all()));
	/** Deaths by weight so far (toward ascension). */
	public static final AttachmentType<Integer> KILLS = AttachmentRegistry.create(JaymosPowers.id("gravity_kills"),
		builder -> builder.persistent(Codec.INT).copyOnDeath());

	/** Per player, server memory. */
	private static final class State {
		int liftTarget = -1;
		double liftHold;
		int liftTicks;
		long crushFrom = -1;
		int weightlessTicks;
		/** The held thing is a dropped item being drawn in (right click). */
		boolean drawing;
		/** It floated by NoGravity (not alive, no weight to take away); whether it had NoGravity before. */
		boolean liftWasNoGravity;
		int weightlessTotal;
		int levitating;
		boolean curedHint;
		/** Lift became a choke (right click while holding R, once R is truly yours). */
		boolean choking;
	}

	/** A creature being slammed down: who threw it, how hard, and what it can break. */
	private record Slam(ResourceKey<Level> dimension, UUID entity, UUID player, float damage, float breakHardness, long started, int rise, double down,
		int depth) {
	}

	private static final List<Slam> SLAMS = new ArrayList<>();

	private record Well(ResourceKey<Level> dimension, Vec3 at, UUID owner, long until, boolean singularity) {
	}

	private record Pinned(ResourceKey<Level> dimension, UUID entity, long until) {
	}

	private record Touch(UUID player, long at) {
	}

	private static final Map<UUID, State> STATES = new HashMap<>();
	private static final List<Well> WELLS = new ArrayList<>();
	private static final List<Pinned> PINNED = new ArrayList<>();
	private static final Map<UUID, Touch> TOUCHED = new HashMap<>();
	/** True while Crush is landing its blows (so its kills count as deaths by weight, unlike a sword's). */
	private static boolean crushing;

	private GravityPower() {
	}

	public static void init() {
		Powers.register(INSTANCE);
		JaymosPowers.LOGGER.debug("gravity attachments: {} {} {} {} {}", COOLDOWNS, WEIGHTLESS_ON, LIFTED, KILLS, GRIP, SLAM_POSE);
		net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.serverboundPlay().register(GravityGrip.TYPE, GravityGrip.CODEC);
		net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.registerGlobalReceiver(GravityGrip.TYPE, (payload, ctx) -> {
			ServerPlayer player = ctx.player();
			if (Powers.has(player, INSTANCE) && player.isAlive() && !player.isSpectator()) {
				grip(player, state(player));
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(GravityPower::serverTick);
		ServerLivingEntityEvents.AFTER_DEATH.register(GravityPower::afterDeath);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			State state = STATES.remove(handler.player.getUUID());
			if (state != null) {
				dropLift(handler.player, state, false);
			}
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			STATES.clear();
			WELLS.clear();
			PINNED.clear();
			SLAMS.clear();
			WEIGHED.clear();
			TOUCHED.clear();
		});
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> GravityDebugCommand.register(dispatcher));
	}

	@Override
	public Identifier id() {
		return ID;
	}

	@Override
	public int color() {
		return 0x8A6CFF;
	}

	@Override
	public ItemStack icon() {
		return new ItemStack(Items.HEAVY_CORE);
	}

	/** The starter, on R: the same Lift (the choke and slam come once R is truly unlocked). */
	@Override
	public PowerKey starterKey() {
		return PowerKey.ABILITY_1;
	}

	/** Lift first, then Weightless and the Well; Crush, the charged one, comes last. */
	@Override
	public PowerKey[] unlockOrder() {
		return new PowerKey[] {PowerKey.ABILITY_1, PowerKey.ABILITY_3, PowerKey.ABILITY_2, PowerKey.ABILITY_4};
	}

	private static State state(ServerPlayer player) {
		return STATES.computeIfAbsent(player.getUUID(), k -> new State());
	}

	static GravityCooldowns cooldowns(Player player) {
		GravityCooldowns cooldowns = player.getAttached(COOLDOWNS);
		return cooldowns == null ? GravityCooldowns.NONE : cooldowns;
	}

	private static boolean ready(ServerPlayer player, int ability) {
		return player.level().getGameTime() >= cooldowns(player).get(ability);
	}

	private static void cool(ServerPlayer player, int ability, int ticks) {
		player.setAttached(COOLDOWNS, cooldowns(player).with(ability, player.level().getGameTime() + ticks));
	}

	@Override
	public void onGained(ServerPlayer player) {
		player.setAttached(COOLDOWNS, GravityCooldowns.NONE);
	}

	@Override
	public void onLost(ServerPlayer player) {
		State state = STATES.remove(player.getUUID());
		if (state != null) {
			dropLift(player, state, false);
		}
		setWeightless(player, false);
		modifier(player, Attributes.GRAVITY, COLLAPSE_ID, 0.0, false);
		modifier(player, Attributes.FALL_DAMAGE_MULTIPLIER, COLLAPSE_FALL_ID, 0.0, false);
		modifier(player, Attributes.KNOCKBACK_RESISTANCE, HORIZON_ID, 0.0, false);
		AttributeInstance jump = player.getAttribute(Attributes.JUMP_STRENGTH);
		if (jump != null) {
			jump.removeModifier(PULL_JUMP_ID);
		}
		player.removeAttached(COOLDOWNS);
		player.removeAttached(KILLS);
	}

	@Override
	public void onPurified(ServerPlayer player) {
		modifier(player, Attributes.GRAVITY, COLLAPSE_ID, 0.0, false);
		modifier(player, Attributes.FALL_DAMAGE_MULTIPLIER, COLLAPSE_FALL_ID, 0.0, false);
	}

	@Override
	public boolean allowsFloating(ServerPlayer player) {
		return player.getAttached(WEIGHTLESS_ON) != null;
	}

	// ---------------------------------------------------------------- keys

	@Override
	public void onKey(ServerPlayer player, PowerKey key, boolean down) {
		State state = state(player);
		switch (key) {
			case ABILITY_1 -> {
				if (down) {
					startLift(player, state);
				} else {
					dropLift(player, state, true);
				}
			}
			case ABILITY_2 -> {
				if (down) {
					well(player);
				}
			}
			case ABILITY_3 -> {
				if (down) {
					toggleWeightless(player, state);
				}
			}
			case ABILITY_4 -> {
				if (down) {
					if (ready(player, CRUSH)) {
						state.crushFrom = player.level().getGameTime();
						player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.PLAYERS, 0.5F, 1.6F);
					}
				} else if (state.crushFrom >= 0) {
					float charge = Mth.clamp((player.level().getGameTime() - state.crushFrom) / (float)CRUSH_FULL_CHARGE, 0.0F, 1.0F);
					state.crushFrom = -1;
					crush(player, charge);
				}
			}
		}
	}

	@Override
	public void onUltimate(ServerPlayer player, boolean down) {
		if (down) {
			singularity(player);
		}
	}

	// ---------------------------------------------------------------- Lift

	private static void startLift(ServerPlayer player, State state) {
		if (state.liftTarget >= 0 || !ready(player, LIFT)) {
			return;
		}
		Entity target = liftableInSight(player, liftRange(player));
		if (target == null || !target.isAlive()) {
			fizzle(player);
			return;
		}
		state.liftTarget = target.getId();
		state.liftTicks = 0;
		state.choking = false;
		state.drawing = false;
		state.liftWasNoGravity = target.isNoGravity();
		player.setAttached(GRIP, target.getId());
		state.liftHold = Mth.clamp(player.getEyePosition().distanceTo(target.getBoundingBox().getCenter()), LIFT_MIN_HOLD, LIFT_MAX_HOLD);
		modifier(target, Attributes.GRAVITY, LIFT_ID, -1.0, true);
		if (!(target instanceof LivingEntity)) {
			// no weight to take away (an item, a cart, a corpse...), only gravity to switch off
			target.setNoGravity(true);
		}
		target.setAttached(LIFTED, true);
		if (target instanceof LivingEntity living) {
			touch(living, player);
		}
		ServerLevel level = player.level();
		level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.SHULKER_SHOOT, SoundSource.PLAYERS, 0.8F, 0.6F);
		level.sendParticles(ParticleTypes.REVERSE_PORTAL, target.getX(), target.getY(), target.getZ(), 20, 0.4, 0.1, 0.4, 0.05);
	}

	private static void tickLift(ServerPlayer player, State state) {
		Entity entity = player.level().getEntity(state.liftTarget);
		if (entity == null || !entity.isAlive() || entity.level() != player.level()
			|| ++state.liftTicks > LIFT_MAX_TICKS || !PowerInput.isDown(player, PowerKey.ABILITY_1)
			|| entity.distanceToSqr(player) > (liftRange(player) + 8.0) * (liftRange(player) + 8.0)) {
			dropLift(player, state, true);
			return;
		}
		Entity target = entity;
		if (state.choking && !(target instanceof LivingEntity)) {
			state.choking = false;
		}
		if (state.drawing && target instanceof ItemEntity item) {
			drawIn(player, state, item);
			return;
		}
		Vec3 hold = player.getEyePosition().add(player.getViewVector(1.0F).scale(state.choking ? CHOKE_DISTANCE + target.getBbWidth() * 0.5 : state.liftHold));
		if (state.choking) {
			// by the throat, held up high: its head at your raised hand
			hold = hold.subtract(0.0, target.getEyeHeight() - target.getBbHeight() * 0.5 - CHOKE_RAISE, 0.0);
			if (state.liftTicks % CHOKE_DAMAGE_EVERY == 0) {
				target.hurtServer(player.level(), player.damageSources().playerAttack(player), CHOKE_DAMAGE);
			}
		}
		Vec3 center = target.getBoundingBox().getCenter();
		Vec3 to = hold.subtract(center);
		Vec3 v = target.getDeltaMovement().scale(0.8).add(to.scale(LIFT_STEER));
		double speed = v.length();
		if (speed > LIFT_MAX_SPEED) {
			v = v.scale(LIFT_MAX_SPEED / speed);
		}
		target.setDeltaMovement(v);
		target.needsSync = true;
		target.syncVelocity = true;
		if (target instanceof Mob mob) {
			mob.getNavigation().stop();
		}
		if (target instanceof LivingEntity living) {
			touch(living, player);
		}
		player.causeFoodExhaustion(LIFT_EXHAUSTION);
		if (state.liftTicks % 4 == 0) {
			player.level().sendParticles(ParticleTypes.REVERSE_PORTAL, target.getX(), target.getY(), target.getZ(), 3, 0.3, 0.05, 0.3, 0.02);
		}
	}

	/** How far Lift reaches: further with every ability mastered. */
	static double liftRange(ServerPlayer player) {
		return Math.min(LIFT_RANGE, LIFT_RANGE_BASE + LIFT_RANGE_PER_LEVEL * com.jaymospowers.core.Mastery.unlocked(player));
	}

	/** Let go: its weight comes back (with Collapse, it's thrown where you look). */
	private static void dropLift(ServerPlayer player, State state, boolean cooldown) {
		if (state.liftTarget < 0) {
			return;
		}
		Entity entity = player.level().getEntity(state.liftTarget);
		state.liftTarget = -1;
		state.choking = false;
		player.removeAttached(GRIP);
		if (cooldown) {
			cool(player, LIFT, LIFT_COOLDOWN);
		}
		if (entity == null) {
			return;
		}
		modifier(entity, Attributes.GRAVITY, LIFT_ID, 0.0, false);
		if (!(entity instanceof LivingEntity)) {
			entity.setNoGravity(state.liftWasNoGravity);
		}
		entity.removeAttached(LIFTED);
		if (Fate.isCorrupted(player) && entity.isAlive()) {
			entity.setDeltaMovement(player.getViewVector(1.0F).scale(COLLAPSE_FLING));
			entity.needsSync = true;
			entity.syncVelocity = true;
			player.level().playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.ILLUSIONER_MIRROR_MOVE, SoundSource.PLAYERS, 0.9F, 0.5F);
		}
	}

	// ---------------------------------------------------------------- Choke and Slam

	/**
	 * Right click while holding something with Lift (R unlocked; the starter's Lift can't): the first pulls it in by the
	 * throat, the second slams it into the ground. The slam grows with the power: more damage and more weight per
	 * ability unlocked (and ascended), and from three abilities up, the block it hits breaks.
	 */
	private static void grip(ServerPlayer player, State state) {
		if (state.liftTarget < 0) {
			return;
		}
		Entity entity = player.level().getEntity(state.liftTarget);
		if (entity instanceof ItemEntity && !state.drawing) {
			// a dropped item: drawn in to you and pocketed
			state.drawing = true;
			player.level().playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.SHULKER_BULLET_HURT, SoundSource.PLAYERS, 0.6F, 1.6F);
			return;
		}
		if (!com.jaymospowers.core.Mastery.isUnlocked(player, PowerKey.ABILITY_1)) {
			return;
		}
		if (!(entity instanceof LivingEntity target) || !target.isAlive()) {
			return;
		}
		ServerLevel level = player.level();
		if (!state.choking) {
			state.choking = true;
			level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 0.8F, 0.6F);
			level.sendParticles(ParticleTypes.REVERSE_PORTAL, target.getX(), target.getEyeY(), target.getZ(), 15, 0.2, 0.2, 0.2, 0.05);
			return;
		}
		// the slam
		int abilities = com.jaymospowers.core.Mastery.unlocked(player);
		boolean ascended = Fate.isAscended(player);
		float damage = (SLAM_DAMAGE_BASE + SLAM_DAMAGE_PER_LEVEL * abilities + (ascended ? SLAM_DAMAGE_ASCENDED : 0.0F))
			* (Fate.isCorrupted(player) ? COLLAPSE_DAMAGE : 1.0F);
		float breakHardness = ascended ? SLAM_BREAK_ASCENDED : abilities >= 4 ? SLAM_BREAK_LEVEL_4 : abilities >= 3 ? SLAM_BREAK_LEVEL_3 : 0.0F;
		double weight = SLAM_WEIGHT_BASE + SLAM_WEIGHT_PER_LEVEL * abilities + (ascended ? SLAM_WEIGHT_PER_LEVEL : 0.0);
		// let go of it (no throw), then drive it down
		state.choking = false;
		dropLiftQuietly(player, state, target);
		touch(target, player);
		modifier(target, Attributes.GRAVITY, SLAM_ID, weight, true);
		int rise = Math.max(SLAM_RISE_MIN, SLAM_RISE_TICKS - abilities - (ascended ? 1 : 0));
		double down = SLAM_SPEED + SLAM_SPEED_PER_LEVEL * (abilities + (ascended ? 1 : 0));
		target.setDeltaMovement(0.0, SLAM_RISE_SPEED, 0.0);
		target.needsSync = true;
		target.syncVelocity = true;
		SLAMS.removeIf(s -> s.entity().equals(target.getUUID()));
		float three = BURY_3_BASE + BURY_3_PER_LEVEL * (abilities + (ascended ? 1 : 0));
		float roll = level.getRandom().nextFloat();
		int depth = roll < three ? 3 : roll < three + (1.0F - three) * 0.5F ? 2 : 1;
		SLAMS.add(new Slam(level.dimension(), target.getUUID(), player.getUUID(), damage, breakHardness, level.getGameTime(), rise, down, depth));
		// something bigger than a cow takes the whole body: both arms up, then bent double to drive it down
		boolean heavy = target.getBbWidth() * target.getBbHeight() > HEAVY_SLAM_SIZE;
		player.setAttached(SLAM_POSE, new SlamPose(level.getGameTime(), rise, -1L, heavy));
		level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.WARDEN_ATTACK_IMPACT, SoundSource.PLAYERS, 1.0F, 0.6F);
	}

	/** A held item flies to you; once close it goes into your inventory (what doesn't fit drops at your feet). */
	private static void drawIn(ServerPlayer player, State state, ItemEntity item) {
		Vec3 chest = player.position().add(0.0, player.getBbHeight() * 0.6, 0.0);
		Vec3 to = chest.subtract(item.position());
		if (to.length() > DRAW_REACH) {
			item.setDeltaMovement(to.normalize().scale(Math.min(DRAW_SPEED, to.length())));
			item.needsSync = true;
			item.syncVelocity = true;
			if (state.liftTicks % 2 == 0) {
				player.level().sendParticles(ParticleTypes.REVERSE_PORTAL, item.getX(), item.getY() + 0.1, item.getZ(), 2, 0.05, 0.05, 0.05, 0.0);
			}
			return;
		}
		ItemStack stack = item.getItem().copy();
		int before = stack.getCount();
		player.getInventory().add(stack);
		int taken = before - stack.getCount();
		state.drawing = false;
		dropLift(player, state, true);
		if (taken > 0) {
			player.take(item, taken);
			player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.4F, 1.4F);
		}
		if (stack.isEmpty()) {
			item.discard();
		} else {
			// no room for the rest: it drops at your feet
			item.setItem(stack);
			item.setPos(player.getX(), player.getY() + 0.2, player.getZ());
			item.setDeltaMovement(Vec3.ZERO);
			item.needsSync = true;
		}
	}

	/** Anything Lift can take: whatever's under the crosshair (small things like dropped items count, with a little slack). */
	private static @Nullable Entity liftableInSight(ServerPlayer player, double range) {
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getViewVector(1.0F);
		Vec3 end = eye.add(look.scale(range));
		BlockHitResult block = player.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
		if (block.getType() != HitResult.Type.MISS) {
			end = block.getLocation();
		}
		Entity best = null;
		double bestDistance = Double.MAX_VALUE;
		for (Entity e : player.level().getEntities(player, player.getBoundingBox().expandTowards(look.scale(range)).inflate(1.0),
			e -> !e.isSpectator() && e != player.getVehicle() && !(e instanceof net.minecraft.world.entity.Marker)
				&& !(e instanceof net.minecraft.world.entity.Display) && !(e instanceof net.minecraft.world.entity.LightningBolt)
				&& !(e instanceof net.minecraft.world.entity.AreaEffectCloud) && !player.isPassengerOfSameVehicle(e))) {
			AABB box = e.getBoundingBox().inflate(Math.max(0.3, e.getPickRadius()));
			java.util.Optional<Vec3> hit = box.contains(eye) ? java.util.Optional.of(eye) : box.clip(eye, end);
			if (hit.isPresent()) {
				double d = eye.distanceToSqr(hit.get());
				if (d < bestDistance) {
					bestDistance = d;
					best = e;
				}
			}
		}
		return best;
	}

	/** Lift ends for a slam: weight back on, cooldown, no Collapse fling. */
	private static void dropLiftQuietly(ServerPlayer player, State state, Entity target) {
		state.liftTarget = -1;
		player.removeAttached(GRIP);
		cool(player, LIFT, LIFT_COOLDOWN);
		modifier(target, Attributes.GRAVITY, LIFT_ID, 0.0, false);
		target.removeAttached(LIFTED);
	}

	/** Slams land: on the ground (or after a moment), the damage and the crater. */
	private static void tickSlams(MinecraftServer server) {
		for (Iterator<Slam> it = SLAMS.iterator(); it.hasNext(); ) {
			Slam slam = it.next();
			ServerLevel level = server.getLevel(slam.dimension());
			Entity entity = level == null ? null : level.getEntity(slam.entity());
			if (!(entity instanceof LivingEntity target) || !target.isAlive()) {
				it.remove();
				slamPoseLanded(server, slam, level);
				continue;
			}
			long age = level.getGameTime() - slam.started();
			if (age < slam.rise()) {
				// hauled up into the air...
				target.setDeltaMovement(0.0, SLAM_RISE_SPEED, 0.0);
				target.needsSync = true;
				target.syncVelocity = true;
				continue;
			}
			if (age == slam.rise()) {
				// ...and driven into the ground
				target.setDeltaMovement(0.0, -slam.down(), 0.0);
				target.needsSync = true;
				target.syncVelocity = true;
				level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.PLAYERS, 0.6F, 2.0F);
			} else if (!target.onGround()) {
				Vec3 v = target.getDeltaMovement();
				if (v.y > -slam.down()) {
					target.setDeltaMovement(v.x * 0.5, -slam.down(), v.z * 0.5);
					target.syncVelocity = true;
				}
			}
			boolean landed = age > slam.rise() + 1 && (target.onGround() || target.verticalCollision || target.isInWater());
			if (!landed && age < SLAM_MAX_TICKS + slam.rise()) {
				if (age % 2 == 0) {
					level.sendParticles(ParticleTypes.REVERSE_PORTAL, target.getX(), target.getY() + target.getBbHeight(), target.getZ(), 3, 0.2, 0.1, 0.2, 0.02);
				}
				continue;
			}
			it.remove();
			slamPoseLanded(server, slam, level);
			modifier(target, Attributes.GRAVITY, SLAM_ID, 0.0, false);
			ServerPlayer player = server.getPlayerList().getPlayer(slam.player());
			boolean grounded = !target.isInWater() && (target.onGround() || target.verticalCollision);
			DamageSource source = player != null ? level.damageSources().playerAttack(player) : level.damageSources().fall();
			target.setInvulnerableTime(0);
			crushing = true;
			try {
				target.hurtServer(level, source, slam.damage());
			} finally {
				crushing = false;
			}
			BlockPos under = BlockPos.containing(target.getX(), target.getY() - 0.2, target.getZ());
			BlockState hit = level.getBlockState(under);
			BlockState shown = hit.isAir() ? net.minecraft.world.level.block.Blocks.DIRT.defaultBlockState() : hit;
			// the ground all around kicks up
			impactRing(level, under, target);
			if (grounded && target.isAlive()) {
				bury(level, target, slam.depth(), Math.max(BURY_HARDNESS, slam.breakHardness()));
			}
			// the heart of the impact: the biggest puff, the colour of what it hit
			com.jaymospowers.core.Debris.puff(level, under, shown, IMPACT_DEBRIS * 1.6F);
			level.sendParticles(ParticleTypes.EXPLOSION, target.getX(), target.getY(), target.getZ(), 1, 0.0, 0.0, 0.0, 0.0);
			level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.MACE_SMASH_GROUND_HEAVY, SoundSource.PLAYERS, 1.3F, 0.7F);
		}
	}


	/** The slam has hit (or failed): the thrower's arm stays down a moment, then relaxes (see SlamPose). */
	private static void slamPoseLanded(MinecraftServer server, Slam slam, ServerLevel level) {
		ServerPlayer player = server.getPlayerList().getPlayer(slam.player());
		SlamPose pose = player == null ? null : player.getAttached(SLAM_POSE);
		if (pose != null && pose.start() == slam.started()) {
			player.setAttached(SLAM_POSE, new SlamPose(pose.start(), pose.rise(), level.getGameTime(), pose.heavy()));
		}
	}

	/**
	 * Driven into the ground: the blocks under it give way, layer by layer (as deep as {@code depth}, stopping at one
	 * too hard, a block entity or a liquid), and it sinks into the hole, stuck there.
	 */
	private static void bury(ServerLevel level, LivingEntity target, int depth, float hardness) {
		AABB box = target.getBoundingBox();
		int minX = Mth.floor(box.minX + 0.05), maxX = Mth.floor(box.maxX - 0.05);
		int minZ = Mth.floor(box.minZ + 0.05), maxZ = Mth.floor(box.maxZ - 0.05);
		int top = Mth.floor(target.getY() - 0.2);
		int sunk = 0;
		for (int d = 0; d < depth; d++) {
			int y = top - d;
			boolean gives = true;
			for (int x = minX; x <= maxX && gives; x++) {
				for (int z = minZ; z <= maxZ && gives; z++) {
					BlockPos pos = new BlockPos(x, y, z);
					BlockState state = level.getBlockState(pos);
					if (state.isAir()) {
						continue;
					}
					float h = state.getDestroySpeed(level, pos);
					gives = state.getFluidState().isEmpty() && !state.hasBlockEntity() && h >= 0.0F && h <= hardness;
				}
			}
			if (!gives) {
				break;
			}
			for (int x = minX; x <= maxX; x++) {
				for (int z = minZ; z <= maxZ; z++) {
					BlockPos pos = new BlockPos(x, y, z);
					BlockState broken = level.getBlockState(pos);
					if (!broken.isAir()) {
						level.destroyBlock(pos, false);
						com.jaymospowers.core.Debris.puff(level, pos, broken, BURY_DEBRIS);
					}
				}
			}
			sunk++;
		}
		if (sunk == 0) {
			return;
		}
		// feet on the floor of the hole
		target.teleportTo(target.getX(), top - sunk + 1.0, target.getZ());
		target.setDeltaMovement(Vec3.ZERO);
		target.needsSync = true;
		target.syncVelocity = true;
		level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.ROOTED_DIRT_BREAK, SoundSource.PLAYERS, 1.2F, 0.5F);
	}

	/** Debris thrown up from every bit of ground in a ring around the impact, a big block-coloured puff each. */
	private static void impactRing(ServerLevel level, BlockPos center, LivingEntity target) {
		com.jaymospowers.core.Debris.ring(level, center, IMPACT_RING + 1, IMPACT_DEBRIS);
	}

	// ---------------------------------------------------------------- Weightless

	private static void toggleWeightless(ServerPlayer player, State state) {
		if (player.getAttached(WEIGHTLESS_ON) != null) {
			endWeightless(player, state, WEIGHTLESS_EARLY_COOLDOWN);
			return;
		}
		if (!ready(player, WEIGHTLESS)) {
			return;
		}
		state.weightlessTicks = 0;
		setWeightless(player, true);
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.7F, 1.6F);
		player.level().sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 0.1, player.getZ(), 12, 0.4, 0.05, 0.4, 0.02);
	}

	private static void endWeightless(ServerPlayer player, State state, int cooldown) {
		setWeightless(player, false);
		cool(player, WEIGHTLESS, cooldown);
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.6F, 1.4F);
	}

	private static void setWeightless(ServerPlayer player, boolean on) {
		modifier(player, Attributes.GRAVITY, WEIGHTLESS_ID, WEIGHTLESS_GRAVITY, on);
		modifier(player, Attributes.FALL_DAMAGE_MULTIPLIER, WEIGHTLESS_FALL_ID, WEIGHTLESS_FALL, on);
		if (on) {
			player.setAttached(WEIGHTLESS_ON, true);
		} else {
			player.removeAttached(WEIGHTLESS_ON);
		}
	}

	// ---------------------------------------------------------------- Gravity Well

	private static void well(ServerPlayer player) {
		if (!ready(player, WELL)) {
			return;
		}
		Vec3 at = aimPoint(player, WELL_RANGE);
		WELLS.add(new Well(player.level().dimension(), at, player.getUUID(), player.level().getGameTime() + WELL_TICKS, false));
		cool(player, WELL, WELL_COOLDOWN);
		player.level().playSound(null, at.x, at.y, at.z, SoundEvents.PORTAL_TRIGGER, SoundSource.PLAYERS, 0.5F, 1.8F);
	}

	// ---------------------------------------------------------------- Crush

	private static void crush(ServerPlayer player, float charge) {
		if (!ready(player, CRUSH)) {
			return;
		}
		ServerLevel level = player.level();
		boolean collapsed = Fate.isCorrupted(player);
		Vec3 at = aimPoint(player, CRUSH_RANGE);
		double radius = Mth.lerp(charge, CRUSH_RADIUS_MIN, CRUSH_RADIUS_MAX) + (collapsed ? COLLAPSE_RADIUS_BONUS : 0.0);
		float damage = Mth.lerp(charge, CRUSH_DAMAGE_MIN, CRUSH_DAMAGE_MAX) * (collapsed ? COLLAPSE_DAMAGE : 1.0F);
		AABB area = new AABB(at, at).inflate(radius, radius + 3.0, radius);
		for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, area,
			e -> e != player && e.isAlive() && !e.isSpectator() && !(e instanceof ArmorStand))) {
			if (target.getBoundingBox().getCenter().distanceToSqr(at) > (radius + 1.5) * (radius + 1.5)) {
				continue;
			}
			touch(target, player);
			Vec3 v = target.getDeltaMovement();
			target.setDeltaMovement(v.x * 0.3, -CRUSH_SLAM * (0.4 + charge), v.z * 0.3);
			target.needsSync = true;
			target.syncVelocity = true;
			crushing = true;
			try {
				target.hurtServer(level, level.damageSources().playerAttack(player), damage);
			} finally {
				crushing = false;
			}
			target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, CRUSH_PIN_TICKS, 1), player);
			modifier(target, Attributes.GRAVITY, CRUSH_ID, CRUSH_WEIGHT, true);
			PINNED.removeIf(p -> p.entity().equals(target.getUUID()));
			PINNED.add(new Pinned(level.dimension(), target.getUUID(), level.getGameTime() + CRUSH_PIN_TICKS));
		}
		// a ring slamming outward along the ground
		for (int i = 0; i < 24; i++) {
			double a = i / 24.0 * Math.PI * 2.0;
			level.sendParticles(ParticleTypes.EXPLOSION, at.x + Math.cos(a) * radius * 0.7, at.y + 0.2, at.z + Math.sin(a) * radius * 0.7, 1, 0.0, 0.0, 0.0, 0.0);
		}
		level.sendParticles(ParticleTypes.SQUID_INK, at.x, at.y + 0.5, at.z, 30, radius * 0.4, 0.3, radius * 0.4, 0.05);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.MACE_SMASH_GROUND_HEAVY, SoundSource.PLAYERS, 1.2F, 0.6F);
		int cooldown = Math.round(Mth.lerp(charge, CRUSH_COOLDOWN_MIN, CRUSH_COOLDOWN_MAX));
		player.setAttached(COOLDOWNS, cooldowns(player).withCrush(level.getGameTime() + cooldown, cooldown));
	}

	// ---------------------------------------------------------------- Singularity (C)

	private static void singularity(ServerPlayer player) {
		if (!ready(player, SINGULARITY)) {
			return;
		}
		Vec3 at = aimPoint(player, SINGULARITY_RANGE).add(0.0, 1.0, 0.0);
		WELLS.add(new Well(player.level().dimension(), at, player.getUUID(), player.level().getGameTime() + SINGULARITY_TICKS, true));
		cool(player, SINGULARITY, SINGULARITY_COOLDOWN);
		player.level().playSound(null, at.x, at.y, at.z, SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.PLAYERS, 1.5F, 0.5F);
	}

	// ---------------------------------------------------------------- ticking

	@Override
	public void tick(ServerPlayer player) {
		if (!player.isAlive() || player.isSpectator()) {
			State state = STATES.get(player.getUUID());
			if (state != null) {
				dropLift(player, state, false);
			}
			if (player.getAttached(WEIGHTLESS_ON) != null) {
				setWeightless(player, false);
			}
			return;
		}
		State state = state(player);
		smallThings(player);
		if (state.liftTarget >= 0) {
			tickLift(player, state);
		}
		if (state.crushFrom >= 0 && player.tickCount % HEAVY_AURA_REFRESH == 0) {
			crushZone(player, state);
		}
		if (player.getAttached(WEIGHTLESS_ON) != null) {
			state.weightlessTicks++;
			if (player.tickCount % HEAVY_AURA_REFRESH == 0) {
				heavyAura(player);
			}
			player.causeFoodExhaustion(WEIGHTLESS_EXHAUSTION);
			if (++state.weightlessTotal >= CORRUPT_WEIGHTLESS_TICKS) {
				state.weightlessTotal = 0;
				Fate.addCorruption(player, 1);
			}
			if (state.weightlessTicks >= WEIGHTLESS_MAX_TICKS || player.isInWater() || player.getFoodData().getFoodLevel() <= 0) {
				endWeightless(player, state, WEIGHTLESS_COOLDOWN);
			}
		}
		boolean collapsed = Fate.isCorrupted(player);
		boolean ascended = Fate.isAscended(player);
		if (player.tickCount % 20 == 0) {
			// the passive weights (re-applied, since attributes don't remember transient modifiers through death)
			modifier(player, Attributes.GRAVITY, COLLAPSE_ID, COLLAPSE_GRAVITY, collapsed);
			modifier(player, Attributes.FALL_DAMAGE_MULTIPLIER, COLLAPSE_FALL_ID, COLLAPSE_FALL, collapsed);
			modifier(player, Attributes.KNOCKBACK_RESISTANCE, HORIZON_ID, 1.0, ascended);
		}
		if (collapsed) {
			tickCollapse(player, state);
		}
		if (ascended && player.tickCount % 2 == 0) {
			deflect(player);
		}
	}

	/**
	 * The starter, kept for good: the pull of small things. Dropped items nearby drift slowly toward you, and you jump
	 * a little higher than you should.
	 */
	private static void smallThings(ServerPlayer player) {
		AttributeInstance jump = player.getAttribute(Attributes.JUMP_STRENGTH);
		if (jump != null && !jump.hasModifier(PULL_JUMP_ID)) {
			jump.addTransientModifier(new AttributeModifier(PULL_JUMP_ID, PULL_JUMP, AttributeModifier.Operation.ADD_VALUE));
		}
		if (player.tickCount % 2 != 0) {
			return;
		}
		Vec3 me = player.position().add(0.0, 0.5, 0.0);
		boolean pulled = false;
		for (ItemEntity item : player.level().getEntitiesOfClass(ItemEntity.class, player.getBoundingBox().inflate(PULL_RADIUS),
			e -> e.isAlive() && !e.hasPickUpDelay())) {
			Vec3 to = me.subtract(item.position());
			double d = to.length();
			if (d > 0.5 && d <= PULL_RADIUS) {
				item.setDeltaMovement(item.getDeltaMovement().add(to.scale(PULL_STRENGTH * 2.0 / d)));
				item.needsSync = true;
				pulled = true;
			}
		}
		if (pulled && player.tickCount % 40 == 0) {
			com.jaymospowers.core.Mastery.used(player);
		}
	}

	/** Collapse: loose things drift in; and the cure, held up by a shulker's levitation long enough. */
	private static void tickCollapse(ServerPlayer player, State state) {
		ServerLevel level = player.level();
		if (player.tickCount % 4 == 0) {
			Vec3 me = player.position().add(0.0, 1.0, 0.0);
			AABB area = player.getBoundingBox().inflate(COLLAPSE_DRIFT_RADIUS);
			for (Entity e : level.getEntities(player, area, e -> e instanceof ItemEntity
				|| e instanceof LivingEntity living && living.getMaxHealth() <= 10.0F && !(e instanceof Player) && living.isAlive())) {
				Vec3 to = me.subtract(e.position());
				double d = to.length();
				if (d > 0.8 && d < COLLAPSE_DRIFT_RADIUS) {
					e.setDeltaMovement(e.getDeltaMovement().add(to.scale(COLLAPSE_DRIFT * 4.0 / d)));
					e.needsSync = true;
				}
			}
			if (player.tickCount % 12 == 0) {
				level.sendParticles(ParticleTypes.SQUID_INK, player.getX(), player.getY() + 1.0, player.getZ(), 2, 0.6, 0.6, 0.6, 0.01);
			}
		}
		if (player.hasEffect(MobEffects.LEVITATION)) {
			if (!state.curedHint) {
				state.curedHint = true;
				player.sendOverlayMessage(Component.translatable("message.jaymospowers.gravity_cure_hint").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
			}
			if (++state.levitating >= CURE_LEVITATION_TICKS) {
				state.levitating = 0;
				Fate.purify(player);
			}
		} else {
			state.levitating = 0;
		}
	}

	/** Event Horizon: projectiles that come too close are bent away. */
	private static void deflect(ServerPlayer player) {
		Vec3 me = player.getBoundingBox().getCenter();
		for (Projectile projectile : player.level().getEntitiesOfClass(Projectile.class, player.getBoundingBox().inflate(HORIZON_DEFLECT_RADIUS),
			p -> p.isAlive() && p.getOwner() != player)) {
			Vec3 away = projectile.position().subtract(me);
			double d = away.length();
			if (d < 0.01) {
				continue;
			}
			Vec3 v = projectile.getDeltaMovement();
			projectile.setDeltaMovement(v.add(away.scale(0.5 / d)).scale(0.8));
			projectile.needsSync = true;
		}
	}

	/** Weightless: the weight you shed lands on everyone around you. */
	private static void heavyAura(ServerPlayer player) {
		weighDown(player, player.position(), HEAVY_AURA_RADIUS);
	}

	/**
	 * Crush being charged (B held): the ground it'll fall on already pulls. Everything within the blast (at the size the
	 * charge has reached) is weighed down like Weightless's aura, until the crush comes.
	 */
	private static void crushZone(ServerPlayer player, State state) {
		float charge = Mth.clamp((player.level().getGameTime() - state.crushFrom) / (float)CRUSH_FULL_CHARGE, 0.0F, 1.0F);
		Vec3 at = aimPoint(player, CRUSH_RANGE);
		double radius = Mth.lerp(charge, CRUSH_RADIUS_MIN, CRUSH_RADIUS_MAX) + (Fate.isCorrupted(player) ? COLLAPSE_RADIUS_BONUS : 0.0);
		weighDown(player, at, radius);
	}

	/** Everyone (but the user) within {@code radius} of {@code center} is heavy for a moment (refresh it to keep them so). */
	private static void weighDown(ServerPlayer player, Vec3 center, double radius) {
		ServerLevel level = player.level();
		long until = level.getGameTime() + HEAVY_AURA_REFRESH * 2L + 1;
		for (LivingEntity living : level.getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(radius, radius + 2.0, radius),
			e -> e != player && e.isAlive() && !e.isSpectator() && !(e instanceof ArmorStand)
				&& e.position().distanceToSqr(center) <= radius * radius)) {
			modifier(living, Attributes.GRAVITY, HEAVY_AURA_ID, HEAVY_AURA_WEIGHT, true);
			AttributeInstance jump = living.getAttribute(Attributes.JUMP_STRENGTH);
			if (jump != null && !jump.hasModifier(HEAVY_AURA_JUMP_ID)) {
				jump.addOrUpdateTransientModifier(new AttributeModifier(HEAVY_AURA_JUMP_ID, HEAVY_AURA_JUMP, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
			}
			living.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, HEAVY_AURA_REFRESH * 2 + 4, 0, false, false, true), player);
			WEIGHED.put(living.getUUID(), new Weighed(level.dimension(), living.getUUID(), until));
			if (level.getRandom().nextInt(4) == 0) {
				level.sendParticles(ParticleTypes.REVERSE_PORTAL, living.getX(), living.getY() + 0.1, living.getZ(), 3, 0.3, 0.05, 0.3, 0.0);
			}
		}
	}

	private static void serverTick(MinecraftServer server) {
		if (!SLAMS.isEmpty()) {
			tickSlams(server);
		}
		if (!WEIGHED.isEmpty()) {
			for (Iterator<Weighed> it = WEIGHED.values().iterator(); it.hasNext(); ) {
				Weighed weighed = it.next();
				ServerLevel level = server.getLevel(weighed.dimension());
				if (level != null && level.getGameTime() < weighed.until()) {
					continue;
				}
				it.remove();
				Entity entity = level == null ? null : level.getEntity(weighed.entity());
				if (entity instanceof LivingEntity living) {
					modifier(living, Attributes.GRAVITY, HEAVY_AURA_ID, 0.0, false);
					AttributeInstance jump = living.getAttribute(Attributes.JUMP_STRENGTH);
					if (jump != null) {
						jump.removeModifier(HEAVY_AURA_JUMP_ID);
					}
				}
			}
		}
		if (!WELLS.isEmpty()) {
			for (Iterator<Well> it = WELLS.iterator(); it.hasNext(); ) {
				Well well = it.next();
				ServerLevel level = server.getLevel(well.dimension());
				if (level == null) {
					it.remove();
					continue;
				}
				long left = well.until() - level.getGameTime();
				ServerPlayer owner = server.getPlayerList().getPlayer(well.owner());
				if (left <= 0) {
					it.remove();
					if (well.singularity()) {
						burst(level, well, owner);
					}
					continue;
				}
				tickWell(level, well, owner, left);
			}
		}
		if (!PINNED.isEmpty()) {
			for (Iterator<Pinned> it = PINNED.iterator(); it.hasNext(); ) {
				Pinned pinned = it.next();
				ServerLevel level = server.getLevel(pinned.dimension());
				Entity entity = level == null ? null : level.getEntity(pinned.entity());
				if (entity == null || level.getGameTime() >= pinned.until()) {
					it.remove();
					if (entity != null) {
						modifier(entity, Attributes.GRAVITY, CRUSH_ID, 0.0, false);
					}
				}
			}
		}
		if (server.getTickCount() % 100 == 0 && !TOUCHED.isEmpty()) {
			long now = server.overworld().getGameTime();
			TOUCHED.values().removeIf(t -> now - t.at() > TOUCH_MEMORY_TICKS);
		}
	}

	private static void tickWell(ServerLevel level, Well well, @Nullable ServerPlayer owner, long left) {
		double radius = well.singularity() ? SINGULARITY_PULL_RADIUS : WELL_RADIUS;
		double pull = well.singularity() ? SINGULARITY_PULL : WELL_PULL;
		if (owner != null && Fate.isCorrupted(owner)) {
			pull *= COLLAPSE_DAMAGE;
		}
		Vec3 at = well.at();
		AABB area = new AABB(at, at).inflate(radius);
		boolean hit = well.singularity() && left % SINGULARITY_HIT_EVERY == 0;
		for (Entity e : level.getEntities((Entity)null, area, e -> e.isAlive() && !e.isSpectator() && !(e instanceof ArmorStand)
			&& (owner == null || e != owner) && !(e instanceof Player p && (p.isCreative() || p.isSpectator())))) {
			Vec3 to = at.subtract(e.getBoundingBox().getCenter());
			double d = to.length();
			if (d > radius || d < 0.05) {
				continue;
			}
			double strength = pull * (0.5 + (1.0 - d / radius));
			e.setDeltaMovement(e.getDeltaMovement().scale(0.9).add(to.scale(strength / d)));
			e.needsSync = true;
			e.syncVelocity = true;
			if (e instanceof LivingEntity living) {
				if (owner != null) {
					touch(living, owner);
				}
				if (hit && d <= SINGULARITY_CORE) {
					DamageSource source = owner != null ? level.damageSources().indirectMagic(owner, owner) : level.damageSources().magic();
					living.setInvulnerableTime(0);
					living.hurtServer(level, source, SINGULARITY_HIT);
				}
			}
		}
		if (well.singularity()) {
			level.sendParticles(ParticleTypes.SQUID_INK, at.x, at.y, at.z, 6, 0.3, 0.3, 0.3, 0.02);
			level.sendParticles(ParticleTypes.PORTAL, at.x, at.y, at.z, 16, radius * 0.3, radius * 0.3, radius * 0.3, -1.0);
			if (left % 20 == 0) {
				level.playSound(null, at.x, at.y, at.z, SoundEvents.RESPAWN_ANCHOR_DEPLETE.value(), SoundSource.PLAYERS, 1.0F, 0.5F);
			}
		} else if (left % 2 == 0) {
			// a slow spiral drawn inward
			double a = left * 0.5;
			for (int i = 0; i < 3; i++) {
				double r = radius * (0.3 + 0.2 * i);
				double ang = a + i * 2.1;
				level.sendParticles(ParticleTypes.REVERSE_PORTAL, at.x + Math.cos(ang) * r, at.y + 0.3 * i, at.z + Math.sin(ang) * r, 1, 0.0, 0.0, 0.0, 0.0);
			}
			level.sendParticles(ParticleTypes.PORTAL, at.x, at.y, at.z, 4, 0.2, 0.2, 0.2, -0.6);
		}
	}

	/** The Singularity's end: it snaps open and throws everything out. */
	private static void burst(ServerLevel level, Well well, @Nullable ServerPlayer owner) {
		Vec3 at = well.at();
		for (LivingEntity living : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(SINGULARITY_CORE + 1.0),
			e -> e.isAlive() && !e.isSpectator() && e != owner && !(e instanceof ArmorStand))) {
			Vec3 away = living.getBoundingBox().getCenter().subtract(at);
			double d = Math.max(0.3, away.length());
			living.setInvulnerableTime(0);
			DamageSource source = owner != null ? level.damageSources().indirectMagic(owner, owner) : level.damageSources().magic();
			living.hurtServer(level, source, SINGULARITY_BURST);
			living.setDeltaMovement(away.scale(1.3 / d).add(0.0, 0.5, 0.0));
			living.needsSync = true;
			living.syncVelocity = true;
		}
		level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
		level.sendParticles(ParticleTypes.REVERSE_PORTAL, at.x, at.y, at.z, 80, 1.5, 1.5, 1.5, 0.6);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 1.2F, 0.6F);
	}

	// ---------------------------------------------------------------- deaths by weight

	private static void touch(LivingEntity target, ServerPlayer player) {
		TOUCHED.put(target.getUUID(), new Touch(player.getUUID(), player.level().getGameTime()));
	}

	private static void afterDeath(LivingEntity entity, DamageSource source) {
		Touch touch = TOUCHED.remove(entity.getUUID());
		if (touch == null || !(entity.level() instanceof ServerLevel level) || level.getGameTime() - touch.at() > TOUCH_MEMORY_TICKS) {
			return;
		}
		// it died to the weight: a fall, being crushed into the ground, or the Singularity's magic
		boolean byWeight = source.is(DamageTypeTags.IS_FALL) || crushing
			|| source.is(net.minecraft.world.damagesource.DamageTypes.INDIRECT_MAGIC) && source.getEntity() != null
				&& source.getEntity().getUUID().equals(touch.player());
		ServerPlayer player = level.getServer().getPlayerList().getPlayer(touch.player());
		if (!byWeight || player == null || !Powers.has(player, INSTANCE) || entity == player) {
			return;
		}
		Fate.addCorruption(player, CORRUPT_PER_KILL);
		if (!Fate.canAscend(player)) {
			return;
		}
		int kills = kills(player) + 1;
		player.setAttached(KILLS, kills);
		if (kills == ASCEND_KILLS / 3 || kills == ASCEND_KILLS * 2 / 3) {
			player.sendOverlayMessage(Component.translatable(kills == ASCEND_KILLS / 3 ? "message.jaymospowers.gravity_ascend.0" : "message.jaymospowers.gravity_ascend.1")
				.withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
		}
		if (kills >= ASCEND_KILLS) {
			Fate.ascend(player);
		}
	}

	public static int kills(Player player) {
		Integer kills = player.getAttached(KILLS);
		return kills == null ? 0 : kills;
	}

	static void setKills(ServerPlayer player, int kills) {
		player.setAttached(KILLS, kills);
	}

	// ---------------------------------------------------------------- helpers

	private static void modifier(Entity entity, Holder<Attribute> attribute, Identifier id, double amount, boolean on) {
		if (!(entity instanceof LivingEntity living)) {
			return;
		}
		AttributeInstance instance = living.getAttribute(attribute);
		if (instance == null) {
			return;
		}
		if (on) {
			AttributeModifier.Operation op = attribute == Attributes.KNOCKBACK_RESISTANCE ? AttributeModifier.Operation.ADD_VALUE
				: AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL;
			AttributeModifier current = instance.getModifier(id);
			if (current == null || current.amount() != amount) {
				instance.addOrUpdateTransientModifier(new AttributeModifier(id, amount, op));
			}
		} else if (instance.hasModifier(id)) {
			instance.removeModifier(id);
		}
	}

	/** Where the crosshair lands (a block, a creature, or open air at {@code range}). */
	private static Vec3 aimPoint(ServerPlayer player, double range) {
		Vec3 eye = player.getEyePosition();
		Vec3 end = eye.add(player.getViewVector(1.0F).scale(range));
		BlockHitResult block = player.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
		Entity entity = entityInSight(player, range);
		if (entity != null && (block.getType() == HitResult.Type.MISS || entity.distanceToSqr(eye) < block.getLocation().distanceToSqr(eye))) {
			return entity.position();
		}
		return block.getType() == HitResult.Type.MISS ? end : block.getLocation();
	}

	private static @Nullable Entity entityInSight(ServerPlayer player, double range) {
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getViewVector(1.0F);
		Vec3 end = eye.add(look.scale(range));
		BlockHitResult block = player.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
		if (block.getType() != HitResult.Type.MISS) {
			end = block.getLocation();
		}
		AABB box = player.getBoundingBox().expandTowards(look.scale(range)).inflate(1.0);
		EntityHitResult hit = ProjectileUtil.getEntityHitResult(player, eye, end, box,
			e -> !e.isSpectator() && e.isPickable() && e != player.getVehicle(), eye.distanceToSqr(end));
		return hit == null ? null : hit.getEntity();
	}

	/** Jaymo's Kitchen's corpses (not living, but Lift can take them too). */
	public static boolean isCorpse(Entity entity) {
		net.minecraft.resources.Identifier type = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
		return type.getNamespace().equals("jaymoskitchen") && type.getPath().contains("corpse");
	}

	private static void fizzle(ServerPlayer player) {
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SHULKER_BULLET_HIT, SoundSource.PLAYERS, 0.4F, 1.6F);
	}
}
