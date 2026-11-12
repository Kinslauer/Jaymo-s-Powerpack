package com.jaymospowers.core.wounds;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.power.zfighter.ZFighterAfterlife;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.BlockUtil;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.piglin.PiglinAi;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * Andy (2026-11-11): "a karmic alignment system, meaning your actions are weighed against you after death, meaning if you
 * were to roll a real death and reincarnate, you get a last chance dependent on your alignment. If you were evil you
 * reincarnate in the nether, one last chance to continue your life, however it will be extremely difficult. Or if you were
 * just you end up in the otherworld, allowed the chance to respawn instead of reincarnating, so good karma pays off. (You
 * will still have to fight in the tournament as a non z fighter)".
 * <p>
 * Every player carries a hidden weight for the life they're living ({@link #KARMA}, {@link #MIN} to {@link #MAX}, never
 * shown as a number): what they do for others raises it (putting down monsters and the dead, getting the fallen up, a good
 * name with lawful settlements, which Jaymo's Kingdoms feeds through {@link #add}), what they do to them lowers it (killing
 * villagers, players and other people's animals, crimes that cost a settlement's good name). When {@link Mortality} ends the
 * life ({@link #weigh}) the weight decides what follows:
 * <ul>
 * <li><b>Just</b> ({@link #JUST} or more): they wake in the Other World with the dead ({@link ZFighterAfterlife}), Z Fighter
 * or not. The fortune teller takes no coin from them; winning the tournament of the dead sends them back as they were. Let
 * go by the long wait before winning, the life is over after all ({@link #letGo}). Going back costs half the weight
 * ({@link #returned}).</li>
 * <li><b>Wicked</b> ({@link #WICKED} or less), once in a life ({@link #SPENT}): they wake in the Nether
 * ({@link #begin}, {@link #land}), hurt and hungry, {@link #WAKE_MIN} to {@link #WAKE_MAX} blocks from a lit portal (the
 * nearest one standing, or one built for it), told only which way its cold draught blows from. Everything hostile down
 * there comes for them ({@link #hunted}), and a death there is final with no roll. Out of the Nether alive, the life is
 * theirs again ({@link #out}).</li>
 * <li>Anything between: born again, as before.</li>
 * </ul>
 * A new life starts weightless ({@link Mortality#wipe} takes every mark of the old one).
 */
public final class Karma {
	// ---- tuning ----
	public static final float MIN = -100.0F, MAX = 100.0F;
	/** At or over {@link #JUST} a life that ends is let into the Other World; at or under {@link #WICKED} it is sent below. */
	public static final float JUST = 25.0F, WICKED = -25.0F;
	/** What a kill weighs: a villager (a child, more), a player who wasn't wicked, a wicked one, another's tamed animal. */
	static final float KILL_INNOCENT = -8.0F, KILL_CHILD = -14.0F, KILL_PLAYER = -12.0F, KILL_WICKED_PLAYER = 2.0F, KILL_PET = -4.0F;
	/** An outlaw (Kingdoms says who), a monster, one of the walking dead, a boss. */
	static final float KILL_OUTLAW = 0.3F, KILL_MONSTER = 0.15F, KILL_UNDEAD = 0.25F, KILL_BOSS = 4.0F;
	/** Getting the fallen up: a player, a villager, anything else that isn't a monster. */
	static final float RAISE_PLAYER = 5.0F, RAISE_VILLAGER = 3.0F, RAISE_OTHER = 0.5F;
	/** The weight at which a player's guilt is whole ({@link #guilt}), and how guilty an outlaw, and an illager or raider, stands. */
	public static final float GUILT_FULL = -60.0F, GUILT_OUTLAW = 0.5F, GUILT_RAIDER = 0.75F;
	/** The share of the weight a just one keeps when they go back as they were. */
	static final float RETURN_KEEPS = 0.5F;
	/** How far (blocks) from the way up one sent below wakes. */
	static final int WAKE_MIN = 140, WAKE_MAX = 220;
	/** The heights a waking spot is looked for between, and how many columns of the chunk are tried. */
	static final int WAKE_LOW = 33, WAKE_HIGH = 108, WAKE_TRIES = 96;
	/** The share of their health, and the food, they wake below with. */
	static final float BELOW_HEALTH = 0.3F;
	static final int BELOW_FOOD = 6;
	/** How long (ticks) the Nether's chunks may take to load before they are loaded on the spot. */
	static final int LOAD_LIMIT = 20 * 45;
	/** How long they're out cold while the way down opens (ticks; cut short when it has). */
	static final int SINK_TICKS = 20 * 60;
	/** How often (ticks) the Nether's own turn on one sent below, how far round, and how often the draught is felt. */
	static final int HUNT_EVERY = 100, DRAUGHT_EVERY = 20 * 45;
	static final double HUNT_RANGE = 24.0;
	/** Within this many blocks of the way up, the draught is "close". */
	static final double DRAUGHT_NEAR = 24.0;
	/** A just one who never reached the Other World (it was shut meanwhile) is born again after this many seconds. */
	static final int STRAY_SECONDS = 8;

	/** The weight of this life. Saved, kept through death, gone with the life ({@link Mortality#wipe}). */
	public static final AttachmentType<Float> KARMA = AttachmentRegistry.create(JaymosPowers.id("karma"),
		builder -> builder.persistent(Codec.FLOAT).copyOnDeath());
	/**
	 * What the weighing gave a life that ended: {@code just} (from the death until they leave the Other World),
	 * {@code wicked} (from the death until they're put below), {@code below} (on their last chance in the Nether).
	 */
	public static final AttachmentType<String> FATE = AttachmentRegistry.create(JaymosPowers.id("karma_fate"),
		builder -> builder.persistent(Codec.STRING).copyOnDeath());
	/** The last chance below was given this life: it isn't given twice. */
	public static final AttachmentType<Boolean> SPENT = AttachmentRegistry.create(JaymosPowers.id("karma_spent"),
		builder -> builder.persistent(Codec.BOOL).copyOnDeath());
	/** In the Nether: where the way up is looked for (set at the death), then where it stands (once they're below). */
	public static final AttachmentType<BlockPos> EXIT = AttachmentRegistry.create(JaymosPowers.id("karma_exit"),
		builder -> builder.persistent(BlockPos.CODEC).copyOnDeath());

	/**
	 * Other tests of who is no innocent, whatever they look like (Kingdoms adds its outlaws: looters, assassins, a camp's
	 * raiders, anyone in a bandit's mask). Killing one weighs a little for you, not against.
	 */
	public static final List<Predicate<Entity>> GUILTY = new ArrayList<>();

	public enum Verdict {
		EVEN, JUST, WICKED
	}

	private static final String F_JUST = "just", F_WICKED = "wicked", F_BELOW = "below";
	private static final String[] DIRECTIONS = {"n", "ne", "e", "se", "s", "sw", "w", "nw"};
	/** Holds the Nether's chunks while a way down opens: no timeout, loads, never saved. */
	private static final TicketType DESCENT = new TicketType(TicketType.NO_TIMEOUT, TicketType.FLAG_LOADING);

	/** A way down being opened: the way up (found, or to be built at {@code exit}), the waking spot's chunk, their loading. */
	private record Descent(BlockPos exit, boolean build, BlockPos wake, CompletableFuture<?> exitLoad, CompletableFuture<?> wakeLoad, int since) {
	}

	private static final Map<UUID, Descent> DESCENTS = new HashMap<>();
	private static final Map<UUID, Integer> STRAYS = new HashMap<>();
	/** What downed something that then died of being down (the killing blow itself often has nobody behind it). */
	private static final Map<LivingEntity, DamageSource> FELLED = new WeakHashMap<>();

	private Karma() {
	}

	static void register() {
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			DamageSource felled = FELLED.remove(entity);
			Entity by = source.getEntity();
			if (by == null && felled != null) {
				by = felled.getEntity();
			}
			if (by instanceof ServerPlayer killer && killer != entity) {
				add(killer, worth(killer, entity));
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(Karma::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			DESCENTS.clear();
			STRAYS.clear();
			FELLED.clear();
		});
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(Commands.literal("karma")
			.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
			.executes(c -> show(c.getSource(), c.getSource().getPlayerOrException()))
			.then(Commands.argument("player", EntityArgument.player())
				.executes(c -> show(c.getSource(), EntityArgument.getPlayer(c, "player")))
				.then(Commands.literal("set").then(Commands.argument("weight", FloatArgumentType.floatArg(MIN, MAX))
					.executes(c -> {
						ServerPlayer player = EntityArgument.getPlayer(c, "player");
						player.setAttached(KARMA, FloatArgumentType.getFloat(c, "weight"));
						return show(c.getSource(), player);
					}))))));
	}

	private static int show(net.minecraft.commands.CommandSourceStack source, ServerPlayer player) {
		float weight = of(player);
		String fate = player.getAttached(FATE);
		source.sendSuccess(() -> Component.translatable("commands.jaymospowers.karma.show", player.getDisplayName(),
			String.format(java.util.Locale.ROOT, "%.1f", weight), weight >= JUST ? "just" : weight <= WICKED ? "wicked" : "even",
			fate == null ? "-" : fate), false);
		return Math.round(weight);
	}

	// ---- the weight ---------------------------------------------------------------------------------------------------------

	/** The weight of {@code player}'s life so far. */
	public static float of(Player player) {
		return player.getAttachedOrElse(KARMA, 0.0F);
	}

	/**
	 * {@code by} more (or less) on {@code player}'s life. Crossing into just or wicked is felt, never explained. Public for
	 * Jaymo's Kingdoms (reputation with lawful settlements), which calls it by name.
	 */
	public static void add(ServerPlayer player, float by) {
		if (by == 0.0F || Float.isNaN(by) || player.isCreative() || player.isSpectator() || player.hasAttached(FATE)) {
			return;
		}
		float before = of(player), after = Mth.clamp(before + by, MIN, MAX);
		if (after == before) {
			return;
		}
		player.setAttached(KARMA, after);
		if (before < JUST && after >= JUST) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.karma.lighter").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
		} else if (before > WICKED && after <= WICKED) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.karma.heavier").withStyle(ChatFormatting.DARK_RED, ChatFormatting.ITALIC));
		}
	}

	/**
	 * How guilty {@code who} stands, 0 (blameless) to 1: a player by the weight of their life (nothing at or above even, all
	 * of it at {@link #GUILT_FULL} or under; the harm done to villagers and players, and to a realm's good name, is in it),
	 * an illager or a raider by {@link #GUILT_RAIDER},
	 * one of the folk outside the law ({@link #GUILTY}: Kingdoms' outlaws) by {@link #GUILT_OUTLAW}, anything else none.
	 */
	public static float guilt(LivingEntity who) {
		if (who instanceof Player player) {
			return Mth.clamp(-of(player) / -GUILT_FULL, 0.0F, 1.0F);
		}
		// (Andy: "they certainly should be guilty": every illager, and whoever rides with a raid; not the beast they bring)
		if (who instanceof net.minecraft.world.entity.monster.illager.AbstractIllager
			|| who instanceof net.minecraft.world.entity.raid.Raider && !(who instanceof net.minecraft.world.entity.monster.Ravager)) {
			return GUILT_RAIDER;
		}
		for (Predicate<Entity> test : GUILTY) {
			try {
				if (test.test(who)) {
					return GUILT_OUTLAW;
				}
			} catch (RuntimeException e) {
				// (a broken test counts for nothing)
			}
		}
		return 0.0F;
	}

	/** How the Judge reads a page, 1 (wicked) to 5 (just): for his talk, never a number. */
	public static int band(Player player) {
		float weight = of(player);
		return weight <= WICKED ? 1 : weight <= WICKED / 3.0F ? 2 : weight < JUST / 3.0F ? 3 : weight < JUST ? 4 : 5;
	}

	/** What killing {@code dead} weighs for {@code killer}. */
	private static float worth(ServerPlayer killer, LivingEntity dead) {
		if (dead instanceof Player victim) {
			return of(victim) <= WICKED ? KILL_WICKED_PLAYER : KILL_PLAYER;
		}
		for (Predicate<Entity> test : GUILTY) {
			try {
				if (test.test(dead)) {
					return KILL_OUTLAW;
				}
			} catch (RuntimeException e) {
				// (a broken test counts for nothing)
			}
		}
		if (dead instanceof AbstractVillager) {
			return dead.isBaby() ? KILL_CHILD : KILL_INNOCENT;
		}
		if (dead instanceof TamableAnimal pet && pet.isTame() && !pet.isOwnedBy(killer)) {
			return KILL_PET;
		}
		if (dead instanceof Enemy) {
			return Mortality.boss(dead) ? KILL_BOSS : dead.getType().builtInRegistryHolder().is(EntityTypeTags.UNDEAD) ? KILL_UNDEAD : KILL_MONSTER;
		}
		return 0.0F;
	}

	/** Told by the downed state: {@code entity} is dying of being down, and {@code felled} is what put it there. */
	static void felled(LivingEntity entity, @Nullable DamageSource felled) {
		if (felled != null && felled.getEntity() instanceof Player) {
			FELLED.put(entity, felled);
		}
	}

	/** {@code helper} got {@code body} back on its feet. */
	static void broughtRound(ServerPlayer helper, LivingEntity body) {
		if (body == helper || body instanceof Enemy) {
			return;
		}
		add(helper, body instanceof Player ? RAISE_PLAYER : body instanceof AbstractVillager ? RAISE_VILLAGER : RAISE_OTHER);
	}

	// ---- the weighing -------------------------------------------------------------------------------------------------------

	public static boolean just(Player player) {
		return F_JUST.equals(player.getAttached(FATE));
	}

	/** Weighed wicked and not yet put below. */
	public static boolean wicked(Player player) {
		return F_WICKED.equals(player.getAttached(FATE));
	}

	/** On their last chance in the Nether. */
	public static boolean below(Player player) {
		return F_BELOW.equals(player.getAttached(FATE));
	}

	/** Weighed, and what follows hasn't begun yet: no other death is rolled meanwhile. */
	static boolean pending(Player player) {
		return just(player) || wicked(player);
	}

	/**
	 * {@code player}'s life has just ended ({@link Mortality}): what the weight makes of it, marked on them. {@link Verdict#EVEN}
	 * marks nothing (the caller ends the life as before).
	 */
	static Verdict weigh(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		float weight = of(player);
		if (weight >= JUST && ZFighterAfterlife.open(server)) {
			player.setAttached(FATE, F_JUST);
			JaymosPowers.LOGGER.info("{} weighed {}: just, let into the Other World", player.getScoreboardName(), weight);
			return Verdict.JUST;
		}
		if (weight <= WICKED && !player.hasAttached(SPENT) && server.getLevel(Level.NETHER) != null) {
			player.setAttached(FATE, F_WICKED);
			player.setAttached(SPENT, true);
			player.setAttached(EXIT, towards(player));
			JaymosPowers.LOGGER.info("{} weighed {}: wicked, sent below", player.getScoreboardName(), weight);
			return Verdict.WICKED;
		}
		return Verdict.EVEN;
	}

	/** Where in the Nether the way up is looked for: under where they fell (or the world's spawn, from anywhere else). */
	private static BlockPos towards(ServerPlayer player) {
		ServerLevel level = player.level();
		if (level.dimension() == Level.NETHER) {
			return new BlockPos(player.getBlockX(), 64, player.getBlockZ());
		}
		BlockPos from = level.dimension() == Level.OVERWORLD ? player.blockPosition() : level.getServer().overworld().getRespawnData().pos();
		return new BlockPos(Mth.floor(from.getX() / 8.0), 64, Mth.floor(from.getZ() / 8.0));
	}

	/** The life is over after all: born again like anyone ({@link Mortality#endLife}, once they stand in the world). */
	private static void over(ServerPlayer player) {
		player.removeAttached(FATE);
		player.removeAttached(EXIT);
		player.setAttached(Mortality.FINAL, "other");
	}

	// ---- the just -----------------------------------------------------------------------------------------------------------

	/** The Other World let go of a just one before they won: the life is over. */
	public static void letGo(ServerPlayer player) {
		over(player);
		player.sendSystemMessage(Component.translatable("message.jaymospowers.karma.let_go").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
	}

	/** A just one chose to stay in the high places for good (the Judge): the weighing is settled and the page keeps its weight. */
	public static void stayed(ServerPlayer player) {
		player.removeAttached(FATE);
		player.removeAttached(EXIT);
	}

	/** One of the dead is leaving the Other World: a just one going back as they were pays for it with part of the weight. */
	public static void returned(ServerPlayer player) {
		if (!just(player)) {
			return;
		}
		player.removeAttached(FATE);
		if (!Mortality.isFinal(player)) {
			player.setAttached(KARMA, of(player) * RETURN_KEEPS);
		}
	}

	// ---- the wicked ---------------------------------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		if (!DESCENTS.isEmpty()) {
			descents(server);
		}
		if (server.getTickCount() % 20 != 0) {
			return;
		}
		for (ServerPlayer player : new ArrayList<>(server.getPlayerList().getPlayers())) {
			String fate = player.getAttached(FATE);
			if (fate == null || !player.isAlive() || Mortality.isFinal(player)) {
				continue;
			}
			switch (fate) {
				case F_WICKED -> {
					if (!DESCENTS.containsKey(player.getUUID())) {
						begin(server, player);
					}
				}
				case F_BELOW -> below(server, player);
				case F_JUST -> {
					if (ZFighterAfterlife.among(player)) {
						STRAYS.remove(player.getUUID());
					} else if (STRAYS.merge(player.getUUID(), 1, Integer::sum) >= STRAY_SECONDS) {
						// (the Other World was shut, or never took them: no chance to give)
						STRAYS.remove(player.getUUID());
						over(player);
					}
				}
				default -> player.removeAttached(FATE);
			}
		}
	}

	/** Back on their feet after the death: out cold again while the way down opens (the way up found, the Nether loaded). */
	private static void begin(MinecraftServer server, ServerPlayer player) {
		ServerLevel nether = server.getLevel(Level.NETHER);
		if (nether == null) {
			over(player);
			return;
		}
		BlockPos target = player.getAttachedOrElse(EXIT, towards(player));
		if (!nether.getWorldBorder().isWithinBounds(target)) {
			target = new BlockPos(Mth.floor(nether.getWorldBorder().getCenterX()), 64, Mth.floor(nether.getWorldBorder().getCenterZ()));
		}
		Optional<BlockPos> standing = nether.getPortalForcer().findClosestPortalPosition(target, false, nether.getWorldBorder());
		BlockPos exit = standing.orElse(target);
		RandomSource random = player.getRandom();
		BlockPos wake = exit;
		for (int i = 0; i < 12; i++) {
			double angle = random.nextDouble() * Math.PI * 2.0;
			int far = WAKE_MIN + random.nextInt(WAKE_MAX - WAKE_MIN + 1);
			BlockPos at = new BlockPos(exit.getX() + Mth.floor(Math.cos(angle) * far), 64, exit.getZ() + Mth.floor(Math.sin(angle) * far));
			if (nether.getWorldBorder().isWithinBounds(at)) {
				wake = at;
				break;
			}
		}
		CompletableFuture<?> exitLoad = standing.isPresent() ? CompletableFuture.completedFuture(null)
			: nether.getChunkSource().addTicketAndLoadWithRadius(DESCENT, ChunkPos.containing(exit), 2);
		CompletableFuture<?> wakeLoad = nether.getChunkSource().addTicketAndLoadWithRadius(DESCENT, ChunkPos.containing(wake), 0);
		DESCENTS.put(player.getUUID(), new Descent(exit, standing.isEmpty(), wake, exitLoad, wakeLoad, server.getTickCount()));
		String line = "message.jaymospowers.mortality.wake.sinking";
		Downed.knockOut(player.level(), player, SINK_TICKS, false, line, false);
		player.setAttached(Mortality.WAKE_LINE, line);
	}

	private static void descents(MinecraftServer server) {
		ServerLevel nether = server.getLevel(Level.NETHER);
		for (Iterator<Map.Entry<UUID, Descent>> it = DESCENTS.entrySet().iterator(); it.hasNext();) {
			Map.Entry<UUID, Descent> entry = it.next();
			Descent descent = entry.getValue();
			ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
			if (nether == null || player == null || !player.isAlive() || !wicked(player)) {
				// (gone, or dead again: it begins anew when they're back on their feet)
				it.remove();
				release(nether, descent);
				continue;
			}
			boolean late = server.getTickCount() - descent.since() > LOAD_LIMIT;
			if (!late && !(descent.exitLoad().isDone() && descent.wakeLoad().isDone())) {
				continue;
			}
			it.remove();
			try {
				land(nether, player, descent);
			} catch (RuntimeException e) {
				JaymosPowers.LOGGER.warn("{} couldn't be sent below: their life ends as any other", player.getScoreboardName(), e);
				over(player);
				if (Downed.isDowned(player)) {
					Downed.forgetQuietly(player);
				}
			}
			release(nether, descent);
		}
	}

	private static void release(@Nullable ServerLevel nether, Descent descent) {
		if (nether == null) {
			return;
		}
		if (descent.build()) {
			nether.getChunkSource().removeTicketWithRadius(DESCENT, ChunkPos.containing(descent.exit()), 2);
		}
		nether.getChunkSource().removeTicketWithRadius(DESCENT, ChunkPos.containing(descent.wake()), 0);
	}

	/** The way down is open: the way up is built if none stood near, and they wake far from it, hurt and hungry. */
	private static void land(ServerLevel nether, ServerPlayer player, Descent descent) {
		BlockPos exit = descent.exit();
		RandomSource random = player.getRandom();
		if (descent.build()) {
			Optional<BlockUtil.FoundRectangle> built = nether.getPortalForcer().createPortal(exit, random.nextBoolean() ? Direction.Axis.X : Direction.Axis.Z);
			if (built.isPresent()) {
				exit = built.get().minCorner;
			}
		}
		BlockPos at = spot(nether, descent.wake(), random);
		player.setAttached(EXIT, exit);
		player.setAttached(FATE, F_BELOW);
		player.teleportTo(nether, at.getX() + 0.5, at.getY(), at.getZ() + 0.5, Set.of(), random.nextFloat() * 360.0F, 0.0F, true);
		player.resetFallDistance();
		player.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
		player.clearFire();
		player.setHealth(Math.max(1.0F, player.getMaxHealth() * BELOW_HEALTH));
		player.getFoodData().setFoodLevel(BELOW_FOOD);
		if (Downed.isDowned(player)) {
			Downed.forgetQuietly(player);
		}
		String line = "message.jaymospowers.mortality.wake.below";
		Downed.knockOut(nether, player, Mortality.OUT_AFTER, false, line, false);
		player.setAttached(Mortality.WAKE_LINE, line);
		nether.playSound(null, at, SoundEvents.SOUL_ESCAPE.value(), SoundSource.PLAYERS, 1.0F, 0.6F);
		JaymosPowers.LOGGER.info("{} wakes below at {}; the way up is at {}", player.getScoreboardName(), at.toShortString(), exit.toShortString());
	}

	/**
	 * Somewhere to wake in {@code near}'s chunk: solid ground under a roof, two blocks of air, no lava beside it. A pocket is
	 * cut for them if the chunk has none (a lava sea, solid netherrack).
	 */
	private static BlockPos spot(ServerLevel nether, BlockPos near, RandomSource random) {
		int x0 = near.getX() & ~15, z0 = near.getZ() & ~15;
		BlockPos.MutableBlockPos feet = new BlockPos.MutableBlockPos();
		for (int i = 0; i < WAKE_TRIES; i++) {
			int x = x0 + random.nextInt(16), z = z0 + random.nextInt(16);
			for (int y = WAKE_HIGH; y >= WAKE_LOW; y--) {
				feet.set(x, y, z);
				if (standable(nether, feet)) {
					return feet.immutable();
				}
			}
		}
		BlockPos floor = new BlockPos(x0 + 8, 70, z0 + 8);
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				nether.setBlockAndUpdate(floor.offset(dx, 0, dz), Blocks.NETHERRACK.defaultBlockState());
				for (int dy = 1; dy <= 3; dy++) {
					nether.setBlockAndUpdate(floor.offset(dx, dy, dz), Blocks.AIR.defaultBlockState());
				}
			}
		}
		return floor.above();
	}

	private static boolean standable(ServerLevel nether, BlockPos feet) {
		BlockPos under = feet.below();
		BlockState ground = nether.getBlockState(under);
		if (!ground.isFaceSturdy(nether, under, Direction.UP) || ground.is(Blocks.MAGMA_BLOCK) || ground.is(Blocks.BEDROCK)
			|| !nether.getBlockState(feet).isAir() || !nether.getBlockState(feet.above()).isAir()) {
			return false;
		}
		for (Direction side : Direction.Plane.HORIZONTAL) {
			if (nether.getFluidState(feet.relative(side)).is(FluidTags.LAVA) || nether.getFluidState(under.relative(side)).is(FluidTags.LAVA)
				|| nether.getBlockState(feet.relative(side)).is(Blocks.FIRE)) {
				return false;
			}
		}
		return true;
	}

	/** Each second on the last chance: out of the Nether it is won; in it, they're hunted and the draught is felt. */
	private static void below(MinecraftServer server, ServerPlayer player) {
		ServerLevel level = player.level();
		if (level.dimension() != Level.NETHER) {
			out(player);
			return;
		}
		if (Downed.isDowned(player) || player.isCreative() || player.isSpectator()) {
			return;
		}
		int now = server.getTickCount();
		if (now % HUNT_EVERY == 0) {
			hunted(level, player);
		}
		BlockPos exit = player.getAttached(EXIT);
		if (exit != null && now % DRAUGHT_EVERY == 0) {
			double dx = exit.getX() + 0.5 - player.getX(), dz = exit.getZ() + 0.5 - player.getZ();
			if (dx * dx + dz * dz <= DRAUGHT_NEAR * DRAUGHT_NEAR) {
				player.sendOverlayMessage(Component.translatable("message.jaymospowers.karma.draught_near").withStyle(ChatFormatting.AQUA, ChatFormatting.ITALIC));
			} else {
				// (north is -z, east is +x)
				int way = Math.floorMod(Math.round((float)(Math.toDegrees(Math.atan2(dx, -dz)) / 45.0)), 8);
				player.sendOverlayMessage(Component.translatable("message.jaymospowers.karma.draught",
					Component.translatable("message.jaymospowers.karma.dir." + DIRECTIONS[way])).withStyle(ChatFormatting.AQUA, ChatFormatting.ITALIC));
			}
		}
	}

	/** Everything hostile near one sent below, and every piglin, comes for them. */
	private static void hunted(ServerLevel level, ServerPlayer player) {
		PiglinAi.angerNearbyPiglins(level, player, false);
		for (Mob mob : level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(HUNT_RANGE),
			m -> m.isAlive() && m.getTarget() == null && (m instanceof Enemy || m instanceof NeutralMob))) {
			if (mob instanceof NeutralMob neutral) {
				neutral.setPersistentAngerTarget(EntityReference.of((LivingEntity)player));
				neutral.startPersistentAngerTimer();
			}
			mob.setTarget(player);
		}
	}

	/** They walked out: the life is theirs again. The weight stays as it was, and the chance isn't given twice. */
	private static void out(ServerPlayer player) {
		player.removeAttached(FATE);
		player.removeAttached(EXIT);
		player.sendSystemMessage(Component.translatable("message.jaymospowers.karma.out").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
		JaymosPowers.LOGGER.info("{} walked out of the Nether: their life goes on", player.getScoreboardName());
	}
}
