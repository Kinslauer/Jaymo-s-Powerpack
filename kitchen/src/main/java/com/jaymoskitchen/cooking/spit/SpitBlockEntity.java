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

	/**
	 * Someone who isn't a player (Kingdoms' camp cooks) puts one of {@code raw} on the spit: true if it went on (the spit
	 * was bare and it's roastable). {@code raw} is shrunk by one.
	 */
	public boolean skewer(ItemStack raw) {
		if (raw.isEmpty() || !raw.is(ROASTABLE) || !items.get(0).isEmpty()) {
			return false;
		}
		items.set(0, raw.copyWithCount(1));
		chunks = SpitBlock.chunksFor(raw);
		raw.shrink(1);
		doneness = 0.0F;
		markUpdated();
		return true;
	}

	/**
	 * Someone who isn't a player tears a cooked chunk off, as a player's empty hand does: one cooked piece, the last tear
	 * clearing the spit. A burnt roast is thrown out (nothing comes off); a raw one gives nothing.
	 */
	public ItemStack tear(ServerLevel level) {
		ItemStack stack = items.get(0);
		if (stack.isEmpty() || doneness < COOKED) {
			return ItemStack.EMPTY;
		}
		if (doneness >= BURNT || chunks <= 0) {
			items.set(0, ItemStack.EMPTY);
			doneness = 0.0F;
			markUpdated();
			return ItemStack.EMPTY;
		}
		ItemStack chunk = SpitBlock.cookedProduct(stack, level).get(0).copyWithCount(1);
		chunks -= 1;
		doneness = Mth.clamp(doneness - CHUNK, 0.0F, BURNT);
		if (chunks <= 0) {
			items.set(0, ItemStack.EMPTY);
		}
		markUpdated();
		return chunk;
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
