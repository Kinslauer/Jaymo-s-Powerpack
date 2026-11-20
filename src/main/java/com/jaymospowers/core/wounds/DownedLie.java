package com.jaymospowers.core.wounds;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.Carry;
import java.util.Map;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.Direction;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

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
	/**
	 * Sitting there needs this much room over where the back and head go (a share of their standing height, from the floor),
	 * so no one sits up into a low ceiling; and that's this deep out from the wall (blocks).
	 */
	static final double SIT_ROOM = 0.7, SIT_DEPTH = 0.45;
	/**
	 * Andy (2026-11-15): "other players should be able to place them on the wall". Set down looking at a wall no further
	 * than this (blocks, from the eyes), someone downed is sat against it: their side this far off its face, on a floor no
	 * further than this below the carrier's feet.
	 */
	static final double PROP_REACH = 3.0, PROP_GAP = 0.02, PROP_DROP = 1.5;
	/** Moved further than this from where they were set (blocks), they're off the wall. */
	static final double PROP_SLIP = 0.3;
	/** Propped this long (ticks), they are sat there: past the get-up from the floor ({@code WallSit.IN_TICKS}, 30) and the slide down (16). */
	static final long SAT_TICKS = 34;
	/**
	 * Andy (2026-11-19): "crawling into a block should also activate the sit". A downed player crawling into a wall (their
	 * keys pushing them at its face, at least this much of the push straight at it) with their side no further than this
	 * off it (blocks), this many ticks running, sits up against it as the Sit key does.
	 */
	static final double BUMP_FACING = 0.7, BUMP_GAP = 0.08;
	static final int BUMP_TICKS = 6;

	/**
	 * On someone downed whom another set against a wall (carried there and set down looking at it, or dragged there and
	 * let go): when (game time), shifted up a bit, the low bit set if they were let go on the floor beside it (not set
	 * down standing against it). Synced, so everyone draws them sat there, out cold or not; gone once they move off the
	 * spot, are picked up again or aren't down any more.
	 */
	public static final AttachmentType<Long> PROPPED = AttachmentRegistry.create(JaymosPowers.id("downed_propped"),
		builder -> builder.syncWith(ByteBufCodecs.VAR_LONG.cast(), AttachmentSyncPredicate.all()));
	/** Each propped body and where it was set (server side). */
	private static final Map<LivingEntity, Vec3> PROPPED_AT = new WeakHashMap<>();
	/**
	 * Each downed player crawling into a wall: the ticks running they've pushed at it; -1 once the Sit key laid them back
	 * down off it, until they stop pushing (so holding the key into the wall doesn't sit them straight back up).
	 */
	private static final Map<ServerPlayer, Integer> BUMPING = new WeakHashMap<>();

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
	 * like a person along the way it faced. One tripped by a bolas and finished there lies face down. Not anyone carried. A
	 * player as {@link #markPlayer}.
	 */
	static void markBody(LivingEntity entity) {
		if (Carry.isCarried(entity)) {
			return;
		}
		if (entity instanceof Player player) {
			markPlayer(player);
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
	 * Andy (2026-11-18): "the death ragdoll when enemies and players are prone or crawling should keep them in that
	 * position". A player dying flat (downed, crawling, crippled or squeezed under a low ceiling: the swimming pose out of
	 * water) is marked so their body starts lying as they lay: on their back if they'd rolled over ({@link Crawl#supine}),
	 * else face down, the head the way they faced.
	 */
	static void markPlayer(Player player) {
		boolean flat = Downed.isDowned(player) || player.getPose() == Pose.SWIMMING && !player.isInWater();
		if (!flat || player.isPassenger()) {
			return;
		}
		clearBody(player);
		boolean faceUp = com.jaymospowers.core.Crawl.supine(player);
		player.addTag(LYING_TAG + (faceUp ? "u." : "d.") + Math.round(net.minecraft.util.Mth.wrapDegrees(player.yBodyRot)));
	}

	/**
	 * The wall someone downed at {@code entity}'s spot sits against, if there's one to sit against: a solid face at their
	 * hips, no further than {@link #WALL_REACH} past their side, with ground under them and room to sit up; the nearest of
	 * the four. As {nx, nz, distance}: the face's way out of the wall, and how far it is from their middle (blocks). Null if
	 * none, or while they're carried.
	 */
	public static double @Nullable [] wall(LivingEntity entity) {
		if (entity.isPassenger() || Carry.isCarried(entity)) {
			return null;
		}
		return wallAt(entity, entity.getX(), entity.getY(), entity.getZ());
	}

	/** As {@link #wall}, with {@code entity}'s feet at {@code x, feet, z}. */
	static double @Nullable [] wallAt(LivingEntity entity, double x, double feet, double z) {
		if (BodyPlan.of(entity) != BodyPlan.HUMANOID || entity.isInWater()) {
			return null;
		}
		net.minecraft.world.level.Level level = entity.level();
		double y = feet + 0.3;
		net.minecraft.core.BlockPos under = net.minecraft.core.BlockPos.containing(x, feet - 0.1, z);
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
		if (best == null) {
			return null;
		}
		// room to sit up: nothing over where the back and head go (not a crawlway under a low ceiling)
		double wx = x - best[0] * (best[2] - 0.02), wz = z - best[1] * (best[2] - 0.02);
		AABB seat = new AABB(wx, feet + 0.2, wz, wx + best[0] * SIT_DEPTH, feet + entity.getDimensions(Pose.STANDING).height() * SIT_ROOM,
			wz + best[1] * SIT_DEPTH).inflate(Math.abs(best[1]) * 0.2, 0.0, Math.abs(best[0]) * 0.2);
		return level.noCollision(entity, seat) ? best : null;
	}

	// ---- set against a wall by another (Carry)

	/**
	 * Where {@code carrier} sets {@code body} (someone downed, held in their arms) to sit against the wall they're looking
	 * at: on the floor at its foot, their side just off its face. Null if they aren't looking at a wall in reach, or there's
	 * no floor, no room or no wall to sit against there (they're set down as usual then).
	 */
	public static @Nullable Vec3 propSpot(ServerPlayer carrier, LivingEntity body) {
		if (!Downed.isDowned(body)) {
			return null;
		}
		Level level = carrier.level();
		Vec3 eye = carrier.getEyePosition();
		BlockHitResult hit = level.clip(new ClipContext(eye, eye.add(carrier.getViewVector(1.0F).scale(PROP_REACH)), ClipContext.Block.COLLIDER,
			ClipContext.Fluid.NONE, carrier));
		if (hit.getType() != HitResult.Type.BLOCK || !hit.getDirection().getAxis().isHorizontal()) {
			return null;
		}
		Direction face = hit.getDirection();
		double off = body.getBbWidth() * 0.5 + PROP_GAP;
		Vec3 at = hit.getLocation().add(face.getStepX() * off, 0.0, face.getStepZ() * off);
		// the floor there, from a little over where they looked (no higher than the carrier's eyes) down to a short drop below
		// their feet
		double top = Math.min(at.y + 0.5, eye.y), bottom = carrier.getY() - PROP_DROP;
		if (top <= bottom) {
			return null;
		}
		BlockHitResult floor = level.clip(new ClipContext(new Vec3(at.x, top, at.z), new Vec3(at.x, bottom, at.z), ClipContext.Block.COLLIDER,
			ClipContext.Fluid.NONE, carrier));
		if (floor.getType() != HitResult.Type.BLOCK || floor.getDirection() != Direction.UP) {
			return null;
		}
		at = new Vec3(at.x, floor.getLocation().y, at.z);
		if (!level.noCollision(body, body.getBoundingBox().move(at.subtract(body.position())))) {
			return null;
		}
		return wallAt(body, at.x, at.y, at.z) != null ? at : null;
	}

	/**
	 * {@code body}, downed and just let go of where it now is (set against a wall: {@link #propSpot}, or {@code fromFloor}
	 * dragged up beside one), is sat against the wall there: marked {@link #PROPPED}, a mob turned to face out of it. Nothing
	 * if there's no wall to sit against.
	 */
	public static void prop(LivingEntity body, boolean fromFloor) {
		double[] wall = Downed.isDowned(body) ? wall(body) : null;
		if (wall == null || !(body.level() instanceof ServerLevel level)) {
			return;
		}
		if (!(body instanceof Player)) {
			// (a player keeps their own look: the sit turns their body)
			float yaw = (float) Math.toDegrees(Math.atan2(-wall[0], wall[1]));
			body.setYRot(yaw);
			body.setYHeadRot(yaw);
			body.setYBodyRot(yaw);
			body.setDeltaMovement(Vec3.ZERO);
			if (body instanceof Mob mob) {
				mob.getNavigation().stop();
			}
		}
		body.setAttached(PROPPED, level.getGameTime() << 1 | (fromFloor ? 1L : 0L));
		PROPPED_AT.put(body, body.position());
	}

	/**
	 * The Sit key, pressed by a downed {@code player} (Andy: "the sit key should start the animation for players"): sat
	 * against the wall beside them, they lie back down off it; otherwise, awake beside one, they get up against it from
	 * the floor. Nothing if there's no wall.
	 */
	public static void sitKey(ServerPlayer player) {
		if (player.hasAttached(Downed.RISING)) {
			// (getting up the wall: the Sit key doesn't drop them off it)
			return;
		}
		if (player.hasAttached(PROPPED)) {
			player.removeAttached(PROPPED);
			PROPPED_AT.remove(player);
			BUMPING.put(player, -1);
		} else if (!Downed.passedOut(player)) {
			prop(player, true);
		}
	}

	/**
	 * Each tick of a downed {@code player} ({@code Downed}): crawled into a wall beside them ({@link #BUMP_TICKS} running,
	 * awake, not carried), they sit up against it as the Sit key would ({@link #sitKey}).
	 */
	static void crawlInto(ServerPlayer player) {
		if (player.hasAttached(PROPPED) || player.hasAttached(Downed.RISING) || Downed.passedOut(player)) {
			BUMPING.remove(player);
			return;
		}
		double[] wall = wall(player);
		net.minecraft.world.entity.player.Input input = player.getLastClientInput();
		// where their keys push them (as Entity#getInputVector turns them by the look): forward/back, left/right
		float ahead = (input.forward() ? 1.0F : 0.0F) - (input.backward() ? 1.0F : 0.0F);
		float side = (input.left() ? 1.0F : 0.0F) - (input.right() ? 1.0F : 0.0F);
		double sin = Math.sin(Math.toRadians(player.getYRot())), cos = Math.cos(Math.toRadians(player.getYRot()));
		double mx = side * cos - ahead * sin, mz = ahead * cos + side * sin;
		double push = Math.sqrt(mx * mx + mz * mz);
		boolean into = wall != null && push > 0.0 && wall[2] <= player.getBbWidth() * 0.5 + BUMP_GAP
			&& -(mx * wall[0] + mz * wall[1]) / push >= BUMP_FACING;
		if (!into) {
			BUMPING.remove(player);
			return;
		}
		int pushed = BUMPING.getOrDefault(player, 0);
		if (pushed < 0) {
			// (laid back down by the key while pushing: not again till they let go)
			return;
		}
		if (++pushed >= BUMP_TICKS) {
			BUMPING.remove(player);
			prop(player, true);
		} else {
			BUMPING.put(player, pushed);
		}
	}

	/**
	 * Whether {@code entity} is sat against a wall by now ({@link #PROPPED}, the getting up against it or the slide down it
	 * done, {@link #SAT_TICKS}): up it, even the too wounded can rise ({@code Downed}).
	 */
	public static boolean satAgainst(LivingEntity entity) {
		Long propped = entity.getAttached(PROPPED);
		return propped != null && entity.level().getGameTime() - (propped >> 1) >= SAT_TICKS;
	}

	/** Each server tick: a propped body that's moved off its spot, been picked up or isn't down any more is off the wall. */
	static void tickPropped() {
		if (PROPPED_AT.isEmpty()) {
			return;
		}
		PROPPED_AT.entrySet().removeIf(spot -> {
			LivingEntity body = spot.getKey();
			if (!body.isRemoved() && Downed.isDowned(body) && !Carry.isCarried(body) && body.position().distanceToSqr(spot.getValue()) <= PROP_SLIP * PROP_SLIP) {
				return false;
			}
			body.removeAttached(PROPPED);
			return true;
		});
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
