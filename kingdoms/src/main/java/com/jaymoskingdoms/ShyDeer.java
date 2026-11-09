package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.biome.v1.ModificationPhase;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.random.Weighted;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.633) Shy deer. Andy (2026-11-06): "the deer are still a nuisance and overspawning, but i like the fact that they
 * are in packs in the overworld, they should just be far more skittish and scare away from your home after the first
 * interaction".
 *
 * <ul>
 * <li><b>Herds of 3 to 10</b> ({@link #registerHerds}, and {@code mixin/tweaks/DeerHerdMixin}): Andy (2026-11-08): "Packs
 * of deer should vary anywhere from 3 to 10 but no more." Naturalist's deer entry in every biome comes as
 * {@link #HERD_LEAST}-{@link #HERD_MOST} (any size in between as likely) at its own weight, and one natural spawn stops at
 * {@link #HERD_MOST} deer (vanilla's cap is 4).</li>
 * <li><b>Fewer, still in herds</b> ({@link #mayGraze}, from {@code mixin/tweaks/DeerSpawnMixin}): whether a herd may
 * come is decided once for each {@link #CELL}-block patch of ground at a time ({@link #HERD_SHARE} of them), so a herd
 * that comes comes whole; none once {@link #CROWD} deer are already within {@link #CROWD_RANGE} (not counting the herd
 * arriving right now, so it isn't cut short); and never in the Nether.</li>
 * <li><b>Skittish</b>: a deer bolts from any player within {@link #FLEE_RANGE} ({@link #SNEAK_RANGE} if they're sneaking),
 * running {@link #FLEE_RUN} blocks away.</li>
 * <li><b>Scared off your home</b>: a player's home is their bed. The first time a deer bolts from a player within
 * {@link #HOME_RANGE} of their bed, deer learn that home ({@link #SCARED}, kept on the Overworld): none spawn within
 * {@link #HOME_RANGE} of it again, and any that wander in turn and run out of it.</li>
 * </ul>
 */
public final class ShyDeer {
	static final Identifier DEER = Identifier.fromNamespaceAndPath("naturalist", "deer");
	/** A herd that spawns on its own is this many deer at least and at most (any size in between as likely). */
	public static final int HERD_LEAST = 3, HERD_MOST = 10;
	/** The share of patches of ground a herd may come to, how big a patch is (blocks), and how long one's choice holds (ticks). */
	static final float HERD_SHARE = 0.15F;
	static final int CELL = 64;
	static final long CHOICE_HOLDS = 6000L;
	/** No more come once this many deer are this near already. */
	static final int CROWD = 6;
	static final double CROWD_RANGE = 96.0;
	/** A deer bolts from a player this near (this near if they sneak), this far, this fast; checked this often (ticks). */
	static final double FLEE_RANGE = 24.0, SNEAK_RANGE = 10.0, FLEE_RUN = 20.0, FLEE_SPEED = 2.0;
	static final int CHECK_EVERY = 5;
	/** A home's reach: deer learn it once one bolts from its player this near their bed, and keep this far from it after. */
	static final double HOME_RANGE = 48.0;
	/** At most this many homes are remembered (the oldest forgotten first). */
	static final int HOMES_MOST = 128;

	/** Homes the deer have learned to keep away from (bed positions, as longs); kept on the Overworld. */
	static final AttachmentType<List<Long>> SCARED = AttachmentRegistry.create(JaymosKingdoms.id("deer_scared_homes"),
		builder -> builder.persistent(Codec.LONG.listOf()));

	private ShyDeer() {
	}

	static void register() {
		JaymosKingdoms.LOGGER.debug("deer attachment: {}", SCARED);
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

	public static boolean isDeer(EntityType<?> type) {
		return DEER.equals(BuiltInRegistries.ENTITY_TYPE.getKey(type));
	}

	/** (From {@code DeerSpawnMixin}) Whether a deer may spawn on its own at {@code pos}. */
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
		if (scaredHome(level, Vec3.atCenterOf(pos)) != null) {
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

	private static void tick(ServerLevel level) {
		if ((level.getGameTime() % CHECK_EVERY) != 0 || level.players().isEmpty()) {
			return;
		}
		EntityType<?> type = deer();
		if (type == null) {
			return;
		}
		for (ServerPlayer player : level.players()) {
			if (player.isSpectator()) {
				continue;
			}
			double range = player.isShiftKeyDown() ? SNEAK_RANGE : FLEE_RANGE;
			BlockPos home = level.dimension() == Level.OVERWORLD ? home(player) : null;
			boolean atHome = home != null && player.position().closerThan(Vec3.atCenterOf(home), HOME_RANGE);
			for (Entity e : level.getEntities(type, player.getBoundingBox().inflate(range), e -> e.isAlive())) {
				if (e instanceof Mob deer && deer.distanceToSqr(player) <= range * range) {
					bolt(deer, player.position());
					if (atHome) {
						learn(level, home);
					}
				}
			}
		}
		// (deer that wander into a home they know turn and run out of it)
		if (level.dimension() != Level.OVERWORLD) {
			return;
		}
		for (long packed : level.getServer().overworld().getAttachedOrElse(SCARED, List.of())) {
			Vec3 home = Vec3.atCenterOf(BlockPos.of(packed));
			if (level.getNearestPlayer(home.x, home.y, home.z, HOME_RANGE + 64.0, false) == null) {
				continue;
			}
			for (Entity e : level.getEntities(type, new AABB(BlockPos.of(packed)).inflate(HOME_RANGE), e -> e.isAlive())) {
				if (e instanceof Mob deer && deer.position().closerThan(home, HOME_RANGE)) {
					bolt(deer, home);
				}
			}
		}
	}

	/** {@code deer} runs off away from {@code from}. */
	private static void bolt(Mob deer, Vec3 from) {
		Vec3 away = deer.position().subtract(from).multiply(1.0, 0.0, 1.0);
		if (away.lengthSqr() < 1.0E-4) {
			away = new Vec3(deer.getRandom().nextDouble() - 0.5, 0.0, deer.getRandom().nextDouble() - 0.5);
		}
		Vec3 to = deer.position().add(away.normalize().scale(FLEE_RUN));
		if (!deer.getNavigation().isInProgress() && deer.getRandom().nextInt(4) == 0) {
			deer.level().playSound(null, deer.getX(), deer.getY(), deer.getZ(), SoundEvents.GOAT_STEP, SoundSource.NEUTRAL, 0.8F, 1.3F);
		}
		deer.getNavigation().moveTo(to.x, to.y, to.z, FLEE_SPEED);
	}

	/** {@code player}'s home: their bed in the Overworld (null: none). */
	private static @Nullable BlockPos home(ServerPlayer player) {
		ServerPlayer.RespawnConfig bed = player.getRespawnConfig();
		if (bed == null || bed.forced() || bed.respawnData().dimension() != Level.OVERWORLD) {
			return null;
		}
		BlockPos at = bed.respawnData().pos();
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
