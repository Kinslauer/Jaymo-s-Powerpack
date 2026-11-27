package com.jaymoskingdoms;

import com.jaymoskingdoms.money.Money;
import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/**
 * Andy (2026-11-26, the life story's realm page): "Realms should have their own random or reasoned specific benefits and
 * patrons." What a realm gives the ones it counts as its own (a noble family's realm, or the realm a sworn start serves),
 * reasoned from what the realm is, so no two realms of different lands and creeds give the same:
 * <ul>
 * <li><b>its patron</b> ({@link RealmFaiths#patron}): known to you, and if you grew up praying to the same one, its shrines
 * kept you, so you start with {@link #KINDRED_FAVOUR} more favour;</li>
 * <li><b>its land</b> ({@link Climate}): a story point in what living there makes of people ({@link #landStat});</li>
 * <li><b>its creed</b>: the Crown a name at court (more goodwill), Coin a family account at its counting house, the others
 * a story point in what the creed asks of its people ({@link #creedStat}).</li>
 * </ul>
 * The client reads {@link #landStat} and {@link #creedStat} to show them on the realm page.
 */
public final class RealmGifts {
	/** Favour more for a childhood faith that is the realm's own patron. */
	public static final int KINDRED_FAVOUR = 10;
	/** A Crown realm's goodwill to its noble families and sworn men (at least this; others give {@link LifeStory#NOBLE_REPUTATION}). */
	public static final int CROWN_GOODWILL = 70;
	/** A Coin realm's family account: emeralds' worth put to your name at its counting house. */
	public static final int COIN_PURSE = 30;
	/** The answers whose realm counts you as its own and gives: a noble family's, and a sworn start's. */
	public static final Set<String> GIVING = Set.of("nobles", "sworn");

	/** The story points the realms named gave ("raised:" or "road:" and a stat key, one per point). Kept through death, set afresh with each story. */
	static final AttachmentType<List<String>> POINTS = AttachmentRegistry.create(JaymosKingdoms.id("realm_gifts"),
		b -> b.persistent(Codec.STRING.listOf()).copyOnDeath());

	private RealmGifts() {
	}

	static void register() {
		JaymosKingdoms.LOGGER.debug("realm gifts attachment: {}", POINTS);
	}

	/** What living in {@code climate} makes of people. */
	public static Stats.Stat landStat(Climate climate) {
		return switch (climate) {
			// the plains keep market days; Blossom bows on greeting
			case PLAINS -> Stats.Stat.WITS;
			case CHERRY -> Stats.Stat.PRESENCE;
			// the long winters; the woodsmen of the north
			case SNOWY -> Stats.Stat.ENDURANCE;
			case TAIGA -> Stats.Stat.MIGHT;
			// the sands charge for water
			case DESERT -> Stats.Stat.GRIT;
			// the open grass's riders and bowmen; the jungle's climbers
			case SAVANNA -> Stats.Stat.FINESSE;
			case JUNGLE -> Stats.Stat.AGILITY;
			// the badlands' deep gold seams
			case BADLANDS -> Stats.Stat.MIGHT;
			// the swamp's witches
			case SWAMP -> Stats.Stat.ARCANA;
		};
	}

	/** What a creed asks of its people, by its key; null for the Crown and Coin, which give otherwise. */
	public static Stats.@Nullable Stat creedStat(String creed) {
		return switch (creed) {
			case "chivalry" -> Stats.Stat.PRESENCE;
			case "conquest" -> Stats.Stat.MIGHT;
			case "faith" -> Stats.Stat.GRIT;
			case "blood" -> Stats.Stat.ENDURANCE;
			default -> null;
		};
	}

	/** Forgets the gifts of an earlier story (a story told again starts clean). */
	static void clear(ServerPlayer player) {
		player.removeAttached(POINTS);
	}

	/**
	 * {@code realm} counts {@code player} as its own ({@code road}: the realm their road named, else their family's): its patron's kindred favour, its land's and creed's points, and the
	 * Crown's goodwill or Coin's account. Call after the patron of their childhood is sworn ({@link Patrons#storyStart}).
	 */
	static void give(ServerPlayer player, Faction realm, boolean road) {
		net.minecraft.server.MinecraftServer server = player.level().getServer();
		Patron patron = RealmFaiths.patron(server, realm);
		if (patron != null) {
			Patrons.learn(player, patron);
			if (Patrons.state(player).patronOf() == patron) {
				Patrons.gain(player, KINDRED_FAVOUR);
			}
		}
		// (a road set out on again replaces the last road's points)
		String from = road ? "road:" : "raised:";
		List<String> points = new ArrayList<>(player.getAttachedOrElse(POINTS, List.of()));
		points.removeIf(k -> k.startsWith(from));
		points.add(from + landStat(Climate.at(Factions.level(server), realm.seat())).key());
		GreatWars.Creed creed = GreatWars.creed(server, realm);
		Stats.Stat stat = creedStat(creed.key());
		if (stat != null) {
			points.add(from + stat.key());
		} else if (creed == GreatWars.Creed.CROWN) {
			int now = Reputation.get(player, realm.id());
			if (now < CROWN_GOODWILL) {
				Reputation.change(player, realm, CROWN_GOODWILL - now);
			}
		} else if (creed == GreatWars.Creed.COIN) {
			Banks.endow(player, realm, Money.worth(COIN_PURSE));
		}
		player.setAttached(POINTS, List.copyOf(points));
		Stats.attributes(player);
	}

	/** Story points in {@code stat} the realms named gave {@code player}. */
	static int points(Player player, Stats.Stat stat) {
		int n = 0;
		for (String key : player.getAttachedOrElse(POINTS, List.of())) {
			if (key.endsWith(":" + stat.key())) {
				n++;
			}
		}
		return n;
	}
}
