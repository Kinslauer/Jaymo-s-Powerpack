package com.jaymoskingdoms.client.dragons;

import com.geckolib.animatable.GeoEntity;
import com.geckolib.constant.dataticket.DataTicket;
import com.geckolib.model.DefaultedEntityGeoModel;
import com.geckolib.renderer.GeoEntityRenderer;
import com.geckolib.renderer.base.BoneSnapshots;
import com.geckolib.renderer.base.GeoRenderState;
import com.geckolib.renderer.base.RenderPassInfo;
import com.geckolib.renderer.layer.builtin.TextureLayerGeoLayer;
import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.dragons.DragonLook;
import com.jaymoskingdoms.dragons.WindDragon;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;

/**
 * Notus and Boreas drawn by GeckoLib: nyxaris' Fire Dragon ({@code geckolib/models/entity/fire_dragon.geo.json}) with
 * its animations ({@code dragons/FireDragonAnimations} picks them), in the species' hide by its {@link DragonLook}, with
 * its glowing markings and eyes over it, as {@link WindDragonRenderer} draws the other dragons. On top of the
 * animation it turns its neck and head where it looks, pitches its body with a climb or a dive, and drops its jaw to
 * breathe.
 */
final class FireDragonRenderer<T extends WindDragon & GeoEntity> extends GeoEntityRenderer<T, LivingEntityRenderState> {
	/** Of where it looks, how much the neck and the head each turn, and how much the head tips (on the ground). */
	static final float NECK_TURN = 0.4F, HEAD_TURN = 0.4F, HEAD_TIP = 0.5F;
	/** How far it looks (degrees): side to side, and up and down. */
	static final float LOOK_YAW = 60.0F, LOOK_UP = -40.0F, LOOK_DOWN = 60.0F;
	/** Breathing: how wide the jaw opens (radians), and how it works while it does. */
	static final float JAW_OPEN = 0.7F, JAW_WORK = 0.05F, JAW_WORK_SPEED = 0.6F;

	static final DataTicket<DragonLook> LOOK = DataTicket.create("jaymoskingdoms_dragon_look", DragonLook.class);
	static final DataTicket<Float> FLY = DataTicket.create("jaymoskingdoms_dragon_fly", Float.class);
	static final DataTicket<Float> BREATH = DataTicket.create("jaymoskingdoms_dragon_breath", Float.class);
	static final DataTicket<Float> PITCH = DataTicket.create("jaymoskingdoms_dragon_pitch", Float.class);

	private final float scale;
	private final float reach;

	/** {@code species}: its folder under {@code textures/entity/dragon/}; drawn {@code scale} times the model's size. */
	FireDragonRenderer(EntityRendererProvider.Context context, String species, float shadow, float scale) {
		super(context, new Model<T>(species));
		this.scale = scale;
		this.reach = (float) WindDragonRenderer.REACH;
		this.shadowRadius = shadow;
		this.withRenderLayer(new Glow<>(this, species));
	}

	@Override
	public void addRenderData(T dragon, Void related, LivingEntityRenderState state, float partialTick) {
		super.addRenderData(dragon, related, state, partialTick);
		state.addGeckolibData(LOOK, dragon.look());
		state.addGeckolibData(FLY, Mth.lerp(partialTick, dragon.flyAnimO, dragon.flyAnim));
		state.addGeckolibData(BREATH, Mth.lerp(partialTick, dragon.breathAnimO, dragon.breathAnim));
		state.addGeckolibData(PITCH, Mth.lerp(partialTick, dragon.pitchAnimO, dragon.pitchAnim));
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
		float breath = info.getOrDefaultGeckolibData(BREATH, 0.0F);
		float pitch = info.getOrDefaultGeckolibData(PITCH, 0.0F);
		float yaw = Mth.clamp(state.yRot, -LOOK_YAW, LOOK_YAW) * Mth.DEG_TO_RAD;
		float tip = Mth.clamp(state.xRot, LOOK_UP, LOOK_DOWN) * Mth.DEG_TO_RAD;
		float age = (float) info.renderState().getAnimatableAge();
		// GeckoLib turns x and y the other way round from a ModelPart: these are the old model's angles, negated
		snapshots.ifPresent("cuello1", b -> b.setRotY(b.getRotY() - yaw * NECK_TURN));
		snapshots.ifPresent("HEAD", b -> b.setRotY(b.getRotY() - yaw * HEAD_TURN).setRotX(b.getRotX() - tip * HEAD_TIP * (1.0F - fly)));
		snapshots.ifPresent("body", b -> b.setRotX(b.getRotX() - pitch * fly));
		if (breath > 0.0F) {
			float jaw = breath * JAW_OPEN + Mth.sin(age * JAW_WORK_SPEED) * JAW_WORK * breath;
			snapshots.ifPresent("mandibula", b -> b.setRotX(b.getRotX() - jaw));
		}
	}

	@Override
	protected AABB getBoundingBoxForCulling(T dragon, float partialTicks) {
		double up = Math.max(WindDragonRenderer.REACH_UP, this.reach * 0.5);
		return dragon.getBoundingBox().inflate(this.reach * this.scale, up * this.scale, this.reach * this.scale);
	}

	/** The Fire Dragon's model and animations, in this dragon's hide. */
	private static final class Model<T extends WindDragon & GeoEntity> extends DefaultedEntityGeoModel<T> {
		private final String species;

		Model(String species) {
			super(JaymosKingdoms.id("fire_dragon"));
			this.species = species;
		}

		@Override
		public Identifier getTextureResource(GeoRenderState state) {
			return DragonLooks.hide(this.species, state.getOrDefaultGeckolibData(LOOK, DragonLook.decode(0)));
		}
	}

	/** Its markings and eyes, glowing (drawn as the other dragons' are, so they show in the dark). */
	private static final class Glow<T extends WindDragon & GeoEntity> extends TextureLayerGeoLayer<T, Void, LivingEntityRenderState> {
		private final String species;

		Glow(FireDragonRenderer<T> renderer, String species) {
			super(renderer, DragonLooks.marks(species, DragonLook.decode(0)), RenderTypes::eyes);
			this.species = species;
		}

		@Override
		protected Identifier getTextureResource(LivingEntityRenderState state) {
			return DragonLooks.marks(this.species, state.getOrDefaultGeckolibData(LOOK, DragonLook.decode(0)));
		}
	}
}
