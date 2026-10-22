package com.jaymospowers.power.web;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/**
 * Jaymo's Kitchen (required since 0.8: the symbiote only feeds on corpses it carries): the only place that touches its
 * classes. Every call still checks it's loaded, so a missing jar fails soft instead of crashing.
 */
public final class KitchenCompat {
	private static final boolean LOADED = FabricLoader.getInstance().isModLoaded("jaymoskitchen");

	private KitchenCompat() {
	}

	static @Nullable Entity carriedCorpse(Player player) {
		return LOADED ? Kitchen.carried(player) : null;
	}

	/** Whether this player has a corpse in their arms (either side): their hands are full. */
	public static boolean isCarryingCorpse(Player player) {
		return carriedCorpse(player) != null;
	}

	/** A corpse drawn in by a power: pocketed if it fits, otherwise carried. */
	public static void take(Player player, Entity corpse) {
		if (LOADED) {
			Kitchen.take(player, corpse);
		}
	}

	/**
	 * A corpse taken up in your arms (never pocketed: the symbiote feeds on what you carry). Returns whether you're now
	 * carrying it. Kitchen's own {@code takeBy} pockets small corpses, so its arms-carry is reached directly.
	 */
	static boolean carry(Player player, Entity corpse) {
		return LOADED && !isHeavy(corpse) && Kitchen.carry(player, corpse);
	}

	/**
	 * Kitchen's heavy corpses (an iron golem's): only Gravity can lift one ({@link #take}); tendrils can't grab it and
	 * nothing butchers it.
	 */
	public static boolean isHeavy(Entity entity) {
		return LOADED && heavy(Kitchen.sourceType(entity));
	}

	/** A creature whose corpse would be too heavy to lift (also true for the living one: an iron golem). */
	static boolean heavy(net.minecraft.world.entity.@Nullable EntityType<?> type) {
		return type != null && type.builtInRegistryHolder().is(HEAVY_CORPSES);
	}

	private static final net.minecraft.tags.TagKey<net.minecraft.world.entity.EntityType<?>> HEAVY_CORPSES = net.minecraft.tags.TagKey.create(
		net.minecraft.core.registries.Registries.ENTITY_TYPE, net.minecraft.resources.Identifier.fromNamespaceAndPath("jaymoskitchen", "heavy_corpses"));

	/** Someone (anyone) is carrying this corpse right now. */
	static boolean isCarried(Entity entity) {
		return LOADED && Kitchen.carried(entity);
	}

	/** A player's corpse (it holds their belongings: never eaten). */
	static boolean isPlayerCorpse(Entity entity) {
		return LOADED && Kitchen.playerCorpse(entity);
	}

	/** (0.15.33) Players leave a body when they die (only with Jaymo's Kitchen): the symbiote hunts them for it. */
	static boolean playersLeaveCorpses() {
		return LOADED;
	}

	/** (0.15.33) Before a player's corpse is eaten: the hotbar it holds drops where it lies. */
	static void spill(Entity corpse) {
		if (LOADED) {
			Kitchen.spill(corpse);
		}
	}

	/** A dead player who got back up (Kitchen's zombie player): what a roaming symbiote bonds with. */
	static boolean isZombiePlayer(Entity entity) {
		return LOADED && Kitchen.zombiePlayer(entity);
	}

	/** Whether this creature leaves a corpse when it dies (without Kitchen: nothing does). */
	static boolean leavesCorpse(net.minecraft.world.entity.LivingEntity entity) {
		return LOADED && Kitchen.leavesCorpse(entity);
	}

	/** What a corpse was in life (null if it isn't one, or Jaymo's Kitchen isn't here). */
	static net.minecraft.world.entity.@Nullable EntityType<?> sourceType(Entity entity) {
		return LOADED ? Kitchen.sourceType(entity) : null;
	}

	/**
	 * For other powers (Darktide's Necromancy): what a corpse lying free was in life, or null if it isn't one, it's a
	 * player's (their belongings), someone is carrying it, or Jaymo's Kitchen isn't here.
	 */
	public static net.minecraft.world.entity.@Nullable EntityType<?> freeCorpseOf(Entity entity) {
		return LOADED && !Kitchen.playerCorpse(entity) && !Kitchen.carried(entity) ? Kitchen.sourceType(entity) : null;
	}

	/**
	 * (0.11.103) For Darktide's Necromancy: the corpse's look, the dead creature's whole saved data (its name, its face,
	 * its family, what it still wears), so a harvested villager can rise as itself. Null if it isn't a corpse.
	 */
	public static @Nullable String lookOf(Entity entity) {
		return LOADED ? Kitchen.look(entity) : null;
	}

	/** A corpse's limbs jerk violently (a hard jolt), if it is one. */
	static void jolt(Entity entity) {
		if (LOADED) {
			Kitchen.jolt(entity);
		}
	}

	/**
	 * A corpse torn apart where it lies (the symbiote's thrash): the same as butchering it without a butcher block, a
	 * rough job, about half its loot. Returns whether it was.
	 */
	static boolean butcherRough(Entity entity) {
		return LOADED && !isHeavy(entity) && Kitchen.butcherRough(entity);
	}

	/** (0.14.4) Wounds: how drunk a player is (Kitchen's {@code Drunk} points), 0 without Kitchen. */
	public static int drunk(Player player) {
		return LOADED ? Kitchen.drunk(player) : 0;
	}

	/** (0.14.4) Wounds: whether {@code stack} is Kitchen's strong spirits (poured on a wound, it cleans it). */
	public static boolean isStrongSpirits(net.minecraft.world.item.ItemStack stack) {
		return LOADED && Kitchen.spirits(stack);
	}

	/** Only touched when Jaymo's Kitchen is loaded. */
	private static final class Kitchen {
		static void endLegCrawl(Entity entity) {
			if (entity.hasAttached(com.jaymoskitchen.archery.LegShots.CRAWL)) {
				entity.removeAttached(com.jaymoskitchen.archery.LegShots.CRAWL);
			}
		}

		static int drunk(Player player) {
			return com.jaymoskitchen.brewing.Drunk.level(player);
		}

		static boolean spirits(net.minecraft.world.item.ItemStack stack) {
			return com.jaymoskitchen.brewing.Brewing.STRONG_SPIRITS != null && stack.is(com.jaymoskitchen.brewing.Brewing.STRONG_SPIRITS);
		}

		static @Nullable Entity carried(Player player) {
			return com.jaymoskitchen.corpse.CorpseEntity.carriedBy(player);
		}

		static void take(Player player, Entity entity) {
			if (entity instanceof com.jaymoskitchen.corpse.CorpseEntity corpse) {
				corpse.takeBy(player);
			}
		}

		private static java.lang.reflect.@Nullable Method carryMethod;
		private static boolean carryLooked;

		static boolean carry(Player player, Entity entity) {
			if (!(entity instanceof com.jaymoskitchen.corpse.CorpseEntity corpse) || !(player.level() instanceof net.minecraft.server.level.ServerLevel level)) {
				return false;
			}
			if (com.jaymoskitchen.corpse.CorpseEntity.carriedBy(player) == corpse) {
				return true;
			}
			if (!carryLooked) {
				carryLooked = true;
				try {
					carryMethod = com.jaymoskitchen.corpse.CorpseEntity.class.getDeclaredMethod("carry", net.minecraft.server.level.ServerLevel.class, Player.class);
					carryMethod.setAccessible(true);
				} catch (ReflectiveOperationException | RuntimeException e) {
					carryMethod = null;
				}
			}
			if (carryMethod != null) {
				try {
					carryMethod.invoke(corpse, level, player);
				} catch (ReflectiveOperationException | RuntimeException e) {
					corpse.takeBy(player);
				}
			} else {
				corpse.takeBy(player);
			}
			return com.jaymoskitchen.corpse.CorpseEntity.carriedBy(player) == corpse;
		}

		static boolean carried(Entity entity) {
			return entity instanceof com.jaymoskitchen.corpse.CorpseEntity corpse && corpse.carrier() != null;
		}

		static boolean playerCorpse(Entity entity) {
			return entity instanceof com.jaymoskitchen.corpse.CorpseEntity corpse && corpse.isPlayerCorpse();
		}

		private static java.lang.reflect.@Nullable Field hotbarField;
		private static boolean hotbarLooked;

		@SuppressWarnings("unchecked")
		static void spill(Entity entity) {
			if (!(entity instanceof com.jaymoskitchen.corpse.CorpseEntity corpse) || !corpse.isPlayerCorpse()
				|| !(entity.level() instanceof net.minecraft.server.level.ServerLevel level)) {
				return;
			}
			if (!hotbarLooked) {
				hotbarLooked = true;
				try {
					hotbarField = com.jaymoskitchen.corpse.CorpseEntity.class.getDeclaredField("hotbar");
					hotbarField.setAccessible(true);
				} catch (ReflectiveOperationException | RuntimeException e) {
					hotbarField = null;
				}
			}
			if (hotbarField == null) {
				return;
			}
			try {
				java.util.List<net.minecraft.world.item.ItemStack> hotbar = (java.util.List<net.minecraft.world.item.ItemStack>)hotbarField.get(corpse);
				for (int i = 0; i < hotbar.size(); i++) {
					net.minecraft.world.item.ItemStack stack = hotbar.get(i);
					if (!stack.isEmpty()) {
						corpse.spawnAtLocation(level, stack.copy(), 0.3F);
						hotbar.set(i, net.minecraft.world.item.ItemStack.EMPTY);
					}
				}
			} catch (ReflectiveOperationException | RuntimeException e) {
				// (left in the body: gone with it)
			}
		}

		static boolean zombiePlayer(Entity entity) {
			return entity instanceof com.jaymoskitchen.corpse.ZombiePlayer;
		}

		static boolean leavesCorpse(net.minecraft.world.entity.LivingEntity entity) {
			return com.jaymoskitchen.corpse.Corpses.leavesCorpse(entity);
		}

		static @Nullable String look(Entity entity) {
			return entity instanceof com.jaymoskitchen.corpse.CorpseEntity corpse && !corpse.isPlayerCorpse() ? corpse.look() : null;
		}

		static net.minecraft.world.entity.@Nullable EntityType<?> sourceType(Entity entity) {
			return entity instanceof com.jaymoskitchen.corpse.CorpseEntity corpse ? corpse.sourceType() : null;
		}

		private static java.lang.reflect.@Nullable Method butcherMethod;
		private static boolean butcherLooked;

		static boolean butcherRough(Entity entity) {
			if (!(entity instanceof com.jaymoskitchen.corpse.CorpseEntity corpse) || !(entity.level() instanceof net.minecraft.server.level.ServerLevel level)
				|| corpse.isPlayerCorpse()) {
				return false;
			}
			if (!butcherLooked) {
				butcherLooked = true;
				try {
					butcherMethod = com.jaymoskitchen.corpse.CorpseEntity.class.getDeclaredMethod("butcher", net.minecraft.server.level.ServerLevel.class, boolean.class);
					butcherMethod.setAccessible(true);
				} catch (ReflectiveOperationException | RuntimeException e) {
					butcherMethod = null;
				}
			}
			if (butcherMethod == null) {
				return false;
			}
			try {
				butcherMethod.invoke(corpse, level, false);
				return true;
			} catch (ReflectiveOperationException | RuntimeException e) {
				return false;
			}
		}

		static void jolt(Entity entity) {
			if (entity instanceof com.jaymoskitchen.corpse.CorpseEntity corpse) {
				corpse.jolt(true);
			}
		}
	}

	// ---- Kitchen's living ragdolls (0.5.1+), reached by reflection: this build compiles against an older Kitchen jar

	private static java.lang.reflect.@Nullable Method limpMethod;
	private static boolean limpLooked;

	/** Whether this entity is a player lying limp as a ragdoll (a crash, a blast...): false without a Kitchen that has them. */
	public static boolean isLimp(Entity entity) {
		if (!LOADED) {
			return false;
		}
		if (!limpLooked) {
			limpLooked = true;
			try {
				limpMethod = Class.forName("com.jaymoskitchen.ragdoll.LivingRagdolls").getMethod("isLimp", Entity.class);
			} catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
				limpMethod = null;
			}
		}
		if (limpMethod == null) {
			return false;
		}
		try {
			return Boolean.TRUE.equals(limpMethod.invoke(null, entity));
		} catch (ReflectiveOperationException | RuntimeException e) {
			return false;
		}
	}

	/**
	 * Powers that hold a player (being carried, Gravity's lift or hold, a web) keep them from going limp: Kitchen asks
	 * these before it knocks anyone down, and lets them up if one starts.
	 */
	public static void registerRagdollVetoes() {
		if (!LOADED) {
			return;
		}
		try {
			java.util.function.Predicate<Player> held = player -> com.jaymospowers.core.Carry.isCarried(player)
				|| player.getAttached(com.jaymospowers.power.gravity.GravityPower.HELD) != null
				|| player.getAttached(com.jaymospowers.power.gravity.GravityPower.LIFTED) != null
				|| player.getAttached(WebPower.HELD) != null || player.getAttached(WebPower.HITCH) != null
				// (0.15.54) a Wind user who takes to the air (catching a fall, or R from the ground past mastery 2)
				|| com.jaymospowers.power.wind.WindFall.holdsUp(player);
			Class.forName("com.jaymoskitchen.ragdoll.LivingRagdolls").getMethod("addVeto", java.util.function.Predicate.class)
				.invoke(null, held);
		} catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
			// an older Kitchen without living ragdolls: nothing to veto
		}
	}

	/**
	 * (0.15.89) A downed player in water ({@code Downed.adrift}) lies limp in Kitchen's ragdoll, floating, for as long
	 * as they're down and in it (Andy: "you should just ragdoll, and float").
	 */
	public static void registerRagdollHolds() {
		if (!LOADED) {
			return;
		}
		try {
			java.util.function.Predicate<Player> adrift = player -> com.jaymospowers.core.wounds.Downed.isDowned(player)
				&& com.jaymospowers.core.wounds.Downed.adrift(player);
			Class.forName("com.jaymoskitchen.ragdoll.LivingRagdolls").getMethod("addHold", java.util.function.Predicate.class)
				.invoke(null, adrift);
		} catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
			// an older Kitchen without holds: the keys still stop the swimming (DownedKeysMixin)
		}
	}

	/**
	 * (0.15.111) Andy: hitting a body on the ground splatters its blood, a blade most of all ({@code BladeBlood.corpse}).
	 * Kitchen (0.5.17 on) tells us of each blow that lands on a body with flesh on it.
	 */
	public static void registerCorpseBlood() {
		if (!LOADED) {
			return;
		}
		try {
			java.util.function.BiConsumer<Entity, java.util.Map.Entry<net.minecraft.world.damagesource.DamageSource, Float>> bleed =
				(corpse, blow) -> {
					net.minecraft.world.entity.EntityType<?> type = isPlayerCorpse(corpse) ? net.minecraft.world.entity.EntityTypes.PLAYER
						: sourceType(corpse);
					if (type != null) {
						com.jaymospowers.core.BladeBlood.corpse(corpse, type, blow.getKey(), blow.getValue());
					}
				};
			// (0.15.121) a heavy blunt blow smashes a dead spider to legs, as it would a live one (Kitchen 0.5.20 reads the tag)
			java.util.function.BiConsumer<Entity, java.util.Map.Entry<net.minecraft.world.damagesource.DamageSource, Float>> crush =
				(corpse, blow) -> {
					if (com.jaymospowers.core.BladeBlood.heavyBlunt(blow.getKey(), blow.getValue())) {
						corpse.addTag(com.jaymospowers.core.Crushing.TAG);
					}
				};
			java.lang.reflect.Method addOnHit = Class.forName("com.jaymoskitchen.corpse.CorpseEntity")
				.getMethod("addOnHit", java.util.function.BiConsumer.class);
			addOnHit.invoke(null, bleed);
			addOnHit.invoke(null, crush);
		} catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
			// an older Kitchen that doesn't say when a body is hit: the dead don't bleed
		}
	}

	/**
	 * (0.15.92) A downed mob lies as it's downed ({@code DownedPose}), not also as Kitchen's leg-shot crawl: that laid it
	 * face down a second time, so a crippled illager or skeleton stood on its head, sticking out of the ground. Its
	 * crawl is dropped while it's down (the limp it's left with stays).
	 */
	public static void endLegCrawl(Entity entity) {
		if (LOADED) {
			Kitchen.endLegCrawl(entity);
		}
	}

	/** Kitchen's hearty meals: a power that gives its holder more stomach slots (summed with any others). */
	public static void extraStomachSlots(java.util.function.ToIntFunction<Player> slots) {
		if (LOADED) {
			com.jaymoskitchen.stomach.Stomach.EXTRA_SLOTS.add(slots);
		}
	}
}
