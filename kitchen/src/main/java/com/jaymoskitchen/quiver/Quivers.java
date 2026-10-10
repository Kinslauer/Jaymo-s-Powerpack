package com.jaymoskitchen.quiver;

import com.google.common.collect.MapMaker;
import com.jaymoskitchen.JaymosKitchen;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

/**
 * The quiver: its item and data component, which quiver a player is "wearing", feeding bows from it, and telling
 * everyone nearby what's in it so it can be drawn on the player's back.
 */
public final class Quivers {
	public static DataComponentType<QuiverContents> CONTENTS;
	public static Item QUIVER;

	/**
	 * The worn quiver's contents, synced to every client tracking the player (inventories aren't). Absent when the
	 * player carries no quiver; recomputed from the inventory every tick, so it's never saved.
	 */
	public static final AttachmentType<QuiverContents> WORN = AttachmentRegistry.create(JaymosKitchen.id("worn_quiver"),
		builder -> builder.syncWith(QuiverContents.STREAM_CODEC, AttachmentSyncPredicate.all()));

	/** Ammo handed to a bow from a quiver, and where it came from, so what the bow uses can be taken out. */
	private static final Map<ItemStack, Draw> DRAWS = new MapMaker().weakKeys().makeMap();

	private static final class Draw {
		final ItemStack quiver;
		final int index;
		int count;

		Draw(ItemStack quiver, int index, int count) {
			this.quiver = quiver;
			this.index = index;
			this.count = count;
		}
	}

	private Quivers() {
	}

	public static void register() {
		CONTENTS = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, JaymosKitchen.id("quiver_contents"),
			DataComponentType.<QuiverContents>builder()
				.persistent(QuiverContents.CODEC)
				.networkSynchronized(QuiverContents.STREAM_CODEC)
				.build());

		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosKitchen.id("quiver"));
		QUIVER = Registry.register(BuiltInRegistries.ITEM, key, new QuiverItem(new Item.Properties()
			.stacksTo(1)
			.component(CONTENTS, QuiverContents.EMPTY)
			.setId(key)));

		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.COMBAT)
			.register(output -> output.insertAfter(Items.ARROW, new ItemStack(QUIVER)));
	}

	/** The quiver a player wears: the off-hand one, else the first in their inventory. */
	public static @Nullable ItemStack worn(Player player) {
		if (player.getOffhandItem().getItem() instanceof QuiverItem) {
			return player.getOffhandItem();
		}
		Inventory inventory = player.getInventory();
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (stack.getItem() instanceof QuiverItem) {
				return stack;
			}
		}
		return null;
	}

	/**
	 * Arrows for a bow, from the top of the first quiver whose top arrows the bow can fire. The returned stack is a
	 * stand-in: the bow shrinks it as it fires, and {@link #afterUseAmmo} takes the same number out of the quiver.
	 */
	public static @Nullable ItemStack drawAmmo(Player player, Predicate<ItemStack> supported) {
		ItemStack offhand = player.getOffhandItem();
		ItemStack found = drawFrom(offhand, supported);
		if (found != null) {
			return found;
		}
		Inventory inventory = player.getInventory();
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			found = drawFrom(inventory.getItem(slot), supported);
			if (found != null) {
				return found;
			}
		}
		return null;
	}

	private static @Nullable ItemStack drawFrom(ItemStack quiver, Predicate<ItemStack> supported) {
		if (!(quiver.getItem() instanceof QuiverItem)) {
			return null;
		}
		QuiverContents contents = QuiverItem.contents(quiver);
		ItemStackTemplate top = contents.top();
		if (top == null) {
			return null;
		}
		ItemStack ammo = top.create();
		if (!supported.test(ammo)) {
			return null;
		}
		DRAWS.put(ammo, new Draw(quiver, contents.arrows().size() - 1, ammo.getCount()));
		return ammo;
	}

	/** After a bow or crossbow uses ammo: if it came from a quiver, take what was used out of that quiver. */
	public static void afterUseAmmo(ItemStack ammo) {
		Draw draw = DRAWS.get(ammo);
		if (draw == null) {
			return;
		}
		int used = draw.count - ammo.getCount();
		if (used <= 0) {
			return;
		}
		draw.count = ammo.getCount();
		QuiverContents contents = QuiverItem.contents(draw.quiver);
		List<ItemStackTemplate> arrows = contents.arrows();
		if (draw.index < arrows.size() && ItemStack.isSameItemSameComponents(arrows.get(draw.index).create(), ammo)) {
			draw.quiver.set(CONTENTS, contents.withRemoved(draw.index, used));
		}
	}

	/** Keeps every player's worn-quiver attachment in step with their inventory (and so with everyone's screens). */
	public static void tick(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			ItemStack quiver = worn(player);
			QuiverContents wearing = quiver == null ? null : QuiverItem.contents(quiver);
			AttachmentTarget target = (AttachmentTarget) player;
			if (!Objects.equals(wearing, target.getAttached(WORN))) {
				if (wearing == null) {
					target.removeAttached(WORN);
				} else {
					target.setAttached(WORN, wearing);
				}
			}
		}
	}
}
