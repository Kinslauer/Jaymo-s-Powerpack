package com.jaymospowers.client.blood;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.Dismember;
import com.mojang.blaze3d.platform.NativeImage;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

/**
 * Andy (2026-11-25, of a guard's body the zombies had eaten the legs off): the blood "is drawn in the wrong place, it's
 * attached to limbs that aren't there". A painted overlay ({@link BloodCoatLayer#paints}: blood and dirt, nothing else
 * in it) has the parts a body has lost cut out of it, by the skin layout's own rectangles (each limb and the sleeve or
 * trouser leg over it, the head and its hat), so whatever model it ends up on, a lost part shows no blood of its own.
 * Only a square overlay in the player's layout (players, MCA folk, zombies and the rest) is cut; any other is left whole.
 */
final class LimbCut {
	/** In the 64-wide layout, per part: rectangles {x, y, width, height} of the part and of what's layered over it. */
	private static final int[][] HEAD = {{0, 0, 32, 16}, {32, 0, 32, 16}};
	private static final int[][] RIGHT_ARM = {{40, 16, 16, 16}, {40, 32, 16, 16}};
	private static final int[][] LEFT_ARM = {{32, 48, 16, 16}, {48, 48, 16, 16}};
	private static final int[][] RIGHT_LEG = {{0, 16, 16, 16}, {0, 32, 16, 16}};
	private static final int[][] LEFT_LEG = {{16, 48, 16, 16}, {0, 48, 16, 16}};
	/** Cut copies kept (per overlay and parts lost); past it they're all let go and cut again as needed. */
	private static final int MOST_KEPT = 200;

	private static final Map<String, Identifier> MADE = new HashMap<>();
	private static int made;

	private LimbCut() {
	}

	/** {@code overlay} with the parts in {@code gone} ({@link Dismember} bits) cut out; itself if nothing is gone or it can't be cut. */
	static Identifier cut(Identifier overlay, int gone) {
		gone &= Dismember.HEAD | Dismember.RIGHT_ARM | Dismember.LEFT_ARM | Dismember.RIGHT_LEG | Dismember.LEFT_LEG;
		if (gone == 0) {
			return overlay;
		}
		String key = overlay + "#" + gone;
		Identifier done = MADE.get(key);
		if (done != null) {
			return done;
		}
		AbstractTexture texture = Minecraft.getInstance().getTextureManager().getTexture(overlay);
		if (!(texture instanceof DynamicTexture dynamic) || dynamic.getPixels() == null) {
			return overlay;
		}
		NativeImage source = dynamic.getPixels();
		int w = source.getWidth(), h = source.getHeight();
		if (w != h || w < 64) {
			return overlay;
		}
		if (MADE.size() >= MOST_KEPT) {
			for (Identifier old : MADE.values()) {
				Minecraft.getInstance().getTextureManager().release(old);
			}
			MADE.clear();
		}
		NativeImage image = new NativeImage(w, h, true);
		image.copyFrom(source);
		int scale = w / 64;
		clear(image, gone, Dismember.HEAD, HEAD, scale);
		clear(image, gone, Dismember.RIGHT_ARM, RIGHT_ARM, scale);
		clear(image, gone, Dismember.LEFT_ARM, LEFT_ARM, scale);
		clear(image, gone, Dismember.RIGHT_LEG, RIGHT_LEG, scale);
		clear(image, gone, Dismember.LEFT_LEG, LEFT_LEG, scale);
		Identifier id = JaymosPowers.id("limb_cut/" + made++);
		Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(id::toString, image));
		MADE.put(key, id);
		return id;
	}

	private static void clear(NativeImage image, int gone, int part, int[][] rects, int scale) {
		if ((gone & part) == 0) {
			return;
		}
		for (int[] r : rects) {
			for (int y = r[1] * scale; y < (r[1] + r[3]) * scale; y++) {
				for (int x = r[0] * scale; x < (r[0] + r[2]) * scale; x++) {
					image.setPixel(x, y, 0);
				}
			}
		}
	}
}
