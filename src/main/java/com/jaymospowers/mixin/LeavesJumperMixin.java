package com.jaymospowers.mixin;

import com.jaymospowers.core.Leaves;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/** Lets {@link Leaves} see whether a living thing is holding Jump (to climb up through leaves). */
@Mixin(LivingEntity.class)
abstract class LeavesJumperMixin implements Leaves.Jumper {
	@Shadow
	protected boolean jumping;

	@Override
	public boolean jaymospowers$jumping() {
		return this.jumping;
	}
}
