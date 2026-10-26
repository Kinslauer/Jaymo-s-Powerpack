package com.jaymoskingdoms.client.mixin.mca;

import com.jaymoskingdoms.Introductions;
import java.util.List;
import java.util.UUID;
import net.conczin.mca.client.gui.AbstractDynamicScreen;
import net.conczin.mca.client.gui.InteractScreen;
import net.conczin.mca.client.gui.MCAButton;
import net.conczin.mca.network.Network;
import net.conczin.mca.network.c2s.InteractionDialogueMessage;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
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

	@Shadow
	protected abstract void buttonPressed(MCAButton button);

	@Inject(method = "setLayout", at = @At("TAIL"))
	private void jaymoskingdoms$strangerPage(String layout, CallbackInfo ci) {
		if (!"main".equals(layout) || !((Object)this instanceof InteractScreen) || !(this instanceof McaInteractScreenAccessor interact)
			|| Introductions.knownHere(interact.jaymoskingdoms$villager().asEntity())) {
			return;
		}
		AbstractWidget talk = null, second = null;
		for (GuiEventListener w : List.copyOf(this.children())) {
			if (!(w instanceof McaButtonExAccessor ex) || !(w instanceof AbstractWidget widget)) {
				continue;
			}
			String id = ex.jaymoskingdoms$apiButton().identifier();
			if (id.equals("gui.button.talk")) {
				talk = widget;
			} else if (id.equals("gui.button.interact")) {
				second = widget;
			}
			if (!KEPT.contains(id)) {
				widget.visible = false;
				widget.active = false;
			}
		}
		if (talk == null) {
			return;
		}
		AbstractWidget first = talk;
		// (in MCA's own first two rows: Trade keeps its third)
		UUID villager = interact.jaymoskingdoms$villager().asEntity().getUUID();
		this.addRenderableWidget(Button.builder(Component.translatable("gui.jaymoskingdoms.introduce"),
				b -> Network.sendToServer(new InteractionDialogueMessage(villager, "main", Introductions.INTRODUCE)))
			.bounds(talk.getX(), talk.getY(), talk.getWidth(), talk.getHeight()).build());
		int giftY = second != null ? second.getY() : talk.getY() + talk.getHeight() + 1;
		this.addRenderableWidget(Button.builder(Component.translatable("gui.button.gift"),
				b -> this.buttonPressed(new MCAButton("gui.button.gift", "", 0, 0, 0, 0, first.getWidth(), first.getHeight(), false, false, "", false, false)))
			.bounds(talk.getX(), giftY, talk.getWidth(), talk.getHeight()).build());
	}
}
