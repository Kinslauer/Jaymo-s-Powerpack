package com.jaymoskingdoms.client.glowwood.creature;

import com.jaymoskingdoms.client.glowwood.CreatureAnim;
import com.jaymoskingdoms.client.glowwood.CreatureRenderState;
import com.jaymoskingdoms.client.glowwood.RigModel;
import com.jaymoskingdoms.client.glowwood.anim.BloatshroomAnimations;
import com.jaymoskingdoms.glowwood.creatures.BloatshroomEntity;
import net.minecraft.client.animation.KeyframeAnimation;
import net.minecraft.client.model.geom.ModelPart;

public class BloatshroomModel extends RigModel<BloatshroomEntity> {
	private final KeyframeAnimation walk;
	private final KeyframeAnimation idle;
	private final KeyframeAnimation swell;

	public BloatshroomModel(ModelPart root) {
		super(root);
		this.walk = bake(BloatshroomAnimations.WALK);
		this.idle = bake(BloatshroomAnimations.IDLE);
		this.swell = bake(BloatshroomAnimations.SWELL);
	}

	@Override
	protected void animate(BloatshroomEntity entity, CreatureRenderState<BloatshroomEntity> state, float limbSwing,
						   float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
		float swelling = entity.getSwelling(state.partialTick);
		this.walk.applyWalk(limbSwing, limbSwingAmount, 3.0F, 2.5F);
		CreatureAnim.loop(this.idle, ageInTicks, 1.0F, 1.0F - Math.min(1.0F, swelling * 2.0F));
		if (swelling > 0.0F) {
			CreatureAnim.at(this.swell, BloatshroomAnimations.SWELL, swelling, 1.0F);
		}
	}
}
