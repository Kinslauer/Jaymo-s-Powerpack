package com.jaymoskitchen.client.corpse;

import com.jaymoskitchen.JaymosKitchen;
import com.jaymoskitchen.corpse.CorpseEntity;
import com.jaymoskitchen.corpse.Wear;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;

/**
 * Andy: "Start work on 1 and 2" (show the work on the carcass). A carcass that's been skinned on the hook is drawn as
 * bare red flesh, and one that's been gutted with its ribs showing: its own texture is copied once (per model, texture
 * and state) and painted over, so it works for any animal whatever its model. Skinned: every solid pixel turns to
 * muscle, a little lighter or darker as the skin was, with short streaks of fat. Gutted: the faces of its body's main
 * box turn to a dark hollow crossed by ribs (the box's two smallest faces, its ends, stay plain). The body's renderer
 * draws with the copy (see {@code LivingEntityRendererMixin}), after {@link ClosedEyes} has had the texture.
 */
public final class Dressed {
	public static final RenderStateDataKey<Integer> KEY = RenderStateDataKey.create(() -> "jaymoskitchen:dressed");
	/** Muscle, darker muscle and fat; one pixel in this many is darker (in pairs down), one pair in this many is fat. */
	private static final int MUSCLE = 0x9E2C30, DARK_MUSCLE = 0x86222A, FAT = 0xDEBEAA;
	private static final int DARK_ONE_IN = 5, FAT_ONE_IN = 17;
	/** How much of the skin's light and dark shows through the flesh, around this much of full brightness. */
	private static final float SHADE_BASE = 0.84F, SHADE_SPAN = 0.28F;
	/** The hollow and its ribs; a rib every this many pixels along a face's long side. */
	private static final int HOLLOW = 0x54121A, RIB = 0xCEBCA2;
	private static final int RIB_EVERY = 3;
	/** The belly cut (its deep line and the raw lips either side) and the insides spilling out of an opened body (pink, darker pink). */
	private static final int CUT = 0x5E0F14, CUT_LIP = 0xB03038, GUT = 0xB86A72, GUT_DARK = 0x7E3F4A;
	/** Parts that are a body's trunk, in any case (Cube Animals' are Polish). */
	private static final String[] TRUNKS = {"body", "torso", "tulow", "chest"};

	/** Where a body's weathering ({@link Wear#PAINT_MASK}) sits in the dressed number. */
	public static final int WEAR_SHIFT = 12;
	/** Rot's darkest, bare bone, charcoal and ice, the colours a weathered body is painted toward. */
	private static final int ROT = 0x4A4228, BONE = 0xE6DFCB, CHARCOAL = 0x1A1512, ICE = 0xD2EEFF;
	/** How far toward charcoal each char level goes, and the greyest a body gets (share of its colour lost, at the last level). */
	private static final float[] CHAR_SHARE = {0.25F, 0.5F, 0.75F, 0.9F};
	private static final float GREY_MOST = 0.8F, ROT_SHARE = 0.6F, ICE_SHARE = 0.7F;
	/** Blotches are cells this many pixels across. */
	private static final int BLOTCH = 3;

	/** Painted copies: per model, original texture and state to copy. */
	private static final Map<Object, Map<String, Identifier>> DONE = new IdentityHashMap<>();
	private static int made;

	private Dressed() {
	}

	/**
	 * A whole beast roasting on the great spit: browning, cooked through, charred (bits beside the dressing's, client-only),
	 * from its doneness ({@code SpitBlockEntity}: 1 cooked, 2 burnt). The flesh is painted toward a roast's browns.
	 */
	public static final int ROAST_BROWNING = 1 << 8, ROAST_COOKED = 1 << 9, ROAST_CHARRED = 1 << 10;
	/** How far the flesh goes toward each roast's colour. */
	private static final int ROAST = 0x9A5A24, CHAR = 0x2E1A10;

	public static int roasted(float doneness) {
		return doneness >= 1.6F ? ROAST_CHARRED : doneness >= 1.0F ? ROAST_COOKED : doneness >= 0.4F ? ROAST_BROWNING : 0;
	}

	public static void mark(EntityRenderState state, int dressed) {
		state.setData(KEY, dressed == 0 ? null : dressed);
	}

	/** The texture to draw a body with: {@code texture} skinned or gutted if the state is such a carcass's. */
	public static Identifier texture(Model<?> model, Object state, Identifier texture) {
		Integer dressed = state instanceof EntityRenderState renderState ? renderState.getData(KEY) : null;
		if (dressed == null) {
			return texture;
		}
		return DONE.computeIfAbsent(model, m -> new HashMap<>()).computeIfAbsent(texture + "#" + dressed, k -> paint(model, texture, dressed));
	}

	private static Identifier paint(Model<?> model, Identifier texture, int dressed) {
		NativeImage painted = ClosedEyes.pixels(texture);
		if (painted == null) {
			return texture;
		}
		if ((dressed & CorpseEntity.DRESSED_SKINNED) != 0) {
			flesh(painted);
		}
		if ((dressed & CorpseEntity.DRESSED_GUTTED) != 0) {
			ribs(painted, trunkFaces(model.root()));
		} else if ((dressed >> CorpseEntity.DRESSED_CUT_SHIFT & CorpseEntity.DRESSED_CUT_MASK) != 0) {
			cut(painted, trunkFaces(model.root()), dressed >> CorpseEntity.DRESSED_CUT_SHIFT & CorpseEntity.DRESSED_CUT_MASK);
		}
		if ((dressed & ROAST_CHARRED) != 0) {
			roast(painted, CHAR, 0.8F);
		} else if ((dressed & ROAST_COOKED) != 0) {
			roast(painted, ROAST, 0.7F);
		} else if ((dressed & ROAST_BROWNING) != 0) {
			roast(painted, ROAST, 0.35F);
		}
		if ((dressed >> WEAR_SHIFT & Wear.PAINT_MASK) != 0) {
			weather(painted, dressed >> WEAR_SHIFT & Wear.PAINT_MASK);
		}
		Identifier id = JaymosKitchen.id("dressed/" + texture.getNamespace() + "/" + texture.getPath().replace(".png", "") + "_" + made++);
		Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(id::toString, painted));
		return id;
	}

	private static int hash(int x, int y, int salt) {
		int n = x * 374761393 + y * 668265263 + salt * 1274126177;
		n = (n ^ n >>> 13) * 1274126177;
		return (n ^ n >>> 16) & 0xFFFF;
	}

	private static void flesh(NativeImage image) {
		for (int y = 0; y < image.getHeight(); y++) {
			for (int x = 0; x < image.getWidth(); x++) {
				int argb = image.getPixel(x, y);
				if (ARGB.alpha(argb) == 0) {
					continue;
				}
				int lum = (ARGB.red(argb) * 299 + ARGB.green(argb) * 587 + ARGB.blue(argb) * 114) / 1000;
				float shade = SHADE_BASE + SHADE_SPAN * lum / 255.0F + (hash(x, y, 0) % 5 - 2) * 0.015F;
				int colour = hash(x / 2, y, 1) % FAT_ONE_IN == 0 ? FAT : hash(x, y / 2, 2) % DARK_ONE_IN == 0 ? DARK_MUSCLE : MUSCLE;
				image.setPixel(x, y, ARGB.color(ARGB.alpha(argb), Math.min(255, (int) ((colour >> 16 & 0xFF) * shade)),
					Math.min(255, (int) ((colour >> 8 & 0xFF) * shade)), Math.min(255, (int) ((colour & 0xFF) * shade))));
			}
		}
	}

	/**
	 * A body's weathering ({@link Wear}): bone where the rot has bared it (patches, the same cells that darkened first, so
	 * the flesh falls away from where it went dark), dark rot-brown in patches, grey with age, charred, or frozen pale blue.
	 */
	private static void weather(NativeImage image, int wear) {
		int charred = Wear.get(wear, Wear.CHAR), dark = Wear.get(wear, Wear.DARK), bare = Wear.get(wear, Wear.BARE), grey = Wear.get(wear, Wear.GREY);
		boolean frozen = Wear.frozen(wear);
		for (int y = 0; y < image.getHeight(); y++) {
			for (int x = 0; x < image.getWidth(); x++) {
				int argb = image.getPixel(x, y);
				if (ARGB.alpha(argb) == 0) {
					continue;
				}
				float r = ARGB.red(argb), g = ARGB.green(argb), b = ARGB.blue(argb);
				float lum = (r * 299 + g * 587 + b * 114) / 1000.0F;
				float shade = 0.75F + 0.25F * lum / 255.0F;
				float cell = hash(x / BLOTCH, y / BLOTCH, 7) % 1000 / 1000.0F;
				if (grey > 0) {
					float k = GREY_MOST * grey / Wear.LEVELS;
					r += (lum - r) * k;
					g += (lum - g) * k;
					b += (lum - b) * k;
				}
				if (dark > 0 && cell < dark / (float) Wear.LEVELS) {
					r += ((ROT >> 16 & 0xFF) * shade - r) * ROT_SHARE;
					g += ((ROT >> 8 & 0xFF) * shade - g) * ROT_SHARE;
					b += ((ROT & 0xFF) * shade - b) * ROT_SHARE;
				}
				if (bare > 0 && cell < bare / (float) Wear.LEVELS) {
					r = (BONE >> 16 & 0xFF) * shade;
					g = (BONE >> 8 & 0xFF) * shade;
					b = (BONE & 0xFF) * shade;
				}
				if (charred > 0) {
					float k = Math.min(1.0F, CHAR_SHARE[charred - 1] + (hash(x, y, 8) % 7 - 3) * 0.02F);
					r += ((CHARCOAL >> 16 & 0xFF) * shade - r) * k;
					g += ((CHARCOAL >> 8 & 0xFF) * shade - g) * k;
					b += ((CHARCOAL & 0xFF) * shade - b) * k;
				}
				if (frozen) {
					r += ((ICE >> 16 & 0xFF) * shade - r) * ICE_SHARE;
					g += ((ICE >> 8 & 0xFF) * shade - g) * ICE_SHARE;
					b += ((ICE & 0xFF) * shade - b) * ICE_SHARE;
				}
				image.setPixel(x, y, ARGB.color(ARGB.alpha(argb), Math.min(255, (int) r), Math.min(255, (int) g), Math.min(255, (int) b)));
			}
		}
	}

	private static void roast(NativeImage image, int colour, float amount) {
		for (int y = 0; y < image.getHeight(); y++) {
			for (int x = 0; x < image.getWidth(); x++) {
				int argb = image.getPixel(x, y);
				if (ARGB.alpha(argb) == 0) {
					continue;
				}
				float a = amount + (hash(x, y, 4) % 7 - 3) * 0.03F;
				image.setPixel(x, y, ARGB.color(ARGB.alpha(argb), (int) net.minecraft.util.Mth.lerp(a, ARGB.red(argb), colour >> 16 & 0xFF),
					(int) net.minecraft.util.Mth.lerp(a, ARGB.green(argb), colour >> 8 & 0xFF), (int) net.minecraft.util.Mth.lerp(a, ARGB.blue(argb), colour & 0xFF)));
			}
		}
	}

	private static void ribs(NativeImage image, List<float[]> faces) {
		List<int[]> rects = new ArrayList<>();
		int smallest = Integer.MAX_VALUE;
		for (float[] face : faces) {
			int[] rect = {Math.max(0, Math.round(face[0] * image.getWidth())), Math.max(0, Math.round(face[1] * image.getHeight())),
				Math.min(image.getWidth(), Math.round(face[2] * image.getWidth())), Math.min(image.getHeight(), Math.round(face[3] * image.getHeight()))};
			if (rect[2] > rect[0] && rect[3] > rect[1]) {
				rects.add(rect);
				smallest = Math.min(smallest, (rect[2] - rect[0]) * (rect[3] - rect[1]));
			}
		}
		for (int[] rect : rects) {
			boolean wide = rect[2] - rect[0] >= rect[3] - rect[1];
			boolean end = (rect[2] - rect[0]) * (rect[3] - rect[1]) <= smallest;
			for (int y = rect[1]; y < rect[3]; y++) {
				for (int x = rect[0]; x < rect[2]; x++) {
					int argb = image.getPixel(x, y);
					if (ARGB.alpha(argb) == 0) {
						continue;
					}
					boolean edge = x == rect[0] || x == rect[2] - 1 || y == rect[1] || y == rect[3] - 1;
					int along = wide ? x - rect[0] : y - rect[1];
					int colour = !end && !edge && along % RIB_EVERY == 1 ? RIB : HOLLOW + (hash(x, y, 3) % 5 * 4 << 16);
					// the insides slump out along the lower side of the opening
					boolean low = (wide ? y - rect[1] : x - rect[0]) * 5 >= (wide ? rect[3] - rect[1] : rect[2] - rect[0]) * 3;
					if (!end && !edge && low && colour != RIB && hash(x / 2, y, 9) % 3 != 0) {
						colour = (x + y) % 3 == 0 ? GUT_DARK : GUT;
					}
					image.setPixel(x, y, ARGB.color(ARGB.alpha(argb), colour >> 16 & 0xFF, colour >> 8 & 0xFF, colour & 0xFF));
				}
			}
		}
	}

	/**
	 * The cut along the belly after {@code strokes} strokes of {@link com.jaymoskitchen.corpse.Gutting#CUT_STROKES}: a dark line
	 * along the middle of the long side of each face of the trunk's box (but its two ends), with a raw lip each side, as long
	 * as the strokes made so far.
	 */
	private static void cut(NativeImage image, List<float[]> faces, int strokes) {
		float reach = Math.min(1.0F, strokes / (float) com.jaymoskitchen.corpse.Gutting.CUT_STROKES);
		int smallest = Integer.MAX_VALUE;
		List<int[]> rects = new ArrayList<>();
		for (float[] face : faces) {
			int[] rect = {Math.max(0, Math.round(face[0] * image.getWidth())), Math.max(0, Math.round(face[1] * image.getHeight())),
				Math.min(image.getWidth(), Math.round(face[2] * image.getWidth())), Math.min(image.getHeight(), Math.round(face[3] * image.getHeight()))};
			if (rect[2] > rect[0] && rect[3] > rect[1]) {
				rects.add(rect);
				smallest = Math.min(smallest, (rect[2] - rect[0]) * (rect[3] - rect[1]));
			}
		}
		for (int[] rect : rects) {
			if ((rect[2] - rect[0]) * (rect[3] - rect[1]) <= smallest) {
				continue;
			}
			boolean wide = rect[2] - rect[0] >= rect[3] - rect[1];
			int length = wide ? rect[2] - rect[0] : rect[3] - rect[1];
			int mid = wide ? (rect[1] + rect[3]) / 2 : (rect[0] + rect[2]) / 2;
			for (int along = 0; along < Math.round(length * reach); along++) {
				for (int across = -1; across <= 1; across++) {
					int x = wide ? rect[0] + along : mid + across, y = wide ? mid + across : rect[1] + along;
					if (x < rect[0] || x >= rect[2] || y < rect[1] || y >= rect[3]) {
						continue;
					}
					int argb = image.getPixel(x, y);
					if (ARGB.alpha(argb) == 0) {
						continue;
					}
					int colour = across == 0 ? CUT : CUT_LIP;
					image.setPixel(x, y, ARGB.color(ARGB.alpha(argb), colour >> 16 & 0xFF, colour >> 8 & 0xFF, colour & 0xFF));
				}
			}
		}
	}

	/** The texture rectangles (u0, v0, u1, v1, as shares of the texture) of every face of the trunk's biggest box. */
	private static List<float[]> trunkFaces(ModelPart root) {
		String[] trunk = {null};
		root.visit(new PoseStack(), (pose, path, index, cube) -> {
			String at = trunkPath(path);
			if (at != null && (trunk[0] == null || at.length() < trunk[0].length())) {
				trunk[0] = at;
			}
		});
		List<float[]> faces = new ArrayList<>();
		if (trunk[0] == null) {
			return faces;
		}
		ModelPart.Cube[] biggest = {null};
		root.visit(new PoseStack(), (pose, path, index, cube) -> {
			if (path.equals(trunk[0]) && (biggest[0] == null || volume(cube) > volume(biggest[0]))) {
				biggest[0] = cube;
			}
		});
		if (biggest[0] == null) {
			return faces;
		}
		for (ModelPart.Polygon polygon : biggest[0].polygons) {
			float u0 = Float.MAX_VALUE, v0 = Float.MAX_VALUE, u1 = -Float.MAX_VALUE, v1 = -Float.MAX_VALUE;
			for (ModelPart.Vertex vertex : polygon.vertices()) {
				u0 = Math.min(u0, vertex.u());
				v0 = Math.min(v0, vertex.v());
				u1 = Math.max(u1, vertex.u());
				v1 = Math.max(v1, vertex.v());
			}
			faces.add(new float[] {u0, v0, u1, v1});
		}
		return faces;
	}

	private static float volume(ModelPart.Cube cube) {
		return (cube.maxX - cube.minX) * (cube.maxY - cube.minY) * (cube.maxZ - cube.minZ);
	}

	/** The path up to the first part named as a trunk; null if there's none. */
	private static String trunkPath(String path) {
		int from = 0;
		while (from <= path.length()) {
			int slash = path.indexOf('/', from);
			int to = slash < 0 ? path.length() : slash;
			String name = path.substring(from, to);
			for (String trunk : TRUNKS) {
				if (name.equalsIgnoreCase(trunk)) {
					return path.substring(0, to);
				}
			}
			if (slash < 0) {
				return null;
			}
			from = slash + 1;
		}
		return null;
	}
}
