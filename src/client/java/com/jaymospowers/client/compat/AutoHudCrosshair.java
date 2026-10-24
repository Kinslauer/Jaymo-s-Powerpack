package com.jaymospowers.client.compat;

import com.jaymospowers.JaymosPowers;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import net.fabricmc.loader.api.FabricLoader;
import org.jspecify.annotations.Nullable;

/**
 * (0.15.148) Auto HUD (LGPL-3.0, in the pack) fades the crosshair out with the rest of the HUD. While you aim or attack
 * ({@code AimCrosshair}) its crosshair is revealed each tick, the way it reveals the hotbar when you scroll; once you
 * stop, Auto HUD fades it again as usual. Our aim crosshair hangs under its crosshair, so it fades along with it. Looked
 * up by name, so nothing breaks when Auto HUD is missing or renames it (then the crosshair just isn't revealed).
 */
public final class AutoHudCrosshair {
	private static @Nullable MethodHandle reveal;
	private static boolean looked;

	private AutoHudCrosshair() {
	}

	/** Shows Auto HUD's crosshair now (and so ours under it). */
	public static void reveal() {
		if (!looked) {
			looked = true;
			if (FabricLoader.getInstance().isModLoaded("autohud")) {
				try {
					Class<?> elements = Class.forName("mod.crend.autohud.hud.element.HudElements");
					Class<?> element = Class.forName("mod.crend.autohud.hud.element.HudElement");
					MethodHandles.Lookup lookup = MethodHandles.publicLookup();
					MethodHandle crosshair = lookup.findStaticGetter(elements, "CROSSHAIR",
						Class.forName("mod.crend.autohud.hud.elements.CrosshairElement"));
					reveal = MethodHandles.filterReturnValue(crosshair, lookup.findVirtual(element, "revealNow", MethodType.methodType(void.class))
						.asType(MethodType.methodType(void.class, crosshair.type().returnType())));
				} catch (ReflectiveOperationException | RuntimeException e) {
					JaymosPowers.LOGGER.warn("Auto HUD's crosshair wasn't found; it won't come back while aiming", e);
				}
			}
		}
		if (reveal != null) {
			try {
				reveal.invoke();
			} catch (Throwable e) {
				// its crosshair isn't set up yet (no world loaded): try again next tick
			}
		}
	}
}
