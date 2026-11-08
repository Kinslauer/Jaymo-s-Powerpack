package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.605) Andy (2026-11-05), with a screenshot of Moog's Voyager Structures' barn: "This is what I imagine for a
 * starting over in a village start, it should instead be family farmhand, and you live with the villager(s) that spawn
 * there." The village road ({@link LifeStory.Start#VILLAGE}) is now the Family farmhand: you wake inside a lone
 * farmhouse, the nearest Moog's Voyager barn ({@link #BARN}) or else a Homesteads farmstead ({@link #HOMESTEADS}), and
 * the family living there takes you in as their hand. A farmhouse with fewer than {@link #FAMILY_LEAST} grown villagers
 * in it gets a farming couple (and, now and then, a child) of your own blood. Everyone in the house starts warm to you
 * ({@link #FAMILY_HEARTS}). With neither mod's farmhouse in reach, the road falls back to the old village start.
 */
public final class FarmhandHome {
	/** Moog's Voyager Structures' barn: Andy's picture of the start. */
	static final ResourceKey<Structure> BARN = key("mvs", "barn");
	/** Homesteads' lone farmsteads (each comes with its own villagers), if no barn is in reach. */
	static final List<ResourceKey<Structure>> HOMESTEADS = List.of(key("homesteads", "flower_home_steads"),
		key("homesteads", "forest_home_steads"), key("homesteads", "taiga_home_steads"), key("homesteads", "swamp_home_steads"));
	/** How far a farmhouse is looked for (chunks, as findNearestMapStructure counts). */
	static final int SEARCH_CHUNKS = 100;
	/** Fewer grown villagers than this in the house: a farming couple moves in. */
	static final int FAMILY_LEAST = 2;
	/** The chance the couple has a child with them. */
	static final float CHILD_CHANCE = 0.5F;
	/** Hearts every villager of the house starts with toward their new hand (MCA). */
	static final int FAMILY_HEARTS = 40;
	/** How far round the house its folk are counted (blocks). */
	static final double HOUSE_REACH = 6.0;

	/** A farmhouse found for a farmhand: where to stand, the house's bounds, and the bed they wake in (its head; null with none). */
	record House(BlockPos wake, BoundingBox box, @Nullable BlockPos bed) {
	}

	/** A spot on the floor beside a bed, and that bed's head. */
	private record Bedside(BlockPos spot, BlockPos head) {
	}

	private FarmhandHome() {
	}

	private static ResourceKey<Structure> key(String ns, String path) {
		return ResourceKey.create(Registries.STRUCTURE, Identifier.fromNamespaceAndPath(ns, path));
	}

	/** The nearest farmhouse to {@code from} (a barn first, then a farmstead), or null if neither mod is loaded or none is in reach. */
	static @Nullable House find(ServerLevel level, BlockPos from, RandomSource random) {
		Registry<Structure> structures = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
		House barn = find(level, structures, List.of(BARN), from, random);
		return barn != null ? barn : find(level, structures, HOMESTEADS, from, random);
	}

	private static @Nullable House find(ServerLevel level, Registry<Structure> structures, List<ResourceKey<Structure>> keys, BlockPos from,
		RandomSource random) {
		List<Holder<Structure>> holders = new ArrayList<>();
		for (ResourceKey<Structure> k : keys) {
			structures.get(k).ifPresent(holders::add);
		}
		if (holders.isEmpty()) {
			return null;
		}
		var found = level.getChunkSource().getGenerator().findNearestMapStructure(level, HolderSet.direct(holders), from, SEARCH_CHUNKS, false);
		if (found == null) {
			return null;
		}
		BlockPos at = found.getFirst();
		StructureStart start = level.getChunk(at.getX() >> 4, at.getZ() >> 4, ChunkStatus.STRUCTURE_STARTS).getStartForStructure(found.getSecond().value());
		if (start == null || !start.isValid()) {
			return null;
		}
		BoundingBox box = start.getBoundingBox();
		Bedside side = bedside(level, box);
		if (side != null) {
			return new House(side.spot(), box, side.head());
		}
		BlockPos wake = LifeStory.ground(level, box.getCenter(), 0, Math.max(box.getXSpan(), box.getZSpan()) / 2 + 2, random);
		return wake == null ? null : new House(wake, box, null);
	}

	/** A spot to stand beside one of the house's beds (loading its chunks), and the bed's head; null with none. */
	private static @Nullable Bedside bedside(ServerLevel level, BoundingBox box) {
		for (BlockPos p : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
			BlockState bed = level.getBlockState(p);
			if (!(bed.getBlock() instanceof BedBlock) || bed.getValue(BedBlock.PART) != BedPart.FOOT) {
				continue;
			}
			// (the floor beside the bed's foot, clear for two blocks)
			for (var dir : net.minecraft.core.Direction.Plane.HORIZONTAL) {
				BlockPos side = p.relative(dir);
				if (level.getBlockState(side).isAir() && level.getBlockState(side.above()).isAir()
					&& level.getBlockState(side.below()).isFaceSturdy(level, side.below(), net.minecraft.core.Direction.UP)) {
					return new Bedside(side.immutable(), p.relative(bed.getValue(BedBlock.FACING)).immutable());
				}
			}
		}
		return null;
	}

	/**
	 * {@code player} wakes in {@code house}: a farming couple of their blood moves in if the house hasn't folk enough, and
	 * everyone there starts warm to them.
	 */
	static void takeIn(ServerPlayer player, ServerLevel level, House house, Heritage.@Nullable Kind blood) {
		AABB room = AABB.of(house.box()).inflate(HOUSE_REACH);
		List<Villager> folk = level.getEntitiesOfClass(Villager.class, room, v -> v.isAlive());
		int grown = 0;
		for (Villager v : folk) {
			if (!v.isBaby()) {
				grown++;
			}
		}
		if (grown < FAMILY_LEAST) {
			Vec3 at = Vec3.atBottomCenterOf(house.wake());
			VillagerProfession farmer = net.minecraft.core.registries.BuiltInRegistries.VILLAGER_PROFESSION.getValue(VillagerProfession.FARMER);
			Villager a = McaCompat.visitor(level, at, farmer, false);
			Villager b = McaCompat.visitor(level, at, farmer, false);
			Villager child = level.getRandom().nextFloat() < CHILD_CHANCE ? McaCompat.visitor(level, at, null, true) : null;
			for (Villager v : java.util.Arrays.asList(a, b, child)) {
				if (v == null) {
					continue;
				}
				v.setPersistenceRequired();
				if (blood != null) {
					VillagerHeritage.born(v, blood, null);
				}
				folk.add(v);
			}
			if (a != null && b != null) {
				McaCompat.marry(a, b);
			}
		}
		for (Villager v : folk) {
			McaCompat.hearts(v, player, FAMILY_HEARTS);
		}
		// (Andy: "they need some kind of utility or function": their farm round, their fear of monsters, their nights indoors)
		FarmFamily.adopt(level, house.wake(), folk);
		if (McaCompat.LOADED && !folk.isEmpty()) {
			player.sendSystemMessage(net.minecraft.network.chat.Component.translatable("story.jaymoskingdoms.farm_family.hint")
				.withStyle(net.minecraft.ChatFormatting.GRAY));
		}
		if (house.bed() != null) {
			FarmhandWake.expect(player, house.bed(), house.wake(), folk);
		}
	}
}
