package com.jaymoskingdoms;

import com.jaymoskingdoms.monsters.Monsters;
import com.jaymoskingdoms.monsters.Ogre;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.490) Ogre raids. A standing ogre hold ({@link OgreHolds}) that has the strength for it sends a war band of its
 * ogres against the nearest settlement with a player near it, the way a camp's war party goes ({@link Raids}): they muster
 * toward the hold, march in, smash into the storehouse and every loose chest and barrel on the land ({@link Plunder}),
 * each carrying one great load (in the off hand, dropped where he falls), and lumber home to heap it in the hold's stash
 * ({@link OgreGrowth#deposit}), which is what pays for the hold's next stage. Kill a carrier and the loot is yours.
 *
 * <p>The band is mustered for the raid and is not part of the hold's own ogres. Raids are not saved: a raider left over
 * after a restart is sent home (his load dropped).
 */
final class OgreRaids {
	// ================================================================================================================
	// Tuning
	/** How often a hold considers a raid (ticks), the chance it does when it can, and the wait after one (ticks: two days). */
	static final int CHECK_TICKS = 400;
	static final float RAID_CHANCE = 0.10F;
	static final long COOLDOWN_TICKS = 48000L;
	/** The same settlement isn't raided again by ogres for this long (ticks: a day). */
	static final long TARGET_COOLDOWN_TICKS = 24000L;
	/** A hold raids settlements this far from its throne, when a player is this near their seat. */
	static final double REACH = 360.0, PLAYER_RANGE = 80.0;
	/** The band: a base, one more for every this many growth stages, at most this many. */
	static final int PARTY_BASE = 2, STAGES_PER_OGRE = 3, PARTY_MAX = 6;
	/** One in this many of the band is a grey cave ogre. */
	static final int CAVE_ODDS = 3;
	/** Where it musters: this far from the seat, toward the hold. */
	static final int[] MUSTER_DISTANCE = {44, 36, 52, 28};
	/** How often the raid is driven (ticks); the march's pace and waypoint. */
	static final int TICK = 10;
	static final double MARCH_SPEED = 0.95, WAYPOINT = 16.0;
	/** Raiders keep within this of the seat on the attack. */
	static final int RAID_HOME_RADIUS = 22;
	/** It has arrived when a raider is this near the seat, or after this long (ticks). */
	static final double ARRIVE_RANGE = 14.0;
	static final long MARCH_TICKS = 20 * 100;
	/** Plundering: the reach to a chest, how much an ogre carries, how long they keep at it (ticks), and the share of chests they smash. */
	static final double LOOT_REACH = 4.5;
	static final int LOAD = 64;
	static final long PLUNDER_TICKS = 20 * 100;
	static final float SMASH = 0.6F;
	/** Golems this near a raider at the seat turn on him. */
	static final double GOLEM_RANGE = 20.0;
	/** Home: a raider is home this near the hold, or once no player is this near him. */
	static final double HOME_REACH = 9.0, VANISH_RANGE = 48.0;
	/** A raid that drags on this long (ticks) is given up. */
	static final long RAID_MAX_TICKS = 20 * 60 * 9;
	/** A load carried home drops for sure (a drop chance over 1 is kept whole). */
	static final float LOAD_DROP = 2.0F;
	/** Reputation with the target for every ogre a player kills during its raid. */
	static final int REP_RAIDER_SLAIN = 5;
	// ================================================================================================================

	/** On a raiding ogre. */
	static final String RAIDER_TAG = "jaymoskingdoms.ogre_raider";

	enum Phase {
		MARCH, PLUNDER, RETURN
	}

	private static final class Raid {
		final int site, target;
		final List<UUID> party = new ArrayList<>();
		final Set<UUID> emptyHanded = new HashSet<>();
		Phase phase = Phase.MARCH;
		int home, looted;
		final long started;
		long phaseAt;
		@Nullable List<BlockPos> loose;

		Raid(int site, int target, long now) {
			this.site = site;
			this.target = target;
			this.started = now;
			this.phaseAt = now;
		}
	}

	/** Site id → its raid under way. */
	private static final Map<Integer, Raid> ACTIVE = new HashMap<>();
	/** Settlement id → game time ogres last raided it. */
	private static final Map<Integer, Long> RAIDED = new HashMap<>();
	/** Raiders that loaded with no raid to belong to (sent home next tick). */
	private static final List<Entity> STRAYS = new ArrayList<>();

	private OgreRaids() {
	}

	static boolean raiding(int site) {
		return ACTIVE.containsKey(site);
	}

	static boolean isRaider(Entity e) {
		return e.entityTags().contains(RAIDER_TAG);
	}

	static void clear() {
		ACTIVE.clear();
		RAIDED.clear();
		STRAYS.clear();
	}

	// ---- starting ------------------------------------------------------------------------------------------------

	static void tick(MinecraftServer server) {
		ServerLevel level = Factions.level(server);
		if (!STRAYS.isEmpty()) {
			List<Entity> strays = List.copyOf(STRAYS);
			STRAYS.clear();
			for (Entity stray : strays) {
				if (stray.isAlive() && !stray.isRemoved() && !inRaid(stray)) {
					dismiss(level, stray);
				}
			}
		}
		long now = level.getGameTime();
		if (now % CHECK_TICKS == 0) {
			consider(server, level, now);
		}
		if (now % TICK == 0) {
			for (Raid raid : List.copyOf(ACTIVE.values())) {
				drive(level, raid, now);
			}
		}
	}

	private static boolean inRaid(Entity e) {
		for (Raid raid : ACTIVE.values()) {
			if (raid.party.contains(e.getUUID())) {
				return true;
			}
		}
		return false;
	}

	/** An ogre of a raid loaded: if his raid is over (or was lost to a restart) he is sent home next tick. */
	static void loaded(Entity e) {
		if (isRaider(e)) {
			STRAYS.add(e);
		}
	}

	private static void consider(MinecraftServer server, ServerLevel level, long now) {
		if (level.getDifficulty() == Difficulty.PEACEFUL) {
			return;
		}
		RandomSource random = level.getRandom();
		for (OgreHolds.Site site : OgreHolds.sites(server)) {
			if (!site.hold() || site.cleared() || raiding(site.id()) || now - site.lastRaid() < COOLDOWN_TICKS
				|| !level.isPositionEntityTicking(site.at()) || !(level.getEntity(site.leader()) instanceof LivingEntity king) || !king.isAlive()) {
				continue;
			}
			if (random.nextFloat() >= Moons.raidChance(server, RAID_CHANCE) * Raids.seasonFactor(level)) {
				continue;
			}
			Faction target = target(server, level, site, now);
			if (target != null) {
				start(level, site, king, target, now);
			}
		}
	}

	/** The nearest standing settlement within {@link #REACH} of {@code site} with a player near its seat that has not been raided lately. */
	private static @Nullable Faction target(MinecraftServer server, ServerLevel level, OgreHolds.Site site, long now) {
		Faction best = null;
		double bestDist = REACH * REACH;
		for (Faction f : Factions.all(server)) {
			if (f.fallen() || f.type() == FactionType.BARBARIAN || now - RAIDED.getOrDefault(f.id(), -TARGET_COOLDOWN_TICKS) < TARGET_COOLDOWN_TICKS
				|| Raids.raided(f.id())) {
				continue;
			}
			double d = Raids.horizontal(f.seat(), site.at());
			if (d >= bestDist || !level.isPositionEntityTicking(f.seat()) || !Raids.playerNear(level, f.seat(), PLAYER_RANGE)) {
				continue;
			}
			if (ACTIVE.values().stream().anyMatch(r -> r.target == f.id())) {
				continue;
			}
			best = f;
			bestDist = d;
		}
		return best;
	}

	private static @Nullable BlockPos muster(ServerLevel level, BlockPos hold, Faction target) {
		double dx = hold.getX() - target.seat().getX(), dz = hold.getZ() - target.seat().getZ();
		double len = Math.max(1.0, Math.sqrt(dx * dx + dz * dz));
		for (int distance : MUSTER_DISTANCE) {
			double d = Math.min(distance, Math.max(8.0, len - 8.0));
			BlockPos column = target.seat().offset(Mth.floor(dx / len * d), 0, Mth.floor(dz / len * d));
			if (!level.isPositionEntityTicking(column)) {
				continue;
			}
			BlockPos at = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column);
			if (level.getFluidState(at.below()).isEmpty() && level.getFluidState(at).isEmpty()) {
				return at;
			}
		}
		return null;
	}

	private static void start(ServerLevel level, OgreHolds.Site site, LivingEntity king, Faction target, long now) {
		MinecraftServer server = level.getServer();
		BlockPos muster = muster(level, site.at(), target);
		if (muster == null) {
			return;
		}
		RandomSource random = level.getRandom();
		Raid raid = new Raid(site.id(), target.id(), now);
		ACTIVE.put(site.id(), raid);
		RAIDED.put(target.id(), now);
		OgreHolds.put(server, site.withRaid(now));
		int size = Math.min(PARTY_MAX, PARTY_BASE + site.stage() / STAGES_PER_OGRE);
		for (int i = 0; i < size; i++) {
			BlockPos at = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, muster.offset(random.nextInt(7) - 3, 0, random.nextInt(7) - 3));
			Ogre ogre = OgreHolds.ogre(level, i % CAVE_ODDS == CAVE_ODDS - 1 ? Monsters.CAVE_OGRE : Monsters.OGRE, at, target.seat(), RAID_HOME_RADIUS, site.id());
			if (ogre == null) {
				continue;
			}
			ogre.setTwoHeads(random.nextInt(3) == 0);
			ogre.addTag(RAIDER_TAG);
			raid.party.add(ogre.getUUID());
		}
		if (raid.party.isEmpty()) {
			ACTIVE.remove(site.id());
			return;
		}
		Raids.tell(level, target.seat(), Component.translatable("message.jaymoskingdoms.raid.announce.ogre", king.getDisplayName(), target.name())
			.withStyle(ChatFormatting.RED));
		for (ServerPlayer player : Raids.players(level, target.seat(), Raids.ANNOUNCE_RANGE)) {
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 2.0F, 0.5F);
		}
		JaymosKingdoms.LOGGER.info("The ogre hold at {} raids {} with {} ogres from {}", site.at(), target.name(), raid.party.size(), muster);
	}

	// ---- driving ---------------------------------------------------------------------------------------------------

	private static void drive(ServerLevel level, Raid raid, long now) {
		MinecraftServer server = level.getServer();
		OgreHolds.Site site = OgreHolds.site(server, raid.site);
		Faction target = Factions.get(server, raid.target);
		List<Ogre> party = new ArrayList<>();
		for (UUID id : raid.party) {
			if (level.getEntity(id) instanceof Ogre ogre && ogre.isAlive()) {
				party.add(ogre);
			}
		}
		if (site == null || site.cleared()) {
			// (the king is dead: his raiders lose heart, dropping what they carry)
			for (Ogre ogre : party) {
				dismiss(level, ogre);
			}
			ACTIVE.remove(raid.site);
			return;
		}
		if (party.isEmpty()) {
			if (raid.phase != Phase.RETURN && target != null) {
				Raids.tell(level, target.seat(), Component.translatable("message.jaymoskingdoms.raid.ogre.beaten").withStyle(ChatFormatting.GREEN));
			}
			ACTIVE.remove(raid.site);
			return;
		}
		if (now - raid.started > RAID_MAX_TICKS) {
			end(level, raid, site, party);
			return;
		}
		if (target == null || target.fallen()) {
			raid.phase = Phase.RETURN;
		}
		switch (raid.phase) {
			case MARCH -> {
				boolean arrived = now - raid.phaseAt > MARCH_TICKS;
				for (Ogre ogre : party) {
					if (ogre.getTarget() == null) {
						walk(level, ogre, target.seat());
					}
					arrived |= ogre.distanceToSqr(Vec3.atCenterOf(target.seat())) < ARRIVE_RANGE * ARRIVE_RANGE;
				}
				if (arrived) {
					raid.phase = Phase.PLUNDER;
					raid.phaseAt = now;
					Economy.noteAttack(server, target, party.getFirst());
					Raids.tell(level, target.seat(), Component.translatable("message.jaymoskingdoms.raid.arrive.ogre", target.name()).withStyle(ChatFormatting.RED));
				}
			}
			case PLUNDER -> plunderPhase(level, raid, site, target, party, now);
			case RETURN -> {
				for (Ogre ogre : party) {
					if (ogre.distanceToSqr(Vec3.atCenterOf(site.at())) < HOME_REACH * HOME_REACH || !Raids.playerNear(level, ogre.blockPosition(), VANISH_RANGE)) {
						homeAt(level, site, ogre);
						raid.party.remove(ogre.getUUID());
						raid.home++;
					} else if (ogre.getTarget() == null) {
						walk(level, ogre, site.at());
					}
				}
				int before = raid.party.size();
				raid.party.removeIf(id -> !(level.getEntity(id) instanceof Ogre));
				raid.home += before - raid.party.size();
				if (raid.party.isEmpty()) {
					ACTIVE.remove(raid.site);
					if (target != null) {
						if (raid.looted > 0) {
							Raids.tell(level, target.seat(), Component.translatable("message.jaymoskingdoms.raid.ogre.looted", raid.looted).withStyle(ChatFormatting.GOLD));
						}
						Raids.tell(level, target.seat(), Component.translatable("message.jaymoskingdoms.raid.ogre.withdraw").withStyle(ChatFormatting.GOLD));
					}
				}
			}
		}
	}

	private static void plunderPhase(ServerLevel level, Raid raid, OgreHolds.Site site, Faction target, List<Ogre> party, long now) {
		Economy.noteAttack(level.getServer(), target, party.getFirst());
		BlockPos depot = Builders.depot(target);
		if (raid.loose == null) {
			raid.loose = new ArrayList<>(Plunder.looseAll(level, target, Raids.LOOSE_SCAN));
		}
		boolean bare = !raid.loose.isEmpty() && Storage.contents(level, target).isEmpty();
		boolean done = true;
		for (Ogre ogre : party) {
			for (IronGolem golem : level.getEntitiesOfClass(IronGolem.class, ogre.getBoundingBox().inflate(GOLEM_RANGE), g -> g.isAlive() && g.getTarget() == null)) {
				golem.setTarget(ogre);
			}
			if (!ogre.getOffhandItem().isEmpty() || raid.emptyHanded.contains(ogre.getUUID())) {
				continue;
			}
			done = false;
			if (ogre.getTarget() != null) {
				continue;
			}
			// (0.7.490) the last loose chest may have gone to an earlier ogre this pass: back to the depot then
			bare &= !raid.loose.isEmpty();
			BlockPos aim = bare ? raid.loose.get(Math.floorMod(ogre.getUUID().hashCode(), raid.loose.size())) : depot;
			boolean near = ogre.distanceToSqr(Vec3.atCenterOf(aim)) <= LOOT_REACH * LOOT_REACH;
			if (!near && !bare) {
				for (BlockPos pos : target.holdings().storage()) {
					near |= ogre.distanceToSqr(Vec3.atCenterOf(pos)) <= LOOT_REACH * LOOT_REACH;
				}
			}
			if (near) {
				ItemStack taken = Plunder.take(level, ogre, target, LOAD, SMASH, false);
				if (taken.isEmpty()) {
					if (bare) {
						raid.loose.remove(aim);
					} else {
						raid.emptyHanded.add(ogre.getUUID());
					}
				} else {
					raid.looted += taken.getCount();
					ogre.setItemSlot(EquipmentSlot.OFFHAND, taken);
					ogre.setDropChance(EquipmentSlot.OFFHAND, LOAD_DROP);
					level.playSound(null, ogre.getX(), ogre.getY(), ogre.getZ(), SoundEvents.WOOD_BREAK, SoundSource.HOSTILE, 1.4F, 0.5F);
				}
			} else {
				walk(level, ogre, aim);
			}
		}
		if (done || now - raid.phaseAt > PLUNDER_TICKS) {
			raid.phase = Phase.RETURN;
			raid.phaseAt = now;
			for (Ogre ogre : party) {
				ogre.setHomeTo(site.at(), OgreHolds.HOLD_RADIUS);
			}
		}
	}

	private static void walk(ServerLevel level, Ogre ogre, BlockPos to) {
		if (!ogre.getNavigation().isDone() && level.getGameTime() % (TICK * 4) != 0) {
			return;
		}
		double dx = to.getX() + 0.5 - ogre.getX(), dz = to.getZ() + 0.5 - ogre.getZ();
		double len = Math.sqrt(dx * dx + dz * dz);
		BlockPos goal = to;
		if (len > WAYPOINT) {
			BlockPos column = BlockPos.containing(ogre.getX() + dx / len * WAYPOINT, ogre.getY(), ogre.getZ() + dz / len * WAYPOINT);
			goal = level.isLoaded(column) ? level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column) : column;
		}
		ogre.getNavigation().moveTo(goal.getX() + 0.5, goal.getY(), goal.getZ() + 0.5, MARCH_SPEED);
	}

	/** An ogre is home: what he carries is heaped in the hold's stash, and he is gone from the raid. */
	private static void homeAt(ServerLevel level, OgreHolds.Site site, Ogre ogre) {
		ItemStack load = take(ogre);
		if (!load.isEmpty()) {
			OgreGrowth.deposit(level, site, load);
		}
		ogre.discard();
	}

	private static ItemStack take(Entity ogre) {
		if (!(ogre instanceof Ogre o)) {
			return ItemStack.EMPTY;
		}
		ItemStack load = o.getOffhandItem();
		o.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
		o.setDropChance(EquipmentSlot.OFFHAND, 0.0F);
		return load;
	}

	private static void end(ServerLevel level, Raid raid, OgreHolds.Site site, List<Ogre> party) {
		ACTIVE.remove(raid.site);
		for (Ogre ogre : party) {
			homeAt(level, site, ogre);
		}
	}

	/** A raider with no hold to go to (or no raid): his load drops where he stands, and he is gone. */
	private static void dismiss(ServerLevel level, Entity ogre) {
		ItemStack load = take(ogre);
		if (!load.isEmpty() && ogre instanceof Ogre o) {
			o.spawnAtLocation(level, load);
		}
		ogre.discard();
	}

	// ---- events ----------------------------------------------------------------------------------------------------

	/** A raiding ogre fell: off the band; a player who killed him earns the target's thanks (his load fell with him). */
	static void raiderDied(ServerLevel level, LivingEntity ogre, DamageSource source) {
		if (!isRaider(ogre)) {
			return;
		}
		for (Raid raid : ACTIVE.values()) {
			if (raid.party.remove(ogre.getUUID())) {
				Faction target = Factions.get(level.getServer(), raid.target);
				if (target != null && source.getEntity() instanceof ServerPlayer player) {
					Reputation.change(player, target, REP_RAIDER_SLAIN);
				}
				return;
			}
		}
	}
}
