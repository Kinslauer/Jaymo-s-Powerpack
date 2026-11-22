package com.jaymospowers.armory;

import java.util.LinkedHashMap;
import java.util.Map;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

/**
 * (Andy, 2026-11-20: "please remove this armor set") Knight Armor and Weapons' armour (the Knight Helmet, Chestplate,
 * Leggings and Boots) is out of the pack; its weapons stay (forged at the Smithing Anvil, {@code KnightArmorForging}).
 * The mod is linked untouched, so its items are still registered (old saves load, and its client code expects them):
 * <ul>
 * <li>none of the four is in a creative tab (the mod's tab shows its long sword instead of the helmet,
 * {@code mixin/RetiredKnightTabIconMixin}), the search tab or JEI ({@code client/jei/RetiredKnightArmourJei}); their
 * grid recipes were already switched off in data ({@code data/knightarmorweapons/recipe});</li>
 * <li>a piece that's still somewhere becomes the iron piece for the same slot, keeping its wear (as a share of its
 * durability), enchantments, name and trim: in a player's inventory, armour slots and ender chest, in any container a
 * player opens, worn or held by any mob or villager when it loads or comes near a player, and lying on the ground.</li>
 * </ul>
 */
public final class RetiredKnightArmour {
	/** Knight Armor and Weapons' mod id (and item namespace). */
	public static final String MOD = "knightarmorweapons";
	static final boolean LOADED = FabricLoader.getInstance().isModLoaded(MOD);

	/** The retired pieces (by path in the mod's namespace) and what each becomes. */
	private static final Map<String, Item> REPLACED = new LinkedHashMap<>();

	static {
		REPLACED.put("knight_helmet", Items.IRON_HELMET);
		REPLACED.put("knight_chestplate", Items.IRON_CHESTPLATE);
		REPLACED.put("knight_leggings", Items.IRON_LEGGINGS);
		REPLACED.put("knight_boots", Items.IRON_BOOTS);
	}

	/** The mod's creative tab, whose icon was the Knight Helmet, and the piece shown on it instead. */
	static final Identifier TAB = Identifier.fromNamespaceAndPath(MOD, "knight_armor_weapons_tab");
	static final Identifier TAB_ICON = Identifier.fromNamespaceAndPath(MOD, "knight_long_sword");
	private static @Nullable ItemStack tabIcon;

	/** How often (ticks) players' inventories, open containers and nearby mobs are looked over. */
	static final int SWEEP_TICKS = 40;
	/** How far (blocks) from a player mobs are looked over. */
	static final double SWEEP_RANGE = 48.0;

	private RetiredKnightArmour() {
	}

	public static void init() {
		if (!LOADED) {
			return;
		}
		CreativeModeTabEvents.MODIFY_OUTPUT_ALL.register((tab, output) -> {
			output.getDisplayStacks().removeIf(RetiredKnightArmour::retired);
			output.getSearchTabStacks().removeIf(RetiredKnightArmour::retired);
		});
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof LivingEntity living) {
				replaceWorn(living);
			} else if (entity instanceof ItemEntity item) {
				ItemStack instead = replacement(item.getItem());
				if (instead != null) {
					item.setItem(instead);
				}
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(RetiredKnightArmour::sweep);
	}

	/** The icon {@code tab} shows instead of its own (the long sword on the mod's tab), or null to keep its own. */
	public static @Nullable ItemStack tabIcon(CreativeModeTab tab) {
		if (!LOADED || !TAB.equals(BuiltInRegistries.CREATIVE_MODE_TAB.getKey(tab))) {
			return null;
		}
		if (tabIcon == null) {
			tabIcon = BuiltInRegistries.ITEM.getOptional(TAB_ICON).map(ItemStack::new).orElse(null);
		}
		return tabIcon;
	}

	/** Whether {@code stack} is one of the retired Knight armour pieces. */
	public static boolean retired(ItemStack stack) {
		return replacementItem(stack) != null;
	}

	private static @Nullable Item replacementItem(ItemStack stack) {
		if (stack.isEmpty()) {
			return null;
		}
		Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
		return MOD.equals(id.getNamespace()) ? REPLACED.get(id.getPath()) : null;
	}

	/**
	 * The iron piece {@code stack} becomes (its components carried over, its wear rescaled to the iron piece's
	 * durability), or null if it isn't a retired piece.
	 */
	public static @Nullable ItemStack replacement(ItemStack stack) {
		Item instead = replacementItem(stack);
		if (instead == null) {
			return null;
		}
		int oldMax = stack.getMaxDamage(), oldDamage = stack.getDamageValue();
		ItemStack out = stack.transmuteCopy(instead);
		// the knight piece's own durability doesn't carry over as a component patch, but its wear does: keep the share
		int newMax = out.getMaxDamage();
		if (newMax > 0) {
			out.setDamageValue(oldMax > 0 && oldDamage > 0 ? Mth.clamp(Math.round((float) oldDamage / oldMax * newMax), 1, newMax) : 0);
		}
		return out;
	}

	/** Swaps any retired piece {@code living} wears or holds. */
	static void replaceWorn(LivingEntity living) {
		for (EquipmentSlot slot : EquipmentSlot.VALUES) {
			ItemStack instead = replacement(living.getItemBySlot(slot));
			if (instead != null) {
				living.setItemSlot(slot, instead);
			}
		}
	}

	/** Swaps any retired piece in {@code container}. */
	static void replaceIn(Container container) {
		for (int i = 0; i < container.getContainerSize(); i++) {
			ItemStack instead = replacement(container.getItem(i));
			if (instead != null) {
				container.setItem(i, instead);
			}
		}
	}

	private static void sweep(MinecraftServer server) {
		if (server.getTickCount() % SWEEP_TICKS != 0) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			replaceIn(player.getInventory());
			replaceWorn(player);
			replaceIn(player.getEnderChestInventory());
			// whatever container the player has open (a chest, a barrel, a villager's or a horse's inventory)
			if (player.containerMenu != player.inventoryMenu) {
				for (Slot slot : player.containerMenu.slots) {
					ItemStack instead = replacement(slot.getItem());
					if (instead != null) {
						slot.set(instead);
					}
				}
			}
			ItemStack carried = replacement(player.containerMenu.getCarried());
			if (carried != null) {
				player.containerMenu.setCarried(carried);
			}
			for (LivingEntity living : player.level().getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(SWEEP_RANGE),
				living -> living != player)) {
				replaceWorn(living);
			}
		}
	}
}
