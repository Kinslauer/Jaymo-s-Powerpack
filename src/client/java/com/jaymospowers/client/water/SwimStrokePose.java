package com.jaymospowers.client.water;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/**
 * (Motion patch 2) Swimming the way you're going, after Actions &amp; Stuff's directional swimming. In water and off the
 * bottom without sprint-swimming (vanilla's crawl stroke keeps that), a player's body takes the stroke for the way
 * they're moving against the way they face: a breaststroke leaning into the water going forward, a backstroke laid back
 * going backward, a side stroke rolled onto the side going sideways, and treading water (sculling hands, slow
 * cycling legs) when hardly moving. Worked out from where they move, so everyone in view sees the same. It pauses
 * Fresh Animations while it plays ({@code PoseTakeover}), and any power's pose wins over it.
 * <p>(ParCool track B) In a fast swim ({@code FastSwim}, out of a long sprint swim) the crawl stroke gives way to a
 * butterfly: both arms sweep round together and the legs dolphin-kick as one.
 */
public final class SwimStrokePose {
	// ===== the knobs ================================================================================================
	/** Moving slower than this (blocks a tick) is treading water. */
	static final double TREAD_BELOW = 0.025;
	/** Only in water deeper than this share of the body's height (so hopping about in the shallows isn't swimming). */
	static final double WAIST = 0.5;
	/** How far the body leans: into a breaststroke, back for a backstroke, rolled for a side stroke (degrees). */
	static final float BREAST_LEAN = 55.0F, BACK_LEAN = 60.0F, SIDE_ROLL = 50.0F, SIDE_LEAN = 35.0F;
	/** Stroke speed (radians a tick) for each stroke, and treading water's. */
	static final float STROKE_SPEED = 0.22F, TREAD_SPEED = 0.16F;
	/** How fast a stroke blends into the next (share a tick), and into the pose at all. */
	static final float BLEND = 0.18F;
	/** The fast swim's butterfly: stroke speed (radians a tick) and how hard the legs kick. */
	static final float FAST_SPEED = 0.3F, FAST_KICK = 0.5F;
	// ================================================================================================================

	/** Weights (0-1, adding to {@code in}) of each stroke ({@code fast}: the fast swim's butterfly); {@code side}: −1 left, 1 right; {@code time} ticks. */
	public record Stroke(float tread, float breast, float back, float sideStroke, float side, float time, float in, float fast) {
	}

	public static final RenderStateDataKey<Stroke> KEY = RenderStateDataKey.create(() -> "jaymospowers:swim_stroke");

	/** Per entity id: {tread, breast, back, side, in, last frame time, side sign, fast}. */
	private static final Map<Integer, float[]> BLENDS = new HashMap<>();

	private SwimStrokePose() {
	}

	public static void extract(LivingEntity entity, LivingEntityRenderState state, float partialTicks) {
		if (!(entity instanceof Player player) || !(state instanceof AvatarRenderState avatar)) {
			return;
		}
		boolean fast = player.isInWater() && avatar.isVisuallySwimming && com.jaymospowers.freerun.moves.FastSwim.active(player) && !player.isPassenger();
		boolean swimming = fast || player.isInWater() && player.getFluidHeight(FluidTags.WATER) > player.getBbHeight() * WAIST && !player.onGround() && !player.isPassenger() && !player.isSpectator()
			&& !avatar.isVisuallySwimming && !avatar.isFallFlying && state.pose == Pose.STANDING && !player.getAbilities().flying;
		float time = player.level().getGameTime() + partialTicks;
		float[] b = BLENDS.get(player.getId());
		if (b == null) {
			if (!swimming) {
				state.setData(KEY, null);
				return;
			}
			b = new float[] {fast ? 0.0F : 1.0F, 0.0F, 0.0F, 0.0F, 0.0F, time, 1.0F, fast ? 1.0F : 0.0F};
			BLENDS.put(player.getId(), b);
		}
		float frame = Mth.clamp(time - b[5], 0.0F, 2.0F);
		b[5] = time;
		float k = Math.min(1.0F, BLEND * frame);
		float tread = 0.0F, breast = 0.0F, back = 0.0F, sideStroke = 0.0F, butterfly = 0.0F;
		if (fast) {
			butterfly = 1.0F;
		} else if (swimming) {
			double dx = player.getX() - player.xo, dz = player.getZ() - player.zo;
			float yaw = state.bodyRot * Mth.DEG_TO_RAD;
			double forward = dx * -Mth.sin(yaw) + dz * Mth.cos(yaw), right = dx * -Mth.cos(yaw) + dz * -Mth.sin(yaw);
			if (Math.sqrt(dx * dx + dz * dz) < TREAD_BELOW) {
				tread = 1.0F;
			} else if (Math.abs(right) > Math.abs(forward)) {
				sideStroke = 1.0F;
				b[6] = right > 0.0 ? 1.0F : -1.0F;
			} else if (forward > 0.0) {
				breast = 1.0F;
			} else {
				back = 1.0F;
			}
		}
		b[0] += (tread - b[0]) * k;
		b[1] += (breast - b[1]) * k;
		b[2] += (back - b[2]) * k;
		b[3] += (sideStroke - b[3]) * k;
		b[7] += (butterfly - b[7]) * k;
		b[4] = Mth.clamp(b[4] + (swimming ? k : -k * 1.5F), 0.0F, 1.0F);
		if (!swimming && b[4] <= 0.0F) {
			BLENDS.remove(player.getId());
			state.setData(KEY, null);
			return;
		}
		float in = b[4] * b[4] * (3.0F - 2.0F * b[4]);
		state.setData(KEY, new Stroke(b[0], b[1], b[2], b[3], b[6], time, in, b[7]));
	}

	static @Nullable Stroke stroke(LivingEntityRenderState state) {
		return state.deathTime > 0.0F ? null : state.getData(KEY);
	}

	/** After the body faces its way: leaned into the stroke, about the middle of the body. */
	public static void rotate(LivingEntityRenderState state, PoseStack poseStack, float entityScale) {
		Stroke s = stroke(state);
		if (s == null) {
			return;
		}
		float middle = state.boundingBoxHeight / Math.max(entityScale, 0.01F) * 0.5F;
		poseStack.translate(0.0F, middle, 0.0F);
		// a negative turn about X tips the top forward; a negative turn about Z tips it to the right
		float pitch = -BREAST_LEAN * s.breast() + BACK_LEAN * s.back() - SIDE_LEAN * s.sideStroke();
		poseStack.rotateDegrees(Axis.XP, pitch * s.in());
		poseStack.rotateDegrees(Axis.ZP, -s.side() * SIDE_ROLL * s.sideStroke() * s.in());
		// treading water bobs a little
		poseStack.translate(0.0F, -middle + Mth.sin(s.time() * TREAD_SPEED * 2.0F) * 0.04F * s.tread() * s.in(), 0.0F);
	}

	/** After the model animates: the arms and legs of each stroke, blended. */
	public static void applyModel(Model<?> model, Object state) {
		if (!(state instanceof LivingEntityRenderState living) || !(model instanceof HumanoidModel<?> h)) {
			return;
		}
		Stroke s = stroke(living);
		if (s == null) {
			return;
		}
		float t = s.time();
		float[] ra = new float[3], la = new float[3], rl = new float[3], ll = new float[3];
		// treading: hands sculling out to the sides, legs cycling slowly
		float sc = Mth.sin(t * TREAD_SPEED);
		add(ra, s.tread(), -0.5F, 0.0F, 0.75F + sc * 0.35F);
		add(la, s.tread(), -0.5F, 0.0F, -0.75F - sc * 0.35F);
		add(rl, s.tread(), Mth.sin(t * TREAD_SPEED) * 0.6F - 0.2F, 0.0F, 0.1F);
		add(ll, s.tread(), -Mth.sin(t * TREAD_SPEED) * 0.6F - 0.2F, 0.0F, -0.1F);
		// breaststroke: reach ahead, sweep out and round, pull in; the legs frog-kick
		float p = (t * STROKE_SPEED) % Mth.TWO_PI / Mth.TWO_PI;
		float reach = p < 0.4F ? p / 0.4F : 1.0F - (p - 0.4F) / 0.6F;
		add(ra, s.breast(), -3.0F + reach * 1.4F, 0.0F, 0.2F + reach * 1.1F);
		add(la, s.breast(), -3.0F + reach * 1.4F, 0.0F, -0.2F - reach * 1.1F);
		add(rl, s.breast(), 0.15F, 0.0F, 0.05F + reach * 0.45F);
		add(ll, s.breast(), 0.15F, 0.0F, -0.05F - reach * 0.45F);
		// backstroke: one arm then the other turning full circles over the head; a flutter kick
		float a = t * STROKE_SPEED;
		add(ra, s.back(), -(a % Mth.TWO_PI), 0.0F, 0.15F);
		add(la, s.back(), -((a + Mth.PI) % Mth.TWO_PI), 0.0F, -0.15F);
		add(rl, s.back(), Mth.sin(t * 0.6F) * 0.35F, 0.0F, 0.0F);
		add(ll, s.back(), -Mth.sin(t * 0.6F) * 0.35F, 0.0F, 0.0F);
		// side stroke: the lower arm reaching ahead, the upper one pulling down the body; a scissor kick
		boolean rightLow = s.side() > 0.0F;
		float pull = Mth.sin(t * STROKE_SPEED) * 0.5F + 0.5F;
		float[] low = rightLow ? ra : la, high = rightLow ? la : ra;
		float sign = rightLow ? 1.0F : -1.0F;
		add(low, s.sideStroke(), -2.9F + pull * 0.6F, 0.0F, sign * 0.15F);
		add(high, s.sideStroke(), -0.4F - (1.0F - pull) * 1.2F, 0.0F, -sign * 0.3F);
		add(rl, s.sideStroke(), (pull - 0.5F) * 1.0F, 0.0F, 0.0F);
		add(ll, s.sideStroke(), -(pull - 0.5F) * 1.0F, 0.0F, 0.0F);
		// butterfly: both arms sweep from far ahead down to the hips together, then swing back round out at the sides;
		// the legs kick as one, twice a stroke
		float bp = t * FAST_SPEED;
		float sweep = -Mth.PI * (0.5F + 0.5F * Mth.cos(bp));
		float out = 0.2F + 1.2F * Math.max(0.0F, Mth.sin(bp));
		add(ra, s.fast(), sweep, 0.0F, out);
		add(la, s.fast(), sweep, 0.0F, -out);
		float kick = Mth.sin(bp * 2.0F) * FAST_KICK;
		add(rl, s.fast(), kick, 0.0F, 0.0F);
		add(ll, s.fast(), kick, 0.0F, 0.0F);
		float total = Math.max(0.001F, s.tread() + s.breast() + s.back() + s.sideStroke() + s.fast());
		float e = s.in();
		set(h.rightArm, ra, total, e);
		set(h.leftArm, la, total, e);
		set(h.rightLeg, rl, total, e);
		set(h.leftLeg, ll, total, e);
		// the head kept looking where it was: a little up into a breaststroke, so the face clears the water
		h.head.xRot = Mth.lerp(e * s.breast(), h.head.xRot, Math.min(h.head.xRot, -0.6F));
	}

	private static void add(float[] to, float weight, float x, float y, float z) {
		to[0] += x * weight;
		to[1] += y * weight;
		to[2] += z * weight;
	}

	private static void set(ModelPart part, float[] rot, float total, float e) {
		part.xRot = Mth.lerp(e, part.xRot, rot[0] / total);
		part.yRot = Mth.lerp(e, part.yRot, rot[1] / total);
		part.zRot = Mth.lerp(e, part.zRot, rot[2] / total);
	}
}
