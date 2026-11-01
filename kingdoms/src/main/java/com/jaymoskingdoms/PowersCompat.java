package com.jaymoskingdoms;

import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
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

	/** (Andy, 2026-11-01) Powers' {@code ZFighterFakeMoon.shines(ServerLevel, Entity)}. */
	private static java.lang.invoke.@Nullable MethodHandle falseMoon;
	private static boolean lookedForMoon;

	/**
	 * (Andy, 2026-11-01) Whether a Z Fighter's false moon (a great ball of ki thrown up into the night) shines on
	 * {@code entity}: it takes the wolf's curse like a full moon. False without Powers.
	 */
	public static boolean falseMoon(net.minecraft.server.level.ServerLevel level, net.minecraft.world.entity.Entity entity) {
		if (!lookedForMoon) {
			lookedForMoon = true;
			if (FabricLoader.getInstance().isModLoaded("jaymospowers")) {
				try {
					falseMoon = java.lang.invoke.MethodHandles.publicLookup().findStatic(Class.forName("com.jaymospowers.power.zfighter.ZFighterFakeMoon"),
						"shines", java.lang.invoke.MethodType.methodType(boolean.class, net.minecraft.server.level.ServerLevel.class,
							net.minecraft.world.entity.Entity.class));
				} catch (ReflectiveOperationException | LinkageError e) {
					JaymosKingdoms.LOGGER.warn("Couldn't find Jaymo's Powers' false moon", e);
				}
			}
		}
		if (falseMoon == null) {
			return false;
		}
		try {
			return (boolean)falseMoon.invoke(level, entity);
		} catch (Throwable e) {
			return false;
		}
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

	/** Powers' {@code Treatment.tendAll(LivingEntity, boolean)}. */
	private static java.lang.invoke.@Nullable MethodHandle tendAll;
	private static boolean lookedForTend;

	/**
	 * (0.7.224) Another's hands on {@code body}: every hurt part dressed with a rag (or a linen bandage), every break
	 * splinted, by Powers' wounds. How many parts were seen to; -1 without Powers (or an older one).
	 */
	public static int tendAll(net.minecraft.world.entity.LivingEntity body, boolean rag) {
		if (!lookedForTend) {
			lookedForTend = true;
			if (FabricLoader.getInstance().isModLoaded("jaymospowers")) {
				try {
					tendAll = java.lang.invoke.MethodHandles.publicLookup().findStatic(Class.forName("com.jaymospowers.core.wounds.Treatment"), "tendAll",
						java.lang.invoke.MethodType.methodType(int.class, net.minecraft.world.entity.LivingEntity.class, boolean.class));
				} catch (ReflectiveOperationException | LinkageError e) {
					JaymosKingdoms.LOGGER.warn("Couldn't find Jaymo's Powers' treatment (older Powers?)", e);
				}
			}
		}
		if (tendAll == null) {
			return -1;
		}
		try {
			return (int)tendAll.invoke(body, rag);
		} catch (Throwable e) {
			return -1;
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

	/** (Havens patch 5) Powers' {@code Baubles.worn(Player)}. */
	private static java.lang.invoke.@Nullable MethodHandle wornBaubles;
	private static boolean lookedForBaubles;

	/**
	 * (Havens patch 5) What {@code player} wears in Jaymo's Powers' bauble slots (server), empty stacks and all; an empty
	 * list without Powers (or an older one).
	 */
	@SuppressWarnings("unchecked")
	public static java.util.List<net.minecraft.world.item.ItemStack> baubles(Player player) {
		if (!lookedForBaubles) {
			lookedForBaubles = true;
			if (FabricLoader.getInstance().isModLoaded("jaymospowers")) {
				try {
					wornBaubles = java.lang.invoke.MethodHandles.publicLookup().findStatic(Class.forName("com.jaymospowers.baubles.Baubles"), "worn",
						java.lang.invoke.MethodType.methodType(java.util.List.class, Player.class));
				} catch (ReflectiveOperationException | LinkageError e) {
					JaymosKingdoms.LOGGER.warn("Couldn't find Jaymo's Powers' baubles (older Powers?)", e);
				}
			}
		}
		if (wornBaubles == null) {
			return java.util.List.of();
		}
		try {
			return (java.util.List<net.minecraft.world.item.ItemStack>)wornBaubles.invoke(player);
		} catch (Throwable e) {
			return java.util.List.of();
		}
	}

	/** (0.7.320) Powers' {@code ZFighterAfterlife.among(Player)}. */
	private static java.lang.invoke.@Nullable MethodHandle among;
	private static boolean lookedForAmong;

	/** (0.7.320) Whether {@code player} is one of the dead in Jaymo's Powers' Other World (the afterlife); false without Powers. */
	public static boolean amongTheDead(Player player) {
		if (!lookedForAmong) {
			lookedForAmong = true;
			if (FabricLoader.getInstance().isModLoaded("jaymospowers")) {
				try {
					among = java.lang.invoke.MethodHandles.publicLookup().findStatic(Class.forName("com.jaymospowers.power.zfighter.ZFighterAfterlife"),
						"among", java.lang.invoke.MethodType.methodType(boolean.class, Player.class));
				} catch (ReflectiveOperationException | LinkageError e) {
					JaymosKingdoms.LOGGER.warn("Couldn't find Jaymo's Powers' afterlife (older Powers?)", e);
				}
			}
		}
		if (among == null) {
			return false;
		}
		try {
			return (boolean)among.invoke(player);
		} catch (Throwable e) {
			return false;
		}
	}

	/** (0.7.330) Powers' {@code ZFighterLineage.ape(Player)}. */
	private static java.lang.invoke.@Nullable MethodHandle ape;
	private static boolean lookedForApe;

	/** (0.7.330) Whether {@code player} is a Great Ape now; false without Powers. */
	public static boolean greatApe(Player player) {
		if (!lookedForApe) {
			lookedForApe = true;
			if (FabricLoader.getInstance().isModLoaded("jaymospowers")) {
				try {
					ape = java.lang.invoke.MethodHandles.publicLookup().findStatic(Class.forName("com.jaymospowers.power.zfighter.ZFighterLineage"),
						"ape", java.lang.invoke.MethodType.methodType(boolean.class, Player.class));
				} catch (ReflectiveOperationException | LinkageError e) {
					JaymosKingdoms.LOGGER.warn("Couldn't find Jaymo's Powers' Great Ape (older Powers?)", e);
				}
			}
		}
		if (ape == null) {
			return false;
		}
		try {
			return (boolean)ape.invoke(player);
		} catch (Throwable e) {
			return false;
		}
	}

	/** (0.7.386) Powers' {@code EightSignCircle.sealed(Entity)}. */
	private static java.lang.invoke.@Nullable MethodHandle sealed;
	private static boolean lookedForSealed;

	/**
	 * (0.7.386) Whether {@code entity} stands in a Divine-born's Eight-Sign Circle (Jaymo's Powers) and so can't use powers or
	 * forms for now; false without Powers (or an older one).
	 */
	public static boolean sealed(net.minecraft.world.entity.Entity entity) {
		if (!lookedForSealed) {
			lookedForSealed = true;
			if (FabricLoader.getInstance().isModLoaded("jaymospowers")) {
				try {
					sealed = java.lang.invoke.MethodHandles.publicLookup().findStatic(Class.forName("com.jaymospowers.power.zfighter.divine.EightSignCircle"),
						"sealed", java.lang.invoke.MethodType.methodType(boolean.class, net.minecraft.world.entity.Entity.class));
				} catch (ReflectiveOperationException | LinkageError e) {
					JaymosKingdoms.LOGGER.warn("Couldn't find Jaymo's Powers' Eight-Sign Circle (older Powers?)", e);
				}
			}
		}
		if (sealed == null) {
			return false;
		}
		try {
			return (boolean)sealed.invoke(entity);
		} catch (Throwable e) {
			return false;
		}
	}

	/** (0.7.406) Powers' {@code Breaks.armless(Player)}. */
	private static java.lang.invoke.@Nullable MethodHandle armless;
	private static boolean lookedForArmless;

	/** (0.7.406) Whether {@code player} has both arms broken (Jaymo's Powers' wounds); false without Powers (or an older one). */
	public static boolean armless(Player player) {
		if (!lookedForArmless) {
			lookedForArmless = true;
			if (FabricLoader.getInstance().isModLoaded("jaymospowers")) {
				try {
					armless = java.lang.invoke.MethodHandles.publicLookup().findStatic(Class.forName("com.jaymospowers.core.wounds.Breaks"),
						"armless", java.lang.invoke.MethodType.methodType(boolean.class, Player.class));
				} catch (ReflectiveOperationException | LinkageError e) {
					JaymosKingdoms.LOGGER.warn("Couldn't find Jaymo's Powers' broken arms (older Powers?)", e);
				}
			}
		}
		if (armless == null) {
			return false;
		}
		try {
			return (boolean)armless.invoke(player);
		} catch (Throwable e) {
			return false;
		}
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

	// ---- (0.7.268) a film: Powers' UltimateFilm (letterbox bars, and one player's camera) ------------------------------

	/** Powers' {@code UltimateFilm.play(ServerPlayer, int, int, Vec3, Entity, double)} and {@code witnessed(Entity, int, int, Vec3, Entity, double)}. */
	private static java.lang.invoke.@Nullable MethodHandle filmPlay, filmWitnessed;
	private static boolean lookedForFilm;
	/** Powers' {@code UltimateFilm} shots. */
	public static final int FILM_ORBIT = 3, FILM_PUSH_IN = 6;
	/** (0.7.382) Powers' tilted (rolled) shot and its hard-cut shot between three angles (Powers 0.18.46; an older Powers shows nothing for them). */
	public static final int FILM_DUTCH = 8, FILM_CUTS = 9;

	private static void films() {
		if (lookedForFilm) {
			return;
		}
		lookedForFilm = true;
		if (!FabricLoader.getInstance().isModLoaded("jaymospowers")) {
			return;
		}
		try {
			java.lang.invoke.MethodHandles.Lookup lookup = java.lang.invoke.MethodHandles.publicLookup();
			Class<?> film = Class.forName("com.jaymospowers.core.UltimateFilm");
			filmPlay = lookup.findStatic(film, "play", java.lang.invoke.MethodType.methodType(void.class, net.minecraft.server.level.ServerPlayer.class,
				int.class, int.class, net.minecraft.world.phys.Vec3.class, net.minecraft.world.entity.Entity.class, double.class));
			filmWitnessed = lookup.findStatic(film, "witnessed", java.lang.invoke.MethodType.methodType(void.class, net.minecraft.world.entity.Entity.class,
				int.class, int.class, net.minecraft.world.phys.Vec3.class, net.minecraft.world.entity.Entity.class, double.class));
		} catch (ReflectiveOperationException | LinkageError e) {
			JaymosKingdoms.LOGGER.warn("Jaymo's Powers has no films (older Powers?)", e);
		}
	}

	/**
	 * (0.7.268) A short film of {@code caster}'s move: {@code star} (a player, if any) gets the camera {@code shot} framed on
	 * {@code at} and {@code target} for {@code ticks}, and everyone within {@code witnesses} blocks of the caster sees black
	 * bars. Nothing without Powers.
	 */
	public static void film(net.minecraft.world.entity.Entity caster, net.minecraft.server.level.@Nullable ServerPlayer star, int shot, int ticks,
		net.minecraft.world.phys.Vec3 at, net.minecraft.world.entity.@Nullable Entity target, double witnesses) {
		films();
		try {
			if (star != null && filmPlay != null) {
				filmPlay.invoke(star, shot, ticks, at, target, witnesses);
			}
			if (filmWitnessed != null) {
				// (the star has their bars already: their own film stays, and no one else's camera is taken)
				filmWitnessed.invoke(caster, shot, ticks, at, target, witnesses);
			}
		} catch (Throwable e) {
			JaymosKingdoms.LOGGER.warn("Couldn't play a film", e);
		}
	}

	/**
	 * (0.7.459) Powers' executions of the downed (pack 0.19.20, {@code MobExecutions}): Andy: guards "prioritize arresting
	 * you because unless they are of a certain temperament they try to be righteous and just". A realm's guard or archer
	 * ({@link Downed#isGuard}) or a village's own iron golem never finishes off a downed player, unless the guard is
	 * hotheaded or spiteful ({@link Temperaments}); they arrest as before. Nothing without that Powers.
	 */
	@SuppressWarnings("unchecked")
	static void spareRighteousGuards() {
		if (!FabricLoader.getInstance().isModLoaded("jaymospowers")) {
			return;
		}
		try {
			Object list = Class.forName("com.jaymospowers.core.wounds.MobExecutions").getField("SPARE").get(null);
			((java.util.List<java.util.function.BiPredicate<net.minecraft.world.entity.Mob, LivingEntity>>)list).add((mob, victim) -> {
				if (mob instanceof net.minecraft.world.entity.animal.golem.IronGolem golem) {
					return !golem.isPlayerCreated();
				}
				if (!Downed.isGuard(mob)) {
					return false;
				}
				if (victim instanceof net.minecraft.server.level.ServerPlayer p && Unmasking.awaits(p)) {
					// (0.7.473) a masked player downed in town: the guards see who it is before anything else
					return true;
				}
				Temperaments.Temperament t = Temperaments.of(mob);
				return t != Temperaments.Temperament.HOTHEADED && t != Temperaments.Temperament.SPITEFUL;
			});
		} catch (ReflectiveOperationException | LinkageError | ClassCastException e) {
			JaymosKingdoms.LOGGER.warn("Jaymo's Powers has no executions to spare guards from (older Powers?)", e);
		}
	}

	private static java.lang.invoke.@Nullable MethodHandle unmaskPlayer, stripCorpse;
	private static boolean lookedForUnmask;

	private static void lookForUnmask() {
		if (lookedForUnmask) {
			return;
		}
		lookedForUnmask = true;
		if (!FabricLoader.getInstance().isModLoaded("jaymospowers")) {
			return;
		}
		try {
			Class<?> c = Class.forName("com.jaymospowers.costume.CorpseCostume");
			java.lang.invoke.MethodHandles.Lookup lookup = java.lang.invoke.MethodHandles.publicLookup();
			unmaskPlayer = lookup.findStatic(c, "unmask", java.lang.invoke.MethodType.methodType(boolean.class, net.minecraft.server.level.ServerPlayer.class));
			stripCorpse = lookup.findStatic(c, "strip", java.lang.invoke.MethodType.methodType(boolean.class, net.minecraft.world.entity.Entity.class,
				LivingEntity.class));
		} catch (ReflectiveOperationException | LinkageError e) {
			JaymosKingdoms.LOGGER.warn("Jaymo's Powers has no corpse costumes (older Powers?)", e);
		}
	}

	/**
	 * (0.7.473) A guard takes a downed {@code player}'s face covering off (Powers' {@code CorpseCostume.unmask}: a costume's
	 * mask or hood, a Rig Helm's faceplate, a bandit mask). False without it, or if nothing came off.
	 */
	static boolean unmaskPlayer(net.minecraft.server.level.ServerPlayer player) {
		lookForUnmask();
		try {
			return unmaskPlayer != null && (boolean)unmaskPlayer.invoke(player);
		} catch (Throwable e) {
			JaymosKingdoms.LOGGER.warn("Couldn't unmask a downed player", e);
			return false;
		}
	}

	/**
	 * (0.7.473) {@code by} pulls the costume off a dead player's body (Powers' {@code CorpseCostume.strip}; Powers then calls
	 * {@link Identities#corpseUnmasked}). False without it.
	 */
	static boolean stripCorpse(net.minecraft.world.entity.Entity corpse, LivingEntity by) {
		lookForUnmask();
		try {
			return stripCorpse != null && (boolean)stripCorpse.invoke(corpse, by);
		} catch (Throwable e) {
			JaymosKingdoms.LOGGER.warn("Couldn't strip a costumed body", e);
			return false;
		}
	}

	private static java.lang.invoke.@Nullable MethodHandle deathFilm;
	private static boolean lookedForDeathFilm;

	/**
	 * (0.7.431) A film for {@code dead}, who has just died, before their death screen: shot {@code shot} on {@code target}
	 * for {@code ticks} (Powers' {@code UltimateFilm.afterDeath}, 0.18.106). False without it (an older Powers, or none).
	 */
	public static boolean deathFilm(net.minecraft.server.level.ServerPlayer dead, int shot, int ticks, net.minecraft.world.entity.Entity target) {
		if (!lookedForDeathFilm) {
			lookedForDeathFilm = true;
			if (FabricLoader.getInstance().isModLoaded("jaymospowers")) {
				try {
					deathFilm = java.lang.invoke.MethodHandles.publicLookup().findStatic(Class.forName("com.jaymospowers.core.UltimateFilm"), "afterDeath",
						java.lang.invoke.MethodType.methodType(void.class, net.minecraft.server.level.ServerPlayer.class, int.class, int.class,
							net.minecraft.world.phys.Vec3.class, net.minecraft.world.entity.Entity.class));
				} catch (ReflectiveOperationException | LinkageError e) {
					JaymosKingdoms.LOGGER.warn("Jaymo's Powers has no film after death (older Powers?)", e);
				}
			}
		}
		if (deathFilm == null) {
			return false;
		}
		try {
			deathFilm.invoke(dead, shot, ticks, target.getBoundingBox().getCenter(), target);
			return true;
		} catch (Throwable e) {
			JaymosKingdoms.LOGGER.warn("Couldn't play a film after death", e);
			return false;
		}
	}

	private static java.lang.invoke.@Nullable MethodHandle impactFrame;
	private static boolean lookedForImpact;

	/**
	 * (0.7.382) Two frames of black and white for every player within about 24 blocks of {@code at} (Powers'
	 * {@code ZFighterSenses.impactFrame}; each player's own setting can turn them off). Nothing without Powers 0.18.46 or later.
	 */
	public static void impactFrame(net.minecraft.server.level.ServerLevel level, net.minecraft.world.phys.Vec3 at) {
		if (!lookedForImpact) {
			lookedForImpact = true;
			if (FabricLoader.getInstance().isModLoaded("jaymospowers")) {
				try {
					impactFrame = java.lang.invoke.MethodHandles.publicLookup().findStatic(Class.forName("com.jaymospowers.power.zfighter.ZFighterSenses"),
						"impactFrame", java.lang.invoke.MethodType.methodType(void.class, net.minecraft.server.level.ServerLevel.class,
							net.minecraft.world.phys.Vec3.class));
				} catch (ReflectiveOperationException | LinkageError e) {
					JaymosKingdoms.LOGGER.debug("Jaymo's Powers has no public impact frames (older Powers?)", e);
				}
			}
		}
		try {
			if (impactFrame != null) {
				impactFrame.invoke(level, at);
			}
		} catch (Throwable ignored) {
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
	 * (0.7.223) Andy: "Lost in time starts you with chrono level 1". {@code player} awakens to Powers' power {@code power}
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
	 * (0.7.223) A life story's "lost in time" start: {@code player} wakes alone in Powers' future. False without Powers,
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

	/** (0.7.349) Powers' {@code ZFighterStarts.land(ServerPlayer, String, BlockPos)}. */
	private static java.lang.invoke.@Nullable MethodHandle land;
	private static boolean lookedForLand;

	/**
	 * (0.7.349) A life story's start on one of Powers' other worlds ({@code moon}, {@code verdant}, {@code rustfall},
	 * {@code maw}, {@code demon}, {@code afterlife}): {@code player} is put there, and what it needs is made. {@code back}:
	 * a spot in the overworld for one who'll come back to it (the afterlife's way home). False without Powers, or if the
	 * place couldn't be made.
	 */
	public static boolean otherWorld(net.minecraft.server.level.ServerPlayer player, String start, net.minecraft.core.BlockPos back) {
		if (!lookedForLand) {
			lookedForLand = true;
			if (FabricLoader.getInstance().isModLoaded("jaymospowers")) {
				try {
					land = java.lang.invoke.MethodHandles.publicLookup().findStatic(Class.forName("com.jaymospowers.power.zfighter.ZFighterStarts"), "land",
						java.lang.invoke.MethodType.methodType(boolean.class, net.minecraft.server.level.ServerPlayer.class, String.class, net.minecraft.core.BlockPos.class));
				} catch (ReflectiveOperationException | LinkageError e) {
					JaymosKingdoms.LOGGER.warn("Couldn't find Jaymo's Powers' other-world starts (older Powers?)", e);
				}
			}
		}
		if (land == null) {
			return false;
		}
		try {
			return (boolean)land.invoke(player, start, back);
		} catch (Throwable e) {
			JaymosKingdoms.LOGGER.warn("Couldn't send {} to the {} start", player.getGameProfile().name(), start, e);
			return false;
		}
	}

	/** (0.7.415) Powers' one-player start entries ({@code start(ServerPlayer)}), by class; null for one that isn't there. */
	private static final java.util.Map<String, java.lang.invoke.@Nullable MethodHandle> STARTS = new java.util.HashMap<>();

	private static boolean callStart(String className, net.minecraft.server.level.ServerPlayer player) {
		if (!STARTS.containsKey(className)) {
			java.lang.invoke.MethodHandle found = null;
			if (FabricLoader.getInstance().isModLoaded("jaymospowers")) {
				try {
					found = java.lang.invoke.MethodHandles.publicLookup().findStatic(Class.forName(className), "start",
						java.lang.invoke.MethodType.methodType(boolean.class, net.minecraft.server.level.ServerPlayer.class));
				} catch (ReflectiveOperationException | LinkageError e) {
					JaymosKingdoms.LOGGER.warn("Couldn't find {}.start in Jaymo's Powers (older Powers?)", className, e);
				}
			}
			STARTS.put(className, found);
		}
		java.lang.invoke.MethodHandle handle = STARTS.get(className);
		if (handle == null) {
			return false;
		}
		try {
			return (boolean)handle.invoke(player);
		} catch (Throwable e) {
			JaymosKingdoms.LOGGER.warn("Couldn't run {}.start for {}", className, player.getGameProfile().name(), e);
			return false;
		}
	}

	/**
	 * (0.7.415) Life story road "Escaped the alchemist's lab" (Powers' {@code LabEscape.start}): {@code player} wakes in a hermit
	 * alchemist's small lab built in the wild, with one infusion (a gift and a price, until a Potion of Cleansing or healing
	 * magic washes it out), and the alchemist's clockwork golems stir and hunt them. For the Bio-built, Rubber-folk, Emberborn,
	 * Stoneborn and Sylvan only (the caller gates it). False without Powers, or if no ground could be found.
	 */
	public static boolean labEscape(net.minecraft.server.level.ServerPlayer player) {
		return callStart("com.jaymospowers.alchemy.LabEscape", player);
	}

	/**
	 * (0.7.415) Life story road "Fresh off the bench" (Powers' {@code ZFighterAndroidStart.start}): {@code player} wakes on the Mad
	 * Tinkerer's bench in a small workshop built in the wild, already an android (endless ki, no hunger, metal repairs; given the
	 * Z Fighter's power if they have none), with a strange note in their pocket. False without Powers, or if no ground could be found.
	 */
	public static boolean androidStart(net.minecraft.server.level.ServerPlayer player) {
		return callStart("com.jaymospowers.power.zfighter.ZFighterAndroidStart", player);
	}

	/** (0.7.236) Powers' {@code Future.WAITING}: on a lost-in-time player still waiting for the future to be made. */
	private static net.fabricmc.fabric.api.attachment.v1.@Nullable AttachmentType<?> futureWaiting;
	private static boolean lookedForFutureWaiting;

	/** (0.7.236) {@code player} is held where they stand till Powers' future is made (lost in time; false without Powers). */
	public static boolean waitingForFuture(net.minecraft.server.level.ServerPlayer player) {
		if (!lookedForFutureWaiting) {
			lookedForFutureWaiting = true;
			if (FabricLoader.getInstance().isModLoaded("jaymospowers")) {
				try {
					futureWaiting = (net.fabricmc.fabric.api.attachment.v1.AttachmentType<?>)Class.forName("com.jaymospowers.power.chrono.future.Future")
						.getField("WAITING").get(null);
				} catch (ReflectiveOperationException | LinkageError | ClassCastException e) {
					JaymosKingdoms.LOGGER.warn("Couldn't find Jaymo's Powers' future wait (older Powers?)", e);
				}
			}
		}
		return futureWaiting != null && player.getAttached(futureWaiting) != null;
	}

	/** Powers' {@code Dismember.bornWithout(LivingEntity, int)}. */
	private static java.lang.invoke.@Nullable MethodHandle bornWithout;
	private static boolean lookedForBornWithout;

	/**
	 * A life story's "unarmed" start: {@code player} has long been without {@code part} (Powers' Dismember part bit: an
	 * arm or a leg), the stump healed over. False without Powers.
	 */
	public static boolean bornWithout(net.minecraft.world.entity.LivingEntity player, int part) {
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

	/** (0.7.262) Powers' {@code Dismember.regrow(LivingEntity, boolean)}. */
	private static java.lang.invoke.@Nullable MethodHandle regrow;
	private static boolean lookedForRegrow;

	/**
	 * (0.7.262) The Rubber-folk's body grows a lost limb back, as a Verdant's does in Powers: the first part still missing
	 * ({@code all}: every one). True if anything grew back; false without Powers.
	 */
	public static boolean regrow(LivingEntity body, boolean all) {
		if (!lookedForRegrow) {
			lookedForRegrow = true;
			if (FabricLoader.getInstance().isModLoaded("jaymospowers")) {
				try {
					regrow = java.lang.invoke.MethodHandles.publicLookup().findStatic(Class.forName("com.jaymospowers.core.Dismember"), "regrow",
						java.lang.invoke.MethodType.methodType(boolean.class, LivingEntity.class, boolean.class));
				} catch (ReflectiveOperationException | LinkageError e) {
					JaymosKingdoms.LOGGER.warn("Couldn't find Jaymo's Powers' limb regrowth (older Powers?)", e);
				}
			}
		}
		if (regrow == null) {
			return false;
		}
		try {
			return (boolean)regrow.invoke(body, all);
		} catch (Throwable e) {
			return false;
		}
	}

	/** (0.7.422) Powers' {@code Fractures.blunt(ItemStack)}. */
	private static java.lang.invoke.@Nullable MethodHandle blunt;
	private static boolean lookedForBlunt;

	/** (0.7.422) Whether {@code stack} is a blunt weapon by Powers' reckoning (a mace, hammer, club...); false without Powers. */
	public static boolean blunt(net.minecraft.world.item.ItemStack stack) {
		if (!lookedForBlunt) {
			lookedForBlunt = true;
			if (FabricLoader.getInstance().isModLoaded("jaymospowers")) {
				try {
					blunt = java.lang.invoke.MethodHandles.publicLookup().findStatic(Class.forName("com.jaymospowers.core.Fractures"), "blunt",
						java.lang.invoke.MethodType.methodType(boolean.class, net.minecraft.world.item.ItemStack.class));
				} catch (ReflectiveOperationException | LinkageError e) {
					JaymosKingdoms.LOGGER.warn("Couldn't find Jaymo's Powers' blunt weapons (older Powers?)", e);
				}
			}
		}
		if (blunt == null) {
			return stack.is(net.minecraft.world.item.Items.MACE);
		}
		try {
			return (boolean)blunt.invoke(stack);
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
		// (0.7.231) healing magic lifts a death blight first
		Necromancy.cleanse(entity);
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

	/** Eases {@code entity}'s fever (festering cured, its weakness, the dirt in its cuts); false without Powers. */
	public static boolean easeFever(net.minecraft.world.entity.LivingEntity entity) {
		mending();
		try {
			return easeFever != null && (boolean)easeFever.invoke(entity);
		} catch (Throwable e) {
			return false;
		}
	}

	private static java.lang.invoke.@Nullable MethodHandle washOut;
	private static boolean lookedForWashOut;

	/**
	 * (0.7.415) A deliberate cure (a cast spell, a healer's work; never a passive aura such as Renewal's pulse or a cleric's rounds):
	 * the moon's curse lets go, and so does the alchemist's mark (Powers 0.18.83). True if either was lifted.
	 */
	public static boolean cureCurses(net.minecraft.world.entity.LivingEntity entity) {
		boolean cured = entity instanceof net.minecraft.server.level.ServerPlayer player && MoonCurse.cure(player);
		if (!lookedForWashOut) {
			lookedForWashOut = true;
			if (FabricLoader.getInstance().isModLoaded("jaymospowers")) {
				try {
					washOut = java.lang.invoke.MethodHandles.publicLookup().findStatic(Class.forName("com.jaymospowers.core.wounds.Mending"), "washOut",
						java.lang.invoke.MethodType.methodType(boolean.class, net.minecraft.world.entity.LivingEntity.class));
				} catch (ReflectiveOperationException | LinkageError e) {
					JaymosKingdoms.LOGGER.warn("Couldn't find Jaymo's Powers' Mending.washOut (older Powers?)", e);
				}
			}
		}
		try {
			return washOut != null && (boolean)washOut.invoke(entity) || cured;
		} catch (Throwable e) {
			return cured;
		}
	}

	/** Restoration: bones set, festering cured, cuts closed, up if downed, and whole again (without Powers, full health). */
	public static void restore(net.minecraft.world.entity.LivingEntity entity) {
		Necromancy.cleanse(entity);
		// (0.7.415) and the moon's curse
		if (entity instanceof net.minecraft.server.level.ServerPlayer player) {
			MoonCurse.cure(player);
		}
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
	/** (0.7.227) A gift's ways (Powers 0.17.12): a master, open or hidden, and seen by a player. */
	private static java.lang.invoke.@Nullable MethodHandle giftGiveWays, giftWays, giftMaster, giftRevealed, giftCloseOnes, giftOpen, giftWitnesses;
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
			return;
		}
		try {
			Class<?> gifted = Class.forName("com.jaymospowers.core.gifted.Gifted");
			giftGiveWays = lookup.findStatic(gifted, "give",
				java.lang.invoke.MethodType.methodType(boolean.class, entity, String.class, int.class, boolean.class, boolean.class));
			giftWays = lookup.findStatic(gifted, "ways", java.lang.invoke.MethodType.methodType(boolean.class, entity, boolean.class, boolean.class));
			giftMaster = lookup.findStatic(gifted, "master", java.lang.invoke.MethodType.methodType(boolean.class, entity));
			giftRevealed = lookup.findStatic(gifted, "revealed", java.lang.invoke.MethodType.methodType(boolean.class, entity));
			giftCloseOnes = lookup.findStatic(gifted, "closeOnes", java.lang.invoke.MethodType.methodType(void.class, java.util.function.BiPredicate.class));
			giftOpen = lookup.findStatic(gifted, "open", java.lang.invoke.MethodType.methodType(boolean.class, entity));
			giftWitnesses = lookup.findStatic(gifted, "witnesses", java.lang.invoke.MethodType.methodType(void.class, java.util.function.BiConsumer.class));
		} catch (ReflectiveOperationException | LinkageError e) {
			giftGiveWays = null;
			JaymosKingdoms.LOGGER.debug("Jaymo's Powers has no masters or hidden gifts (older Powers?)", e);
		}
	}

	/**
	 * (0.7.227) As {@link #give}, as a {@code master} or not, and {@code open} (they don't hide it) or not; with an older
	 * Powers, as {@link #give}.
	 */
	public static boolean give(net.minecraft.world.entity.Entity entity, String power, int style, boolean master, boolean open) {
		lookForGifts();
		if (giftGiveWays == null) {
			return give(entity, power, style);
		}
		try {
			return (boolean)giftGiveWays.invoke(entity, power, style, master, open);
		} catch (Throwable e) {
			return false;
		}
	}

	/** (0.7.227) Sets the ways of {@code entity}'s gift (a master, open); false without one, or with an older Powers. */
	public static boolean giftWays(net.minecraft.world.entity.Entity entity, boolean master, boolean open) {
		lookForGifts();
		try {
			return giftGiveWays != null && (boolean)giftWays.invoke(entity, master, open);
		} catch (Throwable e) {
			return false;
		}
	}

	/** (0.7.227) Whether {@code entity} is a master of their gift. Either side. */
	public static boolean giftMaster(net.minecraft.world.entity.Entity entity) {
		lookForGifts();
		try {
			return giftGiveWays != null && (boolean)giftMaster.invoke(entity);
		} catch (Throwable e) {
			return false;
		}
	}

	/** (0.7.227) Whether players know of {@code entity}'s gift (open, or seen at work); always, with an older Powers. Either side. */
	public static boolean giftRevealed(net.minecraft.world.entity.Entity entity) {
		lookForGifts();
		try {
			return giftGiveWays == null || (boolean)giftRevealed.invoke(entity);
		} catch (Throwable e) {
			return true;
		}
	}

	/** (0.7.227) Whether {@code entity} doesn't hide their gift; false without one, or with an older Powers. */
	public static boolean giftOpen(net.minecraft.world.entity.Entity entity) {
		lookForGifts();
		try {
			return giftGiveWays != null && (boolean)giftOpen.invoke(entity);
		} catch (Throwable e) {
			return false;
		}
	}

	/** (0.7.227) Tells Powers to tell us {@code (player, gifted)} each time a player sees a gift at work ({@link GiftTales}). */
	public static void giftWitnesses(java.util.function.BiConsumer<net.minecraft.world.entity.Entity, net.minecraft.world.entity.Entity> told) {
		lookForGifts();
		try {
			if (giftGiveWays != null) {
				giftWitnesses.invoke(told);
			}
		} catch (Throwable e) {
			JaymosKingdoms.LOGGER.debug("couldn't ask Powers who sees gifts at work", e);
		}
	}

	/** (0.7.227) Tells Powers who a gifted villager hides their gift from ({@code test(gifted, other)}: other is close to them). */
	public static void giftCloseOnes(java.util.function.BiPredicate<net.minecraft.world.entity.Entity, net.minecraft.world.entity.Entity> test) {
		lookForGifts();
		try {
			if (giftGiveWays != null) {
				giftCloseOnes.invoke(test);
			}
		} catch (Throwable e) {
			JaymosKingdoms.LOGGER.debug("couldn't tell Powers who gifted villagers hide from", e);
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

	// ---- (0.7.231) blood magic: Powers' Hemomancy (cuts counted, opened, burst); forbidden magic's corruption ---------

	private static java.lang.invoke.@Nullable MethodHandle cuts, cut, hemorrhage, corruptionAdd;
	private static @Nullable Object lingering;
	private static boolean lookedForHemomancy;

	private static void hemomancy() {
		if (lookedForHemomancy) {
			return;
		}
		lookedForHemomancy = true;
		if (!FabricLoader.getInstance().isModLoaded("jaymospowers")) {
			return;
		}
		var lookup = java.lang.invoke.MethodHandles.publicLookup();
		Class<?> living = net.minecraft.world.entity.LivingEntity.class;
		try {
			Class<?> c = Class.forName("com.jaymospowers.core.wounds.Hemomancy");
			cuts = lookup.findStatic(c, "cuts", java.lang.invoke.MethodType.methodType(int.class, living));
			cut = lookup.findStatic(c, "cut", java.lang.invoke.MethodType.methodType(boolean.class, living));
			hemorrhage = lookup.findStatic(c, "hemorrhage", java.lang.invoke.MethodType.methodType(int.class, living,
				net.minecraft.world.entity.Entity.class, float.class));
		} catch (ReflectiveOperationException | LinkageError e) {
			JaymosKingdoms.LOGGER.warn("Couldn't find Jaymo's Powers' Hemomancy (older Powers?)", e);
		}
		try {
			Class<?> corruption = Class.forName("com.jaymospowers.alchemy.Corruption");
			Class<?> kind = Class.forName("com.jaymospowers.alchemy.Corruption$Kind");
			for (Object k : kind.getEnumConstants()) {
				if (((Enum<?>)k).name().equals("LINGERING")) {
					lingering = k;
				}
			}
			corruptionAdd = lookup.findStatic(corruption, "add", java.lang.invoke.MethodType.methodType(void.class,
				net.minecraft.server.level.ServerPlayer.class, kind, int.class));
		} catch (ReflectiveOperationException | LinkageError e) {
			JaymosKingdoms.LOGGER.debug("Jaymo's Powers has no alchemical corruption (older Powers?)", e);
		}
	}

	/** How many of {@code entity}'s parts bleed now (0 without Powers). */
	public static int cuts(net.minecraft.world.entity.LivingEntity entity) {
		hemomancy();
		try {
			return cuts == null ? 0 : (int)cuts.invoke(entity);
		} catch (Throwable e) {
			return 0;
		}
	}

	/** Opens a fresh cut on {@code entity}; false if none opened (or without Powers). */
	public static boolean cut(net.minecraft.world.entity.LivingEntity entity) {
		hemomancy();
		try {
			return cut != null && (boolean)cut.invoke(entity);
		} catch (Throwable e) {
			return false;
		}
	}

	/** Bursts {@code entity}'s open cuts, {@code times} bleed ticks each at once; how many burst (0 without Powers). */
	public static int hemorrhage(net.minecraft.world.entity.LivingEntity entity, net.minecraft.world.entity.@Nullable Entity by, float times) {
		hemomancy();
		try {
			return hemorrhage == null ? 0 : (int)hemorrhage.invoke(entity, by, times);
		} catch (Throwable e) {
			return 0;
		}
	}

	/** Lingering alchemical corruption for {@code player} (nothing without Powers' alchemy). */
	public static void corrupt(net.minecraft.server.level.ServerPlayer player, int amount) {
		hemomancy();
		if (corruptionAdd == null || lingering == null) {
			return;
		}
		try {
			corruptionAdd.invoke(player, lingering, amount);
		} catch (Throwable e) {
			// (no corruption, then)
		}
	}

	// ---- (0.7.236) mana: meditation, corruption, vis ------------------------------------------------------------------

	private static java.lang.invoke.@Nullable MethodHandle meditateTicks, corruptionOf, phialsCarried, essenceTotal;
	private static boolean lookedForMana;

	private static void lookForMana() {
		if (lookedForMana) {
			return;
		}
		lookedForMana = true;
		if (!FabricLoader.getInstance().isModLoaded("jaymospowers")) {
			return;
		}
		var lookup = java.lang.invoke.MethodHandles.publicLookup();
		try {
			meditateTicks = lookup.findStatic(Class.forName("com.jaymospowers.core.Meditate"), "ticks", java.lang.invoke.MethodType.methodType(long.class, Player.class));
		} catch (ReflectiveOperationException | LinkageError e) {
			JaymosKingdoms.LOGGER.debug("Jaymo's Powers has no Meditate (older Powers?)", e);
		}
		try {
			corruptionOf = lookup.findStatic(Class.forName("com.jaymospowers.alchemy.Corruption"), "total", java.lang.invoke.MethodType.methodType(int.class, Player.class));
			Class<?> list = Class.forName("com.jaymospowers.alchemy.EssenceList");
			phialsCarried = lookup.findStatic(Class.forName("com.jaymospowers.alchemy.Phials"), "carried", java.lang.invoke.MethodType.methodType(list, Player.class));
			essenceTotal = lookup.findVirtual(list, "total", java.lang.invoke.MethodType.methodType(int.class));
		} catch (ReflectiveOperationException | LinkageError e) {
			JaymosKingdoms.LOGGER.debug("Jaymo's Powers has no alchemy (older Powers?)", e);
		}
	}

	/** Whether {@code player} has settled into meditating (or shadowboxing) with Powers' Meditate key. */
	public static boolean meditating(Player player) {
		lookForMana();
		try {
			return meditateTicks != null && (long)meditateTicks.invoke(player) >= MEDITATE_SETTLE;
		} catch (Throwable e) {
			return false;
		}
	}

	/** Powers' Meditate.SETTLE: ticks of stillness before meditation counts. */
	static final long MEDITATE_SETTLE = 40L;

	/** {@code player}'s alchemical corruption, all kinds (0 without Powers; on a client, only its own player's is known). */
	public static int corruption(Player player) {
		lookForMana();
		try {
			return corruptionOf == null ? 0 : (int)corruptionOf.invoke(player);
		} catch (Throwable e) {
			return 0;
		}
	}

	/** How much alchemical essence (vis) {@code player} carries in phials (0 without Powers). */
	public static int visCarried(Player player) {
		lookForMana();
		if (phialsCarried == null || essenceTotal == null) {
			return 0;
		}
		try {
			return (int)essenceTotal.invoke(phialsCarried.invoke(player));
		} catch (Throwable e) {
			return 0;
		}
	}

	// ---- (0.7.237) spirits: finishers and screen shakes ---------------------------------------------------------------

	private static boolean lookedForSpirits;
	private static java.lang.invoke.@Nullable MethodHandle helpless, shake;

	private static void lookForSpirits() {
		if (lookedForSpirits) {
			return;
		}
		lookedForSpirits = true;
		if (!FabricLoader.getInstance().isModLoaded("jaymospowers")) {
			return;
		}
		java.lang.invoke.MethodHandles.Lookup lookup = java.lang.invoke.MethodHandles.publicLookup();
		try {
			helpless = lookup.findStatic(Class.forName("com.jaymospowers.core.finisher.Finishers"), "helpless",
				java.lang.invoke.MethodType.methodType(boolean.class, LivingEntity.class));
			shake = lookup.findStatic(Class.forName("com.jaymospowers.core.Shakes"), "send",
				java.lang.invoke.MethodType.methodType(void.class, net.minecraft.server.level.ServerPlayer.class, float.class));
		} catch (ReflectiveOperationException | LinkageError e) {
			JaymosKingdoms.LOGGER.debug("Jaymo's Powers has no finishers or shakes (older Powers?)", e);
		}
	}

	/** Whether {@code body} lies helpless for a finisher (downed, crippled): Powers' {@code Finishers.helpless}; false without Powers. */
	public static boolean helpless(LivingEntity body) {
		lookForSpirits();
		try {
			return helpless != null && (boolean)helpless.invoke(body);
		} catch (Throwable e) {
			return false;
		}
	}

	/** Shakes {@code player}'s screen with Powers' shake ({@code strength} about 0.3 a nudge, 1 a blow); nothing without Powers. */
	public static void shake(net.minecraft.server.level.ServerPlayer player, float strength) {
		lookForSpirits();
		try {
			if (shake != null) {
				shake.invoke(player, strength);
			}
		} catch (Throwable ignored) {
		}
	}

	/** (0.7.297) Powers' {@code SwallowedPower.take(ServerPlayer, LivingEntity)}. */
	private static java.lang.invoke.@Nullable MethodHandle takePower;
	private static boolean lookedForTake;

	/**
	 * (0.7.297) A Rubber-folk swallowed {@code body} (a player, or a gifted villager): {@code taker} takes on its power, in
	 * place of the last one they took. Its name, or null if it had none (or without Powers).
	 */
	public static net.minecraft.network.chat.@Nullable Component takePower(net.minecraft.server.level.ServerPlayer taker, LivingEntity body) {
		if (!lookedForTake) {
			lookedForTake = true;
			if (FabricLoader.getInstance().isModLoaded("jaymospowers")) {
				try {
					takePower = java.lang.invoke.MethodHandles.publicLookup().findStatic(Class.forName("com.jaymospowers.core.SwallowedPower"), "take",
						java.lang.invoke.MethodType.methodType(net.minecraft.network.chat.Component.class, net.minecraft.server.level.ServerPlayer.class, LivingEntity.class));
				} catch (ReflectiveOperationException | LinkageError e) {
					JaymosKingdoms.LOGGER.warn("Couldn't find Jaymo's Powers' swallowed powers (older Powers?)", e);
				}
			}
		}
		if (takePower == null) {
			return null;
		}
		try {
			return (net.minecraft.network.chat.Component)takePower.invoke(taker, body);
		} catch (Throwable e) {
			JaymosKingdoms.LOGGER.warn("Jaymo's Powers couldn't hand a swallowed power on", e);
			return null;
		}
	}

	/** (0.7.312) Powers' {@code SwallowedPower.drop(ServerPlayer)}. */
	private static java.lang.invoke.@Nullable MethodHandle dropPower;
	private static boolean lookedForDrop;

	/** (0.7.312) A Rubber-folk brought up their last swallow: the power they took from it leaves them (nothing without Powers 0.17.134). */
	public static void dropPower(net.minecraft.server.level.ServerPlayer player) {
		if (!lookedForDrop) {
			lookedForDrop = true;
			if (FabricLoader.getInstance().isModLoaded("jaymospowers")) {
				try {
					dropPower = java.lang.invoke.MethodHandles.publicLookup().findStatic(Class.forName("com.jaymospowers.core.SwallowedPower"), "drop",
						java.lang.invoke.MethodType.methodType(void.class, net.minecraft.server.level.ServerPlayer.class));
				} catch (ReflectiveOperationException | LinkageError e) {
					JaymosKingdoms.LOGGER.warn("Couldn't find Jaymo's Powers' SwallowedPower.drop (older Powers?)", e);
				}
			}
		}
		try {
			if (dropPower != null) {
				dropPower.invoke(player);
			}
		} catch (Throwable e) {
			JaymosKingdoms.LOGGER.warn("Jaymo's Powers couldn't take a swallowed power back", e);
		}
	}

	/**
	 * Tells {@code listener} of every forging finished on one of Powers' Smithing Anvils (the smith and the result), by adding
	 * it to Powers' {@code Provenance.FORGED_LISTENERS}. Nothing happens without Powers (or an older one).
	 */
	@SuppressWarnings("unchecked")
	public static void onForged(java.util.function.BiConsumer<net.minecraft.server.level.ServerPlayer, net.minecraft.world.item.ItemStack> listener) {
		if (!FabricLoader.getInstance().isModLoaded("jaymospowers")) {
			return;
		}
		try {
			Object list = Class.forName("com.jaymospowers.tinkering.smithing.Provenance").getField("FORGED_LISTENERS").get(null);
			((java.util.List<java.util.function.BiConsumer<net.minecraft.server.level.ServerPlayer, net.minecraft.world.item.ItemStack>>)list).add(listener);
		} catch (ReflectiveOperationException | LinkageError e) {
			JaymosKingdoms.LOGGER.warn("Couldn't find Jaymo's Powers' forging (older Powers?)", e);
		}
	}

	/**
	 * (0.7.383) Tells {@code listener} of every tool with iron (or better) in it taken from one of Powers' Tool Stations (the
	 * builder and the tool): Powers' {@code Tinkering.TOOL_BUILT_LISTENERS}. Nothing happens without Powers (or an older one).
	 */
	public static void onToolBuilt(java.util.function.BiConsumer<net.minecraft.server.level.ServerPlayer, net.minecraft.world.item.ItemStack> listener) {
		listen("com.jaymospowers.tinkering.Tinkering", "TOOL_BUILT_LISTENERS", listener);
	}

	/**
	 * (0.7.380) Adds {@code listener} to one of Powers' public listener lists ({@code owner}'s static {@code field}, a
	 * {@code List<BiConsumer<ServerPlayer, ?>>}): {@code Carry.PICKED_UP_LISTENERS} and the like ({@link Rescues}).
	 * Nothing happens without Powers (or an older one).
	 */
	@SuppressWarnings("unchecked")
	public static <T> void listen(String owner, String field, java.util.function.BiConsumer<net.minecraft.server.level.ServerPlayer, T> listener) {
		if (!FabricLoader.getInstance().isModLoaded("jaymospowers")) {
			return;
		}
		try {
			Object list = Class.forName(owner).getField(field).get(null);
			((java.util.List<java.util.function.BiConsumer<net.minecraft.server.level.ServerPlayer, T>>)list).add(listener);
		} catch (ReflectiveOperationException | LinkageError | ClassCastException e) {
			JaymosKingdoms.LOGGER.warn("Couldn't find Jaymo's Powers' {}.{} (older Powers?)", owner, field, e);
		}
	}

	/** (0.7.319) Powers' {@code ZFighterNpcs.talk(ServerPlayer, LivingEntity)}. */
	private static java.lang.invoke.@Nullable MethodHandle zfighterTalk;
	private static boolean lookedForZFighterTalk;

	/**
	 * (0.7.319) "Talk about work" (or "Talk about training") with one of Powers' Z Fighter characters ({@link ZFighterFolk}):
	 * their talk screen. False without Powers, or when they had nothing to say.
	 */
	public static boolean zfighterTalk(net.minecraft.server.level.ServerPlayer player, LivingEntity speaker) {
		if (!lookedForZFighterTalk) {
			lookedForZFighterTalk = true;
			if (FabricLoader.getInstance().isModLoaded("jaymospowers")) {
				try {
					zfighterTalk = java.lang.invoke.MethodHandles.publicLookup().findStatic(Class.forName("com.jaymospowers.power.zfighter.ZFighterNpcs"), "talk",
						java.lang.invoke.MethodType.methodType(boolean.class, net.minecraft.server.level.ServerPlayer.class, LivingEntity.class));
				} catch (ReflectiveOperationException | LinkageError e) {
					JaymosKingdoms.LOGGER.warn("Couldn't find Jaymo's Powers' Z Fighter talk (older Powers?)", e);
				}
			}
		}
		if (zfighterTalk == null) {
			return false;
		}
		try {
			return (boolean)zfighterTalk.invoke(player, speaker);
		} catch (Throwable e) {
			JaymosKingdoms.LOGGER.warn("Couldn't talk with {}", speaker.getName().getString(), e);
			return false;
		}
	}

	// ---- (0.7.342) Will (Will) --------------------------------------------------------------------------------------

	/** Powers' {@code Downed.knockOut} and {@code ZFighterScouter.powerLevel}. */
	private static java.lang.invoke.@Nullable MethodHandle knockOut, powerLevel;
	private static boolean lookedForWill;

	private static void lookForWill() {
		if (lookedForWill) {
			return;
		}
		lookedForWill = true;
		if (!FabricLoader.getInstance().isModLoaded("jaymospowers")) {
			return;
		}
		java.lang.invoke.MethodHandles.Lookup lookup = java.lang.invoke.MethodHandles.publicLookup();
		try {
			knockOut = lookup.findStatic(Class.forName("com.jaymospowers.core.wounds.Downed"), "knockOut", java.lang.invoke.MethodType.methodType(boolean.class,
				net.minecraft.server.level.ServerLevel.class, LivingEntity.class, int.class));
		} catch (ReflectiveOperationException | LinkageError e) {
			JaymosKingdoms.LOGGER.warn("Couldn't find Jaymo's Powers' knock-out (older Powers?)", e);
		}
		try {
			powerLevel = lookup.findStatic(Class.forName("com.jaymospowers.power.zfighter.ZFighterScouter"), "powerLevel",
				java.lang.invoke.MethodType.methodType(int.class, LivingEntity.class));
		} catch (ReflectiveOperationException | LinkageError e) {
			JaymosKingdoms.LOGGER.warn("Couldn't find Jaymo's Powers' power levels (older Powers?)", e);
		}
	}

	/** (0.7.342) Knocks {@code entity} out where it stands for {@code ticks}, by Powers' downing; false without Powers or if it can't be. */
	public static boolean knockOut(net.minecraft.server.level.ServerLevel level, LivingEntity entity, int ticks) {
		lookForWill();
		try {
			return knockOut != null && (boolean)knockOut.invoke(level, entity, ticks);
		} catch (Throwable e) {
			return false;
		}
	}

	/** (0.7.342) {@code entity}'s power level, as a Z Fighter's scouter reads it; 0 without Powers. */
	public static int powerLevel(LivingEntity entity) {
		lookForWill();
		try {
			return powerLevel == null ? 0 : (int)powerLevel.invoke(entity);
		} catch (Throwable e) {
			return 0;
		}
	}

	// ---- (0.7.381) Weapon Forms: poise and lock-on -------------------------------------------------------------------

	/** Powers' {@code ZFighterPoise.wear(LivingEntity, float)} and {@code knockedOut(LivingEntity)}. */
	private static java.lang.invoke.@Nullable MethodHandle poiseWear, poiseKnockedOut;
	private static boolean lookedForPoise;

	private static void lookForPoise() {
		if (lookedForPoise) {
			return;
		}
		lookedForPoise = true;
		if (!FabricLoader.getInstance().isModLoaded("jaymospowers")) {
			return;
		}
		java.lang.invoke.MethodHandles.Lookup lookup = java.lang.invoke.MethodHandles.publicLookup();
		try {
			Class<?> poise = Class.forName("com.jaymospowers.power.zfighter.ZFighterPoise");
			poiseWear = lookup.findStatic(poise, "wear", java.lang.invoke.MethodType.methodType(void.class, LivingEntity.class, float.class));
			poiseKnockedOut = lookup.findStatic(poise, "knockedOut", java.lang.invoke.MethodType.methodType(boolean.class, LivingEntity.class));
		} catch (ReflectiveOperationException | LinkageError e) {
			JaymosKingdoms.LOGGER.debug("Jaymo's Powers has no poise (older Powers?)", e);
		}
	}

	/** (0.7.381) Wears {@code amount} off {@code target}'s poise (a stagger when it breaks, a K.O. on a second break); false without Powers' poise. */
	public static boolean wearPoise(LivingEntity target, float amount) {
		lookForPoise();
		try {
			if (poiseWear != null) {
				poiseWear.invoke(target, amount);
				return true;
			}
		} catch (Throwable ignored) {
		}
		return false;
	}

	/** (0.7.381) Whether {@code target}'s poise is broken to a K.O. just now (it lies open to a masterstroke); false without Powers. */
	public static boolean poiseBroken(LivingEntity target) {
		lookForPoise();
		try {
			return poiseKnockedOut != null && (boolean)poiseKnockedOut.invoke(target);
		} catch (Throwable e) {
			return false;
		}
	}

	/** Powers' {@code LockOnClient}: its private {@code pick(Minecraft, LocalPlayer)} and {@code locked}/{@code blind} fields, and the {@code LockOn.Packet(int)} record. */
	private static java.lang.reflect.@Nullable Method lockPick;
	private static java.lang.reflect.@Nullable Field lockLocked, lockBlind;
	private static java.lang.reflect.@Nullable Constructor<?> lockPacket;
	private static boolean lookedForLock;

	private static void lookForLock() {
		if (lookedForLock) {
			return;
		}
		lookedForLock = true;
		if (!FabricLoader.getInstance().isModLoaded("jaymospowers")) {
			return;
		}
		try {
			Class<?> client = Class.forName("com.jaymospowers.client.LockOnClient");
			java.lang.reflect.Method pick = client.getDeclaredMethod("pick", Class.forName("net.minecraft.client.Minecraft"),
				Class.forName("net.minecraft.client.player.LocalPlayer"));
			java.lang.reflect.Field locked = client.getDeclaredField("locked");
			java.lang.reflect.Field blind = client.getDeclaredField("blind");
			java.lang.reflect.Constructor<?> packet = Class.forName("com.jaymospowers.core.LockOn$Packet").getConstructor(int.class);
			pick.setAccessible(true);
			locked.setAccessible(true);
			blind.setAccessible(true);
			lockPick = pick;
			lockLocked = locked;
			lockBlind = blind;
			lockPacket = packet;
		} catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
			JaymosKingdoms.LOGGER.debug("Couldn't reach Jaymo's Powers' lock-on (older Powers?)", e);
		}
	}

	/**
	 * (0.7.381) Client side, for the Battle Stance: locks Powers' lock-on onto the foe nearest the crosshair, if it isn't
	 * locked already. Takes the client ({@code Minecraft}) and its player as plain objects, since this class is common code.
	 * The packet to send the server ({@code LockOn.Packet}), or null if nothing was locked (no Powers, or nothing in sight).
	 */
	public static @Nullable CustomPacketPayload lockOnNearest(Object minecraft, Object player) {
		lookForLock();
		try {
			if (lockPick == null || lockLocked.getInt(null) >= 0 || !(lockPick.invoke(null, minecraft, player) instanceof LivingEntity foe)) {
				return null;
			}
			lockLocked.setInt(null, foe.getId());
			lockBlind.setInt(null, 0);
			return (CustomPacketPayload)lockPacket.newInstance(foe.getId());
		} catch (ReflectiveOperationException | RuntimeException e) {
			return null;
		}
	}

	/** (0.7.381) Client side: lets go of the lock {@link #lockOnNearest} took on {@code id}. The packet to send, or null if it isn't held any more. */
	public static @Nullable CustomPacketPayload lockOff(int id) {
		lookForLock();
		try {
			if (lockLocked == null || lockLocked.getInt(null) != id) {
				return null;
			}
			lockLocked.setInt(null, -1);
			lockBlind.setInt(null, 0);
			return (CustomPacketPayload)lockPacket.newInstance(-1);
		} catch (ReflectiveOperationException | RuntimeException e) {
			return null;
		}
	}

	/** (0.7.381) The id of the foe Powers' client has locked, or -1 (none, or without Powers). */
	public static int lockedId() {
		lookForLock();
		try {
			return lockLocked == null ? -1 : lockLocked.getInt(null);
		} catch (ReflectiveOperationException | RuntimeException e) {
			return -1;
		}
	}

	// ---- (0.7.390) Lock-on ----------------------------------------------------------------------------------------

	/** Powers' {@code LockOn.target(ServerPlayer)}. */
	private static java.lang.invoke.@Nullable MethodHandle lockTarget;
	private static boolean lookedForLockTarget;

	/** (0.7.390) Who {@code player} is locked onto with Powers' lock-on (middle mouse), or null (always without Powers). */
	public static @Nullable LivingEntity lockTarget(net.minecraft.server.level.ServerPlayer player) {
		if (!lookedForLockTarget) {
			lookedForLockTarget = true;
			if (FabricLoader.getInstance().isModLoaded("jaymospowers")) {
				try {
					lockTarget = java.lang.invoke.MethodHandles.publicLookup().findStatic(Class.forName("com.jaymospowers.core.LockOn"), "target",
						java.lang.invoke.MethodType.methodType(LivingEntity.class, net.minecraft.server.level.ServerPlayer.class));
				} catch (ReflectiveOperationException | LinkageError e) {
					JaymosKingdoms.LOGGER.warn("Couldn't find Jaymo's Powers' lock-on (older Powers?)", e);
				}
			}
		}
		try {
			return lockTarget == null ? null : (LivingEntity)lockTarget.invoke(player);
		} catch (Throwable e) {
			return null;
		}
	}

	/** Powers' {@code BloodTrail.lay(ServerLevel, UUID, int, double, double, double, float, int)}. */
	private static java.lang.invoke.@Nullable MethodHandle layStain;
	private static boolean lookedForStain;

	/** (0.7.390) A blood stain of {@code colour}, this big, on the ground under (x, from, z), as Powers' blood trail lays them; nothing without Powers. */
	public static void stain(net.minecraft.server.level.ServerLevel level, int colour, double x, double from, double z, float size) {
		if (!lookedForStain) {
			lookedForStain = true;
			if (FabricLoader.getInstance().isModLoaded("jaymospowers")) {
				try {
					Class<?> trail = Class.forName("com.jaymospowers.core.blood.BloodTrail");
					layStain = java.lang.invoke.MethodHandles.publicLookup().findStatic(trail, "lay",
						java.lang.invoke.MethodType.methodType(Class.forName("com.jaymospowers.core.blood.BloodTrail$Mark"), net.minecraft.server.level.ServerLevel.class,
							java.util.UUID.class, int.class, double.class, double.class, double.class, float.class, int.class));
				} catch (ReflectiveOperationException | LinkageError e) {
					JaymosKingdoms.LOGGER.warn("Couldn't find Jaymo's Powers' blood trail (older Powers?)", e);
				}
			}
		}
		if (layStain == null) {
			return;
		}
		try {
			layStain.invoke(level, (java.util.UUID) null, colour, x, from, z, size, 0);
		} catch (Throwable e) {
			// no stain
		}
	}
	// ---- (0.7.426) crippling blows: a broken leg ------------------------------------------------------------------------

	private static boolean lookedForBreak;
	/** Powers' {@code Wounds.breakPart(LivingEntity, BodyPart, Entity)} and its {@code BodyPart} legs. */
	private static java.lang.invoke.@Nullable MethodHandle breakPart;
	private static @Nullable Object leftLeg, rightLeg;

	/**
	 * (0.7.426) A crippling blow breaks one of {@code target}'s legs ({@code by} struck it). False without Powers (or an
	 * older one), if it has no legs to break, or that one's broken already.
	 */
	public static boolean breakLeg(LivingEntity target, net.minecraft.world.entity.@Nullable Entity by) {
		if (!lookedForBreak) {
			lookedForBreak = true;
			if (FabricLoader.getInstance().isModLoaded("jaymospowers")) {
				try {
					Class<?> part = Class.forName("com.jaymospowers.core.wounds.BodyPart");
					breakPart = java.lang.invoke.MethodHandles.publicLookup().findStatic(Class.forName("com.jaymospowers.core.wounds.Wounds"), "breakPart",
						java.lang.invoke.MethodType.methodType(boolean.class, LivingEntity.class, part, net.minecraft.world.entity.Entity.class));
					leftLeg = part.getField("LEFT_LEG").get(null);
					rightLeg = part.getField("RIGHT_LEG").get(null);
				} catch (ReflectiveOperationException | LinkageError e) {
					JaymosKingdoms.LOGGER.debug("Jaymo's Powers has no breakable legs (older Powers?)", e);
				}
			}
		}
		if (breakPart == null) {
			return false;
		}
		boolean left = target.getRandom().nextBoolean();
		try {
			return (boolean) breakPart.invoke(target, left ? leftLeg : rightLeg, by) || (boolean) breakPart.invoke(target, left ? rightLeg : leftLeg, by);
		} catch (Throwable e) {
			return false;
		}
	}

	// ---- (0.7.404) bolas: the trip ------------------------------------------------------------------------------------

	private static boolean lookedForTrip;
	/** Powers' {@code Tripped.bind(LivingEntity, Entity, int)}, {@code Tripped.tripped(Entity)} and {@code Tripped.trippable(Entity)}. */
	private static java.lang.invoke.@Nullable MethodHandle bindTrip, tripped, trippable;

	private static void lookForTrip() {
		if (lookedForTrip) {
			return;
		}
		lookedForTrip = true;
		if (!FabricLoader.getInstance().isModLoaded("jaymospowers")) {
			return;
		}
		java.lang.invoke.MethodHandles.Lookup lookup = java.lang.invoke.MethodHandles.publicLookup();
		try {
			Class<?> type = Class.forName("com.jaymospowers.core.wounds.Tripped");
			bindTrip = lookup.findStatic(type, "bind", java.lang.invoke.MethodType.methodType(boolean.class, LivingEntity.class,
				net.minecraft.world.entity.Entity.class, int.class));
			tripped = lookup.findStatic(type, "tripped", java.lang.invoke.MethodType.methodType(boolean.class, net.minecraft.world.entity.Entity.class));
			trippable = lookup.findStatic(type, "trippable", java.lang.invoke.MethodType.methodType(boolean.class, net.minecraft.world.entity.Entity.class));
		} catch (ReflectiveOperationException | LinkageError e) {
			JaymosKingdoms.LOGGER.debug("Jaymo's Powers has no bolas trip (older Powers?)", e);
		}
	}

	/**
	 * (0.7.404) A bolas wraps {@code target}'s legs ({@code by} threw it): Powers breaks a leg and trips it onto its face for
	 * {@code ticks}. False without Powers (or an older one), or if it isn't built like a person.
	 */
	public static boolean trip(LivingEntity target, net.minecraft.world.entity.@Nullable Entity by, int ticks) {
		lookForTrip();
		try {
			return bindTrip != null && (boolean)bindTrip.invoke(target, by, ticks);
		} catch (Throwable e) {
			return false;
		}
	}

	/** (0.7.404) Whether {@code body} is still down from a bolas (Powers' trip); false without Powers. */
	public static boolean tripped(net.minecraft.world.entity.Entity body) {
		lookForTrip();
		try {
			return tripped != null && (boolean)tripped.invoke(body);
		} catch (Throwable e) {
			return false;
		}
	}

	/** (0.7.404) Whether a bolas can trip {@code body} (a mob built like a person, on its feet); false without Powers. */
	public static boolean trippable(net.minecraft.world.entity.Entity body) {
		lookForTrip();
		try {
			return trippable != null && (boolean)trippable.invoke(body);
		} catch (Throwable e) {
			return false;
		}
	}

	// ---- (0.7.414) a forged blade (the Heirloom road) ----------------------------------------------------------------

	/** Powers' {@code ToolStats.build(ToolType, List<TinkerMaterial>)}, its {@code ToolType.SWORD}, and {@code TinkerMaterial.byId(String)}. */
	private static java.lang.invoke.@Nullable MethodHandle buildTool, materialById;
	private static @Nullable Object swordType;
	private static boolean lookedForBlades;

	/**
	 * (0.7.414) A Tinkers sword of Jaymo's Powers put together as its Tool Station does, from {@code head} (a metal's id, such as
	 * {@code "iron"} or {@code "steel"}) and {@code handle} (a wood's id) for the blade and both grips. Null without Powers (or an
	 * older one), or with materials it doesn't know: the caller falls back to a plain sword.
	 */
	public static net.minecraft.world.item.@Nullable ItemStack tinkerSword(String head, String handle) {
		if (!lookedForBlades) {
			lookedForBlades = true;
			if (FabricLoader.getInstance().isModLoaded("jaymospowers")) {
				try {
					Class<?> type = Class.forName("com.jaymospowers.tinkering.ToolType"), material = Class.forName("com.jaymospowers.tinkering.TinkerMaterial");
					java.lang.invoke.MethodHandles.Lookup lookup = java.lang.invoke.MethodHandles.publicLookup();
					swordType = type.getField("SWORD").get(null);
					materialById = lookup.findStatic(material, "byId", java.lang.invoke.MethodType.methodType(material, String.class));
					buildTool = lookup.findStatic(Class.forName("com.jaymospowers.tinkering.ToolStats"), "build",
						java.lang.invoke.MethodType.methodType(net.minecraft.world.item.ItemStack.class, type, java.util.List.class));
				} catch (ReflectiveOperationException | LinkageError e) {
					JaymosKingdoms.LOGGER.warn("Couldn't find Jaymo's Powers' tool building (older Powers?)", e);
				}
			}
		}
		if (buildTool == null || materialById == null || swordType == null) {
			return null;
		}
		try {
			Object blade = materialById.invoke(head), grip = materialById.invoke(handle);
			if (blade == null || grip == null) {
				return null;
			}
			return (net.minecraft.world.item.ItemStack)buildTool.invoke(swordType, java.util.List.of(blade, grip, grip));
		} catch (Throwable e) {
			JaymosKingdoms.LOGGER.warn("Couldn't build a Tinkers sword from {} and {}", head, handle, e);
			return null;
		}
	}

	private static java.lang.invoke.@Nullable MethodHandle questionDone;
	private static boolean lookedForQuestionDone;

	/**
	 * (0.7.424) Powers' {@code Finishers.questionDone(ServerPlayer, boolean)}: ends {@code player}'s questioning hold, the one
	 * held down let go or ({@code kill}) finished with the weapon's execution. Nothing without Powers.
	 */
	public static void questionDone(net.minecraft.server.level.ServerPlayer player, boolean kill) {
		if (!lookedForQuestionDone) {
			lookedForQuestionDone = true;
			if (FabricLoader.getInstance().isModLoaded("jaymospowers")) {
				try {
					questionDone = java.lang.invoke.MethodHandles.publicLookup().findStatic(Class.forName("com.jaymospowers.core.finisher.Finishers"),
						"questionDone", java.lang.invoke.MethodType.methodType(void.class, net.minecraft.server.level.ServerPlayer.class, boolean.class));
				} catch (ReflectiveOperationException | LinkageError e) {
					JaymosKingdoms.LOGGER.warn("Couldn't find Jaymo's Powers' questioning hold", e);
				}
			}
		}
		if (questionDone != null) {
			try {
				questionDone.invoke(player, kill);
			} catch (Throwable e) {
				JaymosKingdoms.LOGGER.warn("Couldn't end a questioning hold", e);
			}
		}
	}

	// ---- (0.7.462) the healing trade: Powers' wounds, for paid treatment, the infirmary cot and the shrine ------------------

	/** What ails a body: bit masks, a bit per body part, as Powers' wounds keep them. */
	public record Injuries(int broken, int bleeding, int festering) {
		public static final Injuries NONE = new Injuries(0, 0, 0);

		public boolean any() {
			return broken != 0 || bleeding != 0 || festering != 0;
		}

		/** Parts broken, cut open, and festering, added up. */
		public int count() {
			return Integer.bitCount(broken) + Integer.bitCount(bleeding) + Integer.bitCount(festering);
		}
	}

	private static java.lang.invoke.@Nullable MethodHandle brokenMask, bleedingMask, festeringMask, setAllBones, healPart, missingLimbs, limbBit, partValues;
	private static boolean lookedForHealing;

	private static void lookForHealing() {
		if (lookedForHealing) {
			return;
		}
		lookedForHealing = true;
		if (!FabricLoader.getInstance().isModLoaded("jaymospowers")) {
			return;
		}
		try {
			java.lang.invoke.MethodHandles.Lookup lookup = java.lang.invoke.MethodHandles.publicLookup();
			Class<?> entity = net.minecraft.world.entity.Entity.class;
			Class<?> living = net.minecraft.world.entity.LivingEntity.class;
			Class<?> wounds = Class.forName("com.jaymospowers.core.wounds.Wounds");
			Class<?> bleeding = Class.forName("com.jaymospowers.core.wounds.Bleeding");
			Class<?> breaks = Class.forName("com.jaymospowers.core.wounds.Breaks");
			Class<?> part = Class.forName("com.jaymospowers.core.wounds.BodyPart");
			Class<?> dismember = Class.forName("com.jaymospowers.core.Dismember");
			java.lang.invoke.MethodType mask = java.lang.invoke.MethodType.methodType(int.class, entity);
			brokenMask = lookup.findStatic(wounds, "broken", mask);
			bleedingMask = lookup.findStatic(bleeding, "bleeding", mask);
			festeringMask = lookup.findStatic(bleeding, "festering", mask);
			setAllBones = lookup.findStatic(wounds, "mendAll", java.lang.invoke.MethodType.methodType(boolean.class, living));
			healPart = lookup.findStatic(wounds, "healPart", java.lang.invoke.MethodType.methodType(float.class, living, part, float.class));
			missingLimbs = lookup.findStatic(dismember, "missing", mask);
			limbBit = lookup.findStatic(breaks, "limb", java.lang.invoke.MethodType.methodType(int.class, part));
			partValues = java.lang.invoke.MethodHandles.constant(Object[].class, part.getEnumConstants());
		} catch (ReflectiveOperationException | LinkageError e) {
			brokenMask = null;
			JaymosKingdoms.LOGGER.warn("Couldn't find Jaymo's Powers' wounds for healing (older Powers?)", e);
		}
	}

	/** (0.7.462) Which parts of {@code entity} are broken, cut open and festering (none without Powers). */
	public static Injuries injuries(net.minecraft.world.entity.LivingEntity entity) {
		lookForHealing();
		if (brokenMask == null || bleedingMask == null || festeringMask == null) {
			return Injuries.NONE;
		}
		try {
			return new Injuries((int)brokenMask.invoke((net.minecraft.world.entity.Entity)entity),
				(int)bleedingMask.invoke((net.minecraft.world.entity.Entity)entity), (int)festeringMask.invoke((net.minecraft.world.entity.Entity)entity));
		} catch (Throwable e) {
			return Injuries.NONE;
		}
	}

	/** (0.7.462) Sets every break {@code entity} has (Powers' {@code Wounds.mendAll}); false if there was none, or without Powers. */
	public static boolean setBones(net.minecraft.world.entity.LivingEntity entity) {
		lookForHealing();
		try {
			return setAllBones != null && (boolean)setAllBones.invoke(entity);
		} catch (Throwable e) {
			return false;
		}
	}

	/**
	 * (0.7.462) Heals every part {@code entity} still has by up to {@code amount} each (Powers' {@code Wounds.healPart}, no
	 * further than each may heal; a severed limb stays gone). Returns the total taken, or -1 without Powers.
	 */
	public static float healEveryPart(net.minecraft.world.entity.LivingEntity entity, float amount) {
		lookForHealing();
		if (healPart == null || missingLimbs == null || limbBit == null || partValues == null) {
			return -1.0F;
		}
		try {
			Object[] parts = (Object[])partValues.invoke();
			int gone = (int)missingLimbs.invoke((net.minecraft.world.entity.Entity)entity);
			float total = 0.0F;
			for (Object part : parts) {
				int limb = (int)limbBit.invoke(part);
				if (limb != 0 && (gone & limb) != 0) {
					continue;
				}
				total += (float)healPart.invoke(entity, part, amount);
			}
			return total;
		} catch (Throwable e) {
			return -1.0F;
		}
	}
}
