package com.jaymoskingdoms.client.glowwood;

import com.jaymoskingdoms.client.glowwood.creature.BloatshroomRenderer;
import com.jaymoskingdoms.client.glowwood.creature.GlowmothRenderer;
import com.jaymoskingdoms.client.glowwood.creature.MimicapRenderer;
import com.jaymoskingdoms.client.glowwood.creature.ShamblerRenderer;
import com.jaymoskingdoms.client.glowwood.creature.SporelingRenderer;
import com.jaymoskingdoms.client.glowwood.creature.TrufflerRenderer;
import com.jaymoskingdoms.client.glowwood.geo.BloatshroomGeometry;
import com.jaymoskingdoms.client.glowwood.geo.GlowmothGeometry;
import com.jaymoskingdoms.client.glowwood.geo.MimicapGeometry;
import com.jaymoskingdoms.client.glowwood.geo.MyceliarchGeometry;
import com.jaymoskingdoms.client.glowwood.geo.RootSpikeGeometry;
import com.jaymoskingdoms.client.glowwood.geo.ShamblerGeometry;
import com.jaymoskingdoms.client.glowwood.geo.SporelingGeometry;
import com.jaymoskingdoms.client.glowwood.geo.TrufflerGeometry;
import com.jaymoskingdoms.client.glowwood.myceliarch.MyceliarchModel;
import com.jaymoskingdoms.client.glowwood.myceliarch.MyceliarchRenderer;
import com.jaymoskingdoms.client.glowwood.rootspike.RootSpikeModel;
import com.jaymoskingdoms.client.glowwood.rootspike.RootSpikeRenderer;
import com.jaymoskingdoms.glowwood.creatures.GlowCreatures;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;

/** The glow-wood creatures' renderers and model layers (their models are the ported Spore &amp; Stem ones, built in code). */
public final class GlowCreaturesClient {
	private GlowCreaturesClient() {
	}

	public static void register() {
		ModelLayerRegistry.registerModelLayer(SporelingRenderer.LAYER, SporelingGeometry::createBodyLayer);
		ModelLayerRegistry.registerModelLayer(TrufflerRenderer.LAYER, TrufflerGeometry::createBodyLayer);
		ModelLayerRegistry.registerModelLayer(GlowmothRenderer.LAYER, GlowmothGeometry::createBodyLayer);
		ModelLayerRegistry.registerModelLayer(MimicapRenderer.LAYER, MimicapGeometry::createBodyLayer);
		ModelLayerRegistry.registerModelLayer(BloatshroomRenderer.LAYER, BloatshroomGeometry::createBodyLayer);
		ModelLayerRegistry.registerModelLayer(ShamblerRenderer.LAYER, ShamblerGeometry::createBodyLayer);
		ModelLayerRegistry.registerModelLayer(MyceliarchModel.LAYER, MyceliarchGeometry::createBodyLayer);
		ModelLayerRegistry.registerModelLayer(RootSpikeModel.LAYER, RootSpikeGeometry::createBodyLayer);
		EntityRenderers.register(GlowCreatures.SPORELING, SporelingRenderer::new);
		EntityRenderers.register(GlowCreatures.TRUFFLER, TrufflerRenderer::new);
		EntityRenderers.register(GlowCreatures.GLOWMOTH, GlowmothRenderer::new);
		EntityRenderers.register(GlowCreatures.MIMICAP, MimicapRenderer::new);
		EntityRenderers.register(GlowCreatures.BLOATSHROOM, BloatshroomRenderer::new);
		EntityRenderers.register(GlowCreatures.SHAMBLER, ShamblerRenderer::new);
		EntityRenderers.register(GlowCreatures.MYCELIARCH, MyceliarchRenderer::new);
		EntityRenderers.register(GlowCreatures.ROOT_SPIKE, RootSpikeRenderer::new);
		EntityRenderers.register(GlowCreatures.SPORE_GLOB, ThrownItemRenderer::new);
	}
}
