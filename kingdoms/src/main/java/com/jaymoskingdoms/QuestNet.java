package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.ItemStack;

/**
 * (0.5.26) The quests' packets. The server is the authority: it sends a giver's quests as words and items to show
 * ({@link Open}), the player's log for their tracker and the givers' marks ({@link Log}), and takes the player's choice
 * back ({@link Act}).
 */
public final class QuestNet {
	/** A quest's state as shown: on offer, taken, ready to turn in. */
	public static final int OFFER = 0, ACTIVE = 1, READY = 2;
	/** What the player chose: take it, turn it in, give it up; or they closed the screen (the giver may go about their day). */
	public static final int ACCEPT = 0, TURN_IN = 1, ABANDON = 2, CLOSE = 3;

	/** One quest as the giver's screen shows it. */
	public record View(String id, int state, boolean royal, Component title, Component text, Component objective, List<ItemStack> rewards, List<Component> extras) {
		static void write(RegistryFriendlyByteBuf buf, View v) {
			buf.writeUtf(v.id);
			buf.writeVarInt(v.state);
			buf.writeBoolean(v.royal);
			ComponentSerialization.STREAM_CODEC.encode(buf, v.title);
			ComponentSerialization.STREAM_CODEC.encode(buf, v.text);
			ComponentSerialization.STREAM_CODEC.encode(buf, v.objective);
			buf.writeVarInt(v.rewards.size());
			for (ItemStack stack : v.rewards) {
				ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, stack);
			}
			components(buf, v.extras);
		}

		static View read(RegistryFriendlyByteBuf buf) {
			String id = buf.readUtf();
			int state = buf.readVarInt();
			boolean royal = buf.readBoolean();
			Component title = ComponentSerialization.STREAM_CODEC.decode(buf);
			Component text = ComponentSerialization.STREAM_CODEC.decode(buf);
			Component objective = ComponentSerialization.STREAM_CODEC.decode(buf);
			int n = buf.readVarInt();
			List<ItemStack> rewards = new ArrayList<>();
			for (int i = 0; i < n; i++) {
				rewards.add(ItemStack.OPTIONAL_STREAM_CODEC.decode(buf));
			}
			return new View(id, state, royal, title, text, objective, rewards, components(buf));
		}
	}

	/** Server → player: open {@code entityId}'s quests ({@code note}, if not empty, says why there are none). */
	public record Open(int entityId, Component giver, List<View> views, Component note) implements CustomPacketPayload {
		public static final Type<Open> TYPE = new Type<>(JaymosKingdoms.id("quests_open"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Open> CODEC = CustomPacketPayload.codec(
			(p, buf) -> {
				buf.writeVarInt(p.entityId);
				ComponentSerialization.STREAM_CODEC.encode(buf, p.giver);
				buf.writeVarInt(p.views.size());
				for (View v : p.views) {
					View.write(buf, v);
				}
				ComponentSerialization.STREAM_CODEC.encode(buf, p.note);
			},
			buf -> {
				int entity = buf.readVarInt();
				Component giver = ComponentSerialization.STREAM_CODEC.decode(buf);
				int n = buf.readVarInt();
				List<View> views = new ArrayList<>();
				for (int i = 0; i < n; i++) {
					views.add(View.read(buf));
				}
				return new Open(entity, giver, views, ComponentSerialization.STREAM_CODEC.decode(buf));
			});

		@Override
		public Type<Open> type() {
			return TYPE;
		}
	}

	/** One quest in the tracker: its giver (for their mark), title, what's left, whether it's ready, and where it leads. */
	public record Tracked(UUID giver, boolean royal, Component title, Component objective, boolean ready, Optional<BlockPos> target) {
	}

	/** Server → player: their quest log, for the tracker and the givers' marks. */
	public record Log(List<Tracked> quests) implements CustomPacketPayload {
		public static final Type<Log> TYPE = new Type<>(JaymosKingdoms.id("quests_log"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Log> CODEC = CustomPacketPayload.codec(
			(p, buf) -> {
				buf.writeVarInt(p.quests.size());
				for (Tracked t : p.quests) {
					UUIDUtil.STREAM_CODEC.encode(buf, t.giver);
					buf.writeBoolean(t.royal);
					ComponentSerialization.STREAM_CODEC.encode(buf, t.title);
					ComponentSerialization.STREAM_CODEC.encode(buf, t.objective);
					buf.writeBoolean(t.ready);
					buf.writeBoolean(t.target.isPresent());
					t.target.ifPresent(buf::writeBlockPos);
				}
			},
			buf -> {
				int n = buf.readVarInt();
				List<Tracked> quests = new ArrayList<>();
				for (int i = 0; i < n; i++) {
					UUID giver = UUIDUtil.STREAM_CODEC.decode(buf);
					boolean royal = buf.readBoolean();
					Component title = ComponentSerialization.STREAM_CODEC.decode(buf);
					Component objective = ComponentSerialization.STREAM_CODEC.decode(buf);
					boolean ready = buf.readBoolean();
					Optional<BlockPos> target = buf.readBoolean() ? Optional.of(buf.readBlockPos()) : Optional.empty();
					quests.add(new Tracked(giver, royal, title, objective, ready, target));
				}
				return new Log(quests);
			});

		@Override
		public Type<Log> type() {
			return TYPE;
		}
	}

	/** Player → server: {@code action} on quest {@code questId} at giver {@code entityId}. */
	public record Act(int entityId, String questId, int action) implements CustomPacketPayload {
		public static final Type<Act> TYPE = new Type<>(JaymosKingdoms.id("quests_act"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Act> CODEC = CustomPacketPayload.codec(
			(p, buf) -> {
				buf.writeVarInt(p.entityId);
				buf.writeUtf(p.questId);
				buf.writeVarInt(p.action);
			},
			buf -> new Act(buf.readVarInt(), buf.readUtf(), buf.readVarInt()));

		@Override
		public Type<Act> type() {
			return TYPE;
		}
	}

	private QuestNet() {
	}

	static void register() {
		PayloadTypeRegistry.clientboundPlay().register(Open.TYPE, Open.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(Log.TYPE, Log.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(Act.TYPE, Act.CODEC);
	}

	private static void components(RegistryFriendlyByteBuf buf, List<Component> list) {
		buf.writeVarInt(list.size());
		for (Component c : list) {
			ComponentSerialization.STREAM_CODEC.encode(buf, c);
		}
	}

	private static List<Component> components(RegistryFriendlyByteBuf buf) {
		int n = buf.readVarInt();
		List<Component> list = new ArrayList<>();
		for (int i = 0; i < n; i++) {
			list.add(ComponentSerialization.STREAM_CODEC.decode(buf));
		}
		return list;
	}
}
