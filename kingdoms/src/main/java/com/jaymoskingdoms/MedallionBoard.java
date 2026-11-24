package com.jaymoskingdoms;

import com.jaymoskingdoms.Classes.PlayerClass;
import io.netty.buffer.ByteBuf;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The Cleric's medallion board (Andy, 2026-11-23): the medallion key ({@code MedallionClient}, default Y) lifts the holy symbol
 * before the chest, and while it is up the numpad's 1 to 9 are its nine marks, each its own sigil, laid out as the pad is
 * (7 8 9 on top, 1 2 3 below). Each key lays a sigil; the order is the prayer. Every spell's pattern ({@link #PATTERNS})
 * is four different marks, so a pattern that matches is cast at once, and marks that begin no pattern the Cleric knows fizzle.
 * Finished while sneaking, the pattern binds the spell to the medallion instead (the spell the symbol is turned to), which a
 * tap of the symbol in either hand casts ({@link CastingFocus}). A cast, a bind, or the key again lowers the board.
 * <p>Others see the arms raised (a {@link Casting.Gesture#RAISE}), the medallion as a small sigil before the chest, a circle at
 * the feet, a rune at each mark laid and strokes joining them.
 */
public final class MedallionBoard {
	/** The board drops after this long with no mark laid (ticks). */
	static final int IDLE_TICKS = 20 * 15;
	/** How often the raised medallion, the circle and the gesture are drawn again (ticks). */
	static final int REDRAW_TICKS = 10;
	/** How far before the eyes the medallion hangs, how far below them, and the gap between its marks (blocks). */
	static final double REACH = 0.9, DROP = 0.35, SPACING = 0.28;
	/** The medallion's size and the circle's at the feet (blocks). */
	static final float MEDALLION_SIZE = 0.45F, CIRCLE_SIZE = 1.6F, STROKE_WIDTH = 0.05F;
	/** The symbol's colour with no patron sworn. */
	static final int PLAIN = 0xFFE9A0;

	/**
	 * Each spell's pattern: four marks of the 3 by 3 board, numbered as the numpad (7 8 9 / 4 5 6 / 1 2 3), none twice. The Cleric's spells from
	 * {@link ClassCasters}; a spell with none here is cast from the symbol only.
	 */
	public static final Map<String, String> PATTERNS = Map.ofEntries(
		Map.entry("mending", "8526"),
		Map.entry("sanctuary", "7931"),
		Map.entry("restoration", "8426"),
		Map.entry(ClassCasters.REVIVE, "2587"),
		Map.entry("warding", "4796"),
		Map.entry("bulwark", "1463"),
		Map.entry("ward_line", "4563"),
		Map.entry("dispel", "7539"),
		Map.entry("counterspell", "9517"),
		Map.entry("purify_food", "8745"),
		Map.entry("create_food", "8965"),
		Map.entry("heroes_feast", "7139"),
		Map.entry("zone_of_truth", "7895"),
		Map.entry("augury", "1235"),
		Map.entry("find_magic", "5632"),
		Map.entry("identify", "5412"),
		Map.entry("locate_object", "7412"),
		Map.entry("feather_fall", "7863"),
		Map.entry("keeping", "1793"));

	/** The class's own workings, known on the board from the start (no tree perk needed there). */
	public static final String BLESS = "bless_the_band", SANCTIFY = "sanctify", TURN = "turn_undead";
	/**
	 * The prayers said only on the board: the workings in four marks, the great prayers ({@link GreatPrayers}) in five. No
	 * pattern here or in {@link #PATTERNS} begins another, so a match is always finished.
	 */
	public static final Map<String, String> PRAYERS = Map.of(
		BLESS, "8624",
		SANCTIFY, "1379",
		TURN, "2589",
		GreatPrayers.HALLOWED, "13974",
		GreatPrayers.LITANY, "45628",
		GreatPrayers.PILLAR, "85213",
		GreatPrayers.HAND, "36987");

	/** The client asks to lift the board or lower it. */
	public record Raise(boolean up) implements CustomPacketPayload {
		public static final Type<Raise> TYPE = new Type<>(JaymosKingdoms.id("medallion_raise"));
		public static final StreamCodec<ByteBuf, Raise> CODEC = ByteBufCodecs.BOOL.map(Raise::new, Raise::up);

		@Override
		public Type<Raise> type() {
			return TYPE;
		}
	}

	/** A numpad key on the raised board: the mark 1 to 9. */
	public record Mark(int mark) implements CustomPacketPayload {
		public static final Type<Mark> TYPE = new Type<>(JaymosKingdoms.id("medallion_mark"));
		public static final StreamCodec<ByteBuf, Mark> CODEC = ByteBufCodecs.VAR_INT.map(Mark::new, Mark::mark);

		@Override
		public Type<Mark> type() {
			return TYPE;
		}
	}

	/** The server's word on the board: up or down, and the marks laid so far. */
	public record State(boolean up, String marks) implements CustomPacketPayload {
		public static final Type<State> TYPE = new Type<>(JaymosKingdoms.id("medallion_state"));
		public static final StreamCodec<ByteBuf, State> CODEC = StreamCodec.composite(ByteBufCodecs.BOOL, State::up, ByteBufCodecs.STRING_UTF8, State::marks,
			State::new);

		@Override
		public Type<State> type() {
			return TYPE;
		}
	}

	private static final class Board {
		final ServerLevel level;
		String marks = "";
		long last;

		Board(ServerLevel level, long now) {
			this.level = level;
			this.last = now;
		}
	}

	private static final Map<UUID, Board> UP = new HashMap<>();

	private MedallionBoard() {
	}

	static void register() {
		PayloadTypeRegistry.serverboundPlay().register(Raise.TYPE, Raise.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(Mark.TYPE, Mark.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(State.TYPE, State.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Raise.TYPE, (payload, context) -> raise(context.player(), payload.up()));
		ServerPlayNetworking.registerGlobalReceiver(Mark.TYPE, (payload, context) -> mark(context.player(), payload.mark()));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> UP.remove(handler.player.getUUID()));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> UP.clear());
		ServerTickEvents.END_SERVER_TICK.register(MedallionBoard::tick);
		GreatPrayers.register();
	}

	/** What {@code player} may lay on the board: the workings, the great prayers their patron has granted, and the spells they know that have a pattern. */
	public static List<String> known(net.minecraft.world.entity.player.Player player) {
		List<String> out = new java.util.ArrayList<>(List.of(BLESS, SANCTIFY, TURN));
		for (String great : List.of(GreatPrayers.HALLOWED, GreatPrayers.LITANY, GreatPrayers.PILLAR, GreatPrayers.HAND)) {
			if (GreatPrayers.granted(player, great)) {
				out.add(great);
			}
		}
		Classes.of(player).spells().stream().filter(PATTERNS::containsKey).forEach(out::add);
		return out;
	}

	/** The pattern of a board entry ({@link #known}). */
	public static String pattern(String id) {
		String spell = PATTERNS.get(id);
		return spell != null ? spell : PRAYERS.get(id);
	}

	/** A board entry's name: a spell's, or a prayer's own. */
	public static Component name(String id) {
		return Component.translatable(PRAYERS.containsKey(id) ? "prayer.jaymoskingdoms." + id : "class.jaymoskingdoms.option." + id);
	}

	private static boolean may(ServerPlayer player) {
		return player.isAlive() && !player.isSpectator() && Classes.is(player, PlayerClass.CLERIC) && symbol(player) != null;
	}

	private static @Nullable ItemStack symbol(ServerPlayer player) {
		if (Clerics.focus(player, player.getMainHandItem())) {
			return player.getMainHandItem();
		}
		return Clerics.focus(player, player.getOffhandItem()) ? player.getOffhandItem() : null;
	}

	private static void raise(ServerPlayer player, boolean up) {
		if (!up) {
			lower(player);
			return;
		}
		if (!Classes.is(player, PlayerClass.CLERIC)) {
			send(player, false, "");
			return;
		}
		if (!may(player)) {
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.cleric.symbol"));
			send(player, false, "");
			return;
		}
		ServerLevel level = (ServerLevel) player.level();
		UP.put(player.getUUID(), new Board(level, level.getGameTime()));
		Casting.gesture(player, Casting.Gesture.RAISE, colour(player));
		draw(player, level);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.8F, 1.2F);
		send(player, true, "");
	}

	private static void lower(ServerPlayer player) {
		if (UP.remove(player.getUUID()) != null) {
			player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_STEP, SoundSource.PLAYERS, 0.6F, 0.9F);
		}
		send(player, false, "");
	}

	private static void mark(ServerPlayer player, int mark) {
		Board board = UP.get(player.getUUID());
		if (board == null || mark < 1 || mark > 9) {
			send(player, false, "");
			return;
		}
		if (!may(player) || board.level != player.level()) {
			lower(player);
			return;
		}
		ServerLevel level = board.level;
		board.last = level.getGameTime();
		int colour = colour(player);
		if (board.marks.indexOf((char) ('0' + mark)) >= 0) {
			// a mark laid twice is no pattern
			fizzle(player, level, board);
			return;
		}
		Vec3 at = node(player, mark);
		if (!board.marks.isEmpty()) {
			Fx.stroke(level, node(player, board.marks.charAt(board.marks.length() - 1) - '0'), at, STROKE_WIDTH, colour);
		}
		level.sendParticles(Fx.rune(colour, 0.6F), at.x, at.y, at.z, 6, 0.03, 0.03, 0.03, 0.0);
		Casting.gesture(player, Casting.Gesture.RAISE, colour);
		board.marks += mark;
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.7F,
			0.9F + 0.1F * board.marks.length());
		String done = null;
		boolean begun = false;
		for (String spell : known(player)) {
			String pattern = pattern(spell);
			if (pattern.equals(board.marks)) {
				done = spell;
			} else if (pattern.startsWith(board.marks)) {
				begun = true;
			}
		}
		if (done != null) {
			finish(player, level, done);
		} else if (!begun) {
			fizzle(player, level, board);
		} else {
			send(player, true, board.marks);
		}
	}

	/** A pattern finished: cast it (or, sneaking, bind it to the medallion), and the board comes down. */
	private static void finish(ServerPlayer player, ServerLevel level, String spell) {
		ItemStack symbol = symbol(player);
		UP.remove(player.getUUID());
		send(player, false, "");
		if (symbol == null) {
			return;
		}
		if (PRAYERS.containsKey(spell)) {
			say(player, level, spell);
			return;
		}
		if (player.isShiftKeyDown()) {
			Classes.Chosen c = Classes.of(player);
			player.setAttached(Classes.CLASS, new Classes.Chosen(c.cls(), c.option(), c.spells(), c.spells().indexOf(spell)));
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.medallion.bound", Spellcraft.spellName(spell)));
			Fx.ring(level, player.position().add(0.0, 0.1, 0.0), 1.0F, colour(player));
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.7F, 1.6F);
			return;
		}
		if (player.getCooldowns().isOnCooldown(symbol)) {
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.medallion.cooling"));
			return;
		}
		Fx.sigil(level, player.position().add(0.0, 0.05, 0.0), CIRCLE_SIZE * 1.4F, colour(player), 20);
		Spellcraft.castFocus(player, level, symbol, spell);
	}

	/** A board-only prayer: a working or a great prayer. Only a spell rests in the medallion, so sneaking binds none of these. */
	private static void say(ServerPlayer player, ServerLevel level, String id) {
		if (player.isShiftKeyDown()) {
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.medallion.no_bind", name(id)));
			return;
		}
		int colour = colour(player);
		Fx.sigil(level, player.position().add(0.0, 0.05, 0.0), CIRCLE_SIZE * 1.4F, colour, 20);
		switch (id) {
			case BLESS -> Clerics.boardBless(player, level);
			case SANCTIFY -> Litany.sanctify(player, true);
			case TURN -> Clerics.boardTurn(player, level);
			default -> {
				if (GreatPrayers.say(player, level, id, colour)) {
					Casting.gesture(player, Casting.Gesture.RAISE, colour);
				}
			}
		}
	}

	/** The marks begin no prayer the Cleric knows: they fade, and the board stays up, empty. */
	private static void fizzle(ServerPlayer player, ServerLevel level, Board board) {
		board.marks = "";
		Vec3 at = node(player, 5);
		level.sendParticles(Fx.mote(0x8A8A8A, 0.5F), at.x, at.y, at.z, 10, 0.2, 0.2, 0.2, 0.01);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.4F, 1.4F);
		player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.medallion.fizzle"));
		send(player, true, "");
	}

	private static void tick(MinecraftServer server) {
		if (UP.isEmpty()) {
			return;
		}
		Iterator<Map.Entry<UUID, Board>> it = UP.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<UUID, Board> e = it.next();
			ServerPlayer player = server.getPlayerList().getPlayer(e.getKey());
			Board board = e.getValue();
			if (player == null) {
				it.remove();
				continue;
			}
			long now = board.level.getGameTime();
			if (!may(player) || board.level != player.level() || now - board.last > IDLE_TICKS) {
				it.remove();
				send(player, false, "");
				continue;
			}
			if (now % REDRAW_TICKS == 0) {
				draw(player, board.level);
			}
		}
	}

	/** The raised medallion as others see it: the medallion before the chest and a circle at the feet (the arms go up on the raise and each mark). */
	private static void draw(ServerPlayer player, ServerLevel level) {
		int colour = colour(player);
		Fx.shape(level, 4, node(player, 5), facing(player), MEDALLION_SIZE, colour, REDRAW_TICKS + 2);
		Fx.sigil(level, player.position().add(0.0, 0.05, 0.0), CIRCLE_SIZE, colour, REDRAW_TICKS + 2);
	}

	/** Where mark {@code mark} (1 to 9) hangs in the air before {@code player}. */
	private static Vec3 node(ServerPlayer player, int mark) {
		Vec3 look = facing(player);
		Vec3 right = look.cross(new Vec3(0.0, 1.0, 0.0)).normalize();
		// as the numpad: 7 8 9 the top row
		int row = 2 - (mark - 1) / 3, col = (mark - 1) % 3;
		Vec3 centre = player.getEyePosition().add(look.scale(REACH)).add(0.0, -DROP, 0.0);
		return centre.add(right.scale((col - 1) * SPACING)).add(0.0, (1 - row) * SPACING, 0.0);
	}

	private static Vec3 facing(ServerPlayer player) {
		Vec3 look = player.getLookAngle().multiply(1.0, 0.0, 1.0);
		return look.lengthSqr() < 1.0E-4 ? new Vec3(0.0, 0.0, 1.0) : look.normalize();
	}

	private static int colour(ServerPlayer player) {
		Patron patron = Patrons.of(player);
		return patron == null ? PLAIN : patron.colour & 0xFFFFFF;
	}

	private static void send(ServerPlayer player, boolean up, String marks) {
		if (ServerPlayNetworking.canSend(player, State.TYPE)) {
			ServerPlayNetworking.send(player, new State(up, marks));
		}
	}
}
