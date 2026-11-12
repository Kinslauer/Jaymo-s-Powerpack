package com.jaymospowers.power.xenoform.forms;

import com.jaymospowers.core.BlockCluster;
import com.jaymospowers.core.Shakes;
import com.jaymospowers.core.fx.MagicFx;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * Towerback's uprooted slab: its grab (R) with no creature and no tree in reach closes on the ground instead, tearing a
 * great slab out, lifting it to its hand while the hold lasts and throwing it where it looks at the end of the hold,
 * as it does a tree ({@code GiantTrees}).
 */
public final class TowerSlab {
	// ---- tuning ----
	/** Blocks in the slab (a chunk holds nine at most), the hardest block it will tear up. */
	public static final int BLOCKS = 9;
	public static final float HARDEST = 50.0F;
	/** The slab is drawn this many times the size of a normal chunk per scale of Towerback, at most this many. */
	public static final float SCALE_PER_SIZE = 0.5F, SCALE_MAX = 4.0F;
	/** Ticks to come up to its hand. */
	public static final int LIFT_TICKS = 10;
	/** Thrown at this speed (blocks a tick), with this much lift. */
	public static final double THROW_SPEED = 1.7, THROW_LIFT = 0.15;
	public static final int COLOR = 0x8A8F96;

	private static final Map<UUID, Integer> HELD = new HashMap<>();

	private TowerSlab() {
	}

	/** The block the slab is torn from, under {@code hand}'s spot on the ground. */
	private static BlockPos spot(ServerLevel level, ServerPlayer player, Vec3 hand, double climb) {
		BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, BlockPos.containing(hand.x, 0.0, hand.z));
		double y = Math.abs(top.getY() - player.getY()) > climb ? player.getY() : top.getY();
		return BlockPos.containing(hand.x, y - 0.5, hand.z);
	}

	/** Whether there is ground at its hand's spot that could be torn up. */
	public static boolean groundNear(ServerLevel level, ServerPlayer player, Vec3 hand, double climb) {
		return player.mayBuild() && !BlockCluster.crater(level, spot(level, player, hand, climb), BLOCKS, HARDEST, player).isEmpty();
	}

	/** Its hand closes on the ground: true if a slab came up. */
	public static boolean take(ServerLevel level, ServerPlayer player, Vec3 hand, double climb) {
		if (!player.mayBuild()) {
			return false;
		}
		BlockPos at = spot(level, player, hand, climb);
		List<BlockPos> taken = BlockCluster.crater(level, at, BLOCKS, HARDEST, player);
		BlockCluster slab = BlockCluster.of(level, taken, Vec3.ZERO, player);
		if (slab == null) {
			return false;
		}
		slab.setAttached(BlockCluster.SCALE, Math.min(SCALE_MAX, Math.max(1.0F, player.getScale() * SCALE_PER_SIZE)));
		slab.script(true, true);
		HELD.put(player.getUUID(), slab.getId());
		level.playSound(null, at.getX(), at.getY(), at.getZ(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 1.6F, 0.5F);
		MagicFx.ring(level, Vec3.atCenterOf(at), 3.0F, COLOR);
		Shakes.heavy(player, Vec3.atCenterOf(at), 30.0, 0.8F);
		return true;
	}

	/** Whether it holds a slab now. */
	public static boolean holding(ServerPlayer player) {
		return HELD.containsKey(player.getUUID());
	}

	/**
	 * From its grab's tick once the hand has closed, {@code held} ticks into the hold (of {@code hold}): the slab is lifted to
	 * {@code hand}, and at the end of the hold thrown. True while it still holds the slab; false once it is thrown or gone.
	 */
	public static boolean tick(ServerLevel level, ServerPlayer player, int held, int hold, Vec3 hand) {
		Integer id = HELD.get(player.getUUID());
		if (id == null) {
			return false;
		}
		if (!(level.getEntity(id) instanceof BlockCluster slab) || slab.isRemoved()) {
			HELD.remove(player.getUUID());
			return false;
		}
		if (held < hold) {
			double lift = Mth.clamp(held / (double)LIFT_TICKS, 0.0, 1.0);
			lift = lift * lift * (3.0 - 2.0 * lift);
			Vec3 to = slab.position().lerp(hand.add(0.0, player.getBbHeight() * 0.1, 0.0), Math.max(lift, 0.35));
			slab.setPos(to.x, to.y, to.z);
			slab.setDeltaMovement(Vec3.ZERO);
			return true;
		}
		throwHeld(level, player, slab);
		return false;
	}

	private static void throwHeld(ServerLevel level, ServerPlayer player, BlockCluster slab) {
		HELD.remove(player.getUUID());
		Vec3 look = player.getViewVector(1.0F);
		slab.script(false, false);
		slab.setDeltaMovement(look.scale(THROW_SPEED).add(0.0, THROW_LIFT, 0.0));
		slab.syncVelocity = true;
		slab.launch(player);
		level.playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.RAVAGER_ROAR, SoundSource.PLAYERS, 2.0F, 0.6F);
	}

	/** Its grab is over: a slab still in its hand drops where it is. */
	public static void drop(ServerPlayer player) {
		Integer id = HELD.remove(player.getUUID());
		if (id != null && player.level() instanceof ServerLevel level && level.getEntity(id) instanceof BlockCluster slab) {
			slab.script(false, false);
			slab.setDeltaMovement(Vec3.ZERO);
		}
	}
}
