package com.jaymospowers.power.chrono;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.LoreBook;
import com.jaymospowers.core.Powers;
import com.jaymospowers.power.chrono.future.Future;
import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Prediction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * The Chrono's road through the Retaking (shown in Jaymo's Kingdoms' Story tab, through the client's {@code StoryRoads}):
 * end your own echo; go forward to the future; hear an Omen pilot out (who hands over a few folded drawings); wake holding
 * the Stopped Clock. It says what to do, never what it means: the drawings are a great clock and the sweepers' parts with
 * the maker's name scraped off, and nothing anywhere says whose they are.
 */
public final class ChronoRoad {
	/** The steps, in order. */
	public static final List<String> STEPS = List.of("echo", "future", "omen", "clock");
	/** How often (ticks) the steps that are a state (echo ended, in the future, holding the clock) are looked at. */
	static final int LOOK_EVERY = 100;
	/** The drawings the pilot hands over: lang pages {@code book.jaymospowers.clock_drawings.page1..n}. */
	static final String DRAWINGS = "clock_drawings";
	static final int DRAWINGS_PAGES = 4;

	/** The steps done. Saved, kept through death, synced to its owner for the Story tab. */
	public static final AttachmentType<List<String>> DONE = AttachmentRegistry.create(JaymosPowers.id("chrono_road"),
		builder -> builder.persistent(Codec.STRING.listOf()).copyOnDeath()
			.syncWith(ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), AttachmentSyncPredicate.targetOnly()));

	private ChronoRoad() {
	}

	static void init() {
		ServerTickEvents.END_SERVER_TICK.register(ChronoRoad::tick);
	}

	public static boolean done(Player player, String step) {
		return player.getAttachedOrElse(DONE, List.of()).contains(step);
	}

	/** Marks {@code step} done for a Chrono; true if it wasn't before. */
	static boolean mark(ServerPlayer player, String step) {
		if (!Powers.has(player, ChronoPower.INSTANCE) || done(player, step)) {
			return false;
		}
		List<String> more = new ArrayList<>(player.getAttachedOrElse(DONE, List.of()));
		more.add(step);
		player.setAttached(DONE, List.copyOf(more));
		return true;
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % LOOK_EVERY != 0) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (!Powers.has(player, ChronoPower.INSTANCE)) {
				continue;
			}
			if (Boolean.TRUE.equals(player.getAttached(Future.ECHO_SLAIN))) {
				mark(player, "echo");
			}
			if (player.level().dimension() == Future.FUTURE) {
				mark(player, "future");
			}
			if (player.getInventory().contains(stack -> stack.is(StoppedClockItem.STOPPED_CLOCK))) {
				mark(player, "clock");
			}
		}
	}

	/** (From an Omen pilot's talk.) The first time, the pilot hands over the drawings. */
	public static void omen(ServerPlayer player) {
		if (!mark(player, "omen")) {
			return;
		}
		ItemStack drawings = LoreBook.written(DRAWINGS, "Drawings", DRAWINGS_PAGES);
		if (!player.getInventory().add(drawings)) {
			player.drop(drawings, false, Prediction.SERVER_ONLY);
		}
		player.sendOverlayMessage(Component.translatable("message.jaymospowers.chrono_road.drawings").withStyle(ChatFormatting.GRAY));
	}
}
