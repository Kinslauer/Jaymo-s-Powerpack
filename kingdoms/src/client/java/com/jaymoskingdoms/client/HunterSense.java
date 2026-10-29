package com.jaymoskingdoms.client;

import com.jaymoskingdoms.Vampires;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * (0.7.323) Hunter's sense. A mortal player holding a torch (either hand) within {@link #RANGE} blocks of a vampire at
 * night sees that vampire's shadow lag: wisps of dark smoke trail where it stood {@link #LAG} ticks (half a second)
 * ago. It's drawn on this client alone, so only the torch-holder sees it: a clue, never a name tag. (The lighter of the
 * two ways to do it; a ghost of the whole model could replace the smoke.)
 */
public final class HunterSense {
	/** How near a vampire must be (blocks), and how far back its shadow trails (ticks). */
	static final double RANGE = 12.0;
	static final int LAG = 10;
	/** The wisps: dust colour and size, and how many a tick at the feet and the chest. */
	static final int DARK = 0x14081C;
	static final float SIZE = 1.1F;

	/** Where each vampire stood, tick by tick, by entity id. */
	private static final Map<Integer, ArrayDeque<Vec3>> TRAIL = new HashMap<>();

	private HunterSense() {
	}

	static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(HunterSense::tick);
	}

	private static boolean torch(ItemStack stack) {
		return stack.is(Items.TORCH) || stack.is(Items.SOUL_TORCH) || stack.is(Items.COPPER_TORCH);
	}

	private static void tick(Minecraft minecraft) {
		LocalPlayer me = minecraft.player;
		if (me == null || minecraft.level == null || minecraft.isPaused()) {
			TRAIL.clear();
			return;
		}
		boolean watching = !Vampires.isVampire(me) && Vampires.night(minecraft.level.getOverworldClockTime())
			&& (torch(me.getMainHandItem()) || torch(me.getOffhandItem()));
		if (!watching) {
			TRAIL.clear();
			return;
		}
		Iterator<Map.Entry<Integer, ArrayDeque<Vec3>>> old = TRAIL.entrySet().iterator();
		while (old.hasNext()) {
			var e = old.next();
			if (!(minecraft.level.getEntity(e.getKey()) instanceof Player p) || !p.isAlive() || p.distanceToSqr(me) > RANGE * RANGE * 4.0) {
				old.remove();
			}
		}
		for (Player other : minecraft.level.players()) {
			if (other == me || !other.isAlive() || other.isSpectator() || !Vampires.isVampire(other) || other.distanceToSqr(me) > RANGE * RANGE) {
				continue;
			}
			ArrayDeque<Vec3> trail = TRAIL.computeIfAbsent(other.getId(), id -> new ArrayDeque<>());
			trail.addLast(other.position());
			if (trail.size() <= LAG) {
				continue;
			}
			Vec3 then = trail.pollFirst();
			if (then.distanceToSqr(other.position()) < 0.04) {
				continue; // standing still: no shadow to lag
			}
			var random = minecraft.level.getRandom();
			minecraft.level.addParticle(new DustParticleOptions(DARK, SIZE), then.x + (random.nextDouble() - 0.5) * 0.4, then.y + 0.2 + random.nextDouble() * 0.4,
				then.z + (random.nextDouble() - 0.5) * 0.4, 0.0, 0.0, 0.0);
			minecraft.level.addParticle(new DustParticleOptions(DARK, SIZE), then.x + (random.nextDouble() - 0.5) * 0.4, then.y + 0.9 + random.nextDouble() * 0.6,
				then.z + (random.nextDouble() - 0.5) * 0.4, 0.0, 0.0, 0.0);
			if (random.nextInt(4) == 0) {
				minecraft.level.addParticle(ParticleTypes.SMOKE, then.x, then.y + 1.0, then.z, 0.0, 0.01, 0.0);
			}
		}
	}
}
