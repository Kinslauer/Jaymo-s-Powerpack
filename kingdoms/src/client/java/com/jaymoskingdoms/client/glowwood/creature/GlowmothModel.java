package com.jaymoskingdoms.client.glowwood.creature;

import com.jaymoskingdoms.client.glowwood.CreatureAnim;
import com.jaymoskingdoms.client.glowwood.CreatureRenderState;
import com.jaymoskingdoms.client.glowwood.RigModel;
import com.jaymoskingdoms.client.glowwood.anim.GlowmothAnimations;
import com.jaymoskingdoms.glowwood.creatures.GlowmothEntity;
import net.minecraft.client.animation.KeyframeAnimation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

public class GlowmothModel extends RigModel<GlowmothEntity> {
	private final ModelPart body;
	private final KeyframeAnimation fly;
	private final KeyframeAnimation hover;

	public GlowmothModel(ModelPart root) {
		super(root);
		this.body = part("body");
		this.fly = bake(GlowmothAnimations.FLY);
		this.hover = bake(GlowmothAnimations.HOVER);
	}

	@Override
	protected void animate(GlowmothEntity entity, CreatureRenderState<GlowmothEntity> state, float limbSwing,
						   float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
		double dx = entity.getX() - entity.xo;
		double dz = entity.getZ() - entity.zo;
		double dy = entity.getY() - entity.yo;
		float speed = (float) Math.sqrt(dx * dx + dz * dz);
		float flying = Mth.clamp(speed * 12.0F, 0.0F, 1.0F);
		// Each moth is a little out of step with its neighbours.
		float age = ageInTicks + (entity.getId() % 7) * 3.0F;
		CreatureAnim.loop(this.fly, age, 1.0F, flying);
		CreatureAnim.loop(this.hover, age, 1.0F, 1.0F - flying);
		body.xRot += Mth.clamp((float) dy * -8.0F, -0.5F, 0.5F);
	}
}
