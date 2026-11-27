package com.jaymoskingdoms;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Endermite;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Silverfish;
import net.minecraft.world.entity.monster.spider.Spider;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

/**
 * (Andy, 2026-11-27) "guards should respond contextually, for example they always say halt criminal scum, even if attacking
 * a zombie, or animal". MCA's guards shouted from one list of battle cries ({@code villager.attack}: "Stop right here,
 * criminal scum!", "Kill those monsters!", "Intruder!") whatever they were going for, a cow as much as a thief. Now what a
 * guard shouts comes from what it faces ({@link Foe}): only a thief, a crook or a player its realm wants hears "Halt!";
 * the dead, spiders, creepers, great brutes, other monsters, wild beasts, loose livestock, raiders, a realm at war, an
 * unwelcome player and a brawler who just drew blood each have their own. How far off it is says the kind of line
 * ({@link Phase}): from afar a guard warns and calls the others, close in it's a battle cry, three or more of a kind is a
 * call for help against the lot, and a guard badly hurt shouts for aid. When it brings a monster or a beast down it says
 * so in kind (MCA's own kill line, "Monster down!" over a cow, is kept quiet: {@code McaGuardKillLineMixin}). A camp's
 * people (raiders, pirates, greenskins: {@link Outlaws}) never speak for the law; they have their own rougher cries.
 *
 * <p>When a disaster strikes near (a quake, a flood, a storm, a tornado touching down, fire from the sky, a great wave, a
 * slide: {@link #disaster}), the guards about shout the townsfolk to safety. Fires have their own alarm ({@link FireAlarm}).
 *
 * <p>Lines don't come round again soon ({@link #RECENT_TICKS}): a line said by anyone is passed over while others are
 * fresh, so a squad of guards doesn't shout the same words at once. All of it is speech bubbles ({@link Speech}); no chat.
 * Lang: {@code guardcry.jaymoskingdoms.<foe>.<phase>.<n>}, {@code guardcry.jaymoskingdoms.help.<n>},
 * {@code guardcry.jaymoskingdoms.outlaw.<kind>.<phase>.<n>}, {@code guardcry.jaymoskingdoms.disaster.<kind>.<n>}.
 */
public final class GuardCries {
	/** Lines in each set. */
	static final int LINES = 5;
	/** A line said by anyone is passed over this long while others are fresh (ticks). */
	static final long RECENT_TICKS = 20 * 45;
	/** Past this a foe is "over there" (a warning, a call to the others); within it a battle cry (blocks). */
	static final double SPOT_RANGE = 12.0;
	/** "Many": this many of a kind within this of the one the guard is going for. */
	static final int MANY = 3;
	static final double MANY_RANGE = 12.0;
	/** Hurt below this share of its health, a guard calls for aid instead (MCA's guard falls back below a quarter). */
	static final float HELP_HEALTH = 0.45F;
	/** How far a cry carries. */
	static final double SHOUT = 24.0;
	/** A kill is called out this often, and not within this of the speaker's last cry (ticks). */
	static final float KILL_CHANCE = 0.6F;
	static final long KILL_QUIET_TICKS = 40;
	/** A great brute: this wide, or this much health. */
	static final float BIG_WIDTH = 1.4F, BIG_HEALTH = 60.0F;
	/** A disaster: guards this far past its reach shout too; at most this many of them; only one this near a player. */
	static final double DISASTER_MARGIN = 16.0, DISASTER_BOX_MOST = 96.0, DISASTER_HEARD = 48.0;
	static final int DISASTER_CRIERS = 3;

	/** What a guard is facing. */
	enum Foe {
		/** A player the realm wants or has outlawed, a villager crook, a looter: the law's lines ("Halt!"). */
		CRIME(true, false, false),
		/** A player the realm counts hostile (or a tale's, a siege's foe): not welcome. */
		HOSTILE(true, false, false),
		/** A person who has just drawn blood, or some other person a guard has gone for. */
		BRAWL(true, false, false),
		/** Another realm's soldier in a war. */
		WAR(true, true, false),
		/** A camp's raiders, pirates, bandits, assassins; illagers and their beasts. */
		RAIDER(true, true, false),
		/** The dead: zombies, skeletons, the risen. */
		UNDEAD(true, true, true),
		/** Spiders and the crawling things. */
		CRAWLER(true, true, true),
		CREEPER(true, false, true),
		/** A great brute of a monster. */
		BRUTE(true, false, true),
		MONSTER(true, true, true),
		/** A dangerous wild beast (wolves, bears, the feared beasts, any beast that has just hurt someone). */
		BEAST(true, true, true),
		/** Some other animal: loose livestock. */
		ANIMAL(true, false, true);

		final boolean spot, many, kill;

		Foe(boolean spot, boolean many, boolean kill) {
			this.spot = spot;
			this.many = many;
			this.kill = kill;
		}

		String key() {
			return this.name().toLowerCase(java.util.Locale.ROOT);
		}
	}

	/** The kind of line: from afar, close in, against many, a kill. */
	enum Phase {
		SPOT, FIGHT, MANY, KILL;

		String key() {
			return this.name().toLowerCase(java.util.Locale.ROOT);
		}
	}

	/** A camp's people's own: who they face. */
	enum Rough {
		PERSON, MONSTER, BEAST;

		String key() {
			return this.name().toLowerCase(java.util.Locale.ROOT);
		}
	}

	/** Line key → when it was last said (game time). */
	private static final Map<String, Long> SAID = new HashMap<>();
	/** Speaker → when it last cried out. */
	private static final Map<Entity, Long> LAST_CRY = new WeakHashMap<>();

	private GuardCries() {
	}

	static void register() {
		ServerLivingEntityEvents.AFTER_DEATH.register(GuardCries::died);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			SAID.clear();
			LAST_CRY.clear();
		});
	}

	// ---- the battle cry -------------------------------------------------------------------------------------------------

	/**
	 * (From MCA's guard battle cry, {@code McaGuardCryMixin}) What {@code guard} shouts going for {@code target}, or null for
	 * nothing (golems, the young, no target).
	 */
	public static @Nullable MutableComponent cry(ServerLevel level, PathfinderMob guard, @Nullable LivingEntity target) {
		if (target == null || !target.isAlive() || guard.isBaby()) {
			return null;
		}
		long now = level.getGameTime();
		boolean hurt = guard.getHealth() < guard.getMaxHealth() * HELP_HEALTH;
		double distance = guard.distanceTo(target);
		String key;
		if (outlawVoice(guard)) {
			Rough r = rough(target);
			key = hurt ? "guardcry.jaymoskingdoms.outlaw.help" : "guardcry.jaymoskingdoms.outlaw." + r.key() + ".fight";
		} else if (hurt) {
			key = "guardcry.jaymoskingdoms.help";
		} else {
			Foe foe = foe(guard, target);
			Phase phase = distance > SPOT_RANGE && foe.spot ? Phase.SPOT : Phase.FIGHT;
			if (foe.many && many(level, guard, target, foe)) {
				phase = Phase.MANY;
			}
			key = "guardcry.jaymoskingdoms." + foe.key() + "." + phase.key();
		}
		LAST_CRY.put(guard, now);
		return Component.translatable(pick(key, now, level.getRandom()));
	}

	/** (From MCA's guard battle cry) {@code guard} shouts going for {@code target}, aloud: false if it has nothing to say. */
	public static boolean shout(ServerLevel level, PathfinderMob guard, @Nullable LivingEntity target) {
		MutableComponent words = cry(level, guard, target);
		if (words == null) {
			return false;
		}
		Speech.say(level, guard, words, Speech.RED, SHOUT);
		return true;
	}

	/** A guard brought down {@code dead}: it may call it out, in kind. */
	private static void died(LivingEntity dead, net.minecraft.world.damagesource.DamageSource source) {
		if (!(dead.level() instanceof ServerLevel level) || !(source.getEntity() instanceof PathfinderMob guard) || !guard.isAlive() || guard.isBaby()
			|| !speaks(guard) || Bloodshed.person(dead) || dead instanceof Raider) {
			// (a person or a raider goes down, not dead, unless beheaded or gibbed: those have their own scenes)
			return;
		}
		long now = level.getGameTime();
		Long last = LAST_CRY.get(guard);
		if (last != null && now - last < KILL_QUIET_TICKS || level.getRandom().nextFloat() > KILL_CHANCE) {
			return;
		}
		String key;
		if (outlawVoice(guard)) {
			Rough r = rough(dead);
			if (r == Rough.PERSON) {
				return;
			}
			key = "guardcry.jaymoskingdoms.outlaw." + r.key() + ".kill";
		} else {
			Foe foe = foe(guard, dead);
			if (!foe.kill) {
				return;
			}
			key = "guardcry.jaymoskingdoms." + foe.key() + ".kill";
		}
		LAST_CRY.put(guard, now);
		Speech.say(level, guard, Component.translatable(pick(key, now, level.getRandom())), Speech.RED, SHOUT);
	}

	/** {@code e} is a guard, an archer, or a camp's fighter: one who shouts in a fight. */
	static boolean speaks(LivingEntity e) {
		return Downed.isGuard(e) || Bloodshed.fighter(e);
	}

	/** {@code speaker} is one of a camp's people (raiders, pirates, greenskins, bandits): it never speaks for the law. */
	static boolean outlawVoice(Entity speaker) {
		return Outlaws.is(speaker) || Tribes.of(speaker) != null;
	}

	/** What {@code guard} makes of {@code target}. */
	static Foe foe(LivingEntity guard, LivingEntity target) {
		int faction = guard instanceof Worker w ? w.factionId() : -1;
		if (target instanceof ServerPlayer player) {
			if (faction >= 0 && (Theft.wanted(player, faction) || Theft.outlaw(player, faction)) || Bounties.wanted(player.level().getServer(), player)) {
				return Foe.CRIME;
			}
			return Foe.HOSTILE;
		}
		if (target instanceof Player) {
			return Foe.HOSTILE;
		}
		if (Crooks.isCriminal(target) || Crooks.isCrook(target) || Arrests.held(target) || Looters.is(target)) {
			return Foe.CRIME;
		}
		if (target instanceof Raider || Outlaws.is(target) || Tribes.of(target) != null) {
			return Foe.RAIDER;
		}
		if (target instanceof Worker other && faction >= 0 && other.factionId() >= 0 && other.factionId() != faction && Bloodshed.fighter(target)) {
			return Foe.WAR;
		}
		if (target instanceof AbstractVillager || Bloodshed.person(target)) {
			return Foe.BRAWL;
		}
		if (target.is(EntityTypeTags.UNDEAD)) {
			return Foe.UNDEAD;
		}
		if (target instanceof Creeper) {
			return Foe.CREEPER;
		}
		if (target instanceof Spider || target instanceof Silverfish || target instanceof Endermite || target.is(EntityTypeTags.ARTHROPOD) && target instanceof Enemy) {
			return Foe.CRAWLER;
		}
		if (target instanceof Enemy) {
			return target.getBbWidth() >= BIG_WIDTH || target.getMaxHealth() >= BIG_HEALTH ? Foe.BRUTE : Foe.MONSTER;
		}
		if (target instanceof Mob m && (Bloodshed.feared(m) || Bloodshed.attacker(m) || m.getMaxHealth() >= BIG_HEALTH / 2.0F)) {
			return Foe.BEAST;
		}
		return Foe.ANIMAL;
	}

	/** What a camp's people make of {@code target}. */
	static Rough rough(LivingEntity target) {
		if (target instanceof Player || target instanceof AbstractVillager || Bloodshed.person(target) || target instanceof Raider) {
			return Rough.PERSON;
		}
		return target instanceof Enemy ? Rough.MONSTER : Rough.BEAST;
	}

	/** {@link #MANY} or more of {@code target}'s kind are about it. */
	private static boolean many(ServerLevel level, LivingEntity guard, LivingEntity target, Foe foe) {
		List<LivingEntity> near = level.getEntitiesOfClass(LivingEntity.class, target.getBoundingBox().inflate(MANY_RANGE),
			e -> e != guard && e.isAlive() && !(e instanceof Player) && e.getClass() == target.getClass());
		if (near.size() >= MANY) {
			return true;
		}
		int n = 0;
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, target.getBoundingBox().inflate(MANY_RANGE), e -> e != guard && e.isAlive())) {
			if (foe(guard, e) == foe && ++n >= MANY) {
				return true;
			}
		}
		return false;
	}

	/** One of {@code base}'s lines, passing over those said lately while others are fresh. */
	static String pick(String base, long now, RandomSource random) {
		int start = random.nextInt(LINES);
		String stalest = base + "." + start;
		long stalestAt = Long.MAX_VALUE;
		for (int i = 0; i < LINES; i++) {
			String key = base + "." + (start + i) % LINES;
			Long at = SAID.get(key);
			if (at == null || now - at >= RECENT_TICKS) {
				SAID.put(key, now);
				return key;
			}
			if (at < stalestAt) {
				stalestAt = at;
				stalest = key;
			}
		}
		SAID.put(stalest, now);
		return stalest;
	}

	// ---- disasters --------------------------------------------------------------------------------------------------------

	/**
	 * (From {@link DisasterRelief#struck} and a tornado's touchdown) A disaster of {@code kind} has struck round (x, z),
	 * {@code radius} across: the guards about (a few, where someone can hear) shout the townsfolk to safety.
	 */
	public static void disaster(ServerLevel level, double x, double z, double radius, String kind) {
		String group = switch (kind) {
			case "quake" -> "quake";
			case "flood" -> "flood";
			case "tsunami" -> "wave";
			case "sandstorm", "blizzard", "hail", "hurricane" -> "storm";
			case "tornado" -> "tornado";
			case "volcano", "meteor", "ash" -> "fire_sky";
			case "avalanche", "landslide" -> "slide";
			// (wildfires have the fire alarm, FireAlarm; a sign is no danger to run from)
			default -> null;
		};
		if (group == null) {
			return;
		}
		double reach = Math.min(radius + DISASTER_MARGIN, DISASTER_BOX_MOST);
		AABB box = new AABB(x - reach, level.getMinY(), z - reach, x + reach, level.getMaxY(), z + reach);
		List<Worker> guards = Workers.near(level, box, w -> w.mob().isAlive() && !w.mob().isBaby() && Downed.isGuard(w.mob()) && !outlawVoice(w.mob())
			&& level.getNearestPlayer(w.mob(), DISASTER_HEARD) != null);
		if (guards.isEmpty()) {
			return;
		}
		java.util.Collections.shuffle(guards, new java.util.Random(level.getRandom().nextLong()));
		long now = level.getGameTime();
		for (int i = 0; i < Math.min(DISASTER_CRIERS, guards.size()); i++) {
			PathfinderMob guard = guards.get(i).mob();
			LAST_CRY.put(guard, now);
			Speech.say(level, guard, Component.translatable(pick("guardcry.jaymoskingdoms.disaster." + group, now, level.getRandom())), Speech.GOLD, SHOUT);
		}
	}
}
