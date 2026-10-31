package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.439) Havens patch 6, second half: wares only havens make (ideas from the havens pick list; our own code and art).
 * <ul>
 * <li><b>Made in havens:</b> a haven's traders (any villager with a trade who lives in one) sell their blood's wares
 * beside their usual offers: Tideborn pearls and deep fish, Stoneborn cut gems and stone tools, Sylvan herbs and bows,
 * Warrior-born hides and war gear. A mixed haven's trader sells one blood's, fixed by who they are.</li>
 * <li><b>Human towns pay well:</b> a trader in a settlement that isn't a haven buys the haven goods a player carries
 * (those their trade has a use for) at about twice what the haven asks.</li>
 * </ul>
 * The offers are added when a trade opens ({@code VillagerPriceMixin}), once each.
 */
public final class HavenWares {
	/** What a haven asks (emeralds): pearl, deep fish (two), cut gem, herbs (three), hide (two), stone tool, bow, war jerkin. */
	static final int PEARL_PRICE = 4, FISH_PRICE = 1, GEM_PRICE = 6, HERB_PRICE = 1, HIDE_PRICE = 2, TOOL_PRICE = 4, BOW_PRICE = 6, JERKIN_PRICE = 7;
	/** How many come for the price: deep fish, herbs, hides. */
	static final int FISH_COUNT = 2, HERB_COUNT = 3, HIDE_COUNT = 2;
	/** What a human town pays for one (emeralds): pearl, deep fish, cut gem, herbs, hide. */
	static final int PEARL_PAID = 9, FISH_PAID = 2, GEM_PAID = 12, HERB_PAID = 2, HIDE_PAID = 3;
	/** Uses before an offer runs out (it restocks as the trader's others do); trader's xp per trade. */
	static final int SELL_USES = 8, BUY_USES = 12, XP = 5;
	static final float PRICE_MULT = 0.05F;
	/** Enchantment levels on a Stoneborn tool (efficiency, unbreaking) and a Sylvan bow (power); a war jerkin's protection. */
	static final int TOOL_EFFICIENCY = 2, TOOL_UNBREAKING = 2, BOW_POWER = 2, JERKIN_PROTECTION = 2;
	/** A war jerkin's dye: dark red. */
	static final int JERKIN_COLOUR = 0x7A1F1A;
	/** Deep fish as food: nutrition, saturation. */
	static final int FISH_NUTRITION = 7;
	static final float FISH_SATURATION = 0.9F;
	/** The key a haven-made stack (tool, bow, gear) keeps its blood under. */
	static final String WARE_KEY = "jaymoskingdoms_haven_ware";

	public static Item TIDE_PEARL, DEEP_FISH, CUT_GEM, SYLVAN_HERBS, WAR_HIDE;

	/** Who in a human town buys each good, by trade. */
	private static Map<Item, List<String>> buyers;

	private HavenWares() {
	}

	static void register() {
		TIDE_PEARL = item("tide_pearl", new Item.Properties().rarity(Rarity.UNCOMMON));
		DEEP_FISH = item("deep_fish", new Item.Properties()
			.food(new FoodProperties.Builder().nutrition(FISH_NUTRITION).saturationModifier(FISH_SATURATION).build()));
		CUT_GEM = item("cut_gem", new Item.Properties().rarity(Rarity.UNCOMMON));
		SYLVAN_HERBS = item("sylvan_herbs", new Item.Properties());
		WAR_HIDE = item("war_hide", new Item.Properties());
		buyers = Map.of(
			TIDE_PEARL, List.of("cleric", "librarian", "cartographer", "armorer"),
			DEEP_FISH, List.of("fisherman", "butcher"),
			CUT_GEM, List.of("toolsmith", "weaponsmith", "armorer", "cleric", "mason"),
			SYLVAN_HERBS, List.of("cleric", "farmer"),
			WAR_HIDE, List.of("leatherworker", "butcher"));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.INGREDIENTS).register(output -> {
			for (Item item : List.of(TIDE_PEARL, CUT_GEM, SYLVAN_HERBS, WAR_HIDE)) {
				output.accept(item);
			}
		});
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FOOD_AND_DRINKS).register(output -> output.accept(DEEP_FISH));
	}

	private static Item item(String name, Item.Properties properties) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosKingdoms.id(name));
		return Registry.register(BuiltInRegistries.ITEM, key, new Ware(properties.setId(key), name));
	}

	/** A haven good: its tooltip says where it comes from. */
	static final class Ware extends Item {
		private final String name;

		Ware(Item.Properties properties, String name) {
			super(properties);
			this.name = name;
		}

		@Override
		public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
			builder.accept(Component.translatable("item.jaymoskingdoms." + this.name + ".hint").withStyle(ChatFormatting.GRAY));
		}
	}

	// ---- offers --------------------------------------------------------------------------------------------------------

	/** (from {@code VillagerPriceMixin}, when {@code player} opens a trade with {@code villager}) the haven offers, once each. */
	public static void offers(AbstractVillager villager, Player player) {
		if (!(villager.level() instanceof ServerLevel level) || !(villager instanceof Villager v) || v.isBaby()) {
			return;
		}
		String job = BuiltInRegistries.VILLAGER_PROFESSION.getKey(v.getVillagerData().profession().value()).getPath();
		if (job.equals("none") || job.equals("nitwit")) {
			return;
		}
		Faction haven = Havens.home(v);
		if (haven != null) {
			sell(level, v, haven);
			return;
		}
		Faction town = Quests.settlement(level, v);
		if (town != null && !Havens.is(level.getServer(), town) && town.type() != FactionType.BARBARIAN) {
			buy(v, job, player);
		}
	}

	/** {@code v}, a trader of haven {@code f}: their blood's wares, if not already offered. */
	private static void sell(ServerLevel level, Villager v, Faction f) {
		String blood = Havens.of(level.getServer(), f);
		if (Havens.MIXED.equals(blood)) {
			String[] four = {Heritage.Kind.TIDEBORN.key(), Heritage.Kind.STONEBORN.key(), Heritage.Kind.SYLVAN.key(), Heritage.Kind.WARRIORBORN.key()};
			blood = four[Math.floorMod(v.getUUID().hashCode(), 4)];
		}
		List<MerchantOffer> wares = new ArrayList<>();
		if (blood.equals(Heritage.Kind.TIDEBORN.key())) {
			wares.add(sale(PEARL_PRICE, new ItemStack(TIDE_PEARL)));
			wares.add(sale(FISH_PRICE, new ItemStack(DEEP_FISH, FISH_COUNT)));
		} else if (blood.equals(Heritage.Kind.STONEBORN.key())) {
			wares.add(sale(GEM_PRICE, new ItemStack(CUT_GEM)));
			wares.add(sale(TOOL_PRICE, tool(level, v.getRandom().nextBoolean() ? Items.STONE_PICKAXE : Items.STONE_AXE, blood)));
		} else if (blood.equals(Heritage.Kind.SYLVAN.key())) {
			wares.add(sale(HERB_PRICE, new ItemStack(SYLVAN_HERBS, HERB_COUNT)));
			wares.add(sale(BOW_PRICE, bow(level, blood)));
		} else if (blood.equals(Heritage.Kind.WARRIORBORN.key())) {
			wares.add(sale(HIDE_PRICE, new ItemStack(WAR_HIDE, HIDE_COUNT)));
			wares.add(sale(JERKIN_PRICE, jerkin(level, blood)));
		}
		for (MerchantOffer ware : wares) {
			if (v.getOffers().stream().noneMatch(o -> same(o.getResult(), ware.getResult()))) {
				v.getOffers().add(ware);
			}
		}
	}

	/** {@code v}, a trader of a human town: buys the haven goods {@code player} carries that {@code job} has a use for. */
	private static void buy(Villager v, String job, Player player) {
		for (Map.Entry<Item, List<String>> e : buyers.entrySet()) {
			Item good = e.getKey();
			if (!e.getValue().contains(job) || !player.getInventory().contains(s -> s.is(good))
				|| v.getOffers().stream().anyMatch(o -> o.getBaseCostA().is(good))) {
				continue;
			}
			v.getOffers().add(new MerchantOffer(new ItemCost(good, 1), new ItemStack(Items.EMERALD, paid(good)), BUY_USES, XP, PRICE_MULT));
		}
	}

	static int paid(Item good) {
		return good == TIDE_PEARL ? PEARL_PAID : good == DEEP_FISH ? FISH_PAID : good == CUT_GEM ? GEM_PAID : good == SYLVAN_HERBS ? HERB_PAID : HIDE_PAID;
	}

	private static MerchantOffer sale(int emeralds, ItemStack ware) {
		return new MerchantOffer(new ItemCost(Items.EMERALD, emeralds), ware, SELL_USES, XP, PRICE_MULT);
	}

	/** The same ware: the same item, and for made goods the same haven mark. */
	private static boolean same(ItemStack a, ItemStack b) {
		return a.is(b.getItem()) && blood(a).equals(blood(b));
	}

	private static String blood(ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		return data == null ? "" : data.copyTag().getStringOr(WARE_KEY, "");
	}

	private static ItemStack made(ItemStack stack, String blood, String name) {
		CompoundTag tag = new CompoundTag();
		tag.putString(WARE_KEY, blood);
		stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		stack.set(DataComponents.ITEM_NAME, Component.translatable("item.jaymoskingdoms.haven_ware." + name));
		return stack;
	}

	private static ItemStack tool(ServerLevel level, Item kind, String blood) {
		ItemStack tool = made(new ItemStack(kind), blood, kind == Items.STONE_AXE ? "stone_axe" : "stone_pick");
		Relics.enchantment(level, Enchantments.EFFICIENCY).ifPresent(h -> tool.enchant(h, TOOL_EFFICIENCY));
		Relics.enchantment(level, Enchantments.UNBREAKING).ifPresent(h -> tool.enchant(h, TOOL_UNBREAKING));
		return tool;
	}

	private static ItemStack bow(ServerLevel level, String blood) {
		ItemStack bow = made(new ItemStack(Items.BOW), blood, "bow");
		Relics.enchantment(level, Enchantments.POWER).ifPresent(h -> bow.enchant(h, BOW_POWER));
		return bow;
	}

	private static ItemStack jerkin(ServerLevel level, String blood) {
		ItemStack jerkin = made(new ItemStack(Items.LEATHER_CHESTPLATE), blood, "jerkin");
		jerkin.set(DataComponents.DYED_COLOR, new DyedItemColor(JERKIN_COLOUR));
		Relics.enchantment(level, Enchantments.PROTECTION).ifPresent(h -> jerkin.enchant(h, JERKIN_PROTECTION));
		return jerkin;
	}

	/** Whether {@code stack} is a haven good a human town buys. */
	static boolean good(@Nullable ItemStack stack) {
		return stack != null && buyers.containsKey(stack.getItem());
	}
}
