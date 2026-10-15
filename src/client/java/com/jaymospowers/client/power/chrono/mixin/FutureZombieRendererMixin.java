package com.jaymospowers.client.power.chrono.mixin;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.power.chrono.future.Future;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.renderer.entity.AbstractZombieRenderer;
import net.minecraft.client.renderer.entity.state.ZombieRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.monster.zombie.Zombie;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The future's plain zombies wear the clothes they died in: each grown one there gets one of our undead civilian
 * skins (tools/gen_future_zombies.py), picked by its id so it keeps it. Husks, drowned and Kitchen's zombie players
 * keep their own looks.
 */
@Mixin(AbstractZombieRenderer.class)
abstract class FutureZombieRendererMixin {
	@Unique
	private static final int SKINS = 16;
	@Unique
	private static final Identifier[] CIVILIANS = new Identifier[SKINS];
	@Unique
	private static final RenderStateDataKey<Integer> CIVILIAN = RenderStateDataKey.create(() -> "jaymospowers:future_civilian");

	static {
		for (int i = 0; i < SKINS; i++) {
			CIVILIANS[i] = JaymosPowers.id("textures/entity/future/civilian_" + i + ".png");
		}
	}

	@Inject(
		method = "extractRenderState(Lnet/minecraft/world/entity/monster/zombie/Zombie;Lnet/minecraft/client/renderer/entity/state/ZombieRenderState;F)V",
		at = @At("TAIL"))
	private void jaymospowers$futureCivilian(Zombie zombie, ZombieRenderState state, float partialTicks, CallbackInfo ci) {
		boolean civilian = zombie.getClass() == Zombie.class && !state.isBaby && zombie.level().dimension() == Future.FUTURE;
		state.setData(CIVILIAN, civilian ? Math.floorMod(zombie.getUUID().hashCode(), SKINS) : null);
	}

	@Inject(
		method = "getTextureLocation(Lnet/minecraft/client/renderer/entity/state/ZombieRenderState;)Lnet/minecraft/resources/Identifier;",
		at = @At("HEAD"), cancellable = true)
	private void jaymospowers$futureCivilianSkin(ZombieRenderState state, CallbackInfoReturnable<Identifier> cir) {
		Integer skin = state.getData(CIVILIAN);
		if (skin != null) {
			cir.setReturnValue(CIVILIANS[skin]);
		}
	}
}
