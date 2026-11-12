package com.jaymospowers.power.xenoform.forms;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.api.PowerKey;
import com.jaymospowers.core.BladeBlood;
import com.jaymospowers.core.blood.BloodTrail;
import com.jaymospowers.core.wounds.Bleeding;
import com.jaymospowers.core.wounds.BodyPart;
import com.jaymospowers.core.wounds.BodyPlan;
import com.jaymospowers.core.wounds.Mending;
import com.jaymospowers.core.wounds.Tripped;
import com.jaymospowers.core.wounds.Wounds;
import com.jaymospowers.power.darktide.DarktideStatus;
import com.jaymospowers.power.xenoform.TailSweep;
import com.jaymospowers.power.xenoform.XenoForm;
import com.jaymospowers.power.xenoform.XenoformPower;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.feline.Ocelot;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * (0.18.109, Scan C) Prowl, the great cat: long and low, tawny with dark stripes, a heavy jaw, round ears, a long tail
 * that twitches. 24 health, fists of 5, the quickest of the beasts. Scanned from an ocelot, or Naturalist's tiger or
 * lion ({@code XenoDna}).
 * <ul>
 * <li>R, Long Pounce: a long, low leap; the first creature it lands on is pinned (tripped, or held fast).</li>
 * <li>G, Rake: claws across the creature in reach: a dirty cut that bleeds.</li>
 * <li>V, Stalk: mark the creature you look at; near it you hit harder, and it glows for you to follow.</li>
 * <li>B, Roar: every mob round you breaks off and runs.</li>
 * </ul>
 * It moves without a sound, and while it sneaks it's hard to see. It always lands on its feet: falls under
 * {@link #SAFE_FALL} blocks don't hurt.
 * <p>Claws, on top of the four moves (nothing is replaced):
 * <ul>
 * <li>Z (tap): claws out or in. Out, its reach is a little longer, and a pounce that lands latches onto the creature's
 * back instead of pinning it.</li>
 * <li>R while sprinting: a Lunge, a longer, flatter, harder pounce.</li>
 * <li>Latched (up to {@link #LATCH_TICKS}): a storm of swipes every {@link #STORM_EVERY} ticks, each cut harder than the
 * last, every few opening another wound; G a quick flurry, V a skewering stab, B a kick that throws it off, R let go.</li>
 * <li>G while sneaking: a tail sweep ({@link TailSweep}).</li>
 * <li>Quick healing: always, through Wounds ({@link Mending}): cuts close and parts mend faster the nearer death it is; a
 * severed limb is never regrown.</li>
 * </ul>
 */
public final class Prowl extends XenoForm {
	// ---- tuning ----
	public static final double POUNCE_OUT = 1.7, POUNCE_UP = 0.35, POUNCE_HIT = 1.2;
	public static final int POUNCE_TICKS = 25, POUNCE_COOLDOWN = 100, PIN_TICKS = 60;
	public static final float POUNCE_DAMAGE = 6.0F;
	public static final double RAKE_REACH = 3.5;
	public static final float RAKE_DAMAGE = 5.0F;
	public static final int RAKE_COOLDOWN = 40;
	public static final double STALK_REACH = 32.0, STALK_NEAR = 6.0;
	public static final int STALK_TICKS = 400, STALK_COOLDOWN = 300;
	public static final double ROAR_RADIUS = 12.0, ROAR_FLEE = 14.0, ROAR_SPEED = 1.4;
	public static final int ROAR_COOLDOWN = 400;
	/** Falls shorter than this (blocks) never hurt it; a fall's damage is about its height less 3. */
	public static final float SAFE_FALL = 12.0F;

	// ---- claws ----
	/** Claws out: extra reach (blocks), also added to Rake's. */
	public static final double CLAW_REACH = 0.8;
	/** The lunge (R while sprinting): speed out and up, damage. */
	public static final double LUNGE_OUT = 2.2, LUNGE_UP = 0.3;
	public static final float LUNGE_DAMAGE = 8.0F;
	public static final int LUNGE_COOLDOWN = 90;
	/** The latch: longest hold (ticks), the biggest it takes (blocks tall), how close it must stay. */
	public static final int LATCH_TICKS = 240;
	public static final float LATCH_MAX_HEIGHT = 4.5F;
	public static final double LATCH_LOSE = 12.0, PIN_STEER = 0.8, PIN_MAX_SPEED = 1.6, PIN_SNAP = 5.0;
	/** Cling to its back: how far behind it (past half its width) and how high (share of its height). */
	public static final double CLING_BEHIND = 0.25, CLING_HEIGHT = 0.4;
	/** The storm: ticks between cuts, a cut's damage (base, added per cut so far, most), and another wound opens every this many cuts. */
	public static final int STORM_EVERY = 8, DEEPEN_EVERY = 3;
	public static final float STORM_BASE = 0.8F, STORM_STEP = 0.1F, STORM_CAP = 2.0F;
	/** G latched: a flurry of this many cuts, and its cooldown. */
	public static final int FLURRY_CUTS = 3, FLURRY_COOLDOWN = 40;
	/** V latched: the skewering stab (damage, plus this per cut so far), how many cuts it counts as, its cooldown. */
	public static final float SKEWER_DAMAGE = 9.0F, SKEWER_PER_CUT = 0.15F;
	public static final int SKEWER_CUTS = 4, SKEWER_COOLDOWN = 100;
	/** B latched: the kick that frees it (damage, push out and up, the stun in ticks) and its cooldown. */
	public static final float KICK_DAMAGE = 3.0F;
	public static final double KICK_OUT = 1.3, KICK_UP = 0.4, HOP_BACK = 0.5, HOP_UP = 0.4;
	public static final int KICK_STUN = 24, KICK_COOLDOWN = 120;
	/** Quick healing: every this many ticks it mends this much (always) plus this much more at the point of death (the square of how hurt it is). */
	public static final int HEAL_EVERY = 20;
	public static final float HEAL_BASE = 0.25F, HEAL_NEAR_DEATH = 1.5F;
	/** An open cut closes every this many ticks, or every {@link #CLOSE_FAST} below {@link #CLOSE_HURT} of its health. */
	public static final int CLOSE_EVERY = 60, CLOSE_FAST = 20;
	public static final float CLOSE_HURT = 0.35F;

	private static final Identifier REACH_ID = JaymosPowers.id("xenoform_prowl_claws_reach");

	/** One Prowl's claws: out or in, what it is latched to (entity id, -1 none) and since when, cuts so far, the next cut, a lunge in the air, the last close of a cut. */
	private static final class Claws {
		boolean out, lunge;
		int target = -1, cuts;
		long since, nextCut, lastClose;
	}

	private static final Map<UUID, Claws> CLAWS = new HashMap<>();

	private static Claws claws(ServerPlayer player) {
		return CLAWS.computeIfAbsent(player.getUUID(), id -> new Claws());
	}

	public Prowl() {
		super("prowl", 1.0F, 24.0F, 5.0, 0.15, 0.1, 0.0, Items.RABBIT_HIDE, 0xC8893A);
	}

	@Override
	public boolean scans(Entity creature) {
		return creature instanceof Ocelot;
	}

	@Override
	public void onEnter(ServerPlayer player, XenoformPower.Live live) {
		player.setSilent(true);
	}

	@Override
	public void onExit(ServerPlayer player, XenoformPower.Live live) {
		player.setSilent(false);
		player.removeEffect(MobEffects.INVISIBILITY);
		live.rush = 0;
		live.stalked = -1;
		live.stalkUntil = 0L;
		Claws claws = CLAWS.remove(player.getUUID());
		setReach(player, false);
		if (claws != null && claws.target >= 0) {
			player.setDeltaMovement(Vec3.ZERO);
		}
		TailSweep.forget(player);
	}

	/** Z (tap): claws out or in. */
	@Override
	public boolean onModeTap(ServerPlayer player, XenoformPower.Live live) {
		Claws claws = claws(player);
		claws.out = !claws.out;
		setReach(player, claws.out);
		if (!claws.out && claws.target >= 0) {
			release(player, claws, true);
		}
		sound(player, SoundEvents.OCELOT_AMBIENT, 0.4F, claws.out ? 1.6F : 0.8F);
		player.sendOverlayMessage(Component.translatable(claws.out ? "message.jaymospowers.xenoform.prowl.claws_out" : "message.jaymospowers.xenoform.prowl.claws_in")
			.withStyle(ChatFormatting.GOLD));
		return true;
	}

	private static void setReach(ServerPlayer player, boolean out) {
		AttributeInstance range = player.getAttribute(Attributes.ENTITY_INTERACTION_RANGE);
		if (range == null) {
			return;
		}
		if (out) {
			range.addOrUpdateTransientModifier(new AttributeModifier(REACH_ID, CLAW_REACH, AttributeModifier.Operation.ADD_VALUE));
		} else {
			range.removeModifier(REACH_ID);
		}
	}

	/** Whether the cat can get onto this one's back. */
	private static boolean latchable(LivingEntity target) {
		return target.isAlive() && !(target instanceof EnderDragon) && !(target instanceof WitherBoss) && target.getBbHeight() <= LATCH_MAX_HEIGHT;
	}

	/** Onto {@code target}'s back, claws in. */
	private static void latch(ServerPlayer player, Claws claws, LivingEntity target) {
		claws.target = target.getId();
		claws.since = player.level().getGameTime();
		claws.cuts = 0;
		claws.nextCut = claws.since + STORM_EVERY;
		sound(player, SoundEvents.OCELOT_HURT, 1.0F, 0.5F);
		com.jaymospowers.core.Shakes.send(player, 0.35F, target.position());
	}

	/** Lets go (a hop off backwards, if {@code hop}). */
	private static void release(ServerPlayer player, Claws claws, boolean hop) {
		Entity e = claws.target < 0 ? null : player.level().getEntity(claws.target);
		claws.target = -1;
		if (hop && player.isAlive()) {
			Vec3 back = e == null ? player.getLookAngle().horizontal().scale(-1.0) : player.position().subtract(e.position()).horizontal();
			back = back.lengthSqr() < 1.0E-4 ? Vec3.ZERO : back.normalize();
			player.setDeltaMovement(back.x * HOP_BACK, HOP_UP, back.z * HOP_BACK);
			player.syncVelocity = true;
		}
	}

	/** One cut of the storm on {@code target}: harder than the last, and every few opens another wound. */
	private static void cut(ServerPlayer player, XenoformPower.Live live, Claws claws, LivingEntity target) {
		ServerLevel level = player.level();
		float damage = Math.min(STORM_CAP, STORM_BASE + STORM_STEP * claws.cuts);
		target.setInvulnerableTime(0);
		target.hurtServer(level, player.damageSources().playerAttack(player), damage);
		claws.cuts++;
		deepen(target, claws.cuts);
		live.hurt = true;
		Vec3 at = target.getBoundingBox().getCenter();
		BloodTrail.bleed(level, target, BladeBlood.color(target), 1, target.getBbWidth() * 0.4, 0.5F, 6);
		level.sendParticles(ParticleTypes.SWEEP_ATTACK, at.x, at.y, at.z, 1, 0.2, 0.2, 0.2, 0.0);
		sound(player, SoundEvents.PLAYER_ATTACK_SWEEP, 0.7F, 1.5F);
	}

	/** The cut opens the body; every {@link #DEEPEN_EVERY} cuts another part of it opens too. */
	private static void deepen(LivingEntity target, int cuts) {
		Bleeding.open(target, BodyPart.BODY, true);
		if (cuts % DEEPEN_EVERY == 0) {
			BodyPlan plan = Wounds.plan(target);
			if (plan != null && plan.size() > 1) {
				Bleeding.open(target, plan.part(target.getRandom().nextInt(plan.size())), true);
			}
		}
	}

	/** Latched: keys R G V B do the back-riding moves. Returns true if the key was taken. */
	private boolean latchedKey(ServerPlayer player, XenoformPower.Live live, Claws claws, PowerKey key) {
		ServerLevel level = player.level();
		if (!(level.getEntity(claws.target) instanceof LivingEntity prey) || !prey.isAlive()) {
			release(player, claws, true);
			return false;
		}
		switch (key) {
			case ABILITY_1 -> release(player, claws, true);
			case ABILITY_2 -> {
				if (ready(player, live, G)) {
					for (int i = 0; i < FLURRY_CUTS; i++) {
						cut(player, live, claws, prey);
					}
					cooldown(player, live, G, FLURRY_COOLDOWN);
				}
			}
			case ABILITY_3 -> {
				if (ready(player, live, V)) {
					prey.setInvulnerableTime(0);
					prey.hurtServer(level, player.damageSources().playerAttack(player), SKEWER_DAMAGE + SKEWER_PER_CUT * claws.cuts);
					claws.cuts += SKEWER_CUTS;
					Bleeding.open(prey, BodyPart.BODY, true);
					deepen(prey, claws.cuts);
					BloodTrail.bleed(level, prey, BladeBlood.color(prey), 3, prey.getBbWidth() * 0.4, 0.7F, 6);
					live.hurt = true;
					sound(player, SoundEvents.PLAYER_ATTACK_CRIT, 1.0F, 0.6F);
					cooldown(player, live, V, SKEWER_COOLDOWN);
				}
			}
			case ABILITY_4 -> {
				if (ready(player, live, B)) {
					prey.hurtServer(level, player.damageSources().playerAttack(player), KICK_DAMAGE);
					Vec3 away = prey.position().subtract(player.position()).horizontal();
					away = away.lengthSqr() < 1.0E-4 ? player.getLookAngle().horizontal() : away.normalize();
					prey.setDeltaMovement(prey.getDeltaMovement().add(away.x * KICK_OUT, KICK_UP, away.z * KICK_OUT));
					prey.syncVelocity = true;
					DarktideStatus.stun(prey, KICK_STUN);
					release(player, claws, true);
					live.hurt = true;
					sound(player, SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.0F, 0.8F);
					cooldown(player, live, B, KICK_COOLDOWN);
				}
			}
			default -> {
			}
		}
		return true;
	}

	/** Every tick latched: pinned to the prey's back, a cut every {@link #STORM_EVERY} ticks. */
	private static void ride(ServerPlayer player, XenoformPower.Live live, Claws claws) {
		ServerLevel level = player.level();
		long now = level.getGameTime();
		if (!(level.getEntity(claws.target) instanceof LivingEntity prey) || !prey.isAlive() || prey.level() != level || !player.isAlive()
			|| now - claws.since > LATCH_TICKS || prey.distanceTo(player) > LATCH_LOSE) {
			release(player, claws, true);
			return;
		}
		double yaw = Math.toRadians(prey.yBodyRot);
		Vec3 forward = new Vec3(-Math.sin(yaw), 0.0, Math.cos(yaw));
		Vec3 spot = prey.position().subtract(forward.scale(prey.getBbWidth() * 0.5 + CLING_BEHIND)).add(0.0, prey.getBbHeight() * CLING_HEIGHT, 0.0);
		Vec3 gap = spot.subtract(player.position());
		if (gap.length() > PIN_SNAP) {
			player.teleportTo(level, spot.x, spot.y, spot.z, java.util.Set.of(), player.getYRot(), player.getXRot(), false);
			player.setDeltaMovement(Vec3.ZERO);
		} else {
			Vec3 v = gap.scale(PIN_STEER);
			double speed = v.length();
			if (speed > PIN_MAX_SPEED) {
				v = v.scale(PIN_MAX_SPEED / speed);
			}
			player.setDeltaMovement(v.add(prey.getDeltaMovement().scale(0.5)));
		}
		player.syncVelocity = true;
		player.resetFallDistance();
		if (prey instanceof Mob mob && mob.getTarget() != player) {
			mob.setTarget(player);
		}
		if (now >= claws.nextCut) {
			claws.nextCut = now + STORM_EVERY;
			cut(player, live, claws, prey);
		}
	}

	/** Quick healing through Wounds: the nearer death, the faster; a severed limb stays gone (Mending skips what is missing). */
	private static void heal(ServerPlayer player, Claws claws) {
		long now = player.level().getGameTime();
		float hurt = 1.0F - Math.min(1.0F, player.getHealth() / Math.max(1.0F, player.getMaxHealth()));
		if (player.tickCount % HEAL_EVERY == 0 && hurt > 0.0F) {
			Mending.mend(player, HEAL_BASE + HEAL_NEAR_DEATH * hurt * hurt);
		}
		if (Bleeding.bleeding(player) != 0 && now - claws.lastClose >= (1.0F - hurt < CLOSE_HURT ? CLOSE_FAST : CLOSE_EVERY)) {
			claws.lastClose = now;
			Mending.closeCut(player);
		}
	}

	@Override
	public boolean allowDamage(ServerPlayer player, XenoformPower.Live live, DamageSource source, float amount) {
		return !(source.is(DamageTypeTags.IS_FALL) && amount <= SAFE_FALL - 3.0F);
	}

	@Override
	public void onKey(ServerPlayer player, XenoformPower.Live live, PowerKey key, boolean down) {
		if (!down) {
			return;
		}
		ServerLevel level = player.level();
		Claws claws = claws(player);
		if (claws.target >= 0 && latchedKey(player, live, claws, key)) {
			return;
		}
		switch (key) {
			case ABILITY_1 -> {
				if (ready(player, live, R)) {
					// sprinting: a Lunge, longer, flatter and harder
					boolean lunge = player.isSprinting();
					Vec3 look = player.getLookAngle().horizontal();
					look = look.lengthSqr() < 1.0E-4 ? new Vec3(0.0, 0.0, 1.0) : look.normalize();
					double out = lunge ? LUNGE_OUT : POUNCE_OUT;
					player.setDeltaMovement(look.x * out, lunge ? LUNGE_UP : POUNCE_UP, look.z * out);
					player.syncVelocity = true;
					live.rush = POUNCE_TICKS;
					claws.lunge = lunge;
					sound(player, SoundEvents.OCELOT_HURT, 0.8F, lunge ? 0.4F : 0.5F);
					cooldown(player, live, R, lunge ? LUNGE_COOLDOWN : POUNCE_COOLDOWN);
				}
			}
			case ABILITY_2 -> {
				if (player.isShiftKeyDown()) {
					// sneaking: the tail sweep
					TailSweep.sweep(player, color);
					return;
				}
				if (!ready(player, live, G)) {
					return;
				}
				LivingEntity target = aimed(player, claws.out ? RAKE_REACH + CLAW_REACH : RAKE_REACH, e -> true);
				if (target == null) {
					return;
				}
				target.hurtServer(level, player.damageSources().playerAttack(player), RAKE_DAMAGE);
				Bleeding.open(target, BodyPart.BODY, true);
				level.sendParticles(ParticleTypes.SWEEP_ATTACK, target.getX(), target.getY() + target.getBbHeight() * 0.6, target.getZ(), 1, 0.0, 0.0, 0.0,
					0.0);
				live.hurt = true;
				sound(player, SoundEvents.PLAYER_ATTACK_SWEEP, 1.0F, 1.3F);
				cooldown(player, live, G, RAKE_COOLDOWN);
			}
			case ABILITY_3 -> {
				if (!ready(player, live, V)) {
					return;
				}
				LivingEntity target = aimed(player, STALK_REACH, e -> true);
				if (target == null) {
					return;
				}
				live.stalked = target.getId();
				live.stalkUntil = level.getGameTime() + STALK_TICKS;
				target.addEffect(new MobEffectInstance(MobEffects.GLOWING, STALK_TICKS, 0, false, false), player);
				sound(player, SoundEvents.OCELOT_AMBIENT, 0.5F, 0.6F);
				cooldown(player, live, V, STALK_COOLDOWN);
			}
			case ABILITY_4 -> {
				if (ready(player, live, B)) {
					roar(player, level);
					cooldown(player, live, B, ROAR_COOLDOWN);
				}
			}
			default -> {
			}
		}
	}

	/** Every mob round you breaks off and runs. */
	private static void roar(ServerPlayer player, ServerLevel level) {
		for (LivingEntity living : around(player, ROAR_RADIUS)) {
			if (!(living instanceof Mob mob)) {
				continue;
			}
			mob.setTarget(null);
			Vec3 away = mob.position().subtract(player.position()).horizontal();
			away = away.lengthSqr() < 1.0E-4 ? new Vec3(1.0, 0.0, 0.0) : away.normalize();
			Vec3 to = mob.position().add(away.scale(ROAR_FLEE));
			mob.getNavigation().moveTo(to.x, to.y, to.z, ROAR_SPEED);
		}
		sound(player, SoundEvents.RAVAGER_ROAR, 1.6F, 1.3F);
		ring(level, ParticleTypes.CLOUD, player.position().add(0.0, 1.0, 0.0), 2.0, 16);
		com.jaymospowers.core.Shakes.heavy(player, player.position(), ROAR_RADIUS, 0.6F);
	}

	@Override
	public void tick(ServerPlayer player, XenoformPower.Live live) {
		ServerLevel level = player.level();
		if (!player.isSilent()) {
			player.setSilent(true);
		}
		// hard to see while it sneaks
		if (player.isCrouching() && player.tickCount % 10 == 0) {
			player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 20, 0, false, false));
		}
		Claws claws = claws(player);
		heal(player, claws);
		if (claws.target >= 0) {
			ride(player, live, claws);
		}
		if (live.rush > 0) {
			live.rush--;
			player.resetFallDistance();
			for (LivingEntity e : around(player, POUNCE_HIT + 1.0)) {
				if (e.getBoundingBox().inflate(POUNCE_HIT * 0.5).intersects(player.getBoundingBox())) {
					e.hurtServer(level, player.damageSources().playerAttack(player), claws.lunge ? LUNGE_DAMAGE : POUNCE_DAMAGE);
					if (claws.out && latchable(e)) {
						// claws out: onto its back
						latch(player, claws, e);
					} else if (!Tripped.bind(e, player, PIN_TICKS)) {
						e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, PIN_TICKS, 9), player);
					}
					player.setDeltaMovement(Vec3.ZERO);
					player.syncVelocity = true;
					live.hurt = true;
					live.rush = 0;
					sound(player, SoundEvents.OCELOT_HURT, 1.0F, 0.4F);
					break;
				}
			}
			if (player.onGround() && live.rush < POUNCE_TICKS - 3) {
				live.rush = 0;
			}
		}
		if (live.stalked >= 0) {
			Entity e = level.getEntity(live.stalked);
			if (!(e instanceof LivingEntity prey) || !prey.isAlive() || level.getGameTime() > live.stalkUntil) {
				live.stalked = -1;
			} else if (prey.distanceTo(player) <= STALK_NEAR && player.tickCount % 10 == 0) {
				// close on what it stalks: it strikes harder
				player.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 20, 1, false, false));
			}
		}
	}

	@Override
	public boolean onUltimate(ServerPlayer player, XenoformPower.Live live) {
		burst(player.level(), ParticleTypes.CRIT, player.position().add(0.0, 1.0, 0.0), 30, 0.8, 0.1);
		sound(player, SoundEvents.RAVAGER_ROAR, 1.2F, 1.6F);
		return true;
	}
}
