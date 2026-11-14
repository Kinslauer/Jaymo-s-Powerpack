package com.jaymospowers.tinkering.smithing;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.tinkering.kiln.Kiln;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.component.TooltipDisplay;

/**
 * The Forge Hammer, a smith's sidearm (drawn as the Forge Mace of cleannrooster's Frontier Armaments, MIT): a hammer
 * that fights like a sword ({@link #DAMAGE} over its metal's own, at {@link #SPEED}), breaks stone like a pickaxe of its
 * metal, and (Andy: "it also works for smithing") strikes a Smithing Anvil as a Smithing Hammer does. In four metals:
 * copper, iron and steel, forged like our other tools (a Forge Hammer Head, {@code tinkering/WeaponParts}, on a Tough
 * Handle with a binding), and netherite, a steel one taken to the smithing table. Steel and netherite ones work the
 * hard metals a steel Smithing Hammer does ({@link SmithTiers}).
 */
public final class ForgeHammers {
	/** Its blow over its metal's bonus, and its swing speed (a sword's are 3 and -2.4: heavier, slower). */
	static final float DAMAGE = 4.0F, SPEED = -2.9F;
	/** Steel's uses, mining speed and bonus damage (between iron's and netherite's). */
	static final int STEEL_USES = 1000;
	static final float STEEL_SPEED = 7.0F, STEEL_BONUS = 2.5F;

	private static final ToolMaterial STEEL = new ToolMaterial(BlockTags.INCORRECT_FOR_IRON_TOOL, STEEL_USES, STEEL_SPEED, STEEL_BONUS, 14,
		TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("c", "ingots/steel")));

	public static Item COPPER, IRON, STEEL_HAMMER, NETHERITE;

	private ForgeHammers() {
	}

	static void register() {
		COPPER = Smithing.item("copper_forge_hammer", Hammer::new, p -> p.pickaxe(ToolMaterial.COPPER, DAMAGE, SPEED));
		IRON = Smithing.item("iron_forge_hammer", Hammer::new, p -> p.pickaxe(ToolMaterial.IRON, DAMAGE, SPEED));
		STEEL_HAMMER = Smithing.item("steel_forge_hammer", Hammer::new, p -> p.pickaxe(STEEL, DAMAGE, SPEED));
		NETHERITE = Smithing.item("netherite_forge_hammer", Hammer::new, p -> p.pickaxe(ToolMaterial.NETHERITE, DAMAGE, SPEED).fireResistant());
		CreativeModeTabEvents.modifyOutputEvent(Kiln.tab()).register(output -> {
			output.accept(COPPER);
			output.accept(IRON);
			output.accept(STEEL_HAMMER);
			output.accept(NETHERITE);
		});
	}

	/** Whether {@code stack} is a Forge Hammer hard enough for steel-class metals. */
	static boolean hard(ItemStack stack) {
		return stack.is(STEEL_HAMMER) || stack.is(NETHERITE);
	}

	/** A Smithing Hammer that is also a weapon and a pick. */
	static final class Hammer extends SmithingHammerItem {
		Hammer(Item.Properties properties) {
			super(properties);
		}

		@Override
		@SuppressWarnings("deprecation")
		public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder,
			TooltipFlag flag) {
			builder.accept(Component.translatable("tooltip.jaymospowers.forge_hammer").withStyle(ChatFormatting.GRAY));
			super.appendHoverText(stack, context, display, builder, flag);
		}
	}
}
