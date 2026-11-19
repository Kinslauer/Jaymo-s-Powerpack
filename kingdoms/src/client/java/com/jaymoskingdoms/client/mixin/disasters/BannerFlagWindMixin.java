package com.jaymoskingdoms.client.mixin.disasters;

import com.jaymoskingdoms.client.disasters.WindCloth;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.object.banner.BannerFlagModel;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The flag swings out by the lean its phase carries ({@code BannerWindMixin}). */
@Mixin(BannerFlagModel.class)
abstract class BannerFlagWindMixin {
	@Shadow
	@Final
	private ModelPart flag;

	@Inject(method = "setupAnim(Ljava/lang/Float;)V", at = @At("TAIL"))
	private void jaymoskingdoms$swingOut(Float phase, CallbackInfo ci) {
		this.flag.xRot += WindCloth.decodeBanner(phase);
	}
}
