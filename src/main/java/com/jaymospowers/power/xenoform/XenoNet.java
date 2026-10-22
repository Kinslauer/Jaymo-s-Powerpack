package com.jaymospowers.power.xenoform;

import com.jaymospowers.JaymosPowers;
import io.netty.buffer.ByteBuf;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;

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

	/** Client → server: the scroll wheel turned while Z was held: turn the dial one alien on (1) or back (-1). */
	public record Dial(int by) implements CustomPacketPayload {
		public static final Type<Dial> TYPE = new Type<>(JaymosPowers.id("xenoform_dial"));
		public static final StreamCodec<ByteBuf, Dial> CODEC = ByteBufCodecs.VAR_INT.map(Dial::new, Dial::by);

		@Override
		public Type<Dial> type() {
			return TYPE;
		}
	}

	/** Server → client: a band flashed green at {@code at} (a change of shape) in your sight ({@link BandFlash}). */
	public record Flash(Vec3 at) implements CustomPacketPayload {
		public static final Type<Flash> TYPE = new Type<>(JaymosPowers.id("xenoform_flash"));
		public static final StreamCodec<ByteBuf, Flash> CODEC = Vec3.STREAM_CODEC.map(Flash::new, Flash::at);

		@Override
		public Type<Flash> type() {
			return TYPE;
		}
	}

	private XenoNet() {
	}

	static void register() {
		PayloadTypeRegistry.serverboundPlay().register(Skill.TYPE, Skill.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Skill.TYPE, (payload, ctx) -> XenoformPower.skill(ctx.player(), payload.down()));
		PayloadTypeRegistry.serverboundPlay().register(Dial.TYPE, Dial.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Dial.TYPE, (payload, ctx) -> XenoformPower.scrollDial(ctx.player(), payload.by()));
		PayloadTypeRegistry.clientboundPlay().register(Flash.TYPE, Flash.CODEC);
	}
}
