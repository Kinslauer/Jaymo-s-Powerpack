package com.jaymoskingdoms;

import com.jaymoskingdoms.props.PropBlock;
import com.jaymoskingdoms.wild.WolfStalkers;
import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.biome.v1.ModificationPhase;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.util.random.Weighted;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.animal.golem.AbstractGolem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.633) Shy deer. Andy (2026-11-06): "the deer are still a nuisance and overspawning, but i like the fact that they
 * are in packs in the overworld, they should just be far more skittish and scare away from your home after the first
 * interaction". Andy (2026-11-19): "deer should avoid civilization completely, darting away from any POSSIBILITY people
 * may be near, much like the real thing ... they only ever come near to eat crops and even then scarecrows will prevent
 * their proximity".
 * <p>Every deer in {@code jaymoskingdoms:shy_deer} ({@link #SHY}: Naturalist's deer, Cube Animals' red deer, roe deer and
 * reindeer; never the blossom shrines' tame little deer, who live by people):
 * <ul>
 * <li><b>Herds of 3 to 10</b> (Naturalist's deer only, {@link #registerHerds}, and {@code mixin/tweaks/DeerHerdMixin}):
 * Andy (2026-11-08): "Packs of deer should vary anywhere from 3 to 10 but no more." Its entry in every biome comes as
 * {@link #HERD_LEAST}-{@link #HERD_MOST} at its own weight, and one natural spawn stops at {@link #HERD_MOST} deer.</li>
 * <li><b>Fewer, still in herds</b> (Naturalist's deer only, {@link #mayGraze}): a herd may come to {@link #HERD_SHARE} of
 * the {@link #CELL}-block patches of ground at a time, so a herd comes whole; none once {@link #CROWD} deer are within
 * {@link #CROWD_RANGE}; never in the Nether.</li>
 * <li><b>Born in the wild</b> ({@link #wild}, from {@code mixin/tweaks/DeerSpawnMixin}): no deer spawns
 * within {@link #SEAT_RANGE} of a settlement's seat, {@link #PLACE_RANGE} of anything people use (a bed, a workstation, a
 * bell: any point of interest but a wild bee nest), in lamplight ({@link #LIT}), by a scarecrow, within
 * {@link #SPAWN_CLEAR} of a player or {@link #PEOPLE_SIGHT} of anyone else, or round a home they've been scared off.</li>
 * <li><b>Bolting from people</b> ({@link #fright}): a player seen within {@link #SIGHT} blocks ({@link #SNEAK_SIGHT} if
 * they sneak), or right on top of it ({@link #NEAR}); a player smelled on the wind (WolfStalkers' nose: {@link #SCENT}
 * blocks, less sneaking or in rain, far more downwind than upwind); a footstep of theirs fresher than {@link #TRAIL_FRESH}
 * within {@link #TRAIL_NEAR} (ScentTrails); and anyone else (villagers, folk, illagers, golems) seen within
 * {@link #PEOPLE_SIGHT} or within {@link #NEAR}. It runs {@link #FLEE_RUN} blocks the other way at {@link #FLEE_SPEED}.</li>
 * <li><b>Bolting from noise</b> ({@link #heard}, from {@link Hearing#noise}): anything a person does that is heard (steps,
 * work, doors, voices, shots, blasts) within its reach sends every deer that hears it running from it.</li>
 * <li><b>Shunning people's places</b> ({@link #settled}): a deer that wanders near a seat, a point of interest, lamplight
 * or a scarecrow turns and runs out again.</li>
 * <li><b>Crops</b> ({@link #raid}): the one thing that brings a deer near: at dusk and through the night, a deer that
 * hasn't eaten its fill looks for crops within {@link #CROP_SMELL} blocks, steals in to them (minding people, noise and
 * scent all the way, but not houses or lamps) and crops {@link #RAID_EAT} plants back to the ground before it goes,
 * full for {@link #RAID_REST} ticks. Crops a scarecrow guards (Jaymo's Kitchen's with its pumpkin, or our straw one
 * within {@link #SCARECROW_RANGE}) never draw it, and a deer by a scarecrow runs from it.</li>
 * <li><b>Scared off your home</b>: a player's home is their bed. The first time a deer bolts from a player within
 * {@link #HOME_RANGE} of their bed, deer learn that home ({@link #SCARED}, kept on the Overworld): none come into the
 * world within {@link #HOME_RANGE} of it again, and any that wander in turn and run out of it.</li>
 * </ul>
 * A deer someone owns, has on a lead, rides or named is left alone, and a Druid who has studied deer and speaks their
 * tongue (Beast Tongue) frightens none.
 */
public final class ShyDeer {
	static final Identifier DEER = Identifier.fromNamespaceAndPath("naturalist", "deer");
	/** Every shy deer. */
	public static final TagKey<EntityType<?>> SHY = TagKey.create(Registries.ENTITY_TYPE, JaymosKingdoms.id("shy_deer"));
	/** A herd that spawns on its own is this many deer at least and at most (any size in between as likely). */
	public static final int HERD_LEAST = 3, HERD_MOST = 10;
	/** The share of patches of ground a herd may come to, how big a patch is (blocks), and how long one's choice holds (ticks). */
	static final float HERD_SHARE = 0.15F;
	static final int CELL = 64;
	static final long CHOICE_HOLDS = 6000L;
	/** No more come once this many deer are this near already. */
	static final int CROWD = 6;
	static final double CROWD_RANGE = 96.0;
	/** How often (ticks) each deer looks about. */
	static final int CHECK_EVERY = 10;

	/** A player is seen this far (sneaking, this far) with a clear line; anyone this near is noticed whatever is between. */
	static final double SIGHT = 40.0, SNEAK_SIGHT = 16.0, NEAR = 10.0;
	/** A player's scent carries this far in still air (before sneaking, rain, wounds and the wind: WolfStalkers' shares). */
	static final double SCENT = 48.0;
	/** A player's footstep this fresh (ticks), this near (blocks), is enough. */
	static final int TRAIL_FRESH = 2400;
	static final double TRAIL_NEAR = 10.0;
	/** Anyone else (villagers, folk, illagers, golems) is seen this far with a clear line. */
	static final double PEOPLE_SIGHT = 32.0;
	/** How far a bolt runs (blocks), how fast, and how soon (ticks) a running deer is sent on again. */
	static final double FLEE_RUN = 24.0, FLEE_SPEED = 2.0;
	static final int RESEND = 30;

	/** People's places: a settlement's seat this near, any point of interest this near, block light this bright. */
	static final double SEAT_RANGE = 64.0;
	static final int PLACE_RANGE = 32, LIT = 6;
	/** No deer comes into the world this near a player. */
	static final double SPAWN_CLEAR = 48.0;

	/** Crops: smelled this far (blocks), looked for every this many ticks (columns this far apart), walked to at this speed. */
	static final int CROP_SMELL = 40, CROP_LOOK = 200, CROP_STEP = 2;
	static final double RAID_SPEED = 1.0, BITE = 1.8;
	/** A raid crops this many plants and ends after this long at most (ticks); then the deer is full this long (ticks). */
	static final int RAID_EAT = 4, RAID_TICKS = 2400, RAID_REST = 12000;
	/** Our straw scarecrow guards crops this near (blocks; Kitchen's has its own reach). */
	static final int SCARECROW_RANGE = 10;
	/** How far round a deer Kitchen's scarecrows are felt for (blocks), to tell which way to run. */
	static final double SCARECROW_FEEL = 6.0;

	/** A home's reach: deer learn it once one bolts from its player this near their bed, and keep this far from it after. */
	static final double HOME_RANGE = 48.0;
	/** At most this many homes are remembered (the oldest forgotten first). */
	static final int HOMES_MOST = 128;

	/** Homes the deer have learned to keep away from (bed positions, as longs); kept on the Overworld. */
	static final AttachmentType<List<Long>> SCARED = AttachmentRegistry.create(JaymosKingdoms.id("deer_scared_homes"),
		builder -> builder.persistent(Codec.LONG.listOf()));

	/** A raid on a field: the crop it's after, how many it has eaten, and when it gives up. */
	private static final class Raid {
		BlockPos crop;
		int eaten;
		final long until;

		Raid(BlockPos crop, long until) {
			this.crop = crop;
			this.until = until;
		}
	}

	/** What frightened a deer: where from, and the player if it was one. */
	private record Fright(Vec3 from, @Nullable ServerPlayer player) {
	}

	/** The deer loaded now, their raids, when each was last sent running, and when each may next look for crops. */
	private static final Set<Mob> LOADED = Collections.newSetFromMap(new WeakHashMap<>());
	private static final Map<Mob, Raid> RAIDS = new WeakHashMap<>();
	private static final Map<Mob, Long> RAN = new WeakHashMap<>();
	private static final Map<Mob, Long> FULL = new WeakHashMap<>();
	/** Where our straw scarecrows stand, by world (each tells us as it looks about). */
	private static final Map<ResourceKey<Level>, Set<BlockPos>> STRAW = new HashMap<>();

	private ShyDeer() {
	}

	static void register() {
		JaymosKingdoms.LOGGER.debug("deer attachment: {}", SCARED);
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof Mob mob && shy(mob.getType())) {
				LOADED.add(mob);
			}
		});
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
			if (entity instanceof Mob mob) {
				LOADED.remove(mob);
				RAIDS.remove(mob);
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			LOADED.clear();
			RAIDS.clear();
			RAN.clear();
			FULL.clear();
			STRAW.clear();
		});
		ServerTickEvents.END_LEVEL_TICK.register(ShyDeer::tick);
		registerHerds();
	}

	/** After every mod has added its spawns, Naturalist's deer come in herds of {@link #HERD_LEAST}-{@link #HERD_MOST}. */
	private static void registerHerds() {
		if (!FabricLoader.getInstance().isModLoaded(DEER.getNamespace())) {
			return;
		}
		BiomeModifications.create(JaymosKingdoms.id("deer_herds")).add(ModificationPhase.POST_PROCESSING, BiomeSelectors.all(), context -> {
			var spawns = context.getMobSpawnSettings();
			for (MobCategory category : MobCategory.values()) {
				List<Weighted<MobSpawnSettings.SpawnerData>> redo = new ArrayList<>();
				for (Weighted<MobSpawnSettings.SpawnerData> entry : spawns.getMobs(category)) {
					if (isDeer(entry.value().type())) {
						redo.add(entry);
					}
				}
				if (redo.isEmpty()) {
					continue;
				}
				spawns.removeSpawns((cat, data) -> cat == category && isDeer(data.type()));
				for (Weighted<MobSpawnSettings.SpawnerData> entry : redo) {
					spawns.addSpawn(category, new MobSpawnSettings.SpawnerData(entry.value().type(), UniformInt.of(HERD_LEAST, HERD_MOST)), entry.weight());
				}
			}
		});
	}

	private static @Nullable EntityType<?> deer() {
		return BuiltInRegistries.ENTITY_TYPE.getOptional(DEER).orElse(null);
	}

	/** Naturalist's deer (the herd and patch rules are its own). */
	public static boolean isDeer(EntityType<?> type) {
		return DEER.equals(BuiltInRegistries.ENTITY_TYPE.getKey(type));
	}

	/** Any shy deer ({@link #SHY}). */
	public static boolean shy(EntityType<?> type) {
		return type.builtInRegistryHolder().is(SHY);
	}

	/** (From {@code DeerSpawnMixin}) Whether Naturalist's deer may spawn on its own at {@code pos}: the herd and patch rules. */
	public static boolean mayGraze(ServerLevelAccessor accessor, EntitySpawnReason reason, BlockPos pos) {
		ServerLevel level = accessor.getLevel();
		if (level.dimension() == Level.NETHER || accessor.getBiome(pos).is(net.minecraft.tags.BiomeTags.IS_NETHER)) {
			return false;
		}
		// (one roll for the whole patch for a while, so a herd comes whole or not at all)
		long cellX = Math.floorDiv(pos.getX(), CELL), cellZ = Math.floorDiv(pos.getZ(), CELL);
		long when = level.getGameTime() / CHOICE_HOLDS;
		long h = Mth.getSeed((int) cellX, (int) when, (int) cellZ) ^ level.getSeed();
		float roll = (float) ((h >>> 11) & 0xFFFFFF) / (float) 0x1000000;
		if (roll >= HERD_SHARE) {
			return false;
		}
		if (reason == EntitySpawnReason.NATURAL && accessor instanceof ServerLevel server) {
			EntityType<?> type = deer();
			// (deer that haven't ticked yet are the herd coming now: spawning runs before entities tick)
			if (type != null && server.getEntities(type, new AABB(pos).inflate(CROWD_RANGE), e -> e.tickCount > 0).size() >= CROWD) {
				return false;
			}
		}
		return true;
	}

	/** (From {@code DeerSpawnMixin}) Whether any shy deer may come into the world at {@code pos}: well away from people. */
	public static boolean wild(ServerLevelAccessor accessor, EntitySpawnReason reason, BlockPos pos) {
		ServerLevel level = accessor.getLevel();
		Vec3 at = Vec3.atCenterOf(pos);
		if (scaredHome(level, at) != null) {
			return false;
		}
		// (a chunk still being made is made off the server thread, with no light yet: only the homes are asked about; a deer
		// born there near people runs off as soon as it looks about)
		if (reason != EntitySpawnReason.NATURAL || !(accessor instanceof ServerLevel)) {
			return true;
		}
		if (settled(level, pos) != null) {
			return false;
		}
		if (level.getNearestPlayer(at.x, at.y, at.z, SPAWN_CLEAR, false) != null) {
			return false;
		}
		return level.getEntitiesOfClass(LivingEntity.class, new AABB(pos).inflate(PEOPLE_SIGHT), ShyDeer::person).isEmpty();
	}

	// ---- each deer's look about ----------------------------------------------------------------------------------------

	private static void tick(ServerLevel level) {
		if (LOADED.isEmpty()) {
			return;
		}
		long now = level.getGameTime();
		for (Mob deer : List.copyOf(LOADED)) {
			if (deer.level() != level || !deer.isAlive() || (deer.tickCount + deer.getId()) % CHECK_EVERY != 0 || kept(deer)) {
				continue;
			}
			look(level, deer, now);
		}
		// (deer that wander into a home they know turn and run out of it)
		if (level.dimension() != Level.OVERWORLD || now % CHECK_EVERY != 0) {
			return;
		}
		for (long packed : level.getAttachedOrElse(SCARED, List.of())) {
			Vec3 home = Vec3.atCenterOf(BlockPos.of(packed));
			if (level.getNearestPlayer(home.x, home.y, home.z, HOME_RANGE + 64.0, false) == null) {
				continue;
			}
			for (Mob deer : level.getEntitiesOfClass(Mob.class, new AABB(BlockPos.of(packed)).inflate(HOME_RANGE),
				e -> e.isAlive() && shy(e.getType()) && !kept(e))) {
				if (deer.position().closerThan(home, HOME_RANGE)) {
					bolt(deer, home, now);
				}
			}
		}
	}

	/** Someone's deer: owned, on a lead, ridden, carrying someone, or named. */
	private static boolean kept(Mob deer) {
		return deer.isLeashed() || deer.isPassenger() || deer.isVehicle() || deer.hasCustomName() || deer.isNoAi()
			|| deer instanceof OwnableEntity owned && owned.getOwnerReference() != null;
	}

	private static void look(ServerLevel level, Mob deer, long now) {
		Fright fright = fright(level, deer);
		if (fright != null) {
			if (RAIDS.remove(deer) != null) {
				FULL.put(deer, now + RAID_REST / 2);
			}
			bolt(deer, fright.from, now);
			ServerPlayer player = fright.player;
			if (player != null && level.dimension() == Level.OVERWORLD) {
				BlockPos home = home(player);
				if (home != null && player.position().closerThan(Vec3.atCenterOf(home), HOME_RANGE)) {
					learn(level, home);
				}
			}
			return;
		}
		Raid raid = RAIDS.get(deer);
		if (raid != null) {
			raid(level, deer, raid, now);
			return;
		}
		Vec3 place = settled(level, deer.blockPosition());
		if (place != null) {
			bolt(deer, place, now);
			return;
		}
		if (level.isDarkOutside() && FULL.getOrDefault(deer, Long.MIN_VALUE) <= now && level.getGameRules().get(GameRules.MOB_GRIEFING)) {
			FULL.put(deer, now + CROP_LOOK);
			BlockPos crop = cropNear(level, deer.blockPosition(), CROP_SMELL);
			if (crop != null) {
				RAIDS.put(deer, new Raid(crop, now + RAID_TICKS));
				deer.getNavigation().moveTo(crop.getX() + 0.5, crop.getY(), crop.getZ() + 0.5, RAID_SPEED);
			}
		}
	}

	// ---- people --------------------------------------------------------------------------------------------------------

	/** Who counts as people besides players: villagers (MCA's too), folk, illagers, witches, piglins, traders, golems. */
	static boolean person(LivingEntity e) {
		return e.isAlive() && (Temperaments.humanoid(e) || e instanceof AbstractGolem);
	}

	/** A Druid who has studied deer and speaks their tongue frightens none. */
	private static boolean friend(Player player) {
		return FaithExtras.studied(player, DEER.getPath()) && ClassTrees.has(player, FaithExtras.TONGUE) > 0;
	}

	/** Whatever tells {@code deer} people are near: seen, smelled, their fresh steps; null: nothing. */
	private static @Nullable Fright fright(ServerLevel level, Mob deer) {
		Vec3 at = deer.position();
		for (ServerPlayer player : level.players()) {
			if (player.isSpectator() || !player.isAlive() || friend(player)) {
				continue;
			}
			double d2 = player.distanceToSqr(deer);
			double sight = player.isShiftKeyDown() ? SNEAK_SIGHT : SIGHT;
			if (d2 <= NEAR * NEAR || d2 <= sight * sight && deer.hasLineOfSight(player)) {
				return new Fright(player.position(), player);
			}
			double scent = SCENT * WolfStalkers.shares(level, player) * WolfStalkers.windShare(level, player.position(), at);
			if (d2 <= scent * scent) {
				return new Fright(player.position(), player);
			}
			if (d2 <= (SCENT * 2.0) * (SCENT * 2.0)) {
				Vec3 step = ScentTrails.freshest(player.getUUID(), level, at, TRAIL_NEAR, TRAIL_FRESH);
				if (step != null) {
					return new Fright(step, null);
				}
			}
		}
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, deer.getBoundingBox().inflate(PEOPLE_SIGHT), ShyDeer::person)) {
			double d2 = e.distanceToSqr(deer);
			if (d2 <= NEAR * NEAR || d2 <= PEOPLE_SIGHT * PEOPLE_SIGHT && deer.hasLineOfSight(e)) {
				return new Fright(e.position(), null);
			}
		}
		return null;
	}

	/** (From {@link Hearing#noise}) A noise at {@code at} carrying {@code reach} blocks: every deer that hears a person's runs. */
	static void heard(ServerLevel level, Vec3 at, double reach, @Nullable UUID who, Hearing.Kind kind) {
		if (LOADED.isEmpty() || who == null && !kind.urgent) {
			return;
		}
		if (who != null && level.getServer().getPlayerList().getPlayer(who) instanceof ServerPlayer player && friend(player)) {
			return;
		}
		long now = level.getGameTime();
		double r2 = reach * reach;
		for (Mob deer : List.copyOf(LOADED)) {
			if (deer.level() == level && deer.isAlive() && deer.distanceToSqr(at) < r2 && !kept(deer)) {
				if (RAIDS.remove(deer) != null) {
					FULL.put(deer, now + RAID_REST / 2);
				}
				bolt(deer, at, now);
			}
		}
	}

	// ---- people's places -----------------------------------------------------------------------------------------------

	/** Where people's place near {@code pos} is (a point to run from), or null: a settlement's seat, a point of interest, lamplight, a scarecrow. */
	static @Nullable Vec3 settled(ServerLevel level, BlockPos pos) {
		if (level.dimension() == Level.OVERWORLD) {
			Faction seat = Factions.near(level.getServer(), pos, SEAT_RANGE);
			if (seat != null) {
				return Vec3.atCenterOf(seat.seat());
			}
		}
		if (Loaded.made(level, pos.getX() - PLACE_RANGE, pos.getZ() - PLACE_RANGE, pos.getX() + PLACE_RANGE, pos.getZ() + PLACE_RANGE)) {
			BlockPos poi = level.getPoiManager().findClosest(type -> !type.is(PoiTypes.BEE_NEST), pos, PLACE_RANGE,
				PoiManager.Occupancy.ANY).orElse(null);
			if (poi != null) {
				return Vec3.atCenterOf(poi);
			}
		}
		if (level.getBrightness(LightLayer.BLOCK, pos) >= LIT) {
			// (run toward the dark: away from the brighter side)
			int gx = light(level, pos.east(4)) - light(level, pos.west(4));
			int gz = light(level, pos.south(4)) - light(level, pos.north(4));
			return Vec3.atCenterOf(pos).add(Math.signum(gx), 0.0, Math.signum(gz));
		}
		Vec3 crow = scarecrow(level, pos);
		if (crow != null) {
			return crow;
		}
		return null;
	}

	private static int light(ServerLevel level, BlockPos pos) {
		return Loaded.made(level, pos) ? level.getBrightness(LightLayer.BLOCK, pos) : 0;
	}

	/** A scarecrow watching over {@code pos} (Kitchen's: felt for round it to tell which way it is), as a point to run from; or null. */
	private static @Nullable Vec3 scarecrow(ServerLevel level, BlockPos pos) {
		BlockPos straw = strawScarecrow(level, pos);
		if (straw != null) {
			return Vec3.atCenterOf(straw);
		}
		if (!KitchenCompat.scarecrowed(level, pos)) {
			return null;
		}
		int f = (int) SCARECROW_FEEL;
		int gx = (KitchenCompat.scarecrowed(level, pos.east(f)) ? 1 : 0) - (KitchenCompat.scarecrowed(level, pos.west(f)) ? 1 : 0);
		int gz = (KitchenCompat.scarecrowed(level, pos.south(f)) ? 1 : 0) - (KitchenCompat.scarecrowed(level, pos.north(f)) ? 1 : 0);
		return Vec3.atCenterOf(pos).add(gx, 0.0, gz);
	}

	/** (From {@link PropBlock}'s look about) Our straw scarecrow stands at {@code pos}. */
	public static void strawStands(ServerLevel level, BlockPos pos) {
		STRAW.computeIfAbsent(level.dimension(), k -> new HashSet<>()).add(pos.immutable());
	}

	/** Our own straw scarecrow within {@link #SCARECROW_RANGE} of {@code pos}, or null. */
	private static @Nullable BlockPos strawScarecrow(ServerLevel level, BlockPos pos) {
		Set<BlockPos> straw = STRAW.get(level.dimension());
		if (straw == null) {
			return null;
		}
		BlockPos found = null;
		for (var it = straw.iterator(); it.hasNext(); ) {
			BlockPos p = it.next();
			if (!Loaded.made(level, p)) {
				continue;
			}
			if (!(level.getBlockState(p).getBlock() instanceof PropBlock prop && prop.kind == PropBlock.Kind.SCARECROW)) {
				it.remove();
			} else if (found == null && p.distSqr(pos) <= SCARECROW_RANGE * SCARECROW_RANGE) {
				found = p;
			}
		}
		return found;
	}

	/** Whether a scarecrow (Kitchen's or ours) keeps deer off {@code crop}. */
	private static boolean guarded(ServerLevel level, BlockPos crop) {
		return KitchenCompat.scarecrowed(level, crop) || strawScarecrow(level, crop) != null;
	}

	// ---- crops ---------------------------------------------------------------------------------------------------------

	/** The nearest unguarded crop with something on it within {@code range} of {@code from}, on the surface; or null. */
	private static @Nullable BlockPos cropNear(ServerLevel level, BlockPos from, int range) {
		if (!Loaded.made(level, from.getX() - range, from.getZ() - range, from.getX() + range, from.getZ() + range)) {
			return null;
		}
		List<BlockPos> crops = new ArrayList<>();
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		int step = range <= 3 ? 1 : CROP_STEP;
		for (int dx = -range; dx <= range; dx += step) {
			for (int dz = -range; dz <= range; dz += step) {
				if (dx * dx + dz * dz > range * range) {
					continue;
				}
				int x = from.getX() + dx, z = from.getZ() + dz;
				int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
				if (Math.abs(y - from.getY()) > 8) {
					continue;
				}
				p.set(x, y, z);
				if (edible(level.getBlockState(p))) {
					crops.add(p.immutable());
				}
			}
		}
		crops.sort((a, b) -> Double.compare(a.distSqr(from), b.distSqr(from)));
		for (BlockPos crop : crops) {
			// (a field a scarecrow guards never draws it)
			if (!guarded(level, crop)) {
				return crop;
			}
		}
		return null;
	}

	private static boolean edible(BlockState state) {
		return state.getBlock() instanceof CropBlock crop && crop.getAge(state) > 0;
	}

	/** {@code deer} on its raid: walk in, crop a plant back to the ground, the next, and off when full or at daybreak. */
	private static void raid(ServerLevel level, Mob deer, Raid raid, long now) {
		if (!level.isDarkOutside() || now > raid.until || guarded(level, raid.crop)) {
			RAIDS.remove(deer);
			FULL.put(deer, now + RAID_REST / 2);
			if (guarded(level, raid.crop)) {
				bolt(deer, Vec3.atCenterOf(raid.crop), now);
			}
			return;
		}
		BlockState state = level.getBlockState(raid.crop);
		if (!edible(state)) {
			BlockPos next = cropNear(level, raid.crop, 3);
			if (next == null) {
				RAIDS.remove(deer);
				return;
			}
			raid.crop = next;
			state = level.getBlockState(next);
		}
		if (deer.position().distanceToSqr(Vec3.atBottomCenterOf(raid.crop)) > BITE * BITE) {
			deer.getNavigation().moveTo(raid.crop.getX() + 0.5, raid.crop.getY(), raid.crop.getZ() + 0.5, RAID_SPEED);
			return;
		}
		CropBlock crop = (CropBlock) state.getBlock();
		deer.getNavigation().stop();
		deer.getLookControl().setLookAt(Vec3.atCenterOf(raid.crop));
		level.levelEvent(LevelEvent.PARTICLES_DESTROY_BLOCK, raid.crop, Block.getId(state));
		level.setBlock(raid.crop, crop.getStateForAge(0), Block.UPDATE_ALL);
		level.playSound(null, deer.getX(), deer.getY(), deer.getZ(), SoundEvents.GENERIC_EAT.value(), SoundSource.NEUTRAL, 0.6F, 0.9F);
		deer.heal(2.0F);
		if (++raid.eaten >= RAID_EAT) {
			RAIDS.remove(deer);
			FULL.put(deer, now + RAID_REST);
		}
	}

	// ---- running -------------------------------------------------------------------------------------------------------

	/** {@code deer} runs off away from {@code from} (sent on again only every {@link #RESEND} ticks while it runs). */
	private static void bolt(Mob deer, Vec3 from, long now) {
		Long last = RAN.get(deer);
		if (last != null && now - last < RESEND && deer.getNavigation().isInProgress()) {
			return;
		}
		RAN.put(deer, now);
		Vec3 away = deer.position().subtract(from).multiply(1.0, 0.0, 1.0);
		if (away.lengthSqr() < 1.0E-4) {
			away = new Vec3(deer.getRandom().nextDouble() - 0.5, 0.0, deer.getRandom().nextDouble() - 0.5);
		}
		Vec3 to = deer.position().add(away.normalize().scale(FLEE_RUN));
		if (last == null || now - last > RESEND * 4L) {
			deer.level().playSound(null, deer.getX(), deer.getY(), deer.getZ(), SoundEvents.GOAT_STEP, SoundSource.NEUTRAL, 0.8F, 1.3F);
		}
		deer.getNavigation().moveTo(to.x, to.y, to.z, FLEE_SPEED);
	}

	// ---- homes ---------------------------------------------------------------------------------------------------------

	/** {@code player}'s home: their bed in the Overworld (null: none). */
	private static @Nullable BlockPos home(ServerPlayer player) {
		ServerPlayer.RespawnConfig bed = player.getRespawnConfig();
		if (bed == null || bed.forced() || bed.respawnData().dimension() != Level.OVERWORLD) {
			return null;
		}
		BlockPos at = bed.respawnData().pos();
		// (a bed far from its sleeper is often in land not loaded: reading it waited on the server thread for it, behind the
		// world generation queue, Deck crash 2026-11-13 16:45; not loaded, it's simply not known this time)
		if (!Loaded.made(player.level(), at)) {
			return null;
		}
		return player.level().getBlockState(at).getBlock() instanceof BedBlock ? at : null;
	}

	/** The deer know {@code home} now. */
	private static void learn(ServerLevel level, BlockPos home) {
		ServerLevel overworld = level.getServer().overworld();
		if (scaredHome(overworld, Vec3.atCenterOf(home)) != null) {
			return;
		}
		List<Long> all = new ArrayList<>(overworld.getAttachedOrElse(SCARED, List.of()));
		all.add(home.asLong());
		while (all.size() > HOMES_MOST) {
			all.remove(0);
		}
		overworld.setAttached(SCARED, List.copyOf(all));
	}

	/** A home the deer know, within {@link #HOME_RANGE} of {@code at} (Overworld only; null: none). */
	private static @Nullable BlockPos scaredHome(ServerLevel level, Vec3 at) {
		if (level.dimension() != Level.OVERWORLD) {
			return null;
		}
		for (long packed : level.getAttachedOrElse(SCARED, List.of())) {
			BlockPos home = BlockPos.of(packed);
			if (at.closerThan(Vec3.atCenterOf(home), HOME_RANGE)) {
				return home;
			}
		}
		return null;
	}
}
