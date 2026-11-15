package com.jaymospowers.client.wounds;

import com.jaymospowers.client.PoseBlend;
import com.jaymospowers.core.Dismember;
import com.jaymospowers.core.wounds.Downed;
import com.jaymospowers.core.wounds.DownedLie;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/**
 * Andy (2026-11-15): "can we make downed players and mobs sit against a wall?" Someone downed beside a wall
 * ({@link DownedLie#wall}) sits with their back to it: turned to face out of it, slid back against it and down onto the
 * floor, leaning back on it, legs out ahead, arms fallen at their sides, head lolled forward and to one side, breathing
 * hard. A downed mob that doesn't crawl sits as it goes down ({@link DownedPose}); a downed player sits once they've lain
 * still a moment ({@link #STILL_TICKS}), and goes back to crawling as soon as they move ({@code CrawlPose}). A body that
 * dies sat there stays sat (Jaymo's Kitchen's corpse, {@code DownedLie#markBody}).
 */
public final class WallSit {
	/** How far it leans back against the wall (radians). */
	static final float LEAN = 0.22F;
	/** How far it sinks (share of its height): its hips down onto the floor. */
	static final float SINK = 0.31F;
	/** Its back sits this far from the wall (model pixels of a 32-pixel body, plus the lean), so neither it nor its head goes in. */
	static final float BACK = 3.0F, SHOULDERS = 12.0F;
	/** Legs out ahead along the floor, turned out this much (radians) and a little more or less each. */
	static final float LEG_SPLAY = 0.18F, LEG_SPREAD = 0.12F;
	/** Arms fallen at its sides: forward, out (radians). */
	static final float ARM_FORWARD = -0.2F, ARM_OUT = 0.22F;
	/** The head: dropped forward, tipped to one side, turned to it (radians). */
	static final float HEAD_DROP = 0.55F, HEAD_TIP = 0.3F, HEAD_TURN = 0.25F;
	/** Hard breathing: beats per tick and depth (radians, on the arms and head). */
	static final float BREATH_RATE = 0.18F, BREATH_DEPTH = 0.05F;
	/** A downed player sits after this many ticks lying still beside a wall, easing in over this many. */
	static final int STILL_TICKS = 30, EASE_TICKS = 12;
	/** Moved further than this in a tick (blocks), a downed player is crawling, not resting. */
	static final double STILL_MOVE = 0.003;

	/** Where it sits: the wall's face (out of it, level) and how far that face is from its middle now (blocks). */
	public record Sit(float nx, float nz, float distance) {
		/** The way it faces sat there (a Minecraft yaw, degrees): out of the wall. */
		float yaw() {
			return (float) Math.toDegrees(Math.atan2(-this.nx, this.nz));
		}
	}

	/** A downed player sitting: where, and how far it's eased in (0-1). */
	record PlayerSit(Sit sit, float weight, int id, boolean out, int broken) {
	}

	public static final RenderStateDataKey<PlayerSit> KEY = RenderStateDataKey.create(() -> "jaymospowers:wall_sit");

	/** Each downed player's last tick moving. */
	private static final Int2LongOpenHashMap LAST_MOVED = new Int2LongOpenHashMap();

	private WallSit() {
	}

	/** The wall {@code entity} sits against, or null. */
	static @Nullable Sit of(LivingEntity entity) {
		double[] wall = DownedLie.wall(entity);
		return wall == null ? null : new Sit((float) wall[0], (float) wall[1], (float) wall[2]);
	}

	/**
	 * After the body's been turned to face its way ({@code bodyRot}): turned to face out of the wall instead, slid back to
	 * it, sunk to the floor and leaned back on the wall, {@code t} of the way (0-1). {@code height}: its standing height
	 * in the pose stack's units.
	 */
	static void rotate(PoseStack poseStack, Sit sit, float bodyRot, float height, float t) {
		float px = height / 32.0F;
		float target = px * (BACK + SHOULDERS * Mth.sin(LEAN));
		// moved toward the wall (in the world), into this frame: its right is +x, its front is -z
		float move = (sit.distance() - target) * t;
		float wx = -sit.nx() * move, wz = -sit.nz() * move;
		float yaw = bodyRot * Mth.DEG_TO_RAD;
		float fx = -Mth.sin(yaw), fz = Mth.cos(yaw), rx = -fz, rz = fx;
		poseStack.translate(wx * rx + wz * rz, 0.0F, -(wx * fx + wz * fz));
		// turned to face out of the wall
		poseStack.rotate(Axis.YP.rotationDegrees(Mth.wrapDegrees(bodyRot - sit.yaw()) * t));
		// down onto the floor, then leaned back (+x turns the top back) about the hips
		float sink = height * SINK * t;
		float hips = height * 0.375F - sink;
		poseStack.translate(0.0F, -sink, 0.0F);
		poseStack.translate(0.0F, hips, 0.0F);
		poseStack.rotate(Axis.XP.rotation(LEAN * t));
		poseStack.translate(0.0F, -hips, 0.0F);
	}

	/** Its limbs sat there: legs out flat (the lean taken off), arms fallen, head lolled, breathing hard unless {@code out} cold. */
	static void limbs(PoseBlend.Frame f, float age, int id, boolean out, int broken) {
		float side = DownedLie.pick(id * 17) < 0.5F ? 1.0F : -1.0F;
		float a = DownedLie.pick(id * 5) - 0.5F, b = DownedLie.pick(id * 7) - 0.5F;
		float breath = out ? 0.0F : Mth.sin(age * BREATH_RATE) * BREATH_DEPTH;
		float flat = -(Mth.HALF_PI - LEAN);
		f.set(PoseBlend.RIGHT_LEG, flat, LEG_SPLAY + a * LEG_SPREAD, 0.04F);
		f.set(PoseBlend.LEFT_LEG, flat, -LEG_SPLAY - b * LEG_SPREAD, -0.04F);
		if ((broken & Dismember.RIGHT_ARM) != 0 && (broken & Dismember.LEFT_ARM) == 0) {
			// a hand held over the broken arm
			f.set(PoseBlend.LEFT_ARM, -0.95F + breath, -0.4F, 0.55F);
		} else {
			f.set(PoseBlend.LEFT_ARM, ARM_FORWARD + b * 0.2F + breath, 0.0F, -ARM_OUT);
		}
		if ((broken & Dismember.LEFT_ARM) != 0 && (broken & Dismember.RIGHT_ARM) == 0) {
			f.set(PoseBlend.RIGHT_ARM, -0.95F + breath, 0.4F, -0.55F);
		} else {
			f.set(PoseBlend.RIGHT_ARM, ARM_FORWARD + a * 0.2F + breath, 0.0F, ARM_OUT);
		}
		f.set(PoseBlend.HEAD, HEAD_DROP - breath, HEAD_TURN * side, HEAD_TIP * side);
	}

	// ---------------------------------------------------------------- downed players (CrawlPose)

	/** Read onto a downed player's state: sitting against a wall once they've lain still beside it a moment. */
	public static void extract(Player player, AvatarRenderState state, float partialTicks, boolean rising) {
		int id = player.getId();
		long now = player.level().getGameTime();
		double dx = player.getX() - player.xo, dz = player.getZ() - player.zo;
		if (!Downed.isDowned(player)) {
			LAST_MOVED.remove(id);
			state.setData(KEY, null);
			return;
		}
		if (!LAST_MOVED.containsKey(id) || dx * dx + dz * dz > STILL_MOVE * STILL_MOVE || rising) {
			LAST_MOVED.put(id, now);
		}
		float still = now + partialTicks - LAST_MOVED.get(id) - STILL_TICKS;
		Sit sit = still > 0.0F ? of(player) : null;
		state.setData(KEY, sit == null ? null : new PlayerSit(sit, smooth(Mth.clamp(still / EASE_TICKS, 0.0F, 1.0F)), id,
			Downed.passedOut(player), com.jaymospowers.core.wounds.Wounds.brokenLimbs(player)));
	}

	/**
	 * In place of the crawl's turn (after vanilla's swimming tilt): the tilt taken off and the player sat against the
	 * wall. False if they aren't sitting.
	 */
	public static boolean rotatePlayer(AvatarRenderState state, PoseStack poseStack) {
		PlayerSit sitting = state.getData(KEY);
		if (sitting == null) {
			return false;
		}
		float w = sitting.weight();
		// pushed up off the floor: vanilla's swimming tilt (and its shift) undone, as far as it's eased in
		if (state.isVisuallySwimming) {
			poseStack.translate(0.0F, w, -0.3F * w);
		}
		poseStack.rotateDegrees(Axis.XP, 90.0F * state.swimAmount * w);
		// (the pose stack is already at the player's own scale: a player stands 1.8)
		rotate(poseStack, sitting.sit(), state.bodyRot, 1.8F, w);
		return true;
	}

	/** In place of the crawl's limbs: sat against the wall. False if they aren't sitting. */
	public static boolean posePlayer(AvatarRenderState state, PoseBlend.Frame f) {
		PlayerSit sitting = state.getData(KEY);
		if (sitting == null) {
			return false;
		}
		limbs(f, state.ageInTicks, sitting.id(), sitting.out(), sitting.broken());
		f.scale(sitting.weight());
		return true;
	}

	/** Whether this player is drawn sitting against a wall (the crawl's bent elbows and knees are left off). */
	public static boolean sitting(AvatarRenderState state) {
		return state.getData(KEY) != null;
	}

	private static float smooth(float t) {
		return t * t * (3.0F - 2.0F * t);
	}
}
