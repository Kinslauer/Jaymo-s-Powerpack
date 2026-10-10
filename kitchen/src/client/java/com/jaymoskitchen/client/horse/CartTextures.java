package com.jaymoskitchen.client.horse;

import com.jaymoskitchen.JaymosKitchen;
import com.jaymoskitchen.horse.CartKind;
import com.jaymoskitchen.horse.NiftyCarts;
import com.mojang.blaze3d.platform.NativeImage;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;

/**
 * The NiftyCarts carts' textures, put together at runtime from the game's own block textures for the cart's wood
 * (planks, its log and stripped log, stone, and for the supply cart composter and dirt), so they match the wood and
 * follow resource packs. The layouts are NiftyCarts' (Copyright (c) 2019 MennoMax, (c) 2023 jmb19905; MIT licensed,
 * see LICENSE_niftycarts). Each is made the first time it's needed.
 */
final class CartTextures {
	private static final int[][] R0 = {{1, 0}, {0, 1}};
	private static final int[][] R90 = {{0, -1}, {1, 0}};
	private static final Map<Identifier, Identifier> MADE = new HashMap<>();

	private CartTextures() {
	}

	/** The texture for a cart of this kind and wood, making it if it hasn't been yet. */
	static Identifier get(CartKind kind, String wood) {
		Identifier id = JaymosKitchen.id("textures/entity/cart/" + wood + "_" + kind.id + ".png");
		if (!MADE.containsKey(id)) {
			MADE.put(id, id);
			try {
				NativeImage image = assemble(layout(kind, wood));
				Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(id::toString, image));
			} catch (Exception e) {
				JaymosKitchen.LOGGER.warn("Couldn't put together the {} texture", id, e);
			}
		}
		return id;
	}

	/** Where a block texture's pixels go in the cart texture. */
	private record Fill(int x, int y, int width, int height, int[][] rot, int u, int v) {
	}

	private record Layer(String block, List<Fill> fills) {
	}

	private static final class Layout {
		final List<Layer> layers = new ArrayList<>();

		Layout add(String block, Fill... fills) {
			layers.add(new Layer(block, List.of(fills)));
			return this;
		}
	}

	private static Fill fill(int x, int y, int width, int height) {
		return new Fill(x, y, width, height, R0, 0, 0);
	}

	private static Fill fill(int x, int y, int width, int height, int[][] rot) {
		return new Fill(x, y, width, height, rot, 0, 0);
	}

	private static Fill fill(int x, int y, int width, int height, int[][] rot, int u, int v) {
		return new Fill(x, y, width, height, rot, u, v);
	}

	/** NiftyCarts' recipe for each cart's 64x64 texture. */
	private static Layout layout(CartKind kind, String wood) {
		String planks = wood + "_planks";
		String log = NiftyCarts.log(wood);
		String stripped = "stripped_" + log;
		return switch (kind) {
			case ANIMAL_CART -> new Layout()
				.add(planks, fill(0, 0, 60, 38, R0, 0, 2), fill(0, 28, 20, 33, R90, 4, -2), fill(12, 30, 8, 31, new int[][] {{0, 1}, {-1, 0}}, 0, 4))
				.add(stripped, fill(54, 54, 10, 10, R0, 0, 2))
				.add(log, fill(0, 21, 60, 4, R90), fill(46, 60, 8, 4, R90))
				.add("stone", fill(62, 55, 2, 9));
			case PLOW -> new Layout()
				.add(planks, fill(0, 0, 64, 32, R90), fill(0, 8, 42, 3, R0, 0, 1), fill(0, 27, 34, 3, R0, 0, 2))
				.add(stripped, fill(54, 54, 10, 10, R0, 2, 0))
				.add(log, fill(0, 0, 54, 4, R90), fill(46, 60, 8, 4, R90))
				.add("stone", fill(62, 55, 2, 9));
			default -> new Layout()
				.add(planks, fill(0, 0, 60, 45, R0, 0, 2), fill(0, 27, 60, 8, R0, 0, 1))
				.add(stripped, fill(54, 54, 10, 10, R0, 0, 2))
				.add(log, fill(0, 23, 54, 4, R90), fill(46, 60, 8, 4, R90))
				.add("stone", fill(62, 55, 2, 9))
				.add("composter_side", fill(16, 47, 44, 5, R0, -2, 1), fill(16, 54, 38, 5, R0, -2, -6))
				.add("composter_top", fill(18, 45, 10, 2, R0, -2, 3), fill(28, 45, 10, 2, R0, 10, 3), fill(18, 52, 8, 2, R0, 0, -4),
					fill(26, 52, 9, 2, R0, 11, -4))
				.add("dirt", fill(0, 45, 16, 17));
		};
	}

	private static NativeImage assemble(Layout layout) throws Exception {
		List<NativeImage> sources = new ArrayList<>();
		int resolution = 1;
		for (Layer layer : layout.layers) {
			NativeImage source = read(layer.block());
			sources.add(source);
			resolution = Math.max(resolution, Math.max(1, source.getWidth() / 16));
		}
		NativeImage image = new NativeImage(64 * resolution, 64 * resolution, true);
		for (int i = 0; i < layout.layers.size(); i++) {
			NativeImage source = sources.get(i);
			int sourceResolution = Math.max(1, source.getWidth() / 16);
			for (Fill fill : layout.layers.get(i).fills()) {
				draw(image, source, fill, sourceResolution, resolution);
			}
			source.close();
		}
		return image;
	}

	/** NiftyCarts' Fill: copies (and turns) the block texture's pixels into a rectangle of the cart texture. */
	private static void draw(NativeImage image, NativeImage source, Fill f, int resolution, int outResolution) {
		int r = outResolution / resolution;
		int x1 = (f.x() + f.width()) * resolution;
		int y1 = (f.y() + f.height()) * resolution;
		int u0 = f.u() * resolution;
		int v0 = f.v() * resolution;
		int[][] rot = f.rot();
		int width = source.getWidth(), height = Math.min(source.getHeight(), source.getWidth());
		for (int y = f.y() * resolution; y < y1; y++) {
			for (int dy = 0; dy < r; dy++) {
				for (int x = f.x() * resolution; x < x1; x++) {
					for (int dx = 0; dx < r; dx++) {
						int u = x * rot[0][0] + y * rot[0][1] + Math.min(rot[0][0], rot[0][1]) + u0;
						int v = x * rot[1][0] + y * rot[1][1] + Math.min(rot[1][0], rot[1][1]) + v0;
						int pixel = source.getPixel(Math.floorMod(u, width), Math.floorMod(v, height));
						image.setPixel(r * x + dx, r * y + dy, pixel);
					}
				}
			}
		}
	}

	private static NativeImage read(String block) throws Exception {
		Identifier location = Identifier.withDefaultNamespace("textures/block/" + block + ".png");
		Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(location);
		if (resource.isEmpty()) {
			throw new IllegalStateException("missing " + location);
		}
		try (InputStream in = resource.get().open()) {
			return NativeImage.read(in);
		}
	}
}
