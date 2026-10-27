package com.jaymospowers.power.zfighter;

import com.jaymospowers.JaymosPowers;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/**
 * (0.17.60, Andy's NOEA picks) The packet that carries a tournament's bracket to the bracket screen
 * ({@code ZFighterBracketScreen}). One server-to-client packet: the whole board each time (a few hundred bytes), sent
 * when the screen is to open ({@code open}) and, without opening anything, to everyone near the ring whenever it changes
 * (the screen redraws if it is up). Both kinds of tournament use it: a bracket fills the {@link Board#matches}; the
 * last-one-standing fight ({@link ZFighterBattleRoyale}) fills the {@link Board#fighters}. A name that starts with
 * {@link #KEY} is a translation key the client looks up (a champion's), so the server needs no language files.
 */
public final class ZFighterBracketNet {
	/** {@link Board#phase}: signing up, fighting, over. */
	public static final int SIGN_UP = 0, FIGHTING = 1, OVER = 2;
	/** {@link Match#winner}: undecided, the first, the second. */
	public static final int UNDECIDED = 0, FIRST = 1, SECOND = 2;
	/** Marks a name that is a translation key. */
	public static final String KEY = "@";

	private ZFighterBracketNet() {
	}

	/** One bout of the bracket: its round (from 0), both names ("" for a place not yet filled), who won, and whether it is on now. */
	public record Match(int round, String a, String b, boolean aPlayer, boolean bPlayer, int winner, boolean live) {
	}

	/** One fighter of a last-one-standing fight: {@code place} 0 while still in (1 is the winner), {@code how} why he is out, {@code by} who did it. */
	public record Fighter(String name, boolean player, int place, String how, String by) {
	}

	/**
	 * The board. {@code any} is false when no tournament is on and none was lately. {@code ticks} is what is left of the
	 * sign-up (or of the fight); {@code rounds} how many rounds the bracket has; {@code champion} the winner once over.
	 */
	public record Board(boolean any, boolean royale, int phase, String kingdom, int round, int rounds, int ticks, String champion,
		List<Match> matches, List<Fighter> fighters) {
		public static final Board NONE = new Board(false, false, OVER, "", 0, 0, 0, "", List.of(), List.of());
	}

	/** Server → client: the board, and whether to open the screen for it. */
	public record Show(boolean open, Board board) implements CustomPacketPayload {
		public static final Type<Show> TYPE = new Type<>(JaymosPowers.id("zfighter_bracket"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Show> CODEC = CustomPacketPayload.codec(ZFighterBracketNet::write, ZFighterBracketNet::read);

		@Override
		public Type<Show> type() {
			return TYPE;
		}
	}

	private static void write(Show p, RegistryFriendlyByteBuf buf) {
		Board b = p.board();
		buf.writeBoolean(p.open());
		buf.writeBoolean(b.any());
		buf.writeBoolean(b.royale());
		buf.writeByte(b.phase());
		buf.writeUtf(b.kingdom(), 64);
		buf.writeByte(b.round());
		buf.writeByte(b.rounds());
		buf.writeVarInt(b.ticks());
		buf.writeUtf(b.champion(), 96);
		buf.writeVarInt(b.matches().size());
		for (Match m : b.matches()) {
			buf.writeByte(m.round());
			buf.writeUtf(m.a(), 96);
			buf.writeUtf(m.b(), 96);
			buf.writeBoolean(m.aPlayer());
			buf.writeBoolean(m.bPlayer());
			buf.writeByte(m.winner());
			buf.writeBoolean(m.live());
		}
		buf.writeVarInt(b.fighters().size());
		for (Fighter f : b.fighters()) {
			buf.writeUtf(f.name(), 96);
			buf.writeBoolean(f.player());
			buf.writeByte(f.place());
			buf.writeUtf(f.how(), 32);
			buf.writeUtf(f.by(), 96);
		}
	}

	private static Show read(RegistryFriendlyByteBuf buf) {
		boolean open = buf.readBoolean();
		boolean any = buf.readBoolean();
		boolean royale = buf.readBoolean();
		int phase = buf.readByte();
		String kingdom = buf.readUtf(64);
		int round = buf.readByte();
		int rounds = buf.readByte();
		int ticks = buf.readVarInt();
		String champion = buf.readUtf(96);
		int count = Math.min(buf.readVarInt(), 64);
		List<Match> matches = new ArrayList<>(count);
		for (int i = 0; i < count; i++) {
			matches.add(new Match(buf.readByte(), buf.readUtf(96), buf.readUtf(96), buf.readBoolean(), buf.readBoolean(), buf.readByte(), buf.readBoolean()));
		}
		count = Math.min(buf.readVarInt(), 64);
		List<Fighter> fighters = new ArrayList<>(count);
		for (int i = 0; i < count; i++) {
			fighters.add(new Fighter(buf.readUtf(96), buf.readBoolean(), buf.readByte(), buf.readUtf(32), buf.readUtf(96)));
		}
		return new Show(open, new Board(any, royale, phase, kingdom, round, rounds, ticks, champion, matches, fighters));
	}

	static void register() {
		PayloadTypeRegistry.clientboundPlay().register(Show.TYPE, Show.CODEC);
	}

	/** Opens the bracket screen for {@code player} with {@code board}. */
	static void open(ServerPlayer player, Board board) {
		ServerPlayNetworking.send(player, new Show(true, board));
	}

	/** Updates the screen if the player has it up; opens nothing. */
	static void update(ServerPlayer player, Board board) {
		ServerPlayNetworking.send(player, new Show(false, board));
	}

	/** A name for the wire: a translatable one as its key (marked), anything else as its text. */
	static String name(Component name) {
		return name.getContents() instanceof TranslatableContents translatable ? KEY + translatable.getKey() : name.getString();
	}
}
