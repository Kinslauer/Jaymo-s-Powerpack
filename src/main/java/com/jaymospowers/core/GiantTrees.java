package com.jaymospowers.core;

import com.jaymospowers.power.web.KitchenCompat;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.20.105) Andy: "giants should also be able to grab and throw the trees with their grabs". A giant's grab (the Great
 * Ape's, Towerback's) with no one in reach closes on a tree instead: a fallen one, or a standing one torn out whole. It's
 * lifted to the giant's hand while the grab holds, and where the grab would slam its catch down, the tree is thrown along
 * the giant's aim instead, faster the bigger and stronger the giant ({@link #THROW_SPEED}), striking what it flies
 * through and landing as a fallen tree. Needs Jaymo's Kingdoms (its {@code FallingTree}); without it, nothing changes.
 */
public final class GiantTrees {
	/** A tree up to this many logs per block of the giant's height can be lifted. */
	public static final float LOGS_PER_HEIGHT = 4.0F;
	/** Thrown at this speed (blocks a tick) times the root of the giant's height in people, with a little lift. */
	public static final double THROW_SPEED = 0.4, THROW_LIFT = 0.15, THROW_FASTEST = 2.5;
	/** How long it takes to come up to the hand (ticks). */
	public static final int LIFT_TICKS = 10;
	/** End over end as it flies (degrees a tick). */
	public static final float TUMBLE = 6.0F;

	private record Hold(int tree, Vec3 from) {
	}

	private static final Map<UUID, Hold> HELD = new HashMap<>();
	private static boolean looked;
	private static @Nullable Method mNear, mTake, mHold, mHurl;

	private GiantTrees() {
	}

	/** The most logs {@code giant} can lift. */
	public static int mostLogs(LivingEntity giant) {
		return Math.max(4, Math.round(giant.getBbHeight() * LOGS_PER_HEIGHT));
	}

	/** Whether its hand at {@code hand} could close on a tree within {@code reach}. */
	public static boolean treeNear(ServerLevel level, LivingEntity giant, Vec3 hand, double reach) {
		if (!kingdoms() || !mayFell(giant, level)) {
			return false;
		}
		try {
			return (boolean)mNear.invoke(null, level, hand, reach, mostLogs(giant));
		} catch (ReflectiveOperationException | RuntimeException e) {
			return false;
		}
	}

	/** Its hand at {@code hand} closes on the nearest tree within {@code reach}: true if it took one. */
	public static boolean take(ServerLevel level, LivingEntity giant, Vec3 hand, double reach) {
		if (!kingdoms() || !mayFell(giant, level)) {
			return false;
		}
		try {
			Entity tree = (Entity)mTake.invoke(null, level, hand, reach, giant, mostLogs(giant));
			if (tree == null) {
				return false;
			}
			HELD.put(giant.getUUID(), new Hold(tree.getId(), tree.position()));
			return true;
		} catch (ReflectiveOperationException | RuntimeException e) {
			return false;
		}
	}

	/** Whether it holds a tree now. */
	public static boolean holding(LivingEntity giant) {
		return HELD.containsKey(giant.getUUID());
	}

	/**
	 * From its grab's tick once the hand has closed, {@code held} ticks into the hold (of {@code hold}): the tree is lifted
	 * to {@code hand}, and at the end of the hold thrown. True while it still holds the tree (the grab goes on as it is);
	 * false once it's thrown or gone (the grab straightens up).
	 */
	public static boolean tick(ServerLevel level, LivingEntity giant, int held, int hold, Vec3 hand) {
		Hold h = HELD.get(giant.getUUID());
		if (h == null) {
			return false;
		}
		Entity tree = level.getEntity(h.tree());
		if (tree == null || tree.isRemoved()) {
			HELD.remove(giant.getUUID());
			return false;
		}
		if (held < hold) {
			double lift = Mth.clamp(held / (double)LIFT_TICKS, 0.0, 1.0);
			lift = lift * lift * (3.0 - 2.0 * lift);
			invoke(mHold, tree, h.from().lerp(hand, lift), giant.getYRot());
			return true;
		}
		HELD.remove(giant.getUUID());
		double size = Math.sqrt(Math.max(1.0, giant.getBbHeight() / 1.8));
		int brawn = giant instanceof Player p ? Math.min(Carry.BRAWN_THROW_MAX, KitchenCompat.brawn(p)) : 0;
		double speed = Math.min(THROW_FASTEST, THROW_SPEED * size * (1.0 + Carry.BRAWN_THROW * brawn));
		Vec3 v = giant.getViewVector(1.0F).scale(speed).add(0.0, THROW_LIFT, 0.0);
		invoke(mHurl, tree, v, TUMBLE, giant);
		level.playSound(null, giant.getX(), giant.getEyeY(), giant.getZ(), SoundEvents.RAVAGER_ROAR, giant instanceof Player ? SoundSource.PLAYERS
			: SoundSource.HOSTILE, 2.0F, 0.6F);
		return false;
	}

	/** Its grab is over: a tree still in its hand drops where it is. */
	public static void drop(LivingEntity giant) {
		Hold h = HELD.remove(giant.getUUID());
		if (h != null && giant.level() instanceof ServerLevel level && level.getEntity(h.tree()) instanceof Entity tree) {
			invoke(mHurl, tree, Vec3.ZERO, TUMBLE / 2.0F, giant);
		}
	}

	/** As its trampling: a player giant only where they may build, a mob giant only with mob griefing on. */
	private static boolean mayFell(LivingEntity giant, ServerLevel level) {
		return GiantTrample.mayFell(giant, level);
	}

	private static void invoke(@Nullable Method method, Object... args) {
		if (method == null) {
			return;
		}
		try {
			method.invoke(null, args);
		} catch (ReflectiveOperationException | RuntimeException ignored) {
			// (Kingdoms changed underneath us: the tree is simply left be)
		}
	}

	/** Kingdoms' {@code FallingTree} hands, if Kingdoms is loaded. */
	private static boolean kingdoms() {
		if (!looked) {
			looked = true;
			try {
				Class<?> c = Class.forName("com.jaymoskingdoms.FallingTree");
				mNear = c.getMethod("treeNear", ServerLevel.class, Vec3.class, double.class, int.class);
				mTake = c.getMethod("take", ServerLevel.class, Vec3.class, double.class, Entity.class, int.class);
				mHold = c.getMethod("hold", Entity.class, Vec3.class, float.class);
				mHurl = c.getMethod("hurl", Entity.class, Vec3.class, float.class, Entity.class);
			} catch (ReflectiveOperationException | LinkageError e) {
				mNear = mTake = mHold = mHurl = null;
			}
		}
		return mTake != null;
	}
}
