package com.jaymoskingdoms.client.mixin.mca;

import com.jaymoskingdoms.client.InteractActionsClient;
import java.util.ArrayList;
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
 * (0.7.449) The Interact page's actions ({@code InteractActions}, added by {@code McaScreensMixin}): only those this
 * villager offers you show, stacked one under another below Ask the Way, with no gaps for the hidden ones.
 */
@Mixin(AbstractDynamicScreen.class)
abstract class McaActionButtonsMixin extends Screen {
	private static final String WAY = "gui.button.way", ACT = "gui.button.act.";

	protected McaActionButtonsMixin(Component title) {
		super(title);
	}

	@Inject(method = "setLayout", at = @At("TAIL"))
	private void jaymoskingdoms$actions(String layout, CallbackInfo ci) {
		if (!"interact".equals(layout) || !((Object)this instanceof InteractScreen) || !(this instanceof McaInteractScreenAccessor interact)) {
			return;
		}
		int villager = interact.jaymoskingdoms$villager().asEntity().getId();
		AbstractWidget way = null;
		List<AbstractWidget> shown = new ArrayList<>();
		for (GuiEventListener w : List.copyOf(this.children())) {
			if (!(w instanceof McaButtonExAccessor ex) || !(w instanceof AbstractWidget widget)) {
				continue;
			}
			String id = ex.jaymoskingdoms$apiButton().identifier();
			if (id.equals(WAY)) {
				way = widget;
			} else if (id.startsWith(ACT)) {
				if (InteractActionsClient.offers(villager, id.substring(ACT.length()))) {
					shown.add(widget);
				} else {
					widget.visible = false;
					widget.active = false;
				}
			}
		}
		if (way == null) {
			return;
		}
		int y = way.getY();
		for (AbstractWidget w : shown) {
			y += way.getHeight() + 1;
			w.setY(y);
		}
	}
}
