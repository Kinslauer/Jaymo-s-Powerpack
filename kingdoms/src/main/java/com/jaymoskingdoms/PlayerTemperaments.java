package com.jaymoskingdoms;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

/**
 * (Powers' Motion patch 1) A player's own temperament (Andy: "starting questionnaire leads to different player
 * temperaments that they cannot change"): worked out from their life story ({@link LifeStory#STORY}) when it's sealed,
 * and for old players with a story on their next join. The same story always gives the same temperament, and nothing
 * else sets it, so it can't be changed. It's kept in the humanoids' own attachment ({@link Temperaments#TEMPERAMENT},
 * synced to everyone), so Jaymo's Powers reads it to draw the player's bearing (a timid or suspicious one jumps at
 * shadows, a brave one shrugs off small hits...). Players aren't {@link Temperaments#humanoid}, so none of the
 * villagers' rules about temperaments touch them.
 * <p>Each answer leans towards a temperament: who raised you counts {@link #RAISED_WEIGHT}, what you did as a child
 * {@link #CHILD_WEIGHT}, and a few roads ({@link #ROADS}) {@link #ROAD_WEIGHT}. The heaviest wins; a tie goes to the
 * childhood's (then to who raised you).
 */
public final class PlayerTemperaments {
	static final int RAISED_WEIGHT = 2, CHILD_WEIGHT = 2, ROAD_WEIGHT = 1;

	/** Who raised you (answer key → temperament). */
	static final Map<String, Temperaments.Temperament> RAISED = Map.ofEntries(
		Map.entry("farmers", Temperaments.Temperament.CHEERFUL),
		Map.entry("smiths", Temperaments.Temperament.DUTIFUL),
		Map.entry("fisherfolk", Temperaments.Temperament.GLOOMY),
		Map.entry("hunters", Temperaments.Temperament.SUSPICIOUS),
		Map.entry("merchants", Temperaments.Temperament.GREEDY),
		Map.entry("soldiers", Temperaments.Temperament.BRAVE),
		Map.entry("healers", Temperaments.Temperament.KINDHEARTED),
		Map.entry("wizard", Temperaments.Temperament.DEVOUT),
		Map.entry("outlaws", Temperaments.Temperament.SPITEFUL),
		Map.entry("nobles", Temperaments.Temperament.PROUD),
		Map.entry("foundling", Temperaments.Temperament.TIMID));
	/** What you did as a child. */
	static final Map<String, Temperaments.Temperament> CHILD = Map.ofEntries(
		Map.entry("roofs", Temperaments.Temperament.BRAVE),
		Map.entry("caves", Temperaments.Temperament.TIMID),
		Map.entry("fights", Temperaments.Temperament.HOTHEADED),
		Map.entry("books", Temperaments.Temperament.SUSPICIOUS),
		Map.entry("swimming", Temperaments.Temperament.CHEERFUL),
		Map.entry("storms", Temperaments.Temperament.HOTHEADED),
		Map.entry("fire", Temperaments.Temperament.GLOOMY),
		Map.entry("snow", Temperaments.Temperament.DUTIFUL),
		Map.entry("bells", Temperaments.Temperament.DEVOUT),
		Map.entry("blades", Temperaments.Temperament.PROUD));
	/** The roads that leave a mark (start key → temperament); the rest add nothing. */
	static final Map<String, Temperaments.Temperament> ROADS = Map.ofEntries(
		Map.entry("banished", Temperaments.Temperament.SPITEFUL),
		Map.entry("captive", Temperaments.Temperament.TIMID),
		Map.entry("chained", Temperaments.Temperament.SUSPICIOUS),
		Map.entry("burned", Temperaments.Temperament.GLOOMY),
		Map.entry("sentenced", Temperaments.Temperament.SPITEFUL),
		Map.entry("rock_bottom", Temperaments.Temperament.GLOOMY),
		Map.entry("trader", Temperaments.Temperament.GREEDY),
		Map.entry("sworn", Temperaments.Temperament.DUTIFUL),
		Map.entry("pup", Temperaments.Temperament.KINDHEARTED),
		Map.entry("heirloom", Temperaments.Temperament.PROUD));

	private PlayerTemperaments() {
	}

	static void register() {
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> settle(handler.player, null));
		// the attachment isn't kept through death (it's the villagers' too), so it's set again from the story
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> settle(newPlayer, false));
	}

	/** The temperament a story gives (in {@link LifeStory.Question} order), or null for no story yet. */
	static Temperaments.@Nullable Temperament fromStory(@Nullable List<String> story) {
		if (story == null || story.size() < 6 || story.get(0).isEmpty()) {
			return null;
		}
		Temperaments.Temperament raised = RAISED.get(story.get(1)), child = CHILD.get(story.get(2)), road = ROADS.get(story.get(5));
		Map<Temperaments.Temperament, Integer> score = new EnumMap<>(Temperaments.Temperament.class);
		if (raised != null) {
			score.merge(raised, RAISED_WEIGHT, Integer::sum);
		}
		if (child != null) {
			score.merge(child, CHILD_WEIGHT, Integer::sum);
		}
		if (road != null) {
			score.merge(road, ROAD_WEIGHT, Integer::sum);
		}
		if (score.isEmpty()) {
			return Temperaments.Temperament.DUTIFUL;
		}
		int best = score.values().stream().max(Integer::compare).orElse(0);
		// a tie: the childhood's, then who raised you, then the road's
		for (Temperaments.Temperament t : new Temperaments.Temperament[] {child, raised, road}) {
			if (t != null && score.getOrDefault(t, 0) == best) {
				return t;
			}
		}
		return Temperaments.Temperament.DUTIFUL;
	}

	/**
	 * Gives the player the temperament their story makes (unchanged if they already have it). {@code tell}: whether
	 * they're told (true when the story was just lived, false while it's still missing its road); null tells them only
	 * if it's new to them (an old player's first join after this patch).
	 */
	static void settle(ServerPlayer player, @Nullable Boolean tell) {
		Temperaments.Temperament t = fromStory(player.getAttached(LifeStory.STORY));
		if (t == null) {
			return;
		}
		boolean had = t.key().equals(player.getAttached(Temperaments.TEMPERAMENT));
		if (!had) {
			player.setAttached(Temperaments.TEMPERAMENT, t.key());
		}
		if (tell == null ? !had : tell) {
			player.sendSystemMessage(Component.translatable("temperament.jaymoskingdoms.yours",
				Component.translatable("temperament.jaymoskingdoms." + t.key()).withStyle(t.colour))
				.withStyle(style -> style.withHoverEvent(new net.minecraft.network.chat.HoverEvent.ShowText(Temperaments.hover(t)))));
		}
	}

	/** A player's temperament, or null (no story yet). */
	public static Temperaments.@Nullable Temperament of(ServerPlayer player) {
		return Temperaments.Temperament.byKey(player.getAttached(Temperaments.TEMPERAMENT));
	}
}
