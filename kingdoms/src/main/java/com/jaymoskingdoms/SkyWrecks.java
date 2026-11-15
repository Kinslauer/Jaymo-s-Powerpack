package com.jaymoskingdoms;

import com.jaymoskingdoms.sailing.Sailing;
import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.Filterable;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.core.Holder;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.SkullBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Downed airship wrecks (the medieval picks' vp-wrecks): now and then a player in the mountains or a forest happens on a
 * balloon ship that came down there, one of the steam towns' (as {@link TreasureWrecks} has players at sea happen on a wreck).
 * Her deck lies broken over the ground, timbers lost, her bag slack and torn across it; her stores are scattered round her,
 * barrels burst open; her crew lie dead by her (Kitchen's bodies, with Jaymo's Kitchen; bones and a skull without). In her
 * chest is her log, which names the town she was out of and the one she was bound for, and which way it lies from here and
 * how far. They lie anywhere wild; with steam towns standing, likelier near the way their airships fly between them (and
 * then she was one of theirs, bound from one to another).
 */
final class SkyWrecks {
	// ---- tuning ----
	/** How often players are looked at (ticks), the chance each look, how far off a wreck lies, how far apart they are. */
	static final int CHANCE_TICKS = 1200;
	static final float WRECK_CHANCE = 0.02F;
	static final int NEAREST = 64, FURTHEST = 112, SPACING = 800;
	/** No player this near where she's laid. */
	static final double HIDE = 48.0;
	/** Her timbers lost (the chance each), her bag's cloth left (the chance each), her posts left standing (blocks at most). */
	static final float TIMBER_LOST = 0.25F, CLOTH_LEFT = 0.45F;
	static final int POST_LEFT = 1;
	/** Near the steam towns' routes (this near a town or the line between two), a wreck is this many times likelier. */
	static final double ROUTE_NEAR = 256.0;
	static final float ROUTE_FAVOUR = 3.0F;
	/** Her dead, her stores barrels thrown this far at most. */
	static final int DEAD_LEAST = 2, DEAD_MOST = 3, THROWN = 6;

	/** Sky wrecks laid (packed places, for spacing). */
	static final AttachmentType<List<Long>> LAID = AttachmentRegistry.create(JaymosKingdoms.id("sky_wrecks"),
		builder -> builder.persistent(Codec.LONG.listOf()));

	private SkyWrecks() {
	}

	static void register() {
		JaymosKingdoms.LOGGER.debug("sky wreck attachment: {}", LAID);
		ServerTickEvents.END_SERVER_TICK.register(SkyWrecks::tick);
	}

	private static void tick(MinecraftServer server) {
		ServerLevel level = Factions.level(server);
		if (level.getGameTime() % CHANCE_TICKS != 11) {
			return;
		}
		RandomSource random = level.getRandom();
		List<Faction> towns = steamTowns(level);
		for (ServerPlayer player : level.players()) {
			float chance = WRECK_CHANCE * (nearRoute(towns, Vec3.atCenterOf(player.blockPosition())) ? ROUTE_FAVOUR : 1.0F);
			if (player.isSpectator() || random.nextFloat() >= chance || !wild(level.getBiome(player.blockPosition()))) {
				continue;
			}
			happenOn(level, player, random);
		}
	}

	/** The steam towns standing (their settlements). */
	private static List<Faction> steamTowns(ServerLevel level) {
		List<Faction> out = new ArrayList<>();
		for (SteamTowns.Town t : SteamTowns.all(level)) {
			Faction f = Factions.get(level.getServer(), t.id());
			if (f != null) {
				out.add(f);
			}
		}
		return out;
	}

	/** {@code at} lies near a steam town, or near the line its airships fly to another. */
	private static boolean nearRoute(List<Faction> towns, Vec3 at) {
		for (int i = 0; i < towns.size(); i++) {
			Vec3 a = Vec3.atCenterOf(towns.get(i).seat());
			if (flat(a, at) < ROUTE_NEAR) {
				return true;
			}
			for (int j = i + 1; j < towns.size(); j++) {
				if (offLine(a, Vec3.atCenterOf(towns.get(j).seat()), at) < ROUTE_NEAR) {
					return true;
				}
			}
		}
		return false;
	}

	private static double flat(Vec3 a, Vec3 b) {
		double dx = a.x - b.x, dz = a.z - b.z;
		return Math.sqrt(dx * dx + dz * dz);
	}

	/** How far {@code p} lies from the line from {@code a} to {@code b} (across the ground). */
	private static double offLine(Vec3 a, Vec3 b, Vec3 p) {
		double vx = b.x - a.x, vz = b.z - a.z, len = vx * vx + vz * vz;
		double k = len < 1.0E-6 ? 0.0 : Mth.clamp(((p.x - a.x) * vx + (p.z - a.z) * vz) / len, 0.0, 1.0);
		return flat(new Vec3(a.x + vx * k, 0.0, a.z + vz * k), p);
	}

	private static boolean wild(Holder<Biome> biome) {
		return biome.is(BiomeTags.IS_MOUNTAIN) || biome.is(BiomeTags.IS_FOREST) || biome.is(BiomeTags.IS_TAIGA) || biome.is(BiomeTags.IS_HILL);
	}

	/** {@code player} happens on a downed airship some way off (none other near). */
	private static void happenOn(ServerLevel level, ServerPlayer player, RandomSource random) {
		for (long packed : level.getAttachedOrElse(LAID, List.of())) {
			if (Raids.horizontal(BlockPos.of(packed), player.blockPosition()) < (double)SPACING * SPACING) {
				return;
			}
		}
		for (int tries = 0; tries < 6; tries++) {
			double angle = random.nextDouble() * Math.PI * 2.0;
			int r = NEAREST + random.nextInt(FURTHEST - NEAREST);
			BlockPos column = player.blockPosition().offset(Mth.floor(Math.cos(angle) * r), 0, Mth.floor(Math.sin(angle) * r));
			if (!level.isPositionEntityTicking(column) || !wild(level.getBiome(column))) {
				continue;
			}
			BlockPos ground = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column);
			if (!level.getFluidState(ground.below()).isEmpty() || Raids.playerNear(level, ground, HIDE) || Factions.at(level.getServer(), level, ground) != null) {
				continue;
			}
			lay(level, ground, random);
			return;
		}
	}

	/** The ground under {@code x, z}: the first block over the land there (through leaves and plants). */
	private static int floor(ServerLevel level, int x, int z) {
		return level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
	}

	/** A downed airship at {@code ground}: broken, her stores thrown about, her dead by her, her log in her chest. */
	private static void lay(ServerLevel level, BlockPos ground, RandomSource random) {
		Vec3 here = Vec3.atCenterOf(ground);
		Faction[] way = bound(level, here, random);
		Faction fromF = way[0], toF = way[1];
		DyeColor colour = fromF != null ? fromF.color() : DyeColor.GRAY;
		List<SkyShips.Placed> design = SkyShips.design(Blocks.SPRUCE_PLANKS.defaultBlockState(), Blocks.SPRUCE_FENCE.defaultBlockState(),
			SteamTowns.wool(colour), SteamTowns.wool(DyeColor.WHITE), 0);
		Direction bow = Direction.Plane.HORIZONTAL.getRandomDirection(random);
		net.minecraft.world.level.block.Rotation turn = net.minecraft.world.level.block.Rotation.NONE;
		for (net.minecraft.world.level.block.Rotation rr : net.minecraft.world.level.block.Rotation.values()) {
			if (rr.rotate(Direction.NORTH) == bow) {
				turn = rr;
			}
		}
		List<BlockPos> chests = new ArrayList<>(), barrels = new ArrayList<>();
		for (SkyShips.Placed p : design) {
			BlockPos flat = ground.offset(p.at().offset(-SkyShips.WIDTH / 2, 0, -SkyShips.LENGTH / 2).rotate(turn));
			int y = p.at().getY();
			BlockState state = p.state().rotate(turn);
			boolean cloth = y > SkyShips.POSTS + 1;
			if (cloth ? random.nextFloat() >= CLOTH_LEFT : random.nextFloat() < TIMBER_LOST && !(state.getBlock() instanceof ChestBlock)) {
				continue;
			}
			if (!cloth && y > 1 + POST_LEFT) {
				// (her posts broke off short)
				continue;
			}
			if (!Loaded.made(level, flat)) {
				continue;
			}
			// she lies over the ground as it falls: her deck on it, her rails on her deck, her bag slack over her
			int base = floor(level, flat.getX(), flat.getZ());
			BlockPos at = new BlockPos(flat.getX(), base + (cloth ? (random.nextInt(3) == 0 ? 1 : 0) : y), flat.getZ());
			if (state.getBlock() instanceof BarrelBlock) {
				// thrown out of her as she struck
				int x = flat.getX() + Mth.nextInt(random, -THROWN, THROWN), z = flat.getZ() + Mth.nextInt(random, -THROWN, THROWN);
				if (!Loaded.made(level, new BlockPos(x, 0, z))) {
					continue;
				}
				at = new BlockPos(x, floor(level, x, z), z);
				state = Blocks.BARREL.defaultBlockState();
			}
			BlockState there = level.getBlockState(at);
			if (!there.isAir() && !there.canBeReplaced()) {
				continue;
			}
			level.setBlock(at, state, Block.UPDATE_CLIENTS);
			if (state.getBlock() instanceof ChestBlock) {
				chests.add(at);
			} else if (state.getBlock() instanceof BarrelBlock) {
				barrels.add(at);
			}
		}
		for (BlockPos b : barrels) {
			if (level.getBlockEntity(b) instanceof Container box) {
				stores(box, random);
			}
		}
		String name = Pirates.navyShip(random);
		for (BlockPos c : chests) {
			if (level.getBlockEntity(c) instanceof Container box) {
				box.setItem(0, log(name, fromF, toF, here));
				box.setItem(1 + random.nextInt(box.getContainerSize() - 1), new ItemStack(Items.GOLD_NUGGET, 6 + random.nextInt(12)));
				if (box instanceof BlockEntity be) {
					be.setChanged();
				}
			}
		}
		int dead = Mth.nextInt(random, DEAD_LEAST, DEAD_MOST);
		for (int i = 0; i < dead; i++) {
			int x = ground.getX() + Mth.nextInt(random, -5, 5), z = ground.getZ() + Mth.nextInt(random, -5, 5);
			if (Loaded.made(level, new BlockPos(x, 0, z))) {
				body(level, new BlockPos(x, floor(level, x, z), z), random);
			}
		}
		List<Long> laid = new ArrayList<>(level.getAttachedOrElse(LAID, List.of()));
		laid.add(ground.asLong());
		while (laid.size() > 256) {
			laid.removeFirst();
		}
		level.setAttached(LAID, List.copyOf(laid));
		JaymosKingdoms.LOGGER.info("A downed airship, the {}, lies at {} (bound for {})", name, ground, toF.name());
	}

	/**
	 * Where she was out of and bound for: of the steam towns, the two whose route passes nearest {@code here} (one standing:
	 * out of it, bound for the settlement nearest here); none, two settlements of any kind; none at all, nulls.
	 */
	private static @Nullable Faction[] bound(ServerLevel level, Vec3 here, RandomSource random) {
		List<Faction> towns = steamTowns(level);
		if (towns.size() >= 2) {
			Faction[] best = null;
			double bestD = Double.MAX_VALUE;
			for (Faction a : towns) {
				for (Faction b : towns) {
					double d = a == b ? Double.MAX_VALUE : offLine(Vec3.atCenterOf(a.seat()), Vec3.atCenterOf(b.seat()), here);
					if (d < bestD) {
						bestD = d;
						best = random.nextBoolean() ? new Faction[] {a, b} : new Faction[] {b, a};
					}
				}
			}
			return best;
		}
		List<Faction> settlements = new ArrayList<>();
		for (Faction f : Factions.all(level.getServer())) {
			if (!f.fallen() && f.type() != FactionType.BARBARIAN && !towns.contains(f)) {
				settlements.add(f);
			}
		}
		settlements.sort(java.util.Comparator.comparingDouble(f -> flat(Vec3.atCenterOf(f.seat()), here)));
		if (towns.size() == 1) {
			return new Faction[] {towns.getFirst(), settlements.isEmpty() ? towns.getFirst() : settlements.getFirst()};
		}
		if (settlements.size() >= 2) {
			return new Faction[] {settlements.get(1 + random.nextInt(settlements.size() - 1)), settlements.getFirst()};
		}
		return new Faction[] {null, null};
	}

	/** One of her dead at {@code at}: the body a crewman would leave (Kitchen's), or his bones and skull without. */
	private static void body(ServerLevel level, BlockPos at, RandomSource random) {
		Villager crewman = net.minecraft.world.entity.EntityTypes.VILLAGER.create(level, EntitySpawnReason.STRUCTURE);
		if (crewman != null) {
			crewman.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, random.nextFloat() * 360.0F, 0.0F);
			boolean laid = KitchenCompat.corpseOf(crewman) != null;
			crewman.discard();
			if (laid) {
				return;
			}
		}
		BlockState there = level.getBlockState(at);
		if (there.isAir() || there.canBeReplaced()) {
			level.setBlock(at, Blocks.SKELETON_SKULL.defaultBlockState().setValue(SkullBlock.ROTATION, random.nextInt(16)), Block.UPDATE_CLIENTS);
		}
		BlockPos by = at.relative(Direction.Plane.HORIZONTAL.getRandomDirection(random));
		BlockState beside = level.getBlockState(by);
		if ((beside.isAir() || beside.canBeReplaced()) && level.getBlockState(by.below()).isFaceSturdy(level, by.below(), Direction.UP)) {
			level.setBlock(by, Blocks.BONE_BLOCK.defaultBlockState(), Block.UPDATE_CLIENTS);
		}
	}

	/** Her stores, what's left of them: coal and coke for her boilers' town, iron, rope, food spoiled and not. */
	private static void stores(Container box, RandomSource random) {
		List<ItemStack> loot = new ArrayList<>();
		loot.add(new ItemStack(Items.COAL, 4 + random.nextInt(12)));
		loot.add(new ItemStack(Items.IRON_INGOT, 1 + random.nextInt(5)));
		loot.add(new ItemStack(Items.STRING, 2 + random.nextInt(8)));
		if (random.nextBoolean()) {
			loot.add(new ItemStack(Items.COPPER_INGOT, 2 + random.nextInt(8)));
		}
		if (random.nextBoolean()) {
			loot.add(new ItemStack(Items.BREAD, 1 + random.nextInt(4)));
		}
		if (random.nextFloat() < 0.4F) {
			loot.add(new ItemStack(Items.GUNPOWDER, 1 + random.nextInt(4)));
		}
		if (random.nextFloat() < 0.3F) {
			loot.add(new ItemStack(Sailing.ROUND_SHOT, 1 + random.nextInt(3)));
		}
		loot.add(new ItemStack(Items.ROTTEN_FLESH, 1 + random.nextInt(3)));
		for (int i = 0; i < loot.size() && i < box.getContainerSize(); i++) {
			box.setItem(random.nextInt(box.getContainerSize()), loot.get(i));
		}
		if (box instanceof BlockEntity be) {
			be.setChanged();
		}
	}

	/** Her log: out of {@code from}, bound for {@code to}; where that lies from {@code here}; her last entries. */
	private static ItemStack log(String name, @Nullable Faction from, @Nullable Faction to, Vec3 here) {
		List<Filterable<Component>> pages;
		if (from == null || to == null) {
			// (nowhere standing to have been bound for: the steam towns she flew between are gone)
			pages = List.of(Filterable.passThrough(Component.translatable("book.jaymoskingdoms.sky_wreck.lost", name)),
				Filterable.passThrough(Component.translatable("book.jaymoskingdoms.sky_wreck.page3")));
		} else {
			Vec3 there = Vec3.atCenterOf(to.seat());
			pages = List.of(
				Filterable.passThrough(Component.translatable("book.jaymoskingdoms.sky_wreck.page1", name, from.name(), to.name())),
				Filterable.passThrough(Component.translatable("book.jaymoskingdoms.sky_wreck.page2", to.name(), Directions.heading(here, there),
					Directions.distance(flat(here, there)))),
				Filterable.passThrough(Component.translatable("book.jaymoskingdoms.sky_wreck.page3")));
		}
		ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
		book.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(Filterable.passThrough("Log of the " + name), name, 0, pages, true));
		return book;
	}
}
