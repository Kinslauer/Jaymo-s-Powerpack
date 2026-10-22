package com.jaymospowers.power.web;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.Debris;
import io.netty.buffer.ByteBuf;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * The big body's slam: jump, and while Jump is held in the air it rears up into an overhead slam, both fists raised
 * over its head (over {@link #RAISE_TICKS}). Landing still holding it, it brings them down with the whole body: a
 * crash that hurts and hurls everything within {@link #RADIUS} ({@link #DAMAGE}, more from higher up), blows out a
 * shallow crater of debris (light terrain damage) and shakes every screen near. Let go in the air and it's called off.
 * <p>
 * Any web slinger's wing freedive that comes down straight onto the ground lands in a superhero landing that breaks
 * the ground ({@link #diveSlam}): a crater as deep as the dive was fast, and everything near hurled away.
 */
public final class WebSlam {
	/** The stance builds over this long (ticks); it must be held this long for a landing to slam. */
	public static final int RAISE_TICKS = 10, MIN_TICKS = 5;
	/** Reach (blocks), damage at the centre (half at the edge), plus this per block fallen (up to {@code FALL_BONUS_MAX}). */
	static final double RADIUS = 4.5;
	static final float DAMAGE = 12.0F, FALL_BONUS = 0.8F, FALL_BONUS_MAX = 10.0F;
	/** Knockback out and up; the crater (a {@link Debris#smash} at this speed: dirt and sand go, stone mostly holds). */
	static final double KNOCK = 1.3, LIFT = 0.6, SMASH_SPEED = 1.5;
	static final int RING = 4;
	static final float SHAKE = 2.2F, NEAR_SHAKE = 1.0F;
	static final double NEAR_SHAKE_RANGE = 16.0;
	/** Ticks before it can slam again. */
	static final int COOLDOWN = 20;
	/**
	 * The dive's landing: it needs a fall this far (blocks) at this speed (blocks/tick), told to the server within
	 * {@code DIVE_WINDOW} ticks of touching down. Reach, damage (half at the edge) plus this per block/tick of speed.
	 */
	static final double DIVE_MIN_DROP = 6.0, DIVE_MIN_SPEED = 1.0;
	static final int DIVE_WINDOW = 8;
	static final double DIVE_RADIUS = 4.0, DIVE_KNOCK = 1.1, DIVE_LIFT = 0.55;
	static final float DIVE_DAMAGE = 6.0F, DIVE_DAMAGE_PER_SPEED = 4.0F;
	static final float DIVE_SHAKE = 1.8F;

	/** In the stance: since when (game time). Synced, for everyone's pose. */
	public static final AttachmentType<Long> STANCE = AttachmentRegistry.create(JaymosPowers.id("web_slam"),
		builder -> builder.syncWith(ByteBufCodecs.VAR_LONG.cast(), AttachmentSyncPredicate.all()));

	/** Client → server: 1 rearing up (Jump held in the air), 2 landed still holding it, 0 called off. */
	public record Slam(int phase) implements CustomPacketPayload {
		public static final Type<Slam> TYPE = new Type<>(JaymosPowers.id("web_slam"));
		public static final StreamCodec<ByteBuf, Slam> CODEC = ByteBufCodecs.VAR_INT.map(Slam::new, Slam::phase);

		@Override
		public Type<Slam> type() {
			return TYPE;
		}
	}

	/** Client → server: my wing dive just came down straight onto the ground. */
	public record Dive() implements CustomPacketPayload {
		public static final Type<Dive> TYPE = new Type<>(JaymosPowers.id("web_dive_slam"));
		public static final StreamCodec<ByteBuf, Dive> CODEC = StreamCodec.unit(new Dive());

		@Override
		public Type<Dive> type() {
			return TYPE;
		}
	}

	/** The fall in progress (top of it, last height) and the last touchdown (when, how far, how fast). */
	private record Fall(double peak, double lastY, double vy, long landed, double drop, double speed) {
	}

	private static final java.util.Map<java.util.UUID, Fall> FALLS = new java.util.HashMap<>();
	private static final java.util.Map<java.util.UUID, Long> READY = new java.util.HashMap<>();
	/** The highest it's been in the stance (for how far it fell). */
	private static final java.util.Map<java.util.UUID, Double> PEAK = new java.util.HashMap<>();

	private WebSlam() {
	}

	static void register() {
		PayloadTypeRegistry.serverboundPlay().register(Slam.TYPE, Slam.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Slam.TYPE, (payload, ctx) -> onSlam(ctx.player(), payload.phase()));
		PayloadTypeRegistry.serverboundPlay().register(Dive.TYPE, Dive.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Dive.TYPE, (payload, ctx) -> onDive(ctx.player()));
	}

	/** Checked against the server's own record of the fall: far enough, fast enough, and only just landed. */
	private static void onDive(ServerPlayer player) {
		Fall fall = FALLS.get(player.getUUID());
		long now = player.level().getGameTime();
		if (fall == null || !player.isAlive() || player.isSpectator() || !player.onGround() || now - fall.landed() > DIVE_WINDOW
			|| fall.drop() < DIVE_MIN_DROP || fall.speed() < DIVE_MIN_SPEED || !com.jaymospowers.core.Powers.has(player, WebPower.INSTANCE)) {
			return;
		}
		// once per landing
		FALLS.put(player.getUUID(), new Fall(player.getY(), player.getY(), 0.0, Long.MIN_VALUE, 0.0, 0.0));
		diveSlam(player, fall.speed());
	}

	/** Watches every web slinger's falls, for {@link #onDive}. */
	private static void trackFall(ServerPlayer player) {
		Fall last = FALLS.get(player.getUUID());
		double y = player.getY();
		if (last == null) {
			FALLS.put(player.getUUID(), new Fall(y, y, 0.0, Long.MIN_VALUE, 0.0, 0.0));
			return;
		}
		double vy = y - last.lastY();
		if (player.onGround() || player.isInWater() || player.onClimbable()) {
			boolean touchdown = last.peak() > y + 0.01 && !player.isInWater();
			FALLS.put(player.getUUID(), touchdown
				? new Fall(y, y, 0.0, player.level().getGameTime(), last.peak() - y, -last.vy())
				: new Fall(y, y, 0.0, last.landed(), last.drop(), last.speed()));
			return;
		}
		// rising starts the fall afresh from the top
		double peak = vy > 0.0 ? y : Math.max(last.peak(), y);
		FALLS.put(player.getUUID(), new Fall(peak, y, vy, last.landed(), last.drop(), last.speed()));
	}

	private static void onSlam(ServerPlayer player, int phase) {
		ServerLevel level = player.level();
		long now = level.getGameTime();
		Long since = player.getAttached(STANCE);
		if (phase == 1) {
			if (since == null && WebSymbiote.bulked(player) && !player.onGround() && now >= READY.getOrDefault(player.getUUID(), 0L)) {
				player.setAttached(STANCE, now);
				PEAK.put(player.getUUID(), player.getY());
				level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_TENDRIL_CLICKS, SoundSource.PLAYERS, 1.0F, 0.5F);
			}
			return;
		}
		if (since == null) {
			return;
		}
		player.removeAttached(STANCE);
		Double peak = PEAK.remove(player.getUUID());
		if (phase == 2 && WebSymbiote.bulked(player) && now - since >= MIN_TICKS) {
			READY.put(player.getUUID(), now + COOLDOWN);
			slam(player, peak == null ? 0.0F : (float)Math.max(0.0, peak - player.getY()));
		}
	}

	/** Every tick: the stance ends if the big body goes. */
	static void tick(ServerPlayer player) {
		trackFall(player);
		if (player.getAttached(STANCE) == null) {
			return;
		}
		if (!WebSymbiote.bulked(player) || !player.isAlive()) {
			player.removeAttached(STANCE);
			PEAK.remove(player.getUUID());
			return;
		}
		PEAK.merge(player.getUUID(), player.getY(), Math::max);
	}

	/** Both fists brought down into the ground. */
	private static void slam(ServerPlayer player, float fallen) {
		ServerLevel level = player.level();
		Vec3 at = player.position();
		Vec3 ahead = player.getLookAngle().horizontal();
		ahead = ahead.lengthSqr() < 1.0E-4 ? Vec3.ZERO : ahead.normalize();
		Vec3 impact = at.add(ahead.scale(0.8));
		float damage = DAMAGE + Math.min(FALL_BONUS_MAX, Math.max(0.0F, fallen) * FALL_BONUS);
		for (LivingEntity living : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(RADIUS),
			e -> e != player && e.isAlive() && !e.isSpectator())) {
			Vec3 to = living.position().subtract(impact);
			double d = to.horizontalDistance();
			if (d > RADIUS + living.getBbWidth() * 0.5 || Math.abs(to.y) > 3.0) {
				continue;
			}
			float share = 1.0F - 0.5F * (float)Math.min(1.0, d / RADIUS);
			living.hurtServer(level, level.damageSources().playerAttack(player), damage * share);
			com.jaymospowers.core.Crushing.mark(living);
			Vec3 out = new Vec3(to.x, 0.0, to.z);
			out = out.lengthSqr() < 1.0E-4 ? ahead : out.normalize();
			Vec3 push = out.scale(KNOCK * share).add(0.0, LIFT * share, 0.0);
			living.setDeltaMovement(living.getDeltaMovement().add(push));
			living.needsSync = true;
			living.syncVelocity = true;
			if (living instanceof ServerPlayer other) {
				ServerPlayNetworking.send(other, new WebNet.Nudge(other.getDeltaMovement()));
			}
		}
		// the ground blown out under the fists, and a ring of debris thrown up around
		Debris.smash(level, impact.subtract(0.0, 0.5, 0.0), SMASH_SPEED + Math.min(0.6, fallen * 0.05), player);
		Debris.ring(level, BlockPos.containing(impact).below(), RING, 1.6F);
		level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, impact.x, impact.y + 0.3, impact.z, 1, 0.0, 0.0, 0.0, 0.0);
		level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, impact.x, impact.y + 0.3, impact.z, 20, RING * 0.5, 0.2, RING * 0.5, 0.02);
		level.sendParticles(ParticleTypes.SQUID_INK, impact.x, impact.y + 0.5, impact.z, 30, 0.8, 0.3, 0.8, 0.15);
		level.playSound(null, impact.x, impact.y, impact.z, SoundEvents.MACE_SMASH_GROUND_HEAVY, SoundSource.PLAYERS, 1.5F, 0.6F);
		level.playSound(null, impact.x, impact.y, impact.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 1.0F, 0.7F);
		level.playSound(null, impact.x, impact.y, impact.z, SoundEvents.WARDEN_ROAR, SoundSource.PLAYERS, 0.8F, 1.2F);
		com.jaymospowers.core.Shakes.send(player, SHAKE, impact);
		for (ServerPlayer near : level.players()) {
			if (near != player && near.distanceToSqr(impact) < NEAR_SHAKE_RANGE * NEAR_SHAKE_RANGE) {
				com.jaymospowers.core.Shakes.send(near, NEAR_SHAKE, impact);
			}
		}
	}

	/** The dive's superhero landing: one fist and a knee into the ground, cratering it as deep as the dive was fast. */
	private static void diveSlam(ServerPlayer player, double speed) {
		ServerLevel level = player.level();
		Vec3 at = player.position();
		float damage = DIVE_DAMAGE + DIVE_DAMAGE_PER_SPEED * (float)speed;
		for (LivingEntity living : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(DIVE_RADIUS),
			e -> e != player && e.isAlive() && !e.isSpectator())) {
			Vec3 to = living.position().subtract(at);
			double d = to.horizontalDistance();
			if (d > DIVE_RADIUS + living.getBbWidth() * 0.5 || Math.abs(to.y) > 2.5) {
				continue;
			}
			float share = 1.0F - 0.5F * (float)Math.min(1.0, d / DIVE_RADIUS);
			living.hurtServer(level, level.damageSources().playerAttack(player), damage * share);
			com.jaymospowers.core.Crushing.mark(living);
			Vec3 out = new Vec3(to.x, 0.0, to.z);
			out = out.lengthSqr() < 1.0E-4 ? new Vec3(1.0, 0.0, 0.0) : out.normalize();
			living.setDeltaMovement(living.getDeltaMovement().add(out.scale(DIVE_KNOCK * share).add(0.0, DIVE_LIFT * share, 0.0)));
			living.needsSync = true;
			living.syncVelocity = true;
			if (living instanceof ServerPlayer other) {
				ServerPlayNetworking.send(other, new WebNet.Nudge(other.getDeltaMovement()));
			}
		}
		// the ground breaks under the landing, a ring of it thrown up all round
		Debris.smash(level, at.subtract(0.0, 0.5, 0.0), Math.max(SMASH_SPEED, speed), player);
		Debris.ring(level, player.blockPosition().below(), (int)Math.min(5, 2 + speed * 1.2), (float)Math.min(2.2, 1.0 + speed * 0.5));
		level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y + 0.2, at.z, 1, 0.0, 0.0, 0.0, 0.0);
		level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, at.x, at.y + 0.2, at.z, 14, DIVE_RADIUS * 0.4, 0.1, DIVE_RADIUS * 0.4, 0.02);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.MACE_SMASH_GROUND_HEAVY, SoundSource.PLAYERS, 1.3F, 0.8F);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.7F, 0.9F);
		com.jaymospowers.core.Shakes.send(player, DIVE_SHAKE, at);
		for (ServerPlayer near : level.players()) {
			if (near != player && near.distanceToSqr(at) < NEAR_SHAKE_RANGE * NEAR_SHAKE_RANGE) {
				com.jaymospowers.core.Shakes.send(near, NEAR_SHAKE, at);
			}
		}
	}

	static void forget(ServerPlayer player) {
		FALLS.remove(player.getUUID());
		READY.remove(player.getUUID());
		PEAK.remove(player.getUUID());
	}
}
