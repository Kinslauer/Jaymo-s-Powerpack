package com.jaymospowers.power.chrono;

import com.jaymospowers.JaymosPowers;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server → client: the chrono player's cooldowns and active durations, in ticks, for the HUD. Index = ability
 * (0 Rewind, 1 Stasis, 2 Overclock, 3 Bloom), plus Paradox Cut's (the ascended ability's) cooldown. The client counts
 * every value down by itself between updates.
 */
public record ChronoSync(int[] cooldownLeft, int[] cooldownTotal, int[] activeLeft, int[] activeTotal, int ultimateLeft, int ultimateTotal) implements CustomPacketPayload {
	public static final int ABILITIES = 4;
	public static final Type<ChronoSync> TYPE = new Type<>(JaymosPowers.id("chrono_sync"));
	public static final StreamCodec<RegistryFriendlyByteBuf, ChronoSync> CODEC = CustomPacketPayload.codec(ChronoSync::write, ChronoSync::read);

	private void write(RegistryFriendlyByteBuf buf) {
		for (int i = 0; i < ABILITIES; i++) {
			buf.writeVarInt(cooldownLeft[i]);
			buf.writeVarInt(cooldownTotal[i]);
			buf.writeVarInt(activeLeft[i]);
			buf.writeVarInt(activeTotal[i]);
		}
		buf.writeVarInt(ultimateLeft);
		buf.writeVarInt(ultimateTotal);
	}

	private static ChronoSync read(RegistryFriendlyByteBuf buf) {
		int[] cl = new int[ABILITIES];
		int[] ct = new int[ABILITIES];
		int[] al = new int[ABILITIES];
		int[] at = new int[ABILITIES];
		for (int i = 0; i < ABILITIES; i++) {
			cl[i] = buf.readVarInt();
			ct[i] = buf.readVarInt();
			al[i] = buf.readVarInt();
			at[i] = buf.readVarInt();
		}
		return new ChronoSync(cl, ct, al, at, buf.readVarInt(), buf.readVarInt());
	}

	@Override
	public Type<ChronoSync> type() {
		return TYPE;
	}
}
