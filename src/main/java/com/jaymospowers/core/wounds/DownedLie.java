package com.jaymospowers.core.wounds;

import com.jaymospowers.core.Carry;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Which way someone downed lies, worked out the same on both sides: the client draws it ({@code DownedPose}), and the
 * server hands it to the body they leave when they die there. Andy (2026-11-12): "downed enemies should react to the
 * executions, i just watched jung wild slam a few zombies and they popped up after, instead of being beaten into the
 * ground". A downed mob still stands as far as the game knows (only its drawing is laid down), so Jaymo's Kitchen began
 * its corpse upright and let it topple: the body sprang to its feet on the killing blow. Now whoever dies downed is
 * marked ({@link #markBody}) and the corpse starts lying where and how they lay.
 */
public final class DownedLie {
	/** Of crawling people, the share that shuffle along on their backs (the rest crawl face down); of the lying, the share curled on their side. */
	public static final float SHUFFLE_SHARE = 0.3F, CURL_SHARE = 0.5F;
	/** Jaymo's Kitchen's {@code Corpses.LYING_TAG}: then {@code u} or {@code d} (face up or down), a dot, and the way the head points (whole degrees). */
	static final String LYING_TAG = "jaymoskitchen.lying.";
	/** Jaymo's Kitchen's {@code Corpses.PROPPED_TAG}: then the wall's face (its 2D index, the way out of the wall). */
	static final String PROPPED_TAG = "jaymoskitchen.propped.";
	/**
	 * Andy (2026-11-15): "can we make downed players and mobs sit against a wall?" Someone downed (not crawling off) with a
	 * wall's face no further than this from their side (blocks, past half their width) sits with their back to it.
	 */
	public static final double WALL_REACH = 0.6;

	private DownedLie() {
	}

	/** A fixed value (0-1) for this body, the same every time it goes down. */
	public static float pick(int id) {
		int h = id * 0x9E3779B1;
		h ^= h >>> 16;
		return (h & 0xFFFF) / 65536.0F;
	}

	/** A person curled on their side: which side they rolled to (1 or -1). */
	public static float curlSide(int id) {
		return pick(id * 31) < 0.5F ? 1.0F : -1.0F;
	}

	/**
	 * Marks a downed mob about to die so its corpse starts as it lies: a crawler face down with its head ahead, one on its
	 * back with its head behind where it stood, one curled with its head out to the side it rolled to; anything not built
	 * like a person along the way it faced. One tripped by a bolas and finished there lies face down. Not players (their own crawl is a lying pose already), nor anyone carried.
	 */
	static void markBody(LivingEntity entity) {
		if (entity instanceof Player || Carry.isCarried(entity)) {
			return;
		}
		boolean tripped = !Downed.isDowned(entity) && Tripped.tripped(entity);
		if (!Downed.isDowned(entity) && !tripped) {
			return;
		}
		float yaw = entity.yBodyRot;
		boolean faceUp = true;
		if (tripped) {
			// a bolas round its shins: it went down on its face
			faceUp = false;
		} else if (BodyPlan.of(entity) == BodyPlan.HUMANOID) {
			int id = entity.getId();
			if (Malfunction.is(entity)) {
				// on its back, or sat slumped (it goes over backward)
				yaw += 180.0F;
			} else if (Downed.crawls(entity)) {
				if (pick(id) < SHUFFLE_SHARE) {
					yaw += 180.0F;
				} else {
					faceUp = false;
				}
			} else if (pick(id) < CURL_SHARE) {
				yaw -= curlSide(id) * 90.0F;
			} else {
				yaw += 180.0F;
			}
		}
		clearBody(entity);
		entity.addTag(LYING_TAG + (faceUp ? "u." : "d.") + Math.round(net.minecraft.util.Mth.wrapDegrees(yaw)));
		double[] wall = !tripped && !Downed.crawls(entity) ? wall(entity) : null;
		if (wall != null) {
			// sat against a wall: the body stays sat there (a Kitchen without walls to sit against lies it down as above)
			entity.addTag(PROPPED_TAG + net.minecraft.core.Direction.getApproximateNearest(wall[0], 0.0, wall[1]).get2DDataValue());
		}
	}

	/**
	 * The wall someone downed at {@code entity}'s spot sits against, if there's one to sit against: a solid face at their
	 * hips, no further than {@link #WALL_REACH} past their side, with ground under them; the nearest of the four. As
	 * {nx, nz, distance}: the face's way out of the wall, and how far it is from their middle (blocks). Null if none.
	 */
	public static double @org.jspecify.annotations.Nullable [] wall(LivingEntity entity) {
		if (BodyPlan.of(entity) != BodyPlan.HUMANOID || entity.isInWater() || entity.isPassenger()) {
			return null;
		}
		net.minecraft.world.level.Level level = entity.level();
		double x = entity.getX(), y = entity.getY() + 0.3, z = entity.getZ();
		net.minecraft.core.BlockPos under = net.minecraft.core.BlockPos.containing(x, entity.getY() - 0.1, z);
		if (level.getBlockState(under).getCollisionShape(level, under).isEmpty()) {
			return null;
		}
		double reach = entity.getBbWidth() * 0.5 + WALL_REACH;
		double[] best = null;
		for (net.minecraft.core.Direction toward : net.minecraft.core.Direction.Plane.HORIZONTAL) {
			int sx = toward.getStepX(), sz = toward.getStepZ();
			net.minecraft.core.BlockPos pos = net.minecraft.core.BlockPos.containing(x + sx * reach, y, z + sz * reach);
			// (only the block next to theirs: one they stand inside isn't a wall)
			if (pos.getX() == net.minecraft.util.Mth.floor(x) && pos.getZ() == net.minecraft.util.Mth.floor(z)) {
				continue;
			}
			net.minecraft.core.Direction face = toward.getOpposite();
			if (!level.getBlockState(pos).isFaceSturdy(level, pos, face)) {
				continue;
			}
			// the face's distance from their middle
			double distance = sx > 0 ? pos.getX() - x : sx < 0 ? x - (pos.getX() + 1.0) : sz > 0 ? pos.getZ() - z : z - (pos.getZ() + 1.0);
			if (distance >= 0.0 && (best == null || distance < best[2])) {
				best = new double[] {face.getStepX(), face.getStepZ(), distance};
			}
		}
		return best;
	}

	/** Takes the mark off again (the death was turned away, so it may yet die on its feet). */
	static void clearBody(LivingEntity entity) {
		for (String tag : java.util.List.copyOf(entity.entityTags())) {
			if (tag.startsWith(LYING_TAG) || tag.startsWith(PROPPED_TAG)) {
				entity.removeTag(tag);
			}
		}
	}
}
