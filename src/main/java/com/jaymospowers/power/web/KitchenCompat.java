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
		return LOADED && Kitchen.carry(player, corpse);
	}

	/** Someone (anyone) is carrying this corpse right now. */
	static boolean isCarried(Entity entity) {
		return LOADED && Kitchen.carried(entity);
	}

	/** A player's corpse (it holds their belongings: never eaten). */
	static boolean isPlayerCorpse(Entity entity) {
		return LOADED && Kitchen.playerCorpse(entity);
	}

	/** Whether this creature leaves a corpse when it dies (without Kitchen: nothing does). */
	static boolean leavesCorpse(net.minecraft.world.entity.LivingEntity entity) {
		return LOADED && Kitchen.leavesCorpse(entity);
	}

	/** What a corpse was in life (null if it isn't one, or Jaymo's Kitchen isn't here). */
	static net.minecraft.world.entity.@Nullable EntityType<?> sourceType(Entity entity) {
		return LOADED ? Kitchen.sourceType(entity) : null;
	}

	/** A corpse's limbs jerk violently (a hard jolt), if it is one. */
	static void jolt(Entity entity) {
		if (LOADED) {
			Kitchen.jolt(entity);
		}
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

		static boolean leavesCorpse(net.minecraft.world.entity.LivingEntity entity) {
			return com.jaymoskitchen.corpse.Corpses.leavesCorpse(entity);
		}

		static net.minecraft.world.entity.@Nullable EntityType<?> sourceType(Entity entity) {
			return entity instanceof com.jaymoskitchen.corpse.CorpseEntity corpse ? corpse.sourceType() : null;
		}

		static void jolt(Entity entity) {
			if (entity instanceof com.jaymoskitchen.corpse.CorpseEntity corpse) {
				corpse.jolt(true);
			}
		}
	}
}
