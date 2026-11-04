package com.jaymospowers.power.xenoform;

import com.jaymospowers.compat.KingdomsCompat;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.enderdragon.EnderDragonPart;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.ElderGuardian;
import net.minecraft.world.entity.monster.warden.Warden;

/**
 * (0.20.9) What counts as a great foe for the unforged band's last stand ({@link XenoformPower#ULT_LAST_STAND}): a vanilla boss
 * (the Ender Dragon, the Wither, the Elder Guardian, the Warden), anything Jaymo's Kingdoms calls a boss (read through
 * {@link KingdomsCompat}, the same test as its world tiers), or anything carrying {@link #TAG} (for Powers' own bosses to opt in).
 */
public final class GreatFoes {
	/** The entity tag that makes anything a great foe. */
	public static final String TAG = "jaymospowers_great_foe";

	private GreatFoes() {
	}

	public static boolean is(Entity entity) {
		if (entity instanceof EnderDragonPart part) {
			entity = part.parentMob;
		}
		return entity instanceof EnderDragon || entity instanceof WitherBoss || entity instanceof ElderGuardian || entity instanceof Warden
			|| entity.entityTags().contains(TAG) || KingdomsCompat.boss(entity) || hasBossBar(entity);
	}

	private static final java.util.Map<Class<?>, java.lang.reflect.Field[]> BAR_FIELDS = new java.util.concurrent.ConcurrentHashMap<>();

	/** (0.20.9) Whether the entity itself holds a visible {@link ServerBossEvent} (any mod's boss with a bar, found by its fields). */
	private static boolean hasBossBar(Entity entity) {
		java.lang.reflect.Field[] fields = BAR_FIELDS.computeIfAbsent(entity.getClass(), type -> {
			java.util.List<java.lang.reflect.Field> found = new java.util.ArrayList<>();
			for (Class<?> c = type; c != null && c != Entity.class; c = c.getSuperclass()) {
				for (java.lang.reflect.Field f : c.getDeclaredFields()) {
					if (ServerBossEvent.class.isAssignableFrom(f.getType()) && !java.lang.reflect.Modifier.isStatic(f.getModifiers())) {
						try {
							f.setAccessible(true);
							found.add(f);
						} catch (RuntimeException e) {
							// not readable: skipped
						}
					}
				}
			}
			return found.toArray(new java.lang.reflect.Field[0]);
		});
		for (java.lang.reflect.Field f : fields) {
			try {
				if (f.get(entity) instanceof ServerBossEvent bar && bar.isVisible()) {
					return true;
				}
			} catch (IllegalAccessException | RuntimeException e) {
				// skipped
			}
		}
		return false;
	}
}
