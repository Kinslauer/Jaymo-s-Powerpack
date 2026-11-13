package com.jaymoskingdoms.blossomland;

import com.jaymoskingdoms.BlossomVillages;
import com.jaymoskingdoms.Faction;
import com.jaymoskingdoms.Factions;
import com.jaymoskingdoms.KitchenCompat;
import com.jaymoskingdoms.Moons;
import com.jaymoskingdoms.sailing.SeaEvents;
import com.jaymoskingdoms.seas.Mohknee;
import java.util.List;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.MoonPhase;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The night things of the blossom lands: things the blossom folk tell of and nobody explains. Each has our own name
 * and its place in the lore; nothing on screen says what it is or what to do about it. What the folk say
 * ({@link #gossip}) is the only hint.
 * <ul>
 * <li>The {@link RiverChild} in the rivers by their villages.</li>
 * <li>The {@link SnowWidow}, who walks snowed-over cherry land on winter nights ({@link #widow}).</li>
 * <li>The {@link Unburied}, which gets up where many dead have been left lying ({@link #unburied}).</li>
 * <li>The {@link BlackSwell}, which rises in a fog bank on the sea round Mohknee ({@link #swell}).</li>
 * <li>The dark of the moon: under the open sky the night's monsters are fewer the fuller the moon is and none at
 * the full; at the new moon and the thin crescents more come ({@link #moonKeepsAway}, {@link #darkMoonBrings}).
 * The pack's own moons (blood, harvest and the rest) keep their own rules.</li>
 * </ul>
 */
public final class NightThings {
	public static EntityType<RiverChild> RIVER_CHILD;
	public static EntityType<SnowWidow> SNOW_WIDOW;
	public static EntityType<Unburied> UNBURIED;
	public static EntityType<BlackSwell> BLACK_SWELL;

	/** The moon, by phase from the full round to the full again: the share of open-sky monsters that stay away, and the chance one brings another. */
	static final float[] KEPT_AWAY = {1.0F, 0.5F, 0.2F, 0.0F, 0.0F, 0.0F, 0.2F, 0.5F};
	static final float[] BRINGS = {0.0F, 0.0F, 0.0F, 0.2F, 0.5F, 0.2F, 0.0F, 0.0F};

	static final int RIVER_CHILD_WEIGHT = 6;
	/** How often the night is looked over (ticks). */
	static final int LOOK_EVERY = 600;
	/** The widow: the chance each look for a player on snowed cherry land on a winter night, how far off she appears, and no other this near. */
	static final float WIDOW_CHANCE = 0.06F;
	static final int WIDOW_NEAR = 20, WIDOW_FAR = 32;
	static final double ONE_WITHIN = 128.0;
	/** The Unburied: this many dead left lying within this far of each other. */
	static final int DEAD_NEEDED = 8;
	static final double DEAD_RANGE = 16.0;
	/** The swell: the chance each look for a sailor in fog at night on Mohknee's sea, the fog it needs, and how far ahead it rises. */
	static final float SWELL_CHANCE = 0.2F, SWELL_FOG = 0.5F;
	static final double SWELL_AHEAD = 30.0, SWELL_ONE_WITHIN = 200.0;
	/** What the blossom folk say of the night: how often, and how many lines. */
	static final float GOSSIP = 0.1F;
	static final int GOSSIP_LINES = 12;

	private NightThings() {
	}

	static void register() {
		RIVER_CHILD = BlossomAnimals.type("river_child", EntityType.Builder.of(RiverChild::new, MobCategory.MONSTER).sized(0.6F, 1.1F).clientTrackingRange(8));
		SNOW_WIDOW = BlossomAnimals.type("snow_widow", EntityType.Builder.of(SnowWidow::new, MobCategory.MONSTER).sized(0.6F, 1.95F).clientTrackingRange(10));
		UNBURIED = BlossomAnimals.type("unburied", EntityType.Builder.of(Unburied::new, MobCategory.MONSTER).sized(1.6F, 5.2F).clientTrackingRange(12));
		BLACK_SWELL = BlossomAnimals.type("black_swell", EntityType.Builder.of(BlackSwell::new, MobCategory.MISC).sized(4.0F, 6.0F).clientTrackingRange(12));
		FabricDefaultAttributeRegistry.register(RIVER_CHILD, RiverChild.createAttributes());
		FabricDefaultAttributeRegistry.register(SNOW_WIDOW, SnowWidow.createAttributes());
		FabricDefaultAttributeRegistry.register(UNBURIED, Unburied.createAttributes());
		FabricDefaultAttributeRegistry.register(BLACK_SWELL, BlackSwell.createAttributes());
		SpawnPlacements.register(RIVER_CHILD, SpawnPlacementTypes.IN_WATER, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, RiverChild::checkSpawn);
		BiomeModifications.addSpawn(BiomeSelectors.includeByKey(Biomes.RIVER, Biomes.CHERRY_GROVE), MobCategory.MONSTER, RIVER_CHILD, RIVER_CHILD_WEIGHT, 1, 1);
		BlossomAnimals.egg("river_child", RIVER_CHILD);
		BlossomAnimals.egg("snow_widow", SNOW_WIDOW);
		BlossomAnimals.egg("unburied", UNBURIED);
		BlossomAnimals.egg("black_swell", BLACK_SWELL);
		ServerTickEvents.END_SERVER_TICK.register(NightThings::tick);
	}

	// ---- the moon ------------------------------------------------------------------------------------------------------

	private static int phase(ServerLevel level) {
		return level.environmentAttributes().getValue(EnvironmentAttributes.MOON_PHASE, BlockPos.ZERO).index() % MoonPhase.COUNT;
	}

	/** A monster come out under the open sky on an ordinary night (not one of the pack's moons). */
	private static boolean underTheMoon(ServerLevel level, Entity entity) {
		return level.dimension() == Level.OVERWORLD && entity.getType().getCategory() == MobCategory.MONSTER && level.isDarkOutside()
			&& level.canSeeSky(entity.blockPosition()) && Moons.now(level.getServer()) == Moons.Moon.NONE;
	}

	/** Whether the moon's light keeps this natural spawn from happening. */
	public static boolean moonKeepsAway(ServerLevel level, Entity entity) {
		return underTheMoon(level, entity) && level.getRandom().nextFloat() < KEPT_AWAY[phase(level)];
	}

	/** In the dark of the moon a monster that has just come out may bring another of its kind with it. */
	public static void darkMoonBrings(ServerLevel level, Entity entity) {
		if (!underTheMoon(level, entity) || !entity.isAlive() || level.getRandom().nextFloat() >= BRINGS[phase(level)]) {
			return;
		}
		Entity other = entity.getType().create(level, EntitySpawnReason.NATURAL);
		if (other instanceof Mob mob) {
			mob.snapTo(entity.getX() + level.getRandom().nextInt(5) - 2, entity.getY(), entity.getZ() + level.getRandom().nextInt(5) - 2, entity.getYRot(), 0.0F);
			if (level.noCollision(mob) && !level.containsAnyLiquid(mob.getBoundingBox())) {
				mob.finalizeSpawn(level, level.getCurrentDifficultyAt(mob.blockPosition()), EntitySpawnReason.NATURAL, null);
				level.addFreshEntity(mob);
			}
		}
	}

	// ---- what rises ----------------------------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		ServerLevel level = server.overworld();
		if (level.getGameTime() % LOOK_EVERY != 0 || !level.isDarkOutside()) {
			return;
		}
		RandomSource random = level.getRandom();
		for (ServerPlayer player : level.players()) {
			if (player.isSpectator()) {
				continue;
			}
			widow(level, player, random);
			unburied(level, player);
			swell(level, player, random);
		}
	}

	private static boolean blossomLand(ServerLevel level, BlockPos pos) {
		return level.getBiome(pos).is(Biomes.CHERRY_GROVE) || Mohknee.on(pos.getX(), pos.getZ());
	}

	/** She comes to a player out on snowed-over blossom land on a winter night. */
	private static void widow(ServerLevel level, ServerPlayer player, RandomSource random) {
		BlockPos at = player.blockPosition();
		if (random.nextFloat() >= WIDOW_CHANCE || !"winter".equals(KitchenCompat.season(level)) || !blossomLand(level, at) || !level.canSeeSky(at)
			|| !level.getEntitiesOfClass(SnowWidow.class, player.getBoundingBox().inflate(ONE_WITHIN), LivingEntity::isAlive).isEmpty()) {
			return;
		}
		double angle = random.nextDouble() * Math.PI * 2.0;
		int r = WIDOW_NEAR + random.nextInt(WIDOW_FAR - WIDOW_NEAR);
		int x = at.getX() + (int)Math.round(Math.cos(angle) * r), z = at.getZ() + (int)Math.round(Math.sin(angle) * r);
		BlockPos ground = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
		// only where the snow lies
		if (!level.isLoaded(ground) || !(level.getBlockState(ground).is(Blocks.SNOW) || level.getBlockState(ground.below()).is(Blocks.SNOW_BLOCK)
			|| level.getBlockState(ground.below()).is(Blocks.POWDER_SNOW))) {
			return;
		}
		SnowWidow widow = SNOW_WIDOW.create(level, EntitySpawnReason.EVENT);
		if (widow != null) {
			widow.snapTo(ground.getX() + 0.5, ground.getY(), ground.getZ() + 0.5, random.nextFloat() * 360.0F, 0.0F);
			level.addFreshEntity(widow);
			level.sendParticles(ParticleTypes.SNOWFLAKE, widow.getX(), widow.getY() + 1.0, widow.getZ(), 40, 0.5, 1.0, 0.5, 0.02);
		}
	}

	/** Where enough dead have been left lying together, they get up as one. */
	private static void unburied(ServerLevel level, ServerPlayer player) {
		List<Entity> near = KitchenCompat.fallenNear(level, player.position(), DEAD_RANGE * 2.0);
		if (near.size() < DEAD_NEEDED || !level.getEntitiesOfClass(Unburied.class, player.getBoundingBox().inflate(ONE_WITHIN), LivingEntity::isAlive).isEmpty()) {
			return;
		}
		for (Entity heart : near) {
			List<Entity> heap = near.stream().filter(e -> e.distanceToSqr(heart) <= DEAD_RANGE * DEAD_RANGE).toList();
			if (heap.size() < DEAD_NEEDED) {
				continue;
			}
			Unburied giant = UNBURIED.create(level, EntitySpawnReason.EVENT);
			if (giant == null) {
				return;
			}
			giant.snapTo(heart.getX(), heart.getY(), heart.getZ(), level.getRandom().nextFloat() * 360.0F, 0.0F);
			if (!level.noCollision(giant)) {
				giant.discard();
				continue;
			}
			for (Entity dead : heap) {
				level.sendParticles(ParticleTypes.SOUL, dead.getX(), dead.getY() + 0.3, dead.getZ(), 6, 0.3, 0.2, 0.3, 0.02);
				dead.discard();
			}
			level.addFreshEntity(giant);
			level.playSound(null, giant.blockPosition(), SoundEvents.SKELETON_HORSE_DEATH, SoundSource.HOSTILE, 3.0F, 0.4F);
			return;
		}
	}

	/** It rises ahead of a sailor in a fog bank at night, on the sea round Mohknee. */
	private static void swell(ServerLevel level, ServerPlayer player, RandomSource random) {
		Vec3 at = player.position();
		if (random.nextFloat() >= SWELL_CHANCE || !SeaEvents.atSea(level, at) || SeaEvents.fog(level, at.x, at.z) < SWELL_FOG
			|| !(Mohknee.crossing((int)at.x, (int)at.z) || Mohknee.distance(at.x, at.z) < Mohknee.edge() + Mohknee.SEA / 4.0)
			|| !level.getEntitiesOfClass(BlackSwell.class, player.getBoundingBox().inflate(SWELL_ONE_WITHIN), LivingEntity::isAlive).isEmpty()) {
			return;
		}
		Vec3 ahead = Vec3.directionFromRotation(0.0F, player.getYRot()).scale(SWELL_AHEAD);
		BlockPos rise = BlockPos.containing(at.x + ahead.x, level.getSeaLevel() - 1, at.z + ahead.z);
		if (!level.isLoaded(rise) || !level.getFluidState(rise).is(net.minecraft.tags.FluidTags.WATER)) {
			return;
		}
		BlackSwell swell = BLACK_SWELL.create(level, EntitySpawnReason.EVENT);
		if (swell != null) {
			swell.snapTo(rise.getX() + 0.5, level.getSeaLevel() - BlackSwell.DEPTH, rise.getZ() + 0.5, player.getYRot() + 180.0F, 0.0F);
			level.addFreshEntity(swell);
		}
	}

	// ---- talk ----------------------------------------------------------------------------------------------------------

	/** Now and then one of the blossom folk says something of the night; never what it is. */
	public static @Nullable Component gossip(ServerLevel level, LivingEntity speaker) {
		if (level.getRandom().nextFloat() >= GOSSIP) {
			return null;
		}
		Faction here = Factions.at(level.getServer(), level, speaker.blockPosition());
		if (here == null || !BlossomVillages.blossom(level, here)) {
			return null;
		}
		return Component.translatable("talk.jaymoskingdoms.gossip.blossom_night." + level.getRandom().nextInt(GOSSIP_LINES));
	}
}
