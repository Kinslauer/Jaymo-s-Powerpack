package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.AbstractBannerBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.WallBannerBlock;
import net.minecraft.world.level.block.entity.BannerBlockEntity;
import net.minecraft.world.level.block.entity.BannerPatternLayers;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jspecify.annotations.Nullable;

/**
 * Castles change hands (medieval pick gb-castles; lore "The realms' ways": "keeps change hands in war and fly the
 * winner's colours"). A seat taken in war goes to the winner as its fiefdom ({@link Wars#seatTaken}, the vassal terms of
 * {@link Wars#conclude}); its keep then flies its holder's colours ({@link Outfits#banner}: the overlord's field and
 * charges, bordered for a vassal). A fiefdom freed flies its own again.
 * <p>
 * What each seat flies is saved ({@link #FLOWN}), first as it's found: its own arms as {@link Outfits#heraldry} has
 * them (a haven's blood's arms too), so nothing is repainted without a change of holder. Only the keep's own banners
 * are rewoven: within its walls ({@link Factions#keep}, {@link #KEEP_PAD}), the realm's plain banners of its colour (as
 * {@link Castle} hangs them) and any bearing its full arms; about a seat without a keep ({@link #RANGE}), only those
 * bearing its full arms. A player's own banners are left alone. The work goes chunk by chunk as they load, each done
 * once ({@link Flown#done}).
 */
final class KeepColours {
	/** How often the seats are looked at (ticks). */
	static final int CHECK_TICKS = 400;
	/** About a seat without a keep: banners within this of it (blocks across), and this far below and above it. */
	static final int RANGE = 40, BELOW = 12, ABOVE = 32;
	/** A keep's walls: this far out from its throne, hall, gate, cellar and posts, this far below them and above. */
	static final int KEEP_PAD = 8, KEEP_BELOW = 4, KEEP_ABOVE = 16;

	/**
	 * What a seat flies ({@code dye}, a dye id, and {@code arms}, its banners' layers as text) and, while its banners are
	 * being changed, what they're changing to and the chunks already done.
	 */
	record Flown(int dye, String arms, int toDye, String toArms, List<Long> done) {
		static final Codec<Flown> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.INT.fieldOf("dye").forGetter(Flown::dye),
				Codec.STRING.fieldOf("arms").forGetter(Flown::arms),
				Codec.INT.fieldOf("to_dye").forGetter(Flown::toDye),
				Codec.STRING.fieldOf("to_arms").forGetter(Flown::toArms),
				Codec.LONG.listOf().fieldOf("done").forGetter(Flown::done))
			.apply(i, Flown::new));

		static Flown of(int dye, String arms) {
			return new Flown(dye, arms, dye, arms, List.of());
		}
	}

	/** Each seat's (faction id) colours as its banners fly them now. Saved on the Overworld. */
	static final AttachmentType<Map<String, Flown>> FLOWN = AttachmentRegistry.create(JaymosKingdoms.id("keep_arms"),
		builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, Flown.CODEC)));

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

	/** {@code layers} as text, to tell one set of arms from another. */
	private static String arms(@Nullable BannerPatternLayers layers) {
		if (layers == null) {
			return "";
		}
		return layers.layers().stream()
			.map(l -> l.pattern().unwrapKey().map(k -> k.identifier().toString()).orElse("?") + "/" + l.color().getName())
			.collect(Collectors.joining(","));
	}

	private static void check(MinecraftServer server) {
		ServerLevel level = Factions.level(server);
		Map<String, Flown> flown = level.getAttachedOrElse(FLOWN, Map.of());
		Map<String, Flown> next = new HashMap<>(flown);
		for (Faction f : Factions.all(server)) {
			if (!f.type().isRealm() || f.fallen()) {
				continue;
			}
			String key = Integer.toString(f.id());
			ItemStack banner = Outfits.banner(level, f);
			int dye = holder(server, f).getId();
			String arms = arms(banner.get(DataComponents.BANNER_PATTERNS));
			Flown was = flown.get(key);
			if (was == null) {
				// (first seen: what it flies now is its own, nothing to change)
				next.put(key, Flown.of(dye, arms));
				continue;
			}
			if (was.dye() == dye && was.arms().equals(arms)) {
				if (!was.done().isEmpty() || was.toDye() != dye) {
					next.put(key, Flown.of(dye, arms));
				}
				continue;
			}
			Flown going = was.toDye() == dye && was.toArms().equals(arms) ? was : new Flown(was.dye(), was.arms(), dye, arms, List.of());
			next.put(key, repaint(level, f, going, banner));
		}
		if (!next.equals(flown)) {
			level.setAttached(FLOWN, Map.copyOf(next));
		}
	}

	/** Rewaves {@code f}'s keep's banners chunk by chunk, as far as they're loaded: what's done, or the new colours flown once all is. */
	private static Flown repaint(ServerLevel level, Faction f, Flown going, ItemStack banner) {
		Castle.Keep keep = Factions.keep(level.getServer(), f.id());
		BoundingBox box = keep == null ? box(f.seat()) : box(keep);
		DyeColor was = DyeColor.byId(going.dye()), now = DyeColor.byId(going.toDye());
		List<Long> done = new ArrayList<>(going.done());
		boolean all = true, any = false;
		for (int cx = box.minX() >> 4; cx <= box.maxX() >> 4; cx++) {
			for (int cz = box.minZ() >> 4; cz <= box.maxZ() >> 4; cz++) {
				long chunk = ChunkPos.pack(cx, cz);
				if (done.contains(chunk)) {
					continue;
				}
				if (!level.hasChunk(cx, cz)) {
					all = false;
					continue;
				}
				int x0 = Math.max(cx << 4, box.minX()), x1 = Math.min((cx << 4) + 15, box.maxX());
				int z0 = Math.max(cz << 4, box.minZ()), z1 = Math.min((cz << 4) + 15, box.maxZ());
				for (BlockPos p : BlockPos.betweenClosed(x0, box.minY(), z0, x1, box.maxY(), z1)) {
					BlockState state = level.getBlockState(p);
					if (!(state.getBlock() instanceof AbstractBannerBlock b) || b.getColor() != was
						|| !(level.getBlockEntity(p) instanceof BannerBlockEntity old)) {
						continue;
					}
					String bears = arms(old.getPatterns());
					if (!bears.equals(going.arms()) && !(keep != null && bears.isEmpty())) {
						// (not the realm's: someone's own banner)
						continue;
					}
					Block kind = state.getBlock() instanceof WallBannerBlock ? Blocks.WALL_BANNER.pick(now) : Blocks.BANNER.pick(now);
					BlockPos at = p.immutable();
					BlockState put = kind.withPropertiesOf(state);
					level.setBlock(at, put, Block.UPDATE_ALL);
					if (level.getBlockEntity(at) instanceof BannerBlockEntity be) {
						be.applyComponentsFromItemStack(banner);
						be.setChanged();
						level.sendBlockUpdated(at, put, put, Block.UPDATE_ALL);
					}
					any = true;
				}
				done.add(chunk);
			}
		}
		if (any) {
			level.playSound(null, f.seat(), SoundEvents.ARMOR_EQUIP_LEATHER.value(), SoundSource.BLOCKS, 1.0F, 0.7F);
		}
		return all ? Flown.of(going.toDye(), going.toArms()) : new Flown(going.dye(), going.arms(), going.toDye(), going.toArms(), List.copyOf(done));
	}

	private static BoundingBox box(BlockPos seat) {
		int half = RANGE / 2;
		return new BoundingBox(seat.getX() - half, seat.getY() - BELOW, seat.getZ() - half, seat.getX() + half, seat.getY() + ABOVE, seat.getZ() + half);
	}

	private static BoundingBox box(Castle.Keep keep) {
		List<BlockPos> points = new ArrayList<>(List.of(keep.throne(), keep.cellar(), keep.gate(), keep.hall()));
		points.addAll(keep.posts());
		BoundingBox box = BoundingBox.encapsulatingPositions(points).orElseThrow();
		return new BoundingBox(box.minX() - KEEP_PAD, box.minY() - KEEP_BELOW, box.minZ() - KEEP_PAD, box.maxX() + KEEP_PAD, box.maxY() + KEEP_ABOVE,
			box.maxZ() + KEEP_PAD);
	}
}
