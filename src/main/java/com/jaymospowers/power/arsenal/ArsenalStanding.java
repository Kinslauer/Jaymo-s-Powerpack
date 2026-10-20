package com.jaymospowers.power.arsenal;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.Powers;
import com.jaymospowers.power.web.WebNet;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.13.36) Arsenal's standing portals (the idea of Rick's Stuff's portal gun, built our own way). Sneak + G opens the
 * places menu (the client's {@code ArsenalPlacesScreen}); letting go over a place opens a pair of upright portals: one
 * {@link #NEAR_DISTANCE} blocks ahead of you, facing you, and one at the place, on safe ground near it. They stay open a
 * Minecraft day ({@link #LIFE}), saved with the world (an Overworld level attachment) so they survive restarts. Players,
 * mobs (not bosses), items and thrown things pass both ways keeping their momentum, with their fall cleared; each
 * entity waits {@link #PASS_COOLDOWN} ticks between passes. Up to {@link #MAX_PAIRS} pairs per owner (the oldest
 * closes), {@link #OPEN_COOLDOWN} ticks between openings. Not from or into the Arsenal pocket or the time chamber (see
 * {@link ArsenalPlaces#allowed}). The pairs are drawn by the Arsenal rift renderer: {@link ArsenalPocket#sync} adds
 * them to the portal list it sends.
 */
public final class ArsenalStanding {
	/** How long a pair stays open: a Minecraft day. */
	public static final long LIFE = 24000L;
	public static final int MAX_PAIRS = 3;
	/** Between two openings by the same player (30 s). */
	static final int OPEN_COOLDOWN = 600;
	/** One entity's wait between two passes. */
	static final int PASS_COOLDOWN = 20;
	/** How far ahead of you the near portal opens. */
	static final double NEAR_DISTANCE = 2.0;
	/** Half the width a portal catches things across, and the height it reaches from its base. */
	static final double CATCH_RADIUS = 0.7, CATCH_BOTTOM = 0.3, CATCH_HEIGHT = 2.3;
	/** Where things come out: this far in front of the portal (clear of its own catch zone). */
	static final double EXIT_DISTANCE = 1.4;
	/** How far (blocks) from a remembered place the far portal looks for safe ground, and how far up and down. */
	static final int GROUND_RADIUS = 3, GROUND_UP = 3, GROUND_DOWN = 6;
	/** A hum from each portal this often (ticks); motes drift off it this often. */
	static final int HUM_EVERY = 100, MOTE_EVERY = 4;

	/** One portal: where it stands (its base) and the way it faces. */
	public record End(ResourceKey<Level> dimension, Vec3 pos, float yaw) {
		public static final Codec<End> CODEC = RecordCodecBuilder.create(i -> i.group(
				Level.RESOURCE_KEY_CODEC.fieldOf("dimension").forGetter(End::dimension),
				Vec3.CODEC.fieldOf("pos").forGetter(End::pos),
				Codec.FLOAT.fieldOf("yaw").forGetter(End::yaw))
			.apply(i, End::new));

		/** The unit vector the portal faces. */
		Vec3 normal() {
			double rad = Math.toRadians(yaw);
			return new Vec3(-Math.sin(rad), 0.0, Math.cos(rad));
		}
	}

	/** Two portals that lead to each other, who opened them, and when (Overworld game time). */
	public record Pair(UUID owner, long opened, long until, End a, End b) {
		public static final Codec<Pair> CODEC = RecordCodecBuilder.create(i -> i.group(
				UUIDUtil.CODEC.fieldOf("owner").forGetter(Pair::owner),
				Codec.LONG.fieldOf("opened").forGetter(Pair::opened),
				Codec.LONG.fieldOf("until").forGetter(Pair::until),
				End.CODEC.fieldOf("a").forGetter(Pair::a),
				End.CODEC.fieldOf("b").forGetter(Pair::b))
			.apply(i, Pair::new));
	}

	/** Every open pair, on the Overworld; saved. */
	public static final AttachmentType<List<Pair>> PAIRS = AttachmentRegistry.create(JaymosPowers.id("arsenal_standing"),
		builder -> builder.persistent(Pair.CODEC.listOf()));
	/** How many pairs a player has open, synced to them so the menu knows whether to offer "Close my portals". */
	public static final AttachmentType<Integer> OPEN = AttachmentRegistry.create(JaymosPowers.id("arsenal_standing_open"),
		builder -> builder.syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.targetOnly()));

	/**
	 * Client → server: the menu's pick. {@code tab} 0 is Marks, 1 is Recent; {@code index} is the place in that list, or
	 * -1 for "Close my portals". (The server looks the place up itself.)
	 */
	public record Pick(int tab, int index) implements CustomPacketPayload {
		public static final Type<Pick> TYPE = new Type<>(JaymosPowers.id("arsenal_places_pick"));
		public static final StreamCodec<ByteBuf, Pick> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, Pick::tab,
			ByteBufCodecs.VAR_INT, Pick::index,
			Pick::new);

		@Override
		public Type<Pick> type() {
			return TYPE;
		}
	}

	/** When each player may open the next pair (server tick count). Server memory only. */
	private static final Map<UUID, Long> READY_AT = new HashMap<>();
	/** When each entity last went through a portal (server tick count). */
	private static final Map<UUID, Long> LAST_PASS = new HashMap<>();

	private ArsenalStanding() {
	}

	static void init() {
		PayloadTypeRegistry.serverboundPlay().register(Pick.TYPE, Pick.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Pick.TYPE, (payload, ctx) -> {
			ServerPlayer player = ctx.player();
			if (!Powers.has(player, ArsenalPower.INSTANCE) || !player.isAlive() || player.isSpectator() || !ArsenalPlaces.unlocked(player)) {
				return;
			}
			if (payload.index() < 0) {
				closeAll(player, true);
				return;
			}
			List<ArsenalPlaces.Place> list = payload.tab() == 0 ? ArsenalPlaces.marks(player) : ArsenalPlaces.recent(player);
			if (payload.index() < list.size()) {
				open(player, list.get(payload.index()));
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(ArsenalStanding::serverTick);
		ArsenalPlaces.init();
	}

	static List<Pair> pairs(MinecraftServer server) {
		return server.overworld().getAttachedOrElse(PAIRS, List.of());
	}

	private static void save(MinecraftServer server, List<Pair> pairs) {
		server.overworld().setAttached(PAIRS, List.copyOf(pairs));
	}

	private static int count(MinecraftServer server, UUID owner) {
		int n = 0;
		for (Pair pair : pairs(server)) {
			if (pair.owner().equals(owner)) {
				n++;
			}
		}
		return n;
	}

	private static void refreshOpen(ServerPlayer player) {
		int n = count(player.level().getServer(), player.getUUID());
		if (player.getAttachedOrElse(OPEN, 0) != n) {
			player.setAttached(OPEN, n);
		}
	}

	// ---------------------------------------------------------------- opening and closing

	/** Opens a pair: one ahead of the player, one at {@code place}. */
	static void open(ServerPlayer player, ArsenalPlaces.Place place) {
		MinecraftServer server = player.level().getServer();
		ServerLevel here = player.level();
		long tick = server.getTickCount();
		long ready = READY_AT.getOrDefault(player.getUUID(), 0L);
		if (tick < ready) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.arsenal.portal_cooldown", (ready - tick + 19) / 20)
				.withStyle(ChatFormatting.GRAY));
			return;
		}
		int colon = place.dimension().indexOf(':');
		ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION, Identifier.fromNamespaceAndPath(
			colon < 0 ? "minecraft" : place.dimension().substring(0, colon), place.dimension().substring(colon + 1)));
		ServerLevel there = server.getLevel(dimension);
		if (!ArsenalPlaces.allowed(here.dimension()) || there == null || !ArsenalPlaces.allowed(dimension)) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.arsenal.portal_nowhere").withStyle(ChatFormatting.GRAY));
			return;
		}
		End far = ground(there, new Vec3(place.x(), place.y(), place.z()), place.yaw());
		if (far == null) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.arsenal.portal_no_ground").withStyle(ChatFormatting.GRAY));
			return;
		}
		End near = near(player);
		long now = server.overworld().getGameTime();
		List<Pair> pairs = new ArrayList<>(pairs(server));
		pairs.add(new Pair(player.getUUID(), now, now + LIFE, near, far));
		// the oldest of this owner's pairs closes when there are too many
		int mine = 0;
		for (Pair pair : pairs) {
			if (pair.owner().equals(player.getUUID())) {
				mine++;
			}
		}
		for (int i = 0; i < pairs.size() && mine > MAX_PAIRS; i++) {
			if (pairs.get(i).owner().equals(player.getUUID())) {
				burst(server, pairs.remove(i));
				mine--;
				i--;
			}
		}
		save(server, pairs);
		READY_AT.put(player.getUUID(), tick + OPEN_COOLDOWN);
		refreshOpen(player);
		burst(server, pairs.get(pairs.size() - 1));
		player.sendOverlayMessage(Component.translatable("message.jaymospowers.arsenal.portal_opened", place.label()).withStyle(ChatFormatting.LIGHT_PURPLE));
	}

	/** "Close my portals" (and when the power is lost). */
	static void closeAll(ServerPlayer player, boolean say) {
		MinecraftServer server = player.level().getServer();
		List<Pair> kept = new ArrayList<>();
		boolean any = false;
		for (Pair pair : pairs(server)) {
			if (pair.owner().equals(player.getUUID())) {
				burst(server, pair);
				any = true;
			} else {
				kept.add(pair);
			}
		}
		if (any) {
			save(server, kept);
		}
		refreshOpen(player);
		if (any && say) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.arsenal.portal_closed").withStyle(ChatFormatting.GRAY));
		}
	}

	/** Both ends flash and a sound plays, as a pair opens or closes. */
	private static void burst(MinecraftServer server, Pair pair) {
		for (End end : new End[] {pair.a(), pair.b()}) {
			ServerLevel level = server.getLevel(end.dimension());
			if (level == null) {
				continue;
			}
			Vec3 p = end.pos();
			level.sendParticles(ParticleTypes.REVERSE_PORTAL, p.x, p.y + 1.1, p.z, 30, 0.3, 0.8, 0.3, 0.06);
			level.playSound(null, p.x, p.y, p.z, SoundEvents.PORTAL_TRIGGER, SoundSource.PLAYERS, 0.4F, 1.6F);
		}
	}

	/** Your end: in front of you at your feet, as far as {@link #NEAR_DISTANCE}, never inside a wall; it faces you. */
	private static End near(ServerPlayer player) {
		double rad = Math.toRadians(player.getYRot());
		Vec3 ahead = new Vec3(-Math.sin(rad), 0.0, Math.cos(rad));
		Vec3 feet = player.position();
		Vec3 spot = feet.add(ahead);
		for (double d = NEAR_DISTANCE; d > 1.0; d -= 0.5) {
			Vec3 at = feet.add(ahead.scale(d));
			if (player.level().noCollision(new AABB(at.x - 0.3, at.y + 0.05, at.z - 0.3, at.x + 0.3, at.y + 1.8, at.z + 0.3))) {
				spot = at;
				break;
			}
		}
		return new End(player.level().dimension(), spot, player.getYRot() + 180.0F);
	}

	/**
	 * The far end: safe ground near {@code want} (a floor, two clear blocks, no fluid), preferring a facing with room in
	 * front of it. Null when there's none within reach.
	 */
	private static @Nullable End ground(ServerLevel level, Vec3 want, float yaw) {
		BlockPos centre = BlockPos.containing(want);
		for (int r = 0; r <= GROUND_RADIUS; r++) {
			for (int dx = -r; dx <= r; dx++) {
				for (int dz = -r; dz <= r; dz++) {
					if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
						continue;
					}
					for (int dy = GROUND_UP; dy >= -GROUND_DOWN; dy--) {
						BlockPos feet = centre.offset(dx, dy, dz);
						if (!safe(level, feet)) {
							continue;
						}
						Vec3 base = Vec3.atBottomCenterOf(feet);
						// the way it was facing first, then the other three
						for (int turn = 0; turn < 4; turn++) {
							End end = new End(level.dimension(), base, yaw + turn * 90.0F);
							if (roomAhead(level, end)) {
								return end;
							}
						}
						return new End(level.dimension(), base, yaw);
					}
				}
			}
		}
		return null;
	}

	private static boolean safe(ServerLevel level, BlockPos feet) {
		BlockPos floor = feet.below();
		return level.getBlockState(feet).isAir() && level.getBlockState(feet.above()).isAir() && level.getFluidState(feet).isEmpty()
			&& level.getFluidState(feet.above()).isEmpty() && level.getBlockState(floor).isFaceSturdy(level, floor, Direction.UP)
			&& level.getFluidState(floor).isEmpty();
	}

	/** Whether someone fits where things come out of {@code end}. */
	private static boolean roomAhead(ServerLevel level, End end) {
		Vec3 at = end.pos().add(end.normal().scale(EXIT_DISTANCE));
		return level.noCollision(new AABB(at.x - 0.3, at.y + 0.05, at.z - 0.3, at.x + 0.3, at.y + 1.8, at.z + 0.3));
	}

	// ---------------------------------------------------------------- standing

	/** Per player: their open count is kept in step (it's lost on relogging), and Arsenal's own ticks are driven from here. */
	static void tick(ServerPlayer player) {
		ArsenalPlaces.tick(player);
		if (player.tickCount % 20 == 0) {
			refreshOpen(player);
		}
	}

	private static void serverTick(MinecraftServer server) {
		List<Pair> pairs = pairs(server);
		if (pairs.isEmpty()) {
			return;
		}
		long now = server.overworld().getGameTime();
		long tick = server.getTickCount();
		boolean expired = false;
		for (Pair pair : pairs) {
			if (now >= pair.until()) {
				expired = true;
				break;
			}
		}
		if (expired) {
			List<Pair> kept = new ArrayList<>();
			for (Pair pair : pairs) {
				if (now >= pair.until()) {
					burst(server, pair);
				} else {
					kept.add(pair);
				}
			}
			save(server, kept);
			pairs = kept;
		}
		for (Pair pair : pairs) {
			stand(server, pair.a(), pair.b(), tick);
			stand(server, pair.b(), pair.a(), tick);
		}
		if (tick % 200 == 0) {
			LAST_PASS.values().removeIf(at -> tick - at > 200);
		}
	}

	/** One portal for one tick: its motes and hum, and whatever is standing in it goes to {@code other}. */
	private static void stand(MinecraftServer server, End end, End other, long tick) {
		ServerLevel level = server.getLevel(end.dimension());
		ServerLevel to = server.getLevel(other.dimension());
		if (level == null || to == null || level.players().isEmpty()) {
			return;
		}
		Vec3 p = end.pos();
		if (tick % MOTE_EVERY == 0) {
			level.sendParticles(ParticleTypes.PORTAL, p.x, p.y + 1.1, p.z, 3, 0.3, 0.7, 0.3, 0.2);
		}
		if (tick % HUM_EVERY == 0) {
			level.playSound(null, p.x, p.y + 1.1, p.z, SoundEvents.PORTAL_AMBIENT, SoundSource.BLOCKS, 0.3F, 1.5F);
		}
		AABB box = new AABB(p.x - CATCH_RADIUS, p.y - CATCH_BOTTOM, p.z - CATCH_RADIUS, p.x + CATCH_RADIUS, p.y + CATCH_HEIGHT, p.z + CATCH_RADIUS);
		for (Entity entity : level.getEntitiesOfClass(Entity.class, box)) {
			if (passable(entity) && tick - LAST_PASS.getOrDefault(entity.getUUID(), -1000L) > PASS_COOLDOWN) {
				LAST_PASS.put(entity.getUUID(), tick);
				pass(entity, end, other, to);
			}
		}
	}

	/** Players, mobs (not bosses), items and thrown things; nothing riding or ridden, nothing in spectator. */
	private static boolean passable(Entity entity) {
		if (!entity.isAlive() || entity.isPassenger() || entity.isSpectator()) {
			return false;
		}
		if (entity instanceof ServerPlayer) {
			return true;
		}
		if (!entity.getPassengers().isEmpty() || entity instanceof ArmorStand || entity instanceof EnderDragon || entity instanceof WitherBoss) {
			return false;
		}
		return entity instanceof LivingEntity || entity instanceof ItemEntity || entity instanceof Projectile;
	}

	/** Through {@code from} and out of {@code other}: turned to its facing, momentum kept, the fall forgotten. */
	private static void pass(Entity entity, End from, End other, ServerLevel to) {
		Vec3 exit = other.pos().add(other.normal().scale(EXIT_DISTANCE));
		// in at the front means moving against its facing; out at the front means moving along the other's
		double turn = other.yaw() - from.yaw() + 180.0;
		double rad = Math.toRadians(turn);
		Vec3 v = entity.getDeltaMovement();
		Vec3 turned = new Vec3(v.x * Math.cos(rad) - v.z * Math.sin(rad), v.y, v.z * Math.cos(rad) + v.x * Math.sin(rad));
		float yaw = entity.getYRot() + (float)turn;
		ServerLevel level = (ServerLevel)entity.level();
		level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.PLAYER_TELEPORT, SoundSource.PLAYERS, 0.5F, 1.4F);
		if (entity instanceof ServerPlayer player && player.isPassenger()) {
			player.stopRiding();
		}
		entity.setDeltaMovement(turned);
		entity.resetFallDistance();
		entity.teleportTo(to, exit.x, exit.y, exit.z, Set.of(), yaw, entity.getXRot(), entity instanceof ServerPlayer && level != to);
		to.playSound(null, exit.x, exit.y, exit.z, SoundEvents.PLAYER_TELEPORT, SoundSource.PLAYERS, 0.5F, 1.2F);
		if (entity instanceof ServerPlayer player) {
			player.resetFallDistance();
			player.syncVelocity = true;
			ServerPlayNetworking.send(player, new WebNet.Nudge(turned));
		}
	}

	/** The pairs' portals within sight of {@code at} in {@code dimension}, for the rift renderer's list. */
	static void portalsNear(MinecraftServer server, ResourceKey<Level> dimension, Vec3 at, double range, List<ArsenalPocket.Portal> out) {
		for (Pair pair : pairs(server)) {
			for (End end : new End[] {pair.a(), pair.b()}) {
				if (end.dimension() == dimension && end.pos().distanceToSqr(at) < range * range) {
					out.add(new ArsenalPocket.Portal(end.pos().x, end.pos().y, end.pos().z, end.yaw(), pair.opened(), false, false));
				}
			}
		}
	}
}
