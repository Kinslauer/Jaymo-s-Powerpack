package com.jaymoskingdoms;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;

/**
 * (0.7.227) Asking a gifted villager about their power. Andy: "The mca screen should only show it once the player has seen,
 * and asked about the powers they saw. The option comes after that happens." Powers tells us of each player who sees a
 * gift at work ({@link PowersCompat#giftWitnesses}); from then on that player gets "Ask about their power" in the
 * villager's MCA menu ({@link #ANSWER}). Asked, they answer (one who hides it begs you to keep it quiet, one who doesn't
 * care boasts, a master speaks of what they've made of it), and only then does that player's MCA screen show their gift.
 * One who bears you too much ill will won't talk about it, and it takes a shared tongue.
 */
public final class GiftTales {
	/** "Ask about their power" (added to MCA's {@code main} menu by our dialogue data). */
	public static final String ANSWER = "jaymoskingdoms_gift";
	/** How many ways each kind of answer is said. */
	static final int LINES = 3;

	/** On a player: the gifted villagers they've seen use their power. Saved, kept through death. */
	static final AttachmentType<Set<UUID>> SEEN = AttachmentRegistry.create(JaymosKingdoms.id("gifts_seen"),
		builder -> builder.persistent(UUIDUtil.CODEC_SET).copyOnDeath());
	/** On a player: the gifted villagers who've told them of their power. Saved, kept through death, synced to them alone. */
	static final AttachmentType<Set<UUID>> ASKED = AttachmentRegistry.create(JaymosKingdoms.id("gifts_asked"),
		builder -> builder.persistent(UUIDUtil.CODEC_SET).copyOnDeath()
			.syncWith(ByteBufCodecs.collection(HashSet::new, UUIDUtil.STREAM_CODEC), AttachmentSyncPredicate.targetOnly()));

	private GiftTales() {
	}

	static void register() {
		ServerLifecycleEvents.SERVER_STARTING.register(server -> PowersCompat.giftWitnesses((player, gifted) -> {
			if (player instanceof ServerPlayer p) {
				saw(p, gifted);
			}
		}));
	}

	private static boolean holds(Player player, AttachmentType<Set<UUID>> type, Entity e) {
		Set<UUID> set = player.getAttached(type);
		return set != null && set.contains(e.getUUID());
	}

	private static void add(Player player, AttachmentType<Set<UUID>> type, Entity e) {
		if (!holds(player, type, e)) {
			// a new set, so it's synced
			Set<UUID> set = player.getAttached(type);
			Set<UUID> next = set == null ? new HashSet<>() : new HashSet<>(set);
			next.add(e.getUUID());
			player.setAttached(type, next);
		}
	}

	/** {@code player} saw {@code gifted}'s power at work. */
	static void saw(ServerPlayer player, Entity gifted) {
		add(player, SEEN, gifted);
	}

	/** Whether {@code player} may ask {@code villager} about their power: they've seen it, and haven't asked yet. */
	public static boolean canAsk(ServerPlayer player, Villager villager) {
		return PowersCompat.gift(villager) != null && holds(player, SEEN, villager) && !holds(player, ASKED, villager);
	}

	/** {@code player} asked {@code villager} about their power (MCA's menu): what they say; it's on the MCA screen from then. */
	public static Component ask(ServerPlayer player, Villager villager) {
		String gift = PowersCompat.gift(villager);
		if (gift == null) {
			return Component.translatable("dialogue.chat.jaymoskingdoms.gift_none");
		}
		if (Recollections.refuses(villager, player)) {
			return Component.translatable("dialogue.chat.jaymoskingdoms.gift_refused");
		}
		add(player, ASKED, villager);
		Component power = Component.translatable("power.jaymospowers." + gift);
		String kind;
		if (PowersCompat.giftCorrupted(villager)) {
			kind = "gift_corrupted";
		} else if (PowersCompat.giftMaster(villager)) {
			kind = PowersCompat.giftOpen(villager) ? "gift_master_open" : "gift_master_hidden";
		} else {
			kind = PowersCompat.giftOpen(villager) ? "gift_open" : "gift_hidden";
		}
		return Component.translatable("dialogue.chat.jaymoskingdoms." + kind + "." + villager.getRandom().nextInt(LINES), power);
	}

	/** This client's player has asked {@code e} about their power (client side; the MCA screen). */
	public static boolean askedHere(Entity e) {
		Player viewer = Introductions.viewer.get();
		return viewer != null && holds(viewer, ASKED, e);
	}
}
