package com.jaymospowers.client.power.web;

import com.jaymospowers.power.web.WebSymbiote;
import com.jaymospowers.power.web.WebTendrils;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.phys.AABB;

/**
 * (0.16.2) The symbiote climbing a wall (Wall Crawl on, the tendrils out, the latch biting a wall: thin armour or the big
 * suit), the way Venom goes up a skyscraper: on all fours against it, the torso flat to the wall and spread a little
 * wider and heavier than it stands, the head up the wall, the arms reaching far overhead and the legs spread wide,
 * every limb planted on it. It climbs in bounds ({@code WebSymbioteClient.BOUND_GATHER}): gathered under the bite with
 * the knees drawn up and the arms bent, then heaving up with the arms flung straight overhead and the legs driving, then
 * the hands and feet slamming into the wall as the body stops, jolting into it. Each client reads the bound off the
 * climber's own motion (rising hard is the heave; stopping dead after it is the slam), so everyone sees the same
 * climb, and the pose eases in and out ({@link WebEase}) so nothing snaps. The walking tendrils keep assisting behind
 * it; the hands and feet do the visible work. Sideways crawls and ceilings keep their old look.
 */
public final class WebClimbPose {
	// ===== the look: knobs to tweak ==================================================================================
	/** Ticks the pose takes to blend in and out. */
	static final float BLEND_TICKS = 6.0F;
	/** The spread: the suit drawn this much wider across the wall, and flattened against it by as much (a share of its size). */
	static final float SPREAD = 0.1F;
	/** How far the body tips forward onto the wall about its middle (degrees), and how far it presses in toward it (blocks). */
	static final float FLAT = 6.0F, PRESS = 0.0F;
	/** A touch of bend at the hip, chest to the wall (radians). */
	static final float HIP_BEND = 0.08F;
	/** The arms: straight overhead at the top of a heave (radians, -pi is straight up), bent down and ready in the gather, held this far out to the sides. */
	static final float ARM_REACH = -2.95F, ARM_GATHER = -1.9F, ARM_SPREAD = 0.5F;
	/** The legs: knees drawn up frog-like in the gather, driven nearly straight in the heave (radians); spread this wide. */
	static final float LEG_GATHER = -1.1F, LEG_HEAVE = -0.25F, LEG_SPREAD = 0.45F;
	/** The head, looking up the wall (radians), plus this share of where the player looks. */
	static final float HEAD_UP = -0.75F, HEAD_LOOK = 0.3F;
	/** How far the body sinks toward its feet in the gather (model pixels), and how much more the big suit gathers and bends. */
	static final float GATHER_SINK = 1.5F, BIG_WEIGHT = 1.15F;
	/** Rising at this speed (blocks/tick) is a full heave; the heave weight eases over this many ticks. */
	static final float HEAVE_FULL = 0.4F, HEAVE_EASE = 2.0F;
	/** A slam: it was rising at least this share of a full heave and dropped under this share; its jolt lasts this many ticks. */
	static final float SLAM_FROM = 0.5F, SLAM_TO = 0.2F, SLAM_TICKS = 4.0F;
	/** The slam's jolt: the body thrown this far in against the wall (blocks), the limbs kicked this far (radians), the body dropped this far (pixels). */
	static final float SLAM_JOLT = 0.08F, SLAM_KICK = 0.25F, SLAM_DROP = 1.0F;
	// ================================================================================================================

	/** {@code in}: how far into the pose (0-1); {@code heave}: how hard it's rising (0-1); {@code slam}: the jolt of the last slam (1 at the impact, fading to 0). */
	public record Climb(float in, float heave, float slam, float time, boolean big) {
	}

	public static final RenderStateDataKey<Climb> KEY = RenderStateDataKey.create(() -> "jaymospowers:web_climb");

	/** Entity id → how far into the pose, and how hard it's heaving, both eased. */
	private static final WebEase IN = new WebEase(), HEAVE = new WebEase();
	/** Per entity id: the vertical speed last seen, and when its last slam was. */
	private static final Map<Integer, float[]> BOUNDS = new HashMap<>();

	private WebClimbPose() {
	}

	/**
	 * Climbing a wall by the symbiote's tendrils right now: Wall Crawl on with the tendrils out, the latch biting, off
	 * the ground, a wall against the body and no ceiling overhead (a ceiling crawl hangs, WebHangPose).
	 */
	static boolean climbingWall(Player player) {
		if (!WebSymbiote.isBonded(player) || !WebSymbiote.crawlTendrils(player) || player.getAttached(WebTendrils.CLIMB) == null
			|| player.onGround() || player.isPassenger() || player.isFallFlying() || player.getAbilities().flying) {
			return false;
		}
		AABB box = player.getBoundingBox();
		if (!player.level().noCollision(player, box.move(0.0, 0.15, 0.0).deflate(0.05, 0.0, 0.05))) {
			return false;
		}
		AABB side = box.deflate(0.0, 0.1, 0.0);
		for (Direction dir : Direction.Plane.HORIZONTAL) {
			if (!player.level().noCollision(player, side.move(dir.getUnitVec3().scale(0.2)))) {
				return true;
			}
		}
		return false;
	}

	public static void extract(LivingEntity entity, LivingEntityRenderState state, float partialTicks) {
		if (!(entity instanceof Player player)) {
			state.setData(KEY, null);
			return;
		}
		boolean on = climbingWall(player);
		float in = WebEase.smooth(IN.to(entity.getId(), on ? 1.0F : 0.0F, BLEND_TICKS));
		if (in <= 0.001F) {
			BOUNDS.remove(entity.getId());
			state.setData(KEY, null);
			return;
		}
		if (BOUNDS.size() > 64) {
			BOUNDS.keySet().removeIf(id -> id != entity.getId() && entity.level().getEntity(id) == null);
		}
		float time = entity.level().getGameTime() + partialTicks;
		float vy = (float)(entity.getY() - entity.yo);
		float[] bound = BOUNDS.computeIfAbsent(entity.getId(), id -> new float[] {vy, -100.0F});
		if (bound[0] > HEAVE_FULL * SLAM_FROM && vy < HEAVE_FULL * SLAM_TO && time - bound[1] > SLAM_TICKS) {
			// rising hard, then stopped dead: the hands and feet just hit the wall
			bound[1] = time;
		}
		bound[0] = vy;
		float heave = HEAVE.to(entity.getId(), Mth.clamp(vy / HEAVE_FULL, 0.0F, 1.0F), HEAVE_EASE);
		float slam = 1.0F - Mth.clamp((time - bound[1]) / SLAM_TICKS, 0.0F, 1.0F);
		// no stepping on the wall: the limbs are planted
		state.walkAnimationSpeed *= 1.0F - in;
		state.setData(KEY, new Climb(in, heave, slam, time, WebSymbiote.bulked(player)));
	}

	/** After the body faces its way: flattened and spread against the wall, jolting into it with each slam. */
	public static void rotate(LivingEntityRenderState state, PoseStack poseStack, float entityScale) {
		Climb climb = state.getData(KEY);
		if (climb == null || state.deathTime > 0.0F) {
			return;
		}
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player != null && state instanceof AvatarRenderState avatar && avatar.id == minecraft.player.getId()
			&& minecraft.options.getCameraType().isFirstPerson()) {
			// your own body round the camera (First-person Model) keeps its size and stance
			return;
		}
		float in = climb.in();
		float middle = state.boundingBoxHeight / Math.max(entityScale, 0.01F) * 0.5F;
		// (here +X is the body's right, +Y up, +Z behind it: the wall is ahead)
		poseStack.translate(0.0F, 0.0F, -(PRESS + SLAM_JOLT * climb.slam() * climb.slam()) * in);
		float spread = 1.0F + SPREAD * in;
		poseStack.scale(spread, 1.0F, 1.0F / spread);
		poseStack.translate(0.0F, middle, 0.0F);
		poseStack.rotateDegrees(Axis.XP, -FLAT * in);
		poseStack.translate(0.0F, -middle, 0.0F);
	}

	/** After the model animates: on all fours against the wall, eased from whatever the body was doing. */
	public static void applyModel(Model<?> model, Object state) {
		if (!(state instanceof AvatarRenderState avatar) || !(model instanceof HumanoidModel<?> h)) {
			return;
		}
		Climb climb = avatar.getData(KEY);
		if (climb == null || avatar.getData(WebArms.KEY) != null) {
			return;
		}
		float[] was = WebFeral.snapshot(h);
		pose(h, avatar, climb);
		WebFeral.blend(h, was, climb.in());
	}

	/** The climb outright: set from the render state alone, so applying it twice changes nothing. */
	private static void pose(HumanoidModel<?> h, AvatarRenderState avatar, Climb climb) {
		boolean slim = avatar.skin != null && avatar.skin.model() == PlayerModelType.SLIM;
		float heave = climb.heave();
		// the jolt: hardest at the impact, gone in a few ticks
		float jolt = climb.slam() * climb.slam();
		float weight = climb.big() ? BIG_WEIGHT : 1.0F;
		float armY = slim ? 2.5F : 2.0F;
		// upright on the wall (the hunch had bent everything forward at the hip), chest to it
		WebFeral.place(h.body, 0.0F, 0.0F, HIP_BEND);
		WebFeral.place(h.head, 0.0F, 0.0F, HIP_BEND);
		WebFeral.place(h.rightArm, -5.0F, armY, HIP_BEND);
		WebFeral.place(h.leftArm, 5.0F, armY, HIP_BEND);
		h.body.xRot = HIP_BEND;
		h.body.yRot = 0.0F;
		h.body.zRot = 0.0F;
		// the head up the wall, where it's going (and where the player looks, a little)
		h.head.xRot = HEAD_UP + avatar.xRot * Mth.DEG_TO_RAD * HEAD_LOOK;
		h.head.zRot = 0.0F;
		// the arms: bent and ready in the gather, flung straight overhead in the heave, recoiling at the slam; the two a beat apart
		float beat = Mth.sin(climb.time() * 0.9F) * 0.05F;
		float reach = Mth.lerp(heave, ARM_GATHER * weight, ARM_REACH) + jolt * SLAM_KICK;
		h.rightArm.xRot = reach + beat - HIP_BEND;
		h.leftArm.xRot = reach - beat - HIP_BEND;
		h.rightArm.yRot = 0.0F;
		h.leftArm.yRot = 0.0F;
		float armSpread = ARM_SPREAD * (1.0F - 0.3F * heave);
		h.rightArm.zRot = armSpread;
		h.leftArm.zRot = -armSpread;
		// the legs: knees drawn up in the gather, driving straight in the heave, planting hard at the slam; spread wide
		float leg = Mth.lerp(heave, LEG_GATHER * weight, LEG_HEAVE) - jolt * SLAM_KICK;
		h.rightLeg.xRot = leg - beat;
		h.leftLeg.xRot = leg + beat;
		h.rightLeg.yRot = 0.0F;
		h.leftLeg.yRot = 0.0F;
		h.rightLeg.zRot = LEG_SPREAD;
		h.leftLeg.zRot = -LEG_SPREAD;
		// sunk toward the feet in the gather, dropped a touch by the slam
		h.root().y = GATHER_SINK * weight * (1.0F - heave) + SLAM_DROP * jolt;
	}
}
