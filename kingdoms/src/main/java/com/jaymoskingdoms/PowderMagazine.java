package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * A realm's powder garrison (Andy, 2026-11-15: "There should be ways for them to resupply, either from powder garrisons
 * in kingdoms or looting", and "It shouldnt replace the storehouse"): its own building, apart from the storehouse, a
 * squat stone magazine with one door, no windows and nothing burning inside (the lanterns hang outside), its
 * powder kept in barrels along the walls. Kingdoms and fiefdoms raise one as they grow ({@link Buildings#register}).
 * The realm's gunmen draw their powder here ({@link MobAmmo}); its weaponsmith keeps it filled, carrying over powder
 * the storehouse got in trade and milling more ({@code Jobs}). Anyone who gets in can take what's in the barrels.
 */
final class PowderMagazine {
	static final String ID = "powder_magazine";
	/** Its size: width and depth (door in the middle of the north wall), wall height. */
	static final int W = 7, D = 7, WALL = 3;

	private PowderMagazine() {
	}

	/** The magazine: stone walls and floor, a slab roof, a door, barrels round the inside, lanterns outside the door. */
	static Blueprint design(Buildings.Kit k) {
		Buildings.Sketch s = new Buildings.Sketch(ID, W, D, WALL + 2);
		Palette p = k.p;
		for (int x = 0; x < W; x++) {
			for (int z = 0; z < D; z++) {
				boolean edge = x == 0 || x == W - 1 || z == 0 || z == D - 1;
				boolean corner = (x == 0 || x == W - 1) && (z == 0 || z == D - 1);
				s.set(x, 0, z, p.wall());
				if (edge) {
					for (int y = 1; y <= WALL; y++) {
						s.set(x, y, z, corner ? p.trim() : Buildings.Sketch.weathered(p, x, y, z, k.salt));
					}
				}
				s.set(x, WALL + 1, z, edge ? p.wall() : p.slab());
			}
		}
		s.door(W / 2, 1, 0, Direction.NORTH, k.door);
		// barrels along the back and side walls, two high at the back
		for (int x = 1; x < W - 1; x++) {
			s.set(x, 1, D - 2, Blocks.BARREL.defaultBlockState());
			if (x % 2 == 1) {
				s.set(x, 2, D - 2, Blocks.BARREL.defaultBlockState());
			}
		}
		for (int z = 2; z < D - 2; z++) {
			s.set(1, 1, z, Blocks.BARREL.defaultBlockState());
			s.set(W - 2, 1, z, Blocks.BARREL.defaultBlockState());
		}
		// the lights stay outside, either side of the door
		s.set(W / 2 - 1, 2, 0, Buildings.Sketch.facing(Blocks.WALL_TORCH, Direction.NORTH));
		s.set(W / 2 + 1, 2, 0, Buildings.Sketch.facing(Blocks.WALL_TORCH, Direction.NORTH));
		return s.done();
	}

	/** The barrels of {@code f}'s standing magazines (none if it has none, or they aren't loaded). */
	static List<Container> barrels(ServerLevel level, Faction f) {
		List<Container> found = new ArrayList<>();
		for (Works.Built b : Works.of(level.getServer(), f.id()).built()) {
			if (!b.entry().equals(ID) || b.ruined() != 0 || !level.isLoaded(b.min()) || !level.isLoaded(b.max())) {
				continue;
			}
			for (BlockPos pos : BlockPos.betweenClosed(b.min(), b.max())) {
				BlockEntity be = level.getBlockEntity(pos);
				if (be instanceof BarrelBlockEntity barrel) {
					found.add(barrel);
				}
			}
		}
		return found;
	}

	/** Where {@code f}'s nearest standing magazine's door side is to {@code from}, or null if it has none. */
	static @org.jspecify.annotations.Nullable BlockPos nearest(ServerLevel level, Faction f, BlockPos from) {
		BlockPos best = null;
		for (Works.Built b : Works.of(level.getServer(), f.id()).built()) {
			if (!b.entry().equals(ID) || b.ruined() != 0) {
				continue;
			}
			BlockPos middle = new BlockPos((b.min().getX() + b.max().getX()) / 2, b.min().getY() + 1, (b.min().getZ() + b.max().getZ()) / 2);
			if (best == null || middle.distSqr(from) < best.distSqr(from)) {
				best = middle;
			}
		}
		return best;
	}

	/** Gunpowder in {@code f}'s magazines. */
	static int powder(ServerLevel level, Faction f) {
		int n = 0;
		for (Container c : barrels(level, f)) {
			for (int i = 0; i < c.getContainerSize(); i++) {
				ItemStack s = c.getItem(i);
				if (s.is(Items.GUNPOWDER)) {
					n += s.getCount();
				}
			}
		}
		return n;
	}

	/** Takes up to {@code max} gunpowder out of {@code f}'s magazines; returns how much it took. */
	static int take(ServerLevel level, Faction f, int max) {
		int took = 0;
		for (Container c : barrels(level, f)) {
			for (int i = 0; i < c.getContainerSize() && took < max; i++) {
				ItemStack s = c.getItem(i);
				if (s.is(Items.GUNPOWDER)) {
					int n = Math.min(max - took, s.getCount());
					s.shrink(n);
					took += n;
					c.setChanged();
				}
			}
		}
		return took;
	}

	/** Puts {@code count} gunpowder into {@code f}'s magazines; returns how much didn't fit (all of it without one). */
	static int put(ServerLevel level, Faction f, int count) {
		int left = count;
		for (Container c : barrels(level, f)) {
			for (int i = 0; i < c.getContainerSize() && left > 0; i++) {
				ItemStack s = c.getItem(i);
				int max = Items.GUNPOWDER.getDefaultMaxStackSize();
				if (s.isEmpty()) {
					int n = Math.min(left, max);
					c.setItem(i, new ItemStack(Items.GUNPOWDER, n));
					left -= n;
				} else if (s.is(Items.GUNPOWDER) && s.getCount() < max) {
					int n = Math.min(left, max - s.getCount());
					s.grow(n);
					left -= n;
					c.setChanged();
				}
			}
		}
		return left;
	}
}
