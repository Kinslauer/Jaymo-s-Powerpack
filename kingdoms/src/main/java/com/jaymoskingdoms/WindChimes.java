package com.jaymoskingdoms;

import java.util.function.Function;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * (batch 4) Wind chimes, after Better With Mods' chimes (MIT; its idea, our own code): hung under a block, a fence, a
 * chain or a rope, they ring soft notes now and then in rain (more in a thunderstorm) and whenever something walks
 * into them. They tell nothing but the sound (no signal, no message): a sentry's bell for a doorway. A sneaking player
 * with the Soft Step perk ({@link Skills}) slips past them without a sound. The storm bell ({@code Instruments}) is
 * the one for tornadoes.
 */
public final class WindChimes {
	/** How often (ticks) a chime feels the weather; how likely it rings then in rain, and in a thunderstorm. */
	static final int WEATHER_TICKS = 40;
	static final float RAIN_CHANCE = 0.3F, THUNDER_CHANCE = 0.75F;
	/** Rain this near (blocks, any side) reaches a chime under a roof's edge. */
	static final int OPEN_REACH = 2;
	/** A chime rung by a bump keeps still this long (ticks) before another can ring it. */
	static final int BUMP_QUIET = 15;
	/** The notes (semitones on a note block's scale): a pentatonic run, so any two together sound well. */
	static final int[] NOTES = {6, 8, 10, 13, 15, 18, 20};

	/** Rung lately (a bump): stays quiet until its next tick. */
	public static final BooleanProperty RINGING = BooleanProperty.create("ringing");

	public static Block WIND_CHIME, IRON_WIND_CHIME;
	public static Item WIND_CHIME_ITEM, IRON_WIND_CHIME_ITEM;

	private WindChimes() {
	}

	static void register() {
		WIND_CHIME = block("wind_chime", p -> new Chime(p, SoundEvents.NOTE_BLOCK_XYLOPHONE, 0.35F), BlockBehaviour.Properties.of()
			.mapColor(MapColor.PLANT).sound(SoundType.BAMBOO_WOOD));
		IRON_WIND_CHIME = block("iron_wind_chime", p -> new Chime(p, SoundEvents.NOTE_BLOCK_CHIME, 0.25F), BlockBehaviour.Properties.of()
			.mapColor(MapColor.METAL).sound(SoundType.CHAIN));
		WIND_CHIME_ITEM = item("wind_chime", WIND_CHIME);
		IRON_WIND_CHIME_ITEM = item("iron_wind_chime", IRON_WIND_CHIME);
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS).register(output -> {
			output.insertAfter(Items.BELL, WIND_CHIME_ITEM);
			output.insertAfter(WIND_CHIME_ITEM, IRON_WIND_CHIME_ITEM);
		});
	}

	private static Block block(String name, Function<BlockBehaviour.Properties, Block> make, BlockBehaviour.Properties properties) {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, JaymosKingdoms.id(name));
		return Registry.register(BuiltInRegistries.BLOCK, key, make.apply(properties.setId(key).strength(0.5F).noCollision().noOcclusion()
			.pushReaction(PushReaction.POPPED)));
	}

	private static Item item(String name, Block block) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosKingdoms.id(name));
		return Registry.register(BuiltInRegistries.ITEM, key, new BlockItem(block, new Item.Properties().setId(key).useBlockDescriptionPrefix()));
	}

	/** A chime: rods hanging from a little plate, rung by the weather or by whatever walks into it. */
	static final class Chime extends Block {
		private static final VoxelShape SHAPE = Block.box(4, 3, 4, 12, 16, 12);
		private final Holder<SoundEvent> note;
		private final float volume;

		Chime(BlockBehaviour.Properties properties, Holder<SoundEvent> note, float volume) {
			super(properties);
			this.note = note;
			this.volume = volume;
			registerDefaultState(stateDefinition.any().setValue(RINGING, false));
		}

		@Override
		protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
			builder.add(RINGING);
		}

		@Override
		protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
			return SHAPE;
		}

		@Override
		protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
			BlockState above = level.getBlockState(pos.above());
			return Block.canSupportCenter(level, pos.above(), Direction.DOWN) || above.is(BlockTags.FENCES) || above.is(BlockTags.WALLS)
				|| above.is(BlockTags.CHAINS) || above.is(Blocks.IRON_CHAIN) || RopeLift.hangsFrom(above);
		}

		@Override
		protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction direction,
			BlockPos neighbourPos, BlockState neighbour, RandomSource random) {
			return direction == Direction.UP && !canSurvive(state, level, pos) ? Blocks.AIR.defaultBlockState()
				: super.updateShape(state, level, ticks, pos, direction, neighbourPos, neighbour, random);
		}

		@Override
		protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
			if (!oldState.is(this)) {
				level.scheduleTick(pos, this, WEATHER_TICKS);
			}
		}

		/** The weather's turn: in rain it may ring (a bump's quiet is over too). */
		@Override
		protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
			if (state.getValue(RINGING)) {
				level.setBlock(pos, state.setValue(RINGING, false), Block.UPDATE_CLIENTS);
			}
			if (level.isRaining() && open(level, pos)
				&& random.nextFloat() < (level.isThundering() ? THUNDER_CHANCE : RAIN_CHANCE)) {
				ring(level, pos, random, 1 + random.nextInt(level.isThundering() ? 3 : 2));
			}
			level.scheduleTick(pos, this, WEATHER_TICKS + random.nextInt(WEATHER_TICKS / 2));
		}

		/** Walked into: it rings (a sneaking player with Soft Step slips by). */
		@Override
		protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier effects, boolean intersects) {
			if (!(level instanceof ServerLevel server) || state.getValue(RINGING) || entity.isSpectator()
				|| !entity.getBoundingBox().intersects(SHAPE.bounds().move(pos))) {
				return;
			}
			if (entity instanceof Player player && player.isShiftKeyDown() && Skills.rank(player, Skills.Perk.SOFT_STEP) > 0) {
				return;
			}
			server.setBlock(pos, state.setValue(RINGING, true), Block.UPDATE_CLIENTS);
			server.scheduleTick(pos, this, BUMP_QUIET);
			ring(server, pos, server.getRandom(), 2 + server.getRandom().nextInt(2));
		}

		/** A few soft notes, together. */
		private void ring(ServerLevel level, BlockPos pos, RandomSource random, int notes) {
			for (int i = 0; i < notes; i++) {
				float pitch = (float)Math.pow(2.0, (NOTES[random.nextInt(NOTES.length)] - 12) / 12.0);
				level.playSound(null, pos, note.value(), SoundSource.BLOCKS, volume * (0.7F + random.nextFloat() * 0.3F), pitch);
			}
		}

		/** Rain reaches it, or the air beside it (under a roof's edge). */
		private static boolean open(ServerLevel level, BlockPos pos) {
			for (int dx = -OPEN_REACH; dx <= OPEN_REACH; dx += OPEN_REACH) {
				for (int dz = -OPEN_REACH; dz <= OPEN_REACH; dz += OPEN_REACH) {
					if (level.isRainingAt(pos.offset(dx, 0, dz))) {
						return true;
					}
				}
			}
			return false;
		}
	}
}
