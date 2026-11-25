package com.jaymoskingdoms.client.harvest;

import java.util.EnumSet;
import java.util.Set;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;

/**
 * The Harvest Lich's body (ported from Seasonal: The Wicked Harvest, MIT; see {@code licenses/seasonal}): a humanoid twice a
 * man's size in the model's own units (drawn 0.6 of that), a big head under a crown of spikes, a long cape of
 * {@value #STRIPS} strips of {@value #SEGMENTS} hanging segments that sways and streams back as it walks or gallops, in a
 * 128 by 128 texture.
 */
public final class LichModel extends HumanoidModel<HumanoidRenderState> {
	static final int STRIPS = 5, SEGMENTS = 8, STRIP_WIDTH = 4, SEGMENT_LENGTH = 4;
	/** How the cape rests (radians back from straight down), streams and sways. */
	static final float REST = 0.08F, RIDING_REST = 0.2F, STREAM = 0.45F, SWAY = 0.05F, FLAP = 0.17F, MOST = 1.35F;
	static final float IDLE_SPEED = 0.07F, WAVE_LAG = 0.75F, STRIP_LAG = 0.55F;
	/** The head's idle sway, and how far it may tip up. */
	static final float HEAD_SWAY = 0.12F, HEAD_SPEED = 0.04F, HEAD_PITCH_LIMIT = -0.15F;
	private static final Set<Direction> INNER = EnumSet.of(Direction.NORTH, Direction.WEST, Direction.EAST);
	private static final Set<Direction> TOP_INNER = EnumSet.of(Direction.NORTH, Direction.WEST, Direction.EAST, Direction.DOWN);
	private static final Set<Direction> OUTER = EnumSet.of(Direction.SOUTH);

	private final ModelPart[][] cape = new ModelPart[STRIPS][SEGMENTS];

	public LichModel(ModelPart root) {
		super(root);
		ModelPart base = this.body.getChild("cape");
		for (int s = 0; s < STRIPS; s++) {
			ModelPart part = base.getChild("strip" + s);
			for (int j = 0; j < SEGMENTS; j++) {
				this.cape[s][j] = part;
				if (j + 1 < SEGMENTS) {
					part = part.getChild("segment");
				}
			}
		}
	}

	@Override
	public void setupAnim(HumanoidRenderState state) {
		super.setupAnim(state);
		this.head.yRot += Mth.sin(state.ageInTicks * HEAD_SPEED) * HEAD_SWAY;
		this.head.xRot = Math.max(this.head.xRot, HEAD_PITCH_LIMIT);
		float speed = Math.min(1.0F, state.walkAnimationSpeed);
		float rest = state.isPassenger ? RIDING_REST : REST;
		for (int s = 0; s < STRIPS; s++) {
			for (int j = 0; j < SEGMENTS; j++) {
				float along = (j + 1) / (float)SEGMENTS;
				float wave = Mth.sin(state.ageInTicks * (IDLE_SPEED + speed * 0.33F) - j * WAVE_LAG + s * STRIP_LAG);
				float bend = rest * 0.5F + speed * STREAM * along + (SWAY + speed * FLAP) * wave * along;
				this.cape[s][j].xRot = Mth.clamp(bend, 0.0F, MOST);
			}
		}
	}

	public static LayerDefinition createBodyLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 0).addBox(-8.0F, -16.0F, -8.0F, 16.0F, 16.0F, 16.0F), PartPose.ZERO);
		addCrown(head);
		PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(64, 0).addBox(-8.0F, 0.0F, -4.0F, 16.0F, 24.0F, 8.0F), PartPose.ZERO);
		addCape(body);
		addArm(root, "right_arm", -1.0F);
		addArm(root, "left_arm", 1.0F);
		addLeg(root, "right_leg", -1.0F);
		addLeg(root, "left_leg", 1.0F);
		return LayerDefinition.create(mesh, 128, 128);
	}

	/** Five strips side by side, each a chain of {@value #SEGMENTS} segments hung one from the next. */
	private static void addCape(PartDefinition body) {
		PartDefinition cape = body.addOrReplaceChild("cape", CubeListBuilder.create(), PartPose.offset(0.0F, 2.0F, 7.0F));
		for (int s = 0; s < STRIPS; s++) {
			int x = -10 + s * STRIP_WIDTH;
			PartDefinition parent = cape;
			String name = "strip" + s;
			for (int j = 0; j < SEGMENTS; j++) {
				int v = 64 + j * SEGMENT_LENGTH;
				CubeListBuilder cube = CubeListBuilder.create()
					.texOffs(x + 10, v).addBox(x, 0.0F, 0.0F, 4.0F, 4.0F, 2.0F, j == 0 ? TOP_INNER : INNER)
					.texOffs(30 - x - 8, v).addBox(x, 0.0F, 0.0F, 4.0F, 4.0F, 2.0F, OUTER);
				parent = parent.addOrReplaceChild(name, cube, PartPose.offset(0.0F, j == 0 ? 0.0F : 4.0F, 0.0F));
				name = "segment";
			}
		}
	}

	/** A crown of spikes round the head: a flat band, and eight tines standing on it. */
	private static void addCrown(PartDefinition head) {
		CubeListBuilder crown = CubeListBuilder.create().texOffs(0, 98).addBox(-9.0F, -17.0F, -9.0F, 18.0F, 4.0F, 18.0F);
		float[][] tines = {{-7, -7}, {0, -7}, {7, -7}, {-7, 0}, {7, 0}, {-7, 7}, {0, 7}, {7, 7}};
		for (float[] t : tines) {
			crown.texOffs(72, 98).addBox(t[0] - 1.0F, -22.0F, t[1] - 1.0F, 2.0F, 6.0F, 2.0F);
		}
		head.addOrReplaceChild("hat", crown, PartPose.ZERO);
	}

	private static void addArm(PartDefinition root, String name, float side) {
		float from = side < 0.0F ? -6.0F : -2.0F;
		root.addOrReplaceChild(name, CubeListBuilder.create().texOffs(0, 32).addBox(from, -4.0F, -4.0F, 8.0F, 24.0F, 8.0F), PartPose.offset(side * 10.0F, 4.0F, 0.0F));
	}

	private static void addLeg(PartDefinition root, String name, float side) {
		root.addOrReplaceChild(name, CubeListBuilder.create().texOffs(32, 32).addBox(-4.0F, 0.0F, -4.0F, 8.0F, 24.0F, 8.0F), PartPose.offset(side * 3.8F, 24.0F, 0.0F));
	}
}
