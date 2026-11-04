package com.jaymospowers.client.mixin;

import com.jaymospowers.client.wet.ArmourGrimeTextures;
import com.jaymospowers.core.wet.ArmourGrime;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.function.Function;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.EquipmentLayerRenderer;
import net.minecraft.client.resources.model.EquipmentClientInfo;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.EquipmentAsset;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * (0.19.74) Dirty armour ({@link ArmourGrime}) is drawn with grime painted into its texture in splotches
 * ({@link ArmourGrimeTextures}), so each wash visibly takes some away. (It was a flat tint over the whole piece before.)
 */
@Mixin(EquipmentLayerRenderer.class)
abstract class ArmourGrimeMixin {
	/** The grime of the piece being drawn now, or null (render thread only). */
	@Unique
	private static ArmourGrime.@Nullable Grime jaymospowers$grime;
	/** (0.20.41) And its blood. */
	@Unique
	private static com.jaymospowers.core.blood.Bloodied.@Nullable Blood jaymospowers$blood;

	@Inject(method = "renderLayers(Lnet/minecraft/client/resources/model/EquipmentClientInfo$LayerType;Lnet/minecraft/resources/ResourceKey;Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lnet/minecraft/world/item/ItemStack;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/resources/Identifier;II)V",
		at = @At("HEAD"))
	private void jaymospowers$grime(EquipmentClientInfo.LayerType layer, ResourceKey<EquipmentAsset> asset, Model<?> model, Object state, ItemStack stack,
		PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, Identifier texture, int outlineColor, int order, CallbackInfo ci) {
		jaymospowers$grime = ArmourGrime.grime(stack);
		jaymospowers$blood = com.jaymospowers.core.blood.Bloodied.blood(stack);
	}

	/** Each of the armour's own layers (not the trim) is drawn from its grimy copy. */
	@WrapOperation(method = "renderLayers(Lnet/minecraft/client/resources/model/EquipmentClientInfo$LayerType;Lnet/minecraft/resources/ResourceKey;Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lnet/minecraft/world/item/ItemStack;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/resources/Identifier;II)V",
		at = @At(value = "INVOKE", target = "Ljava/util/function/Function;apply(Ljava/lang/Object;)Ljava/lang/Object;", ordinal = 0), require = 0)
	private Object jaymospowers$grimyLayer(Function<Object, Object> lookup, Object key, Operation<Object> original) {
		Object texture = original.call(lookup, key);
		ArmourGrime.Grime grime = jaymospowers$grime;
		var blood = jaymospowers$blood;
		return (grime != null || blood != null) && texture instanceof Identifier id ? ArmourGrimeTextures.armour(id, grime, blood) : texture;
	}
}
