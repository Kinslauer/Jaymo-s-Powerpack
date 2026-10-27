package com.jaymospowers.power.zfighter;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.Powers;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.entity.EntityTypeTest;
import org.jspecify.annotations.Nullable;

/**
 * (0.17.61, Andy's NOEA picks) Story sagas: an original quest tree told through the fighters and masters of the pack's
 * lore. The Aether scattered remnants of itself across the Overworld; the last pieces are the monoliths and the Giants
 * that wake beside them (Highfield, Magnetic, Arctic, Irradiated), and the first Giant to fall raises the world's tier.
 * A saga is 4 to 6 steps: speak with a master or the Keeper, raise your stats or reach a form, find a shrine or the
 * Tower or the Time Chamber, defeat foes, hand over goods, and defeat a boss (a fighter of ours, {@link ZFighterRogue},
 * or a Giant, named only by its entity id so nothing here needs Jaymo's Kingdoms; without it that step is skipped).
 * <p>
 * A fighter starts a saga from the Story screen (the Story button on the training screen, X) and picks a difficulty
 * then ({@link #BOSS_HEALTH}, {@link #BOSS_DAMAGE}, {@link #REWARD}). To speak with a master or the Keeper: sneak and
 * right-click with anything in hand. A boss kill, a Giant's fall and each foe count for the killer's party members
 * within {@link #PARTY_RANGE} blocks ({@link ZFighterKingdoms#partyMembers}). Progress is saved on the player
 * ({@link #DATA}) and told to his client, which draws it.
 */
public final class ZFighterSagas {
	// ---- tuning ----
	/** The three difficulties, picked when a saga starts. */
	public static final int EASY = 0, NORMAL = 1, HARD = 2;
	public static final String[] DIFFICULTY_KEYS = {"easy", "normal", "hard"};
	/** A boss's health and damage, times these (by difficulty). */
	static final float[] BOSS_HEALTH = {0.6F, 1.0F, 1.8F};
	static final float[] BOSS_DAMAGE = {0.6F, 1.0F, 1.5F};
	/** The TP, items and step prizes, times these. */
	static final float[] REWARD = {0.6F, 1.0F, 1.7F};
	/** The foes to defeat and the goods to hand over, times these. */
	static final float[] COUNT = {0.7F, 1.0F, 1.4F};
	/** TP for each step done. */
	static final int STEP_TP = 60;
	/** A kill counts for the killer's party members within this many blocks of the victim. */
	static final double PARTY_RANGE = 64.0;
	/** How often (ticks) the steps that watch you are checked. */
	static final int SCAN_TICKS = 20;
	/** How near a shrine, the Tower and the like must be to count as found (blocks, flat). */
	static final double SHRINE_RANGE = 14.0, TOWER_RANGE = 30.0;
	/** A boss steps in this many ticks after the step opens, and again (if he is gone) this often. */
	static final int BOSS_FIRST_DELAY = 100, BOSS_RESPAWN = 1200;
	/** He lands this far from the fighter (min, max), and counts as there within {@code BOSS_NEAR}. */
	static final double BOSS_MIN = 16.0, BOSS_MAX = 24.0, BOSS_NEAR = 96.0;
	static final String BOSS_TAG = "jaymospowers_saga_boss";
	/** The Giants, by their entity ids (Jaymo's Kingdoms; none of it is needed to compile). */
	static final String[] GIANTS = {"jaymoskingdoms:highfield_giant", "jaymoskingdoms:magnetic_giant", "jaymoskingdoms:arctic_giant",
		"jaymoskingdoms:irradiated_giant"};

	public enum Kind {
		/** Speak with a master or the Keeper ({@code arg} "keeper": the Keeper only). */
		TALK,
		/** All stat levels together reach {@code amount}. */
		STATS,
		/** A form (1: Super) is open. */
		FORM,
		/** Stand at any shrine. */
		SHRINE,
		/** Stand at the foot of the Tower. */
		TOWER,
		/** Step into the Time Chamber. */
		CHAMBER,
		/** Defeat {@code amount} hostile creatures. */
		FOES,
		/** Hand {@code amount} of the item {@code arg} to a master or the Keeper. */
		DELIVER,
		/** Defeat the boss {@code arg}. */
		BOSS,
		/** Defeat any of the Giants. */
		GIANT
	}

	/** One step; {@code amount} and {@code arg} mean what the kind says. */
	public record Step(Kind kind, int amount, String arg) {
	}

	/** A boss of ours: his health and damage as shares of a tyrant's. */
	record Boss(String key, float health, float damage) {
	}

	/** A saga: its key, the sagas that must be done first (a bitmask), its steps, and its prize (TP and a technique). */
	record Saga(String key, int needs, Step[] steps, int tp, int technique) {
	}

	static final Boss[] BOSSES = {new Boss("rival", 0.2F, 0.6F), new Boss("hollow", 1.0F, 1.0F)};

	private static final Saga[] SAGAS = {
		// the first saga: a master sets a young fighter on the road
		new Saga("ashes", 0, new Step[] {
			new Step(Kind.TALK, 0, "any"), new Step(Kind.STATS, 8, ""), new Step(Kind.SHRINE, 0, ""), new Step(Kind.BOSS, 0, "rival")},
			800, ZFighterTraining.KI_BARRIER),
		// the Keeper tells what the sky left behind, and the road leads to a Giant
		new Saga("sky", 1, new Step[] {
			new Step(Kind.TALK, 0, "keeper"), new Step(Kind.FOES, 20, ""), new Step(Kind.FORM, 1, ""), new Step(Kind.TOWER, 0, ""),
			new Step(Kind.GIANT, 0, "")},
			3000, ZFighterTraining.SOLAR_FLASH),
		// a fighter who took a splinter of the sky for a crown
		new Saga("crown", 1, new Step[] {
			new Step(Kind.TALK, 0, "any"), new Step(Kind.DELIVER, 12, "minecraft:amethyst_shard"), new Step(Kind.STATS, 30, ""),
			new Step(Kind.CHAMBER, 0, ""), new Step(Kind.BOSS, 0, "hollow"), new Step(Kind.TALK, 0, "any")},
			5000, ZFighterTraining.RISING_DOME)
	};

	/**
	 * A fighter's story: the saga he is on ({@code saga}, -1 for none), its step, the count so far in that step, the
	 * difficulty picked, and the sagas he has finished (a bitmask).
	 */
	public record Data(int saga, int step, int progress, int difficulty, int done) {
		public static final Data NONE = new Data(-1, 0, 0, NORMAL, 0);
		public static final Codec<Data> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.INT.optionalFieldOf("saga", -1).forGetter(Data::saga),
			Codec.INT.optionalFieldOf("step", 0).forGetter(Data::step),
			Codec.INT.optionalFieldOf("progress", 0).forGetter(Data::progress),
			Codec.INT.optionalFieldOf("difficulty", NORMAL).forGetter(Data::difficulty),
			Codec.INT.optionalFieldOf("done", 0).forGetter(Data::done)).apply(i, Data::new));
		public static final StreamCodec<ByteBuf, Data> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, Data::saga,
			ByteBufCodecs.VAR_INT, Data::step,
			ByteBufCodecs.VAR_INT, Data::progress,
			ByteBufCodecs.VAR_INT, Data::difficulty,
			ByteBufCodecs.VAR_INT, Data::done,
			Data::new);

		public boolean active() {
			return saga >= 0 && saga < SAGAS.length;
		}

		public boolean finished(int index) {
			return (done & 1 << index) != 0;
		}
	}

	public static final AttachmentType<Data> DATA = AttachmentRegistry.create(JaymosPowers.id("zfighter_saga"),
		builder -> builder.persistent(Data.CODEC).copyOnDeath().syncWith(Data.STREAM_CODEC, AttachmentSyncPredicate.targetOnly()));

	/** When each fighter's boss may next step in (game time). Not saved: a restart starts the wait again. */
	private static final Map<UUID, Long> NEXT_BOSS = new HashMap<>();

	private ZFighterSagas() {
	}

	static void init() {
		ServerTickEvents.END_SERVER_TICK.register(ZFighterSagas::tick);
		ServerLivingEntityEvents.AFTER_DEATH.register(ZFighterSagas::afterDeath);
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			boolean master = entity.entityTags().contains(ZFighterMasters.TAG) || entity.entityTags().contains(ZFighterMasters.DARK_TAG);
			if (!master || hand != InteractionHand.MAIN_HAND || player.getMainHandItem().isEmpty() || !player.isShiftKeyDown()) {
				return InteractionResult.PASS;
			}
			if (!level.isClientSide() && player instanceof ServerPlayer server) {
				speak(server, entity, false);
			}
			return InteractionResult.SUCCESS;
		});
	}

	// ---------------------------------------------------------------- what the screen asks

	public static Data of(Player player) {
		Data data = player.getAttached(DATA);
		return data == null ? Data.NONE : data;
	}

	public static int sagaCount() {
		return SAGAS.length;
	}

	public static String key(int saga) {
		return SAGAS[saga].key();
	}

	public static int stepCount(int saga) {
		return SAGAS[saga].steps().length;
	}

	public static Step step(int saga, int step) {
		return SAGAS[saga].steps()[step];
	}

	/** Whether the sagas it hangs from are done. */
	public static boolean unlocked(Data data, int saga) {
		return (data.done() & SAGAS[saga].needs()) == SAGAS[saga].needs();
	}

	/** The count for the step's bar: the stat levels, the form, or the foes so far (what it needs is {@link #need}); -1 when it has none. */
	public static int have(Player player, Data data) {
		if (!data.active()) {
			return -1;
		}
		Step step = step(data.saga(), data.step());
		return switch (step.kind()) {
			case STATS -> ZFighterTraining.of(player).totalLevels();
			case FORM -> ZFighterTraining.formsOpen(player);
			case FOES, DELIVER -> data.progress();
			default -> -1;
		};
	}

	/** What the step asks for in numbers (foes and goods scale with the difficulty), or -1. */
	public static int need(Step step, int difficulty) {
		return switch (step.kind()) {
			case STATS, FORM -> step.amount();
			case FOES, DELIVER -> Math.max(1, Math.round(step.amount() * COUNT[difficulty]));
			default -> -1;
		};
	}

	/** The line for what the step asks. */
	public static Component objective(Step step, int difficulty) {
		int need = need(step, difficulty);
		return switch (step.kind()) {
			case TALK -> Component.translatable("saga.jaymospowers.step.talk" + ("keeper".equals(step.arg()) ? "_keeper" : ""));
			case DELIVER -> Component.translatable("saga.jaymospowers.step.deliver", need, itemName(step.arg()));
			case BOSS -> Component.translatable("saga.jaymospowers.step.boss", Component.translatable("entity.jaymospowers.saga_boss." + step.arg()));
			case STATS, FORM, FOES -> Component.translatable("saga.jaymospowers.step." + step.kind().name().toLowerCase(java.util.Locale.ROOT), need);
			default -> Component.translatable("saga.jaymospowers.step." + step.kind().name().toLowerCase(java.util.Locale.ROOT));
		};
	}

	private static Component itemName(String id) {
		Identifier identifier = Identifier.tryParse(id);
		Item item = identifier == null ? Items.AIR : BuiltInRegistries.ITEM.getValue(identifier);
		return new ItemStack(item).getHoverName();
	}

	// ---------------------------------------------------------------- starting and leaving

	/** Starts {@code saga} at {@code difficulty} (from the Story screen). */
	static void start(ServerPlayer player, int saga, int difficulty) {
		Data data = of(player);
		if (saga < 0 || saga >= SAGAS.length || difficulty < EASY || difficulty > HARD || data.active() || data.finished(saga) || !unlocked(data, saga)) {
			player.sendSystemMessage(Component.translatable("message.jaymospowers.zfighter_saga_cannot").withStyle(ChatFormatting.GRAY));
			return;
		}
		player.setAttached(DATA, new Data(saga, 0, 0, difficulty, data.done()));
		ServerLevel level = player.level();
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.0F, 0.8F);
		player.sendSystemMessage(Component.translatable("message.jaymospowers.zfighter_saga_start", title(saga),
			Component.translatable("screen.jaymospowers.zfighter_story." + DIFFICULTY_KEYS[difficulty])).withStyle(ChatFormatting.GOLD));
		player.sendSystemMessage(Component.translatable("saga.jaymospowers." + key(saga) + ".intro").withStyle(ChatFormatting.AQUA));
		open(player, of(player));
	}

	/** Gives the saga up (it can be started again; nothing is lost but the count). */
	static void abandon(ServerPlayer player) {
		Data data = of(player);
		if (!data.active()) {
			return;
		}
		player.setAttached(DATA, new Data(-1, 0, 0, data.difficulty(), data.done()));
		clearBosses(player);
		player.sendSystemMessage(Component.translatable("message.jaymospowers.zfighter_saga_abandoned", title(data.saga())).withStyle(ChatFormatting.GRAY));
	}

	private static Component title(int saga) {
		return Component.translatable("saga.jaymospowers." + key(saga) + ".title");
	}

	// ---------------------------------------------------------------- talking

	/** The gesture (sneak and right-click a master or the Keeper holding anything): the story first, then jobs, then a town's errands. */
	static void speak(ServerPlayer player, Entity giver, boolean keeper) {
		if (!Powers.has(player, ZFighterPower.INSTANCE)) {
			player.sendSystemMessage(Component.translatable("message.jaymospowers.zfighter_saga_no_spark").withStyle(ChatFormatting.GRAY));
			return;
		}
		if (story(player, keeper)) {
			return;
		}
		if (ZFighterSideQuests.talk(player, giver, keeper)) {
			return;
		}
		if (!keeper && giver instanceof LivingEntity living && ZFighterKingdoms.openErrands(player, living)) {
			return;
		}
		player.sendSystemMessage(Component.translatable("message.jaymospowers.zfighter_jobs_none").withStyle(ChatFormatting.GRAY));
	}

	/** Speaking moves the saga on if it asks for it (a word, or goods); otherwise it points the way to a place. True if a step was done. */
	private static boolean story(ServerPlayer player, boolean keeper) {
		Data data = of(player);
		if (!data.active()) {
			return false;
		}
		Step step = step(data.saga(), data.step());
		switch (step.kind()) {
			case TALK -> {
				if (!"keeper".equals(step.arg()) || keeper) {
					player.sendSystemMessage(Component.translatable("saga.jaymospowers." + key(data.saga()) + ".said" + data.step()).withStyle(ChatFormatting.AQUA));
					advance(player);
					return true;
				}
				player.sendSystemMessage(Component.translatable("message.jaymospowers.zfighter_saga_keeper_only").withStyle(ChatFormatting.GRAY));
			}
			case DELIVER -> {
				int need = need(step, data.difficulty());
				int gave = take(player, step.arg(), need - data.progress());
				if (gave > 0) {
					int total = data.progress() + gave;
					player.sendSystemMessage(Component.translatable("message.jaymospowers.zfighter_saga_gave", gave, itemName(step.arg()), total, need)
						.withStyle(ChatFormatting.AQUA));
					if (total >= need) {
						advance(player);
					} else {
						player.setAttached(DATA, new Data(data.saga(), data.step(), total, data.difficulty(), data.done()));
					}
					return true;
				}
			}
			case SHRINE, TOWER, GIANT -> hint(player, step);
			default -> {
			}
		}
		return false;
	}

	/** Says which way the place the step asks for lies (a shrine, the Tower, a monolith). */
	private static void hint(ServerPlayer player, Step step) {
		ServerLevel level = player.level();
		BlockPos target = null;
		MinecraftServer server = level.getServer();
		if (step.kind() == Kind.SHRINE) {
			double best = Double.MAX_VALUE;
			for (BlockPos spot : ZFighterSites.data(server).shrines()) {
				double d = flat(player, spot);
				if (d < best) {
					best = d;
					target = spot;
				}
			}
		} else if (step.kind() == Kind.TOWER) {
			target = ZFighterSites.data(server).tower().orElse(null);
		} else {
			target = ZFighterKingdoms.nearestMonolith(level, player.blockPosition());
		}
		if (target == null) {
			player.sendSystemMessage(Component.translatable("message.jaymospowers.zfighter_saga_hint_none").withStyle(ChatFormatting.GRAY));
			return;
		}
		player.sendSystemMessage(Component.translatable("message.jaymospowers.zfighter_saga_hint", Math.round(Math.sqrt(flat(player, target))),
			Component.translatable("compass.jaymospowers." + compass(player, target))).withStyle(ChatFormatting.GRAY));
	}

	private static double flat(Entity entity, BlockPos spot) {
		double dx = entity.getX() - spot.getX() - 0.5;
		double dz = entity.getZ() - spot.getZ() - 0.5;
		return dx * dx + dz * dz;
	}

	/** One of eight points of the compass, from the fighter to the place. */
	static String compass(Entity from, BlockPos to) {
		double angle = Math.toDegrees(Math.atan2(to.getZ() + 0.5 - from.getZ(), to.getX() + 0.5 - from.getX()));
		String[] points = {"east", "south_east", "south", "south_west", "west", "north_west", "north", "north_east"};
		return points[Math.floorMod(Math.round(angle / 45.0), 8)];
	}

	/** Takes up to {@code want} of the item {@code id} from his inventory; how many were taken. */
	private static int take(ServerPlayer player, String id, int want) {
		int taken = 0;
		var inventory = player.getInventory();
		for (int i = 0; i < inventory.getContainerSize() && taken < want; i++) {
			ItemStack stack = inventory.getItem(i);
			if (!stack.isEmpty() && id.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString())) {
				int n = Math.min(want - taken, stack.getCount());
				stack.shrink(n);
				taken += n;
			}
		}
		return taken;
	}

	// ---------------------------------------------------------------- moving on

	/** The step is done: its prize, then the next (or the end). */
	private static void advance(ServerPlayer player) {
		Data data = of(player);
		if (!data.active()) {
			return;
		}
		float reward = REWARD[data.difficulty()];
		ZFighterTraining.grant(player, Math.round(STEP_TP * reward));
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.6F, 1.4F);
		clearBosses(player);
		int next = data.step() + 1;
		if (next >= stepCount(data.saga())) {
			finish(player, data);
			return;
		}
		Data moved = new Data(data.saga(), next, 0, data.difficulty(), data.done());
		player.setAttached(DATA, moved);
		open(player, moved);
	}

	/** The step has just begun: its words, and the clock for a boss. */
	private static void open(ServerPlayer player, Data data) {
		player.sendSystemMessage(Component.translatable("saga.jaymospowers." + key(data.saga()) + ".step" + data.step()).withStyle(ChatFormatting.AQUA));
		player.sendSystemMessage(Component.translatable("message.jaymospowers.zfighter_saga_step", data.step() + 1, stepCount(data.saga()),
			objective(step(data.saga(), data.step()), data.difficulty())).withStyle(ChatFormatting.GOLD));
		NEXT_BOSS.put(player.getUUID(), player.level().getGameTime() + BOSS_FIRST_DELAY);
	}

	private static void finish(ServerPlayer player, Data data) {
		Saga saga = SAGAS[data.saga()];
		float reward = REWARD[data.difficulty()];
		player.setAttached(DATA, new Data(-1, 0, 0, data.difficulty(), data.done() | 1 << data.saga()));
		int tp = Math.round(saga.tp() * reward);
		ZFighterTraining.grant(player, tp);
		player.sendSystemMessage(Component.translatable("saga.jaymospowers." + saga.key() + ".done").withStyle(ChatFormatting.AQUA));
		player.sendSystemMessage(Component.translatable("message.jaymospowers.zfighter_saga_done", title(data.saga()), tp).withStyle(ChatFormatting.GOLD));
		ZFighterTraining.Data training = ZFighterTraining.of(player);
		if (!training.knows(saga.technique())) {
			ZFighterTraining.learn(player, training, saga.technique());
		} else {
			ZFighterTraining.grant(player, Math.round(saga.tp() * 0.1F * reward));
		}
		for (ItemStack stack : prize(data.saga(), reward)) {
			give(player, stack);
		}
		ServerLevel level = player.level();
		level.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD, player.getX(), player.getY() + 1.0, player.getZ(), 40, 0.5, 0.9, 0.5, 0.06);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1.0F, 1.0F);
		level.getServer().getPlayerList().broadcastSystemMessage(Component.translatable("message.jaymospowers.zfighter_saga_told", player.getDisplayName(),
			title(data.saga())).withStyle(ChatFormatting.GRAY), false);
	}

	/** The items a saga ends with, for a reward scale. */
	private static List<ItemStack> prize(int saga, float reward) {
		List<ItemStack> items = new ArrayList<>();
		switch (saga) {
			case 0 -> items.add(new ItemStack(ZFighterItems.HERMIT_BEAN, Math.max(1, Math.round(2 * reward))));
			case 1 -> items.add(new ItemStack(ZFighterItems.HERMIT_BEAN, Math.max(1, Math.round(4 * reward))));
			default -> {
				items.add(new ItemStack(ZFighterItems.HERMIT_BEAN, Math.max(1, Math.round(3 * reward))));
				items.add(new ItemStack(ZFighterItems.HEAVY_TRAINING_WEIGHTS));
			}
		}
		return items;
	}

	static void give(ServerPlayer player, ItemStack stack) {
		if (!player.getInventory().add(stack)) {
			player.drop(stack, false, net.minecraft.util.Prediction.SERVER_ONLY);
		}
	}

	// ---------------------------------------------------------------- watching

	private static void tick(MinecraftServer server) {
		long now = server.overworld().getGameTime();
		if (now % SCAN_TICKS != 0) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			Data data = of(player);
			if (!data.active() || !player.isAlive() || player.isSpectator() || !Powers.has(player, ZFighterPower.INSTANCE)) {
				continue;
			}
			Step step = step(data.saga(), data.step());
			boolean done = switch (step.kind()) {
				case STATS -> ZFighterTraining.of(player).totalLevels() >= step.amount();
				case FORM -> ZFighterTraining.formsOpen(player) >= step.amount();
				case SHRINE -> player.level().dimension() == Level.OVERWORLD && atShrine(player, server);
				case TOWER -> player.level().dimension() == Level.OVERWORLD
					&& ZFighterSites.data(server).tower().filter(t -> flat(player, t) <= TOWER_RANGE * TOWER_RANGE).isPresent();
				case CHAMBER -> player.level().dimension() == ZFighterSites.TIME_CHAMBER;
				case BOSS -> {
					keepBoss(player, data, step);
					yield false;
				}
				case GIANT -> {
					if (giantTypes().isEmpty()) {
						player.sendSystemMessage(Component.translatable("message.jaymospowers.zfighter_saga_no_giants").withStyle(ChatFormatting.GRAY));
						yield true;
					}
					yield false;
				}
				default -> false;
			};
			if (done) {
				advance(player);
			}
		}
	}

	private static boolean atShrine(ServerPlayer player, MinecraftServer server) {
		for (BlockPos spot : ZFighterSites.data(server).shrines()) {
			if (flat(player, spot) <= SHRINE_RANGE * SHRINE_RANGE) {
				return true;
			}
		}
		return false;
	}

	/** The Giants this world has (those whose entity ids are registered). */
	private static List<net.minecraft.world.entity.EntityType<?>> giantTypes() {
		List<net.minecraft.world.entity.EntityType<?>> types = new ArrayList<>();
		for (String id : GIANTS) {
			Identifier identifier = Identifier.tryParse(id);
			if (identifier != null && BuiltInRegistries.ENTITY_TYPE.containsKey(identifier)) {
				types.add(BuiltInRegistries.ENTITY_TYPE.getValue(identifier));
			}
		}
		return types;
	}

	private static boolean giant(Entity entity) {
		Identifier key = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
		for (String id : GIANTS) {
			if (id.equals(key.toString())) {
				return true;
			}
		}
		return false;
	}

	// ---------------------------------------------------------------- bosses

	private static String stepTag(int saga, int step) {
		return "jaymospowers_saga_" + saga + "_" + step;
	}

	private static String ownerTag(UUID owner) {
		return BOSS_TAG + "_" + owner;
	}

	/** While a boss step is open, a boss stands near the fighter (or a party member's); if he is gone, another steps in after a wait. */
	private static void keepBoss(ServerPlayer player, Data data, Step step) {
		ServerLevel level = player.level();
		if (level.dimension() != Level.OVERWORLD) {
			return;
		}
		long now = level.getGameTime();
		String tag = stepTag(data.saga(), data.step());
		boolean there = !level.getEntitiesOfClass(ZFighterRogue.class, player.getBoundingBox().inflate(BOSS_NEAR),
			e -> e.isAlive() && e.entityTags().contains(tag)).isEmpty();
		if (there) {
			NEXT_BOSS.put(player.getUUID(), now + BOSS_RESPAWN);
			return;
		}
		Long next = NEXT_BOSS.get(player.getUUID());
		if (next == null) {
			NEXT_BOSS.put(player.getUUID(), now + BOSS_FIRST_DELAY);
			return;
		}
		if (now < next) {
			return;
		}
		NEXT_BOSS.put(player.getUUID(), now + BOSS_RESPAWN);
		Boss boss = null;
		for (Boss candidate : BOSSES) {
			if (candidate.key().equals(step.arg())) {
				boss = candidate;
			}
		}
		if (boss != null) {
			clearBosses(player);
			spawn(player, data, boss);
		}
	}

	private static void spawn(ServerPlayer player, Data data, Boss boss) {
		ServerLevel level = player.level();
		ZFighterRogue fighter = ZFighterRaids.ROGUE.create(level, EntitySpawnReason.EVENT);
		if (fighter == null) {
			return;
		}
		RandomSource random = level.getRandom();
		double angle = random.nextDouble() * Math.PI * 2.0;
		double distance = BOSS_MIN + random.nextDouble() * (BOSS_MAX - BOSS_MIN);
		BlockPos at = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, BlockPos.containing(player.getX() + Math.cos(angle) * distance, 0,
			player.getZ() + Math.sin(angle) * distance));
		fighter.snapTo(at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, random.nextFloat() * 360.0F, 0.0F);
		fighter.become(ZFighterRogue.TYRANT, Component.translatable("entity.jaymospowers.saga_boss." + boss.key()));
		double health = ZFighterRogue.TYRANT_HEALTH * boss.health() * BOSS_HEALTH[data.difficulty()];
		fighter.getAttribute(Attributes.MAX_HEALTH).setBaseValue(health);
		fighter.setHealth((float)health);
		fighter.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(ZFighterRogue.TYRANT_DAMAGE * boss.damage() * BOSS_DAMAGE[data.difficulty()]);
		fighter.addTag(BOSS_TAG);
		fighter.addTag(ownerTag(player.getUUID()));
		fighter.addTag(stepTag(data.saga(), data.step()));
		fighter.setPersistenceRequired();
		fighter.setTarget(player);
		level.addFreshEntity(fighter);
		LightningBolt bolt = EntityTypes.LIGHTNING_BOLT.create(level, EntitySpawnReason.TRIGGERED);
		if (bolt != null) {
			bolt.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
			bolt.setVisualOnly(true);
			level.addFreshEntity(bolt);
		}
		level.playSound(null, at, SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 3.0F, 1.3F);
		player.sendSystemMessage(Component.translatable("message.jaymospowers.zfighter_saga_boss", fighter.getDisplayName()).withStyle(ChatFormatting.RED));
	}

	/** Sends his saga's bosses away (the step is over, or he gave up). */
	private static void clearBosses(ServerPlayer player) {
		String tag = ownerTag(player.getUUID());
		ServerLevel level = player.level();
		for (ZFighterRogue boss : level.getEntities(EntityTypeTest.forClass(ZFighterRogue.class), e -> e.entityTags().contains(tag))) {
			boss.discard();
		}
	}

	// ---------------------------------------------------------------- kills

	private static void afterDeath(LivingEntity entity, DamageSource source) {
		if (!(entity.level() instanceof ServerLevel level)) {
			return;
		}
		ServerPlayer killer = killer(level, entity, source);
		if (killer == null) {
			return;
		}
		List<ServerPlayer> credited = witnesses(killer, entity);
		boolean boss = entity instanceof ZFighterRogue && entity.entityTags().contains(BOSS_TAG);
		boolean giant = !boss && giant(entity);
		for (ServerPlayer player : credited) {
			if (!Powers.has(player, ZFighterPower.INSTANCE)) {
				continue;
			}
			Data data = of(player);
			if (data.active()) {
				Step step = step(data.saga(), data.step());
				if (boss && step.kind() == Kind.BOSS && entity.entityTags().contains(stepTag(data.saga(), data.step()))) {
					player.sendSystemMessage(Component.translatable("message.jaymospowers.zfighter_saga_boss_down", entity.getDisplayName()).withStyle(ChatFormatting.GOLD));
					advance(player);
					continue;
				}
				if (giant && step.kind() == Kind.GIANT) {
					player.sendSystemMessage(Component.translatable("message.jaymospowers.zfighter_saga_boss_down", entity.getDisplayName()).withStyle(ChatFormatting.GOLD));
					advance(player);
					continue;
				}
				if (step.kind() == Kind.FOES && entity instanceof Enemy) {
					int total = data.progress() + 1;
					if (total >= need(step, data.difficulty())) {
						advance(player);
					} else {
						player.setAttached(DATA, new Data(data.saga(), data.step(), total, data.difficulty(), data.done()));
					}
				}
			}
			if (entity instanceof Enemy) {
				ZFighterSideQuests.foeFell(player);
			}
		}
	}

	private static @Nullable ServerPlayer killer(ServerLevel level, LivingEntity victim, DamageSource source) {
		if (source.getEntity() instanceof ServerPlayer player && player != victim) {
			return player;
		}
		if (victim instanceof ZFighterRogue rogue) {
			UUID best = null;
			float most = 0.0F;
			for (Map.Entry<UUID, Float> hurt : rogue.hurtBy.entrySet()) {
				if (hurt.getValue() > most) {
					most = hurt.getValue();
					best = hurt.getKey();
				}
			}
			return best == null ? null : level.getServer().getPlayerList().getPlayer(best);
		}
		return null;
	}

	/** The killer and the members of his party within {@link #PARTY_RANGE} blocks of the victim. */
	static List<ServerPlayer> witnesses(ServerPlayer killer, Entity victim) {
		List<ServerPlayer> out = new ArrayList<>();
		out.add(killer);
		for (ServerPlayer member : ZFighterKingdoms.partyMembers(killer)) {
			if (member != killer && member.isAlive() && member.level() == victim.level() && member.distanceToSqr(victim) <= PARTY_RANGE * PARTY_RANGE) {
				out.add(member);
			}
		}
		return out;
	}

	/** The party members of {@code player} within range of him (himself included); a job done by one counts for these. */
	static List<ServerPlayer> neighbours(ServerPlayer player) {
		return witnesses(player, player);
	}
}
