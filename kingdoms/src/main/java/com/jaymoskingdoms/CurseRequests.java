package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.animal.wolf.WolfSoundVariants;
import net.minecraft.world.entity.npc.villager.Villager;

/**
 * (0.7.332) What the curse asks for. Each night a werewolf player (the full curse) is asked, with a line in the overlay, for
 * one thing before dawn: to run under the open sky ({@link #RUN_SECONDS} sprinting), to hunt something as the beast, to
 * stay out of towns, or to let the beast out. Doing it lowers the wildness ({@link FeralCurse}) by {@link #KEPT} at once;
 * breaking the ban, or ignoring the request till dawn, raises it by {@link #IGNORED}. The ask is kept with the player
 * ({@link #ASK}), so a restart doesn't lose it; one left unanswered over a whole later night lapses unjudged.
 */
public final class CurseRequests {
	// ---- tuning --------------------------------------------------------------------------------------------------------

	/** The chance the curse asks anything on a given night. */
	static final float ASK_CHANCE = 0.85F;
	/** Wildness lost for doing what it asked, gained for ignoring it or breaking its ban. */
	static final float KEPT = 2.5F, IGNORED = 3.0F;
	/** Seconds of running under the open sky it takes; the ask is repeated this often (seconds) while it stands. */
	static final int RUN_SECONDS = 60, REMIND_SECONDS = 180;
	/** Towns: a realm's seat or village within this (blocks), or this many grown villagers within {@link #TOWN_FOLK_RANGE}. */
	static final double TOWN_RANGE = 72.0, TOWN_FOLK_RANGE = 24.0;
	static final int TOWN_FOLK = 3;
	/** Stages of the wildness from which the beast comes anyway every night, so it isn't asked to let it out. */
	static final int ALWAYS_OUT_FROM = 4;

	/** The kinds of ask. */
	static final int RUN = 0, HUNT = 1, STAY_OUT = 2, LET_OUT = 3, KINDS = 4;
	/** An ask's state. */
	static final int OPEN = 0, DONE = 1, BROKEN = 2, SKIPPED = 3;

	/** Tonight's ask: its kind, the night it was made for, how far along it is (seconds), and its state. */
	record Ask(int kind, long night, int progress, int state) {
		static final Codec<Ask> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.INT.fieldOf("kind").forGetter(Ask::kind),
			Codec.LONG.fieldOf("night").forGetter(Ask::night),
			Codec.INT.optionalFieldOf("progress", 0).forGetter(Ask::progress),
			Codec.INT.optionalFieldOf("state", OPEN).forGetter(Ask::state)).apply(i, Ask::new));

		Ask with(int progress, int state) {
			return new Ask(this.kind, this.night, progress, state);
		}
	}

	/** On a werewolf player: the night's ask. Saved, kept through death, not synced. */
	static final AttachmentType<Ask> ASK = AttachmentRegistry.create(JaymosKingdoms.id("curse_ask"),
		builder -> builder.persistent(Ask.CODEC).copyOnDeath());

	private CurseRequests() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(CurseRequests::tick);
	}

	// ---- hooks ---------------------------------------------------------------------------------------------------------

	/** The beast of {@code player} killed something (it hunted with its own teeth). */
	static void killed(ServerPlayer player) {
		Ask ask = player.getAttached(ASK);
		if (ask != null && ask.state() == OPEN && ask.kind() == HUNT) {
			kept(player, ask);
		}
	}

	/** The beast tore out of {@code player} (the moon's, the curse's own, or at will). */
	static void changed(ServerPlayer player) {
		Ask ask = player.getAttached(ASK);
		if (ask != null && ask.state() == OPEN && ask.kind() == LET_OUT) {
			kept(player, ask);
		}
	}

	// ---- every second --------------------------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		ServerLevel overworld = server.overworld();
		long now = overworld.getGameTime();
		long clock = overworld.getOverworldClockTime();
		boolean night = Vampires.night(clock);
		long nightNo = Vampires.nightIndex(clock);
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if ((now + player.getId()) % 20 != 17) {
				continue;
			}
			Ask ask = player.getAttached(ASK);
			if (!FeralCurse.stillCursed(player)) {
				if (ask != null) {
					// (cured: nothing left to ask)
					player.removeAttached(ASK);
				}
				continue;
			}
			if (!player.isAlive() || player.isSpectator() || player.isCreative()) {
				continue;
			}
			if (ask != null && (ask.night() != nightNo || !night)) {
				// dawn (or a later night): the ask is judged, then forgotten
				judge(player, ask, ask.night() == nightNo);
				player.removeAttached(ASK);
				ask = null;
			}
			if (ask == null) {
				if (night) {
					ask(player, overworld, clock, nightNo);
				}
				continue;
			}
			if (ask.state() != OPEN) {
				continue;
			}
			if ((now / 20 + player.getId()) % REMIND_SECONDS == 0) {
				say(player, ask.kind());
			}
			switch (ask.kind()) {
				case RUN -> {
					if (player.isSprinting() && !player.isPassenger() && player.level().canSeeSky(player.blockPosition())) {
						Ask next = ask.with(ask.progress() + 1, OPEN);
						player.setAttached(ASK, next);
						if (next.progress() >= RUN_SECONDS) {
							kept(player, next);
						}
					}
				}
				case STAY_OUT -> {
					if (inTown(server, player)) {
						player.setAttached(ASK, ask.with(ask.progress(), BROKEN));
						broke(player);
					}
				}
				default -> {
				}
			}
		}
	}

	/** Tonight's ask, rolled (or none). */
	private static void ask(ServerPlayer player, ServerLevel overworld, long clock, long nightNo) {
		if (player.getRandom().nextFloat() >= ASK_CHANCE) {
			player.setAttached(ASK, new Ask(0, nightNo, 0, SKIPPED));
			return;
		}
		boolean moon = Lycans.fullMoon(overworld, clock);
		// (the beast comes out anyway on a full moon, and every night from Feral: no use asking)
		int kinds = moon || FeralCurse.stage(player) >= ALWAYS_OUT_FROM ? LET_OUT : KINDS;
		int kind = player.getRandom().nextInt(kinds);
		player.setAttached(ASK, new Ask(kind, nightNo, 0, OPEN));
		say(player, kind);
		growl(player);
	}

	private static void say(ServerPlayer player, int kind) {
		player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.feral.request." + kind).withStyle(ChatFormatting.GOLD, ChatFormatting.ITALIC));
	}

	/** It did what the curse asked. */
	private static void kept(ServerPlayer player, Ask ask) {
		player.setAttached(ASK, ask.with(ask.progress(), DONE));
		FeralCurse.drift(player, -KEPT);
		player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.feral.request.kept").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
	}

	/** It went where the curse said not to. */
	private static void broke(ServerPlayer player) {
		FeralCurse.drift(player, IGNORED);
		player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.feral.request.broken").withStyle(ChatFormatting.GOLD, ChatFormatting.ITALIC));
		growl(player);
	}

	/** Dawn: an ask still open is answered or ignored. {@code seen}: this is the morning of its own night (else it lapsed unseen). */
	private static void judge(ServerPlayer player, Ask ask, boolean seen) {
		if (ask.state() != OPEN || !seen) {
			return;
		}
		if (ask.kind() == STAY_OUT) {
			kept(player, ask);
			return;
		}
		FeralCurse.drift(player, IGNORED);
		player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.feral.request.ignored").withStyle(ChatFormatting.GOLD, ChatFormatting.ITALIC));
	}

	private static void growl(ServerPlayer player) {
		player.level().playSound(null, player.blockPosition(), SoundEvents.WOLF_SOUNDS.get(WolfSoundVariants.SoundSet.BIG).adultSounds().growlSound().value(),
			SoundSource.PLAYERS, 0.6F, 0.6F);
	}

	/** In a town: near a realm's seat or village, or among its people. */
	private static boolean inTown(MinecraftServer server, ServerPlayer player) {
		if (player.level() == Factions.level(server) && Factions.near(server, player.blockPosition(), TOWN_RANGE) != null) {
			return true;
		}
		return player.level().getEntitiesOfClass(Villager.class, player.getBoundingBox().inflate(TOWN_FOLK_RANGE), v -> v.isAlive() && !v.isBaby()).size() >= TOWN_FOLK;
	}
}
