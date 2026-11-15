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

/**
 * Downed airship wrecks (the medieval picks' vp-wrecks): now and then a player in the mountains or a forest happens on a
 * balloon ship that came down there, one of the steam towns' (as {@link TreasureWrecks} has players at sea happen on a wreck).
 * Her deck lies broken over the ground, timbers lost, her bag slack and torn across it; her stores are scattered round her,
 * barrels burst open; her crew lie dead by her (Kitchen's bodies, with Jaymo's Kitchen; bones and a skull without). In her
 * chest is her log, which names the town she was out of and the one she was bound for, and which way it lies from here and
 * how far. Only once a steam town stands (there are airships up to come down).
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
		for (ServerPlayer player : level.players()) {
			if (player.isSpectator() || random.nextFloat() >= WRECK_CHANCE || !wild(level.getBiome(player.blockPosition()))) {
				continue;
			}
			happenOn(level, player, random);
		}
	}

	private static boolean wild(Holder<Biome> biome) {
		return biome.is(BiomeTags.IS_MOUNTAIN) || biome.is(BiomeTags.IS_FOREST) || biome.is(BiomeTags.IS_TAIGA) || biome.is(BiomeTags.IS_HILL);
	}

	/** {@code player} happens on a downed airship some way off (none other near). */
	private static void happenOn(ServerLevel level, ServerPlayer player, RandomSource random) {
		List<SteamTowns.Town> towns = SteamTowns.all(level);
		if (towns.isEmpty()) {
			return;
		}
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
			lay(level, ground, towns, random);
			return;
		}
	}

	/** The ground under {@code x, z}: the first block over the land there (through leaves and plants). */
	private static int floor(ServerLevel level, int x, int z) {
		return level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
	}

	/** A downed airship at {@code ground}: broken, her stores thrown about, her dead by her, her log in her chest. */
	private static void lay(ServerLevel level, BlockPos ground, List<SteamTowns.Town> towns, RandomSource random) {
		MinecraftServer server = level.getServer();
		// where she was out of and bound for: the nearest steam town she was making for, and another she'd left (or the same)
		Vec3 here = Vec3.atCenterOf(ground);
		SteamTowns.Town to = towns.get(random.nextInt(towns.size()));
		SteamTowns.Town from = towns.get(random.nextInt(towns.size()));
		Faction toF = Factions.get(server, to.id()), fromF = Factions.get(server, from.id());
		if (toF == null || fromF == null) {
			return;
		}
		DyeColor colour = fromF.color();
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
				box.setItem(0, log(name, fromF, toF, here, Vec3.atCenterOf(toF.seat())));
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
	private static ItemStack log(String name, Faction from, Faction to, Vec3 here, Vec3 there) {
		double d = Math.sqrt((there.x - here.x) * (there.x - here.x) + (there.z - here.z) * (there.z - here.z));
		List<Filterable<Component>> pages = List.of(
			Filterable.passThrough(Component.translatable("book.jaymoskingdoms.sky_wreck.page1", name, from.name(), to.name())),
			Filterable.passThrough(Component.translatable("book.jaymoskingdoms.sky_wreck.page2", to.name(), Directions.heading(here, there),
				Directions.distance(d))),
			Filterable.passThrough(Component.translatable("book.jaymoskingdoms.sky_wreck.page3")));
		ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
		book.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(Filterable.passThrough("Log of the " + name), name, 0, pages, true));
		return book;
	}
}
