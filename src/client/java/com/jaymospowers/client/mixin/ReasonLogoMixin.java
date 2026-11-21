package com.jaymospowers.client.mixin;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.LogoRenderer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The title logo (title screen, credits, accessibility onboarding) is the pack's "Reason" logo: ivy-grown stone letters
 * with a warrior and a horde of mob shadows behind them (drawn by tools/reason_logo.py). It is taller than vanilla's
 * 256x44 MINECRAFT logo, so it starts {@link #TOP} units higher; the letters land where vanilla's did and the
 * silhouettes rise above them. No "Java Edition" strip and no "Minceraft" easter egg.
 */
@Mixin(LogoRenderer.class)
abstract class ReasonLogoMixin {
	private static final Identifier REASON_LOGO = Identifier.fromNamespaceAndPath("jaymospowers", "textures/gui/title/reason.png");
	private static final int WIDTH = 256;
	private static final int HEIGHT = 80;
	/** GUI units drawn above vanilla's logo position (tools/reason_logo.py TOP_GUI). */
	private static final int TOP = 28;

	@Shadow
	public abstract boolean keepLogoThroughFade();

	@Inject(method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IFI)V", at = @At("HEAD"), cancellable = true)
	private void jaymospowers$reasonLogo(GuiGraphicsExtractor graphics, int screenWidth, float alpha, int heightOffset, CallbackInfo ci) {
		int color = ARGB.white(keepLogoThroughFade() ? 1.0F : alpha);
		graphics.blit(RenderPipelines.GUI_TEXTURED, REASON_LOGO, screenWidth / 2 - WIDTH / 2, heightOffset - TOP, 0.0F, 0.0F,
			WIDTH, HEIGHT, WIDTH, HEIGHT, color);
		ci.cancel();
	}
}
