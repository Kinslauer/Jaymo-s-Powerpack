package com.jaymoskingdoms.client;

import com.jaymoskingdoms.Celebrations;
import it.unimi.dsi.fastutil.ints.IntList;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.item.component.FireworkExplosion;
import net.minecraft.world.phys.Vec3;

/**
 * A far city's fireworks ({@link Celebrations}), drawn here. A burst further off than {@link #PROXY} blocks is drawn
 * that far along the same line of sight, in the same place against the sky, so a show hundreds of blocks away still
 * reads as a show and not as specks (the way the stars are drawn on a near dome); a near one is drawn where it is.
 */
public final class CelebrationsClient {
	/** How far off (blocks) a burst is drawn at most. */
	static final double PROXY = 96.0;

	private CelebrationsClient() {
	}

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(Celebrations.Burst.TYPE, (payload, context) -> context.client().execute(() -> burst(payload)));
	}

	private static void burst(Celebrations.Burst b) {
		Minecraft minecraft = Minecraft.getInstance();
		ClientLevel level = minecraft.level;
		if (level == null) {
			return;
		}
		Vec3 eye = minecraft.gameRenderer.mainCamera().position();
		Vec3 at = new Vec3(b.x(), b.y(), b.z());
		double away = at.distanceTo(eye);
		if (away > PROXY) {
			at = eye.add(at.subtract(eye).scale(PROXY / away));
		}
		FireworkExplosion.Shape[] shapes = FireworkExplosion.Shape.values();
		FireworkExplosion explosion = new FireworkExplosion(shapes[Math.floorMod(b.shape(), shapes.length)], IntList.of(b.colours().stream()
			.mapToInt(Integer::intValue).toArray()), IntList.of(b.fades().stream().mapToInt(Integer::intValue).toArray()), b.trail(), b.twinkle());
		level.createFireworks(at.x, at.y, at.z, 0.0, 0.0, 0.0, List.of(explosion), true);
	}
}
