package com.jaymospowers.client.power.zfighter;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.client.costume.CostumeModels;
import com.jaymospowers.core.Powers;
import com.jaymospowers.power.zfighter.ZFighterAndroid;
import com.jaymospowers.power.zfighter.ZFighterFrostForms;
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
import org.jspecify.annotations.Nullable;

/**
 * (0.17.50, Andy's NOEA picks) The plating of a rebuilt body, worn like the Primal Super fur ({@link ZFighterPrimalFur}):
 * dark panels with glowing cyan seams on an android ({@link ZFighterAndroid#android}), and steel plates on a Frost-born
 * rebuilt in metal ({@link ZFighterFrostForms#metal}); both on everyone's screen, never on the Great Ape
 * ({@code textures/entity/zfighter_android_panels.png}, {@code zfighter_metal_plating.png}, {@code tools/gen_form_looks.py}).
 */
public final class ZFighterFormLooks {
	/** On his render state: bit 0 the android's panels, bit 1 the metal plates; absent for neither. */
	public static final RenderStateDataKey<Integer> KEY = RenderStateDataKey.create(() -> "jaymospowers:zfighter_form_looks");
	static final Identifier ANDROID = JaymosPowers.id("textures/entity/zfighter_android_panels.png");
	static final Identifier METAL = JaymosPowers.id("textures/entity/zfighter_metal_plating.png");
	/** How far each sits over the skin (past the skin's jacket and sleeves; metal over the panels). */
	static final float ANDROID_GAP = 0.28F, METAL_GAP = 0.34F;

	private ZFighterFormLooks() {
	}

	static void extract(Avatar entity, AvatarRenderState state) {
		int looks = 0;
		if (entity instanceof Player player && Powers.has(player, ZFighterPower.INSTANCE) && !ZFighterLineage.ape(player)) {
			looks = (ZFighterAndroid.stage(player) >= ZFighterAndroid.DONE ? 1 : 0) | (ZFighterFrostForms.metal(player) ? 2 : 0);
		}
		state.setData(KEY, looks == 0 ? null : looks);
	}

	static final class Layer extends RenderLayer<AvatarRenderState, PlayerModel> {
		private @Nullable PlayerModel androidWide, androidSlim, metalWide, metalSlim;

		Layer(RenderLayerParent<AvatarRenderState, PlayerModel> renderer) {
			super(renderer);
		}

		private PlayerModel model(boolean metal, boolean slimArms) {
			PlayerModel have = metal ? slimArms ? this.metalSlim : this.metalWide : slimArms ? this.androidSlim : this.androidWide;
			if (have != null) {
				return have;
			}
			PlayerModel made = CostumeModels.worn(LayerDefinition.create(PlayerModel.createMesh(new CubeDeformation(metal ? METAL_GAP : ANDROID_GAP), slimArms), 64, 64),
				slimArms, this::getParentModel, (worn, state) -> {
				});
			if (metal) {
				if (slimArms) {
					this.metalSlim = made;
				} else {
					this.metalWide = made;
				}
			} else if (slimArms) {
				this.androidSlim = made;
			} else {
				this.androidWide = made;
			}
			return made;
		}

		@Override
		public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, AvatarRenderState state, float yRot, float xRot) {
			Integer looks = state.getData(KEY);
			if (looks == null || state.isInvisible) {
				return;
			}
			PlayerModel parent = this.getParentModel();
			CostumeModels.wear(state, parent);
			boolean slimArms = CostumeModels.slim(parent, state);
			if ((looks & 1) != 0) {
				renderColoredCutoutModel(model(false, slimArms), ANDROID, poseStack, collector, lightCoords, state, -1, 1);
			}
			if ((looks & 2) != 0) {
				renderColoredCutoutModel(model(true, slimArms), METAL, poseStack, collector, lightCoords, state, -1, 1);
			}
		}
	}
}
