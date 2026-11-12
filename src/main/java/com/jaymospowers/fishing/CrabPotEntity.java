package com.jaymospowers.fishing;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.Prediction;
import net.minecraft.world.ContainerHelper;
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
import net.minecraft.world.phys.AABB;

/**
 * Track B's leftover, the crab pot (Advanced Fishing's idea, our own code). Sink it on the bed and bait it with meat or a
 * fish: crabs, lobsters, crayfish and shrimp round about smell it and walk in ({@link Critter#goTo}), and others come in
 * from farther off over time ({@link #DRAW_CHANCE}, three times as often at night, when they're about). It holds
 * {@link #HOLDS}; a bait lasts {@link #BAIT_MEALS} of them. What's inside stays alive while it's in the water, to be put
 * back if it's too small. Empty hand takes the catch out; sneak with an empty hand takes the bait back.
 */
public class CrabPotEntity extends BlockEntity {
	static final int HOLDS = 6;
	static final int BAIT_MEALS = 3;
	static final int CHECK_EVERY = 40;
	static final double SMELL_RANGE = 8.0, IN_RANGE = 1.2;
	static final float DRAW_CHANCE = 0.006F, NIGHT_TIMES = 3.0F;

	private final NonNullList<ItemStack> caught = NonNullList.withSize(HOLDS, ItemStack.EMPTY);
	private ItemStack baitItem = ItemStack.EMPTY;
	private int meals;
	private String owner = "";

	public CrabPotEntity(BlockPos pos, BlockState state) {
		super(FishingBlocks.CRAB_POT_ENTITY, pos, state);
	}

	void setOwner(String name) {
		this.owner = name;
		setChanged();
	}

	/** {@code stack} will bait a pot: meat, a fish, or meat bait. */
	static boolean baits(ItemStack stack) {
		Tackle.Bait b = Tackle.baitOf(stack.getItem());
		return b != null && (b.cat() == Tackle.Cat.MEAT || b.cat() == Tackle.Cat.LIVE) || FishSpecies.ofItem(stack.getItem()) != null
			|| stack.is(Items.COD) || stack.is(Items.SALMON) || stack.is(Items.ROTTEN_FLESH) || stack.is(Items.CHICKEN)
			|| stack.is(Items.BEEF) || stack.is(Items.PORKCHOP) || stack.is(Items.MUTTON) || stack.is(Items.RABBIT);
	}

	InteractionResult useWith(ItemStack stack, Player player) {
		if (!baits(stack)) {
			return InteractionResult.TRY_WITH_EMPTY_HAND;
		}
		if (level == null || level.isClientSide()) {
			return InteractionResult.SUCCESS;
		}
		if (meals > 0) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.fishing.pot_baited").withStyle(ChatFormatting.GRAY));
			return InteractionResult.SUCCESS;
		}
		baitItem = stack.split(1);
		meals = BAIT_MEALS;
		level.playSound(null, worldPosition, SoundEvents.BUCKET_EMPTY_FISH, SoundSource.BLOCKS, 0.5F, 1.2F);
		changed();
		return InteractionResult.SUCCESS;
	}

	InteractionResult useEmpty(Player player) {
		if (level == null || level.isClientSide()) {
			return InteractionResult.SUCCESS;
		}
		if (player.isShiftKeyDown()) {
			if (!baitItem.isEmpty()) {
				player.getInventory().placeItemBackInInventory(baitItem, Prediction.SERVER_ONLY);
				baitItem = ItemStack.EMPTY;
				meals = 0;
				changed();
			}
			return InteractionResult.SUCCESS;
		}
		boolean any = false;
		long now = level.getGameTime();
		for (int i = 0; i < HOLDS; i++) {
			ItemStack stack = caught.get(i);
			if (!stack.isEmpty()) {
				FishCatch.Info info = FishCatch.info(stack);
				if (info != null) {
					// out of the water now: alive a while, to be kept or put back
					stack.set(FishCatch.CATCH, info.withAliveUntil(now + FishCatch.ALIVE_TICKS));
				}
				player.getInventory().placeItemBackInInventory(stack, Prediction.SERVER_ONLY);
				caught.set(i, ItemStack.EMPTY);
				any = true;
			}
		}
		if (!any) {
			player.sendOverlayMessage(Component.translatable(meals > 0 ? "message.jaymospowers.fishing.pot_empty"
				: "message.jaymospowers.fishing.pot_unbaited").withStyle(ChatFormatting.GRAY));
		} else {
			level.playSound(null, worldPosition, SoundEvents.BUCKET_FILL_FISH, SoundSource.BLOCKS, 0.6F, 1.0F);
			changed();
		}
		return InteractionResult.SUCCESS;
	}

	private int free() {
		for (int i = 0; i < HOLDS; i++) {
			if (caught.get(i).isEmpty()) {
				return i;
			}
		}
		return -1;
	}

	public static void serverTick(Level level, BlockPos pos, BlockState state, CrabPotEntity pot) {
		if (!(level instanceof ServerLevel server) || level.getGameTime() % CHECK_EVERY != 0 || pot.meals <= 0
			|| !state.getValue(FishingBlocks.WATERLOGGED) || pot.free() < 0) {
			return;
		}
		AABB around = new AABB(pos).inflate(SMELL_RANGE);
		for (Critter c : server.getEntitiesOfClass(Critter.class, around, c -> c.isAlive() && c.species().nettable() && !c.jelly())) {
			if (c.position().distanceTo(net.minecraft.world.phys.Vec3.atCenterOf(pos)) <= IN_RANGE + c.getBbWidth()) {
				pot.take(server, c.species(), c.weight());
				c.discard();
				return;
			}
			c.goTo(pos);
		}
		// others come in from farther off, more at night
		long t = server.getOverworldClockTime() % 24000L;
		float chance = DRAW_CHANCE * (t >= 13000L && t < 23000L ? NIGHT_TIMES : 1.0F);
		if (server.getRandom().nextFloat() >= chance) {
			return;
		}
		List<Species> here = new ArrayList<>();
		int total = 0;
		for (Species sp : new Species[] {Species.CRAB, Species.LOBSTER, Species.CRAYFISH, Species.SHRIMP}) {
			if (sp.livesIn(server.getBiome(pos))) {
				here.add(sp);
				total += sp.spawnWeight;
			}
		}
		if (total <= 0) {
			return;
		}
		int roll = server.getRandom().nextInt(total);
		for (Species sp : here) {
			roll -= sp.spawnWeight;
			if (roll < 0) {
				pot.take(server, sp, sp.rollKg(server.getRandom(), false));
				return;
			}
		}
	}

	/** One walked in: in the pot, alive while it's in the water, and the bait's a little more eaten. */
	private void take(ServerLevel level, Species sp, float kg) {
		int slot = free();
		if (slot < 0) {
			return;
		}
		ItemStack stack = FishCatch.loot(sp.kind(), kg);
		if (stack.isEmpty()) {
			return;
		}
		FishCatch.tag(stack, sp, kg, owner, level, "");
		stack.set(FishCatch.CATCH, FishCatch.info(stack).withAliveUntil(Long.MAX_VALUE));
		caught.set(slot, stack);
		if (--meals <= 0) {
			baitItem = ItemStack.EMPTY;
		}
		level.playSound(null, worldPosition, SoundEvents.WOOD_HIT, SoundSource.BLOCKS, 0.4F, 1.4F);
		changed();
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
			long now = level.getGameTime();
			for (ItemStack stack : caught) {
				FishCatch.Info info = FishCatch.info(stack);
				if (info != null) {
					stack.set(FishCatch.CATCH, info.withAliveUntil(now + FishCatch.ALIVE_TICKS));
				}
			}
			Containers.dropContents(level, pos, caught);
			Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), baitItem);
		}
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		for (int i = 0; i < HOLDS; i++) {
			caught.set(i, ItemStack.EMPTY);
		}
		ContainerHelper.loadAllItems(input, caught);
		baitItem = input.read("bait", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);
		meals = input.getIntOr("meals", 0);
		owner = input.getStringOr("owner", "");
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		ContainerHelper.saveAllItems(output, caught);
		output.store("bait", ItemStack.OPTIONAL_CODEC, baitItem);
		output.putInt("meals", meals);
		output.putString("owner", owner);
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
