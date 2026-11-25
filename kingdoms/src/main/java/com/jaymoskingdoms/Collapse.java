package com.jaymoskingdoms;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.tag.convention.v2.ConventionalBlockTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.Clearable;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Blasts bring buildings down (Andy's TNT Logic Enhanced pick; our own code, nothing of that mod's). A blast that
 * breaks blocks (TNT, a creeper, Balkon's dynamite, a cannon's shell or ball: {@code Bombardment}) undercuts what stood
 * on them: once the blocks are gone, what a building has left above the hole that nothing holds up any more comes down
 * as falling blocks, onto whoever is under it.
 * <p>
 * What holds: a block stands on what's under it, and reaches out sideways from a held one a few blocks at most
 * ({@link #SPAN_STONE} for stone and brick, {@link #SPAN_OTHER} for timber and the rest), the way a floor spans from a
 * wall. A support beam (Powers' {@code support_beam}) carries {@link #SPAN_BEAM}: posts and cross beams hold up what
 * they prop. The land itself (stone, earth, sand, ore, ice, living trees) always holds and never falls here, and only
 * what the blast took the hold from comes down: a sky bridge or cloud town that held before it still does.
 * <p>
 * Chests, barrels and anything else that keeps things ride down whole with what's in them, and land in the rubble; one
 * that can't land (onto a spike, out of the world) spills its things where it came down. Doors and beds break. A siege
 * camp's guns can bring a tower down this way, and what falls counts as breached for the realm's masons
 * ({@link Sieges#broken}).
 */
public final class Collapse {
	/** How far round a blast (blocks, by its radius, plus this, at most this) and above and below it is looked at. */
	static final double REACH_PER_RADIUS = 1.5;
	static final int REACH_MORE = 3, REACH_MOST = 14, UP = 32, DOWN = 3;
	/** How far (blocks) a held block holds up the one beside it: stone and the like, anything else, a support beam. */
	static final int SPAN_STONE = 4, SPAN_OTHER = 3, SPAN_BEAM = 8;
	/** Blast resistance from which a block counts as stone (planks are 3, stone and brick 6). */
	static final float STONE_RESISTANCE = 4.0F;
	/** At least as tough as this, a block never falls (obsidian, and worse). */
	static final float TOUGHEST = 1000.0F;
	/** The most blocks one blast brings down. */
	static final int MOST = 384;
	/** A falling block hurts what it lands on this much a block it fell, at most this much. */
	static final float HURT = 2.0F;
	static final int HURT_MOST = 20;
	/** The most blasts looked at a tick (the rest wait), and the most waiting (a chain of TNT past that isn't looked at). */
	static final int CHECKS_PER_TICK = 4, WAITING_MOST = 24;
	static final Identifier SUPPORT_BEAM = Identifier.fromNamespaceAndPath("jaymospowers", "support_beam");

	private static final int AIR = 0, LAND = 1, BUILT = 2, BEAM = 3;
	/** Land holds without end. */
	private static final int HELD_FOREVER = 1000;

	/** A box of the world round a blast, looked at before it and again after. */
	public static final class Look {
		final ServerLevel level;
		final BlockPos min;
		final int sx, sy, sz;
		/** What held before the blast. */
		@Nullable BitSet before;

		Look(ServerLevel level, BlockPos min, int sx, int sy, int sz) {
			this.level = level;
			this.min = min;
			this.sx = sx;
			this.sy = sy;
			this.sz = sz;
		}

		int index(int x, int y, int z) {
			return x + sx * (z + sz * y);
		}
	}

	/** A falling chest or barrel and what was in it, in case it can't land. */
	private record Carried(FallingBlockEntity entity, ServerLevel level, List<ItemStack> things) {
	}

	private static final Deque<Look> AFTER = new ArrayDeque<>();
	private static final List<Carried> CARRIED = new ArrayList<>();
	private static @Nullable Block beam;
	private static boolean lookedBeam;

	private Collapse() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Collapse::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			AFTER.clear();
			CARRIED.clear();
		});
	}

	/**
	 * A blast of {@code radius} at {@code center} is about to break blocks: what holds round it now, for
	 * {@link #after}. Null if there's nothing to look at (the land isn't loaded).
	 */
	public static @Nullable Look before(ServerLevel level, Vec3 center, double radius) {
		if (AFTER.size() >= WAITING_MOST) {
			return null;
		}
		int reach = Math.min(REACH_MOST, Mth.ceil(radius * REACH_PER_RADIUS) + REACH_MORE);
		BlockPos c = BlockPos.containing(center);
		BlockPos min = new BlockPos(c.getX() - reach, Math.max(level.getMinY(), c.getY() - DOWN), c.getZ() - reach);
		int top = Math.min(level.getMaxY(), c.getY() + UP);
		if (top <= min.getY() || !Loaded.made(level, min) || !Loaded.made(level, min.offset(2 * reach, 0, 2 * reach))
			|| !Loaded.made(level, min.offset(2 * reach, 0, 0)) || !Loaded.made(level, min.offset(0, 0, 2 * reach))) {
			return null;
		}
		Look look = new Look(level, min, 2 * reach + 1, top - min.getY() + 1, 2 * reach + 1);
		look.before = held(look);
		return look;
	}

	/** The blast {@code look} was taken for has broken its blocks: what lost its hold comes down (at the end of the tick). */
	public static void after(@Nullable Look look) {
		if (look != null && look.before != null) {
			AFTER.add(look);
		}
	}

	private static void tick(MinecraftServer server) {
		for (int i = 0; i < CHECKS_PER_TICK && !AFTER.isEmpty(); i++) {
			collapse(AFTER.poll());
		}
		for (Iterator<Carried> it = CARRIED.iterator(); it.hasNext();) {
			Carried c = it.next();
			FallingBlockEntity e = c.entity();
			if (!e.isRemoved()) {
				continue;
			}
			it.remove();
			if (e.getRemovalReason() != Entity.RemovalReason.DISCARDED && e.getRemovalReason() != Entity.RemovalReason.KILLED) {
				// (gone with its land: it's saved with its things and lands when that land loads again)
				continue;
			}
			BlockPos at = e.blockPosition();
			if (c.level().getBlockState(at).is(e.getBlockState().getBlock()) && c.level().getBlockEntity(at) != null) {
				continue;
			}
			// it couldn't land: what was in it spills where it came down
			for (ItemStack thing : c.things()) {
				Block.popResource(c.level(), at, thing);
			}
		}
	}

	/** What lost its hold in {@code look} comes down, lowest first. */
	private static void collapse(Look look) {
		ServerLevel level = look.level;
		if (!Loaded.made(level, look.min) || !Loaded.made(level, look.min.offset(look.sx - 1, 0, look.sz - 1))) {
			return;
		}
		BitSet now = held(look);
		BitSet before = look.before;
		int fell = 0;
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		for (int y = 0; y < look.sy && fell < MOST; y++) {
			for (int z = 0; z < look.sz && fell < MOST; z++) {
				for (int x = 0; x < look.sx && fell < MOST; x++) {
					int i = look.index(x, y, z);
					if (!before.get(i) || now.get(i)) {
						continue;
					}
					p.set(look.min.getX() + x, look.min.getY() + y, look.min.getZ() + z);
					BlockState state = level.getBlockState(p);
					int kind = kind(level, p, state);
					if (kind != BUILT && kind != BEAM) {
						continue;
					}
					drop(level, p.immutable(), state);
					fell++;
				}
			}
		}
		if (fell > 0) {
			Vec3 mid = Vec3.atCenterOf(look.min.offset(look.sx / 2, 0, look.sz / 2));
			level.playSound(null, mid.x, mid.y + 2.0, mid.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 0.8F, 0.5F);
			level.playSound(null, mid.x, mid.y + 2.0, mid.z, SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR, SoundSource.BLOCKS, 1.0F, 0.5F);
		}
	}

	/** The block at {@code pos} comes down: as a falling block, a chest with what's in it; a door or bed just breaks. */
	private static void drop(ServerLevel level, BlockPos pos, BlockState state) {
		Sieges.broken(level, pos, state);
		if (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF) || state.hasProperty(BlockStateProperties.BED_PART)) {
			level.destroyBlock(pos, true);
			return;
		}
		BlockEntity be = level.getBlockEntity(pos);
		CompoundTag data = null;
		List<ItemStack> things = new ArrayList<>();
		if (be != null) {
			data = be.saveWithoutMetadata(level.registryAccess());
			if (be instanceof Container container) {
				for (int i = 0; i < container.getContainerSize(); i++) {
					ItemStack s = container.getItem(i);
					if (!s.isEmpty()) {
						things.add(s.copy());
					}
				}
			}
			// (emptied first, or it would spill as the block leaves; it's all in what rides down with it)
			if (be instanceof Clearable clearable) {
				clearable.clearContent();
			}
		}
		// (a double chest's halves come down apart, each a chest of its own with its half of the things)
		BlockState falls = state.hasProperty(BlockStateProperties.CHEST_TYPE)
			? state.setValue(BlockStateProperties.CHEST_TYPE, net.minecraft.world.level.block.state.properties.ChestType.SINGLE) : state;
		FallingBlockEntity falling = FallingBlockEntity.fall(level, pos, falls);
		falling.setHurtsEntities(HURT, HURT_MOST);
		falling.blockData = data;
		if (!things.isEmpty()) {
			CARRIED.add(new Carried(falling, level, things));
		}
	}

	/**
	 * What holds in {@code look}, from the bottom up: a block on a held one is held as far as its span reaches, and
	 * passes its hold on sideways one block less each block out; land always holds; the box's sides and floor are taken
	 * as held (what's past them isn't looked at).
	 */
	private static BitSet held(Look look) {
		ServerLevel level = look.level;
		int layer = look.sx * look.sz;
		int[] kinds = new int[layer], below = new int[layer], here = new int[layer], spans = new int[layer];
		BitSet out = new BitSet(layer * look.sy);
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		for (int y = 0; y < look.sy; y++) {
			for (int z = 0; z < look.sz; z++) {
				for (int x = 0; x < look.sx; x++) {
					int j = x + look.sx * z;
					p.set(look.min.getX() + x, look.min.getY() + y, look.min.getZ() + z);
					BlockState state = level.getBlockState(p);
					int kind = kind(level, p, state);
					kinds[j] = kind;
					if (kind == AIR) {
						here[j] = 0;
						continue;
					}
					if (kind == LAND) {
						here[j] = HELD_FOREVER;
						continue;
					}
					int span = kind == BEAM ? SPAN_BEAM : state.getBlock().getExplosionResistance() >= STONE_RESISTANCE ? SPAN_STONE : SPAN_OTHER;
					spans[j] = span;
					boolean edge = y == 0 || x == 0 || z == 0 || x == look.sx - 1 || z == look.sz - 1;
					here[j] = edge || below[j] > 0 ? span : 0;
				}
			}
			// sideways: each pass carries a hold one block further out
			for (int pass = 0; pass < SPAN_BEAM; pass++) {
				boolean changed = false;
				for (int z = 0; z < look.sz; z++) {
					for (int x = 0; x < look.sx; x++) {
						int j = x + look.sx * z;
						if ((kinds[j] != BUILT && kinds[j] != BEAM) || here[j] >= spans[j]) {
							continue;
						}
						int best = 0;
						if (x > 0) {
							best = Math.max(best, here[j - 1]);
						}
						if (x < look.sx - 1) {
							best = Math.max(best, here[j + 1]);
						}
						if (z > 0) {
							best = Math.max(best, here[j - look.sx]);
						}
						if (z < look.sz - 1) {
							best = Math.max(best, here[j + look.sx]);
						}
						int got = Math.min(spans[j], best - 1);
						if (got > here[j]) {
							here[j] = got;
							changed = true;
						}
					}
				}
				if (!changed) {
					break;
				}
			}
			for (int j = 0; j < layer; j++) {
				if (here[j] > 0 && kinds[j] != AIR) {
					out.set(j + layer * y);
				}
				below[j] = kinds[j] == AIR ? 0 : here[j];
			}
		}
		return out;
	}

	/** What the block at {@code pos} is to a building: nothing, the land, part of the building, or a support beam. */
	private static int kind(ServerLevel level, BlockPos pos, BlockState state) {
		if (state.isAir() || state.getCollisionShape(level, pos).isEmpty()) {
			return AIR;
		}
		Block block = state.getBlock();
		if (block == beam()) {
			return BEAM;
		}
		if (state.getDestroySpeed(level, pos) < 0.0F || block.getExplosionResistance() >= TOUGHEST
			|| state.is(BlockTags.BASE_STONE_OVERWORLD) || state.is(BlockTags.BASE_STONE_NETHER) || state.is(BlockTags.DIRT)
			|| state.is(BlockTags.SAND) || state.is(BlockTags.MINEABLE_WITH_SHOVEL) || state.is(BlockTags.TERRACOTTA)
			|| state.is(BlockTags.ICE) || state.is(ConventionalBlockTags.ORES) || state.is(Blocks.END_STONE)
			|| block instanceof LeavesBlock && !state.getValue(LeavesBlock.PERSISTENT)
			|| state.is(BlockTags.LOGS) && Trees.natural(level, pos)) {
			return LAND;
		}
		return BUILT;
	}

	private static @Nullable Block beam() {
		if (!lookedBeam) {
			lookedBeam = true;
			beam = BuiltInRegistries.BLOCK.getOptional(SUPPORT_BEAM).orElse(null);
		}
		return beam;
	}
}
