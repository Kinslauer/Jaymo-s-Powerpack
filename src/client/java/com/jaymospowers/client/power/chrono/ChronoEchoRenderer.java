package com.jaymospowers.client.power.chrono;

import com.jaymospowers.power.chrono.ChronoEcho;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.PlayerSkinRenderCache;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.item.ItemStack;

/**
 * Draws an echo as its owner: the player model (wide or slim, from the skin) with the owner's skin, resolved from the
 * synced profile through the same cache mannequins and player heads use, translucent and tinted pale cyan, with a
 * slow flicker. The held item is drawn normally (it's the one solid thing about it).
 */
public class ChronoEchoRenderer extends MobRenderer<ChronoEcho, AvatarRenderState, PlayerModel> {
	/** How much the alpha wavers, and how fast. */
	private static final float FLICKER = 0.18F;
	private static final float FLICKER_SPEED = 0.13F;

	private final PlayerModel wide;
	private final PlayerModel slim;
	private final PlayerSkinRenderCache skins;

	public ChronoEchoRenderer(EntityRendererProvider.Context context) {
		super(context, new PlayerModel(context.bakeLayer(ModelLayers.PLAYER), false), 0.35F);
		this.wide = this.model;
		this.slim = new PlayerModel(context.bakeLayer(ModelLayers.PLAYER_SLIM), true);
		this.skins = context.getPlayerSkinRenderCache();
		this.addLayer(new ItemInHandLayer<>(this));
	}

	@Override
	public AvatarRenderState createRenderState() {
		return new AvatarRenderState();
	}

	@Override
	public void extractRenderState(ChronoEcho entity, AvatarRenderState state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		HumanoidMobRenderer.extractHumanoidRenderState(entity, state, partialTicks, this.itemModelResolver);
		state.leftArmPose = armPose(entity, HumanoidArm.LEFT);
		state.rightArmPose = armPose(entity, HumanoidArm.RIGHT);
		state.skin = skinOf(entity);
		state.id = entity.getId();
	}

	/** The owner's skin as this client already shows it (tab list), else resolved from the synced profile. */
	private PlayerSkin skinOf(ChronoEcho echo) {
		ClientPacketListener connection = Minecraft.getInstance().getConnection();
		if (connection != null) {
			PlayerInfo info = echo.ownerId().map(connection::getPlayerInfo).orElse(null);
			if (info != null) {
				return info.getSkin();
			}
		}
		return this.skins.getOrDefault(echo.getProfile()).playerSkin();
	}

	private static HumanoidModel.ArmPose armPose(ChronoEcho echo, HumanoidArm arm) {
		ItemStack item = echo.getItemHeldByArm(arm);
		if (item.isEmpty()) {
			return HumanoidModel.ArmPose.EMPTY;
		}
		return HumanoidMobRenderer.usesSpearPose(item, arm, echo) ? HumanoidModel.ArmPose.SPEAR : HumanoidModel.ArmPose.ITEM;
	}

	@Override
	public void submit(AvatarRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		this.model = state.skin.model() == PlayerModelType.SLIM ? this.slim : this.wide;
		super.submit(state, poseStack, collector, camera);
	}

	@Override
	public Identifier getTextureLocation(AvatarRenderState state) {
		return state.skin.body().texturePath();
	}

	@Override
	protected void scale(AvatarRenderState state, PoseStack poseStack) {
		poseStack.scale(0.9375F, 0.9375F, 0.9375F); // as players are drawn
	}

	/** Pale cyan and half there, wavering a little (each echo out of step with the others). */
	@Override
	protected int getModelTint(AvatarRenderState state) {
		float wave = Mth.sin(state.ageInTicks * FLICKER_SPEED + state.id * 1.7F) * 0.5F + 0.5F;
		int baseAlpha = ARGB.alpha(ChronoEcho.TINT);
		int alpha = Mth.clamp(Math.round(baseAlpha * (1.0F - FLICKER * wave)), 0, 255);
		return ARGB.color(alpha, ARGB.red(ChronoEcho.TINT), ARGB.green(ChronoEcho.TINT), ARGB.blue(ChronoEcho.TINT));
	}
}
