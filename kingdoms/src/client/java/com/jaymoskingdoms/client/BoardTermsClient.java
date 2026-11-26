package com.jaymoskingdoms.client;

import com.jaymoskingdoms.BoardTerms;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;

/** (village mods 4) The work on a notice you took is done: a toast says so ({@link BoardTerms.Toast}). */
final class BoardTermsClient {
	private BoardTermsClient() {
	}

	static void register() {
		ClientPlayNetworking.registerGlobalReceiver(BoardTerms.Toast.TYPE, (payload, context) -> {
			Minecraft mc = Minecraft.getInstance();
			SystemToast.addOrUpdate(mc.gui.toastManager(), new SystemToast.SystemToastId(BoardTerms.TOAST_MS), payload.title(), payload.text());
		});
	}
}
