package com.jaymoskingdoms.mixin.fifth;

import com.jaymoskingdoms.Poisons;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A villager (or any mob) opening a door whose handle is smeared with poison is dosed by it ({@link Poisons#touchedDoor}). Players are caught by the use-block event. */
@Mixin(DoorBlock.class)
abstract class DoorHandleMixin {
	@Inject(method = "setOpen", at = @At("HEAD"))
	private void jaymoskingdoms$handle(Entity entity, Level level, BlockState state, BlockPos pos, boolean open, CallbackInfo ci) {
		if (open && entity != null && !(entity instanceof Player) && level instanceof ServerLevel server) {
			Poisons.touchedDoor(server, pos, entity);
		}
	}
}
