package com.jaymoskingdoms;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.villager.Villager;
import org.jspecify.annotations.Nullable;

/**
 * Costumed gifted folk. Andy (2026-11-11): "You can find some npc costumed power users in the world, either wreaking havoc
 * or trying to help their people. Some power users dont care for the theatrics at all as usual and will use their powers
 * outright, mostly to help their daily life" (that last is Powers' {@code GiftKit#everyday}). Lore.md, "The costumed
 * gifted, and the everyday ones".
 * <ul>
 * <li><b>Who</b> ({@link #weigh}): a grown gifted villager who hides their gift (the open ones don't care for theatrics),
 * and isn't a ruler, is weighed once: by their temperament a {@link #VIGILANTE} (the brave, kindhearted, dutiful and
 * devout), a {@link #VILLAIN} (the spiteful, greedy and hotheaded), now and then either for the rest. Powers makes them a
 * costume of their own for their power and their side.</li>
 * <li><b>When</b>: it goes on after dark and comes off at dawn, only while no player is near enough to see who is under
 * it ({@link #UNSEEN}).</li>
 * <li><b>Vigilantes</b>, suited, go after a monster near them ({@link #PATROL}), and the gift does the rest.</li>
 * <li><b>Villains</b>, suited, now and then ({@link #HAVOC_CHANCE}) rough up a passer-by for a little while
 * ({@link #HAVOC_TICKS}): a player near, or a grown villager who isn't one of their own.</li>
 * <li><b>Rivals</b> (Andy: "vigilantes will fight villians as well, meaning you can stumble upon rivalries and even get
 * involved"): a suited vigilante goes after a suited villain before any monster; from then on each knows the other
 * ({@link #RIVAL}), the vigilante goes after them from further off, and the villain comes looking for them. A player who
 * joins in hears from the other side what they think of the help.</li>
 * <li><b>The folk talk of them</b> ("the villagers will talk about the ones they have seen"): those who see a suited one,
 * or two rivals at it, remember the names ({@link #SAW}), and by day tell a player near about it.</li>
 * <li><b>Nobody likes either</b> ({@link #grumble}): folk near a suited one grumble at a vigilante and cry out at a
 * villain, in speech bubbles.</li>
 * </ul>
 * Nothing without Jaymo's Powers.
 */
final class CostumedFolk {
	/** Tagged once weighed; tagged with their side. */
	static final String ROLLED = "jaymoskingdoms_costume_rolled", VIGILANTE = "jaymoskingdoms_vigilante", VILLAIN = "jaymoskingdoms_villain";
	/** The chance a hidden gift takes a costume: a temperament that leans to a side, and any other (either side, evenly). */
	static final float LEANING_CHANCE = 0.35F, OTHER_CHANCE = 0.08F;
	/** No player within this far (blocks) sees them put it on or take it off. */
	static final double UNSEEN = 16.0;
	/** A vigilante goes after a monster within this far (blocks). */
	static final double PATROL = 16.0;
	/** A vigilante calls out this often as they go after one. */
	static final float CALL_CHANCE = 0.3F;
	/** A villain, each look while suited (about once a second), starts trouble this often, with someone this near. */
	static final float HAVOC_CHANCE = 0.003F;
	static final double HAVOC_RANGE = 20.0;
	/** How long a villain's trouble lasts (ticks). */
	static final int HAVOC_TICKS = 240;
	/** Each look while suited, this often someone within this far grumbles at them; heard this far. */
	static final float GRUMBLE_CHANCE = 0.02F;
	static final double GRUMBLE_RANGE = 8.0, SAY_RANGE = 12.0;
	/** The lines in the lang part ({@code costumed-folk}). */
	static final int HERO_LINES = 5, VILLAIN_LINES = 4, HERO_CALL_LINES = 3, VILLAIN_CALL_LINES = 3, RIVAL_CALL_LINES = 3, THANKS_LINES = 3,
		GOSSIP_LINES = 3, GOSSIP_RIVAL_LINES = 3;
	/** A rival is gone after from this far (blocks); a villain about with one goes looking for a fight this often a second. */
	static final double RIVAL_RANGE = 32.0;
	static final float RIVAL_HAVOC_CHANCE = 0.02F;
	/** Tagged on each of two who have fought: the other's UUID. */
	static final String RIVAL = "jaymoskingdoms_rival:";
	/** A rival's word to a player who joined in, at most this often (ticks). */
	static final int THANKS_GAP = 600;
	/** What the folk remember seeing (tags): a vigilante, a villain, two rivals at it ("hero|villain"); the last this many. */
	static final String SAW = "jaymoskingdoms_saw_", SAW_HERO = SAW + "hero:", SAW_VILLAIN = SAW + "villain:", SAW_RIVALS = SAW + "rivals:";
	static final int MEMORIES = 4;
	/** Each look while suited, this often those who see them take note. */
	static final float REMEMBER_CHANCE = 0.1F;
	/** By day, one who saw them talks of it this often a second, with a player within this far. */
	static final float GOSSIP_CHANCE = 0.004F;
	static final double GOSSIP_RANGE = 10.0;

	/** A villain's trouble: on whom, until when (game time). */
	private record Havoc(LivingEntity target, long until) {
	}

	private static final Map<PathfinderMob, Havoc> HAVOC = new WeakHashMap<>();
	/** When each suited one last had a word for a player who joined their fight (game time). */
	private static final Map<PathfinderMob, Long> THANKED = new WeakHashMap<>();

	private CostumedFolk() {
	}

	/** About once a second, for each gifted MCA villager ({@link GiftedFolk#step}). */
	static void step(ServerLevel level, PathfinderMob mob) {
		if (mob.isBaby()) {
			return;
		}
		if (PowersCompat.gift(mob) == null) {
			gossip(level, mob);
			return;
		}
		if (!mob.entityTags().contains(ROLLED)) {
			weigh(level, mob);
		}
		boolean villain = mob.entityTags().contains(VILLAIN);
		if (!villain && !mob.entityTags().contains(VIGILANTE)) {
			return;
		}
		boolean night = level.isDarkOutside();
		boolean suited = PowersCompat.costumeSuited(mob);
		if (night != suited && !seen(level, mob)) {
			suited = PowersCompat.costumeSuit(mob, night) ? night : suited;
		}
		Havoc havoc = HAVOC.get(mob);
		if (havoc != null && (!suited || havoc.until() < level.getGameTime() || !havoc.target().isAlive())) {
			HAVOC.remove(mob);
			if (mob.getTarget() == havoc.target()) {
				mob.setTarget(null);
			}
		}
		if (!suited) {
			return;
		}
		if (villain) {
			trouble(level, mob);
		} else {
			patrol(level, mob);
		}
		grumble(level, mob, villain);
	}

	/** Weighed once: whether they take a costume, and for which side. */
	static void weigh(ServerLevel level, PathfinderMob mob) {
		mob.addTag(ROLLED);
		if (PowersCompat.giftOpen(mob) || ruler(level, mob)) {
			return;
		}
		Temperaments.Temperament t = Temperaments.of(mob);
		Boolean side = t == null ? null : switch (t) {
			case BRAVE, KINDHEARTED, DUTIFUL, DEVOUT -> Boolean.FALSE;
			case SPITEFUL, GREEDY, HOTHEADED -> Boolean.TRUE;
			default -> null;
		};
		float chance = side != null ? LEANING_CHANCE : OTHER_CHANCE;
		if (level.getRandom().nextFloat() >= chance) {
			return;
		}
		boolean villain = side != null ? side : level.getRandom().nextBoolean();
		if (PowersCompat.costumeDress(mob, villain)) {
			mob.addTag(villain ? VILLAIN : VIGILANTE);
		}
	}

	/** Whether a player is near enough to see who puts the costume on or takes it off. */
	private static boolean seen(ServerLevel level, PathfinderMob mob) {
		for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class, mob.getBoundingBox().inflate(UNSEEN), p -> !p.isSpectator())) {
			if (p.hasLineOfSight(mob)) {
				return true;
			}
		}
		return false;
	}

	/** Whether {@code mob} rules a realm or a settlement (a ruler never hides behind a mask). */
	private static boolean ruler(ServerLevel level, PathfinderMob mob) {
		UUID id = mob.getUUID();
		for (Faction f : Factions.all(level.getServer())) {
			if (f.leader().isPresent() && f.leader().get().equals(id)) {
				return true;
			}
		}
		return false;
	}

	/** A vigilante: after the nearest monster, if they aren't fighting already. */
	private static void patrol(ServerLevel level, PathfinderMob mob) {
		LivingEntity target = mob.getTarget();
		if (target != null && target.isAlive()) {
			return;
		}
		PathfinderMob villain = foe(level, mob, VILLAIN);
		if (villain != null) {
			// a villain out: they come first, a rival before anyone (and the villain knows them from now on)
			rivals(mob, villain);
			mob.setTarget(villain);
			Speech.say(level, mob, Component.translatable("speech.jaymoskingdoms.costumed.hero_rival." + mob.getRandom().nextInt(RIVAL_CALL_LINES),
				name(villain)), Speech.GOLD, SAY_RANGE);
			return;
		}
		LivingEntity nearest = null;
		double best = Double.MAX_VALUE;
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, mob.getBoundingBox().inflate(PATROL),
			e -> e instanceof Enemy && e.isAlive() && !e.isSpectator())) {
			double d = e.distanceToSqr(mob);
			if (d < best && mob.hasLineOfSight(e)) {
				best = d;
				nearest = e;
			}
		}
		if (nearest != null) {
			mob.setTarget(nearest);
			if (mob.getRandom().nextFloat() < CALL_CHANCE) {
				Speech.say(level, mob, Component.translatable("speech.jaymoskingdoms.costumed.hero_call." + mob.getRandom().nextInt(HERO_CALL_LINES)),
					Speech.GOLD, SAY_RANGE);
			}
		}
	}

	/** A villain: now and then, someone to rough up for a while. */
	private static void trouble(ServerLevel level, PathfinderMob mob) {
		if (HAVOC.containsKey(mob)) {
			return;
		}
		PathfinderMob rival = foe(level, mob, VIGILANTE);
		if (rival != null && rival(mob, rival) && level.getRandom().nextFloat() < RIVAL_HAVOC_CHANCE) {
			// an old rival about: the villain goes looking for a fight
			HAVOC.put(mob, new Havoc(rival, level.getGameTime() + HAVOC_TICKS));
			mob.setTarget(rival);
			Speech.say(level, mob, Component.translatable("speech.jaymoskingdoms.costumed.villain_rival." + mob.getRandom().nextInt(RIVAL_CALL_LINES),
				name(rival)), Speech.RED, SAY_RANGE);
			return;
		}
		if (level.getRandom().nextFloat() >= HAVOC_CHANCE) {
			return;
		}
		LivingEntity target = victim(level, mob);
		if (target == null) {
			return;
		}
		HAVOC.put(mob, new Havoc(target, level.getGameTime() + HAVOC_TICKS));
		mob.setTarget(target);
		Speech.say(level, mob, Component.translatable("speech.jaymoskingdoms.costumed.villain_call." + mob.getRandom().nextInt(VILLAIN_CALL_LINES)),
			Speech.RED, SAY_RANGE);
	}

	/** Who a villain picks on: a player near (never family), else a grown villager who isn't one of their own. */
	private static @Nullable LivingEntity victim(ServerLevel level, PathfinderMob mob) {
		List<ServerPlayer> players = level.getEntitiesOfClass(ServerPlayer.class, mob.getBoundingBox().inflate(HAVOC_RANGE),
			p -> p.isAlive() && !p.isCreative() && !p.isSpectator() && !(mob instanceof Villager v && McaCompat.family(v, p)));
		if (!players.isEmpty()) {
			return players.get(level.getRandom().nextInt(players.size()));
		}
		List<PathfinderMob> folk = level.getEntitiesOfClass(PathfinderMob.class, mob.getBoundingBox().inflate(HAVOC_RANGE),
			e -> e != mob && e.isAlive() && !e.isBaby() && McaCompat.isMca(e) && !GiftedFolk.close(mob, e));
		return folk.isEmpty() ? null : folk.get(level.getRandom().nextInt(folk.size()));
	}

	/** Now and then someone near a suited one grumbles at a vigilante, or cries out at a villain. */
	private static void grumble(ServerLevel level, PathfinderMob mob, boolean villain) {
		boolean speak = level.getRandom().nextFloat() < GRUMBLE_CHANCE;
		boolean remember = level.getRandom().nextFloat() < REMEMBER_CHANCE;
		if (!speak && !remember) {
			return;
		}
		List<PathfinderMob> near = level.getEntitiesOfClass(PathfinderMob.class, mob.getBoundingBox().inflate(GRUMBLE_RANGE),
			e -> e != mob && e.isAlive() && !e.isBaby() && McaCompat.isMca(e) && PowersCompat.gift(e) == null && e.hasLineOfSight(mob));
		if (near.isEmpty()) {
			return;
		}
		if (remember) {
			// (now and then) those who see them remember it, and a fight with a rival too, to talk of by day
			String name = name(mob);
			String memory;
			if (mob.getTarget() instanceof PathfinderMob other && PowersCompat.costumeSuited(other)) {
				memory = villain ? SAW_RIVALS + name(other) + "|" + name : SAW_RIVALS + name + "|" + name(other);
			} else {
				memory = (villain ? SAW_VILLAIN : SAW_HERO) + name;
			}
			for (PathfinderMob e : near) {
				remember(e, memory);
			}
		}
		if (!speak) {
			return;
		}
		PathfinderMob speaker = near.get(level.getRandom().nextInt(near.size()));
		String key = villain ? "villain." + level.getRandom().nextInt(VILLAIN_LINES) : "vigilante." + level.getRandom().nextInt(HERO_LINES);
		Speech.say(level, speaker, Component.translatable("speech.jaymoskingdoms.costumed." + key), villain ? Speech.RED : Speech.WHITE, SAY_RANGE);
	}

	// ---- rivals ----------------------------------------------------------------------------------------------------

	/** Andy (2026-11-11): "vigilantes will fight villians as well, meaning you can stumble upon rivalries and even get involved." */
	static void register() {
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
			if (taken > 0.0F && entity instanceof PathfinderMob hurt && source.getEntity() instanceof ServerPlayer player
				&& hurt.level() instanceof ServerLevel level) {
				involved(level, hurt, player);
			}
		});
	}

	/**
	 * A player has hit a suited one in the middle of a fight with their rival: the rival lets it be known what they think of
	 * the help (a vigilante thanks them, grudgingly; a villain crows).
	 */
	private static void involved(ServerLevel level, PathfinderMob hurt, ServerPlayer player) {
		if (!PowersCompat.costumeSuited(hurt)) {
			return;
		}
		boolean villain = hurt.entityTags().contains(VILLAIN);
		long now = level.getGameTime();
		for (PathfinderMob other : level.getEntitiesOfClass(PathfinderMob.class, hurt.getBoundingBox().inflate(RIVAL_RANGE),
			e -> e != hurt && e.isAlive() && e.getTarget() == hurt && PowersCompat.costumeSuited(e))) {
			Long last = THANKED.get(other);
			if (last != null && now - last < THANKS_GAP) {
				continue;
			}
			THANKED.put(other, now);
			// the one hit was a villain: the vigilante fighting them thanks the player; the other way, the villain crows
			String key = villain ? "hero_thanks." + level.getRandom().nextInt(THANKS_LINES) : "villain_crows." + level.getRandom().nextInt(THANKS_LINES);
			Speech.sayTo(level, other, player, Component.translatable("speech.jaymoskingdoms.costumed." + key), villain ? Speech.GOLD : Speech.RED);
		}
	}

	/** The nearest suited one of {@code side} that {@code mob} can see: a rival from further off, any other close by. */
	private static @Nullable PathfinderMob foe(ServerLevel level, PathfinderMob mob, String side) {
		PathfinderMob nearest = null;
		double best = Double.MAX_VALUE;
		for (PathfinderMob e : level.getEntitiesOfClass(PathfinderMob.class, mob.getBoundingBox().inflate(RIVAL_RANGE),
			e -> e != mob && e.isAlive() && e.entityTags().contains(side) && PowersCompat.costumeSuited(e))) {
			double d = e.distanceToSqr(mob);
			boolean known = rival(mob, e);
			if ((known || d <= PATROL * PATROL) && mob.hasLineOfSight(e)) {
				// a rival counts as nearer than anyone else
				double rank = known ? d - RIVAL_RANGE * RIVAL_RANGE * 4 : d;
				if (rank < best) {
					best = rank;
					nearest = e;
				}
			}
		}
		return nearest;
	}

	/** Whether {@code a} and {@code b} have fought before. */
	static boolean rival(PathfinderMob a, PathfinderMob b) {
		return a.entityTags().contains(RIVAL + b.getStringUUID());
	}

	/** They've fought: each knows the other from now on. */
	private static void rivals(PathfinderMob a, PathfinderMob b) {
		a.addTag(RIVAL + b.getStringUUID());
		b.addTag(RIVAL + a.getStringUUID());
	}

	/** The costume's name, for the folk's talk. */
	private static String name(PathfinderMob mob) {
		String name = PowersCompat.costumeName(mob);
		return name == null || name.isEmpty() ? "the masked one" : name;
	}

	// ---- the folk's talk -------------------------------------------------------------------------------------------

	/** One who saw a suited one keeps it in mind (the last few), to talk of by day. */
	private static void remember(PathfinderMob folk, String memory) {
		java.util.Set<String> tags = folk.entityTags();
		if (tags.contains(memory)) {
			return;
		}
		List<String> held = new java.util.ArrayList<>();
		for (String t : tags) {
			if (t.startsWith(SAW)) {
				held.add(t);
			}
		}
		for (int i = 0; i + MEMORIES <= held.size(); i++) {
			folk.removeTag(held.get(i));
		}
		folk.addTag(memory);
	}

	/** By day, now and then, one who saw a suited one talks of it to a player near. */
	private static void gossip(ServerLevel level, PathfinderMob mob) {
		if (!level.isBrightOutside() || level.getRandom().nextFloat() >= GOSSIP_CHANCE || level.getNearestPlayer(mob, GOSSIP_RANGE) == null) {
			return;
		}
		List<String> held = new java.util.ArrayList<>();
		for (String t : mob.entityTags()) {
			if (t.startsWith(SAW)) {
				held.add(t);
			}
		}
		if (held.isEmpty()) {
			return;
		}
		String memory = held.get(level.getRandom().nextInt(held.size()));
		Component line;
		if (memory.startsWith(SAW_RIVALS)) {
			String[] two = memory.substring(SAW_RIVALS.length()).split("\\|", 2);
			if (two.length < 2) {
				mob.removeTag(memory);
				return;
			}
			line = Component.translatable("speech.jaymoskingdoms.costumed.gossip_rivals." + level.getRandom().nextInt(GOSSIP_RIVAL_LINES), two[0], two[1]);
		} else {
			boolean villain = memory.startsWith(SAW_VILLAIN);
			String name = memory.substring((villain ? SAW_VILLAIN : SAW_HERO).length());
			line = Component.translatable("speech.jaymoskingdoms.costumed.gossip_" + (villain ? "villain." : "vigilante.")
				+ level.getRandom().nextInt(GOSSIP_LINES), name);
		}
		Speech.say(level, mob, line, Speech.WHITE, SAY_RANGE);
	}
}
