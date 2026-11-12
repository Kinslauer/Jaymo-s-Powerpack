package com.jaymospowers.power.darktide;

import com.jaymospowers.core.fx.MagicFx;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

/**
 * How Darktide looks, in one place: ink, dark water and the pale light on wet steel, all through {@link MagicFx}.
 * Every cut is a stroke, the tide is a wave, what lies on the ground is a pool, loose ink is miasma and spray is pale
 * motes off a ring. No squid ink, rain splash or sweep.
 */
public final class DarktideLooks {
	/** Ink (cuts, murk), the tide's dark water (waves, pools), a heavier blue for the widest cuts, and the pale of foam and wet steel. */
	public static final int INK = 0x070A1C, WATER = 0x0A1838, DEEP = 0x10285A, PALE = 0xCFE8FF;
	/** A falling drop of the tide's water, and the grey of a shade coming apart. */
	public static final int DROP = 0x5FA8E8, ASH = 0x3A3A44;
	/** The still water of Stillwater's field: lighter, so it reads as a mirror and not as a hole. */
	public static final int MIRROR = 0x16304F;
	/** Stillwater's field: how far it spreads (blocks) and how long that takes (ticks). Both sides draw and judge by these. */
	public static final double FIELD_RADIUS = 10.0;
	public static final int FIELD_GROW_TICKS = 60;
	/** The water at the feet of someone at high tide and at spring tide (blocks). */
	public static final float HIGH_POOL = 0.9F, SPRING_POOL = 1.5F;
	/** How wide a blade's cut, a knife's cut and a heavy cut are painted (blocks), and how long a cut stays (ticks). */
	public static final float BLADE_STROKE = 0.55F, KNIFE_STROKE = 0.3F, HEAVY_STROKE = 0.9F;
	public static final int CUT_TICKS = 10;
	/** How far a pool reaches under one body, and how long a pool left behind lies (ticks). */
	public static final float BODY_POOL = 1.1F;
	public static final int POOL_TICKS = 50;

	private DarktideLooks() {
	}

	/** A puff of loose ink at {@code at}: {@code count} puffs over {@code spread}. */
	public static void ink(ServerLevel level, Vec3 at, int count, double spread) {
		level.sendParticles(MagicFx.miasma(INK, 1.8F), at.x, at.y, at.z, count, spread, spread, spread, 0.01);
	}

	/** Spray: a pale ring over the ground at {@code at} out to {@code radius}, and pale motes thrown up off it. */
	public static void spray(ServerLevel level, Vec3 at, float radius) {
		MagicFx.ring(level, at.add(0.0, 0.1, 0.0), radius, PALE);
		level.sendParticles(MagicFx.mote(PALE, 1.0F), at.x, at.y + 0.3, at.z, Math.round(6 + radius * 6), radius * 0.4, 0.2, radius * 0.4, 0.08);
	}

	/**
	 * One cut through {@code at}, seen from {@code from}: a stroke {@code length} long across whoever stands there, at
	 * an angle of its own each time.
	 */
	public static void cut(ServerLevel level, Vec3 from, Vec3 at, double length, float width, int color) {
		Vec3 to = at.subtract(from).multiply(1.0, 0.0, 1.0);
		Vec3 look = to.lengthSqr() < 1.0E-6 ? new Vec3(1.0, 0.0, 0.0) : to.normalize();
		Vec3 right = new Vec3(-look.z, 0.0, look.x);
		double angle = level.getRandom().nextDouble() * Math.PI;
		Vec3 along = right.scale(Math.cos(angle)).add(0.0, Math.sin(angle), 0.0).scale(length * 0.5);
		if (level.getRandom().nextBoolean()) {
			along = along.scale(-1.0);
		}
		// bowed a little toward the one who cut
		MagicFx.stroke(level, at.subtract(along), at.add(along), look.scale(-length * 0.12), width, color, CUT_TICKS);
	}

	/** A cut straight down through {@code at}, from {@code height} above it. */
	public static void stab(ServerLevel level, Vec3 at, double height, float width, int color) {
		double lean = (level.getRandom().nextDouble() - 0.5) * 0.6;
		MagicFx.stroke(level, at.add(lean, height, -lean), at.add(-lean * 0.3, -0.3, lean * 0.3), null, width, color, CUT_TICKS);
	}

	/** A pool opening under {@code at}. */
	public static void pool(ServerLevel level, Vec3 at, float radius, int ticks) {
		MagicFx.pool(level, at, radius, WATER, ticks);
	}
}
