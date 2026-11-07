package com.jaymospowers.client.power.xenoform;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.power.xenoform.BandStyle;
import com.mojang.blaze3d.platform.NativeImage;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;

/**
 * (0.18.126, Xeno-Band patch 9) The band's looks, painted on the client from the skins we already have, so every alien
 * (and any added later) gets them without new art:
 * <ul>
 * <li><b>Armoured:</b> the skin worked into plates: a dark seam every {@link #PLATE_ROWS} rows, a lit top edge, rivets,
 * and the colour pulled toward steel.</li>
 * <li><b>Weathered:</b> faded and greyed, grime, and pale scratches running on the slant.</li>
 * <li><b>The glow:</b> the band's green (the lines, the insignia, its pale heart and dark rim; exact colours from
 * {@code tools/gen_xenoform.py}) repainted in the chosen colour. On the band's own textures every green-led pixel is
 * repainted by its brightness, as its glow is shaded.</li>
 * </ul>
 * Each skin, look and colour is painted once, the first time it's drawn, and kept (as a dynamic texture) until the
 * resources reload.
 */
public final class XenoLooks {
	// ---- tuning ----
	/** Armoured: the plates' height in pixels, how hard the seams darken and top edges lighten, the pull toward steel. */
	static final int PLATE_ROWS = 4;
	static final float SEAM = 0.55F, EDGE = 1.18F, STEEL_PULL = 0.35F;
	static final int STEEL = 0x8C96A0, RIVET = 0xC8D0D8;
	/** Weathered: how much colour it loses, its grime and scratches (shares of pixels), and their strength. */
	static final float FADE = 0.35F, GRIME = 0.12F, GRIME_DARK = 0.7F, SCRATCH = 0.05F, SCRATCH_LIGHT = 0.45F;
	static final int DUST = 0x7A6A55;
	static final float DUST_PULL = 0.15F;

	/** The band's colours in the skins (RGB; NativeImage.getPixel reads ARGB). */
	static final int GREEN = 0x5CF07A, PALE_GREEN = 0xBEFFCD, DEEP_GREEN = 0x0C3018;

	private static final Map<String, Identifier> MADE = new HashMap<>();
	private static int made;

	private XenoLooks() {
	}

	static void init() {
		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> MADE.clear());
	}

	/** Forgets every painted texture (the resources reloaded). */
	static void reset() {
		MADE.clear();
	}

	/** {@code base} (an alien's skin) in {@code look}, its green lines in {@code glow}; {@code base} itself when that's all plain. */
	public static Identifier skin(Identifier base, BandStyle.Look look, int glow) {
		if (look == BandStyle.Look.PLAIN && glow == BandStyle.GREEN) {
			return base;
		}
		return paint(base, look, glow, false);
	}

	/** One of the band's own textures with its green repainted in {@code glow}. */
	public static Identifier band(Identifier base, int glow) {
		if (glow == BandStyle.GREEN) {
			return base;
		}
		return paint(base, BandStyle.Look.PLAIN, glow, true);
	}

	private static Identifier paint(Identifier base, BandStyle.Look look, int glow, boolean band) {
		String key = base + "|" + look.id + "|" + Integer.toHexString(glow) + (band ? "|b" : "");
		Identifier done = MADE.get(key);
		if (done != null) {
			return done;
		}
		NativeImage image = read(base);
		if (image == null) {
			// can't be read (yet): draw the plain skin, and try again next time
			return base;
		}
		for (int y = 0; y < image.getHeight(); y++) {
			for (int x = 0; x < image.getWidth(); x++) {
				int argb = image.getPixel(x, y);
				int a = argb >>> 24;
				if (a == 0) {
					continue;
				}
				int rgb = argb & 0xFFFFFF;
				int out;
				if (band) {
					out = bandGlow(rgb, glow);
				} else if (rgb == GREEN) {
					out = glow;
				} else if (rgb == PALE_GREEN) {
					out = mix(glow, 0xFFFFFF, 0.6F);
				} else if (rgb == DEEP_GREEN) {
					out = scale(glow, 0.2F);
				} else {
					out = switch (look) {
						case ARMOURED -> armoured(rgb, x, y);
						case WEATHERED -> weathered(rgb, x, y);
						default -> rgb;
					};
				}
				image.setPixel(x, y, a << 24 | out & 0xFFFFFF);
			}
		}
		Identifier id = JaymosPowers.id("xenolook/" + made++);
		Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(id::toString, image));
		MADE.put(key, id);
		return id;
	}

	private static @Nullable NativeImage read(Identifier texture) {
		Resource resource = Minecraft.getInstance().getResourceManager().getResource(texture).orElse(null);
		if (resource == null) {
			return null;
		}
		try (InputStream in = resource.open()) {
			return NativeImage.read(in);
		} catch (Exception e) {
			return null;
		}
	}

	// ---- the looks -------------------------------------------------------------------------------------------------

	private static int armoured(int rgb, int x, int y) {
		int out = mix(rgb, STEEL, STEEL_PULL);
		int row = y % PLATE_ROWS;
		if (row == PLATE_ROWS - 1) {
			out = scale(out, SEAM);
		} else if (row == 0) {
			out = scale(out, EDGE);
		} else if (row == 1 && (x + (y / PLATE_ROWS) * 3) % 6 == 1) {
			out = RIVET;
		}
		return out;
	}

	private static int weathered(int rgb, int x, int y) {
		int grey = luma(rgb);
		int out = mix(rgb, grey << 16 | grey << 8 | grey, FADE);
		out = mix(out, DUST, DUST_PULL);
		float h = hash(x, y);
		// scratches run on the slant: a pixel is scratched if its diagonal's start is
		if (hash(x - y, 7) < SCRATCH * 2.0F && hash(x + y, 13) < 0.5F) {
			out = mix(out, 0xFFFFFF, SCRATCH_LIGHT);
		} else if (h < GRIME) {
			out = scale(out, GRIME_DARK);
		}
		return out;
	}

	/** A pixel of the band's own glow, repainted: its brightness (by green) of the colour, as far to white as it was. */
	private static int bandGlow(int rgb, int glow) {
		int r = rgb >> 16 & 0xFF, g = rgb >> 8 & 0xFF, b = rgb & 0xFF;
		if (g <= r + 20 || g <= b + 20) {
			// not the band's green (its metal, its dark rim)
			return rgb;
		}
		float white = Mth.clamp((Math.min(r, b) - 92.0F) / (220.0F - 92.0F), 0.0F, 1.0F);
		float bright = g / 240.0F;
		return scale(mix(glow, 0xFFFFFF, white), bright);
	}

	// ---- colour sums -----------------------------------------------------------------------------------------------

	static int mix(int from, int to, float t) {
		int r = (int)Mth.lerp(t, from >> 16 & 0xFF, to >> 16 & 0xFF);
		int g = (int)Mth.lerp(t, from >> 8 & 0xFF, to >> 8 & 0xFF);
		int b = (int)Mth.lerp(t, from & 0xFF, to & 0xFF);
		return r << 16 | g << 8 | b;
	}

	private static int scale(int rgb, float k) {
		int r = Mth.clamp((int)((rgb >> 16 & 0xFF) * k), 0, 255);
		int g = Mth.clamp((int)((rgb >> 8 & 0xFF) * k), 0, 255);
		int b = Mth.clamp((int)((rgb & 0xFF) * k), 0, 255);
		return r << 16 | g << 8 | b;
	}

	private static int luma(int rgb) {
		return (int)(0.3F * (rgb >> 16 & 0xFF) + 0.59F * (rgb >> 8 & 0xFF) + 0.11F * (rgb & 0xFF));
	}

	/** A steady 0-1 for a pixel (the same every time, so the skin never shimmers). */
	private static float hash(int x, int y) {
		int h = x * 73856093 ^ y * 19349663;
		h ^= h >>> 13;
		h *= 0x5bd1e995;
		h ^= h >>> 15;
		return (h & 0xFFFF) / 65535.0F;
	}
}
