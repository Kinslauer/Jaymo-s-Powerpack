package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;

/**
 * Hunter's Road: the <b>kinds</b> of monster ({@code jaymoskingdoms:kind/<kind>} entity type tags): the dead, the
 * corpse-eaters, the brutes (trolls, ogres, cyclops, giants), the beasts, the spirits (wraiths, bog lights), the cursed
 * (werewolves and vampires) and people. The tags hold vanilla's, ours and Alex's Mobs' (any other mod joins by adding its
 * own to a tag). Werewolves and vampires are mostly players and villagers, not entity types, so {@link #is} also reads
 * their state: a vampire (not a dhampir) and a werewolf in its beast form are {@link #CURSED}. Everything that treats a
 * kind alike (the bestiary, the silver sword's sear, the steel sword's edge) asks here and nowhere else.
 */
public final class MonsterKinds {
	public static final String DEAD = "dead", CORPSE_EATERS = "corpse_eaters", BRUTES = "brutes", BEASTS = "beasts",
		SPIRITS = "spirits", CURSED = "cursed", PEOPLE = "people";
	/** Every kind, in the bestiary's page order. */
	public static final List<String> ALL = List.of(DEAD, CORPSE_EATERS, BRUTES, BEASTS, SPIRITS, CURSED, PEOPLE);

	private MonsterKinds() {
	}

	/** The entity type tag of {@code kind}. */
	public static TagKey<EntityType<?>> tag(String kind) {
		return TagKey.create(Registries.ENTITY_TYPE, JaymosKingdoms.id("kind/" + kind));
	}

	/** Whether {@code e} is of {@code kind}: its type is in the kind's tag, or (for {@link #CURSED}) it is a vampire or a werewolf in beast form. */
	public static boolean is(Entity e, String kind) {
		if (e.is(tag(kind))) {
			return true;
		}
		return CURSED.equals(kind) && cursedState(e);
	}

	/** Whether {@code type} is in the tag of {@code kind} (no state: only what its type says). */
	public static boolean is(EntityType<?> type, String kind) {
		return BuiltInRegistries.ENTITY_TYPE.wrapAsHolder(type).is(tag(kind));
	}

	/** Every kind {@code type} is of by its tags alone. */
	public static List<String> of(EntityType<?> type) {
		List<String> kinds = new ArrayList<>();
		for (String kind : ALL) {
			if (is(type, kind)) {
				kinds.add(kind);
			}
		}
		return kinds;
	}

	/** Every kind {@code e} is of (a werewolf player in beast form is both people and cursed). */
	public static List<String> of(Entity e) {
		List<String> kinds = new ArrayList<>();
		for (String kind : ALL) {
			if (is(e, kind)) {
				kinds.add(kind);
			}
		}
		return kinds;
	}

	/** A vampire that is not a dhampir (half-blooded: none of the weaknesses), or a werewolf changed into its beast. */
	static boolean cursedState(Entity e) {
		if (Vampires.isVampire(e) && !VampirePlayers.dhampir(e)) {
			return true;
		}
		return e instanceof ServerPlayer player ? WerewolfPlayers.beast(player) : Lycans.cursed(e) && Lycans.changed(e);
	}
}
