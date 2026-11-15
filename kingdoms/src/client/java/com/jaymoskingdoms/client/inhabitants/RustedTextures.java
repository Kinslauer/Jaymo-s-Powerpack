package com.jaymoskingdoms.client.inhabitants;

import com.jaymoskingdoms.JaymosKingdoms;
import com.mojang.blaze3d.platform.NativeImage;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;

/**
 * Andy: "draugr should have varied levels of rusted armor, so should traveler skeletons". Their mail, helms, pauldrons,
 * shield rims and blades are drawn in their own textures, not worn as items, so each one's rust is painted into a copy of
 * its texture: a stage {@code 1..}{@link #STAGES} from its id ({@link #stage}), so every one is its own and stays so.
 * <p>
 * Only iron is painted: cool grey pixels (blue at least as strong as green, green as red, as {@code tools/gen_tomb_undead.py}
 * colours iron, mail and steel, and its blackened look's, but not as blue as the shroud), not too dark and not pale rime
 * or bone. A draugr chieftain's
 * bronze is warm and so is left alone, as are flesh, fur, leather and wood. Rust starts in pits and the darker creases
 * and spreads with the stage, orange-brown, keeping the metal's light and shade. The same look as Jaymo's Powers' rusty
 * armour pieces.
 */
public final class RustedTextures {
	public static final int STAGES = 10;
	/** How much of the iron the worst rust covers, and how strong a patch is at its edge and heart. */
	static final float COVER = 0.92F, EDGE = 0.5F, HEART = 0.9F, CURVE = 0.6F;
	/** Iron's limits: its brightness (0..255), and how far its blue may run past its red, a share of its brightness (a
	 * draugr's dark blue-grey shroud runs further). */
	static final int DARKEST = 45, PALEST = 185;
	static final float COOLEST = 0.22F;
	static final int LIGHT = 0xA65A2A, DARK = 0x5A2A14;

	private static final Map<String, Identifier> MADE = new HashMap<>();
	private static int made;
	private static boolean registered;

	private RustedTextures() {
	}

	public static void register() {
		if (registered) {
			return;
		}
		registered = true;
		ResourceLoader.get(PackType.CLIENT_RESOURCES).registerReloadListener(JaymosKingdoms.id("rusted_textures"),
			(ResourceManagerReloadListener) manager -> reset());
	}

	private static void reset() {
		Minecraft minecraft = Minecraft.getInstance();
		for (Identifier id : MADE.values()) {
			minecraft.getTextureManager().release(id);
		}
		MADE.clear();
	}

	/** A stage from {@code least} to {@link #STAGES}, fixed by {@code roll} (an id's hash). */
	public static int stage(int roll, int least) {
		int mixed = roll * 0x9E3779B1;
		return least + Math.floorMod(mixed >>> 7, STAGES - least + 1);
	}

	/** {@code base} with rust of {@code stage} painted into its iron, or {@code base} when stage is 0 or it can't be read. */
	public static Identifier rusted(Identifier base, int stage) {
		if (stage <= 0) {
			return base;
		}
		String key = base + "|" + stage;
		Identifier done = MADE.get(key);
		if (done != null) {
			return done;
		}
		NativeImage image = read(base);
		if (image == null) {
			MADE.put(key, base);
			return base;
		}
		float threshold = 1.0F - COVER * (float) Math.pow(stage / (float) STAGES, CURVE);
		for (int y = 0; y < image.getHeight(); y++) {
			for (int x = 0; x < image.getWidth(); x++) {
				int argb = image.getPixel(x, y);
				int a = argb >>> 24;
				int r = argb >> 16 & 0xFF, g = argb >> 8 & 0xFF, b = argb & 0xFF;
				float lum = r * 0.3F + g * 0.59F + b * 0.11F;
				if (a == 0 || lum < DARKEST || lum > PALEST || b < g - 3 || g < r - 3 || b - r > COOLEST * lum) {
					continue;
				}
				float pit = pit(x, y), patch = pit(x / 3 + 101, y / 3 + 57);
				float order = 0.55F * patch + 0.3F * pit + 0.35F * (0.5F - lum / 255.0F);
				if (order <= threshold) {
					continue;
				}
				float strength = EDGE + (HEART - EDGE) * Math.min(1.0F, (order - threshold) * 4.0F);
				float k = 0.65F + 0.35F * lum / 255.0F;
				int rr = Math.round(mix(LIGHT >> 16 & 0xFF, DARK >> 16 & 0xFF, pit) * k);
				int rg = Math.round(mix(LIGHT >> 8 & 0xFF, DARK >> 8 & 0xFF, pit) * k);
				int rb = Math.round(mix(LIGHT & 0xFF, DARK & 0xFF, pit) * k);
				image.setPixel(x, y, a << 24 | Math.round(mix(r, rr, strength)) << 16 | Math.round(mix(g, rg, strength)) << 8
					| Math.round(mix(b, rb, strength)));
			}
		}
		Identifier id = JaymosKingdoms.id("rusted/" + made++);
		Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(id::toString, image));
		MADE.put(key, id);
		return id;
	}

	private static float mix(float from, float to, float t) {
		return Mth.clamp(from + (to - from) * t, 0.0F, 255.0F);
	}

	/** A fixed speckle (0..1) for pixel ({@code x}, {@code y}). */
	static float pit(int x, int y) {
		int n = x * 374761393 + y * 668265263;
		n = (n ^ (n >>> 13)) * 1274126177;
		return ((n ^ (n >>> 16)) & 0xFFFF) / 65535.0F;
	}

	private static @Nullable NativeImage read(Identifier texture) {
		var resource = Minecraft.getInstance().getResourceManager().getResource(texture).orElse(null);
		if (resource == null) {
			return null;
		}
		try (InputStream in = resource.open()) {
			return NativeImage.read(in);
		} catch (Exception e) {
			return null;
		}
	}
}
