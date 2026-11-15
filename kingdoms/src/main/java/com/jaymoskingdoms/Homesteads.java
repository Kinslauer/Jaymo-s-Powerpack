package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * (gb-tent) A tent, and the homestead it makes. Lay a 3 x 3 foundation of your own blocks and pitch a tent on its middle:
 * it claims the patch round it ({@link #RADIUS} either way). The realm's people count the patch as yours: what you
 * break or take there is no crime, and anyone else breaking it where a realm's law holds is a vandal ({@link Theft}).
 * One homestead each; pitching another moves it. It is where your own settlement starts: sneak and ring a bell you hung
 * in your homestead, coin enough on you, and your realm is founded at the tent ({@link Crowns#found}).
 * <p>
 * The tent is all ours: a small wool A-frame, a bedroll inside. Kept on the Overworld, by player.
 */
public final class Homesteads {
	/** The patch a homestead claims: this many blocks either way of its tent, and this far up and down. */
	static final int RADIUS = 6, HEIGHT = 8;
	/** The foundation: this far either way of the tent's middle (3 x 3). */
	static final int FOOT = 1;

	/** Player id → their tent's middle (the foundation block it stands on). Saved on the Overworld. */
	static final AttachmentType<Map<String, BlockPos>> HOMESTEADS = AttachmentRegistry.create(JaymosKingdoms.id("homesteads"),
		builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, BlockPos.CODEC)));

	public static Item TENT;

	private Homesteads() {
	}

	static void register() {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosKingdoms.id("tent"));
		TENT = Registry.register(BuiltInRegistries.ITEM, key, new Tent(new Item.Properties().setId(key).stacksTo(16)));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(output -> output.insertAfter(Items.MAP, TENT));
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (player instanceof ServerPlayer sp && level instanceof ServerLevel server && player.isShiftKeyDown()
				&& level.getBlockState(hit.getBlockPos()).is(Blocks.BELL)) {
				rang(sp, server, hit.getBlockPos());
			}
			return InteractionResult.PASS;
		});
	}

	// ---- whose patch ------------------------------------------------------------------------------------------

	private static Map<String, BlockPos> all(ServerLevel level) {
		return Factions.level(level.getServer()).getAttachedOrElse(HOMESTEADS, Map.of());
	}

	/** {@code player}'s homestead's tent, if they have one. */
	static @Nullable BlockPos of(ServerLevel level, UUID player) {
		return all(level).get(player.toString());
	}

	private static boolean within(BlockPos home, BlockPos pos) {
		return Math.abs(pos.getX() - home.getX()) <= RADIUS && Math.abs(pos.getZ() - home.getZ()) <= RADIUS && Math.abs(pos.getY() - home.getY()) <= HEIGHT;
	}

	/**
	 * Breaking {@code state} at {@code pos} is {@code player}'s own business: it's in their homestead and is the land's own
	 * (nobody placed it) and nobody's goods (no container, bed, door, workstation or bell). What they placed there
	 * themselves is theirs anyway ({@link Placed}).
	 */
	static boolean mayBreak(ServerPlayer player, ServerLevel level, BlockPos pos, BlockState state, net.minecraft.world.level.block.entity.@Nullable BlockEntity entity) {
		if (!owns(player, level, pos) || Placed.any(level, pos) || entity != null || state.is(net.minecraft.tags.BlockTags.BEDS)
			|| state.is(net.minecraft.tags.BlockTags.DOORS) || state.is(net.minecraft.tags.BlockTags.TRAPDOORS) || state.is(net.minecraft.tags.BlockTags.FENCE_GATES)
			|| state.is(Blocks.BELL)) {
			return false;
		}
		return net.minecraft.world.entity.ai.village.poi.PoiTypes.forState(state).isEmpty();
	}

	/** {@code pos} is in {@code player}'s own homestead. */
	static boolean owns(ServerPlayer player, ServerLevel level, BlockPos pos) {
		if (level != Factions.level(level.getServer())) {
			return false;
		}
		BlockPos home = of(level, player.getUUID());
		if (home == null || !within(home, pos)) {
			return false;
		}
		// (never over another's patch, should two ever touch)
		String me = player.getUUID().toString();
		for (var e : all(level).entrySet()) {
			if (!e.getKey().equals(me) && within(e.getValue(), pos)) {
				return false;
			}
		}
		return true;
	}

	/** Whose homestead {@code pos} is in (null if nobody's). */
	static @Nullable UUID ownerAt(ServerLevel level, BlockPos pos) {
		if (level != Factions.level(level.getServer())) {
			return null;
		}
		for (var e : all(level).entrySet()) {
			if (within(e.getValue(), pos)) {
				return UUID.fromString(e.getKey());
			}
		}
		return null;
	}

	// ---- pitching -----------------------------------------------------------------------------------------------

	/** The tent: used on the middle of a laid 3 x 3 foundation of your own blocks, it pitches there and claims the patch. */
	static final class Tent extends Item {
		Tent(Properties properties) {
			super(properties);
		}

		@Override
		public InteractionResult useOn(UseOnContext context) {
			if (!(context.getLevel() instanceof ServerLevel level) || !(context.getPlayer() instanceof ServerPlayer player)) {
				return InteractionResult.SUCCESS;
			}
			BlockPos middle = context.getClickedPos();
			Direction front = context.getHorizontalDirection().getOpposite();
			if (level != Factions.level(level.getServer()) || !pitch(level, player, middle, front)) {
				level.playSound(null, middle, SoundEvents.WOOL_FALL, SoundSource.PLAYERS, 1.0F, 0.6F);
				return InteractionResult.FAIL;
			}
			context.getItemInHand().consume(1, player);
			return InteractionResult.SUCCESS_SERVER;
		}
	}

	/**
	 * Pitch {@code player}'s tent on the foundation round {@code middle}, its door to {@code front}. False (and where
	 * it falls short shown in smoke) if the foundation isn't all laid by them, or there's no room above it.
	 */
	private static boolean pitch(ServerLevel level, ServerPlayer player, BlockPos middle, Direction front) {
		if (!free(level, player, middle)) {
			// on or near a settlement's land, or over someone else's patch: no claim here
			level.sendParticles(player, ParticleTypes.LARGE_SMOKE, true, false, middle.getX() + 0.5, middle.getY() + 1.2, middle.getZ() + 0.5, 12, 0.6, 0.3, 0.6, 0.0);
			return false;
		}
		boolean ok = true;
		for (int dx = -FOOT; dx <= FOOT; dx++) {
			for (int dz = -FOOT; dz <= FOOT; dz++) {
				BlockPos p = middle.offset(dx, 0, dz);
				BlockState s = level.getBlockState(p);
				if (!s.isCollisionShapeFullBlock(level, p) || !Placed.byPlayer(player, level, p, s)) {
					ok = false;
					level.sendParticles(player, ParticleTypes.SMOKE, true, false, p.getX() + 0.5, p.getY() + 1.05, p.getZ() + 0.5, 4, 0.25, 0.0, 0.25, 0.0);
				}
			}
		}
		Map<BlockPos, BlockState> tent = tent(middle, front);
		for (BlockPos p : tent.keySet()) {
			if (!level.getBlockState(p).canBeReplaced()) {
				ok = false;
				level.sendParticles(player, ParticleTypes.SMOKE, true, false, p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, 4, 0.2, 0.2, 0.2, 0.0);
			}
		}
		for (int y = 1; y <= 2; y++) {
			if (!level.getBlockState(middle.above(y)).canBeReplaced()) {
				ok = false;
			}
		}
		if (!ok) {
			return false;
		}
		for (var e : tent.entrySet()) {
			level.setBlock(e.getKey(), e.getValue(), 3);
			Placed.placed(player, level, e.getKey(), e.getValue());
		}
		ServerLevel world = Factions.level(level.getServer());
		Map<String, BlockPos> all = new HashMap<>(all(world));
		all.put(player.getUUID().toString(), middle.immutable());
		world.setAttached(HOMESTEADS, all);
		level.playSound(null, middle, SoundEvents.WOOL_PLACE, SoundSource.PLAYERS, 1.0F, 0.9F);
		// the patch it claims, marked at its corners
		for (int sx = -1; sx <= 1; sx += 2) {
			for (int sz = -1; sz <= 1; sz += 2) {
				BlockPos c = middle.offset(sx * RADIUS, 0, sz * RADIUS);
				int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, c.getX(), c.getZ());
				level.sendParticles(ParticleTypes.HAPPY_VILLAGER, c.getX() + 0.5, y + 0.3, c.getZ() + 0.5, 8, 0.3, 0.4, 0.3, 0.0);
			}
		}
		return true;
	}

	/**
	 * A patch round {@code middle} may be claimed by {@code player}: it lies clear of every settlement (its village and
	 * the land it claims, and a village not yet anyone's) and overlaps no one else's homestead.
	 */
	private static boolean free(ServerLevel level, ServerPlayer player, BlockPos middle) {
		net.minecraft.server.MinecraftServer server = level.getServer();
		if (Factions.around(server, level, middle, Villages.VILLAGE_RADIUS + RADIUS) != null
			|| Seats.structureAt(level, middle, Seats::village).status() != Seats.Status.NONE) {
			return false;
		}
		for (int sx = -1; sx <= 1; sx++) {
			for (int sz = -1; sz <= 1; sz++) {
				if (Factions.at(server, level, middle.offset(sx * RADIUS, 0, sz * RADIUS)) != null) {
					return false;
				}
			}
		}
		String me = player.getUUID().toString();
		for (var e : all(level).entrySet()) {
			BlockPos other = e.getValue();
			if (!e.getKey().equals(me) && Math.abs(other.getX() - middle.getX()) <= 2 * RADIUS && Math.abs(other.getZ() - middle.getZ()) <= 2 * RADIUS
				&& Math.abs(other.getY() - middle.getY()) <= 2 * HEIGHT) {
				return false;
			}
		}
		return true;
	}

	/** The tent's blocks over the foundation at {@code middle}: wool walls each side, a ridge, a closed back and a bedroll. */
	private static Map<BlockPos, BlockState> tent(BlockPos middle, Direction front) {
		Direction side = front.getClockWise();
		BlockState wall = Blocks.WOOL.pick(DyeColor.WHITE).defaultBlockState(), ridge = Blocks.WOOL.pick(DyeColor.BROWN).defaultBlockState();
		Map<BlockPos, BlockState> out = new HashMap<>();
		for (int d = -1; d <= 1; d++) {
			BlockPos row = middle.relative(front, d);
			for (int y = 1; y <= 2; y++) {
				out.put(row.relative(side, 1).above(y), wall);
				out.put(row.relative(side, -1).above(y), wall);
			}
			out.put(row.above(3), ridge);
		}
		BlockPos back = middle.relative(front, -1);
		out.put(back.above(1), wall);
		out.put(back.above(2), wall);
		out.put(middle.above(1), Blocks.CARPET.pick(DyeColor.LIGHT_GRAY).defaultBlockState());
		return out;
	}

	// ---- founding ---------------------------------------------------------------------------------------------

	/** {@code player} sneaked and rang the bell at {@code bell}: one they hung in their homestead founds their realm at the tent. */
	private static void rang(ServerPlayer player, ServerLevel level, BlockPos bell) {
		BlockPos home = of(level, player.getUUID());
		if (home == null || !owns(player, level, bell) || !Placed.byPlayer(player, level, bell, level.getBlockState(bell))) {
			return;
		}
		Component refused = Crowns.found(player, null, home.above());
		level.playSound(null, bell, SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 2.0F, refused == null ? 0.8F : 1.4F);
		if (refused != null) {
			// not here, or not yet: a cracked note and smoke, nothing said
			level.playSound(null, bell, SoundEvents.VILLAGER_NO, SoundSource.PLAYERS, 0.6F, 0.8F);
			level.sendParticles(player, ParticleTypes.SMOKE, true, false, bell.getX() + 0.5, bell.getY() + 0.8, bell.getZ() + 0.5, 10, 0.3, 0.3, 0.3, 0.0);
		}
	}
}
