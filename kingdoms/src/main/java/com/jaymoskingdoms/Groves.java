package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Groves, and the old druid who keeps one (Andy's pick E5). A grove is a stand of old wood: enough trunks and enough leaves
 * round a spot ({@link #isGrove}). In the forests, now and then, an old druid has made one their own: a hermit who stands
 * in it all year and will give a new body to anyone dead for good whose body is laid at their feet ({@link Rebirths}). A place
 * is rolled once for good, like the bog lights and the caves' moulds.
 */
public final class Groves {
	// ---- tuning ----
	/** A grove: at least this many trunk blocks and leaf blocks within this reach (blocks across, up). */
	static final int REACH = 7, HEIGHT = 11, LOGS = 5, LEAVES = 40;
	/** Looked at this often (ticks); a cell this big (blocks across) is rolled once, with this chance of a druid. */
	static final int EVERY = 400, CELL = 128;
	static final float DRUID = 0.35F;
	/** The scoreboard tag an old druid carries. */
	public static final String DRUID_TAG = "jaymoskingdoms_old_druid";

	private static final AttachmentType<List<Long>> ROLLED = AttachmentRegistry.create(JaymosKingdoms.id("druids_rolled"),
		b -> b.persistent(Codec.LONG.listOf()));
	private static Set<Long> rolled;

	private Groves() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Groves::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> rolled = null);
	}

	/** Whether the wood round {@code at} is a grove: trunks and canopy enough. */
	public static boolean isGrove(ServerLevel level, BlockPos at) {
		int logs = 0, leaves = 0;
		for (BlockPos p : BlockPos.betweenClosed(at.offset(-REACH, -3, -REACH), at.offset(REACH, HEIGHT, REACH))) {
			if (!level.isLoaded(p)) {
				continue;
			}
			BlockState s = level.getBlockState(p);
			if (s.is(BlockTags.LOGS)) {
				logs++;
			} else if (s.is(BlockTags.LEAVES)) {
				leaves++;
			}
			if (logs >= LOGS && leaves >= LEAVES) {
				return true;
			}
		}
		return false;
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % EVERY != 0) {
			return;
		}
		ServerLevel level = server.overworld();
		for (ServerPlayer player : level.players()) {
			if (player.isSpectator() || !player.isAlive() || !level.getBiome(player.blockPosition()).is(BiomeTags.IS_FOREST)) {
				continue;
			}
			try {
				place(level, player);
			} catch (RuntimeException e) {
				JaymosKingdoms.LOGGER.debug("druid scan failed", e);
			}
		}
	}

	private static void place(ServerLevel level, ServerPlayer player) {
		BlockPos here = player.blockPosition();
		long cell = ((long) Math.floorDiv(here.getX(), CELL) << 32) ^ (Math.floorDiv(here.getZ(), CELL) & 0xFFFFFFFFL);
		if (rolled == null) {
			rolled = new HashSet<>(level.getAttachedOrElse(ROLLED, List.<Long>of()));
		}
		if (rolled.contains(cell)) {
			return;
		}
		RandomSource random = level.getRandom();
		for (int tries = 0; tries < 12; tries++) {
			BlockPos spot = here.offset(random.nextInt(48) - 24, 0, random.nextInt(48) - 24);
			if (Math.abs(spot.getX() - here.getX()) < 12 && Math.abs(spot.getZ() - here.getZ()) < 12 || !level.isLoaded(spot)) {
				continue;
			}
			BlockPos ground = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, spot);
			if (!level.getBiome(ground).is(BiomeTags.IS_FOREST) || !level.getFluidState(ground.below()).isEmpty() || !isGrove(level, ground)) {
				continue;
			}
			rolled.add(cell);
			level.setAttached(ROLLED, new ArrayList<>(rolled));
			if (random.nextFloat() < DRUID) {
				druidAt(level, ground);
			}
			return;
		}
	}

	/** An old druid, standing at {@code ground}. */
	static Villager druidAt(ServerLevel level, BlockPos ground) {
		Villager druid = new Villager(net.minecraft.world.entity.EntityTypes.VILLAGER, level);
		druid.setVillagerData(druid.getVillagerData().withProfession(BuiltInRegistries.VILLAGER_PROFESSION.getOrThrow(VillagerProfession.NITWIT)));
		druid.setPos(ground.getX() + 0.5, ground.getY(), ground.getZ() + 0.5);
		druid.setCustomName(Component.translatable("entity.jaymoskingdoms.old_druid"));
		druid.setCustomNameVisible(false);
		druid.addTag(DRUID_TAG);
		druid.setPersistenceRequired();
		druid.setNoAi(true);
		level.addFreshEntity(druid);
		return druid;
	}
}
