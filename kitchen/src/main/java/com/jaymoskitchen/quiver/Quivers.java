package com.jaymoskitchen.quiver;

import com.google.common.collect.MapMaker;
import com.jaymoskitchen.JaymosKitchen;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.component.TypedDataComponent;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.component.ContainerComponent;
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
	 * The quiver shown on the player's back and what's in it, synced to every client tracking the player (inventories
	 * aren't). Absent when the player shows no quiver; recomputed every tick, so it's never saved.
	 */
	public static final AttachmentType<WornQuiver> WORN = AttachmentRegistry.create(JaymosKitchen.id("worn_quiver"),
		builder -> builder.syncWith(WornQuiver.STREAM_CODEC, AttachmentSyncPredicate.all()));

	/**
	 * (0.5.81) Other mods' quivers drawn on the back when worn in a bauble slot (the RPG Series' small, medium and large
	 * quivers). Ours is drawn from anywhere in the inventory as well.
	 */
	public static final TagKey<Item> SHOWN_QUIVERS = TagKey.create(Registries.ITEM, JaymosKitchen.id("shown_quivers"));

	/** Jaymo's Powers' {@code Baubles.worn(Player)}, looked up once (null when Powers isn't there). */
	private static @Nullable MethodHandle wornBaubles;
	private static boolean lookedForBaubles;

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

	/**
	 * What the player wears in Jaymo's Powers' bauble slots (a quiver goes in the body slot), empty when Powers isn't
	 * there. Works on the server, and on the client for the local player (the slots sync to their wearer).
	 */
	@SuppressWarnings("unchecked")
	public static List<ItemStack> baubles(Player player) {
		if (!lookedForBaubles) {
			lookedForBaubles = true;
			if (FabricLoader.getInstance().isModLoaded("jaymospowers")) {
				try {
					wornBaubles = MethodHandles.publicLookup().findStatic(Class.forName("com.jaymospowers.baubles.Baubles"), "worn",
						MethodType.methodType(List.class, Player.class));
				} catch (ReflectiveOperationException | LinkageError e) {
					JaymosKitchen.LOGGER.warn("Couldn't find Jaymo's Powers' bauble slots; quivers won't be read from them", e);
				}
			}
		}
		if (wornBaubles == null) {
			return List.of();
		}
		try {
			return (List<ItemStack>) wornBaubles.invoke(player);
		} catch (Throwable e) {
			return List.of();
		}
	}

	/** (0.5.81) Our quiver worn in a bauble slot, if any. */
	private static @Nullable ItemStack baubleQuiver(Player player) {
		for (ItemStack stack : baubles(player)) {
			if (stack.getItem() instanceof QuiverItem) {
				return stack;
			}
		}
		return null;
	}

	/** The quiver a player wears: one in a bauble slot, else the off-hand one, else the first in their inventory. */
	public static @Nullable ItemStack worn(Player player) {
		ItemStack bauble = baubleQuiver(player);
		if (bauble != null) {
			return bauble;
		}
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
		ItemStack bauble = baubleQuiver(player);
		ItemStack found = bauble == null ? null : drawFrom(bauble, supported);
		if (found != null) {
			return found;
		}
		ItemStack offhand = player.getOffhandItem();
		found = drawFrom(offhand, supported);
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

	/**
	 * (0.5.81) The quiver shown on a player's back: any quiver worn in a bauble slot (ours or another mod's), else ours
	 * carried in the off hand or inventory.
	 */
	static @Nullable WornQuiver shown(Player player) {
		for (ItemStack stack : baubles(player)) {
			if (stack.getItem() instanceof QuiverItem) {
				return new WornQuiver(BuiltInRegistries.ITEM.getKey(stack.getItem()), QuiverItem.contents(stack));
			}
			if (!stack.isEmpty() && stack.is(SHOWN_QUIVERS)) {
				return new WornQuiver(BuiltInRegistries.ITEM.getKey(stack.getItem()), containerArrows(stack));
			}
		}
		ItemStack quiver = worn(player);
		return quiver == null ? null : new WornQuiver(BuiltInRegistries.ITEM.getKey(quiver.getItem()), QuiverItem.contents(quiver));
	}

	/** The arrows in another mod's quiver: whatever container component it keeps them in (Bundle API's, for the RPG Series). */
	private static QuiverContents containerArrows(ItemStack stack) {
		QuiverContents contents = QuiverContents.EMPTY;
		for (TypedDataComponent<?> component : stack.getComponents()) {
			if (component.value() instanceof ContainerComponent<?> container) {
				for (ItemStack arrow : (Iterable<ItemStack>) container.itemCopies()::iterator) {
					if (QuiverContents.canHold(arrow)) {
						contents = contents.withAdded(arrow, arrow.getCount());
					}
				}
			}
		}
		return contents;
	}

	/** Keeps every player's worn-quiver attachment in step with their inventory (and so with everyone's screens). */
	public static void tick(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			WornQuiver wearing = shown(player);
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
