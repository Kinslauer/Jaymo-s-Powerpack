package com.jaymospowers.tinkering.smithing;

import com.jaymospowers.tinkering.smeltery.Molten;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;
import org.jspecify.annotations.Nullable;

/**
 * (Smithing) Jaymo's Kingdoms' late-medieval harness, hammered out here when Kingdoms is in (its pieces are found by id;
 * without it there is nothing to forge): the eight helms, the cuirass, spaulders, arm harness, gauntlets, cuisses,
 * greaves and sabatons from steel, and the mail haubergeon and the mail and brigandine spaulders from iron; and (Harness 6, so
 * every place of the harness has at least three makes) the gorget, bevor, faulds and tassets, rondel spaulders and the
 * fur-lined limb pieces (each lined with {@link #FUR_HIDES} rabbit hides) from steel, and the jazerant, mail mitons, coif,
 * standard and skirt and the splinted vambraces and greaves from iron.
 * <p>
 * Every plate piece is offered in its three finishes, the smith choosing one as they start it: <b>bright</b>,
 * <b>blackened</b>, and <b>gilded</b>. Gilding is done only for a royal or a lord ({@link #noble}, told by Kingdoms),
 * and takes {@link #GILD_GOLD} gold ingots from the smith when the piece is done; anyone else is not offered it. The
 * finish rides on the piece as Kingdoms' {@code jaymoskingdoms:finish}. The barbutes can be painted too ({@link #PAINTABLE}).
 */
public final class HeraldryForging {
	/** The gold ingots a gilded piece takes. */
	static final int GILD_GOLD = 2;
	/** Each piece's item id, and the ingots it takes. */
	private static final String[] PLATE = {"frogmouth_helm", "houndskull_bascinet", "armet", "close_helm", "savoyard_helm", "sallet", "kettle_hat",
		"morion", "plate_cuirass", "plate_spaulders", "plate_arm_harness", "plate_gauntlets", "plate_cuisses", "plate_greaves", "plate_sabatons",
		// every harness place filled (Kingdoms' Harness 6): the wider makes and the snows' fur-lined pieces, and the bevor
		"gorget", "faulds_and_tassets", "rondel_spaulders", "bevor", "fur_lined_spaulders", "fur_lined_arm_harness", "fur_lined_gauntlets",
		"fur_lined_cuisses", "fur_lined_greaves",
		// (Mordhau harness 1) the barbute and the spangenhelm, which only knights wore till now, and the new helms: the
		// kettle's other cuts and its deep kin, the sallets, and the older and the far helms
		"barbute", "spangenhelm", "broad_kettle", "tall_kettle", "deep_kettle", "kettle_sallet", "archers_sallet", "long_sallet",
		"gallowglass_helm", "visored_barbute", "visored_great_helm", "flat_top_helm", "nasal_helm", "spired_helm", "spectacle_helm",
		"skullcap", "turban_helm",
		// (Kingdoms' Mordhau harness 2) the armet's wrapper
		"armet_wrapper"};
	private static final int[] PLATE_INGOTS = {5, 5, 5, 5, 5, 5, 4, 4, 8, 3, 4, 3, 4, 3, 4, 3, 4, 4, 3, 3, 4, 3, 4, 3,
		5, 4, 4, 4, 5, 5, 4, 5, 5, 6, 6, 5, 3, 4, 5, 2, 5, 3};
	private static final String[] MAIL = {"mail_haubergeon", "mail_spaulders", "brigandine_spaulders", "jazerant", "mail_mitons", "mail_coif",
		"standard_of_mail", "mail_skirt", "splinted_vambraces", "splinted_greaves",
		// (Kingdoms' Mordhau harness 2) the coif's cuts
		"lowered_mail_coif", "square_mail_coif", "ventailed_mail_coif", "square_ventailed_mail_coif"};
	private static final int[] MAIL_INGOTS = {6, 3, 3, 5, 2, 3, 2, 3, 2, 2, 3, 3, 4, 4};
	/** The hides a fur-lined piece takes from the smith when it is done (its lining): any of c:hides, rabbit hide or another. */
	static final int FUR_HIDES = 2;
	public static final TagKey<Item> HIDES = TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("c", "hides"));
	private static final String FUR_LINED = "fur_lined_";
	private static final String[] FINISHES = {"bright", "blackened", "gilded"};
	/** The pieces also offered painted (the barbute's painted finish, Mordhau harness 1): paint needs no gold or rank. */
	private static final java.util.Set<String> PAINTABLE = java.util.Set.of("barbute", "visored_barbute");
	private static final String PAINTED = "painted";
	/**
	 * (Kingdoms' Mordhau harness 3) More plate from steel, each with the ingots it takes (kept apart from the arrays above so their
	 * counts stay in step): the cuirasses that only knights wore, the new cuirasses, the large pauldrons and the mitten gauntlets.
	 */
	private static final Map<String, Integer> MORE_PLATE = new LinkedHashMap<>();
	/** More mail from iron: the long hauberk, mail with plates, the scale shirt. */
	private static final Map<String, Integer> MORE_MAIL = new LinkedHashMap<>();
	/** The finishes some pieces come in besides bright, blackened and gilded: the Italian's paint, the fluted cuirass's etching, the hussar's brass, the muscle cuirass's bronze, leather lamellar, browned munition plate. */
	private static final Map<String, List<String>> EXTRA_FINISHES = new LinkedHashMap<>();
	/** The plate that can be covered in cloth (the cuirasses, the sallets and the flat-topped helm), and the wool it takes (its colour is the wool's). */
	private static final java.util.Set<String> COVERABLE = java.util.Set.of("plate_cuirass", "rounded_breastplate", "fluted_cuirass", "gothic_cuirass",
		"kastenbrust", "peascod_cuirass", "sallet", "kettle_sallet", "archers_sallet", "flat_top_helm");
	static final int COVER_WOOL = 2;
	private static final String COVERED = "covered";
	static {
		MORE_PLATE.put("rounded_breastplate", 8);
		MORE_PLATE.put("fluted_cuirass", 8);
		MORE_PLATE.put("laced_lamellar", 7);
		MORE_PLATE.put("gothic_cuirass", 8);
		MORE_PLATE.put("kastenbrust", 8);
		MORE_PLATE.put("peascod_cuirass", 8);
		MORE_PLATE.put("hussar_cuirass", 8);
		MORE_PLATE.put("muscle_cuirass", 7);
		MORE_PLATE.put("embossed_cuirass", 8);
		MORE_PLATE.put("pauldrons", 5);
		MORE_PLATE.put("mitten_gauntlets", 3);
		// (Mordhau harness 4) the long tassets, the fluted cuisses, the schynbald and the scale sabatons
		MORE_PLATE.put("long_tassets", 6);
		MORE_PLATE.put("fluted_cuisses", 5);
		MORE_PLATE.put("schynbald", 2);
		MORE_PLATE.put("scale_sabatons", 4);
		MORE_MAIL.put("mail_hauberk", 8);
		MORE_MAIL.put("plated_mail", 8);
		MORE_MAIL.put("scale_shirt", 6);
		// (Mordhau harness 4) the scale and lamellar skirts, the brigandine cuisses, the mail chausses and the mail shoes
		MORE_MAIL.put("scale_skirt", 4);
		MORE_MAIL.put("lamellar_skirt", 5);
		MORE_MAIL.put("brigandine_cuisses", 3);
		MORE_MAIL.put("mail_chausses", 8);
		MORE_MAIL.put("mail_shoes", 3);
		EXTRA_FINISHES.put("rounded_breastplate", List.of(PAINTED));
		EXTRA_FINISHES.put("fluted_cuirass", List.of(PAINTED, "etched"));
		EXTRA_FINISHES.put("plate_cuirass", List.of("russeted"));
		EXTRA_FINISHES.put("laced_lamellar", List.of("hide"));
		EXTRA_FINISHES.put("gothic_cuirass", List.of("etched"));
		EXTRA_FINISHES.put("peascod_cuirass", List.of(PAINTED));
		EXTRA_FINISHES.put("hussar_cuirass", List.of("gilt_banded", PAINTED));
		EXTRA_FINISHES.put("muscle_cuirass", List.of("bronze"));
		EXTRA_FINISHES.put("embossed_cuirass", List.of(PAINTED, "etched"));
	}
	private static final String PREFIX = "heraldry/";
	private static final String GILDED = "gilded";

	/** Whether {@code smith} is a royal or a lord, for whom plate is gilded. Jaymo's Kingdoms sets this at server start; without it nobody is. */
	public static Predicate<ServerPlayer> noble = smith -> false;

	private HeraldryForging() {
	}

	static void register() {
		Forging.PROVIDERS.add(HeraldryForging::options);
		Forging.CHECKS.add((smith, anvil, option) -> furLined(option) && count(smith.getInventory(), s -> s.is(HIDES)) < FUR_HIDES
			? Component.translatable("message.jaymospowers.smithing.fur_lining", FUR_HIDES) : null);
		Forging.FINISHES.add((smith, anvil, option, result) -> {
			if (furLined(option) && !smith.getAbilities().instabuild) {
				take(smith.getInventory(), s -> s.is(HIDES), FUR_HIDES);
			}
		});
		Forging.CHECKS.add((smith, anvil, option) -> {
			if (!gilded(option)) {
				return null;
			}
			if (!noble.test(smith)) {
				return Component.translatable("message.jaymospowers.smithing.gilding_noble");
			}
			return count(smith.getInventory(), s -> s.is(Items.GOLD_INGOT)) < GILD_GOLD
				? Component.translatable("message.jaymospowers.smithing.gilding_gold", GILD_GOLD) : null;
		});
		Forging.FINISHES.add((smith, anvil, option, result) -> {
			if (gilded(option) && !smith.getAbilities().instabuild) {
				take(smith.getInventory(), s -> s.is(Items.GOLD_INGOT), GILD_GOLD);
			}
		});
		// (Mordhau harness 3) plate covered in cloth: the wool the smith carries covers it, and its colour is the piece's
		Forging.CHECKS.add((smith, anvil, option) -> covered(option) && count(smith.getInventory(), s -> s.is(ItemTags.WOOL)) < COVER_WOOL
			? Component.translatable("message.jaymospowers.smithing.covering", COVER_WOOL) : null);
		Forging.FINISHES.add((smith, anvil, option, result) -> {
			if (covered(option)) {
				for (int i = 0; i < smith.getInventory().getContainerSize(); i++) {
					ItemStack stack = smith.getInventory().getItem(i);
					if (stack.is(ItemTags.WOOL)) {
						result.set(DataComponents.DYED_COLOR, new DyedItemColor(woolColour(stack)));
						break;
					}
				}
				if (!smith.getAbilities().instabuild) {
					take(smith.getInventory(), s -> s.is(ItemTags.WOOL), COVER_WOOL);
				}
			}
		});
	}

	private static boolean covered(Forging.Option option) {
		return option.id().startsWith(PREFIX) && option.id().endsWith("/" + COVERED);
	}

	/** The colour of a block of wool (white for any that is not one of the sixteen). */
	private static int woolColour(ItemStack wool) {
		String name = BuiltInRegistries.ITEM.getKey(wool.getItem()).getPath();
		for (DyeColor dye : DyeColor.values()) {
			if (name.equals(dye.getName() + "_wool")) {
				return dye.getTextureDiffuseColor() & 0xFFFFFF;
			}
		}
		return 0xFFFFFF;
	}

	private static boolean gilded(Forging.Option option) {
		return option.id().startsWith(PREFIX) && option.id().endsWith("/" + GILDED);
	}

	/** What else {@code option} takes from the smith when it is done, each the choices for it: hides for a fur lining, gold for gilding (for JEI). */
	public static List<List<ItemStack>> extras(Forging.Option option) {
		List<List<ItemStack>> out = new ArrayList<>();
		if (furLined(option)) {
			List<ItemStack> hides = new ArrayList<>();
			BuiltInRegistries.ITEM.getTagOrEmpty(HIDES).forEach(item -> hides.add(new ItemStack(item, FUR_HIDES)));
			out.add(hides.isEmpty() ? List.of(new ItemStack(Items.RABBIT_HIDE, FUR_HIDES)) : hides);
		}
		if (gilded(option)) {
			out.add(List.of(new ItemStack(Items.GOLD_INGOT, GILD_GOLD)));
		}
		if (covered(option)) {
			List<ItemStack> wool = new ArrayList<>();
			BuiltInRegistries.ITEM.getTagOrEmpty(ItemTags.WOOL).forEach(item -> wool.add(new ItemStack(item, COVER_WOOL)));
			out.add(wool);
		}
		return out;
	}

	/** Whether {@code option} is gilded (done only for a royal or a lord). */
	public static boolean gildedOption(Forging.Option option) {
		return gilded(option);
	}

	private static boolean furLined(Forging.Option option) {
		return option.id().startsWith(PREFIX + FUR_LINED);
	}

	private static int count(Inventory inventory, Predicate<ItemStack> item) {
		int n = 0;
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack stack = inventory.getItem(i);
			if (item.test(stack)) {
				n += stack.getCount();
			}
		}
		return n;
	}

	private static void take(Inventory inventory, Predicate<ItemStack> item, int count) {
		for (int i = 0; i < inventory.getContainerSize() && count > 0; i++) {
			ItemStack stack = inventory.getItem(i);
			if (item.test(stack)) {
				int taken = Math.min(count, stack.getCount());
				stack.shrink(taken);
				count -= taken;
			}
		}
	}

	/**
	 * Everything the anvil forges of the harness (JEI's Smithing Anvil page reads it too: it builds the list fresh, so it
	 * never fills {@link Forging#all}'s cache early).
	 */
	public static List<Forging.Option> options() {
		List<Forging.Option> out = new ArrayList<>();
		DataComponentType<String> finish = finishComponent();
		for (int i = 0; i < PLATE.length; i++) {
			plate(out, finish, PLATE[i], PLATE_INGOTS[i]);
		}
		MORE_PLATE.forEach((name, ingots) -> plate(out, finish, name, ingots));
		for (int i = 0; i < MAIL.length; i++) {
			mail(out, MAIL[i], MAIL_INGOTS[i]);
		}
		MORE_MAIL.forEach((name, ingots) -> mail(out, name, ingots));
		return out;
	}

	/** One plate piece in each finish it comes in ({@code ingots} of steel each). */
	private static void plate(List<Forging.Option> out, @Nullable DataComponentType<String> finish, String name, int ingots) {
		Item item = item(name);
		if (item == null) {
			return;
		}
		List<String> finishes = new ArrayList<>(List.of(FINISHES));
		if (PAINTABLE.contains(name)) {
			finishes.add(PAINTED);
		}
		finishes.addAll(EXTRA_FINISHES.getOrDefault(name, List.of()));
		if (COVERABLE.contains(name)) {
			finishes.add(COVERED);
		}
		for (String f : finishes) {
			ItemStack result = new ItemStack(item);
			if (!f.equals("bright") && finish != null) {
				result.set(finish, f);
				result.set(DataComponents.ITEM_NAME, Component.translatable("item.jaymoskingdoms.finish." + f, Component.translatable(item.getDescriptionId())));
			}
			// (the finish is the id's last part, so all of them share the piece's blueprint: Blueprints.shape)
			out.add(new Forging.Option(PREFIX + name + "/" + f, result, Molten.STEEL, ingots, Forging.hitsFor(ingots)));
		}
	}

	private static void mail(List<Forging.Option> out, String name, int ingots) {
		Item item = item(name);
		if (item != null) {
			out.add(new Forging.Option(PREFIX + name + "/iron", new ItemStack(item), Molten.IRON, ingots, Forging.hitsFor(ingots)));
		}
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
