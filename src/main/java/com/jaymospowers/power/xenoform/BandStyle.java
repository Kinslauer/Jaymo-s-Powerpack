package com.jaymospowers.power.xenoform;

import com.jaymospowers.JaymosPowers;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/**
 * (0.18.126, Xeno-Band patch 9) How a band is dressed, picked on the band screen's Looks tab: each alien's look (plain,
 * armoured or weathered, {@link Look}), the glow colour (the band's face and glow, every alien's green lines, the slam
 * flash and the hologram), the face plate's shape ({@link #PLATES}) and spikes. All for show. Kept on the wearer (synced
 * to everyone, so others see it) and carried on a band item taken off, like the upgrades.
 */
public final class BandStyle {
	/** An alien's look. The client draws them from its own skin ({@code client/power/xenoform/XenoLooks}). */
	public enum Look {
		PLAIN("plain"), ARMOURED("armoured"), WEATHERED("weathered");

		public final String id;

		Look(String id) {
			this.id = id;
		}

		public static Look of(@Nullable String id) {
			for (Look look : values()) {
				if (look.id.equals(id)) {
					return look;
				}
			}
			return PLAIN;
		}

		public Look next() {
			return values()[(ordinal() + 1) % values().length];
		}
	}

	// ---- tuning ----
	/** The glows to pick from (RGB); the first is the band's own green. 0 in a style: the band's own (green, or the rival band's red). */
	public static final int[] GLOWS = {0x5CF07A, 0x4AB8FF, 0x40F0E0, 0xFFC940, 0xFF8A30, 0xFF4040, 0xFF6FB5, 0xB070FF, 0xF0F4FF};
	public static final int GREEN = 0x5CF07A, RIVAL_RED = 0xFF3030;
	/** The face plate's shapes. */
	public static final List<String> PLATES = List.of("square", "round", "diamond", "wide", "tall");

	public record Style(Map<String, String> looks, int glow, String plate, boolean spikes) {
		public static final Style NONE = new Style(Map.of(), 0, "square", false);
		public static final Codec<Style> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.unboundedMap(Codec.STRING, Codec.STRING).optionalFieldOf("looks", Map.of()).forGetter(Style::looks),
				Codec.INT.optionalFieldOf("glow", 0).forGetter(Style::glow),
				Codec.STRING.optionalFieldOf("plate", "square").forGetter(Style::plate),
				Codec.BOOL.optionalFieldOf("spikes", false).forGetter(Style::spikes))
			.apply(i, Style::new));
		public static final StreamCodec<ByteBuf, Style> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.map(HashMap::new, ByteBufCodecs.STRING_UTF8, ByteBufCodecs.STRING_UTF8), s -> new HashMap<>(s.looks()),
			ByteBufCodecs.INT, Style::glow,
			ByteBufCodecs.STRING_UTF8, Style::plate,
			ByteBufCodecs.BOOL, Style::spikes,
			(looks, glow, plate, spikes) -> new Style(Map.copyOf(looks), glow, plate, spikes));

		public Look look(String form) {
			return Look.of(this.looks.get(form));
		}

		Style withLook(String form, Look look) {
			Map<String, String> map = new HashMap<>(this.looks);
			if (look == Look.PLAIN) {
				map.remove(form);
			} else {
				map.put(form, look.id);
			}
			return new Style(Map.copyOf(map), this.glow, this.plate, this.spikes);
		}
	}

	public static final AttachmentType<Style> STYLE = AttachmentRegistry.create(JaymosPowers.id("xenoform_style"),
		builder -> builder.persistent(Style.CODEC).copyOnDeath().syncWith(Style.STREAM_CODEC, AttachmentSyncPredicate.all()));

	private BandStyle() {
	}

	public static Style get(Player player) {
		Style style = player.getAttached(STYLE);
		return style == null ? Style.NONE : style;
	}

	/** The glow {@code player}'s band shows (RGB): their pick, or the band's own. */
	public static int glow(Player player) {
		int glow = get(player).glow();
		return glow != 0 ? glow : XenoformState.rival(player) ? RIVAL_RED : GREEN;
	}

	/** The band screen's Looks tab: {@code what} is look (arg form), glow, plate or spikes. True if it changed anything. */
	static boolean act(ServerPlayer player, String what, String arg) {
		Style s = get(player);
		Style next = switch (what) {
			case "look" -> XenoformState.band(player).has(arg) ? s.withLook(arg, s.look(arg).next()) : null;
			case "glow" -> {
				int i = 0;
				for (int k = 0; k < GLOWS.length; k++) {
					if (GLOWS[k] == glow(player)) {
						i = k + 1;
					}
				}
				yield new Style(s.looks(), GLOWS[i % GLOWS.length], s.plate(), s.spikes());
			}
			case "plate" -> new Style(s.looks(), s.glow(), PLATES.get((Math.max(0, PLATES.indexOf(s.plate())) + 1) % PLATES.size()), s.spikes());
			case "spikes" -> new Style(s.looks(), s.glow(), s.plate(), !s.spikes());
			default -> null;
		};
		if (next == null) {
			return false;
		}
		player.setAttached(STYLE, next);
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SMITHING_TABLE_USE, SoundSource.PLAYERS, 0.4F, 1.6F);
		return true;
	}
}
