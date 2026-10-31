package com.jaymoskingdoms;

import com.jaymoskingdoms.monsters.Monsters;
import com.jaymoskingdoms.monsters.Ogre;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.throwableitemprojectile.Snowball;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.426) Havens patch 2, the ogre tribe (ideas from King's Monsters, ARR, and Shadow of War: our own names, code and
 * buildings). In each region ({@link Tribes#REGION} a side) the ogres keep up to {@link #OUTPOSTS} <b>outposts</b> in the
 * plains and swamps, a ring of mossy stones round a lean-to and a bone pile, each led by a two-headed brute (plains) or a
 * cave ogre (swamps) with a couple of ogres. Once a region has an outpost, a great <b>ogre hold</b> may rise in it: a
 * walled yard of old stone with a throne, the <b>Ogre King</b> on it and his guard (the biggest of their kind) at the gate.
 * <p>The king fights in three phases ({@link #step}): his fists and smash; at two thirds he roars, calls ogres from the
 * hold and quickens; at a third he tears up boulders and hurls them. Killing him clears his region's outposts (their
 * ogres go), and the first king to fall frees the bog iron ({@link BogIron}).
 */
public final class OgreHolds {
	// ================================================================================================================
	// Tuning
	/** How often outposts and holds may rise (ticks); the chance each time, for each player out in the wild. */
	static final int EVERY = 600;
	static final float OUTPOST_CHANCE = 0.06F, HOLD_CHANCE = 0.04F;
	/** At most this many standing outposts in a region. */
	static final int OUTPOSTS = 3;
	/** Where they rise: so far from the player (blocks), never this near a settlement or another ogre site. */
	static final int RISE_NEAR = 64, RISE_FAR = 96;
	static final double SETTLEMENT_GAP = 240.0, SITE_GAP = 200.0;
	/** Sizes: an outpost's ring and a hold's walls (radius), the most the ground may rise and fall under one. */
	static final int OUTPOST_RADIUS = 6, HOLD_RADIUS = 12, HOLD_WALL = 4, MAX_SPREAD = 4;
	/** An outpost's ogres with its leader, and how near home they keep. */
	static final int OUTPOST_OGRES = 2, OUTPOST_HOME = 12, HOLD_HOME = 10, GUARD_HOME = 3;
	/** The Ogre King: health, blows, size; his guards. */
	static final double KING_HEALTH = 320.0, KING_DAMAGE = 15.0, KING_SCALE = 1.7;
	static final double GUARD_HEALTH = 110.0, GUARD_DAMAGE = 11.0, GUARD_SCALE = 1.3;
	/** His phases (share of his health) and what the second calls up. */
	static final float PHASE_TWO = 0.66F, PHASE_THREE = 0.33F;
	static final int CALLED = 2;
	/** Boulders (phase three): how often (ticks), the least range, their damage. */
	static final int BOULDER_TICKS = 50;
	static final double BOULDER_MIN = 4.0;
	static final float BOULDER_DAMAGE = 10.0F;
	// ================================================================================================================

	static final String OGRE_TAG = "jaymoskingdoms.ogre_site", KING_TAG = "jaymoskingdoms.ogre_king", SITE_PREFIX = "jaymoskingdoms.ogre_site.",
		GUARD_TAG = "jaymoskingdoms.ogre_guard";

	/** An ogre site: an outpost or a hold, where, its region, its leader (or king), and whether it's cleared. */
	record Site(int id, boolean hold, BlockPos at, String region, UUID leader, boolean cleared) {
		static final Codec<Site> CODEC = RecordCodecBuilder.create(i -> i.group(Codec.INT.fieldOf("id").forGetter(Site::id),
				Codec.BOOL.fieldOf("hold").forGetter(Site::hold), BlockPos.CODEC.fieldOf("at").forGetter(Site::at),
				Codec.STRING.fieldOf("region").forGetter(Site::region), net.minecraft.core.UUIDUtil.CODEC.fieldOf("leader").forGetter(Site::leader),
				Codec.BOOL.fieldOf("cleared").forGetter(Site::cleared))
			.apply(i, Site::new));

		Site clear() {
			return new Site(id, hold, at, region, leader, true);
		}
	}

	/** (0.7.429) On a player: ogre region → game time their truce with its ogres ends (a stone-lifting brawl won, {@link Trials}). Saved. */
	static final AttachmentType<Map<String, Long>> TRUCE = AttachmentRegistry.create(JaymosKingdoms.id("ogre_truce"),
		builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, Codec.LONG)).copyOnDeath());

	/** On the Overworld: every ogre site. Saved. */
	static final AttachmentType<List<Site>> SITES = AttachmentRegistry.create(JaymosKingdoms.id("ogre_sites"), builder -> builder.persistent(Site.CODEC.listOf()));

	private static final String[] KING_FIRST = {"Grukk", "Mogg", "Hruff", "Bulg", "Throgg", "Urm", "Dagg", "Lumm", "Gorb", "Krull"};
	private static final String[] KING_BYNAME = {"the Fat", "Stone-Eater", "Two-Thrones", "the Hungry", "Bone-Crown", "the Old", "Mud-Belly",
		"the Unmoved"};

	/** The kings in the world just now, their bars and phases. */
	private static final Map<UUID, ServerBossEvent> BARS = new HashMap<>();
	private static final Map<UUID, Integer> PHASES = new HashMap<>();
	private static final Map<UUID, Long> NEXT_BOULDER = new HashMap<>();
	/** Boulders in flight. */
	private static final Set<Projectile> BOULDERS = new HashSet<>();
	private static boolean redealing;

	private OgreHolds() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			ServerLevel level = Factions.level(server);
			if (server.getTickCount() % EVERY == 0) {
				rise(level);
			}
			if (server.getTickCount() % 5 == 0) {
				tend(level);
			}
		});
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			Site site = siteOf(entity);
			if (site != null && site.cleared() && !(entity.entityTags().contains(KING_TAG))) {
				// (its outpost or hold was cleared while it was away: it's gone)
				entity.discard();
			}
		});
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> dropBar(entity.getUUID()));
		ServerLivingEntityEvents.AFTER_DEATH.register(OgreHolds::died);
		ServerLivingEntityEvents.ALLOW_DAMAGE.register(OgreHolds::boulderHit);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			BARS.values().forEach(ServerBossEvent::removeAllPlayers);
			BARS.clear();
			PHASES.clear();
			NEXT_BOULDER.clear();
			BOULDERS.clear();
		});
	}

	// ---- the sites ---------------------------------------------------------------------------------------------------

	private static List<Site> sites(MinecraftServer server) {
		return Factions.level(server).getAttachedOrElse(SITES, List.of());
	}

	private static void put(MinecraftServer server, Site site) {
		List<Site> all = new ArrayList<>(sites(server));
		all.removeIf(s -> s.id() == site.id());
		all.add(site);
		Factions.level(server).setAttached(SITES, List.copyOf(all));
	}

	/** The region {@code at} lies in. */
	static String region(BlockPos at) {
		return Math.floorDiv(at.getX(), Tribes.REGION) + "," + Math.floorDiv(at.getZ(), Tribes.REGION);
	}

	/** The site {@code e} belongs to (by its tag), or null. */
	private static @Nullable Site siteOf(Entity e) {
		MinecraftServer server = e.level().getServer();
		if (server == null || !e.entityTags().contains(OGRE_TAG)) {
			return null;
		}
		for (String tag : e.entityTags()) {
			if (tag.startsWith(SITE_PREFIX)) {
				try {
					int id = Integer.parseInt(tag.substring(SITE_PREFIX.length()));
					for (Site s : sites(server)) {
						if (s.id() == id) {
							return s;
						}
					}
				} catch (NumberFormatException ignored) {
					return null;
				}
			}
		}
		return null;
	}

	/** (0.7.429) {@code e} is one of an Ogre King's guard. */
	public static boolean guard(Entity e) {
		return e.entityTags().contains(GUARD_TAG);
	}

	/** (0.7.429) {@code player} won a brawl in {@code region}: its ogres leave them be for {@link Trials#AGAIN_DAYS} days. */
	static void truce(ServerPlayer player, String region) {
		Map<String, Long> truce = new HashMap<>(player.getAttachedOrElse(TRUCE, Map.of()));
		truce.put(region, player.level().getGameTime() + 24000L * Trials.AGAIN_DAYS);
		player.setAttached(TRUCE, Map.copyOf(truce));
	}

	/** (0.7.429) The ogres of {@code ogre}'s region are under truce with {@code player}. */
	static boolean truce(Player player, Entity ogre) {
		Long until = player.getAttachedOrElse(TRUCE, Map.of()).get(region(ogre.blockPosition()));
		return until != null && player.level().getGameTime() < until;
	}

	/** {@code e} is an Ogre King. */
	public static boolean king(Entity e) {
		return e.entityTags().contains(KING_TAG);
	}

	/** The region's hold, standing or fallen, or null if none rose there yet. */
	private static @Nullable Site hold(MinecraftServer server, String region) {
		for (Site s : sites(server)) {
			if (s.hold() && s.region().equals(region)) {
				return s;
			}
		}
		return null;
	}

	// ---- rising ------------------------------------------------------------------------------------------------------

	/** For each player out in the wild: an outpost may rise near them, or (with an outpost in the region already) the hold. */
	private static void rise(ServerLevel level) {
		MinecraftServer server = level.getServer();
		RandomSource random = level.getRandom();
		for (ServerPlayer player : level.players()) {
			if (player.isCreative() || player.isSpectator() || !player.isAlive()) {
				continue;
			}
			String region = region(player.blockPosition());
			Site hold = hold(server, region);
			if (hold != null && hold.cleared()) {
				// (its king is dead: the ogres here are done)
				continue;
			}
			long standing = sites(server).stream().filter(s -> !s.hold() && !s.cleared() && s.region().equals(region)).count();
			boolean any = sites(server).stream().anyMatch(s -> !s.hold() && s.region().equals(region));
			if (hold == null && any && random.nextFloat() < HOLD_CHANCE) {
				BlockPos at = spot(level, player, HOLD_RADIUS, false);
				if (at != null && region(at).equals(region)) {
					buildHold(level, at, region);
					return;
				}
			}
			if (standing < OUTPOSTS && random.nextFloat() < OUTPOST_CHANCE) {
				BlockPos at = spot(level, player, OUTPOST_RADIUS, true);
				if (at != null && region(at).equals(region)) {
					buildOutpost(level, at, region);
					return;
				}
			}
		}
	}

	/** A place near {@code player} for a site of {@code radius}: plains or swamp for an outpost, any dry ground for a hold; or null. */
	private static @Nullable BlockPos spot(ServerLevel level, ServerPlayer player, int radius, boolean outpost) {
		RandomSource random = level.getRandom();
		double angle = random.nextDouble() * Math.PI * 2.0;
		int r = RISE_NEAR + random.nextInt(RISE_FAR - RISE_NEAR);
		BlockPos column = BlockPos.containing(player.getX() + Math.cos(angle) * r, 0, player.getZ() + Math.sin(angle) * r);
		for (int dx : new int[] {-radius, radius}) {
			for (int dz : new int[] {-radius, radius}) {
				if (!level.isLoaded(column.offset(dx, 0, dz))) {
					return null;
				}
			}
		}
		BlockPos ground = level.getHeightmapPos(Heightmap.Types.WORLD_SURFACE_WG, column);
		Holder<Biome> biome = level.getBiome(ground);
		boolean swamp = biome.is(Biomes.SWAMP) || biome.is(Biomes.MANGROVE_SWAMP);
		if (outpost ? !(swamp || biome.is(Biomes.PLAINS) || biome.is(Biomes.SUNFLOWER_PLAINS)) : biome.is(BiomeTags.IS_OCEAN) || biome.is(BiomeTags.IS_RIVER)) {
			return null;
		}
		ground = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column);
		if (!swamp && !level.getFluidState(ground.below()).isEmpty()) {
			return null;
		}
		int low = ground.getY(), high = ground.getY();
		for (int dx = -radius; dx <= radius; dx += radius) {
			for (int dz = -radius; dz <= radius; dz += radius) {
				int y = level.getHeight(Heightmap.Types.OCEAN_FLOOR, column.getX() + dx, column.getZ() + dz);
				low = Math.min(low, y);
				high = Math.max(high, y);
			}
		}
		if (high - low > MAX_SPREAD) {
			return null;
		}
		MinecraftServer server = level.getServer();
		BlockPos here = ground;
		if (Factions.around(server, level, here, SETTLEMENT_GAP) != null
			|| sites(server).stream().anyMatch(s -> s.at().distSqr(here) < SITE_GAP * SITE_GAP)) {
			return null;
		}
		return new BlockPos(ground.getX(), level.getHeight(Heightmap.Types.OCEAN_FLOOR, ground.getX(), ground.getZ()), ground.getZ());
	}

	private static int nextId(MinecraftServer server) {
		return sites(server).stream().mapToInt(Site::id).max().orElse(0) + 1;
	}

	/** Levels a round floor of {@code floor} at {@code y} with {@code radius}, clearing what stands above. */
	private static void floor(ServerLevel level, BlockPos center, int radius, BlockState floor, int clear) {
		for (int dx = -radius; dx <= radius; dx++) {
			for (int dz = -radius; dz <= radius; dz++) {
				if (dx * dx + dz * dz > radius * radius + radius) {
					continue;
				}
				BlockPos p = center.offset(dx, -1, dz);
				level.setBlock(p, floor, 2);
				for (int y = 1; y <= 3; y++) {
					if (level.getBlockState(p.below(y)).canBeReplaced() || !level.getFluidState(p.below(y)).isEmpty()) {
						level.setBlock(p.below(y), Blocks.DIRT.defaultBlockState(), 2);
					}
				}
				for (int y = 1; y <= clear; y++) {
					level.setBlock(p.above(y), Blocks.AIR.defaultBlockState(), 2);
				}
			}
		}
	}

	private static void buildOutpost(ServerLevel level, BlockPos at, String region) {
		MinecraftServer server = level.getServer();
		RandomSource random = level.getRandom();
		boolean swamp = level.getBiome(at).is(Biomes.SWAMP) || level.getBiome(at).is(Biomes.MANGROVE_SWAMP);
		floor(level, at, OUTPOST_RADIUS, (swamp ? Blocks.MUD : Blocks.COARSE_DIRT).defaultBlockState(), 5);
		// a broken ring of mossy stones
		for (int a = 0; a < 360; a += 15) {
			if (random.nextInt(4) == 0) {
				continue;
			}
			BlockPos p = at.offset((int) Math.round(Math.cos(Math.toRadians(a)) * OUTPOST_RADIUS), 0, (int) Math.round(Math.sin(Math.toRadians(a)) * OUTPOST_RADIUS));
			level.setBlock(p, Blocks.MOSSY_COBBLESTONE.defaultBlockState(), 2);
			if (random.nextBoolean()) {
				level.setBlock(p.above(), Blocks.MOSSY_COBBLESTONE.defaultBlockState(), 2);
			}
		}
		// a lean-to of logs, its roof sloping to the back
		BlockPos lean = at.offset(-3, 0, -3);
		for (int dx = 0; dx < 4; dx++) {
			level.setBlock(lean.offset(dx, 0, 0), Blocks.SPRUCE_LOG.defaultBlockState(), 2);
			level.setBlock(lean.offset(dx, 1, 0), Blocks.SPRUCE_LOG.defaultBlockState(), 2);
			level.setBlock(lean.offset(dx, 2, 0), Blocks.SPRUCE_SLAB.defaultBlockState(), 2);
			level.setBlock(lean.offset(dx, 2, 1), Blocks.SPRUCE_SLAB.defaultBlockState(), 2);
			level.setBlock(lean.offset(dx, 1, 2), Blocks.SPRUCE_SLAB.defaultBlockState(), 2);
		}
		level.setBlock(lean.offset(0, 0, 2), Blocks.SPRUCE_FENCE.defaultBlockState(), 2);
		level.setBlock(lean.offset(3, 0, 2), Blocks.SPRUCE_FENCE.defaultBlockState(), 2);
		// the bone pile and the fire
		level.setBlock(at.offset(3, 0, 2), Blocks.BONE_BLOCK.defaultBlockState(), 2);
		level.setBlock(at.offset(3, 1, 2), Blocks.BONE_BLOCK.defaultBlockState(), 2);
		level.setBlock(at.offset(2, 0, 3), Blocks.BONE_BLOCK.defaultBlockState(), 2);
		level.setBlock(at.offset(4, 0, 3), Blocks.SKELETON_SKULL.defaultBlockState(), 2);
		level.setBlock(at, Blocks.CAMPFIRE.defaultBlockState(), 2);
		chest(level, lean.offset(1, 0, 1), Direction.SOUTH, random, false);
		int id = nextId(server);
		Ogre leader = ogre(level, swamp ? Monsters.CAVE_OGRE : Monsters.OGRE, at.offset(0, 0, 2), at, OUTPOST_HOME, id);
		if (leader == null) {
			return;
		}
		if (!swamp) {
			leader.setTwoHeads(true);
		}
		for (int i = 0; i < OUTPOST_OGRES; i++) {
			Ogre o = ogre(level, Monsters.OGRE, at.offset(random.nextInt(5) - 2, 0, random.nextInt(5) - 2), at, OUTPOST_HOME, id);
			if (o != null) {
				o.setTwoHeads(false);
			}
		}
		put(server, new Site(id, false, at, region, leader.getUUID(), false));
		JaymosKingdoms.LOGGER.info("An ogre outpost rose at {}", at);
	}

	private static void buildHold(ServerLevel level, BlockPos at, String region) {
		MinecraftServer server = level.getServer();
		RandomSource random = level.getRandom();
		floor(level, at, HOLD_RADIUS, Blocks.COBBLESTONE.defaultBlockState(), HOLD_WALL + 4);
		Direction gate = Direction.Plane.HORIZONTAL.getRandomDirection(random);
		// the walls: old stone, crumbling at the top, a gap for the gate
		for (int dx = -HOLD_RADIUS; dx <= HOLD_RADIUS; dx++) {
			for (int dz = -HOLD_RADIUS; dz <= HOLD_RADIUS; dz++) {
				double d = Math.sqrt(dx * dx + dz * dz);
				if (d < HOLD_RADIUS - 0.5 || d > HOLD_RADIUS + 0.5) {
					continue;
				}
				int along = dx * gate.getStepX() + dz * gate.getStepZ();
				int across = dx * gate.getStepZ() - dz * gate.getStepX();
				if (along > HOLD_RADIUS - 2 && Math.abs(across) <= 1) {
					continue;
				}
				for (int y = 0; y < HOLD_WALL; y++) {
					if (y == HOLD_WALL - 1 && random.nextInt(3) == 0) {
						continue;
					}
					level.setBlock(at.offset(dx, y, dz), (random.nextInt(3) == 0 ? Blocks.MOSSY_STONE_BRICKS : random.nextInt(4) == 0 ? Blocks.CRACKED_STONE_BRICKS
						: Blocks.STONE_BRICKS).defaultBlockState(), 2);
				}
			}
		}
		// the throne at the back, facing the gate: a dais, a seat, skulls and fires either side
		BlockPos back = at.relative(gate.getOpposite(), HOLD_RADIUS - 4);
		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				level.setBlock(back.offset(dx, 0, dz), Blocks.POLISHED_ANDESITE.defaultBlockState(), 2);
			}
		}
		BlockPos seat = back.above();
		level.setBlock(seat, Blocks.STONE_BRICK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, gate.getOpposite()), 2);
		level.setBlock(seat.relative(gate.getOpposite()), Blocks.STONE_BRICKS.defaultBlockState(), 2);
		level.setBlock(seat.relative(gate.getOpposite()).above(), Blocks.STONE_BRICKS.defaultBlockState(), 2);
		level.setBlock(seat.relative(gate.getOpposite()).above(2), Blocks.SKELETON_SKULL.defaultBlockState(), 2);
		Direction side = gate.getClockWise();
		for (int s : new int[] {-2, 2}) {
			level.setBlock(seat.relative(side, s), Blocks.CAMPFIRE.defaultBlockState(), 2);
			level.setBlock(at.relative(gate, HOLD_RADIUS - 2).relative(side, s), Blocks.CAMPFIRE.defaultBlockState(), 2);
		}
		for (int i = 0; i < 6; i++) {
			BlockPos p = at.offset(random.nextInt(HOLD_RADIUS) - HOLD_RADIUS / 2, 0, random.nextInt(HOLD_RADIUS) - HOLD_RADIUS / 2);
			if (level.getBlockState(p).isAir()) {
				level.setBlock(p, (random.nextBoolean() ? Blocks.BONE_BLOCK : Blocks.MOSSY_COBBLESTONE).defaultBlockState(), 2);
			}
		}
		chest(level, seat.relative(side, 1).relative(gate.getOpposite()), gate, random, true);
		int id = nextId(server);
		Ogre king = ogre(level, Monsters.CAVE_OGRE, seat.relative(gate), back, HOLD_HOME, id);
		if (king == null) {
			return;
		}
		king.setTwoHeads(false);
		king.addTag(KING_TAG);
		build(king, KING_HEALTH, KING_DAMAGE, KING_SCALE);
		king.setCustomName(Component.translatable("entity.jaymoskingdoms.ogre_king", KING_FIRST[random.nextInt(KING_FIRST.length)],
			Component.literal(KING_BYNAME[random.nextInt(KING_BYNAME.length)])));
		// the king's guard: the biggest of their kind, posted either side of the gate
		BlockPos door = at.relative(gate, HOLD_RADIUS - 2);
		for (int s : new int[] {-2, 2}) {
			Ogre guard = ogre(level, Monsters.CAVE_OGRE, door.relative(side, s), door.relative(side, s), GUARD_HOME, id);
			if (guard != null) {
				guard.setTwoHeads(true);
				guard.addTag(GUARD_TAG);
				build(guard, GUARD_HEALTH, GUARD_DAMAGE, GUARD_SCALE);
				guard.setCustomName(Component.translatable("entity.jaymoskingdoms.ogre_kings_guard"));
			}
		}
		put(server, new Site(id, true, at, region, king.getUUID(), false));
		JaymosKingdoms.LOGGER.info("An ogre hold rose at {} ({})", at, king.getCustomName().getString());
	}

	/** A chest of an ogre site's: bones and scraps, or (a king's) his hoard. */
	private static void chest(ServerLevel level, BlockPos at, Direction facing, RandomSource random, boolean hoard) {
		level.setBlock(at, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, facing), 2);
		if (!(level.getBlockEntity(at) instanceof ChestBlockEntity chest)) {
			return;
		}
		List<ItemStack> loot = hoard
			? List.of(new ItemStack(Items.EMERALD, 8 + random.nextInt(12)), new ItemStack(Items.GOLD_INGOT, 4 + random.nextInt(6)),
				new ItemStack(Items.IRON_INGOT, 6 + random.nextInt(8)), new ItemStack(Items.GOLDEN_APPLE, 1 + random.nextInt(2)),
				new ItemStack(Items.DIAMOND, 1 + random.nextInt(2)), new ItemStack(Items.SADDLE))
			: List.of(new ItemStack(Items.BONE, 4 + random.nextInt(8)), new ItemStack(Items.ROTTEN_FLESH, 3 + random.nextInt(6)),
				new ItemStack(Items.IRON_NUGGET, 4 + random.nextInt(10)), new ItemStack(Items.LEATHER, 1 + random.nextInt(4)),
				new ItemStack(Items.EMERALD, random.nextInt(3)));
		int slot = 0;
		for (ItemStack s : loot) {
			if (!s.isEmpty()) {
				chest.setItem(slot, s);
				slot += 1 + random.nextInt(3);
			}
		}
	}

	/** An ogre of {@code type} for site {@code site}, kept near {@code home}. */
	private static @Nullable Ogre ogre(ServerLevel level, EntityType<Ogre> type, BlockPos at, BlockPos home, int radius, int site) {
		Ogre ogre = type.create(level, EntitySpawnReason.EVENT);
		if (ogre == null) {
			return null;
		}
		ogre.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, level.getRandom().nextFloat() * 360.0F, 0.0F);
		ogre.finalizeSpawn(level, level.getCurrentDifficultyAt(at), EntitySpawnReason.EVENT, null);
		ogre.setPersistenceRequired();
		ogre.setHomeTo(home, radius);
		ogre.addTag(OGRE_TAG);
		ogre.addTag(SITE_PREFIX + site);
		level.addFreshEntity(ogre);
		return ogre;
	}

	private static void build(Ogre ogre, double health, double damage, double scale) {
		set(ogre.getAttribute(Attributes.MAX_HEALTH), health);
		set(ogre.getAttribute(Attributes.ATTACK_DAMAGE), damage);
		set(ogre.getAttribute(Attributes.SCALE), scale);
		set(ogre.getAttribute(Attributes.KNOCKBACK_RESISTANCE), 1.0);
		ogre.setHealth(ogre.getMaxHealth());
	}

	private static void set(@Nullable AttributeInstance a, double v) {
		if (a != null) {
			a.setBaseValue(v);
		}
	}

	// ---- the king in a fight -----------------------------------------------------------------------------------------

	/** Every few ticks: kings' bars and phases; ogres strayed from home walk back. */
	private static void tend(ServerLevel level) {
		for (Player p : level.players()) {
			for (Ogre ogre : level.getEntitiesOfClass(Ogre.class, p.getBoundingBox().inflate(96.0), o -> o.entityTags().contains(OGRE_TAG) && o.isAlive())) {
				if (ogre.getTarget() instanceof Player foe && truce(foe, ogre) && ogre.getLastHurtByMob() != foe && !Trials.brawling(foe, ogre)) {
					// (0.7.429) under truce: they leave you be unless you strike them
					ogre.setTarget(null);
				}
				if (king(ogre)) {
					step(level, ogre);
				} else if (ogre.getTarget() == null && ogre.hasHome() && !ogre.isWithinHome() && ogre.getNavigation().isDone()) {
					BlockPos home = ogre.getHomePosition();
					ogre.getNavigation().moveTo(home.getX() + 0.5, home.getY(), home.getZ() + 0.5, 0.8);
				}
			}
		}
	}

	/** The king's bar and his three phases. */
	private static void step(ServerLevel level, Ogre king) {
		ServerBossEvent bar = BARS.computeIfAbsent(king.getUUID(), id -> new ServerBossEvent(id, king.getDisplayName(), BossEvent.BossBarColor.GREEN,
			BossEvent.BossBarOverlay.NOTCHED_6));
		float share = king.getHealth() / king.getMaxHealth();
		bar.setProgress(share);
		BossBars.show(level, king, bar);
		int phase = share <= PHASE_THREE ? 3 : share <= PHASE_TWO ? 2 : 1;
		int was = PHASES.getOrDefault(king.getUUID(), 1);
		if (phase > was) {
			PHASES.put(king.getUUID(), phase);
			bar.setColor(phase == 2 ? BossEvent.BossBarColor.YELLOW : BossEvent.BossBarColor.RED);
			level.playSound(null, king.getX(), king.getY(), king.getZ(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 3.0F, 0.5F);
			level.sendParticles(ParticleTypes.ANGRY_VILLAGER, king.getX(), king.getY() + king.getBbHeight(), king.getZ(), 8, 0.6, 0.3, 0.6, 0.0);
			Speech.say(level, king, Component.translatable("talk.jaymoskingdoms.ogre_king.phase" + phase + "." + king.getRandom().nextInt(2)), Speech.RED, 40.0);
			if (phase >= 2 && was < 2) {
				// (two thirds: he calls his ogres and quickens)
				king.addEffect(new MobEffectInstance(MobEffects.SPEED, 20 * 60 * 10, 0));
				king.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 20 * 60 * 10, 0));
				Site site = siteOf(king);
				for (int i = 0; i < CALLED && site != null; i++) {
					Ogre o = ogre(level, Monsters.OGRE, king.blockPosition().offset(king.getRandom().nextInt(7) - 3, 0, king.getRandom().nextInt(7) - 3),
						king.blockPosition(), HOLD_HOME, site.id());
					if (o != null && king.getTarget() != null) {
						o.setTarget(king.getTarget());
					}
				}
			}
		}
		LivingEntity target = king.getTarget();
		long now = level.getGameTime();
		if (phase == 3 && target != null && target.isAlive() && king.distanceTo(target) > BOULDER_MIN
			&& now >= NEXT_BOULDER.getOrDefault(king.getUUID(), 0L) && king.hasLineOfSight(target)) {
			// (a third: he tears up a boulder and hurls it)
			NEXT_BOULDER.put(king.getUUID(), now + BOULDER_TICKS);
			Snowball boulder = new Snowball(level, king, new ItemStack(Items.MOSSY_COBBLESTONE));
			boulder.setPos(king.getX(), king.getY() + king.getBbHeight() * 0.8, king.getZ());
			Vec3 to = target.getEyePosition().subtract(boulder.position());
			boulder.shoot(to.x, to.y + to.horizontalDistance() * 0.15, to.z, 1.3F, 2.0F);
			level.addFreshEntity(boulder);
			BOULDERS.add(boulder);
			BOULDERS.removeIf(b -> b.isRemoved());
			king.swing(net.minecraft.world.InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
			level.playSound(null, king.getX(), king.getY(), king.getZ(), SoundEvents.STONE_BREAK, SoundSource.HOSTILE, 1.5F, 0.5F);
		}
	}

	/** A boulder hits with its weight (a snowball does nothing on its own). */
	private static boolean boulderHit(LivingEntity victim, DamageSource source, float amount) {
		if (redealing || !(source.getDirectEntity() instanceof Projectile p) || !BOULDERS.contains(p) || !(victim.level() instanceof ServerLevel level)) {
			return true;
		}
		if (victim instanceof Ogre) {
			return false;
		}
		redealing = true;
		try {
			victim.hurtServer(level, source, BOULDER_DAMAGE);
		} finally {
			redealing = false;
		}
		return false;
	}

	private static void dropBar(UUID id) {
		ServerBossEvent bar = BARS.remove(id);
		if (bar != null) {
			bar.removeAllPlayers();
		}
	}

	// ---- death -------------------------------------------------------------------------------------------------------

	/** A site's leader died: an outpost is cleared; a king's fall clears his region's outposts (and may free the bog iron). */
	private static void died(LivingEntity entity, DamageSource source) {
		dropBar(entity.getUUID());
		PHASES.remove(entity.getUUID());
		NEXT_BOULDER.remove(entity.getUUID());
		if (!(entity.level() instanceof ServerLevel level)) {
			return;
		}
		Site site = siteOf(entity);
		if (site == null || site.cleared() || !site.leader().equals(entity.getUUID())) {
			return;
		}
		MinecraftServer server = level.getServer();
		put(server, site.clear());
		if (!site.hold()) {
			return;
		}
		// the king is dead: his outposts break up, their ogres go
		Set<Integer> cleared = new HashSet<>();
		for (Site s : sites(server)) {
			if (!s.hold() && !s.cleared() && s.region().equals(site.region())) {
				put(server, s.clear());
				cleared.add(s.id());
			}
		}
		for (Ogre o : level.getEntitiesOfClass(Ogre.class, entity.getBoundingBox().inflate(Tribes.REGION), o -> o.entityTags().contains(OGRE_TAG))) {
			Site of = siteOf(o);
			if (of != null && cleared.contains(of.id())) {
				level.sendParticles(ParticleTypes.POOF, o.getX(), o.getY() + 1.0, o.getZ(), 20, 0.6, 1.0, 0.6, 0.02);
				o.discard();
			}
		}
		level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 4.0F, 0.5F);
		Component who = source.getEntity() instanceof ServerPlayer p ? p.getDisplayName() : null;
		Post.news(server, who == null ? Component.translatable("news.jaymoskingdoms.ogre_king.fell", entity.getDisplayName())
			: Component.translatable("news.jaymoskingdoms.ogre_king.slain", entity.getDisplayName(), who));
		BogIron.fell(server);
		JaymosKingdoms.LOGGER.info("{} fell; {} outposts broke up", entity.getDisplayName().getString(), cleared.size());
	}
}
