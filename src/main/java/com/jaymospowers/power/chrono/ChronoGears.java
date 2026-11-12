package com.jaymospowers.power.chrono;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.wounds.Mending;
import io.netty.buffer.ByteBuf;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/**
 * What a runner sees while Overclock (or Velox's Velocity) is on: five gears, shifted with the mouse wheel while sprinting
 * at speed. A higher gear runs faster ({@link #SPEED_SCALE}), turns wider (client side, {@link #TURN_SHED}), burns more
 * hunger ({@link #EXHAUSTION}), leaves a longer streak (the streak follows real speed) and closes wounds faster
 * ({@link #HEAL_PER_SECOND}) for a steep hunger price ({@link #HEAL_COST}). Gear 3 is the speed Overclock always had.
 * The gear shows on a chip bottom right, beside the clocks (or the Xenoform chip).
 */
public final class ChronoGears {
	// ---- tuning ----
	public static final int GEARS = 5, DEFAULT_GEAR = 3;
	/** The sprint boost per gear, as a share of Overclock's old boost (gear 3 = 1.0). */
	static final double[] SPEED_SCALE = {0.45, 0.75, 1.0, 1.35, 1.8};
	/** Food exhaustion per tick of sprinting, per gear. */
	static final float[] EXHAUSTION = {0.0F, 0.004F, 0.009F, 0.018F, 0.035F};
	/** How much of its speed a sharp turn sheds, per gear (client, see ChronoMomentumClient). */
	public static final float[] TURN_SHED = {0.10F, 0.18F, 0.28F, 0.40F, 0.55F};
	/** Speed healing: hearts a second closed per gear, and the exhaustion each heart costs (rest costs far less). */
	static final float[] HEAL_PER_SECOND = {0.0F, 0.0F, 0.5F, 1.0F, 2.0F};
	static final float HEAL_COST = 6.0F;
	/** No speed healing below this food level. */
	static final int HEAL_MIN_FOOD = 6;
	/** Shifting needs this much real horizontal speed (blocks a tick) so the wheel keeps its hotbar job at a walk. */
	public static final double SHIFT_MIN_SPEED = 0.2;

	/** On a runner while overclocked: the gear, 1 to {@link #GEARS} (synced for the HUD and the streak). */
	public static final AttachmentType<Integer> GEAR = AttachmentRegistry.create(JaymosPowers.id("chrono_gear"),
		builder -> builder.syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.all()));

	/** Client to server: shift {@code delta} gears (+1 or -1). */
	public record Shift(int delta) implements CustomPacketPayload {
		public static final Type<Shift> TYPE = new Type<>(JaymosPowers.id("chrono_gear_shift"));
		public static final StreamCodec<ByteBuf, Shift> CODEC = ByteBufCodecs.VAR_INT.map(Shift::new, Shift::delta);

		@Override
		public Type<Shift> type() {
			return TYPE;
		}
	}

	private ChronoGears() {
	}

	static void register() {
		PayloadTypeRegistry.serverboundPlay().register(Shift.TYPE, Shift.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Shift.TYPE, (payload, ctx) -> shift(ctx.player(), payload.delta()));
	}

	/** The gear {@code player} is in, 1 to 5 ({@link #DEFAULT_GEAR} when none is set). */
	public static int gear(Player player) {
		Integer gear = player.getAttached(GEAR);
		return gear == null ? DEFAULT_GEAR : Mth.clamp(gear, 1, GEARS);
	}

	private static void shift(ServerPlayer player, int delta) {
		if (delta == 0 || !ChronoSpeed.overclocked(player) || !player.isAlive()) {
			return;
		}
		int now = gear(player);
		int to = Mth.clamp(now + (delta > 0 ? 1 : -1), 1, GEARS);
		if (to != now) {
			player.setAttached(GEAR, to);
			player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS,
				0.6F, 0.6F + to * 0.25F);
		}
	}

	/** The sprint boost for {@code gear} (Overclock's {@link ChronoSpeed#SPRINT_BOOST} scaled). */
	static double sprintBoost(int gear) {
		return ChronoSpeed.SPRINT_BOOST * SPEED_SCALE[gear - 1];
	}

	/** Each tick (server): the gear shows while overclocked, sprinting burns hunger, and wounds close in the higher gears. */
	static void tick(ServerPlayer player, ServerLevel level, boolean overclocked) {
		if (!overclocked) {
			clear(player);
			return;
		}
		if (player.getAttached(GEAR) == null) {
			player.setAttached(GEAR, DEFAULT_GEAR);
		}
		int gear = gear(player);
		if (player.isSprinting() && !player.isCreative()) {
			player.causeFoodExhaustion(EXHAUSTION[gear - 1]);
		}
		float heal = HEAL_PER_SECOND[gear - 1];
		if (heal > 0.0F && player.tickCount % 10 == 0 && player.getHealth() < player.getMaxHealth()
			&& (player.isCreative() || player.getFoodData().getFoodLevel() >= HEAL_MIN_FOOD)) {
			float took = Mending.mend(player, heal / 2.0F);
			if (took > 0.0F) {
				player.causeFoodExhaustion(took * HEAL_COST);
				ChronoFx.particles(level, player, ParticleTypes.HAPPY_VILLAGER, player.getX(), player.getY() + 1.0, player.getZ(), 2, 0.3, 0.5, 0.3, 0.0);
			}
		}
	}

	static void clear(ServerPlayer player) {
		if (player.getAttached(GEAR) != null) {
			player.removeAttached(GEAR);
		}
	}
}
