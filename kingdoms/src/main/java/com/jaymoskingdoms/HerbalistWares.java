package com.jaymoskingdoms;

import com.jaymoskingdoms.money.Money;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.util.Prediction;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * The order's herbalist sells belt charms (docs/Climate-Plan.md, cl-charm). Jaymo's Powers' hot-stone pouch (warms) and
 * cold-clay flask (cools) are worn in the belt slot; the herbalist of a monastery's herbalist house ({@link IllagerSites}) makes them
 * for silver, and says so when spoken to. The rare equinox charm (both at once) is made by no one: now and then one lies in the
 * monastery's own chests ({@link #RARE_CHANCE} of a chest from the brothers' store). Powers is found by item id; without it
 * the herbalist has nothing to sell and speaks as before.
 */
public final class HerbalistWares {
	/** Emeralds' worth for the pouch and the flask. */
	static final int POUCH_PRICE = 4, FLASK_PRICE = 4;
	/** The chance (out of 1) a chest of the monastery's store holds an equinox charm. */
	static final float RARE_CHANCE = 0.12F;
	private static final Identifier POUCH = Identifier.fromNamespaceAndPath("jaymospowers", "hot_stone_pouch"),
		FLASK = Identifier.fromNamespaceAndPath("jaymospowers", "cold_clay_flask"),
		EQUINOX = Identifier.fromNamespaceAndPath("jaymospowers", "equinox_charm");

	/** The MCA command (the button's id, less "gui.button."). */
	public static final String COMMAND = "herbalist_wares";

	private HerbalistWares() {
	}

	static void register() {
		// the rare one: now and then in a chest of the monastery's store
		LootTableEvents.MODIFY_DROPS.register((table, context, drops) -> {
			String path = table.unwrapKey().map(key -> key.identifier().getPath()).orElse("");
			if (path.equals("chests/illager_sites/monks") && context.getRandom().nextFloat() < RARE_CHANCE) {
				BuiltInRegistries.ITEM.getOptional(EQUINOX).ifPresent(item -> drops.add(new ItemStack(item)));
			}
		});
	}

	/** The Belt Charms button: the herbalist lays out what they make. */
	public static void ask(ServerPlayer player, Villager villager) {
		ServerLevel level = (ServerLevel)villager.level();
		Optional<Item> pouch = BuiltInRegistries.ITEM.getOptional(POUCH), flask = BuiltInRegistries.ITEM.getOptional(FLASK);
		if (!(villager instanceof Worker w) || w.role() != IllagerSites.HERBALIST) {
			Speech.tell(level, villager, player, Component.translatable("speech.jaymoskingdoms.herbalist.none"), Speech.WHITE);
			return;
		}
		List<Menus.Choice> choices = new ArrayList<>();
		pouch.ifPresent(item -> choices.add(sale(player, item, POUCH_PRICE, "pouch")));
		flask.ifPresent(item -> choices.add(sale(player, item, FLASK_PRICE, "flask")));
		// (what the things out on the road fear: one met kind's fear a time, into the bestiary)
		choices.add(Menus.Choice.of(Component.translatable("speech.jaymoskingdoms.herbalist.fears"), p -> Speech.tell(level, villager, p,
			Component.translatable(Bestiary.toldOfAny(p) ? "speech.jaymoskingdoms.herbalist.fears.told" : "speech.jaymoskingdoms.herbalist.fears.none"), Speech.WHITE)));
		choices.add(Menus.Choice.of(Component.translatable("speech.jaymoskingdoms.herbalist.leave"), p -> {
		}));
		Menus.ask(player, villager, Component.translatable("speech.jaymoskingdoms.herbalist.wares"), choices);
	}

	private static Menus.Choice sale(ServerPlayer player, Item item, int price, String key) {
		return Menus.Choice.of(Component.translatable("speech.jaymoskingdoms.herbalist." + key, price), Money.canPay(player, Money.worth(price)), p -> {
			if (Money.charge(p, Money.worth(price))) {
				p.getInventory().placeItemBackInInventory(new ItemStack(item), Prediction.SERVER_ONLY);
				p.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.herbalist.sold", new ItemStack(item).getHoverName()));
			}
		});
	}
}
