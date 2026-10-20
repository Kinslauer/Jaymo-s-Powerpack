package com.jaymoskitchen.brewing;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Prediction;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * A cask's workings: fill it with water ({@link #CAPACITY} bottles) and {@link #BASE_PER_BATCH} of one base
 * ingredient (see {@link Brew}), and it seals and ferments for {@link #FERMENT_TICKS}. Then it holds
 * {@link #DRINKS_PER_BATCH} drinks, drawn off one glass bottle at a time. Tuning: the constants below.
 */
public class CaskEntity extends BlockEntity {
	/** Bottles of water a cask takes (a bucket fills it). */
	public static final int CAPACITY = 3;
	/** How many of the base ingredient a batch takes. */
	public static final int BASE_PER_BATCH = 3;
	/** Half a Minecraft day. */
	public static final int FERMENT_TICKS = 12000;
	public static final int DRINKS_PER_BATCH = 3;

	private int water;
	private @Nullable Brew base;
	private int baseCount;
	/** Ticks fermented so far (only counts while sealed). */
	private int progress;
	/** Drinks waiting (of {@link #base}); 0 unless the batch is done. */
	private int left;

	public CaskEntity(BlockPos pos, BlockState state) {
		super(Brewing.CASK_ENTITY, pos, state);
	}

	public int water() {
		return water;
	}

	public int baseCount() {
		return baseCount;
	}

	public int drinksLeft() {
		return left;
	}

	public @Nullable Brew base() {
		return base;
	}

	/** Full of water and base, and fermenting. */
	public boolean sealed() {
		return left == 0 && base != null && baseCount >= BASE_PER_BATCH && water >= CAPACITY;
	}

	// --- using it ---

	public InteractionResult use(Player player, InteractionHand hand, ItemStack held) {
		if (!(level instanceof ServerLevel)) {
			return InteractionResult.SUCCESS;
		}
		if (left > 0) {
			if (held.is(Items.GLASS_BOTTLE) && base != null) {
				player.setItemInHand(hand, ItemUtils.createFilledResult(held, player, new ItemStack(Brewing.drink(base))));
				left--;
				if (left == 0) {
					base = null;
				}
				play(SoundEvents.BOTTLE_FILL, 1.0F);
				markUpdated();
				return InteractionResult.SUCCESS_SERVER;
			}
		} else if (!sealed()) {
			if (held.is(Items.WATER_BUCKET) && water < CAPACITY) {
				player.setItemInHand(hand, ItemUtils.createFilledResult(held, player, new ItemStack(Items.BUCKET)));
				water = CAPACITY;
				play(SoundEvents.BUCKET_EMPTY, 1.0F);
				afterFilling(player);
				return InteractionResult.SUCCESS_SERVER;
			}
			if (isWaterBottle(held) && water < CAPACITY) {
				player.setItemInHand(hand, ItemUtils.createFilledResult(held, player, new ItemStack(Items.GLASS_BOTTLE)));
				water++;
				play(SoundEvents.BOTTLE_EMPTY, 1.0F);
				afterFilling(player);
				return InteractionResult.SUCCESS_SERVER;
			}
			Brew brew = Brewing.baseOf(held);
			if (brew != null) {
				if (base != null && base != brew) {
					player.sendOverlayMessage(Component.translatable("message.jaymoskitchen.cask.mixed"));
				} else if (baseCount >= BASE_PER_BATCH) {
					player.sendOverlayMessage(Component.translatable("message.jaymoskitchen.cask.base_full"));
				} else {
					if (held.is(Items.HONEY_BOTTLE)) {
						player.setItemInHand(hand, ItemUtils.createFilledResult(held, player, new ItemStack(Items.GLASS_BOTTLE)));
					} else if (!player.getAbilities().instabuild) {
						held.shrink(1);
					}
					base = brew;
					baseCount++;
					play(SoundEvents.ITEM_PICKUP, 0.8F);
					afterFilling(player);
				}
				return InteractionResult.SUCCESS_SERVER;
			}
			if (held.isEmpty() && player.isSecondaryUseActive() && base != null && baseCount > 0) {
				give(player, new ItemStack(base.baseItem()));
				baseCount--;
				if (baseCount == 0) {
					base = null;
				}
				play(SoundEvents.ITEM_PICKUP, 0.8F);
				markUpdated();
				return InteractionResult.SUCCESS_SERVER;
			}
		}
		if (!held.isEmpty()) {
			return InteractionResult.PASS;
		}
		status(player);
		return InteractionResult.SUCCESS_SERVER;
	}

	private static boolean isWaterBottle(ItemStack stack) {
		if (!stack.is(Items.POTION)) {
			return false;
		}
		PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
		return contents != null && contents.is(Potions.WATER);
	}

	/** Something went in: seal it if that was the last of it. */
	private void afterFilling(Player player) {
		if (sealed()) {
			progress = 0;
			play(SoundEvents.BARREL_CLOSE, 0.8F);
			player.sendOverlayMessage(Component.translatable("message.jaymoskitchen.cask.sealed"));
		} else {
			status(player);
		}
		markUpdated();
	}

	private void status(Player player) {
		if (left > 0 && base != null) {
			player.sendOverlayMessage(Component.translatable("message.jaymoskitchen.cask.ready", left, brewName(base)));
		} else if (sealed()) {
			player.sendOverlayMessage(Component.translatable("message.jaymoskitchen.cask.fermenting",
				Math.min(99, progress * 100 / FERMENT_TICKS)));
		} else if (base != null) {
			player.sendOverlayMessage(Component.translatable("message.jaymoskitchen.cask.filling", water, CAPACITY, baseCount,
				BASE_PER_BATCH, brewName(base)));
		} else if (water > 0) {
			player.sendOverlayMessage(Component.translatable("message.jaymoskitchen.cask.water", water, CAPACITY));
		} else {
			player.sendOverlayMessage(Component.translatable("message.jaymoskitchen.cask.empty"));
		}
	}

	private static Component brewName(Brew brew) {
		return Component.translatable("item.jaymoskitchen." + brew.id);
	}

	private static void give(Player player, ItemStack stack) {
		if (!player.getInventory().add(stack)) {
			player.drop(stack, false, Prediction.SERVER_ONLY);
		}
	}

	// --- fermenting ---

	public static void serverTick(Level level, BlockPos pos, BlockState state, CaskEntity cask) {
		if (level instanceof ServerLevel server && cask.sealed()) {
			cask.progress++;
			if (server.getGameTime() % 80L == 0L) {
				server.sendParticles(ParticleTypes.BUBBLE_POP, pos.getX() + 0.5, pos.getY() + 0.9, pos.getZ() + 0.5, 2, 0.2, 0.03, 0.2, 0.01);
				server.playSound(null, pos, SoundEvents.BUBBLE_COLUMN_BUBBLE_POP, SoundSource.BLOCKS, 0.3F, 0.8F);
			}
			if (cask.progress >= FERMENT_TICKS) {
				cask.finish();
			} else if (cask.progress % 200 == 0) {
				cask.setChanged();
			}
		}
	}

	private void finish() {
		left = DRINKS_PER_BATCH;
		water = 0;
		baseCount = 0;
		progress = 0;
		play(SoundEvents.BREWING_STAND_BREW, 1.0F);
		markUpdated();
	}

	/** The cask's going: the base and the drinks in it spill. */
	@Override
	public void preRemoveSideEffects(BlockPos pos, BlockState state) {
		if (level instanceof ServerLevel server && base != null) {
			if (baseCount > 0) {
				Block.popResource(server, pos, new ItemStack(base.baseItem(), baseCount));
			}
			if (left > 0) {
				Block.popResource(server, pos, new ItemStack(Brewing.drink(base), left));
			}
		}
	}

	private void play(SoundEvent sound, float pitch) {
		if (level != null) {
			level.playSound(null, worldPosition, sound, SoundSource.BLOCKS, 1.0F, pitch);
		}
	}

	private void markUpdated() {
		setChanged();
		if (level != null) {
			level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
		}
	}

	// --- saving and syncing ---

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		water = input.getIntOr("Water", 0);
		base = Brew.byName(input.getStringOr("Base", ""));
		baseCount = input.getIntOr("BaseCount", 0);
		progress = input.getIntOr("Progress", 0);
		left = input.getIntOr("Left", 0);
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		writeShared(output);
	}

	private void writeShared(ValueOutput output) {
		output.putInt("Water", water);
		output.putString("Base", base == null ? "" : base.name());
		output.putInt("BaseCount", baseCount);
		output.putInt("Progress", progress);
		output.putInt("Left", left);
	}

	@Override
	public ClientboundBlockEntityDataPacket getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this);
	}

	@Override
	public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
		TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, registries);
		writeShared(output);
		return output.buildResult();
	}
}
