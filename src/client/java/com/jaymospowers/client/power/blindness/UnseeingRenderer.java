package com.jaymospowers.client.power.blindness;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.power.blindness.Unseeing;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.resources.Identifier;

/**
 * The Unseeing ({@link Unseeing}) on the humanoid model, in plain robes: a follower with their eyes, the blindfolded
 * (a black band round the head), the gouged (linen bound over the sockets, blood showing through) and the zealous (the
 * linen soaked, blood run down the face). The skins are our own pixel work.
 */
public class UnseeingRenderer extends HumanoidMobRenderer<Unseeing, HumanoidRenderState, HumanoidModel<HumanoidRenderState>> {
	static final Identifier[] SKINS = new Identifier[Unseeing.Look.values().length];
	static final RenderStateDataKey<Integer> LOOK = RenderStateDataKey.create(() -> "jaymospowers:unseeing_look");

	static {
		for (Unseeing.Look look : Unseeing.Look.values()) {
			SKINS[look.ordinal()] = JaymosPowers.id("textures/entity/unseeing/" + look.name().toLowerCase(java.util.Locale.ROOT) + ".png");
		}
	}

	public UnseeingRenderer(EntityRendererProvider.Context context) {
		super(context, new HumanoidModel<>(context.bakeLayer(ModelLayers.ZOMBIE)), 0.5F);
	}

	@Override
	public HumanoidRenderState createRenderState() {
		return new HumanoidRenderState();
	}

	@Override
	public void extractRenderState(Unseeing entity, HumanoidRenderState state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		state.setData(LOOK, entity.look().ordinal());
	}

	@Override
	public Identifier getTextureLocation(HumanoidRenderState state) {
		Integer look = state.getData(LOOK);
		return SKINS[look == null ? 1 : look];
	}
}
