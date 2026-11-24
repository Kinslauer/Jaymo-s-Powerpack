package com.jaymoskingdoms;

import java.util.Map;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

/**
 * Getting down off a building (Andy, 2026-11-24: "they get stuck in water or on buildings"). The pathfinder plans drops of
 * three blocks or so and no more, so a humanoid that has ended up on a roof, a wall top or a high ledge (a jump, a stair
 * roof it walked up, a spawn) has no way down it will take, and walks the roof while everywhere it wants to go is below.
 *
 * <p>When one stands at the end of a path that falls short and where it was going is lower down ({@link GapJumps} finds
 * it, after looking for a jump), it is marked stranded for {@link #HOLD_TICKS}: its path may drop up to {@link #CLIMB_DOWN}
 * blocks (the mixin on {@code Mob.getMaxFallDistance}), it paths again, and while it's marked it lowers itself down any
 * drop no faster than {@link #LOWER_SPEED} and takes no fall damage, hanging off the edge and letting go rather than
 * leaping. Not again for {@link #AGAIN_TICKS} after, so one cut off by something else doesn't keep trying.
 */
public final class Stranded {
	/** The deepest drop its path may take while stranded (blocks). */
	public static final int CLIMB_DOWN = 10;
	/** How long it stays marked (ticks), and how long before it can be marked again. */
	static final int HOLD_TICKS = 200, AGAIN_TICKS = 600;
	/** Its fastest fall while marked (blocks a tick; a free fall reaches about 0.8). */
	static final double LOWER_SPEED = 0.3;

	/** Per mob: marked until (game time), when it was last marked, and 1 while it's lowering itself down a drop. */
	private static final Map<Mob, long[]> MARKED = new WeakHashMap<>();
	private static final int UNTIL = 0, SINCE = 1, LOWERING = 2;

	private Stranded() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Stranded::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> MARKED.clear());
	}

	/** Marks {@code mob} stranded from {@code now}; false if it was marked too lately to be again. */
	static boolean mark(Mob mob, long now) {
		long[] m = MARKED.get(mob);
		if (m != null && now - m[SINCE] < AGAIN_TICKS) {
			return false;
		}
		MARKED.put(mob, new long[] {now + HOLD_TICKS, now, 0L});
		return true;
	}

	/** Whether {@code mob} is marked just now. */
	public static boolean marked(Mob mob) {
		long[] m = MARKED.get(mob);
		return m != null && mob.level().getGameTime() < m[UNTIL];
	}

	/** {@code mob}'s deepest planned drop: {@code was}, or {@link #CLIMB_DOWN} while it's marked. */
	public static int maxFall(Mob mob, int was) {
		return marked(mob) ? Math.max(was, CLIMB_DOWN) : was;
	}

	/** Every tick: a marked mob lowers itself down whatever it steps off, and lands unhurt. */
	private static void tick(MinecraftServer server) {
		if (MARKED.isEmpty()) {
			return;
		}
		MARKED.entrySet().removeIf(e -> {
			Mob mob = e.getKey();
			long[] m = e.getValue();
			long now = mob.level().getGameTime();
			// (the entry stays a while after the mark lapses, so it isn't marked again too soon)
			if (!mob.isAlive() || now - m[SINCE] > AGAIN_TICKS && m[LOWERING] == 0L) {
				return true;
			}
			if (mob.onGround() || mob.isInWater() || mob.isPassenger()) {
				m[LOWERING] = 0L;
			} else if (now < m[UNTIL] || m[LOWERING] != 0L) {
				// (a drop begun while marked is lowered all the way down)
				m[LOWERING] = 1L;
				Vec3 v = mob.getDeltaMovement();
				if (v.y < -LOWER_SPEED) {
					mob.setDeltaMovement(v.x, -LOWER_SPEED, v.z);
				}
				mob.resetFallDistance();
			}
			return false;
		});
	}
}
