package com.jaymospowers.client.fishing;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.fishing.Species;
import com.jaymospowers.fishing.SpeciesSizes;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;

/**
 * Track C: draws one of the new species (a {@code WildFish} or a {@code Critter}) with its own model and texture, at its
 * real size ({@link SpeciesSizes}); its weight grows or shrinks it through its scale. A fish sways as it swims and flops
 * on its side out of the water; walkers just walk; a jellyfish glows through.
 */
public final class WildFishRenderer<T extends Mob> extends MobRenderer<T, LivingEntityRenderState, FishShapeModel> {
	private final Identifier texture;
	private final float size;
	private final boolean fish, flat;

	public WildFishRenderer(EntityRendererProvider.Context context, Species species) {
		super(context, FishShapeModel.create(species.id, species == Species.JELLYFISH), 0.3F);
		this.texture = JaymosPowers.id("textures/entity/fish/" + species.id + ".png");
		SpeciesSizes.Size s = SpeciesSizes.ALL.get(species.id);
		this.size = s == null ? 1.0F : s.scale();
		this.fish = !species.walker() && species != Species.JELLYFISH;
		this.flat = species == Species.HALIBUT;
		this.shadowRadius = s == null ? 0.3F : Math.min(1.5F, s.width() * 0.5F);
	}

	@Override
	public Identifier getTextureLocation(LivingEntityRenderState state) {
		return texture;
	}

	@Override
	public LivingEntityRenderState createRenderState() {
		return new LivingEntityRenderState();
	}

	@Override
	protected void scale(LivingEntityRenderState state, PoseStack poseStack) {
		poseStack.scale(size, size, size);
	}

	@Override
	protected void setupRotations(LivingEntityRenderState state, PoseStack poseStack, float bodyRot, float entityScale) {
		super.setupRotations(state, poseStack, bodyRot, entityScale);
		if (!fish) {
			return;
		}
		poseStack.rotateDegrees(Axis.YP, 3.0F * Mth.sin(0.6F * state.ageInTicks));
		if (!state.isInWater && !flat) {
			poseStack.translate(0.1F, 0.1F, 0.0F);
			poseStack.rotateDegrees(Axis.ZP, 90.0F);
		}
	}
}
