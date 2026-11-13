package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Prediction;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
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
 * <li><b>The Thief's Road</b> (Thieves, taught by the Ash Whisperer's quick-handed faithful): pick a pocket clean; pick a
 * lock; find a den; and, behind a lock picked in the Dark Below, a contract under a black candle and the book someone paid
 * to have buried ({@code a_contract}, with a copy of {@code the_long_defence}).</li>
 * <li><b>The Old Campaign</b> (Warlords, Brutes, Riders, Marksmen, Archers and Survivalists): rise in your trade's skill
 * (Survival for Archers and Survivalists, Strength for the rest), each {@link #CAMPAIGN_EVERY} levels a step, and each step
 * hands you the next part of an old campaigner's account ({@code the_old_campaign_1..4}).</li>
 * <li><b>The Hermit's Road</b> (Smoke, Gravity and Cryo users, whose ki went to an element and who cannot see spirits): meet a
 * hermit wizard, who writes you a letter ({@code a_hermits_letter}); then grow the thing you have instead, your Valor
 * (Kindled, Tempered, Steeled).</li>
 * <li><b>The Common Road</b> (anyone with no power): hold a job; take up a class; swear to a patron; become a Master at
 * your job; reach Steeled Valor.</li>
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
	/** The contract under a black candle (a lore book), found with the book it is about behind a picked lock. */
	static final String CONTRACT = JaymosKingdoms.id("a_contract").toString();
	/** The chance a locked chest picked on the Thief's last step holds the contract. */
	static final double CONTRACT_CHANCE = 0.34;
	/** The trade skill levels between the Old Campaign's steps. */
	static final int CAMPAIGN_EVERY = 5;
	static final List<String> CAMPAIGN_STEPS = List.of("first", "second", "third", "fourth");
	/** The powers the Hermit's Road is for (their ids' paths). */
	static final Set<String> ELEMENTAL = Set.of("smoke", "gravity", "cryo");
	static final Set<Classes.PlayerClass> CAMPAIGNERS = EnumSet.of(Classes.PlayerClass.WARLORD, Classes.PlayerClass.BRUTE,
		Classes.PlayerClass.RIDER, Classes.PlayerClass.MARKSMAN, Classes.PlayerClass.RANGER, Classes.PlayerClass.PALADIN, Classes.PlayerClass.MONK);

	public static final List<Road> ROADS = List.of(
		new Road("arcana", p -> Classes.is(p, Classes.PlayerClass.MAGE) || Classes.is(p, Classes.PlayerClass.SORCERER), List.of(
			new Step("staff", Retaking.Act.HEARTH), new Step("told", Retaking.Act.OLD_STONES), new Step("margins", Retaking.Act.LIGHTS),
			new Step("learned", Retaking.Act.DARK_BELOW))),
		new Road("faith", p -> Patrons.of(p) != null, List.of(
			new Step("sworn", Retaking.Act.HEARTH), new Step("favour", Retaking.Act.OLD_STONES), new Step("champion", Retaking.Act.LIGHTS),
			new Step("rival", Retaking.Act.DARK_BELOW), new Step("champion_three", Retaking.Act.LEGEND))),
		new Road("thief", p -> Classes.is(p, Classes.PlayerClass.THIEF), List.of(
			new Step("lift", Retaking.Act.HEARTH), new Step("lock", Retaking.Act.OLD_STONES), new Step("den", Retaking.Act.LIGHTS),
			new Step("buried", Retaking.Act.DARK_BELOW))),
		new Road("campaign", p -> CAMPAIGNERS.contains(Classes.classOf(p)), List.of(
			new Step("first", Retaking.Act.HEARTH), new Step("second", Retaking.Act.OLD_STONES), new Step("third", Retaking.Act.LIGHTS),
			new Step("fourth", Retaking.Act.DARK_BELOW))),
		new Road("hermit", p -> PowersCompat.power(p) != null && ELEMENTAL.contains(PowersCompat.power(p)), List.of(
			new Step("met", Retaking.Act.HEARTH), new Step("kindled", Retaking.Act.OLD_STONES), new Step("tempered", Retaking.Act.LIGHTS),
			new Step("steeled", Retaking.Act.DARK_BELOW))),
		new Road("common", p -> PowersCompat.power(p) == null, List.of(
			new Step("job", Retaking.Act.HEARTH), new Step("class", Retaking.Act.OLD_STONES), new Step("sworn", Retaking.Act.LIGHTS),
			new Step("master", Retaking.Act.DARK_BELOW), new Step("steeled", Retaking.Act.LEGEND))));

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

	/** {@code road}'s {@code step} is done by {@code player}, if the road is theirs and its act has begun; true if newly. */
	static boolean mark(ServerPlayer player, String road, String step) {
		Road r = road(road);
		if (r == null || !r.yours().test(player)) {
			return false;
		}
		for (Step s : r.steps()) {
			if (s.name().equals(step) && Knowledge.level(player) >= s.act().ordinal()) {
				String id = road + "." + step;
				List<String> done = done(player);
				if (!done.contains(id)) {
					List<String> more = new ArrayList<>(done);
					more.add(id);
					player.setAttached(DONE, List.copyOf(more));
					return true;
				}
				return false;
			}
		}
		return false;
	}

	static boolean isDone(ServerPlayer player, String road, String step) {
		return done(player).contains(road + "." + step);
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
		if (Classes.is(player, Classes.PlayerClass.THIEF)) {
			if (!player.getAttachedOrElse(Underworld.FOUND, List.of()).isEmpty()) {
				mark(player, "thief", "den");
			}
			if (LoreCodex.holds(player, CONTRACT)) {
				mark(player, "thief", "buried");
			}
		}
		Classes.PlayerClass cls = Classes.classOf(player);
		if (cls != null && CAMPAIGNERS.contains(cls)) {
			Skills.Pillar pillar = cls == Classes.PlayerClass.RANGER ? Skills.Pillar.SURVIVAL : Skills.Pillar.STRENGTH;
			int level = Skills.of(player).level(pillar);
			for (int i = 0; i < CAMPAIGN_STEPS.size() && level >= (i + 1) * CAMPAIGN_EVERY; i++) {
				if (mark(player, "campaign", CAMPAIGN_STEPS.get(i))) {
					give(player, LoreBooks.book("the_old_campaign_" + (i + 1)));
				}
			}
		}
		int valor = Valor.rank(player);
		if (valor >= Valor.KINDLED) {
			mark(player, "hermit", "kindled");
		}
		if (valor >= Valor.TEMPERED) {
			mark(player, "hermit", "tempered");
		}
		if (valor >= Valor.STEELED) {
			mark(player, "hermit", "steeled");
			mark(player, "common", "steeled");
		}
		Careers.Career career = Careers.of(player);
		if (!career.posts().isEmpty()) {
			mark(player, "common", "job");
		}
		if (career.master()) {
			mark(player, "common", "master");
		}
		if (Classes.classOf(player) != null) {
			mark(player, "common", "class");
		}
		if (Patrons.of(player) != null) {
			mark(player, "common", "sworn");
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

	private static void give(ServerPlayer player, ItemStack stack) {
		if (!stack.isEmpty() && !player.getInventory().add(stack)) {
			player.drop(stack, false, Prediction.SERVER_ONLY);
		}
	}

	/** (From a hermit wizard's greeting.) The first time, on the Hermit's Road, the hermit writes you a letter. */
	static void hermit(ServerPlayer player) {
		if (mark(player, "hermit", "met")) {
			give(player, LoreBooks.book("a_hermits_letter"));
		}
	}

	/** (From a pocket picked with something in it, or a lock picked.) {@code step}: "lift" or "lock". */
	public static void thief(ServerPlayer player, String step) {
		mark(player, "thief", step);
	}

	/**
	 * (From a locked chest picked, before it opens.) On the Thief's last step, now and then the chest holds the contract
	 * and the book it is about.
	 */
	static void pickedChest(ServerPlayer player, BlockEntity chest) {
		if (!(chest instanceof Container box) || !Classes.is(player, Classes.PlayerClass.THIEF) || isDone(player, "thief", "buried")
			|| Knowledge.level(player) < Retaking.Act.DARK_BELOW.ordinal() || LoreCodex.holds(player, CONTRACT)
			|| player.getRandom().nextDouble() >= CONTRACT_CHANCE) {
			return;
		}
		for (ItemStack book : List.of(LoreBooks.book("a_contract"), LoreBooks.book("the_long_defence"))) {
			for (int i = 0; i < box.getContainerSize() && !book.isEmpty(); i++) {
				if (box.getItem(i).isEmpty()) {
					box.setItem(i, book);
					book = ItemStack.EMPTY;
				}
			}
		}
		box.setChanged();
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
