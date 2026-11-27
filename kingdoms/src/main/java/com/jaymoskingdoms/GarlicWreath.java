package com.jaymoskingdoms;

import java.util.EnumMap;
import java.util.Map;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * The garlic wreath (Night's Blood patch 7, "Garlic wreath"): a ring of garlic and vine. Worn in a baubles slot, the amulet
 * (a necklace) or the head ({@code jaymospowers:baubles/amulet} and {@code .../head} tags), a vampire's bite slips off the
 * wearer and its grab cannot hold them ({@link HunterKit#slips}). Hung over a door (set against a wall, or a door, as a block),
 * no vampire comes through it, even one that was invited ({@link com.jaymoskingdoms.creatures.OldRules}, {@link #hung}). It
 * withers: the item loses a point of wear every {@link #WITHER_EVERY} ticks wherever it is carried and is gone after
 * {@link #LIFE_DAYS} days; the block dries a stage each day and falls away on the third. A block taken down comes back
 * as an item as worn as the block was.
 */
public final class GarlicWreath {
	/** Days a wreath lasts (item and block alike), and the item's wear points: one every {@link #WITHER_EVERY} ticks. */
	static final int LIFE_DAYS = 3;
	static final int WITHER_EVERY = 1200;
	public static final int WEAR = LIFE_DAYS * 24000 / WITHER_EVERY;
	/** How far (blocks) from a hung wreath a vampire is kept back: sideways, down and up, from the wreath's own cell. */
	static final int KEEP_SIDE = 1, KEEP_DOWN = 2, KEEP_UP = 1;

	public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;
	/** 0 fresh, 1 dry, 2 brittle. */
	public static final IntegerProperty AGE = IntegerProperty.create("age", 0, LIFE_DAYS - 1);

	public static Block BLOCK;
	public static Item ITEM;

	private GarlicWreath() {
	}

	static void register() {
		ResourceKey<Block> blockKey = ResourceKey.create(Registries.BLOCK, JaymosKingdoms.id("garlic_wreath"));
		BLOCK = Registry.register(BuiltInRegistries.BLOCK, blockKey, new WreathBlock(BlockBehaviour.Properties.of().setId(blockKey)
			.mapColor(MapColor.PLANT).noCollision().instabreak().sound(SoundType.VINE).pushReaction(PushReaction.POPPED)));
		ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, JaymosKingdoms.id("garlic_wreath"));
		ITEM = Registry.register(BuiltInRegistries.ITEM, itemKey, new WreathItem(BLOCK, new Item.Properties().setId(itemKey).useBlockDescriptionPrefix()
			.durability(WEAR)));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(output -> output.accept(ITEM));
	}

	/** Whether a wreath hangs close enough to {@code pos} to keep a vampire standing there out. */
	public static boolean hung(ServerLevel level, BlockPos pos) {
		for (BlockPos at : BlockPos.betweenClosed(pos.offset(-KEEP_SIDE, -KEEP_DOWN, -KEEP_SIDE), pos.offset(KEEP_SIDE, KEEP_UP, KEEP_SIDE))) {
			if (level.getBlockState(at).is(BLOCK)) {
				return true;
			}
		}
		return false;
	}

	/** The wreath as an item: it loses wear wherever it is carried or worn, and is gone once it is spent. */
	static final class WreathItem extends BlockItem {
		WreathItem(Block block, Properties properties) {
			super(block, properties);
		}

		@Override
		public void inventoryTick(ItemStack stack, ServerLevel level, Entity owner, @Nullable EquipmentSlot slot) {
			if (owner.tickCount % WITHER_EVERY != 0) {
				return;
			}
			stack.setDamageValue(stack.getDamageValue() + 1);
			if (stack.getDamageValue() >= stack.getMaxDamage()) {
				level.sendParticles(ParticleTypes.COMPOSTER, owner.getX(), owner.getY(0.6), owner.getZ(), 6, 0.3, 0.4, 0.3, 0.0);
				level.playSound(null, owner.blockPosition(), SoundEvents.GRASS_BREAK, SoundSource.PLAYERS, 0.6F, 0.7F);
				stack.shrink(1);
			}
		}
	}

	/** The wreath on a wall: it dries a stage a day and falls away. */
	static final class WreathBlock extends Block {
		/** How long a stage lasts (ticks). */
		static final int DAY = 24000;
		private final Map<Direction, VoxelShape> shapes = new EnumMap<>(Direction.class);

		WreathBlock(Properties properties) {
			super(properties);
			// a ring 14 across and 2 thick, against the wall behind it
			this.shapes.put(Direction.NORTH, Block.box(1, 1, 14, 15, 15, 16));
			this.shapes.put(Direction.SOUTH, Block.box(1, 1, 0, 15, 15, 2));
			this.shapes.put(Direction.WEST, Block.box(14, 1, 1, 16, 15, 15));
			this.shapes.put(Direction.EAST, Block.box(0, 1, 1, 2, 15, 15));
			this.registerDefaultState(this.defaultBlockState().setValue(FACING, Direction.NORTH).setValue(AGE, 0));
		}

		@Override
		protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
			builder.add(FACING, AGE);
		}

		@Override
		protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
			return this.shapes.getOrDefault(state.getValue(FACING), Shapes.block());
		}

		@Override
		protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
			Direction back = state.getValue(FACING).getOpposite();
			BlockPos wall = pos.relative(back);
			BlockState behind = level.getBlockState(wall);
			return behind.isFaceSturdy(level, wall, state.getValue(FACING)) || behind.is(BlockTags.DOORS);
		}

		@Override
		public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
			for (Direction d : context.getNearestLookingDirections()) {
				if (d.getAxis().isHorizontal()) {
					BlockState state = this.defaultBlockState().setValue(FACING, d.getOpposite());
					if (state.canSurvive(context.getLevel(), context.getClickedPos())) {
						return state;
					}
				}
			}
			return null;
		}

		@Override
		protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction direction, BlockPos neighbour,
			BlockState neighbourState, RandomSource random) {
			return direction == state.getValue(FACING).getOpposite() && !state.canSurvive(level, pos) ? Blocks.AIR.defaultBlockState()
				: super.updateShape(state, level, ticks, pos, direction, neighbour, neighbourState, random);
		}

		@Override
		public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
			// as worn as the item was
			int stage = stack.getMaxDamage() <= 0 ? 0 : Math.min(LIFE_DAYS - 1, stack.getDamageValue() * LIFE_DAYS / stack.getMaxDamage());
			if (stage != state.getValue(AGE)) {
				level.setBlock(pos, state.setValue(AGE, stage), Block.UPDATE_ALL);
			}
			level.scheduleTick(pos, this, DAY);
		}

		@Override
		protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
			int age = state.getValue(AGE);
			if (age + 1 >= LIFE_DAYS) {
				level.sendParticles(ParticleTypes.COMPOSTER, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 8, 0.3, 0.3, 0.3, 0.0);
				level.playSound(null, pos, SoundEvents.GRASS_BREAK, SoundSource.BLOCKS, 0.7F, 0.7F);
				level.removeBlock(pos, false);
				return;
			}
			level.setBlock(pos, state.setValue(AGE, age + 1), Block.UPDATE_ALL);
			level.scheduleTick(pos, this, DAY);
		}
	}
}
