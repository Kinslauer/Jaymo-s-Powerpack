package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.breeze.Breeze;
import net.minecraft.world.entity.monster.spider.CaveSpider;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.218) <b>Gifted folk</b>: villagers who awaken to a power (Jaymo's Powers holds it and fights with it, its
 * {@code core/gifted}; here is who awakens, and to what). Andy: "villagers should be able to acquire and use powers as
 * well! This is the only way that encountering a stormborn purist king makes sense."
 * <ul>
 * <li><b>Coming of age</b> ({@link #cameOfAge}): each MCA villager is weighed once, when they grow up (or the first time
 * they're seen grown): about one in {@link #AWAKEN_ODDS} awakens (Andy: "rarer, like 1 in 80"), a child of a gifted parent
 * {@link #PARENT_FACTOR} times as often. The power leans on their blood ({@link #lean}) and on a gifted parent's power.</li>
 * <li><b>Callings</b> ({@link #register}): what calls a power to a player calls it to a villager too, where one can meet
 * it: killing a Breeze (Wind), living through a cave spider's bite (Web), a long fall (Gravity), the cold's bite (Cryo),
 * killing a monster bare-handed (Z Fighter), with a blade (Arsenal), or living through burning (Smoke).</li>
 * <li><b>How they fight</b>: their temperament ({@link #style}): the hotheaded and the brave go straight in, the timid and
 * the gloomy get away first.</li>
 * <li><b>The Stormborn line</b>: no one is born Stormborn any more but to a Stormborn parent ({@link VillagerHeritage}); a
 * gifted Wind villager whose power the storm takes (corrupted) wakes to it ({@link #step}), and their children carry it.</li>
 * <li><b>Rulers</b>: a gifted villager is likelier to be crowned ({@link #rises}, {@link #RISE_CHANCE}), and a gifted
 * ruler unleashes their power's ultimate when their throne room is attacked ({@link #atCourt}).</li>
 * <li>(0.7.227) <b>Masters</b>: about one gifted in {@link #MASTER_ODDS} awakens as a master of it (Andy: "maybe 1 in 80 is
 * an extremely gifted wielder, achieving great mastery of the power using it in their own way"; Powers makes them so).</li>
 * <li>(0.7.227) <b>Hidden gifts</b> ({@link #open}): most keep it to a minimum and hide it from their family and friends
 * ({@link #close}); by their temperament some don't care who sees ("who is going to stop them?"), masters more often.</li>
 * <li>(0.7.227) Resonance's calling: living through a Warden's sonic boom.</li>
 * </ul>
 * Nothing without Jaymo's Powers (no one awakens).
 */
public final class GiftedFolk {
	/** One in this many villagers awakens when they come of age; a gifted parent's child this many times as often. */
	static final int AWAKEN_ODDS = 80;
	static final float PARENT_FACTOR = 3.0F;
	/** A gifted parent's power is this many times as likely for their child (over the blood's lean). */
	static final float PARENT_LEAN = 6.0F;
	/** A power the blood leans to is this many times as likely. */
	static final float BLOOD_LEAN = 4.0F;
	/** Tagged once weighed at coming of age (so it's only ever rolled once). */
	static final String ROLLED = "jaymoskingdoms_gift_rolled";
	/** The callings: the chance each wakes a power (Breeze: always). */
	static final float WEB_CHANCE = 0.25F, GRAVITY_CHANCE = 0.33F;
	/** Callings that come again and again (each kill, each hurt of the cold or the fire), so their chances are small: a
	 * monster killed bare-handed (Z Fighter), with a weapon (Arsenal; guards kill a lot), freezing (Cryo), burning (Smoke). */
	static final float FIST_CHANCE = 0.05F, BLADE_CHANCE = 0.004F, CRYO_CHANCE = 0.05F, SMOKE_CHANCE = 0.005F;
	/** A fall that hurts this much, lived through, is Gravity's calling. */
	static final float GRAVITY_FALL_HURT = 8.0F;
	/** Among the townsfolk heirs to a throne, a gifted one is crowned this often (if there's one). */
	static final float RISE_CHANCE = 0.6F;
	/** The throne room: within this far of the throne. */
	static final double COURT_RANGE = 16.0;
	/** One gifted in this many is a master of it. */
	static final int MASTER_ODDS = 80;
	/** Living through a Warden's sonic boom wakes Resonance this often. */
	static final float RESONANCE_CHANCE = 0.5F;
	/** The chance a gifted villager doesn't care who sees, by temperament: the brash, the bold, and the rest; a master's is this much more. */
	static final float OPEN_BRASH = 0.6F, OPEN_BOLD = 0.3F, OPEN_REST = 0.08F, OPEN_MASTER = 0.25F;
	/** A player this many hearts warm with them is a friend (hidden from); a villager who thinks this well of them too. */
	static final int FRIEND_HEARTS = 40, FRIEND_REGARD = 40;
	/** Tagged once a gift's ways are weighed (master, open), for the gifted of a world from before them. */
	static final String WAYS_ROLLED = "jaymoskingdoms_gift_ways";

	private GiftedFolk() {
	}

	static void register() {
		ServerLifecycleEvents.SERVER_STARTING.register(server -> PowersCompat.giftCloseOnes(GiftedFolk::close));
		ServerLivingEntityEvents.AFTER_DEATH.register(GiftedFolk::killed);
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
			if (taken > 0.0F && entity.isAlive()) {
				survived(entity, source, taken);
				atCourt(entity, source);
			}
		});
	}

	// ---- who awakens ------------------------------------------------------------------------------------------------

	/**
	 * {@code mob} has come of age (from {@link CourtWizards#age}), or is grown and was never weighed (from {@link #step}: the
	 * villagers of a world from before): weighed once for a power.
	 */
	static void cameOfAge(ServerLevel level, PathfinderMob mob) {
		if (mob.entityTags().contains(ROLLED) || !McaCompat.isMca(mob) || PowersCompat.gift(mob) != null) {
			return;
		}
		mob.addTag(ROLLED);
		String parentPower = parentGift(level, mob);
		float odds = 1.0F / AWAKEN_ODDS * (parentPower != null ? PARENT_FACTOR : 1.0F);
		if (level.getRandom().nextFloat() < odds) {
			awaken(level, mob, pick(mob, parentPower, level.getRandom()));
		}
	}

	/** A gifted parent's power (the first one found loaded), or null. */
	private static @Nullable String parentGift(ServerLevel level, PathfinderMob mob) {
		for (UUID parent : McaCompat.parents(mob)) {
			Entity e = level.getEntity(parent);
			String power = e == null ? null : PowersCompat.gift(e);
			if (power != null) {
				return power;
			}
		}
		return null;
	}

	/** Which power for {@code mob}: any can be had; their blood leans them, and a gifted parent's power more. */
	static String pick(PathfinderMob mob, @Nullable String parentPower, RandomSource random) {
		List<String> powers = PowersCompat.giftable();
		if (powers.isEmpty()) {
			return "wind";
		}
		String leaned = lean(VillagerHeritage.of(mob));
		float[] w = new float[powers.size()];
		float total = 0.0F;
		for (int i = 0; i < w.length; i++) {
			String p = powers.get(i);
			w[i] = p.equals(parentPower) ? PARENT_LEAN : p.equals(leaned) ? BLOOD_LEAN : 1.0F;
			total += w[i];
		}
		float roll = random.nextFloat() * total;
		for (int i = 0; i < w.length; i++) {
			roll -= w[i];
			if (roll < 0.0F) {
				return powers.get(i);
			}
		}
		return powers.getFirst();
	}

	/** The power a blood leans to, or null. */
	static @Nullable String lean(Heritage.@Nullable Kind kind) {
		if (kind == null) {
			return null;
		}
		return switch (kind) {
			case FROSTBORN -> "cryo";
			case EMBERBORN -> "smoke";
			case TIDEBORN, DEEPBORN -> "darktide";
			case STONEBORN -> "gravity";
			case SYLVAN, VERDANT -> "web";
			case WARRIORBORN -> "zfighter";
			case STORMBORN -> "wind";
			case HEARTLANDER, EARTHLING -> "arsenal";
			default -> null;
		};
	}

	/** How {@code mob} fights with it, by their temperament: {@link PowersCompat#BOLD}, steady, or wary. */
	static int style(Entity mob) {
		Temperaments.Temperament t = Temperaments.of(mob);
		if (t == null) {
			return PowersCompat.STEADY;
		}
		return switch (t) {
			case HOTHEADED, BRAVE, PROUD, SPITEFUL -> PowersCompat.BOLD;
			case TIMID, GLOOMY, SUSPICIOUS, KINDHEARTED -> PowersCompat.WARY;
			default -> PowersCompat.STEADY;
		};
	}

	/**
	 * Whether {@code mob} won't hide their gift, by their temperament: the hotheaded, the proud, the spiteful and the greedy
	 * often don't care who sees; the brave and the cheerful now and then; the rest seldom. A {@code master} more often.
	 */
	static boolean open(Entity mob, boolean master, RandomSource random) {
		Temperaments.Temperament t = Temperaments.of(mob);
		float chance = t == null ? OPEN_REST : switch (t) {
			case HOTHEADED, PROUD, SPITEFUL, GREEDY -> OPEN_BRASH;
			case BRAVE, CHEERFUL -> OPEN_BOLD;
			default -> OPEN_REST;
		};
		return random.nextFloat() < chance + (master ? OPEN_MASTER : 0.0F);
	}

	/**
	 * Whether {@code other} is close to {@code gifted} (Powers asks: a hidden gift isn't used in front of them): their family
	 * or partner, a player who's family or a friend ({@link #FRIEND_HEARTS}), or a villager friend ({@link #FRIEND_REGARD}).
	 * Without MCA, any villager or player.
	 */
	static boolean close(Entity gifted, Entity other) {
		if (!McaCompat.LOADED) {
			return other instanceof Player || other instanceof AbstractVillager;
		}
		if (!(gifted instanceof Villager v)) {
			return false;
		}
		if (other instanceof ServerPlayer player) {
			return McaCompat.family(v, player) || McaCompat.heartsOf(v, player) >= FRIEND_HEARTS;
		}
		return other instanceof Villager o
			&& (McaCompat.kin(v, o) != McaCompat.STRANGERS || Neighbours.regard(v, o) >= FRIEND_REGARD);
	}

	/** {@code mob} awakens to {@code power}: the moment of it, seen by those round them. */
	static boolean awaken(ServerLevel level, PathfinderMob mob, String power) {
		boolean master = level.getRandom().nextInt(MASTER_ODDS) == 0;
		if (!PowersCompat.give(mob, power, style(mob), master, open(mob, master, level.getRandom()))) {
			return false;
		}
		mob.addTag(ROLLED);
		mob.addTag(WAYS_ROLLED);
		level.sendParticles(ParticleTypes.END_ROD, mob.getX(), mob.getY(0.6), mob.getZ(), 24, 0.3, 0.6, 0.3, 0.08);
		level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.NEUTRAL, 1.0F, 0.8F);
		JaymosKingdoms.LOGGER.info("{} awakened to {}", mob.getName().getString(), power);
		return true;
	}

	// ---- callings ---------------------------------------------------------------------------------------------------

	/** A grown MCA villager with no power yet, who a calling can reach. */
	private static boolean callable(@Nullable Entity e) {
		return e instanceof PathfinderMob mob && mob.isAlive() && !mob.isBaby() && McaCompat.isMca(mob) && PowersCompat.gift(mob) == null;
	}

	private static void call(PathfinderMob mob, String power, float chance) {
		if (mob.level() instanceof ServerLevel level && level.getRandom().nextFloat() < chance) {
			awaken(level, mob, power);
		}
	}

	private static void killed(LivingEntity dead, DamageSource source) {
		Entity by = source.getEntity();
		if (!callable(by) || source.getDirectEntity() != by) {
			return;
		}
		PathfinderMob mob = (PathfinderMob)by;
		if (dead instanceof Breeze) {
			call(mob, "wind", 1.0F);
		} else if (dead instanceof Enemy) {
			if (mob.getMainHandItem().isEmpty()) {
				call(mob, "zfighter", FIST_CHANCE);
			} else {
				call(mob, "arsenal", BLADE_CHANCE);
			}
		}
	}

	private static void survived(LivingEntity entity, DamageSource source, float taken) {
		if (!callable(entity)) {
			return;
		}
		PathfinderMob mob = (PathfinderMob)entity;
		if (source.getEntity() instanceof CaveSpider) {
			call(mob, "web", WEB_CHANCE);
		} else if (source.is(DamageTypes.FALL) && taken >= GRAVITY_FALL_HURT) {
			call(mob, "gravity", GRAVITY_CHANCE);
		} else if (source.is(DamageTypes.FREEZE)) {
			call(mob, "cryo", CRYO_CHANCE);
		} else if (source.is(DamageTypeTags.IS_FIRE)) {
			call(mob, "smoke", SMOKE_CHANCE);
		} else if (source.is(DamageTypes.SONIC_BOOM)) {
			call(mob, "resonance", RESONANCE_CHANCE);
		}
	}

	// ---- the storm, the throne --------------------------------------------------------------------------------------

	/**
	 * Now and then, for each MCA villager ({@link Workers#step}): a grown one never weighed is weighed now, and a Wind the
	 * storm has taken wakes the Stormborn in them.
	 */
	static void step(ServerLevel level, PathfinderMob mob) {
		if (!mob.isBaby() && !mob.entityTags().contains(ROLLED)) {
			cameOfAge(level, mob);
		}
		if (!mob.entityTags().contains(WAYS_ROLLED) && PowersCompat.gift(mob) != null) {
			boolean master = level.getRandom().nextInt(MASTER_ODDS) == 0;
			if (PowersCompat.giftWays(mob, master, open(mob, master, level.getRandom()))) {
				mob.addTag(WAYS_ROLLED);
			}
		}
		if ("wind".equals(PowersCompat.gift(mob)) && PowersCompat.giftCorrupted(mob) && VillagerHeritage.of(mob) != Heritage.Kind.STORMBORN) {
			VillagerHeritage.born(mob, Heritage.Kind.STORMBORN, null);
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, mob.getX(), mob.getY(0.6), mob.getZ(), 30, 0.4, 0.7, 0.4, 0.3);
			level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.WEATHER, 1.5F, 1.2F);
		}
	}

	/** Of {@code candidates} for a throne, a gifted one now and then ({@link #RISE_CHANCE}); null to choose as before. */
	static <T extends Entity> @Nullable T rises(List<T> candidates, RandomSource random) {
		List<T> gifted = new ArrayList<>();
		for (T c : candidates) {
			if (PowersCompat.gift(c) != null) {
				gifted.add(c);
			}
		}
		return gifted.isEmpty() || random.nextFloat() >= RISE_CHANCE ? null : gifted.get(random.nextInt(gifted.size()));
	}

	/**
	 * Someone in a throne room was hurt (its ruler, or anyone of the realm near the throne): its gifted ruler, there too, unleashes
	 * their power's ultimate on whoever did it (Powers keeps it to once in a long while; Gravity's meteor only as a last
	 * resort).
	 */
	private static void atCourt(LivingEntity hurt, DamageSource source) {
		if (!(hurt instanceof Worker) || !(source.getEntity() instanceof LivingEntity foe) || foe == hurt || !(hurt.level() instanceof ServerLevel level)) {
			return;
		}
		PathfinderMob ruler = rulerAt(level, hurt);
		if (ruler != null && ruler != foe) {
			PowersCompat.unleash(ruler, foe);
		}
	}

	/** The gifted villager ruler whose throne room {@code hurt} is in (or who {@code hurt} is, at court), or null. */
	private static @Nullable PathfinderMob rulerAt(ServerLevel level, LivingEntity hurt) {
		for (Faction f : Factions.all(level.getServer())) {
			if (f.fallen() || f.leader().isEmpty() || !f.type().isRealm()) {
				continue;
			}
			Castle.Keep keep = Factions.keep(level.getServer(), f.id());
			if (keep == null || !hurt.blockPosition().closerThan(keep.throne(), COURT_RANGE)) {
				continue;
			}
			if (level.getEntity(f.leader().get()) instanceof PathfinderMob ruler && PowersCompat.gift(ruler) != null
				&& ruler.blockPosition().closerThan(keep.throne(), COURT_RANGE)) {
				return ruler;
			}
		}
		return null;
	}
}
