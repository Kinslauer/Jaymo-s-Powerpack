package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.201) The pigeon post: what a {@link Pigeon} does once it's out of sight. A bird flying a letter is taken out of the
 * world into a record ({@link Flight}: its whole saved self, the letter, who it's for) and comes down near them once it's
 * had time to cross the distance ({@link #BIRD_SPEED}, at least {@link #MIN_FLIGHT}); if they aren't about, it waits for
 * them, and after {@link #GIVE_UP} brings the letter home. Home is the same: it comes down to its owner when they're about.
 */
final class PigeonPost {
	/** A flight's leg: out with the letter, home to its owner. */
	static final int OUT = 0, HOME = 1;
	/** Blocks a tick over the land (a pigeon's ~80 km/h); the shortest and longest flight (ticks). */
	static final double BIRD_SPEED = 1.1;
	static final long MIN_FLIGHT = 200L, MAX_FLIGHT = 6000L;
	/** Nobody to take it for this long (ticks: two days) and it comes home. */
	static final long GIVE_UP = 48000L;
	/** It comes down this far off (across, and above them), into a loaded spot. */
	static final double COME_IN = 22.0, COME_IN_RISE = 10.0;
	/** Lost on the way in: it tries again after this long (ticks). */
	static final long RETRY = 200L;
	static final int CHECK_EVERY = 20;

	/** A pigeon out of the world: {@code body} itself, {@code letter} what it carries (or empty), due to come down at {@code due}. */
	record Flight(UUID bird, UUID owner, UUID to, ItemStack letter, int leg, long due, long since, CompoundTag body) {
		static final Codec<Flight> CODEC = RecordCodecBuilder.create(i -> i.group(
			UUIDUtil.CODEC.fieldOf("bird").forGetter(Flight::bird),
			UUIDUtil.CODEC.fieldOf("owner").forGetter(Flight::owner),
			UUIDUtil.CODEC.fieldOf("to").forGetter(Flight::to),
			ItemStack.OPTIONAL_CODEC.optionalFieldOf("letter", ItemStack.EMPTY).forGetter(Flight::letter),
			Codec.INT.fieldOf("leg").forGetter(Flight::leg),
			Codec.LONG.fieldOf("due").forGetter(Flight::due),
			Codec.LONG.fieldOf("since").forGetter(Flight::since),
			CompoundTag.CODEC.fieldOf("body").forGetter(Flight::body)).apply(i, Flight::new));

		UUID goal() {
			return this.leg == OUT ? this.to : this.owner;
		}
	}

	/** On the Overworld: every pigeon out of the world, by its id. Saved. */
	static final AttachmentType<Map<String, Flight>> FLIGHTS = AttachmentRegistry.create(JaymosKingdoms.id("pigeon_flights"),
		builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, Flight.CODEC)));

	/** Copies of birds that are in the record (left in a chunk), put away next check. */
	private static final Set<Entity> STALE = Collections.newSetFromMap(new IdentityHashMap<>());

	private PigeonPost() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(PigeonPost::tick);
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			Flight f = entity instanceof Pigeon ? flights(level.getServer()).get(entity.getUUID().toString()) : null;
			if (f != null && !f.body().isEmpty()) {
				STALE.add(entity);
			}
		});
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
			// (its chunk went while it was on an errand: on in the record, a little later)
			if (entity instanceof Pigeon bird && bird.errand != Pigeon.NONE && !bird.wild && bird.getRemovalReason() == Entity.RemovalReason.UNLOADED_TO_CHUNK) {
				record(level, bird, level.getGameTime() + RETRY);
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> STALE.clear());
	}

	private static Map<String, Flight> flights(MinecraftServer server) {
		return Factions.level(server).getAttachedOrElse(FLIGHTS, Map.of());
	}

	private static void put(MinecraftServer server, Flight f) {
		Map<String, Flight> all = new HashMap<>(flights(server));
		all.put(f.bird().toString(), f);
		Factions.level(server).setAttached(FLIGHTS, Map.copyOf(all));
	}

	private static void drop(MinecraftServer server, UUID bird) {
		Map<String, Flight> all = new HashMap<>(flights(server));
		if (all.remove(bird.toString()) != null) {
			Factions.level(server).setAttached(FLIGHTS, Map.copyOf(all));
		}
	}

	// ---- sending ------------------------------------------------------------------------------------------------------

	/** {@code owner} gives their pigeon the sealed letter in {@code hand}: it's off, if it knows who that is. */
	static void send(Pigeon bird, ServerPlayer owner, InteractionHand hand) {
		ItemStack held = owner.getItemInHand(hand);
		Letters.Letter letter = Letters.of(held);
		if (letter == null) {
			return;
		}
		UUID to = Post.resolve(owner.level().getServer(), letter.to());
		if (to == null || to.equals(owner.getUUID())) {
			// (it cocks its head: nobody it knows)
			bird.playSound(SoundEvents.PARROT_FLY, 1.0F, 1.3F);
			owner.sendOverlayMessage(Component.translatable(to == null ? "message.jaymoskingdoms.pigeon.nobody" : "message.jaymoskingdoms.pigeon.self",
				letter.to()));
			return;
		}
		ItemStack stack = held.copyWithCount(1);
		held.shrink(1);
		bird.toward = to;
		bird.carried = stack;
		bird.homeward = false;
		bird.depart();
	}

	/** A wild pigeon with the news (or MCA's mail) for {@code to}, coming down to them now; whether one could. */
	static boolean wild(ServerPlayer to, ItemStack letter) {
		ServerLevel level = to.level();
		Pigeon bird = Pigeon.TYPE.create(level, EntitySpawnReason.EVENT);
		if (bird == null) {
			return false;
		}
		Vec3 at = comeIn(level, to);
		if (at == null) {
			return false;
		}
		bird.snapTo(at.x, at.y, at.z, level.getRandom().nextFloat() * 360.0F, 0.0F);
		bird.wild = true;
		bird.arrive(to.getUUID(), letter, false);
		return level.addFreshEntity(bird);
	}

	// ---- in and out of the world --------------------------------------------------------------------------------------

	/** Out of sight: a wild bird is gone; a tame one goes on in the record. */
	static void departed(ServerLevel level, Pigeon bird) {
		if (bird.wild) {
			bird.discard();
			return;
		}
		UUID goal = bird.homeward ? bird.getOwnerReference() == null ? null : bird.getOwnerReference().getUUID() : bird.toward;
		long flight = MIN_FLIGHT;
		ServerPlayer p = goal == null ? null : level.getServer().getPlayerList().getPlayer(goal);
		if (p != null && p.level() == level) {
			flight = Math.clamp((long)(Math.hypot(p.getX() - bird.getX(), p.getZ() - bird.getZ()) / BIRD_SPEED), MIN_FLIGHT, MAX_FLIGHT);
		}
		record(level, bird, level.getGameTime() + flight);
	}

	/** {@code bird} into the record, due to come down at {@code due}. */
	private static void record(ServerLevel level, Pigeon bird, long due) {
		UUID owner = bird.getOwnerReference() == null ? null : bird.getOwnerReference().getUUID();
		if (owner == null || bird.toward == null) {
			bird.discard();
			return;
		}
		ItemStack letter = bird.carried;
		boolean homeward = bird.homeward;
		UUID to = homeward ? toOf(level.getServer(), bird.getUUID(), bird.toward) : bird.toward;
		bird.rest();
		TagValueOutput out = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
		if (!bird.saveAsPassenger(out)) {
			// (it can't be kept: it drops the letter where it is)
			if (!letter.isEmpty()) {
				bird.spawnAtLocation(level, letter);
			}
			return;
		}
		Flight old = flights(level.getServer()).get(bird.getUUID().toString());
		long since = old != null && old.leg() == (homeward ? HOME : OUT) ? old.since() : level.getGameTime();
		put(level.getServer(), new Flight(bird.getUUID(), owner, to, letter, homeward ? HOME : OUT, due, since, out.buildResult()));
		if (!bird.isRemoved()) {
			bird.discard();
		}
	}

	/** The one a bird flying home had flown out to (kept for the record), else {@code fallback}. */
	private static UUID toOf(MinecraftServer server, UUID bird, UUID fallback) {
		Flight f = flights(server).get(bird.toString());
		return f != null ? f.to() : fallback;
	}

	/** Lost on the way in (they've gone, or it can't reach them): back in the record, to try again. */
	static void lost(ServerLevel level, Pigeon bird) {
		if (bird.wild) {
			// (a wild bird gives up: the news goes back to wait)
			if (!bird.carried.isEmpty() && bird.toward != null) {
				Post.later(bird.toward, bird.carried);
			}
			bird.carried = ItemStack.EMPTY;
			bird.depart();
			return;
		}
		record(level, bird, level.getGameTime() + RETRY);
	}

	/** In to {@code p}: the letter handed over; then home (a tame bird), away (a wild one), or a pet again (home already). */
	static void arrived(ServerLevel level, Pigeon bird, ServerPlayer p) {
		if (!bird.carried.isEmpty()) {
			ItemStack letter = bird.carried;
			bird.carried = ItemStack.EMPTY;
			if (!p.getInventory().add(letter) && !letter.isEmpty()) {
				p.drop(letter, false, net.minecraft.util.Prediction.SERVER_ONLY);
			}
			level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.5F, 1.3F);
			level.playSound(null, bird.getX(), bird.getY(), bird.getZ(), SoundEvents.PARROT_FLY, SoundSource.NEUTRAL, 0.8F, 1.1F);
			level.sendParticles(ParticleTypes.POOF, bird.getX(), bird.getY() + 0.3, bird.getZ(), 3, 0.15, 0.1, 0.15, 0.01);
		}
		if (bird.homeward) {
			drop(level.getServer(), bird.getUUID());
			bird.rest();
			return;
		}
		if (bird.wild) {
			bird.depart();
			return;
		}
		UUID owner = bird.getOwnerReference() == null ? null : bird.getOwnerReference().getUUID();
		if (owner == null) {
			bird.rest();
			return;
		}
		// (the record keeps who it went to, for its flight home)
		Flight old = flights(level.getServer()).get(bird.getUUID().toString());
		if (old != null) {
			put(level.getServer(), new Flight(old.bird(), old.owner(), p.getUUID(), ItemStack.EMPTY, HOME, old.due(), level.getGameTime(), old.body()));
		}
		bird.toward = owner;
		bird.homeward = true;
		bird.depart();
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % CHECK_EVERY != 0) {
			return;
		}
		for (Entity stale : new ArrayList<>(STALE)) {
			if (!stale.isRemoved()) {
				stale.discard();
			}
		}
		STALE.clear();
		Map<String, Flight> all = flights(server);
		if (all.isEmpty()) {
			return;
		}
		long now = Factions.level(server).getGameTime();
		for (Flight f : List.copyOf(all.values())) {
			if (f.body().isEmpty() || now < f.due()) {
				continue;
			}
			ServerPlayer p = server.getPlayerList().getPlayer(f.goal());
			if (p == null || p.isSpectator() || !p.isAlive()) {
				if (f.leg() == OUT && now - f.since() > GIVE_UP) {
					// (nobody came for it: home with the letter)
					put(server, new Flight(f.bird(), f.owner(), f.to(), f.letter(), HOME, now + MIN_FLIGHT, now, f.body()));
				}
				continue;
			}
			comeDown(p, f);
		}
	}

	/** {@code f}'s bird comes down near {@code p}, flying in to them. */
	private static void comeDown(ServerPlayer p, Flight f) {
		ServerLevel level = p.level();
		Vec3 at = comeIn(level, p);
		if (at == null) {
			return;
		}
		for (ServerLevel l : level.getServer().getAllLevels()) {
			if (l.getEntity(f.bird()) instanceof Pigeon copy && !copy.isRemoved()) {
				STALE.remove(copy);
				copy.discard();
			}
		}
		Entity loaded = EntityType.loadEntityRecursive(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), f.body()), level,
			EntitySpawnReason.LOAD, e -> {
				e.snapTo(at.x, at.y, at.z, e.getYRot(), e.getXRot());
				return e;
			});
		if (!(loaded instanceof Pigeon bird)) {
			// (the bird can't be had back: the letter isn't lost with it)
			drop(level.getServer(), f.bird());
			if (!f.letter().isEmpty()) {
				Post.later(f.goal(), f.letter());
			}
			return;
		}
		bird.arrive(p.getUUID(), f.letter(), f.leg() == HOME);
		// (the record says it's in the world before it is, so its loading isn't taken for a stale copy's)
		put(level.getServer(), new Flight(f.bird(), f.owner(), f.to(), ItemStack.EMPTY, f.leg(), f.due(), f.since(), new CompoundTag()));
		if (!level.addFreshEntity(bird)) {
			put(level.getServer(), f);
		}
	}

	/** A spot in the air off to one side of {@code p} where a bird can come in from, in loaded land, or null. */
	private static @Nullable Vec3 comeIn(ServerLevel level, ServerPlayer p) {
		for (int tries = 0; tries < 6; tries++) {
			float yaw = level.getRandom().nextFloat() * (float)(Math.PI * 2.0);
			double x = p.getX() + Math.cos(yaw) * COME_IN, z = p.getZ() + Math.sin(yaw) * COME_IN;
			BlockPos column = BlockPos.containing(x, p.getY(), z);
			if (!level.isPositionEntityTicking(column)) {
				continue;
			}
			int ground = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, column).getY();
			double y = Math.max(p.getY() + COME_IN_RISE, ground + 3.0);
			if (level.dimensionType().hasCeiling()) {
				// (under a roof of rock: it comes in at their height)
				y = p.getY() + 2.0;
			}
			if (!level.getBlockState(BlockPos.containing(x, y, z)).isAir() || !level.getBlockState(BlockPos.containing(x, y + 1.0, z)).isAir()) {
				continue;
			}
			return new Vec3(x, y, z);
		}
		return level.isPositionEntityTicking(p.blockPosition()) ? p.position().add(0.0, 3.0, 0.0) : null;
	}
}
