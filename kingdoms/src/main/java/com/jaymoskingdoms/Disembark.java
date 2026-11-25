package com.jaymoskingdoms;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;

/**
 * Patch 6 of the fighting AI (getting there; the ideas are Hostile Humans', none of its code): a fighter sitting in a boat or
 * a minecart with its foe within {@link #REACH} gets out to fight (not when the foe rides the same one, and not the bound, the
 * arrested or a guard making an arrest). Every {@link #EVERY} ticks, over the fighters {@link Comrades} keeps near players.
 */
public final class Disembark {
	/** A foe this near (blocks) and it gets out. */
	static final double REACH = 6.0;
	/** Looked at this often (ticks). */
	static final int EVERY = 10;

	private Disembark() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Disembark::tick);
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % EVERY != 0) {
			return;
		}
		for (ServerLevel level : server.getAllLevels()) {
			for (Mob m : Comrades.fighters(level)) {
				Entity ride = m.getVehicle();
				if (!(ride instanceof AbstractBoat) && !(ride instanceof AbstractMinecart) || !m.isAlive() || Downed.isDowned(m) || Captives.bound(m)
					|| Arrests.busy(m) || Arrests.held(m)) {
					continue;
				}
				LivingEntity foe = Comrades.target(m);
				if (foe != null && !Downed.isDowned(foe) && foe.getVehicle() != ride && m.distanceToSqr(foe) < REACH * REACH) {
					m.stopRiding();
				}
			}
		}
	}
}
