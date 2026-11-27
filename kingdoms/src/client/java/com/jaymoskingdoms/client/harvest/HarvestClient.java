package com.jaymoskingdoms.client.harvest;

import com.jaymoskingdoms.harvest.WickedHarvest;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.client.renderer.entity.NoopRenderer;
import net.minecraft.client.renderer.entity.UndeadHorseRenderer;
import net.minecraft.client.resources.model.EquipmentClientInfo;

/** The wicked harvest's drawing: the lich and its things (see {@link LichRenderer}, {@link PumpkinRenderers}). The steed is the skeleton horse's own model and bones. */
public final class HarvestClient {
	private HarvestClient() {
	}

	public static void register() {
		ModelLayerRegistry.registerModelLayer(LichRenderer.BODY, LichModel::createBodyLayer);
		ModelLayerRegistry.registerModelLayer(LichRenderer.GEM, LichRenderer::gemLayer);
		ModelLayerRegistry.registerModelLayer(LichRenderer.SCYTHE, LichRenderer::scytheLayer);
		ModelLayerRegistry.registerModelLayer(PumpkinRenderers.WICKED, PumpkinRenderers::wickedLayer);
		ModelLayerRegistry.registerModelLayer(PumpkinRenderers.THROWN, PumpkinRenderers::thrownLayer);
		EntityRenderers.register(WickedHarvest.LICH, LichRenderer::new);
		EntityRenderers.register(WickedHarvest.STEED, context -> new UndeadHorseRenderer(context, EquipmentClientInfo.LayerType.SKELETON_HORSE_SADDLE,
			ModelLayers.SKELETON_HORSE_SADDLE, UndeadHorseRenderer.Type.SKELETON, UndeadHorseRenderer.Type.SKELETON_BABY));
		EntityRenderers.register(WickedHarvest.PUMPKIN, PumpkinRenderers.Wicked::new);
		EntityRenderers.register(WickedHarvest.THROWN_PUMPKIN, PumpkinRenderers.Thrown::new);
		// (the wicked orb is only its trail of light)
		EntityRenderers.register(WickedHarvest.ORB, NoopRenderer::new);
	}
}
