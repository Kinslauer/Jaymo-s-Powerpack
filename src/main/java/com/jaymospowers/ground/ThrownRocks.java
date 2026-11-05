package com.jaymospowers.ground;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;
import com.jaymospowers.JaymosPowers;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import com.jaymospowers.core.wounds.Downed;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import io.netty.buffer.ByteBuf;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;
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
 * <p>
 * (0.20.69) Andy: "Rocks should deal .5 damage to zombies and pigmen/hornkin". Every zombie (husks, drowned, zombie
 * villagers, MCA's and Kitchen's zombies), zombified piglins and every Hornkin take {@link #SHRUGGED_DAMAGE} from a rock
 * ({@link #shrugsOff}). An unsuspecting one can still be knocked out as before.
 * <p>
 * (0.20.81) Andy: "you also should only be able to throw rocks from the offhand, and after a small charge. it no longer
 * places unless you have it in the main hand". A rock in the main hand only sets down (it is never thrown); a rock in the
 * off hand is never set down, and is thrown once use has been held for {@link #CHARGE} ticks, at the air or at a block
 * ({@link Charge}; 0.20.90: 1.5 s).
 * <p>
 * (0.20.90) Andy: "the player's rock throw should have an animated windup and release". While the charge is held the
 * throwing arm draws back over the shoulder and the body turns that side away; when the rock goes, the arm whips over
 * and through, the other arm swinging back, and follows through for {@link #FOLLOW_TICKS}. A hold let go early lets the
 * arm back down. Everyone sees it ({@link #THROW}, drawn by {@code RockThrowPose}, first and third person); your own
 * client counts the charge ({@link #windUpHere}) and tells the server when the rock goes ({@link Charge}), so your arm
 * moves the moment you press and the rock leaves as it comes over.
 * <p>
 * (0.20.103) Andy: "you can hold a full charged rock before you throw, and each you charge afterward is faster to charge
 * due to momentum". A full charge no longer throws by itself: the arm stays drawn back ({@link #READY}, trembling) for as
 * long as use is held, and the rock goes when you let go. Let go before the charge is full and nothing is thrown. Each
 * throw builds momentum ({@link #MOMENTUM_STEP} ticks off the next charge, up to {@link #MOMENTUM_MAX} throws: 1.5 s down
 * to 0.5 s), and it fades a throw's worth for every {@link #MOMENTUM_FADE} ticks between one throw and the next wind-up.
 */
public final class ThrownRocks {
	/** A rock hits for this much. */
	static final float DAMAGE = 3.0F;
	/** (0.20.69) Zombies, zombified piglins and the Hornkin shrug a rock off: it hits them for only this much. */
	static final float SHRUGGED_DAMAGE = 0.5F;
	/** How hard it's thrown, and how wide it may stray. */
	static final float SPEED = 1.5F, SPREAD = 1.0F;
	/** (0.21.7) A gravity user's rock: weighs no more than a pebble to them, so it goes at once, this hard and this straight. */
	static final float GRAVITY_SPEED = 3.0F, GRAVITY_SPREAD = 0.3F;
	/** Ticks between throws. */
	public static final int COOLDOWN = 10;
	/** How often a rock lies where it lands rather than breaking. */
	static final float KEEP_CHANCE = 0.5F;
	/** (0.20.81) How long use is held with a rock in the off hand before it's thrown (ticks; 0.20.90 Andy: 1.5 s, was 10). */
	public static final int CHARGE = 30;
	/** (0.20.90) How much sooner than {@link #CHARGE} (ticks) the server takes a client's word that the rock goes (lag). */
	static final int CHARGE_SLACK = 8;
	/** (0.20.90) A wind-up whose client says nothing more for this long past the charge (ticks) is let go. */
	static final int STALE_TICKS = 60;
	/** (0.20.81) A hold broken for longer than this (ticks; a held use repeats every 4) starts the charge again. */
	public static final int CHARGE_GAP = 6;
	/** (0.20.103) Momentum: each throw takes this many ticks off the next charge, for up to this many throws in a row. */
	public static final int MOMENTUM_STEP = 5, MOMENTUM_MAX = 4;
	/** (0.20.103) Momentum loses one throw's worth for every this many ticks from a throw to the next wind-up. */
	public static final int MOMENTUM_FADE = 40;
	/** (0.20.103) While a full charge is held, the client says so this often (ticks), so the server keeps the hold. */
	public static final int KEEPALIVE = 20;
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
	/** (0.20.81) Players winding up an off hand throw (0.20.90: and following one through). */
	private static final Map<UUID, Hold> WINDING = new HashMap<>();
	/** (0.20.103) Each player's momentum: {throws in a row, game time of the last}. Not saved. */
	private static final Map<UUID, long[]> MOMENTUM = new HashMap<>();

	/** (0.20.90) The arm's follow-through after the rock goes (ticks). */
	public static final int FOLLOW_TICKS = 6;
	/** (0.20.90) Stages in {@link #THROW} (its low two bits; 0.20.102: {@link #READY}, the full charge held). */
	public static final int WINDING_UP = 1, THROWING = 2, READY = 3;
	/**
	 * (0.20.90) A player throwing a rock, for everyone's client (the pose): {@link #value}, a new number for each hold so
	 * one straight after another starts afresh; absent otherwise. Not saved.
	 */
	public static final AttachmentType<Integer> THROW = AttachmentRegistry.create(JaymosPowers.id("rock_throw"),
		builder -> builder.syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.all()));
	/** (0.20.90) Set by the client: each held use on your own client, which counts the charge and sends {@link Charge}. */
	public static BiConsumer<Player, InteractionHand> windUpHere = (player, hand) -> {
	};
	private static int holds;

	/** (0.20.103) A throw's synced value: the hold's number, its charge (ticks, so the wind-up is drawn at its pace), the stage. */
	public static int value(long id, int charge, int stage) {
		return (int) (id & 0xFFFFF) << 8 | (charge & 63) << 2 | stage;
	}

	/**
	 * (0.21.7) In a {@link #THROW} value: the main arm throws, not the off hand. Andy: "gravity users throw the rocks the
	 * old way": a gravity user's Lift hurling what it holds ({@link #hurled}) whips the main arm through the same way.
	 */
	public static final int MAIN_ARM = 1 << 28;

	/**
	 * (0.21.7) A gravity user's Lift draws the main arm back to hurl what it holds, over {@code ticks} (GravityPower lets
	 * go then, and calls {@link #hurled}). False if a rock in the other hand is winding up (that pose stays).
	 */
	public static void hurling(ServerPlayer player, int ticks) {
		Hold hold = WINDING.get(player.getUUID());
		if (hold != null && hold.thrown < 0 && !hold.hurl) {
			return;
		}
		holds = holds + 1 & 0xFFFFF;
		hold = new Hold(player, player.level().getGameTime(), holds, 0);
		hold.hurl = true;
		hold.charge = Math.max(1, Math.min(63, ticks));
		WINDING.put(player.getUUID(), hold);
		player.setAttached(THROW, value(hold.id, hold.charge, WINDING_UP) | MAIN_ARM);
	}

	/** (0.21.7) The Lift's hurl let go: the main arm whips through and follows through, for everyone. */
	public static void hurled(ServerPlayer player) {
		Hold hold = WINDING.get(player.getUUID());
		if (hold == null || !hold.hurl || hold.thrown >= 0) {
			if (hold != null && hold.thrown < 0) {
				// winding up a rock in the other hand: that pose stays
				return;
			}
			hurling(player, 1);
			hold = WINDING.get(player.getUUID());
		}
		hold.thrown = player.level().getGameTime();
		player.setAttached(THROW, value(hold.id, hold.charge, THROWING) | MAIN_ARM);
	}

	/** (0.21.7) The Lift dropped what it held before the hurl: the arm comes back down. */
	public static void hurlDropped(ServerPlayer player) {
		Hold hold = WINDING.get(player.getUUID());
		if (hold != null && hold.hurl && hold.thrown < 0) {
			WINDING.remove(player.getUUID());
			player.removeAttached(THROW);
		}
	}

	/** (0.20.103) The charge (ticks) in a {@link #value}. */
	public static int chargeOf(int value) {
		return value >> 2 & 63;
	}

	/** (0.20.103) Momentum left at {@code now}: {@code streak} throws in a row, the last at {@code last} (-1: none), faded since. */
	public static int momentum(int streak, long last, long now) {
		if (last < 0 || now < last) {
			return 0;
		}
		return (int) Math.max(0L, streak - (now - last) / MOMENTUM_FADE);
	}

	/** (0.20.103) How long a charge takes (ticks) with {@code momentum} throws behind it. */
	public static int charge(int momentum) {
		return CHARGE - MOMENTUM_STEP * Math.clamp(momentum, 0, MOMENTUM_MAX);
	}

	/**
	 * (0.20.90) A hold winding up: when it began (game ticks), and when its rock went, or -1; (0.20.103) the momentum it
	 * began with, how long its charge takes, and when its client last spoke.
	 */
	private static final class Hold {
		final Player player;
		final long start;
		long thrown = -1, heard;
		final int id, momentum;
		int charge;
		/** (0.21.7) A gravity user's Lift hurl (the main arm), not a rock. */
		boolean hurl;

		Hold(Player player, long now, int id, int momentum) {
			this.player = player;
			this.start = now;
			this.heard = now;
			this.id = id;
			this.momentum = momentum;
			this.charge = ThrownRocks.charge(momentum);
		}
	}
	/** While a rock's blow is being dealt again (so it isn't again and again). */
	private static boolean redealing;

	private ThrownRocks() {
	}

	public static void init() {
		UseItemCallback.EVENT.register(ThrownRocks::use);
		// an off hand rock is never set down: a use on a block winds up the throw instead
		net.fabricmc.fabric.api.event.player.UseBlockCallback.EVENT.register((player, level, hand, hit) -> use(player, level, hand));
		ServerLivingEntityEvents.ALLOW_DAMAGE.register(ThrownRocks::allowDamage);
		ServerTickEvents.END_SERVER_TICK.register(ThrownRocks::tick);
		PayloadTypeRegistry.serverboundPlay().register(Charge.TYPE, Charge.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Charge.TYPE, (payload, context) -> charge(context.player(), payload.stage()));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			FLYING.clear();
			CRACKS.clear();
			WINDING.clear();
			MOMENTUM.clear();
		});
	}

	private static InteractionResult use(Player player, Level level, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (hand != InteractionHand.OFF_HAND || player.isSpectator() || !FirstTools.rock(stack) || FirstTools.striking(player)) {
			return InteractionResult.PASS;
		}
		if (level.isClientSide() && !player.getCooldowns().isOnCooldown(stack)) {
			// (0.20.90) your own client counts the charge and says when the rock goes (Charge)
			windUpHere.accept(player, hand);
		}
		// never set down, and no vanilla swing: the throw has its own
		return InteractionResult.CONSUME;
	}

	/**
	 * (0.20.90) From your client as you hold use with a rock in the off hand: {@link #WINDING_UP} as the hold begins,
	 * {@link #THROWING} once it has been held {@link #CHARGE} ticks, 0 if it was let go early. (0.20.103) {@link #READY}
	 * once the charge is full (and every {@link #KEEPALIVE} ticks it's held), {@link #THROWING} when use is let go. (The server used to count
	 * the held use itself, but at the air the client never tells it of a use that does nothing on its side, so the charge
	 * only ran when the use landed on a block.)
	 */
	public record Charge(int stage) implements CustomPacketPayload {
		public static final Type<Charge> TYPE = new Type<>(JaymosPowers.id("rock_charge"));
		public static final StreamCodec<ByteBuf, Charge> CODEC = ByteBufCodecs.VAR_INT.map(Charge::new, Charge::stage);

		@Override
		public Type<Charge> type() {
			return TYPE;
		}
	}

	/** (0.20.90) A player's {@link Charge}: the wind-up begun, the rock let go (checked against the server's own count), or the hold let go. */
	private static void charge(ServerPlayer player, int stage) {
		ItemStack stack = player.getOffhandItem();
		long now = player.level().getGameTime();
		Hold hold = WINDING.get(player.getUUID());
		boolean able = !player.isSpectator() && player.isAlive() && !player.isSleeping() && !Downed.passedOut(player) && FirstTools.rock(stack)
			&& !FirstTools.striking(player) && !player.getCooldowns().isOnCooldown(stack);
		if (stage == WINDING_UP) {
			if (!able) {
				return;
			}
			holds = holds + 1 & 0xFFFFF;
			long[] momentum = MOMENTUM.get(player.getUUID());
			int left = momentum == null ? 0 : momentum(Math.toIntExact(momentum[0]), momentum[1], now);
			hold = new Hold(player, now, holds, left);
			WINDING.put(player.getUUID(), hold);
			if (gravity(player)) {
				// (0.21.7) a gravity user's rock goes at once, and flies harder
				hold.thrown = now;
				player.setAttached(THROW, value(hold.id, hold.charge, THROWING));
				player.getCooldowns().addCooldown(stack, COOLDOWN);
				release(player, stack, GRAVITY_SPEED, GRAVITY_SPREAD);
				return;
			}
			player.setAttached(THROW, value(hold.id, hold.charge, WINDING_UP));
			player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ARMOR_EQUIP_GENERIC.value(),
				SoundSource.PLAYERS, 0.4F, 0.7F);
			return;
		}
		if (hold == null || hold.thrown >= 0) {
			return;
		}
		if (stage == READY && able) {
			// (0.20.103) the full charge held: drawn right back, waiting to be let go
			hold.heard = now;
			if (now - hold.start >= hold.charge - CHARGE_SLACK) {
				player.setAttached(THROW, value(hold.id, hold.charge, READY));
			}
			return;
		}
		if (stage != THROWING || !able) {
			// let go early (or the rock's gone): the arm comes back down
			WINDING.remove(player.getUUID());
			player.removeAttached(THROW);
			return;
		}
		if (now - hold.start < hold.charge - CHARGE_SLACK) {
			// too soon by the server's count: not thrown, and the hold is over
			WINDING.remove(player.getUUID());
			player.removeAttached(THROW);
			return;
		}
		// let go: the arm whips through, and follows through (tick clears it)
		hold.thrown = now;
		player.setAttached(THROW, value(hold.id, hold.charge, THROWING));
		// (0.20.103) momentum: the next charge comes sooner
		MOMENTUM.put(player.getUUID(), new long[] {Math.min(MOMENTUM_MAX, hold.momentum + 1), now});
		player.getCooldowns().addCooldown(stack, COOLDOWN);
		release(player, stack, SPEED, SPREAD);
	}

	/**
	 * (0.21.7) Andy: "when a gravity user throws a rock, it flies faster and further and throws instantly". A gravity user
	 * skips the charge: the rock goes the moment use is pressed, at {@link #GRAVITY_SPEED}, straighter.
	 */
	public static boolean gravity(Player player) {
		return com.jaymospowers.core.Powers.has(player, com.jaymospowers.power.gravity.GravityPower.INSTANCE);
	}

	/** The rock leaves the hand where you look. */
	private static void release(ServerPlayer player, ItemStack stack, float speed, float spread) {
		ServerLevel server = (ServerLevel) player.level();
		ItemStack stone = stack.copyWithCount(1);
		Snowball ball = Projectile.spawnProjectileFromRotation((l, owner, s) -> new Snowball(l, owner, s), server, stone, player, 0.0F, speed,
			spread);
		FLYING.add(new Rock(ball, stone, player.getAbilities().instabuild));
		server.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SNOWBALL_THROW, SoundSource.PLAYERS, 0.6F,
			0.5F + server.getRandom().nextFloat() * 0.2F);
		stack.consume(1, player);
	}

	/** (0.20.90) A throw followed through, or a hold never finished (its client gone quiet): the arm comes back down. */
	private static boolean holdOver(Hold hold) {
		Player player = hold.player;
		if (player.isRemoved()) {
			return true;
		}
		long now = player.level().getGameTime();
		boolean over = now < hold.start
			|| (hold.thrown >= 0 ? now - hold.thrown >= FOLLOW_TICKS : now - Math.max(hold.heard, hold.start + hold.charge) > STALE_TICKS);
		if (over) {
			player.removeAttached(THROW);
		}
		return over;
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
		boolean shrugs = shrugsOff(victim);
		boolean unsuspecting = unsuspecting(victim, source.getEntity());
		boolean head = ball.getY() >= victim.getEyeY() - HEAD_REACH;
		boolean hurt;
		redealing = true;
		try {
			hurt = victim.hurtServer(level, source, shrugs ? SHRUGGED_DAMAGE : DAMAGE);
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
	 * (0.20.69) Whether a rock barely troubles {@code victim}: any zombie (the game's {@code Zombie} family, which takes in
	 * husks, drowned, zombie villagers and zombified piglins, and the modded zombies built on it) or one of Kingdoms'
	 * Hornkin ({@code jaymoskingdoms:hornkin*}, {@code jaymoskingdoms:zombified_hornkin}). Kingdoms' guards' rocks use the
	 * same rule ({@code Javelins.rockDamage}).
	 */
	public static boolean shrugsOff(LivingEntity victim) {
		if (victim instanceof Zombie) {
			return true;
		}
		Identifier id = BuiltInRegistries.ENTITY_TYPE.getKey(victim.getType());
		return "jaymoskingdoms".equals(id.getNamespace()) && (id.getPath().startsWith("hornkin") || id.getPath().equals("zombified_hornkin"));
	}

	/**
	 * (0.18.84) Whether {@code victim} never saw the rock coming: asleep; or not in a fight (no target, nobody's hurt them
	 * in {@link #WARY_TICKS}) and the thrower out of their sight (behind them, off to the side, or round a corner).
	 */
	public static boolean unsuspecting(LivingEntity victim, @Nullable Entity thrower) {
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
		// (0.20.90) the wind-up's pose, let go of or thrown
		WINDING.values().removeIf(ThrownRocks::holdOver);
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
