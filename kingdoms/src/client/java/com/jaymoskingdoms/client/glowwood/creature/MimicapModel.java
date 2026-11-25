package com.jaymoskingdoms.client.glowwood.creature;

import com.jaymoskingdoms.client.glowwood.CreatureAnim;
import com.jaymoskingdoms.client.glowwood.CreatureRenderState;
import com.jaymoskingdoms.client.glowwood.RigModel;
import com.jaymoskingdoms.client.glowwood.anim.MimicapAnimations;
import com.jaymoskingdoms.glowwood.creatures.MimicapEntity;
import net.minecraft.client.animation.KeyframeAnimation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

public class MimicapModel extends RigModel<MimicapEntity> {
	private final ModelPart cap;
	private final KeyframeAnimation hidden;
	private final KeyframeAnimation wake;
	private final KeyframeAnimation settle;
	private final KeyframeAnimation walk;
	private final KeyframeAnimation idle;
	private final KeyframeAnimation bite;

	public MimicapModel(ModelPart root) {
		super(root);
		this.cap = part("cap");
		this.hidden = bake(MimicapAnimations.HIDDEN);
		this.wake = bake(MimicapAnimations.WAKE);
		this.settle = bake(MimicapAnimations.SETTLE);
		this.walk = bake(MimicapAnimations.WALK);
		this.idle = bake(MimicapAnimations.IDLE);
		this.bite = bake(MimicapAnimations.BITE);
	}

	@Override
	protected void animate(MimicapEntity entity, CreatureRenderState<MimicapEntity> state, float limbSwing,
						   float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
		switch (entity.getPhase()) {
			case MimicapEntity.HIDDEN -> CreatureAnim.loop(this.hidden, ageInTicks);
			case MimicapEntity.WAKING -> this.wake.apply(entity.wakeState, ageInTicks);
			case MimicapEntity.SETTLING -> this.settle.apply(entity.settleState, ageInTicks);
			default -> {
				this.walk.applyWalk(limbSwing, limbSwingAmount, 4.0F, 2.0F);
				CreatureAnim.loop(this.idle, ageInTicks, 1.0F, 1.0F - Math.min(1.0F, limbSwingAmount * 3.0F));
				this.bite.apply(entity.biteState, ageInTicks);
				cap.yRot += Mth.clamp(netHeadYaw, -25.0F, 25.0F) * Mth.DEG_TO_RAD * 0.4F;
			}
		}
	}
}
