package com.jaymospowers.client.power.zfighter;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.power.zfighter.ZFighterLineage;
import com.jaymospowers.power.zfighter.ZFighterPower;
import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.player.Player;

/**
 * Super's hair ({@code textures/entity/zfighter_hair.png}, pale strands tinted the form's colour, lit from within): a cap
 * over his own hair with spikes standing up, taller and sharper in Super 2, and in Super 3 a mane down to the waist.
 * Earthlings and the Warrior-born only (the Verdant grow, the Frost-born have no hair to change); never the Great Ape.
 */
public final class ZFighterHair {
	/** The form (1-3) and its colour, or null for no Super hair. */
	public record Hair(int form, int color) {
	}

	public static final RenderStateDataKey<Hair> KEY = RenderStateDataKey.create(() -> "jaymospowers:zfighter_hair");
	static final Identifier TEXTURE = JaymosPowers.id("textures/entity/zfighter_hair.png");
	static final int FULL_BRIGHT = 0xF000F0;

	private static final ModelPart[] HAIR = new ModelPart[4];
	/** The cap sits this far out from the head: past the skin's hat layer (0.5). */
	private static final CubeDeformation CAP = new CubeDeformation(0.7F);

	private ZFighterHair() {
	}

	/** Spikes as {x, z, height, tilt forward (radians), tilt out (radians)} on the crown (the head's top is y -8). */
	private static final float[][] SPIKES_1 = {
		{0, -2, 5, 0.5F, 0}, {-2.5F, -1, 5, 0.3F, -0.5F}, {2.5F, -1, 5, 0.3F, 0.5F}, {0, 1.5F, 6, -0.3F, 0},
		{-2.5F, 2, 5, -0.4F, -0.5F}, {2.5F, 2, 5, -0.4F, 0.5F}, {0, -3.5F, 3, 1.1F, 0},
	};
	private static final float[][] SPIKES_2 = {
		{0, -2, 7, 0.35F, 0}, {-2.5F, -1, 7, 0.2F, -0.4F}, {2.5F, -1, 7, 0.2F, 0.4F}, {0, 1.5F, 8, -0.25F, 0},
		{-2.5F, 2, 7, -0.3F, -0.45F}, {2.5F, 2, 7, -0.3F, 0.45F}, {-1, -3.5F, 4, 1.2F, -0.2F}, {1, -3.5F, 4, 1.2F, 0.2F},
	};

	private static ModelPart hair(int form) {
		if (HAIR[form] == null) {
			MeshDefinition mesh = new MeshDefinition();
			PartDefinition root = mesh.getRoot();
			// the cap: over the top, a fringe above the eyes, down the back and over the ears, proud of the skin's own hat
			// layer (0.5) so none of his own hair shows through
			root.addOrReplaceChild("cap", CubeListBuilder.create().texOffs(0, 0).addBox(-4.0F, -8.0F, -4.0F, 8.0F, 4.0F, 8.0F, CAP)
				.texOffs(0, 11).addBox(-4.0F, -4.0F, 1.0F, 8.0F, 4.0F, 3.0F, CAP)
				.texOffs(24, 0).addBox(-4.0F, -4.0F, -2.0F, 1.0F, 2.0F, 3.0F, CAP)
				.texOffs(24, 0).mirror().addBox(3.0F, -4.0F, -2.0F, 1.0F, 2.0F, 3.0F, CAP), PartPose.ZERO);
			float[][] spikes = form >= 2 ? SPIKES_2 : SPIKES_1;
			for (int i = 0; i < spikes.length; i++) {
				float[] s = spikes[i];
				root.addOrReplaceChild("spike" + i, CubeListBuilder.create().texOffs(24, 11).addBox(-1.0F, -s[2], -1.0F, 2.0F, s[2], 2.0F),
					PartPose.offsetAndRotation(s[0], -8.0F, s[1], -s[3], 0.0F, s[4]));
			}
			if (form == 3) {
				// the mane: long, down the back to the waist, flaring a little
				root.addOrReplaceChild("mane", CubeListBuilder.create().texOffs(0, 17).addBox(-4.5F, 0.0F, 0.0F, 9.0F, 14.0F, 2.0F),
					PartPose.offsetAndRotation(0.0F, -7.0F, 3.5F, 0.18F, 0.0F, 0.0F));
				root.addOrReplaceChild("mane_l", CubeListBuilder.create().texOffs(22, 17).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 11.0F, 2.0F),
					PartPose.offsetAndRotation(-4.0F, -6.0F, 2.5F, 0.15F, 0.0F, 0.25F));
				root.addOrReplaceChild("mane_r", CubeListBuilder.create().texOffs(22, 17).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 11.0F, 2.0F),
					PartPose.offsetAndRotation(4.0F, -6.0F, 2.5F, 0.15F, 0.0F, -0.25F));
			}
			HAIR[form] = LayerDefinition.create(mesh, 32, 48).bakeRoot();
		}
		return HAIR[form];
	}

	static void extract(Avatar entity, AvatarRenderState state) {
		Hair hair = null;
		if (entity instanceof Player player && !ZFighterLineage.ape(player)) {
			Integer form = player.getAttached(ZFighterPower.FORM);
			int lineage = ZFighterLineage.of(player);
			if (form != null && form > 0 && (lineage == ZFighterLineage.NONE || lineage == ZFighterLineage.EARTHLING
				|| lineage == ZFighterLineage.WARRIOR)) {
				hair = new Hair(Math.min(form, 3), ZFighterLineage.color(lineage, form));
			}
		}
		state.setData(KEY, hair);
		if (hair != null) {
			// his own hair (the skin's hat layer) goes under the Super hair, or it pokes through
			state.showHat = false;
		}
	}

	static final class Layer extends RenderLayer<AvatarRenderState, PlayerModel> {
		Layer(RenderLayerParent<AvatarRenderState, PlayerModel> renderer) {
			super(renderer);
		}

		@Override
		public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, AvatarRenderState state, float yRot, float xRot) {
			Hair hair = state.getData(KEY);
			if (hair == null || state.isInvisible) {
				return;
			}
			poseStack.pushPose();
			this.getParentModel().head.translateAndRotate(poseStack);
			collector.submitModelPart(hair(hair.form()), poseStack, RenderTypes.entityCutout(TEXTURE), FULL_BRIGHT, OverlayTexture.NO_OVERLAY, null,
				0xFF000000 | hair.color());
			poseStack.popPose();
		}
	}
}
