package com.jaymoskingdoms;

import com.jaymoskingdoms.money.Money;
import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import org.jspecify.annotations.Nullable;

/**
 * What a milestone of the Retaking pays ({@link Retaking}): every milestone done pays, once, with
 * <ul>
 * <li><b>coin</b> in the coin of the player's realm (the culture coins, {@link Money#pay(ServerPlayer, Faction, long)}; the
 * coin of where they stand if they have no realm), more for the later acts and double for a king's fall;</li>
 * <li><b>standing with the realm</b> they belong to: reputation with it and standing in it (which is rank). Without a realm,
 * only the coin;</li>
 * <li><b>understanding</b>, where the milestone is a thing learned about the world rather than a thing fought (a stone seen,
 * a Giant, the Aether, the ford, a fallen star, the Maw, a far world, a spirit, the dark below, the Judge, the Grue, the
 * light kept, the dragon, the Second Defence): experience in the Scholar pillar, which is the Astronomer's Journal's. The
 * kings carry their own unlock already, the {@link Knowledge} rung.</li>
 * </ul>
 * It is settled from {@link Retaking#look}, so it pays however the milestone was done (a deed, an advancement, a party
 * member's share). The milestones paid are kept, so nothing pays twice. It is said on the action bar, never in chat.
 */
final class StoryPay {
	/** Emeralds' worth of coin per act (Hearth to Legend), and a king's fall pays this many times over. */
	static final int[] COINS = {4, 8, 12, 18, 25};
	static final int KING_FACTOR = 2;
	/** Reputation with the realm per act, and standing in it per act. */
	static final int[] REPUTATION = {3, 4, 5, 6, 8};
	static final int[] STANDING = {5, 8, 12, 18, 25};
	/** Scholar experience per act for the milestones that are understanding. */
	static final int[] SCHOLAR = {0, 20, 28, 36, 48};
	/** The milestones that are understanding (see the class comment). */
	static final Set<String> UNDERSTANDING = Set.of("monolith", "giant", "aether", "ford", "giant_two", "giant_all", "star", "maw", "far_world",
		"spirit", "abyss", "weighed", "grue", "light", "dragon", "second_defence");

	/** The milestones already paid. Saved, kept through death. */
	static final AttachmentType<List<String>> PAID = AttachmentRegistry.create(JaymosKingdoms.id("retaking_paid"),
		builder -> builder.persistent(Codec.STRING.listOf()).copyOnDeath());

	private StoryPay() {
	}

	static void register() {
		JaymosKingdoms.LOGGER.debug("retaking pay attachment: {}", PAID);
	}

	/** Pays every milestone {@code player} has done and not been paid for. */
	static void settle(ServerPlayer player) {
		List<String> paid = player.getAttachedOrElse(PAID, List.of());
		List<Retaking.Milestone> owed = new ArrayList<>();
		for (Retaking.Milestone m : Retaking.MILESTONES) {
			if (!paid.contains(m.name()) && Retaking.done(player, m.name())) {
				owed.add(m);
			}
		}
		if (owed.isEmpty()) {
			return;
		}
		List<String> now = new ArrayList<>(paid);
		for (Retaking.Milestone m : owed) {
			now.add(m.name());
		}
		player.setAttached(PAID, List.copyOf(now));
		long coin = 0L;
		int reputation = 0, standing = 0, scholar = 0;
		for (Retaking.Milestone m : owed) {
			int act = m.act().ordinal();
			boolean king = isKing(m.name());
			coin += Money.worth((long)COINS[act] * (king ? KING_FACTOR : 1));
			reputation += REPUTATION[act] * (king ? KING_FACTOR : 1);
			standing += STANDING[act] * (king ? KING_FACTOR : 1);
			if (UNDERSTANDING.contains(m.name())) {
				scholar += SCHOLAR[act];
			}
		}
		Faction realm = realm(player);
		if (realm != null) {
			Money.pay(player, realm, coin);
			Reputation.change(player, realm, reputation);
			Faction.Member member = realm.member(player.getUUID());
			if (member != null) {
				KingdomEvents.standing(player.level().getServer(), player, realm, member.standing() + standing);
			}
		} else {
			Money.pay(player, coin);
		}
		if (scholar > 0) {
			Skills.grant(player, Skills.Pillar.SCHOLAR, scholar);
		}
		Component pay = realm != null ? Component.translatable("message.jaymoskingdoms.retaking.paid_realm", Money.text(realm, coin), realm.name())
			: Component.translatable("message.jaymoskingdoms.retaking.paid", Money.text(player, coin));
		player.sendOverlayMessage(pay.copy().withStyle(ChatFormatting.GOLD));
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.7F, 1.1F);
		// only the latest king's fall is remarked on, so a long story paid at once is not a speech
		Retaking.Milestone latest = null;
		for (Retaking.Milestone m : owed) {
			if (isKing(m.name())) {
				latest = m;
			}
		}
		for (Retaking.Milestone m : owed) {
			StoryTellers.paid(player, m, m == latest);
		}
	}

	/** {@code name} is a king's fall (the milestone that closes an act). */
	static boolean isKing(String name) {
		for (String king : Retaking.KINGS) {
			if (king.equals(name)) {
				return true;
			}
		}
		return false;
	}

	/** The realm {@code player} belongs to, if any. */
	static @Nullable Faction realm(ServerPlayer player) {
		Faction f = Factions.of(player.level().getServer(), player.getUUID());
		return f != null && f.type().isRealm() ? f : null;
	}
}
