package com.jaymoskingdoms;

import java.util.Optional;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Prediction;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * (0.7.69) Andy: "Village tailors (the ones who already change MCA clothes) sell the table and can make a costume for you
 * for emeralds, so you don't need the materials." Two more buttons on a tailor's MCA page ({@code McaScreensMixin}):
 * <ul>
 * <li><b>Buy a Costume Table</b>: Jaymo's Powers' {@code jaymospowers:costume_table}, by id, for emeralds;</li>
 * <li><b>Sew me a costume</b>: the tailor names a price and Powers' designer opens in its paid mode, by Powers' own
 * command run as the server ({@code /costume tailor <player> <price> <tailor>}): you design it, and the price is taken
 * in emeralds when it's sewn (you must still be near the tailor, within 5 minutes). Powers checks the rest.</li>
 * </ul>
 * Both are priced like the tailor's outfits ({@link Tailors#price}): the tailor's own rate, less for friends, more for
 * people they dislike, half for family. Nothing of Powers is loaded here: only its item id and its command.
 * <p>
 * (0.7.166) Andy: "the tailor villagers should provide an option that brings you to the costume editor for up to 3
 * emeralds". Sew a Costume (the costume editor) is now {@link #costumePrice}: {@link #COSTUME_MOST} emeralds, one off per
 * {@link Tailors#HEARTS_PER_EMERALD} hearts with the tailor, never under one; family pays one.
 */
public final class TailorCostumes {
	/** The MCA commands (the buttons' ids, less "gui.button."). */
	public static final String TABLE_COMMAND = "costume_table", SEW_COMMAND = "costume";
	/** The cheapest tailor's price for a Costume Table. */
	static final int TABLE_BASE = 10;
	/** (0.7.166) The most (and the cheapest) a tailor asks to sew a costume. */
	static final int COSTUME_MOST = 3, COSTUME_LEAST = 1;
	static final Identifier TABLE = Identifier.fromNamespaceAndPath("jaymospowers", "costume_table");

	private TailorCostumes() {
	}

	/** The Buy a Costume Table button. */
	public static void sellTable(ServerPlayer player, Villager villager) {
		ServerLevel level = (ServerLevel)villager.level();
		if (!Tailors.is(villager)) {
			Speech.tell(level, villager, player, Component.translatable("tailor.jaymoskingdoms.not"), Speech.WHITE);
			return;
		}
		Optional<Item> table = BuiltInRegistries.ITEM.getOptional(TABLE);
		if (table.isEmpty()) {
			Speech.tell(level, villager, player, Component.translatable("tailor.jaymoskingdoms.no_costumes"), Speech.WHITE);
			return;
		}
		int price = Tailors.price(villager, player, TABLE_BASE);
		if (!player.isCreative()) {
			if (Purse.count(player, Items.EMERALD) < price) {
				Speech.tell(level, villager, player, Component.translatable("tailor.jaymoskingdoms.table_cant_pay", price), Speech.WHITE);
				return;
			}
			Purse.take(player, Items.EMERALD, price);
		}
		player.getInventory().placeItemBackInInventory(new ItemStack(table.get()), Prediction.SERVER_ONLY);
		Speech.tell(level, villager, player, Component.translatable("tailor.jaymoskingdoms.table_sold", price), Speech.WHITE);
	}

	/** The Sew me a costume button: Powers' designer, for the tailor's price. */
	public static void sew(ServerPlayer player, Villager villager) {
		ServerLevel level = (ServerLevel)villager.level();
		if (!Tailors.is(villager)) {
			Speech.tell(level, villager, player, Component.translatable("tailor.jaymoskingdoms.not"), Speech.WHITE);
			return;
		}
		MinecraftServer server = level.getServer();
		if (!FabricLoader.getInstance().isModLoaded("jaymospowers") || server.getCommands().getDispatcher().getRoot().getChild("costume") == null) {
			Speech.tell(level, villager, player, Component.translatable("tailor.jaymoskingdoms.no_costumes"), Speech.WHITE);
			return;
		}
		int price = costumePrice(villager, player);
		if (!player.isCreative() && Purse.count(player, Items.EMERALD) < price) {
			Speech.tell(level, villager, player, Component.translatable("tailor.jaymoskingdoms.costume_cant_pay", price), Speech.WHITE);
			return;
		}
		Speech.tell(level, villager, player, Component.translatable("tailor.jaymoskingdoms.costume_fee", price), Speech.WHITE);
		// (by UUID, which the command's entity arguments take, so no name can go wrong)
		server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(),
			"costume tailor " + player.getStringUUID() + " " + price + " " + villager.getStringUUID());
	}

	/** (0.7.166) What {@code tailor} asks {@code player} for Sew a Costume: 3 emeralds, less for friends, 1 for family. */
	static int costumePrice(Villager tailor, ServerPlayer player) {
		if (McaBridge.family(tailor, player)) {
			return COSTUME_LEAST;
		}
		int off = Math.max(0, McaBridge.heartsOf(tailor, player) / Tailors.HEARTS_PER_EMERALD);
		return Math.max(COSTUME_LEAST, COSTUME_MOST - off);
	}
}
