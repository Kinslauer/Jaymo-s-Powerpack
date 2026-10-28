package com.jaymospowers.tinkering.fletching;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.tinkering.PartType;
import com.jaymospowers.tinkering.TinkerMaterial;
import com.jaymospowers.tinkering.Tinkering;
import com.jaymospowers.tinkering.ToolPartItem;
import com.jaymospowers.tinkering.ToolStats;
import com.jaymospowers.tinkering.ToolType;
import java.util.List;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.level.block.Blocks;
import org.jspecify.annotations.Nullable;

/**
 * The Fletching Station: the vanilla fletching table, which did nothing, opens a screen that puts arrows together.
 * <ul>
 * <li>flint + stick + feather: four arrows, as ever;</li>
 * <li>an iron or steel nugget in place of the flint: four iron or steel arrows (harder-hitting, {@link TieredArrowItem});</li>
 * <li>a Tinkers arrow head, shaft or fletching (with flint, sticks and feathers standing in for the rest): a Tinkers arrow
 * ({@code tinkering/ranged/TinkerArrowItem}), four at a time as at the Tool Station;</li>
 * <li>a lingering potion in the potion slot turns the flint arrows it makes into lingering arrows ({@link LingeringArrow}), or
 * dips up to {@link #ARROWS_PER_POTION} plain arrows put in the head slot.</li>
 * </ul>
 */
public final class Fletching {
	/** Arrows one set of head, shaft and fletching makes. */
	public static final int ARROWS_PER_SET = 4;
	/** Plain arrows one lingering potion dips. */
	public static final int ARROWS_PER_POTION = 8;

	public static Item IRON_ARROW, STEEL_ARROW, LINGERING_ARROW;
	public static MenuType<FletchingMenu> MENU;

	/** What taking the result uses: the result, and how much of each input goes. */
	public record Craft(ItemStack result, int head, int shaft, int fletching, int potion) {
	}

	private Fletching() {
	}

	public static void init() {
		IRON_ARROW = item("iron_arrow", p -> new TieredArrowItem(p, TieredArrowItem.Tier.IRON));
		STEEL_ARROW = item("steel_arrow", p -> new TieredArrowItem(p, TieredArrowItem.Tier.STEEL));
		LINGERING_ARROW = item("lingering_arrow", LingeringArrowItem::new);
		MENU = Registry.register(BuiltInRegistries.MENU, JaymosPowers.id("fletching"), new MenuType<>(FletchingMenu::new, FeatureFlags.VANILLA_SET));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.COMBAT).register(output -> {
			output.accept(IRON_ARROW);
			output.accept(STEEL_ARROW);
			output.accept(LINGERING_ARROW.getDefaultInstance());
		});
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (player.isSpectator() || !level.getBlockState(hit.getBlockPos()).is(Blocks.FLETCHING_TABLE)
				|| player.isSecondaryUseActive() && !player.getMainHandItem().isEmpty()) {
				return InteractionResult.PASS;
			}
			if (!level.isClientSide()) {
				ContainerLevelAccess access = ContainerLevelAccess.create(level, hit.getBlockPos());
				player.openMenu(new SimpleMenuProvider((id, inventory, p) -> new FletchingMenu(id, inventory, access),
					Component.translatable("gui.jaymospowers.fletching")));
			}
			return InteractionResult.SUCCESS;
		});
	}

	private static Item item(String id, java.util.function.Function<Item.Properties, Item> factory) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosPowers.id(id));
		return Registry.register(BuiltInRegistries.ITEM, key, factory.apply(new Item.Properties().setId(key)));
	}

	// --- what the station accepts ---

	public static boolean isHead(ItemStack stack) {
		return stack.is(Items.FLINT) || stack.is(Items.ARROW) || metalHead(stack) != null
			|| stack.getItem() instanceof ToolPartItem part && part.part.kind == PartType.Kind.ARROW_HEAD;
	}

	public static boolean isShaft(ItemStack stack) {
		return stack.is(Items.STICK) || stack.getItem() instanceof ToolPartItem part && part.part.kind == PartType.Kind.ARROW_SHAFT;
	}

	public static boolean isFletching(ItemStack stack) {
		return stack.is(Items.FEATHER) || stack.getItem() instanceof ToolPartItem part && part.part.kind == PartType.Kind.FLETCHING;
	}

	public static boolean isPotion(ItemStack stack) {
		return stack.is(Items.LINGERING_POTION);
	}

	/** The arrow an iron or steel nugget heads, or null. */
	private static @Nullable Item metalHead(ItemStack stack) {
		if (stack.is(Items.IRON_NUGGET)) {
			return IRON_ARROW;
		}
		return Tinkering.STEEL_NUGGET != null && stack.is(Tinkering.STEEL_NUGGET) ? STEEL_ARROW : null;
	}

	/** The Tinkers material of a part, or what a raw flint, stick or feather counts as. */
	private static @Nullable TinkerMaterial material(ItemStack stack, TinkerMaterial raw, Item rawItem, PartType.Kind kind) {
		if (stack.is(rawItem)) {
			return raw;
		}
		if (stack.getItem() instanceof ToolPartItem part && part.part.kind == kind) {
			TinkerMaterial material = ToolPartItem.material(stack);
			return material != null && material.makes(kind) ? material : null;
		}
		return null;
	}

	/** What these inputs make, or null. */
	public static @Nullable Craft craft(ItemStack head, ItemStack shaft, ItemStack fletching, ItemStack potion) {
		if (!potion.isEmpty() && !isPotion(potion)) {
			return null;
		}
		PotionContents contents = potion.get(DataComponents.POTION_CONTENTS);
		if (head.is(Items.ARROW)) {
			// plain arrows dipped
			if (potion.isEmpty() || contents == null || !shaft.isEmpty() || !fletching.isEmpty()) {
				return null;
			}
			int count = Math.min(head.getCount(), ARROWS_PER_POTION);
			return new Craft(lingering(contents, count), count, 0, 0, 1);
		}
		if (head.isEmpty() || shaft.isEmpty() || fletching.isEmpty()) {
			return null;
		}
		Item metal = metalHead(head);
		if (metal != null) {
			// metal arrows are plain: a stick and a feather
			return shaft.is(Items.STICK) && fletching.is(Items.FEATHER) && potion.isEmpty() ? new Craft(new ItemStack(metal, ARROWS_PER_SET), 1, 1, 1, 0)
				: null;
		}
		TinkerMaterial headMaterial = material(head, TinkerMaterial.FLINT, Items.FLINT, PartType.Kind.ARROW_HEAD);
		TinkerMaterial shaftMaterial = material(shaft, TinkerMaterial.WOOD, Items.STICK, PartType.Kind.ARROW_SHAFT);
		TinkerMaterial fletchMaterial = material(fletching, TinkerMaterial.FEATHER, Items.FEATHER, PartType.Kind.FLETCHING);
		if (headMaterial == null || shaftMaterial == null || fletchMaterial == null) {
			return null;
		}
		boolean raw = head.is(Items.FLINT) && shaft.is(Items.STICK) && fletching.is(Items.FEATHER);
		if (raw) {
			ItemStack out = contents != null ? lingering(contents, ARROWS_PER_SET) : new ItemStack(Items.ARROW, ARROWS_PER_SET);
			return new Craft(out, 1, 1, 1, contents != null ? 1 : 0);
		}
		if (!potion.isEmpty()) {
			return null;
		}
		ItemStack built = ToolStats.build(ToolType.ARROW, List.of(headMaterial, shaftMaterial, fletchMaterial));
		built.setCount(ToolType.ARROW.ranged.yield());
		return new Craft(built, 1, 1, 1, 0);
	}

	private static ItemStack lingering(PotionContents contents, int count) {
		ItemStack stack = new ItemStack(LINGERING_ARROW, count);
		stack.set(DataComponents.POTION_CONTENTS, contents);
		return stack;
	}
}
