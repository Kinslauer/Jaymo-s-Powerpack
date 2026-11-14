package com.jaymospowers.power.arsenal;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.api.PowerKey;
import com.jaymospowers.core.Fate;
import com.jaymospowers.core.LockOn;
import com.jaymospowers.core.Mastery;
import com.jaymospowers.core.Powers;
import com.jaymospowers.core.fx.MagicFx;
import com.jaymospowers.power.arsenal.entity.RiftBlade;
import io.netty.buffer.ByteBuf;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * <b>Blade riding</b> (double tap Jump): a great blade comes out of the rift under the holder's feet and carries them.
 * It flies the way they look (the rider's own client moves them, {@code ArsenalRideClient}); their hands stay free, so
 * they fight from it as they would on the ground, and the Attendant Blades come along and take their orders.
 *
 * <p>Double tap Jump again to step off: the blade flies on the way its rider looks as one heavy hit, then goes home.
 * It also goes home by itself after {@link #RIDE_TICKS}, and under water. Until every ability is unlocked it is not
 * yet the rider's to keep: now and then it shakes for {@link #WARN_TICKS} and leaves early. However it ends, the rider
 * is let down gently.
 *
 * <p>Only the ride's times and the blade are synced ({@link #RIDE}, to everyone); {@code ArsenalRideRenderer} draws
 * the blade under the feet and the rider stands side-on on it (the earthboard's stance, arms free).
 */
public final class ArsenalRide {
	// tuning (ticks)
	public static final int RIDE_TICKS = 600;
	public static final int COOLDOWN = 300;
	/** The blade shakes this long before it goes home. */
	public static final int WARN_TICKS = 30;
	/** Not yet mastered: after this long, once a second, one chance in {@link #FICKLE_CHANCE} that it leaves early. */
	public static final int FICKLE_AFTER = 100;
	public static final int FICKLE_CHANCE = 25;
	/** The gentle set-down afterwards. */
	public static final int SLOW_FALL_TICKS = 80;
	/** Stepping off sends it on: this hard, at most this far. */
	public static final float DIVE_DAMAGE = 10.0F;
	public static final double DIVE_RANGE = 40.0;
	/** How high over the ground it will carry its rider (the rider's client keeps to it). */
	public static final double MAX_ALTITUDE = 40.0;

	/** A ride: when it began, when the blade goes home, and the blade. */
	public record Ride(long start, long end, ItemStack item) {
		public static final StreamCodec<RegistryFriendlyByteBuf, Ride> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_LONG, Ride::start,
			ByteBufCodecs.VAR_LONG, Ride::end,
			ItemStack.OPTIONAL_STREAM_CODEC, Ride::item,
			Ride::new);

		/** 0 steady, up to 1 as it is about to go home. */
		public float shake(double now) {
			return (float) Math.clamp(1.0 - (end - now) / WARN_TICKS, 0.0, 1.0);
		}
	}

	public static final AttachmentType<Ride> RIDE = AttachmentRegistry.create(JaymosPowers.id("arsenal_ride"),
		builder -> builder.syncWith(Ride.STREAM_CODEC, AttachmentSyncPredicate.all()));

	/** Client → server: Jump was double tapped (step on, or step off). */
	public record Step() implements CustomPacketPayload {
		public static final Step INSTANCE = new Step();
		public static final Type<Step> TYPE = new Type<>(JaymosPowers.id("arsenal_ride_step"));
		public static final StreamCodec<ByteBuf, Step> CODEC = StreamCodec.unit(INSTANCE);

		@Override
		public Type<Step> type() {
			return TYPE;
		}
	}

	/** When each holder may ride again. */
	private static final Map<UUID, Long> NEXT = new HashMap<>();

	private ArsenalRide() {
	}

	static void init() {
		PayloadTypeRegistry.serverboundPlay().register(Step.TYPE, Step.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Step.TYPE, (payload, ctx) -> {
			ServerPlayer player = ctx.player();
			if (!Powers.has(player, ArsenalPower.INSTANCE) || !player.isAlive() || player.isSpectator()) {
				return;
			}
			Ride ride = player.getAttached(RIDE);
			if (ride == null) {
				start(player);
			} else {
				send(player, ride);
			}
		});
	}

	/** Whether this holder may ride at all (from the second ability on). */
	public static boolean unlocked(Player player) {
		return !Mastery.starterOnly(player) && Mastery.isUnlocked(player, PowerKey.ABILITY_2);
	}

	/** Every ability unlocked (or the Armory, or the Void Arsenal): the blade stays until its time is up. */
	public static boolean mastered(Player player) {
		return Mastery.unlocked(player) >= Mastery.ALL || Fate.isAscended(player) || Fate.isCorrupted(player);
	}

	public static boolean riding(Player player) {
		return player.getAttached(RIDE) != null;
	}

	private static void start(ServerPlayer player) {
		if (!unlocked(player) || player.isPassenger() || player.isInWater() || player.isSleeping() || player.getAbilities().flying) {
			return;
		}
		ServerLevel level = player.level();
		long now = level.getGameTime();
		long next = NEXT.getOrDefault(player.getUUID(), 0L);
		if (now < next) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.arsenal.ride_recharging",
				String.format("%.1f", (next - now) / 20.0)).withStyle(ChatFormatting.GRAY));
			return;
		}
		boolean corrupted = Fate.isCorrupted(player);
		ItemStack blade = ArsenalPool.randomBlade(player.getRandom());
		player.setAttached(RIDE, new Ride(now, now + RIDE_TICKS, corrupted ? ArsenalVoid.voidTouched(blade) : blade));
		player.resetFallDistance();
		Mastery.used(player);
		MagicFx.ring(level, player.position(), 0.9F, corrupted ? 0x2A1840 : MagicFx.VOID_COLOR);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ILLUSIONER_MIRROR_MOVE, SoundSource.PLAYERS, 0.8F, 1.3F);
		player.sendOverlayMessage(Component.translatable("message.jaymospowers.arsenal.ride_on").withStyle(ChatFormatting.LIGHT_PURPLE));
	}

	/** Every tick for the holder. */
	static void tick(ServerPlayer player) {
		Ride ride = player.getAttached(RIDE);
		if (ride == null) {
			return;
		}
		ServerLevel level = player.level();
		long now = level.getGameTime();
		if (!player.isAlive() || player.isSpectator()) {
			player.removeAttached(RIDE);
			return;
		}
		if (now >= ride.end() || player.isPassenger() || player.isInWater() || player.isSleeping()) {
			home(player);
			return;
		}
		player.resetFallDistance();
		long ridden = now - ride.start();
		if (!mastered(player) && ridden >= FICKLE_AFTER && ridden % 20 == 0 && ride.end() - now > WARN_TICKS
			&& player.getRandom().nextInt(FICKLE_CHANCE) == 0) {
			// not theirs to keep yet: it shakes, then goes
			player.setAttached(RIDE, new Ride(ride.start(), now + WARN_TICKS, ride.item()));
			warn(player);
		} else if (ride.end() - now == WARN_TICKS) {
			warn(player);
		}
		if (now % 3 == 0) {
			Vec3 at = player.position();
			level.sendParticles(MagicFx.mote(Fate.isCorrupted(player) ? 0x6A4A9A : MagicFx.VOID_COLOR, 0.7F), at.x, at.y - 0.1, at.z, 1, 0.25, 0.02, 0.25, 0.0);
		}
	}

	private static void warn(ServerPlayer player) {
		player.sendOverlayMessage(Component.translatable("message.jaymospowers.arsenal.ride_slipping").withStyle(ChatFormatting.RED));
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.0F, 0.6F);
	}

	/** The blade goes back into the rift, and its rider is let down. */
	static void home(ServerPlayer player) {
		if (!riding(player)) {
			return;
		}
		ServerLevel level = player.level();
		MagicFx.ring(level, player.position(), 0.9F, Fate.isCorrupted(player) ? 0x2A1840 : MagicFx.VOID_COLOR);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ILLUSIONER_MIRROR_MOVE, SoundSource.PLAYERS, 0.7F, 0.8F);
		off(player);
	}

	/** Stepped off: the blade flies on the way its rider looks (at what they're locked on to, if anything), one heavy hit. */
	private static void send(ServerPlayer player, Ride ride) {
		ServerLevel level = player.level();
		boolean corrupted = Fate.isCorrupted(player);
		Vec3 from = player.position().add(0.0, -0.2, 0.0);
		LivingEntity locked = LockOn.target(player);
		LivingEntity target = locked != null && locked.isAlive() && ArsenalWing.fair(player, locked) ? locked : null;
		Vec3 to = target != null ? target.getBoundingBox().getCenter() : from.add(player.getLookAngle().scale(DIVE_RANGE));
		RiftBlade flying = RiftBlade.create(level, player, ride.item(), from, to, target, 1,
			DIVE_DAMAGE * (corrupted ? ArsenalVoid.DAMAGE_MULTIPLIER : 1.0F));
		// like one of the Hundred Blades: it lands through a guard's leftovers, and is gone after
		flying.makeSkyBlade(corrupted);
		level.addFreshEntity(flying);
		MagicFx.stroke(level, from, to, 0.18F, corrupted ? 0x2A1840 : MagicFx.VOID_COLOR);
		level.playSound(null, from.x, from.y, from.z, SoundEvents.TRIDENT_THROW, SoundSource.PLAYERS, 0.9F, 0.8F);
		off(player);
	}

	private static void off(ServerPlayer player) {
		player.removeAttached(RIDE);
		player.resetFallDistance();
		player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, SLOW_FALL_TICKS, 0, true, false, false));
		NEXT.put(player.getUUID(), player.level().getGameTime() + COOLDOWN);
	}

	/** The power went away, or its holder died: the blade with it. */
	static void onLost(ServerPlayer player) {
		NEXT.remove(player.getUUID());
		player.removeAttached(RIDE);
	}
}
