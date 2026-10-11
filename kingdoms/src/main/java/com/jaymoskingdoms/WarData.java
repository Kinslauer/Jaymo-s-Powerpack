package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.UnaryOperator;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.MinecraftServer;
import org.jspecify.annotations.Nullable;

/**
 * What's saved of the wars (on the Overworld, with the factions): every war declared or under way with its two sides'
 * scores, the banners being held, and the truces that follow a peace. Immutable records, written back whole.
 */
final class WarData {
	private WarData() {
	}

	/**
	 * One side of a war: its {@code principal} (the realm at the head of it; its vassals fight with it) and what it has
	 * won so far: enemy folk killed, enemy leaders killed, chunks taken, and items carried off from the enemy's storage.
	 */
	record Side(int principal, int kills, int leaders, int chunks, int loot) {
		static final Codec<Side> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.INT.fieldOf("principal").forGetter(Side::principal),
				Codec.INT.optionalFieldOf("kills", 0).forGetter(Side::kills),
				Codec.INT.optionalFieldOf("leaders", 0).forGetter(Side::leaders),
				Codec.INT.optionalFieldOf("chunks", 0).forGetter(Side::chunks),
				Codec.INT.optionalFieldOf("loot", 0).forGetter(Side::loot))
			.apply(i, Side::new));

		static Side of(int principal) {
			return new Side(principal, 0, 0, 0, 0);
		}

		/** Its war score. */
		int score() {
			return this.kills * Wars.SCORE_KILL + this.leaders * Wars.SCORE_LEADER + this.chunks * Wars.SCORE_CHUNK + this.loot / Wars.LOOT_PER_SCORE;
		}

		Side add(int kills, int leaders, int chunks, int loot) {
			return new Side(this.principal, this.kills + kills, this.leaders + leaders, this.chunks + chunks, this.loot + loot);
		}
	}

	/**
	 * A war: its id, the declaring side and the defending side, when it was declared and when the fighting starts (the
	 * warm-up's end), whether it has started, and a peace offer standing (the offering principal, -1 for none, and when).
	 */
	record War(int id, Side attacker, Side defender, long declaredAt, long startsAt, boolean active, int offerFrom, long offerAt) {
		static final Codec<War> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.INT.fieldOf("id").forGetter(War::id),
				Side.CODEC.fieldOf("attacker").forGetter(War::attacker),
				Side.CODEC.fieldOf("defender").forGetter(War::defender),
				Codec.LONG.fieldOf("declared_at").forGetter(War::declaredAt),
				Codec.LONG.fieldOf("starts_at").forGetter(War::startsAt),
				Codec.BOOL.optionalFieldOf("active", false).forGetter(War::active),
				Codec.INT.optionalFieldOf("offer_from", -1).forGetter(War::offerFrom),
				Codec.LONG.optionalFieldOf("offer_at", 0L).forGetter(War::offerAt))
			.apply(i, War::new));

		boolean involves(int principal) {
			return this.attacker.principal() == principal || this.defender.principal() == principal;
		}

		/** The side led by {@code principal} (null if it leads neither). */
		@Nullable Side side(int principal) {
			return this.attacker.principal() == principal ? this.attacker : this.defender.principal() == principal ? this.defender : null;
		}

		/** The side against {@code principal}'s. */
		@Nullable Side other(int principal) {
			return this.attacker.principal() == principal ? this.defender : this.defender.principal() == principal ? this.attacker : null;
		}

		War withSide(Side side) {
			return side.principal() == this.attacker.principal()
				? new War(this.id, side, this.defender, this.declaredAt, this.startsAt, this.active, this.offerFrom, this.offerAt)
				: new War(this.id, this.attacker, side, this.declaredAt, this.startsAt, this.active, this.offerFrom, this.offerAt);
		}

		War withActive(boolean active) {
			return new War(this.id, this.attacker, this.defender, this.declaredAt, this.startsAt, active, this.offerFrom, this.offerAt);
		}

		War withOffer(int from, long at) {
			return new War(this.id, this.attacker, this.defender, this.declaredAt, this.startsAt, this.active, from, at);
		}
	}

	/**
	 * A banner planted in enemy land: where, the faction that will hold the chunk ({@code faction}), the war, the faction
	 * whose land it stands in ({@code target}), whether it stands at the enemy's seat ({@code seat}: holding it takes the
	 * seat), how long it has been held (ticks), and who planted it (empty: an NPC war party).
	 */
	record Banner(BlockPos pos, int faction, int war, int target, boolean seat, long held, Optional<UUID> planter) {
		static final Codec<Banner> CODEC = RecordCodecBuilder.create(i -> i.group(
				BlockPos.CODEC.fieldOf("pos").forGetter(Banner::pos),
				Codec.INT.fieldOf("faction").forGetter(Banner::faction),
				Codec.INT.fieldOf("war").forGetter(Banner::war),
				Codec.INT.fieldOf("target").forGetter(Banner::target),
				Codec.BOOL.optionalFieldOf("seat", false).forGetter(Banner::seat),
				Codec.LONG.optionalFieldOf("held", 0L).forGetter(Banner::held),
				UUIDUtil.CODEC.optionalFieldOf("planter").forGetter(Banner::planter))
			.apply(i, Banner::new));

		Banner withHeld(long held) {
			return new Banner(this.pos, this.faction, this.war, this.target, this.seat, held, this.planter);
		}
	}

	/**
	 * Everything: the wars, the banners, the truces (a pair of principals "a|b", lower id first → game time it ends), the
	 * next war id, and the last day rivalries were stirred.
	 */
	record Book(List<War> wars, List<Banner> banners, Map<String, Long> truces, int nextId, long lastRivalryDay) {
		static final Book EMPTY = new Book(List.of(), List.of(), Map.of(), 1, -1L);

		static final Codec<Book> CODEC = RecordCodecBuilder.create(i -> i.group(
				War.CODEC.listOf().optionalFieldOf("wars", List.of()).forGetter(Book::wars),
				Banner.CODEC.listOf().optionalFieldOf("banners", List.of()).forGetter(Book::banners),
				Codec.unboundedMap(Codec.STRING, Codec.LONG).optionalFieldOf("truces", Map.of()).forGetter(Book::truces),
				Codec.INT.optionalFieldOf("next_id", 1).forGetter(Book::nextId),
				Codec.LONG.optionalFieldOf("last_rivalry_day", -1L).forGetter(Book::lastRivalryDay))
			.apply(i, Book::new));

		Book withWars(List<War> wars) {
			return new Book(List.copyOf(wars), this.banners, this.truces, this.nextId, this.lastRivalryDay);
		}

		Book withBanners(List<Banner> banners) {
			return new Book(this.wars, List.copyOf(banners), this.truces, this.nextId, this.lastRivalryDay);
		}

		Book withTruces(Map<String, Long> truces) {
			return new Book(this.wars, this.banners, Map.copyOf(truces), this.nextId, this.lastRivalryDay);
		}

		Book withNextId(int nextId) {
			return new Book(this.wars, this.banners, this.truces, nextId, this.lastRivalryDay);
		}

		Book withLastRivalryDay(long day) {
			return new Book(this.wars, this.banners, this.truces, this.nextId, day);
		}
	}

	/** The wars, saved on the Overworld (not synced: clients never need it). */
	static final AttachmentType<Book> WARS = AttachmentRegistry.create(JaymosKingdoms.id("wars"), builder -> builder.persistent(Book.CODEC));

	static Book book(MinecraftServer server) {
		return Factions.level(server).getAttachedOrElse(WARS, Book.EMPTY);
	}

	static void put(MinecraftServer server, Book book) {
		Factions.level(server).setAttached(WARS, book);
	}

	static void update(MinecraftServer server, UnaryOperator<Book> change) {
		put(server, change.apply(book(server)));
	}

	static List<War> wars(MinecraftServer server) {
		return book(server).wars();
	}

	static @Nullable War war(MinecraftServer server, int id) {
		for (War war : wars(server)) {
			if (war.id() == id) {
				return war;
			}
		}
		return null;
	}

	/** Replaces war {@code id} with {@code change}'s result (or removes it when that's null). */
	static void updateWar(MinecraftServer server, int id, UnaryOperator<@Nullable War> change) {
		update(server, b -> {
			List<War> wars = new ArrayList<>();
			for (War war : b.wars()) {
				if (war.id() == id) {
					War changed = change.apply(war);
					if (changed != null) {
						wars.add(changed);
					}
				} else {
					wars.add(war);
				}
			}
			return b.withWars(wars);
		});
	}

	static List<Banner> banners(MinecraftServer server) {
		return book(server).banners();
	}

	static void putBanners(MinecraftServer server, List<Banner> banners) {
		update(server, b -> b.withBanners(banners));
	}

	static String pair(int a, int b) {
		return Math.min(a, b) + "|" + Math.max(a, b);
	}

	/** {@code a} and {@code b} are under a truce (game time {@code now}). */
	static boolean truce(MinecraftServer server, int a, int b, long now) {
		Long until = book(server).truces().get(pair(a, b));
		return until != null && until > now;
	}

	static void setTruce(MinecraftServer server, int a, int b, long until) {
		update(server, book -> {
			Map<String, Long> truces = new HashMap<>(book.truces());
			truces.put(pair(a, b), until);
			return book.withTruces(truces);
		});
	}
}
