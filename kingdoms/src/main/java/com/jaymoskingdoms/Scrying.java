package com.jaymoskingdoms;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * Scrying (fifth edition): watch someone far away in a bowl of water, using a lock of their hair ({@link Hair}). Cast beside a
 * cauldron of water with the lock in your pack. If their mind holds (an illusion save) the water only clouds. Otherwise the
 * Magic screen shows the bowl: a map-like view of the ground round them, drawn from the loaded land as it is now, with
 * the people and monsters about them as dots, refreshed each second for as long as the spell is held. They feel the watching
 * (a chill and a sound at the ear), nothing more. It needs their land to be loaded: a player anywhere, a villager where
 * someone is near enough to keep it loaded.
 */
public final class Scrying {
	/** The bowl must be this near (blocks); the view is this many columns across each way from the target; held this long (ticks). */
	static final double BOWL_REACH = 4.0;
	static final int RADIUS = 20, SIZE = RADIUS * 2 + 1, HOLD_TICKS = 20 * 30, REFRESH = 20;
	/** The most dots drawn. */
	static final int DOTS_MOST = 48;
	static final int COLOUR = 0x7FB5E8;

	/** Server to player: the view (colours row by row from the north-west, dots as {dx, dz, kind}) and the one watched. */
	public record View(Component who, int[] colours, int[] dots, boolean night) implements CustomPacketPayload {
		public static final Type<View> TYPE = new Type<>(JaymosKingdoms.id("scry_view"));
		public static final StreamCodec<RegistryFriendlyByteBuf, View> CODEC = CustomPacketPayload.codec((v, buf) -> {
			net.minecraft.network.chat.ComponentSerialization.STREAM_CODEC.encode(buf, v.who);
			buf.writeVarInt(v.colours.length);
			for (int c : v.colours) {
				buf.writeInt(c);
			}
			buf.writeVarInt(v.dots.length);
			for (int d : v.dots) {
				buf.writeVarInt(d + 64);
			}
			buf.writeBoolean(v.night);
		}, buf -> {
			Component who = net.minecraft.network.chat.ComponentSerialization.STREAM_CODEC.decode(buf);
			int[] colours = new int[Math.min(buf.readVarInt(), SIZE * SIZE)];
			for (int i = 0; i < colours.length; i++) {
				colours[i] = buf.readInt();
			}
			int[] dots = new int[Math.min(buf.readVarInt(), DOTS_MOST * 3)];
			for (int i = 0; i < dots.length; i++) {
				dots[i] = buf.readVarInt() - 64;
			}
			return new View(who, colours, dots, buf.readBoolean());
		});

		@Override
		public Type<View> type() {
			return TYPE;
		}
	}

	/** Server to player: the water goes still; close the bowl. */
	public record Close() implements CustomPacketPayload {
		public static final Type<Close> TYPE = new Type<>(JaymosKingdoms.id("scry_close"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Close> CODEC = StreamCodec.unit(new Close());

		@Override
		public Type<Close> type() {
			return TYPE;
		}
	}

	private record Watch(ServerLevel from, UUID target, long until) {
	}

	private static final Map<UUID, Watch> WATCHES = new HashMap<>();

	private Scrying() {
	}

	static void register() {
		PayloadTypeRegistry.clientboundPlay().register(View.TYPE, View.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(Close.TYPE, Close.CODEC);
		ServerTickEvents.END_SERVER_TICK.register(Scrying::tick);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> WATCHES.clear());
	}

	/** Whether {@code caster} has a bowl of water within reach (a cauldron with water in it). */
	private static boolean bowl(ServerLevel level, ServerPlayer caster) {
		BlockPos c = caster.blockPosition();
		int r = (int) BOWL_REACH;
		for (BlockPos p : BlockPos.betweenClosed(c.offset(-r, -2, -r), c.offset(r, 2, r))) {
			if (level.getBlockState(p).is(Blocks.WATER_CAULDRON)) {
				return true;
			}
		}
		return false;
	}

	/** Ends {@code caster}'s watching (the screen was closed, or the spell dropped). */
	static void stop(ServerPlayer caster) {
		if (WATCHES.remove(caster.getUUID()) != null) {
			Concentration.drop(caster);
			if (ServerPlayNetworking.canSend(caster, Close.TYPE)) {
				ServerPlayNetworking.send(caster, new Close());
			}
		}
	}

	static boolean cast(ServerLevel level, ServerPlayer caster) {
		ItemStack lock = Hair.carried(caster);
		UUID owner = Hair.ownerOf(lock);
		if (owner == null || !bowl(level, caster) || !SpellParts.canSpeak(caster)) {
			return false;
		}
		Entity target = Hair.find(level, owner);
		level.playSound(null, caster.getX(), caster.getY(), caster.getZ(), SoundEvents.BREWING_STAND_BREW, SoundSource.PLAYERS, 0.8F, 0.6F);
		if (!(target instanceof LivingEntity who) || !who.isAlive() || Saves.resist(who, Saves.Kind.ILLUSION)) {
			// the water clouds over
			level.sendParticles(caster, Fx.miasma(0x9AA6B4, 1.2F), true, true, caster.getX(), caster.getY(1.1), caster.getZ(), 6, 0.3, 0.1, 0.3, 0.0);
			if (target instanceof ServerPlayer felt) {
				chill(felt);
			}
			lock.shrink(1);
			return true;
		}
		lock.shrink(1);
		WATCHES.put(caster.getUUID(), new Watch(level, owner, level.getGameTime() + HOLD_TICKS));
		Concentration.hold(caster, "scrying", HOLD_TICKS, () -> {
			WATCHES.remove(caster.getUUID());
			if (ServerPlayNetworking.canSend(caster, Close.TYPE)) {
				ServerPlayNetworking.send(caster, new Close());
			}
		});
		chill(who);
		send(caster, who);
		return true;
	}

	/** The watched feel it: a chill at the ear, no words. */
	private static void chill(LivingEntity who) {
		if (who.level() instanceof ServerLevel level) {
			level.playSound(null, who.getX(), who.getEyeY(), who.getZ(), SoundEvents.AMBIENT_CAVE.value(), SoundSource.AMBIENT, 0.4F, 0.5F);
			level.sendParticles(Fx.rune(COLOUR, 0.7F), who.getX(), who.getEyeY(), who.getZ(), 3, 0.3, 0.2, 0.3, 0.01);
		}
	}

	private static void tick(MinecraftServer server) {
		if (WATCHES.isEmpty() || server.getTickCount() % REFRESH != 0) {
			return;
		}
		for (Map.Entry<UUID, Watch> e : List.copyOf(WATCHES.entrySet())) {
			ServerPlayer caster = server.getPlayerList().getPlayer(e.getKey());
			Watch w = e.getValue();
			Entity t = Hair.find(w.from(), w.target());
			if (caster == null || !(t instanceof LivingEntity who) || !who.isAlive() || w.from().getGameTime() >= w.until() || !"scrying".equals(Concentration.holding(caster))) {
				WATCHES.remove(e.getKey());
				if (caster != null) {
					if (ServerPlayNetworking.canSend(caster, Close.TYPE)) {
						ServerPlayNetworking.send(caster, new Close());
					}
					if ("scrying".equals(Concentration.holding(caster))) {
						Concentration.drop(caster);
					}
				}
				continue;
			}
			send(caster, who);
		}
	}

	private static void send(ServerPlayer caster, LivingEntity who) {
		if (!ServerPlayNetworking.canSend(caster, View.TYPE) || !(who.level() instanceof ServerLevel level)) {
			return;
		}
		int[] colours = new int[SIZE * SIZE];
		int cx = who.getBlockX(), cz = who.getBlockZ();
		int[][] heights = new int[SIZE][SIZE];
		for (int dz = -RADIUS; dz <= RADIUS; dz++) {
			for (int dx = -RADIUS; dx <= RADIUS; dx++) {
				int x = cx + dx, z = cz + dz;
				LevelChunk chunk = level.getChunkSource().getChunkNow(x >> 4, z >> 4);
				int i = (dz + RADIUS) * SIZE + dx + RADIUS;
				if (chunk == null) {
					colours[i] = 0x101418;
					continue;
				}
				int y = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
				BlockPos pos = new BlockPos(x, y, z);
				BlockState state = level.getBlockState(pos);
				heights[dz + RADIUS][dx + RADIUS] = y;
				colours[i] = state.getMapColor(level, pos).col;
			}
		}
		// shade each column by the slope toward the north-west, as a map does
		for (int z = 0; z < SIZE; z++) {
			for (int x = 0; x < SIZE; x++) {
				int i = z * SIZE + x;
				int up = z > 0 ? heights[z - 1][x] : heights[z][x];
				int left = x > 0 ? heights[z][x - 1] : heights[z][x];
				int slope = heights[z][x] - (up + left) / 2;
				int shade = slope > 0 ? 24 : slope < 0 ? -28 : 0;
				int c = colours[i];
				int r = Math.clamp(((c >> 16) & 255) + shade, 0, 255), g = Math.clamp(((c >> 8) & 255) + shade, 0, 255), b = Math.clamp((c & 255) + shade, 0, 255);
				colours[i] = r << 16 | g << 8 | b;
			}
		}
		int[] dots = new int[DOTS_MOST * 3];
		int n = 0;
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, who.getBoundingBox().inflate(RADIUS), x -> x.isAlive() && !x.isSpectator())) {
			if (n >= DOTS_MOST) {
				break;
			}
			int kind = e == who ? 0 : e instanceof Player ? 1 : e instanceof Enemy ? 2 : 3;
			int dx = Math.clamp(e.getBlockX() - cx, -RADIUS, RADIUS), dz = Math.clamp(e.getBlockZ() - cz, -RADIUS, RADIUS);
			dots[n * 3] = dx;
			dots[n * 3 + 1] = dz;
			dots[n * 3 + 2] = kind;
			n++;
		}
		int[] used = java.util.Arrays.copyOf(dots, n * 3);
		ServerPlayNetworking.send(caster, new View(who.getDisplayName(), colours, used, level.isDarkOutside()));
	}

	/** The point a view's centre sits over, for others to read (the target's eyes). */
	static Vec3 eyes(LivingEntity who) {
		return who.getEyePosition();
	}
}
