package com.jaymoskingdoms;

import com.jaymoskingdoms.Temperaments.Temperament;
import java.util.ArrayList;
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
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.animal.golem.AbstractGolem;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Andy (2026-11-23), after an ocelot finished him in a street and walked right past a guard: "the npcs should react to
 * all of that, running from large animals or if they are in a group and start losing numbers they route and retreat".
 * People (players, villagers, MCA's, the realms' folk) are one kind of quarry, and whoever sees one of them hurt reacts
 * the same way whoever the victim was.
 * <ul>
 * <li><b>Whatever strikes a person is a foe</b> ({@link #attacker}): any mob that isn't a person itself, a pet or a golem
 * (an animal as much as a monster) that hurt a person within {@link #MEMORY}. Guards go for it on sight (MCA's through
 * {@code McaGuardFoesMixin}, the folk's through their monster target), and villagers run from it ({@link Frights}).</li>
 * <li><b>Witnesses</b>: those within {@link #WITNESS} who can see a person struck or killed react at once: a guard who
 * can't see the one who did it goes to the spot; everyone else runs from it.</li>
 * <li><b>The scene</b>: a person killed by a beast or a monster leaves a scene ({@link #SCENE_TICKS}: the body, the
 * blood) that frightens whoever comes on it later and draws the guards to look. A killer still by it is a foe again.</li>
 * <li><b>Big beasts</b> ({@link #FEARED}: bears, the big cats, crocodiles, feral wolves, the moose) frighten villagers as
 * monsters do, hunting or not ({@link #feared}).</li>
 * <li><b>The rout</b>: when a band of a side's fighters (guards, archers, soldiers, a camp's raiders) has lost
 * {@link #ROUT_SHARE} of itself (downed or dead) within {@link #BAND} blocks and {@link #ROUT_WINDOW}, with at least
 * {@link #ROUT_MIN_LOST} lost, the rest each may break and run ({@link #routChance}, by temperament: the brave never), for
 * {@link #ROUT_TICKS}.</li>
 * </ul>
 * Fights between people (a spar, an arrest, a war) are the realms' own business and never count here. Nothing is saved.
 */
public final class Bloodshed {
	// ================================================================================================================
	// Tuning
	/** How long (ticks) a mob that hurt a person stays a foe to the guards and a fright to everyone else. */
	static final int MEMORY = 20 * 30;
	/** How far off (blocks) a person struck or killed is seen. */
	static final double WITNESS = 16.0;
	/** How long (ticks) a kill's spot frightens those who come on it, and how far off they see it. */
	static final int SCENE_TICKS = 20 * 120;
	static final double SCENE_SIGHT = 12.0;
	/** A killer this near its kill when a guard comes on it is a foe again. */
	static final double KILLER_NEAR = 24.0;
	/** How often (ticks) the scenes are looked over and the bands counted. */
	static final int LOOK_EVERY = 20;
	/** A guard going to look walks at this pace. */
	static final float LOOK_SPEED = 0.8F;
	/** A band: a side's fighters within this of a loss. Losses this recent count. */
	static final double BAND = 20.0;
	static final int ROUT_WINDOW = 20 * 60;
	/** The band breaks once it has lost at least this many, and this share of itself. */
	static final int ROUT_MIN_LOST = 2;
	static final float ROUT_SHARE = 0.4F;
	/** How long (ticks) a routed fighter runs before it may turn back. */
	static final int ROUT_TICKS = 20 * 12;
	/** The chance each fighter breaks, by temperament (others {@link #ROUT_OTHER}). */
	static final float ROUT_TIMID = 1.0F, ROUT_GLOOMY = 0.8F, ROUT_BRAVE = 0.0F, ROUT_DUTIFUL = 0.2F, ROUT_BOLD = 0.25F, ROUT_OTHER = 0.55F;
	/** Lines each: a witness's cry, a band breaking. */
	static final int CRY_LINES = 4, ROUT_LINES = 4;
	// ================================================================================================================

	/** Beasts people run from on sight (data/jaymoskingdoms/tags/entity_type/feared_beasts.json). */
	public static final TagKey<EntityType<?>> FEARED = TagKey.create(Registries.ENTITY_TYPE, JaymosKingdoms.id("feared_beasts"));

	private record Scene(ResourceKey<Level> dimension, Vec3 at, long until, @Nullable UUID killer, Set<UUID> seen) {
	}

	private record Loss(UUID who, int faction, ResourceKey<Level> dimension, Vec3 at, long time) {
	}

	/** Game time each mob last hurt a person. */
	private static final Map<Mob, Long> STRUCK = new WeakHashMap<>();
	/** Fighters who broke and ran, until when. */
	private static final Map<LivingEntity, Long> ROUTED = new WeakHashMap<>();
	/** Fighters already counted as lost while they lie down. */
	private static final Map<LivingEntity, Boolean> DOWN_SEEN = new WeakHashMap<>();
	private static final List<Scene> SCENES = new ArrayList<>();
	private static final List<Loss> LOSSES = new ArrayList<>();

	private Bloodshed() {
	}

	static void register() {
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
			if (taken > 0.0F && source.getEntity() instanceof Mob by && preys(by) && person(entity) && entity.level() instanceof ServerLevel level) {
				STRUCK.put(by, level.getGameTime());
				witnessed(level, entity, by, false);
			}
		});
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (!(entity.level() instanceof ServerLevel level)) {
				return;
			}
			if (source.getEntity() instanceof Mob by && preys(by) && person(entity)) {
				STRUCK.put(by, level.getGameTime());
				SCENES.add(new Scene(level.dimension(), entity.position(), level.getGameTime() + SCENE_TICKS, by.getUUID(), new HashSet<>()));
				witnessed(level, entity, by, true);
			}
			if (fighter(entity)) {
				lost(level, entity, source.getEntity());
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(Bloodshed::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			STRUCK.clear();
			ROUTED.clear();
			DOWN_SEEN.clear();
			SCENES.clear();
			LOSSES.clear();
		});
	}

	/**
	 * {@code m} is to be taken for something that has just hurt a person, for another {@link #MEMORY}: a player's true shape of
	 * the night (the horror, {@link NightHorrors}) that folk must run from and guards go for.
	 */
	public static void alarm(Mob m) {
		STRUCK.put(m, m.level().getGameTime());
	}

	/** A person: a player in play, a villager (MCA's too), or one of the realms' folk. */
	static boolean person(Entity e) {
		return e instanceof Player p ? !p.isCreative() && !p.isSpectator() : e instanceof AbstractVillager || e instanceof Folk;
	}

	/** A mob whose blows on a person count: not a person itself, a pet, or a golem. */
	static boolean preys(Mob m) {
		return !(m instanceof AbstractVillager || m instanceof Folk || m instanceof AbstractGolem || m instanceof TamableAnimal t && t.isTame());
	}

	/** Whether {@code e} hurt a person within {@link #MEMORY}: a foe to the guards, a fright to the rest. */
	public static boolean attacker(@Nullable Entity e) {
		if (!(e instanceof Mob m) || !m.isAlive()) {
			return false;
		}
		Long at = STRUCK.get(m);
		return at != null && m.level().getGameTime() - at <= MEMORY;
	}

	/** A beast people run from on sight: grown, wild and loose. */
	static boolean feared(Mob m) {
		return m.getType().builtInRegistryHolder().is(FEARED) && !m.isBaby() && !m.isLeashed() && !(m instanceof TamableAnimal t && t.isTame());
	}

	/** Whether {@code v} broke and is running from a lost fight: it neither stands its ground nor goes for anyone. */
	public static boolean routed(@Nullable Entity v) {
		Long until = v instanceof LivingEntity l ? ROUTED.get(l) : null;
		return until != null && v.level().getGameTime() < until;
	}

	/** One of a side's fighters: a guard or an archer, a soldier, a camp's raider or chief. */
	static boolean fighter(Entity e) {
		if (!(e instanceof Worker w) || w.factionId() < 0 || !(e instanceof LivingEntity l) || l.isBaby()) {
			return false;
		}
		return Downed.isGuard(l) || w.role() == Folk.BARBARIAN || w.role() == Folk.CHIEF || WarParties.isSoldier(w)
			|| e instanceof PathfinderMob pm && McaCompat.isGuard(pm);
	}

	/** {@code victim} was struck (or killed) by {@code by}: whoever saw it reacts. */
	private static void witnessed(ServerLevel level, LivingEntity victim, Mob by, boolean killed) {
		boolean cried = false;
		for (Villager v : level.getEntitiesOfClass(Villager.class, victim.getBoundingBox().inflate(WITNESS),
			v -> v != victim && v.isAlive() && !Downed.isDowned(v) && v.hasLineOfSight(victim))) {
			if (guard(v)) {
				// it'll go for the one who did it if it can see it (attacker); if not, it goes to look
				if (!v.hasLineOfSight(by)) {
					look(v, victim.blockPosition());
				}
			} else if (!Frights.standsFirm(level.getServer(), v)) {
				if (!Frights.afraid(v.getUUID()) && killed && !cried) {
					cried = true;
					Speech.say(level, v, Component.translatable("talk.jaymoskingdoms.bloodshed.cry." + level.getRandom().nextInt(CRY_LINES)),
						Speech.YELLOW, Captains.SAY_RANGE);
				}
				Frights.scare(level, v, by.position());
			}
		}
	}

	/** A guard going to see what happened at {@code at}, if it isn't in a fight already. */
	private static void look(Villager guard, BlockPos at) {
		if (guard.getTarget() == null && !guard.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_TARGET) && !routed(guard)) {
			BehaviorUtils.setWalkAndLookTargetMemories(guard, at, LOOK_SPEED, 2);
		}
	}

	private static boolean guard(Villager v) {
		return Downed.isGuard(v) || McaCompat.isGuard(v) || v instanceof Worker w && WarParties.isSoldier(w);
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % LOOK_EVERY != 0) {
			return;
		}
		ROUTED.values().removeIf(until -> until < server.overworld().getGameTime());
		for (ServerLevel level : server.getAllLevels()) {
			long now = level.getGameTime();
			SCENES.removeIf(s -> s.dimension().equals(level.dimension()) && s.until() < now);
			LOSSES.removeIf(l -> l.dimension().equals(level.dimension()) && now - l.time() > ROUT_WINDOW);
			for (Scene s : SCENES) {
				if (s.dimension().equals(level.dimension()) && level.isPositionEntityTicking(BlockPos.containing(s.at()))) {
					scene(level, s);
				}
			}
			downedFighters(level);
			for (Map.Entry<LivingEntity, Long> e : ROUTED.entrySet()) {
				// a routed guard keeps out of the fight (MCA's brain would take it straight back in)
				if (e.getKey().level() == level && e.getKey() instanceof Villager v) {
					v.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
					v.setTarget(null);
				}
			}
		}
	}

	/** Those who come on a kill's spot: guards go to look (and know its killer, if it's still by it), the rest run. */
	private static void scene(ServerLevel level, Scene s) {
		Vec3 at = s.at().add(0.0, 0.3, 0.0);
		for (Villager v : level.getEntitiesOfClass(Villager.class, new AABB(at, at).inflate(SCENE_SIGHT),
			v -> v.isAlive() && !Downed.isDowned(v) && !s.seen().contains(v.getUUID()) && sees(level, v, at))) {
			s.seen().add(v.getUUID());
			if (guard(v)) {
				Entity killer = s.killer() == null ? null : level.getEntity(s.killer());
				if (killer instanceof Mob m && m.isAlive() && m.distanceToSqr(s.at()) < KILLER_NEAR * KILLER_NEAR) {
					STRUCK.put(m, level.getGameTime());
				}
				look(v, BlockPos.containing(s.at()));
			} else if (!Frights.standsFirm(level.getServer(), v)) {
				Frights.scare(level, v, s.at());
			}
		}
	}

	private static boolean sees(ServerLevel level, LivingEntity v, Vec3 at) {
		return level.clip(new ClipContext(v.getEyePosition(), at, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, v)).getType() == HitResult.Type.MISS;
	}

	/** A side's fighters who've gone down near a player (where the world is awake) count as lost, once each. */
	private static void downedFighters(ServerLevel level) {
		DOWN_SEEN.keySet().removeIf(e -> e.level() == level && (!e.isAlive() || !Downed.isDowned(e)));
		for (Player player : level.players()) {
			for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(64.0),
				e -> e.isAlive() && Downed.isDowned(e) && fighter(e) && !DOWN_SEEN.containsKey(e))) {
				DOWN_SEEN.put(e, true);
				lost(level, e, e.getLastHurtByMob());
			}
		}
	}

	/** {@code who}, one of a side's fighters, is down or dead (by {@code by}, if known): does its band break? */
	private static void lost(ServerLevel level, LivingEntity who, @Nullable Entity by) {
		UUID id = who.getUUID();
		for (Loss l : LOSSES) {
			if (l.who().equals(id)) {
				return;
			}
		}
		int faction = ((Worker) who).factionId();
		long now = level.getGameTime();
		Vec3 at = who.position();
		LOSSES.add(new Loss(id, faction, level.dimension(), at, now));
		int lost = 0;
		for (Loss l : LOSSES) {
			if (l.faction() == faction && l.dimension().equals(level.dimension()) && l.at().distanceToSqr(at) < BAND * BAND) {
				lost++;
			}
		}
		List<LivingEntity> standing = level.getEntitiesOfClass(LivingEntity.class, who.getBoundingBox().inflate(BAND),
			e -> e != who && e.isAlive() && !Downed.isDowned(e) && fighter(e) && ((Worker) e).factionId() == faction);
		if (lost < ROUT_MIN_LOST || lost < (lost + standing.size()) * ROUT_SHARE) {
			return;
		}
		boolean spoke = false;
		for (LivingEntity e : standing) {
			LivingEntity foe = foe(e, by);
			if (foe == null || routed(e) || Captains.is(e) || !(e instanceof PathfinderMob pm) || level.getRandom().nextFloat() >= routChance(e)) {
				continue;
			}
			rout(level, pm, foe);
			if (!spoke) {
				spoke = true;
				Speech.say(level, e, Component.translatable("talk.jaymoskingdoms.bloodshed.rout." + level.getRandom().nextInt(ROUT_LINES)),
					Speech.YELLOW, Captains.SAY_RANGE);
			}
		}
	}

	/** What {@code e} is fighting: its own target, else whoever felled its fellow. Null if it isn't in this fight. */
	private static @Nullable LivingEntity foe(LivingEntity e, @Nullable Entity by) {
		if (e instanceof Mob m && m.getTarget() != null) {
			return m.getTarget();
		}
		if (e.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_TARGET)) {
			return e.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null);
		}
		return by instanceof LivingEntity l && l.isAlive() && l.distanceToSqr(e) < BAND * BAND ? l : null;
	}

	/** {@code pm} breaks and runs from {@code foe}. */
	private static void rout(ServerLevel level, PathfinderMob pm, LivingEntity foe) {
		ROUTED.put(pm, level.getGameTime() + ROUT_TICKS);
		if (pm instanceof Villager v) {
			v.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
			v.setTarget(null);
			Frights.scare(level, v, foe.position());
		} else {
			Dread.flee(pm, foe, ROUT_TICKS);
		}
	}

	static float routChance(LivingEntity e) {
		Temperament t = Temperaments.of(e);
		if (t == null) {
			return ROUT_OTHER;
		}
		return switch (t) {
			case TIMID -> ROUT_TIMID;
			case GLOOMY -> ROUT_GLOOMY;
			case BRAVE -> ROUT_BRAVE;
			case DUTIFUL -> ROUT_DUTIFUL;
			case PROUD, HOTHEADED -> ROUT_BOLD;
			default -> ROUT_OTHER;
		};
	}
}
