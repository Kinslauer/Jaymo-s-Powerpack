package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (am-homeraid, medieval picks; Armed Mobs' raids on the player's base, an idea only) At war, your own homestead
 * ({@link Homesteads}) can be marched on: a realm at war with yours sends a war party against it, rammers,
 * keg men and siege engines by how rich that realm is ({@link WarParties#startHome}), mustered a way off on the road from its seat. It lays siege in one wave
 * ({@link Sieges#HOME_WAVES}); if it's still standing at the end it breaks open what's stored there and carries it off
 * ({@link #sack}). Only while you're about (so you can hold it), at most once in {@link #GAP_TICKS}, and not a homestead
 * at your own realm's seat (its siege is the seat's). Your own settlement, crowned, is a seat like any other.
 */
final class HomeRaids {
	/** How often a homestead is considered (ticks), the chance it's marched on then, and the least gap between raids on one. */
	static final int CHECK_EVERY = 20 * 60;
	static final float CHANCE = 0.08F;
	static final long GAP_TICKS = 20L * 60 * 40;
	/** Its owner must be this near it; the enemy's seat no further than this; and the homestead this far from their own seat. */
	static final double OWNER_NEAR = 48.0, MARCH_FROM = 640.0, OWN_SEAT = 96.0;
	/** The party musters this far from the homestead, toward the enemy's seat. */
	static final double MUSTER_FAR = 56.0;
	/** Stacks it carries off from what's stored there. */
	static final int SACK_STACKS = 6;

	/** On a player: when their homestead was last marched on (game time). Saved. */
	static final AttachmentType<Long> RAIDED_AT = AttachmentRegistry.create(JaymosKingdoms.id("homestead_raided_at"),
		builder -> builder.persistent(Codec.LONG));

	private HomeRaids() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			ServerLevel level = Factions.level(server);
			if (level.getGameTime() % CHECK_EVERY == 0) {
				consider(server, level);
			}
		});
	}

	private static void consider(MinecraftServer server, ServerLevel level) {
		long now = level.getGameTime();
		for (ServerPlayer p : level.players()) {
			if (p.isCreative() || p.isSpectator() || WarParties.raidingHome(p.getUUID())
				|| now - p.getAttachedOrElse(RAIDED_AT, -GAP_TICKS) < GAP_TICKS) {
				continue;
			}
			BlockPos home = Homesteads.of(level, p.getUUID());
			Faction mine = Factions.of(server, p.getUUID());
			if (home == null || mine == null || p.distanceToSqr(Vec3.atCenterOf(home)) > OWNER_NEAR * OWNER_NEAR
				|| mine.seat().distSqr(home) < OWN_SEAT * OWN_SEAT || level.getRandom().nextFloat() >= CHANCE) {
				continue;
			}
			Faction from = enemy(server, mine, home);
			if (from == null) {
				continue;
			}
			Vec3 way = Vec3.atCenterOf(from.seat()).subtract(Vec3.atCenterOf(home)).multiply(1.0, 0.0, 1.0);
			Vec3 at = Vec3.atCenterOf(home).add(way.normalize().scale(MUSTER_FAR));
			BlockPos column = BlockPos.containing(at);
			if (!Loaded.made(level, column)) {
				continue;
			}
			BlockPos muster = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column);
			if (!level.getFluidState(muster).isEmpty() || !level.getFluidState(muster.below()).isEmpty()) {
				continue;
			}
			p.setAttached(RAIDED_AT, now);
			if (WarParties.startHome(level, from, p.getUUID(), home, muster) != null) {
				// a far horn, from the way they're coming
				level.playSound(null, muster, SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 4.0F, 0.9F);
			}
		}
	}

	/** The nearest realm at war with {@code mine} whose seat is within reach of {@code home}, or null. */
	private static @Nullable Faction enemy(MinecraftServer server, Faction mine, BlockPos home) {
		Faction best = null;
		double bestD = MARCH_FROM * MARCH_FROM;
		for (Faction f : Factions.all(server)) {
			if (f.fallen() || !f.type().isRealm() || !Wars.enemies(server, mine.id(), f.id())) {
				continue;
			}
			double d = f.seat().distSqr(home);
			if (d < bestD) {
				best = f;
				bestD = d;
			}
		}
		return best;
	}

	/** (WarParties) {@code party} took the homestead: what's stored there goes on its cart. */
	static void sack(ServerLevel level, WarParties.Party party) {
		BlockPos home = party.home;
		if (home == null) {
			return;
		}
		party.sacked = true;
		int taken = 0;
		int r = Homesteads.RADIUS, h = Homesteads.HEIGHT;
		for (BlockPos pos : BlockPos.betweenClosed(home.offset(-r, -h, -r), home.offset(r, h, r))) {
			if (taken >= SACK_STACKS) {
				break;
			}
			if (!(level.getBlockEntity(pos) instanceof Container c)) {
				continue;
			}
			for (int slot = 0; slot < c.getContainerSize() && taken < SACK_STACKS; slot++) {
				ItemStack stack = c.getItem(slot);
				if (!stack.isEmpty()) {
					party.spoils.add(c.removeItemNoUpdate(slot));
					taken++;
				}
			}
			c.setChanged();
			level.playSound(null, pos, SoundEvents.CHEST_OPEN, SoundSource.BLOCKS, 0.8F, 0.8F);
		}
	}
}
