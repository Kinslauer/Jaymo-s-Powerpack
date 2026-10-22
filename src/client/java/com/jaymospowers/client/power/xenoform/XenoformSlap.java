package com.jaymospowers.client.power.xenoform;

import com.jaymospowers.client.ScreenShake;
import com.jaymospowers.power.xenoform.XenoformPower;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Avatar;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

/**
 * Slamming the band: the left arm (the band's wrist) comes up across the chest and the right hand drops onto it. For
 * yourself the arm rises as you hold Z (while you scroll the dial) and the hand comes down as you let go; as an alien the
 * hand lands as the hold turns you back (the server's {@link XenoformPower#HOLD_TICKS}). Everyone else sees the slap when a player changes shape. The pose is worked
 * out here from the time ({@link #extract}, onto the render state), laid over the model after it animates
 * ({@link #applyModel}, through the model-feature mixin: the human body and the alien alike), and mirrored in first
 * person by {@code XenoformFirstPersonMixin}.
 */
public final class XenoformSlap {
	/**
	 * The slap as raw clocks: {@code rise} the wrist coming up with the hand poised (0-1), {@code hit} the hand coming down
	 * on it (0-1), {@code since} the ticks since the hand landed (-1 before it has), {@code fall} the arms dropping away
	 * (0-1). Third person eases them ({@link #raise}, {@link #strike}); first person shapes its own curves from them
	 * ({@link XenoformSlamView}).
	 */
	public record Pose(float rise, float hit, float since, float fall) {
		/** How far the wrist is up and the hand poised (0-1), eased, as the arms fall away. */
		public float raise() {
			return ease(this.rise) * ease(1.0F - this.fall);
		}

		/** How far the hand has come down on it (0-1), eased. */
		public float strike() {
			return ease(this.hit) * ease(1.0F - this.fall);
		}
	}

	public static final RenderStateDataKey<Pose> KEY = RenderStateDataKey.create(() -> "jaymospowers:xenoform_slap");

	/** Ticks: the wrist rising; the hand dropping; resting on the band after the slam; the arms falling back. */
	static final float RAISE_TICKS = 5.5F, STRIKE_TICKS = 2.0F, REST_TICKS = 4.0F, DROP_TICKS = 6.0F;
	/** Someone else's slap starts with the wrist already on its way: this long before the hand drops. */
	static final float OTHERS_RAISE_TICKS = 2.0F;
	/** Someone else's hand drops over this long (yours is the faster {@link #STRIKE_TICKS}). */
	static final float OTHERS_STRIKE_TICKS = 3.0F;
	/** A dropped (refused-looking) release still strikes if the form changes this soon after: the HUD's recharge can lag the server. */
	static final float DROP_GRACE_TICKS = 6.0F;
	/** A hold older than this when the change comes isn't what caused it (a timed-out form, a glitch). */
	static final float HOLD_STALE_TICKS = 40.0F;
	/** The band's wrist, raised across the chest (radians, left arm). */
	static final float WRIST_X = -1.15F, WRIST_Y = 0.85F;
	/** The right hand poised high, and down on the wrist. */
	static final float POISED_X = -2.4F, POISED_Y = -0.3F, POISED_Z = 0.3F, STRUCK_X = -1.34F, STRUCK_Y = -0.49F;
	/**
	 * Your own body seen from your eyes (First-person Model drawing it): the forearm comes up level across the front of
	 * the chest, rolled so the band's dial faces up at you, just under the middle of the view; the right hand hangs above
	 * it and comes down onto the dial. The left arm's turn is {@link #FP_WRIST} (ZYX radians: a big roll, so it is slerped
	 * from the arm's own pose rather than lerped angle by angle); the right arm's are angles (radians).
	 */
	static final Quaternionf FP_WRIST = new Quaternionf().rotationZYX(1.536F, -2.897F, 0.977F);
	static final float FP_POISED_X = -2.57F, FP_POISED_Y = -0.85F, FP_STRUCK_X = -2.22F, FP_STRUCK_Y = -0.85F;
	/** First person: the camera kick when the hand lands (degrees, ticks). */
	static final float IMPACT_SHAKE = 0.9F;
	static final int IMPACT_SHAKE_TICKS = 7;
	/** The head looks down at the band. */
	static final float HEAD_DOWN = 0.4F;

	// yourself
	private static float holdAt = -1.0F, releaseAt = -1.0F, slamAt = -1.0F, dropAt = -1.0F;
	/** The hold began as yourself: letting go is the slam (the hand comes down then), not a hold timer. */
	private static boolean slamOnRelease;
	/** The first-person burst and kick of this slam have gone off (once a slam). */
	private static boolean landed;
	/** The running slam started from your own release: play the strike, like everyone else's slap. */
	private static boolean selfStrike;
	/** An alien's hold that the change cut short: when it began (the wrist keeps rising), else -1. The hand then lands at slamAt. */
	private static float earlyFrom = -1.0F;
	// everyone else: the form each was last seen in, and when their slap started
	private static final Map<UUID, @Nullable String> FORMS = new HashMap<>();
	private static final Map<UUID, Float> SLAMS = new HashMap<>();

	private XenoformSlap() {
	}

	/** Z went down (as a band wearer). */
	public static void holdStart(float now, boolean human) {
		holdAt = now;
		releaseAt = -1.0F;
		slamOnRelease = human;
	}

	/** Z came up: as yourself, the band slams now. */
	public static void release(float now) {
		if (holdAt >= 0.0F && releaseAt < 0.0F) {
			if (slamOnRelease) {
				// the wrist keeps the place the hold had it, then finishes its rise at the faster others' rate before the strike: no snap on a short tap
				float r = Math.min(1.0F, Math.max(0.0F, now - holdAt) / RAISE_TICKS);
				slamAt = now - OTHERS_RAISE_TICKS * r;
				selfStrike = true;
				landed = false;
				earlyFrom = -1.0F;
				holdAt = -1.0F;
				return;
			}
			releaseAt = now;
		}
	}

	/** Z came up but nothing will change (the band is recharging): the arms just drop away. */
	public static void drop(float now) {
		if (holdAt >= 0.0F && releaseAt < 0.0F) {
			releaseAt = now;
			dropAt = now;
		}
	}

	/** Your shape changed: if Z was the cause, the hand lands (unless the release already brought it down). */
	public static void changed(float now) {
		boolean striking = selfStrike && slamAt >= 0.0F && now - slamAt <= HOLD_STALE_TICKS;
		if (dropAt >= 0.0F && now - dropAt <= DROP_GRACE_TICKS && !striking) {
			// the drop was a guess from a lagging HUD: the band did change, so the hand comes down after all
			slamAt = now - OTHERS_RAISE_TICKS;
			selfStrike = true;
			landed = false;
			earlyFrom = -1.0F;
			holdAt = releaseAt = dropAt = -1.0F;
			return;
		}
		dropAt = -1.0F;
		if (slamOnRelease && holdAt >= 0.0F && releaseAt < 0.0F && !striking && now - holdAt <= HOLD_STALE_TICKS) {
			// Z is still down as a human (a glitch changed the shape): the hand stays poised and the release slams, no jump
			return;
		}
		// a hold that was already let go (releaseAt) isn't what caused this change
		if (!striking && holdAt >= 0.0F && releaseAt < 0.0F && now - holdAt <= HOLD_STALE_TICKS) {
			// an alien's hand has already come down by the hold's own clock: carry on from there, so the landing isn't played twice
			float held = now - holdAt;
			if (!slamOnRelease && held < XenoformPower.HOLD_TICKS) {
				// cut short (the form ran out): the hand comes down from where the hold had it, over the usual strike
				float hit = Mth.clamp((held - (XenoformPower.HOLD_TICKS - STRIKE_TICKS)) / STRIKE_TICKS, 0.0F, 1.0F);
				slamAt = now + (1.0F - hit) * STRIKE_TICKS;
				earlyFrom = holdAt;
			} else {
				float late = slamOnRelease ? 0.0F : Math.max(0.0F, held - XenoformPower.HOLD_TICKS);
				slamAt = now - late;
				earlyFrom = -1.0F;
			}
			selfStrike = false;
		}
		holdAt = -1.0F;
		releaseAt = -1.0F;
	}

	public static void clear() {
		holdAt = releaseAt = slamAt = earlyFrom = dropAt = -1.0F;
		selfStrike = slamOnRelease = landed = false;
		FORMS.clear();
		SLAMS.clear();
	}

	/** From the avatar renderer's extractRenderState: this player's slap pose right now, or null. */
	public static @Nullable Pose extract(Avatar entity, @Nullable String form, float partialTicks) {
		float now = entity.tickCount + partialTicks;
		if (entity == Minecraft.getInstance().player) {
			Pose pose = pose(now, holdAt, releaseAt, slamAt, !selfStrike, slamOnRelease, selfStrike ? STRIKE_TICKS : OTHERS_STRIKE_TICKS);
			if (pose == null) {
				slamAt = -1.0F;
				selfStrike = false;
				landed = false;
				earlyFrom = -1.0F;
				// a Z held down through the drop starts its rise over from the lowered arm
				if (holdAt >= 0.0F) {
					holdAt = now;
				}
			}
			return pose;
		}
		UUID id = entity.getUUID();
		boolean known = FORMS.containsKey(id);
		String was = FORMS.put(id, form);
		if (known && was == null && form != null) {
			SLAMS.put(id, now);
		}
		Float slam = SLAMS.get(id);
		if (slam == null) {
			return null;
		}
		Pose pose = pose(now, -1.0F, -1.0F, slam, false, false, OTHERS_STRIKE_TICKS);
		if (pose == null) {
			SLAMS.remove(id);
		}
		return pose;
	}

	static @Nullable Pose pose(float now, float hold, float release, float slam, boolean fromHold, boolean poisedWhileHeld, float strikeTicks) {
		if (slam >= 0.0F) {
			float dt = now - slam;
			if (fromHold && dt < 0.0F && earlyFrom >= 0.0F) {
				// the hand still on its way down after a cut-short hold
				return new Pose(Math.min(1.0F, (now - earlyFrom) / RAISE_TICKS), 1.0F + dt / STRIKE_TICKS, -1.0F, 0.0F);
			}
			if (!fromHold) {
				if (dt < OTHERS_RAISE_TICKS) {
					return new Pose(dt / OTHERS_RAISE_TICKS, 0.0F, -1.0F, 0.0F);
				}
				if (dt < OTHERS_RAISE_TICKS + strikeTicks) {
					return new Pose(1.0F, (dt - OTHERS_RAISE_TICKS) / strikeTicks, -1.0F, 0.0F);
				}
				dt -= OTHERS_RAISE_TICKS + strikeTicks;
			}
			// dt: the ticks since the hand landed
			if (dt < REST_TICKS) {
				// a cut-short alien hold may still be mid-rise as the hand lands: carry the rise on
				return new Pose(fromHold && earlyFrom >= 0.0F ? Math.min(1.0F, (now - earlyFrom) / RAISE_TICKS) : 1.0F, 1.0F, dt, 0.0F);
			}
			if (dt < REST_TICKS + DROP_TICKS) {
				return new Pose(1.0F, 1.0F, dt, (dt - REST_TICKS) / DROP_TICKS);
			}
			return null;
		}
		if (hold >= 0.0F) {
			float dt = now - hold;
			float rise = Math.min(1.0F, dt / RAISE_TICKS);
			// as yourself the hand waits, poised, for as long as you hold (and scroll); as an alien it lands as the hold turns you back
			float hit = poisedWhileHeld ? 0.0F : Mth.clamp((dt - (XenoformPower.HOLD_TICKS - STRIKE_TICKS)) / STRIKE_TICKS, 0.0F, 1.0F);
			float since = hit >= 1.0F ? dt - XenoformPower.HOLD_TICKS : -1.0F;
			float fall = 0.0F;
			if (release >= 0.0F) {
				fall = (now - release) / DROP_TICKS;
				if (fall >= 1.0F) {
					return null;
				}
			}
			return new Pose(rise, hit, since, fall);
		}
		return null;
	}

	/** First person, once the hand has landed: the camera's kick and the burst of light, once a slam. */
	public static void landed(Pose pose, LocalPlayer player) {
		if (pose.since() < 0.0F || landed) {
			return;
		}
		landed = true;
		// felt from just under the eye: the view dips, then rattles
		ScreenShake.add(IMPACT_SHAKE, IMPACT_SHAKE_TICKS, player.getEyePosition().add(0.0, -0.5, 0.0));
		XenoformPowerClient.burst = XenoformPowerClient.BURST_TICKS;
	}

	private static float ease(float t) {
		t = Mth.clamp(t, 0.0F, 1.0F);
		return t * t * (3.0F - 2.0F * t);
	}

	/** Right after a humanoid model animates (the model-feature mixin, and the band layer before it places the band). */
	public static void applyModel(Model<?> model, Object state) {
		if (state instanceof AvatarRenderState avatar && model instanceof HumanoidModel<?> h) {
			Pose pose = avatar.getData(KEY);
			if (pose != null) {
				apply(h, pose, ownBody(avatar));
			}
		}
	}

	/** {@code state} is your own body as First-person Model draws it in first person: the slam plays on it. */
	public static boolean ownBody(@Nullable AvatarRenderState state) {
		LocalPlayer self = Minecraft.getInstance().player;
		return state != null && self != null && state.id == self.getId() && com.jaymospowers.client.power.web.FirstPersonCompat.bodyShown();
	}

	public static void apply(HumanoidModel<?> h, Pose pose, boolean firstPerson) {
		float raise = pose.raise(), strike = pose.strike();
		if (firstPerson) {
			Quaternionf from = new Quaternionf().rotationZYX(h.leftArm.zRot, h.leftArm.yRot, h.leftArm.xRot);
			Vector3f euler = from.slerp(FP_WRIST, raise).getEulerAnglesZYX(new Vector3f());
			h.leftArm.xRot = euler.x;
			h.leftArm.yRot = euler.y;
			h.leftArm.zRot = euler.z;
			h.rightArm.xRot = Mth.lerp(raise, h.rightArm.xRot, Mth.lerp(strike, FP_POISED_X, FP_STRUCK_X));
			h.rightArm.yRot = Mth.lerp(raise, h.rightArm.yRot, Mth.lerp(strike, FP_POISED_Y, FP_STRUCK_Y));
			h.rightArm.zRot = Mth.lerp(raise, h.rightArm.zRot, 0.0F);
			return;
		}
		h.leftArm.xRot = Mth.lerp(raise, h.leftArm.xRot, WRIST_X);
		h.leftArm.yRot = Mth.lerp(raise, h.leftArm.yRot, WRIST_Y);
		h.leftArm.zRot = Mth.lerp(raise, h.leftArm.zRot, 0.0F);
		h.rightArm.xRot = Mth.lerp(raise, h.rightArm.xRot, Mth.lerp(strike, POISED_X, STRUCK_X));
		h.rightArm.yRot = Mth.lerp(raise, h.rightArm.yRot, Mth.lerp(strike, POISED_Y, STRUCK_Y));
		h.rightArm.zRot = Mth.lerp(raise, h.rightArm.zRot, Mth.lerp(strike, POISED_Z, 0.0F));
		h.head.xRot += HEAD_DOWN * raise;
	}
}
