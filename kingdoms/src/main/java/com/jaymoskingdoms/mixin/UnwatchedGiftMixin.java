package com.jaymoskingdoms.mixin;

import com.jaymoskingdoms.Windfall;
import java.util.function.BiConsumer;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootTable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * What an animal leaves of its own accord (a chicken's egg, an armadillo's scute, a sniffer's seed, a panda's sneeze) isn't
 * left unless a player is near ({@link Windfall#unwatched}); the animal waits its usual time for the next. A cat's
 * morning gift and a villager's gift to a hero always have their player beside them.
 */
@Mixin(LivingEntity.class)
abstract class UnwatchedGiftMixin {
	@Inject(method = "dropFromGiftLootTable", at = @At("HEAD"), cancellable = true)
	private void jaymoskingdoms$unwatched(ServerLevel level, ResourceKey<LootTable> table, BiConsumer<ServerLevel, ItemStack> drop,
		CallbackInfoReturnable<Boolean> cir) {
		LivingEntity self = (LivingEntity) (Object) this;
		if (Windfall.unwatched(level, self.getX(), self.getY(), self.getZ())) {
			cir.setReturnValue(false);
		}
	}
}
