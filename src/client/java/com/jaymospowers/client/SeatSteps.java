package com.jaymospowers.client;

import com.jaymoskitchen.horse.CartEntity;
import com.jaymospowers.core.MountClimb;
import com.jaymospowers.core.MountClimb.Kind;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The climbs that only the client sees ({@link MountClimbPose} plays them): the step up into a cart, wagon, coach,
 * rickshaw or cab and the step into a boat, and the way back down; and the villagers, guards, carters and caravan hands
 * (Kingdoms' folk and MCA's villagers) climbing onto and off horses on their own.
 * <p>
 * Nothing here holds a ride back. The server seats the rider as it always did, so a fare put in a cab, a carriage that
 * takes a ruler or a rider remounted after a revive is never delayed or broken; the client watches who has just gained
 * or lost a vehicle and, if they stood within {@link #REACH} of the seat the tick before, carries the body from where
 * they stood to it ({@code MountClimbPose.play}) while the clip plays. Anyone who appears already seated, or was put in
 * the seat from farther off, is simply seated. A player on a horse, donkey, mule or pig is the server's own climb
 * (MountClimb), and is left to it.
 */
public final class SeatSteps {
	/** A step is shown only if they stood this close (blocks) to the seat they took, or landed this close to the one they left. */
	static final double REACH = 4.0;

	/** Kingdoms' Wagon: its class, how a rider is told which one it is ({@code kind()}), and which kinds have a bench or a thwart. */
	private static final String WAGON = "com.jaymoskingdoms.Wagon";
	private static final String[] WAGON_BENCHES = {"CARRIAGE", "RICKSHAW", "CAB", "COACH"};
	private static final String WAGON_BOAT = "SKIFF";
	/** The namespaces of the folk who climb (Kingdoms' folk, MCA's villagers). */
	private static final Set<String> FOLK = Set.of("jaymoskingdoms", "mca");

	/** Where someone was last tick: on foot ({@code vehicle} -1), or in a vehicle (and the step down from it, if it has one). */
	private record Last(int vehicle, Vec3 at, float yaw, Vec3 vehicleAt, @Nullable Kind down) {
	}

	private static final Map<Integer, Last> LAST = new HashMap<>();
	private static final Map<EntityType<?>, Boolean> FOLK_TYPES = new HashMap<>();
	private static boolean wagonLooked;
	private static @Nullable Class<?> wagonClass;
	private static @Nullable Method wagonKind;
	private static final Map<String, Integer> WAGON_KINDS = new HashMap<>();

	private SeatSteps() {
	}

	static void init() {
		ClientTickEvents.END_CLIENT_TICK.register(SeatSteps::tick);
	}

	private static void tick(Minecraft minecraft) {
		ClientLevel level = minecraft.level;
		if (level == null) {
			LAST.clear();
			return;
		}
		Set<Integer> seen = new HashSet<>();
		for (Entity entity : level.entitiesForRendering()) {
			if (!(entity instanceof LivingEntity who) || !(who instanceof Player || folk(who))) {
				continue;
			}
			int id = who.getId();
			seen.add(id);
			Last was = LAST.get(id);
			Entity vehicle = who.getVehicle();
			if (vehicle == null) {
				if (was != null && was.vehicle >= 0) {
					dismounted(level, who, was);
				}
				LAST.put(id, new Last(-1, who.position(), MountClimbPose.yawOf(who), Vec3.ZERO, null));
				continue;
			}
			boolean horse = MountClimb.climbable(vehicle);
			Kind steps = horse ? null : stepsInto(vehicle);
			if (horse && who instanceof Player || !horse && steps == null) {
				// the server's own climb (a player's horse), or a seat that has no step
				LAST.remove(id);
				continue;
			}
			if (was != null && was.vehicle == -1 && !MountClimbPose.playing(id) && !who.isSpectator() && was.at.distanceTo(who.position()) <= REACH) {
				Kind on = steps != null ? steps : onHorse(who, vehicle, was.at);
				MountClimbPose.play(who, on, vehicle.getId(), was.at, was.yaw);
			}
			LAST.put(id, new Last(vehicle.getId(), who.position(), MountClimbPose.yawOf(vehicle), vehicle.position(),
				steps == null ? null : steps == Kind.STEP_IN ? Kind.STEP_OUT : Kind.STEP_DOWN));
		}
		LAST.keySet().retainAll(seen);
	}

	/** {@code who} has just got off what {@code was} says they sat in: down the way they landed, if it's still there and they landed beside it. */
	private static void dismounted(ClientLevel level, LivingEntity who, Last was) {
		Entity vehicle = level.getEntity(was.vehicle);
		if (vehicle == null || !vehicle.isAlive() || !who.isAlive() || who.isSpectator() || who.isInWater() || MountClimbPose.playing(who.getId())
			|| who.position().distanceTo(was.at) > REACH) {
			return;
		}
		Kind off = was.down != null ? was.down : MountClimbPose.offKind(was.vehicleAt, was.yaw, who.position());
		MountClimbPose.play(who, off, was.vehicle, was.at, was.yaw);
	}

	/** How {@code who} climbs onto a horse from {@code from}: up behind a rider already in the saddle, else from the side or the back they stood. */
	private static Kind onHorse(LivingEntity who, Entity horse, Vec3 from) {
		for (Entity rider : horse.getPassengers()) {
			if (rider != who && rider instanceof LivingEntity) {
				return Kind.ON_PILLION;
			}
		}
		return MountClimb.side(from, horse);
	}

	/**
	 * Whether {@code who} is one of the folk who climb on their own: a mob of Kingdoms' or MCA's, other than an animal
	 * (Kingdoms' horses ride in carts too, and mustn't be carried up like a person).
	 */
	private static boolean folk(LivingEntity who) {
		return who instanceof Mob && !(who instanceof Animal)
			&& FOLK_TYPES.computeIfAbsent(who.getType(), type -> FOLK.contains(BuiltInRegistries.ENTITY_TYPE.getKey(type).getNamespace()));
	}

	/** The step into {@code vehicle}: up into a cart or coach, into a boat; null for a seat without one (minecarts, pods, balloons...). */
	static @Nullable Kind stepsInto(Entity vehicle) {
		if (vehicle instanceof AbstractBoat) {
			return Kind.STEP_IN;
		}
		if (vehicle instanceof CartEntity) {
			return Kind.STEP_UP;
		}
		return wagon(vehicle);
	}

	/** Kingdoms' rickshaw, cab, carriage and coach (a step up), and the sky-cove skiff (a step in), by reflection: Powers doesn't depend on Kingdoms. */
	private static @Nullable Kind wagon(Entity vehicle) {
		if (!wagonLooked) {
			wagonLooked = true;
			try {
				Class<?> type = Class.forName(WAGON);
				wagonKind = type.getMethod("kind");
				for (String name : WAGON_BENCHES) {
					WAGON_KINDS.put(name, type.getField(name).getInt(null));
				}
				WAGON_KINDS.put(WAGON_BOAT, type.getField(WAGON_BOAT).getInt(null));
				wagonClass = type;
			} catch (ReflectiveOperationException | LinkageError e) {
				wagonClass = null;
			}
		}
		if (wagonClass == null || wagonKind == null || vehicle.getClass() != wagonClass) {
			return null;
		}
		try {
			int kind = (Integer)wagonKind.invoke(vehicle);
			if (kind == WAGON_KINDS.get(WAGON_BOAT)) {
				return Kind.STEP_IN;
			}
			for (String name : WAGON_BENCHES) {
				if (kind == WAGON_KINDS.get(name)) {
					return Kind.STEP_UP;
				}
			}
		} catch (ReflectiveOperationException | RuntimeException e) {
			// not one we can read
		}
		return null;
	}
}
