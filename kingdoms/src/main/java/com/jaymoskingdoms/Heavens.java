package com.jaymoskingdoms;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ScaffoldingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.547) Andy: "some villagers know about the heavens overhead, but they never explicitly tell you", then "some of them try
 * to get there". The heavens are the Aether over the world (the top band of a One Dimension world, or Jaymo's Powers'
 * {@code jaymospowers:aether}) and the high sky past it.
 * <p>
 * One grown villager in {@link #KNOW_IN} knows (fixed by who they are, so it never changes). A knower never names the place:
 * they speak in riddles and half-hints, in MCA's chat now and then ({@link #chat}, instead of small talk), in a few talks of
 * their own ({@link TalkBook}'s {@code heavens_*}, whose replies may draw a riddle, {@link TalkBook.Out#RIDDLE}), and they
 * notice the Aether's stones in your pack. On a clear night a knower near you may stand looking straight up and mutter.
 * <p>
 * One knower in {@link #SEEK_IN} is a seeker, who tries to get there: they build a scaffolding tower by their home, a block now
 * and then by day while they're nearby, never higher than {@link #TOWER_MAX}. Once it is as high as it goes, sooner or later
 * they see it's hopeless, take it down and, after {@link #REST_DAYS}, start again. Guards and hermits are never seekers (a
 * hermit already lives halfway up). Their words go over their heads ({@link Speech}), never in chat.
 */
public final class Heavens {
	/** One grown villager in this many knows of the heavens. */
	static final int KNOW_IN = 8;
	/** One knower in this many tries to get there. */
	static final int SEEK_IN = 3;
	/** The chance a chat with a knower (MCA's menu) brings up the heavens instead of small talk. */
	public static final float CHAT_CHANCE = 0.25F;
	/** In a chat, the chance a seeker talks of their tower, and a knower of the Aether's stones you carry, before a riddle. */
	static final float SEEKER_CHAT = 0.5F, POCKET_CHAT = 0.6F;
	/** How many lines each set has (lang {@code talk.jaymoskingdoms.heavens.<set>.<n>}). */
	static final int RIDDLES = 18, POCKETS = 5, SEEKER_LINES = 7, MUTTERS = 9, TOPPLES = 5, BUILDS = 5;
	/** How often (ticks) knowers near players are looked at. */
	static final int STEP_TICKS = 200;
	/** How far from a player a knower is looked at. */
	static final double NEAR_PLAYER = 64.0;
	/** A knower mutters at the night sky only for a player this near, and is heard this far. */
	static final double MUTTER_NEAR = 12.0, MUTTER_RANGE = 14.0;
	/** The chance a knower who isn't a seeker stands looking up on a clear night (once a night). */
	static final float KNOWER_STARGAZE = 0.25F;
	/** The tallest a seeker's tower gets (blocks), and how far from their home it stands. */
	static final int TOWER_MAX = 24, TOWER_RADIUS = 6;
	/** The tower is looked for this far below and above the home's height. */
	static final int TOWER_DOWN = 4, TOWER_UP = 4;
	/** A seeker works on it only while this near it. */
	static final double BUILD_NEAR = 16.0;
	/** The chance, each step by day, that a seeker near their tower adds a block (about one or two a day). */
	static final float BUILD_CHANCE = 1.0F / 40.0F;
	/** The chance, each step by day, that a seeker with a finished tower gives up on it. */
	static final float TOPPLE_CHANCE = 1.0F / 120.0F;
	/** Days a seeker rests after taking their tower down. */
	static final int REST_DAYS = 3;
	/** Tries at finding ground for a new tower. */
	static final int SITE_TRIES = 12;
	/** The chance a seeker says something as they add a block. */
	static final float BUILD_SAY = 1.0F / 5.0F;
	/** A pale sky blue for their words. */
	static final int COLOR = 0xBFE3FF;

	/** The Aether's stones and gems a knower notices in your pack (Jaymo's Powers ids). */
	static final Set<String> AETHER_THINGS = Set.of("ambrosium_shard", "zanite_gemstone", "golden_amber", "gravitite_ore", "enchanted_gravitite",
		"holystone", "mossy_holystone", "skyroot_log", "golden_oak_log", "cold_aercloud", "blue_aercloud", "golden_aercloud", "quicksoil", "icestone",
		"glint_gemstone", "white_apple");
	private static final ResourceKey<Level> AETHER = ResourceKey.create(Registries.DIMENSION, Identifier.fromNamespaceAndPath("jaymospowers", "aether"));
	private static final String KEY = "talk.jaymoskingdoms.heavens.";

	/** villager → the day they last looked up at the night sky. */
	private static final Map<UUID, Long> GAZED = new HashMap<>();
	/** villager → the day they last took their tower down. */
	private static final Map<UUID, Long> TOPPLED = new HashMap<>();

	private Heavens() {
	}

	static void register() {
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			GAZED.clear();
			TOPPLED.clear();
		});
		ServerTickEvents.END_LEVEL_TICK.register(level -> {
			if (level.getGameTime() % STEP_TICKS == 0 && !level.players().isEmpty()) {
				step(level);
			}
		});
	}

	// ---- who knows ----------------------------------------------------------------------------------------------------------

	private static long seed(UUID id) {
		long h = id.getMostSignificantBits() * 0x9E3779B97F4A7C15L ^ id.getLeastSignificantBits();
		return h ^ h >>> 29;
	}

	/** {@code v} knows of the heavens (a grown MCA villager, not a hermit, one in {@link #KNOW_IN}). */
	public static boolean knows(Villager v) {
		return !v.isBaby() && McaCompat.isMca(v) && !Hermits.is(v) && Math.floorMod(seed(v.getUUID()), KNOW_IN) == 0;
	}

	/** {@code v} knows, and tries to get there. */
	public static boolean seeks(Villager v) {
		return knows(v) && !Downed.isGuard(v) && Math.floorMod(seed(v.getUUID()) >>> 16, SEEK_IN) == 0;
	}

	/** {@code player} carries something from the Aether. */
	static boolean carriesAether(ServerPlayer player) {
		for (ItemStack stack : player.getInventory()) {
			if (!stack.isEmpty()) {
				Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
				if (id.getNamespace().equals("jaymospowers") && AETHER_THINGS.contains(id.getPath())) {
					return true;
				}
			}
		}
		return false;
	}

	// ---- what they say ------------------------------------------------------------------------------------------------------

	/** A riddle about the heavens, never naming them. */
	static Component riddle(RandomSource random) {
		return Component.translatable(KEY + "riddle." + random.nextInt(RIDDLES));
	}

	/** What a knower says in MCA's chat instead of small talk; null if this chat isn't about the heavens. */
	public static @Nullable Component chat(Villager v, ServerPlayer player) {
		if (!knows(v)) {
			return null;
		}
		RandomSource random = v.getRandom();
		if (random.nextFloat() >= CHAT_CHANCE) {
			return null;
		}
		if (carriesAether(player) && random.nextFloat() < POCKET_CHAT) {
			return Component.translatable(KEY + "pocket." + random.nextInt(POCKETS));
		}
		if (seeks(v) && random.nextFloat() < SEEKER_CHAT) {
			return Component.translatable(KEY + "seeker." + random.nextInt(SEEKER_LINES));
		}
		return riddle(random);
	}

	// ---- the step: stargazing and towers ------------------------------------------------------------------------------------

	private static void step(ServerLevel level) {
		Set<Villager> seen = new HashSet<>();
		for (ServerPlayer player : level.players()) {
			if (player.isSpectator()) {
				continue;
			}
			seen.addAll(level.getEntitiesOfClass(Villager.class, player.getBoundingBox().inflate(NEAR_PLAYER), v -> v.isAlive() && knows(v)));
		}
		if (seen.isEmpty()) {
			return;
		}
		long clock = Promises.clockOf(level.getServer());
		long today = Math.floorDiv(clock, 24000L);
		boolean night = Vampires.night(clock);
		for (Villager v : seen) {
			if (v.isSleeping() || McaCompat.busy(v) || v.getTarget() != null || Downed.isDowned(v) || Stacked.in(v, AETHER)) {
				continue;
			}
			if (night) {
				stargaze(level, v, today);
			} else if (seeks(v)) {
				tower(level, v, today);
			}
		}
	}

	/** On a clear night, a knower near a player looks straight up and mutters. */
	private static void stargaze(ServerLevel level, Villager v, long today) {
		if (level.isRaining() || !level.canSeeSky(v.blockPosition().above()) || GAZED.getOrDefault(v.getUUID(), -1L) == today) {
			return;
		}
		ServerPlayer near = level.getNearestPlayer(v, MUTTER_NEAR) instanceof ServerPlayer p && !p.isSpectator() ? p : null;
		if (near == null) {
			return;
		}
		GAZED.put(v.getUUID(), today);
		if (!seeks(v) && v.getRandom().nextFloat() >= KNOWER_STARGAZE) {
			return;
		}
		v.getNavigation().stop();
		v.getLookControl().setLookAt(v.getX(), v.getEyeY() + 40.0, v.getZ(), 10.0F, 90.0F);
		Speech.say(level, v, Component.translatable(KEY + "mutter." + v.getRandom().nextInt(MUTTERS)), COLOR, MUTTER_RANGE);
	}

	/** A seeker's work on their tower: a block now and then, or, once it's as high as it goes, giving up on it. */
	private static void tower(ServerLevel level, Villager v, long today) {
		Long toppled = TOPPLED.get(v.getUUID());
		if (toppled != null && today - toppled < REST_DAYS) {
			return;
		}
		GlobalPos home = McaCompat.home(v).orElse(null);
		if (home == null || !home.dimension().equals(level.dimension()) || level.isRaining()) {
			return;
		}
		RandomSource random = v.getRandom();
		BlockPos base = findTower(level, home.pos());
		if (base == null) {
			if (random.nextFloat() >= BUILD_CHANCE || v.blockPosition().distSqr(home.pos()) > BUILD_NEAR * BUILD_NEAR) {
				return;
			}
			BlockPos site = site(level, home.pos(), random);
			if (site != null) {
				place(level, v, site);
			}
			return;
		}
		if (v.blockPosition().distSqr(base) > BUILD_NEAR * BUILD_NEAR) {
			return;
		}
		BlockPos top = base;
		while (top.getY() < level.getMaxY() && isScaffold(level.getBlockState(top.above()))) {
			top = top.above();
		}
		int height = top.getY() - base.getY() + 1;
		if (height >= TOWER_MAX || !level.isEmptyBlock(top.above()) || top.getY() + 1 >= level.getMaxY()) {
			if (random.nextFloat() < TOPPLE_CHANCE) {
				topple(level, v, base, top, today);
			}
			return;
		}
		if (random.nextFloat() < BUILD_CHANCE) {
			place(level, v, top.above());
		}
	}

	private static boolean isScaffold(BlockState state) {
		return state.is(Blocks.SCAFFOLDING);
	}

	/** The foot of the scaffolding tower by {@code home} (scaffolding with no scaffolding under it), or null. */
	private static @Nullable BlockPos findTower(ServerLevel level, BlockPos home) {
		BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
		for (int dx = -TOWER_RADIUS; dx <= TOWER_RADIUS; dx++) {
			for (int dz = -TOWER_RADIUS; dz <= TOWER_RADIUS; dz++) {
				for (int dy = -TOWER_DOWN; dy <= TOWER_UP; dy++) {
					at.set(home.getX() + dx, home.getY() + dy, home.getZ() + dz);
					if (isScaffold(level.getBlockState(at)) && !isScaffold(level.getBlockState(at.below()))) {
						return at.immutable();
					}
				}
			}
		}
		return null;
	}

	/** Open ground by {@code home} under the open sky, with room for a tower; or null. */
	private static @Nullable BlockPos site(ServerLevel level, BlockPos home, RandomSource random) {
		for (int i = 0; i < SITE_TRIES; i++) {
			int x = home.getX() + random.nextInt(TOWER_RADIUS * 2 + 1) - TOWER_RADIUS;
			int z = home.getZ() + random.nextInt(TOWER_RADIUS * 2 + 1) - TOWER_RADIUS;
			BlockPos pos = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, new BlockPos(x, home.getY(), z));
			if (Math.abs(pos.getY() - home.getY()) > TOWER_UP || !level.isEmptyBlock(pos) || !level.isEmptyBlock(pos.above())
				|| !level.getFluidState(pos).isEmpty() || !level.canSeeSky(pos)) {
				continue;
			}
			BlockState ground = level.getBlockState(pos.below());
			if (ground.isFaceSturdy(level, pos.below(), Direction.UP) && !ground.is(Blocks.DIRT_PATH) && !ground.is(Blocks.FARMLAND)) {
				return pos;
			}
		}
		return null;
	}

	private static void place(ServerLevel level, Villager v, BlockPos pos) {
		// (the block's own tick works out how it stands, as for one placed by hand)
		level.setBlock(pos, Blocks.SCAFFOLDING.defaultBlockState().setValue(ScaffoldingBlock.DISTANCE, 0), 3);
		level.playSound(null, pos, SoundEvents.SCAFFOLDING_PLACE, SoundSource.NEUTRAL, 0.8F, 1.0F);
		v.getLookControl().setLookAt(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
		v.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
		if (v.getRandom().nextFloat() < BUILD_SAY) {
			Speech.say(level, v, Component.translatable(KEY + "build." + v.getRandom().nextInt(BUILDS)), COLOR, MUTTER_RANGE);
		}
	}

	/** Not high enough: they take it down, top first, and will start again after a rest. */
	private static void topple(ServerLevel level, Villager v, BlockPos base, BlockPos top, long today) {
		for (BlockPos p = top; p.getY() >= base.getY(); p = p.below()) {
			if (isScaffold(level.getBlockState(p))) {
				level.destroyBlock(p, false);
			}
		}
		TOPPLED.put(v.getUUID(), today);
		v.getLookControl().setLookAt(v.getX(), v.getEyeY() + 40.0, v.getZ(), 10.0F, 90.0F);
		Speech.say(level, v, Component.translatable(KEY + "topple." + v.getRandom().nextInt(TOPPLES)), COLOR, MUTTER_RANGE);
		level.playSound(null, v.getX(), v.getY(), v.getZ(), SoundEvents.VILLAGER_NO, SoundSource.NEUTRAL, 0.8F, 1.0F);
	}
}
