package com.jaymospowers.power.chrono;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.PickKit;
import com.jaymospowers.core.fx.MagicFx;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * A speedster's momentum (Overclock and Velox's Velocity both; Andy's Awakening pick "Speedster", folded in here).
 * <ul>
 * <li><b>Momentum in gears:</b> the wheel no longer puts you in a gear, it sets your <b>top gear</b> ({@link #TOP}). You
 * start a run in gear {@link #START_GEAR} (or the top gear, if that is lower) and momentum builds only above it: sprinting
 * at speed carries you up a gear every {@link #BUILD_TICKS} ticks, to the top gear; stop and you lose one every
 * {@link #FALL_TICKS}, down to the gear a run starts in.</li>
 * <li><b>The blur:</b> in gear {@link ChronoGears#GEARS} at speed you are a {@link #BLUR}: arrows and every other shot pass
 * through you ({@code ChronoShotPassMixin}; a held phase does the same), and sneak held at a wall in your way opens it as
 * a tunnel ({@link ChronoTunnel}).</li>
 * <li><b>Static:</b> running charges you ({@link #STATIC_PER_TICK}, by gear). Use with an empty hand and nothing to catch
 * or snatch hurls it all as a bolt down your sight; it arcs on to those beside the one it strikes.</li>
 * </ul>
 */
public final class ChronoMomentum {
	// ---- tuning ----
	/** The gear a run starts in, as Overclock always ran; a top gear at or under it is reached at once, only those above are climbed to. */
	static final int START_GEAR = 3;
	/** Ticks of sprinting at speed that carry you up a gear, and ticks off speed that lose one. */
	static final int BUILD_TICKS = 8, FALL_TICKS = 10;
	/** The blur stays this long (ticks) after you drop off speed, so a wall you hit still opens. */
	static final int BLUR_GRACE = 8;
	/** Static: charge a tick at speed for each gear (1 is full), what leaks a tick standing, the least that can be thrown. */
	static final float[] STATIC_PER_TICK = {0.002F, 0.003F, 0.005F, 0.008F, 0.0125F};
	static final float STATIC_LEAK = 0.001F, STATIC_MIN = 0.25F;
	/** The bolt: reach, harm at a full charge, how many more it arcs to and how far, the share they take, the daze (ticks), the wait between bolts. */
	static final double STATIC_REACH = 24.0, ARC_REACH = 5.0;
	static final float STATIC_DAMAGE = 10.0F, ARC_SHARE = 0.5F;
	static final int ARCS = 2, DAZE_TICKS = 30, STATIC_GAP = 10;
	/** The Slowness level a static bolt leaves on what it shocks. */
	static final int DAZE_LEVEL = 3;
	/** The static's own colours (Chrono's, Velox's); a Speed Prism's colour comes first ({@link SpeedPrism}). */
	static final int GOLD = 0xFFD070, SKY = 0x8FD8FF;

	/** The top gear the wheel has set, 1 to 5 (the owner's HUD). */
	public static final AttachmentType<Integer> TOP = AttachmentRegistry.create(JaymosPowers.id("chrono_top_gear"),
		builder -> builder.syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.targetOnly()));
	/** On a runner in top gear at speed: shots pass through (both sides). */
	public static final AttachmentType<Boolean> BLUR = AttachmentRegistry.create(JaymosPowers.id("chrono_blur"),
		builder -> builder.syncWith(ByteBufCodecs.BOOL, AttachmentSyncPredicate.all()));

	private static final class Run {
		int built, lost, blurLeft, nextBolt;
		float charge;
	}

	private static final Map<UUID, Run> RUNS = new HashMap<>();

	private ChronoMomentum() {
	}

	/** The top gear {@code player}'s wheel has set. */
	public static int top(Player player) {
		Integer top = player.getAttached(TOP);
		return top == null ? ChronoGears.DEFAULT_GEAR : Mth.clamp(top, 1, ChronoGears.GEARS);
	}

	/** The wheel sets a new top gear; a gear above it is dropped at once. */
	static void top(ServerPlayer player, int top) {
		player.setAttached(TOP, top);
		if (ChronoGears.gear(player) > top) {
			player.setAttached(ChronoGears.GEAR, top);
		}
	}

	/** Whether shots pass through {@code player}: a blur, or phasing. On either side. */
	public static boolean shotsPass(Player player) {
		return player.getAttached(BLUR) != null || player.getAttached(ChronoPhase.PHASING) != null;
	}

	/** Each tick (server), before {@link ChronoGears#tick}: the gear follows the momentum, the blur and the static charge follow the gear. */
	static void tick(ServerPlayer player, ServerLevel level, boolean overclocked, int palette) {
		if (!overclocked) {
			clear(player);
			return;
		}
		Run run = RUNS.computeIfAbsent(player.getUUID(), id -> new Run());
		if (player.getAttached(TOP) == null) {
			player.setAttached(TOP, ChronoGears.DEFAULT_GEAR);
		}
		int top = top(player), start = Math.min(top, START_GEAR);
		// a run starts in the starting gear (or the top gear, if the wheel is set under it); momentum builds only above it
		if (player.getAttached(ChronoGears.GEAR) == null || ChronoGears.gear(player) < start) {
			player.setAttached(ChronoGears.GEAR, start);
		}
		int gear = ChronoGears.gear(player);
		boolean atSpeed = player.isSprinting() && !player.isPassenger() && ChronoRapid.speed(player) >= ChronoGears.SHIFT_MIN_SPEED;
		if (atSpeed) {
			run.lost = 0;
			if (gear < top && ++run.built >= BUILD_TICKS) {
				run.built = 0;
				gear++;
				player.setAttached(ChronoGears.GEAR, gear);
				level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.5F, 0.6F + gear * 0.25F);
			}
			run.charge = Math.min(1.0F, run.charge + STATIC_PER_TICK[gear - 1]);
		} else {
			run.built = 0;
			if (gear > start && ++run.lost >= FALL_TICKS) {
				run.lost = 0;
				gear--;
				player.setAttached(ChronoGears.GEAR, gear);
			}
			run.charge = Math.max(0.0F, run.charge - STATIC_LEAK);
		}
		if (atSpeed && gear >= ChronoGears.GEARS) {
			run.blurLeft = BLUR_GRACE;
		} else if (run.blurLeft > 0) {
			run.blurLeft--;
		}
		boolean blur = run.blurLeft > 0;
		if (blur != (player.getAttached(BLUR) != null)) {
			if (blur) {
				player.setAttached(BLUR, true);
			} else {
				player.removeAttached(BLUR);
			}
		}
		if (blur && player.isSprinting()) {
			ChronoTunnel.run(player, level, SpeedPrism.colour(player, palette == ChronoSpeed.SKY ? SKY : GOLD));
		}
		ChronoMeters.set(player, ChronoMeters.STATIC, run.charge > 0.0F ? run.charge : -1.0F);
		if (run.charge >= STATIC_MIN && player.tickCount % 6 == 0) {
			level.sendParticles(MagicFx.spark(SpeedPrism.colour(player, palette == ChronoSpeed.SKY ? SKY : GOLD), 0.6F), player.getX(), player.getY() + 0.9, player.getZ(),
				1 + (int) (run.charge * 3.0F), 0.3, 0.5, 0.3, 0.0);
		}
	}

	/** Takes it all off (Overclock ended, power lost). */
	static void clear(ServerPlayer player) {
		RUNS.remove(player.getUUID());
		if (player.getAttached(TOP) != null) {
			player.removeAttached(TOP);
		}
		if (player.getAttached(BLUR) != null) {
			player.removeAttached(BLUR);
		}
		ChronoMeters.set(player, ChronoMeters.STATIC, -1.0F);
	}

	/**
	 * Use at speed with nothing to catch or snatch ({@link ChronoRapid}): with an empty hand and enough charge, all of it
	 * goes down the sight as a bolt. True if a bolt went.
	 */
	static boolean hurl(ServerPlayer player, ServerLevel level) {
		Run run = RUNS.get(player.getUUID());
		if (run == null || run.charge < STATIC_MIN || run.nextBolt > player.tickCount || !player.getMainHandItem().isEmpty()) {
			return false;
		}
		float charge = run.charge;
		run.charge = 0.0F;
		run.nextBolt = player.tickCount + STATIC_GAP;
		int color = SpeedPrism.colour(player, ChronoSpeed.velocity(player) ? SKY : GOLD);
		Vec3 eye = player.getEyePosition(), look = player.getLookAngle();
		Vec3 end = eye.add(look.scale(STATIC_REACH));
		HitResult wall = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
		if (wall.getType() != HitResult.Type.MISS) {
			end = wall.getLocation();
		}
		LivingEntity struck = null;
		double best = Double.MAX_VALUE;
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(eye, end).inflate(1.0), e -> e != player && e.isAlive() && !e.isSpectator())) {
			var hit = e.getBoundingBox().inflate(0.3).clip(eye, end);
			if (hit.isPresent() && hit.get().distanceToSqr(eye) < best) {
				best = hit.get().distanceToSqr(eye);
				struck = e;
			}
		}
		Vec3 from = eye.add(look.scale(0.6)).add(0.0, -0.3, 0.0);
		if (struck != null) {
			end = struck.getBoundingBox().getCenter();
		}
		MagicFx.stroke(level, from, end, 0.12F + 0.12F * charge, color);
		MagicFx.blast(level, end, 0.6F + charge, color);
		player.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.TRIDENT_THUNDER.value(), SoundSource.PLAYERS, 0.35F + 0.3F * charge, 1.8F);
		if (struck == null) {
			return true;
		}
		shock(player, level, struck, STATIC_DAMAGE * charge);
		// it arcs on to those beside the one it struck
		LivingEntity last = struck;
		java.util.Set<LivingEntity> done = new java.util.HashSet<>();
		done.add(struck);
		for (int i = 0; i < ARCS; i++) {
			LivingEntity at = last, next = null;
			double near = ARC_REACH * ARC_REACH;
			for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, at.getBoundingBox().inflate(ARC_REACH),
				e -> !done.contains(e) && PickKit.enemy(player, e))) {
				double d = e.distanceToSqr(at);
				if (d < near) {
					near = d;
					next = e;
				}
			}
			if (next == null) {
				break;
			}
			MagicFx.stroke(level, at.getBoundingBox().getCenter(), next.getBoundingBox().getCenter(), 0.1F, color);
			shock(player, level, next, STATIC_DAMAGE * charge * ARC_SHARE);
			done.add(next);
			last = next;
		}
		return true;
	}

	private static void shock(ServerPlayer player, ServerLevel level, LivingEntity target, float damage) {
		if (target.hurtServer(level, level.damageSources().indirectMagic(player, player), damage)) {
			target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, DAZE_TICKS, DAZE_LEVEL), player);
			level.sendParticles(MagicFx.CRACKLE, target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(), 8, 0.3, 0.4, 0.3, 0.1);
		}
	}
}
