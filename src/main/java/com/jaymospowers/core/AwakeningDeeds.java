package com.jaymospowers.core;

import com.jaymospowers.alchemy.picks.GivingUp;
import com.jaymospowers.api.Power;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

/**
 * Deeds: one thing a powerless survival player can do that wakes the power they are already set to awaken, early and
 * all at once, next to the 3-5 day timer. A deed only ever wakes the power the player leans to ({@link #leaning}: a
 * power handed down, a calling's, or their affinity's); it never gives a second power, and does nothing for someone who
 * has one. A player left to fate (no leaning) is called by their first deed, as by a calling ({@link Callings#call}),
 * and it wakes at once. Felt, not read: no line says what
 * the deed was; the awakening plays as any other ({@link Powers#awakenAs}).
 * <ul>
 * <li>Wind: struck by lightning and live.</li>
 * <li>Gravity: a long fall survived ({@link #FALL_DAMAGE}+ points of it).</li>
 * <li>Cryokinesis: {@link #COLD_NIGHT} ticks frozen through the night, awake at dawn.</li>
 * <li>Darktide: nearly drowned at night ({@link #DROWN_HEALTH} health or less) and lived.</li>
 * <li>Smoke: {@link #SMOKE_SECONDS} s in the smoke of a big fire ({@link #BIG_FIRE} burning blocks round you).</li>
 * <li>Chrono: a clock held in the hand through {@link #CLOCK_NIGHT} ticks of a night spent awake, till dawn.</li>
 * <li>Pocket Arsenal: a monster killed while carrying {@link #ARSENAL_KINDS} different kinds of weapon.</li>
 * <li>Z Fighter: a hardy monster ({@link #HARDY} health or more) beaten bare-handed at {@link #HURT_HEALTH} health or less.</li>
 * <li>Web Slinger: bitten by a crater spider ({@code power/web/CraterSpider}).</li>
 * <li>Resonance: striking a Tuning Fork while blind, or after the blindfold's vigil ({@code power/blindness/Blind#forkAwakens};
 * already there, left as it is).</li>
 * <li>Xenoform: closing a Xeno-Band round the wrist ({@code power/xenoform/XenoBandItem}; already there, and the only way
 * in, since fate never hands Xenoform out).</li>
 * </ul>
 */
public final class AwakeningDeeds {
	/** Gravity: fall damage taken (points) in one landing, and lived. */
	public static final float FALL_DAMAGE = 15.0F;
	/** Cryokinesis: ticks frozen in a row through the dark (a night is ~10000). */
	public static final int COLD_NIGHT = 6000;
	/** Darktide: health left (points) by a drowning at night, at most. */
	public static final float DROWN_HEALTH = 4.0F;
	/** Smoke: burning blocks and lit campfires near you, and for how many seconds in a row. */
	public static final int BIG_FIRE = 8;
	public static final int SMOKE_SECONDS = 20;
	/** Chrono: dark ticks spent awake with a clock in hand. */
	public static final int CLOCK_NIGHT = 7000;
	/** Pocket Arsenal: kinds of weapon (sword, axe, spear, bow, crossbow, trident, mace) carried at the kill. */
	public static final int ARSENAL_KINDS = 5;
	/** Z Fighter: a foe's most health (points) that counts as hard, and the health (points) you must be at or under. */
	public static final float HARDY = 20.0F;
	public static final float HURT_HEALTH = 6.0F;

	/** Cryokinesis and Chrono: the ticks counted so far this night. */
	private static final Map<UUID, Integer> COLD = new HashMap<>();
	private static final Map<UUID, Integer> CLOCK = new HashMap<>();
	/** Smoke: seconds in a row in a big fire's smoke. */
	private static final Map<UUID, Integer> SMOKE = new HashMap<>();

	private AwakeningDeeds() {
	}

	/** The power this player is set to awaken: handed down, called, or their affinity's (the order {@link Powers#awaken} takes), or null. */
	public static @Nullable Power leaning(ServerPlayer player) {
		Power power = Affinity.inherited(player);
		if (power == null) {
			power = Callings.power(player);
		}
		return power != null ? power : Affinity.power(player);
	}

	/**
	 * A deed of {@code name}'s power was done: it awakens now, if it is the power {@code player} is set to awaken (and they
	 * are a living, powerless survival player). Returns whether it did.
	 */
	public static boolean wake(ServerPlayer player, String name) {
		Power power = Powers.byName(name);
		if (power == null || Powers.of(player) != null || player.isCreative() || player.isSpectator() || !player.isAlive()
			|| GivingUp.settled(player)) {
			return false;
		}
		Power leaning = leaning(player);
		if (leaning == null) {
			// left to fate (no affinity, calling or inheritance): the deed is their calling, and wakes it at once
			Callings.call(player, name, Integer.MAX_VALUE);
			return Powers.of(player) == power;
		}
		if (leaning != power) {
			return false;
		}
		player.removeAttached(Affinity.INHERITED);
		Powers.awakenAs(player, power);
		return Powers.of(player) == power;
	}

	static void register() {
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damageTaken, blocked) -> {
			if (!(entity instanceof ServerPlayer player) || !player.isAlive() || damageTaken <= 0.0F || Powers.of(player) != null) {
				return;
			}
			if (source.is(DamageTypes.LIGHTNING_BOLT)) {
				wake(player, "wind");
			} else if (source.is(DamageTypes.FALL) && damageTaken >= FALL_DAMAGE) {
				wake(player, "gravity");
			} else if (source.is(DamageTypes.DROWN) && player.getHealth() <= DROWN_HEALTH && player.level().isDarkOutside()) {
				wake(player, "darktide");
			}
		});
		ServerLivingEntityEvents.AFTER_DEATH.register(AwakeningDeeds::afterDeath);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> forget(handler.player.getUUID()));
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			COLD.clear();
			CLOCK.clear();
			SMOKE.clear();
		});
	}

	private static void forget(UUID id) {
		COLD.remove(id);
		CLOCK.remove(id);
		SMOKE.remove(id);
	}

	/** Pocket Arsenal (a kill with many kinds of weapon carried) and Z Fighter (a hard foe, bare-handed, while hurt). */
	private static void afterDeath(LivingEntity entity, DamageSource source) {
		if (!(source.getEntity() instanceof ServerPlayer player) || Powers.of(player) != null || !(entity instanceof Enemy)) {
			return;
		}
		if (player.getMainHandItem().isEmpty() && source.getDirectEntity() == player && source.is(DamageTypes.PLAYER_ATTACK)) {
			if (player.getHealth() <= HURT_HEALTH && entity.getAttributeValue(Attributes.MAX_HEALTH) >= HARDY) {
				wake(player, "zfighter");
			}
		} else if (weaponKinds(player.getInventory()) >= ARSENAL_KINDS) {
			wake(player, "arsenal");
		}
	}

	/** How many of the seven kinds of weapon are somewhere in {@code inventory}. */
	private static int weaponKinds(Inventory inventory) {
		boolean[] kinds = new boolean[7];
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack stack = inventory.getItem(i);
			if (stack.isEmpty()) {
				continue;
			}
			kinds[0] |= stack.is(ItemTags.SWORDS);
			kinds[1] |= stack.is(ItemTags.AXES);
			kinds[2] |= stack.is(ItemTags.SPEARS);
			kinds[3] |= stack.is(Items.BOW);
			kinds[4] |= stack.is(Items.CROSSBOW);
			kinds[5] |= stack.is(Items.TRIDENT);
			kinds[6] |= stack.is(Items.MACE);
		}
		int count = 0;
		for (boolean kind : kinds) {
			count += kind ? 1 : 0;
		}
		return count;
	}

	/** Every tick, for a powerless survival player: the deeds that take time (a night frozen, a night's watch, a fire's smoke). */
	static void tick(ServerPlayer player) {
		UUID id = player.getUUID();
		ServerLevel level = player.level();
		boolean dark = level.isDarkOutside();
		// Cryokinesis: frozen through the dark, awake at dawn
		if (dark) {
			if (player.getTicksFrozen() > 0) {
				COLD.merge(id, 1, Integer::sum);
			} else {
				COLD.remove(id);
			}
		} else {
			Integer cold = COLD.remove(id);
			if (cold != null && cold >= COLD_NIGHT) {
				wake(player, "cryo");
			}
		}
		// Chrono: awake all night with a clock in hand, till dawn
		if (dark) {
			if (!player.isSleeping() && (player.getMainHandItem().is(Items.CLOCK) || player.getOffhandItem().is(Items.CLOCK))) {
				CLOCK.merge(id, 1, Integer::sum);
			} else if (player.isSleeping()) {
				CLOCK.remove(id);
			}
		} else {
			Integer watched = CLOCK.remove(id);
			if (watched != null && watched >= CLOCK_NIGHT) {
				wake(player, "chrono");
			}
		}
		// Smoke: breathing a big fire's smoke
		if (player.tickCount % 20 == 0) {
			if (Callings.fires(level, player.blockPosition(), BIG_FIRE) >= BIG_FIRE) {
				if (SMOKE.merge(id, 1, Integer::sum) >= SMOKE_SECONDS) {
					SMOKE.remove(id);
					wake(player, "smoke");
				}
			} else {
				SMOKE.remove(id);
			}
		}
	}
}
