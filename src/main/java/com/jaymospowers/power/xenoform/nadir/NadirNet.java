package com.jaymospowers.power.xenoform.nadir;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.power.xenoform.XenoForms;
import com.jaymospowers.power.xenoform.XenoformPower;
import io.netty.buffer.ByteBuf;
import java.util.List;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/** The Rules screen's packets (Nadir's Command, B). */
public final class NadirNet {
	/** Server → client: open the Rules screen on these rules ({@link NadirRules#FIELDS} numbers a rule). */
	public record OpenRules(List<Integer> rules) implements CustomPacketPayload {
		public static final Type<OpenRules> TYPE = new Type<>(JaymosPowers.id("nadir_open_rules"));
		public static final StreamCodec<ByteBuf, OpenRules> CODEC = ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(64)).map(OpenRules::new, OpenRules::rules);

		@Override
		public Type<OpenRules> type() {
			return TYPE;
		}
	}

	/** Client → server: the rules as the screen left them. */
	public record SetRules(List<Integer> rules) implements CustomPacketPayload {
		public static final Type<SetRules> TYPE = new Type<>(JaymosPowers.id("nadir_set_rules"));
		public static final StreamCodec<ByteBuf, SetRules> CODEC = ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(64)).map(SetRules::new, SetRules::rules);

		@Override
		public Type<SetRules> type() {
			return TYPE;
		}
	}

	private NadirNet() {
	}

	public static void register() {
		PayloadTypeRegistry.clientboundPlay().register(OpenRules.TYPE, OpenRules.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(SetRules.TYPE, SetRules.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(SetRules.TYPE, (payload, ctx) -> {
			// only Nadir writes the rules
			if (XenoformPower.formOf(ctx.player()) == XenoForms.NADIR) {
				NadirRules.set(ctx.player(), payload.rules());
			}
		});
	}

	public static void open(ServerPlayer player) {
		ServerPlayNetworking.send(player, new OpenRules(NadirRules.get(player)));
	}
}
