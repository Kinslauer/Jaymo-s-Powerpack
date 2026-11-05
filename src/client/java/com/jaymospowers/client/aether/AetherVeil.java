package com.jaymospowers.client.aether;

import java.lang.reflect.Method;
import java.util.List;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelHeightAccessor;

/**
 * (0.20.52, Andy: "The aether should NOT be visible so easy from ground level especially, maybe it only reveals once you are
 * past the cloud layer?") In a One Dimension world the Aether's islands are ordinary terrain over the Overworld's sky
 * ({@link com.jaymospowers.compat.Stacked}), so from any hilltop you looked straight up at their undersides. Now, while the
 * camera is under the Overworld's clouds, nothing at or above the Aether's floor is drawn: not its chunks (Sodium's culling,
 * {@code client/mixin/sodium}), not its mobs or anything else in it (the game's frustum, {@code AetherVeilFrustumMixin}).
 * Fly or climb up through the clouds and it is there; sink back under them and it is gone again.
 * <p>
 * It turns on above the clouds' top and off below their bottom, so walking a ridge at cloud height never flickers it, and the
 * change itself happens inside the cloud. Only a stacked world's Overworld level is touched: an ordinary world, or the Aether
 * reached as a dimension of its own, is drawn as before.
 */
public final class AetherVeil {
	// ---- tuning ----
	/** The Overworld's clouds, in its own heights (vanilla's cloud height, and the 4 blocks they stand). */
	static final double CLOUD_BOTTOM = 192.33, CLOUD_TOP = CLOUD_BOTTOM + 4.0;
	/** How often (ticks) to look again for the Aether band while the stack's layout hasn't reached the client yet. */
	static final int RETRY_TICKS = 40;
	static final String AETHER = "jaymospowers:aether", OVERWORLD = "minecraft:overworld";

	/** Nothing hidden. */
	public static final double NONE = Double.POSITIVE_INFINITY;
	/** Everything with its bottom at or above this height is hidden this frame ({@link #NONE}: nothing). Read from the cull thread too. */
	private static volatile double ceiling = NONE;
	/** Whether the camera is under the clouds (kept between the clouds' bottom and top, so it only flips when through them). */
	private static boolean below = true;

	private static ClientLevel layoutOf;
	private static int retry;
	/** This level's Aether floor, and its Overworld's cloud heights; floor {@link #NONE} when it has no Aether band. */
	private static double floor = NONE, cloudBottom, cloudTop;

	private static final boolean STACKS = FabricLoader.getInstance().isModLoaded("customchunks");
	private static final boolean SODIUM = FabricLoader.getInstance().isModLoaded("sodium");
	private static boolean reflected;
	private static Method layoutOfLevel, layers, source, minY, shift;

	private AetherVeil() {
	}

	public static void init() {
		if (STACKS) {
			ClientTickEvents.END_CLIENT_TICK.register(AetherVeil::tick);
		}
	}

	/** The height everything at or above is hidden at ({@link #NONE}: nothing is). */
	public static double ceiling() {
		return ceiling;
	}

	/** Whether a box with this bottom is hidden. */
	public static boolean hides(double bottom) {
		return bottom >= ceiling;
	}

	private static void tick(Minecraft minecraft) {
		ClientLevel level = minecraft.level;
		if (level == null || level.dimension() != Level.OVERWORLD) {
			set(NONE);
			layoutOf = null;
			return;
		}
		if (layoutOf != level || floor == NONE && --retry <= 0) {
			layoutOf = level;
			retry = RETRY_TICKS;
			read(level);
			below = true;
		}
		if (floor == NONE) {
			set(NONE);
			return;
		}
		double y = minecraft.gameRenderer.mainCamera().position().y;
		if (y > cloudTop) {
			below = false;
		} else if (y < cloudBottom) {
			below = true;
		}
		set(below ? floor : NONE);
	}

	private static void set(double value) {
		if (value != ceiling) {
			ceiling = value;
			// Sodium keeps the chunks it found visible between frames: have it look again with the new ceiling
			if (SODIUM) {
				SodiumVeil.refresh();
			}
		}
	}

	/** This level's Aether floor and its Overworld's cloud heights, from CustomChunks' stack layout (synced to the client). */
	private static void read(LevelHeightAccessor level) {
		floor = NONE;
		if (!api()) {
			return;
		}
		try {
			Object layout = layoutOfLevel.invoke(null, level);
			if (layout == null) {
				return;
			}
			double aether = NONE;
			int overworldShift = 0;
			for (Object layer : (List<?>) layers.invoke(layout)) {
				String id = String.valueOf(source.invoke(layer));
				if (AETHER.equals(id)) {
					aether = (Integer) minY.invoke(layer);
				} else if (OVERWORLD.equals(id)) {
					overworldShift = (Integer) shift.invoke(layer);
				}
			}
			cloudBottom = CLOUD_BOTTOM + overworldShift;
			cloudTop = CLOUD_TOP + overworldShift;
			// only when the Aether sits over the clouds (a stack in another order is left alone)
			floor = aether > cloudTop ? aether : NONE;
		} catch (ReflectiveOperationException | RuntimeException e) {
			floor = NONE;
		}
	}

	private static boolean api() {
		if (!reflected) {
			reflected = true;
			try {
				Class<?> layout = Class.forName("codx.customchunks.StackLayout");
				Class<?> layer = Class.forName("codx.customchunks.StackLayout$Layer");
				layoutOfLevel = layout.getMethod("of", LevelHeightAccessor.class);
				layers = layout.getMethod("layers");
				source = layer.getMethod("source");
				minY = layer.getMethod("minY");
				shift = layer.getMethod("shift");
			} catch (ReflectiveOperationException | LinkageError e) {
				layoutOfLevel = null;
			}
		}
		return layoutOfLevel != null;
	}
}
