package com.jaymospowers.power.zfighter;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.AbilityPower;
import com.jaymospowers.core.BlockCluster;
import com.jaymospowers.core.BlowClash;
import com.jaymospowers.core.GroundCracks;
import com.jaymospowers.core.Powers;
import com.jaymospowers.core.fx.MagicFx;
import io.netty.buffer.ByteBuf;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Full Great Ape move set alongside {@link ApeSmash}, {@link ApeGrab} and {@link ApeEarthwork}:
 * <ul>
 * <li><b>Swipes (left and right)</b>: quick left-click tap alternates wide left and right arm swipes that fling foes
 * sideways across the arc.</li>
 * <li><b>Roar</b> (Sprint+R): rears back and looses a shockwave roar that staggers and throws back foes around him.</li>
 * <li><b>Ground slam pose</b>: plays a two-handed overhead slam on {@link ApeEarthwork} stomps.</li>
 * <li><b>Leap smash</b> (Sneak+B, or B while airborne): springs forward and high, then crashes down in a cratering
 * shockwave.</li>
 * <li><b>Boulder throw</b> (Sneak+G): scoops a giant chunk of earth overhead and hurls it where he looks.</li>
 * </ul>
 * Each move syncs its {@link Action} to every client so {@code ZFighterApe.Model} poses it distinctly.
 */
public final class ApeMoves {
	// ---- move kinds (synced in ACTION) ----
	public static final int NONE = 0;
	public static final int SWIPE_LEFT = 1;
	public static final int SWIPE_RIGHT = 2;
	public static final int ROAR = 3;
	public static final int GROUND_SLAM = 4;
	public static final int LEAP_AIR = 5;
	public static final int LEAP_LAND = 6;
	public static final int BOULDER_THROW = 7;

	// ---- durations (ticks) ----
	public static final int SWIPE_TICKS = 12;
	public static final int ROAR_TICKS = 26;
	public static final int SLAM_TICKS = 16;
	public static final int LEAP_LAND_TICKS = 14;
	public static final int BOULDER_LIFT_TICKS = 14;
	public static final int BOULDER_TICKS = 26;

	// ---- gating ----
	/** Minimum ape growth fraction before ape moves may start or resolve. */
	static final float MIN_APE_SIZE = 0.5F;

	// ---- swipes ----
	/** Left-click released sooner than this (ticks) swipes instead of charging a smash. */
	static final int SWIPE_TAP_TICKS = 6;
	static final int SWIPE_COOLDOWN = 10;
	static final double SWIPE_REACH = 9.5;
	static final double SWIPE_ARC = 0.05;
	static final float SWIPE_DAMAGE = 1.05F;
	static final double SWIPE_THROW = 1.35, SWIPE_LIFT = 0.45;
	static final float SWIPE_SHAKE = 0.45F;
	static final double SWIPE_SHAKE_RANGE = 30.0;
	static final int SWIPE_COLOR = 0xD8B080;

	// ---- roar ----
	static final int ROAR_COOLDOWN = 120;
	static final double ROAR_RADIUS = 18.0;
	static final float ROAR_DAMAGE = 0.6F;
	static final double ROAR_PUSH = 1.6, ROAR_LIFT = 0.4;
	static final int ROAR_SLOW_TICKS = 60, ROAR_WEAK_TICKS = 60;
	static final float ROAR_SHAKE = 1.4F;
	static final double ROAR_SHAKE_RANGE = 56.0;
	static final int ROAR_COLOR = 0xE06030;

	// ---- leap smash ----
	static final int LEAP_COOLDOWN = 140;
	static final double LEAP_FORWARD = 1.45, LEAP_UP = 1.3;
	static final double DIVE_FORWARD = 1.1, DIVE_DOWN = -1.0;
	static final int LEAP_MIN_AIR = 4, LEAP_MAX_AIR = 70;
	static final double LEAP_RADIUS = 10.5;
	static final float LEAP_DAMAGE = 2.2F;
	static final double LEAP_THROW = 1.7, LEAP_LIFT = 0.85;
	static final float LEAP_SHAKE = 1.5F;
	static final double LEAP_SHAKE_RANGE = 52.0;
	static final int LEAP_COLOR = 0xC07840;

	// ---- boulder throw ----
	static final int BOULDER_COOLDOWN = 100;
	static final int BOULDER_BLOCKS = 9;
	static final float BOULDER_HARDEST = 50.0F;
	static final float BOULDER_SCALE_PER_SIZE = 0.45F, BOULDER_SCALE_MAX = 4.0F;
	static final double BOULDER_SPEED = 2.0, BOULDER_LIFT_VY = 0.22;
	static final double BOULDER_CLIMB = 5.0;
	static final int BOULDER_COLOR = 0x9A8B7A;

	/** Synced Great Ape move for client posing. */
	public record Action(int kind, long start) {
		public static final StreamCodec<ByteBuf, Action> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, Action::kind,
			ByteBufCodecs.VAR_LONG, Action::start,
			Action::new);
	}

	public static final AttachmentType<Action> ACTION = AttachmentRegistry.create(JaymosPowers.id("zfighter_ape_action"),
		builder -> builder.syncWith(Action.STREAM_CODEC, AttachmentSyncPredicate.all()));

	private static final Set<UUID> NEXT_LEFT = new HashSet<>();
	private static final Map<UUID, Long> ROAR_READY = new HashMap<>();
	private static final Map<UUID, Long> SWIPE_READY = new HashMap<>();
	private static final Map<UUID, Long> LEAP_START = new HashMap<>();

	private record Boulder(int entityId, long start) {
	}

	private static final Map<UUID, Boulder> BOULDERS = new HashMap<>();

	private ApeMoves() {
	}

	static void init() {
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> clear(handler.player));
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			NEXT_LEFT.clear();
			ROAR_READY.clear();
			SWIPE_READY.clear();
			LEAP_START.clear();
			BOULDERS.clear();
		});
	}

	/** Whether this ape can start a move now. */
	static boolean able(ServerPlayer player, ZFighterPower.Live live) {
		return Powers.has(player, ZFighterPower.INSTANCE) && ZFighterLineage.ape(player) && live.apeSize >= MIN_APE_SIZE
			&& !ZFighterLineage.crippled(player) && !BlowClash.locked(player)
			&& !player.hasAttached(ApeRage.FIT) && !player.hasAttached(ZFighterLineage.BERSERK)
			&& !ApeGrab.grabbing(player) && !BOULDERS.containsKey(player.getUUID());
	}

	/** Whether this ape currently has an in-flight leap smash or hoisted boulder. */
	static boolean busy(ServerPlayer player) {
		UUID id = player.getUUID();
		return LEAP_START.containsKey(id) || BOULDERS.containsKey(id);
	}

	/** How long a synced action pose lasts (ticks); LEAP_AIR lasts until landing. */
	public static int duration(int kind) {
		return switch (kind) {
			case SWIPE_LEFT, SWIPE_RIGHT -> SWIPE_TICKS;
			case ROAR -> ROAR_TICKS;
			case GROUND_SLAM -> SLAM_TICKS;
			case LEAP_AIR -> LEAP_MAX_AIR;
			case LEAP_LAND -> LEAP_LAND_TICKS;
			case BOULDER_THROW -> BOULDER_TICKS;
			default -> 0;
		};
	}

	// ---------------------------------------------------------------- swipes (left & right)

	/** Alternating left and right arm swipes across a wide front arc. */
	static boolean swipe(ServerPlayer player, ZFighterPower.Live live) {
		if (!able(player, live)) {
			return false;
		}
		ServerLevel level = player.level();
		long now = level.getGameTime();
		if (now < SWIPE_READY.getOrDefault(player.getUUID(), 0L)) {
			return false;
		}
		SWIPE_READY.put(player.getUUID(), now + SWIPE_COOLDOWN);
		boolean left = NEXT_LEFT.add(player.getUUID());
		if (!left) {
			NEXT_LEFT.remove(player.getUUID());
		}
		player.setAttached(ACTION, new Action(left ? SWIPE_LEFT : SWIPE_RIGHT, now));

		Vec3 look = flatLook(player);
		Vec3 right = new Vec3(-look.z, 0.0, look.x);
		double reach = AbilityPower.size(player, SWIPE_REACH) * live.apeSize;
		float damage = (float)player.getAttributeValue(Attributes.ATTACK_DAMAGE) * SWIPE_DAMAGE;
		Vec3 chest = player.position().add(0.0, player.getBbHeight() * 0.42, 0.0);

		// sweep arc visual from outer arm side across to the far side
		double dirSign = left ? -1.0 : 1.0;
		Vec3 start = chest.add(right.scale(dirSign * reach * 0.65)).add(look.scale(reach * 0.35));
		Vec3 end = chest.add(right.scale(-dirSign * reach * 0.65)).add(look.scale(reach * 0.55));
		MagicFx.stroke(level, start, end, 1.8F * live.apeSize, SWIPE_COLOR);

		Vec3 flingDir = right.scale(-dirSign).add(look.scale(0.35)).normalize();
		for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(reach, 3.0, reach),
			e -> eligible(player, e))) {
			Vec3 to = victim.position().subtract(player.position()).multiply(1.0, 0.0, 1.0);
			double dist = to.length();
			if (dist > reach + victim.getBbWidth() * 0.5) {
				continue;
			}
			if (dist > 0.5 && to.scale(1.0 / dist).dot(look) < SWIPE_ARC) {
				continue;
			}
			if (victim.hurtServer(level, ZFighterLineage.blow(level, player), damage)) {
				victim.push(flingDir.x * SWIPE_THROW, SWIPE_LIFT, flingDir.z * SWIPE_THROW);
				victim.syncVelocity = true;
				level.sendParticles(ParticleTypes.CRIT, true, false, victim.getX(), victim.getY(0.5), victim.getZ(), 8, 0.4, 0.4, 0.4, 0.2);
			}
		}
		level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, true, false, chest.x + look.x * reach * 0.4, player.getY() + 0.4,
			chest.z + look.z * reach * 0.4, 10, reach * 0.3, 0.3, reach * 0.3, 0.04);
		level.playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 2.2F, left ? 0.45F : 0.52F);
		level.playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.RAVAGER_ATTACK, SoundSource.PLAYERS, 1.6F, 0.6F);
		ZFighterLineage.shake(player, chest, SWIPE_SHAKE_RANGE, SWIPE_SHAKE * live.apeSize);
		return true;
	}

	// ---------------------------------------------------------------- roar (Sprint+R)

	/** Deafening ape roar on Sprint+R: shockwave ring, knockback, slow and weakness. */
	static boolean roar(ServerPlayer player, ZFighterPower.Live live) {
		if (!able(player, live)) {
			return false;
		}
		ServerLevel level = player.level();
		long now = level.getGameTime();
		if (now < ROAR_READY.getOrDefault(player.getUUID(), 0L)) {
			return false;
		}
		ROAR_READY.put(player.getUUID(), now + ROAR_COOLDOWN);
		player.setAttached(ACTION, new Action(ROAR, now));

		Vec3 at = player.position();
		double radius = AbilityPower.size(player, ROAR_RADIUS) * live.apeSize;
		float damage = (float)player.getAttributeValue(Attributes.ATTACK_DAMAGE) * ROAR_DAMAGE;
		MagicFx.ring(level, at.add(0.0, 0.4, 0.0), (float)radius, ROAR_COLOR);
		MagicFx.blast(level, at.add(0.0, player.getBbHeight() * 0.75, 0.0), (float)(radius * 0.35), ROAR_COLOR);
		for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(radius, 6.0, radius),
			e -> eligible(player, e))) {
			double d = victim.position().distanceTo(at);
			if (d > radius + victim.getBbWidth() * 0.5) {
				continue;
			}
			float falloff = (float)(1.0 - 0.5 * Math.min(1.0, d / radius));
			if (victim.hurtServer(level, ZFighterLineage.blow(level, player), damage * falloff)) {
				Vec3 away = victim.position().subtract(at).multiply(1.0, 0.0, 1.0);
				away = away.lengthSqr() < 1.0E-4 ? flatLook(player) : away.normalize();
				victim.push(away.x * ROAR_PUSH * falloff, ROAR_LIFT * falloff, away.z * ROAR_PUSH * falloff);
				victim.syncVelocity = true;
				victim.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, ROAR_SLOW_TICKS, 1), player);
				victim.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, ROAR_WEAK_TICKS, 0), player);
			}
		}
		level.playSound(null, at.x, player.getEyeY(), at.z, SoundEvents.WARDEN_ROAR, SoundSource.PLAYERS, 4.5F, 0.42F);
		level.playSound(null, at.x, player.getEyeY(), at.z, SoundEvents.RAVAGER_ROAR, SoundSource.PLAYERS, 4.0F, 0.38F);
		ZFighterLineage.shake(player, at, ROAR_SHAKE_RANGE, ROAR_SHAKE * live.apeSize);
		return true;
	}

	// ---------------------------------------------------------------- ground slam pose

	/** Sets the two-handed ground slam pose when {@link ApeEarthwork} stomps. */
	static void groundSlamPose(ServerPlayer player) {
		player.setAttached(ACTION, new Action(GROUND_SLAM, player.level().getGameTime()));
	}

	// ---------------------------------------------------------------- leap smash (Sneak+B or airborne B)

	/** Springs forward and high (or dives from midair) and slams the earth on landing. */
	static boolean leapSmash(ServerPlayer player, ZFighterPower.Live live) {
		if (!able(player, live) || LEAP_START.containsKey(player.getUUID()) || !ZFighterPower.ready(player, ZFighterState.STEP)) {
			return false;
		}
		ServerLevel level = player.level();
		long now = level.getGameTime();
		ZFighterPower.cooldown(player, ZFighterState.STEP, LEAP_COOLDOWN);
		LEAP_START.put(player.getUUID(), now);
		player.setAttached(ACTION, new Action(LEAP_AIR, now));

		Vec3 look = flatLook(player);
		if (player.onGround()) {
			player.setDeltaMovement(look.x * LEAP_FORWARD, LEAP_UP, look.z * LEAP_FORWARD);
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.RAVAGER_ROAR, SoundSource.PLAYERS, 2.5F, 0.55F);
			level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, true, false, player.getX(), player.getY() + 0.3, player.getZ(), 14, 1.6, 0.2, 1.6, 0.05);
		} else {
			player.setDeltaMovement(look.x * DIVE_FORWARD, DIVE_DOWN, look.z * DIVE_FORWARD);
			level.playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.BREEZE_JUMP, SoundSource.PLAYERS, 2.2F, 0.45F);
		}
		player.syncVelocity = true;
		return true;
	}

	private static void leapImpact(ServerPlayer player, ZFighterPower.Live live, ServerLevel level, long now) {
		player.setAttached(ACTION, new Action(LEAP_LAND, now));
		Vec3 at = player.position();
		double radius = AbilityPower.size(player, LEAP_RADIUS) * live.apeSize;
		float damage = (float)player.getAttributeValue(Attributes.ATTACK_DAMAGE) * LEAP_DAMAGE;
		ZFighterSenses.impactFrame(level, at);
		MagicFx.ring(level, at.add(0.0, 0.25, 0.0), (float)radius, LEAP_COLOR);
		for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(radius, 4.0, radius),
			e -> eligible(player, e))) {
			double d = victim.position().distanceTo(at);
			if (d > radius + victim.getBbWidth() * 0.5) {
				continue;
			}
			float falloff = (float)(1.0 - 0.45 * Math.min(1.0, d / radius));
			if (victim.hurtServer(level, ZFighterLineage.blow(level, player), damage * falloff)) {
				Vec3 away = victim.position().subtract(at).multiply(1.0, 0.0, 1.0);
				away = away.lengthSqr() < 1.0E-4 ? flatLook(player) : away.normalize();
				victim.push(away.x * LEAP_THROW * falloff, LEAP_LIFT * falloff, away.z * LEAP_THROW * falloff);
				victim.syncVelocity = true;
			}
		}
		BlockPos under = BlockPos.containing(at.x, at.y - 0.5, at.z);
		BlockState ground = level.getBlockState(under);
		if (!ground.isAir()) {
			level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), true, false, at.x, at.y + 0.3, at.z, 120, radius * 0.45, 0.5,
				radius * 0.45, 0.45);
		}
		level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, true, false, at.x, at.y + 0.8, at.z, 2, radius * 0.2, 0.3, radius * 0.2, 0.0);
		level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, true, false, at.x, at.y + 0.3, at.z, 18, radius * 0.4, 0.2, radius * 0.4, 0.04);
		GroundCracks.crack(level, under, level.getRandom(), Math.max(4, (int)Math.round(radius * 0.6)));
		level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 3.8F, 0.42F);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.MACE_SMASH_GROUND_HEAVY, SoundSource.PLAYERS, 3.0F, 0.5F);
		ZFighterLineage.shake(player, at, LEAP_SHAKE_RANGE, LEAP_SHAKE * live.apeSize);
	}

	// ---------------------------------------------------------------- boulder throw (Sneak+G)

	/** Tears a boulder out of the earth, hoists it overhead and hurls it along the ape's aim. */
	static boolean boulderThrow(ServerPlayer player, ZFighterPower.Live live) {
		if (!able(player, live) || !player.onGround() || !player.mayBuild() || !ZFighterPower.ready(player, ZFighterState.BLAST)) {
			return false;
		}
		ServerLevel level = player.level();
		Vec3 ahead = player.position().add(flatLook(player).scale(player.getBbWidth() * 0.65));
		BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, BlockPos.containing(ahead.x, 0.0, ahead.z));
		double y = Math.abs(top.getY() - player.getY()) > BOULDER_CLIMB ? player.getY() : top.getY();
		BlockPos spot = BlockPos.containing(ahead.x, y - 0.5, ahead.z);
		var taken = BlockCluster.crater(level, spot, BOULDER_BLOCKS, BOULDER_HARDEST, player);
		if (taken.isEmpty()) {
			return false;
		}
		BlockCluster boulder = BlockCluster.of(level, taken, Vec3.ZERO, player);
		if (boulder == null) {
			return false;
		}
		long now = level.getGameTime();
		ZFighterPower.cooldown(player, ZFighterState.BLAST, BOULDER_COOLDOWN);
		boulder.setAttached(BlockCluster.SCALE, Math.min(BOULDER_SCALE_MAX, Math.max(1.5F, player.getScale() * BOULDER_SCALE_PER_SIZE)));
		boulder.script(true, true);
		BOULDERS.put(player.getUUID(), new Boulder(boulder.getId(), now));
		player.setAttached(ACTION, new Action(BOULDER_THROW, now));

		Vec3 at = Vec3.atCenterOf(spot);
		MagicFx.ring(level, at, 4.0F * live.apeSize, BOULDER_COLOR);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 2.0F, 0.5F);
		ZFighterLineage.shake(player, at, 36.0, 0.9F * live.apeSize);
		return true;
	}

	// ---------------------------------------------------------------- tick & cleanup

	/** Called each tick from {@link ZFighterLineage#ape} while the Great Ape is active. */
	static void tick(ServerPlayer player, ZFighterPower.Live live, ServerLevel level, long now) {
		UUID id = player.getUUID();
		if (live.apeSize < MIN_APE_SIZE) {
			if (LEAP_START.containsKey(id) || BOULDERS.containsKey(id) || player.hasAttached(ACTION)) {
				clear(player);
			}
			return;
		}
		// leap smash landing
		Long leapAt = LEAP_START.get(id);
		if (leapAt != null) {
			long air = now - leapAt;
			if (air >= LEAP_MIN_AIR && player.onGround() || air >= LEAP_MAX_AIR) {
				LEAP_START.remove(id);
				leapImpact(player, live, level, now);
			}
		}
		// boulder hoist and release
		Boulder held = BOULDERS.get(id);
		if (held != null) {
			if (!(level.getEntity(held.entityId()) instanceof BlockCluster boulder) || boulder.isRemoved()) {
				BOULDERS.remove(id);
			} else {
				long elapsed = now - held.start();
				Vec3 overhead = player.position().add(0.0, player.getBbHeight() * 1.05, 0.0).add(flatLook(player).scale(player.getBbWidth() * 0.25));
				if (elapsed < BOULDER_LIFT_TICKS) {
					double u = Mth.clamp(elapsed / (double)BOULDER_LIFT_TICKS, 0.0, 1.0);
					u = u * u * (3.0 - 2.0 * u);
					Vec3 pos = boulder.position().lerp(overhead, Math.max(u, 0.35));
					boulder.setPos(pos.x, pos.y, pos.z);
					boulder.setDeltaMovement(Vec3.ZERO);
				} else {
					BOULDERS.remove(id);
					Vec3 aim = player.getViewVector(1.0F);
					boulder.setPos(overhead.x, overhead.y, overhead.z);
					boulder.script(false, false);
					boulder.setDeltaMovement(aim.scale(BOULDER_SPEED).add(0.0, BOULDER_LIFT_VY, 0.0));
					boulder.syncVelocity = true;
					boulder.launch(player);
					level.playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.RAVAGER_ROAR, SoundSource.PLAYERS, 2.5F, 0.52F);
					level.playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 2.0F, 0.45F);
				}
			}
		}
		// expire finished pose attachment
		Action action = player.getAttached(ACTION);
		if (action != null && action.kind() != LEAP_AIR) {
			int len = duration(action.kind());
			if (now - action.start() >= len) {
				player.removeAttached(ACTION);
			}
		}
	}

	/** Ends any active ape move when the Great Ape ends or the player disconnects. */
	static void clear(ServerPlayer player) {
		UUID id = player.getUUID();
		LEAP_START.remove(id);
		Boulder held = BOULDERS.remove(id);
		if (held != null && player.level() instanceof ServerLevel level && level.getEntity(held.entityId()) instanceof BlockCluster boulder) {
			boulder.script(false, false);
			boulder.setDeltaMovement(Vec3.ZERO);
		}
		if (player.hasAttached(ACTION)) {
			player.removeAttached(ACTION);
		}
	}

	private static boolean eligible(ServerPlayer player, LivingEntity e) {
		return e != player && e.isAlive() && !e.isSpectator() && !(e instanceof ArmorStand)
			&& !player.isPassengerOfSameVehicle(e) && !(e instanceof Player other && other.isCreative());
	}

	private static Vec3 flatLook(ServerPlayer player) {
		Vec3 look = player.getLookAngle().multiply(1.0, 0.0, 1.0);
		return look.lengthSqr() < 1.0E-4 ? Vec3.directionFromRotation(0.0F, player.getYRot()) : look.normalize();
	}
}
