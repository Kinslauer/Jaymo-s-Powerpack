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

	/**
	 * (0.7.212) What {@code player} has learned of Jaymo's Powers' alchemy: the research they've unlocked, how many essences
	 * they've discovered, and their alchemical corruption (all from Powers' synced attachments, by reflection). The floating
	 * islands' hermits ({@link HermitAlchemy}) read it to give hints. {@link Alchemy#NONE} without Powers (or an older one).
	 */
	public record Alchemy(java.util.Set<String> research, int essencesSeen, int corruption) {
		public static final Alchemy NONE = new Alchemy(java.util.Set.of(), 0, 0);

		public boolean started() {
			return !research.isEmpty();
		}

		public boolean knows(String id) {
			return research.contains(id);
		}
	}

	private static boolean lookedForAlchemy;
	private static java.lang.invoke.@Nullable MethodHandle knowledgeOf, stateResearch, stateSeen, corruptionTotal, fluxGet;

	@SuppressWarnings("unchecked")
	public static Alchemy alchemy(Player player) {
		lookForAlchemy();
		if (knowledgeOf == null) {
			return Alchemy.NONE;
		}
		try {
			Object state = knowledgeOf.invoke(player);
			java.util.Set<String> research = (java.util.Set<String>)stateResearch.invoke(state);
			int seen = ((java.util.Set<?>)stateSeen.invoke(state)).size();
			int corruption = corruptionTotal == null ? 0 : (int)corruptionTotal.invoke(player);
			return new Alchemy(java.util.Set.copyOf(research), seen, corruption);
		} catch (Throwable e) {
			return Alchemy.NONE;
		}
	}

	/** (0.7.212) The alchemical flux pooled in the chunk at {@code pos} (0 without Powers). */
	public static int flux(net.minecraft.server.level.ServerLevel level, net.minecraft.core.BlockPos pos) {
		lookForAlchemy();
		try {
			return fluxGet == null ? 0 : (int)fluxGet.invoke(level, pos);
		} catch (Throwable e) {
			return 0;
		}
	}

	private static void lookForAlchemy() {
		if (lookedForAlchemy) {
			return;
		}
		lookedForAlchemy = true;
		if (!FabricLoader.getInstance().isModLoaded("jaymospowers")) {
			return;
		}
		var lookup = java.lang.invoke.MethodHandles.publicLookup();
		try {
			Class<?> state = Class.forName("com.jaymospowers.alchemy.Knowledge$State");
			knowledgeOf = lookup.findStatic(Class.forName("com.jaymospowers.alchemy.Knowledge"), "of", java.lang.invoke.MethodType.methodType(state, Player.class));
			stateResearch = lookup.findVirtual(state, "research", java.lang.invoke.MethodType.methodType(java.util.Set.class));
			stateSeen = lookup.findVirtual(state, "seen", java.lang.invoke.MethodType.methodType(java.util.Set.class));
			corruptionTotal = lookup.findStatic(Class.forName("com.jaymospowers.alchemy.Corruption"), "total",
				java.lang.invoke.MethodType.methodType(int.class, Player.class));
			fluxGet = lookup.findStatic(Class.forName("com.jaymospowers.alchemy.Flux"), "get", java.lang.invoke.MethodType.methodType(int.class,
				net.minecraft.server.level.ServerLevel.class, net.minecraft.core.BlockPos.class));
		} catch (ReflectiveOperationException | LinkageError e) {
			knowledgeOf = null;
			JaymosKingdoms.LOGGER.debug("Jaymo's Powers has no alchemy (older Powers?)", e);
		}
	}
}
