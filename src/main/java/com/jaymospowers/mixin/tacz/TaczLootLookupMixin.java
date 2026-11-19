package com.jaymospowers.mixin.tacz;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.storage.loot.LootTable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/**
 * TaCZ (GPL-3.0, shipped unchanged, nothing of it copied) runs LootTableInjectorModifier.doApply on every loot roll that
 * returns a list (mob equipment, chests). doApply needs the rolled table's id, which LootTable doesn't keep, so it scans
 * every loot table in the registry for the one that is the same object, before it checks whether any injection exists.
 * Pre-generating a new world rolls equipment for every mob made, so the Deck's server thread spent a good part of each
 * stall in that scan. The scan is now a lookup in a map from table to id, built once per registry lookup (a /reload makes
 * a new one) and keeping the first table in registry order, as the scan's findFirst did. TaCZ still decides everything
 * after the id. Skipped when the mod isn't there or its code has changed.
 */
@Pseudo
@Mixin(targets = "com.tacz.guns.loot.LootTableInjectorModifier", remap = false)
abstract class TaczLootLookupMixin {
	private record Snapshot(Object source, Map<LootTable, Identifier> ids) {}

	private static volatile Snapshot jaymospowers$snapshot;

	@SuppressWarnings({"unchecked", "rawtypes"})
	@WrapOperation(method = "doApply", require = 0,
		at = @At(value = "INVOKE", target = "Ljava/util/Optional;flatMap(Ljava/util/function/Function;)Ljava/util/Optional;"))
	private static Optional jaymospowers$byIdentity(Optional lookup, Function scan, Operation<Optional> original,
		@Local(argsOnly = true) LootTable table) {
		if (lookup.isEmpty()) {
			return Optional.empty();
		}
		HolderLookup.RegistryLookup<LootTable> registry = (HolderLookup.RegistryLookup<LootTable>) lookup.get();
		Snapshot snapshot = jaymospowers$snapshot;
		if (snapshot == null || snapshot.source() != registry) {
			Map<LootTable, Identifier> ids = new IdentityHashMap<>();
			registry.listElements().forEach(holder -> ids.putIfAbsent(holder.value(), holder.key().identifier()));
			snapshot = new Snapshot(registry, Collections.unmodifiableMap(ids));
			jaymospowers$snapshot = snapshot;
		}
		return Optional.ofNullable(snapshot.ids().get(table));
	}
}
