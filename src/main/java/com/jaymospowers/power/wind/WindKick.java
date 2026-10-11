package com.jaymospowers.power.wind;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.Fate;
import com.jaymospowers.core.Mastery;
import com.jaymospowers.core.Powers;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Tempest Kick (a tap of the power's special key, X; every ability mastered; holding X is the {@link WindCyclone}): a roundhouse kick that throws a white crescent of
 * wind out sideways-flat ahead of you. It flies {@link #RANGE} blocks for {@link #DAMAGE}; ascended (the Tempest),
 * {@link #RANGE_ASCENDED} blocks for {@link #DAMAGE_ASCENDED}. It cuts through everything in its path (each once) and
 * stops at walls.
 */
public final class WindKick {
	static final double RANGE = 5.0, RANGE_ASCENDED = 20.0;
	static final float DAMAGE = 6.0F, DAMAGE_ASCENDED = 12.0F;
	/** The crescent: how fast it flies (blocks a tick), how wide it reaches either side, and how tall. */
	static final double SPEED = 1.6, HALF_WIDTH = 1.8, HALF_HEIGHT = 0.7;
	static final double PUSH = 0.8, LIFT = 0.25;
	static final int COOLDOWN = 60, COOLDOWN_ASCENDED = 40;
	/**
	 * How long the kick is drawn (ticks): {@link #WINDUP_TICKS} of wind-up (the lean, the leg chambering), the snap
	 * through the arc on the hit tick ({@link #SNAP_TICKS}), then {@link #RECOVER_TICKS} of follow-through and recovery.
	 * In the cold the wind-up is held longer (see {@link Kicking#windup}), and the whole kick with it.
	 */
	public static final int KICK_TICKS = 16;
	/** The crescent leaves the foot this many ticks after the key, at the top of the snap. */
	public static final int WINDUP_TICKS = 6;
	/** The snap through the arc, and the recovery after it (ticks; the pose's beats). */
	public static final float SNAP_TICKS = 1.5F, RECOVER_TICKS = 8.0F;
	/** The swing trail drawn round the body on the hit tick: its radius, height (share of the body) and points. */
	static final double SWING_RADIUS = 1.1, SWING_HEIGHT = 0.8;
	static final int SWING_POINTS = 10;
	/** How far the crescent tips, its kicking-side tip low and the other high (blocks at each tip). */
	static final double CRESCENT_TILT = 0.35;
	private static final DustParticleOptions WHITE = new DustParticleOptions(0xFFFFFF, 1.4F);
	/**
	 * The cold's extra wind-up for the kick (ticks, 1 s; the other Wind abilities take {@link WindCold#EXTRA_WINDUP_TICKS}).
	 * While it lasts, white wind swirls round the kicker ({@code COLD_SWIRL_*}: points on the ring, its radius in blocks,
	 * how fast it turns in radians a tick) with snowflakes, and the wind blows every {@code COLD_SOUND_EVERY} ticks.
	 */
	static final int COLD_EXTRA_WINDUP_TICKS = 20;
	static final int COLD_SWIRL_POINTS = 10, COLD_SOUND_EVERY = 8;
	static final double COLD_SWIRL_RADIUS = 0.9, COLD_SWIRL_SPIN = 0.45;

	/**
	 * A kick under way: when it began (game time), which leg kicks ({@code left}: the left leg, everything mirrored),
	 * a seed that picks the style (a roundhouse, or now and then a spinning back kick; see the client's {@code WindPose})
	 * and jitters the angles, and how long its wind-up is (ticks:
	 * {@link #WINDUP_TICKS}, longer in the cold).
	 */
	public record Kicking(long start, boolean left, int seed, int windup) {
		public static final Codec<Kicking> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.LONG.fieldOf("start").forGetter(Kicking::start),
			Codec.BOOL.fieldOf("left").forGetter(Kicking::left),
			Codec.INT.fieldOf("seed").forGetter(Kicking::seed),
			Codec.INT.fieldOf("windup").forGetter(Kicking::windup)
		).apply(i, Kicking::new));
		public static final StreamCodec<ByteBuf, Kicking> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_LONG, Kicking::start,
			ByteBufCodecs.BOOL, Kicking::left,
			ByteBufCodecs.INT, Kicking::seed,
			ByteBufCodecs.VAR_INT, Kicking::windup,
			Kicking::new);

		/** How long the whole kick is drawn (ticks), the wind-up's extra included. */
		public int length() {
			return KICK_TICKS + Math.max(0, this.windup - WINDUP_TICKS);
		}
	}

	/** On a kicking player: the kick (see {@link Kicking}). Synced to everyone, who see the roundhouse. */
	public static final AttachmentType<Kicking> KICKING = AttachmentRegistry.create(JaymosPowers.id("wind_kick"),
		builder -> builder.syncWith(Kicking.STREAM_CODEC, AttachmentSyncPredicate.all()));

	/** Client → server: X tapped (released before {@link WindCyclone#HOLD_TICKS}). */
	public record Kick() implements CustomPacketPayload {
		public static final Type<Kick> TYPE = new Type<>(JaymosPowers.id("wind_kick"));
		public static final StreamCodec<ByteBuf, Kick> CODEC = StreamCodec.unit(new Kick());

		@Override
		public Type<Kick> type() {
			return TYPE;
		}
	}

	private static final class Crescent {
		final ResourceKey<Level> dimension;
		final UUID owner;
		Vec3 pos;
		final Vec3 dir, side;
		final double range;
		final float damage;
		/** +1 kicked with the right leg (it sweeps in from the right), -1 the left. */
		final double sweep;
		/** Ticks of wind-up left before it leaves the foot, and the whole wind-up. */
		int delay;
		final int windup;
		double travelled;
		final Set<Integer> hit = new HashSet<>();

		Crescent(ResourceKey<Level> dimension, UUID owner, Vec3 pos, Vec3 dir, Vec3 side, double range, float damage, double sweep, int delay) {
			this.dimension = dimension;
			this.owner = owner;
			this.pos = pos;
			this.dir = dir;
			this.side = side;
			this.range = range;
			this.damage = damage;
			this.sweep = sweep;
			this.delay = delay;
			this.windup = delay;
		}
	}

	private static final List<Crescent> CRESCENTS = new ArrayList<>();
	private static final Map<UUID, Long> READY_AT = new HashMap<>();

	private WindKick() {
	}

	static void init() {
		PayloadTypeRegistry.serverboundPlay().register(Kick.TYPE, Kick.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Kick.TYPE, (payload, ctx) -> kick(ctx.player()));
		ServerTickEvents.END_SERVER_TICK.register(WindKick::tick);
	}

	private static void kick(ServerPlayer player) {
		if (!Powers.has(player, WindPower.INSTANCE) || Mastery.unlocked(player) < Mastery.ALL || !player.isAlive() || player.isSpectator()
			|| WindCyclone.isActive(player)) {
			return;
		}
		ServerLevel level = player.level();
		long now = level.getGameTime();
		if (now < READY_AT.getOrDefault(player.getUUID(), 0L)) {
			return;
		}
		boolean ascended = Fate.isAscended(player);
		// the cold holds the wind-up (and the cooldown after) longer
		int windup = WindCold.isCold(player) ? WINDUP_TICKS + COLD_EXTRA_WINDUP_TICKS : WINDUP_TICKS;
		READY_AT.put(player.getUUID(), now + windup - WINDUP_TICKS + WindCold.cooldown(player, ascended ? COOLDOWN_ASCENDED : COOLDOWN));
		Kicking kicking = new Kicking(now, player.getRandom().nextBoolean(), player.getRandom().nextInt(), windup);
		player.setAttached(KICKING, kicking);
		Vec3 look = player.getLookAngle();
		Vec3 flat = new Vec3(look.x, 0.0, look.z);
		flat = flat.lengthSqr() < 1.0E-4 ? Vec3.directionFromRotation(0.0F, player.getYRot()) : flat.normalize();
		// (the player's right, in the world)
		Vec3 side = new Vec3(-flat.z, 0.0, flat.x);
		Vec3 start = player.position().add(0.0, player.getBbHeight() * 0.55, 0.0).add(flat.scale(0.8));
		CRESCENTS.add(new Crescent(level.dimension(), player.getUUID(), start, flat, side, ascended ? RANGE_ASCENDED : RANGE,
			(ascended ? DAMAGE_ASCENDED : DAMAGE) * WindModes.abilityScale(player), kicking.left() ? -1.0 : 1.0, windup));
		// the wind-up: a low draw of breath before the snap
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BREEZE_INHALE, SoundSource.PLAYERS, 0.6F, 1.6F);
	}

	/** The snap: the leg whips round from the kicking side to the front, leaving a white arc at head height. */
	private static void snap(ServerLevel level, Crescent c) {
		Vec3 centre = c.pos.subtract(c.dir.scale(0.8)).add(0.0, (SWING_HEIGHT - 0.55) * 1.8, 0.0);
		for (int i = 0; i <= SWING_POINTS; i++) {
			// from straight out on the kicking side (90 degrees) round to dead ahead, rising as it goes
			double a = Math.PI * 0.5 * (1.0 - i / (double)SWING_POINTS);
			Vec3 at = centre.add(c.side.scale(Math.sin(a) * SWING_RADIUS * c.sweep)).add(c.dir.scale(Math.cos(a) * SWING_RADIUS))
				.add(0.0, -0.25 * Math.sin(a), 0.0);
			level.sendParticles(WHITE, at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
		}
		level.playSound(null, centre.x, centre.y, centre.z, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.0F, 0.7F);
		level.playSound(null, centre.x, centre.y, centre.z, SoundEvents.BREEZE_SHOOT, SoundSource.PLAYERS, 1.0F, 1.2F);
	}

	/**
	 * The cold's held wind-up: white wind swirling round the kicker, thickening as it builds, with snowflakes and a
	 * blowing wind.
	 */
	private static void coldSwirl(ServerLevel level, ServerPlayer owner, Crescent c) {
		int elapsed = c.windup - c.delay;
		double build = elapsed / (double)c.windup;
		int points = 3 + (int)Math.round((COLD_SWIRL_POINTS - 3) * build);
		for (int i = 0; i < points; i++) {
			double a = elapsed * COLD_SWIRL_SPIN + i * Math.PI * 2.0 / points;
			// a spiral up the body, round and round
			double y = owner.getBbHeight() * (0.15 + 0.7 * ((i / (double)points + elapsed * 0.07) % 1.0));
			level.sendParticles(WHITE, owner.getX() + Math.cos(a) * COLD_SWIRL_RADIUS, owner.getY() + y,
				owner.getZ() + Math.sin(a) * COLD_SWIRL_RADIUS, 1, 0.0, 0.0, 0.0, 0.0);
		}
		level.sendParticles(ParticleTypes.SNOWFLAKE, owner.getX(), owner.getY() + owner.getBbHeight() * 0.5, owner.getZ(),
			1 + (int)(build * 3), COLD_SWIRL_RADIUS * 0.6, 0.6, COLD_SWIRL_RADIUS * 0.6, 0.03);
		if (elapsed % COLD_SOUND_EVERY == 0) {
			level.playSound(null, owner.getX(), owner.getY(), owner.getZ(), SoundEvents.BREEZE_IDLE_AIR, SoundSource.PLAYERS,
				0.7F + 0.3F * (float)build, 0.8F + 0.3F * (float)build);
			level.playSound(null, owner.getX(), owner.getY(), owner.getZ(), SoundEvents.BREEZE_WHIRL, SoundSource.PLAYERS, 0.5F, 1.1F);
		}
	}

	private static void tick(MinecraftServer server) {
		for (Iterator<Crescent> it = CRESCENTS.iterator(); it.hasNext(); ) {
			Crescent c = it.next();
			ServerLevel level = server.getLevel(c.dimension);
			ServerPlayer owner = server.getPlayerList().getPlayer(c.owner);
			if (level == null || c.travelled >= c.range) {
				it.remove();
				continue;
			}
			if (c.delay > 0) {
				// still winding up: it follows the kicker until it leaves the foot
				if (owner != null && owner.level() == level) {
					c.pos = owner.position().add(0.0, owner.getBbHeight() * 0.55, 0.0).add(c.dir.scale(0.8));
					if (c.windup > WINDUP_TICKS) {
						coldSwirl(level, owner, c);
					}
				}
				if (--c.delay == 0) {
					snap(level, c);
				}
				continue;
			}
			Vec3 next = c.pos.add(c.dir.scale(SPEED));
			if (!level.noCollision(new AABB(next, next).inflate(0.25))) {
				// a wall: it bursts against it
				level.sendParticles(ParticleTypes.GUST, c.pos.x, c.pos.y, c.pos.z, 1, 0.0, 0.0, 0.0, 0.0);
				it.remove();
				continue;
			}
			c.pos = next;
			c.travelled += SPEED;
			// a flat crescent, swept back at its tips
			for (int i = -6; i <= 6; i++) {
				double t = i / 6.0;
				// tipped: low on the side it was kicked from, high on the other
				Vec3 at = c.pos.add(c.side.scale(t * HALF_WIDTH)).subtract(c.dir.scale(t * t * 0.9)).add(0.0, -t * c.sweep * CRESCENT_TILT, 0.0);
				level.sendParticles(WHITE, at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
			}
			level.sendParticles(ParticleTypes.CLOUD, c.pos.x, c.pos.y, c.pos.z, 2, HALF_WIDTH * 0.4, 0.1, HALF_WIDTH * 0.4, 0.01);
			AABB box = new AABB(c.pos, c.pos).inflate(HALF_WIDTH, HALF_HEIGHT, HALF_WIDTH);
			for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, box,
				e -> e.isAlive() && !e.isSpectator() && e != owner && !c.hit.contains(e.getId()))) {
				c.hit.add(target.getId());
				target.hurtServer(level, owner != null ? level.damageSources().playerAttack(owner) : level.damageSources().magic(), c.damage);
				target.setDeltaMovement(target.getDeltaMovement().add(c.dir.scale(PUSH)).add(0.0, LIFT, 0.0));
				target.syncVelocity = true;
				level.sendParticles(ParticleTypes.SWEEP_ATTACK, target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(), 1, 0.0, 0.0, 0.0, 0.0);
			}
		}
		READY_AT.entrySet().removeIf(e -> server.getPlayerList().getPlayer(e.getKey()) == null);
	}
}
