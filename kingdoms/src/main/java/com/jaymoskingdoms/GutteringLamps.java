package com.jaymoskingdoms;

import com.jaymoskingdoms.remnants.Element;
import com.jaymoskingdoms.remnants.Monoliths;
import com.jaymoskingdoms.whaling.OilLampBlock;
import com.jaymoskingdoms.whaling.Whaling;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
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
 * (medieval picks, de-flicker) Lamps in abandoned places flicker and stutter: nobody is left to trim them. A lantern of
 * any kind (plain, soul, copper of every age, waxed or not) in a ruin (any world-made structure with no living folk near
 * and no law), the Irradiated remnants round their monoliths, or a dead settlement (round the seat of a fallen faction)
 * turns, on one of its random ticks, into a guttering one: it gutters down near dark for a few ticks at a time and
 * catches again, now and then stuttering twice in a row, while anyone is near to see it. When folk come back (law, a
 * travelled road, villagers near) it burns steady again. A guttering plain lantern still burns its fuel ({@link Lanterns})
 * and goes out when it's spent. A lit oil lamp there gutters the same way, in place ({@link OilLampBlock#GUTTERED}).
 * The court wizards' mote lantern ({@link MoteLanterns}) never gutters.
 */
public final class GutteringLamps {
	// ---- tuning ----
	/** Guttered down to (light levels): a lantern (lit 15), a soul lantern (lit 10). */
	static final int LOW = 5, SOUL_LOW = 3;
	/** Burning steady between gutters (ticks, from and up to). */
	static final int STEADY_MIN = 40, STEADY_MAX = 200;
	/** How long a gutter lasts (ticks, from and up to). */
	static final int GUTTER_MIN = 2, GUTTER_MAX = 7;
	/** The chance a gutter stutters straight into another. */
	static final float STUTTER = 0.35F;
	/** It only gutters with someone this near to see it (blocks); otherwise it looks again after {@link #IDLE} ticks. */
	static final double WATCHED = 32.0;
	static final int IDLE = 100;
	/** No living villager this near a structure's lamp (blocks) and it's a ruin. */
	static final double FOLK_NEAR = 32.0;
	/** This near an Irradiated monolith (blocks) is its remnants. */
	static final double IRRADIATED_REACH = 64.0;
	/** This near a fallen faction's seat (blocks) is a dead settlement. */
	static final double DEAD_REACH = 64.0;

	public static final BooleanProperty LIT = BlockStateProperties.LIT;

	/** The guttering plain lantern and soul lantern. */
	public static Block LANTERN, SOUL_LANTERN;
	/** Each steady lantern to the guttering one it turns into. */
	private static final Map<Block, Block> GUTTERS = new LinkedHashMap<>();

	/** Per level, the guttering oil lamps to the game time each next gutters or catches. */
	private static final Map<ServerLevel, Map<BlockPos, Long>> OIL_LAMPS = new HashMap<>();

	private GutteringLamps() {
	}

	/** A guttering lantern: {@link #LIT} or guttered low; back to {@code steady} when its place is tended again. */
	static final class Guttering extends LanternBlock {
		private final Block steady;

		Guttering(BlockBehaviour.Properties properties, Block steady) {
			super(properties);
			this.steady = steady;
			this.registerDefaultState(this.defaultBlockState().setValue(LIT, true));
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
			if (!watched(level, pos)) {
				if (!state.getValue(LIT)) {
					level.setBlock(pos, state.setValue(LIT, true), Block.UPDATE_ALL);
				}
				level.scheduleTick(pos, this, IDLE);
				return;
			}
			boolean lit = state.getValue(LIT);
			level.setBlock(pos, state.setValue(LIT, !lit), Block.UPDATE_ALL);
			level.scheduleTick(pos, this, next(lit, random));
		}

		@Override
		protected boolean isRandomlyTicking(BlockState state) {
			return true;
		}

		@Override
		protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
			if (!abandoned(level, pos)) {
				level.setBlock(pos, this.steady.defaultBlockState().setValue(HANGING, state.getValue(HANGING))
					.setValue(WATERLOGGED, state.getValue(WATERLOGGED)), Block.UPDATE_ALL);
				return;
			}
			if (!level.getBlockTicks().hasScheduledTick(pos, this)) {
				level.scheduleTick(pos, this, STEADY_MIN);
			}
			if (this.steady == Blocks.LANTERN) {
				// (its fuel still burns down; spent, it goes out, Lanterns.goOut)
				Lanterns.randomTick(state, level, pos);
			}
		}

		@Override
		public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
			if (!state.getValue(LIT) && random.nextInt(3) == 0) {
				smoke(level, pos, state.getValue(HANGING) ? 0.5 : 0.4);
			}
		}

		@Override
		protected ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state, boolean includeData) {
			return new ItemStack(this.steady);
		}
	}

	public static void register() {
		LANTERN = block("guttering_lantern", Blocks.LANTERN, 15, LOW);
		SOUL_LANTERN = block("guttering_soul_lantern", Blocks.SOUL_LANTERN, 10, SOUL_LOW);
		Blocks.COPPER_LANTERN.forEach(copper -> block("guttering_" + BuiltInRegistries.BLOCK.getKey(copper).getPath(), copper, 15, LOW));
		ServerTickEvents.END_SERVER_TICK.register(GutteringLamps::tickOilLamps);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> OIL_LAMPS.clear());
	}

	private static Block block(String name, Block steady, int light, int low) {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, JaymosKingdoms.id(name));
		Block gutter = Registry.register(BuiltInRegistries.BLOCK, key, new Guttering(BlockBehaviour.Properties.of().setId(key)
			.mapColor(MapColor.METAL).forceSolidOn().strength(3.5F).sound(SoundType.LANTERN).noOcclusion()
			.lightLevel(state -> state.getValue(LIT) ? light : low).pushReaction(PushReaction.POPPED), steady));
		GUTTERS.put(steady, gutter);
		return gutter;
	}

	/** How long (ticks) till a lamp that just went {@code lit} -> not, or back, changes again. */
	private static int next(boolean wasLit, RandomSource random) {
		if (wasLit) {
			// gutters down
			return GUTTER_MIN + random.nextInt(GUTTER_MAX - GUTTER_MIN + 1);
		}
		// and catches again: a stutter gutters straight back down
		return random.nextFloat() < STUTTER ? GUTTER_MIN + random.nextInt(3) : STEADY_MIN + random.nextInt(STEADY_MAX - STEADY_MIN);
	}

	private static boolean watched(ServerLevel level, BlockPos pos) {
		return level.hasNearbyAlivePlayer(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, WATCHED);
	}

	private static void smoke(Level level, BlockPos pos, double up) {
		level.addParticle(ParticleTypes.SMOKE, pos.getX() + 0.5, pos.getY() + up, pos.getZ() + 0.5, 0.0, 0.01, 0.0);
	}

	/**
	 * A steady lantern's random tick (mixin.LanternBlockMixin, mixin.WeatheringLanternBlockMixin): one in an abandoned
	 * place starts guttering. True if it did (and nothing else is done with this tick).
	 */
	public static boolean gutter(BlockState state, ServerLevel level, BlockPos pos) {
		Block to = GUTTERS.get(state.getBlock());
		if (to == null || !abandoned(level, pos)) {
			return false;
		}
		level.setBlock(pos, to.defaultBlockState().setValue(LanternBlock.HANGING, state.getValue(LanternBlock.HANGING))
			.setValue(LanternBlock.WATERLOGGED, state.getValue(LanternBlock.WATERLOGGED)), Block.UPDATE_ALL);
		return true;
	}

	/**
	 * Steady lanterns that may start guttering (they take random ticks for it). Asked by the game while it builds its block
	 * states, before our own are registered, so by the lantern itself, not {@link #GUTTERS}.
	 */
	public static boolean mayGutter(BlockState state) {
		Block b = state.getBlock();
		if (b == Blocks.LANTERN || b == Blocks.SOUL_LANTERN) {
			return true;
		}
		net.minecraft.resources.Identifier id = BuiltInRegistries.BLOCK.getKey(b);
		return b instanceof LanternBlock && id.getNamespace().equals("minecraft") && id.getPath().endsWith("copper_lantern");
	}

	// ---- oil lamps ----

	/** A lit oil lamp's random tick ({@link OilLampBlock}): abandoned, it gutters (in place); tended again, it burns steady. */
	public static void oilLampTick(BlockState state, ServerLevel level, BlockPos pos) {
		Map<BlockPos, Long> lamps = OIL_LAMPS.computeIfAbsent(level, l -> new HashMap<>());
		if (state.getValue(OilLampBlock.LIT) && abandoned(level, pos)) {
			lamps.putIfAbsent(pos.immutable(), level.getGameTime() + STEADY_MIN);
		} else if (lamps.remove(pos) != null && state.getValue(OilLampBlock.GUTTERED)) {
			level.setBlock(pos, state.setValue(OilLampBlock.GUTTERED, false), Block.UPDATE_ALL);
		}
	}

	private static void tickOilLamps(MinecraftServer server) {
		for (Map.Entry<ServerLevel, Map<BlockPos, Long>> e : OIL_LAMPS.entrySet()) {
			ServerLevel level = e.getKey();
			long now = level.getGameTime();
			for (Iterator<Map.Entry<BlockPos, Long>> it = e.getValue().entrySet().iterator(); it.hasNext();) {
				Map.Entry<BlockPos, Long> lamp = it.next();
				if (now < lamp.getValue()) {
					continue;
				}
				BlockPos pos = lamp.getKey();
				if (!level.isLoaded(pos)) {
					it.remove();
					continue;
				}
				BlockState state = level.getBlockState(pos);
				if (!state.is(Whaling.OIL_LAMP) || !state.getValue(OilLampBlock.LIT)) {
					it.remove();
					continue;
				}
				boolean low = state.getValue(OilLampBlock.GUTTERED);
				if (!watched(level, pos)) {
					if (low) {
						level.setBlock(pos, state.setValue(OilLampBlock.GUTTERED, false), Block.UPDATE_ALL);
					}
					lamp.setValue(now + IDLE);
					continue;
				}
				level.setBlock(pos, state.setValue(OilLampBlock.GUTTERED, !low), Block.UPDATE_ALL);
				lamp.setValue(now + next(!low, level.getRandom()));
			}
		}
	}

	/** Smoke off a guttered oil lamp (its {@code animateTick}). */
	public static void oilLampSmoke(BlockState state, Level level, BlockPos pos, RandomSource random) {
		if (state.getValue(OilLampBlock.LIT) && state.getValue(OilLampBlock.GUTTERED) && random.nextInt(3) == 0) {
			smoke(level, pos, state.getValue(LanternBlock.HANGING) ? 0.85 : 0.6);
		}
	}

	// ---- where ----

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
