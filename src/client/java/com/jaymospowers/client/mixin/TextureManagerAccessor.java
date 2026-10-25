package com.jaymospowers.client.mixin;

import java.util.Map;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * (0.16.16) The textures already registered, so the fusion's look can read one without {@code getTexture} loading (and
 * registering a missing texture for) one that hasn't arrived yet, such as an MCA outfit still downloading.
 */
@Mixin(TextureManager.class)
public interface TextureManagerAccessor {
	@Accessor("byPath")
	Map<Identifier, AbstractTexture> jaymospowers$byPath();
}
