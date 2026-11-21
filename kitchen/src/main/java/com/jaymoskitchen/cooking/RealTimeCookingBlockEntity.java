package com.jaymoskitchen.cooking;

import com.jaymoskitchen.JaymosKitchen;
import com.jaymoskitchen.ModItems;
import com.jaymoskitchen.kitchen.KitchenBlocks;
import com.jaymoskitchen.kitchen.OvenHob;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.Clearable;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.Optional;

/**
 * A block entity for real-time cooking: items placed in it cook over ticks using heat/fuel,
 * similar to a furnace but for the kitchen's dishes. Supports multiple cooking slots,
 * each with its own progress. Heat is consumed from stored heat (like the oven) or fuel items.
 */
public class RealTimeCookingBlockEntity extends BlockEntity implements Clearable {

    /** Number of cooking slots. */
    public static final int SLOTS = 4;
    /** How many ticks of heat one fuel item provides (configurable per fuel type via tag). */
    private static final int BASE_FUEL_TICKS = 200;
    /** Maximum heat storage. */
    public static final int MAX_HEAT = 4000;
    /** Heat consumed per tick per active cooking slot. */
    private static final int HEAT_PER_TICK_PER_SLOT = 2;
    /** Heat required to start cooking a dish (per dish). */
    private static final int START_HEAT_COST = 200;

    /** Items in each cooking slot (ingredient -> result transition). */
    private final NonNullList<ItemStack> cookingItems = NonNullList.withSize(SLOTS, ItemStack.EMPTY);
    /** Cooking progress for each slot (0 to required ticks). */
    private final int[] cookingProgress = new int[SLOTS];
    /** Total ticks required for each slot. */
    private final int[] cookingTotalTime = new int[SLOTS];
    /** The dish being cooked in each slot (for result lookup). */
    private final int[] cookingDishIndex = new int[SLOTS];
    /** Stored heat (like oven heat). */
    private int storedHeat = 0;
    /** Whether the cooker is lit/active. */
    private boolean lit = false;
    /** Fuel slot (single fuel slot for simplicity). */
    private ItemStack fuelItem = ItemStack.EMPTY;

    // Recipe lookup cache for campfire cooking recipes (used for ingredient -> dish mapping)
    private final RecipeManager.CachedCheck<SingleRecipeInput, net.minecraft.world.item.crafting.CampfireCookingRecipe> campfireRecipes =
            RecipeManager.createCheck(RecipeType.CAMPFIRE_COOKING);

    public RealTimeCookingBlockEntity(BlockPos pos, BlockState state) {
        super(KitchenBlocks.REAL_TIME_COOKER_ENTITY, pos, state);
        for (int i = 0; i < SLOTS; i++) {
            cookingDishIndex[i] = -1;
        }
    }

    // ===== Accessors =====

    public NonNullList<ItemStack> getCookingItems() {
        return cookingItems;
    }

    public int getCookingProgress(int slot) {
        return cookingProgress[slot];
    }

    public int getCookingTotalTime(int slot) {
        return cookingTotalTime[slot];
    }

    public int getCookingDishIndex(int slot) {
        return cookingDishIndex[slot];
    }

    public int getStoredHeat() {
        return storedHeat;
    }

    public int getMaxHeat() {
        return MAX_HEAT;
    }

    public boolean isLit() {
        return lit;
    }

    public ItemStack getFuelItem() {
        return fuelItem;
    }

    public void setFuelItem(ItemStack stack) {
        this.fuelItem = stack;
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    public boolean canAcceptFuel(ItemStack stack) {
        if (stack.isEmpty()) return false;
        // Check if it's a valid fuel (has burn time)
        if (level instanceof ServerLevel server) {
            return net.minecraft.world.item.crafting.FurnaceRecipes.getInstance().getBurnTime(stack) > 0;
        }
        // Client-side approximation: check common fuels
        return stack.is(net.minecraft.tags.ItemTags.COALS)
                || stack.is(net.minecraft.tags.ItemTags.LOGS)
                || stack.is(net.minecraft.tags.ItemTags.PLANKS)
                || stack.getItem() == net.minecraft.world.item.Items.LAVA_BUCKET
                || stack.getItem() == net.minecraft.world.item.Items.BLAZE_ROD;
    }

    // ===== Cooking logic =====

    /**
     * Tries to start cooking a dish in the first available slot.
     * Returns the slot index if successful, -1 otherwise.
     */
    public int tryStartCooking(ServerLevel level, Dish dish, ItemStack ingredientStack) {
        // Find empty slot
        int slot = -1;
        for (int i = 0; i < SLOTS; i++) {
            if (cookingItems.get(i).isEmpty()) {
                slot = i;
                break;
            }
        }
        if (slot < 0) return -1; // No free slot

        // Check if we have enough heat to start
        if (storedHeat < START_HEAT_COST) return -1;

        // Consume heat to start
        storedHeat -= START_HEAT_COST;

        // Place ingredient and set up cooking
        cookingItems.set(slot, ingredientStack.copyWithCount(1));
        cookingDishIndex[slot] = dish.ordinal();
        cookingTotalTime[slot] = dish.hobTicks() > 0 ? dish.hobTicks() : calculateCookTime(dish);
        cookingProgress[slot] = 0;

        setChanged();
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        return slot;
    }

    /**
     * Calculates cook time for dishes without explicit hobTicks.
     */
    private int calculateCookTime(Dish dish) {
        // Base time on dish's par ticks scaled by furnace station
        return Math.max(100, dish.parTicks(Station.FURNACE));
    }

    /** Server tick: consume fuel, use heat, advance cooking. */
    public static void serverTick(ServerLevel level, BlockPos pos, BlockState state, RealTimeCookingBlockEntity entity) {
        // Consume fuel to generate heat
        entity.consumeFuel(level);

        // If not lit and no heat, nothing happens
        if (!entity.lit && entity.storedHeat <= 0) {
            return;
        }

        // Auto-light if we have heat
        if (!entity.lit && entity.storedHeat > 0) {
            entity.lit = true;
            entity.setChanged();
            level.sendBlockUpdated(pos, state, state, 3);
        }

        // Use heat for active cooking slots
        int activeSlots = 0;
        for (int i = 0; i < SLOTS; i++) {
            if (!entity.cookingItems.get(i).isEmpty()) {
                activeSlots++;
            }
        }

        if (activeSlots > 0) {
            int heatCost = activeSlots * HEAT_PER_TICK_PER_SLOT;
            if (entity.storedHeat >= heatCost) {
                entity.storedHeat -= heatCost;
                entity.cookSlots(level);
            } else {
                // Not enough heat - cooking pauses, but we still consume what we have
                entity.storedHeat = 0;
                entity.lit = false;
                entity.setChanged();
                level.sendBlockUpdated(pos, state, state, 3);
            }
        } else {
            // No active cooking - idle heat drain
            if (entity.storedHeat > 0) {
                entity.storedHeat = Mth.clamp(entity.storedHeat - 1, 0, MAX_HEAT);
                if (entity.storedHeat == 0) {
                    entity.lit = false;
                    entity.setChanged();
                    level.sendBlockUpdated(pos, state, state, 3);
                }
            }
        }
    }

    /** Consumes fuel item to generate heat. */
    private void consumeFuel(ServerLevel level) {
        if (fuelItem.isEmpty()) return;

        int burnTime = net.minecraft.world.item.crafting.FurnaceRecipes.getInstance().getBurnTime(fuelItem);
        if (burnTime <= 0) return;

        // Consume one fuel item
        fuelItem.shrink(1);
        if (fuelItem.isEmpty()) {
            // Handle container item (e.g., lava bucket -> empty bucket)
            net.minecraft.world.item.ItemStackTemplate remainder = fuelItem.getItem().getCraftingRemainder();
            if (remainder != null) {
                fuelItem = remainder.create();
            }
        }

        // Add heat
        storedHeat = Math.min(MAX_HEAT, storedHeat + burnTime);
        setChanged();
    }

    /** Advances cooking progress for all active slots. */
    private void cookSlots(ServerLevel level) {
        boolean changed = false;
        for (int i = 0; i < SLOTS; i++) {
            ItemStack ingredient = cookingItems.get(i);
            if (ingredient.isEmpty()) continue;

            cookingProgress[i]++;
            changed = true;

            if (cookingProgress[i] >= cookingTotalTime[i]) {
                // Cooking complete - produce result
                finishCooking(level, i);
            }
        }
        if (changed) {
            setChanged();
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    /** Completes cooking in a slot, outputting the result item. */
    private void finishCooking(ServerLevel level, int slot) {
        int dishIdx = cookingDishIndex[slot];
        if (dishIdx < 0 || dishIdx >= Dish.values().length) {
            // Fallback: clear slot
            cookingItems.set(slot, ItemStack.EMPTY);
            cookingDishIndex[slot] = -1;
            cookingProgress[slot] = 0;
            cookingTotalTime[slot] = 0;
            return;
        }

        Dish dish = Dish.byIndex(dishIdx);
        ItemStack result = new ItemStack(ModItems.dish(dish), dish.batch());
        Quality quality = determineQuality(slot);
        quality.apply(result, dish);

        // Preserve if smoked in smoker
        if ((quality == Quality.GOOD || quality == Quality.PERFECT)
                && level.getBlockState(worldPosition).is(net.minecraft.world.level.block.Blocks.SMOKER)) {
            com.jaymoskitchen.fresh.Freshness.preserve(result);
        }

        // Eject result (in a real implementation, this would go to an output slot or drop)
        net.minecraft.world.entity.item.ItemEntity out = new net.minecraft.world.entity.item.ItemEntity(
                level,
                worldPosition.getX() + 0.5,
                worldPosition.getY() + 1.0,
                worldPosition.getZ() + 0.5,
                result
        );
        out.setDeltaMovement(level.getRandom().nextGaussian() * 0.01, 0.1, level.getRandom().nextGaussian() * 0.01);
        level.addFreshEntity(out);

        // Clear slot
        cookingItems.set(slot, ItemStack.EMPTY);
        cookingDishIndex[slot] = -1;
        cookingProgress[slot] = 0;
        cookingTotalTime[slot] = 0;

        level.gameEvent(net.minecraft.world.level.gameevent.GameEvent.BLOCK_CHANGE, worldPosition,
                net.minecraft.world.level.gameevent.GameEvent.Context.of(getBlockState()));
    }

    /** Determines quality based on cooking conditions (simplified). */
    private Quality determineQuality(int slot) {
        // For real-time cooking, quality is based on heat stability
        // Perfect if heat never dropped below 50% during cooking
        // This is a simplified version - in practice you'd track heat history
        return Quality.GOOD;
    }

    // ===== NBT =====

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        cookingItems.clear();
        ContainerHelper.loadAllItems(input, cookingItems);
        input.getIntArray("CookingProgress").ifPresent(arr -> System.arraycopy(arr, 0, cookingProgress, 0, Math.min(SLOTS, arr.length)));
        input.getIntArray("CookingTotalTime").ifPresent(arr -> System.arraycopy(arr, 0, cookingTotalTime, 0, Math.min(SLOTS, arr.length)));
        input.getIntArray("CookingDishIndex").ifPresent(arr -> System.arraycopy(arr, 0, cookingDishIndex, 0, Math.min(SLOTS, arr.length)));
        storedHeat = input.getIntOr("StoredHeat", 0);
        lit = input.getBooleanOr("Lit", false);
        fuelItem = input.read("FuelItem", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, cookingItems, true);
        output.putIntArray("CookingProgress", cookingProgress);
        output.putIntArray("CookingTotalTime", cookingTotalTime);
        output.putIntArray("CookingDishIndex", cookingDishIndex);
        output.putInt("StoredHeat", storedHeat);
        output.putBoolean("Lit", lit);
        if (!fuelItem.isEmpty()) {
            output.store("FuelItem", ItemStack.OPTIONAL_CODEC, fuelItem);
        }
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

    @Override
    public void clearContent() {
        cookingItems.clear();
        fuelItem = ItemStack.EMPTY;
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        if (level != null) {
            for (ItemStack stack : cookingItems) {
                if (!stack.isEmpty()) {
                    net.minecraft.world.Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), stack);
                }
            }
            if (!fuelItem.isEmpty()) {
                net.minecraft.world.Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), fuelItem);
            }
        }
        super.preRemoveSideEffects(pos, state);
    }

    /** Recipe lookup: finds a dish that can be made from the given ingredient at this station. */
    public @Nullable Dish findRecipeFor(ItemStack ingredient, Station station) {
        for (Dish dish : station.dishes()) {
            for (Map.Entry<Step, Integer> entry : dish.ingredients().entrySet()) {
                Step step = entry.getKey();
                if (step.matches(ingredient)) {
                    return dish;
                }
            }
        }
        return null;
    }

    /** Gets the campfire cooking recipe for an ingredient (for vanilla compatibility). */
    public Optional<RecipeHolder<net.minecraft.world.item.crafting.CampfireCookingRecipe>> getCampfireRecipe(ItemStack ingredient) {
        if (level instanceof ServerLevel server) {
            return campfireRecipes.getRecipeFor(new SingleRecipeInput(ingredient), server);
        }
        return Optional.empty();
    }
}