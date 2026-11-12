package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * A good smith knows a Twinblade when they see one (Andy, 2026-11-12: "Good enough blacksmiths can notice it on you and
 * fawn in amazement that you have one, and can tell you more of its origin"). A weaponsmith, armourer or toolsmith of at
 * least {@link #SMITH_LEVEL} (a journeyman or better) who sees one in your hands or on your belt within {@link #SEEN}
 * blocks stares, says so, and then tells you what they know of it, a line every {@link #GAP} ticks: an old Warrior-born
 * design, the oath in its joining, and the one drawing left in the Stormborn Tomb. Each smith tells each player once
 * ({@link #TOLD}). Jaymo's Powers' Twinblade is found by id; without Powers there is none, and nothing happens.
 */
public final class TwinbladeSmiths {
	/** How often it looks (ticks); how far a smith sees it; the least trade level that knows it; ticks between lines. */
	static final int EVERY = 20, SMITH_LEVEL = 3, GAP = 80;
	static final double SEEN = 8.0;
	/** How many amazed lines and origin lines there are in the language file. */
	static final int AMAZED_LINES = 3, ORIGIN_LINES = 3;
	static final Identifier TWINBLADE = Identifier.fromNamespaceAndPath("jaymospowers", "twinblade");

	/** On a smith: the players (UUIDs) they have told all they know. */
	public static final AttachmentType<List<String>> TOLD = AttachmentRegistry.create(JaymosKingdoms.id("twinblade_told"),
		builder -> builder.persistent(Codec.STRING.listOf()));

	/** A smith partway through the telling: to whom, the next line (0 the amazement), and when the last was said. */
	private record Telling(ServerPlayer player, int next, long last) {
	}

	private static final Map<Villager, Telling> TELLING = new HashMap<>();

	private TwinbladeSmiths() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(TwinbladeSmiths::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> TELLING.clear());
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % EVERY != 0) {
			return;
		}
		Item twinblade = BuiltInRegistries.ITEM.getOptional(TWINBLADE).orElse(null);
		if (twinblade == null) {
			return;
		}
		// the smiths already telling go on
		TELLING.entrySet().removeIf(e -> !go(e.getKey(), e.getValue(), e));
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (!player.isAlive() || player.isSpectator() || !carries(player, twinblade)) {
				continue;
			}
			ServerLevel level = player.level();
			for (Villager v : level.getEntitiesOfClass(Villager.class, player.getBoundingBox().inflate(SEEN), TwinbladeSmiths::goodSmith)) {
				if (TELLING.containsKey(v) || told(v, player) || v.distanceToSqr(player) > SEEN * SEEN || !v.hasLineOfSight(player)
					|| v.isSleeping() || Frights.afraid(v.getUUID())) {
					continue;
				}
				TELLING.put(v, new Telling(player, 0, level.getGameTime() - GAP));
			}
		}
	}

	/** One step of {@code v}'s telling; false once it is over (told, or the player gone). */
	private static boolean go(Villager v, Telling t, Map.Entry<Villager, Telling> entry) {
		ServerPlayer player = t.player();
		if (!v.isAlive() || v.isRemoved() || !player.isAlive() || player.isRemoved() || player.level() != v.level()
			|| v.distanceToSqr(player) > (SEEN * 1.5) * (SEEN * 1.5)) {
			return false;
		}
		ServerLevel level = player.level();
		long now = level.getGameTime();
		if (now - t.last() < GAP) {
			return true;
		}
		v.getLookControl().setLookAt(player, 30.0F, 30.0F);
		Component line = t.next() == 0
			? Component.translatable("talk.jaymoskingdoms.twinblade.amazed." + (1 + level.getRandom().nextInt(AMAZED_LINES)))
			: Component.translatable("talk.jaymoskingdoms.twinblade.origin." + t.next());
		Speech.tell(level, v, player, line, Speech.GOLD);
		if (t.next() >= ORIGIN_LINES) {
			List<String> told = new ArrayList<>(v.getAttachedOrElse(TOLD, List.of()));
			told.add(player.getStringUUID());
			v.setAttached(TOLD, List.copyOf(told));
			return false;
		}
		entry.setValue(new Telling(player, t.next() + 1, now));
		return true;
	}

	private static boolean told(Villager v, ServerPlayer player) {
		return v.getAttachedOrElse(TOLD, List.of()).contains(player.getStringUUID());
	}

	/** A weaponsmith, armourer or toolsmith of a journeyman's standing or better. */
	private static boolean goodSmith(Villager v) {
		if (!v.isAlive() || v.isBaby()) {
			return false;
		}
		Holder<VillagerProfession> trade = v.getVillagerData().profession();
		return (trade.is(VillagerProfession.WEAPONSMITH) || trade.is(VillagerProfession.ARMORER) || trade.is(VillagerProfession.TOOLSMITH))
			&& v.getVillagerData().level() >= SMITH_LEVEL;
	}

	/** {@code player} has a Twinblade in hand or on their hotbar (where the belt and the worn weapons show it). */
	private static boolean carries(ServerPlayer player, Item twinblade) {
		if (player.getMainHandItem().is(twinblade) || player.getOffhandItem().is(twinblade)) {
			return true;
		}
		for (int i = 0; i < 9; i++) {
			ItemStack stack = player.getInventory().getItem(i);
			if (stack.is(twinblade)) {
				return true;
			}
		}
		return false;
	}
}
