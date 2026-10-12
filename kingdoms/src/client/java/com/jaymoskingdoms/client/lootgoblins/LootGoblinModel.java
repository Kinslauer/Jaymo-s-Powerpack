package com.jaymoskingdoms.client.lootgoblins;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

/**
 * The loot goblin's model and its two animations, from Loot Goblins' GeckoLib model ({@code lootgoblin.geo.json}) and
 * animations ({@code lootgoblin.animation.json}), LGPL-3.0, carried over to a vanilla model: Bedrock y flipped
 * ({@code 24 - y}), x and z kept, bone angles as they are; a cube turned on its own pivot gets a part of its own. The
 * keyframes are played linearly (GeckoLib eased some), {@link #RUN} while it moves and {@link #IDLE} (crouched over its
 * open chest, counting its loot) while it stands, blended by how fast it walks.
 */
final class LootGoblinModel extends EntityModel<LootGoblinRenderer.State> {
	private static final String[] PARTS = {"torso", "head", "nose", "jaw", "earInner", "earOuter", "earInner2", "earOuter2",
		"shoulder", "forearm", "shoulder2", "forearm2", "thigh", "shin", "thigh2", "shin2", "chest", "lid"};
	/** The two animations: their lengths (seconds), then per part its rotation keys {t, x, y, z...} (degrees) and position keys. */
	static final float RUN_LENGTH = 0.48F, IDLE_LENGTH = 1.12F;
	private final java.util.Map<String, ModelPart> parts = new java.util.HashMap<>();

	LootGoblinModel(ModelPart root) {
		super(root);
		for (String name : PARTS) {
			this.parts.put(name, find(root, name));
		}
	}

	private static ModelPart find(ModelPart part, String name) {
		for (ModelPart parent : part.getAllParts()) {
			if (parent.hasChild(name)) {
				return parent.getChild(name);
			}
		}
		throw new IllegalStateException("no part " + name);
	}

	static LayerDefinition createBodyLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		PartDefinition torso = root.addOrReplaceChild("torso", CubeListBuilder.create().texOffs(0, 14).addBox(-2.0F, -1.0F, -1.0F, 4.0F, 3.0F, 2.0F), PartPose.offset(0.0F, 16.0F, 0.0F));
		PartDefinition head = torso.addOrReplaceChild("head", CubeListBuilder.create(), PartPose.offset(0.0F, -1.0F, 1.0F));
		PartDefinition nose = head.addOrReplaceChild("nose", CubeListBuilder.create().texOffs(20, 8).addBox(-0.5F, 0.0F, -1.0F, 1.0F, 2.0F, 1.0F), PartPose.offset(0.0F, -3.0F, -3.0F));
		PartDefinition earring4 = nose.addOrReplaceChild("earring4", CubeListBuilder.create(), PartPose.offset(0.50116F, 1.5F, -1.0F));
		earring4.addOrReplaceChild("earring4_0", CubeListBuilder.create().texOffs(0, 14).addBox(-0.53407F, -0.5F, 0.45F, 1.0F, 1.0F, 0.1F), PartPose.offsetAndRotation(0.03291F, 0.5F, 0.0F, 0.0F, 0.0F, -0.2618F));
		head.addOrReplaceChild("core", CubeListBuilder.create().texOffs(18, 15).addBox(2.0F, -0.5F, 0.0F, 1.0F, 1.0F, 0.0F).texOffs(0, 7).addBox(-0.75F, -1.0F, 0.01F, 4.0F, 3.0F, 4.0F).texOffs(18, 16).addBox(-0.5F, -0.5F, 0.0F, 1.0F, 1.0F, 0.0F), PartPose.offset(-1.25F, -3.0F, -3.01F));
		PartDefinition jaw = head.addOrReplaceChild("jaw", CubeListBuilder.create().texOffs(13, 11).addBox(-2.0F, 0.0F, -3.0F, 4.0F, 1.0F, 3.0F).texOffs(9, 19).addBox(-2.0F, -1.0F, -3.01F, 1.0F, 1.0F, 0.0F).texOffs(7, 19).addBox(1.0F, -1.0F, -3.01F, 1.0F, 1.0F, 0.0F), PartPose.offsetAndRotation(0.0F, -1.0F, 0.0F, 0.08727F, 0.0F, 0.0F));
		jaw.addOrReplaceChild("jaw_2", CubeListBuilder.create().texOffs(12, 16).addBox(2.0F, -1.0F, -2.01F, 1.0F, 1.0F, 0.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 1.5708F, 0.0F));
		jaw.addOrReplaceChild("jaw_3", CubeListBuilder.create().texOffs(12, 15).addBox(-3.0F, -1.0F, -2.01F, 1.0F, 1.0F, 0.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, -1.5708F, 0.0F));
		PartDefinition earR = head.addOrReplaceChild("earR", CubeListBuilder.create(), PartPose.offset(-2.0F, -3.0F, -1.0F));
		PartDefinition earInner = earR.addOrReplaceChild("earInner", CubeListBuilder.create().texOffs(16, 8).addBox(-1.0F, -2.0F, 0.0F, 2.0F, 3.0F, 0.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, -0.7854F));
		PartDefinition earring2 = earInner.addOrReplaceChild("earring2", CubeListBuilder.create(), PartPose.offset(-0.5F, -1.0F, 0.0F));
		earring2.addOrReplaceChild("earring2_0", CubeListBuilder.create().texOffs(0, 15).addBox(-0.01704F, 1.12941F, -0.05F, 1.0F, 1.0F, 0.1F), PartPose.offsetAndRotation(-0.98296F, -1.62941F, 0.0F, 0.0F, 0.0F, 0.2618F));
		PartDefinition earOuter = earInner.addOrReplaceChild("earOuter", CubeListBuilder.create().texOffs(19, 23).addBox(-1.0F, -2.87868F, 0.0F, 2.0F, 3.0F, 0.0F), PartPose.offset(0.0F, -2.0F, 0.0F));
		PartDefinition earring = earOuter.addOrReplaceChild("earring", CubeListBuilder.create(), PartPose.offset(-0.5F, -0.36177F, 0.0F));
		earring.addOrReplaceChild("earring_0", CubeListBuilder.create().texOffs(10, 15).addBox(1.96593F, -0.5F, -0.05F, 1.0F, 1.0F, 0.1F), PartPose.offsetAndRotation(-2.96593F, -0.63823F, 0.0F, 0.0F, 0.0F, 0.2618F));
		PartDefinition earL = head.addOrReplaceChild("earL", CubeListBuilder.create(), PartPose.offset(2.0F, -3.0F, -1.0F));
		PartDefinition earInner2 = earL.addOrReplaceChild("earInner2", CubeListBuilder.create().texOffs(16, 0).addBox(-1.0F, -2.0F, 0.0F, 2.0F, 3.0F, 0.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.7854F));
		earInner2.addOrReplaceChild("earOuter2", CubeListBuilder.create().texOffs(12, 8).addBox(0.0F, -2.87868F, 0.0F, 2.0F, 3.0F, 0.0F), PartPose.offset(-1.0F, -2.0F, 0.0F));
		earInner2.addOrReplaceChild("earring3", CubeListBuilder.create().texOffs(10, 14).addBox(-0.24984F, -0.43243F, -0.05F, 1.0F, 1.0F, 0.1F), PartPose.offsetAndRotation(1.0F, -1.0F, 0.0F, 0.0F, 0.0F, -0.5236F));
		PartDefinition etc = head.addOrReplaceChild("etc", CubeListBuilder.create(), PartPose.offset(0.0F, 0.0F, 0.0F));
		PartDefinition flatcross = etc.addOrReplaceChild("flatcross", CubeListBuilder.create().texOffs(48, -4).addBox(0.0F, -8.0F, -3.0F, 0.0F, 4.0F, 4.0F), PartPose.offset(0.0F, 0.0F, 0.0F));
		flatcross.addOrReplaceChild("flatcross_0", CubeListBuilder.create().texOffs(56, -4).addBox(-1.0F, -8.0F, -2.0F, 0.0F, 4.0F, 4.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, -1.5708F, 0.0F));
		etc.addOrReplaceChild("hat", CubeListBuilder.create().texOffs(40, 25).addBox(-3.0F, -5.0F, -4.0F, 6.0F, 1.0F, 6.0F).texOffs(48, 19).addBox(-2.0F, -7.0F, -3.0F, 4.0F, 2.0F, 4.0F).texOffs(44, 19).addBox(-1.0F, -9.0F, -2.0F, 2.0F, 2.0F, 2.0F), PartPose.offset(0.0F, 0.0F, 0.0F));
		PartDefinition armR = torso.addOrReplaceChild("armR", CubeListBuilder.create(), PartPose.offset(-3.25F, -0.25F, 0.0F));
		PartDefinition shoulder = armR.addOrReplaceChild("shoulder", CubeListBuilder.create().texOffs(0, 23).addBox(-1.25F, -1.25F, -1.25F, 2.5F, 2.5F, 2.5F).texOffs(20, 15).addBox(-0.75F, 1.25F, -0.75F, 1.5F, 2.0F, 1.5F), PartPose.offset(0.0F, 0.0F, 0.0F));
		shoulder.addOrReplaceChild("forearm", CubeListBuilder.create().texOffs(15, 23).addBox(-1.34F, -1.0F, -0.74F, 1.48F, 3.5F, 1.48F), PartPose.offset(0.6F, 3.25F, 0.0F));
		PartDefinition armL = torso.addOrReplaceChild("armL", CubeListBuilder.create(), PartPose.offset(3.75F, -0.25F, 0.0F));
		PartDefinition shoulder2 = armL.addOrReplaceChild("shoulder2", CubeListBuilder.create().texOffs(8, 20).addBox(-1.75F, -1.25F, -1.25F, 2.5F, 2.5F, 2.5F).texOffs(20, 0).addBox(-1.25F, 1.25F, -0.75F, 1.5F, 2.0F, 1.5F), PartPose.offset(0.0F, 0.0F, 0.0F));
		shoulder2.addOrReplaceChild("forearm2", CubeListBuilder.create().texOffs(12, 0).addBox(0.16F, -1.0F, -0.74F, 1.48F, 3.5F, 1.48F), PartPose.offset(-1.4F, 3.25F, 0.0F));
		PartDefinition legs = torso.addOrReplaceChild("legs", CubeListBuilder.create(), PartPose.offset(0.0F, 2.0F, 0.0F));
		PartDefinition thigh = legs.addOrReplaceChild("thigh", CubeListBuilder.create().texOffs(18, 18).addBox(-2.0F, 0.0F, -1.0F, 2.0F, 3.0F, 2.0F), PartPose.offset(0.0F, 0.0F, 0.0F));
		thigh.addOrReplaceChild("shin", CubeListBuilder.create().texOffs(0, 7).addBox(-0.75F, 0.0F, -0.75F, 1.5F, 3.0F, 1.5F), PartPose.offset(-1.0F, 3.0F, 0.0F));
		PartDefinition thigh2 = legs.addOrReplaceChild("thigh2", CubeListBuilder.create().texOffs(12, 15).addBox(-2.0F, 0.0F, -1.0F, 2.0F, 3.0F, 2.0F), PartPose.offset(2.0F, 0.0F, 0.0F));
		thigh2.addOrReplaceChild("shin2", CubeListBuilder.create().texOffs(0, 0).addBox(-0.75F, 0.0F, -0.75F, 1.5F, 3.0F, 1.5F), PartPose.offset(-1.0F, 3.0F, 0.0F));
		PartDefinition chest = torso.addOrReplaceChild("chest", CubeListBuilder.create().texOffs(0, 0).addBox(-2.0F, -1.0F, 0.0F, 4.0F, 3.0F, 4.0F), PartPose.offset(0.0F, 1.0F, 1.0F));
		chest.addOrReplaceChild("chest_1", CubeListBuilder.create().texOffs(0, 19).addBox(-1.5F, -1.69508F, -1.53421F, 3.0F, 3.0F, 1.5F), PartPose.offsetAndRotation(0.0F, -1.30492F, 2.53421F, 1.26536F, 0.0F, 0.0F));
		chest.addOrReplaceChild("lid", CubeListBuilder.create().texOffs(12, 3).addBox(-2.25F, -1.0F, 0.0F, 4.5F, 1.0F, 4.25F).texOffs(0, 31).addBox(-0.5F, -0.5F, 4.25F, 1.0F, 1.5F, 0.5F), PartPose.offset(0.0F, -1.0F, 0.0F));
		return LayerDefinition.create(mesh, 64, 32);
	}

	// ---- the animations (from lootgoblin.animation.json) -------------------------------------------------------------

	/** Running: leaning in, legs and arms pumping, ears flapping, the chest lid rattling. */
	private static final Object[][] RUN = {
		{"head", new float[] {0, -22.5F, 0, 0}},
		{"jaw", new float[] {0, 0, 0, 0, 0.12F, 10, 0, 0, 0.24F, 0, 0, 0, 0.36F, 10, 0, 0, 0.48F, 0, 0, 0}},
		{"torso", new float[] {0, 30, 0, 0}, new float[] {0, 0, -1, 0, 0.12F, 0, 0, 0, 0.24F, 0, -1, 0, 0.36F, 0, 0, 0, 0.48F, 0, -1, 0}},
		{"shoulder", new float[] {0, 15, 0, 0, 0.24F, -62.5F, 0, 0, 0.48F, 15, 0, 0}},
		{"forearm", new float[] {0, -30, 0, 0, 0.24F, -60, 0, 0, 0.48F, -30, 0, 0}},
		{"shoulder2", new float[] {0, -62.5F, 0, 0, 0.24F, 15, 0, 0, 0.48F, -62.5F, 0, 0}},
		{"forearm2", new float[] {0, -60, 0, 0, 0.24F, -30, 0, 0, 0.48F, -60, 0, 0}},
		{"earInner", new float[] {0, -45, 0, 0, 0.12F, -55, 0, 0, 0.24F, -45, 0, 0, 0.36F, -55, 0, 0, 0.48F, -45, 0, 0}},
		{"earOuter", new float[] {0, -40, 0, 0, 0.12F, -50, 0, 0, 0.24F, -40, 0, 0, 0.36F, -50, 0, 0, 0.48F, -40, 0, 0}},
		{"earInner2", new float[] {0, -37.5F, 0, 0, 0.12F, -47.5F, 0, 0, 0.24F, -37.5F, 0, 0, 0.36F, -47.5F, 0, 0, 0.48F, -37.5F, 0, 0}},
		{"earOuter2", new float[] {0, -40, 0, 0, 0.12F, -52.5F, 0, 0, 0.24F, -40, 0, 0, 0.36F, -52.5F, 0, 0, 0.48F, -40, 0, 0}},
		{"shin", new float[] {0, 35, 0, 0, 0.12F, 35, 0, 0, 0.24F, 50, 0, 0, 0.36F, 70, 0, 0, 0.48F, 35, 0, 0}},
		{"thigh", new float[] {0, -77.5F, 0, 0, 0.24F, 7.5F, 0, 0, 0.48F, -77.5F, 0, 0}},
		{"thigh2", new float[] {0, 7.5F, 0, 0, 0.24F, -77.5F, 0, 0, 0.48F, 7.5F, 0, 0}},
		{"shin2", new float[] {0, 35, 0, 0, 0.12F, 70, 0, 0, 0.24F, 35, 0, 0, 0.36F, 35, 0, 0, 0.48F, 35, 0, 0}},
		{"lid", new float[] {0, 17.5F, 0, 0, 0.12F, 7.5F, 0, 0, 0.24F, 17.5F, 0, 0, 0.36F, 7.5F, 0, 0, 0.48F, 17.5F, 0, 0}},
		{"nose", new float[] {0, -10, 0, -5, 0.24F, 0, 0, 5, 0.48F, -10, 0, -5}}};
	/** Standing: crouched, the chest set down in front of it and opened, nodding over the loot. */
	private static final Object[][] IDLE = {
		{"torso", new float[] {0, 20, 0, 0}, new float[] {0, 0, -2, 0}},
		{"chest", new float[] {0, -20, 0, 0}, new float[] {0, 0, -1, -8}},
		{"lid", new float[] {0, 155, 0, 0, 0.28F, 162.5F, 0, 0, 0.52F, 155, 0, 0, 0.88F, 162.5F, 0, 0, 1.12F, 155, 0, 0}},
		{"head", new float[] {0, 20, 0, 0, 0.52F, 35, 0, 0, 1.12F, 20, 0, 0}},
		{"nose", new float[] {0, -22.5F, 0, 0, 0.52F, -37.5F, 0, 0, 1.12F, -22.5F, 0, 0}},
		{"jaw", new float[] {0, 7.5F, 0, 0, 0.52F, 0, 0, 0, 1.12F, 7.5F, 0, 0}},
		{"earInner", new float[] {0, -50, 0, 0, 0.52F, -40, 0, 0, 1.12F, -50, 0, 0}},
		{"earOuter", new float[] {0, 14.69F, 0, 0, 0.52F, 39.69F, 0, 0, 1.12F, 14.69F, 0, 0}},
		{"earInner2", new float[] {0, -50, 0, 0, 0.52F, -40, 0, 0, 1.12F, -50, 0, 0}},
		{"earOuter2", new float[] {0, 14.69F, 0, 0, 0.52F, 29.69F, 0, 0, 1.12F, 14.69F, 0, 0}},
		{"shoulder", new float[] {0, -64.47F, -11.31F, -5.35F, 0.24F, -65, 0, 0, 0.52F, -64.47F, -11.31F, -5.35F, 0.84F, -65, 0, 0, 1.12F, -64.47F, -11.31F, -5.35F}},
		{"forearm", new float[] {0, 0, 0, -32.5F, 0.24F, 0, 0, -17.5F, 0.52F, 0, 0, -32.5F, 0.84F, 0, 0, -17.5F, 1.12F, 0, 0, -32.5F}},
		{"shoulder2", new float[] {0, -65, 0, 0, 0.28F, -64.47F, 11.31F, 5.35F, 0.52F, -65, 0, 0, 0.88F, -64.47F, 11.31F, 5.35F, 1.12F, -65, 0, 0}},
		{"forearm2", new float[] {0, 0, 0, 17.5F, 0.28F, 0, 0, 32.5F, 0.52F, 0, 0, 17.5F, 0.88F, 0, 0, 32.5F, 1.12F, 0, 0, 17.5F}},
		{"thigh", new float[] {0, -17.5F, 0, 0}},
		{"shin", new float[] {0, 75, 0, 0, 0.52F, 90, 0, 0, 1.12F, 75, 0, 0}},
		{"thigh2", new float[] {0, -30, 0, 0}},
		{"shin2", new float[] {0, 95, 0, 0, 0.52F, 90, 0, 0, 1.12F, 95, 0, 0}}};

	@Override
	public void setupAnim(LootGoblinRenderer.State state) {
		super.setupAnim(state);
		float seconds = state.ageInTicks / 20.0F;
		float run = Mth.clamp(state.walkAnimationSpeed * 4.0F, 0.0F, 1.0F);
		this.play(IDLE, seconds % IDLE_LENGTH, 1.0F - run);
		this.play(RUN, seconds % RUN_LENGTH, run);
		ModelPart head = this.parts.get("head");
		head.yRot += state.yRot * Mth.DEG_TO_RAD * 0.5F;
		head.xRot += state.xRot * Mth.DEG_TO_RAD * 0.5F;
	}

	/** Adds {@code anim} at {@code time}, weighted: rotations onto each part's resting angles, positions onto its pivot. */
	private void play(Object[][] anim, float time, float weight) {
		if (weight <= 0.0F) {
			return;
		}
		float[] v = new float[3];
		for (Object[] track : anim) {
			ModelPart part = this.parts.get((String)track[0]);
			sample((float[])track[1], time, v);
			part.xRot += v[0] * Mth.DEG_TO_RAD * weight;
			part.yRot += v[1] * Mth.DEG_TO_RAD * weight;
			part.zRot += v[2] * Mth.DEG_TO_RAD * weight;
			if (track.length > 2) {
				sample((float[])track[2], time, v);
				// Bedrock positions: y up (so down in the model), x and z as they are
				part.x += v[0] * weight;
				part.y -= v[1] * weight;
				part.z += v[2] * weight;
			}
		}
	}

	/** The value of keys {t, x, y, z, ...} at {@code time}: held before the first and after the last, linear between. */
	private static void sample(float[] keys, float time, float[] out) {
		int n = keys.length / 4;
		int i = 0;
		while (i < n - 1 && keys[(i + 1) * 4] <= time) {
			i++;
		}
		if (i >= n - 1) {
			System.arraycopy(keys, i * 4 + 1, out, 0, 3);
			return;
		}
		float t0 = keys[i * 4], t1 = keys[(i + 1) * 4];
		float f = t1 > t0 ? (time - t0) / (t1 - t0) : 0.0F;
		for (int k = 0; k < 3; k++) {
			out[k] = Mth.lerp(f, keys[i * 4 + 1 + k], keys[(i + 1) * 4 + 1 + k]);
		}
	}
}
