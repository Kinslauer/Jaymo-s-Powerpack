package com.jaymospowers.power.zfighter.farworld;

import com.jaymospowers.power.zfighter.Prospecting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** (0.18.55) A far world's first metal: knapping it (taking it from a crafting grid, the field anvil's too) is a first find for the Prospector career. */
public final class FirstMetalIngot extends Item {
	private final FarWorld world;

	public FirstMetalIngot(Item.Properties properties, FarWorld world) {
		super(properties);
		this.world = world;
	}

	@Override
	public void onCraftedBy(ItemStack stack, Player player) {
		super.onCraftedBy(stack, player);
		if (player instanceof ServerPlayer server) {
			Prospecting.found(server, this.world.key, "metal:" + BuiltInRegistries.ITEM.getKey(this));
		}
	}
}
