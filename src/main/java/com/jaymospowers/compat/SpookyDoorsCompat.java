package com.jaymospowers.compat;

import com.jaymospowers.JaymosPowers;
import java.lang.reflect.Method;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * (0.21.49) Spooky Doors (BlayTheNinth, All Rights Reserved, in the pack with Balm): doors that open partway. A downed
 * player's struggle ({@code core.wounds.DownedStruggle}) pushes one open a little more each moment it's held, through
 * Spooky Doors' own public API ({@code net.blay09.mods.spookydoors.api.SpookyDoorsAPI}), called by reflection so Powers
 * neither needs it to build nor to run. Nothing of Spooky Doors' is copied here. Without it every call is a no-op and the
 * struggle opens the door the vanilla way when it's done.
 */
public final class SpookyDoorsCompat {
	private static final boolean LOADED = FabricLoader.getInstance().isModLoaded("spookydoors");
	private static final String API = "net.blay09.mods.spookydoors.api.SpookyDoorsAPI";

	private static boolean looked;
	private static @Nullable Method percentOpen, operateDoor;

	private SpookyDoorsCompat() {
	}

	/** Whether Spooky Doors is installed and its API answers. */
	public static boolean loaded() {
		return LOADED && api();
	}

	/** How far open the door at {@code pos} is (0 shut, 1 open), as Spooky Doors has it; -1 without it. */
	public static float percentOpen(Level level, BlockPos pos) {
		if (!loaded()) {
			return -1.0F;
		}
		try {
			return (float) percentOpen.invoke(null, level, pos);
		} catch (ReflectiveOperationException | RuntimeException e) {
			fail(e);
			return -1.0F;
		}
	}

	/** Swings the door at {@code pos} to {@code percent} open (0 to 1), by {@code by} (its sounds and who opened it). */
	public static void operate(Level level, BlockPos pos, @Nullable Entity by, float percent) {
		if (!loaded()) {
			return;
		}
		try {
			operateDoor.invoke(null, level, pos, by, percent);
		} catch (ReflectiveOperationException | RuntimeException e) {
			fail(e);
		}
	}

	private static boolean api() {
		if (!looked) {
			looked = true;
			try {
				Class<?> api = Class.forName(API);
				percentOpen = api.getMethod("getPercentOpen", Level.class, BlockPos.class);
				operateDoor = api.getMethod("operateDoor", Level.class, BlockPos.class, Entity.class, float.class);
			} catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
				JaymosPowers.LOGGER.warn("Spooky Doors is loaded but its API couldn't be found; downed players open doors the vanilla way", e);
				percentOpen = null;
				operateDoor = null;
			}
		}
		return percentOpen != null && operateDoor != null;
	}

	private static void fail(Throwable e) {
		JaymosPowers.LOGGER.warn("Spooky Doors' API failed; downed players open doors the vanilla way from now on", e);
		percentOpen = null;
		operateDoor = null;
	}
}
