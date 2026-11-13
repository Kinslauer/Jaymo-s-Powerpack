package com.jaymoskitchen.butchery;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Prediction;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * A beetle box's colony and the bones it's working on. Flesh beetles strip a frame clean in a day ({@link #CLEAN_TICKS})
 * and leave it white. The scraps on the bones are what the colony lives on: a box that has had nothing to eat for
 * {@link #STARVE_TICKS} has died off, and won't clean anything until rotten flesh starts a new colony. So a box that's
 * kept busy keeps working, and nobody is punished for letting one go: it just needs restocking.
 */
public class BeetleBoxEntity extends BlockEntity {
	/** How long the beetles take over a frame: a day. */
	public static final long CLEAN_TICKS = 24000L;
	/** How long a colony lasts with nothing to eat: three days. */
	public static final long STARVE_TICKS = 72000L;

	private ItemStack bones = ItemStack.EMPTY;
	private long readyAt, lastFed;

	public BeetleBoxEntity(BlockPos pos, BlockState state) {
		super(Skeletons.BEETLE_BOX_ENTITY, pos, state);
	}

	static void serverTick(Level level, BlockPos pos, BlockState state, BeetleBoxEntity box) {
		long now = level.getGameTime();
		if (box.lastFed == 0L) {
			// a new box comes with a colony
			box.lastFed = now;
			box.setChanged();
		}
		if (now % 20 == 0 && !box.bones.isEmpty() && box.bones.is(Skeletons.FRAME_BONES) && now >= box.readyAt) {
			box.bones = Skeletons.cleaned(box.bones, Skeletons.Clean.BEETLES);
			box.lastFed = now;
			box.markUpdated();
		}
	}

	/** Busy on a frame: the bones in it aren't clean yet. */
	public boolean working() {
		return !bones.isEmpty() && bones.is(Skeletons.FRAME_BONES);
	}

	/** The colony is alive: it has bones to eat, or ate recently enough. */
	boolean alive() {
		return working() || level == null || level.getGameTime() - lastFed < STARVE_TICKS;
	}

	InteractionResult use(Player player, ItemStack held) {
		if (level == null) {
			return InteractionResult.PASS;
		}
		if (held.is(Items.ROTTEN_FLESH) && !alive()) {
			if (!level.isClientSide()) {
				lastFed = level.getGameTime();
				if (!player.hasInfiniteMaterials()) {
					held.shrink(1);
				}
				level.playSound(null, worldPosition, SoundEvents.SILVERFISH_AMBIENT, SoundSource.BLOCKS, 0.4F, 1.6F);
				player.sendOverlayMessage(Component.translatable("message.jaymoskitchen.bones.beetles_restocked"));
				markUpdated();
			}
			return InteractionResult.SUCCESS;
		}
		if (!held.is(Skeletons.FRAME_BONES) || Skeletons.kind(held) == null) {
			return InteractionResult.PASS;
		}
		if (level.isClientSide()) {
			return InteractionResult.SUCCESS;
		}
		if (!bones.isEmpty()) {
			player.sendOverlayMessage(Component.translatable("message.jaymoskitchen.bones.box_full"));
			return InteractionResult.SUCCESS;
		}
		if (!alive()) {
			player.sendOverlayMessage(Component.translatable("message.jaymoskitchen.bones.beetles_gone"));
			return InteractionResult.SUCCESS;
		}
		bones = held.copyWithCount(1);
		readyAt = level.getGameTime() + CLEAN_TICKS;
		if (!player.hasInfiniteMaterials()) {
			held.shrink(1);
		}
		level.playSound(null, worldPosition, SoundEvents.BONE_BLOCK_PLACE, SoundSource.BLOCKS, 0.8F, 0.9F);
		player.sendOverlayMessage(Component.translatable("message.jaymoskitchen.bones.beetles_at_work", CLEAN_TICKS / 24000L));
		markUpdated();
		return InteractionResult.SUCCESS;
	}

	InteractionResult takeOut(Player player) {
		if (bones.isEmpty() || level == null) {
			return InteractionResult.PASS;
		}
		if (!level.isClientSide()) {
			if (working()) {
				// not done: say how long, unless they sneak to take them out dirty
				if (!player.isShiftKeyDown()) {
					long left = Math.max(0L, readyAt - level.getGameTime());
					player.sendOverlayMessage(Component.translatable("message.jaymoskitchen.bones.beetles_left", (left + 1199L) / 1200L));
					return InteractionResult.SUCCESS;
				}
				lastFed = level.getGameTime();
			}
			player.getInventory().placeItemBackInInventory(bones.copy(), Prediction.SERVER_ONLY);
			bones = ItemStack.EMPTY;
			level.playSound(null, worldPosition, SoundEvents.BONE_BLOCK_BREAK, SoundSource.BLOCKS, 0.8F, 1.0F);
			markUpdated();
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	public void preRemoveSideEffects(BlockPos pos, BlockState state) {
		if (level instanceof ServerLevel && !bones.isEmpty()) {
			Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, bones);
		}
	}

	private void markUpdated() {
		setChanged();
		if (level != null) {
			level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
		}
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		bones = input.read("bones", ItemStack.CODEC).orElse(ItemStack.EMPTY);
		readyAt = input.getLongOr("ready_at", 0L);
		lastFed = input.getLongOr("last_fed", 0L);
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		if (!bones.isEmpty()) {
			output.store("bones", ItemStack.CODEC, bones);
		}
		output.putLong("ready_at", readyAt);
		output.putLong("last_fed", lastFed);
	}

	@Override
	public ClientboundBlockEntityDataPacket getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this);
	}

	@Override
	public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
		TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, registries);
		saveAdditional(output);
		return output.buildResult();
	}
}
