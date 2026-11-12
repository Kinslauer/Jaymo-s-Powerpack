package com.AdamNew.dynamicwaters.compat;

import java.lang.reflect.Method;
import java.util.Optional;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import org.jetbrains.annotations.Nullable;

/**
 * One Dimension (CustomChunks) stacks the Overworld, the Nether and other worlds into one level, each as a band of
 * height with its own generator. The level's own generator is then CustomChunks' stacked one, which runs every band's
 * generator into a scratch chunk at that band's own heights. Dynamic Waters only knows how to make rivers with the
 * Overworld's noise generator, so in a stacked world it works out its rivers with the Overworld band's generator and
 * random state, and only that band's generator carves them: the Nether and Aether bands share the Overworld level but
 * get no rivers. Read by reflection, so nothing loads without CustomChunks. Added for Jaymo's Powers' port.
 */
public final class OneDimension {
    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();
    private static final boolean LOADED = FabricLoader.getInstance().isModLoaded("customchunks");
    private static boolean ready, broken;
    private static Method stacksOf, bandOf, generator, randomState, shift, minY, maxY;

    /** The Overworld band of the last stacked Overworld looked up, or null when the world isn't stacked. */
    private static volatile @Nullable Object band;
    private static volatile @Nullable ServerLevel bandLevel;

    private OneDimension() {
    }

    private static boolean api() {
        if (!LOADED || broken) return false;
        if (ready) return true;
        try {
            final Class<?> stacks = Class.forName("codx.customchunks.Stacks");
            final Class<?> stack = Class.forName("codx.customchunks.Stack");
            final Class<?> bandClass = Class.forName("codx.customchunks.Band");
            stacksOf = stacks.getMethod("of", ServerLevel.class);
            bandOf = stack.getMethod("bandOf", net.minecraft.resources.ResourceKey.class);
            generator = bandClass.getMethod("generator");
            randomState = bandClass.getMethod("randomState");
            shift = bandClass.getMethod("shift");
            minY = bandClass.getMethod("minY");
            maxY = bandClass.getMethod("maxYExclusive");
            ready = true;
        } catch (ReflectiveOperationException | LinkageError e) {
            broken = true;
            LOGGER.warn("Dynamic Waters: couldn't read One Dimension's bands ({}); rivers use the level's own generator", e.toString());
        }
        return ready;
    }

    /** The Overworld band of this level's stack, or null if One Dimension doesn't stack it. */
    private static @Nullable Object overworldBand(ServerLevel level) {
        if (level == null || level.dimension() != Level.OVERWORLD || !api()) return null;
        final Object cached = band;
        if (cached != null && bandLevel == level) return cached;
        try {
            final Optional<?> stack = (Optional<?>) stacksOf.invoke(null, level);
            if (stack.isEmpty()) return null;
            final Object found = bandOf.invoke(stack.get(), Level.OVERWORLD);
            if (found != null) {
                band = found;
                bandLevel = level;
                LOGGER.info("Dynamic Waters: One Dimension stack found; rivers follow the Overworld band (Y {} to {}, shift {})",
                        minY.invoke(found), maxY.invoke(found), shift.invoke(found));
            }
            return found;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    /** The Overworld this server loaded, kept so a stack bound after the level loaded is still found. */
    private static volatile @Nullable ServerLevel overworld;

    /** Forget the band when the server stops, so the next world looks again. */
    public static void reset() {
        band = null;
        bandLevel = null;
        overworld = null;
    }

    /** Called when the Overworld loads: points Dynamic Waters' global generator at the right one. */
    public static void onOverworldLoad(ServerLevel level) {
        overworld = level;
        com.AdamNew.dynamicwaters.HydrologyManager.setGlobalGenerator(generator(level), randomState(level));
        com.AdamNew.dynamicwaters.HydrologyManager.setSeaLevel(stacked() ? generator(level).getSeaLevel() : level.getSeaLevel());
    }

    /** If CustomChunks bound its stack after the Overworld loaded, pick the band up now. */
    private static void late() {
        final ServerLevel level = overworld;
        if (!LOADED || band != null || level == null) return;
        if (com.AdamNew.dynamicwaters.HydrologyManager.getGlobalGenerator()
                instanceof net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator) return;
        if (overworldBand(level) != null) onOverworldLoad(level);
    }

    /** Whether the last Overworld seen was stacked by One Dimension. */
    public static boolean stacked() {
        return band != null;
    }

    /** The generator Dynamic Waters works its rivers out with: the Overworld band's in a stacked world. */
    public static ChunkGenerator generator(ServerLevel level) {
        final Object found = overworldBand(level);
        if (found != null) {
            try {
                return (ChunkGenerator) generator.invoke(found);
            } catch (ReflectiveOperationException e) {
                // fall through to the level's own
            }
        }
        return level.getChunkSource().getGenerator();
    }

    /** The random state that goes with {@link #generator}. */
    public static RandomState randomState(ServerLevel level) {
        final Object found = overworldBand(level);
        if (found != null) {
            try {
                return (RandomState) randomState.invoke(found);
            } catch (ReflectiveOperationException e) {
                // fall through to the level's own
            }
        }
        return level.getChunkSource().randomState();
    }

    /** How far up the Overworld band sits in the stacked level (world Y = the Overworld's own Y + shift); 0 unstacked. */
    public static int shift(ServerLevel level) {
        final Object found = overworldBand(level);
        if (found == null) return 0;
        try {
            return (int) shift.invoke(found);
        } catch (ReflectiveOperationException e) {
            return 0;
        }
    }

    /**
     * Whether this generator may carve rivers. Unstacked, any generator of the Overworld may (as before); stacked, only
     * the Overworld band's generator, which is Dynamic Waters' global generator.
     */
    public static boolean carves(ChunkGenerator gen) {
        late();
        if (!stacked()) return true;
        return gen == com.AdamNew.dynamicwaters.HydrologyManager.getGlobalGenerator();
    }
}
