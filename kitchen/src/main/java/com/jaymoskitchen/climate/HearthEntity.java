package com.jaymoskitchen.climate;

import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * A hearth's or icebox's fuel and the room it fills ({@link HearthBlock}). While it burns, every {@link #SCAN_EVERY}
 * ticks it walks the air around it (through open doors, gates and trapdoors, not through walls, closed doors or panes)
 * and hands what it found to {@link Warmth}. Air that sees the sky is outside: it isn't warmed and the walk doesn't go on
 * through it, so a room with a hole in the roof is only warmed under the roof. The walk stops at {@link #MAX_CELLS}
 * blocks and {@link #REACH} blocks away, so a great hall needs more than one.
 */
public class HearthEntity extends BlockEntity {
	/** Ticks between walks of the room. */
	static final int SCAN_EVERY = 100;
	/** The most air one hearth warms, and how far it reaches. */
	static final int MAX_CELLS = 640, REACH = 12;
	/** The most fuel it holds, in ticks (an in-game day). */
	static final int MAX_FUEL = 24000;

	private int fuel;

	public HearthEntity(BlockPos pos, BlockState state) {
		super(Warmth.HEARTH_ENTITY, pos, state);
	}

	int fuel() {
		return fuel;
	}

	/** The air this one fills right now. */
	int cells() {
		return level instanceof ServerLevel server ? Warmth.cells(server, worldPosition) : 0;
	}

	/** What the screen is told: see {@link com.jaymoskitchen.net.Payloads.OpenHearth}. */
	void open(net.minecraft.server.level.ServerPlayer player) {
		boolean canFeed = false;
		for (int i = 0; i < player.getInventory().getContainerSize() && !canFeed; i++) {
			canFeed = Warmth.burnTicks(player.getInventory().getItem(i), cold()) > 0;
		}
		net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player, new com.jaymoskitchen.net.Payloads.OpenHearth(worldPosition, fuel,
			MAX_FUEL, cold(), getBlockState().getValue(HearthBlock.LIT), cells(), canFeed));
	}

	/** Takes one thing the player carries that it burns, and feeds it; false if there is none or it is full. */
	boolean feedFrom(net.minecraft.server.level.ServerPlayer player) {
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			net.minecraft.world.item.ItemStack stack = player.getInventory().getItem(i);
			int burn = Warmth.burnTicks(stack, cold());
			if (burn > 0 && feed(burn)) {
				if (!player.getAbilities().instabuild) {
					stack.shrink(1);
				}
				return true;
			}
		}
		return false;
	}

	boolean cold() {
		return getBlockState().getBlock() instanceof HearthBlock hearth && hearth.cold;
	}

	/** Adds {@code ticks} of fuel; false if it's already full. */
	boolean feed(int ticks) {
		if (fuel >= MAX_FUEL) {
			return false;
		}
		fuel = Math.min(MAX_FUEL, fuel + ticks);
		setChanged();
		if (level instanceof ServerLevel server) {
			server.playSound(null, worldPosition, cold() ? SoundEvents.GLASS_PLACE : SoundEvents.FIRECHARGE_USE, SoundSource.BLOCKS, 0.6F,
				cold() ? 1.3F : 1.0F);
			if (!getBlockState().getValue(HearthBlock.LIT)) {
				server.setBlock(worldPosition, getBlockState().setValue(HearthBlock.LIT, true), Block.UPDATE_ALL);
			}
			scan(server);
		}
		return true;
	}

	static void serverTick(Level level, BlockPos pos, BlockState state, HearthEntity hearth) {
		if (!(level instanceof ServerLevel server)) {
			return;
		}
		if (hearth.fuel > 0) {
			hearth.fuel--;
			if (hearth.fuel % 200 == 0) {
				hearth.setChanged();
			}
			if (server.getGameTime() % SCAN_EVERY == Math.floorMod(pos.asLong(), SCAN_EVERY) || !Warmth.has(server, pos)) {
				hearth.scan(server);
			}
			if (!state.getValue(HearthBlock.LIT)) {
				server.setBlock(pos, state.setValue(HearthBlock.LIT, true), Block.UPDATE_ALL);
			}
		} else if (state.getValue(HearthBlock.LIT)) {
			server.setBlock(pos, state.setValue(HearthBlock.LIT, false), Block.UPDATE_ALL);
			Warmth.clear(server, pos);
		}
	}

	/** Walks the room and hands it to {@link Warmth}. */
	void scan(ServerLevel level) {
		LongOpenHashSet room = new LongOpenHashSet();
		LongOpenHashSet seen = new LongOpenHashSet();
		LongArrayFIFOQueue queue = new LongArrayFIFOQueue();
		seen.add(worldPosition.asLong());
		for (Direction d : Direction.values()) {
			long next = worldPosition.relative(d).asLong();
			seen.add(next);
			queue.enqueue(next);
		}
		BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
		while (!queue.isEmpty() && room.size() < MAX_CELLS) {
			long packed = queue.dequeueLong();
			at.set(packed);
			if (at.distManhattan(worldPosition) > REACH * 2 || Math.abs(at.getX() - worldPosition.getX()) > REACH
				|| Math.abs(at.getY() - worldPosition.getY()) > REACH || Math.abs(at.getZ() - worldPosition.getZ()) > REACH) {
				continue;
			}
			if (!level.isLoaded(at) || !open(level, at, level.getBlockState(at)) || level.canSeeSky(at)) {
				continue;
			}
			room.add(packed);
			for (Direction d : Direction.values()) {
				long next = BlockPos.offset(packed, d);
				if (seen.add(next)) {
					queue.enqueue(next);
				}
			}
		}
		Warmth.set(level, worldPosition, cold(), room);
	}

	/** Air moves through it: nothing to bump into, or an open door, gate or trapdoor. */
	static boolean open(Level level, BlockPos pos, BlockState state) {
		if (state.getBlock() instanceof DoorBlock || state.getBlock() instanceof FenceGateBlock || state.getBlock() instanceof TrapDoorBlock) {
			return state.getValueOrElse(BlockStateProperties.OPEN, false);
		}
		return state.getCollisionShape(level, pos).isEmpty();
	}

	@Override
	public void setRemoved() {
		super.setRemoved();
		if (level instanceof ServerLevel server) {
			Warmth.clear(server, worldPosition);
		}
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		fuel = input.getIntOr("Fuel", 0);
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		output.putInt("Fuel", fuel);
	}
}
