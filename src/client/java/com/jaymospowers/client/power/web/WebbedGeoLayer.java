package com.jaymospowers.client.power.web;

import com.geckolib.renderer.base.GeoRenderer;
import com.geckolib.renderer.base.RenderPassInfo;
import com.geckolib.renderer.layer.GeoRenderLayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;

/**
 * {@link WebbedLook} on GeckoLib's bodies (a Kingdoms Imp, a Giant, a town golem): its renderer isn't a living one, so
 * the web skin is its model drawn again in the web (as GeckoLib's own texture layers are), and the tufts stand round it.
 * Added to every GeckoLib entity renderer as it's made ({@code WebbedGeoRendererMixin}).
 */
@SuppressWarnings({"rawtypes", "unchecked"})
public final class WebbedGeoLayer extends GeoRenderLayer {
	public WebbedGeoLayer(GeoRenderer renderer) {
		super(renderer);
	}

	@Override
	public void submitRenderTask(RenderPassInfo info, SubmitNodeCollector collector) {
		if (!info.willRender() || !(info.renderState() instanceof EntityRenderState state) || state.isInvisible) {
			return;
		}
		WebbedLook.Look look = state.getData(WebbedLook.KEY);
		if (look == null) {
			return;
		}
		this.renderer.submitRenderTasks(info, collector.order(1), RenderTypes.entityCutout(WebbedLook.skin(look.layers())));
		WebbedLook.tufts(info.poseStack(), collector, look, info.packedLight(), true);
	}
}
