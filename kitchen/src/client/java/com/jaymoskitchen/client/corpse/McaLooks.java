package com.jaymoskitchen.client.corpse;

import java.util.Optional;
import java.util.UUID;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
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

	/**
	 * Draws {@code villager} (from {@link #of}) as far gone in the zombie infection as MCA draws anyone (green-grey skin)
	 * while {@code draw} runs, then puts it back: it's the same villager MCA draws the living player as.
	 */
	public static <T> T infected(Entity villager, java.util.function.Supplier<T> draw) {
		return MCA ? Mca.infected(villager, draw) : draw.get();
	}

	/** Kept apart so MCA's classes load only when it's there. */
	private static final class Mca {
		static @Nullable Entity villager(UUID owner) {
			// only for someone the game knows (on the server, or already sent): asking MCA for an id makes the server look
			// that player up, and the future's zombie players carry made-up ids
			ClientPacketListener connection = Minecraft.getInstance().getConnection();
			boolean known = net.conczin.mca.MCAClient.playerData.containsKey(owner)
				|| connection != null && connection.getPlayerInfo(owner) != null;
			if (!known) {
				return null;
			}
			return net.conczin.mca.MCAClient.getVillagerRendererData(owner)
				.map(look -> look instanceof Entity entity ? entity : null)
				.orElse(null);
		}

		static <T> T infected(Entity villager, java.util.function.Supplier<T> draw) {
			if (!(villager instanceof net.conczin.mca.entity.Infectable infectable)) {
				return draw.get();
			}
			float was = infectable.getInfectionProgress();
			infectable.setInfectionProgress(1.0F);
			try {
				return draw.get();
			} finally {
				infectable.setInfectionProgress(was);
			}
		}
	}
}
