package com.jaymospowers.power.web;

import com.jaymospowers.JaymosPowers;
import io.netty.buffer.ByteBuf;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
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
import net.minecraft.world.entity.player.Player;

/**
 * Wings: a host the symbiote has wholly taken in (a whole bond) can spread two great spans of goo from its back, red as
 * raw meat between black ribs, wider than a house, every strain alike (drawn by the client's {@code WebSymbioteWingsLayer}
 * for everyone). The radial menu's Wings chip spreads or folds them ({@link #toggle}); out, they carry the host down a
 * long way (the client glides, {@code WebSymbioteWingsClient}) and a Jump in the air beats them for a climb: {@link #FLAPS}
 * strong flaps, each costing {@link #FLAP_COST} satiety, one coming back every {@link #RECHARGE_TICKS} (all of them on
 * the ground). They fold by themselves after {@link #GROUND_TICKS} on the ground, in water, riding, or when it takes the
 * wheel. The fall is the symbiote's to take, never the host's.
 */
public final class WebSymbioteWings {
	/** Strong flaps in a row, satiety each costs, and ticks for one flap to come back (flaps are also counted by the client, same numbers). */
	public static final int FLAPS = 4, FLAP_COST = 2, RECHARGE_TICKS = 40;
	/** Ticks the wings stay out on the ground before they fold of their own accord. */
	public static final int GROUND_TICKS = 100;
	/** The beat's length (ticks) for the drawn flap. */
	public static final int FLAP_ANIM_TICKS = 10;

	/** When the wings last beat (game time), for everyone to draw. */
	public static final AttachmentType<Long> FLAPPED = AttachmentRegistry.create(JaymosPowers.id("web_symbiote_flapped"),
		builder -> builder.syncWith(ByteBufCodecs.VAR_LONG, AttachmentSyncPredicate.all()));

	/** Client → server: a strong flap, in the air with the wings out. */
	public record Flap() implements CustomPacketPayload {
		public static final Type<Flap> TYPE = new Type<>(JaymosPowers.id("web_symbiote_flap"));
		public static final StreamCodec<ByteBuf, Flap> CODEC = StreamCodec.unit(new Flap());

		@Override
		public Type<Flap> type() {
			return TYPE;
		}
	}

	/** Per host: flaps used, when one last came back, ticks on the ground with the wings out. */
	private static final Map<UUID, int[]> LIVE = new ConcurrentHashMap<>();
	private static final Map<UUID, Long> RECHARGED = new ConcurrentHashMap<>();

	private WebSymbioteWings() {
	}

	static void register() {
		PayloadTypeRegistry.serverboundPlay().register(Flap.TYPE, Flap.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Flap.TYPE, (payload, ctx) -> flap(ctx.player()));
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				tick(player);
			}
		});
	}

	static void forget(ServerPlayer player) {
		LIVE.remove(player.getUUID());
		RECHARGED.remove(player.getUUID());
	}

	/** Its wings are spread. */
	public static boolean out(Player player) {
		return WebSymbiote.isBonded(player) && WebSymbiote.state(player).has(WebSymbioteState.WINGS);
	}

	/** The bond is whole (or lent to you) and it isn't settling in: it can give you wings. */
	public static boolean available(Player player) {
		return WebSymbiote.isBonded(player) && !WebSymbioteBond.dormant(player)
			&& (player.isCreative() || WebSymbiote.empowered(player) || WebSymbiote.state(player).bond() >= WebSymbiote.BOND_MAX);
	}

	/** The radial menu's Wings chip: spread them, or fold them. */
	static void toggle(ServerPlayer player) {
		if (!WebSymbiote.isBonded(player) || !player.isAlive() || player.isSpectator() || WebSymbiote.driving(player)) {
			return;
		}
		WebSymbioteState state = WebSymbiote.state(player);
		if (state.has(WebSymbioteState.WINGS)) {
			fold(player, true);
			return;
		}
		if (!available(player)) {
			WebSymbiote.whisper(player, "wings.locked");
			return;
		}
		if (player.isPassenger() || player.isInWater()) {
			return;
		}
		WebSymbiote.set(player, state.withFlag(WebSymbioteState.WINGS, true));
		LIVE.put(player.getUUID(), new int[] {0, 0});
		RECHARGED.put(player.getUUID(), player.level().getGameTime());
		ServerLevel level = player.level();
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PHANTOM_FLAP, SoundSource.PLAYERS, 1.2F, 0.5F);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SLIME_SQUISH, SoundSource.PLAYERS, 1.0F, 0.4F);
		level.sendParticles(WebSymbiote.dust(1.4F), player.getX(), player.getY() + 1.3, player.getZ(), 24, 0.5, 0.5, 0.5, 0.0);
		if (player.getRandom().nextInt(3) == 0) {
			WebSymbiote.whisper(player, "wings.out." + (1 + player.getRandom().nextInt(2)));
		}
	}

	/** Folds them away. */
	static void fold(ServerPlayer player, boolean sound) {
		WebSymbioteState state = WebSymbiote.state(player);
		if (state.has(WebSymbioteState.WINGS)) {
			WebSymbiote.set(player, state.withFlag(WebSymbioteState.WINGS, false));
		}
		LIVE.remove(player.getUUID());
		RECHARGED.remove(player.getUUID());
		player.removeAttached(FLAPPED);
		if (sound) {
			player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PHANTOM_FLAP, SoundSource.PLAYERS, 0.8F, 0.7F);
		}
	}

	/** A strong flap, from the client: paid for in satiety, and counted. */
	private static void flap(ServerPlayer player) {
		int[] live = LIVE.get(player.getUUID());
		if (live == null || !out(player) || !player.isAlive() || player.onGround() || player.isInWater() || WebSymbiote.driving(player)) {
			return;
		}
		recharge(player, live);
		WebSymbioteState state = WebSymbiote.state(player);
		if (live[0] >= FLAPS || state.satiety() < FLAP_COST && !player.isCreative()) {
			return;
		}
		live[0]++;
		if (!player.isCreative()) {
			WebSymbiote.set(player, state.withSatiety(state.satiety() - FLAP_COST));
		}
		ServerLevel level = player.level();
		player.setAttached(FLAPPED, level.getGameTime());
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PHANTOM_FLAP, SoundSource.PLAYERS, 1.6F, 0.6F);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ELYTRA_FLYING, SoundSource.PLAYERS, 0.8F, 0.7F);
		level.sendParticles(WebSymbiote.dust(1.2F), player.getX(), player.getY() + 1.2, player.getZ(), 10, 0.9, 0.5, 0.9, 0.0);
	}

	/** A flap comes back every {@link #RECHARGE_TICKS}. */
	private static void recharge(ServerPlayer player, int[] live) {
		long now = player.level().getGameTime();
		long last = RECHARGED.getOrDefault(player.getUUID(), now);
		if (live[0] > 0 && now - last >= RECHARGE_TICKS) {
			live[0]--;
			last = now;
		} else if (live[0] == 0) {
			last = now;
		}
		RECHARGED.put(player.getUUID(), last);
	}

	/** Every tick, for a bonded host: the wings fold when they should, and the fall is never the host's. */
	static void tick(ServerPlayer player) {
		if (!WebSymbiote.state(player).has(WebSymbioteState.WINGS)) {
			return;
		}
		if (!out(player) || !player.isAlive() || player.isSpectator() || player.isPassenger() || player.isInWater() || WebSymbiote.driving(player)
			|| !available(player)) {
			fold(player, false);
			return;
		}
		int[] live = LIVE.computeIfAbsent(player.getUUID(), id -> new int[] {0, 0});
		if (player.onGround()) {
			live[0] = 0;
			if (++live[1] > GROUND_TICKS) {
				fold(player, true);
				return;
			}
		} else {
			live[1] = 0;
			recharge(player, live);
			// the symbiote takes the fall
			player.resetFallDistance();
			if (player.tickCount % 6 == 0 && player.getDeltaMovement().y < -0.2) {
				player.level().sendParticles(ParticleTypes.SQUID_INK, player.getX(), player.getY() + 1.4, player.getZ(), 2, 1.2, 0.4, 0.5, 0.0);
			}
		}
	}
}
