package com.jaymospowers.client;

import com.jaymospowers.client.finisher.FinisherPose;
import com.jaymospowers.client.power.chrono.ChronoFlowClient;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.jspecify.annotations.Nullable;

/**
 * Tiv: "F5 should only toggle between first and 3rd person, the front facing camera should be toggled on w/ f6."
 * Andy (2026-11-12): "f5 should toggle between first and third person, and f7 should be the front facing view. our custom
 * third person should remain as is." So the front camera's own key (F7 by default; Simple Voice Chat's menu moved to F6,
 * {@code KeyConflicts} under Miscellaneous in the controls beside Toggle Perspective) turns the
 * view round to look at you; pressed again, it goes back to the view you had before (first person or from behind),
 * or from behind if that isn't known. F5 never lands on it ({@code FrontCameraCycleMixin}, and
 * {@code ShoulderSurfingFrontMixin} for Shoulder Surfing's F5).
 * <p>
 * While a film or a power holds the camera (an ultimate's or a beam battle's film, Time Flow's shoulder view, a
 * finisher watched from behind), presses do nothing, so they don't fight it or fire once it ends. Those put back the
 * view they found, so the front camera comes back after them, and pressing again still returns to the view before it.
 */
public final class FrontCamera {
	private static @Nullable KeyMapping key;
	/** The view before the key turned it to the front. */
	private static @Nullable CameraType viewBefore;

	private FrontCamera() {
	}

	static void init() {
		key = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.jaymospowers.front_camera", InputConstants.KEY_F7, KeyMapping.Category.MISC));
		// before the tick's films and powers set the view, so one that holds it puts it straight back
		ClientTickEvents.START_CLIENT_TICK.register(FrontCamera::tick);
	}

	private static void tick(Minecraft minecraft) {
		if (key == null || minecraft.player == null || minecraft.gui.screen() != null) {
			return;
		}
		while (key.consumeClick()) {
			if (!held()) {
				toggle(minecraft);
			}
		}
	}

	/** Whether a film or a power holds the camera now. */
	private static boolean held() {
		return UltimateCinematic.filming() || BeamCinematic.running() || ChronoFlowClient.holdsView() || FinisherPose.holdsView();
	}

	private static void toggle(Minecraft minecraft) {
		CameraType now = minecraft.options.getCameraType();
		CameraType next;
		if (now == CameraType.THIRD_PERSON_FRONT) {
			next = viewBefore != null ? viewBefore : CameraType.THIRD_PERSON_BACK;
			viewBefore = null;
		} else {
			viewBefore = now;
			next = CameraType.THIRD_PERSON_FRONT;
		}
		minecraft.options.setCameraType(next);
		// as F5 does: a spectated creeper's or spider's view filter only shows in first person
		if (now.isFirstPerson() != next.isFirstPerson()) {
			minecraft.gameRenderer.checkEntityPostEffect(next.isFirstPerson() ? minecraft.getCameraEntity() : null);
		}
	}
}
