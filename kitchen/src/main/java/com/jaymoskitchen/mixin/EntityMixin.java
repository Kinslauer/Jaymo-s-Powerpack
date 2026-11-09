package com.jaymoskitchen.mixin;

import com.jaymoskitchen.butchery.Butchery;
import com.jaymoskitchen.corpse.CorpseCapture;
import com.jaymoskitchen.corpse.CorpseKind;
import com.jaymoskitchen.corpse.Corpses;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Every item an entity drops goes through here; a dying corpse-leaver's drops are caught for its corpse. */
@Mixin(Entity.class)
abstract class EntityMixin {
	@Inject(
		method = "spawnAtLocation(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/entity/item/ItemEntity;",
		at = @At("HEAD"),
		cancellable = true)
	private void jaymoskitchen$catchDeathLoot(ServerLevel level, ItemStack stack, Vec3 offset,
		CallbackInfoReturnable<ItemEntity> cir) {
		if ((Object) this instanceof CorpseCapture capture && !stack.isEmpty()
			&& (capture.jaymoskitchen$isCapturing() || strayCarcassLoot((Entity) (Object) this, stack))) {
			capture.jaymoskitchen$capture(stack);
			cir.setReturnValue(null);
		}
	}

	/**
	 * Andy: "Deer also drop their venison and hide immediately, it should only be acquired from butchery." Meat or a hide
	 * that a dying animal drops outside its own death-loot drop (by any other path, before its body takes its place) goes
	 * into its body too, so it comes only from butchering.
	 */
	private static boolean strayCarcassLoot(Entity self, ItemStack stack) {
		return self instanceof LivingEntity mob && mob.isDeadOrDying() && mob.deathTime == 0 && Corpses.leavesCorpse(mob)
			&& CorpseKind.of(mob) == CorpseKind.ANIMAL && !mob.entityTags().contains(Corpses.NO_CORPSE_TAG) && !Corpses.inNoCorpseZone(mob)
			&& (stack.has(DataComponents.FOOD) || stack.is(Butchery.SKINS));
	}
}
