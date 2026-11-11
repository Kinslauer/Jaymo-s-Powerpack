package com.jaymoskingdoms;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
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
	static final int HERO_LINES = 5, VILLAIN_LINES = 4, HERO_CALL_LINES = 3, VILLAIN_CALL_LINES = 3;

	/** A villain's trouble: on whom, until when (game time). */
	private record Havoc(LivingEntity target, long until) {
	}

	private static final Map<PathfinderMob, Havoc> HAVOC = new WeakHashMap<>();

	private CostumedFolk() {
	}

	/** About once a second, for each gifted MCA villager ({@link GiftedFolk#step}). */
	static void step(ServerLevel level, PathfinderMob mob) {
		if (mob.isBaby() || PowersCompat.gift(mob) == null) {
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
		if (HAVOC.containsKey(mob) || level.getRandom().nextFloat() >= HAVOC_CHANCE) {
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
		if (level.getRandom().nextFloat() >= GRUMBLE_CHANCE) {
			return;
		}
		List<PathfinderMob> near = level.getEntitiesOfClass(PathfinderMob.class, mob.getBoundingBox().inflate(GRUMBLE_RANGE),
			e -> e != mob && e.isAlive() && !e.isBaby() && McaCompat.isMca(e) && PowersCompat.gift(e) == null && e.hasLineOfSight(mob));
		if (near.isEmpty()) {
			return;
		}
		PathfinderMob speaker = near.get(level.getRandom().nextInt(near.size()));
		String key = villain ? "villain." + level.getRandom().nextInt(VILLAIN_LINES) : "vigilante." + level.getRandom().nextInt(HERO_LINES);
		Speech.say(level, speaker, Component.translatable("speech.jaymoskingdoms.costumed." + key), villain ? Speech.RED : Speech.WHITE, SAY_RANGE);
	}
}
