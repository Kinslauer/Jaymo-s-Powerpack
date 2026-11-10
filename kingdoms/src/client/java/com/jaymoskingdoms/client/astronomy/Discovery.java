package com.jaymoskingdoms.client.astronomy;

import com.jaymoskingdoms.astronomy.StarDiscovery;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import org.jspecify.annotations.Nullable;

/**
 * The client's view of the stars this player has discovered. The list itself is the server's
 * ({@link StarDiscovery#DISCOVERED}, synced to this player); here the client only counts how long the player has kept
 * each star in view, and after {@link #LOOK_TICKS} ticks reports it ({@link StarDiscovery.Looked}). Constellation figures
 * connect once all their stars are discovered.
 */
public final class Discovery {
	/** Ticks of steady looking at a star before it is reported (about 4 seconds). */
	static final int LOOK_TICKS = 80;

	private static final Discovery INSTANCE = new Discovery();

	/** Star HIP to ticks the player has looked at it (this session). */
	private final Map<Integer, Integer> looking = new HashMap<>();
	/** Reported and not yet confirmed by the server, so a star isn't reported every tick while the sync is on its way. */
	private final Set<Integer> reported = new HashSet<>();
	private @Nullable List<Integer> seenList;
	private Set<Integer> stars = Set.of();

	private Discovery() {
	}

	/** The discovery view for the player in the game; null with no player. */
	static @Nullable Discovery getOrCreate(Minecraft minecraft) {
		return minecraft.player == null ? null : INSTANCE.refresh(minecraft.player);
	}

	/** Re-reads the synced list when the server has sent a new one. */
	private Discovery refresh(LocalPlayer player) {
		List<Integer> list = player.getAttachedOrElse(StarDiscovery.DISCOVERED, List.of());
		if (list != this.seenList) {
			this.seenList = list;
			this.stars = Set.copyOf(list);
			this.reported.removeAll(this.stars);
		}
		return this;
	}

	boolean isDiscovered(int hip) {
		return this.stars.contains(hip);
	}

	/**
	 * Call once per tick while the player keeps {@code hip} in view at night. Reports it to the server once it has been
	 * watched for {@link #LOOK_TICKS} ticks; returns true that tick.
	 */
	boolean noteLook(int hip) {
		if (this.stars.contains(hip) || this.reported.contains(hip)) {
			return false;
		}
		int ticks = this.looking.merge(hip, 1, Integer::sum);
		if (ticks < LOOK_TICKS) {
			return false;
		}
		this.looking.remove(hip);
		this.reported.add(hip);
		ClientPlayNetworking.send(new StarDiscovery.Looked(hip));
		return true;
	}

	/**
	 * The player is no longer looking at {@code hip}: its count starts again, and a report the server turned down
	 * (under a roof, out of turns) can be made again next time.
	 */
	void stopLook(int hip) {
		this.looking.remove(hip);
		this.reported.remove(hip);
	}

	/** Leaving a world: nothing carries over to the next one (the next server sends its own list). */
	static void clear() {
		INSTANCE.looking.clear();
		INSTANCE.reported.clear();
		INSTANCE.seenList = null;
		INSTANCE.stars = Set.of();
	}
}
