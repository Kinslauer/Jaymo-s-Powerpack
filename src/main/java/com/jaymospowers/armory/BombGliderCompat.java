package com.jaymospowers.armory;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.power.arsenal.ArsenalPool;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * The bomb glider's only look at Balkon's WeaponMod (optional, MIT): its dynamite stick ({@code weaponmod:dynamite}, the
 * item and the lit entity, {@code ckathode.weaponmod.entity.projectile.EntityDynamite}), found by registry id at runtime
 * with no compile dependency, the way {@link ArsenalPool} finds its weapons. Without WeaponMod the glider's rack takes
 * vanilla TNT only.
 */
public final class BombGliderCompat {
	/** WeaponMod's dynamite: the item's id and the lit entity type's are the same. */
	static final Identifier DYNAMITE = Identifier.fromNamespaceAndPath(ArsenalPool.WEAPON_MOD, "dynamite");

	/** The lit stick's class (only named, never linked). */
	static final String DYNAMITE_ENTITY = "ckathode.weaponmod.entity.projectile.EntityDynamite";

	private static final boolean LOADED = FabricLoader.getInstance().isModLoaded(ArsenalPool.WEAPON_MOD);

	private BombGliderCompat() {
	}

	public static boolean loaded() {
		return LOADED;
	}

	/** Whether {@code stack} is WeaponMod's dynamite (false without WeaponMod). */
	public static boolean isDynamite(ItemStack stack) {
		return LOADED && !stack.isEmpty() && DYNAMITE.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()));
	}

	/**
	 * A lit stick of WeaponMod's dynamite (its own fuse, 1 to 2.5 s), not yet in the world; null without WeaponMod or if
	 * its entity type isn't registered.
	 */
	static @Nullable Entity dynamite(ServerLevel level) {
		if (!LOADED) {
			return null;
		}
		EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(DYNAMITE).orElse(null);
		return type == null ? null : type.create(level, EntitySpawnReason.TRIGGERED);
	}

	/**
	 * Stretches a lit stick's fuse to at least {@code ticks}, so one dropped from high up lands before it goes off.
	 * WeaponMod keeps its fuse in a private field with no setter ({@code EntityDynamite.explodefuse}), so it's set by
	 * name; if that ever changes, the stick keeps its own fuse.
	 */
	static void atLeastFuse(Entity stick, int ticks) {
		if (fuse == null) {
			return;
		}
		try {
			if (fuse.getDeclaringClass().isInstance(stick) && fuse.getInt(stick) < ticks) {
				fuse.setInt(stick, ticks);
			}
		} catch (ReflectiveOperationException | RuntimeException e) {
			fuse = null;
		}
	}

	private static java.lang.reflect.@Nullable Field fuse = findFuse();

	private static java.lang.reflect.@Nullable Field findFuse() {
		if (!LOADED) {
			return null;
		}
		try {
			java.lang.reflect.Field field = Class.forName(DYNAMITE_ENTITY).getDeclaredField("explodefuse");
			field.setAccessible(true);
			return field;
		} catch (ReflectiveOperationException | RuntimeException e) {
			JaymosPowers.LOGGER.warn("Bomb glider: couldn't reach WeaponMod's dynamite fuse ({}); dropped sticks keep their own", e.toString());
			return null;
		}
	}
}
