package com.jaymoskingdoms.client.dragons;

import com.jaymoskingdoms.dragons.DragonLook;
import com.jaymoskingdoms.dragons.WindDragon;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;

/**
 * (0.7.137) Draws a wind dragon: its species' model ({@link FireDragonModel} for Notus and Boreas since 0.7.326,
 * {@link ZephyrusDragonModel}) in its own hide (one of the species' shades, by its {@link DragonLook}), with its glowing
 * markings and eyes over it ({@link DragonLooks}, drawn as the wild beasts' and the heritages' glowing eyes are, so they
 * show in the dark).
 */
final class WindDragonRenderer<M extends EntityModel<WindDragonRenderer.State>> extends MobRenderer<WindDragon, WindDragonRenderer.State, M> {
	/** How far past its box its wings, neck and tail reach (blocks), for culling. */
	static final double REACH = 4.5, REACH_UP = 1.5;
	/** (0.7.141) A hatchling is drawn this big. */
	static final float HATCHLING_SCALE = 0.35F;

	static final class State extends LivingEntityRenderState {
		DragonLook look = DragonLook.decode(0);
		float fly, breath, land, sit, crash, flap, glide, pitch, swim;
		boolean saddled;
	}

	private final String species;
	private final float scale;
	private final float reach;

	/** {@code species}: its folder under {@code textures/entity/dragon/}; {@code shadow}: its shadow's radius (blocks). */
	WindDragonRenderer(EntityRendererProvider.Context context, String species, M model, float shadow) {
		this(context, species, model, shadow, 1.0F);
	}

	/** (0.7.141) {@code scale}: how much bigger than its model it's drawn (Aeolus). */
	WindDragonRenderer(EntityRendererProvider.Context context, String species, M model, float shadow, float scale) {
		this(context, species, model, shadow, scale, (float) REACH);
	}

	/** (0.7.329) {@code reach}: how far it reaches past its box when drawn at full size (blocks; the Kintsugi's wings). */
	WindDragonRenderer(EntityRendererProvider.Context context, String species, M model, float shadow, float scale, float reach) {
		super(context, model, shadow);
		this.species = species;
		this.scale = scale;
		this.reach = reach;
		this.addLayer(new Glow<>(this));
	}

	@Override
	protected void scale(State state, PoseStack poseStack) {
		float s = this.scale * (state.isBaby ? HATCHLING_SCALE : 1.0F);
		if (s != 1.0F) {
			poseStack.scale(s, s, s);
		}
		super.scale(state, poseStack);
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(WindDragon dragon, State state, float partialTicks) {
		super.extractRenderState(dragon, state, partialTicks);
		state.look = dragon.look();
		state.fly = Mth.lerp(partialTicks, dragon.flyAnimO, dragon.flyAnim);
		state.breath = Mth.lerp(partialTicks, dragon.breathAnimO, dragon.breathAnim);
		state.land = Mth.lerp(partialTicks, dragon.landAnimO, dragon.landAnim);
		state.sit = Mth.lerp(partialTicks, dragon.sitAnimO, dragon.sitAnim);
		state.crash = Mth.lerp(partialTicks, dragon.crashAnimO, dragon.crashAnim);
		state.flap = Mth.lerp(partialTicks, dragon.flapPhaseO, dragon.flapPhase);
		state.glide = Mth.lerp(partialTicks, dragon.glideAnimO, dragon.glideAnim);
		state.pitch = Mth.lerp(partialTicks, dragon.pitchAnimO, dragon.pitchAnim);
		state.swim = Mth.lerp(partialTicks, dragon.swimAnimO, dragon.swimAnim);
		state.saddled = dragon.saddled();
	}

	@Override
	public Identifier getTextureLocation(State state) {
		return DragonLooks.hide(this.species, state.look);
	}

	@Override
	protected AABB getBoundingBoxForCulling(WindDragon dragon, float partialTicks) {
		return dragon.getBoundingBox().inflate(this.reach * this.scale, Math.max(REACH_UP, this.reach * 0.5) * this.scale, this.reach * this.scale);
	}

	/** Its markings and eyes, glowing. */
	private static final class Glow<M extends EntityModel<State>> extends RenderLayer<State, M> {
		private final WindDragonRenderer<M> renderer;

		Glow(WindDragonRenderer<M> parent) {
			super(parent);
			this.renderer = parent;
		}

		@Override
		public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, State state, float yRot, float xRot) {
			if (state.isInvisible) {
				return;
			}
			collector.order(1).submitModel(this.getParentModel(), state, poseStack, RenderTypes.eyes(DragonLooks.marks(this.renderer.species, state.look)), lightCoords,
				OverlayTexture.NO_OVERLAY, -1, null, state.outlineColor);
		}
	}
}
