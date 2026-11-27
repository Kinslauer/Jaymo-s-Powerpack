package com.jaymoskingdoms.client.champions;

import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.Patron;
import com.jaymoskingdoms.colossus.PatronColossus;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;

/**
 * Draws a patron's colossus ({@link PatronColossus}), after the figure in Andy's "Commandment breaker" clip: a vast, faceless,
 * pale figure, seen through like cloud. Its body is the champion's ({@link ChampionModel}: each patron's marks, the crown,
 * the antlers, the hood), the Truth's left bare, all one pale skin ({@code tools/gen_colossus.py}) leaning to its patron's
 * colour, and lit by nothing but itself. It fades in, walks with slow long strides, lifts its right foot and stamps, and fades
 * out. Drawn at any distance.
 */
public final class ColossusRenderer extends EntityRenderer<PatronColossus, ColossusRenderer.State> {
	static final Identifier TEXTURE = JaymosKingdoms.id("textures/entity/patron_colossus.png");
	/** How see-through it is at its most solid (0-255): a strike, an omen or a hunt. */
	static final int STRIKE_ALPHA = 190, OMEN_ALPHA = 110, HUNT_ALPHA = 150;
	/** How far its pale leans to its patron's colour. */
	static final float HUE = 0.3F;
	/** The stride: how fast its legs swing (radians a tick) and how far; how far the arms swing against them. */
	static final float STRIDE_SPEED = 0.08F, STRIDE_SWING = 0.35F, ARM_SWING = 0.2F;
	/** The stamp: how high the foot is raised (the leg's angle), the arms thrown out to balance, the head bowed to look. */
	static final float LIFT = 0.9F, BALANCE = 0.5F, BOW = 0.35F;

	public static final class State extends HumanoidRenderState {
		Patron patron = Patron.TRUTH;
		int mode;
		boolean walking;
		/** 0 to 1 how there it is; the stamp's lift, 0 to 1 (raised); the stride's phase (ticks). */
		float there, lift, stride;
	}

	private final Map<Patron, Body> bodies = new EnumMap<>(Patron.class);

	ColossusRenderer(EntityRendererProvider.Context context) {
		super(context);
	}

	public static void register() {
		EntityRenderers.register(PatronColossus.TYPE, ColossusRenderer::new);
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public boolean shouldRender(PatronColossus colossus, Frustum frustum, double x, double y, double z, float partialTicks) {
		return true;
	}

	@Override
	public void extractRenderState(PatronColossus c, State state, float partialTicks) {
		super.extractRenderState(c, state, partialTicks);
		long now = c.level().getGameTime();
		if (c.seenAt < 0L) {
			c.seenAt = now;
		}
		float t = now + partialTicks;
		state.patron = c.patron();
		state.mode = c.mode();
		state.walking = c.walking();
		state.yRot = c.getYRot(partialTicks);
		state.stride = t;
		float in = Mth.clamp((t - c.seenAt) / PatronColossus.FADE_IN, 0.0F, 1.0F);
		float out = c.goneAt < 0L ? 1.0F : 1.0F - Mth.clamp((t - c.goneAt) / PatronColossus.FADE_OUT, 0.0F, 1.0F);
		state.there = Math.min(in, out);
		if (c.stampAt < 0L) {
			state.lift = 0.0F;
		} else {
			float s = t - c.stampAt;
			// up slowly, eased; down hard
			state.lift = s < PatronColossus.LIFT_TICKS ? Mth.sin(s / PatronColossus.LIFT_TICKS * Mth.HALF_PI)
				: 1.0F - Mth.clamp((s - PatronColossus.LIFT_TICKS) / PatronColossus.FALL_TICKS, 0.0F, 1.0F);
		}
		state.lightCoords = 0xF000F0;
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		if (state.there <= 0.01F) {
			return;
		}
		Body body = this.bodies.computeIfAbsent(state.patron, Body::new);
		float scale = PatronColossus.HEIGHT / 2.0F;
		poseStack.pushPose();
		poseStack.rotateDegrees(Axis.YP, 180.0F - state.yRot);
		poseStack.scale(scale, scale, scale);
		poseStack.scale(-1.0F, -1.0F, 1.0F);
		poseStack.translate(0.0F, -1.501F, 0.0F);
		int alpha = state.mode == PatronColossus.STRIKE ? STRIKE_ALPHA : state.mode == PatronColossus.OMEN ? OMEN_ALPHA : HUNT_ALPHA;
		collector.submitModel(body, state, poseStack, RenderTypes.entityTranslucentEmissive(TEXTURE), state.lightCoords, OverlayTexture.NO_OVERLAY,
			tint(state.patron, Math.round(alpha * state.there)));
		poseStack.popPose();
	}

	/** The pale, leaning {@link #HUE} toward {@code p}'s colour, at {@code alpha}. */
	static int tint(Patron p, int alpha) {
		int c = p.colour;
		int r = Math.round(255 + (((c >> 16) & 0xFF) - 255) * HUE);
		int g = Math.round(255 + (((c >> 8) & 0xFF) - 255) * HUE);
		int b = Math.round(255 + ((c & 0xFF) - 255) * HUE);
		return ARGB.color(alpha, r, g, b);
	}

	/** One patron's colossal body: the champion's, or a bare man's for the Truth, which keeps no idol. */
	static final class Body extends HumanoidModel<State> {
		Body(Patron patron) {
			super(layer(patron).bakeRoot());
		}

		private static LayerDefinition layer(Patron patron) {
			return patron == Patron.TRUTH ? LayerDefinition.create(HumanoidModel.createMesh(CubeDeformation.NONE, 0.0F), 64, 64)
				: ChampionModel.createBodyLayer(patron);
		}

		@Override
		public void setupAnim(State state) {
			super.setupAnim(state);
			this.hat.visible = false;
			// it looks down at whoever is below it
			this.head.xRot = BOW;
			if (state.walking) {
				float swing = Mth.sin(state.stride * STRIDE_SPEED);
				this.rightLeg.xRot = swing * STRIDE_SWING;
				this.leftLeg.xRot = -swing * STRIDE_SWING;
				this.rightArm.xRot = -swing * ARM_SWING;
				this.leftArm.xRot = swing * ARM_SWING;
			}
			if (state.lift > 0.0F) {
				this.rightLeg.xRot = -LIFT * state.lift;
				this.rightArm.zRot = BALANCE * state.lift;
				this.leftArm.zRot = -BALANCE * state.lift;
			}
		}
	}
}
