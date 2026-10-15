package com.jaymospowers.mixin;

import com.jaymospowers.core.Leaves;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** Remembers the last tick leaves dragged on this entity, so a body in several leaves blocks is dragged once. */
@Mixin(Entity.class)
abstract class LeavesEntityMixin implements Leaves.Dragged {
	@Unique
	private int jaymospowers$leafTick = Integer.MIN_VALUE;

	@Override
	public int jaymospowers$leafTick() {
		return this.jaymospowers$leafTick;
	}

	@Override
	public void jaymospowers$leafTick(int tick) {
		this.jaymospowers$leafTick = tick;
	}
}
