package com.jaymospowers.ground;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import com.jaymospowers.core.wounds.Downed;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.throwableitemprojectile.Snowball;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.17.14) Andy: "add the ability for players to throw rocks not just the npcs". Right click the air with a loose rock
 * ({@link GroundRocks}) in either hand and you throw it, the way Kingdoms' poor guards and barbarians do: a snowball
 * wearing the rock, that hits for {@link #DAMAGE} (Kingdoms' {@code Javelins.ROCK_DAMAGE}). Right clicking a block still
 * sets the rock down, and a rock with flint in the other hand still strikes fire. The rock lies where it lands
 * {@link #KEEP_CHANCE} of the time, to be picked up again; the rest break.
 * <p>
 * (0.18.84) Andy: "throwing rocks should have a chance to knockout unsuspecting mobs and players". A rock that hits
 * someone who isn't braced for it ({@link #unsuspecting}: not in a fight, and not looking your way, or asleep) knocks
 * them out ({@link #HEAD_CHANCE} on the head, {@link #BODY_CHANCE} elsewhere) for {@link #OUT_MIN} to {@link #OUT_MAX}
 * ticks: down where they stand, eyes shut, still breathing ({@link Downed#knockOut}). Players see black and can mash
 * sneak to come round sooner. Anything with a body can be laid out this way, wild animals and spiders too, though only
 * people can be lifted and carried off ({@code Carry}, which wants strength for a body in heavy armour).
 * <p>
 * (0.18.84) Andy: "the thrown rocks that break leave what looks like an egg splat, it should instead be a crack or a
 * break, and little specks of the rocks debris fly out and off into rubble that dissipates". A rock landing never sends
 * the snowball's burst ({@code RockSnowballMixin}): one that breaks cracks the block it hit ({@link #CRACK_STAGE},
 * fading) and throws {@link #SPECKS} specks of stone that fall and settle as grit; one that lies there sheds a few.
 */
public final class ThrownRocks {
	/** A rock hits for this much. */
	static final float DAMAGE = 3.0F;
	/** How hard it's thrown, and how wide it may stray. */
	static final float SPEED = 1.5F, SPREAD = 1.0F;
	/** Ticks between throws. */
	static final int COOLDOWN = 10;
	/** How often a rock lies where it lands rather than breaking. */
	static final float KEEP_CHANCE = 0.5F;
	/** A rock still flying after this long is no longer watched. */
	static final int LIFE = 200;
	/** (0.18.84) The chance a rock knocks out someone unsuspecting: hit on the head (near eye height), and anywhere else. */
	static final float HEAD_CHANCE = 0.6F, BODY_CHANCE = 0.2F;
	/** (0.18.84) How near their eyes (blocks, below) a rock still counts as a blow to the head. */
	static final double HEAD_REACH = 0.35;
	/** (0.18.84) How long a knock-out lasts (ticks): 15 to 45 seconds. */
	static final int OUT_MIN = 300, OUT_MAX = 900;
	/** (0.18.84) Hurt this recently (ticks), they're on their guard. */
	static final int WARY_TICKS = 100;
	/** (0.18.84) Looking this straight at the thrower (cosine of the angle), they see it coming: about 60 degrees either side. */
	static final double SEES_COSINE = 0.5;
	/** (0.18.84) A broken rock: specks of stone thrown out (and a few from one that lies there), and how fast they fly. */
	static final int SPECKS = 12, KEPT_SPECKS = 3;
	static final double SPECK_OUT = 0.14, SPECK_SPREAD = 0.12, SPECK_UP = 0.08;
	/** (0.18.84) The crack a broken rock leaves on the block: how deep (0-9, the block-breaking stages), and how long each stage lasts as it fades (ticks). */
	static final int CRACK_STAGE = 3, CRACK_STEP = 12;

	/** (0.18.84) A crack fading off a block: the id it's drawn under, where, and its stage now. */
	private static final class Crack {
		final ServerLevel level;
		final int id;
		final BlockPos pos;
		int age, stage = CRACK_STAGE;

		Crack(ServerLevel level, int id, BlockPos pos) {
			this.level = level;
			this.id = id;
			this.pos = pos;
		}
	}

	private static final List<Crack> CRACKS = new ArrayList<>();

	private static final class Rock {
		final Snowball ball;
		final ItemStack stone;
		final boolean creative;
		int age;

		Rock(Snowball ball, ItemStack stone, boolean creative) {
			this.ball = ball;
			this.stone = stone;
			this.creative = creative;
		}
	}

	private static final List<Rock> FLYING = new ArrayList<>();
	/** While a rock's blow is being dealt again (so it isn't again and again). */
	private static boolean redealing;

	private ThrownRocks() {
	}

	public static void init() {
		UseItemCallback.EVENT.register(ThrownRocks::use);
		ServerLivingEntityEvents.ALLOW_DAMAGE.register(ThrownRocks::allowDamage);
		ServerTickEvents.END_SERVER_TICK.register(ThrownRocks::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			FLYING.clear();
			CRACKS.clear();
		});
	}

	private static InteractionResult use(Player player, Level level, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (player.isSpectator() || !FirstTools.rock(stack) || FirstTools.striking(player) || player.getCooldowns().isOnCooldown(stack)) {
			return InteractionResult.PASS;
		}
		player.getCooldowns().addCooldown(stack, COOLDOWN);
		if (level instanceof ServerLevel server) {
			ItemStack stone = stack.copyWithCount(1);
			Snowball ball = Projectile.spawnProjectileFromRotation((l, owner, s) -> new Snowball(l, owner, s), server, stone, player, 0.0F, SPEED,
				SPREAD);
			FLYING.add(new Rock(ball, stone, player.getAbilities().instabuild));
			server.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SNOWBALL_THROW, SoundSource.PLAYERS, 0.6F,
				0.5F + server.getRandom().nextFloat() * 0.2F);
			stack.consume(1, player);
		}
		return InteractionResult.SUCCESS;
	}

	private static @Nullable Rock rock(Entity entity) {
		for (Rock rock : FLYING) {
			if (rock.ball == entity) {
				return rock;
			}
		}
		return null;
	}

	private static boolean thrown(Entity entity) {
		return rock(entity) != null;
	}

	/** A snowball does no harm: a rock's blow is dealt again with its weight. */
	private static boolean allowDamage(LivingEntity victim, DamageSource source, float amount) {
		if (redealing || amount >= DAMAGE || !(source.getDirectEntity() instanceof Snowball ball) || !thrown(ball)
			|| !(victim.level() instanceof ServerLevel level)) {
			return true;
		}
		// (0.18.84) whether they saw it coming, before the blow makes them wary
		boolean unsuspecting = unsuspecting(victim, source.getEntity());
		boolean head = ball.getY() >= victim.getEyeY() - HEAD_REACH;
		boolean hurt;
		redealing = true;
		try {
			hurt = victim.hurtServer(level, source, DAMAGE);
		} finally {
			redealing = false;
		}
		if (hurt && unsuspecting && victim.isAlive() && !Downed.passedOut(victim)
			&& level.getRandom().nextFloat() < (head ? HEAD_CHANCE : BODY_CHANCE)) {
			int ticks = OUT_MIN + level.getRandom().nextInt(OUT_MAX - OUT_MIN + 1);
			if (Downed.knockOut(level, victim, ticks, true, "message.jaymospowers.downed.knocked_rock")) {
				level.playSound(null, victim.getX(), victim.getEyeY(), victim.getZ(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.NEUTRAL, 0.9F, 0.7F);
			}
		}
		return false;
	}

	/**
	 * (0.18.84) Whether {@code victim} never saw the rock coming: asleep; or not in a fight (no target, nobody's hurt them
	 * in {@link #WARY_TICKS}) and the thrower out of their sight (behind them, off to the side, or round a corner).
	 */
	static boolean unsuspecting(LivingEntity victim, @Nullable Entity thrower) {
		if (victim.isSleeping()) {
			return true;
		}
		if (thrower == null || victim instanceof Mob mob && mob.getTarget() != null
			|| victim.getLastHurtByMob() != null && victim.tickCount - victim.getLastHurtByMobTimestamp() < WARY_TICKS) {
			return false;
		}
		Vec3 toward = thrower.getEyePosition().subtract(victim.getEyePosition()).normalize();
		return victim.getViewVector(1.0F).dot(toward) < SEES_COSINE || !victim.hasLineOfSight(thrower);
	}

	/**
	 * (0.18.84) A snowball has landed ({@code RockSnowballMixin}, server side): if it wears a rock (ours, or Kingdoms'
	 * cobblestone), it breaks (a crack in the block it hit, specks of stone) or, one of ours {@link #KEEP_CHANCE} of the
	 * time, lies there. False for a real snowball, which bursts as ever.
	 */
	public static boolean landed(Snowball ball, HitResult hit) {
		ItemStack stone = ball.getItem();
		if (!(ball.level() instanceof ServerLevel level) || !(FirstTools.rock(stone) || stone.is(Items.COBBLESTONE))) {
			return false;
		}
		Rock rock = rock(ball);
		if (rock != null) {
			FLYING.remove(rock);
		}
		boolean keep = rock != null && !rock.creative && level.getRandom().nextFloat() < KEEP_CHANCE;
		Vec3 at = ball.position();
		// the face it struck (straight back along its flight off a body)
		Vec3 normal = hit instanceof BlockHitResult block ? Vec3.atLowerCornerOf(block.getDirection().getUnitVec3i())
			: ball.getDeltaMovement().lengthSqr() > 1.0E-6 ? ball.getDeltaMovement().normalize().scale(-1.0) : new Vec3(0.0, 1.0, 0.0);
		specks(level, stone, at, normal, keep ? KEPT_SPECKS : SPECKS);
		if (keep) {
			ItemEntity item = new ItemEntity(level, at.x, at.y, at.z, rock.stone.copy());
			item.setDefaultPickUpDelay();
			level.addFreshEntity(item);
			level.playSound(null, at.x, at.y, at.z, SoundEvents.STONE_HIT, SoundSource.NEUTRAL, 0.6F, 0.9F);
			return true;
		}
		level.playSound(null, at.x, at.y, at.z, SoundEvents.STONE_BREAK, SoundSource.NEUTRAL, 0.55F, 1.3F + level.getRandom().nextFloat() * 0.3F);
		if (hit instanceof BlockHitResult block && hit.getType() == HitResult.Type.BLOCK && !level.getBlockState(block.getBlockPos()).isAir()) {
			Crack crack = new Crack(level, ball.getId(), block.getBlockPos());
			level.destroyBlockProgress(crack.id, crack.pos, crack.stage);
			CRACKS.add(crack);
		}
		return true;
	}

	/** (0.18.84) Specks of {@code stone} thrown out off the face it hit ({@code normal}), to fall and settle as grit. */
	private static void specks(ServerLevel level, ItemStack stone, Vec3 at, Vec3 normal, int count) {
		BlockState look = stone.getItem() instanceof BlockItem item ? item.getBlock().defaultBlockState() : Blocks.STONE.defaultBlockState();
		BlockParticleOption speck = new BlockParticleOption(ParticleTypes.BLOCK, look);
		var random = level.getRandom();
		Vec3 from = at.add(normal.scale(0.1));
		for (int i = 0; i < count; i++) {
			double vx = normal.x * SPECK_OUT + (random.nextDouble() - 0.5) * 2.0 * SPECK_SPREAD;
			double vy = normal.y * SPECK_OUT + SPECK_UP * random.nextDouble();
			double vz = normal.z * SPECK_OUT + (random.nextDouble() - 0.5) * 2.0 * SPECK_SPREAD;
			// (a count of 0 sends the speck off along its own line)
			level.sendParticles(speck, from.x, from.y, from.z, 0, vx, vy, vz, 1.0);
		}
	}

	private static void tick(MinecraftServer server) {
		// (0.18.84) where it landed is seen to in landed; gone some other way (unloaded), it's forgotten
		FLYING.removeIf(rock -> rock.ball.isRemoved() || ++rock.age > LIFE);
		// (0.18.84) cracks fade a stage at a time, then go
		CRACKS.removeIf(crack -> {
			if (++crack.age % CRACK_STEP != 0) {
				return false;
			}
			crack.stage--;
			crack.level.destroyBlockProgress(crack.id, crack.pos, crack.stage < 0 ? -1 : crack.stage);
			return crack.stage < 0;
		});
	}
}
