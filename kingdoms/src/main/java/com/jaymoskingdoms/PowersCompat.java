package com.jaymoskingdoms;

import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.137) Jaymo's Powers, when it's loaded: which power a player has. Read from Powers' own synced attachment
 * ({@code com.jaymospowers.core.Powers.POWER}, looked up once by reflection), so Kingdoms never needs Powers. The wind
 * dragons bond with some powers (Boreas with Cryokinesis), and Eurus and Aeolus will judge players by theirs.
 */
public final class PowersCompat {
	private static boolean looked;
	private static @Nullable AttachmentType<Identifier> power;
	/** (0.7.184) Powers' {@code Affinity.passDown(ServerPlayer)}. */
	private static java.lang.invoke.@Nullable MethodHandle passDown;
	/** (0.7.140) Powers' {@code Fate.isCorrupted(Player)}. */
	private static java.lang.invoke.@Nullable MethodHandle corrupted;

	/** (0.7.169) Powers' still red blood fleck (or null before it's looked up, or without Powers). */
	private static @Nullable ParticleOptions bloodFleck;
	private static boolean lookedForBlood;

	/** (0.7.184) Powers' {@code Dismember.beheadPinned(LivingEntity, LivingEntity, Vec3, float)}. */
	private static java.lang.invoke.@Nullable MethodHandle beheadPinned;
	private static boolean lookedForDismember;

	private PowersCompat() {
	}

	/**
	 * (0.7.184) Takes {@code target}'s head off as Jaymo's Powers' dismemberment does (a headless mob may stagger on a while,
	 * a skeleton fights on, anything else drops), {@code by} its killer, and nails the head to the wall at {@code wall},
	 * facing {@code yaw}. False without Powers, or if it has no head to lose.
	 */
	public static boolean beheadPinned(net.minecraft.world.entity.LivingEntity target, net.minecraft.world.entity.@Nullable LivingEntity by,
		net.minecraft.world.phys.Vec3 wall, float yaw) {
		if (!lookedForDismember) {
			lookedForDismember = true;
			if (FabricLoader.getInstance().isModLoaded("jaymospowers")) {
				try {
					beheadPinned = java.lang.invoke.MethodHandles.publicLookup().findStatic(Class.forName("com.jaymospowers.core.Dismember"), "beheadPinned",
						java.lang.invoke.MethodType.methodType(boolean.class, net.minecraft.world.entity.LivingEntity.class,
							net.minecraft.world.entity.LivingEntity.class, net.minecraft.world.phys.Vec3.class, float.class));
				} catch (ReflectiveOperationException | LinkageError e) {
					JaymosKingdoms.LOGGER.warn("Couldn't find Jaymo's Powers' dismemberment", e);
				}
			}
		}
		if (beheadPinned == null) {
			return false;
		}
		try {
			return (boolean)beheadPinned.invoke(target, by, wall, yaw);
		} catch (Throwable e) {
			return false;
		}
	}

	/**
	 * (0.7.169) Blood drops of this colour (RGB) and size for {@code sendParticles}: Jaymo's Powers' still red blood fleck
	 * (Andy: blood mustn't shimmer like redstone dust), or vanilla dust of that colour without Powers.
	 */
	public static ParticleOptions blood(int color, float size) {
		if (!lookedForBlood) {
			lookedForBlood = true;
			if (FabricLoader.getInstance().isModLoaded("jaymospowers")
				&& net.minecraft.core.registries.BuiltInRegistries.PARTICLE_TYPE.getValue(Identifier.fromNamespaceAndPath("jaymospowers",
					"blood_fleck_red")) instanceof net.minecraft.core.particles.SimpleParticleType fleck) {
				bloodFleck = fleck;
			}
		}
		return bloodFleck != null ? bloodFleck : new net.minecraft.core.particles.DustParticleOptions(color, size);
	}

	/** The id path of {@code player}'s power ("cryo", "wind", ...), or null without one (or without Powers). */
	public static @Nullable String power(Player player) {
		AttachmentType<Identifier> type = type();
		Identifier id = type == null ? null : player.getAttached(type);
		return id == null ? null : id.getPath();
	}

	/** (0.7.140) Whether {@code player}'s power has been corrupted (Wind's Stormbound, say); false without Powers. */
	public static boolean corrupted(Player player) {
		type();
		try {
			return corrupted != null && (boolean)corrupted.invoke(player);
		} catch (Throwable e) {
			return false;
		}
	}

	/**
	 * (0.7.184) {@link Lineage}: {@code player} lives on as their child, so their power leaves them and is handed down, to
	 * awaken in the child in time. Nothing without Powers.
	 */
	public static void passDown(net.minecraft.server.level.ServerPlayer player) {
		type();
		if (passDown == null) {
			return;
		}
		try {
			passDown.invoke(player);
		} catch (Throwable e) {
			JaymosKingdoms.LOGGER.warn("Couldn't hand down {}'s power", player.getGameProfile().name(), e);
		}
	}

	// ---- (0.7.212) healing magic: Powers' Mending, so Restoration heals the body's parts -------------------------------

	private static java.lang.invoke.@Nullable MethodHandle mend, closeCut, easeFever, restore, rekindle;
	private static boolean lookedForMending;

	private static void mending() {
		if (lookedForMending) {
			return;
		}
		lookedForMending = true;
		if (!FabricLoader.getInstance().isModLoaded("jaymospowers")) {
			return;
		}
		try {
			Class<?> c = Class.forName("com.jaymospowers.core.wounds.Mending");
			java.lang.invoke.MethodHandles.Lookup lookup = java.lang.invoke.MethodHandles.publicLookup();
			Class<?> living = net.minecraft.world.entity.LivingEntity.class;
			mend = lookup.findStatic(c, "mend", java.lang.invoke.MethodType.methodType(float.class, living, float.class));
			closeCut = lookup.findStatic(c, "closeCut", java.lang.invoke.MethodType.methodType(boolean.class, living));
			easeFever = lookup.findStatic(c, "easeFever", java.lang.invoke.MethodType.methodType(boolean.class, living));
			restore = lookup.findStatic(c, "restore", java.lang.invoke.MethodType.methodType(void.class, living));
			rekindle = lookup.findStatic(c, "rekindle", java.lang.invoke.MethodType.methodType(boolean.class, living, float.class));
		} catch (ReflectiveOperationException | LinkageError e) {
			JaymosKingdoms.LOGGER.warn("Couldn't find Jaymo's Powers' Mending (older Powers?)", e);
		}
	}

	/** Heals {@code amount} into {@code entity}'s worst parts (Powers' wounds), or just the hearts without Powers. Nothing on the downed. Returns what it took. */
	public static float mend(net.minecraft.world.entity.LivingEntity entity, float amount) {
		mending();
		if (mend != null) {
			try {
				return (float)mend.invoke(entity, amount);
			} catch (Throwable e) {
				return 0.0F;
			}
		}
		if (Downed.isDowned(entity)) {
			return 0.0F;
		}
		float before = entity.getHealth();
		entity.heal(amount);
		return entity.getHealth() - before;
	}

	/** Closes {@code entity}'s worst open cut (never a stump); false without Powers. */
	public static boolean closeCut(net.minecraft.world.entity.LivingEntity entity) {
		mending();
		try {
			return closeCut != null && (boolean)closeCut.invoke(entity);
		} catch (Throwable e) {
			return false;
		}
	}

	/** Eases {@code entity}'s fever (its weakness, the dirt in its cuts); false without Powers. */
	public static boolean easeFever(net.minecraft.world.entity.LivingEntity entity) {
		mending();
		try {
			return easeFever != null && (boolean)easeFever.invoke(entity);
		} catch (Throwable e) {
			return false;
		}
	}

	/** Restoration: bones set, festering cured, cuts closed, up if downed, and whole again (without Powers, full health). */
	public static void restore(net.minecraft.world.entity.LivingEntity entity) {
		mending();
		if (restore != null) {
			try {
				restore.invoke(entity);
				return;
			} catch (Throwable e) {
				// (falls through to the hearts)
			}
		}
		entity.setHealth(entity.getMaxHealth());
	}

	/** Rekindle: a downed one up from afar with {@code share} of their health; false if they weren't down (or without Powers). */
	public static boolean rekindle(net.minecraft.world.entity.LivingEntity entity, float share) {
		mending();
		if (rekindle == null) {
			return false;
		}
		try {
			return (boolean)rekindle.invoke(entity, share);
		} catch (Throwable e) {
			return false;
		}
	}

	@SuppressWarnings("unchecked")
	private static @Nullable AttachmentType<Identifier> type() {
		if (!looked) {
			looked = true;
			if (FabricLoader.getInstance().isModLoaded("jaymospowers")) {
				try {
					power = (AttachmentType<Identifier>)Class.forName("com.jaymospowers.core.Powers").getField("POWER").get(null);
					corrupted = java.lang.invoke.MethodHandles.publicLookup().findStatic(Class.forName("com.jaymospowers.core.Fate"), "isCorrupted",
						java.lang.invoke.MethodType.methodType(boolean.class, Player.class));
				} catch (ReflectiveOperationException | ClassCastException | LinkageError e) {
					JaymosKingdoms.LOGGER.warn("Couldn't read Jaymo's Powers' power attachment", e);
				}
				try {
					passDown = java.lang.invoke.MethodHandles.publicLookup().findStatic(Class.forName("com.jaymospowers.core.Affinity"), "passDown",
						java.lang.invoke.MethodType.methodType(void.class, net.minecraft.server.level.ServerPlayer.class));
				} catch (ReflectiveOperationException | LinkageError e) {
					JaymosKingdoms.LOGGER.warn("Jaymo's Powers can't hand a power down (older Powers?)", e);
				}
			}
		}
		return power;
	}
}
