package com.jaymospowers.power.chrono;

import com.jaymospowers.JaymosPowers;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server → every client: a Chrono's Time Stop ({@link ChronoTimeStop}) began or ended. {@code owner} is the stopping
 * Chrono's entity id (they alone move), {@code ticksLeft} how long time still stands still (0: it just resumed), and
 * {@code total} the stop's full length. Each client counts down by itself between updates.
 */
public record ChronoTimeStopSync(int owner, int ticksLeft, int total) implements CustomPacketPayload {
	public static final Type<ChronoTimeStopSync> TYPE = new Type<>(JaymosPowers.id("chrono_time_stop"));
	public static final StreamCodec<RegistryFriendlyByteBuf, ChronoTimeStopSync> CODEC = CustomPacketPayload.codec(ChronoTimeStopSync::write, ChronoTimeStopSync::read);

	private void write(RegistryFriendlyByteBuf buf) {
		buf.writeVarInt(owner);
		buf.writeVarInt(ticksLeft);
		buf.writeVarInt(total);
	}

	private static ChronoTimeStopSync read(RegistryFriendlyByteBuf buf) {
		return new ChronoTimeStopSync(buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
	}

	@Override
	public Type<ChronoTimeStopSync> type() {
		return TYPE;
	}
}
