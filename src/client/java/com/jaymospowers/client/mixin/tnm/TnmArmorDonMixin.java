package com.jaymospowers.client.mixin.tnm;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * (0.17.35) Touch'n Motion's helmet gesture (optional, nothing of it copied) looped every two seconds in first person
 * (Andy: "kept touching my head"). Its ArmorDon reads what each player wears while the model draws, and First-person
 * Model answers {@code getItemBySlot(HEAD)} with nothing while it draws your own body, so the helmet seemed to come off and
 * go back on every frame. ArmorDon now reads the armour slots from the inventory, which First-person Model leaves alone.
 */
@Pseudo
@Mixin(targets = "strm.touchnmotion.gesture.ArmorDon", remap = false)
abstract class TnmArmorDonMixin {
	/** The inventory slot behind each equipment slot (36-39 for the armour, in 26.3). */
	@Unique
	private static final Map<EquipmentSlot, Integer> jaymospowers$SLOTS = new EnumMap<>(EquipmentSlot.class);

	static {
		for (Int2ObjectMap.Entry<EquipmentSlot> entry : Inventory.EQUIPMENT_SLOT_MAPPING.int2ObjectEntrySet()) {
			jaymospowers$SLOTS.put(entry.getValue(), entry.getIntKey());
		}
	}

	@WrapOperation(method = "watch", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/player/AbstractClientPlayer;getItemBySlot(Lnet/minecraft/world/entity/EquipmentSlot;)Lnet/minecraft/world/item/ItemStack;"), require = 0)
	private ItemStack jaymospowers$worn(AbstractClientPlayer player, EquipmentSlot slot, Operation<ItemStack> original) {
		Integer index = jaymospowers$SLOTS.get(slot);
		return index != null ? player.getInventory().getItem(index) : original.call(player, slot);
	}
}
