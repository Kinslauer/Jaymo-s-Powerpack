package com.jaymoskingdoms;

import java.util.function.Predicate;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;

/**
 * (0.7.98) OmniCrossbow (optional; LGPL-3.0, by ThePotatoArchivist; the pack ships its jar unchanged): load almost any
 * item into a crossbow from the off hand, and each flies its own way. In the pack it's a skill, not a treasure:
 * Survival's Make Do perk ({@link Skills.Perk#MAKE_DO}) lets any class's crossbow take anything, enchanted or not, and
 * the mod's Omni enchantment does nothing in a player's hands without it ({@code mixin/omni/PlayerProjectileMixin}).
 * Mobs (a raid's last pillagers) keep their Omni crossbows. Nothing of the mod's is called, so Kingdoms builds and runs
 * without it.
 */
public final class OmniCrossbows {
	public static final String MOD = "omnicrossbow";
	public static final boolean LOADED = FabricLoader.getInstance().isModLoaded(MOD);
	/** The mod's enchantment, by id. */
	static final String OMNI = MOD + ":omni";

	private OmniCrossbows() {
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
		for (var entry : weapon.getEnchantments().entrySet()) {
			Holder<Enchantment> enchantment = entry.getKey();
			if (enchantment.unwrapKey().map(key -> key.identifier().toString().equals(OMNI)).orElse(false)) {
				return true;
			}
		}
		return false;
	}
}
