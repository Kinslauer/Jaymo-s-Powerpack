package com.jaymospowers.core;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.power.arsenal.ArsenalArmory;
import com.jaymospowers.power.arsenal.ArsenalPlaces;
import com.jaymospowers.power.arsenal.ArsenalPower;
import com.jaymospowers.power.arsenal.ArsenalRelics;
import com.jaymospowers.power.web.WebPower;
import com.jaymospowers.power.web.WebSymbiote;
import com.jaymospowers.power.web.WebSymbioteState;
import com.jaymospowers.power.web.WebSymbioteStrain;
import com.jaymospowers.power.xenoform.BandUpgrades;
import com.jaymospowers.power.xenoform.XenoformPower;
import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Prediction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * Three powers' roads through the Retaking (shown in Jaymo's Kingdoms' Story tab, through the client's {@code StoryRoads};
 * the Chrono's is {@code ChronoRoad}). Each says what to do, never what it means; a few steps hand over a short book.
 * <ul>
 * <li><b>The Web Slinger's Road</b>: bond with a symbiote at a meteor crater; grow close enough that it stops fighting you
 * ({@link #WEB_DEEP_BOND}), when it gives back what it remembers ({@code web_memory}); purify it white; keep the white one
 * until it trusts you wholly ({@link #WEB_ANTI_BOND}), when it writes a few words of its own ({@code web_white}); meet the
 * Quiet One (Kingdoms leaves {@link #QUIET_TAG} once the old war's film is seen, or he is your spirit).</li>
 * <li><b>The Xenoform's Road</b>: hear a star-reader out; set foot on the Pale Moon; have your band reforged; tear the
 * rival band off the band hunter, who leaves his orders behind ({@code hunter_orders}).</li>
 * <li><b>The Arsenal's Road</b>: claim a relic; mark a place to come back to; pull something from the future (once, a
 * handbill from Los Perrito comes through with it, {@code perrito_handbill}); pin a weapon, so it is you who says what
 * comes.</li>
 * </ul>
 */
public final class PowerRoads {
	/** The road keys and their steps, in order. */
	public static final Map<String, List<String>> STEPS = Map.of(
		"web", List.of("crater", "deep", "white", "anti", "quiet"),
		"xeno", List.of("reader", "moon", "forge", "hunter"),
		"arsenal", List.of("relic", "place", "future", "pinned"));
	/** The bond at which the Web road's second step is done. */
	public static final int WEB_DEEP_BOND = 60;
	/** The bond at which a white symbiote (the anti) writes its page, and the tag Kingdoms leaves when the Quiet One has been met. */
	public static final int WEB_ANTI_BOND = 90;
	public static final String QUIET_TAG = "jaymoskingdoms.retaking.quiet";
	/** How often (ticks) the steps that are a state are looked at. */
	static final int LOOK_EVERY = 100;
	static final ResourceKey<Level> MOON = ResourceKey.create(Registries.DIMENSION, JaymosPowers.id("moon"));

	/** The steps done, as {@code road.step}. Saved, kept through death, synced to its owner for the Story tab. */
	public static final AttachmentType<List<String>> DONE = AttachmentRegistry.create(JaymosPowers.id("power_roads"),
		builder -> builder.persistent(Codec.STRING.listOf()).copyOnDeath()
			.syncWith(ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), AttachmentSyncPredicate.targetOnly()));

	private PowerRoads() {
	}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(PowerRoads::tick);
	}

	/** Whether {@code player} has road {@code road}. */
	public static boolean yours(Player player, String road) {
		return switch (road) {
			case "web" -> Powers.has(player, WebPower.INSTANCE);
			case "xeno" -> Powers.has(player, XenoformPower.INSTANCE);
			case "arsenal" -> Powers.has(player, ArsenalPower.INSTANCE);
			default -> false;
		};
	}

	public static boolean done(Player player, String road, String step) {
		return player.getAttachedOrElse(DONE, List.of()).contains(road + "." + step);
	}

	/** Marks {@code road}'s {@code step} done, if the road is theirs; true if it wasn't before. */
	public static boolean mark(ServerPlayer player, String road, String step) {
		if (!yours(player, road) || done(player, road, step)) {
			return false;
		}
		List<String> more = new ArrayList<>(player.getAttachedOrElse(DONE, List.of()));
		more.add(road + "." + step);
		player.setAttached(DONE, List.copyOf(more));
		return true;
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % LOOK_EVERY != 0) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (yours(player, "web")) {
				WebSymbioteState state = WebSymbiote.state(player);
				if (state.bonded()) {
					mark(player, "web", "crater");
					if (state.bond() >= WEB_DEEP_BOND && mark(player, "web", "deep")) {
						give(player, LoreBook.written("web_memory", "What It Remembers", 4), "message.jaymospowers.road.web_memory");
					}
					if (state.kind() == WebSymbioteStrain.WHITE) {
						mark(player, "web", "white");
						if (state.bond() >= WEB_ANTI_BOND && mark(player, "web", "anti")) {
							give(player, LoreBook.written("web_white", "Quiet", 3), "message.jaymospowers.road.web_white");
						}
					}
				}
				if (player.entityTags().contains(QUIET_TAG)) {
					mark(player, "web", "quiet");
				}
			}
			if (yours(player, "xeno")) {
				if (player.level().dimension() == MOON) {
					mark(player, "xeno", "moon");
				}
				if (BandUpgrades.get(player).reforged()) {
					mark(player, "xeno", "forge");
				}
			}
			if (yours(player, "arsenal")) {
				if (!ArsenalRelics.stored(player).isEmpty()) {
					mark(player, "arsenal", "relic");
				}
				if (!ArsenalPlaces.marks(player).isEmpty()) {
					mark(player, "arsenal", "place");
				}
				if (ArsenalArmory.pinned(player) != null) {
					mark(player, "arsenal", "pinned");
				}
			}
		}
	}

	/** (A star-reader's talk.) */
	public static void starReader(ServerPlayer player) {
		mark(player, "xeno", "reader");
	}

	/** (The rival band torn off the band hunter.) He leaves his orders behind. */
	public static void hunterBeaten(ServerPlayer player) {
		if (mark(player, "xeno", "hunter")) {
			give(player, LoreBook.written("hunter_orders", "Orders", 3), "message.jaymospowers.road.hunter_orders");
		}
	}

	/** (A pull from the future through the Arsenal's rift.) The first time, a handbill comes through with it. */
	public static void arsenalFuture(ServerPlayer player) {
		if (mark(player, "arsenal", "future")) {
			give(player, LoreBook.written("perrito_handbill", "Notice", 3), "message.jaymospowers.road.perrito_handbill");
		}
	}

	private static void give(ServerPlayer player, ItemStack book, @Nullable String message) {
		if (!player.getInventory().add(book)) {
			player.drop(book, false, Prediction.SERVER_ONLY);
		}
		if (message != null) {
			player.sendOverlayMessage(Component.translatable(message).withStyle(ChatFormatting.GRAY));
		}
	}
}
