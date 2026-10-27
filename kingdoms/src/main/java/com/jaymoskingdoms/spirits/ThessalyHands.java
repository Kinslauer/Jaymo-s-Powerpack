package com.jaymoskingdoms.spirits;

import com.jaymoskingdoms.Mana;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.IntPredicate;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.DoubleBlockCombiner;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.241) The per-tick state of Thessaly the Learned's hands: what her long reach is drawing in through the walls, the rune
 * traps she has laid, the things she is juggling round her keeper (and the ones thrown, waiting to land), and the chests she has
 * already pointed out or seen opened. Andy: "manipulating items from way farther away than you could naturally". All of it
 * is kept per keeper, by server tick count, and let go the moment the keeper's spirit is no longer out (a trap is a glyph: it
 * waits, out or not). {@link #forget} drops a keeper who left; the server's end clears the lot.
 */
final class ThessalyHands {
	/** Long reach: how far it reaches (blocks), how long things take to arrive (ticks), the most it draws at once. */
	static final double DRAW_RANGE = 24.0;
	static final int DRAW_TICKS = 40, DRAW_MAX = 64;
	/** Things nearer than this (blocks) are left where they are. */
	static final double DRAW_NEAR = 1.5;
	/** Rune traps: how long one lasts (ticks), how many a keeper may have, how long it pins (ticks), the damage. */
	static final int TRAP_TICKS = 1200, TRAP_MAX = 3, TRAP_PIN = HandGrip.PIN_TICKS;
	static final float TRAP_DAMAGE = 4.0F;
	/** How often a trap looks for a foe, and how often it shows itself (ticks). */
	static final int TRAP_LOOK = 2, TRAP_SHOW = 10;
	/** Juggling: the most things, how far from the keeper they are taken (blocks), how long they are kept (ticks), the orbit's radius and speed. */
	static final int JUGGLE_MAX = 4, JUGGLE_TICKS = 200;
	static final double JUGGLE_RANGE = 8.0, ORBIT = 2.0;
	static final float ORBIT_SPEED = 0.15F;
	/** Nothing taller than this (blocks) is juggled. */
	static final double JUGGLE_HEIGHT = 1.0;
	/** The throw, as the grab's: speed and extra speed for each tick the key was held. Thrown things are watched this long (ticks) for their landing. */
	static final double THROW_SPEED = HandGrip.THROW_SPEED, THROW_CHARGE = HandGrip.THROW_CHARGE;
	static final int THROWN_TICKS = HandGrip.THROWN_TICKS, THROWN_MIN = HandGrip.THROWN_MIN;
	/** Chests pointed out: how many a keeper's memory holds before it is wiped. */
	static final int SEEN_MAX = 512;

	/** Something drawn in by the long reach: it arrives at {@code end}. */
	private record Draw(Entity thing, UUID owner, int end, boolean hadNoGravity) {
	}

	/** A glyph: in which dimension, where, until when. */
	private record Trap(ResourceKey<Level> dimension, BlockPos pos, int until) {
	}

	/** Something in the air round its keeper. */
	private record Juggled(Entity thing, boolean hadNoGravity) {
	}

	/** A keeper's juggled things and when they began (tick count). */
	private static final class Juggle {
		final List<Juggled> things = new ArrayList<>();
		final int since;

		Juggle(int since) {
			this.since = since;
		}
	}

	/** Something thrown, waiting to land. */
	private record Thrown(Entity thing, UUID owner, int since, boolean hadNoGravity) {
	}

	private static final List<Draw> DRAWS = new ArrayList<>();
	private static final Map<UUID, List<Trap>> TRAPS = new HashMap<>();
	private static final Map<UUID, Juggle> JUGGLES = new HashMap<>();
	private static final List<Thrown> THROWN = new ArrayList<>();
	/** Chests (positions) each keeper has been pointed at or has opened. */
	private static final Map<UUID, Set<Long>> SEEN = new HashMap<>();

	private ThessalyHands() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(ThessalyHands::tick);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> letGo());
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> clear());
		// a chest its keeper opens is one she needn't point out
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (!level.isClientSide() && player instanceof ServerPlayer keeper && level instanceof ServerLevel server) {
				SpiritState state = Spirits.state(keeper);
				if (state != null && state.kind() == Spirits.THESSALY && level.getBlockEntity(hit.getBlockPos()) instanceof Container) {
					markSeen(server, keeper.getUUID(), hit.getBlockPos());
				}
			}
			return InteractionResult.PASS;
		});
	}

	/** Before the world saves: nothing is left floating or without gravity. */
	static void letGo() {
		for (Draw d : DRAWS) {
			finish(d);
		}
		for (Juggle j : JUGGLES.values()) {
			releaseAll(j);
		}
		for (Thrown t : THROWN) {
			if (!t.thing.isRemoved()) {
				HandGrip.settle(t.thing, t.hadNoGravity);
			}
		}
		clear();
	}

	static void clear() {
		DRAWS.clear();
		TRAPS.clear();
		JUGGLES.clear();
		THROWN.clear();
		SEEN.clear();
	}

	/** A keeper left: their pulls, traps, juggled things and memory of chests go. */
	static void forget(UUID id) {
		for (Iterator<Draw> it = DRAWS.iterator(); it.hasNext();) {
			Draw d = it.next();
			if (d.owner.equals(id)) {
				finish(d);
				it.remove();
			}
		}
		TRAPS.remove(id);
		Juggle j = JUGGLES.remove(id);
		if (j != null) {
			releaseAll(j);
		}
		SEEN.remove(id);
	}

	private static int tick(Entity e) {
		MinecraftServer server = e.level().getServer();
		return server == null ? 0 : server.getTickCount();
	}

	private static @Nullable ServerPlayer keeper(MinecraftServer server, UUID id) {
		ServerPlayer p = server.getPlayerList().getPlayer(id);
		return p != null && p.isAlive() && !p.hasDisconnected() && Spirits.out(p) ? p : null;
	}

	private static boolean busy(Entity e) {
		for (Draw d : DRAWS) {
			if (d.thing == e) {
				return true;
			}
		}
		for (Juggle j : JUGGLES.values()) {
			for (Juggled held : j.things) {
				if (held.thing == e) {
					return true;
				}
			}
		}
		return false;
	}

	// ---- long reach -------------------------------------------------------------------------------------------------

	/** Starts every item and experience orb within {@link #DRAW_RANGE} of {@code player} on its way to them, through walls; how many. */
	static int draw(ServerPlayer player) {
		ServerLevel level = player.level();
		AABB box = player.getBoundingBox().inflate(DRAW_RANGE);
		List<Entity> found = new ArrayList<>(level.getEntitiesOfClass(Entity.class, box, e -> (e instanceof ItemEntity || e instanceof ExperienceOrb)
			&& e.isAlive() && e.distanceToSqr(player) <= DRAW_RANGE * DRAW_RANGE && e.distanceToSqr(player) > DRAW_NEAR * DRAW_NEAR && !busy(e)));
		found.sort(Comparator.comparingDouble(e -> e.distanceToSqr(player)));
		int now = tick(player);
		int n = 0;
		for (Entity e : found) {
			if (n >= DRAW_MAX) {
				break;
			}
			DRAWS.add(new Draw(e, player.getUUID(), now + DRAW_TICKS, e.isNoGravity()));
			HandGrip.floatUp(e);
			n++;
		}
		return n;
	}

	private static void finish(Draw d) {
		if (!d.thing.isRemoved()) {
			HandGrip.settle(d.thing, d.hadNoGravity);
			if (d.thing instanceof ItemEntity item) {
				item.setNoPickUpDelay();
			}
		}
	}

	private static void tickDraws(MinecraftServer server, int now) {
		for (Iterator<Draw> it = DRAWS.iterator(); it.hasNext();) {
			Draw d = it.next();
			ServerPlayer owner = keeper(server, d.owner);
			Entity thing = d.thing;
			if (owner == null || thing.isRemoved() || thing.level() != owner.level() || now >= d.end) {
				finish(d);
				it.remove();
				continue;
			}
			// (straight through whatever is in the way: it is moved, not pushed)
			Vec3 to = owner.position().add(0.0, 0.4, 0.0).subtract(thing.position());
			thing.setPos(thing.position().add(to.scale(1.0 / Math.max(1, d.end - now))));
			thing.setDeltaMovement(Vec3.ZERO);
			thing.syncVelocity = true;
			if (now % 4 == 0 && thing.level() instanceof ServerLevel level) {
				Mana.sendSeen(level, Mana.MOTE, thing.getX(), thing.getY() + 0.2, thing.getZ(), 1, 0.1, 0.1, 0.1, 0.0);
			}
		}
	}

	// ---- rune traps -------------------------------------------------------------------------------------------------

	/** Lays a glyph at {@code pos}; the oldest of the keeper's glyphs fades if that makes too many. False if one is already there. */
	static boolean plant(ServerPlayer player, ServerLevel level, BlockPos pos) {
		List<Trap> mine = TRAPS.computeIfAbsent(player.getUUID(), k -> new ArrayList<>());
		for (Trap t : mine) {
			if (t.dimension.equals(level.dimension()) && t.pos.equals(pos)) {
				return false;
			}
		}
		while (mine.size() >= TRAP_MAX) {
			mine.removeFirst();
		}
		mine.add(new Trap(level.dimension(), pos.immutable(), tick(player) + TRAP_TICKS));
		return true;
	}

	private static void tickTraps(MinecraftServer server, int now) {
		for (Iterator<Map.Entry<UUID, List<Trap>>> outer = TRAPS.entrySet().iterator(); outer.hasNext();) {
			Map.Entry<UUID, List<Trap>> entry = outer.next();
			ServerPlayer owner = server.getPlayerList().getPlayer(entry.getKey());
			for (Iterator<Trap> it = entry.getValue().iterator(); it.hasNext();) {
				Trap trap = it.next();
				ServerLevel level = server.getLevel(trap.dimension);
				if (level == null || now >= trap.until) {
					it.remove();
					continue;
				}
				if (!level.isLoaded(trap.pos)) {
					continue;
				}
				if (now % TRAP_SHOW == 0) {
					show(level, trap.pos);
				}
				if (owner == null || !owner.isAlive() || now % TRAP_LOOK != 0) {
					continue;
				}
				List<LivingEntity> stepped = level.getEntitiesOfClass(LivingEntity.class, new AABB(trap.pos), e -> HandMoves.foe(owner, e));
				if (!stepped.isEmpty()) {
					spring(level, owner, stepped.getFirst(), trap.pos);
					it.remove();
				}
			}
			if (entry.getValue().isEmpty()) {
				outer.remove();
			}
		}
	}

	/** A glyph's look: a ring of motes the attuned see, a faint ring of sparks for everyone. */
	private static void show(ServerLevel level, BlockPos pos) {
		double cx = pos.getX() + 0.5, cy = pos.getY() + 0.06, cz = pos.getZ() + 0.5;
		for (int i = 0; i < 6; i++) {
			double a = i * Math.PI / 3.0 + level.getGameTime() * 0.05;
			double x = cx + Math.cos(a) * 0.45, z = cz + Math.sin(a) * 0.45;
			level.sendParticles(ParticleTypes.END_ROD, x, cy, z, 1, 0.0, 0.0, 0.0, 0.0);
			Mana.sendSeen(level, Mana.MOTE, x, cy, z, 1, 0.02, 0.0, 0.02, 0.0);
		}
	}

	private static void spring(ServerLevel level, ServerPlayer owner, LivingEntity foe, BlockPos pos) {
		SpiritState state = Spirits.state(owner);
		float damage = TRAP_DAMAGE * (state == null ? 1.0F : Spirits.damageScale(state));
		foe.setInvulnerableTime(0);
		SpiritFoes.strike(level, owner, foe, damage);
		if (foe instanceof Mob mob && mob.isAlive()) {
			HandGrip.pin(owner, mob);
		} else if (foe.isAlive()) {
			foe.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, TRAP_PIN, HandGrip.PIN_SLOW, false, false));
		}
		level.sendParticles(ParticleTypes.END_ROD, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 18, 0.3, 0.4, 0.3, 0.08);
		Mana.sendSeen(level, Mana.MOTE, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 12, 0.3, 0.4, 0.3, 0.05);
		level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.0F, 1.2F);
		Spirits.show(owner, Poses.TRAP);
		owner.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.spirit.trap.sprung", foe.getDisplayName()).withStyle(ChatFormatting.LIGHT_PURPLE));
	}

	// ---- juggling ---------------------------------------------------------------------------------------------------

	static boolean juggling(ServerPlayer player) {
		Juggle j = JUGGLES.get(player.getUUID());
		return j != null && !j.things.isEmpty();
	}

	/**
	 * Lifts up to {@link #JUGGLE_MAX} nearby item entities and small mobs round {@code player}, nearest first, as many as
	 * {@code afford} (given a count, whether that many can be paid for) allows; how many.
	 */
	static int lift(ServerPlayer player, IntPredicate afford) {
		ServerLevel level = player.level();
		List<Entity> found = new ArrayList<>(level.getEntitiesOfClass(Entity.class, player.getBoundingBox().inflate(JUGGLE_RANGE), e -> e.isAlive() && !busy(e)
			&& e.distanceToSqr(player) <= JUGGLE_RANGE * JUGGLE_RANGE && juggleable(player, e)));
		found.sort(Comparator.comparingDouble(e -> e.distanceToSqr(player)));
		Juggle juggle = new Juggle(tick(player));
		for (Entity e : found) {
			if (juggle.things.size() >= JUGGLE_MAX || !afford.test(juggle.things.size() + 1)) {
				break;
			}
			e.stopRiding();
			juggle.things.add(new Juggled(e, e.isNoGravity()));
			HandGrip.floatUp(e);
			e.setDeltaMovement(Vec3.ZERO);
		}
		if (juggle.things.isEmpty()) {
			return 0;
		}
		JUGGLES.put(player.getUUID(), juggle);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.5F, 1.6F);
		return juggle.things.size();
	}

	private static boolean juggleable(ServerPlayer player, Entity e) {
		if (e instanceof ItemEntity) {
			return true;
		}
		return e instanceof Mob mob && mob.getBbHeight() < JUGGLE_HEIGHT && !HandMoves.ally(player, mob) && !HandGrip.pinned(mob) && mob != player.getVehicle();
	}

	/** Throws the next juggled thing along {@code player}'s look; false if nothing is in the air. */
	static boolean hurl(ServerPlayer player, int charge) {
		Juggle juggle = JUGGLES.get(player.getUUID());
		if (juggle == null) {
			return false;
		}
		while (!juggle.things.isEmpty()) {
			Juggled held = juggle.things.removeFirst();
			Entity thing = held.thing;
			if (thing.isRemoved() || !thing.isAlive()) {
				continue;
			}
			HandGrip.settle(thing, held.hadNoGravity);
			thing.setDeltaMovement(player.getLookAngle().scale(THROW_SPEED + charge * THROW_CHARGE));
			thing.syncVelocity = true;
			thing.resetFallDistance();
			if (thing instanceof ItemEntity item) {
				item.setPickUpDelay(20);
			}
			THROWN.add(new Thrown(thing, player.getUUID(), tick(player), held.hadNoGravity));
			player.level().playSound(null, thing.getX(), thing.getY(), thing.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.8F, 1.1F);
			if (juggle.things.isEmpty()) {
				JUGGLES.remove(player.getUUID());
			}
			return true;
		}
		JUGGLES.remove(player.getUUID());
		return false;
	}

	private static void releaseAll(Juggle juggle) {
		for (Juggled held : juggle.things) {
			if (!held.thing.isRemoved()) {
				HandGrip.settle(held.thing, held.hadNoGravity);
				held.thing.resetFallDistance();
			}
		}
		juggle.things.clear();
	}

	private static void startCooldown(ServerPlayer owner) {
		Move juggle = Moves.of("juggle");
		if (juggle != null) {
			Moves.start(owner, juggle, owner.level().getGameTime());
		}
	}

	private static void tickJuggles(MinecraftServer server, int now) {
		for (Iterator<Map.Entry<UUID, Juggle>> it = JUGGLES.entrySet().iterator(); it.hasNext();) {
			Map.Entry<UUID, Juggle> entry = it.next();
			Juggle juggle = entry.getValue();
			ServerPlayer owner = keeper(server, entry.getKey());
			juggle.things.removeIf(h -> h.thing.isRemoved() || !h.thing.isAlive() || owner != null && h.thing.level() != owner.level());
			if (owner == null || juggle.things.isEmpty() || now - juggle.since >= JUGGLE_TICKS) {
				releaseAll(juggle);
				it.remove();
				if (owner != null) {
					startCooldown(owner);
				}
				continue;
			}
			float spin = (now - juggle.since) * ORBIT_SPEED;
			int n = juggle.things.size();
			for (int i = 0; i < n; i++) {
				Entity thing = juggle.things.get(i).thing;
				double a = spin + i * (Math.PI * 2.0 / n);
				thing.setPos(owner.getX() + Math.cos(a) * ORBIT, owner.getY() + 1.0 + 0.25 * Math.sin(spin * 2.0 + i), owner.getZ() + Math.sin(a) * ORBIT);
				thing.setDeltaMovement(Vec3.ZERO);
				thing.resetFallDistance();
				thing.syncVelocity = true;
				if (thing instanceof Mob mob) {
					mob.setTarget(null);
					mob.getNavigation().stop();
				}
				if (now % 6 == 0 && owner.level() instanceof ServerLevel level) {
					Mana.sendSeen(level, Mana.MOTE, thing.getX(), thing.getY() + thing.getBbHeight() * 0.5, thing.getZ(), 2, 0.2, 0.2, 0.2, 0.01);
				}
			}
		}
	}

	private static void tickThrown(MinecraftServer server, int now) {
		for (Iterator<Thrown> it = THROWN.iterator(); it.hasNext();) {
			Thrown t = it.next();
			Entity thing = t.thing;
			if (thing.isRemoved() || now - t.since > THROWN_TICKS || !(thing.level() instanceof ServerLevel level)) {
				it.remove();
				continue;
			}
			if (now - t.since < THROWN_MIN || !(thing.onGround() || thing.horizontalCollision || thing.isInWater())) {
				continue;
			}
			it.remove();
			ServerPlayer owner = server.getPlayerList().getPlayer(t.owner);
			if (owner == null) {
				continue;
			}
			SpiritState state = Spirits.state(owner);
			float damage = HandGrip.THROW_DAMAGE * (state == null ? 1.0F : Spirits.damageScale(state));
			DamageSource source = level.damageSources().indirectMagic(owner, owner);
			for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, thing.getBoundingBox().inflate(HandGrip.LAND_RANGE),
				e -> e != thing && HandMoves.foe(owner, e))) {
				e.hurtServer(level, source, damage);
			}
			if (thing instanceof LivingEntity living && !(living instanceof Player)) {
				living.hurtServer(level, source, damage);
			}
			level.sendParticles(ParticleTypes.POOF, thing.getX(), thing.getY() + 0.2, thing.getZ(), 6, 0.3, 0.1, 0.3, 0.02);
		}
	}

	private static void tick(MinecraftServer server) {
		if (DRAWS.isEmpty() && TRAPS.isEmpty() && JUGGLES.isEmpty() && THROWN.isEmpty()) {
			return;
		}
		int now = server.getTickCount();
		tickDraws(server, now);
		tickTraps(server, now);
		tickJuggles(server, now);
		tickThrown(server, now);
	}

	// ---- chests -----------------------------------------------------------------------------------------------------

	/** Remembers the chest at {@code pos} (and the other half of a double chest) as one {@code keeper} has been shown or has opened. */
	static void markSeen(ServerLevel level, UUID keeper, BlockPos pos) {
		Set<Long> seen = SEEN.computeIfAbsent(keeper, k -> new HashSet<>());
		if (seen.size() >= SEEN_MAX) {
			seen.clear();
		}
		seen.add(pos.asLong());
		BlockState state = level.getBlockState(pos);
		if (state.getBlock() instanceof ChestBlock && ChestBlock.getBlockType(state) != DoubleBlockCombiner.BlockType.SINGLE) {
			seen.add(ChestBlock.getConnectedBlockPos(pos, state).asLong());
		}
	}

	/**
	 * The nearest chest or barrel within {@code range} of {@code player} that still has something in it (or loot to come) and
	 * that they have not opened or been shown, through any wall; null if none. Looks at the loaded chunks' block entities, not at
	 * every block.
	 */
	static @Nullable BlockPos unopenedChest(ServerPlayer player, double range) {
		ServerLevel level = player.level();
		Set<Long> seen = SEEN.getOrDefault(player.getUUID(), Set.of());
		BlockPos centre = player.blockPosition();
		int r = (int)Math.ceil(range);
		BlockPos best = null;
		double bestD = range * range;
		for (int cx = (centre.getX() - r) >> 4; cx <= (centre.getX() + r) >> 4; cx++) {
			for (int cz = (centre.getZ() - r) >> 4; cz <= (centre.getZ() + r) >> 4; cz++) {
				LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
				if (chunk == null) {
					continue;
				}
				for (BlockEntity be : chunk.getBlockEntities().values()) {
					if (!(be instanceof ChestBlockEntity) && !(be instanceof BarrelBlockEntity) || seen.contains(be.getBlockPos().asLong())) {
						continue;
					}
					boolean worth = be instanceof RandomizableContainer loot && loot.getLootTable() != null || be instanceof Container c && !c.isEmpty();
					double d = be.getBlockPos().distToCenterSqr(player.position());
					if (worth && d < bestD) {
						bestD = d;
						best = be.getBlockPos();
					}
				}
			}
		}
		return best;
	}
}
