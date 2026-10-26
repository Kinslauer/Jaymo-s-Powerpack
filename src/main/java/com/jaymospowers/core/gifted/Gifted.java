package com.jaymospowers.core.gifted;

import com.jaymospowers.JaymosPowers;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/**
 * (0.17.1) <b>Gifted folk</b>: villagers (and any other mob that isn't a player) who hold a power. Andy: "villagers should
 * be able to acquire and use powers as well! This is the only way that encountering a stormborn purist king makes sense."
 * <p>
 * A player's power runs off their keys and their client, so a villager can't hold one the same way. A gifted villager
 * holds a {@link Gift} instead, and fights with that power's {@link GiftKit} (a few moves of its own), picked by
 * {@link GiftedAI}. Who awakens and to which power is Jaymo's Kingdoms' business (it knows the villagers' blood, families
 * and lives): it calls {@link #give} and reads {@link #power} and {@link #corrupted} by reflection, so neither mod needs
 * the other. Saved, and synced to every client (Kingdoms' MCA screen shows it to those who know them).
 */
public final class Gifted {
	/** How a gifted villager fights (from their temperament, Kingdoms says): straight in, as it comes, or from afar. */
	public static final int BOLD = 0, STEADY = 1, WARY = 2;

	/** Their power, whether it's been corrupted, how they fight ({@link #BOLD}...), and how hard they've used it (moves used). */
	public record Gift(Identifier power, boolean corrupted, int style, int strain) {
		public static final Codec<Gift> CODEC = RecordCodecBuilder.create(i -> i.group(
				Identifier.CODEC.fieldOf("power").forGetter(Gift::power),
				Codec.BOOL.optionalFieldOf("corrupted", false).forGetter(Gift::corrupted),
				Codec.INT.optionalFieldOf("style", STEADY).forGetter(Gift::style),
				Codec.INT.optionalFieldOf("strain", 0).forGetter(Gift::strain))
			.apply(i, Gift::new));
		public static final StreamCodec<ByteBuf, Gift> STREAM_CODEC = StreamCodec.composite(
			Identifier.STREAM_CODEC, Gift::power,
			ByteBufCodecs.BOOL, Gift::corrupted,
			ByteBufCodecs.VAR_INT, Gift::style,
			ByteBufCodecs.VAR_INT, Gift::strain,
			Gift::new);

		Gift strained() {
			return new Gift(this.power, this.corrupted, this.style, this.strain + 1);
		}

		Gift corrupt() {
			return new Gift(this.power, true, this.style, this.strain);
		}
	}

	public static final AttachmentType<Gift> GIFT = AttachmentRegistry.create(JaymosPowers.id("gift"),
		builder -> builder.persistent(Gift.CODEC).syncWith(Gift.STREAM_CODEC, AttachmentSyncPredicate.all()));

	private static final Map<Identifier, GiftKit> KITS = new LinkedHashMap<>();

	private Gifted() {
	}

	/** Each power's kit registers here, from its own {@code init}. */
	public static void register(GiftKit kit) {
		if (KITS.putIfAbsent(kit.power(), kit) != null) {
			throw new IllegalStateException("gift kit registered twice: " + kit.power());
		}
	}

	static @Nullable GiftKit kit(Identifier power) {
		return KITS.get(power);
	}

	/** The powers a villager can be gifted (their id paths: "wind", "cryo"...). */
	public static List<String> giftable() {
		List<String> keys = new ArrayList<>();
		for (Identifier id : KITS.keySet()) {
			keys.add(id.getPath());
		}
		return keys;
	}

	private static Identifier id(String power) {
		return power.contains(":") ? Identifier.parse(power) : JaymosPowers.id(power);
	}

	/**
	 * Gives {@code entity} the power {@code power} ("wind"...), fighting as {@code style}. False for a player, a mob that
	 * can't walk to a fight, or a power with no kit. Called by Kingdoms (by reflection) and {@code /power gift}.
	 */
	public static boolean give(Entity entity, String power, int style) {
		Identifier id = id(power);
		if (entity instanceof Player || !(entity instanceof PathfinderMob mob) || !KITS.containsKey(id)) {
			return false;
		}
		mob.setAttached(GIFT, new Gift(id, false, Math.clamp(style, BOLD, WARY), 0));
		GiftedAI.track(mob);
		return true;
	}

	/** Takes {@code entity}'s gift away. */
	public static void take(Entity entity) {
		entity.removeAttached(GIFT);
	}

	public static @Nullable Gift of(Entity entity) {
		return entity instanceof Player ? null : entity.getAttached(GIFT);
	}

	/** The id path of {@code entity}'s gift ("wind"...), or null. Either side. */
	public static @Nullable String power(Entity entity) {
		Gift gift = of(entity);
		return gift == null ? null : gift.power().getPath();
	}

	/** Whether {@code entity}'s gift has been corrupted. Either side. */
	public static boolean corrupted(Entity entity) {
		Gift gift = of(entity);
		return gift != null && gift.corrupted();
	}

	/** Corrupts {@code entity}'s gift at once (as long use does in time, {@link GiftedAI}); false without one, or if it already is. */
	public static boolean corrupt(Entity entity) {
		return entity instanceof PathfinderMob mob && GiftedAI.corrupt(mob);
	}

	/**
	 * A gifted ruler's ultimate on {@code foe}, when their throne room is attacked (Kingdoms calls it, by reflection,
	 * whenever a foe is at court; {@link GiftedAI#unleash} keeps it to once in a long while). False if it didn't go off.
	 */
	public static boolean unleash(Entity entity, Entity foe) {
		return entity instanceof PathfinderMob mob && foe instanceof net.minecraft.world.entity.LivingEntity living && GiftedAI.unleash(mob, living);
	}

	static void set(PathfinderMob mob, Gift gift) {
		mob.setAttached(GIFT, gift);
	}
}
