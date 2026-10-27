package com.jaymospowers.power.zfighter;

import com.jaymospowers.core.Powers;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.phys.Vec3;

/**
 * (0.17.45, Andy's NOEA picks) Lend ki: while you charge ki (R held), sneak and right click a Z Fighter you look at within
 * {@link #REACH} blocks, and your ki pours into theirs, {@link #PER_SECOND} a second, while they glow. It stops when your
 * ki runs out, theirs is full, either of you is down or too far off ({@link #BREAK_RANGE}), or you let go of R.
 */
public final class ZFighterLendKi {
	/** How close you must be to start, and how far apart the two may drift before it breaks (blocks). */
	static final double REACH = 6.0;
	static final double BREAK_RANGE = 9.0;
	/** Ki handed over a second. */
	static final float PER_SECOND = 10.0F;
	/** The friend glows this long (ticks), kept topped up while it runs. */
	static final int GLOW_TICKS = 30;
	static final int GLOW_REFRESH = 10;
	/** A mote of ki flies between you every this many ticks. */
	static final int MOTE_EVERY = 2;

	/** Lender → the one receiving. */
	private static final Map<UUID, UUID> LENDING = new HashMap<>();

	private ZFighterLendKi() {
	}

	static void init() {
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (level.isClientSide() || hand != InteractionHand.MAIN_HAND || !(player instanceof ServerPlayer lender) || !(entity instanceof ServerPlayer friend)
				|| !lender.isShiftKeyDown() || !Powers.has(lender, ZFighterPower.INSTANCE) || !ZFighterPower.live(lender).charging) {
				return InteractionResult.PASS;
			}
			return begin(lender, friend) ? InteractionResult.SUCCESS : InteractionResult.PASS;
		});
		ServerTickEvents.END_SERVER_TICK.register(ZFighterLendKi::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			UUID id = handler.player.getUUID();
			LENDING.remove(id);
			LENDING.values().removeIf(id::equals);
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> LENDING.clear());
	}

	private static boolean begin(ServerPlayer lender, ServerPlayer friend) {
		if (friend == lender || !friend.isAlive() || friend.isSpectator() || !Powers.has(friend, ZFighterPower.INSTANCE)
			|| lender.distanceToSqr(friend) > REACH * REACH || LENDING.containsKey(lender.getUUID())) {
			return false;
		}
		LENDING.put(lender.getUUID(), friend.getUUID());
		lender.sendOverlayMessage(Component.translatable("message.jaymospowers.zfighter_lend_start", friend.getDisplayName()).withStyle(ChatFormatting.AQUA));
		friend.sendOverlayMessage(Component.translatable("message.jaymospowers.zfighter_lend_receive", lender.getDisplayName()).withStyle(ChatFormatting.AQUA));
		lender.level().playSound(null, lender.getX(), lender.getY(), lender.getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.6F, 1.6F);
		return true;
	}

	private static void tick(MinecraftServer server) {
		if (LENDING.isEmpty()) {
			return;
		}
		for (Map.Entry<UUID, UUID> entry : new ArrayList<>(LENDING.entrySet())) {
			ServerPlayer lender = server.getPlayerList().getPlayer(entry.getKey());
			ServerPlayer friend = server.getPlayerList().getPlayer(entry.getValue());
			String stop = lender == null || friend == null ? "" : stopReason(lender, friend);
			if (stop != null) {
				LENDING.remove(entry.getKey());
				if (lender != null && !stop.isEmpty()) {
					lender.sendOverlayMessage(Component.translatable(stop).withStyle(ChatFormatting.GRAY));
				}
				continue;
			}
			pour(lender, friend);
		}
	}

	/** Why this lending ends now (a lang key; empty for no message), or null when it goes on. */
	private static String stopReason(ServerPlayer lender, ServerPlayer friend) {
		ZFighterPower.Live giving = ZFighterPower.live(lender);
		ZFighterPower.Live getting = ZFighterPower.live(friend);
		if (!lender.isAlive() || !friend.isAlive() || lender.level() != friend.level() || !Powers.has(lender, ZFighterPower.INSTANCE)
			|| !Powers.has(friend, ZFighterPower.INSTANCE) || lender.distanceToSqr(friend) > BREAK_RANGE * BREAK_RANGE) {
			return "message.jaymospowers.zfighter_lend_apart";
		}
		if (!giving.charging) {
			return "message.jaymospowers.zfighter_lend_stop";
		}
		if (!lender.isCreative() && giving.ki <= 0.0F) {
			return "message.jaymospowers.zfighter_lend_empty";
		}
		if (getting.ki >= ZFighterPower.maxKi(friend)) {
			return "message.jaymospowers.zfighter_lend_full";
		}
		return null;
	}

	private static void pour(ServerPlayer lender, ServerPlayer friend) {
		ZFighterPower.Live giving = ZFighterPower.live(lender);
		ZFighterPower.Live getting = ZFighterPower.live(friend);
		float room = ZFighterPower.maxKi(friend) - getting.ki;
		float step = Math.min(PER_SECOND / 20.0F, room);
		if (!lender.isCreative()) {
			step = Math.min(step, giving.ki);
			giving.ki -= step;
		}
		getting.ki += step;
		ServerLevel level = lender.level();
		long now = level.getGameTime();
		if (now % GLOW_REFRESH == 0) {
			friend.addEffect(new MobEffectInstance(MobEffects.GLOWING, GLOW_TICKS, 0, false, false, false));
		}
		if (now % MOTE_EVERY == 0) {
			Vec3 from = lender.getBoundingBox().getCenter();
			Vec3 to = friend.getBoundingBox().getCenter();
			DustParticleOptions dust = ZFighterPower.dust(giving);
			double t = level.getRandom().nextDouble();
			Vec3 at = from.lerp(to, t);
			level.sendParticles(dust, true, false, at.x, at.y, at.z, 2, 0.08, 0.08, 0.08, 0.0);
			level.sendParticles(dust, true, false, to.x, to.y, to.z, 1, 0.3, 0.5, 0.3, 0.0);
		}
		if (now % 20 == 0) {
			level.playSound(null, friend.getX(), friend.getY(), friend.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.5F, 1.4F);
		}
	}
}
