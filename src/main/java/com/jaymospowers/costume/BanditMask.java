package com.jaymospowers.costume;

import java.util.Locale;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;

/**
 * (0.15.32) Jaymo's Kingdoms' bandit mask and bandana (found by id; Powers never loads Kingdoms' classes) hide the face
 * like a costume's mask: the wearer goes by "Bandit", or "Pirate" in or on the water, in a boat or aboard a ship, or by
 * the sea or a river (the same rule as Kingdoms' {@code Bandits.pirate}), and is tagged masked as the identity
 * {@link #key}, the key Kingdoms gives the same bandit.
 */
public final class BanditMask {
	private static final Identifier MASK = Identifier.fromNamespaceAndPath("jaymoskingdoms", "bandit_mask"),
		BANDANA = Identifier.fromNamespaceAndPath("jaymoskingdoms", "bandana");

	private BanditMask() {
	}

	/** {@code player} has a bandit mask or bandana on (either side). */
	public static boolean wears(Player player) {
		Item head = player.getItemBySlot(EquipmentSlot.HEAD).getItem();
		Identifier id = BuiltInRegistries.ITEM.getKey(head);
		return id.equals(MASK) || id.equals(BANDANA);
	}

	/** The identity a masked bandit is: theirs alone, whichever mask they have on. */
	public static String key(Player player) {
		return "bandit_" + player.getStringUUID().replace("-", "").toLowerCase(Locale.ROOT);
	}

	/** What a masked bandit is called where they are now. */
	public static Component name(Player player) {
		boolean pirate = player.isInWater() || player.getVehicle() != null && !(player.getVehicle() instanceof LivingEntity);
		if (!pirate) {
			var biome = player.level().getBiome(player.blockPosition());
			pirate = biome.is(BiomeTags.IS_OCEAN) || biome.is(BiomeTags.IS_BEACH) || biome.is(BiomeTags.IS_RIVER);
		}
		return Component.translatable(pirate ? "costume.jaymospowers.identity.pirate" : "costume.jaymospowers.identity.bandit");
	}
}
