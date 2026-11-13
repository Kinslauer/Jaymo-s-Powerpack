package com.jaymospowers.power.wind;

import com.jaymospowers.core.fx.MagicFx;
import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.Fate;
import com.jaymospowers.core.Mastery;
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
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * (Jung's wind revamp) The body and the shape of four of Wind's moves:
 * <ul>
 * <li><b>Gust</b> (G held on the ground): the arms wheel round gathering wind while it's held, then a one-armed push
 * flowing into an uppercut throws a spiralling gust that runs forward and curls upward, carrying and lifting what it
 * catches. It's bigger with mastery and ascension; at full mastery it takes up the element round about (as the
 * Cyclone does: fire, water, snow, sand, storm, or an Imbue's).</li>
 * <li><b>Zephyr's Breath</b>: a spiral of wind lifts the caster two blocks and spins them, limbs trailing.</li>
 * <li><b>Gale Barrier</b>: both arms flung out at shoulder height; a sphere of wind forms round them and fades over three
 * seconds (the barrier itself lasts as long as ever).</li>
 * <li><b>Cyclone Lance</b>: a longer wind-up, then a bigger spiral shot with a line of wind down its middle that flies
 * forward (same damage).</li>
 * </ul>
 * Everyone sees the pose from the synced {@link #MOVE}; the gusts and lances are server-run shots drawn in particles.
 */
public final class WindMoves {
	/** The pose kinds in {@link Move}. */
	public static final int GATHER = 0, PUSH = 1, ARMS_OUT = 2, SPIN = 3, LANCE = 4, DIVE = 5;

	/** A pose under way: which, and the game time it began. Synced to everyone. */
	public record Move(int kind, long start) {
		public static final Codec<Move> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.INT.fieldOf("kind").forGetter(Move::kind),
			Codec.LONG.fieldOf("start").forGetter(Move::start)
		).apply(i, Move::new));
		public static final StreamCodec<ByteBuf, Move> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, Move::kind,
			ByteBufCodecs.VAR_LONG, Move::start,
			Move::new);
	}

	public static final AttachmentType<Move> MOVE = AttachmentRegistry.create(JaymosPowers.id("wind_move"),
		builder -> builder.syncWith(Move.STREAM_CODEC, AttachmentSyncPredicate.all()));

	/** How long the release poses hold (ticks): the push and uppercut, arms out after the barrier, the spin, the lance's throw. */
	public static final int PUSH_TICKS = 12, ARMS_OUT_HOLD = 14, SPIN_TICKS = 40, LANCE_WINDUP = 24, LANCE_THROW = 8;
	/** The Spiral Dive's pose holds until the dive ends it ({@code WindSpiralDive}); this is only the longest it could last (ticks). */
	public static final int DIVE_TICKS = 400;
	/** The push lands at this tick of the push pose and the uppercut sends the gust off at this one. */
	public static final int PUSH_HIT = 4, UPPERCUT_HIT = 8;

	/** Zephyr: how high the spiral lifts (blocks) and how hard it pulls toward that height. */
	static final double ZEPHYR_LIFT = 2.0;
	/** Gale Barrier's sphere: radius (blocks) and how long it takes to fade (ticks). */
	static final double SPHERE_RADIUS = 2.6;
	static final int SPHERE_TICKS = 60;
	/** The gust: speed (blocks/tick) at the weakest and full charge; how much bigger mastery and ascension make it. */
	static final double GUST_SPEED_MIN = 0.9, GUST_SPEED_MAX = 1.5;
	static final double GUST_MASTERY_SIZE = 0.6, GUST_ASCENDED_SIZE = 0.3;
	/** The gust starts curling up after this share of its range, and keeps flying this share past its range. */
	static final double GUST_CURL_AT = 0.35, GUST_TAIL = 0.5;
	/** The lance: speed (blocks/tick), its hit radius (bigger than the old 0.6) and its spiral's radius. */
	static final double LANCE_SPEED = 3.0, LANCE_HIT_RADIUS = 1.1, LANCE_SPIRAL = 0.75;

	private static final Map<UUID, List<Shot>> SHOTS = new HashMap<>();
	/** player → {zephyr start Y, ticks left}. */
	private static final Map<UUID, double[]> ZEPHYRS = new HashMap<>();
	/** player → sphere ticks left. */
	private static final Map<UUID, int[]> SPHERES = new HashMap<>();
	/** player → {lance wind-up ticks left, weather}. */
	private static final Map<UUID, float[]> LANCES = new HashMap<>();

	private WindMoves() {
	}

	static void init() {
		// (loads the attachment)
	}

	// ---- poses ------------------------------------------------------------------------------------------------

	static void pose(ServerPlayer player, int kind) {
		Move current = player.getAttached(MOVE);
		if (current == null || current.kind() != kind) {
			player.setAttached(MOVE, new Move(kind, player.level().getGameTime()));
		}
	}

	static void restart(ServerPlayer player, int kind) {
		player.setAttached(MOVE, new Move(kind, player.level().getGameTime()));
	}

	static void unpose(ServerPlayer player, int kind) {
		Move current = player.getAttached(MOVE);
		if (current != null && current.kind() == kind) {
			player.removeAttached(MOVE);
		}
	}

	/** How long a released pose holds before it clears itself (ticks); the gather is held for as long as the channel. */
	private static int length(int kind) {
		return switch (kind) {
			case PUSH -> PUSH_TICKS;
			case ARMS_OUT -> ARMS_OUT_HOLD;
			case SPIN -> SPIN_TICKS;
			case LANCE -> LANCE_WINDUP + LANCE_THROW;
			case DIVE -> DIVE_TICKS;
			default -> -1;
		};
	}

	/** The share of the way to full mastery (0..1). */
	static double masteryShare(Player player) {
		return Mth.clamp(Mastery.unlocked(player) / (double)Mastery.ALL, 0.0, 1.0);
	}

	// ---- the tick (from WindPower, for each Wind user) -------------------------------------------------------

	static void tick(ServerPlayer player, boolean channelling) {
		ServerLevel level = player.level();
		Move move = player.getAttached(MOVE);
		if (move != null) {
			int length = length(move.kind());
			long age = level.getGameTime() - move.start();
			// a held pose lasts while the channel does; a released one runs its length
			if (length < 0 ? !channelling : !channelling && (age >= length || age < 0)) {
				player.removeAttached(MOVE);
			}
		}
		UUID id = player.getUUID();
		double[] zephyr = ZEPHYRS.get(id);
		if (zephyr != null) {
			zephyrTick(player, level, zephyr);
			if (--zephyr[1] <= 0) {
				ZEPHYRS.remove(id);
			}
		}
		int[] sphere = SPHERES.get(id);
		if (sphere != null) {
			sphereTick(player, level, sphere[0]);
			if (--sphere[0] <= 0) {
				SPHERES.remove(id);
			}
		}
		float[] lance = LANCES.get(id);
		if (lance != null && --lance[0] <= 0) {
			LANCES.remove(id);
			fireLance(player, lance[1]);
		} else if (lance != null) {
			lanceWindupTick(player, level, (int)lance[0]);
		}
		List<Shot> shots = SHOTS.get(id);
		if (shots != null) {
			for (Iterator<Shot> it = shots.iterator(); it.hasNext();) {
				if (!it.next().tick(player, level)) {
					it.remove();
				}
			}
			if (shots.isEmpty()) {
				SHOTS.remove(id);
			}
		}
	}

	/** Dropped on death, logout and losing the power. */
	static void clear(ServerPlayer player) {
		UUID id = player.getUUID();
		SHOTS.remove(id);
		ZEPHYRS.remove(id);
		SPHERES.remove(id);
		LANCES.remove(id);
		player.removeAttached(MOVE);
	}

	// ---- Gust -------------------------------------------------------------------------------------------------

	/** While G is held on the ground with the Gust ready: arms wheeling, wind drawn in from all round (and its element, at full mastery). */
	static void gatherTick(ServerPlayer player, ServerLevel level, int ticks, float f) {
		pose(player, GATHER);
		double reach = 3.5 - 1.5 * f;
		for (int i = 0; i < 2; i++) {
			double a = ticks * 0.5 + i * Math.PI;
			double x = player.getX() + Math.cos(a) * reach, z = player.getZ() + Math.sin(a) * reach;
			double y = player.getY() + 0.6 + 0.6 * Math.sin(ticks * 0.2 + i);
			// drawn in toward the hands, curling round
			Vec3 in = new Vec3(player.getX() - x, player.getY() + 1.2 - y, player.getZ() - z).scale(0.12)
				.add(-Math.sin(a) * 0.12, 0.0, Math.cos(a) * 0.12);
			level.sendParticles(ParticleTypes.CLOUD, x, y, z, 0, in.x, in.y, in.z, 1.0);
		}
		if (ticks % 10 == 0 && Mastery.unlocked(player) >= Mastery.ALL) {
			WindCycloneElement element = WindCycloneElement.sample(player, level, 3.0, List.of(), WindCycloneElement.NONE);
			ParticleOptions p = element.particle(level);
			if (p != null) {
				level.sendParticles(p, player.getX(), player.getY() + 1.0, player.getZ(), 8, 1.2, 0.6, 1.2, 0.02);
			}
		}
	}

	/** Released: the push and uppercut, and the gust that follows the fist. */
	static void gust(ServerPlayer player, ServerLevel level, float f, float maxDamage, double knockback, boolean full, double range) {
		restart(player, PUSH);
		double size = 1.0 + GUST_MASTERY_SIZE * masteryShare(player) + (Fate.isAscended(player) ? GUST_ASCENDED_SIZE : 0.0);
		WindCycloneElement element = Mastery.unlocked(player) >= Mastery.ALL
			? WindCycloneElement.sample(player, level, 3.0, List.of(), WindCycloneElement.NONE) : WindCycloneElement.NONE;
		Shot shot = new Shot(Shot.GUST, player.getEyePosition().add(0.0, -0.4, 0.0), player.getLookAngle(), Mth.lerp(f, GUST_SPEED_MIN, GUST_SPEED_MAX),
			range * size, (0.8 + 0.9 * f) * size);
		shot.f = f;
		shot.damage = maxDamage;
		shot.knockback = knockback;
		shot.full = full;
		shot.element = element;
		// the gust leaves with the uppercut
		shot.delay = UPPERCUT_HIT;
		SHOTS.computeIfAbsent(player.getUUID(), k -> new ArrayList<>()).add(shot);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WIND_CHARGE_THROW, SoundSource.PLAYERS, 1.0F, 0.8F);
		if (element != WindCycloneElement.NONE) {
			element.announce(player, level, WindCycloneElement.NONE, 2.0);
		}
	}

	// ---- Zephyr's Breath --------------------------------------------------------------------------------------

	static void zephyr(ServerPlayer player, ServerLevel level) {
		restart(player, SPIN);
		ZEPHYRS.put(player.getUUID(), new double[] {player.getY(), SPIN_TICKS});
		player.fallDistance = 0.0F;
	}

	private static void zephyrTick(ServerPlayer player, ServerLevel level, double[] z) {
		double target = z[0] + ZEPHYR_LIFT;
		Vec3 v = player.getDeltaMovement();
		// rise to two blocks up and hang there while the spin lasts
		double vy = Mth.clamp((target - player.getY()) * 0.35, -0.1, 0.45);
		player.setDeltaMovement(v.x * 0.6, vy, v.z * 0.6);
		player.syncVelocity = true;
		player.fallDistance = 0.0F;
		// the spiral under and round them
		double age = SPIN_TICKS - z[1];
		for (int i = 0; i < 3; i++) {
			double a = age * 0.7 + i * Math.PI * 2.0 / 3.0;
			double h = (age * 0.15 + i * 0.7) % 2.6;
			double r = 0.5 + h * 0.3;
			level.sendParticles(ParticleTypes.CLOUD, player.getX() + Math.cos(a) * r, player.getY() - 0.4 + h, player.getZ() + Math.sin(a) * r,
				0, -Math.sin(a) * 0.15, 0.05, Math.cos(a) * 0.15, 1.0);
		}
		if ((int)age % 8 == 0) {
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BREEZE_IDLE_AIR, SoundSource.PLAYERS, 0.7F, 1.4F);
		}
	}

	// ---- Gale Barrier -----------------------------------------------------------------------------------------

	/** Released: the arms stay out a moment and the sphere forms, fading over {@link #SPHERE_TICKS}. */
	static void barrier(ServerPlayer player) {
		restart(player, ARMS_OUT);
		SPHERES.put(player.getUUID(), new int[] {SPHERE_TICKS});
	}

	private static void sphereTick(ServerPlayer player, ServerLevel level, int left) {
		double fade = left / (double)SPHERE_TICKS;
		Vec3 c = player.position().add(0.0, player.getBbHeight() * 0.5, 0.0);
		double age = SPHERE_TICKS - left;
		// the first few ticks it swells out to full size
		double r = SPHERE_RADIUS * Math.min(1.0, 0.4 + age / 6.0);
		// bands of wind sweeping round the sphere at different tilts, fewer as it fades
		int bands = Math.max(1, (int)Math.round(6 * fade));
		for (int b = 0; b < bands; b++) {
			double lat = -1.1 + 2.2 * (b + 0.5) / 6.0;
			double ring = Math.cos(lat) * r, y = Math.sin(lat) * r;
			double spin = age * (0.35 + 0.05 * b) + b * 1.7;
			for (int k = 0; k < 4; k++) {
				double a = spin + k * 0.18;
				level.sendParticles(ParticleTypes.CLOUD, c.x + Math.cos(a) * ring, c.y + y, c.z + Math.sin(a) * ring,
					0, -Math.sin(a) * 0.12, 0.0, Math.cos(a) * 0.12, 1.0);
			}
		}
		if (age == 0) {
			level.sendParticles(ParticleTypes.GUST_EMITTER_SMALL, c.x, c.y, c.z, 1, 0.0, 0.0, 0.0, 0.0);
		}
	}

	// ---- Cyclone Lance ----------------------------------------------------------------------------------------

	/** C pressed: the wind-up (the caller has set the cooldown); the lance flies at the end of it. */
	static void lanceWindup(ServerPlayer player, float weather) {
		restart(player, LANCE);
		LANCES.put(player.getUUID(), new float[] {LANCE_WINDUP, weather});
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BREEZE_INHALE, SoundSource.PLAYERS, 1.2F, 0.7F);
	}

	private static void lanceWindupTick(ServerPlayer player, ServerLevel level, int left) {
		// a spiral tightening round the drawn-back arm
		float share = 1.0F - left / (float)LANCE_WINDUP;
		Vec3 look = player.getLookAngle();
		Vec3 side = look.cross(new Vec3(0.0, 1.0, 0.0));
		side = side.lengthSqr() < 1.0E-4 ? new Vec3(1.0, 0.0, 0.0) : side.normalize();
		Vec3 hand = player.getEyePosition().add(side.scale(0.4)).add(look.scale(-0.3)).add(0.0, -0.3, 0.0);
		for (int i = 0; i < 2; i++) {
			double a = left * 0.9 + i * Math.PI;
			double r = 1.4 - share;
			Vec3 up = side.cross(look).normalize();
			Vec3 at = hand.add(side.scale(Math.cos(a) * r)).add(up.scale(Math.sin(a) * r));
			Vec3 in = hand.subtract(at).scale(0.15);
			level.sendParticles(ParticleTypes.CLOUD, at.x, at.y, at.z, 0, in.x, in.y, in.z, 1.0);
		}
		if (left % 6 == 0) {
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WIND_CHARGE_THROW, SoundSource.PLAYERS, 0.5F, 0.6F + share);
		}
	}

	private static void fireLance(ServerPlayer player, float weather) {
		if (!player.isAlive() || player.isSpectator()) {
			return;
		}
		WindTempest.lance(player, weather);
	}

	/** The lance's spiral shot (from {@link WindTempest#lance}). */
	static void launchLance(ServerPlayer player, Vec3 from, Vec3 look, double range, float damage, DamageSource source, boolean stormbound, boolean stun) {
		Shot shot = new Shot(Shot.LANCE, from, look, LANCE_SPEED, range, LANCE_HIT_RADIUS);
		shot.damage = damage;
		shot.source = source;
		shot.stormbound = stormbound;
		shot.stun = stun;
		SHOTS.computeIfAbsent(player.getUUID(), k -> new ArrayList<>()).add(shot);
	}

	// ---- the shots ----------------------------------------------------------------------------------------------

	private static final class Shot {
		static final int GUST = 0, LANCE = 1;
		final int kind;
		Vec3 pos, dir;
		final Vec3 start;
		final double speed, range, radius;
		double travelled;
		int age, delay;
		final Set<Integer> hit = new HashSet<>();
		float f, damage;
		double knockback;
		boolean full, stormbound, stun;
		int bolts;
		WindCycloneElement element = WindCycloneElement.NONE;
		DamageSource source;

		Shot(int kind, Vec3 pos, Vec3 dir, double speed, double range, double radius) {
			this.kind = kind;
			this.pos = pos;
			this.start = pos;
			this.dir = dir.normalize();
			this.speed = speed;
			this.range = range;
			this.radius = radius;
		}

		/** One tick; false once it's spent. */
		boolean tick(ServerPlayer owner, ServerLevel level) {
			if (this.delay > 0) {
				this.delay--;
				if (this.delay == 0 && this.kind == GUST) {
					// starts from the fist, wherever they face now
					this.pos = owner.getEyePosition().add(0.0, -0.4, 0.0);
					this.dir = owner.getLookAngle();
					level.playSound(null, owner.getX(), owner.getY(), owner.getZ(), SoundEvents.BREEZE_WIND_CHARGE_BURST, SoundSource.PLAYERS, 1.0F + this.f, 0.9F - 0.3F * this.f);
					level.sendParticles(ParticleTypes.GUST_EMITTER_SMALL, this.pos.x, this.pos.y, this.pos.z, 1, 0.0, 0.0, 0.0, 0.0);
				}
				return true;
			}
			this.age++;
			double limit = this.kind == GUST ? this.range * (1.0 + GUST_TAIL) : this.range;
			if (this.kind == GUST && this.travelled > this.range * GUST_CURL_AT && this.dir.y < 0.85) {
				// curling upward
				this.dir = this.dir.add(0.0, 0.16, 0.0).normalize();
			}
			Vec3 next = this.pos.add(this.dir.scale(this.speed));
			HitResult block = level.clip(new ClipContext(this.pos, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, owner));
			boolean stopped = block.getType() != HitResult.Type.MISS;
			if (stopped) {
				next = block.getLocation();
			}
			double r = this.currentRadius();
			this.draw(level, this.pos, next, r);
			this.hitAlong(owner, level, this.pos, next, r);
			if (this.kind == GUST && this.element != WindCycloneElement.FIRE && this.age % 2 == 0) {
				WindDouse.douse(level, next, r, owner);
			}
			this.travelled += this.pos.distanceTo(next);
			this.pos = next;
			if (stopped || this.travelled >= limit) {
				this.end(owner, level);
				return false;
			}
			return true;
		}

		private double currentRadius() {
			if (this.kind == LANCE) {
				return this.radius;
			}
			// the gust widens as it goes
			return this.radius * (0.55 + 0.45 * Math.min(1.0, this.travelled / this.range));
		}

		private void draw(ServerLevel level, Vec3 from, Vec3 to, double r) {
			Vec3 side = this.dir.cross(new Vec3(0.0, 1.0, 0.0));
			side = side.lengthSqr() < 1.0E-4 ? new Vec3(1.0, 0.0, 0.0) : side.normalize();
			Vec3 up = side.cross(this.dir).normalize();
			double length = from.distanceTo(to);
			int steps = Math.max(2, (int)Math.ceil(length * 3.0));
			ParticleOptions accent = this.kind == LANCE ? (this.stormbound ? MagicFx.CRACKLE : ParticleTypes.CLOUD) : this.element.particle(level);
			double spiral = this.kind == LANCE ? LANCE_SPIRAL : r * 0.75;
			int strands = this.kind == LANCE ? 2 : 3;
			for (int i = 0; i < steps; i++) {
				double d = this.travelled + length * i / steps;
				Vec3 at = from.lerp(to, i / (double)steps);
				for (int s = 0; s < strands; s++) {
					double a = d * (this.kind == LANCE ? 1.4 : 1.1) + s * Math.PI * 2.0 / strands;
					Vec3 p = at.add(side.scale(Math.cos(a) * spiral)).add(up.scale(Math.sin(a) * spiral));
					// a little swirl along the spiral
					Vec3 v = side.scale(-Math.sin(a) * 0.08).add(up.scale(Math.cos(a) * 0.08)).add(this.dir.scale(0.05));
					ParticleOptions particle = s == 0 && accent != null ? accent : ParticleTypes.CLOUD;
					level.sendParticles(particle, true, false, p.x, p.y, p.z, 0, v.x, v.y, v.z, 1.0);
				}
				if (this.kind == LANCE) {
					// the line of wind down the middle
					level.sendParticles(ParticleTypes.CLOUD, true, false, at.x, at.y, at.z, 0, this.dir.x * 0.02, this.dir.y * 0.02, this.dir.z * 0.02, 1.0);
				}
			}
			if (this.age % 3 == 0) {
				level.sendParticles(this.kind == GUST && this.f > 0.5F ? ParticleTypes.GUST_EMITTER_SMALL : ParticleTypes.GUST, true, false, to.x, to.y, to.z, 1, 0.0, 0.0, 0.0, 0.0);
			}
		}

		private void hitAlong(ServerPlayer owner, ServerLevel level, Vec3 from, Vec3 to, double r) {
			AABB area = new AABB(from, to).inflate(r + 1.0);
			for (Entity e : level.getEntities(owner, area, x -> x.isAlive() && !x.isSpectator() && x != owner.getVehicle()
				&& (x instanceof LivingEntity || x instanceof Projectile))) {
				Vec3 centre = e.getBoundingBox().getCenter();
				if (distanceToSegment(centre, from, to) > r + e.getBbWidth() * 0.5) {
					continue;
				}
				if (e instanceof Projectile projectile) {
					projectile.setDeltaMovement(this.dir.scale(this.kind == LANCE ? 2.0 : 1.0 + this.f));
					projectile.needsSync = true;
					continue;
				}
				LivingEntity living = (LivingEntity)e;
				boolean first = this.hit.add(e.getId());
				if (this.kind == LANCE) {
					if (first) {
						this.lanceHit(owner, level, living);
					}
					continue;
				}
				if (first) {
					// the old gust's falloff, along the gust's run instead of from the caster
					double impact = Mth.clamp(1.0 - this.travelled / this.range, 0.3, 1.0);
					float damage = (float)(this.damage * (impact * impact + impact) / 2.0) + this.element.bonus(living);
					living.hurtServer(level, this.full ? owner.damageSources().explosion(owner, owner) : owner.damageSources().windCharge(owner, owner),
						Math.max(1.0F, damage));
					this.element.batter(owner, level, living);
					level.sendParticles(ParticleTypes.SMALL_GUST, centre.x, centre.y, centre.z, 1, 0.0, 0.0, 0.0, 0.0);
				}
				// carried along and lifted the whole time it's inside
				double resist = 1.0 - living.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);
				if (resist <= 0.0) {
					continue;
				}
				double push = (0.35 + 0.35 * this.knockback) * resist;
				Vec3 v = living.getDeltaMovement().scale(0.4).add(this.dir.scale(push));
				living.setDeltaMovement(v.x, Math.max(v.y, (0.25 + 0.25 * this.f + Math.max(0.0, this.dir.y) * 0.4) * resist), v.z);
				living.syncVelocity = true;
				living.fallDistance = 0.0F;
			}
		}

		private void lanceHit(ServerPlayer owner, ServerLevel level, LivingEntity living) {
			if (this.stormbound && this.bolts < WindTempest.LANCE_MAX_BOLTS) {
				this.bolts++;
				WindStorm.strike(owner, living.position());
			}
			living.hurtServer(level, this.source, this.damage);
			double resist = 1.0 - living.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);
			if (resist > 0.0) {
				Vec3 push = this.dir.scale(WindTempest.LANCE_KNOCKBACK * resist);
				living.push(push.x, Math.max(push.y, 0.0) + WindTempest.LANCE_LIFT * resist, push.z);
				living.syncVelocity = true;
			}
			level.sendParticles(ParticleTypes.SWEEP_ATTACK, living.getX(), living.getY() + living.getBbHeight() * 0.5, living.getZ(), 1, 0.0, 0.0, 0.0, 0.0);
			if (this.stun) {
				living.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.SLOWNESS, WindTempest.LANCE_MODE_STUN, 4), owner);
				living.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.WEAKNESS, WindTempest.LANCE_MODE_STUN, 1), owner);
				living.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.NAUSEA, WindTempest.LANCE_MODE_STUN, 0), owner);
			}
		}

		private void end(ServerPlayer owner, ServerLevel level) {
			level.sendParticles(ParticleTypes.GUST_EMITTER_SMALL, true, false, this.pos.x, this.pos.y, this.pos.z, 1, 0.0, 0.0, 0.0, 0.0);
			if (this.kind == LANCE) {
				// a Stormbound lance still calls its bolts down along the line if it met nobody
				while (this.stormbound && this.bolts < WindTempest.LANCE_MAX_BOLTS) {
					this.bolts++;
					WindStorm.strike(owner, this.start.lerp(this.pos, this.bolts / (double)WindTempest.LANCE_MAX_BOLTS));
				}
				level.playSound(null, this.pos.x, this.pos.y, this.pos.z, SoundEvents.WIND_CHARGE_BURST, SoundSource.PLAYERS, 1.2F, 0.7F);
				com.jaymospowers.core.Shakes.around(level, this.pos, 14.0, 1.2F, com.jaymospowers.core.Shakes.ticksFor(1.2F), owner);
			} else if (this.full) {
				level.sendParticles(ParticleTypes.GUST_EMITTER_LARGE, true, false, this.pos.x, this.pos.y, this.pos.z, 1, 0.0, 0.0, 0.0, 0.0);
				level.playSound(null, this.pos.x, this.pos.y, this.pos.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 2.0F, 0.9F);
			}
		}
	}

	private static double distanceToSegment(Vec3 p, Vec3 a, Vec3 b) {
		Vec3 ab = b.subtract(a);
		double len = ab.lengthSqr();
		double t = len < 1.0E-6 ? 0.0 : Mth.clamp(p.subtract(a).dot(ab) / len, 0.0, 1.0);
		return p.distanceTo(a.add(ab.scale(t)));
	}
}
