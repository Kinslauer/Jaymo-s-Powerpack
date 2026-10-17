package com.jaymoskingdoms.client;

import com.jaymoskingdoms.JaymosKingdoms;
import com.mojang.blaze3d.platform.NativeImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import net.conczin.mca.client.render.VillagerVisuals;
import net.conczin.mca.resources.FaceList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.util.ARGB;
import org.jspecify.annotations.Nullable;

/**
 * An MCA villager's eyes, glowing (for {@link VillagerLooks}). MCA draws a villager's eyes as their own face texture
 * (the eye pixels alone, picked by their face gene, {@code FaceLayer}): that face, every pixel of it painted one colour,
 * is an eyes-layer texture that lights exactly their eyes. Only called with MCA loaded.
 */
final class McaEyes {
	/** By face texture and colour: its glowing copy (or null when it couldn't be read). */
	private static final Map<String, Identifier> MADE = new HashMap<>();

	private McaEyes() {
	}

	/** The glowing-eyes texture for the MCA villager drawn with {@code state}, in {@code color}; null if it isn't one, or its face can't be read. */
	static @Nullable Identifier glowing(Object state, int color) {
		Identifier face;
		try {
			FaceList faces = FaceList.getInstance();
			if (faces == null) {
				return null;
			}
			face = faces.pick("normal", VillagerVisuals.require(state).faceGene());
		} catch (RuntimeException e) {
			return null;
		}
		if (face == null) {
			return null;
		}
		String key = face + "#" + Integer.toHexString(color);
		if (MADE.containsKey(key)) {
			return MADE.get(key);
		}
		Minecraft minecraft = Minecraft.getInstance();
		Optional<Resource> resource = minecraft.getResourceManager().getResource(face);
		Identifier made = null;
		if (resource.isPresent()) {
			try (InputStream in = resource.get().open(); NativeImage pixels = NativeImage.read(in)) {
				NativeImage eyes = new NativeImage(pixels.getWidth(), pixels.getHeight(), true);
				for (int x = 0; x < pixels.getWidth(); x++) {
					for (int y = 0; y < pixels.getHeight(); y++) {
						if (ARGB.alpha(pixels.getPixel(x, y)) > 0) {
							eyes.setPixel(x, y, color);
						}
					}
				}
				made = JaymosKingdoms.id("mca_eyes/" + Integer.toHexString(key.hashCode()) + "_" + MADE.size());
				minecraft.getTextureManager().register(made, new DynamicTexture(made::toString, eyes));
			} catch (IOException | RuntimeException e) {
				made = null;
			}
		}
		MADE.put(key, made);
		return made;
	}
}
