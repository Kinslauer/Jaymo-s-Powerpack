package com.jaymospowers.combat;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.LockOn;
import com.jaymospowers.core.wounds.Downed;
import com.jaymospowers.core.wounds.ValorSkill;
import com.jaymospowers.stamina.Feathers;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The three disciplines of defence (Andy, 2026-11-22: "fights should be more decisive but also skillful"; the plan is the
 * vault's {@code defence-disciplines}). A player's <b>passive blocks</b>: a blow from the front, or from anywhere while
 * locked on, is caught by itself, {@link #SHARE} of it stopped, and one feather icon at the end of the row turns into that
 * discipline's shield ({@link Feathers#addPenalty}: it can't be spent and doesn't regrow). Once every block is a shield,
 * blows land in full (with a {@link #FLINCH} flinch) unless you guard or parry by hand. A parry gives them back (a perfect
 * one all, a plain one one, {@link Guard#PARRIED}); so do {@link #CALM_TICKS} out of the fight, one every
 * {@link #RETURN_TICKS}.
 * <ul>
 * <li><b>Arms</b> ({@link #ARMS}): the reflex block, the weapon or shield snapping up (today's sparks and splinters,
 * {@link ClashFx}). Bare-handed it stops only {@link #FIST_SHARE}, unless the fists are Valor-coated.</li>
 * <li><b>Ki</b> ({@link #KI}): the ki ward, the body hardening, for {@link #KI_COST} ki. Bare-handed or armed.</li>
 * <li><b>Arcana</b> ({@link #ARCANA}): the ward, a turning of magic, for {@link #MANA_COST} mana. Bare-handed or armed.</li>
 * </ul>
 * Which disciplines a player has, how many blocks each, and the skills (Keen Timing, Snatch, Iron Guard) come from
 * Jaymo's Kingdoms ({@code com.jaymoskingdoms.Disciplines}, asked by reflection). Without it everyone has one Arms block,
 * and a Z Fighter one Ki block instead.
 */
public final class PassiveBlocks {
	public static final int ARMS = 0, KI = 1, ARCANA = 2, KINDS = 3;
	/** The most blocks anyone has, all disciplines together. */
	public static final int MAX = 5;
	/** What a block stops; bare fists (Arms without Valor) only this. */
	public static final float SHARE = 0.6F, FIST_SHARE = 0.1F;
	/** Ki a ki ward takes, and mana a ward takes. */
	public static final float KI_COST = 3.0F;
	public static final int MANA_COST = 2;
	/** The reach round your front a block covers when not locked on (degrees each side, as the guard's). */
	public static final double ANGLE = Guard.GUARD_ANGLE;
	/** Out of the fight (not hit, not swinging) this long, the shields turn back into feathers, one every {@link #RETURN_TICKS}. */
	public static final int CALM_TICKS = 120, RETURN_TICKS = 20;
	/** A clean hit (no block left, no guard): the flinch, in ticks. */
	public static final int FLINCH = 8;

	/**
	 * The owner's blocks, for the HUD: five slots of two bits each (the discipline, 3 for none), in the order they catch,
	 * and above them, which slots are used (a shield). Missing: none.
	 */
	public static final AttachmentType<Integer> STATE = AttachmentRegistry.create(JaymosPowers.id("passive_blocks"),
		builder -> builder.syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.targetOnly()));

	private static final class State {
		/** The slots (disciplines, catching order) and which are used. */
		final int[] slots = new int[MAX];
		int count;
		int used;
		long calmFrom;
		long lastReturn;
		/** No blocks until then (a broken guard). */
		long brokenUntil;
		/** Feathers owed for shields given back, paid once the row's cap has risen to hold them. */
		int owed;
		long owedAt;
	}

	private static final Map<UUID, State> STATES = new HashMap<>();

	private PassiveBlocks() {
	}

	public static void init() {
		Feathers.addPenalty(player -> {
			State s = STATES.get(player.getUUID());
			return s == null ? 0 : Integer.bitCount(s.used) * 2;
		});
		Guard.PARRIED.register((player, attacker, perfect) -> giveBack(player, perfect ? MAX : 1));
		AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (player instanceof ServerPlayer server) {
				fighting(server);
			}
			return InteractionResult.PASS;
		});
		ServerTickEvents.END_SERVER_TICK.register(PassiveBlocks::tick);
		ServerTickEvents.END_SERVER_TICK.register(PassiveBlocks::scramble);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> STATES.remove(handler.player.getUUID()));
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			STATES.clear();
			SCRAMBLES.clear();
		});
	}

	private static State state(ServerPlayer player) {
		return STATES.computeIfAbsent(player.getUUID(), id -> new State());
	}

	// ---- Kingdoms (com.jaymoskingdoms.Disciplines), by reflection ---------------------------------------------------

	private static final @Nullable Method BLOCKS, SPEND, VALOR_FISTS, KEEN, SNATCH, IRON_GUARD, TRAINED, MOB_BROKEN;

	static {
		Method blocks = null, spend = null, valor = null, keen = null, snatch = null, iron = null, trained = null, mobBroken = null;
		if (FabricLoader.getInstance().isModLoaded("jaymoskingdoms")) {
			try {
				Class<?> c = Class.forName("com.jaymoskingdoms.Disciplines");
				blocks = c.getMethod("blocks", ServerPlayer.class);
				spend = c.getMethod("spend", ServerPlayer.class, int.class);
				valor = c.getMethod("valorFists", ServerPlayer.class);
				keen = c.getMethod("keenTiming", ServerPlayer.class);
				snatch = c.getMethod("snatch", ServerPlayer.class);
				iron = c.getMethod("ironGuard", ServerPlayer.class);
				trained = c.getMethod("trained", ServerPlayer.class, int.class);
				mobBroken = Class.forName("com.jaymoskingdoms.MobBlocks").getMethod("broken", net.minecraft.world.entity.Mob.class);
			} catch (ReflectiveOperationException | LinkageError e) {
				// (an older Kingdoms: no disciplines; the defaults below)
			}
		}
		BLOCKS = blocks;
		SPEND = spend;
		VALOR_FISTS = valor;
		KEEN = keen;
		SNATCH = snatch;
		IRON_GUARD = iron;
		TRAINED = trained;
		MOB_BROKEN = mobBroken;
	}

	private static @Nullable Object ask(@Nullable Method method, Object... args) {
		if (method == null) {
			return null;
		}
		try {
			return method.invoke(null, args);
		} catch (ReflectiveOperationException | RuntimeException e) {
			return null;
		}
	}

	/**
	 * {@code player}'s block slots in catching order (disciplines: the inclination's first). Kingdoms decides; without it,
	 * one Arms block (one Ki block for a Z Fighter).
	 */
	static int[] slots(ServerPlayer player) {
		if (ask(BLOCKS, player) instanceof int[] slots) {
			return slots.length > MAX ? java.util.Arrays.copyOf(slots, MAX) : slots;
		}
		return new int[] {zfighter(player) ? KI : ARMS};
	}

	private static boolean zfighter(ServerPlayer player) {
		return com.jaymospowers.core.Powers.has(player, com.jaymospowers.power.zfighter.ZFighterPower.INSTANCE);
	}

	/** Keen Timing's rank (0-3): Kingdoms' skill; 0 without it. */
	public static int keenTiming(ServerPlayer player) {
		return ask(KEEN, player) instanceof Integer rank ? Math.clamp(rank, 0, 3) : 0;
	}

	/** Whether a perfect fist parry takes the foe's weapon into {@code player}'s hand (Kingdoms' Snatch; Monks have it). */
	static boolean snatch(ServerPlayer player) {
		return ask(SNATCH, player) instanceof Boolean b && b;
	}

	/** Iron Guard: a held guard costs less. */
	public static boolean ironGuard(ServerPlayer player) {
		return ask(IRON_GUARD, player) instanceof Boolean b && b;
	}

	private static boolean valorFists(ServerPlayer player) {
		return ask(VALOR_FISTS, player) instanceof Boolean b && b;
	}

	/** Takes a ki ward's ki or a ward's mana; Arms costs nothing. False if there isn't enough. */
	private static boolean pay(ServerPlayer player, int discipline) {
		if (discipline == ARMS || player.isCreative()) {
			return true;
		}
		if (discipline == KI && zfighter(player) && com.jaymospowers.power.zfighter.ZFighterHooks.spendKi(player, KI_COST)) {
			return true;
		}
		return ask(SPEND, player, discipline) instanceof Boolean b && b;
	}

	// ---- catching a blow ---------------------------------------------------------------------------------------------

	/** Whether {@code player}'s hands are bare (no weapon, no shield, no tool to raise). */
	static boolean fists(Player player) {
		ItemStack main = player.getMainHandItem();
		return (main.isEmpty() || com.jaymospowers.boxing.BoxingGear.isGear(main)) && !player.getOffhandItem().has(net.minecraft.core.component.DataComponents.BLOCKS_ATTACKS);
	}

	/**
	 * From the front of the damage chain ({@link Guard#riposte}): a blow on {@code victim}, a player, that a passive block
	 * catches comes back smaller; one that gets past every block flinches them.
	 */
	public static float caught(LivingEntity victim, DamageSource source, float damage) {
		if (!(victim instanceof ServerPlayer player) || damage <= 0.0F || player.isSpectator() || Downed.isDowned(player)) {
			return damage;
		}
		Entity direct = source.getDirectEntity();
		boolean projectile = direct instanceof Projectile || source.is(DamageTypeTags.IS_PROJECTILE);
		boolean melee = !projectile && direct instanceof LivingEntity && direct == source.getEntity();
		if (!projectile && !melee || source.is(DamageTypeTags.BYPASSES_SHIELD) || source.is(DamageTypeTags.IS_EXPLOSION)
			|| source.is(DamageTypeTags.IS_FIRE)) {
			return damage;
		}
		Vec3 from = source.getSourcePosition();
		LivingEntity attacker = source.getEntity() instanceof LivingEntity living && living != player ? living : null;
		boolean locked = attacker != null && LockOn.target(player) == attacker;
		if (from == null || !locked && !inFront(player, from)) {
			return damage;
		}
		State s = state(player);
		long now = player.level().getGameTime();
		s.calmFrom = now;
		if (Guard.raised(player)) {
			// a held guard meets it: never a passive block
			return damage;
		}
		if (now < s.brokenUntil) {
			return damage;
		}
		refresh(player, s);
		for (int i = 0; i < s.count; i++) {
			int kind = s.slots[i];
			if ((s.used & 1 << i) != 0 || !pay(player, kind)) {
				continue;
			}
			s.used |= 1 << i;
			sync(player, s);
			boolean bare = kind == ARMS && fists(player) && !valorFists(player) && !ValorSkill.pierces(source);
			show(player, kind, attacker, from);
			ask(TRAINED, player, kind);
			return damage * (1.0F - (bare ? FIST_SHARE : SHARE));
		}
		flinch(player);
		return damage;
	}

	private static boolean inFront(ServerPlayer player, Vec3 from) {
		Vec3 to = from.subtract(player.position());
		Vec3 flat = new Vec3(to.x, 0.0, to.z);
		if (flat.lengthSqr() < 1.0E-4) {
			return true;
		}
		Vec3 look = Vec3.directionFromRotation(0.0F, player.getYRot());
		return flat.normalize().dot(look) >= Math.cos(Math.toRadians(ANGLE));
	}

	/** The catch, as it looks: sparks and splinters off the guard (Arms), a flare of ki, a ring of runes. */
	private static void show(ServerPlayer player, int kind, @Nullable LivingEntity attacker, Vec3 from) {
		ServerLevel level = player.level();
		Vec3 eye = player.getEyePosition();
		Vec3 toward = from.subtract(eye);
		Vec3 at = eye.add((toward.lengthSqr() > 1.0E-4 ? toward.normalize() : player.getLookAngle()).scale(0.6)).add(0.0, -0.3, 0.0);
		switch (kind) {
			case KI -> {
				level.sendParticles(com.jaymospowers.core.fx.MagicFx.wisp(0xFFD54A, 1.3F), at.x, at.y, at.z, 10, 0.2, 0.25, 0.2, 0.05);
				level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 0.4F, 1.8F);
			}
			case ARCANA -> {
				level.sendParticles(ParticleTypes.ENCHANT, at.x, at.y, at.z, 24, 0.25, 0.25, 0.25, 0.6);
				level.sendParticles(com.jaymospowers.core.fx.MagicFx.wisp(0x9CC8FF, 1.2F), at.x, at.y, at.z, 8, 0.25, 0.25, 0.25, 0.02);
				level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.6F, 1.4F);
			}
			default -> {
				ItemStack guard = fists(player) ? player.getMainHandItem() : !player.getOffhandItem().isEmpty()
					&& player.getOffhandItem().has(net.minecraft.core.component.DataComponents.BLOCKS_ATTACKS) ? player.getOffhandItem() : player.getMainHandItem();
				ClashFx.met(level, player, guard, attacker, from, ClashFx.BLOCK_SPARKS, ClashFx.BLOCK_SCALE, 2);
				level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 0.5F, 1.3F);
			}
		}
	}

	/** A clean hit: no sprinting, the swing cut short. */
	private static void flinch(ServerPlayer player) {
		player.setSprinting(false);
		player.resetAttackStrengthTicker();
		player.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.SLOWNESS, FLINCH, 1, false, false));
	}

	/** Swinging is fighting: the calm clock starts over. */
	private static void fighting(ServerPlayer player) {
		state(player).calmFrom = player.level().getGameTime();
	}

	/** The guard broke: no passive blocks until it mends. */
	static void broken(ServerPlayer player, int ticks) {
		state(player).brokenUntil = player.level().getGameTime() + ticks;
	}

	/** {@code count} shields turn back into feathers (a parry: one, a perfect one {@link #MAX}). */
	static void giveBack(ServerPlayer player, int count) {
		State s = STATES.get(player.getUUID());
		if (s == null || s.used == 0) {
			return;
		}
		int back = 0;
		for (int i = s.count - 1; i >= 0 && back < count; i--) {
			if ((s.used & 1 << i) != 0) {
				s.used &= ~(1 << i);
				back++;
			}
		}
		sync(player, s);
		// the feathers they stood for, paid once the row's cap has risen (the next feathers tick)
		s.owed += back * 2;
		s.owedAt = player.level().getGameTime() + 2;
	}

	/** The slots Kingdoms gives now; a slot that's gone takes its shield with it. */
	private static void refresh(ServerPlayer player, State s) {
		int[] slots = slots(player);
		boolean changed = slots.length != s.count;
		for (int i = 0; i < slots.length && !changed; i++) {
			changed = slots[i] != s.slots[i];
		}
		if (!changed) {
			return;
		}
		s.count = slots.length;
		System.arraycopy(slots, 0, s.slots, 0, slots.length);
		s.used &= (1 << s.count) - 1;
		sync(player, s);
	}

	private static void tick(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			State s = state(player);
			long now = player.level().getGameTime();
			if (now % 20 == 0) {
				refresh(player, s);
			}
			if (s.used != 0 && now - s.calmFrom >= CALM_TICKS && now - s.lastReturn >= RETURN_TICKS) {
				s.lastReturn = now;
				giveBack(player, 1);
			}
			if (s.owed > 0 && now >= s.owedAt) {
				Feathers.refund(player, s.owed);
				s.owed = 0;
			}
			if (player.getAttached(STATE) == null) {
				sync(player, s);
			}
		}
	}

	private static void sync(ServerPlayer player, State s) {
		player.setAttached(STATE, pack(s));
	}

	static int pack(State s) {
		int packed = 0;
		for (int i = 0; i < MAX; i++) {
			packed |= (i < s.count ? s.slots[i] : 3) << i * 2;
		}
		return packed | (s.used & 0x1F) << 10;
	}

	/** Slot {@code i}'s discipline from the synced state ({@code -1}: no slot). */
	public static int slot(int packed, int i) {
		int kind = packed >> i * 2 & 3;
		return kind == 3 ? -1 : kind;
	}

	/** Whether slot {@code i} is used (a shield) in the synced state. */
	public static boolean used(int packed, int i) {
		return (packed >> 10 + i & 1) != 0;
	}

	/** A parry of a mob's blow takes all its passive blocks (Kingdoms' MobBlocks). */
	static void foeParried(@Nullable LivingEntity attacker) {
		if (attacker instanceof net.minecraft.world.entity.Mob mob) {
			ask(MOB_BROKEN, mob);
		}
	}

	// ---- fists: the disarm -----------------------------------------------------------------------------------------

	/**
	 * A fist parry knocks the attacker's weapon out of its hand: to the floor at its feet, or with Snatch on a perfect one,
	 * straight into {@code player}'s empty hand.
	 */
	static void disarm(ServerPlayer player, LivingEntity attacker, boolean perfect) {
		ItemStack held = attacker.getMainHandItem();
		if (held.isEmpty() || !fists(player)) {
			return;
		}
		ServerLevel level = player.level();
		attacker.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		if (perfect && snatch(player) && player.getMainHandItem().isEmpty()) {
			player.setItemInHand(InteractionHand.MAIN_HAND, held);
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.8F, 0.8F);
		} else {
			ItemEntity item = attacker.spawnAtLocation(level, held, attacker.getBbHeight() * 0.5F);
			if (item != null) {
				item.setPickUpDelay(DROP_PICKUP_DELAY);
				level.sendParticles(ParticleTypes.ENCHANTED_HIT, item.getX(), item.getY() + 0.3, item.getZ(), 8, 0.15, 0.15, 0.15, 0.1);
				if (attacker instanceof net.minecraft.world.entity.Mob mob) {
					// it scrambles to get it back
					SCRAMBLES.add(new Scramble(mob, item, level.getGameTime() + SCRAMBLE_TICKS));
				}
			}
		}
		level.playSound(null, attacker.getX(), attacker.getY(), attacker.getZ(), SoundEvents.ITEM_BREAK.value(), SoundSource.PLAYERS, 0.4F, 1.6F);
	}

	/** A dropped weapon can't be picked up for this long (ticks). */
	static final int DROP_PICKUP_DELAY = 20;
	/** A disarmed mob goes after its weapon this long (ticks), and takes it up within this reach (blocks). */
	static final int SCRAMBLE_TICKS = 200;
	static final double SCRAMBLE_REACH = 1.6;

	/** A disarmed mob going after its dropped weapon. */
	private record Scramble(net.minecraft.world.entity.Mob mob, ItemEntity item, long until) {
	}

	private static final java.util.List<Scramble> SCRAMBLES = new java.util.ArrayList<>();

	/** Each tick: a disarmed mob runs to its weapon and takes it back up, if nobody got there first. */
	private static void scramble(MinecraftServer server) {
		if (SCRAMBLES.isEmpty()) {
			return;
		}
		SCRAMBLES.removeIf(s -> {
			net.minecraft.world.entity.Mob mob = s.mob();
			ItemEntity item = s.item();
			if (!mob.isAlive() || !item.isAlive() || Downed.isDowned(mob) || !mob.getMainHandItem().isEmpty() || mob.level() != item.level()
				|| mob.level().getGameTime() > s.until()) {
				return true;
			}
			if (mob.distanceTo(item) <= SCRAMBLE_REACH && !item.hasPickUpDelay()) {
				mob.setItemInHand(InteractionHand.MAIN_HAND, item.getItem().copy());
				item.discard();
				return true;
			}
			if (mob.tickCount % 10 == 0) {
				mob.getNavigation().moveTo(item, 1.2);
			}
			return false;
		});
	}
}
