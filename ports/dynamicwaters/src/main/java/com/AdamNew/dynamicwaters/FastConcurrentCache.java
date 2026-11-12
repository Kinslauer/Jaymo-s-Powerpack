/*
 * Adapted from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * specifically net.dries007.tfc.world.FastConcurrentCache.
 * Original work Copyright (c) TerraFirmaCraft team, licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Modified for Dynamic Waters by AdamNew, 2026-07-13.
 * As a Derivative Work under the EUPL, this file remains available under the
 * EUPL v1.2, or a licence listed as compatible in the EUPL's Appendix
 * (e.g. GPLv3, LGPLv3, MPL2), for as long as it carries this notice.
 *
 * Ported essentially unchanged - this class has no TFC-specific coupling.
 */

package com.AdamNew.dynamicwaters;

import java.util.Arrays;
import java.util.concurrent.locks.StampedLock;
import it.unimi.dsi.fastutil.HashCommon;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import org.jetbrains.annotations.Nullable;

/**
 * A concurrent (safe to read and write between multiple threads), positional, lossy cache: fixed-size,
 * direct-mapped (one slot per hash bucket - a colliding key simply evicts whatever was there). That's a
 * deliberate trade: no chaining/resizing means O(1) lookups with a single array read under the lock, at the
 * cost of occasional avoidable re-computation on a hash collision. For per-column river lookups during chunk
 * generation, re-computing occasionally is far cheaper than a general-purpose Map would be under this much
 * concurrent traffic.
 */
public class FastConcurrentCache<T>
{
    private final StampedLock lock = new StampedLock();

    private final long[] keys;
    private final T[] values;
    private final int mask;

    @SuppressWarnings("unchecked")
    public FastConcurrentCache(int size)
    {
        size = Mth.smallestEncompassingPowerOfTwo(size);

        this.mask = size - 1;
        this.keys = new long[size];
        this.values = (T[]) new Object[size];

        Arrays.fill(this.keys, Long.MIN_VALUE);
    }

    @Nullable
    public T getIfPresent(int x, int z)
    {
        final long key = ChunkPos.pack(x, z);
        final int index = (int) HashCommon.mix(key) & mask;
        final long stamp = lock.readLock();

        T t = null;
        if (keys[index] == key)
        {
            t = values[index];
        }

        lock.unlockRead(stamp);
        return t;
    }

    public void set(int x, int z, T value)
    {
        final long key = ChunkPos.pack(x, z);
        final int index = (int) HashCommon.mix(key) & mask;
        final long stamp = lock.writeLock();

        keys[index] = key;
        values[index] = value;

        lock.unlockWrite(stamp);
    }

    public void clear()
    {
        final long stamp = lock.writeLock();
        Arrays.fill(this.keys, Long.MIN_VALUE);
        Arrays.fill(this.values, null);
        lock.unlockWrite(stamp);
    }
}
