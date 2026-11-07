package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.ItemStack;

/**
 * (0.7.488) The mason's table's screen ({@code MasonScreen} on the client): the server sends the designs on the world,
 * what the table is doing and what it still needs; the screen sends back what's chosen (draw a design on a plan, take the
 * plan back, try skipped blocks again, offer the design to the settlement). The server decides everything and sends the
 * screen again with the result.
 */
public final class MasonNet {
	/** What the screen asks for. */
	public static final int REFRESH = 0, DRAW = 1, TAKE = 2, RETRY = 3, SUBMIT = 4, SAVE = 5;

	/** A design on the world: its name, size, blocks, who drew it, and whether a settlement's builders can put it up. */
	public record Design(String name, int w, int d, int h, int blocks, String author, boolean crews) {
		static void write(FriendlyByteBuf buf, Design d) {
			buf.writeUtf(d.name, 64);
			buf.writeVarInt(d.w);
			buf.writeVarInt(d.d);
			buf.writeVarInt(d.h);
			buf.writeVarInt(d.blocks);
			buf.writeUtf(d.author, 128);
			buf.writeBoolean(d.crews);
		}

		static Design read(FriendlyByteBuf buf) {
			return new Design(buf.readUtf(64), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readUtf(128), buf.readBoolean());
		}
	}

	/** One material still to lay, and how many. */
	public record Need(ItemStack item, int count) {
	}

	/**
	 * Server to client: open (or refresh) the screen of the table at {@code pos}. {@code plan}: the design in the table
	 * ("" for none); {@code laid}/{@code total}: blocks done; {@code waiting}: the item it waits for (empty for none);
	 * {@code blankPlans}: building plans the player carries; {@code marked}: they've two corners marked with a surveyor's
	 * rod (so the build between can be saved); {@code note}: the last answer, to show at the bottom.
	 */
	public record Open(BlockPos pos, List<Design> designs, String plan, int laid, int total, boolean done, int skipped, ItemStack waiting, List<Need> needs,
		int blankPlans, boolean marked, Component note) implements CustomPacketPayload {
		public static final Type<Open> TYPE = new Type<>(JaymosKingdoms.id("mason_open"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Open> CODEC = CustomPacketPayload.codec((p, buf) -> {
			buf.writeBlockPos(p.pos);
			buf.writeVarInt(p.designs.size());
			p.designs.forEach(d -> Design.write(buf, d));
			buf.writeUtf(p.plan, 64);
			buf.writeVarInt(p.laid);
			buf.writeVarInt(p.total);
			buf.writeBoolean(p.done);
			buf.writeVarInt(p.skipped);
			ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, p.waiting);
			buf.writeVarInt(p.needs.size());
			for (Need n : p.needs) {
				ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, n.item);
				buf.writeVarInt(n.count);
			}
			buf.writeVarInt(p.blankPlans);
			buf.writeBoolean(p.marked);
			ComponentSerialization.STREAM_CODEC.encode(buf, p.note);
		}, buf -> {
			BlockPos pos = buf.readBlockPos();
			int n = buf.readVarInt();
			List<Design> designs = new ArrayList<>();
			for (int i = 0; i < n; i++) {
				designs.add(Design.read(buf));
			}
			String plan = buf.readUtf(64);
			int laid = buf.readVarInt(), total = buf.readVarInt();
			boolean done = buf.readBoolean();
			int skipped = buf.readVarInt();
			ItemStack waiting = ItemStack.OPTIONAL_STREAM_CODEC.decode(buf);
			int m = buf.readVarInt();
			List<Need> needs = new ArrayList<>();
			for (int i = 0; i < m; i++) {
				needs.add(new Need(ItemStack.OPTIONAL_STREAM_CODEC.decode(buf), buf.readVarInt()));
			}
			return new Open(pos, designs, plan, laid, total, done, skipped, waiting, needs, buf.readVarInt(), buf.readBoolean(),
				ComponentSerialization.STREAM_CODEC.decode(buf));
		});

		@Override
		public Type<Open> type() {
			return TYPE;
		}
	}

	/** Client to server: {@code action} at the table at {@code pos}, on design {@code name} (DRAW, SUBMIT, SAVE). */
	public record Act(BlockPos pos, int action, String name) implements CustomPacketPayload {
		public static final Type<Act> TYPE = new Type<>(JaymosKingdoms.id("mason_act"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Act> CODEC = CustomPacketPayload.codec((p, buf) -> {
			buf.writeBlockPos(p.pos);
			buf.writeVarInt(p.action);
			buf.writeUtf(p.name, 64);
		}, buf -> new Act(buf.readBlockPos(), buf.readVarInt(), buf.readUtf(64)));

		@Override
		public Type<Act> type() {
			return TYPE;
		}
	}

	private MasonNet() {
	}

	static void register() {
		PayloadTypeRegistry.clientboundPlay().register(Open.TYPE, Open.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(Act.TYPE, Act.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Act.TYPE, (payload, context) -> MasonTable.act(context.player(), payload));
	}
}
