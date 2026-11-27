package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmlandBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import org.jspecify.annotations.Nullable;

/**
 * Village mods patch 8, the swamp goblins (Andy's picks rb_swamp, rb_gardener, rb_hedgewitch, rb_trades, rb_home; the
 * ideas from Ribbits, whose code and art are not used: Andy, "our goblins should replace these frogs").
 * <ul>
 * <li><b>Stilt villages</b> ({@link #build}): a goblin village that rises in a swamp or mangrove swamp stands over the water
 * on stilts. Every building of an ordinary goblin village ({@link GoblinBuildings}) sits on its own deck of mossy oak
 * planks on spruce stilts, joined to the fire's deck by plank walks; its mushroom domes are toadstool caps, its lanterns
 * swamp lanterns, swamp lanterns hang from the trees and big lily pads float about it ({@link SwampBlocks}). There is no
 * palisade: the water is its wall. What it raises as it grows goes up on decks too ({@link #raise}). A village is a
 * swamp one when its fire stands on mossy oak planks ({@link #is}), so nothing new is saved.</li>
 * <li><b>Gardeners</b> ({@link #gardener}): one goblin in four, in every goblin village, tends what grows round it: the
 * crops, mushrooms and swamp plants near it grow a little faster, and dry farmland it passes is watered. A gardener a
 * player has housed ({@link #gift}) tends the crops round its new home by day, so goblins who like you work your farm.</li>
 * <li><b>The hedge witch</b> ({@link #witch}): one goblin in each swamp village now and then blesses a player the village
 * spares ({@link GreenMarket#welcome}) who comes near her: a burst of speed, night sight, or a little luck.</li>
 * <li><b>Swamp trades</b> ({@link #goods}): a swamp village's store fills a little each day with toadstool caps, lily
 * pads, swamp lanterns, umbrella leaves, eels and the odd gourd shaker, and its market merchants sell what it can spare
 * for bent ingots like the rest of its goods ({@link GreenMarket}).</li>
 * <li><b>Home at night</b> ({@link #homes}): in every goblin village, at bedtime the goblins go back to their huts (each to
 * its own, by who it is), and come out again in the morning. Hand one an amethyst shard or a gold nugget, if the village
 * spares you, and it takes the place you stand as its home from then on.</li>
 * </ul>
 */
public final class SwampGoblins {
	// ---- tuning ----
	/** The decks stand this high over the highest ground or water under the village. */
	static final int STILT_HIGH = 2;
	/** The most the ground under a swamp village may rise and fall (blocks). */
	static final int MAX_SPREAD = 8;
	/** How high a building's room is cleared of trees above its deck. */
	static final int CLEAR_HEIGHT = 10;
	/** The fire's deck's half-width; a walk's width. */
	static final int FIRE_DECK = 4, WALK_WIDTH = 2;
	/** Lily pads and lanterns put about a new village: tries for each. */
	static final int PAD_TRIES = 60, LANTERN_TRIES = 24, LEAF_TRIES = 20;
	/** One goblin in this many is a gardener; a gardener's reach (blocks) and how many spots it tends each round. */
	static final int GARDENER_IN = 4, GARDEN_REACH = 4, GARDEN_SPOTS = 6;
	/** The witch blesses a player this near her, at most this often each (ticks), this often each round she can. */
	static final double BLESS_REACH = 6.0;
	static final long BLESS_GAP = 20L * 60 * 3;
	static final float BLESS_CHANCE = 0.5F;
	/** A blessing's length (ticks). */
	static final int BLESS_TICKS = 20 * 90;
	/** A goblin's gifted home must be within this of its village's fire (blocks). */
	static final double GIFT_REACH = 96.0;
	/** What a swamp village's store takes in each day: toadstool caps, lily pads, lanterns, umbrella leaves, eels; a shaker every so many days. */
	static final int DAY_CAPS = 2, DAY_PADS = 2, DAY_LANTERNS = 1, DAY_LEAVES = 1, DAY_EELS = 3, SHAKER_DAYS = 3;

	/** The witch's tag; a gifted home's tag (with the place after it). */
	static final String WITCH = "jaymoskingdoms.hedge_witch", HOME = "jaymoskingdoms.goblin_home.";

	/** Goblins sent home for the night (so the morning lets them out again). */
	private static final Set<UUID> ABED = new HashSet<>();
	/** Player → when the witch last blessed them. */
	private static final Map<UUID, Long> BLESSED = new HashMap<>();

	private SwampGoblins() {
	}

	static void register() {
		UseEntityCallback.EVENT.register(SwampGoblins::gift);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			ABED.clear();
			BLESSED.clear();
		});
	}

	// ---- where ---------------------------------------------------------------------------------------------------------

	/** {@code at} is in a swamp or a mangrove swamp. */
	static boolean swamp(ServerLevel level, BlockPos at) {
		Holder<Biome> biome = level.getBiome(at);
		return biome.is(Biomes.SWAMP) || biome.is(Biomes.MANGROVE_SWAMP);
	}

	/** The village whose fire is at {@code fire} stands on the swamp's decks. */
	static boolean is(ServerLevel level, BlockPos fire) {
		return level.isLoaded(fire) && level.getBlockState(fire.below()).is(SwampBlocks.MOSSY_PLANKS);
	}

	/**
	 * The floor (the fire's height, a block over the decks) for a stilt village round {@code center}: {@link #STILT_HIGH}
	 * over the highest ground or water under it, or {@link Integer#MIN_VALUE} if it can't stand there (unloaded, built on,
	 * or the ground too uneven).
	 */
	static int floor(ServerLevel level, BlockPos center) {
		int radius = GoblinBuildings.CLEAR_RADIUS;
		int low = Integer.MAX_VALUE, high = Integer.MIN_VALUE, top = Integer.MIN_VALUE;
		for (int dx = -radius; dx <= radius; dx += 2) {
			for (int dz = -radius; dz <= radius; dz += 2) {
				if (dx * dx + dz * dz > radius * radius) {
					continue;
				}
				BlockPos column = center.offset(dx, 0, dz);
				if (!com.jaymoskingdoms.Loaded.made(level, column)) {
					return Integer.MIN_VALUE;
				}
				int ground = bottom(level, column);
				BlockState state = level.getBlockState(column.atY(ground));
				if (!Storehouse.ground(state) && !state.is(Blocks.MUD) && !state.is(Blocks.MANGROVE_ROOTS) && !state.is(Blocks.MUDDY_MANGROVE_ROOTS)) {
					return Integer.MIN_VALUE;
				}
				low = Math.min(low, ground);
				high = Math.max(high, ground);
				top = Math.max(top, surface(level, column));
			}
		}
		if (high == Integer.MIN_VALUE || high - low > MAX_SPREAD) {
			return Integer.MIN_VALUE;
		}
		return top + STILT_HIGH + 1;
	}

	/** The ground at {@code column} under its water, trees and plants (the block's y). */
	private static int bottom(ServerLevel level, BlockPos column) {
		int y = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column).getY() - 1;
		while (y > level.getMinY() + 1) {
			BlockState s = level.getBlockState(column.atY(y));
			if (s.isAir() || !s.getFluidState().isEmpty() || s.is(BlockTags.LOGS) || s.is(BlockTags.LEAVES) || s.canBeReplaced()
				|| s.is(Blocks.MANGROVE_ROOTS) && level.getBlockState(column.atY(y - 1)).getFluidState().isEmpty() == false) {
				y--;
				continue;
			}
			return y;
		}
		return y;
	}

	/** The top of the ground or the water at {@code column} (the block's y), through trees and plants. */
	private static int surface(ServerLevel level, BlockPos column) {
		int y = bottom(level, column);
		while (!level.getFluidState(column.atY(y + 1)).isEmpty()) {
			y++;
		}
		return y;
	}

	// ---- building --------------------------------------------------------------------------------------------------------

	/** An ordinary goblin village's block as the swamp's goblins build it. */
	static BlockState swampy(BlockState s) {
		if (s.is(Blocks.RED_MUSHROOM_BLOCK)) {
			return SwampBlocks.RED_CAP.defaultBlockState();
		}
		if (s.is(Blocks.BROWN_MUSHROOM_BLOCK)) {
			return SwampBlocks.BROWN_CAP.defaultBlockState();
		}
		if (s.is(Blocks.MUSHROOM_STEM)) {
			return SwampBlocks.STEM.defaultBlockState();
		}
		if (s.is(Blocks.SPRUCE_PLANKS)) {
			return SwampBlocks.MOSSY_PLANKS.defaultBlockState();
		}
		if (s.is(Blocks.LANTERN)) {
			return SwampBlocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, s.getValue(LanternBlock.HANGING));
		}
		return s;
	}

	/** Builds a whole stilt village round {@code center}, its fire at {@code floor}, its front toward {@code gate}. */
	static GoblinBuildings.Layout build(ServerLevel level, BlockPos center, int floor, Direction gate) {
		BlockPos fire = new BlockPos(center.getX(), floor, center.getZ());
		RandomSource random = level.getRandom();
		CampBuildings.Placer village = new CampBuildings.Placer(fire, gate, 0);
		// the fire's deck, cleared above
		for (int dx = -FIRE_DECK; dx <= FIRE_DECK; dx++) {
			for (int dz = -FIRE_DECK; dz <= FIRE_DECK; dz++) {
				BlockPos at = fire.offset(dx, 0, dz);
				clearAbove(level, at);
				deck(level, at.below(), Math.abs(dx) == FIRE_DECK && Math.abs(dz) == FIRE_DECK || dx == 0 && Math.abs(dz) == FIRE_DECK
					|| dz == 0 && Math.abs(dx) == FIRE_DECK);
			}
		}
		level.setBlock(fire, Blocks.CAMPFIRE.defaultBlockState(), Block.UPDATE_CLIENTS);
		BlockState bench = Blocks.MOSSY_COBBLESTONE_SLAB.defaultBlockState();
		for (int[] b : new int[][] {{0, -2}, {-2, 0}, {2, 0}, {-2, -2}, {2, -2}}) {
			level.setBlock(village.at(b[0], 0, b[1]), bench, Block.UPDATE_CLIENTS);
		}
		for (int[] c : new int[][] {{-FIRE_DECK, -FIRE_DECK}, {FIRE_DECK, -FIRE_DECK}, {-FIRE_DECK, FIRE_DECK}, {FIRE_DECK, FIRE_DECK}}) {
			level.setBlock(fire.offset(c[0], 0, c[1]), SwampBlocks.LANTERN.defaultBlockState(), Block.UPDATE_CLIENTS);
		}
		List<BlockPos> storage = new ArrayList<>();
		List<IllagerBuildings.Spot> spots = new ArrayList<>(List.of(GoblinBuildings.GREAT_HUT, GoblinBuildings.JUNK, GoblinBuildings.TAVERN,
			GoblinBuildings.LOOKOUT, GoblinBuildings.STEWPOT));
		spots.addAll(GoblinBuildings.START);
		for (IllagerBuildings.Spot s : spots) {
			List<BlockPos> containers = raise(level, s, fire, gate);
			if (s == GoblinBuildings.JUNK) {
				storage.addAll(containers);
			}
		}
		decorate(level, fire, random);
		CampBuildings.Placer hall = GoblinBuildings.GREAT_HUT.placer(fire, gate);
		GoblinBuildings.stockHoard(level, hall.at(GoblinBuildings.HOARD_CHEST), random);
		return new GoblinBuildings.Layout(fire, storage, GoblinBuildings.JUNK.placer(fire, gate).at(GoblinBuildings.JUNK_DEPOT),
			hall.at(GoblinBuildings.THRONE), GoblinBuildings.TAVERN.placer(fire, gate).at(GoblinBuildings.TAVERN_SEAT),
			GoblinBuildings.LOOKOUT.placer(fire, gate).at(GoblinBuildings.LOOKOUT_SPOT));
	}

	/**
	 * Raises one of the village's buildings at its spot, swamp fashion: the trees in its room cut back, a deck under it on
	 * stilts, a plank walk from the fire's deck to its door, then the building in the swamp's blocks. Its containers.
	 */
	static List<BlockPos> raise(ServerLevel level, IllagerBuildings.Spot spot, BlockPos fire, Direction gate) {
		CampBuildings.Placer placer = spot.placer(fire, gate);
		Blueprint design = spot.design().get();
		int w = Math.max(1, design.width()), d = Math.max(1, design.depth());
		for (int x = -1; x <= w; x++) {
			for (int z = -1; z <= d; z++) {
				BlockPos at = placer.at(x, 0, z);
				clearAbove(level, at);
				boolean corner = (x == -1 || x == w) && (z == -1 || z == d);
				deck(level, at.below(), corner || (x == -1 || x == w) && z % 4 == 1 || (z == -1 || z == d) && x % 4 == 1);
			}
		}
		walk(level, fire, placer.at(w / 2, 0, -2));
		List<BlockPos> containers = new ArrayList<>();
		for (Blueprint.Placement p : design.blocks()) {
			BlockPos at = placer.at(p.offset());
			BlockState state = placer.turn(swampy(p.state()));
			level.setBlock(at, state, Block.UPDATE_CLIENTS);
			if (state.is(Blocks.CHEST) || state.is(Blocks.BARREL)) {
				containers.add(at.immutable());
			}
		}
		for (Blueprint.Placement p : design.blocks()) {
			BlockPos at = placer.at(p.offset());
			BlockState state = level.getBlockState(at);
			if (state.is(BlockTags.FENCES)) {
				level.setBlock(at, Block.updateFromNeighbourShapes(state, level, at), Block.UPDATE_CLIENTS);
			}
		}
		return containers;
	}

	/** The room above a deck cell cleared: trees, plants and whatever stood there. */
	private static void clearAbove(ServerLevel level, BlockPos at) {
		for (int y = 0; y < CLEAR_HEIGHT; y++) {
			BlockPos p = at.above(y);
			if (!level.getBlockState(p).isAir()) {
				level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
			}
		}
	}

	/** A deck plank at {@code at}; with {@code stilt}, a spruce stilt down from it to the ground. */
	private static void deck(ServerLevel level, BlockPos at, boolean stilt) {
		level.setBlock(at, SwampBlocks.MOSSY_PLANKS.defaultBlockState(), Block.UPDATE_CLIENTS);
		if (stilt) {
			stilt(level, at.below());
		}
	}

	private static void stilt(ServerLevel level, BlockPos from) {
		BlockPos p = from;
		for (int i = 0; i < 24 && p.getY() > level.getMinY(); i++, p = p.below()) {
			BlockState s = level.getBlockState(p);
			if (!s.isAir() && s.getFluidState().isEmpty() && !s.canBeReplaced() && !s.is(BlockTags.LEAVES)) {
				return;
			}
			level.setBlock(p, Blocks.SPRUCE_LOG.defaultBlockState(), Block.UPDATE_CLIENTS);
		}
	}

	/** A plank walk {@link #WALK_WIDTH} wide from the fire's deck to {@code door}: along x, then along z, on stilts every few planks. */
	private static void walk(ServerLevel level, BlockPos fire, BlockPos door) {
		int y = fire.getY() - 1;
		int x = fire.getX(), z = fire.getZ();
		int n = 0;
		while (x != door.getX() || z != door.getZ()) {
			if (x != door.getX()) {
				x += Integer.signum(door.getX() - x);
			} else {
				z += Integer.signum(door.getZ() - z);
			}
			for (int w = 0; w < WALK_WIDTH; w++) {
				BlockPos at = x != door.getX() || z == fire.getZ() ? new BlockPos(x, y, z + w) : new BlockPos(x + w, y, z);
				BlockState s = level.getBlockState(at);
				if (s.isAir() || !s.getFluidState().isEmpty() || s.canBeReplaced() || s.is(BlockTags.LEAVES)) {
					level.setBlock(at, SwampBlocks.MOSSY_PLANKS.defaultBlockState(), Block.UPDATE_CLIENTS);
					clearAbove(level, at.above());
					if (n % 4 == 0) {
						stilt(level, at.below());
					}
				}
			}
			n++;
		}
	}

	/** Big lily pads on the open water, swamp lanterns hung under the trees, umbrella leaves on the banks. */
	private static void decorate(ServerLevel level, BlockPos fire, RandomSource random) {
		int r = GoblinBuildings.RING_RADIUS;
		for (int i = 0; i < PAD_TRIES; i++) {
			BlockPos column = fire.offset(random.nextInt(2 * r + 1) - r, 0, random.nextInt(2 * r + 1) - r);
			BlockPos top = level.getHeightmapPos(Heightmap.Types.WORLD_SURFACE, column);
			if (level.getBlockState(top.below()).is(Blocks.WATER) && level.getBlockState(top).isAir()) {
				level.setBlock(top, (random.nextInt(3) == 0 ? Blocks.LILY_PAD : SwampBlocks.BIG_LILY_PAD).defaultBlockState(), Block.UPDATE_CLIENTS);
			}
		}
		for (int i = 0; i < LANTERN_TRIES; i++) {
			BlockPos column = fire.offset(random.nextInt(2 * r + 1) - r, 0, random.nextInt(2 * r + 1) - r);
			for (int y = 1; y <= CLEAR_HEIGHT + 4; y++) {
				BlockPos p = column.atY(fire.getY() + y);
				if (level.getBlockState(p).is(BlockTags.LEAVES)) {
					BlockPos under = p.below();
					if (level.getBlockState(under).isAir()) {
						level.setBlock(under, SwampBlocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true), Block.UPDATE_CLIENTS);
					}
					break;
				}
			}
		}
		for (int i = 0; i < LEAF_TRIES; i++) {
			BlockPos column = fire.offset(random.nextInt(2 * r + 1) - r, 0, random.nextInt(2 * r + 1) - r);
			BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column);
			BlockState leaf = SwampBlocks.UMBRELLA_LEAF.defaultBlockState();
			if (level.getBlockState(top).isAir() && top.getY() < fire.getY() - 1 && leaf.canSurvive(level, top)) {
				level.setBlock(top, leaf, Block.UPDATE_CLIENTS);
			}
		}
	}

	// ---- the village's life ------------------------------------------------------------------------------------------------

	/** Every {@link Camps#CHECK_TICKS} while goblin village {@code village} is loaded (any goblin village): gardeners, homes, the witch. */
	static void tick(ServerLevel level, Faction village, Camps.CampData data) {
		if (village.fallen()) {
			return;
		}
		AABB box = new AABB(data.center()).inflate(GIFT_REACH);
		List<Worker> goblins = Workers.near(level, box, x -> x.factionId() == village.id() && x.role() == Goblins.GOBLIN && x.mob().isAlive());
		homes(level, village, data, goblins);
		for (Worker w : goblins) {
			if (gardener(w.mob()) && w.mob().getTarget() == null && !Downed.isDowned(w.mob())) {
				tend(level, w.mob());
			}
		}
		if (is(level, data.center())) {
			bless(level, village, data, goblins);
		}
	}

	/** One goblin in {@link #GARDENER_IN} is a gardener (by who it is). */
	static boolean gardener(Entity e) {
		return Math.floorMod(e.getUUID().getLeastSignificantBits() >>> 5, (long)GARDENER_IN) == 0L;
	}

	/** A gardener's round: a few spots round it, each grown on a step if it's something that grows; dry farmland watered. */
	private static void tend(ServerLevel level, PathfinderMob goblin) {
		RandomSource random = level.getRandom();
		BlockPos at = goblin.blockPosition();
		boolean any = false;
		for (int i = 0; i < GARDEN_SPOTS; i++) {
			BlockPos p = at.offset(random.nextInt(2 * GARDEN_REACH + 1) - GARDEN_REACH, random.nextInt(3) - 1,
				random.nextInt(2 * GARDEN_REACH + 1) - GARDEN_REACH);
			BlockState s = level.getBlockState(p);
			if (s.getBlock() instanceof FarmlandBlock && s.getValue(FarmlandBlock.MOISTURE) < FarmlandBlock.MAX_MOISTURE) {
				level.setBlock(p, s.setValue(FarmlandBlock.MOISTURE, FarmlandBlock.MAX_MOISTURE), Block.UPDATE_CLIENTS);
				any = true;
			} else if ((s.getBlock() instanceof CropBlock || s.getBlock() instanceof BonemealableBlock && !s.is(BlockTags.DIRT)
				&& !s.is(Blocks.GRASS_BLOCK)) && s.isRandomlyTicking()) {
				s.randomTick(level, p, random);
				any = true;
			}
			if (any && i == 0) {
				level.sendParticles(ParticleTypes.HAPPY_VILLAGER, p.getX() + 0.5, p.getY() + 0.6, p.getZ() + 0.5, 2, 0.25, 0.2, 0.25, 0.0);
			}
		}
	}

	/** At bedtime every goblin of the village to its hut (or the home a player gave it), out again in the morning. */
	private static void homes(ServerLevel level, Faction village, Camps.CampData data, List<Worker> goblins) {
		MinecraftServer server = level.getServer();
		boolean night = Homes.bedtime(level);
		List<BlockPos> huts = huts(data);
		int radius = Hordes.homeRadius(server, village.id(), GoblinBuildings.HOME_RADIUS);
		for (Worker w : goblins) {
			PathfinderMob m = w.mob();
			if (GreenMarket.merchant(m) || GoblinBands.playing(m.getUUID()) || m.getTarget() != null) {
				continue;
			}
			BlockPos gifted = giftedHome(m, data.center());
			if (night) {
				BlockPos bed = gifted != null ? gifted : huts.isEmpty() ? null : huts.get((int)Math.floorMod(m.getUUID().getMostSignificantBits(), (long)huts.size()));
				if (bed != null) {
					m.setHomeTo(bed, 1);
					ABED.add(m.getUUID());
				}
			} else if (ABED.remove(m.getUUID()) || gifted != null) {
				// by day: about the village, or (a gardener a player housed) about the farm round its new home
				if (gifted != null && gardener(m)) {
					m.setHomeTo(gifted, GARDEN_REACH + 4);
				} else {
					m.setHomeTo(data.center(), radius);
				}
			}
		}
	}

	/** Where the village's huts' floors are, the ones standing from its founding and those it has raised since. */
	private static List<BlockPos> huts(Camps.CampData data) {
		List<BlockPos> out = new ArrayList<>();
		List<IllagerBuildings.Spot> all = new ArrayList<>(GoblinBuildings.START);
		all.addAll(GoblinBuildings.GROWTH.subList(0, Math.min(data.built(), GoblinBuildings.GROWTH.size())));
		for (IllagerBuildings.Spot s : all) {
			if (s.id().startsWith("goblin_hut")) {
				out.add(s.placer(data.center(), data.gate()).at(2, 0, 2));
			}
		}
		return out;
	}

	private static @Nullable BlockPos giftedHome(Entity goblin, BlockPos fire) {
		for (String tag : goblin.entityTags()) {
			if (tag.startsWith(HOME)) {
				String[] xyz = tag.substring(HOME.length()).split("_");
				try {
					BlockPos home = new BlockPos(Integer.parseInt(xyz[0]), Integer.parseInt(xyz[1]), Integer.parseInt(xyz[2]));
					return home.distSqr(fire) < GIFT_REACH * GIFT_REACH ? home : null;
				} catch (RuntimeException e) {
					return null;
				}
			}
		}
		return null;
	}

	/** A player hands a goblin of a village that spares them an amethyst shard or a gold nugget: it takes where they stand as its home. */
	private static InteractionResult gift(Player player, Level level, InteractionHand hand, Entity target, @Nullable EntityHitResult hit) {
		ItemStack held = player.getItemInHand(hand);
		if (!(target instanceof Worker w) || w.role() != Goblins.GOBLIN || !held.is(Items.AMETHYST_SHARD) && !held.is(Items.GOLD_NUGGET)) {
			return InteractionResult.PASS;
		}
		if (!(level instanceof ServerLevel server) || !(player instanceof ServerPlayer sp)) {
			return InteractionResult.SUCCESS;
		}
		Faction village = Factions.get(server.getServer(), w.factionId());
		Camps.CampData data = village == null ? null : Camps.data(server.getServer(), village.id());
		if (village == null || data == null || !data.goblin() || !GreenMarket.welcome(sp, village)) {
			return InteractionResult.PASS;
		}
		BlockPos home = player.blockPosition();
		if (home.distSqr(data.center()) >= GIFT_REACH * GIFT_REACH) {
			sp.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.goblin_home.far"));
			return InteractionResult.SUCCESS;
		}
		for (String tag : List.copyOf(target.entityTags())) {
			if (tag.startsWith(HOME)) {
				target.removeTag(tag);
			}
		}
		target.addTag(HOME + home.getX() + "_" + home.getY() + "_" + home.getZ());
		if (!player.hasInfiniteMaterials()) {
			held.shrink(1);
		}
		server.sendParticles(ParticleTypes.HEART, target.getX(), target.getY() + target.getBbHeight() + 0.3, target.getZ(), 3, 0.3, 0.2, 0.3, 0.0);
		sp.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.goblin_home.moved", target.getDisplayName()));
		return InteractionResult.SUCCESS;
	}

	/** {@code e} is a swamp village's hedge witch. */
	static boolean witch(Entity e) {
		return e.entityTags().contains(WITCH);
	}

	/** The witch kept (one of the village's goblins takes it up if she's missing), and her blessings. */
	private static void bless(ServerLevel level, Faction village, Camps.CampData data, List<Worker> goblins) {
		PathfinderMob witch = null;
		for (Worker w : goblins) {
			if (witch(w.mob())) {
				witch = w.mob();
				break;
			}
		}
		if (witch == null) {
			double best = Double.MAX_VALUE;
			for (Worker w : goblins) {
				double d = w.mob().distanceToSqr(data.center().getX() + 0.5, data.center().getY(), data.center().getZ() + 0.5);
				if (!GreenMarket.merchant(w.mob()) && d < best) {
					best = d;
					witch = w.mob();
				}
			}
			if (witch == null) {
				return;
			}
			witch.addTag(WITCH);
		}
		long now = level.getGameTime();
		RandomSource random = level.getRandom();
		for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class, witch.getBoundingBox().inflate(BLESS_REACH))) {
			if (now - BLESSED.getOrDefault(p.getUUID(), Long.MIN_VALUE / 2) < BLESS_GAP || random.nextFloat() > BLESS_CHANCE
				|| !GreenMarket.welcome(p, village)) {
				continue;
			}
			BLESSED.put(p.getUUID(), now);
			int which = random.nextInt(3);
			p.addEffect(new MobEffectInstance(which == 0 ? MobEffects.SPEED : which == 1 ? MobEffects.NIGHT_VISION : MobEffects.LUCK, BLESS_TICKS, 0));
			Fx.ring(level, p.position().add(0.0, 0.1, 0.0), 0.9F, 0x9AD04C);
			Fx.burst(level, p.getX(), p.getY() + 1.0, p.getZ(), 0xB8E65E, 0.4F, 8, 0.3, 0.5, 0.3);
			level.playSound(null, witch.getX(), witch.getY(), witch.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.NEUTRAL, 1.0F, 1.3F);
			witch.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
			p.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.hedge_witch." + which));
		}
	}

	// ---- trade -----------------------------------------------------------------------------------------------------------

	/** What a swamp village's market sells besides a goblin village's usual goods. */
	static List<GreenMarket.Good> goods() {
		return List.of(new GreenMarket.Good(SwampBlocks.RED_CAP.asItem(), 4, 3, 4, 16), new GreenMarket.Good(SwampBlocks.BROWN_CAP.asItem(), 4, 3, 4, 16),
			new GreenMarket.Good(SwampBlocks.BIG_LILY_PAD.asItem(), 4, 2, 2, 12), new GreenMarket.Good(SwampBlocks.LANTERN.asItem(), 2, 5, 2, 8),
			new GreenMarket.Good(SwampBlocks.UMBRELLA_LEAF.asItem(), 2, 2, 2, 8), new GreenMarket.Good(SwampBlocks.EEL, 3, 3, 3, 15),
			new GreenMarket.Good(SwampBlocks.SHAKER, 1, 6, 0, 2));
	}

	/** {@code days} of a swamp village's gathering into its store: toadstools, lily pads, lanterns, leaves, eels, and now and then a shaker. */
	static void day(ServerLevel level, Faction village, int days) {
		if (!Storage.loaded(level, village)) {
			return;
		}
		Map<Item, Integer> take = Map.of(SwampBlocks.RED_CAP.asItem(), DAY_CAPS * days, SwampBlocks.BROWN_CAP.asItem(), DAY_CAPS * days,
			SwampBlocks.BIG_LILY_PAD.asItem(), DAY_PADS * days, SwampBlocks.LANTERN.asItem(), DAY_LANTERNS * days,
			SwampBlocks.UMBRELLA_LEAF.asItem(), DAY_LEAVES * days, SwampBlocks.EEL, DAY_EELS * days);
		for (GreenMarket.Good g : goods()) {
			int add = g.item() == SwampBlocks.SHAKER ? (level.getRandom().nextInt(SHAKER_DAYS) < days ? 1 : 0) : take.getOrDefault(g.item(), 0);
			int room = Math.max(0, g.target() * 2 - Storage.count(level, village, g.item()));
			if (Math.min(add, room) > 0) {
				Storage.put(level, village, g.item(), Math.min(add, room));
			}
		}
	}

	static void clear() {
		ABED.clear();
		BLESSED.clear();
	}
}
