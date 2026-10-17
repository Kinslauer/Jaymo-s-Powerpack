package com.jaymoskitchen.client.mixin;

import com.jaymoskitchen.corpse.Butchers;
import com.jaymoskitchen.corpse.CorpseEntity;
import com.jaymoskitchen.corpse.Corpses;
import com.jaymoskitchen.net.Payloads;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Carrying a corpse, right-clicking anywhere (at the air too, which normally sends nothing with an empty hand) lets go
 * of it: it drops from the arms, or with sneak held it's thrown a little way ahead. Only a plain right-click on a
 * butcher block goes through as usual, to lay it on top, and one on a butcher villager, to offer it to them.
 */
@Mixin(Minecraft.class)
abstract class MinecraftUseMixin {
	@Shadow
	public @Nullable LocalPlayer player;

	@Shadow
	public @Nullable HitResult hitResult;

	@Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
	private void jaymoskitchen$dropCarried(CallbackInfo ci) {
		if (player == null || CorpseEntity.carriedBy(player) == null) {
			return;
		}
		boolean toss = player.isShiftKeyDown();
		if (!toss && hitResult instanceof BlockHitResult block && block.getType() == HitResult.Type.BLOCK
			&& Corpses.isButcherBlock(player.level().getBlockState(block.getBlockPos()))) {
			return;
		}
		// (0.4.32) a plain right-click on a butcher villager offers them the body
		if (!toss && hitResult instanceof EntityHitResult entity && Butchers.isButcher(entity.getEntity())) {
			return;
		}
		// (0.4.48) and one on a horse slings it over the horse's back
		if (!toss && hitResult instanceof EntityHitResult entity && entity.getEntity() instanceof net.minecraft.world.entity.animal.equine.AbstractHorse) {
			return;
		}
		// sneaking throws it a little way ahead (onto the ground, or into a cart); otherwise it just drops
		ClientPlayNetworking.send(new Payloads.DropCarried(toss));
		ci.cancel();
	}
}
