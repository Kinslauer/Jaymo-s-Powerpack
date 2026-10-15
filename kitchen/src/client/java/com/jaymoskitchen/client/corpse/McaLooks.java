package com.jaymoskitchen.client.corpse;

import java.util.Optional;
import java.util.UUID;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.Nullable;

/**
 * (0.4.44) A dead player's villager look. With Minecraft Comes Alive everyone is drawn as a villager (their genes,
 * skin, hair and clothes), so their corpse is that villager too: MCA's own stand-in villager for the player, drawn by
 * its own renderer. Only touches MCA when it's loaded.
 */
public final class McaLooks {
	private static final boolean MCA = FabricLoader.getInstance().isModLoaded("mca");

	private McaLooks() {
	}

	/**
	 * The villager MCA draws the player {@code owner} as, or null: no MCA, the player picked the plain player body, or
	 * their look hasn't arrived from the server yet (asking MCA for it sends for it, so it shows up a moment later).
	 */
	public static @Nullable Entity of(Optional<UUID> owner) {
		if (!MCA || owner.isEmpty()) {
			return null;
		}
		return Mca.villager(owner.get());
	}

	/** Kept apart so MCA's classes load only when it's there. */
	private static final class Mca {
		static @Nullable Entity villager(UUID owner) {
			return net.conczin.mca.MCAClient.getVillagerRendererData(owner)
				.map(look -> look instanceof Entity entity ? entity : null)
				.orElse(null);
		}
	}
}
