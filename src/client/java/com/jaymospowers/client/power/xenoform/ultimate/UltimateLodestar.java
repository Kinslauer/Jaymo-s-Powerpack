package com.jaymospowers.client.power.xenoform.ultimate;

import com.jaymospowers.client.power.xenoform.XenoFormModels;
import com.jaymospowers.client.power.xenoform.XenoMotion;
import java.util.Map;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;

/**
 * (0.21.37, Ultimates patch 2) Polestar's Ultimate, Lodestar: Polestar's own body (the same broad humanoid build and gait, banded red
 * and grey) in heavier plates of dark lodestone: a thick chest plate with the insignia set in it, a shoulder yoke, pauldrons with a
 * spike each, bracers with an elbow spike, greaves, a brow and three spikes down the back. The horseshoe prongs rise long and curl
 * inward, their blue tips nearly meeting, and four iron shards orbit the shoulders. The seams glow magnet red, the prong tips blue
 * (the skin's glow map, drawn fullbright by the layer, and breathing slowly through {@link #submitExtras}). It walks with a heavy,
 * planted stride. The skin is {@code tools/ultimates/polestar.py}, 128x128; {@link #UV} here is the table that script prints
 * ({@code polestar.py layout}). The base limbs keep the vanilla layout in the top-left 64x64.
 * <p>
 * The change-in film zooms on, in order, {@code body}, {@code plates} and {@code right_prong+left_prong}: parts of this mesh.
 */
public final class UltimateLodestar extends XenoFormModels.UltimateModel {
	// ---- tuning ----
	/** The shards' orbit: how far out (pixels) from the body's middle, how high they ride on the shoulders (body y), the bob, and the turns a tick (radians). */
	static final float ORBIT_RADIUS = 11.5F, ORBIT_Y = 1.0F, ORBIT_BOB = 1.6F, ORBIT_RATE = 0.06F, ORBIT_RUN_RATE = 0.05F;
	/** How far the prongs sway (radians) and how fast; how far their tips lean in more as the glow breathes. */
	static final float PRONG_SWAY = 0.03F, PRONG_RATE = 0.11F;
	/** The heavy stride: how much of the vanilla leg and arm swing is kept, and how far the body rolls (radians) at a full walk. */
	static final float LEG_KEEP = 0.8F, ARM_KEEP = 0.85F, ROLL = 0.04F;
	/** The glow's breath: its pulse (radians a tick), how bright it never goes below, and how much it swells. */
	static final float GLOW_RATE = 0.09F, GLOW_FLOOR = 0.1F, GLOW_SWELL = 0.35F;
	private static final int FULL_BRIGHT = 0xF000F0;
	private static final int SHARDS = 4;

	/** The skin's patches (u, v) by box name: the table {@code tools/ultimates/polestar.py layout} prints. */
	private static final Map<String, int[]> UV = Map.ofEntries(
		uv("prong", 0, 64), uv("tip", 8, 64), uv("brow", 16, 64), uv("yoke", 40, 64), uv("chest", 82, 64), uv("back", 104, 64),
		uv("pauld", 0, 75), uv("bracer", 28, 75), uv("greave", 48, 75), uv("spike", 68, 75), uv("bspike", 72, 75), uv("shard", 76, 75));

	private static Map.Entry<String, int[]> uv(String name, int u, int v) {
		return Map.entry(name, new int[] {u, v});
	}

	private final ModelPart rightProng, leftProng, rightTip, leftTip;
	private final ModelPart[] shards = new ModelPart[SHARDS];

	public UltimateLodestar() {
		super(XenoFormModels.bake(mesh(), 128, 128), "polestar");
		// on the chest plate, which it taps to change back
		this.insigniaY = 4.5F;
		this.insigniaZ = -3.8F;
		this.insigniaSize = 0.8F;
		this.rightProng = this.head.getChild("right_prong");
		this.leftProng = this.head.getChild("left_prong");
		this.rightTip = this.rightProng.getChild("right_tip");
		this.leftTip = this.leftProng.getChild("left_tip");
		for (int i = 0; i < SHARDS; i++) {
			this.shards[i] = this.body.getChild("shard_" + i);
		}
	}

	/** A box of {@code key}'s patch of the skin, its size {@code w x h x d} (the same as {@code DIMS} in the painter). */
	private static CubeListBuilder cube(String key, boolean mirror, float x, float y, float z, int w, int h, int d, float grow) {
		int[] at = UV.get(key);
		CubeListBuilder builder = CubeListBuilder.create().texOffs(at[0], at[1]);
		if (mirror) {
			builder = builder.mirror();
		}
		return builder.addBox(x, y, z, w, h, d, new CubeDeformation(grow));
	}

	private static MeshDefinition mesh() {
		// Polestar's build: the humanoid parts broad (0.7), on the vanilla layout
		MeshDefinition mesh = HumanoidModel.createMesh(new CubeDeformation(0.7F), 0.0F);
		PartDefinition root = mesh.getRoot();
		PartDefinition head = root.getChild("head");
		head.addOrReplaceChild("hat", CubeListBuilder.create(), PartPose.ZERO);
		// a heavy brow across the forehead, and the horseshoe: each prong rises and leans out, its tip curls back in
		head.addOrReplaceChild("brow", cube("brow", false, -5.0F, -7.4F, -5.4F, 10, 2, 2, 0.0F), PartPose.ZERO);
		PartDefinition rightProng = head.addOrReplaceChild("right_prong", cube("prong", false, -1.0F, -8.0F, -1.0F, 2, 8, 2, 0.0F),
			PartPose.offsetAndRotation(-3.5F, -8.5F, 0.0F, 0.0F, 0.0F, -0.1F));
		rightProng.addOrReplaceChild("right_tip", cube("tip", false, -1.0F, -6.0F, -1.0F, 2, 6, 2, 0.0F),
			PartPose.offsetAndRotation(0.0F, -7.5F, 0.0F, 0.0F, 0.0F, 0.7F));
		PartDefinition leftProng = head.addOrReplaceChild("left_prong", cube("prong", true, -1.0F, -8.0F, -1.0F, 2, 8, 2, 0.0F),
			PartPose.offsetAndRotation(3.5F, -8.5F, 0.0F, 0.0F, 0.0F, 0.1F));
		leftProng.addOrReplaceChild("left_tip", cube("tip", true, -1.0F, -6.0F, -1.0F, 2, 6, 2, 0.0F),
			PartPose.offsetAndRotation(0.0F, -7.5F, 0.0F, 0.0F, 0.0F, -0.7F));
		// the body: a shoulder yoke, a thick chest plate with the insignia in it, a plate down the back with three spikes
		PartDefinition body = root.getChild("body");
		body.addOrReplaceChild("plates", cube("yoke", false, -7.0F, -1.5F, -3.5F, 14, 4, 7, 0.7F), PartPose.ZERO);
		body.addOrReplaceChild("chestplate", cube("chest", false, -5.0F, 0.5F, -3.6F, 10, 8, 1, 0.0F), PartPose.ZERO);
		body.addOrReplaceChild("backplate", cube("back", false, -4.5F, 1.0F, 2.8F, 9, 9, 1, 0.0F), PartPose.ZERO);
		for (int i = 0; i < 3; i++) {
			body.addOrReplaceChild("back_spike_" + i, cube("spike", false, -0.5F, -5.0F, -0.5F, 1, 5, 1, 0.0F),
				PartPose.offsetAndRotation(0.0F, 2.5F + i * 3.0F, 3.8F, -0.7F + i * 0.1F, 0.0F, 0.0F));
		}
		// four iron shards, posed in orbit each frame
		for (int i = 0; i < SHARDS; i++) {
			body.addOrReplaceChild("shard_" + i, cube("shard", false, -1.0F, -3.0F, -1.0F, 2, 6, 2, 0.0F), PartPose.ZERO);
		}
		// the arms: a pauldron with a spike on each shoulder, a bracer on each forearm with a spike at the elbow
		PartDefinition rightArm = root.getChild("right_arm");
		rightArm.addOrReplaceChild("pauldron_r", cube("pauld", false, -4.5F, -3.8F, -3.5F, 7, 4, 7, 0.2F), PartPose.ZERO);
		rightArm.addOrReplaceChild("pauldron_spike_r", cube("spike", false, -0.5F, -5.0F, -0.5F, 1, 5, 1, 0.0F),
			PartPose.offsetAndRotation(-2.5F, -3.8F, 0.0F, 0.0F, 0.0F, -0.5F));
		rightArm.addOrReplaceChild("bracer_r", cube("bracer", false, -3.5F, 5.5F, -2.5F, 5, 5, 5, 0.6F), PartPose.ZERO);
		rightArm.addOrReplaceChild("elbow_spike_r", cube("bspike", false, -0.5F, -3.0F, -0.5F, 1, 3, 1, 0.0F),
			PartPose.offsetAndRotation(-1.0F, 7.5F, 3.4F, -1.35F, 0.0F, 0.0F));
		PartDefinition leftArm = root.getChild("left_arm");
		leftArm.addOrReplaceChild("pauldron_l", cube("pauld", true, -2.5F, -3.8F, -3.5F, 7, 4, 7, 0.2F), PartPose.ZERO);
		leftArm.addOrReplaceChild("pauldron_spike_l", cube("spike", true, -0.5F, -5.0F, -0.5F, 1, 5, 1, 0.0F),
			PartPose.offsetAndRotation(2.5F, -3.8F, 0.0F, 0.0F, 0.0F, 0.5F));
		leftArm.addOrReplaceChild("bracer_l", cube("bracer", true, -1.5F, 5.5F, -2.5F, 5, 5, 5, 0.6F), PartPose.ZERO);
		leftArm.addOrReplaceChild("elbow_spike_l", cube("bspike", true, -0.5F, -3.0F, -0.5F, 1, 3, 1, 0.0F),
			PartPose.offsetAndRotation(1.0F, 7.5F, 3.4F, -1.35F, 0.0F, 0.0F));
		// the legs: a greave on each shin
		root.getChild("right_leg").addOrReplaceChild("greave_r", cube("greave", false, -2.5F, 5.5F, -2.5F, 5, 5, 5, 0.6F), PartPose.ZERO);
		root.getChild("left_leg").addOrReplaceChild("greave_l", cube("greave", true, -2.5F, 5.5F, -2.5F, 5, 5, 5, 0.6F), PartPose.ZERO);
		return mesh;
	}

	/** The seams breathe: an extra pass of the glow map over the layer's own, swelling and fading slowly. */
	@Override
	public void submitExtras(AvatarRenderState state, com.mojang.blaze3d.vertex.PoseStack poseStack, SubmitNodeCollector collector, int light) {
		float pulse = GLOW_FLOOR + GLOW_SWELL * (0.5F + 0.5F * Mth.sin(state.ageInTicks * GLOW_RATE));
		int alpha = (int)(Mth.clamp(pulse, 0.0F, 1.0F) * 255.0F);
		collector.order(1).submitModel(this, state, poseStack, RenderTypes.entityTranslucentEmissive(this.ultimateGlow), FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
			alpha << 24 | 0xFFFFFF, null, 0);
	}

	@Override
	protected void pose(AvatarRenderState state) {
		super.pose(state);
		float t = state.ageInTicks;
		XenoMotion.Read m = XenoMotion.read(state);
		float speed = Mth.clamp(state.walkAnimationSpeed, 0.0F, 1.0F);
		// a heavy, planted stride: the swing is shortened and the body rolls over each foot
		this.rightLeg.xRot *= LEG_KEEP;
		this.leftLeg.xRot *= LEG_KEEP;
		this.rightArm.xRot *= ARM_KEEP;
		this.leftArm.xRot *= ARM_KEEP;
		float roll = Mth.cos(state.walkAnimationPos * 0.6662F) * ROLL * speed;
		this.body.zRot = roll;
		this.head.zRot = -roll * 0.6F;
		// the prongs sway a little, out of step, and their tips lean in as it moves
		float lean = m.speed() * 0.08F;
		this.rightProng.zRot = -0.1F + Mth.sin(t * PRONG_RATE) * PRONG_SWAY - m.trail() * 0.05F;
		this.leftProng.zRot = 0.1F - Mth.sin(t * PRONG_RATE + 1.1F) * PRONG_SWAY - m.trail() * 0.05F;
		this.rightTip.zRot = 0.7F + lean + Mth.sin(t * PRONG_RATE * 1.3F + 0.5F) * PRONG_SWAY;
		this.leftTip.zRot = -0.7F - lean - Mth.sin(t * PRONG_RATE * 1.3F + 1.6F) * PRONG_SWAY;
		// the iron shards circle the shoulders, a little faster as it moves, each bobbing and turning on its own
		float turn = t * (ORBIT_RATE + ORBIT_RUN_RATE * m.speed());
		for (int i = 0; i < SHARDS; i++) {
			float a = turn + i * (float)Math.PI * 0.5F;
			ModelPart shard = this.shards[i];
			shard.x = Mth.cos(a) * ORBIT_RADIUS;
			shard.z = Mth.sin(a) * ORBIT_RADIUS;
			shard.y = ORBIT_Y + Mth.sin(t * 0.1F + i * 1.7F) * ORBIT_BOB;
			shard.xRot = Mth.sin(t * 0.07F + i) * 0.3F;
			shard.yRot = -a;
			shard.zRot = 0.35F + Mth.cos(t * 0.09F + i * 2.1F) * 0.2F;
		}
	}
}
