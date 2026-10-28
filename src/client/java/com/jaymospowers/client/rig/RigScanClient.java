package com.jaymospowers.client.rig;

import com.jaymospowers.rig.RigNet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;

/**
 * (0.11.87) A scan's finds, on the wearer's screen only: the creatures glow through walls in their kind's colour (the
 * mixins in {@code client.rig.mixin}), the chests and ores are marked on the HUD ({@link RigHud}), for
 * {@link #SHOW_TICKS}. The locked target glows too, for as long as the lock holds.
 */
public final class RigScanClient {
	/** How long a scan's finds stay lit (ticks). */
	static final int SHOW_TICKS = 200;
	/** Colours: hostile, player, chest, ore; and the lock. */
	static final int[] COLORS = {0xFF5040, 0xFFD040, 0xE0A040, 0x5FE6FF};
	static final int LOCK_COLOR = 0xFF3070;

	record Marker(BlockPos pos, int kind) {
	}

	private static final Map<Integer, Integer> ENTITIES = new HashMap<>();
	private static final List<Marker> BLOCKS = new ArrayList<>();
	private static int left;

	private RigScanClient() {
	}

	static void accept(RigNet.Scan scan) {
		ENTITIES.clear();
		BLOCKS.clear();
		int k = 0;
		for (int id : scan.entities()) {
			ENTITIES.put(id, k < scan.kinds().size() ? scan.kinds().get(k) : RigNet.FOUND_HOSTILE);
			k++;
		}
		for (long packed : scan.blocks()) {
			BLOCKS.add(new Marker(BlockPos.of(packed), k < scan.kinds().size() ? scan.kinds().get(k) : RigNet.FOUND_ORE));
			k++;
		}
		left = SHOW_TICKS;
	}

	static void clear() {
		ENTITIES.clear();
		BLOCKS.clear();
		left = 0;
	}

	static void tick(Minecraft minecraft, LocalPlayer player) {
		if (left > 0 && --left == 0) {
			clear();
		}
	}

	/** How much of the scan's show is left (0..1). */
	static float fade() {
		return left / (float)SHOW_TICKS;
	}

	static List<Marker> blocks() {
		return BLOCKS;
	}

	/** The glow colour (RGB) for {@code entity} on the local wearer's screen, 0 for none. */
	public static int color(Entity entity) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player == null) {
			return 0;
		}
		// (0.17.118) entities drawn outside the world (another mod's screen figures) have no id yet: never glow
		int id;
		try {
			id = entity.getId();
		} catch (IllegalStateException e) {
			return 0;
		}
		if (RigClient.status.lock() >= 0 && id == RigClient.status.lock()) {
			return LOCK_COLOR;
		}
		Integer kind = ENTITIES.get(id);
		return kind == null || kind < 0 || kind >= COLORS.length ? 0 : COLORS[kind];
	}
}
