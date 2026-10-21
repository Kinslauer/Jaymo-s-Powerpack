package com.jaymoskingdoms.equestrian;

import java.util.function.Predicate;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * (0.7.107) Free rein: in third person the mouse moves the camera on its own and the movement keys turn the horse
 * toward where you're looking (W ahead, A and D to the sides, S back the way you came). The idea is Better Mount
 * Steering's (Seramicx, MIT); this is our own code. The client decides when it's on ({@code FreeReinClient}) and turns
 * the rider; here the ridden horse just goes forward whenever a movement key is held.
 */
public final class FreeRein {
	/** Whether {@code rider} steers by free rein now (set by the client; never on a server). */
	public static Predicate<Player> active = rider -> false;

	private FreeRein() {
	}

	/** (From {@code AbstractHorseMixin}) the horse's input under free rein: straight on, as hard as any key is held. */
	public static Vec3 input(Player rider, Vec3 input) {
		float push = Math.max(Math.abs(rider.zza), Math.abs(rider.xxa));
		return push > 0.0F ? new Vec3(0.0, input.y, push) : new Vec3(0.0, input.y, 0.0);
	}
}
