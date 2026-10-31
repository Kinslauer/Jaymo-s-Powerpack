package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Prediction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.449) Andy: "when villagers run up to you, you should be able to ask them why or whats going on. they should
 * also have a reason other than just introducing themselves. people wouldnt usually break routine for that unless
 * there was a good reason". A stranger who hurries over to you ({@link Handshakes}) does so only for a {@link Reason}:
 * you're hurt, there's a monster at your back, a storm's coming, they owe you thanks, they've heard what you did here,
 * you're of their own rare blood, you're a foreigner or in gear they've never seen, (now and then) they took you for
 * someone else, or there's word of a beast near. No reason, no one comes. They shout it as they set off, tell you on
 * arriving, and introduce themselves while they're at it.
 * <p>
 * "Ask what's going on" ({@link #ANSWER}) in MCA's menu, for a day after, has them tell you in their own words why they
 * came (by gesture, if you share no tongue). It also works on someone who came over with {@link Approaches}' business:
 * they say what more they had to say.
 */
public final class ComingOver {
	/** MCA's main-menu answer: why they came over. */
	public static final String ANSWER = "jaymoskingdoms_whats_up";

	/** How long (ticks) after coming over they'll still tell you why. */
	static final long REMEMBER_TICKS = 24000L;
	/** At or under this share of their health a player looks hurt enough to come running over. */
	static final float HURT_SHARE = 0.5F;
	/** How far from the player (blocks) a monster gets them warned of it, and how near it may be before it's a fight, not a warning. */
	static final double DANGER_RANGE = 22.0, DANGER_NEAR = 4.0;
	/** How long (ticks) a monster they point out glows for. */
	static final int POINT_GLOW_TICKS = 20 * 6;
	/** How keen each reason makes them, and how many lines each has to hail, tell and explain. */
	static final int HAIL_LINES = 2, COME_LINES = 3, WHY_LINES = 2;

	/** Why a stranger breaks off what they were doing to come over. */
	enum Reason {
		HURT("hurt", 7),
		DANGER("danger", 8),
		STORM("storm", 3),
		THANKS("thanks", 6),
		DEED("deed", 5),
		KIN("kin", 5),
		FOREIGN("foreign", 3),
		GEAR("gear", 2),
		BEAST("beast", 3),
		MISTAKEN("mistaken", 1),
		/** Approaches' business (a question, a quest, gossip...): what more they had to say. */
		APPROACH("approach", 0);

		final String key;
		final int weight;

		Reason(String key, int weight) {
			this.key = key;
			this.weight = weight;
		}
	}

	/**
	 * What {@code villager} came over to {@code player} about, at {@code at} (game time): the reason, the line they
	 * said on arrival (or the more they had, for {@link Reason#APPROACH}), and the arguments its lines take.
	 */
	record Visit(Reason reason, Component said, Object[] args, long at) {
	}

	/** A reason picked for coming over, with what its lines need (a monster, the item, a heritage...). */
	record Pick(Reason reason, Object[] args, @Nullable LivingEntity monster) {
	}

	/** "villager/player" → why they last came over. */
	private static final Map<String, Visit> VISITS = new HashMap<>();

	private ComingOver() {
	}

	static void register() {
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> VISITS.clear());
	}

	private static String key(LivingEntity who, ServerPlayer player) {
		return who.getUUID() + "/" + player.getUUID();
	}

	// ---- why they'd come ------------------------------------------------------------------------------------------

	/** The reason {@code v} would have to come over to {@code player} (a weighted draw over all they have), or null for none. */
	static @Nullable Pick reason(ServerLevel level, ServerPlayer player, Villager v, @Nullable Faction f, long today) {
		Temperaments.Temperament t = Temperaments.of(v);
		RandomSource random = level.getRandom();
		List<Pick> picks = new ArrayList<>();
		if (player.getHealth() <= player.getMaxHealth() * HURT_SHARE && t != Temperaments.Temperament.GREEDY && t != Temperaments.Temperament.PROUD
			&& t != Temperaments.Temperament.GLOOMY) {
			picks.add(new Pick(Reason.HURT, new Object[0], null));
		}
		if (t == null || t == Temperaments.Temperament.BRAVE || t == Temperaments.Temperament.DUTIFUL || t == Temperaments.Temperament.KINDHEARTED
			|| t == Temperaments.Temperament.DEVOUT || t == Temperaments.Temperament.CHEERFUL || t == Temperaments.Temperament.HOTHEADED) {
			Monster m = monster(level, player, v);
			if (m != null) {
				picks.add(new Pick(Reason.DANGER, new Object[] {m.getDisplayName(), Sightings.direction(player.blockPosition(), m.blockPosition())}, m));
			}
		}
		if (level.isRaining() && level.canSeeSky(player.blockPosition()) && t != Temperaments.Temperament.SPITEFUL) {
			picks.add(new Pick(Reason.STORM, new Object[0], null));
		}
		if (Recollections.leaning(v, player) >= Recollections.WARM && Recollections.hasRemark(v, player)) {
			picks.add(new Pick(Reason.THANKS, new Object[0], null));
		}
		if (f != null && Deeds.known(player, f.id(), today) != null) {
			picks.add(new Pick(Reason.DEED, new Object[0], null));
		}
		Heritage.Kind mine = Heritage.of(player);
		if (mine != null && !VillagerHeritage.common(mine) && mine == VillagerHeritage.of(v)) {
			picks.add(new Pick(Reason.KIN, new Object[] {Component.translatable("heritage.jaymoskingdoms." + mine.key())}, null));
		}
		boolean curious = t == null || t == Temperaments.Temperament.CHEERFUL || t == Temperaments.Temperament.BRAVE || t == Temperaments.Temperament.GREEDY
			|| t == Temperaments.Temperament.PROUD || t == Temperaments.Temperament.HOTHEADED || t == Temperaments.Temperament.KINDHEARTED;
		if (curious && !Languages.understands(player, v)) {
			picks.add(new Pick(Reason.FOREIGN, new Object[0], null));
		}
		ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
		if (curious && !chest.isEmpty() && !chest.is(Items.LEATHER_CHESTPLATE) && !Identities.masked(player)) {
			picks.add(new Pick(Reason.GEAR, new Object[] {chest.getHoverName()}, null));
		}
		if (Sightings.near(v.blockPosition(), Sightings.GOSSIP_RANGE) != null) {
			picks.add(new Pick(Reason.BEAST, new Object[0], null));
		}
		if (t == Temperaments.Temperament.CHEERFUL || t == Temperaments.Temperament.GLOOMY || t == Temperaments.Temperament.KINDHEARTED) {
			picks.add(new Pick(Reason.MISTAKEN, new Object[0], null));
		}
		int total = 0;
		Pick best = null;
		for (Pick p : picks) {
			total += p.reason().weight;
			if (random.nextInt(total) < p.reason().weight) {
				best = p;
			}
		}
		return best;
	}

	/** How much keener {@code pick} makes them to come over (the urgent reasons outweigh the idle ones). */
	static int weight(Pick pick) {
		return pick.reason().weight;
	}

	/** The nearest monster near {@code player} that's not yet on them, and that {@code v} can see; or null. */
	private static @Nullable Monster monster(ServerLevel level, ServerPlayer player, Villager v) {
		Monster best = null;
		for (Monster m : level.getEntitiesOfClass(Monster.class, player.getBoundingBox().inflate(DANGER_RANGE),
			e -> e.isAlive() && !e.isInvisible() && e.distanceTo(player) > DANGER_NEAR && e.getTarget() != v)) {
			if ((best == null || m.distanceToSqr(player) < best.distanceToSqr(player)) && v.hasLineOfSight(m)) {
				best = m;
			}
		}
		return best;
	}

	// ---- coming over ----------------------------------------------------------------------------------------------

	/** What {@code v} shouts as they set off toward {@code player}. */
	static Component hail(Villager v, Pick pick) {
		return Component.translatable("come.jaymoskingdoms.hail." + pick.reason().key + "." + v.getRandom().nextInt(HAIL_LINES), pick.args());
	}

	/**
	 * {@code v} has reached {@code player}: they say why they came (aloud), do what it calls for (bread for the hurt,
	 * the monster pointed out), and it's remembered for "what's going on?".
	 */
	static void arrived(ServerPlayer player, Villager v, Pick pick) {
		ServerLevel level = player.level();
		RandomSource random = v.getRandom();
		Component said = null;
		Faction f = Approaches.home(level, v);
		if (pick.reason() == Reason.THANKS) {
			said = Recollections.remark(v, player, random);
		} else if (pick.reason() == Reason.DEED && f != null) {
			said = Deeds.greeting(level, player, f);
		} else if (pick.reason() == Reason.BEAST) {
			said = Sightings.gossip(level, v);
		}
		if (said == null) {
			said = Component.translatable("come.jaymoskingdoms." + pick.reason().key + "." + random.nextInt(COME_LINES), pick.args());
		}
		Speech.say(level, v, said, pick.reason() == Reason.DANGER || pick.reason() == Reason.HURT ? Speech.YELLOW : Speech.WHITE, 12.0);
		if (pick.reason() == Reason.HURT) {
			ItemStack bread = new ItemStack(Items.BREAD);
			if (!player.getInventory().add(bread)) {
				player.drop(bread, false, Prediction.SERVER_ONLY);
			}
		}
		LivingEntity m = pick.monster();
		if (pick.reason() == Reason.DANGER && m != null && m.isAlive()) {
			m.addEffect(new MobEffectInstance(MobEffects.GLOWING, POINT_GLOW_TICKS, 0, true, false));
			v.getLookControl().setLookAt(m, 30.0F, 30.0F);
		}
		if (!Languages.understands(player, v)) {
			// (what they mean, from their hands)
			player.sendSystemMessage(Component.translatable("come.jaymoskingdoms.gist." + pick.reason().key, pick.args())
				.withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
		}
		VISITS.put(key(v, player), new Visit(pick.reason(), said, pick.args(), level.getGameTime()));
	}

	/** (From {@link Approaches}) {@code mob} came over to {@code player} on their own business; {@code more} is what more they'd say. */
	static void approached(Mob mob, ServerPlayer player, Component more) {
		VISITS.put(key(mob, player), new Visit(Reason.APPROACH, more, new Object[0], player.level().getGameTime()));
	}

	// ---- "what's going on?" ---------------------------------------------------------------------------------------

	private static @Nullable Visit visit(ServerPlayer player, LivingEntity who) {
		Visit v = VISITS.get(key(who, player));
		if (v != null && player.level().getGameTime() - v.at() > REMEMBER_TICKS) {
			VISITS.remove(key(who, player));
			return null;
		}
		return v;
	}

	/** "Ask what's going on" shows on {@code who} only when they came over to {@code player} lately. */
	public static boolean canAsk(ServerPlayer player, LivingEntity who) {
		return visit(player, who) != null;
	}

	/** {@code player} asked {@code who} what's going on: why they came over, in their words (or, in a tongue you don't share, their hands). */
	public static Component ask(ServerPlayer player, LivingEntity who) {
		Visit v = visit(player, who);
		if (v == null) {
			return Component.translatable("come.jaymoskingdoms.why.nothing");
		}
		if (!Languages.understands(player, who)) {
			return Component.translatable("come.jaymoskingdoms.gist." + v.reason().key, v.args());
		}
		if (v.reason() == Reason.APPROACH) {
			return v.said();
		}
		RandomSource random = who.getRandom();
		return Component.translatable("come.jaymoskingdoms.why." + v.reason().key + "." + random.nextInt(WHY_LINES), v.args());
	}

	/** The visit's been explained: the option leaves their menu. */
	public static void answered(ServerPlayer player, LivingEntity who) {
		VISITS.remove(key(who, player));
	}
}
