package com.jaymospowers.alchemy.codex;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

/** The Apothecary's Codex: use it to open the book (the screen is the client's, the server only says when). */
public class CodexItem extends Item {
	public CodexItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (!com.jaymospowers.core.TreeLocks.check(player, com.jaymospowers.core.TreeLocks.ALCHEMIST)) {
			// (Andy's skill-tree locks) the codex reads only for an alchemist (the Trades page)
			return InteractionResult.FAIL;
		}
		if (player instanceof ServerPlayer server && ServerPlayNetworking.canSend(server, CodexNet.Open.TYPE)) {
			ServerPlayNetworking.send(server, new CodexNet.Open());
		}
		return InteractionResult.SUCCESS;
	}
}
