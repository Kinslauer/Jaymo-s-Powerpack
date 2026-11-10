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
import com.jaymoskingdoms.dragons.ZephyrusDragon;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;

/**
 * Zephyrus drawn by GeckoLib from our own {@code zephyrus.bbmodel} (the old ZephyrusDragonModel's body, box for box;
 * {@code dragons/ZephyrusAnimations} picks its animations), in its hide by its {@link DragonLook} with its glowing
 * markings and eyes over it. Its horns, lost spikes and tack show as before. On top of the animation, as the old model
 * did every frame: its neck and head turn where it looks (the head tips with it unless crashed), its body pitches with a
 * climb or a dive, and breathing it drops its jaw, working it, and thrusts its neck out.
 */
final class ZephyrusDragonRenderer extends GeoEntityRenderer<ZephyrusDragon, LivingEntityRenderState> {
	/** Of where it looks, how much each neck segment and the head turn, and how much the head tips. */
	static final float NECK_TURN = 0.25F, HEAD_TURN = 0.25F, HEAD_TIP = 0.5F;
	/** How far it looks (degrees): side to side, and up and down. */
	static final float LOOK_YAW = 60.0F, LOOK_UP = -40.0F, LOOK_DOWN = 60.0F;
	/** Breathing: how wide the jaw opens and works (radians), how the head dips and the neck thrusts (on the ground). */
	static final float JAW_OPEN = 0.65F, JAW_WORK = 0.05F, JAW_WORK_SPEED = 0.6F, HEAD_DIP = 0.2F, NECK_THRUST = 0.15F, NECK_BEND = 0.05F;

	static final DataTicket<DragonLook> LOOK = DataTicket.create("jaymoskingdoms_zephyrus_look", DragonLook.class);
	static final DataTicket<Float> FLY = DataTicket.create("jaymoskingdoms_zephyrus_fly", Float.class);
	static final DataTicket<Float> CRASH = DataTicket.create("jaymoskingdoms_zephyrus_crash", Float.class);
	static final DataTicket<Float> BREATH = DataTicket.create("jaymoskingdoms_zephyrus_breath", Float.class);
	static final DataTicket<Float> PITCH = DataTicket.create("jaymoskingdoms_zephyrus_pitch", Float.class);
	static final DataTicket<Boolean> SADDLED = DataTicket.create("jaymoskingdoms_zephyrus_saddled", Boolean.class);

	ZephyrusDragonRenderer(EntityRendererProvider.Context context, float shadow) {
		super(context, new Model());
		this.shadowRadius = shadow;
		this.withRenderLayer(new Glow(this));
	}

	@Override
	public void addRenderData(ZephyrusDragon dragon, Void related, LivingEntityRenderState state, float partialTick) {
		super.addRenderData(dragon, related, state, partialTick);
		state.addGeckolibData(LOOK, dragon.look());
		state.addGeckolibData(FLY, Mth.lerp(partialTick, dragon.flyAnimO, dragon.flyAnim));
		state.addGeckolibData(CRASH, Mth.lerp(partialTick, dragon.crashAnimO, dragon.crashAnim));
		state.addGeckolibData(BREATH, Mth.lerp(partialTick, dragon.breathAnimO, dragon.breathAnim));
		state.addGeckolibData(PITCH, Mth.lerp(partialTick, dragon.pitchAnimO, dragon.pitchAnim));
		state.addGeckolibData(SADDLED, dragon.saddled());
	}

	@Override
	public void scaleModelForRender(RenderPassInfo<LivingEntityRenderState> info, float widthScale, float heightScale) {
		float s = info.renderState().isBaby ? WindDragonRenderer.HATCHLING_SCALE : 1.0F;
		super.scaleModelForRender(info, widthScale * s, heightScale * s);
	}

	@Override
	public void adjustModelBonesForRender(RenderPassInfo<LivingEntityRenderState> info, BoneSnapshots snapshots) {
		super.adjustModelBonesForRender(info, snapshots);
		LivingEntityRenderState state = info.renderState();
		float fly = info.getOrDefaultGeckolibData(FLY, 0.0F);
		float crash = info.getOrDefaultGeckolibData(CRASH, 0.0F) * (1.0F - fly);
		float breath = info.getOrDefaultGeckolibData(BREATH, 0.0F);
		float pitch = info.getOrDefaultGeckolibData(PITCH, 0.0F);
		float yaw = Mth.clamp(state.yRot, -LOOK_YAW, LOOK_YAW) * Mth.DEG_TO_RAD;
		float tip = Mth.clamp(state.xRot, LOOK_UP, LOOK_DOWN) * Mth.DEG_TO_RAD;
		float age = (float) state.getAnimatableAge();
		// GeckoLib turns x and y the other way round from a ModelPart: these are the old model's angles, negated
		for (int i = 0; i < 3; i++) {
			float thrust = breath * (i == 0 ? -NECK_THRUST : NECK_BEND) * (1.0F - fly);
			snapshots.ifPresent("neck" + i, b -> b.setRotY(b.getRotY() - yaw * NECK_TURN).setRotX(b.getRotX() - thrust));
		}
		float headX = tip * HEAD_TIP * (1.0F - crash) - breath * HEAD_DIP;
		snapshots.ifPresent("head", b -> b.setRotY(b.getRotY() - yaw * HEAD_TURN).setRotX(b.getRotX() - headX));
		snapshots.ifPresent("body", b -> b.setRotX(b.getRotX() - pitch * fly));
		if (breath > 0.0F) {
			float jaw = breath * JAW_OPEN + Mth.sin(age * JAW_WORK_SPEED) * JAW_WORK * breath;
			snapshots.ifPresent("jaw", b -> b.setRotX(b.getRotX() - jaw));
		}

		// its look: long or short horns (and the twin's brow pair), lost spikes; its tack
		DragonLook look = info.getOrDefaultGeckolibData(LOOK, DragonLook.decode(0));
		boolean shortHorns = look.horns() == DragonLook.SHORT_HORNS;
		boolean twin = look.horns() == DragonLook.TWIN_HORNS;
		for (String side : new String[] {"l", "r"}) {
			snapshots.ifPresent("horn_" + side, b -> b.skipRender(shortHorns));
			snapshots.ifPresent("horn_" + side + "_tip", b -> b.skipRender(shortHorns));
			snapshots.ifPresent("short_horn_" + side, b -> b.skipRender(!shortHorns));
			snapshots.ifPresent("short_horn_" + side + "_tip", b -> b.skipRender(!shortHorns));
			snapshots.ifPresent("brow_horn_" + side, b -> b.skipRender(!twin));
		}
		boolean saddled = info.getOrDefaultGeckolibData(SADDLED, false);
		for (int i = 0; i < DragonLook.SPIKES; i++) {
			// the saddle and the pillion pad sit where the middle three spikes would
			boolean hide = !look.spike(i) || saddled && i >= 1 && i <= 3;
			snapshots.ifPresent("spike" + i, b -> b.skipRender(hide));
		}
		for (String tack : new String[] {"saddle", "strap", "pad"}) {
			snapshots.ifPresent(tack, b -> b.skipRender(!saddled));
		}
	}

	@Override
	protected AABB getBoundingBoxForCulling(ZephyrusDragon dragon, float partialTicks) {
		return dragon.getBoundingBox().inflate(WindDragonRenderer.REACH, WindDragonRenderer.REACH_UP, WindDragonRenderer.REACH);
	}

	/** The model and its animations, in this dragon's hide. */
	private static final class Model extends DefaultedEntityGeoModel<ZephyrusDragon> {
		Model() {
			super(JaymosKingdoms.id("zephyrus"));
		}

		@Override
		public Identifier getTextureResource(GeoRenderState state) {
			return DragonLooks.hide("zephyrus", state.getOrDefaultGeckolibData(LOOK, DragonLook.decode(0)));
		}
	}

	/** Its markings and eyes, glowing (drawn as the other dragons' are, so they show in the dark). */
	private static final class Glow extends TextureLayerGeoLayer<ZephyrusDragon, Void, LivingEntityRenderState> {
		Glow(ZephyrusDragonRenderer renderer) {
			super(renderer, DragonLooks.marks("zephyrus", DragonLook.decode(0)), RenderTypes::eyes);
		}

		@Override
		protected Identifier getTextureResource(LivingEntityRenderState state) {
			return DragonLooks.marks("zephyrus", state.getOrDefaultGeckolibData(LOOK, DragonLook.decode(0)));
		}
	}
}
