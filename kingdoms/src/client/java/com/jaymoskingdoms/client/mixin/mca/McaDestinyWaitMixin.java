package com.jaymoskingdoms.client.mixin.mca;

import com.jaymoskingdoms.Classes;
import com.jaymoskingdoms.Heritage;
import com.jaymoskingdoms.LifeStory;
import net.conczin.mca.DestinyManager;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * (0.7.229) Andy: "can you choose your characters story BEFORE the MCA creator and name choosing?" MCA's start-up
 * creator waits while a newcomer's life story is still to tell (the server opens the story first), and opens once it's
 * told. It's held, not dropped: MCA still means to open it, so it does as soon as the story is in.
 */
@Mixin(DestinyManager.class)
abstract class McaDestinyWaitMixin {
	@Inject(method = "tick", at = @At("HEAD"), cancellable = true)
	private void jaymoskingdoms$storyFirst(Minecraft minecraft, CallbackInfo ci) {
		if (minecraft.player != null && LifeStory.of(minecraft.player) == null && Heritage.of(minecraft.player) == null
			&& Classes.classOf(minecraft.player) == null && ClientPlayNetworking.canSend(LifeStory.Live.TYPE)) {
			ci.cancel();
		}
	}
}
