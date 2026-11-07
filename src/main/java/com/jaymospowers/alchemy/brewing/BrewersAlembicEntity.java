package com.jaymospowers.alchemy.brewing;

import com.jaymospowers.alchemy.Knowledge;
import com.jaymospowers.alchemy.cauldron.CauldronBrewing;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Prediction;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * (0.20.13) A Brewer's Alembic: up to {@link #HOLDS} bottles of one brew, distilled over heat into one fewer bottle,
 * each a grade better. The timer pauses when cold and starts over when the bottles change.
 */
public class BrewersAlembicEntity extends BlockEntity {
	/** How many bottles it holds, and the fewest that can be distilled. */
	public static final int HOLDS = 3, MIN_BOTTLES = 2;
	/** Bottles lost in a distilling. */
	public static final int BOTTLE_LOSS = 1;
	/** Ticks over heat that a distilling takes (60 s). */
	public static final int DISTIL_TICKS = 1200;
	/** Ticks between puffs from the spout. */
	public static final int PUFF_TICKS = 20;

	private final List<ItemStack> bottles = new ArrayList<>();
	private int progress;

	public BrewersAlembicEntity(BlockPos pos, BlockState state) {
		super(Brewing.BREWERS_ALEMBIC_ENTITY, pos, state);
	}

	/** Whether every bottle in it has been through already. */
	private boolean done() {
		for (ItemStack bottle : bottles) {
			if (!BrewGrades.distilled(bottle)) {
				return false;
			}
		}
		return !bottles.isEmpty();
	}

	public void use(ServerPlayer player, ItemStack held, InteractionHand hand) {
		if (!held.isEmpty()) {
			if (BrewGrades.isBrew(held)) {
				if (!Knowledge.knows(player, Brewing.DISTILLING)) {
					say(player, "unknown");
				} else if (BrewGrades.distilled(held)) {
					say(player, "distilled_once");
				} else if (BrewGrades.orPlain(held) == BrewGrade.MASTERWORK) {
					say(player, "masterwork");
				} else if (!bottles.isEmpty() && !ItemStack.isSameItemSameComponents(held, bottles.getFirst())) {
					say(player, "not_same");
				} else if (bottles.size() >= HOLDS) {
					say(player, "full");
				} else {
					bottles.add(held.copyWithCount(1));
					held.consume(1, player);
					progress = 0;
					level.playSound(null, worldPosition, SoundEvents.BOTTLE_EMPTY, SoundSource.BLOCKS, 0.8F, 1.2F);
					changed();
				}
			} else if ((held.is(Items.POTION) || held.is(Items.SPLASH_POTION) || held.is(Items.LINGERING_POTION))) {
				say(player, "nothing_to_distil");
			} else {
				say(player, "not_a_brew");
			}
			return;
		}
		if (player.isShiftKeyDown()) {
			if (!bottles.isEmpty()) {
				for (ItemStack bottle : bottles) {
					player.getInventory().placeItemBackInInventory(bottle, Prediction.SERVER_ONLY);
				}
				bottles.clear();
				progress = 0;
				level.playSound(null, worldPosition, SoundEvents.BOTTLE_FILL, SoundSource.BLOCKS, 0.8F, 1.2F);
				changed();
			}
			return;
		}
		if (bottles.isEmpty()) {
			say(player, "empty");
		} else if (done()) {
			say(player, "done");
		} else if (bottles.size() < MIN_BOTTLES) {
			say(player, "too_few");
		} else if (!CauldronBrewing.heated(level, worldPosition)) {
			say(player, "cold");
		} else {
			ItemStack first = bottles.getFirst();
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.brewers_alembic.status", first.getHoverName(),
				BrewGrades.orPlain(first).displayName(), bottles.size(), progress * 100 / DISTIL_TICKS));
		}
	}

	private static void say(ServerPlayer player, String key) {
		player.sendOverlayMessage(Component.translatable("message.jaymospowers.brewers_alembic." + key));
	}

	public static void serverTick(Level level, BlockPos pos, BlockState state, BrewersAlembicEntity alembic) {
		if (alembic.bottles.size() < MIN_BOTTLES || alembic.done() || !(level instanceof ServerLevel server)
			|| !CauldronBrewing.heated(level, pos)) {
			return;
		}
		alembic.progress++;
		alembic.setChanged();
		if (alembic.progress % PUFF_TICKS == 0) {
			int colour = alembic.bottles.getFirst().getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY).getColor();
			double x = pos.getX() + 0.85, y = pos.getY() + 0.8, z = pos.getZ() + 0.5;
			server.sendParticles(ColorParticleOption.create(ParticleTypes.ENTITY_EFFECT, colour | 0xFF000000), x, y, z, 2, 0.05, 0.05, 0.05, 0.0);
			server.sendParticles(ParticleTypes.CLOUD, x, y, z, 1, 0.05, 0.05, 0.05, 0.01);
		}
		if (alembic.progress >= DISTIL_TICKS) {
			alembic.distil(server);
		}
	}

	private void distil(ServerLevel server) {
		ItemStack first = bottles.getFirst();
		BrewGrade next = BrewGrades.orPlain(first).higher();
		int keep = bottles.size() - BOTTLE_LOSS;
		List<ItemStack> out = new ArrayList<>();
		for (int i = 0; i < keep; i++) {
			ItemStack copy = first.copyWithCount(1);
			BrewGrades.set(copy, next);
			BrewGrades.markDistilled(copy);
			out.add(copy);
		}
		bottles.clear();
		bottles.addAll(out);
		progress = 0;
		Containers.dropItemStack(server, worldPosition.getX() + 0.5, worldPosition.getY() + 1.0, worldPosition.getZ() + 0.5,
			new ItemStack(Items.GLASS_BOTTLE));
		server.playSound(null, worldPosition, SoundEvents.BREWING_STAND_BREW, SoundSource.BLOCKS, 1.0F, 1.0F);
		if (next == BrewGrade.MASTERWORK) {
			server.playSound(null, worldPosition, SoundEvents.PLAYER_LEVELUP, SoundSource.BLOCKS, 0.6F, 1.4F);
		}
		server.sendParticles(ParticleTypes.HAPPY_VILLAGER, worldPosition.getX() + 0.5, worldPosition.getY() + 1.0, worldPosition.getZ() + 0.5,
			6, 0.3, 0.2, 0.3, 0.0);
		changed();
	}

	/** Keeps the block's bottle count equal to what is in it, and saves. */
	private void changed() {
		setChanged();
		if (level != null) {
			BlockState state = getBlockState();
			if (state.getValue(BrewersAlembicBlock.BOTTLES) != bottles.size()) {
				level.setBlock(worldPosition, state.setValue(BrewersAlembicBlock.BOTTLES, bottles.size()), Block.UPDATE_ALL);
			}
		}
	}

	@Override
	public void preRemoveSideEffects(BlockPos pos, BlockState state) {
		if (level != null) {
			for (ItemStack bottle : bottles) {
				Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), bottle);
			}
			bottles.clear();
		}
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		bottles.clear();
		bottles.addAll(input.read("bottles", ItemStack.CODEC.listOf()).orElse(List.of()));
		progress = input.getIntOr("progress", 0);
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		output.store("bottles", ItemStack.CODEC.listOf(), List.copyOf(bottles));
		output.putInt("progress", progress);
	}
}
