package com.jaymoskingdoms.client.mixin.mca;

import com.jaymoskingdoms.ZFighterFolk;
import net.conczin.mca.client.gui.InteractScreen;
import net.conczin.mca.entity.VillagerLike;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * (0.7.405) Andy: the Judge's MCA screen said "Jobless" and "Mood: Depressed". On one of Powers' Z Fighter characters
 * ({@link ZFighterFolk#PART}), MCA's trade line shows their part ("Judge of the Dead") and its mood line their bearing
 * ("Bearing: Stern"); personality and temperament stay as they are.
 */
@Mixin(InteractScreen.class)
abstract class McaZFighterInfoMixin {
	@Shadow
	@Final
	private VillagerLike<?> villager;

	@Redirect(method = "drawTextPopups", at = @At(value = "INVOKE", target = "Lnet/conczin/mca/entity/VillagerLike;getProfessionText()Lnet/minecraft/network/chat/MutableComponent;"))
	private MutableComponent jaymoskingdoms$part(VillagerLike<?> self) {
		String kind = this.villager.asEntity().getAttached(ZFighterFolk.PART);
		return kind == null ? self.getProfessionText() : ZFighterFolk.part(kind).copy();
	}

	/** (MCA's first styled line is the mood's, in the mood's colour) */
	@Redirect(method = "drawTextPopups", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/chat/MutableComponent;withStyle(Lnet/minecraft/ChatFormatting;)Lnet/minecraft/network/chat/MutableComponent;", ordinal = 0))
	private MutableComponent jaymoskingdoms$bearing(MutableComponent mood, ChatFormatting colour) {
		String kind = this.villager.asEntity().getAttached(ZFighterFolk.PART);
		return kind == null ? mood.withStyle(colour) : ZFighterFolk.bearing(kind).copy().withStyle(ChatFormatting.GRAY);
	}
}
