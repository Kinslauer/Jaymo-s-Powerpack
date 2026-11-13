package com.jaymoskingdoms.films;

import com.jaymoskingdoms.Ford;
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
import net.minecraft.core.BlockPos;
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
	/**
	 * The closing of the Second Defence ({@code SecondDefence}): dawn over the ford that was held, the light still burning
	 * in the keep, the last of the old legion going back into the ground. No words. It plays at the ford itself.
	 */
	public static final String SECOND_DEFENCE = "second_defence";
	public static final List<String> ALL = List.of(LONG_DEFENCE, SECOND_DEFENCE);
	/** Lore books that carry a film: the book's id ({@link LoreBooks#idOf}) to the film it plays the first time it's opened. */
	static final Map<String, String> BOOK_FILMS = Map.of(JaymosKingdoms.id("the_long_defence").toString(), LONG_DEFENCE);

	/**
	 * How long a film runs on the player's game (ticks; {@code LoreFilmClient.LENGTH}). Andy's rule for our films: the
	 * player takes no harm while one plays, since their keys don't answer. The protection ends early once they strike a blow
	 * or walk off from where it began (a skipped film), so it can't be used as a shield.
	 */
	public static final int FILM_TICKS = 1144, SECOND_TICKS = 320;
	/** The Second Defence's closing is set at the ford when the player is within this of it; else where they stand, a keep taken to be this far behind them. */
	static final double SITE_NEAR = 96.0;
	static final int NO_KEEP_BACK = 30;
	/**
	 * Andy: "take an area outside of an actually generated kingdom seat, and set the stage there". The Long Defence is staged
	 * on the land outside the nearest kingdom's seat when one is within {@link #SEAT_NEAR} of the player (its ground is
	 * loaded and real), the seat standing in the distance as the film's Keep, {@link #SEAT_BACK} behind the stage's origin
	 * (the film's own Keep stood that far back).
	 */
	static final double SEAT_NEAR = 110.0;
	static final int SEAT_BACK = 62;
	static final double LEFT_SPOT = 1.5;
	/** (Server) players in a film just now: until when (game time), and where they stood. */
	private static final Map<java.util.UUID, Long> WATCHING = new java.util.HashMap<>();
	private static final Map<java.util.UUID, net.minecraft.world.phys.Vec3> WATCHING_AT = new java.util.HashMap<>();

	/** On a player: the films they have seen from a book. Saved, kept through death. */
	static final AttachmentType<List<String>> SEEN = AttachmentRegistry.create(JaymosKingdoms.id("lore_films_seen"),
		builder -> builder.persistent(Codec.STRING.listOf()).copyOnDeath());

	/**
	 * Tells a player's game to play a film. {@code seat}: the Long Defence is staged on a kingdom's seat ({@link #seatStage}):
	 * {@code ford} is the stage's origin, on the land outside the seat, and {@code keep} is the seat itself, which stands
	 * where the film's Keep does (so the film does not build one of its own).
	 */
	public record Play(String film, BlockPos ford, BlockPos keep, boolean seat) implements CustomPacketPayload {
		public static final Type<Play> TYPE = new Type<>(JaymosKingdoms.id("lore_film"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Play> CODEC = StreamCodec.composite(ByteBufCodecs.STRING_UTF8, Play::film,
			BlockPos.STREAM_CODEC, Play::ford, BlockPos.STREAM_CODEC, Play::keep, ByteBufCodecs.BOOL, Play::seat, Play::new);

		@Override
		public Type<Play> type() {
			return TYPE;
		}
	}

	private LoreFilms() {
	}

	/** The sorcerer's fire: soul fire, but black (drawn by the client's ShadowFlameParticle). */
	public static net.minecraft.core.particles.SimpleParticleType SHADOWFLAME;

	public static void register() {
		SHADOWFLAME = net.minecraft.core.Registry.register(net.minecraft.core.registries.BuiltInRegistries.PARTICLE_TYPE, JaymosKingdoms.id("shadowflame"),
			net.fabricmc.fabric.api.particle.v1.FabricParticleTypes.simple(true));
		JaymosKingdoms.LOGGER.debug("lore film attachment: {}", SEEN);
		FilmActor.register();
		PayloadTypeRegistry.clientboundPlay().register(Play.TYPE, Play.CODEC);
		net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.ALLOW_DAMAGE.register(LoreFilms::harm);
		net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			WATCHING.remove(handler.player.getUUID());
			WATCHING_AT.remove(handler.player.getUUID());
		});
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

	public static boolean seen(ServerPlayer player, String film) {
		return player.getAttachedOrElse(SEEN, List.of()).contains(film);
	}

	/** Whether {@code player}'s game has the film player (Kingdoms on the client). */
	public static boolean canPlay(ServerPlayer player) {
		return ServerPlayNetworking.canSend(player, Play.TYPE);
	}

	/** How long {@code film} runs (ticks). */
	public static int ticks(String film) {
		return SECOND_DEFENCE.equals(film) ? SECOND_TICKS : FILM_TICKS;
	}

	/** Plays {@code film} for {@code player}. */
	public static void play(ServerPlayer player, String film) {
		if (canPlay(player)) {
			// (a film set at a place of the world's is told where: the ford and the keep, when the player is there)
			BlockPos ford = player.blockPosition(), keep = ford.relative(player.getDirection().getOpposite(), NO_KEEP_BACK);
			Ford.Site site = Ford.site(player.level().getServer());
			if (site != null && player.level().dimension() == net.minecraft.world.level.Level.OVERWORLD && site.ford().closerToCenterThan(player.position(), SITE_NEAR)) {
				ford = site.ford();
				keep = site.keep();
			}
			boolean seat = false;
			if (LONG_DEFENCE.equals(film)) {
				BlockPos[] stage = seatStage(player);
				if (stage != null) {
					ford = stage[0];
					keep = stage[1];
					seat = true;
				}
			}
			ServerPlayNetworking.send(player, new Play(film, ford, keep, seat));
			WATCHING.put(player.getUUID(), player.level().getGameTime() + ticks(film));
			WATCHING_AT.put(player.getUUID(), player.position());
		}
	}

	/**
	 * The stage on a kingdom's seat: {the stage's origin, the seat}, or null when no realm's seat is within {@link #SEAT_NEAR}
	 * of {@code player} on the Overworld. The origin lies {@link #SEAT_BACK} from the seat, on the side the player is on (the
	 * way they face if they stand at the seat itself), on the ground there.
	 */
	static BlockPos @org.jspecify.annotations.Nullable [] seatStage(ServerPlayer player) {
		if (player.level().dimension() != net.minecraft.world.level.Level.OVERWORLD) {
			return null;
		}
		net.minecraft.server.MinecraftServer server = player.level().getServer();
		com.jaymoskingdoms.Faction best = null;
		double bestD = SEAT_NEAR * SEAT_NEAR;
		for (com.jaymoskingdoms.Faction f : com.jaymoskingdoms.Factions.all(server)) {
			if (!f.type().isRealm() || f.fallen()) {
				continue;
			}
			double dx = f.seat().getX() + 0.5 - player.getX(), dz = f.seat().getZ() + 0.5 - player.getZ(), d = dx * dx + dz * dz;
			if (d < bestD) {
				best = f;
				bestD = d;
			}
		}
		if (best == null) {
			return null;
		}
		BlockPos seat = best.seat();
		double dx = player.getX() - (seat.getX() + 0.5), dz = player.getZ() - (seat.getZ() + 0.5), len = Math.sqrt(dx * dx + dz * dz);
		if (len < 8.0) {
			net.minecraft.world.phys.Vec3 look = player.getLookAngle();
			dx = look.x;
			dz = look.z;
			len = Math.max(1.0E-3, Math.sqrt(dx * dx + dz * dz));
		}
		int x = (int)Math.floor(seat.getX() + 0.5 + dx / len * SEAT_BACK), z = (int)Math.floor(seat.getZ() + 0.5 + dz / len * SEAT_BACK);
		net.minecraft.server.level.ServerLevel level = player.level();
		if (!level.hasChunkAt(new BlockPos(x, seat.getY(), z))) {
			return null;
		}
		int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
		return new BlockPos[] {new BlockPos(x, y, z), seat};
	}

	/** No harm to a player while their film plays; a blow they strike, or walking away from the spot, ends that. */
	private static boolean harm(LivingEntity victim, net.minecraft.world.damagesource.DamageSource source, float amount) {
		if (WATCHING.isEmpty()) {
			return true;
		}
		if (source.getEntity() instanceof ServerPlayer striker) {
			WATCHING.remove(striker.getUUID());
		}
		Long until = WATCHING.get(victim.getUUID());
		if (until == null) {
			return true;
		}
		net.minecraft.world.phys.Vec3 at = WATCHING_AT.get(victim.getUUID());
		if (victim.level().getGameTime() >= until || at == null || victim.position().distanceTo(at) > LEFT_SPOT) {
			WATCHING.remove(victim.getUUID());
			WATCHING_AT.remove(victim.getUUID());
			return true;
		}
		return false;
	}

	/** An elder mage or a demon tells {@code player} the story: the old war, as a film. */
	public static void told(ServerPlayer player, LivingEntity teller) {
		play(player, LONG_DEFENCE);
		com.jaymoskingdoms.StoryRoads.told(player);
	}
}
