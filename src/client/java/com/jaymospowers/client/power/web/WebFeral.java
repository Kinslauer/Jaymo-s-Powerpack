package com.jaymospowers.client.power.web;

import com.jaymospowers.power.web.WebSymbiote;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.phys.Vec3;

/**
 * Living Armor moves like a beast. Standing, it hunches: bent forward at the hip, head thrust forward, the arms
 * hanging heavy and wide, legs apart (a mutant zombie's stance). Sprinting, it drops onto all fours and gallops, the
 * arms its front legs. Everything is set outright from the render state, so applying it twice changes nothing.
 */
public final class WebFeral {
	/** How far the body bends forward at the hip (radians): hunched, and running on all fours. */
	static final float IDLE_BEND = 0.45F, RUN_BEND = 1.35F;
	/** The hip, in model pixels below the neck. */
	private static final float HIP = 12.0F;

	private WebFeral() {
	}

	/** Poses the model if this is Living Armor (and nothing else, a web swing or a dive, has the body). */
	public static void applyModel(Model<?> model, Object state) {
		if (!(state instanceof AvatarRenderState avatar) || !(model instanceof HumanoidModel<?> humanoid)) {
			return;
		}
		boolean slim = avatar.skin != null && avatar.skin.model() == PlayerModelType.SLIM;
		Float charge = avatar.getData(WebSymbioteClient.CHARGE);
		WebSymbioteClient.Tendrils tendrils = avatar.getData(WebSymbioteClient.TENDRILS);
		// Living Armor on the run keeps galloping while it charges a leap; standing (or the plain suit), it coils
		boolean galloping = tendrils != null && tendrils.run() > 0.3F && avatar.walkAnimationSpeed > 0.3F;
		if (charge != null && !galloping) {
			coil(humanoid, avatar, charge, slim);
			return;
		}
		if (tendrils == null || avatar.getData(WebArms.KEY) != null || avatar.isFallFlying || avatar.swimAmount > 0.0F
			|| avatar.isPassenger || avatar.isCrouching && charge == null) {
			return;
		}
		// eased between standing, walking and the gallop (see WebSymbioteClient.smoothRun)
		float run = tendrils.run() * Mth.clamp(0.4F + avatar.walkAnimationSpeed, 0.0F, 1.0F);
		pose(humanoid, avatar, run, slim);
	}

	/**
	 * Coiling for the suit's charged leap: sinking into a deep crouch, bent low over the knees, arms swept back, head
	 * up at where it's going. {@code c}: how far into the charge (0-1).
	 */
	private static void coil(HumanoidModel<?> h, AvatarRenderState state, float c, boolean slim) {
		float e = c * (2.0F - c);
		float bend = 0.75F * e;
		float squat = 0.85F * e;
		float armY = slim ? 2.5F : 2.0F;
		place(h.body, 0.0F, 0.0F, bend);
		place(h.head, 0.0F, 0.0F, bend);
		place(h.rightArm, -5.0F, armY, bend);
		place(h.leftArm, 5.0F, armY, bend);
		h.body.xRot = bend;
		h.head.xRot = state.xRot * Mth.DEG_TO_RAD * 0.4F - bend * 0.9F;
		// arms swept back behind, ready to swing through; a tremble as it builds
		float shiver = Mth.sin(state.ageInTicks * 2.3F) * 0.03F * e;
		for (ModelPart arm : new ModelPart[] {h.rightArm, h.leftArm}) {
			float side = arm == h.rightArm ? 1.0F : -1.0F;
			arm.xRot = 0.9F * e - bend + shiver;
			arm.yRot = 0.0F;
			arm.zRot = side * (0.15F + 0.2F * e);
		}
		// knees forward: the legs swing forward from the hip and the whole body sinks so the feet stay down
		for (ModelPart leg : new ModelPart[] {h.rightLeg, h.leftLeg}) {
			float side = leg == h.rightLeg ? 1.0F : -1.0F;
			leg.xRot = -squat;
			leg.zRot = side * 0.12F * e;
		}
		float sink = 12.0F * (1.0F - Mth.cos(squat));
		ModelPart root = h.root();
		root.y = sink;
	}

	private static void pose(HumanoidModel<?> h, AvatarRenderState state, float run, boolean slim) {
		float bend = Mth.lerp(run, IDLE_BEND, RUN_BEND);
		float pos = state.walkAnimationPos;
		float speed = Math.min(1.0F, state.walkAnimationSpeed);
		float breathe = Mth.sin(state.ageInTicks * 0.08F);
		float armY = slim ? 2.5F : 2.0F;
		// the upper body turns about the hip
		place(h.body, 0.0F, 0.0F, bend);
		place(h.head, 0.0F, 0.0F, bend);
		place(h.rightArm, -5.0F, armY, bend);
		place(h.leftArm, 5.0F, armY, bend);
		h.body.xRot = bend + breathe * 0.03F;
		h.body.yRot = 0.0F;
		// the head stays up, looking ahead (and where the player looks, a little)
		h.head.xRot = state.xRot * Mth.DEG_TO_RAD * 0.5F - bend * 0.85F;

		// hunched: arms hang heavy, forward and out, swinging as it walks
		float walkSwing = Mth.cos(pos * 0.6662F) * speed * 0.6F;
		float idleRight = 0.35F + walkSwing + breathe * 0.04F;
		float idleLeft = 0.35F - walkSwing + breathe * 0.04F;
		// galloping: both arms reach and pull together, the legs a beat behind
		float gallop = pos * 0.45F;
		float runArms = Mth.sin(gallop) * 0.95F * speed;
		float runLegs = Mth.sin(gallop + Mth.PI) * 0.95F * speed;
		boolean swinging = state.swingAnimation > 0.0F;
		ModelPart attacking = state.mainArm == net.minecraft.world.entity.HumanoidArm.LEFT ? h.leftArm : h.rightArm;
		for (ModelPart arm : new ModelPart[] {h.rightArm, h.leftArm}) {
			float side = arm == h.rightArm ? 1.0F : -1.0F;
			if (swinging && arm == attacking) {
				// a punch keeps its own swing, from the bent shoulder
				arm.xRot -= bend * 0.6F;
				continue;
			}
			float world = Mth.lerp(run, arm == h.rightArm ? idleRight : idleLeft, runArms);
			arm.xRot = world - bend;
			arm.yRot = 0.0F;
			arm.zRot = side * Mth.lerp(run, 0.45F + breathe * 0.03F, 0.12F);
		}
		// legs: planted wide, bent a little; running, they drive together
		for (ModelPart leg : new ModelPart[] {h.rightLeg, h.leftLeg}) {
			float side = leg == h.rightLeg ? 1.0F : -1.0F;
			if (run > 0.0F) {
				leg.xRot = Mth.lerp(run, leg.xRot, runLegs + 0.2F);
			}
			leg.zRot = side * Mth.lerp(run, 0.14F, 0.05F);
		}
	}

	/** Puts a part's pivot where it ends up when everything above the hip bends forward by {@code bend}. */
	private static void place(ModelPart part, float restX, float restY, float bend) {
		float vy = restY - HIP;
		part.x = restX;
		part.y = HIP + vy * Mth.cos(bend);
		part.z = vy * Mth.sin(bend);
	}

	/** How far this player's Living Armor is bent right now (0 if it isn't up), for things drawn outside the model. */
	public static float bend(Player player, float partialTick) {
		if (!WebSymbiote.armored(player) || player.isCrouching() || player.isFallFlying() || player.isSwimming() || player.isPassenger()) {
			return 0.0F;
		}
		float run = WebSymbioteClient.run(player) * Mth.clamp(0.4F + player.walkAnimation.speed(partialTick), 0.0F, 1.0F);
		return Mth.lerp(run, IDLE_BEND, RUN_BEND);
	}

	/**
	 * Where a point on the body (model pixels: x to the wearer's left, y down from the neck, z back) is in the world,
	 * with the body bent {@code bend} at the hip.
	 */
	public static Vec3 bodyPoint(Player player, float partialTick, float bend, float x, float y, float z) {
		float vy = y - HIP;
		float my = HIP + vy * Mth.cos(bend) - z * Mth.sin(bend);
		float mz = vy * Mth.sin(bend) + z * Mth.cos(bend);
		float yaw = Mth.lerp(partialTick, player.yBodyRotO, player.yBodyRot) * Mth.DEG_TO_RAD;
		Vec3 forward = new Vec3(-Mth.sin(yaw), 0.0, Mth.cos(yaw));
		Vec3 left = new Vec3(Mth.cos(yaw), 0.0, Mth.sin(yaw));
		double scale = player.getScale() / 16.0;
		return player.getPosition(partialTick).add(0.0, (24.0 - my) * scale, 0.0).add(forward.scale(-mz * scale)).add(left.scale(x * scale));
	}
}
