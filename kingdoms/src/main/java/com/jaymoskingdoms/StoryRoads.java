package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import org.jspecify.annotations.Nullable;

/**
 * The Retaking's path stories ({@link Retaking}): beside the five acts, a player's own road from their calling, a few steps
 * that each belong to an act and count only once that act has begun (as the act milestones do). They say what to do, never
 * what it means. The Story tab lists the player's roads under the acts; the Z Fighter's road (the sagas) is Jaymo's Powers'
 * and reaches the tab its own way.
 * <ul>
 * <li><b>The Arcanist's Road</b> (Mages and Sorcerers, told by court wizards): carry a caster's staff; be told the old war
 * by an elder mage (or a demon); read a court wizard's margin notes ({@code a_wizards_margins}); learn a second spell for
 * good.</li>
 * <li><b>The Faithful Road</b> (anyone sworn to a patron): swear; reach your patron's second tier of favour; bring down
 * your patron's champion; stand against a rival faith's champion; bring down your own champion three times.</li>
 * </ul>
 */
public final class StoryRoads {
	/** A road's step: its name and the act it belongs to. */
	public record Step(String name, Retaking.Act act) {
	}

	/** A road: its key, whose it is, and its steps in order. */
	public record Road(String key, Predicate<ServerPlayer> yours, List<Step> steps) {
	}

	/** The court wizard's margin notes (a lore book). */
	static final String MARGINS = JaymosKingdoms.id("a_wizards_margins").toString();
	/** The patron favour tier the Faithful Road's second step asks for, and the champion kills its last. */
	static final int FAITH_TIER = 2, OWN_CHAMPIONS = 3;

	public static final List<Road> ROADS = List.of(
		new Road("arcana", p -> Classes.is(p, Classes.PlayerClass.MAGE) || Classes.is(p, Classes.PlayerClass.SORCERER), List.of(
			new Step("staff", Retaking.Act.HEARTH), new Step("told", Retaking.Act.OLD_STONES), new Step("margins", Retaking.Act.LIGHTS),
			new Step("learned", Retaking.Act.DARK_BELOW))),
		new Road("faith", p -> Patrons.of(p) != null, List.of(
			new Step("sworn", Retaking.Act.HEARTH), new Step("favour", Retaking.Act.OLD_STONES), new Step("champion", Retaking.Act.LIGHTS),
			new Step("rival", Retaking.Act.DARK_BELOW), new Step("champion_three", Retaking.Act.LEGEND))));

	/** The road steps done, as {@code road.step}. Saved, kept through death. */
	static final AttachmentType<List<String>> DONE = AttachmentRegistry.create(JaymosKingdoms.id("retaking_roads"),
		builder -> builder.persistent(Codec.STRING.listOf()).copyOnDeath());
	/** Your own patron's champions brought down (for the Faithful Road). Saved, kept through death. */
	static final AttachmentType<Integer> OWN_CHAMPION_KILLS = AttachmentRegistry.create(JaymosKingdoms.id("retaking_own_champions"),
		builder -> builder.persistent(Codec.INT).copyOnDeath());

	private StoryRoads() {
	}

	static void register() {
		JaymosKingdoms.LOGGER.debug("roads: {} {}", DONE, OWN_CHAMPION_KILLS);
	}

	public static @Nullable Road road(String key) {
		for (Road r : ROADS) {
			if (r.key().equals(key)) {
				return r;
			}
		}
		return null;
	}

	/** The keys of {@code player}'s roads. */
	static List<String> yours(ServerPlayer player) {
		List<String> keys = new ArrayList<>();
		for (Road r : ROADS) {
			if (r.yours().test(player)) {
				keys.add(r.key());
			}
		}
		return keys;
	}

	static List<String> done(ServerPlayer player) {
		return player.getAttachedOrElse(DONE, List.of());
	}

	/** {@code road}'s {@code step} is done by {@code player}, if the road is theirs and its act has begun. */
	static void mark(ServerPlayer player, String road, String step) {
		Road r = road(road);
		if (r == null || !r.yours().test(player)) {
			return;
		}
		for (Step s : r.steps()) {
			if (s.name().equals(step) && Knowledge.level(player) >= s.act().ordinal()) {
				String id = road + "." + step;
				List<String> done = done(player);
				if (!done.contains(id)) {
					List<String> more = new ArrayList<>(done);
					more.add(id);
					player.setAttached(DONE, List.copyOf(more));
				}
				return;
			}
		}
	}

	/** Looked at with the act milestones: the steps that are a state rather than a deed. */
	static void look(ServerPlayer player) {
		if (Classes.is(player, Classes.PlayerClass.MAGE) || Classes.is(player, Classes.PlayerClass.SORCERER)) {
			if (carriesStaff(player)) {
				mark(player, "arcana", "staff");
			}
			if (LoreCodex.holds(player, MARGINS)) {
				mark(player, "arcana", "margins");
			}
			if (Classes.of(player).spells().size() >= 2) {
				mark(player, "arcana", "learned");
			}
		}
		Patron sworn = Patrons.of(player);
		if (sworn != null) {
			mark(player, "faith", "sworn");
			if (Patrons.state(player).tier() >= FAITH_TIER) {
				mark(player, "faith", "favour");
			}
		}
	}

	private static boolean carriesStaff(ServerPlayer player) {
		Inventory pack = player.getInventory();
		for (int i = 0; i < pack.getContainerSize(); i++) {
			if (Arcana.isStaff(pack.getItem(i))) {
				return true;
			}
		}
		return false;
	}

	/** (From the court wizard's or a demon's telling of the old war.) */
	public static void told(ServerPlayer player) {
		mark(player, "arcana", "told");
	}

	/** (From a patron champion's fall, for each who fought it.) {@code own}: it was their own patron's; {@code rival}: a rival's. */
	static void champion(ServerPlayer player, boolean own, boolean rival) {
		if (own) {
			int kills = player.getAttachedOrElse(OWN_CHAMPION_KILLS, 0) + 1;
			player.setAttached(OWN_CHAMPION_KILLS, kills);
			mark(player, "faith", "champion");
			if (kills >= OWN_CHAMPIONS) {
				mark(player, "faith", "champion_three");
			}
		} else if (rival) {
			mark(player, "faith", "rival");
		}
	}
}
