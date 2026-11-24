package com.jaymoskitchen.mixin;

import com.jaymoskitchen.corpse.PlayerCorpses;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.gamerules.GameRules;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.jaymoskitchen.quiver.Quivers;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileWeaponItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Bows and crossbows look in a quiver for arrows after the hands, before loose arrows in the inventory. */
@Mixin(Player.class)
abstract class PlayerMixin implements PlayerCorpses.Holder {
	@Unique
	private List<ItemStack> jaymoskitchen$hotbar = List.of();
	@Unique
	private int jaymoskitchen$selected;
	@Unique
	private List<Integer> jaymoskitchen$parts = new ArrayList<>();

	@Override
	public void jaymoskitchen$setHotbar(List<ItemStack> hotbar, int selected) {
		jaymoskitchen$hotbar = hotbar;
		jaymoskitchen$selected = selected;
		jaymoskitchen$parts = new ArrayList<>();
	}

	@Override
	public List<Integer> jaymoskitchen$parts() {
		return jaymoskitchen$parts;
	}

	@Override
	public List<ItemStack> jaymoskitchen$hotbar() {
		return jaymoskitchen$hotbar;
	}

	@Override
	public int jaymoskitchen$selected() {
		return jaymoskitchen$selected;
	}

	/**
	 * Dying: everything they carry stays with the body (see {@link PlayerCorpses}), slot for slot (inventory, armour, off
	 * hand), and nothing drops; it comes loose only if the body is blown apart.
	 */
	@Inject(method = "dropEquipment", at = @At("HEAD"))
	private void jaymoskitchen$keepHotbarOnBody(ServerLevel level, CallbackInfo ci) {
		Player self = (Player) (Object) this;
		if (level.getGameRules().get(GameRules.KEEP_INVENTORY) || self.isSpectator()
			|| self.entityTags().contains(com.jaymoskitchen.corpse.Corpses.NO_CORPSE_TAG)
			|| com.jaymoskitchen.corpse.Corpses.inNoCorpseZone(self)) {
			// (no body to keep it: a tagged player's things drop as usual)
			return;
		}
		Inventory inventory = self.getInventory();
		List<ItemStack> hotbar = new ArrayList<>();
		for (int i = 0; i < com.jaymoskitchen.corpse.CorpseEntity.INVENTORY_SLOTS; i++) {
			ItemStack stack = inventory.getItem(i);
			if (!stack.isEmpty() && EnchantmentHelper.has(stack, EnchantmentEffectComponents.PREVENT_EQUIPMENT_DROP)) {
				// curse of vanishing: gone, as it would be
				stack = ItemStack.EMPTY;
			}
			hotbar.add(stack.copy());
			inventory.setItem(i, ItemStack.EMPTY);
		}
		jaymoskitchen$setHotbar(hotbar, inventory.getSelectedSlot());
	}

	@Inject(
		method = "getProjectile",
		at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/item/ProjectileWeaponItem;getAllSupportedProjectiles()Ljava/util/function/Predicate;"),
		cancellable = true)
	private void jaymoskitchen$quiverAmmo(ItemStack heldWeapon, CallbackInfoReturnable<ItemStack> cir) {
		ItemStack ammo = Quivers.drawAmmo((Player) (Object) this,
			((ProjectileWeaponItem) heldWeapon.getItem()).getAllSupportedProjectiles());
		if (ammo != null) {
			cir.setReturnValue(ammo);
		}
	}
}
