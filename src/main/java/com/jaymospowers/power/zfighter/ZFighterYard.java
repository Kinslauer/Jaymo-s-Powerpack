package com.jaymospowers.power.zfighter;

import com.jaymospowers.power.zfighter.pod.PodEntity;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

/**
 * (0.18.53, Andy's SevTech picks: "Landing Yard") Where pods come down near a kingdom's landing yard (Jaymo's Kingdoms, read by
 * reflection through {@link ZFighterKingdoms}): a patrol pod, and a pilot's trip home, sets down on a free pad; a raiders' band
 * lands just outside the yard, never on it. Without Kingdoms, or with no yard within {@link #RANGE}, every answer is null and the
 * pods land where they always did.
 */
final class ZFighterYard {
	// ---- tuning ----
	/** A yard this near (blocks) the town's seat or the spot is the one pods aim for. */
	static final double RANGE = 64.0;
	/** A pad is taken if a pod stands this near (blocks, flat). */
	static final double PAD_TAKEN = 2.5;
	/** Raiders come down this far (blocks) beyond the pad furthest from the post, at most; tries to find open ground. */
	static final int OUTSIDE_MIN = 5, OUTSIDE_MAX = 11, OUTSIDE_TRIES = 12;

	private ZFighterYard() {
	}

	/** A pad of the yard near {@code near} with no pod on it, or null (no yard, or every pad taken). */
	static @Nullable BlockPos freePad(ServerLevel level, BlockPos near) {
		List<BlockPos> pads = ZFighterKingdoms.yardPads(level, near, RANGE);
		if (pads.size() < 2) {
			return null;
		}
		List<BlockPos> order = new ArrayList<>(pads.subList(1, pads.size()));
		Collections.shuffle(order, new java.util.Random(level.getRandom().nextLong()));
		for (BlockPos pad : order) {
			// a pad built over or griefed shut is no landing place
			if (!level.getBlockState(pad).getCollisionShape(level, pad).isEmpty() || !level.getBlockState(pad.above()).getCollisionShape(level, pad.above()).isEmpty()) {
				continue;
			}
			AABB box = new AABB(pad).inflate(PAD_TAKEN, 3.0, PAD_TAKEN);
			if (level.getEntitiesOfClass(PodEntity.class, box, e -> true).isEmpty()) {
				return pad;
			}
		}
		return null;
	}

	/** Open ground just outside the yard near {@code near}, or null (no yard, or no ground found). */
	static @Nullable BlockPos outside(ServerLevel level, BlockPos near, RandomSource random) {
		List<BlockPos> pads = ZFighterKingdoms.yardPads(level, near, RANGE);
		if (pads.size() < 2) {
			return null;
		}
		BlockPos post = pads.get(0);
		double reach = 0.0;
		for (BlockPos pad : pads.subList(1, pads.size())) {
			reach = Math.max(reach, Math.sqrt(post.distSqr(new BlockPos(pad.getX(), post.getY(), pad.getZ()))));
		}
		for (int tries = 0; tries < OUTSIDE_TRIES; tries++) {
			double angle = random.nextDouble() * Math.PI * 2.0;
			double distance = reach + OUTSIDE_MIN + random.nextDouble() * (OUTSIDE_MAX - OUTSIDE_MIN);
			int x = Mth.floor(post.getX() + Math.cos(angle) * distance), z = Mth.floor(post.getZ() + Math.sin(angle) * distance);
			level.getChunk(x >> 4, z >> 4);
			BlockPos ground = ZFighterSpace.surface(level, x, z);
			if (ground != null) {
				return ground;
			}
		}
		return null;
	}
}
