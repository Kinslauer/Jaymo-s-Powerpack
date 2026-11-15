package com.jaymoskitchen.corpse;

import java.util.function.BiConsumer;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.Nullable;

/**
 * A free-lying body that hits the ground or a wall hard (thrown, blown, dropped from a height, flung by a power) is told
 * to whoever listens (Jaymo's Powers smears its blood where it struck). Judged in {@link CorpseRagdoll}: the speed the
 * body lost in one step, past what gravity explains.
 */
public final class CorpseImpacts {
	/** A body must lose at least this much speed in one step (blocks a tick) to count as an impact. */
	static final double IMPACT = 0.4;
	/** No body counts two impacts within this many ticks. */
	static final int COOLDOWN = 8;

	/** (Jaymo's Powers) A body struck here: {x, y, z, speed lost}. */
	private static @Nullable BiConsumer<Entity, double[]> onImpact;
	/**
	 * (Jaymo's Powers) A body flung into a wall, sliding down it to sit at its foot: {x, top, z, nx, nz, floor}, where it
	 * struck (its middle, and the top of it), the wall's face (pointing out of it), and the floor it slides to.
	 */
	private static @Nullable BiConsumer<Entity, double[]> onSlump;

	private CorpseImpacts() {
	}

	public static void setOnImpact(BiConsumer<Entity, double[]> hook) {
		onImpact = hook;
	}

	public static void setOnSlump(BiConsumer<Entity, double[]> hook) {
		onSlump = hook;
	}

	static void slump(Entity corpse, double x, double top, double z, double nx, double nz, double floor) {
		BiConsumer<Entity, double[]> hook = onSlump;
		if (hook != null) {
			try {
				hook.accept(corpse, new double[] {x, top, z, nx, nz, floor});
			} catch (RuntimeException e) {
				// a broken listener never stops the body
			}
		}
	}

	static void impact(Entity corpse, double x, double y, double z, double lost) {
		BiConsumer<Entity, double[]> hook = onImpact;
		if (hook != null) {
			try {
				hook.accept(corpse, new double[] {x, y, z, lost});
			} catch (RuntimeException e) {
				// a broken listener never stops the body
			}
		}
	}
}
