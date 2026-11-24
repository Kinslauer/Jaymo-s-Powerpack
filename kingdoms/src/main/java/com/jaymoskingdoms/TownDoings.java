package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.Nullable;

/**
 * (Village mods patch 5, Andy's pick "what the town is doing") A live page of what a town is about right now, read off its
 * own books as they stand: what its builders are putting up and what plans wait for them ({@link Works},
 * {@link Commissions}), how much weathering its builders have still to mend ({@link Upkeep}), the town's shared works
 * ({@link TownProjects}) and its troubles ({@link TownTroubles}), what the town is known for ({@link KnownGoods}) and
 * the house it's building you ({@link GiftHouses}). Read at the town board (sneak and use it with something in hand:
 * the board's own uses are an empty hand, or a notice held standing) or asked for under town business
 * ({@link TalkErrands}). Shown on Minecraft's book page; nothing is handed over. A town that hates you won't say.
 */
public final class TownDoings {
	/** The pages, sent to the client to open on a book page. */
	public record Page(List<Component> pages) implements CustomPacketPayload {
		public static final Type<Page> TYPE = new Type<>(JaymosKingdoms.id("town_doings"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Page> CODEC = StreamCodec.composite(
			ComponentSerialization.STREAM_CODEC.apply(ByteBufCodecs.list()), Page::pages, Page::new);

		@Override
		public Type<Page> type() {
			return TYPE;
		}
	}

	private TownDoings() {
	}

	static void register() {
		PayloadTypeRegistry.clientboundPlay().register(Page.TYPE, Page.CODEC);
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (hand != InteractionHand.MAIN_HAND || !player.isSecondaryUseActive() || player.getMainHandItem().isEmpty()
				|| Bounties.BOARD == null || !level.getBlockState(hit.getBlockPos()).is(Bounties.BOARD)) {
				return InteractionResult.PASS;
			}
			if (level instanceof ServerLevel server && player instanceof ServerPlayer sp) {
				Faction f = Bounties.courtAt(server, hit.getBlockPos());
				if (f == null) {
					f = Factions.around(server.getServer(), server, hit.getBlockPos(), Villages.VILLAGE_RADIUS);
				}
				open(sp, f);
			}
			return InteractionResult.SUCCESS;
		});
	}

	/** {@link TalkErrands}: {@code speaker}'s town's page. */
	static void show(ServerPlayer player, LivingEntity speaker) {
		if (speaker.level() instanceof ServerLevel level) {
			open(player, Factions.around(level.getServer(), level, speaker.blockPosition(), Villages.VILLAGE_RADIUS));
		}
	}

	/** {@code f}'s page for {@code player}, opened on their screen. */
	static void open(ServerPlayer player, @Nullable Faction f) {
		if (f == null || f.fallen() || f.type() == FactionType.BARBARIAN) {
			player.sendOverlayMessage(Component.translatable("doings.jaymoskingdoms.nowhere"));
			return;
		}
		if (Reputation.get(player, f.id()) <= Reputation.HOSTILE) {
			player.sendOverlayMessage(Component.translatable("doings.jaymoskingdoms.refused", f.name()));
			return;
		}
		if (ServerPlayNetworking.canSend(player, Page.TYPE)) {
			ServerPlayNetworking.send(player, new Page(pages(player, f)));
		}
	}

	/** The pages: a heading page, then building, mending, the town's works and its troubles. */
	static List<Component> pages(ServerPlayer player, Faction f) {
		MinecraftServer server = player.level().getServer();
		ServerLevel world = Factions.level(server);
		List<Component> pages = new ArrayList<>();
		List<Component> about = new ArrayList<>();
		about.add(KnownGoods.line(world, f, player));
		Component gift = GiftHouses.line(server, f, player);
		if (gift != null) {
			about.add(gift);
		}
		pages.add(Component.empty().append(Component.translatable("doings.jaymoskingdoms.title", f.name()).withStyle(ChatFormatting.BOLD))
			.append("\n\n").append(join(about)));
		TownLedger.pack(pages, "doings_building", building(server, f));
		int weathered = world.getAttachedOrElse(Upkeep.QUEUE, Map.<String, List<Long>>of()).getOrDefault(Integer.toString(f.id()), List.of()).size();
		TownLedger.pack(pages, "doings_mending", weathered == 0 ? List.of()
			: List.of(Component.translatable("doings.jaymoskingdoms.mending", weathered)));
		TownLedger.pack(pages, "works", TownProjects.ledger(server, f));
		List<Component> troubles = new ArrayList<>();
		for (String kind : TownTroubles.of(server, f.id())) {
			troubles.add(Component.translatable("trouble.jaymoskingdoms." + kind + ".start", f.name()));
		}
		TownLedger.pack(pages, "troubles", troubles);
		return pages;
	}

	/** What's going up now (and how far along), then the plans waiting for the builders. */
	private static List<Component> building(MinecraftServer server, Faction f) {
		List<Component> lines = new ArrayList<>();
		for (Works.Project p : Works.of(server, f.id()).projects()) {
			String stage = p.stage() == Works.STAKE ? "staking" : p.stage() == Works.PREP ? "clearing" : "building";
			lines.add(Component.translatable("doings.jaymoskingdoms.under_way", name(p.entry()), Component.translatable("doings.jaymoskingdoms.stage." + stage)));
		}
		for (Commissions.Commission c : Commissions.of(server, f.id())) {
			lines.add(c.byName().isEmpty() ? Component.translatable("doings.jaymoskingdoms.waiting", name(c.design()))
				: Component.translatable(c.gift() ? "doings.jaymoskingdoms.waiting_gift" : "doings.jaymoskingdoms.waiting_plans", name(c.design()), c.byName()));
		}
		return lines;
	}

	/** A building's name: the catalogue's own, or a design's file name. */
	private static Component name(String entry) {
		String bare = Designs.isDesign(entry) ? entry.substring(Designs.PREFIX.length()) : entry;
		return Component.translatableWithFallback("building.jaymoskingdoms." + bare, bare.replace('_', ' '));
	}

	private static Component join(List<Component> lines) {
		var out = Component.empty();
		for (int i = 0; i < lines.size(); i++) {
			out.append(i > 0 ? Component.literal("\n\n") : Component.empty()).append(lines.get(i));
		}
		return out;
	}
}
