package com.jaymoskitchen.tanning;

import com.jaymoskitchen.ModItems;
import com.jaymoskitchen.herb.PmehFarming;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Prediction;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * What hangs on a drying rack ({@link Hides#DRYING}): up to {@link Hides#RACK_HOLDS} of one thing, drying together.
 * Hanging more on restarts the clock; rain on the rack holds it back. Dried Pmeh left hanging goes brittle and loses grade.
 */
public class DryingRackEntity extends BlockEntity {
	private final NonNullList<ItemStack> held = NonNullList.withSize(1, ItemStack.EMPTY);
	/** Ticks of drying left (rain doesn't count down). */
	private int left;
	/** Ticks a dried Pmeh has hung on past drying (it loses a grade each {@link PmehFarming#OVERDRY_TICKS}). */
	private int overdry;

	public DryingRackEntity(BlockPos pos, BlockState state) {
		super(Hides.DRYING_RACK_ENTITY, pos, state);
	}

	public ItemStack held() {
		return held.get(0);
	}

	public static void serverTick(Level level, BlockPos pos, BlockState state, DryingRackEntity rack) {
		ItemStack stack = rack.held();
		if (stack.is(ModItems.PMEH)) {
			rack.overdry(level);
			return;
		}
		if (stack.isEmpty() || Hides.dried(stack) == null || level.isRainingAt(pos.above())) {
			return;
		}
		rack.left--;
		if (rack.left % 20 == 0) {
			rack.setChanged();
		}
		if (rack.left <= 0) {
			rack.finish();
		} else if (level instanceof ServerLevel server && level.getRandom().nextInt(200) == 0) {
			server.sendParticles(ParticleTypes.WHITE_SMOKE, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 2, 0.2, 0.2, 0.2, 0.0);
		}
	}

	/** A dried Pmeh left hanging goes brittle: a grade lost each {@link PmehFarming#OVERDRY_TICKS}, down to scraggly. */
	private void overdry(Level level) {
		if (level.isRainingAt(worldPosition.above()) || ++overdry < PmehFarming.OVERDRY_TICKS) {
			if (overdry % 20 == 0) {
				setChanged();
			}
			return;
		}
		overdry = 0;
		int grade = PmehFarming.grade(held());
		if (grade > PmehFarming.SCRAGGLY) {
			PmehFarming.stamp(held(), grade - 1);
			markUpdated();
		}
	}

	/** Done drying: what hangs there turns into what it dries into. */
	private boolean finish() {
		ItemStack into = Hides.dried(held());
		if (into == null) {
			return false;
		}
		held.set(0, into.copyWithCount(held().getCount()));
		left = 0;
		overdry = 0;
		if (level != null) {
			level.playSound(null, worldPosition, SoundEvents.ARMOR_EQUIP_LEATHER.value(), SoundSource.BLOCKS, 0.6F, 1.2F);
		}
		markUpdated();
		return true;
	}

	/** Hangs up as many of the held item as fit, or says why not. */
	public InteractionResult hang(Player player, ItemStack stack) {
		if (Hides.dried(stack) == null) {
			return InteractionResult.PASS;
		}
		ItemStack there = held();
		if (!there.isEmpty() && (!ItemStack.isSameItemSameComponents(there, stack) || there.getCount() >= Hides.RACK_HOLDS)) {
			if (level != null && !level.isClientSide()) {
				player.sendOverlayMessage(Component.translatable("message.jaymoskitchen.drying_rack.full"));
			}
			return InteractionResult.SUCCESS;
		}
		if (level == null || level.isClientSide()) {
			return InteractionResult.SUCCESS;
		}
		int room = Hides.RACK_HOLDS - there.getCount();
		int moving = Math.min(room, stack.getCount());
		ItemStack hung = there.isEmpty() ? stack.copyWithCount(moving) : there.copyWithCount(there.getCount() + moving);
		if (!player.hasInfiniteMaterials()) {
			stack.shrink(moving);
		}
		held.set(0, hung);
		left = Hides.DRY_TICKS;
		level.playSound(null, worldPosition, SoundEvents.ARMOR_EQUIP_LEATHER.value(), SoundSource.BLOCKS, 0.7F, 0.9F);
		player.sendOverlayMessage(Component.translatable("message.jaymoskitchen.drying_rack.hung", hung.getHoverName(), (Hides.DRY_TICKS + 1199) / 1200));
		markUpdated();
		return InteractionResult.SUCCESS;
	}

	/** Takes everything down, dry or not (wet things say how long they've got). */
	public InteractionResult takeDown(Player player) {
		if (held().isEmpty()) {
			return InteractionResult.PASS;
		}
		if (level == null || level.isClientSide()) {
			return InteractionResult.SUCCESS;
		}
		if (Hides.dried(held()) != null && player.isShiftKeyDown()) {
			player.sendOverlayMessage(Component.translatable("message.jaymoskitchen.drying_rack.wait", (left + 1199) / 1200));
			return InteractionResult.SUCCESS;
		}
		player.getInventory().placeItemBackInInventory(held().copy(), Prediction.SERVER_ONLY);
		held.set(0, ItemStack.EMPTY);
		left = 0;
		overdry = 0;
		level.playSound(null, worldPosition, SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.3F, 1.0F);
		markUpdated();
		return InteractionResult.SUCCESS;
	}

	@Override
	public void preRemoveSideEffects(BlockPos pos, BlockState state) {
		if (level != null) {
			Containers.dropContents(level, pos, held);
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
		held.clear();
		ContainerHelper.loadAllItems(input, held);
		left = (int) input.getLongOr("left", 0L);
		overdry = (int) input.getLongOr("overdry", 0L);
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		ContainerHelper.saveAllItems(output, held, true);
		output.putLong("left", left);
		output.putLong("overdry", overdry);
	}

	@Override
	public ClientboundBlockEntityDataPacket getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this);
	}

	@Override
	public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
		TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, registries);
		ContainerHelper.saveAllItems(output, held, true);
		return output.buildResult();
	}
}
