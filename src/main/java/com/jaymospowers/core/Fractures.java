package com.jaymospowers.core;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.wounds.BodyPart;
import com.jaymospowers.core.wounds.Breaks;
import com.jaymospowers.core.wounds.Downed;
import com.jaymospowers.core.wounds.Wounds;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.villager.VillagerDataHolder;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Broken bones: a blunt weapon's blow ({@link #blunt}: maces, hammers, clubs, flails, staves...) can break the limb it
 * lands on ({@link LimbBoxes}), the way a blade cuts one off ({@link Dismember}). Humanoids only.
 * <ul>
 * <li><b>Arm or leg</b>: (0.14.2) broken in Wounds ({@link Wounds#breakPart}): the limb's health is gone and its effects
 * (a weak arm, a limp, a crawl on two broken legs) are Wounds' own ({@code core/wounds/Breaks}).</li>
 * <li><b>Head</b>: no break, a daze: slowed and reeling (a player's view swims), a mob forgets who it was fighting.</li>
 * <li><b>Skeletons</b> have nothing but bone: the part shatters and is gone for good ({@link Dismember#shatter}).</li>
 * </ul>
 * (0.14.2) Wounds phase 3: Fractures is a front for Wounds' broken parts. A break is set (and then heals as any wound,
 * the break going once the limb is back above {@code Breaks.BROKEN_CAP}) by a priest ({@link #PRIEST_FEE} emeralds:
 * right-click a cleric with them), a respawn, a Hermit Bean, a Verdant's healing (or a Verdant's own body), Chrono's
 * Bloom or Wind's Zephyr's Breath ({@link #heal}, {@link #startMend}: both {@link Wounds#mendAll}). The body is drawn
 * with the broken limb hanging limp (client {@code Dismembered}, from Wounds' broken parts).
 */
public final class Fractures {
	/** A humanoid mob's chance per blunt blow of at least {@link #MIN_DAMAGE}, plus {@link #PER_DAMAGE} per point over. */
	public static final float BREAK_CHANCE = 0.08F, PER_DAMAGE = 0.01F, MAX_CHANCE = 0.3F;
	/** A player's chance per blunt blow, whatever its weight. */
	public static final float PLAYER_CHANCE = 0.05F;
	public static final float MIN_DAMAGE = 4.0F;
	/** Skeletons shatter at this many times the break chance. */
	public static final float SKELETON_SCALE = 2.0F;
	/** (before 0.14.2: started healing's length) Chrono's Bloom and Wind's Zephyr's Breath now set breaks at once; kept for their callers. */
	public static final int BLOOM_MEND = 2400, ZEPHYR_MEND = 6000;
	/** What a priest asks to set your bones, in emeralds. */
	public static final int PRIEST_FEE = 2;
	public static final int DAZE_TICKS = 80;

	/** Any weapon that breaks bones, whatever its name. */
	public static final TagKey<net.minecraft.world.item.Item> CRUSHING = TagKey.create(Registries.ITEM, JaymosPowers.id("crushing"));

	private static final String[] BLUNT_WORDS = {"mace", "hammer", "club", "maul", "flail", "morning_star", "morningstar", "cudgel",
		"staff", "mallet", "sledge", "bludgeon", "knobkerrie", "truncheon", "baton"};
	/** (0.14.2) The blunt roll runs after Wounds has routed the blow (so the break comes on top of the hit's own loss). */
	private static final Identifier AFTER_WOUNDS = JaymosPowers.id("fractures_after_wounds");

	private Fractures() {
	}

	public static void init() {
		ServerLivingEntityEvents.AFTER_DAMAGE.addPhaseOrdering(Event.DEFAULT_PHASE, AFTER_WOUNDS);
		ServerLivingEntityEvents.AFTER_DAMAGE.register(AFTER_WOUNDS, Fractures::afterDamage);
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (hand != InteractionHand.MAIN_HAND || Breaks.unset(player) == 0 || !player.getMainHandItem().is(Items.EMERALD)
				|| player.getMainHandItem().getCount() < PRIEST_FEE || !priest(entity)) {
				return InteractionResult.PASS;
			}
			if (!level.isClientSide() && level instanceof ServerLevel server) {
				player.getMainHandItem().consume(PRIEST_FEE, player);
				heal(player);
				server.sendParticles(ParticleTypes.HAPPY_VILLAGER, player.getX(), player.getY() + 1.0, player.getZ(), 12, 0.4, 0.5, 0.4, 0.0);
				server.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.VILLAGER_WORK_CLERIC, SoundSource.NEUTRAL, 1.0F, 1.0F);
				player.sendOverlayMessage(Component.translatable("message.jaymospowers.fracture.priest", entity.getDisplayName())
					.withStyle(ChatFormatting.GREEN));
			}
			return InteractionResult.SUCCESS;
		});
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> heal(newPlayer));
	}

	/** A cleric (a village's or a kingdom's priest, MCA's included) who can set bones. */
	public static boolean priest(Entity entity) {
		return entity instanceof VillagerDataHolder holder && entity instanceof LivingEntity living && living.isAlive() && !living.isBaby()
			&& holder.getVillagerData().profession().is(VillagerProfession.CLERIC);
	}

	/** The broken arms and legs, as {@link Dismember} bits ((0.14.2) Wounds' broken parts; both sides). */
	public static int broken(LivingEntity entity) {
		return Wounds.brokenLimbs(entity);
	}

	/** Whether {@code stack} breaks bones: maces, hammers, clubs and the like (never a blade). */
	public static boolean blunt(ItemStack stack) {
		if (stack.isEmpty() || Dismember.blade(stack)) {
			return false;
		}
		if (stack.is(Items.MACE) || stack.is(CRUSHING)) {
			return true;
		}
		String path = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
		for (String word : BLUNT_WORDS) {
			if (path.contains(word)) {
				return true;
			}
		}
		return false;
	}

	private static void afterDamage(LivingEntity target, DamageSource source, float baseDamage, float damage, boolean blocked) {
		if (blocked || damage < MIN_DAMAGE || !(target.level() instanceof ServerLevel)) {
			return;
		}
		if (!(source.getDirectEntity() instanceof LivingEntity attacker) || source.getEntity() != attacker || attacker == target
			|| !blunt(attacker.getMainHandItem()) || !Dismember.canLoseParts(target) || !target.isAlive() || Downed.isDowned(target)) {
			return;
		}
		if (target instanceof Player p && (p.isCreative() || p.isSpectator())) {
			return;
		}
		float chance = target instanceof Player ? PLAYER_CHANCE : Math.min(MAX_CHANCE, BREAK_CHANCE + PER_DAMAGE * (damage - MIN_DAMAGE));
		boolean bones = target.getType().builtInRegistryHolder().is(EntityTypeTags.SKELETONS);
		if (bones) {
			chance = Math.min(1.0F, chance * SKELETON_SCALE);
		}
		if (target.getRandom().nextFloat() >= chance) {
			return;
		}
		int part = Dismember.aimedPart(attacker, target);
		if (bones) {
			Dismember.shatter(target, part, attacker);
		} else {
			breakBone(target, part, attacker);
		}
	}

	/** Breaks {@code part} (a {@link Dismember} bit; a head is dazed instead). True if something happened. (0.14.2) The break is Wounds'. */
	public static boolean breakBone(LivingEntity target, int part, LivingEntity by) {
		if (!(target.level() instanceof ServerLevel level) || (Dismember.missing(target) & part) != 0) {
			return false;
		}
		if (part == Dismember.HEAD) {
			crack(level, target, part);
			target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, DAZE_TICKS, 1), by);
			if (target instanceof Player player) {
				player.addEffect(new MobEffectInstance(MobEffects.NAUSEA, DAZE_TICKS * 2, 0), by);
				player.sendOverlayMessage(Component.translatable("message.jaymospowers.fracture.dazed").withStyle(ChatFormatting.RED));
			} else if (target instanceof Mob mob) {
				mob.setTarget(null);
				mob.getNavigation().stop();
			}
			return true;
		}
		BodyPart limb = Breaks.part(part);
		// (Wounds snaps it: the crack, the message, the hand letting go)
		return limb != null && Wounds.breakPart(target, limb, by);
	}

	/** (0.14.2) Its breaks set ({@link Wounds#mendAll}): they heal from here, and go once healed past the cap. True if it had any to set. */
	public static boolean heal(LivingEntity target) {
		return Wounds.mendAll(target);
	}

	/** (0.14.2) As {@link #heal}: started healing now sets the breaks at once ({@code ticks} no longer counts). True if it had any to set. */
	public static boolean startMend(LivingEntity target, int ticks) {
		return Wounds.mendAll(target);
	}

	/** The snap: a dull crack and chips of bone. */
	public static void crack(ServerLevel level, LivingEntity target, int part) {
		Vec3 at = Dismember.stump(target, part);
		level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.BONE_BLOCK.defaultBlockState()), at.x, at.y, at.z, 16,
			0.12, 0.12, 0.12, 0.15);
		level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 8, 0.1, 0.1, 0.1, 0.2);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.BONE_BLOCK_BREAK, SoundSource.HOSTILE, 1.2F, 0.6F);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.HOSTILE, 0.8F, 0.5F);
	}
}
