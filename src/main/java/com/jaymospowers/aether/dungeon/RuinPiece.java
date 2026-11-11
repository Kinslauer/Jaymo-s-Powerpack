package com.jaymospowers.aether.dungeon;

import com.jaymospowers.JaymosPowers;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * One block of the Sentry Ruins, built in code: the boss room, a room (six kinds), a tunnel between two, the lobby, a flight of
 * stairs, or the ruin on the surface. {@link RuinLayout} decides where each goes; this builds it, a chunk at a time.
 */
public final class RuinPiece extends StructurePiece {
	public enum Kind {
		BOSS, TUNNEL, CHEST, LOUNGE, DEPOSIT, COLD, TESTING, WORKSHOP, FORGE, LOBBY, STAIRS, RUIN
	}

	// ---- sizes (the notes): rooms 26 by 26 and 10 tall, a deposit 12, a forge 18; tunnels 6 by 9 by 6; the boss room 22 by 19 by 28 inside ----
	public static final int ROOM = 26, ROOM_H = 10, DEPOSIT_H = 12, FORGE_H = 18;
	public static final int BOSS_W = 24, BOSS_L = 30, BOSS_H = 21, ALCOVE = 5;
	public static final int TUNNEL = 6, TUNNEL_H = 9;
	public static final int LOBBY_L = 16, LOBBY_H = 10;
	/** A flight of stairs: 6 wide, 8 long, 12 tall, rising 6. */
	public static final int STAIR_W = 6, STAIR_L = 8, STAIR_H = 12, RISE = 6;
	public static final int RUIN = 10, RUIN_H = 13;
	// ---- tuning ----
	/** The doors are 4 wide and 7 high (4 in the ruin). */
	public static final int DOOR_W = 4, DOOR_H = 7, RUIN_DOOR_H = 4;
	/** A sentry crate is a mimic this often (the notes: 30 percent). */
	public static final float MIMIC_CHANCE = 0.30F;
	/** Traps hidden in the boss room's floor; spawners in its walls. */
	public static final int BOSS_TRAPS = 18;

	public static StructurePieceType TYPE;

	private final Kind kind;
	private final Direction facing;
	private final long seed;
	/** The sides (world directions, as a bit mask of horizontal indices) that have a door. */
	private final int doors;

	public RuinPiece(Kind kind, BoundingBox box, Direction facing, long seed, int doors) {
		super(TYPE, 0, box);
		this.kind = kind;
		this.facing = facing;
		this.seed = seed;
		this.doors = doors;
		this.setOrientation(facing);
	}

	public RuinPiece(CompoundTag tag) {
		super(TYPE, tag);
		this.kind = Kind.valueOf(tag.getStringOr("kind", "CHEST"));
		this.facing = Direction.from2DDataValue(tag.getIntOr("facing", 0));
		this.seed = tag.getLongOr("seed", 0L);
		this.doors = tag.getIntOr("doors", 0);
	}

	public Kind kind() {
		return this.kind;
	}

	public static int doorBit(Direction direction) {
		return 1 << direction.get2DDataValue();
	}

	@Override
	protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
		tag.putString("kind", this.kind.name());
		tag.putInt("facing", this.facing.get2DDataValue());
		tag.putLong("seed", this.seed);
		tag.putInt("doors", this.doors);
	}

	private static ResourceKey<LootTable> loot(String name) {
		return ResourceKey.create(Registries.LOOT_TABLE, JaymosPowers.id("chests/sentry_ruins/" + name));
	}

	@Override
	public void postProcess(WorldGenLevel level, StructureManager structures, ChunkGenerator generator, RandomSource random, BoundingBox chunkBox,
		ChunkPos chunkPos, BlockPos pivot) {
		RuinSite s = new RuinSite(level, chunkBox, this.boundingBox, this.facing, this.seed);
		switch (this.kind) {
			case BOSS -> this.boss(s);
			case TUNNEL -> this.tunnel(s);
			case LOBBY -> this.lobby(s);
			case STAIRS -> this.stairs(s);
			case RUIN -> this.ruin(s);
			default -> this.room(s);
		}
	}

	// ---- common ----

	private static BlockState wall(RuinSite s, int u, int y, int v) {
		return RuinKit.wall(s.rnd(u, y, v, 1), s.rnd(u, y, v, 2));
	}

	/** The floor of a room: bricks and shale with a few flagstones; the palettes of the cold store and the testing room differ. */
	private BlockState floorBlock(RuinSite s, int u, int v) {
		float r = s.rnd(u, 0, v, 3);
		switch (this.kind) {
			case COLD:
				return RuinKit.state(r < 0.3F ? "arctic_packed_ice" : r < 0.7F ? "icestone_bricks" : "undershale_bricks");
			case TESTING:
				return RuinKit.state((u + v) % 2 == 0 ? "sentry_tile" : "undershale_bricks");
			case WORKSHOP:
			case FORGE:
				return RuinKit.state(r < 0.3F ? "sentry_tile" : r < 0.5F ? "undershale" : "undershale_bricks");
			default:
				return RuinKit.state(r < 0.12F ? "sentry_flagstones" : r < 0.37F ? "undershale" : "undershale_bricks");
		}
	}

	private BlockState shellBlock(RuinSite s, int u, int y, int v) {
		if (y == s.box.minY()) {
			return this.floorBlock(s, u, v);
		}
		if (this.kind == Kind.COLD && s.rnd(u, y, v, 4) < 0.25F) {
			return RuinKit.state("icestone_bricks");
		}
		return wall(s, u, y, v);
	}

	/** Cuts the doors of the sides in the mask out of the shell (the wall cells at the middle of each side). */
	private void doors(RuinSite s, int height) {
		for (int k = 0; k < 4; k++) {
			if ((this.doors & doorBit(s.dir(k))) == 0) {
				continue;
			}
			boolean ends = k == 0 || k == 2;
			int span = ends ? s.width : s.length;
			int from = span / 2 - DOOR_W / 2;
			for (int i = from; i < from + DOOR_W; i++) {
				int u = ends ? i : k == 1 ? s.width - 1 : 0;
				int v = ends ? (k == 0 ? s.length - 1 : 0) : i;
				for (int y = s.floor; y < s.floor + height; y++) {
					s.set(u, y, v, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
				}
			}
		}
	}

	private static void air(RuinSite s, int u0, int y0, int v0, int u1, int y1, int v1) {
		s.fill(u0, y0, v0, u1, y1, v1, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
	}

	private static void trap(RuinSite s, int u, int v) {
		s.set(u, s.floor - 1, v, DungeonBlocks.SENTRY_TRAP.defaultBlockState());
	}

	private static void spawner(RuinSite s, int u, int y, int v) {
		s.set(u, y, v, DungeonBlocks.SENTRY_SPAWNER.defaultBlockState());
	}

	private static void light(RuinSite s, int u, int y, int v) {
		s.set(u, y, v, RuinKit.state("sentry_lightstone"));
	}

	private static void pillar(RuinSite s, int u, int v, int top) {
		s.fill(u, s.floor, v, u, top, v, RuinKit.state("sentry_pillar"));
	}

	private void traps(RuinSite s, int count, int salt) {
		java.util.Random r = s.random(salt);
		for (int i = 0; i < count; i++) {
			trap(s, 2 + r.nextInt(s.width - 4), 2 + r.nextInt(s.length - 4));
		}
	}

	/** Sentry spawners in the walls at floor height + 1, {@code at} cells along each side. */
	private void wallSpawners(RuinSite s, int... at) {
		for (int p : at) {
			spawner(s, 0, s.floor + 1, p);
			spawner(s, s.width - 1, s.floor + 1, p);
			spawner(s, p, s.floor + 1, 0);
			spawner(s, p, s.floor + 1, s.length - 1);
		}
	}

	private void ceilingLights(RuinSite s, int... at) {
		for (int u : at) {
			for (int v : at) {
				light(s, u, s.box.maxY(), v);
			}
		}
	}

	/** Sentry crates against the walls (never in the door spans), some of them the rare kind, 3 in 10 a mimic. */
	private void crates(RuinSite s, int count, String table, float rare, int salt) {
		java.util.Random r = s.random(salt);
		Set<Long> used = new HashSet<>();
		for (int i = 0; i < count; i++) {
			int k = r.nextInt(4), span = k == 0 || k == 2 ? s.width : s.length, t = 3 + r.nextInt(span - 6);
			if (t >= span / 2 - 4 && t <= span / 2 + 3) {
				continue;
			}
			int u = k == 1 ? s.width - 2 : k == 3 ? 1 : t;
			int v = k == 0 ? s.length - 2 : k == 2 ? 1 : t;
			int face = k == 0 ? 2 : k == 2 ? 0 : k == 1 ? 3 : 1;
			if (!used.add(((long)u << 32) | v)) {
				continue;
			}
			boolean rareOne = s.rnd(u, s.floor, v, 78) < rare;
			s.crate(u, s.floor, v, face, loot(rareOne ? "rare" : table), s.rnd(u, s.floor, v, 77) < MIMIC_CHANCE);
		}
	}

	private void golems(RuinSite s, int count, int salt) {
		java.util.Random r = s.random(salt);
		for (int i = 0; i < count; i++) {
			s.mob(DungeonMobs.SENTRY_GOLEM, 4 + r.nextInt(s.width - 8), s.floor, 4 + r.nextInt(s.length - 8), r.nextInt(360));
		}
	}

	// ---- the boss room ----

	private void boss(RuinSite s) {
		int f = s.floor;
		int top = s.box.maxY();
		// the room (u 0..23, v 0..29) and the vault behind it (u 8..15, v 30..34); every block of the shell is locked
		RuinSite.Pick shell = (u, y, v) -> RuinKit.locked(wall(s, u, y, v));
		s.fill(0, f - 1, 0, BOSS_W - 1, top, BOSS_L - 1, shell);
		s.fill(8, f - 1, BOSS_L, 15, f + 5, BOSS_L + ALCOVE - 1, shell);
		air(s, 1, f, 1, BOSS_W - 2, top - 1, BOSS_L - 2);
		air(s, 9, f, BOSS_L, 14, f + 4, BOSS_L + ALCOVE - 2);
		// the way in is a boss doorway (open: nothing there), the way to the vault a treasure doorway
		s.fill(10, f, 0, 13, f + DOOR_H - 1, 0, DungeonBlocks.BOSS_DOORWAY.defaultBlockState());
		s.fill(9, f, BOSS_L - 1, 14, f + 4, BOSS_L - 1, DungeonBlocks.TREASURE_DOORWAY.defaultBlockState());
		// light in the walls and the ceiling
		BlockState glow = RuinKit.state("locked_sentry_lightstone");
		for (int v : new int[] {4, 10, 16, 22, 27}) {
			for (int y : new int[] {f + 8, f + 14}) {
				s.set(0, y, v, glow);
				s.set(BOSS_W - 1, y, v, glow);
			}
		}
		for (int u : new int[] {6, 17}) {
			for (int v : new int[] {6, 14, 22}) {
				s.set(u, top, v, glow);
			}
		}
		s.set(8, f + 2, BOSS_L + 1, glow);
		s.set(15, f + 2, BOSS_L + 1, glow);
		// pillars the Slider can break, spawners in the walls, traps in the floor
		for (int v : new int[] {5, 11, 17, 23}) {
			pillar(s, 3, v, f + BOSS_H - 3);
			pillar(s, BOSS_W - 4, v, f + BOSS_H - 3);
		}
		for (int v : new int[] {6, 14, 22}) {
			spawner(s, 0, f + 1, v);
			spawner(s, BOSS_W - 1, f + 1, v);
		}
		java.util.Random r = s.random(5);
		for (int i = 0; i < BOSS_TRAPS; i++) {
			trap(s, 2 + r.nextInt(BOSS_W - 4), 3 + r.nextInt(BOSS_L - 6));
		}
		// the boss's crates in the vault, facing the room
		s.crate(11, f, BOSS_L + 3, 2, loot("boss"), false);
		s.crate(12, f, BOSS_L + 3, 2, loot("boss"), false);
		// the Slider, asleep in the middle of the room
		BoundingBox room = BoundingBox.fromCorners(new BlockPos(s.x(1, 1), f, s.z(1, 1)), new BlockPos(s.x(BOSS_W - 2, BOSS_L - 2), f + BOSS_H - 3,
			s.z(BOSS_W - 2, BOSS_L - 2)));
		double cx = (room.minX() + room.maxX() + 1) / 2.0, cz = (room.minZ() + room.maxZ() + 1) / 2.0;
		s.spawn(DungeonMobs.SLIDER, cx, room.minY() + 2.0, cz, s.facing.toYRot(), slider -> slider.setRoom(room));
	}

	// ---- tunnels ----

	private void tunnel(RuinSite s) {
		int f = s.floor;
		s.fill(0, f - 1, 0, s.width - 1, s.box.maxY(), s.length - 1, (u, y, v) -> y == f - 1 ? this.floorBlock(s, u, v) : wall(s, u, y, v));
		air(s, 1, f, 0, s.width - 2, f + DOOR_H - 1, s.length - 1);
		light(s, 2, s.box.maxY(), 2);
		if (s.rnd(0, 0, 0, 9) < 0.7F) {
			trap(s, 2, 3);
		}
		spawner(s, 0, f + 1, 3);
		spawner(s, s.width - 1, f + 1, 2);
	}

	// ---- rooms ----

	private void room(RuinSite s) {
		int f = s.floor;
		int top = s.box.maxY();
		s.fill(0, f - 1, 0, s.width - 1, top, s.length - 1, (u, y, v) -> this.shellBlock(s, u, y, v));
		air(s, 1, f, 1, s.width - 2, top - 1, s.length - 2);
		this.doors(s, DOOR_H);
		this.wallSpawners(s, 5, 20);
		this.ceilingLights(s, 5, 12, 20);
		for (int u : new int[] {7, 18}) {
			for (int v : new int[] {7, 18}) {
				pillar(s, u, v, top - 1);
			}
		}
		switch (this.kind) {
			case CHEST -> {
				this.traps(s, 6, 11);
				// a raised middle with the best crate on it
				s.fill(10, f, 10, 15, f, 15, RuinKit.state("sentry_flagstones"));
				s.crate(12, f + 1, 12, 0, loot("rare"), s.rnd(12, f, 12, 77) < MIMIC_CHANCE);
				s.crate(13, f + 1, 13, 2, loot("rare"), false);
				this.crates(s, 5, "common", 0.2F, 12);
			}
			case LOUNGE -> {
				this.traps(s, 4, 11);
				this.seats(s, 3, 3);
				this.seats(s, 16, 16);
				this.crates(s, 3, "common", 0.25F, 12);
				this.golems(s, (int)(s.rnd(0, 0, 0, 14) * 2), 13);
			}
			case DEPOSIT -> {
				this.traps(s, 6, 11);
				this.piles(s, 11, "undershale", "holystone", "agiosite", "undershale_zanite_ore", "undershale_ambrosium_ore");
				this.crates(s, 4, "deposit", 0.1F, 12);
			}
			case COLD -> {
				this.traps(s, 6, 11);
				this.piles(s, 8, "icestone", "arctic_packed_ice", "icestone_bricks", "arctic_snow_block", "icestone");
				this.snow(s);
				this.crates(s, 4, "cold", 0.15F, 12);
			}
			case TESTING -> {
				this.traps(s, 18, 11);
				this.crates(s, 2, "common", 0.25F, 12);
				this.golems(s, 1 + (int)(s.rnd(0, 0, 0, 14) * 2), 13);
			}
			case WORKSHOP -> {
				this.traps(s, 8, 11);
				this.stations(s, 14);
				this.lanterns(s, 4);
				this.crates(s, 3, "common", 0.25F, 12);
				this.golems(s, 1, 13);
			}
			case FORGE -> {
				this.traps(s, 8, 11);
				this.forge(s);
				this.lanterns(s, 4);
				this.crates(s, 3, "common", 0.6F, 12);
				this.golems(s, 1 + (int)(s.rnd(0, 0, 0, 14) * 2), 13);
			}
			default -> {
			}
		}
	}

	/** A bench of stair seats facing a slab table. */
	private void seats(RuinSite s, int u, int v) {
		int f = s.floor;
		s.set(u + 1, f, v + 1, RuinKit.state("undershale_brick_slab"));
		s.set(u + 2, f, v + 1, RuinKit.state("undershale_brick_slab"));
		for (int i = 0; i < 4; i++) {
			BlockState seat = RuinKit.state("undershale_brick_stairs").setValue(BlockStateProperties.HORIZONTAL_FACING, s.dir(i));
			int du = i == 1 ? 3 : i == 3 ? 0 : 1 + (i == 0 ? 0 : 1);
			int dv = i == 0 ? 0 : i == 2 ? 2 : 1;
			s.set(u + du, f, v + dv, seat);
		}
	}

	/** Mounds of block, two to three high, scattered over the floor. */
	private void piles(RuinSite s, int count, String... blocks) {
		java.util.Random r = s.random(21);
		for (int i = 0; i < count; i++) {
			int cu = 3 + r.nextInt(s.width - 6), cv = 3 + r.nextInt(s.length - 6);
			int height = 2 + r.nextInt(2);
			for (int du = -2; du <= 2; du++) {
				for (int dv = -2; dv <= 2; dv++) {
					int reach = height - (Math.abs(du) + Math.abs(dv)) / 2;
					for (int y = 0; y < reach; y++) {
						String id = blocks[(int)(s.rnd(cu + du, y, cv + dv, 22) * blocks.length)];
						s.set(cu + du, s.floor + y, cv + dv, RuinKit.state(id));
					}
				}
			}
		}
	}

	/** A dusting of snow over the cold store's floor. */
	private void snow(RuinSite s) {
		s.fill(1, s.floor, 1, s.width - 2, s.floor, s.length - 2, (u, y, v) -> s.get(u, y, v).isAir() && s.rnd(u, y, v, 23) < 0.3F
			? RuinKit.state("arctic_snow") : null);
	}

	/** Workbenches along the walls. */
	private void stations(RuinSite s, int count) {
		String[] ids = {"smithing_table", "anvil", "grindstone", "crafting_table", "fletching_table", "cauldron", "blast_furnace", "loom"};
		java.util.Random r = s.random(31);
		for (int i = 0; i < count; i++) {
			int k = r.nextInt(4), span = k == 0 || k == 2 ? s.width : s.length, t = 3 + r.nextInt(span - 6);
			if (t >= span / 2 - 4 && t <= span / 2 + 3) {
				continue;
			}
			int u = k == 1 ? s.width - 2 : k == 3 ? 1 : t;
			int v = k == 0 ? s.length - 2 : k == 2 ? 1 : t;
			s.set(u, s.floor, v, RuinKit.vstate(ids[r.nextInt(ids.length)]));
		}
	}

	/** Lanterns hanging on chains from the ceiling. */
	private void lanterns(RuinSite s, int count) {
		java.util.Random r = s.random(41);
		int top = s.box.maxY();
		for (int i = 0; i < count; i++) {
			int u = 3 + r.nextInt(s.width - 6), v = 3 + r.nextInt(s.length - 6), drop = 2 + r.nextInt(3);
			for (int y = 1; y <= drop; y++) {
				s.set(u, top - y, v, RuinKit.vstate("iron_chain"));
			}
			s.set(u, top - drop - 1, v, RuinKit.vstate("lantern").setValue(net.minecraft.world.level.block.LanternBlock.HANGING, true));
		}
	}

	/** The forge's hearth: a low dais with furnaces round a bed of magma and bars. */
	private void forge(RuinSite s) {
		int f = s.floor;
		s.fill(8, f, 8, 17, f, 17, RuinKit.state("undershale_bricks"));
		s.fill(11, f, 11, 14, f, 14, RuinKit.vstate("magma_block"));
		s.fill(11, f + 1, 11, 14, f + 3, 14, (u, y, v) -> (u == 11 || u == 14) && (v == 11 || v == 14) ? RuinKit.vstate("iron_bars") : null);
		for (int i = 0; i < 4; i++) {
			s.set(9 + i * 2, f + 1, 8, RuinKit.vstate("blast_furnace"));
			s.set(9 + i * 2, f + 1, 17, RuinKit.vstate("blast_furnace"));
		}
		s.set(8, f + 1, 12, RuinKit.vstate("anvil"));
		s.set(17, f + 1, 13, RuinKit.vstate("anvil"));
	}

	// ---- the lobby ----

	private void lobby(RuinSite s) {
		int f = s.floor;
		int top = s.box.maxY();
		s.fill(0, f - 1, 0, s.width - 1, top, s.length - 1, (u, y, v) -> this.shellBlock(s, u, y, v));
		air(s, 1, f, 1, s.width - 2, top - 1, s.length - 2);
		this.doors(s, DOOR_H);
		for (int u : new int[] {6, 19}) {
			for (int v : new int[] {4, 11}) {
				pillar(s, u, v, top - 1);
			}
		}
		for (int u : new int[] {5, 12, 20}) {
			for (int v : new int[] {4, 11}) {
				light(s, u, top, v);
			}
		}
		for (int p : new int[] {5, 20}) {
			spawner(s, p, f + 1, 0);
			spawner(s, p, f + 1, s.length - 1);
		}
		this.traps(s, 3, 11);
		this.crates(s, 1, "common", 0.2F, 12);
	}

	// ---- a flight of stairs ----

	/** The block level a cell's floor stands on: 0 is the landing, 1 to 6 the steps, 7 the landing above. */
	private static int step(int floor, int v) {
		return v == 0 ? floor - 1 : v <= RISE ? floor + v - 2 : floor + RISE - 1;
	}

	private void stairs(RuinSite s) {
		int f = s.floor;
		BlockState steps = RuinKit.state("undershale_brick_stairs").setValue(BlockStateProperties.HORIZONTAL_FACING, s.facing);
		for (int v = 0; v < STAIR_L; v++) {
			int solid = step(f, v);
			boolean stair = v >= 1 && v <= RISE;
			int top = stair ? solid + 1 : solid;
			int vv = v;
			s.fill(0, s.box.minY(), v, STAIR_W - 1, top, v, (u, y, w) -> u == 0 || u == STAIR_W - 1 || y <= solid ? wall(s, u, y, vv) : null);
			if (stair) {
				s.fill(1, top, v, STAIR_W - 2, top, v, steps);
			}
			// four clear above the step, a ceiling over it
			air(s, 1, top + 1, v, STAIR_W - 2, top + 4, v);
			s.fill(0, top + 1, v, STAIR_W - 1, top + 5, v, (u, y, w) -> u == 0 || u == STAIR_W - 1 || y == top + 5 ? wall(s, u, y, vv) : null);
			if (v == 3 || v == 6) {
				light(s, 0, top + 2, v);
			}
		}
	}

	// ---- the ruin on the surface ----

	private void ruin(RuinSite s) {
		int f = s.floor;
		int top = s.box.maxY();
		s.fill(0, f - 1, 0, RUIN - 1, f - 1, RUIN - 1, RuinKit.state("undershale_bricks"));
		RuinSite.Pick stone = (u, y, v) -> {
			float r = s.rnd(u, y, v, 51);
			return RuinKit.state(r < 0.3F ? "mossy_holystone" : r < 0.7F ? "faded_holystone_bricks" : "holystone_bricks");
		};
		// the walls stand low and broken: whole at the foot, thinning toward the top
		s.fill(0, f, 0, RUIN - 1, top, RUIN - 1, (u, y, v) -> {
			boolean edge = u == 0 || v == 0 || u == RUIN - 1 || v == RUIN - 1;
			if (!edge) {
				return null;
			}
			boolean corner = (u == 0 || u == RUIN - 1) && (v == 0 || v == RUIN - 1);
			float keep = corner ? 0.95F - 0.07F * (y - f) : 0.95F - 0.13F * (y - f);
			return y - f <= 1 || s.rnd(u, y, v, 52) < keep ? (corner && y - f > 2 ? RuinKit.state("holystone_pillar") : stone.at(u, y, v)) : null;
		});
		// the inside is open to the sky: everything above the floor is cleared, up through the ground
		for (int u = 1; u < RUIN - 1; u++) {
			for (int v = 1; v < RUIN - 1; v++) {
				int x = s.x(u, v), z = s.z(u, v);
				if (!s.chunk.isInside(x, f, z)) {
					continue;
				}
				int ground = Math.min(s.level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z) + 1, f + 40);
				air(s, u, f, v, u, Math.max(top, ground), v);
			}
		}
		// the way down: the stairs come up at the middle of the back wall
		air(s, RUIN / 2 - 2, f, 0, RUIN / 2 + 1, f + RUIN_DOOR_H - 1, 0);
		s.crate(1, f, RUIN - 2, 2, loot("common"), s.rnd(1, f, RUIN - 2, 77) < MIMIC_CHANCE);
	}
}
