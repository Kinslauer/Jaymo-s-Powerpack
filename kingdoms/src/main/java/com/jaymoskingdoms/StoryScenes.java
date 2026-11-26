package com.jaymoskingdoms;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Story scenes. Andy (2026-11-26), on Endless Lantern Corps (All Rights Reserved: the idea only, none of its code or words):
 * "the lantern ring acquisition is a great way that i'd like our story missions and quests to flow." A story scene is a
 * talk staged as a film, played where the story happens: the letterbox comes in, the HUD clears and the camera cuts
 * between shots (a wide one of the place, over your shoulder on whoever speaks, on your face for a hard question, round
 * the two of you for a rite). Whoever you meet speaks in the box on the left, and you answer from the right:
 * <ul>
 * <li><b>Replies</b>: a few things to say, each leading on (ask about what you want to know, or get on with it).</li>
 * <li><b>A question</b> with a yes or a no that decides the story ({@code ask}): said aloud into the microphone, clicked, or
 * Y / N.</li>
 * <li><b>A rite</b> ({@code rite}): the words are given a line at a time and said back, aloud or by holding the right
 * mouse button over each line, before the time runs out; you kneel for it.</li>
 * <li><b>What you were given</b> ({@code outcome}): a scene's own code runs as a node opens and may hand back what it
 * gave, shown as a list over the last line.</li>
 * </ul>
 * Scenes are data, {@code data/<ns>/story_scenes/<id>.json} (see {@link #read}), so a mission's talk is written there and
 * only its hooks ({@link #outcome}) are code. Nothing can hurt you while one plays, and it ends if you're moved away,
 * die, log out or leave it with Esc (a scene left unfinished can be met again). The first is the Warlock's midnight
 * bargain ({@link MidnightBargain}).
 */
public final class StoryScenes {
	/** Moved this far from where it began, the scene is over (blocks). */
	static final double LEAVE_REACH = 6.0;
	/** No answer from the player in this long and the scene is over (ticks). */
	static final int QUIET_TICKS = 20 * 60 * 4;
	/** What the player's answer is (Answer#choice) when it isn't a reply's number. */
	public static final int GO_ON = -1, LEAVE = -2, YES = 0, NO = 1, SAID = 0, LATE = 1;

	/** One thing to say back, and the node it leads to. */
	record Reply(String text, String to) {
	}

	/** A yes-or-no question and where each answer leads. */
	record Ask(String question, String yes, String no) {
	}

	/** Words said back a line at a time within {@code seconds}, then {@code to}; too slow, {@code fail}. */
	record Rite(List<String> words, int seconds, String to, String fail) {
	}

	/** One beat of a scene. {@code say} is a lang key (empty: nothing said); {@code next} for a line that just goes on. */
	record Node(String id, String shot, String say, List<Reply> replies, @Nullable Ask ask, @Nullable Rite rite, String next, String outcome, boolean end) {
	}

	/** A whole scene: who speaks (a lang key), in what colour, where it starts. */
	record Scene(String id, String speaker, int color, String start, Map<String, Node> nodes) {
	}

	/** A scene's own code, named by a node's {@code outcome}; what it returns is shown as what the player received. */
	public interface Outcome {
		List<Component> run(ServerPlayer player, Playing playing);
	}

	/** A scene being played for one player: where, which node, since when nothing has been heard from them. */
	public static final class Playing {
		final Scene scene;
		final Vec3 anchor, began;
		String node;
		long heardAt;

		Playing(Scene scene, Vec3 anchor, Vec3 began, long now) {
			this.scene = scene;
			this.anchor = anchor;
			this.began = began;
			this.node = scene.start();
			this.heardAt = now;
		}

		public String id() {
			return scene.id();
		}

		public Vec3 anchor() {
			return anchor;
		}
	}

	/**
	 * Server → client: the scene's next beat. {@code kind}: {@link #LINE} (a line, then on), {@link #REPLIES}, {@link #ASK},
	 * {@link #RITE}, {@link #LAST} (a last line, then it's over) or {@link #END} (over now). {@code anchor} is what the
	 * camera frames besides you.
	 */
	public record Shot(String scene, String node, int kind, String shot, Component speaker, int color, Component line, List<Component> replies,
		Component question, List<Component> words, int seconds, List<Component> gifts, Vec3 anchor) implements CustomPacketPayload {
		public static final int LINE = 0, REPLIES = 1, ASK = 2, RITE = 3, LAST = 4, END = 5;
		public static final Type<Shot> TYPE = new Type<>(JaymosKingdoms.id("story_scene_shot"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Shot> CODEC = CustomPacketPayload.codec(
			(p, buf) -> {
				buf.writeUtf(p.scene);
				buf.writeUtf(p.node);
				buf.writeVarInt(p.kind);
				buf.writeUtf(p.shot);
				ComponentSerialization.STREAM_CODEC.encode(buf, p.speaker);
				buf.writeInt(p.color);
				ComponentSerialization.STREAM_CODEC.encode(buf, p.line);
				writeAll(buf, p.replies);
				ComponentSerialization.STREAM_CODEC.encode(buf, p.question);
				writeAll(buf, p.words);
				buf.writeVarInt(p.seconds);
				writeAll(buf, p.gifts);
				Vec3.STREAM_CODEC.encode(buf, p.anchor);
			},
			buf -> new Shot(buf.readUtf(), buf.readUtf(), buf.readVarInt(), buf.readUtf(), ComponentSerialization.STREAM_CODEC.decode(buf), buf.readInt(),
				ComponentSerialization.STREAM_CODEC.decode(buf), readAll(buf), ComponentSerialization.STREAM_CODEC.decode(buf), readAll(buf), buf.readVarInt(),
				readAll(buf), Vec3.STREAM_CODEC.decode(buf)));

		static Shot end(String scene) {
			return new Shot(scene, "", END, "", Component.empty(), 0, Component.empty(), List.of(), Component.empty(), List.of(), 0, List.of(), Vec3.ZERO);
		}

		@Override
		public Type<Shot> type() {
			return TYPE;
		}
	}

	/** Client → server: the player's answer to {@code node}: a reply's number, or {@link #GO_ON}, {@link #LEAVE}, {@link #YES} / {@link #NO}, {@link #SAID} / {@link #LATE}. */
	public record Answer(String scene, String node, int choice) implements CustomPacketPayload {
		public static final Type<Answer> TYPE = new Type<>(JaymosKingdoms.id("story_scene_answer"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Answer> CODEC = CustomPacketPayload.codec(
			(p, buf) -> {
				buf.writeUtf(p.scene);
				buf.writeUtf(p.node);
				buf.writeVarInt(p.choice);
			},
			buf -> new Answer(buf.readUtf(), buf.readUtf(), buf.readVarInt()));

		@Override
		public Type<Answer> type() {
			return TYPE;
		}
	}

	private static Map<String, Scene> all = Map.of();
	private static final Map<String, Outcome> OUTCOMES = new HashMap<>();
	private static final Map<UUID, Playing> PLAYING = new HashMap<>();

	private StoryScenes() {
	}

	static void register() {
		PayloadTypeRegistry.clientboundPlay().register(Shot.TYPE, Shot.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(Answer.TYPE, Answer.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Answer.TYPE, (payload, context) -> answer(context.player(), payload));
		ResourceLoader.get(PackType.SERVER_DATA).registerReloadListener(JaymosKingdoms.id("story_scenes"),
			new SimpleJsonResourceReloadListener<>(ExtraCodecs.JSON, FileToIdConverter.json("story_scenes")) {
				@Override
				protected void apply(Map<Identifier, JsonElement> loaded, ResourceManager manager, ProfilerFiller profiler) {
					Map<String, Scene> read = new LinkedHashMap<>();
					loaded.forEach((file, json) -> {
						Scene scene = read(file, json);
						if (scene != null) {
							read.put(scene.id(), scene);
						}
					});
					all = read;
				}
			});
		// nothing hurts you while a scene plays (Andy's rule for our films)
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> !(entity instanceof ServerPlayer p && PLAYING.containsKey(p.getUUID())));
		ServerTickEvents.END_SERVER_TICK.register(StoryScenes::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> PLAYING.remove(handler.player.getUUID()));
	}

	/** Names {@code name} as a scene hook a node's {@code outcome} may call. */
	public static void outcome(String name, Outcome outcome) {
		OUTCOMES.put(name, outcome);
	}

	/** Whether {@code player} is in a scene now. */
	public static boolean playing(Player player) {
		return PLAYING.containsKey(player.getUUID());
	}

	/** Whether the scene {@code id} exists (its file was read). */
	public static boolean exists(String id) {
		return all.containsKey(id);
	}

	/** Starts the scene {@code id} for {@code player}, framed on {@code anchor}; false if there's no such scene, they're in one, or their game can't show it. */
	public static boolean play(ServerPlayer player, String id, Vec3 anchor) {
		Scene scene = all.get(id);
		if (scene == null || playing(player) || !ServerPlayNetworking.canSend(player, Shot.TYPE)) {
			return false;
		}
		Playing playing = new Playing(scene, anchor, player.position(), player.level().getGameTime());
		PLAYING.put(player.getUUID(), playing);
		enter(player, playing, scene.start());
		return true;
	}

	/** Ends {@code player}'s scene now (their game is told). */
	public static void stop(ServerPlayer player) {
		Playing playing = PLAYING.remove(player.getUUID());
		if (playing != null && ServerPlayNetworking.canSend(player, Shot.TYPE)) {
			ServerPlayNetworking.send(player, Shot.end(playing.id()));
		}
	}

	private static void enter(ServerPlayer player, Playing playing, String id) {
		Node node = playing.scene.nodes().get(id);
		if (node == null) {
			JaymosKingdoms.LOGGER.warn("Story scene {} has no node {}; ended", playing.id(), id);
			stop(player);
			return;
		}
		playing.node = id;
		playing.heardAt = player.level().getGameTime();
		List<Component> gifts = List.of();
		if (!node.outcome().isEmpty()) {
			Outcome outcome = OUTCOMES.get(node.outcome());
			if (outcome == null) {
				JaymosKingdoms.LOGGER.warn("Story scene {} calls {}, which nothing answers", playing.id(), node.outcome());
			} else {
				gifts = outcome.run(player, playing);
			}
			if (!PLAYING.containsKey(player.getUUID())) {
				return; // the hook ended it
			}
		}
		int kind = node.rite() != null ? Shot.RITE : node.ask() != null ? Shot.ASK : !node.replies().isEmpty() ? Shot.REPLIES
			: node.end() || node.next().isEmpty() ? Shot.LAST : Shot.LINE;
		List<Component> replies = new ArrayList<>();
		for (Reply r : node.replies()) {
			replies.add(Component.translatable(r.text()));
		}
		List<Component> words = new ArrayList<>();
		if (node.rite() != null) {
			for (String w : node.rite().words()) {
				words.add(Component.translatable(w));
			}
		}
		Scene scene = playing.scene;
		ServerPlayNetworking.send(player, new Shot(scene.id(), id, kind, node.shot(), Component.translatable(scene.speaker()), scene.color(),
			node.say().isEmpty() ? Component.empty() : Component.translatable(node.say()), replies,
			node.ask() != null ? Component.translatable(node.ask().question()) : Component.empty(), words,
			node.rite() != null ? node.rite().seconds() : 0, gifts, playing.anchor));
		if (kind == Shot.LAST) {
			// over on the server now; the client plays the last line out and lets go
			PLAYING.remove(player.getUUID());
		}
	}

	private static void answer(ServerPlayer player, Answer answer) {
		Playing playing = PLAYING.get(player.getUUID());
		if (playing == null || !playing.id().equals(answer.scene()) || !playing.node.equals(answer.node())) {
			return;
		}
		if (answer.choice() == LEAVE) {
			PLAYING.remove(player.getUUID());
			return;
		}
		Node node = playing.scene.nodes().get(playing.node);
		String to;
		if (node.rite() != null) {
			to = answer.choice() == SAID ? node.rite().to() : node.rite().fail();
		} else if (node.ask() != null) {
			to = answer.choice() == YES ? node.ask().yes() : answer.choice() == NO ? node.ask().no() : "";
		} else if (!node.replies().isEmpty()) {
			to = answer.choice() >= 0 && answer.choice() < node.replies().size() ? node.replies().get(answer.choice()).to() : "";
		} else {
			to = answer.choice() == GO_ON ? node.next() : "";
		}
		if (!to.isEmpty()) {
			enter(player, playing, to);
		}
	}

	private static void tick(MinecraftServer server) {
		if (PLAYING.isEmpty() || server.getTickCount() % 10 != 0) {
			return;
		}
		for (UUID id : List.copyOf(PLAYING.keySet())) {
			ServerPlayer player = server.getPlayerList().getPlayer(id);
			Playing playing = PLAYING.get(id);
			if (player == null) {
				PLAYING.remove(id);
			} else if (!player.isAlive() || player.position().distanceTo(playing.began) > LEAVE_REACH
				|| player.level().getGameTime() - playing.heardAt > QUIET_TICKS) {
				stop(player);
			}
		}
	}

	// ---- reading ------------------------------------------------------------------------------------------------------

	/**
	 * A scene file: {@code speaker} (lang key), {@code color} (hex RGB), {@code start} (a node id) and {@code nodes}, each
	 * with a {@code shot} (wide, shoulder, face, orbit, low, high), {@code say} (lang key), and one of {@code replies}
	 * ([{text, to}]), {@code ask} ({question, yes, no}), {@code rite} ({words: [keys], seconds, to, fail}), {@code next},
	 * or {@code end}; {@code outcome} names a hook ({@link #outcome}) run as the node opens.
	 */
	static @Nullable Scene read(Identifier file, JsonElement json) {
		String id = file.getNamespace().equals(JaymosKingdoms.MOD_ID) ? file.getPath() : file.toString();
		if (!json.isJsonObject()) {
			JaymosKingdoms.LOGGER.warn("Story scene {} is not a JSON object; left out", id);
			return null;
		}
		JsonObject o = json.getAsJsonObject();
		Map<String, Node> nodes = new LinkedHashMap<>();
		JsonObject in = GsonHelper.getAsJsonObject(o, "nodes", new JsonObject());
		for (Map.Entry<String, JsonElement> e : in.entrySet()) {
			if (!e.getValue().isJsonObject()) {
				continue;
			}
			JsonObject n = e.getValue().getAsJsonObject();
			List<Reply> replies = new ArrayList<>();
			for (JsonElement r : GsonHelper.getAsJsonArray(n, "replies", new JsonArray())) {
				if (r.isJsonObject()) {
					replies.add(new Reply(GsonHelper.getAsString(r.getAsJsonObject(), "text", ""), GsonHelper.getAsString(r.getAsJsonObject(), "to", "")));
				}
			}
			Ask ask = null;
			if (n.has("ask") && n.get("ask").isJsonObject()) {
				JsonObject a = n.getAsJsonObject("ask");
				ask = new Ask(GsonHelper.getAsString(a, "question", ""), GsonHelper.getAsString(a, "yes", ""), GsonHelper.getAsString(a, "no", ""));
			}
			Rite rite = null;
			if (n.has("rite") && n.get("rite").isJsonObject()) {
				JsonObject r = n.getAsJsonObject("rite");
				List<String> words = new ArrayList<>();
				for (JsonElement w : GsonHelper.getAsJsonArray(r, "words", new JsonArray())) {
					words.add(w.getAsString());
				}
				rite = new Rite(List.copyOf(words), GsonHelper.getAsInt(r, "seconds", 60), GsonHelper.getAsString(r, "to", ""), GsonHelper.getAsString(r, "fail", ""));
			}
			nodes.put(e.getKey(), new Node(e.getKey(), GsonHelper.getAsString(n, "shot", "shoulder"), GsonHelper.getAsString(n, "say", ""), List.copyOf(replies),
				ask, rite, GsonHelper.getAsString(n, "next", ""), GsonHelper.getAsString(n, "outcome", ""), GsonHelper.getAsBoolean(n, "end", false)));
		}
		String start = GsonHelper.getAsString(o, "start", "");
		if (!nodes.containsKey(start)) {
			JaymosKingdoms.LOGGER.warn("Story scene {} starts at {}, which isn't one of its nodes; left out", id, start);
			return null;
		}
		int color;
		try {
			color = Integer.parseInt(GsonHelper.getAsString(o, "color", "FFD27A"), 16);
		} catch (NumberFormatException ex) {
			color = 0xFFD27A;
		}
		return new Scene(id, GsonHelper.getAsString(o, "speaker", ""), color, start, Map.copyOf(nodes));
	}

	private static void writeAll(RegistryFriendlyByteBuf buf, List<Component> list) {
		buf.writeVarInt(list.size());
		for (Component c : list) {
			ComponentSerialization.STREAM_CODEC.encode(buf, c);
		}
	}

	private static List<Component> readAll(RegistryFriendlyByteBuf buf) {
		int n = buf.readVarInt();
		List<Component> out = new ArrayList<>(n);
		for (int i = 0; i < n; i++) {
			out.add(ComponentSerialization.STREAM_CODEC.decode(buf));
		}
		return out;
	}
}
