package com.jaymoskingdoms.mixin.locks;

import com.jaymoskingdoms.ChestNoise;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** (0.7.538) A chest or barrel opened by a light-fingered thief creaks quieter ({@link ChestNoise#hush}). */
@Mixin(ServerLevel.class)
abstract class ChestCreakVolumeMixin {
	@ModifyVariable(method = "playSeededSound(Lnet/minecraft/world/entity/Entity;DDDLnet/minecraft/core/Holder;Lnet/minecraft/sounds/SoundSource;FFJ)V",
		at = @At("HEAD"), ordinal = 0, argsOnly = true)
	private float jaymoskingdoms$hushCreak(float volume, Entity except, double x, double y, double z, Holder<SoundEvent> sound, SoundSource source) {
		return source == SoundSource.BLOCKS ? volume * ChestNoise.hush() : volume;
	}
}
