package com.jaymoskingdoms.client.inhabitants.fts;

import com.geckolib.constant.dataticket.DataTicket;
import com.geckolib.animatable.client.GeoRenderProvider;
import com.geckolib.model.DefaultedEntityGeoModel;
import com.geckolib.model.DefaultedItemGeoModel;
import com.geckolib.renderer.GeoArmorRenderer;
import com.jaymoskingdoms.inhabitants.fts.PlagueDoctorMask;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import com.geckolib.renderer.base.BoneSnapshots;
import com.geckolib.renderer.base.GeoRenderState;
import com.geckolib.renderer.base.RenderPassInfo;
import com.geckolib.renderer.layer.builtin.TextureLayerGeoLayer;
import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.client.inhabitants.InhabitantRenderer;
import com.jaymoskingdoms.inhabitants.fts.Fts;
import com.jaymoskingdoms.inhabitants.fts.Froglin;
import com.jaymoskingdoms.inhabitants.fts.PlagueBearer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;

/**
 * The renderers of the mobs ported from From the Shadows Reborn (Sonmok14 and L_Ender, AGPL v3; their models, textures
 * and animations, see licenses/from-the-shadows/). The froglin is drawn in the colour its variant says; its vomit as a
 * slime ball.
 */
public final class FtsClient {
	static final float FROGLIN_SHADOW = 0.8F;
	static final float BEARER_SHADOW = 0.5F;
	static final DataTicket<Boolean> BEARER_ANGRY = DataTicket.create("jaymoskingdoms_plague_bearer_angry", Boolean.class);
	static final DataTicket<Integer> FROGLIN_VARIANT = DataTicket.create("jaymoskingdoms_froglin_variant", Integer.class);
	static final Identifier[] FROGLIN_SKINS = {JaymosKingdoms.id("textures/entity/froglin/froglin.png"),
		JaymosKingdoms.id("textures/entity/froglin/froglin_red.png"), JaymosKingdoms.id("textures/entity/froglin/froglin_gray.png")};

	private FtsClient() {
	}

	public static void register() {
		EntityRenderers.register(Fts.FROGLIN, FroglinRenderer::new);
		EntityRenderers.register(Fts.FROG_VOMIT, ThrownItemRenderer::new);
		EntityRenderers.register(Fts.PLAGUE_BEARER, PlagueBearerRenderer::new);
		EntityRenderers.register(Fts.PLAGUE_KNIFE, ThrownItemRenderer::new);
		FtsGearClient.register();
		// the mask, worn, is the original's 3D model drawn by GeckoLib's armour renderer
		PlagueDoctorMask.PROVIDER = new GeoRenderProvider() {
			private GeoArmorRenderer<?, ?> renderer;

			@Override
			public GeoArmorRenderer<?, ?> getGeoArmorRenderer(ItemStack stack, EquipmentSlot slot) {
				if (this.renderer == null) {
					this.renderer = new GeoArmorRenderer<PlagueDoctorMask, HumanoidRenderState>(new MaskModel());
				}
				return this.renderer;
			}
		};
	}

	/** The mask's model and animations by name (geckolib/models/item/plague_doctor_mask.geo.json), its texture the original's armour sheet. */
	static final class MaskModel extends DefaultedItemGeoModel<PlagueDoctorMask> {
		MaskModel() {
			super(JaymosKingdoms.id("plague_doctor_mask"));
		}

		@Override
		public Identifier getTextureResource(GeoRenderState state) {
			return JaymosKingdoms.id("textures/armor/plague_doctor_mask.png");
		}
	}

	static final class PlagueBearerRenderer extends InhabitantRenderer<PlagueBearer> {
		PlagueBearerRenderer(EntityRendererProvider.Context context) {
			super(context, "plague_bearer", 1.0F, BEARER_SHADOW, "head");
			this.withRenderLayer(new TextureLayerGeoLayer<>(this, JaymosKingdoms.id("textures/entity/plague_bearer_glow.png"), RenderTypes::eyes));
		}

		@Override
		public void addRenderData(PlagueBearer bearer, Void related, LivingEntityRenderState state, float partialTick) {
			super.addRenderData(bearer, related, state, partialTick);
			state.addGeckolibData(BEARER_ANGRY, bearer.angry());
		}

		/** His arms folded while he is calm, out when he fights. */
		@Override
		public void adjustModelBonesForRender(RenderPassInfo<LivingEntityRenderState> info, BoneSnapshots snapshots) {
			super.adjustModelBonesForRender(info, snapshots);
			boolean angry = info.getOrDefaultGeckolibData(BEARER_ANGRY, false);
			snapshots.ifPresent("arms", bone -> bone.skipRender(angry));
			snapshots.ifPresent("left_arm", bone -> bone.skipRender(!angry));
			snapshots.ifPresent("right_arm", bone -> bone.skipRender(!angry));
		}
	}

	static final class FroglinRenderer extends InhabitantRenderer<Froglin> {
		FroglinRenderer(EntityRendererProvider.Context context) {
			super(context, new DefaultedEntityGeoModel<Froglin>(JaymosKingdoms.id("froglin")) {
				@Override
				public Identifier getTextureResource(GeoRenderState state) {
					int variant = state.getOrDefaultGeckolibData(FROGLIN_VARIANT, 0);
					return FROGLIN_SKINS[Math.max(0, Math.min(FROGLIN_SKINS.length - 1, variant))];
				}
			}, 1.0F, FROGLIN_SHADOW, "head");
		}

		@Override
		public void addRenderData(Froglin froglin, Void related, LivingEntityRenderState state, float partialTick) {
			super.addRenderData(froglin, related, state, partialTick);
			state.addGeckolibData(FROGLIN_VARIANT, froglin.variant());
		}
	}
}
