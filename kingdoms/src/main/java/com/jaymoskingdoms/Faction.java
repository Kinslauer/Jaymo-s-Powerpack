package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.ChunkPos;

/**
 * A faction: a kingdom, a fiefdom, a village or a barbarian camp ({@link FactionType}). Its name and colour, its seat
 * (a village's bell, or a camp's fire), its leader (a king, a lord, a village elder or a chief, by entity UUID), its
 * members with their rank and standing, its treasury (emeralds), its land (claimed chunks), its relations with other
 * factions, and its {@link Holdings} (storage, production survey, personnel). Immutable: change it with the
 * {@code with...} methods and store it back with {@link Factions#put} (or {@link Factions#update}, which re-reads the
 * stored copy first).
 *
 * <p>{@code overlord}: the faction this one is a vassal of (-1 if none). {@code fallen}: in ruin (no leader, no land).
 * {@code successionAt}: a successor is on the way (game time), or -1. Saved under the 0.2 field names ({@code capital},
 * {@code king}), so 0.2 kingdoms load as {@link FactionType#KINGDOM}s.
 */
public record Faction(int id, FactionType type, String name, DyeColor color, BlockPos seat, Optional<UUID> leader, Map<UUID, Member> members,
	long treasury, List<Long> claims, int overlord, boolean fallen, long successionAt, Map<String, Relation> relations, Holdings holdings) {

	/** A member: their rank in this faction (see {@link Rank}) and their standing with it (earned in its service). */
	public record Member(int rank, int standing) {
		public static final Codec<Member> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.INT.fieldOf("rank").forGetter(Member::rank),
				Codec.INT.fieldOf("standing").forGetter(Member::standing))
			.apply(i, Member::new));
	}

	/**
	 * What a faction has and who works for it. {@code storage}: its containers (chests, barrels) in the Overworld;
	 * {@code depot}: where its workers stand to load and unload (the storehouse door), if it has one. {@code lastDay}:
	 * the last day it produced (see {@link Production}). {@code population}, {@code farms}, {@code forest}: the last
	 * survey of its land (villagers, crop blocks, and the share of wooded ground, 0-100). {@code roster}: its folk by
	 * role name ({@link Folk#roleName}), leader excluded; {@code vacancies}: the dead waiting to be replaced;
	 * {@code nextHire}: game time of the next replacement.
	 */
	public record Holdings(List<BlockPos> storage, Optional<BlockPos> depot, long lastDay, int population, int farms, int forest,
		Map<String, Integer> roster, Map<String, Integer> vacancies, long nextHire) {
		public static final Holdings EMPTY = new Holdings(List.of(), Optional.empty(), -1L, 0, 0, 50, Map.of(), Map.of(), 0L);

		public static final Codec<Holdings> CODEC = RecordCodecBuilder.create(i -> i.group(
				BlockPos.CODEC.listOf().optionalFieldOf("storage", List.of()).forGetter(Holdings::storage),
				BlockPos.CODEC.optionalFieldOf("depot").forGetter(Holdings::depot),
				Codec.LONG.optionalFieldOf("last_day", -1L).forGetter(Holdings::lastDay),
				Codec.INT.optionalFieldOf("population", 0).forGetter(Holdings::population),
				Codec.INT.optionalFieldOf("farms", 0).forGetter(Holdings::farms),
				Codec.INT.optionalFieldOf("forest", 50).forGetter(Holdings::forest),
				Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("roster", Map.of()).forGetter(Holdings::roster),
				Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("vacancies", Map.of()).forGetter(Holdings::vacancies),
				Codec.LONG.optionalFieldOf("next_hire", 0L).forGetter(Holdings::nextHire))
			.apply(i, Holdings::new));

		public Holdings withStorage(List<BlockPos> storage, Optional<BlockPos> depot) {
			return new Holdings(List.copyOf(storage), depot, this.lastDay, this.population, this.farms, this.forest, this.roster, this.vacancies,
				this.nextHire);
		}

		public Holdings withLastDay(long lastDay) {
			return new Holdings(this.storage, this.depot, lastDay, this.population, this.farms, this.forest, this.roster, this.vacancies, this.nextHire);
		}

		public Holdings withSurvey(int population, int farms, int forest) {
			return new Holdings(this.storage, this.depot, this.lastDay, population, farms, forest, this.roster, this.vacancies, this.nextHire);
		}

		public Holdings withRoster(Map<String, Integer> roster) {
			return new Holdings(this.storage, this.depot, this.lastDay, this.population, this.farms, this.forest, Map.copyOf(roster), this.vacancies,
				this.nextHire);
		}

		public Holdings withVacancies(Map<String, Integer> vacancies) {
			return new Holdings(this.storage, this.depot, this.lastDay, this.population, this.farms, this.forest, this.roster, Map.copyOf(vacancies),
				this.nextHire);
		}

		public Holdings withNextHire(long nextHire) {
			return new Holdings(this.storage, this.depot, this.lastDay, this.population, this.farms, this.forest, this.roster, this.vacancies, nextHire);
		}

		/** How many of {@code role} it has (alive, as far as it knows). */
		public int count(int role) {
			return this.roster.getOrDefault(Folk.roleName(role), 0);
		}

		/** All its folk (leader excluded). */
		public int staff() {
			return this.roster.values().stream().mapToInt(Integer::intValue).sum();
		}

		public int vacant() {
			return this.vacancies.values().stream().mapToInt(Integer::intValue).sum();
		}

		/** {@code role}'s count shifted by {@code by} (never below 0). */
		public Holdings adjust(int role, int by) {
			Map<String, Integer> roster = new HashMap<>(this.roster);
			roster.merge(Folk.roleName(role), by, Integer::sum);
			roster.values().removeIf(n -> n <= 0);
			return this.withRoster(roster);
		}

		/** {@code role}'s vacancies shifted by {@code by} (never below 0). */
		public Holdings adjustVacancy(int role, int by) {
			Map<String, Integer> vacancies = new HashMap<>(this.vacancies);
			vacancies.merge(Folk.roleName(role), by, Integer::sum);
			vacancies.values().removeIf(n -> n <= 0);
			return this.withVacancies(vacancies);
		}
	}

	public static final Codec<Faction> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.INT.fieldOf("id").forGetter(Faction::id),
			FactionType.CODEC.optionalFieldOf("type", FactionType.KINGDOM).forGetter(Faction::type),
			Codec.STRING.fieldOf("name").forGetter(Faction::name),
			DyeColor.CODEC.fieldOf("color").forGetter(Faction::color),
			BlockPos.CODEC.fieldOf("capital").forGetter(Faction::seat),
			UUIDUtil.CODEC.optionalFieldOf("king").forGetter(Faction::leader),
			Codec.unboundedMap(UUIDUtil.STRING_CODEC, Member.CODEC).fieldOf("members").forGetter(Faction::members),
			Codec.LONG.fieldOf("treasury").forGetter(Faction::treasury),
			Codec.LONG.listOf().fieldOf("claims").forGetter(Faction::claims),
			Codec.INT.optionalFieldOf("overlord", -1).forGetter(Faction::overlord),
			Codec.BOOL.optionalFieldOf("fallen", false).forGetter(Faction::fallen),
			Codec.LONG.optionalFieldOf("succession_at", -1L).forGetter(Faction::successionAt),
			Codec.unboundedMap(Codec.STRING, Relation.CODEC).optionalFieldOf("relations", Map.of()).forGetter(Faction::relations),
			Holdings.CODEC.optionalFieldOf("holdings", Holdings.EMPTY).forGetter(Faction::holdings))
		.apply(i, Faction::new));

	/** A new faction: no leader, members, treasury, relations or holdings yet. */
	public static Faction create(int id, FactionType type, String name, DyeColor color, BlockPos seat, List<Long> claims) {
		return new Faction(id, type, name, color, seat, Optional.empty(), Map.of(), 0L, List.copyOf(claims), -1, false, -1L, Map.of(), Holdings.EMPTY);
	}

	public boolean claims(ChunkPos chunk) {
		return !this.fallen && this.claims.contains(chunk.pack());
	}

	public boolean is(FactionType type) {
		return this.type == type;
	}

	/** The colour its folk wear: its own, or for a fiefdom its overlord's. */
	public DyeColor styleColor(MinecraftServer server) {
		if (this.type == FactionType.FIEFDOM && this.overlord >= 0) {
			Faction overlord = Factions.get(server, this.overlord);
			if (overlord != null) {
				return overlord.color;
			}
		}
		return this.color;
	}

	/** Its stored relation with {@code other} (neutral when none is kept). See {@link Relations#between} for the full rule. */
	public Relation relation(int other) {
		return this.relations.getOrDefault(Integer.toString(other), Relation.NEUTRAL);
	}

	public Faction withType(FactionType type) {
		return new Faction(this.id, type, this.name, this.color, this.seat, this.leader, this.members, this.treasury, this.claims, this.overlord,
			this.fallen, this.successionAt, this.relations, this.holdings);
	}

	public Faction withLeader(Optional<UUID> leader) {
		return new Faction(this.id, this.type, this.name, this.color, this.seat, leader, this.members, this.treasury, this.claims, this.overlord,
			this.fallen, this.successionAt, this.relations, this.holdings);
	}

	public Faction withMember(UUID player, Member member) {
		Map<UUID, Member> members = new HashMap<>(this.members);
		if (member == null) {
			members.remove(player);
		} else {
			members.put(player, member);
		}
		return new Faction(this.id, this.type, this.name, this.color, this.seat, this.leader, Map.copyOf(members), this.treasury, this.claims,
			this.overlord, this.fallen, this.successionAt, this.relations, this.holdings);
	}

	public Faction withTreasury(long treasury) {
		return new Faction(this.id, this.type, this.name, this.color, this.seat, this.leader, this.members, treasury, this.claims, this.overlord,
			this.fallen, this.successionAt, this.relations, this.holdings);
	}

	public Faction withClaims(List<Long> claims) {
		return new Faction(this.id, this.type, this.name, this.color, this.seat, this.leader, this.members, this.treasury, List.copyOf(claims),
			this.overlord, this.fallen, this.successionAt, this.relations, this.holdings);
	}

	public Faction withOverlord(int overlord) {
		return new Faction(this.id, this.type, this.name, this.color, this.seat, this.leader, this.members, this.treasury, this.claims, overlord,
			this.fallen, this.successionAt, this.relations, this.holdings);
	}

	public Faction withFallen(boolean fallen) {
		return new Faction(this.id, this.type, this.name, this.color, this.seat, this.leader, this.members, this.treasury,
			fallen ? List.of() : this.claims, this.overlord, fallen, this.successionAt, this.relations, this.holdings);
	}

	public Faction withSuccessionAt(long at) {
		return new Faction(this.id, this.type, this.name, this.color, this.seat, this.leader, this.members, this.treasury, this.claims, this.overlord,
			this.fallen, at, this.relations, this.holdings);
	}

	/** Its relation with {@code other} set (one side only: {@link Relations#set} keeps both sides in step). */
	public Faction withRelation(int other, Relation relation) {
		Map<String, Relation> relations = new HashMap<>(this.relations);
		if (relation == Relation.NEUTRAL) {
			relations.remove(Integer.toString(other));
		} else {
			relations.put(Integer.toString(other), relation);
		}
		return new Faction(this.id, this.type, this.name, this.color, this.seat, this.leader, this.members, this.treasury, this.claims, this.overlord,
			this.fallen, this.successionAt, Map.copyOf(relations), this.holdings);
	}

	public Faction withHoldings(Holdings holdings) {
		return new Faction(this.id, this.type, this.name, this.color, this.seat, this.leader, this.members, this.treasury, this.claims, this.overlord,
			this.fallen, this.successionAt, this.relations, holdings);
	}

	public Member member(UUID player) {
		return this.members.get(player);
	}
}
