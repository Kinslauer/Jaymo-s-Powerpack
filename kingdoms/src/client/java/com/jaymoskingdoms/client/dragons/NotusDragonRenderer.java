package com.jaymoskingdoms.client.dragons;

import com.jaymoskingdoms.dragons.DragonLook;
import com.jaymoskingdoms.dragons.NotusDragon;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;

/**
 * (0.7.135) Draws Notus: {@link NotusDragonModel} in its own hide (one of the species' shades, by its
 * {@link DragonLook}), with its glowing markings and eyes over it ({@link DragonLooks}, drawn as the wild beasts' and the
 * heritages' glowing eyes are, so they show in the dark).
 */
final class NotusDragonRenderer extends MobRenderer<NotusDragon, NotusDragonRenderer.State, NotusDragonModel> {
	static final String SPECIES = "notus";
	/** Its shadow (blocks). */
	static final float SHADOW = 2.0F;

	static final class State extends LivingEntityRenderState {
		DragonLook look = DragonLook.decode(0);
		float fly, breath, land, sit, crash, flap, glide, pitch;
		boolean saddled;
	}

	NotusDragonRenderer(EntityRendererProvider.Context context) {
		super(context, new NotusDragonModel(NotusDragonModel.createBodyLayer().bakeRoot()), SHADOW);
		this.addLayer(new Glow(this));
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(NotusDragon dragon, State state, float partialTicks) {
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
		state.saddled = dragon.saddled();
	}

	@Override
	public Identifier getTextureLocation(State state) {
		return DragonLooks.hide(SPECIES, state.look);
	}

	@Override
	protected AABB getBoundingBoxForCulling(NotusDragon dragon, float partialTicks) {
		// its wings, neck and tail reach well past its box
		return dragon.getBoundingBox().inflate(4.5, 1.5, 4.5);
	}

	/** Its ember markings and eyes, glowing. */
	private static final class Glow extends RenderLayer<State, NotusDragonModel> {
		Glow(RenderLayerParent<State, NotusDragonModel> parent) {
			super(parent);
		}

		@Override
		public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, State state, float yRot, float xRot) {
			if (state.isInvisible) {
				return;
			}
			collector.order(1).submitModel(this.getParentModel(), state, poseStack, RenderTypes.eyes(DragonLooks.marks(SPECIES, state.look)), lightCoords,
				OverlayTexture.NO_OVERLAY, -1, null, state.outlineColor);
		}
	}
}
