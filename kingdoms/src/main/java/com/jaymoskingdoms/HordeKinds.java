package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.jspecify.annotations.Nullable;

/**
 * The kinds among a horde of the dead ({@link ZombieHordes}), Andy's picks from Zombie Horde (NeoAorus, MIT: its ideas,
 * our own code). Each is a zombie of the horde, picked when it rises ({@link #assign}):
 * <ul>
 * <li><b>Builders</b> ({@link #build}): carry a few blocks in the off hand (cobblestone, dirt or planks,
 * {@link #BUILD_MIN}-{@link #BUILD_MAX} by the danger), and when they can't find a way to where they're going they make
 * one: pillar up under themselves (a target above, or a wall in the way), lay a step ahead and climb it (a target
 * higher up and further off), or lay the floor ahead (a gap). Only what they carry, and what's left drops when they fall
 * (no free blocks); only with mob griefing on.</li>
 * <li><b>Runners</b> ({@link #run}): sprint while they can see who they're after, and leap gaps of one or two blocks
 * toward them. More of the horde as the danger climbs ({@link #RUNNER_SHARE}).</li>
 * <li><b>Brutes</b>: big, slow, tough to shove and to kill, and every blow on a door counts {@link #BRUTE_DOOR} times
 * ({@code DoorPounding}). Commoner than the grave-captains, who still lead.</li>
 * <li><b>Scouts</b> ({@link #scout}): quick and frail, they range ahead of the horde straight for the one it came for;
 * once they see them they howl (a sound, never text), which draws the rest of the horde there ({@link Senses#lure}) and
 * keeps it on the trail for {@link #HOWL_TRACK}, and then hang back out of reach, howling again now and then. Cornered,
 * they fight.</li>
 * </ul>
 * Every zombie of a horde also shows how it moves ({@link Act}, the client's {@code HordePoses}): arms reaching as it
 * walks (for that it carries {@link #MARCHING}), a shoulder into a door it pounds, a stagger when hit hard, a lunge at
 * the last step before it strikes ({@link #lunge}), and a scout's head thrown back to howl.
 */
public final class HordeKinds {
	/** Builders: from this danger, this chance at no danger plus the extra at full; two of them from {@link #BUILDER_TWO}. */
	static final float BUILDER_DANGER = 0.15F, BUILDER_CHANCE = 0.35F, BUILDER_CHANCE_DANGER = 0.4F, BUILDER_TWO = 0.5F;
	/** How many blocks a builder carries: at no danger, at full. */
	static final int BUILD_MIN = 6, BUILD_MAX = 16;
	/** What it carries. */
	private static final Item[] BUILD_BLOCKS = {Items.COBBLESTONE, Items.DIRT, Items.OAK_PLANKS};
	/** It lays a block no more often than this (ticks). */
	static final int BUILD_EVERY = 10;
	/** It builds up when where it's going is this much higher; it pillars rather than stairs when that's this near (flat). */
	static final double BUILD_RISE = 1.5, PILLAR_NEAR = 2.0;
	/** Runners: this share of the horde's zombies at no danger, plus the extra at full. */
	static final float RUNNER_SHARE = 0.1F, RUNNER_SHARE_DANGER = 0.3F;
	/** A running runner's extra speed (share of its own, on top of the sprint's). */
	static final double RUN_SPEED = 0.15;
	/** Leaping: gaps up to this wide (blocks), at most this often (ticks), this far from its prey at least; its spring. */
	static final int LEAP_GAP = 2, LEAP_EVERY = 20;
	static final double LEAP_FROM = 2.5, LEAP_UP = 0.42, LEAP_REACH = 7.0, LEAP_MIN = 0.2, LEAP_MAX = 0.5;
	/** Brutes: from this danger, this chance plus the extra at full; two from {@link #BRUTE_TWO}. Their size, health (shares), slowness, knockback resistance. */
	static final float BRUTE_DANGER = 0.25F, BRUTE_CHANCE = 0.3F, BRUTE_CHANCE_DANGER = 0.4F, BRUTE_TWO = 0.7F;
	static final double BRUTE_SCALE = 0.2, BRUTE_HEALTH = 1.0, BRUTE_SLOW = 0.25, BRUTE_KNOCKBACK = 0.5;
	/** A brute's every blow on a door counts as this many. */
	static final float BRUTE_DOOR = 3.0F;
	/** Scouts: from this danger, this chance plus the extra at full; two from {@link #SCOUT_TWO}. Their speed, frailty (health lost), size (shares). */
	static final float SCOUT_DANGER = 0.1F, SCOUT_CHANCE = 0.4F, SCOUT_CHANCE_DANGER = 0.3F, SCOUT_TWO = 0.6F;
	static final double SCOUT_SPEED = 0.4, SCOUT_FRAIL = 0.4, SCOUT_SCALE = -0.08;
	/** A scout ranges for the one its horde came for this far; sees them this near; keeps this far off them; fights when this near. */
	static final double SCOUT_RANGE = 160.0, SCOUT_SIGHT = 24.0, SCOUT_HOLD = 12.0, SCOUT_CORNERED = 3.0, SCOUT_BACK_SPEED = 1.1;
	/** Its howl: at most this often (ticks), this loud to the horde's senses (the march is 2, a fight 9), this far heard (16 blocks per), this pitch. */
	static final int HOWL_EVERY = 300;
	static final double HOWL_LOUDNESS = 10.0;
	static final float HOWL_VOLUME = 6.0F, HOWL_PITCH = 0.55F;
	/** After a howl the horde keeps after them this long (ticks), however far. */
	static final int HOWL_TRACK = 1200;
	/** A lunge: when it closes within this of its prey, with this push forward. */
	static final double LUNGE_RANGE = 2.6, LUNGE_PUSH = 0.12;
	/** A blow this hard, or this share of its health, staggers it (a brute: twice that). */
	static final float STAGGER_HIT = 6.0F, STAGGER_SHARE = 0.25F;

	public static final String BUILDER_TAG = "jaymoskingdoms.horde_builder", RUNNER_TAG = "jaymoskingdoms.horde_runner",
		BRUTE_TAG = "jaymoskingdoms.horde_brute", SCOUT_TAG = "jaymoskingdoms.horde_scout";

	private static final Identifier RUN_ID = JaymosKingdoms.id("horde_run"), BRUTE_SCALE_ID = JaymosKingdoms.id("horde_brute_scale"),
		BRUTE_HEALTH_ID = JaymosKingdoms.id("horde_brute_health"), BRUTE_SLOW_ID = JaymosKingdoms.id("horde_brute_slow"),
		BRUTE_KNOCKBACK_ID = JaymosKingdoms.id("horde_brute_knockback"), SCOUT_SPEED_ID = JaymosKingdoms.id("horde_scout_speed"),
		SCOUT_HEALTH_ID = JaymosKingdoms.id("horde_scout_health"), SCOUT_SCALE_ID = JaymosKingdoms.id("horde_scout_scale");

	/** On every member of a horde while it marches, for its client to draw it reaching as it walks. Not kept. */
	public static final AttachmentType<Integer> MARCHING = AttachmentRegistry.create(JaymosKingdoms.id("horde_marching"),
		builder -> builder.syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.all()));

	/** What a horde's zombie does, shown by its client ({@link Act}). */
	public static final int SHOULDER = 1, STAGGER = 2, LUNGE = 3, HOWL = 4;

	/** Server to client: {@code entity} does {@code act} now. */
	public record Act(int entity, int act) implements CustomPacketPayload {
		public static final Type<Act> TYPE = new Type<>(JaymosKingdoms.id("horde_act"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Act> CODEC = CustomPacketPayload.codec(
			(p, buf) -> {
				buf.writeVarInt(p.entity);
				buf.writeByte(p.act);
			},
			buf -> new Act(buf.readVarInt(), buf.readByte()));

		@Override
		public Type<Act> type() {
			return TYPE;
		}
	}

	/** Each member's distance to its prey last tick (for the lunge). */
	private static final Map<Mob, Double> CLOSING = new WeakHashMap<>();
	/** When each runner may next leap, each builder next lay a block, each scout next howl. */
	private static final Map<Mob, Long> NEXT_LEAP = new WeakHashMap<>(), NEXT_BUILD = new WeakHashMap<>(), HOWLED = new WeakHashMap<>();
	/** A pillaring builder: the block it jumped from (to lay under itself at the top of the jump), and when it jumped. */
	private record Pillar(BlockPos from, long at) {
	}

	private static final Map<Mob, Pillar> PILLAR = new WeakHashMap<>();
	/** A jump that hasn't topped out in this long (ticks) is given up. */
	static final int PILLAR_TICKS = 16;

	private HordeKinds() {
	}

	static void register() {
		PayloadTypeRegistry.clientboundPlay().register(Act.TYPE, Act.CODEC);
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
			if (!blocked && entity instanceof Zombie zombie && ZombieHordes.inHorde(zombie)) {
				float factor = zombie.entityTags().contains(BRUTE_TAG) ? 2.0F : 1.0F;
				if (taken >= STAGGER_HIT * factor || taken >= zombie.getMaxHealth() * STAGGER_SHARE * factor) {
					act(zombie, STAGGER);
				}
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			CLOSING.clear();
			NEXT_LEAP.clear();
			NEXT_BUILD.clear();
			HOWLED.clear();
			PILLAR.clear();
		});
	}

	/** Tells everyone who can see {@code mob} (one of a horde's zombies) that it does {@code act}. */
	static void act(Mob mob, int act) {
		if (!(mob instanceof Zombie)) {
			return;
		}
		Act packet = new Act(mob.getId(), act);
		for (ServerPlayer p : PlayerLookup.tracking(mob)) {
			if (ServerPlayNetworking.canSend(p, Act.TYPE)) {
				ServerPlayNetworking.send(p, packet);
			}
		}
	}

	/** {@code mob} marches with a horde now ({@code true}), or no longer (a runner stops running). */
	static void marching(Mob mob, boolean marching) {
		if (marching) {
			mob.setAttached(MARCHING, 1);
			return;
		}
		mob.removeAttached(MARCHING);
		if (mob.entityTags().contains(RUNNER_TAG) && mob.isSprinting()) {
			mob.setSprinting(false);
			AttributeInstance speed = mob.getAttribute(Attributes.MOVEMENT_SPEED);
			if (speed != null) {
				speed.removeModifier(RUN_ID);
			}
		}
	}

	static boolean scout(Mob mob) {
		return mob.entityTags().contains(SCOUT_TAG);
	}

	/** How many blows each of {@code mob}'s blows on a door counts as. */
	static float doorBlows(Mob mob) {
		return mob.entityTags().contains(BRUTE_TAG) ? BRUTE_DOOR : 1.0F;
	}

	// ---- who's who --------------------------------------------------------------------------------------------------------

	/** Picks a new horde of the dead's builders, brutes, scouts and runners among its zombies (not its captain or lobber). */
	static void assign(ZombieHordes.Horde horde, RandomSource random) {
		if (!horde.kind.dead()) {
			return;
		}
		List<Mob> pool = new ArrayList<>();
		for (Mob m : horde.members) {
			if (m instanceof Zombie && !m.entityTags().contains(ZombieHordes.CAPTAIN_TAG) && !m.entityTags().contains(ZombieHordes.LOBBER_TAG)) {
				pool.add(m);
			}
		}
		Collections.shuffle(pool, new java.util.Random(random.nextLong()));
		float danger = horde.danger;
		if (danger >= BRUTE_DANGER && random.nextFloat() < BRUTE_CHANCE + BRUTE_CHANCE_DANGER * danger) {
			for (int i = danger >= BRUTE_TWO ? 2 : 1; i > 0 && !pool.isEmpty(); i--) {
				makeBrute(pool.removeLast());
			}
		}
		if (danger >= SCOUT_DANGER && random.nextFloat() < SCOUT_CHANCE + SCOUT_CHANCE_DANGER * danger) {
			for (int i = danger >= SCOUT_TWO ? 2 : 1; i > 0 && !pool.isEmpty(); i--) {
				makeScout(pool.removeLast());
			}
		}
		if (danger >= BUILDER_DANGER && random.nextFloat() < BUILDER_CHANCE + BUILDER_CHANCE_DANGER * danger) {
			int want = danger >= BUILDER_TWO ? 2 : 1;
			for (int i = pool.size() - 1; i >= 0 && want > 0; i--) {
				if (pool.get(i).getOffhandItem().isEmpty()) {
					makeBuilder(pool.remove(i), danger, random);
					want--;
				}
			}
		}
		float runners = RUNNER_SHARE + RUNNER_SHARE_DANGER * danger;
		for (Mob m : pool) {
			if (random.nextFloat() < runners) {
				m.addTag(RUNNER_TAG);
			}
		}
	}

	private static void makeBrute(Mob mob) {
		mob.addTag(BRUTE_TAG);
		modify(mob, Attributes.SCALE, BRUTE_SCALE_ID, BRUTE_SCALE, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
		modify(mob, Attributes.MAX_HEALTH, BRUTE_HEALTH_ID, BRUTE_HEALTH, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
		modify(mob, Attributes.MOVEMENT_SPEED, BRUTE_SLOW_ID, -BRUTE_SLOW, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
		modify(mob, Attributes.KNOCKBACK_RESISTANCE, BRUTE_KNOCKBACK_ID, BRUTE_KNOCKBACK, AttributeModifier.Operation.ADD_VALUE);
		mob.setHealth(mob.getMaxHealth());
	}

	private static void makeScout(Mob mob) {
		mob.addTag(SCOUT_TAG);
		modify(mob, Attributes.MOVEMENT_SPEED, SCOUT_SPEED_ID, SCOUT_SPEED, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
		modify(mob, Attributes.MAX_HEALTH, SCOUT_HEALTH_ID, -SCOUT_FRAIL, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
		modify(mob, Attributes.SCALE, SCOUT_SCALE_ID, SCOUT_SCALE, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
		mob.setHealth(mob.getMaxHealth());
	}

	/** It carries its blocks in the off hand, where you can see them; what's left falls with it. */
	private static void makeBuilder(Mob mob, float danger, RandomSource random) {
		mob.addTag(BUILDER_TAG);
		int count = BUILD_MIN + Math.round(danger * (BUILD_MAX - BUILD_MIN));
		mob.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(BUILD_BLOCKS[random.nextInt(BUILD_BLOCKS.length)], count));
		mob.setDropChance(EquipmentSlot.OFFHAND, 2.0F);
	}

	private static void modify(Mob mob, Holder<Attribute> attribute, Identifier id, double amount, AttributeModifier.Operation op) {
		AttributeInstance instance = mob.getAttribute(attribute);
		if (instance != null) {
			instance.addOrReplacePermanentModifier(new AttributeModifier(id, amount, op));
		}
	}

	// ---- every tick --------------------------------------------------------------------------------------------------------

	/** One tick of a horde's kinds: lunges for all its zombies, and each runner, builder and scout at its work. */
	static void tick(ZombieHordes.Horde horde) {
		ServerLevel level = horde.level;
		long now = level.getGameTime();
		boolean griefing = level.getGameRules().get(GameRules.MOB_GRIEFING);
		ServerPlayer hunted = level.getPlayerByUUID(horde.hunted) instanceof ServerPlayer p && p.isAlive() && !p.isSpectator() && !p.isCreative()
			&& p.level() == level ? p : null;
		for (Mob mob : horde.members) {
			if (!mob.isAlive() || mob.isRemoved() || !(mob instanceof Zombie)) {
				continue;
			}
			lunge(mob);
			var tags = mob.entityTags();
			if (tags.contains(RUNNER_TAG)) {
				run(level, mob, now);
			} else if (tags.contains(BUILDER_TAG)) {
				if (griefing) {
					build(level, mob, horde.goal, now);
				}
			} else if (tags.contains(SCOUT_TAG)) {
				scout(level, horde, mob, hunted, now);
			}
		}
	}

	/** It closes the last step on its prey: a lunge, and a push forward. */
	private static void lunge(Mob mob) {
		LivingEntity target = mob.getTarget();
		if (target == null || !target.isAlive()) {
			CLOSING.remove(mob);
			return;
		}
		double d = mob.distanceTo(target);
		Double was = CLOSING.put(mob, d);
		if (was != null && was > LUNGE_RANGE && d <= LUNGE_RANGE && mob.onGround()) {
			Vec3 to = target.position().subtract(mob.position()).multiply(1.0, 0.0, 1.0);
			if (to.lengthSqr() > 1.0E-4) {
				mob.setDeltaMovement(mob.getDeltaMovement().add(to.normalize().scale(LUNGE_PUSH)));
			}
			act(mob, LUNGE);
		}
	}

	// ---- runners -----------------------------------------------------------------------------------------------------------

	/** A runner sprints while it sees who it's after, and leaps the small gaps between. */
	private static void run(ServerLevel level, Mob mob, long now) {
		LivingEntity target = mob.getTarget();
		boolean sees = target != null && target.isAlive() && !mob.isInWater() && mob.getSensing().hasLineOfSight(target);
		AttributeInstance speed = mob.getAttribute(Attributes.MOVEMENT_SPEED);
		if (sees) {
			if (!mob.isSprinting()) {
				mob.setSprinting(true);
				if (speed != null) {
					speed.addOrUpdateTransientModifier(new AttributeModifier(RUN_ID, RUN_SPEED, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
				}
			}
			leap(level, mob, target, now);
		} else if (mob.isSprinting()) {
			mob.setSprinting(false);
			if (speed != null) {
				speed.removeModifier(RUN_ID);
			}
		}
	}

	/** At the edge of a gap one or two blocks wide (and at least two deep) toward its prey, with floor beyond: it jumps it. */
	private static void leap(ServerLevel level, Mob mob, LivingEntity target, long now) {
		if (!mob.onGround() || now < NEXT_LEAP.getOrDefault(mob, 0L)) {
			return;
		}
		Vec3 off = target.position().subtract(mob.position());
		if (off.horizontalDistance() < LEAP_FROM) {
			return;
		}
		Direction dir = Direction.getApproximateNearest(off.x, 0.0, off.z);
		BlockPos feet = mob.blockPosition();
		BlockPos first = feet.relative(dir);
		if (!open(level, first) || !open(level, first.above()) || !open(level, first.below()) || !open(level, first.below(2))) {
			return;
		}
		for (int i = 2; i <= LEAP_GAP + 1; i++) {
			BlockPos land = feet.relative(dir, i);
			if (!open(level, land) || !open(level, land.above())) {
				return;
			}
			if (!open(level, land.below())) {
				Vec3 aim = Vec3.atBottomCenterOf(land).subtract(mob.position()).multiply(1.0, 0.0, 1.0);
				double v = Mth.clamp(aim.length() / LEAP_REACH, LEAP_MIN, LEAP_MAX);
				mob.setDeltaMovement(aim.normalize().scale(v).add(0.0, LEAP_UP, 0.0));
				NEXT_LEAP.put(mob, now + LEAP_EVERY);
				return;
			}
		}
	}

	/** Nothing to stand on or bump into there. */
	private static boolean open(ServerLevel level, BlockPos pos) {
		return level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
	}

	// ---- builders ----------------------------------------------------------------------------------------------------------

	/**
	 * A builder with no way to where it's going (its path doesn't reach, or it has none) makes one: up under itself for a
	 * target above it or a wall in its way, a step ahead for a target higher and further off, the floor ahead over a gap.
	 */
	private static void build(ServerLevel level, Mob mob, Vec3 goal, long now) {
		ItemStack blocks = mob.getOffhandItem();
		Block block = Block.byItem(blocks.getItem());
		if (blocks.isEmpty() || block == Blocks.AIR) {
			PILLAR.remove(mob);
			return;
		}
		BlockState state = block.defaultBlockState();
		// mid-jump: at the top, the block under its feet
		Pillar pillar = PILLAR.get(mob);
		if (pillar != null) {
			if (now - pillar.at() > PILLAR_TICKS || mob.getY() >= pillar.from().getY() + 1.0 && place(level, mob, pillar.from(), state, now)) {
				PILLAR.remove(mob);
			}
			return;
		}
		if (now < NEXT_BUILD.getOrDefault(mob, 0L) || !mob.onGround() || mob.isInWater()) {
			return;
		}
		LivingEntity target = mob.getTarget();
		if ((target == null || !target.isAlive()) && !mob.horizontalCollision) {
			// only for someone it's after, or (marching) when it has walked into something
			return;
		}
		Vec3 to = target != null && target.isAlive() ? target.position() : goal;
		Vec3 here = mob.position();
		double rise = to.y - here.y, flat = Math.hypot(to.x - here.x, to.z - here.z);
		if (flat < 1.5 && rise < BUILD_RISE) {
			return;
		}
		Path path = mob.getNavigation().getPath();
		if (path != null && !path.isDone() && path.canReach()) {
			return;
		}
		BlockPos feet = mob.blockPosition();
		Direction dir = Direction.getApproximateNearest(to.x - here.x, 0.0, to.z - here.z);
		BlockPos ahead = feet.relative(dir);
		if (rise >= BUILD_RISE) {
			boolean wall = !level.getBlockState(ahead).canBeReplaced();
			if (flat < PILLAR_NEAR || wall) {
				// up: jump straight, and lay the block it left at the top of the jump
				if (level.getBlockState(feet).canBeReplaced() && open(level, feet.above(2))) {
					mob.getNavigation().stop();
					mob.jumpFromGround();
					mob.setDeltaMovement(0.0, mob.getDeltaMovement().y, 0.0);
					PILLAR.put(mob, new Pillar(feet.immutable(), now));
				}
			} else if (open(level, ahead.above()) && open(level, ahead.above(2)) && place(level, mob, ahead, state, now)) {
				// a step, and up onto it
				mob.jumpFromGround();
				mob.setDeltaMovement(dir.getStepX() * 0.15, mob.getDeltaMovement().y, dir.getStepZ() * 0.15);
			}
		} else if (rise > -BUILD_RISE && open(level, ahead) && open(level, ahead.above()) && open(level, ahead.below(2))
			&& level.getBlockState(ahead.below()).canBeReplaced()) {
			// the floor ahead, over a gap
			place(level, mob, ahead.below(), state, now);
		}
	}

	/** Lays one of the blocks it carries at {@code pos}, if there's room. */
	private static boolean place(ServerLevel level, Mob mob, BlockPos pos, BlockState state, long now) {
		if (!level.getBlockState(pos).canBeReplaced() || !level.isUnobstructed(state, pos, CollisionContext.empty())) {
			return false;
		}
		level.setBlock(pos, state, Block.UPDATE_ALL);
		level.gameEvent(mob, GameEvent.BLOCK_PLACE, pos);
		SoundType sound = state.getSoundType();
		level.playSound(null, pos, sound.getPlaceSound(), SoundSource.HOSTILE, (sound.getVolume() + 1.0F) / 2.0F, sound.getPitch() * 0.8F);
		mob.swing(InteractionHand.OFF_HAND, SwingAnimation.DEFAULT, true);
		ItemStack blocks = mob.getOffhandItem();
		blocks.shrink(1);
		if (blocks.isEmpty()) {
			mob.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
		}
		NEXT_BUILD.put(mob, now + BUILD_EVERY);
		return true;
	}

	// ---- scouts ------------------------------------------------------------------------------------------------------------

	/** Where a scout ranges on the march: straight for who it came for until it has howled, then hanging back; null when they're out of its range. */
	static @Nullable Vec3 scoutSpot(Mob mob, @Nullable ServerPlayer hunted) {
		if (hunted == null || !scout(mob) || mob.distanceTo(hunted) > SCOUT_RANGE) {
			return null;
		}
		Long howled = HOWLED.get(mob);
		if (howled == null || mob.level().getGameTime() - howled > HOWL_TRACK) {
			return hunted.position();
		}
		return holdBack(mob, hunted);
	}

	private static Vec3 holdBack(Mob mob, LivingEntity from) {
		Vec3 away = mob.position().subtract(from.position()).multiply(1.0, 0.0, 1.0);
		if (away.lengthSqr() < 1.0E-4) {
			away = new Vec3(1.0, 0.0, 0.0);
		}
		return from.position().add(away.normalize().scale(SCOUT_HOLD));
	}

	/** A scout that sees who it's after howls for the horde (now and then), and keeps out of reach unless cornered. */
	private static void scout(ServerLevel level, ZombieHordes.Horde horde, Mob mob, @Nullable ServerPlayer hunted, long now) {
		LivingEntity target = mob.getTarget();
		LivingEntity seen = null;
		if (hunted != null && mob.distanceTo(hunted) < SCOUT_SIGHT && mob.getSensing().hasLineOfSight(hunted)) {
			seen = hunted;
		} else if (target instanceof Player && target.isAlive() && mob.distanceTo(target) < SCOUT_SIGHT && mob.getSensing().hasLineOfSight(target)) {
			seen = target;
		}
		if (seen != null && now - HOWLED.getOrDefault(mob, Long.MIN_VALUE / 2) >= HOWL_EVERY) {
			howl(level, horde, mob, seen, now);
		}
		LivingEntity from = target != null && target.isAlive() ? target : seen;
		if (from == null || mob.distanceTo(from) <= SCOUT_CORNERED) {
			return;
		}
		if (target != null) {
			mob.setTarget(null);
		}
		if (mob.distanceTo(from) < SCOUT_HOLD - 2.0 && now % 10 == 0) {
			Vec3 back = holdBack(mob, from);
			mob.getNavigation().moveTo(back.x, back.y, back.z, SCOUT_BACK_SPEED);
		}
	}

	/** The howl: a dead voice heard far off, and the rest of the horde turns to where it saw them. */
	private static void howl(ServerLevel level, ZombieHordes.Horde horde, Mob scout, LivingEntity seen, long now) {
		HOWLED.put(scout, now);
		horde.howled = now;
		horde.goal = seen.position();
		level.playSound(null, scout.getX(), scout.getEyeY(), scout.getZ(), SoundEvents.ZOMBIE_VILLAGER_AMBIENT, SoundSource.HOSTILE, HOWL_VOLUME, HOWL_PITCH);
		scout.getLookControl().setLookAt(seen);
		act(scout, HOWL);
		UUID who = seen instanceof ServerPlayer p ? p.getUUID() : null;
		for (Mob m : horde.members) {
			if (m != scout && m.getTarget() == null && !scout(m)) {
				Senses.lure(m, seen.position(), who, HOWL_LOUDNESS);
			}
		}
	}
}
