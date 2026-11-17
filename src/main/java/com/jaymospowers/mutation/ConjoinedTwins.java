package com.jaymospowers.mutation;

import com.jaymospowers.JaymosPowers;
import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ServerLevelAccessor;

/**
 * Cosmetic conjoined twin mutation: when a villager or zombie spawns naturally, it may have a baby/toddler
 * passenger on its shoulder. Purely visual — no gameplay effect.
 * <p>
 * Uses {@link #CONJOINED_TWIN} attachment on the adult to track the passenger entity UUID.
 * The passenger is a baby version of the same entity type, positioned on the shoulder via
 * {@link com.jaymospowers.mixin.ConjoinedTwinVillagerMixin} and {@link com.jaymospowers.mixin.ConjoinedTwinZombieMixin}.
 */
public final class ConjoinedTwins {

    /** Entity types that can spawn with a conjoined twin (villagers, zombies, MCA villagers). */
    public static final TagKey<EntityType<?>> HAS_CONJOINED_TWIN = TagKey.create(Registries.ENTITY_TYPE, JaymosPowers.id("has_conjoined_twin"));

    /** Chance a natural spawn gets a conjoined twin passenger. */
    private static final float CHANCE = 0.05F; // 5%

    /** The passenger entity UUID, stored on the adult (synced to clients for rendering). */
    public static final AttachmentType<String> CONJOINED_TWIN = AttachmentRegistry.create(JaymosPowers.id("conjoined_twin"),
            builder -> builder.persistent(Codec.STRING).syncWith(ByteBufCodecs.STRING_UTF8, AttachmentSyncPredicate.all()));

    private ConjoinedTwins() {}

    /**
     * Called when a mob finishes spawning naturally. May attach a conjoined twin passenger.
     */
    public static void onNaturalSpawn(Mob adult, ServerLevelAccessor level, EntitySpawnReason reason) {
        if (reason != EntitySpawnReason.NATURAL && reason != EntitySpawnReason.CHUNK_GENERATION) {
            return;
        }
        if (!adult.getType().builtInRegistryHolder().is(HAS_CONJOINED_TWIN)) {
            return;
        }
        if (adult.hasAttached(CONJOINED_TWIN)) {
            return; // already has one
        }
        RandomSource random = level.getRandom();
        if (random.nextFloat() >= CHANCE) {
            return;
        }

        // Create the baby passenger
        Entity baby = adult.getType().create(level.getLevel(), reason);
        if (!(baby instanceof Mob child)) {
            return;
        }

        // Make it a baby
        if (child instanceof AgeableMob ageable) {
            ageable.setAge(-24000); // standard baby age
        }

        // Position at adult's location
        child.snapTo(adult.getX(), adult.getY(), adult.getZ(), adult.getYRot(), adult.getXRot());

        // Store the baby's UUID on the adult (for sync/rendering)
        adult.setAttached(CONJOINED_TWIN, child.getUUID().toString());

        // Make the baby a passenger of the adult
        // Note: startRiding returns false if it fails
        if (!child.startRiding(adult, true, true)) {
            adult.removeAttached(CONJOINED_TWIN);
            child.discard();
            return;
        }

        // Spawn the baby in the world (it will be saved as a passenger of the adult)
        level.addFreshEntityWithPassengers(child);
    }

    /**
     * Checks if an entity has a conjoined twin passenger.
     */
    public static boolean hasConjoinedTwin(Entity entity) {
        return entity.hasAttached(CONJOINED_TWIN);
    }

    /**
     * Gets the conjoined twin passenger entity, if present and still riding.
     */
    @SuppressWarnings("unchecked")
    public static <T extends Entity> T getConjoinedTwin(Entity adult) {
        String uuidStr = adult.getAttached(CONJOINED_TWIN);
        if (uuidStr == null) {
            return null;
        }
        for (Entity passenger : adult.getPassengers()) {
            if (passenger.getUUID().toString().equals(uuidStr)) {
                return (T) passenger;
            }
        }
        // Passenger lost - clean up
        adult.removeAttached(CONJOINED_TWIN);
        return null;
    }
}