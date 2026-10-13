package com.jaymospowers.power.xenoform;

import com.jaymospowers.JaymosPowers;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/**
 * Xenoform's attachments (all declared here, so {@code JaymosPowers.registerAttachments()} loads this one class):
 * <ul>
 * <li>{@link #BAND}: the Xeno-Band itself. Saved, kept through death, synced to its owner: which aliens it holds and
 * which one is dialled.</li>
 * <li>{@link #FORM}: the alien the player is right now (a {@link XenoForm#id}), synced to everyone so every client draws
 * it. Not saved: a relog turns you back (the server restores your own health on the way out).</li>
 * <li>{@link #HUD}: the owner's HUD: the form's timer, the band's recharge, the four moves' cooldowns and the scan.</li>
 * </ul>
 */
public final class XenoformState {
	/** The band: the aliens unlocked (form ids, in the order they came) and the one dialled (an index into them). */
	public record Band(List<String> unlocked, int dial) {
		public static final Band NONE = new Band(List.of(), 0);
		public static final Codec<Band> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.STRING.listOf().optionalFieldOf("unlocked", List.of()).forGetter(Band::unlocked),
				Codec.INT.optionalFieldOf("dial", 0).forGetter(Band::dial))
			.apply(i, Band::new));
		public static final StreamCodec<ByteBuf, Band> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), Band::unlocked,
			ByteBufCodecs.VAR_INT, Band::dial,
			Band::new);

		public boolean has(String form) {
			return unlocked.contains(form);
		}

		/** The dialled alien's id, or null for an empty band. */
		public @Nullable String dialled() {
			return unlocked.isEmpty() ? null : unlocked.get(Math.floorMod(dial, unlocked.size()));
		}

		Band withDial(int value) {
			return new Band(unlocked, unlocked.isEmpty() ? 0 : Math.floorMod(value, unlocked.size()));
		}

		Band plus(String form) {
			if (has(form)) {
				return this;
			}
			List<String> list = new ArrayList<>(unlocked);
			list.add(form);
			return new Band(List.copyOf(list), dial);
		}
	}

	/**
	 * What the owner's HUD shows. {@code formTicks}/{@code formTotal}: the time left in the form; {@code recharge}/
	 * {@code rechargeTotal}: the band's recharge; {@code until}/{@code length}: each move's cooldown end (game time) and
	 * length; {@code scan}/{@code scanTotal}: the scan's progress; {@code scanForm}: the alien being scanned ("" for none).
	 */
	public record Hud(int formTicks, int formTotal, int recharge, int rechargeTotal, long[] until, int[] length, int scan, int scanTotal, String scanForm) {
		public static final int SLOTS = 4;
		public static final Hud NONE = new Hud(0, 0, 0, 0, new long[SLOTS], new int[SLOTS], 0, 0, "");
		public static final StreamCodec<ByteBuf, Hud> STREAM_CODEC = StreamCodec.of((buf, hud) -> {
			ByteBufCodecs.VAR_INT.encode(buf, hud.formTicks);
			ByteBufCodecs.VAR_INT.encode(buf, hud.formTotal);
			ByteBufCodecs.VAR_INT.encode(buf, hud.recharge);
			ByteBufCodecs.VAR_INT.encode(buf, hud.rechargeTotal);
			for (int i = 0; i < SLOTS; i++) {
				ByteBufCodecs.VAR_LONG.encode(buf, hud.until[i]);
				ByteBufCodecs.VAR_INT.encode(buf, hud.length[i]);
			}
			ByteBufCodecs.VAR_INT.encode(buf, hud.scan);
			ByteBufCodecs.VAR_INT.encode(buf, hud.scanTotal);
			ByteBufCodecs.STRING_UTF8.encode(buf, hud.scanForm);
		}, buf -> {
			int formTicks = ByteBufCodecs.VAR_INT.decode(buf);
			int formTotal = ByteBufCodecs.VAR_INT.decode(buf);
			int recharge = ByteBufCodecs.VAR_INT.decode(buf);
			int rechargeTotal = ByteBufCodecs.VAR_INT.decode(buf);
			long[] until = new long[SLOTS];
			int[] length = new int[SLOTS];
			for (int i = 0; i < SLOTS; i++) {
				until[i] = ByteBufCodecs.VAR_LONG.decode(buf);
				length[i] = ByteBufCodecs.VAR_INT.decode(buf);
			}
			return new Hud(formTicks, formTotal, recharge, rechargeTotal, until, length, ByteBufCodecs.VAR_INT.decode(buf),
				ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.STRING_UTF8.decode(buf));
		});

		public long cooldownLeft(int slot, long now) {
			return Math.max(0L, until[slot] - now);
		}

		boolean same(Hud other) {
			return formTicks == other.formTicks && formTotal == other.formTotal && recharge == other.recharge && rechargeTotal == other.rechargeTotal
				&& java.util.Arrays.equals(until, other.until) && java.util.Arrays.equals(length, other.length) && scan == other.scan
				&& scanTotal == other.scanTotal && scanForm.equals(other.scanForm);
		}
	}

	public static final AttachmentType<Band> BAND = AttachmentRegistry.create(JaymosPowers.id("xenoform_band"),
		builder -> builder.persistent(Band.CODEC).copyOnDeath().syncWith(Band.STREAM_CODEC, AttachmentSyncPredicate.targetOnly()));
	public static final AttachmentType<String> FORM = AttachmentRegistry.create(JaymosPowers.id("xenoform_form"),
		builder -> builder.syncWith(ByteBufCodecs.STRING_UTF8, AttachmentSyncPredicate.all()));
	public static final AttachmentType<Hud> HUD = AttachmentRegistry.create(JaymosPowers.id("xenoform_hud"),
		builder -> builder.syncWith(Hud.STREAM_CODEC, AttachmentSyncPredicate.targetOnly()));

	private XenoformState() {
	}

	public static Band band(Player player) {
		Band band = player.getAttached(BAND);
		return band == null ? Band.NONE : band;
	}

	public static Hud hud(Player player) {
		Hud hud = player.getAttached(HUD);
		return hud == null ? Hud.NONE : hud;
	}

	/** The alien this player is right now, or null (both sides). */
	public static @Nullable XenoForm form(Player player) {
		return XenoForms.get(player.getAttached(FORM));
	}

	public static boolean transformed(Player player) {
		return player.hasAttached(FORM);
	}
}
