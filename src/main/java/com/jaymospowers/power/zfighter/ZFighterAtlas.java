package com.jaymospowers.power.zfighter;

import com.jaymospowers.JaymosPowers;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/**
 * The star map's atlas (Andy's Buder's Lanterns pick, ideas only): what a pilot has found on each far world, kept on the
 * player and shown on that world's card. How many times they've come down there, where they last did, and their bed if
 * their respawn point is on that world (noted each time the map is opened or they land).
 */
public final class ZFighterAtlas {
	/** One world's pins: landings, the last landing spot, and the bed (if their respawn point is there). */
	public record Pin(int landings, BlockPos landed, Optional<BlockPos> bed) {
		static final Codec<Pin> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.INT.fieldOf("landings").forGetter(Pin::landings),
			BlockPos.CODEC.fieldOf("landed").forGetter(Pin::landed),
			BlockPos.CODEC.optionalFieldOf("bed").forGetter(Pin::bed)).apply(i, Pin::new));
		static final StreamCodec<io.netty.buffer.ByteBuf, Pin> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, Pin::landings,
			BlockPos.STREAM_CODEC, Pin::landed,
			ByteBufCodecs.optional(BlockPos.STREAM_CODEC), Pin::bed,
			Pin::new);
	}

	/** On the player: pins by world key ({@link ZFighterSpace.Dest#key}). Kept through death, sent to them. */
	public static final AttachmentType<Map<String, Pin>> PINS = AttachmentRegistry.create(JaymosPowers.id("zfighter_atlas"),
		builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, Pin.CODEC)).copyOnDeath()
			.syncWith(ByteBufCodecs.map(HashMap::new, ByteBufCodecs.STRING_UTF8, Pin.STREAM_CODEC), AttachmentSyncPredicate.targetOnly()));

	private ZFighterAtlas() {
	}

	/** {@code player}'s pins on world {@code index} (a {@link ZFighterSpace#DESTS} index), or null if they've never come down there. */
	public static @Nullable Pin of(Player player, int index) {
		if (index < 0 || index >= ZFighterSpace.DESTS.length) {
			return null;
		}
		return player.getAttachedOrElse(PINS, Map.of()).get(ZFighterSpace.DESTS[index].key());
	}

	/** {@code player} came down on {@code dest}: one more landing, here. */
	static void landed(ServerPlayer player, ZFighterSpace.Dest dest) {
		Map<String, Pin> pins = new HashMap<>(player.getAttachedOrElse(PINS, Map.of()));
		Pin old = pins.get(dest.key());
		pins.put(dest.key(), new Pin(old == null ? 1 : old.landings() + 1, player.blockPosition(), old == null ? Optional.empty() : old.bed()));
		player.setAttached(PINS, pins);
		noteBed(player);
	}

	/** Marks the bed on whichever far world {@code player}'s respawn point is on, and clears it from the others. */
	static void noteBed(ServerPlayer player) {
		Map<String, Pin> pins = player.getAttachedOrElse(PINS, Map.of());
		if (pins.isEmpty()) {
			return;
		}
		ServerPlayer.RespawnConfig config = player.getRespawnConfig();
		Map<String, Pin> next = new HashMap<>();
		boolean changed = false;
		for (Map.Entry<String, Pin> e : pins.entrySet()) {
			Pin pin = e.getValue();
			Optional<BlockPos> bed = Optional.empty();
			if (config != null) {
				for (ZFighterSpace.Dest dest : ZFighterSpace.DESTS) {
					if (dest.key().equals(e.getKey()) && dest.level().equals(config.respawnData().dimension())) {
						bed = Optional.of(config.respawnData().pos());
					}
				}
			}
			changed |= !bed.equals(pin.bed());
			next.put(e.getKey(), new Pin(pin.landings(), pin.landed(), bed));
		}
		if (changed) {
			player.setAttached(PINS, next);
		}
	}
}
