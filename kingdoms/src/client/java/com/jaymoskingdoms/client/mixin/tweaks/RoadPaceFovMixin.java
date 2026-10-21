package com.jaymoskingdoms.client.mixin.tweaks;

import com.jaymoskingdoms.RoadPace;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * (0.7.103) The road's pace ({@link RoadPace}) doesn't zoom the view: the speed the field of view is worked out from is
 * read as if the pace modifier weren't there (it multiplies the total, so dividing it out is exact).
 */
@Mixin(AbstractClientPlayer.class)
abstract class RoadPaceFovMixin {
	@ModifyExpressionValue(method = "getFieldOfViewModifier", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/player/AbstractClientPlayer;getAttributeValue(Lnet/minecraft/core/Holder;)D"))
	private double jaymoskingdoms$noRoadZoom(double speed) {
		AttributeInstance movement = ((AbstractClientPlayer)(Object)this).getAttribute(Attributes.MOVEMENT_SPEED);
		AttributeModifier pace = movement == null ? null : movement.getModifier(RoadPace.PACE_ID);
		return pace == null || pace.amount() <= -1.0 ? speed : speed / (1.0 + pace.amount());
	}
}
