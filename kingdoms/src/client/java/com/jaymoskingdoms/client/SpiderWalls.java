package com.jaymoskingdoms.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.Map;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.monster.spider.Spider;
import org.jspecify.annotations.Nullable;

/**
 * A spider climbing a wall is drawn on the wall (Subtly Wild's idea and numbers, from its CC0 first release): its body
 * pitches up flat against the wall it climbs and turns to face it, head up going up, head down coming down, and turned
 * along it when it runs sideways; it eases on and off the wall. The game only ever drew it standing, as if it floated up.
 */
public final class SpiderWalls {
	/** How fast it eases onto and off the wall, and turns (share a tick). */
	static final float EASE = 0.2F;
	/** Moving up or down this little a tick (blocks) on the ground is still standing. */
	static final double STILL = 0.01;
	/** How it sits on the wall: raised this much and moved toward the wall by this share of its height less this much (blocks, at full climb). */
	static final float LIFT = 0.2F, TOWARD = 1.06F, TOWARD_LESS = 0.2F;

	/** On a climbing spider's render state: how far onto the wall it is (0-1), and which way along the wall it heads (degrees). */
	public static final RenderStateDataKey<float[]> CLIMB = RenderStateDataKey.create(() -> "jaymoskingdoms:spider_climb");

	private static final class Look {
		float clock = Float.NaN, onWall, wallYaw, along;
	}

	private static final Map<Spider, Look> LOOKS = new WeakHashMap<>();

	private SpiderWalls() {
	}

	/** At the end of the spider's render state extraction. */
	public static void extract(Spider spider, LivingEntityRenderState state, float partialTicks) {
		Look l = LOOKS.computeIfAbsent(spider, s -> new Look());
		float clock = spider.tickCount + partialTicks;
		float dt = Float.isNaN(l.clock) ? 1.0F : Mth.clamp(clock - l.clock, 0.0F, 5.0F);
		l.clock = clock;
		Direction wall = nearestWall(spider);
		double dy = spider.getY() - spider.yo;
		boolean climbing = wall != null && spider.isClimbing() && state.deathTime <= 0.0F && (!spider.onGround() || Math.abs(dy) > STILL);
		float step = Math.min(1.0F, EASE * dt);
		l.onWall = Mth.clamp(l.onWall + (climbing ? step : -step), 0.0F, 1.0F);
		if (l.onWall <= 0.0F) {
			state.setData(CLIMB, null);
			return;
		}
		if (wall != null) {
			l.wallYaw = Mth.rotLerp(step, l.wallYaw, wall.toYRot());
			double dx = spider.getX() - spider.xo, dz = spider.getZ() - spider.zo;
			if (dx * dx + dy * dy + dz * dz > 1.0E-5) {
				double side = switch (wall) {
					case NORTH -> -dx;
					case SOUTH -> dx;
					case EAST -> -dz;
					default -> dz;
				};
				l.along = Mth.rotLerp(step, l.along, (float) Math.toDegrees(Mth.atan2(side, dy)));
			}
		}
		state.bodyRot = Mth.rotLerp(l.onWall, state.bodyRot, l.wallYaw);
		state.setData(CLIMB, new float[] {l.onWall, l.along});
	}

	/** At the end of the renderer's body rotations: pitched up onto the wall. */
	public static void rotate(LivingEntityRenderState state, PoseStack poseStack) {
		float[] climb = state.getData(CLIMB);
		if (climb == null) {
			return;
		}
		float p = climb[0];
		poseStack.translate(0.0F, LIFT * p, (-TOWARD * state.boundingBoxHeight + TOWARD_LESS) * p);
		poseStack.rotateDegrees(Axis.XP, 90.0F * p);
		poseStack.rotateDegrees(Axis.YP, climb[1] * p);
	}

	/** The wall it's against: the one it's heading into, else any beside it. */
	static @Nullable Direction nearestWall(Spider spider) {
		BlockPos at = spider.blockPosition();
		Direction heading = spider.getMotionDirection();
		if (heading.getAxis().isHorizontal() && solid(spider, at.relative(heading))) {
			return heading;
		}
		for (Direction d : Direction.Plane.HORIZONTAL) {
			if (solid(spider, at.relative(d))) {
				return d;
			}
		}
		return null;
	}

	private static boolean solid(Spider spider, BlockPos pos) {
		return !spider.level().getBlockState(pos).getCollisionShape(spider.level(), pos).isEmpty();
	}
}
