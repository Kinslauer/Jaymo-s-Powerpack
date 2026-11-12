package com.jaymospowers.power.xenoform.forms;

import com.jaymospowers.api.PowerKey;
import com.jaymospowers.core.fx.MagicFx;
import com.jaymospowers.core.wounds.Bleeding;
import com.jaymospowers.core.wounds.BodyPart;
import com.jaymospowers.core.wounds.Tripped;
import com.jaymospowers.power.darktide.DarktideStatus;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import com.jaymospowers.power.xenoform.XenoForm;
import com.jaymospowers.power.xenoform.XenoformPower;
import com.jaymospowers.power.xenoform.XenoformState;
import net.minecraft.core.particles.ParticleTypes;
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
import net.minecraft.world.entity.animal.parrot.Parrot;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * (0.18.109, Scan C) Talon, the raptor: light and long-armed, brown feathers barred paler, a white-feathered head with a
 * hooked yellow beak, broad wings folded down its arms, hooked talons. 20 health, fists of 4. Scanned from a parrot, or
 * Naturalist's vulture or songbirds ({@code XenoDna}). It flies: hold jump in the air to beat up and where you look;
 * let go and it glides; sneak and it drops. Falls never hurt it.
 * <ul>
 * <li>R, Stoop: a dive where you look; the first creature small enough is snatched in the talons and carried.</li>
 * <li>G: let go of what you carry (from high up, the fall does the rest); with nothing held, a talon strike.</li>
 * <li>V, Far Sight: every monster within {@link #SIGHT_RANGE} blocks shows through fog and walls, and you see in the
 * dark.</li>
 * <li>B, Feather Darts: a fan of hard quills.</li>
 * </ul>
 * Takedown (sneak+R, only in the air): dive onto the creature in your crosshair (else the nearest within
 * {@link #TAKEDOWN_REACH}); the further you fell, the harder it hits (capped at {@link #FELL_CAP} blocks), and it is
 * left bleeding. A creature caught in mid-air (a phantom, a bat) is crashed down into the ground, and hurt again when it
 * lands. One wingbeat when you land bowls over everything near.
 */
public final class Talon extends XenoForm {
	// ---- tuning ----
	/** Wing beats: this fast toward where you look (slower carrying), easing in; gliding falls no faster than this. */
	public static final double FLAP_SPEED = 0.8, FLAP_CARRY = 0.55, FLAP_EASE = 0.2, FLAP_LIFT = 0.12;
	public static final double GLIDE_FALL = -0.12, GLIDE_DRIFT = 0.05;
	public static final double STOOP_SPEED = 1.4, STOOP_HIT = 1.0;
	public static final int STOOP_TICKS = 25, STOOP_COOLDOWN = 120;
	public static final float STOOP_DAMAGE = 4.0F;
	/** Snatched: only creatures this narrow and this short; held at most this long. */
	public static final float GRAB_WIDTH = 1.4F, GRAB_HEIGHT = 2.2F;
	public static final int CARRY_TICKS = 160;
	public static final double STRIKE_REACH = 3.0;
	public static final float STRIKE_DAMAGE = 5.0F;
	public static final int STRIKE_COOLDOWN = 30;
	public static final double SIGHT_RANGE = 96.0;
	public static final int SIGHT_TICKS = 200, SIGHT_COOLDOWN = 600;
	public static final int DARTS = 5, DARTS_COOLDOWN = 80;
	public static final float DART_FAN = 6.0F, DART_SPEED = 2.2F;
	public static final double DART_DAMAGE = 2.5;
	/** Takedown: how far it sees a target (blocks), the dive's speed, how close counts as landing on it, its longest (ticks), the cooldown. */
	public static final double TAKEDOWN_REACH = 28.0, TAKEDOWN_SPEED = 1.7, TAKEDOWN_HIT = 1.2;
	public static final int TAKEDOWN_TICKS = 40, TAKEDOWN_COOLDOWN = 160;
	/** Its damage: a base and this much more for every block fallen (counted to the cap). */
	public static final float TAKEDOWN_BASE = 5.0F, TAKEDOWN_PER_BLOCK = 0.45F;
	public static final double FELL_CAP = 24.0;
	/** From this far fallen on, a second wound opens. */
	public static final double DEEP_CUT_FELL = 10.0;
	/** A creature caught in the air is driven down at this speed for at most this many ticks; its crash on landing: damage, plus per block it fell (to the cap), and how long it is stunned. */
	public static final double CRASH_SPEED = 2.4;
	public static final int CRASH_TICKS = 60, CRASH_STUN = 30;
	public static final float CRASH_DAMAGE = 4.0F, CRASH_PER_BLOCK = 0.25F, CRASH_CAP = 10.0F;
	/** How long it waits to land after the blow (ticks), then the wingbeat: ticks knocked down, radius, push out and up, damage. */
	public static final int LAND_TICKS = 60, BEAT_DOWN = 40;
	public static final double BEAT_RADIUS = 4.5, BEAT_OUT = 1.1, BEAT_UP = 0.45;
	public static final float BEAT_DAMAGE = 2.0F;

	/** One Talon's takedown: the target, ticks left of the dive, the highest point since it left the ground, waiting to land, and the creatures being crashed (id, with the height it was caught at). */
	private static final class Dive {
		int target = -1, ticks, landTicks, crashTicks;
		boolean diving, landing;
		double peak;
		final List<Integer> crash = new ArrayList<>();
		final Map<Integer, Double> crashFrom = new HashMap<>();
	}

	private static final Map<UUID, Dive> DIVES = new HashMap<>();

	private static Dive dive(ServerPlayer player) {
		return DIVES.computeIfAbsent(player.getUUID(), id -> new Dive());
	}

	private static boolean takingDown(ServerPlayer player) {
		Dive d = DIVES.get(player.getUUID());
		return d != null && (d.diving || d.landing);
	}

	public Talon() {
		super("talon", 0.9F, 20.0F, 4.0, 0.05, 0.0, 0.0, Items.FEATHER, 0xA0703C);
	}

	@Override
	public boolean scans(Entity creature) {
		return creature instanceof Parrot;
	}

	@Override
	public void onExit(ServerPlayer player, XenoformPower.Live live) {
		live.rush = 0;
		live.latched = -1;
		live.latchTicks = 0;
		DIVES.remove(player.getUUID());
	}

	@Override
	public boolean allowDamage(ServerPlayer player, XenoformPower.Live live, DamageSource source, float amount) {
		return !source.is(DamageTypeTags.IS_FALL);
	}

	@Override
	public void onKey(ServerPlayer player, XenoformPower.Live live, PowerKey key, boolean down) {
		if (!down) {
			return;
		}
		ServerLevel level = player.level();
		switch (key) {
			case ABILITY_1 -> {
				if (player.isShiftKeyDown() && !player.onGround() && !player.isInWater() && live.latched < 0) {
					// sneaking in the air: the takedown
					if (ready(player, live, R) && takedown(player, live)) {
						cooldown(player, live, R, TAKEDOWN_COOLDOWN);
					}
					return;
				}
				if (ready(player, live, R) && live.latched < 0) {
					Vec3 look = player.getLookAngle();
					Vec3 go = look.scale(STOOP_SPEED);
					if (player.onGround()) {
						go = go.add(0.0, 0.6, 0.0);
					}
					player.setDeltaMovement(go);
					player.syncVelocity = true;
					live.rush = STOOP_TICKS;
					sound(player, SoundEvents.PHANTOM_SWOOP, 1.0F, 1.4F);
					cooldown(player, live, R, STOOP_COOLDOWN);
				}
			}
			case ABILITY_2 -> {
				if (live.latched >= 0) {
					release(live);
					sound(player, SoundEvents.PARROT_FLY, 1.0F, 0.8F);
					return;
				}
				if (!ready(player, live, G)) {
					return;
				}
				LivingEntity target = aimed(player, STRIKE_REACH, e -> true);
				if (target != null) {
					target.hurtServer(level, player.damageSources().playerAttack(player), STRIKE_DAMAGE);
					live.hurt = true;
					sound(player, SoundEvents.PARROT_HURT, 1.0F, 0.5F);
					cooldown(player, live, G, STRIKE_COOLDOWN);
				}
			}
			case ABILITY_3 -> {
				if (ready(player, live, V)) {
					for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(SIGHT_RANGE),
						e -> e instanceof Enemy && e.isAlive())) {
						e.addEffect(new MobEffectInstance(MobEffects.GLOWING, SIGHT_TICKS, 0, false, false), player);
					}
					player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, SIGHT_TICKS + 20, 0, false, false));
					sound(player, SoundEvents.PARROT_AMBIENT, 1.0F, 0.6F);
					cooldown(player, live, V, SIGHT_COOLDOWN);
				}
			}
			case ABILITY_4 -> {
				if (ready(player, live, B)) {
					darts(player, level);
					cooldown(player, live, B, DARTS_COOLDOWN);
				}
			}
			default -> {
			}
		}
	}

	/** A fan of quills, flat across where you look. */
	private static void darts(ServerPlayer player, ServerLevel level) {
		Vec3 look = player.getLookAngle();
		for (int i = 0; i < DARTS; i++) {
			float turn = (i - (DARTS - 1) / 2.0F) * DART_FAN * net.minecraft.util.Mth.DEG_TO_RAD;
			Vec3 dir = look.yRot(turn);
			Arrow dart = new Arrow(level, player, new ItemStack(Items.ARROW), null);
			dart.pickup = AbstractArrow.Pickup.DISALLOWED;
			dart.setBaseDamage(DART_DAMAGE);
			dart.shoot(dir.x, dir.y, dir.z, DART_SPEED, 1.0F);
			level.addFreshEntity(dart);
		}
		sound(player, SoundEvents.ARROW_SHOOT, 1.0F, 1.6F);
		burst(level, ParticleTypes.CLOUD, player.getEyePosition(), 6, 0.2, 0.02);
	}

	/** The takedown begins: the target is the creature in the crosshair, else the nearest. */
	private static boolean takedown(ServerPlayer player, XenoformPower.Live live) {
		LivingEntity target = aimed(player, TAKEDOWN_REACH, e -> true);
		if (target == null) {
			double best = Double.MAX_VALUE;
			for (LivingEntity e : around(player, TAKEDOWN_REACH)) {
				double d = e.distanceToSqr(player);
				if (d < best) {
					best = d;
					target = e;
				}
			}
		}
		if (target == null) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.xenoform.talon.no_prey").withStyle(ChatFormatting.GRAY));
			return false;
		}
		Dive d = dive(player);
		d.target = target.getId();
		d.ticks = TAKEDOWN_TICKS;
		d.diving = true;
		d.landing = false;
		live.rush = 0;
		sound(player, SoundEvents.PHANTOM_SWOOP, 1.2F, 1.2F);
		return true;
	}

	/** Every tick: the dive homes on its target; the landing waits for the ground; crashed creatures are driven down. True while the takedown holds the body. */
	private boolean takedownTick(ServerPlayer player, XenoformPower.Live live) {
		Dive d = dive(player);
		ServerLevel level = player.level();
		d.peak = player.onGround() ? player.getY() : Math.max(d.peak, player.getY());
		if (!d.crash.isEmpty()) {
			crashes(player, d, level);
		}
		if (d.diving) {
			if (!(level.getEntity(d.target) instanceof LivingEntity prey) || !prey.isAlive() || --d.ticks <= 0 || player.onGround() && d.ticks < TAKEDOWN_TICKS - 3) {
				d.diving = false;
			} else {
				Vec3 to = prey.getBoundingBox().getCenter().subtract(player.getBoundingBox().getCenter());
				if (to.length() <= TAKEDOWN_HIT + prey.getBbWidth() * 0.5) {
					impact(player, live, d, prey, level);
				} else {
					player.setDeltaMovement(to.normalize().scale(TAKEDOWN_SPEED));
					player.syncVelocity = true;
				}
			}
		}
		if (d.landing) {
			if (player.onGround()) {
				d.landing = false;
				wingbeat(player, level);
			} else if (--d.landTicks <= 0) {
				d.landing = false;
			}
		}
		return d.diving || d.landing;
	}

	/** Onto the target: hurt by how far it fell, bleeding; a creature in the air is sent crashing down. */
	private void impact(ServerPlayer player, XenoformPower.Live live, Dive d, LivingEntity prey, ServerLevel level) {
		double fell = Mth.clamp(d.peak - prey.getY(), 0.0, FELL_CAP);
		prey.setInvulnerableTime(0);
		prey.hurtServer(level, player.damageSources().playerAttack(player), TAKEDOWN_BASE + TAKEDOWN_PER_BLOCK * (float)fell);
		Bleeding.open(prey, BodyPart.BODY, true);
		if (fell >= DEEP_CUT_FELL) {
			Bleeding.open(prey, BodyPart.HEAD, true);
		}
		live.hurt = true;
		Vec3 at = prey.getBoundingBox().getCenter();
		MagicFx.blast(level, at, 1.4F + (float)(fell / FELL_CAP), color);
		level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 10, 0.3, 0.3, 0.3, 0.3);
		sound(player, SoundEvents.PLAYER_ATTACK_CRIT, 1.2F, 0.6F);
		com.jaymospowers.core.Shakes.send(player, 0.5F, at);
		if (prey.isAlive() && !prey.onGround() && !prey.isInWater() && !prey.isPassenger()) {
			// caught in mid-air: down it goes
			d.crash.add(prey.getId());
			d.crashFrom.put(prey.getId(), prey.getY());
			d.crashTicks = CRASH_TICKS;
			DarktideStatus.stun(prey, CRASH_TICKS);
			prey.setDeltaMovement(0.0, -CRASH_SPEED, 0.0);
			prey.syncVelocity = true;
		}
		d.diving = false;
		d.landing = true;
		d.landTicks = LAND_TICKS;
		player.setDeltaMovement(0.0, -0.8, 0.0);
		player.syncVelocity = true;
	}

	/** The creatures crashing: driven down until they land, then hurt again. */
	private static void crashes(ServerPlayer player, Dive d, ServerLevel level) {
		d.crashTicks--;
		var it = d.crash.iterator();
		while (it.hasNext()) {
			int id = it.next();
			if (!(level.getEntity(id) instanceof LivingEntity e) || !e.isAlive() || d.crashTicks <= 0) {
				d.crashFrom.remove(id);
				it.remove();
			} else if (e.onGround() || e.isInWater()) {
				double from = d.crashFrom.remove(id);
				float damage = Math.min(CRASH_CAP, CRASH_DAMAGE + CRASH_PER_BLOCK * (float)Math.max(0.0, from - e.getY()));
				e.setInvulnerableTime(0);
				e.hurtServer(level, player.damageSources().playerAttack(player), damage);
				Bleeding.open(e, BodyPart.BODY, true);
				DarktideStatus.stun(e, CRASH_STUN);
				level.sendParticles(ParticleTypes.POOF, e.getX(), e.getY() + 0.1, e.getZ(), 12, 0.4, 0.1, 0.4, 0.05);
				sound(player, SoundEvents.GENERIC_EXPLODE.value(), 0.6F, 1.4F);
				it.remove();
			} else {
				e.setDeltaMovement(e.getDeltaMovement().x * 0.2, -CRASH_SPEED, e.getDeltaMovement().z * 0.2);
				e.syncVelocity = true;
			}
		}
	}

	/** Landed after a takedown: one beat of the wings bowls over everything near. */
	private void wingbeat(ServerPlayer player, ServerLevel level) {
		for (LivingEntity e : around(player, BEAT_RADIUS)) {
			e.hurtServer(level, player.damageSources().playerAttack(player), BEAT_DAMAGE);
			shove(e, player.position(), BEAT_OUT, BEAT_UP);
			if (!Tripped.bind(e, player, BEAT_DOWN)) {
				e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, BEAT_DOWN, 4), player);
			}
		}
		Vec3 at = player.position().add(0.0, 0.2, 0.0);
		MagicFx.ring(level, at, (float)BEAT_RADIUS, color);
		ring(level, ParticleTypes.CLOUD, at, BEAT_RADIUS * 0.6, 20);
		sound(player, SoundEvents.PHANTOM_FLAP, 1.6F, 0.6F);
		com.jaymospowers.core.Shakes.heavy(player, player.position(), BEAT_RADIUS * 2.0, 0.5F);
	}

	private static void release(XenoformPower.Live live) {
		live.latched = -1;
		live.latchTicks = 0;
	}

	/** Whether it beats its wings this tick: in the air, jump held. */
	private static boolean flapping(ServerPlayer player) {
		return !player.onGround() && player.getLastClientInput().jump() && !player.isInWater();
	}

	private static boolean gliding(ServerPlayer player) {
		return !player.onGround() && !player.getLastClientInput().shift() && !player.isInWater() && player.getDeltaMovement().y < 0.0;
	}

	@Override
	public void tick(ServerPlayer player, XenoformPower.Live live) {
		ServerLevel level = player.level();
		player.resetFallDistance();
		boolean carrying = live.latched >= 0;
		boolean takedown = takedownTick(player, live);
		if (live.rush > 0) {
			live.rush--;
			if (!carrying) {
				for (LivingEntity e : around(player, STOOP_HIT + 2.0)) {
					if (e.getBoundingBox().inflate(STOOP_HIT * 0.5).intersects(player.getBoundingBox())) {
						e.hurtServer(level, player.damageSources().playerAttack(player), STOOP_DAMAGE);
						live.hurt = true;
						live.rush = 0;
						if (e instanceof Mob && e.getBbWidth() <= GRAB_WIDTH && e.getBbHeight() <= GRAB_HEIGHT && !e.isPassenger() && !e.isVehicle()) {
							live.latched = e.getId();
							live.latchTicks = CARRY_TICKS;
							player.setDeltaMovement(player.getDeltaMovement().multiply(0.4, 0.0, 0.4).add(0.0, 0.5, 0.0));
							player.syncVelocity = true;
						}
						sound(player, SoundEvents.PARROT_HURT, 1.2F, 0.5F);
						break;
					}
				}
			}
			if (player.onGround() && live.rush < STOOP_TICKS - 3) {
				live.rush = 0;
			}
		} else if (takedown) {
			// the takedown has the body
		} else if (flapping(player)) {
			double speed = carrying ? FLAP_CARRY : FLAP_SPEED;
			Vec3 want = player.getLookAngle().scale(speed).add(0.0, FLAP_LIFT, 0.0);
			player.setDeltaMovement(player.getDeltaMovement().lerp(want, FLAP_EASE));
			player.syncVelocity = true;
			if (player.tickCount % 8 == 0) {
				sound(player, SoundEvents.PARROT_FLY, 0.8F, 0.7F);
			}
		} else if (gliding(player)) {
			Vec3 m = player.getDeltaMovement();
			Vec3 drift = player.getLookAngle().horizontal().scale(GLIDE_DRIFT);
			player.setDeltaMovement(m.x + drift.x, Math.max(m.y, GLIDE_FALL), m.z + drift.z);
			player.syncVelocity = true;
		}
		if (live.latched >= 0) {
			Entity e = level.getEntity(live.latched);
			if (!(e instanceof LivingEntity held) || !held.isAlive() || --live.latchTicks <= 0 || held.distanceTo(player) > 6.0) {
				release(live);
			} else {
				// held in the talons, hanging under you
				Vec3 under = player.position().add(0.0, -held.getBbHeight() - 0.1, 0.0);
				held.setPos(under.x, under.y, under.z);
				held.setDeltaMovement(player.getDeltaMovement());
				held.resetFallDistance();
				held.syncVelocity = true;
				if (held instanceof Mob mob) {
					mob.setTarget(null);
				}
			}
		}
	}

	@Override
	public boolean allowsFloating(ServerPlayer player, XenoformPower.Live live) {
		return !player.onGround();
	}

	@Override
	public boolean skipsMovementCheck(ServerPlayer player, XenoformPower.Live live) {
		return live.rush > 0 || flapping(player) || takingDown(player);
	}

	@Override
	public int flight(ServerPlayer player, XenoformPower.Live live) {
		if (flapping(player) || live.rush > 0 && !player.onGround()) {
			return XenoformState.FLY_WINGS;
		}
		if (takingDown(player)) {
			return XenoformState.FLY_GLIDE;
		}
		return gliding(player) ? XenoformState.FLY_GLIDE : XenoformState.FLY_NONE;
	}

	@Override
	public boolean onUltimate(ServerPlayer player, XenoformPower.Live live) {
		burst(player.level(), ParticleTypes.CLOUD, player.position().add(0.0, 1.0, 0.0), 30, 0.8, 0.05);
		sound(player, SoundEvents.PHANTOM_SWOOP, 1.6F, 0.6F);
		return true;
	}
}
