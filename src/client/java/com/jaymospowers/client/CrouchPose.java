package com.jaymospowers.client;

import com.jaymospowers.core.Carry;
import com.jaymospowers.core.Kneel;
import com.jaymospowers.core.finisher.Finishers;
import com.jaymospowers.core.wounds.Downed;
import com.jaymospowers.core.wounds.Tending;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Avatar;

/**
 * (0.17.7) Andy: "shift should now animate a crouch, the players gets low, hands out to the side, swaying as they move, ready
 * to draw a weapon and fire more comfortably on one knee when they stop moving. Not moving and crouching will kneel when
 * tending to players as well, or for executions". Drawn for everyone, over vanilla's crouch:
 * <ul>
 * <li>moving: lower still ({@link #LOW}), arms held out from the sides, the body swaying side to side with the stride;</li>
 * <li>still for {@link Kneel#KNEEL_AFTER} ticks: down on the left knee, right foot planted, upright, right hand by the hip
 * ready to draw, left forearm over the raised knee (the server steadies a bow from here, {@link Kneel}). So the hold of
 * a sneak + left-click execution is knelt through, and tending someone kneels as it always did ({@code TendPose}).</li>
 * </ul>
 * The arms are left alone while drawing a bow, aiming, blocking or swinging, and the whole pose stands aside for the other
 * full-body poses (tending, a finisher, carrying, crawling).
 * <p>(0.17.18) Andy: "crouching looks like a split right now, the back leg is fine, but the front leg should bend at the
 * knee, and stay bent while firing ... same for all ranged weapons". The front thigh now goes out level and the shin bends
 * down to the foot ({@link KneeBend}); the legs hold that whatever is in the hands, bow, crossbow, gun or thrown.
 */
public final class CrouchPose {
	/** Arms out, and down onto the knee or back up (ticks). */
	static final float READY_IN = 5.0F, KNEEL_IN = 7.0F, GET_UP = 5.0F;
	/** Moving: how much lower than vanilla's crouch (blocks). On the knee: how far below vanilla's crouch the body sits. */
	static final float LOW = 0.08F, SINK = 0.3F, HIP = 0.75F;
	/** The arms out from the sides (radians), and a little forward. */
	static final float ARMS_OUT = 0.55F, ARMS_FORWARD = -0.35F;
	/** The sway with the stride: the body's twist and roll (radians at full walking speed), and how fast it goes against the stride. */
	static final float SWAY_TWIST = 0.14F, SWAY_ROLL = 0.08F, SWAY_RATE = 0.3331F;
	/** On the knee: a slight lean forward (degrees). */
	static final float KNEEL_LEAN = 4.0F;
	/**
	 * On the knee: the front (right) thigh out level from the hip, the knee bent so the shin stands on the ground
	 * (radians), and the back (left) leg trailing to the knee on the ground. Drawn while aiming and firing too.
	 */
	static final float FRONT_THIGH = -1.55F, FRONT_KNEE = 1.5F, BACK_LEG = 1.35F;
	/** Under this walking speed (vanilla's 0..1) the body counts as still. */
	static final float STILL_SPEED = 0.05F;

	/** {@code ready}: 0 to 1, arms out; {@code kneel}: 0 to 1, on the knee; {@code pos}, {@code speed}: the stride's. */
	public record Crouch(float ready, float kneel, float pos, float speed) {
	}

	public static final RenderStateDataKey<Crouch> KEY = RenderStateDataKey.create(() -> "jaymospowers:crouch");

	private static final class Track {
		float ready;
		float kneel;
		float stillSince = -1.0F;
		float last = -1.0F;
	}

	private static final Int2ObjectOpenHashMap<Track> TRACKS = new Int2ObjectOpenHashMap<>();

	private CrouchPose() {
	}

	public static void extract(Avatar entity, AvatarRenderState state, float partialTicks) {
		Track track = TRACKS.get(entity.getId());
		boolean crouched = entity.isCrouching() && entity.onGround() && !entity.isPassenger() && !entity.isSwimming() && !entity.isFallFlying()
			&& !entity.isSpectator() && !otherwise(entity);
		if (!crouched && track == null) {
			state.setData(KEY, null);
			return;
		}
		if (track == null) {
			track = new Track();
			TRACKS.put(entity.getId(), track);
		}
		float now = entity.level().getGameTime() + partialTicks;
		float dt = track.last < 0.0F ? 0.0F : Mth.clamp(now - track.last, 0.0F, 5.0F);
		track.last = now;
		boolean still = crouched && state.walkAnimationSpeed < STILL_SPEED;
		if (!still) {
			track.stillSince = -1.0F;
		} else if (track.stillSince < 0.0F) {
			track.stillSince = now;
		}
		boolean kneel = still && now - track.stillSince >= Kneel.KNEEL_AFTER;
		track.ready = approach(track.ready, crouched ? 1.0F : 0.0F, dt / READY_IN);
		track.kneel = approach(track.kneel, kneel ? 1.0F : 0.0F, dt / (kneel ? KNEEL_IN : GET_UP));
		if (!crouched && track.ready <= 0.0F && track.kneel <= 0.0F || entity.isRemoved()) {
			TRACKS.remove(entity.getId());
			state.setData(KEY, null);
			return;
		}
		state.setData(KEY, new Crouch(smooth(track.ready), smooth(track.kneel), state.walkAnimationPos, state.walkAnimationSpeed));
	}

	/** Another full-body pose has them: tending, a finisher, carrying or being carried, crawling, downed. */
	private static boolean otherwise(Avatar entity) {
		return entity.hasAttached(Tending.TEND) || Finishers.of(entity) != null || Carry.isCarrying(entity) || Carry.isCarried(entity)
			|| Downed.isDowned(entity);
	}

	/** After the body faces its way: lower while moving, sunk onto the knee when still. */
	public static void rotate(AvatarRenderState state, PoseStack poseStack) {
		Crouch c = state.getData(KEY);
		if (c == null || state.deathTime > 0.0F || !state.isCrouching) {
			return;
		}
		poseStack.translate(0.0F, -LOW * c.ready() * (1.0F - c.kneel()) - SINK * c.kneel(), 0.0F);
		if (c.kneel() > 0.0F) {
			poseStack.translate(0.0F, HIP, 0.0F);
			poseStack.rotate(Axis.XP.rotationDegrees(-KNEEL_LEAN * c.kneel()));
			poseStack.translate(0.0F, -HIP, 0.0F);
		}
	}

	/** After the model animates (crouched by vanilla): arms out and swaying, or up out of the crouch onto one knee. */
	public static void applyModel(Model<?> model, Object renderState) {
		Crouch c = posed(model, renderState);
		if (c == null) {
			return;
		}
		AvatarRenderState state = (AvatarRenderState) renderState;
		HumanoidModel<?> h = (HumanoidModel<?>) model;
		boolean armsFree = !state.isUsingItem && state.swingAnimation <= 0.0F && free(state.rightArmPose) && free(state.leftArmPose);
		float walk = (1.0F - c.kneel()) * c.ready();
		if (walk > 0.0F) {
			// the sway: the shoulders roll and twist against the stride
			float phase = c.pos() * SWAY_RATE;
			float sway = Mth.clamp(c.speed(), 0.0F, 1.0F) * walk;
			h.body.yRot += Mth.sin(phase) * SWAY_TWIST * sway;
			h.body.zRot += Mth.cos(phase) * SWAY_ROLL * sway;
			h.head.yRot -= Mth.sin(phase) * SWAY_TWIST * 0.5F * sway;
			if (armsFree) {
				float bob = Mth.cos(phase) * 0.08F * sway;
				blend(h.rightArm, ARMS_FORWARD + bob, h.body.yRot, ARMS_OUT, walk);
				blend(h.leftArm, ARMS_FORWARD - bob, h.body.yRot, -ARMS_OUT, walk);
			}
		}
		float k = c.kneel();
		if (k <= 0.0F) {
			return;
		}
		// up out of vanilla's crouch: the body upright, the parts back where they stand
		float pitch = KNEEL_LEAN * Mth.DEG_TO_RAD;
		h.body.xRot = Mth.lerp(k, h.body.xRot, 0.0F);
		h.body.y = Mth.lerp(k, h.body.y, 0.0F);
		h.head.y = Mth.lerp(k, h.head.y, 0.0F);
		h.rightArm.y = Mth.lerp(k, h.rightArm.y, 2.0F);
		h.leftArm.y = Mth.lerp(k, h.leftArm.y, 2.0F);
		h.rightLeg.y = Mth.lerp(k, h.rightLeg.y, 12.0F);
		h.leftLeg.y = Mth.lerp(k, h.leftLeg.y, 12.0F);
		h.rightLeg.z = Mth.lerp(k, h.rightLeg.z, 0.0F);
		h.leftLeg.z = Mth.lerp(k, h.leftLeg.z, 0.0F);
		// right thigh out ahead, bent at the knee (KneeBend) down to the planted foot; left knee down
		blend(h.rightLeg, FRONT_THIGH - pitch, 0.1F, 0.0F, k);
		blend(h.leftLeg, BACK_LEG - pitch, -0.1F, 0.0F, k);
		if (armsFree) {
			// right hand by the hip, ready to draw; left forearm over the raised knee
			blend(h.rightArm, -0.25F, 0.0F, 0.12F, k);
			blend(h.leftArm, -0.75F, 0.25F, -0.05F, k);
		} else {
			// (aiming or swinging from the knee: the arms are vanilla's, only no longer crouched)
			h.rightArm.xRot -= 0.4F * k;
			h.leftArm.xRot -= 0.4F * k;
		}
	}

	/**
	 * Right before the model is drawn (after {@link #applyModel}): on the knee, the front leg bent at the knee for this one
	 * draw ({@link KneeBend#restore} once it's drawn).
	 */
	public static void bendKnee(Model<?> model, Object renderState) {
		Crouch c = posed(model, renderState);
		if (c != null && c.kneel() > 0.0F) {
			KneeBend.bend(((HumanoidModel<?>) model).rightLeg, FRONT_KNEE * c.kneel());
		}
	}

	/** The crouch to lay over this model, or null when it isn't a crouching body or another full-body pose has it. */
	private static Crouch posed(Model<?> model, Object renderState) {
		if (!(renderState instanceof AvatarRenderState state) || !(model instanceof HumanoidModel<?>) || state.deathTime > 0.0F || !state.isCrouching) {
			return null;
		}
		Crouch c = state.getData(KEY);
		if (c == null || state.getData(com.jaymospowers.client.wounds.TendPose.KEY) != null
			|| state.getData(com.jaymospowers.client.finisher.FinisherPose.KEY) != null || state.getData(CarryPose.CARRIER_KEY) != null
			|| state.getData(CrawlPose.KEY) != null) {
			return null;
		}
		return c;
	}

	/** An arm with nothing to do (empty, or just holding something), free to be held out or rested. */
	private static boolean free(HumanoidModel.ArmPose pose) {
		return pose == HumanoidModel.ArmPose.EMPTY || pose == HumanoidModel.ArmPose.ITEM;
	}

	private static float approach(float from, float to, float step) {
		return from < to ? Math.min(to, from + step) : Math.max(to, from - step);
	}

	private static float smooth(float t) {
		t = Mth.clamp(t, 0.0F, 1.0F);
		return t * t * (3.0F - 2.0F * t);
	}

	private static void blend(ModelPart part, float x, float y, float z, float k) {
		part.xRot = Mth.lerp(k, part.xRot, x);
		part.yRot = Mth.lerp(k, part.yRot, y);
		part.zRot = Mth.lerp(k, part.zRot, z);
	}
}
