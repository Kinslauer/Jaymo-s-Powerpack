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

	// ---- (0.7.216) a life story's power: Powers' affinity and callings ({@link LifeStory}) ---------------------------------

	/** Powers' {@code Affinity.set(ServerPlayer, Identifier)} and {@code Callings.call(ServerPlayer, String, int)}. */
	private static java.lang.invoke.@Nullable MethodHandle setAffinity, call;
	private static boolean lookedForAffinity;

	private static void affinities() {
		if (lookedForAffinity) {
			return;
		}
		lookedForAffinity = true;
		if (!FabricLoader.getInstance().isModLoaded("jaymospowers")) {
			return;
		}
		try {
			java.lang.invoke.MethodHandles.Lookup lookup = java.lang.invoke.MethodHandles.publicLookup();
			Class<?> sp = net.minecraft.server.level.ServerPlayer.class;
			setAffinity = lookup.findStatic(Class.forName("com.jaymospowers.core.Affinity"), "set", java.lang.invoke.MethodType.methodType(void.class, sp, Identifier.class));
			call = lookup.findStatic(Class.forName("com.jaymospowers.core.Callings"), "call", java.lang.invoke.MethodType.methodType(void.class, sp, String.class, int.class));
		} catch (ReflectiveOperationException | LinkageError e) {
			JaymosKingdoms.LOGGER.warn("Couldn't find Jaymo's Powers' affinity (older Powers?)", e);
		}
	}

	/** Leans {@code player}'s awakening to Powers' power {@code power} (an id path, or "fate" for a random one). Nothing without Powers. */
	public static void affinity(net.minecraft.server.level.ServerPlayer player, String power) {
		affinities();
		if (setAffinity == null || power.isEmpty()) {
			return;
		}
		try {
			setAffinity.invoke(player, Identifier.fromNamespaceAndPath("jaymospowers", power));
		} catch (Throwable e) {
			JaymosKingdoms.LOGGER.warn("Couldn't set {}'s affinity", player.getGameProfile().name(), e);
		}
	}

	/** Answers Powers' calling of {@code power} for {@code player}, taking {@code ticks} off their wait. Nothing without Powers. */
	public static void call(net.minecraft.server.level.ServerPlayer player, String power, int ticks) {
		affinities();
		if (call == null) {
			return;
		}
		try {
			call.invoke(player, power, ticks);
		} catch (Throwable e) {
			JaymosKingdoms.LOGGER.warn("Couldn't call {}'s power", player.getGameProfile().name(), e);
		}
	}

	/** Powers' {@code Powers.byName(String)} and {@code Powers.awakenAs(ServerPlayer, Power)}. */
	private static java.lang.invoke.@Nullable MethodHandle byName, awakenAs;
	private static boolean lookedForAwaken;

	/**
	 * (0.7.224) Andy: "Lost in time starts you with chrono level 1". {@code player} awakens to Powers' power {@code power}
	 * (an id path) now, with just its first ability, as a natural awakening starts. False without Powers.
	 */
	public static boolean awaken(net.minecraft.server.level.ServerPlayer player, String power) {
		if (!lookedForAwaken) {
			lookedForAwaken = true;
			if (FabricLoader.getInstance().isModLoaded("jaymospowers")) {
				try {
					Class<?> powers = Class.forName("com.jaymospowers.core.Powers"), type = Class.forName("com.jaymospowers.api.Power");
					java.lang.invoke.MethodHandles.Lookup lookup = java.lang.invoke.MethodHandles.publicLookup();
					byName = lookup.findStatic(powers, "byName", java.lang.invoke.MethodType.methodType(type, String.class));
					awakenAs = lookup.findStatic(powers, "awakenAs", java.lang.invoke.MethodType.methodType(void.class, net.minecraft.server.level.ServerPlayer.class, type));
				} catch (ReflectiveOperationException | LinkageError e) {
					JaymosKingdoms.LOGGER.warn("Couldn't find Jaymo's Powers' awakening (older Powers?)", e);
				}
			}
		}
		if (byName == null || awakenAs == null) {
			return false;
		}
		try {
			Object found = byName.invoke(power);
			if (found == null) {
				return false;
			}
			awakenAs.invoke(player, found);
			return true;
		} catch (Throwable e) {
			JaymosKingdoms.LOGGER.warn("Couldn't awaken {}'s power", player.getGameProfile().name(), e);
			return false;
		}
	}

	/** Powers' {@code Future.strand(ServerPlayer)}. */
	private static java.lang.invoke.@Nullable MethodHandle strand;
	private static boolean lookedForStrand;

	/**
	 * (0.7.224) A life story's "lost in time" start: {@code player} wakes alone in Powers' future. False without Powers,
	 * or while the future isn't in place yet.
	 */
	public static boolean strand(net.minecraft.server.level.ServerPlayer player) {
		if (!lookedForStrand) {
			lookedForStrand = true;
			if (FabricLoader.getInstance().isModLoaded("jaymospowers")) {
				try {
					strand = java.lang.invoke.MethodHandles.publicLookup().findStatic(Class.forName("com.jaymospowers.power.chrono.future.Future"), "strand",
						java.lang.invoke.MethodType.methodType(boolean.class, net.minecraft.server.level.ServerPlayer.class));
				} catch (ReflectiveOperationException | LinkageError e) {
					JaymosKingdoms.LOGGER.warn("Couldn't find Jaymo's Powers' future (older Powers?)", e);
				}
			}
		}
		if (strand == null) {
			return false;
		}
		try {
			return (boolean)strand.invoke(player);
		} catch (Throwable e) {
			JaymosKingdoms.LOGGER.warn("Couldn't send {} to the future", player.getGameProfile().name(), e);
			return false;
		}
	}

	/** Powers' {@code Dismember.bornWithout(LivingEntity, int)}. */
	private static java.lang.invoke.@Nullable MethodHandle bornWithout;
	private static boolean lookedForBornWithout;

	/**
	 * A life story's "unarmed" start: {@code player} has long been without {@code part} (Powers' Dismember part bit: an
	 * arm or a leg), the stump healed over. False without Powers.
	 */
	public static boolean bornWithout(net.minecraft.server.level.ServerPlayer player, int part) {
		if (!lookedForBornWithout) {
			lookedForBornWithout = true;
			if (FabricLoader.getInstance().isModLoaded("jaymospowers")) {
				try {
					bornWithout = java.lang.invoke.MethodHandles.publicLookup().findStatic(Class.forName("com.jaymospowers.core.Dismember"), "bornWithout",
						java.lang.invoke.MethodType.methodType(boolean.class, net.minecraft.world.entity.LivingEntity.class, int.class));
				} catch (ReflectiveOperationException | LinkageError e) {
					JaymosKingdoms.LOGGER.warn("Couldn't find Jaymo's Powers' lost limbs (older Powers?)", e);
				}
			}
		}
		if (bornWithout == null) {
			return false;
		}
		try {
			return (boolean)bornWithout.invoke(player, part);
		} catch (Throwable e) {
			return false;
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

	// ---- (0.7.218) gifted villagers: Powers' core/gifted (a villager's power and its moves) ------------------------------

	/** How a gifted villager fights (Powers' {@code Gifted.BOLD}...). */
	public static final int BOLD = 0, STEADY = 1, WARY = 2;
	private static java.lang.invoke.@Nullable MethodHandle giftGive, giftPower, giftCorrupted, giftUnleash, giftable;
	private static boolean lookedForGifts;

	private static void lookForGifts() {
		if (lookedForGifts) {
			return;
		}
		lookedForGifts = true;
		if (!FabricLoader.getInstance().isModLoaded("jaymospowers")) {
			return;
		}
		var lookup = java.lang.invoke.MethodHandles.publicLookup();
		Class<?> entity = net.minecraft.world.entity.Entity.class;
		try {
			Class<?> gifted = Class.forName("com.jaymospowers.core.gifted.Gifted");
			giftGive = lookup.findStatic(gifted, "give", java.lang.invoke.MethodType.methodType(boolean.class, entity, String.class, int.class));
			giftPower = lookup.findStatic(gifted, "power", java.lang.invoke.MethodType.methodType(String.class, entity));
			giftCorrupted = lookup.findStatic(gifted, "corrupted", java.lang.invoke.MethodType.methodType(boolean.class, entity));
			giftUnleash = lookup.findStatic(gifted, "unleash", java.lang.invoke.MethodType.methodType(boolean.class, entity, entity));
			giftable = lookup.findStatic(gifted, "giftable", java.lang.invoke.MethodType.methodType(java.util.List.class));
		} catch (ReflectiveOperationException | LinkageError e) {
			giftGive = null;
			JaymosKingdoms.LOGGER.debug("Jaymo's Powers has no gifted villagers (older Powers?)", e);
		}
	}

	/** Gives {@code entity} (a villager) the power {@code power} ("wind"...), fighting as {@code style}; false without Powers. */
	public static boolean give(net.minecraft.world.entity.Entity entity, String power, int style) {
		lookForGifts();
		try {
			return giftGive != null && (boolean)giftGive.invoke(entity, power, style);
		} catch (Throwable e) {
			return false;
		}
	}

	/** The id path of {@code entity}'s gift ("wind"...), or null (none, or no Powers). Either side. */
	public static @Nullable String gift(net.minecraft.world.entity.Entity entity) {
		lookForGifts();
		try {
			return giftGive == null ? null : (String)giftPower.invoke(entity);
		} catch (Throwable e) {
			return null;
		}
	}

	/** Whether {@code entity}'s gift has been corrupted (by long use). */
	public static boolean giftCorrupted(net.minecraft.world.entity.Entity entity) {
		lookForGifts();
		try {
			return giftGive != null && (boolean)giftCorrupted.invoke(entity);
		} catch (Throwable e) {
			return false;
		}
	}

	/** A gifted ruler's ultimate on {@code foe} (their throne room attacked); Powers keeps it rare. */
	public static boolean unleash(net.minecraft.world.entity.Entity ruler, net.minecraft.world.entity.Entity foe) {
		lookForGifts();
		try {
			return giftGive != null && (boolean)giftUnleash.invoke(ruler, foe);
		} catch (Throwable e) {
			return false;
		}
	}

	/** The powers a villager can be given ("wind", "cryo"...); empty without Powers. */
	@SuppressWarnings("unchecked")
	public static java.util.List<String> giftable() {
		lookForGifts();
		try {
			return giftGive == null ? java.util.List.of() : (java.util.List<String>)giftable.invoke();
		} catch (Throwable e) {
			return java.util.List.of();
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
	 * (0.7.215) What {@code player} has learned of Jaymo's Powers' alchemy: the research they've unlocked, how many essences
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

	/** (0.7.215) The alchemical flux pooled in the chunk at {@code pos} (0 without Powers). */
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
