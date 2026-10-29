package com.jaymospowers.client.aether.guardian;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.aether.guardian.GuardianMobs;
import com.jaymospowers.aether.guardian.VisageOfWar;
import com.jaymospowers.client.bb.BbModel;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;

/**
 * (Powers 0.17.148) The Visage of War ({@link VisageOfWar}), drawn as eanncrod's model (Blockbench Workshop, CC BY 4.0,
 * credits in textures/entity/bb/CREDITS.txt) with its own animations: asleep it holds its promo pose, awake it hovers or
 * flies, and each move plays its own from the moment it began. Dying it hangs in its recovery pose as it falls.
 */
public final class VisageOfWarRenderer extends MobRenderer<VisageOfWar, VisageOfWarRenderer.State, VisageOfWarRenderer.Model> {
	static final Identifier TEXTURE = JaymosPowers.id("textures/entity/bb/visage_of_war.png");
	/** The model stands about 2.3 blocks: drawn to its 3.4-block box. */
	static final float SCALE = 1.5F;
	/** How long it takes to blend from one move into the next (ticks). */
	static final float BLEND = 4.0F;

	public static final class State extends LivingEntityRenderState {
		VisageOfWar.Move move = VisageOfWar.Move.ASLEEP;
		float moveAge;
	}

	public VisageOfWarRenderer(EntityRendererProvider.Context context) {
		super(context, new Model(BbModel.load(JaymosPowers.MOD_ID, "visage_of_war")), 1.2F);
	}

	public static void init() {
		EntityRenderers.register(GuardianMobs.VISAGE_OF_WAR, VisageOfWarRenderer::new);
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(VisageOfWar visage, State state, float partialTicks) {
		super.extractRenderState(visage, state, partialTicks);
		state.move = visage.move();
		state.moveAge = visage.moveAge() + partialTicks;
	}

	@Override
	protected void scale(State state, PoseStack poseStack) {
		poseStack.scale(SCALE, SCALE, SCALE);
	}

	/** It falls dying as it is, not tipped over. */
	@Override
	protected float getFlipDegrees() {
		return 0.0F;
	}

	@Override
	public Identifier getTextureLocation(State state) {
		return TEXTURE;
	}

	static final class Model extends EntityModel<State> {
		private final BbModel bb;

		Model(BbModel bb) {
			super(bb.root);
			this.bb = bb;
		}

		@Override
		public void setupAnim(State state) {
			super.setupAnim(state);
			if (state.deathTime > 0.0F) {
				this.bb.play("recover", 0.0F, 1.0F);
				return;
			}
			VisageOfWar.Move move = state.move;
			float seconds = state.moveAge / 20.0F;
			// a move eases in over the hover it came from
			float in = move.ticks > 0 ? Math.min(1.0F, state.moveAge / BLEND) : 1.0F;
			if (in < 1.0F) {
				this.bb.play("hover", state.ageInTicks / 20.0F, 1.0F - in);
			}
			this.bb.play(move.anim, seconds, in);
		}
	}
}
