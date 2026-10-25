package com.jaymospowers.power.arsenal;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.Fate;
import com.jaymospowers.power.arsenal.entity.RiftBlade;
import io.netty.buffer.ByteBuf;
import java.util.List;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The ascended ability, <b>Hundred Blades</b> (C): rifts tear open in the sky over the point you're looking at, and a
 * storm of weapons hangs from them, then falls, one after another. Each blade that lands on something deals a fixed
 * {@link #BLADE_DAMAGE}, through the usual hurt-immunity, so a target caught under it all takes about thirty.
 *
 * <p>The rifts are drawn by every client from the synced {@link #SKY} (see {@code ArsenalSkyRenderer}); the blades are
 * ordinary {@link RiftBlade}s.
 */
public final class HundredBlades {
	public static final int COOLDOWN = 400;
	/** (0.16.24) The film (ticks): low by where they fall, looking up at the portals; bars for everyone this near (blocks). */
	static final int FILM_TICKS = 50;
	static final double FILM_WITNESSES = 32.0;
	public static final int RANGE = 48;
	public static final int BLADES = 20;
	public static final float BLADE_DAMAGE = 3.0F;
	/** Blades land within this many blocks of the point. */
	public static final double RADIUS = 4.0;
	/** The rifts open this high above the point (less under a ceiling, but never below {@link #MIN_HEIGHT}). */
	public static final double HEIGHT = 11.0;
	public static final double MIN_HEIGHT = 3.5;
	/** How many rifts, how big, and how far the outer ones sit from the middle one. */
	public static final int PORTALS = 5;
	public static final double PORTAL_RADIUS = 1.15;
	public static final double PORTAL_SPREAD = 2.6;

	// timeline (ticks since the cast)
	public static final int OPEN_END = 8;
	/** The first blade falls at this tick, then one every {@link #DROP_INTERVAL} (20 blades over ~2 s). */
	public static final int FIRST_DROP = 12;
	public static final int DROP_INTERVAL = 2;
	public static final int CLOSE_START = FIRST_DROP + (BLADES - 1) * DROP_INTERVAL + 6;
	public static final int CLOSE_END = CLOSE_START + 8;

	/** Rifts open in the sky: when, where (the point on the ground), how high, and how the ring is turned. */
	public record Sky(long start, double x, double y, double z, float height, float angle) {
		public static final StreamCodec<ByteBuf, Sky> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_LONG, Sky::start,
			ByteBufCodecs.DOUBLE, Sky::x,
			ByteBufCodecs.DOUBLE, Sky::y,
			ByteBufCodecs.DOUBLE, Sky::z,
			ByteBufCodecs.FLOAT, Sky::height,
			ByteBufCodecs.FLOAT, Sky::angle,
			Sky::new);

		/** The centre of rift {@code i}: the first in the middle, the rest in a ring. */
		public Vec3 portal(int i) {
			double py = y + height;
			if (i == 0) {
				return new Vec3(x, py, z);
			}
			double a = angle + (i - 1) * Mth.TWO_PI / (PORTALS - 1);
			return new Vec3(x + Math.cos(a) * PORTAL_SPREAD, py - 0.4 * (i % 2), z + Math.sin(a) * PORTAL_SPREAD);
		}
	}

	public static final AttachmentType<Sky> SKY = AttachmentRegistry.create(JaymosPowers.id("arsenal_sky"),
		builder -> builder.syncWith(Sky.STREAM_CODEC, AttachmentSyncPredicate.all()));

	private HundredBlades() {
	}

	static void init() {
		// (the attachment registers itself when this class loads)
	}

	/** How open the sky rifts are (0-1) at {@code t} ticks. */
	public static float openness(float t) {
		if (t < OPEN_END) {
			float x = t / OPEN_END;
			return (float) Math.min(1.1, 1.0 - Math.pow(1.0 - x, 3.0) + Math.sin(x * Math.PI) * 0.1);
		}
		if (t < CLOSE_START) {
			return 1.0F;
		}
		return Math.max(0.0F, 1.0F - (t - CLOSE_START) / (float) (CLOSE_END - CLOSE_START));
	}

	static void cast(ServerPlayer player) {
		ServerLevel level = player.level();
		long now = level.getGameTime();
		ArsenalPower.Cooldowns cd = ArsenalPower.cooldowns(player);
		if (now < cd.ultimate()) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.arsenal.ultimate_recharging",
				String.format("%.1f", (cd.ultimate() - now) / 20.0)).withStyle(net.minecraft.ChatFormatting.GRAY));
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.DISPENSER_FAIL, SoundSource.PLAYERS, 0.3F, 1.6F);
			return;
		}

		// where: what you're looking at (a creature, or the ground), within RANGE
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getLookAngle();
		Vec3 end = eye.add(look.scale(RANGE));
		BlockHitResult blockHit = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
		boolean hitBlock = blockHit.getType() != HitResult.Type.MISS;
		if (hitBlock) {
			end = blockHit.getLocation();
		}
		AABB search = player.getBoundingBox().expandTowards(look.scale(RANGE)).inflate(1.5);
		EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(player, eye, end, search,
			e -> e instanceof LivingEntity && !e.isSpectator() && e.isAlive() && e != player, eye.distanceToSqr(end));
		Vec3 point;
		if (entityHit != null) {
			point = entityHit.getEntity().position();
		} else if (hitBlock) {
			point = end;
		} else {
			// nothing within range: drop from the end of the ray to the ground below it
			BlockHitResult down = level.clip(new ClipContext(end, end.add(0, -RANGE, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
			if (down.getType() == HitResult.Type.MISS) {
				player.sendOverlayMessage(Component.translatable("message.jaymospowers.arsenal.ultimate_nowhere").withStyle(net.minecraft.ChatFormatting.GRAY));
				return;
			}
			point = down.getLocation();
		}
		player.setAttached(ArsenalPower.COOLDOWNS, cd.withUltimate(now + COOLDOWN));

		// how high: HEIGHT, or just under the ceiling
		Vec3 base = point.add(0, 0.2, 0);
		BlockHitResult up = level.clip(new ClipContext(base, base.add(0, HEIGHT, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
		double height = up.getType() == HitResult.Type.MISS ? HEIGHT : Math.max(MIN_HEIGHT, up.getLocation().y - point.y - 1.0);
		Sky sky = new Sky(now, point.x, point.y, point.z, (float) height, player.getRandom().nextFloat() * Mth.TWO_PI);
		player.setAttached(SKY, sky);
		com.jaymospowers.core.UltimateFilm.play(player, com.jaymospowers.core.UltimateFilm.SKYWARD, FILM_TICKS, point, null, FILM_WITNESSES);

		boolean corrupted = Fate.isCorrupted(player);
		float damage = BLADE_DAMAGE * (corrupted ? ArsenalVoid.DAMAGE_MULTIPLIER : 1.0F);
		List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class,
			new AABB(point.x - RADIUS, point.y - 3, point.z - RADIUS, point.x + RADIUS, point.y + height, point.z + RADIUS),
			e -> e != player && e.isAlive() && !e.isSpectator() && horizontal(e.position(), point) <= RADIUS);
		for (int i = 0; i < BLADES; i++) {
			Vec3 portal = sky.portal(i % PORTALS);
			double ja = player.getRandom().nextDouble() * Mth.TWO_PI;
			double jr = player.getRandom().nextDouble() * PORTAL_RADIUS * 0.6;
			Vec3 pos = portal.add(Math.cos(ja) * jr, -0.35, Math.sin(ja) * jr);
			@Nullable Entity target = targets.isEmpty() ? null : targets.get(i % targets.size());
			Vec3 aim;
			if (target != null) {
				aim = target.getBoundingBox().getCenter();
			} else {
				double a = player.getRandom().nextDouble() * Mth.TWO_PI;
				double r = Math.sqrt(player.getRandom().nextDouble()) * RADIUS;
				aim = point.add(Math.cos(a) * r, 0.1, Math.sin(a) * r);
			}
			RiftBlade blade = RiftBlade.create(level, player,
				corrupted ? ArsenalVoid.voidTouched(ArsenalPool.randomBlade(player.getRandom())) : ArsenalPool.randomBlade(player.getRandom()), pos, aim, target,
				FIRST_DROP + i * DROP_INTERVAL, damage);
			blade.makeSkyBlade(corrupted);
			level.addFreshEntity(blade);
		}

		double sy = point.y + height;
		level.playSound(null, point.x, sy, point.z, SoundEvents.END_PORTAL_SPAWN, SoundSource.PLAYERS, 0.8F, 1.6F);
		level.playSound(null, point.x, sy, point.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.5F, 0.5F);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ILLUSIONER_CAST_SPELL, SoundSource.PLAYERS, 1.0F, 0.7F);
	}

	private static double horizontal(Vec3 a, Vec3 b) {
		double dx = a.x - b.x, dz = a.z - b.z;
		return Math.sqrt(dx * dx + dz * dz);
	}

	/** Every tick for the caster: rift motes and the rifts closing. */
	static void tick(ServerPlayer player) {
		Sky sky = player.getAttached(SKY);
		if (sky == null) {
			return;
		}
		ServerLevel level = player.level();
		long t = level.getGameTime() - sky.start();
		if (t < 0 || t >= CLOSE_END) {
			player.removeAttached(SKY);
			Vec3 c = sky.portal(0);
			level.playSound(null, c.x, c.y, c.z, SoundEvents.ENDER_EYE_DEATH, SoundSource.PLAYERS, 1.0F, 0.6F);
			return;
		}
		if (t % 3 == 0) {
			for (int i = 0; i < PORTALS; i++) {
				Vec3 p = sky.portal(i);
				level.sendParticles(Fate.isCorrupted(player) ? ParticleTypes.SQUID_INK : ParticleTypes.PORTAL,
					p.x, p.y - 0.2, p.z, 2, PORTAL_RADIUS * 0.5, 0.05, PORTAL_RADIUS * 0.5, 0.05);
				level.sendParticles(ParticleTypes.REVERSE_PORTAL, p.x, p.y - 0.1, p.z, 2, PORTAL_RADIUS * 0.5, 0.05, PORTAL_RADIUS * 0.5, 0.02);
			}
		}
	}

	/** The power went away mid-storm. */
	static void onLost(ServerPlayer player) {
		player.removeAttached(SKY);
	}
}
