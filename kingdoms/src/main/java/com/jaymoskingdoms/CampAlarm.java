package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A camp sounds the alarm. Andy, after watching a barbarian camp being cut down while the rest of it went about its day:
 * "if anyone in the area is attacked they should run back and alert the camp with a horn. then everyone is on alert and
 * patrols and defends until safe."
 * <ul>
 * <li><b>The runner.</b> When one of a camp's people (a barbarian, goblin or orc camp's: raiders, chief, casters and
 * builders, folk or MCA villagers) is struck within {@link #area} of its fire by someone not of the camp, the one struck
 * (or, if it fell, the nearest of its own who saw) drops the fight and runs for the fire, crying out as it goes.</li>
 * <li><b>The horn.</b> At the fire (within {@link #HORN_AT}), or after {@link #RUN_TICKS} wherever it got to, it raises
 * a goat horn and blows the call. If the one struck was at the fire already, the horn goes at once. If the runner is cut
 * down on the way, the next one of the camp struck runs instead.</li>
 * <li><b>The alert.</b> From the horn the whole camp is on alert. Its fighters (and its brave: {@link Temperaments#fearless})
 * go for the nearest foe in the camp's ground: whoever struck any of the camp, and any monster that walks in. With no foe
 * in sight they patrol, each walking to a fresh spot round the fire's ring every {@link #PATROL_EVERY} ticks. The rest
 * (builders, the timid) stop work and keep to the fire ({@link Frights#shelter}).</li>
 * <li><b>Standing down.</b> Once no foe has been on the camp's ground for {@link #CALM_TICKS} (and the alert has run
 * {@link #MIN_ALERT}), someone says so, the patrols end and the camp goes back to its day.</li>
 * </ul>
 * Fights inside a camp (its own people, an orc camp's brawls: {@link Brutality}) never sound it. Illager camps and pirate
 * coves keep their own ways ({@link Illagers}, {@link Pirates}). Nothing here is saved: a restart ends an alert.
 */
public final class CampAlarm {
	/** The camp's ground: this far past its palisade (or a grown greenskin camp's outer wall), blocks. */
	static final int AREA_MARGIN = 24;
	/** The runner blows the horn within this of the fire (blocks); gives up running and blows it where it is after this (ticks). */
	static final double HORN_AT = 7.0;
	static final long RUN_TICKS = 20L * 20;
	/** If the one struck is down, the nearest of its own within this (blocks) runs instead. */
	static final double WITNESS = 24.0;
	/** Paces: the runner, a defender going for a foe, a patrol (villager walk speeds; folk navigation speeds are the second of each). */
	static final float RUN_SPEED = 1.0F, DEFEND_SPEED = 0.9F, PATROL_SPEED = 0.55F;
	static final double FOLK_RUN = 1.3, FOLK_DEFEND = 1.2, FOLK_PATROL = 0.8;
	/** The horn: how loud (16 blocks a unit) and how long it's held up (ticks). */
	static final float HORN_VOLUME = 6.0F;
	static final long HORN_HOLD = 60L;
	/** The alert: it stands down once no foe has been on the ground this long, and never before it has lasted this long (ticks). */
	static final long CALM_TICKS = 20L * 30, MIN_ALERT = 20L * 45;
	/** A patroller picks a fresh spot this often (ticks); patrol spots lie this far from the fire (fractions of the palisade). */
	static final long PATROL_EVERY = 20L * 8;
	static final double PATROL_NEAR = 0.6, PATROL_FAR = 1.3;
	/** How often the camps on alert are kept (ticks). */
	static final int STEP_EVERY = 5;
	/** Lines each (lang {@code talk.jaymoskingdoms.camp_alarm.<key>.<n>}). */
	static final int RUN_LINES = 6, HORN_LINES = 6, CALM_LINES = 5;

	/** A camp's alarm: who's running with it (null once the horn's blown), who's struck the camp, when it went up. */
	private static final class Alarm {
		final int camp;
		@Nullable UUID runner;
		long runFrom;
		/** When the horn was blown (-1: not yet). */
		long sounded = -1L;
		long lastFoe;
		final Set<UUID> foes = new HashSet<>();

		Alarm(int camp) {
			this.camp = camp;
		}
	}

	private static final Map<Integer, Alarm> ALARMS = new HashMap<>();
	/** Patrollers: when each next picks a spot. */
	private static final Map<Mob, Long> PATROL_NEXT = new WeakHashMap<>();
	/** Who's holding the horn up: what their hand held before, and until when. */
	private static final Map<Mob, Held> HORNS = new WeakHashMap<>();

	private record Held(ItemStack before, long until) {
	}

	private CampAlarm() {
	}

	static void register() {
		ServerLivingEntityEvents.AFTER_DAMAGE.register(CampAlarm::struck);
		ServerTickEvents.END_SERVER_TICK.register(CampAlarm::tick);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			for (Mob m : new ArrayList<>(HORNS.keySet())) {
				lowerHorn(m);
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			ALARMS.clear();
			PATROL_NEXT.clear();
			HORNS.clear();
		});
	}

	/** {@code camp} is on alert just now (the horn has gone). */
	public static boolean alert(int camp) {
		Alarm a = ALARMS.get(camp);
		return a != null && a.sounded >= 0L;
	}

	// ---- who belongs ----------------------------------------------------------------------------------------------------

	/** The camp {@code e} belongs to, if it's one whose people sound the horn (a barbarian, goblin or orc camp), else null. */
	private static @Nullable Faction campOf(MinecraftServer server, LivingEntity e) {
		if (!(e instanceof Worker w) || w.factionId() < 0) {
			return null;
		}
		boolean ours = e instanceof Folk folk ? Barbarians.isBarbarian(folk) || Greenskins.isRole(folk.role()) : Barbarians.isCampVillager(e);
		if (!ours) {
			return null;
		}
		Faction f = Factions.get(server, w.factionId());
		if (f == null || f.fallen() || f.type() != FactionType.BARBARIAN) {
			return null;
		}
		String kind = Camps.kind(server, f.id());
		return Camps.BARBARIAN.equals(kind) || GoblinVillages.KIND.equals(kind) || OrcCamps.KIND.equals(kind) ? f : null;
	}

	private static boolean member(LivingEntity e, int camp) {
		return e instanceof Worker w && w.factionId() == camp && e.isAlive() && !e.isBaby() && !Downed.isDowned(e)
			&& e.level().getServer() != null && campOf(e.level().getServer(), e) != null;
	}

	/** It fights for the camp: a raider, the chief, a greenskin fighter, a caster, or one of the brave. */
	private static boolean fighter(LivingEntity e) {
		if (e instanceof Folk folk) {
			return folk.combatant() || Temperaments.fearless(e);
		}
		return e instanceof Worker w && Barbarians.villagerRole(w.role()) || Casters.campCaster(e) || Temperaments.fearless(e);
	}

	/** The camp's fire. */
	private static BlockPos fire(MinecraftServer server, Faction camp) {
		Camps.CampData data = Camps.data(server, camp.id());
		return data != null ? data.center() : camp.seat();
	}

	/** Its palisade's (or grown outer wall's) radius. */
	private static int wall(MinecraftServer server, Faction camp) {
		return Hordes.greenskin(server, camp.id()) ? Math.max(CampBuildings.PALISADE_RADIUS, Hordes.radius(server, camp.id())) : CampBuildings.PALISADE_RADIUS;
	}

	/** How far the camp's ground reaches from its fire (blocks). */
	static int area(MinecraftServer server, Faction camp) {
		return wall(server, camp) + AREA_MARGIN;
	}

	private static double flat(Vec3 a, BlockPos b) {
		double dx = a.x - (b.getX() + 0.5), dz = a.z - (b.getZ() + 0.5);
		return Math.sqrt(dx * dx + dz * dz);
	}

	// ---- struck -----------------------------------------------------------------------------------------------------------

	private static void struck(LivingEntity victim, DamageSource source, float baseDamage, float damage, boolean blocked) {
		if (!(victim.level() instanceof ServerLevel level) || !(source.getEntity() instanceof LivingEntity attacker) || attacker == victim) {
			return;
		}
		MinecraftServer server = level.getServer();
		Faction camp = campOf(server, victim);
		if (camp == null || level != Factions.level(server) || attacker instanceof Worker w && w.factionId() == camp.id()) {
			// (not a camp that sounds the horn; or one of its own: a brawl, a stray blow)
			return;
		}
		BlockPos fire = fire(server, camp);
		if (flat(victim.position(), fire) > area(server, camp)) {
			return;
		}
		long now = level.getGameTime();
		Alarm a = ALARMS.computeIfAbsent(camp.id(), Alarm::new);
		a.foes.add(attacker.getUUID());
		a.lastFoe = now;
		if (a.sounded >= 0L || a.runner != null && level.getEntity(a.runner) instanceof LivingEntity r && member(r, camp.id())) {
			// (the horn's gone already, or someone's running with it)
			return;
		}
		LivingEntity runner = member(victim, camp.id()) ? victim : witness(level, victim, camp.id());
		if (runner == null) {
			if (a.runner == null) {
				ALARMS.remove(camp.id());
			}
			return;
		}
		a.runner = runner.getUUID();
		a.runFrom = now;
		if (flat(runner.position(), fire) <= HORN_AT) {
			sound(level, camp, a, (Mob)runner, now);
			return;
		}
		Speech.say(level, runner, Component.translatable("talk.jaymoskingdoms.camp_alarm.run." + level.getRandom().nextInt(RUN_LINES)), Speech.RED, 32.0);
	}

	/** The nearest of the camp's own to {@code victim}, standing and in sight of it. */
	private static @Nullable LivingEntity witness(ServerLevel level, LivingEntity victim, int camp) {
		LivingEntity best = null;
		double bestD = Double.MAX_VALUE;
		for (Mob m : level.getEntitiesOfClass(Mob.class, victim.getBoundingBox().inflate(WITNESS), m -> m != victim && member(m, camp) && m.hasLineOfSight(victim))) {
			double d = m.distanceToSqr(victim);
			if (d < bestD) {
				bestD = d;
				best = m;
			}
		}
		return best;
	}

	// ---- the horn ---------------------------------------------------------------------------------------------------------

	private static void sound(ServerLevel level, Faction camp, Alarm a, Mob blower, long now) {
		a.runner = null;
		a.sounded = now;
		a.lastFoe = now;
		raiseHorn(blower, now);
		level.playSound(null, blower.getX(), blower.getY(), blower.getZ(), SoundEvents.GOAT_HORN_SOUND_VARIANTS.get(5).value(), SoundSource.NEUTRAL,
			HORN_VOLUME, 0.85F);
		Speech.say(level, blower, Component.translatable("talk.jaymoskingdoms.camp_alarm.horn." + level.getRandom().nextInt(HORN_LINES)), Speech.RED, 48.0);
	}

	/** {@code m} raises a goat horn to its lips (whatever its hand held goes back after {@link #HORN_HOLD}). */
	private static void raiseHorn(Mob m, long now) {
		if (!HORNS.containsKey(m)) {
			HORNS.put(m, new Held(m.getMainHandItem().copy(), now + HORN_HOLD));
			m.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.GOAT_HORN));
			m.setDropChance(EquipmentSlot.MAINHAND, 0.0F);
		}
		m.startUsingItem(InteractionHand.MAIN_HAND);
	}

	private static void lowerHorn(Mob m) {
		Held held = HORNS.remove(m);
		if (held == null) {
			return;
		}
		m.stopUsingItem();
		if (m.getMainHandItem().is(Items.GOAT_HORN)) {
			m.setItemSlot(EquipmentSlot.MAINHAND, held.before());
		}
	}

	// ---- the alert --------------------------------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % STEP_EVERY != 0) {
			return;
		}
		ServerLevel level = Factions.level(server);
		long now = level.getGameTime();
		List<Mob> done = new ArrayList<>();
		HORNS.forEach((m, held) -> {
			if (held.until() <= now || !m.isAlive()) {
				done.add(m);
			}
		});
		done.forEach(CampAlarm::lowerHorn);
		if (ALARMS.isEmpty()) {
			return;
		}
		for (Alarm a : new ArrayList<>(ALARMS.values())) {
			Faction camp = Factions.get(server, a.camp);
			if (camp == null || camp.fallen()) {
				ALARMS.remove(a.camp);
				continue;
			}
			if (a.sounded < 0L) {
				run(level, camp, a, now);
			} else {
				keep(level, camp, a, now);
			}
		}
	}

	/** The runner makes for the fire, and blows the horn when it's there (or has run long enough). */
	private static void run(ServerLevel level, Faction camp, Alarm a, long now) {
		Mob runner = a.runner != null && level.getEntity(a.runner) instanceof Mob m && member(m, camp.id()) ? m : null;
		if (runner == null) {
			// (cut down on the way: the next one struck runs instead)
			ALARMS.remove(a.camp);
			return;
		}
		BlockPos fire = fire(level.getServer(), camp);
		if (flat(runner.position(), fire) <= HORN_AT || now - a.runFrom >= RUN_TICKS) {
			sound(level, camp, a, runner, now);
			return;
		}
		// (no stopping to fight, nor to panic: it runs for the fire)
		Comrades.stand(runner);
		if (runner instanceof Villager v) {
			v.getBrain().eraseMemory(MemoryModuleType.HURT_BY);
			v.getBrain().eraseMemory(MemoryModuleType.HURT_BY_ENTITY);
			BehaviorUtils.setWalkAndLookTargetMemories(v, fire, RUN_SPEED, 2);
		} else {
			runner.getNavigation().moveTo(fire.getX() + 0.5, fire.getY(), fire.getZ() + 0.5, FOLK_RUN);
		}
	}

	/** The camp on alert: fighters go for the foes on its ground or patrol, the rest keep to the fire; it stands down once it's safe. */
	private static void keep(ServerLevel level, Faction camp, Alarm a, long now) {
		MinecraftServer server = level.getServer();
		BlockPos fire = fire(server, camp);
		int area = area(server, camp);
		AABB ground = new AABB(fire).inflate(area, 16.0, area);
		List<LivingEntity> foes = level.getEntitiesOfClass(LivingEntity.class, ground, e -> e.isAlive() && !Downed.isDowned(e)
			&& flat(e.position(), fire) <= area && foe(e, a, camp.id()));
		if (!foes.isEmpty()) {
			a.lastFoe = now;
		}
		List<Mob> people = level.getEntitiesOfClass(Mob.class, ground.inflate(16.0, 0.0, 16.0), m -> member(m, camp.id()));
		if (foes.isEmpty() && now - a.lastFoe >= CALM_TICKS && now - a.sounded >= MIN_ALERT) {
			standDown(level, a, people);
			return;
		}
		int wall = wall(server, camp);
		for (Mob m : people) {
			if (HORNS.containsKey(m)) {
				continue;
			}
			if (!fighter(m)) {
				// (to the fire, and keep there)
				if (m instanceof Villager v) {
					Frights.shelter(level, v, fire, now + STEP_EVERY * 4L);
				} else if (m.distanceToSqr(Vec3.atCenterOf(fire)) > HORN_AT * HORN_AT) {
					m.getNavigation().moveTo(fire.getX() + 0.5, fire.getY(), fire.getZ() + 0.5, FOLK_RUN);
				}
				continue;
			}
			LivingEntity foe = nearest(m, foes);
			if (foe != null) {
				PATROL_NEXT.remove(m);
				Comrades.aim(m, foe);
				if (m instanceof Villager v) {
					BehaviorUtils.setWalkAndLookTargetMemories(v, foe, DEFEND_SPEED, 1);
				} else if (m.getNavigation().isDone()) {
					m.getNavigation().moveTo(foe, FOLK_DEFEND);
				}
				continue;
			}
			patrol(level, m, fire, wall, now);
		}
	}

	/** A foe on the camp's ground: whoever struck it, or a monster that isn't the camp's own or its captive. */
	private static boolean foe(LivingEntity e, Alarm a, int camp) {
		if (a.foes.contains(e.getUUID())) {
			return true;
		}
		if (!(e instanceof Enemy) || e instanceof Worker w && w.factionId() == camp || Captives.bound(e)) {
			return false;
		}
		return !(e instanceof Folk folk && Barbarians.isBarbarian(folk));
	}

	private static @Nullable LivingEntity nearest(Mob m, List<LivingEntity> foes) {
		LivingEntity best = null;
		double bestD = Double.MAX_VALUE;
		for (LivingEntity f : foes) {
			double d = f.distanceToSqr(m);
			if (d < bestD) {
				bestD = d;
				best = f;
			}
		}
		return best;
	}

	/** {@code m} walks the camp's ring: a fresh spot round the fire every {@link #PATROL_EVERY}. */
	private static void patrol(ServerLevel level, Mob m, BlockPos fire, int wall, long now) {
		Long next = PATROL_NEXT.get(m);
		if (next != null && next > now) {
			return;
		}
		PATROL_NEXT.put(m, now + PATROL_EVERY + level.getRandom().nextInt(40));
		double angle = level.getRandom().nextDouble() * Math.PI * 2.0;
		double r = wall * Mth.lerp(level.getRandom().nextDouble(), PATROL_NEAR, PATROL_FAR);
		int x = fire.getX() + Mth.floor(Math.cos(angle) * r), z = fire.getZ() + Mth.floor(Math.sin(angle) * r);
		BlockPos spot = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, fire.getY(), z));
		if (m instanceof Villager v) {
			BehaviorUtils.setWalkAndLookTargetMemories(v, spot, PATROL_SPEED, 2);
		} else if (m instanceof PathfinderMob pm) {
			pm.getNavigation().moveTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, FOLK_PATROL);
		}
	}

	/** It's safe: someone says so, the patrols end, and the camp goes back to its day. */
	private static void standDown(ServerLevel level, Alarm a, List<Mob> people) {
		ALARMS.remove(a.camp);
		Mob speaker = null;
		for (Mob m : people) {
			if (PATROL_NEXT.remove(m) != null && m instanceof Villager v) {
				v.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
			}
			if (speaker == null && fighter(m)) {
				speaker = m;
			}
		}
		if (speaker != null) {
			Speech.say(level, speaker, Component.translatable("talk.jaymoskingdoms.camp_alarm.calm." + level.getRandom().nextInt(CALM_LINES)), Speech.GOLD, 32.0);
		}
	}
}
