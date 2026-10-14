package com.jaymospowers.client.power.wind;

import java.util.UUID;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.Nullable;

/**
 * Better Combat's attack swings, read through Player Animation Library (MIT; compiled against, touched only from
 * {@link PalAccess} and only when it's loaded). Better Combat plays each attack on its own animation layer,
 * {@code bettercombat:attack}, for the local player and everyone else alike, mirrored for the left arm.
 */
public final class CombatAnimCompat {
	private static final boolean PAL = FabricLoader.getInstance().isModLoaded("player_animation_library");
	private static final boolean LOADED = PAL && FabricLoader.getInstance().isModLoaded("bettercombat");

	/** An attack animation playing: which one (its id and name), whether it's mirrored, and its own tick. */
	public record Attack(UUID id, String name, boolean mirrored, float ticks) {
	}

	private CombatAnimCompat() {
	}

	/** The Better Combat attack this player is swinging right now, or null. */
	public static @Nullable Attack attack(Entity entity) {
		return LOADED ? PalAccess.attack(entity) : null;
	}

	/**
	 * This render state is Player Animation Library's first-person pass: your own body drawn round the camera so the
	 * swing shows in first person. The flight's body poses stay out of it (they'd tip the arms out of view).
	 */
	public static boolean firstPersonPass(Object state) {
		return PAL && PalAccess.firstPersonPass(state);
	}
}
