package com.jaymoskingdoms.films;

import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.LoreBooks;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Lore films: the old stories played as short films, never told in words on screen (Andy's rule: lore is found, not
 * stated). Andy (2026-11-10, Jujutsu Five picks): "The lore film should play upon discovery of a lore book, or being told
 * the story by an elder mage or demon". A film plays for one player, on their own game ({@code LoreFilmClient}); the
 * server only says which and when.
 * <ul>
 * <li><b>A lore book:</b> the first time you open a book that carries a film ({@link #BOOK_FILMS}), the film plays
 * instead of the pages; after that the book reads as any other.</li>
 * <li><b>An elder mage or a demon:</b> a kingdom's court wizard offers "Ask About the Old War" on MCA's Interact page
 * ({@code InteractActions#STORY}), and a demon master asks whether you want the story when you talk to them
 * ({@code ZFighterFolk#talk}). Told, it plays every time you ask.</li>
 * <li>Ops: {@code /kingdom film <film> [player]}.</li>
 * </ul>
 * The first film is {@link #LONG_DEFENCE}: the night the dead rose out of the shadows behind a sorcerer, and five mages held
 * the line (the grue, and Ambrose, Grimsby, Thessaly and Varro alive, and one more nobody names).
 */
public final class LoreFilms {
	/** The films there are. */
	public static final String LONG_DEFENCE = "long_defence";
	public static final List<String> ALL = List.of(LONG_DEFENCE);
	/** Lore books that carry a film: the book's id ({@link LoreBooks#idOf}) to the film it plays the first time it's opened. */
	static final Map<String, String> BOOK_FILMS = Map.of(JaymosKingdoms.id("the_long_defence").toString(), LONG_DEFENCE);

	/** On a player: the films they have seen from a book. Saved, kept through death. */
	static final AttachmentType<List<String>> SEEN = AttachmentRegistry.create(JaymosKingdoms.id("lore_films_seen"),
		builder -> builder.persistent(Codec.STRING.listOf()).copyOnDeath());

	/** Tells a player's game to play a film. */
	public record Play(String film) implements CustomPacketPayload {
		public static final Type<Play> TYPE = new Type<>(JaymosKingdoms.id("lore_film"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Play> CODEC = StreamCodec.composite(ByteBufCodecs.STRING_UTF8, Play::film, Play::new);

		@Override
		public Type<Play> type() {
			return TYPE;
		}
	}

	private LoreFilms() {
	}

	public static void register() {
		JaymosKingdoms.LOGGER.debug("lore film attachment: {}", SEEN);
		PayloadTypeRegistry.clientboundPlay().register(Play.TYPE, Play.CODEC);
		UseItemCallback.EVENT.register((player, level, hand) -> {
			ItemStack stack = player.getItemInHand(hand);
			if (!stack.is(Items.WRITTEN_BOOK)) {
				return InteractionResult.PASS;
			}
			String film = BOOK_FILMS.get(LoreBooks.idOf(stack));
			if (film == null) {
				return InteractionResult.PASS;
			}
			if (level.isClientSide()) {
				// the server decides: a book already seen opens as a book (the server's own use sends the screen)
				return InteractionResult.PASS;
			}
			if (player instanceof ServerPlayer sp && !seen(sp, film) && canPlay(sp)) {
				List<String> seen = new ArrayList<>(sp.getAttachedOrElse(SEEN, List.of()));
				seen.add(film);
				sp.setAttached(SEEN, List.copyOf(seen));
				play(sp, film);
				return InteractionResult.SUCCESS;
			}
			return InteractionResult.PASS;
		});
		CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) -> dispatcher.register(Commands.literal("kingdom")
			.then(Commands.literal("film").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(Commands.argument("film", StringArgumentType.word()).suggests((c, b) -> {
					ALL.forEach(b::suggest);
					return b.buildFuture();
				})
					.executes(c -> command(List.of(c.getSource().getPlayerOrException()), StringArgumentType.getString(c, "film")))
					.then(Commands.argument("players", EntityArgument.players())
						.executes(c -> command(EntityArgument.getPlayers(c, "players"), StringArgumentType.getString(c, "film"))))))));
	}

	private static int command(java.util.Collection<ServerPlayer> players, String film) {
		if (!ALL.contains(film)) {
			return 0;
		}
		int n = 0;
		for (ServerPlayer p : players) {
			if (canPlay(p)) {
				play(p, film);
				n++;
			}
		}
		return n;
	}

	static boolean seen(ServerPlayer player, String film) {
		return player.getAttachedOrElse(SEEN, List.of()).contains(film);
	}

	/** Whether {@code player}'s game has the film player (Kingdoms on the client). */
	public static boolean canPlay(ServerPlayer player) {
		return ServerPlayNetworking.canSend(player, Play.TYPE);
	}

	/** Plays {@code film} for {@code player}. */
	public static void play(ServerPlayer player, String film) {
		if (canPlay(player)) {
			ServerPlayNetworking.send(player, new Play(film));
		}
	}

	/** An elder mage or a demon tells {@code player} the story: the old war, as a film. */
	public static void told(ServerPlayer player, LivingEntity teller) {
		play(player, LONG_DEFENCE);
	}
}
