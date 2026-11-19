package com.jaymospowers.mixin.shiftingwares;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.List;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.trading.TradeSet;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Shifting Wares (EUPL-1.2, shipped unchanged, nothing of it copied) rerolls a villager's trades each day from the trade
 * set of each of its profession's levels, and calls Optional.get on that set. VillagersPlus professions (miner,
 * alchemist, oceanographer, occultist, horticulturist) name a set per level (villagersplus:miner/level_1 ...) that its jar
 * never defines, so the get threw and crashed the server tick (the Deck, 2026-11-19, an MCA villager). A villager whose
 * profession names a set that isn't registered is now left as it is: no layout is built and the reroll does nothing, so
 * its offers are neither changed nor emptied (an empty layout would have removed them all). Professions whose sets all
 * exist reroll as before. Skipped when the mod isn't there.
 */
@Pseudo
@Mixin(targets = "fr.estecka.shiftingwares.TradeShuffler", remap = false)
abstract class TradeShufflerMixin {
	@Shadow @Final private List<?> tradeLayout;

	@WrapOperation(method = "<init>", require = 0,
		at = @At(value = "INVOKE", target = "Lfr/estecka/shiftingwares/SlotRerollSource;CreateTradeLayout(Lnet/minecraft/world/entity/npc/villager/Villager;)Ljava/util/List;"))
	private List<?> jaymospowers$skipMissingSets(Villager villager, Operation<List<?>> original) {
		return hasAllSets(villager) ? original.call(villager) : null;
	}

	@Inject(method = "Reroll", at = @At("HEAD"), cancellable = true, require = 0)
	private void jaymospowers$nothingToReroll(CallbackInfo ci) {
		if (tradeLayout == null) {
			ci.cancel();
		}
	}

	/** Every level of the villager's profession names a set that is in the registry (the check Shifting Wares skips). */
	private static boolean hasAllSets(Villager villager) {
		VillagerProfession profession = villager.getVillagerData().profession().value();
		Registry<TradeSet> sets = villager.registryAccess().lookupOrThrow(Registries.TRADE_SET);
		for (int level : profession.tradeSetsByLevel().keySet()) {
			if (sets.getOptional(profession.getTrades(level)).isEmpty()) {
				return false;
			}
		}
		return true;
	}
}
