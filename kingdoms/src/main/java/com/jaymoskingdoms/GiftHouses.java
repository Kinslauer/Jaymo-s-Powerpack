package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.Nullable;

/**
 * (Village mods patch 5, Andy's pick "a town builds you a house") Once a town holds you in the highest regard
 * ({@link #GIFT_REPUTATION}, revered), it offers, once, to build you a house of your own. Ask about town business there
 * to take it up ({@link TalkErrands}): its builders put a house up like any plan given ({@link Commissions#gift}), out of
 * its own stores, and when it stands it's yours. You're told where it is; if you have no homestead yet, it becomes your
 * homestead ({@link Homesteads#grant}), so the land round it is yours to work. One offer a town, never again, and
 * nothing is lost by leaving it.
 */
public final class GiftHouses {
	// ===== the knobs ================================================================================================
	/** Reputation with a town that brings its offer: the top of the scale ("revered", {@link Reputation#word}). */
	static final int GIFT_REPUTATION = 800;
	/** How often offers and finished houses are looked for (ticks). */
	static final int CHECK_TICKS = 200;
	/** The catalogue building a town builds for you. */
	static final String HOUSE = "house";
	// ================================================================================================================

	/** Where an offer stands. */
	static final int OFFERED = 0, ASKED = 1, BUILT = 2;

	/** One town's offer to one player: how far it got, the house (its middle, once built), and whether they've been told. */
	record Gift(int stage, BlockPos house, boolean told) {
		static final Codec<Gift> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.INT.fieldOf("stage").forGetter(Gift::stage),
				BlockPos.CODEC.optionalFieldOf("house", BlockPos.ZERO).forGetter(Gift::house),
				Codec.BOOL.optionalFieldOf("told", false).forGetter(Gift::told))
			.apply(i, Gift::new));
	}

	/** "player uuid@faction id" → the offer. Kept on the Overworld. */
	static final AttachmentType<Map<String, Gift>> GIFTS = AttachmentRegistry.create(JaymosKingdoms.id("gift_houses"),
		builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, Gift.CODEC)));

	private GiftHouses() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % CHECK_TICKS == 0) {
				for (ServerPlayer player : server.getPlayerList().getPlayers()) {
					check(server, player);
				}
			}
		});
	}

	private static String key(UUID player, int faction) {
		return player + "@" + faction;
	}

	private static Map<String, Gift> all(MinecraftServer server) {
		return Factions.level(server).getAttachedOrElse(GIFTS, Map.of());
	}

	static @Nullable Gift of(MinecraftServer server, UUID player, int faction) {
		return all(server).get(key(player, faction));
	}

	private static void put(MinecraftServer server, UUID player, int faction, Gift gift) {
		Map<String, Gift> all = new HashMap<>(all(server));
		all.put(key(player, faction), gift);
		Factions.level(server).setAttached(GIFTS, Map.copyOf(all));
	}

	/** A town that can build houses at all: standing, lawful, with builders who build them. */
	private static boolean builds(Faction f) {
		return !f.fallen() && f.type() != FactionType.BARBARIAN && Blueprint.catalogue(f.type()).stream().anyMatch(e -> e.id().equals(HOUSE));
	}

	/** New offers for {@code player}, and word of a house finished while they were away. */
	private static void check(MinecraftServer server, ServerPlayer player) {
		for (Faction f : Factions.all(server)) {
			Gift gift = of(server, player.getUUID(), f.id());
			if (gift == null) {
				if (builds(f) && Reputation.get(player, f.id()) >= GIFT_REPUTATION) {
					put(server, player.getUUID(), f.id(), new Gift(OFFERED, BlockPos.ZERO, false));
					player.sendSystemMessage(Component.translatable("gift_house.jaymoskingdoms.offered", f.name()).withStyle(ChatFormatting.GOLD));
				}
			} else if (gift.stage() == BUILT && !gift.told()) {
				tell(server, player, f, gift);
			}
		}
	}

	/** The house is up: {@code player} hears where, and it's their homestead if they had none. */
	private static void tell(MinecraftServer server, ServerPlayer player, Faction f, Gift gift) {
		ServerLevel world = Factions.level(server);
		BlockPos at = gift.house();
		boolean home = Homesteads.of(world, player.getUUID()) == null && Homesteads.grant(player, world, at);
		put(server, player.getUUID(), f.id(), new Gift(BUILT, at, true));
		player.sendSystemMessage(Component.translatable(home ? "gift_house.jaymoskingdoms.built_home" : "gift_house.jaymoskingdoms.built",
			f.name(), at.getX(), at.getY(), at.getZ()).withStyle(ChatFormatting.GOLD));
		player.level().playSound(null, player.blockPosition(), SoundEvents.VILLAGER_CELEBRATE, SoundSource.PLAYERS, 0.8F, 1.0F);
	}

	/** The town {@code speaker} belongs to has offered {@code player} a house they haven't taken up yet. */
	static boolean offered(ServerPlayer player, LivingEntity speaker) {
		Faction f = town(player, speaker);
		Gift gift = f == null ? null : of(player.level().getServer(), player.getUUID(), f.id());
		return gift != null && gift.stage() == OFFERED;
	}

	private static @Nullable Faction town(ServerPlayer player, LivingEntity speaker) {
		if (!(speaker.level() instanceof ServerLevel level)) {
			return null;
		}
		Faction f = Factions.around(level.getServer(), level, speaker.blockPosition(), Villages.VILLAGE_RADIUS);
		return f == null || !builds(f) ? null : f;
	}

	/** {@link TalkErrands}: {@code player} takes the town up on its offer; its builders queue the house. */
	static void accept(ServerPlayer player, LivingEntity speaker) {
		ServerLevel level = (ServerLevel)speaker.level();
		MinecraftServer server = level.getServer();
		Faction f = town(player, speaker);
		if (f == null || !offered(player, speaker)) {
			return;
		}
		if (!Commissions.gift(server, f, player, HOUSE)) {
			Speech.tell(level, speaker, player, Component.translatable("gift_house.jaymoskingdoms.busy"), Speech.WHITE);
			return;
		}
		put(server, player.getUUID(), f.id(), new Gift(ASKED, BlockPos.ZERO, false));
		Speech.tell(level, speaker, player, Component.translatable("gift_house.jaymoskingdoms.accepted", f.name()), Speech.GOLD);
	}

	/** {@link Commissions}: the house {@code f} was building for {@code player} is finished (the newest of its houses). */
	static void built(MinecraftServer server, Faction f, UUID player, Works.Site works, String entry) {
		BlockPos at = null;
		List<Works.Built> built = works.built();
		for (int i = built.size() - 1; i >= 0 && at == null; i--) {
			Works.Built b = built.get(i);
			if (b.entry().equals(entry)) {
				at = new BlockPos((b.min().getX() + b.max().getX()) / 2, b.min().getY() + 1, (b.min().getZ() + b.max().getZ()) / 2);
			}
		}
		put(server, player, f.id(), new Gift(BUILT, at == null ? f.seat() : at, false));
		ServerPlayer online = server.getPlayerList().getPlayer(player);
		if (online != null) {
			tell(server, online, f, of(server, player, f.id()));
		}
	}

	/** A line for the town's page ({@link TownDoings}) about {@code player}'s house here, or null if there's nothing to say. */
	static @Nullable Component line(MinecraftServer server, Faction f, ServerPlayer player) {
		Gift gift = of(server, player.getUUID(), f.id());
		if (gift == null) {
			return null;
		}
		return switch (gift.stage()) {
			case OFFERED -> Component.translatable("gift_house.jaymoskingdoms.page.offered");
			case ASKED -> Component.translatable("gift_house.jaymoskingdoms.page.building");
			default -> Component.translatable("gift_house.jaymoskingdoms.page.built", gift.house().getX(), gift.house().getZ());
		};
	}
}
