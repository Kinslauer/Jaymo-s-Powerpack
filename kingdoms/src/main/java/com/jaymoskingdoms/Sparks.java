package com.jaymoskingdoms;

import com.jaymoskingdoms.LifeStory.Start;
import com.mojang.serialization.Codec;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Sparks for playing true (fifth edition's inspiration, ideals, bonds and flaws). After heritage and class a character picks one
 * ideal, one bond and one flaw from short lists that fit their start ({@link #options}). Acting on them ({@link #act}) earns a
 * spark: keeping your word for an ideal of honour, a lesson for a teacher, giving in to your temper at a bad moment for a flaw.
 * You hold one at most. A spark turns your next failed parry ({@code Guard}), failed lockpick ({@link Lockpicking}) or fear
 * ({@link Fear#expose}) into a success ({@link #rescue}), and can be given to a friend from the K screen ({@link Give}).
 */
public final class Sparks {
	/** The least ticks between two sparks earned (five minutes), so a deed repeated is not farmed. */
	static final long GAP_TICKS = 6000L;
	/** A friend must be this near (blocks) to be given a spark. */
	static final double GIVE_RANGE = 8.0;
	/** The sparks' colour. */
	static final int COLOR = 0xFFD34A;

	/** The deeds that earn sparks; each ideal, bond and flaw lists the ones it answers to. */
	public enum Act {
		KEPT_WORD, PAID_TOLL, GIFT, FREED, LEARNED, PERSUADED, LIED, THREATENED, CAROUSED, CHEATED, WON_BIG
	}

	/** The master lists (lang {@code spark.<kind>.<key>}); a start offers three of each. */
	public static final List<String> IDEALS = List.of("honour", "kindness", "freedom", "knowledge", "wealth", "cunning");
	public static final List<String> BONDS = List.of("kin", "home", "friend", "vow", "teacher", "debt");
	public static final List<String> FLAWS = List.of("temper", "greed", "drink", "pride");

	private static final Map<String, Set<Act>> IDEAL_ACTS = Map.of(
		"honour", Set.of(Act.KEPT_WORD, Act.PAID_TOLL), "kindness", Set.of(Act.GIFT, Act.FREED), "freedom", Set.of(Act.FREED, Act.CAROUSED),
		"knowledge", Set.of(Act.LEARNED), "wealth", Set.of(Act.WON_BIG, Act.PERSUADED), "cunning", Set.of(Act.LIED, Act.PERSUADED));
	private static final Map<String, Set<Act>> BOND_ACTS = Map.of(
		"kin", Set.of(Act.GIFT), "home", Set.of(Act.PAID_TOLL, Act.GIFT), "friend", Set.of(Act.GIFT, Act.CAROUSED),
		"vow", Set.of(Act.KEPT_WORD), "teacher", Set.of(Act.LEARNED), "debt", Set.of(Act.KEPT_WORD));
	/** A flaw only earns when it costs: the act went badly. */
	private static final Map<String, Set<Act>> FLAW_ACTS = Map.of(
		"temper", Set.of(Act.THREATENED), "greed", Set.of(Act.CHEATED), "drink", Set.of(Act.CAROUSED), "pride", Set.of(Act.LIED));

	/** The three picks, as {ideal, bond, flaw} keys. Saved, kept through death, synced to its own player. */
	public static final AttachmentType<List<String>> PICKS = AttachmentRegistry.create(JaymosKingdoms.id("spark_picks"),
		builder -> builder.persistent(Codec.STRING.listOf()).copyOnDeath()
			.syncWith(ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), AttachmentSyncPredicate.targetOnly()));
	/** Whether a spark is held. Saved, synced to its own player. */
	public static final AttachmentType<Boolean> HELD = AttachmentRegistry.create(JaymosKingdoms.id("spark_held"),
		builder -> builder.persistent(Codec.BOOL).copyOnDeath().syncWith(ByteBufCodecs.BOOL.cast(), AttachmentSyncPredicate.targetOnly()));
	private static final AttachmentType<Long> LAST = AttachmentRegistry.create(JaymosKingdoms.id("spark_last"),
		builder -> builder.persistent(Codec.LONG));

	/** Player → server: give the spark held to {@code friend}. */
	public record Give(UUID friend) implements CustomPacketPayload {
		public static final Type<Give> TYPE = new Type<>(JaymosKingdoms.id("spark_give"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Give> CODEC = StreamCodec.composite(
			net.minecraft.core.UUIDUtil.STREAM_CODEC, Give::friend, Give::new);

		@Override
		public Type<Give> type() {
			return TYPE;
		}
	}

	private static final Map<Start, Integer> THEME = new EnumMap<>(Start.class);

	static {
		for (Start s : List.of(Start.VILLAGE, Start.CITY, Start.TRADER, Start.NOBODIES, Start.PUP, Start.HEIRLOOM, Start.SNOWED, Start.BURNED)) {
			THEME.put(s, 0);
		}
		for (Start s : List.of(Start.BANISHED, Start.ROCK_BOTTOM, Start.SENTENCED, Start.EVICTED, Start.HIDEOUT, Start.CAST_OUT, Start.CHAINED,
			Start.CAPTIVE, Start.GREENSKIN, Start.UNARMED)) {
			THEME.put(s, 1);
		}
		for (Start s : List.of(Start.SWORN, Start.LAB, Start.ANDROID, Start.MOON_CURSED)) {
			THEME.put(s, 3);
		}
	}

	/** The three of each kind a theme offers, as indices into {@link #IDEALS}, {@link #BONDS} and {@link #FLAWS}. */
	private static final int[][][] OFFERS = {
		{{0, 1, 4}, {0, 1, 2}, {1, 3, 0}},
		{{2, 5, 1}, {2, 5, 3}, {0, 2, 3}},
		{{2, 3, 0}, {1, 4, 3}, {0, 3, 1}},
		{{0, 3, 5}, {3, 4, 0}, {3, 0, 1}}};

	private Sparks() {
	}

	static void register() {
		PayloadTypeRegistry.serverboundPlay().register(Give.TYPE, Give.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Give.TYPE, (payload, context) -> give(context.player(), payload.friend()));
		PowersCompat.sparkParry(Sparks::rescue);
	}

	// ---- choosing ----------------------------------------------------------------------------------------------------

	/** Which of the four lists of offers {@code start} (null: a road still to be told) gets. */
	public static int theme(@Nullable Start start) {
		return start == null ? 2 : THEME.getOrDefault(start, 2);
	}

	/** The three keys of {@code kind} (0 ideal, 1 bond, 2 flaw) a start offers. */
	public static List<String> options(@Nullable Start start, int kind) {
		List<String> master = kind == 0 ? IDEALS : kind == 1 ? BONDS : FLAWS;
		int[] pick = OFFERS[theme(start)][kind];
		return List.of(master.get(pick[0]), master.get(pick[1]), master.get(pick[2]));
	}

	/**
	 * (From {@link LifeStory}) Takes the three picks of the story screen, {@code packed} as a position 1-3 (0: none) in each of the
	 * three lists, 3 bits apiece. Anything unlisted is ignored: the client is not trusted.
	 */
	static void choose(ServerPlayer player, int packed, @Nullable Start start) {
		String[] keys = new String[3];
		for (int kind = 0; kind < 3; kind++) {
			int at = (packed >> (3 * kind)) & 7;
			List<String> offered = options(start, kind);
			if (at < 1 || at > offered.size()) {
				player.removeAttached(PICKS);
				return;
			}
			keys[kind] = offered.get(at - 1);
		}
		player.setAttached(PICKS, List.of(keys));
		player.removeAttached(HELD);
	}

	/** The key {@code player} picked of {@code kind} (0 ideal, 1 bond, 2 flaw), or null. */
	public static @Nullable String pick(net.minecraft.world.entity.player.Player player, int kind) {
		List<String> picks = player.getAttached(PICKS);
		return picks == null || picks.size() != 3 ? null : picks.get(kind);
	}

	// ---- earning and spending -----------------------------------------------------------------------------------------

	public static boolean held(net.minecraft.world.entity.player.Player player) {
		return player.getAttachedOrElse(HELD, false);
	}

	/**
	 * {@code player} did {@code act}. If it is something their ideal or bond answers to (or, when it {@code costly} them, their
	 * flaw), and they hold no spark, they earn one.
	 */
	public static void act(ServerPlayer player, Act act, boolean costly) {
		List<String> picks = player.getAttached(PICKS);
		if (picks == null || picks.size() != 3 || held(player)) {
			return;
		}
		boolean ideal = IDEAL_ACTS.getOrDefault(picks.get(0), Set.of()).contains(act);
		boolean bond = BOND_ACTS.getOrDefault(picks.get(1), Set.of()).contains(act);
		boolean flaw = costly && FLAW_ACTS.getOrDefault(picks.get(2), Set.of()).contains(act);
		if (!ideal && !bond && !flaw) {
			return;
		}
		ServerLevel level = player.level();
		long now = level.getGameTime();
		if (now < player.getAttachedOrElse(LAST, 0L) + GAP_TICKS) {
			return;
		}
		player.setAttached(LAST, now);
		player.setAttached(HELD, true);
		glow(player, 1.4F);
	}

	/** If a spark is held, spends it and says so with light and sound: the failure just met is a success instead. */
	public static boolean rescue(ServerPlayer player) {
		if (!held(player)) {
			return false;
		}
		player.removeAttached(HELD);
		glow(player, 0.7F);
		return true;
	}

	private static void glow(ServerPlayer player, float pitch) {
		ServerLevel level = player.level();
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.8F, pitch);
		Fx.burst(level, player.getX(), player.getY(1.2), player.getZ(), COLOR, 0.5F, 8, 0.3, 0.4, 0.3);
		Fx.ring(level, new Vec3(player.getX(), player.getY(0.5), player.getZ()), 0.8F, COLOR);
	}

	/** Gives the spark to a friend near, who holds none. */
	private static void give(ServerPlayer from, UUID friend) {
		if (!held(from) || from.level().getServer() == null) {
			return;
		}
		ServerPlayer to = from.level().getServer().getPlayerList().getPlayer(friend);
		if (to == null || to == from || to.level() != from.level() || to.distanceTo(from) > GIVE_RANGE || held(to) || !to.isAlive()) {
			return;
		}
		from.removeAttached(HELD);
		to.setAttached(HELD, true);
		glow(to, 1.6F);
		from.level().playSound(null, from.getX(), from.getY(), from.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.8F, 1.2F);
		to.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.spark.given", from.getDisplayName()));
	}
}
