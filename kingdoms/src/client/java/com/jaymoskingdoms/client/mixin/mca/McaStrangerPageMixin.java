package com.jaymoskingdoms.client.mixin.mca;

import com.jaymoskingdoms.Introductions;
import java.util.List;
import net.conczin.mca.client.gui.AbstractDynamicScreen;
import net.conczin.mca.client.gui.InteractScreen;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * (0.7.230) Andy: "introductions should be the first option available. before that none of this is visible, no options
 * but gift trade or introduce". MCA's main page for a villager you haven't been introduced to: Introduce yourself
 * first, then Gift, then Trade (if they trade), and nothing else. Introducing asks their name, or offers a handshake
 * when you share no tongue ({@code Introductions.INTRODUCE}); the page fills in once you know them
 * ({@code McaStrangerInfoMixin}).
 */
@Mixin(AbstractDynamicScreen.class)
abstract class McaStrangerPageMixin extends Screen {
	/** MCA's buttons a stranger still gets (Trade, where its own constraints allow it). */
	private static final List<String> KEPT = List.of("gui.button.trade");

	protected McaStrangerPageMixin(Component title) {
		super(title);
	}

	@Inject(method = "setLayout", at = @At("TAIL"))
	private void jaymoskingdoms$strangerPage(String layout, CallbackInfo ci) {
		if (!"main".equals(layout) || !((Object)this instanceof InteractScreen) || !(this instanceof McaInteractScreenAccessor interact)
			|| Introductions.knownHere(interact.jaymoskingdoms$villager().asEntity())
			// (0.7.555) one of Powers' Z Fighter characters has its own trimmed page (McaZFighterPageMixin)
			|| interact.jaymoskingdoms$villager().asEntity().getAttached(com.jaymoskingdoms.ZFighterFolk.PART) != null) {
			return;
		}
		for (GuiEventListener w : List.copyOf(this.children())) {
			if (w instanceof McaButtonExAccessor ex && w instanceof AbstractWidget widget && !KEPT.contains(ex.jaymoskingdoms$apiButton().identifier())) {
				widget.visible = false;
				widget.active = false;
			}
		}
	}
}
