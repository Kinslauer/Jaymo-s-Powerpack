package com.jaymoskingdoms.client;

import com.geckolib.constant.DefaultAnimations;
import com.geckolib.constant.dataticket.DataTicket;
import com.geckolib.model.DefaultedEntityGeoModel;
import com.geckolib.renderer.GeoEntityRenderer;
import com.geckolib.renderer.base.BoneSnapshots;
import com.geckolib.renderer.base.GeoRenderState;
import com.geckolib.renderer.base.RenderPassInfo;
import com.geckolib.renderer.layer.builtin.TextureLayerGeoLayer;
import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.TownGolem;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/**
 * Draws the town golems ({@link TownGolem}) with GeckoLib, each kind its own body, skin and animations
 * ({@code geckolib/models/entity/town_golem_<kind>.geo.json}, {@code geckolib/animations/entity/town_golem_<kind>.animation.json},
 * {@code textures/entity/town_golem_<kind>.png}, all written by {@code kingdoms/tools/gen_town_golems.py}): the lamp
 * warden with its lantern head, the mossy mender with crystals on its back, and the throne golem, a walking keep that
 * folds into a throne by its {@code kneel} animation. What shines in the dark (eyes, cores, runes, crystals) is the
 * kind's glow sheet drawn over it; the lamp warden's light (the bones {@code flame_lit} and {@code core_lit}) is drawn
 * only at night. A golem looks where it looks with its head, except a throne, whose head keeps its own slow watch.
 */
public final class TownGolemRenderer extends GeoEntityRenderer<TownGolem, LivingEntityRenderState> {
	static final float SHADOW = 0.6F;
	static final DataTicket<Integer> KIND = DataTicket.create("jaymoskingdoms_town_golem_kind", Integer.class);
	static final DataTicket<Boolean> LIT = DataTicket.create("jaymoskingdoms_town_golem_lit", Boolean.class);
	static final DataTicket<Boolean> THRONE = DataTicket.create("jaymoskingdoms_town_golem_throne", Boolean.class);

	private static final int KINDS = TownGolem.Kind.values().length;

	public TownGolemRenderer(EntityRendererProvider.Context context) {
		super(context, new Model());
		this.shadowRadius = SHADOW;
		this.withRenderLayer(new Glow(this));
	}

	private static int kind(GeoRenderState state) {
		return Mth.clamp(state.getOrDefaultGeckolibData(KIND, 0), 0, KINDS - 1);
	}

	/** Each kind's own model, skin and animations. */
	static final class Model extends DefaultedEntityGeoModel<TownGolem> {
		private final Identifier[] models = new Identifier[KINDS], textures = new Identifier[KINDS], animations = new Identifier[KINDS];

		Model() {
			super(JaymosKingdoms.id("town_golem_gilt"));
			for (TownGolem.Kind kind : TownGolem.Kind.values()) {
				Identifier id = JaymosKingdoms.id("town_golem_" + kind.id);
				this.models[kind.ordinal()] = this.buildFormattedModelPath(id);
				this.textures[kind.ordinal()] = this.buildFormattedTexturePath(id);
				this.animations[kind.ordinal()] = this.buildFormattedAnimationPath(id);
			}
		}

		@Override
		public Identifier getModelResource(GeoRenderState state) {
			return this.models[kind(state)];
		}

		@Override
		public Identifier getTextureResource(GeoRenderState state) {
			return this.textures[kind(state)];
		}

		@Override
		public Identifier getAnimationResource(TownGolem golem) {
			return this.animations[golem.kind().ordinal()];
		}
	}

	/** Its eyes, core, runes and crystals, bright in the dark. */
	private static final class Glow extends TextureLayerGeoLayer<TownGolem, Void, LivingEntityRenderState> {
		private final Identifier[] glow = new Identifier[KINDS];

		Glow(TownGolemRenderer renderer) {
			super(renderer, JaymosKingdoms.id("textures/entity/town_golem_gilt_glow.png"), RenderTypes::eyes);
			for (TownGolem.Kind kind : TownGolem.Kind.values()) {
				this.glow[kind.ordinal()] = JaymosKingdoms.id("textures/entity/town_golem_" + kind.id + "_glow.png");
			}
		}

		@Override
		protected Identifier getTextureResource(LivingEntityRenderState state) {
			return this.glow[kind(state)];
		}

		@Override
		public void submitRenderTask(RenderPassInfo<LivingEntityRenderState> info, SubmitNodeCollector collector) {
			LivingEntityRenderState state = info.renderState();
			// a lamp warden's light is out by day, and nothing else of it shines
			if (state.isInvisible || kind(state) == TownGolem.Kind.GILT.ordinal() && !state.getOrDefaultGeckolibData(LIT, false)) {
				return;
			}
			super.submitRenderTask(info, collector);
		}
	}

	@Override
	public void addRenderData(TownGolem golem, Void related, LivingEntityRenderState state, float partialTick) {
		super.addRenderData(golem, related, state, partialTick);
		state.addGeckolibData(KIND, golem.kind().ordinal());
		state.addGeckolibData(LIT, golem.lit());
		state.addGeckolibData(THRONE, golem.throne());
	}

	@Override
	public void adjustModelBonesForRender(RenderPassInfo<LivingEntityRenderState> info, BoneSnapshots snapshots) {
		super.adjustModelBonesForRender(info, snapshots);
		if (!info.getOrDefaultGeckolibData(THRONE, false)) {
			DefaultAnimations.hardcodedHeadRotation(info, snapshots, "head");
		}
		boolean lit = info.getOrDefaultGeckolibData(LIT, false);
		snapshots.ifPresent("flame_lit", bone -> bone.skipRender(!lit));
		snapshots.ifPresent("core_lit", bone -> bone.skipRender(!lit));
	}

	/** It falls by its own death animation, so it isn't tipped over on its side as well. */
	@Override
	protected float getDeathMaxRotation(GeoRenderState state) {
		return 0.0F;
	}
}
