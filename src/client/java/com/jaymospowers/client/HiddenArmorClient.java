package com.jaymospowers.client;

import com.jaymospowers.client.mixin.ContainerScreenAccessor;
import com.jaymospowers.core.HiddenArmor;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Hidden armour on the client: the eye buttons beside the inventory's armour slots, and taking hidden pieces off every
 * player's render state (so the vanilla armour layer, Kingdoms' armoury sets, worn heads and pumpkins all skip them).
 */
public final class HiddenArmorClient {
	/** A button's size, in GUI pixels. */
	static final int SIZE = 7;
	private static final int BORDER = 0xFF373737;
	private static final int FACE = 0xFFC6C6C6;
	private static final int FACE_HOVER = 0xFFFFFFFF;
	private static final int SHOWN = 0xFF3F7F3F;
	private static final int HIDDEN = 0xFF8B2020;

	private HiddenArmorClient() {
	}

	public static void init() {
		ScreenEvents.AFTER_INIT.register((minecraft, screen, width, height) -> {
			if (screen instanceof InventoryScreen || screen instanceof CreativeModeInventoryScreen) {
				for (int i = 0; i < HiddenArmor.SLOTS.length; i++) {
					Toggle toggle = new Toggle((AbstractContainerScreen<?>)screen, i);
					toggle.place();
					Screens.getWidgets(screen).add(toggle);
				}
				// the recipe book and the creative tabs move things about: follow them every frame
				ScreenEvents.beforeExtract(screen).register((s, graphics, mouseX, mouseY, delta) -> {
					for (var widget : Screens.getWidgets(s)) {
						if (widget instanceof Toggle toggle) {
							toggle.place();
						}
					}
				});
			}
		});
	}

	/** From the avatar renderer's extractRenderState, last: hidden pieces aren't drawn (an elytra still spreads). */
	public static void extract(Avatar entity, AvatarRenderState state) {
		if (!(entity instanceof Player player) || !player.hasAttached(HiddenArmor.HIDDEN)) {
			return;
		}
		if (HiddenArmor.hidden(player, EquipmentSlot.HEAD)) {
			state.headEquipment = ItemStack.EMPTY;
			state.headItem.clear();
			state.wornHeadType = null;
			state.wornHeadProfile = null;
		}
		if (HiddenArmor.hidden(player, EquipmentSlot.CHEST) && !state.chestEquipment.has(DataComponents.GLIDER)) {
			state.chestEquipment = ItemStack.EMPTY;
		}
		if (HiddenArmor.hidden(player, EquipmentSlot.LEGS)) {
			state.legsEquipment = ItemStack.EMPTY;
		}
		if (HiddenArmor.hidden(player, EquipmentSlot.FEET)) {
			state.feetEquipment = ItemStack.EMPTY;
		}
	}

	/** One slot's eye: click to hide or show that piece. */
	private static final class Toggle extends AbstractButton {
		private final AbstractContainerScreen<?> screen;
		private final int index;

		Toggle(AbstractContainerScreen<?> screen, int index) {
			super(0, 0, SIZE, SIZE, Component.empty());
			this.screen = screen;
			this.index = index;
		}

		/** Beside its armour slot: right of it in survival, under it on the creative inventory tab. */
		void place() {
			ContainerScreenAccessor at = (ContainerScreenAccessor)this.screen;
			int x = at.jaymospowers$leftPos();
			int y = at.jaymospowers$topPos();
			if (this.screen instanceof CreativeModeInventoryScreen creative) {
				this.visible = creative.isInventoryOpen();
				// head and chest at x 54, legs and feet at x 108; the top row at y 6, the bottom at y 33
				x += (this.index < 2 ? 54 : 108) + 5;
				y += (this.index % 2 == 0 ? 6 : 33) + 17;
			} else {
				this.visible = true;
				x += 8 + 18;
				y += 8 + this.index * 18 + 5;
			}
			setPosition(x, y);
			setTooltip(Tooltip.create(Component.translatable(hidden() ? "button.jaymospowers.armor.show" : "button.jaymospowers.armor.hide",
				Component.translatable("button.jaymospowers.armor." + HiddenArmor.SLOTS[this.index].getName()))));
		}

		private boolean hidden() {
			LocalPlayer player = Minecraft.getInstance().player;
			return player != null && HiddenArmor.hidden(player, HiddenArmor.SLOTS[this.index]);
		}

		@Override
		public void onPress(InputWithModifiers input) {
			ClientPlayNetworking.send(new HiddenArmor.Toggle(this.index));
		}

		@Override
		protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
			int x = getX();
			int y = getY();
			graphics.fill(x, y, x + SIZE, y + SIZE, BORDER);
			graphics.fill(x + 1, y + 1, x + SIZE - 1, y + SIZE - 1, isHoveredOrFocused() ? FACE_HOVER : FACE);
			if (hidden()) {
				// a struck-out eye
				for (int i = 1; i < SIZE - 1; i++) {
					graphics.fill(x + i, y + i, x + i + 1, y + i + 1, HIDDEN);
				}
				graphics.fill(x + 2, y + 3, x + SIZE - 2, y + 4, HIDDEN);
			} else {
				// an open eye: the lids and the pupil
				graphics.fill(x + 2, y + 2, x + SIZE - 2, y + 3, SHOWN);
				graphics.fill(x + 1, y + 3, x + SIZE - 1, y + 4, SHOWN);
				graphics.fill(x + 2, y + 4, x + SIZE - 2, y + 5, SHOWN);
				graphics.fill(x + 3, y + 3, x + 4, y + 4, BORDER);
			}
		}

		@Override
		protected void updateWidgetNarration(NarrationElementOutput output) {
			defaultButtonNarrationText(output);
		}
	}

}
