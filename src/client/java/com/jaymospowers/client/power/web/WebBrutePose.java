package com.jaymospowers.client.power.web;

import com.jaymospowers.power.web.WebBrute;
import com.jaymospowers.power.web.WebSymbiote;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.PlayerModelType;

/**
 * The big suit's ground-wrecking moves ({@link WebBrute}) as seen, and the keys that start them. Poses: heaving a chunk
 * out (bent over, gripping, then hauling up), holding it overhead, hurling it, both fists raised and driven into the
 * ground, the shoulder charge (leaning in, one shoulder forward) and the stagger off a wall. Each eases in and out
 * from whatever the body was doing; everyone sees it, since the move is synced.
 */
public final class WebBrutePose {
	// ===== the look: knobs to tweak ==================================================================================
	/** Ticks a pose takes to blend in, and the finite ones to blend out. */
	static final float BLEND_IN = 3.0F, BLEND_OUT = 4.0F;
	/** Heaving: bent this far (radians), the arms down-forward gripping, then hauled up to this. */
	static final float RIP_BEND = 0.85F, RIP_GRIP = -0.9F, RIP_HAUL = -2.2F, RIP_SQUAT = 0.6F;
	/** Holding overhead: the arms up to this, spread this much, the back arched this much. */
	static final float HOLD_ARMS = -3.0F, HOLD_SPREAD = 0.15F, HOLD_ARCH = -0.2F;
	/** Throwing: the arms swung down to this, the body folding this far. */
	static final float THROW_ARMS = -0.5F, THROW_BEND = 0.6F;
	/** The ground tear: fists up to this, driven down to this, the body folding this far. */
	static final float TEAR_UP = -3.0F, TEAR_DOWN = -0.35F, TEAR_BEND = 0.9F;
	/** The ram: leaning in this far building and charged, turned shoulder-first this much, legs striding this hard. */
	static final float RAM_LEAN_FROM = 0.25F, RAM_LEAN = 0.65F, RAM_TURN = 0.35F, RAM_STRIDE = 1.5F;
	/** The stagger: reeling back this far, flung arms this wide. */
	static final float STAGGER_BACK = -0.35F, STAGGER_ARMS = 1.0F;
	// ================================================================================================================

	/** {@code kind}: a {@link WebBrute} move; {@code t}: ticks into it. */
	public record Pose(int kind, float t) {
	}

	public static final RenderStateDataKey<Pose> KEY = RenderStateDataKey.create(() -> "jaymospowers:web_brute");

	private WebBrutePose() {
	}

	/** From the avatar renderer's extractRenderState. */
	public static void extract(Entity entity, AvatarRenderState state) {
		WebBrute.Move move = entity.getAttached(WebBrute.MOVE);
		if (move == null || !(entity instanceof Player player) || !WebSymbiote.bulked(player)) {
			state.setData(KEY, null);
			return;
		}
		float partial = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
		state.setData(KEY, new Pose(move.kind(), Math.max(0.0F, entity.level().getGameTime() - move.since() + partial)));
	}

	private static float smooth(float x) {
		x = Mth.clamp(x, 0.0F, 1.0F);
		return x * x * (3.0F - 2.0F * x);
	}

	/** How long a finite move lasts (ticks), or 0 for one that holds until it ends. */
	private static float length(int kind) {
		return switch (kind) {
			case WebBrute.THROW -> WebBrute.THROW_TICKS;
			case WebBrute.TEAR -> WebBrute.TEAR_WINDUP + WebBrute.TEAR_LENGTH + WebBrute.TEAR_RECOVER;
			case WebBrute.STAGGER -> WebBrute.STAGGER_TICKS;
			default -> 0.0F;
		};
	}

	/** Poses the model if a move is under way. True if it did (nothing else poses the body then). */
	static boolean apply(HumanoidModel<?> h, AvatarRenderState state, boolean slim) {
		Pose pose = state.getData(KEY);
		if (pose == null) {
			return false;
		}
		float weight = smooth(pose.t() / BLEND_IN);
		float length = length(pose.kind());
		if (length > 0.0F) {
			weight = Math.min(weight, smooth((length - pose.t()) / BLEND_OUT));
		}
		float[] was = WebFeral.snapshot(h);
		switch (pose.kind()) {
			case WebBrute.RIP -> rip(h, state, pose.t(), slim);
			case WebBrute.HOLD -> hold(h, state, pose.t(), slim);
			case WebBrute.THROW -> hurl(h, state, pose.t(), slim);
			case WebBrute.TEAR -> tear(h, state, pose.t(), slim);
			case WebBrute.RAM_BUILD, WebBrute.RAM -> ram(h, state, pose.kind() == WebBrute.RAM ? 1.0F : Mth.clamp(pose.t() / WebBrute.RAM_BUILD_TICKS, 0.0F, 1.0F), slim);
			case WebBrute.STAGGER -> stagger(h, state, pose.t(), slim);
			default -> {
				return false;
			}
		}
		WebFeral.blend(h, was, weight);
		return true;
	}

	/** The body bent to {@code bend} with the head and arm pivots carried round with it. */
	private static void lean(HumanoidModel<?> h, float bend, boolean slim) {
		float armY = slim ? 2.5F : 2.0F;
		WebFeral.place(h.body, 0.0F, 0.0F, bend);
		WebFeral.place(h.head, 0.0F, 0.0F, bend);
		WebFeral.place(h.rightArm, -5.0F, armY, bend);
		WebFeral.place(h.leftArm, 5.0F, armY, bend);
		h.body.xRot = bend;
		h.body.yRot = 0.0F;
		h.body.zRot = 0.0F;
	}

	/** Knees bent {@code squat} and braced apart {@code apart}; the body sunk to keep the feet down. */
	private static void legs(HumanoidModel<?> h, float squat, float apart) {
		for (ModelPart leg : new ModelPart[] {h.rightLeg, h.leftLeg}) {
			float side = leg == h.rightLeg ? 1.0F : -1.0F;
			leg.xRot = -squat;
			leg.yRot = 0.0F;
			leg.zRot = side * apart;
		}
		h.root().y = 12.0F * (1.0F - Mth.cos(squat));
	}

	private static void arms(HumanoidModel<?> h, float rightX, float leftX, float spread) {
		h.rightArm.xRot = rightX;
		h.leftArm.xRot = leftX;
		h.rightArm.yRot = 0.0F;
		h.leftArm.yRot = 0.0F;
		h.rightArm.zRot = spread;
		h.leftArm.zRot = -spread;
	}

	private static void rip(HumanoidModel<?> h, AvatarRenderState state, float t, boolean slim) {
		float u = Mth.clamp(t / WebBrute.RIP_TICKS, 0.0F, 1.0F);
		float e = smooth(t / 5.0F);
		float haul = smooth((u - 0.7F) / 0.3F);
		float bend = RIP_BEND * e * (1.0F - 0.4F * haul);
		lean(h, bend, slim);
		h.head.xRot = state.xRot * Mth.DEG_TO_RAD * 0.4F - bend * 0.9F;
		float tremble = Mth.sin(state.ageInTicks * 3.0F) * 0.06F * u;
		float reach = Mth.lerp(haul, RIP_GRIP * e, RIP_HAUL) - bend * 0.5F;
		arms(h, reach + tremble, reach - tremble, 0.15F);
		legs(h, RIP_SQUAT * e, 0.2F * e);
	}

	private static void hold(HumanoidModel<?> h, AvatarRenderState state, float t, boolean slim) {
		float e = smooth(t / 6.0F);
		float arch = HOLD_ARCH * e;
		lean(h, arch, slim);
		h.head.xRot = state.xRot * Mth.DEG_TO_RAD * 0.5F + 0.2F * e;
		float strain = Mth.sin(state.ageInTicks * 1.7F) * 0.03F * e;
		float up = Mth.lerp(e, -1.0F, HOLD_ARMS);
		arms(h, up + strain, up - strain, Mth.lerp(e, 0.3F, HOLD_SPREAD));
		legs(h, 0.1F * e, 0.14F * e);
	}

	private static void hurl(HumanoidModel<?> h, AvatarRenderState state, float t, boolean slim) {
		float k = smooth(t / 3.0F);
		float bend = Mth.lerp(k, HOLD_ARCH, THROW_BEND) * (1.0F - 0.5F * smooth((t - 4.0F) / 4.0F));
		lean(h, bend, slim);
		h.head.xRot = state.xRot * Mth.DEG_TO_RAD * 0.5F;
		float swing = Mth.lerp(k, HOLD_ARMS, THROW_ARMS) - bend * 0.5F;
		arms(h, swing, swing, Mth.lerp(k, HOLD_SPREAD, 0.1F));
		legs(h, 0.3F * k, 0.15F);
	}

	private static void tear(HumanoidModel<?> h, AvatarRenderState state, float t, boolean slim) {
		float raise = smooth(t / (WebBrute.TEAR_WINDUP - 1.0F));
		float drive = smooth((t - (WebBrute.TEAR_WINDUP - 1.0F)) / 3.0F);
		float bend = Mth.lerp(drive, -0.2F * raise, TEAR_BEND);
		lean(h, bend, slim);
		h.head.xRot = state.xRot * Mth.DEG_TO_RAD * 0.4F + 0.3F * drive;
		float fists = Mth.lerp(drive, TEAR_UP * raise, TEAR_DOWN) - bend * 0.5F;
		arms(h, fists, fists, Mth.lerp(drive, -0.2F, 0.05F));
		legs(h, 0.45F * drive, 0.2F * drive);
	}

	private static void ram(HumanoidModel<?> h, AvatarRenderState state, float p, boolean slim) {
		float bend = Mth.lerp(p, RAM_LEAN_FROM, RAM_LEAN);
		lean(h, bend, slim);
		// shoulder first
		h.body.yRot = -RAM_TURN * p;
		h.head.xRot = state.xRot * Mth.DEG_TO_RAD * 0.3F - bend * 0.4F + 0.35F * p;
		float shiver = Mth.sin(state.ageInTicks * 2.3F) * 0.04F * p;
		arms(h, -0.9F * p - bend + shiver, 0.8F * p - bend - shiver, 0.3F + 0.2F * p);
		h.rightArm.yRot = -RAM_TURN * p;
		h.leftArm.yRot = -RAM_TURN * p;
		float stride = Mth.cos(state.walkAnimationPos * 0.6662F) * RAM_STRIDE * Mth.clamp(state.walkAnimationSpeed * 1.5F, 0.0F, 1.0F);
		h.rightLeg.xRot = stride;
		h.leftLeg.xRot = -stride;
		h.rightLeg.yRot = 0.0F;
		h.leftLeg.yRot = 0.0F;
		h.rightLeg.zRot = 0.08F;
		h.leftLeg.zRot = -0.08F;
		h.root().y = 0.0F;
	}

	private static void stagger(HumanoidModel<?> h, AvatarRenderState state, float t, boolean slim) {
		float u = Mth.clamp(t / WebBrute.STAGGER_TICKS, 0.0F, 1.0F);
		float fade = 1.0F - u;
		float bend = STAGGER_BACK * smooth(t / 3.0F) * fade;
		lean(h, bend, slim);
		h.body.yRot = Mth.sin(t * 0.6F) * 0.3F * fade;
		h.body.zRot = Mth.sin(t * 0.45F) * 0.15F * fade;
		h.head.xRot = state.xRot * Mth.DEG_TO_RAD * 0.3F + 0.3F * fade;
		h.head.zRot = Mth.sin(t * 0.7F) * 0.25F * fade;
		float flail = Mth.sin(t * 0.9F) * 0.3F * fade;
		arms(h, -0.6F * fade + flail, -0.6F * fade - flail, STAGGER_ARMS * fade);
		h.rightLeg.xRot = 0.3F * fade * Mth.sin(t * 0.8F);
		h.leftLeg.xRot = -0.3F * fade * Mth.sin(t * 0.8F);
		h.rightLeg.yRot = 0.0F;
		h.leftLeg.yRot = 0.0F;
		h.rightLeg.zRot = 0.25F * fade;
		h.leftLeg.zRot = -0.25F * fade;
		h.root().y = 0.0F;
	}

	// ===== the keys (G, left click) ==================================================================================

	/** G went to a move, so its release is nothing else's. */
	private static boolean keyHeld;

	/** G with something held or being torn loose: drops it (or calls the struggle off). True if the key was used (press or release). */
	public static boolean earlyKey(LocalPlayer player, boolean down) {
		if (!down) {
			if (keyHeld) {
				keyHeld = false;
				return true;
			}
			return false;
		}
		if (WebBrute.busyHolding(player)) {
			keyHeld = true;
			ClientPlayNetworking.send(new WebBrute.Press());
			return true;
		}
		return false;
	}

	/** G with nothing to grab: starts the rip, the ground tear or the tree lift if the aim calls for one. True if it did. */
	public static boolean startKey(LocalPlayer player) {
		if (!WebBrute.wants(player)) {
			return false;
		}
		keyHeld = true;
		ClientPlayNetworking.send(new WebBrute.Press());
		return true;
	}

	/** Left click while a chunk or tree is held overhead: throws it. True if the click was used. */
	public static boolean onAttack(LocalPlayer player, int clicks) {
		if (!WebBrute.busyHolding(player)) {
			return false;
		}
		if (clicks > 0 && WebBrute.holding(player)) {
			ClientPlayNetworking.send(new WebBrute.Throw());
		}
		return true;
	}
}
