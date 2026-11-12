package com.jaymospowers.power.web;

import com.jaymospowers.JaymosPowers;
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
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/**
 * The symbiote remembers who hurt its host. Up to {@link #MAX_ENTRIES} grudges (a player, by UUID and name, or a kind of
 * mob), each with when it was last hurt and how much it dealt in all. A grudge is made by a hit of at least
 * {@link #MIN_HIT} damage, or by the blow that downs or kills the host; the weakest (then the oldest) is dropped to make
 * room. It nags by whisper when a holder is near, sours slowly while old grudges sit unpaid, and lets go when the host
 * kills the holder (a player, or any mob of that kind): trust rises. Grudges fade after {@link #FADE_TICKS}. The host is
 * never a grudge, nor their own pets. Saved and synced to the owner (the Status page lists them).
 */
public final class WebGrudges {
	// ================================================================================================================
	// Tuning
	public static final int MAX_ENTRIES = 5;
	/** A hit this hard (after armour) makes a grudge; the blow that downs or kills counts at least this much. */
	static final float MIN_HIT = 4.0F;
	/** Three in-game days. */
	static final long FADE_TICKS = 72000L;
	/** A holder this close (blocks) is nagged about, at most once per {@code NAG_GAP}; it looks every {@code CHECK} ticks. */
	static final double NEAR_RANGE = 24.0;
	static final long NAG_GAP = 2400L;
	static final int CHECK = 20, LINES = 3;
	/** A grudge unpaid this long starts to sour it: a point of stress every {@code SOUR_EVERY} ticks, never past {@code SOUR_STRESS_CAP}. */
	static final long SOUR_AFTER = 6000L;
	static final int SOUR_EVERY = 1200, SOUR_STEP = 1, SOUR_STRESS_CAP = 40;
	/** Trust gained when one is settled. */
	static final int TRUST_PAID = 4;

	/** A grudge: a player ({@code id} their UUID) or a kind of mob ({@code id} its entity type id). */
	public record Entry(boolean player, String id, String name, long at, float damage) {
		public static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.BOOL.optionalFieldOf("player", false).forGetter(Entry::player),
				Codec.STRING.fieldOf("id").forGetter(Entry::id),
				Codec.STRING.optionalFieldOf("name", "").forGetter(Entry::name),
				Codec.LONG.optionalFieldOf("at", 0L).forGetter(Entry::at),
				Codec.FLOAT.optionalFieldOf("damage", 0.0F).forGetter(Entry::damage))
			.apply(i, Entry::new));
		public static final StreamCodec<ByteBuf, Entry> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.BOOL, Entry::player,
			ByteBufCodecs.STRING_UTF8, Entry::id,
			ByteBufCodecs.STRING_UTF8, Entry::name,
			ByteBufCodecs.VAR_LONG, Entry::at,
			ByteBufCodecs.FLOAT, Entry::damage,
			Entry::new);

		/** Who or what it is, for a line or the Status page. */
		public Component display() {
			if (player) {
				return Component.literal(name);
			}
			Identifier key = Identifier.tryParse(id);
			EntityType<?> type = key == null ? null : BuiltInRegistries.ENTITY_TYPE.getOptional(key).orElse(null);
			return type == null ? Component.literal(id) : type.getDescription();
		}

		boolean matches(Entity other) {
			return player ? other instanceof Player p && p.getUUID().toString().equals(id)
				: !(other instanceof Player) && BuiltInRegistries.ENTITY_TYPE.getKey(other.getType()).toString().equals(id);
		}
	}

	/** Everything it holds against anyone. */
	public record Book(List<Entry> entries) {
		public static final Book EMPTY = new Book(List.of());
		public static final Codec<Book> CODEC = Entry.CODEC.listOf().xmap(Book::new, Book::entries);
		public static final StreamCodec<ByteBuf, Book> STREAM_CODEC = Entry.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_ENTRIES))
			.map(Book::new, Book::entries);
	}

	public static final AttachmentType<Book> BOOK = AttachmentRegistry.create(JaymosPowers.id("web_grudges"),
		builder -> builder.persistent(Book.CODEC).copyOnDeath().syncWith(Book.STREAM_CODEC, AttachmentSyncPredicate.targetOnly()));

	/** When each host was last nagged (game time). In memory. */
	private static final Map<UUID, Long> NAGGED = new HashMap<>();

	private WebGrudges() {
	}

	public static Book book(Player player) {
		Book book = player.getAttached(BOOK);
		return book == null ? Book.EMPTY : book;
	}

	static void register() {
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damageTaken, blocked) -> {
			if (entity instanceof ServerPlayer host && !blocked && damageTaken >= MIN_HIT && WebSymbiote.isBonded(host)) {
				remember(host, source, damageTaken);
			}
		});
		// (the blow that downs or kills the host: it never reaches AFTER_DAMAGE)
		ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> {
			if (entity instanceof ServerPlayer host && WebSymbiote.isBonded(host)) {
				remember(host, source, Math.max(MIN_HIT, amount));
			}
			return true;
		});
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (source.getEntity() instanceof ServerPlayer host && host != entity && WebSymbiote.isBonded(host)) {
				settle(host, entity);
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				if (player.tickCount % CHECK == 0 && player.isAlive() && !player.isSpectator() && WebSymbiote.isBonded(player)) {
					tick(player);
				}
			}
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> NAGGED.remove(handler.player.getUUID()));
	}

	/** Who dealt {@code source} to {@code host}, as a grudge to hold: a player, or a mob kind. Never the host or their pets. */
	private static @Nullable Entry culprit(ServerPlayer host, DamageSource source, float damage, long now) {
		Entity who = source.getEntity();
		if (who instanceof OwnableEntity owned && owned.getOwner() instanceof ServerPlayer owner) {
			who = owner;
		}
		if (who == null || who == host || !(who instanceof LivingEntity)) {
			return null;
		}
		if (who instanceof ServerPlayer player) {
			return new Entry(true, player.getUUID().toString(), player.getGameProfile().name(), now, damage);
		}
		return new Entry(false, BuiltInRegistries.ENTITY_TYPE.getKey(who.getType()).toString(), "", now, damage);
	}

	private static void remember(ServerPlayer host, DamageSource source, float damage) {
		long now = host.level().getGameTime();
		Entry made = culprit(host, source, damage, now);
		if (made == null) {
			return;
		}
		List<Entry> list = new ArrayList<>(book(host).entries());
		for (int i = 0; i < list.size(); i++) {
			Entry old = list.get(i);
			if (old.player == made.player && old.id.equals(made.id)) {
				list.set(i, new Entry(old.player, old.id, made.name.isEmpty() ? old.name : made.name, now, old.damage + made.damage));
				host.setAttached(BOOK, new Book(List.copyOf(list)));
				return;
			}
		}
		if (list.size() >= MAX_ENTRIES) {
			// the weakest goes, the oldest of equals
			Entry weakest = list.get(0);
			for (Entry entry : list) {
				if (entry.damage < weakest.damage || entry.damage == weakest.damage && entry.at < weakest.at) {
					weakest = entry;
				}
			}
			list.remove(weakest);
		}
		list.add(made);
		host.setAttached(BOOK, new Book(List.copyOf(list)));
	}

	/** The host killed {@code victim}: a grudge against it (or its kind) is paid. */
	private static void settle(ServerPlayer host, LivingEntity victim) {
		Book book = book(host);
		for (Entry entry : book.entries()) {
			if (entry.matches(victim)) {
				List<Entry> rest = new ArrayList<>(book.entries());
				rest.remove(entry);
				host.setAttached(BOOK, new Book(List.copyOf(rest)));
				WebSymbioteMind.addTrust(host, TRUST_PAID);
				WebSymbiote.whisper(host, "grudge.paid." + (entry.player ? "player." : "mob.") + (1 + host.getRandom().nextInt(LINES)), entry.display());
				return;
			}
		}
	}

	private static void tick(ServerPlayer player) {
		long now = player.level().getGameTime();
		Book book = book(player);
		if (book.entries().isEmpty()) {
			return;
		}
		List<Entry> kept = new ArrayList<>();
		for (Entry entry : book.entries()) {
			if (now - entry.at < FADE_TICKS) {
				kept.add(entry);
			}
		}
		if (kept.size() != book.entries().size()) {
			player.setAttached(BOOK, new Book(List.copyOf(kept)));
		}
		if (kept.isEmpty()) {
			return;
		}
		// a point of stress now and then while old grudges sit unpaid
		if (player.tickCount % SOUR_EVERY == 0 && WebSymbiote.state(player).stress() < SOUR_STRESS_CAP) {
			for (Entry entry : kept) {
				if (now - entry.at >= SOUR_AFTER) {
					WebSymbiote.addStress(player, SOUR_STEP);
					break;
				}
			}
		}
		Long last = NAGGED.get(player.getUUID());
		if ((last != null && now - last < NAG_GAP) || !WebSymbiote.canSpeak(player, now) || WebSymbioteMind.quiet(player)) {
			return;
		}
		for (Entry entry : kept) {
			if (nearby(player, entry)) {
				NAGGED.put(player.getUUID(), now);
				WebSymbiote.whisper(player, "grudge.near." + (entry.player ? "player." : "mob.") + (1 + player.getRandom().nextInt(LINES)), entry.display());
				return;
			}
		}
	}

	private static boolean nearby(ServerPlayer host, Entry entry) {
		ServerLevel level = host.level();
		if (entry.player) {
			for (ServerPlayer other : level.players()) {
				if (other != host && other.isAlive() && !other.isSpectator() && entry.matches(other) && other.distanceTo(host) <= NEAR_RANGE) {
					return true;
				}
			}
			return false;
		}
		return !level.getEntitiesOfClass(LivingEntity.class, host.getBoundingBox().inflate(NEAR_RANGE),
			other -> other.isAlive() && !(other instanceof Player) && entry.matches(other) && other.distanceTo(host) <= NEAR_RANGE).isEmpty();
	}
}
