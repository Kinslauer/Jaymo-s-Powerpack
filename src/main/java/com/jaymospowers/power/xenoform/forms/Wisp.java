package com.jaymospowers.power.xenoform.forms;

import com.jaymospowers.api.PowerKey;
import com.jaymospowers.power.xenoform.XenoForm;
import com.jaymospowers.power.xenoform.XenoformPower;
import com.jaymospowers.power.xenoform.XenoformState;
import java.util.Set;
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
import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Wisp, the ghost (scanned from a phantom): your size, only 16 health, never hurt by a fall.
 * <ul>
 * <li>R, Phase: slip through a wall up to {@link #PHASE_BLOCKS} thick to the air beyond.</li>
 * <li>G, Shroud: fade from sight until you strike something.</li>
 * <li>V, Dread Wail: every creature within {@link #WAIL_RADIUS} turns and flees; players are plunged into darkness.</li>
 * <li>B (hold), Glide: fall slowly and drift forward.</li>
 * <li>C (ascended), Revenant: slip into the creature you look at (within {@link #POSSESS_REACH}) and wear it for
 * {@link #POSSESS_TICKS}: it goes where you look, jumps for you, and nothing hunts you meanwhile. C again lets go.</li>
 * </ul>
 * Corrupted: the shroud flickers (you show for a moment every few seconds), and one phase in {@link #CORRUPT_STUCK}
 * leaves part of you in the wall: {@link #CORRUPT_STUCK_DAMAGE} and a reeling head.
 */
public final class Wisp extends XenoForm {
	// ---- tuning ----
	public static final int PHASE_COOLDOWN = 60, PHASE_BLOCKS = 2;
	public static final double PHASE_REACH = 3.0;
	public static final int SHROUD_COOLDOWN = 40;
	public static final int WAIL_COOLDOWN = 300, WAIL_TICKS = 60, WAIL_DARKNESS_TICKS = 100;
	public static final double WAIL_RADIUS = 10.0, WAIL_FLEE = 10.0, WAIL_FLEE_SPEED = 1.5;
	public static final double GLIDE_FALL = -0.12, GLIDE_PUSH = 0.06, GLIDE_MAX = 0.7;
	/** Revenant: how far you can reach into a creature, how long you wear it, how fast it goes for you, C's cooldown. */
	public static final double POSSESS_REACH = 8.0, POSSESS_SPEED = 0.32;
	public static final int POSSESS_TICKS = 200, POSSESS_COOLDOWN = 300;
	/** Corrupted: the shroud shows you every this many ticks for this long; one phase in this many bites. */
	public static final int CORRUPT_FLICKER_EVERY = 60, CORRUPT_FLICKER_TICKS = 10, CORRUPT_STUCK = 4;
	public static final float CORRUPT_STUCK_DAMAGE = 3.0F;

	public Wisp() {
		super("wisp", 1.0F, 16.0F, 3.0, 0.1, 0.0, 0.0, Items.PHANTOM_MEMBRANE, 0xB8E8F8);
	}

	@Override
	public boolean scans(Entity creature) {
		return creature instanceof Phantom;
	}

	@Override
	public void onExit(ServerPlayer player, XenoformPower.Live live) {
		unshroud(player, live, false);
		live.glide = false;
		live.wail = 0;
	}

	@Override
	public void onKey(ServerPlayer player, XenoformPower.Live live, PowerKey key, boolean down) {
		if (key == PowerKey.ABILITY_4) {
			live.glide = down;
			return;
		}
		if (!down) {
			return;
		}
		switch (key) {
			case ABILITY_1 -> {
				if (ready(player, live, R) && phase(player)) {
					cooldown(player, live, R, PHASE_COOLDOWN);
					if (live.corrupted && player.getRandom().nextInt(CORRUPT_STUCK) == 0) {
						player.hurtServer(player.level(), player.damageSources().inWall(), CORRUPT_STUCK_DAMAGE);
						player.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 80, 0, false, false));
						sound(player, SoundEvents.PHANTOM_HURT, 1.0F, 0.6F);
					}
				}
			}
			case ABILITY_2 -> {
				if (live.shroud) {
					unshroud(player, live, true);
				} else if (ready(player, live, G)) {
					shroud(player, live);
					cooldown(player, live, G, SHROUD_COOLDOWN);
				}
			}
			case ABILITY_3 -> {
				if (ready(player, live, V)) {
					wail(player, live);
					cooldown(player, live, V, WAIL_COOLDOWN);
				}
			}
			default -> {
			}
		}
	}

	@Override
	public void tick(ServerPlayer player, XenoformPower.Live live) {
		ServerLevel level = player.level();
		if (live.glide && !player.onGround() && !player.isInWater()) {
			Vec3 motion = player.getDeltaMovement();
			Vec3 push = player.getLookAngle().horizontal();
			push = push.lengthSqr() < 1.0E-4 ? Vec3.ZERO : push.normalize().scale(GLIDE_PUSH);
			Vec3 next = new Vec3(motion.x + push.x, Math.max(motion.y, GLIDE_FALL), motion.z + push.z);
			if (next.horizontalDistance() > GLIDE_MAX) {
				Vec3 flat = new Vec3(next.x, 0.0, next.z).normalize().scale(GLIDE_MAX);
				next = new Vec3(flat.x, next.y, flat.z);
			}
			player.setDeltaMovement(next);
			player.syncVelocity = true;
			player.resetFallDistance();
			if (player.tickCount % 3 == 0) {
				level.sendParticles(ParticleTypes.SCULK_SOUL, player.getX(), player.getY() + 0.5, player.getZ(), 1, 0.2, 0.2, 0.2, 0.0);
			}
			if (player.tickCount % 16 == 0) {
				sound(player, SoundEvents.PHANTOM_FLAP, 0.5F, 1.4F);
			}
		}
		if (live.shroud) {
			boolean flicker = live.corrupted && player.tickCount % CORRUPT_FLICKER_EVERY < CORRUPT_FLICKER_TICKS;
			if (flicker) {
				if (player.hasEffect(MobEffects.INVISIBILITY)) {
					player.removeEffect(MobEffects.INVISIBILITY);
					sound(player, SoundEvents.PHANTOM_FLAP, 0.4F, 2.0F);
				}
			} else if (!player.hasEffect(MobEffects.INVISIBILITY)) {
				player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 6000, 0, false, false));
			}
			if (player.tickCount % 5 == 0) {
				level.sendParticles(player, ParticleTypes.SCULK_SOUL, true, false, player.getX(), player.getY() + 1.0, player.getZ(), 1, 0.2, 0.3, 0.2, 0.0);
			}
		}
		if (live.wail > 0) {
			live.wail--;
			if (live.wail % 10 == 0) {
				scatter(player);
			}
		}
		// always a little unreal
		if (player.tickCount % 6 == 0) {
			level.sendParticles(ParticleTypes.SOUL, player.getX(), player.getY() + 0.3, player.getZ(), 1, 0.3, 0.1, 0.3, 0.005);
		}
	}

	/** The shroud lifts the moment you strike. */
	@Override
	public void onDealtDamage(ServerPlayer player, XenoformPower.Live live, LivingEntity target) {
		if (live.shroud) {
			unshroud(player, live, true);
		}
	}

	/** A ghost falls as it pleases. */
	@Override
	public boolean allowDamage(ServerPlayer player, XenoformPower.Live live, DamageSource source, float amount) {
		return !source.is(DamageTypeTags.IS_FALL);
	}

	@Override
	public boolean allowsFloating(ServerPlayer player, XenoformPower.Live live) {
		return live.glide || live.possessed >= 0;
	}

	@Override
	public int flight(ServerPlayer player, XenoformPower.Live live) {
		return live.glide && !player.onGround() && !player.isInWater() && player.getVehicle() == null
			? XenoformState.FLY_GLIDE : XenoformState.FLY_NONE;
	}

	// ---- Revenant ----

	@Override
	public boolean onUltimate(ServerPlayer player, XenoformPower.Live live) {
		return possess(player, live);
	}

	@Override
	public void onUltimateAgain(ServerPlayer player, XenoformPower.Live live) {
		if (live.possessed >= 0) {
			release(player, live, true);
		} else if (ultimateReady(player, live)) {
			possess(player, live);
		}
	}

	@Override
	public void tickUltimate(ServerPlayer player, XenoformPower.Live live) {
		if (live.possessed < 0) {
			return;
		}
		ServerLevel level = player.level();
		if (!(level.getEntity(live.possessed) instanceof Mob host) || !host.isAlive() || player.getVehicle() != host
			|| host.tickCount - hostSince(host) > POSSESS_TICKS) {
			release(player, live, true);
			return;
		}
		// it goes where you look
		Vec3 look = player.getLookAngle().horizontal();
		look = look.lengthSqr() < 1.0E-4 ? Vec3.ZERO : look.normalize().scale(POSSESS_SPEED * (player.getXRot() > 60.0F ? 0.0 : 1.0));
		Vec3 motion = host.getDeltaMovement();
		double up = motion.y;
		if (host.horizontalCollision && host.onGround()) {
			up = 0.42;
		}
		if (host.isInWater()) {
			up = Math.max(up, 0.1);
		}
		host.setDeltaMovement(look.x, up, look.z);
		host.syncVelocity = true;
		host.setYRot(player.getYRot());
		host.setYBodyRot(player.getYRot());
		host.setYHeadRot(player.getYRot());
		host.setTarget(null);
		player.resetFallDistance();
		host.resetFallDistance();
		if (player.tickCount % 10 == 0) {
			for (Mob mob : level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(24.0), m -> m.getTarget() == player)) {
				mob.setTarget(null);
			}
			if (!player.hasEffect(MobEffects.INVISIBILITY)) {
				player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 40, 0, false, false));
			}
		}
		if (player.tickCount % 4 == 0) {
			level.sendParticles(ParticleTypes.SCULK_SOUL, host.getX(), host.getY() + host.getBbHeight() * 0.5, host.getZ(), 1, 0.3, 0.3, 0.3, 0.0);
		}
	}

	@Override
	public void onUltimateEnd(ServerPlayer player, XenoformPower.Live live) {
		release(player, live, false);
	}

	private static final String SINCE_TAG = "jaymospowers_xenoform_possessed_at:";

	private static int hostSince(Mob host) {
		for (String tag : host.entityTags()) {
			if (tag.startsWith(SINCE_TAG)) {
				try {
					return Integer.parseInt(tag.substring(SINCE_TAG.length()));
				} catch (NumberFormatException ignored) {
					return host.tickCount;
				}
			}
		}
		return host.tickCount;
	}

	private static boolean possess(ServerPlayer player, XenoformPower.Live live) {
		LivingEntity target = aimed(player, POSSESS_REACH, e -> e instanceof Mob && !(e instanceof Player) && !e.entityTags().contains(com.jaymospowers.power.xenoform.XenoformState.MINION_TAG));
		if (!(target instanceof Mob host) || host.isVehicle()) {
			return false;
		}
		ServerLevel level = player.level();
		Vec3 from = player.position();
		if (!player.startRiding(host, true, true)) {
			return false;
		}
		live.possessed = host.getId();
		host.setNoAi(true);
		host.setTarget(null);
		host.addTag(SINCE_TAG + host.tickCount);
		player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 40, 0, false, false));
		level.sendParticles(ParticleTypes.SCULK_SOUL, from.x, from.y + 1.0, from.z, 24, 0.3, 0.6, 0.3, 0.05);
		level.sendParticles(ParticleTypes.SOUL, host.getX(), host.getY() + host.getBbHeight() * 0.5, host.getZ(), 24, 0.4, 0.5, 0.4, 0.05);
		sound(player, SoundEvents.PHANTOM_DEATH, 1.0F, 1.5F);
		sound(player, SoundEvents.WARDEN_HEARTBEAT, 1.0F, 1.2F);
		ultimateCooldown(player, live, POSSESS_COOLDOWN);
		return true;
	}

	/** Out of the creature: it comes to, dazed and marked; you stand beside it. */
	private static void release(ServerPlayer player, XenoformPower.Live live, boolean fx) {
		if (live.possessed < 0) {
			return;
		}
		ServerLevel level = player.level();
		Entity e = level.getEntity(live.possessed);
		live.possessed = -1;
		if (player.getVehicle() == e) {
			player.stopRiding();
		}
		if (e instanceof Mob host) {
			host.setNoAi(false);
			host.entityTags().removeIf(t -> t.startsWith(SINCE_TAG));
			if (host.isAlive()) {
				host.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 200, 1));
				host.addEffect(new MobEffectInstance(MobEffects.GLOWING, 200, 0));
				host.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 2));
			}
		}
		if (!live.shroud) {
			player.removeEffect(MobEffects.INVISIBILITY);
		}
		if (fx) {
			level.sendParticles(ParticleTypes.SOUL, player.getX(), player.getY() + 1.0, player.getZ(), 16, 0.3, 0.5, 0.3, 0.03);
			sound(player, SoundEvents.PHANTOM_SWOOP, 1.0F, 0.8F);
		}
	}

	/** Through the wall ahead: the first place beyond it, within {@link #PHASE_BLOCKS} of its face, where you fit. */
	private static boolean phase(ServerPlayer player) {
		ServerLevel level = player.level();
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getLookAngle();
		BlockHitResult hit = level.clip(new ClipContext(eye, eye.add(look.scale(PHASE_REACH)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
		if (hit.getType() != HitResult.Type.BLOCK) {
			return false;
		}
		Vec3 face = hit.getLocation();
		AABB box = player.getBoundingBox();
		for (double d = 0.6; d <= PHASE_BLOCKS + 0.6; d += 0.25) {
			Vec3 point = face.add(look.scale(d));
			for (int drop = 0; drop <= 1; drop++) {
				Vec3 feet = new Vec3(point.x, point.y - player.getEyeHeight() - drop, point.z);
				AABB moved = box.move(feet.subtract(player.position()));
				if (level.noCollision(player, moved)) {
					Vec3 from = player.position();
					player.teleportTo(level, feet.x, feet.y, feet.z, Set.of(), player.getYRot(), player.getXRot(), false);
					player.resetFallDistance();
					level.sendParticles(ParticleTypes.REVERSE_PORTAL, from.x, from.y + 1.0, from.z, 20, 0.3, 0.5, 0.3, 0.05);
					level.sendParticles(ParticleTypes.REVERSE_PORTAL, feet.x, feet.y + 1.0, feet.z, 20, 0.3, 0.5, 0.3, 0.05);
					sound(player, SoundEvents.PHANTOM_SWOOP, 1.0F, 1.3F);
					return true;
				}
			}
		}
		return false;
	}

	private static void shroud(ServerPlayer player, XenoformPower.Live live) {
		live.shroud = true;
		player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 6000, 0, false, false));
		// whoever was after you loses the thread
		for (Mob mob : player.level().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(24.0), m -> m.getTarget() == player)) {
			mob.setTarget(null);
		}
		ServerLevel level = player.level();
		level.sendParticles(ParticleTypes.SCULK_SOUL, player.getX(), player.getY() + 1.0, player.getZ(), 20, 0.3, 0.6, 0.3, 0.02);
		sound(player, SoundEvents.PHANTOM_AMBIENT, 0.8F, 1.6F);
	}

	private static void unshroud(ServerPlayer player, XenoformPower.Live live, boolean fx) {
		if (!live.shroud) {
			return;
		}
		live.shroud = false;
		player.removeEffect(MobEffects.INVISIBILITY);
		if (fx) {
			player.level().sendParticles(ParticleTypes.SOUL, player.getX(), player.getY() + 1.0, player.getZ(), 12, 0.3, 0.5, 0.3, 0.02);
			sound(player, SoundEvents.PHANTOM_BITE, 0.8F, 1.4F);
		}
	}

	private static void wail(ServerPlayer player, XenoformPower.Live live) {
		live.wail = WAIL_TICKS;
		ServerLevel level = player.level();
		scatter(player);
		for (LivingEntity living : around(player, WAIL_RADIUS)) {
			if (living instanceof Player) {
				living.addEffect(new MobEffectInstance(MobEffects.DARKNESS, WAIL_DARKNESS_TICKS, 0));
				living.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 60, 0));
			}
		}
		ring(level, ParticleTypes.SCULK_SOUL, player.position().add(0.0, 1.0, 0.0), WAIL_RADIUS * 0.4, 16);
		ring(level, ParticleTypes.SOUL, player.position(), WAIL_RADIUS, 32);
		sound(player, SoundEvents.PHANTOM_SWOOP, 2.0F, 0.5F);
		sound(player, SoundEvents.WARDEN_ROAR, 0.6F, 1.8F);
		sound(player, SoundEvents.PHANTOM_AMBIENT, 2.0F, 0.6F);
		com.jaymospowers.core.Shakes.heavy(player, player.position(), WAIL_RADIUS, 1.0F);
	}

	/** Every mob within reach drops what it was doing and runs. */
	private static void scatter(ServerPlayer player) {
		for (LivingEntity living : around(player, WAIL_RADIUS)) {
			if (!(living instanceof Mob mob)) {
				continue;
			}
			mob.setTarget(null);
			Vec3 away = mob.position().subtract(player.position()).horizontal();
			away = away.lengthSqr() < 1.0E-4 ? new Vec3(1.0, 0.0, 0.0) : away.normalize();
			Vec3 to = mob.position().add(away.scale(WAIL_FLEE));
			mob.getNavigation().moveTo(to.x, to.y, to.z, WAIL_FLEE_SPEED);
		}
	}
}
