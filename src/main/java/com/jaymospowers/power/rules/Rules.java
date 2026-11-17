package com.jaymospowers.power.rules;

import com.jaymospowers.JaymosPowers;
import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import java.util.List;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.player.Player;

/**
 * A Lawgiver's written rules, shared by both sides (the rulebook screen reads and prices them as the server does). A rule is one
 * sentence of four parts: <b>who</b> it is about, <b>what</b> is true of them, <b>how strong</b> (1-5) and <b>how long</b>.
 * Packed in an int: {@code who | what << 4 | strength << 8 | length << 12}.
 */
public final class Rules {
	/** At most this many written. */
	public static final int MOST = 5;
	public static final int SELF = 0, ALLIES = 1, FOES = 2, TARGET = 3, EVERYONE = 4, WHO_COUNT = 5;
	public static final int MEND = 0, WITHER = 1, SWIFT = 2, SLOW = 3, STRONG = 4, WEAK = 5, HARD = 6, FLOAT = 7, BURN = 8, MUTE = 9, GLOW = 10,
		BLIND = 11, WHAT_COUNT = 12;
	/** How long each length is (seconds). */
	public static final int[] SECONDS = {5, 15, 30, 60};
	/** What each "what" costs in ink for 5 s at strength 1. */
	static final float[] WHAT_COST = {3.0F, 4.0F, 2.0F, 3.0F, 3.0F, 3.0F, 4.0F, 3.0F, 3.0F, 8.0F, 1.0F, 3.0F};
	/** How much more it costs for whom: yourself, your allies near, your foes near, the one you look at, everyone near. */
	static final float[] WHO_COST = {1.0F, 1.5F, 2.0F, 1.0F, 2.5F};
	/** How the length weighs on the cost (seconds / 5, to this power). */
	static final double LENGTH_WEIGHT = 0.6;

	public static final String[] WHO_NAMES = {"self", "allies", "foes", "target", "everyone"};
	public static final String[] WHAT_NAMES = {"mend", "wither", "swift", "slow", "strong", "weak", "hard", "float", "burn", "mute", "glow", "blind"};

	/** The rules written, in order (saved, kept through death; the owner's client sees them). */
	public static final AttachmentType<List<Integer>> WRITTEN = AttachmentRegistry.create(JaymosPowers.id("rules_written"),
		builder -> builder.persistent(Codec.INT.listOf()).copyOnDeath()
			.syncWith(ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(MOST)), AttachmentSyncPredicate.targetOnly()));

	/** Server → client: open the rulebook. */
	public record OpenBook() implements CustomPacketPayload {
		public static final Type<OpenBook> TYPE = new Type<>(JaymosPowers.id("rules_open"));
		public static final StreamCodec<RegistryFriendlyByteBuf, OpenBook> CODEC = StreamCodec.unit(new OpenBook());

		@Override
		public Type<OpenBook> type() {
			return TYPE;
		}
	}

	/** Client → server: the rulebook as written now. */
	public record Write(List<Integer> rules) implements CustomPacketPayload {
		public static final Type<Write> TYPE = new Type<>(JaymosPowers.id("rules_write"));
		public static final StreamCodec<ByteBuf, Write> RAW = ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(MOST)).map(Write::new, Write::rules);
		public static final StreamCodec<RegistryFriendlyByteBuf, Write> CODEC = RAW.cast();

		@Override
		public Type<Write> type() {
			return TYPE;
		}
	}

	private Rules() {
	}

	static void registerPayloads() {
		PayloadTypeRegistry.clientboundPlay().register(OpenBook.TYPE, OpenBook.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(Write.TYPE, Write.CODEC);
	}

	public static int pack(int who, int what, int strength, int length) {
		return clamp(who, WHO_COUNT) | clamp(what, WHAT_COUNT) << 4 | Math.max(1, Math.min(5, strength)) << 8 | clamp(length, SECONDS.length) << 12;
	}

	private static int clamp(int v, int count) {
		return Math.max(0, Math.min(count - 1, v));
	}

	public static int who(int rule) {
		return clamp(rule & 0xF, WHO_COUNT);
	}

	public static int what(int rule) {
		return clamp(rule >> 4 & 0xF, WHAT_COUNT);
	}

	public static int strength(int rule) {
		return Math.max(1, Math.min(5, rule >> 8 & 0xF));
	}

	public static int length(int rule) {
		return clamp(rule >> 12 & 0xF, SECONDS.length);
	}

	/** What a rule costs to enact (ink). */
	public static float cost(int rule) {
		return (float)(WHAT_COST[what(rule)] * strength(rule) * Math.pow(SECONDS[length(rule)] / 5.0, LENGTH_WEIGHT) * WHO_COST[who(rule)]);
	}

	/** The strongest a rule may be for {@code player} now: 1, and one more with each ability learned. Both sides. */
	public static int strongest(Player player) {
		return 1 + com.jaymospowers.core.Mastery.unlocked(player);
	}

	/** How many rules may run at once: one, and three at mastery 9 (every ability and the last rank). Both sides. */
	public static int atOnce(Player player) {
		return com.jaymospowers.core.Mastery.unlocked(player) + com.jaymospowers.core.Mastery.rank(player) >= 9 ? 3 : 1;
	}

	/** The rulebook a fresh Lawgiver starts with: "I mend, strength 1, for 15 seconds." */
	public static List<Integer> first() {
		return List.of(pack(SELF, MEND, 1, 1));
	}
}
