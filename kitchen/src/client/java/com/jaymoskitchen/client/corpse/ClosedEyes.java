package com.jaymoskitchen.client.corpse;

import com.jaymoskitchen.JaymosKitchen;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.util.ARGB;
import org.jspecify.annotations.Nullable;

/**
 * (0.5.16) A corpse's eyes are shut. Its texture is copied once (per texture and model) with the eyes painted over:
 * on the faces of the head's main box (the top half of its front and sides), a little white-and-black eye or a lone
 * dark speck is filled in with the face's own colour, its lowest row a shade darker as the closed lid. Big patches
 * (a cow's spots, a horse's blaze) are left alone. The body's renderer then draws with that copy (see
 * {@code LivingEntityRendererMixin}).
 */
public final class ClosedEyes {
	public static final RenderStateDataKey<Boolean> KEY = RenderStateDataKey.create(() -> "jaymoskitchen:closed_eyes");

	/** Eye pixels, against the face's middle colour: a dark pupil (luminance) and a white (luminance, greyness). */
	private static final float DARK_SHARE = 0.55F;
	private static final int DARK_MAX = 90, WHITE_MIN = 190, WHITE_ABOVE = 40, WHITE_GREY = 45;
	/** Bigger runs than this (pixels) are markings, not eyes. */
	private static final int EYE_MAX = 4, SPECK_MAX = 2;
	/**
	 * (0.5.19) A brightly coloured eye (a spider's red ones) on a dull face: colour spread of the eye's pixels and the
	 * face's most, and the largest such run (with the dark pixels touching it) that's still eyes.
	 */
	private static final int GLOW_SPREAD = 90, DULL_FACE = 50, GLOW_MAX = 12;
	/** How much darker the closed lid's line is than the face. */
	private static final float LID_SHADE = 0.68F;

	/** Painted copies: per model, original texture to copy (the original again when nothing could be painted). */
	private static final Map<Object, Map<Identifier, Identifier>> DONE = new IdentityHashMap<>();
	private static int made;

	private ClosedEyes() {
	}

	public static void mark(EntityRenderState state) {
		state.setData(KEY, Boolean.TRUE);
	}

	/** Whether {@code state} is a corpse's, drawn with its eyes shut. */
	public static boolean closed(Object state) {
		return state instanceof EntityRenderState renderState && Boolean.TRUE.equals(renderState.getData(KEY));
	}

	/** The texture to draw a body with: {@code texture} with its eyes shut if the state is a corpse's. */
	public static Identifier texture(Model<?> model, Object state, Identifier texture) {
		if (!closed(state)) {
			return texture;
		}
		Map<Identifier, Identifier> byTexture = DONE.computeIfAbsent(model, m -> new HashMap<>());
		Identifier done = byTexture.get(texture);
		if (done == null) {
			done = paint(model, texture);
			byTexture.put(texture, done);
		}
		return done;
	}

	private static Identifier paint(Model<?> model, Identifier texture) {
		List<float[]> faces = headFaces(model.root());
		if (faces.isEmpty()) {
			return texture;
		}
		NativeImage painted = pixels(texture);
		if (painted == null) {
			return texture;
		}
		boolean changed = false;
		for (float[] face : faces) {
			int x0 = Math.round(face[0] * painted.getWidth()), y0 = Math.round(face[1] * painted.getHeight());
			int x1 = Math.round(face[2] * painted.getWidth()), y1 = Math.round(face[3] * painted.getHeight());
			changed |= shut(painted, Math.max(0, x0), Math.max(0, y0), Math.min(painted.getWidth(), x1),
				Math.min(painted.getHeight(), y1));
		}
		if (!changed) {
			painted.close();
			return texture;
		}
		Identifier id = JaymosKitchen.id("closed_eyes/" + texture.getNamespace() + "/" + texture.getPath().replace(".png", "")
			+ "_" + made++);
		Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(id::toString, painted));
		return id;
	}

	/**
	 * The texture rectangles (u0, v0, u1, v1, as shares of the texture) of the upper halves of the front and side
	 * faces of the head's main box: the first box of the part named "head" (a horse's sits in "head_parts", a wolf's is
	 * its "real_head").
	 */
	private static List<float[]> headFaces(ModelPart root) {
		List<float[]> faces = new ArrayList<>();
		root.visit(new PoseStack(), (pose, path, index, cube) -> {
			if (index != 0 || !(path.endsWith("/head") || path.endsWith("/real_head"))) {
				return;
			}
			for (ModelPart.Polygon polygon : cube.polygons) {
				if (Math.abs(polygon.normal().y()) > 0.5F || polygon.normal().z() > 0.5F) {
					// not the top or bottom, nor the back of the head
					continue;
				}
				float u0 = Float.MAX_VALUE, v0 = Float.MAX_VALUE, u1 = -Float.MAX_VALUE, v1 = -Float.MAX_VALUE;
				for (ModelPart.Vertex vertex : polygon.vertices()) {
					u0 = Math.min(u0, vertex.u());
					v0 = Math.min(v0, vertex.v());
					u1 = Math.max(u1, vertex.u());
					v1 = Math.max(v1, vertex.v());
				}
				faces.add(new float[] {u0, v0, u1, (v0 + v1) * 0.5F});
			}
		});
		return faces;
	}

	/** Paints over the eyes in one face's rectangle; true if any were found. */
	private static boolean shut(NativeImage image, int x0, int y0, int x1, int y1) {
		int w = x1 - x0, h = y1 - y0;
		if (w <= 0 || h <= 0) {
			return false;
		}
		int[] colours = new int[w * h];
		int[] lums = new int[w * h];
		int solid = 0;
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				int argb = image.getPixel(x0 + x, y0 + y);
				colours[y * w + x] = argb;
				lums[y * w + x] = ARGB.alpha(argb) < 128 ? -1 : luminance(argb);
				if (lums[y * w + x] >= 0) {
					solid++;
				}
			}
		}
		if (solid < 4) {
			return false;
		}
		int middle = middleColour(colours, lums);
		int middleLum = luminance(middle);
		int middleSpread = spread(middle);
		// 1 dark, 2 white, 3 brightly coloured on a dull face, 0 anything else
		int[] kind = new int[w * h];
		for (int i = 0; i < kind.length; i++) {
			int lum = lums[i];
			if (lum < 0) {
				continue;
			}
			int spread = spread(colours[i]);
			if (spread > GLOW_SPREAD && middleSpread < DULL_FACE) {
				kind[i] = 3;
			} else if (lum < DARK_MAX && lum < middleLum * DARK_SHARE) {
				kind[i] = 1;
			} else if (lum > WHITE_MIN && lum > middleLum + WHITE_ABOVE && spread < WHITE_GREY) {
				kind[i] = 2;
			}
		}
		int[] group = new int[w * h];
		Arrays.fill(group, -1);
		List<int[]> groups = new ArrayList<>();
		for (int i = 0; i < kind.length; i++) {
			if (kind[i] != 0 && group[i] < 0) {
				groups.add(flood(kind, group, w, h, i, groups.size()));
			}
		}
		boolean[] eye = new boolean[w * h];
		for (int g = 0; g < groups.size(); g++) {
			int[] members = groups.get(g);
			int k = kind[members[0]];
			if (members.length > EYE_MAX) {
				continue;
			}
			// a white beside a small dark (or a dark beside a small white) is an eye; a lone dark speck is one too
			boolean paired = false;
			for (int i : members) {
				for (int n : neighbours(i, w, h)) {
					if (n >= 0 && kind[n] != 0 && kind[n] != k && groups.get(group[n]).length <= EYE_MAX) {
						paired = true;
					}
				}
			}
			boolean speck = k == 1 && members.length <= SPECK_MAX;
			if (paired || speck) {
				for (int i : members) {
					eye[i] = true;
				}
			}
		}
		// coloured eyes, with the dark pixels round them, however they're clustered (a spider has eight)
		int[] glowing = new int[w * h];
		for (int i = 0; i < kind.length; i++) {
			glowing[i] = kind[i] == 1 || kind[i] == 3 ? 1 : 0;
		}
		int[] glowGroup = new int[w * h];
		Arrays.fill(glowGroup, -1);
		for (int i = 0; i < kind.length; i++) {
			if (kind[i] == 3 && glowGroup[i] < 0) {
				int[] members = flood(glowing, glowGroup, w, h, i, i);
				if (members.length <= GLOW_MAX) {
					for (int m : members) {
						eye[m] = true;
					}
				}
			}
		}
		boolean any = false;
		int lid = ARGB.color(ARGB.alpha(middle), Math.round(ARGB.red(middle) * LID_SHADE),
			Math.round(ARGB.green(middle) * LID_SHADE), Math.round(ARGB.blue(middle) * LID_SHADE));
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				int i = y * w + x;
				if (!eye[i]) {
					continue;
				}
				any = true;
				// the eye's lowest row is the lid's line, the rest skin
				boolean lowest = y + 1 >= h || !eye[i + w];
				image.setPixel(x0 + x, y0 + y, lowest ? lid : middle);
			}
		}
		return any;
	}

	/** The pixels joined (side by side) to {@code start} of its kind, marked as group {@code id}. */
	private static int[] flood(int[] kind, int[] group, int w, int h, int start, int id) {
		int[] stack = new int[kind.length];
		int top = 0, count = 0;
		int[] members = new int[kind.length];
		stack[top++] = start;
		group[start] = id;
		while (top > 0) {
			int i = stack[--top];
			members[count++] = i;
			for (int n : neighbours(i, w, h)) {
				if (n >= 0 && group[n] < 0 && kind[n] == kind[start]) {
					group[n] = id;
					stack[top++] = n;
				}
			}
		}
		return Arrays.copyOf(members, count);
	}

	private static int[] neighbours(int i, int w, int h) {
		int x = i % w, y = i / w;
		return new int[] {x > 0 ? i - 1 : -1, x + 1 < w ? i + 1 : -1, y > 0 ? i - w : -1, y + 1 < h ? i + w : -1};
	}

	/** The face's middle colour: the solid pixel of middle brightness. */
	private static int middleColour(int[] colours, int[] lums) {
		List<Integer> solid = new ArrayList<>();
		for (int i = 0; i < colours.length; i++) {
			if (lums[i] >= 0) {
				solid.add(i);
			}
		}
		solid.sort((a, b) -> Integer.compare(lums[a], lums[b]));
		return colours[solid.get(solid.size() / 2)];
	}

	private static int spread(int argb) {
		return Math.max(ARGB.red(argb), Math.max(ARGB.green(argb), ARGB.blue(argb)))
			- Math.min(ARGB.red(argb), Math.min(ARGB.green(argb), ARGB.blue(argb)));
	}

	private static int luminance(int argb) {
		return (ARGB.red(argb) * 299 + ARGB.green(argb) * 587 + ARGB.blue(argb) * 114) / 1000;
	}

	/** A copy of the texture's pixels, read from the resources or from a texture made in game. */
	private static @Nullable NativeImage pixels(Identifier texture) {
		Minecraft minecraft = Minecraft.getInstance();
		Optional<Resource> resource = minecraft.getResourceManager().getResource(texture);
		if (resource.isPresent()) {
			try (InputStream in = resource.get().open()) {
				return NativeImage.read(in);
			} catch (Exception e) {
				JaymosKitchen.LOGGER.warn("Closed eyes: couldn't read {}", texture, e);
				return null;
			}
		}
		AbstractTexture made = minecraft.getTextureManager().getTexture(texture);
		if (!(made instanceof DynamicTexture dynamic) || dynamic.getPixels() == null) {
			return null;
		}
		NativeImage copy = new NativeImage(dynamic.getPixels().getWidth(), dynamic.getPixels().getHeight(), true);
		copy.copyFrom(dynamic.getPixels());
		return copy;
	}
}
