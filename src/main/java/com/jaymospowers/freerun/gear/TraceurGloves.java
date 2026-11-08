package com.jaymospowers.freerun.gear;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.baubles.BaubleItem;
import com.jaymospowers.baubles.Baubles;
import com.jaymospowers.freerun.WallSlide;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.world.item.CreativeModeTabs;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;

/**
 * Traceur gloves (ParCool track B part 2): a wrist bauble ("gloves and claws on the wrist", like the
 * ArtifactBaubles' vampiric glove). Bare hands burn on a long wall slide
 * ({@link WallSlide#BURN_DAMAGE} damage every {@link WallSlide#BURN_TICKS} ticks); wearing these guards the hands
 * through {@link WallSlide#addHandGuard}, which {@link #init} registers once. Craftable, own texture.
 */
public class TraceurGloves extends BaubleItem {
	public static Item ITEM;

	public TraceurGloves(Item.Properties properties) {
		super(properties, Baubles.Kind.WRIST, List.of());
	}

	public static void init() {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosPowers.id("traceur_gloves"));
		ITEM = Registry.register(BuiltInRegistries.ITEM, key,
				new TraceurGloves(new Item.Properties().setId(key).stacksTo(1).rarity(Rarity.COMMON)));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES)
				.register(output -> output.accept(ITEM));
		WallSlide.addHandGuard(player -> Baubles.wears(player, ITEM));
	}

	@Override
	public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display,
		Consumer<Component> builder, TooltipFlag flag) {
		super.appendHoverText(stack, context, display, builder, flag);
		builder.accept(Component.translatable("item.jaymospowers.traceur_gloves.desc")
			.withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
	}

	/** Whether this player wears the traceur gloves on the wrist ({@link Baubles.Kind#WRIST}). */
	public static boolean wears(Player player) {
		return Baubles.wears(player, ITEM);
	}
}
