package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

/**
 * The peoples and allegiances that drive structure placement, sieges and diplomacy.
 * Distinct from {@link FactionType} which describes a settlement's form (kingdom, village, camp).
 * <p>
 * Default relations are symmetric. Greenskins and Pillagers are sworn enemies (always {@link Relation#WAR}).
 */
public enum FactionAllegiance {
    /** Earthlings and Hearthlanders — the human peoples. */
    HUMAN,
    /** Non-human peoples (Sylvan, Verdant, Tideborn, etc.). */
    NONHUMAN,
    /** Bandits — raiders and outlaws. */
    BANDIT,
    /** Pirates — sea raiders and privateers. */
    PIRATE,
    /** Greenskins — goblins, orcs and their kin. */
    GREENSKIN,
    /** Pillagers — illager warbands and raiders. */
    PILLAGER,
    /** Demons — fiends and the corrupted. */
    DEMON,
    /** Avatars — the chosen of the patrons, divine champions. */
    AVATAR,
    /** The Pallid — intelligent Hollow who lead the risen dead. */
    PALLID;

    public static final Codec<FactionAllegiance> CODEC = Codec.STRING.xmap(FactionAllegiance::parse, FactionAllegiance::id);

    public String id() {
        return this.name().toLowerCase(Locale.ROOT);
    }

    public static FactionAllegiance parse(String id) {
        for (FactionAllegiance allegiance : values()) {
            if (allegiance.id().equals(id)) {
                return allegiance;
            }
        }
        return HUMAN;
    }

    public String key() {
        return "faction_allegiance.jaymoskingdoms." + this.id();
    }

    /** Base relation between two allegiances (symmetric, before any diplomacy). */
    public Relation baseRelation(FactionAllegiance other) {
        return BASE_RELATIONS.get(this).get(other);
    }

    /** True if these two allegiances can never be allied or at peace (sworn enemies). */
    public boolean neverAllies(FactionAllegiance other) {
        return (this == GREENSKIN && other == PILLAGER) || (this == PILLAGER && other == GREENSKIN);
    }

    private static final Map<FactionAllegiance, Map<FactionAllegiance, Relation>> BASE_RELATIONS = buildBaseRelations();

    private static Map<FactionAllegiance, Map<FactionAllegiance, Relation>> buildBaseRelations() {
        Map<FactionAllegiance, Map<FactionAllegiance, Relation>> map = new EnumMap<>(FactionAllegiance.class);
        for (FactionAllegiance a : values()) {
            Map<FactionAllegiance, Relation> row = new EnumMap<>(FactionAllegiance.class);
            for (FactionAllegiance b : values()) {
                row.put(b, computeBaseRelation(a, b));
            }
            map.put(a, row);
        }
        return Map.copyOf(map);
    }

    private static Relation computeBaseRelation(FactionAllegiance a, FactionAllegiance b) {
        if (a == b) {
            return Relation.ALLIED;
        }
        // Sworn enemies: Greenskins and Pillagers are always at war, never allied.
        if ((a == GREENSKIN && b == PILLAGER) || (a == PILLAGER && b == GREENSKIN)) {
            return Relation.WAR;
        }
        // Humans and Nonhumans are generally neutral but can ally.
        if ((a == HUMAN && b == NONHUMAN) || (a == NONHUMAN && b == HUMAN)) {
            return Relation.NEUTRAL;
        }
        // Avatars are aligned with good peoples.
        if (a == AVATAR || b == AVATAR) {
            FactionAllegiance other = a == AVATAR ? b : a;
            if (other == HUMAN || other == NONHUMAN) {
                return Relation.ALLIED;
            }
            if (other == DEMON || other == PALLID || other == GREENSKIN || other == PILLAGER) {
                return Relation.WAR;
            }
            return Relation.NEUTRAL;
        }
        // Demons are hostile to most living peoples.
        if (a == DEMON || b == DEMON) {
            FactionAllegiance other = a == DEMON ? b : a;
            if (other == HUMAN || other == NONHUMAN || other == AVATAR) {
                return Relation.WAR;
            }
            return Relation.RIVAL;
        }
        // Pallids lead the dead; hostile to the living.
        if (a == PALLID || b == PALLID) {
            FactionAllegiance other = a == PALLID ? b : a;
            if (other == HUMAN || other == NONHUMAN || other == AVATAR) {
                return Relation.WAR;
            }
            if (other == DEMON) {
                return Relation.RIVAL;
            }
            return Relation.NEUTRAL;
        }
        // Bandits and Pirates are rivals to most, war with lawful peoples.
        if (a == BANDIT || a == PIRATE) {
            FactionAllegiance other = a == BANDIT ? b : a;
            if (other == HUMAN || other == NONHUMAN || other == AVATAR) {
                return Relation.RIVAL;
            }
            if (other == DEMON || other == PALLID) {
                return Relation.NEUTRAL;
            }
            return Relation.NEUTRAL;
        }
        if (b == BANDIT || b == PIRATE) {
            return computeBaseRelation(b, a);
        }
        // Greenskins and Pillagers (handled above for each other) are rivals to others.
        if (a == GREENSKIN || a == PILLAGER) {
            FactionAllegiance other = a == GREENSKIN ? b : a;
            if (other == HUMAN || other == NONHUMAN || other == AVATAR) {
                return Relation.RIVAL;
            }
            if (other == DEMON || other == PALLID || other == BANDIT || other == PIRATE) {
                return Relation.NEUTRAL;
            }
            return Relation.NEUTRAL;
        }
        if (b == GREENSKIN || b == PILLAGER) {
            return computeBaseRelation(b, a);
        }
        return Relation.NEUTRAL;
    }
}