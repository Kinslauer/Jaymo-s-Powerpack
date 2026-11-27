package com.jaymospowers.alchemy.toxic;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.baubles.BaubleItem;
import com.jaymospowers.baubles.Baubles;
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
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/**
 * The toxin gauge (Hunter's Road, patch 6, "T-A4"): a brass instrument with a needle. Held in either hand, or worn in the
 * charm slot of the baubles, it shows the wearer's load, tolerance, the next threshold and mutagens ({@code ToxinGaugeHud});
 * it needs no bonding. Everything it shows is the synced {@link Toxicity#STATE}, so it sends nothing of its own.
 */
public final class ToxinGaugeItem extends BaubleItem {
	public static Item GAUGE;

	private ToxinGaugeItem(Properties properties) {
		super(properties, Baubles.Kind.CHARM, List.of());
	}

	public static void init() {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosPowers.id("toxin_gauge"));
		GAUGE = Baubles.add(Registry.register(BuiltInRegistries.ITEM, key, new ToxinGaugeItem(new Item.Properties().setId(key).stacksTo(1))));
	}

	@Override
	public boolean needsBond() {
		return false;
	}

	/** Whether {@code player} holds the gauge in a hand or wears it in the charm slot (both sides, for the local player). */
	public static boolean reads(Player player) {
		if (GAUGE == null) {
			return false;
		}
		if (player.getMainHandItem().is(GAUGE) || player.getOffhandItem().is(GAUGE)) {
			return true;
		}
		List<ItemStack> worn = player.getAttached(Baubles.WORN);
		if (worn == null) {
			return false;
		}
		for (int i = 0; i < worn.size() && i < Baubles.SIZE; i++) {
			if (Baubles.SLOTS[i] == Baubles.Kind.CHARM && worn.get(i).is(GAUGE)) {
				return true;
			}
		}
		return false;
	}

	@Override
	public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
		builder.accept(Component.translatable("tooltip.jaymospowers.toxin_gauge").withStyle(ChatFormatting.GOLD));
		super.appendHoverText(stack, context, display, builder, flag);
	}
}
