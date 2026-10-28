package com.jaymoskingdoms.client;

import com.jaymoskingdoms.BioBud;
import com.jaymoskingdoms.JaymosKingdoms;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;

/**
 * (0.7.307, Andy: "they should look like smaller versions of the user, not zombies") A Bio-built's bud drawn as its maker:
 * their own skin on a player's body (wide or slim arms, as theirs are), small (its SCALE), with the third form's seams,
 * plates and amber nodes over it, and the heritage's amber eyes. A maker this client doesn't know wears the default skin
 * for their id.
 */
final class BioBudRenderer extends HumanoidMobRenderer<BioBud, BioBudRenderer.State, HumanoidModel<BioBudRenderer.State>> {
	/** The third form's marks (buds come only from the third form), its glow and the eyes. */
	private static final Identifier MARKS = JaymosKingdoms.id("textures/entity/heritage/biobuilt_2_skin.png"),
		GLOW = JaymosKingdoms.id("textures/entity/heritage/biobuilt_2_glow.png"), EYES = JaymosKingdoms.id("textures/entity/heritage/biobuilt_eyes.png");

	static final class State extends HumanoidRenderState {
		Identifier skin = DefaultPlayerSkin.getDefaultTexture();
		boolean slim;
	}

	private final HumanoidModel<State> wide, slim;

	private BioBudRenderer(EntityRendererProvider.Context context) {
		super(context, body(false), 0.5F);
		this.wide = this.model;
		this.slim = body(true);
		this.addLayer(new Marks(this));
	}

	/** A player's body (sleeves, jacket and trousers too) for a bud's mob render state. */
	private static HumanoidModel<State> body(boolean slim) {
		return new HumanoidModel<>(LayerDefinition.create(PlayerModel.createMesh(CubeDeformation.NONE, slim), 64, 64).bakeRoot());
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(BioBud bud, State state, float partialTicks) {
		super.extractRenderState(bud, state, partialTicks);
		PlayerSkin skin = skin(BioBud.maker(bud));
		state.skin = skin.body().texturePath();
		state.slim = skin.model() == PlayerModelType.SLIM;
	}

	/** {@code maker}'s skin, as this client has it. */
	private static PlayerSkin skin(UUID maker) {
		if (maker == null) {
			return DefaultPlayerSkin.getDefaultSkin();
		}
		var connection = Minecraft.getInstance().getConnection();
		PlayerInfo info = connection == null ? null : connection.getPlayerInfo(maker);
		return info != null ? info.getSkin() : DefaultPlayerSkin.get(maker);
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		this.model = state.slim ? this.slim : this.wide;
		super.submit(state, poseStack, collector, camera);
	}

	@Override
	public Identifier getTextureLocation(State state) {
		return state.skin;
	}

	/** The third form's marks over the skin, and their glow and the eyes lit. */
	private static final class Marks extends RenderLayer<State, HumanoidModel<State>> {
		Marks(RenderLayerParent<State, HumanoidModel<State>> parent) {
			super(parent);
		}

		@Override
		public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, State state, float yRot, float xRot) {
			if (state.isInvisible) {
				return;
			}
			HumanoidModel<State> model = this.getParentModel();
			renderColoredCutoutModel(model, MARKS, poseStack, collector, lightCoords, state, -1, 1);
			collector.order(2).submitModel(model, state, poseStack, RenderTypes.eyes(GLOW), lightCoords, OverlayTexture.NO_OVERLAY, -1, null, state.outlineColor);
			collector.order(2).submitModel(model, state, poseStack, RenderTypes.eyes(EYES), lightCoords, OverlayTexture.NO_OVERLAY, -1, null, state.outlineColor);
		}
	}

	static void register() {
		EntityRenderers.register(BioBud.TYPE, BioBudRenderer::new);
	}
}
