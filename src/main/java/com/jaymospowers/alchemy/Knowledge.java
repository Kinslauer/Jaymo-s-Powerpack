package com.jaymospowers.alchemy;

import com.jaymospowers.JaymosPowers;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * What a player knows of alchemy: the essences they've discovered (a compound only once both parents are), the insight
 * they hold in each (earned scanning new things with the Scrying Lens, spent on {@link Research}), and the research
 * they've unlocked. Kept through death, synced to its own player. Creative players know everything.
 */
public final class Knowledge {
	public record State(Set<Essence> seen, EssenceList insight, Set<String> research) {
		public static final State NONE = new State(Set.of(), EssenceList.EMPTY, Set.of());
		public static final Codec<State> CODEC = RecordCodecBuilder.create(i -> i.group(
			Essence.CODEC.listOf().xmap(Set::copyOf, List::copyOf).fieldOf("seen").forGetter(State::seen),
			EssenceList.CODEC.fieldOf("insight").forGetter(State::insight),
			Codec.STRING.listOf().xmap(Set::copyOf, List::copyOf).fieldOf("research").forGetter(State::research)).apply(i, State::new));
		public static final StreamCodec<io.netty.buffer.ByteBuf, State> STREAM_CODEC = StreamCodec.composite(
			Essence.STREAM_CODEC.apply(ByteBufCodecs.list()).map(Set::copyOf, List::copyOf), State::seen,
			EssenceList.STREAM_CODEC, State::insight,
			ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()).map(Set::copyOf, List::copyOf), State::research,
			State::new);
	}

	public static final AttachmentType<State> KNOWLEDGE = AttachmentRegistry.create(JaymosPowers.id("alchemy_knowledge"),
		builder -> builder.persistent(State.CODEC).copyOnDeath().initializer(() -> State.NONE)
			.syncWith(State.STREAM_CODEC, AttachmentSyncPredicate.targetOnly()));

	private Knowledge() {
	}

	public static State of(Player player) {
		State s = player.getAttached(KNOWLEDGE);
		return s == null ? State.NONE : s;
	}

	public static boolean knows(Player player, String research) {
		return player.isCreative() || of(player).research().contains(research);
	}

	public static boolean hasSeen(Player player, Essence essence) {
		return player.isCreative() || of(player).seen().contains(essence);
	}

	/** Marks an essence discovered; a compound needs both parents seen first. Returns whether it was new. */
	public static boolean discover(ServerPlayer player, Essence essence) {
		State s = of(player);
		if (s.seen().contains(essence) || !essence.primal() && (!s.seen().contains(essence.a) || !s.seen().contains(essence.b))) {
			return false;
		}
		EnumSet<Essence> seen = EnumSet.of(essence);
		seen.addAll(s.seen());
		player.setAttached(KNOWLEDGE, new State(Set.copyOf(seen), s.insight(), s.research()));
		return true;
	}

	public static void addInsight(ServerPlayer player, EssenceList gained) {
		State s = of(player);
		player.setAttached(KNOWLEDGE, new State(s.seen(), s.insight().plus(gained), s.research()));
	}

	/** Unlocks a research entry if its parents are known and the insight is there (paid). Creative pays nothing. */
	public static boolean unlock(ServerPlayer player, Research.Entry entry) {
		State s = of(player);
		if (s.research().contains(entry.id()) || !s.research().containsAll(entry.parents())) {
			return false;
		}
		EssenceList left = player.isCreative() ? s.insight() : s.insight().minus(entry.cost());
		if (left == null || Corruption.total(player) < entry.corruption() && !player.isCreative()) {
			return false;
		}
		Set<String> research = new HashSet<>(s.research());
		research.add(entry.id());
		player.setAttached(KNOWLEDGE, new State(s.seen(), left, Set.copyOf(research)));
		return true;
	}
}
