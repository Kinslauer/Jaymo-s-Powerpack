package com.jaymospowers.power.chrono.future;

import com.jaymospowers.JaymosPowers;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.phys.Vec3;

/**
 * Chronokinesis's leap into the future: a lifetime on, the world is a ruined city overrun by feral dead
 * ({@code jaymospowers:future}, built from Los Perrito City by Uospag; see {@link LosPerrito}).
 * <ul>
 * <li>Sneak+B (Fast Forward), for any Chrono user past the starter who has killed their echo for good (in the Nether,
 * since 0.10.111; {@link #ECHO_SLAIN}): you, and every player within
 * {@link #COMPANION_RADIUS} blocks, leap to the future. Sneak+B there brings you (and those near you) back to where
 * you left.</li>
 * <li>There's no other way home: only a Chrono's leap back, or dying (you wake at your own spawn as usual). Anyone
 * who goes forward without a Chrono stays until they die.</li>
 * <li>The first leap on a server fetches the city ({@link LosPerrito}); until it's in place, the leap only says the
 * future is forming ({@code /future status} shows how far along it is).</li>
 * <li>A Chrono in the future is stalked by up to {@link #ECHOES} of their echoes at once, each armed with a modern gun;
 * there they can be killed. Those close behind you when you leave come home with you. TaCZ's modern guns are only
 * found in the future (in no creative tab or JEI), but one you bring back is yours.</li>
 * </ul>
 * It is always night there (the {@code jaymospowers:future_night} timeline). Arrival square: {@link FutureStart}.
 * Ruins: {@link FutureRuins}. The city beyond the island: {@link FutureCities}. The dead: {@link FutureFerals}. Androids and mechs: {@link FutureMachines}. Guns: {@link FutureGuns}.
 */
public final class Future {
	public static final ResourceKey<Level> FUTURE = ResourceKey.create(Registries.DIMENSION, JaymosPowers.id("future"));
	/** Who comes along: every player this close to the one leaping. */
	static final double COMPANION_RADIUS = 4.0;
	/** Fast Forward's own recharge (a minute; it doesn't touch Time Bloom's). */
	static final int LEAP_COOLDOWN = 1200;
	/** In the future, a Chrono with room for another echo has one turn up this long after (30-90 s of survival play); checked this often. */
	static final int ECHO_DUE_MIN = 600, ECHO_DUE_MAX = 1800, ECHO_CHECK = 100;
	/** At most this many of one Chrono's echoes stalk them in the future at once (one, anywhere else). */
	static final int ECHOES = 4;

	/** Where to send someone when they come back. */
	public record Return(ResourceKey<Level> dimension, Vec3 pos, float yaw, float pitch) {
		public static final Codec<Return> CODEC = RecordCodecBuilder.create(i -> i.group(
			ResourceKey.codec(Registries.DIMENSION).fieldOf("dimension").forGetter(Return::dimension),
			Vec3.CODEC.fieldOf("pos").forGetter(Return::pos),
			Codec.FLOAT.fieldOf("yaw").forGetter(Return::yaw),
			Codec.FLOAT.fieldOf("pitch").forGetter(Return::pitch)
		).apply(i, Return::new));
	}

	/** Where the player left from. */
	public static final AttachmentType<Return> RETURN = AttachmentRegistry.create(JaymosPowers.id("future_return"),
		builder -> builder.persistent(Return.CODEC).copyOnDeath());
	/** Has this player ended their echo for good (killed it in the Nether or the future)? Fast Forward needs it. */
	public static final AttachmentType<Boolean> ECHO_SLAIN = AttachmentRegistry.create(JaymosPowers.id("future_echo_slain"),
		builder -> builder.persistent(Codec.BOOL).copyOnDeath());
	/** Has this player been told whose city this is? */
	public static final AttachmentType<Boolean> CREDITED = AttachmentRegistry.create(JaymosPowers.id("future_credited"),
		builder -> builder.persistent(Codec.BOOL).copyOnDeath());
	/** On an item frame in the future: its map art has been moved to the city's map ids (or it came empty). */
	public static final AttachmentType<Boolean> FRAME_FIXED = AttachmentRegistry.create(JaymosPowers.id("future_frame"),
		builder -> builder.persistent(Codec.BOOL));

	/**
	 * (0.17.8) A life story's "lost in time" newcomer waiting for the future to finish forming (Andy: "Always wait for the
	 * future if you get this start"): where they're held, unhurt and still, until it's in place and they're sent.
	 */
	public static final AttachmentType<net.minecraft.core.BlockPos> WAITING = AttachmentRegistry.create(JaymosPowers.id("future_waiting"),
		builder -> builder.persistent(net.minecraft.core.BlockPos.CODEC).copyOnDeath());
	/** How often a waiting newcomer is checked on and told how far along the future is (ticks). */
	static final int WAIT_CHECK = 20;

	private static final Map<UUID, Long> READY_AT = new HashMap<>();
	/** When each Chrono in the future (with room for one) has their next echo turn up. */
	private static final Map<UUID, Long> ECHO_DUE = new HashMap<>();

	private Future() {
	}

	public static void init() {
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			LosPerrito.load(server);
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			READY_AT.clear();
			ECHO_DUE.clear();
			FutureRuins.clear();
		});
		ServerChunkEvents.CHUNK_LOAD.register(FutureRuins::onLoad);
		ServerEntityEvents.ENTITY_LOAD.register(Future::onEntityLoad);
		ServerTickEvents.END_SERVER_TICK.register(FutureRuins::tick);
		ServerTickEvents.END_SERVER_TICK.register(FutureFerals::tick);
		ServerTickEvents.END_SERVER_TICK.register(Future::echoes);
		ServerTickEvents.END_SERVER_TICK.register(Future::waiting);
		FutureGuns.init();
		FutureMachines.init();
		FutureCities.init();
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> register(dispatcher));
	}

	// ---------------------------------------------------------------- the leap

	/** Sneak+B: into the future, or back from it, with everyone close by. */
	public static void leap(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		long now = server.getTickCount();
		long ready = READY_AT.getOrDefault(player.getUUID(), 0L);
		if (now < ready) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.future.recharging", (ready - now + 19) / 20)
				.withStyle(ChatFormatting.GOLD));
			return;
		}
		if (player.level().dimension() != FUTURE && !Boolean.TRUE.equals(player.getAttached(ECHO_SLAIN))) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.future.echo_first").withStyle(ChatFormatting.GOLD));
			return;
		}
		if (player.level().dimension() == FUTURE) {
			for (ServerPlayer other : companions(player)) {
				goBack(other);
			}
			READY_AT.put(player.getUUID(), now + LEAP_COOLDOWN);
			return;
		}
		if (!ready(player)) {
			return;
		}
		List<ServerPlayer> going = companions(player);
		for (int i = 0; i < going.size(); i++) {
			goForward(going.get(i), i, going.size());
		}
		READY_AT.put(player.getUUID(), now + LEAP_COOLDOWN);
	}

	/**
	 * (0.17.8) Jaymo's Kingdoms (a life story's "lost in time" start, by reflection): {@code player} wakes alone in the
	 * future's arrival square, with no leap home (a Chrono's leap back, or dying, as for anyone else who goes forward
	 * without one). If the future isn't in place yet, its download is started and they wait for it where they stand,
	 * unhurt and held still ({@link #WAITING}), and go the moment it's ready. Always true: they're sent, or on their way.
	 */
	public static boolean strand(ServerPlayer player) {
		if (sendable(player.level().getServer())) {
			goForward(player, 0, 1);
			return true;
		}
		player.setAttached(WAITING, player.blockPosition());
		player.setPermanentlyInvulnerable(true);
		return true;
	}

	/** Whether the future is in place to be sent to (if not and nothing's fetching it, the fetch is started). */
	private static boolean sendable(MinecraftServer server) {
		LosPerrito.Stage stage = LosPerrito.stage();
		if (stage == LosPerrito.Stage.READY && server.getLevel(FUTURE) != null) {
			return true;
		}
		if (stage == LosPerrito.Stage.IDLE || stage == LosPerrito.Stage.FAILED) {
			LosPerrito.begin(server);
		}
		return false;
	}

	/** Lost-in-time newcomers waiting on the future: held where they stand, told how far along it is, sent once it's there. */
	private static void waiting(MinecraftServer server) {
		if (server.getTickCount() % WAIT_CHECK != 0) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			net.minecraft.core.BlockPos hold = player.getAttached(WAITING);
			if (hold == null) {
				continue;
			}
			if (sendable(server)) {
				player.removeAttached(WAITING);
				player.setPermanentlyInvulnerable(false);
				goForward(player, 0, 1);
				continue;
			}
			if (player.blockPosition().distManhattan(hold) > 1) {
				player.teleportTo(player.level(), hold.getX() + 0.5, hold.getY(), hold.getZ() + 0.5, Set.of(), player.getYRot(), player.getXRot(), true);
			}
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.future.waiting", LosPerrito.percent()).withStyle(ChatFormatting.GOLD));
		}
	}

	/** From {@code ChronoEchoes.collapse}: the player's echo died for good. */
	public static void echoSlain(ServerPlayer player) {
		if (!Boolean.TRUE.equals(player.getAttached(ECHO_SLAIN))) {
			player.setAttached(ECHO_SLAIN, true);
			if (com.jaymospowers.core.Powers.has(player, com.jaymospowers.power.chrono.ChronoPower.INSTANCE)) {
				player.sendSystemMessage(Component.translatable("message.jaymospowers.future.unlocked").withStyle(ChatFormatting.GOLD));
			}
		}
	}

	/**
	 * Every few seconds: a Chrono in the future is stalked by their echoes, armed, up to {@link #ECHOES} at once; while
	 * there are fewer, another turns up {@link #ECHO_DUE_MIN}-{@link #ECHO_DUE_MAX} ticks after the last check found room.
	 */
	static void echoes(MinecraftServer server) {
		ServerLevel future = server.getLevel(FUTURE);
		if (future == null || server.getTickCount() % ECHO_CHECK != 0) {
			return;
		}
		long now = server.getTickCount();
		for (ServerPlayer player : future.players()) {
			if (!com.jaymospowers.core.Powers.has(player, com.jaymospowers.power.chrono.ChronoPower.INSTANCE)
				|| !player.isAlive() || player.isCreative() || player.isSpectator()
				|| com.jaymospowers.power.chrono.ChronoEchoes.loadedIn(player, future) >= ECHOES) {
				ECHO_DUE.remove(player.getUUID());
				continue;
			}
			Long due = ECHO_DUE.get(player.getUUID());
			if (due == null) {
				ECHO_DUE.put(player.getUUID(), now + ECHO_DUE_MIN + player.getRandom().nextInt(ECHO_DUE_MAX - ECHO_DUE_MIN + 1));
			} else if (now >= due && com.jaymospowers.power.chrono.ChronoEchoes.spawnFutureEcho(player)) {
				ECHO_DUE.remove(player.getUUID());
			}
		}
	}

	/** Is the city in place? If not, start it coming; all anyone is told is that the future is forming. */
	static boolean ready(ServerPlayer player) {
		switch (LosPerrito.stage()) {
			case READY -> {
				return player.level().getServer().getLevel(FUTURE) != null;
			}
			case IDLE, FAILED -> {
				LosPerrito.begin(player.level().getServer());
				forming(player);
			}
			case DOWNLOADING, UNPACKING -> forming(player);
		}
		return false;
	}

	private static void forming(ServerPlayer player) {
		player.sendOverlayMessage(Component.translatable("message.jaymospowers.future.forming").withStyle(ChatFormatting.GOLD));
	}

	private static List<ServerPlayer> companions(ServerPlayer player) {
		List<ServerPlayer> list = new ArrayList<>();
		list.add(player);
		for (ServerPlayer other : player.level().getEntitiesOfClass(ServerPlayer.class, player.getBoundingBox().inflate(COMPANION_RADIUS))) {
			if (other != player && other.isAlive() && !other.isSpectator()) {
				list.add(other);
			}
		}
		return list;
	}

	static void goForward(ServerPlayer player, int index, int of) {
		MinecraftServer server = player.level().getServer();
		ServerLevel future = server.getLevel(FUTURE);
		if (future == null) {
			return;
		}
		ServerLevel from = player.level();
		if (player.isPassenger()) {
			player.stopRiding();
		}
		player.setAttached(RETURN, new Return(from.dimension(), player.position(), player.getYRot(), player.getXRot()));
		if (future.players().isEmpty()) {
			FutureStart.restore(future);
		}
		burst(from, player.position());
		double angle = of <= 1 ? 0.0 : Math.PI * 2.0 * index / of;
		double radius = of <= 1 ? 0.0 : 1.5;
		int x = LosPerrito.SPAWN_X + (int) Math.round(Math.cos(angle) * radius);
		int z = LosPerrito.SPAWN_Z + (int) Math.round(Math.sin(angle) * radius);
		future.getChunk(x >> 4, z >> 4);
		int y = future.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
		player.teleportTo(future, x + 0.5, y, z + 0.5, Set.of(), 180.0F, 0.0F, true);
		burst(future, player.position());
		player.sendOverlayMessage(Component.translatable("message.jaymospowers.future.arrived").withStyle(ChatFormatting.GOLD));
		if (!Boolean.TRUE.equals(player.getAttached(CREDITED))) {
			player.setAttached(CREDITED, true);
			player.sendSystemMessage(Component.translatable("message.jaymospowers.future.credit")
				.withStyle(style -> style.withColor(ChatFormatting.GRAY).withClickEvent(new ClickEvent.OpenUrl(java.net.URI.create(LosPerrito.CREDIT_URL)))));
		}
	}

	/** (0.17.62, Andy's NOEA picks) The space pod's way in: is the city ready? If not it is started and the player told. */
	public static boolean podReady(ServerPlayer player) {
		return ready(player);
	}

	/** (0.17.62) The space pod's flight into the future: set down in the city, with no Echo needed. False if the city is not ready. */
	public static boolean podGo(ServerPlayer player) {
		if (!ready(player)) {
			return false;
		}
		goForward(player, 0, 1);
		return true;
	}

	/** (0.17.62) The space pod's flight home from the future. */
	public static void podBack(ServerPlayer player) {
		goBack(player);
	}

	/** Back to where they left from (or the world spawn if that's gone). */
	static void goBack(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		Return back = player.getAttached(RETURN);
		ServerLevel to = back != null ? server.getLevel(back.dimension()) : null;
		Vec3 at;
		float yaw = player.getYRot();
		float pitch = player.getXRot();
		if (to == null || to.dimension() == FUTURE) {
			to = server.overworld();
			at = Vec3.atBottomCenterOf(to.getRespawnData().pos());
		} else {
			at = back.pos();
			yaw = back.yaw();
			pitch = back.pitch();
		}
		if (player.isPassenger()) {
			player.stopRiding();
		}
		burst(player.level(), player.position());
		player.teleportTo(to, at.x, at.y, at.z, Set.of(), yaw, pitch, true);
		player.removeAttached(RETURN);
		burst(to, at);
		player.sendOverlayMessage(Component.translatable("message.jaymospowers.future.returned").withStyle(ChatFormatting.GOLD));
	}

	private static void burst(ServerLevel level, Vec3 at) {
		level.sendParticles(ParticleTypes.END_ROD, at.x, at.y + 1.0, at.z, 40, 0.4, 0.9, 0.4, 0.05);
		level.sendParticles(ParticleTypes.REVERSE_PORTAL, at.x, at.y + 1.0, at.z, 30, 0.3, 0.8, 0.3, 0.06);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.BELL_RESONATE, SoundSource.PLAYERS, 0.8F, 0.6F);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.PLAYER_TELEPORT, SoundSource.PLAYERS, 0.6F, 0.8F);
	}

	// ---------------------------------------------------------------- the city's map art

	/** The city's billboards and posters are maps; their ids are moved up past the world's own (see {@link LosPerrito}). */
	private static void onEntityLoad(Entity entity, ServerLevel level) {
		if (level.dimension() == FUTURE && unwanted(entity)) {
			// only the dead walk the future: no spiders, zombified villagers, camels, or husks but the hordes' own
			entity.discard();
			return;
		}
		if (level.dimension() != FUTURE || !(entity instanceof ItemFrame frame) || Boolean.TRUE.equals(frame.getAttached(FRAME_FIXED))) {
			return;
		}
		frame.setAttached(FRAME_FIXED, true);
		ItemStack held = frame.getItem();
		MapId id = held.get(DataComponents.MAP_ID);
		if (id != null && LosPerrito.mapOffset() > 0) {
			ItemStack moved = held.copy();
			moved.set(DataComponents.MAP_ID, new MapId(id.id() + LosPerrito.mapOffset()));
			frame.setItem(moved, false);
		}
	}

	/**
	 * What never turns up in the future: spiders and cave spiders (any mod's spider too, Stormie's Spiders' kinds among
	 * them), husks other than the hordes' own ({@link FutureFerals}), zombified villagers, and camels (the husks' camels
	 * included).
	 */
	static boolean unwanted(Entity entity) {
		String path = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).getPath();
		return entity instanceof net.minecraft.world.entity.monster.spider.Spider || path.contains("spider")
			|| entity instanceof net.minecraft.world.entity.monster.zombie.Husk && !entity.entityTags().contains(FutureFerals.TAG)
			|| entity instanceof net.minecraft.world.entity.monster.zombie.ZombieVillager
			|| entity instanceof net.minecraft.world.entity.animal.camel.Camel || path.contains("camel");
	}

	// ---------------------------------------------------------------- testing

	/**
	 * {@code /future go} (you and those near you, Chrono or not), {@code /future back}, {@code /future fetch} (start
	 * fetching the city now; works from the console), {@code /future status}, {@code /future start save [radius]} and
	 * {@code /future start clear} ({@link FutureStart}) (gamemasters only).
	 */
	static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("future")
			.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
			.then(Commands.literal("go").executes(c -> {
				ServerPlayer player = c.getSource().getPlayerOrException();
				if (player.level().dimension() == FUTURE || !ready(player)) {
					return 0;
				}
				List<ServerPlayer> going = companions(player);
				for (int i = 0; i < going.size(); i++) {
					goForward(going.get(i), i, going.size());
				}
				return going.size();
			}))
			.then(Commands.literal("back").executes(c -> {
				ServerPlayer player = c.getSource().getPlayerOrException();
				if (player.level().dimension() != FUTURE) {
					return 0;
				}
				goBack(player);
				return 1;
			}))
			.then(Commands.literal("fetch").executes(c -> {
				// from the console too: get the city in place before anyone leaps
				LosPerrito.begin(c.getSource().getServer());
				c.getSource().sendSuccess(() -> Component.literal("Los Perrito: " + LosPerrito.stage()), false);
				return 1;
			}))
			.then(Commands.literal("start")
				.then(Commands.literal("save")
					.executes(c -> saveStart(c.getSource(), FutureStart.RADIUS))
					.then(Commands.argument("radius", IntegerArgumentType.integer(FutureStart.MIN_RADIUS, FutureStart.MAX_RADIUS))
						.executes(c -> saveStart(c.getSource(), IntegerArgumentType.getInteger(c, "radius")))))
				.then(Commands.literal("clear").executes(c -> {
					ServerLevel future = c.getSource().getServer().getLevel(FUTURE);
					try {
						boolean had = future != null && FutureStart.clear(future);
						c.getSource().sendSuccess(() -> Component.literal(had ? "Forgot the saved arrival square." : "No arrival square was saved."), true);
						return had ? 1 : 0;
					} catch (java.io.IOException e) {
						c.getSource().sendFailure(Component.literal("Couldn't delete it: " + e.getMessage()));
						return 0;
					}
				})))
			.then(Commands.literal("status").executes(c -> {
				String failure = LosPerrito.failure();
				c.getSource().sendSuccess(() -> Component.literal("Los Perrito: " + LosPerrito.stage() + " " + LosPerrito.percent() + "%"
					+ (failure.isEmpty() ? "" : " (" + failure + ")") + ", map offset " + LosPerrito.mapOffset()), false);
				return 1;
			})));
	}

	private static int saveStart(CommandSourceStack source, int radius) {
		ServerLevel future = source.getServer().getLevel(FUTURE);
		if (future == null || LosPerrito.stage() != LosPerrito.Stage.READY) {
			source.sendFailure(Component.literal("The future isn't in place yet."));
			return 0;
		}
		try {
			int blocks = FutureStart.save(future, radius);
			source.sendSuccess(() -> Component.literal("Saved the arrival square (" + (radius * 2 + 1) + " blocks across, " + blocks
				+ " blocks). It's put back like this whenever someone leaps into an empty future."), true);
			return 1;
		} catch (java.io.IOException e) {
			source.sendFailure(Component.literal("Couldn't save it: " + e.getMessage()));
			return 0;
		}
	}
}
