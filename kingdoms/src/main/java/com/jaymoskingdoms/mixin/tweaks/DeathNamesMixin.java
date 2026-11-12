package com.jaymoskingdoms.mixin.tweaks;

import com.jaymoskingdoms.DeadNames;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * (Andy, 2026-11-12) A player's death message reads to each player as they knew the dead ({@link DeadNames#forViewer}):
 * sent one by one in place of the broadcast, to the same players the broadcast would have reached.
 */
@Mixin(ServerPlayer.class)
abstract class DeathNamesMixin {
	@WrapOperation(method = "die", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/server/players/PlayerList;broadcastSystemMessage(Lnet/minecraft/network/chat/Component;Z)V"), require = 0)
	private void jaymoskingdoms$toAll(PlayerList list, Component message, boolean overlay, Operation<Void> original) {
		ServerPlayer self = (ServerPlayer)(Object)this;
		list.getServer().sendSystemMessage(message);
		for (ServerPlayer p : list.getPlayers()) {
			p.sendSystemMessage(DeadNames.forViewer(message, self, self.getKillCredit(), p), overlay);
		}
	}

	@WrapOperation(method = "die", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/server/players/PlayerList;broadcastSystemToTeam(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/network/chat/Component;)V"),
		require = 0)
	private void jaymoskingdoms$toTeam(PlayerList list, Player player, Component message, Operation<Void> original) {
		ServerPlayer self = (ServerPlayer)(Object)this;
		if (self.getTeam() == null) {
			original.call(list, player, message);
			return;
		}
		for (ServerPlayer p : list.getPlayers()) {
			if (p.getTeam() == self.getTeam()) {
				p.sendSystemMessage(DeadNames.forViewer(message, self, self.getKillCredit(), p));
			}
		}
	}

	@WrapOperation(method = "die", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/server/players/PlayerList;broadcastSystemToAllExceptTeam(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/network/chat/Component;)V"),
		require = 0)
	private void jaymoskingdoms$toOthers(PlayerList list, Player player, Component message, Operation<Void> original) {
		ServerPlayer self = (ServerPlayer)(Object)this;
		if (self.getTeam() == null) {
			original.call(list, player, message);
			return;
		}
		for (ServerPlayer p : list.getPlayers()) {
			if (p.getTeam() != self.getTeam()) {
				p.sendSystemMessage(DeadNames.forViewer(message, self, self.getKillCredit(), p));
			}
		}
	}
}
