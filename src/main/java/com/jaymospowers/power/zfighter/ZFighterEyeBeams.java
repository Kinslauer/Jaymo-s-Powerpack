package com.jaymospowers.power.zfighter;

import com.jaymospowers.core.LockOn;
import com.jaymospowers.core.Soundscape;
import com.jaymospowers.core.fx.MagicFx;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Eye Beams, a charged technique (sneak + V, held; bought with TP like the others). Hold it and two thin beams leave the
 * eyes ({@link MagicFx#beam}) wherever he aims (the locked foe, else the look), hurting what they touch every
 * {@link #HIT_EVERY} ticks and costing {@link #KI_PER_TICK} ki a tick. They run on a <b>heat gauge</b>: every tick of beam
 * adds {@link #HEAT_PER_TICK}; at {@link #HEAT_MAX} the eyes overheat and the beam stops until the gauge has cooled to
 * {@link #RESUME_AT} (it cools by {@link #COOL_PER_TICK} a tick, {@link #COOL_DELAY} ticks after the last beam). The gauge is
 * shown in the action bar. Variants, none needing a key of their own:
 * <ul>
 * <li><b>Blinding shot</b>: let go of V within {@link #TAP_TICKS} of starting. One bright flash from the eyes blinds
 * whatever it strikes for {@link #BLIND_TICKS} and makes a mob lose track of him.</li>
 * <li><b>Spin</b>: R while the beams are held. {@link #SPIN_BEAMS} beams sweep all round him for {@link #SPIN_TICKS} (a
 * ring of damage at {@link #SPIN_RANGE}), at a heat cost of {@link #SPIN_HEAT}, once per {@link #SPIN_COOLDOWN}.</li>
 * <li><b>Cold</b> (a Frost-born's, automatically): the beams are pale blue, and a foe they stay on for about 6 s freezes
 * ({@link ZFighterChill}).</li>
 * <li><b>Held face</b>: with someone in his Z Fighter grab ({@link ZFighterGrab}), the beams go for the held foe's eyes and keep
 * them there, harder ({@link #HELD_SHARE}).</li>
 * </ul>
 */
final class ZFighterEyeBeams {
	static final float MIN_KI = 6.0F, MAX_KI = 10.0F;
	/** The ticks of gathering the bar reads as a full charge (it only scales the cost of letting go). */
	static final int FULL_TICKS = 40;
	/** V's cooldown after he lets go. */
	static final int COOLDOWN = 50;

	static final double RANGE = 40.0;
	static final float DAMAGE = 1.5F;
	static final int HIT_EVERY = 4;
	static final float KI_PER_TICK = 0.45F;
	static final float RADIUS = 0.05F;
	static final int BEAM_TICKS = 2;
	/** Where the eyes are (out along the look, to each side, blocks). */
	static final double EYE_OUT = 0.3, EYE_SIDE = 0.11;
	static final float POISE_WEAR = 1.5F;

	// ---- the heat gauge ----
	static final float HEAT_MAX = 100.0F, HEAT_PER_TICK = 1.1F, COOL_PER_TICK = 1.4F, RESUME_AT = 35.0F;
	static final int COOL_DELAY = 10;

	// ---- the blinding shot ----
	static final int TAP_TICKS = 8, BLIND_TICKS = 100;
	static final double BLIND_RANGE = 28.0;
	static final float BLIND_HEAT = 20.0F;

	// ---- the spin ----
	static final int SPIN_TICKS = 24, SPIN_BEAMS = 6, SPIN_COOLDOWN = 100;
	static final float SPIN_STEP_DEGREES = 3.0F;
	static final double SPIN_RANGE = 14.0;
	static final float SPIN_HEAT = 28.0F, SPIN_SHARE = 0.6F;

	// ---- the held face ----
	static final float HELD_SHARE = 1.6F;

	static final int BEAM_COLOR = 0xFF5030;

	private static final Soundscape.Layer HUM = Soundscape.Layer.of(0.35F, 0.5F, 1.6F, 2.0F, SoundEvents.BEACON_AMBIENT);

	private static final class Eyes {
		float heat;
		boolean over;
		long first = -100L, last = -100L, lastHit, spinUntil, spinReadyAt;
		float spinPhase;
		final ZFighterChill chill = new ZFighterChill();
	}

	private static final Map<UUID, Eyes> EYES = new HashMap<>();

	private ZFighterEyeBeams() {
	}

	static void init() {
		ServerTickEvents.END_SERVER_TICK.register(ZFighterEyeBeams::tick);
	}

	static void clear() {
		EYES.clear();
	}

	/** A lang key saying why the eyes can't light now (overheated), or null. */
	static @Nullable String blocker(ServerPlayer player) {
		Eyes eyes = EYES.get(player.getUUID());
		return eyes != null && eyes.over ? "message.jaymospowers.zfighter_gamma.eyes_hot" : null;
	}

	/** Every tick: heat drains away from eyes that aren't beaming. */
	private static void tick(MinecraftServer server) {
		for (Iterator<Map.Entry<UUID, Eyes>> it = EYES.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<UUID, Eyes> entry = it.next();
			ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
			Eyes eyes = entry.getValue();
			if (player == null || !player.isAlive() || !com.jaymospowers.core.Powers.has(player, ZFighterPower.INSTANCE)) {
				it.remove();
				continue;
			}
			long now = player.level().getGameTime();
			if (now - eyes.last <= COOL_DELAY) {
				continue;
			}
			eyes.heat = Math.max(0.0F, eyes.heat - COOL_PER_TICK);
			eyes.chill.reset();
			if (eyes.over && eyes.heat <= RESUME_AT) {
				eyes.over = false;
				player.sendOverlayMessage(Component.translatable("message.jaymospowers.zfighter_gamma.eyes_cool").withStyle(ChatFormatting.AQUA));
			}
			if (eyes.heat <= 0.0F && !eyes.over && now >= eyes.spinReadyAt) {
				it.remove();
			}
		}
	}

	/** R while the beams are held: the spin. True if the press was this move's (so R does nothing else). */
	static boolean spin(ServerPlayer player) {
		ZFighterPower.Live live = ZFighterPower.live(player);
		if (live.waveFrom < 0 || ZFighterTraining.of(player).charged() != ZFighterTraining.EYE_BEAMS) {
			return false;
		}
		Eyes eyes = EYES.get(player.getUUID());
		long now = player.level().getGameTime();
		if (eyes == null || eyes.over || now - eyes.first < TAP_TICKS || now < eyes.spinReadyAt || now < eyes.spinUntil) {
			return true;
		}
		eyes.spinUntil = now + SPIN_TICKS;
		eyes.spinReadyAt = now + SPIN_COOLDOWN;
		eyes.spinPhase = 0.0F;
		eyes.heat += SPIN_HEAT;
		player.level().playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.BREEZE_SHOOT, SoundSource.PLAYERS, 0.9F, 1.5F);
		return true;
	}

	private static int color(ServerPlayer player, ZFighterPower.Live live) {
		return live.lineage == ZFighterLineage.FROST ? ZFighterChill.COLOR : BEAM_COLOR;
	}

	/** While V is held: the beams, the heat, the gauge. */
	static void gather(ServerPlayer player, ZFighterPower.Live live, float charge) {
		ServerLevel level = player.level();
		long now = level.getGameTime();
		Eyes eyes = EYES.computeIfAbsent(player.getUUID(), id -> new Eyes());
		if (now - eyes.last > 2) {
			eyes.first = now;
		}
		eyes.last = now;
		boolean free = player.isCreative() || live.raging();
		if (eyes.over || !free && charge <= 0.0F) {
			return;
		}
		Vec3 eye = player.getEyePosition();
		int color = color(player, live);
		if (now - eyes.first < TAP_TICKS) {
			// the eyes brighten: a quick let-go from here is the blinding shot
			if (now % 2 == 0) {
				MagicFx.blast(level, eye.add(player.getViewVector(1.0F).scale(EYE_OUT)), 0.35F, 0xFFFFFF, 6);
			}
			return;
		}
		eyes.heat += HEAT_PER_TICK;
		if (eyes.heat >= HEAT_MAX) {
			eyes.heat = HEAT_MAX;
			eyes.over = true;
			MagicFx.blast(level, eye.add(player.getViewVector(1.0F).scale(0.5)), 0.8F, 0x808080);
			level.playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 1.0F, 1.4F);
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.zfighter_gamma.eyes_hot").withStyle(ChatFormatting.RED));
			return;
		}
		if (!free) {
			live.ki = Math.max(0.0F, live.ki - KI_PER_TICK * ZFighterAndroid.costFactor(player));
		}
		float power = ZFighterPower.kiPower(player, live);
		boolean cold = live.lineage == ZFighterLineage.FROST;
		LivingEntity held = ZFighterGrab.foe(player);
		LivingEntity struck = null;
		if (now < eyes.spinUntil) {
			spinBeams(player, eyes, eye, color, power, now);
		} else if (held != null && held.isAlive()) {
			Vec3 face = held.getEyePosition();
			eyeBeams(level, player, eye, face, color);
			struck = held;
			if (now - eyes.lastHit >= HIT_EVERY) {
				eyes.lastHit = now;
				hit(level, player, held, DAMAGE * HELD_SHARE * power);
			}
		} else {
			Vec3 dir = LockOn.aim(player);
			Strike strike = trace(player, eye, dir, RANGE);
			eyeBeams(level, player, eye, strike.end, color);
			struck = strike.target;
			if (struck != null && now - eyes.lastHit >= HIT_EVERY) {
				eyes.lastHit = now;
				hit(level, player, struck, DAMAGE * power);
			}
			if (strike.target == null && strike.wall && now % 3 == 0) {
				level.sendParticles(MagicFx.spark(color, 0.8F), true, false, strike.end.x, strike.end.y, strike.end.z, 2, 0.1, 0.1, 0.1, 0.1);
			}
		}
		if (cold && eyes.chill.tick(level, now < eyes.spinUntil ? null : struck)) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.zfighter_gamma.frozen").withStyle(ChatFormatting.AQUA));
		}
		if (Soundscape.due(player, "eyes.hum", 6, 10)) {
			Soundscape.play(level, eye, SoundSource.PLAYERS, HUM);
		}
		if (now % 4 == 0) {
			gauge(player, eyes, cold);
		}
	}

	private static void gauge(ServerPlayer player, Eyes eyes, boolean cold) {
		int filled = Mth.clamp(Math.round(eyes.heat / HEAT_MAX * 10.0F), 0, 10);
		String bar = "[" + "|".repeat(filled) + ".".repeat(10 - filled) + "]";
		if (cold && eyes.chill.progress() > 0.0F) {
			bar += " " + Math.round(eyes.chill.progress() * 100.0F) + "%";
		}
		player.sendOverlayMessage(Component.literal(bar).withStyle(eyes.heat > 75.0F ? ChatFormatting.RED : cold ? ChatFormatting.AQUA : ChatFormatting.GOLD));
	}

	/** One tick of the spin: a ring of beams, turned a little further each tick. */
	private static void spinBeams(ServerPlayer player, Eyes eyes, Vec3 eye, int color, float power, long now) {
		ServerLevel level = player.level();
		float base = player.getYRot() + eyes.spinPhase;
		eyes.spinPhase += SPIN_STEP_DEGREES;
		for (int i = 0; i < SPIN_BEAMS; i++) {
			float yaw = base + i * 360.0F / SPIN_BEAMS;
			Vec3 dir = Vec3.directionFromRotation(-4.0F, yaw);
			Strike strike = trace(player, eye, dir, SPIN_RANGE);
			MagicFx.beam(level, eye.add(dir.scale(EYE_OUT)), strike.end, RADIUS * 1.4F, color, BEAM_TICKS);
			if (strike.target != null && now % HIT_EVERY == i % HIT_EVERY) {
				hit(level, player, strike.target, DAMAGE * SPIN_SHARE * power);
			}
		}
	}

	/** Both eyes' beams to {@code to}. */
	private static void eyeBeams(ServerLevel level, ServerPlayer player, Vec3 eye, Vec3 to, int color) {
		Vec3 look = player.getViewVector(1.0F);
		Vec3 right = look.cross(new Vec3(0.0, 1.0, 0.0));
		right = right.lengthSqr() < 1.0E-6 ? Vec3.directionFromRotation(0.0F, player.getYRot() + 90.0F) : right.normalize();
		for (int side = -1; side <= 1; side += 2) {
			Vec3 from = eye.add(look.scale(EYE_OUT)).add(right.scale(EYE_SIDE * side));
			MagicFx.beam(level, from, to, RADIUS, color, BEAM_TICKS);
		}
	}

	private static void hit(ServerLevel level, ServerPlayer player, LivingEntity foe, float damage) {
		foe.setInvulnerableTime(0);
		foe.hurtServer(level, ZFighterKi.source(player), damage);
		ZFighterPoise.wear(foe, POISE_WEAR);
	}

	/** Where a line from {@code from} along {@code dir} ends: the first block, or the first living thing in the way. */
	private record Strike(Vec3 end, @Nullable LivingEntity target, boolean wall) {
	}

	private static Strike trace(ServerPlayer player, Vec3 from, Vec3 dir, double range) {
		ServerLevel level = player.level();
		HitResult wall = level.clip(new ClipContext(from, from.add(dir.scale(range)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
		double reach = wall.getType() == HitResult.Type.MISS ? range : wall.getLocation().distanceTo(from);
		Vec3 end = from.add(dir.scale(reach));
		EntityHitResult hit = ProjectileUtil.getEntityHitResult(player, from, end, player.getBoundingBox().expandTowards(dir.scale(reach)).inflate(1.0),
			e -> e instanceof LivingEntity living && ZFighterArts.valid(player, living) && e.isPickable(), reach * reach);
		if (hit != null) {
			return new Strike(hit.getLocation(), (LivingEntity)hit.getEntity(), false);
		}
		return new Strike(end, null, wall.getType() != HitResult.Type.MISS);
	}

	/** V let go with the ki paid: the blinding shot if it was a tap; otherwise the beams just end. */
	static void release(ServerPlayer player, ZFighterPower.Live live, float charge) {
		Eyes eyes = EYES.get(player.getUUID());
		ServerLevel level = player.level();
		long now = level.getGameTime();
		boolean tap = eyes != null && now - eyes.first <= TAP_TICKS + 1;
		if (eyes != null) {
			eyes.last = now - 2;
			eyes.chill.reset();
		}
		if (!tap) {
			return;
		}
		eyes.heat += BLIND_HEAT;
		Vec3 eye = player.getEyePosition();
		Strike strike = trace(player, eye, LockOn.aim(player), BLIND_RANGE);
		Vec3 at = strike.end;
		MagicFx.beam(level, eye.add(player.getViewVector(1.0F).scale(EYE_OUT)), at, RADIUS * 3.0F, 0xFFFFFF, 6);
		MagicFx.blast(level, at, 2.0F, 0xFFFFFF, 10);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.FIREWORK_ROCKET_BLAST, SoundSource.PLAYERS, 1.0F, 1.6F);
		level.playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.0F, 1.9F);
		LivingEntity foe = strike.target;
		if (foe != null) {
			foe.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, BLIND_TICKS, 0), player);
			if (foe instanceof Mob mob) {
				mob.setTarget(null);
			}
		}
	}
}
