package com.jaymospowers.client;

import com.jaymospowers.JaymosPowers;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Where Icy's Better Horses (in the pack, shipped unchanged) really draws its rider, for the climbs onto and off a horse
 * ({@link MountClimbPose}). Icy's puts the seat higher than it draws it: a Percheron's by a quarter block, and a rearing
 * horse's back along its rump, both left in the seat the server keeps (and the camera) but taken off again when the rider
 * is drawn ({@code BhRiderSeat.applied}, by horse). Our climbs aimed at the server's seat, so a rider stood a quarter
 * block over a Percheron's saddle at the end of the climb and dropped into it; getting off started that high too. And its
 * breeds run from the Icelandic (1.2 blocks wide) to the Belgian (1.6), so a climber standing at a draft horse's shoulder
 * started inside its flank. Andy, 2026-11-27: "the horse mounting animations need to be readjusted for icy's models."
 */
final class IcySaddle {
	static final boolean LOADED = FabricLoader.getInstance().isModLoaded("icys-better-horses");
	/** A climber's foot in the stirrup: this far out from the flank (a player is 0.6 wide, so their middle, and a little room). */
	static final double STIRRUP_OUT = 0.35;
	/** The step out to the flank takes this first share of a climb from the side. */
	static final float STEP_OUT = 0.15F;

	private static @Nullable MethodHandle applied;
	private static boolean looked;

	private IcySaddle() {
	}

	/**
	 * How far Icy's draws a rider of {@code mount} from the seat the server keeps (world blocks; zero for anything Icy's
	 * doesn't draw, or without it). Icy's works it out each time the horse's seat is asked for, so ask for the seat first.
	 */
	static Vec3 drawnShift(Entity mount) {
		if (!LOADED || !(mount instanceof AbstractHorse)) {
			return Vec3.ZERO;
		}
		MethodHandle handle = handle();
		if (handle == null) {
			return Vec3.ZERO;
		}
		try {
			Object shift = handle.invoke(mount.getId());
			return shift instanceof Vec3 v ? v : Vec3.ZERO;
		} catch (Throwable e) {
			applied = null;
			return Vec3.ZERO;
		}
	}

	private static @Nullable MethodHandle handle() {
		if (!looked) {
			looked = true;
			try {
				Class<?> seat = Class.forName("icy.betterhorses.net.BhRiderSeat");
				applied = MethodHandles.publicLookup().findStatic(seat, "applied", MethodType.methodType(Vec3.class, int.class));
			} catch (ReflectiveOperationException | LinkageError e) {
				JaymosPowers.LOGGER.warn("Icy's Better Horses has no BhRiderSeat.applied: climbs aim at the server's seat", e);
			}
		}
		return applied;
	}

	/**
	 * Where someone climbing onto {@code mount} from its side ({@code side}: 1 its right, -1 its left) puts their foot in
	 * the stirrup, from {@code from}: no nearer its middle than its flank and {@link #STIRRUP_OUT}, straight out to the
	 * side if they stood closer (inside a big horse's shoulder), else {@code from} as it is.
	 */
	static Vec3 stirrup(Entity mount, Vec3 from, int side) {
		float facing = (mount instanceof LivingEntity living ? living.yBodyRot : mount.getYRot()) * Mth.DEG_TO_RAD;
		// the mount's right hand (yaw grows turning right: facing +z, its right is -x), as MountClimb's shy
		double rx = -Mth.cos(facing), rz = -Mth.sin(facing);
		double out = (from.x - mount.getX()) * rx + (from.z - mount.getZ()) * rz;
		double need = mount.getBbWidth() * 0.5 + STIRRUP_OUT;
		if (side * out >= need) {
			return from;
		}
		double push = side * need - out;
		return from.add(rx * push, 0.0, rz * push);
	}
}
