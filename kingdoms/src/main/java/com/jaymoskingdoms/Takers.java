package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.npc.villager.Villager;
import org.jspecify.annotations.Nullable;

/**
 * A taker hides among a village's people (Psycho Villagers' idea of a killer behind a neighbour's face, ours alone).
 * <p><b>Who.</b> Once per village, the first time it's loaded with enough grown villagers in it, one roll: with
 * {@link #VAMPIRE_CHANCE} a grown villager of it is always a vampire ({@link Vampires}, a feeder that keeps its own
 * cellar, {@link TakersCellar}), at most {@link #MAX_PER_VILLAGE} a village; and with {@link #MORTAL_CHANCE} another is only a
 * would-be taker, a mortal gone wrong, far weaker. Both look and talk like everyone else by day.
 * <p><b>How.</b> At night the taker looks for someone it can take without a witness: a person alone (nobody else within
 * {@link #ALONE_RADIUS}), drunk (Kitchen's drink) or hurt, with no guard in sight of them or of it ({@link #GUARD_SIGHT}). It
 * creeps up without showing what it is (no red eyes, no alarm) until it's within {@link #SHOW_NEAR}; a guard seeing it on the
 * way makes it break off and walk home. A vampire taker bites as any vampire does and, as its prey goes down, drags them down
 * to its cellar instead of leaving them ({@link TakersCellar#drag}); a mortal one only strikes, and the victim, the guards
 * (called at once) and the village see to it, so most of them die at it.
 * <p><b>Tailing.</b> A player tailing a taker at night (behind it, close, unseen) sees it glance back now and then; if it
 * notices them ({@link Senses#notices}: sneaking in the dark keeps you unseen) it gives the night up, turns innocent, goes
 * home and remembers them ({@link Recollections.Kind#TAILED}).
 * <p><b>Gossip.</b> What a village says when asked the news ({@link #gossip}) hangs on whether anyone has gone missing: a safe
 * one speaks of the harvest, a stalked one of the neighbour who hasn't been seen. Never of what took them.
 */
public final class Takers {
	// ---- tuning ----
	/** How often (ticks) villages are looked at for a roll, how many grown villagers one needs loaded, and the chances (a vampire, a would-be taker). */
	static final int PICK_EVERY = 1200, PEOPLE_LEAST = 5;
	static final float VAMPIRE_CHANCE = 0.06F, MORTAL_CHANCE = 0.05F;
	/** The most vampire takers and mortal ones a village keeps. */
	static final int MAX_PER_VILLAGE = 1, MAX_MORTALS = 1;
	/** How far the taker looks for someone (blocks), how near it must be before it shows what it is, and how much of a crowd it won't act in. */
	static final double PREY_RANGE = 24.0, SHOW_NEAR = 4.0, ALONE_RADIUS = 8.0;
	/** A guard this near (blocks) with a clear line to the prey or the taker is a witness. */
	static final double GUARD_SIGHT = 28.0;
	/** Hurt: under this share of health. Drunk: Kitchen's drunk points from here. */
	static final float HURT_SHARE = 0.6F;
	static final int DRUNK_FROM = 4;
	/** A mortal taker: its health, its blow, how often it can strike (ticks) and how near. */
	static final double MORTAL_HEALTH = 10.0;
	static final float MORTAL_BLOW = 3.0F;
	static final int MORTAL_BLOW_EVERY = 20, MORTAL_GIVE_UP = 20 * 30;
	static final double MORTAL_REACH = 2.0, MORTAL_SPEED = 1.0;
	/** How near the guards are called when a mortal taker strikes (blocks). */
	static final double CALL_RANGE = 32.0;
	/** Tailing: how far behind a taker you may be (blocks), how often it glances (ticks, least and most), and the range its eyes carry. */
	static final double TAIL_RANGE = 16.0, TAIL_NEAR = 2.5, GLANCE_SIGHT = 14.0;
	static final int GLANCE_LEAST = 100, GLANCE_MOST = 240;
	/** How long (ticks) a taker that was spotted stays innocent, walking home. */
	static final int INNOCENT_TICKS = 20 * 90;
	/** How long (ticks) blood stays on a taker's boots after it drinks, and how near (blocks) a player must be to see it. */
	static final int BLOOD_TICKS = 24000, BOOTS_SIGHT = 5;
	/** A village is "stalked" in its talk for this many days after someone was taken, and this many names are remembered. */
	static final int STALKED_DAYS = 12, NAMES_KEPT = 4;
	/** Lines of village talk of each kind. */
	static final int LINES = 4;
	/** The chance the news is the taker-talk when there's some. */
	static final float TALK_CHANCE = 0.7F;

	/** On a village's person who is a taker: a vampire (else a mortal), where its hatch is (0: none), the cells still free in its cellar, its village. */
	public record Taker(boolean vampire, long hatch, List<Long> rooms, int village) {
		public static final Codec<Taker> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.BOOL.fieldOf("vampire").forGetter(Taker::vampire),
			Codec.LONG.optionalFieldOf("hatch", 0L).forGetter(Taker::hatch),
			Codec.LONG.listOf().optionalFieldOf("rooms", List.of()).forGetter(Taker::rooms),
			Codec.INT.optionalFieldOf("village", -1).forGetter(Taker::village)).apply(i, Taker::new));
	}

	/** What a village remembers: it has been rolled, the day someone was last taken, and who ("name|trade", newest first). */
	public record Log(boolean rolled, long lastDay, List<String> missing) {
		public static final Codec<Log> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.BOOL.fieldOf("rolled").forGetter(Log::rolled),
			Codec.LONG.optionalFieldOf("last_day", -1L).forGetter(Log::lastDay),
			Codec.STRING.listOf().optionalFieldOf("missing", List.of()).forGetter(Log::missing)).apply(i, Log::new));
	}

	static final AttachmentType<Taker> TAKER = AttachmentRegistry.create(JaymosKingdoms.id("taker"), builder -> builder.persistent(Taker.CODEC));
	/** Village id → its log. On the overworld. */
	static final AttachmentType<Map<String, Log>> LOGS = AttachmentRegistry.create(JaymosKingdoms.id("taker_logs"),
		builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, Log.CODEC)));
	/** Blood on a person's boots: the game time it was got. */
	static final AttachmentType<Long> BLOODY = AttachmentRegistry.create(JaymosKingdoms.id("taker_blood"), builder -> builder.persistent(Codec.LONG));

	private static final Map<UUID, PathfinderMob> LOADED = new HashMap<>();
	/** A mortal taker's chase: its prey, since when and its last blow. */
	private static final Map<UUID, long[]> MORTAL_PREY = new HashMap<>();
	private static final Map<UUID, UUID> MORTAL_TARGET = new HashMap<>();
	/** When each taker next glances back, and until when each is playing innocent (game time). */
	private static final Map<UUID, Long> GLANCES = new HashMap<>(), INNOCENT = new HashMap<>();
	/** The night a taker was last rested from hunting (spotted): index by taker. */
	private static final Map<UUID, Long> SPOTTED_NIGHT = new HashMap<>();

	private Takers() {
	}

	static void register() {
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof PathfinderMob mob && mob.hasAttached(TAKER)) {
				LOADED.put(mob.getUUID(), mob);
			}
		});
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> LOADED.remove(entity.getUUID()));
		ServerTickEvents.END_SERVER_TICK.register(Takers::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			LOADED.clear();
			MORTAL_PREY.clear();
			MORTAL_TARGET.clear();
			GLANCES.clear();
			INNOCENT.clear();
			SPOTTED_NIGHT.clear();
		});
	}

	/** A vampire taker's hatch is within {@code range} of {@code player} (it's shut, and always has been). */
	static boolean nearHatch(ServerPlayer player, double range) {
		for (PathfinderMob mob : LOADED.values()) {
			Taker t = mob.getAttached(TAKER);
			if (t != null && t.hatch() != 0L && mob.level() == player.level() && BlockPos.of(t.hatch()).distToCenterSqr(player.position()) <= range * range) {
				return true;
			}
		}
		return false;
	}

	/** {@code e} is a taker, of either kind. */
	public static boolean is(net.minecraft.world.entity.Entity e) {
		return e.hasAttached(TAKER);
	}

	// ---- who -----------------------------------------------------------------------------------------------------------

	private static Map<String, Log> logs(ServerLevel overworld) {
		return overworld.getAttachedOrElse(LOGS, Map.of());
	}

	private static void save(ServerLevel overworld, Map<String, Log> map) {
		overworld.setAttached(LOGS, Map.copyOf(map));
	}

	/** By day, each loaded village not yet rolled gets its one roll. */
	private static void pick(MinecraftServer server) {
		ServerLevel level = Factions.level(server);
		if (Vampires.night(level.getOverworldClockTime())) {
			return;
		}
		for (Faction village : Factions.all(server, FactionType.VILLAGE)) {
			if (village.fallen() || !level.isPositionEntityTicking(village.seat())) {
				continue;
			}
			Map<String, Log> all = new HashMap<>(logs(level));
			String key = Integer.toString(village.id());
			Log log = all.get(key);
			if (log != null && log.rolled()) {
				continue;
			}
			List<Villager> people = new ArrayList<>(Villages.people(level, village).stream()
				.filter(v -> Vampires.person(v) && !v.hasAttached(Captives.CAPTIVE) && !Rulers.is(v) && !Captives.bound(v)
					&& !(village.leader().isPresent() && village.leader().get().equals(v.getUUID()))).toList());
			if (people.size() < PEOPLE_LEAST) {
				continue;
			}
			all.put(key, new Log(true, log == null ? -1L : log.lastDay(), log == null ? List.of() : log.missing()));
			save(level, all);
			RandomSource random = level.getRandom();
			if (random.nextFloat() < VAMPIRE_CHANCE) {
				Villager chosen = people.remove(random.nextInt(people.size()));
				makeVampire(level, village, chosen);
			}
			if (random.nextFloat() < MORTAL_CHANCE && people.size() > 1) {
				Villager chosen = people.remove(random.nextInt(people.size()));
				makeMortal(level, village, chosen);
			}
		}
	}

	/** {@code who}, one of {@code village}'s, is a vampire taker with a cellar under their home. */
	static void makeVampire(ServerLevel level, Faction village, Villager who) {
		Vampires.turn(level, who, Vampires.Kind.FEEDER);
		BlockPos home = McaCompat.home(who).map(g -> g.pos()).orElse(who.blockPosition());
		Taker made = TakersCellar.dig(level, village, who, home);
		who.setAttached(TAKER, made);
		LOADED.put(who.getUUID(), who);
		JaymosKingdoms.LOGGER.info("A taker keeps house in {}: {} at {}", village.name(), who.getName().getString(), home.toShortString());
	}

	/** {@code who} is a would-be taker: a mortal, weak, with no cellar. */
	static void makeMortal(ServerLevel level, Faction village, Villager who) {
		who.setAttached(TAKER, new Taker(false, 0L, List.of(), village.id()));
		var health = who.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
		if (health != null) {
			health.setBaseValue(MORTAL_HEALTH);
			who.setHealth((float)MORTAL_HEALTH);
		}
		LOADED.put(who.getUUID(), who);
		JaymosKingdoms.LOGGER.info("A would-be taker lives in {}: {}", village.name(), who.getName().getString());
	}

	// ---- the hunt ------------------------------------------------------------------------------------------------------

	/** Whether {@code e} has no one else within {@link #ALONE_RADIUS} (players and the village's people). */
	private static boolean alone(ServerLevel level, LivingEntity e, PathfinderMob taker) {
		return level.getEntitiesOfClass(LivingEntity.class, e.getBoundingBox().inflate(ALONE_RADIUS),
			o -> o != e && o != taker && o.isAlive() && !o.isBaby() && (o instanceof Villager || o instanceof ServerPlayer p && !p.isSpectator() && !p.isCreative()
				|| Downed.isGuard(o)) && !Downed.isDowned(o)).isEmpty();
	}

	private static boolean hurt(LivingEntity e) {
		return e.getHealth() < e.getMaxHealth() * HURT_SHARE;
	}

	private static boolean drunk(LivingEntity e) {
		return e instanceof ServerPlayer p && KitchenCompat.drunk(p, DRUNK_FROM);
	}

	/** A guard (or an iron golem) sees {@code a} or {@code b}. */
	static boolean guarded(ServerLevel level, LivingEntity a, LivingEntity b) {
		return !level.getEntitiesOfClass(LivingEntity.class, a.getBoundingBox().inflate(GUARD_SIGHT),
			g -> g != a && g != b && g.isAlive() && !Downed.isDowned(g) && (Downed.isGuard(g) || g instanceof IronGolem)
				&& (g.hasLineOfSight(a) || g.hasLineOfSight(b))).isEmpty();
	}

	/** The one the taker would go for tonight: the nearest alone, drunk or hurt, that no guard sees; or null. */
	static @Nullable LivingEntity prey(ServerLevel level, PathfinderMob mob, java.util.function.Predicate<LivingEntity> allowed) {
		LivingEntity best = null;
		double bestD = Double.MAX_VALUE;
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, mob.getBoundingBox().inflate(PREY_RANGE),
			e -> e != mob && allowed.test(e) && !Downed.isDowned(e) && !is(e) && (Vampires.person(e) || Vampires.mortal(e)))) {
			double d = e.distanceToSqr(mob);
			if (d < bestD && (alone(level, e, mob) || hurt(e) || drunk(e)) && !guarded(level, e, mob)) {
				bestD = d;
				best = e;
			}
		}
		return best;
	}

	/** For {@link Vampires}: the vampire taker hunts only what {@link #prey} says. */
	static @Nullable LivingEntity vampirePrey(ServerLevel level, PathfinderMob mob) {
		return prey(level, mob, e -> true);
	}

	/** Still creeping up, not yet within {@link #SHOW_NEAR}: a taker shows nothing of what it is. */
	static boolean stalking(PathfinderMob mob, LivingEntity prey) {
		return mob.hasAttached(TAKER) && mob.distanceTo(prey) > SHOW_NEAR;
	}

	/** A guard sees the taker or its prey on the way: it breaks off the hunt (true). */
	static boolean abort(ServerLevel level, PathfinderMob mob, LivingEntity prey) {
		return stalking(mob, prey) && guarded(level, prey, mob);
	}

	// ---- the tick ------------------------------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		ServerLevel overworld = server.overworld();
		long now = overworld.getGameTime();
		if (now % PICK_EVERY == 0) {
			pick(server);
		}
		if (LOADED.isEmpty() || now % 10 != 0) {
			return;
		}
		boolean night = Vampires.night(overworld.getOverworldClockTime());
		for (PathfinderMob mob : new ArrayList<>(LOADED.values())) {
			Taker taker = mob.getAttached(TAKER);
			if (taker == null || !mob.isAlive() || !(mob.level() instanceof ServerLevel level)) {
				LOADED.remove(mob.getUUID());
				MORTAL_PREY.remove(mob.getUUID());
				continue;
			}
			if (!night) {
				MORTAL_PREY.remove(mob.getUUID());
				INNOCENT.remove(mob.getUUID());
				continue;
			}
			long t = level.getGameTime();
			Long innocent = INNOCENT.get(mob.getUUID());
			if (innocent != null && t < innocent) {
				goHome(mob, taker);
				continue;
			}
			glance(level, mob, taker, t);
			if (!taker.vampire()) {
				mortal(level, mob, taker, t);
			}
		}
	}

	/** Players tailing {@code mob}: it glances back now and then; seeing one, it turns innocent. */
	private static void glance(ServerLevel level, PathfinderMob mob, Taker taker, long now) {
		if (now < GLANCES.getOrDefault(mob.getUUID(), 0L)) {
			return;
		}
		RandomSource random = level.getRandom();
		GLANCES.put(mob.getUUID(), now + GLANCE_LEAST + random.nextInt(GLANCE_MOST - GLANCE_LEAST));
		ServerPlayer tail = null;
		for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class, mob.getBoundingBox().inflate(TAIL_RANGE), p -> !p.isCreative() && !p.isSpectator() && p.isAlive())) {
			// behind it: the taker's back is toward them
			var look = mob.getLookAngle().multiply(1.0, 0.0, 1.0);
			var to = p.position().subtract(mob.position()).multiply(1.0, 0.0, 1.0);
			if (to.lengthSqr() > TAIL_NEAR * TAIL_NEAR && look.dot(to.normalize()) < 0.0 && (tail == null || p.distanceTo(mob) < tail.distanceTo(mob))) {
				tail = p;
			}
		}
		if (tail == null) {
			return;
		}
		// the glance: it turns its head, and if its eyes can make the follower out it knows
		mob.getLookControl().setLookAt(tail.getX(), tail.getEyeY(), tail.getZ());
		level.playSound(null, mob.blockPosition(), SoundEvents.VILLAGER_AMBIENT, SoundSource.NEUTRAL, 0.6F, 1.0F);
		if (Senses.notices(mob, tail, GLANCE_SIGHT)) {
			spotted(level, mob, taker, tail, now);
		}
	}

	/** {@code mob} saw {@code by} tailing it: it gives the night up, plays innocent, goes home, and remembers them. */
	private static void spotted(ServerLevel level, PathfinderMob mob, Taker taker, ServerPlayer by, long now) {
		Vampires.standDown(mob);
		MORTAL_PREY.remove(mob.getUUID());
		MORTAL_TARGET.remove(mob.getUUID());
		INNOCENT.put(mob.getUUID(), now + INNOCENT_TICKS);
		SPOTTED_NIGHT.put(mob.getUUID(), Vampires.nightIndex(level.getOverworldClockTime()));
		Speech.say(level, mob, Component.translatable("speech.jaymoskingdoms.taker.innocent." + level.getRandom().nextInt(LINES)), Speech.WHITE, 12.0);
		if (mob instanceof Villager v) {
			Recollections.remember(v, by, Recollections.Kind.TAILED, Component.empty());
		}
		goHome(mob, taker);
	}

	/** Whether {@code mob} gave tonight up because it was seen. */
	static boolean sleptOn(PathfinderMob mob, long night) {
		return SPOTTED_NIGHT.getOrDefault(mob.getUUID(), -1L) == night;
	}

	private static void goHome(PathfinderMob mob, Taker taker) {
		if (!(mob instanceof Villager v)) {
			return;
		}
		BlockPos home = McaCompat.home(v).map(g -> g.pos()).orElse(null);
		if (home != null && v.distanceToSqr(home.getX() + 0.5, home.getY(), home.getZ() + 0.5) > 4.0) {
			BehaviorUtils.setWalkAndLookTargetMemories(v, new BlockPosTracker(home), 0.7F, 1);
		}
	}

	// ---- a mortal taker ------------------------------------------------------------------------------------------------

	private static void mortal(ServerLevel level, PathfinderMob mob, Taker taker, long now) {
		if (Vampires.night(level.getOverworldClockTime()) && sleptOn(mob, Vampires.nightIndex(level.getOverworldClockTime()))) {
			return;
		}
		long[] state = MORTAL_PREY.get(mob.getUUID());
		LivingEntity prey = null;
		UUID id = MORTAL_TARGET.get(mob.getUUID());
		if (id != null && level.getEntity(id) instanceof LivingEntity l && l.isAlive() && !Downed.isDowned(l)) {
			prey = l;
		}
		if (prey == null || state == null || now - state[0] > MORTAL_GIVE_UP) {
			MORTAL_PREY.remove(mob.getUUID());
			MORTAL_TARGET.remove(mob.getUUID());
			if (level.getRandom().nextInt(6) != 0) {
				return;
			}
			prey = prey(level, mob, e -> true);
			if (prey == null) {
				return;
			}
			MORTAL_PREY.put(mob.getUUID(), new long[] {now, 0L});
			MORTAL_TARGET.put(mob.getUUID(), prey.getUUID());
			state = MORTAL_PREY.get(mob.getUUID());
		}
		if (guarded(level, prey, mob) && stalking(mob, prey)) {
			MORTAL_PREY.remove(mob.getUUID());
			MORTAL_TARGET.remove(mob.getUUID());
			goHome(mob, taker);
			return;
		}
		Vampires.chase(mob, prey);
		if (mob.distanceTo(prey) <= MORTAL_REACH && now - state[1] >= MORTAL_BLOW_EVERY) {
			state[1] = now;
			DamageSource source = level.damageSources().mobAttack(mob);
			mob.swing(net.minecraft.world.InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
			prey.hurtServer(level, source, MORTAL_BLOW);
			callGuards(level, mob);
			if (prey instanceof Villager victim) {
				// the neighbours fight back as they can: it's one of them against one of them
				victim.setLastHurtByMob(mob);
			}
			Speech.say(level, mob, Component.translatable("speech.jaymoskingdoms.taker.caught." + level.getRandom().nextInt(LINES)), Speech.RED, 16.0);
		}
	}

	/** The guards about go for {@code mob}: it was seen at its work. */
	private static void callGuards(ServerLevel level, PathfinderMob mob) {
		for (LivingEntity g : level.getEntitiesOfClass(LivingEntity.class, mob.getBoundingBox().inflate(CALL_RANGE),
			e -> e.isAlive() && (Downed.isGuard(e) || e instanceof IronGolem) && !Downed.isDowned(e))) {
			if (g instanceof net.minecraft.world.entity.Mob m) {
				m.setTarget(mob);
			}
		}
	}

	// ---- blood, and taking ---------------------------------------------------------------------------------------------

	/** The taker {@code mob} fed or took someone: blood on its boots for a day. */
	static void stained(PathfinderMob mob) {
		mob.setAttached(BLOODY, mob.level().getGameTime());
	}

	/** Boots a player could notice blood on: a taker that fed within the day, a butcher at work, anyone badly hurt (red herrings among them). */
	static boolean bloodyBoots(Villager v) {
		Long at = v.getAttached(BLOODY);
		if (at != null && v.level().getGameTime() - at < BLOOD_TICKS) {
			return true;
		}
		String job = net.minecraft.core.registries.BuiltInRegistries.VILLAGER_PROFESSION.getKey(v.getVillagerData().profession().value()).getPath();
		return job.equals("butcher") || v.getHealth() < v.getMaxHealth() * 0.5F;
	}

	/** {@code victim} (named, of a trade) was taken from {@code village}: remembered, talked of, and a clue to those near. */
	static void taken(ServerLevel level, @Nullable Faction village, LivingEntity victim) {
		ServerLevel overworld = level.getServer().overworld();
		if (village != null) {
			Map<String, Log> all = new HashMap<>(logs(overworld));
			String key = Integer.toString(village.id());
			Log log = all.getOrDefault(key, new Log(true, -1L, List.of()));
			List<String> missing = new ArrayList<>(log.missing());
			missing.addFirst(victim.getName().getString().replace('|', ' ') + "|" + trade(victim));
			while (missing.size() > NAMES_KEPT) {
				missing.removeLast();
			}
			all.put(key, new Log(true, Math.floorDiv(overworld.getOverworldClockTime(), 24000L), missing));
			save(overworld, all);
		}
		for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class, victim.getBoundingBox().inflate(Villages.VILLAGE_RADIUS))) {
			Casebook.note(p, Casebook.MISSING, victim.getName().getString(), victim);
		}
	}

	private static String trade(LivingEntity e) {
		if (e instanceof Villager v) {
			return net.minecraft.core.registries.BuiltInRegistries.VILLAGER_PROFESSION.getKey(v.getVillagerData().profession().value()).getPath();
		}
		return "";
	}

	// ---- talk ----------------------------------------------------------------------------------------------------------

	/**
	 * What {@code v} says of the news ({@link ChatTalk}): a safe village the harvest and the weather, a stalked one of the
	 * neighbour who hasn't been seen (never of what took them), and (if the realm boiled over lately) of the glass in the
	 * lanes ({@link Unrest#talk}). A taker only ever talks of the harvest. Null when it has none to say.
	 */
	static @Nullable Component gossip(Villager v, ServerPlayer listener, RandomSource random) {
		if (!(v.level() instanceof ServerLevel level)) {
			return null;
		}
		Faction village = Theft.lawAt(level, v.blockPosition());
		if (village == null) {
			return null;
		}
		Component spill = Unrest.talk(level, village, random);
		if (spill != null && random.nextFloat() < 0.5F) {
			return spill;
		}
		if (random.nextFloat() >= TALK_CHANCE) {
			return null;
		}
		Log log = logs(level.getServer().overworld()).get(Integer.toString(village.id()));
		long day = Math.floorDiv(level.getServer().overworld().getOverworldClockTime(), 24000L);
		boolean stalked = log != null && !log.missing().isEmpty() && log.lastDay() >= 0 && day - log.lastDay() <= STALKED_DAYS && !is(v);
		if (!stalked) {
			return Component.translatable("talk.jaymoskingdoms.takers.safe." + random.nextInt(LINES));
		}
		String[] who = log.missing().get(random.nextInt(log.missing().size())).split("\\|", -1);
		String trade = who.length > 1 ? who[1] : "";
		Component named = trade.isEmpty() ? Component.literal(who[0])
			: Component.translatable("talk.jaymoskingdoms.takers.of_trade", who[0], Component.translatable("entity.minecraft.villager." + trade));
		Casebook.note(listener, Casebook.MISSING, who[0], null);
		return Component.translatable("talk.jaymoskingdoms.takers.stalked." + random.nextInt(LINES), named);
	}
}
