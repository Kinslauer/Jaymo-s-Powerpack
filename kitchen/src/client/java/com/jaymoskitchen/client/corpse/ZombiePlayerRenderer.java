package com.jaymoskitchen.client.corpse;

import com.jaymoskitchen.JaymosKitchen;
import com.jaymoskitchen.corpse.ZombiePlayer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;

/**
 * A zombie player: the dead player's own skin (slim or wide arms, as theirs were), gone grey-blue, with their
 * clothes in tatters over it, shambling with its arms out like any zombie. After the Zombie Players mod.
 *
 * <p>Its render state is deliberately not a player's: the game sends any player-type render state to its own player
 * renderer, which drew these as plain, untouched players.
 */
public class ZombiePlayerRenderer extends LivingEntityRenderer<ZombiePlayer, ZombiePlayerRenderer.State, ZombiePlayerModel> {
	private static final Identifier TATTERS = JaymosKitchen.id("textures/entity/zombie_player_tatters.png");
	/** Multiplied over the skin: dead grey-blue. */
	private static final int DEAD_SKIN = 0xFF93A8AE;

	public static class State extends HumanoidRenderState {
		PlayerSkin skin = DefaultPlayerSkin.getDefaultSkin();
	}

	private final ZombiePlayerModel wide;
	private final ZombiePlayerModel slim;

	public ZombiePlayerRenderer(EntityRendererProvider.Context context) {
		// (0.4.42) the player corpse's layers, not the vanilla player ones: Fresh Animations would walk it like a player,
		// arms down, over its zombie arms (see CorpseRenderer#PLAYER_BODY)
		super(context, new ZombiePlayerModel(context.bakeLayer(CorpseRenderer.PLAYER_BODY)), 0.5F);
		this.wide = this.model;
		this.slim = new ZombiePlayerModel(context.bakeLayer(CorpseRenderer.PLAYER_BODY_SLIM));
		addLayer(new ItemInHandLayer<>(this));
		addLayer(new Tatters(this, new ZombiePlayerModel(context.bakeLayer(ZombiePlayerModel.TATTERS)),
			new ZombiePlayerModel(context.bakeLayer(ZombiePlayerModel.TATTERS_SLIM))));
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(ZombiePlayer zombie, State state, float partialTicks) {
		super.extractRenderState(zombie, state, partialTicks);
		HumanoidMobRenderer.extractHumanoidRenderState(zombie, state, partialTicks, this.itemModelResolver);
		state.skin = Skins.of(zombie.owner(), zombie.ownerName());
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		this.model = state.skin.model() == PlayerModelType.SLIM ? slim : wide;
		super.submit(state, poseStack, collector, camera);
	}

	@Override
	protected boolean shouldShowName(ZombiePlayer zombie, double distanceToCameraSq) {
		// as any mob: only a name-tagged one, looked at (the living-entity default shows every one, as for players)
		return super.shouldShowName(zombie, distanceToCameraSq)
			&& (zombie.shouldShowName() || zombie.hasCustomName() && zombie == this.entityRenderDispatcher.crosshairPickEntity);
	}

	@Override
	public Identifier getTextureLocation(State state) {
		return state.skin.body().texturePath();
	}

	@Override
	protected int getModelTint(State state) {
		return DEAD_SKIN;
	}

	/** Torn clothes and grey flesh showing through, drawn just over the skin. */
	private static final class Tatters extends RenderLayer<State, ZombiePlayerModel> {
		private final ZombiePlayerModel wide;
		private final ZombiePlayerModel slim;

		Tatters(RenderLayerParent<State, ZombiePlayerModel> renderer, ZombiePlayerModel wide, ZombiePlayerModel slim) {
			super(renderer);
			this.wide = wide;
			this.slim = slim;
		}

		@Override
		public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, State state, float yRot, float xRot) {
			if (state.isInvisible) {
				return;
			}
			ZombiePlayerModel model = state.skin.model() == PlayerModelType.SLIM ? slim : wide;
			collector.submitModel(model, state, poseStack, RenderTypes.entityCutout(TATTERS), lightCoords,
				LivingEntityRenderer.getOverlayCoords(state, 0.0F), -1, null, state.outlineColor);
		}
	}
}
