package com.jaymospowers.client.power.chrono;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.power.chrono.future.FutureTrooper;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.ArmorModelSet;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.resources.Identifier;

/**
 * A Frostborn trooper of the future (FutureTrooper) on the humanoid model: a frost-pale body in a flight suit
 * (tools/gen_future_machines.py) under the whole Rig, drawn by the Rig's own armour layer as on an armour stand.
 */
public class FutureTrooperRenderer extends HumanoidMobRenderer<FutureTrooper, HumanoidRenderState, HumanoidModel<HumanoidRenderState>> {
	static final Identifier SKIN = JaymosPowers.id("textures/entity/future/frostborn_trooper.png");

	public FutureTrooperRenderer(EntityRendererProvider.Context context) {
		super(context, new HumanoidModel<>(context.bakeLayer(ModelLayers.ZOMBIE)), 0.5F);
		this.addLayer(new HumanoidArmorLayer<>(this, ArmorModelSet.bake(ModelLayers.ZOMBIE_ARMOR, context.getModelSet(), HumanoidModel::new),
			context.getEquipmentRenderer()));
	}

	@Override
	public HumanoidRenderState createRenderState() {
		return new HumanoidRenderState();
	}

	@Override
	public Identifier getTextureLocation(HumanoidRenderState state) {
		return SKIN;
	}
}
