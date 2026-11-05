package com.jaymoskingdoms.client.mixin.mca;

import com.jaymoskingdoms.ZFighterFolk;
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
 * (0.7.555) Andy: "the judge, keeper and masters dont need the FULL screen, no reason to tell rumors or hug them". On one of
 * Powers' Z Fighter characters ({@link ZFighterFolk#PART}: the Judge, the Keeper, the Mad Tinkerer, the demon masters, the
 * star smith...) MCA's main page shows only Talk and Trade: no Interact, Gift, Family Tree, Quests or the rest. Their talk
 * menu is trimmed too ({@link ZFighterFolk#keeps}). They're never shown the stranger's page either ({@code McaStrangerPageMixin}).
 */
@Mixin(AbstractDynamicScreen.class)
abstract class McaZFighterPageMixin extends Screen {
	private static final java.util.Set<String> KEPT = java.util.Set.of("gui.button.talk", "gui.button.trade");

	protected McaZFighterPageMixin(Component title) {
		super(title);
	}

	@Inject(method = "setLayout", at = @At("TAIL"))
	private void jaymoskingdoms$trimmed(String layout, CallbackInfo ci) {
		if (!"main".equals(layout) || !((Object)this instanceof InteractScreen) || !(this instanceof McaInteractScreenAccessor interact)
			|| interact.jaymoskingdoms$villager().asEntity().getAttached(ZFighterFolk.PART) == null) {
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
