package com.jaymoskitchen.mixin;

import com.jaymoskitchen.corpse.CorpseCapture;
import com.jaymoskitchen.corpse.Corpses;
import com.jaymoskitchen.corpse.PlayerCorpses;
import net.minecraft.server.level.ServerPlayer;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityEquipment;
import net.minecraft.world.entity.EquipmentSlot;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mobs that leave corpses: their death loot is caught (see {@code EntityMixin}) instead of scattered, and when the
 * mob starts to die, it is swapped for its corpse (a ragdoll) instead of vanishing in a puff of smoke.
 */
@Mixin(LivingEntity.class)
abstract class LivingEntityMixin implements CorpseCapture {
	@Shadow
	@Final
	protected EntityEquipment equipment;

	@Unique
	private @Nullable List<ItemStack> jaymoskitchen$loot;
	@Unique
	private boolean jaymoskitchen$capturing;
	/** While capturing: what the mob had on as it died (untouched copies), and the slots vanilla tried to drop. */
	@Unique
	private @Nullable Map<EquipmentSlot, ItemStack> jaymoskitchen$worn;
	@Unique
	private @Nullable List<EquipmentSlot> jaymoskitchen$keptOn;

	@Override
	public boolean jaymoskitchen$isCapturing() {
		return jaymoskitchen$capturing;
	}

	@Override
	public void jaymoskitchen$capture(ItemStack stack) {
		// a piece of its own gear (vanilla's equipment drop, at its drop chance): it stays on the body instead, and comes
		// off by hand like the rest of its gear (CorpseEntity#takeGear), so it isn't in the loot as well
		if (jaymoskitchen$worn != null) {
			LivingEntity self = (LivingEntity) (Object) this;
			for (EquipmentSlot slot : jaymoskitchen$worn.keySet()) {
				if (self.getItemBySlot(slot) == stack) {
					if (jaymoskitchen$keptOn == null) {
						jaymoskitchen$keptOn = new ArrayList<>();
					}
					jaymoskitchen$keptOn.add(slot);
					return;
				}
			}
		}
		if (jaymoskitchen$loot == null) {
			jaymoskitchen$loot = new ArrayList<>();
		}
		jaymoskitchen$loot.add(stack.copy());
	}

	@Inject(method = "dropAllDeathLoot", at = @At("HEAD"))
	private void jaymoskitchen$startCapture(ServerLevel level, DamageSource source, CallbackInfo ci) {
		LivingEntity self = (LivingEntity) (Object) this;
		jaymoskitchen$capturing = Corpses.leavesCorpse(self);
		jaymoskitchen$worn = null;
		jaymoskitchen$keptOn = null;
		if (jaymoskitchen$capturing) {
			jaymoskitchen$worn = new EnumMap<>(EquipmentSlot.class);
			for (EquipmentSlot slot : EquipmentSlot.VALUES) {
				ItemStack stack = self.getItemBySlot(slot);
				if (!stack.isEmpty()) {
					jaymoskitchen$worn.put(slot, stack.copy());
				}
			}
		}
	}

	@Inject(method = "dropAllDeathLoot", at = @At("RETURN"))
	private void jaymoskitchen$stopCapture(ServerLevel level, DamageSource source, CallbackInfo ci) {
		jaymoskitchen$capturing = false;
		if (jaymoskitchen$worn != null && jaymoskitchen$keptOn != null) {
			// put back what vanilla took off to drop (and had already knocked about), as it was; straight into the
			// equipment, so there's no equip sound from a body
			for (EquipmentSlot slot : jaymoskitchen$keptOn) {
				equipment.set(slot, jaymoskitchen$worn.get(slot).copy());
			}
		}
		jaymoskitchen$worn = null;
		jaymoskitchen$keptOn = null;
	}

	/**
	 * (0.5.1) The body appears on the first tick of dying, not after the death animation: it falls and crumples as a
	 * ragdoll from the start, so the mob is swapped for it at once (no puff).
	 */
	@Inject(method = "tickDeath", at = @At("HEAD"), cancellable = true)
	private void jaymoskitchen$leaveCorpse(CallbackInfo ci) {
		LivingEntity self = (LivingEntity) (Object) this;
		if (self.deathTime != 0) {
			return;
		}
		if (self.entityTags().contains(Corpses.NO_CORPSE_TAG)) {
			// (marked by another mod: a body that leaves nothing behind, like a Darktide shade)
			return;
		}
		if (self instanceof ServerPlayer player && !player.isSpectator()) {
			// a player's body stays where they fell, with their hotbar (they go on to respawn as usual)
			PlayerCorpses.spawn(player);
			return;
		}
		if (Corpses.leavesCorpse(self)) {
			Corpses.spawnCorpse(self, jaymoskitchen$loot == null ? List.of() : jaymoskitchen$loot);
			self.remove(Entity.RemovalReason.KILLED);
			ci.cancel();
		}
	}

	/** Down on the ground from a leg hit: whatever it was drawing (a bow, a crossbow, a trident) is let go. */
	@Inject(method = "tick", at = @At("TAIL"))
	private void jaymoskitchen$downed(org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
		LivingEntity self = (LivingEntity) (Object) this;
		if (!self.level().isClientSide() && self.isUsingItem() && !(self instanceof net.minecraft.world.entity.player.Player)
			&& com.jaymoskitchen.archery.LegShots.isCrawling(self)) {
			self.stopUsingItem();
		}
	}
}
