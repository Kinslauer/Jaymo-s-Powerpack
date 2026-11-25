package com.jaymoskingdoms.client.mixin.mca;

import com.jaymoskingdoms.client.TalkFirst;
import java.util.List;
import net.conczin.mca.client.gui.AbstractDynamicScreen;
import net.conczin.mca.client.gui.InteractScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Andy (2026-11-25): every interaction through talk, only Gift and Trade left as buttons ({@link TalkFirst}). After MCA
 * and our page mixins have laid out a page of the villager screen (and hidden what this villager doesn't offer you), its
 * buttons leave the column for the talk wheel. The column tidy ({@code McaButtonColumnMixin}, priority 1100) runs after
 * this, on Gift and Trade alone.
 */
@Mixin(value = AbstractDynamicScreen.class, priority = 1050)
abstract class McaTalkFirstMixin extends Screen {
	protected McaTalkFirstMixin(Component title) {
		super(title);
	}

	@Inject(method = "setLayout", at = @At("TAIL"))
	private void jaymoskingdoms$talkFirst(String layout, CallbackInfo ci) {
		if ((Object) this instanceof InteractScreen screen) {
			TalkFirst.laidOut(screen, layout, List.copyOf(this.children()), w -> this.addRenderableWidget(w));
		}
	}
}
