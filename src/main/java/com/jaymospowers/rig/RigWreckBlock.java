package com.jaymospowers.rig;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A wrecked Rig slumped where it fell. A Dead Rig (the ruined future) gives Rig Salvage; a Fallen Rig (the Nether, the
 * End) is a weaker, older wreck: one of the explorers who went looking for a way off Earth and died trying. It gives
 * Fallen Rig Scrap, and its last log (lang {@code rig.jaymospowers.fallen.lore.<n>}, {@link #FALLEN_LORE} of them).
 * Right-click it (or break it) to strip it: its loot table drops what's left.
 */
public class RigWreckBlock extends Block {
	/** How many fallen explorers' logs there are to find. */
	static final int FALLEN_LORE = 6, DEAD_LORE = 3;
	private static final VoxelShape SHAPE = Block.box(2.0, 0.0, 2.0, 14.0, 11.0, 14.0);

	private final boolean fallen;

	public RigWreckBlock(boolean fallen, Block.Properties properties) {
		super(properties);
		this.fallen = fallen;
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (player instanceof ServerPlayer server && level instanceof ServerLevel serverLevel) {
			lore(server, pos);
			serverLevel.playSound(null, pos, SoundEvents.CHAIN_BREAK, SoundSource.BLOCKS, 1.0F, 0.7F);
			serverLevel.destroyBlock(pos, true, player);
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
		if (player instanceof ServerPlayer server) {
			lore(server, pos);
		}
		return super.playerWillDestroy(level, pos, state, player);
	}

	/** The wreck's last words, picked by where it lies (the same wreck always says the same). */
	private void lore(ServerPlayer player, BlockPos pos) {
		int n = Math.floorMod((int)(pos.asLong() ^ pos.asLong() >>> 32), this.fallen ? FALLEN_LORE : DEAD_LORE);
		String key = (this.fallen ? "rig.jaymospowers.fallen.lore." : "rig.jaymospowers.dead.lore.") + n;
		player.sendSystemMessage(Component.translatable(key).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
	}
}
