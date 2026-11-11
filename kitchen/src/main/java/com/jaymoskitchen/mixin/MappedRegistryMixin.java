package com.jaymoskitchen.mixin;

import com.jaymoskitchen.JaymosKitchen;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * The 26.3 registries have no rename table, so the old {@code jaymoskitchen:waterskin} item is looked up as the Leather
 * Waterskin that replaced it. Stacks read from saves resolve their item id through these two lookups.
 */
@Mixin(MappedRegistry.class)
abstract class MappedRegistryMixin<T> {
	@ModifyVariable(method = "get(Lnet/minecraft/resources/Identifier;)Ljava/util/Optional;", at = @At("HEAD"), argsOnly = true)
	private Identifier jaymoskitchen$oldWaterskinGet(Identifier id) {
		return jaymoskitchen$rename(id);
	}

	@ModifyVariable(method = "getValue(Lnet/minecraft/resources/Identifier;)Ljava/lang/Object;", at = @At("HEAD"), argsOnly = true)
	private Identifier jaymoskitchen$oldWaterskinValue(Identifier id) {
		return jaymoskitchen$rename(id);
	}

	private Identifier jaymoskitchen$rename(Identifier id) {
		if (id != null && id.getNamespace().equals(JaymosKitchen.MOD_ID) && id.getPath().equals("waterskin")
			&& ((MappedRegistry<?>) (Object) this).key() == Registries.ITEM) {
			return JaymosKitchen.id("waterskin_leather");
		}
		return id;
	}
}
