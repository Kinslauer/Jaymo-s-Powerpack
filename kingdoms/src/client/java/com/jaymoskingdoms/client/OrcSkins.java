package com.jaymoskingdoms.client;

import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.Orcs;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.Identifier;

/**
 * (0.5.45) The orcs' look: a man's shape drawn {@link #BROAD} times as wide (the same height, so they still fit through a
 * door), with tusks, a heavy brow and small swept-back ears drawn over it; grey-green hides in war paint and scars, a
 * ranger's hood, a shaman's bone mask and feathers, the warchief's iron jaw-guard and trophies (drawn by
 * {@code tools/gen_orcs.py}).
 */
final class OrcSkins {
	/** Faces drawn per role. */
	static final int GRUNT_FACES = 4, RANGER_FACES = 2;
	/** How much broader than a man an orc is drawn (x and z; y unchanged). */
	static final float BROAD = 1.16F;
	private static final Identifier[] GRUNTS = new Identifier[GRUNT_FACES];
	private static final Identifier[] RANGERS = new Identifier[RANGER_FACES];
	private static final Identifier SHAMAN = JaymosKingdoms.id("textures/entity/orc/shaman.png");
	private static final Identifier WARCHIEF = JaymosKingdoms.id("textures/entity/orc/warchief.png");
	/** How far the ears sweep back (radians). */
	static final float EAR_SWEEP = 0.45F;

	private OrcSkins() {
	}

	static void register() {
		for (int i = 0; i < GRUNT_FACES; i++) {
			GRUNTS[i] = JaymosKingdoms.id("textures/entity/orc/grunt_" + i + ".png");
		}
		for (int i = 0; i < RANGER_FACES; i++) {
			RANGERS[i] = JaymosKingdoms.id("textures/entity/orc/ranger_" + i + ".png");
		}
		JaymosKingdomsClient.SKINS.put(Orcs.GRUNT, state -> GRUNTS[state.variant % GRUNT_FACES]);
		JaymosKingdomsClient.SKINS.put(Orcs.RANGER, state -> RANGERS[state.variant % RANGER_FACES]);
		JaymosKingdomsClient.SKINS.put(Orcs.SHAMAN, state -> SHAMAN);
		JaymosKingdomsClient.SKINS.put(Orcs.WARCHIEF, state -> WARCHIEF);
	}

	static boolean orc(int role) {
		return Orcs.isOrc(role);
	}

	/** Draws an orc broad across the shoulders (from the renderer's {@code scale}). */
	static void broaden(JaymosKingdomsClient.FolkState state, PoseStack poseStack) {
		if (orc(state.role)) {
			poseStack.scale(BROAD, 1.0F, BROAD);
		}
	}

	/**
	 * The orc's features as a model of their own (a humanoid with no cubes but these, posed like its wearer's body): two
	 * tusks jutting up from the jaw (texture at 0,32 and 4,32), a heavy brow (at 8,32) and two small ears swept back
	 * (at 0,36 and 8,36).
	 */
	static HumanoidModel<JaymosKingdomsClient.FolkState> features() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create(), PartPose.ZERO);
		head.addOrReplaceChild("hat", CubeListBuilder.create(), PartPose.ZERO);
		for (String part : new String[] {"body", "right_arm", "left_arm", "right_leg", "left_leg"}) {
			root.addOrReplaceChild(part, CubeListBuilder.create(), PartPose.ZERO);
		}
		head.addOrReplaceChild("right_tusk", CubeListBuilder.create().texOffs(0, 32).addBox(-3.0F, -3.0F, -5.0F, 1.0F, 2.0F, 1.0F), PartPose.ZERO);
		head.addOrReplaceChild("left_tusk", CubeListBuilder.create().texOffs(4, 32).addBox(2.0F, -3.0F, -5.0F, 1.0F, 2.0F, 1.0F), PartPose.ZERO);
		head.addOrReplaceChild("brow", CubeListBuilder.create().texOffs(8, 32).addBox(-4.0F, -6.0F, -4.6F, 8.0F, 1.0F, 1.0F), PartPose.ZERO);
		head.addOrReplaceChild("right_ear", CubeListBuilder.create().texOffs(0, 36).addBox(-1.0F, -1.5F, 0.0F, 1.0F, 3.0F, 2.0F),
			PartPose.offsetAndRotation(-4.0F, -4.5F, -0.5F, 0.0F, -EAR_SWEEP, 0.0F));
		head.addOrReplaceChild("left_ear", CubeListBuilder.create().texOffs(8, 36).mirror().addBox(0.0F, -1.5F, 0.0F, 1.0F, 3.0F, 2.0F),
			PartPose.offsetAndRotation(4.0F, -4.5F, -0.5F, 0.0F, EAR_SWEEP, 0.0F));
		return new HumanoidModel<>(LayerDefinition.create(mesh, 64, 64).bakeRoot());
	}

	/** Draws an orc's {@link #features} over its body, in its own skin. */
	static final class Features extends RenderLayer<JaymosKingdomsClient.FolkState, HumanoidModel<JaymosKingdomsClient.FolkState>> {
		private final HumanoidModel<JaymosKingdomsClient.FolkState> model = features();

		Features(RenderLayerParent<JaymosKingdomsClient.FolkState, HumanoidModel<JaymosKingdomsClient.FolkState>> parent) {
			super(parent);
		}

		@Override
		public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, JaymosKingdomsClient.FolkState state, float yRot, float xRot) {
			if (!orc(state.role)) {
				return;
			}
			Identifier skin = JaymosKingdomsClient.SKINS.get(state.role).apply(state);
			coloredCutoutModelCopyLayerRender(this.model, skin, poseStack, collector, lightCoords, state, -1, 1);
		}
	}
}
