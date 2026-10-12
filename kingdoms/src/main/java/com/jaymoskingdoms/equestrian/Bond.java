package com.jaymoskingdoms.equestrian;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * (0.5.40) A horse and its people ({@link Bonding}): how much it trusts each rider (0-100, by player), what it has
 * learnt ({@link Discipline} experience), how fed and watered it is (0-100), its hooves (worn down on stone without
 * shoes, 0-100) and its shoes (0 unshod, else what's left of them), until when its coat shines from a brushing, when it
 * was last brushed, and when its needs were last seen to (game time). Saved, and synced so its rider's HUD and screens
 * show it.
 */
public record Bond(Map<UUID, Float> trust, float trail, float racing, float jumping, float war, float hunger, float thirst, float hooves,
	float shoes, long shine, long brushed, long at) {
	public static final float FULL = 100.0F;

	public static final Codec<Bond> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.unboundedMap(UUIDUtil.STRING_CODEC, Codec.FLOAT).optionalFieldOf("trust", Map.of()).forGetter(Bond::trust),
			Codec.FLOAT.optionalFieldOf("trail", 0.0F).forGetter(Bond::trail),
			Codec.FLOAT.optionalFieldOf("racing", 0.0F).forGetter(Bond::racing),
			Codec.FLOAT.optionalFieldOf("jumping", 0.0F).forGetter(Bond::jumping),
			Codec.FLOAT.optionalFieldOf("war", 0.0F).forGetter(Bond::war),
			Codec.FLOAT.optionalFieldOf("hunger", FULL).forGetter(Bond::hunger),
			Codec.FLOAT.optionalFieldOf("thirst", FULL).forGetter(Bond::thirst),
			Codec.FLOAT.optionalFieldOf("hooves", FULL).forGetter(Bond::hooves),
			Codec.FLOAT.optionalFieldOf("shoes", 0.0F).forGetter(Bond::shoes),
			Codec.LONG.optionalFieldOf("shine", 0L).forGetter(Bond::shine),
			Codec.LONG.optionalFieldOf("brushed", 0L).forGetter(Bond::brushed),
			Codec.LONG.optionalFieldOf("at", 0L).forGetter(Bond::at))
		.apply(i, Bond::new));

	private static final StreamCodec<ByteBuf, Map<UUID, Float>> TRUST_STREAM = ByteBufCodecs.map(HashMap::new, UUIDUtil.STREAM_CODEC, ByteBufCodecs.FLOAT);

	public static final StreamCodec<ByteBuf, Bond> STREAM_CODEC = StreamCodec.of(
		(buf, b) -> {
			TRUST_STREAM.encode(buf, b.trust);
			buf.writeFloat(b.trail);
			buf.writeFloat(b.racing);
			buf.writeFloat(b.jumping);
			buf.writeFloat(b.war);
			buf.writeFloat(b.hunger);
			buf.writeFloat(b.thirst);
			buf.writeFloat(b.hooves);
			buf.writeFloat(b.shoes);
			buf.writeLong(b.shine);
			buf.writeLong(b.brushed);
			buf.writeLong(b.at);
		},
		buf -> new Bond(TRUST_STREAM.decode(buf), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(),
			buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readLong(), buf.readLong(), buf.readLong()));

	public static final Bond NEW = new Bond(Map.of(), 0.0F, 0.0F, 0.0F, 0.0F, FULL, FULL, FULL, 0.0F, 0L, 0L, 0L);

	public float trustOf(UUID player) {
		return this.trust.getOrDefault(player, 0.0F);
	}

	public boolean knows(UUID player) {
		return this.trust.containsKey(player);
	}

	public Bond withTrust(UUID player, float value) {
		Map<UUID, Float> map = new HashMap<>(this.trust);
		map.put(player, Math.max(0.0F, Math.min(FULL, value)));
		return new Bond(map, trail, racing, jumping, war, hunger, thirst, hooves, shoes, shine, brushed, at);
	}

	public float xp(Discipline d) {
		return switch (d) {
			case TRAIL -> this.trail;
			case RACING -> this.racing;
			case JUMPING -> this.jumping;
			case WAR -> this.war;
		};
	}

	public int level(Discipline d) {
		return Discipline.level(this.xp(d));
	}

	public Bond withXp(Discipline d, float value) {
		return new Bond(trust, d == Discipline.TRAIL ? value : trail, d == Discipline.RACING ? value : racing,
			d == Discipline.JUMPING ? value : jumping, d == Discipline.WAR ? value : war, hunger, thirst, hooves, shoes, shine, brushed, at);
	}

	public Bond withNeeds(float hunger, float thirst) {
		return new Bond(trust, trail, racing, jumping, war, clamp(hunger), clamp(thirst), hooves, shoes, shine, brushed, at);
	}

	public Bond withFeet(float hooves, float shoes) {
		return new Bond(trust, trail, racing, jumping, war, hunger, thirst, clamp(hooves), clamp(shoes), shine, brushed, at);
	}

	public Bond withBrush(long shine, long brushed) {
		return new Bond(trust, trail, racing, jumping, war, hunger, thirst, hooves, shoes, shine, brushed, at);
	}

	public Bond at(long at) {
		return new Bond(trust, trail, racing, jumping, war, hunger, thirst, hooves, shoes, shine, brushed, at);
	}

	public boolean shod() {
		return this.shoes > 0.0F;
	}

	private static float clamp(float v) {
		return Math.max(0.0F, Math.min(FULL, v));
	}
}
