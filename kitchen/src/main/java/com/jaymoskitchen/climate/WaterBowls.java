package com.jaymoskitchen.climate;

import com.jaymoskitchen.JaymosKitchen;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.Consumables;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * Bowls of water: use an empty wooden bowl on still water (a source block of it, as a pond has) and it comes up full
 * ({@link #WATER_BOWL}). That water is as unclean as a pond bottle's until it's boiled over a fire ({@link Climate#boils},
 * {@link Climate#boil}), when it is a {@link #CLEAN_WATER_BOWL}. Drinking one gives its water ({@link Thirst#consumed}) and
 * gives the bowl back. They stack {@link #STACK} high, since a bowl holds less than a bottle. Scooping leaves the pond as
 * it was: a bowlful is nothing to a pond.
 */
public final class WaterBowls {
	/** How many bowls of water stack. */
	public static final int STACK = 4;

	public static Item WATER_BOWL;
	public static Item CLEAN_WATER_BOWL;

	private WaterBowls() {
	}

	static void register() {
		WATER_BOWL = bowl("water_bowl");
		CLEAN_WATER_BOWL = bowl("clean_water_bowl");
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FOOD_AND_DRINKS).register(output -> {
			output.accept(WATER_BOWL);
			output.accept(CLEAN_WATER_BOWL);
		});
		// an empty bowl, used looking at still water: the same look that fills a bottle or a waterskin
		UseItemCallback.EVENT.register((player, level, hand) -> {
			ItemStack held = player.getItemInHand(hand);
			if (!held.is(Items.BOWL) || player.isSpectator()) {
				return InteractionResult.PASS;
			}
			HitResult hit = player.pick(player.blockInteractionRange(), 0.0F, true);
			if (!(hit instanceof BlockHitResult block) || hit.getType() != HitResult.Type.BLOCK) {
				return InteractionResult.PASS;
			}
			BlockPos pos = block.getBlockPos();
			if (!level.mayInteract(player, pos) || !level.getFluidState(pos).is(FluidTags.WATER) || !level.getFluidState(pos).isSource()) {
				return InteractionResult.PASS;
			}
			if (!level.isClientSide()) {
				player.setItemInHand(hand, ItemUtils.createFilledResult(held, player, new ItemStack(WATER_BOWL)));
				level.playSound(null, pos, SoundEvents.BOTTLE_FILL, SoundSource.NEUTRAL, 1.0F, 1.1F);
				level.gameEvent(player, GameEvent.FLUID_PICKUP, pos);
			}
			return InteractionResult.SUCCESS;
		});
	}

	private static Item bowl(String id) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosKitchen.id(id));
		return Registry.register(BuiltInRegistries.ITEM, key, new Item(new Item.Properties().stacksTo(STACK)
			.component(DataComponents.CONSUMABLE, Consumables.DEFAULT_DRINK).usingConvertsTo(Items.BOWL).setId(key)));
	}

	/** A bowl of water, unclean or clean. */
	public static boolean isBowl(ItemStack stack) {
		return stack.is(WATER_BOWL) || stack.is(CLEAN_WATER_BOWL);
	}
}
