package com.jaymoskingdoms.beasts;

import com.jaymoskingdoms.Fx;
import com.jaymoskingdoms.Loaded;
import java.util.List;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The queen of a bombardier colony, who lives at the foot of a great tree (a trunk {@link #MIN_TRUNK} logs high or more,
 * in the warm lands: {@link #turnUp}) with her workers and pupae ({@link BombardierBeetle}). Kept to the three rules:
 * <ul>
 * <li><b>Sympathy.</b> A colony feeds on the wood it lives in, as a termite's does. Every {@link #FEED_EVERY} ticks she
 * gnaws the highest log of her tree within {@link #FEED_REACH} blocks of her home ({@link #gnaw}), and each meal she lays
 * a pupa, up to {@link #CAP} of the colony about her. When her young have hatched and the colony is full and well fed
 * ({@link #SPREAD_FEEDS} meals), it swarms: a young queen founds a colony in another great tree ({@link #spread}). Her
 * workers spray anything that hurts her.</li>
 * <li><b>Feedback.</b> What she eats is her own tree: its branches go first, then its trunk. With no wood left within her
 * reach she lays no more and never swarms, so a colony left alone eats its tree bare, spreads while it has food and is
 * then stuck. If she dies the workers scatter, leave and wander off ({@link #die}), the pupae hatching wild.</li>
 * <li><b>Stewardship.</b> A log held out to her is a meal ({@link #mobInteract}): the tree is spared, the colony grows
 * and every {@link #GIFT_FEEDS} logs she gives up a pair of glands. Nobody who leaves a colony alone loses anything,
 * and nothing makes anyone kill one.</li>
 * </ul>
 */
public class BeetleQueen extends BombardierBeetle {
	static final double HEALTH = 60.0, ARMOR = 8.0, SPEED = 0.18, SCALE = 2.0, FOLLOW = 5.0;
	static final double QUEEN_RANGE = 6.0;
	static final float QUEEN_DAMAGE = 6.0F;
	/** A great tree: the trunk's least height (logs). */
	static final int MIN_TRUNK = 7;
	/** How near the tree's foot she keeps (blocks), and how far her workers keep to her and count her (blocks). */
	static final int HOME_RADIUS = 5;
	public static final int RANGE = 48;
	/** A worker with no queen about this long (ticks) leaves. */
	public static final int SCATTER_TICKS = 400;
	/** Meals: how often she gnaws (ticks), how far from her home she reaches for wood (blocks, across and up). */
	static final int FEED_EVERY = 1200, FEED_REACH = 6, FEED_UP = 14;
	/** The most workers and pupae of one colony, and the meals before it swarms. */
	static final int CAP = 8, SPREAD_FEEDS = 8;
	/** Logs fed by hand before she gives glands (and how many). */
	static final int GIFT_FEEDS = 3, GIFT_GLANDS = 2;
	/** Swarming: how far from her tree a new one is looked for (blocks, least and most), tries, the space a queen keeps from another, and most queens near. */
	static final int SWARM_MIN = 20, SWARM_MAX = 44, SWARM_TRIES = 16, SPACING = 40, MOST_NEAR = 3;
	/** Turning up: ticks between looks at the warm lands round each player, the chance a look is made, the range (blocks) and tries. */
	static final int LOOK_EVERY = 600, LOOK_RANGE = 48, LOOK_TRIES = 12;
	static final float LOOK_CHANCE = 0.1F;
	/** A new colony: its pupae and workers. */
	static final int FOUND_PUPAE = 3, FOUND_WORKERS = 2;

	/** Ticks to her next meal, meals toward a swarm, and logs fed by hand toward a gift. */
	private int hungry = FEED_EVERY / 2, fed, handFed;

	public BeetleQueen(EntityType<? extends BeetleQueen> type, Level level) {
		super(type, level);
		this.xpReward = 20;
	}

	public static AttributeSupplier.Builder createAttributes() {
		return BombardierBeetle.createAttributes().add(Attributes.MAX_HEALTH, HEALTH).add(Attributes.ARMOR, ARMOR).add(Attributes.MOVEMENT_SPEED, SPEED)
			.add(Attributes.SCALE, SCALE).add(Attributes.FOLLOW_RANGE, FOLLOW).add(Attributes.ATTACK_DAMAGE, QUEEN_DAMAGE);
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(BeetleQueen::turnUp);
	}

	@Override
	protected double sprayRange() {
		return QUEEN_RANGE;
	}

	@Override
	protected float sprayDamage() {
		return QUEEN_DAMAGE;
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		if (!this.colony()) {
			this.joinColony(this.blockPosition(), HOME_RADIUS);
		}
		if (--this.hungry <= 0) {
			this.hungry = FEED_EVERY + this.getRandom().nextInt(FEED_EVERY / 4);
			BlockPos log = this.findLog(level);
			if (log != null) {
				this.gnaw(level, log);
				this.meal(level);
			}
		}
	}

	/** The highest log of her tree within reach of her home, or null. */
	private @Nullable BlockPos findLog(ServerLevel level) {
		BlockPos home = this.hasHome() ? this.getHomePosition() : this.blockPosition();
		BlockPos best = null;
		for (BlockPos pos : BlockPos.betweenClosed(home.offset(-FEED_REACH, -1, -FEED_REACH), home.offset(FEED_REACH, FEED_UP, FEED_REACH))) {
			if (level.getBlockState(pos).is(BlockTags.LOGS) && (best == null || pos.getY() > best.getY())) {
				best = pos.immutable();
			}
		}
		return best;
	}

	/** She gnaws {@code log} away. */
	private void gnaw(ServerLevel level, BlockPos log) {
		level.destroyBlock(log, false, this);
		level.playSound(null, log, SoundEvents.WOOD_BREAK, SoundSource.NEUTRAL, 0.8F, 0.7F);
	}

	/** A meal (gnawed or given): it fills her, and she lays a pupa; a full, fed colony swarms. */
	private void meal(ServerLevel level) {
		this.fed++;
		List<BombardierBeetle> kin = this.kin(level);
		if (kin.size() < CAP) {
			BombardierBeetle pupa = LabyrinthCraft.BOMBARDIER.create(level, EntitySpawnReason.BREEDING);
			if (pupa != null) {
				pupa.snapTo(this.getX() + this.getRandom().nextDouble() - 0.5, this.getY(), this.getZ() + this.getRandom().nextDouble() - 0.5,
					this.getRandom().nextFloat() * 360.0F, 0.0F);
				pupa.joinColony(this.hasHome() ? this.getHomePosition() : this.blockPosition(), HOME_RADIUS);
				pupa.setPupa(BombardierBeetle.PUPA_TICKS);
				level.addFreshEntity(pupa);
			}
		} else if (this.fed >= SPREAD_FEEDS) {
			this.fed = 0;
			this.spread(level);
		}
	}

	/** The colony about her: her workers and pupae. */
	private List<BombardierBeetle> kin(ServerLevel level) {
		return level.getEntitiesOfClass(BombardierBeetle.class, this.getBoundingBox().inflate(RANGE), b -> b != this && b.isAlive() && b.colony());
	}

	/** A young queen founds a colony in another great tree not far off. */
	private void spread(ServerLevel level) {
		RandomSource random = this.getRandom();
		for (int i = 0; i < SWARM_TRIES; i++) {
			double angle = random.nextDouble() * Math.PI * 2.0, far = SWARM_MIN + random.nextInt(SWARM_MAX - SWARM_MIN + 1);
			BlockPos spot = BlockPos.containing(this.getX() + Math.cos(angle) * far, this.getY(), this.getZ() + Math.sin(angle) * far);
			if (found(level, spot, false)) {
				return;
			}
		}
	}

	/** Founds a colony in the great tree at {@code near}, if there is one and no colony is crowding it; whether it did. */
	private static boolean found(ServerLevel level, BlockPos near, boolean full) {
		if (!Loaded.made(level, near)) {
			return false;
		}
		BlockPos stand = foot(level, near);
		if (stand == null) {
			return false;
		}
		List<BeetleQueen> queens = level.getEntitiesOfClass(BeetleQueen.class, new AABB(stand).inflate(SPACING * 2.0));
		if (queens.size() >= MOST_NEAR || queens.stream().anyMatch(q -> q.blockPosition().distSqr(stand) < (double) SPACING * SPACING)) {
			return false;
		}
		BeetleQueen queen = LabyrinthCraft.QUEEN.create(level, EntitySpawnReason.NATURAL);
		if (queen == null) {
			return false;
		}
		queen.snapTo(stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5, level.getRandom().nextFloat() * 360.0F, 0.0F);
		queen.joinColony(stand, HOME_RADIUS);
		level.addFreshEntity(queen);
		int pupae = full ? FOUND_PUPAE : 1, workers = full ? FOUND_WORKERS : 0;
		for (int i = 0; i < pupae + workers; i++) {
			BombardierBeetle kin = LabyrinthCraft.BOMBARDIER.create(level, EntitySpawnReason.NATURAL);
			if (kin != null) {
				kin.snapTo(stand.getX() + 0.5 + level.getRandom().nextDouble() * 2.0 - 1.0, stand.getY(), stand.getZ() + 0.5 + level.getRandom().nextDouble() * 2.0 - 1.0,
					level.getRandom().nextFloat() * 360.0F, 0.0F);
				kin.joinColony(stand, HOME_RADIUS);
				if (i < pupae) {
					kin.setPupa(BombardierBeetle.PUPA_TICKS / 2);
				}
				level.addFreshEntity(kin);
			}
		}
		return true;
	}

	/** Open ground beside the foot of the great tree that stands at {@code near} (its trunk {@link #MIN_TRUNK} logs high), or null. */
	private static @Nullable BlockPos foot(ServerLevel level, BlockPos near) {
		BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, near).below();
		if (!level.getBlockState(top).is(BlockTags.LOGS)) {
			return null;
		}
		BlockPos base = top;
		int height = 1;
		while (level.getBlockState(base.below()).is(BlockTags.LOGS)) {
			base = base.below();
			height++;
		}
		if (height < MIN_TRUNK) {
			return null;
		}
		for (Direction side : Direction.Plane.HORIZONTAL) {
			BlockPos cell = base.relative(side);
			if (level.getBlockState(cell).isAir() && level.getBlockState(cell.above()).isAir()
				&& level.getBlockState(cell.below()).isFaceSturdy(level, cell.below(), Direction.UP)) {
				return cell;
			}
		}
		return null;
	}

	/** A log held out to her is a meal. */
	@Override
	protected InteractionResult mobInteract(Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (!stack.is(ItemTags.LOGS)) {
			return super.mobInteract(player, hand);
		}
		if (this.level() instanceof ServerLevel level) {
			if (!player.isCreative()) {
				stack.shrink(1);
			}
			level.playSound(null, this.blockPosition(), SoundEvents.WOOD_BREAK, SoundSource.NEUTRAL, 0.8F, 0.9F);
			this.hungry = FEED_EVERY + this.getRandom().nextInt(FEED_EVERY / 4);
			this.meal(level);
			if (++this.handFed >= GIFT_FEEDS) {
				this.handFed = 0;
				this.spawnAtLocation(level, new ItemStack(LabyrinthCraft.BEETLE_GLAND, GIFT_GLANDS));
			}
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		boolean hurt = super.hurtServer(level, source, damage);
		if (hurt && this.isAlive() && source.getEntity() instanceof LivingEntity attacker) {
			// her workers come to her defence
			for (BombardierBeetle worker : this.kin(level)) {
				if (!worker.pupa()) {
					worker.setTarget(attacker);
				}
			}
		}
		return hurt;
	}

	/** With her gone, her workers scatter. */
	@Override
	public void die(DamageSource source) {
		super.die(source);
		if (this.level() instanceof ServerLevel level) {
			for (BombardierBeetle kin : this.kin(level)) {
				kin.scatter();
			}
			Vec3 at = this.position();
			Fx.ring(level, at, 3.0F, STEAM);
		}
	}

	@Override
	void scatter() {
		// (a queen does not leave her tree)
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putInt("hungry", this.hungry);
		output.putInt("fed", this.fed);
		output.putInt("hand_fed", this.handFed);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		this.hungry = input.getIntOr("hungry", FEED_EVERY / 2);
		this.fed = input.getIntOr("fed", 0);
		this.handFed = input.getIntOr("hand_fed", 0);
	}

	// ---- turning up ------------------------------------------------------------------------------------------------------

	/** Now and then a great tree in the warm lands near a player holds a colony. */
	private static void turnUp(MinecraftServer server) {
		if (LabyrinthCraft.QUEEN == null || server.getTickCount() % LOOK_EVERY != 0) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			ServerLevel level = player.level();
			RandomSource random = level.getRandom();
			if (player.isSpectator() || random.nextFloat() >= LOOK_CHANCE) {
				continue;
			}
			for (int i = 0; i < LOOK_TRIES; i++) {
				BlockPos pos = player.blockPosition().offset(random.nextInt(LOOK_RANGE * 2 + 1) - LOOK_RANGE, 0, random.nextInt(LOOK_RANGE * 2 + 1) - LOOK_RANGE);
				if (Loaded.made(level, pos) && level.getBiome(pos).is(LabyrinthCraft.HAS_BOMBARDIER) && found(level, pos, true)) {
					break;
				}
			}
		}
	}
}
