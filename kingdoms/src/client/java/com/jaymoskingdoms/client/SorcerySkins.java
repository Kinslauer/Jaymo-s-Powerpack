package com.jaymoskingdoms.client;

import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.Sorcery;
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
import net.minecraft.world.item.DyeColor;

/**
 * (0.5.37) The casters' look ({@link Sorcery}): the necromancer's hood and black robe (two faces), the shaman's war paint and
 * wolf-skull headdress in his camp's colour, the court wizard's robe in his realm's colour, and the wizard's pointed hat, a
 * model of its own over his head (drawn by {@code tools/gen_sorcery.py}).
 */
final class SorcerySkins {
	static final int NECROMANCER_FACES = 2;
	private static final Identifier[] NECROMANCERS = new Identifier[NECROMANCER_FACES];
	private static final Identifier[] SHAMANS = new Identifier[DyeColor.values().length];
	private static final Identifier[] WIZARDS = new Identifier[DyeColor.values().length];
	/** How far the hat's tip leans back (radians). */
	static final float TIP_LEAN = 0.35F;

	private SorcerySkins() {
	}

	static void register() {
		for (int i = 0; i < NECROMANCER_FACES; i++) {
			NECROMANCERS[i] = JaymosKingdoms.id("textures/entity/sorcery/necromancer_" + i + ".png");
		}
		for (DyeColor dye : DyeColor.values()) {
			SHAMANS[dye.getId()] = JaymosKingdoms.id("textures/entity/sorcery/shaman/" + dye.getName() + ".png");
			WIZARDS[dye.getId()] = JaymosKingdoms.id("textures/entity/sorcery/wizard/" + dye.getName() + ".png");
		}
		JaymosKingdomsClient.SKINS.put(Sorcery.NECROMANCER, state -> NECROMANCERS[state.variant % NECROMANCER_FACES]);
		JaymosKingdomsClient.SKINS.put(Sorcery.SHAMAN, state -> SHAMANS[dye(state.color, DyeColor.RED)]);
		JaymosKingdomsClient.SKINS.put(Sorcery.WIZARD, state -> WIZARDS[dye(state.color, DyeColor.BLUE)]);
	}

	private static int dye(int color, DyeColor fallback) {
		return color >= 0 && color < DyeColor.values().length ? color : fallback.getId();
	}

	/**
	 * The wizard's hat as a model of its own (a humanoid with no cubes but these, posed like its wearer's head): a wide brim
	 * (texture at 0,32), three narrowing tiers (40,32; 0,48; 48,48) and a tip leaning back (56,48).
	 */
	static HumanoidModel<JaymosKingdomsClient.FolkState> hat() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create(), PartPose.ZERO);
		head.addOrReplaceChild("hat", CubeListBuilder.create(), PartPose.ZERO);
		for (String part : new String[] {"body", "right_arm", "left_arm", "right_leg", "left_leg"}) {
			root.addOrReplaceChild(part, CubeListBuilder.create(), PartPose.ZERO);
		}
		head.addOrReplaceChild("brim", CubeListBuilder.create().texOffs(0, 32).addBox(-5.0F, -9.0F, -5.0F, 10.0F, 1.0F, 10.0F), PartPose.ZERO);
		head.addOrReplaceChild("tier0", CubeListBuilder.create().texOffs(40, 32).addBox(-3.0F, -12.0F, -3.0F, 6.0F, 3.0F, 6.0F), PartPose.ZERO);
		head.addOrReplaceChild("tier1", CubeListBuilder.create().texOffs(0, 48).addBox(-2.0F, -15.0F, -1.75F, 4.0F, 3.0F, 4.0F), PartPose.ZERO);
		PartDefinition top = head.addOrReplaceChild("tier2", CubeListBuilder.create().texOffs(48, 48).addBox(-1.0F, -3.0F, -1.0F, 2.0F, 3.0F, 2.0F),
			PartPose.offsetAndRotation(0.0F, -15.0F, 0.5F, -TIP_LEAN * 0.5F, 0.0F, 0.0F));
		top.addOrReplaceChild("tip", CubeListBuilder.create().texOffs(56, 48).addBox(-0.5F, -2.0F, -0.5F, 1.0F, 2.0F, 1.0F),
			PartPose.offsetAndRotation(0.0F, -3.0F, 0.0F, -TIP_LEAN, 0.0F, 0.0F));
		return new HumanoidModel<>(LayerDefinition.create(mesh, 64, 64).bakeRoot());
	}

	/** Draws a wizard's {@link #hat} on his head, in his own skin. */
	static final class Hat extends RenderLayer<JaymosKingdomsClient.FolkState, HumanoidModel<JaymosKingdomsClient.FolkState>> {
		private final HumanoidModel<JaymosKingdomsClient.FolkState> model = hat();

		Hat(RenderLayerParent<JaymosKingdomsClient.FolkState, HumanoidModel<JaymosKingdomsClient.FolkState>> parent) {
			super(parent);
		}

		@Override
		public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, JaymosKingdomsClient.FolkState state, float yRot, float xRot) {
			if (state.role != Sorcery.WIZARD) {
				return;
			}
			Identifier skin = JaymosKingdomsClient.SKINS.get(state.role).apply(state);
			coloredCutoutModelCopyLayerRender(this.model, skin, poseStack, collector, lightCoords, state, -1, 1);
		}
	}
}
