package com.jaymospowers.combat;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.phys.Vec3;

/**
 * Batting a shot back (Andy's pick from Star Wars: Galaxy in a Cube, ideas only). A perfect parry made with a blade
 * (not a shield) knocks an arrow back toward whoever loosed it at {@link #STRENGTH} of the speed it came with, so it
 * lands for about half what it would have. Never a gun's ball or anything tagged unparryable ({@link Guard#shot}):
 * those are only ever blocked. Darktide's own guard sends shots back its own way (DarktideGuard).
 */
public final class Swat {
	/** The share of its speed a batted shot goes back with, the least speed it leaves at (blocks/tick), and how far it scatters (0: dead on). */
	public static final double STRENGTH = 0.5, MIN_SPEED = 0.6, SCATTER = 0.12;

	private record Batted(Projectile shot, ServerPlayer by, Vec3 velocity) {
	}

	private static final List<Batted> BATTED = new ArrayList<>();

	private Swat() {
	}

	static void init() {
		// (the shot's own bounce off a guard is set as the blow is refused: ours goes on the tick after)
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (BATTED.isEmpty()) {
				return;
			}
			for (Batted b : BATTED) {
				if (!b.shot().isRemoved()) {
					b.shot().setOwner(b.by());
					b.shot().setDeltaMovement(b.velocity());
					b.shot().needsSync = true;
				}
			}
			BATTED.clear();
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> BATTED.clear());
	}

	/** {@code player} perfectly parried {@code shot} with a blade: it goes back at its shooter (or the way it came). */
	static void back(ServerPlayer player, Projectile shot) {
		Vec3 incoming = shot.getDeltaMovement();
		double speed = Math.max(MIN_SPEED, incoming.length() * STRENGTH);
		Entity shooter = shot.getOwner();
		Vec3 dir;
		if (shooter != null && shooter != player && shooter.isAlive()) {
			dir = shooter.position().add(0.0, shooter.getBbHeight() * 0.6, 0.0).subtract(shot.position()).normalize();
		} else {
			dir = incoming.lengthSqr() > 1.0E-6 ? incoming.normalize().scale(-1.0) : player.getLookAngle();
		}
		var random = player.getRandom();
		dir = dir.add((random.nextDouble() - 0.5) * SCATTER, (random.nextDouble() - 0.5) * SCATTER, (random.nextDouble() - 0.5) * SCATTER).normalize();
		// (a little lift, so it carries to a far shooter at half speed)
		BATTED.add(new Batted(shot, player, dir.scale(speed).add(0.0, 0.08, 0.0)));
		swing(player);
	}

	/** The guard's blade is seen to swing at what it met. */
	public static void swing(ServerPlayer player) {
		player.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
		BladeSounds.swing(player.level(), player.getEyePosition(), BladeSounds.weight(player.getMainHandItem()), 0.6F, 1.2F);
	}
}
