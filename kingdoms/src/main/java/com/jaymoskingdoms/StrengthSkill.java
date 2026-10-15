package com.jaymoskingdoms;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityCombatEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;

/**
 * (0.5.52) The Strength pillar of the skill tree ({@link Skills}). It grows as you fight and labour: landing blows,
 * felling monsters, mining stone and chopping wood. Practice makes your blows a little heavier.
 *
 * <p>Perks: Heavy Blows (more melee damage), Brace (less knockback taken), Bulldozer (more knockback dealt), Quarryman
 * (break blocks faster), Packhorse (sprinting, mining and fighting tire you less) and Titan's Grip (a full-strength blow
 * may stagger what it hits). (0.6.5) Past Titan's Grip, the Combat Arts: {@link CombatArts}. (0.7.30) A second root, Weapon
 * Forms, opens every weapon's full combo ({@link WeaponForms}).
 */
public final class StrengthSkill {
	/** Strength experience: a melee blow landed (at most this often, ticks), a monster felled, stone or wood broken (this chance). */
	static final int XP_HIT = 1, XP_KILL = 4, XP_BREAK = 1;
	static final long XP_HIT_TICKS = 10L;
	static final float XP_BREAK_CHANCE = 0.25F;
	/** Blocks at least this hard to break count as hard labour (stone is 1.5). */
	static final float LABOUR_HARDNESS = 1.5F;
	/** Practice: each Strength level adds this share to melee damage. */
	static final double PRACTICE_PER_LEVEL = 0.01;
	/** Heavy Blows: per rank, this share more melee damage. Brace: per rank, this much knockback resistance (of 1). */
	static final double HEAVY_BLOWS = 0.04, BRACE = 0.1;
	/** Bulldozer: per rank, this much more knockback dealt. Quarryman: this share faster breaking. */
	static final double BULLDOZER = 0.25, QUARRYMAN = 0.25;
	/** Packhorse: this share off how fast hunger drains. */
	static final float PACKHORSE = 0.25F;
	/** Titan's Grip: a blow at least this charged staggers its target this often, for this long (ticks). */
	static final float TITANS_GRIP_CHARGE = 0.95F, TITANS_GRIP_CHANCE = 0.2F;
	static final int TITANS_GRIP_TICKS = 40;

	static final String DAMAGE_ID = "skill_heavy_blows", BRACE_ID = "skill_brace", KNOCKBACK_ID = "skill_bulldozer", BREAK_ID = "skill_quarryman";

	/** Player → game time their next blow gives experience. */
	private static final Map<UUID, Long> NEXT_HIT_XP = new HashMap<>();

	private StrengthSkill() {
	}

	static void register() {
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damageTaken, blocked) -> {
			if (!(source.getEntity() instanceof ServerPlayer sp) || source.getDirectEntity() != sp || entity == sp || damageTaken <= 0.0F || sp.isCreative()) {
				return;
			}
			long now = sp.level().getGameTime();
			if (NEXT_HIT_XP.getOrDefault(sp.getUUID(), 0L) > now) {
				return;
			}
			NEXT_HIT_XP.put(sp.getUUID(), now + XP_HIT_TICKS);
			Skills.grant(sp, Skills.Pillar.STRENGTH, XP_HIT);
		});
		ServerEntityCombatEvents.AFTER_KILLED_OTHER_ENTITY.register((level, killer, killed, source) -> {
			if (killer instanceof ServerPlayer sp && killed instanceof Enemy && !sp.isCreative()) {
				Skills.grant(sp, Skills.Pillar.STRENGTH, XP_KILL);
			}
		});
		PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> {
			if (!(player instanceof ServerPlayer sp) || sp.isCreative()) {
				return;
			}
			if ((state.is(BlockTags.LOGS) || state.getDestroySpeed(world, pos) >= LABOUR_HARDNESS) && world.getRandom().nextFloat() < XP_BREAK_CHANCE) {
				Skills.grant(sp, Skills.Pillar.STRENGTH, XP_BREAK);
			}
		});
		AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
			if (world instanceof ServerLevel level && player instanceof ServerPlayer sp && entity instanceof LivingEntity target && target.isAlive()
				&& Skills.rank(sp, Skills.Perk.TITANS_GRIP) > 0 && sp.getAttackStrengthScale(0.5F) >= TITANS_GRIP_CHARGE
				&& level.getRandom().nextFloat() < TITANS_GRIP_CHANCE) {
				target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, TITANS_GRIP_TICKS, 2), sp);
				target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, TITANS_GRIP_TICKS, 0), sp);
				level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.3F, 0.7F);
			}
			return InteractionResult.PASS;
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> NEXT_HIT_XP.clear());
	}

	/** How much slower {@code player}'s hunger drains, as a factor on its exhaustion (Packhorse). */
	static float exhaustion(ServerPlayer player) {
		return Skills.rank(player, Skills.Perk.PACKHORSE) > 0 ? 1.0F - PACKHORSE : 1.0F;
	}

	/** Practice, Heavy Blows, Brace, Bulldozer and Quarryman, as attribute modifiers. */
	static void attributes(ServerPlayer player) {
		Skills.Progress p = Skills.of(player);
		Skills.modifier(player, Attributes.ATTACK_DAMAGE, DAMAGE_ID,
			PRACTICE_PER_LEVEL * p.level(Skills.Pillar.STRENGTH) + HEAVY_BLOWS * p.rank(Skills.Perk.HEAVY_BLOWS), AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
		Skills.modifier(player, Attributes.KNOCKBACK_RESISTANCE, BRACE_ID, BRACE * p.rank(Skills.Perk.BRACE), AttributeModifier.Operation.ADD_VALUE);
		Skills.modifier(player, Attributes.ATTACK_KNOCKBACK, KNOCKBACK_ID, BULLDOZER * p.rank(Skills.Perk.BULLDOZER), AttributeModifier.Operation.ADD_VALUE);
		Skills.modifier(player, Attributes.BLOCK_BREAK_SPEED, BREAK_ID, p.rank(Skills.Perk.QUARRYMAN) > 0 ? QUARRYMAN : 0.0,
			AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
	}
}
