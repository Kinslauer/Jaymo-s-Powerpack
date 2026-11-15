package com.jaymoskingdoms;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/**
 * (gb-capes) A lord's, a king's and a knight's chest armour carries a cape in the realm's colours: its field, edged in
 * its charge. The colours ride on the stack ({@link #TAG}, as RGB), so the cape goes wherever the armour does; the
 * client hangs it from the shoulders as cloth that sways as its wearer moves ({@code CapeLayer}).
 */
public final class Capes {
	/** The stack's custom data key: {field RGB, edge RGB}. */
	public static final String TAG = "jaymoskingdoms_cape";

	private Capes() {
	}

	/** {@code chest}, with a cape in {@code arms}' colours (the same stack, for chaining). */
	static ItemStack cape(ItemStack chest, Outfits.Heraldry arms) {
		if (!chest.isEmpty()) {
			int[] colours = {Outfits.rgb(arms.field()), Outfits.rgb(arms.charge())};
			CustomData.update(DataComponents.CUSTOM_DATA, chest, tag -> tag.putIntArray(TAG, colours));
		}
		return chest;
	}

	/**
	 * {@code mob}, of {@code f}, wears a cape if it should (a king, a lord or a knight) and doesn't yet: put on after all its
	 * dressing (Epic Knights' pieces too), on folk already in the world as well ({@link Folk}), and on a ruler fitted
	 * ({@link HeraldryWear#ruler}). Only the cape is added; nothing else it wears changes.
	 */
	static void ensure(net.minecraft.world.entity.Mob mob, Faction f) {
		ensure(mob, f, false);
	}

	/** {@link #ensure}; {@code ruler}: {@code mob} has just been fitted as one (an MCA villager on the throne). */
	static void ensure(net.minecraft.world.entity.Mob mob, Faction f, boolean ruler) {
		net.minecraft.server.MinecraftServer server = mob.level().getServer();
		ItemStack chest = mob.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST);
		if (server == null || chest.isEmpty() || of(chest) != null || !caped(mob, ruler)) {
			return;
		}
		mob.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, cape(chest.copy(), Outfits.heraldry(f, server)));
	}

	/** {@code mob} is one who wears the realm's cape: a ruler, or a knight in the realm's cuirass. */
	private static boolean caped(net.minecraft.world.entity.Mob mob, boolean ruler) {
		if (ruler || mob instanceof Folk folk && (folk.role() == Folk.KING || folk.role() == Folk.LORD)) {
			return true;
		}
		HeraldryArmour.Piece piece = HeraldryArmour.of(mob.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST));
		return piece != null && piece.name().equals("plate_cuirass");
	}

	/** The cape's colours on {@code chest} ({field, edge} RGB), or null for none. */
	public static int @org.jspecify.annotations.Nullable [] of(ItemStack chest) {
		CustomData data = chest.get(DataComponents.CUSTOM_DATA);
		if (data == null) {
			return null;
		}
		int[] colours = data.copyTag().getIntArray(TAG).orElse(null);
		return colours != null && colours.length >= 2 ? colours : null;
	}
}
