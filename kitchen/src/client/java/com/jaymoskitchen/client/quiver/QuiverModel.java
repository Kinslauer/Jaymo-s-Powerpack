package com.jaymoskitchen.client.quiver;

import com.jaymoskitchen.JaymosKitchen;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;

/**
 * The quiver worn on the back: a leather tube slung diagonally across the back with its strap running over the
 * shoulder and across the chest, and up to 16 arrow shafts poking out of the top. Built on the player model's
 * skeleton (like the cape) so it follows the body when sneaking, swimming and so on.
 *
 * <p>Fletchings are separate models ({@link Fletching}), one per shaft, because each is tinted to its arrow.
 *
 * <p>(0.5.81) It moves: the quiver swings and bounces against the back as you walk and run, the arrows rattle in its
 * mouth, and drawing a bow pulls the top arrow up out of it (gone from the quiver once it's on the string).
 */
public class QuiverModel extends PlayerModel {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(JaymosKitchen.id("quiver"), "main");
	public static final ModelLayerLocation FLETCHING_LAYER = new ModelLayerLocation(JaymosKitchen.id("quiver"), "fletching");

	/** Slung from the right shoulder to the left hip (seen from behind). */
	private static final float QUIVER_Y = 6.0F, QUIVER_TILT = -0.62F;
	private static final PartPose QUIVER_POSE = PartPose.offsetAndRotation(0.0F, QUIVER_Y, 3.7F, 0.0F, 0.0F, QUIVER_TILT);
	/** How high (pixels) the top arrow is lifted out of the quiver by the time it's on the bow. */
	private static final float PULL_HEIGHT = 7.0F;
	private static final float MOUTH_Y = -6.0F;

	private final ModelPart quiver;
	private final ModelPart[] shafts = new ModelPart[QuiverRenderData.SHAFTS];

	public QuiverModel(ModelPart root) {
		super(root, false);
		this.quiver = this.body.getChild("quiver");
		for (int i = 0; i < shafts.length; i++) {
			shafts[i] = quiver.getChild("shaft" + i);
		}
	}

	/** Whether shaft {@code i}'s arrow has been pulled all the way out onto the bow (so neither it nor its fletching shows). */
	static boolean pulled(QuiverRenderData data, int i) {
		return data.draw() >= 1.0F && i == data.shafts() - 1;
	}

	/**
	 * The shared motion, put on both the quiver and the fletching models so they move as one: the quiver swings side
	 * to side with the stride and kicks out from the back at each footfall, a slow breath at rest, and each arrow
	 * rattles on its own beat around where it sits in the mouth.
	 */
	static void animate(AvatarRenderState state, @Nullable QuiverRenderData data, ModelPart quiver, ModelPart[] shafts) {
		float stride = state.walkAnimationPos * 0.6662F;
		float pace = Math.min(1.0F, state.walkAnimationSpeed);
		float breath = Mth.sin(state.ageInTicks * 0.067F);
		quiver.zRot = QUIVER_TILT + Mth.sin(stride) * 0.11F * pace;
		quiver.xRot = Mth.abs(Mth.cos(stride)) * 0.09F * pace + breath * 0.012F;
		quiver.y = QUIVER_Y - Mth.abs(Mth.sin(stride)) * 0.45F * pace;
		for (int i = 0; i < shafts.length; i++) {
			ModelPart shaft = shafts[i];
			float beat = stride * 2.0F + i * 1.7F;
			shaft.xRot = Mth.sin(beat) * 0.07F * pace + breath * 0.01F;
			shaft.zRot = Mth.cos(beat + i * 0.6F) * 0.07F * pace;
			shaft.y = MOUTH_Y;
		}
		if (data != null && data.draw() >= 0.0F && data.shafts() > 0) {
			// the hand reaching back: the top arrow slides up out of the mouth, straightening as it goes
			ModelPart top = shafts[data.shafts() - 1];
			float lift = data.draw() * data.draw() * (3.0F - 2.0F * data.draw());
			top.y = MOUTH_Y - lift * PULL_HEIGHT;
			top.xRot *= 1.0F - lift;
			top.zRot = top.zRot * (1.0F - lift) - lift * QUIVER_TILT * 0.5F;
		}
	}

	/** Where shaft {@code i} sits in the quiver's mouth (a 4x4 grid) and how far it sticks out. */
	private static float shaftX(int i) {
		return -1.35F + (i % 4) * 0.9F;
	}

	private static float shaftZ(int i) {
		return -1.05F + (i / 4) * 0.7F;
	}

	private static float shaftLength(int i) {
		// staggered so the bundle looks loose rather than cut flat
		return 3.4F + ((i * 7) % 5) * 0.45F;
	}

	public static LayerDefinition createLayer() {
		MeshDefinition mesh = PlayerModel.createMesh(CubeDeformation.NONE, false);
		PartDefinition body = mesh.getRoot().clearRecursively().getChild("body");
		PartDefinition quiver = body.addOrReplaceChild("quiver",
			CubeListBuilder.create()
				// the leather tube
				.texOffs(0, 0).addBox(-2.0F, MOUTH_Y, -1.5F, 4.0F, 12.0F, 3.0F)
				// a reinforced rim around the mouth
				.texOffs(0, 15).addBox(-2.5F, MOUTH_Y - 0.5F, -2.0F, 5.0F, 1.5F, 4.0F)
				// end cap
				.texOffs(0, 22).addBox(-2.25F, MOUTH_Y + 11.0F, -1.75F, 4.5F, 1.5F, 3.5F),
			QUIVER_POSE);
		for (int i = 0; i < QuiverRenderData.SHAFTS; i++) {
			float length = shaftLength(i);
			quiver.addOrReplaceChild("shaft" + i,
				CubeListBuilder.create().texOffs(28, 0).addBox(-0.25F, -length, -0.25F, 0.5F, length, 0.5F),
				PartPose.offset(shaftX(i), MOUTH_Y, shaftZ(i)));
		}
		// strap over the shoulder and across the chest, on the same line as the quiver
		body.addOrReplaceChild("strap",
			CubeListBuilder.create().texOffs(20, 0).addBox(-0.5F, -8.0F, -0.5F, 1.0F, 16.0F, 1.0F, new CubeDeformation(0.05F)),
			PartPose.offsetAndRotation(0.0F, 6.0F, -2.3F, 0.0F, 0.0F, -0.62F));
		return LayerDefinition.create(mesh, 32, 32);
	}

	/** All 16 fletchings; each {@link Fletching} model shows just one of them. */
	public static LayerDefinition createFletchingLayer() {
		MeshDefinition mesh = PlayerModel.createMesh(CubeDeformation.NONE, false);
		PartDefinition body = mesh.getRoot().clearRecursively().getChild("body");
		PartDefinition quiver = body.addOrReplaceChild("quiver", CubeListBuilder.create(), QUIVER_POSE);
		for (int i = 0; i < QuiverRenderData.SHAFTS; i++) {
			float top = -shaftLength(i) - 0.2F;
			// pivoted where its shaft is (in the quiver's mouth) so it rattles and lifts with the shaft
			quiver.addOrReplaceChild("fletching" + i,
				CubeListBuilder.create()
					// two crossed vanes
					.texOffs(24, 20).addBox(-0.9F, top, -0.1F, 1.8F, 2.2F, 0.2F)
					.texOffs(24, 20).addBox(-0.1F, top, -0.9F, 0.2F, 2.2F, 1.8F),
				PartPose.offset(shaftX(i), MOUTH_Y, shaftZ(i)));
		}
		return LayerDefinition.create(mesh, 32, 32);
	}

	@Override
	public void setupAnim(AvatarRenderState state) {
		super.setupAnim(state);
		QuiverRenderData data = state.getData(QuiverRenderData.KEY);
		int visible = data == null ? 0 : data.shafts();
		for (int i = 0; i < shafts.length; i++) {
			shafts[i].visible = i < visible && (data == null || !pulled(data, i));
		}
		animate(state, data, quiver, shafts);
	}

	/** One arrow's fletching, drawn with that arrow's colour. */
	public static class Fletching extends PlayerModel {
		private final ModelPart quiver;
		private final ModelPart[] fletchings = new ModelPart[QuiverRenderData.SHAFTS];

		public Fletching(ModelPart root, int index) {
			super(root, false);
			this.quiver = this.body.getChild("quiver");
			for (int i = 0; i < QuiverRenderData.SHAFTS; i++) {
				fletchings[i] = quiver.getChild("fletching" + i);
				fletchings[i].visible = i == index;
			}
		}

		@Override
		public void setupAnim(AvatarRenderState state) {
			super.setupAnim(state);
			animate(state, state.getData(QuiverRenderData.KEY), quiver, fletchings);
		}
	}
}
