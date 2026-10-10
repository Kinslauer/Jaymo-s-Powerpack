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
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.ChunkPos;

/**
 * A kingdom: its name and colour, its capital (a village's bell), its ruler (an NPC king, by entity UUID), its
 * members with their rank and standing, its treasury (emeralds) and its land (claimed chunks). Immutable: change it
 * with the {@code with...} methods and store it back with {@link Kingdoms#put}.
 *
 * <p>{@code overlord}: the kingdom this one is a vassal of (-1 if none). {@code fallen}: in ruin (no ruler, no land).
 * {@code successionAt}: a successor is on the way (game time), or -1.
 */
public record Kingdom(int id, String name, DyeColor color, BlockPos capital, Optional<UUID> king, Map<UUID, Member> members, long treasury,
	List<Long> claims, int overlord, boolean fallen, long successionAt) {

	/** A member: their rank in this kingdom (see {@link Rank}) and their standing with it (earned in its service). */
	public record Member(int rank, int standing) {
		public static final Codec<Member> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.INT.fieldOf("rank").forGetter(Member::rank),
				Codec.INT.fieldOf("standing").forGetter(Member::standing))
			.apply(i, Member::new));
	}

	public static final Codec<Kingdom> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.INT.fieldOf("id").forGetter(Kingdom::id),
			Codec.STRING.fieldOf("name").forGetter(Kingdom::name),
			DyeColor.CODEC.fieldOf("color").forGetter(Kingdom::color),
			BlockPos.CODEC.fieldOf("capital").forGetter(Kingdom::capital),
			UUIDUtil.CODEC.optionalFieldOf("king").forGetter(Kingdom::king),
			Codec.unboundedMap(UUIDUtil.STRING_CODEC, Member.CODEC).fieldOf("members").forGetter(Kingdom::members),
			Codec.LONG.fieldOf("treasury").forGetter(Kingdom::treasury),
			Codec.LONG.listOf().fieldOf("claims").forGetter(Kingdom::claims),
			Codec.INT.optionalFieldOf("overlord", -1).forGetter(Kingdom::overlord),
			Codec.BOOL.optionalFieldOf("fallen", false).forGetter(Kingdom::fallen),
			Codec.LONG.optionalFieldOf("succession_at", -1L).forGetter(Kingdom::successionAt))
		.apply(i, Kingdom::new));

	public boolean claims(ChunkPos chunk) {
		return !this.fallen && this.claims.contains(chunk.pack());
	}

	public Kingdom withKing(Optional<UUID> king) {
		return new Kingdom(this.id, this.name, this.color, this.capital, king, this.members, this.treasury, this.claims, this.overlord, this.fallen,
			this.successionAt);
	}

	public Kingdom withMember(UUID player, Member member) {
		Map<UUID, Member> members = new HashMap<>(this.members);
		if (member == null) {
			members.remove(player);
		} else {
			members.put(player, member);
		}
		return new Kingdom(this.id, this.name, this.color, this.capital, this.king, Map.copyOf(members), this.treasury, this.claims, this.overlord,
			this.fallen, this.successionAt);
	}

	public Kingdom withTreasury(long treasury) {
		return new Kingdom(this.id, this.name, this.color, this.capital, this.king, this.members, treasury, this.claims, this.overlord, this.fallen,
			this.successionAt);
	}

	public Kingdom withOverlord(int overlord) {
		return new Kingdom(this.id, this.name, this.color, this.capital, this.king, this.members, this.treasury, this.claims, overlord, this.fallen,
			this.successionAt);
	}

	public Kingdom withFallen(boolean fallen) {
		return new Kingdom(this.id, this.name, this.color, this.capital, this.king, this.members, this.treasury, fallen ? List.of() : this.claims,
			this.overlord, fallen, this.successionAt);
	}

	public Kingdom withSuccessionAt(long at) {
		return new Kingdom(this.id, this.name, this.color, this.capital, this.king, this.members, this.treasury, this.claims, this.overlord, this.fallen,
			at);
	}

	public Member member(UUID player) {
		return this.members.get(player);
	}
}
