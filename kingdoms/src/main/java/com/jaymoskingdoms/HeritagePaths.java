package com.jaymoskingdoms;

import com.jaymoskingdoms.sailing.Ship;
import com.jaymoskingdoms.sailing.Ships;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Guardian;
import net.minecraft.world.entity.monster.zombie.Drowned;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Heritage paths (Andy's picks, 2026-11-18, Inbox {@code heritage-paths}): what a people's blood grows into as you live it.
 * <ul>
 * <li><b>Your deeds open paths.</b> Nobody picks one from a menu: each path counts the deeds that are its own (a
 * Stoneborn mining deep counts toward the Deepdelver), and opens once it has {@link Path#open} of them.</li>
 * <li><b>One path per life.</b> The first to open is the one you walk for this life; the others show as what could
 * have been. A child you live on as through {@link Lineage} is born with your path opened (the first deed of it is
 * enough), but it isn't theirs until they live it, and another may still open first.</li>
 * <li><b>Paths deepen with the ladder.</b> Each has five steps, one per great foe on the ladder of {@link Knowledge}:
 * step {@code k} wakes once the {@code k}th has fallen and you've done {@link #STEP_TIMES}{@code [k-1]} times the
 * opening deeds.</li>
 * <li><b>Nothing is lost for leaving it.</b> Deeds are never taken back, so a path never fades; it only stops deepening.</li>
 * </ul>
 * A blood's paths are open to those of that blood, and to those whose look leans toward it (a hybrid look,
 * {@link HeritageVariants.Variant#hybrid}). This holds the Heartlander, Stoneborn and Tideborn paths; the other bloods'
 * come in later patches. Shown in the heritage picker's Paths section and on the Character page.
 */
public final class HeritagePaths {
	// ---- tuning -----------------------------------------------------------------------------------------------------
	/** Step k (1 to 5) wants this many times its path's opening deeds (and the kth great foe fallen). */
	static final int[] STEP_TIMES = {1, 2, 4, 7, 12};
	/** How often walked paths' gifts are put on (ticks); how often deeds kept up over time are counted. */
	static final int GIFT_TICKS = 20, COUNT_TICKS = 20;
	/** Deepdelver: the depth (below Y 0) at which its mining gift is whole (blocks); the gift per step at that depth; the ore sense's reach per step. */
	static final int DEEP_FULL = 64;
	static final double DEEP_SPEED = 0.06;
	static final int ORE_SENSE_BASE = 2, ORE_SENSE_STEP = 2, ORE_SENSE_TICKS = 40;
	/** Mountain's Shield: knockback resistance per step, and more per step while the shield is raised. */
	static final double SHIELD_KNOCKBACK = 0.04, SHIELD_RAISED = 0.1;
	/** Lanternkeeper: the chance per step a kept light's burn roll misses; the lantern fuel added per step; how far undead are kept from a held lantern (blocks, base and per step). */
	static final float KEPT_LIGHT = 0.15F, LANTERN_FUEL = 0.25F;
	static final double WARD_BASE = 2.0, WARD_STEP = 1.0, WARD_PUSH = 0.35;
	static final float WARD_BURN = 1.0F;
	/** Hearthkeeper: the reach of home round the bed (blocks); crops grown a step each look; how often they're looked at (ticks); trust gained, more a step; the bell's cooldown (ticks). */
	static final int HOME_REACH = 48, HOME_CROPS = 2, HOME_CROP_TICKS = 100, HOME_CROP_REACH = 16;
	static final float HOME_TRUST = 0.15F;
	static final int BELL_TICKS = 1200;
	static final double BELL_REACH = 96.0;
	/** Wayfarer: sprinting's hunger forgiven per step on a road (share); a horse's stamina spent less a step on a road (share); golden feathers per step (halves). */
	static final float ROAD_HUNGER = 0.1F, ROAD_HORSE = 0.08F;
	static final int ROAD_FEATHERS = 1;
	/** A road counts as travelled when you've gone this far along it since the last look (blocks). */
	static final double ROAD_MOVED = 2.5;
	/** Borrowed Blood: how often the folk round you are looked at (ticks) and how far (blocks). */
	static final int BORROW_TICKS = 1200;
	static final double BORROW_REACH = 32.0;
	/** Reefwalker: breath (oxygen bonus) and swimming per step. */
	static final double REEF_BREATH = 1.0, REEF_SWIM = 0.1;
	/** Saltblood: fish keener and a ship faster, per step (shares). */
	static final float SALT_KEEN = 0.15F, SALT_SAIL = 0.03F;
	/** Deep Mother's Voice: the chance per step each look that the sea's creatures let you be; their reach; the surge's reach, blow and shove. */
	static final float MOTHER_CALM = 0.15F;
	static final double MOTHER_REACH = 24.0, SURGE_REACH = 6.0, SURGE_SHOVE = 1.4;
	static final float SURGE_HURT = 4.0F;

	/** The paths. Each counts one kind of deed ({@code open}: how many open it). Their names are placeholders Andy may rename. */
	public enum Path {
		HL_HEARTH(Heritage.Kind.HEARTLANDER, Items.HAY_BLOCK, 150),
		HL_WAY(Heritage.Kind.HEARTLANDER, Items.COMPASS, 1800),
		HL_BORROWED(Heritage.Kind.HEARTLANDER, Items.NAME_TAG, 60),
		SB_DEEP(Heritage.Kind.STONEBORN, Items.DEEPSLATE_IRON_ORE, 300),
		SB_SHIELD(Heritage.Kind.STONEBORN, Items.SHIELD, 60),
		SB_LANTERN(Heritage.Kind.STONEBORN, Items.LANTERN, 40),
		TB_REEF(Heritage.Kind.TIDEBORN, Items.TUBE_CORAL, 900),
		TB_SALT(Heritage.Kind.TIDEBORN, Items.OAK_BOAT, 1200),
		TB_MOTHER(Heritage.Kind.TIDEBORN, Items.HEART_OF_THE_SEA, 40);

		public final Heritage.Kind blood;
		public final net.minecraft.world.item.Item icon;
		public final int open;

		Path(Heritage.Kind blood, net.minecraft.world.item.Item icon, int open) {
			this.blood = blood;
			this.icon = icon;
			this.open = open;
		}

		public String key() {
			return name().toLowerCase(Locale.ROOT);
		}

		/** The deeds step {@code k} (1 to 5) wants. */
		public int needs(int k) {
			return this.open * STEP_TIMES[k - 1];
		}

		public static @Nullable Path of(String key) {
			for (Path p : values()) {
				if (p.key().equals(key)) {
					return p;
				}
			}
			return null;
		}
	}

	/**
	 * A life's paths: deeds done toward each (by path key; {@code "blood:<heritage>"} keys count time lived among each
	 * other blood, for the Borrowed Blood), the path walked ("" none yet), the one opened by your parent ("" none), and
	 * the steps last told of.
	 */
	public record State(Map<String, Integer> deeds, String walked, String inherited, int told) {
		public static final State NONE = new State(Map.of(), "", "", 0);
		public static final Codec<State> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("deeds", Map.of()).forGetter(State::deeds),
				Codec.STRING.optionalFieldOf("walked", "").forGetter(State::walked),
				Codec.STRING.optionalFieldOf("inherited", "").forGetter(State::inherited),
				Codec.INT.optionalFieldOf("told", 0).forGetter(State::told))
			.apply(i, State::new));

		public int deeds(Path p) {
			return this.deeds.getOrDefault(p.key(), 0);
		}

		public @Nullable Path walkedPath() {
			return Path.of(this.walked);
		}

		/** Whether {@code p} is open: its deeds done, or the first of them for the one your parent walked. */
		public boolean opened(Path p) {
			return this.deeds(p) >= (p.key().equals(this.inherited) ? 1 : p.open);
		}
	}

	/** A player's paths. Saved, kept through death (a new life comes only with {@link Lineage}), sent to its owner for the screens. */
	public static final AttachmentType<State> STATE = AttachmentRegistry.create(JaymosKingdoms.id("heritage_paths"),
		builder -> builder.persistent(State.CODEC).copyOnDeath().syncWith(ByteBufCodecs.fromCodec(State.CODEC), AttachmentSyncPredicate.targetOnly()));

	/** Hearthkeeper: when each player's bell was last answered. */
	private static final Map<UUID, Long> BELL_RUNG = new HashMap<>();
	/** Wayfarer: where each player stood at the last look. */
	private static final Map<UUID, Vec3> LAST_AT = new HashMap<>();
	/** Lanternkeeper: torches set down by one walking it, and their step (dimension + pos → step). Not saved: kept lights burn as any once the world restarts. */
	private static final Map<String, Integer> KEPT = new HashMap<>();

	private HeritagePaths() {
	}

	static void register() {
		JaymosKingdoms.LOGGER.debug("heritage paths attachment: {}", STATE);
		ServerTickEvents.END_SERVER_TICK.register(HeritagePaths::tick);
		PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, entity) -> {
			if (player instanceof ServerPlayer sp) {
				broken(sp, pos, state);
			}
		});
		AttackBlockCallback.EVENT.register((player, level, hand, pos, direction) -> {
			if (player instanceof ServerPlayer sp && !sp.isSpectator() && looseStone(sp, level.getBlockState(pos))) {
				// (the Deepdelver's last step: loose stone parts under your hands)
				sp.gameMode.destroyBlock(pos);
				return InteractionResult.SUCCESS;
			}
			return InteractionResult.PASS;
		});
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damageTaken, blocked) -> {
			if (entity instanceof ServerPlayer sp && blocked && sp.isAlive()) {
				deed(sp, Path.SB_SHIELD, 1);
			}
		});
		EntitySleepEvents.STOP_SLEEPING.register((entity, pos) -> {
			if (entity instanceof ServerPlayer sp && sp.level().isBrightOutside() && atHome(sp, pos, HOME_REACH)) {
				// a night slept in your own bed
				deed(sp, Path.HL_HEARTH, 5);
			}
		});
		UseItemCallback.EVENT.register((player, level, hand) -> {
			if (player instanceof ServerPlayer sp && player.getItemInHand(hand).is(Items.BELL) && bell(sp)) {
				return InteractionResult.SUCCESS;
			}
			return InteractionResult.PASS;
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			LAST_AT.remove(handler.player.getUUID());
			BELL_RUNG.remove(handler.player.getUUID());
		});
		feathers();
	}

	// ---- the state ----------------------------------------------------------------------------------------------------

	public static State of(Player player) {
		return player.getAttachedOrElse(STATE, State.NONE);
	}

	/** The path {@code player} walks, or null. */
	public static @Nullable Path walked(Player player) {
		return of(player).walkedPath();
	}

	/** The woken steps of {@code path} for {@code player} (0 to 5): each wants its great foe fallen and its deeds done. */
	public static int steps(Player player, Path path) {
		return steps(of(player).deeds(path), Knowledge.level(player), path);
	}

	public static int steps(int deeds, int knowledge, Path path) {
		int n = 0;
		while (n < Knowledge.MAX && knowledge > n && deeds >= path.needs(n + 1)) {
			n++;
		}
		return n;
	}

	/** (Both sides, for the screens) the steps of {@code path} shown lit: the walked path's woken steps as last told, else none. */
	public static int stepsShown(Player player, Path path) {
		State s = of(player);
		return s.walkedPath() == path ? s.told() : 0;
	}

	/** (Both sides, for the screens) how far {@code player} is on {@code path}, in a few words. */
	public static Component status(Player player, Path path) {
		State s = of(player);
		if (s.walkedPath() == path) {
			return Component.translatable("gui.jaymoskingdoms.path.walking", s.told());
		}
		if (s.opened(path)) {
			return Component.translatable("gui.jaymoskingdoms.path.could_have");
		}
		if (path.key().equals(s.inherited())) {
			return Component.translatable("gui.jaymoskingdoms.path.parents");
		}
		int pct = (int)Math.min(99L, 100L * s.deeds(path) / path.open);
		return Component.translatable(s.walked().isEmpty() ? "gui.jaymoskingdoms.path.opening" : "gui.jaymoskingdoms.path.closed", pct);
	}

	/** The woken steps of {@code path} if it's the one {@code player} walks, else 0. */
	static int step(Player player, Path path) {
		return walked(player) == path ? steps(player, path) : 0;
	}

	/** Whether {@code path} is of {@code player}'s blood, or of the blood their look leans toward. */
	public static boolean ofBlood(Player player, Path path) {
		if (Heritage.of(player) == path.blood) {
			return true;
		}
		HeritageVariants.Variant look = HeritageVariants.of(player);
		return look != null && look.hybrid() == path.blood;
	}

	/** {@code n} deeds toward {@code path} by {@code player}: counted if it's of their blood; the first path to open becomes theirs. */
	static void deed(ServerPlayer player, Path path, int n) {
		if (n <= 0 || player.isCreative() || player.isSpectator() || !ofBlood(player, path)) {
			return;
		}
		State s = of(player);
		boolean wasOpen = s.opened(path);
		Map<String, Integer> deeds = new HashMap<>(s.deeds());
		deeds.merge(path.key(), n, (a, b) -> (int)Math.min(Integer.MAX_VALUE, (long)a + b));
		String walked = s.walked();
		State now = new State(Map.copyOf(deeds), walked, s.inherited(), s.told());
		if (!wasOpen && now.opened(path)) {
			if (walked.isEmpty()) {
				walked = path.key();
				now = new State(now.deeds(), walked, now.inherited(), now.told());
				player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.9F, 0.8F);
				Fanfare.title(player, Component.translatable("path.jaymoskingdoms." + path.key()), Component.translatable("message.jaymoskingdoms.path.opened"));
				player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.path.walked", Component.translatable("path.jaymoskingdoms." + path.key()))
					.withStyle(ChatFormatting.GOLD));
			} else {
				player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.path.could_have", Component.translatable("path.jaymoskingdoms." + path.key()))
					.withStyle(ChatFormatting.GRAY));
			}
		}
		player.setAttached(STATE, now);
	}

	/** (From {@link Lineage}) {@code player} lives on as their child: a new life, with the path they walked opened, not chosen. */
	static void newLife(ServerPlayer player) {
		State s = of(player);
		String inherited = !s.walked().isEmpty() ? s.walked() : s.inherited();
		player.setAttached(STATE, new State(Map.of(), "", inherited, 0));
		clearGifts(player);
	}

	// ---- deeds ----------------------------------------------------------------------------------------------------------

	private static void broken(ServerPlayer player, BlockPos pos, BlockState state) {
		// Deepdelver: stone and ore mined below Y 0
		if (pos.getY() < 0 && (state.is(BlockTags.BASE_STONE_OVERWORLD) || ore(state))) {
			deed(player, Path.SB_DEEP, 1);
		}
		// Hearthkeeper: a ripe crop brought in near your bed
		if (state.getBlock() instanceof CropBlock crop && crop.isMaxAge(state) && atHome(player, pos, HOME_REACH)) {
			deed(player, Path.HL_HEARTH, 1);
		}
	}

	private static boolean ore(BlockState state) {
		return state.is(net.fabricmc.fabric.api.tag.convention.v2.ConventionalBlockTags.ORES);
	}

	/** (From the block item mixin) {@code player} set down {@code state}: a light in the dark is the Lanternkeeper's, and burns longer for one who walks it. */
	public static void placed(ServerPlayer player, ServerLevel level, BlockPos pos, BlockState state) {
		boolean torch = Torches.burns(state), lantern = Lanterns.burns(state);
		if (!torch && !lantern) {
			return;
		}
		if (!level.canSeeSky(pos)) {
			deed(player, Path.SB_LANTERN, 1);
		}
		int step = step(player, Path.SB_LANTERN);
		if (step <= 0) {
			return;
		}
		if (torch) {
			KEPT.put(keptKey(level, pos), step);
		} else {
			Lanterns.stretch(level, pos, 1.0F + LANTERN_FUEL * step);
		}
	}

	private static String keptKey(ServerLevel level, BlockPos pos) {
		return level.dimension().identifier() + "|" + pos.asLong();
	}

	/** (From {@link Torches#randomTick}) whether the torch at {@code pos}, which was about to burn out, is kept a while longer. */
	static boolean keeps(ServerLevel level, BlockPos pos, RandomSource random) {
		Integer step = KEPT.get(keptKey(level, pos));
		return step != null && random.nextFloat() < KEPT_LIGHT * step;
	}

	/** (From {@link Torches}) the torch at {@code pos} went out or is gone: no longer kept. */
	static void forgetLight(ServerLevel level, BlockPos pos) {
		KEPT.remove(keptKey(level, pos));
	}

	/** (From {@link Patrons#gain}) {@code n} favour won with {@code patron}. */
	static void favour(ServerPlayer player, Patron patron, int n) {
		if (patron == Patron.DEEP_MOTHER) {
			deed(player, Path.TB_MOTHER, n);
		}
	}

	/** (From {@link TideFishing#fished}, by way of Jaymo's Powers) a fish took {@code player}'s hook. */
	static void fished(ServerPlayer player) {
		deed(player, Path.TB_SALT, 5);
	}

	/** Deeds kept up over time: roads travelled, time under water and aboard ship, and the folk you live among. */
	private static void count(ServerPlayer player, MinecraftServer server) {
		Vec3 at = player.position();
		Vec3 was = LAST_AT.put(player.getUUID(), at);
		if (was != null && onRoad(player) && at.distanceToSqr(was.x, at.y, was.z) >= ROAD_MOVED * ROAD_MOVED) {
			deed(player, Path.HL_WAY, 1);
		}
		if (player.isUnderWater()) {
			deed(player, Path.TB_REEF, 1);
		}
		if (Ships.aboard(player) != null) {
			deed(player, Path.TB_SALT, 1);
		}
		if (server.getTickCount() % BORROW_TICKS == 0) {
			borrowed(player);
		}
	}

	/** Borrowed Blood: a minute among folk of other bloods counts, and toward the blood you've lived among most. */
	private static void borrowed(ServerPlayer player) {
		Heritage.Kind mine = Heritage.of(player);
		Map<Heritage.Kind, Integer> near = new HashMap<>();
		for (LivingEntity e : player.level().getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(BORROW_REACH), e -> !(e instanceof Player))) {
			Heritage.Kind k = VillagerHeritage.of(e);
			if (k != null && k != mine && k != Heritage.Kind.HEARTLANDER) {
				near.merge(k, 1, Integer::sum);
			}
		}
		Heritage.Kind most = near.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(null);
		if (most == null || !ofBlood(player, Path.HL_BORROWED) || player.isCreative() || player.isSpectator()) {
			return;
		}
		State s = of(player);
		Map<String, Integer> deeds = new HashMap<>(s.deeds());
		deeds.merge("blood:" + most.key(), 1, Integer::sum);
		player.setAttached(STATE, new State(Map.copyOf(deeds), s.walked(), s.inherited(), s.told()));
		deed(player, Path.HL_BORROWED, 1);
	}

	/** The bloods {@code player} has lived among, most first. */
	public static List<Heritage.Kind> livedAmong(State s) {
		List<Map.Entry<String, Integer>> rows = new ArrayList<>();
		for (Map.Entry<String, Integer> e : s.deeds().entrySet()) {
			if (e.getKey().startsWith("blood:")) {
				rows.add(e);
			}
		}
		rows.sort(Map.Entry.<String, Integer>comparingByValue().reversed());
		List<Heritage.Kind> out = new ArrayList<>();
		for (Map.Entry<String, Integer> e : rows) {
			Heritage.Kind k = Heritage.Kind.byKey(e.getKey().substring("blood:".length()));
			if (k != null) {
				out.add(k);
			}
		}
		return out;
	}

	// ---- the gifts ----------------------------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		boolean count = server.getTickCount() % COUNT_TICKS == 0, gifts = server.getTickCount() % GIFT_TICKS == 0;
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (count) {
				count(player, server);
			}
			Path walked = walked(player);
			if (walked == null) {
				continue;
			}
			int step = steps(player, walked);
			// the shield answers at once to being raised and lowered
			if (walked == Path.SB_SHIELD) {
				shield(player, step);
			}
			if (gifts) {
				told(player, walked, step);
				gifts(player, server, walked, step);
			}
		}
	}

	/** A newly woken step is told: a chime and a line. */
	private static void told(ServerPlayer player, Path walked, int step) {
		State s = of(player);
		if (step <= s.told()) {
			return;
		}
		player.setAttached(STATE, new State(s.deeds(), s.walked(), s.inherited(), step));
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BELL_RESONATE, SoundSource.PLAYERS, 0.7F, 1.4F);
		player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.path.step", Component.translatable("path.jaymoskingdoms." + walked.key()), step,
			Component.translatable("path.jaymoskingdoms." + walked.key() + ".step" + step)).withStyle(ChatFormatting.GOLD));
	}

	private static void gifts(ServerPlayer player, MinecraftServer server, Path walked, int step) {
		ServerLevel level = player.level();
		switch (walked) {
			case SB_DEEP -> {
				double depth = Mth.clamp(-player.getY() / DEEP_FULL, 0.0, 1.0);
				put(player, Attributes.BLOCK_BREAK_SPEED, "path_deep", DEEP_SPEED * step * depth, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
				if (step > 0 && server.getTickCount() % ORE_SENSE_TICKS == 0) {
					oreSense(player, level, ORE_SENSE_BASE + ORE_SENSE_STEP * step);
				}
			}
			case SB_LANTERN -> {
				if (step > 0) {
					ward(player, level, step);
				}
			}
			case HL_HEARTH -> {
				if (step > 0 && server.getTickCount() % HOME_CROP_TICKS == 0) {
					growHome(player, step);
				}
			}
			case HL_WAY -> {
				// (the horse and the feathers ask as they need it; the way home shows with a compass in hand)
				if (step >= 5 && (player.getMainHandItem().is(Items.COMPASS) || player.getOffhandItem().is(Items.COMPASS))) {
					wayHome(player);
				}
				if (step > 0 && player.isSprinting() && onRoad(player)) {
					// (sprinting costs 0.1 a block; about a fifth of a block a tick, a second's worth forgiven in part)
					player.causeFoodExhaustion(-ROAD_HUNGER * step * 0.1F * (float)player.getDeltaMovement().horizontalDistance() * GIFT_TICKS);
				}
			}
			case HL_BORROWED -> borrowedGifts(player, step);
			case TB_REEF -> {
				put(player, Attributes.OXYGEN_BONUS, "path_reef_breath", REEF_BREATH * step, AttributeModifier.Operation.ADD_VALUE);
				put(player, Attributes.WATER_MOVEMENT_EFFICIENCY, "path_reef_swim", Math.min(1.0, REEF_SWIM * step), AttributeModifier.Operation.ADD_VALUE);
				if (step >= 5 && player.isInWater()) {
					player.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, GIFT_TICKS * 3, 0, true, false, true));
				}
			}
			case TB_MOTHER -> {
				if (step > 0) {
					calmTheSea(player, level, step);
				}
			}
			default -> {
			}
		}
	}

	private static void put(LivingEntity entity, Holder<Attribute> attribute, String id, double amount, AttributeModifier.Operation op) {
		AttributeInstance instance = entity.getAttribute(attribute);
		if (instance == null) {
			return;
		}
		Identifier key = JaymosKingdoms.id(id);
		if (amount == 0.0) {
			instance.removeModifier(key);
		} else {
			AttributeModifier had = instance.getModifier(key);
			if (had == null || had.amount() != amount) {
				instance.addOrUpdateTransientModifier(new AttributeModifier(key, amount, op));
			}
		}
	}

	/** Every gift a path puts on as attributes, taken off (a new life). */
	private static void clearGifts(ServerPlayer player) {
		put(player, Attributes.BLOCK_BREAK_SPEED, "path_deep", 0.0, AttributeModifier.Operation.ADD_VALUE);
		put(player, Attributes.KNOCKBACK_RESISTANCE, "path_shield", 0.0, AttributeModifier.Operation.ADD_VALUE);
		put(player, Attributes.EXPLOSION_KNOCKBACK_RESISTANCE, "path_shield", 0.0, AttributeModifier.Operation.ADD_VALUE);
		put(player, Attributes.OXYGEN_BONUS, "path_reef_breath", 0.0, AttributeModifier.Operation.ADD_VALUE);
		put(player, Attributes.WATER_MOVEMENT_EFFICIENCY, "path_reef_swim", 0.0, AttributeModifier.Operation.ADD_VALUE);
		for (Gift g : GIFTS.values()) {
			put(player, g.attribute(), "path_borrowed_0", 0.0, g.op());
			put(player, g.attribute(), "path_borrowed_1", 0.0, g.op());
		}
	}

	// Stoneborn

	/** Deepdelver: ores within {@code reach} glint, for you alone. */
	private static void oreSense(ServerPlayer player, ServerLevel level, int reach) {
		BlockPos c = player.blockPosition();
		BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
		int shown = 0;
		for (int y = -reach; y <= reach && shown < 64; y++) {
			for (int z = -reach; z <= reach && shown < 64; z++) {
				for (int x = -reach; x <= reach && shown < 64; x++) {
					at.set(c.getX() + x, c.getY() + y, c.getZ() + z);
					if (ore(level.getBlockState(at))) {
						level.sendParticles(player, ParticleTypes.WAX_OFF, false, true, at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, 2, 0.3, 0.3, 0.3, 0.0);
						shown++;
					}
				}
			}
		}
	}

	/** Deepdelver's last step: stone gives at once under a pickaxe. */
	private static boolean looseStone(ServerPlayer player, BlockState state) {
		return !player.isCreative() && player.getMainHandItem().is(ItemTags.PICKAXES)
			&& (state.is(BlockTags.BASE_STONE_OVERWORLD) || state.is(Blocks.COBBLESTONE) || state.is(Blocks.COBBLED_DEEPSLATE))
			&& step(player, Path.SB_DEEP) >= 5;
	}

	/** Mountain's Shield: hard to shift, harder with the shield up, and at the end not at all crouched behind it. */
	private static void shield(ServerPlayer player, int step) {
		boolean raised = player.isBlocking();
		double kb = step >= 5 && raised && player.isShiftKeyDown() ? 1.0 : SHIELD_KNOCKBACK * step + (raised ? SHIELD_RAISED * step : 0.0);
		put(player, Attributes.KNOCKBACK_RESISTANCE, "path_shield", kb, AttributeModifier.Operation.ADD_VALUE);
		put(player, Attributes.EXPLOSION_KNOCKBACK_RESISTANCE, "path_shield", kb, AttributeModifier.Operation.ADD_VALUE);
	}

	/** Lanternkeeper: with a lantern in hand the undead keep back from its light, and at the end it burns them. */
	private static void ward(ServerPlayer player, ServerLevel level, int step) {
		if (!lanternHeld(player)) {
			return;
		}
		double reach = WARD_BASE + WARD_STEP * step;
		for (Mob m : level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(reach), m -> m.isAlive())) {
			Vec3 away = m.position().subtract(player.position()).multiply(1.0, 0.0, 1.0);
			if (away.lengthSqr() < 1.0E-4) {
				continue;
			}
			away = away.normalize().scale(WARD_PUSH);
			m.push(away.x, 0.05, away.z);
			m.syncVelocity = true;
			m.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, GIFT_TICKS * 2, 1, true, false, false));
			if (step >= 5) {
				m.hurtServer(level, level.damageSources().magic(), WARD_BURN);
				level.sendParticles(ParticleTypes.SMALL_FLAME, m.getX(), m.getY() + m.getBbHeight() * 0.6, m.getZ(), 3, 0.2, 0.3, 0.2, 0.01);
			}
		}
	}

	private static boolean lanternHeld(Player player) {
		for (ItemStack s : new ItemStack[] {player.getMainHandItem(), player.getOffhandItem()}) {
			if (s.getItem() instanceof net.minecraft.world.item.BlockItem item && item.getBlock() instanceof net.minecraft.world.level.block.LanternBlock) {
				return true;
			}
		}
		return false;
	}

	// Heartlander

	/** {@code player}'s bed (their respawn point, in this dimension), or null. */
	private static @Nullable BlockPos home(ServerPlayer player) {
		ServerPlayer.RespawnConfig cfg = player.getRespawnConfig();
		return cfg == null || cfg.respawnData().dimension() != player.level().dimension() ? null : cfg.respawnData().pos();
	}

	private static boolean atHome(ServerPlayer player, BlockPos pos, int reach) {
		BlockPos home = home(player);
		return home != null && home.distSqr(pos) <= (double)reach * reach;
	}

	/** Hearthkeeper: the crops round your bed grow on. */
	private static void growHome(ServerPlayer player, int step) {
		BlockPos home = home(player);
		ServerLevel level = player.level();
		if (home == null || !Loaded.made(level, home) || home.distSqr(player.blockPosition()) > (double)HOME_REACH * HOME_REACH * 4) {
			// (only while you're about the place)
			return;
		}
		RandomSource random = level.getRandom();
		BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
		int grown = 0;
		for (int tries = 0; tries < 24 * step && grown < HOME_CROPS * step; tries++) {
			at.set(home.getX() + random.nextInt(HOME_CROP_REACH * 2 + 1) - HOME_CROP_REACH, home.getY() + random.nextInt(9) - 4,
				home.getZ() + random.nextInt(HOME_CROP_REACH * 2 + 1) - HOME_CROP_REACH);
			if (!Loaded.made(level, at)) {
				continue;
			}
			BlockState s = level.getBlockState(at);
			if (s.getBlock() instanceof CropBlock crop && !crop.isMaxAge(s)) {
				s.randomTick(level, at.immutable(), random);
				grown++;
			}
		}
	}

	/** (From {@link Bearings}) trust {@code who} gains in the player known as {@code id}: sooner for a Hearthkeeper's own neighbours. */
	static int trust(LivingEntity who, UUID id, int trust) {
		if (trust <= 0 || !(who.level() instanceof ServerLevel level)) {
			return trust;
		}
		ServerPlayer p = level.getServer().getPlayerList().getPlayer(id);
		int step = p == null ? 0 : step(p, Path.HL_HEARTH);
		if (step <= 0 || !atHome(p, who.blockPosition(), HOME_REACH)) {
			return trust;
		}
		return Math.max(trust + 1, Math.round(trust * (1.0F + HOME_TRUST * step)));
	}

	/** Hearthkeeper's last step: a bell used in a fight calls your home town's guards to whatever fights you. True if it was answered. */
	private static boolean bell(ServerPlayer player) {
		if (step(player, Path.HL_HEARTH) < 5) {
			return false;
		}
		BlockPos home = home(player);
		ServerLevel level = player.level();
		LivingEntity foe = player.getLastHurtByMob();
		if (foe == null || !foe.isAlive() || player.tickCount - player.getLastHurtByMobTimestamp() > 200) {
			// (else whatever is after you)
			foe = null;
			double best = Double.MAX_VALUE;
			for (Mob m : level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(24.0), m -> m.isAlive() && m.getTarget() == player)) {
				if (m.distanceToSqr(player) < best) {
					best = m.distanceToSqr(player);
					foe = m;
				}
			}
		}
		Faction f = home == null ? null : Factions.at(level.getServer(), level, home);
		long now = level.getGameTime();
		Long last = BELL_RUNG.get(player.getUUID());
		if (foe == null || f == null || f.fallen() || last != null && now - last < BELL_TICKS) {
			return false;
		}
		BELL_RUNG.put(player.getUUID(), now);
		int answered = AlarmBells.answer(level, player.blockPosition(), BELL_REACH, foe, m -> Factions.at(level.getServer(), level, m.blockPosition()) == f);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BELL_BLOCK, SoundSource.PLAYERS, 1.2F, 1.0F);
		player.sendOverlayMessage(Component.translatable(answered > 0 ? "message.jaymoskingdoms.path.bell_answered" : "message.jaymoskingdoms.path.bell_unheard",
			f.name()).withStyle(answered > 0 ? ChatFormatting.GOLD : ChatFormatting.GRAY));
		return true;
	}

	/** Whether {@code player} stands on a road (King's Roads, paths, cobbles, bridges: {@link Milestones#road}). */
	static boolean onRoad(Entity entity) {
		return Milestones.road(entity.level().getBlockState(entity.getOnPos()));
	}

	/** (From {@link com.jaymoskingdoms.equestrian.Equestrian#drain}) a Wayfarer's horse on a road tires slower. */
	public static double horseDrain(AbstractHorse horse) {
		if (!(horse.getControllingPassenger() instanceof ServerPlayer rider) || !onRoad(horse)) {
			return 1.0;
		}
		return 1.0 - ROAD_HORSE * step(rider, Path.HL_WAY);
	}

	/** Wayfarer's last step: a compass in hand tells the way home. */
	private static void wayHome(ServerPlayer player) {
		BlockPos home = home(player);
		if (home == null) {
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.path.no_home").withStyle(ChatFormatting.GRAY));
			return;
		}
		double dx = home.getX() + 0.5 - player.getX(), dz = home.getZ() + 0.5 - player.getZ();
		int dist = (int)Math.sqrt(dx * dx + dz * dz);
		player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.path.home", dist,
			Component.translatable("direction.jaymoskingdoms.path." + compass(dx, dz))).withStyle(ChatFormatting.YELLOW));
	}

	/** The eight-point way along (dx, dz): "n", "ne" ... "nw". */
	static String compass(double dx, double dz) {
		// (north is -z)
		double deg = Math.toDegrees(Math.atan2(dx, -dz));
		int i = Math.floorMod((int)Math.round(deg / 45.0), 8);
		return new String[] {"n", "ne", "e", "se", "s", "sw", "w", "nw"}[i];
	}

	/** Wayfarer: golden feathers (Jaymo's Powers' stamina) on the road. Reached by reflection: Kingdoms runs without Powers. */
	private static void feathers() {
		if (!FabricLoader.getInstance().isModLoaded("jaymospowers")) {
			return;
		}
		try {
			java.util.function.ToIntFunction<ServerPlayer> bonus = p -> onRoad(p) ? ROAD_FEATHERS * step(p, Path.HL_WAY) : 0;
			Class.forName("com.jaymospowers.stamina.Feathers").getMethod("addBonus", java.util.function.ToIntFunction.class).invoke(null, bonus);
		} catch (ReflectiveOperationException | LinkageError e) {
			JaymosKingdoms.LOGGER.warn("Heritage paths: couldn't reach Jaymo's Powers' feathers; the Wayfarer's road gives none", e);
		}
	}

	/** Borrowed Blood's gifts: one small gift of each blood, the attribute it raises and how much (at its whole). */
	record Gift(Holder<Attribute> attribute, double amount, AttributeModifier.Operation op) {
	}

	static final Map<Heritage.Kind, Gift> GIFTS = Map.ofEntries(
		Map.entry(Heritage.Kind.STONEBORN, new Gift(Attributes.BLOCK_BREAK_SPEED, 0.1, AttributeModifier.Operation.ADD_MULTIPLIED_BASE)),
		Map.entry(Heritage.Kind.TIDEBORN, new Gift(Attributes.OXYGEN_BONUS, 1.0, AttributeModifier.Operation.ADD_VALUE)),
		Map.entry(Heritage.Kind.SYLVAN, new Gift(Attributes.SAFE_FALL_DISTANCE, 1.0, AttributeModifier.Operation.ADD_VALUE)),
		Map.entry(Heritage.Kind.VERDANT, new Gift(Attributes.MAX_HEALTH, 2.0, AttributeModifier.Operation.ADD_VALUE)),
		Map.entry(Heritage.Kind.EMBERBORN, new Gift(Attributes.BURNING_TIME, -0.25, AttributeModifier.Operation.ADD_MULTIPLIED_BASE)),
		Map.entry(Heritage.Kind.FROSTBORN, new Gift(Attributes.MOVEMENT_EFFICIENCY, 0.25, AttributeModifier.Operation.ADD_VALUE)),
		Map.entry(Heritage.Kind.NIGHTBORN, new Gift(Attributes.SNEAKING_SPEED, 0.1, AttributeModifier.Operation.ADD_VALUE)),
		Map.entry(Heritage.Kind.WARRIORBORN, new Gift(Attributes.ATTACK_DAMAGE, 0.5, AttributeModifier.Operation.ADD_VALUE)),
		Map.entry(Heritage.Kind.EARTHLING, new Gift(Attributes.LUCK, 1.0, AttributeModifier.Operation.ADD_VALUE)),
		Map.entry(Heritage.Kind.STORMBORN, new Gift(Attributes.JUMP_STRENGTH, 0.04, AttributeModifier.Operation.ADD_VALUE)),
		Map.entry(Heritage.Kind.DAWNBORN, new Gift(Attributes.ARMOR_TOUGHNESS, 1.0, AttributeModifier.Operation.ADD_VALUE)),
		Map.entry(Heritage.Kind.ENDBORN, new Gift(Attributes.ENTITY_INTERACTION_RANGE, 0.5, AttributeModifier.Operation.ADD_VALUE)),
		Map.entry(Heritage.Kind.DRAGONBLOOD, new Gift(Attributes.ARMOR, 1.0, AttributeModifier.Operation.ADD_VALUE)),
		Map.entry(Heritage.Kind.DEEPBORN, new Gift(Attributes.BLOCK_INTERACTION_RANGE, 0.5, AttributeModifier.Operation.ADD_VALUE)),
		Map.entry(Heritage.Kind.DIVINE, new Gift(Attributes.MAX_ABSORPTION, 2.0, AttributeModifier.Operation.ADD_VALUE)),
		Map.entry(Heritage.Kind.BIOBUILT, new Gift(Attributes.ARMOR, 1.5, AttributeModifier.Operation.ADD_VALUE)),
		Map.entry(Heritage.Kind.RUBBERFOLK, new Gift(Attributes.FALL_DAMAGE_MULTIPLIER, -0.15, AttributeModifier.Operation.ADD_VALUE)),
		Map.entry(Heritage.Kind.GOBLIN, new Gift(Attributes.MOVEMENT_SPEED, 0.02, AttributeModifier.Operation.ADD_MULTIPLIED_BASE)),
		Map.entry(Heritage.Kind.ORC, new Gift(Attributes.ATTACK_KNOCKBACK, 0.3, AttributeModifier.Operation.ADD_VALUE)),
		Map.entry(Heritage.Kind.BEASTFOLK, new Gift(Attributes.ATTACK_SPEED, 0.1, AttributeModifier.Operation.ADD_VALUE)),
		Map.entry(Heritage.Kind.HOLLOW, new Gift(Attributes.MAX_HEALTH, 2.0, AttributeModifier.Operation.ADD_VALUE)));

	/** Borrowed Blood: the gift of the blood you've lived among most, growing a step at a time, and at the end the next blood's too. */
	private static void borrowedGifts(ServerPlayer player, int step) {
		List<Heritage.Kind> among = livedAmong(of(player));
		for (int i = 0; i < 2; i++) {
			Heritage.Kind k = i < among.size() ? among.get(i) : null;
			double share = i == 0 ? Math.min(1.0, step / 4.0) : step >= 5 ? 1.0 : 0.0;
			Gift mine = k == null ? null : GIFTS.get(k);
			// (each slot keeps one modifier per attribute: every other gift's is taken off)
			for (Gift g : GIFTS.values()) {
				if (mine == null || g.attribute() != mine.attribute()) {
					put(player, g.attribute(), "path_borrowed_" + i, 0.0, g.op());
				}
			}
			if (mine != null) {
				put(player, mine.attribute(), "path_borrowed_" + i, mine.amount() * share, mine.op());
			}
		}
	}

	// Tideborn

	/** (From Jaymo's Powers' fishing, through {@code KingdomsCompat}) how much keener fish are for {@code player}'s bait. */
	public static float fishKeen(ServerPlayer player) {
		return 1.0F + SALT_KEEN * step(player, Path.TB_SALT);
	}

	/** (From {@link Ship#topSpeed}) how much faster she sails for the Saltblood aboard her (the best of them). */
	public static double sail(Ship ship) {
		int best = 0;
		if (ship.level() instanceof ServerLevel level) {
			for (ServerPlayer p : level.players()) {
				if (Ships.aboard(p) == ship) {
					best = Math.max(best, step(p, Path.TB_SALT));
				}
			}
		}
		return 1.0 + SALT_SAIL * best;
	}

	/** (From {@code SeaEvents}' squalls) whether a Saltblood at the end of the path is aboard {@code ship}: the squall calms round her. */
	public static boolean calms(Ship ship) {
		if (!(ship.level() instanceof ServerLevel level)) {
			return false;
		}
		for (ServerPlayer p : level.players()) {
			if (Ships.aboard(p) == ship && step(p, Path.TB_SALT) >= 5) {
				return true;
			}
		}
		return false;
	}

	/** Deep Mother's Voice: the drowned and the guardians round you often let you be (unless you've struck them). */
	private static void calmTheSea(ServerPlayer player, ServerLevel level, int step) {
		RandomSource random = level.getRandom();
		for (Mob m : level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(MOTHER_REACH),
			m -> (m instanceof Drowned || m instanceof Guardian) && m.getTarget() == player)) {
			boolean struck = m.getLastHurtByMob() == player && m.tickCount - m.getLastHurtByMobTimestamp() < 400;
			if (!struck && random.nextFloat() < MOTHER_CALM * step) {
				m.setTarget(null);
				level.sendParticles(ParticleTypes.BUBBLE_POP, m.getX(), m.getY() + m.getBbHeight(), m.getZ(), 4, 0.2, 0.2, 0.2, 0.01);
			}
		}
	}

	/** (From {@link HeritageKits}' Tidal Breath) at the end of the Deep Mother's Voice it brings a surge of tide that throws foes back. */
	static void tidalBreath(ServerPlayer player, ServerLevel level) {
		if (step(player, Path.TB_MOTHER) < 5) {
			return;
		}
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(SURGE_REACH),
			e -> e != player && e.isAlive() && (e instanceof Enemy || e instanceof Mob m && m.getTarget() == player))) {
			Vec3 away = e.position().subtract(player.position()).multiply(1.0, 0.0, 1.0);
			away = away.lengthSqr() < 1.0E-4 ? new Vec3(0.0, 0.0, 1.0) : away.normalize();
			e.hurtServer(level, level.damageSources().drown(), SURGE_HURT);
			e.push(away.x * SURGE_SHOVE, 0.5, away.z * SURGE_SHOVE);
			e.syncVelocity = true;
			level.sendParticles(ParticleTypes.SPLASH, e.getX(), e.getY() + 0.5, e.getZ(), 20, 0.4, 0.4, 0.4, 0.2);
		}
		for (int i = 0; i < 36; i++) {
			double a = Math.PI * 2.0 * i / 36.0;
			level.sendParticles(ParticleTypes.SPLASH, player.getX() + Math.cos(a) * 2.5, player.getY() + 0.3, player.getZ() + Math.sin(a) * 2.5, 6, 0.2, 0.1, 0.2, 0.3);
		}
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.GENERIC_SPLASH, SoundSource.PLAYERS, 1.2F, 0.7F);
	}
}
