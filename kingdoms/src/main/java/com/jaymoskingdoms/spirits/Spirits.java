package com.jaymoskingdoms.spirits;

import com.jaymoskingdoms.Classes;
import com.jaymoskingdoms.Downed;
import com.jaymoskingdoms.Folk;
import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.Mana;
import com.jaymoskingdoms.ManaSickness;
import com.jaymoskingdoms.PowersCompat;
import com.jaymoskingdoms.Sorcery;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.entity.monster.illager.Evoker;
import net.minecraft.world.entity.monster.illager.Illusioner;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.236) Mana spirits. Andy (2026-10-26): "a mage hand type of ability that mimics the way stands work but not their
 * presentation ... use of the mage hands calls upon a mana spirit, each of which has a differing personality of 5, one of
 * those being seemingly non responsive at all ... these spirits should conjure as arms behind you with the same effects as
 * your mana aura making up their entire form ... some just give you buffs ... some have entire move sets but all can be
 * upgraded and leveled and the ones with personalities can kick in on their own to assist you at times similar to how the
 * symbiote operates. make them speak like wise wizards from ancient times looking after a young child or grumpy old wizards
 * that are tired of looking after said young child ... Make them give battle advice while in combat at higher bonding levels."
 * <ul>
 * <li><b>One for life:</b> an attuned player who nearly dies may be answered by a spirit ({@link SpiritSickness}); they
 * sicken for up to five days, and when it wakes it is theirs. It can be released at a cost and none other is called.</li>
 * <li><b>Summoned</b> with the spirit key ({@link Key}): conjured as arms of mana behind you, it drains
 * {@link #DRAIN_PER_SECOND} a second and fades when you're {@link #TIRED_AT} from empty. Its moves are the shared hands
 * ({@link HandMoves}) then its own ({@link Spirit#moves}), unlocked by level.</li>
 * <li><b>Bond</b> grows out with it, in fights won together, and meditating; at {@link #ACT_BOND} it acts on its own
 * ({@link #act}: every such act flashes your screen), from {@link #IDLE_BOND} it remarks, from {@link #ADVICE_BOND} it
 * advises in a fight, and from {@link #AHEAD_BOND} it warns you before one.</li>
 * </ul>
 * The client draws the arms ({@code SpiritArms}); the state they read is {@link #SPIRIT}.
 */
public final class Spirits {
	/** The kinds ({@link SpiritState#kind}). Only the first two can be gained yet. */
	public static final int GRIMSBY = 0, VARRO = 1, AMBROSE = 2, THESSALY = 3, QUIET = 4;
	/** The key actions ({@link Key#action}). */
	public static final int SUMMON = 0, CAST = 1, NEXT = 2, DISMISS = 3;
	/** What a screen flash is for ({@link #FLASH}). */
	public static final int FLASH_ACT = 0, FLASH_PARRY = 1, FLASH_WARD = 2, FLASH_WARN = 3;

	/** Mana to conjure it, mana it drains a second while out, and the mana below which it fades by itself. */
	static final float SUMMON_COST = 10.0F, DRAIN_PER_SECOND = 0.5F, TIRED_AT = 5.0F;
	/** The most level and bond there are. */
	public static final int MAX_LEVEL = 20, MAX_BOND = 100;
	/** Bond for the second arm; for acting on its own; for idle remarks; for advice; for advice before the fight. */
	public static final int TWO_ARMS_BOND = 40, ACT_BOND = 20, IDLE_BOND = 40, ADVICE_BOND = 60, AHEAD_BOND = 80;
	/** One bond point for this many ticks out (three times as fast meditating with it out). */
	static final int BOND_TICKS = 1200, BOND_MEDITATING = 3;
	/** Bond for a kill while out; bond lost when the player burns out with it out. */
	static final int BOND_PER_KILL = 2, BOND_BURNOUT = 1;
	/** Experience for a kill while out, for a move cast; a level n needs {@code XP_PER_LEVEL} times n. */
	static final int XP_PER_KILL = 10, XP_PER_CAST = 1, XP_PER_LEVEL = 100;
	/** Each level adds this share to its moves' damage. */
	static final float LEVEL_DAMAGE = 0.05F;
	/** An autonomous act never leaves the player with less than this much mana. */
	static final float ACT_FLOOR = 15.0F;
	/** The chance an autonomous act is spoken. */
	static final float ACT_SAY = 0.7F;
	/** Chances the spirit comments on a blow taken, and (when low on health) on that. */
	static final float HURT_SAY = 0.2F, LOW_SAY = 0.5F;
	/** Health share (or less) that counts as low. */
	static final float LOW_HEALTH = 0.3F;
	/** How often the spirit thinks (ticks); how far it sees a foe, and how far with bond {@link #AHEAD_BOND} (blocks). */
	static final int THINK_EVERY = 10;
	static final double SEE = 16.0, SEE_AHEAD = 24.0;
	/** Idle remarks come this often, at least and at most (ticks). */
	static final int IDLE_MIN = 1200, IDLE_MAX = 2400;
	/** The same advice is given this seldom (ticks); a crowd is this many foes within {@code MANY_RANGE}; a big foe this tall. */
	static final int ADVICE_GAP = 600, MANY = 4;
	static final double MANY_RANGE = 8.0, BIG_HEIGHT = 2.5;
	/** Armour at which a foe is armoured. */
	static final int ARMOURED = 10;
	/** Releasing needs asking twice within this many ticks. */
	static final int RELEASE_CONFIRM = 600;

	/** The spirit kept by a player (or a folk): saved, kept through death, synced to everyone. */
	public static final AttachmentType<SpiritState> SPIRIT = AttachmentRegistry.create(JaymosKingdoms.id("spirit"),
		builder -> builder.persistent(SpiritState.CODEC).copyOnDeath().syncWith(SpiritState.STREAM_CODEC, AttachmentSyncPredicate.all()));
	/** The sickness before a spirit wakes. Synced to them (the flickers on their screen). */
	public static final AttachmentType<SpiritSickness.State> SICK = AttachmentRegistry.create(JaymosKingdoms.id("spirit_sick"),
		builder -> builder.persistent(SpiritSickness.State.CODEC).copyOnDeath().syncWith(SpiritSickness.State.STREAM_CODEC, AttachmentSyncPredicate.targetOnly()));
	/** Set once a spirit has been released: never another. */
	public static final AttachmentType<Boolean> RELEASED = AttachmentRegistry.create(JaymosKingdoms.id("spirit_released"),
		builder -> builder.persistent(Codec.BOOL).copyOnDeath());
	/** The last autonomous act: game time times 8, plus a {@code FLASH_} kind. Synced to them (the screen flash). */
	public static final AttachmentType<Long> FLASH = AttachmentRegistry.create(JaymosKingdoms.id("spirit_flash"),
		builder -> builder.syncWith(ByteBufCodecs.VAR_LONG, AttachmentSyncPredicate.targetOnly()));

	/** Player → server: the spirit key. {@code held} is the ticks it was held (for a CAST); {@code sneak} whether they sneak. */
	public record Key(int action, int held, boolean sneak) implements CustomPacketPayload {
		public static final Type<Key> TYPE = new Type<>(JaymosKingdoms.id("spirit_key"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Key> CODEC = CustomPacketPayload.codec(
			(p, buf) -> {
				buf.writeVarInt(p.action);
				buf.writeVarInt(p.held);
				buf.writeBoolean(p.sneak);
			}, buf -> new Key(buf.readVarInt(), buf.readVarInt(), buf.readBoolean()));

		@Override
		public Type<Key> type() {
			return TYPE;
		}
	}

	/** Every spirit there is, by kind. The last three are placeholders for the next patch. */
	private static final Spirit[] ALL = {
		new Grimsby(), new Varro(),
		new Spirit(AMBROSE, "ambrose", 0xFF9BE08F, 2.0F) {
		},
		new Spirit(THESSALY, "thessaly", 0xFFFF9ED8, 0.6F) {
		},
		new Spirit(QUIET, "quiet", 0xFFC8C8DC, 1.2F) {
		}};

	/** Ticks each player's spirit has been out, towards a bond point. */
	private static final Map<UUID, Integer> OUT_TICKS = new HashMap<>();
	/** Game time of each player's next idle remark. */
	private static final Map<UUID, Long> IDLE_AT = new HashMap<>();
	/** Game time each kind of advice may be given again, by player. */
	private static final Map<UUID, Map<String, Long>> ADVISED = new HashMap<>();
	/** Game time each player asked to release their spirit. */
	private static final Map<UUID, Long> RELEASING = new HashMap<>();

	private Spirits() {
	}

	public static void register() {
		// (their attachments, registered as the classes load)
		JaymosKingdoms.LOGGER.debug("spirit attachments: {} {} {} {}", SPIRIT, SICK, RELEASED, FLASH);
		PayloadTypeRegistry.serverboundPlay().register(Key.TYPE, Key.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Key.TYPE, (payload, context) -> key(context.player(), payload));
		Moves.register();
		SpiritVoice.register();
		SpiritSickness.register();
		HandMoves.register();
		Grimsby.register();
		Varro.register();
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			OUT_TICKS.clear();
			IDLE_AT.clear();
			ADVISED.clear();
			RELEASING.clear();
			for (Spirit spirit : ALL) {
				spirit.forgetAll();
			}
		});
		// a blow the spirit can turn aside (a parry, a ward)
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			if (entity instanceof ServerPlayer player) {
				SpiritState state = state(player);
				if (state != null && state.out() && Mana.canPay(player, 1.0F) && of(state.kind()).hurt(player, state, source, amount)) {
					return false;
				}
			}
			return true;
		});
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
			if (entity instanceof ServerPlayer player && player.isAlive()) {
				SpiritState state = state(player);
				if (state != null && state.out() && taken > 0.0F) {
					if (player.getHealth() <= player.getMaxHealth() * LOW_HEALTH && player.getRandom().nextFloat() < LOW_SAY) {
						SpiritVoice.say(player, "low");
					} else if (player.getRandom().nextFloat() < HURT_SAY) {
						SpiritVoice.say(player, "hurt");
					}
				}
			}
		});
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof ServerPlayer dead) {
				putAway(dead, null);
			}
			if (source.getEntity() instanceof ServerPlayer killer && killer != entity) {
				killed(killer, entity);
			}
		});
		// it never stays out across a login, a death or a change of game mode
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> putAway(handler.getPlayer(), null));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			ServerPlayer player = handler.getPlayer();
			putAway(player, null);
			UUID id = player.getUUID();
			OUT_TICKS.remove(id);
			IDLE_AT.remove(id);
			ADVISED.remove(id);
			RELEASING.remove(id);
			Moves.forget(id);
		});
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> putAway(newPlayer, null));
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % 20 == 0) {
				for (ServerPlayer player : server.getPlayerList().getPlayers()) {
					if (player.isSpectator()) {
						putAway(player, null);
					}
				}
			}
		});
		CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) -> dispatcher.register(Commands.literal("kingdom")
			.then(Commands.literal("spirit")
				.then(Commands.literal("release").executes(c -> release(c.getSource().getPlayerOrException())))
				.then(Commands.literal("give").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
					.then(Commands.argument("player", EntityArgument.player())
						.then(Commands.argument("kind", StringArgumentType.word())
							.suggests((c, b) -> SharedSuggestionProvider.suggest(names(), b))
							.executes(c -> give(c.getSource(), EntityArgument.getPlayer(c, "player"), StringArgumentType.getString(c, "kind"))))))
				.then(Commands.literal("set").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
					.then(Commands.argument("player", EntityArgument.player())
						.then(Commands.literal("bond").then(Commands.argument("n", IntegerArgumentType.integer(0, MAX_BOND))
							.executes(c -> set(c.getSource(), EntityArgument.getPlayer(c, "player"), true, IntegerArgumentType.getInteger(c, "n")))))
						.then(Commands.literal("level").then(Commands.argument("n", IntegerArgumentType.integer(1, MAX_LEVEL))
							.executes(c -> set(c.getSource(), EntityArgument.getPlayer(c, "player"), false, IntegerArgumentType.getInteger(c, "n"))))))))));
	}

	// ---- who and what -----------------------------------------------------------------------------------------------

	/** The spirit of this {@code kind} (an unknown kind is Grimsby). */
	public static Spirit of(int kind) {
		return ALL[kind >= 0 && kind < ALL.length ? kind : GRIMSBY];
	}

	/** {@code player}'s spirit state, or null if they've none. */
	public static @Nullable SpiritState state(net.minecraft.world.entity.Entity player) {
		return player.getAttached(SPIRIT);
	}

	/** Whether {@code player} has a spirit conjured now. */
	public static boolean out(net.minecraft.world.entity.Entity player) {
		SpiritState state = state(player);
		return state != null && state.out();
	}

	/** The moves {@code state}'s spirit can cast: the shared hands, then its own, those unlocked by its level. */
	public static List<Move> moves(SpiritState state) {
		List<Move> list = new ArrayList<>();
		for (Move move : HandMoves.SHARED) {
			if (move.level() <= state.level()) {
				list.add(move);
			}
		}
		for (Move move : of(state.kind()).moves()) {
			if (move.level() <= state.level()) {
				list.add(move);
			}
		}
		return list;
	}

	/** The selected move of {@code state} (the index wraps), or null if it knows none. */
	public static @Nullable Move selected(SpiritState state) {
		List<Move> list = moves(state);
		return list.isEmpty() ? null : list.get(Math.floorMod(state.move(), list.size()));
	}

	/** How much harder its moves hit at this level (1.0 at level 1, {@link #LEVEL_DAMAGE} more for each level after). */
	public static float damageScale(SpiritState state) {
		return 1.0F + LEVEL_DAMAGE * (Math.clamp(state.level(), 1, MAX_LEVEL) - 1);
	}

	/** The arms hold {@code pose} ({@link Poses}) from now, for the viewers. */
	public static void show(ServerPlayer player, int pose) {
		SpiritState state = state(player);
		if (state != null) {
			player.setAttached(SPIRIT, state.withShow(player.level().getGameTime(), pose));
		}
	}

	private static void set(ServerPlayer player, SpiritState state) {
		player.setAttached(SPIRIT, state);
	}

	// ---- the key ----------------------------------------------------------------------------------------------------

	private static void key(ServerPlayer player, Key key) {
		if (player.isSpectator() || !player.isAlive()) {
			return;
		}
		SpiritState state = state(player);
		if (state == null) {
			if (key.action() == SUMMON) {
				player.sendOverlayMessage(Component.translatable(SpiritSickness.sick(player) ? "message.jaymoskingdoms.spirit.asleep"
					: "message.jaymoskingdoms.spirit.none").withStyle(ChatFormatting.BLUE));
			}
			return;
		}
		switch (key.action()) {
			case SUMMON -> summon(player, state);
			case CAST -> {
				if (state.out()) {
					cast(player, state, Math.max(0, key.held()));
				}
			}
			case NEXT -> {
				if (state.out()) {
					next(player, state);
				}
			}
			case DISMISS -> {
				if (state.out()) {
					putAway(player, "dismiss");
				}
			}
			default -> {
			}
		}
	}

	private static void summon(ServerPlayer player, SpiritState state) {
		if (state.out()) {
			return;
		}
		if (!Mana.attuned(player) || Downed.isDowned(player)) {
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.mana.unattuned").withStyle(ChatFormatting.BLUE));
			return;
		}
		if (!Mana.pay(player, SUMMON_COST, Mana.WILL)) {
			return;
		}
		long now = player.level().getGameTime();
		set(player, state.withOut(true).withShow(now, Poses.REACH));
		OUT_TICKS.put(player.getUUID(), 0);
		IDLE_AT.put(player.getUUID(), now + IDLE_MIN + player.getRandom().nextInt(IDLE_MAX - IDLE_MIN + 1));
		ServerLevel level = (ServerLevel)player.level();
		Mana.sendSeen(level, Mana.MOTE, player.getX(), player.getY() + 1.2, player.getZ(), 24, 0.5, 0.7, 0.5, 0.05);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.0F, 0.9F);
		SpiritVoice.sayNow(player, state.kind(), "summon");
	}

	/** Puts the spirit away, saying {@code event} if it has one to say (null: quietly). */
	public static void putAway(ServerPlayer player, @Nullable String event) {
		SpiritState state = state(player);
		if (state == null || !state.out()) {
			return;
		}
		set(player, state.withOut(false).withShow(player.level().getGameTime(), Poses.IDLE));
		OUT_TICKS.remove(player.getUUID());
		IDLE_AT.remove(player.getUUID());
		if (event != null) {
			SpiritVoice.sayNow(player, state.kind(), event);
			if (player.level() instanceof ServerLevel level) {
				level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.8F, 0.6F);
			}
		}
	}

	private static void next(ServerPlayer player, SpiritState state) {
		List<Move> list = moves(state);
		if (list.isEmpty()) {
			return;
		}
		int to = Math.floorMod(state.move() + 1, list.size());
		set(player, state.withMove(to));
		player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.spirit.move", Component.translatable("spirit.jaymoskingdoms.move." + list.get(to).id()))
			.withStyle(s -> s.withColor(of(state.kind()).color() & 0xFFFFFF)));
	}

	/** Casts the selected move if it's ready and paid for; the mana is taken only if the cast did something. */
	private static void cast(ServerPlayer player, SpiritState state, int held) {
		Move move = selected(state);
		if (move == null) {
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.spirit.no_moves").withStyle(ChatFormatting.GRAY));
			return;
		}
		long now = player.level().getGameTime();
		if (!Moves.ready(player, move, now)) {
			return;
		}
		if (!Mana.canPay(player, move.cost())) {
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.spirit.no_mana").withStyle(ChatFormatting.BLUE));
			return;
		}
		if (!move.cast(player, state, move.charges() ? held : 0)) {
			return;
		}
		Mana.pay(player, move.cost(), move.intent());
		Moves.start(player, move, now);
		show(player, move.pose());
		addXp(player, XP_PER_CAST);
	}

	// ---- every tick -------------------------------------------------------------------------------------------------

	/** Every tick, for each player (after {@link Mana#tick}). */
	public static void tick(ServerPlayer player) {
		long now = player.level().getGameTime();
		if (now % 20L == 0L) {
			SpiritSickness.tick(player, now);
		}
		SpiritState state = state(player);
		if (state == null || !state.out()) {
			return;
		}
		if (!Mana.attuned(player) || Downed.isDowned(player)) {
			putAway(player, null);
			return;
		}
		if ((now + player.getId()) % 20L == 0L && !second(player, state)) {
			return;
		}
		if ((now + player.getId()) % THINK_EVERY == 0L) {
			think(player, now);
		}
	}

	/** Once a second while it's out: the drain, the bond, the tiredness. False if it faded. */
	private static boolean second(ServerPlayer player, SpiritState state) {
		if (!player.isCreative()) {
			Mana.drain(player, DRAIN_PER_SECOND);
			if (Mana.current(player) < TIRED_AT) {
				putAway(player, "tired");
				return false;
			}
		}
		UUID id = player.getUUID();
		int ticks = OUT_TICKS.merge(id, 20 * (PowersCompat.meditating(player) ? BOND_MEDITATING : 1), Integer::sum);
		if (ticks >= BOND_TICKS) {
			OUT_TICKS.put(id, ticks - BOND_TICKS);
			addBond(player, 1);
		}
		return true;
	}

	/** Every {@link #THINK_EVERY} ticks while it's out: its hooks, its advice, its remarks. */
	private static void think(ServerPlayer player, long now) {
		SpiritState state = state(player);
		if (state == null || !state.out() || !Mana.canPay(player, 1.0F)) {
			return;
		}
		Spirit spirit = of(state.kind());
		double look = state.bond() >= AHEAD_BOND ? SEE_AHEAD : SEE;
		List<Mob> foes = player.level().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(look), m -> m.isAlive() && m.getTarget() == player);
		Mob nearest = null, archer = null;
		for (Mob foe : foes) {
			double d = foe.distanceToSqr(player);
			if (nearest == null || d < nearest.distanceToSqr(player)) {
				nearest = foe;
			}
			boolean bow = foe.getMainHandItem().is(Items.BOW) || foe.getMainHandItem().is(Items.CROSSBOW);
			if (bow && d <= SEE * SEE && (archer == null || d < archer.distanceToSqr(player))) {
				archer = foe;
			}
		}
		LivingEntity close = nearest != null && nearest.distanceToSqr(player) <= SEE * SEE ? nearest : null;
		spirit.tick(player, state, close, now);
		if (archer != null) {
			spirit.aimedAt(player, state, archer);
		}
		if (nearest != null && state.bond() >= ADVICE_BOND) {
			advice(player, state, nearest);
		}
		Long idle = IDLE_AT.get(player.getUUID());
		if (idle != null && now >= idle && state.bond() >= IDLE_BOND) {
			if (nearest == null) {
				SpiritVoice.say(player, "idle");
			}
			IDLE_AT.put(player.getUUID(), now + IDLE_MIN + player.getRandom().nextInt(IDLE_MAX - IDLE_MIN + 1));
		}
	}

	// ---- advice -----------------------------------------------------------------------------------------------------

	/** What kind of foe {@code threat} is, for the advice key: caster, archer, creeper, armoured, many, big, foe. */
	public static String foeKey(ServerPlayer player, LivingEntity threat) {
		if (threat instanceof Folk folk && Sorcery.isCaster(folk.role()) || threat instanceof Witch || threat instanceof Evoker
			|| threat instanceof Illusioner || threat instanceof ServerPlayer p && Classes.is(p, Classes.PlayerClass.SORCERER)) {
			return "caster";
		}
		if (threat.getMainHandItem().is(Items.BOW) || threat.getMainHandItem().is(Items.CROSSBOW)) {
			return "archer";
		}
		if (threat instanceof Creeper) {
			return "creeper";
		}
		int crowd = player.level().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(MANY_RANGE), m -> m.isAlive() && m.getTarget() == player).size();
		if (crowd >= MANY) {
			return "many";
		}
		if (threat.getArmorValue() >= ARMOURED) {
			return "armoured";
		}
		if (threat.getBbHeight() >= BIG_HEIGHT) {
			return "big";
		}
		return "foe";
	}

	/** Battle advice about {@code threat}, once per kind of foe per {@link #ADVICE_GAP} ticks. Whether it was said. */
	public static boolean advice(ServerPlayer player, SpiritState state, LivingEntity threat) {
		if (state.bond() < ADVICE_BOND) {
			return false;
		}
		String foe = foeKey(player, threat);
		long now = player.level().getGameTime();
		Map<String, Long> mine = ADVISED.computeIfAbsent(player.getUUID(), k -> new HashMap<>());
		if (now < mine.getOrDefault(foe, 0L)) {
			return false;
		}
		if (!SpiritVoice.say(player, state.kind(), "advice." + foe)) {
			return false;
		}
		mine.put(foe, now + ADVICE_GAP);
		return true;
	}

	// ---- bond, level, kills ------------------------------------------------------------------------------------------

	/** {@code player}'s spirit gains {@code n} bond (clamped); each 20 crossed is said, and the second arm is remarked. */
	public static void addBond(ServerPlayer player, int n) {
		SpiritState state = state(player);
		if (state == null || n == 0) {
			return;
		}
		SpiritState to = state.withBond(state.bond() + n);
		if (to.bond() == state.bond()) {
			return;
		}
		set(player, to);
		if (n > 0 && to.bond() / 20 > state.bond() / 20) {
			SpiritVoice.sayNow(player, state.kind(), "bond");
		}
		if (state.bond() < TWO_ARMS_BOND && to.bond() >= TWO_ARMS_BOND) {
			player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.spirit.arms").withStyle(ChatFormatting.AQUA));
		}
	}

	/** {@code player}'s spirit gains {@code n} experience, and a level when it has enough. */
	public static void addXp(ServerPlayer player, int n) {
		SpiritState state = state(player);
		if (state == null || state.level() >= MAX_LEVEL || n <= 0) {
			return;
		}
		int xp = state.xp() + n, level = state.level();
		while (level < MAX_LEVEL && xp >= XP_PER_LEVEL * level) {
			xp -= XP_PER_LEVEL * level;
			level++;
		}
		if (level >= MAX_LEVEL) {
			xp = 0;
		}
		if (level == state.level()) {
			set(player, state.withXp(xp));
			return;
		}
		set(player, state.withLevel(level, xp));
		Spirit spirit = of(state.kind());
		player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.spirit.level", Component.translatable("spirit.jaymoskingdoms." + spirit.id() + ".name"), level)
			.withStyle(ChatFormatting.AQUA));
		SpiritVoice.sayNow(player, state.kind(), "level");
	}

	/** {@code player} killed {@code victim}: with the spirit out, a fight won together. */
	private static void killed(ServerPlayer player, LivingEntity victim) {
		SpiritState state = state(player);
		if (state == null || !state.out() || victim instanceof net.minecraft.world.entity.player.Player) {
			return;
		}
		if (!(victim instanceof Enemy) && !(victim instanceof Mob mob && mob.getTarget() == player)) {
			return;
		}
		addBond(player, BOND_PER_KILL);
		addXp(player, XP_PER_KILL);
		SpiritState now = state(player);
		if (now != null) {
			of(now.kind()).killed(player, now, victim);
		}
		SpiritVoice.say(player, "kill");
	}

	/** {@code player} has burnt out ({@link ManaSickness}): a spirit that is out loses a little bond. */
	public static void burntOut(ServerPlayer player) {
		if (out(player)) {
			addBond(player, -BOND_BURNOUT);
		}
	}

	// ---- autonomous acts --------------------------------------------------------------------------------------------

	/**
	 * What every autonomous act of a spirit goes through: the spirit of {@code kind} acts for {@code player} at a cost of
	 * {@code cost} mana. Only with the bond at {@link #ACT_BOND}, the spirit out, and mana to spare: it never leaves the
	 * player with less than {@link #ACT_FLOOR}. Pays, flashes their screen ({@code flashKind}, a {@code FLASH_} constant), and
	 * sometimes says its {@code act} line. False (and nothing done) if it can't; the caller keeps its own cooldown.
	 */
	public static boolean act(ServerPlayer player, int kind, int flashKind, float cost) {
		SpiritState state = state(player);
		if (state == null || !state.out() || state.kind() != kind || state.bond() < ACT_BOND) {
			return false;
		}
		if (!Mana.canPay(player, cost + ACT_FLOOR)) {
			return false;
		}
		int intent = flashKind == FLASH_PARRY || flashKind == FLASH_WARD ? Mana.WARD : flashKind == FLASH_WARN ? Mana.WILL : Mana.HARM;
		if (!Mana.pay(player, cost, intent)) {
			return false;
		}
		player.setAttached(FLASH, player.level().getGameTime() * 8L + flashKind);
		addXp(player, XP_PER_CAST);
		if (player.getRandom().nextFloat() < ACT_SAY) {
			SpiritVoice.say(player, kind, "act");
		}
		return true;
	}

	// ---- commands ---------------------------------------------------------------------------------------------------

	private static List<String> names() {
		List<String> names = new ArrayList<>();
		for (Spirit spirit : ALL) {
			names.add(spirit.id());
		}
		return names;
	}

	/** Asked twice within {@link #RELEASE_CONFIRM} ticks, the spirit is let go for good. */
	private static int release(ServerPlayer player) {
		SpiritState state = state(player);
		if (state == null) {
			player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.spirit.none").withStyle(ChatFormatting.BLUE));
			return 0;
		}
		long now = player.level().getGameTime();
		Long asked = RELEASING.get(player.getUUID());
		if (asked == null || now - asked > RELEASE_CONFIRM) {
			RELEASING.put(player.getUUID(), now);
			player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.spirit.release.ask").withStyle(ChatFormatting.RED));
			return 1;
		}
		RELEASING.remove(player.getUUID());
		SpiritVoice.sayNow(player, state.kind(), "release");
		putAway(player, null);
		player.removeAttached(SPIRIT);
		player.setAttached(RELEASED, true);
		Mana.drain(player, Mana.current(player));
		player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.spirit.release.done").withStyle(ChatFormatting.DARK_AQUA));
		ManaSickness.burnOut(player);
		return 1;
	}

	private static int give(CommandSourceStack source, ServerPlayer player, String name) {
		for (Spirit spirit : ALL) {
			if (spirit.id().equalsIgnoreCase(name)) {
				player.removeAttached(SICK);
				player.setAttached(RELEASED, false);
				player.setAttached(SPIRIT, SpiritState.fresh(spirit.kind(), SpiritSickness.WOKEN_BOND));
				source.sendSuccess(() -> Component.literal(player.getGameProfile().name() + " has " + spirit.id()), true);
				return 1;
			}
		}
		source.sendFailure(Component.literal("no such spirit: " + name));
		return 0;
	}

	private static int set(CommandSourceStack source, ServerPlayer player, boolean bond, int n) {
		SpiritState state = state(player);
		if (state == null) {
			source.sendFailure(Component.literal(player.getGameProfile().name() + " has no spirit"));
			return 0;
		}
		set(player, bond ? state.withBond(n) : state.withLevel(n, 0));
		source.sendSuccess(() -> Component.literal((bond ? "bond " : "level ") + n), true);
		return 1;
	}
}
