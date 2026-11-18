package com.jaymospowers.client.moving;

import com.jaymospowers.client.JaymosPowersClient;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;

/**
 * Auto walk: an unbound key that walks you forward until you press forward or back, open a screen, or die. The walking
 * itself is put into the movement keys by {@code AutoWalkInputMixin}.
 */
public final class AutoWalk {
	private static KeyMapping key;
	private static boolean on;

	private AutoWalk() {
	}

	public static void init() {
		key = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.jaymospowers.auto_walk", InputConstants.UNKNOWN.getValue(),
			JaymosPowersClient.CATEGORY));
		ClientTickEvents.END_CLIENT_TICK.register(AutoWalk::tick);
	}

	private static void tick(Minecraft minecraft) {
		if (minecraft.player == null || !minecraft.player.isAlive()) {
			on = false;
			return;
		}
		boolean pressed = false;
		while (key.consumeClick()) {
			pressed = true;
		}
		if (pressed && minecraft.gui.screen() == null) {
			on = !on;
			minecraft.player.sendOverlayMessage(Component.translatable(on ? "message.jaymospowers.auto_walk.on" : "message.jaymospowers.auto_walk.off"));
		} else if (on && minecraft.gui.screen() != null) {
			on = false;
		}
	}

	/** From the input mixin, after the keys are read: adds the walk, or ends it if forward or back was pressed. */
	public static void steer(Input keys, Vec2 move, java.util.function.BiConsumer<Input, Vec2> set) {
		if (!on) {
			return;
		}
		if (keys.forward() || keys.backward()) {
			on = false;
			return;
		}
		set.accept(new Input(true, false, keys.left(), keys.right(), keys.jump(), keys.shift(), keys.sprint()), new Vec2(move.x, 1.0F));
	}
}
