package com.jaymospowers.mixin.alexscaves;

import com.jaymospowers.power.chrono.future.Future;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The steam towns' green fire is forbidden tech that only works in the future (Andy): a primed Alex's Caves nuclear bomb
 * anywhere but {@link Future#FUTURE} fizzles on its first tick and drops back as the bomb block. This covers every way
 * of lighting it (fire, redstone, the remote detonator, a projectile), since each one spawns this entity.
 */
@Pseudo
@Mixin(targets = "com.github.alexmodguy.alexscaves.server.entity.item.NuclearBombEntity", remap = false)
abstract class NuclearBombFutureMixin {
	@Inject(method = "tick", at = @At("HEAD"), cancellable = true, require = 0)
	private void jaymospowers$onlyInTheFuture(CallbackInfo ci) {
		Entity bomb = (Entity) (Object) this;
		if (!(bomb.level() instanceof ServerLevel level) || level.dimension() == Future.FUTURE) {
			return;
		}
		ci.cancel();
		level.playSound(null, bomb.blockPosition(), SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 1.0F, 0.6F);
		level.sendParticles(ParticleTypes.LARGE_SMOKE, bomb.getX(), bomb.getY() + 0.5, bomb.getZ(), 12, 0.3, 0.3, 0.3, 0.02);
		BuiltInRegistries.ITEM.getOptional(Identifier.fromNamespaceAndPath("alexscaves", "nuclear_bomb"))
			.ifPresent(item -> bomb.spawnAtLocation(level, new ItemStack(item)));
		bomb.discard();
	}
}
