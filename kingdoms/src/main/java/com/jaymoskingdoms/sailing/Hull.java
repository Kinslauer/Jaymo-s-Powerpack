package com.jaymoskingdoms.sailing;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import java.util.ArrayDeque;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * (0.7.7) What a ship's blocks add up to, worked out once from its {@link Ship.Piece}s: where each piece is, how solid it
 * is, how heavy the whole ship is, how much water its hull keeps out at each height (its walls and the dry space they
 * close in), and from those how deep it sits, how long it is at the waterline, and how much sail it carries.
 */
public final class Hull {
	/** Weight of a block-full of each stuff, water being 1 (our own rough figures). */
	static final float CLOTH = 0.12F, WOOD = 0.5F, GLASS = 0.9F, STONE = 2.4F, METAL = 7.5F, GOLD = 19.0F;
	/** The least a block with no body (a torch, a button) still weighs, as a share of a full block. */
	static final float SPECK = 0.05F;
	/** A piece this solid (share of its cell) is a wall that keeps the water out. */
	static final float WALL = 0.5F;
	/** A ship may weigh at most this share of what its hull can hold up (the rest is freeboard). */
	static final float MOST_LOAD = 0.9F;
	/** (0.7.111) Wool hung in her rigging (not laid on a floor) catches the wind too: this many blocks of it make one sail's worth. */
	static final int CLOTH_PER_SAIL = 3;

	public final List<Ship.Piece> pieces;
	/** Each piece's index by its packed local position. */
	public final Long2IntOpenHashMap index = new Long2IntOpenHashMap();
	/** Each piece's collision box, as heights within its cell (lo > hi: none). */
	public final float[] lo, hi;
	public final int minX, minY, minZ, maxX, maxY, maxZ;
	/** Weight, and the water each layer (from the bottom, y = 0) keeps out. */
	public final float mass;
	public final float[] layer;
	/** The dry cells the hull's walls close in (local, packed). */
	public final long[] enclosed;
	private final it.unimi.dsi.fastutil.longs.LongOpenHashSet dry;
	/** How deep it sits (blocks below the waterline, from its bottom), and whether it floats at all. */
	public final float draft;
	public final boolean floats;
	/** Its length at the waterline (blocks, along local z), its sails, and (0.7.18) its oars. */
	public final int waterline, sails, oars;
	/** (0.7.18) Its guns' pieces, and its oars'; (0.7.28) the pieces that give light (torches, lanterns, glowstone...). */
	public final int[] cannons, oarPieces, lights;
	/** The pieces on its outside (walls, bottom and ends), which run into land first. */
	public final int[] skin;
	/** The helm's piece (-1: none). */
	public final int helm;
	/** Its reach from its own origin (blocks, across). */
	public final double radius;

	Hull(List<Ship.Piece> pieces) {
		this.pieces = pieces;
		this.index.defaultReturnValue(-1);
		int n = pieces.size();
		this.lo = new float[n];
		this.hi = new float[n];
		int x0 = Integer.MAX_VALUE, y0 = Integer.MAX_VALUE, z0 = Integer.MAX_VALUE, x1 = Integer.MIN_VALUE, y1 = Integer.MIN_VALUE, z1 = Integer.MIN_VALUE;
		int h = -1, sailCount = 0;
		IntArrayList guns = new IntArrayList(), oarList = new IntArrayList(), lightList = new IntArrayList();
		double r = 1.0;
		float[] fill = new float[n];
		float m = 0.0F;
		for (int i = 0; i < n; i++) {
			Ship.Piece p = pieces.get(i);
			this.index.put(BlockPos.asLong(p.x(), p.y(), p.z()), i);
			x0 = Math.min(x0, p.x());
			y0 = Math.min(y0, p.y());
			z0 = Math.min(z0, p.z());
			x1 = Math.max(x1, p.x());
			y1 = Math.max(y1, p.y());
			z1 = Math.max(z1, p.z());
			r = Math.max(r, Math.sqrt((double)p.x() * p.x() + (double)p.z() * p.z()) + 1.0);
			BlockState state = p.blockState();
			VoxelShape shape = state.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
			if (shape.isEmpty()) {
				this.lo[i] = 1.0F;
				this.hi[i] = 0.0F;
			} else {
				AABB b = shape.bounds();
				this.lo[i] = (float)Math.max(0.0, b.minY);
				this.hi[i] = (float)Math.min(1.5, b.maxY);
			}
			float volume = 0.0F;
			for (AABB box : shape.toAabbs()) {
				volume += (float)(box.getXsize() * box.getYsize() * Math.min(1.0, box.getZsize()));
			}
			fill[i] = Mth.clamp(volume, 0.0F, 1.0F);
			m += density(state) * Math.max(SPECK, fill[i]);
			if (state.getBlock() instanceof HelmBlock && h < 0) {
				h = i;
			}
			if (state.getBlock() instanceof SailBlock) {
				sailCount++;
			}
			if (state.getBlock() instanceof ShipCannonBlock) {
				guns.add(i);
			}
			if (state.getBlock() instanceof OarBlock) {
				oarList.add(i);
			}
			if (state.getLightEmission() > 0) {
				lightList.add(i);
			}
		}
		this.cannons = guns.toIntArray();
		this.oarPieces = oarList.toIntArray();
		this.lights = lightList.toIntArray();
		this.oars = this.oarPieces.length;
		this.minX = x0;
		this.minY = y0;
		this.minZ = z0;
		this.maxX = x1;
		this.maxY = y1;
		this.maxZ = z1;
		this.helm = h;
		// (0.7.111) wool sails (Jaki's ships', or anyone's): wool with nothing under it but more wool, or air
		int cloth = 0;
		for (int i = 0; i < n; i++) {
			Ship.Piece p = pieces.get(i);
			if (p.blockState().is(BlockTags.WOOL)) {
				int below = this.index.get(BlockPos.asLong(p.x(), p.y() - 1, p.z()));
				if (below < 0 || pieces.get(below).blockState().is(BlockTags.WOOL)) {
					cloth++;
				}
			}
		}
		this.sails = sailCount + cloth / CLOTH_PER_SAIL;
		this.radius = r;
		this.mass = m;
		// layer by layer: the walls, and the dry space they close in (what the water can't reach from outside)
		int w = x1 - x0 + 3, d = z1 - z0 + 3, height = y1 - y0 + 1;
		this.layer = new float[Math.max(0, height)];
		IntArrayList inside = new IntArrayList();
		java.util.ArrayList<Long> dry = new java.util.ArrayList<>();
		for (int y = y0; y <= y1; y++) {
			boolean[] wall = new boolean[w * d], wet = new boolean[w * d];
			float kept = 0.0F;
			for (int i = 0; i < n; i++) {
				Ship.Piece p = pieces.get(i);
				if (p.y() == y) {
					int c = (p.x() - x0 + 1) * d + (p.z() - z0 + 1);
					if (fill[i] >= WALL) {
						wall[c] = true;
						kept += 1.0F;
					} else {
						kept += fill[i];
					}
				}
			}
			ArrayDeque<Integer> open = new ArrayDeque<>();
			for (int a = 0; a < w; a++) {
				for (int b = 0; b < d; b++) {
					if ((a == 0 || b == 0 || a == w - 1 || b == d - 1) && !wall[a * d + b]) {
						wet[a * d + b] = true;
						open.add(a * d + b);
					}
				}
			}
			while (!open.isEmpty()) {
				int c = open.poll(), a = c / d, b = c % d;
				int[][] next = {{a + 1, b}, {a - 1, b}, {a, b + 1}, {a, b - 1}};
				for (int[] q : next) {
					if (q[0] >= 0 && q[1] >= 0 && q[0] < w && q[1] < d) {
						int k = q[0] * d + q[1];
						if (!wall[k] && !wet[k]) {
							wet[k] = true;
							open.add(k);
						}
					}
				}
			}
			for (int a = 0; a < w; a++) {
				for (int b = 0; b < d; b++) {
					int k = a * d + b;
					if (!wall[k] && !wet[k]) {
						int lx = a - 1 + x0, lz = b - 1 + z0;
						int piece = this.index.get(BlockPos.asLong(lx, y, lz));
						// a dry cell holding a slight piece (a torch, a carpet) keeps the rest of its water out too
						kept += piece >= 0 ? 1.0F - fill[piece] : 1.0F;
						dry.add(BlockPos.asLong(lx, y, lz));
					}
				}
			}
			this.layer[y - y0] = kept;
		}
		this.enclosed = dry.stream().mapToLong(Long::longValue).toArray();
		this.dry = new it.unimi.dsi.fastutil.longs.LongOpenHashSet(this.enclosed);
		// how deep it sits: down to where the water it keeps out weighs what it does
		float held = 0.0F, depth = height;
		boolean afloat = false;
		for (int y = 0; y < this.layer.length; y++) {
			if (held + this.layer[y] >= m) {
				depth = y + (this.layer[y] > 0.0F ? (m - held) / this.layer[y] : 0.0F);
				afloat = true;
				break;
			}
			held += this.layer[y];
		}
		float total = 0.0F;
		for (float k : this.layer) {
			total += k;
		}
		this.floats = afloat && m <= total * MOST_LOAD;
		this.draft = depth;
		// its length where it meets the water
		int line = Mth.clamp(Mth.floor(depth) + y0, y0, y1), zl = Integer.MAX_VALUE, zh = Integer.MIN_VALUE;
		for (Ship.Piece p : pieces) {
			if (p.y() == line || p.y() == y0) {
				zl = Math.min(zl, p.z());
				zh = Math.max(zh, p.z());
			}
		}
		this.waterline = zh >= zl ? zh - zl + 1 : 1;
		// its outside: any piece with open water or air beside or below it
		for (int i = 0; i < n; i++) {
			Ship.Piece p = pieces.get(i);
			if (this.lo[i] > this.hi[i]) {
				continue;
			}
			if (!this.has(p.x() + 1, p.y(), p.z()) || !this.has(p.x() - 1, p.y(), p.z()) || !this.has(p.x(), p.y(), p.z() + 1)
				|| !this.has(p.x(), p.y(), p.z() - 1) || !this.has(p.x(), p.y() - 1, p.z())) {
				inside.add(i);
			}
		}
		this.skin = inside.toIntArray();
	}

	boolean has(int x, int y, int z) {
		return this.index.get(BlockPos.asLong(x, y, z)) >= 0;
	}

	/** Whether a local cell is inside the ship: one of its blocks, or the dry space its walls close in. */
	public boolean inside(int x, int y, int z) {
		long cell = BlockPos.asLong(x, y, z);
		return this.index.get(cell) >= 0 || this.dry.contains(cell);
	}

	/** The piece at a local cell (-1: none). */
	public int at(int x, int y, int z) {
		return this.index.get(BlockPos.asLong(x, y, z));
	}

	/** A block-full of this weighs (water = 1). */
	static float density(BlockState state) {
		if (state.is(Blocks.GOLD_BLOCK) || state.is(Blocks.RAW_GOLD_BLOCK)) {
			return GOLD;
		}
		if (state.is(BlockTags.WOOL) || state.is(BlockTags.WOOL_CARPETS) || state.is(BlockTags.BANNERS) || state.getBlock() instanceof SailBlock
			|| state.is(BlockTags.LEAVES)) {
			return CLOTH;
		}
		SoundType sound = state.getSoundType();
		if (sound == SoundType.METAL || sound == SoundType.COPPER || sound == SoundType.NETHERITE_BLOCK || sound == SoundType.ANVIL) {
			return METAL;
		}
		if (sound == SoundType.GLASS) {
			return GLASS;
		}
		if (sound == SoundType.WOOD || sound == SoundType.BAMBOO_WOOD || sound == SoundType.CHERRY_WOOD || sound == SoundType.NETHER_WOOD) {
			return WOOD;
		}
		// stone and earth: what wants a pickaxe or a shovel, and loose ground
		if (state.requiresCorrectToolForDrops() || sound == SoundType.GRAVEL || sound == SoundType.SAND || sound == SoundType.ROOTED_DIRT
			|| sound == SoundType.STONE || sound == SoundType.DEEPSLATE || sound == SoundType.TUFF || sound == SoundType.MUD_BRICKS) {
			return STONE;
		}
		return WOOD;
	}
}
