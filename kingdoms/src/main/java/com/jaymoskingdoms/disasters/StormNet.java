package com.jaymoskingdoms.disasters;

import com.jaymoskingdoms.JaymosKingdoms;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** (0.6.10) Server → client: every storm and tornado in the Overworld, and the wind, sent a few times a second. */
public final class StormNet {
	public record Sync(float windAngle, float windSpeed, List<Storm> storms, List<Tornado> tornadoes) implements CustomPacketPayload {
		public static final Type<Sync> TYPE = new Type<>(JaymosKingdoms.id("storms"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Sync> CODEC = CustomPacketPayload.codec(
			(p, buf) -> {
				buf.writeFloat(p.windAngle);
				buf.writeFloat(p.windSpeed);
				buf.writeVarInt(p.storms.size());
				for (Storm s : p.storms) {
					s.write(buf);
				}
				buf.writeVarInt(p.tornadoes.size());
				for (Tornado t : p.tornadoes) {
					t.write(buf);
				}
			},
			buf -> {
				float angle = buf.readFloat();
				float speed = buf.readFloat();
				int n = buf.readVarInt();
				List<Storm> storms = new ArrayList<>(n);
				for (int i = 0; i < n; i++) {
					storms.add(Storm.read(buf));
				}
				int m = buf.readVarInt();
				List<Tornado> tornadoes = new ArrayList<>(m);
				for (int i = 0; i < m; i++) {
					tornadoes.add(Tornado.read(buf));
				}
				return new Sync(angle, speed, storms, tornadoes);
			});

		@Override
		public Type<Sync> type() {
			return TYPE;
		}
	}

	private StormNet() {
	}

	static void register() {
		PayloadTypeRegistry.clientboundPlay().register(Sync.TYPE, Sync.CODEC);
	}
}
