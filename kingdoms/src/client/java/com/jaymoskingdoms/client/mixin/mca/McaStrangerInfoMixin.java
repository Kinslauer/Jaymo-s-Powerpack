package com.jaymoskingdoms.client.mixin.mca;

import com.jaymoskingdoms.Introductions;
import net.conczin.mca.client.gui.AbstractDynamicScreen;
import net.conczin.mca.client.gui.InteractScreen;
import net.conczin.mca.entity.VillagerLike;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * (0.7.230) Andy: "before that none of this is visible". On MCA's interact screen, a villager you haven't been
 * introduced to shows nothing: no name, trade, mood, personality, temperament or standing lines, and no hearts or
 * other icons. Once you're introduced, the page is laid out again with everything ({@code McaStrangerPageMixin}).
 */
@Mixin(InteractScreen.class)
abstract class McaStrangerInfoMixin {
	@Shadow
	@Final
	private VillagerLike<?> villager;

	@Shadow
	private boolean inGiftMode;

	/** Whether this viewer knew them when the page was last laid out (null: not looked at yet). */
	@Unique
	private Boolean jaymoskingdoms$knew;

	@Unique
	private boolean jaymoskingdoms$stranger() {
		return !Introductions.knownHere(this.villager.asEntity());
	}

	@Inject(method = "drawTextPopups", at = @At("HEAD"), cancellable = true)
	private void jaymoskingdoms$noLines(GuiGraphicsExtractor graphics, CallbackInfo ci) {
		if (this.jaymoskingdoms$stranger()) {
			ci.cancel();
		}
	}

	@Inject(method = "drawIcons", at = @At("HEAD"), cancellable = true)
	private void jaymoskingdoms$noIcons(GuiGraphicsExtractor graphics, CallbackInfo ci) {
		// (holding out a gift still shows MCA's gift icon)
		if (this.jaymoskingdoms$stranger() && !this.inGiftMode) {
			ci.cancel();
		}
	}

	@Inject(method = "tick", at = @At("TAIL"))
	private void jaymoskingdoms$introduced(CallbackInfo ci) {
		boolean knew = !this.jaymoskingdoms$stranger();
		AbstractDynamicScreen screen = (AbstractDynamicScreen)(Object)this;
		if (this.jaymoskingdoms$knew != null && this.jaymoskingdoms$knew != knew && "main".equals(screen.getActiveScreen())) {
			screen.setLayout("main");
		}
		this.jaymoskingdoms$knew = knew;
	}
}
