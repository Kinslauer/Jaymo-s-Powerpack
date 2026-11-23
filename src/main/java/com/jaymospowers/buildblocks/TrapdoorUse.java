package com.jaymospowers.buildblocks;

import com.jaymospowers.buildblocks.mixin.TrapdoorTypeAccessor;
import java.util.Map;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Andy: "trapdoors need a more deliberate activation method ... as it is its too easy to flip them and alot of the world gen
 * uses them as detail". A player's hand works a trapdoor on the second use in a row: two clicks, or the use key held a
 * moment (held, it repeats). The first use only says so. Every trapdoor a hand can work (vanilla's wood and copper, any
 * mod's that extends {@link TrapDoorBlock}); iron is left to redstone as before. Only the player's own use is gated:
 * redstone, villagers, wind charges, locks and our own calls to the block go straight through. Sneaking with something in
 * hand still places it against the trapdoor. Both sides judge the same way, so the client doesn't flip it ahead.
 */
public final class TrapdoorUse {
	/** Ticks a second use may come after the one before and still work the trapdoor (a held key repeats every 4). */
	static final int AGAIN_TICKS = 10;

	/** The trapdoor a player last pressed, when, and whether that run of presses has worked it already. */
	private static final class Press {
		final BlockPos pos;
		int at;
		boolean worked;

		Press(BlockPos pos, int at) {
			this.pos = pos;
			this.at = at;
		}
	}

	/** One map for each side, so the client's and the integrated server's threads never share one. */
	private static final Map<Player, Press> CLIENT = new WeakHashMap<>(), SERVER = new WeakHashMap<>();

	private TrapdoorUse() {
	}

	public static void init() {
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			BlockPos pos = hit.getBlockPos();
			BlockState state = level.getBlockState(pos);
			if (player.isSpectator() || !(state.getBlock() instanceof TrapDoorBlock trapdoor)
				|| !((TrapdoorTypeAccessor) trapdoor).jaymospowers$type().canOpenByHand()
				|| player.isSecondaryUseActive() && (!player.getMainHandItem().isEmpty() || !player.getOffhandItem().isEmpty())) {
				return InteractionResult.PASS;
			}
			Map<Player, Press> presses = level.isClientSide() ? CLIENT : SERVER;
			int now = player.tickCount;
			Press last = presses.get(player);
			if (last != null && last.pos.equals(pos) && now - last.at >= 0 && now - last.at <= AGAIN_TICKS) {
				last.at = now;
				if (last.worked) {
					// still held after it moved: it stays as it is
					return InteractionResult.SUCCESS;
				}
				last.worked = true;
				return InteractionResult.PASS;
			}
			presses.put(player, new Press(pos.immutable(), now));
			if (!level.isClientSide()) {
				player.sendOverlayMessage(Component.translatable("message.jaymospowers.trapdoor_again"));
			}
			return InteractionResult.SUCCESS;
		});
	}
}
