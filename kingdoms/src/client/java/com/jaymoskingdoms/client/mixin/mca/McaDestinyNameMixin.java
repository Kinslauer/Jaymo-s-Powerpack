package com.jaymoskingdoms.client.mixin.mca;

import net.conczin.mca.client.gui.DestinyScreen;
import net.conczin.mca.client.gui.VillagerEditorScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * (0.7.165) Andy: "character names still randomize even though both players input their own when joining the server".
 * MCA's start-up creator keeps the typed name on the client only: its name page's Accept button moves on without
 * saving, and nothing after it does either, so the server kept the name it had given ({@code CharacterNames}). Leaving
 * the name page now saves the editor to the server, as the editor's own Done button does.
 */
@Mixin(DestinyScreen.class)
abstract class McaDestinyNameMixin {
	@Inject(method = "setPage", at = @At("HEAD"))
	private void jaymoskingdoms$saveName(String next, CallbackInfo ci) {
		// (0.7.229) the creator's first page is "intro" (its "general" tab is sent there), so the name was saved only if
		// the general tab was visited and left: Andy's "it requires an extra enter". Either page saves now.
		String page = ((McaEditorPageAccessor)(Object)this).jaymoskingdoms$page();
		if (("intro".equals(page) || "general".equals(page)) && !page.equals(next)) {
			((VillagerEditorScreen)(Object)this).syncVillagerData();
		}
	}
}
