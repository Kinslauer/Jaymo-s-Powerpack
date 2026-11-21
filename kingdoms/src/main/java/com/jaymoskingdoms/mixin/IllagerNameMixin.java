package com.jaymoskingdoms.mixin;

import com.jaymoskingdoms.IllagerTalk;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Andy (2026-11-18): "the pillagers dont have names for each other". On the server an illager goes by its own name
 * ({@link IllagerTalk#nameOf}) wherever it's named without a name tag: what its fellows shout about it ("I've got Vorik,
 * cover me!"), and its death in chat. The tag over its head still waits until it tells you (Introductions), and a name
 * tag still wins.
 */
@Mixin(Entity.class)
abstract class IllagerNameMixin {
	@Inject(method = "getTypeName", at = @At("HEAD"), cancellable = true)
	private void jaymoskingdoms$illagerName(CallbackInfoReturnable<Component> cir) {
		String name = IllagerTalk.nameOf((Entity) (Object) this);
		if (name != null) {
			cir.setReturnValue(Component.literal(name));
		}
	}
}
