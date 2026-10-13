package com.jaymoskingdoms.mixin;

import com.jaymoskingdoms.ClassPerks;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * (0.6.0) A Brute's raised shield ({@link ClassPerks#blocked}): what a shield didn't stop (the damage that bypasses
 * shields, and the leftover of a partial block) is halved on its way through.
 */
@Mixin(Player.class)
abstract class PlayerBlockingMixin {
	@ModifyVariable(method = "actuallyHurt", at = @At("HEAD"), argsOnly = true)
	private float jaymoskingdoms$bruteBlock(float amount, ServerLevel level, DamageSource source) {
		return (Object) this instanceof ServerPlayer player && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) ? ClassPerks.blocked(player, amount) : amount;
	}
}
