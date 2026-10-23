package com.jaymospowers.mixin;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** A body's own hurt cry, from outside it (a downed mob groans with it: {@code DownedCrawl}). */
@Mixin(LivingEntity.class)
public interface LivingEntityVoiceAccessor {
	@Invoker("getHurtSound")
	@Nullable SoundEvent jaymospowers$hurtSound(DamageSource source);
}
