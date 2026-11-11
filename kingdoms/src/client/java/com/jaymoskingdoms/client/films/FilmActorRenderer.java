package com.jaymoskingdoms.client.films;

import com.geckolib.constant.dataticket.DataTicket;
import com.geckolib.model.DefaultedEntityGeoModel;
import com.geckolib.renderer.GeoEntityRenderer;
import com.geckolib.renderer.base.BoneSnapshots;
import com.geckolib.renderer.base.GeoRenderState;
import com.geckolib.renderer.base.RenderPassInfo;
import com.geckolib.renderer.layer.builtin.ItemInHandGeoLayer;
import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.films.FilmActor;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Draws a lore film's actor ({@link FilmActor}): the body it's cast with (wide, slim or a piglin's), in its own skin
 * with the armour it wears drawn onto it ({@link FilmGear}),
 * what it holds in its hands; an orc's tusks, brow and ears on an orc; no head or weapon arm once a finisher has taken
 * it; and on the fifth, the cloak and its tethers, each turned at what it has hold of and stretched there.
 */
public final class FilmActorRenderer extends GeoEntityRenderer<FilmActor, LivingEntityRenderState> {
	static final float SHADOW = 0.5F;
	/** A tether's root on the body (blocks up from the feet, at a body of ordinary size), and how long its cord is modelled (blocks). */
	static final double TETHER_UP = 1.5;
	static final String[] BODIES = {"film_actor", "film_actor_slim", "film_actor_piglin"};
	static final Identifier PIGLIN = Identifier.withDefaultNamespace("textures/entity/piglin/zombified_piglin.png");

	static final DataTicket<String> SKIN = DataTicket.create("jaymoskingdoms_film_skin", String.class);
	static final DataTicket<String> GEAR = DataTicket.create("jaymoskingdoms_film_gear", String.class);
	static final DataTicket<Integer> BODY = DataTicket.create("jaymoskingdoms_film_body", Integer.class);
	static final DataTicket<Integer> ORC = DataTicket.create("jaymoskingdoms_film_orc", Integer.class);
	static final DataTicket<Integer> GONE = DataTicket.create("jaymoskingdoms_film_gone", Integer.class);
	static final DataTicket<Boolean> CLOAK = DataTicket.create("jaymoskingdoms_film_cloak", Boolean.class);
	static final DataTicket<Boolean> RIBBONS = DataTicket.create("jaymoskingdoms_film_ribbons", Boolean.class);
	/** Each tether's {turn, tilt (radians, the model's way), length (blocks; 0 is in)}. */
	static final DataTicket<float[]> TETHERS = DataTicket.create("jaymoskingdoms_film_tethers", float[].class);

	public FilmActorRenderer(EntityRendererProvider.Context context) {
		super(context, new Model());
		this.shadowRadius = SHADOW;
		FilmGear.assets = context.getEquipmentAssets();
		this.withRenderLayer(new ItemInHandGeoLayer<>(context, this, "right_hand", "left_hand"));
	}

	/** One set of animations; the body and the skin are the actor's own. */
	static final class Model extends DefaultedEntityGeoModel<FilmActor> {
		private final Identifier[] bodies = new Identifier[BODIES.length];

		Model() {
			super(JaymosKingdoms.id("film_actor"));
			for (int i = 0; i < BODIES.length; i++) {
				this.bodies[i] = this.buildFormattedModelPath(JaymosKingdoms.id(BODIES[i]));
			}
		}

		@Override
		public Identifier getModelResource(GeoRenderState state) {
			return this.bodies[Mth.clamp(state.getOrDefaultGeckolibData(BODY, 0), 0, BODIES.length - 1)];
		}

		@Override
		public Identifier getTextureResource(GeoRenderState state) {
			if (state.getOrDefaultGeckolibData(BODY, 0) == FilmActor.PIGLIN) {
				return PIGLIN;
			}
			return FilmGear.texture(state.getOrDefaultGeckolibData(SKIN, "soldier_0"), state.getOrDefaultGeckolibData(GEAR, ""));
		}
	}

	@Override
	public void addRenderData(FilmActor actor, Void related, LivingEntityRenderState state, float partialTick) {
		super.addRenderData(actor, related, state, partialTick);
		state.addGeckolibData(SKIN, actor.skin);
		state.addGeckolibData(GEAR, FilmGear.key(actor));
		state.addGeckolibData(BODY, actor.body);
		state.addGeckolibData(ORC, actor.orc);
		state.addGeckolibData(GONE, actor.gone);
		state.addGeckolibData(CLOAK, actor.cloak);
		state.addGeckolibData(RIBBONS, actor.ribbons);
		float[] tethers = new float[FilmActor.TETHERS * 3];
		if (actor.cloak) {
			double scale = actor.getScale();
			float yaw = (float)Math.toRadians(Mth.rotLerp(partialTick, actor.yBodyRotO, actor.yBodyRot));
			// the way it faces and its right hand's side, in the world
			double fx = -Mth.sin(yaw), fz = Mth.cos(yaw), rx = -fz, rz = fx;
			Vec3 at = actor.getPosition(partialTick).add(0.0, TETHER_UP * scale, 0.0);
			for (int i = 0; i < FilmActor.TETHERS; i++) {
				float reach = actor.tetherReach(i, partialTick);
				if (reach < 0.02F) {
					continue;
				}
				Vec3 to = actor.tetherAt(i).subtract(at);
				double ahead = to.x * fx + to.z * fz, aside = to.x * rx + to.z * rz, length = Math.max(0.01, to.length());
				tethers[i * 3] = (float)Math.atan2(aside, ahead);
				tethers[i * 3 + 1] = (float)Math.asin(Mth.clamp(to.y / length, -1.0, 1.0));
				tethers[i * 3 + 2] = (float)(length * reach / scale);
			}
		}
		state.addGeckolibData(TETHERS, tethers);
	}

	@Override
	public void adjustModelBonesForRender(RenderPassInfo<LivingEntityRenderState> info, BoneSnapshots snapshots) {
		super.adjustModelBonesForRender(info, snapshots);
		int orc = info.getOrDefaultGeckolibData(ORC, 0), gone = info.getOrDefaultGeckolibData(GONE, 0);
		snapshots.ifPresent("orc_0", bone -> bone.skipRender(orc != 1));
		snapshots.ifPresent("orc_1", bone -> bone.skipRender(orc != 2));
		if ((gone & FilmActor.NO_HEAD) != 0) {
			snapshots.ifPresent("head", bone -> bone.skipRender(true).skipChildrenRender(true));
		}
		if ((gone & FilmActor.NO_RIGHT_ARM) != 0) {
			snapshots.ifPresent("right_arm", bone -> bone.skipRender(true).skipChildrenRender(true));
		}
		boolean cloak = info.getOrDefaultGeckolibData(CLOAK, false);
		boolean whole = cloak && !info.getOrDefaultGeckolibData(RIBBONS, false), ribbons = cloak && !whole;
		snapshots.ifPresent("cape_0", bone -> bone.skipRender(!whole).skipChildrenRender(!whole));
		for (int j = 0; j < 3; j++) {
			snapshots.ifPresent("ribbon_" + j + "_0", bone -> bone.skipRender(!ribbons).skipChildrenRender(!ribbons));
		}
		float[] tethers = info.getOrDefaultGeckolibData(TETHERS, null);
		for (int i = 0; i < FilmActor.TETHERS; i++) {
			float turn = tethers == null ? 0.0F : tethers[i * 3], tilt = tethers == null ? 0.0F : tethers[i * 3 + 1],
				length = tethers == null ? 0.0F : tethers[i * 3 + 2];
			snapshots.ifPresent("tether_" + i, bone -> {
				bone.skipRender(length <= 0.0F);
				if (length > 0.0F) {
					// (GeckoLib's x and y rotations run the other way from the model's)
					bone.setRotX(-tilt);
					bone.setRotY(-turn);
					bone.setScaleZ(length);
				}
			});
		}
	}

	@Override
	protected float getDeathMaxRotation(GeoRenderState state) {
		return 0.0F;
	}
}
