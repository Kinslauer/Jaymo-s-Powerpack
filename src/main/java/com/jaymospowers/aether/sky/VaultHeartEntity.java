package com.jaymospowers.aether.sky;

import com.jaymospowers.core.fx.MagicFx;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * (0.20.96) Andy (seven-mods plan, patch 10): the watched vault. Its chest and its crystals stay put while anyone looks at
 * them; the moment nobody near does, now and then, one is somewhere else in the room. An open chest stays where it is.
 */
public class VaultHeartEntity extends BlockEntity {
	/** The room over the heart: this far out each way, this high. */
	static final int HALF = 3, HIGH = 4;
	static final int EVERY = 10;
	static final double WATCH_RANGE = 16.0, LOOK_COS = 0.55;
	static final float SHIFT_CHANCE = 0.3F;

	public VaultHeartEntity(BlockPos pos, BlockState state) {
		super(SkyBlocks.VAULT_HEART_ENTITY, pos, state);
	}

	public static void serverTick(Level level, BlockPos pos, BlockState state, VaultHeartEntity heart) {
		if (!(level instanceof ServerLevel server) || (server.getGameTime() + pos.asLong()) % EVERY != 0) {
			return;
		}
		List<ServerPlayer> near = server.getPlayers(p -> !p.isSpectator() && p.distanceToSqr(Vec3.atCenterOf(pos)) < WATCH_RANGE * WATCH_RANGE);
		if (near.isEmpty()) {
			return;
		}
		RandomSource random = server.getRandom();
		List<BlockPos> things = new ArrayList<>();
		List<BlockPos> free = new ArrayList<>();
		for (BlockPos p : BlockPos.betweenClosed(pos.offset(-HALF, 1, -HALF), pos.offset(HALF, HIGH, HALF))) {
			BlockState st = server.getBlockState(p);
			if (st.getBlock() instanceof ChestBlock || st.getBlock() instanceof AmethystClusterBlock) {
				things.add(p.immutable());
			} else if (st.isAir() && server.getBlockState(p.below()).isFaceSturdy(server, p.below(), Direction.UP)) {
				free.add(p.immutable());
			}
		}
		if (things.isEmpty() || free.isEmpty()) {
			return;
		}
		BlockPos thing = things.get(random.nextInt(things.size()));
		if (watched(near, thing) || random.nextFloat() >= SHIFT_CHANCE) {
			return;
		}
		BlockPos to = free.get(random.nextInt(free.size()));
		if (watched(near, to)) {
			return;
		}
		BlockState st = server.getBlockState(thing);
		if (st.getBlock() instanceof ChestBlock) {
			if (ChestBlockEntity.getOpenCount(server, thing) > 0 || !(server.getBlockEntity(thing) instanceof Container box)) {
				return;
			}
			List<ItemStack> items = new ArrayList<>();
			for (int i = 0; i < box.getContainerSize(); i++) {
				items.add(box.getItem(i).copy());
			}
			box.clearContent();
			server.setBlock(thing, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
			server.setBlock(to, st.setValue(ChestBlock.TYPE, net.minecraft.world.level.block.state.properties.ChestType.SINGLE), Block.UPDATE_ALL);
			if (server.getBlockEntity(to) instanceof Container moved) {
				for (int i = 0; i < items.size() && i < moved.getContainerSize(); i++) {
					moved.setItem(i, items.get(i));
				}
			}
		} else {
			server.setBlock(thing, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
			server.setBlock(to, st.setValue(AmethystClusterBlock.FACING, Direction.UP), Block.UPDATE_ALL);
		}
		server.playSound(null, to, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.4F, 0.6F);
		server.sendParticles(MagicFx.VOID, to.getX() + 0.5, to.getY() + 0.5, to.getZ() + 0.5, 6, 0.2, 0.2, 0.2, 0.01);
	}

	/** Whether any of {@code players} looks at the block at {@code p}. */
	static boolean watched(List<ServerPlayer> players, BlockPos p) {
		Vec3 centre = Vec3.atCenterOf(p);
		for (ServerPlayer pl : players) {
			Vec3 to = centre.subtract(pl.getEyePosition());
			if (to.lengthSqr() < 2.25 || pl.getViewVector(1.0F).dot(to.normalize()) > LOOK_COS) {
				return true;
			}
		}
		return false;
	}
}
