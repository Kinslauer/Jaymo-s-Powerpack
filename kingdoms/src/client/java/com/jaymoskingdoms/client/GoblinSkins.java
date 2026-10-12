package com.jaymoskingdoms.client;

import com.jaymoskingdoms.Goblins;
import com.jaymoskingdoms.JaymosKingdoms;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.Identifier;

/**
 * The goblins' look: a man's shape with long ears and a hooked nose (shrunk by their scale attribute), green skins in
 * rags, a slinger's hood, the king's crown and robe (drawn by {@code tools/gen_goblins.py}).
 */
final class GoblinSkins {
	/** Faces drawn per role. */
	static final int GOBLIN_FACES = 4, SLINGER_FACES = 2;
	private static final Identifier[] GOBLINS = new Identifier[GOBLIN_FACES];
	private static final Identifier[] SLINGERS = new Identifier[SLINGER_FACES];
	private static final Identifier KING = JaymosKingdoms.id("textures/entity/goblin/king.png");
	/** How far the ears lift from level (radians). */
	static final float EAR_LIFT = 0.35F;

	private GoblinSkins() {
	}

	static void register() {
		for (int i = 0; i < GOBLIN_FACES; i++) {
			GOBLINS[i] = JaymosKingdoms.id("textures/entity/goblin/goblin_" + i + ".png");
		}
		for (int i = 0; i < SLINGER_FACES; i++) {
			SLINGERS[i] = JaymosKingdoms.id("textures/entity/goblin/slinger_" + i + ".png");
		}
		JaymosKingdomsClient.SKINS.put(Goblins.GOBLIN, state -> GOBLINS[state.variant % GOBLIN_FACES]);
		JaymosKingdomsClient.SKINS.put(Goblins.SLINGER, state -> SLINGERS[state.variant % SLINGER_FACES]);
		JaymosKingdomsClient.SKINS.put(Goblins.KING, state -> KING);
	}

	static boolean goblin(int role) {
		return role == Goblins.GOBLIN || role == Goblins.SLINGER || role == Goblins.KING;
	}

	/**
	 * The goblin body: the humanoid mesh, its head given two long ears out to the sides, tips lifted (texture at 0,32 and
	 * 0,36), and a hooked nose (at 12,32).
	 */
	static <S extends net.minecraft.client.renderer.entity.state.HumanoidRenderState> HumanoidModel<S> model() {
		MeshDefinition mesh = HumanoidModel.createMesh(CubeDeformation.NONE, 0.0F);
		PartDefinition head = mesh.getRoot().getChild("head");
		head.addOrReplaceChild("right_ear", CubeListBuilder.create().texOffs(0, 32).addBox(-4.0F, -1.5F, -0.5F, 4.0F, 3.0F, 1.0F),
			PartPose.offsetAndRotation(-4.0F, -4.5F, 0.0F, 0.0F, 0.0F, EAR_LIFT));
		head.addOrReplaceChild("left_ear", CubeListBuilder.create().texOffs(0, 36).mirror().addBox(0.0F, -1.5F, -0.5F, 4.0F, 3.0F, 1.0F),
			PartPose.offsetAndRotation(4.0F, -4.5F, 0.0F, 0.0F, 0.0F, -EAR_LIFT));
		head.addOrReplaceChild("nose", CubeListBuilder.create().texOffs(12, 32).addBox(-1.0F, -4.0F, -6.0F, 2.0F, 2.0F, 2.0F), PartPose.ZERO);
		return new HumanoidModel<>(LayerDefinition.create(mesh, 64, 64).bakeRoot());
	}
}
