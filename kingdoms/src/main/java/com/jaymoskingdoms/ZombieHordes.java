package com.jaymoskingdoms;

import com.jaymoskingdoms.reaped.Reaped;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.monster.hoglin.Hoglin;
import net.minecraft.world.entity.monster.illager.Vindicator;
import net.minecraft.world.entity.monster.piglin.AbstractPiglin;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import net.minecraft.world.entity.monster.zombie.Drowned;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.monster.zombie.ZombifiedPiglin;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Zombie hordes (Andy, 2026-11-14: "zombies should be more abundant and travel in hordes, with the largest numbers in the
 * future and during blood moons"). Ideas from Harder Hordes (vm_madness, MIT) and Undead Nights (MCModsPete, ARR): only
 * the ideas, our own code.
 * <p>
 * The old dead of the Overworld (Lore.md, "The dead") walk together now. Every {@link #ROLL_EVERY} ticks each player may
 * draw a horde: a pack that comes up out of sight, behind them {@link #SPAWN_MIN}-{@link #SPAWN_MAX} blocks away (or in
 * the dark tunnels round them, {@link #CAVE_MIN}-{@link #CAVE_MAX} off, when they're underground), groans once (a sound,
 * never a line of text), and comes their way. How likely and how big follows a hidden danger ({@link #danger}): how old the
 * world is (days since the {@link #GRACE_DAYS} quiet ones, full after {@link #RAMP_DAYS} more), the player's world tier
 * ({@link WorldTiers}, the SevTech ladder), the armour they wear, and the game's own local difficulty. On the surface
 * hordes come at night, under rain ({@link #DAY_DIVISOR} times rarer), and on overcast horde days ({@link HordeDays});
 * underground, at any hour. A blood moon makes them come often and twice as big (a super blood moon, three times), and
 * the dead among them claw up out of the ground like the moon's own ({@link Moons}).
 * <p>
 * Andy's picks from the two mods (2026-11-14), each in its place below: what a horde is ({@link Kind}: the dead, skeletons
 * (never creepers), drowned by the water, the dead with archers behind, the Nether's (never blazes), and the illagers' hit
 * squads sent after a player who's become a threat; and the Reaper's host, {@link Reaped}, dead illagers who march on the
 * nearest settlement or camp and take the living illagers they kill into their ranks); grave-captains with a boss bar, undead pirate lobbers with powder
 * kegs, one member now and then carrying loot that's sure to drop, and more health and harm as the danger climbs
 * ({@link #special}); climbing over each other, crossing water and breaking through ({@link HordeMoves}); bell wards
 * ({@link HordeWards}); the grave scent and the grave lure ({@link HordeLure}); the danger hinted at in the Rung Journal.
 * <p>
 * A horde keeps together: every {@link #MARCH_EVERY} ticks its idle members are led ({@link Senses#lure}, quietly, so a
 * louder noise still draws them off) to where the player they came for is, each a step off its neighbours; once the
 * player is beyond {@link #TRACK_RANGE} it wanders on instead (a hit squad never loses them). Idle zombies a horde of the
 * dead passes fall in with it, up to {@link #MAX_MEMBERS}. When one of them goes for someone, the rest are called to the
 * fight. Only part of each horde is armed (more as the danger climbs): with a dead villager's old tools at Hearth, and
 * weapons of the world tier's grades above it ({@link MonsterGear}); their tier's strength and armour still come from
 * {@link WorldTiers}. Lit ground keeps them from coming up there. At sunrise (for a surface horde of the dead), or after
 * {@link #MAX_AGE}, a horde breaks up.
 * <p>
 * Operators: {@code /kingdom horde} sends one at once.
 */
public final class ZombieHordes {
	/** How often each player rolls for a horde, and how often the hordes march (ticks). */
	static final int ROLL_EVERY = 200, MARCH_EVERY = 40;
	/** No hordes in the world's first days; the age part of the danger is full this many days after. */
	static final long GRACE_DAYS = 3, RAMP_DAYS = 60;
	/** The danger's parts (they add up to 1): world age, world tier, armour, local difficulty. */
	static final float AGE_WEIGHT = 0.45F, TIER_WEIGHT = 0.25F, GEAR_WEIGHT = 0.15F, LOCAL_WEIGHT = 0.15F;
	/** The chance per roll at night, at no danger and the extra at full danger; by day under rain, divided by this. */
	static final float NIGHT_CHANCE = 0.02F, NIGHT_CHANCE_DANGER = 0.06F, DAY_DIVISOR = 10.0F;
	/** Underground by day, the night's chance divided by this. */
	static final float UNDER_DAY_DIVISOR = 3.0F;
	/** Under a blood moon, the chance per roll (super blood moon: the second). */
	static final float BLOOD_CHANCE = 0.2F, SUPER_BLOOD_CHANCE = 0.3F;
	/** In the Nether (only from {@link #NETHER_TIER} up): the chance per roll at no danger, and the extra at full. */
	static final float NETHER_CHANCE = 0.015F, NETHER_CHANCE_DANGER = 0.04F;
	static final int NETHER_TIER = 2;
	/** After a horde, how long until the same player draws another (ticks); under a blood moon. */
	static final int COOLDOWN = 2400, BLOOD_COOLDOWN = 900;
	/** A horde's size at no danger and at full danger (a few either way); times this under a blood or super blood moon. */
	static final int SIZE_MIN = 3, SIZE_MAX = 12;
	static final int BLOOD_SIZE = 2, SUPER_SIZE = 3;
	/** A hit squad's size. */
	static final int SQUAD_MIN = 3, SQUAD_MAX = 6;
	/** The most monsters near a player (within {@link #COUNT_RADIUS}) a new horde may bring it to: at no danger, the extra at full. */
	static final int NEAR_CAP = 12, NEAR_CAP_DANGER = 20;
	static final float BLOOD_CAP = 1.5F, SUPER_CAP = 2.0F;
	static final double COUNT_RADIUS = 64.0;
	/** Where it comes up on the surface: this far, behind the player (within this angle either side of straight behind), spread this wide. */
	static final int SPAWN_MIN = 40, SPAWN_MAX = 56, SPREAD = 4;
	static final double BEHIND = Math.PI / 3.0;
	/** Underground or in the Nether: this far off, out of the player's line of sight, within this height of them. */
	static final int CAVE_MIN = 20, CAVE_MAX = 36, CAVE_SPREAD = 3, CAVE_HEIGHT = 10;
	/** A player is underground when the surface is this far above them and they can't see the sky. */
	static final int UNDERGROUND = 6;
	/** Its members stand no higher or lower than this from the player (on the surface). */
	static final int SPAWN_HEIGHT = 24;
	/** The most block light where they come up: the Overworld, the Nether. */
	static final int DARK = 0, NETHER_DARK = 11;
	/** The share of a horde that's armed: at no danger, the extra at full. */
	static final float ARMED = 0.15F, ARMED_DANGER = 0.35F;
	/** How far a horde follows the player it came for; how far it wanders on once they're lost. */
	static final double TRACK_RANGE = 96.0, WANDER = 24.0;
	/** A hit squad goes for the player it was sent after once they're this near and in sight. */
	static final double SQUAD_SIGHT = 24.0;
	/** How loud the march is to its members' senses (a sprint is 12, a held torch 6) and the call to a fight. */
	static final double MARCH_LOUDNESS = 2.0, FIGHT_LOUDNESS = 9.0;
	/** Idle zombies this near a horde of the dead fall in with it, up to this many members (under a blood moon, the second). */
	static final double GATHER_RANGE = 12.0;
	static final int MAX_MEMBERS = 24, BLOOD_MAX_MEMBERS = 40;
	/** A horde breaks up after this long (ticks), if the dawn hasn't come first. */
	static final int MAX_AGE = 12000;
	/** A host its keeper called: how many rise, how long they stay (ticks), and how lately the keeper must have struck someone for them to go after it. */
	/** A host of the Reaper's this big, with a seat to march on, brings a mangonel. */
	static final int SIEGE_HOST = 10;
	/** By the water, how often a host of the Reaper's comes by long ship. */
	static final float LONGSHIP_CHANCE = 0.5F;
	static final int CALL_SIZE = 6, CALL_AGE = 20 * 120, CALL_FIGHT_TICKS = 200;
	/** How far its groan carries (volume: 16 blocks per). */
	static final float GROAN_VOLUME = 4.0F;

	/** Which kind a horde is, by the danger: skeletons from this, the dead with archers from this, hit squads from this (or Wildlands). */
	static final float SKELETON_DANGER = 0.2F, MIXED_DANGER = 0.5F, SQUAD_DANGER = 0.6F;
	static final float SKELETON_CHANCE = 0.25F, MIXED_CHANCE = 0.2F, DROWNED_CHANCE = 0.5F, SQUAD_CHANCE = 0.06F;
	/** The Reaper's host: from this danger, this chance (under a blood moon, the second). */
	static final float REAPED_DANGER = 0.3F, REAPED_CHANCE = 0.1F, REAPED_BLOOD_CHANCE = 0.15F;
	/** It marches on the nearest settlement or camp whose seat is within this many blocks of where it rose, and holds within this many of the seat. */
	static final double WAR_REACH = 128.0, WAR_HOLD = 12.0;
	/** A warlord leads it: this chance at no danger plus the extra at full. */
	static final float WARLORD_CHANCE = 0.2F, WARLORD_CHANCE_DANGER = 0.4F;
	/** A hit squad has an evoker leading it from this world tier (Dread); so has the Reaper's host. */
	static final int EVOKER_TIER = 3;

	/** Extra health and harm for every member, at full danger (shares of their own). */
	static final double DANGER_HEALTH = 0.6, DANGER_DAMAGE = 0.4;
	/** A grave-captain: from this danger, this chance at no danger plus the extra at full; its size, health and harm (shares), knockback resistance. */
	static final float CAPTAIN_DANGER = 0.35F, CAPTAIN_CHANCE = 0.1F, CAPTAIN_CHANCE_DANGER = 0.25F;
	static final double CAPTAIN_SCALE = 0.3, CAPTAIN_HEALTH = 2.0, CAPTAIN_DAMAGE = 0.75, CAPTAIN_KNOCKBACK = 0.6;
	/** A pirate lobber: from this danger, this chance in a horde of the dead (by the water, the second); its kegs, and their blast. */
	static final float LOBBER_DANGER = 0.3F, LOBBER_CHANCE = 0.15F, LOBBER_WATER_CHANCE = 0.35F;
	static final int LOBBER_KEGS = 3;
	static final float KEG_POWER = 2.5F;
	/** A lobber throws at a foe between these distances, once every this many ticks; the keg's fuse (ticks, plus one a block). */
	static final double LOB_MIN = 5.0, LOB_MAX = 16.0;
	static final int LOB_EVERY = 80, KEG_FUSE = 40;
	/** Now and then one member carries loot (sure to drop, never worn out), shown by its glint. */
	static final float LOOT_CHANCE = 0.35F;
	static final int LOOT_ENCHANT_LEVEL = 12;

	/** Every member carries this tag (a member left over after a restart is just a monster). */
	static final String TAG = "jaymoskingdoms.horde";
	public static final String CAPTAIN_TAG = "jaymoskingdoms.grave_captain", LOBBER_TAG = "jaymoskingdoms.horde_lobber",
		LOOT_TAG = "jaymoskingdoms.horde_loot";

	private static final Identifier HEALTH_ID = JaymosKingdoms.id("horde_health"), DAMAGE_ID = JaymosKingdoms.id("horde_damage"),
		CAPTAIN_SCALE_ID = JaymosKingdoms.id("grave_captain_scale"), CAPTAIN_HEALTH_ID = JaymosKingdoms.id("grave_captain_health"),
		CAPTAIN_DAMAGE_ID = JaymosKingdoms.id("grave_captain_damage"), CAPTAIN_KNOCKBACK_ID = JaymosKingdoms.id("grave_captain_knockback");

	/** A pirate's colours for the lobber's coat. */
	private static final int[] PIRATE_COATS = {0x7A1E1E, 0x1E2A4A, 0x3A2A1A};

	/** What a horde is. */
	enum Kind {
		/** The old dead: zombies, husks in the desert. */
		DEAD,
		/** Skeletons, strays in the snow (never creepers, Andy). */
		SKELETONS,
		/** The drowned, by the water. */
		DROWNED,
		/** The dead in front, skeleton archers behind. */
		MIXED,
		/** The Nether's: piglins, hoglins, wither skeletons, magma cubes (never blazes, Andy). */
		NETHER,
		/** The illagers' hit squad, put together to deal with a player who's become a threat (Andy): it never loses them. */
		SQUAD,
		/** The Reaper's host ({@link Reaped}): dead illagers, at war with the living. They don't burn, and when the host breaks up the Reaper takes them back. */
		REAPED;

		boolean dead() {
			return this == DEAD || this == DROWNED || this == MIXED;
		}
	}

	static final class Horde {
		final ServerLevel level;
		final UUID hunted;
		final Kind kind;
		final boolean blood, cave;
		final float danger;
		final List<Mob> members = new ArrayList<>();
		final long born;
		Vec3 goal;
		/** The seat the Reaper's host marches on, if one was in reach. */
		@Nullable Vec3 war;
		/** The mangonel a big host of the Reaper's pushes to its war ({@link ReapedHost#engine}). */
		@Nullable Mangonel engine;
		/** A host its keeper called ({@link #call}): it keeps to them, fights what they fight, and is soon taken back. */
		boolean called;

		Horde(ServerLevel level, UUID hunted, Kind kind, boolean blood, boolean cave, float danger, Vec3 goal) {
			this.level = level;
			this.hunted = hunted;
			this.kind = kind;
			this.blood = blood;
			this.cave = cave;
			this.danger = danger;
			this.goal = goal;
			this.born = level.getGameTime();
		}

		/** It stays out by day: underground, in the Nether, or the illagers (they don't burn). */
		boolean daylit() {
			return this.cave || this.kind == Kind.NETHER || this.kind == Kind.SQUAD || this.kind == Kind.REAPED;
		}
	}

	private static final List<Horde> HORDES = new ArrayList<>();
	/** Player → the game time they may next draw a horde. */
	private static final Map<UUID, Long> NEXT = new HashMap<>();
	/** Grave-captains loaded, and their bars. */
	private static final Map<Mob, ServerBossEvent> CAPTAINS = new WeakHashMap<>();
	/** Lobbers loaded, and when each last threw. */
	private static final Map<Mob, Long> LOBBERS = new WeakHashMap<>();

	/** The kind the next horde sent is, whatever would have come ({@code /kingdom horde reaped}). */
	private static @Nullable Kind forced;

	private ZombieHordes() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(ZombieHordes::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			HORDES.clear();
			NEXT.clear();
			CAPTAINS.values().forEach(ServerBossEvent::removeAllPlayers);
			CAPTAINS.clear();
			LOBBERS.clear();
		});
		// a captain or a lobber loaded again (a restart, a chunk come back) keeps its part
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof Mob mob) {
				if (mob.entityTags().contains(CAPTAIN_TAG)) {
					CAPTAINS.computeIfAbsent(mob, ZombieHordes::bar);
				}
				if (mob.entityTags().contains(LOBBER_TAG)) {
					LOBBERS.putIfAbsent(mob, 0L);
				}
			}
		});
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
			if (entity instanceof Mob mob) {
				ServerBossEvent bar = CAPTAINS.remove(mob);
				if (bar != null) {
					bar.removeAllPlayers();
				}
			}
		});
		ServerLivingEntityEvents.AFTER_DEATH.register(HordeLure::slain);
		CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) -> dispatcher.register(Commands.literal("kingdom")
			.then(Commands.literal("horde").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)).executes(c -> {
				ServerPlayer player = c.getSource().getPlayerOrException();
				return send(player, Moons.now(c.getSource().getServer())) > 0 ? 1 : 0;
			}).then(Commands.literal("reaped").executes(c -> {
				// the Reaper's host, whatever the danger
				ServerPlayer player = c.getSource().getPlayerOrException();
				forced = Kind.REAPED;
				try {
					return send(player, Moons.Moon.NONE) > 0 ? 1 : 0;
				} finally {
					forced = null;
				}
			})))));
	}

	// ---- the danger -----------------------------------------------------------------------------------------------------

	/** The world's day (counted from its first). */
	static long day(MinecraftServer server) {
		return Math.floorDiv(server.overworld().getOverworldClockTime(), 24000L);
	}

	/** How dangerous {@code player}'s hordes are, 0 to 1: the world's age, their world tier, their armour, the local difficulty. */
	public static float danger(ServerPlayer player) {
		ServerLevel level = (ServerLevel) player.level();
		float age = Mth.clamp((day(level.getServer()) - GRACE_DAYS) / (float) RAMP_DAYS, 0.0F, 1.0F);
		float tier = WorldTiers.of(player).ordinal() / (float) (WorldTiers.Tier.values().length - 1);
		float gear = Mth.clamp(player.getArmorValue() / 20.0F, 0.0F, 1.0F);
		float local = Mth.clamp(level.getCurrentDifficultyAt(player.blockPosition()).getSpecialMultiplier(), 0.0F, 1.0F);
		return Mth.clamp(AGE_WEIGHT * age + TIER_WEIGHT * tier + GEAR_WEIGHT * gear + LOCAL_WEIGHT * local, 0.0F, 1.0F);
	}

	/** Whether the hordes have begun (the quiet days are over). */
	public static boolean begun(MinecraftServer server) {
		return day(server) >= GRACE_DAYS;
	}

	private static boolean night(ServerLevel overworld) {
		return Moons.nightOf(overworld.getOverworldClockTime()) >= 0;
	}

	/** On the Overworld's surface the dead walk: at night, under rain, or on an overcast horde day. */
	static boolean surfaceWeather(ServerLevel overworld) {
		return night(overworld) || overworld.isRaining() || HordeDays.overcast(overworld);
	}

	/** {@code player} is underground in the Overworld: the surface well above them and no sky in sight. */
	static boolean underground(ServerPlayer player) {
		Level level = player.level();
		if (level.dimension() != Level.OVERWORLD) {
			return false;
		}
		int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, player.getBlockX(), player.getBlockZ());
		return player.getY() < surface - UNDERGROUND && !level.canSeeSky(player.blockPosition());
	}

	// ---- every tick -----------------------------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		long ticks = server.getTickCount();
		if (!HORDES.isEmpty()) {
			for (Horde horde : HORDES) {
				if (horde.kind == Kind.SQUAD) {
					SquadMounts.row(horde.members, horde.goal);
				}
				HordeMoves.tick(horde.level, horde.members, horde.goal, horde.danger, horde.kind.dead() || horde.kind == Kind.SQUAD || horde.kind == Kind.NETHER || horde.kind == Kind.REAPED);
			}
			if (ticks % MARCH_EVERY == 0) {
				march();
			}
		}
		if (ticks % 10 == 0) {
			captains();
		}
		if (ticks % 20 == 0) {
			lobbers();
		}
		HordeWards.tick(server);
		if (ticks % ROLL_EVERY != 0 || !begun(server)) {
			return;
		}
		ServerLevel overworld = server.overworld();
		Moons.Moon moon = Moons.now(server);
		boolean night = night(overworld);
		long now = overworld.getGameTime();
		RandomSource random = overworld.getRandom();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (player.isSpectator() || player.isCreative() || !player.isAlive() || now < NEXT.getOrDefault(player.getUUID(), 0L)) {
				continue;
			}
			ServerLevel level = (ServerLevel) player.level();
			boolean nether = level.dimension() == Level.NETHER;
			if (level != overworld && !nether) {
				continue;
			}
			float danger = danger(player);
			boolean under = !nether && underground(player);
			float chance;
			if (nether) {
				if (WorldTiers.of(player).ordinal() < NETHER_TIER) {
					continue;
				}
				chance = NETHER_CHANCE + NETHER_CHANCE_DANGER * danger;
			} else if (moon.blood() && !under) {
				chance = moon == Moons.Moon.SUPER_BLOOD ? SUPER_BLOOD_CHANCE : BLOOD_CHANCE;
			} else if (under) {
				chance = NIGHT_CHANCE + NIGHT_CHANCE_DANGER * danger;
				if (!night) {
					chance /= UNDER_DAY_DIVISOR;
				}
			} else if (night || HordeDays.overcast(overworld)) {
				chance = NIGHT_CHANCE + NIGHT_CHANCE_DANGER * danger;
			} else if (overworld.isRaining()) {
				chance = (NIGHT_CHANCE + NIGHT_CHANCE_DANGER * danger) / DAY_DIVISOR;
			} else {
				continue;
			}
			chance *= HordeLure.scentFactor(player);
			if (random.nextFloat() < chance && send(player, nether ? Moons.Moon.NONE : moon) > 0) {
				int wait = moon.blood() ? BLOOD_COOLDOWN : COOLDOWN;
				NEXT.put(player.getUUID(), now + Math.round(wait / HordeLure.scentFactor(player)));
			}
		}
	}

	// ---- a horde comes --------------------------------------------------------------------------------------------------

	/** What kind of horde comes for {@code player} at {@code centre}. */
	private static Kind kind(ServerLevel level, ServerPlayer player, BlockPos centre, float danger, boolean blood, RandomSource random) {
		if (forced != null) {
			return forced;
		}
		if (level.dimension() == Level.NETHER) {
			return Kind.NETHER;
		}
		if (danger >= REAPED_DANGER && random.nextFloat() < (blood ? REAPED_BLOOD_CHANCE : REAPED_CHANCE)) {
			return Kind.REAPED;
		}
		if (blood) {
			// a blood moon wakes the dead
			return random.nextFloat() < 0.25F ? Kind.MIXED : Kind.DEAD;
		}
		int tier = WorldTiers.of(player).ordinal();
		if ((danger >= SQUAD_DANGER || tier >= 2) && random.nextFloat() < SQUAD_CHANCE) {
			return Kind.SQUAD;
		}
		if (byWater(level, centre) && random.nextFloat() < DROWNED_CHANCE) {
			return Kind.DROWNED;
		}
		if (danger >= MIXED_DANGER && random.nextFloat() < MIXED_CHANCE) {
			return Kind.MIXED;
		}
		if (danger >= SKELETON_DANGER && random.nextFloat() < SKELETON_CHANCE) {
			return Kind.SKELETONS;
		}
		return Kind.DEAD;
	}

	private static boolean byWater(ServerLevel level, BlockPos centre) {
		var biome = level.getBiome(centre);
		if (biome.is(BiomeTags.IS_OCEAN) || biome.is(BiomeTags.IS_RIVER) || biome.is(BiomeTags.IS_BEACH)) {
			return true;
		}
		for (int i = 0; i < 8; i++) {
			BlockPos p = centre.offset((i & 1) * 12 - 6, 0, ((i >> 1) & 1) * 12 - 6);
			if (level.isLoaded(p) && !level.getFluidState(level.getHeightmapPos(Heightmap.Types.WORLD_SURFACE, p).below()).isEmpty()) {
				return true;
			}
		}
		return false;
	}

	/** Who stands in a horde of {@code kind}: the {@code index}th member. */
	private static EntityType<? extends Mob> member(Kind kind, ServerLevel level, BlockPos column, int index, int tier, RandomSource random) {
		return switch (kind) {
			case DEAD -> level.getBiome(column).is(Biomes.DESERT) || level.getBiome(column).is(Biomes.BADLANDS) ? EntityTypes.HUSK : EntityTypes.ZOMBIE;
			case SKELETONS -> level.getBiome(column).value().coldEnoughToSnow(column, level.getSeaLevel()) ? EntityTypes.STRAY : EntityTypes.SKELETON;
			case DROWNED -> EntityTypes.DROWNED;
			case MIXED -> index % 3 == 2 ? EntityTypes.SKELETON : EntityTypes.ZOMBIE;
			case NETHER -> {
				float r = random.nextFloat();
				yield r < 0.3F ? EntityTypes.PIGLIN : r < 0.5F ? EntityTypes.HOGLIN : r < 0.8F ? EntityTypes.WITHER_SKELETON : EntityTypes.MAGMA_CUBE;
			}
			case REAPED -> index == 0 && tier >= EVOKER_TIER ? Reaped.evoker() : random.nextFloat() < 0.55F ? Reaped.vindicator() : Reaped.pillager();
			case SQUAD -> index == 0 && tier >= EVOKER_TIER ? EntityTypes.EVOKER : random.nextFloat() < 0.6F ? EntityTypes.VINDICATOR : EntityTypes.PILLAGER;
		};
	}

	/** Sends a horde {@code player}'s way, out of their sight. How many came (0 if there was no room or no place). */
	static int send(ServerPlayer player, Moons.Moon moon) {
		ServerLevel level = (ServerLevel) player.level();
		boolean nether = level.dimension() == Level.NETHER;
		if (level != level.getServer().overworld() && !nether) {
			return 0;
		}
		RandomSource random = level.getRandom();
		float danger = danger(player);
		boolean cave = nether || underground(player);
		boolean blood = moon.blood() && !cave;
		int size = SIZE_MIN + Math.round(danger * (SIZE_MAX - SIZE_MIN)) + random.nextInt(3) - 1;
		float capFactor = 1.0F;
		if (blood) {
			size *= moon == Moons.Moon.SUPER_BLOOD ? SUPER_SIZE : BLOOD_SIZE;
			capFactor = moon == Moons.Moon.SUPER_BLOOD ? SUPER_CAP : BLOOD_CAP;
		}
		int cap = Math.round((NEAR_CAP + NEAR_CAP_DANGER * danger) * capFactor);
		int near = level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(COUNT_RADIUS), m -> m.isAlive() && m instanceof net.minecraft.world.entity.monster.Enemy).size();
		int tier = WorldTiers.of(player).ordinal();
		for (int attempt = 0; attempt < 6; attempt++) {
			BlockPos centre = cave ? caveCentre(level, player, random) : surfaceCentre(level, player, random);
			if (centre == null || HordeWards.warded(level, centre)) {
				continue;
			}
			Kind kind = kind(level, player, centre, danger, blood, random);
			int want = kind == Kind.SQUAD ? Mth.nextInt(random, SQUAD_MIN, SQUAD_MAX) : Math.min(size, cap - near);
			if (want < 2) {
				return 0;
			}
			Horde horde = new Horde(level, player.getUUID(), kind, blood, cave, danger, player.position());
			float armed = ARMED + ARMED_DANGER * danger;
			boolean shipped = false;
			if (kind == Kind.REAPED && !cave && byWater(level, centre) && random.nextFloat() < LONGSHIP_CHANCE) {
				// by the water the host comes in a black long ship, moored off the shore, and wades in from her deck
				for (BlockPos at : ReapedHost.longship(level, centre, random)) {
					if (horde.members.size() < want) {
						place(level, horde, at, tier, random);
						shipped = true;
					}
				}
			}
			for (int tries = 0; !shipped && horde.members.size() < want && tries < want * 4; tries++) {
				Mob mob = rise(level, player, horde, centre, horde.members.size(), random.nextFloat() < armed, tier, random);
				if (mob != null) {
					horde.members.add(mob);
				}
			}
			if (horde.members.isEmpty()) {
				continue;
			}
			special(level, horde, tier, random);
			if (kind == Kind.SQUAD && !cave) {
				SquadMounts.mount(level, horde.members, centre, random);
			}
			if (kind == Kind.REAPED) {
				horde.war = warSeat(level, centre);
				ReapedHost.mount(level, horde.members, random);
				if (horde.war != null && horde.members.size() >= SIEGE_HOST) {
					horde.engine = ReapedHost.engine(level, horde.members, horde.war);
				}
			}
			HORDES.add(horde);
			level.playSound(null, centre.getX() + 0.5, centre.getY(), centre.getZ() + 0.5, groan(kind), SoundSource.HOSTILE, GROAN_VOLUME, 0.55F);
			JaymosKingdoms.LOGGER.debug("a {} horde of {} came for {} (danger {})", kind, horde.members.size(), player.getScoreboardName(), danger);
			return horde.members.size();
		}
		return 0;
	}

	/** Where the Reaper's host goes to war: the seat of the nearest standing settlement or camp within {@link #WAR_REACH} of {@code centre}, or null. */
	private static @Nullable Vec3 warSeat(ServerLevel level, BlockPos centre) {
		if (level.dimension() != Level.OVERWORLD) {
			return null;
		}
		Faction best = null;
		double bestD = WAR_REACH * WAR_REACH;
		for (Faction f : Factions.all(level.getServer())) {
			double d = f.seat().distSqr(centre);
			if (!f.fallen() && d <= bestD && !ReapedHolds.is(level.getServer(), f.id())) {
				best = f;
				bestD = d;
			}
		}
		return best == null ? null : Vec3.atBottomCenterOf(best.seat());
	}

	private static net.minecraft.sounds.SoundEvent groan(Kind kind) {
		return switch (kind) {
			case REAPED -> SoundEvents.RAID_HORN.value();
			case SKELETONS -> SoundEvents.SKELETON_AMBIENT;
			case DROWNED -> SoundEvents.DROWNED_AMBIENT;
			case NETHER -> SoundEvents.HOGLIN_ANGRY;
			case SQUAD -> SoundEvents.VINDICATOR_AMBIENT;
			default -> SoundEvents.ZOMBIE_AMBIENT;
		};
	}

	/** Out on the surface: a column behind the player, where they aren't looking. */
	private static @Nullable BlockPos surfaceCentre(ServerLevel level, ServerPlayer player, RandomSource random) {
		float yaw = player.getYRot() * Mth.DEG_TO_RAD;
		double angle = Math.atan2(Mth.cos(yaw), -Mth.sin(yaw)) + Math.PI + (random.nextDouble() * 2.0 - 1.0) * BEHIND;
		int dist = Mth.nextInt(random, SPAWN_MIN, SPAWN_MAX);
		BlockPos centre = BlockPos.containing(player.getX() + Math.cos(angle) * dist, player.getY(), player.getZ() + Math.sin(angle) * dist);
		return Loaded.made(level, centre) && level.isPositionEntityTicking(centre) ? centre : null;
	}

	/** Underground or in the Nether: a dark spot with floor near the player's height, out of their line of sight. */
	private static @Nullable BlockPos caveCentre(ServerLevel level, ServerPlayer player, RandomSource random) {
		double angle = random.nextDouble() * Math.PI * 2.0;
		int dist = Mth.nextInt(random, CAVE_MIN, CAVE_MAX);
		BlockPos column = BlockPos.containing(player.getX() + Math.cos(angle) * dist, player.getY(), player.getZ() + Math.sin(angle) * dist);
		if (!Loaded.made(level, column) || !level.isPositionEntityTicking(column)) {
			return null;
		}
		BlockPos at = floorNear(level, column, CAVE_HEIGHT);
		if (at == null) {
			return null;
		}
		HitResult sight = level.clip(new ClipContext(player.getEyePosition(), Vec3.atCenterOf(at.above()), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
		return sight.getType() == HitResult.Type.MISS ? null : at;
	}

	/** The standing spot (air for the feet and head, solid floor, no fluid) nearest {@code column}'s height, within {@code reach}. */
	private static @Nullable BlockPos floorNear(ServerLevel level, BlockPos column, int reach) {
		for (int d = 0; d <= reach; d++) {
			for (int sign = -1; sign <= 1; sign += 2) {
				BlockPos p = column.above(d * sign);
				BlockPos below = p.below();
				if (level.getBlockState(p).isAir() && level.getBlockState(p.above()).isAir() && level.getFluidState(below).isEmpty()
					&& level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)) {
					return p;
				}
				if (d == 0) {
					break;
				}
			}
		}
		return null;
	}

	/** One of the horde, up near {@code centre} on dark, solid ground, or null if the spot won't do. */
	private static @Nullable Mob rise(ServerLevel level, ServerPlayer player, Horde horde, BlockPos centre, int index, boolean armed, int tier, RandomSource random) {
		int spread = horde.cave ? CAVE_SPREAD : SPREAD;
		BlockPos column = centre.offset(random.nextInt(spread * 2 + 1) - spread, 0, random.nextInt(spread * 2 + 1) - spread);
		if (!level.isPositionEntityTicking(column)) {
			return null;
		}
		EntityType<? extends Mob> type = member(horde.kind, level, column, index, tier, random);
		BlockPos at;
		if (horde.cave) {
			at = floorNear(level, column, 3);
			if (at == null) {
				return null;
			}
		} else {
			at = level.getHeightmapPos(SpawnPlacements.getHeightmapType(type), column);
			if (Math.abs(at.getY() - player.getY()) > SPAWN_HEIGHT) {
				return null;
			}
		}
		BlockPos below = at.below();
		int dark = level.dimension() == Level.NETHER ? NETHER_DARK : DARK;
		if (level.getBrightness(LightLayer.BLOCK, at) > dark || !level.getFluidState(at).isEmpty() || !level.getFluidState(below).isEmpty()
			|| !level.getBlockState(below).isFaceSturdy(level, below, Direction.UP) || !SpawnPlacements.isSpawnPositionOk(type, level, at)) {
			return null;
		}
		Mob mob = type.create(level, EntitySpawnReason.EVENT);
		if (mob == null) {
			return null;
		}
		mob.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, random.nextFloat() * 360.0F, 0.0F);
		if (!level.noCollision(mob)) {
			mob.discard();
			return null;
		}
		mob.finalizeSpawn(level, level.getCurrentDifficultyAt(at), EntitySpawnReason.EVENT, null);
		if (armed && (mob instanceof Zombie || mob instanceof Vindicator) && mob.getMainHandItem().isEmpty()) {
			mob.setItemSlot(EquipmentSlot.MAINHAND, tier <= 0 ? oldTool(random) : MonsterGear.weaponFor(level, random, tier));
		}
		join(mob, horde.danger);
		level.addFreshEntityWithPassengers(mob);
		return mob;
	}

	/** A dead villager's old tools: what the armed few carry at Hearth. */
	private static ItemStack oldTool(RandomSource random) {
		return new ItemStack(switch (random.nextInt(5)) {
			case 0 -> Items.STONE_HOE;
			case 1 -> Items.STONE_SHOVEL;
			case 2 -> Items.WOODEN_AXE;
			case 3 -> Items.STONE_AXE;
			default -> Items.WOODEN_SHOVEL;
		});
	}

	/** {@code mob} becomes one of a horde: the tag, the danger's extra health and harm, and it'll swim. */
	private static void join(Mob mob, float danger) {
		mob.addTag(TAG);
		mob.getNavigation().setCanFloat(true);
		DoorPounding.enlist(mob);
		modify(mob, Attributes.MAX_HEALTH, HEALTH_ID, DANGER_HEALTH * danger);
		modify(mob, Attributes.ATTACK_DAMAGE, DAMAGE_ID, DANGER_DAMAGE * danger);
		mob.setHealth(mob.getMaxHealth());
	}

	private static void modify(Mob mob, Holder<Attribute> attribute, Identifier id, double share) {
		AttributeInstance instance = mob.getAttribute(attribute);
		if (instance != null && share > 0.0) {
			instance.addOrReplacePermanentModifier(new AttributeModifier(id, share, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
		}
	}

	// ---- the special few --------------------------------------------------------------------------------------------------

	/** Now and then: a grave-captain, a pirate lobber, one carrying loot. */
	private static void special(ServerLevel level, Horde horde, int tier, RandomSource random) {
		List<Mob> pool = new ArrayList<>(horde.members);
		Collections.shuffle(pool, new java.util.Random(random.nextLong()));
		Iterator<Mob> next = pool.iterator();
		float danger = horde.danger;
		if (horde.kind == Kind.REAPED && random.nextFloat() < WARLORD_CHANCE + WARLORD_CHANCE_DANGER * danger) {
			Mob m = nextOf(next, Vindicator.class);
			if (m != null) {
				captain(m, "entity.jaymoskingdoms.reaped_warlord");
			}
		}
		if (horde.kind.dead() && danger >= CAPTAIN_DANGER && random.nextFloat() < CAPTAIN_CHANCE + CAPTAIN_CHANCE_DANGER * danger) {
			Mob m = nextOf(next, Zombie.class);
			if (m != null) {
				captain(m, "entity.jaymoskingdoms.grave_captain");
			}
		}
		if (horde.kind.dead() && danger >= LOBBER_DANGER
			&& random.nextFloat() < (horde.kind == Kind.DROWNED ? LOBBER_WATER_CHANCE : LOBBER_CHANCE)) {
			Mob m = nextOf(next, Zombie.class);
			if (m != null) {
				lobber(m, random);
			}
		}
		if (horde.kind != Kind.NETHER && random.nextFloat() < LOOT_CHANCE) {
			Mob m = nextOf(next, Mob.class);
			if (m != null && (m instanceof Zombie || m instanceof AbstractSkeleton || m instanceof Vindicator)) {
				loot(level, m, tier, random);
			}
		}
	}

	private static @Nullable Mob nextOf(Iterator<Mob> it, Class<?> kind) {
		while (it.hasNext()) {
			Mob m = it.next();
			if (kind.isInstance(m) && !m.entityTags().contains(CAPTAIN_TAG) && !m.entityTags().contains(LOBBER_TAG)) {
				return m;
			}
		}
		return null;
	}

	/** Whether {@code entity} is a grave-captain (a boss, {@link BossBars#boss}). */
	public static boolean captain(Entity entity) {
		return entity.entityTags().contains(CAPTAIN_TAG);
	}

	/** {@code mob} leads the dead: bigger, much tougher, harder hitting, hard to shove, and its own bar. */
	private static void captain(Mob mob, String name) {
		mob.addTag(CAPTAIN_TAG);
		modify(mob, Attributes.SCALE, CAPTAIN_SCALE_ID, CAPTAIN_SCALE);
		modify(mob, Attributes.MAX_HEALTH, CAPTAIN_HEALTH_ID, CAPTAIN_HEALTH);
		modify(mob, Attributes.ATTACK_DAMAGE, CAPTAIN_DAMAGE_ID, CAPTAIN_DAMAGE);
		AttributeInstance knock = mob.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
		if (knock != null) {
			knock.addOrReplacePermanentModifier(new AttributeModifier(CAPTAIN_KNOCKBACK_ID, CAPTAIN_KNOCKBACK, AttributeModifier.Operation.ADD_VALUE));
		}
		mob.setHealth(mob.getMaxHealth());
		mob.setCustomName(Component.translatable(name));
		CAPTAINS.computeIfAbsent(mob, ZombieHordes::bar);
	}

	private static ServerBossEvent bar(Mob mob) {
		ServerBossEvent bar = new ServerBossEvent(mob.getUUID(), mob.getCustomName() != null ? mob.getCustomName() : Component.translatable("entity.jaymoskingdoms.grave_captain"),
			BossEvent.BossBarColor.WHITE, BossEvent.BossBarOverlay.NOTCHED_10);
		return bar;
	}

	/** Each captain's bar: its health, shown to whoever has struck it ({@link BossBars#show}). */
	private static void captains() {
		for (Iterator<Map.Entry<Mob, ServerBossEvent>> it = CAPTAINS.entrySet().iterator(); it.hasNext();) {
			Map.Entry<Mob, ServerBossEvent> e = it.next();
			Mob mob = e.getKey();
			ServerBossEvent bar = e.getValue();
			if (!mob.isAlive() || mob.isRemoved() || !(mob.level() instanceof ServerLevel level)) {
				bar.removeAllPlayers();
				it.remove();
				continue;
			}
			bar.setProgress(Mth.clamp(mob.getHealth() / mob.getMaxHealth(), 0.0F, 1.0F));
			BossBars.show(level, mob, bar);
		}
	}

	/** {@code mob} is one of the drowned pirates' dead: a bandana, a sea coat, and a few powder kegs to throw. */
	private static void lobber(Mob mob, RandomSource random) {
		mob.addTag(LOBBER_TAG);
		if (Bandits.BANDANA != null) {
			mob.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Bandits.BANDANA));
			mob.setDropChance(EquipmentSlot.HEAD, 0.0F);
		}
		ItemStack coat = new ItemStack(Items.LEATHER_CHESTPLATE);
		coat.set(net.minecraft.core.component.DataComponents.DYED_COLOR, new DyedItemColor(PIRATE_COATS[random.nextInt(PIRATE_COATS.length)]));
		mob.setItemSlot(EquipmentSlot.CHEST, coat);
		mob.setDropChance(EquipmentSlot.CHEST, 0.0F);
		// the kegs are real: what's left in its hand when it falls is there to take (no free projectiles)
		mob.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.TNT, LOBBER_KEGS));
		LOBBERS.put(mob, 0L);
	}

	/** Lobbers with a foe in throwing range and in sight light a keg and lob it at them. */
	private static void lobbers() {
		for (Iterator<Map.Entry<Mob, Long>> it = LOBBERS.entrySet().iterator(); it.hasNext();) {
			Map.Entry<Mob, Long> e = it.next();
			Mob mob = e.getKey();
			if (!mob.isAlive() || mob.isRemoved() || !(mob.level() instanceof ServerLevel level)) {
				it.remove();
				continue;
			}
			ItemStack kegs = mob.getMainHandItem();
			LivingEntity foe = mob.getTarget();
			long now = level.getGameTime();
			if (!kegs.is(Items.TNT) || foe == null || !foe.isAlive() || now - e.getValue() < LOB_EVERY || mob.isInWater()) {
				continue;
			}
			double d = mob.distanceTo(foe);
			if (d < LOB_MIN || d > LOB_MAX || !mob.hasLineOfSight(foe)) {
				continue;
			}
			e.setValue(now);
			Vec3 from = mob.getEyePosition();
			PrimedTnt keg = new PrimedTnt(level, from.x, from.y, from.z, mob);
			// a lob: up and over, landing by them about when the fuse runs out
			int flight = Math.max(12, (int) (d * 1.6));
			double g = 0.04;
			Vec3 to = foe.position();
			keg.setDeltaMovement((to.x - from.x) / flight, (to.y - from.y + 0.5 * g * flight * flight) / flight, (to.z - from.z) / flight);
			keg.setFuse(KEG_FUSE + (int) d);
			((com.jaymoskingdoms.mixin.hordes.PrimedTntAccessor) keg).jaymoskingdoms$setExplosionPower(KEG_POWER);
			level.addFreshEntity(keg);
			mob.swing(net.minecraft.world.InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
			level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.TNT_PRIMED, SoundSource.HOSTILE, 1.0F, 1.0F);
			kegs.shrink(1);
			if (kegs.isEmpty()) {
				mob.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
			}
		}
	}

	/** {@code mob} carries something worth having: a better, enchanted (it glints) weapon that it's sure to drop, unworn. */
	private static void loot(ServerLevel level, Mob mob, int tier, RandomSource random) {
		mob.addTag(LOOT_TAG);
		ItemStack prize = mob instanceof AbstractSkeleton ? new ItemStack(Items.BOW) : MonsterGear.weaponFor(level, random, Math.min(4, Math.max(1, tier) + 1));
		if (!prize.isEnchanted()) {
			prize = EnchantmentHelper.enchantItem(random, prize, LOOT_ENCHANT_LEVEL, level.registryAccess(),
				level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).get(EnchantmentTags.ON_MOB_SPAWN_EQUIPMENT));
		}
		mob.setItemSlot(EquipmentSlot.MAINHAND, prize);
		// above 1: always dropped, as it was (never worn out)
		mob.setDropChance(EquipmentSlot.MAINHAND, 2.0F);
	}

	// ---- the march ------------------------------------------------------------------------------------------------------

	/** A zombie that may fall in with a horde of the dead: a plain wild one (not a thrall, a Bio-built's bud, a risen orc, a drowned or a piglin). */
	private static boolean stray(Mob mob) {
		return mob instanceof Zombie && !(mob instanceof ZombifiedPiglin) && !(mob instanceof Drowned) && !(mob instanceof BioBud)
			&& !(mob instanceof RisenOrc) && !mob.entityTags().contains(TAG) && !mob.isPersistenceRequired() && !Thralls.isThrall(mob)
			&& mob.isAlive() && mob.getTarget() == null && !mob.isPassenger();
	}

	private static void march() {
		for (Iterator<Horde> it = HORDES.iterator(); it.hasNext();) {
			Horde horde = it.next();
			horde.members.removeIf(m -> !m.isAlive() || m.isRemoved() || m.level() != horde.level);
			ServerLevel level = horde.level;
			ServerLevel overworld = level.getServer().overworld();
			boolean over = horde.members.isEmpty() || level.getGameTime() - horde.born > (horde.called ? CALL_AGE : MAX_AGE)
				|| !horde.daylit() && !surfaceWeather(overworld);
			if (over) {
				// sunrise, or it's had its night: ordinary monsters again (the Reaper's host is taken back)
				horde.members.forEach(m -> m.removeTag(TAG));
				if (horde.kind == Kind.REAPED) {
					horde.members.forEach(m -> takeBack(level, m));
					ReapedHost.scrap(level, horde.engine);
				}
				it.remove();
				continue;
			}
			Vec3 centre = centre(horde);
			if (horde.called) {
				follow(horde);
				continue;
			}
			ServerPlayer hunted = level.getPlayerByUUID(horde.hunted) instanceof ServerPlayer p && p.isAlive() && !p.isSpectator() && !p.isCreative()
				&& p.level() == level ? p : null;
			// a hit squad, near enough to see them, goes for them
			if (horde.kind == Kind.SQUAD && hunted != null) {
				for (Mob m : horde.members) {
					if (m.getTarget() == null && m.distanceTo(hunted) < SQUAD_SIGHT && m.hasLineOfSight(hunted)) {
						m.setTarget(hunted);
					}
				}
			}
			// one of them is after someone: the rest are called to it
			LivingEntity fight = null;
			for (Mob m : horde.members) {
				if (m.getTarget() != null && m.getTarget().isAlive()) {
					fight = m.getTarget();
					break;
				}
			}
			if (fight != null) {
				UUID who = fight instanceof ServerPlayer p ? p.getUUID() : null;
				for (Mob m : horde.members) {
					if (m.getTarget() == null) {
						steer(m, fight.position(), who, FIGHT_LOUDNESS);
					}
				}
				continue;
			}
			// the Reaper's host goes to its war first; once it stands on the seat it turns on whoever it came up behind
			if (horde.war != null && horde.war.distanceTo(centre) > WAR_HOLD) {
				horde.goal = horde.war;
			} else
			// the one they came for, while they're near enough (a hit squad: anywhere here); lost, it wanders on
			if (hunted != null && (horde.kind == Kind.SQUAD || hunted.position().distanceTo(centre) < TRACK_RANGE)) {
				horde.goal = hunted.position();
			} else if (horde.goal.distanceTo(centre) < 6.0) {
				RandomSource random = level.getRandom();
				double angle = random.nextDouble() * Math.PI * 2.0;
				horde.goal = centre.add(Math.cos(angle) * WANDER, 0.0, Math.sin(angle) * WANDER);
			}
			// the strays it passes fall in
			int max = horde.blood ? BLOOD_MAX_MEMBERS : MAX_MEMBERS;
			if (horde.kind.dead() && horde.members.size() < max) {
				for (Zombie stray : level.getEntitiesOfClass(Zombie.class, new AABB(centre, centre).inflate(GATHER_RANGE), ZombieHordes::stray)) {
					if (horde.members.size() >= max) {
						break;
					}
					stray.addTag(TAG);
					stray.getNavigation().setCanFloat(true);
					horde.members.add(stray);
				}
			}
			if (horde.engine != null && horde.war != null) {
				ReapedHost.drive(level, horde.engine, horde.war);
			}
			for (Mob m : horde.members) {
				if (m.getTarget() == null && !ReapedHost.crews(horde.engine, m)) {
					// each keeps its own place in the crowd
					int h = m.getUUID().hashCode();
					Vec3 spot = horde.goal.add(((h & 7) - 3.5) * 0.8, 0.0, (((h >> 3) & 7) - 3.5) * 0.8);
					steer(m, spot, null, MARCH_LOUDNESS);
				}
			}
		}
	}

	/**
	 * The keeper's call (the Reaper at its Shroud, {@link Reaper}): a small host rises round {@code keeper}, keeps to them
	 * and fights whoever they fight for {@link #CALL_AGE} ticks, then is taken back. Returns how many rose.
	 */
	static int call(ServerPlayer keeper) {
		ServerLevel level = (ServerLevel) keeper.level();
		RandomSource random = level.getRandom();
		int tier = WorldTiers.of(keeper).ordinal();
		Horde horde = new Horde(level, keeper.getUUID(), Kind.REAPED, false, false, danger(keeper), keeper.position());
		horde.called = true;
		for (int tries = 0; horde.members.size() < CALL_SIZE && tries < CALL_SIZE * 4; tries++) {
			BlockPos at = Construction.standable(level, keeper.blockPosition().offset(random.nextInt(13) - 6, 0, random.nextInt(13) - 6), 3);
			if (Math.abs(at.getY() - keeper.getY()) > 6 || !level.getFluidState(at).isEmpty()) {
				continue;
			}
			Mob mob = place(level, horde, at, tier, random);
			if (mob != null) {
				level.sendParticles(Fx.miasma(Reaper.SMOKE, 1.4F), mob.getX(), mob.getY() + 1.0, mob.getZ(), 12, 0.3, 0.6, 0.3, 0.01);
			}
		}
		if (horde.members.isEmpty()) {
			return 0;
		}
		HORDES.add(horde);
		level.playSound(null, keeper.getX(), keeper.getY(), keeper.getZ(), groan(Kind.REAPED), SoundSource.PLAYERS, GROAN_VOLUME, 0.55F);
		return horde.members.size();
	}

	/** One more of {@code horde} stood at {@code at} whatever the light there (a called host, a long ship's deck); null if there's no room. */
	private static @Nullable Mob place(ServerLevel level, Horde horde, BlockPos at, int tier, RandomSource random) {
		Mob mob = member(horde.kind, level, at, horde.members.size(), tier, random).create(level, EntitySpawnReason.EVENT);
		if (mob == null) {
			return null;
		}
		mob.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, random.nextFloat() * 360.0F, 0.0F);
		if (!level.noCollision(mob)) {
			mob.discard();
			return null;
		}
		mob.finalizeSpawn(level, level.getCurrentDifficultyAt(at), EntitySpawnReason.EVENT, null);
		join(mob, horde.danger);
		level.addFreshEntity(mob);
		horde.members.add(mob);
		return mob;
	}

	/** A called host's march: after whoever its keeper last struck or was struck by, else at their heels. Without its keeper it is taken back. */
	private static void follow(Horde horde) {
		ServerLevel level = horde.level;
		if (!(level.getPlayerByUUID(horde.hunted) instanceof ServerPlayer keeper) || !keeper.isAlive()) {
			horde.members.forEach(m -> takeBack(level, m));
			horde.members.clear();
			return;
		}
		LivingEntity fight = keeper.getLastHurtMob();
		if (fight == null || !fight.isAlive() || keeper.tickCount - keeper.getLastHurtMobTimestamp() > CALL_FIGHT_TICKS) {
			fight = keeper.getLastHurtByMob();
		}
		if (fight != null && (!fight.isAlive() || fight instanceof Reaped || fight.distanceToSqr(keeper) > TRACK_RANGE * TRACK_RANGE)) {
			fight = null;
		}
		horde.goal = keeper.position();
		for (Mob m : horde.members) {
			if (fight != null) {
				m.setTarget(fight);
			} else if (m.getTarget() == null) {
				int h = m.getUUID().hashCode();
				steer(m, horde.goal.add(((h & 7) - 3.5) * 0.8, 0.0, (((h >> 3) & 7) - 3.5) * 0.8), null, MARCH_LOUDNESS);
			}
		}
	}

	/** The Reaper takes one of its host back: gone in smoke, leaving nothing. */
	private static void takeBack(ServerLevel level, Mob mob) {
		level.sendParticles(Fx.miasma(Reaper.SMOKE, 1.4F), mob.getX(), mob.getY() + 1.0, mob.getZ(), 12, 0.3, 0.6, 0.3, 0.01);
		level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.SOUL_ESCAPE.value(), SoundSource.HOSTILE, 0.8F, 0.5F);
		ReapedHost.dismiss(mob);
		mob.discard();
	}

	/** {@code newcomer} (an illager the Reaper's host killed, got up again) falls in with the horde {@code member} marches with, if there's room. */
	public static boolean enlist(Mob member, Mob newcomer) {
		for (Horde horde : HORDES) {
			if (horde.members.contains(member)) {
				if (horde.members.size() >= MAX_MEMBERS) {
					return false;
				}
				join(newcomer, horde.danger);
				horde.members.add(newcomer);
				return true;
			}
		}
		return false;
	}

	/** Leads {@code mob} to {@code spot}: through its senses, and the Nether's beasts (who think with a brain) by their walk target. */
	private static void steer(Mob mob, Vec3 spot, @Nullable UUID who, double loudness) {
		Senses.lure(mob, spot, who, loudness);
		if (mob instanceof AbstractPiglin || mob instanceof Hoglin) {
			mob.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(spot, 1.0F, 2));
		}
	}

	private static Vec3 centre(Horde horde) {
		double x = 0.0, y = 0.0, z = 0.0;
		for (Mob m : horde.members) {
			x += m.getX();
			y += m.getY();
			z += m.getZ();
		}
		int n = horde.members.size();
		return new Vec3(x / n, y / n, z / n);
	}

	/** Whether {@code entity} walks in a horde (the lure's scent, {@link HordeLure}). */
	/** Where the horde {@code mob} marches with is headed; null if it's in none. */
	static @Nullable Vec3 goalOf(Mob mob) {
		if (!inHorde(mob)) {
			return null;
		}
		for (Horde horde : HORDES) {
			if (horde.members.contains(mob)) {
				return horde.goal;
			}
		}
		return null;
	}

	public static boolean inHorde(Entity entity) {
		return entity.entityTags().contains(TAG);
	}
}
