package com.jaymospowers.tinkering.dangers;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.baubles.BaubleItem;
import com.jaymospowers.baubles.Baubles;
import java.util.List;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;

/**
 * (GTNH picks, the forge's dangers) Smithing gloves: thick leather, worn on the wrist ({@link Baubles.Kind#WRIST}, where
 * the pack's gloves and claws go). Worn, they take the heat of hot metal, a pot off the fire, a lava bucket or a blaze
 * rod for the hands, as tongs do ({@code HotMetal}), and wear a point for it every
 * {@link com.jaymospowers.tinkering.smithing.HotMetal#TONGS_WEAR_TICKS}. Plain leather, so no bond to make.
 */
public class SmithingGloves extends BaubleItem {
	/** Uses before they wear through (wooden tongs have 120, iron 512). */
	public static final int USES = 240;
	public static Item ITEM;

	public SmithingGloves(Item.Properties properties) {
		super(properties, Baubles.Kind.WRIST, List.of());
	}

	static void register() {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosPowers.id("smithing_gloves"));
		ITEM = Registry.register(BuiltInRegistries.ITEM, key, new SmithingGloves(new Item.Properties().setId(key).durability(USES).rarity(Rarity.COMMON)));
	}

	@Override
	public boolean needsBond() {
		return false;
	}

	/** The gloves this player wears on the wrist (live), or empty. */
	public static ItemStack worn(Player player) {
		return ITEM == null ? ItemStack.EMPTY : Baubles.wornStack(player, ITEM);
	}

	/** A point of wear on the worn gloves; worn through, they're gone from the slot. */
	public static void wear(ServerPlayer player, ItemStack gloves) {
		gloves.hurtAndBreak(1, player.level(), player, broken -> {
			Baubles.setWorn(player, Baubles.worn(player));
			player.level().playSound(null, player.blockPosition(), SoundEvents.ITEM_BREAK.value(), SoundSource.PLAYERS, 0.8F, 0.9F);
		});
	}
}
