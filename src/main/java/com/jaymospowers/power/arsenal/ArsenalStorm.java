package com.jaymospowers.power.arsenal;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.api.PowerKey;
import com.jaymospowers.core.Fate;
import com.jaymospowers.core.LockOn;
import com.jaymospowers.core.Mastery;
import com.jaymospowers.core.fx.MagicFx;
import com.jaymospowers.ground.FirstTools;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.ChatFormatting;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * <b>Blade Storm</b> (hold V; a tap is still Barrage. Also a slice of the Attendant Blades' order wheel, which is how
 * the Void Arsenal, whose V is its gates, reaches it): for as long as V is held (up to {@link #MAX_TICKS}), weapons whip
 * out of a rift behind the holder's shoulder one after another, each swinging out in a wide loop through what they aim
 * at and back into the rift. A blade cuts everything along its loop and keeps going. The holder is slowed while it runs.
 *
 * <p>Every Attendant Blade out quickens the storm ({@link #gap}); the wing's own weapons and loose rocks take their
 * turns in it. Every third blade is an echo: it comes through half there, and its loop ignores walls. Barrage's cooldown
 * pays for it, more the longer it ran.
 *
 * <p>Only the storm's timing and its weapons are synced ({@link #STORM}, to everyone). Both sides work out every blade's
 * place from the same formula ({@link #place}), so the loops follow the holder and their aim as both move; the server
 * cuts along them, and {@code ArsenalStormRenderer} draws them.
 */
public final class ArsenalStorm {
	// tuning (ticks)
	/** V held this long is a storm; let go sooner and it was a Barrage. */
	public static final int HOLD_TICKS = 6;
	public static final int MAX_TICKS = 120;
	/** From the order wheel there is no key to hold: it runs this long. */
	public static final int WHEEL_TICKS = 80;
	/** One blade every this many ticks, less {@link #GAP_PER_BLADE} for each Attendant Blade out, never under {@link #MIN_GAP}. */
	public static final float BASE_GAP = 5.0F;
	public static final float GAP_PER_BLADE = 0.5F;
	public static final float MIN_GAP = 2.0F;
	/** How long one loop takes, out and back. */
	public static final int LOOP_TICKS = 12;
	public static final double RANGE = 20.0;
	public static final float DAMAGE = 3.0F;
	/** With a relic or a Sword in the Stone in hand. */
	public static final float RELIC_DAMAGE = 4.5F;
	/** One thing is cut at most this often, however many blades pass through it. */
	public static final int HIT_GAP = 8;
	public static final double HIT_RADIUS = 1.3;
	/** Barrage's cooldown afterwards: this, plus {@link #COOLDOWN_PER_TICK} for every tick the storm ran. */
	public static final int COOLDOWN = 200;
	public static final int COOLDOWN_PER_TICK = 3;
	public static final int SLOWNESS = 1;
	/** How many different weapons one storm cycles through. */
	public static final int PALETTE = 8;

	/**
	 * A storm: when it began, when the last blade leaves ({@code Long.MAX_VALUE} while V is held), ticks between blades,
	 * a seed for the loops' tilt, and the weapons it cycles through.
	 */
	public record Storm(long start, long end, float gap, int seed, List<ItemStack> weapons) {
		public static final StreamCodec<RegistryFriendlyByteBuf, Storm> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_LONG, Storm::start,
			ByteBufCodecs.VAR_LONG, Storm::end,
			ByteBufCodecs.FLOAT, Storm::gap,
			ByteBufCodecs.VAR_INT, Storm::seed,
			ItemStack.OPTIONAL_STREAM_CODEC.apply(ByteBufCodecs.list()), Storm::weapons,
			Storm::new);

		/** How many blades have left by game time {@code now}. */
		public int count(double now) {
			double ran = Math.min(now, end) - start;
			return ran < 0.0 ? 0 : 1 + (int) (ran / gap);
		}

		/** How far along its loop blade {@code i} is at {@code now}: under 0 not yet out, over 1 back in the rift. */
		public double along(int i, double now) {
			return (now - start - i * gap) / LOOP_TICKS;
		}

		public ItemStack weapon(int i) {
			return weapons.isEmpty() ? ItemStack.EMPTY : weapons.get(i % weapons.size());
		}

		/** Every third blade: half there, and walls don't stop its loop. */
		public boolean echo(int i) {
			return i % 3 == 2;
		}

		public boolean over(double now) {
			return end != Long.MAX_VALUE && now > end + LOOP_TICKS + 2;
		}
	}

	public static final AttachmentType<Storm> STORM = AttachmentRegistry.create(JaymosPowers.id("arsenal_storm"),
		builder -> builder.syncWith(Storm.STREAM_CODEC, AttachmentSyncPredicate.all()));

	/** When V went down. */
	private static final Map<UUID, Long> PRESSED = new HashMap<>();
	/** When each thing (by entity id) was last cut by this holder's storm. */
	private static final Map<UUID, Map<Integer, Long>> CUT = new HashMap<>();

	private ArsenalStorm() {
	}

	/** V went down or up (not the Void Arsenal's, not with Sneak). True when it was taken: a tap ends as a Barrage. */
	static boolean key(ServerPlayer player, boolean down) {
		UUID id = player.getUUID();
		if (down) {
			PRESSED.put(id, player.level().getGameTime());
			return true;
		}
		if (PRESSED.remove(id) == null) {
			return false;
		}
		Storm storm = player.getAttached(STORM);
		if (storm != null && storm.end() == Long.MAX_VALUE) {
			release(player, storm);
		} else if (storm == null) {
			ArsenalPower.barrage(player);
		}
		return true;
	}

	/** Ticks between blades for this holder now: quicker for every Attendant Blade out. */
	public static float gap(Player player) {
		ArsenalWing.Wing wing = player.getAttached(ArsenalWing.WING);
		return Math.max(MIN_GAP, BASE_GAP - (wing == null ? 0 : wing.blades().size()) * GAP_PER_BLADE);
	}

	/** The storm begins, to run until {@code end}. False when Barrage isn't ready (the holder is told). */
	static boolean start(ServerPlayer player, long end) {
		ServerLevel level = player.level();
		long now = level.getGameTime();
		if (player.getAttached(STORM) != null || Mastery.starterOnly(player) || !Mastery.isUnlocked(player, PowerKey.ABILITY_3)) {
			return false;
		}
		ArsenalPower.Cooldowns cd = ArsenalPower.cooldowns(player);
		if (now < cd.barrage()) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.arsenal.barrage_recharging",
				String.format("%.1f", (cd.barrage() - now) / 20.0)).withStyle(ChatFormatting.GRAY));
			return false;
		}
		boolean corrupted = Fate.isCorrupted(player);
		List<ItemStack> weapons = new ArrayList<>();
		ArsenalWing.Wing wing = player.getAttached(ArsenalWing.WING);
		if (wing != null) {
			// the wing's own weapons and rocks take their turns
			wing.blades().stream().filter(ArsenalWing.Blade::own).forEach(blade -> weapons.add(blade.item().copyWithCount(1)));
		}
		while (weapons.size() < PALETTE) {
			ItemStack blade = ArsenalPool.randomBlade(player.getRandom());
			weapons.add(corrupted ? ArsenalVoid.voidTouched(blade) : blade);
		}
		Storm storm = new Storm(now, end, gap(player), player.getRandom().nextInt(), List.copyOf(weapons));
		player.setAttached(STORM, storm);
		if (end != Long.MAX_VALUE) {
			// from the wheel: its length is known, so it is paid for now
			bill(player, storm, end);
		}
		Mastery.used(player);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ILLUSIONER_CAST_SPELL, SoundSource.PLAYERS, 1.0F, 1.2F);
		return true;
	}

	/** V let go (or time up): no more blades leave, and Barrage's cooldown pays for the ones that did. */
	private static void release(ServerPlayer player, Storm storm) {
		long now = player.level().getGameTime();
		player.setAttached(STORM, new Storm(storm.start(), now, storm.gap(), storm.seed(), storm.weapons()));
		bill(player, storm, now);
	}

	private static void bill(ServerPlayer player, Storm storm, long end) {
		long ran = Math.max(0, end - storm.start());
		player.setAttached(ArsenalPower.COOLDOWNS, ArsenalPower.cooldowns(player).withBarrage(end + COOLDOWN + ran * COOLDOWN_PER_TICK));
	}

	/** Every tick for the holder: V held into a storm, blades leaving, and what they cut. */
	static void tick(ServerPlayer player) {
		UUID id = player.getUUID();
		ServerLevel level = player.level();
		long now = level.getGameTime();
		Long pressed = PRESSED.get(id);
		Storm storm = player.getAttached(STORM);
		if (pressed != null && storm == null && now - pressed == HOLD_TICKS && player.isAlive() && !player.isSpectator()) {
			start(player, Long.MAX_VALUE);
			storm = player.getAttached(STORM);
		}
		if (storm == null) {
			return;
		}
		if (storm.over(now) || !player.isAlive()) {
			player.removeAttached(STORM);
			CUT.remove(id);
			return;
		}
		if (storm.end() == Long.MAX_VALUE && now - storm.start() >= MAX_TICKS) {
			release(player, storm);
			return;
		}
		player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 10, SLOWNESS, true, false, false));
		boolean corrupted = Fate.isCorrupted(player);
		int color = corrupted ? 0x2A1840 : MagicFx.VOID_COLOR;
		float damage = (relicInHand(player) ? RELIC_DAMAGE : DAMAGE) * (corrupted ? ArsenalVoid.DAMAGE_MULTIPLIER : 1.0F);
		DamageSource source = player.damageSources().playerAttack(player);
		Map<Integer, Long> last = CUT.computeIfAbsent(id, key -> new HashMap<>());
		int count = storm.count(now);
		for (int i = Math.max(0, count - 1 - (int) (LOOP_TICKS / storm.gap()) - 1); i < count; i++) {
			double along = storm.along(i, now);
			if (along < 0.0 || along > 1.0) {
				continue;
			}
			Vec3 at = place(player, storm, i, along, 1.0F);
			double before = storm.along(i, now - 1.0);
			if (before < 0.0) {
				// just out: its ribbon, the way it will go
				Vec3 far = place(player, storm, i, 0.5, 1.0F);
				Vec3 bend = place(player, storm, i, 0.25, 1.0F).subtract(at.add(far).scale(0.5));
				MagicFx.stroke(level, at, far, bend, 0.12F, color, LOOP_TICKS);
				level.playSound(null, at.x, at.y, at.z, SoundEvents.TRIDENT_THROW, SoundSource.PLAYERS, 0.45F, 1.2F + level.getRandom().nextFloat() * 0.5F);
			} else if (before < 0.5 && along >= 0.5) {
				// the far end of the swing: a flash
				level.sendParticles(MagicFx.spark(corrupted ? 0x6A4A9A : 0xFFFFFF, 1.2F), at.x, at.y, at.z, 6, 0.2, 0.2, 0.2, 0.15);
			}
			// swept from where it was a tick ago, so a fast blade doesn't step over anything
			Vec3 was = place(player, storm, i, Math.max(0.0, before), 1.0F);
			boolean rock = FirstTools.rock(storm.weapon(i));
			for (LivingEntity living : level.getEntitiesOfClass(LivingEntity.class, new AABB(was, at).inflate(HIT_RADIUS),
				e -> ArsenalWing.fair(player, e))) {
				if (now - last.getOrDefault(living.getId(), -1000L) < HIT_GAP) {
					continue;
				}
				last.put(living.getId(), now);
				living.setInvulnerableTime(0);
				if (living.hurtServer(level, source, rock ? Math.min(damage, ArsenalWing.ROCK_DAMAGE) : damage)) {
					if (corrupted) {
						ArsenalVoid.voidHit(living, player, false);
					}
					level.sendParticles(net.minecraft.core.particles.ParticleTypes.CRIT, at.x, at.y, at.z, 4, 0.2, 0.2, 0.2, 0.2);
					level.playSound(null, at.x, at.y, at.z, SoundEvents.TRIDENT_HIT, SoundSource.PLAYERS, 0.5F, 1.3F);
				}
			}
		}
	}

	private static boolean relicInHand(ServerPlayer player) {
		ItemStack held = player.getMainHandItem();
		return !held.isEmpty() && !Summoned.is(held) && ArsenalRelics.is(held);
	}

	/** The rift the blades leave from and come back to: behind and over the holder's shoulder. */
	public static Vec3 rift(Player player, float partialTick) {
		float yaw = player.getViewYRot(partialTick) * Mth.DEG_TO_RAD;
		Vec3 forward = new Vec3(-Math.sin(yaw), 0.0, Math.cos(yaw));
		Vec3 right = new Vec3(-forward.z, 0.0, forward.x);
		return player.getPosition(partialTick).add(forward.scale(-1.1)).add(right.scale(0.5)).add(0.0, 2.1, 0.0);
	}

	/**
	 * What the loops swing through: the lock-on target, else the first block along the look, else the end of
	 * {@link #RANGE}. An echo's loop ignores the block.
	 */
	public static Vec3 aim(Player player, boolean echo, float partialTick) {
		LivingEntity locked = LockOn.target(player);
		if (locked != null && locked.isAlive()) {
			return locked.getBoundingBox().getCenter();
		}
		Vec3 eye = player.getEyePosition(partialTick);
		Vec3 end = eye.add(player.getViewVector(partialTick).scale(RANGE));
		if (echo) {
			return end;
		}
		BlockHitResult hit = player.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
		return hit.getType() == HitResult.Type.MISS ? end : hit.getLocation();
	}

	/**
	 * Where blade {@code i} is, {@code along} of the way round its loop (0 leaving the rift, 0.5 at its far end, 1 back
	 * in). The loop is an ellipse from the rift to the aim point, tilted by the blade's own seeded angle and spread a
	 * little to one side so a storm covers a crowd; near aims make tight loops, far ones long fast ones. It starts
	 * fast and slows into the far end.
	 */
	public static Vec3 place(Player player, Storm storm, int i, double along, float partialTick) {
		Vec3 from = rift(player, partialTick);
		long hash = mix(storm.seed() * 31L + i);
		double tilt = (hash & 0xFFFF) / 65535.0 * Mth.TWO_PI;
		double spread = ((hash >>> 16 & 0xFF) / 255.0 - 0.5) * 3.0;
		Vec3 to = aim(player, storm.echo(i), partialTick);
		Vec3 line = to.subtract(from);
		double reach = Math.max(4.0, line.length());
		Vec3 u = line.lengthSqr() < 1.0E-4 ? player.getViewVector(partialTick) : line.normalize();
		Vec3 side = u.cross(new Vec3(0.0, 1.0, 0.0));
		side = side.lengthSqr() < 1.0E-4 ? new Vec3(1.0, 0.0, 0.0) : side.normalize();
		Vec3 over = side.cross(u).normalize();
		Vec3 w = side.scale(Math.cos(tilt)).add(over.scale(Math.sin(tilt)));
		double width = Mth.clamp(reach * 0.35, 1.2, 3.5);
		// eased: quick out of the rift, slowing into the far end, quick home
		double s = along < 0.5 ? 0.5 * (1.0 - Math.pow(1.0 - along * 2.0, 2.0)) : 0.5 + 0.5 * Math.pow((along - 0.5) * 2.0, 2.0);
		double angle = s * Mth.TWO_PI;
		double out = (1.0 - Math.cos(angle)) * 0.5;
		return from.add(u.scale(out * reach)).add(w.scale(Math.sin(angle) * width)).add(side.scale(spread * out));
	}

	/** SplitMix64's finaliser: the same number in, the same well-scrambled number out, on every side. */
	private static long mix(long z) {
		z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
		z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
		return z ^ (z >>> 31);
	}

	/** The power went away: the storm with it. */
	static void onLost(ServerPlayer player) {
		PRESSED.remove(player.getUUID());
		CUT.remove(player.getUUID());
		player.removeAttached(STORM);
	}
}
