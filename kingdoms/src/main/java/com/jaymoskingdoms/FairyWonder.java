package com.jaymoskingdoms;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.512, Andy 2026-11-04: pink fairies "highly prized possessions among players and villagers alike"). Jaymo's
 * Powers' Bottled Pink Fairy is the rarest thing a villager will ever see. Townsfolk (vanilla and MCA villagers) who see
 * a player wearing one (amulet or charm slot) or holding one stop and stare and say so, longing, envious or awed
 * (children beg to see her), once a day each per player, and the sight lifts an MCA villager's mood. Given to an MCA
 * villager she is the dearest gift of all ({@code data/mca/gifts/fairy.json}, above a nether star). No screen text
 * says what she is or does; only their talk.
 */
public final class FairyWonder {
	/** How often (ticks) players are looked at; how near a villager must be to see her. */
	static final int EVERY = 40;
	static final double SEEN_NEAR = 8.0;
	/** How often (ticks) one villager remarks to one player: a day. */
	static final long REMARK_EVERY = 24000L;
	/** The mood an MCA villager takes from the sight. */
	static final int WONDER = 3;
	/** Lines (lang {@code fairy.jaymoskingdoms.seen.N}, {@code .child.N}). */
	static final int LINES = 8, CHILD_LINES = 4;

	/** Per player and villager: when the villager last remarked on her. Not saved. */
	private static final Map<String, Long> REMARKED = new HashMap<>();
	private static @Nullable Item fairy;
	private static boolean looked;

	private FairyWonder() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(FairyWonder::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			REMARKED.clear();
			looked = false;
			fairy = null;
		});
	}

	/** Powers' bottled fairy, or null without Powers (or an older one). */
	private static @Nullable Item fairy() {
		if (!looked) {
			looked = true;
			fairy = BuiltInRegistries.ITEM.getOptional(Identifier.fromNamespaceAndPath("jaymospowers", "bottled_fairy")).orElse(null);
			if (fairy == Items.AIR) {
				fairy = null;
			}
		}
		return fairy;
	}

	/** Whether this player shows a fairy: worn as a bauble, or in either hand. */
	static boolean shows(ServerPlayer player) {
		Item item = fairy();
		if (item == null) {
			return false;
		}
		if (player.getItemInHand(InteractionHand.MAIN_HAND).is(item) || player.getItemInHand(InteractionHand.OFF_HAND).is(item)) {
			return true;
		}
		for (ItemStack stack : PowersCompat.baubles(player)) {
			if (stack.is(item)) {
				return true;
			}
		}
		return false;
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % EVERY != 0 || fairy() == null) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (!player.isAlive() || player.isSpectator() || player.isInvisible() || !(player.level() instanceof ServerLevel level) || !shows(player)) {
				continue;
			}
			long now = level.getGameTime();
			List<Villager> near = level.getEntitiesOfClass(Villager.class, player.getBoundingBox().inflate(SEEN_NEAR),
				v -> v.isAlive() && !v.isSleeping() && !Routines.busy(v) && v.hasLineOfSight(player));
			for (Villager v : near) {
				String key = player.getUUID() + "|" + v.getUUID();
				Long last = REMARKED.get(key);
				if (last != null && now - last < REMARK_EVERY) {
					continue;
				}
				REMARKED.put(key, now);
				remark(level, v, player);
				// (one at a time: the rest gape and wait their turn)
				break;
			}
		}
	}

	private static void remark(ServerLevel level, Villager v, ServerPlayer player) {
		RandomSource random = level.getRandom();
		String line = v.isBaby() ? "fairy.jaymoskingdoms.seen.child." + random.nextInt(CHILD_LINES) : "fairy.jaymoskingdoms.seen." + random.nextInt(LINES);
		v.getLookControl().setLookAt(player, 30.0F, 30.0F);
		v.getNavigation().stop();
		McaCompat.cheer(v, WONDER);
		Speech.sayTo(level, v, player, Component.translatable(line), Speech.GOLD);
	}
}
