package com.jaymoskingdoms.client.dragons;

import com.geckolib.constant.dataticket.DataTicket;
import com.geckolib.model.DefaultedEntityGeoModel;
import com.geckolib.renderer.GeoEntityRenderer;
import com.geckolib.renderer.base.BoneSnapshots;
import com.geckolib.renderer.base.GeoRenderState;
import com.geckolib.renderer.base.RenderPassInfo;
import com.geckolib.renderer.layer.builtin.TextureLayerGeoLayer;
import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.dragons.AetherionDragon;
import com.jaymoskingdoms.dragons.DragonLook;
import com.jaymoskingdoms.dragons.WindDragon;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;

/**
 * Aetherion drawn by GeckoLib from our own {@code aetherion.bbmodel}. Its hide and glowing markings still come from
 * {@link DragonLook}; horn, spine and saddle visibility are unchanged. The keyed animations carry the body and limbs;
 * this adds where it looks, its pitch with a climb or dive, and its jaw while breathing, as the old model did.
 */
final class AetherionDragonRenderer extends GeoEntityRenderer<AetherionDragon, LivingEntityRenderState> {
	/** Its look is shared over four neck joints and the head; the head takes half its pitch. */
	static final float NECK_TURN = 0.2F, HEAD_TURN = 0.2F, HEAD_TIP = 0.5F;
	static final float LOOK_YAW = 50.0F, LOOK_PITCH = 35.0F;
	/** The old jaw's extra opening while the starlight beam is breathed (radians). */
	static final float BREATH_JAW = 0.65F;

	static final DataTicket<DragonLook> LOOK = DataTicket.create("jaymoskingdoms_aetherion_look", DragonLook.class);
	static final DataTicket<Float> FLY = DataTicket.create("jaymoskingdoms_aetherion_fly", Float.class);
	static final DataTicket<Float> BREATH = DataTicket.create("jaymoskingdoms_aetherion_breath", Float.class);
	static final DataTicket<Float> PITCH = DataTicket.create("jaymoskingdoms_aetherion_pitch", Float.class);
	static final DataTicket<Boolean> SADDLED = DataTicket.create("jaymoskingdoms_aetherion_saddled", Boolean.class);

	private final float scale, reach;

	AetherionDragonRenderer(EntityRendererProvider.Context context, float shadow, float scale, float reach) {
		super(context, new Model());
		this.scale = scale;
		this.reach = reach;
		this.shadowRadius = shadow;
		this.withRenderLayer(new Glow(this));
	}

	@Override
	public void addRenderData(AetherionDragon dragon, Void related, LivingEntityRenderState state, float partialTick) {
		super.addRenderData(dragon, related, state, partialTick);
		state.addGeckolibData(LOOK, dragon.look());
		state.addGeckolibData(FLY, Mth.lerp(partialTick, dragon.flyAnimO, dragon.flyAnim));
		state.addGeckolibData(BREATH, Mth.lerp(partialTick, dragon.breathAnimO, dragon.breathAnim));
		state.addGeckolibData(PITCH, Mth.lerp(partialTick, dragon.pitchAnimO, dragon.pitchAnim));
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
		float fly = info.getOrDefaultGeckolibData(FLY, 0.0F);
		float pitch = info.getOrDefaultGeckolibData(PITCH, 0.0F);
		float yaw = Mth.clamp(state.yRot, -LOOK_YAW, LOOK_YAW) * Mth.DEG_TO_RAD;
		float tip = Mth.clamp(state.xRot, -LOOK_PITCH, LOOK_PITCH) * Mth.DEG_TO_RAD;
		// GeckoLib turns x and y the other way round from a ModelPart: the old model's angles are negated here.
		for (int i = 0; i < 4; i++) {
			snapshots.ifPresent("neck" + i, b -> b.setRotY(b.getRotY() - yaw * NECK_TURN)
				.setRotX(b.getRotX() + pitch * fly * 0.25F));
		}
		snapshots.ifPresent("head", b -> b.setRotY(b.getRotY() - yaw * HEAD_TURN).setRotX(b.getRotX() - tip * HEAD_TIP));
		snapshots.ifPresent("body", b -> b.setRotX(b.getRotX() - pitch * fly));
		float breath = info.getOrDefaultGeckolibData(BREATH, 0.0F);
		if (breath > 0.0F) {
			snapshots.ifPresent("jaw", b -> b.setRotX(b.getRotX() - breath * BREATH_JAW));
		}

		DragonLook look = info.getOrDefaultGeckolibData(LOOK, DragonLook.decode(0));
		boolean shortHorns = look.horns() == DragonLook.SHORT_HORNS;
		snapshots.ifPresent("horn_tip_l", b -> b.skipRender(shortHorns));
		snapshots.ifPresent("horn_tip_r", b -> b.skipRender(shortHorns));
		boolean twin = look.horns() == DragonLook.TWIN_HORNS;
		snapshots.ifPresent("low_horn_l", b -> b.skipRender(!twin));
		snapshots.ifPresent("low_horn_r", b -> b.skipRender(!twin));
		boolean saddled = info.getOrDefaultGeckolibData(SADDLED, false);
		for (int i = 0; i < DragonLook.SPIKES; i++) {
			boolean hide = !look.spike(i) || saddled && i >= 1 && i <= 3;
			snapshots.ifPresent("spine" + i, b -> b.skipRender(hide));
		}
		snapshots.ifPresent("saddle", b -> b.skipRender(!saddled));
		snapshots.ifPresent("strap", b -> b.skipRender(!saddled));
	}

	@Override
	protected AABB getBoundingBoxForCulling(AetherionDragon dragon, float partialTicks) {
		double up = Math.max(WindDragonRenderer.REACH_UP, this.reach * 0.5);
		return dragon.getBoundingBox().inflate(this.reach * this.scale, up * this.scale, this.reach * this.scale);
	}

	/** The editable model and its animations, textured with the rolled hide. */
	private static final class Model extends DefaultedEntityGeoModel<AetherionDragon> {
		Model() {
			super(JaymosKingdoms.id("aetherion"));
		}

		@Override
		public Identifier getTextureResource(GeoRenderState state) {
			return DragonLooks.hide("aetherion", state.getOrDefaultGeckolibData(LOOK, DragonLook.decode(0)));
		}
	}

	/** Its marking pattern and star-white eyes over the hide, glowing as before. */
	private static final class Glow extends TextureLayerGeoLayer<AetherionDragon, Void, LivingEntityRenderState> {
		Glow(AetherionDragonRenderer renderer) {
			super(renderer, DragonLooks.marks("aetherion", DragonLook.decode(0)), RenderTypes::eyes);
		}

		@Override
		protected Identifier getTextureResource(LivingEntityRenderState state) {
			return DragonLooks.marks("aetherion", state.getOrDefaultGeckolibData(LOOK, DragonLook.decode(0)));
		}
	}
}
