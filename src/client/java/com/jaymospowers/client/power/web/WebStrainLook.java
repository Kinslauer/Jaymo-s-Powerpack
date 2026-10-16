package com.jaymospowers.client.power.web;

import com.jaymospowers.power.web.WebSymbioteStrain;
import com.jaymospowers.power.web.WebSymbioteStrains;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;

/**
 * How each symbiote strain looks (see {@link WebSymbioteStrains}): every symbiote texture has a white and a red twin
 * named {@code <name>_white.png} and {@code <name>_red.png} (tools/gen_web_strains.py), and the web lines and the
 * goo-coloured geometry have their own colours. The strain of whatever is drawn is put on its render state
 * ({@link #STRAIN}) as it's extracted, for every entity (players, behemoths, ridden mobs, roaming blobs).
 */
public final class WebStrainLook {
	/** The strain of what's being drawn (absent: black). */
	public static final RenderStateDataKey<WebSymbioteStrain> STRAIN = RenderStateDataKey.create(() -> "jaymospowers:web_symbiote_strain");

	/** The web lines (ARGB): the white strain's are white with a pale gold sheen, the red one's red with a black sheen. */
	public static final int WHITE_LINE = 0xFFE9E6DC, WHITE_LINE_SHEEN = 0xFFE6D49A;
	public static final int RED_LINE = 0xFF8E0F18, RED_LINE_SHEEN = 0xFF140608;
	/** The rope look (the lines' thicker swing ropes), as RGB. */
	public static final int WHITE_ROPE = 0xDCD8CC, WHITE_ROPE_SHEEN = 0xE6D49A, RED_ROPE = 0x6A0A12, RED_ROPE_SHEEN = 0x100406;

	private static final Map<Identifier, Identifier[]> TEXTURES = new HashMap<>();

	private WebStrainLook() {
	}

	/** From EntityRenderer.extractRenderState (WebBehemothClient.extract): the entity's strain, if it isn't black. */
	static void extract(Entity entity, EntityRenderState state) {
		WebSymbioteStrain strain = WebSymbioteStrains.of(entity);
		state.setData(STRAIN, strain == WebSymbioteStrain.BLACK ? null : strain);
	}

	/** The strain on a render state (black if none was put there). */
	public static WebSymbioteStrain of(EntityRenderState state) {
		WebSymbioteStrain strain = state.getData(STRAIN);
		return strain == null ? WebSymbioteStrain.BLACK : strain;
	}

	/** {@code black}'s twin for {@code strain} ({@code <path>_white.png} / {@code _red.png}); black is itself. */
	public static Identifier texture(Identifier black, WebSymbioteStrain strain) {
		if (strain == WebSymbioteStrain.BLACK) {
			return black;
		}
		Identifier[] twins = TEXTURES.computeIfAbsent(black, b -> new Identifier[WebSymbioteStrain.values().length]);
		Identifier twin = twins[strain.ordinal()];
		if (twin == null) {
			String path = black.getPath();
			int dot = path.lastIndexOf('.');
			String suffix = "_" + strain.name().toLowerCase(Locale.ROOT);
			twin = Identifier.fromNamespaceAndPath(black.getNamespace(), dot < 0 ? path + suffix : path.substring(0, dot) + suffix + path.substring(dot));
			twins[strain.ordinal()] = twin;
		}
		return twin;
	}

	/** {@code black}'s twin for whatever is on {@code state}. */
	public static Identifier texture(Identifier black, EntityRenderState state) {
		return texture(black, of(state));
	}

	/** A web line's colour (ARGB) for {@code strain}, or {@code black} (the line's own) for the black one. */
	public static int line(WebSymbioteStrain strain, int black) {
		return switch (strain) {
			case WHITE -> WHITE_LINE;
			case RED -> RED_LINE;
			default -> black;
		};
	}

	/** A web line's sheen (ARGB) for {@code strain}, or {@code black}. */
	public static int lineSheen(WebSymbioteStrain strain, int black) {
		return switch (strain) {
			case WHITE -> WHITE_LINE_SHEEN;
			case RED -> RED_LINE_SHEEN;
			default -> black;
		};
	}
}
