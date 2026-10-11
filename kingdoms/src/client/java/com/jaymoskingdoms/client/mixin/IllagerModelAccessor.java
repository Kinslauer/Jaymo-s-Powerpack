package com.jaymoskingdoms.client.mixin;

import com.jaymoskingdoms.client.MobAnimations;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.monster.illager.IllagerModel;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/** An illager's limbs, for a vindicator's Better Combat swing (MobAnimations). */
@Mixin(IllagerModel.class)
abstract class IllagerModelAccessor implements MobAnimations.IllagerParts {
	@Shadow
	@Final
	private ModelPart head;
	@Shadow
	@Final
	private ModelPart rightArm;
	@Shadow
	@Final
	private ModelPart leftArm;
	@Shadow
	@Final
	private ModelPart rightLeg;
	@Shadow
	@Final
	private ModelPart leftLeg;

	@Override
	public ModelPart jaymoskingdoms$head() {
		return this.head;
	}

	@Override
	public ModelPart jaymoskingdoms$rightArm() {
		return this.rightArm;
	}

	@Override
	public ModelPart jaymoskingdoms$leftArm() {
		return this.leftArm;
	}

	@Override
	public ModelPart jaymoskingdoms$rightLeg() {
		return this.rightLeg;
	}

	@Override
	public ModelPart jaymoskingdoms$leftLeg() {
		return this.leftLeg;
	}
}
