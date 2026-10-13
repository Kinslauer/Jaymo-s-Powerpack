package com.jaymospowers.power.xenoform;

import com.jaymospowers.JaymosPowers;
import io.netty.buffer.ByteBuf;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Xenoform's packets. Everything else rides on the synced attachments in {@link XenoformState}. */
public final class XenoNet {
	/** Client → server: the power skill key (X) went down or up: the scan. */
	public record Skill(boolean down) implements CustomPacketPayload {
		public static final Type<Skill> TYPE = new Type<>(JaymosPowers.id("xenoform_skill"));
		public static final StreamCodec<ByteBuf, Skill> CODEC = ByteBufCodecs.BOOL.map(Skill::new, Skill::down);

		@Override
		public Type<Skill> type() {
			return TYPE;
		}
	}

	private XenoNet() {
	}

	static void register() {
		PayloadTypeRegistry.serverboundPlay().register(Skill.TYPE, Skill.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Skill.TYPE, (payload, ctx) -> XenoformPower.skill(ctx.player(), payload.down()));
	}
}
