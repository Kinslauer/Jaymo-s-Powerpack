package com.jaymoskingdoms.client.glowwood.rootspike;

import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.client.glowwood.CreatureRenderState;
import com.jaymoskingdoms.client.glowwood.RigModel;
import com.jaymoskingdoms.client.glowwood.anim.RootSpikeAnimations;
import com.jaymoskingdoms.glowwood.creatures.RootSpikeEntity;
import net.minecraft.client.animation.KeyframeAnimation;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;

/** Root Spike: geometry and animation come from tools/assets/ss/entities/root_spike.py. */
public class RootSpikeModel extends RigModel<RootSpikeEntity> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(JaymosKingdoms.id("root_spike"), "main");
	private final KeyframeAnimation erupt;

	public RootSpikeModel(ModelPart root) {
		super(root);
		this.erupt = bake(RootSpikeAnimations.ERUPT);
	}

	@Override
	protected void animate(RootSpikeEntity entity, CreatureRenderState<RootSpikeEntity> state, float limbSwing,
						   float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
		this.erupt.apply(entity.eruptState, ageInTicks);
	}
}
