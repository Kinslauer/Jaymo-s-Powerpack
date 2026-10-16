package com.jaymospowers.client.power.zfighter;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.power.zfighter.ZFighterRogue;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.ArmorModelSet;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;

/**
 * Rogue fighters on the player model with their own skins (a tyrant's dark armour, a champion's blue gi). A tyrant
 * wears Super hair, gold, paler and taller in Super 2.
 */
public class ZFighterRogueRenderer extends MobRenderer<ZFighterRogue, AvatarRenderState, PlayerModel> {
	private static final Identifier TYRANT = JaymosPowers.id("textures/entity/rogue_fighter/tyrant.png");
	private static final Identifier CHAMPION = JaymosPowers.id("textures/entity/rogue_fighter/champion.png");
	/*
	 * Also as the render state's skin: other mods' player-model renderers (and the model swap) draw from that, not
	 * getTextureLocation, and its default is Steve.
	 */
	private static final PlayerSkin TYRANT_SKIN = skin(TYRANT), CHAMPION_SKIN = skin(CHAMPION);
	static final int SUPER_HAIR = 0xFFD030;
	static final int SUPER_2_HAIR = 0xFFF090;

	public ZFighterRogueRenderer(EntityRendererProvider.Context context) {
		super(context, new PlayerModel(context.bakeLayer(ModelLayers.PLAYER), false), 0.5F);
		this.addLayer(new ZFighterHair.Layer(this));
		this.addLayer(new HumanoidArmorLayer<>(this, ArmorModelSet.bake(ModelLayers.PLAYER_ARMOR, context.getModelSet(), HumanoidModel::new),
			context.getEquipmentRenderer()));
		this.addLayer(new ItemInHandLayer<>(this));
	}

	private static PlayerSkin skin(Identifier texture) {
		ClientAsset.ResourceTexture body = new ClientAsset.ResourceTexture(texture, texture);
		return PlayerSkin.insecure(body, null, null, PlayerModelType.WIDE);
	}

	@Override
	public AvatarRenderState createRenderState() {
		return new AvatarRenderState();
	}

	@Override
	public void extractRenderState(ZFighterRogue entity, AvatarRenderState state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		HumanoidMobRenderer.extractHumanoidRenderState(entity, state, partialTicks, this.itemModelResolver);
		state.id = entity.getId();
		state.setData(ZFighterRogueRenderer.KIND, entity.kind());
		state.skin = entity.kind() == ZFighterRogue.TYRANT ? TYRANT_SKIN : CHAMPION_SKIN;
		int form = entity.form();
		state.setData(ZFighterHair.KEY, form <= 0 ? null : new ZFighterHair.Hair(form, form >= 2 ? SUPER_2_HAIR : SUPER_HAIR,
			ZFighterHair.style(entity.getUUID())));
	}

	static final net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey<Integer> KIND =
		net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey.create(() -> "jaymospowers:rogue_kind");

	@Override
	public Identifier getTextureLocation(AvatarRenderState state) {
		Integer kind = state.getData(KIND);
		return kind != null && kind == ZFighterRogue.TYRANT ? TYRANT : CHAMPION;
	}

	@Override
	protected void scale(AvatarRenderState state, PoseStack poseStack) {
		Integer kind = state.getData(KIND);
		float s = kind != null && kind == ZFighterRogue.TYRANT ? 1.1F : 0.9375F;
		poseStack.scale(s, s, s);
	}
}
