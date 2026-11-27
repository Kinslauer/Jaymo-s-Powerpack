package com.jaymospowers.alchemy.hunter;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.alchemy.Essence;
import com.jaymospowers.alchemy.EssenceList;
import com.jaymospowers.alchemy.essence.Essences;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * A poison's lean (Hunter's Road, patch 6, "T-A3"): every poison ingredient, paste and brew leans one of six ways, read off
 * the essences it is made of ({@link Lean#essences}). A lean is <i>learned</i> in one of two ways: by taking a lot of it
 * ({@link #LEARN_AT} load units of that lean, counted in {@link #taken}), or by the needle test ({@link NeedleItem}: a reagent
 * of that lean tried on a strong creature brought near death). The Scrying Lens shows a thing's lean only once its lean is
 * learned. Patch 11 (mutagens) reads {@link #leans}: how much of each lean the body has been taking lately (it drains away
 * slowly), and {@link #known}.
 */
public final class Leans {
	/** The six leans, and which of our essences each is made of (the biggest share in a thing's essences wins). */
	public enum Lean {
		HEAT(Essence.FIRE, Essence.SPARK), SEA(Essence.WATER), DECAY(Essence.ROT, Essence.DEATH, Essence.UNDEATH),
		COLD(Essence.FROST), FOREST(Essence.PLANT, Essence.LIFE), NETHER(Essence.SULPHUR, Essence.SHADOW);

		public final Essence[] essences;

		Lean(Essence... essences) {
			this.essences = essences;
		}

		public String id() {
			return name().toLowerCase(Locale.ROOT);
		}

		/** {@code lean.jaymospowers.<id>}. */
		public String key() {
			return "lean.jaymospowers." + id();
		}

		public static @Nullable Lean byId(String id) {
			for (Lean l : values()) {
				if (l.id().equals(id)) {
					return l;
				}
			}
			return null;
		}
	}

	// ---- tuning ----
	/** Load units of one lean a body must take to learn it by taking it. */
	public static final int LEARN_AT = 400;
	/** What the count of each lean drains by each minute (so it shows the lean taken lately). */
	public static final int DRAIN_PER_MINUTE = 12;
	/** The needle test: a creature this strong (max health) brought below this share of it. */
	public static final float STRONG_HEALTH = 30.0F, NEAR_DEATH = 0.25F;

	/** What a body has taken of each lean (by id) and which leans it has learned. Kept through death; synced to its owner. */
	public record State(Map<String, Integer> taken, Set<String> known) {
		public static final State NONE = new State(Map.of(), Set.of());
		public static final Codec<State> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.unboundedMap(Codec.STRING, Codec.INT).fieldOf("taken").forGetter(State::taken),
			Codec.STRING.listOf().xmap(Set::copyOf, java.util.List::copyOf).fieldOf("known").forGetter(State::known)).apply(i, State::new));
		public static final StreamCodec<ByteBuf, State> STREAM = StreamCodec.composite(
			ByteBufCodecs.map(HashMap::new, ByteBufCodecs.STRING_UTF8, ByteBufCodecs.VAR_INT).map(m -> (Map<String, Integer>) m, m -> new HashMap<>(m)), State::taken,
			ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()).map(Set::copyOf, java.util.List::copyOf), State::known, State::new);
	}

	public static final AttachmentType<State> LEANS = AttachmentRegistry.create(JaymosPowers.id("poison_leans"),
		builder -> builder.persistent(State.CODEC).copyOnDeath().initializer(() -> State.NONE)
			.syncWith(State.STREAM, AttachmentSyncPredicate.targetOnly()));

	private Leans() {
	}

	static void init() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % 1200 != 0) {
				return;
			}
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				State s = of(player);
				if (s.taken().isEmpty()) {
					continue;
				}
				Map<String, Integer> next = new HashMap<>();
				s.taken().forEach((k, v) -> {
					if (v - DRAIN_PER_MINUTE > 0) {
						next.put(k, v - DRAIN_PER_MINUTE);
					}
				});
				player.setAttached(LEANS, new State(Map.copyOf(next), s.known()));
			}
		});
	}

	public static State of(Player player) {
		State s = player.getAttached(LEANS);
		return s == null ? State.NONE : s;
	}

	// ---- reading a thing's lean ---------------------------------------------------------------------------------------

	/** The lean of a mix of essences: the one whose essences make up the most of it, or null if none of them lean. */
	public static @Nullable Lean of(EssenceList essences) {
		Map<Lean, Integer> sums = new EnumMap<>(Lean.class);
		for (Map.Entry<Essence, Integer> en : essences.amounts().entrySet()) {
			for (Lean lean : Lean.values()) {
				for (Essence e : lean.essences) {
					if (e == en.getKey()) {
						sums.merge(lean, en.getValue(), Integer::sum);
					}
				}
			}
		}
		Lean best = null;
		int most = 0;
		for (Map.Entry<Lean, Integer> en : sums.entrySet()) {
			if (en.getValue() > most) {
				most = en.getValue();
				best = en.getKey();
			}
		}
		return best;
	}

	/** The lean of an item (from its essences), or null. */
	public static @Nullable Lean of(ItemStack stack) {
		return stack.isEmpty() ? null : of(Essences.of(stack));
	}

	// ---- what a body has learned and taken ---------------------------------------------------------------------------

	/** Whether {@code player} has learned {@code lean} (both sides; creative players know them all). */
	public static boolean knows(Player player, Lean lean) {
		return player.isCreative() || of(player).known().contains(lean.id());
	}

	public static Set<Lean> known(Player player) {
		Set<Lean> out = new HashSet<>();
		for (Lean l : Lean.values()) {
			if (knows(player, l)) {
				out.add(l);
			}
		}
		return out;
	}

	/** What {@code player} has been taking lately, lean by lean (for patch 11: the way a mutation leans). Empty entries left out. */
	public static Map<Lean, Integer> leans(Player player) {
		Map<Lean, Integer> out = new EnumMap<>(Lean.class);
		of(player).taken().forEach((k, v) -> {
			Lean lean = Lean.byId(k);
			if (lean != null && v > 0) {
				out.put(lean, v);
			}
		});
		return out;
	}

	/** {@code player} took {@code amount} load units of whatever {@code stack} was: that much of its lean, and learned if enough. */
	public static void taken(ServerPlayer player, ItemStack stack, int amount) {
		if (amount <= 0) {
			return;
		}
		Lean lean = of(stack);
		if (lean == null) {
			return;
		}
		State s = of(player);
		Map<String, Integer> taken = new HashMap<>(s.taken());
		int now = taken.merge(lean.id(), amount, Integer::sum);
		Set<String> known = s.known();
		if (now >= LEARN_AT && !known.contains(lean.id())) {
			known = new HashSet<>(known);
			known.add(lean.id());
			player.level().playSound(null, player.getX(), player.getY(), player.getZ(), net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_CHIME,
				net.minecraft.sounds.SoundSource.PLAYERS, 0.5F, 0.7F);
			player.sendOverlayMessage(net.minecraft.network.chat.Component.translatable("message.jaymospowers.lean.learned", net.minecraft.network.chat.Component.translatable(lean.key())));
		}
		player.setAttached(LEANS, new State(Map.copyOf(taken), Set.copyOf(known)));
	}

	/** Learns {@code lean} outright (the needle test). Returns whether it was new. */
	public static boolean learn(ServerPlayer player, Lean lean) {
		State s = of(player);
		if (s.known().contains(lean.id())) {
			return false;
		}
		Set<String> known = new HashSet<>(s.known());
		known.add(lean.id());
		player.setAttached(LEANS, new State(s.taken(), Set.copyOf(known)));
		player.sendOverlayMessage(net.minecraft.network.chat.Component.translatable("message.jaymospowers.lean.learned", net.minecraft.network.chat.Component.translatable(lean.key())));
		return true;
	}
}
