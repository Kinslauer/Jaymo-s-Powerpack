package com.jaymoskingdoms.client.glowwood;

import net.minecraft.client.animation.AnimationDefinition;
import net.minecraft.client.animation.KeyframeAnimation;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;

/**
 * Base for the generated-geometry creature models: the model's root is the "root" part, the pose is reset every frame
 * (so keyframed animations always start from the rest pose) and a head can turn toward where the creature looks.
 */
public abstract class RigModel<E extends Entity> extends EntityModel<CreatureRenderState<E>> {

	protected RigModel(ModelPart root) {
		super(root.getChild("root"));
	}

	@Override
	public void setupAnim(CreatureRenderState<E> state) {
		super.setupAnim(state);
		if (state.entity != null) {
			animate(state.entity, state, state.walkAnimationPos, state.walkAnimationSpeed, state.ageInTicks, state.yRot,
					state.xRot);
		}
	}

	/** Poses the model; called after the pose was reset. */
	protected abstract void animate(E entity, CreatureRenderState<E> state, float limbSwing, float limbSwingAmount,
									float ageInTicks, float netHeadYaw, float headPitch);

	/** Bakes a keyframed animation against this model's parts. */
	protected KeyframeAnimation bake(AnimationDefinition definition) {
		return definition.bake(this.root());
	}

	protected ModelPart part(String name) {
		ModelPart[] found = new ModelPart[1];
		this.root().getAllParts().forEach(p -> {
			if (found[0] == null && p.hasChild(name)) {
				found[0] = p.getChild(name);
			}
		});
		if (found[0] == null) {
			throw new IllegalArgumentException("No model part " + name);
		}
		return found[0];
	}

	/** Adds the look rotation (degrees from the renderer) to a head part, clamped. */
	protected static void look(ModelPart head, float netHeadYaw, float headPitch, float maxYaw, float maxPitch) {
		head.yRot += Mth.clamp(netHeadYaw, -maxYaw, maxYaw) * Mth.DEG_TO_RAD;
		head.xRot += Mth.clamp(headPitch, -maxPitch, maxPitch) * Mth.DEG_TO_RAD;
	}
}
