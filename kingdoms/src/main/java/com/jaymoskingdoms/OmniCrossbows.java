package com.jaymoskingdoms;

import java.util.function.Predicate;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;

/**
 * (0.7.103) OmniCrossbow (optional; LGPL-3.0, by ThePotatoArchivist; the pack ships its jar unchanged): load almost any
 * item into a crossbow from the off hand, and each flies its own way. In the pack it's a skill, not a treasure:
 * Survival's Make Do perk ({@link Skills.Perk#MAKE_DO}) lets any class's crossbow take anything. The mod's Omni
 * enchantment is gone (Andy: "No omni enchant at all"): it's stripped from all loot (an Omni book becomes a plain book)
 * and from the crossbow of a raid's last pillager, and one that turns up anyway (an op's {@code /give}) does nothing in a
 * player's hands ({@code mixin/omni/PlayerProjectileMixin}). Nothing of the mod's is called, so Kingdoms builds and runs
 * without it.
 */
public final class OmniCrossbows {
	public static final String MOD = "omnicrossbow";
	public static final boolean LOADED = FabricLoader.getInstance().isModLoaded(MOD);
	/** The mod's enchantment, by id. */
	static final String OMNI = MOD + ":omni";

	private OmniCrossbows() {
	}

	static void register() {
		if (!LOADED) {
			return;
		}
		// vault rewards, chests, fishing, mob drops: every loot table
		LootTableEvents.MODIFY_DROPS.register((table, context, drops) -> drops.replaceAll(OmniCrossbows::strip));
		// a raid's last pillager is handed an Omni crossbow before it joins the world
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof Mob mob) {
				for (EquipmentSlot slot : EquipmentSlot.VALUES) {
					strip(mob.getItemBySlot(slot));
				}
			}
		});
	}

	/** {@code stack} without the Omni enchantment (changed in place); a book that had only Omni comes back plain. */
	static ItemStack strip(ItemStack stack) {
		if (stack.isEmpty() || !EnchantmentHelper.getEnchantmentsForCrafting(stack).keySet().stream().anyMatch(OmniCrossbows::omni)) {
			return stack;
		}
		ItemEnchantments left = EnchantmentHelper.updateEnchantments(stack, enchantments -> enchantments.removeIf(OmniCrossbows::omni));
		return stack.is(Items.ENCHANTED_BOOK) && left.isEmpty() ? stack.transmuteCopy(Items.BOOK) : stack;
	}

	/**
	 * What {@code player} may load into {@code weapon} from a hand, given what the game (and OmniCrossbow) would allow:
	 * anything but the crossbow itself with Make Do; without it, an Omni crossbow takes only what a plain one does.
	 */
	public static Predicate<ItemStack> heldProjectiles(Player player, ItemStack weapon, Predicate<ItemStack> allowed) {
		if (!LOADED || !(weapon.getItem() instanceof CrossbowItem crossbow)) {
			return allowed;
		}
		if (Skills.rank(player, Skills.Perk.MAKE_DO) > 0) {
			return projectile -> projectile != weapon;
		}
		return omni(weapon) ? crossbow.getSupportedHeldProjectiles() : allowed;
	}

	/** Whether {@code weapon} carries OmniCrossbow's Omni enchantment. */
	static boolean omni(ItemStack weapon) {
		for (Holder<Enchantment> enchantment : weapon.getEnchantments().keySet()) {
			if (omni(enchantment)) {
				return true;
			}
		}
		return false;
	}

	static boolean omni(Holder<Enchantment> enchantment) {
		return enchantment.unwrapKey().map(key -> key.identifier().toString().equals(OMNI)).orElse(false);
	}
}
