package com.jaymoskingdoms;

import com.jaymoskingdoms.remnants.Element;
import com.jaymoskingdoms.remnants.Monoliths;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.AABB;

/**
 * (medieval picks, de-flicker) Lamps in abandoned places flicker and stutter: nobody is left to trim them. A lantern (or soul
 * lantern) in a ruin (any world-made structure with no living folk near and no law), the Irradiated remnants round their
 * monoliths, or a dead settlement (round the seat of a fallen faction) turns, on one of its random ticks, into a guttering
 * one: it gutters down near dark for a few ticks at a time and catches again, now and then stuttering twice in a row,
 * while anyone is near to see it. When folk come back (law, a travelled road, villagers near) it burns steady again. A
 * guttering lantern still burns its fuel ({@link Lanterns}) and goes out when it's spent; a soul lantern never does. The
 * court wizards' mote lantern ({@link MoteLanterns}) never gutters.
 */
public final class GutteringLamps {
	// ---- tuning ----
	/** Lit, and guttering down to (light levels): a lantern, a soul lantern. */
	static final int LIGHT = 15, LOW = 5, SOUL_LIGHT = 10, SOUL_LOW = 3;
	/** Burning steady between gutters (ticks, from and up to). */
	static final int STEADY_MIN = 40, STEADY_MAX = 200;
	/** How long a gutter lasts (ticks, from and up to). */
	static final int GUTTER_MIN = 2, GUTTER_MAX = 7;
	/** The chance a gutter stutters straight into another. */
	static final float STUTTER = 0.35F;
	/** It only gutters with someone this near to see it (blocks); otherwise it looks again after {@link #IDLE} ticks. */
	static final double WATCHED = 32.0;
	static final int IDLE = 100;
	/** No living villager this near a structure's lantern (blocks) and it's a ruin. */
	static final double FOLK_NEAR = 32.0;
	/** This near an Irradiated monolith (blocks) is its remnants. */
	static final double IRRADIATED_REACH = 64.0;
	/** This near a fallen faction's seat (blocks) is a dead settlement. */
	static final double DEAD_REACH = 64.0;

	public static final BooleanProperty LIT = BlockStateProperties.LIT;

	/** The guttering lantern and soul lantern. */
	public static Block LANTERN, SOUL_LANTERN;

	private GutteringLamps() {
	}

	/** A guttering lantern: {@link #LIT} or guttered low; back to {@code steady} when its place is tended again. */
	static final class Guttering extends LanternBlock {
		private final boolean soul;

		Guttering(BlockBehaviour.Properties properties, boolean soul) {
			super(properties);
			this.soul = soul;
			this.registerDefaultState(this.defaultBlockState().setValue(LIT, true));
		}

		Block steady() {
			return this.soul ? Blocks.SOUL_LANTERN : Blocks.LANTERN;
		}

		@Override
		protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
			super.createBlockStateDefinition(builder);
			builder.add(LIT);
		}

		@Override
		protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
			super.onPlace(state, level, pos, oldState, movedByPiston);
			if (!oldState.is(this)) {
				level.scheduleTick(pos, this, STEADY_MIN + level.getRandom().nextInt(STEADY_MAX - STEADY_MIN));
			}
		}

		@Override
		protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
			if (!level.hasNearbyAlivePlayer(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, WATCHED)) {
				if (!state.getValue(LIT)) {
					level.setBlock(pos, state.setValue(LIT, true), Block.UPDATE_ALL);
				}
				level.scheduleTick(pos, this, IDLE);
				return;
			}
			if (state.getValue(LIT)) {
				// gutters down
				level.setBlock(pos, state.setValue(LIT, false), Block.UPDATE_ALL);
				level.scheduleTick(pos, this, GUTTER_MIN + random.nextInt(GUTTER_MAX - GUTTER_MIN + 1));
			} else {
				// and catches again: a stutter gutters straight back down
				level.setBlock(pos, state.setValue(LIT, true), Block.UPDATE_ALL);
				level.scheduleTick(pos, this, random.nextFloat() < STUTTER ? GUTTER_MIN + random.nextInt(3)
					: STEADY_MIN + random.nextInt(STEADY_MAX - STEADY_MIN));
			}
		}

		@Override
		protected boolean isRandomlyTicking(BlockState state) {
			return true;
		}

		@Override
		protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
			if (!abandoned(level, pos)) {
				level.setBlock(pos, this.steady().defaultBlockState().setValue(HANGING, state.getValue(HANGING))
					.setValue(WATERLOGGED, state.getValue(WATERLOGGED)), Block.UPDATE_ALL);
				return;
			}
			if (!level.getBlockTicks().hasScheduledTick(pos, this)) {
				level.scheduleTick(pos, this, STEADY_MIN);
			}
			if (!this.soul) {
				// (its fuel still burns down; spent, it goes out, Lanterns.goOut)
				Lanterns.randomTick(state, level, pos);
			}
		}

		@Override
		public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
			if (!state.getValue(LIT) && random.nextInt(3) == 0) {
				double y = pos.getY() + (state.getValue(HANGING) ? 0.5 : 0.4);
				level.addParticle(ParticleTypes.SMOKE, pos.getX() + 0.5, y, pos.getZ() + 0.5, 0.0, 0.01, 0.0);
			}
		}

		@Override
		protected ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state, boolean includeData) {
			return new ItemStack(this.steady());
		}
	}

	public static void register() {
		LANTERN = block("guttering_lantern", false, LIGHT, LOW);
		SOUL_LANTERN = block("guttering_soul_lantern", true, SOUL_LIGHT, SOUL_LOW);
	}

	private static Block block(String name, boolean soul, int light, int low) {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, JaymosKingdoms.id(name));
		return Registry.register(BuiltInRegistries.BLOCK, key, new Guttering(BlockBehaviour.Properties.of().setId(key)
			.mapColor(MapColor.METAL).forceSolidOn().strength(3.5F).sound(SoundType.LANTERN).noOcclusion()
			.lightLevel(state -> state.getValue(LIT) ? light : low).pushReaction(PushReaction.POPPED), soul));
	}

	/**
	 * A steady lantern's random tick (mixin.LanternBlockMixin): one in an abandoned place starts guttering. True if it did
	 * (and nothing else is done with this tick).
	 */
	public static boolean gutter(BlockState state, ServerLevel level, BlockPos pos) {
		Block to = state.is(Blocks.LANTERN) ? LANTERN : state.is(Blocks.SOUL_LANTERN) ? SOUL_LANTERN : null;
		if (to == null || !abandoned(level, pos)) {
			return false;
		}
		level.setBlock(pos, to.defaultBlockState().setValue(LanternBlock.HANGING, state.getValue(LanternBlock.HANGING))
			.setValue(LanternBlock.WATERLOGGED, state.getValue(LanternBlock.WATERLOGGED)), Block.UPDATE_ALL);
		return true;
	}

	/** Steady lanterns that may start guttering (they take random ticks for it). */
	public static boolean mayGutter(BlockState state) {
		return state.is(Blocks.LANTERN) || state.is(Blocks.SOUL_LANTERN);
	}

	/**
	 * {@code pos} is somewhere nobody is left to tend the lamps: not where people live or travel ({@link Lamplight#kept}),
	 * and among the Irradiated remnants, in a dead settlement, or in a ruin (a world-made structure, haunted houses among
	 * them, with no living villager near).
	 */
	static boolean abandoned(ServerLevel level, BlockPos pos) {
		if (Lamplight.kept(level, pos)) {
			return false;
		}
		if (Monoliths.near(level, pos, Element.IRRADIATED, IRRADIATED_REACH)) {
			return true;
		}
		MinecraftServer server = level.getServer();
		if (level == Factions.level(server)) {
			for (Faction f : Factions.all(server)) {
				if (f.fallen() && f.seat().distSqr(pos) <= DEAD_REACH * DEAD_REACH) {
					return true;
				}
			}
		}
		return Ruins.inPiece(level, pos)
			&& level.getEntitiesOfClass(AbstractVillager.class, new AABB(pos).inflate(FOLK_NEAR), LivingEntity::isAlive).isEmpty();
	}
}
