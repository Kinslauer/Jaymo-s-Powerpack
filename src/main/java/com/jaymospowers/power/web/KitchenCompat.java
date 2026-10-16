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
	 * (0.11.102) For Darktide's Necromancy: the corpse's look, the dead creature's whole saved data (its name, its face,
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

	/** Only touched when Jaymo's Kitchen is loaded. */
	private static final class Kitchen {
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

	/** Kitchen's hearty meals: a power that gives its holder more stomach slots (summed with any others). */
	public static void extraStomachSlots(java.util.function.ToIntFunction<Player> slots) {
		if (LOADED) {
			com.jaymoskitchen.stomach.Stomach.EXTRA_SLOTS.add(slots);
		}
	}
}
