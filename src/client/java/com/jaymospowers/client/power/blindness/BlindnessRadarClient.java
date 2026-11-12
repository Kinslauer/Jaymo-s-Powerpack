package com.jaymospowers.client.power.blindness;

import com.jaymospowers.power.blindness.BlindnessRadar;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * The radar's marks on the dark ({@link BlindnessRadar}): each thing felt is a small mark pressed to the edge of the view
 * on the side it is on, so a threat behind you sits at the bottom and swings round the rim as you turn. Warnings (hostile,
 * behind or out of sight) are red and pulse, nearer ones larger; what lies ahead is pale and small; the marked quarry of a
 * pursuit is gold with a ring round it.
 */
public final class BlindnessRadarClient {
	/** How long a set of contacts stays when no newer set arrives (ticks). */
	static final int HOLD_TICKS = 14;
	/** How far in from the screen's edge the rim of marks runs (shares of half the width and half the height). */
	static final float RIM_X = 0.9F, RIM_Y = 0.86F;
	/** Mark half-sizes (pixels): far to near, for a warning and for the rest. */
	static final int WARN_FAR = 3, WARN_NEAR = 6, CALM = 2;
	static final int WARN_COLOR = 0xFF5A4A, CALM_COLOR = 0x9FD8FF, MARKED_COLOR = 0xFFC860;
	/** Range the marks scale over (blocks). */
	static final float NEAR = 4.0F, FAR = 24.0F;

	private static List<BlindnessRadar.Contact> contacts = new ArrayList<>();
	private static int age;

	private BlindnessRadarClient() {
	}

	static void init() {
		ClientPlayNetworking.registerGlobalReceiver(BlindnessRadar.Contacts.TYPE, (payload, context) -> context.client().execute(() -> {
			contacts = new ArrayList<>(payload.list());
			age = 0;
		}));
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (client.player == null || age++ > HOLD_TICKS) {
				contacts = new ArrayList<>();
			}
		});
	}

	static void render(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		if (player == null || contacts.isEmpty()) {
			return;
		}
		float partial = delta.getGameTimeDeltaPartialTick(false);
		int w = graphics.guiWidth(), h = graphics.guiHeight();
		float time = player.tickCount + partial;
		float yaw = player.getViewYRot(partial);
		Vec3 eye = player.getEyePosition(partial);
		float fade = Mth.clamp(1.0F - (age + partial - HOLD_TICKS * 0.5F) / (HOLD_TICKS * 0.5F), 0.0F, 1.0F);
		for (BlindnessRadar.Contact contact : contacts) {
			double dx = contact.x() - eye.x, dz = contact.z() - eye.z;
			float distance = (float)Math.sqrt(dx * dx + dz * dz);
			// the angle off straight ahead: 0 ahead, 90 on the right (yaw grows toward the player's right)
			float rel = Mth.wrapDegrees((float)(Math.toDegrees(Math.atan2(-dx, dz)) - yaw));
			float rad = rel * Mth.DEG_TO_RAD;
			int x = Math.round(w * 0.5F + Mth.sin(rad) * w * 0.5F * RIM_X);
			int y = Math.round(h * 0.5F - Mth.cos(rad) * h * 0.5F * RIM_Y);
			float near = 1.0F - Mth.clamp((distance - NEAR) / (FAR - NEAR), 0.0F, 1.0F);
			boolean warning = contact.kind() == BlindnessRadar.WARNING;
			boolean marked = contact.kind() == BlindnessRadar.MARKED;
			int color = marked ? MARKED_COLOR : warning ? WARN_COLOR : CALM_COLOR;
			float pulse = warning ? 0.65F + 0.35F * Mth.sin(time * 0.9F) : 1.0F;
			int size = marked ? WARN_NEAR - 1 : warning ? Math.round(Mth.lerp(near, WARN_FAR, WARN_NEAR)) : CALM;
			int alpha = Math.round(255.0F * fade * pulse * (marked || warning ? 1.0F : 0.55F));
			if (alpha <= 6) {
				continue;
			}
			graphics.fill(x - size, y - size, x + size + 1, y + size + 1, alpha << 24 | color);
			if (marked) {
				int ring = size + 3;
				int a = alpha / 2 << 24 | color;
				graphics.fill(x - ring, y - ring, x + ring + 1, y - ring + 1, a);
				graphics.fill(x - ring, y + ring, x + ring + 1, y + ring + 1, a);
				graphics.fill(x - ring, y - ring, x - ring + 1, y + ring + 1, a);
				graphics.fill(x + ring, y - ring, x + ring + 1, y + ring + 1, a);
			}
		}
	}
}
