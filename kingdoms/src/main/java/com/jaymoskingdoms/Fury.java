package com.jaymoskingdoms;

import com.jaymoskingdoms.Classes.PlayerClass;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * The Brute's tree, Fury (class trees, docs/ClassTrees-Plan.md), the mirror of the Warlord's {@link Discipline}, on the Brute's
 * gauge, <b>Rage</b> ({@link BruteRage}: built by blows dealt and taken, fading out of a fight; raging, the Brute hits harder,
 * more with each level, and can't block, drink or eat: that is the price).
 * <ul>
 * <li><b>Blood Up</b> (3): Rage builds faster, and each rank more damage per point of Rage.</li>
 * <li><b>Pain Feeds</b> (3): blows taken while raging heal a sliver back.</li>
 * <li><b>Reckless</b> or <b>Juggernaut</b> (10): no shield, +25% while raging; or heavy armour weighs nothing while raging.</li>
 * <li><b>Shrug It Off</b> (15): raging, no knockback and no stagger.</li>
 * <li><b>Blackout</b> or <b>Bloodlust</b> (20): raging, you can't go down until it runs out; or each kill while raging refills
 * the Rage and some health.</li>
 * <li><b>Rampage</b> (top): the class key while raging spends the Rage: every blow cleaves for 8 s, then you're spent, slow and
 * weak for a while.</li>
 * </ul>
 */
public final class Fury {
	/** Blood Up, a rank: Rage builds this share faster; this share harder at full Rage (a share of it as the gauge fills). */
	static final float BLOOD_UP_FILL = 0.25F, BLOOD_UP_DAMAGE = 0.05F;
	/** Pain Feeds: this share of a blow taken while raging comes back as health. */
	static final float PAIN_FEEDS = 0.2F;
	/** Reckless: this share harder while raging, with no shield. */
	static final float RECKLESS = 0.25F;
	/** Shrug It Off: knockback resistance while raging (on top of the rage's own). */
	static final double SHRUG_KNOCKBACK = 1.0;
	/** Bloodlust: a kill while raging gives back this share of health. */
	static final float BLOODLUST_HEAL = 0.15F;
	/** Rampage: how long (ticks), what share of a blow carries to the foes round it, and how far; spent this long after. */
	static final int RAMPAGE_TICKS = 20 * 8, RAMPAGE_SPENT = 20 * 15;
	static final float RAMPAGE_CLEAVE = 0.75F;
	static final double RAMPAGE_REACH = 3.0;
	static final int COLOUR = 0xC0301A;

	public static final String BLOOD_UP = "wb_blood_up", PAIN_FEEDS_KEY = "wb_pain_feeds", RECKLESS_KEY = "wb_reckless", JUGGERNAUT = "wb_juggernaut",
		SHRUG = "wb_shrug", BLACKOUT = "wb_blackout", BLOODLUST = "wb_bloodlust", RAMPAGE = "wb_rampage";

	/** Rampaging Brutes: until when. */
	private static final Map<UUID, Long> RAMPAGING = new HashMap<>();
	/** Brutes whose rage was on last tick (for its start and end). */
	private static final Set<UUID> WAS_RAGING = new HashSet<>();
	private static boolean cleaving;

	private Fury() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Fury::tick);
		ServerLivingEntityEvents.AFTER_DAMAGE.register(Fury::damaged);
		ServerLivingEntityEvents.AFTER_DEATH.register(Fury::killed);
		ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> {
			if (entity instanceof ServerPlayer brute && blackout(brute) && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
				// Blackout: not down while the rage lasts
				brute.setHealth(1.0F);
				return false;
			}
			return true;
		});
		UseItemCallback.EVENT.register((player, level, hand) -> {
			ItemStack held = player.getItemInHand(hand);
			if (player instanceof ServerPlayer brute && raging(brute) && forbidden(held)) {
				brute.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.rage.no"));
				return InteractionResult.FAIL;
			}
			return InteractionResult.PASS;
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			RAMPAGING.clear();
			WAS_RAGING.clear();
		});
	}

	static boolean raging(Player player) {
		return Classes.is(player, PlayerClass.BRUTE) && BruteRage.raging(player) > 0;
	}

	/** What a raging Brute may not do: block (a shield, a raised guard), drink or eat. */
	private static boolean forbidden(ItemStack stack) {
		return stack.has(DataComponents.BLOCKS_ATTACKS) || stack.has(DataComponents.CONSUMABLE);
	}

	private static boolean shield(Player player) {
		return player.getMainHandItem().getItem() instanceof net.minecraft.world.item.ShieldItem || player.getOffhandItem().has(DataComponents.BLOCKS_ATTACKS);
	}

	/** Blood Up: how many times as fast {@code brute}'s Rage builds ({@link BruteRage}). */
	static float fillRate(ServerPlayer brute) {
		return 1.0F + BLOOD_UP_FILL * ClassTrees.power(brute, BLOOD_UP);
	}

	/** Juggernaut: heavy armour weighs nothing on {@code player} just now ({@link ClassPerks#attributes}). */
	static boolean weightless(ServerPlayer player) {
		return raging(player) && ClassTrees.has(player, JUGGERNAUT) > 0;
	}

	private static boolean blackout(ServerPlayer brute) {
		return raging(brute) && ClassTrees.has(brute, BLACKOUT) > 0;
	}

	/** What Fury does to a blow ({@link TreePerks#dealt}): Blood Up, Reckless and the Rampage's cleave; and Blackout's floor. */
	static float dealt(LivingEntity target, DamageSource source, float amount) {
		if (target instanceof ServerPlayer brute && blackout(brute) && amount >= brute.getHealth()
			&& !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			// Blackout: the blow leaves them standing
			amount = Math.max(0.0F, brute.getHealth() - 1.0F);
		}
		if (!(source.getEntity() instanceof ServerPlayer brute) || brute == target || source.getDirectEntity() != brute
			|| !Classes.is(brute, PlayerClass.BRUTE) || !(brute.level() instanceof ServerLevel level)) {
			return amount;
		}
		boolean raging = BruteRage.raging(brute) > 0;
		if (ClassTrees.has(brute, BLOOD_UP) > 0) {
			amount *= 1.0F + BLOOD_UP_DAMAGE * ClassTrees.power(brute, BLOOD_UP) * (raging ? 1.0F : BruteRage.meter(brute));
		}
		if (raging && ClassTrees.has(brute, RECKLESS_KEY) > 0 && !shield(brute)) {
			amount *= 1.0F + RECKLESS;
		}
		if (raging && !cleaving && !CombatArts.striking() && RAMPAGING.getOrDefault(brute.getUUID(), 0L) >= level.getGameTime()) {
			// the Rampage: the blow carries to every foe round the one struck
			cleaving = true;
			try {
				for (LivingEntity foe : CombatArts.foes(level, brute, target.getBoundingBox().inflate(RAMPAGE_REACH))) {
					if (foe != target) {
						CombatArts.strike(level, brute, foe, amount * RAMPAGE_CLEAVE);
					}
				}
			} finally {
				cleaving = false;
			}
			level.sendParticles(ParticleTypes.SWEEP_ATTACK, target.getX(), target.getY(0.5), target.getZ(), 2, 0.6, 0.1, 0.6, 0.0);
		}
		return amount;
	}

	private static void damaged(LivingEntity victim, DamageSource source, float base, float taken, boolean blocked) {
		if (victim instanceof ServerPlayer brute && taken > 0.0F && raging(brute) && ClassTrees.has(brute, PAIN_FEEDS_KEY) > 0
			&& source.getEntity() instanceof LivingEntity && source.getEntity() != brute) {
			brute.heal(taken * PAIN_FEEDS);
		}
	}

	private static void killed(LivingEntity dead, DamageSource source) {
		if (source.getEntity() instanceof ServerPlayer brute && brute != dead && raging(brute) && ClassTrees.has(brute, BLOODLUST) > 0) {
			brute.setAttached(BruteRage.RAGING, Math.max(BruteRage.raging(brute), BruteRage.RAGE_TICKS));
			brute.heal(brute.getMaxHealth() * BLOODLUST_HEAL);
			brute.level().sendParticles(ParticleTypes.DAMAGE_INDICATOR, brute.getX(), brute.getY(1.0), brute.getZ(), 6, 0.3, 0.3, 0.3, 0.1);
		}
	}

	/** The class key: the Rampage, while raging; otherwise how full the Rage is. */
	static void key(ServerPlayer brute, boolean sneak) {
		ServerLevel level = brute.level();
		if (ClassTrees.has(brute, RAMPAGE) > 0 && raging(brute) && RAMPAGING.getOrDefault(brute.getUUID(), 0L) < level.getGameTime()) {
			RAMPAGING.put(brute.getUUID(), level.getGameTime() + RAMPAGE_TICKS);
			brute.setAttached(BruteRage.RAGING, RAMPAGE_TICKS);
			Fx.blast(level, brute.position().add(0.0, 1.0, 0.0), 2.0F, COLOUR);
			level.playSound(null, brute.getX(), brute.getY(), brute.getZ(), SoundEvents.RAVAGER_ROAR, SoundSource.PLAYERS, 1.0F, 1.1F);
			brute.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.rage.rampage"));
			return;
		}
		int meter = raging(brute) ? 100 : Math.round(BruteRage.meter(brute) * 100.0F);
		brute.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.rage.gauge", meter));
	}

	private static void tick(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			boolean raging = raging(player) && player.isAlive();
			boolean was = WAS_RAGING.contains(player.getUUID());
			if (raging != was) {
				if (raging) {
					WAS_RAGING.add(player.getUUID());
				} else {
					WAS_RAGING.remove(player.getUUID());
					Long rampage = RAMPAGING.remove(player.getUUID());
					if (rampage != null) {
						// spent after a Rampage: slow and weak for a while
						player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, RAMPAGE_SPENT, 1));
						player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, RAMPAGE_SPENT, 0));
					}
				}
				// Juggernaut: the armour's weight comes off and goes back on with the rage
				ClassPerks.attributes(player);
			}
			boolean shrug = raging && ClassTrees.has(player, SHRUG) > 0;
			Skills.modifier(player, Attributes.KNOCKBACK_RESISTANCE, "fury_shrug", shrug ? SHRUG_KNOCKBACK : 0.0, AttributeModifier.Operation.ADD_VALUE);
			if (shrug && player.hasEffect(MobEffects.SLOWNESS)) {
				// no stagger
				player.removeEffect(MobEffects.SLOWNESS);
			}
			if (raging && player.isUsingItem() && forbidden(player.getUseItem())) {
				// the rage takes the shield down, the cup and the bread out of hand
				player.stopUsingItem();
			}
		}
	}
}
