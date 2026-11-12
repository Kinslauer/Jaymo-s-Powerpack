package com.jaymospowers.power.xenoform.forms;

import com.jaymospowers.core.fx.MagicFx;
import com.jaymospowers.api.PowerKey;
import com.jaymospowers.power.xenoform.XenoForm;
import com.jaymospowers.power.xenoform.XenoformPower;
import com.jaymospowers.power.xenoform.XenoformState;
import com.jaymospowers.power.xenoform.minds.WispHand;
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
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Wisp, the ghost (scanned from a phantom): your size, only 16 health, never hurt by a fall.
 * <ul>
 * <li>R (hold), Phase: for up to {@link #PHASE_TICKS} ticks nothing solid stops it ({@code WispPhaseMixin}, both sides): it
 * walks through walls, and floats as on B, so Jump lifts it through a ceiling and sneaking sinks it through the floor (the
 * floor holds until it sneaks; bedrock and the like always hold). Nothing hurts it and it hurts nothing. Let go inside a
 * block, or run dry there, and it is put out at the nearest place it fits ({@link #EJECT_REACH}), or failing that where it
 * last stood clear; running dry costs {@link #EJECT_DAMAGE}.</li>
 * <li>G, Shroud: fade from sight until you strike something. Sneak + G, Overshadow: slip into the creature you look at
 * (within {@link #POSSESS_REACH}) and walk it as your own for {@link #POSSESS_TICKS} ticks: your keys steer it, Jump
 * jumps it, your swing is its strike; Sneak steps out. Revenant's Possession, shorter ({@code Revenant#wearTick}).</li>
 * <li>V, Dread Wail: every creature within {@link #WAIL_RADIUS} turns and flees; players are plunged into darkness. It
 * goes out ahead as a cone of rings ({@link #WAIL_CONE} long) that shoves back what it passes over and shatters glass,
 * and leaves you drained for a moment.</li>
 * <li>Sneak + B, Ghost Ray: let go for a pale bolt along your aim; hold up to {@link #RAY_CHARGE} ticks first and it is
 * wider, longer and hits harder. Not while phased.</li>
 * <li>B (hold), Float: (0.20.23) float upright where you steer, Jump rising and sneaking sinking; let go and you drop.</li>
 * <li>V while Phase is held, Ghost Hand ({@link WispHand}): phase one hand, and your next blow passes into the chest (ignores armour,
 * hits harder, a cold shudder).</li>
 * <li>Z (a tap), Thin Out: for a few seconds you take half the damage, and when struck you sink through the floor and rise behind
 * the attacker.</li>
 * <li>C (ascended), Revenant: (0.20.21) its Ultimate is its own evolved form, {@link com.jaymospowers.power.xenoform.forms.ultimate.Revenant}.</li>
 * </ul>
 */
public final class Wisp extends XenoForm {
	// ---- tuning ----
	/** Phase: how long it can be held (ticks); the cooldown after is {@link #PHASE_COOLDOWN} plus this share of the time it was held. */
	public static final int PHASE_TICKS = 120, PHASE_COOLDOWN = 30;
	public static final float PHASE_COOLDOWN_SHARE = 0.75F;
	/** Ending Phase inside a block: how far (blocks) it looks for somewhere to stand, and what running dry there costs. */
	public static final double EJECT_REACH = 3.0;
	public static final float EJECT_DAMAGE = 4.0F;
	public static final int SHROUD_COOLDOWN = 40;
	public static final int WAIL_COOLDOWN = 300, WAIL_TICKS = 60, WAIL_DARKNESS_TICKS = 100;
	public static final double WAIL_RADIUS = 10.0, WAIL_FLEE = 10.0, WAIL_FLEE_SPEED = 1.5;
	/** The wail's cone: how far it reaches ahead, how wide it opens (the least dot with your aim), its shove out and up, how many panes it can break, and how long it leaves you slowed and weak. */
	public static final double WAIL_CONE = 9.0, WAIL_CONE_DOT = 0.7, WAIL_SHOVE = 1.3, WAIL_LIFT = 0.35;
	public static final int WAIL_GLASS = 32, WAIL_DRAIN_TICKS = 50;
	public static final int WAIL_COLOR = 0xB8E8F8;
	/** Ghost Ray: ticks to a full charge; reach, half-width and damage as a bolt and fully charged; the wait after as a bolt and fully charged. */
	public static final int RAY_CHARGE = 30;
	public static final double RAY_REACH = 20.0, RAY_REACH_FULL = 32.0;
	public static final float RAY_WIDTH = 0.12F, RAY_WIDTH_FULL = 0.45F, RAY_DAMAGE = 4.0F, RAY_DAMAGE_FULL = 12.0F;
	public static final int RAY_WAIT = 20, RAY_WAIT_FULL = 70, RAY_COLOR = 0x9FF5E0;
	/** Overshadow: how far you can reach into a creature, how long you wear it, and G's cooldown after. */
	public static final double POSSESS_REACH = 5.0;
	public static final int POSSESS_TICKS = 160, POSSESS_COOLDOWN = 400;
	/** (0.20.23, Andy: "it should float upright instead of an angled glide") Float: how fast it drifts where you steer
	 * (blocks a tick; sprinting faster), how fast Jump lifts and sneaking sinks it, how fast it eases to that (share a tick). */
	public static final double FLOAT_SPEED = 0.22, FLOAT_SPRINT = 0.3, FLOAT_RISE = 0.12, FLOAT_SINK = 0.12, FLOAT_EASE = 0.25;

	/** (0.20.21) Revenant is its own evolved form now: the Ultimate move, health bar, model and kit are in {@code Revenant}. */
	private final com.jaymospowers.power.xenoform.XenoUltimate revenant = new com.jaymospowers.power.xenoform.forms.ultimate.Revenant(this);

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
		endPhase(player, live, false);
		com.jaymospowers.power.xenoform.forms.ultimate.Revenant.release(player, live, false);
		live.rayDown = -1L;
		live.glide = false;
		live.wail = 0;
		WispHand.forget(player);
	}

	/** Z tapped: thin out. */
	@Override
	public boolean onModeTap(ServerPlayer player, XenoformPower.Live live) {
		WispHand.toggleThin(player);
		return true;
	}

	@Override
	public void onKey(ServerPlayer player, XenoformPower.Live live, PowerKey key, boolean down) {
		if (key == PowerKey.ABILITY_4) {
			if (down && player.isShiftKeyDown() && !live.glide && !live.phase && live.possessed < 0) {
				// Sneak + B gathers the Ghost Ray instead of floating
				if (player.level().getGameTime() >= live.rayNext) {
					live.rayDown = player.level().getGameTime();
				}
				return;
			}
			if (!down && live.rayDown >= 0L) {
				fireRay(player, live);
				return;
			}
			live.glide = down;
			return;
		}
		if (key == PowerKey.ABILITY_1) {
			if (!down) {
				endPhase(player, live, false);
			} else if (!live.phase && ready(player, live, R)) {
				startPhase(player, live);
			}
			return;
		}
		if (!down) {
			return;
		}
		switch (key) {
			case ABILITY_2 -> {
				if (live.possessed >= 0) {
					com.jaymospowers.power.xenoform.forms.ultimate.Revenant.release(player, live, true);
				} else if (player.isShiftKeyDown() && !live.phase) {
					if (ready(player, live, G) && com.jaymospowers.power.xenoform.forms.ultimate.Revenant.possess(player, live, POSSESS_REACH)) {
						cooldown(player, live, G, POSSESS_COOLDOWN);
					}
				} else if (live.shroud) {
					unshroud(player, live, true);
				} else if (ready(player, live, G)) {
					shroud(player, live);
					cooldown(player, live, G, SHROUD_COOLDOWN);
				}
			}
			case ABILITY_3 -> {
				if (live.phase) {
					WispHand.arm(player);
				} else if (ready(player, live, V)) {
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
		WispHand.tick(player);
		// (on the ground it walks as usual until Jump lifts it off)
		if (live.phase) {
			live.phaseTicks++;
			// (a null entity asks the world itself, so Phase's own pass through blocks doesn't answer)
			if (level.noCollision(null, player.getBoundingBox())) {
				live.phaseFree = player.position();
			}
			if (player.tickCount % 2 == 0) {
				level.sendParticles(MagicFx.VOID, player.getX(), player.getY() + 0.9, player.getZ(), 1, 0.25, 0.45, 0.25, 0.0);
			}
			if (live.phaseTicks >= PHASE_TICKS || player.isSpectator()) {
				endPhase(player, live, true);
			}
		}
		if ((live.glide || live.phase) && !player.isInWater() && player.getVehicle() == null && (!player.onGround() || player.getLastClientInput().jump())) {
			floatUpright(player);
			if (player.tickCount % 3 == 0) {
				level.sendParticles(ParticleTypes.SCULK_SOUL, player.getX(), player.getY() + 0.5, player.getZ(), 1, 0.2, 0.2, 0.2, 0.0);
			}
			if (player.tickCount % 24 == 0) {
				sound(player, SoundEvents.PHANTOM_FLAP, 0.4F, 1.4F);
			}
		}
		if (live.possessed >= 0) {
			com.jaymospowers.power.xenoform.forms.ultimate.Revenant.wearTick(player, live, POSSESS_TICKS);
		}
		if (live.rayDown >= 0L) {
			if (live.phase || live.possessed >= 0) {
				live.rayDown = -1L;
			} else if (player.tickCount % 2 == 0) {
				// it gathers in the hand
				float charge = rayCharge(level, live);
				Vec3 hand = player.getEyePosition().add(player.getLookAngle().scale(0.7)).add(0.0, -0.35, 0.0);
				MagicFx.orb(level, hand, 0.08F + 0.22F * charge, RAY_COLOR, 3);
			}
		}
		if (live.shroud) {
			if (!player.hasEffect(MobEffects.INVISIBILITY)) {
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
		if (live.phase && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return false;
		}
		return !source.is(DamageTypeTags.IS_FALL);
	}

	/** Phased, it goes where the server would say nothing can. */
	@Override
	public boolean skipsMovementCheck(ServerPlayer player, XenoformPower.Live live) {
		return live.phase;
	}

	@Override
	public boolean allowsFloating(ServerPlayer player, XenoformPower.Live live) {
		return live.glide || live.phase || live.possessed >= 0;
	}

	@Override
	public int flight(ServerPlayer player, XenoformPower.Live live) {
		return (live.glide || live.phase) && !player.onGround() && !player.isInWater() && player.getVehicle() == null
			? XenoformState.FLY_FLOAT : XenoformState.FLY_NONE;
	}

	/**
	 * (0.20.23) Float: no fall, the body upright; WASD steers it (relative to where you face), Jump lifts and sneaking sinks.
	 * The speed eases toward the steer so it drifts like a ghost rather than snapping.
	 */
	private static void floatUpright(ServerPlayer player) {
		net.minecraft.world.entity.player.Input input = player.getLastClientInput();
		double forward = (input.forward() ? 1.0 : 0.0) - (input.backward() ? 1.0 : 0.0);
		double strafe = (input.left() ? 1.0 : 0.0) - (input.right() ? 1.0 : 0.0);
		double speed = input.sprint() ? FLOAT_SPRINT : FLOAT_SPEED;
		Vec3 want = Vec3.ZERO;
		if (forward != 0.0 || strafe != 0.0) {
			float yaw = player.getYRot() * net.minecraft.util.Mth.DEG_TO_RAD;
			double sin = net.minecraft.util.Mth.sin(yaw), cos = net.minecraft.util.Mth.cos(yaw);
			// facing is (-sin, cos); to its left is (cos, sin)
			want = new Vec3(-sin * forward + cos * strafe, 0.0, cos * forward + sin * strafe).normalize().scale(speed);
		}
		double up = input.jump() ? FLOAT_RISE : input.shift() ? -FLOAT_SINK : 0.0;
		Vec3 motion = player.getDeltaMovement();
		player.setDeltaMovement(new Vec3(motion.x + (want.x - motion.x) * FLOAT_EASE, up, motion.z + (want.z - motion.z) * FLOAT_EASE));
		player.syncVelocity = true;
		player.resetFallDistance();
	}

	// ---- Revenant ----

	/** (0.20.21) Revenant is its own evolved form: see {@link com.jaymospowers.power.xenoform.forms.ultimate.Revenant}. */
	@Override
	public com.jaymospowers.power.xenoform.XenoUltimate evolved() {
		return revenant;
	}

	private static void startPhase(ServerPlayer player, XenoformPower.Live live) {
		ServerLevel level = player.level();
		live.phase = true;
		live.phaseTicks = 0;
		live.phaseFree = player.position();
		player.setAttached(XenoformState.PHASE, level.getGameTime() + PHASE_TICKS);
		level.sendParticles(MagicFx.VOID, player.getX(), player.getY() + 1.0, player.getZ(), 16, 0.3, 0.5, 0.3, 0.04);
		sound(player, SoundEvents.PHANTOM_SWOOP, 0.8F, 1.5F);
	}

	/**
	 * Solid again. Caught inside a block, it is put out at the nearest place it fits, else where it last stood clear;
	 * {@code spent} (it ran dry rather than let go) and that costs it {@link #EJECT_DAMAGE}.
	 */
	public static void endPhase(ServerPlayer player, XenoformPower.Live live, boolean spent) {
		if (!live.phase) {
			return;
		}
		ServerLevel level = player.level();
		live.phase = false;
		player.removeAttached(XenoformState.PHASE);
		cooldown(player, live, R, PHASE_COOLDOWN + Math.round(live.phaseTicks * PHASE_COOLDOWN_SHARE));
		live.phaseTicks = 0;
		AABB box = player.getBoundingBox();
		if (!level.noCollision(null, box)) {
			Vec3 out = clearSpot(level, player.position(), box);
			if (out == null) {
				out = live.phaseFree;
			}
			if (out != null) {
				player.teleportTo(level, out.x, out.y, out.z, Set.of(), player.getYRot(), player.getXRot(), false);
				player.setDeltaMovement(Vec3.ZERO);
				player.syncVelocity = true;
				level.sendParticles(MagicFx.VOID, out.x, out.y + 1.0, out.z, 20, 0.3, 0.5, 0.3, 0.05);
			}
			if (spent) {
				player.hurtServer(level, player.damageSources().magic(), EJECT_DAMAGE);
			}
		}
		live.phaseFree = null;
		player.resetFallDistance();
		sound(player, SoundEvents.PHANTOM_BITE, 0.6F, 1.6F);
	}

	/** The nearest place within {@link #EJECT_REACH} of {@code from} where {@code box} fits, tried in half-block steps; null if none. */
	private static @org.jspecify.annotations.Nullable Vec3 clearSpot(ServerLevel level, Vec3 from, AABB box) {
		int steps = (int)Math.ceil(EJECT_REACH * 2.0);
		Vec3 best = null;
		double bestSqr = EJECT_REACH * EJECT_REACH + 1.0E-6;
		for (int x = -steps; x <= steps; x++) {
			for (int y = -steps; y <= steps; y++) {
				for (int z = -steps; z <= steps; z++) {
					double sqr = (x * x + y * y + z * z) * 0.25;
					if (sqr >= bestSqr) {
						continue;
					}
					Vec3 shift = new Vec3(x * 0.5, y * 0.5, z * 0.5);
					if (level.noCollision(null, box.move(shift))) {
						best = from.add(shift);
						bestSqr = sqr;
					}
				}
			}
		}
		return best;
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
		wailCone(player);
		ring(level, ParticleTypes.SCULK_SOUL, player.position().add(0.0, 1.0, 0.0), WAIL_RADIUS * 0.4, 16);
		ring(level, ParticleTypes.SOUL, player.position(), WAIL_RADIUS, 32);
		sound(player, SoundEvents.PHANTOM_SWOOP, 2.0F, 0.5F);
		sound(player, SoundEvents.WARDEN_ROAR, 0.6F, 1.8F);
		sound(player, SoundEvents.PHANTOM_AMBIENT, 2.0F, 0.6F);
		com.jaymospowers.core.Shakes.heavy(player, player.position(), WAIL_RADIUS, 1.0F);
	}

	/** The wail going out ahead: rings down the cone, everything in it shoved back, glass in it broken, and the wailer left drained. */
	private static void wailCone(ServerPlayer player) {
		ServerLevel level = player.level();
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getLookAngle();
		for (int i = 1; i <= 4; i++) {
			double d = WAIL_CONE * i / 4.0;
			MagicFx.ring(level, eye.add(look.scale(d)), look, (float)(0.5 + d * 0.45), WAIL_COLOR, 6 + i * 3);
		}
		for (LivingEntity living : around(player, WAIL_CONE)) {
			Vec3 to = living.getBoundingBox().getCenter().subtract(eye);
			if (to.lengthSqr() > 1.0E-4 && to.normalize().dot(look) >= WAIL_CONE_DOT) {
				shove(living, player.position(), WAIL_SHOVE, WAIL_LIFT);
			}
		}
		int broken = 0;
		int reach = (int)Math.ceil(WAIL_CONE);
		for (net.minecraft.core.BlockPos pos : net.minecraft.core.BlockPos.betweenClosed(player.blockPosition().offset(-reach, -reach, -reach),
			player.blockPosition().offset(reach, reach, reach))) {
			if (broken >= WAIL_GLASS) {
				break;
			}
			Vec3 to = Vec3.atCenterOf(pos).subtract(eye);
			double far = to.length();
			if (far > WAIL_CONE || far < 0.5 || to.scale(1.0 / far).dot(look) < WAIL_CONE_DOT) {
				continue;
			}
			net.minecraft.world.level.block.state.BlockState state = level.getBlockState(pos);
			if ((state.is(net.fabricmc.fabric.api.tag.convention.v2.ConventionalBlockTags.GLASS_BLOCKS)
				|| state.is(net.fabricmc.fabric.api.tag.convention.v2.ConventionalBlockTags.GLASS_PANES)) && player.mayInteract(level, pos)) {
				level.destroyBlock(pos, false, player);
				broken++;
			}
		}
		player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, WAIL_DRAIN_TICKS, 1, false, false));
		player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, WAIL_DRAIN_TICKS, 0, false, false));
	}

	/** How charged the Ghost Ray is, 0 a bolt to 1 full. */
	private static float rayCharge(ServerLevel level, XenoformPower.Live live) {
		return net.minecraft.util.Mth.clamp((level.getGameTime() - live.rayDown) / (float)RAY_CHARGE, 0.0F, 1.0F);
	}

	/** Lets the Ghost Ray go along the aim: it stops at the first block and burns every creature it passes through. */
	private static void fireRay(ServerPlayer player, XenoformPower.Live live) {
		ServerLevel level = player.level();
		float charge = rayCharge(level, live);
		live.rayDown = -1L;
		live.rayNext = level.getGameTime() + Math.round(net.minecraft.util.Mth.lerp(charge, RAY_WAIT, RAY_WAIT_FULL));
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getLookAngle();
		Vec3 end = eye.add(look.scale(net.minecraft.util.Mth.lerp(charge, RAY_REACH, RAY_REACH_FULL)));
		net.minecraft.world.phys.BlockHitResult wall = level.clip(new net.minecraft.world.level.ClipContext(eye, end,
			net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, player));
		if (wall.getType() != net.minecraft.world.phys.HitResult.Type.MISS) {
			end = wall.getLocation();
		}
		float width = net.minecraft.util.Mth.lerp(charge, RAY_WIDTH, RAY_WIDTH_FULL);
		float damage = net.minecraft.util.Mth.lerp(charge, RAY_DAMAGE, RAY_DAMAGE_FULL);
		Vec3 from = eye.add(look.scale(0.7)).add(0.0, -0.3, 0.0);
		Vec3 stop = end;
		for (LivingEntity living : level.getEntitiesOfClass(LivingEntity.class, new AABB(eye, end).inflate(width + 1.0),
			e -> e != player && e.isAlive() && !e.isSpectator() && !spared(player, e) && e.getBoundingBox().inflate(width).clip(eye, stop).isPresent())) {
			living.hurtServer(level, player.damageSources().indirectMagic(player, player), damage);
		}
		MagicFx.beam(level, from, end, width, RAY_COLOR, 6 + Math.round(charge * 6.0F));
		MagicFx.blast(level, end, 0.5F + charge, RAY_COLOR, 8);
		sound(player, SoundEvents.PHANTOM_BITE, 1.0F, 1.7F - 0.6F * charge);
		sound(player, SoundEvents.BEACON_DEACTIVATE, 0.5F, 2.0F - 0.5F * charge);
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
