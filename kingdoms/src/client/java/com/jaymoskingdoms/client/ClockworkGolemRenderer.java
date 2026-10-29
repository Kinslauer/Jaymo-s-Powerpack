package com.jaymoskingdoms.client;

import com.jaymoskingdoms.ClockworkGolem;
import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.client.bb.BbModel;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/**
 * (0.7.326) The clockwork golem ({@link ClockworkGolem}), drawn as octopusstudio's Mech Golem (Blockbench Workshop,
 * CC BY 4.0, credits in textures/entity/bb/CREDITS.txt) with its own animations: idle, walk, its blow, folding into its
 * shell (its lights going out) and unfolding from it.
 */
public final class ClockworkGolemRenderer extends MobRenderer<ClockworkGolem, ClockworkGolemRenderer.State, ClockworkGolemRenderer.Model> {
	static final Identifier LIT = JaymosKingdoms.id("textures/entity/bb/clockwork_golem.png");
	static final Identifier DARK = JaymosKingdoms.id("textures/entity/bb/clockwork_golem_1.png");
	/** The Mech Golem stands 2.86 blocks: drawn to the golem's 2.7. */
	static final float SCALE = 0.94F;

	public static final class State extends LivingEntityRenderState {
		boolean folded;
		float sinceChanged;
		/** How far its blow has run (ticks, counting down from 10), 0 for none. */
		float attack;
	}

	public ClockworkGolemRenderer(EntityRendererProvider.Context context) {
		super(context, new Model(BbModel.load(JaymosKingdoms.MOD_ID, "clockwork_golem")), 1.0F);
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(ClockworkGolem golem, State state, float partialTicks) {
		super.extractRenderState(golem, state, partialTicks);
		state.folded = golem.folded();
		state.sinceChanged = golem.sinceChanged() + partialTicks;
		int tick = golem.getAttackAnimationTick();
		state.attack = tick > 0 ? tick - partialTicks : 0.0F;
	}

	@Override
	protected void scale(State state, PoseStack poseStack) {
		poseStack.scale(SCALE, SCALE, SCALE);
	}

	@Override
	public Identifier getTextureLocation(State state) {
		boolean dark = state.folded ? state.sinceChanged > 20.0F : state.sinceChanged < 20.0F;
		return dark ? DARK : LIT;
	}

	static final class Model extends EntityModel<State> {
		/** An iron golem's blow runs this long (ticks). */
		static final float BLOW_TICKS = 10.0F;
		static final float STRIDE = Mth.TWO_PI / 0.6662F;

		private final BbModel bb;

		Model(BbModel bb) {
			super(bb.root);
			this.bb = bb;
		}

		@Override
		public void setupAnim(State state) {
			super.setupAnim(state);
			float seconds = state.sinceChanged / 20.0F;
			if (state.folded) {
				this.bb.play("shell", seconds, 1.0F);
				return;
			}
			if (seconds < this.bb.length("shell-leave")) {
				this.bb.play("shell-leave", seconds, 1.0F);
				return;
			}
			float pace = Mth.clamp(state.walkAnimationSpeed * 2.0F, 0.0F, 1.0F);
			this.bb.play("idle", state.ageInTicks / 20.0F, 1.0F - pace);
			this.bb.play("walk", state.walkAnimationPos / STRIDE * this.bb.length("walk"), pace);
			if (state.attack > 0.0F) {
				this.bb.play("atack", (1.0F - state.attack / BLOW_TICKS) * this.bb.length("atack"), 1.0F);
			}
			this.bb.part("head").yRot += state.yRot * Mth.DEG_TO_RAD;
			this.bb.part("head").xRot += state.xRot * Mth.DEG_TO_RAD;
		}
	}
}
