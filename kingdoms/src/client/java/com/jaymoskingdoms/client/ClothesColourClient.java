package com.jaymoskingdoms.client;

import com.jaymoskingdoms.ClothesColour;
import com.mojang.blaze3d.platform.NativeImage;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;

/**
 * (0.7.332) Reads the colour of the MCA clothes this client's own player is drawn in and tells the server
 * ({@link ClothesColour}): the beast's rags and the torn cloth are dyed the same. Fed by {@link McaSkinCover#clothes}.
 */
public final class ClothesColourClient {
	/** Pixels at least this opaque count (out of 255). */
	static final int SOLID_ALPHA = 128;
	/** Rows above this are the head and hat (a 64x64 MCA body): left out, so a hood doesn't colour the shirt. */
	static final int BODY_FROM = 16;

	/** Per clothes texture, its colour (resource textures; library clothes are read each time they load). */
	private static final Map<Identifier, Integer> KNOWN = new HashMap<>();
	private static Identifier last;
	private static int told = -1;

	private ClothesColourClient() {
	}

	static void register() {
		// a new server hasn't been told
		net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.JOIN.register((handler, sender, minecraft) -> {
			last = null;
			told = -1;
		});
	}

	/** Whether {@code texture}, just drawn on the local player, still needs reading (if it's known, it's told now). */
	static boolean wants(Identifier texture) {
		if (texture.equals(last) && told >= 0) {
			return false;
		}
		Integer colour = KNOWN.get(texture);
		if (colour != null) {
			last = texture;
			if (colour >= 0) {
				tell(colour);
			}
			return false;
		}
		return true;
	}

	/** {@code texture}'s pixels, read: its colour is kept and told. */
	static void worn(Identifier texture, NativeImage pixels) {
		last = texture;
		int colour = average(pixels);
		KNOWN.put(texture, colour);
		if (colour >= 0) {
			tell(colour);
		}
	}

	private static void tell(int colour) {
		if (colour != told && Minecraft.getInstance().getConnection() != null && ClientPlayNetworking.canSend(ClothesColour.Tell.TYPE)) {
			told = colour;
			ClientPlayNetworking.send(new ClothesColour.Tell(colour));
		}
	}

	/** The mean colour of the solid pixels below the head, or -1 if there are none. */
	private static int average(NativeImage image) {
		long r = 0, g = 0, b = 0, n = 0;
		for (int y = Math.min(BODY_FROM, image.getHeight()); y < image.getHeight(); y++) {
			for (int x = 0; x < image.getWidth(); x++) {
				int p = image.getPixel(x, y);
				if (ARGB.alpha(p) >= SOLID_ALPHA) {
					r += ARGB.red(p);
					g += ARGB.green(p);
					b += ARGB.blue(p);
					n++;
				}
			}
		}
		return n == 0 ? -1 : ARGB.color(0, (int) (r / n), (int) (g / n), (int) (b / n)) & 0xFFFFFF;
	}
}
