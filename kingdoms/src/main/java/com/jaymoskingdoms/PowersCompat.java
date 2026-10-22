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

	private PowersCompat() {
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
