package com.jaymospowers.client.compat;

import com.jaymospowers.JaymosPowers;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Entity;

/**
 * Take a Seat's poses on our own sitting and kneeling. Andy (2026-11-17): "There seems to be new sit animations on x, move
 * those to our sit button, and change the kneel animation to theirs, it happens on the first x press"; then "Replace ours"
 * and "Every kneel".
 * <ul>
 * <li><b>Sitting</b> (the Sit key, a seat, meditating): the body plays Take a Seat's pose for where you are and what you
 * hold (a campfire or furnace you look at, a sword, axe, shovel or rod in hand, a seat, a fence, a bed, else the ground),
 * in place of our own ({@code MeditatePose}). Pressing the Sit key again goes round that pose's variants.</li>
 * <li><b>Kneeling</b> (the low crouch held still, and tending someone): the body plays Take a Seat's ground kneel
 * ({@code kneesitting}, its first press) in place of ours. Tending keeps its own working hands.</li>
 * </ul>
 * Nothing of Take a Seat's is copied: its animations are played from the mod as installed, through the Player Animation
 * Library ({@link TakeASeatPal}). Without either mod, or if an animation is missing, our own poses show as before. Its
 * own key (X) is taken off ({@code KeyConflicts}) and its right-click on stairs turned off, so only our Sit key sits.
 */
public final class TakeASeatPoses {
	static final boolean LOADED = FabricLoader.getInstance().isModLoaded("takeaseat")
		&& FabricLoader.getInstance().isModLoaded("player_animation_library");

	/** On a render state: Take a Seat's kneel holds this body's legs and hips, so our kneel leaves them alone. */
	public static final RenderStateDataKey<Boolean> KNEELING = RenderStateDataKey.create(() -> "jaymospowers:tas_kneeling");

	private TakeASeatPoses() {
	}

	public static void init() {
		if (!LOADED) {
			return;
		}
		try {
			TakeASeatPal.init();
		} catch (LinkageError | RuntimeException e) {
			JaymosPowers.LOGGER.warn("Take a Seat's poses couldn't be hooked up; our own sitting and kneeling poses stay", e);
		}
	}

	/** Whether Take a Seat's sitting pose is playing on {@code entity} (our own sitting pose stands down). */
	public static boolean sitting(Entity entity) {
		return LOADED && TakeASeatPal.sitting(entity.getId());
	}

	/** Whether Take a Seat's kneel is playing on {@code entity} (our own kneel leaves the legs and hips to it). */
	public static boolean kneeling(Entity entity) {
		return LOADED && TakeASeatPal.kneeling(entity.getId());
	}

	/** Whether {@code state}'s body has Take a Seat's kneel ({@link #KNEELING}). */
	public static boolean kneeling(AvatarRenderState state) {
		return Boolean.TRUE.equals(state.getData(KNEELING));
	}
}
