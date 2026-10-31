package com.jaymospowers.client.mixin;

import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.EquipmentLayerRenderer;
import net.minecraft.client.resources.model.EquipmentClientInfo;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.trim.ArmorTrim;
import net.minecraft.world.item.equipment.trim.TrimMaterial;
import com.mojang.blaze3d.vertex.PoseStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * (Motion patch 5) Armour trims made from glowing materials glow, after Actions &amp; Stuff's emissive trims: an
 * amethyst, redstone, diamond or emerald trim is drawn at full block light, so it shows in the dark. Only the trim
 * itself; the armour under it is lit as before.
 */
@Mixin(EquipmentLayerRenderer.class)
abstract class GlowingTrimMixin {
	/** Trim materials that glow. */
	@Unique
	private static final java.util.List<ResourceKey<TrimMaterial>> GLOWING = java.util.stream.Stream.of("amethyst", "redstone", "diamond", "emerald")
		.map(id -> ResourceKey.create(Registries.TRIM_MATERIAL, Identifier.withDefaultNamespace(id))).toList();

	/** Whether the piece being drawn now has a glowing trim (render thread only). */
	@Unique
	private static boolean jaymospowers$glows;

	@Inject(method = "renderLayers(Lnet/minecraft/client/resources/model/EquipmentClientInfo$LayerType;Lnet/minecraft/resources/ResourceKey;Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lnet/minecraft/world/item/ItemStack;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/resources/Identifier;II)V",
		at = @At("HEAD"))
	private void jaymospowers$trimGlows(EquipmentClientInfo.LayerType layer, ResourceKey<EquipmentAsset> asset, Model<?> model, Object state, ItemStack stack,
		PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, Identifier texture, int outlineColor, int order, CallbackInfo ci) {
		ArmorTrim trim = stack.get(DataComponents.TRIM);
		Holder<TrimMaterial> material = trim == null ? null : trim.material();
		jaymospowers$glows = material != null && GLOWING.stream().anyMatch(material::is);
	}

	/** The second model submitted is the trim: at full block light when its material glows. */
	@ModifyArg(method = "renderLayers(Lnet/minecraft/client/resources/model/EquipmentClientInfo$LayerType;Lnet/minecraft/resources/ResourceKey;Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lnet/minecraft/world/item/ItemStack;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/resources/Identifier;II)V",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/OrderedSubmitNodeCollector;submitModel(Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/rendertype/RenderType;IIILnet/minecraft/client/renderer/texture/UvMapping;I)V",
			ordinal = 1), index = 4, require = 0)
	private int jaymospowers$trimLight(int lightCoords) {
		return jaymospowers$glows ? (lightCoords & 0xFFFF0000) | 0xF0 : lightCoords;
	}
}
