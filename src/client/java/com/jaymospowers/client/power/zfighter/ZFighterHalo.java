package com.jaymospowers.client.power.zfighter;

import com.jaymospowers.power.zfighter.ZFighterAfterlife;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/**
 * (0.17.62, Andy's NOEA picks) The halo of the dead: a thin gold ring turning over the head of every player who woke in the
 * Other World ({@link ZFighterAfterlife#STAY}, synced to everyone), drawn on this client only, in dust that fades by itself.
 * Nothing is sent: each client draws the rings of the players it can see.
 */
public final class ZFighterHalo {
	/** The ring's radius above the head (blocks), how far over the head it floats, how many motes a tick, and the dust. */
	static final double RADIUS = 0.34;
	static final double LIFT = 0.28;
	static final int MOTES = 4;
	static final float TURN = 0.21F;
	static final int COLOR = 0xFFE680;
	static final float SIZE = 0.55F;

	private ZFighterHalo() {
	}

	static void init() {
		ClientTickEvents.END_CLIENT_TICK.register(ZFighterHalo::tick);
	}

	private static void tick(Minecraft minecraft) {
		ClientLevel level = minecraft.level;
		if (level == null || minecraft.isPaused()) {
			return;
		}
		for (Player player : level.players()) {
			if (!player.isAlive() || !ZFighterAfterlife.among(player) || player.isSpectator()) {
				continue;
			}
			// first person: the ring is above the camera, so only others (and the third-person view) see it
			double y = player.getY() + player.getBbHeight() + LIFT;
			for (int i = 0; i < MOTES; i++) {
				float angle = (player.tickCount + i * (Mth.TWO_PI / MOTES / 2.0F)) * TURN + i * Mth.TWO_PI / MOTES;
				level.addParticle(new DustParticleOptions(COLOR, SIZE), player.getX() + Mth.cos(angle) * RADIUS, y,
					player.getZ() + Mth.sin(angle) * RADIUS, 0.0, 0.0, 0.0);
			}
		}
	}
}
