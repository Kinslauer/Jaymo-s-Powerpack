package com.jaymospowers.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;

/**
 * Fast flight (Wind, Z Fighter, Xenoform, elytra, a ridden mount, anything) never outruns the terrain. Each move of
 * the player, or of what they ride, looks a second ahead along the way they're going; if the ground there hasn't
 * arrived from the server or hasn't been drawn yet, they slow to a speed that reaches it no sooner than it's ready, and
 * pick up again as it loads. Without this they flew into chunks the game hadn't shown and stuck on invisible blocks.
 * Only horizontal speed is held back (chunks are whole columns); momentum is kept, so flight resumes at full speed.
 * Applied from {@code TerrainAheadMixin}.
 */
public final class TerrainAhead {
	/** below this many blocks a tick (14 blocks a second; sprinting is 5.6) nothing is checked */
	static final double FREE_SPEED = 0.7;
	/** never slowed below this many blocks a tick, so the player can always edge on into what's loading */
	static final double MIN_SPEED = 0.45;
	/** how many ticks ahead the terrain must be ready */
	static final double LEAD_TICKS = 20;
	/** spacing of the look-ahead checks, in blocks */
	static final double STEP = 8;
	private TerrainAhead() {
	}

	/** {@code delta} cut down to what the terrain ahead allows; {@code entity}'s own momentum is left as it was */
	public static Vec3 limit(Entity entity, MoverType type, Vec3 delta) {
		if (type != MoverType.SELF) {
			return delta;
		}
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || entity != mc.player.getRootVehicle() || !entity.isLocalInstanceAuthoritative()) {
			return delta;
		}
		double speed = delta.horizontalDistance();
		if (speed <= FREE_SPEED || mc.levelRenderer == null) {
			return delta;
		}
		ClientLevel level = mc.level;
		if (level == null) {
			return delta;
		}
		// no further than the chunks the server will send (its view distance) or the client keeps (its render distance)
		double loaded = (mc.options.getEffectiveRenderDistance() - 1) * 16.0;
		double reach = Math.min(speed * LEAD_TICKS, loaded);
		double dx = delta.x / speed;
		double dz = delta.z / speed;
		int y = Mth.clamp(Mth.floor(entity.getY()), level.getMinY(), level.getMaxY());
		double ready = reach;
		for (double d = 0; d <= reach; d += STEP) {
			if (!ready(mc, level, entity.getX() + dx * d, y, entity.getZ() + dz * d)) {
				ready = d;
				break;
			}
		}
		double allowed = Math.max(MIN_SPEED, ready / LEAD_TICKS);
		if (speed <= allowed) {
			return delta;
		}
		double scale = allowed / speed;
		return new Vec3(delta.x * scale, delta.y, delta.z * scale);
	}

	/** the chunk column there has arrived and the section at that height has been built for drawing */
	private static boolean ready(Minecraft mc, ClientLevel level, double x, int y, double z) {
		int bx = Mth.floor(x);
		int bz = Mth.floor(z);
		return level.hasChunk(bx >> 4, bz >> 4) && mc.levelRenderer.isSectionCompiledAndVisible(new BlockPos(bx, y, bz), 0L);
	}
}
