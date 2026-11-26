package com.jaymospowers.baubles;

import com.jaymospowers.JaymosPowers;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/**
 * Belt charms against the weather (docs/Climate-Plan.md, cl-charm), worn in the bauble belt slot: the hot-stone pouch (a stone
 * warmed at the fire, wrapped in wool: warms), the cold-clay flask (clay kept in a stream: cools) and the rare equinox
 * charm (a stone and clay bound together: both). Jaymo's Kitchen's body heat counts each as warm and cool clothes are counted
 * ({@code BodyHeat.addWorn}, registered by {@link com.jaymospowers.compat.KitchenBodyHeat}, which is all of Kitchen this
 * class ever sees); without Kitchen they are only ornaments. The herbalists of Jaymo's Kingdoms' monasteries sell the pouch and
 * the flask; the equinox charm turns up only in the brothers' own chests.
 */
public final class ClimateCharms extends BaubleItem {
	/** Warm pieces and cool pieces each counts as (a wool shirt is one warm piece). */
	static final double POUCH_WARM = 1.0, FLASK_COOL = 1.0, EQUINOX_WARM = 1.0, EQUINOX_COOL = 1.0;

	public static Item POUCH, FLASK, EQUINOX;

	private final double warm, cool;

	private ClimateCharms(Properties properties, double warm, double cool) {
		super(properties, Baubles.Kind.BELT, List.of());
		this.warm = warm;
		this.cool = cool;
	}

	/** Weather charms need no bonding: tied on, they work. */
	@Override
	public boolean needsBond() {
		return false;
	}

	static void init() {
		POUCH = add("hot_stone_pouch", Rarity.COMMON, POUCH_WARM, 0.0);
		FLASK = add("cold_clay_flask", Rarity.COMMON, 0.0, FLASK_COOL);
		EQUINOX = add("equinox_charm", Rarity.RARE, EQUINOX_WARM, EQUINOX_COOL);
	}

	private static Item add(String id, Rarity rarity, double warm, double cool) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosPowers.id(id));
		return Baubles.add(Registry.register(BuiltInRegistries.ITEM, key, new ClimateCharms(new Item.Properties().setId(key).stacksTo(1).rarity(rarity), warm, cool)));
	}

	/** Warm pieces {@code player}'s belt charm counts as (0 with none). */
	public static double warm(Player player) {
		return Baubles.beltSlot(player).getItem() instanceof ClimateCharms charm ? charm.warm : 0.0;
	}

	/** Cool pieces {@code player}'s belt charm counts as (0 with none). */
	public static double cool(Player player) {
		return Baubles.beltSlot(player).getItem() instanceof ClimateCharms charm ? charm.cool : 0.0;
	}

	@Override
	public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
		builder.accept(Component.translatable("tooltip.jaymospowers.climate_charm." + BuiltInRegistries.ITEM.getKey(this).getPath()).withStyle(ChatFormatting.GOLD));
		super.appendHoverText(stack, context, display, builder, flag);
	}
}
