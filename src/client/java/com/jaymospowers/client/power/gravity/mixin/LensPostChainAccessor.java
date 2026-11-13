package com.jaymospowers.client.power.gravity.mixin;

import java.util.List;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.PostPass;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** A post effect's passes, so the lensing effect's settings can be written each frame (see LensClient). */
@Mixin(PostChain.class)
public interface LensPostChainAccessor {
	@Accessor("passes")
	List<PostPass> jaymospowers$passes();
}
