package com.jaymospowers.client.power.xenoform;

import com.jaymospowers.power.xenoform.XenoformPower;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Avatar;
import org.jspecify.annotations.Nullable;

/**
 * Slamming the band: the left arm (the band's wrist) comes up across the chest and the right hand drops onto it. For
 * yourself the arm rises as you hold Z and the hand lands as the change fires (the server's {@link XenoformPower#HOLD_TICKS});
 * a tap only lifts the wrist for a look. Everyone else sees the slap when a player changes shape. The pose is worked
 * out here from the time ({@link #extract}, onto the render state), laid over the model after it animates
 * ({@link #applyModel}, through the model-feature mixin: the human body and the alien alike), and mirrored in first
 * person by {@code XenoformFirstPersonMixin}.
 */
public final class XenoformSlap {
	/** {@code raise}: how far the wrist is up and the hand poised (0-1); {@code strike}: how far the hand has come down on it. */
	public record Pose(float raise, float strike) {
	}

	public static final RenderStateDataKey<Pose> KEY = RenderStateDataKey.create(() -> "jaymospowers:xenoform_slap");

	/** Ticks: the wrist rising; the hand dropping; resting on the band after the slam; the arms falling back. */
	static final float RAISE_TICKS = 5.0F, STRIKE_TICKS = 3.0F, REST_TICKS = 4.0F, DROP_TICKS = 6.0F;
	/** Someone else's slap starts with the wrist already on its way: this long before the hand drops. */
	static final float OTHERS_RAISE_TICKS = 2.0F;
	/** A hold older than this when the change comes isn't what caused it (a timed-out form, a glitch). */
	static final float HOLD_STALE_TICKS = 40.0F;
	/** The band's wrist, raised across the chest (radians, left arm). */
	static final float WRIST_X = -1.15F, WRIST_Y = 0.85F;
	/** The right hand poised high, and down on the wrist. */
	static final float POISED_X = -2.4F, POISED_Y = -0.3F, POISED_Z = 0.3F, STRUCK_X = -1.34F, STRUCK_Y = -0.49F;
	/** The head looks down at the band. */
	static final float HEAD_DOWN = 0.4F;

	// yourself
	private static float holdAt = -1.0F, releaseAt = -1.0F, slamAt = -1.0F;
	// everyone else: the form each was last seen in, and when their slap started
	private static final Map<UUID, @Nullable String> FORMS = new HashMap<>();
	private static final Map<UUID, Float> SLAMS = new HashMap<>();

	private XenoformSlap() {
	}

	/** Z went down (as a band wearer). */
	public static void holdStart(float now) {
		holdAt = now;
		releaseAt = -1.0F;
	}

	/** Z came up. */
	public static void release(float now) {
		if (holdAt >= 0.0F && releaseAt < 0.0F) {
			releaseAt = now;
		}
	}

	/** Your shape changed: if Z was the cause, the hand lands. */
	public static void changed(float now) {
		if (holdAt >= 0.0F && now - holdAt <= HOLD_STALE_TICKS) {
			slamAt = now;
		}
		holdAt = -1.0F;
		releaseAt = -1.0F;
	}

	public static void clear() {
		holdAt = releaseAt = slamAt = -1.0F;
		FORMS.clear();
		SLAMS.clear();
	}

	/** From the avatar renderer's extractRenderState: this player's slap pose right now, or null. */
	public static @Nullable Pose extract(Avatar entity, @Nullable String form, float partialTicks) {
		float now = entity.tickCount + partialTicks;
		if (entity == Minecraft.getInstance().player) {
			Pose pose = pose(now, holdAt, releaseAt, slamAt, true);
			if (pose == null) {
				slamAt = -1.0F;
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
		Pose pose = pose(now, -1.0F, -1.0F, slam, false);
		if (pose == null) {
			SLAMS.remove(id);
		}
		return pose;
	}

	static @Nullable Pose pose(float now, float hold, float release, float slam, boolean fromHold) {
		if (slam >= 0.0F) {
			float dt = now - slam;
			if (!fromHold) {
				if (dt < OTHERS_RAISE_TICKS) {
					return new Pose(ease(dt / OTHERS_RAISE_TICKS), 0.0F);
				}
				if (dt < OTHERS_RAISE_TICKS + STRIKE_TICKS) {
					return new Pose(1.0F, ease((dt - OTHERS_RAISE_TICKS) / STRIKE_TICKS));
				}
				dt -= OTHERS_RAISE_TICKS + STRIKE_TICKS;
			}
			if (dt < REST_TICKS) {
				return new Pose(1.0F, 1.0F);
			}
			if (dt < REST_TICKS + DROP_TICKS) {
				float f = ease(1.0F - (dt - REST_TICKS) / DROP_TICKS);
				return new Pose(f, f);
			}
			return null;
		}
		if (hold >= 0.0F) {
			float dt = now - hold;
			float raise = ease(Math.min(1.0F, dt / RAISE_TICKS));
			float strike = ease(Mth.clamp((dt - (XenoformPower.HOLD_TICKS - STRIKE_TICKS)) / STRIKE_TICKS, 0.0F, 1.0F));
			if (release >= 0.0F) {
				float f = 1.0F - (now - release) / DROP_TICKS;
				if (f <= 0.0F) {
					return null;
				}
				raise *= ease(f);
				strike *= ease(f);
			}
			return new Pose(raise, strike);
		}
		return null;
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
				apply(h, pose);
			}
		}
	}

	public static void apply(HumanoidModel<?> h, Pose pose) {
		float raise = pose.raise(), strike = pose.strike();
		h.leftArm.xRot = Mth.lerp(raise, h.leftArm.xRot, WRIST_X);
		h.leftArm.yRot = Mth.lerp(raise, h.leftArm.yRot, WRIST_Y);
		h.leftArm.zRot = Mth.lerp(raise, h.leftArm.zRot, 0.0F);
		h.rightArm.xRot = Mth.lerp(raise, h.rightArm.xRot, Mth.lerp(strike, POISED_X, STRUCK_X));
		h.rightArm.yRot = Mth.lerp(raise, h.rightArm.yRot, Mth.lerp(strike, POISED_Y, STRUCK_Y));
		h.rightArm.zRot = Mth.lerp(raise, h.rightArm.zRot, Mth.lerp(strike, POISED_Z, 0.0F));
		h.head.xRot += HEAD_DOWN * raise;
	}
}
