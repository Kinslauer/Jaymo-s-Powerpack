package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import org.jspecify.annotations.Nullable;

/**
 * The casebook: a page of the traveller's journal ({@link Journal}) where you pin what you've seen and mark faces. What you
 * notice lands in it by itself as a <b>clue</b> (blood on someone's boots, a neighbour gone missing, a cellar hatch that's
 * always shut: {@link #BOOTS}, {@link #MISSING}, {@link #CELLAR}); you decide which ones to pin. Every face you've looked at
 * for a moment is listed with the day you last saw it, and you mark each one as suspect, missing or dead, or clear it. The
 * book only holds what you put in it: it never marks anyone itself and never names who took anyone. Clues also come from
 * things true of the innocent (a butcher's boots, a badly hurt villager, a neighbour who simply left).
 */
public final class Casebook {
	public static final String BOOTS = "boots", MISSING = "missing", CELLAR = "cellar";
	/** Marks on a face. */
	public static final int NONE = 0, SUSPECT = 1, GONE = 2, DEAD = 3;
	/** The most clues and faces kept (the oldest unpinned, the oldest unmarked, go first). */
	static final int MAX_CLUES = 40, MAX_FACES = 40;
	/** How often (ticks) a player's surroundings are looked at, how near (blocks) a face must be to be taken in, how near a hatch must be to be noticed. */
	static final int EVERY = 20;
	static final double FACE_NEAR = 6.0, HATCH_NEAR = 3.5;

	public record Clue(String id, String kind, String arg, long day, boolean pinned) {
		public static final Codec<Clue> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.STRING.fieldOf("id").forGetter(Clue::id), Codec.STRING.fieldOf("kind").forGetter(Clue::kind),
			Codec.STRING.fieldOf("arg").forGetter(Clue::arg), Codec.LONG.fieldOf("day").forGetter(Clue::day),
			Codec.BOOL.fieldOf("pinned").forGetter(Clue::pinned)).apply(i, Clue::new));
	}

	public record Face(String uuid, String name, String trade, int mark, long seen) {
		public static final Codec<Face> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.STRING.fieldOf("uuid").forGetter(Face::uuid), Codec.STRING.fieldOf("name").forGetter(Face::name),
			Codec.STRING.fieldOf("trade").forGetter(Face::trade), Codec.INT.fieldOf("mark").forGetter(Face::mark),
			Codec.LONG.fieldOf("seen").forGetter(Face::seen)).apply(i, Face::new));
	}

	public record Page(List<Clue> clues, List<Face> faces) {
		public static final Page EMPTY = new Page(List.of(), List.of());
		public static final Codec<Page> CODEC = RecordCodecBuilder.create(i -> i.group(
			Clue.CODEC.listOf().fieldOf("clues").forGetter(Page::clues),
			Face.CODEC.listOf().fieldOf("faces").forGetter(Page::faces)).apply(i, Page::new));
	}

	/** Server to player: the page, sent with the journal and after each change. */
	public record Sync(Page page) implements CustomPacketPayload {
		public static final Type<Sync> TYPE = new Type<>(JaymosKingdoms.id("casebook_sync"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Sync> CODEC = CustomPacketPayload.codec(Sync::write, Sync::read);

		private void write(RegistryFriendlyByteBuf buf) {
			buf.writeVarInt(page.clues().size());
			for (Clue c : page.clues()) {
				buf.writeUtf(c.id());
				buf.writeUtf(c.kind());
				buf.writeUtf(c.arg());
				buf.writeVarLong(c.day());
				buf.writeBoolean(c.pinned());
			}
			buf.writeVarInt(page.faces().size());
			for (Face f : page.faces()) {
				buf.writeUtf(f.uuid());
				buf.writeUtf(f.name());
				buf.writeUtf(f.trade());
				buf.writeVarInt(f.mark());
				buf.writeVarLong(f.seen());
			}
		}

		private static Sync read(RegistryFriendlyByteBuf buf) {
			List<Clue> clues = new ArrayList<>();
			for (int n = buf.readVarInt(); n > 0; n--) {
				clues.add(new Clue(buf.readUtf(), buf.readUtf(), buf.readUtf(), buf.readVarLong(), buf.readBoolean()));
			}
			List<Face> faces = new ArrayList<>();
			for (int n = buf.readVarInt(); n > 0; n--) {
				faces.add(new Face(buf.readUtf(), buf.readUtf(), buf.readUtf(), buf.readVarInt(), buf.readVarLong()));
			}
			return new Sync(new Page(clues, faces));
		}

		@Override
		public Type<Sync> type() {
			return TYPE;
		}
	}

	/** Player to server: pin or unpin clue {@code id} (face false), or mark face {@code id} with {@code value} (face true). */
	public record Act(boolean face, String id, int value) implements CustomPacketPayload {
		public static final Type<Act> TYPE = new Type<>(JaymosKingdoms.id("casebook_act"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Act> CODEC = CustomPacketPayload.codec(
			(a, buf) -> {
				buf.writeBoolean(a.face);
				buf.writeUtf(a.id);
				buf.writeVarInt(a.value);
			}, buf -> new Act(buf.readBoolean(), buf.readUtf(), buf.readVarInt()));

		@Override
		public Type<Act> type() {
			return TYPE;
		}
	}

	static final AttachmentType<Page> CASES = AttachmentRegistry.create(JaymosKingdoms.id("casebook"),
		builder -> builder.persistent(Page.CODEC).copyOnDeath());

	private Casebook() {
	}

	static void register() {
		PayloadTypeRegistry.clientboundPlay().register(Sync.TYPE, Sync.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(Act.TYPE, Act.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Act.TYPE, (payload, context) -> context.server().execute(() -> act(context.player(), payload)));
		ServerTickEvents.END_SERVER_TICK.register(Casebook::tick);
	}

	static Page page(ServerPlayer player) {
		return player.getAttachedOrElse(CASES, Page.EMPTY);
	}

	static void send(ServerPlayer player) {
		if (ServerPlayNetworking.canSend(player, Sync.TYPE)) {
			ServerPlayNetworking.send(player, new Sync(page(player)));
		}
	}

	private static void act(ServerPlayer player, Act a) {
		Page page = page(player);
		if (a.face()) {
			List<Face> faces = new ArrayList<>();
			for (Face f : page.faces()) {
				faces.add(f.uuid().equals(a.id()) ? new Face(f.uuid(), f.name(), f.trade(), Math.max(NONE, Math.min(DEAD, a.value())), f.seen()) : f);
			}
			player.setAttached(CASES, new Page(page.clues(), faces));
		} else {
			List<Clue> clues = new ArrayList<>();
			for (Clue c : page.clues()) {
				clues.add(c.id().equals(a.id()) ? new Clue(c.id(), c.kind(), c.arg(), c.day(), !c.pinned()) : c);
			}
			player.setAttached(CASES, new Page(clues, page.faces()));
		}
		send(player);
	}

	/** {@code player} noticed a clue of {@code kind} about {@code arg} (a name, or ""). Written once. */
	static void note(ServerPlayer player, String kind, String arg, @Nullable LivingEntity who) {
		String id = kind + ":" + arg;
		Page page = page(player);
		for (Clue c : page.clues()) {
			if (c.id().equals(id)) {
				return;
			}
		}
		List<Clue> clues = new ArrayList<>(page.clues());
		clues.addFirst(new Clue(id, kind, arg, Math.floorDiv(player.level().getOverworldClockTime(), 24000L), false));
		while (clues.size() > MAX_CLUES) {
			int drop = clues.size() - 1;
			while (drop > 0 && clues.get(drop).pinned()) {
				drop--;
			}
			clues.remove(drop);
		}
		player.setAttached(CASES, new Page(clues, page.faces()));
		send(player);
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % EVERY != 0) {
			return;
		}
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			if (p.isSpectator() || !p.isAlive() || !Journal.carries(p)) {
				continue;
			}
			for (Villager v : p.level().getEntitiesOfClass(Villager.class, p.getBoundingBox().inflate(FACE_NEAR), v -> v.isAlive() && !v.isBaby())) {
				if (looking(p, v)) {
					see(p, v);
					if (v.distanceTo(p) <= Takers.BOOTS_SIGHT && Takers.bloodyBoots(v)) {
						note(p, BOOTS, v.getName().getString(), v);
					}
				}
			}
			if (Takers.nearHatch(p, HATCH_NEAR)) {
				note(p, CELLAR, "", null);
			}
		}
	}

	private static boolean looking(ServerPlayer p, LivingEntity e) {
		var to = e.getEyePosition().subtract(p.getEyePosition()).normalize();
		return p.getLookAngle().dot(to) > 0.6 && p.hasLineOfSight(e);
	}

	/** A face taken in: listed (or its day updated). */
	private static void see(ServerPlayer p, Villager v) {
		Page page = page(p);
		long day = Math.floorDiv(p.level().getOverworldClockTime(), 24000L);
		String id = v.getUUID().toString();
		List<Face> faces = new ArrayList<>(page.faces());
		for (int i = 0; i < faces.size(); i++) {
			Face f = faces.get(i);
			if (f.uuid().equals(id)) {
				if (f.seen() != day) {
					faces.set(i, new Face(f.uuid(), f.name(), f.trade(), f.mark(), day));
					p.setAttached(CASES, new Page(page.clues(), faces));
				}
				return;
			}
		}
		String trade = net.minecraft.core.registries.BuiltInRegistries.VILLAGER_PROFESSION.getKey(v.getVillagerData().profession().value()).getPath();
		faces.addFirst(new Face(id, v.getName().getString(), trade, NONE, day));
		while (faces.size() > MAX_FACES) {
			int drop = faces.size() - 1;
			while (drop > 0 && faces.get(drop).mark() != NONE) {
				drop--;
			}
			faces.remove(drop);
		}
		p.setAttached(CASES, new Page(page.clues(), faces));
	}
}
