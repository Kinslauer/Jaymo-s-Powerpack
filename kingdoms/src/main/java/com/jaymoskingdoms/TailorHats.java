package com.jaymoskingdoms;

import com.jaymoskingdoms.money.Money;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Prediction;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * (Village mods patch 5) A tailor sews the village trades' hats (Jaymo's Powers' trade hats, ported from Villager Hats):
 * the Sew a Hat button on a tailor's page ({@code McaScreensMixin}, shown only with Powers) opens a list of the hats and
 * what each costs from this tailor ({@link Tailors#price}), cheaper the better they like you. The hats have no grid
 * recipe: a tailor, or a zombie villager of the trade, is where one comes from. Which job each hat goes with:
 * {@link #HATS}, read by {@link Careers#hatBonus}.
 */
public final class TailorHats {
	/** The MCA command (the button's id, less "gui.button."). */
	public static final String COMMAND = "trade_hat";
	/** The cheapest tailor's price for a hat (silver). */
	static final int HAT_BASE = 4;

	/** A hat (its Powers item id) and the job (Careers) whose hat it is, or null for a trade no player job matches. */
	record Hat(String id, Careers.@Nullable Job job) {
	}

	static final List<Hat> HATS = List.of(new Hat("farmer_hat", Careers.Job.FARMER), new Hat("fletcher_hat", Careers.Job.HUNTER),
		new Hat("fisherman_hat", Careers.Job.FISHER), new Hat("armorer_hat", Careers.Job.SMITH), new Hat("shepherd_hat", null),
		new Hat("librarian_hat", null), new Hat("butcher_hat", Careers.Job.BUTCHER));

	private TailorHats() {
	}

	/** Powers' item for {@code hat}, if Powers is loaded. */
	static Optional<Item> item(Hat hat) {
		return BuiltInRegistries.ITEM.getOptional(Identifier.fromNamespaceAndPath("jaymospowers", hat.id()));
	}

	/** The Sew a Hat button: the hats this tailor sews, each with its price. */
	public static void ask(ServerPlayer player, Villager villager) {
		ServerLevel level = (ServerLevel)villager.level();
		if (!Tailors.is(villager)) {
			Speech.tell(level, villager, player, Component.translatable("tailor.jaymoskingdoms.not"), Speech.WHITE);
			return;
		}
		List<Item> items = new ArrayList<>();
		for (Hat hat : HATS) {
			item(hat).ifPresent(items::add);
		}
		if (items.isEmpty()) {
			Speech.tell(level, villager, player, Component.translatable("tailor.jaymoskingdoms.no_hats"), Speech.WHITE);
			return;
		}
		int price = Tailors.price(villager, player, HAT_BASE);
		List<Component> options = new ArrayList<>();
		List<Boolean> enabled = new ArrayList<>();
		for (Item item : items) {
			options.add(Component.translatable("tailor.jaymoskingdoms.hat_option", new ItemStack(item).getHoverName(), price));
			enabled.add(true);
		}
		options.add(Component.translatable("talk.jaymoskingdoms.errands.leave"));
		enabled.add(true);
		ZFighterFolk.ask(player, villager, Component.translatable("tailor.jaymoskingdoms.hat_fee", price), options, enabled, pick -> {
			if (pick >= 0 && pick < items.size()) {
				sell(player, villager, items.get(pick), price);
			}
		});
	}

	private static void sell(ServerPlayer player, Villager villager, Item hat, int price) {
		ServerLevel level = (ServerLevel)villager.level();
		if (!player.isCreative()) {
			if (!Money.canPay(player, Money.worth(price))) {
				Speech.tell(level, villager, player, Component.translatable("tailor.jaymoskingdoms.hat_cant_pay", price), Speech.WHITE);
				return;
			}
			Money.charge(player, Money.worth(price));
		}
		ItemStack stack = new ItemStack(hat);
		Component name = stack.getHoverName();
		player.getInventory().placeItemBackInInventory(stack, Prediction.SERVER_ONLY);
		Speech.tell(level, villager, player, Component.translatable("tailor.jaymoskingdoms.hat_sold", name, price), Speech.WHITE);
	}
}
