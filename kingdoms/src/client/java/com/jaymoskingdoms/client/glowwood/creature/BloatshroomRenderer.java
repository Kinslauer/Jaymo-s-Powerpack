package com.jaymoskingdoms.client.glowwood.creature;

import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.client.glowwood.CreatureRenderState;
import com.jaymoskingdoms.client.glowwood.CreatureRenderer;
import com.jaymoskingdoms.glowwood.creatures.BloatshroomEntity;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

public class BloatshroomRenderer extends CreatureRenderer<BloatshroomEntity, BloatshroomModel> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(JaymosKingdoms.id("bloatshroom"), "main");
	private static final Identifier TEXTURE = JaymosKingdoms.id("textures/entity/glowwood/bloatshroom/bloatshroom.png");

	public BloatshroomRenderer(EntityRendererProvider.Context context) {
		super(context, new BloatshroomModel(context.bakeLayer(LAYER)), 0.45F);
	}

	@Override
	protected Identifier texture(BloatshroomEntity entity) {
		return TEXTURE;
	}

	/** Flashes white faster and faster as the fuse runs down, like a creeper. */
	protected float getWhiteOverlayProgress(CreatureRenderState<BloatshroomEntity> state) {
		if (state.entity == null) {
			return 0.0F;
		}
		float f = state.entity.getSwelling(state.partialTick);
		return (int) (f * 10.0F) % 2 == 0 ? 0.0F : Mth.clamp(f, 0.5F, 1.0F);
	}
}
