package com.jaymoskingdoms.client.bb;

import com.jaymoskingdoms.Bodies;
import com.jaymoskingdoms.JaymosKingdoms;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.UvMapping;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.326) Villagers drawn as a body of their own ({@link Bodies}) in place of MCA's: the renderer's model is swapped
 * for the body's as it's handed in, and MCA's clothes, hair, armour and held things are left off (the body has its own),
 * by {@code BbBodyLivingEntityRendererMixin}. The bodies:
 * <ul>
 * <li><b>warchief</b>: nyxaris' Chieftain (Blockbench Workshop, CC BY 4.0), drawn down to the warchief's height, with
 * its own idle, walk, run, blows, slam and roar, each a fiercer one once he's been driven below
 * {@link #ENRAGED_BELOW} of his health; glowing eyes.</li>
 * </ul>
 */
public final class BbBodies {
	/** On a villager's render state: its body, what it last did and how long ago (seconds), and whether it's enraged. */
	public record Body(String kind, String act, float actSeconds, boolean enraged) {
	}

	public static final RenderStateDataKey<Body> KEY = RenderStateDataKey.create(() -> "jaymoskingdoms:bb_body");
	/** The warchief is enraged below this share of his health (his rally). */
	static final float ENRAGED_BELOW = 0.4F;
	/** The Chieftain stands 2.6 blocks: drawn this big, he's a man's height, and the warchief's own size does the rest. */
	static final float WARCHIEF_SCALE = 0.69F;

	private static final Map<String, BodyModel> MODELS = new HashMap<>();

	private BbBodies() {
	}

	/** Render-state extraction: the body a villager is drawn as (none: drawn as MCA draws it). */
	public static void extract(LivingEntity entity, LivingEntityRenderState state, float partialTicks) {
		String body = entity.getAttached(Bodies.BODY);
		if (body == null) {
			state.setData(KEY, null);
			return;
		}
		String[] bits = body.split("\\|");
		String act = "";
		float since = Float.MAX_VALUE;
		if (bits.length == 3) {
			act = bits[1];
			try {
				since = (entity.level().getGameTime() - Long.parseLong(bits[2]) + partialTicks) / 20.0F;
			} catch (NumberFormatException e) {
				act = "";
			}
		}
		state.setData(KEY, new Body(bits[0], act, since, entity.getHealth() <= entity.getMaxHealth() * ENRAGED_BELOW));
	}

	/** The model {@code state} is drawn with in place of the renderer's, or null. */
	public static @Nullable BodyModel model(Object state) {
		if (!(state instanceof LivingEntityRenderState living)) {
			return null;
		}
		Body body = living.getData(KEY);
		if (body == null) {
			return null;
		}
		return MODELS.computeIfAbsent(body.kind(), BbBodies::create);
	}

	private static @Nullable BodyModel create(String kind) {
		return switch (kind) {
			case Bodies.WARCHIEF -> new Warchief(BbModel.load(JaymosKingdoms.MOD_ID, "chieftain"));
			default -> null;
		};
	}

	/** Hands in {@code state}'s body in place of the renderer's model (and its glow, if it has one). */
	public static void submit(BodyModel model, SubmitNodeCollector collector, Object state, PoseStack poseStack, RenderType ignored, int light, int overlay,
		int color, @Nullable UvMapping uv, int outline) {
		poseStack.pushPose();
		// about the feet (the model's y = 24 pixels)
		poseStack.translate(0.0F, 1.5F, 0.0F);
		poseStack.scale(model.scale, model.scale, model.scale);
		poseStack.translate(0.0F, -1.5F, 0.0F);
		collector.submitModel(model, (LivingEntityRenderState) state, poseStack, RenderTypes.entityCutout(model.bb.texture), light, overlay, color, uv, outline);
		if (model.glow != null) {
			collector.order(1).submitModel(model, (LivingEntityRenderState) state, poseStack, RenderTypes.eyes(model.glow), light, OverlayTexture.NO_OVERLAY, -1, null, outline);
		}
		poseStack.popPose();
	}

	/** A body: its Blockbench model, how big it's drawn, its glowing texture (or none), and how it moves. */
	public abstract static class BodyModel extends EntityModel<LivingEntityRenderState> {
		final BbModel bb;
		final float scale;
		final @Nullable Identifier glow;

		BodyModel(BbModel bb, float scale, @Nullable Identifier glow) {
			super(bb.root);
			this.bb = bb;
			this.scale = scale;
			this.glow = glow;
		}
	}

	/** The warchief: idle, walking or running by his pace, his blows, his slam and his roar. */
	static final class Warchief extends BodyModel {
		Warchief(BbModel bb) {
			super(bb, WARCHIEF_SCALE, JaymosKingdoms.id("textures/entity/bb/chieftain_1.png"));
		}

		@Override
		public void setupAnim(LivingEntityRenderState state) {
			super.setupAnim(state);
			Body body = state.getData(KEY);
			String rage = body != null && body.enraged() ? "_enraged" : "";
			float pace = Mth.clamp(state.walkAnimationSpeed * 2.0F, 0.0F, 1.0F);
			float run = Mth.clamp((state.walkAnimationSpeed - 0.6F) * 3.0F, 0.0F, 1.0F);
			this.bb.play("idle" + rage, state.ageInTicks / 20.0F, 1.0F - pace);
			this.bb.play("walk", state.walkAnimationPos / 20.0F, pace * (1.0F - run));
			this.bb.play("run", state.walkAnimationPos / 20.0F, pace * run);
			if (body != null && !body.act().isEmpty()) {
				String act = body.act() + rage;
				if (body.actSeconds() < this.bb.length(act)) {
					this.bb.play(act, body.actSeconds(), 1.0F);
				}
			}
			if (state instanceof ArmedEntityRenderState armed && armed.swingAnimation > 0.0F) {
				String attack = "attack" + rage;
				this.bb.play(attack, armed.swingAnimation * this.bb.length(attack), 1.0F);
			}
			this.bb.part("head").yRot += state.yRot * Mth.DEG_TO_RAD * 0.6F;
			this.bb.part("head").xRot += state.xRot * Mth.DEG_TO_RAD * 0.6F;
		}
	}
}
