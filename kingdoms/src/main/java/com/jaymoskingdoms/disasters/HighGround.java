package com.jaymoskingdoms.disasters;

import com.jaymoskingdoms.Faction;
import com.jaymoskingdoms.FactionType;
import com.jaymoskingdoms.Factions;
import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.Loaded;
import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

/**
 * Lightning that seeks high ground (medieval picks, nd-lightning). In the worst storms (a supercell, a hurricane's wall)
 * a bolt doesn't fall just anywhere: it picks the tallest thing about it, a tower, a lone tree, a rider on a hill. A
 * lightning rod draws it from further off and takes it safely. The settlements look after themselves: the first storm
 * to come over a realm's keep or a village's church sees its folk put a rod on the top of it.
 */
public final class HighGround {
	/** How far round the bolt's spot it looks for something taller, and how many columns it tries. */
	static final int SEEK_RADIUS = 24, SEEK_TRIES = 20;
	/** A creature out in the open stands this much above the ground it's on, as the bolt sees it (a rider: more). */
	static final double STANDING = 1.0;
	/** A rod this far off draws the bolt in the worst storms (blocks). */
	static final int ROD_REACH = 48;
	/** How often the settlements under a storm are looked at for a rod (ticks); how far round the seat their tallest
	 * roof is looked for; how far from the seat a church may be, and how far round its brewing stand its tower. */
	static final int FIT_EVERY = 400, KEEP_RADIUS = 14, CHURCH_REACH = 64, CHURCH_RADIUS = 6;

	/** The settlements (faction ids) whose keep and church already have their rods. */
	static final AttachmentType<List<Integer>> FITTED = AttachmentRegistry.create(JaymosKingdoms.id("lightning_rods_fitted"),
		b -> b.persistent(Codec.INT.listOf()));

	private HighGround() {
	}

	/** The highest thing near {@code top} (a column's top, or a creature standing tall on one) for a bolt to hit. */
	static BlockPos seek(ServerLevel level, BlockPos top, RandomSource random) {
		BlockPos best = top;
		double bestY = top.getY();
		for (int i = 0; i < SEEK_TRIES; i++) {
			int x = top.getX() + random.nextInt(SEEK_RADIUS * 2 + 1) - SEEK_RADIUS, z = top.getZ() + random.nextInt(SEEK_RADIUS * 2 + 1) - SEEK_RADIUS;
			if (!Loaded.made(level, x, z)) {
				continue;
			}
			int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
			if (y > bestY) {
				bestY = y;
				best = new BlockPos(x, y, z);
			}
		}
		AABB around = new AABB(top).inflate(SEEK_RADIUS, 48.0, SEEK_RADIUS);
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, around, e -> e.isAlive() && !e.isSpectator())) {
			double tall = e.getBoundingBox().maxY + (e.isPassenger() ? STANDING : 0.0);
			if (tall > bestY + STANDING && level.canSeeSky(e.blockPosition().above())) {
				bestY = tall;
				best = e.blockPosition();
			}
		}
		return best;
	}

	/** A lightning rod standing at the top of its column within {@link #ROD_REACH} of {@code pos}, or null. */
	static @Nullable BlockPos rod(ServerLevel level, BlockPos pos) {
		Optional<BlockPos> rod = level.getPoiManager().findClosest(p -> p.is(PoiTypes.LIGHTNING_ROD),
			at -> Loaded.made(level, at) && at.getY() == level.getHeight(Heightmap.Types.WORLD_SURFACE, at.getX(), at.getZ()) - 1, pos, ROD_REACH,
			PoiManager.Occupancy.ANY);
		return rod.orElse(null);
	}

	/** Settlements under a thunderstorm get their rods (now and then). */
	static void tick(MinecraftServer server) {
		ServerLevel level = server.overworld();
		if (level.getGameTime() % FIT_EVERY != 0 || Storms.SERVER.isEmpty() || level != Factions.level(server)) {
			return;
		}
		List<Integer> fitted = level.getAttachedOrElse(FITTED, List.of());
		List<Integer> now = null;
		for (Faction f : Factions.all(server)) {
			if (f.fallen() || f.type() == FactionType.BARBARIAN || fitted.contains(f.id()) || Storms.thunder(level, f.seat().getX(), f.seat().getZ()) < 0.3F) {
				continue;
			}
			if (!Loaded.made(level, f.seat())) {
				continue;
			}
			fit(level, f.seat(), KEEP_RADIUS);
			level.getPoiManager().findClosest(p -> p.is(PoiTypes.CLERIC), f.seat(), CHURCH_REACH, PoiManager.Occupancy.ANY)
				.filter(at -> Loaded.made(level, at)).ifPresent(church -> fit(level, church, CHURCH_RADIUS));
			if (now == null) {
				now = new ArrayList<>(fitted);
			}
			now.add(f.id());
		}
		if (now != null) {
			level.setAttached(FITTED, List.copyOf(now));
		}
	}

	/** A rod on the tallest built roof within {@code radius} of {@code centre}. */
	private static void fit(ServerLevel level, BlockPos centre, int radius) {
		BlockPos best = null;
		for (int dx = -radius; dx <= radius; dx++) {
			for (int dz = -radius; dz <= radius; dz++) {
				int x = centre.getX() + dx, z = centre.getZ() + dz;
				if (!Loaded.made(level, x, z)) {
					continue;
				}
				int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
				if (best == null || y > best.getY()) {
					best = new BlockPos(x, y, z);
				}
			}
		}
		if (best == null) {
			return;
		}
		BlockPos roof = best.below();
		BlockState state = level.getBlockState(roof);
		if (state.is(Blocks.LIGHTNING_ROD.waxed().unaffected()) || state.is(BlockTags.LIGHTNING_RODS)) {
			return;
		}
		boolean built = !state.is(BlockTags.BASE_STONE_OVERWORLD) && !state.is(BlockTags.DIRT) && !state.is(BlockTags.LOGS)
			&& !state.is(BlockTags.LEAVES) && !state.is(BlockTags.SAND) && state.getFluidState().isEmpty();
		if (built && level.getBlockState(best).isAir() && state.isFaceSturdy(level, roof, net.minecraft.core.Direction.UP)) {
			BlockBudget.set(BlockBudget.Of.RODS, level, best, Blocks.LIGHTNING_ROD.waxed().unaffected().defaultBlockState());
		}
	}
}
