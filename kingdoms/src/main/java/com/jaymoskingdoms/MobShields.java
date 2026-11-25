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
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.component.BlocksAttacks;
import net.minecraft.world.item.component.ChargedProjectiles;
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
 * (0.7.267) With no shield, a fighter whose weapon has a guard (Jaymo's Powers gives every plain melee weapon one) raises
 * the blade instead, less often ({@link #BLADE_GUARD_CHANCE}) and only if it fights Better Combat's way ({@link MobCombat}).
 * A blow on it in the first ticks of that guard may be parried ({@link MobParry}).
 * <p>
 * Patch 3 of the fighting AI (shields and blows; the ideas are Hostile Humans', none of its code):
 * <ul>
 * <li><b>A shot on its way</b> ({@link #incoming}, from {@link ShieldSense}): the shield comes up and is held until the arrow,
 * bolt, javelin or ball has landed, if it can be up in time.</li>
 * <li><b>Watched</b> ({@link #watch}): someone in sight drawing a bow, holding a loaded crossbow or aiming a gun at it, its own foe
 * or not: up while its own foe isn't at hand.</li>
 * <li><b>Counter</b>: a raised block holds at least {@link #MIN_HOLD_TICKS}; a blow taken on it opens {@link #COUNTER_TICKS} in
 * which the shield comes down and its own blow comes first ({@link MobCombat#quicken}, {@link #countering}).</li>
 * <li><b>One thing at a time</b> ({@link #holdsFire}): no shot with the shield up. Held for a shot on its way, the shot waits;
 * up for anything else it comes down first. A crossbowman reloads behind it ({@link #cover}) and lowers it to fire.</li>
 * </ul>
 */
public final class MobShields {
	/** A foe within this (blocks) is close enough to raise the shield against; one drawing a bow, within this. */
	static final double GUARD_RANGE = 7.0, ARROW_RANGE = 28.0;
	/** Each time it could raise its shield, the chance it does (else it presses on for {@link #RETRY_TICKS}). */
	static final float GUARD_CHANCE = 0.65F;
	/** (0.7.267) The same for a raised blade, no shield. */
	static final float BLADE_GUARD_CHANCE = 0.3F;
	/** How long it holds the shield up (ticks, between these); and after lowering it, how long it fights before raising it again. */
	static final int GUARD_MIN_TICKS = 20, GUARD_MAX_TICKS = 50;
	static final int REST_MIN_TICKS = 20, REST_MAX_TICKS = 50;
	static final int RETRY_TICKS = 15;
	/** A drawn bow on it: the shield stays up at least this long (ticks). */
	static final int ARROW_GUARD_TICKS = 30;
	/** With no target left, a raised shield comes down after this long (ticks). */
	static final int IDLE_TICKS = 10;
	/** (sh-counter) A raised block stays up at least this long (ticks); a blow taken on it opens a window this long for its own. */
	static final int MIN_HOLD_TICKS = 6, COUNTER_TICKS = 10;
	/** (sh-busy) Lowered to shoot, the shot goes this long (ticks) after; for this long nothing but a shot on its way raises it again. */
	static final int LOWER_TICKS = 3, FIRE_TICKS = 6;
	/** (sh-busy) A crossbowman's shield comes up to reload behind this long (ticks) after its shot. */
	static final int COVER_DELAY = 4;

	/**
	 * Per mob: guarding until, resting until, knocked aside until, last seen fighting, raised at, held for a shot on its way
	 * until, held for a watching shooter (or a reload) until, lowered to shoot at, countering until, last shot (game time).
	 */
	private static final Map<Mob, long[]> STATE = new WeakHashMap<>();
	private static final int GUARD = 0, REST = 1, DISABLED = 2, SEEN = 3, RAISED = 4, HARD = 5, SOFT = 6, FIRED = 7, COUNTER = 8, SHOT = 9;
	/** Those with a shield held for a shot on its way: kept up every tick, whatever their own brain or goal does with their hands. */
	private static final Map<Mob, Boolean> HELD = new WeakHashMap<>();

	private MobShields() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(MobShields::tick);
		// (fighting AI patch 3) shots in the air and shooters in sight; jump crits and spacing by reach
		ShieldSense.register();
		Blows.register();
	}

	private static long[] state(Mob mob) {
		return STATE.computeIfAbsent(mob, m -> new long[] {0L, 0L, 0L, 0L, 0L, 0L, 0L, Long.MIN_VALUE / 2, 0L, Long.MIN_VALUE / 2});
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
		if (hand == InteractionHand.OFF_HAND) {
			ShieldSense.seen(mob);
		}
		long now = level.getGameTime();
		long[] s = state(mob);
		s[SEEN] = now;
		if (now < s[DISABLED] || MobCombat.swinging(mob)) {
			if (raised) {
				lower(mob, s, now);
			}
			return false;
		}
		// (sh-counter) a blow just taken on it: down once it has held its least, and its own blow comes first
		if (now < s[COUNTER]) {
			return raised && !lowerHeld(mob, s, now);
		}
		// (sh-arc) a shot on its way to it: up and held till it lands
		if (now < s[HARD]) {
			if (!raised) {
				raise(mob, hand, s, now, (int) (s[HARD] - now));
			}
			return true;
		}
		boolean aimedAt = aiming(target, mob) && mob.distanceToSqr(target) <= ARROW_RANGE * ARROW_RANGE;
		boolean near = mob.distanceToSqr(target) <= GUARD_RANGE * GUARD_RANGE;
		// (sh-shooter) someone else's bow or gun on it, while its own foe isn't at hand
		boolean watched = now < s[SOFT] && !near;
		if (!near && !aimedAt && !watched) {
			return raised && !lowerHeld(mob, s, now);
		}
		if (raised) {
			if (now < s[GUARD] || aimedAt || watched) {
				return true;
			}
			return !lowerHeld(mob, s, now);
		}
		if (aimedAt || watched) {
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

	/** (sh-counter) Lowers the raised guard unless it has been up less than {@link #MIN_HOLD_TICKS}; true if it came down. */
	private static boolean lowerHeld(Mob mob, long[] s, long now) {
		if (now - s[RAISED] < MIN_HOLD_TICKS) {
			return false;
		}
		lower(mob, s, now);
		return true;
	}

	/**
	 * (0.7.267) The hand it guards with: a shield in the off hand, else its main-hand weapon's own guard when it fights
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

	/** (0.7.267) How long its guard has been up (ticks), or -1 when it isn't ({@link MobParry}). */
	public static long raisedFor(Mob mob) {
		long[] s = STATE.get(mob);
		return s == null || !guarding(mob) ? -1L : mob.level().getGameTime() - s[RAISED];
	}

	/** (0.7.267) Lowers a raised guard now (a parry answered, {@link MobParry}). */
	static void drop(Mob mob) {
		long[] s = STATE.get(mob);
		if (s != null && guarding(mob)) {
			lower(mob, s, mob.level().getGameTime());
		}
	}

	/** (0.7.267) Snaps the guard up at once (a parry, {@link MobParry}), held this many ticks. */
	static void snap(Mob mob, int ticks) {
		InteractionHand hand = guardHand(mob);
		if (hand == null || !(mob.level() instanceof ServerLevel level)) {
			return;
		}
		long now = level.getGameTime();
		long[] s = state(mob);
		s[SEEN] = now;
		// (a shield an axe knocked aside stays down until it's back in hand, snapped or not)
		if (now >= s[DISABLED] && !guarding(mob)) {
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
		if (blocks == null) {
			return;
		}
		if (seconds > 0.0F) {
			disable(level, mob, seconds);
			return;
		}
		// (sh-counter) the blow stopped, its own comes first: the guard drops once it has held its least, and the next swing
		// starts at once and quick (MobCombat), the goal's wait is cut (MeleeAttackGoalMixin), a brain's cooldown forgotten
		long now = level.getGameTime();
		long[] s = state(mob);
		s[COUNTER] = now + COUNTER_TICKS;
		s[HARD] = Math.min(s[HARD], now);
		MobCombat.quicken(mob);
		MobCombat.hasten(mob);
		mob.getBrain().eraseMemory(MemoryModuleType.ATTACK_COOLING_DOWN);
	}

	/** (sh-counter) Whether a blow was just taken on its shield and its own may come at once. */
	public static boolean countering(Mob mob) {
		long[] s = STATE.get(mob);
		return s != null && mob.level().getGameTime() < s[COUNTER];
	}

	/** (0.7.245) Knocks {@code mob}'s raised shield aside for {@code seconds} (a spirit's axe, a guard break); false if it had none up. */
	public static boolean disable(ServerLevel level, Mob mob, float seconds) {
		ItemStack shield = mob.getUseItem();
		BlocksAttacks blocks = shield.get(DataComponents.BLOCKS_ATTACKS);
		if (blocks == null || !mob.isUsingItem()) {
			return false;
		}
		blocks.disable(level, mob, seconds, shield);
		long[] s = state(mob);
		s[DISABLED] = level.getGameTime() + Math.round(seconds * blocks.disableCooldownScale() * 20.0F);
		s[HARD] = Math.min(s[HARD], level.getGameTime());
		s[SOFT] = Math.min(s[SOFT], level.getGameTime());
		// (defence disciplines) a broken guard takes its passive blocks too
		MobBlocks.broken(mob);
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

	/**
	 * (sh-shooter) Whether {@code shooter} has a shot ready for {@code at}: a bow drawn, a loaded crossbow, a gun aimed (its red glint,
	 * {@link MobCombat#aiming}, or a loaded one held), a folk archer shooting at it ({@link Folk#shooting}). Where its aim points is
	 * {@link ShieldSense}'s to judge.
	 */
	static boolean aiming(LivingEntity shooter, Mob at) {
		if (aiming(shooter) || shooter instanceof Folk f && f.shooting() && f.getTarget() == at || shooter instanceof Mob m && MobCombat.aiming(m)) {
			return true;
		}
		ItemStack held = shooter.getMainHandItem();
		boolean gun = held.is(ClassPerks.GUNS) || EarlyGuns.Rung.of(held) != null;
		return gun && (shooter.isUsingItem() || !held.getOrDefault(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.EMPTY).isEmpty());
	}

	// ---- fighting AI patch 3: shields and blows -------------------------------------------------------------------------

	/** Whether {@code mob} can bring a real shield up now: one in its off hand, not knocked aside, not mid-swing, nothing else in use but a bow or crossbow. */
	static boolean canRaise(Mob mob) {
		if (!mob.isAlive() || mob.isPassenger() || mob.isSleeping() || Downed.isDowned(mob) || Arrests.busy(mob) || !mob.getOffhandItem().has(DataComponents.BLOCKS_ATTACKS)
			|| MobCombat.swinging(mob)) {
			return false;
		}
		long[] s = STATE.get(mob);
		if (s != null && mob.level().getGameTime() < s[DISABLED]) {
			return false;
		}
		return !mob.isUsingItem() || guarding(mob) || mob.getUseItem().getItem() instanceof ProjectileWeaponItem;
	}

	/**
	 * (sh-arc, from {@link ShieldSense}) A shot will hit {@code mob}'s front in {@code ticks}: if its shield can be up by then (the
	 * shield's own delay, {@code BlocksAttacks.blockDelayTicks}, and {@code spare} more), it comes up now (a draw or a reload is
	 * dropped for it) and is held till {@code after} ticks past the hit. True if it's up for it.
	 */
	static boolean incoming(Mob mob, double ticks, int spare, int after) {
		BlocksAttacks blocks = mob.getOffhandItem().get(DataComponents.BLOCKS_ATTACKS);
		if (blocks == null || !canRaise(mob)) {
			return false;
		}
		boolean up = guarding(mob) && mob.getUsedItemHand() == InteractionHand.OFF_HAND;
		int ready = up ? Math.max(0, blocks.blockDelayTicks() - mob.getTicksUsingItem()) : blocks.blockDelayTicks() + spare;
		if (ticks < ready) {
			return false;
		}
		long now = mob.level().getGameTime();
		long[] s = state(mob);
		s[HARD] = Math.max(s[HARD], now + Math.max(MIN_HOLD_TICKS, (long) Math.ceil(ticks) + after));
		s[SEEN] = now;
		if (!up) {
			if (mob.isUsingItem()) {
				mob.stopUsingItem();
			}
			raise(mob, InteractionHand.OFF_HAND, s, now, (int) (s[HARD] - now));
		}
		HELD.put(mob, Boolean.TRUE);
		return true;
	}

	/**
	 * (sh-shooter, and a crossbowman's reload) Held up while someone in sight aims at it, {@code ticks} more: raised now unless it is
	 * a bowman (a bow takes both hands), it's busy with its own shot, its own foe is at hand (the melee guard decides then), or it has
	 * just taken a blow on the shield.
	 */
	static void watch(Mob mob, int ticks) {
		if (!canRaise(mob) || mob.getMainHandItem().getItem() instanceof BowItem) {
			return;
		}
		long now = mob.level().getGameTime();
		long[] s = state(mob);
		if (now < s[FIRED] + FIRE_TICKS || now < s[COUNTER] || mob.isUsingItem() && !guarding(mob)) {
			return;
		}
		s[SOFT] = Math.max(s[SOFT], now + ticks);
		s[SEEN] = now;
		LivingEntity foe = Rulers.target(mob);
		if (guarding(mob) || foe != null && mob.distanceToSqr(foe) <= GUARD_RANGE * GUARD_RANGE) {
			return;
		}
		raise(mob, InteractionHand.OFF_HAND, s, now, ticks);
	}

	/**
	 * (sh-busy) A crossbowman with a shield reloads behind it: up between its shots, from {@link #COVER_DELAY} after the last
	 * ({@link #watch}'s rules), and lowered to fire ({@link #holdsFire}).
	 */
	static void cover(Mob mob, int ticks) {
		long[] s = STATE.get(mob);
		if (s != null && mob.level().getGameTime() - s[SHOT] < COVER_DELAY) {
			return;
		}
		watch(mob, ticks);
	}

	/**
	 * (sh-busy) Asked just before {@code mob} shoots or throws: true if the shot must wait. A shield held up for a shot on its way
	 * stays up (no shot). Up for anything else it comes down now: the shot then goes at once ({@code wait} false, for a brain or
	 * goal that tries its shot only once) or {@link #LOWER_TICKS} later ({@code wait} true, for those that ask again each tick).
	 */
	public static boolean holdsFire(Mob mob, boolean wait) {
		long[] s = STATE.get(mob);
		if (s == null) {
			return false;
		}
		long now = mob.level().getGameTime();
		if (now < s[HARD]) {
			return true;
		}
		if (guarding(mob) && mob.getUsedItemHand() == InteractionHand.OFF_HAND) {
			// a crossbowman lowers his shield to fire
			mob.stopUsingItem();
			s[SOFT] = Math.min(s[SOFT], now);
			s[FIRED] = now;
			if (wait) {
				return true;
			}
		} else if (wait && now < s[FIRED] + LOWER_TICKS) {
			return true;
		}
		s[SHOT] = now;
		return false;
	}

	/**
	 * A shield left up when the fight moved on (the goal stopped calling) comes down, once nothing holds it up; one held for a shot
	 * on its way is kept up every tick (its own brain or goal may have let it go to draw or swing).
	 */
	private static void tick(MinecraftServer server) {
		if (!HELD.isEmpty()) {
			for (Mob mob : new ArrayList<>(HELD.keySet())) {
				long[] s = mob == null ? null : STATE.get(mob);
				if (s == null || !mob.isAlive() || mob.level().getGameTime() >= s[HARD]) {
					HELD.remove(mob);
				} else if (!guarding(mob) && canRaise(mob)) {
					long now = mob.level().getGameTime();
					if (mob.isUsingItem()) {
						mob.stopUsingItem();
					}
					raise(mob, InteractionHand.OFF_HAND, s, now, (int) (s[HARD] - now));
				}
			}
		}
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
			if (now - s[SEEN] > IDLE_TICKS && now >= s[HARD] && now >= s[SOFT]) {
				if (guarding(mob) && now - s[RAISED] >= MIN_HOLD_TICKS) {
					mob.stopUsingItem();
				}
				if (now - s[SEEN] > 1200 && now > s[DISABLED]) {
					STATE.remove(mob);
				}
			}
		}
	}
}
