package com.jaymospowers.power.cryo;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.Powers;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * (0.14.0) R, Ice Slide: a toggle. While it's on you skate: the client moves you ({@code CryoSlideClient}: forward
 * pushes off along your look, Sprint pushes harder, S digs in to brake, and you glide on with your speed, turning only
 * so fast, like Wind's and Smoke's flight). Under and ahead of you a lane of ice freezes ({@link #SLIDING} is synced so
 * every screen draws it, {@code CryoLaneClient}); it melts away behind you after {@link #LANE_TICKS}. Water ahead
 * freezes into a real bridge of frosted ice ({@link CryoIce}) that melts back to water just as fast, so keep moving.
 * <p>Physical, as Andy likes his movement: it costs hunger (more the faster you go) and ends when you're too hungry;
 * hitting a wall at speed hurts with the square of the speed ({@link #crash}); falls hurt as always. A fresh awakening
 * (only the starter) skates slower.
 */
public final class CryoSlide {
	/** Hunger a tick, plus this much a block skated. At or below this food it ends. */
	static final float EXHAUSTION = 0.006F;
	static final float EXHAUSTION_PER_BLOCK = 0.014F;
	static final int MIN_FOOD = 6;
	/** How long the lane (and a water bridge) lasts behind you (ticks: 10 s). */
	public static final int LANE_TICKS = 200;
	/** How far ahead (blocks) water is frozen, and how wide the bridge is (blocks either side). */
	static final double AHEAD = 3.0;
	static final int BRIDGE_HALF = 1;
	/** Cooldown after switching it off. */
	static final int COOLDOWN = 20;
	/** Crash: {@code K × (speed² − safe²)}, the most speed a crash can report, and the fewest ticks between two. */
	static final float CRASH_K = 9.0F;
	static final float CRASH_SAFE = 0.45F;
	static final float CRASH_MAX = 1.6F;
	static final int CRASH_SPACING = 8;

	/** On a player skating: synced to everyone, who draw the ice lane. */
	public static final AttachmentType<Boolean> SLIDING = AttachmentRegistry.create(JaymosPowers.id("cryo_slide"),
		builder -> builder.syncWith(ByteBufCodecs.BOOL, AttachmentSyncPredicate.all()));

	private static final Map<UUID, Vec3> LAST = new HashMap<>();
	private static final Map<UUID, Integer> LAST_CRASH = new HashMap<>();

	private CryoSlide() {
	}

	/** Skating on Ice Slide (both sides). */
	public static boolean active(Player player) {
		return player.getAttached(SLIDING) != null && Powers.has(player, CryoPower.INSTANCE);
	}

	/** Whether this player can skate at all right now. */
	public static boolean canSlide(Player player) {
		return player.isAlive() && !player.isSpectator() && !player.isPassenger() && !player.isSleeping() && !player.isFallFlying()
			&& !player.isEyeInFluid(FluidTags.WATER) && player.getFluidHeight(FluidTags.LAVA) < 0.4;
	}

	static void toggle(ServerPlayer player) {
		if (player.getAttached(SLIDING) != null) {
			stop(player, true);
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.cryo_slide_off").withStyle(ChatFormatting.GRAY));
			return;
		}
		if (!CryoPower.ready(player, CryoState.SLIDE) || !canSlide(player)) {
			return;
		}
		if (CryoPower.spent(player, MIN_FOOD)) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.cryo_hungry").withStyle(ChatFormatting.GRAY));
			return;
		}
		player.setAttached(SLIDING, true);
		LAST.put(player.getUUID(), player.position());
		player.sendOverlayMessage(Component.translatable("message.jaymospowers.cryo_slide_on").withStyle(ChatFormatting.AQUA));
		ServerLevel level = player.level();
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.GLASS_PLACE, SoundSource.PLAYERS, 0.8F, 1.6F);
		level.sendParticles(ParticleTypes.SNOWFLAKE, player.getX(), player.getY() + 0.1, player.getZ(), 20, 0.6, 0.05, 0.6, 0.02);
	}

	/** Off; with {@code cooldown}, the short cooldown starts. */
	static void stop(ServerPlayer player, boolean cooldown) {
		LAST.remove(player.getUUID());
		LAST_CRASH.remove(player.getUUID());
		if (player.getAttached(SLIDING) == null) {
			return;
		}
		player.removeAttached(SLIDING);
		if (cooldown) {
			CryoPower.cooldown(player, CryoState.SLIDE, COOLDOWN);
		}
	}

	static void tick(ServerPlayer player) {
		if (player.getAttached(SLIDING) == null) {
			return;
		}
		if (!canSlide(player) || CryoPower.spent(player, MIN_FOOD)) {
			stop(player, true);
			return;
		}
		ServerLevel level = player.level();
		UUID id = player.getUUID();
		Vec3 now = player.position();
		Vec3 last = LAST.put(id, now);
		Vec3 moved = last == null ? Vec3.ZERO : now.subtract(last);
		double distance = Math.min(3.0, moved.horizontalDistance());
		player.causeFoodExhaustion(EXHAUSTION + (float)distance * EXHAUSTION_PER_BLOCK);
		// water under and ahead of you freezes into a bridge (only the surface; it melts back behind you)
		Vec3 heading = moved.horizontalDistanceSqr() > 1.0E-4 ? new Vec3(moved.x, 0.0, moved.z).normalize() : player.getLookAngle().multiply(1.0, 0.0, 1.0);
		BlockState ice = Blocks.FROSTED_ICE.defaultBlockState();
		for (double t = 0.0; t <= AHEAD; t += 1.0) {
			BlockPos center = BlockPos.containing(now.add(heading.scale(t))).below();
			for (int dx = -BRIDGE_HALF; dx <= BRIDGE_HALF; dx++) {
				for (int dz = -BRIDGE_HALF; dz <= BRIDGE_HALF; dz++) {
					CryoIce.place(level, center.offset(dx, 0, dz), ice, LANE_TICKS, CryoIce.Mode.WATER);
				}
			}
		}
		// frost off the blades, for everyone
		if (player.onGround() && distance > 0.05) {
			level.sendParticles(ParticleTypes.SNOWFLAKE, now.x, now.y + 0.05, now.z, 2, 0.25, 0.02, 0.25, 0.01);
			if (player.tickCount % 3 == 0) {
				level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.ICE.defaultBlockState()), now.x, now.y + 0.05, now.z, 2, 0.2,
					0.02, 0.2, 0.05);
			}
			if (player.tickCount % 8 == 0) {
				level.playSound(null, now.x, now.y, now.z, SoundEvents.GLASS_STEP, SoundSource.PLAYERS, 0.3F, 1.2F + (float)Math.min(0.6, distance));
			}
		}
	}

	/** A wall hit at {@code speed} (from the skater's client): damage grows with the square of it. Survival and adventure only. */
	static void crash(ServerPlayer player, float speed) {
		if (!active(player) || player.isCreative() || player.isSpectator() || !player.isAlive()) {
			return;
		}
		Integer last = LAST_CRASH.get(player.getUUID());
		if (last != null && player.tickCount - last < CRASH_SPACING) {
			return;
		}
		float hit = Mth.clamp(speed, 0.0F, CRASH_MAX);
		float damage = (hit * hit - CRASH_SAFE * CRASH_SAFE) * CRASH_K;
		if (damage < 1.0F) {
			return;
		}
		LAST_CRASH.put(player.getUUID(), player.tickCount);
		ServerLevel level = player.level();
		player.hurtServer(level, player.damageSources().flyIntoWall(), damage);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), damage >= 6.0F ? SoundEvents.PLAYER_BIG_FALL : SoundEvents.PLAYER_SMALL_FALL,
			SoundSource.PLAYERS, 1.0F, 1.0F);
	}

	static void clear() {
		LAST.clear();
		LAST_CRASH.clear();
	}
}
