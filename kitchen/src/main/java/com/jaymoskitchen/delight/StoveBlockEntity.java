package com.jaymoskitchen.delight;

import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.Clearable;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CampfireCookingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec2;
import org.jspecify.annotations.Nullable;

/**
 * (Farmer's Delight's stove block entity, MIT.) The six things grilling on a {@link StoveBlock}: each cooks for what its
 * campfire recipe says while the stove is lit, cools back down (twice as fast as it heated) when it isn't, and is dropped
 * if something covers the top.
 */
public class StoveBlockEntity extends BlockEntity implements Clearable {
	/** Grill spots. */
	public static final int SLOTS = 6;
	/** Cooling is this many times as fast as cooking. */
	private static final int COOLING_RATE = 2;
	/** Smoke puffs per rising wisp, and the chance each tick that a cooking item gives one. */
	private static final int SMOKE_PUFFS = 3;
	private static final float SMOKE_CHANCE = 0.2F;
	/** Where each spot is on the stove top, from its centre (front-left to back-right). */
	private static final float X_OFFSET = 0.3F;
	private static final float Y_OFFSET = 0.2F;
	private static final Vec2[] OFFSETS = {
		new Vec2(X_OFFSET, Y_OFFSET), new Vec2(0.0F, Y_OFFSET), new Vec2(-X_OFFSET, Y_OFFSET),
		new Vec2(X_OFFSET, -Y_OFFSET), new Vec2(0.0F, -Y_OFFSET), new Vec2(-X_OFFSET, -Y_OFFSET)
	};

	private final NonNullList<ItemStack> items = NonNullList.withSize(SLOTS, ItemStack.EMPTY);
	private final int[] cookingTimes = new int[SLOTS];
	private final int[] cookingProgress = new int[SLOTS];
	private final RecipeManager.CachedCheck<SingleRecipeInput, CampfireCookingRecipe> recipes = RecipeManager.createCheck(RecipeType.CAMPFIRE_COOKING);

	public StoveBlockEntity(BlockPos pos, BlockState state) {
		super(DelightHome.STOVE_ENTITY, pos, state);
	}

	public NonNullList<ItemStack> items() {
		return items;
	}

	/** Where grill spot {@code index} is on the stove top, as a shift from the centre (before the stove's facing). */
	public static Vec2 offset(int index) {
		return OFFSETS[index];
	}

	public boolean isEmpty() {
		return items.stream().allMatch(ItemStack::isEmpty);
	}

	public Optional<RecipeHolder<CampfireCookingRecipe>> recipeFor(ItemStack stack) {
		if (level instanceof ServerLevel server) {
			return recipes.getRecipeFor(new SingleRecipeInput(stack), server);
		}
		return Optional.empty();
	}

	/** Sets one of {@code stack} on the first free spot; false if there is none. */
	public boolean placeFood(@Nullable Entity actor, ItemStack stack, RecipeHolder<CampfireCookingRecipe> recipe) {
		int slot = -1;
		for (int i = 0; i < SLOTS && slot < 0; i++) {
			if (items.get(i).isEmpty()) {
				slot = i;
			}
		}
		if (slot < 0 || level == null) {
			return false;
		}
		cookingTimes[slot] = recipe.value().cookingTime();
		cookingProgress[slot] = 0;
		items.set(slot, stack.split(1));
		sync();
		level.gameEvent(GameEvent.BLOCK_CHANGE, worldPosition, GameEvent.Context.of(actor, getBlockState()));
		setChanged();
		return true;
	}

	/** Lit or put out: tell the clients. */
	public void litChanged() {
		sync();
		setChanged();
	}

	private void sync() {
		if (level != null) {
			level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
		}
	}

	public static void serverTick(ServerLevel level, BlockPos pos, BlockState state, StoveBlockEntity stove) {
		if (stove.isEmpty()) {
			return;
		}
		if (StoveBlock.topCovered(level, pos, state)) {
			stove.dropAll();
			stove.setChanged();
			return;
		}
		if (state.getValue(StoveBlock.LIT)) {
			stove.cook(level);
		} else {
			stove.cool();
		}
	}

	private void cook(ServerLevel level) {
		boolean changed = false;
		for (int i = 0; i < SLOTS; i++) {
			ItemStack ingredient = items.get(i);
			if (ingredient.isEmpty()) {
				continue;
			}
			changed = true;
			if (++cookingProgress[i] < cookingTimes[i]) {
				continue;
			}
			SingleRecipeInput input = new SingleRecipeInput(ingredient);
			ItemStack result = recipes.getRecipeFor(input, level).map(recipe -> recipe.value().assemble(input)).orElse(ingredient);
			if (!result.isItemEnabled(level.enabledFeatures())) {
				continue;
			}
			ItemEntity out = new ItemEntity(level, worldPosition.getX() + 0.5, worldPosition.getY() + 1.0, worldPosition.getZ() + 0.5, result.copy());
			out.setDeltaMovement(level.getRandom().nextGaussian() * 0.01, 0.1, level.getRandom().nextGaussian() * 0.01);
			level.addFreshEntity(out);
			items.set(i, ItemStack.EMPTY);
			sync();
			level.gameEvent(GameEvent.BLOCK_CHANGE, worldPosition, GameEvent.Context.of(getBlockState()));
		}
		if (changed) {
			setChanged();
		}
	}

	private void cool() {
		boolean changed = false;
		for (int i = 0; i < SLOTS; i++) {
			if (cookingProgress[i] > 0) {
				changed = true;
				cookingProgress[i] = Mth.clamp(cookingProgress[i] - COOLING_RATE, 0, cookingTimes[i]);
			}
		}
		if (changed) {
			setChanged();
		}
	}

	/** Smoke over what's cooking (client). */
	public void particleTick() {
		if (level == null || isEmpty()) {
			return;
		}
		Direction facing = getBlockState().getValue(HorizontalDirectionalBlock.FACING);
		for (int i = 0; i < SLOTS; i++) {
			if (items.get(i).isEmpty() || level.getRandom().nextFloat() >= SMOKE_CHANCE) {
				continue;
			}
			Vec2 offset = OFFSETS[i];
			if (facing.get2DDataValue() % 2 != 0) {
				offset = new Vec2(offset.y, offset.x);
			}
			double x = worldPosition.getX() + 0.5 - facing.getStepX() * offset.x + facing.getClockWise().getStepX() * offset.x;
			double z = worldPosition.getZ() + 0.5 - facing.getStepZ() * offset.y + facing.getClockWise().getStepZ() * offset.y;
			for (int k = 0; k < SMOKE_PUFFS; k++) {
				level.addParticle(ParticleTypes.SMOKE, x, worldPosition.getY() + 1.0, z, 0.0, 5.0E-4, 0.0);
			}
		}
	}

	private void dropAll() {
		if (level == null) {
			return;
		}
		for (ItemStack stack : items) {
			Containers.dropItemStack(level, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), stack);
		}
		items.clear();
		sync();
		level.gameEvent(GameEvent.BLOCK_CHANGE, worldPosition, GameEvent.Context.of(getBlockState()));
	}

	@Override
	public void preRemoveSideEffects(BlockPos pos, BlockState state) {
		if (level != null) {
			for (ItemStack stack : items) {
				Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), stack);
			}
		}
		super.preRemoveSideEffects(pos, state);
	}

	@Override
	public void clearContent() {
		items.clear();
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		items.clear();
		ContainerHelper.loadAllItems(input, items);
		input.getIntArray("CookingTimes").ifPresent(array -> System.arraycopy(array, 0, cookingTimes, 0, Math.min(SLOTS, array.length)));
		input.getIntArray("CookingTotalTimes").ifPresent(array -> System.arraycopy(array, 0, cookingProgress, 0, Math.min(SLOTS, array.length)));
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		ContainerHelper.saveAllItems(output, items, true);
		output.putIntArray("CookingTimes", cookingTimes);
		output.putIntArray("CookingTotalTimes", cookingProgress);
	}

	@Override
	public ClientboundBlockEntityDataPacket getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this);
	}

	@Override
	public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
		TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, registries);
		ContainerHelper.saveAllItems(output, items, true);
		return output.buildResult();
	}
}
