package com.jaymoskingdoms.client.glowwood.myceliarch;

import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.client.glowwood.CreatureRenderState;
import com.jaymoskingdoms.client.glowwood.CreatureRenderer;
import com.jaymoskingdoms.client.glowwood.GlowLayer;
import com.jaymoskingdoms.glowwood.creatures.MyceliarchEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/**
 * Draws the Myceliarch with its emissive gills, veins and eyes: a slow pulse in the first bloom, full and with more
 * veins in the second, fading as it dies. It does not topple over on death (its own collapse animation plays) and
 * casts no shadow while it is still underground.
 */
public class MyceliarchRenderer extends CreatureRenderer<MyceliarchEntity, MyceliarchModel> {
	private static final Identifier TEXTURE = JaymosKingdoms.id("textures/entity/glowwood/myceliarch/myceliarch.png");
	private static final Identifier GLOW = JaymosKingdoms.id("textures/entity/glowwood/myceliarch/myceliarch_glow.png");
	private static final Identifier BLOOM_GLOW = JaymosKingdoms.id("textures/entity/glowwood/myceliarch/myceliarch_bloom_glow.png");
	private static final float SHADOW = 1.6F;

	public MyceliarchRenderer(EntityRendererProvider.Context context) {
		super(context, new MyceliarchModel(context.bakeLayer(MyceliarchModel.LAYER)), SHADOW);
		this.addLayer(new GlowLayer<>(this, e -> e.isBloom() ? BLOOM_GLOW : GLOW, MyceliarchRenderer::glow));
	}

	private static double glow(MyceliarchEntity entity) {
		int collapse = entity.getCollapseTicks();
		float fade = collapse > 0 ? Math.max(0.0F, 1.0F - collapse / 55.0F) : 1.0F;
		if (entity.isBloom()) {
			return (0.9 + 0.1 * Mth.sin(entity.tickCount * 0.35F)) * fade;
		}
		return (0.7 + 0.15 * Mth.sin(entity.tickCount * 0.08F)) * fade;
	}

	@Override
	protected Identifier texture(MyceliarchEntity entity) {
		return TEXTURE;
	}

	/** No shadow while it is still underground or sinking back into the ground. */
	public float getShadowRadius(CreatureRenderState<MyceliarchEntity> state) {
		MyceliarchEntity entity = state.entity;
		if (entity == null) {
			return SHADOW;
		}
		float underground = 0.0F;
		if (entity.isEmerging() && entity.emergeState.isStarted()) {
			float t = entity.emergeState.getTimeInMillis(state.ageInTicks) / 1000.0F;
			underground = t < 3.4F ? 1.0F : Math.max(0.0F, 1.0F - (t - 3.4F) / 1.2F);
		}
		int collapse = entity.getCollapseTicks();
		float sinking = collapse > 30 ? Math.min(1.0F, (collapse - 30) / 25.0F) : 0.0F;
		return SHADOW * (1.0F - Math.max(underground, sinking));
	}

	/** It does not topple over on death: its own collapse animation plays. */
	public float getFlipDegrees() {
		return 0.0F;
	}
}
