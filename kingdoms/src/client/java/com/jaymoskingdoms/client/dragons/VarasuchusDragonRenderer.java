package com.jaymoskingdoms.client.dragons;

import com.geckolib.constant.dataticket.DataTicket;
import com.geckolib.model.DefaultedEntityGeoModel;
import com.geckolib.renderer.GeoEntityRenderer;
import com.geckolib.renderer.base.BoneSnapshots;
import com.geckolib.renderer.base.GeoRenderState;
import com.geckolib.renderer.base.RenderPassInfo;
import com.geckolib.renderer.layer.builtin.TextureLayerGeoLayer;
import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.dragons.DragonLook;
import com.jaymoskingdoms.dragons.VarasuchusDragon;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;

/**
 * Varasuchus drawn by GeckoLib from our own {@code varasuchus.bbmodel} (its animations: {@code dragons/VarasuchusAnimations}).
 * Its hide and glowing markings come from its {@link DragonLook} as before, and so do its horns (long, short, or long with
 * a nose horn) and its scutes; the middle three pairs of scutes give way to the saddle. Its size, the hatchling's,
 * its reach and its riders' seats are unchanged. On top of the animation it turns its neck and head where it looks
 * and opens its jaws as it breathes, as the old model did.
 */
final class VarasuchusDragonRenderer extends GeoEntityRenderer<VarasuchusDragon, LivingEntityRenderState> {
	/** Of where it looks, how much each neck bone and the head turn, and how much the head tips; how far it looks (degrees). */
	static final float NECK_TURN = 0.3F, HEAD_TURN = 0.3F, HEAD_TIP = 0.5F, LOOK_YAW = 45.0F, LOOK_PITCH = 30.0F;
	/** Breathing: how far its jaw opens (radians). */
	static final float JAW_OPEN = 0.4F;
	static final DataTicket<DragonLook> LOOK = DataTicket.create("jaymoskingdoms_varasuchus_look", DragonLook.class);
	static final DataTicket<Boolean> SADDLED = DataTicket.create("jaymoskingdoms_varasuchus_saddled", Boolean.class);
	static final DataTicket<Float> BREATH = DataTicket.create("jaymoskingdoms_varasuchus_breath", Float.class);
	static final DataTicket<Boolean> DEAD = DataTicket.create("jaymoskingdoms_varasuchus_dead", Boolean.class);

	private final float scale, reach;

	VarasuchusDragonRenderer(EntityRendererProvider.Context context, float shadow, float scale, float reach) {
		super(context, new Model());
		this.scale = scale;
		this.reach = reach;
		this.shadowRadius = shadow;
		this.withRenderLayer(new Glow(this));
	}

	@Override
	public void addRenderData(VarasuchusDragon dragon, Void related, LivingEntityRenderState state, float partialTick) {
		super.addRenderData(dragon, related, state, partialTick);
		state.addGeckolibData(LOOK, dragon.look());
		state.addGeckolibData(SADDLED, dragon.saddled());
		state.addGeckolibData(BREATH, Mth.lerp(partialTick, dragon.breathAnimO, dragon.breathAnim));
		state.addGeckolibData(DEAD, dragon.deathTime > 0);
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
		boolean dead = info.getOrDefaultGeckolibData(DEAD, false);
		float yaw = dead ? 0.0F : Mth.clamp(state.yRot, -LOOK_YAW, LOOK_YAW) * Mth.DEG_TO_RAD;
		float tip = Mth.clamp(state.xRot, -LOOK_PITCH, LOOK_PITCH) * Mth.DEG_TO_RAD;
		float jaw = info.getOrDefaultGeckolibData(BREATH, 0.0F) * JAW_OPEN;
		// GeckoLib turns x and y the other way round from a ModelPart
		snapshots.ifPresent("neck0", b -> b.setRotY(b.getRotY() - yaw * NECK_TURN));
		snapshots.ifPresent("neck1", b -> b.setRotY(b.getRotY() - yaw * NECK_TURN));
		snapshots.ifPresent("head", b -> b.setRotY(b.getRotY() - yaw * HEAD_TURN).setRotX(b.getRotX() - tip * HEAD_TIP));
		if (jaw > 0.0F) {
			snapshots.ifPresent("jaw", b -> b.setRotX(b.getRotX() - jaw));
		}

		DragonLook look = info.getOrDefaultGeckolibData(LOOK, DragonLook.decode(0));
		boolean shortHorns = look.horns() == DragonLook.SHORT_HORNS;
		for (String bone : new String[] {"horn_l", "horn_r", "horn_tip_l", "horn_tip_r"}) {
			snapshots.ifPresent(bone, b -> b.skipRender(shortHorns));
		}
		snapshots.ifPresent("short_horn_l", b -> b.skipRender(!shortHorns));
		snapshots.ifPresent("short_horn_r", b -> b.skipRender(!shortHorns));
		snapshots.ifPresent("nose_horn", b -> b.skipRender(look.horns() != DragonLook.TWIN_HORNS));
		boolean saddled = info.getOrDefaultGeckolibData(SADDLED, false);
		for (int i = 0; i < DragonLook.SPIKES; i++) {
			boolean tack = saddled && i >= 1 && i <= 3;
			boolean left = look.spike(i) && !tack;
			snapshots.ifPresent("scute" + i + "_l", b -> b.skipRender(!left));
			snapshots.ifPresent("scute" + i + "_r", b -> b.skipRender(tack));
		}
		for (String bone : new String[] {"saddle", "pommel", "cantle", "strap", "pad"}) {
			snapshots.ifPresent(bone, b -> b.skipRender(!saddled));
		}
	}

	@Override
	protected AABB getBoundingBoxForCulling(VarasuchusDragon dragon, float partialTicks) {
		double up = Math.max(WindDragonRenderer.REACH_UP, this.reach * 0.5);
		return dragon.getBoundingBox().inflate(this.reach * this.scale, up * this.scale, this.reach * this.scale);
	}

	private static final class Model extends DefaultedEntityGeoModel<VarasuchusDragon> {
		Model() {
			super(JaymosKingdoms.id("varasuchus"));
		}

		@Override
		public Identifier getTextureResource(GeoRenderState state) {
			return DragonLooks.hide("varasuchus", state.getOrDefaultGeckolibData(LOOK, DragonLook.decode(0)));
		}
	}

	private static final class Glow extends TextureLayerGeoLayer<VarasuchusDragon, Void, LivingEntityRenderState> {
		Glow(VarasuchusDragonRenderer renderer) {
			super(renderer, DragonLooks.marks("varasuchus", DragonLook.decode(0)), RenderTypes::eyes);
		}

		@Override
		protected Identifier getTextureResource(LivingEntityRenderState state) {
			return DragonLooks.marks("varasuchus", state.getOrDefaultGeckolibData(LOOK, DragonLook.decode(0)));
		}
	}
}
