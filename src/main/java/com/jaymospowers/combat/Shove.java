package com.jaymospowers.combat;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.HitStop;
import com.jaymospowers.core.fx.MagicFx;
import com.jaymospowers.power.darktide.DarktideStatus;
import com.jaymospowers.stamina.Feathers;
import io.netty.buffer.ByteBuf;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BlocksAttacks;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.phys.Vec3;

/**
 * A shove for everyone (Andy picked it from Hackers 'n Slashers, All Rights Reserved: the idea is its, the code, names and
 * effects are ours). The Shove key (default backslash, rebindable) strikes the one in front of you with whatever you
 * hold: a weapon in the main hand is a pommel strike, an empty off hand with anything in the main hand a shoulder shove.
 * You lunge half a step, what's in front of you within {@link #REACH} is struck, thrown back, and its guard is broken
 * for a moment (a raised shield or weapon guard is knocked aside, as the shield bash does) and it reels
 * ({@link #STUN}). It costs {@link #FEATHERS} feathers and has its own {@link #COOLDOWN}, apart from the shield bang and
 * bash ({@link ShieldBang}), which a raised shield keeps.
 */
public final class Shove {
	// ===== the knobs ================================================================================================
	/** Feathers a shove costs, and ticks before the next. */
	public static final int FEATHERS = 2, COOLDOWN = 24;
	/** How far in front (blocks) and how wide round the front (degrees each side) a shove reaches. */
	public static final double REACH = 2.4, ANGLE = 55.0;
	/** The thrown-back strength (blocks a tick), its lift, for a pommel strike and a shoulder shove. */
	public static final double POMMEL_SHOVE = 0.9, SHOULDER_SHOVE = 1.1, LIFT = 0.2;
	/** Damage of a pommel strike; a shoulder shove does none. */
	public static final float POMMEL_DAMAGE = 1.5F;
	/** How long the struck one reels (ticks), and how long a guard it knocked aside stays down (seconds). */
	public static final int STUN = 14;
	public static final float GUARD_BREAK = 1.5F;
	/** How far you lunge in (blocks a tick), the client's step. */
	public static final double LUNGE = 0.3;
	/** The hit-stop's length (ticks), and the ring's colour and size. */
	public static final int HOLD = 2;
	public static final int RING_COLOR = 0xD9D2C0;
	public static final float RING_SIZE = 0.9F;
	// ================================================================================================================

	/** Client → server: the Shove key was pressed. */
	public record Press() implements CustomPacketPayload {
		public static final Press INSTANCE = new Press();
		public static final Type<Press> TYPE = new Type<>(JaymosPowers.id("shove"));
		public static final StreamCodec<ByteBuf, Press> CODEC = StreamCodec.unit(INSTANCE);

		@Override
		public Type<Press> type() {
			return TYPE;
		}
	}

	private static final Map<UUID, Long> LAST = new HashMap<>();

	private Shove() {
	}

	public static void init() {
		PayloadTypeRegistry.serverboundPlay().register(Press.TYPE, Press.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Press.TYPE, (payload, context) -> shove(context.player()));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> LAST.remove(handler.player.getUUID()));
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> LAST.clear());
	}

	/** Whether {@code player} can shove with what is in their hands (both sides): a weapon out, or a free off hand. */
	public static boolean ready(Player player) {
		return !player.isSpectator() && !player.isUsingItem() && !player.getMainHandItem().isEmpty() && (pommel(player) || player.getOffhandItem().isEmpty());
	}

	/** Whether the shove is a pommel strike (a weapon in the main hand) rather than a shoulder shove. */
	public static boolean pommel(Player player) {
		return Armed.kind(player.getMainHandItem()) != null;
	}

	private static void shove(ServerPlayer player) {
		ServerLevel level = player.level();
		long now = level.getGameTime();
		Long last = LAST.get(player.getUUID());
		// a couple of ticks' grace for network jitter
		if (!ready(player) || last != null && now - last < COOLDOWN - 2 || !Feathers.spend(player, FEATHERS)) {
			return;
		}
		LAST.put(player.getUUID(), now);
		boolean pommel = pommel(player);
		player.swing(pommel ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND, SwingAnimation.DEFAULT, true);
		Vec3 eye = player.getEyePosition();
		Vec3 look = Vec3.directionFromRotation(0.0F, player.getYRot());
		boolean hit = false;
		for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(REACH),
			e -> e != player && e.isAlive() && !e.isSpectator() && !player.isAlliedTo(e))) {
			Vec3 flat = new Vec3(target.getX() - player.getX(), 0.0, target.getZ() - player.getZ());
			if (Math.sqrt(target.getBoundingBox().distanceToSqr(eye)) > REACH || flat.lengthSqr() < 1.0E-4
				|| flat.normalize().dot(look) < Math.cos(Math.toRadians(ANGLE))) {
				continue;
			}
			hit = true;
			if (target.isBlocking()) {
				ItemStack guard = target.getUseItem();
				BlocksAttacks blocks = guard.get(DataComponents.BLOCKS_ATTACKS);
				if (blocks != null) {
					blocks.disable(level, target, GUARD_BREAK, guard);
				}
				target.stopUsingItem();
			}
			if (pommel) {
				target.hurtServer(level, level.damageSources().playerAttack(player), POMMEL_DAMAGE);
			}
			Vec3 away = flat.normalize();
			double force = pommel ? POMMEL_SHOVE : SHOULDER_SHOVE;
			target.push(away.x * force, LIFT, away.z * force);
			target.needsSync = true;
			DarktideStatus.stun(target, STUN);
			HitStop.hit(player, target, HOLD);
			MagicFx.ring(level, target.getBoundingBox().getCenter(), RING_SIZE, RING_COLOR);
		}
		level.playSound(null, player.getX(), player.getEyeY(), player.getZ(), hit ? SoundEvents.PLAYER_ATTACK_KNOCKBACK : SoundEvents.PLAYER_ATTACK_SWEEP,
			SoundSource.PLAYERS, 1.0F, pommel ? 0.9F : 0.7F);
	}
}
