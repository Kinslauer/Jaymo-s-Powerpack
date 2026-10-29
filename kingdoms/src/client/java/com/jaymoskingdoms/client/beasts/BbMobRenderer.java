package com.jaymoskingdoms.client.beasts;

import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.beasts.Acting;
import com.jaymoskingdoms.client.bb.BbModel;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.329) Draws a creature from a Blockbench model with its own animations ({@link BbModel}): between moves its
 * idle, its walk (and run, going fast) in step with its feet; a move ({@link Acting}) played from when it began, eased
 * in over the rest; its head turned to where it looks; its death animation when it has one. A glow texture, if given,
 * is drawn over it bright in the dark.
 */
public class BbMobRenderer<T extends Mob & Acting> extends MobRenderer<T, BbMobRenderer.State, BbMobRenderer.Model> {
	/** Ticks to ease into a move. */
	static final float BLEND = 3.0F;

	public static final class State extends LivingEntityRenderState {
		public @Nullable String action;
		public float actionAge;
	}

	/**
	 * How one creature is drawn: its model, how big, its animations (idle, walk, run, death; null for none), how many
	 * seconds of its walk one block of walking is, how fast it must go to run, its head bone, and its glow texture.
	 */
	public record Look(String model, float scale, float shadow, String idle, String walk, @Nullable String run, @Nullable String death,
		float walkSeconds, float runSpeed, @Nullable String head, @Nullable Identifier glow, @Nullable Extra extra) {
		public Look(String model, float scale, float shadow, String idle, String walk, @Nullable String run, @Nullable String death, float walkSeconds,
			float runSpeed, @Nullable String head) {
			this(model, scale, shadow, idle, walk, run, death, walkSeconds, runSpeed, head, null, null);
		}

		public Look withGlow(Identifier glow) {
			return new Look(this.model, this.scale, this.shadow, this.idle, this.walk, this.run, this.death, this.walkSeconds, this.runSpeed, this.head, glow,
				this.extra);
		}

		public Look withExtra(Extra extra) {
			return new Look(this.model, this.scale, this.shadow, this.idle, this.walk, this.run, this.death, this.walkSeconds, this.runSpeed, this.head,
				this.glow, extra);
		}
	}

	/** Posing of its own, after the animations (for moves its model has no animation for). */
	public interface Extra {
		void pose(BbModel bb, State state);
	}

	private final Look look;
	private final Identifier texture;

	public BbMobRenderer(EntityRendererProvider.Context context, Look look) {
		this(context, look, BbModel.load(JaymosKingdoms.MOD_ID, look.model()));
	}

	private BbMobRenderer(EntityRendererProvider.Context context, Look look, BbModel bb) {
		super(context, new Model(bb, look), look.shadow());
		this.look = look;
		this.texture = bb.texture;
		if (look.glow() != null) {
			this.addLayer(new Glow(this, look.glow()));
		}
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(T mob, State state, float partialTicks) {
		super.extractRenderState(mob, state, partialTicks);
		state.action = mob.action();
		state.actionAge = mob.actionAge() + partialTicks;
	}

	@Override
	protected void scale(State state, PoseStack poseStack) {
		poseStack.scale(this.look.scale(), this.look.scale(), this.look.scale());
	}

	@Override
	protected float getFlipDegrees() {
		return this.look.death() != null ? 0.0F : super.getFlipDegrees();
	}

	@Override
	public Identifier getTextureLocation(State state) {
		return this.texture;
	}

	public static final class Model extends EntityModel<State> {
		private final BbModel bb;
		private final Look look;
		private final @Nullable ModelPart head;

		Model(BbModel bb, Look look) {
			super(bb.root);
			this.bb = bb;
			this.look = look;
			this.head = look.head() != null ? bb.find(look.head()) : null;
		}

		@Override
		public void setupAnim(State state) {
			super.setupAnim(state);
			if (state.deathTime > 0.0F && this.look.death() != null) {
				this.bb.play(this.look.death(), state.deathTime / 20.0F, 1.0F);
				return;
			}
			float in = state.action != null ? Math.min(1.0F, state.actionAge / BLEND) : 0.0F;
			float rest = 1.0F - in;
			float speed = Math.min(state.walkAnimationSpeed, 1.0F);
			boolean running = this.look.run() != null && speed >= this.look.runSpeed();
			String gait = running ? this.look.run() : this.look.walk();
			float walkWeight = Mth.clamp(speed * 2.5F, 0.0F, 1.0F) * rest;
			this.bb.play(this.look.idle(), state.ageInTicks / 20.0F, rest * (1.0F - walkWeight));
			this.bb.play(gait, state.walkAnimationPos * this.look.walkSeconds() / (running ? 1.6F : 1.0F), walkWeight);
			if (state.action != null) {
				this.bb.play(state.action, state.actionAge / 20.0F, in);
			}
			if (this.head != null) {
				this.head.yRot += Mth.clamp(state.yRot, -60.0F, 60.0F) * Mth.DEG_TO_RAD * rest;
				this.head.xRot += Mth.clamp(state.xRot, -40.0F, 40.0F) * Mth.DEG_TO_RAD * rest;
			}
			if (this.look.extra() != null) {
				this.look.extra().pose(this.bb, state);
			}
		}
	}

	/** Its glowing parts, bright in the dark. */
	private static final class Glow extends RenderLayer<State, Model> {
		private final Identifier texture;

		Glow(BbMobRenderer<?> parent, Identifier texture) {
			super(parent);
			this.texture = texture;
		}

		@Override
		public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, State state, float yRot, float xRot) {
			if (state.isInvisible) {
				return;
			}
			collector.order(1).submitModel(this.getParentModel(), state, poseStack, RenderTypes.eyes(this.texture), lightCoords, OverlayTexture.NO_OVERLAY, -1,
				null, state.outlineColor);
		}
	}
}
