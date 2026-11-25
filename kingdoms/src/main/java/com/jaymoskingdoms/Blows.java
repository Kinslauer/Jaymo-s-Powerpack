package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.component.AttackRange;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Patch 3 of the fighting AI (shields and blows; the ideas are Hostile Humans', none of its code): how a fighter times and spaces
 * its blows. For the humanoid melee fighters {@link MobCombat} knows ({@link MobCombat#humanoid}), whoever runs them: Better
 * Combat's swings ({@link MobCombat#attackTick}), the game's melee goal ({@code MeleeAttackGoalMixin}) or MCA's melee task
 * ({@code McaMeleeSwingMixin}, {@link #brainStep}).
 * <ul>
 * <li><b>Jump crits</b> (ml-crit): with a heavy weapon ({@link #heavy}: an axe, a great sword, a hammer or mace) it now and then
 * ({@link #JUMP_CHANCE}, {@link #JUMP_COOLDOWN} apart) jumps so the blow lands as it comes down ({@link #JUMP_LEAD} ticks after
 * leaving the ground): a falling blow hits {@link #CRIT_DAMAGE} times as hard, with a critical's sparks and sound
 * ({@code MobBlowMixin}). A Better Combat swing plans the jump from its wind-up; the game's blow and MCA's wait for the fall.</li>
 * <li><b>Full charge</b>: any weapon swings no sooner than its full charge after the last swing ({@link #fullCharge}, a player's
 * attack-strength time), so quick weapons never spam.</li>
 * <li><b>Spacing by reach</b> (ml-reach): each fighter knows its weapon's reach ({@link #reach}: Better Combat's range, else one
 * by kind of weapon). After a blow, a foe left inside {@link #CLOSE_SHARE} of it is stepped back from, facing it, to
 * {@link #HOLD_SHARE} of it, for at most {@link #BACK_MOST} ticks and never off a drop or into lava; so spears and long blades
 * hold off and daggers stay close. Without Better Combat the game's melee goal also lets the blow land from that reach.</li>
 * </ul>
 * Never at a downed body, never from one making an arrest, falling back or otherwise busy ({@link Comrades#busy}).
 */
public final class Blows {
	/** ml-crit: the chance a heavy hitter times a jump for a blow, and the least time between two (ticks). */
	static final float JUMP_CHANCE = 0.3F;
	static final int JUMP_COOLDOWN = 60;
	/** Rolled no more often than this (ticks) where there's no wind-up to plan by. */
	static final int ROLL_GAP = 20;
	/** The blow lands this long after the jump (ticks: past the top, coming down); a wind-up shorter than the least gets no jump. */
	static final int JUMP_LEAD = 8, LEAST_LEAD = 6;
	/** A jump this recent (ticks) still counts for its blow. */
	static final int JUMP_WINDOW = 14;
	/** A little carry toward the foe on the jump (blocks a tick). */
	static final double JUMP_CARRY = 0.12;
	/** A falling blow's damage, as a player's critical. */
	static final float CRIT_DAMAGE = 1.5F;
	/** A charge never quicker than this (ticks), whatever the weapon. */
	static final int LEAST_CHARGE = 4;
	/** ml-reach: a foe this close (a share of its reach) after a blow is stepped back from, to this share; for at most this long (ticks), within this long of the blow. */
	static final double CLOSE_SHARE = 0.6, HOLD_SHARE = 0.85;
	static final int BACK_MOST = 12, BACK_WINDOW = 16;
	static final float BACK_SPEED = 1.0F;
	/** Without Better Combat: reach (blocks, from the eyes to the foe's body) by kind of weapon. */
	static final double REACH_SPEAR = 3.75, REACH_LONG = 3.25, REACH_SWORD = 3.0, REACH_HEAVY = 2.75, REACH_OTHER = 2.6, REACH_SHORT = 2.25;
	/** Without Better Combat the game's melee blow lands from this share of the reach (Better Combat's swings start there too). */
	static final double LAND_SHARE = MobCombat.START_REACH_SHARE;

	/** Better Combat's categories of heavy weapons, and the names of heavy weapons without it. */
	static final Set<String> HEAVY_CATEGORIES = Set.of("axe", "double_axe", "claymore", "hammer", "mace");
	static final String[] HEAVY_NAMES = {"hammer", "claymore", "greatsword", "great_sword", "greataxe", "great_axe", "battleaxe", "battle_axe", "maul"};
	/** Names of long and short weapons without Better Combat. */
	static final String[] LONG_NAMES = {"glaive", "halberd", "pike", "lance", "claymore", "greatsword", "great_sword", "zweihander"};
	static final String[] SHORT_NAMES = {"dagger", "knife", "dirk", "sai", "tanto"};

	private static final class State {
		long jumpedAt = Long.MIN_VALUE / 2, nextJump, nextRoll, lastBlow = Long.MIN_VALUE / 2, critAt = Long.MIN_VALUE / 2;
		/** A Better Combat swing's planned jump (game time; 0 none) and its foe. */
		long jumpAt;
		@Nullable LivingEntity jumpFoe;
		/** The blow a step back was taken after, and stepping back until. */
		long backedFor = Long.MIN_VALUE / 2, backUntil;
	}

	private static final Map<Mob, State> STATES = new WeakHashMap<>();

	private Blows() {
	}

	/** From {@link MobShields#register}. */
	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Blows::tick);
	}

	private static State state(Mob mob) {
		return STATES.computeIfAbsent(mob, m -> new State());
	}

	// ---- what it holds ---------------------------------------------------------------------------------------------------

	/** An axe, a great sword, a hammer or a mace: Better Combat's category if it knows the weapon, else the item's kind and name. */
	static boolean heavy(ItemStack weapon) {
		if (weapon.isEmpty()) {
			return false;
		}
		String category = MobCombat.ENABLED ? BetterCombatBridge.category(weapon) : null;
		if (category != null) {
			return HEAVY_CATEGORIES.contains(category);
		}
		return weapon.is(ItemTags.AXES) || weapon.is(Items.MACE) || named(weapon, HEAVY_NAMES);
	}

	private static boolean named(ItemStack weapon, String[] names) {
		String path = BuiltInRegistries.ITEM.getKey(weapon.getItem()).getPath();
		for (String name : names) {
			if (path.contains(name)) {
				return true;
			}
		}
		return false;
	}

	/** Its weapon's reach (blocks, eyes to the foe's body): Better Combat's range, else the item's own attack range, else by kind. */
	static double reach(Mob mob) {
		ItemStack weapon = mob.getMainHandItem();
		MobCombat.Moves moves = MobCombat.ENABLED ? BetterCombatBridge.moves(weapon) : null;
		if (moves != null) {
			return moves.reach();
		}
		AttackRange range = weapon.get(DataComponents.ATTACK_RANGE);
		if (range != null && range.effectiveMaxRange(mob) > REACH_SHORT) {
			return range.effectiveMaxRange(mob);
		}
		if (weapon.isEmpty() || named(weapon, SHORT_NAMES)) {
			return REACH_SHORT;
		}
		if (weapon.is(ItemTags.SPEARS) || weapon.is(Items.TRIDENT)) {
			return REACH_SPEAR;
		}
		if (named(weapon, LONG_NAMES)) {
			return REACH_LONG;
		}
		if (weapon.is(ItemTags.SWORDS)) {
			return REACH_SWORD;
		}
		return heavy(weapon) ? REACH_HEAVY : REACH_OTHER;
	}

	/** A full charge with its weapon (ticks): a player's attack-strength time, 20 over the attack speed. */
	static int fullCharge(Mob mob) {
		return Math.max(LEAST_CHARGE, (int) Math.ceil(20.0 / Math.max(0.5, MobCombat.attackSpeed(mob))));
	}

	/** A melee fighter this is for, at {@code foe}. */
	private static boolean fighter(Mob mob, @Nullable LivingEntity foe) {
		return foe != null && foe.isAlive() && !Downed.isDowned(foe) && mob.isAlive() && !mob.isPassenger() && !Downed.isDowned(mob)
			&& MobCombat.humanoid(mob) && !(mob.getMainHandItem().getItem() instanceof ProjectileWeaponItem) && !Comrades.busy(mob);
	}

	// ---- ml-crit ---------------------------------------------------------------------------------------------------------

	/** Whether it can jump for a blow now: a heavy weapon, on the ground, out of water, its last such jump long enough ago. */
	private static boolean canJump(Mob mob, State s, long now) {
		return heavy(mob.getMainHandItem()) && mob.onGround() && !mob.isInWater() && !mob.isInLava() && !mob.onClimbable() && now >= s.nextJump
			&& !mob.entityTags().contains(MobCombat.WAITING_TAG);
	}

	/** Up it goes, toward the foe a little. */
	private static void jump(Mob mob, State s, LivingEntity foe, long now) {
		s.jumpedAt = now;
		s.nextJump = now + JUMP_COOLDOWN;
		mob.getJumpControl().jump();
		Vec3 to = new Vec3(foe.getX() - mob.getX(), 0.0, foe.getZ() - mob.getZ());
		if (to.lengthSqr() > 1.0E-4) {
			mob.addDeltaMovement(to.normalize().scale(JUMP_CARRY));
		}
	}

	/**
	 * (from {@link MobCombat#attackTick}) A Better Combat swing has just started, its blow to land at {@code hitAt}: a heavy hitter
	 * may plan a jump {@link #JUMP_LEAD} before it.
	 */
	static void plan(Mob mob, LivingEntity foe, long hitAt, long now) {
		if (hitAt < 0 || !fighter(mob, foe)) {
			return;
		}
		State s = state(mob);
		long at = Math.max(now, hitAt - JUMP_LEAD);
		if (hitAt - at < LEAST_LEAD || !canJump(mob, s, now) || mob.getRandom().nextFloat() >= JUMP_CHANCE) {
			return;
		}
		s.jumpAt = at;
		s.jumpFoe = foe;
	}

	/**
	 * (the game's melee goal, MCA's melee task, with the blow ready and the foe in reach) Whether the blow waits: not yet a full charge
	 * since the last, or it is in the air from a jump for this blow and still going up. A heavy hitter may jump for it now (and
	 * the blow waits for the fall).
	 */
	public static boolean holds(Mob mob, LivingEntity foe) {
		if (!(mob.level() instanceof ServerLevel level) || !fighter(mob, foe)) {
			return false;
		}
		State s = state(mob);
		long now = level.getGameTime();
		if (now - s.lastBlow < fullCharge(mob)) {
			return true;
		}
		if (now - s.jumpedAt <= JUMP_WINDOW) {
			// up from a jump for this blow: it comes as it falls
			return !mob.onGround() && mob.getDeltaMovement().y >= 0.0;
		}
		if (now >= s.nextRoll && canJump(mob, s, now)) {
			s.nextRoll = now + ROLL_GAP;
			if (mob.getRandom().nextFloat() < JUMP_CHANCE) {
				jump(mob, s, foe, now);
				return true;
			}
		}
		return false;
	}

	/** Whether {@code mob}'s blow now is a falling one from a jump for it. */
	private static boolean falling(Mob mob, State s, long now) {
		return now - s.jumpedAt <= JUMP_WINDOW && (s.critAt < s.jumpedAt || s.critAt == now) && !mob.onGround() && mob.getDeltaMovement().y < 0.0
			&& mob.fallDistance > 0.0 && !mob.isInWater() && !mob.onClimbable() && !mob.hasEffect(MobEffects.BLINDNESS) && heavy(mob.getMainHandItem());
	}

	/** (from {@code MobBlowMixin}, as {@code Mob.doHurtTarget} strikes) The blow's damage: a falling one is a critical. */
	public static float dealt(Mob mob, float damage) {
		if (!(mob.level() instanceof ServerLevel level)) {
			return damage;
		}
		long now = level.getGameTime();
		State s = STATES.get(mob);
		if (s == null) {
			if (!MobCombat.humanoid(mob)) {
				return damage;
			}
			s = state(mob);
		}
		s.lastBlow = now;
		if (!falling(mob, s, now)) {
			return damage;
		}
		s.critAt = now;
		Vec3 at = mob.getEyePosition().add(Vec3.directionFromRotation(0.0F, mob.getYHeadRot()).scale(1.0)).add(0.0, -0.4, 0.0);
		level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 12, 0.3, 0.3, 0.3, 0.3);
		level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.PLAYER_ATTACK_CRIT, mob.getSoundSource(), 1.0F, 0.9F);
		return damage * CRIT_DAMAGE;
	}

	/** (from {@link MobCombat}) A swing's blow has fallen, landed or not. */
	static void blown(Mob mob) {
		state(mob).lastBlow = mob.level().getGameTime();
	}

	// ---- ml-reach --------------------------------------------------------------------------------------------------------

	/**
	 * (the game's melee goal, without Better Combat) Whether {@code foe} is within the reach its weapon lands from ({@link #LAND_SHARE}
	 * of {@link #reach}), for an armed humanoid; the game's own short reach is left for fists and everyone else.
	 */
	public static boolean inReach(Mob mob, LivingEntity foe) {
		return !MobCombat.ENABLED && !mob.getMainHandItem().isEmpty() && fighter(mob, foe) && MobCombat.gap(mob, foe) <= reach(mob) * LAND_SHARE;
	}

	/**
	 * Every tick in a melee fight (the game's melee goal after its own tick; MCA's through {@link #brainStep}): after a blow, a foe
	 * left inside its weapon's reach is stepped back from to that reach, facing it. True while it steps back.
	 */
	public static boolean space(Mob mob, LivingEntity foe) {
		if (!(mob.level() instanceof ServerLevel level) || !fighter(mob, foe)) {
			return false;
		}
		State s = STATES.get(mob);
		if (s == null) {
			return false;
		}
		long now = level.getGameTime();
		if (MobCombat.blowWithin(mob, Integer.MAX_VALUE)) {
			// (a swing wound up: it stands to it)
			s.backUntil = 0;
			return false;
		}
		double reach = reach(mob), gap = MobCombat.gap(mob, foe);
		if (now >= s.backUntil) {
			if (now - s.lastBlow > BACK_WINDOW || s.backedFor == s.lastBlow || gap >= reach * CLOSE_SHARE) {
				return false;
			}
			s.backedFor = s.lastBlow;
			s.backUntil = now + BACK_MOST;
		}
		Vec3 back = new Vec3(mob.getX() - foe.getX(), 0.0, mob.getZ() - foe.getZ());
		if (gap >= reach * HOLD_SHARE || !mob.onGround() || back.lengthSqr() < 1.0E-4 || !footing(level, mob, back.normalize())) {
			s.backUntil = 0;
			return false;
		}
		mob.getNavigation().stop();
		mob.getLookControl().setLookAt(foe, 30.0F, 30.0F);
		// (squared to the foe, so the step back is straight away from it)
		float yaw = (float) Math.toDegrees(Math.atan2(-back.z, -back.x)) - 90.0F;
		mob.setYRot(yaw);
		mob.setYBodyRot(yaw);
		mob.getMoveControl().strafe(-BACK_SPEED, 0.0F);
		return true;
	}

	/** A step of a block along {@code dir}: room to stand, floor under it no more than a block down, no lava. */
	private static boolean footing(ServerLevel level, Mob mob, Vec3 dir) {
		BlockPos at = BlockPos.containing(mob.getX() + dir.x, mob.getY(), mob.getZ() + dir.z);
		if (!level.getBlockState(at).getCollisionShape(level, at).isEmpty() || !level.getBlockState(at.above()).getCollisionShape(level, at.above()).isEmpty()) {
			return false;
		}
		for (int dy = 0; dy <= 1; dy++) {
			if (level.getFluidState(at.below(dy)).is(FluidTags.LAVA)) {
				return false;
			}
		}
		return !level.getBlockState(at.below()).getCollisionShape(level, at.below()).isEmpty()
			|| !level.getBlockState(at.below(2)).getCollisionShape(level, at.below(2)).isEmpty();
	}

	/**
	 * (from {@code Workers.step}, before an MCA villager's brain) Spacing for MCA's melee fighters: true while it steps back from its
	 * foe (its brain waits).
	 */
	public static boolean brainStep(Mob mob) {
		if (!McaCompat.isMca(mob)) {
			return false;
		}
		LivingEntity foe = Rulers.target(mob);
		return foe != null && space(mob, foe);
	}

	// ---- the planned jumps -----------------------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		if (STATES.isEmpty()) {
			return;
		}
		for (Map.Entry<Mob, State> e : new ArrayList<>(STATES.entrySet())) {
			Mob mob = e.getKey();
			State s = e.getValue();
			if (mob == null || !mob.isAlive() || mob.isRemoved()) {
				STATES.remove(mob);
				continue;
			}
			long now = mob.level().getGameTime();
			if (s.jumpAt > 0 && now >= s.jumpAt) {
				LivingEntity foe = s.jumpFoe;
				s.jumpAt = 0;
				s.jumpFoe = null;
				// (still wound up, the blow still to fall)
				if (fighter(mob, foe) && canJump(mob, s, now) && MobCombat.blowWithin(mob, JUMP_LEAD + 2)) {
					jump(mob, s, foe, now);
				}
			}
			if (s.jumpAt == 0 && now - s.lastBlow > 1200 && now - s.jumpedAt > 1200) {
				STATES.remove(mob);
			}
		}
	}
}
