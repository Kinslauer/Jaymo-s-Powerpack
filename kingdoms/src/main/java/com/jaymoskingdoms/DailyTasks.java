package com.jaymoskingdoms;

import com.jaymoskingdoms.money.Money;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Locale;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityCombatEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;

/**
 * The daily task, a page of the H menu (World): one small task a day, picked from what is already counted: blows struck at
 * living things, ground walked, hostile monsters felled. It has a progress bar, a timer to the next day, a streak of days
 * claimed in a row, and a Claim button that pays coin (more for a longer streak, up to {@link #STREAK_MOST} days).
 * Nothing is lost for skipping a day: the task is simply a new one, the streak starts again at one on the next claim, and
 * the best streak is kept.
 *
 * <p>The day is the settlements' day ({@link #today}, the overworld's clock). The state lives in {@link #STATE}, saved, kept
 * through death and synced to its player, and is set for a new day when the player is next seen after it began.
 */
public final class DailyTasks {
	/** The kinds of task: what is counted, and the goal's smallest and largest (the goal is picked from these by the day). */
	public enum Kind {
		/** Blows struck at living things. */
		STRIKE(20, 60, 5),
		/** Ground walked, in blocks. */
		WALK(400, 1500, 50),
		/** Hostile monsters defeated. */
		SLAY(4, 12, 1);

		public final int least, most, round;

		Kind(int least, int most, int round) {
			this.least = least;
			this.most = most;
			this.round = round;
		}

		public String key() {
			return name().toLowerCase(Locale.ROOT);
		}
	}

	/** The coin a claim pays (emeralds' worth): this, and {@link #PER_STREAK_DAY} more for each day of the streak before today's. */
	public static final int BASE_PAY = 3, PER_STREAK_DAY = 1, STREAK_MOST = 7;
	/** A day's length in ticks. */
	public static final long DAY = 24000L;

	/**
	 * The task and the streak: the day it is for, its kind's ordinal, the goal and the progress so far, whether it was claimed, the
	 * game time the day ends at (for the timer), the streak (days claimed in a row, as of the last claim), the day of the last claim,
	 * the best streak and the distance walked when last counted (cm, to tell what is new).
	 */
	public record State(long day, int kind, int goal, int progress, boolean claimed, long ends, int streak, long lastClaim, int best, long walked) {
		public static final State EMPTY = new State(-1L, 0, 1, 0, false, 0L, 0, -10L, 0, 0L);

		public static final Codec<State> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.LONG.fieldOf("day").forGetter(State::day),
				Codec.INT.fieldOf("kind").forGetter(State::kind),
				Codec.INT.fieldOf("goal").forGetter(State::goal),
				Codec.INT.fieldOf("progress").forGetter(State::progress),
				Codec.BOOL.fieldOf("claimed").forGetter(State::claimed),
				Codec.LONG.fieldOf("ends").forGetter(State::ends),
				Codec.INT.fieldOf("streak").forGetter(State::streak),
				Codec.LONG.fieldOf("last_claim").forGetter(State::lastClaim),
				Codec.INT.fieldOf("best").forGetter(State::best),
				Codec.LONG.fieldOf("walked").forGetter(State::walked))
			.apply(i, State::new));

		public Kind kindOf() {
			Kind[] all = Kind.values();
			return all[Math.floorMod(this.kind, all.length)];
		}

		public boolean done() {
			return this.progress >= this.goal;
		}

		State with(int progress) {
			return new State(day, kind, goal, Math.min(goal, progress), claimed, ends, streak, lastClaim, best, walked);
		}

		State walking(long walked) {
			return new State(day, kind, goal, progress, claimed, ends, streak, lastClaim, best, walked);
		}
	}

	public static final AttachmentType<State> STATE = AttachmentRegistry.create(JaymosKingdoms.id("daily_task"),
		builder -> builder.persistent(State.CODEC).copyOnDeath().syncWith(ByteBufCodecs.fromCodec(State.CODEC), AttachmentSyncPredicate.targetOnly()));

	/** Player → server: claim today's task. */
	public record Claim() implements CustomPacketPayload {
		public static final Type<Claim> TYPE = new Type<>(JaymosKingdoms.id("daily_claim"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Claim> CODEC = StreamCodec.unit(new Claim());

		@Override
		public Type<Claim> type() {
			return TYPE;
		}
	}

	private DailyTasks() {
	}

	static void register() {
		PayloadTypeRegistry.serverboundPlay().register(Claim.TYPE, Claim.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Claim.TYPE, (payload, context) -> claim(context.player()));
		ServerTickEvents.END_SERVER_TICK.register(DailyTasks::tick);
		AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
			if (player instanceof ServerPlayer sp && entity instanceof LivingEntity target && target.isAlive() && !sp.isSpectator()) {
				count(sp, Kind.STRIKE, 1);
			}
			return InteractionResult.PASS;
		});
		ServerEntityCombatEvents.AFTER_KILLED_OTHER_ENTITY.register((level, killer, killed, source) -> {
			if (killer instanceof ServerPlayer sp && killed instanceof Enemy) {
				count(sp, Kind.SLAY, 1);
			}
		});
	}

	/** The settlements' day number: the overworld's clock over a day. */
	public static long today(MinecraftServer server) {
		return Math.floorDiv(server.overworld().getOverworldClockTime(), DAY);
	}

	public static State of(ServerPlayer player) {
		return player.getAttachedOrElse(STATE, State.EMPTY);
	}

	/** The coin a claim pays for a streak of {@code streak} days (counting today's). */
	public static int pay(int streak) {
		return BASE_PAY + PER_STREAK_DAY * (Math.min(STREAK_MOST, Math.max(1, streak)) - 1);
	}

	/** The streak {@code s} stands at on {@code day}: kept if the last claim was yesterday or today, else none. */
	public static int streakOn(State s, long day) {
		return s.lastClaim() >= day - 1L ? s.streak() : 0;
	}

	private static int walkedCm(ServerPlayer player) {
		var stats = player.getStats();
		return stats.getValue(Stats.CUSTOM.get(Stats.WALK_ONE_CM)) + stats.getValue(Stats.CUSTOM.get(Stats.SPRINT_ONE_CM));
	}

	/** Sets a new task for {@code day} when the player's is for an older one. */
	private static State fresh(ServerPlayer player, long day) {
		State s = of(player);
		if (s.day() == day) {
			return s;
		}
		MinecraftServer server = player.level().getServer();
		// the same day gives the same task to everyone's choice of kind, and each player's goal sits in the kind's range
		Kind[] kinds = Kind.values();
		Kind kind = kinds[(int)Math.floorMod(day, (long)kinds.length)];
		int span = (kind.most - kind.least) / kind.round;
		int goal = kind.least + kind.round * (int)Math.floorMod(day * 31L + player.getUUID().hashCode(), (long)span + 1L);
		long ends = server.overworld().getGameTime() + DAY - Math.floorMod(server.overworld().getOverworldClockTime(), DAY);
		State next = new State(day, kind.ordinal(), goal, 0, false, ends, s.streak(), s.lastClaim(), s.best(), walkedCm(player));
		player.setAttached(STATE, next);
		return next;
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % 20 != 0) {
			return;
		}
		long day = today(server);
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			State s = fresh(player, day);
			if (s.kindOf() == Kind.WALK && !s.done() && !s.claimed()) {
				long now = walkedCm(player);
				long metres = (now - s.walked()) / 100L;
				if (now < s.walked()) {
					player.setAttached(STATE, s.walking(now));
				} else if (metres > 0L) {
					// (whole blocks only: the rest stays to be counted next time)
					State next = s.with(s.progress() + (int)metres).walking(s.walked() + metres * 100L);
					player.setAttached(STATE, next);
				}
			}
		}
	}

	/** Counts {@code n} of {@code kind} towards {@code player}'s task today, if it is that kind and still open. */
	private static void count(ServerPlayer player, Kind kind, int n) {
		State s = of(player);
		if (s.day() != today(player.level().getServer()) || s.kindOf() != kind || s.done() || s.claimed()) {
			return;
		}
		State next = s.with(s.progress() + n);
		player.setAttached(STATE, next);
		if (next.done()) {
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.daily.done"));
		}
	}

	private static void claim(ServerPlayer player) {
		long day = today(player.level().getServer());
		State s = fresh(player, day);
		if (!s.done() || s.claimed()) {
			return;
		}
		int streak = s.lastClaim() == day - 1L ? s.streak() + 1 : s.lastClaim() == day ? s.streak() : 1;
		long milli = Money.worth(pay(streak));
		Money.pay(player, milli);
		player.setAttached(STATE, new State(s.day(), s.kind(), s.goal(), s.progress(), true, s.ends(), streak, day, Math.max(s.best(), streak), s.walked()));
		ServerLevel level = player.level();
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.7F, 1.1F);
		player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.daily.claimed", Money.textPaid(player, milli), streak));
	}

	/** The coin a claim of {@code s}'s task pays now, for the page (the streak a claim would make, today's). */
	public static int payNow(State s) {
		return pay(s.lastClaim() == s.day() - 1L ? s.streak() + 1 : s.lastClaim() == s.day() ? s.streak() : 1);
	}

	/** The streak {@code s} stands at today, for the page: kept if the last claim was yesterday or today, else none. */
	public static int streakNow(State s) {
		return streakOn(s, s.day());
	}
}
