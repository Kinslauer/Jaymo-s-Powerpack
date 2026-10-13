package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/**
 * (0.5.50) The skill tree: three pillars, Survival, Stealth and Strength. Each pillar has its own experience, earned by
 * doing what it's about, and its own levels; every level is a point to spend on that pillar's perks (the {@code K} key
 * opens the tree). Practice counts on its own too: each level of a pillar makes you a little better at its work before
 * any point is spent.
 *
 * <p>Stealth is the first pillar built. It grows with every pocket picked clean ({@link Pickpocket}) and every theft
 * nobody saw ({@link Theft}); each level makes marks less likely to feel your hand, and its perks make you lighter
 * fingered, sneakier and harder to pin. Survival and Strength are laid out in {@code docs/Skills-Plan.md} and come later.
 */
public final class Skills {
	/** A level's experience: from level {@code l} to the next takes {@code XP_BASE + XP_STEP * l}; the highest level. */
	public static final int XP_BASE = 30, XP_STEP = 15, MAX_LEVEL = 15;
	/** Stealth experience: a pocket picked clean with something in it, an empty one, a theft nobody saw. */
	static final int XP_LIFT = 10, XP_EMPTY = 2, XP_THEFT = 4;
	/** A guard's pocket counts this many times over; a sleeper's this share. */
	static final float XP_GUARD = 2.0F, XP_ASLEEP = 0.5F;
	/** Unseen thefts give experience at most this often (ticks), so a chest can't be farmed one item at a time. */
	static final long THEFT_XP_TICKS = 600L;
	/** Practice: each Stealth level takes this share off the chance a mark feels your hand. */
	static final float PRACTICE_PER_LEVEL = 0.03F;
	/** Light Fingers: this share off the chance per rank. However good you get, a mark feels you at least this often. */
	static final float LIGHT_FINGERS_PER_RANK = 0.08F, CATCH_FLOOR = 0.02F;
	/** Deep Pockets: per rank, this much more of what a mark carries, and one more try at a pocket that turns up empty. */
	static final float DEEP_POCKETS_SHARE = 0.15F;
	/** Soft Step: per rank, sneaking witnesses see you this much less far (a share of their full sight). */
	static final double SOFT_STEP_PER_RANK = 0.1;
	/** Quick Hands: you can pick a pocket from the side (the cosine between their gaze and you, at most). */
	static final double QUICK_HANDS_COS = 0.35;
	/** Slip Away: the chance a mark who feels your hand can't tell it was you. */
	static final float SLIP_AWAY_CHANCE = 0.5F;

	/** The three pillars of the tree. Only the open ones have perks yet. */
	public enum Pillar {
		SURVIVAL(false), STEALTH(true), STRENGTH(false);

		public final boolean open;

		Pillar(boolean open) {
			this.open = open;
		}

		public String key() {
			return name().toLowerCase(Locale.ROOT);
		}
	}

	/**
	 * A perk: its pillar, how many ranks it has, what a rank costs, the pillar level it needs, where it sits in the tree
	 * (tier from the top, column of three) and the perks it needs a rank in first.
	 */
	public enum Perk {
		LIGHT_FINGERS(Pillar.STEALTH, 5, 1, 0, 0, 1),
		DEEP_POCKETS(Pillar.STEALTH, 3, 1, 0, 1, 0, LIGHT_FINGERS),
		SOFT_STEP(Pillar.STEALTH, 3, 1, 0, 1, 2, LIGHT_FINGERS),
		QUICK_HANDS(Pillar.STEALTH, 1, 1, 5, 2, 0, DEEP_POCKETS),
		SLIP_AWAY(Pillar.STEALTH, 1, 1, 5, 2, 2, SOFT_STEP),
		GHOST(Pillar.STEALTH, 1, 2, 10, 3, 1, QUICK_HANDS, SLIP_AWAY);

		public final Pillar pillar;
		public final int maxRank, cost, minLevel, tier, column;
		public final List<Perk> requires;

		Perk(Pillar pillar, int maxRank, int cost, int minLevel, int tier, int column, Perk... requires) {
			this.pillar = pillar;
			this.maxRank = maxRank;
			this.cost = cost;
			this.minLevel = minLevel;
			this.tier = tier;
			this.column = column;
			this.requires = List.of(requires);
		}

		public String key() {
			return name().toLowerCase(Locale.ROOT);
		}
	}

	/** A player's experience in each pillar and ranks in each perk (by their keys). */
	public record Progress(Map<String, Integer> xp, Map<String, Integer> ranks) {
		public static final Progress EMPTY = new Progress(Map.of(), Map.of());
		static final Codec<Progress> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("xp", Map.of()).forGetter(Progress::xp),
			Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("ranks", Map.of()).forGetter(Progress::ranks)
		).apply(i, Progress::new));
		static final StreamCodec<FriendlyByteBuf, Progress> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.map(HashMap::new, ByteBufCodecs.STRING_UTF8, ByteBufCodecs.VAR_INT), p -> new HashMap<>(p.xp()),
			ByteBufCodecs.map(HashMap::new, ByteBufCodecs.STRING_UTF8, ByteBufCodecs.VAR_INT), p -> new HashMap<>(p.ranks()),
			(xp, ranks) -> new Progress(Map.copyOf(xp), Map.copyOf(ranks)));

		public int xp(Pillar pillar) {
			return xp.getOrDefault(pillar.key(), 0);
		}

		public int rank(Perk perk) {
			return ranks.getOrDefault(perk.key(), 0);
		}

		public int level(Pillar pillar) {
			return Skills.level(xp(pillar));
		}

		/** Points left to spend in {@code pillar}: one per level, less what its perks cost. */
		public int points(Pillar pillar) {
			int spent = 0;
			for (Perk perk : Perk.values()) {
				if (perk.pillar == pillar) {
					spent += rank(perk) * perk.cost;
				}
			}
			return level(pillar) - spent;
		}

		/** Why {@code perk} can't take another rank now (a translation key), or null if it can. */
		public @Nullable String blocked(Perk perk) {
			if (!perk.pillar.open) {
				return "gui.jaymoskingdoms.skills.blocked.later";
			}
			if (rank(perk) >= perk.maxRank) {
				return "gui.jaymoskingdoms.skills.blocked.max";
			}
			if (level(perk.pillar) < perk.minLevel) {
				return "gui.jaymoskingdoms.skills.blocked.level";
			}
			for (Perk need : perk.requires) {
				if (rank(need) == 0) {
					return "gui.jaymoskingdoms.skills.blocked.requires";
				}
			}
			if (points(perk.pillar) < perk.cost) {
				return "gui.jaymoskingdoms.skills.blocked.points";
			}
			return null;
		}
	}

	/** Each player's progress through the tree. Saved, kept through death, synced to its own player (the tree's screen). */
	public static final AttachmentType<Progress> PROGRESS = AttachmentRegistry.create(JaymosKingdoms.id("skills"),
		builder -> builder.persistent(Progress.CODEC).copyOnDeath().syncWith(Progress.STREAM_CODEC, AttachmentSyncPredicate.targetOnly()));

	/** Player → server: spend points on a rank of {@code perk} (its ordinal). */
	public record Spend(int perk) implements CustomPacketPayload {
		public static final Type<Spend> TYPE = new Type<>(JaymosKingdoms.id("skill_spend"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Spend> CODEC = CustomPacketPayload.codec(
			(p, buf) -> buf.writeVarInt(p.perk), buf -> new Spend(buf.readVarInt()));

		@Override
		public Type<Spend> type() {
			return TYPE;
		}
	}

	/** Player → game time an unseen theft next gives experience. */
	private static final Map<UUID, Long> NEXT_THEFT_XP = new HashMap<>();

	private Skills() {
	}

	static void register() {
		PayloadTypeRegistry.serverboundPlay().register(Spend.TYPE, Spend.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Spend.TYPE, (payload, context) -> spend(context.player(), payload.perk()));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> NEXT_THEFT_XP.clear());
	}

	// ---- levels ------------------------------------------------------------------------------------------------

	/** Experience from level {@code level} to the next. */
	public static int toNext(int level) {
		return XP_BASE + XP_STEP * level;
	}

	/** Experience it takes to reach {@code level} from nothing. */
	public static int total(int level) {
		int sum = 0;
		for (int l = 0; l < level; l++) {
			sum += toNext(l);
		}
		return sum;
	}

	/** The level {@code xp} experience reaches. */
	public static int level(int xp) {
		int level = 0;
		while (level < MAX_LEVEL && xp >= total(level + 1)) {
			level++;
		}
		return level;
	}

	public static Progress of(Player player) {
		return player.getAttachedOrElse(PROGRESS, Progress.EMPTY);
	}

	static int rank(Player player, Perk perk) {
		return of(player).rank(perk);
	}

	/** Gives {@code player} {@code amount} experience in {@code pillar}; a level reached is announced. */
	static void grant(ServerPlayer player, Pillar pillar, int amount) {
		if (amount <= 0) {
			return;
		}
		Progress p = of(player);
		int before = p.xp(pillar);
		int after = Math.min(total(MAX_LEVEL), before + amount);
		if (after == before) {
			return;
		}
		Map<String, Integer> xp = new HashMap<>(p.xp());
		xp.put(pillar.key(), after);
		player.setAttached(PROGRESS, new Progress(Map.copyOf(xp), p.ranks()));
		int was = level(before), now = level(after);
		if (now > was) {
			player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.skills.level", Component.translatable("skill.jaymoskingdoms." + pillar.key()), now)
				.withStyle(ChatFormatting.GOLD));
			player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.4F, 1.3F);
		}
	}

	private static void spend(ServerPlayer player, int ordinal) {
		Perk[] perks = Perk.values();
		if (ordinal < 0 || ordinal >= perks.length) {
			return;
		}
		Perk perk = perks[ordinal];
		Progress p = of(player);
		if (p.blocked(perk) != null) {
			return;
		}
		Map<String, Integer> ranks = new HashMap<>(p.ranks());
		ranks.merge(perk.key(), 1, Integer::sum);
		player.setAttached(PROGRESS, new Progress(p.xp(), Map.copyOf(ranks)));
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 0.5F, 1.4F);
	}

	// ---- Stealth -----------------------------------------------------------------------------------------------

	/** A pocket picked clean: experience for it, the more for a guard's, the less for a sleeper's. Returns what it gave. */
	static int pickedClean(ServerPlayer player, boolean lifted, boolean guard, boolean asleep) {
		float xp = lifted ? XP_LIFT : XP_EMPTY;
		if (guard) {
			xp *= XP_GUARD;
		}
		if (asleep) {
			xp *= XP_ASLEEP;
		}
		int amount = Math.max(1, Math.round(xp));
		if (of(player).level(Pillar.STEALTH) >= MAX_LEVEL) {
			return 0;
		}
		grant(player, Pillar.STEALTH, amount);
		return amount;
	}

	/** A theft nobody saw (from a store, a chest, saddlebags): a little experience, at most every so often. */
	static void unseenTheft(ServerPlayer player, long now) {
		if (NEXT_THEFT_XP.getOrDefault(player.getUUID(), 0L) > now) {
			return;
		}
		NEXT_THEFT_XP.put(player.getUUID(), now + THEFT_XP_TICKS);
		grant(player, Pillar.STEALTH, XP_THEFT);
	}

	/** The chance a mark feels {@code player}'s hand, from {@code base}: less with practice and with Light Fingers. */
	static float catchChance(Player player, float base) {
		Progress p = of(player);
		float chance = base * (1.0F - PRACTICE_PER_LEVEL * p.level(Pillar.STEALTH)) * (1.0F - LIGHT_FINGERS_PER_RANK * p.rank(Perk.LIGHT_FINGERS));
		return Math.max(CATCH_FLOOR, chance);
	}

	/** Ghost: a guard is no harder to rob than anyone else, and a theft nobody saw is never missed. */
	static boolean ghost(Player player) {
		return rank(player, Perk.GHOST) > 0;
	}

	/** The share of a carried stack {@code player} takes (Deep Pockets). */
	static float carriedShare(Player player, float base) {
		return Math.min(1.0F, base + DEEP_POCKETS_SHARE * rank(player, Perk.DEEP_POCKETS));
	}

	/** How many tries {@code player} gets at a pocket that turns up empty (Deep Pockets). */
	static int pocketTries(Player player) {
		return 1 + rank(player, Perk.DEEP_POCKETS);
	}

	/** The share of their sight sneaking hides {@code player} by (Soft Step). */
	static double sneakFactor(Player player, double base) {
		return Math.max(0.1, base - SOFT_STEP_PER_RANK * rank(player, Perk.SOFT_STEP));
	}

	/** How far round the mark {@code player} may stand and still be behind them (Quick Hands: the side will do). */
	static double behindCos(Player player, double base) {
		return rank(player, Perk.QUICK_HANDS) > 0 ? Math.max(base, QUICK_HANDS_COS) : base;
	}

	/** Slip Away: the mark felt it, but can't tell who. */
	static boolean slipsAway(Player player, RandomSource random) {
		return rank(player, Perk.SLIP_AWAY) > 0 && random.nextFloat() < SLIP_AWAY_CHANCE;
	}
}
