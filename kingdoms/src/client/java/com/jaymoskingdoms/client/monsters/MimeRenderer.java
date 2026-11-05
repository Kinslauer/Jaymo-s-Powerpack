package com.jaymoskingdoms.client.monsters;

import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.monsters.Mime;
import com.jaymoskingdoms.monsters.Monsters;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.ArmorModelSet;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.EntityRenderers;
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
 * (0.7.580) The mime on the player model in its own pale skin, with the armour and things in hand it copied. The skin
 * is also set on the render state: 26.3 draws every avatar state with the player renderer, which reads it from there.
 */
public class MimeRenderer extends MobRenderer<Mime, AvatarRenderState, PlayerModel> {
	static final Identifier TEXTURE = JaymosKingdoms.id("textures/entity/mime.png");
	static final PlayerSkin SKIN = PlayerSkin.insecure(new ClientAsset.ResourceTexture(TEXTURE, TEXTURE), null, null, PlayerModelType.SLIM);

	public MimeRenderer(EntityRendererProvider.Context context) {
		super(context, new PlayerModel(context.bakeLayer(ModelLayers.PLAYER_SLIM), true), 0.45F);
		this.addLayer(new HumanoidArmorLayer<>(this, ArmorModelSet.bake(ModelLayers.PLAYER_ARMOR, context.getModelSet(), HumanoidModel::new),
			context.getEquipmentRenderer()));
		this.addLayer(new ItemInHandLayer<>(this));
	}

	public static void register() {
		EntityRenderers.register(Monsters.MIME, MimeRenderer::new);
	}

	@Override
	public AvatarRenderState createRenderState() {
		return new AvatarRenderState();
	}

	@Override
	public void extractRenderState(Mime entity, AvatarRenderState state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		HumanoidMobRenderer.extractHumanoidRenderState(entity, state, partialTicks, this.itemModelResolver);
		state.id = entity.getId();
		state.skin = SKIN;
	}

	@Override
	public Identifier getTextureLocation(AvatarRenderState state) {
		return TEXTURE;
	}
}
