package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.jaymoskingdoms.equestrian.Breed;
import com.jaymoskingdoms.equestrian.Equestrian;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.46) Townsfolk carry things for you, after Village Life's {@code /vgive} (remade, not copied: that mod is All Rights
 * Reserved). {@code /kingdom deliver <player>} in a realm or village that doesn't hold you in ill repute: the nearest
 * villager free to go takes what's in your hand and takes it to that player, hands it over and says who it's from.
 * <p>(0.7.201) Across the land, after Courier Owls: sneak and use a sealed letter ({@link Letters}) on a grown villager and
 * they take it to whoever it's addressed to, wherever they are in the Overworld. Far off ({@link #RIDE_FROM}), they saddle
 * a horse and ride. Like a travelling elder ({@link Elders}), once nobody can see them they go on in a record
 * ({@link Errand}) along the {@link Roads King's Roads}, and come back into the world on the road when someone's near; so
 * you may meet a courier riding past, and the one with your letter rides up to you. If the one it's for isn't about, they
 * wait at home for them; after {@link #WAIT} they bring it back to whoever sent it. Then they go home. The news and MCA's
 * mail are brought this way too ({@link Post}: a herald, with no sender). Killed on the way, they drop what they carried.
 */
final class Couriers {
	/** How often couriers are thought about (ticks). */
	static final int EVERY = 10;
	/** How far from you a courier is found; how near is handing it over; arrived home this close. */
	static final double FIND = 24.0, HAND_OVER = 2.5, HOME = 6.0;
	/** The realm or village whose folk you ask: within this; you need at least this reputation with it. */
	static final double REALM_RANGE = 128.0;
	static final int LEAST_REP = 0;
	/** Farther than this, they ride. */
	static final double RIDE_FROM = 160.0;
	/** Their pace seen (a brisk walk; a horse's own speed when riding) and unseen (blocks a tick: walking, riding). */
	static final float PACE = 0.7F, RIDE_PACE = 1.0F;
	static final double WALK_SPEED = 0.12, RIDE_SPEED = 0.45, HORSE_SPEED = 0.3;
	/** Seen: put in the world when a player is this close to where they've got to, taken out when none is this close and the goal is farther than {@link #LOCAL}. */
	static final double APPEAR = 80.0, LEAVE = 112.0, LOCAL = 48.0;
	/** Farther than this, they walk a stage of this length at a time. */
	static final double STAGE = 32.0;
	/** No nearer for this long (ticks), seen, and they're helped over whatever is in their way: set this far on. */
	static final int STUCK_TICKS = 240;
	static final double HOP = 6.0;
	/** Nobody to take it for this long (ticks: two days) and they bring it back. */
	static final long WAIT = 48000L;
	/** The courier's thanks: MCA hearts with the sender for the errand. */
	static final int HEARTS = 1;
	/** Lines (lang {@code courier.jaymoskingdoms.<kind>.N}). */
	static final int LINES = 3;
	static final double SPEECH_RANGE = 12.0;
	/** On a courier's horse. */
	static final String HORSE_TAG = "jaymoskingdoms_courier_horse";
	/** A leg of an errand: to the one it's for, back to who sent it, home. */
	static final int TO = 0, BACK = 1, HOMEWARD = 2;

	/**
	 * An errand: {@code courier} carrying {@code stack} from {@code from} (none: a herald's news) to {@code to}, on leg
	 * {@code leg}, home at {@code home}, got to {@code at} (while out of the world); begun {@code since}; {@code rides} on
	 * horseback; {@code body} their whole saved self while they're out of the world (empty while they walk in it).
	 */
	record Errand(UUID courier, ItemStack stack, Optional<UUID> from, UUID to, int leg, BlockPos home, BlockPos at, long since, boolean rides,
		CompoundTag body) {
		static final Codec<Errand> CODEC = RecordCodecBuilder.create(i -> i.group(
			UUIDUtil.CODEC.fieldOf("courier").forGetter(Errand::courier),
			ItemStack.OPTIONAL_CODEC.optionalFieldOf("stack", ItemStack.EMPTY).forGetter(Errand::stack),
			UUIDUtil.CODEC.optionalFieldOf("from").forGetter(Errand::from),
			UUIDUtil.CODEC.fieldOf("to").forGetter(Errand::to),
			Codec.INT.fieldOf("leg").forGetter(Errand::leg),
			BlockPos.CODEC.fieldOf("home").forGetter(Errand::home),
			BlockPos.CODEC.fieldOf("at").forGetter(Errand::at),
			Codec.LONG.fieldOf("since").forGetter(Errand::since),
			Codec.BOOL.fieldOf("rides").forGetter(Errand::rides),
			CompoundTag.CODEC.fieldOf("body").forGetter(Errand::body)).apply(i, Errand::new));

		Errand at(BlockPos at) {
			return new Errand(this.courier, this.stack, this.from, this.to, this.leg, this.home, at, this.since, this.rides, this.body);
		}

		Errand body(CompoundTag body) {
			return new Errand(this.courier, this.stack, this.from, this.to, this.leg, this.home, this.at, this.since, this.rides, body);
		}

		Errand leg(int leg, ItemStack stack, long since) {
			return new Errand(this.courier, stack, this.from, this.to, leg, this.home, this.at, since, this.rides, this.body);
		}

		boolean out() {
			return !this.body.isEmpty();
		}

		/** Who they're making for on this leg, or null (home). */
		@Nullable UUID goal() {
			return this.leg == TO ? this.to : this.leg == BACK ? this.from.orElse(null) : null;
		}
	}

	/** On the Overworld: every courier's errand, by their id. Saved. */
	static final AttachmentType<Map<String, Errand>> ERRANDS = AttachmentRegistry.create(JaymosKingdoms.id("courier_errands"),
		builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, Errand.CODEC)));

	/** Couriers loaded just now. */
	private static final Set<Villager> LOADED = Collections.newSetFromMap(new IdentityHashMap<>());
	/** Out-of-date copies of couriers who are back in the record, and riderless courier horses: put away next tick. */
	private static final Set<Entity> STALE = Collections.newSetFromMap(new IdentityHashMap<>());
	/** Seen couriers: their best distance to their goal so far, and when it last got better. */
	private static final Map<UUID, double[]> PROGRESS = new HashMap<>();
	/** Couriers being taken out of the world by us just now. */
	private static final Set<UUID> TAKING = new HashSet<>();
	/** What a courier is carrying, known on this server just now (so {@link #carrying} is quick). */
	private static final Set<UUID> BUSY = new HashSet<>();

	private Couriers() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Couriers::tick);
		ServerEntityEvents.ENTITY_LOAD.register(Couriers::loaded);
		ServerEntityEvents.ENTITY_UNLOAD.register(Couriers::unloaded);
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			BUSY.clear();
			for (Errand e : errands(server).values()) {
				BUSY.add(e.courier());
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			LOADED.clear();
			STALE.clear();
			PROGRESS.clear();
			TAKING.clear();
			BUSY.clear();
		});
		UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
			if (hand != InteractionHand.MAIN_HAND || !player.isShiftKeyDown() || !(entity instanceof Villager v) || v.isBaby()
				|| !player.getItemInHand(hand).is(Letters.SEALED)) {
				return InteractionResult.PASS;
			}
			if (player instanceof ServerPlayer sp) {
				handed(sp, v);
			}
			return InteractionResult.SUCCESS;
		});
		CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) -> dispatcher.register(Commands.literal("kingdom")
			.then(Commands.literal("deliver").then(Commands.argument("player", EntityArgument.player()).executes(Couriers::deliver)))));
	}

	/** {@code v} is out on an errand. */
	static boolean carrying(Villager v) {
		return BUSY.contains(v.getUUID());
	}

	// ---- the record ---------------------------------------------------------------------------------------------------

	private static Map<String, Errand> errands(MinecraftServer server) {
		return Factions.level(server).getAttachedOrElse(ERRANDS, Map.of());
	}

	private static @Nullable Errand errand(MinecraftServer server, UUID courier) {
		return errands(server).get(courier.toString());
	}

	private static void put(MinecraftServer server, Errand e) {
		Map<String, Errand> all = new HashMap<>(errands(server));
		all.put(e.courier().toString(), e);
		Factions.level(server).setAttached(ERRANDS, Map.copyOf(all));
		BUSY.add(e.courier());
	}

	private static void drop(MinecraftServer server, UUID courier) {
		Map<String, Errand> all = new HashMap<>(errands(server));
		if (all.remove(courier.toString()) != null) {
			Factions.level(server).setAttached(ERRANDS, Map.copyOf(all));
		}
		BUSY.remove(courier);
		PROGRESS.remove(courier);
	}

	// ---- setting off ----------------------------------------------------------------------------------------------------

	/** A sealed letter given to {@code v}: they take it to whoever it's for, if they'll run errands for you. */
	private static void handed(ServerPlayer sender, Villager v) {
		ServerLevel level = sender.level();
		ItemStack hand = sender.getMainHandItem();
		Letters.Letter letter = Letters.of(hand);
		if (letter == null || level != Factions.level(level.getServer())) {
			return;
		}
		long t = Royals.timeOfDay(level);
		if (carrying(v) || v.hasAttached(Elders.JOURNEY) || !Routines.free(v, t)) {
			say(level, v, "busy");
			return;
		}
		Faction f = Factions.around(level.getServer(), level, v.blockPosition(), REALM_RANGE);
		if (f == null || Reputation.get(sender, f.id()) < LEAST_REP) {
			say(level, v, "refuse");
			return;
		}
		UUID to = Post.resolve(level.getServer(), letter.to());
		if (to == null || to.equals(sender.getUUID())) {
			say(level, v, "who");
			return;
		}
		ItemStack stack = hand.copyWithCount(1);
		hand.shrink(1);
		start(level, v, stack, sender.getUUID(), to);
		McaCompat.hearts(v, sender, HEARTS);
	}

	private static int deliver(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		ServerPlayer sender = c.getSource().getPlayerOrException();
		ServerPlayer to = EntityArgument.getPlayer(c, "player");
		ServerLevel level = sender.level();
		ItemStack hand = sender.getMainHandItem();
		if (hand.isEmpty()) {
			c.getSource().sendFailure(Component.translatable("message.jaymoskingdoms.courier_empty"));
			return 0;
		}
		if (to == sender || level != Factions.level(level.getServer())) {
			c.getSource().sendFailure(Component.translatable("message.jaymoskingdoms.courier_far"));
			return 0;
		}
		Faction f = Factions.around(level.getServer(), level, sender.blockPosition(), REALM_RANGE);
		if (f == null) {
			c.getSource().sendFailure(Component.translatable("message.jaymoskingdoms.courier_nobody"));
			return 0;
		}
		if (Reputation.get(sender, f.id()) < LEAST_REP) {
			c.getSource().sendFailure(Component.translatable("message.jaymoskingdoms.courier_refused", f.name()));
			return 0;
		}
		Villager courier = nearestFree(level, sender.position(), FIND);
		if (courier == null) {
			c.getSource().sendFailure(Component.translatable("message.jaymoskingdoms.courier_nobody"));
			return 0;
		}
		ItemStack stack = hand.copy();
		hand.setCount(0);
		start(level, courier, stack, sender.getUUID(), to.getUUID());
		McaCompat.hearts(courier, sender, HEARTS);
		return 1;
	}

	/** The nearest grown villager about {@code at} who's free to go, or null. */
	static @Nullable Villager nearestFree(ServerLevel level, Vec3 at, double range) {
		long t = Royals.timeOfDay(level);
		Villager best = null;
		double bestDist = Double.MAX_VALUE;
		for (Villager v : level.getEntitiesOfClass(Villager.class, new AABB(BlockPos.containing(at)).inflate(range),
			v -> v.isAlive() && !v.isBaby() && !carrying(v) && !v.hasAttached(Elders.JOURNEY) && !Downed.isDowned(v) && Routines.free(v, t))) {
			double d = v.distanceToSqr(at);
			if (d < bestDist) {
				best = v;
				bestDist = d;
			}
		}
		return best;
	}

	/** {@code courier} sets off with {@code stack} for {@code to} (from {@code from}, or a herald's news if null). */
	static void start(ServerLevel level, Villager courier, ItemStack stack, @Nullable UUID from, UUID to) {
		ServerPlayer goal = level.getServer().getPlayerList().getPlayer(to);
		boolean rides = goal == null || goal.level() != level || courier.distanceTo(goal) > RIDE_FROM;
		Errand e = new Errand(courier.getUUID(), stack, Optional.ofNullable(from), to, TO, courier.blockPosition(), courier.blockPosition(),
			level.getGameTime(), rides, new CompoundTag());
		put(level.getServer(), e);
		LOADED.add(courier);
		courier.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
		say(level, courier, rides && goal != null ? "ride" : "off");
	}

	// ---- in and out of the world ----------------------------------------------------------------------------------------

	private static void loaded(Entity entity, ServerLevel level) {
		if (entity instanceof AbstractHorse horse && horse.entityTags().contains(HORSE_TAG) && !horse.isVehicle()) {
			// (a courier's horse left behind: back to its stable)
			STALE.add(horse);
			return;
		}
		if (!(entity instanceof Villager v)) {
			return;
		}
		Errand e = errand(level.getServer(), v.getUUID());
		if (e == null) {
			return;
		}
		if (e.out()) {
			STALE.add(v);
			return;
		}
		LOADED.add(v);
	}

	private static void unloaded(Entity entity, ServerLevel level) {
		if (!(entity instanceof Villager v) || !LOADED.remove(v) || TAKING.contains(v.getUUID())) {
			return;
		}
		Errand e = errand(level.getServer(), v.getUUID());
		if (e == null || e.out()) {
			return;
		}
		if (v.getRemovalReason() == Entity.RemovalReason.UNLOADED_TO_CHUNK) {
			// (their chunk went with them: on in the record, and the chunk's copy is put away later)
			CompoundTag body = save(level, v);
			if (body != null) {
				put(level.getServer(), e.at(v.blockPosition()).body(body));
			}
		} else if (v.getRemovalReason() == Entity.RemovalReason.KILLED) {
			// (killed on the way: what they carried falls with them)
			if (!e.stack().isEmpty()) {
				v.spawnAtLocation(level, e.stack());
			}
			drop(level.getServer(), v.getUUID());
		}
	}

	private static @Nullable CompoundTag save(ServerLevel level, Villager courier) {
		TagValueOutput out = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
		return courier.saveAsPassenger(out) ? out.buildResult() : null;
	}

	/** Takes {@code courier} (and their horse, back to its stable) out of the world into their errand's record. */
	private static void takeOut(ServerLevel level, Villager courier, Errand e) {
		Entity horse = courier.getVehicle();
		courier.stopRiding();
		if (horse != null && horse.entityTags().contains(HORSE_TAG)) {
			horse.discard();
		}
		CompoundTag body = save(level, courier);
		if (body == null) {
			return;
		}
		put(level.getServer(), e.at(courier.blockPosition()).body(body));
		TAKING.add(courier.getUUID());
		LOADED.remove(courier);
		PROGRESS.remove(courier.getUUID());
		courier.discard();
		TAKING.remove(courier.getUUID());
	}

	/** Puts the courier on {@code e} back in the world on the ground at {@code at} (on a horse, if they ride); them, or null. */
	private static @Nullable Villager putBack(ServerLevel level, Errand e, BlockPos at) {
		level.getChunk(at.getX() >> 4, at.getZ() >> 4);
		BlockPos ground = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at);
		Villager courier;
		if (level.getEntity(e.courier()) instanceof Villager v && !v.isRemoved()) {
			STALE.remove(v);
			v.teleportTo(ground.getX() + 0.5, ground.getY(), ground.getZ() + 0.5);
			courier = v;
		} else {
			Entity loaded = EntityType.loadEntityRecursive(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), e.body()), level,
				EntitySpawnReason.LOAD, c -> {
					c.snapTo(ground.getX() + 0.5, ground.getY(), ground.getZ() + 0.5, c.getYRot(), c.getXRot());
					return c;
				});
			if (!(loaded instanceof Villager v)) {
				return null;
			}
			put(level.getServer(), e.at(ground).body(new CompoundTag()));
			if (!level.addFreshEntity(v)) {
				put(level.getServer(), e);
				return null;
			}
			courier = v;
		}
		put(level.getServer(), e.at(ground).body(new CompoundTag()));
		LOADED.add(courier);
		return courier;
	}

	/** A saddled courser under {@code courier}. */
	private static void mount(ServerLevel level, Villager courier) {
		if (courier.isPassenger()) {
			return;
		}
		AbstractHorse horse = Equestrian.create(level, Breed.COURSER, courier.blockPosition(), EntitySpawnReason.MOB_SUMMONED);
		if (horse == null) {
			return;
		}
		horse.snapTo(courier.getX(), courier.getY(), courier.getZ(), courier.getYRot(), 0.0F);
		horse.setTamed(true);
		horse.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(HORSE_SPEED);
		horse.setItemSlot(EquipmentSlot.SADDLE, new ItemStack(Items.SADDLE));
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			horse.setDropChance(slot, 0.0F);
		}
		horse.addTag(HORSE_TAG);
		if (level.addFreshEntity(horse)) {
			courier.startRiding(horse);
		}
	}

	// ---- on the way ---------------------------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % EVERY != 0) {
			return;
		}
		ServerLevel level = Factions.level(server);
		for (Entity stale : new ArrayList<>(STALE)) {
			if (!stale.isRemoved()) {
				TAKING.add(stale.getUUID());
				stale.discard();
				TAKING.remove(stale.getUUID());
			}
		}
		STALE.clear();
		if (errands(server).isEmpty()) {
			return;
		}
		for (Villager v : new ArrayList<>(LOADED)) {
			if (v.isRemoved() || !v.isAlive()) {
				LOADED.remove(v);
				continue;
			}
			Errand e = errand(server, v.getUUID());
			if (e == null) {
				LOADED.remove(v);
				continue;
			}
			if (v.level() == level) {
				step(level, v, e);
			}
		}
		for (Errand e : List.copyOf(errands(server).values())) {
			if (e.out()) {
				travel(level, e);
			}
		}
	}

	/** Who the leg is for, if they're in the Overworld now. */
	private static @Nullable ServerPlayer goalPlayer(ServerLevel level, Errand e) {
		UUID goal = e.goal();
		ServerPlayer p = goal == null ? null : level.getServer().getPlayerList().getPlayer(goal);
		return p != null && p.level() == level && !p.isSpectator() && p.isAlive() ? p : null;
	}

	/** {@code e} changes leg if the one it's for hasn't been about for too long. */
	private static Errand overdue(ServerLevel level, Errand e) {
		long now = level.getGameTime();
		if (e.leg() == TO && now - e.since() > WAIT) {
			if (e.from().isPresent()) {
				return put(level, e.leg(BACK, e.stack(), now));
			}
			// (a herald's news: it waits in the post for them instead)
			Post.later(e.to(), e.stack());
			return put(level, e.leg(HOMEWARD, ItemStack.EMPTY, now));
		}
		return e;
	}

	private static Errand put(ServerLevel level, Errand e) {
		put(level.getServer(), e);
		return e;
	}

	/** A courier in the world: on toward the one it's for (or home), handing over, home, or out of the world if nobody's about. */
	private static void step(ServerLevel level, Villager courier, Errand e) {
		e = overdue(level, e);
		ServerPlayer goal = goalPlayer(level, e);
		Vec3 to = goal != null ? goal.position() : Vec3.atBottomCenterOf(e.home());
		double d = Math.hypot(courier.getX() - to.x, courier.getZ() - to.z);
		if (goal != null && courier.distanceTo(goal) <= HAND_OVER + (courier.isPassenger() ? 1.0 : 0.0)) {
			handOver(level, courier, e, goal);
			return;
		}
		if (goal == null && d <= HOME) {
			if (e.leg() == HOMEWARD) {
				home(level, courier, e);
			}
			// (otherwise: home, waiting for whoever it's for to be about)
			return;
		}
		if (d > LOCAL && !near(level, courier.position(), LEAVE)) {
			takeOut(level, courier, e);
			return;
		}
		if (Downed.isDowned(courier) || courier.isSleeping()) {
			return;
		}
		if (e.rides() && d > LOCAL && !courier.isPassenger() && !courier.isInWater()) {
			mount(level, courier);
		}
		long now = level.getGameTime();
		double[] best = PROGRESS.computeIfAbsent(courier.getUUID(), id -> new double[] {Double.MAX_VALUE, now});
		if (d < best[0] - 1.0) {
			best[0] = d;
			best[1] = now;
		}
		if (now - (long)best[1] > STUCK_TICKS) {
			// (stuck on something: helped over it)
			Vec3 here = courier.position();
			Vec3 hop = here.add(to.subtract(here).multiply(1.0, 0.0, 1.0).normalize().scale(HOP));
			BlockPos ground = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, BlockPos.containing(hop));
			Entity mover = courier.getVehicle() != null ? courier.getVehicle() : courier;
			mover.teleportTo(ground.getX() + 0.5, ground.getY(), ground.getZ() + 0.5);
			best[1] = now;
			return;
		}
		if (now % 20 == 0 || courier.getNavigation().isDone()) {
			walk(level, courier, goal, to, d);
		}
	}

	/** Toward {@code goal} (or {@code to}): straight to them when near, else a stage of the way, along a King's Road where one runs. */
	private static void walk(ServerLevel level, Villager courier, @Nullable ServerPlayer goal, Vec3 to, double d) {
		float pace = courier.isPassenger() ? RIDE_PACE : PACE;
		if (d <= STAGE) {
			if (goal != null) {
				BehaviorUtils.setWalkAndLookTargetMemories(courier, goal, pace, 1);
			} else {
				BehaviorUtils.setWalkAndLookTargetMemories(courier, Construction.standable(level, BlockPos.containing(to), 3), pace, 1);
			}
			return;
		}
		Vec3 here = courier.position();
		Vec3 ahead = Roads.ahead(level, here, BlockPos.containing(to), STAGE);
		Vec3 next = ahead != null ? ahead : here.add(to.subtract(here).multiply(1.0, 0.0, 1.0).normalize().scale(STAGE));
		BlockPos at = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, BlockPos.containing(next));
		BehaviorUtils.setWalkAndLookTargetMemories(courier, Construction.standable(level, at, 3), pace, 1);
	}

	/** An errand in the record: on along the road toward the one it's for (or home), back in the world if someone's near. */
	private static void travel(ServerLevel level, Errand e) {
		if (level.getEntity(e.courier()) instanceof Villager copy && !copy.isRemoved() && e.out()) {
			STALE.add(copy);
		}
		e = overdue(level, e);
		ServerPlayer goal = goalPlayer(level, e);
		Vec3 at = Vec3.atBottomCenterOf(e.at());
		Vec3 to = goal != null ? goal.position() : Vec3.atBottomCenterOf(e.home());
		double d = Math.hypot(at.x - to.x, at.z - to.z);
		if (goal == null && d <= HOME) {
			if (e.leg() == HOMEWARD) {
				Villager courier = putBack(level, e, e.home());
				if (courier != null) {
					home(level, courier, e);
				}
			}
			return;
		}
		if (near(level, at, APPEAR) && level.isPositionEntityTicking(e.at())) {
			putBack(level, e, e.at());
			return;
		}
		double step = (e.rides() ? RIDE_SPEED : WALK_SPEED) * EVERY;
		Vec3 ahead = Roads.ahead(level, at, BlockPos.containing(to), step);
		Vec3 next = ahead != null ? ahead : at.add(to.subtract(at).multiply(1.0, 0.0, 1.0).scale(Math.min(1.0, step / Math.max(d, 1.0E-3))));
		put(level.getServer(), e.at(BlockPos.containing(next)));
	}

	/** {@code courier} hands {@code e}'s load to {@code to}: then home. */
	private static void handOver(ServerLevel level, Villager courier, Errand e, ServerPlayer to) {
		courier.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
		if (!e.stack().isEmpty()) {
			give(to, e.stack());
			level.playSound(null, to.getX(), to.getY(), to.getZ(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.4F, 1.2F);
		}
		if (e.leg() == BACK) {
			say(level, courier, "returned");
		} else if (e.from().isEmpty()) {
			say(level, courier, "news");
		} else {
			ServerPlayer sender = level.getServer().getPlayerList().getPlayer(e.from().get());
			Component name = sender != null ? Post.nameOf(sender) : Post.nameOf(level.getServer(), e.from().get());
			Speech.say(level, courier, Component.translatable("courier.jaymoskingdoms.from." + level.getRandom().nextInt(LINES), name), Speech.WHITE,
				SPEECH_RANGE);
		}
		Errand next = e.leg(HOMEWARD, ItemStack.EMPTY, level.getGameTime());
		put(level.getServer(), next);
		PROGRESS.remove(courier.getUUID());
		if (Math.hypot(courier.getX() - e.home().getX(), courier.getZ() - e.home().getZ()) <= HOME) {
			home(level, courier, next);
		}
	}

	/** Home: off the horse (it goes back to its stable when nobody's looking), the errand done. */
	private static void home(ServerLevel level, Villager courier, Errand e) {
		Entity horse = courier.getVehicle();
		courier.stopRiding();
		if (horse != null && horse.entityTags().contains(HORSE_TAG)) {
			if (near(level, horse.position(), 24.0)) {
				STALE.add(horse);
			} else {
				horse.discard();
			}
		}
		if (!e.stack().isEmpty()) {
			// (nobody left to have it: they keep it about them)
			ItemStack left = McaCompat.stow(courier, e.stack());
			if (!left.isEmpty()) {
				courier.spawnAtLocation(level, left);
			}
		}
		courier.getNavigation().stop();
		drop(level.getServer(), courier.getUUID());
	}

	private static boolean near(ServerLevel level, Vec3 at, double range) {
		for (ServerPlayer p : level.players()) {
			if (!p.isSpectator() && Math.hypot(p.getX() - at.x, p.getZ() - at.z) < range) {
				return true;
			}
		}
		return false;
	}

	private static void give(ServerPlayer player, ItemStack stack) {
		if (!player.getInventory().add(stack) && !stack.isEmpty()) {
			player.drop(stack, false, net.minecraft.util.Prediction.SERVER_ONLY);
		}
	}

	private static void say(ServerLevel level, Villager v, String kind) {
		Speech.say(level, v, Component.translatable("courier.jaymoskingdoms." + kind + "." + level.getRandom().nextInt(LINES)), Speech.WHITE, SPEECH_RANGE);
	}
}
