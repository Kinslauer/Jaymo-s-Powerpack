package com.jaymospowers.client.power.zfighter;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.power.zfighter.ApeRage;
import com.jaymospowers.power.zfighter.ZFighterLineage;
import com.jaymospowers.power.zfighter.ZFighterPower;
import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.player.Player;

/**
 * (0.15.148) Super 4's body (Andy: "combining the primal ape look with the character"): the Great Ape's red fur over his
 * arms, shoulders, back and sides, his chest and hands bare, his legs his own. Drawn just over the skin with the player
 * model's own pose ({@code textures/entity/zfighter_primal_fur.png}, {@code tools/gen_primal_fur.py}, in the skin's
 * layout: wide and slim arms both covered). The long red mane is the Super 3 hair in the form's red ({@link ZFighterHair}).
 */
public final class ZFighterPrimalFur {
	public static final RenderStateDataKey<Boolean> KEY = RenderStateDataKey.create(() -> "jaymospowers:zfighter_primal_fur");
	static final Identifier TEXTURE = JaymosPowers.id("textures/entity/zfighter_primal_fur.png");
	/**
	 * The fur sits this far over the skin: past MCA's villager clothes (0.0625 and their outer layer 0.25 more) and the skin's own
	 * jacket and sleeves, so clothes don't hide it, and under armour (0.5).
	 */
	static final float GAP = 0.36F;

	private ZFighterPrimalFur() {
	}

	static void extract(Avatar entity, AvatarRenderState state) {
		boolean fur = entity instanceof Player player && !ZFighterLineage.ape(player)
			&& Integer.valueOf(ApeRage.SUPER_4).equals(player.getAttached(ZFighterPower.FORM));
		state.setData(KEY, fur ? Boolean.TRUE : null);
	}

	static final class Layer extends RenderLayer<AvatarRenderState, PlayerModel> {
		private final PlayerModel wide = model(false), slim = model(true);

		Layer(RenderLayerParent<AvatarRenderState, PlayerModel> renderer) {
			super(renderer);
		}

		private static PlayerModel model(boolean slim) {
			return new PlayerModel(LayerDefinition.create(PlayerModel.createMesh(new CubeDeformation(GAP), slim), 64, 64).bakeRoot(), slim);
		}

		@Override
		public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, AvatarRenderState state, float yRot, float xRot) {
			if (state.getData(KEY) == null || state.isInvisible) {
				return;
			}
			boolean slim = state.skin.model() == net.minecraft.world.entity.player.PlayerModelType.SLIM;
			coloredCutoutModelCopyLayerRender(slim ? this.slim : this.wide, TEXTURE, poseStack, collector, lightCoords, state, -1, 1);
		}
	}
}
