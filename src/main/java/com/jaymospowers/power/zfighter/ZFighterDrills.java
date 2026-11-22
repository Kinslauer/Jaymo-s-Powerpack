package com.jaymospowers.power.zfighter;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.Powers;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;

/**
 * The Training Post's six drills, played on a small screen on the client ({@code ZFighterDrillScreen}): Rhythm, Ki
 * Control, Shadow Boxing, Precision, Memory and Pod Pilot. Each ends with a score (0-100), sent here and paid in TP ({@link #TP_PER_POINT}
 * a point, before weights and heavy gravity), if he's still at a post and that drill has rested {@link #REST} ticks.
 * (0.17.60) The fifth, Memory, also trains Ki Power ({@link #MEMORY_KI_POWER_PER_POINT}).
 * <p>
 * Drills beyond Rhythm must be unlocked by training with a master (a few runs each). Once unlocked, they stay unlocked.
 * Training near your master pays a mentor TP bonus. Each run earns a grade (S to D). Challenge mode strings drills
 * together and restarts on a miss.
 */
public final class ZFighterDrills {

	public static final int RHYTHM = 0;
	public static final int KI_CONTROL = 1;
	public static final int SHADOW_BOXING = 2;
	public static final int PRECISION = 3;
	/** (0.17.60, Andy's NOEA picks) The fifth drill: a sequence of pads is shown, you repeat it, each round longer. */
	public static final int MEMORY = 4;
	/** (Z Fighter gaps 5) The sixth drill: fly a pod through gaps in rock pillars while gravity pulls it down. */
	public static final int POD_PILOT = 5;
	public static final int DRILLS = 6;
	public static final String[] KEYS = {"rhythm", "ki_control", "shadow_boxing", "precision", "memory", "pod_pilot"};
	public static final int MAX_SCORE = 100;
	static final float TP_PER_POINT = 0.5F;
	/**
	 * (0.17.60) Memory also trains Ki Power, as mind training: this many {@link ZFighterGrowth} points of its "meditation"
	 * method for each point scored (a perfect run is 6 of a bar of 600 and more, under the method's usual daily halving).
	 */
	static final float MEMORY_KI_POWER_PER_POINT = 0.06F;
	/** Each drill pays once in this long (ticks). */
	public static final int REST = 2400;
	static final int POST_RANGE = 4;

	/** Grade thresholds (score needed for each grade). S: 90, A: 75, B: 60, C: 40, D: below 40. */
	public static final int[] GRADE_THRESHOLDS = {90, 75, 60, 40, 0};
	public static final String[] GRADE_KEYS = {"S", "A", "B", "C", "D"};
	/** How many runs with a master nearby to unlock each drill (Rhythm is always open). */
	private static final int[] UNLOCK_RUNS = {0, 3, 3, 3, 3, 5};
	/** Mentor TP bonus when training near a master (multiplier on top of normal rate). */
	static final float MENTOR_BONUS = 0.5F;
	/** Maximum share of strength the shadow dummy can take (0.75 = 75%). */
	public static final float MAX_SHADOW_SHARE = 0.75F;

	/** Client → server: drill {@code drill} done with {@code score}. */
	public record Done(int drill, int score) implements CustomPacketPayload {
		public static final Type<Done> TYPE = new Type<>(JaymosPowers.id("zfighter_drill"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Done> CODEC = CustomPacketPayload.codec(
			(p, buf) -> {
				buf.writeVarInt(p.drill);
				buf.writeVarInt(p.score);
			},
			buf -> new Done(buf.readVarInt(), buf.readVarInt()));

		@Override
		public Type<Done> type() {
			return TYPE;
		}
	}

	/** Client → server: challenge mode done with {@code score} and {@code grade}. */
	public record ChallengeDone(int score, String grade) implements CustomPacketPayload {
		public static final Type<ChallengeDone> TYPE = new Type<>(JaymosPowers.id("zfighter_challenge"));
		public static final StreamCodec<RegistryFriendlyByteBuf, ChallengeDone> CODEC = CustomPacketPayload.codec(
			(p, buf) -> {
				buf.writeVarInt(p.score);
				buf.writeUtf(p.grade);
			},
			buf -> new ChallengeDone(buf.readVarInt(), buf.readUtf()));

		@Override
		public Type<ChallengeDone> type() {
			return TYPE;
		}
	}

	/** Player → drill → when it pays again (game time). */
	private static final Map<UUID, long[]> RESTING = new HashMap<>();

	/** Unlocked drills and best grades per player. */
	public static final AttachmentType<DrillData> DRILL_DATA = AttachmentRegistry.create(JaymosPowers.id("zfighter_drill_data"),
		builder -> builder.persistent(DrillData.CODEC).copyOnDeath().syncWith(DrillData.STREAM_CODEC, AttachmentSyncPredicate.targetOnly()));

	/** The shadow dummy stat share (0-0.75) a player chose. */
	public static final AttachmentType<Float> SHADOW_SHARE = AttachmentRegistry.create(JaymosPowers.id("zfighter_shadow_share"),
		builder -> builder.persistent(Codec.FLOAT).copyOnDeath().syncWith(ByteBufCodecs.FLOAT, AttachmentSyncPredicate.targetOnly()));

	/** Drill unlocks and best grades. */
	public record DrillData(int unlockedMask, int[] bestScores, String[] bestGrades) {
		public static final DrillData NONE = new DrillData(1 << RHYTHM, new int[DRILLS], new String[DRILLS]);
		public static final Codec<DrillData> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.INT.optionalFieldOf("unlocked", 1 << RHYTHM).forGetter(DrillData::unlockedMask),
				Codec.INT.listOf().optionalFieldOf("bestScores", java.util.List.of()).forGetter(d -> java.util.Arrays.stream(d.bestScores).boxed().toList()),
				Codec.STRING.listOf().optionalFieldOf("bestGrades", java.util.List.of()).forGetter(d -> java.util.Arrays.stream(d.bestGrades).collect(java.util.stream.Collectors.toList()))
		).apply(i, (unlocked, scores, grades) -> {
				int[] sa = new int[DRILLS];
				String[] ga = new String[DRILLS];
				for (int idx = 0; idx < Math.min(scores.size(), DRILLS); idx++) sa[idx] = scores.get(idx);
				for (int idx = 0; idx < Math.min(grades.size(), DRILLS); idx++) ga[idx] = grades.get(idx);
				return new DrillData(unlocked, sa, ga);
			}));
			public static final StreamCodec<ByteBuf, DrillData> STREAM_CODEC = StreamCodec.composite(
				ByteBufCodecs.VAR_INT, DrillData::unlockedMask,
				ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()), d -> java.util.Arrays.stream(d.bestScores).boxed().toList(),
				ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), d -> java.util.Arrays.stream(d.bestGrades).collect(java.util.stream.Collectors.toList()),
				(unlocked, scores, grades) -> {
					int[] sa = new int[DRILLS];
					String[] ga = new String[DRILLS];
					for (int idx = 0; idx < Math.min(scores.size(), DRILLS); idx++) sa[idx] = scores.get(idx);
					for (int idx = 0; idx < Math.min(grades.size(), DRILLS); idx++) ga[idx] = grades.get(idx);
					return new DrillData(unlocked, sa, ga);
				});

		public boolean isUnlocked(int drill) {
			return (unlockedMask & (1 << drill)) != 0;
		}

		public DrillData withUnlocked(int drill) {
			return new DrillData(unlockedMask | (1 << drill), bestScores, bestGrades);
		}

		public int bestScore(int drill) {
			return drill >= 0 && drill < bestScores.length ? bestScores[drill] : 0;
		}

		public String bestGrade(int drill) {
			return drill >= 0 && drill < bestGrades.length ? bestGrades[drill] : null;
		}

		public DrillData withScore(int drill, int score) {
			int[] nextScores = bestScores.clone();
			nextScores[drill] = Math.max(nextScores[drill], score);
			return new DrillData(unlockedMask, nextScores, bestGrades);
		}

		public DrillData withGrade(int drill, String grade) {
			String[] nextGrades = bestGrades.clone();
			nextGrades[drill] = grade;
			return new DrillData(unlockedMask, bestScores, nextGrades);
		}
	}

	private ZFighterDrills() {
	}

	static void init() {
		PayloadTypeRegistry.serverboundPlay().register(Done.TYPE, Done.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Done.TYPE, (payload, ctx) -> done(ctx.player(), payload.drill(), payload.score()));
		PayloadTypeRegistry.serverboundPlay().register(ChallengeDone.TYPE, ChallengeDone.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(ChallengeDone.TYPE, (payload, ctx) -> challengeDone(ctx.player(), payload.score(), payload.grade()));
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> RESTING.clear());
		// a Z Fighter's use of the post is the drill screen (the client opens it): nothing else happens here
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> !level.isClientSide() && hand == InteractionHand.MAIN_HAND
			&& level.getBlockState(hit.getBlockPos()).is(ZFighterItems.TRAINING_POST) && Powers.has(player, ZFighterPower.INSTANCE)
			? InteractionResult.SUCCESS : InteractionResult.PASS);
	}

	/** Whether a Training Post stands within reach of {@code player}. Both sides. */
	public static boolean nearPost(net.minecraft.world.entity.player.Player player) {
		BlockPos at = player.blockPosition();
		for (BlockPos pos : BlockPos.betweenClosed(at.offset(-POST_RANGE, -2, -POST_RANGE), at.offset(POST_RANGE, 2, POST_RANGE))) {
			if (player.level().getBlockState(pos).is(ZFighterItems.TRAINING_POST)) {
				return true;
			}
		}
		return false;
	}

	/** Whether a master (good or dark) is near {@code player}. */
	public static boolean nearMaster(ServerPlayer player) {
		for (var entity : player.level().getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class,
				player.getBoundingBox().inflate(ZFighterMasters.NEAR),
				e -> e.entityTags().contains(ZFighterMasters.TAG) || e.entityTags().contains(ZFighterMasters.DARK_TAG))) {
			return true;
		}
		return false;
	}

	private static void done(ServerPlayer player, int drill, int score) {
		if (drill < 0 || drill >= DRILLS || !Powers.has(player, ZFighterPower.INSTANCE) || !nearPost(player)) {
			return;
		}
		DrillData data = player.getAttached(DRILL_DATA);
		if (data == null || !data.isUnlocked(drill)) {
			return;
		}
		score = Math.clamp(score, 0, MAX_SCORE);
		long now = player.level().getGameTime();
		long[] resting = RESTING.computeIfAbsent(player.getUUID(), id -> new long[DRILLS]);
		Component name = Component.translatable("drill.jaymospowers.zfighter." + KEYS[drill]);
		if (now < resting[drill]) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.zfighter_drill_rest", name, (resting[drill] - now + 19) / 20)
				.withStyle(ChatFormatting.GRAY));
			return;
		}
		resting[drill] = now + REST;

		// Mentor bonus if near a master
		float rate = ZFighterTraining.rate(player);
		if (nearMaster(player)) {
			rate *= (1.0F + MENTOR_BONUS);
		}

		float tp = score * TP_PER_POINT;
		ZFighterTraining.earn(player, tp * rate / ZFighterTraining.rate(player));
		player.sendSystemMessage(Component.translatable("message.jaymospowers.zfighter_drill_done", name, score, Math.round(tp * rate))
			.withStyle(ChatFormatting.GOLD));

		// Update best score and grade
		String grade = gradeFor(score);
		player.setAttached(DRILL_DATA, data.withScore(drill, score).withGrade(drill, grade));

		// Track unlock progress near master
		trackMasterRun(player, drill);

		if (drill == MEMORY && score > 0) {
			ZFighterGrowth.train(player, ZFighterGrowth.MEDITATION, score * MEMORY_KI_POWER_PER_POINT);
		}
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.5F,
			0.8F + score / 100.0F);
	}

	private static void challengeDone(ServerPlayer player, int score, String grade) {
		if (!Powers.has(player, ZFighterPower.INSTANCE) || !nearPost(player)) {
			return;
		}
		DrillData data = player.getAttached(DRILL_DATA);
		if (data == null) {
			return;
		}
		score = Math.clamp(score, 0, MAX_SCORE);
		float tp = score * TP_PER_POINT * 1.5F;
		ZFighterTraining.earn(player, tp * ZFighterTraining.rate(player));
		player.sendSystemMessage(Component.translatable("message.jaymospowers.zfighter_challenge_done", score, grade, Math.round(tp * ZFighterTraining.rate(player)))
			.withStyle(ChatFormatting.GOLD));
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 0.6F, 1.4F);
	}

	/** Returns the grade (S/A/B/C/D) for a given score. */
	public static String gradeFor(int score) {
		for (int i = 0; i < GRADE_THRESHOLDS.length; i++) {
			if (score >= GRADE_THRESHOLDS[i]) {
				return GRADE_KEYS[i];
			}
		}
		return GRADE_KEYS[GRADE_KEYS.length - 1];
	}

	/** Whether {@code drill} is unlocked for {@code player}. */
	public static boolean isUnlocked(net.minecraft.world.entity.player.Player player, int drill) {
		DrillData data = player.getAttached(DRILL_DATA);
		return data != null && data.isUnlocked(drill);
	}

	/** Called when a drill is completed near a master; tracks unlock progress. */
	public static void trackMasterRun(ServerPlayer player, int drill) {
		if (drill <= RHYTHM) return;
		DrillData data = player.getAttached(DRILL_DATA);
		if (data == null || data.isUnlocked(drill)) return;
		if (nearMaster(player)) {
			player.setAttached(DRILL_DATA, data.withUnlocked(drill));
			player.sendSystemMessage(Component.translatable("message.jaymospowers.zfighter_drill_unlocked",
				Component.translatable("drill.jaymospowers.zfighter." + KEYS[drill])).withStyle(ChatFormatting.AQUA));
		}
	}
}