package com.jaymoskingdoms;

import com.jaymoskingdoms.money.Money;
import com.mojang.serialization.Codec;
import java.util.List;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * A lord draws on the treasury from the Faction tab of the character menu ({@link MenuNet.Withdraw}): no more than a
 * tenth of it in one in-game day, however many times he asks. The server works the cap out; the screen only asks.
 */
final class Withdrawals {
	/** The share of the treasury (one in this many) a lord may draw in a day. */
	static final int DAY_SHARE = 10;
	/** The length of a day (ticks). */
	static final long DAY_TICKS = 24000L;

	/** Per player: the day number and what he has drawn that day (silver). Saved. */
	static final AttachmentType<List<Long>> DRAWN = AttachmentRegistry.create(JaymosKingdoms.id("treasury_drawn"),
		builder -> builder.persistent(Codec.LONG.listOf()));

	private Withdrawals() {
	}

	/** Loads the class, so {@link #DRAWN} is registered before any world loads. */
	static void register() {
	}

	/** Whether {@code member} is a lord, by standing or by a rank set higher (a crowned lord). */
	static boolean lord(Faction.Member member) {
		return Rank.forStanding(member.standing()) == Rank.LORD || member.rank() >= Rank.LORD.ordinal() && member.rank() < Rank.values().length;
	}

	private static long today(MinecraftServer server) {
		return server.overworld().getGameTime() / DAY_TICKS;
	}

	private static long taken(ServerPlayer player, long day) {
		List<Long> drawn = player.getAttached(DRAWN);
		return drawn != null && drawn.size() == 2 && drawn.get(0) == day ? drawn.get(1) : 0L;
	}

	/** What {@code player} may still draw from {@code f} today: a tenth of what it held before today's draws (at least 1 while it holds any). */
	static long allowance(ServerPlayer player, Faction f) {
		long taken = taken(player, today(player.level().getServer()));
		long cap = (f.treasury() + taken) / DAY_SHARE;
		if (f.treasury() > 0L) {
			cap = Math.max(1L, cap);
		}
		return Math.max(0L, Math.min(f.treasury(), cap - taken));
	}

	/** {@code player} asks to draw {@code silver} (nothing or less: all of today's allowance). */
	static void draw(ServerPlayer player, int silver) {
		MinecraftServer server = player.level().getServer();
		Faction k = Factions.of(server, player.getUUID());
		Faction.Member member = k == null ? null : k.member(player.getUUID());
		if (member == null || !lord(member)) {
			player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.lords_only"), true);
			return;
		}
		long allowed = allowance(player, k);
		long want = silver <= 0 ? allowed : Math.min(silver, allowed);
		if (want <= 0L) {
			player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.withdraw_cap"), true);
			return;
		}
		long day = today(server);
		long taken = taken(player, day);
		Factions.update(server, k.id(), f -> f.withTreasury(f.treasury() - want));
		player.setAttached(DRAWN, List.of(day, taken + want));
		// (paid out in silver: into the worn purse if it has room, else the pack, else the ground)
		Money.pay(player, Money.worth(want));
		player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.withdrew", Money.text(Money.worth(want)), k.name()), true);
	}
}
