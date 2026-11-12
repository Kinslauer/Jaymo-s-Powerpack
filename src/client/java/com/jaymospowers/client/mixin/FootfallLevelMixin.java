package com.jaymospowers.client.mixin;

import com.jaymospowers.client.sound.Footfalls;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Every step sound in the world gets its ground and armour layers, and a sneaking walker's step is quieter (see Footfalls). */
@Mixin(ClientLevel.class)
abstract class FootfallLevelMixin {
	@WrapOperation(method = "playSound(DDDLnet/minecraft/sounds/SoundEvent;Lnet/minecraft/sounds/SoundSource;FFZJ)V",
		at = @At(value = "NEW", target = "(Lnet/minecraft/sounds/SoundEvent;Lnet/minecraft/sounds/SoundSource;FFLnet/minecraft/util/RandomSource;DDD)Lnet/minecraft/client/resources/sounds/SimpleSoundInstance;"))
	private SimpleSoundInstance jaymospowers$footfall(SoundEvent sound, SoundSource source, float volume, float pitch, RandomSource random,
		double x, double y, double z, Operation<SimpleSoundInstance> original) {
		return original.call(sound, source, Footfalls.heard((ClientLevel)(Object)this, sound, source, volume, x, y, z), pitch, random, x, y, z);
	}
}
