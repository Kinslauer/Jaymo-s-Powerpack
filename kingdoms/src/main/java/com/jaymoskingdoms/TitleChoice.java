package com.jaymoskingdoms;

import com.jaymoskingdoms.Careers.Job;
import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/**
 * The title you wear: of the titles you have earned (a pillar's, from {@link Renown}; a job's, from {@link Careers}; the one a
 * finished aim made, from {@link LifeAims}), which one shows to others, under your name and in the tab list. Left alone
 * ({@link #AUTO}) you wear what you always did: your alignment's and your best job's. Picking one shows only it; picking
 * {@link #NONE} shows none. Picked on the H menu's Titles page; saved and kept through death.
 */
public final class TitleChoice {
	/** The pick when none was made, and the pick of no title. */
	public static final String AUTO = "", NONE = "none", AIM = "aim", SKILL = "skill:", JOB = "job:";

	/** What you wear, an id from {@link #earned} (or {@link #AUTO}, {@link #NONE}). */
	public static final AttachmentType<String> WORN = AttachmentRegistry.create(JaymosKingdoms.id("worn_title"),
		builder -> builder.persistent(Codec.STRING).copyOnDeath());

	/** One title you have earned: its id, its words, and its colour (0xRRGGBB). */
	public record Entry(String id, Component name, int color) {
	}

	/** Player → server: ask for your titles. */
	public record Ask() implements CustomPacketPayload {
		public static final Type<Ask> TYPE = new Type<>(JaymosKingdoms.id("titles_ask"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Ask> CODEC = StreamCodec.unit(new Ask());

		@Override
		public Type<Ask> type() {
			return TYPE;
		}
	}

	/** Player → server: wear the title with this id ({@link #AUTO}: as before, {@link #NONE}: none). */
	public record Pick(String id) implements CustomPacketPayload {
		public static final Type<Pick> TYPE = new Type<>(JaymosKingdoms.id("titles_pick"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Pick> CODEC = CustomPacketPayload.codec(
			(p, buf) -> buf.writeUtf(p.id, 64), buf -> new Pick(buf.readUtf(64)));

		@Override
		public Type<Pick> type() {
			return TYPE;
		}
	}

	/** Server → player: the titles you have earned and the one you wear. */
	public record Info(String worn, List<Entry> titles) implements CustomPacketPayload {
		public static final Type<Info> TYPE = new Type<>(JaymosKingdoms.id("titles_info"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Info> CODEC = CustomPacketPayload.codec(
			(p, buf) -> {
				buf.writeUtf(p.worn, 64);
				buf.writeVarInt(p.titles.size());
				for (Entry e : p.titles) {
					buf.writeUtf(e.id, 64);
					ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, e.name);
					buf.writeVarInt(e.color);
				}
			},
			buf -> {
				String worn = buf.readUtf(64);
				int n = buf.readVarInt();
				List<Entry> titles = new ArrayList<>();
				for (int i = 0; i < n; i++) {
					titles.add(new Entry(buf.readUtf(64), ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf), buf.readVarInt()));
				}
				return new Info(worn, List.copyOf(titles));
			});

		@Override
		public Type<Info> type() {
			return TYPE;
		}
	}

	/** On the client: the last titles sent, or null before any. */
	public static @Nullable Info info;

	private TitleChoice() {
	}

	static void register() {
		PayloadTypeRegistry.serverboundPlay().register(Ask.TYPE, Ask.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(Pick.TYPE, Pick.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(Info.TYPE, Info.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Ask.TYPE, (payload, context) -> send(context.player()));
		ServerPlayNetworking.registerGlobalReceiver(Pick.TYPE, (payload, context) -> pick(context.player(), payload.id()));
	}

	/** The titles {@code player} has earned: a pillar's from level {@link Renown#ALIGN_LEVEL}, each job they have worked, a finished aim's. */
	public static List<Entry> earned(Player player) {
		List<Entry> out = new ArrayList<>();
		Skills.Progress p = Skills.of(player);
		for (Skills.Pillar pillar : Skills.Pillar.values()) {
			if (pillar.web() && p.level(pillar) >= Renown.ALIGN_LEVEL) {
				boolean master = p.level(pillar) >= pillar.maxLevel;
				out.add(new Entry(SKILL + pillar.key(), Component.translatable("title.jaymoskingdoms." + pillar.key() + (master ? ".master" : "")),
					pillar.color & 0xFFFFFF));
			}
		}
		Careers.Career career = Careers.of(player);
		for (Job job : Job.values()) {
			if (career.xpIn(job.key) > 0) {
				out.add(new Entry(JOB + job.key, Careers.title(job, career.rank(job.key)), 0xC8A060));
			}
		}
		Component aim = LifeAims.title(player);
		if (aim != null) {
			out.add(new Entry(AIM, aim, 0xE8C860));
		}
		return out;
	}

	/** The id {@code player} wears, if it is still one they have earned; else {@link #AUTO}. */
	public static String worn(Player player) {
		String id = player.getAttachedOrElse(WORN, AUTO);
		if (id.isEmpty() || id.equals(NONE)) {
			return id;
		}
		for (Entry e : earned(player)) {
			if (e.id().equals(id)) {
				return id;
			}
		}
		return AUTO;
	}

	/** The title code (as {@link Renown#TITLE}) {@code player}'s alignment shows over their head, by what they wear. */
	static int renownCode(Player player) {
		String id = worn(player);
		if (id.isEmpty()) {
			return Renown.title(Skills.of(player));
		}
		if (id.startsWith(SKILL)) {
			Skills.Progress p = Skills.of(player);
			for (Skills.Pillar pillar : Skills.Pillar.values()) {
				if (id.equals(SKILL + pillar.key())) {
					return 1 + pillar.ordinal() * 2 + (p.level(pillar) >= pillar.maxLevel ? 1 : 0);
				}
			}
		}
		return 0;
	}

	/** The job and aim title shown with {@code player}'s name (see {@link Careers#refreshTitles}), by what they wear. */
	static @Nullable Component shown(Player player) {
		String id = worn(player);
		if (id.isEmpty()) {
			return Careers.title(player);
		}
		if (id.startsWith(JOB)) {
			Job job = Job.of(id.substring(JOB.length()));
			return job == null ? null : Careers.title(job, Careers.of(player).rank(job.key));
		}
		return id.equals(AIM) ? LifeAims.title(player) : null;
	}

	private static void send(ServerPlayer player) {
		if (ServerPlayNetworking.canSend(player, Info.TYPE)) {
			ServerPlayNetworking.send(player, new Info(worn(player), List.copyOf(earned(player))));
		}
	}

	private static void pick(ServerPlayer player, String id) {
		boolean ok = id.isEmpty() || id.equals(NONE);
		for (Entry e : earned(player)) {
			ok |= e.id().equals(id);
		}
		if (!ok) {
			return;
		}
		player.setAttached(WORN, id);
		Renown.update(player);
		Careers.refreshTitles(player.level().getServer());
		send(player);
	}
}
