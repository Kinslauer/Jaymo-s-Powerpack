package com.jaymospowers.power.zfighter;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.Powers;
import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.Vec3;

/**
 * (0.17.60, Andy's NOEA picks) The Lookout, bigger. Andy: "add all that on the LARGER floating platform above the tower
 * called the lookout"; "our lookout structure is nice, but needs more". The disc grows from {@link #OLD_RADIUS} to
 * {@link #RADIUS} blocks. The old courtyard (the Keeper's palace on the north half, four flowering azaleas) stays as it
 * was; round it, out on the new ground (x east, z south, from the pole), stand:
 * <ul>
 * <li>the <b>Keeper's quarters</b> (east: a bed, shelves, a lectern, a carpet) and the <b>technique library</b> (west: shelves
 * and three lecterns; right-click a lectern to buy a lesson of the masters back, {@link #LIBRARY_COST} TP, once you have had
 * a good master's teaching ({@link ZFighterGodRitual#lessons}), for the lesson a master would give, no master needed);</li>
 * <li>the <b>meditation garden</b> (south east: Hermit Beans on watered farmland, they drop a bean now and then when ripe);</li>
 * <li>the <b>sparring ring</b> (south west edge: a raised square, four corner posts, a rope between them, a Training Post
 * beside it) and a small <b>gravity room</b> (north west) with a {@link ZFighterGravityMachine} in it;</li>
 * <li>the <b>seeing pool</b> (north east): stand in it, or right-click it, and the nearest orbs still in the world are named
 * with their direction and distance, a trail of light pointing to each ({@link #POOL_SHOWN}, every {@link #POOL_EVERY} ticks);</li>
 * <li>the <b>god ritual circle</b> (south: {@link ZFighterGodRitual#CIRCLE_DZ} is 17 now).</li>
 * </ul>
 * <b>Existing worlds:</b> the platform is already placed. The first time a player comes within {@link #UPGRADE_NEAR} blocks of
 * a Tower whose Lookout is the old one ({@link #BUILT} below {@link #VERSION}) it is upgraded once: the new disc and every
 * new building go into <em>air only</em> (the floor layer may replace the platform's own quartz, calcite and gold, so the
 * old gold rim becomes courtyard); nothing of a player's is touched, the old courtyard stays, and the old painted ritual
 * circle is wiped. The version is saved on the overworld.
 */
public final class ZFighterLookout {
	/** The disc's radius now and before (blocks). */
	public static final int RADIUS = 23;
	static final int OLD_RADIUS = 13;
	/** The layout version saved on the overworld: 2 is this one; nothing saved is the old Lookout. */
	static final int VERSION = 2;
	/** A Lookout is upgraded when a player is within this many blocks (flat) of its pole. */
	static final double UPGRADE_NEAR = 64.0;
	/** How deep the bowl under the disc goes (at the middle). */
	static final int BOWL = 7;
	/** The seeing pool: orbs shown, ticks between shows for one player. */
	static final int POOL_SHOWN = 3;
	static final int POOL_EVERY = 100;
	/** The trail of light to each orb: how long (blocks) and how far apart its motes are. */
	static final double TRAIL = 9.0, TRAIL_STEP = 1.2;
	/** A lesson bought back at a library lectern costs this much TP. */
	static final int LIBRARY_COST = 1000;

	/** The layout version built (saved on the overworld). */
	public static final AttachmentType<Integer> BUILT = AttachmentRegistry.create(JaymosPowers.id("zfighter_lookout_version"),
		builder -> builder.persistent(Codec.INT));

	private static final String[] ARROWS = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};
	private static final Map<UUID, Long> PINGED = new HashMap<>();

	// ---- where things stand (x east, z south, from the pole) ----
	/** The seeing pool: the rim (x, z) and the water inside. */
	static final int POOL_X0 = 8, POOL_X1 = 14, POOL_Z0 = -15, POOL_Z1 = -9;
	/** The library's three lecterns (x, z). */
	static final int LECTERN_X = -17, LECTERN_Z0 = -2, LECTERN_Z1 = 0;

	private ZFighterLookout() {
	}

	static void init() {
		ServerTickEvents.END_SERVER_TICK.register(ZFighterLookout::tick);
		UseBlockCallback.EVENT.register(ZFighterLookout::useBlock);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> PINGED.clear());
	}

	// ---------------------------------------------------------------- the build

	/** Where one build puts blocks: relative to the pole, {@code up} above the floor layer (0 is the floor itself, below it the bowl). */
	private static final class Site {
		final ServerLevel level;
		final int cx, cz, y;
		final boolean fresh;

		Site(ServerLevel level, int cx, int cz, boolean fresh) {
			this.level = level;
			this.cx = cx;
			this.cz = cz;
			this.y = ZFighterSites.LOOKOUT_Y;
			this.fresh = fresh;
		}

		BlockPos at(int dx, int up, int dz) {
			return new BlockPos(cx + dx, y + up, cz + dz);
		}

		BlockState get(int dx, int up, int dz) {
			return level.getBlockState(at(dx, up, dz));
		}

		void put(int dx, int up, int dz, BlockState state) {
			place(dx, up, dz, state, Block.UPDATE_CLIENTS);
		}

		/** Fences and bars: placed with updates, so they join their neighbours. */
		void join(int dx, int up, int dz, BlockState state) {
			place(dx, up, dz, state, Block.UPDATE_ALL);
		}

		private void place(int dx, int up, int dz, BlockState state, int flags) {
			BlockPos pos = at(dx, up, dz);
			if (!fresh) {
				BlockState now = level.getBlockState(pos);
				// an upgrade places into air only; the floor and the bowl may take the platform's own stone
				if (!now.isAir() && !(up <= 0 && platform(now))) {
					return;
				}
			}
			level.setBlock(pos, state, flags);
		}

		void fill(int x0, int u0, int z0, int x1, int u1, int z1, BlockState state) {
			for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) {
				for (int u = Math.min(u0, u1); u <= Math.max(u0, u1); u++) {
					for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) {
						put(x, u, z, state);
					}
				}
			}
		}
	}

	/** The Lookout's own floor stone (what an upgrade may rebuild). */
	private static boolean platform(BlockState state) {
		return state.is(Blocks.SMOOTH_QUARTZ) || state.is(Blocks.QUARTZ_BRICKS) || state.is(Blocks.GOLD_BLOCK) || state.is(Blocks.CALCITE)
			|| state.is(Blocks.CHISELED_QUARTZ_BLOCK) || state.is(Blocks.AMETHYST_BLOCK);
	}

	private static BlockState named(String id) {
		return BuiltInRegistries.BLOCK.getValue(Identifier.withDefaultNamespace(id)).defaultBlockState();
	}

	private static <T extends Comparable<T>> BlockState with(BlockState state, Property<T> property, T value) {
		return state.hasProperty(property) ? state.setValue(property, value) : state;
	}

	private static BlockState facing(BlockState state, Direction direction) {
		return with(state, BlockStateProperties.HORIZONTAL_FACING, direction);
	}

	private static BlockState air() {
		return Blocks.AIR.defaultBlockState();
	}

	private static BlockState ladder() {
		return Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.SOUTH);
	}

	/**
	 * Builds the Lookout round the pole at ({@code cx}, {@code cz}): all of it when {@code fresh} (the Tower has just been
	 * built), or only what is missing when upgrading an old one. Sets the saved version.
	 */
	static void build(ServerLevel level, int cx, int cz, boolean fresh) {
		int reach = RADIUS + 2;
		for (int x = cx - reach >> 4; x <= cx + reach >> 4; x++) {
			for (int z = cz - reach >> 4; z <= cz + reach >> 4; z++) {
				level.getChunk(x, z);
			}
		}
		Site s = new Site(level, cx, cz, fresh);
		disc(s);
		if (fresh) {
			palace(s);
		} else {
			wipeOldCircle(s);
		}
		quarters(s);
		library(s);
		garden(s);
		ring(s);
		gravityRoom(s);
		pool(s);
		circle(s);
		level.setAttached(BUILT, VERSION);
	}

	/** The floating disc: a floor of quartz ringed with gold, an inlaid ring between the courtyard and the rooms, and the bowl under it. */
	private static void disc(Site s) {
		for (int dx = -RADIUS; dx <= RADIUS; dx++) {
			for (int dz = -RADIUS; dz <= RADIUS; dz++) {
				double d = Math.sqrt(dx * dx + dz * dz);
				if (d > RADIUS + 0.5) {
					continue;
				}
				boolean hole = dx == 0 && dz == 1;
				BlockState top = d > RADIUS - 0.5 ? Blocks.GOLD_BLOCK.defaultBlockState()
					: d > OLD_RADIUS + 0.5 && d < OLD_RADIUS + 1.5 ? Blocks.CHISELED_QUARTZ_BLOCK.defaultBlockState()
					: (dx + dz) % 4 == 0 ? Blocks.QUARTZ_BRICKS.defaultBlockState() : Blocks.SMOOTH_QUARTZ.defaultBlockState();
				s.put(dx, 0, dz, hole ? ladder() : top);
				int depth = (int)Math.round(BOWL * (1.0 - d / (RADIUS + 1.0)));
				for (int down = 1; down <= depth; down++) {
					if (!hole && !(dx == 0 && dz == 0)) {
						s.put(dx, -down, dz, down == depth ? Blocks.GOLD_BLOCK.defaultBlockState() : Blocks.CALCITE.defaultBlockState());
					}
				}
				if (s.fresh) {
					for (int up = 1; up <= 12; up++) {
						s.put(dx, up, dz, air());
					}
				}
			}
		}
	}

	/** The old courtyard, as it always was: azaleas, the Keeper's palace and its two doors (a fresh build only). */
	private static void palace(Site s) {
		for (int[] tree : new int[][] {{-8, 4}, {8, 4}, {-6, 8}, {6, 8}}) {
			s.put(tree[0], 0, tree[1], Blocks.GRASS_BLOCK.defaultBlockState());
			s.put(tree[0], 1, tree[1], Blocks.FLOWERING_AZALEA.defaultBlockState());
		}
		int pz = -6;
		for (int dx = -5; dx <= 5; dx++) {
			for (int dz = -4; dz <= 4; dz++) {
				s.put(dx, 1, pz + dz, Blocks.QUARTZ_BRICKS.defaultBlockState());
				s.put(dx, 7, pz + dz, Math.abs(dx) == 5 || Math.abs(dz) == 4 ? Blocks.GOLD_BLOCK.defaultBlockState() : Blocks.SMOOTH_QUARTZ.defaultBlockState());
			}
			s.put(dx, 8, pz, Blocks.SMOOTH_QUARTZ_SLAB.defaultBlockState());
		}
		for (int dx = -5; dx <= 5; dx += 2) {
			for (int dz : new int[] {-4, 4}) {
				for (int up = 2; up <= 6; up++) {
					s.put(dx, up, pz + dz, Blocks.QUARTZ_PILLAR.defaultBlockState());
				}
			}
		}
		for (int up = 2; up <= 3; up++) {
			s.put(-2, up, pz - 3, ZFighterSites.CHAMBER_DOOR.defaultBlockState());
			s.put(2, up, pz - 3, ZFighterSites.VERDANT_GATE.defaultBlockState());
		}
		s.put(0, 2, pz - 3, Blocks.LANTERN.defaultBlockState());
		ZFighterSites.spawnNpc(s.level, new Vec3(s.cx + 0.5, s.y + 2, s.cz + pz + 0.5), ZFighterSites.KEEPER_TAG, "entity.jaymospowers.keeper",
			new MerchantOffers());
	}

	/** The old god ritual circle (south of the pole, five blocks) painted over with plain floor. */
	private static void wipeOldCircle(Site s) {
		int cz = 5;
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				if (s.get(dx, 0, cz + dz).is(Blocks.CHISELED_QUARTZ_BLOCK) || s.get(dx, 0, cz + dz).is(Blocks.GOLD_BLOCK)) {
					s.put(dx, 0, cz + dz, Blocks.SMOOTH_QUARTZ.defaultBlockState());
				}
			}
		}
		for (int[] pad : new int[][] {{0, 4}, {-4, 1}, {-2, -3}, {2, -3}, {4, 1}}) {
			if (s.get(pad[0], 0, cz + pad[1]).is(Blocks.AMETHYST_BLOCK)) {
				s.put(pad[0], 0, cz + pad[1], Blocks.SMOOTH_QUARTZ.defaultBlockState());
			}
		}
	}

	/** A walled room: walls {@code height} high round x0..x1, z0..z1, pillars at the corners, a flat roof over it. */
	private static void room(Site s, int x0, int z0, int x1, int z1, int height, BlockState wall, BlockState roof) {
		for (int x = x0; x <= x1; x++) {
			for (int z = z0; z <= z1; z++) {
				boolean edge = x == x0 || x == x1 || z == z0 || z == z1;
				boolean corner = (x == x0 || x == x1) && (z == z0 || z == z1);
				for (int up = 1; up <= height; up++) {
					if (edge) {
						s.put(x, up, z, corner ? Blocks.QUARTZ_PILLAR.defaultBlockState() : wall);
					} else {
						s.put(x, up, z, air());
					}
				}
				s.put(x, height + 1, z, edge ? Blocks.GOLD_BLOCK.defaultBlockState() : roof);
			}
		}
	}

	/** The Keeper's quarters, east: a bed, shelves, a lectern, a carpet, a lantern; the way in is on the courtyard side. */
	private static void quarters(Site s) {
		room(s, 14, -4, 20, 2, 4, Blocks.QUARTZ_BRICKS.defaultBlockState(), Blocks.SMOOTH_QUARTZ.defaultBlockState());
		s.fill(14, 1, -1, 14, 2, -1, air());
		s.fill(20, 2, -1, 20, 3, -1, Blocks.GLASS.defaultBlockState());
		s.fill(17, 2, -4, 17, 3, -4, Blocks.GLASS.defaultBlockState());
		s.fill(17, 2, 2, 17, 3, 2, Blocks.GLASS.defaultBlockState());
		for (int x = 16; x <= 18; x++) {
			for (int z = -1; z <= 1; z++) {
				s.put(x, 1, z, named("light_blue_carpet"));
			}
		}
		bed(s, 19, -2, Direction.NORTH);
		s.fill(15, 1, -3, 17, 2, -3, Blocks.BOOKSHELF.defaultBlockState());
		s.put(15, 1, 1, facing(Blocks.LECTERN.defaultBlockState(), Direction.EAST));
		s.put(19, 1, 1, Blocks.LANTERN.defaultBlockState());
		s.put(15, 1, -2, Blocks.FLOWER_POT.defaultBlockState());
	}

	/** A bed with its foot at (x, z) and its head one block along {@code toward}. */
	private static void bed(Site s, int x, int z, Direction toward) {
		BlockState bed = with(facing(named("red_bed"), toward), BlockStateProperties.BED_PART, BedPart.FOOT);
		s.put(x, 1, z, bed);
		s.put(x + toward.getStepX(), 1, z + toward.getStepZ(), with(bed, BlockStateProperties.BED_PART, BedPart.HEAD));
	}

	/** The technique library, west: shelves all round, three lecterns in a row ({@link #LECTERN_X}), a green carpet. */
	private static void library(Site s) {
		room(s, -20, -4, -14, 2, 4, Blocks.QUARTZ_BRICKS.defaultBlockState(), Blocks.SMOOTH_QUARTZ.defaultBlockState());
		s.fill(-14, 1, -1, -14, 2, -1, air());
		s.fill(-20, 2, -1, -20, 3, -1, Blocks.GLASS.defaultBlockState());
		s.fill(-17, 2, -4, -17, 3, -4, Blocks.GLASS.defaultBlockState());
		for (int x : new int[] {-18, -16, -15}) {
			for (int z = -2; z <= 0; z++) {
				s.put(x, 1, z, named("green_carpet"));
			}
		}
		s.fill(-19, 1, -3, -19, 3, 1, Blocks.BOOKSHELF.defaultBlockState());
		s.fill(-18, 1, -3, -15, 2, -3, Blocks.BOOKSHELF.defaultBlockState());
		s.fill(-18, 1, 1, -16, 2, 1, Blocks.BOOKSHELF.defaultBlockState());
		for (int z = LECTERN_Z0; z <= LECTERN_Z1; z++) {
			s.put(LECTERN_X, 1, z, facing(Blocks.LECTERN.defaultBlockState(), Direction.EAST));
		}
		s.put(-15, 1, -3, Blocks.LANTERN.defaultBlockState());
		s.put(-15, 1, 1, Blocks.LANTERN.defaultBlockState());
	}

	/** The meditation garden, south east: farmland round a pool of water, Hermit Beans coming up, lanterns on the corners. */
	private static void garden(Site s) {
		for (int x = 9; x <= 15; x++) {
			for (int z = 9; z <= 15; z++) {
				boolean rim = x == 9 || x == 15 || z == 9 || z == 15;
				if (rim) {
					s.put(x, 0, z, Blocks.MOSS_BLOCK.defaultBlockState());
				} else if (x == 12 && z == 12) {
					s.put(x, 0, z, Blocks.WATER.defaultBlockState());
				} else {
					s.put(x, 0, z, Blocks.FARMLAND.defaultBlockState().setValue(BlockStateProperties.MOISTURE, 7));
					int age = Math.floorMod(x * 7 + z * 3, 4);
					s.put(x, 1, z, ZFighterItems.HERMIT_BEANS.defaultBlockState().setValue(ZFighterItems.HermitBeansBlock.AGE, age));
				}
			}
		}
		for (int[] corner : new int[][] {{9, 9}, {15, 9}, {9, 15}, {15, 15}}) {
			s.fill(corner[0], 1, corner[1], corner[0], 2, corner[1], Blocks.QUARTZ_PILLAR.defaultBlockState());
			s.put(corner[0], 3, corner[1], Blocks.LANTERN.defaultBlockState());
		}
		s.put(12, 1, 9, with(Blocks.QUARTZ_STAIRS.defaultBlockState(), BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH));
		s.put(12, 1, 15, with(Blocks.QUARTZ_STAIRS.defaultBlockState(), BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
	}

	/** The sparring ring, south west on the edge: a raised square (red border, white mat), a post at each corner, a rope between them. */
	private static void ring(Site s) {
		int x0 = -16, x1 = -10, z0 = 9, z1 = 15;
		for (int x = x0; x <= x1; x++) {
			for (int z = z0; z <= z1; z++) {
				boolean border = x == x0 || x == x1 || z == z0 || z == z1;
				s.put(x, 1, z, border ? named("red_terracotta") : named("white_terracotta"));
			}
		}
		s.put(-13, 1, z0 - 1, facing(Blocks.QUARTZ_STAIRS.defaultBlockState(), Direction.SOUTH));
		for (int[] post : new int[][] {{x0, z0}, {x1, z0}, {x0, z1}, {x1, z1}}) {
			s.fill(post[0], 2, post[1], post[0], 4, post[1], Blocks.QUARTZ_PILLAR.defaultBlockState());
			s.put(post[0], 5, post[1], Blocks.LANTERN.defaultBlockState());
		}
		for (int x = x0 + 1; x < x1; x++) {
			if (x != -13) {
				s.join(x, 3, z0, Blocks.CRIMSON_FENCE.defaultBlockState());
			}
			s.join(x, 3, z1, Blocks.CRIMSON_FENCE.defaultBlockState());
		}
		for (int z = z0 + 1; z < z1; z++) {
			s.join(x0, 3, z, Blocks.CRIMSON_FENCE.defaultBlockState());
			s.join(x1, 3, z, Blocks.CRIMSON_FENCE.defaultBlockState());
		}
		s.put(-8, 1, 12, ZFighterItems.TRAINING_POST.defaultBlockState());
	}

	/** The gravity room, north west: a walled room with a Gravity Machine in it, set small (5 blocks) and unfuelled. */
	private static void gravityRoom(Site s) {
		room(s, -14, -15, -8, -9, 4, Blocks.SMOOTH_QUARTZ.defaultBlockState(), Blocks.SMOOTH_QUARTZ.defaultBlockState());
		s.fill(-11, 1, -9, -11, 2, -9, air());
		s.fill(-14, 2, -12, -14, 3, -12, Blocks.GLASS.defaultBlockState());
		s.fill(-8, 2, -12, -8, 3, -12, Blocks.GLASS.defaultBlockState());
		BlockPos machine = s.at(-11, 1, -12);
		if (s.get(-11, 1, -12).isAir() && ZFighterGravityMachine.BLOCK != null) {
			s.put(-11, 1, -12, ZFighterGravityMachine.BLOCK.defaultBlockState());
			ZFighterGravityMachine.setUp(s.level, machine, 0, 0);
		}
		s.put(-13, 1, -10, Blocks.LANTERN.defaultBlockState());
		s.put(-9, 1, -14, Blocks.LANTERN.defaultBlockState());
	}

	/** The seeing pool, north east: a prismarine rim round five by five of water over a dark floor with a sea lantern at its heart. */
	private static void pool(Site s) {
		for (int x = POOL_X0; x <= POOL_X1; x++) {
			for (int z = POOL_Z0; z <= POOL_Z1; z++) {
				boolean rim = x == POOL_X0 || x == POOL_X1 || z == POOL_Z0 || z == POOL_Z1;
				if (rim) {
					s.put(x, 0, z, Blocks.PRISMARINE_BRICKS.defaultBlockState());
				} else {
					s.put(x, -1, z, x == 11 && z == -12 ? Blocks.SEA_LANTERN.defaultBlockState() : Blocks.DARK_PRISMARINE.defaultBlockState());
					s.put(x, 0, z, Blocks.WATER.defaultBlockState());
				}
			}
		}
		for (int[] corner : new int[][] {{POOL_X0, POOL_Z0}, {POOL_X1, POOL_Z0}, {POOL_X0, POOL_Z1}, {POOL_X1, POOL_Z1}}) {
			s.fill(corner[0], 1, corner[1], corner[0], 2, corner[1], Blocks.PRISMARINE_BRICKS.defaultBlockState());
			s.put(corner[0], 3, corner[1], Blocks.SEA_LANTERN.defaultBlockState());
		}
	}

	/** The god ritual circle's sign and two lamps on the south side; the circle itself is {@link ZFighterGodRitual}'s. */
	private static void circle(Site s) {
		s.put(-7, 1, ZFighterGodRitual.CIRCLE_DZ, Blocks.QUARTZ_PILLAR.defaultBlockState());
		s.put(-7, 2, ZFighterGodRitual.CIRCLE_DZ, Blocks.SOUL_LANTERN.defaultBlockState());
		s.put(7, 1, ZFighterGodRitual.CIRCLE_DZ, Blocks.QUARTZ_PILLAR.defaultBlockState());
		s.put(7, 2, ZFighterGodRitual.CIRCLE_DZ, Blocks.SOUL_LANTERN.defaultBlockState());
		ZFighterGodRitual.paintAt(s.level, new Vec3(s.cx + 0.5, s.y + 1.0, s.cz + ZFighterGodRitual.CIRCLE_DZ + 0.5));
	}

	// ---------------------------------------------------------------- every second

	private static boolean near(Player player, BlockPos spot, double distance) {
		double dx = player.getX() - spot.getX() - 0.5;
		double dz = player.getZ() - spot.getZ() - 0.5;
		return dx * dx + dz * dz <= distance * distance;
	}

	private static void tick(MinecraftServer server) {
		ServerLevel overworld = server.overworld();
		long now = overworld.getGameTime();
		if (now % 20 != 0) {
			return;
		}
		ZFighterSites.Data data = overworld.getAttached(ZFighterSites.DATA);
		if (data == null || !data.towerBuilt() || data.tower().isEmpty()) {
			return;
		}
		BlockPos tower = data.tower().get();
		boolean old = overworld.getAttachedOrElse(BUILT, 0) < VERSION;
		for (ServerPlayer player : overworld.players()) {
			if (player.isSpectator()) {
				continue;
			}
			if (old && near(player, tower, UPGRADE_NEAR)) {
				build(overworld, tower.getX(), tower.getZ(), false);
				old = false;
				JaymosPowers.LOGGER.info("The Lookout over {} {} was upgraded to the bigger one.", tower.getX(), tower.getZ());
			}
			if (!old && inPool(tower, player.getX(), player.getY(), player.getZ())) {
				Long last = PINGED.get(player.getUUID());
				if (last == null || now - last >= POOL_EVERY) {
					PINGED.put(player.getUUID(), now);
					showOrbs(player, tower);
				}
			}
		}
	}

	/** How far above the floor layer the Lookout's buildings reach (blocks): what counts as standing on the platform. */
	static final int PLATFORM_HEADROOM = 10;

	/** (0.18.51) Whether {@code pos} is on the Lookout's platform: over its disc, from the floor up to the tops of its buildings. */
	public static boolean onPlatform(ServerLevel level, BlockPos pos) {
		if (level.dimension() != net.minecraft.world.level.Level.OVERWORLD) {
			return false;
		}
		ZFighterSites.Data data = level.getAttached(ZFighterSites.DATA);
		if (data == null || !data.towerBuilt() || data.tower().isEmpty()) {
			return false;
		}
		BlockPos tower = data.tower().get();
		double dx = pos.getX() - tower.getX(), dz = pos.getZ() - tower.getZ();
		return dx * dx + dz * dz <= (double)RADIUS * RADIUS && pos.getY() >= ZFighterSites.LOOKOUT_Y && pos.getY() <= ZFighterSites.LOOKOUT_Y + PLATFORM_HEADROOM;
	}

	/** Whether the point stands in the pool's water (feet at the water's depth). */
	private static boolean inPool(BlockPos tower, double x, double y, double z) {
		double dx = x - tower.getX() - 0.5, dz = z - tower.getZ() - 0.5;
		return dx > POOL_X0 + 1 - 0.5 && dx < POOL_X1 - 0.5 && dz > POOL_Z0 + 1 - 0.5 && dz < POOL_Z1 - 0.5
			&& y >= ZFighterSites.LOOKOUT_Y - 0.5 && y <= ZFighterSites.LOOKOUT_Y + 0.9;
	}

	/** The pool shows the nearest orbs still in the world: a line each (direction, distance) and a trail of light pointing to it. */
	static void showOrbs(ServerPlayer player, BlockPos tower) {
		MinecraftServer server = player.level().getServer();
		ServerLevel overworld = server.overworld();
		ZFighterSites.Data data = ZFighterSites.data(server);
		long now = overworld.getGameTime();
		if (now < data.restUntil()) {
			long days = (data.restUntil() - now + 23999L) / 24000L;
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.zfighter_radar_stone", days).withStyle(ChatFormatting.GRAY));
			return;
		}
		List<double[]> orbs = new ArrayList<>();
		for (int i = 0; i < ZFighterSites.ORBS; i++) {
			if (!ZFighterSites.holdsOrb(overworld, data, i)) {
				continue;
			}
			BlockPos at = data.shrines().get(i);
			double dx = at.getX() + 0.5 - player.getX(), dz = at.getZ() + 0.5 - player.getZ();
			orbs.add(new double[] {i, dx, dz, Math.sqrt(dx * dx + dz * dz)});
		}
		orbs.sort((a, b) -> Double.compare(a[3], b[3]));
		if (orbs.isEmpty()) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.zfighter_pool_none").withStyle(ChatFormatting.GRAY));
			return;
		}
		MutableComponent line = Component.empty();
		for (int n = 0; n < Math.min(POOL_SHOWN, orbs.size()); n++) {
			double[] orb = orbs.get(n);
			float yaw = (float)(Mth.atan2(-orb[1], orb[2]) * Mth.RAD_TO_DEG);
			int arrow = Math.floorMod(Math.round(Mth.wrapDegrees(yaw - player.getYRot()) / 45.0F), 8);
			if (n > 0) {
				line.append(Component.literal("   ").withStyle(ChatFormatting.DARK_GRAY));
			}
			line.append(Component.translatable("message.jaymospowers.zfighter_pool_orb", (int)orb[0] + 1, ARROWS[arrow], (int)orb[3])
				.withStyle(ChatFormatting.AQUA));
			double length = Math.max(1.0E-3, orb[3]);
			for (double t = 1.5; t <= TRAIL; t += TRAIL_STEP) {
				player.level().sendParticles(player, ParticleTypes.END_ROD, false, false, player.getX() + orb[1] / length * t, player.getY() + 1.0 + 0.25 * n,
					player.getZ() + orb[2] / length * t, 1, 0.0, 0.0, 0.0, 0.0);
			}
		}
		player.sendOverlayMessage(line);
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.8F, 1.3F);
	}

	// ---------------------------------------------------------------- right clicks

	private static InteractionResult useBlock(Player player, net.minecraft.world.level.Level level, InteractionHand hand,
		net.minecraft.world.phys.BlockHitResult hit) {
		if (hand != InteractionHand.MAIN_HAND || !(player instanceof ServerPlayer server) || !(level instanceof ServerLevel serverLevel)
			|| serverLevel != serverLevel.getServer().overworld()) {
			return InteractionResult.PASS;
		}
		ZFighterSites.Data data = ZFighterSites.data(serverLevel.getServer());
		if (!data.towerBuilt() || data.tower().isEmpty()) {
			return InteractionResult.PASS;
		}
		BlockPos tower = data.tower().get();
		BlockPos at = hit.getBlockPos();
		int dx = at.getX() - tower.getX(), dy = at.getY() - ZFighterSites.LOOKOUT_Y, dz = at.getZ() - tower.getZ();
		if (dy == 1 && dx == LECTERN_X && dz >= LECTERN_Z0 && dz <= LECTERN_Z1 && level.getBlockState(at).is(Blocks.LECTERN)) {
			library(server);
			return InteractionResult.SUCCESS;
		}
		if (dy >= -1 && dy <= 0 && dx >= POOL_X0 && dx <= POOL_X1 && dz >= POOL_Z0 && dz <= POOL_Z1) {
			showOrbs(server, tower);
			return InteractionResult.SUCCESS;
		}
		return InteractionResult.PASS;
	}

	/** A library lectern: the lesson a good master would give, bought back for {@link #LIBRARY_COST} TP (needs a master's teaching before). */
	private static void library(ServerPlayer player) {
		if (!Powers.has(player, ZFighterPower.INSTANCE)) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.zfighter_library_no_spark").withStyle(ChatFormatting.GRAY));
			return;
		}
		if (ZFighterGodRitual.lessons(player) < 1) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.zfighter_library_untaught").withStyle(ChatFormatting.GRAY));
			return;
		}
		ZFighterTraining.Data data = ZFighterTraining.of(player);
		if (data.tp() < LIBRARY_COST) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.zfighter_library_poor", LIBRARY_COST).withStyle(ChatFormatting.GRAY));
			return;
		}
		player.setAttached(ZFighterTraining.DATA, data.withTp(data.tp() - LIBRARY_COST));
		Component lesson = ZFighterMasters.lesson(player, false);
		player.sendSystemMessage(Component.translatable("message.jaymospowers.zfighter_library", lesson).withStyle(ChatFormatting.AQUA));
		ServerLevel level = player.level();
		level.sendParticles(ParticleTypes.ENCHANT, player.getX(), player.getY() + 1.5, player.getZ(), 30, 0.5, 0.5, 0.5, 0.5);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1.0F, 0.9F);
	}
}
