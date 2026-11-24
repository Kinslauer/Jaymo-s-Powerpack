package com.jaymoskingdoms.client.illagers;

import com.jaymoskingdoms.illagers.ModEntityTypes;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshTransformer;
import net.minecraft.client.model.monster.illager.IllagerModel;
import net.minecraft.client.model.monster.skeleton.SkeletonModel;
import net.minecraft.client.model.object.book.BookModel;
import net.minecraft.client.renderer.entity.EntityRenderers;

/** How the illager kinds ported from Illager Invasion (MIT) are drawn: its models and textures, its renderers. */
public final class IllagerKindsClient {
	private IllagerKindsClient() {
	}

	public static void register() {
		EntityRenderers.register(ModEntityTypes.PROVOKER_ENTITY_TYPE, ProvokerRenderer::new);
		EntityRenderers.register(ModEntityTypes.INVOKER_ENTITY_TYPE, InvokerRenderer::new);
		EntityRenderers.register(ModEntityTypes.SURRENDERED_ENTITY_TYPE, SurrenderedRenderer::new);
		EntityRenderers.register(ModEntityTypes.BASHER_ENTITY_TYPE, BasherRenderer::new);
		EntityRenderers.register(ModEntityTypes.ARCHIVIST_ENTITY_TYPE, ArchivistRenderer::new);
		EntityRenderers.register(ModEntityTypes.INQUISITOR_ENTITY_TYPE, InquisitorRenderer::new);
		EntityRenderers.register(ModEntityTypes.MARAUDER_ENTITY_TYPE, MarauderRenderer::new);
		EntityRenderers.register(ModEntityTypes.ALCHEMIST_ENTITY_TYPE, AlchemistRenderer::new);
		EntityRenderers.register(ModEntityTypes.FIRECALLER_ENTITY_TYPE, FirecallerRenderer::new);
		EntityRenderers.register(ModEntityTypes.INVOKER_FANGS_ENTITY_TYPE, InvokerFangsRenderer::new);
		EntityRenderers.register(ModEntityTypes.HATCHET_ENTITY_TYPE, ThrownHatchetRenderer::new);
		EntityRenderers.register(ModEntityTypes.FLYING_MAGMA_ENTITY_TYPE, FlyingMagmaRenderer::new);

		ModelLayerRegistry.registerModelLayer(ModModelLayers.ARCHIVIST, () -> IllagerModel.createBodyLayer().apply(CustomIllagerModel.SCALE_TRANSFORMER));
		ModelLayerRegistry.registerModelLayer(ModModelLayers.ARCHIVIST_BOOK, BookModel::createBodyLayer);
		MeshTransformer surrendered = MeshTransformer.scaling(0.85F);
		ModelLayerRegistry.registerModelLayer(ModModelLayers.SURRENDERED, () -> SkeletonModel.createBodyLayer().apply(surrendered));
		ModelLayerRegistry.registerArmorModelLayers(ModModelLayers.SURRENDERED_ARMOR,
			() -> HumanoidModel.createArmorMeshSet(new CubeDeformation(0.5F), new CubeDeformation(1.0F))
				.map(mesh -> LayerDefinition.create(mesh, 64, 32)));
		ModelLayerRegistry.registerModelLayer(ModModelLayers.PROVOKER, () -> IllagerModel.createBodyLayer().apply(CustomIllagerModel.SCALE_TRANSFORMER));
		ModelLayerRegistry.registerModelLayer(ModModelLayers.INVOKER, () -> InvokerModel.createBodyLayer(CubeDeformation.NONE).apply(CustomIllagerModel.SCALE_TRANSFORMER));
		ModelLayerRegistry.registerModelLayer(ModModelLayers.INVOKER_ARMOR,
			() -> InvokerModel.createBodyLayer(new CubeDeformation(0.5F)).apply(CustomIllagerModel.SCALE_TRANSFORMER));
		ModelLayerRegistry.registerModelLayer(ModModelLayers.INVOKER_FANGS, InvokerFangsModel::createBodyLayer);
		ModelLayerRegistry.registerModelLayer(ModModelLayers.INQUISITOR, () -> InquisitorModel.createBodyLayer().apply(MeshTransformer.scaling(1.1F)));
		ModelLayerRegistry.registerModelLayer(ModModelLayers.FIRECALLER, () -> FirecallerModel.createBodyLayer().apply(CustomIllagerModel.SCALE_TRANSFORMER));
		ModelLayerRegistry.registerModelLayer(ModModelLayers.ALCHEMIST, () -> AlchemistRenderer.createBodyLayer().apply(CustomIllagerModel.SCALE_TRANSFORMER));
		ModelLayerRegistry.registerModelLayer(ModModelLayers.BASHER, () -> IllagerModel.createBodyLayer().apply(CustomIllagerModel.SCALE_TRANSFORMER));
		ModelLayerRegistry.registerModelLayer(ModModelLayers.MARAUDER, () -> IllagerModel.createBodyLayer().apply(CustomIllagerModel.SCALE_TRANSFORMER));
	}
}
