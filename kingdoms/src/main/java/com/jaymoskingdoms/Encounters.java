package com.jaymoskingdoms;

import com.jaymoskingdoms.monsters.Monsters;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.biome.v1.ModificationPhase;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jspecify.annotations.Nullable;

/**
 * (0.5.55) Wilderness encounters with Naturalist's predators, and (0.7.526) no longer: Andy, "remove the encounters, its
 * better when the predators can naturally spawn". Nothing comes hunting a player out of nowhere any more. Naturalist's
 * bears, lions, tigers, alligators, komodo dragons, rhinos, scorpions and elephants spawn as Naturalist has them, and every
 * Kingdoms beast in {@link #TABLE} (feral wolves, coyotes, tarantulas, moose, the monsters of legend, the mud golem, the wind dragons)
 * spawns on its own in its biome tag ({@code jaymoskingdoms:has_<beast>}) by the table's weight: on the surface (the cave
 * ogre anywhere dark), the night ones only in the dark, the monsters of legend only {@link #LEGEND_CHANCE} of the times they
 * could and never on or near a settlement's land ({@link #SETTLEMENT_CLEARANCE}), as are the wind dragons.
 * The table still names where each roams for the talk of them ({@link Sightings}) and the quests that send one after you
 * ({@link #send}). Cube Animals (0.7.526) is let in beside them, with its kinds that Naturalist or Kingdoms already have
 * kept out of natural spawning ({@link #CUBE_DUPLICATES}); its rattlesnake, shark, elephant, (0.7.527) red and roe deer,
 * crocodile and black bear stay as other breeds.
 */
public final class Encounters {
	static final String MOD = "naturalist";
	/** Keep Naturalist's other animals in natural spawning (false: its mobs only ever come as encounters). */
	static final boolean WILDLIFE = true;
	/**
	 * Naturalist mobs kept out of natural spawning: the mammoths and whales Kingdoms has. (0.7.526) Its predators and its
	 * elephants spawn again (Andy: elephants as another breed beside Kingdoms' own).
	 */
	static final List<String> NO_NATURAL_SPAWNS = List.of("mammoth",
		// (0.7.206) its whales: Kingdoms' great whales take their place (whaling)
		"whale",
		// (0.7.329) its boar: Kingdoms' wild boar takes its place (beasts/WildBoar)
		"boar");
	/**
	 * (0.7.526) Cube Animals: its kinds that Naturalist or Kingdoms already have, kept out of natural spawning (komodo dragon,
	 * moose, piranha, snail, tiger). Its rattlesnake, shark and elephant, and (0.7.527) its red and roe deer, crocodile and
	 * black bear, spawn as other breeds beside Naturalist's and Kingdoms' (Andy).
	 */
	static final String CUBE = "cubeanimals";
	static final List<String> CUBE_DUPLICATES = List.of("komododragon", "moose", "piranha", "snail", "tiger");
	/** Of the times a monster of legend could spawn on its own, how many it does (they stay rare). */
	static final float LEGEND_CHANCE = 0.1F;
	/** Of the times a monster that walks by day (the ogre, the mud golem) could spawn in daylight, how many it does (by night, all of them). */
	static final float DAY_MONSTER_CHANCE = 0.03F;
	/** The mud golem's weight among the swamps' monsters (a zombie's is 95). */
	static final int MUD_GOLEM_WEIGHT = 15;
	/** No monster of legend or wind dragon spawns this close to a settlement's seat (blocks), nor on any settlement's land. */
	static final double SETTLEMENT_CLEARANCE = 256.0;
	/** How far off they come from (blocks). */
	static final int MIN_DISTANCE = 20, MAX_DISTANCE = 32;
	/** A player this far under the ground's surface (blocks) is underground, not in the wild. */
	static final int SURFACE_SLACK = 6;
	/** The most a spot may sit above or below the player (blocks). */
	static final int MAX_RISE = 12;
	/** Tries at finding a spot for them. */
	static final int SPOT_TRIES = 8;
	/** How long they keep after their player (ticks; 2 minutes), and how far off they give up on them. */
	static final int HUNT_TICKS = 2400;
	static final double LOSE_RANGE = 96.0;
	/** How often the hunts are looked over (ticks). */
	static final int HUNT_CHECK_TICKS = 20;

	/**
	 * A beast that can find you: its entity id, the biome tag it hunts in, its weight, how many come, and whether only at
	 * night.
	 */
	record Encounter(String mob, String biomes, int weight, int min, int max, boolean night) {
		Encounter(String mob, String biomes, int weight, int min, int max) {
			this(mob, biomes, weight, min, max, false);
		}

		/** The beast's name, for its action bar line. */
		String name() {
			return this.mob.substring(this.mob.indexOf(':') + 1);
		}
	}

	static final List<Encounter> TABLE = List.of(
		new Encounter("naturalist:bear", "naturalist:has_bear", 10, 1, 1),
		new Encounter("naturalist:lion", "naturalist:has_lion", 10, 2, 3),
		new Encounter("naturalist:tiger", "naturalist:has_tiger", 10, 1, 1),
		new Encounter("naturalist:alligator", "naturalist:has_alligator", 10, 1, 2),
		new Encounter("naturalist:komodo_dragon", "naturalist:has_komodo_dragon", 10, 1, 2),
		new Encounter("naturalist:rhino", "naturalist:has_rhino", 6, 1, 1),
		new Encounter("naturalist:desert_scorpion", "naturalist:has_desert_scorpion", 10, 2, 3),
		new Encounter("naturalist:jungle_scorpion", "naturalist:has_jungle_scorpion", 10, 2, 3),
		// (0.7.329) Kingdoms' own wild boar in place of Naturalist's (beasts/WildBoar)
		new Encounter("jaymoskingdoms:wild_boar", "jaymoskingdoms:has_wild_boar", 6, 2, 4),
		new Encounter("naturalist:snake", "naturalist:has_rattlesnake", 4, 1, 1),
		new Encounter("naturalist:snake", "naturalist:has_coral_snake", 4, 1, 1),
		// Kingdoms' own, after Better Animals Plus (wild/Wild)
		new Encounter("jaymoskingdoms:feral_wolf", "jaymoskingdoms:has_feral_wolf", 10, 3, 5),
		new Encounter("jaymoskingdoms:coyote", "jaymoskingdoms:has_coyote", 10, 2, 3, true),
		// (Andy: "tarantula should only spawn in the jungle at night"; under the canopy too, see wildSpawn)
		new Encounter("jaymoskingdoms:tarantula", "jaymoskingdoms:has_tarantula", 8, 1, 2, true),
		new Encounter("jaymoskingdoms:moose", "jaymoskingdoms:has_moose", 6, 1, 1),
		// (0.5.56) the monsters of legend (monsters/Monsters)
		new Encounter("jaymoskingdoms:werewolf", "jaymoskingdoms:has_werewolf", 5, 1, 1, true),
		new Encounter("jaymoskingdoms:ogre", "jaymoskingdoms:has_ogre", 4, 1, 1),
		new Encounter("jaymoskingdoms:cave_ogre", "jaymoskingdoms:has_cave_ogre", 4, 1, 1, true),
		new Encounter("jaymoskingdoms:fire_ogre", "jaymoskingdoms:has_fire_ogre", 3, 1, 1, true),
		new Encounter("jaymoskingdoms:wraith", "jaymoskingdoms:has_wraith", 5, 1, 2, true),
		new Encounter("jaymoskingdoms:flame_wraith", "jaymoskingdoms:has_flame_wraith", 3, 1, 1, true),
		new Encounter("jaymoskingdoms:silver_skeleton", "jaymoskingdoms:has_silver_skeleton", 5, 2, 3, true),
		// the Gaunt of the snowy woods (hunger/Gaunt), at night only
		new Encounter("jaymoskingdoms:gaunt", "jaymoskingdoms:has_gaunt", 3, 1, 1, true),
		// the mud golem is no monster of legend any more but one of the swamps' common threats (Andy), and the witches'
		// allies at their huts (monsters/WitchHuts). The minotaur no longer roams the mountains: it keeps the great
		// dungeons under the Overworld (beasts/Labyrinths).
		new Encounter("jaymoskingdoms:mud_golem", "jaymoskingdoms:has_mud_golem", MUD_GOLEM_WEIGHT, 1, 2),
		// (0.7.137) the wind dragons (dragons/Dragons), rare: Notus over the badlands, Boreas over the frozen peaks, Zephyrus
		// in the swamps
		new Encounter("jaymoskingdoms:notus_dragon", "jaymoskingdoms:has_notus_dragon", 2, 1, 1),
		new Encounter("jaymoskingdoms:boreas_dragon", "jaymoskingdoms:has_boreas_dragon", 2, 1, 1),
		new Encounter("jaymoskingdoms:zephyrus_dragon", "jaymoskingdoms:has_zephyrus_dragon", 2, 1, 1));

	/** A beast after a player, until {@code until} (game time). */
	private record Hunt(Mob mob, UUID player, long until) {
	}

	private static final List<Hunt> HUNTS = new ArrayList<>();

	private Encounters() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Encounters::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> HUNTS.clear());
		wildSpawns();
		if (FabricLoader.getInstance().isModLoaded(CUBE)) {
			BiomeModifications.create(JaymosKingdoms.id("cube_animals_duplicates")).add(ModificationPhase.REMOVALS, BiomeSelectors.all(), context -> {
				for (String name : CUBE_DUPLICATES) {
					EntityType<?> type = type(CUBE + ":" + name);
					if (type != null) {
						context.getMobSpawnSettings().removeSpawnsOfEntityType(type);
					}
				}
			});
		}
		if (!FabricLoader.getInstance().isModLoaded(MOD)) {
			return;
		}
		BiomeModifications.create(JaymosKingdoms.id("naturalist_encounters")).add(ModificationPhase.REMOVALS, BiomeSelectors.all(), context -> {
			if (!WILDLIFE) {
				context.getMobSpawnSettings().removeSpawns((category, spawner) -> MOD.equals(BuiltInRegistries.ENTITY_TYPE.getKey(spawner.type()).getNamespace()));
				return;
			}
			for (String name : NO_NATURAL_SPAWNS) {
				EntityType<?> type = type(MOD + ":" + name);
				if (type != null) {
					context.getMobSpawnSettings().removeSpawnsOfEntityType(type);
				}
			}
		});
	}

	private static @Nullable EntityType<?> type(String id) {
		return BuiltInRegistries.ENTITY_TYPE.getOptional(Identifier.parse(id)).orElse(null);
	}

	private static void tick(MinecraftServer server) {
		ServerLevel level = Factions.level(server);
		long now = level.getGameTime();
		if (now % HUNT_CHECK_TICKS == 0 && !HUNTS.isEmpty()) {
			hunts(server, now);
		}
	}

	/**
	 * (0.7.526) Every Kingdoms beast in {@link #TABLE} spawns on its own where it roams (its biome tag), by its weight and
	 * pack size, in its own category (creatures among the animals, monsters among the monsters). The wild boar has its own
	 * spawns already ({@code beasts/Beasts}).
	 */
	private static void wildSpawns() {
		for (Encounter e : TABLE) {
			Identifier id = Identifier.parse(e.mob());
			if (!JaymosKingdoms.MOD_ID.equals(id.getNamespace()) || "wild_boar".equals(id.getPath())) {
				continue;
			}
			EntityType<?> type = type(e.mob());
			if (type == null) {
				continue;
			}
			boolean legend = Monsters.legend(type), dragon = id.getPath().endsWith("_dragon"), cave = "cave_ogre".equals(id.getPath());
			placement(type, e.night(), cave, legend, dragon, "tarantula".equals(id.getPath()) || "gaunt".equals(id.getPath()));
			BiomeModifications.addSpawn(BiomeSelectors.tag(TagKey.create(Registries.BIOME, Identifier.parse(e.biomes()))), type.getCategory(), type,
				e.weight(), e.min(), e.max());
		}
	}

	/** Where and when a beast of {@link #TABLE} may spawn on its own (kept if its kind has its own rules already). */
	@SuppressWarnings("unchecked")
	private static void placement(EntityType<?> type, boolean night, boolean cave, boolean legend, boolean dragon, boolean underTrees) {
		try {
			SpawnPlacements.register((EntityType<Mob>) type, SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
				(t, level, reason, pos, random) -> wildSpawn(t, level, reason, pos, random, night, cave, legend, dragon, underTrees));
		} catch (IllegalStateException already) {
			// the fire ogre and the flame wraith keep the Nether's monster rules (monsters/Monsters)
		}
	}

	private static boolean wildSpawn(EntityType<? extends Mob> type, ServerLevelAccessor level, EntitySpawnReason reason, BlockPos pos,
		RandomSource random, boolean night, boolean cave, boolean legend, boolean dragon, boolean underTrees) {
		if (!Mob.checkMobSpawnRules(type, level, reason, pos, random)) {
			return false;
		}
		boolean monster = type.getCategory() == MobCategory.MONSTER;
		if (monster && level.getDifficulty() == Difficulty.PEACEFUL) {
			return false;
		}
		// (the dragons stay out of the Overworld until someone has killed one: DragonRecords#worldSlain)
		if (dragon && !com.jaymoskingdoms.dragons.DragonRecords.worldSlain()) {
			return false;
		}
		boolean dark = level.getLevel().isDarkOutside();
		if (underTrees) {
			// (the tarantula: under the jungle's canopy or in the open, but only once night has fallen)
			if (!dark) {
				return false;
			}
		} else if (!cave && !level.canSeeSky(pos)) {
			return false;
		}
		if ((night || cave) && !Monster.isDarkEnoughToSpawn(level, pos, random)) {
			return false;
		}
		// (the cut for the monsters that walk by day applies in daylight only: by night they come as often as any)
		if (legend && random.nextFloat() >= LEGEND_CHANCE || monster && !night && !cave && !dark && random.nextFloat() >= DAY_MONSTER_CHANCE) {
			return false;
		}
		if (legend || dragon) {
			// not while the land is first made (off the server thread): they come later, among the living
			if (reason == EntitySpawnReason.CHUNK_GENERATION) {
				return false;
			}
			ServerLevel server = level.getLevel();
			return Factions.around(server.getServer(), server, pos, SETTLEMENT_CLEARANCE) == null;
		}
		return true;
	}

	/** Out on the surface, playing, and well away from every settlement. */
	public static boolean inTheWild(MinecraftServer server, ServerLevel level, ServerPlayer player) {
		if (!player.isAlive() || player.isCreative() || player.isSpectator()) {
			return false;
		}
		BlockPos at = player.blockPosition();
		if (at.getY() < level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at.getX(), at.getZ()) - SURFACE_SLACK) {
			return false;
		}
		return Factions.around(server, level, at, SETTLEMENT_CLEARANCE) == null;
	}

	/**
	 * (0.5.56) Sends {@code count} of {@code type} after {@code player} from a spot near them (a quest's beast), after
	 * them for {@code huntTicks} and kept from despawning; none if no spot could be found.
	 */
	static List<Mob> send(ServerLevel level, ServerPlayer player, EntityType<?> type, int count, int huntTicks) {
		RandomSource random = level.getRandom();
		List<Mob> came = new ArrayList<>();
		for (int i = 0; i < SPOT_TRIES && came.isEmpty(); i++) {
			BlockPos spot = spot(level, player, random);
			if (spot == null) {
				continue;
			}
			for (int n = 0; n < count; n++) {
				BlockPos at = n == 0 ? spot : near(level, spot, random);
				Mob mob = at == null ? null : spawn(level, type, at, player, huntTicks);
				if (mob != null) {
					mob.setPersistenceRequired();
					came.add(mob);
				}
			}
		}
		return came;
	}

	/** A dry spot on the surface {@link #MIN_DISTANCE}-{@link #MAX_DISTANCE} blocks from the player, if one's loaded. */
	public static @Nullable BlockPos spot(ServerLevel level, ServerPlayer player, RandomSource random) {
		double angle = random.nextDouble() * Math.PI * 2.0;
		int distance = Mth.nextInt(random, MIN_DISTANCE, MAX_DISTANCE);
		BlockPos column = BlockPos.containing(player.getX() + Math.cos(angle) * distance, player.getY(), player.getZ() + Math.sin(angle) * distance);
		return dry(level, column, player.getBlockY());
	}

	/** A spot a few blocks from {@code spot}, for the rest of a pack. */
	private static @Nullable BlockPos near(ServerLevel level, BlockPos spot, RandomSource random) {
		return dry(level, spot.offset(random.nextInt(5) - 2, 0, random.nextInt(5) - 2), spot.getY());
	}

	private static @Nullable BlockPos dry(ServerLevel level, BlockPos column, int y) {
		if (!level.isLoaded(column)) {
			return null;
		}
		BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column);
		if (Math.abs(top.getY() - y) > MAX_RISE || !level.getFluidState(top).isEmpty() || !level.getFluidState(top.below()).isEmpty()) {
			return null;
		}
		return top;
	}

	/** One of the beasts that hunt {@code biome}, by weight; null if none does. */
	static @Nullable Encounter pick(Holder<Biome> biome, boolean night, RandomSource random) {
		return pick(biome, night, random, false);
	}

	/** {@link #pick}, among the monsters of legend only if {@code legends}. */
	static @Nullable Encounter pick(Holder<Biome> biome, boolean night, RandomSource random, boolean legends) {
		List<Encounter> fits = new ArrayList<>();
		int total = 0;
		for (Encounter e : TABLE) {
			EntityType<?> type = type(e.mob());
			if ((night || !e.night()) && type != null && (!legends || Monsters.legend(type))
				&& biome.is(TagKey.create(Registries.BIOME, Identifier.parse(e.biomes())))) {
				fits.add(e);
				total += e.weight();
			}
		}
		if (total <= 0) {
			return null;
		}
		int roll = random.nextInt(total);
		for (Encounter e : fits) {
			roll -= e.weight();
			if (roll < 0) {
				return e;
			}
		}
		return null;
	}

	private static @Nullable Mob spawn(ServerLevel level, EntityType<?> type, BlockPos at, ServerPlayer player, int huntTicks) {
		if (!(type.create(level, EntitySpawnReason.EVENT) instanceof Mob mob)) {
			return null;
		}
		mob.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, level.getRandom().nextFloat() * 360.0F, 0.0F);
		mob.finalizeSpawn(level, level.getCurrentDifficultyAt(at), EntitySpawnReason.EVENT, null);
		if (mob instanceof AgeableMob ageable) {
			ageable.setAge(0);
		}
		if (!level.noCollision(mob)) {
			return null;
		}
		level.addFreshEntity(mob);
		after(mob, player);
		HUNTS.add(new Hunt(mob, player.getUUID(), level.getGameTime() + huntTicks));
		return mob;
	}

	private static void after(Mob mob, ServerPlayer player) {
		// (0.7.600) wolves stalk them from cover first
		if (com.jaymoskingdoms.wild.WolfStalkers.hunt(mob, player)) {
			return;
		}
		mob.setTarget(player);
		if (mob instanceof NeutralMob neutral) {
			neutral.setPersistentAngerTarget(EntityReference.<LivingEntity>of(player));
			neutral.startPersistentAngerTimer();
		}
	}

	/** Keeps the beasts after their players; once the hunt's over, they're ordinary animals, or gone if no one is near. */
	private static void hunts(MinecraftServer server, long now) {
		for (Iterator<Hunt> it = HUNTS.iterator(); it.hasNext(); ) {
			Hunt h = it.next();
			Mob mob = h.mob();
			if (mob.isRemoved() || !mob.isAlive() || mob instanceof net.minecraft.world.entity.animal.equine.AbstractHorse tamed && tamed.isTamed()) {
				// (0.7.137) gone, or tamed (a dragon won over mid-hunt): no longer hunting, and never cleared away
				it.remove();
				continue;
			}
			ServerPlayer player = server.getPlayerList().getPlayer(h.player());
			boolean close = player != null && player.level() == mob.level() && player.isAlive() && !player.isCreative() && !player.isSpectator()
				&& player.distanceToSqr(mob) <= LOSE_RANGE * LOSE_RANGE;
			if (now < h.until()) {
				if (close && mob.getTarget() != player) {
					after(mob, player);
				}
				continue;
			}
			it.remove();
			if (mob.getTarget() == player) {
				mob.setTarget(null);
			}
			if (!close && mob.level().getNearestPlayer(mob, LOSE_RANGE) == null) {
				mob.discard();
			}
		}
	}
}
