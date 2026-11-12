package com.jaymoskingdoms.client.thirst;

import com.jaymoskingdoms.thirst.Thirst;
import com.jaymoskingdoms.thirst.ThirstForms;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.HitResult;

/**
 * The thirsty player's one input: crouch and use, empty-handed, on the open air (nothing in reach under the crosshair). The server
 * opens the shape menu, or, in a shape, turns them back. Sent once per press; it adds no key.
 */
public final class ThirstInput {
	private static boolean was;

	private ThirstInput() {
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(minecraft -> {
			boolean down = minecraft.player != null && minecraft.gui.screen() == null && minecraft.options.keyUse.isDown();
			if (down && !was && minecraft.player != null && Thirst.thirsty(minecraft.player) && minecraft.player.isShiftKeyDown()
				&& minecraft.player.getMainHandItem().isEmpty() && open(minecraft) && ClientPlayNetworking.canSend(ThirstForms.Menu.TYPE)) {
				ClientPlayNetworking.send(new ThirstForms.Menu());
			}
			was = down;
		});
	}

	/** Nothing under the crosshair to use. */
	private static boolean open(Minecraft minecraft) {
		return minecraft.hitResult == null || minecraft.hitResult.getType() == HitResult.Type.MISS;
	}
}
