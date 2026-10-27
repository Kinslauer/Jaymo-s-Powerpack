package com.jaymospowers.client.costume;

import com.jaymospowers.costume.CostumeDesign;
import java.util.function.IntBinaryOperator;

/**
 * (0.17.58, Andy's NOEA picks) The suit editor's chest marks: ready-made 16x16 emblems to pick from (the painter in the
 * costume table still lets you draw your own). Each is drawn by a small function over the pixel grid, in the emblem's own
 * palette ({@link CostumeDesign#PRIMARY} and friends), so a mark takes the suit's colours. Our own shapes.
 */
final class SuitMarks {
	/** The marks' lang keys, in the order the editor lists them (after the blank one). */
	static final String[] KEYS = {"ring", "star", "diamond", "cross", "crescent", "triangle", "shield", "drop", "bolt", "orb"};
	private static final int S = CostumeDesign.EMBLEM_SIZE;
	private static final float C = (S - 1) / 2.0F;
	/** The bolt's rows: where it is drawn on each row (from, to inclusive), from the top. */
	private static final int[][] BOLT = {
		{8, 12}, {7, 11}, {6, 10}, {5, 9}, {4, 13}, {5, 12}, {7, 10}, {6, 9}, {5, 8}, {4, 7}, {4, 6}, {3, 5}};

	private SuitMarks() {
	}

	/** How many marks there are, the blank one included (index 0). */
	static int count() {
		return KEYS.length + 1;
	}

	/** Mark {@code index}'s pixels (palette entries, row by row); index 0 is blank. */
	static byte[] pixels(int index) {
		byte[] out = new byte[CostumeDesign.EMBLEM_PIXELS];
		if (index <= 0 || index > KEYS.length) {
			return out;
		}
		IntBinaryOperator shape = switch (KEYS[index - 1]) {
			case "ring" -> SuitMarks::ring;
			case "star" -> SuitMarks::star;
			case "diamond" -> SuitMarks::diamond;
			case "cross" -> SuitMarks::cross;
			case "crescent" -> SuitMarks::crescent;
			case "triangle" -> SuitMarks::triangle;
			case "shield" -> SuitMarks::shield;
			case "drop" -> SuitMarks::drop;
			case "bolt" -> SuitMarks::bolt;
			default -> SuitMarks::orb;
		};
		for (int y = 0; y < S; y++) {
			for (int x = 0; x < S; x++) {
				out[y * S + x] = (byte)shape.applyAsInt(x, y);
			}
		}
		return out;
	}

	private static float dist(int x, int y) {
		return (float)Math.hypot(x - C, y - C);
	}

	/** A gold ring round a secondary disc with a white heart. */
	private static int ring(int x, int y) {
		float r = dist(x, y);
		return r <= 7.3F && r > 5.4F ? CostumeDesign.GOLD : r <= 5.4F && r > 2.0F ? CostumeDesign.SECONDARY : r <= 2.0F ? CostumeDesign.WHITE : CostumeDesign.EMPTY;
	}

	/** A five-pointed star in gold with a primary heart. */
	private static int star(int x, int y) {
		float dx = x - C, dy = y - C - 0.5F;
		float r = (float)Math.hypot(dx, dy);
		float angle = (float)Math.atan2(dx, -dy);
		float lobe = Math.abs(((angle / (2.0F * (float)Math.PI) * 5.0F) % 1.0F + 1.0F) % 1.0F - 0.5F) * 2.0F;
		float edge = 3.2F + (7.6F - 3.2F) * lobe;
		return r <= edge ? r <= 2.0F ? CostumeDesign.PRIMARY : CostumeDesign.GOLD : CostumeDesign.EMPTY;
	}

	private static int diamond(int x, int y) {
		float d = Math.abs(x - C) + Math.abs(y - C);
		return d <= 7.2F ? d > 5.0F ? CostumeDesign.GOLD : d > 2.4F ? CostumeDesign.PRIMARY : CostumeDesign.WHITE : CostumeDesign.EMPTY;
	}

	private static int cross(int x, int y) {
		float dx = Math.abs(x - C), dy = Math.abs(y - C);
		boolean arm = dx <= 1.6F && dy <= 7.2F || dy <= 1.6F && dx <= 7.2F;
		return arm ? dx <= 0.6F && dy <= 0.6F ? CostumeDesign.WHITE : CostumeDesign.SECONDARY : CostumeDesign.EMPTY;
	}

	/** A gold crescent opening to the right. */
	private static int crescent(int x, int y) {
		return dist(x, y) <= 7.2F && Math.hypot(x - C - 3.2F, y - C) > 6.0F ? CostumeDesign.GOLD : CostumeDesign.EMPTY;
	}

	private static int triangle(int x, int y) {
		float half = (y - 1) * 0.55F;
		boolean in = y >= 1 && y <= 14 && Math.abs(x - C) <= half;
		return in ? y >= 12 || Math.abs(x - C) >= half - 1.0F ? CostumeDesign.GOLD : CostumeDesign.PRIMARY : CostumeDesign.EMPTY;
	}

	private static int shield(int x, int y) {
		float half = y <= 8 ? 6.4F : Math.max(0.0F, 6.4F - (y - 8) * 0.95F);
		boolean in = y >= 1 && y <= 14 && Math.abs(x - C) <= half;
		if (!in) {
			return CostumeDesign.EMPTY;
		}
		boolean rim = y == 1 || Math.abs(x - C) >= half - 1.0F;
		return rim ? CostumeDesign.GOLD : x < C ? CostumeDesign.PRIMARY : CostumeDesign.SECONDARY;
	}

	/** A drop (a flame) in gold with a primary core. */
	private static int drop(int x, int y) {
		float t = (y - 1) / 13.0F;
		float half = y < 1 || y > 14 ? -1.0F : (float)Math.pow(Math.sin(Math.PI * Math.pow(t, 0.7F)), 0.9F) * 5.6F;
		float d = Math.abs(x - C);
		return d <= half ? d <= half - 2.0F && t > 0.35F ? CostumeDesign.PRIMARY : CostumeDesign.GOLD : CostumeDesign.EMPTY;
	}

	private static int bolt(int x, int y) {
		int row = y - 2;
		if (row < 0 || row >= BOLT.length) {
			return CostumeDesign.EMPTY;
		}
		return x >= BOLT[row][0] && x <= BOLT[row][1] ? row == 0 || row == BOLT.length - 1 ? CostumeDesign.WHITE : CostumeDesign.GOLD : CostumeDesign.EMPTY;
	}

	/** A primary orb with a ring of white and a star-like glint. */
	private static int orb(int x, int y) {
		float r = dist(x, y);
		if (r > 7.2F) {
			return CostumeDesign.EMPTY;
		}
		if (r > 6.0F) {
			return CostumeDesign.WHITE;
		}
		boolean glint = x >= 4 && x <= 6 && y >= 4 && y <= 5 || x == 5 && y == 3 || x == 5 && y == 6;
		return glint ? CostumeDesign.WHITE : r > 3.2F ? CostumeDesign.PRIMARY : CostumeDesign.SECONDARY;
	}
}
