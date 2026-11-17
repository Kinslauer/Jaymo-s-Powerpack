package com.jaymospowers.compat;

import java.lang.reflect.Method;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/**
 * (0.20.9) Jaymo's Kingdoms is optional for Powers: this is the only place that touches it, by reflection, and only when it is
 * loaded. A missing jar (or a changed method) answers "no" instead of crashing.
 */
public final class KingdomsCompat {
	private static final boolean LOADED = FabricLoader.getInstance().isModLoaded("jaymoskingdoms");
	private static Method boss;
	private static boolean looked;
	private static Method blessing;
	private static boolean blessingLooked;
	private static Method grabbed;
	private static boolean grabbedLooked;
	private static Method fished;
	private static boolean fishedLooked;
	private static Method trainMight;
	private static boolean trainMightLooked;

	private static Method formsKnown, formsPerk;
	private static boolean formsLooked;

	private static Method knowledgeLevel;
	private static boolean knowledgeLooked;

	private static Method seesGhosts;
	private static boolean seesGhostsLooked;

	private KingdomsCompat() {
	}

	/** Kingdoms' static methods looked up by {@link #call}, by class, name and argument types (null: not there). */
	private static final java.util.Map<String, java.util.Optional<Method>> CALLS = new java.util.HashMap<>();

	/**
	 * Calls Kingdoms' public static {@code cls.method(args)} (types as given), or answers {@code otherwise} without Kingdoms, if it
	 * has no such method, or if the call throws. Used by the Awakening picks, so each needs no lookup of its own.
	 */
	public static @Nullable Object call(String cls, String method, Class<?>[] types, @Nullable Object otherwise, Object... args) {
		if (!LOADED) {
			return otherwise;
		}
		String key = cls + "#" + method + java.util.Arrays.toString(types);
		java.util.Optional<Method> m = CALLS.computeIfAbsent(key, k -> {
			try {
				return java.util.Optional.of(Class.forName("com.jaymoskingdoms." + cls).getMethod(method, types));
			} catch (ReflectiveOperationException | LinkageError e) {
				return java.util.Optional.empty();
			}
		});
		if (m.isEmpty()) {
			return otherwise;
		}
		try {
			return m.get().invoke(null, args);
		} catch (ReflectiveOperationException | RuntimeException e) {
			return otherwise;
		}
	}

	/** Lays {@code stacks} of the Ash's rot (Kingdoms' {@code AshRot.rot}) on {@code target}; false without Kingdoms. */
	public static boolean rot(LivingEntity target, @Nullable LivingEntity by, int stacks) {
		return call("AshRot", "rot", new Class<?>[] {LivingEntity.class, LivingEntity.class, int.class}, Boolean.FALSE, target, by, stacks) == null;
	}

	/**
	 * {@code player}'s Knowledge level (Kingdoms' boss ladder, {@code Knowledge.level}, 0 to 5; server side). Without
	 * Kingdoms (or if it can't be read) {@link Integer#MAX_VALUE}: there is nothing to earn, so nothing is held back.
	 */
	public static int knowledge(Player player) {
		if (!LOADED || player == null) {
			return Integer.MAX_VALUE;
		}
		if (!knowledgeLooked) {
			knowledgeLooked = true;
			try {
				knowledgeLevel = Class.forName("com.jaymoskingdoms.Knowledge").getMethod("level", Player.class);
			} catch (ReflectiveOperationException | LinkageError e) {
				knowledgeLevel = null;
			}
		}
		try {
			return knowledgeLevel != null && knowledgeLevel.invoke(null, player) instanceof Integer level ? level : Integer.MAX_VALUE;
		} catch (ReflectiveOperationException | RuntimeException e) {
			return Integer.MAX_VALUE;
		}
	}

	/**
	 * Whether {@code player} has Weapon Forms: a form learnt from any teacher (Kingdoms' {@code forms.Forms}), or the
	 * Strength perk that opens every weapon's full combo ({@code WeaponForms}). Both are synced to their owner, so this
	 * answers alike on the client (for yourself) and the server. True without Kingdoms: there is nothing to learn it from.
	 */
	public static boolean knowsForms(Player player) {
		if (!LOADED || player == null) {
			return true;
		}
		if (!formsLooked) {
			formsLooked = true;
			try {
				formsKnown = Class.forName("com.jaymoskingdoms.forms.Forms").getMethod("known", Player.class);
			} catch (ReflectiveOperationException | LinkageError e) {
				formsKnown = null;
			}
			try {
				formsPerk = Class.forName("com.jaymoskingdoms.WeaponForms").getMethod("knows", Player.class);
			} catch (ReflectiveOperationException | LinkageError e) {
				formsPerk = null;
			}
		}
		try {
			if (formsKnown != null && formsKnown.invoke(null, player) instanceof java.util.Collection<?> known && !known.isEmpty()) {
				return true;
			}
			return formsPerk != null && Boolean.TRUE.equals(formsPerk.invoke(null, player));
		} catch (ReflectiveOperationException | RuntimeException e) {
			return false;
		}
	}

	/**
	 * Whether {@code viewer} sees ghosts and hears them (Kingdoms' {@code Mana.seesGhosts}: the attuned and Death Sorcerers;
	 * server side). True without Kingdoms: there is no attunement to lack.
	 */
	public static boolean seesGhosts(Player viewer) {
		if (!LOADED || viewer == null) {
			return true;
		}
		if (!seesGhostsLooked) {
			seesGhostsLooked = true;
			try {
				seesGhosts = Class.forName("com.jaymoskingdoms.Mana").getMethod("seesGhosts", Player.class);
			} catch (ReflectiveOperationException | LinkageError e) {
				seesGhosts = null;
			}
		}
		try {
			return seesGhosts == null || Boolean.TRUE.equals(seesGhosts.invoke(null, viewer));
		} catch (ReflectiveOperationException | RuntimeException e) {
			return true;
		}
	}

	/** Whether Kingdoms calls {@code entity} a boss (one with a boss bar: a king, a lord, a warlord, an Aether Giant, a wild dragon). */
	public static boolean boss(Entity entity) {
		if (!LOADED) {
			return false;
		}
		if (!looked) {
			looked = true;
			try {
				boss = Class.forName("com.jaymoskingdoms.BossBars").getMethod("boss", Entity.class);
			} catch (ReflectiveOperationException | LinkageError e) {
				boss = null;
			}
		}
		if (boss == null) {
			return false;
		}
		try {
			return Boolean.TRUE.equals(boss.invoke(null, entity));
		} catch (ReflectiveOperationException | RuntimeException e) {
			return false;
		}
	}

	private static boolean campLooked;
	private static @Nullable Class<?> folkClass;
	private static @Nullable Method isBarbarian, isCampVillager, isOrc;

	private static void lookCamp() {
		if (campLooked) {
			return;
		}
		campLooked = true;
		try {
			Class<?> barbarians = Class.forName("com.jaymoskingdoms.Barbarians");
			folkClass = Class.forName("com.jaymoskingdoms.Folk");
			isBarbarian = barbarians.getMethod("isBarbarian", folkClass);
			isCampVillager = barbarians.getMethod("isCampVillager", Entity.class);
		} catch (ReflectiveOperationException | LinkageError e) {
			folkClass = null;
			isBarbarian = isCampVillager = null;
		}
		try {
			isOrc = Class.forName("com.jaymoskingdoms.Orcs").getMethod("isOrc", net.minecraft.world.entity.LivingEntity.class);
		} catch (ReflectiveOperationException | LinkageError e) {
			isOrc = null;
		}
	}

	/**
	 * (Elemental Corruption) Whether {@code entity} is one of a camp's own: a barbarian raider, chief or builder, or any
	 * camp's crew that is an MCA villager (a pirate cove's, a goblin village's, an orc camp's). False without Kingdoms.
	 */
	public static boolean barbarian(Entity entity) {
		if (!LOADED) {
			return false;
		}
		lookCamp();
		try {
			if (folkClass != null && isBarbarian != null && folkClass.isInstance(entity) && Boolean.TRUE.equals(isBarbarian.invoke(null, entity))) {
				return true;
			}
			return isCampVillager != null && Boolean.TRUE.equals(isCampVillager.invoke(null, entity));
		} catch (ReflectiveOperationException | RuntimeException e) {
			return false;
		}
	}

	/** (Elemental Corruption) Whether {@code entity} is one of an orc camp's orcs. False without Kingdoms. */
	public static boolean orc(Entity entity) {
		if (!LOADED || !(entity instanceof net.minecraft.world.entity.LivingEntity)) {
			return false;
		}
		lookCamp();
		try {
			return isOrc != null && Boolean.TRUE.equals(isOrc.invoke(null, entity));
		} catch (ReflectiveOperationException | RuntimeException e) {
			return false;
		}
	}

	private static boolean shrugLooked;
	private static Method shrugs;

	/**
	 * Whether {@code entity} is a boss that shrugs off this stun, hold or lockdown (Kingdoms' legendary resistances): one is
	 * spent and the effect must not take. False for anything else, and for a boss with none left, or without Kingdoms.
	 */
	public static boolean shrugs(Entity entity) {
		if (!LOADED || !(entity instanceof net.minecraft.world.entity.LivingEntity living)) {
			return false;
		}
		if (!shrugLooked) {
			shrugLooked = true;
			try {
				shrugs = Class.forName("com.jaymoskingdoms.Legendary").getMethod("shrugs", net.minecraft.world.entity.LivingEntity.class);
			} catch (ReflectiveOperationException | LinkageError e) {
				shrugs = null;
			}
		}
		if (shrugs == null) {
			return false;
		}
		try {
			return Boolean.TRUE.equals(shrugs.invoke(null, living));
		} catch (ReflectiveOperationException | RuntimeException e) {
			return false;
		}
	}

	/** (0.20.18) {@code player}'s sworn value for a Kingdoms key (negative: turned from that patron), 0 when unknown or Kingdoms is absent. */
	public static float blessing(Player player, String key) {
		if (!LOADED || player == null) {
			return 0.0F;
		}
		if (!blessingLooked) {
			blessingLooked = true;
			try {
				blessing = Class.forName("com.jaymoskingdoms.Patrons").getMethod("blessing", Player.class, String.class);
			} catch (ReflectiveOperationException | LinkageError e) {
				blessing = null;
			}
		}
		if (blessing == null) {
			return 0.0F;
		}
		try {
			return blessing.invoke(null, player, key) instanceof Number n ? n.floatValue() : 0.0F;
		} catch (ReflectiveOperationException | RuntimeException e) {
			return 0.0F;
		}
	}

	/**
	 * (0.21.33) Whether a vampire holds {@code entity} up by the throat now (Kingdoms' {@code BloodGrab}), on the client or the
	 * server: a held body that goes down stays in the hand, not laid on the ground ({@code DownedPose}). False without Kingdoms.
	 */
	public static boolean grabbed(Entity entity) {
		if (!LOADED || entity == null) {
			return false;
		}
		if (!grabbedLooked) {
			grabbedLooked = true;
			try {
				grabbed = Class.forName("com.jaymoskingdoms.BloodGrab").getMethod("grabbed", Entity.class);
			} catch (ReflectiveOperationException | LinkageError e) {
				grabbed = null;
			}
		}
		if (grabbed == null) {
			return false;
		}
		try {
			return Boolean.TRUE.equals(grabbed.invoke(null, entity));
		} catch (ReflectiveOperationException | RuntimeException e) {
			return false;
		}
	}

	/**
	 * (0.21.53) A great white shark (the Naturalist entity Kingdoms' whaling sharks use, see {@code Sharks}), brought up at
	 * {@code (x, y, z)} to be fought on the line; null without Kingdoms, without the shark, or in shallow water.
	 */
	public static @Nullable LivingEntity sharkDrawnIn(net.minecraft.server.level.ServerLevel level, double x, double y, double z) {
		if (!LOADED) {
			return null;
		}
		net.minecraft.world.entity.EntityType<?> sharkType =
				net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE
						.getOptional(net.minecraft.resources.Identifier.fromNamespaceAndPath("naturalist", "great_white_shark"))
						.orElse(null);
		if (sharkType == null) {
			return null;
		}
		// Needs water deep enough for a great white: the hook block and two below must be water.
		net.minecraft.core.BlockPos pos = net.minecraft.core.BlockPos.containing(x, y, z);
		if (!level.getFluidState(pos).is(net.minecraft.tags.FluidTags.WATER)
				|| !level.getFluidState(pos.below()).is(net.minecraft.tags.FluidTags.WATER)
				|| !level.getFluidState(pos.below().below()).is(net.minecraft.tags.FluidTags.WATER)) {
			return null;
		}
		net.minecraft.world.entity.Entity shark = sharkType.spawn(level, pos, net.minecraft.world.entity.EntitySpawnReason.EVENT);
		if (!(shark instanceof LivingEntity living)) {
			return null;
		}
		// held on the line, not hunting: no AI, no gravity, and no one else can kill it
		living.setPermanentlyInvulnerable(true);
		living.setNoGravity(true);
		if (living instanceof net.minecraft.world.entity.Mob mob) {
			mob.setNoAi(true);
			mob.setPersistenceRequired();
		}
		return living;
	}

	/**
	 * (0.21.61, Andy: "tideborn will see fishing as an attack, and doing in their havens is seen as a crime") A fish took
	 * {@code player}'s hook at {@code hook}: Kingdoms' {@code TideFishing} has any Tideborn who see it fight them, and calls it
	 * poaching in a Tideborn haven. Nothing without Kingdoms.
	 */
	public static void fished(net.minecraft.server.level.ServerPlayer player, net.minecraft.core.BlockPos hook) {
		if (!LOADED || player == null) {
			return;
		}
		if (!fishedLooked) {
			fishedLooked = true;
			try {
				fished = Class.forName("com.jaymoskingdoms.TideFishing").getMethod("fished",
					net.minecraft.server.level.ServerPlayer.class, net.minecraft.core.BlockPos.class);
			} catch (ReflectiveOperationException | LinkageError e) {
				fished = null;
			}
		}
		if (fished == null) {
			return;
		}
		try {
			fished.invoke(null, player, hook);
		} catch (ReflectiveOperationException | RuntimeException e) {
			// (an older Kingdoms, or one that threw: the fishing goes on)
		}
	}

	/**
	 * {@code points} of Might trained by {@code player} (shadowboxing in Training Weights, {@code core.Training}): Kingdoms'
	 * {@code Stats.trainMight} keeps them toward its Might attribute. Nothing without Kingdoms.
	 */
	public static void trainMight(net.minecraft.server.level.ServerPlayer player, double points) {
		if (!LOADED || player == null) {
			return;
		}
		if (!trainMightLooked) {
			trainMightLooked = true;
			try {
				trainMight = Class.forName("com.jaymoskingdoms.Stats").getMethod("trainMight", net.minecraft.server.level.ServerPlayer.class, double.class);
			} catch (ReflectiveOperationException | LinkageError e) {
				trainMight = null;
			}
		}
		if (trainMight == null) {
			return;
		}
		try {
			trainMight.invoke(null, player, points);
		} catch (ReflectiveOperationException | RuntimeException e) {
			// (an older Kingdoms, or one that threw: the shadowboxing goes on)
		}
	}

	private static Method temperOf, temperKey;
	private static boolean temperLooked;

	/**
	 * {@code entity}'s temperament key in Kingdoms ("timid", "brave", "hotheaded"...), or null without Kingdoms, for a
	 * creature that has none, or if the call fails. Kingdoms draws one for a humanoid of its own that has none yet.
	 */
	public static @Nullable String temperament(Entity entity) {
		if (!LOADED) {
			return null;
		}
		if (!temperLooked) {
			temperLooked = true;
			try {
				temperOf = Class.forName("com.jaymoskingdoms.Temperaments").getMethod("of", Entity.class);
				temperKey = Class.forName("com.jaymoskingdoms.Temperaments$Temperament").getMethod("key");
			} catch (ReflectiveOperationException | LinkageError e) {
				temperOf = temperKey = null;
			}
		}
		if (temperOf == null || temperKey == null) {
			return null;
		}
		try {
			Object t = temperOf.invoke(null, entity);
			return t == null ? null : (String)temperKey.invoke(t);
		} catch (ReflectiveOperationException | RuntimeException e) {
			return null;
		}
	}

	private static Method claimedBy;
	private static boolean claimLooked;

	/**
	 * Whether {@code pos} lies on land a Kingdoms faction holds (a claimed chunk of a standing kingdom: {@code Factions.at}).
	 * Things that eat or reshape the ground on their own (the sprouted lichen) leave claimed land alone. False without
	 * Kingdoms, or if the call fails: with nobody holding land there is nothing to protect.
	 */
	public static boolean claimed(net.minecraft.server.level.ServerLevel level, net.minecraft.core.BlockPos pos) {
		if (!LOADED || level == null) {
			return false;
		}
		if (!claimLooked) {
			claimLooked = true;
			try {
				claimedBy = Class.forName("com.jaymoskingdoms.Factions").getMethod("at", net.minecraft.server.MinecraftServer.class,
					net.minecraft.world.level.Level.class, net.minecraft.core.BlockPos.class);
			} catch (ReflectiveOperationException | LinkageError e) {
				claimedBy = null;
			}
		}
		if (claimedBy == null) {
			return false;
		}
		try {
			return claimedBy.invoke(null, level.getServer(), level, pos) != null;
		} catch (ReflectiveOperationException | RuntimeException e) {
			return false;
		}
	}

	private static Method blightNearest, blightEase;
	private static boolean blightLooked;

	private static void lookForBlight() {
		if (blightLooked) {
			return;
		}
		blightLooked = true;
		try {
			Class<?> blight = Class.forName("com.jaymoskingdoms.remnants.Blight");
			blightNearest = blight.getMethod("nearest", net.minecraft.server.level.ServerPlayer.class);
			blightEase = blight.getMethod("ease", net.minecraft.server.level.ServerPlayer.class);
		} catch (ReflectiveOperationException | LinkageError e) {
			blightNearest = blightEase = null;
		}
	}

	/**
	 * How far (blocks) {@code player} is from Kingdoms' blight: the Irradiated monolith or a changed wolf, whichever is
	 * nearer. {@link Double#MAX_VALUE} without Kingdoms, with none, or if the call fails. The ticking charm reads this.
	 */
	public static double blightDistance(net.minecraft.server.level.ServerPlayer player) {
		if (!LOADED) {
			return Double.MAX_VALUE;
		}
		lookForBlight();
		if (blightNearest == null) {
			return Double.MAX_VALUE;
		}
		try {
			return blightNearest.invoke(null, player) instanceof Double d ? d : Double.MAX_VALUE;
		} catch (ReflectiveOperationException | RuntimeException e) {
			return Double.MAX_VALUE;
		}
	}

	/** Honey or wild garlic taken: some of Kingdoms' blight sickness comes off. Nothing without Kingdoms. */
	public static void easeBlight(net.minecraft.server.level.ServerPlayer player) {
		if (!LOADED) {
			return;
		}
		lookForBlight();
		if (blightEase != null) {
			try {
				blightEase.invoke(null, player);
			} catch (ReflectiveOperationException | RuntimeException e) {
				// (nothing eased)
			}
		}
	}

	private static java.lang.invoke.@Nullable MethodHandle payLocal;
	private static boolean payLocalLooked;

	/**
	 * (Track D of docs/Plan-fishing.md) Pay {@code player} {@code milli} thousandths of an emerald for fish sold at {@code at},
	 * in the coin of the place (Kingdoms' {@code FisherTalk.pay}); what it came to, or null without Kingdoms (the caller pays
	 * emeralds).
	 */
	public static net.minecraft.network.chat.@Nullable Component payLocal(net.minecraft.server.level.ServerPlayer player,
		net.minecraft.core.BlockPos at, long milli) {
		if (!LOADED) {
			return null;
		}
		if (!payLocalLooked) {
			payLocalLooked = true;
			try {
				payLocal = java.lang.invoke.MethodHandles.publicLookup().findStatic(Class.forName("com.jaymoskingdoms.FisherTalk"), "pay",
					java.lang.invoke.MethodType.methodType(net.minecraft.network.chat.Component.class, net.minecraft.server.level.ServerPlayer.class,
						net.minecraft.core.BlockPos.class, long.class));
			} catch (ReflectiveOperationException | LinkageError e) {
				payLocal = null;
			}
		}
		if (payLocal == null) {
			return null;
		}
		try {
			return (net.minecraft.network.chat.Component) payLocal.invoke(player, at, milli);
		} catch (Throwable t) {
			return null;
		}
	}

	private static Method gunMan, gunManning, gunLeave, gunTick;
	private static boolean gunLooked;

	private static boolean guns() {
		if (!LOADED) {
			return false;
		}
		if (!gunLooked) {
			gunLooked = true;
			try {
				Class<?> c = Class.forName("com.jaymoskingdoms.sailing.MeldedGuns");
				Class<?> p = net.minecraft.server.level.ServerPlayer.class;
				gunMan = c.getMethod("man", p, double.class);
				gunManning = c.getMethod("manning", p);
				gunLeave = c.getMethod("leave", p);
				gunTick = c.getMethod("tick", p);
			} catch (ReflectiveOperationException | LinkageError e) {
				gunMan = null;
			}
		}
		return gunMan != null;
	}

	/**
	 * Rivet melds into the cannon {@code player} looks at within {@code reach} (Kingdoms' {@code MeldedGuns}, a gun on land or
	 * aboard a ship). False without Kingdoms or when there's no gun there.
	 */
	public static boolean meldGun(net.minecraft.server.level.ServerPlayer player, double reach) {
		try {
			return guns() && Boolean.TRUE.equals(gunMan.invoke(null, player, reach));
		} catch (ReflectiveOperationException | RuntimeException e) {
			return false;
		}
	}

	/** Whether Rivet is melded into a cannon. */
	public static boolean inGun(net.minecraft.server.level.ServerPlayer player) {
		try {
			return guns() && Boolean.TRUE.equals(gunManning.invoke(null, player));
		} catch (ReflectiveOperationException | RuntimeException e) {
			return false;
		}
	}

	/** Rivet pours out of the cannon he's melded into. */
	public static void leaveGun(net.minecraft.server.level.ServerPlayer player) {
		try {
			if (guns()) {
				gunLeave.invoke(null, player);
			}
		} catch (ReflectiveOperationException | RuntimeException e) {
			// (nothing to let go of)
		}
	}

	/** Every tick Rivet is changed: the cannon he's melded into is laid, fired and loaded. */
	public static void workGun(net.minecraft.server.level.ServerPlayer player) {
		try {
			if (guns()) {
				gunTick.invoke(null, player);
			}
		} catch (ReflectiveOperationException | RuntimeException e) {
			// (an older Kingdoms: no guns to meld into)
		}
	}

	private static Method treesHurry;
	private static boolean treesLooked;

	/**
	 * Chrono's Time Bloom (GT New Horizons picks: "bloom affects all of them"): Kingdoms' growing trees within {@code radius}
	 * of {@code centre} come {@code ticks} nearer their next step ({@code Trees.hurry}). Nothing without Kingdoms.
	 */
	public static void hurryTrees(net.minecraft.server.level.ServerLevel level, net.minecraft.core.BlockPos centre, double radius, long ticks) {
		if (!LOADED) {
			return;
		}
		if (!treesLooked) {
			treesLooked = true;
			try {
				treesHurry = Class.forName("com.jaymoskingdoms.Trees").getMethod("hurry", net.minecraft.server.level.ServerLevel.class,
					net.minecraft.core.BlockPos.class, double.class, long.class);
			} catch (ReflectiveOperationException | LinkageError e) {
				treesHurry = null;
			}
		}
		try {
			if (treesHurry != null) {
				treesHurry.invoke(null, level, centre, radius, ticks);
			}
		} catch (ReflectiveOperationException | RuntimeException e) {
			// (an older Kingdoms: its trees grow as they will)
		}
	}

	private static Method greatStarStruck;
	private static boolean greatStarLooked;

	/**
	 * (medieval picks, nd-meteor) A great falling star has struck at (x, y, z): Kingdoms' disasters take it from there (the
	 * ground shakes, the land burns, its dust hangs in the sky, the settlements near hear of it and remember;
	 * {@code disasters.Bolides.powersStar}). Nothing without Kingdoms.
	 */
	public static void greatStar(net.minecraft.server.level.ServerLevel level, double x, double y, double z, int magnitude, int radius) {
		if (!LOADED) {
			return;
		}
		if (!greatStarLooked) {
			greatStarLooked = true;
			try {
				greatStarStruck = Class.forName("com.jaymoskingdoms.disasters.Bolides").getMethod("powersStar", net.minecraft.server.level.ServerLevel.class,
					double.class, double.class, double.class, int.class, int.class);
			} catch (ReflectiveOperationException | LinkageError e) {
				greatStarStruck = null;
			}
		}
		try {
			if (greatStarStruck != null) {
				greatStarStruck.invoke(null, level, x, y, z, magnitude, radius);
			}
		} catch (ReflectiveOperationException | RuntimeException e) {
			// (an older Kingdoms: the crater is all there is)
		}
	}

	private static Method bellOut, bellMake, bellMade;
	private static boolean bellLooked;

	private static void lookBell() {
		if (bellLooked) {
			return;
		}
		bellLooked = true;
		try {
			Class<?> bell = Class.forName("com.jaymoskingdoms.disasters.LastBell");
			bellOut = bell.getMethod("isOut", net.minecraft.server.MinecraftServer.class);
			bellMake = bell.getMethod("make", net.minecraft.server.MinecraftServer.class);
			bellMade = bell.getMethod("made", net.minecraft.server.MinecraftServer.class);
		} catch (ReflectiveOperationException | LinkageError e) {
			bellOut = bellMake = bellMade = null;
		}
	}

	/** (medieval picks, nd-harbinger) Whether Kingdoms has the Last Bell to give. */
	public static boolean hasLastBell() {
		if (!LOADED) {
			return false;
		}
		lookBell();
		return bellOut != null && bellMake != null && bellMade != null;
	}

	/** (medieval picks, nd-harbinger) The Arsenal pulled a Last Bell through: it counts as out in the world now. */
	public static void lastBellMade(net.minecraft.server.MinecraftServer server) {
		if (!hasLastBell() || server == null) {
			return;
		}
		try {
			bellMade.invoke(null, server);
		} catch (ReflectiveOperationException | RuntimeException e) {
			// (it simply isn't counted)
		}
	}

	/** (medieval picks, nd-harbinger) Whether the Last Bell can be pulled through now: Kingdoms is here and none is out. */
	public static boolean lastBellFree(net.minecraft.server.MinecraftServer server) {
		if (!LOADED || server == null) {
			return false;
		}
		lookBell();
		try {
			return bellOut != null && bellMake != null && bellOut.invoke(null, server) instanceof Boolean out && !out;
		} catch (ReflectiveOperationException | RuntimeException e) {
			return false;
		}
	}

	/** (medieval picks, nd-harbinger) A new Last Bell, now out in the world (Kingdoms' {@code disasters.LastBell.make}); empty without Kingdoms. */
	public static net.minecraft.world.item.ItemStack newLastBell(net.minecraft.server.MinecraftServer server) {
		if (!LOADED || server == null) {
			return net.minecraft.world.item.ItemStack.EMPTY;
		}
		lookBell();
		try {
			return bellMake != null && bellMake.invoke(null, server) instanceof net.minecraft.world.item.ItemStack stack ? stack
				: net.minecraft.world.item.ItemStack.EMPTY;
		} catch (ReflectiveOperationException | RuntimeException e) {
			return net.minecraft.world.item.ItemStack.EMPTY;
		}
	}

	private static Method mimicTinkerer, mimicShop;
	private static boolean mimicLooked;

	private static void lookForMimic() {
		if (mimicLooked) {
			return;
		}
		mimicLooked = true;
		try {
			Class<?> bench = Class.forName("com.jaymoskingdoms.tinker.MimicBench");
			mimicTinkerer = bench.getMethod("tinkerer", net.minecraft.server.level.ServerPlayer.class);
			mimicShop = bench.getMethod("shop", net.minecraft.server.level.ServerPlayer.class, LivingEntity.class);
		} catch (ReflectiveOperationException | LinkageError e) {
			mimicTinkerer = null;
			mimicShop = null;
		}
	}

	/** Whether {@code player} is of Kingdoms' Tinkerer class ({@code MimicBench.tinkerer}): the Mad Tinkerer trades his gadgets only with them. */
	public static boolean tinkerer(net.minecraft.server.level.ServerPlayer player) {
		if (!LOADED) {
			return false;
		}
		lookForMimic();
		try {
			return mimicTinkerer != null && (boolean)mimicTinkerer.invoke(null, player);
		} catch (ReflectiveOperationException | RuntimeException e) {
			return false;
		}
	}

	/** Opens the Mad Tinkerer's gadget trade for a Tinkerer ({@code MimicBench.shop}); false if it didn't open. */
	public static boolean mimicShop(net.minecraft.server.level.ServerPlayer player, LivingEntity tinkerer) {
		if (!LOADED) {
			return false;
		}
		lookForMimic();
		try {
			return mimicShop != null && (boolean)mimicShop.invoke(null, player, tinkerer);
		} catch (ReflectiveOperationException | RuntimeException e) {
			return false;
		}
	}
}
