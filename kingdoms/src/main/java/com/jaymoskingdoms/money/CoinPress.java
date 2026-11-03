package com.jaymoskingdoms.money;

import com.jaymoskingdoms.Faction;
import com.jaymoskingdoms.FactionType;
import com.jaymoskingdoms.Factions;
import com.jaymoskingdoms.JaymosKingdoms;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.487) The Coin Press's rules: what it strikes, where, and how much. It only strikes inside a realm (a kingdom or a
 * fiefdom with a seat, which has its mint with its town hall); anywhere else it has "no die". Each strike pays
 * {@link #MINT_TAX_PERCENT} of its value into the realm's treasury (seigniorage), and a realm's mint strikes at most
 * {@link #MINT_PER_DAY} coins a day, {@link #MINT_PER_FIEF} more for each fiefdom it holds, so a piglin-gold or copper farm
 * can't print money without end. All of it is tuning: change the numbers here.
 *
 * <p>Struck from: a copper ingot into {@value #COPPER_PER_INGOT} copper coins; an ingot of the {@code c:ingots/silver}
 * tag (Powers' silver) into {@value #SILVER_PER_INGOT} silver coins; {@value #NUGGETS_PER_GOLD} gold nuggets into one gold coin.
 */
public final class CoinPress {
	/** Seigniorage: the share of a strike's worth that goes to the realm's treasury. */
	public static final int MINT_TAX_PERCENT = 10;
	/** A realm's mint strikes this many coins a day, and this many more for each fiefdom it holds. */
	public static final int MINT_PER_DAY = 32, MINT_PER_FIEF = 8;
	/** How far (blocks) from a realm's seat its press still belongs to it (its claimed land counts at any distance). */
	public static final double MINT_RANGE = 160.0;
	public static final int COPPER_PER_INGOT = 2, SILVER_PER_INGOT = 3, NUGGETS_PER_GOLD = 9;
	/** The most coins a press hands over at once (one stack). */
	public static final int MOST_AT_ONCE = 64;
	/** Status codes the press screen shows. */
	public static final int OK = 0, NO_DIE = 1, SPENT = 2;

	/** The silver ingots of Jaymo's Powers (or any mod's): the {@code c:ingots/silver} tag. */
	public static final TagKey<Item> SILVER_INGOTS = TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("c", "ingots/silver"));

	/** A realm's mint today: the day, the coins struck in it, and the seigniorage owed but not yet a whole silver (in hundredths of cp). */
	public record Day(long day, int coins, int due) {
		public static final Codec<Day> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.LONG.fieldOf("day").forGetter(Day::day),
				Codec.INT.fieldOf("coins").forGetter(Day::coins),
				Codec.INT.optionalFieldOf("due", 0).forGetter(Day::due))
			.apply(i, Day::new));
	}

	/** Each realm's mint (faction id → its day), kept on the Overworld. */
	public static final AttachmentType<Map<String, Day>> MINTED = AttachmentRegistry.create(JaymosKingdoms.id("minted"),
		builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, Day.CODEC)));

	/** What an input strikes into: the coin, how much input makes one strike, and how many coins that strike gives. */
	public record Die(Coin coin, int input, int coins) {
	}

	private CoinPress() {
	}

	static void register() {
		JaymosKingdoms.LOGGER.debug("mint attachment: {}", MINTED);
	}

	/** What {@code input} is struck into, or null if the press takes no such thing. */
	public static @Nullable Die die(ItemStack input) {
		if (input.is(Items.COPPER_INGOT)) {
			return new Die(Coin.COPPER, 1, COPPER_PER_INGOT);
		}
		if (input.is(SILVER_INGOTS)) {
			return new Die(Coin.SILVER, 1, SILVER_PER_INGOT);
		}
		if (input.is(Items.GOLD_NUGGET)) {
			return new Die(Coin.GOLD, NUGGETS_PER_GOLD, 1);
		}
		return null;
	}

	/** The realm whose mint a press at {@code pos} is in, or null (no die). */
	public static @Nullable Faction mint(ServerLevel level, BlockPos pos) {
		Faction f = Factions.around(level.getServer(), level, pos, MINT_RANGE);
		return f != null && f.type().isRealm() && !f.fallen() ? f : null;
	}

	private static long today(MinecraftServer server) {
		return server.overworld().getGameTime() / 24000L;
	}

	/** Coins {@code f}'s mint may still strike today. */
	public static int remaining(MinecraftServer server, Faction f) {
		long fiefs = Factions.all(server, FactionType.FIEFDOM).stream().filter(x -> x.overlord() == f.id() && !x.fallen()).count();
		int cap = MINT_PER_DAY + MINT_PER_FIEF * (int)fiefs;
		return Math.max(0, cap - struckToday(server, f));
	}

	public static int cap(MinecraftServer server, Faction f) {
		long fiefs = Factions.all(server, FactionType.FIEFDOM).stream().filter(x -> x.overlord() == f.id() && !x.fallen()).count();
		return MINT_PER_DAY + MINT_PER_FIEF * (int)fiefs;
	}

	private static int struckToday(MinecraftServer server, Faction f) {
		Day d = server.overworld().getAttachedOrElse(MINTED, Map.of()).get(Integer.toString(f.id()));
		return d != null && d.day() == today(server) ? d.coins() : 0;
	}

	/** Records a strike of {@code coins} {@code coin}s by {@code f}'s mint, and pays the realm its seigniorage. */
	public static void struck(MinecraftServer server, Faction f, Coin coin, int coins) {
		Map<String, Day> all = new HashMap<>(server.overworld().getAttachedOrElse(MINTED, Map.of()));
		Day d = all.get(Integer.toString(f.id()));
		long day = today(server);
		int struck = d != null && d.day() == day ? d.coins() : 0;
		int due = d != null ? d.due() : 0;
		due += coins * coin.value() * MINT_TAX_PERCENT;
		long silver = due / (100 * Money.SILVER);
		due -= (int)(silver * 100 * Money.SILVER);
		all.put(Integer.toString(f.id()), new Day(day, struck + coins, due));
		server.overworld().setAttached(MINTED, Map.copyOf(all));
		if (silver > 0L) {
			Factions.update(server, f.id(), x -> x.withTreasury(x.treasury() + silver));
		}
	}
}
