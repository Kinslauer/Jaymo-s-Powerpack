package com.jaymospowers.client.power.chrono;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.util.Mth;

/**
 * The android mech: our own walker of the future, drawn by {@link FutureMechRenderer} for the war mech and the test unit.
 * A plated chest with a lit cockpit and core, a sensor head, long arms ending in cannons (or a claw), stocky legs that
 * bend at the knee. One frame carries four builds (Warden, Bulwark, Strider, Reclaimer); each shows only its own optional
 * pieces. tools/gen_android_mech.py writes the part block and the build table below and paints the skins, so change the
 * shape there. y is down, the feet are at y 24, the mech faces -z.
 */
public class AndroidMechModel extends EntityModel<AndroidMechModel.State> {
	static final float DEG = Mth.DEG_TO_RAD;
	/** The cannons' rise when it fires: level with where it looks. */
	static final float AIM = -1.45F;
	/** The stride: how far a leg swings, how far the knee bends, how much the arms and body sway. */
	static final float STRIDE = 0.7F, KNEE = 0.7F, ARM_SWAY = 0.3F, BODY_SWAY = 0.05F;
	/** Ticks of the double-fisted smash, as the iron golem's. */
	static final float SMASH_TICKS = 10.0F;

	public static class State extends LivingEntityRenderState {
		/** 0 Warden, 1 Bulwark, 2 Strider, 3 Reclaimer. */
		public int build;
		public boolean aiming;
		public float attackTicks;
	}

	/** Which builds (a bit each) show an optional part; parts not listed are on every build. */
	private static final Map<String, Integer> BUILDS = Map.ofEntries(
		// builds:start
			Map.entry("exhaust_r", 11),
			Map.entry("exhaust_l", 11),
			Map.entry("jets", 4),
			Map.entry("shell_pod", 2),
			Map.entry("antenna", 4),
			Map.entry("dome", 8),
			Map.entry("cannon_l", 5),
			Map.entry("claw_a", 10),
			Map.entry("claw_b", 10),
			Map.entry("claw_c", 10),
			Map.entry("shield", 2)
		// builds:end
	);

	private final ModelPart torso, head, rightArm, leftArm, rightLeg, leftLeg, shinR, shinL;
	private final List<ModelPart> optional = new ArrayList<>();
	private final List<Integer> masks = new ArrayList<>();
	private final int leftCannon;

	public AndroidMechModel(ModelPart root) {
		super(root);
		this.torso = root.getChild("torso");
		this.head = this.torso.getChild("head");
		this.rightArm = root.getChild("right_arm");
		this.leftArm = root.getChild("left_arm");
		this.rightLeg = root.getChild("right_leg");
		this.leftLeg = root.getChild("left_leg");
		this.shinR = this.rightLeg.getChild("shin_r");
		this.shinL = this.leftLeg.getChild("shin_l");
		this.leftCannon = BUILDS.get("cannon_l");
		find(root);
	}

	private void find(ModelPart part) {
		for (Map.Entry<String, Integer> entry : BUILDS.entrySet()) {
			if (part.hasChild(entry.getKey())) {
				this.optional.add(part.getChild(entry.getKey()));
				this.masks.add(entry.getValue());
			}
		}
		for (String name : CHILDREN) {
			if (part.hasChild(name)) {
				find(part.getChild(name));
			}
		}
	}

	/** The parts that carry optional pieces, searched for them. */
	private static final String[] CHILDREN = {"torso", "head", "right_arm", "forearm_r", "left_arm", "forearm_l", "right_leg", "left_leg"};

	public static AndroidMechModel create() {
		return new AndroidMechModel(layer().bakeRoot());
	}

	static PartDefinition part(PartDefinition parent, String name, int u, int v, float x, float y, float z, int w, int h, int d,
		float px, float py, float pz) {
		return parent.addOrReplaceChild(name, CubeListBuilder.create().texOffs(u, v).addBox(x, y, z, w, h, d), PartPose.offset(px, py, pz));
	}

	static LayerDefinition layer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition r = mesh.getRoot();
		// parts:start
		part(r, "pelvis", 32, 68, -5F, -2F, -3F, 10, 5, 6, 0F, 2F, 0F);
		PartDefinition torso = part(r, "torso", 0, 41, -4F, -8F, -3F, 8, 8, 6, 0F, 1F, 0F);
		part(torso, "chest", 28, 0, -8F, -20F, -5F, 16, 12, 10, 0F, 0F, 0F);
		part(torso, "cockpit", 46, 80, -5F, -18F, -6F, 10, 5, 1, 0F, 0F, 0F);
		part(torso, "core", 116, 80, -2F, -12F, -6F, 4, 3, 1, 0F, 0F, 0F);
		part(torso, "backpack", 92, 25, -6F, -19F, 5F, 12, 10, 4, 0F, 0F, 0F);
		part(torso, "exhaust_r", 96, 68, -6F, -25F, 6F, 3, 7, 3, 0F, 0F, 0F);
		part(torso, "exhaust_l", 108, 68, 3F, -25F, 6F, 3, 7, 3, 0F, 0F, 0F);
		part(torso, "jets", 0, 80, -5F, -16F, 9F, 10, 6, 2, 0F, 0F, 0F);
		part(torso, "shell_pod", 28, 41, -13F, -26F, -2F, 6, 6, 8, 0F, 0F, 0F);
		PartDefinition head = part(torso, "head", 60, 55, -3F, -5F, -4F, 6, 5, 7, 0F, -20F, -1F);
		part(head, "antenna", 42, 80, 2F, -11F, 1F, 1, 6, 1, 0F, 0F, 0F);
		part(head, "dome", 24, 80, -2F, -7F, -3F, 4, 2, 5, 0F, 0F, 0F);
		PartDefinition right_arm = part(r, "right_arm", 86, 55, -4F, 2F, -2F, 4, 8, 4, -10F, -14F, 0F);
		part(right_arm, "pauldron_r", 80, 0, -5F, -3F, -5F, 8, 6, 10, 0F, 0F, 0F);
		PartDefinition forearm_r = part(right_arm, "forearm_r", 36, 25, -3.5F, 0F, -3.5F, 7, 9, 7, -2F, 10F, 0F);
		part(forearm_r, "cannon_r", 64, 68, -2F, 9F, -2F, 4, 6, 4, 0F, 0F, 0F);
		PartDefinition left_arm = part(r, "left_arm", 102, 55, 0F, 2F, -2F, 4, 8, 4, 10F, -14F, 0F);
		part(left_arm, "pauldron_l", 0, 25, -3F, -3F, -5F, 8, 6, 10, 0F, 0F, 0F);
		PartDefinition forearm_l = part(left_arm, "forearm_l", 64, 25, -3.5F, 0F, -3.5F, 7, 9, 7, 2F, 10F, 0F);
		part(forearm_l, "cannon_l", 80, 68, -2F, 9F, -2F, 4, 6, 4, 0F, 0F, 0F);
		part(forearm_l, "claw_a", 92, 80, -3F, 9F, -3F, 2, 4, 2, 0F, 0F, 0F);
		part(forearm_l, "claw_b", 100, 80, 1F, 9F, -3F, 2, 4, 2, 0F, 0F, 0F);
		part(forearm_l, "claw_c", 108, 80, -1F, 9F, 1F, 2, 4, 2, 0F, 0F, 0F);
		part(forearm_l, "shield", 0, 0, 3.5F, -2F, -6F, 2, 13, 12, 0F, 0F, 0F);
		PartDefinition right_leg = part(r, "right_leg", 56, 41, -2.5F, 0F, -3F, 5, 8, 6, -4.5F, 5F, 0F);
		part(right_leg, "knee_r", 68, 80, -2F, 6F, -4.5F, 4, 4, 2, 0F, 0F, 0F);
		PartDefinition shin_r = part(right_leg, "shin_r", 0, 68, -2F, 0F, -2F, 4, 8, 4, 0F, 8F, 0F);
		part(shin_r, "foot_r", 0, 55, -3F, 7F, -6F, 6, 4, 9, 0F, 0F, 0F);
		PartDefinition left_leg = part(r, "left_leg", 78, 41, -2.5F, 0F, -3F, 5, 8, 6, 4.5F, 5F, 0F);
		part(left_leg, "knee_l", 80, 80, -2F, 6F, -4.5F, 4, 4, 2, 0F, 0F, 0F);
		PartDefinition shin_l = part(left_leg, "shin_l", 16, 68, -2F, 0F, -2F, 4, 8, 4, 0F, 8F, 0F);
		part(shin_l, "foot_l", 30, 55, -3F, 7F, -6F, 6, 4, 9, 0F, 0F, 0F);
		// parts:end
		return LayerDefinition.create(mesh, 128, 128);
	}

	@Override
	public void setupAnim(State state) {
		super.setupAnim(state);
		int bit = 1 << state.build;
		for (int i = 0; i < this.optional.size(); i++) {
			this.optional.get(i).visible = (this.masks.get(i) & bit) != 0;
		}
		this.head.yRot = state.yRot * DEG;
		this.head.xRot = state.xRot * DEG;

		float speed = Math.min(state.walkAnimationSpeed, 1.0F);
		float phase = state.walkAnimationPos * 0.5F;
		float swing = Mth.cos(phase);
		this.rightLeg.xRot = -swing * STRIDE * speed;
		this.leftLeg.xRot = swing * STRIDE * speed;
		// the knee folds as the foot comes forward off the ground
		this.shinR.xRot = Math.max(0.0F, Mth.sin(phase)) * KNEE * speed;
		this.shinL.xRot = Math.max(0.0F, -Mth.sin(phase)) * KNEE * speed;
		this.rightArm.xRot = swing * ARM_SWAY * speed;
		this.leftArm.xRot = -swing * ARM_SWAY * speed;
		this.torso.zRot = Mth.sin(phase) * BODY_SWAY * speed;
		// idle: the plates rise and settle as it breathes coolant
		this.torso.y += Mth.sin(state.ageInTicks * 0.08F) * 0.3F;

		if (state.attackTicks > 0.0F) {
			// both fists up and down, as the golem's
			float smash = -2.0F + 1.5F * triangle(state.attackTicks, SMASH_TICKS);
			this.rightArm.xRot = smash;
			this.leftArm.xRot = smash;
		} else if (state.aiming) {
			float aim = AIM + state.xRot * DEG;
			this.rightArm.xRot = aim;
			this.rightArm.yRot = state.yRot * DEG * 0.5F;
			if ((this.leftCannon & bit) != 0) {
				this.leftArm.xRot = aim;
				this.leftArm.yRot = state.yRot * DEG * 0.5F;
			}
		}
	}

	private static float triangle(float t, float period) {
		return (Math.abs(t % period - period * 0.5F) - period * 0.25F) / (period * 0.25F);
	}
}
