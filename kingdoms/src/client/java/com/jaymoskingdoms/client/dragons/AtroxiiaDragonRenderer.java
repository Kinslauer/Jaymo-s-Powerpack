package com.jaymoskingdoms.client.dragons;

import com.geckolib.constant.dataticket.DataTicket;
import com.geckolib.model.DefaultedEntityGeoModel;
import com.geckolib.renderer.GeoEntityRenderer;
import com.geckolib.renderer.base.BoneSnapshots;
import com.geckolib.renderer.base.GeoRenderState;
import com.geckolib.renderer.base.RenderPassInfo;
import com.geckolib.renderer.layer.builtin.TextureLayerGeoLayer;
import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.dragons.AtroxiiaDragon;
import com.jaymoskingdoms.dragons.DragonLook;
import com.jaymoskingdoms.dragons.WindDragon;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;

/**
 * Atroxiia drawn by GeckoLib from our own {@code atroxiia.bbmodel}. Its hide and glowing markings still come from
 * {@link DragonLook}; headdress, spine and tack visibility, rider seats, adult/hatchling scale and culling reach are
 * unchanged. Keyframes carry the old body/limb poses; this adds the old per-frame head and neck look.
 */
final class AtroxiiaDragonRenderer extends GeoEntityRenderer<AtroxiiaDragon, LivingEntityRenderState> {
	static final float NECK_TURN = 0.2F, HEAD_TURN = 0.3F, HEAD_TIP = 0.5F;
	static final float LOOK_YAW = 60.0F, LOOK_PITCH = 35.0F;
	static final DataTicket<DragonLook> LOOK = DataTicket.create("jaymoskingdoms_atroxiia_look", DragonLook.class);
	static final DataTicket<Boolean> SADDLED = DataTicket.create("jaymoskingdoms_atroxiia_saddled", Boolean.class);

	private final float scale, reach;

	AtroxiiaDragonRenderer(EntityRendererProvider.Context context, float shadow, float scale, float reach) {
		super(context, new Model());
		this.scale = scale;
		this.reach = reach;
		this.shadowRadius = shadow;
		this.withRenderLayer(new Glow(this));
	}

	@Override
	public void addRenderData(AtroxiiaDragon dragon, Void related, LivingEntityRenderState state, float partialTick) {
		super.addRenderData(dragon, related, state, partialTick);
		state.addGeckolibData(LOOK, dragon.look());
		state.addGeckolibData(SADDLED, dragon.saddled());
	}

	@Override
	public void scaleModelForRender(RenderPassInfo<LivingEntityRenderState> info, float widthScale, float heightScale) {
		float s = this.scale * (info.renderState().isBaby ? WindDragonRenderer.HATCHLING_SCALE : 1.0F);
		super.scaleModelForRender(info, widthScale * s, heightScale * s);
	}

	@Override
	public void adjustModelBonesForRender(RenderPassInfo<LivingEntityRenderState> info, BoneSnapshots snapshots) {
		super.adjustModelBonesForRender(info, snapshots);
		LivingEntityRenderState state = info.renderState();
		float yaw = Mth.clamp(state.yRot, -LOOK_YAW, LOOK_YAW) * Mth.DEG_TO_RAD;
		float tip = Mth.clamp(state.xRot, -LOOK_PITCH, LOOK_PITCH) * Mth.DEG_TO_RAD;
		// GeckoLib turns x and y the other way round from a ModelPart.
		for (int i = 0; i < 3; i++) {
			snapshots.ifPresent("neck" + i, b -> b.setRotY(b.getRotY() - yaw * NECK_TURN));
		}
		snapshots.ifPresent("head", b -> b.setRotY(b.getRotY() - yaw * HEAD_TURN).setRotX(b.getRotX() - tip * HEAD_TIP));

		DragonLook look = info.getOrDefaultGeckolibData(LOOK, DragonLook.decode(0));
		boolean shortNemes = look.horns() == DragonLook.SHORT_HORNS;
		snapshots.ifPresent("nemes_l", b -> b.skipRender(shortNemes));
		snapshots.ifPresent("nemes_r", b -> b.skipRender(shortNemes));
		snapshots.ifPresent("short_nemes_l", b -> b.skipRender(!shortNemes));
		snapshots.ifPresent("short_nemes_r", b -> b.skipRender(!shortNemes));
		snapshots.ifPresent("uraeus", b -> b.skipRender(look.horns() != DragonLook.TWIN_HORNS));
		boolean saddled = info.getOrDefaultGeckolibData(SADDLED, false);
		for (int i = 0; i < DragonLook.SPIKES; i++) {
			boolean hide = !look.spike(i) || saddled && i == 0;
			snapshots.ifPresent("spine" + i, b -> b.skipRender(hide));
		}
		snapshots.ifPresent("saddle", b -> b.skipRender(!saddled));
		snapshots.ifPresent("strap", b -> b.skipRender(!saddled));
		snapshots.ifPresent("pad", b -> b.skipRender(!saddled));
	}

	@Override
	protected AABB getBoundingBoxForCulling(AtroxiiaDragon dragon, float partialTicks) {
		double up = Math.max(WindDragonRenderer.REACH_UP, this.reach * 0.5);
		return dragon.getBoundingBox().inflate(this.reach * this.scale, up * this.scale, this.reach * this.scale);
	}

	private static final class Model extends DefaultedEntityGeoModel<AtroxiiaDragon> {
		Model() {
			super(JaymosKingdoms.id("atroxiia"));
		}

		@Override
		public Identifier getTextureResource(GeoRenderState state) {
			return DragonLooks.hide("atroxiia", state.getOrDefaultGeckolibData(LOOK, DragonLook.decode(0)));
		}
	}

	private static final class Glow extends TextureLayerGeoLayer<AtroxiiaDragon, Void, LivingEntityRenderState> {
		Glow(AtroxiiaDragonRenderer renderer) {
			super(renderer, DragonLooks.marks("atroxiia", DragonLook.decode(0)), RenderTypes::eyes);
		}

		@Override
		protected Identifier getTextureResource(LivingEntityRenderState state) {
			return DragonLooks.marks("atroxiia", state.getOrDefaultGeckolibData(LOOK, DragonLook.decode(0)));
		}
	}
}
