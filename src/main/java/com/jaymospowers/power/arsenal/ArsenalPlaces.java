package com.jaymospowers.power.arsenal;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.power.zfighter.ZFighterSites;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * (0.13.36) The places an Arsenal user remembers, for the standing portals ({@link ArsenalStanding}). Marks: Sneak + R
 * marks where you stand, up to {@link #MAX} (the oldest goes when full). Recent: where you arrived after every
 * dimension change and every Arsenal teleport, up to {@link #MAX}, merged when within {@link #MERGE_RADIUS} blocks of
 * an entry already there. Both are saved, kept through death and synced to their owner for the menu. Newest first.
 * Needs B unlocked (as Z and X do). Nothing in the Arsenal pocket or the time chamber is remembered.
 */
public final class ArsenalPlaces {
	public static final int MAX = 8;
	/** A new arrival this close to an older one in the same dimension replaces it. */
	static final double MERGE_RADIUS = 32.0;

	/** One place: its dimension's id, where, which way you faced, and the biome's id (named by the menu). */
	public record Place(String dimension, double x, double y, double z, float yaw, String biome) {
		public static final Codec<Place> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.STRING.fieldOf("dimension").forGetter(Place::dimension),
				Codec.DOUBLE.fieldOf("x").forGetter(Place::x),
				Codec.DOUBLE.fieldOf("y").forGetter(Place::y),
				Codec.DOUBLE.fieldOf("z").forGetter(Place::z),
				Codec.FLOAT.fieldOf("yaw").forGetter(Place::yaw),
				Codec.STRING.fieldOf("biome").forGetter(Place::biome))
			.apply(i, Place::new));
		public static final StreamCodec<ByteBuf, Place> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.STRING_UTF8, Place::dimension,
			ByteBufCodecs.DOUBLE, Place::x,
			ByteBufCodecs.DOUBLE, Place::y,
			ByteBufCodecs.DOUBLE, Place::z,
			ByteBufCodecs.FLOAT, Place::yaw,
			ByteBufCodecs.STRING_UTF8, Place::biome,
			Place::new);

		/** "Plains (120, 64, -33)". */
		public Component label() {
			String id = biome;
			int colon = id.indexOf(':');
			String key = "biome." + (colon < 0 ? "minecraft" : id.substring(0, colon)) + "." + id.substring(colon + 1).replace('/', '.');
			return Component.translatable(key).append(Component.literal(" (" + (int)Math.floor(x) + ", " + (int)Math.floor(y) + ", " + (int)Math.floor(z) + ")"));
		}
	}

	public static final AttachmentType<List<Place>> MARKS = AttachmentRegistry.create(JaymosPowers.id("arsenal_marks"),
		builder -> builder.persistent(Place.CODEC.listOf()).copyOnDeath()
			.syncWith(Place.STREAM_CODEC.apply(ByteBufCodecs.list()), AttachmentSyncPredicate.targetOnly()));
	public static final AttachmentType<List<Place>> RECENT = AttachmentRegistry.create(JaymosPowers.id("arsenal_recent"),
		builder -> builder.persistent(Place.CODEC.listOf()).copyOnDeath()
			.syncWith(Place.STREAM_CODEC.apply(ByteBufCodecs.list()), AttachmentSyncPredicate.targetOnly()));

	/** The dimension each Arsenal user was last seen in, to notice a change. Server memory only. */
	private static final Map<UUID, ResourceKey<Level>> LAST_DIMENSION = new HashMap<>();

	private ArsenalPlaces() {
	}

	static void init() {
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> LAST_DIMENSION.remove(handler.player.getUUID()));
	}

	/** Whether the places (and the standing portals) work for this player yet: once B is (both sides). */
	public static boolean unlocked(Player player) {
		return ArsenalCannons.unlocked(player);
	}

	/** Sneak held and B unlocked: R and G mean the places instead of Reach and Return. */
	static boolean sneakKey(ServerPlayer player) {
		return player.isShiftKeyDown() && unlocked(player);
	}

	/** Not the Arsenal pocket or the time chamber: nothing there is remembered, and no portal opens from or into them. */
	public static boolean allowed(ResourceKey<Level> dimension) {
		return dimension != ArsenalPocket.POCKET && dimension != ZFighterSites.TIME_CHAMBER;
	}

	public static List<Place> marks(Player player) {
		return player.getAttachedOrElse(MARKS, List.of());
	}

	public static List<Place> recent(Player player) {
		return player.getAttachedOrElse(RECENT, List.of());
	}

	/** The place the player stands in now. */
	static Place here(ServerPlayer player) {
		ServerLevel level = player.level();
		String biome = level.getBiome(player.blockPosition()).unwrapKey().map(key -> key.identifier().toString()).orElse("minecraft:plains");
		return new Place(level.dimension().identifier().toString(), player.getX(), player.getY(), player.getZ(), player.getYRot(), biome);
	}

	/** Sneak + R: mark where you stand. */
	static void mark(ServerPlayer player) {
		ServerLevel level = player.level();
		if (!allowed(level.dimension())) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.arsenal.place_nowhere").withStyle(ChatFormatting.GRAY));
			return;
		}
		Place place = here(player);
		List<Place> marks = new ArrayList<>(marks(player));
		marks.add(0, place);
		while (marks.size() > MAX) {
			marks.remove(marks.size() - 1);
		}
		player.setAttached(MARKS, List.copyOf(marks));
		player.sendOverlayMessage(Component.translatable("message.jaymospowers.arsenal.place_marked", place.label()).withStyle(ChatFormatting.LIGHT_PURPLE));
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 0.6F, 1.5F);
		level.sendParticles(ParticleTypes.REVERSE_PORTAL, player.getX(), player.getY() + 1.0, player.getZ(), 20, 0.3, 0.7, 0.3, 0.05);
	}

	/** Where the player has just arrived: it goes to the top of Recent (replacing a neighbour within the merge radius). */
	static void arrived(ServerPlayer player) {
		if (!allowed(player.level().dimension())) {
			return;
		}
		Place place = here(player);
		List<Place> recent = new ArrayList<>(recent(player));
		recent.removeIf(old -> old.dimension().equals(place.dimension())
			&& (old.x() - place.x()) * (old.x() - place.x()) + (old.y() - place.y()) * (old.y() - place.y())
				+ (old.z() - place.z()) * (old.z() - place.z()) <= MERGE_RADIUS * MERGE_RADIUS);
		recent.add(0, place);
		while (recent.size() > MAX) {
			recent.remove(recent.size() - 1);
		}
		player.setAttached(RECENT, List.copyOf(recent));
		LAST_DIMENSION.put(player.getUUID(), player.level().dimension());
	}

	/** Every tick: a change of dimension (any way at all) is an arrival. */
	static void tick(ServerPlayer player) {
		ResourceKey<Level> now = player.level().dimension();
		ResourceKey<Level> before = LAST_DIMENSION.put(player.getUUID(), now);
		if (before != null && before != now && player.isAlive()) {
			arrived(player);
		}
	}

	static void forget(ServerPlayer player) {
		LAST_DIMENSION.remove(player.getUUID());
	}
}
