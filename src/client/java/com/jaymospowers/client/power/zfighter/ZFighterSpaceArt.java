package com.jaymospowers.client.power.zfighter;

import com.jaymospowers.power.zfighter.ZFighterSpace;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * (0.18.2) The pixel-art bodies of the star map and the flight screen: the sun, Home, the worlds the pod flies to and the Maw. A
 * world is drawn as a grid of square cells (bigger cells for a bigger world, so it keeps the blocky look), lit from the sun's side,
 * each with its own surface: Home's seas and land, the moon's craters, Rustfall's rust bands, Glacia's ice cracks, Cinder's lava,
 * the Verdant World's forests. The Maw is a black disc in a burning ring.
 */
final class ZFighterSpaceArt {
	/** How many cells across a world is drawn at most (above that the cells grow). */
	static final int CELLS = 28;

	private ZFighterSpaceArt() {
	}

	/** A world's look: its main colour, its second colour (land, craters, bands...), and how much of the second it shows. */
	record Look(int main, int second, int share, int shadow) {
	}

	static Look look(int index) {
		if (index == ZFighterSpace.HOME_INDEX) {
			return new Look(0xFF2F6FCF, 0xFF4E9E3E, 42, 0xFF0E1E3A);
		}
		return switch (index) {
			case ZFighterSpace.MOON -> new Look(0xFFB4B4B0, 0xFF85857F, 26, 0xFF2A2A2A);
			case ZFighterSpace.RUSTFALL -> new Look(0xFFB4492A, 0xFF7A2C18, 40, 0xFF2A0D06);
			case ZFighterSpace.GLACIA -> new Look(0xFFD8ECF6, 0xFF8FBFDC, 22, 0xFF26384A);
			case ZFighterSpace.CINDER -> new Look(0xFF3A2A26, 0xFFFF7A1E, 30, 0xFF120A08);
			case ZFighterSpace.VERDANT -> new Look(0xFF4FB84A, 0xFF2D7A2E, 45, 0xFF0E2A10);
			default -> new Look(0xFF606060, 0xFF404040, 20, 0xFF101010);
		};
	}

	/** A world: the disc at ({@code cx}, {@code cy}) of radius {@code r}, lit from ({@code lightX}, {@code lightY}). */
	static void world(GuiGraphicsExtractor graphics, int index, float cx, float cy, float r, float lightX, float lightY) {
		if (index == ZFighterSpace.MAW) {
			maw(graphics, cx, cy, r, System.currentTimeMillis() / 1000.0F);
			return;
		}
		Look look = look(index);
		int cells = Math.max(3, Math.min(CELLS, Math.round(r * 2.0F)));
		float cell = r * 2.0F / cells;
		float lx = lightX - cx, ly = lightY - cy;
		float ll = (float)Math.sqrt(lx * lx + ly * ly);
		if (ll > 0.001F) {
			lx /= ll;
			ly /= ll;
		}
		int seed = index * 7919 + 13;
		for (int i = 0; i < cells; i++) {
			for (int j = 0; j < cells; j++) {
				float px = (i + 0.5F) / cells * 2.0F - 1.0F;
				float py = (j + 0.5F) / cells * 2.0F - 1.0F;
				float d = px * px + py * py;
				if (d > 1.0F) {
					continue;
				}
				int colour = pattern(index, i * CELLS / cells, j * CELLS / cells, seed) ? look.second() : look.main();
				// lit side, a soft rim, and the night side
				float light = px * lx + py * ly;
				if (light < -0.25F) {
					colour = blend(colour, look.shadow(), 0.78F);
				} else if (light < 0.05F) {
					colour = blend(colour, look.shadow(), 0.45F);
				} else if (light > 0.6F && d < 0.7F) {
					colour = blend(colour, 0xFFFFFFFF, 0.12F);
				}
				int x0 = Math.round(cx - r + i * cell), y0 = Math.round(cy - r + j * cell);
				int x1 = Math.round(cx - r + (i + 1) * cell), y1 = Math.round(cy - r + (j + 1) * cell);
				graphics.fill(x0, y0, Math.max(x0 + 1, x1), Math.max(y0 + 1, y1), colour);
			}
		}
		if (index == ZFighterSpace.HOME_INDEX || index == ZFighterSpace.VERDANT) {
			// a thin air glow
			ring(graphics, cx, cy, r + Math.max(1.0F, cell * 0.6F), 0x553F9FFF, Math.max(1, Math.round(cell * 0.6F)));
		}
	}

	/** Whether cell (i, j) of a world shows its second colour. */
	private static boolean pattern(int index, int i, int j, int seed) {
		return switch (index) {
			// bands across the world, broken up
			case ZFighterSpace.RUSTFALL -> ((j / 3) % 2 == 0) != (hash(i / 4, j, seed) % 100 < 18);
			// cracks: thin crossing lines
			case ZFighterSpace.GLACIA -> (i + j * 2 + seed) % 9 == 0 || (i * 3 - j + seed) % 11 == 0;
			// craters: small round clusters
			case ZFighterSpace.MOON -> hash(i / 3, j / 3, seed) % 100 < 26 && hash(i, j, seed) % 3 != 0;
			// blobs of land, lava or forest at a coarse scale
			default -> smooth(i, j, seed) < look(index).share();
		};
	}

	/** A coarse blobby noise from 0 to 99. */
	private static int smooth(int i, int j, int seed) {
		int a = hash(i / 4, j / 4, seed) % 100, b = hash((i + 2) / 4, j / 4, seed) % 100, c = hash(i / 4, (j + 2) / 4, seed) % 100;
		return (a * 2 + b + c) / 4;
	}

	static int hash(int x, int y, int seed) {
		int h = x * 374761393 + y * 668265263 + seed * 982451653;
		h = (h ^ (h >>> 13)) * 1274126177;
		return (h ^ (h >>> 16)) & 0x7FFFFFFF;
	}

	/** The sun, with its glow. */
	static void sun(GuiGraphicsExtractor graphics, float cx, float cy, float r) {
		for (int k = 3; k >= 1; k--) {
			disc(graphics, cx, cy, r + k * 2.5F, ((0x22 * (4 - k)) << 24) | 0xFFC040);
		}
		disc(graphics, cx, cy, r, 0xFFFFD040);
		disc(graphics, cx - r * 0.2F, cy - r * 0.2F, r * 0.55F, 0xFFFFF0A0);
	}

	/** The Maw: a black disc in a turning ring of fire. */
	static void maw(GuiGraphicsExtractor graphics, float cx, float cy, float r, float time) {
		disc(graphics, cx, cy, r * 1.5F, 0x20FF7020);
		int dots = Math.max(24, Math.round(r * 6.0F));
		for (int k = 0; k < dots; k++) {
			double a = Math.PI * 2.0 * k / dots + time * 0.8;
			float rr = r * (1.25F + 0.25F * (float)Math.sin(k * 1.7 + time * 2.0));
			// a tilted ring: squashed top to bottom
			float x = cx + (float)Math.cos(a) * rr * 1.6F;
			float y = cy + (float)Math.sin(a) * rr * 0.45F;
			int size = Math.max(1, Math.round(r / 10.0F));
			int colour = k % 3 == 0 ? 0xFFFFE070 : k % 3 == 1 ? 0xFFFF9030 : 0xFFE04818;
			graphics.fill(Math.round(x), Math.round(y), Math.round(x) + size, Math.round(y) + size, colour);
		}
		disc(graphics, cx, cy, r, 0xFF000000);
		ring(graphics, cx, cy, r, 0xFFFFB040, Math.max(1, Math.round(r / 8.0F)));
	}

	/** A filled disc in one colour (rows of fills). */
	static void disc(GuiGraphicsExtractor graphics, float cx, float cy, float r, int colour) {
		int top = Math.round(cy - r), bottom = Math.round(cy + r);
		for (int y = top; y < bottom; y++) {
			float dy = y + 0.5F - cy;
			float half = (float)Math.sqrt(Math.max(0.0F, r * r - dy * dy));
			int x0 = Math.round(cx - half), x1 = Math.round(cx + half);
			if (x1 > x0) {
				graphics.fill(x0, y, x1, y + 1, colour);
			}
		}
	}

	/** A ring of {@code width} round a disc of radius {@code r}. */
	static void ring(GuiGraphicsExtractor graphics, float cx, float cy, float r, int colour, int width) {
		int steps = Math.max(16, Math.round(r * 5.0F));
		for (int k = 0; k < steps; k++) {
			double a = Math.PI * 2.0 * k / steps;
			int x = Math.round(cx + (float)Math.cos(a) * r), y = Math.round(cy + (float)Math.sin(a) * r);
			graphics.fill(x, y, x + width, y + width, colour);
		}
	}

	/** A dotted orbit. */
	static void orbit(GuiGraphicsExtractor graphics, float cx, float cy, float r, int colour) {
		int steps = Math.max(24, Math.round(r * 1.2F));
		for (int k = 0; k < steps; k++) {
			double a = Math.PI * 2.0 * k / steps;
			int x = Math.round(cx + (float)Math.cos(a) * r), y = Math.round(cy + (float)Math.sin(a) * r);
			graphics.fill(x, y, x + 1, y + 1, colour);
		}
	}

	/** A field of still stars over the box (seeded, so they don't jump about). */
	static void stars(GuiGraphicsExtractor graphics, int x, int y, int w, int h, int count, int seed) {
		for (int k = 0; k < count; k++) {
			int sx = x + hash(k, 1, seed) % Math.max(1, w), sy = y + hash(k, 2, seed) % Math.max(1, h);
			int b = hash(k, 3, seed) % 3;
			graphics.fill(sx, sy, sx + 1, sy + 1, b == 0 ? 0xFFFFFFFF : b == 1 ? 0xA0C8D8FF : 0x60FFFFFF);
		}
	}

	static int blend(int a, int b, float t) {
		int ar = (a >> 16) & 255, ag = (a >> 8) & 255, ab = a & 255;
		int br = (b >> 16) & 255, bg = (b >> 8) & 255, bb = b & 255;
		int r = Math.round(ar + (br - ar) * t), g = Math.round(ag + (bg - ag) * t), bl = Math.round(ab + (bb - ab) * t);
		return (a & 0xFF000000) | (r << 16) | (g << 8) | bl;
	}
}
