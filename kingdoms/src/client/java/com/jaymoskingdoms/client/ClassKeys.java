package com.jaymoskingdoms.client;

import com.jaymoskingdoms.Absorption;
import com.jaymoskingdoms.Classes;
import com.jaymoskingdoms.SecondClass;
import com.jaymoskingdoms.BloodBlasts;
import com.jaymoskingdoms.Hemomancy;
import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.Spellcraft;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/** (0.6.0) The classes' key: {@code J} imbues a Sorcerer's blade ({@link Spellcraft}); sneak and {@code J} throws the bolt. */
final class ClassKeys {
	private static KeyMapping imbue, growth;
	/** (0.7.554) A Blood Sorcerer's J is down (BloodBlasts.Hold sent). */
	private static boolean bloodHeld;
	/** (0.7.570) Any other Sorcerer's J is down (Absorption.Hold sent). */
	private static boolean elementHeld;

	private ClassKeys() {
	}

	/** Whether either Alt key is down. */
	private static boolean altHeld(Minecraft minecraft) {
		return InputConstants.isKeyDown(InputConstants.KEY_LALT) || InputConstants.isKeyDown(InputConstants.KEY_RALT);
	}

	static void register() {
		KeyMapping.Category category = KeyMapping.Category.register(JaymosKingdoms.id("classes"));
		imbue = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.jaymoskingdoms.imbue", InputConstants.KEY_J, category));
		// the feats and paths screen, for a pack without the H menu (unbound unless the player binds it)
		growth = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.jaymoskingdoms.growth", InputConstants.UNKNOWN.getValue(), category));
		ClientTickEvents.END_CLIENT_TICK.register(minecraft -> {
			while (growth.consumeClick()) {
				if (minecraft.player != null && minecraft.gui.screen() == null) {
					minecraft.gui.setScreen(new GrowthScreen());
				}
			}
			while (imbue.consumeClick()) {
				if (minecraft.player != null && minecraft.gui.screen() == null) {
					// Alt held: the second class's button (the first class's J is unchanged)
					if (Classes.second(minecraft.player) != null && altHeld(minecraft)) {
						ClientPlayNetworking.send(new SecondClass.Key(minecraft.player.isShiftKeyDown()));
						continue;
					}
					// (0.7.554) a Blood Sorcerer's J is held, not pressed: gather the blood near, empty-handed throw it
					// (BloodBlasts; a tap is still the Imbue, decided on the server when it's let go)
					if (Hemomancy.bloodMage(minecraft.player) && !minecraft.player.isShiftKeyDown()) {
						if (!bloodHeld) {
							bloodHeld = true;
							ClientPlayNetworking.send(new BloodBlasts.Hold(true));
						}
					} else if (Absorption.absorber(minecraft.player) && !minecraft.player.isShiftKeyDown()) {
						// (0.7.570) every other element: held to absorb it (fire: the cone), empty-handed to throw it
						if (!elementHeld) {
							elementHeld = true;
							ClientPlayNetworking.send(new Absorption.Hold(true));
						}
					} else {
						ClientPlayNetworking.send(new Spellcraft.Imbue(minecraft.player.isShiftKeyDown()));
					}
				}
			}
			if (bloodHeld && (!imbue.isDown() || minecraft.player == null || minecraft.gui.screen() != null)) {
				bloodHeld = false;
				if (minecraft.getConnection() != null) {
					ClientPlayNetworking.send(new BloodBlasts.Hold(false));
				}
			}
			if (elementHeld && (!imbue.isDown() || minecraft.player == null || minecraft.gui.screen() != null)) {
				elementHeld = false;
				if (minecraft.getConnection() != null) {
					ClientPlayNetworking.send(new Absorption.Hold(false));
				}
			}
		});
		// (0.7.49) an imbued blade says whose element it carries and for how long, in anyone's hands
		ItemTooltipCallback.EVENT.register((stack, context, flag, lines) -> {
			Spellcraft.Charge charge = Spellcraft.charge(stack);
			Minecraft minecraft = Minecraft.getInstance();
			if (charge == null || minecraft.level == null) {
				return;
			}
			long left = charge.until() - minecraft.level.getGameTime();
			if (left <= 0) {
				return;
			}
			lines.add(Math.min(1, lines.size()), Component.translatable("tooltip.jaymoskingdoms.imbued",
				Component.translatable("class.jaymoskingdoms.option." + charge.element()), charge.name(), left / 20)
				.withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.ITALIC));
		});
	}
}
