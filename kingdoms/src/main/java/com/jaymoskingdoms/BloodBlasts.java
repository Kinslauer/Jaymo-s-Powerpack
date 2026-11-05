package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * (0.7.554) Andy: "blood Sorcerers should also be able to conjure small blasts of their blood by holding J when near it
 * to gather, and they build a reservoir of that blood, that fire like z fighter ki blasts after holding j with an empty
 * hand." A Blood Sorcerer's {@code J}, held (not sneaking):
 * <ul>
 * <li><b>Gather</b> (begun looking at a blood source: a bleeding creature, the stains on the ground, Kitchen's blood
 * grates; Andy: "J only collects when looking at a blood source, and when collecting you dont fire"): what they look at
 * is drawn up to their hands in threads of red, far faster than the pool's own slow drinking, into their reservoir
 * ({@link Hemomancy#RESERVOIR}, the bar by the hotbar). A gathering hold never fires.</li>
 * <li><b>Blood blasts:</b> begun looking at anything else, empty-handed, after a short windup (a ball of blood swells in each palm) the reservoir is
 * thrown as a barrage of small blood blasts, the hands taking turns like a Z Fighter's Ki Barrage, for as long as J is
 * held and the reservoir lasts. Each hurts, opens a cut (more blood to gather) and splashes a stain where it bursts. Paid
 * only from the reservoir, never the body. Still forbidden magic: a barrage counts as one cast (corruption, the Grue,
 * a crime where seen) and stokes a vampire's fever.</li>
 * <li>A <b>tap</b> of J is still the Imbue ({@link Spellcraft#imbue}), and sneak and J the keys ({@link Hemomancy#key}).</li>
 * </ul>
 */
public final class BloodBlasts {
	/** Let go before this (ticks) and it was a tap: the Imbue. */
	static final int TAP_TICKS = 6;
	/** Gathering: how often (ticks); how far you look; how far round the spot looked at; how much of each stain a pulse; what each cut on a bleeder near gives; the most a pulse. */
	static final int GATHER_EVERY = 5;
	static final double GATHER_RANGE = 8.0, GATHER_SPOT = 1.75;
	static final float GATHER_STAIN_EACH = 0.2F, GATHER_PER_CUT = 0.35F, GATHER_MOST = 1.5F;
	/** A blood grate's level a second at most while gathering (Kitchen). */
	static final int GATHER_GRATE_EVERY = 20;
	/** The barrage: the windup (ticks), a blast every so many ticks, what each costs from the reservoir. */
	static final int WINDUP = 12, EVERY = 5;
	static final float BLAST_COST = 1.0F;
	/** A blast: its damage (a vampire's harder, Hemomancy#power), speed (blocks a tick), life (ticks), spread, knockback. */
	static final float BLAST_DAMAGE = 4.0F;
	static final double BLAST_SPEED = 1.5, SPREAD = 0.04, KNOCKBACK = 0.35;
	static final int BLAST_LIFE = 30;
	/** The stain a blast leaves where it bursts. */
	static final float BLAST_STAIN = 0.35F;

	/** Player → server: J down or up, for a Blood Sorcerer not sneaking. */
	public record Hold(boolean down) implements CustomPacketPayload {
		public static final Type<Hold> TYPE = new Type<>(JaymosKingdoms.id("blood_hold"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Hold> CODEC = CustomPacketPayload.codec(
			(p, buf) -> buf.writeBoolean(p.down), buf -> new Hold(buf.readBoolean()));

		@Override
		public Type<Hold> type() {
			return TYPE;
		}
	}

	/** One hold of J: when it began (game time); blasts fired; whether the barrage has counted as a cast. */
	private static final class Holding {
		final long from;
		int fired;
		boolean counted, dry, gathering;

		Holding(long from) {
			this.from = from;
		}
	}

	private static final class Blast {
		final ServerLevel level;
		final UUID owner;
		Vec3 pos, velocity;
		final float damage;
		int age;
		final Set<Integer> struck = new HashSet<>();

		Blast(ServerLevel level, UUID owner, Vec3 pos, Vec3 velocity, float damage) {
			this.level = level;
			this.owner = owner;
			this.pos = pos;
			this.velocity = velocity;
			this.damage = damage;
		}
	}

	private static final Map<UUID, Holding> HOLDING = new HashMap<>();
	private static final List<Blast> BLASTS = new ArrayList<>();

	private BloodBlasts() {
	}

	static void register() {
		PayloadTypeRegistry.serverboundPlay().register(Hold.TYPE, Hold.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Hold.TYPE, (payload, context) -> hold(context.player(), payload.down()));
		ServerTickEvents.END_SERVER_TICK.register(BloodBlasts::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			HOLDING.clear();
			BLASTS.clear();
		});
	}

	/** Holding J to gather or throw (on the server). */
	static boolean holding(Player player) {
		return HOLDING.containsKey(player.getUUID());
	}

	private static void hold(ServerPlayer player, boolean down) {
		long now = player.level().getGameTime();
		if (down) {
			if (Hemomancy.bloodMage(player) && player.isAlive()) {
				HOLDING.put(player.getUUID(), new Holding(now));
			} else {
				// (not one any more: an ordinary press)
				Spellcraft.imbue(player, false);
			}
			return;
		}
		Holding h = HOLDING.remove(player.getUUID());
		if (h != null && now - h.from < TAP_TICKS) {
			Spellcraft.imbue(player, false);
		} else if (h != null && h.fired > 0 && player.level() instanceof ServerLevel level) {
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.HONEY_BLOCK_SLIDE, SoundSource.PLAYERS, 0.6F, 0.6F);
		}
	}

	private static void tick(MinecraftServer server) {
		for (Iterator<Map.Entry<UUID, Holding>> it = HOLDING.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<UUID, Holding> e = it.next();
			ServerPlayer player = server.getPlayerList().getPlayer(e.getKey());
			if (player == null || !player.isAlive() || !Hemomancy.bloodMage(player) || !(player.level() instanceof ServerLevel level)) {
				it.remove();
				continue;
			}
			tickHold(level, player, e.getValue());
		}
		for (Iterator<Blast> it = BLASTS.iterator(); it.hasNext(); ) {
			Blast b = it.next();
			ServerPlayer owner = server.getPlayerList().getPlayer(b.owner);
			if (owner == null || owner.level() != b.level || !fly(owner, b)) {
				it.remove();
			}
		}
	}

	private static void tickHold(ServerLevel level, ServerPlayer player, Holding h) {
		long held = level.getGameTime() - h.from;
		if (held < TAP_TICKS) {
			// too soon to tell a tap from a hold
			return;
		}
		Vec3 look = player.getLookAngle();
		if (held == TAP_TICKS) {
			// (Andy: "J only collects when looking at a blood source, and when collecting you dont fire") what the hold
			// is for is settled as it begins: looking at blood it can draw, it gathers for as long as it's held, and never fires
			h.gathering = gather(level, player, look, true);
			if (!h.gathering && !player.getMainHandItem().isEmpty()) {
				return;
			}
		}
		if (h.gathering) {
			if ((held - TAP_TICKS) % GATHER_EVERY == 0 && held > TAP_TICKS) {
				gather(level, player, look, (held - TAP_TICKS) % GATHER_GRATE_EVERY == 0);
			}
			return;
		}
		if (!player.getMainHandItem().isEmpty()) {
			return;
		}
		float pool = Hemomancy.pool(player);
		float cost = cost(player);
		if (held < WINDUP) {
			if (pool >= cost || player.isCreative()) {
				float wind = (held - TAP_TICKS) / (float) (WINDUP - TAP_TICKS);
				for (boolean left : new boolean[] {false, true}) {
					Vec3 p = palm(player, look, left);
					level.sendParticles(Hemomancy.BLOOD_MOTE, p.x, p.y, p.z, 1 + (int) (wind * 2), 0.05, 0.05, 0.05, 0.0);
				}
				if (held == TAP_TICKS) {
					level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 0.8F, 1.4F);
				}
			}
			return;
		}
		long firing = held - WINDUP;
		boolean next = (firing / EVERY) % 2 == 0;
		Vec3 forming = palm(player, look, next);
		level.sendParticles(Hemomancy.BLOOD_MOTE, forming.x, forming.y, forming.z, 1, 0.04, 0.04, 0.04, 0.0);
		if (firing % EVERY != 0) {
			return;
		}
		if (!player.isCreative() && pool < cost) {
			if (!h.dry) {
				// the reservoir is dry: the hands close on nothing
				h.dry = true;
				level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.HONEY_BLOCK_SLIDE, SoundSource.PLAYERS, 0.7F, 0.5F);
			}
			return;
		}
		if (!h.counted) {
			// (the spirit stays your hand: the Grue is near)
			if (ForbiddenArts.stayed(player)) {
				return;
			}
			h.counted = true;
			Spellcraft.afterCast(player, level, "blood");
			if (VampirePlayers.vampire(player)) {
				BloodFever.stoke(player, Hemomancy.VAMPIRE_FEVER);
			}
		}
		h.dry = false;
		if (!player.isCreative()) {
			Hemomancy.setPool(player, pool - cost);
		}
		boolean left = !next;
		RandomSource random = level.getRandom();
		Vec3 aim = look.add((random.nextDouble() - 0.5) * SPREAD * 2, (random.nextDouble() - 0.5) * SPREAD * 2,
			(random.nextDouble() - 0.5) * SPREAD * 2).normalize();
		BLASTS.add(new Blast(level, player.getUUID(), palm(player, look, left), aim.scale(BLAST_SPEED), BLAST_DAMAGE * Hemomancy.power(player)));
		h.fired++;
		player.swing(left ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.HONEY_BLOCK_BREAK, SoundSource.PLAYERS, 0.7F,
			1.3F + random.nextFloat() * 0.3F);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BREEZE_SHOOT, SoundSource.PLAYERS, 0.35F, 0.7F);
	}

	private static float cost(ServerPlayer player) {
		return BLAST_COST * (VampirePlayers.vampire(player) ? Hemomancy.VAMPIRE_COST : 1.0F);
	}

	/**
	 * One pulse of gathering from the blood source {@code player} looks at, its thread rising to the hands: a bleeding
	 * creature in sight, else the stains (and Kitchen's grates, when {@code grates}) round the spot on the ground they
	 * look at, within {@link #GATHER_RANGE}. True if there was blood there to draw (the reservoir had room for it).
	 */
	private static boolean gather(ServerLevel level, ServerPlayer player, Vec3 look, boolean grates) {
		float pool = Hemomancy.pool(player);
		float room = Math.min(GATHER_MOST, Hemomancy.POOL_MAX - pool);
		if (room <= 0.0F) {
			return false;
		}
		Vec3 hands = player.getEyePosition().add(look.scale(0.5)).add(0.0, -0.45, 0.0);
		float drawn = 0.0F;
		LivingEntity sighted = Arcana.sighted(player);
		int cuts = sighted == null || player.distanceTo(sighted) > GATHER_RANGE ? 0 : PowersCompat.cuts(sighted);
		if (cuts > 0) {
			drawn = Math.min(room, cuts * GATHER_PER_CUT);
			Hemomancy.rise(level, sighted.getBoundingBox().getCenter(), hands);
		} else {
			Vec3 eye = player.getEyePosition();
			BlockHitResult ground = level.clip(new ClipContext(eye, eye.add(look.scale(GATHER_RANGE)), ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
			if (ground.getType() == HitResult.Type.MISS) {
				return false;
			}
			Vec3 at = ground.getLocation();
			drawn = Hemomancy.STAIN_BLOOD * PowersCompat.drawStains(level, at, GATHER_SPOT, GATHER_STAIN_EACH, room / Hemomancy.STAIN_BLOOD,
				from -> Hemomancy.rise(level, from, hands));
			if (grates && Hemomancy.POOL_MAX - pool - drawn >= Hemomancy.GRATE_BLOOD) {
				drawn += Hemomancy.GRATE_BLOOD * KitchenCompat.drawGrates(level, at, 1, 1, from -> Hemomancy.rise(level, from, hands));
			}
		}
		if (drawn <= 0.0F) {
			return false;
		}
		Hemomancy.setPool(player, pool + drawn);
		if (level.getRandom().nextInt(3) == 0) {
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.HONEY_DRINK.value(), SoundSource.PLAYERS, 0.5F, 0.6F);
		}
		return true;
	}

	/** Where a palm is: in front of the chest, to that side. */
	private static Vec3 palm(Player player, Vec3 look, boolean left) {
		Vec3 flat = new Vec3(look.x, 0.0, look.z);
		Vec3 side = flat.lengthSqr() < 1.0E-4 ? new Vec3(1.0, 0.0, 0.0) : flat.normalize().cross(new Vec3(0.0, 1.0, 0.0));
		return player.getEyePosition().add(look.scale(0.6)).add(side.scale(left ? -0.35 : 0.35)).add(0.0, -0.4, 0.0);
	}

	/** One tick of a blast's flight; false once it's burst or spent. */
	private static boolean fly(ServerPlayer owner, Blast b) {
		ServerLevel level = b.level;
		Vec3 from = b.pos;
		Vec3 to = from.add(b.velocity);
		BlockHitResult block = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, owner));
		Vec3 end = block.getType() == HitResult.Type.MISS ? to : block.getLocation();
		EntityHitResult hit = ProjectileUtil.getEntityHitResult(owner, from, end, new AABB(from, end).inflate(1.0),
			e -> e instanceof LivingEntity l && l.isAlive() && !e.isSpectator() && !(e instanceof ArmorStand) && e != owner && !b.struck.contains(e.getId())
				&& !Thralls.refuses(owner, l),
			from.distanceToSqr(end));
		if (hit != null && hit.getEntity() instanceof LivingEntity target) {
			target.setInvulnerableTime(0);
			if (target.hurtServer(level, level.damageSources().indirectMagic(owner, owner), b.damage)) {
				PowersCompat.cut(target);
			}
			Vec3 push = b.velocity.normalize().scale(KNOCKBACK);
			target.push(push.x, 0.1, push.z);
			target.syncVelocity = true;
			burst(level, hit.getLocation());
			return false;
		}
		// the trail: dark blood with a bright core
		Vec3 step = end.subtract(from);
		for (int i = 0; i < 3; i++) {
			Vec3 p = from.add(step.scale(i / 3.0));
			level.sendParticles(Hemomancy.BLOOD_MOTE, true, false, p.x, p.y, p.z, 1, 0.03, 0.03, 0.03, 0.0);
		}
		level.sendParticles(Hemomancy.DARK_MOTE, true, false, end.x, end.y, end.z, 1, 0.02, 0.02, 0.02, 0.0);
		if (block.getType() != HitResult.Type.MISS) {
			burst(level, block.getLocation());
			return false;
		}
		b.pos = to;
		// a little weight to it: blood falls
		b.velocity = b.velocity.add(0.0, -0.01, 0.0);
		return ++b.age < BLAST_LIFE;
	}

	/** A blast bursting at {@code at}: a splash, and a stain on the ground under it. */
	private static void burst(ServerLevel level, Vec3 at) {
		level.sendParticles(Hemomancy.BLOOD_MOTE, at.x, at.y, at.z, 8, 0.2, 0.2, 0.2, 0.05);
		level.sendParticles(PowersCompat.blood(Hemomancy.CRIMSON, 1.0F), at.x, at.y, at.z, 4, 0.15, 0.15, 0.15, 0.1);
		PowersCompat.stain(level, Hemomancy.CRIMSON, at.x, at.y + 0.5, at.z, BLAST_STAIN);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.SLIME_SQUISH_SMALL, SoundSource.PLAYERS, 0.6F, 0.8F);
	}
}
