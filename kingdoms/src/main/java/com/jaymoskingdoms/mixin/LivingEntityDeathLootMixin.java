package com.jaymoskingdoms.mixin;

import com.jaymoskingdoms.NpcPockets;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * An NPC's belt and pockets drop as its gear does (NpcPockets#died): inside the death's drops, so Jaymo's Kitchen's corpse
 * gathers them onto the body's loot screen.
 */
@Mixin(LivingEntity.class)
abstract class LivingEntityDeathLootMixin {
	@Inject(method = "dropAllDeathLoot(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;)V",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;dropEquipment(Lnet/minecraft/server/level/ServerLevel;)V"))
	private void jaymoskingdoms$pockets(ServerLevel level, DamageSource source, CallbackInfo ci) {
		NpcPockets.died(level, (LivingEntity) (Object) this);
	}
}
