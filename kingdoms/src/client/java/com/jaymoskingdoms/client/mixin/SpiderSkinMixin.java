package com.jaymoskingdoms.client.mixin;

import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.wild.SpiderKinds;
import java.util.Locale;
import net.minecraft.client.renderer.entity.SpiderRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A spider of a kind ({@link SpiderKinds}) wears its kind's skin (textures/entity/spider/&lt;kind&gt;.png, drawn by
 * tools/gen_spider_kinds.py); its glowing eyes stay as they are.
 */
@Mixin(SpiderRenderer.class)
abstract class SpiderSkinMixin {

	@Inject(method = "getTextureLocation(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;)Lnet/minecraft/resources/Identifier;",
		at = @At("RETURN"), cancellable = true)
	private void jaymoskingdoms$kindSkin(LivingEntityRenderState state, CallbackInfoReturnable<Identifier> cir) {
		SpiderKinds.Kind kind = state.getData(com.jaymoskingdoms.client.SpiderSkins.KIND);
		if (kind != null) {
			cir.setReturnValue(JaymosKingdoms.id("textures/entity/spider/" + kind.name().toLowerCase(Locale.ROOT) + ".png"));
		}
	}
}
