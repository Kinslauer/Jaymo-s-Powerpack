package com.jaymospowers.core.finisher;

import com.jaymospowers.JaymosPowers;
import io.netty.buffer.ByteBuf;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Timed prompts on a finisher (Andy, on the QTE Finishers mod: its idea only, our own code). A prompted finisher
 * ({@link #PROMPTED}) shows one of the four ability keys before each of its blows ({@link #asks}); the right key inside
 * the window lands the blow, and a wrong key or none throws you off ({@link #miss}): the finisher ends and they live.
 * <p>
 * Which key each blow asks for comes from the finisher's own start time ({@link #key}), so both sides know it with no
 * packet. The first prompted finisher is a great foe's: one that is {@link #OPEN} (Kingdoms sets it on a boss brought to
 * a knee with little health left) can be finished on its knee like someone downed, by the same hold.
 */
public final class Prompts {
	/** How long before its blow a prompt comes up (ticks). */
	public static final int WINDOW = 14;
	/** The client stops taking a key this long before the blow, so its answer is with the server in time. */
	public static final int LEAD = 3;
	/** Blows closer than this to the last prompted one go unasked (a dagger's quick stabs). */
	static final int GAP = 8;
	/** Thrown off: how hard, how high, and what it costs. */
	static final double THROWN = 1.1, THROWN_UP = 0.4;
	static final float MISS_HURT = 4.0F;
	/** {@link #PASSED}'s value once a wrong key was pressed. */
	private static final int FAILED = -1;

	/** On a living thing: it can be finished where it kneels, by a prompted finisher. Synced to everyone, not saved. */
	public static final AttachmentType<Boolean> OPEN = AttachmentRegistry.create(JaymosPowers.id("finish_open"),
		builder -> builder.syncWith(ByteBufCodecs.BOOL, AttachmentSyncPredicate.all()));
	/** On a player: the finisher they are playing asks for keys. Synced to everyone, not saved. */
	public static final AttachmentType<Boolean> PROMPTED = AttachmentRegistry.create(JaymosPowers.id("finish_prompted"),
		builder -> builder.syncWith(ByteBufCodecs.BOOL, AttachmentSyncPredicate.all()));

	/** Client → server: ability key {@code key} (0-3) pressed for the finisher's blow number {@code beat}. */
	public record Answer(int beat, int key) implements CustomPacketPayload {
		public static final Type<Answer> TYPE = new Type<>(JaymosPowers.id("finisher_prompt"));
		public static final StreamCodec<ByteBuf, Answer> CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, Answer::beat,
			ByteBufCodecs.VAR_INT, Answer::key, Answer::new);

		@Override
		public Type<Answer> type() {
			return TYPE;
		}
	}

	/** The blows each player has answered rightly (a bit per blow), or {@link #FAILED}. */
	private static final Map<UUID, Integer> PASSED = new HashMap<>();

	private Prompts() {
	}

	static void register() {
		PayloadTypeRegistry.serverboundPlay().register(Answer.TYPE, Answer.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Answer.TYPE, (payload, ctx) -> answered(ctx.player(), payload.beat(), payload.key()));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> PASSED.clear());
	}

	// ---- queries (both sides) ---------------------------------------------------------------------------------------------

	/** Whether {@code entity} can be finished where it kneels. */
	public static boolean open(Entity entity) {
		return entity.getAttachedOrElse(OPEN, false);
	}

	/** Kingdoms' boss posture, by name: {@code body} is brought to a knee and may be finished there ({@code on}), or is up again. */
	public static void offer(LivingEntity body, boolean on) {
		if (on) {
			body.setAttached(OPEN, true);
		} else {
			body.removeAttached(OPEN);
		}
	}

	/** Whether the finisher {@code player} is playing asks for keys. */
	public static boolean prompted(Entity player) {
		return player.getAttachedOrElse(PROMPTED, false);
	}

	/** Whether blow number {@code beat} of {@code kind} asks for a key: the first, and each one {@link #GAP} after the last asked. */
	public static boolean asks(Finishers.Kind kind, int beat) {
		int last = -GAP;
		for (int i = 0; i <= beat && i < kind.beats.length; i++) {
			if (kind.beats[i] - last >= GAP) {
				if (i == beat) {
					return true;
				}
				last = kind.beats[i];
			}
		}
		return false;
	}

	/** The tick blow number {@code beat}'s prompt comes up: {@link #WINDOW} before it, never before the blow ahead of it has landed. */
	public static int opens(Finishers.Kind kind, int beat) {
		return Math.max(kind.beats[beat] - WINDOW, beat == 0 ? 1 : kind.beats[beat - 1] + 1);
	}

	/** Which ability key (0-3) blow number {@code beat} of {@code finish} asks for. */
	public static int key(Finishers.Finish finish, int beat) {
		return (int) (((finish.start() + beat * 31L) * 0x9E3779B97F4A7C15L) >>> 62);
	}

	// ---- the server's side -----------------------------------------------------------------------------------------------

	/** A finisher begins on {@code body}: prompted if they are open to one, or it is a power's own ({@link PowerFinishers}). */
	static void begin(ServerPlayer player, LivingEntity body, Finishers.Kind kind) {
		PASSED.remove(player.getUUID());
		if (open(body) || kind.strike == Finishers.Strike.POWER) {
			player.setAttached(PROMPTED, true);
		}
	}

	static void end(ServerPlayer player) {
		PASSED.remove(player.getUUID());
		player.removeAttached(PROMPTED);
	}

	private static void answered(ServerPlayer player, int beat, int key) {
		Finishers.Finish finish = Finishers.of(player);
		if (finish == null || !prompted(player) || PASSED.getOrDefault(player.getUUID(), 0) == FAILED) {
			return;
		}
		Finishers.Kind kind = finish.type();
		if (beat < 0 || beat >= kind.beats.length || !asks(kind, beat)) {
			return;
		}
		int t = (int) (player.level().getGameTime() - finish.start());
		if (t >= kind.beats[beat]) {
			// (too late: the blow's tick has already judged it)
			return;
		}
		if (key != key(finish, beat) || t < opens(kind, beat) - LEAD) {
			PASSED.put(player.getUUID(), FAILED);
		} else {
			PASSED.merge(player.getUUID(), 1 << beat, (a, b) -> a | b);
		}
	}

	/** Whether {@code player}'s prompted finisher fails at tick {@code t}: a wrong key pressed, or a blow due with its key not given. */
	static boolean missed(ServerPlayer player, Finishers.Kind kind, int t) {
		if (!prompted(player)) {
			return false;
		}
		int passed = PASSED.getOrDefault(player.getUUID(), 0);
		if (passed == FAILED) {
			return true;
		}
		for (int i = 0; i < kind.beats.length; i++) {
			if (t == kind.beats[i] && asks(kind, i) && (passed & 1 << i) == 0) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Thrown off: {@code player} flung back from {@code body} and hurt by it, and it gets up: a kneeling boss is no longer
	 * open, a downed mob is back on its feet (a downed player stays down).
	 */
	static void miss(ServerLevel level, ServerPlayer player, LivingEntity body) {
		body.removeAttached(OPEN);
		if (!(body instanceof net.minecraft.world.entity.player.Player) && com.jaymospowers.core.wounds.Downed.isDowned(body)) {
			com.jaymospowers.core.wounds.Downed.getUp(level, body);
		}
		Vec3 away = player.position().subtract(body.position()).multiply(1.0, 0.0, 1.0);
		away = away.lengthSqr() < 1.0E-4 ? player.getLookAngle().multiply(-1.0, 0.0, -1.0) : away;
		away = away.normalize().scale(THROWN);
		player.setDeltaMovement(away.x, THROWN_UP, away.z);
		player.needsSync = true;
		player.syncVelocity = true;
		player.hurtServer(level, level.damageSources().mobAttack(body), MISS_HURT);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.HOSTILE, 1.0F, 0.7F);
		player.sendOverlayMessage(Component.translatable("message.jaymospowers.finisher.thrown_off", body.getDisplayName()).withStyle(ChatFormatting.GRAY));
	}
}
