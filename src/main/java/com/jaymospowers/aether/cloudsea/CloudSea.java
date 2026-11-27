package com.jaymospowers.aether.cloudsea;

import com.jaymospowers.JaymosPowers;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * (Andy: "another dimension that is just clouds and air between the overworld and aether ... that way players wont be able
 * to see it or get there at all easily") The Cloud Sea: a band of a One Dimension world ({@link com.jaymospowers.compat.Stacked})
 * between the Overworld's sky and the Aether's islands, {@value #HEIGHT} blocks of open air and cloud and nothing else. No
 * ground, no mobs, no structures.
 * <ul>
 * <li>Low down, loose banks of cloud drift about {@value #LOW_DECK} blocks up.</li>
 * <li>Near the top lies the overcast, about {@value #OVERCAST} blocks up: one bank from horizon to horizon with a few wide
 * wells through it. Cloudbank ({@link CloudbankBlock}) two deep is solid, so from underneath the overcast is a ceiling: you
 * find a well, or dig. From on top it is a floor that catches whoever falls off the Aether.</li>
 * <li>The Aether stays out of sight until you are over the overcast ({@code client/aether/AetherVeil}), and the Cloud Sea
 * itself until you are over the Overworld's own clouds.</li>
 * </ul>
 * The clouds are plain world generation: {@code worldgen/noise_settings/cloud_sea.json}, written by
 * {@code tools/gen_cloud_sea.py}, where the decks' heights, cover and thickness are tuned (the heights here are copies for
 * the code that needs them). It is a band only: nothing leads to {@code jaymospowers:cloud_sea} as a dimension of its own, and
 * a world that isn't stacked has no Cloud Sea.
 */
public final class CloudSea {
	public static final ResourceKey<Level> REALM = ResourceKey.create(Registries.DIMENSION, JaymosPowers.id("cloud_sea"));
	/** How tall the band is (the dimension type's height). */
	public static final int HEIGHT = 256;
	/** The middle of the loose low banks, in the band's own heights. */
	public static final int LOW_DECK = 72;
	/** The middle of the overcast, in the band's own heights. */
	public static final int OVERCAST = 184;
	/** The overcast lies between these heights (its middle wanders and it is up to 18 thick), in the band's own heights. */
	public static final int OVERCAST_BOTTOM = 166, OVERCAST_TOP = 202;

	private CloudSea() {
	}
}
