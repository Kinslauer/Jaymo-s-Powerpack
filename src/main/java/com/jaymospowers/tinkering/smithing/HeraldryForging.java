package com.jaymospowers.tinkering.smithing;

import com.jaymospowers.tinkering.smeltery.Molten;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

/**
 * (Smithing) Jaymo's Kingdoms' late-medieval harness, hammered out here when Kingdoms is in (its pieces are found by id;
 * without it there is nothing to forge): the eight helms, the cuirass, spaulders, arm harness, gauntlets, cuisses,
 * greaves and sabatons from steel, and the mail haubergeon and the mail and brigandine spaulders from iron.
 * <p>
 * Every plate piece is offered in its three finishes, the smith choosing one as they start it: <b>bright</b>,
 * <b>blackened</b>, and <b>gilded</b>. Gilding is done only for a royal or a lord ({@link #noble}, told by Kingdoms),
 * and takes {@link #GILD_GOLD} gold ingots from the smith when the piece is done; anyone else is not offered it. The
 * finish rides on the piece as Kingdoms' {@code jaymoskingdoms:finish}.
 */
public final class HeraldryForging {
	/** The gold ingots a gilded piece takes. */
	static final int GILD_GOLD = 2;
	/** Each piece's item id, and the ingots it takes. */
	private static final String[] PLATE = {"frogmouth_helm", "houndskull_bascinet", "armet", "close_helm", "savoyard_helm", "sallet", "kettle_hat",
		"morion", "plate_cuirass", "plate_spaulders", "plate_arm_harness", "plate_gauntlets", "plate_cuisses", "plate_greaves", "plate_sabatons"};
	private static final int[] PLATE_INGOTS = {5, 5, 5, 5, 5, 5, 4, 4, 8, 3, 4, 3, 4, 3, 4};
	private static final String[] MAIL = {"mail_haubergeon", "mail_spaulders", "brigandine_spaulders"};
	private static final int[] MAIL_INGOTS = {6, 3, 3};
	private static final String[] FINISHES = {"bright", "blackened", "gilded"};
	private static final String PREFIX = "heraldry/";
	private static final String GILDED = "gilded";

	/** Whether {@code smith} is a royal or a lord, for whom plate is gilded. Jaymo's Kingdoms sets this at server start; without it nobody is. */
	public static Predicate<ServerPlayer> noble = smith -> false;

	private HeraldryForging() {
	}

	static void register() {
		Forging.PROVIDERS.add(HeraldryForging::options);
		Forging.CHECKS.add((smith, anvil, option) -> {
			if (!gilded(option)) {
				return null;
			}
			if (!noble.test(smith)) {
				return Component.translatable("message.jaymospowers.smithing.gilding_noble");
			}
			return gold(smith.getInventory()) < GILD_GOLD
				? Component.translatable("message.jaymospowers.smithing.gilding_gold", GILD_GOLD) : null;
		});
		Forging.FINISHES.add((smith, anvil, option, result) -> {
			if (gilded(option) && !smith.getAbilities().instabuild) {
				take(smith.getInventory(), GILD_GOLD);
			}
		});
	}

	private static boolean gilded(Forging.Option option) {
		return option.id().startsWith(PREFIX) && option.id().endsWith("/" + GILDED);
	}

	private static int gold(Inventory inventory) {
		int n = 0;
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack stack = inventory.getItem(i);
			if (stack.is(Items.GOLD_INGOT)) {
				n += stack.getCount();
			}
		}
		return n;
	}

	private static void take(Inventory inventory, int count) {
		for (int i = 0; i < inventory.getContainerSize() && count > 0; i++) {
			ItemStack stack = inventory.getItem(i);
			if (stack.is(Items.GOLD_INGOT)) {
				int taken = Math.min(count, stack.getCount());
				stack.shrink(taken);
				count -= taken;
			}
		}
	}

	private static List<Forging.Option> options() {
		List<Forging.Option> out = new ArrayList<>();
		DataComponentType<String> finish = finishComponent();
		for (int i = 0; i < PLATE.length; i++) {
			Item item = item(PLATE[i]);
			if (item == null) {
				continue;
			}
			for (String f : FINISHES) {
				ItemStack result = new ItemStack(item);
				if (!f.equals("bright") && finish != null) {
					result.set(finish, f);
					result.set(DataComponents.ITEM_NAME, Component.translatable("item.jaymoskingdoms.finish." + f, Component.translatable(item.getDescriptionId())));
				}
				// (the finish is the id's last part, so all three share the piece's blueprint: Blueprints.shape)
				out.add(new Forging.Option(PREFIX + PLATE[i] + "/" + f, result, Molten.STEEL, PLATE_INGOTS[i], Forging.hitsFor(PLATE_INGOTS[i])));
			}
		}
		for (int i = 0; i < MAIL.length; i++) {
			Item item = item(MAIL[i]);
			if (item != null) {
				out.add(new Forging.Option(PREFIX + MAIL[i] + "/iron", new ItemStack(item), Molten.IRON, MAIL_INGOTS[i], Forging.hitsFor(MAIL_INGOTS[i])));
			}
		}
		return out;
	}

	private static @Nullable Item item(String name) {
		return BuiltInRegistries.ITEM.getOptional(Identifier.fromNamespaceAndPath("jaymoskingdoms", name)).orElse(null);
	}

	@SuppressWarnings("unchecked")
	private static @Nullable DataComponentType<String> finishComponent() {
		return (DataComponentType<String>) BuiltInRegistries.DATA_COMPONENT_TYPE.getOptional(Identifier.fromNamespaceAndPath("jaymoskingdoms", "finish"))
			.orElse(null);
	}
}
