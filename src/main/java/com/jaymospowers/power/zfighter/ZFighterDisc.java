package com.jaymospowers.power.zfighter;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.Powers;
import com.jaymospowers.core.Soundscape;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The Ki Disc's throw (G, when it's the quick technique). The throwing arm goes straight up and a flat disc of ki forms
 * over the palm, lying level and spinning, growing from a sliver to full size over {@link #WINDUP_TICKS} with a rising
 * whine. Then the overhand throw: the arm comes over and down, the body leaning into it, and {@link #RELEASE_TICK} ticks
 * in the disc leaves the hand from above the head, flat and spinning fast ({@code ZFighterKi.throwDisc}; its cutting is
 * unchanged). Everyone sees the pose ({@link #THROW}, drawn by {@code ZFighterDiscPose}) and the disc (a ki orb flagged
 * {@code ZFighterNet.Orb.DISC}). The ki is paid when the wind-up starts; dying, losing the power, being silenced or
 * turning into the Great Ape mid-wind-up lets the disc fizzle.
 */
public final class ZFighterDisc {
	/** Arm up, the disc gathering over the palm (ticks). */
	public static final int WINDUP_TICKS = 16;
	/** The overhand sweep from overhead to low and forward (ticks), then the arm held through before it eases back. */
	public static final int SWEEP_TICKS = 4;
	public static final int FOLLOW_TICKS = 5;
	/** Ticks into the sweep that the disc leaves the hand. */
	public static final int RELEASE_TICK = 2;
	/** The throwing arm's pitch (model radians, negative raises it forward and up): overhead, and low after the throw. */
	public static final float ARM_UP = -3.05F;
	public static final float ARM_FOLLOW = -0.55F;
	/** The disc's size across while it forms (blocks), from the first tick to full; full is its cutting width. */
	static final double HELD_MIN = 0.15;
	static final double FULL_SIZE = ZFighterKi.DISC_HALF_WIDTH * 2.0;
	/** Where the throwing hand is (blocks, a 1.8-tall player): the shoulder's height, how far out to the side, the arm's length. */
	static final double SHOULDER_HEIGHT = 1.375;
	static final double SHOULDER_SIDE = 0.33;
	static final double ARM_LENGTH = 0.72;
	/** How far past the fingertips the disc floats while held. */
	static final double ABOVE_PALM = 0.28;
	/** Aimed at what's under the crosshair this far off at most; nearer than {@link #AIM_MIN}, straight along the look. */
	static final double AIM_MIN = 3.0;
	/** The whine: a hum every this many ticks, rising in pitch over the wind-up, and a ringing chime over it. */
	static final int HUM_EVERY = 4;
	static final float HUM_PITCH_FROM = 0.8F;
	static final float HUM_PITCH_TO = 1.9F;
	static final int CHIME_EVERY = 3;

	/** Stages in {@link #THROW}. */
	public static final int WINDING = 1;
	public static final int THROWING = 2;

	/** Throwing a Ki Disc, for everyone's client (the pose): {@link #WINDING} or {@link #THROWING}; absent otherwise. Not saved. */
	public static final AttachmentType<Integer> THROW = AttachmentRegistry.create(JaymosPowers.id("zfighter_disc_throw"),
		builder -> builder.syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.all()));

	private static final class Throw {
		final ServerLevel level;
		final long start;
		final int orbId;

		Throw(ServerLevel level, long start, int orbId) {
			this.level = level;
			this.start = start;
			this.orbId = orbId;
		}
	}

	private static final Map<UUID, Throw> THROWS = new HashMap<>();

	private ZFighterDisc() {
	}

	static void init() {
		ServerTickEvents.END_SERVER_TICK.register(ZFighterDisc::tick);
	}

	static void clear() {
		THROWS.clear();
	}

	/** Mid-throw (wind-up or sweep): he's slowed like charging, and G waits. */
	static boolean throwing(ServerPlayer player) {
		return THROWS.containsKey(player.getUUID());
	}

	/** G: the arm goes up and the disc starts to form. False when he can't pay or is already throwing. */
	static boolean start(ServerPlayer player, ZFighterPower.Live live) {
		if (throwing(player) || !ZFighterPower.spend(player, live, ZFighterKi.DISC_KI)) {
			return false;
		}
		ServerLevel level = player.level();
		THROWS.put(player.getUUID(), new Throw(level, level.getGameTime(), -(player.getId() * 4 + 4)));
		player.setAttached(THROW, WINDING);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.8F, 1.5F);
		ZFighterPower.refresh(player, live);
		return true;
	}

	/** The throwing arm's pitch {@code t} ticks into the sweep: accelerating out of the overhead hold, over and down. */
	public static float sweepAngle(float t) {
		float k = Mth.clamp(t / SWEEP_TICKS, 0.0F, 1.0F);
		return Mth.lerp(k * k * (2.0F - k), ARM_UP, ARM_FOLLOW);
	}

	private static void tick(MinecraftServer server) {
		for (Iterator<Map.Entry<UUID, Throw>> it = THROWS.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<UUID, Throw> entry = it.next();
			Throw t = entry.getValue();
			ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
			if (player == null) {
				it.remove();
				continue;
			}
			if (player.level() != t.level || !player.isAlive() || !Powers.has(player, ZFighterPower.INSTANCE) || Powers.silenced(player) || ZFighterLineage.ape(player)) {
				// it fizzles in the hand
				it.remove();
				end(player);
				continue;
			}
			long held = t.level.getGameTime() - t.start;
			if (held < WINDUP_TICKS) {
				wind(player, t, held);
			} else if (held < WINDUP_TICKS + RELEASE_TICK) {
				if (held == WINDUP_TICKS) {
					player.setAttached(THROW, THROWING);
					t.level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WIND_CHARGE_THROW, SoundSource.PLAYERS, 0.7F, 1.3F);
				}
				// the arm coming over, the disc still on it
				ZFighterKi.discOrb(t.level, t.orbId, held(player, sweepAngle(held - WINDUP_TICKS)), FULL_SIZE, ZFighterKi.discColor(ZFighterPower.live(player)));
			} else if (held == WINDUP_TICKS + RELEASE_TICK) {
				release(player, t);
			} else if (held >= WINDUP_TICKS + SWEEP_TICKS + FOLLOW_TICKS) {
				it.remove();
				end(player);
			}
		}
	}

	/** The wind-up: the disc grows over the palm, a mote drawn into it now and then, the whine rising. */
	private static void wind(ServerPlayer player, Throw t, long held) {
		ServerLevel level = t.level;
		float charge = (held + 1) / (float)WINDUP_TICKS;
		int color = ZFighterKi.discColor(ZFighterPower.live(player));
		Vec3 at = held(player, ARM_UP);
		ZFighterKi.discOrb(level, t.orbId, at, Mth.lerp(charge * charge * (3.0F - 2.0F * charge), HELD_MIN, FULL_SIZE), color);
		RandomSource random = level.getRandom();
		if (random.nextInt(2) == 0) {
			// a mote drawn in level with the disc, from round its edge
			double a = random.nextDouble() * Math.PI * 2.0;
			double r = 0.5 + 0.6 * (1.0F - charge);
			double ox = Math.cos(a) * r, oz = Math.sin(a) * r;
			level.sendParticles(new DustParticleOptions(color, 0.8F), true, false, at.x + ox, at.y, at.z + oz, 0, -ox, 0.0, -oz, 0.12);
		}
		if (held % HUM_EVERY == 0) {
			Soundscape.play(level, player.position(), SoundSource.PLAYERS,
				ZFighterKi.KI_HUM.louder(0.8F).pitched(Mth.lerp(charge, HUM_PITCH_FROM, HUM_PITCH_TO)));
		}
		if (held % CHIME_EVERY == 1) {
			level.playSound(null, at.x, at.y, at.z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.35F + 0.4F * charge,
				Mth.lerp(charge, 0.7F, 2.0F));
		}
		if (held == WINDUP_TICKS - 1) {
			// full: it rings
			level.playSound(null, at.x, at.y, at.z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0F, 2.0F);
		}
	}

	/** It leaves the hand: from above his head, at what's under his crosshair. */
	private static void release(ServerPlayer player, Throw t) {
		ServerLevel level = t.level;
		ZFighterPower.Live live = ZFighterPower.live(player);
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getViewVector(1.0F);
		Vec3 from = held(player, sweepAngle(RELEASE_TICK));
		// a low ceiling: it leaves from where the hand can reach instead
		BlockHitResult room = level.clip(new ClipContext(eye, from, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
		if (room.getType() != HitResult.Type.MISS) {
			from = eye.lerp(room.getLocation(), 0.6);
		}
		BlockHitResult aimed = level.clip(new ClipContext(eye, eye.add(look.scale(ZFighterKi.DISC_RANGE)), ClipContext.Block.COLLIDER,
			ClipContext.Fluid.NONE, player));
		Vec3 target = aimed.getLocation();
		Vec3 dir = target.distanceTo(from) < AIM_MIN ? look : target.subtract(from).normalize();
		ZFighterKi.throwDisc(player, live, from, dir);
		player.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
	}

	/**
	 * Where the held disc is with the throwing arm at {@code pitch}: from his shoulder (turned with his body) out along
	 * the arm, a little past the fingertips.
	 */
	private static Vec3 held(ServerPlayer player, float pitch) {
		double scale = player.getBbHeight() / 1.8;
		float yaw = player.yBodyRot;
		Vec3 forward = Vec3.directionFromRotation(0.0F, yaw);
		Vec3 right = Vec3.directionFromRotation(0.0F, yaw + 90.0F);
		double side = player.getMainArm() == HumanoidArm.RIGHT ? SHOULDER_SIDE : -SHOULDER_SIDE;
		Vec3 shoulder = player.position().add(0.0, SHOULDER_HEIGHT * scale, 0.0).add(right.scale(side * scale));
		// the model's arm hangs down at pitch 0 and comes forward and up as it goes negative
		double reach = (ARM_LENGTH + ABOVE_PALM) * scale;
		return shoulder.add(0.0, -Math.cos(pitch) * reach, 0.0).add(forward.scale(-Math.sin(pitch) * reach));
	}

	private static void end(ServerPlayer player) {
		player.removeAttached(THROW);
		ZFighterPower.refresh(player, ZFighterPower.live(player));
	}

	/** Dropped on logout. */
	static void forget(ServerPlayer player) {
		THROWS.remove(player.getUUID());
		player.removeAttached(THROW);
	}
}
