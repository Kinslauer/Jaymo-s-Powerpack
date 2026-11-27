package com.jaymoskingdoms.client.glowwood.myceliarch;

import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.client.glowwood.CreatureRenderState;
import com.jaymoskingdoms.client.glowwood.RigModel;
import com.jaymoskingdoms.client.glowwood.anim.MyceliarchAnimations;
import com.jaymoskingdoms.glowwood.creatures.MyceliarchEntity;
import net.minecraft.client.animation.KeyframeAnimation;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;

/** The Myceliarch: geometry and animations come from tools/assets/ss/entities/myceliarch.py. */
public class MyceliarchModel extends RigModel<MyceliarchEntity> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(JaymosKingdoms.id("myceliarch"), "main");
	private final ModelPart head;
	private final KeyframeAnimation walk;
	private final KeyframeAnimation idle;
	private final KeyframeAnimation emerge;
	private final KeyframeAnimation slam;
	private final KeyframeAnimation erupt;
	private final KeyframeAnimation nova;
	private final KeyframeAnimation brood;
	private final KeyframeAnimation roar;
	private final KeyframeAnimation rain;
	private final KeyframeAnimation death;

	public MyceliarchModel(ModelPart root) {
		super(root);
		this.head = part("head");
		this.walk = bake(MyceliarchAnimations.WALK);
		this.idle = bake(MyceliarchAnimations.IDLE);
		this.emerge = bake(MyceliarchAnimations.EMERGE);
		this.slam = bake(MyceliarchAnimations.SLAM);
		this.erupt = bake(MyceliarchAnimations.ERUPT);
		this.nova = bake(MyceliarchAnimations.NOVA);
		this.brood = bake(MyceliarchAnimations.BROOD);
		this.roar = bake(MyceliarchAnimations.ROAR);
		this.rain = bake(MyceliarchAnimations.RAIN);
		this.death = bake(MyceliarchAnimations.DEATH);
	}

	@Override
	protected void animate(MyceliarchEntity entity, CreatureRenderState<MyceliarchEntity> state, float limbSwing,
						   float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
		boolean free = entity.getState() == MyceliarchEntity.IDLE && entity.getCollapseTicks() == 0;
		if (free) {
			look(this.head, netHeadYaw * 0.6F, headPitch * 0.6F, 35.0F, 25.0F);
			this.walk.applyWalk(limbSwing, limbSwingAmount, 2.0F, 2.2F);
		}
		this.idle.apply(entity.idleState, ageInTicks);
		this.emerge.apply(entity.emergeState, ageInTicks);
		this.slam.apply(entity.slamState, ageInTicks);
		this.erupt.apply(entity.eruptState, ageInTicks);
		this.nova.apply(entity.novaState, ageInTicks);
		this.brood.apply(entity.broodState, ageInTicks);
		this.roar.apply(entity.roarState, ageInTicks);
		this.rain.apply(entity.rainState, ageInTicks);
		this.death.apply(entity.deathState, ageInTicks);
	}
}
