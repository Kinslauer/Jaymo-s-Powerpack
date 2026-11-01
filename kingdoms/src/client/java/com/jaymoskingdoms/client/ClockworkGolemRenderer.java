package com.jaymoskingdoms.client;

import com.jaymoskingdoms.ClockworkGolem;
import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.client.bb.BbModel;
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

/**
 * (0.7.326) The clockwork golem ({@link ClockworkGolem}), drawn as octopusstudio's Mech Golem (Blockbench Workshop,
 * CC BY 4.0, credits in textures/entity/bb/CREDITS.txt) with its own animations: idle, walk, its blow, folding into its
 * shell (its lights going out) and unfolding from it. (0.7.333) A rogue one's lights burn red and glow in the dark.
 */
public final class ClockworkGolemRenderer extends MobRenderer<ClockworkGolem, ClockworkGolemRenderer.State, ClockworkGolemRenderer.Model> {
	static final Identifier LIT = JaymosKingdoms.id("textures/entity/bb/clockwork_golem.png");
	static final Identifier DARK = JaymosKingdoms.id("textures/entity/bb/clockwork_golem_1.png");
	/** A rogue's lit texture (its lights red), and those lights alone, drawn bright ({@code tools/gen_clockwork_golem.py}). */
	static final Identifier ROGUE_LIT = JaymosKingdoms.id("textures/entity/bb/clockwork_golem_rogue.png");
	static final Identifier ROGUE_GLOW = JaymosKingdoms.id("textures/entity/bb/clockwork_golem_rogue_glow.png");
	/** The Mech Golem stands 2.86 blocks: drawn to the golem's 2.7. */
	static final float SCALE = 0.94F;

	public static final class State extends LivingEntityRenderState {
		boolean folded;
		boolean rogue;
		float sinceChanged;
		/** How far its blow has run (ticks, counting down from 10), 0 for none. */
		float attack;
		/** (Motion patch 6) Its entity id, so each golem fidgets on its own clock. */
		int id;
	}

	public ClockworkGolemRenderer(EntityRendererProvider.Context context) {
		super(context, new Model(BbModel.load(JaymosKingdoms.MOD_ID, "clockwork_golem")), 1.0F);
		this.addLayer(new Glow(this));
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(ClockworkGolem golem, State state, float partialTicks) {
		super.extractRenderState(golem, state, partialTicks);
		state.folded = golem.folded();
		state.rogue = golem.rogue();
		state.sinceChanged = golem.sinceChanged() + partialTicks;
		int tick = golem.getAttackAnimationTick();
		state.attack = tick > 0 ? tick - partialTicks : 0.0F;
		state.id = golem.getId();
	}

	@Override
	protected void scale(State state, PoseStack poseStack) {
		poseStack.scale(SCALE, SCALE, SCALE);
	}

	@Override
	public Identifier getTextureLocation(State state) {
		return dark(state) ? DARK : state.rogue ? ROGUE_LIT : LIT;
	}

	/** Its lights are out: folded (after it has shut), or still waking. */
	static boolean dark(State state) {
		return state.folded ? state.sinceChanged > 20.0F : state.sinceChanged < 20.0F;
	}

	/** A rogue's red eyes, bright in the dark while its lights are on. */
	private static final class Glow extends RenderLayer<State, Model> {
		Glow(ClockworkGolemRenderer parent) {
			super(parent);
		}

		@Override
		public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, State state, float yRot, float xRot) {
			if (!state.rogue || state.isInvisible || dark(state)) {
				return;
			}
			collector.order(1).submitModel(this.getParentModel(), state, poseStack, RenderTypes.eyes(ROGUE_GLOW), lightCoords, OverlayTexture.NO_OVERLAY, -1,
				null, state.outlineColor);
		}
	}

	static final class Model extends EntityModel<State> {
		/** An iron golem's blow runs this long (ticks). */
		static final float BLOW_TICKS = 10.0F;
		static final float STRIDE = Mth.TWO_PI / 0.6662F;
		/**
		 * (Motion patch 6) Idle fidgets, after Actions &amp; Stuff's golem idle: every FIDGET_EVERY ticks (plus a little per
		 * golem) it looks slowly round (LOOK radians each way) and shifts its weight (SHIFT radians), over FIDGET_TICKS.
		 */
		static final float FIDGET_EVERY = 260.0F, FIDGET_TICKS = 90.0F, LOOK = 0.7F, SHIFT = 0.06F;

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
			if (pace < 0.1F && state.attack <= 0.0F) {
				// standing about: now and then a slow look round and a shift of its weight from foot to foot
				float every = FIDGET_EVERY + (state.id & 7) * 20.0F;
				float t = (state.ageInTicks + (state.id * 31 & 127)) % every;
				if (t < FIDGET_TICKS) {
					float f = Mth.sin(t / FIDGET_TICKS * Mth.PI);
					this.bb.part("head").yRot += LOOK * f * Mth.sin(t / FIDGET_TICKS * Mth.TWO_PI);
					this.root().zRot += SHIFT * f * Mth.sin(t / FIDGET_TICKS * Mth.TWO_PI * 1.5F);
				}
			}
			this.bb.part("head").yRot += state.yRot * Mth.DEG_TO_RAD;
			this.bb.part("head").xRot += state.xRot * Mth.DEG_TO_RAD;
		}
	}
}
