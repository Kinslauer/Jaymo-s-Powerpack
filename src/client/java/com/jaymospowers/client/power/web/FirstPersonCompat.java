package com.jaymospowers.client.power.web;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;

/**
 * First-person Model (tr7zw), if it's installed: it draws your own body in first person, so Living Armor's tendrils
 * render from {@link WebTendrilLayer} with it (it hides no layer unless its player sets {@code hiddenLayers}). Its
 * API is reached by reflection, so it isn't a compile dependency: {@code FirstPersonAPI.isEnabled()} (it can be toggled
 * with a key) and {@code isRenderingPlayer()}.
 */
public final class FirstPersonCompat {
	private static final boolean LOADED = FabricLoader.getInstance().isModLoaded("firstperson");
	private static final String API = "dev.tr7zw.firstperson.api.FirstPersonAPI";
	private static final String STATE_ACCESS = "dev.tr7zw.firstperson.access.LivingEntityRenderStateAccess";
	private static MethodHandle enabled;
	private static boolean looked;
	private static Class<?> stateAccess;
	private static MethodHandle hideLeftArm;
	private static MethodHandle hideRightArm;
	private static boolean lookedArms;

	private FirstPersonCompat() {
	}

	public static boolean loaded() {
		return LOADED;
	}

	/** Your own body (and so its tendrils) is being drawn in first person right now, by First-person Model. */
	public static boolean bodyShown() {
		if (!LOADED) {
			return false;
		}
		Minecraft minecraft = Minecraft.getInstance();
		if (!minecraft.options.getCameraType().isFirstPerson()) {
			return false;
		}
		if (!looked) {
			looked = true;
			try {
				enabled = MethodHandles.publicLookup().findStatic(Class.forName(API), "isEnabled", MethodType.methodType(boolean.class));
			} catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
				enabled = null;
			}
		}
		if (enabled == null) {
			// it's there but its API moved: assume it's drawing the body (its default)
			return true;
		}
		try {
			return (boolean)enabled.invokeExact();
		} catch (Throwable e) {
			return true;
		}
	}

	/**
	 * Undoes First-person Model hiding your body's arms (it does while it shows the vanilla hands instead, holding an item
	 * or with its vanilla hands mode on), so a move that poses them shows on the body; called before the body is
	 * submitted, as its arm and held-item rendering read these flags then. Its {@code LivingEntityRenderStateAccess},
	 * by reflection.
	 */
	public static void showArms(Object state) {
		if (!LOADED) {
			return;
		}
		if (!lookedArms) {
			lookedArms = true;
			try {
				stateAccess = Class.forName(STATE_ACCESS);
				MethodType set = MethodType.methodType(void.class, boolean.class);
				hideLeftArm = MethodHandles.publicLookup().findVirtual(stateAccess, "setHideLeftArm", set);
				hideRightArm = MethodHandles.publicLookup().findVirtual(stateAccess, "setHideRightArm", set);
			} catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
				stateAccess = null;
			}
		}
		if (stateAccess == null || !stateAccess.isInstance(state)) {
			return;
		}
		try {
			hideLeftArm.invoke(state, false);
			hideRightArm.invoke(state, false);
		} catch (Throwable e) {
			stateAccess = null;
		}
	}
}
