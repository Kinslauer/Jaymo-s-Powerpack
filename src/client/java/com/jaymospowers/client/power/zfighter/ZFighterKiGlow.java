package com.jaymospowers.client.power.zfighter;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.client.costume.CostumeModels;
import com.jaymospowers.core.Powers;
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
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/**
 * (0.16.7) Andy, pointing at Create: Lanterns' ring-bearers (All Rights Reserved; the look only): "the aura on these
 * characters is what i mean by the ki aura glow. it should be faint, but apparent". A soft glow of his ki colour (a
 * form's colour in a form) hugging his outline: a few shells just out from the body, each fainter than the one inside
 * it, wisps scrolling up them, added onto what's behind like light. Brighter while charging and in each higher form;
 * faint in base form. Worn like a costume ({@link CostumeModels#worn}), so it takes every pose the body's drawn in.
 * Not on the Great Ape, nor while hidden.
 */
public final class ZFighterKiGlow {
	/** On his render state: the glow's colour (RGB) and strength (0-1), or null for none. */
	public static final RenderStateDataKey<float[]> KEY = RenderStateDataKey.create(() -> "jaymospowers:zfighter_ki_glow");
	static final Identifier TEXTURE = JaymosPowers.id("textures/entity/zfighter_ki_glow.png");
	/** How far out each shell sits (model pixels) and how bright it is, inside out. */
	static final float[] SHELLS = {0.45F, 0.95F, 1.6F};
	static final float[] SHELL_LIGHT = {0.30F, 0.17F, 0.08F};
	/** Strength in base form, while charging, and in a form (plus this per form). */
	static final float BASE = 0.35F, CHARGING = 0.9F, FORM = 0.5F, PER_FORM = 0.12F;
	/** How fast the wisps rise, and the glow's slow breathing. */
	static final float RISE = 0.012F, BREATHE = 0.08F, BREATHE_SPEED = 0.15F;

	private ZFighterKiGlow() {
	}

	static void extract(Avatar entity, AvatarRenderState state) {
		state.setData(KEY, entity instanceof Player player ? glow(player) : null);
	}

	private static float @Nullable [] glow(Player player) {
		if (!Powers.has(player, ZFighterPower.INSTANCE) || player.isInvisible() || ZFighterLineage.ape(player)) {
			return null;
		}
		Integer form = player.getAttached(ZFighterPower.FORM);
		int f = form == null ? 0 : form;
		boolean charging = player.hasAttached(ZFighterPower.CHARGE);
		float strength = Math.min(1.0F, (f > 0 ? FORM + PER_FORM * f : BASE) + (charging ? CHARGING - BASE : 0.0F));
		int lineage = ZFighterLineage.of(player);
		Integer ki = player.getAttached(ZFighterPower.KI_COLOR);
		int color = f > 0 || ki == null ? ZFighterLineage.color(lineage, f) : ki;
		return new float[] {color, strength};
	}

	static final class Layer extends RenderLayer<AvatarRenderState, PlayerModel> {
		private final PlayerModel[] wide = new PlayerModel[SHELLS.length], slim = new PlayerModel[SHELLS.length];

		Layer(RenderLayerParent<AvatarRenderState, PlayerModel> renderer) {
			super(renderer);
		}

		private PlayerModel shell(int i, boolean slimArms) {
			PlayerModel[] models = slimArms ? this.slim : this.wide;
			if (models[i] == null) {
				models[i] = CostumeModels.worn(LayerDefinition.create(PlayerModel.createMesh(new CubeDeformation(SHELLS[i]), slimArms), 64, 64),
					slimArms, this::getParentModel, (model, state) -> {
					});
			}
			return models[i];
		}

		@Override
		public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, AvatarRenderState state, float yRot, float xRot) {
			float[] glow = state.getData(KEY);
			if (glow == null || state.isInvisible) {
				return;
			}
			PlayerModel parent = this.getParentModel();
			CostumeModels.wear(state, parent);
			boolean slimArms = CostumeModels.slim(parent, state);
			int color = (int)glow[0];
			float breathe = 1.0F + BREATHE * Mth.sin(state.ageInTicks * BREATHE_SPEED);
			float rise = (state.ageInTicks * RISE) % 1.0F;
			for (int i = 0; i < SHELLS.length; i++) {
				// additive: the colour's brightness is how strongly it shows
				float light = Mth.clamp(SHELL_LIGHT[i] * glow[1] * breathe, 0.0F, 1.0F);
				int tint = ARGB.color(255, (int)(ARGB.red(color) * light), (int)(ARGB.green(color) * light), (int)(ARGB.blue(color) * light));
				collector.order(2 + i).submitModel(shell(i, slimArms), state, poseStack, RenderTypes.energySwirl(TEXTURE, i * 0.31F, -rise * (1 + i)),
					lightCoords, OverlayTexture.NO_OVERLAY, tint, null, 0);
			}
		}
	}
}
