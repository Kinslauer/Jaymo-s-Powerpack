package com.jaymoskingdoms;

import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/**
 * Renown, the reward for going on past the top of a pillar. A pillar's experience keeps counting after its highest level
 * (Skills.Progress xp, the same value as before); every {@link #XP_PER_POINT} of it past that level is one renown point,
 * at most {@link #MOST} earned in all, across the body's pillars (the Scholar's study earns none). A point is spent on the
 * skill screen, on either one rank of a perk (that rank costs its pillar no points) or {@link #STAT_POINTS} attribute points
 * (they join the pool and stay under the tier's cap, {@link Stats#cap}). What is earned is worked out from the experience, so
 * nothing but what is spent is saved: in the skills' ranks, under {@link #STAT_KEY} and a perk's {@link #rankKey}, which a
 * respec clears (the points come back with the rest).
 */
public final class SkillRenown {
	/** Experience past a pillar's top level for one renown point, and the most points earned in all. */
	public static final int XP_PER_POINT = 3000, MOST = 30;
	/** Attribute points one renown point buys. */
	public static final int STAT_POINTS = 5;
	/** The ranks key counting renown points spent on attribute points. */
	static final String STAT_KEY = "_renown_stat";

	/** Player to server: spend one renown point on a rank of the perk with this ordinal, or on attribute points (-1). */
	public record Spend(int perk) implements CustomPacketPayload {
		public static final Type<Spend> TYPE = new Type<>(JaymosKingdoms.id("renown_spend"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Spend> CODEC = CustomPacketPayload.codec(
			(p, buf) -> buf.writeVarInt(p.perk + 1), buf -> new Spend(buf.readVarInt() - 1));

		@Override
		public Type<Spend> type() {
			return TYPE;
		}
	}

	private SkillRenown() {
	}

	static void register() {
		PayloadTypeRegistry.serverboundPlay().register(Spend.TYPE, Spend.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Spend.TYPE, (payload, context) -> spend(context.player(), payload.perk()));
	}

	/** The ranks key counting the ranks of {@code perk} bought with renown (they cost the pillar no points). */
	public static String rankKey(Skills.Perk perk) {
		return perk.key() + "@renown";
	}

	/** The most experience a pillar keeps: to its top level, and for the body's pillars on to the last renown point. */
	public static int xpCap(Skills.Pillar pillar) {
		return pillar.web() ? Skills.total(pillar.maxLevel) + MOST * XP_PER_POINT : Skills.total(pillar.maxLevel);
	}

	/** Renown points earned in all (at most {@link #MOST}). */
	public static int earned(Skills.Progress p) {
		long n = 0;
		for (Skills.Pillar pillar : Skills.Pillar.values()) {
			if (pillar.web()) {
				n += Math.max(0, p.xp(pillar) - Skills.total(pillar.maxLevel)) / XP_PER_POINT;
			}
		}
		return (int)Math.min(MOST, n);
	}

	/** Renown points already spent, on perk ranks and on attribute points. */
	public static int spent(Skills.Progress p) {
		int n = p.ranks().getOrDefault(STAT_KEY, 0);
		for (Skills.Perk perk : Skills.Perk.values()) {
			n += Math.min(p.rank(perk), p.ranks().getOrDefault(rankKey(perk), 0));
		}
		return n;
	}

	/** Renown points to spend now. */
	public static int available(Skills.Progress p) {
		return Math.max(0, earned(p) - spent(p));
	}

	/** Attribute points the renown spent on them gave. */
	public static int statPoints(Skills.Progress p) {
		return p.ranks().getOrDefault(STAT_KEY, 0) * STAT_POINTS;
	}

	/** The ranks of {@code perk} paid for with renown. */
	static int ranksOf(Skills.Progress p, Skills.Perk perk) {
		return Math.min(p.rank(perk), p.ranks().getOrDefault(rankKey(perk), 0));
	}

	private static void spend(ServerPlayer player, int ordinal) {
		Skills.Progress p = Skills.of(player);
		if (available(p) <= 0) {
			return;
		}
		Map<String, Integer> ranks = new HashMap<>(p.ranks());
		if (ordinal < 0) {
			ranks.merge(STAT_KEY, 1, Integer::sum);
		} else {
			Skills.Perk[] perks = Skills.Perk.values();
			if (ordinal >= perks.length) {
				return;
			}
			Skills.Perk perk = perks[ordinal];
			if (perk.bridge() || p.blocked(perk, true) != null || Knowledge.lockedPerk(player, perk) || Learnables.classLocked(player, perk)
				|| TreeLocks.hidden(player, perk)) {
				return;
			}
			ranks.merge(perk.key(), 1, Integer::sum);
			ranks.merge(rankKey(perk), 1, Integer::sum);
			player.setAttached(Skills.PROGRESS, new Skills.Progress(p.xp(), Map.copyOf(ranks)));
			Learnables.spent(player, perk);
			Skills.refresh(player);
			player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 0.5F, 1.4F);
			return;
		}
		player.setAttached(Skills.PROGRESS, new Skills.Progress(p.xp(), Map.copyOf(ranks)));
		Skills.refresh(player);
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 0.5F, 1.4F);
	}
}
