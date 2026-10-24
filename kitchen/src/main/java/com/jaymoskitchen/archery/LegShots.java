package com.jaymoskitchen.archery;

import com.jaymoskitchen.JaymosKitchen;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.InteractionResult;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.phys.Vec3;

/**
 * Leg hits: an arrow a player fires, or a full-strength blow, that lands in the lower part of a creature's body knocks
 * it down to crawl for five seconds (thirty for hostile humanoids) (drawn on the ground: see CrawlPose), then leaves it limping. A fully drawn
 * (critical) shot hobbles longer, and every further leg hit stacks the limp up, to a limit.
 * Creatures without legs to speak of (slimes, fish, ghasts...) are listed in the {@link #NO_LEGS} tag.
 * <p>
 * (0.5.13) Andy (2026-10-22): "crippling should only activate if the legs are broken, not just on any hit". A leg hit
 * only cripples (the crawl and the limp) a creature with a broken leg: Jaymo's Powers' Wounds tags a broken part
 * {@code jaymoskitchen.broken.<part>} ({@link #BROKEN_LEG_TAGS}), so without Powers nothing is crippled. The check waits
 * for the end of the tick, after the blow's damage has had its chance to break the leg.
 */
public final class LegShots {
	/** How much of a creature's height counts as legs: about the bottom three eighths, as on a player. */
	public static final double LEG_FRACTION = 0.375;
	/** (0.5.23) An arrow tagged this pinned the legs it hit (Jaymo's Kingdoms' Archer) and doesn't cripple them as well. */
	public static final String LEG_PIN_TAG = "jaymoskingdoms.leg_pin";
	/** Slowness from one leg shot: duration, and level (0 = Slowness I) for a normal and a fully drawn shot. */
	private static final int TICKS = 60, CRIT_TICKS = 100;
	private static final int AMPLIFIER = 0, CRIT_AMPLIFIER = 1;
	/** Stacked leg shots cap out at Slowness III. */
	private static final int MAX_AMPLIFIER = 2;

	/** Entity types that can't be shot in the legs (data/jaymoskitchen/tags/entity_type/no_legs.json). */
	public static final TagKey<EntityType<?>> NO_LEGS = TagKey.create(Registries.ENTITY_TYPE, JaymosKitchen.id("no_legs"));

	private LegShots() {
	}

	/**
	 * A hobbled creature's crawl: the game time it ends, shifted up 12 bits, with how long it lasts in the low bits
	 * (synced to everyone who can see it, who draw it crawling). Not saved: a crawl outlasting a reload doesn't matter.
	 */
	public static final AttachmentType<Long> CRAWL = AttachmentRegistry.create(JaymosKitchen.id("leg_crawl"),
		builder -> builder.syncWith(ByteBufCodecs.VAR_LONG.cast(), AttachmentSyncPredicate.all()));
	/** A leg hit puts it down on the ground for this long, and slows it to a crawl. */
	public static final int CRAWL_TICKS = 100;
	/** Hostile humanoids (zombies, skeletons, illagers... see the tag) stay down much longer. */
	public static final int LONG_CRAWL_TICKS = 600;
	public static final TagKey<EntityType<?>> DOWNED_LONG = TagKey.create(Registries.ENTITY_TYPE, JaymosKitchen.id("downed_long"));
	private static final int CRAWL_AMPLIFIER = 3;
	/** (0.5.13) The tags Powers' Wounds puts on a body for each broken leg (any of its legs). */
	public static final List<String> BROKEN_LEG_TAGS = List.of("jaymoskitchen.broken.left_leg", "jaymoskitchen.broken.right_leg",
		"jaymoskitchen.broken.front_legs", "jaymoskitchen.broken.hind_legs", "jaymoskitchen.broken.legs");
	/** (0.5.13) Leg hits waiting for the end of the tick, when the blow's damage (and any break) has landed. */
	private static final List<Runnable> PENDING = new ArrayList<>();

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (!PENDING.isEmpty()) {
				List<Runnable> due = new ArrayList<>(PENDING);
				PENDING.clear();
				due.forEach(Runnable::run);
			}
		});
		// melee: where the swing meets the body, along the attacker's line of sight
		AttackEntityCallback.EVENT.register((player, level, hand, entity, hitResult) -> {
			if (level instanceof ServerLevel && !player.isSpectator() && entity instanceof LivingEntity target
				&& player.getAttackStrengthScale(0.5F) > 0.9F) {
				Vec3 eye = player.getEyePosition();
				Vec3 reach = eye.add(player.getViewVector(1.0F).scale(player.entityInteractionRange() + 1.0));
				target.getBoundingBox().inflate(0.1).clip(eye, reach).ifPresent(hit -> onLegHit(target, player, hit, false));
			}
			return InteractionResult.PASS;
		});
	}

	/** Called when {@code arrow} has just hurt {@code target}, having struck it at {@code hit}. */
	public static void onArrowHit(AbstractArrow arrow, LivingEntity target, Vec3 hit) {
		// (0.5.23) an Archer's arrow pins the legs instead (Jaymo's Kingdoms tags it)
		if (arrow.getOwner() instanceof Player player && !arrow.entityTags().contains(LEG_PIN_TAG)) {
			onLegHit(target, player, hit, arrow.isCritArrow());
		}
	}

	/** A player's arrow or blow landed on {@code target} at {@code hit}: if that's its legs, and one is broken, it's hobbled. */
	public static void onLegHit(LivingEntity target, Player attacker, Vec3 hit, boolean crit) {
		if (!(target.level() instanceof ServerLevel) || target.is(NO_LEGS) || target.getBbHeight() <= 0.0F) {
			return;
		}
		// how far up the body it landed: 0 at the feet, 1 at the top of the head
		double height = (hit.y - target.getY()) / target.getBbHeight();
		if (height > LEG_FRACTION) {
			return;
		}
		PENDING.add(() -> cripple(target, attacker, hit, crit));
	}

	/** Whether any of its legs is broken (Powers' Wounds). */
	public static boolean legBroken(net.minecraft.world.entity.Entity entity) {
		for (String tag : BROKEN_LEG_TAGS) {
			if (entity.entityTags().contains(tag)) {
				return true;
			}
		}
		return false;
	}

	/** The end of the tick a leg hit landed: a broken leg puts it down to crawl, then leaves it limping. */
	private static void cripple(LivingEntity target, Player attacker, Vec3 hit, boolean crit) {
		if (!target.isAlive() || target.isRemoved() || !(target.level() instanceof ServerLevel level) || !legBroken(target)) {
			return;
		}

		int crawlTicks = target.is(DOWNED_LONG) ? LONG_CRAWL_TICKS : CRAWL_TICKS;
		int amplifier = crit ? CRIT_AMPLIFIER : AMPLIFIER;
		int ticks = crit ? CRIT_TICKS : TICKS;
		MobEffectInstance current = target.getEffect(MobEffects.SLOWNESS);
		if (current != null) {
			// already limping: another leg hit makes it worse (never weaker than what it already has)
			amplifier = Math.min(MAX_AMPLIFIER, Math.max(amplifier, Math.min(MAX_AMPLIFIER, current.getAmplifier()) + 1));
			ticks = Math.max(ticks, current.getDuration());
		}
		// the limp it's left with, starting once it's back on its feet...
		target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, crawlTicks + ticks, amplifier), attacker);
		// ...and first, down on the ground dragging itself along (the effect system keeps the limp underneath)
		target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, crawlTicks, CRAWL_AMPLIFIER), attacker);
		target.setAttached(CRAWL, (level.getGameTime() + crawlTicks) << 12 | crawlTicks);

		level.sendParticles(ParticleTypes.CRIT, hit.x, hit.y, hit.z, 10, 0.15, 0.1, 0.15, 0.25);
		level.playSound(null, hit.x, hit.y, hit.z, SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 0.7F, 0.8F);
		level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.PLAYER_BIG_FALL, SoundSource.HOSTILE, 0.6F, 0.9F);
	}

	/** Whether it's down on the ground right now (it can't shoot or draw a bow while it is). */
	public static boolean isCrawling(net.minecraft.world.entity.Entity entity) {
		Long crawl = entity.getAttached(CRAWL);
		return crawl != null && entity.level().getGameTime() < (crawl >> 12);
	}

	/** How far through its crawl a creature is: 0 standing, 1 down, easing down and back up over a quarter second. */
	public static float crawlAmount(net.minecraft.world.entity.Entity entity, float partialTicks) {
		Long crawl = entity.getAttached(CRAWL);
		if (crawl == null) {
			return 0.0F;
		}
		float now = entity.level().getGameTime() + partialTicks;
		float left = (crawl >> 12) - now;
		float elapsed = (crawl & 0xFFF) - left;
		return Math.max(0.0F, Math.min(1.0F, Math.min(elapsed, left) / 5.0F));
	}
}
