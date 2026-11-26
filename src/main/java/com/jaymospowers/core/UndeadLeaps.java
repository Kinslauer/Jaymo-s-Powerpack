package com.jaymospowers.core;

import com.jaymospowers.JaymosPowers;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;

/**
 * The undead are clumsy at a jump (Andy, on Mobs Attempt Parkour, CC0: "zombies and skeletons should be clumsy, and
 * fall, skeletons can catch the edge and pull up. zombies lose their limbs most time they try this, or struggle till
 * the daylight comes"). Jaymo's Kingdoms' gap jumps ({@code GapJumps}) ask {@link #leap} by reflection before a zombie or a
 * skeleton jumps a gap, and it says how much of the jump the body puts into it.
 * <ul>
 * <li>A zombie seldom makes it ({@link #ZOMBIE_CLEAN}): it comes up short ({@link #ZOMBIE_SHORT}) and drops into the
 * gap. Landing there jars a rotten arm or leg off ({@link #ZOMBIE_LIMB}, {@link Dismember#sever}); if it keeps its limbs
 * it's stuck down there clawing at the wall, scarcely moving ({@link #STRUGGLE_SLOW}), until the daylight comes (and then
 * the Sun finds it, unless it finds shade).</li>
 * <li>A skeleton makes it half the time ({@link #SKELETON_CLEAN}); a short one ({@link #SKELETON_SHORT}) hits the far side
 * below the edge, and most often ({@link #SKELETON_CATCH}) catches it with its bony hands, hangs a moment
 * ({@link #HANG_TICKS}) and pulls itself up onto the landing. Otherwise it falls.</li>
 * </ul>
 */
public final class UndeadLeaps {
	/** A zombie's chance its jump carries; a short one's share of the jump. */
	static final float ZOMBIE_CLEAN = 0.15F;
	static final double ZOMBIE_SHORT = 0.5;
	/** A zombie that came down in the gap: the chance a limb comes off. Else it struggles there till day. */
	static final float ZOMBIE_LIMB = 0.7F;
	/** A struggling zombie's speed is cut by this share; it claws at the wall this often (ticks, least and spread); it gives up after this long anyway (ticks: no day comes in the Nether). */
	static final double STRUGGLE_SLOW = 0.85;
	static final int CLAW_EVERY = 25, CLAW_SPREAD = 30, STRUGGLE_LONGEST = 12000;
	/** A clawing hop: how high, how hard at the wall. */
	static final double CLAW_UP = 0.36, CLAW_PUSH = 0.08;
	/** A skeleton's chance its jump carries; a short one's share; the chance a short one catches the edge. */
	static final float SKELETON_CLEAN = 0.5F;
	static final double SKELETON_SHORT = 0.72;
	static final float SKELETON_CATCH = 0.7F;
	/** It catches the edge within this much across of the landing, with its feet up to this far under the ledge's top (blocks). */
	static final double CATCH_ACROSS = 1.6, CATCH_BELOW = 1.9;
	/** How long it hangs before it pulls up (ticks); how low it hangs (its feet under the top, blocks). */
	static final int HANG_TICKS = 24;
	static final double HANG_LOW = 1.2;
	/** A leap is watched this long at most (ticks). */
	static final int WATCH_TICKS = 60;

	private static final Identifier STRUGGLE_ID = JaymosPowers.id("struggling_in_a_gap");
	private static final int[] LIMBS = {Dismember.RIGHT_ARM, Dismember.LEFT_ARM, Dismember.RIGHT_LEG, Dismember.LEFT_LEG};

	/** A botched leap being watched: zombie or skeleton, from and to, until when, whether it will catch, ticks hung (-1: not yet). */
	private static final class Leap {
		final boolean zombie;
		final Vec3 from, to;
		final long until;
		final boolean catches;
		boolean airborne;
		int hung = -1;

		Leap(boolean zombie, Vec3 from, Vec3 to, long until, boolean catches) {
			this.zombie = zombie;
			this.from = from;
			this.to = to;
			this.until = until;
			this.catches = catches;
		}
	}

	/** A zombie stuck in a gap: the way it was jumping, when it gives up, and its next claw at the wall. */
	private static final class Struggle {
		final Vec3 way;
		final long until;
		long nextClaw;

		Struggle(Vec3 way, long until, long nextClaw) {
			this.way = way;
			this.until = until;
			this.nextClaw = nextClaw;
		}
	}

	private static final Map<Mob, Leap> LEAPS = new WeakHashMap<>();
	private static final Map<Mob, Struggle> STRUGGLING = new WeakHashMap<>();

	private UndeadLeaps() {
	}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(UndeadLeaps::tick);
	}

	/**
	 * Jaymo's Kingdoms (by reflection), as {@code mob} jumps a gap from {@code from} to {@code to}: how much of a clean jump
	 * it puts in, 1 for anything but a zombie or a skeleton, and 0 for a zombie stuck in a gap already (it doesn't jump).
	 */
	public static double leap(Mob mob, Vec3 from, Vec3 to) {
		if (!(mob.level() instanceof ServerLevel level)) {
			return 1.0;
		}
		if (STRUGGLING.containsKey(mob)) {
			return 0.0;
		}
		boolean zombie = Dismember.zombie(mob);
		boolean skeleton = !zombie && mob.getType().builtInRegistryHolder().is(EntityTypeTags.SKELETONS);
		if (!zombie && !skeleton) {
			return 1.0;
		}
		RandomSource random = mob.getRandom();
		if (random.nextFloat() < (zombie ? ZOMBIE_CLEAN : SKELETON_CLEAN)) {
			return 1.0;
		}
		LEAPS.put(mob, new Leap(zombie, from, to, level.getGameTime() + WATCH_TICKS, !zombie && random.nextFloat() < SKELETON_CATCH));
		return zombie ? ZOMBIE_SHORT : SKELETON_SHORT;
	}

	/** Whether {@code mob} is a zombie stuck in a gap, struggling till day. */
	public static boolean struggling(Mob mob) {
		return STRUGGLING.containsKey(mob);
	}

	private static void tick(MinecraftServer server) {
		if (!LEAPS.isEmpty()) {
			List<Mob> done = new ArrayList<>();
			LEAPS.forEach((mob, leap) -> {
				if (!mob.isAlive() || mob.isRemoved() || mob.level().getGameTime() > leap.until || watch(mob, leap)) {
					done.add(mob);
				}
			});
			done.forEach(LEAPS::remove);
		}
		if (!STRUGGLING.isEmpty()) {
			Iterator<Map.Entry<Mob, Struggle>> it = STRUGGLING.entrySet().iterator();
			while (it.hasNext()) {
				Map.Entry<Mob, Struggle> e = it.next();
				Mob mob = e.getKey();
				if (!mob.isAlive() || mob.isRemoved() || mob.level().isBrightOutside() || mob.level().getGameTime() > e.getValue().until) {
					slow(mob, false);
					it.remove();
				} else {
					struggle(mob, e.getValue());
				}
			}
		}
	}

	/** One tick of a botched leap. True when it's over. */
	private static boolean watch(Mob mob, Leap leap) {
		if (leap.hung >= 0) {
			return hang(mob, leap);
		}
		if (!mob.onGround()) {
			leap.airborne = true;
			if (leap.catches && mob.getDeltaMovement().y < 0.0 && mob.getY() < leap.to.y && mob.getY() > leap.to.y - CATCH_BELOW
				&& across(mob.position(), leap.to) < CATCH_ACROSS) {
				leap.hung = 0;
				mob.playSound(SoundEvents.SKELETON_STEP, 1.0F, 0.7F);
				return hang(mob, leap);
			}
			return false;
		}
		if (!leap.airborne) {
			return false;
		}
		// down again: on the landing after all, or in the gap
		if (mob.getY() >= leap.to.y - 0.5 && across(mob.position(), leap.to) < 1.0 || !leap.zombie) {
			return true;
		}
		fell(mob, leap);
		return true;
	}

	/** A skeleton hanging by its hands from the edge: held against the wall, then pulled up. True once it's up. */
	private static boolean hang(Mob mob, Leap leap) {
		Vec3 way = new Vec3(leap.to.x - leap.from.x, 0.0, leap.to.z - leap.from.z).normalize();
		double back = 0.5 + mob.getBbWidth() / 2.0 + 0.05;
		mob.getNavigation().stop();
		mob.resetFallDistance();
		if (++leap.hung < HANG_TICKS) {
			mob.setPos(leap.to.x - way.x * back, leap.to.y - HANG_LOW, leap.to.z - way.z * back);
			mob.setDeltaMovement(Vec3.ZERO);
			if (leap.hung % 8 == 0) {
				mob.playSound(SoundEvents.SKELETON_STEP, 0.6F, 0.8F + mob.getRandom().nextFloat() * 0.3F);
			}
			return false;
		}
		mob.setPos(leap.to.x, leap.to.y, leap.to.z);
		mob.setDeltaMovement(way.x * 0.1, 0.1, way.z * 0.1);
		mob.playSound(SoundEvents.SKELETON_AMBIENT, 0.8F, 1.1F);
		return true;
	}

	/** A zombie came down in the gap: a limb jarred off, or stuck there struggling till day. */
	private static void fell(Mob mob, Leap leap) {
		RandomSource random = mob.getRandom();
		if (random.nextFloat() < ZOMBIE_LIMB) {
			int missing = Dismember.missing(mob);
			int[] left = java.util.Arrays.stream(LIMBS).filter(p -> (missing & p) == 0).toArray();
			if (left.length > 0 && Dismember.sever(mob, left[random.nextInt(left.length)], null)) {
				return;
			}
		}
		if (mob.level().isBrightOutside()) {
			return;
		}
		long now = mob.level().getGameTime();
		Vec3 way = new Vec3(leap.to.x - leap.from.x, 0.0, leap.to.z - leap.from.z).normalize();
		STRUGGLING.put(mob, new Struggle(way, now + STRUGGLE_LONGEST, now + CLAW_EVERY));
		slow(mob, true);
	}

	/** A tick in the gap: it scarcely gets anywhere, and now and then claws up at the wall it couldn't clear. */
	private static void struggle(Mob mob, Struggle s) {
		long now = mob.level().getGameTime();
		if (now < s.nextClaw || !mob.onGround()) {
			return;
		}
		RandomSource random = mob.getRandom();
		s.nextClaw = now + CLAW_EVERY + random.nextInt(CLAW_SPREAD);
		mob.setYRot((float) (Math.atan2(s.way.z, s.way.x) * (180.0 / Math.PI)) - 90.0F);
		mob.setDeltaMovement(s.way.x * CLAW_PUSH, CLAW_UP, s.way.z * CLAW_PUSH);
		mob.needsSync = true;
		mob.swing(net.minecraft.world.InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
		mob.level().playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.ZOMBIE_AMBIENT, SoundSource.HOSTILE, 0.8F,
			0.8F + random.nextFloat() * 0.2F);
	}

	private static void slow(Mob mob, boolean on) {
		AttributeInstance speed = mob.getAttribute(Attributes.MOVEMENT_SPEED);
		if (speed == null) {
			return;
		}
		if (on) {
			speed.addOrUpdateTransientModifier(new AttributeModifier(STRUGGLE_ID, -STRUGGLE_SLOW, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
		} else {
			speed.removeModifier(STRUGGLE_ID);
		}
	}

	private static double across(Vec3 a, Vec3 b) {
		double dx = a.x - b.x, dz = a.z - b.z;
		return Math.sqrt(dx * dx + dz * dz);
	}
}
