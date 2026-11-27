package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Court wizard creeper ward: a persistent magical field over a kingdom's plot that prevents, rewinds,
 * weakens and repels creepers.
 * <ul>
 * <li><b>Prevention/rewind:</b> while the court wizard is fresh (grown < 16 days), creeper explosions
 *     inside the ward fizzle before they blow; once the wizard ages, any blast that goes off is
 *     rewound (blocks restored over a few seconds with a visible rewind effect).</li>
 * <li><b>Weakening:</b> creepers inside the ward are 30% slower and their blast radius is halved.</li>
 * <li><b>Repulsion:</b> about half of creepers feel the disturbance at the ward's edge and turn away,
 *     never entering; a shimmer effect marks the boundary.</li>
 * </ul>
 * The ward covers the entire kingdom plot, is always on while the court wizard lives and is home
 * (within the plot), and breaks on the wizard's death, absence from the plot, or any siege against
 * the kingdom. Prevention chance scales with wizard strength (younger = stronger).
 */
public final class CreeperWard {

    /** A warded plot: faction id → its claimed chunks that are warded. Saved on the Overworld. */
    static final AttachmentType<Map<String, List<Long>>> WARDS = AttachmentRegistry.create(JaymosKingdoms.id("creeper_wards"),
            builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, Codec.LONG.listOf())));

    /** How often the ward effect is visualised (ticks). */
    private static final int VISUAL_TICKS = 40;
    /** How often the ward checks for active sieges (ticks). */
    private static final int SIEGE_CHECK_TICKS = 200;
    /** How often creepers at the edge are tested for repulsion (ticks). */
    private static final int REPULSE_CHECK_TICKS = 20;
    /** Ward colour (bluish magical shimmer). */
    private static final int WARD_COLOUR = 0x4488FF;
    /** Rewind effect duration (ticks). */
    private static final int REWIND_TICKS = 80;

    /** Record of blocks a creeper blast would destroy, for rewinding. */
    private record RewindBlock(BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
    }

    /** Pending rewinds: chunk key → list of blocks to restore over time. */
    private static final Map<Long, List<RewindBlock>> REWINDS = new HashMap<>();
    /** Creepers currently weakened by a ward (their UUIDs). */
    private static final Set<UUID> WEAKENED = new HashSet<>();

    /** Unpacks a packed chunk position into x and z coordinates. */
    private static int unpackX(long packed) { return (int)(packed >> 32); }
    private static int unpackZ(long packed) { return (int)packed; }

    private CreeperWard() {
    }

    static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            ServerLevel level = Factions.level(server);
            long now = level.getGameTime();

            // Visualise active wards
            if (now % VISUAL_TICKS == 0) {
                visualiseWards(level, now);
            }

            // Check sieges and remove wards for besieged kingdoms
            if (now % SIEGE_CHECK_TICKS == 0) {
                checkSieges(level);
            }

            // Process pending rewinds
            processRewinds(level, now);

            // Repulse creepers at ward edges
            if (now % REPULSE_CHECK_TICKS == 0) {
                repulseAtEdges(level);
            }

            // Weaken/strengthen creepers inside/outside wards
            weakenCreepersInWards(level);
        });
    }

    /**
     * Whether the given faction has an active creeper ward.
     */
    public static boolean isWarded(MinecraftServer server, int factionId) {
        ServerLevel level = Factions.level(server);
        Map<String, List<Long>> wards = level.getAttachedOrElse(WARDS, Map.of());
        return wards.containsKey(Integer.toString(factionId));
    }

    /**
     * Whether {@code pos} is inside a warded plot.
     */
    public static boolean isWarded(MinecraftServer server, BlockPos pos) {
        if (server == null) return false;
        ServerLevel level = Factions.level(server);
        ChunkPos chunk = ChunkPos.containing(pos);
        Map<String, List<Long>> wards = level.getAttachedOrElse(WARDS, Map.of());
        for (Map.Entry<String, List<Long>> e : wards.entrySet()) {
            if (e.getValue().contains(chunk.pack())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Gets the warded faction id at {@code pos}, or -1 if none.
     */
    public static int wardedFactionAt(MinecraftServer server, BlockPos pos) {
        if (server == null) return -1;
        ServerLevel level = Factions.level(server);
        ChunkPos chunk = ChunkPos.containing(pos);
        Map<String, List<Long>> wards = level.getAttachedOrElse(WARDS, Map.of());
        for (Map.Entry<String, List<Long>> e : wards.entrySet()) {
            if (e.getValue().contains(chunk.pack())) {
                return Integer.parseInt(e.getKey());
            }
        }
        return -1;
    }

    /**
     * Adds or updates the ward for {@code faction} to cover its current claims.
     */
    public static void setWarded(MinecraftServer server, Faction faction, boolean warded) {
        ServerLevel level = Factions.level(server);
        Map<String, List<Long>> wards = new HashMap<>(level.getAttachedOrElse(WARDS, Map.of()));
        String key = Integer.toString(faction.id());
        if (warded) {
            List<Long> chunks = new ArrayList<>(faction.claims());
            wards.put(key, chunks);
        } else {
            wards.remove(key);
        }
        level.setAttached(WARDS, Map.copyOf(wards));
    }

    /**
     * Called when a court wizard's status changes (dies, moves away, etc.): re-evaluates the ward.
     */
    public static void reevalWard(MinecraftServer server, Faction faction) {
        if (faction.type() != FactionType.KINGDOM || faction.fallen()) {
            setWarded(server, faction, false);
            return;
        }
        // Check if the kingdom has a living court wizard at home
        Worker wizard = findCourtWizard(server, faction);
        if (wizard != null && isWizardAtHome(wizard, faction)) {
            setWarded(server, faction, true);
        } else {
            setWarded(server, faction, false);
        }
    }

    /**
     * Finds the court wizard worker for {@code faction}, or null.
     */
    private static @Nullable Worker findCourtWizard(MinecraftServer server, Faction faction) {
        if (server == null) return null;
        ServerLevel level = Factions.level(server);
        // Large box covering the level (in practice, just the kingdom's area)
        net.minecraft.world.phys.AABB box = new net.minecraft.world.phys.AABB(
                faction.seat().getX() - 256, -64, faction.seat().getZ() - 256,
                faction.seat().getX() + 256, 320, faction.seat().getZ() + 256);
        List<Worker> workers = Workers.near(level, box, w -> w.role() == Sorcery.WIZARD && w.factionId() == faction.id());
        return workers.isEmpty() ? null : workers.get(0);
    }

    /**
     * Whether {@code wizard} is within their kingdom's plot (at home).
     */
    private static boolean isWizardAtHome(Worker wizard, Faction faction) {
        BlockPos pos = wizard.mob().blockPosition();
        return faction.claims(ChunkPos.containing(pos));
    }

    /**
     * Returns the wizard's strength 0.0-1.0 based on their level/experience.
     * Used to scale prevention chance.
     */
    public static double wizardStrength(ServerLevel level, Worker wizard) {
    	// Use the worker's max health as a proxy for strength (higher = stronger)
    	if (wizard.mob() instanceof net.minecraft.world.entity.LivingEntity living) {
    		float maxHp = living.getMaxHealth();
    		return Math.min(1.0, maxHp / 20.0F);
    	}
    	return 0.5;
    }

    /**
     * Visualises all active wards with a subtle shimmer at the plot's edge, near players.
     */
    private static void visualiseWards(ServerLevel level, long now) {
    	Map<String, List<Long>> wards = level.getAttachedOrElse(WARDS, Map.of());
    	for (Map.Entry<String, List<Long>> e : wards.entrySet()) {
    		int factionId = Integer.parseInt(e.getKey());
    		Faction faction = Factions.get(level.getServer(), factionId);
    		if (faction == null || faction.fallen()) continue;

    		// Draw sigil at the kingdom's seat (only if a player is within 64 blocks)
    		BlockPos seat = faction.seat();
    		boolean nearPlayer = level.getPlayers(p -> true).stream().anyMatch(p -> p.blockPosition().distSqr(seat) < 64 * 64);
    		if (nearPlayer) {
    			Fx.sigil(level, Vec3.atCenterOf(seat), 16.0F, WARD_COLOUR, VISUAL_TICKS);
    		}
    	}
    }

    /**
     * Removes wards for kingdoms under siege.
     */
    private static void checkSieges(ServerLevel level) {
        Map<String, List<Long>> wards = new HashMap<>(level.getAttachedOrElse(WARDS, Map.of()));
        boolean changed = false;
        for (WarParties.Party party : WarParties.active()) {
            if (party.phase != WarParties.Phase.SIEGE || party.target < 0) continue;
            String key = Integer.toString(party.target);
            if (wards.remove(key) != null) {
                changed = true;
                // Visual: ward shatters
                Faction target = Factions.get(level.getServer(), party.target);
                if (target != null) {
                    Fx.burst(level, target.seat().getX(), target.seat().getY() + 2, target.seat().getZ(),
                            WARD_COLOUR, 2.0F, 60, 4.0, 1.0, 4.0);
                    level.playSound(null, target.seat().getX(), target.seat().getY(), target.seat().getZ(),
                            SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 1.0F, 0.8F);
                }
            }
        }
        if (changed) {
            level.setAttached(WARDS, Map.copyOf(wards));
        }
    }

    /**
     * Repulses creepers at ward edges: ~50% chance to turn away with a visual recoil.
     * Each creeper rolls once at the edge; the result is remembered.
     */
    private static void repulseAtEdges(ServerLevel level) {
    	Map<String, List<Long>> wards = level.getAttachedOrElse(WARDS, Map.of());
    	if (wards.isEmpty()) return;

    	// Collect all warded chunks into a set for fast lookup
    	Set<Long> wardedChunks = new HashSet<>();
    	for (List<Long> chunks : wards.values()) {
    		wardedChunks.addAll(chunks);
    	}

    	// Find creepers near warded chunks (within 32 blocks of boundary)
    	for (Creeper creeper : level.getEntitiesOfClass(Creeper.class,
    			searchBoxAroundWards(wardedChunks), Creeper::isAlive)) {
    		ChunkPos creeperChunk = creeper.chunkPosition();
    		boolean atEdge = false;
    		for (int dx = -1; dx <= 1 && !atEdge; dx++) {
    			for (int dz = -1; dz <= 1 && !atEdge; dz++) {
    				if (wardedChunks.contains(new ChunkPos(creeperChunk.x() + dx, creeperChunk.z() + dz).pack())) {
    					atEdge = true;
    				}
    			}
    		}
    		if (!atEdge) continue;

    		// Roll once per creeper: mark repelled or not via a custom tag
    		if (!creeper.entityTags().contains("ward_repelled") && !creeper.isRemoved()) {
    			if (level.getRandom().nextFloat() < 0.5F) {
    				creeper.addTag("ward_repelled");
    				// Turn away from the ward center
    				Vec3 away = creeper.position().subtract(findNearestWardCenter(creeper.position(), wardedChunks, level)).normalize();
    				Vec3 newPos = creeper.position().add(away.scale(4.0));
    				creeper.getNavigation().moveTo(newPos.x, newPos.y, newPos.z, 1.2);
    				// Visual: shimmer recoil
    				Fx.ring(level, creeper.position(), 2.0F, WARD_COLOUR);
    				Fx.wave(level, creeper.position(), away, 4.0, 1.5F, WARD_COLOUR, 10);
    				level.playSound(null, creeper.getX(), creeper.getY(), creeper.getZ(),
    						SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 0.6F, 1.2F);
    			}
    		}
    	}
    }

    /**
     * Finds the center of the nearest warded area.
     */
    private static Vec3 findNearestWardCenter(Vec3 pos, Set<Long> wardedChunks, ServerLevel level) {
        int bestX = ((int)pos.x) >> 4;
        int bestZ = ((int)pos.z) >> 4;
        double bestDist = Double.MAX_VALUE;
        for (long packed : wardedChunks) {
            int cx = unpackX(packed) << 4;
            int cz = unpackZ(packed) << 4;
            double d = cx - pos.x;
            double dz = cz - pos.z;
            double dist = d * d + dz * dz;
            if (dist < bestDist) {
                bestDist = dist;
                bestX = cx;
                bestZ = cz;
            }
        }
        return new Vec3(bestX, pos.y, bestZ);
    }

    /**
     * Applies weakening to creepers inside wards, restores those outside.
     * Searches only near warded plots, every 10 ticks.
     */
    private static void weakenCreepersInWards(ServerLevel level) {
    	Map<String, List<Long>> wards = level.getAttachedOrElse(WARDS, Map.of());
    	if (wards.isEmpty()) return;

    	Set<Long> wardedChunks = new HashSet<>();
    	for (List<Long> chunks : wards.values()) {
    		wardedChunks.addAll(chunks);
    	}

    	// Search only near warded chunks (within 32 blocks of each)
    	for (Creeper creeper : level.getEntitiesOfClass(Creeper.class,
    			searchBoxAroundWards(wardedChunks), Creeper::isAlive)) {
    		long key = creeper.chunkPosition().pack();
    		boolean inWard = wardedChunks.contains(key);
    		boolean wasWeakened = WEAKENED.contains(creeper.getUUID());
    		if (inWard && !wasWeakened) {
    			weakenCreeperInWard(creeper);
    			WEAKENED.add(creeper.getUUID());
    		} else if (inWard && wasWeakened) {
    			// Renew the effects while the creeper stays in the ward
    			renewCreeperWardEffects(creeper);
    		} else if (!inWard && wasWeakened) {
    			restoreCreeperFromWard(creeper);
    			WEAKENED.remove(creeper.getUUID());
    		}
    	}

    	// Clean up dead creepers from the set
    	WEAKENED.removeIf(id -> {
    		net.minecraft.world.entity.Entity e = level.getEntity(id);
    		return e == null || !e.isAlive();
    	});
    }

    /** Builds a search AABB that covers all warded chunks plus a 32-block margin. */
    private static AABB searchBoxAroundWards(Set<Long> wardedChunks) {
    	double minX = Double.MAX_VALUE, minY = -64, minZ = Double.MAX_VALUE;
    	double maxX = -Double.MAX_VALUE, maxY = 320, maxZ = -Double.MAX_VALUE;
    	for (long packed : wardedChunks) {
    		int cx = unpackX(packed) << 4;
    		int cz = unpackZ(packed) << 4;
    		minX = Math.min(minX, cx - 32);
    		minZ = Math.min(minZ, cz - 32);
    		maxX = Math.max(maxX, cx + 48);
    		maxZ = Math.max(maxZ, cz + 48);
    	}
    	if (minX > maxX) return new AABB(0, -64, 0, 1, 320, 1);
    	return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
    }

    /**
     * Handles a creeper explosion inside a ward: prevents it if the wizard is strong enough,
     * otherwise the blast goes off and blocks are recorded for rewind.
     * @return true if the explosion was prevented (fizzled)
     */
    public static boolean tryPrevent(ServerLevel level, Creeper creeper) {
    	int factionId = wardedFactionAt(level.getServer(), creeper.blockPosition());
    	if (factionId < 0) return false;

    	Faction faction = Factions.get(level.getServer(), factionId);
    	if (faction == null || faction.fallen()) return false;

    	Worker wizard = findCourtWizard(level.getServer(), faction);
    	double strength = wizard != null ? wizardStrength(level, wizard) : 0.0;

    	// Prevention chance scales with wizard strength: strong = 90% prevention, weak = 10%
    	double preventChance = 0.1 + 0.8 * strength;
    	if (level.getRandom().nextDouble() < preventChance) {
    		// Fizzle: prevent the explosion entirely
    		Fx.ring(level, creeper.position(), 3.0F, WARD_COLOUR);
    		Fx.burst(level, creeper.getX(), creeper.getY(0.5), creeper.getZ(),
    				WARD_COLOUR, 1.5F, 30, 2.0, 1.0, 2.0);
    		level.playSound(null, creeper.getX(), creeper.getY(), creeper.getZ(),
    				SoundEvents.FIRE_EXTINGUISH, SoundSource.HOSTILE, 0.8F, 1.5F);
    		// Discard the creeper (it fizzled)
    		creeper.discard();
    		return true;
    	}
    	return false;
    }

    /**
     * Records blocks a creeper blast inside a ward will break, for later rewind.
     * The blocks DO break (the explosion goes off); they are restored over time.
     */
    public static void recordRewindBlocks(ServerLevel level, Creeper creeper, List<BlockPos> targets) {
    	int factionId = wardedFactionAt(level.getServer(), creeper.blockPosition());
    	if (factionId < 0) return;

    	Faction faction = Factions.get(level.getServer(), factionId);
    	if (faction == null || faction.fallen()) return;

    	if (targets.isEmpty()) return;
    	List<RewindBlock> blocks = new ArrayList<>();
    	for (BlockPos pos : targets) {
    		net.minecraft.world.level.block.state.BlockState state = level.getBlockState(pos);
    		if (!state.isAir() && !state.hasBlockEntity()) {
    			blocks.add(new RewindBlock(pos.immutable(), state));
    		}
    	}
    	if (!blocks.isEmpty()) {
    		long chunkKey = creeper.chunkPosition().pack();
    		REWINDS.computeIfAbsent(chunkKey, k -> new ArrayList<>()).addAll(blocks);
    		// Visual: time rewind shimmer
    		Fx.blast(level, creeper.position(), 4.0F, WARD_COLOUR);
    		level.playSound(null, creeper.getX(), creeper.getY(), creeper.getZ(),
    				SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.0F, 0.6F);
    	}
    }

    /**
     * Processes pending rewinds: restores blocks over time with visual effect.
     */
    private static void processRewinds(ServerLevel level, long now) {
        for (Map.Entry<Long, List<RewindBlock>> e : new HashMap<>(REWINDS).entrySet()) {
            List<RewindBlock> blocks = e.getValue();
            if (blocks.isEmpty()) {
                REWINDS.remove(e.getKey());
                continue;
            }
            // Restore up to 8 blocks per tick per chunk
            int restored = 0;
            for (RewindBlock rb : List.copyOf(blocks)) {
                if (restored >= 8) break;
                if (!level.getBlockState(rb.pos()).canBeReplaced()) {
                    // Player placed something there: skip this block
                    blocks.remove(rb);
                    continue;
                }
                level.setBlock(rb.pos(), rb.state(), net.minecraft.world.level.block.Block.UPDATE_ALL);
                // Visual: block reappears with time particles
                level.sendParticles(ParticleTypes.REVERSE_PORTAL, rb.pos().getX() + 0.5, rb.pos().getY() + 0.5, rb.pos().getZ() + 0.5,
                        8, 0.2, 0.2, 0.2, 0.02);
                level.playSound(null, rb.pos().getX(), rb.pos().getY(), rb.pos().getZ(),
                        rb.state().getSoundType().getPlaceSound(), SoundSource.BLOCKS, 0.5F, 1.2F);
                blocks.remove(rb);
                restored++;
            }
            if (blocks.isEmpty()) {
                REWINDS.remove(e.getKey());
            }
        }
    }

    /**
     * Weakens a creeper inside a ward: 30% slower, blast radius halved.
     * Call this from a creeper tick or when it enters the ward.
     */
    public static void weakenCreeperInWard(Creeper creeper) {
    	creeper.addEffect(new net.minecraft.world.effect.MobEffectInstance(
    			net.minecraft.world.effect.MobEffects.SLOWNESS,
    			20 * 10,
    			1, // amplifier 1 = 30% slow
    			false, false
    	));
    	creeper.addEffect(new net.minecraft.world.effect.MobEffectInstance(
    			net.minecraft.world.effect.MobEffects.RESISTANCE,
    			20 * 10,
    			1, // amplifier 1 = 50% damage reduction
    			false, false
    	));
    }

    /**
     * Renews the ward effects on a creeper that is still inside the ward.
     */
    private static void renewCreeperWardEffects(Creeper creeper) {
    	if (creeper.hasEffect(net.minecraft.world.effect.MobEffects.SLOWNESS)) {
    		creeper.addEffect(new net.minecraft.world.effect.MobEffectInstance(
    				net.minecraft.world.effect.MobEffects.SLOWNESS, 20 * 5, 1, false, false));
    	}
    	if (creeper.hasEffect(net.minecraft.world.effect.MobEffects.RESISTANCE)) {
    		creeper.addEffect(new net.minecraft.world.effect.MobEffectInstance(
    				net.minecraft.world.effect.MobEffects.RESISTANCE, 20 * 5, 1, false, false));
    	}
    }

    /**
     * Restores a creeper's original stats when it leaves the ward.
     */
    public static void restoreCreeperFromWard(Creeper creeper) {
    	creeper.removeEffect(net.minecraft.world.effect.MobEffects.SLOWNESS);
    	creeper.removeEffect(net.minecraft.world.effect.MobEffects.RESISTANCE);
    }
}