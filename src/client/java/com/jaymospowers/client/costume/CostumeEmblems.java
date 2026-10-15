package com.jaymospowers.client.costume;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.costume.CostumeDesign;
import com.mojang.blaze3d.platform.NativeImage;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

/**
 * The emblems' textures: one small dynamic texture per emblem as it looks in its design's colours, made the first time
 * it is drawn and kept while anything draws it. One not drawn for {@link #UNUSED_TICKS} is freed (checked every
 * {@link #SWEEP_TICKS}), so painting in the designer (a new emblem each stroke) doesn't pile textures up.
 */
final class CostumeEmblems {
	/** Free an emblem's texture after this long undrawn (client ticks). */
	static final int UNUSED_TICKS = 600;
	/** How often unused textures are looked for. */
	static final int SWEEP_TICKS = 100;

	private record Entry(Identifier id, long[] lastUsed) {
	}

	private static final Map<String, Entry> TEXTURES = new HashMap<>();
	private static long now;
	private static int counter;

	private CostumeEmblems() {
	}

	/** The texture for this design's emblem (call only when it has one). */
	static Identifier texture(CostumeDesign design) {
		String key = design.emblem() + "|" + design.primary().getId() + "|" + design.secondary().getId();
		Entry entry = TEXTURES.get(key);
		if (entry == null) {
			byte[] pixels = design.pixels();
			NativeImage image = new NativeImage(CostumeDesign.EMBLEM_SIZE, CostumeDesign.EMBLEM_SIZE, true);
			for (int y = 0; y < CostumeDesign.EMBLEM_SIZE; y++) {
				for (int x = 0; x < CostumeDesign.EMBLEM_SIZE; x++) {
					image.setPixel(x, y, design.colour(pixels[y * CostumeDesign.EMBLEM_SIZE + x]));
				}
			}
			Identifier id = JaymosPowers.id("costume_emblem/" + Integer.toHexString(key.hashCode()) + "_" + counter++);
			Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(id::toString, image));
			entry = new Entry(id, new long[1]);
			TEXTURES.put(key, entry);
		}
		entry.lastUsed()[0] = now;
		return entry.id();
	}

	/** Every client tick: frees textures nothing has drawn for a while. */
	static void tick(Minecraft minecraft) {
		now++;
		if (now % SWEEP_TICKS != 0) {
			return;
		}
		for (Iterator<Entry> it = TEXTURES.values().iterator(); it.hasNext(); ) {
			Entry entry = it.next();
			if (now - entry.lastUsed()[0] > UNUSED_TICKS) {
				minecraft.getTextureManager().release(entry.id());
				it.remove();
			}
		}
	}
}
