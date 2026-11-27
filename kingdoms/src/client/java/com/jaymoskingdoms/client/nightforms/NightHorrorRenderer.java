package com.jaymoskingdoms.client.nightforms;

import com.geckolib.constant.dataticket.DataTicket;
import com.geckolib.model.DefaultedEntityGeoModel;
import com.geckolib.renderer.GeoEntityRenderer;
import com.geckolib.renderer.base.GeoRenderState;
import com.geckolib.renderer.layer.builtin.TextureLayerGeoLayer;
import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.nightforms.NightHorror;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;

/**
 * Draws the true shape of the night ({@link NightHorror}) with GeckoLib: our own gaunt, winged model
 * ({@code geckolib/models/entity/night_horror.geo.json}) in one of five skins ({@code textures/entity/night_horror*.png}), the
 * one it wears picked by who wears it (the same player always has the same skin), with its eyes and veins drawn over it in
 * the dark ({@code night_horror_glow.png}). The wearer does not see it from inside in first person.
 */
public final class NightHorrorRenderer extends GeoEntityRenderer<NightHorror, LivingEntityRenderState> {
	static final float SHADOW = 0.9F;
	static final String[] SKINS = {"night_horror", "night_horror_pale", "night_horror_dusk", "night_horror_russet", "night_horror_ebon"};
	static final DataTicket<Integer> SKIN = DataTicket.create("jaymoskingdoms_night_horror_skin", Integer.class);

	public NightHorrorRenderer(EntityRendererProvider.Context context) {
		super(context, new Model());
		this.shadowRadius = SHADOW;
		this.withRenderLayer(new Glow(this));
	}

	/** Which of the skins the player with entity id {@code host} wears (their id's own, so it stays the same). */
	private static int skinOf(int host) {
		Entity wearer = Minecraft.getInstance().level == null ? null : Minecraft.getInstance().level.getEntity(host);
		int key = wearer == null ? host : wearer.getUUID().hashCode();
		return Math.floorMod(key, SKINS.length);
	}

	private static Identifier skin(GeoRenderState state) {
		int i = Math.floorMod(state.getOrDefaultGeckolibData(SKIN, 0), SKINS.length);
		return JaymosKingdoms.id("textures/entity/" + SKINS[i] + ".png");
	}

	/** The one model, in the skin the wearer's. */
	static final class Model extends DefaultedEntityGeoModel<NightHorror> {
		Model() {
			super(JaymosKingdoms.id("night_horror"));
		}

		@Override
		public Identifier getTextureResource(GeoRenderState state) {
			return skin(state);
		}
	}

	/** Its eyes and veins, bright in the dark. */
	private static final class Glow extends TextureLayerGeoLayer<NightHorror, Void, LivingEntityRenderState> {
		Glow(NightHorrorRenderer renderer) {
			super(renderer, JaymosKingdoms.id("textures/entity/night_horror_glow.png"), RenderTypes::eyes);
		}
	}

	@Override
	public void addRenderData(NightHorror horror, Void related, LivingEntityRenderState state, float partialTick) {
		super.addRenderData(horror, related, state, partialTick);
		state.addGeckolibData(SKIN, skinOf(horror.host()));
	}

	@Override
	public boolean shouldRender(NightHorror horror, Frustum frustum, double x, double y, double z, float partialTick) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player != null && horror.host() == minecraft.player.getId() && minecraft.options.getCameraType().isFirstPerson()) {
			return false;
		}
		return super.shouldRender(horror, frustum, x, y, z, partialTick);
	}
}
