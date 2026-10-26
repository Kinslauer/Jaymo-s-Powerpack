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
 * <li>B, Gravity Wave (hold to charge): a fan of gravitic pressure rolls out the way you look, hurting, throwing and
 * slowing what it passes and carving up the ground ({@link GravityWave}). A tap is still the force kneel around you.
 * B while Lift holds a creature (R+B): Inversion, yanked in for a gut punch that bleeds it inside
 * ({@link GravityGutPunch}).</li>
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
	public static final double LIFT_RANGE = 60.0;
	/** Lift's reach grows with the power: {@code LIFT_RANGE_BASE} plus this per ability mastered, up to {@code LIFT_RANGE}. */
	static final double LIFT_RANGE_BASE = 44.0, LIFT_RANGE_PER_LEVEL = 4.0;
	/**
	 * How forgiving Lift's aim is on a player (blocks added round them), growing this much per block away so a far
	 * one is still easy to catch. Anything else gets {@code LIFT_SLACK}.
	 */
	static final double LIFT_SLACK = 0.3, LIFT_PLAYER_SLACK = 0.9, LIFT_PLAYER_SLACK_PER_BLOCK = 0.012;
	/** Held distance is clamped to this range (blocks from the eyes). */
	static final double LIFT_MIN_HOLD = 3.0, LIFT_MAX_HOLD = 14.0;
	/** How hard a lifted creature is steered toward the held point, and its top speed (b/t). */
	static final double LIFT_STEER = 0.22, LIFT_MAX_SPEED = 0.7;
	/** Longest a lift lasts, and the cooldown after it ends. */
	public static final int LIFT_MAX_TICKS = 120;
	public static final int LIFT_COOLDOWN = 160;
	static final float LIFT_EXHAUSTION = 0.012F;

	// ---- Gravity Well (G) ----
	/**
	 * The well goes off around you (no longer aimed) and stays on you for {@code WELL_TICKS}: everything that touches
	 * it ({@code WELL_RADIUS}, the bubble's size) is heavy and slowed and forced to its knees. It no longer pulls.
	 */
	static final double WELL_RADIUS = 8.0;
	static final int WELL_TICKS = 80;
	/** A tap of B while your well is up: you're launched straight up this high (blocks), once per well. */
	static final double WELL_LAUNCH_HEIGHT = 14.5;
	/** B let go within this many ticks is a tap: the force kneel around you (in your well, with the launch). */
	static final int CRUSH_TAP_TICKS = 6;
	/** How far the tap's force kneel reaches around you (blocks). */
	static final double CRUSH_TAP_RADIUS = 5.0;
	public static final int WELL_COOLDOWN = 280;

	// ---- Weightless (V) ----
	/** Your gravity while weightless (fraction removed), and how much lighter you land. */
	static final double WEIGHTLESS_GRAVITY = -0.7;
	/** Weightless, falls don't hurt at all (the fall multiplier drops to nothing). */
	static final double WEIGHTLESS_FALL = -1.0;
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

	// ---- Crush (B tap) and Gravity Wave (B, charged: see GravityWave) ----
	public static final int CRUSH_FULL_CHARGE = 60;
	static final double CRUSH_RADIUS_MIN = 2.5, CRUSH_RADIUS_MAX = 5.5;
	static final float CRUSH_DAMAGE_MIN = 4.0F, CRUSH_DAMAGE_MAX = 16.0F;
	/** Crushed creatures weigh this many times more (gravity multiplier) for {@code CRUSH_PIN_TICKS}. */
	static final double CRUSH_WEIGHT = 3.0;
	static final int CRUSH_PIN_TICKS = 60;
	static final double CRUSH_SLAM = 1.6;
	static final int CRUSH_COOLDOWN_MIN = 120, CRUSH_COOLDOWN_MAX = 400;
	/**
	 * Crush doesn't hurt: it pins. Caught, a creature is too heavy to move ({@code CRUSH_PIN_SLOWNESS}, no jumping) for the
	 * pin; only armour suffers under the pressure: {@code CRUSH_ARMOR_DAMAGE} per point of armour worn (with the charge).
	 */
	static final int CRUSH_PIN_SLOWNESS = 6;
	static final float CRUSH_ARMOR_DAMAGE = 0.6F;
	/**
	 * R at a block is a struggle: held {@code RIP_CHARGE} it comes loose (when you let go, or once it's as big as it can
	 * get), the longer the bigger: a block more every {@code RIP_GROW}, up to 1 + {@code RIP_PER_LEVEL} per ability.
	 * Torn loose, it hangs in your hold without R held, until thrown (left click), pocketed (right click) or dropped (R).
	 */
	static final int RIP_CHARGE = 40, RIP_GROW = 12, RIP_PER_LEVEL = 2;
	/** Fully mastered, the whole struggle (to its biggest) is over within this many ticks: everything above runs faster to fit. */
	static final int RIP_MASTERED_TICKS = 20;
	/** Rock flight: V held this long (ascended or corrupted) instead of tapped. */
	static final int ROCK_HOLD_TICKS = 8;
	/** How high a rock slab can fly over the ground (the rider's client keeps it there). */
	public static final double ROCK_MAX_ALTITUDE = GravityRockFlight.MAX_ALTITUDE;
	/** The band under that height where the board fights to stay up, shuddering and sagging. */
	public static final double ROCK_STRUGGLE_BAND = GravityRockFlight.STRUGGLE_BAND;
	/** Collapse letting go: it's driven down this fast, and the landing hurts this much more per block fallen. */
	static final double PLUMMET_SPEED = 2.5;
	static final float PLUMMET_PER_BLOCK = 1.0F;
	/** Surviving a fall this hard (half-hearts) counts toward ascension like a kill by weight. */
	static final float ASCEND_FALL = 10.0F;

	// ---- Choke and Slam (right click while lifting, once R is unlocked) ----
	/** The choke holds it this far out, its head at your hand; the squeeze's damage, every so often. */
	static final double CHOKE_DISTANCE = 0.75;
	/** Right click on a held dropped item: it's drawn to you this fast and into your inventory (as much as fits). */
	static final double DRAW_SPEED = 1.1, DRAW_REACH = 1.3;
	/**
	 * Right click on something Lift holds that can't be pocketed or choked (a cart, a boat, an armour stand, a falling
	 * block...): it's hurled where you look, this fast (blocks/tick), and whatever it hits takes {@code THROW_DAMAGE_BASE}
	 * + {@code THROW_DAMAGE_PER_SPEED} x its speed (x its size). It flies for at most {@code THROW_TICKS}.
	 */
	static final double THROW_SPEED = 2.6;
	static final float THROW_DAMAGE_BASE = 4.0F, THROW_DAMAGE_PER_SPEED = 5.0F;
	static final int THROW_TICKS = 60;
	/** Held, a throwable thing drifts in toward you (blocks a tick) until it hangs this close, for a better throw. */
	static final double THROW_DRIFT = 0.12, THROW_HOLD = 2.5;
	/**
	 * A player held by Lift is stunned at first (a wind ragdolls): {@code HELD_STUN_WIND} ticks for a wind, {@code HELD_STUN}
	 * for anyone else. After that the hold loosens to {@code HELD_STEER_FREE} and they break free by getting
	 * {@code HELD_ESCAPE} blocks from where they're held (a wind simply flies out).
	 */
	static final int HELD_STUN_WIND = 10, HELD_STUN = 80;
	static final double HELD_STEER_FREE = 0.06, HELD_ESCAPE = 3.0;
	/** Before Weightless is yours, holding V draws loose items in from this far, this fast (blocks/tick). */
	static final double MAGNET_RADIUS = 12.0, MAGNET_SPEED = 0.85;
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
	/**
	 * A slam begun in mid-air: your fall stops while you haul it up and bring it down (the rise and
	 * {@code AERIAL_HANG} ticks more), it hits hardest of all (see {@link #SLAM_DAMAGE_MAX}), always buries three deep
	 * through anything up to {@code AERIAL_HARDNESS}, smashes the ground around it, and throws up more debris and smoke.
	 */
	static final float AERIAL_HARDNESS = 5.0F, AERIAL_DEBRIS = 1.6F;
	static final int AERIAL_HANG = 4;
	/** Width × height (blocks) past which a slammed creature counts as heavy (a cow is 0.9 × 1.4). */
	static final float HEAVY_SLAM_SIZE = 1.27F;
	/** How many full-body slam animations there are to pick from (see SlamPose). */
	public static final int SLAM_STYLES = 4;
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
	/**
	 * Damage. The most a slam ever does is {@code SLAM_DAMAGE_MAX}: from mid-air, on something big, every ability
	 * mastered and ascended. Everything else is a share of that: a fresh power {@code SLAM_DAMAGE_FRESH} of it (growing
	 * with each ability and the ascension), from the ground {@code SLAM_GROUND_SHARE}, on something small
	 * {@code SLAM_SMALL_SHARE}.
	 */
	static final float SLAM_DAMAGE_MAX = 20.0F, SLAM_DAMAGE_FRESH = 0.35F, SLAM_GROUND_SHARE = 0.65F, SLAM_SMALL_SHARE = 0.75F;
	/** Something hurled with Lift never hits harder than this. */
	static final float THROW_DAMAGE_MAX = 12.0F;
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
	static final double HORIZON_DEFLECT_RADIUS = 6.0;

	// ---- Singularity (C, ascended) ----
	static final double SINGULARITY_RANGE = 32.0;
	static final double SINGULARITY_PULL_RADIUS = 10.0;
	static final double SINGULARITY_PULL = 0.07;
	static final double SINGULARITY_CORE = 4.0;
	static final int SINGULARITY_TICKS = 60;
	/** (0.16.24) The Singularity's film (ticks): over your shoulder, pushing in on it. */
	static final int SINGULARITY_FILM_TICKS = 30;
	static final int SINGULARITY_HIT_EVERY = 10;
	static final float SINGULARITY_HIT = 3.0F;
	static final float SINGULARITY_BURST = 6.0F;
	public static final int SINGULARITY_COOLDOWN = 500;

	// ---- the starter (kept for good): the pull of small things ----
	/** Loose items this close drift toward you, this hard (b/t per tick); and your jump is this much stronger. */
	static final double PULL_RADIUS = 4.0, PULL_RADIUS_PER_LEVEL = 2.0;
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
	/**
	 * {@code style}: 0, the plain one-armed slam; otherwise one of the {@link #SLAM_STYLES} full-body slams (1 one hand
	 * overhead, 2 two-handed hammer, 3 one hand twisting in from the side, 4 both arms up in a V).
	 */
	public record SlamPose(long start, int rise, long landed, int style, boolean aerial) {
		public static final net.minecraft.network.codec.StreamCodec<io.netty.buffer.ByteBuf, SlamPose> STREAM_CODEC =
			net.minecraft.network.codec.StreamCodec.composite(ByteBufCodecs.VAR_LONG, SlamPose::start, ByteBufCodecs.VAR_INT, SlamPose::rise,
				ByteBufCodecs.VAR_LONG, SlamPose::landed, ByteBufCodecs.VAR_INT, SlamPose::style, ByteBufCodecs.BOOL, SlamPose::aerial, SlamPose::new);
	}

	public static final AttachmentType<SlamPose> SLAM_POSE = AttachmentRegistry.create(JaymosPowers.id("gravity_slam_pose"),
		builder -> builder.syncWith(SlamPose.STREAM_CODEC, AttachmentSyncPredicate.all()));
	/** On a creature: held by someone's Lift (clients let it hang limp). */
	public static final AttachmentType<Boolean> LIFTED = AttachmentRegistry.create(JaymosPowers.id("gravity_lifted"),
		builder -> builder.syncWith(ByteBufCodecs.BOOL, AttachmentSyncPredicate.all()));
	/** On a player held by Lift: stunned until this game time; a wind is ragdolled meanwhile, and flies free after. */
	public record Held(long stunUntil, boolean wind) {
		public static final net.minecraft.network.codec.StreamCodec<io.netty.buffer.ByteBuf, Held> STREAM_CODEC =
			net.minecraft.network.codec.StreamCodec.composite(ByteBufCodecs.VAR_LONG, Held::stunUntil, ByteBufCodecs.BOOL, Held::wind, Held::new);
	}

	public static final AttachmentType<Held> HELD = AttachmentRegistry.create(JaymosPowers.id("gravity_held"),
		builder -> builder.syncWith(Held.STREAM_CODEC, AttachmentSyncPredicate.all()));
	/** Tearing a block loose with R (since when): synced to the owner, whose view shakes with the struggle. */
	public static final AttachmentType<Long> RIPPING = AttachmentRegistry.create(JaymosPowers.id("gravity_ripping"),
		builder -> builder.syncWith(ByteBufCodecs.VAR_LONG, AttachmentSyncPredicate.targetOnly()));
	/** Riding a rock slab (its chunk's id): synced to everyone; its rider's client flies it. */
	public static final AttachmentType<Integer> ROCK_FLIGHT = AttachmentRegistry.create(JaymosPowers.id("gravity_rock_flight"),
		builder -> builder.syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.all()));
	/**
	 * On a creature or player under Gravity's weight (Crush's pin, a Gravity Well's pull): forced to its knees, in one
	 * of {@link #KNEEL_STYLES} poses, since {@code since} (game time). Players and humanoid mobs are drawn resisting,
	 * then dropping to their knees; when it's taken off they get back up (see GravityKneelPose).
	 */
	public record Kneel(int style, long since) {
		public static final net.minecraft.network.codec.StreamCodec<io.netty.buffer.ByteBuf, Kneel> STREAM_CODEC =
			net.minecraft.network.codec.StreamCodec.composite(ByteBufCodecs.VAR_INT, Kneel::style, ByteBufCodecs.VAR_LONG, Kneel::since, Kneel::new);
	}

	public static final AttachmentType<Kneel> KNEEL = AttachmentRegistry.create(JaymosPowers.id("gravity_kneel"),
		builder -> builder.syncWith(Kneel.STREAM_CODEC, AttachmentSyncPredicate.all()));
	/** How many kneeling poses there are to pick from (see GravityKneelPose). */
	public static final int KNEEL_STYLES = 4;
	/** A Gravity Well keeps those it holds kneeling this long after they leave it (ticks). */
	static final int KNEEL_WELL_LINGER = 6;

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
		/** A torn-out chunk held without R (see {@link #RIP_CHARGE}). */
		boolean chunkHold;
		/** Tearing a block loose: since when, where, and the blocks that would come with it (in order). */
		long ripFrom = -1;
		@Nullable BlockHitResult ripHit;
		List<BlockPos> ripBlocks = List.of();
		/** V went down at (game time), for tap or hold. */
		long vDownAt = -1;
		/** The well (by when it ends) that B already launched you out of, so it only does once. */
		long launchedFrom = -1;
		/** C went down at (game time): the Meteor gathering (see {@link GravityMeteor#FULL_CHARGE}). */
		long meteorFrom = -1;
	}

	/** Dropped by Collapse: driven down, and hurt more for every block it falls. */
	private record Plummet(ResourceKey<Level> dimension, UUID entity, UUID player, double fromY, long until) {
	}

	private static final List<Plummet> PLUMMETS = new ArrayList<>();

	/** A creature being slammed down: who threw it, how hard, and what it can break. */
	private record Slam(ResourceKey<Level> dimension, UUID entity, UUID player, float damage, float breakHardness, long started, int rise, double down,
		int depth, boolean aerial) {
	}

	private static final List<Slam> SLAMS = new ArrayList<>();

	/** Something hurled by Lift's right click: its speed last tick, and who it has hit already. */
	private static final class Thrown {
		final ResourceKey<Level> dimension;
		final UUID entity, thrower;
		final long until;
		Vec3 velocity;
		final java.util.Set<Integer> hit = new java.util.HashSet<>();

		Thrown(ResourceKey<Level> dimension, UUID entity, UUID thrower, long until, Vec3 velocity) {
			this.dimension = dimension;
			this.entity = entity;
			this.thrower = thrower;
			this.until = until;
			this.velocity = velocity;
		}
	}

	private static final List<Thrown> THROWN = new ArrayList<>();

	private record Well(ResourceKey<Level> dimension, Vec3 at, UUID owner, long until, boolean singularity) {
	}


	private record Pinned(ResourceKey<Level> dimension, UUID entity, long until) {
	}

	/** Who's on their knees under Gravity's weight, and until when (game time). */
	private record Kneeling(ResourceKey<Level> dimension, UUID entity, long until) {
	}

	private static final Map<UUID, Kneeling> KNEELING = new HashMap<>();

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
		JaymosPowers.LOGGER.debug("gravity attachments: {} {} {} {} {} {}", COOLDOWNS, WEIGHTLESS_ON, LIFTED, KILLS, GRIP, SLAM_POSE, KNEEL);
		net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.serverboundPlay().register(GravityGrip.TYPE, GravityGrip.CODEC);
		net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.clientboundPlay().register(GravityMeteorBurst.TYPE, GravityMeteorBurst.CODEC);
		net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.registerGlobalReceiver(GravityGrip.TYPE, (payload, ctx) -> {
			ServerPlayer player = ctx.player();
			if (Powers.has(player, INSTANCE) && player.isAlive() && !player.isSpectator()) {
				grip(player, state(player), payload.left());
			}
		});
		GravityWalk.register();
		GravityTrainingField.init();
		// (0.17.3) what a gifted villager does with Gravity
		GravityGift.init();
		ServerTickEvents.END_SERVER_TICK.register(GravityPower::serverTick);
		ServerLivingEntityEvents.AFTER_DEATH.register(GravityPower::afterDeath);
		// surviving a hard fall is a step toward ascension, like a kill by weight
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damageTaken, blocked) -> {
			if (entity instanceof ServerPlayer player && source.is(DamageTypeTags.IS_FALL) && damageTaken >= ASCEND_FALL && player.isAlive()
				&& Powers.has(player, INSTANCE)) {
				countTowardAscension(player);
			}
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			State state = STATES.remove(handler.player.getUUID());
			if (state != null) {
				dropLift(handler.player, state, false);
			}
			GravityOrbit.drop(handler.player);
			GravityRockFlight.stop(handler.player);
			GravitySkyfall.land(handler.player);
			GravityForward.forget(handler.player);
			GravityWalk.stop(handler.player);
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			STATES.clear();
			WELLS.clear();
			PINNED.clear();
			KNEELING.clear();
			GravityShockwave.clear();
			SLAMS.clear();
			WEIGHED.clear();
			TOUCHED.clear();
			PLUMMETS.clear();
			GravityPush.clear();
			GravityMeteor.clear();
			GravitySkyfall.clear();
			GravityOrbit.clear();
			GravitySideFall.clear();
			GravityForward.clear();
			GravityWalk.clear();
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

	/** V works from the start: before Weightless is yours, holding it draws loose items in. */
	@Override
	public boolean usableEarly(PowerKey key) {
		return key == PowerKey.ABILITY_3;
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

	/** (0.15.31) Downed: what was running ends. */
	@Override
	public void onDowned(ServerPlayer player) {
		// a lift lets go, Weightless ends (no cooldown: it wasn't their choice), and every way of moving stops
		State state = STATES.get(player.getUUID());
		if (state != null) {
			dropLift(player, state, false);
		}
		if (player.getAttached(WEIGHTLESS_ON) != null) {
			setWeightless(player, false);
		}
		GravityWalk.stop(player);
		GravityRockFlight.stop(player);
		GravityForward.stop(player);
	}

	@Override
	public void onLost(ServerPlayer player) {
		State state = STATES.remove(player.getUUID());
		if (state != null) {
			dropLift(player, state, false);
		}
		setWeightless(player, false);
		GravityForward.forget(player);
		GravityWalk.stop(player);
		modifier(player, Attributes.GRAVITY, COLLAPSE_ID, 0.0, false);
		modifier(player, Attributes.FALL_DAMAGE_MULTIPLIER, COLLAPSE_FALL_ID, 0.0, false);
		modifier(player, Attributes.KNOCKBACK_RESISTANCE, HORIZON_ID, 0.0, false);
		AttributeInstance jump = player.getAttribute(Attributes.JUMP_STRENGTH);
		if (jump != null) {
			jump.removeModifier(PULL_JUMP_ID);
		}
		GravityWeight.clear(player);
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
		return player.getAttached(WEIGHTLESS_ON) != null || GravitySkyfall.riding(player) || GravityForward.falling(player)
			|| GravityWalk.walking(player);
	}

	// ---------------------------------------------------------------- keys

	@Override
	public void onKey(ServerPlayer player, PowerKey key, boolean down) {
		State state = state(player);
		switch (key) {
			case ABILITY_1 -> {
				if (down) {
					if (state.chunkHold && state.liftTarget >= 0) {
						// R again drops a chunk held on its own
						dropLift(player, state, true);
					} else {
						startLift(player, state);
					}
				} else if (state.ripFrom >= 0) {
					finishRip(player, state);
				} else if (!state.chunkHold) {
					// sneaking as you let go: what you held falls sideways, the way you look
					Entity held = player.isShiftKeyDown() && state.liftTarget >= 0 ? player.level().getEntity(state.liftTarget) : null;
					dropLift(player, state, true);
					if (held instanceof LivingEntity living && living.isAlive()) {
						GravitySideFall.start(player, living, player.getLookAngle());
						// (Collapse's plummet gives way to it)
						PLUMMETS.removeIf(p -> p.entity().equals(living.getUUID()));
					}
				}
			}
			case ABILITY_2 -> {
				if (down) {
					well(player);
				}
			}
			case ABILITY_3 -> {
				// held down before Weightless is yours: the item pull (see tick)
				if (!com.jaymospowers.core.Mastery.isUnlocked(player, PowerKey.ABILITY_3)) {
					return;
				}
				// tapped: Weightless; held: the earthboard (see tick)
				boolean toggles = GravityRockFlight.toggles(player);
				boolean riding = player.getAttached(ROCK_FLIGHT) != null;
				if (down) {
					if (riding) {
						// riding (the toggle, once mastered): V again steps off (the release that follows does nothing)
						GravityRockFlight.stop(player);
						state.vDownAt = -1;
					} else if (player.isShiftKeyDown() && player.getAttached(WEIGHTLESS_ON) != null) {
						// sneak+V while weightless: fall forward, the way you look (it uses up some of Weightless's time)
						if (GravityForward.start(player)) {
							state.weightlessTicks += GravityForward.WEIGHTLESS_COST;
						}
						state.vDownAt = -1;
					} else {
						state.vDownAt = player.level().getGameTime();
					}
				} else {
					if (riding && !toggles) {
						// not yet mastered: you ride only while V is held
						GravityRockFlight.stop(player);
					} else if (!riding && state.vDownAt >= 0) {
						toggleWeightless(player, state);
					}
					state.vDownAt = -1;
				}
			}
			case ABILITY_4 -> {
				if (down) {
					if (!state.chunkHold && state.ripFrom < 0 && ready(player, CRUSH) && liftedCreature(player, state) instanceof LivingEntity target) {
						// R+B: Inversion, the held creature yanked in for a gut punch
						dropLift(player, state, true);
						// (Collapse's plummet on letting go gives way to the yank)
						PLUMMETS.removeIf(p -> p.entity().equals(target.getUUID()));
						GravityGutPunch.start(player, target);
						int cooldown = GravityGutPunch.COOLDOWN;
						player.setAttached(COOLDOWNS, cooldowns(player).withCrush(player.level().getGameTime() + cooldown, cooldown));
						state.crushFrom = -1;
					} else if (ready(player, CRUSH) || ownWell(player) != null) {
						state.crushFrom = player.level().getGameTime();
						player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.PLAYERS, 0.5F, 1.6F);
					}
				} else if (state.crushFrom >= 0) {
					long held = player.level().getGameTime() - state.crushFrom;
					float charge = Mth.clamp(held / (float)CRUSH_FULL_CHARGE, 0.0F, 1.0F);
					state.crushFrom = -1;
					releaseCrush(player, state, held, charge);
				}
			}
		}
	}

	/** Z: the Immense Field (a dome of heavy gravity to train in), or take yours down. */
	@Override
	public void onMode(ServerPlayer player, boolean down) {
		if (down) {
			GravityTrainingField.toggle(player);
		}
	}

	/** Collapse has a C of its own (the black hole), corrupted or not ascended. */
	@Override
	public boolean ultimateWhenCorrupted() {
		return true;
	}

	/** C: the Meteor (ascended), the black hole (corrupted), Skyfall (both). */
	@Override
	public void onUltimate(ServerPlayer player, boolean down) {
		boolean ascended = Fate.isAscended(player), corrupted = Fate.isCorrupted(player);
		State state = state(player);
		if (ascended && !corrupted) {
			// the Meteor: held, it gathers; let go, it comes
			if (down) {
				state.meteorFrom = ready(player, SINGULARITY) ? player.level().getGameTime() : -1;
			} else if (state.meteorFrom >= 0) {
				float charge = meteorCharge(player, state);
				state.meteorFrom = -1;
				if (ready(player, SINGULARITY) && GravityMeteor.call(player, charge)) {
					int cooldown = GravityMeteor.cooldown(charge);
					player.setAttached(COOLDOWNS, cooldowns(player).withSingularity(player.level().getGameTime() + cooldown, cooldown));
				}
			}
			return;
		}
		if (!down || !ready(player, SINGULARITY)) {
			return;
		}
		if (ascended) {
			if (GravitySkyfall.call(player)) {
				cool(player, SINGULARITY, GravitySkyfall.COOLDOWN);
			}
		} else if (corrupted) {
			singularity(player);
		}
	}

	/** How far the Meteor has gathered: 0 (a tap) to 1. */
	private static float meteorCharge(ServerPlayer player, State state) {
		return Mth.clamp((player.level().getGameTime() - state.meteorFrom) / (float)GravityMeteor.FULL_CHARGE, 0.0F, 1.0F);
	}

	/** The C's cooldown for this player's form (for the HUD). */
	public static int ultimateCooldown(Player player) {
		boolean ascended = Fate.isAscended(player), corrupted = Fate.isCorrupted(player);
		return ascended && corrupted ? GravitySkyfall.COOLDOWN : ascended ? GravityMeteor.COOLDOWN : SINGULARITY_COOLDOWN;
	}

	// ---------------------------------------------------------------- Lift

	private static void startLift(ServerPlayer player, State state) {
		if (state.liftTarget >= 0 || !ready(player, LIFT)) {
			return;
		}
		Entity target = liftableInSight(player, liftRange(player));
		if (target == null) {
			// aimed at a wall or the ground: a struggle to tear it loose (see finishRip)
			if (!startRip(player, state)) {
				fizzle(player);
			}
			return;
		}
		if (!target.isAlive()) {
			fizzle(player);
			return;
		}
		if (target instanceof com.jaymospowers.core.BlockCluster cluster && cluster.scripted()) {
			// a block out of Collapsing Horizon's ring (or anyone's)
			GravityOrbit.taken(player, target);
		}
		hold(player, state, target);
	}

	/** Lift takes hold of {@code target}. */
	private static void hold(ServerPlayer player, State state, Entity target) {
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
		if (target instanceof ServerPlayer held) {
			boolean wind = Powers.has(held, com.jaymospowers.power.wind.WindPower.INSTANCE);
			held.setAttached(HELD, new Held(player.level().getGameTime() + (wind ? HELD_STUN_WIND : HELD_STUN), wind));
		}
		ServerLevel level = player.level();
		level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.SHULKER_SHOOT, SoundSource.PLAYERS, 0.8F, 0.6F);
		level.sendParticles(ParticleTypes.REVERSE_PORTAL, target.getX(), target.getY(), target.getZ(), 20, 0.4, 0.1, 0.4, 0.05);
	}

	private static void tickLift(ServerPlayer player, State state) {
		Entity entity = player.level().getEntity(state.liftTarget);
		if (entity == null || !entity.isAlive() || entity.level() != player.level()
			|| ++state.liftTicks > LIFT_MAX_TICKS || !state.chunkHold && !PowerInput.isDown(player, PowerKey.ABILITY_1)
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
		if (state.drawing && isCorpse(target)) {
			drawCorpse(player, state, target);
			return;
		}
		if (throwable(target) && state.liftHold > THROW_HOLD) {
			state.liftHold = Math.max(THROW_HOLD, state.liftHold - THROW_DRIFT);
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
		double steer = LIFT_STEER;
		Held held = target.getAttached(HELD);
		if (held != null && player.level().getGameTime() >= held.stunUntil()) {
			// the stun's worn off: the hold loosens, and far enough off they've broken free
			if (to.length() > HELD_ESCAPE) {
				player.level().playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.CHAIN_BREAK, SoundSource.PLAYERS, 0.9F, 0.7F);
				player.level().sendParticles(ParticleTypes.REVERSE_PORTAL, center.x, center.y, center.z, 20, 0.3, 0.4, 0.3, 0.1);
				dropLift(player, state, true);
				return;
			}
			steer = HELD_STEER_FREE;
		}
		Vec3 v = target.getDeltaMovement().scale(0.8).add(to.scale(steer));
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
	public static double liftRange(Player player) {
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
		state.chunkHold = false;
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
		entity.removeAttached(HELD);
		if (Fate.isCorrupted(player) && entity.isAlive() && entity instanceof LivingEntity living && !(entity instanceof ArmorStand)) {
			// Collapse: let go, and it's driven into the ground under a crushing weight
			ServerLevel level = player.level();
			living.setDeltaMovement(0.0, -PLUMMET_SPEED, 0.0);
			living.needsSync = true;
			living.syncVelocity = true;
			if (living instanceof ServerPlayer held) {
				net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(held, new com.jaymospowers.power.web.WebNet.Nudge(new Vec3(0.0, -PLUMMET_SPEED, 0.0)));
			}
			touch(living, player);
			PLUMMETS.removeIf(p -> p.entity().equals(living.getUUID()));
			PLUMMETS.add(new Plummet(level.dimension(), living.getUUID(), player.getUUID(), living.getY(), level.getGameTime() + 100));
			level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.WARDEN_ATTACK_IMPACT, SoundSource.PLAYERS, 0.9F, 0.5F);
		}
	}

	/** Collapse's dropped: driven down until they land, then the extra weight of the fall lands on them. */
	private static void tickPlummets(MinecraftServer server) {
		for (Iterator<Plummet> it = PLUMMETS.iterator(); it.hasNext(); ) {
			Plummet plummet = it.next();
			ServerLevel level = server.getLevel(plummet.dimension());
			Entity entity = level == null ? null : level.getEntity(plummet.entity());
			if (!(entity instanceof LivingEntity living) || !living.isAlive() || level.getGameTime() > plummet.until()) {
				it.remove();
				continue;
			}
			if (!living.onGround() && !living.isInWater()) {
				living.setDeltaMovement(living.getDeltaMovement().x * 0.5, Math.min(living.getDeltaMovement().y, -PLUMMET_SPEED), living.getDeltaMovement().z * 0.5);
				living.syncVelocity = true;
				continue;
			}
			it.remove();
			double fell = Math.max(0.0, plummet.fromY() - living.getY());
			if (fell > 1.0) {
				living.setInvulnerableTime(0);
				living.hurtServer(level, level.damageSources().fall(), (float)fell * PLUMMET_PER_BLOCK);
				BlockPos under = living.blockPosition().below();
				com.jaymospowers.core.Debris.puff(level, under, level.getBlockState(under), 1.8F);
				level.playSound(null, living.getX(), living.getY(), living.getZ(), SoundEvents.MACE_SMASH_GROUND_HEAVY, SoundSource.PLAYERS, 1.2F, 0.7F);
				com.jaymospowers.core.Shakes.around(level, living.position(), 14.0, (float)Mth.clamp(fell * 0.12, 0.4, 1.4));
			}
		}
	}

	// ---------------------------------------------------------------- Choke and Slam

	/**
	 * Right click while holding something with Lift (R unlocked; the starter's Lift can't): the first pulls it in by the
	 * throat, the second slams it into the ground. The slam grows with the power: more damage and more weight per
	 * ability unlocked (and ascended), and from three abilities up, the block it hits breaks.
	 */
	private static void grip(ServerPlayer player, State state, boolean left) {
		if (state.liftTarget < 0) {
			return;
		}
		Entity entity = player.level().getEntity(state.liftTarget);
		if (left) {
			if (entity != null && throwable(entity)) {
				hurl(player, state, entity);
			} else if (entity instanceof LivingEntity living && living.isAlive() && com.jaymospowers.core.Mastery.isUnlocked(player, PowerKey.ABILITY_1)) {
				// the Push: away, far, and through
				state.choking = false;
				dropLiftQuietly(player, state, living);
				touch(living, player);
				GravityPush.push(player, living);
			}
			return;
		}
		if (entity instanceof com.jaymospowers.core.BlockCluster chunk) {
			// a chunk (or a block from the ring): into your pockets
			dropLiftQuietly(player, state, chunk);
			chunk.pocket(player);
			return;
		}
		if ((entity instanceof ItemEntity || entity != null && isCorpse(entity)) && !state.drawing) {
			// a dropped item (or a corpse): drawn in to you and pocketed (a corpse too big for a bag, carried)
			state.drawing = true;
			player.level().playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.SHULKER_BULLET_HURT, SoundSource.PLAYERS, 0.6F, 1.6F);
			return;
		}
		if (entity != null && throwable(entity)) {
			hurl(player, state, entity);
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
		// zombies, players and anything bigger than a cow are heavy: they take the whole body (and the full blow)
		boolean heavy = target.getBbWidth() * target.getBbHeight() > HEAVY_SLAM_SIZE || target instanceof Player
			|| target instanceof net.minecraft.world.entity.monster.zombie.Zombie;
		float grown = (abilities + (ascended ? 1 : 0)) / (float)(com.jaymospowers.core.Mastery.ALL + 1);
		float damage = SLAM_DAMAGE_MAX * Mth.lerp(grown, SLAM_DAMAGE_FRESH, 1.0F) * (heavy ? 1.0F : SLAM_SMALL_SHARE);
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
		// from mid-air: you hang there, flip over with it, and it hits far harder (always three deep)
		boolean aerial = !player.onGround() && !player.isInWater();
		if (aerial) {
			depth = 3;
		} else {
			damage *= SLAM_GROUND_SHARE;
		}
		if (aerial) {
			breakHardness = Math.max(breakHardness, AERIAL_HARDNESS);
			stall(player);
		}
		SLAMS.add(new Slam(level.dimension(), target.getUUID(), player.getUUID(), damage, breakHardness, level.getGameTime(), rise, down, depth,
			aerial));
		// the heavy ones get one of the full overhead slams, at random
		int style = heavy ? 1 + level.getRandom().nextInt(SLAM_STYLES) : 0;
		player.setAttached(SLAM_POSE, new SlamPose(level.getGameTime(), rise, -1L, style, aerial));
		level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.WARDEN_ATTACK_IMPACT, SoundSource.PLAYERS, 1.0F, 0.6F);
	}

	/**
	 * R aimed at a block: Lift tears a chunk of it loose (bigger and from harder stone as the power grows:
	 * {@code CHUNK_PIECES_BASE} + one per ability, no harder than {@code CHUNK_HARDEST_BASE} + {@code CHUNK_HARDEST_PER_LEVEL}
	 * per ability). Thrown with left (or right) click.
	 */
	static final int CHUNK_PIECES_BASE = 3;
	static final float CHUNK_HARDEST_BASE = 1.5F, CHUNK_HARDEST_PER_LEVEL = 0.75F;

	/** R at a block: the struggle begins (cracks spread, your view shakes). False if there's nothing there to take. */
	private static boolean startRip(ServerPlayer player, State state) {
		Vec3 eye = player.getEyePosition();
		BlockHitResult hit = player.level().clip(new ClipContext(eye, eye.add(player.getViewVector(1.0F).scale(liftRange(player))),
			ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
		if (hit.getType() != HitResult.Type.BLOCK || !player.mayBuild()) {
			return false;
		}
		int abilities = com.jaymospowers.core.Mastery.unlocked(player);
		List<BlockPos> blocks = com.jaymospowers.core.BlockCluster.crater(player.level(), hit.getBlockPos(), ripMax(player),
			CHUNK_HARDEST_BASE + CHUNK_HARDEST_PER_LEVEL * abilities, player);
		if (blocks.isEmpty()) {
			return false;
		}
		state.ripFrom = player.level().getGameTime();
		state.ripHit = hit;
		state.ripBlocks = blocks;
		player.setAttached(RIPPING, state.ripFrom);
		return true;
	}

	/** The most a struggle can tear loose. */
	private static int ripMax(ServerPlayer player) {
		return Math.min(com.jaymospowers.core.BlockCluster.MAX_PIECES, 1 + RIP_PER_LEVEL * com.jaymospowers.core.Mastery.unlocked(player));
	}

	/** How long the struggle has gone on, in ticks of an unmastered struggle (a master's runs faster, see RIP_MASTERED_TICKS). */
	private static long ripHeld(ServerPlayer player, State state) {
		long held = player.level().getGameTime() - state.ripFrom;
		if (com.jaymospowers.core.Mastery.unlocked(player) < com.jaymospowers.core.Mastery.ALL) {
			return held;
		}
		int whole = RIP_CHARGE + RIP_GROW * (Math.min(ripMax(player), state.ripBlocks.size()) - 1);
		return held * Math.max(whole, RIP_MASTERED_TICKS) / RIP_MASTERED_TICKS;
	}

	/** How many blocks the struggle has worked loose so far. */
	private static int ripCount(ServerPlayer player, State state) {
		long held = ripHeld(player, state);
		if (held < RIP_CHARGE) {
			return 0;
		}
		return (int)Math.min(Math.min(ripMax(player), state.ripBlocks.size()), 1 + (held - RIP_CHARGE) / RIP_GROW);
	}

	/** Still straining: the cracks spread to every block that would come; at its biggest, it comes loose on its own. */
	private static void tickRip(ServerPlayer player, State state) {
		ServerLevel level = player.level();
		long held = ripHeld(player, state);
		int coming = Math.max(1, ripCount(player, state));
		for (int i = 0; i < state.ripBlocks.size(); i++) {
			int stage = i < coming ? (int)Math.min(9, held * 9 / Math.max(1, RIP_CHARGE + RIP_GROW * i)) : -1;
			level.destroyBlockProgress(ripCrackId(player, i), state.ripBlocks.get(i), stage);
		}
		if ((level.getGameTime() - state.ripFrom) % 8 == 0) {
			BlockPos pos = state.ripBlocks.getFirst();
			level.playSound(null, pos, level.getBlockState(pos).getSoundType().getHitSound(), SoundSource.BLOCKS, 1.0F, 0.5F);
			level.sendParticles(new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.BLOCK, level.getBlockState(pos)),
				pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 6, 0.4, 0.4, 0.4, 0.05);
		}
		if (ripCount(player, state) >= Math.min(ripMax(player), state.ripBlocks.size())) {
			finishRip(player, state);
		}
	}

	private static int ripCrackId(ServerPlayer player, int i) {
		return player.getId() * 43 + 3000 + i;
	}

	/** The struggle ends: long enough, and it tears loose and hangs in your hold; too soon, and nothing comes. */
	private static void finishRip(ServerPlayer player, State state) {
		ServerLevel level = player.level();
		int count = ripCount(player, state);
		for (int i = 0; i < state.ripBlocks.size(); i++) {
			level.destroyBlockProgress(ripCrackId(player, i), state.ripBlocks.get(i), -1);
		}
		List<BlockPos> blocks = new ArrayList<>();
		for (int i = 0; i < count && i < state.ripBlocks.size(); i++) {
			if (com.jaymospowers.core.BlockCluster.takeable(level, state.ripBlocks.get(i), 50.0F, player)) {
				blocks.add(state.ripBlocks.get(i));
			}
		}
		BlockHitResult hit = state.ripHit;
		state.ripFrom = -1;
		state.ripHit = null;
		state.ripBlocks = List.of();
		player.removeAttached(RIPPING);
		if (blocks.isEmpty() || hit == null) {
			fizzle(player);
			return;
		}
		com.jaymospowers.core.BlockCluster chunk = com.jaymospowers.core.BlockCluster.of(level, blocks, hit.getDirection().getUnitVec3().scale(0.9), player);
		if (chunk == null) {
			return;
		}
		hold(player, state, chunk);
		state.chunkHold = true;
	}

	/** Things Lift can't pocket or choke: anything that isn't alive (bar items and corpses), and armour stands. */
	private static boolean throwable(Entity entity) {
		return !(entity instanceof ItemEntity) && !isCorpse(entity) && (!(entity instanceof LivingEntity) || entity instanceof ArmorStand);
	}

	/** Right click on such a thing: let go and hurl it where you look, hard. */
	private static void hurl(ServerPlayer player, State state, Entity entity) {
		dropLift(player, state, true);
		Vec3 velocity = player.getViewVector(1.0F).scale(THROW_SPEED);
		entity.setDeltaMovement(velocity);
		entity.needsSync = true;
		entity.syncVelocity = true;
		entity.syncVelocity = true;
		ServerLevel level = player.level();
		if (entity instanceof com.jaymospowers.core.BlockCluster chunk) {
			// a chunk of the world does its own battering, and breaks up on what stops it
			chunk.launch(player);
		} else {
			THROWN.removeIf(t -> t.entity.equals(entity.getUUID()));
			THROWN.add(new Thrown(level.dimension(), entity.getUUID(), player.getUUID(), level.getGameTime() + THROW_TICKS, velocity));
		}
		player.swing(net.minecraft.world.InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
		level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.SHULKER_SHOOT, SoundSource.PLAYERS, 1.0F, 0.4F);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 0.8F, 0.5F);
		level.sendParticles(ParticleTypes.REVERSE_PORTAL, entity.getX(), entity.getY() + 0.3, entity.getZ(), 25, 0.3, 0.3, 0.3, 0.2);
	}

	/** Hurled things in flight: they batter whatever they pass through, and smash into what stops them. */
	private static void tickThrown(MinecraftServer server) {
		for (Iterator<Thrown> it = THROWN.iterator(); it.hasNext(); ) {
			Thrown thrown = it.next();
			ServerLevel level = server.getLevel(thrown.dimension);
			Entity entity = level == null ? null : level.getEntity(thrown.entity);
			if (entity == null || !entity.isAlive() || level.getGameTime() >= thrown.until) {
				it.remove();
				continue;
			}
			double speed = thrown.velocity.length();
			Entity thrower = level.getEntity(thrown.thrower);
			float size = Mth.clamp(entity.getBbWidth() * entity.getBbHeight(), 0.5F, 3.0F);
			if (speed > 0.4) {
				for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, entity.getBoundingBox().inflate(0.3).expandTowards(thrown.velocity),
					e -> e != entity && e != thrower && e.isAlive() && !e.isSpectator() && !thrown.hit.contains(e.getId()))) {
					thrown.hit.add(victim.getId());
					float damage = Math.min(THROW_DAMAGE_MAX, (THROW_DAMAGE_BASE + THROW_DAMAGE_PER_SPEED * (float)speed) * size);
					DamageSource source = thrower instanceof Player p ? level.damageSources().mobProjectile(entity, p) : level.damageSources().flyIntoWall();
					victim.hurtServer(level, source, damage);
					victim.setDeltaMovement(victim.getDeltaMovement().add(thrown.velocity.scale(0.5)).add(0.0, 0.25, 0.0));
					victim.syncVelocity = true;
					level.playSound(null, victim.getX(), victim.getY(), victim.getZ(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 1.0F, 0.6F);
				}
			}
			if ((entity.horizontalCollision || entity.verticalCollision) && speed >= com.jaymospowers.power.web.WebImpacts.SMASH_SPEED) {
				// it hit something solid at speed: that breaks, like any hard impact
				Vec3 at = entity.getBoundingBox().getCenter().add(thrown.velocity.normalize().scale(0.6));
				com.jaymospowers.core.Debris.smash(level, at, speed * Math.sqrt(size), thrower);
				it.remove();
				continue;
			}
			thrown.velocity = entity.getDeltaMovement();
		}
	}

	/** A held corpse flies to you; once close it's yours (Jaymo's Kitchen bags it if it fits, otherwise you carry it). */
	private static void drawCorpse(ServerPlayer player, State state, Entity corpse) {
		Vec3 chest = player.position().add(0.0, player.getBbHeight() * 0.5, 0.0);
		Vec3 to = chest.subtract(corpse.getBoundingBox().getCenter());
		if (to.length() > DRAW_REACH + corpse.getBbWidth() * 0.5) {
			corpse.setDeltaMovement(to.normalize().scale(Math.min(DRAW_SPEED, to.length())));
			corpse.needsSync = true;
			corpse.syncVelocity = true;
			return;
		}
		state.drawing = false;
		dropLift(player, state, true);
		com.jaymospowers.power.web.KitchenCompat.take(player, corpse);
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
	public static @Nullable Entity liftableInSight(Player player, double range) {
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
			// a player is easier to catch, the further off the more so
			double slack = e instanceof Player ? LIFT_PLAYER_SLACK + LIFT_PLAYER_SLACK_PER_BLOCK * eye.distanceTo(e.position()) : LIFT_SLACK;
			AABB box = e.getBoundingBox().inflate(Math.max(slack, e.getPickRadius()));
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
		target.removeAttached(HELD);
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
			if (slam.aerial() && age <= slam.rise() + AERIAL_HANG) {
				ServerPlayer slammer = server.getPlayerList().getPlayer(slam.player());
				if (slammer != null && !slammer.onGround()) {
					stall(slammer);
				}
			}
			// the slam is the blow: no fall damage on top of it
			target.resetFallDistance();
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
				com.jaymospowers.core.Crushing.mark(target);
			} finally {
				crushing = false;
			}
			BlockPos under = BlockPos.containing(target.getX(), target.getY() - 0.2, target.getZ());
			BlockState hit = level.getBlockState(under);
			BlockState shown = hit.isAir() ? net.minecraft.world.level.block.Blocks.DIRT.defaultBlockState() : hit;
			// the ground all around kicks up
			float debris = slam.aerial() ? AERIAL_DEBRIS : 1.0F;
			com.jaymospowers.core.Debris.ring(level, under, IMPACT_RING + 1 + (slam.aerial() ? 2 : 0), IMPACT_DEBRIS * debris);
			if (grounded && target.isAlive()) {
				bury(level, target, slam.depth(), Math.max(BURY_HARDNESS, slam.breakHardness()));
			}
			if (slam.aerial()) {
				// and from that height it breaks the ground around it like a blast
				com.jaymospowers.core.Debris.smash(level, Vec3.atCenterOf(under), slam.down(), player);
				if (player != null && !player.onGround() && com.jaymospowers.core.Mastery.unlocked(player) >= com.jaymospowers.core.Mastery.ALL) {
					// mastery: a chunk of the ground comes flying up and knocks you clear of the crater
					clearOfCrater(level, player, Vec3.atCenterOf(under));
				}
				level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, target.getX(), target.getY() + 0.3, target.getZ(), 14, 1.2, 0.3, 1.2, 0.03);
				level.sendParticles(ParticleTypes.LARGE_SMOKE, target.getX(), target.getY() + 0.3, target.getZ(), 30, 1.5, 0.4, 1.5, 0.05);
				level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 1.2F, 0.7F);
			}
			// the heart of the impact: the biggest puff, the colour of what it hit
			com.jaymospowers.core.Debris.puff(level, under, shown, IMPACT_DEBRIS * 1.6F * debris);
			level.sendParticles(ParticleTypes.EXPLOSION, target.getX(), target.getY(), target.getZ(), 1, 0.0, 0.0, 0.0, 0.0);
			level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.MACE_SMASH_GROUND_HEAVY, SoundSource.PLAYERS, 1.3F, 0.7F);
			com.jaymospowers.core.Shakes.around(level, target.position(), 20.0, slam.aerial() ? 2.2F : 1.3F);
		}
	}


	/** How hard (blocks/tick) the flying debris knocks a master clear of their crater, out and up. */
	static final double CLEAR_KNOCK = 1.15, CLEAR_LIFT = 0.65;
	/** Debris thrown up to knock a master clear: gone after this many ticks (it never lands as a block). */
	private record Shrapnel(ResourceKey<Level> dimension, int entity, long until) {
	}

	private static final List<Shrapnel> SHRAPNEL = new ArrayList<>();

	private static void clearOfCrater(ServerLevel level, ServerPlayer player, Vec3 crater) {
		Vec3 away = player.position().subtract(crater);
		away = new Vec3(away.x, 0.0, away.z);
		if (away.lengthSqr() < 0.04) {
			Vec3 look = player.getLookAngle();
			away = new Vec3(-look.x, 0.0, -look.z);
		}
		away = away.lengthSqr() < 1.0E-4 ? new Vec3(1.0, 0.0, 0.0) : away.normalize();
		// the chunk: a lump of what was hit, flung up from the crater's edge into you
		BlockPos from = BlockPos.containing(crater.add(away.scale(1.5)));
		for (int dy = 1; dy >= -2; dy--) {
			BlockPos pos = from.above(dy);
			BlockState state = level.getBlockState(pos);
			if (!state.isAir() && state.getFluidState().isEmpty() && !state.hasBlockEntity() && state.getDestroySpeed(level, pos) >= 0.0F
				&& state.getDestroySpeed(level, pos) <= 5.0F) {
				net.minecraft.world.entity.item.FallingBlockEntity chunk = net.minecraft.world.entity.item.FallingBlockEntity.fall(level, pos, state);
				chunk.disableDrop();
				Vec3 to = player.position().add(0.0, 0.8, 0.0).subtract(chunk.position());
				chunk.setDeltaMovement(to.scale(0.5).add(0.0, 0.25, 0.0));
				chunk.needsSync = true;
				chunk.syncVelocity = true;
				SHRAPNEL.add(new Shrapnel(level.dimension(), chunk.getId(), level.getGameTime() + 6));
				com.jaymospowers.core.Debris.puff(level, pos, state, 1.5F);
				break;
			}
		}
		player.setDeltaMovement(away.scale(CLEAR_KNOCK).add(0.0, CLEAR_LIFT, 0.0));
		player.resetFallDistance();
		player.needsSync = true;
		player.syncVelocity = true;
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 1.0F, 0.6F);
	}

	private static void tickShrapnel(MinecraftServer server) {
		for (Iterator<Shrapnel> it = SHRAPNEL.iterator(); it.hasNext(); ) {
			Shrapnel s = it.next();
			ServerLevel level = server.getLevel(s.dimension());
			if (level == null) {
				it.remove();
				continue;
			}
			Entity chunk = level.getEntity(s.entity());
			if (chunk == null) {
				it.remove();
				continue;
			}
			if (level.getGameTime() >= s.until()) {
				it.remove();
				if (chunk instanceof net.minecraft.world.entity.item.FallingBlockEntity falling) {
					com.jaymospowers.core.Debris.puff(level, chunk.blockPosition(), falling.getBlockState(), 1.0F);
				}
				chunk.discard();
			}
		}
	}

	/** Hanging in the air for an aerial slam: the fall stops dead (sent to the player, whose client moves them). */
	private static void stall(ServerPlayer player) {
		Vec3 v = player.getDeltaMovement();
		player.setDeltaMovement(v.x * 0.3, 0.0, v.z * 0.3);
		player.resetFallDistance();
		player.needsSync = true;
		player.syncVelocity = true;
	}

	/** The slam has hit (or failed): the thrower's arm stays down a moment, then relaxes (see SlamPose). */
	private static void slamPoseLanded(MinecraftServer server, Slam slam, ServerLevel level) {
		ServerPlayer player = server.getPlayerList().getPlayer(slam.player());
		SlamPose pose = player == null ? null : player.getAttached(SLAM_POSE);
		if (pose != null && pose.start() == slam.started()) {
			player.setAttached(SLAM_POSE, new SlamPose(pose.start(), pose.rise(), level.getGameTime(), pose.style(), pose.aerial()));
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
		if (player.isShiftKeyDown()) {
			// sneak+G: no well; down turns sideways around you, and everything falls outward into the walls
			cool(player, WELL, WELL_COOLDOWN);
			GravitySideFall.well(player);
			return;
		}
		Vec3 at = player.position();
		WELLS.add(new Well(player.level().dimension(), at, player.getUUID(), player.level().getGameTime() + WELL_TICKS, false));
		cool(player, WELL, WELL_COOLDOWN);
		player.level().playSound(null, at.x, at.y, at.z, SoundEvents.PORTAL_TRIGGER, SoundSource.PLAYERS, 0.5F, 1.8F);
		// it goes off in a swelling bubble that shoves the ground up around it
		GravityShockwave.start(player.level(), at);
	}

	/** What Lift holds, if it's a living creature (not a chunk, an item, a cart or a corpse), or null. */
	private static @Nullable LivingEntity liftedCreature(ServerPlayer player, State state) {
		if (state.liftTarget < 0) {
			return null;
		}
		Entity held = player.level().getEntity(state.liftTarget);
		return held instanceof LivingEntity living && living.isAlive() && !(held instanceof ArmorStand) ? living : null;
	}

	/**
	 * B let go. A tap is a force kneel around you: everything within {@code CRUSH_TAP_RADIUS} is pinned and drops to
	 * its knees, and inside your own well the tap also launches you ({@link #wellLaunch}). Held, it's the Gravity Wave
	 * the way you look, as charged ({@link GravityWave}); Collapsing Horizon also rains its ring down where the wave ends.
	 */
	private static void releaseCrush(ServerPlayer player, State state, long held, float charge) {
		if (held < CRUSH_TAP_TICKS) {
			if (ownWell(player) != null) {
				wellLaunch(player, state);
			}
			crush(player, 0.0F, player.position(), CRUSH_TAP_RADIUS);
			return;
		}
		if (!ready(player, CRUSH)) {
			return;
		}
		ServerLevel level = player.level();
		if (GravityOrbit.collapsingHorizon(player)) {
			GravityOrbit.rain(player, aimPoint(player, GravityWave.range(charge) + GravityWave.COLLAPSE_RANGE_BONUS),
				Mth.lerp(charge, CRUSH_RADIUS_MIN, CRUSH_RADIUS_MAX) + COLLAPSE_RADIUS_BONUS);
		}
		GravityWave.fire(player, charge);
		int cooldown = Math.round(Mth.lerp(charge, CRUSH_COOLDOWN_MIN, CRUSH_COOLDOWN_MAX));
		player.setAttached(COOLDOWNS, cooldowns(player).withCrush(level.getGameTime() + cooldown, cooldown));
	}

	/** Your own well, still up (and not yet launched out of), or null. */
	private static @Nullable Well ownWell(ServerPlayer player) {
		for (Well well : WELLS) {
			if (!well.singularity() && well.owner().equals(player.getUUID()) && well.dimension() == player.level().dimension()) {
				return well;
			}
		}
		return null;
	}

	/** B while your well is up: launched straight up {@code WELL_LAUNCH_HEIGHT} (whatever you weigh). True if it happened. */
	private static boolean wellLaunch(ServerPlayer player, State state) {
		Well well = ownWell(player);
		if (well == null || state.launchedFrom == well.until()) {
			return false;
		}
		state.launchedFrom = well.until();
		ServerLevel level = player.level();
		double gravity = Math.max(0.01, player.getAttributeValue(Attributes.GRAVITY));
		// (with the air's drag, a touch more than v² = 2gh)
		double up = Math.max(0.8, Math.sqrt(2.0 * gravity * WELL_LAUNCH_HEIGHT) * 1.05);
		Vec3 launch = new Vec3(player.getDeltaMovement().x, up, player.getDeltaMovement().z);
		player.setDeltaMovement(launch);
		player.syncVelocity = true;
		net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player, new com.jaymospowers.power.web.WebNet.Nudge(launch));
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.MACE_SMASH_GROUND_HEAVY, SoundSource.PLAYERS, 1.2F, 1.3F);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BREEZE_JUMP, SoundSource.PLAYERS, 1.2F, 0.6F);
		level.sendParticles(ParticleTypes.REVERSE_PORTAL, player.getX(), player.getY() + 0.1, player.getZ(), 40, 0.6, 0.1, 0.6, 0.3);
		com.jaymospowers.core.Debris.puff(level, player.blockPosition().below(), level.getBlockState(player.blockPosition().below()), 2.0F);
		com.jaymospowers.core.Shakes.heavy(player, player.position(), 10.0, 0.8F);
		return true;
	}

	// ---------------------------------------------------------------- Crush

	private static void crush(ServerPlayer player, float charge, Vec3 at, double baseRadius) {
		if (!ready(player, CRUSH)) {
			return;
		}
		ServerLevel level = player.level();
		boolean collapsed = Fate.isCorrupted(player);
		double radius = baseRadius + (collapsed ? COLLAPSE_RADIUS_BONUS : 0.0);
		float pressure = (0.5F + 0.5F * charge) * (collapsed ? COLLAPSE_DAMAGE : 1.0F);
		// Collapsing Horizon's ring comes down with it, in a shockwave
		GravityOrbit.shockwave(player, at, radius);
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
			// no harm to them, only to what they wear
			int armor = target.getArmorValue();
			if (armor > 0) {
				crushing = true;
				try {
					target.hurtServer(level, level.damageSources().playerAttack(player), armor * CRUSH_ARMOR_DAMAGE * pressure);
				} finally {
					crushing = false;
				}
			}
			// too heavy to move
			target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, CRUSH_PIN_TICKS, CRUSH_PIN_SLOWNESS), player);
			target.addEffect(new MobEffectInstance(MobEffects.JUMP_BOOST, CRUSH_PIN_TICKS, 128, false, false), player);
			modifier(target, Attributes.GRAVITY, CRUSH_ID, CRUSH_WEIGHT, true);
			PINNED.removeIf(p -> p.entity().equals(target.getUUID()));
			PINNED.add(new Pinned(level.dimension(), target.getUUID(), level.getGameTime() + CRUSH_PIN_TICKS));
			kneel(target, level.getGameTime() + CRUSH_PIN_TICKS);
		}
		flatten(level, at, radius, player);
		// a ring slamming outward along the ground
		for (int i = 0; i < 24; i++) {
			double a = i / 24.0 * Math.PI * 2.0;
			level.sendParticles(ParticleTypes.EXPLOSION, at.x + Math.cos(a) * radius * 0.7, at.y + 0.2, at.z + Math.sin(a) * radius * 0.7, 1, 0.0, 0.0, 0.0, 0.0);
		}
		level.sendParticles(ParticleTypes.SQUID_INK, at.x, at.y + 0.5, at.z, 30, radius * 0.4, 0.3, radius * 0.4, 0.05);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.MACE_SMASH_GROUND_HEAVY, SoundSource.PLAYERS, 1.2F, 0.6F);
		com.jaymospowers.core.Shakes.heavy(player, at, radius * 2.5, 1.0F + 1.2F * charge);
		int cooldown = Math.round(Mth.lerp(charge, CRUSH_COOLDOWN_MIN, CRUSH_COOLDOWN_MAX));
		player.setAttached(COOLDOWNS, cooldowns(player).withCrush(level.getGameTime() + cooldown, cooldown));
	}

	/**
	 * Crush flattens everything light in the blast, at any charge: flowers, grass, torches and the like pop off and drop,
	 * and item frames and paintings come down.
	 */
	private static void flatten(ServerLevel level, Vec3 at, double radius, ServerPlayer player) {
		int r = Mth.ceil(radius);
		BlockPos center = BlockPos.containing(at);
		for (BlockPos pos : BlockPos.betweenClosed(center.offset(-r, -2, -r), center.offset(r, 3, r))) {
			if (pos.distToCenterSqr(at) > radius * radius) {
				continue;
			}
			BlockState state = level.getBlockState(pos);
			if (state.isAir() || !state.getFluidState().isEmpty() || state.hasBlockEntity() || state.is(net.minecraft.tags.BlockTags.PORTALS)) {
				continue;
			}
			float hardness = state.getDestroySpeed(level, pos);
			if (hardness >= 0.0F && hardness < 1.0F && state.getCollisionShape(level, pos).isEmpty()) {
				level.destroyBlock(pos, true, player);
			}
		}
		for (net.minecraft.world.entity.decoration.HangingEntity hanging : level.getEntitiesOfClass(
			net.minecraft.world.entity.decoration.HangingEntity.class, new AABB(at, at).inflate(radius, 3.0, radius))) {
			// twice: an item frame lets go of its item first, then comes down itself
			hanging.hurtServer(level, level.damageSources().playerAttack(player), 1.0F);
			if (hanging.isAlive()) {
				hanging.hurtServer(level, level.damageSources().playerAttack(player), 1.0F);
			}
		}
	}

	// ---------------------------------------------------------------- Singularity (C)

	private static void singularity(ServerPlayer player) {
		if (!ready(player, SINGULARITY)) {
			return;
		}
		Vec3 at = aimPoint(player, SINGULARITY_RANGE).add(0.0, 1.0, 0.0);
		WELLS.add(new Well(player.level().dimension(), at, player.getUUID(), player.level().getGameTime() + SINGULARITY_TICKS, true));
		// (0.16.24) over the shoulder, pushing in on it
		com.jaymospowers.core.UltimateFilm.play(player, com.jaymospowers.core.UltimateFilm.PUSH_IN, SINGULARITY_FILM_TICKS, at, null, 0.0);
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
			GravityForward.stop(player);
			if (player.getAttached(ROCK_FLIGHT) != null) {
				GravityRockFlight.stop(player);
			}
			return;
		}
		State state = state(player);
		smallThings(player);
		GravityWeight.tick(player);
		if (state.meteorFrom >= 0) {
			if (Fate.isAscended(player) && !Fate.isCorrupted(player)) {
				GravityMeteor.charging(player, meteorCharge(player, state), player.level().getGameTime() - state.meteorFrom);
			} else {
				state.meteorFrom = -1;
			}
		}
		if (state.ripFrom >= 0) {
			if (PowerInput.isDown(player, PowerKey.ABILITY_1)) {
				tickRip(player, state);
			} else {
				finishRip(player, state);
			}
		}
		Integer slab = player.getAttached(ROCK_FLIGHT);
		if (slab != null && !GravityRockFlight.toggles(player) && !PowerInput.isDown(player, PowerKey.ABILITY_3)) {
			// held-only riding, and V's no longer down
			GravityRockFlight.stop(player);
		} else if (slab != null) {
			GravityRockFlight.tick(player, slab);
		} else if (state.vDownAt >= 0 && player.level().getGameTime() - state.vDownAt >= ROCK_HOLD_TICKS
			&& PowerInput.isDown(player, PowerKey.ABILITY_3)) {
			// held, not tapped: up on a slab of the ground
			state.vDownAt = -1;
			if (player.getAttached(WEIGHTLESS_ON) != null) {
				endWeightless(player, state, WEIGHTLESS_EARLY_COOLDOWN);
			}
			if (!GravityRockFlight.start(player)) {
				fizzle(player);
			}
		}
		if (GravityOrbit.collapsingHorizon(player)) {
			GravityOrbit.tick(player);
		} else if (player.tickCount % 20 == 0) {
			GravityOrbit.drop(player);
		}
		// the Nether's weight takes hold of the power: corrupted on arrival
		if (player.level().dimension() == Level.NETHER && Fate.canCorrupt(player) && !player.isCreative()) {
			Fate.corrupt(player);
		}
		if (!com.jaymospowers.core.Mastery.isUnlocked(player, PowerKey.ABILITY_3) && PowerInput.isDown(player, PowerKey.ABILITY_3)) {
			magnet(player);
		}
		if (state.liftTarget >= 0) {
			tickLift(player, state);
		}
		if (state.crushFrom >= 0 && player.tickCount % HEAVY_AURA_REFRESH == 0) {
			crushZone(player, state);
		}
		GravityForward.tick(player);
		if (player.getAttached(WEIGHTLESS_ON) != null) {
			state.weightlessTicks++;
			// no fall counts while weightless: only what you fall after it ends
			player.resetFallDistance();
			if (player.tickCount % HEAVY_AURA_REFRESH == 0) {
				heavyAura(player);
			}
			player.causeFoodExhaustion(WEIGHTLESS_EXHAUSTION);
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
		double radius = PULL_RADIUS + PULL_RADIUS_PER_LEVEL * com.jaymospowers.core.Mastery.unlocked(player);
		for (ItemEntity item : player.level().getEntitiesOfClass(ItemEntity.class, player.getBoundingBox().inflate(radius),
			e -> e.isAlive() && !e.hasPickUpDelay())) {
			Vec3 to = me.subtract(item.position());
			double d = to.length();
			if (d > 0.5 && d <= radius) {
				item.setDeltaMovement(item.getDeltaMovement().add(to.scale(PULL_STRENGTH * 2.0 / d)));
				item.needsSync = true;
				pulled = true;
			}
		}
		if (pulled && player.tickCount % 40 == 0) {
			com.jaymospowers.core.Mastery.used(player);
		}
	}

	/**
	 * Before Weightless: V held drags loose items (and experience) in from {@link #MAGNET_RADIUS}, fast. Let go and
	 * it's back to the gentle pull of small things.
	 */
	private static void magnet(ServerPlayer player) {
		Vec3 me = player.position().add(0.0, player.getBbHeight() * 0.5, 0.0);
		ServerLevel level = player.level();
		boolean pulled = false;
		for (Entity loose : level.getEntities(player, player.getBoundingBox().inflate(MAGNET_RADIUS),
			e -> e.isAlive() && (e instanceof ItemEntity || e instanceof net.minecraft.world.entity.ExperienceOrb))) {
			Vec3 to = me.subtract(loose.position());
			double d = to.length();
			if (d > MAGNET_RADIUS || d < 0.4) {
				continue;
			}
			loose.setDeltaMovement(to.scale(Math.min(MAGNET_SPEED, d * 0.45) / d));
			loose.needsSync = true;
			loose.syncVelocity = true;
			pulled = true;
			if (player.tickCount % 4 == 0) {
				level.sendParticles(ParticleTypes.REVERSE_PORTAL, loose.getX(), loose.getY() + 0.15, loose.getZ(), 1, 0.05, 0.05, 0.05, 0.0);
			}
		}
		if (player.tickCount % 10 == 0) {
			level.sendParticles(ParticleTypes.REVERSE_PORTAL, me.x, me.y, me.z, 6, 0.4, 0.5, 0.4, 0.02);
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
	 * The Gravity Wave being charged (B held): the pressure gathers in front of you, more of it the longer it's held
	 * (and Collapsing Horizon's ring is drawn to where it'll rain).
	 */
	private static void crushZone(ServerPlayer player, State state) {
		float charge = Mth.clamp((player.level().getGameTime() - state.crushFrom) / (float)CRUSH_FULL_CHARGE, 0.0F, 1.0F);
		ServerLevel level = player.level();
		Vec3 hand = player.getEyePosition().add(player.getViewVector(1.0F).scale(1.2)).subtract(0.0, 0.3, 0.0);
		double spread = 0.15 + charge * 0.3;
		level.sendParticles(ParticleTypes.REVERSE_PORTAL, hand.x, hand.y, hand.z, 3 + Math.round(charge * 9), spread, spread, spread, 0.02);
		if (GravityOrbit.collapsingHorizon(player)) {
			GravityOrbit.drawTo(player, aimPoint(player, GravityWave.range(charge) + GravityWave.COLLAPSE_RANGE_BONUS));
		}
	}

	/** Everyone (but the user) within {@code radius} of {@code center} is heavy for a moment (refresh it to keep them so). */
	/** Forced to its knees until {@code until} (or longer, if it's already kneeling for longer). */
	static void kneel(LivingEntity living, long until) {
		Kneeling was = KNEELING.get(living.getUUID());
		if (was != null && was.until() >= until) {
			return;
		}
		KNEELING.put(living.getUUID(), new Kneeling(living.level().dimension(), living.getUUID(), until));
		if (living.getAttached(KNEEL) == null) {
			living.setAttached(KNEEL, new Kneel(living.getRandom().nextInt(KNEEL_STYLES), living.level().getGameTime()));
		}
	}

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
		GravitySideFall.tick(server);
		GravityWave.tick(server);
		GravityGutPunch.tick(server);
		GravityWalk.tick(server);
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
		if (!SHRAPNEL.isEmpty()) {
			tickShrapnel(server);
		}
		if (!THROWN.isEmpty()) {
			tickThrown(server);
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
		if (!PLUMMETS.isEmpty()) {
			tickPlummets(server);
		}
		GravityPush.tick(server);
		GravityShockwave.tick(server);
		GravityMeteor.tick(server);
		GravitySkyfall.tick(server);
		GravityOrbit.tickRain(server);
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
		if (!KNEELING.isEmpty()) {
			for (Iterator<Kneeling> it = KNEELING.values().iterator(); it.hasNext(); ) {
				Kneeling kneeling = it.next();
				ServerLevel level = server.getLevel(kneeling.dimension());
				Entity entity = level == null ? null : level.getEntity(kneeling.entity());
				if (entity == null || !entity.isAlive() || level.getGameTime() >= kneeling.until()) {
					it.remove();
					if (entity != null) {
						// the weight's off: they get back up
						entity.removeAttached(KNEEL);
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
		if (!well.singularity()) {
			tickField(level, well, owner, left);
			return;
		}
		double radius = SINGULARITY_PULL_RADIUS;
		double pull = SINGULARITY_PULL;
		if (owner != null && Fate.isCorrupted(owner)) {
			pull *= COLLAPSE_DAMAGE;
		}
		Vec3 at = well.at();
		AABB area = new AABB(at, at).inflate(radius);
		boolean hit = left % SINGULARITY_HIT_EVERY == 0;
		for (Entity e : level.getEntities((Entity)null, area, e -> e.isAlive() && !e.isSpectator() && !(e instanceof ArmorStand)
			&& (owner == null || e != owner) && !(e instanceof Player p && (p.isCreative() || p.isSpectator()))
			&& !(e instanceof com.jaymospowers.core.BlockCluster cluster && cluster.lingering()))) {
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
		level.sendParticles(ParticleTypes.SQUID_INK, at.x, at.y, at.z, 6, 0.3, 0.3, 0.3, 0.02);
		level.sendParticles(ParticleTypes.PORTAL, at.x, at.y, at.z, 16, radius * 0.3, radius * 0.3, radius * 0.3, -1.0);
		if (left % 20 == 0) {
			level.playSound(null, at.x, at.y, at.z, SoundEvents.RESPAWN_ANCHOR_DEPLETE.value(), SoundSource.PLAYERS, 1.0F, 0.5F);
		}
	}

	/**
	 * The Gravity Well, around its owner (it goes where they go): everything that touches it is weighed down and forced
	 * to its knees. Nothing is pulled.
	 */
	private static void tickField(ServerLevel level, Well well, @Nullable ServerPlayer owner, long left) {
		if (owner == null || !owner.isAlive() || owner.level() != level) {
			return;
		}
		Vec3 at = owner.position();
		double radius = WELL_RADIUS;
		if (left % HEAVY_AURA_REFRESH == 0) {
			weighDown(owner, at, radius);
		}
		for (LivingEntity living : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(radius, radius + 2.0, radius),
			e -> e != owner && e.isAlive() && !e.isSpectator() && !(e instanceof ArmorStand) && !(e instanceof Player p && p.isCreative())
				&& e.getBoundingBox().distanceToSqr(at) <= radius * radius)) {
			touch(living, owner);
			kneel(living, level.getGameTime() + KNEEL_WELL_LINGER);
		}
		if (left % 2 == 0) {
			// its edge, a ring of pressure pressing down round you
			for (int i = 0; i < 10; i++) {
				double ang = left * 0.08 + i / 10.0 * Math.PI * 2.0;
				level.sendParticles(ParticleTypes.REVERSE_PORTAL, at.x + Math.cos(ang) * radius, at.y + 0.2, at.z + Math.sin(ang) * radius, 1, 0.0, 0.0, 0.0, -0.05);
			}
			level.sendParticles(ParticleTypes.PORTAL, at.x, at.y + 1.0, at.z, 6, radius * 0.5, 0.6, radius * 0.5, -0.3);
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
		com.jaymospowers.core.Shakes.around(level, at, 28.0, 1.8F);
	}

	// ---------------------------------------------------------------- deaths by weight

	/** A camera shake for everyone within {@code range} of {@code at}, weaker with distance, felt from where it hit. */
	static void shake(ServerLevel level, Vec3 at, double range, float strength) {
		com.jaymospowers.core.Shakes.around(level, at, range, strength);
	}

	static void touch(LivingEntity target, ServerPlayer player) {
		TOUCHED.put(target.getUUID(), new Touch(player.getUUID(), player.level().getGameTime()));
	}

	/** {@code player}'s gravity hurts {@code target}: a death by it counts as one by weight (the Gravity Wave, Inversion). */
	static void hurtByWeight(LivingEntity target, ServerPlayer player, float damage) {
		touch(target, player);
		crushing = true;
		try {
			target.hurtServer(player.level(), player.damageSources().playerAttack(player), damage);
		} finally {
			crushing = false;
		}
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
		if (byWeight && !entity.getUUID().equals(touch.player())) {
			// crushed to death under the weight (a meteor's too): it bursts (Andy)
			com.jaymospowers.core.BladeBlood.crushed(entity);
		}
		ServerPlayer player = level.getServer().getPlayerList().getPlayer(touch.player());
		if (!byWeight || player == null || !Powers.has(player, INSTANCE) || entity == player) {
			return;
		}
		countTowardAscension(player);
	}

	/** A death by weight, or a hard fall survived: one closer to the Event Horizon. */
	static void countTowardAscension(ServerPlayer player) {
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

	static void modifier(Entity entity, Holder<Attribute> attribute, Identifier id, double amount, boolean on) {
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
	public static Vec3 aimPoint(ServerPlayer player, double range) {
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
