package com.jaymospowers.tinkering.smeltery;

import com.jaymospowers.tinkering.Tinkering;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * A casting table or basin: a faucet fills it, the metal cools, and the result sits on top until taken. A table needs a
 * cast (or an item to make a gold cast from); a basin makes blocks.
 */
public class CastingEntity extends BlockEntity {
	private ItemStack cast = ItemStack.EMPTY;
	private ItemStack output = ItemStack.EMPTY;
	private @Nullable Molten molten;
	private int amount;
	private int needed;
	private int cooling;

	public CastingEntity(BlockPos pos, BlockState state) {
		super(Tinkering.CASTING_ENTITY, pos, state);
	}

	public boolean basin() {
		return getBlockState().getBlock() instanceof CastingBlock block && block.basin;
	}

	public ItemStack cast() {
		return cast;
	}

	public ItemStack output() {
		return output;
	}

	public @Nullable Molten molten() {
		return molten;
	}

	public int amount() {
		return amount;
	}

	public int needed() {
		return needed;
	}

	/** Busy: filling, cooling or holding a result. */
	public boolean busy() {
		return amount > 0 || !output.isEmpty();
	}

	/** How much of this liquid it would still take (0: none). */
	public int wants(Molten liquid) {
		if (!output.isEmpty() || cooling > 0 || molten != null && molten != liquid) {
			return 0;
		}
		int target = target(liquid);
		return Math.max(0, target - amount);
	}

	private int target(Molten liquid) {
		if (basin()) {
			Melting.BasinResult result = Melting.basinResult(liquid);
			return result == null ? 0 : result.amount();
		}
		return cast.isEmpty() ? 0 : Melting.tableAmount(cast, liquid);
	}

	/** A faucet pours some in. */
	public void fill(Molten liquid, int poured) {
		if (poured <= 0) {
			return;
		}
		if (molten == null) {
			molten = liquid;
			needed = target(liquid);
		}
		amount += poured;
		if (amount >= needed) {
			cooling = Melting.coolTicks(needed);
		}
		changed();
	}

	public static void serverTick(Level level, BlockPos pos, BlockState state, CastingEntity casting) {
		if (casting.cooling > 0 && --casting.cooling == 0) {
			casting.finish(level);
		}
	}

	private void finish(Level level) {
		ItemStack result = null;
		if (molten != null) {
			if (basin()) {
				Melting.BasinResult basinResult = Melting.basinResult(molten);
				result = basinResult == null ? null : basinResult.result();
			} else {
				result = Melting.tableResult(cast, molten);
				if (result != null && Melting.usesUpCast(cast)) {
					cast = ItemStack.EMPTY;
				}
			}
		}
		output = result == null ? ItemStack.EMPTY : result;
		molten = null;
		amount = 0;
		needed = 0;
		level.playSound(null, worldPosition, SoundEvents.LAVA_EXTINGUISH, SoundSource.BLOCKS, 0.4F, 1.6F);
		changed();
	}

	/** Takes what's on it: the result first, then the cast once it's free. */
	public ItemStack take() {
		if (!output.isEmpty()) {
			ItemStack out = output;
			output = ItemStack.EMPTY;
			changed();
			return out;
		}
		if (!cast.isEmpty() && amount == 0) {
			ItemStack out = cast;
			cast = ItemStack.EMPTY;
			changed();
			return out;
		}
		return ItemStack.EMPTY;
	}

	/** Sets a cast (or an item to make one from) on an empty table. */
	public boolean putCast(ItemStack stack) {
		if (basin() || !cast.isEmpty() || busy() || !(stack.getItem() instanceof CastItem) && CastItem.Shape.of(stack) == null && !Melting.gilds(stack)) {
			return false;
		}
		cast = stack.split(1);
		changed();
		return true;
	}

	private void changed() {
		setChanged();
		if (level != null) {
			level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
		}
	}

	@Override
	public void preRemoveSideEffects(BlockPos pos, BlockState state) {
		if (level != null) {
			Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), cast);
			Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), output);
		}
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		cast = input.read("cast", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);
		output = input.read("output", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);
		molten = Molten.byId(input.getStringOr("molten", ""));
		amount = input.getIntOr("amount", 0);
		needed = input.getIntOr("needed", 0);
		cooling = input.getIntOr("cooling", 0);
		if (level != null && level.isClientSide()) {
			level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_IMMEDIATE);
		}
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		output.store("cast", ItemStack.OPTIONAL_CODEC, cast);
		output.store("output", ItemStack.OPTIONAL_CODEC, this.output);
		if (molten != null) {
			output.putString("molten", molten.id);
		}
		output.putInt("amount", amount);
		output.putInt("needed", needed);
		output.putInt("cooling", cooling);
	}

	@Override
	public ClientboundBlockEntityDataPacket getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this);
	}

	@Override
	public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
		TagValueOutput out = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, registries);
		saveAdditional(out);
		return out.buildResult();
	}
}
