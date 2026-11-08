package com.jaymoskitchen.cooking.spit;

import com.jaymoskitchen.JaymosKitchen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * One side of the spit: what's roasting and how done it is (0 raw, {@link #COOKED} cooked, {@link #BURNT} burnt).
 * The server ticks the doneness while the block sits over a lit heat source; the client draws it turning and
 * changing colour (see {@link com.jaymoskitchen.client.spit.SpitBlockEntityRenderer}).
 */
public class SpitBlockEntity extends BlockEntity {
	/** Doneness at which the roast is cooked through. */
	public static final float COOKED = 1.0F;
	/** Doneness at which it's burnt to a crisp. */
	public static final float BURNT = 2.0F;
	/** Ticks from raw to cooked over a lit heat source. */
	public static final int COOK_TICKS = 10 * 20;
	/** Ticks from cooked to burnt. */
	public static final int BURNT_TICKS = 8 * 20;
	/** Doneness a single torn chunk takes off. */
	public static final float CHUNK = 0.25F;
	/** Chunks a whole animal carcass yields when torn off the spit. */
	public static final int CHUNKS_PER_ANIMAL = 4;
	/** Chunks a single piece of food yields. */
	public static final int CHUNKS_PER_PIECE = 1;
	/** What can go on the spit: raw meats and fish, and carcasses in your pocket. */
	public static final TagKey<Item> ROASTABLE = TagKey.create(Registries.ITEM, JaymosKitchen.id("spit_foods"));

	final NonNullList<ItemStack> items = NonNullList.withSize(1, ItemStack.EMPTY);
	float doneness;
	/** How many cooked chunks are left to tear off this roast. */
	int chunks;
	/** Ticks this roast has been cooking: for periodic client colour updates. */
	int cookTicks;

	public SpitBlockEntity(BlockPos pos, BlockState state) {
		super(com.jaymoskitchen.cooking.spit.SpitBlock.ENTITY, pos, state);
	}

	void markUpdated() {
		setChanged();
		if (level != null) {
			level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
		}
	}

	public ItemStack roasting() {
		return items.get(0);
	}

	public float doneness() {
		return doneness;
	}

	public static void serverTick(Level level, BlockPos pos, BlockState state, SpitBlockEntity be) {
		if (!com.jaymoskitchen.cooking.spit.SpitBlock.heat(level.getBlockState(pos.below()))) {
			return;
		}
		ItemStack stack = be.items.get(0);
		if (stack.isEmpty()) {
			return;
		}
		float rate = be.doneness < COOKED ? 1.0F / COOK_TICKS : 1.0F / BURNT_TICKS;
		float before = be.doneness;
		be.doneness = Mth.clamp(be.doneness + rate, 0.0F, BURNT);
		if ((before >= COOKED) != (be.doneness >= COOKED) || (before >= BURNT) != (be.doneness >= BURNT)
			|| ++be.cookTicks % 20 == 0) {
			be.markUpdated();
		}
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		items.clear();
		net.minecraft.world.ContainerHelper.loadAllItems(input, items);
		doneness = input.getFloatOr("Doneness", 0.0F);
		chunks = input.getIntOr("Chunks", 0);
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		writeShared(output);
		output.putFloat("Doneness", doneness);
		output.putInt("Chunks", chunks);
	}

	private void writeShared(ValueOutput output) {
		net.minecraft.world.ContainerHelper.saveAllItems(output, items, true);
	}

	@Override
	public ClientboundBlockEntityDataPacket getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this);
	}

	@Override
	public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
		TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, registries);
		writeShared(output);
		output.putFloat("Doneness", doneness);
		output.putInt("Chunks", chunks);
		return output.buildResult();
	}
}
