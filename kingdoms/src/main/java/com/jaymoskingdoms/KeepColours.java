package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.AbstractBannerBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.WallBannerBlock;
import net.minecraft.world.level.block.entity.BannerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Castles change hands (medieval pick gb-castles; lore "The realms' ways": "keeps change hands in war and fly the
 * winner's colours"). A seat taken in war goes to the winner as its fiefdom ({@link Wars#seatTaken}, the vassal terms of
 * {@link Wars#conclude}); its keep then flies its holder's colours: every banner about the seat ({@link #RANGE}) in the
 * colour it flew before is taken down and the holder's arms put up in its place ({@link Outfits#banner}: the overlord's
 * field and charges, bordered for a vassal). A fiefdom freed flies its own again. Done once the seat's ground is loaded;
 * what each seat flies is saved ({@link #FLOWN}). The map shows the holders ({@link RealmMap}).
 */
final class KeepColours {
	/** How often the seats are looked at (ticks). */
	static final int CHECK_TICKS = 400;
	/** The banners within this of the seat (blocks across), and this far below and above it, are the keep's. */
	static final int RANGE = 40, BELOW = 12, ABOVE = 32;

	/** Each seat's colour (faction id → dye id) as its banners fly it now. Saved on the Overworld. */
	static final AttachmentType<Map<String, Integer>> FLOWN = AttachmentRegistry.create(JaymosKingdoms.id("keep_colours"),
		builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, Codec.INT)));

	private KeepColours() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % CHECK_TICKS == 0) {
				check(server);
			}
		});
	}

	/** The colour {@code f}'s keep should fly: its holder's field (an overlord's, for a fiefdom). */
	static DyeColor holder(MinecraftServer server, Faction f) {
		return Outfits.heraldry(f, server).field();
	}

	private static void check(MinecraftServer server) {
		ServerLevel level = Factions.level(server);
		Map<String, Integer> flown = level.getAttachedOrElse(FLOWN, Map.of());
		Map<String, Integer> next = null;
		for (Faction f : Factions.all(server)) {
			if (!f.type().isRealm() || f.fallen()) {
				continue;
			}
			String key = Integer.toString(f.id());
			DyeColor was = DyeColor.byId(flown.getOrDefault(key, f.color().getId()));
			DyeColor now = holder(server, f);
			if (was == now || !level.hasChunkAt(f.seat())) {
				continue;
			}
			if (repaint(level, f, was, now)) {
				if (next == null) {
					next = new HashMap<>(flown);
				}
				next.put(key, now.getId());
			}
		}
		if (next != null) {
			level.setAttached(FLOWN, Map.copyOf(next));
		}
	}

	/** {@code f}'s keep's banners in {@code was} put up again in its holder's arms ({@code now}); true if all its ground was loaded. */
	private static boolean repaint(ServerLevel level, Faction f, DyeColor was, DyeColor now) {
		BlockPos seat = f.seat();
		ItemStack arms = Outfits.banner(level, f);
		boolean all = true, any = false;
		int half = RANGE / 2;
		for (int cx = (seat.getX() - half) >> 4; cx <= (seat.getX() + half) >> 4; cx++) {
			for (int cz = (seat.getZ() - half) >> 4; cz <= (seat.getZ() + half) >> 4; cz++) {
				if (!level.hasChunk(cx, cz)) {
					all = false;
					continue;
				}
				int x0 = Math.max(cx << 4, seat.getX() - half), x1 = Math.min((cx << 4) + 15, seat.getX() + half);
				int z0 = Math.max(cz << 4, seat.getZ() - half), z1 = Math.min((cz << 4) + 15, seat.getZ() + half);
				for (BlockPos p : BlockPos.betweenClosed(x0, seat.getY() - BELOW, z0, x1, seat.getY() + ABOVE, z1)) {
					BlockState state = level.getBlockState(p);
					if (!(state.getBlock() instanceof AbstractBannerBlock banner) || banner.getColor() != was) {
						continue;
					}
					Block kind = state.getBlock() instanceof WallBannerBlock ? Blocks.WALL_BANNER.pick(now) : Blocks.BANNER.pick(now);
					BlockPos at = p.immutable();
					BlockState put = kind.withPropertiesOf(state);
					level.setBlock(at, put, Block.UPDATE_ALL);
					if (level.getBlockEntity(at) instanceof BannerBlockEntity be) {
						be.applyComponentsFromItemStack(arms);
						be.setChanged();
						level.sendBlockUpdated(at, put, put, Block.UPDATE_ALL);
					}
					any = true;
				}
			}
		}
		if (any) {
			level.playSound(null, seat, SoundEvents.ARMOR_EQUIP_LEATHER.value(), SoundSource.BLOCKS, 1.0F, 0.7F);
		}
		return all;
	}
}
