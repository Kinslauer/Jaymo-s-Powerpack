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
import com.jaymoskingdoms.dragons.StegonautDragon;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;

/**
 * Stegonaut drawn by GeckoLib from our own {@code kingdoms/art/stegonaut.bbmodel} (animations:
 * {@code dragons/StegonautAnimations}), in its hide and glowing markings by its {@link DragonLook}, at the size it always
 * had. On top of the animation, as the old model did every frame: the neck and head turn and tip where it looks (less
 * as it dozes), the jaw drops at a breath, and its look and tack show or hide (long, short or twin horns, a broken
 * plate, the saddle, girth and pillion pad, which take the middle three plate pairs' places).
 */
final class StegonautDragonRenderer extends GeoEntityRenderer<StegonautDragon, LivingEntityRenderState> {
	/** Of where it looks: how much each neck segment and the head turn, and how much the head tips (as the old model). */
	static final float NECK_TURN = 0.22F, HEAD_TURN = 0.12F, HEAD_TIP = 0.4F;
	/** How far it looks (degrees): side to side, and up and down. */
	static final float LOOK_YAW = 50.0F, LOOK_PITCH = 30.0F;
	/** How wide the jaw opens at a breath (radians). */
	static final float JAW_OPEN = 0.4F;

	static final DataTicket<DragonLook> LOOK = DataTicket.create("jaymoskingdoms_stegonaut_look", DragonLook.class);
	static final DataTicket<Boolean> SADDLED = DataTicket.create("jaymoskingdoms_stegonaut_saddled", Boolean.class);
	static final DataTicket<Float> BREATH = DataTicket.create("jaymoskingdoms_stegonaut_breath", Float.class);
	/** How settled it is (sitting, eased): it dozes, and stops following with its head. */
	static final DataTicket<Float> DOZE = DataTicket.create("jaymoskingdoms_stegonaut_doze", Float.class);

	private final float scale, reach;

	StegonautDragonRenderer(EntityRendererProvider.Context context, float shadow, float scale, float reach) {
		super(context, new Model());
		this.scale = scale;
		this.reach = reach;
		this.shadowRadius = shadow;
		this.withRenderLayer(new Glow(this));
	}

	@Override
	public void addRenderData(StegonautDragon dragon, Void related, LivingEntityRenderState state, float partialTick) {
		super.addRenderData(dragon, related, state, partialTick);
		state.addGeckolibData(LOOK, dragon.look());
		state.addGeckolibData(SADDLED, dragon.saddled());
		state.addGeckolibData(BREATH, Mth.lerp(partialTick, dragon.breathAnimO, dragon.breathAnim));
		float crash = ease(Mth.lerp(partialTick, dragon.crashAnimO, dragon.crashAnim));
		float swim = ease(Mth.lerp(partialTick, dragon.swimAnimO, dragon.swimAnim)) * (1.0F - crash);
		float sit = ease(Mth.lerp(partialTick, dragon.sitAnimO, dragon.sitAnim)) * (1.0F - crash) * (1.0F - swim);
		state.addGeckolibData(DOZE, sit * sit);
	}

	private static float ease(float x) {
		x = Mth.clamp(x, 0.0F, 1.0F);
		return x * x * (3.0F - 2.0F * x);
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
		float awake = 1.0F - info.getOrDefaultGeckolibData(DOZE, 0.0F);
		float yaw = Mth.clamp(state.yRot, -LOOK_YAW, LOOK_YAW) * Mth.DEG_TO_RAD * awake;
		float tip = Mth.clamp(state.xRot, -LOOK_PITCH, LOOK_PITCH) * Mth.DEG_TO_RAD * awake;
		float breath = info.getOrDefaultGeckolibData(BREATH, 0.0F);
		// GeckoLib turns x and y the other way round from a ModelPart: these are the old model's angles, negated
		for (int i = 0; i < 4; i++) {
			snapshots.ifPresent("neck" + i, b -> b.setRotY(b.getRotY() - yaw * NECK_TURN));
		}
		snapshots.ifPresent("head", b -> b.setRotY(b.getRotY() - yaw * HEAD_TURN).setRotX(b.getRotX() - tip * HEAD_TIP));
		if (breath > 0.0F) {
			snapshots.ifPresent("jaw", b -> b.setRotX(b.getRotX() - breath * JAW_OPEN));
		}

		DragonLook look = info.getOrDefaultGeckolibData(LOOK, DragonLook.decode(0));
		boolean shortHorns = look.horns() == DragonLook.SHORT_HORNS;
		snapshots.ifPresent("horn_l", b -> b.skipRender(shortHorns));
		snapshots.ifPresent("horn_r", b -> b.skipRender(shortHorns));
		snapshots.ifPresent("short_horn_l", b -> b.skipRender(!shortHorns));
		snapshots.ifPresent("short_horn_r", b -> b.skipRender(!shortHorns));
		snapshots.ifPresent("nose_horn", b -> b.skipRender(look.horns() != DragonLook.TWIN_HORNS));
		boolean saddled = info.getOrDefaultGeckolibData(SADDLED, false);
		for (int i = 0; i < DragonLook.SPIKES; i++) {
			// the saddle and the pillion pad sit where the middle plates would
			boolean tack = saddled && (i == 1 || i == 2 || i == 3);
			boolean left = look.spike(i) && !tack;
			snapshots.ifPresent("plate" + i + "_l", b -> b.skipRender(!left));
			snapshots.ifPresent("plate" + i + "_r", b -> b.skipRender(tack));
		}
		for (String tack : new String[] {"saddle", "pommel", "cantle", "strap", "pad"}) {
			snapshots.ifPresent(tack, b -> b.skipRender(!saddled));
		}
	}

	@Override
	protected AABB getBoundingBoxForCulling(StegonautDragon dragon, float partialTicks) {
		double up = Math.max(WindDragonRenderer.REACH_UP, this.reach * 0.5);
		return dragon.getBoundingBox().inflate(this.reach * this.scale, up * this.scale, this.reach * this.scale);
	}

	private static final class Model extends DefaultedEntityGeoModel<StegonautDragon> {
		Model() {
			super(JaymosKingdoms.id("stegonaut"));
		}

		@Override
		public Identifier getTextureResource(GeoRenderState state) {
			return DragonLooks.hide("stegonaut", state.getOrDefaultGeckolibData(LOOK, DragonLook.decode(0)));
		}
	}

	/** Its markings and eyes, glowing (so they show in the dark). */
	private static final class Glow extends TextureLayerGeoLayer<StegonautDragon, Void, LivingEntityRenderState> {
		Glow(StegonautDragonRenderer renderer) {
			super(renderer, DragonLooks.marks("stegonaut", DragonLook.decode(0)), RenderTypes::eyes);
		}

		@Override
		protected Identifier getTextureResource(LivingEntityRenderState state) {
			return DragonLooks.marks("stegonaut", state.getOrDefaultGeckolibData(LOOK, DragonLook.decode(0)));
		}
	}
}
