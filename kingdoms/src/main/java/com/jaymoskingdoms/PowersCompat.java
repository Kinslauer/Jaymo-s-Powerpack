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
	/** (0.7.140) Powers' {@code Fate.isCorrupted(Player)}. */
	private static java.lang.invoke.@Nullable MethodHandle corrupted;

	/** (0.7.169) Powers' still red blood fleck (or null before it's looked up, or without Powers). */
	private static @Nullable ParticleOptions bloodFleck;
	private static boolean lookedForBlood;

	/** (0.7.184) Powers' {@code Dismember.canLoseParts(LivingEntity)} and {@code Dismember.sever(LivingEntity, int, LivingEntity)}. */
	private static java.lang.invoke.@Nullable MethodHandle canLoseParts, sever;
	private static boolean lookedForDismember;

	private PowersCompat() {
	}

	/**
	 * (0.7.184) Takes {@code target}'s head off as Jaymo's Powers' dismemberment does (a headless mob may stagger on a while,
	 * a skeleton fights on, anything else drops), {@code by} its killer. False without Powers, or if it has no head to lose.
	 */
	public static boolean behead(net.minecraft.world.entity.LivingEntity target, net.minecraft.world.entity.@Nullable LivingEntity by) {
		if (!lookedForDismember) {
			lookedForDismember = true;
			if (FabricLoader.getInstance().isModLoaded("jaymospowers")) {
				try {
					Class<?> dismember = Class.forName("com.jaymospowers.core.Dismember");
					java.lang.invoke.MethodHandles.Lookup lookup = java.lang.invoke.MethodHandles.publicLookup();
					canLoseParts = lookup.findStatic(dismember, "canLoseParts",
						java.lang.invoke.MethodType.methodType(boolean.class, net.minecraft.world.entity.LivingEntity.class));
					sever = lookup.findStatic(dismember, "sever", java.lang.invoke.MethodType.methodType(boolean.class,
						net.minecraft.world.entity.LivingEntity.class, int.class, net.minecraft.world.entity.LivingEntity.class));
				} catch (ReflectiveOperationException | LinkageError e) {
					JaymosKingdoms.LOGGER.warn("Couldn't find Jaymo's Powers' dismemberment", e);
				}
			}
		}
		if (canLoseParts == null || sever == null) {
			return false;
		}
		try {
			// (Dismember.HEAD is 1)
			return (boolean)canLoseParts.invoke(target) && (boolean)sever.invoke(target, 1, by);
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
			}
		}
		return power;
	}
}
