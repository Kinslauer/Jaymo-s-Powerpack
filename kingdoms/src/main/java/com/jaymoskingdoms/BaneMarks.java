package com.jaymoskingdoms;

import com.jaymoskingdoms.Captains.Captain;
import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.620) Rivals patch 6, <b>the marks that end your Bane</b> (ideas from Warframe's three-part key, a commercial game: our
 * own names, code and lines). Each Bane is undone by three of eight old marks ({@link #MARKS}), in one order, its own
 * ({@link #KEYS}).
 * <ul>
 * <li><b>Its men know them:</b> each of its living band carries its owner's brand and one of the three marks. Down one and
 * question him ("What marks can end your master?"): he names his mark. With Rough Questions or Interrogation he also says
 * where it falls in the order; with Quiet Word he lets slip two marks that aren't any of it. The proud and spiteful may lie
 * (never to Interrogation). What anyone learns is learnt for the Bane's owner ({@link #LEARNT}).</li>
 * <li><b>Carving:</b> sneak and use a grindstone with a weapon in hand: the marks screen shows the eight and what's known
 * of each; pick three in order and carve them into the blade ({@link #CARVED}, a data component).</li>
 * <li><b>The finisher judges them:</b> finish your Bane (downed, or its head off) with a carved blade. The right three in
 * order end it: killed for good (its weapon is yours), or, if you sneak as you do it, sworn to you
 * ({@code SwornCaptains.swearBane}). Wrong ones and it shrugs off the blow, laughs, climbs a rank and is gone, and you
 * learn which were right, which belong elsewhere in the order and which aren't its at all. A finisher with no marks fells
 * it as before (it's back in a day or two).</li>
 * </ul>
 */
public final class BaneMarks {
	/** The eight marks (their lang names are {@code bane.jaymoskingdoms.mark.<name>}). */
	public static final List<String> MARKS = List.of("ash", "bone", "crow", "flood", "iron", "moon", "thorn", "wolf");
	public static final int KEY_LENGTH = 3;
	/** What's known of a mark ({@link #LEARNT}): not one of it, one of it (place unknown), or its place 0..2. */
	public static final int OUT = -2, IN = -1;
	/** On one of a band: which of the key's three he knows. */
	static final String KNOWS_TAG = "jaymoskingdoms.bane_knows=";
	/** How many marks Quiet Word rules out. */
	static final int QUIET_RULES_OUT = 2;
	static final int LINES = 2;

	/** The marks carved into a blade, in order. Saved id: never rename. */
	public static DataComponentType<List<String>> CARVED;

	/** On the Overworld: each Bane's (captain id) key, its three marks in order. Saved. */
	static final AttachmentType<Map<String, List<String>>> KEYS = AttachmentRegistry.create(JaymosKingdoms.id("bane_keys"),
		builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, Codec.STRING.listOf())));
	/** On the Overworld: what's been learnt of each Bane's marks (captain id → mark → {@link #OUT}, {@link #IN} or its place). Saved. */
	static final AttachmentType<Map<String, Map<String, Integer>>> LEARNT = AttachmentRegistry.create(JaymosKingdoms.id("bane_learnt"),
		builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, Codec.unboundedMap(Codec.STRING, Codec.INT))));

	/** Server → player: open the marks screen (the Bane's name, what's known of each mark in {@link #MARKS} order, what's carved now). */
	public record Board(Component bane, List<Integer> known, List<String> carved) implements CustomPacketPayload {
		public static final Type<Board> TYPE = new Type<>(JaymosKingdoms.id("bane_marks_board"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Board> CODEC = CustomPacketPayload.codec(Board::write, Board::read);

		private void write(RegistryFriendlyByteBuf buf) {
			net.minecraft.network.chat.ComponentSerialization.STREAM_CODEC.encode(buf, bane);
			buf.writeVarInt(known.size());
			known.forEach(buf::writeInt);
			buf.writeVarInt(carved.size());
			carved.forEach(buf::writeUtf);
		}

		private static Board read(RegistryFriendlyByteBuf buf) {
			Component name = net.minecraft.network.chat.ComponentSerialization.STREAM_CODEC.decode(buf);
			int n = buf.readVarInt();
			List<Integer> known = new ArrayList<>();
			for (int i = 0; i < n; i++) {
				known.add(buf.readInt());
			}
			int m = buf.readVarInt();
			List<String> carved = new ArrayList<>();
			for (int i = 0; i < m; i++) {
				carved.add(buf.readUtf(32));
			}
			return new Board(name, known, carved);
		}

		@Override
		public Type<Board> type() {
			return TYPE;
		}
	}

	/** Player → server: carve these marks (in order) into the blade in hand; none clears it. */
	public record Carve(List<String> marks) implements CustomPacketPayload {
		public static final Type<Carve> TYPE = new Type<>(JaymosKingdoms.id("bane_marks_carve"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Carve> CODEC = StreamCodec.composite(
			ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list(KEY_LENGTH)), Carve::marks, Carve::new);

		@Override
		public Type<Carve> type() {
			return TYPE;
		}
	}

	private BaneMarks() {
	}

	static void register() {
		CARVED = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, JaymosKingdoms.id("bane_marks"),
			DataComponentType.<List<String>>builder().persistent(Codec.STRING.listOf())
				.networkSynchronized(ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list(KEY_LENGTH))).build());
		PayloadTypeRegistry.clientboundPlay().register(Board.TYPE, Board.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(Carve.TYPE, Carve.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Carve.TYPE, (payload, context) -> carve(context.player(), payload.marks()));
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (hand != InteractionHand.MAIN_HAND || !player.isShiftKeyDown() || !level.getBlockState(hit.getBlockPos()).is(Blocks.GRINDSTONE)
				|| !blade(player.getMainHandItem())) {
				return InteractionResult.PASS;
			}
			if (player instanceof ServerPlayer sp) {
				open(sp);
			}
			return InteractionResult.SUCCESS;
		});
	}

	/** A blade marks can go on: a weapon or tool that wears (one to a stack). */
	static boolean blade(ItemStack stack) {
		return !stack.isEmpty() && stack.getMaxStackSize() == 1 && stack.isDamageableItem();
	}

	/** {@code c}'s key: made the first time it's asked for (three of the eight, in an order of its own). */
	static List<String> key(MinecraftServer server, Captain c) {
		ServerLevel world = Factions.level(server);
		List<String> key = world.getAttachedOrElse(KEYS, Map.of()).get(c.id());
		if (key != null) {
			return key;
		}
		RandomSource random = world.getRandom();
		List<String> pool = new ArrayList<>(MARKS);
		List<String> made = new ArrayList<>();
		for (int i = 0; i < KEY_LENGTH; i++) {
			made.add(pool.remove(random.nextInt(pool.size())));
		}
		Map<String, List<String>> all = new HashMap<>(world.getAttachedOrElse(KEYS, Map.of()));
		all.put(c.id(), List.copyOf(made));
		world.setAttached(KEYS, Map.copyOf(all));
		return made;
	}

	static Map<String, Integer> learnt(MinecraftServer server, String id) {
		return Factions.level(server).getAttachedOrElse(LEARNT, Map.of()).getOrDefault(id, Map.of());
	}

	/** {@code mark} of Bane {@code id} is now known as {@code what}; a place known is never lost to a vaguer word. */
	private static void learn(MinecraftServer server, String id, String mark, int what) {
		ServerLevel world = Factions.level(server);
		Map<String, Map<String, Integer>> all = new HashMap<>(world.getAttachedOrElse(LEARNT, Map.of()));
		Map<String, Integer> mine = new HashMap<>(all.getOrDefault(id, Map.of()));
		Integer had = mine.get(mark);
		if (what == IN && had != null && had >= 0) {
			return;
		}
		mine.put(mark, what);
		all.put(id, Map.copyOf(mine));
		world.setAttached(LEARNT, Map.copyOf(all));
	}

	/** Every trace of Bane {@code id}'s key, once it's ended. */
	static void forget(MinecraftServer server, String id) {
		ServerLevel world = Factions.level(server);
		Map<String, List<String>> keys = new HashMap<>(world.getAttachedOrElse(KEYS, Map.of()));
		keys.remove(id);
		world.setAttached(KEYS, Map.copyOf(keys));
		Map<String, Map<String, Integer>> learnt = new HashMap<>(world.getAttachedOrElse(LEARNT, Map.of()));
		learnt.remove(id);
		world.setAttached(LEARNT, Map.copyOf(learnt));
	}

	// ---- its men ------------------------------------------------------------------------------------------------------------

	/** (From {@code Banes.follower}) One of its living band: he carries its owner's brand and one of its marks. */
	static void carry(LivingEntity man, String ladder, RandomSource random) {
		UUID owner = Banes.owner(ladder);
		if (owner != null) {
			man.setAttached(Banes.BRAND, owner.toString());
		}
		man.addTag(KNOWS_TAG + random.nextInt(KEY_LENGTH));
	}

	/** The place in the key {@code man} knows, or −1. */
	static int knows(LivingEntity man) {
		for (String tag : man.entityTags()) {
			if (tag.startsWith(KNOWS_TAG)) {
				try {
					return Integer.parseInt(tag.substring(KNOWS_TAG.length()));
				} catch (NumberFormatException e) {
					return -1;
				}
			}
		}
		return -1;
	}

	/** (Questioning) {@code body} is one of a Bane's band who knows one of its marks. */
	public static boolean asks(LivingEntity body) {
		return Banes.followerOf(body) != null && knows(body) >= 0;
	}

	/**
	 * (Questioning) "What marks can end your master?": his mark; with {@code rough} or {@code will} its place; with {@code quiet}
	 * two that aren't its; {@code lies}: a mark that isn't its, said just as surely (and learnt as surely).
	 */
	public static Component questioned(ServerPlayer player, LivingEntity body, boolean lies, boolean rough, boolean quiet, boolean will) {
		MinecraftServer server = player.level().getServer();
		String ladder = Banes.followerOf(body);
		Captain c = null;
		for (Captain x : ladder == null ? List.<Captain>of() : List.copyOf(Captains.all(server).values())) {
			if (x.tribe().equals(ladder) && !(x.fallen() && x.back() < 0)) {
				c = x;
			}
		}
		int place = knows(body);
		RandomSource random = body.getRandom();
		Component who = Recollections.nameTo(player, body);
		if (c == null || place < 0) {
			return Component.translatable("bane.jaymoskingdoms.question.none." + random.nextInt(LINES), who);
		}
		List<String> key = key(server, c);
		String mark = key.get(place);
		if (lies) {
			List<String> not = new ArrayList<>(MARKS);
			not.removeAll(key);
			mark = not.get(random.nextInt(not.size()));
		}
		boolean placed = rough || will;
		learn(server, c.id(), mark, placed ? place : IN);
		Component name = Component.translatable("bane.jaymoskingdoms.mark." + mark).withStyle(ChatFormatting.DARK_RED);
		Component line = placed
			? Component.translatable("bane.jaymoskingdoms.question.placed." + random.nextInt(LINES), who, name, Component.translatable("bane.jaymoskingdoms.place." + place))
			: Component.translatable("bane.jaymoskingdoms.question.told." + random.nextInt(LINES), who, name);
		if (quiet) {
			List<String> not = new ArrayList<>(MARKS);
			not.removeAll(key);
			List<Component> out = new ArrayList<>();
			for (int i = 0; i < QUIET_RULES_OUT && !not.isEmpty(); i++) {
				String o = not.remove(random.nextInt(not.size()));
				learn(server, c.id(), o, OUT);
				out.add(Component.translatable("bane.jaymoskingdoms.mark." + o));
			}
			if (out.size() == 2) {
				line = Component.empty().append(line).append(" ").append(Component.translatable("bane.jaymoskingdoms.question.quiet", out.get(0), out.get(1)));
			}
		}
		player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.bane.learnt", name, Component.literal(c.name())).withStyle(ChatFormatting.GRAY));
		return line;
	}

	// ---- carving --------------------------------------------------------------------------------------------------------------

	/** The marks screen for {@code player}'s Bane, if they have one. */
	static void open(ServerPlayer player) {
		Captain c = Banes.of(player);
		if (c == null) {
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.bane.no_bane").withStyle(ChatFormatting.GRAY));
			return;
		}
		Map<String, Integer> learnt = learnt(player.level().getServer(), c.id());
		List<Integer> known = new ArrayList<>();
		for (String m : MARKS) {
			known.add(learnt.getOrDefault(m, Integer.MIN_VALUE));
		}
		ServerPlayNetworking.send(player, new Board(Component.literal(c.name()), known, player.getMainHandItem().getOrDefault(CARVED, List.of())));
	}

	/** {@code player} carves {@code marks} (in order) into the blade in hand, at a grindstone; none clears it. */
	private static void carve(ServerPlayer player, List<String> marks) {
		ItemStack held = player.getMainHandItem();
		if (!blade(held) || marks.size() > KEY_LENGTH || !MARKS.containsAll(marks) || marks.stream().distinct().count() != marks.size()) {
			return;
		}
		if (marks.isEmpty()) {
			held.remove(CARVED);
		} else {
			held.set(CARVED, List.copyOf(marks));
		}
		ServerLevel level = (ServerLevel)player.level();
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.GRINDSTONE_USE, SoundSource.PLAYERS, 0.8F, 0.8F);
		player.sendOverlayMessage(Component.translatable(marks.isEmpty() ? "message.jaymoskingdoms.bane.ground_off" : "message.jaymoskingdoms.bane.carved",
			names(marks)).withStyle(ChatFormatting.GRAY));
	}

	/** "ash, crow, wolf" in their lang names. */
	public static Component names(List<String> marks) {
		var out = Component.empty();
		for (int i = 0; i < marks.size(); i++) {
			if (i > 0) {
				out.append(", ");
			}
			out.append(Component.translatable("bane.jaymoskingdoms.mark." + marks.get(i)));
		}
		return out;
	}

	// ---- the judgement -----------------------------------------------------------------------------------------------------------

	/** What a finisher's marks come to: none carved, the right three, or wrong. */
	enum Verdict {
		NONE, RIGHT, WRONG
	}

	/** {@code by}'s blade on {@code c}'s finisher: what its marks come to (and what's learnt from wrong ones). */
	static Verdict judge(ServerPlayer by, Captain c) {
		List<String> carved = by.getMainHandItem().getOrDefault(CARVED, List.of());
		if (carved.isEmpty()) {
			return Verdict.NONE;
		}
		MinecraftServer server = by.level().getServer();
		List<String> key = key(server, c);
		if (carved.equals(key)) {
			return Verdict.RIGHT;
		}
		// (you learn which were right: in place, out of place, or none of it)
		for (int i = 0; i < carved.size(); i++) {
			String m = carved.get(i);
			int at = key.indexOf(m);
			learn(server, c.id(), m, at == i ? i : at >= 0 ? IN : OUT);
		}
		return Verdict.WRONG;
	}

	/** Particles and a sound for a judged blow. */
	static void flash(ServerLevel level, LivingEntity body, boolean right) {
		level.sendParticles(right ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.CRIT, body.getX(), body.getY(0.6), body.getZ(), 30, 0.4, 0.5, 0.4, 0.1);
		level.playSound(null, body.getX(), body.getY(), body.getZ(), right ? SoundEvents.WITHER_DEATH : SoundEvents.ANVIL_LAND, SoundSource.HOSTILE,
			right ? 0.6F : 0.8F, right ? 1.4F : 1.6F);
	}

	/** The marks known of {@code c} as a line, for the journal ("ash (first), crow, not moon"). */
	static @Nullable Component known(MinecraftServer server, Captain c) {
		Map<String, Integer> learnt = learnt(server, c.id());
		if (learnt.isEmpty()) {
			return null;
		}
		var out = Component.empty();
		boolean first = true;
		for (String m : MARKS) {
			Integer k = learnt.get(m);
			if (k == null) {
				continue;
			}
			if (!first) {
				out.append(", ");
			}
			first = false;
			Component name = Component.translatable("bane.jaymoskingdoms.mark." + m);
			out.append(k == OUT ? Component.translatable("bane.jaymoskingdoms.known.out", name)
				: k == IN ? Component.translatable("bane.jaymoskingdoms.known.in", name)
				: Component.translatable("bane.jaymoskingdoms.known.placed", name, Component.translatable("bane.jaymoskingdoms.place." + k)));
		}
		return out;
	}
}
