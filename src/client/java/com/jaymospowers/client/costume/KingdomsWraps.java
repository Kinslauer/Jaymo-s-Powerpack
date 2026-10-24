package com.jaymospowers.client.costume;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.Identifier;

/**
 * (0.15.147) An Earthling's training wraps on the gi (Andy: "Can you add the wraps to the gi?"). Jaymo's Kingdoms draws
 * no heritage marks under a costume (they followed the plain body's pose and slid out of the gi's sleeves), so the gi
 * wears them itself, in its own pose: Kingdoms' wraps texture over the gi's wristbands, and a Z Fighter's ki glow over
 * them while it flares. Asked of Kingdoms by reflection ({@code HeritageLooks.earthlingWraps}) when it's loaded;
 * without it, never.
 */
final class KingdomsWraps {
	static final Identifier SKIN = Identifier.fromNamespaceAndPath("jaymoskingdoms", "textures/entity/heritage/earthling_skin.png");
	static final Identifier GLOW = Identifier.fromNamespaceAndPath("jaymoskingdoms", "textures/entity/heritage/earthling_glow.png");
	/** What {@link #of} answers: none, the wraps, the wraps lit. */
	static final int NONE = 0, WRAPS = 1, LIT = 2;

	private static final MethodHandle WRAPS_OF;

	static {
		MethodHandle wraps = null;
		if (FabricLoader.getInstance().isModLoaded("jaymoskingdoms")) {
			try {
				wraps = MethodHandles.publicLookup().findStatic(Class.forName("com.jaymoskingdoms.client.HeritageLooks"), "earthlingWraps",
					MethodType.methodType(int.class, Object.class));
			} catch (ReflectiveOperationException | LinkageError e) {
				wraps = null;
			}
		}
		WRAPS_OF = wraps;
	}

	private KingdomsWraps() {
	}

	/** The wraps on the player whose render state this is ({@link #NONE}, {@link #WRAPS} or {@link #LIT}). */
	static int of(Object state) {
		if (WRAPS_OF == null) {
			return NONE;
		}
		try {
			return (int)WRAPS_OF.invokeExact(state);
		} catch (Throwable e) {
			return NONE;
		}
	}
}
