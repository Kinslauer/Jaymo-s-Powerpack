package com.jaymospowers.mixin;

import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Whether a creature has died (its {@code die} ran), not only run out of health ({@code Downed}'s limbo watch). */
@Mixin(LivingEntity.class)
public interface LivingEntityDeadAccessor {
	@Accessor("dead")
	boolean jaymospowers$dead();
}
