package com.jaymoskingdoms.client.wither;

import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.client.bb.BbModel;
import com.jaymoskingdoms.wither.WitherAct;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.EnergySwirlLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.WitherRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import org.jspecify.annotations.Nullable;

/**
 * The Wither's own look (Andy: "more hulking ... broader shoulders for the heads to sit on, the two side heads becoming
 * pauldron like. and four ribcage like arms sticking out"): our own Blockbench model (tools/gen_wither.py, editable at
 * kingdoms/art/wither.bbmodel) in charred bone, its core and eyes glowing the Wither's violet in the dark. It hangs
 * breathing, circles its quarry leant into the turn (harder in its last third), and plays each move of its fight
 * ({@link WitherAct}, told by the server) from when it began; it curls and unfolds through its spawn and slumps in its
 * death. The three skulls look about on their own as vanilla's do, and its half-health shield swirls round it.
 */
public final class WitherRenderer extends MobRenderer<WitherBoss, WitherRenderer.State, WitherRenderer.Model> {
	/** How big it's drawn (the model is about 4.7 blocks tall), and how much smaller while it's still being raised. */
	static final float SCALE = 0.85F, SPAWN_SHRINK = 0.25F;
	/** Its shadow's radius (blocks). */
	static final float SHADOW = 1.2F;
	/** Vanilla's ticks of build-up after it's made (WitherBoss), the length of our spawn animation. */
	static final float SPAWN_TICKS = 220.0F;
	/** Ticks to ease into a move. */
	static final float BLEND = 3.0F;
	/** How far its centre skull turns to look (degrees, yaw and pitch). */
	static final float LOOK_YAW = 60.0F, LOOK_PITCH = 40.0F;

	static final Identifier SKIN = JaymosKingdoms.id("textures/entity/bb/wither.png");
	static final Identifier PALE = JaymosKingdoms.id("textures/entity/bb/wither_pale.png");
	static final Identifier GLOW = JaymosKingdoms.id("textures/entity/bb/wither_glow.png");
	static final Identifier SHIELD = Identifier.withDefaultNamespace("textures/entity/wither/wither_armor.png");

	public static final class State extends WitherRenderState {
		public @Nullable String action;
		public float actionAge;
		/** In its last third of health: it circles faster. */
		public boolean angry;
	}

	public static void register() {
		EntityRenderers.register(EntityTypes.WITHER, WitherRenderer::new);
	}

	private WitherRenderer(EntityRendererProvider.Context context) {
		super(context, new Model(BbModel.load(JaymosKingdoms.MOD_ID, "wither")), SHADOW);
		this.addLayer(new Glow(this));
		this.addLayer(new Shield(this, new Model(BbModel.load(JaymosKingdoms.MOD_ID, "wither_armor"))));
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(WitherBoss wither, State state, float partialTicks) {
		super.extractRenderState(wither, state, partialTicks);
		int invulnerable = wither.getInvulnerableTicks();
		state.invulnerableTicks = invulnerable > 0 ? invulnerable - partialTicks : 0.0F;
		System.arraycopy(wither.getHeadXRots(), 0, state.xHeadRots, 0, state.xHeadRots.length);
		System.arraycopy(wither.getHeadYRots(), 0, state.yHeadRots, 0, state.yHeadRots.length);
		state.isPowered = wither.isPowered();
		state.angry = wither.getHealth() <= wither.getMaxHealth() / 3.0F;
		WitherAct.Act act = WitherAct.of(wither);
		state.action = act != null ? act.name() : null;
		state.actionAge = act != null ? Math.max(0.0F, wither.level().getGameTime() - act.start() + partialTicks) : 0.0F;
	}

	@Override
	protected int getBlockLightLevel(WitherBoss wither, BlockPos pos) {
		return 15;
	}

	@Override
	protected void scale(State state, PoseStack poseStack) {
		float s = SCALE;
		if (state.invulnerableTicks > 0.0F) {
			s *= 1.0F - state.invulnerableTicks / SPAWN_TICKS * SPAWN_SHRINK;
		}
		poseStack.scale(s, s, s);
	}

	@Override
	protected float getFlipDegrees() {
		// (its own death animation, so it doesn't also fall over sideways)
		return 0.0F;
	}

	@Override
	public Identifier getTextureLocation(State state) {
		// while it's being raised it flickers ash-pale, as vanilla's does, then holds pale for the last moments
		int ticks = (int)state.invulnerableTicks;
		return ticks > 0 && (ticks > 80 || ticks / 5 % 2 != 1) ? PALE : SKIN;
	}

	public static final class Model extends EntityModel<State> {
		private final BbModel bb;
		private final ModelPart centre, right, left;

		Model(BbModel bb) {
			super(bb.root);
			this.bb = bb;
			this.centre = bb.part("head_c");
			this.right = bb.part("head_r");
			this.left = bb.part("head_l");
		}

		@Override
		public void setupAnim(State state) {
			super.setupAnim(state);
			if (state.deathTime > 0.0F) {
				this.bb.play("death", state.deathTime / 20.0F, 1.0F);
				return;
			}
			if (state.invulnerableTicks > 0.0F) {
				this.bb.play("spawn", (SPAWN_TICKS - state.invulnerableTicks) / 20.0F, 1.0F);
				return;
			}
			float in = state.action != null ? Math.min(1.0F, state.actionAge / BLEND) : 0.0F;
			float rest = 1.0F - in;
			// circling (it flies, so its stride is how fast it goes): the orbit, over the idle hang
			float flying = Mth.clamp(Math.min(state.walkAnimationSpeed, 1.0F) * 2.5F, 0.0F, 1.0F) * rest;
			float seconds = state.ageInTicks / 20.0F;
			this.bb.play("idle", seconds, rest * (1.0F - flying));
			this.bb.play(state.angry ? "orbit_fast" : "orbit", seconds, flying);
			if (state.action != null) {
				this.bb.play(state.action, state.actionAge / 20.0F, in);
			}
			// the skulls look about on their own, as vanilla's three heads do
			this.centre.yRot += Mth.clamp(state.yRot, -LOOK_YAW, LOOK_YAW) * Mth.DEG_TO_RAD;
			this.centre.xRot += Mth.clamp(state.xRot, -LOOK_PITCH, LOOK_PITCH) * Mth.DEG_TO_RAD;
			look(this.right, state, 0);
			look(this.left, state, 1);
		}

		private static void look(ModelPart head, State state, int i) {
			head.yRot += Mth.clamp(Mth.wrapDegrees(state.yHeadRots[i] - state.bodyRot), -LOOK_YAW, LOOK_YAW) * Mth.DEG_TO_RAD;
			head.xRot += Mth.clamp(state.xHeadRots[i], -LOOK_PITCH, LOOK_PITCH) * Mth.DEG_TO_RAD;
		}
	}

	/** Its eyes and the seams of its core, bright in the dark. */
	private static final class Glow extends RenderLayer<State, Model> {
		Glow(WitherRenderer parent) {
			super(parent);
		}

		@Override
		public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, State state, float yRot, float xRot) {
			if (state.isInvisible) {
				return;
			}
			collector.order(1).submitModel(this.getParentModel(), state, poseStack, RenderTypes.eyes(GLOW), lightCoords, OverlayTexture.NO_OVERLAY, -1,
				null, state.outlineColor);
		}
	}

	/** At half health its shield: vanilla's swirl over a puffed-out copy of the model. */
	private static final class Shield extends EnergySwirlLayer<State, Model> {
		private final Model model;

		Shield(WitherRenderer parent, Model model) {
			super(parent);
			this.model = model;
		}

		@Override
		protected boolean isPowered(State state) {
			return state.isPowered;
		}

		@Override
		protected float xOffset(float ticks) {
			return Mth.cos(ticks * 0.02F) * 3.0F;
		}

		@Override
		protected Identifier getTextureLocation() {
			return SHIELD;
		}

		@Override
		protected Model model() {
			return this.model;
		}
	}
}
