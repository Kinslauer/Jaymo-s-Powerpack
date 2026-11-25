package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
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
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.illager.Pillager;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Patch 1 of the fighting AI (weapon sense; the ideas are Hostile Humans', none of its code): how a fighter handles the weapons it
 * really holds and carries. One {@link #step} a tick for every NPC fighter that shoots or throws: folk and pillagers through
 * {@link SenseGoal}, MCA's villagers through {@link RangedTactics#brainStep}.
 * <ul>
 * <li><b>Pick</b> ({@link #pick}): from what its hands hold (and an MCA villager's pack), a loaded gun first ({@link MobAmmo.Kind#SHOT}),
 * then a bow or crossbow with arrows, then a throwing weapon (javelins, a boomerang, thrown from what it carries, so the hands stay as
 * they are), then its best melee weapon, and that goes into its hand. Re-picked as ammo runs out and as the range changes ({@link #arm}
 * names what the hand holds then). Mobs with one weapon (a skeleton's bow, a pillager's crossbow) keep it and are not touched.</li>
 * <li><b>Blade when rushed</b>: a shooter with a melee weapon (in the off hand, in an MCA villager's pack, or a folk archer's belt
 * sidearm, {@link Rearm#SIDEARM}, once a life) swaps to it when a foe is inside {@link #RUSHED} blocks ({@link #RUSHED_LOADED} for a
 * crossbow already loaded) and back to the bow once the foe is past {@link #CLEAR} blocks, it has stepped clear (the foe is after
 * someone else), or the fight is over. Worked out from what the hands hold, so it holds across a reload. A folk's hands change (bow
 * to the off hand, blade to the main, each with its own drop chance); an MCA villager's hand takes the pack's own stack, as MCA's
 * arming does, so nothing is copied or lost, and MCA's re-arming leaves it be for the fight ({@link #keeps}). It takes
 * {@link #SWAP_TICKS} before it can shoot. The WeaponMod boomerang is thrown from {@link Boomerangs}.</li>
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
	/**
	 * A vanilla pillager walks up to this far (blocks) from its foe before it stops to shoot, in place of 8 ({@code CrossbowGoalAmmoMixin}):
	 * the far end of the crossbow's band, so one at a corner spot anywhere in the band stands and shoots.
	 */
	public static final float CROSSBOW_STOP = 26.0F;

	/** Where a weapon to pick is: in the main hand already, nowhere, the off hand, a folk's belt sidearm; 0 and up, a pack slot. */
	private static final int MAIN = -4, NONE = -2, OFF = -1, BELT = -3;

	private static final class State {
		/** Holding its blade because a foe rushed it (worked out from its hands each look). */
		boolean rushed;
		long swapUntil, nextBand, nextPotion, flipAt;
		boolean side, out;
		Arm arm = Arm.MELEE;
		/** The lean side this spot offers (0 none), looked at every step. */
		int corner;
		/** An MCA villager's: the pack stack it took in hand for this fight (MCA's re-arming leaves it be, {@link #keeps}). */
		@Nullable ItemStack held;
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
			// (a wall archer or a gunner kept to his post, home within a block of it, shoots from there with MCA's archer brain;
			// any other enlisted archer, a guard, a camp's or a ship's fighter, or a forest ranger is a fighter, never a civilian)
			boolean posted = villager instanceof Enlisted e && e.role() == Folk.ARCHER && villager.hasHome() && villager.getHomeRadius() <= 1;
			return !villager.isBaby() && !posted && (Downed.isGuard(villager) || Barbarians.isCampVillager(villager)
				|| villager instanceof Worker w && Pirates.isPirate(w.role()) || villager.entityTags().contains(ForestRangers.TAG));
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
		boolean over = foe == null || !foe.isAlive() || Downed.isDowned(foe);
		if (over || !applies(mob)) {
			release(level, mob, s, over);
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
			pick(level, mob, foe, s, d, now);
		}
		s.arm = arm(mob, d);
		// (looked for whether or not it sees the foe: at a corner the wall hides it till it leans out, which is the point)
		s.corner = s.arm.ranged() ? Cover.corner(level, mob, foe, mob.position(), s.corner) : 0;
		if (swapping(mob) || RangedTactics.melee(mob)) {
			return;
		}
		// (a throw needs a clear line to where it is aimed, not a shoulder showing round a wall: NpcEyes)
		boolean clear = sees && NpcEyes.clearShot(mob, foe);
		if (clear && throwPotion(level, mob, foe, s, now, d)) {
			return;
		}
		if (s.arm == Arm.THROWN && clear && d >= Arm.THROWN.least && d <= Arm.THROWN.most && Boomerangs.armed(mob)) {
			Boomerangs.throwAt(level, mob, foe);
		}
		if (fetch(level, mob, foe, d)) {
			return;
		}
		hold(level, mob, foe, s, now, d);
	}

	/**
	 * It has no foe or isn't a fighter any more: upright, and the blade rule forgotten. A fight that is {@code over} puts a folk
	 * shooter's bow back in hand (from what its hands hold, so after a reload too); an MCA villager's hands are MCA's again.
	 */
	private static void release(ServerLevel level, Mob mob, @Nullable State s, boolean over) {
		if (s != null) {
			if (s.out || s.corner != 0) {
				Lean.lean(mob, 0);
			}
			s.out = false;
			s.corner = 0;
			s.rushed = false;
			s.arm = Arm.MELEE;
			s.held = null;
		}
		if (over && !(mob instanceof Villager) && !(mob instanceof Pillager) && mob.isAlive() && !Downed.isDowned(mob) && !swapping(mob)
			&& Rearm.isMelee(mob.getMainHandItem()) && drawn(mob.getOffhandItem()) && MobAmmo.has(mob, MobAmmo.Kind.ARROWS)) {
			exchange(level, mob, OFF, level.getGameTime());
		}
	}

	// ---- blade or bow ----------------------------------------------------------------------------------------------------

	/** A bow or a crossbow, what the blade rule is about (a gun keeps its bayonet). */
	private static boolean drawn(ItemStack stack) {
		return stack.getItem() instanceof BowItem || stack.getItem() instanceof CrossbowItem && !stack.is(Muskets.musket());
	}

	/** A gun: an early gun's rung, or a musket. */
	private static boolean gun(ItemStack stack) {
		return !stack.isEmpty() && (EarlyGuns.Rung.of(stack) != null || stack.is(Muskets.musket()));
	}

	/** The stack at {@code at} (as {@link #MAIN}, {@link #OFF} or a pack slot), or empty. */
	private static ItemStack at(Mob mob, int at) {
		if (at == MAIN) {
			return mob.getMainHandItem();
		}
		if (at == OFF) {
			return mob.getOffhandItem();
		}
		return at >= 0 && mob instanceof Villager v && at < v.getInventory().getContainerSize() ? v.getInventory().getItem(at) : ItemStack.EMPTY;
	}

	/** Where else than the main hand {@code mob} has a weapon {@code kind} likes: the off hand, else an MCA villager's pack; {@link #NONE} if nowhere. */
	private static int find(Mob mob, java.util.function.Predicate<ItemStack> kind) {
		if (kind.test(mob.getOffhandItem())) {
			return OFF;
		}
		if (mob instanceof Villager v) {
			SimpleContainer pack = v.getInventory();
			for (int i = 0; i < pack.getContainerSize(); i++) {
				if (kind.test(pack.getItem(i))) {
					return i;
				}
			}
		}
		return NONE;
	}

	/** Its best blade other than the main hand's: the hardest-hitting in the off hand or the pack, else a folk shooter's belt sidearm ({@link #BELT}) while it has it. */
	private static int bestBlade(Mob mob) {
		int best = NONE;
		double most = -1.0;
		int size = mob instanceof Villager v ? v.getInventory().getContainerSize() : 0;
		for (int i = OFF; i < size; i++) {
			ItemStack stack = at(mob, i);
			double hits = Rearm.isMelee(stack) ? stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY)
				.compute(Attributes.ATTACK_DAMAGE, 0.0, EquipmentSlot.MAINHAND) : -1.0;
			if (hits > most) {
				most = hits;
				best = i;
			}
		}
		if (best == NONE && mob instanceof Folk f && f.ranged() && mob.getOffhandItem().isEmpty() && Rearm.sidearmLeft(mob)) {
			best = BELT;
		}
		return best;
	}

	/** It will throw from what it carries at {@code d} blocks (javelins, a boomerang): the hand it holds stays as it is. */
	private static boolean throwing(Mob mob, double d) {
		if (d < Arm.THROWN.least - MARGIN) {
			return false;
		}
		boolean javelins = mob instanceof Folk f ? Javelins.thrower(f) : mob instanceof Worker w && Javelins.thrower(w);
		return Boomerangs.armed(mob) || javelins && MobAmmo.has(mob, MobAmmo.Kind.JAVELINS);
	}

	/**
	 * ws-pick and ws-swap: the weapon {@code mob} should hold just now, from its hands (and an MCA villager's pack): a gun with shot,
	 * else a bow or crossbow with arrows, else (thrown from what it carries) whatever it holds, else its best blade; and its blade
	 * in place of the bow while a foe is on top of it. Into the hand it goes. Ammo is only asked of a kind it has the weapon for.
	 */
	private static void pick(ServerLevel level, Mob mob, LivingEntity foe, State s, double d, long now) {
		s.rushed = false;
		if (mob instanceof Pillager) {
			// (one weapon: the crossbow)
			return;
		}
		ItemStack main = mob.getMainHandItem();
		if (gun(main) && MobAmmo.has(mob, MobAmmo.Kind.SHOT)) {
			return;
		}
		boolean shooting = !RangedTactics.melee(mob);
		int ranged = NONE;
		int gunAt = find(mob, WeaponSense::gun);
		if (gunAt != NONE && MobAmmo.has(mob, MobAmmo.Kind.SHOT)) {
			ranged = gunAt;
		} else if (shooting && drawn(main) && MobAmmo.has(mob, MobAmmo.Kind.ARROWS)) {
			ranged = MAIN;
		} else if (shooting) {
			int bow = find(mob, WeaponSense::drawn);
			if (bow != NONE && MobAmmo.has(mob, MobAmmo.Kind.ARROWS)) {
				ranged = bow;
			}
		}
		int blade = Rearm.isMelee(main) ? MAIN : bestBlade(mob);
		int want;
		if (ranged != NONE && drawn(at(mob, ranged))) {
			ItemStack bow = at(mob, ranged);
			double near = bow.getItem() instanceof CrossbowItem && CrossbowItem.isCharged(bow) ? RUSHED_LOADED : RUSHED;
			// (holding its blade already it keeps it till the foe is past the clear range or it has stepped clear)
			boolean onTop = d < near || blade == MAIN && d <= CLEAR && !steppedClear(mob, foe, d);
			s.rushed = onTop && blade != NONE;
			want = s.rushed ? blade : ranged;
		} else if (ranged != NONE) {
			want = ranged;
		} else if (throwing(mob, d)) {
			want = MAIN;
		} else {
			want = blade;
		}
		if (want != MAIN && want != NONE) {
			exchange(level, mob, want, now);
		}
	}

	/** Out of a rush: the foe is beyond {@link #RUSHED} and after someone else (a player is always taken to be after it). */
	private static boolean steppedClear(Mob mob, LivingEntity foe, double d) {
		return d > RUSHED && foe instanceof Mob m && Comrades.target(m) != mob;
	}

	/**
	 * Takes the weapon at {@code from} ({@link #OFF}, {@link #BELT}, or a pack slot) into the main hand. Hands change places, each
	 * with the drop chance it had (a weapon picked up off the ground always drops, {@link Rearm}); the belt sidearm is drawn once a
	 * life, the bow going to the empty off hand. An MCA villager's hand takes the pack's own stack, as MCA's own arming does: what
	 * it held stays in the pack (or goes into it, if it came from nowhere), so nothing is copied or lost. False, nothing changed, if
	 * there is no room.
	 */
	private static boolean exchange(ServerLevel level, Mob mob, int from, long now) {
		ItemStack main = mob.getMainHandItem(), off = mob.getOffhandItem();
		float mainDrop = mob.getDropChances().byEquipment(EquipmentSlot.MAINHAND), offDrop = mob.getDropChances().byEquipment(EquipmentSlot.OFFHAND);
		if (from == OFF) {
			mob.setItemSlot(EquipmentSlot.MAINHAND, off);
			mob.setItemSlot(EquipmentSlot.OFFHAND, main);
			mob.setDropChance(EquipmentSlot.MAINHAND, offDrop);
			mob.setDropChance(EquipmentSlot.OFFHAND, mainDrop);
		} else if (from == BELT) {
			if (!off.isEmpty() || !Rearm.drawSidearm(mob)) {
				return false;
			}
			mob.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Rearm.SIDEARM));
			mob.setItemSlot(EquipmentSlot.OFFHAND, main);
			// (Rearm's sidearm never drops)
			mob.setDropChance(EquipmentSlot.MAINHAND, 0.0F);
			mob.setDropChance(EquipmentSlot.OFFHAND, mainDrop);
		} else if (from >= 0 && mob instanceof Villager v) {
			SimpleContainer pack = v.getInventory();
			ItemStack take = pack.getItem(from);
			if (take.isEmpty()) {
				return false;
			}
			if (!main.isEmpty() && !inPack(pack, main)) {
				if (!pack.canAddItem(main)) {
					return false;
				}
				pack.addItem(main);
			}
			mob.setItemSlot(EquipmentSlot.MAINHAND, take);
		} else {
			return false;
		}
		State s = STATES.get(mob);
		if (s != null) {
			s.swapUntil = now + SWAP_TICKS;
			s.held = mob instanceof Villager ? mob.getMainHandItem() : null;
		}
		mob.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
		mob.swing(InteractionHand.OFF_HAND, SwingAnimation.DEFAULT, true);
		level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.ARMOR_EQUIP_IRON.value(), SoundSource.HOSTILE, 0.8F, 1.2F);
		return true;
	}

	/** {@code stack} is one of {@code pack}'s own (the very stack, as MCA's hands hold them). */
	private static boolean inPack(SimpleContainer pack, ItemStack stack) {
		for (int i = 0; i < pack.getContainerSize(); i++) {
			if (pack.getItem(i) == stack) {
				return true;
			}
		}
		return false;
	}

	/**
	 * ({@code McaEquipmentMixin}) MCA's re-arming leaves {@code villager}'s {@code slot} be: its main hand holds the pack stack weapon
	 * sense picked for this fight (an archer's blade for a foe on top of it, a guard's bow for a far one).
	 */
	public static boolean keeps(Mob villager, EquipmentSlot slot) {
		State s = STATES.get(villager);
		return slot == EquipmentSlot.MAINHAND && s != null && s.held != null && villager.getMainHandItem() == s.held;
	}

	// ---- the band and the strafe -----------------------------------------------------------------------------------------

	/** Keeps its weapon's band: too close backs off, too far closes in, to a corner if it can find one. */
	private static void hold(ServerLevel level, Mob mob, LivingEntity foe, State s, long now, double d) {
		Arm arm = s.arm;
		// (a javelin or spear thrower too, while it has throws left: arm names it a thrower only then; out of them it fights hand to hand)
		boolean kites = arm.ranged() && !s.rushed;
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

	/**
	 * While it aims it sidesteps (a side every {@link #STRAFE_LEAST} to {@link #STRAFE_MOST} ticks). At a corner (the wall hides the
	 * foe from where it stands upright) it leans out past it instead and stays out while it aims, standing where it is: no goal walks it
	 * out into the open (a pillager's crossbow goal would, not seeing its foe upright). It comes back in when it ducks to cover
	 * ({@link RangedTactics}) or stops aiming.
	 */
	private static void strafe(ServerLevel level, Mob mob, LivingEntity foe, State s, long now, boolean sees, double d) {
		boolean aiming = s.arm.ranged() && (sees || s.corner != 0) && !s.rushed && d >= s.arm.least - MARGIN && !swapping(mob) && !RangedTactics.active(mob)
			&& !mob.isBlocking();
		if (!aiming) {
			if (s.out) {
				s.out = false;
				Lean.lean(mob, 0);
			}
			return;
		}
		if (s.corner != 0) {
			if (!s.out || Lean.dir(mob) != s.corner) {
				s.out = true;
				Lean.lean(mob, s.corner);
			}
			mob.getNavigation().stop();
			mob.getLookControl().setLookAt(foe, 30.0F, 30.0F);
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
		// (counted without filling: asking MobAmmo whether it "has" a kind it never carried would hand it a stock of them)
		boolean javelins = MobAmmo.carries(mob, MobAmmo.Kind.JAVELINS), boomerangs = MobAmmo.carries(mob, MobAmmo.Kind.BOOMERANGS);
		if (!javelins && !boomerangs || d < FETCH_FOE_GAP || RangedTactics.active(mob) || javelins && MobAmmo.left(mob, MobAmmo.Kind.JAVELINS) > 0
			|| boomerangs && MobAmmo.left(mob, MobAmmo.Kind.BOOMERANGS) > 0) {
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
			if (RangedTactics.friendly(mob, e) || e instanceof Player && Rulers.target(mob) != e || e instanceof Folk f && mob instanceof Folk m && m.sameSide(f)) {
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
