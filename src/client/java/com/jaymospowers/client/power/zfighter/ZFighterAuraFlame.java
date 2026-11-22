package com.jaymospowers.client.power.zfighter;

import com.jaymospowers.JaymosPowers;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Avatar;

/**
 * (Ideas only; ARR - no DragonMineZ or Sparking Zero art copied.) A flame shell round a Z Fighter's body with a bright
 * white core at its heart: flame tongues lick up past him and flicker, the core holds steady and white. Drawn under the
 * same conditions as {@link ZFighterKiAura} (charging, held after a full charge, flaring in a form), in his ki colour
 * with a white centre. Not on the Great Ape, while hidden, nor round your own first-person camera.
 */
public final class ZFighterAuraFlame {
	private static final Identifier TEXTURE = JaymosPowers.id("textures/entity/zfighter_ki_wisp.png");
	private static final int TONGUES = 10;
	private static final float LENGTH = 0.9F, WIDTH = 0.20F;
	private static final float FEET = 1.5F, CROWN = -0.5F;

	private ZFighterAuraFlame() {
	}

	/** The shell's layer, drawn under the wisps. */
	static final class Layer extends RenderLayer<AvatarRenderState, PlayerModel> {
		Layer(RenderLayerParent<AvatarRenderState, PlayerModel> renderer) {
			super(renderer);
		}

		@Override
		public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, AvatarRenderState state,
				float yRot, float xRot) {
			float[] aura = state.getData(ZFighterKiAura.KEY);
			if (aura == null || state.isInvisible) {
				return;
			}
			int color = (int) aura[0];
			float strength = aura[1];
			float size = aura[2];
			float light = Mth.clamp(strength * 1.2F, 0.0F, 1.0F);
			float age = state.ageInTicks;
			collector.order(6).submitCustomGeometry(poseStack, RenderTypes.energySwirl(TEXTURE, 0.0F, 0.0F),
					(pose, buffer) -> {
						core(pose, buffer, size, light);
						shell(pose, buffer, color, light, size, age);
					});
		}

		/** A steady white column through his centre, brightest at his middle. */
		private static void core(PoseStack.Pose pose, VertexConsumer buffer, float size, float light) {
			float height = FEET - CROWN;
			int steps = 6;
			for (int s = 0; s < steps; s++) {
				float t = (float) s / (steps - 1);
				float y = FEET - t * height;
				float bright = 1.0F - Math.abs(t - 0.5F) * 1.4F;
				if (bright <= 0.0F || bright * light <= 0.05F) {
					continue;
				}
				int tint = ARGB.color(255, 235, 240, 255);
				float half = WIDTH * 0.35F * size * bright * light;
				ZFighterKiAura.Layer.vertex(pose, buffer, -half, y, -half, 0.5F, 0.5F, tint);
				ZFighterKiAura.Layer.vertex(pose, buffer, half, y, -half, 0.5F, 0.5F, tint);
				ZFighterKiAura.Layer.vertex(pose, buffer, half, y, half, 0.5F, 0.5F, tint);
				ZFighterKiAura.Layer.vertex(pose, buffer, -half, y, half, 0.5F, 0.5F, tint);
			}
		}

		/** Flame tongues licking up round his body, in his ki colour, each flickering on its own beat. */
		private static void shell(PoseStack.Pose pose, VertexConsumer buffer, int color, float light, float size,
				float age) {
			float height = FEET - CROWN;
			for (int i = 0; i < TONGUES; i++) {
				float a = (float) i / TONGUES * (float) Math.PI * 2.0F;
				float ca = Mth.cos(a), sa = Mth.sin(a);
				float rootY = FEET - (0.25F + 0.5F * Mth.sin(age * 0.2F + i)) * height;
				float rx = ca * 0.32F * size, rz = sa * 0.32F * size;
				float lean = Mth.sin(age * 0.5F + i * 0.8F) * 0.15F;
				float rise = LENGTH * size * (0.6F + 0.5F * Mth.sin(age * 0.45F + i * 0.9F));
				float dx = ca * (0.22F + lean) * size, dy = -rise, dz = sa * (0.22F + lean) * size;
				float norm = Mth.sqrt(dx * dx + dy * dy + dz * dz);
				dx /= norm;
				dy /= norm;
				dz /= norm;
				float l = rise;
				int tint = ZFighterKiAura.Layer.tint(color, light * (0.5F + 0.5F * Mth.sin(age * 0.6F + i)));
				float half = WIDTH * size * (0.7F + 0.4F * Mth.sin(age * 0.3F + i));
				float tx = -sa, tz = ca;
				ZFighterKiAura.Layer.vertex(pose, buffer, rx - tx * half, rootY, rz - tz * half, 0.0F, 1.0F, tint);
				ZFighterKiAura.Layer.vertex(pose, buffer, rx + tx * half, rootY, rz + tz * half, 1.0F, 1.0F, tint);
				ZFighterKiAura.Layer.vertex(pose, buffer, rx + tx * half + dx * l, rootY + dy * l, rz + tz * half + dz * l,
						1.0F, 0.0F, tint);
				ZFighterKiAura.Layer.vertex(pose, buffer, rx - tx * half + dx * l, rootY + dy * l, rz - tz * half + dz * l,
						0.0F, 0.0F, tint);
			}
		}
	}
}
