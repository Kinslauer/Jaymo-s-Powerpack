package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.Map;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.component.BlocksAttacks;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.105) Mobs raise their shields, after Just-In COMBAT's shield-bearing monsters (its idea only, our own code). A melee
 * mob with a shield in its off hand (a guard with the realm's arms, a tiered zombie from {@link MonsterGear}) holds it up
 * while it closes in and between blows, and lowers it to strike: vanilla's shield blocking then stops what comes at it
 * from the front, arrows too. It raises it at once when its target draws a bow on it. An axe (anything that disables a
 * shield) knocks it aside for as long as it would a player's, and a blow from behind still lands.
 * <p>
 * The goal's attack is held off while the shield is up ({@code MeleeAttackGoalMixin}); the axe in
 * {@code LivingEntityBlockMixin}; the raised arm on the client in {@code ShieldPoseHumanoidMobRendererMixin}.
 * <p>
 * (0.7.266) With no shield, a fighter whose weapon has a guard (Jaymo's Powers gives every plain melee weapon one) raises
 * the blade instead, less often ({@link #BLADE_GUARD_CHANCE}) and only if it fights Better Combat's way ({@link MobCombat}).
 * A blow on it in the first ticks of that guard may be parried ({@link MobParry}).
 */
public final class MobShields {
	/** A foe within this (blocks) is close enough to raise the shield against; one drawing a bow, within this. */
	static final double GUARD_RANGE = 7.0, ARROW_RANGE = 28.0;
	/** Each time it could raise its shield, the chance it does (else it presses on for {@link #RETRY_TICKS}). */
	static final float GUARD_CHANCE = 0.55F;
	/** (0.7.266) The same for a raised blade, no shield. */
	static final float BLADE_GUARD_CHANCE = 0.3F;
	/** How long it holds the shield up (ticks, between these); and after lowering it, how long it fights before raising it again. */
	static final int GUARD_MIN_TICKS = 20, GUARD_MAX_TICKS = 50;
	static final int REST_MIN_TICKS = 25, REST_MAX_TICKS = 60;
	static final int RETRY_TICKS = 15;
	/** A drawn bow on it: the shield stays up at least this long (ticks). */
	static final int ARROW_GUARD_TICKS = 30;
	/** With no target left, a raised shield comes down after this long (ticks). */
	static final int IDLE_TICKS = 10;

	/** Per mob: guarding until, resting until, knocked aside until, last seen fighting (game time). */
	private static final Map<Mob, long[]> STATE = new WeakHashMap<>();
	private static final int GUARD = 0, REST = 1, DISABLED = 2, SEEN = 3, RAISED = 4;

	private MobShields() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(MobShields::tick);
	}

	/**
	 * Called every tick its melee goal would attack ({@code MeleeAttackGoal.checkAndPerformAttack}): raises or lowers the
	 * shield. True while it's up, and the goal then doesn't strike this tick.
	 */
	public static boolean guard(Mob mob, LivingEntity target) {
		if (!(mob.level() instanceof ServerLevel level)) {
			return false;
		}
		InteractionHand hand = guardHand(mob);
		boolean raised = hand != null && mob.isUsingItem() && mob.getUsedItemHand() == hand;
		if (hand == null || mob.isPassenger() || Downed.isDowned(mob)) {
			if (raised) {
				mob.stopUsingItem();
			}
			return false;
		}
		long now = level.getGameTime();
		long[] s = STATE.computeIfAbsent(mob, m -> new long[5]);
		s[SEEN] = now;
		boolean aimedAt = aiming(target) && mob.distanceToSqr(target) <= ARROW_RANGE * ARROW_RANGE;
		boolean near = mob.distanceToSqr(target) <= GUARD_RANGE * GUARD_RANGE;
		if (now < s[DISABLED] || (!near && !aimedAt) || MobCombat.swinging(mob)) {
			if (raised) {
				lower(mob, s, now);
			}
			return false;
		}
		if (raised) {
			if (now < s[GUARD] || aimedAt) {
				return true;
			}
			lower(mob, s, now);
			return false;
		}
		if (aimedAt) {
			raise(mob, hand, s, now, ARROW_GUARD_TICKS);
			return true;
		}
		if (now < s[REST]) {
			return false;
		}
		if (mob.getRandom().nextFloat() >= (hand == InteractionHand.OFF_HAND ? GUARD_CHANCE : BLADE_GUARD_CHANCE)) {
			s[REST] = now + RETRY_TICKS;
			return false;
		}
		raise(mob, hand, s, now, GUARD_MIN_TICKS + mob.getRandom().nextInt(GUARD_MAX_TICKS - GUARD_MIN_TICKS + 1));
		return true;
	}

	/**
	 * (0.7.266) The hand it guards with: a shield in the off hand, else its main-hand weapon's own guard when it fights
	 * Better Combat's way and the off hand is free; null if neither.
	 */
	static @Nullable InteractionHand guardHand(Mob mob) {
		if (mob.getOffhandItem().has(DataComponents.BLOCKS_ATTACKS)) {
			return InteractionHand.OFF_HAND;
		}
		if (mob.getOffhandItem().isEmpty() && mob.getMainHandItem().has(DataComponents.BLOCKS_ATTACKS) && MobCombat.ENABLED && MobCombat.handles(mob)) {
			return InteractionHand.MAIN_HAND;
		}
		return null;
	}

	/** Whether {@code mob} has its shield (or blade) up right now (and so won't strike). */
	public static boolean guarding(Mob mob) {
		InteractionHand hand = guardHand(mob);
		return hand != null && mob.isUsingItem() && mob.getUsedItemHand() == hand;
	}

	/** (0.7.266) How long its guard has been up (ticks), or -1 when it isn't ({@link MobParry}). */
	public static long raisedFor(Mob mob) {
		long[] s = STATE.get(mob);
		return s == null || !guarding(mob) ? -1L : mob.level().getGameTime() - s[RAISED];
	}

	/** (0.7.266) Lowers a raised guard now (a parry answered, {@link MobParry}). */
	static void drop(Mob mob) {
		long[] s = STATE.get(mob);
		if (s != null && guarding(mob)) {
			lower(mob, s, mob.level().getGameTime());
		}
	}

	/** (0.7.266) Snaps the guard up at once (a parry, {@link MobParry}), held this many ticks. */
	static void snap(Mob mob, int ticks) {
		InteractionHand hand = guardHand(mob);
		if (hand == null || !(mob.level() instanceof ServerLevel level)) {
			return;
		}
		long now = level.getGameTime();
		long[] s = STATE.computeIfAbsent(mob, m -> new long[5]);
		s[SEEN] = now;
		if (!guarding(mob)) {
			raise(mob, hand, s, now, ticks);
		}
	}

	/**
	 * A blow taken on a mob's raised shield ({@code LivingEntity.blockUsingItem}): a weapon that disables shields (an
	 * axe) knocks it aside, as vanilla does to a player's.
	 */
	public static void blocked(ServerLevel level, Mob mob, LivingEntity attacker) {
		ItemStack shield = mob.getUseItem();
		BlocksAttacks blocks = shield.get(DataComponents.BLOCKS_ATTACKS);
		float seconds = attacker.getSecondsToDisableBlocking();
		if (blocks == null || seconds <= 0.0F) {
			return;
		}
		disable(level, mob, seconds);
	}

	/** (0.7.245) Knocks {@code mob}'s raised shield aside for {@code seconds} (a spirit's axe, a guard break); false if it had none up. */
	public static boolean disable(ServerLevel level, Mob mob, float seconds) {
		ItemStack shield = mob.getUseItem();
		BlocksAttacks blocks = shield.get(DataComponents.BLOCKS_ATTACKS);
		if (blocks == null || !mob.isUsingItem()) {
			return false;
		}
		blocks.disable(level, mob, seconds, shield);
		long[] s = STATE.computeIfAbsent(mob, m -> new long[5]);
		s[DISABLED] = level.getGameTime() + Math.round(seconds * blocks.disableCooldownScale() * 20.0F);
		return true;
	}

	private static void raise(Mob mob, InteractionHand hand, long[] s, long now, int ticks) {
		mob.startUsingItem(hand);
		s[GUARD] = now + ticks;
		s[RAISED] = now;
	}

	private static void lower(Mob mob, long[] s, long now) {
		mob.stopUsingItem();
		s[REST] = now + REST_MIN_TICKS + mob.getRandom().nextInt(REST_MAX_TICKS - REST_MIN_TICKS + 1);
	}

	/** Whether {@code target} is drawing a bow or holding a loaded crossbow. */
	static boolean aiming(LivingEntity target) {
		if (target.isUsingItem() && target.getUseItem().getItem() instanceof ProjectileWeaponItem) {
			return true;
		}
		for (InteractionHand hand : InteractionHand.values()) {
			ItemStack held = target.getItemInHand(hand);
			if (held.getItem() instanceof CrossbowItem && CrossbowItem.isCharged(held)) {
				return true;
			}
		}
		return false;
	}

	/** A shield left up when the fight moved on (the goal stopped calling): it comes down. */
	private static void tick(MinecraftServer server) {
		if (STATE.isEmpty() || server.getTickCount() % 5 != 0) {
			return;
		}
		for (Map.Entry<Mob, long[]> entry : new ArrayList<>(STATE.entrySet())) {
			Mob mob = entry.getKey();
			if (mob == null || !mob.isAlive()) {
				STATE.remove(mob);
				continue;
			}
			long now = mob.level().getGameTime();
			long[] s = entry.getValue();
			if (now - s[SEEN] > IDLE_TICKS) {
				if (guarding(mob)) {
					mob.stopUsingItem();
				}
				if (now - s[SEEN] > 1200 && now > s[DISABLED]) {
					STATE.remove(mob);
				}
			}
		}
	}
}
