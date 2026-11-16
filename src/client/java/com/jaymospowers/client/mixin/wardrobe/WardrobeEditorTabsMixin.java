package com.jaymospowers.client.mixin.wardrobe;

import com.jaymospowers.client.wardrobe.WardrobeClient;
import java.util.Arrays;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The wardrobe's Looks tab is MCA's own editor (WardrobeClient): its page row (General, Body, Traits) gets the
 * wardrobe's other tabs on the end, as MCA's own buttons, and picking one switches the wardrobe to it. Only on the
 * editor the wardrobe opened, never on MCA's creator, needle or comb.
 */
@Pseudo
@Mixin(targets = "net.conczin.mca.client.gui.VillagerEditorScreen", remap = false)
abstract class WardrobeEditorTabsMixin {
	@Inject(method = "getPages", at = @At("RETURN"), cancellable = true, require = 0)
	private void jaymospowers$wardrobeTabs(CallbackInfoReturnable<String[]> cir) {
		if (WardrobeClient.editorTabs(this)) {
			String[] pages = cir.getReturnValue();
			String[] more = Arrays.copyOf(pages, pages.length + WardrobeClient.EDITOR_PAGES.size());
			int i = pages.length;
			for (String page : WardrobeClient.EDITOR_PAGES.keySet()) {
				more[i++] = page;
			}
			cir.setReturnValue(more);
		}
	}

	@Inject(method = "setPage", at = @At("HEAD"), cancellable = true, require = 0)
	private void jaymospowers$wardrobeTab(String page, CallbackInfo ci) {
		WardrobeClient.Tab tab = WardrobeClient.EDITOR_PAGES.get(page);
		if (tab != null && WardrobeClient.editorTabs(this)) {
			ci.cancel();
			WardrobeClient.show(tab);
		}
	}
}
