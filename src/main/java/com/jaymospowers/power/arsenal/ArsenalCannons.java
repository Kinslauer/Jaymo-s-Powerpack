package com.jaymospowers.power.arsenal;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.Fate;
import com.jaymospowers.core.Mastery;
import com.jaymospowers.core.Powers;
import com.jaymospowers.api.PowerKey;
import com.jaymospowers.power.arsenal.entity.RiftCannonball;
import io.netty.buffer.ByteBuf;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * <b>Cannon Battery</b> (X, the Power Skill key; once B is unlocked): hold it and rifts open in a fan behind you, a
 * cannon barrel pushing out of each at a random spot behind you, one more every {@link #GROW_TICKS} the longer you hold
 * (up to {@link #MAX}). They
 * all swing to follow your crosshair. Let go and they fire in a rolling battery, one after another, then sink back
 * into their rifts.
 *
 * <p>Only the timing is synced ({@link #BATTERY}, to everyone); every client works out the cannons' places and aim
 * from the same formulas ({@link #slot}, {@link #aimPoint}). Drawn by {@code ArsenalCannonRenderer}.
 */
public final class ArsenalCannons {
	// tuning
	public static final int COOLDOWN = 300;
	public static final int MAX = 10;
	/** A new cannon every this many ticks of holding (the first comes at once). */
	public static final int GROW_TICKS = 16;
	/** Held this long, the battery fires on its own. */
	public static final int MAX_HOLD = 200;
	/** How far the crosshair aim reaches. */
	public static final double RANGE = 64.0;
	/** One cannon fires every this many ticks after release, in the order they came out. */
	public static final int FIRE_GAP = 2;
	public static final float BALL_DAMAGE = 7.0F;

	// look (shared with the renderer)
	/** A cannon's rift opens over this many ticks, then its barrel pushes out over {@link #EMERGE_TICKS}. */
	public static final float OPEN_TICKS = 4.0F;
	public static final float EMERGE_TICKS = 8.0F;
	public static final double RIFT_RADIUS = 0.55;
	public static final double BARREL_LENGTH = 1.6;
	/** After firing, a barrel kicks back and then sinks into its rift over this many ticks; the rift closes after. */
	public static final float RECOIL_TICKS = 3.0F;
	public static final float SINK_TICKS = 8.0F;

	/**
	 * A battery: when the hold began, and when it was let go ({@code -1} while held). How many cannons there are
	 * follows from the two ({@link #count}).
	 */
	public record Battery(long start, long released) {
		public static final StreamCodec<ByteBuf, Battery> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_LONG, Battery::start,
			ByteBufCodecs.VAR_LONG, Battery::released,
			Battery::new);

		public boolean held() {
			return released < 0;
		}

		/** How many cannons have come out by game time {@code now} (they stop coming once let go). */
		public int count(float now) {
			float held = (held() ? now : released) - start;
			return Math.min(MAX, 1 + (int) Math.max(0.0F, held / GROW_TICKS));
		}

		/** Ticks since cannon {@code i} began to come out. */
		public float age(int i, float now) {
			return now - start - i * GROW_TICKS;
		}

		/** Ticks since cannon {@code i} fired (negative before), or {@code NaN} while still held. */
		public float sinceFired(int i, float now) {
			return held() ? Float.NaN : now - released - i * FIRE_GAP;
		}

		/** When the last rift has closed. */
		public long over() {
			return held() ? Long.MAX_VALUE : released + (long) (count(released) - 1) * FIRE_GAP + (long) (RECOIL_TICKS + SINK_TICKS + OPEN_TICKS) + 2;
		}
	}

	public static final AttachmentType<Battery> BATTERY = AttachmentRegistry.create(JaymosPowers.id("arsenal_cannons"),
		builder -> builder.syncWith(Battery.STREAM_CODEC, AttachmentSyncPredicate.all()));

	/** Client → server: the Power Skill key (X) went down or up. */
	public record Hold(boolean down) implements CustomPacketPayload {
		public static final Type<Hold> TYPE = new Type<>(JaymosPowers.id("arsenal_cannons"));
		public static final StreamCodec<ByteBuf, Hold> CODEC = ByteBufCodecs.BOOL.map(Hold::new, Hold::down);

		@Override
		public Type<Hold> type() {
			return TYPE;
		}
	}

	private ArsenalCannons() {
	}

	static void init() {
		PayloadTypeRegistry.serverboundPlay().register(Hold.TYPE, Hold.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Hold.TYPE, (payload, ctx) -> {
			ServerPlayer player = ctx.player();
			if (Powers.has(player, ArsenalPower.INSTANCE) && player.isAlive() && !player.isSpectator()) {
				key(player, payload.down());
			}
		});
	}

	/** Whether X does anything yet: once B is (both sides). */
	public static boolean unlocked(Player player) {
		return !Mastery.starterOnly(player) && Mastery.isUnlocked(player, PowerKey.ABILITY_4);
	}

	static void key(ServerPlayer player, boolean down) {
		ServerLevel level = player.level();
		long now = level.getGameTime();
		Battery battery = player.getAttached(BATTERY);
		if (!down) {
			if (battery != null && battery.held()) {
				release(player, battery);
			}
			return;
		}
		if (battery != null || !unlocked(player)) {
			return;
		}
		ArsenalPower.Cooldowns cd = ArsenalPower.cooldowns(player);
		if (now < cd.cannons()) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.arsenal.cannons_recharging",
				String.format("%.1f", (cd.cannons() - now) / 20.0)).withStyle(ChatFormatting.GRAY));
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.DISPENSER_FAIL, SoundSource.PLAYERS, 0.3F, 1.6F);
			return;
		}
		player.setAttached(BATTERY, new Battery(now, -1));
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ILLUSIONER_CAST_SPELL, SoundSource.PLAYERS, 0.8F, 0.6F);
	}

	private static void release(ServerPlayer player, Battery battery) {
		long now = player.level().getGameTime();
		player.setAttached(BATTERY, new Battery(battery.start(), now));
		player.setAttached(ArsenalPower.COOLDOWNS, ArsenalPower.cooldowns(player).withCannons(now + COOLDOWN));
	}

	/** Every tick for the holder: cannons coming out while held, firing in turn once let go. */
	static void tick(ServerPlayer player) {
		Battery battery = player.getAttached(BATTERY);
		if (battery == null) {
			return;
		}
		ServerLevel level = player.level();
		long now = level.getGameTime();
		if (now < battery.start() || now >= battery.over() || !player.isAlive() && battery.held()) {
			player.removeAttached(BATTERY);
			return;
		}
		if (battery.held()) {
			long held = now - battery.start();
			if (held % GROW_TICKS == 0 && held / GROW_TICKS < MAX) {
				Vec3 at = slot(player, battery, (int) (held / GROW_TICKS), 1.0F);
				level.sendParticles(ParticleTypes.REVERSE_PORTAL, at.x, at.y, at.z, 10, 0.2, 0.2, 0.2, 0.03);
				level.playSound(null, at.x, at.y, at.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.3F, 0.5F + held / (float) GROW_TICKS * 0.05F);
				level.playSound(null, at.x, at.y, at.z, SoundEvents.CHAIN_PLACE, SoundSource.PLAYERS, 0.6F, 0.6F);
			}
			if (held >= MAX_HOLD) {
				release(player, battery);
			}
			return;
		}
		int count = battery.count(battery.released());
		long since = now - battery.released();
		if (since % FIRE_GAP == 0 && since / FIRE_GAP < count) {
			fire(player, battery, (int) (since / FIRE_GAP));
		}
	}

	/** Cannon {@code i} goes off at the crosshair. */
	private static void fire(ServerPlayer player, Battery battery, int i) {
		ServerLevel level = player.level();
		Vec3 at = slot(player, battery, i, 1.0F);
		Vec3 aim = aimPoint(player, 1.0F);
		Vec3 dir = aim.subtract(at).normalize();
		Vec3 muzzle = at.add(dir.scale(BARREL_LENGTH));
		boolean corrupted = Fate.isCorrupted(player);
		RiftCannonball ball = RiftCannonball.create(level, player, muzzle, dir, BALL_DAMAGE * (corrupted ? ArsenalVoid.DAMAGE_MULTIPLIER : 1.0F), corrupted);
		level.addFreshEntity(ball);
		level.sendParticles(ParticleTypes.EXPLOSION, muzzle.x, muzzle.y, muzzle.z, 1, 0.0, 0.0, 0.0, 0.0);
		level.sendParticles(ParticleTypes.LARGE_SMOKE, muzzle.x, muzzle.y, muzzle.z, 8, 0.15, 0.15, 0.15, 0.05);
		level.sendParticles(ParticleTypes.FLAME, muzzle.x, muzzle.y, muzzle.z, 4, 0.1, 0.1, 0.1, 0.05);
		level.playSound(null, muzzle.x, muzzle.y, muzzle.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 1.2F, 1.5F + level.getRandom().nextFloat() * 0.2F);
		level.playSound(null, muzzle.x, muzzle.y, muzzle.z, SoundEvents.FIREWORK_ROCKET_BLAST, SoundSource.PLAYERS, 1.0F, 0.5F);
	}

	/**
	 * Where cannon {@code i}'s rift sits: a random spot behind and above you that turns with your view. The spots are
	 * cells of a grid behind you ({@link #COLUMNS} across, {@link #ROWS} up) shuffled by the battery's start time, so
	 * every client picks the same ones and no two cannons share a cell, each nudged a little off its cell's centre.
	 */
	public static Vec3 slot(Player player, Battery battery, int i, float partialTick) {
		float yaw = player.getViewYRot(partialTick) * ((float) Math.PI / 180.0F);
		Vec3 forward = new Vec3(-Math.sin(yaw), 0.0, Math.cos(yaw));
		Vec3 right = new Vec3(-forward.z, 0.0, forward.x);
		int[] cells = shuffledCells(battery.start());
		int cell = cells[i % cells.length];
		int column = cell % COLUMNS;
		int row = cell / COLUMNS;
		long hash = mix(battery.start() * 31 + i);
		double jitterAcross = ((hash & 0xFF) / 255.0 - 0.5) * 0.5;
		double jitterUp = ((hash >>> 8 & 0xFF) / 255.0 - 0.5) * 0.4;
		double jitterBack = (hash >>> 16 & 0xFF) / 255.0 * 0.9;
		double across = (column - (COLUMNS - 1) / 2.0) * 1.15 + jitterAcross;
		double up = 1.6 + row * 1.05 + jitterUp;
		double back = 1.0 + Math.abs(across) * 0.15 + jitterBack;
		Vec3 base = player.getPosition(partialTick);
		return base.add(right.scale(across)).add(forward.scale(-back)).add(0.0, up, 0.0);
	}

	/** The grid behind you the cannons are spread over (more cells than {@link #MAX}, so some stay empty). */
	private static final int COLUMNS = 5;
	private static final int ROWS = 3;

	/** The grid cells in a random order fixed by {@code seed} (a Fisher-Yates shuffle). */
	private static int[] shuffledCells(long seed) {
		int[] cells = new int[COLUMNS * ROWS];
		for (int c = 0; c < cells.length; c++) {
			cells[c] = c;
		}
		long state = seed;
		for (int c = cells.length - 1; c > 0; c--) {
			state = mix(state + c);
			int j = (int) Long.remainderUnsigned(state, c + 1);
			int swap = cells[c];
			cells[c] = cells[j];
			cells[j] = swap;
		}
		return cells;
	}

	/** SplitMix64's finaliser: the same number in, the same well-scrambled number out, on every side. */
	private static long mix(long z) {
		z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
		z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
		return z ^ (z >>> 31);
	}

	/** What the crosshair is on: the first block along the look, or the end of {@link #RANGE}. */
	public static Vec3 aimPoint(Player player, float partialTick) {
		Vec3 eye = player.getEyePosition(partialTick);
		Vec3 end = eye.add(player.getViewVector(partialTick).scale(RANGE));
		BlockHitResult hit = player.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
		return hit.getType() == HitResult.Type.MISS ? end : hit.getLocation();
	}

	/** The power went away: the cannons with it. */
	static void onLost(ServerPlayer player) {
		player.removeAttached(BATTERY);
	}
}
