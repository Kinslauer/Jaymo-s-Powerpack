package com.jaymospowers.power.arsenal;

import com.jaymospowers.JaymosPowers;
import com.mojang.serialization.Codec;
import java.lang.reflect.Method;
import java.util.Set;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * (0.13.36) Emberborn rifts into the Nether. An Emberborn (a Jaymo's Kingdoms heritage, read by reflection only) who
 * uses Pocket Arsenal and hasn't yet escaped the Nether may find that the rift Z opens is a Nether rift
 * ({@link #NETHER_CHANCE}, rolled when it opens): walking through lands them in the Nether and strands them there.
 * While stranded, Z opens the ordinary pocket rift, whose way out leads back to the Nether, so the pocket is no way
 * home. Reaching the Overworld by any means ends it and gives the toggle: Sneak + Z flips their rift between the
 * pocket and the Nether, from then on.
 */
public final class ArsenalEmber {
	/** The chance, for an Emberborn who hasn't escaped yet, that a rift opened in the Overworld leads to the Nether. */
	static final double NETHER_CHANCE = 0.3;
	/** Overworld to Nether coordinates are divided by this (and multiplied by it going back). */
	static final int SCALE = 8;
	/** The Nether's floor is searched from TOP down to BOTTOM, within RADIUS blocks of the spot; else a platform at PLATFORM_Y. */
	static final int NETHER_TOP = 120, NETHER_BOTTOM = 32, SEARCH_RADIUS = 4, PLATFORM_Y = 64;
	/** How far out the Overworld goes (so x * 8 stays inside the world border's limit). */
	static final int WORLD_LIMIT = 29_999_000;
	/** How often (ticks) a stranded player is checked for having reached the Overworld. */
	static final int CHECK_EVERY = 20;

	/** The player went through a random Nether rift and hasn't been back to the Overworld since. */
	public static final AttachmentType<Boolean> STRANDED = AttachmentRegistry.create(JaymosPowers.id("arsenal_ember_stranded"),
		builder -> builder.persistent(Codec.BOOL).copyOnDeath());
	/** The player escaped the Nether once: random Nether rifts stop and Sneak + Z chooses where rifts lead. */
	public static final AttachmentType<Boolean> ESCAPED = AttachmentRegistry.create(JaymosPowers.id("arsenal_ember_escaped"),
		builder -> builder.persistent(Codec.BOOL).copyOnDeath());
	/** Once escaped: whether their rifts lead to the Nether (true) or to the pocket (false). */
	public static final AttachmentType<Boolean> NETHER_MODE = AttachmentRegistry.create(JaymosPowers.id("arsenal_ember_nether_mode"),
		builder -> builder.persistent(Codec.BOOL).copyOnDeath());

	private static final boolean LOADED = FabricLoader.getInstance().isModLoaded("jaymoskingdoms");
	private static Method IS;
	private static Object EMBERBORN;

	static {
		if (LOADED) {
			try {
				Class<?> heritage = Class.forName("com.jaymoskingdoms.Heritage");
				Class<?> kind = Class.forName("com.jaymoskingdoms.Heritage$Kind");
				IS = heritage.getMethod("is", Player.class, kind);
				EMBERBORN = kind.getField("EMBERBORN").get(null);
			} catch (ReflectiveOperationException | LinkageError e) {
				IS = null;
			}
		}
	}

	private ArsenalEmber() {
	}

	/** Whether this player's Kingdoms heritage is Emberborn (never without Kingdoms, or if anything fails). */
	public static boolean emberborn(Player player) {
		if (IS == null || EMBERBORN == null) {
			return false;
		}
		try {
			return (boolean)IS.invoke(null, player, EMBERBORN);
		} catch (ReflectiveOperationException | RuntimeException e) {
			return false;
		}
	}

	public static boolean stranded(Player player) {
		return Boolean.TRUE.equals(player.getAttached(STRANDED));
	}

	public static boolean escaped(Player player) {
		return Boolean.TRUE.equals(player.getAttached(ESCAPED));
	}

	public static boolean netherMode(Player player) {
		return Boolean.TRUE.equals(player.getAttached(NETHER_MODE));
	}

	/**
	 * Whether the rift this player opens now (with Z, outside the pocket) leads to the Nether. Rolled once, as it opens.
	 * Stranded: never (the pocket is the only rift they get). Escaped: whichever they chose, from the Overworld or the
	 * Nether. Not escaped yet: a {@link #NETHER_CHANCE} chance, from the Overworld only.
	 */
	static boolean netherRift(ServerPlayer player, ServerLevel level) {
		if (!emberborn(player) || stranded(player)) {
			return false;
		}
		boolean home = level.dimension() == Level.OVERWORLD;
		if (escaped(player)) {
			return netherMode(player) && (home || level.dimension() == Level.NETHER);
		}
		return home && level.getRandom().nextDouble() < NETHER_CHANCE;
	}

	/** Sneak + Z: flips an escaped Emberborn's rift between the pocket and the Nether. True when it handled the key. */
	static boolean flip(ServerPlayer player) {
		if (!emberborn(player) || !escaped(player)) {
			return false;
		}
		boolean nether = !netherMode(player);
		player.setAttached(NETHER_MODE, nether);
		player.sendOverlayMessage(Component.translatable(nether ? "message.jaymospowers.arsenal.ember_nether" : "message.jaymospowers.arsenal.ember_pocket")
			.withStyle(nether ? ChatFormatting.GOLD : ChatFormatting.LIGHT_PURPLE));
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 0.4F, nether ? 0.8F : 1.6F);
		return true;
	}

	/** Every {@link #CHECK_EVERY} ticks: a stranded player who is in the Overworld has escaped. */
	static void tick(ServerPlayer player) {
		if (player.tickCount % CHECK_EVERY != 0 || !stranded(player) || player.level().dimension() != Level.OVERWORLD) {
			return;
		}
		player.removeAttached(STRANDED);
		player.setAttached(ESCAPED, true);
		player.sendOverlayMessage(Component.translatable("message.jaymospowers.arsenal.ember_escaped").withStyle(ChatFormatting.GOLD));
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 0.8F, 1.2F);
	}

	/** The fire a Nether rift gives off (instead of the pocket rift's portal motes). */
	static void flames(ServerLevel level, Vec3 c, int count) {
		level.sendParticles(ParticleTypes.FLAME, c.x, c.y, c.z, count, 0.3, 0.7, 0.3, 0.05);
		level.sendParticles(ParticleTypes.LARGE_SMOKE, c.x, c.y + 0.5, c.z, Math.max(1, count / 3), 0.3, 0.6, 0.3, 0.02);
	}

	/** A rift opens: fire and a blaze's breath, so it reads differently from the pocket's. */
	static void opened(ServerLevel level, Vec3 at) {
		flames(level, at.add(0.0, 1.0, 0.0), 40);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 0.7F, 0.7F);
	}

	/**
	 * Through a Nether rift: from the Overworld to the Nether (x / 8, z / 8, on safe ground), from the Nether to the
	 * Overworld (x * 8, z * 8, on the surface). {@code owner}: it's the opener walking in, who is stranded by a random
	 * one (not once they've escaped).
	 */
	static void cross(ServerPlayer player, boolean owner) {
		MinecraftServer server = player.level().getServer();
		ServerLevel from = player.level();
		boolean toNether = from.dimension() != Level.NETHER;
		ServerLevel to = server.getLevel(toNether ? Level.NETHER : Level.OVERWORLD);
		if (to == null) {
			return;
		}
		Vec3 at;
		if (toNether) {
			at = netherSpot(to, Math.floorDiv(player.getBlockX(), SCALE), Math.floorDiv(player.getBlockZ(), SCALE));
		} else {
			int x = Math.max(-WORLD_LIMIT, Math.min(WORLD_LIMIT, player.getBlockX() * SCALE));
			int z = Math.max(-WORLD_LIMIT, Math.min(WORLD_LIMIT, player.getBlockZ() * SCALE));
			BlockPos top = to.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, z));
			at = Vec3.atBottomCenterOf(top);
		}
		if (player.isPassenger()) {
			player.stopRiding();
		}
		flames(from, player.position().add(0.0, 1.0, 0.0), 40);
		player.teleportTo(to, at.x, at.y, at.z, Set.of(), player.getYRot(), player.getXRot(), true);
		flames(to, at.add(0.0, 1.0, 0.0), 40);
		to.playSound(null, at.x, at.y, at.z, SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 0.8F, 0.6F);
		if (toNether && owner && emberborn(player) && !escaped(player)) {
			player.setAttached(STRANDED, true);
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.arsenal.ember_stranded").withStyle(ChatFormatting.RED));
		}
	}

	/** Safe ground near (x, z) in the Nether: a solid floor with two air above and no lava; else a small platform. */
	private static Vec3 netherSpot(ServerLevel nether, int x, int z) {
		BlockPos.MutableBlockPos feet = new BlockPos.MutableBlockPos();
		for (int r = 0; r <= SEARCH_RADIUS; r++) {
			for (int dx = -r; dx <= r; dx++) {
				for (int dz = -r; dz <= r; dz++) {
					if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
						continue;
					}
					for (int y = NETHER_TOP; y >= NETHER_BOTTOM; y--) {
						feet.set(x + dx, y, z + dz);
						if (safe(nether, feet)) {
							return Vec3.atBottomCenterOf(feet.immutable());
						}
					}
				}
			}
		}
		BlockState obsidian = Blocks.OBSIDIAN.defaultBlockState();
		BlockState netherrack = Blocks.NETHERRACK.defaultBlockState();
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				nether.setBlock(new BlockPos(x + dx, PLATFORM_Y - 1, z + dz), dx == 0 && dz == 0 ? obsidian : netherrack, Block.UPDATE_CLIENTS);
				for (int h = 0; h < 3; h++) {
					nether.setBlock(new BlockPos(x + dx, PLATFORM_Y + h, z + dz), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
				}
			}
		}
		return Vec3.atBottomCenterOf(new BlockPos(x, PLATFORM_Y, z));
	}

	private static boolean safe(ServerLevel level, BlockPos feet) {
		BlockPos floor = feet.below();
		return level.getBlockState(feet).isAir() && level.getBlockState(feet.above()).isAir() && level.getFluidState(feet).isEmpty()
			&& level.getFluidState(feet.above()).isEmpty() && level.getBlockState(floor).isFaceSturdy(level, floor, Direction.UP)
			&& level.getFluidState(floor).isEmpty();
	}
}
