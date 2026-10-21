package com.jaymoskitchen.brewing;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * A still's workings: load it with {@link #POT} fermented drinks (any mix), and over a fire it distils them for
 * {@link #DISTIL_TICKS} into one Strong Spirits (aqua vitae). Cold, it waits. Tuning: the constants below.
 */
public class StillEntity extends BlockEntity {
	/** Drinks the pot takes. */
	public static final int POT = 3;
	/** Two minutes over a fire. */
	public static final int DISTIL_TICKS = 2400;

	private final List<Brew> pot = new ArrayList<>();
	/** Ticks distilled so far (paused while the fire's out). */
	private int progress;
	/** One Strong Spirits is waiting to be bottled. */
	private boolean ready;

	public StillEntity(BlockPos pos, BlockState state) {
		super(Brewing.STILL_ENTITY, pos, state);
	}

	public int loaded() {
		return pot.size();
	}

	public boolean ready() {
		return ready;
	}

	// --- using it ---

	public InteractionResult use(Player player, InteractionHand hand, ItemStack held) {
		if (!(level instanceof ServerLevel)) {
			return InteractionResult.SUCCESS;
		}
		if (ready) {
			if (held.is(Items.GLASS_BOTTLE)) {
				player.setItemInHand(hand, ItemUtils.createFilledResult(held, player, new ItemStack(Brewing.STRONG_SPIRITS)));
				ready = false;
				play(SoundEvents.BOTTLE_FILL, 1.0F);
				markUpdated();
				return InteractionResult.SUCCESS_SERVER;
			}
		} else if (pot.size() < POT) {
			Brew brew = Brewing.brewOf(held);
			if (brew != null) {
				player.setItemInHand(hand, ItemUtils.createFilledResult(held, player, new ItemStack(Items.GLASS_BOTTLE)));
				pot.add(brew);
				progress = 0;
				play(SoundEvents.BOTTLE_EMPTY, 1.0F);
				markUpdated();
				if (pot.size() >= POT) {
					player.sendOverlayMessage(Component.translatable(heated() ? "message.jaymoskitchen.still.started"
						: "message.jaymoskitchen.still.cold"));
				} else {
					status(player);
				}
				return InteractionResult.SUCCESS_SERVER;
			}
		}
		if (!held.isEmpty()) {
			return InteractionResult.PASS;
		}
		status(player);
		return InteractionResult.SUCCESS_SERVER;
	}

	private void status(Player player) {
		if (ready) {
			player.sendOverlayMessage(Component.translatable("message.jaymoskitchen.still.ready"));
		} else if (pot.size() >= POT) {
			if (heated()) {
				player.sendOverlayMessage(Component.translatable("message.jaymoskitchen.still.distilling",
					Math.min(99, progress * 100 / DISTIL_TICKS)));
			} else {
				player.sendOverlayMessage(Component.translatable("message.jaymoskitchen.still.cold"));
			}
		} else if (pot.isEmpty()) {
			player.sendOverlayMessage(Component.translatable("message.jaymoskitchen.still.empty", POT));
		} else {
			player.sendOverlayMessage(Component.translatable("message.jaymoskitchen.still.loaded", pot.size(), POT));
		}
	}

	// --- heat and distilling ---

	/** Whether there's a fire under it: a lit campfire or furnace, fire, lava or magma. */
	private boolean heated() {
		return level != null && isHeat(level.getBlockState(worldPosition.below()));
	}

	public static boolean isHeat(BlockState below) {
		if (below.getBlock() instanceof CampfireBlock) {
			return below.getValue(CampfireBlock.LIT);
		}
		if (below.is(Blocks.FURNACE) || below.is(Blocks.SMOKER) || below.is(Blocks.BLAST_FURNACE)) {
			return below.getValue(BlockStateProperties.LIT);
		}
		return below.is(Blocks.FIRE) || below.is(Blocks.SOUL_FIRE) || below.is(Blocks.LAVA) || below.is(Blocks.MAGMA_BLOCK);
	}

	public static void serverTick(Level level, BlockPos pos, BlockState state, StillEntity still) {
		if (level instanceof ServerLevel server && !still.ready && still.pot.size() >= POT && isHeat(server.getBlockState(pos.below()))) {
			still.progress++;
			long now = server.getGameTime();
			if (now % 10L == 0L) {
				server.sendParticles(ParticleTypes.SMOKE, pos.getX() + 0.5, pos.getY() + 1.05, pos.getZ() + 0.5, 1, 0.1, 0.02, 0.1, 0.005);
			}
			if (now % 40L == 0L) {
				server.sendParticles(ParticleTypes.CLOUD, pos.getX() + 0.5, pos.getY() + 1.05, pos.getZ() + 0.5, 1, 0.1, 0.05, 0.1, 0.01);
				server.playSound(null, pos, SoundEvents.BUBBLE_COLUMN_BUBBLE_POP, SoundSource.BLOCKS, 0.3F, 1.6F);
			}
			if (still.progress >= DISTIL_TICKS) {
				still.pot.clear();
				still.progress = 0;
				still.ready = true;
				still.play(SoundEvents.BREWING_STAND_BREW, 1.0F);
				still.markUpdated();
			} else if (still.progress % 200 == 0) {
				still.setChanged();
			}
		}
	}

	/** The still's going: what's in it spills. */
	@Override
	public void preRemoveSideEffects(BlockPos pos, BlockState state) {
		if (level instanceof ServerLevel server) {
			for (Brew brew : pot) {
				Block.popResource(server, pos, new ItemStack(Brewing.drink(brew)));
			}
			if (ready) {
				Block.popResource(server, pos, new ItemStack(Brewing.STRONG_SPIRITS));
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
		pot.clear();
		for (String name : input.getStringOr("Pot", "").split(",")) {
			Brew brew = Brew.byName(name);
			if (brew != null && pot.size() < POT) {
				pot.add(brew);
			}
		}
		progress = input.getIntOr("Progress", 0);
		ready = input.getIntOr("Ready", 0) != 0;
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		writeShared(output);
	}

	private void writeShared(ValueOutput output) {
		StringBuilder names = new StringBuilder();
		for (Brew brew : pot) {
			if (names.length() > 0) {
				names.append(',');
			}
			names.append(brew.name());
		}
		output.putString("Pot", names.toString());
		output.putInt("Progress", progress);
		output.putInt("Ready", ready ? 1 : 0);
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
