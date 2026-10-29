package com.jaymoskingdoms;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.animal.wolf.WolfSoundVariants;
import net.minecraft.world.phys.Vec3;

/**
 * (0.7.323) Tells: the small things that give a vampire away to the living.
 *
 * <ul>
 * <li>Animals (any {@link Animal}: cows, horses, cats, wolves) within {@link #RANGE} blocks of a vampire player shy away
 * from them. A tamed wolf stands its ground and growls ({@link #GROWL_EVERY}) at a vampire that isn't its owner; a pet
 * tamed by the vampire themselves stays calm.</li>
 * <li>Normal food gives a vampire nothing ({@link #noNutrition}; the eating animation and any potion effect stay, the
 * nutrition and saturation don't: {@code VampireFoodMixin}). A dhampir eats as anyone does.</li>
 * </ul>
 */
public final class VampireTells {
	/** Animals this close (blocks) feel it. */
	static final double RANGE = 6.0;
	/** A shy animal runs this far (blocks) at this speed. */
	static final int FLEE_DISTANCE = 12, FLEE_VERTICAL = 5;
	static final double FLEE_SPEED = 1.5;
	/** A tamed wolf growls this often (ticks) at the same vampire. */
	static final long GROWL_EVERY = 100L;

	/** No growl from this wolf before this game time (not kept). */
	private static final Map<UUID, Long> GROWLED = new HashMap<>();

	private VampireTells() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(VampireTells::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> GROWLED.clear());
	}

	/** A vampire whose body no longer takes ordinary food (a dhampir still does); on either side. */
	public static boolean noNutrition(Entity e) {
		return e instanceof net.minecraft.world.entity.player.Player && Vampires.isVampire(e) && !VampirePlayers.dhampir(e);
	}

	private static void tick(MinecraftServer server) {
		long now = server.overworld().getGameTime();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if ((now + player.getId()) % 20 != 7 || !player.isAlive() || player.isSpectator() || !noNutrition(player)) {
				continue;
			}
			ServerLevel level = player.level();
			for (Animal animal : level.getEntitiesOfClass(Animal.class, player.getBoundingBox().inflate(RANGE), Animal::isAlive)) {
				if (animal instanceof OwnableEntity pet && pet.getOwner() == player) {
					continue;
				}
				if (animal instanceof Wolf wolf && wolf.isTame()) {
					growl(level, wolf, now);
				} else if (!animal.isLeashed() && !animal.isVehicle() && !animal.isPassenger() && !animal.isSleeping()) {
					Vec3 away = DefaultRandomPos.getPosAway(animal, FLEE_DISTANCE, FLEE_VERTICAL, player.position());
					if (away != null) {
						animal.getNavigation().moveTo(away.x, away.y, away.z, FLEE_SPEED);
					}
				}
			}
		}
	}

	private static void growl(ServerLevel level, Wolf wolf, long now) {
		if (now < GROWLED.getOrDefault(wolf.getUUID(), 0L)) {
			return;
		}
		GROWLED.put(wolf.getUUID(), now + GROWL_EVERY);
		level.playSound(null, wolf.blockPosition(), SoundEvents.WOLF_SOUNDS.get(WolfSoundVariants.SoundSet.CLASSIC).adultSounds().growlSound().value(),
			SoundSource.NEUTRAL, 1.0F, 0.9F + wolf.getRandom().nextFloat() * 0.2F);
	}
}
