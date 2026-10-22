package com.jaymospowers.client.mixin;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** (0.15.65) Lets {@code EmberKindleClient} throw the punch it held back when a left click turned out not to be Kindle. */
@Mixin(Minecraft.class)
public interface MinecraftAttackInvoker {
	@Invoker("startAttack")
	boolean jaymospowers$startAttack();
}
