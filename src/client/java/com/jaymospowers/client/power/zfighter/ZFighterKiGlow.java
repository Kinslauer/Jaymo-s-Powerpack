package com.jaymospowers.client.power.zfighter;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.client.costume.CostumeModels;
import com.jaymospowers.core.Powers;
import com.jaymospowers.power.zfighter.ZFighterBranchForms;
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
 * <p>
 * (0.16.20) Andy: "the ki glow looks great, i need that one to be an idle for forms though, and maybe it pulses for a
 * second at max ki". The glow is the look of a form at rest, under {@link ZFighterKiAura}'s flares; the charge no longer
 * brightens it (the aura has that), and when a charge fills his ki ({@link ZFighterPower#KI_FULL}) it flashes up and
 * swells for {@link #PULSE_TICKS} before settling.
 */
public final class ZFighterKiGlow {
	/** On his render state: the glow's colour (RGB) and strength (0-1), or null for none. */
	public static final RenderStateDataKey<float[]> KEY = RenderStateDataKey.create(() -> "jaymospowers:zfighter_ki_glow");
	static final Identifier TEXTURE = JaymosPowers.id("textures/entity/zfighter_ki_glow.png");
	/** How far out each shell sits (model pixels) and how bright it is, inside out. */
	static final float[] SHELLS = {0.45F, 0.95F, 1.6F};
	static final float[] SHELL_LIGHT = {0.30F, 0.17F, 0.08F};
	/** Strength in base form, and in a form (plus this per form). */
	static final float BASE = 0.35F, FORM = 0.5F, PER_FORM = 0.12F;
	/** Ki full: the glow flashes up by this much (fading out over this many ticks) and its shells swell by up to this share. */
	static final float PULSE = 1.1F, PULSE_SWELL = 0.12F;
	static final int PULSE_TICKS = 20;
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
		// (0.17.50) a form past Primal Super reads as its tier; a branch form or Ego as a higher form
		int f = ZFighterBranchForms.tier(player, com.jaymospowers.power.zfighter.ZFighterForms.tier(form == null ? 0 : form));
		float strength = Math.min(1.0F, f > 0 ? FORM + PER_FORM * f : BASE);
		// ki full: a flash that fades over a second
		Long full = player.getAttached(ZFighterPower.KI_FULL);
		long since = full == null ? Long.MAX_VALUE : player.level().getGameTime() - full;
		float pulse = since >= 0L && since < PULSE_TICKS ? 1.0F - (float)since / PULSE_TICKS : 0.0F;
		return new float[] {color(player, f), strength + PULSE * pulse, pulse};
	}

	/** His ki colour in base form, the form's in a form. */
	public static int color(Player player, int form) {
		int special = ZFighterBranchForms.color(player);
		if (special != 0) {
			return special;
		}
		Integer ki = player.getAttached(ZFighterPower.KI_COLOR);
		// (0.17.50) silver instinct
		if (com.jaymospowers.power.zfighter.ZFighterInstinct.active(player)) {
			return com.jaymospowers.power.zfighter.ZFighterInstinct.COLOR;
		}
		return form > 0 || ki == null ? ZFighterLineage.color(ZFighterLineage.of(player), form) : ki;
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
			// the ki-full pulse: the shells puff out round him, from his feet
			float swell = 1.0F + PULSE_SWELL * glow[2];
			poseStack.pushPose();
			poseStack.translate(0.0F, 1.5F, 0.0F);
			poseStack.scale(swell, swell, swell);
			poseStack.translate(0.0F, -1.5F, 0.0F);
			for (int i = 0; i < SHELLS.length; i++) {
				// additive: the colour's brightness is how strongly it shows
				float light = Mth.clamp(SHELL_LIGHT[i] * glow[1] * breathe, 0.0F, 1.0F);
				int tint = ARGB.color(255, (int)(ARGB.red(color) * light), (int)(ARGB.green(color) * light), (int)(ARGB.blue(color) * light));
				collector.order(2 + i).submitModel(shell(i, slimArms), state, poseStack, RenderTypes.energySwirl(TEXTURE, i * 0.31F, -rise * (1 + i)),
					lightCoords, OverlayTexture.NO_OVERLAY, tint, null, 0);
			}
			poseStack.popPose();
		}
	}
}
