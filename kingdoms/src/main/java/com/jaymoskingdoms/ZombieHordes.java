package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.monster.zombie.Drowned;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.monster.zombie.ZombifiedPiglin;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Zombie hordes (Andy, 2026-11-14: "zombies should be more abundant and travel in hordes, with the largest numbers in the
 * future and during blood moons"). Ideas from Harder Hordes (vm_madness, MIT) and Undead Nights (MCModsPete, ARR): only
 * the ideas, our own code.
 * <p>
 * The old dead of the Overworld (Lore.md, "The dead") walk together now. Every {@link #ROLL_EVERY} ticks each player in
 * the Overworld may draw a horde: a pack of zombies (husks in the desert) that comes up out of sight, behind them
 * {@link #SPAWN_MIN}-{@link #SPAWN_MAX} blocks away, groans once (a sound, never a line of text), and shambles their
 * way. How likely and how big follows a hidden danger ({@link #danger}): how old the world is (days since the
 * {@link #GRACE_DAYS} quiet ones, full after {@link #RAMP_DAYS} more), the player's world tier ({@link WorldTiers}, the
 * SevTech ladder), the armour they wear, and the game's own local difficulty. Hordes come at night, and by day only
 * under rain (the Sun would only send them running for shade, {@code SunShelterZombieMixin}). A blood moon makes
 * them come often and twice as big (a super blood moon, three times), and the ones of zombie kind claw up out of the
 * ground like the moon's own ({@link Moons}).
 * <p>
 * A horde keeps together: every {@link #MARCH_EVERY} ticks its idle members are led ({@link Senses#lure}, quietly, so a
 * louder noise still draws them off) to where the player they came for is, each a step off its neighbours; once the
 * player is beyond {@link #TRACK_RANGE} it wanders on instead. Idle zombies it passes fall in with it, up to
 * {@link #MAX_MEMBERS}. When one of them goes for someone, the rest are called to the fight. Only part of each horde is
 * armed (more as the danger climbs): with a dead villager's old tools at Hearth, and weapons of the world tier's grades
 * above it ({@link MonsterGear}); their tier's strength and armour still come from {@link WorldTiers}. Lit ground keeps
 * them from coming up there. At sunrise, or after {@link #MAX_AGE}, a horde breaks up into ordinary zombies.
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
	/** Under a blood moon, the chance per roll (super blood moon: the second). */
	static final float BLOOD_CHANCE = 0.2F, SUPER_BLOOD_CHANCE = 0.3F;
	/** After a horde, how long until the same player draws another (ticks); under a blood moon. */
	static final int COOLDOWN = 2400, BLOOD_COOLDOWN = 900;
	/** A horde's size at no danger and at full danger (a few either way); times this under a blood or super blood moon. */
	static final int SIZE_MIN = 3, SIZE_MAX = 12;
	static final int BLOOD_SIZE = 2, SUPER_SIZE = 3;
	/** The most zombies near a player (within {@link #COUNT_RADIUS}) a new horde may bring it to: at no danger, the extra at full. */
	static final int NEAR_CAP = 12, NEAR_CAP_DANGER = 20;
	static final float BLOOD_CAP = 1.5F, SUPER_CAP = 2.0F;
	static final double COUNT_RADIUS = 64.0;
	/** Where it comes up: this far, behind the player (within this angle either side of straight behind), spread this wide. */
	static final int SPAWN_MIN = 40, SPAWN_MAX = 56, SPREAD = 4;
	static final double BEHIND = Math.PI / 3.0;
	/** Its members stand no higher or lower than this from the player. */
	static final int SPAWN_HEIGHT = 24;
	/** The share of a horde that's armed: at no danger, the extra at full. */
	static final float ARMED = 0.15F, ARMED_DANGER = 0.35F;
	/** How far a horde follows the player it came for; how far it wanders on once they're lost. */
	static final double TRACK_RANGE = 96.0, WANDER = 24.0;
	/** How loud the march is to its members' senses (a sprint is 12, a held torch 6) and the call to a fight. */
	static final double MARCH_LOUDNESS = 2.0, FIGHT_LOUDNESS = 9.0;
	/** Idle zombies this near a horde fall in with it, up to this many members (under a blood moon, the second). */
	static final double GATHER_RANGE = 12.0;
	static final int MAX_MEMBERS = 24, BLOOD_MAX_MEMBERS = 40;
	/** A horde breaks up after this long (ticks), if the dawn hasn't come first. */
	static final int MAX_AGE = 12000;
	/** How far its groan carries (volume: 16 blocks per). */
	static final float GROAN_VOLUME = 4.0F;

	/** Every member carries this tag (a member left over after a restart is just a zombie). */
	static final String TAG = "jaymoskingdoms.horde";

	/** A dead villager's old tools: what the armed few carry at Hearth. */
	private static final ItemStack[] OLD_TOOLS = {new ItemStack(Items.STONE_HOE), new ItemStack(Items.STONE_SHOVEL), new ItemStack(Items.WOODEN_AXE),
		new ItemStack(Items.STONE_AXE), new ItemStack(Items.WOODEN_SHOVEL)};

	private static final class Horde {
		final ServerLevel level;
		final UUID hunted;
		final boolean blood;
		final List<Mob> members = new ArrayList<>();
		final long born;
		Vec3 goal;

		Horde(ServerLevel level, UUID hunted, boolean blood, Vec3 goal) {
			this.level = level;
			this.hunted = hunted;
			this.blood = blood;
			this.goal = goal;
			this.born = level.getGameTime();
		}
	}

	private static final List<Horde> HORDES = new ArrayList<>();
	/** Player → the game time they may next draw a horde. */
	private static final Map<UUID, Long> NEXT = new HashMap<>();

	private ZombieHordes() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(ZombieHordes::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			HORDES.clear();
			NEXT.clear();
		});
		CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) -> dispatcher.register(Commands.literal("kingdom")
			.then(Commands.literal("horde").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)).executes(c -> {
				ServerPlayer player = c.getSource().getPlayerOrException();
				return player.level() == c.getSource().getServer().overworld() && send(player, Moons.now(c.getSource().getServer())) > 0 ? 1 : 0;
			}))));
	}

	// ---- the danger -----------------------------------------------------------------------------------------------------

	/** The world's day (counted from its first). */
	static long day(ServerLevel level) {
		return Math.floorDiv(level.getOverworldClockTime(), 24000L);
	}

	/** How dangerous {@code player}'s hordes are, 0 to 1: the world's age, their world tier, their armour, the local difficulty. */
	static float danger(ServerPlayer player) {
		ServerLevel level = (ServerLevel) player.level();
		float age = Mth.clamp((day(level) - GRACE_DAYS) / (float) RAMP_DAYS, 0.0F, 1.0F);
		float tier = WorldTiers.of(player).ordinal() / (float) (WorldTiers.Tier.values().length - 1);
		float gear = Mth.clamp(player.getArmorValue() / 20.0F, 0.0F, 1.0F);
		float local = Mth.clamp(level.getCurrentDifficultyAt(player.blockPosition()).getSpecialMultiplier(), 0.0F, 1.0F);
		return Mth.clamp(AGE_WEIGHT * age + TIER_WEIGHT * tier + GEAR_WEIGHT * gear + LOCAL_WEIGHT * local, 0.0F, 1.0F);
	}

	/** Night, or day under rain (the Sun would only send them running for shade). */
	private static boolean hordeWeather(ServerLevel level) {
		return Moons.nightOf(level.getOverworldClockTime()) >= 0 || level.isRaining();
	}

	// ---- every tick -----------------------------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		long ticks = server.getTickCount();
		if (ticks % MARCH_EVERY == 0 && !HORDES.isEmpty()) {
			march();
		}
		if (ticks % ROLL_EVERY != 0) {
			return;
		}
		ServerLevel level = server.overworld();
		if (day(level) < GRACE_DAYS || !hordeWeather(level)) {
			return;
		}
		Moons.Moon moon = Moons.now(server);
		boolean night = Moons.nightOf(level.getOverworldClockTime()) >= 0;
		long now = level.getGameTime();
		RandomSource random = level.getRandom();
		for (ServerPlayer player : level.players()) {
			if (player.isSpectator() || player.isCreative() || !player.isAlive() || now < NEXT.getOrDefault(player.getUUID(), 0L)) {
				continue;
			}
			float chance;
			if (moon.blood()) {
				chance = moon == Moons.Moon.SUPER_BLOOD ? SUPER_BLOOD_CHANCE : BLOOD_CHANCE;
			} else {
				chance = NIGHT_CHANCE + NIGHT_CHANCE_DANGER * danger(player);
				if (!night) {
					chance /= DAY_DIVISOR;
				}
			}
			if (random.nextFloat() < chance && send(player, moon) > 0) {
				NEXT.put(player.getUUID(), now + (moon.blood() ? BLOOD_COOLDOWN : COOLDOWN));
			}
		}
	}

	// ---- a horde comes --------------------------------------------------------------------------------------------------

	/** Sends a horde {@code player}'s way, out of their sight. How many came (0 if there was no room or no place). */
	static int send(ServerPlayer player, Moons.Moon moon) {
		ServerLevel level = (ServerLevel) player.level();
		RandomSource random = level.getRandom();
		float danger = danger(player);
		int size = SIZE_MIN + Math.round(danger * (SIZE_MAX - SIZE_MIN)) + random.nextInt(3) - 1;
		float capFactor = 1.0F;
		if (moon.blood()) {
			size *= moon == Moons.Moon.SUPER_BLOOD ? SUPER_SIZE : BLOOD_SIZE;
			capFactor = moon == Moons.Moon.SUPER_BLOOD ? SUPER_CAP : BLOOD_CAP;
		}
		int cap = Math.round((NEAR_CAP + NEAR_CAP_DANGER * danger) * capFactor);
		int near = level.getEntitiesOfClass(Zombie.class, player.getBoundingBox().inflate(COUNT_RADIUS), z -> z.isAlive()).size();
		size = Math.min(size, cap - near);
		if (size < 2) {
			return 0;
		}
		// behind them: where they face, turned round, give or take
		float yaw = player.getYRot() * Mth.DEG_TO_RAD;
		for (int attempt = 0; attempt < 6; attempt++) {
			double angle = Math.atan2(Mth.cos(yaw), -Mth.sin(yaw)) + Math.PI + (random.nextDouble() * 2.0 - 1.0) * BEHIND;
			int dist = Mth.nextInt(random, SPAWN_MIN, SPAWN_MAX);
			BlockPos centre = BlockPos.containing(player.getX() + Math.cos(angle) * dist, player.getY(), player.getZ() + Math.sin(angle) * dist);
			if (!Loaded.made(level, centre) || !level.isPositionEntityTicking(centre)) {
				continue;
			}
			Horde horde = new Horde(level, player.getUUID(), moon.blood(), player.position());
			float armed = ARMED + ARMED_DANGER * danger;
			int tier = WorldTiers.of(player).ordinal();
			for (int tries = 0; horde.members.size() < size && tries < size * 4; tries++) {
				Mob mob = rise(level, player, centre, random, random.nextFloat() < armed, tier);
				if (mob != null) {
					horde.members.add(mob);
				}
			}
			if (horde.members.isEmpty()) {
				continue;
			}
			HORDES.add(horde);
			level.playSound(null, centre.getX() + 0.5, centre.getY(), centre.getZ() + 0.5, SoundEvents.ZOMBIE_AMBIENT, SoundSource.HOSTILE, GROAN_VOLUME, 0.55F);
			JaymosKingdoms.LOGGER.debug("a horde of {} rose for {} (danger {})", horde.members.size(), player.getScoreboardName(), danger);
			return horde.members.size();
		}
		return 0;
	}

	/** One of the horde, up near {@code centre} on dark, solid ground, or null if the spot won't do. */
	private static Mob rise(ServerLevel level, ServerPlayer player, BlockPos centre, RandomSource random, boolean armed, int tier) {
		BlockPos column = centre.offset(random.nextInt(SPREAD * 2 + 1) - SPREAD, 0, random.nextInt(SPREAD * 2 + 1) - SPREAD);
		if (!level.isPositionEntityTicking(column)) {
			return null;
		}
		EntityType<? extends Zombie> type = level.getBiome(column).is(Biomes.DESERT) || level.getBiome(column).is(Biomes.BADLANDS)
			? EntityTypes.HUSK : EntityTypes.ZOMBIE;
		BlockPos at = level.getHeightmapPos(SpawnPlacements.getHeightmapType(type), column);
		BlockPos below = at.below();
		if (Math.abs(at.getY() - player.getY()) > SPAWN_HEIGHT || level.getBrightness(LightLayer.BLOCK, at) > 0
			|| !level.getFluidState(at).isEmpty() || !level.getFluidState(below).isEmpty()
			|| !level.getBlockState(below).isFaceSturdy(level, below, Direction.UP) || !SpawnPlacements.isSpawnPositionOk(type, level, at)) {
			return null;
		}
		Zombie mob = type.create(level, EntitySpawnReason.EVENT);
		if (mob == null) {
			return null;
		}
		mob.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, random.nextFloat() * 360.0F, 0.0F);
		if (!level.noCollision(mob)) {
			mob.discard();
			return null;
		}
		mob.finalizeSpawn(level, level.getCurrentDifficultyAt(at), EntitySpawnReason.EVENT, null);
		if (armed && mob.getMainHandItem().isEmpty()) {
			mob.setItemSlot(EquipmentSlot.MAINHAND, tier <= 0 ? OLD_TOOLS[random.nextInt(OLD_TOOLS.length)].copy() : MonsterGear.weaponFor(level, random, tier));
		}
		mob.addTag(TAG);
		level.addFreshEntityWithPassengers(mob);
		return mob;
	}

	// ---- the march ------------------------------------------------------------------------------------------------------

	/** A zombie that may fall in with a horde: a plain wild one (not a thrall, a Bio-built's bud, a risen orc, a drowned or a piglin). */
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
			if (horde.members.isEmpty() || level.getGameTime() - horde.born > MAX_AGE || !hordeWeather(level)) {
				// sunrise, or it's had its night: ordinary zombies again
				horde.members.forEach(m -> m.removeTag(TAG));
				it.remove();
				continue;
			}
			Vec3 centre = centre(horde);
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
						Senses.lure(m, fight.position(), who, FIGHT_LOUDNESS);
					}
				}
				continue;
			}
			// the one they came for, while they're near enough; lost, it wanders on
			if (level.getPlayerByUUID(horde.hunted) instanceof ServerPlayer hunted && hunted.isAlive() && !hunted.isSpectator() && !hunted.isCreative()
				&& hunted.level() == level && hunted.position().distanceTo(centre) < TRACK_RANGE) {
				horde.goal = hunted.position();
			} else if (horde.goal.distanceTo(centre) < 6.0) {
				RandomSource random = level.getRandom();
				double angle = random.nextDouble() * Math.PI * 2.0;
				horde.goal = centre.add(Math.cos(angle) * WANDER, 0.0, Math.sin(angle) * WANDER);
			}
			// the strays it passes fall in
			int max = horde.blood ? BLOOD_MAX_MEMBERS : MAX_MEMBERS;
			if (horde.members.size() < max) {
				for (Zombie stray : level.getEntitiesOfClass(Zombie.class, new AABB(centre, centre).inflate(GATHER_RANGE), ZombieHordes::stray)) {
					if (horde.members.size() >= max) {
						break;
					}
					stray.addTag(TAG);
					horde.members.add(stray);
				}
			}
			for (Mob m : horde.members) {
				if (m.getTarget() == null) {
					// each keeps its own place in the crowd
					int h = m.getUUID().hashCode();
					Vec3 spot = horde.goal.add(((h & 7) - 3.5) * 0.8, 0.0, (((h >> 3) & 7) - 3.5) * 0.8);
					Senses.lure(m, spot, null, MARCH_LOUDNESS);
				}
			}
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
}
