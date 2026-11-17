package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Class trees patch 8, the extras of the casters (docs/ClassTrees-Plan.md).
 *
 * <p><b>Sorcerer.</b> Element's Hide (15% less harm from their own element a rank), Quick Glyph (the sigil's wait a third
 * shorter), Sigil Chain (a sigil that bursts on a foe jumps to one more within four blocks at half strength), Blade Memory
 * (an imbued blade put away stops burning down until it is drawn; worked in {@link Spellcraft}) and Wellspring (mana comes
 * back twice as fast standing in their element).
 *
 * <p><b>Mage.</b> The second crossroads Scholar (a scroll is learned in half the study) or Battlemage (a staff strikes
 * like a mace, and a cast leaves the shield on their arm up).
 *
 * <p><b>Warlock.</b> Dark Sight (they see in the dark while they owe), Borrowed Time (once a rest, the blow that would put
 * them down goes on the account instead), the second crossroads Tithe (each foe killed while owing pays part of the debt)
 * or Usury (casts a fifth harder while owing, and the debt comes a fifth sooner), and Hand of the Patron (a debt settled
 * gives one cast for nothing after the next rest).
 */
public final class ArcaneExtras {
	// ---- Sorcerer ----
	/** Element's Hide: the share of their own element's harm turned, each rank. */
	static final float HIDE_PER_RANK = 0.15F;
	/** Quick Glyph: the share of the sigil's wait taken off. */
	static final float QUICK = 1.0F / 3.0F;
	/** Sigil Chain: how far the jump (blocks), and the share of the burst the second foe takes. */
	static final double CHAIN_RANGE = 4.0;
	static final float CHAIN_SHARE = 0.5F;
	/** Wellspring: how many times as fast mana comes back, and how high open sky counts as open wind. */
	static final float WELL = 2.0F;
	static final double WELL_WIND = 96.0;
	// ---- Mage ----
	/** Learning a scroll: how long it is studied (ticks), and the share of that a Scholar needs. */
	static final int STUDY_TICKS = 60;
	static final float SCHOLAR = 0.5F;
	/** Battlemage: what a staff's blow deals at least (a mace's), and how long after a cast the shield on the arm still turns blows (ticks). */
	static final float MACE = 6.0F;
	static final int GUARD_TICKS = 30;
	// ---- Warlock ----
	/** Borrowed Time: the health they are left on, and what an open debt grows by (casts counted against the grace, days sooner). */
	static final float BORROWED_HEALTH = 4.0F;
	static final int BORROWED_CASTS = 1, BORROWED_DAYS = 1;
	/** Tithe: the foes whose deaths pay a debt off. */
	static final int TITHE_KILLS = 5;
	/** Usury: how much harder casts hit while owing, and how much sooner the next debt comes. */
	static final float USURY = 1.2F;

	public static final String HIDE = "sor_hide", QUICK_KEY = "sor_quick", CHAIN = "sor_chain", MEMORY = "sor_memory", WELL_KEY = "sor_well";
	public static final String SCHOLAR_KEY = "mage_scholar", BATTLEMAGE = "mage_battle";
	public static final String SIGHT = "wlk_sight", TIME = "wlk_time", TITHE = "wlk_tithe", USURY_KEY = "wlk_usury", HAND = "wlk_hand";
	/** Borrowed Time's once-a-rest pool ({@link Rests}). */
	static final String TIME_POOL = "borrowed_time";

	/** Tithe: foes killed against the open debt. Saved, kept through death. */
	static final AttachmentType<Integer> TITHED = AttachmentRegistry.create(JaymosKingdoms.id("pact_tithe"), b -> b.persistent(Codec.INT).copyOnDeath());
	/** Hand of the Patron: 1 a debt was settled (the next rest opens the hand), 2 the free cast is ready. Saved, kept through death. */
	static final AttachmentType<Integer> HAND_STATE = AttachmentRegistry.create(JaymosKingdoms.id("pact_hand"), b -> b.persistent(Codec.INT).copyOnDeath());

	/** Battlemage: until when each Mage's shield still turns blows after a cast. */
	private static final Map<UUID, Long> GUARD = new HashMap<>();

	private ArcaneExtras() {
	}

	static void register() {
		JaymosKingdoms.LOGGER.debug("class tree extras attachments: {} {}", TITHED, HAND_STATE);
		ServerTickEvents.END_SERVER_TICK.register(ArcaneExtras::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> GUARD.clear());
		// Borrowed Time: before Jaymo's Powers' own downing (which waits to the last)
		ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> !(entity instanceof ServerPlayer warlock && borrowTime(warlock)));
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> !(entity instanceof ServerPlayer mage && guarded(mage, source)));
		// Tithe: a foe killed while owing
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof Enemy && source.getEntity() instanceof ServerPlayer warlock && ClassTrees.has(warlock, TITHE) > 0 && Warlocks.pact(warlock).owing()) {
				tithe(warlock);
			}
		});
	}

	// ---- Sorcerer -------------------------------------------------------------------------------------------------------------

	/** Quick Glyph: what the sigil's wait is multiplied by. */
	static float glyph(Player player) {
		return 1.0F - QUICK * Math.min(1.0F, ClassTrees.power(player, QUICK_KEY));
	}

	/** Sigil Chain: {@code owner}'s sigil of {@code element} burst on {@code struck}; it jumps from {@code from} to one more foe near, at half strength. */
	static void chain(ServerLevel level, ServerPlayer owner, String element, Vec3 from, Set<LivingEntity> struck, float damage) {
		float chain = Math.min(1.0F, ClassTrees.power(owner, CHAIN));
		if (chain <= 0.0F || struck.isEmpty()) {
			return;
		}
		LivingEntity next = null;
		for (LivingEntity foe : CombatArts.foes(level, owner, new AABB(from, from).inflate(CHAIN_RANGE))) {
			if (!struck.contains(foe) && foe.position().distanceToSqr(from) <= CHAIN_RANGE * CHAIN_RANGE
				&& (next == null || foe.position().distanceToSqr(from) < next.position().distanceToSqr(from))) {
				next = foe;
			}
		}
		if (next == null) {
			return;
		}
		int colour = Absorption.colour(element);
		Fx.beam(level, from, next.getBoundingBox().getCenter(), 0.06F, colour, 8);
		Fx.blast(level, next.getBoundingBox().getCenter(), 0.8F, colour);
		next.setInvulnerableTime(0);
		next.hurtServer(level, level.damageSources().indirectMagic(owner, owner), damage * CHAIN_SHARE * chain);
		Spellcraft.strike(level, owner, next, element, true);
	}

	/** Wellspring: what {@code player}'s mana regeneration is multiplied by, standing in their element. */
	static float wellspring(Player player) {
		if (!(player instanceof ServerPlayer sp) || !(sp.level() instanceof ServerLevel level)) {
			return 1.0F;
		}
		float well = Math.min(1.0F, ClassTrees.power(sp, WELL_KEY));
		String element = well > 0.0F ? Spellcraft.element(sp) : "";
		if (element.isEmpty() || !inElement(level, sp, element)) {
			return 1.0F;
		}
		return 1.0F + (WELL - 1.0F) * well;
	}

	/** Standing in one's element: where it trickles in by itself ({@link Absorption#inElement}), and rain for the storm, open sky up high for the wind, soul ground for death. */
	private static boolean inElement(ServerLevel level, ServerPlayer player, String element) {
		if (Absorption.inElement(level, player, element)) {
			return true;
		}
		BlockPos at = player.blockPosition();
		return switch (element) {
			case "lightning" -> level.isRainingAt(at.above());
			case "air" -> level.canSeeSky(at) && player.getY() >= WELL_WIND;
			case "death" -> level.getBlockState(at.below()).is(BlockTags.SOUL_FIRE_BASE_BLOCKS) || level.getBlockState(at).is(BlockTags.SOUL_FIRE_BASE_BLOCKS);
			default -> false;
		};
	}

	/** Whether {@code source} is harm of {@code element}'s own kind. */
	private static boolean ofElement(DamageSource source, String element, ServerPlayer sorcerer) {
		// another of their element's spell, or their own burst come back on them
		if (source.is(DamageTypes.INDIRECT_MAGIC) && source.getEntity() instanceof ServerPlayer caster && Spellcraft.element(caster).equals(element)) {
			return true;
		}
		return switch (element) {
			case "fire" -> source.is(DamageTypeTags.IS_FIRE);
			case "water" -> source.is(DamageTypeTags.IS_DROWNING);
			case "ice" -> source.is(DamageTypeTags.IS_FREEZING);
			case "lightning" -> source.is(DamageTypeTags.IS_LIGHTNING);
			case "earth" -> source.is(DamageTypes.FALLING_BLOCK) || source.is(DamageTypes.FALLING_ANVIL) || source.is(DamageTypes.FALLING_STALACTITE)
				|| source.is(DamageTypes.STALAGMITE) || source.is(DamageTypes.IN_WALL);
			case "air" -> source.is(DamageTypes.FLY_INTO_WALL) || source.is(DamageTypes.WIND_CHARGE);
			case "death" -> source.is(DamageTypes.WITHER) || source.is(DamageTypes.WITHER_SKULL);
			case "restoration" -> source.is(DamageTypes.MAGIC);
			default -> false;
		};
	}

	// ---- Mage -----------------------------------------------------------------------------------------------------------------

	/** How long {@code reader} studies a scroll to learn it for good (ticks): half for a Scholar. */
	static int study(LivingEntity reader) {
		float scholar = reader instanceof Player player ? Math.min(1.0F, ClassTrees.power(player, SCHOLAR_KEY)) : 0.0F;
		return Math.max(1, Math.round(STUDY_TICKS * (1.0F - (1.0F - SCHOLAR) * scholar)));
	}

	/** Battlemage: a staff cast was made; a shield on the other arm stays up through it. */
	static void cast(ServerPlayer mage) {
		if (ClassTrees.has(mage, BATTLEMAGE) > 0 && mage.getOffhandItem().has(DataComponents.BLOCKS_ATTACKS)) {
			GUARD.put(mage.getUUID(), mage.level().getGameTime() + GUARD_TICKS);
		}
	}

	/** Battlemage: the shield kept up through a cast turns {@code source}, a blow from the front that a shield stops. */
	private static boolean guarded(ServerPlayer mage, DamageSource source) {
		Long until = GUARD.get(mage.getUUID());
		if (until == null) {
			return false;
		}
		if (until < mage.level().getGameTime() || !mage.getOffhandItem().has(DataComponents.BLOCKS_ATTACKS)) {
			GUARD.remove(mage.getUUID());
			return false;
		}
		Vec3 from = source.getSourcePosition();
		if (source.is(DamageTypeTags.BYPASSES_SHIELD) || from == null
			|| from.subtract(mage.position()).multiply(1.0, 0.0, 1.0).dot(mage.getLookAngle().multiply(1.0, 0.0, 1.0)) <= 0.0) {
			return false;
		}
		mage.level().playSound(null, mage.getX(), mage.getY(), mage.getZ(), SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 1.0F, 1.0F);
		mage.getOffhandItem().hurtAndBreak(1, mage, net.minecraft.world.InteractionHand.OFF_HAND);
		return true;
	}

	// ---- Warlock --------------------------------------------------------------------------------------------------------------

	/** Borrowed Time: the blow that would put {@code warlock} down goes on the account instead. True if it did. */
	private static boolean borrowTime(ServerPlayer warlock) {
		if (ClassTrees.has(warlock, TIME) <= 0 || !Rests.spend(warlock, TIME_POOL, 1, 1)) {
			return false;
		}
		warlock.setHealth(Math.max(1.0F, BORROWED_HEALTH * Math.min(1.0F, ClassTrees.power(warlock, TIME))));
		warlock.setInvulnerableTime(20);
		Warlocks.borrowed(warlock);
		ServerLevel level = warlock.level();
		Fx.ring(level, warlock.position().add(0.0, 0.1, 0.0), 1.6F, 0x8A4FD0);
		level.playSound(null, warlock.getX(), warlock.getY(), warlock.getZ(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 1.0F, 0.5F);
		return true;
	}

	private static void tithe(ServerPlayer warlock) {
		int needed = Math.max(1, Math.round(TITHE_KILLS / Math.min(1.0F, ClassTrees.power(warlock, TITHE))));
		int paid = warlock.getAttachedOrElse(TITHED, 0) + 1;
		if (paid >= needed) {
			warlock.removeAttached(TITHED);
			Warlocks.settled(warlock);
			return;
		}
		warlock.setAttached(TITHED, paid);
		warlock.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.extras.tithe", paid, needed).withStyle(net.minecraft.ChatFormatting.DARK_PURPLE));
	}

	/** Usury: what a Warlock's cast is multiplied by (a fifth harder while they owe). */
	static float usury(Player player) {
		return player instanceof ServerPlayer sp && Warlocks.pact(sp).owing() ? 1.0F + (USURY - 1.0F) * Math.min(1.0F, ClassTrees.power(sp, USURY_KEY)) : 1.0F;
	}

	/** Usury: the casts between debts, from {@code casts} (a fifth sooner). */
	static int castsPerDebt(ServerPlayer player, int casts) {
		return Math.max(1, Math.round(casts / (1.0F + (USURY - 1.0F) * Math.min(1.0F, ClassTrees.power(player, USURY_KEY)))));
	}

	/** Hand of the Patron: a debt was settled, in full and in time (an overdue one is collected, not settled). */
	static void settled(ServerPlayer player) {
		player.removeAttached(TITHED);
		if (ClassTrees.has(player, HAND) > 0 && player.getAttachedOrElse(HAND_STATE, 0) < 2) {
			player.setAttached(HAND_STATE, 1);
		}
	}

	/** A rest: the hand opens, if a debt was settled since the last. */
	static void rested(ServerPlayer player) {
		if (player.getAttachedOrElse(HAND_STATE, 0) == 1 && ClassTrees.has(player, HAND) > 0) {
			player.setAttached(HAND_STATE, 2);
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.extras.hand_open").withStyle(net.minecraft.ChatFormatting.DARK_PURPLE));
		}
	}

	/** Hand of the Patron: whether {@code player}'s next pact cast costs nothing. */
	static boolean handReady(ServerPlayer player) {
		return player.getAttachedOrElse(HAND_STATE, 0) == 2 && ClassTrees.has(player, HAND) > 0;
	}

	/** Hand of the Patron: the free cast is made. */
	static void handSpent(ServerPlayer player, ServerLevel level) {
		player.removeAttached(HAND_STATE);
		Fx.ring(level, player.position().add(0.0, 0.1, 0.0), 1.4F, 0x8A4FD0);
		player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.extras.hand_spent").withStyle(net.minecraft.ChatFormatting.DARK_PURPLE));
	}

	// ---- blows ----------------------------------------------------------------------------------------------------------------

	/** Element's Hide on the one hurt; a Battlemage's staff in the hand that strikes. */
	static float dealt(LivingEntity target, DamageSource source, float amount) {
		if (target instanceof ServerPlayer sorcerer) {
			float hide = ClassTrees.power(sorcerer, HIDE);
			if (hide > 0.0F && ofElement(source, Spellcraft.element(sorcerer), sorcerer)) {
				amount *= Math.max(0.0F, 1.0F - HIDE_PER_RANK * hide);
			}
		}
		if (source.getEntity() instanceof ServerPlayer mage && source.getDirectEntity() == mage && mage != target && Arcana.isStaff(mage.getMainHandItem())
			&& !source.is(DamageTypes.INDIRECT_MAGIC) && !source.is(DamageTypes.MAGIC)) {
			float battle = Math.min(1.0F, ClassTrees.power(mage, BATTLEMAGE));
			if (battle > 0.0F && amount < MACE * battle) {
				amount = MACE * battle;
				mage.level().playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.MACE_SMASH_GROUND, SoundSource.PLAYERS, 0.5F, 1.2F);
			}
		}
		return amount;
	}

	// ---- every tick -----------------------------------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % 20 != 0) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			// Dark Sight: they see in the dark while they owe
			if (player.isAlive() && ClassTrees.has(player, SIGHT) > 0 && Warlocks.pact(player).owing()) {
				player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 260, 0, true, false, false));
			}
		}
	}
}
