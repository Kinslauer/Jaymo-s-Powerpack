package com.jaymoskingdoms.mixin.mca;

import com.jaymoskingdoms.DialogueCue;
import net.conczin.mca.entity.interaction.EntityCommandHandler;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Opening an MCA villager's talk menu starts the talk ({@link com.jaymoskingdoms.Conversations}): they stand still,
 * turned to you, from now until it's over, not only while the menu is open.
 */
@Mixin(EntityCommandHandler.class)
abstract class McaTalkHoldMixin {
	@Shadow
	@Final
	protected Entity entity;

	@Inject(method = "interactAt", at = @At("RETURN"))
	private void jaymoskingdoms$talkBegins(Player player, Vec3 pos, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
		if (player instanceof ServerPlayer sp) {
			DialogueCue.begin(sp, this.entity);
		}
	}
}
