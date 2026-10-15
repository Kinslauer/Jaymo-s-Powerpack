package com.jaymospowers.power.chrono.future;

import com.jaymospowers.armory.TaczGuns;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityLevelChangeEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.ItemContainerContents;

/**
 * (0.10.106) Andy: the modern guns are for the future only. TaCZ's modern guns, their ammo and attachments
 * ({@link TaczGuns#isModern}) can't leave it: they're taken off anyone who goes home (inventory, ender chest, the
 * stack on the cursor, and inside shulker boxes and bundles), checked again every second (so a {@code /give} or a
 * chest raided elsewhere doesn't keep one), dropped ones outside the future vanish as they load, and mobs out there put
 * theirs down. In the creative tabs and JEI they never show at all ({@link TaczGuns#hideModern}).
 */
public final class FutureContraband {
	/** How often everyone outside the future is checked. */
	static final int EVERY = 20;

	private FutureContraband() {
	}

	static void init() {
		if (!TaczGuns.LOADED) {
			return;
		}
		ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL.register((player, from, to) -> {
			if (to.dimension() != Future.FUTURE) {
				strip(player);
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(FutureContraband::tick);
		ServerEntityEvents.ENTITY_LOAD.register(FutureContraband::onLoad);
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % EVERY != 0) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (player.level().dimension() != Future.FUTURE) {
				strip(player);
			}
		}
	}

	private static void onLoad(Entity entity, ServerLevel level) {
		if (level.dimension() == Future.FUTURE) {
			return;
		}
		if (entity instanceof ItemEntity item && TaczGuns.isModern(item.getItem())) {
			item.discard();
		} else if (entity instanceof Mob mob) {
			for (EquipmentSlot slot : EquipmentSlot.values()) {
				if (TaczGuns.isModern(mob.getItemBySlot(slot))) {
					mob.setItemSlot(slot, ItemStack.EMPTY);
				}
			}
		}
	}

	/** Takes every modern gun, round and attachment off a player. */
	static void strip(ServerPlayer player) {
		boolean took = strip(player.getInventory()) | strip(player.getEnderChestInventory());
		ItemStack carried = player.containerMenu.getCarried();
		if (TaczGuns.isModern(carried)) {
			player.containerMenu.setCarried(ItemStack.EMPTY);
			took = true;
		} else {
			took |= clean(carried);
		}
		if (took) {
			player.containerMenu.broadcastChanges();
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.future.guns_stay").withStyle(ChatFormatting.GOLD));
		}
	}

	private static boolean strip(Container container) {
		boolean took = false;
		for (int i = 0; i < container.getContainerSize(); i++) {
			ItemStack stack = container.getItem(i);
			if (TaczGuns.isModern(stack)) {
				container.setItem(i, ItemStack.EMPTY);
				took = true;
			} else {
				took |= clean(stack);
			}
		}
		return took;
	}

	/** Empties modern things out of a shulker box or bundle. Returns whether any were there. */
	private static boolean clean(ItemStack stack) {
		if (stack.isEmpty()) {
			return false;
		}
		ItemContainerContents box = stack.get(DataComponents.CONTAINER);
		if (box != null && box.nonEmptyItemCopyStream().anyMatch(TaczGuns::isModern)) {
			stack.set(DataComponents.CONTAINER, box.copyWithContents(box.itemCopies().map(s -> TaczGuns.isModern(s) ? ItemStack.EMPTY : s)));
			return true;
		}
		BundleContents bundle = stack.get(DataComponents.BUNDLE_CONTENTS);
		if (bundle != null && bundle.itemCopies().anyMatch(TaczGuns::isModern)) {
			stack.set(DataComponents.BUNDLE_CONTENTS, bundle.copyWithContents(bundle.itemCopies().filter(s -> !TaczGuns.isModern(s))));
			return true;
		}
		return false;
	}
}
