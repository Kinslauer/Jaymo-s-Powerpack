package com.jaymoskingdoms.client.inhabitants;

import com.jaymoskingdoms.inhabitants.YouthWisp;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;

/**
 * Youth, the wood-wisp: drawn see-through and glowing (its wings and trail are half-there light), and lit as if it stood
 * in full light wherever it is, so it shows in a dark cave. Its head turns to whoever it faces.
 */
public class YouthWispRenderer extends InhabitantRenderer<YouthWisp> {
	/** Its size against the model, and its shadow (blocks): a faint one, being mostly light. */
	static final float SCALE = 1.0F, SHADOW = 0.15F;

	public YouthWispRenderer(EntityRendererProvider.Context context) {
		super(context, "youth_wisp", SCALE, SHADOW, "head");
	}

	@Override
	public RenderType getRenderType(LivingEntityRenderState renderState, Identifier texture) {
		return RenderTypes.entityTranslucentEmissive(texture);
	}

	@Override
	protected int getBlockLightLevel(YouthWisp entity, BlockPos pos) {
		return 15;
	}
}
