package com.jaymospowers.gear.arrow;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;

/**
 * The quiver key, on Jaymo's Kitchen's quiver (optional: this is the only place Powers touches its quiver classes, and every
 * call checks Kitchen is loaded). The quiver draws from its last stack, so cycling turns the top stack to the bottom and
 * the next kind of arrow comes up. Kitchen itself is unchanged.
 */
public final class QuiverCycle {
	private static final boolean LOADED = FabricLoader.getInstance().isModLoaded("jaymoskitchen");

	private QuiverCycle() {
	}

	/** Brings the next stack in the worn quiver to the top, and says what is there. */
	public static void cycle(ServerPlayer player) {
		if (!LOADED) {
			return;
		}
		ItemStack quiver = Kitchen.worn(player);
		if (quiver == null) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.quiver.none").withStyle(ChatFormatting.GRAY));
			return;
		}
		Kitchen.rotate(quiver);
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BUNDLE_INSERT, SoundSource.PLAYERS, 0.6F, 1.4F);
		player.sendOverlayMessage(Kitchen.top(quiver));
	}

	/** The only holder of Kitchen's quiver classes, loaded only when Kitchen is. */
	private static final class Kitchen {
		static ItemStack worn(ServerPlayer player) {
			return com.jaymoskitchen.quiver.Quivers.worn(player);
		}

		static void rotate(ItemStack quiver) {
			com.jaymoskitchen.quiver.QuiverContents contents = com.jaymoskitchen.quiver.QuiverItem.contents(quiver);
			if (contents.arrows().size() > 1) {
				List<ItemStackTemplate> turned = new ArrayList<>(contents.arrows());
				turned.add(0, turned.remove(turned.size() - 1));
				quiver.set(com.jaymoskitchen.quiver.Quivers.CONTENTS, new com.jaymoskitchen.quiver.QuiverContents(turned));
			}
		}

		static Component top(ItemStack quiver) {
			ItemStackTemplate top = com.jaymoskitchen.quiver.QuiverItem.contents(quiver).top();
			if (top == null) {
				return Component.translatable("message.jaymospowers.quiver.empty").withStyle(ChatFormatting.GRAY);
			}
			return Component.translatable("message.jaymospowers.quiver.top", top.count(), top.create().getHoverName()).withStyle(ChatFormatting.GOLD);
		}
	}
}
