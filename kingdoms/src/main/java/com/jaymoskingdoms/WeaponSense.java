package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.illager.Pillager;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Patch 1 of the fighting AI (weapon sense; the ideas are Hostile Humans', none of its code): how a fighter handles the weapons it
 * really holds and carries. One {@link #step} a tick for every NPC fighter that shoots or throws: folk and pillagers through
 * {@link SenseGoal}, MCA's villagers through {@link RangedTactics#brainStep}.
 * <ul>
 * <li><b>Pick</b> ({@link #arm}): a loaded gun first ({@link MobAmmo.Kind#SHOT}), then a bow or crossbow with arrows, then a throwing
 * weapon (javelins, a boomerang), then its best melee weapon. Re-picked as ammo runs out and as the range changes. Mobs with one
 * weapon (a skeleton's bow) keep it and are not touched.</li>
 * <li><b>Blade when rushed</b>: a shooter with a melee weapon (in the off hand, in an MCA villager's pack, or a folk archer's belt
 * sidearm, {@link Rearm#SIDEARM}) swaps to it when a foe is inside {@link #RUSHED} blocks ({@link #RUSHED_LOADED} for a crossbow
 * already loaded) and back to the bow once the foe is past {@link #CLEAR} blocks. Both hands change (bow to the off hand, blade to
 * the main) and it takes {@link #SWAP_TICKS} before it can shoot. The WeaponMod boomerang is thrown from {@link Boomerangs}.</li>
 * <li><b>Distance bands</b> ({@link Arm}): each weapon has the range it likes. Too close it backs off, too far it closes in
 * ({@link RangedTactics.Plan#BAND}), to a spot that is a wall corner it can lean round ({@link Cover#corner}) first, else low cover
 * ({@link Cover#works}), else any clear line; never to a spot beside lava, and only along a path that exists.</li>
 * <li><b>Strafe</b>: while it aims it sidesteps, changing side every {@link #STRAFE_LEAST} to {@link #STRAFE_MOST} ticks; at a corner it
 * leans out ({@link Lean#lean}) and back in instead of stepping out.</li>
 * <li><b>Throw and fetch</b>: javelin, trident and boomerang throwers throw from their band ({@link Javelins}, {@link Boomerangs});
 * thrown, the weapon lies where it falls ({@link MobAmmo}) and the thrower walks to it when the foe is not on top of it
 * ({@link #FETCH_FOE_GAP}); with none it fights hand to hand.</li>
 * <li><b>Harmful potions</b>: a fighter carrying a splash potion of harm, poison, slowness or weakness throws it at a foe
 * {@link #POTION_NEAR} to {@link #POTION_FAR} blocks off, spending the real potion, never when a friend or a player stands in the splash.</li>
 * </ul>
 * Never against a downed body ({@link Downed}), never from a guard making an arrest ({@link Arrests}), never from a wall archer kept to
 * his post or a gunner at his cannon.
 */
public final class WeaponSense {
	/** What a fighter fights with just now, and the range (blocks, least to most) it likes to fight it from. */
	enum Arm {
		PISTOL(5.0, 12.0), MUSKET(14.0, 28.0), BOW(12.0, 22.0), CROSSBOW(14.0, 26.0), THROWN(6.0, 16.0), SPEAR_THROWER(6.0, 14.0), MELEE(0.0, 0.0);

		final double least, most;

		Arm(double least, double most) {
			this.least = least;
			this.most = most;
		}

		boolean ranged() {
			return this != MELEE;
		}
	}

	/** Ticks between the fuller looks (swap, potions, band, fetch); strafing is every tick. */
	static final int STEP = 5;
	/** A foe this close (blocks) rushes a shooter to its blade; 4 for a crossbow already loaded; it goes back to the bow beyond {@link #CLEAR}. */
	static final double RUSHED = 6.0, RUSHED_LOADED = 4.0, CLEAR = 9.0;
	/** A blade swap takes this long (ticks) before the shooter can shoot again. */
	static final int SWAP_TICKS = 10;
	/** The band is held to this much leeway (blocks), and a shooter looks to move this often (ticks) at most. */
	static final double MARGIN = 1.5;
	static final int BAND_COOLDOWN = 60;
	/** Candidate spots tried for a band, and the arc (degrees, either side of the foe's line to it) they are tried in. */
	static final int SAMPLES = 14;
	static final double ARC = 70.0;
	/** Strafing: side changes every this many ticks (1 to 2 seconds), and how hard it steps. */
	static final int STRAFE_LEAST = 20, STRAFE_MOST = 40;
	static final float STRAFE_SPEED = 0.5F;
	/** Harmful splash potions are thrown from this near to this far (blocks), this long apart (ticks); the splash reaches this far (blocks) from where it lands. */
	static final double POTION_NEAR = 4.0, POTION_FAR = 10.0, SPLASH = 4.0;
	static final int POTION_GAP = 100;
	static final float POTION_POWER = 0.6F, POTION_STRAY = 2.0F;
	/** Going for a thrown weapon needs the foe at least this far (blocks) from the thrower. */
	static final double FETCH_FOE_GAP = 6.0;
	/** A vanilla pillager walks up to this far (blocks) from its foe before it stops to shoot, in place of 8 ({@code CrossbowGoalAmmoMixin}). */
	public static final float CROSSBOW_STOP = 22.0F;

	private static final class State {
		/** Holding its blade because a foe rushed it. */
		boolean rushed;
		long swapUntil, nextBand, nextPotion, flipAt;
		boolean side, out;
		Arm arm = Arm.MELEE;
		/** The lean side this spot offers (0 none), looked at every step. */
		int corner;
	}

	private static final Map<Mob, State> STATES = new WeakHashMap<>();

	private WeaponSense() {
	}

	// ---- who, and what with ----------------------------------------------------------------------------------------------

	/** Whether {@code mob} is a fighter this is for just now. */
	static boolean applies(Mob mob) {
		if (!mob.isAlive() || mob.isPassenger() || Downed.isDowned(mob) || Arrests.busy(mob) || !(mob.level() instanceof ServerLevel level)) {
			return false;
		}
		if (mob instanceof Folk folk) {
			return folk.combatant() && !Sorcery.isCaster(folk.role()) && !folk.isCarrying() && !(folk.getVehicle() instanceof Cannon)
				&& !WallArchers.posted(level, folk);
		}
		if (mob instanceof Villager villager) {
			return !villager.isBaby() && !(villager instanceof Enlisted e && e.role() == Folk.ARCHER)
				&& (Downed.isGuard(villager) || Barbarians.isCampVillager(villager) || villager instanceof Worker w && Pirates.isPirate(w.role()));
		}
		return mob instanceof Pillager;
	}

	/** What {@code mob} fights with against a foe {@code distance} blocks off, from what it holds and carries (see the class comment). */
	static Arm arm(Mob mob, double distance) {
		ItemStack main = mob.getMainHandItem();
		if (mob instanceof Folk folk && Muskets.armed(folk) && MobAmmo.has(mob, MobAmmo.Kind.SHOT)) {
			return Arm.MUSKET;
		}
		EarlyGuns.Rung rung = EarlyGuns.Rung.of(main);
		if (rung != null && MobAmmo.has(mob, MobAmmo.Kind.SHOT)) {
			return rung == EarlyGuns.Rung.HANDGONNE ? Arm.PISTOL : Arm.MUSKET;
		}
		if (mob instanceof Worker w && Pirates.isPirate(w.role()) && mob instanceof PathfinderMob pirate && MobAmmo.has(mob, MobAmmo.Kind.SHOT)
			&& (w.role() == Pirates.GUNNER || Pirates.pistolReady(pirate, mob.level().getGameTime()))) {
			return Arm.PISTOL;
		}
		if (main.getItem() instanceof BowItem && MobAmmo.has(mob, MobAmmo.Kind.ARROWS)) {
			return Arm.BOW;
		}
		if (main.getItem() instanceof CrossbowItem && MobAmmo.has(mob, MobAmmo.Kind.ARROWS)) {
			return Arm.CROSSBOW;
		}
		if (distance >= Arm.THROWN.least - MARGIN) {
			if (Boomerangs.armed(mob)) {
				return Arm.THROWN;
			}
			boolean javelins = mob instanceof Folk f ? Javelins.thrower(f) : mob instanceof Worker w && Javelins.thrower(w);
			if (javelins && MobAmmo.has(mob, MobAmmo.Kind.JAVELINS)) {
				return spear(main) ? Arm.SPEAR_THROWER : Arm.THROWN;
			}
		}
		return Arm.MELEE;
	}

	/** Whether a thrower of javelins in a band its foe stands in throws first ({@code Javelins#tick}: the pick puts a throwing weapon before the blade). */
	static boolean throwsFirst(Mob mob, double distance) {
		Arm arm = arm(mob, distance);
		State s = STATES.get(mob);
		return (arm == Arm.THROWN || arm == Arm.SPEAR_THROWER) && distance >= arm.least && distance <= arm.most && (s == null || !s.rushed)
			&& !swapping(mob);
	}

	/** It is changing hands (a blade swap) and can't shoot yet. */
	static boolean swapping(Mob mob) {
		State s = STATES.get(mob);
		return s != null && s.swapUntil > mob.level().getGameTime();
	}

	/** It holds its blade because a foe rushed it (Orcs' range-keyed bow and crossbow wait). */
	static boolean rushed(Mob mob) {
		State s = STATES.get(mob);
		return s != null && s.rushed;
	}

	/** Spears and tridents in hand put a thrower in the spear thrower's band. */
	private static boolean spear(ItemStack stack) {
		return stack.is(net.minecraft.tags.ItemTags.SPEARS) || stack.is(Items.TRIDENT);
	}

	// ---- the step --------------------------------------------------------------------------------------------------------

	/** One tick of {@code mob}'s weapon sense (cheap when it has no foe). */
	static void step(Mob mob) {
		if (!(mob.level() instanceof ServerLevel level)) {
			return;
		}
		LivingEntity foe = Rulers.target(mob);
		State s = STATES.get(mob);
		if (foe == null || !foe.isAlive() || Downed.isDowned(foe) || !applies(mob)) {
			if (s != null) {
				release(mob, s);
			}
			return;
		}
		if (s == null) {
			s = new State();
			STATES.put(mob, s);
		}
		long now = level.getGameTime();
		double d = mob.distanceTo(foe);
		boolean sees = mob.getSensing().hasLineOfSight(foe);
		strafe(level, mob, foe, s, now, sees, d);
		if ((mob.tickCount + mob.getId()) % STEP != 0) {
			return;
		}
		if (!swapping(mob)) {
			swapHands(level, mob, s, d, now);
		}
		s.arm = arm(mob, d);
		s.corner = s.arm.ranged() && sees ? Cover.corner(level, mob, foe, mob.position()) : 0;
		if (swapping(mob) || RangedTactics.melee(mob)) {
			return;
		}
		if (sees && throwPotion(level, mob, foe, s, now, d)) {
			return;
		}
		if (s.arm == Arm.THROWN && sees && d >= Arm.THROWN.least && d <= Arm.THROWN.most && Boomerangs.armed(mob)) {
			Boomerangs.throwAt(level, mob, foe);
		}
		if (fetch(level, mob, foe, d)) {
			return;
		}
		hold(level, mob, foe, s, now, d);
	}

	/** It has no foe or isn't a fighter any more: upright, and the blade rule forgotten (the hands are left as they are). */
	private static void release(Mob mob, State s) {
		if (s.out || s.corner != 0) {
			Lean.lean(mob, 0);
		}
		s.out = false;
		s.corner = 0;
		s.rushed = false;
		s.arm = Arm.MELEE;
	}

	// ---- blade or bow ----------------------------------------------------------------------------------------------------

	/** The ranged weapon in the main hand that the blade rule is about: a bow or a crossbow (a gun keeps its bayonet). */
	private static boolean drawn(ItemStack stack) {
		return stack.getItem() instanceof BowItem || stack.getItem() instanceof CrossbowItem && !stack.is(Muskets.musket());
	}

	/** Where a blade could come from: -2 none, -1 the off hand, otherwise the index in a villager's pack. */
	private static int blade(Mob mob, State s) {
		if (Rearm.isMelee(mob.getOffhandItem())) {
			return -1;
		}
		if (mob instanceof Villager v) {
			SimpleContainer pack = v.getInventory();
			for (int i = 0; i < pack.getContainerSize(); i++) {
				if (Rearm.isMelee(pack.getItem(i))) {
					return i;
				}
			}
			return -2;
		}
		// (a folk archer's belt sidearm, as Rearm's: a stone sword in an empty off hand)
		return mob instanceof Folk f && f.ranged() && mob.getOffhandItem().isEmpty() ? -3 : -2;
	}

	/** A ranged weapon (with arrows to loose) in the off hand or the pack, to go back to: its place as {@link #blade}'s. */
	private static int bow(Mob mob) {
		if (drawn(mob.getOffhandItem()) && MobAmmo.has(mob, MobAmmo.Kind.ARROWS)) {
			return -1;
		}
		if (mob instanceof Villager v && MobAmmo.has(mob, MobAmmo.Kind.ARROWS)) {
			SimpleContainer pack = v.getInventory();
			for (int i = 0; i < pack.getContainerSize(); i++) {
				if (drawn(pack.getItem(i))) {
					return i;
				}
			}
		}
		return -2;
	}

	private static void swapHands(ServerLevel level, Mob mob, State s, double d, long now) {
		ItemStack main = mob.getMainHandItem();
		if (drawn(main) && !(mob instanceof Pillager)) {
			double near = main.getItem() instanceof CrossbowItem && CrossbowItem.isCharged(main) ? RUSHED_LOADED : RUSHED;
			if (d < near) {
				int from = blade(mob, s);
				if (from != -2 && exchange(level, mob, from, now)) {
					s.rushed = true;
				}
			}
		} else if (Rearm.isMelee(main) && d > CLEAR && (s.rushed || mob instanceof Villager)) {
			// back to the bow once the foe is past the clear range (an MCA guard with a bow in the pack draws it for a far foe too)
			int from = bow(mob);
			if (from != -2 && exchange(level, mob, from, now)) {
				s.rushed = false;
			}
		}
		if (s.rushed && !Rearm.isMelee(mob.getMainHandItem()) && !drawn(mob.getMainHandItem())) {
			s.rushed = false;
		}
	}

	/**
	 * Takes the weapon from {@code from} ({@link #blade}: -1 off hand, -3 a belt sidearm, else a pack slot) into the main hand, and
	 * the main hand's into the off hand (or the pack if a shield is there). False, nothing changed, if there is no room.
	 */
	private static boolean exchange(ServerLevel level, Mob mob, int from, long now) {
		ItemStack displaced = mob.getMainHandItem().copy();
		ItemStack off = mob.getOffhandItem().copy();
		SimpleContainer pack = mob instanceof Villager v ? v.getInventory() : null;
		ItemStack take;
		if (from == -1) {
			take = off;
			off = ItemStack.EMPTY;
		} else if (from == -3) {
			take = new ItemStack(Rearm.SIDEARM);
		} else if (pack != null) {
			take = pack.getItem(from).copy();
			pack.setItem(from, ItemStack.EMPTY);
		} else {
			return false;
		}
		if (off.isEmpty()) {
			off = displaced;
		} else if (pack == null || !pack.addItem(displaced).isEmpty()) {
			// (something else is in the off hand, a shield say, and the pack has no room: nothing changes)
			if (from >= 0) {
				pack.setItem(from, take);
			}
			return false;
		}
		mob.setItemSlot(EquipmentSlot.MAINHAND, take);
		mob.setItemSlot(EquipmentSlot.OFFHAND, off);
		mob.setDropChance(EquipmentSlot.MAINHAND, 0.0F);
		mob.setDropChance(EquipmentSlot.OFFHAND, 0.0F);
		State s = STATES.get(mob);
		if (s != null) {
			s.swapUntil = now + SWAP_TICKS;
		}
		mob.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
		mob.swing(InteractionHand.OFF_HAND, SwingAnimation.DEFAULT, true);
		level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.ARMOR_EQUIP_IRON.value(), SoundSource.HOSTILE, 0.8F, 1.2F);
		return true;
	}

	// ---- the band and the strafe -----------------------------------------------------------------------------------------

	/** Keeps its weapon's band: too close backs off, too far closes in, to a corner if it can find one. */
	private static void hold(ServerLevel level, Mob mob, LivingEntity foe, State s, long now, double d) {
		Arm arm = s.arm;
		boolean kites = arm.ranged() && !s.rushed && (arm != Arm.THROWN && arm != Arm.SPEAR_THROWER || Boomerangs.armed(mob));
		if (!kites || now < s.nextBand || RangedTactics.active(mob)) {
			return;
		}
		boolean closer = d > arm.most + MARGIN, back = d < arm.least - MARGIN;
		if (!closer && !back) {
			return;
		}
		s.nextBand = now + BAND_COOLDOWN;
		Vec3 spot = bandSpot(level, mob, foe, arm, back);
		if (spot != null) {
			RangedTactics.go(mob, RangedTactics.Plan.BAND, spot);
		}
	}

	/** A spot within {@code arm}'s band of {@code foe}, on {@code mob}'s side of it, best a corner to lean round; null if none is safe and reachable. */
	private static @Nullable Vec3 bandSpot(ServerLevel level, Mob mob, LivingEntity foe, Arm arm, boolean back) {
		RandomSource random = mob.getRandom();
		Vec3 foePos = foe.position();
		double base = Math.atan2(mob.getZ() - foePos.z, mob.getX() - foePos.x);
		List<Vec3> spots = new ArrayList<>();
		List<Double> scores = new ArrayList<>();
		for (int i = 0; i < SAMPLES; i++) {
			double angle = base + Math.toRadians((random.nextDouble() * 2.0 - 1.0) * ARC);
			double r = arm.least + 1.0 + random.nextDouble() * Math.max(1.0, arm.most - arm.least - 2.0);
			BlockPos ground = RangedTactics.ground(level, new Vec3(foePos.x + Math.cos(angle) * r, mob.getY(), foePos.z + Math.sin(angle) * r), mob.getBlockY());
			if (ground == null || nearLava(level, ground)) {
				continue;
			}
			Vec3 p = Vec3.atBottomCenterOf(ground);
			double score;
			if (Cover.corner(level, mob, foe, p) != 0) {
				score = 3.0;
			} else if (Cover.works(level, mob, foe, p)) {
				score = 2.0;
			} else if (RangedTactics.clear(level, mob, p, foe)) {
				score = 1.0;
			} else {
				continue;
			}
			score -= p.distanceTo(mob.position()) * 0.05;
			int at = 0;
			while (at < scores.size() && scores.get(at) >= score) {
				at++;
			}
			spots.add(at, p);
			scores.add(at, score);
		}
		for (int i = 0; i < Math.min(3, spots.size()); i++) {
			Vec3 p = spots.get(i);
			Path path = mob.getNavigation().createPath(BlockPos.containing(p), 0);
			if (path != null && path.canReach()) {
				return p;
			}
		}
		return null;
	}

	/** Lava on the spot or next to it. */
	private static boolean nearLava(ServerLevel level, BlockPos at) {
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				for (int dy = -1; dy <= 0; dy++) {
					if (level.getFluidState(at.offset(dx, dy, dz)).is(FluidTags.LAVA)) {
						return true;
					}
				}
			}
		}
		return false;
	}

	/** While it aims it sidesteps (a side every {@link #STRAFE_LEAST} to {@link #STRAFE_MOST} ticks); at a corner it leans out and in instead. */
	private static void strafe(ServerLevel level, Mob mob, LivingEntity foe, State s, long now, boolean sees, double d) {
		boolean aiming = s.arm.ranged() && sees && !s.rushed && d >= s.arm.least - MARGIN && !swapping(mob) && !RangedTactics.active(mob) && !mob.isBlocking();
		if (!aiming) {
			if (s.out) {
				s.out = false;
				Lean.lean(mob, 0);
			}
			return;
		}
		if (s.corner != 0) {
			if (now >= s.flipAt) {
				s.flipAt = now + STRAFE_LEAST + mob.getRandom().nextInt(STRAFE_MOST - STRAFE_LEAST + 1);
				s.out = !s.out;
				Lean.lean(mob, s.out ? s.corner : 0);
			}
			return;
		}
		if (s.out) {
			s.out = false;
			Lean.lean(mob, 0);
		}
		if (!mob.onGround() || mob.getNavigation().isInProgress()) {
			return;
		}
		if (now >= s.flipAt) {
			s.flipAt = now + STRAFE_LEAST + mob.getRandom().nextInt(STRAFE_MOST - STRAFE_LEAST + 1);
			s.side = !s.side;
		}
		// the step sideways is onto floor that isn't a drop or lava, else it goes the other way
		Vec3 right = new Vec3(-Math.cos(Math.toRadians(mob.getYRot())), 0.0, -Math.sin(Math.toRadians(mob.getYRot())));
		if (!footing(level, mob, right.scale(s.side ? 1.2 : -1.2))) {
			if (!footing(level, mob, right.scale(s.side ? -1.2 : 1.2))) {
				return;
			}
			s.side = !s.side;
		}
		mob.getLookControl().setLookAt(foe, 30.0F, 30.0F);
		mob.getMoveControl().strafe(0.0F, s.side ? -STRAFE_SPEED : STRAFE_SPEED);
	}

	/** Whether a step of {@code by} from {@code mob} lands on floor with room to stand, no lava, no drop of more than a block. */
	private static boolean footing(ServerLevel level, Mob mob, Vec3 by) {
		BlockPos at = BlockPos.containing(mob.getX() + by.x, mob.getY(), mob.getZ() + by.z);
		return level.getBlockState(at).getCollisionShape(level, at).isEmpty() && level.getBlockState(at.above()).getCollisionShape(level, at.above()).isEmpty()
			&& !level.getBlockState(at.below()).getCollisionShape(level, at.below()).isEmpty() && !nearLava(level, at);
	}

	// ---- going for what it threw -----------------------------------------------------------------------------------------

	/** Out of throwing weapons with its foe not on top of it: to the nearest one lying about. True if it is on its way. */
	private static boolean fetch(ServerLevel level, Mob mob, LivingEntity foe, double d) {
		if (RangedTactics.planOf(mob) == RangedTactics.Plan.FETCH && d < FETCH_FOE_GAP) {
			RangedTactics.cancel(mob);
			return false;
		}
		boolean carries = MobAmmo.carries(mob, MobAmmo.Kind.JAVELINS) || MobAmmo.carries(mob, MobAmmo.Kind.BOOMERANGS);
		if (!carries || d < FETCH_FOE_GAP || RangedTactics.active(mob) || MobAmmo.has(mob, MobAmmo.Kind.JAVELINS) && MobAmmo.carries(mob, MobAmmo.Kind.JAVELINS)
			|| MobAmmo.has(mob, MobAmmo.Kind.BOOMERANGS) && MobAmmo.carries(mob, MobAmmo.Kind.BOOMERANGS)) {
			return false;
		}
		Entity lying = MobAmmo.nearestThrown(level, mob);
		return lying != null && RangedTactics.go(mob, RangedTactics.Plan.FETCH, lying.position());
	}

	// ---- harmful potions -------------------------------------------------------------------------------------------------

	/** Whether {@code stack} is a splash potion with harm, poison, slowness or weakness in it. */
	static boolean harmful(ItemStack stack) {
		if (!stack.is(Items.SPLASH_POTION)) {
			return false;
		}
		PotionContents contents = stack.get(net.minecraft.core.component.DataComponents.POTION_CONTENTS);
		if (contents == null) {
			return false;
		}
		for (MobEffectInstance effect : contents.getAllEffects()) {
			if (effect.getEffect().is(MobEffects.INSTANT_DAMAGE) || effect.getEffect().is(MobEffects.POISON) || effect.getEffect().is(MobEffects.SLOWNESS)
				|| effect.getEffect().is(MobEffects.WEAKNESS)) {
				return true;
			}
		}
		return false;
	}

	/** Throws a harmful splash potion it carries at the foe, spending it; true if thrown. */
	private static boolean throwPotion(ServerLevel level, Mob mob, LivingEntity foe, State s, long now, double d) {
		if (now < s.nextPotion || d < POTION_NEAR || d > POTION_FAR || splashesFriends(level, mob, foe)) {
			return false;
		}
		ItemStack potion = null;
		for (ItemStack held : new ItemStack[] {mob.getMainHandItem(), mob.getOffhandItem()}) {
			if (harmful(held)) {
				potion = held;
				break;
			}
		}
		if (potion == null && mob instanceof Villager v) {
			SimpleContainer pack = v.getInventory();
			for (int i = 0; i < pack.getContainerSize() && potion == null; i++) {
				if (harmful(pack.getItem(i))) {
					potion = pack.getItem(i);
				}
			}
		}
		if (potion == null) {
			return false;
		}
		s.nextPotion = now + POTION_GAP + level.getRandom().nextInt(POTION_GAP);
		// (the real potion is spent: one of the stack)
		ItemStack thrown = potion.copyWithCount(1);
		potion.shrink(1);
		ClericHeals.throwSplash(level, mob, foe, thrown, POTION_POWER, POTION_STRAY);
		return true;
	}

	/** A friend of {@code mob}'s, or any player it isn't fighting, stands where the splash would reach. */
	private static boolean splashesFriends(ServerLevel level, Mob mob, LivingEntity foe) {
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, foe.getBoundingBox().inflate(SPLASH), e -> e != foe && e != mob && e.isAlive())) {
			if (RangedTactics.friendly(mob, e) || e instanceof Player && mob.getTarget() != e || e instanceof Folk f && mob instanceof Folk m && m.sameSide(f)) {
				return true;
			}
		}
		return false;
	}

	/** The goal that runs {@link #step} for a mob with goals (folk, pillagers); it takes no legs. */
	static final class SenseGoal extends Goal {
		private final Mob mob;

		SenseGoal(Mob mob) {
			this.mob = mob;
			this.setFlags(EnumSet.noneOf(Flag.class));
		}

		@Override
		public boolean canUse() {
			return this.mob.getTarget() != null;
		}

		@Override
		public boolean canContinueToUse() {
			return this.mob.getTarget() != null;
		}

		@Override
		public boolean requiresUpdateEveryTick() {
			return true;
		}

		@Override
		public void tick() {
			step(this.mob);
		}

		@Override
		public void stop() {
			step(this.mob);
		}
	}
}
