package com.jaymospowers.client.power.zfighter;

import java.util.EnumMap;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;

/**
 * (0.17.58, Andy's NOEA picks) Where each piece of the Z Fighter HUD is drawn: the ki bar, the form name, the combo
 * count, the technique chips, the Ki Wave's charge bar, the Spirit Bomb's bar, the Great Ape's rage bar and the scouter's
 * readout. Each piece is drawn where it always was, between {@link #begin} and {@link #end}, which move and scale it by
 * what the player set in the HUD editor ({@link ZFighterHudEditor}): an offset in pixels from its usual place and a scale
 * about its pivot (the top-left corner of the piece, or the middle of its top edge for those drawn centred). The layout is
 * kept in {@code config/jaymospowers-client.properties} ({@link ZFighterClientConfig}), one line a piece:
 * {@code zfighter_hud.ki_bar=dx,dy,scale}. A piece left alone has no line.
 */
public final class ZFighterHudLayout {
	/** The smallest and largest a piece can be scaled to, and the step of one notch of the wheel. */
	public static final float MIN_SCALE = 0.5F, MAX_SCALE = 2.0F, SCALE_STEP = 0.1F;
	/** The chips' size (mirrors {@code ZFighterPowerClient}) and the form name's width. */
	private static final int CHIP = 20, FORM_W = 72;
	private static final String PREFIX = "zfighter_hud.";

	/** The pieces of the HUD that can be moved. */
	public enum Piece {
		KI_BAR("ki_bar"), FORM_NAME("form_name"), COMBO("combo"), CHIPS("chips"), CHARGE("charge"), SPIRIT("spirit"), APE_RAGE("ape_rage"),
		SCOUTER("scouter");

		public final String key;

		Piece(String key) {
			this.key = key;
		}
	}

	/** How a piece is moved and scaled. */
	public record Place(int dx, int dy, float scale) {
		public static final Place DEFAULT = new Place(0, 0, 1.0F);

		public boolean isDefault() {
			return this.dx == 0 && this.dy == 0 && this.scale == 1.0F;
		}
	}

	/** A piece's usual place on screen: its box, and where along its top edge it pivots (0 left, 0.5 middle). */
	public record Rect(int x, int y, int w, int h, float pivot) {
		public float pivotX() {
			return this.x + this.pivot * this.w;
		}
	}

	private static final Map<Piece, Place> PLACES = new EnumMap<>(Piece.class);
	private static boolean loaded;

	private ZFighterHudLayout() {
	}

	private static void load() {
		if (loaded) {
			return;
		}
		loaded = true;
		for (Piece piece : Piece.values()) {
			String value = ZFighterClientConfig.get(PREFIX + piece.key, "");
			String[] parts = value.split(",");
			if (parts.length == 3) {
				try {
					Place place = new Place(Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim()), Float.parseFloat(parts[2].trim()));
					PLACES.put(piece, clean(place));
				} catch (NumberFormatException e) {
					// an unreadable line is the usual place
				}
			}
		}
	}

	private static Place clean(Place place) {
		return new Place(Mth.clamp(place.dx, -4000, 4000), Mth.clamp(place.dy, -4000, 4000), Mth.clamp(place.scale, MIN_SCALE, MAX_SCALE));
	}

	/** Where {@code piece} is set now. */
	public static Place place(Piece piece) {
		load();
		return PLACES.getOrDefault(piece, Place.DEFAULT);
	}

	/** Sets {@code piece} in memory only (a usual place is forgotten): for while it is being dragged. */
	public static void move(Piece piece, Place place) {
		load();
		Place clean = clean(place);
		if (clean.isDefault()) {
			PLACES.remove(piece);
		} else {
			PLACES.put(piece, clean);
		}
	}

	/** Writes {@code piece}'s place to the file. */
	public static void save(Piece piece) {
		Place place = place(piece);
		ZFighterClientConfig.set(PREFIX + piece.key, place.isDefault() ? null
			: place.dx + "," + place.dy + "," + String.format(java.util.Locale.ROOT, "%.2f", place.scale));
	}

	/** Sets {@code piece} and saves it. */
	public static void put(Piece piece, Place place) {
		move(piece, place);
		save(piece);
	}

	public static void resetAll() {
		for (Piece piece : Piece.values()) {
			put(piece, Place.DEFAULT);
		}
	}

	/** Where {@code piece} is drawn when left alone, on a screen {@code gw} by {@code gh}. */
	public static Rect rect(Piece piece, LocalPlayer player, int gw, int gh) {
		int cx = gw / 2, cy = gh / 2;
		int[] chips = ZFighterPowerClient.chipsRect(player, gw, gh);
		int barY = chips[1] - 6;
		return switch (piece) {
			case KI_BAR -> new Rect(chips[0], barY - 9, chips[2], 13, 0.0F);
			case FORM_NAME -> new Rect(chips[0], barY - 18, FORM_W, 18, 0.0F);
			case COMBO -> new Rect(chips[0] + chips[2] / 2 - 24, barY - 9, 48, 9, 0.5F);
			case CHIPS -> new Rect(chips[0], chips[1], chips[2], CHIP, 0.0F);
			case CHARGE -> new Rect(cx - 26, cy - 39, 52, 16, 0.5F);
			case SPIRIT -> new Rect(cx - 40, cy - 51, 80, 16, 0.5F);
			case APE_RAGE -> new Rect(cx - ApeRageClient.BAR_W / 2, gh - ApeRageClient.BAR_UP - 11, ApeRageClient.BAR_W, 17, 0.5F);
			case SCOUTER -> new Rect(cx + 14, cy - 12, 90, 22, 0.0F);
		};
	}

	/** {@code rect} where {@code place} puts it. */
	public static Rect placed(Rect rect, Place place) {
		float px = rect.pivotX();
		int x = Math.round(px + (rect.x - px) * place.scale + place.dx);
		int y = rect.y + place.dy;
		return new Rect(x, y, Math.round(rect.w * place.scale), Math.round(rect.h * place.scale), rect.pivot);
	}

	/** Before drawing {@code piece} where it always was: moves and scales what is drawn until {@link #end}. */
	public static void begin(GuiGraphicsExtractor graphics, Piece piece, LocalPlayer player) {
		Place place = place(piece);
		graphics.pose().pushMatrix();
		if (!place.isDefault()) {
			Rect rect = rect(piece, player, graphics.guiWidth(), graphics.guiHeight());
			float px = rect.pivotX(), py = rect.y;
			graphics.pose().translate(px + place.dx, py + place.dy);
			graphics.pose().scale(place.scale, place.scale);
			graphics.pose().translate(-px, -py);
		}
	}

	public static void end(GuiGraphicsExtractor graphics) {
		graphics.pose().popMatrix();
	}
}
