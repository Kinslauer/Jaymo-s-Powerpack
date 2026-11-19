package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import org.jspecify.annotations.Nullable;

/**
 * Groves, and the old druid who keeps one (Andy's pick E5). A grove is a stand of old wood: enough trunks and enough leaves
 * round a spot ({@link #isGrove}). In the forests, now and then, an old druid has made one their own: a hermit who stands
 * in it all year and will give a new body to anyone dead for good whose body is laid at their feet ({@link Rebirths}). A place
 * is rolled once for good, like the bog lights and the caves' moulds.
 * <p>
 * Andy (2026-11-19, of one standing silent by a lake: "what is she doing here? she wont tell me anything"): she was only
 * ever written to speak to a Druid (the rite, {@link Rebirths#use}), and MCA's menu gave anyone else the cold word of a
 * villager wary of them. Now she has words of her own for anyone ({@code McaBridge.druidOpening}): who she is, what the
 * grove is for, and the one thing she wants ({@link #want}): a sapling or a growing thing of woods far from hers, picked
 * once from who she is. Held out to her, she takes it (once a day from each player), thinks better of you for it
 * ({@link #GIFT_HEARTS}), and plants it in her grove if there's ground for it ({@link #offer}). She minds no one's blood
 * and no realm's word of them ({@link Introductions#wary}).
 * <p>
 * Andy (2026-11-19): "she should also be found in any of the generating homes in the forests". The lone homes other mods
 * build in the woods ({@link #HOMES}: Homesteads' farmsteads, Moog's Voyager houses and huts, the Grim Kingdoms cabins,
 * Vanilla Constructs' lumberjack cabin), standing in a forest or a taiga, are each rolled once when a player comes near,
 * and {@link #HOME_DRUID} of them have an old druid living inside ({@link #moveIn}).
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
	/** What an old druid may want: a sapling or growing thing of woods unlike hers (one, from who she is). */
	static final Item[] WANTS = {Items.CHERRY_SAPLING, Items.MANGROVE_PROPAGULE, Items.JUNGLE_SAPLING, Items.ACACIA_SAPLING,
		Items.DARK_OAK_SAPLING, Items.PALE_OAK_SAPLING, Items.AZALEA, Items.FLOWERING_AZALEA};
	/** MCA hearts her want brings; how far round her she looks for ground to plant it (blocks). */
	static final int GIFT_HEARTS = 6, PLANT_REACH = 4;
	/** How many lines she has: openings, wordless openings, thanks, and the want said aloud. */
	public static final int OPENINGS = 4, THANKS = 3;
	/** The lone homes of the woods an old druid may live in, and the share of them that has one. */
	static final TagKey<Structure> HOMES = TagKey.create(Registries.STRUCTURE, JaymosKingdoms.id("druid_homes"));
	static final float HOME_DRUID = 0.35F;
	/** How far round a player a home is looked for (blocks, each way along the ground). */
	static final int HOME_LOOK = 16;

	private static final AttachmentType<List<Long>> ROLLED = AttachmentRegistry.create(JaymosKingdoms.id("druids_rolled"),
		b -> b.persistent(Codec.LONG.listOf()));
	private static Set<Long> rolled;
	private static final AttachmentType<List<Long>> HOMES_ROLLED = AttachmentRegistry.create(JaymosKingdoms.id("druid_homes_rolled"),
		b -> b.persistent(Codec.LONG.listOf()));
	private static Set<Long> homesRolled;
	/** "druid/player" → the day the player last brought her want. */
	private static final Map<String, Long> GIVEN = new HashMap<>();

	private Groves() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Groves::tick);
		// (before Rebirths' telling of the rite: a Druid holding out her want gives it too)
		UseEntityCallback.EVENT.register(Groves::offer);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			rolled = null;
			homesRolled = null;
			GIVEN.clear();
		});
	}

	/** Whether {@code e} is an old druid. */
	public static boolean is(@Nullable Entity e) {
		return e instanceof Villager && e.entityTags().contains(DRUID_TAG);
	}

	/** What {@code druid} wants, the same every time. */
	public static Item want(Entity druid) {
		return WANTS[Math.floorMod(druid.getUUID().hashCode() * 31 + 7, WANTS.length)];
	}

	/** Whether {@code player} has brought {@code druid} her want today already. */
	public static boolean givenToday(ServerPlayer player, Entity druid) {
		Long day = GIVEN.get(druid.getUUID() + "/" + player.getUUID());
		return day != null && day == Deeds.today(player.level().getServer());
	}

	/** {@code player} holds out what an old druid wants: she takes it, thanks them, and plants it in her grove. */
	private static InteractionResult offer(Player player, Level level, InteractionHand hand, Entity target,
		net.minecraft.world.phys.@Nullable EntityHitResult hit) {
		if (!is(target) || !(target instanceof Villager druid) || !druid.isAlive() || player.isSpectator()) {
			return InteractionResult.PASS;
		}
		ItemStack held = player.getItemInHand(hand);
		Item want = want(druid);
		if (!held.is(want)) {
			return InteractionResult.PASS;
		}
		if (!(player instanceof ServerPlayer sp) || !(level instanceof ServerLevel server)) {
			return InteractionResult.SUCCESS;
		}
		if (hand != InteractionHand.MAIN_HAND || givenToday(sp, druid)) {
			// (one a day: after that, holding it out is only talk)
			return InteractionResult.PASS;
		}
		GIVEN.put(druid.getUUID() + "/" + sp.getUUID(), Deeds.today(server.getServer()));
		ItemStack one = held.copyWithCount(1);
		held.consume(1, sp);
		McaCompat.hearts(druid, sp, GIFT_HEARTS);
		Introductions.learn(sp, druid);
		druid.getLookControl().setLookAt(sp);
		BlockPos planted = plant(server, druid, one);
		Speech.sayPlainly(server, druid, sp, Component.translatable("speech.jaymoskingdoms.druid.thanks." + server.getRandom().nextInt(THANKS),
			one.getHoverName()), Rebirths.COLOUR);
		server.playSound(null, druid.getX(), druid.getY(), druid.getZ(), SoundEvents.VILLAGER_YES, SoundSource.NEUTRAL, 0.8F, 0.8F);
		if (planted != null) {
			server.sendParticles(net.minecraft.core.particles.ParticleTypes.HAPPY_VILLAGER, planted.getX() + 0.5, planted.getY() + 0.5,
				planted.getZ() + 0.5, 12, 0.4, 0.4, 0.4, 0.0);
			server.playSound(null, planted, SoundEvents.GRASS_PLACE, SoundSource.BLOCKS, 0.8F, 1.0F);
		}
		return InteractionResult.CONSUME;
	}

	/** Plants {@code sapling} on open ground in {@code druid}'s grove; where, or null if there was none (she keeps it). */
	private static @Nullable BlockPos plant(ServerLevel level, Villager druid, ItemStack sapling) {
		if (!(sapling.getItem() instanceof BlockItem item)) {
			return null;
		}
		BlockState state = item.getBlock().defaultBlockState();
		RandomSource random = level.getRandom();
		BlockPos at = druid.blockPosition();
		for (int tries = 0; tries < 24; tries++) {
			BlockPos column = at.offset(random.nextInt(PLANT_REACH * 2 + 1) - PLANT_REACH, 0, random.nextInt(PLANT_REACH * 2 + 1) - PLANT_REACH);
			if (column.closerThan(at, 1.5)) {
				continue;
			}
			BlockPos spot = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column);
			if (Math.abs(spot.getY() - at.getY()) > 2 || !level.getBlockState(spot).canBeReplaced() || !level.getFluidState(spot).isEmpty()
				|| !state.canSurvive(level, spot)) {
				continue;
			}
			level.setBlockAndUpdate(spot, state);
			return spot;
		}
		return null;
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
			if (player.isSpectator() || !player.isAlive()) {
				continue;
			}
			var biome = level.getBiome(player.blockPosition());
			try {
				if (biome.is(BiomeTags.IS_FOREST)) {
					place(level, player);
				}
				if (biome.is(BiomeTags.IS_FOREST) || biome.is(BiomeTags.IS_TAIGA)) {
					home(level, player);
				}
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
			if (Math.abs(spot.getX() - here.getX()) < 12 && Math.abs(spot.getZ() - here.getZ()) < 12 || !com.jaymoskingdoms.Loaded.made(level, spot)) {
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

	/** A lone home of the woods near {@code player}, not rolled yet: rolled once, and maybe an old druid moves in. */
	private static void home(ServerLevel level, ServerPlayer player) {
		var homes = level.registryAccess().lookupOrThrow(Registries.STRUCTURE).get(HOMES).orElse(null);
		if (homes == null || homes.size() == 0) {
			return;
		}
		if (homesRolled == null) {
			homesRolled = new HashSet<>(level.getAttachedOrElse(HOMES_ROLLED, List.<Long>of()));
		}
		BlockPos here = player.blockPosition();
		for (BlockPos at : new BlockPos[] {here, here.east(HOME_LOOK), here.west(HOME_LOOK), here.south(HOME_LOOK), here.north(HOME_LOOK)}) {
			StructureStart start = level.structureManager().getStructureAt(at, homes);
			if (!start.isValid()) {
				continue;
			}
			long key = start.getChunkPos().pack();
			if (homesRolled.contains(key)) {
				continue;
			}
			BoundingBox box = start.getBoundingBox();
			BlockPos middle = box.getCenter();
			if (!Loaded.made(level, new BlockPos(box.minX(), middle.getY(), box.minZ()))
				|| !Loaded.made(level, new BlockPos(box.maxX(), middle.getY(), box.maxZ()))) {
				// (not all of it made and loaded yet: looked at again later)
				continue;
			}
			var biome = level.getBiome(middle);
			if (!biome.is(BiomeTags.IS_FOREST) && !biome.is(BiomeTags.IS_TAIGA)) {
				homesRolled.add(key);
				level.setAttached(HOMES_ROLLED, new ArrayList<>(homesRolled));
				continue;
			}
			homesRolled.add(key);
			level.setAttached(HOMES_ROLLED, new ArrayList<>(homesRolled));
			if (level.getRandom().nextFloat() < HOME_DRUID) {
				moveIn(level, start);
			}
			return;
		}
	}

	/** An old druid comes to live in {@code start}: on a floor inside it, under its roof if there is one; true if they did. */
	static boolean moveIn(ServerLevel level, StructureStart start) {
		BoundingBox box = start.getBoundingBox();
		RandomSource random = level.getRandom();
		BlockPos outdoors = null;
		for (int tries = 0; tries < 300; tries++) {
			BlockPos p = new BlockPos(Mth.randomBetweenInclusive(random, box.minX(), box.maxX()),
				Mth.randomBetweenInclusive(random, box.minY() + 1, box.maxY()), Mth.randomBetweenInclusive(random, box.minZ(), box.maxZ()));
			if (!level.isLoaded(p) || !level.structureManager().structureHasPieceAt(p, start) || !standable(level, p)) {
				continue;
			}
			if (!level.canSeeSky(p)) {
				druidAt(level, p);
				return true;
			}
			if (outdoors == null) {
				outdoors = p;
			}
		}
		if (outdoors != null) {
			druidAt(level, outdoors);
			return true;
		}
		return false;
	}

	/** Whether one can stand at {@code p}: a firm floor under it, and room for a body, dry. */
	private static boolean standable(ServerLevel level, BlockPos p) {
		BlockPos below = p.below();
		return level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)
			&& level.getBlockState(p).getCollisionShape(level, p).isEmpty() && level.getFluidState(p).isEmpty()
			&& level.getBlockState(p.above()).getCollisionShape(level, p.above()).isEmpty();
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
