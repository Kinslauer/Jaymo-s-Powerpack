package com.jaymospowers.client.mixin;

import com.jaymospowers.core.wet.ArmourGrime;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.EquipmentLayerRenderer;
import net.minecraft.client.resources.model.EquipmentClientInfo;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.EquipmentAsset;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * (0.19.61) Dirty armour ({@link ArmourGrime}) is drawn tinted toward dusty tan or mud brown, the dirtier the more. The
 * tint multiplies the piece's own colour (and a dyed piece's dye), so see-through bits of the texture stay see-through.
 */
@Mixin(EquipmentLayerRenderer.class)
abstract class ArmourGrimeMixin {
	/** The tint of a filthy piece: all dust, and all mud. */
	@Unique
	private static final int DUST = 0xC9B48C, MUD = 0x6B5238;
	/** How far toward that tint a filthy piece goes (1 would be the tint's colour alone). */
	@Unique
	private static final float MOST = 0.8F;

	/** The tint of the piece being drawn now, or -1 for none (render thread only). */
	@Unique
	private static int jaymospowers$tint = -1;

	@Inject(method = "renderLayers(Lnet/minecraft/client/resources/model/EquipmentClientInfo$LayerType;Lnet/minecraft/resources/ResourceKey;Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lnet/minecraft/world/item/ItemStack;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/resources/Identifier;II)V",
		at = @At("HEAD"))
	private void jaymospowers$grime(EquipmentClientInfo.LayerType layer, ResourceKey<EquipmentAsset> asset, Model<?> model, Object state, ItemStack stack,
		PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, Identifier texture, int outlineColor, int order, CallbackInfo ci) {
		ArmourGrime.Grime grime = ArmourGrime.grime(stack);
		if (grime == null || grime.amount() <= 0) {
			jaymospowers$tint = -1;
			return;
		}
		int dirt = ARGB.srgbLerp(grime.muddiness(), DUST, MUD);
		float how = MOST * Mth.sqrt(grime.level());
		jaymospowers$tint = ARGB.srgbLerp(how, 0xFFFFFF, dirt) & 0xFFFFFF;
	}

	/** The armour's own layers (not the trim) take the tint. */
	@ModifyArg(method = "renderLayers(Lnet/minecraft/client/resources/model/EquipmentClientInfo$LayerType;Lnet/minecraft/resources/ResourceKey;Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lnet/minecraft/world/item/ItemStack;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/resources/Identifier;II)V",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/OrderedSubmitNodeCollector;submitModel(Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/rendertype/RenderType;IIILnet/minecraft/client/renderer/texture/UvMapping;I)V",
			ordinal = 0), index = 6, require = 0)
	private int jaymospowers$tintLayer(int color) {
		int tint = jaymospowers$tint;
		return tint == -1 ? color : ARGB.multiply(color, 0xFF000000 | tint);
	}
}
