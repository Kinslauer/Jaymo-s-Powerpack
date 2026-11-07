package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.490) Andy (2026-11-03): orcs "get by on force and brutality, toward everyone and each other", and their camps
 * "brawl inside and war with each other all the time". Three parts, each on the machinery already there:
 * <ul>
 * <li><b>Camp brawls.</b> Now and then two idle grunts of a loaded camp square up and fight with their fists: each hit
 * takes a share of the other's health, and whoever is beaten down to {@link #FLOOR} loses (never killed: his hurt is
 * stopped there, he is slowed, weakened and left to mend). The rest of the camp gathers round and jeers in orc talk.
 * A foe they hate walking up breaks it off.</li>
 * <li><b>Chief challenges.</b> Now and then the strongest idle grunt calls out the warchief and they fight the same way
 * ({@link #CHALLENGER_EDGE}: the chief is the harder to beat). If the warchief is the one beaten, the grunt is warchief
 * from then on and the old one a grunt. This isn't a death, so the camp is not cleared ({@link Camps#chiefSlain}).</li>
 * <li><b>Feuds.</b> An orc camp's war band may raid an orc camp of another tribe or a goblin village ({@link #rival},
 * from {@link Raids#target}); while one is on, each side's folk are the other's prey ({@link #foe}) and the two camps
 * aren't allies ({@link #atWar}).</li>
 * </ul>
 * Goblin slaves are {@link Slaves}'.
 */
final class Brutality {
	/** How often brawls are driven (ticks), and how often a camp looks to start one. */
	static final int DRIVE_TICKS = 10, START_TICKS = 100;
	/** A camp with a player this near (blocks) and loaded is one that brawls. */
	static final double ACTIVE_RANGE = 96.0;
	/** The chance a loaded camp starts a brawl, and (more rarely) a challenge, at each look. */
	static final float BRAWL_CHANCE = 0.025F, CHALLENGE_CHANCE = 0.004F;
	/** Grunts are looked for this far from the fire; a brawl pairs two within {@link #PAIR_RANGE} of each other. */
	static final double SEARCH_RADIUS = 16.0, PAIR_RANGE = 10.0;
	/** A challenge wants this many idle grunts in the camp, and a brawl this many orcs of every kind. */
	static final int CHALLENGE_MIN_GRUNTS = 3, BRAWL_MIN_GRUNTS = 2;
	/** A fighter starts at this share of his health or better. */
	static final float START_HEALTH = 0.8F;
	/** A fist's hit: this share of the other's top health, give or take {@link #HIT_SPREAD}; the loser is held at {@link #FLOOR}.
	 * ((0.7.490) the floor stays above a chief's rally, {@link Folk#RALLY_AT}: a beaten warchief doesn't call his camp in) */
	static final float HIT_SHARE = 0.07F, HIT_SPREAD = 0.3F, FLOOR = 0.45F;
	/** (0.7.490) A new warchief: camp folk this near (blocks) that are after him are called off. */
	static final double CALL_OFF_RANGE = 48.0;
	/** A challenger lands this much of a hit against the warchief (below 1: the chief is the harder to beat). */
	static final float CHALLENGER_EDGE = 0.9F;
	/** A shove from a fist. */
	static final double FIST_PUSH = 0.4;
	/** A brawl is called off after this long (ticks), and the camp keeps from another for this long. */
	static final long BRAWL_MAX_TICKS = 20 * 45, COOLDOWN_TICKS = 20 * 90;
	/** The loser is slowed and weakened this long (ticks), and mends this long. */
	static final int DOWN_TICKS = 20 * 6, MEND_TICKS = 20 * 20;
	/** A foe this near (blocks) that the camp hates breaks a brawl off. */
	static final double DISTURB_RANGE = 28.0;
	/** The crowd: how far from the fight it comes from, how many, how close it stands, how fast it comes. */
	static final double CROWD_RANGE = 18.0, CROWD_RING = 5.0, CROWD_SPEED = 1.0;
	/** (0.7.490) An MCA villager's pace to the ring, as its brain's walk target (its own wandering pace is 0.6). */
	static final float CROWD_WALK_SPEED = 0.6F;
	static final int CROWD_MAX = 6;
	/** A jeer from the crowd about this often (ticks, and as much again at random); heard this far (blocks). */
	static final int JEER_EVERY = 50;
	static final double SAY_RANGE = 40.0;
	/** How many lines of each kind there are (lang). */
	static final int JEER_LINES = 6, START_LINES = 4, WIN_LINES = 4, LOSE_LINES = 4, CHALLENGE_LINES = 4, REPLY_LINES = 3, NEW_CHIEF_LINES = 4,
		DEPOSED_LINES = 3;
	/** Strength for a challenge: health, blows ×, armour × (a tie goes by who he is). */
	static final double STRENGTH_DAMAGE = 4.0, STRENGTH_ARMOUR = 2.0;
	/** News of a new warchief reaches players this near. */
	static final double NEWS_RANGE = 64.0;

	private static final class Brawl {
		final int camp;
		final UUID a, b;
		/** {@code a} is calling out the warchief {@code b}. */
		final boolean challenge;
		final long started;
		@Nullable UUID loser;
		long jeerAt;
		final Set<UUID> crowd = new HashSet<>();

		Brawl(int camp, UUID a, UUID b, boolean challenge, long started) {
			this.camp = camp;
			this.a = a;
			this.b = b;
			this.challenge = challenge;
			this.started = started;
			this.jeerAt = started + JEER_EVERY;
		}
	}

	/** Camp id → the brawl in it; fighter → the brawl he's in; camp id → game time it may start another. */
	private static final Map<Integer, Brawl> BRAWLS = new HashMap<>();
	private static final Map<UUID, Brawl> FIGHTERS = new HashMap<>();
	private static final Map<Integer, Long> COOL = new HashMap<>();

	private Brutality() {
	}

	static void register() {
		ServerLivingEntityEvents.ALLOW_DAMAGE.register(Brutality::fist);
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % DRIVE_TICKS == 0) {
				tick(server);
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			BRAWLS.clear();
			FIGHTERS.clear();
			COOL.clear();
		});
	}

	// ---- feuds ------------------------------------------------------------------------------------------------------------

	/** Camps {@code a} and {@code b} are at each other's throats just now (a raid of one on the other). */
	static boolean atWar(MinecraftServer server, int a, int b) {
		return a != b && Raids.between(a, b);
	}

	/** {@code target} is a camp's villager or folk of a camp that {@code camp} is at war with just now. */
	static boolean foe(int camp, LivingEntity target) {
		if (!(target instanceof Worker w) || w.factionId() == camp) {
			return false;
		}
		MinecraftServer server = target.level().getServer();
		return server != null && atWar(server, camp, w.factionId());
	}

	/**
	 * (From {@link Raids#target}) {@code f}, another camp, is one orc camp {@code camp} may raid: a standing orc camp of
	 * another tribe, or a goblin village (not one of its own tribe).
	 */
	static boolean rival(MinecraftServer server, Faction camp, Faction f) {
		if (f.type() != FactionType.BARBARIAN || f.id() == camp.id() || f.leader().isEmpty() || !Camps.orc(server, camp.id())) {
			return false;
		}
		Camps.CampData data = Camps.data(server, f.id());
		if (data == null || data.clearedAt() >= 0) {
			return false;
		}
		if (Camps.goblin(server, f.id())) {
			return true;
		}
		if (!Camps.orc(server, f.id())) {
			return false;
		}
		String mine = Tribes.of(server, camp.id());
		return mine != null && !mine.equals(Tribes.of(server, f.id()));
	}

	// ---- fists --------------------------------------------------------------------------------------------------------------

	/** {@code mob} is in a camp brawl just now. */
	static boolean fighting(LivingEntity mob) {
		return FIGHTERS.containsKey(mob.getUUID());
	}

	/** Blows between two brawlers are fists: a share of health, never a kill, and the one beaten to the floor has lost. */
	private static boolean fist(LivingEntity victim, DamageSource source, float amount) {
		Brawl brawl = FIGHTERS.get(victim.getUUID());
		if (brawl == null || !(source.getEntity() instanceof LivingEntity hitter) || !(victim.level() instanceof ServerLevel level)) {
			return true;
		}
		UUID other = brawl.a.equals(victim.getUUID()) ? brawl.b : brawl.a;
		if (!hitter.getUUID().equals(other)) {
			return true;
		}
		if (brawl.loser == null) {
			RandomSource random = victim.getRandom();
			float cut = victim.getMaxHealth() * HIT_SHARE * (1.0F + (random.nextFloat() * 2.0F - 1.0F) * HIT_SPREAD)
				* (brawl.challenge && hitter.getUUID().equals(brawl.a) ? CHALLENGER_EDGE : 1.0F);
			float floor = victim.getMaxHealth() * FLOOR;
			if (victim.getHealth() - cut <= floor) {
				victim.setHealth(Math.min(victim.getHealth(), floor));
				brawl.loser = victim.getUUID();
			} else {
				victim.setHealth(victim.getHealth() - cut);
			}
			level.broadcastEntityEvent(victim, (byte)2);
			level.playSound(null, victim.getX(), victim.getY(), victim.getZ(), SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.HOSTILE, 1.0F, 0.7F);
			Vec3 away = victim.position().subtract(hitter.position()).multiply(1.0, 0.0, 1.0);
			if (away.lengthSqr() > 1.0E-4) {
				Vec3 push = away.normalize().scale(FIST_PUSH);
				victim.setDeltaMovement(push.x, 0.2, push.z);
				victim.syncVelocity = true;
			}
		}
		return false;
	}

	// ---- driving ------------------------------------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		ServerLevel level = Factions.level(server);
		long now = level.getGameTime();
		for (Brawl brawl : List.copyOf(BRAWLS.values())) {
			drive(level, brawl, now);
		}
		// (0.7.490) gate starts on the server tick count, the same clock as DRIVE_TICKS: game time has a fixed per-world offset
		if (server.getTickCount() % START_TICKS != 0) {
			return;
		}
		RandomSource random = level.getRandom();
		for (Faction camp : Factions.all(server, FactionType.BARBARIAN)) {
			if (camp.fallen() || camp.leader().isEmpty() || !Camps.orc(server, camp.id()) || BRAWLS.containsKey(camp.id())
				|| now < COOL.getOrDefault(camp.id(), 0L) || Raids.raiding(camp.id()) || Raids.raided(camp.id())) {
				continue;
			}
			Camps.CampData data = Camps.data(server, camp.id());
			if (data == null || data.clearedAt() >= 0 || !level.isPositionEntityTicking(data.center()) || !OrcCamps.wholeCampLoaded(level, data.center())
				|| !Raids.playerNear(level, data.center(), ACTIVE_RANGE)) {
				continue;
			}
			float roll = random.nextFloat();
			if (roll < CHALLENGE_CHANCE) {
				challenge(level, camp, data);
			} else if (roll < CHALLENGE_CHANCE + BRAWL_CHANCE) {
				brawl(level, camp, data);
			}
		}
	}

	/** The camp's orcs (folk or MCA villagers) of {@code role} (-1 for all) near {@code center}. */
	private static List<PathfinderMob> orcs(ServerLevel level, int camp, BlockPos center, int role) {
		return level.getEntitiesOfClass(PathfinderMob.class, new AABB(center).inflate(SEARCH_RADIUS, 8.0, SEARCH_RADIUS),
			e -> e instanceof Worker w && w.factionId() == camp && Orcs.isOrc(w.role()) && (role < 0 || w.role() == role));
	}

	/** It has nothing on its mind: awake, no foe, not on a raid or out hunting, whole enough, not already in a fight. */
	private static boolean idle(PathfinderMob e) {
		return e.isAlive() && !e.isSleeping() && !e.isPassenger() && Rulers.target(e) == null && !Raids.isRaider(e) && !Downed.isDowned(e)
			&& !McaCompat.busy(e) && !FIGHTERS.containsKey(e.getUUID()) && e.getHealth() >= e.getMaxHealth() * START_HEALTH
			&& e instanceof Worker w && w.task().isEmpty();
	}

	/** Two of the camp's idle grunts, near each other, square up. */
	private static void brawl(ServerLevel level, Faction camp, Camps.CampData data) {
		List<PathfinderMob> all = orcs(level, camp.id(), data.center(), -1);
		for (PathfinderMob orc : all) {
			if (Rulers.target(orc) != null) {
				// (the camp has a foe on it: no time for this)
				return;
			}
		}
		List<PathfinderMob> grunts = new ArrayList<>();
		for (PathfinderMob orc : all) {
			if (((Worker)orc).role() == Orcs.GRUNT && idle(orc)) {
				grunts.add(orc);
			}
		}
		if (grunts.size() < BRAWL_MIN_GRUNTS) {
			return;
		}
		RandomSource random = level.getRandom();
		PathfinderMob a = grunts.get(random.nextInt(grunts.size()));
		PathfinderMob b = null;
		double best = PAIR_RANGE * PAIR_RANGE;
		for (PathfinderMob other : grunts) {
			double d = other.distanceToSqr(a);
			if (other != a && d < best) {
				b = other;
				best = d;
			}
		}
		if (b != null) {
			start(level, camp.id(), a, b, false);
		}
	}

	/** The strongest idle grunt calls out the warchief. */
	private static void challenge(ServerLevel level, Faction camp, Camps.CampData data) {
		PathfinderMob chief = camp.leader().isPresent() && level.getEntity(camp.leader().get()) instanceof PathfinderMob m ? m : null;
		if (chief == null || !(chief instanceof Worker cw) || cw.role() != Orcs.WARCHIEF || !idle(chief)) {
			return;
		}
		List<PathfinderMob> all = orcs(level, camp.id(), data.center(), -1);
		PathfinderMob strongest = null;
		int grunts = 0;
		for (PathfinderMob orc : all) {
			if (Rulers.target(orc) != null) {
				return;
			}
			if (((Worker)orc).role() == Orcs.GRUNT && idle(orc)) {
				grunts++;
				if (strongest == null || strength(orc) > strength(strongest)) {
					strongest = orc;
				}
			}
		}
		if (strongest == null || grunts < CHALLENGE_MIN_GRUNTS) {
			return;
		}
		start(level, camp.id(), strongest, chief, true);
	}

	/** How strong an orc is: health, blows and armour, a tie going by who he is. */
	static double strength(PathfinderMob orc) {
		double tie = (orc.getUUID().getLeastSignificantBits() & 0xFF) / 255.0 * 3.0;
		return orc.getMaxHealth() + orc.getAttributeValue(Attributes.ATTACK_DAMAGE) * STRENGTH_DAMAGE + orc.getArmorValue() * STRENGTH_ARMOUR + tie;
	}

	private static void start(ServerLevel level, int camp, PathfinderMob a, PathfinderMob b, boolean challenge) {
		if (disturbed(level, a) || disturbed(level, b)) {
			return; // (0.7.490) a foe is near: no fight is called, so no shout and no cooldown spent
		}
		Brawl brawl = new Brawl(camp, a.getUUID(), b.getUUID(), challenge, level.getGameTime());
		BRAWLS.put(camp, brawl);
		FIGHTERS.put(a.getUUID(), brawl);
		FIGHTERS.put(b.getUUID(), brawl);
		Rulers.charge(a, b);
		Rulers.charge(b, a);
		RandomSource random = level.getRandom();
		if (challenge) {
			Speech.say(level, a, Dialects.trBrood(Dialects.ORC, "speech.jaymoskingdoms.orc.challenge_" + random.nextInt(CHALLENGE_LINES)), Speech.RED, SAY_RANGE);
			Speech.say(level, b, Dialects.trBrood(Dialects.ORC, "speech.jaymoskingdoms.orc.reply_" + random.nextInt(REPLY_LINES)), Speech.RED, SAY_RANGE);
		} else {
			Speech.say(level, a, Dialects.trBrood(Dialects.ORC, "speech.jaymoskingdoms.orc.brawl_" + random.nextInt(START_LINES)), Speech.RED, SAY_RANGE);
		}
		level.playSound(null, a.getX(), a.getY(), a.getZ(), SoundEvents.PIGLIN_BRUTE_ANGRY, SoundSource.HOSTILE, 1.2F, 0.6F);
	}

	private static @Nullable PathfinderMob mob(ServerLevel level, UUID id) {
		return level.getEntity(id) instanceof PathfinderMob m && m.isAlive() ? m : null;
	}

	private static void drive(ServerLevel level, Brawl brawl, long now) {
		PathfinderMob a = mob(level, brawl.a), b = mob(level, brawl.b);
		if (a == null || b == null || now - brawl.started > BRAWL_MAX_TICKS || disturbed(level, a)) {
			// (one is gone or dead, it's dragged on, or a foe has come: the fight is off)
			end(level, brawl, a, b, now);
			return;
		}
		if (brawl.loser != null) {
			finish(level, brawl, a, b, now);
			return;
		}
		if (Rulers.target(a) != b) {
			Rulers.charge(a, b);
		}
		if (Rulers.target(b) != a) {
			Rulers.charge(b, a);
		}
		crowd(level, brawl, a, b, now);
	}

	/**
	 * A foe of the camp's (a player it would hunt) is in sight of the fight. (0.7.490) Only players the orcs would really go
	 * for count: a truce, the tribe's favour or a sworn captain spares them, as in {@code McaCompat.foe}.
	 */
	private static boolean disturbed(ServerLevel level, PathfinderMob fighter) {
		return !level.getEntitiesOfClass(ServerPlayer.class, fighter.getBoundingBox().inflate(DISTURB_RANGE),
				p -> Orcs.prey(p) && !Tribes.spares(fighter, p)).isEmpty();
	}

	/** The idle orcs round about come to watch, and jeer. */
	private static void crowd(ServerLevel level, Brawl brawl, PathfinderMob a, PathfinderMob b, long now) {
		Vec3 mid = a.position().add(b.position()).scale(0.5);
		AABB box = new AABB(mid, mid).inflate(CROWD_RANGE, 6.0, CROWD_RANGE);
		for (PathfinderMob orc : level.getEntitiesOfClass(PathfinderMob.class, box,
			e -> e != a && e != b && e instanceof Worker w && w.factionId() == brawl.camp && w.role() == Orcs.GRUNT && idle(e))) {
			if (brawl.crowd.size() < CROWD_MAX || brawl.crowd.contains(orc.getUUID())) {
				brawl.crowd.add(orc.getUUID());
			}
		}
		List<PathfinderMob> watchers = new ArrayList<>();
		for (UUID id : Set.copyOf(brawl.crowd)) {
			PathfinderMob orc = mob(level, id);
			if (orc == null || Rulers.target(orc) != null || orc.isSleeping()) {
				brawl.crowd.remove(id);
				continue;
			}
			watchers.add(orc);
			if (orc.distanceToSqr(mid) > CROWD_RING * CROWD_RING) {
				if (orc instanceof Folk) {
					if (orc.getNavigation().isDone()) {
						orc.getNavigation().moveTo(mid.x, mid.y, mid.z, CROWD_SPEED);
					}
				} else if (!orc.getBrain().getMemory(MemoryModuleType.WALK_TARGET)
					.map(t -> t.getTarget().currentPosition().distanceToSqr(mid) <= CROWD_RING * CROWD_RING).orElse(false)) {
					// (0.7.490) an MCA villager walks where its brain is told to (as in Tempers); a path of our own is taken over by its wandering
					orc.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(mid, CROWD_WALK_SPEED, (int) CROWD_RING));
				}
			}
		}
		if (!watchers.isEmpty() && now >= brawl.jeerAt) {
			RandomSource random = level.getRandom();
			brawl.jeerAt = now + JEER_EVERY + random.nextInt(JEER_EVERY);
			jeer(level, watchers.get(random.nextInt(watchers.size())));
		}
	}

	private static void jeer(ServerLevel level, PathfinderMob orc) {
		Speech.say(level, orc, Dialects.trBrood(Dialects.ORC, "speech.jaymoskingdoms.orc.jeer_" + level.getRandom().nextInt(JEER_LINES)), Speech.RED, SAY_RANGE);
	}

	/** Stops {@code mob} fighting whom it was. */
	private static void release(@Nullable PathfinderMob mob) {
		if (mob == null) {
			return;
		}
		if (mob instanceof Folk) {
			mob.setTarget(null);
		} else {
			mob.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
		}
		mob.setLastHurtByMob(null);
		mob.getNavigation().stop();
	}

	private static void forget(Brawl brawl, long now) {
		BRAWLS.remove(brawl.camp);
		FIGHTERS.remove(brawl.a);
		FIGHTERS.remove(brawl.b);
		COOL.put(brawl.camp, now + COOLDOWN_TICKS);
	}

	/** Called off with no loser. */
	private static void end(ServerLevel level, Brawl brawl, @Nullable PathfinderMob a, @Nullable PathfinderMob b, long now) {
		release(a);
		release(b);
		forget(brawl, now);
	}

	/** One of them is beaten to the floor: he is slowed, weakened and left to mend, the other crows, the crowd roars. */
	private static void finish(ServerLevel level, Brawl brawl, PathfinderMob a, PathfinderMob b, long now) {
		PathfinderMob loser = brawl.a.equals(brawl.loser) ? a : b, winner = loser == a ? b : a;
		release(a);
		release(b);
		forget(brawl, now);
		RandomSource random = level.getRandom();
		loser.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, DOWN_TICKS, 2));
		loser.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, DOWN_TICKS, 0));
		loser.addEffect(new MobEffectInstance(MobEffects.REGENERATION, MEND_TICKS, 1));
		winner.addEffect(new MobEffectInstance(MobEffects.REGENERATION, MEND_TICKS, 1));
		level.sendParticles(ParticleTypes.CRIT, loser.getX(), loser.getY() + 1.0, loser.getZ(), 12, 0.4, 0.4, 0.4, 0.1);
		level.playSound(null, loser.getX(), loser.getY(), loser.getZ(), SoundEvents.PIGLIN_BRUTE_HURT, SoundSource.HOSTILE, 1.2F, 0.5F);
		Speech.say(level, loser, Dialects.trBrood(Dialects.ORC, "speech.jaymoskingdoms.orc.lose_" + random.nextInt(LOSE_LINES)), Speech.RED, SAY_RANGE);
		if (brawl.challenge && loser == b) {
			depose(level, brawl.camp, b, a);
		} else {
			Speech.say(level, winner, Dialects.trBrood(Dialects.ORC, "speech.jaymoskingdoms.orc.win_" + random.nextInt(WIN_LINES)), Speech.RED, SAY_RANGE);
		}
		for (UUID id : brawl.crowd) {
			PathfinderMob orc = mob(level, id);
			if (orc != null && random.nextInt(2) == 0) {
				jeer(level, orc);
				break;
			}
		}
	}

	/** The warchief was beaten: {@code winner} is warchief, and the old one a grunt. */
	private static void depose(ServerLevel level, int campId, PathfinderMob old, PathfinderMob winner) {
		MinecraftServer server = level.getServer();
		Faction camp = Factions.get(server, campId);
		Camps.CampData data = Camps.data(server, campId);
		if (camp == null || data == null) {
			return;
		}
		retitle(level, winner, Orcs.WARCHIEF, camp, 1.0F);
		retitle(level, old, Orcs.GRUNT, camp, old.getHealth() / old.getMaxHealth());
		BlockPos throne = OrcBuildings.throne(data.center(), data.gate());
		winner.setHomeTo(throne.relative(data.gate()), OrcCamps.WARCHIEF_HOME_RADIUS);
		old.setHomeTo(data.center(), OrcBuildings.HOME_RADIUS);
		CampChiefs.dropBar(old.getUUID());
		old.removeAttached(Bodies.BODY);
		// (the camp's leader is a new one; a grunt less and a grunt more, so the roster is as it was)
		Factions.update(server, campId, f -> f.withLeader(Optional.of(winner.getUUID())));
		callOff(level, campId, winner);
		RandomSource random = level.getRandom();
		Speech.say(level, winner, Dialects.trBrood(Dialects.ORC, "speech.jaymoskingdoms.orc.new_chief_" + random.nextInt(NEW_CHIEF_LINES)), Speech.RED, SAY_RANGE);
		Speech.say(level, old, Dialects.trBrood(Dialects.ORC, "speech.jaymoskingdoms.orc.deposed_" + random.nextInt(DEPOSED_LINES)), Speech.RED, SAY_RANGE);
		level.playSound(null, winner.getX(), winner.getY(), winner.getZ(), SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 3.0F, 0.9F);
		Component news = Component.translatable("message.jaymoskingdoms.orc.new_chief", winner.getDisplayName(), old.getDisplayName(), camp.name())
			.withStyle(ChatFormatting.GOLD);
		for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, winner.getBoundingBox().inflate(NEWS_RANGE))) {
			player.sendSystemMessage(news);
		}
		JaymosKingdoms.LOGGER.info("{} has a new warchief, won in a challenge", camp.name());
	}

	/** (0.7.490) Camp folk after the new warchief {@code winner} (a rally that fired anyway) are called off him. */
	private static void callOff(ServerLevel level, int campId, PathfinderMob winner) {
		AABB box = winner.getBoundingBox().inflate(CALL_OFF_RANGE);
		for (Folk folk : level.getEntitiesOfClass(Folk.class, box, f -> f != winner && f.factionId() == campId && Rulers.target(f) == winner)) {
			release(folk);
		}
		for (Worker w : Workers.near(level, box, x -> x.factionId() == campId && x.mob() != winner && Rulers.target(x.mob()) == winner)) {
			release(w.mob());
		}
	}

	/** {@code mob} takes {@code role}, a grunt's or the warchief's, in place (a folk by {@link Folk#changeRole}, a villager by its enlistment). */
	private static void retitle(ServerLevel level, PathfinderMob mob, int role, Faction camp, float healthShare) {
		if (mob instanceof Folk folk) {
			folk.changeRole(level, role, camp, healthShare);
		} else if (mob instanceof Enlisted villager) {
			villager.enlist(role, camp.id());
			Orcs.rank(mob, role, camp.id());
			mob.setHealth(Math.max(1.0F, mob.getMaxHealth() * healthShare));
		}
	}

	static void clear() {
		BRAWLS.clear();
		FIGHTERS.clear();
		COOL.clear();
	}
}
