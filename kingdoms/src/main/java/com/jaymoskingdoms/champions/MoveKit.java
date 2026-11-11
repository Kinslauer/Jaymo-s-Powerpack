package com.jaymoskingdoms.champions;

import com.jaymoskingdoms.Fx;
import com.jaymoskingdoms.PatronChampion;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** (0.7.518) Shared helpers for the champions' moves: telegraphs in the patron's colour, shapes, safe spots and pushes. */
public final class MoveKit {
	private MoveKit() {
	}

	public static void dust(ServerLevel level, ParticleOptions particle, Vec3 at, int count, double spread, double speed) {
		level.sendParticles(particle, at.x, at.y, at.z, count, spread, spread, spread, speed);
	}

	/** A ring of dust on the ground, sent each tell tick. */
	public static void ring(ServerLevel level, Vec3 center, double radius, int colour) {
		ParticleOptions dust = Fx.wisp(colour & 0xFFFFFF, 1.2F);
		int n = Math.max(12, (int) (radius * 10));
		for (int i = 0; i < n; i++) {
			double a = i * Math.PI * 2.0 / n;
			level.sendParticles(dust, center.x + Math.cos(a) * radius, center.y + 0.1, center.z + Math.sin(a) * radius, 1, 0.0, 0.0, 0.0, 0.0);
		}
	}

	/** A column of dust standing up from {@code pos}. */
	public static void column(ServerLevel level, Vec3 pos, double height, int colour) {
		ParticleOptions dust = Fx.wisp(colour & 0xFFFFFF, 1.2F);
		for (double y = 0.0; y <= height; y += 0.5) {
			level.sendParticles(dust, pos.x, pos.y + y, pos.z, 1, 0.15, 0.0, 0.15, 0.0);
		}
	}

	/** A marked spot on the ground: a ring and a short column. */
	public static void markSpot(ServerLevel level, Vec3 center, double radius, int colour) {
		ring(level, center, radius, colour);
		column(level, center, 1.5, colour);
	}

	/** Whether {@code e} stands within {@code range} and {@code degrees} (full width) of where {@code c} faces. */
	public static boolean inCone(PatronChampion c, Entity e, double range, double degrees) {
		Vec3 to = e.position().add(0.0, e.getBbHeight() / 2.0, 0.0).subtract(c.position().add(0.0, c.getBbHeight() * 0.6, 0.0));
		if (to.length() > range || to.lengthSqr() < 1.0E-4) {
			return false;
		}
		Vec3 look = c.getLookAngle();
		return look.dot(to.normalize()) >= Math.cos(Math.toRadians(degrees / 2.0));
	}

	/** Whether {@code e} is within {@code width} of the segment {@code from}..{@code to}. */
	public static boolean nearLine(Vec3 from, Vec3 to, Entity e, double width) {
		Vec3 seg = to.subtract(from);
		double len2 = seg.lengthSqr();
		Vec3 p = e.position().add(0.0, e.getBbHeight() / 2.0, 0.0);
		double t = len2 < 1.0E-6 ? 0.0 : Math.clamp(p.subtract(from).dot(seg) / len2, 0.0, 1.0);
		return from.add(seg.scale(t)).distanceTo(p) <= width;
	}

	/** Pushes {@code e} away from {@code from} and up. */
	public static void push(Entity e, Vec3 from, double strength, double lift) {
		Vec3 away = e.position().subtract(from).multiply(1.0, 0.0, 1.0);
		away = away.lengthSqr() < 1.0E-4 ? new Vec3(1.0, 0.0, 0.0) : away.normalize();
		e.push(away.x * strength, lift, away.z * strength);
		e.needsSync = true;
		e.syncVelocity = true;
	}

	/** Pulls {@code e} toward {@code to}. */
	public static void pull(Entity e, Vec3 to, double strength) {
		Vec3 d = to.subtract(e.position()).multiply(1.0, 0.0, 1.0);
		d = d.lengthSqr() < 1.0E-4 ? Vec3.ZERO : d.normalize();
		e.push(d.x * strength, 0.15, d.z * strength);
		e.needsSync = true;
		e.syncVelocity = true;
	}

	/** The share-of-blow damage, as a number. */
	public static float share(PatronChampion c, float share) {
		return (float) (c.blow() * share);
	}

	/** A standable spot {@code min}..{@code max} blocks from {@code around} (two blocks of headroom), or null. */
	public static @Nullable Vec3 blinkSpot(ServerLevel level, Entity around, double min, double max) {
		for (int i = 0; i < 16; i++) {
			double a = level.getRandom().nextDouble() * Math.PI * 2.0;
			double d = min + level.getRandom().nextDouble() * (max - min);
			Vec3 spot = standable(level, around.getX() + Math.cos(a) * d, around.getY(), around.getZ() + Math.sin(a) * d);
			if (spot != null) {
				return spot;
			}
		}
		return null;
	}

	/** Ground at (x, near y, z) with headroom, or null. Looks 4 up and 6 down. */
	public static @Nullable Vec3 standable(ServerLevel level, double x, double nearY, double z) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		int bx = (int) Math.floor(x), bz = (int) Math.floor(z);
		for (int y = (int) nearY + 4; y >= (int) nearY - 6; y--) {
			if (level.getBlockState(pos.set(bx, y, bz)).isSolid()
				&& level.getBlockState(pos.set(bx, y + 1, bz)).getCollisionShape(level, pos).isEmpty()
				&& level.getBlockState(pos.set(bx, y + 2, bz)).getCollisionShape(level, pos).isEmpty()
				&& level.getFluidState(pos.set(bx, y + 1, bz)).isEmpty()) {
				return new Vec3(bx + 0.5, y + 1.0, bz + 0.5);
			}
		}
		return null;
	}

	/** How many adds the fourth phase brings. */
	public static int adds(int group) {
		return 1 + group / 2;
	}

	public static void sound(ServerLevel level, Vec3 at, SoundEvent sound, float volume, float pitch) {
		level.playSound(null, at.x, at.y, at.z, sound, SoundSource.HOSTILE, volume, pitch);
	}

	public static void sound(ServerLevel level, Vec3 at, Holder<SoundEvent> sound, float volume, float pitch) {
		level.playSound(null, at.x, at.y, at.z, sound.value(), SoundSource.HOSTILE, volume, pitch);
	}

	/** The foe the move is aimed at, or the champion's nearest foe. */
	public static @Nullable LivingEntity aim(PatronChampion c, @Nullable LivingEntity target) {
		if (target != null && target.isAlive()) {
			return target;
		}
		var foes = c.foes(com.jaymoskingdoms.PatronChampions.FOE_RANGE);
		return foes.isEmpty() ? null : foes.get(0);
	}

	/** Names an add ({@code entity.jaymoskingdoms.patron_champion.add.<key>}). */
	public static void name(Mob mob, String key) {
		mob.setCustomName(Component.translatable("entity.jaymoskingdoms.patron_champion.add." + key));
	}
}
