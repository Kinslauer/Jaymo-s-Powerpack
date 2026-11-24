package com.jaymospowers.client.air;

import java.util.IdentityHashMap;
import java.util.Map;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

/**
 * Light in its block's colour (Reference Mods patch 14, X10, after ImmersiveVFX's idea; our own code). The game keeps
 * one colour for all block light (warm), worked into its lightmap; here that colour is taken from the lights round the
 * camera: by soul fire it goes blue, by redstone red, by sea lanterns and end rods a cool white, by amethyst and crying
 * obsidian violet, by copper torches green, by lava a deep orange; torches, lanterns, fires and the rest keep the warm
 * light they have. Each light counts by how bright it is and how near; the colour eases over as you walk from one to
 * another ({@link #EASE}).
 * <p>
 * The lightmap is one for the whole view, so this is the light of the place you stand in (a room lit by soul lanterns
 * is blue, torches outside it too while you're in it), not each block lit its own colour: that would need the world's
 * light kept per colour, which the game doesn't do. Read by {@code LightTintMixin}.
 */
public final class LightTint {
	/** How far round the camera lights are looked for (blocks, each way), and how often (ticks). */
	static final int RANGE = 8, EVERY = 10;
	/** How much of the way a tick the colour eases to the one the lights round it give, and the most it moves from warm. */
	static final float EASE = 0.08F, MOST = 0.85F;
	/** A place counts as fully lit its own colour once its coloured lights' weight (brightness by nearness) reaches this. */
	static final float FULL = 12.0F;

	static final Vector3fc SOUL = new Vector3f(0.35F, 0.72F, 1.0F), REDSTONE = new Vector3f(1.0F, 0.22F, 0.14F),
		COOL = new Vector3f(0.78F, 0.94F, 1.0F), VIOLET = new Vector3f(0.74F, 0.42F, 1.0F), COPPER = new Vector3f(0.5F, 1.0F, 0.5F),
		LAVA = new Vector3f(1.0F, 0.42F, 0.14F), GOLDEN = new Vector3f(1.0F, 0.8F, 0.42F), SCULK = new Vector3f(0.45F, 0.95F, 0.95F),
		PEARL = new Vector3f(1.0F, 0.72F, 0.95F), VERDANT = new Vector3f(0.62F, 1.0F, 0.58F);

	private static final Map<Block, Vector3fc> COLOURS = new IdentityHashMap<>();
	/** The colour the lights round the camera give (null: warm, the game's own) and how much of it there is (0..1). */
	private static final Vector3f TARGET = new Vector3f(1.0F);
	private static float targetShare, share;
	private static final Vector3f SHOWN = new Vector3f(1.0F);
	private static int clock;

	private LightTint() {
	}

	static void init() {
		ClientTickEvents.END_CLIENT_TICK.register(LightTint::tick);
	}

	/** The colour a light-giving block lights in, or null for the game's warm light. */
	static @Nullable Vector3fc colour(BlockState state) {
		Block block = state.getBlock();
		if (COLOURS.containsKey(block)) {
			return COLOURS.get(block);
		}
		String id = BuiltInRegistries.BLOCK.getKey(block).getPath();
		Vector3fc c = id.startsWith("soul_") ? SOUL
			: id.equals("redstone_lamp") ? GOLDEN
			: id.contains("redstone") ? REDSTONE
			: id.contains("copper_torch") || id.contains("copper_wall_torch") || id.contains("copper_lantern") ? COPPER
			: id.equals("sea_lantern") || id.equals("beacon") || id.equals("end_rod") || id.equals("conduit") ? COOL
			: id.contains("amethyst") || id.equals("crying_obsidian") || id.equals("respawn_anchor") || id.equals("nether_portal") ? VIOLET
			: id.equals("lava") || id.equals("magma_block") ? LAVA
			: id.equals("glowstone") || id.equals("shroomlight") || id.equals("ochre_froglight") ? GOLDEN
			: id.equals("glow_lichen") || id.contains("sculk") ? SCULK
			: id.equals("pearlescent_froglight") ? PEARL
			: id.equals("verdant_froglight") ? VERDANT
			: null;
		COLOURS.put(block, c);
		return c;
	}

	private static void tick(Minecraft minecraft) {
		ClientLevel level = minecraft.level;
		if (level == null || minecraft.player == null) {
			share = targetShare = 0.0F;
			return;
		}
		if (++clock % EVERY == 0) {
			look(level, minecraft.gameRenderer.mainCamera().position());
		}
		share = Mth.lerp(EASE, share, targetShare);
		SHOWN.lerp(TARGET, EASE);
	}

	/** Weighs the lights round {@code eye}: each counts its brightness by how near it is; warm ones count toward warm. */
	private static void look(ClientLevel level, Vec3 eye) {
		float warm = 0.0F, coloured = 0.0F, r = 0.0F, g = 0.0F, b = 0.0F;
		BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
		int ex = Mth.floor(eye.x), ey = Mth.floor(eye.y), ez = Mth.floor(eye.z);
		for (int dx = -RANGE; dx <= RANGE; dx++) {
			for (int dy = -RANGE; dy <= RANGE; dy++) {
				for (int dz = -RANGE; dz <= RANGE; dz++) {
					BlockState state = level.getBlockState(at.set(ex + dx, ey + dy, ez + dz));
					int emits = state.getLightEmission();
					if (emits <= 0) {
						continue;
					}
					float near = 1.0F - (float) Math.sqrt(dx * dx + dy * dy + dz * dz) / (RANGE + 1.0F);
					if (near <= 0.0F) {
						continue;
					}
					float w = emits / 15.0F * near * near;
					Vector3fc c = colour(state);
					if (c == null) {
						warm += w;
					} else {
						coloured += w;
						r += c.x() * w;
						g += c.y() * w;
						b += c.z() * w;
					}
				}
			}
		}
		if (coloured <= 0.0F) {
			targetShare = 0.0F;
			return;
		}
		TARGET.set(r / coloured, g / coloured, b / coloured);
		// a coloured light among warm ones colours the place by its share of the light, and only so much when it's dim
		targetShare = MOST * coloured / (coloured + warm) * Mth.clamp(coloured / FULL * 4.0F, 0.0F, 1.0F);
	}

	/** The block light's colour to use, from the game's own ({@code warm}). */
	public static Vector3fc tint(Vector3fc warm) {
		if (share <= 0.01F) {
			return warm;
		}
		return new Vector3f(warm).lerp(SHOWN, share);
	}
}
