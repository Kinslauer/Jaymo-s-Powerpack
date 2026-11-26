package com.jaymoskingdoms.mixin.mca;

import com.jaymoskingdoms.Anglers;
import java.util.List;
import net.conczin.mca.entity.MCAFishingBobberEntity;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.brain.tasks.chore.FishingTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * (Andy, 2026-11-26: "all npcs should have to fight fish and be seen doing so as well") A villager at MCA's fishing chore
 * fights the fish that takes the float, as anyone fishing does ({@link Anglers}): when MCA would reel its catch in (the
 * items flying to the villager), a fish on the line is fought from where the float lay instead, MCA's float taken up and
 * ours put in its mouth, and MCA's chore waits while the fight lasts. Landed, the catch goes into the villager's
 * inventory, where MCA's would have; lost, there's none. A catch with no fish in it (junk, treasure) comes in MCA's way.
 */
@Mixin(FishingTask.class)
abstract class McaFishingMixin {
	@Shadow
	abstract List<ItemStack> getFishingLoot(ServerLevel level, VillagerEntityMCA villager);

	@Shadow
	private void discardBobber(VillagerEntityMCA villager) {
		throw new AssertionError();
	}

	@Inject(method = "beginReel", at = @At("HEAD"), cancellable = true)
	private void jaymoskingdoms$fight(ServerLevel level, VillagerEntityMCA villager, CallbackInfo ci) {
		MCAFishingBobberEntity bobber = villager.getFishingBobber();
		if (bobber == null || bobber.isRemoved()) {
			return;
		}
		List<ItemStack> loot = this.getFishingLoot(level, villager);
		if (loot.isEmpty()) {
			return;
		}
		List<ItemStack> rest = loot.subList(1, loot.size()).stream().map(ItemStack::copy).toList();
		if (!Anglers.hooked(level, villager, bobber.position(), loot.get(0), caught -> {
			give(level, villager, caught);
			rest.forEach(stack -> give(level, villager, stack));
		})) {
			return;
		}
		this.discardBobber(villager);
		ci.cancel();
	}

	/** While the fight's on, the chore waits (it would cast again, the float gone). */
	@Inject(method = "tick(Lnet/minecraft/server/level/ServerLevel;Lnet/conczin/mca/entity/VillagerEntityMCA;J)V", at = @At("HEAD"), cancellable = true)
	private void jaymoskingdoms$fighting(ServerLevel level, VillagerEntityMCA villager, long time, CallbackInfo ci) {
		if (Anglers.angling(villager)) {
			ci.cancel();
		}
	}

	private static void give(ServerLevel level, VillagerEntityMCA villager, ItemStack stack) {
		ItemStack left = villager.getInventory().addItem(stack);
		if (!left.isEmpty()) {
			villager.spawnAtLocation(level, left);
		}
	}
}
