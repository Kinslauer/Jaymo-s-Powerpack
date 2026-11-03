package com.jaymospowers.client.wet;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.wet.ArmourGrime;
import com.mojang.blaze3d.platform.NativeImage;
import java.io.InputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * (0.19.71) Andy: "the dirty armor should be splotchier and reflect the grime in the png, so you can see it cleaned if you
 * wash it manually". Dirty armour ({@link ArmourGrime}) is drawn with a copy of its own texture that has grime painted
 * into it in splotches, and its inventory icon gets the same splotches on top. Every wash (a scrub at a cauldron, a wet
 * sponge, a dunk, rain, wading) takes splotches away, edges first, until the piece is clean.
 * <p>
 * Where the grime goes comes from our own masks in {@code textures/grime/} ({@code tools/grime_textures.py}): alpha is how
 * early a pixel gets dirty (the bottoms of the legs, boots and chest hem first), grey is how dark the grime is there. Dust
 * and mud have their own masks and colours and blend by how muddy the piece is. The armour's own picture is read from
 * whatever resource pack is drawing it, so the grime always sits on the armour's pixels and nowhere else. Its dark
 * outline pixels are left clean so the shape still reads.
 * <p>
 * The colours suit the normal textures. With the optional Excalibur pack switched on, the grime takes Excalibur's grade
 * (darker, less saturated; {@code tools/excalibur_style/grade.py} on the default colours).
 */
public final class ArmourGrimeTextures {
	/** Dust and mud colours for the normal textures, and graded for Excalibur. */
	static final int DUST = 0xB59E78, MUD = 0x5C4630;
	static final int EXCALIBUR_DUST = 0x94836B, EXCALIBUR_MUD = 0x41382E;
	/** How many steps of dirtiness, and of muddiness, get their own picture. */
	static final int STAGES = 10, MUD_STEPS = 3;
	/** Even filthy, this share of the mask's order is never reached, so some armour always shows through. */
	static final float MOST_COVER = 0.8F;
	/** How far a grimy pixel goes toward the grime colour: at its edge, and at its heart. */
	static final float EDGE_STRENGTH = 0.45F, HEART_STRENGTH = 0.85F;
	/** Pixels darker than this (0..255 luminance) are outlines and stay clean. */
	static final int OUTLINE = 40;
	/** Painted pictures kept before all are thrown away and painted again as needed. */
	static final int MOST_KEPT = 600;

	private static final Map<String, Identifier> MADE = new HashMap<>();
	private static final Set<Identifier> UNREADABLE = new HashSet<>();
	private static @Nullable Mask armourDust, armourMud, itemDust, itemMud;
	private static boolean masksRead;
	private static boolean excalibur;
	private static int made;

	private ArmourGrimeTextures() {
	}

	public static void init() {
		ResourceLoader.get(PackType.CLIENT_RESOURCES).registerReloadListener(JaymosPowers.id("armour_grime"),
			(ResourceManagerReloadListener) manager -> reset());
		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> MADE.clear());
	}

	/** Throws every painted picture away (the resource packs changed, or too many were kept). */
	static void reset() {
		Minecraft minecraft = Minecraft.getInstance();
		for (Identifier id : MADE.values()) {
			minecraft.getTextureManager().release(id);
		}
		MADE.clear();
		UNREADABLE.clear();
		masksRead = false;
		armourDust = armourMud = itemDust = itemMud = null;
		excalibur = false;
		for (String id : minecraft.getResourcePackRepository().getSelectedIds()) {
			if (id.toLowerCase(Locale.ROOT).contains("excalibur")) {
				excalibur = true;
				break;
			}
		}
	}

	/** The worn texture {@code base} with {@code grime} painted in, or {@code base} when it's clean or can't be read. */
	public static Identifier armour(Identifier base, ArmourGrime.@Nullable Grime grime) {
		if (grime == null || grime.amount() <= 0) {
			return base;
		}
		readMasks();
		if (armourDust == null || armourMud == null) {
			return base;
		}
		Identifier painted = paint(base, grime, armourDust, armourMud, false);
		return painted == null ? base : painted;
	}

	/** Just the grime splotches to lay over {@code stack}'s inventory icon, or null for none. */
	public static @Nullable Identifier icon(ItemStack stack) {
		ArmourGrime.Grime grime = ArmourGrime.grime(stack);
		if (grime == null || grime.amount() <= 0) {
			return null;
		}
		Identifier model = stack.get(DataComponents.ITEM_MODEL);
		if (model == null) {
			return null;
		}
		readMasks();
		if (itemDust == null || itemMud == null) {
			return null;
		}
		return paint(model.withPath("textures/item/" + model.getPath() + ".png"), grime, itemDust, itemMud, true);
	}

	// ---- painting -------------------------------------------------------------------------------------------------------

	private static @Nullable Identifier paint(Identifier base, ArmourGrime.Grime grime, Mask dust, Mask mud, boolean overlay) {
		if (UNREADABLE.contains(base)) {
			return null;
		}
		int stage = Mth.clamp(Mth.ceil(grime.level() * STAGES), 1, STAGES);
		int mudStep = Math.round(grime.muddiness() * MUD_STEPS);
		String key = base + "|" + stage + "|" + mudStep + (overlay ? "|o" : "");
		Identifier done = MADE.get(key);
		if (done != null) {
			return done;
		}
		NativeImage image = read(base);
		if (image == null) {
			UNREADABLE.add(base);
			return null;
		}
		if (MADE.size() >= MOST_KEPT) {
			reset();
		}
		float level = stage / (float) STAGES;
		float muddy = mudStep / (float) MUD_STEPS;
		int colour = lerpRgb(muddy, excalibur ? EXCALIBUR_DUST : DUST, excalibur ? EXCALIBUR_MUD : MUD);
		float threshold = 1.0F - MOST_COVER * level;
		int w = image.getWidth(), h = image.getHeight();
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				int argb = image.getPixel(x, y);
				int a = argb >>> 24;
				int r = argb >> 16 & 0xFF, g = argb >> 8 & 0xFF, b = argb & 0xFF;
				float lum = r * 0.3F + g * 0.59F + b * 0.11F;
				int out = overlay ? 0 : argb;
				if (a != 0 && lum >= OUTLINE) {
					int dm = dust.at(x, y, w, h), mm = mud.at(x, y, w, h);
					float order = ((1.0F - muddy) * (dm >>> 24) + muddy * (mm >>> 24)) / 255.0F;
					if (order > 0.0F && order > threshold) {
						float shade = ((1.0F - muddy) * (dm & 0xFF) + muddy * (mm & 0xFF)) / 255.0F;
						float strength = EDGE_STRENGTH + (HEART_STRENGTH - EDGE_STRENGTH) * Math.min(1.0F, (order - threshold) * 4.0F);
						// the grime keeps a little of the armour's own light and shadow
						float k = shade * (0.7F + 0.3F * lum / 255.0F);
						int gr = Math.round((colour >> 16 & 0xFF) * k), gg = Math.round((colour >> 8 & 0xFF) * k), gb = Math.round((colour & 0xFF) * k);
						if (overlay) {
							out = Math.round(strength * a) << 24 | gr << 16 | gg << 8 | gb;
						} else {
							out = a << 24 | mix(r, gr, strength) << 16 | mix(g, gg, strength) << 8 | mix(b, gb, strength);
						}
					}
				}
				image.setPixel(x, y, out);
			}
		}
		Identifier id = JaymosPowers.id("grime/" + made++);
		Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(id::toString, image));
		MADE.put(key, id);
		return id;
	}

	private static int mix(int from, int to, float t) {
		return Mth.clamp(Math.round(from + (to - from) * t), 0, 255);
	}

	private static int lerpRgb(float t, int from, int to) {
		return mix(from >> 16 & 0xFF, to >> 16 & 0xFF, t) << 16 | mix(from >> 8 & 0xFF, to >> 8 & 0xFF, t) << 8 | mix(from & 0xFF, to & 0xFF, t);
	}

	private static void readMasks() {
		if (masksRead) {
			return;
		}
		masksRead = true;
		armourDust = Mask.read(JaymosPowers.id("textures/grime/armour_dust.png"));
		armourMud = Mask.read(JaymosPowers.id("textures/grime/armour_mud.png"));
		itemDust = Mask.read(JaymosPowers.id("textures/grime/item_dust.png"));
		itemMud = Mask.read(JaymosPowers.id("textures/grime/item_mud.png"));
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

	/** A grime mask's pixels (ARGB: alpha = order, blue = shade), sampled at the same spot of any size of texture. */
	record Mask(int width, int height, int[] pixels) {
		static @Nullable Mask read(Identifier id) {
			NativeImage image = ArmourGrimeTextures.read(id);
			if (image == null) {
				return null;
			}
			try (image) {
				int w = image.getWidth(), h = image.getHeight();
				int[] pixels = new int[w * h];
				for (int y = 0; y < h; y++) {
					for (int x = 0; x < w; x++) {
						pixels[y * w + x] = image.getPixel(x, y);
					}
				}
				return new Mask(w, h, pixels);
			}
		}

		int at(int x, int y, int w, int h) {
			return this.pixels[(y * this.height / h) * this.width + x * this.width / w];
		}
	}
}
