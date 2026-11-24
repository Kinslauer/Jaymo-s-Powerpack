package com.jaymoskingdoms;

import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

/**
 * Hidden switches (Andy's pick from Infinity Buttons, MIT, ideas only): blocks that look like any other and work a
 * lever door. The {@link #BOOKSHELF_LEVER} is a bookshelf with a book that pulls out: it stays on until pulled again, like
 * a lever. The {@link #LOOSE_STONE} is a stone brick that pushes in: it springs back after {@link #PRESS_TICKS}, like a
 * button. Either powers every block beside it (as a block of redstone does) while it's on, and looks the same on or off.
 * A keep's builders hang its panic room's hatch on one ({@link Castle#panicRoom}).
 */
public final class HiddenSwitches {
	/** How long the loose stone stays pushed in (ticks). */
	static final int PRESS_TICKS = 20;

	public static Block BOOKSHELF_LEVER, LOOSE_STONE;

	private HiddenSwitches() {
	}

	public static void register() {
		BOOKSHELF_LEVER = block("bookshelf_lever", new SwitchBlock(props("bookshelf_lever", Blocks.BOOKSHELF), false));
		LOOSE_STONE = block("loose_stone", new SwitchBlock(props("loose_stone", Blocks.STONE_BRICKS), true));
		Item lever = item("bookshelf_lever", BOOKSHELF_LEVER), stone = item("loose_stone", LOOSE_STONE);
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.REDSTONE_BLOCKS).register(output -> {
			output.accept(lever);
			output.accept(stone);
		});
	}

	private static ResourceKey<Block> key(String name) {
		return ResourceKey.create(Registries.BLOCK, JaymosKingdoms.id(name));
	}

	private static BlockBehaviour.Properties props(String name, Block look) {
		return BlockBehaviour.Properties.ofFullCopy(look).setId(key(name));
	}

	private static Block block(String name, Block block) {
		return Registry.register(BuiltInRegistries.BLOCK, key(name), block);
	}

	private static Item item(String name, Block block) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosKingdoms.id(name));
		return Registry.register(BuiltInRegistries.ITEM, key, new BlockItem(block, new Item.Properties().setId(key).useBlockDescriptionPrefix()));
	}

	/** A switch that looks like the block it hides in: a lever (stays on) or a button (springs back). */
	static final class SwitchBlock extends Block {
		static final BooleanProperty POWERED = BlockStateProperties.POWERED;
		private final boolean button;

		SwitchBlock(BlockBehaviour.Properties properties, boolean button) {
			super(properties);
			this.button = button;
			registerDefaultState(stateDefinition.any().setValue(POWERED, false));
		}

		@Override
		protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
			if (button && state.getValue(POWERED)) {
				return InteractionResult.CONSUME;
			}
			if (!level.isClientSide()) {
				set(level, pos, state.cycle(POWERED), player);
				if (button) {
					level.scheduleTick(pos, this, PRESS_TICKS);
				}
			}
			return InteractionResult.SUCCESS;
		}

		@Override
		protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
			if (button && state.getValue(POWERED)) {
				set(level, pos, state.setValue(POWERED, false), null);
			}
		}

		private void set(Level level, BlockPos pos, BlockState state, @Nullable Player player) {
			level.setBlockAndUpdate(pos, state);
			level.updateNeighborsAt(pos, this, null);
			boolean on = state.getValue(POWERED);
			// a book sliding, a stone grinding in: quiet, and nothing to see
			level.playSound(player, pos, button ? SoundEvents.STONE_BUTTON_CLICK_ON : SoundEvents.BOOK_PAGE_TURN, SoundSource.BLOCKS,
				0.5F, on ? 0.7F : 0.55F);
			level.gameEvent(player, on ? GameEvent.BLOCK_ACTIVATE : GameEvent.BLOCK_DEACTIVATE, pos);
		}

		@Override
		protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
			if (!movedByPiston && state.getValue(POWERED)) {
				level.updateNeighborsAt(pos, this, null);
			}
		}

		@Override
		protected boolean isSignalSource(BlockState state) {
			return true;
		}

		@Override
		protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
			return state.getValue(POWERED) ? 15 : 0;
		}

		@Override
		protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
			builder.add(POWERED);
		}
	}
}
