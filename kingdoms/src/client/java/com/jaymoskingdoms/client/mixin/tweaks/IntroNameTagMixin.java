package com.jaymoskingdoms.client.mixin.tweaks;

import com.jaymoskingdoms.Introductions;
import com.jaymoskingdoms.client.JobTitles;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * (0.7.180) No name over the head of a villager or player you haven't been told the name of ({@link Introductions});
 * once you know it, a ruler's carries their title ("Queen Elsa"), and (0.7.218) a player's job shows under it ("Master
 * Woodcutter", {@link JobTitles}) where nothing else does. Before Jaymo's Powers puts a costume's name on
 * the avatar's state, which still shows to everyone.
 */
@Mixin(EntityRenderer.class)
abstract class IntroNameTagMixin {
	@Inject(method = "extractNameTags(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/client/renderer/entity/state/EntityRenderState;FDD)V", at = @At("RETURN"))
	private void jaymoskingdoms$introduced(Entity entity, EntityRenderState state, float partialTicks, double a, double b, CallbackInfo ci) {
		if (state.nameTag == null) {
			return;
		}
		if (!Introductions.knownHere(entity)) {
			state.nameTag = null;
			state.scoreText = null;
			return;
		}
		Component title = entity.getAttached(Introductions.TITLE);
		if (title != null) {
			state.nameTag = title.copy().append(" ").append(state.nameTag);
		}
		Component job = entity instanceof Player ? JobTitles.of(entity.getUUID()) : null;
		if (job != null && state.scoreText == null) {
			// (0.7.218) a player's job, under their name
			state.scoreText = job;
		}
	}
}
