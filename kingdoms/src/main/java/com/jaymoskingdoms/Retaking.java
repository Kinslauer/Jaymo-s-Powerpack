package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * The Retaking: the pack's one main story, for every player whatever their power, class or blood (Andy, 2026-11-11:
 * "large, relate to our lore, and accessible to all ... tied in with the advancements, giving you milestones to achieve
 * and furthering understanding of the world"). Five acts, each closed by a rung of {@link Knowledge} (the Goblin King,
 * an Ogre King, the Skeleton Lord, the Piglin King, the Wither). Inside an act its milestones can be done in any order.
 * <p>
 * The milestones are the advancements of the "Jaymo's Powerpack" tab ({@code jaymospowers:pack/*}, Powers' data), which
 * now reads as the story: the kings down the middle, each act's milestones hanging off the king before it. A milestone of
 * a later act only counts once the act before it is closed ({@link #open}); something done early has to be done again.
 * The journal (the character menu's Story tab) lists each act's milestones as plain goals and never says what they mean:
 * that comes from people, books and films ({@code docs/vault/Live/Lore.md}, "Rules for writing against this").
 * <p>
 * Milestones done together count for the party: a town errand handed in counts for every party member within
 * {@link #PARTY_RANGE} blocks; the kings and the Giants were already shared by those systems.
 */
public final class Retaking {
	/** How near (blocks) a party member must be for a shared milestone to count for them. */
	static final double PARTY_RANGE = 64.0;
	/** Town errands (quests handed in) for the Act I milestone. */
	public static final int ERRANDS = 3;
	/** The Aether; reaching it is an Act II milestone. */
	static final ResourceKey<Level> AETHER = ResourceKey.create(Registries.DIMENSION, Identifier.fromNamespaceAndPath("jaymospowers", "aether"));
	/** The Long Defence's codex id. */
	static final String LONG_DEFENCE_BOOK = JaymosKingdoms.id("the_long_defence").toString();

	/** The acts, in order. {@code rung}: the Knowledge level that closes it. */
	public enum Act {
		HEARTH("hearth"), OLD_STONES("old_stones"), LIGHTS("lights"), DARK_BELOW("dark_below"), LEGEND("legend");

		public final String key;

		Act(String key) {
			this.key = key;
		}

		/** The Knowledge level that closes this act (1 to 5). */
		public int rung() {
			return ordinal() + 1;
		}
	}

	/** One milestone: its advancement ({@code jaymospowers:pack/<name>}) and its act. */
	public record Milestone(String name, Act act) {
	}

	/**
	 * Every milestone, act by act, the king that closes each act last. Later acts gain theirs as they are built
	 * (the far worlds, the dark below, the Second Defence). The ford is {@link Ford}'s.
	 */
	public static final List<Milestone> MILESTONES = List.of(
		new Milestone("story", Act.HEARTH), new Milestone("awakened", Act.HEARTH), new Milestone("first_rank", Act.HEARTH),
		new Milestone("sworn", Act.HEARTH), new Milestone("errands", Act.HEARTH), new Milestone("long_defence", Act.HEARTH),
		new Milestone("goblin_king", Act.HEARTH),
		new Milestone("monolith", Act.OLD_STONES), new Milestone("giant", Act.OLD_STONES), new Milestone("aether", Act.OLD_STONES),
		new Milestone("ford", Act.OLD_STONES),
		new Milestone("giant_two", Act.OLD_STONES), new Milestone("giant_all", Act.OLD_STONES), new Milestone("ogre_king", Act.OLD_STONES),
		new Milestone("raiders", Act.LIGHTS), new Milestone("star", Act.LIGHTS), new Milestone("pod", Act.LIGHTS),
		new Milestone("maw", Act.LIGHTS), new Milestone("far_world", Act.LIGHTS), new Milestone("skeleton_lord", Act.LIGHTS),
		new Milestone("piglin_king", Act.DARK_BELOW),
		new Milestone("wither", Act.LEGEND));

	/**
	 * Milestones only Jaymo's Powers sees done (a raider band beaten, a fallen star broken, a pod flown, a far world freed or
	 * conquered). Powers leaves a tag, {@link #DEED} + name, which {@link #look} turns into the milestone if its act has begun
	 * and takes off either way.
	 */
	static final List<String> DEEDS = List.of("raiders", "star", "pod", "far_world");
	/** Powers' deed tag prefix ({@code PackAdvancements.DEED} there). */
	static final String DEED = "jaymospowers.retaking.";
	/** The Maw; setting foot in it is an Act III milestone. */
	static final ResourceKey<Level> MAW = ResourceKey.create(Registries.DIMENSION, Identifier.fromNamespaceAndPath("jaymospowers", "maw"));

	/** Per Knowledge level 1 to 5, its king's milestone. */
	static final String[] KINGS = {"", "goblin_king", "ogre_king", "skeleton_lord", "piglin_king", "wither"};

	/** Town errands handed in, counted toward {@link #ERRANDS}. Saved, kept through death. */
	public static final AttachmentType<Integer> ERRANDS_DONE = AttachmentRegistry.create(JaymosKingdoms.id("retaking_errands"),
		builder -> builder.persistent(Codec.INT).copyOnDeath());

	private Retaking() {
	}

	static void register() {
		JaymosKingdoms.LOGGER.debug("retaking attachment: {}", ERRANDS_DONE);
		PayloadTypeRegistry.clientboundPlay().register(State.TYPE, State.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(Ask.TYPE, Ask.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Ask.TYPE, (payload, context) -> send(context.player()));
	}

	/** The milestone called {@code name}, or null if it isn't part of the story. */
	public static @Nullable Milestone milestone(String name) {
		for (Milestone m : MILESTONES) {
			if (m.name().equals(name)) {
				return m;
			}
		}
		return null;
	}

	/** {@code player}'s current act: the first whose king hasn't fallen (the last once every king has). */
	public static Act act(ServerPlayer player) {
		return actAt(Knowledge.level(player));
	}

	/** The act a player with Knowledge level {@code level} is in. */
	public static Act actAt(int level) {
		Act[] all = Act.values();
		return all[Math.min(level, all.length - 1)];
	}

	/** Whether {@code name} can count for {@code player} now: not part of the story, or its act has begun. */
	public static boolean open(ServerPlayer player, String name) {
		Milestone m = milestone(name);
		return m == null || Knowledge.level(player) >= m.act().ordinal();
	}

	/** Looked at with the other pack advancements, every few seconds. */
	static void look(ServerPlayer player) {
		int level = Knowledge.level(player);
		for (int k = 1; k <= level; k++) {
			PackAdvancements.award(player, KINGS[k], "done");
		}
		if (player.getAttachedOrElse(ERRANDS_DONE, 0) >= ERRANDS) {
			PackAdvancements.award(player, "errands", "done");
		}
		if (LoreCodex.holds(player, LONG_DEFENCE_BOOK) || com.jaymoskingdoms.films.LoreFilms.seen(player, com.jaymoskingdoms.films.LoreFilms.LONG_DEFENCE)) {
			PackAdvancements.award(player, "long_defence", "done");
		}
		if (player.level().dimension() == AETHER) {
			PackAdvancements.award(player, "aether", "aether");
		}
		if (player.level().dimension() == MAW) {
			PackAdvancements.award(player, "maw", "done");
		}
		for (String deed : DEEDS) {
			if (player.removeTag(DEED + deed)) {
				PackAdvancements.award(player, deed, "done");
			}
		}
	}

	/** A town errand was handed in by {@code player}: it counts for them and their party near them. */
	static void errand(ServerPlayer player) {
		for (ServerPlayer p : Parties.members(player)) {
			if (p == player || p.level() == player.level() && p.distanceToSqr(player) <= PARTY_RANGE * PARTY_RANGE) {
				p.setAttached(ERRANDS_DONE, p.getAttachedOrElse(ERRANDS_DONE, 0) + 1);
			}
		}
	}

	// ---- the journal ------------------------------------------------------------------------------------------------

	private static void send(ServerPlayer player) {
		if (!ServerPlayNetworking.canSend(player, State.TYPE)) {
			return;
		}
		MinecraftServer server = player.level().getServer();
		List<String> done = new ArrayList<>();
		for (Milestone m : MILESTONES) {
			AdvancementHolder holder = PackAdvancements.get(server, m.name());
			if (holder != null && player.getAdvancements().getOrStartProgress(holder).isDone()) {
				done.add(m.name());
			}
		}
		ServerPlayNetworking.send(player, new State(Knowledge.level(player), player.getAttachedOrElse(ERRANDS_DONE, 0), done));
	}

	/** Server → player: their Knowledge level, errands handed in, and the milestones done. */
	public record State(int level, int errands, List<String> done) implements CustomPacketPayload {
		public static final Type<State> TYPE = new Type<>(JaymosKingdoms.id("retaking_state"));
		public static final StreamCodec<RegistryFriendlyByteBuf, State> CODEC = CustomPacketPayload.codec(State::write, State::read);

		private void write(RegistryFriendlyByteBuf buf) {
			buf.writeVarInt(level);
			buf.writeVarInt(errands);
			buf.writeVarInt(done.size());
			for (String s : done) {
				buf.writeUtf(s);
			}
		}

		private static State read(RegistryFriendlyByteBuf buf) {
			int level = buf.readVarInt(), errands = buf.readVarInt(), n = buf.readVarInt();
			List<String> done = new ArrayList<>();
			for (int i = 0; i < n; i++) {
				done.add(buf.readUtf());
			}
			return new State(level, errands, done);
		}

		@Override
		public Type<State> type() {
			return TYPE;
		}
	}

	/** Player → server: the Story tab opened. */
	public record Ask() implements CustomPacketPayload {
		public static final Type<Ask> TYPE = new Type<>(JaymosKingdoms.id("retaking_ask"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Ask> CODEC = StreamCodec.unit(new Ask());

		@Override
		public Type<Ask> type() {
			return TYPE;
		}
	}
}
