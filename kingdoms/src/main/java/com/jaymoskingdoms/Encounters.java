package com.jaymoskingdoms;

import com.jaymoskingdoms.monsters.Monsters;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.biome.v1.ModificationPhase;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jspecify.annotations.Nullable;

/**
 * (0.5.55) Wilderness encounters with Naturalist's predators. Naturalist's bears, lions, tigers, alligators, komodo
 * dragons, rhinos and scorpions don't spawn on their own (nor its elephants and mammoths: Kingdoms has its own); instead,
 * now and then ({@link #CHANCE} every {@link #CHECK_TICKS}, no more than once per {@link #COOLDOWN_TICKS}), a player out
 * in the wild, on the surface and well away from every settlement ({@link #SETTLEMENT_CLEARANCE}), is found by whatever
 * hunts that biome (Naturalist's own biome tags: lions in savanna, alligators in swamps...). They come from
 * {@link #MIN_DISTANCE}-{@link #MAX_DISTANCE} blocks off, after that player, for {@link #HUNT_TICKS}; then they're
 * ordinary animals, gone if no one is near. Boars and snakes keep spawning as Naturalist has them and can be encounters
 * too. Kingdoms' own wild beasts (feral wolves, coyotes at night, tarantulas, moose; {@code wild/Wild}) come the same
 * way, and (0.5.56) the monsters of legend ({@code monsters/Monsters}), each a sighting folk talk about ({@link Sightings}).
 * Naturalist's are by id, so without it only Kingdoms' own come.
 */
public final class Encounters {
	static final String MOD = "naturalist";
	/** Keep Naturalist's other animals in natural spawning (false: its mobs only ever come as encounters). */
	static final boolean WILDLIFE = true;
	/** Naturalist mobs kept out of natural spawning: the encounter beasts, and the elephants, mammoths and whales Kingdoms has. */
	static final List<String> NO_NATURAL_SPAWNS = List.of("bear", "lion", "tiger", "alligator", "komodo_dragon", "rhino",
		"desert_scorpion", "jungle_scorpion", "elephant", "mammoth",
		// (0.7.206) its whales: Kingdoms' great whales take their place (whaling)
		"whale");
	/** How often the players' surroundings are weighed (ticks). */
	static final int CHECK_TICKS = 400;
	/** The chance, at each check, that something finds a player out in the wild (about once in 15 minutes there). */
	static final float CHANCE = 1.0F / 45.0F;
	/** A player's quiet spell after an encounter, and after joining (ticks; 20 minutes). */
	static final int COOLDOWN_TICKS = 24000;
	/** No encounter this close to a settlement's seat (blocks), nor on any settlement's land. */
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
		new Encounter("naturalist:boar", "naturalist:has_boar", 6, 2, 4),
		new Encounter("naturalist:snake", "naturalist:has_rattlesnake", 4, 1, 1),
		new Encounter("naturalist:snake", "naturalist:has_coral_snake", 4, 1, 1),
		// Kingdoms' own, after Better Animals Plus (wild/Wild)
		new Encounter("jaymoskingdoms:feral_wolf", "jaymoskingdoms:has_feral_wolf", 10, 3, 5),
		new Encounter("jaymoskingdoms:coyote", "jaymoskingdoms:has_coyote", 10, 2, 3, true),
		new Encounter("jaymoskingdoms:tarantula", "jaymoskingdoms:has_tarantula", 8, 1, 2),
		new Encounter("jaymoskingdoms:moose", "jaymoskingdoms:has_moose", 6, 1, 1),
		// (0.5.56) the monsters of legend (monsters/Monsters)
		new Encounter("jaymoskingdoms:werewolf", "jaymoskingdoms:has_werewolf", 5, 1, 1, true),
		new Encounter("jaymoskingdoms:ogre", "jaymoskingdoms:has_ogre", 4, 1, 1),
		new Encounter("jaymoskingdoms:cave_ogre", "jaymoskingdoms:has_cave_ogre", 4, 1, 1, true),
		new Encounter("jaymoskingdoms:fire_ogre", "jaymoskingdoms:has_fire_ogre", 3, 1, 1, true),
		new Encounter("jaymoskingdoms:wraith", "jaymoskingdoms:has_wraith", 5, 1, 2, true),
		new Encounter("jaymoskingdoms:flame_wraith", "jaymoskingdoms:has_flame_wraith", 3, 1, 1, true),
		new Encounter("jaymoskingdoms:silver_skeleton", "jaymoskingdoms:has_silver_skeleton", 5, 2, 3, true),
		new Encounter("jaymoskingdoms:mud_golem", "jaymoskingdoms:has_mud_golem", 6, 1, 2),
		// (0.7.137) the wind dragons (dragons/Dragons), rare: Notus over the badlands, Boreas over the frozen peaks, Zephyrus
		// in the swamps
		new Encounter("jaymoskingdoms:notus_dragon", "jaymoskingdoms:has_notus_dragon", 2, 1, 1),
		new Encounter("jaymoskingdoms:boreas_dragon", "jaymoskingdoms:has_boreas_dragon", 2, 1, 1),
		new Encounter("jaymoskingdoms:zephyrus_dragon", "jaymoskingdoms:has_zephyrus_dragon", 2, 1, 1));

	/** A beast after a player, until {@code until} (game time). */
	private record Hunt(Mob mob, UUID player, long until) {
	}

	/** When each player can next be found (game time). */
	private static final Map<UUID, Long> NEXT = new HashMap<>();
	private static final List<Hunt> HUNTS = new ArrayList<>();

	private Encounters() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Encounters::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			NEXT.clear();
			HUNTS.clear();
		});
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
		if (now % CHECK_TICKS != 0) {
			return;
		}
		for (ServerPlayer player : level.players()) {
			long next = NEXT.computeIfAbsent(player.getUUID(), id -> now + COOLDOWN_TICKS);
			if (now >= next && inTheWild(server, level, player) && level.getRandom().nextFloat() < CHANCE && encounter(server, level, player)) {
				NEXT.put(player.getUUID(), now + COOLDOWN_TICKS);
			}
		}
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

	/** Sends whatever hunts there after {@code player}; false if nothing could come. */
	static boolean encounter(MinecraftServer server, ServerLevel level, ServerPlayer player) {
		RandomSource random = level.getRandom();
		for (int i = 0; i < SPOT_TRIES; i++) {
			BlockPos spot = spot(level, player, random);
			if (spot == null || Factions.around(server, level, spot, SETTLEMENT_CLEARANCE) != null) {
				continue;
			}
			Encounter e = pick(level.getBiome(spot), level.isDarkOutside(), random);
			EntityType<?> type = e == null ? null : type(e.mob());
			if (type == null) {
				continue;
			}
			int count = Mth.nextInt(random, e.min(), e.max()), came = 0;
			for (int n = 0; n < count; n++) {
				BlockPos at = n == 0 ? spot : near(level, spot, random);
				if (at != null && spawn(level, type, at, player, HUNT_TICKS) != null) {
					came++;
				}
			}
			if (came > 0) {
				player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.encounter." + e.name()).withStyle(ChatFormatting.GOLD));
				Sightings.seen(level, type, spot);
				JaymosKingdoms.LOGGER.debug("{} {} came after {} at {}", came, e.mob(), player.getName().getString(), spot);
				return true;
			}
		}
		return false;
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
