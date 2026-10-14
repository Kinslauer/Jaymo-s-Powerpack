package com.jaymospowers.core;

import com.jaymospowers.JaymosPowers;
import com.mojang.serialization.Codec;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Broken bones: a blunt weapon's blow ({@link #blunt}: maces, hammers, clubs, flails, staves...) can break the limb it
 * lands on ({@link LimbBoxes}), the way a blade cuts one off ({@link Dismember}). Humanoids only.
 * <ul>
 * <li><b>Arm</b>: whatever that hand held falls, and it holds nothing until the bone knits.</li>
 * <li><b>Leg</b>: a limp: slower, no sprinting, short jumps; both legs, barely a hobble.</li>
 * <li><b>Head</b>: no break, a daze: slowed and reeling (a player's view swims), a mob forgets who it was fighting.</li>
 * <li><b>Skeletons</b> have nothing but bone: the part shatters and is gone for good ({@link Dismember#shatter}).</li>
 * </ul>
 * A break knits by itself after {@link #HEAL_TICKS} of mending, faster the healthier the body ({@link #HEALTHY_RATE}
 * at full health); at once with a respawn, a Hermit Bean, a Verdant's healing (or a Verdant's own body), or a priest
 * ({@link #PRIEST_FEE} emeralds: right-click a cleric with them). Chrono's Bloom and Wind's Zephyr's Breath start the
 * healing ({@link #startMend}): whole again {@link #BLOOM_MEND} and {@link #ZEPHYR_MEND} ticks later, if not sooner. The
 * body is drawn with the broken limb hanging limp (client {@code Dismembered}).
 */
public final class Fractures {
	/** A humanoid mob's chance per blunt blow of at least {@link #MIN_DAMAGE}, plus {@link #PER_DAMAGE} per point over. */
	public static final float BREAK_CHANCE = 0.08F, PER_DAMAGE = 0.01F, MAX_CHANCE = 0.3F;
	/** A player's chance per blunt blow, whatever its weight. */
	public static final float PLAYER_CHANCE = 0.05F;
	public static final float MIN_DAMAGE = 4.0F;
	/** Skeletons shatter at this many times the break chance. */
	public static final float SKELETON_SCALE = 2.0F;
	/** How much mending a break takes: five minutes when near death, a third of that at full health. */
	public static final int HEAL_TICKS = 6000;
	/** Mending per tick at full health (1 at none, in between by health). */
	public static final float HEALTHY_RATE = 3.0F;
	/** How often mending is counted (and saved). */
	private static final int MEND_EVERY = 20;
	/** Started healing: whole again this long after Chrono's Bloom (2 min) and Wind's Zephyr's Breath (5 min). */
	public static final int BLOOM_MEND = 2400, ZEPHYR_MEND = 6000;
	/** What a priest asks to set your bones, in emeralds. */
	public static final int PRIEST_FEE = 2;
	/** A limp: speed and jump lost to one broken leg, and to both. */
	public static final double LIMP_SPEED = -0.3, LIMPS_SPEED = -0.6, LIMP_JUMP = -0.4, LIMPS_JUMP = -0.7;
	public static final int DAZE_TICKS = 80;

	/** The bones this creature has broken (a {@link Dismember#RIGHT_ARM}... bitmask). Saved, synced to everyone. */
	public static final AttachmentType<Integer> BROKEN = AttachmentRegistry.create(JaymosPowers.id("broken_bones"),
		builder -> builder.persistent(Codec.INT).syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.all()));
	/** The mending its breaks still need (ticks at the slowest rate). */
	public static final AttachmentType<Integer> MENDING = AttachmentRegistry.create(JaymosPowers.id("bones_mending"),
		builder -> builder.persistent(Codec.INT));

	/** The game time its breaks will have mended by, whatever its health (a started healing). */
	public static final AttachmentType<Long> MENDED_BY = AttachmentRegistry.create(JaymosPowers.id("bones_mended_by"),
		builder -> builder.persistent(Codec.LONG));

	/** Any weapon that breaks bones, whatever its name. */
	public static final TagKey<net.minecraft.world.item.Item> CRUSHING = TagKey.create(Registries.ITEM, JaymosPowers.id("crushing"));

	private static final String[] BLUNT_WORDS = {"mace", "hammer", "club", "maul", "flail", "morning_star", "morningstar", "cudgel",
		"staff", "mallet", "sledge", "bludgeon", "knobkerrie", "truncheon", "baton"};
	private static final Identifier LIMP_SPEED_ID = JaymosPowers.id("broken_leg_speed");
	private static final Identifier LIMP_JUMP_ID = JaymosPowers.id("broken_leg_jump");
	/** Everyone with a break, until it knits. */
	private static final Set<LivingEntity> HEALING = new HashSet<>();

	private Fractures() {
	}

	public static void init() {
		ServerLivingEntityEvents.AFTER_DAMAGE.register(Fractures::afterDamage);
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof LivingEntity living && broken(living) != 0) {
				HEALING.add(living);
			}
		});
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (hand != InteractionHand.MAIN_HAND || broken(player) == 0 || !player.getMainHandItem().is(Items.EMERALD)
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
		ServerTickEvents.END_SERVER_TICK.register(Fractures::tick);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> HEALING.clear());
	}

	/** A cleric (a village's or a kingdom's priest, MCA's included) who can set bones. */
	public static boolean priest(Entity entity) {
		return entity instanceof VillagerDataHolder holder && entity instanceof LivingEntity living && living.isAlive() && !living.isBaby()
			&& holder.getVillagerData().profession().is(VillagerProfession.CLERIC);
	}

	public static int broken(LivingEntity entity) {
		Integer mask = entity.getAttached(BROKEN);
		return mask == null ? 0 : mask;
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
			|| !blunt(attacker.getMainHandItem()) || !Dismember.canLoseParts(target) || !target.isAlive()) {
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

	/** Breaks {@code part} (a head is dazed instead). True if something happened. */
	public static boolean breakBone(LivingEntity target, int part, LivingEntity by) {
		if (!(target.level() instanceof ServerLevel level) || (Dismember.missing(target) & part) != 0) {
			return false;
		}
		crack(level, target, part);
		if (part == Dismember.HEAD) {
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
		int mask = broken(target);
		if ((mask & part) != 0) {
			return false;
		}
		mask |= part;
		target.setAttached(BROKEN, mask);
		target.setAttached(MENDING, HEAL_TICKS);
		// a fresh break isn't covered by healing started before it
		target.removeAttached(MENDED_BY);
		HEALING.add(target);
		if (part == Dismember.RIGHT_ARM || part == Dismember.LEFT_ARM) {
			Dismember.emptyHand(level, target, part);
		} else {
			limp(target, mask);
		}
		if (target instanceof Player player) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.fracture." + Dismember.name(part)).withStyle(ChatFormatting.RED));
		}
		return true;
	}

	/** All its breaks knit at once. True if it had any. */
	public static boolean heal(LivingEntity target) {
		boolean had = broken(target) != 0;
		target.removeAttached(BROKEN);
		target.removeAttached(MENDING);
		target.removeAttached(MENDED_BY);
		limp(target, 0);
		HEALING.remove(target);
		return had;
	}

	/** Starts its breaks healing: whole again within {@code ticks} (sooner if they'd mend sooner). True if it had any. */
	public static boolean startMend(LivingEntity target, int ticks) {
		if (broken(target) == 0 || !(target.level() instanceof ServerLevel level)) {
			return false;
		}
		long by = level.getGameTime() + ticks;
		Long before = target.getAttached(MENDED_BY);
		if (before == null || by < before) {
			target.setAttached(MENDED_BY, by);
		}
		return before == null || by < before;
	}

	private static void limp(LivingEntity target, int mask) {
		int legs = ((mask & Dismember.RIGHT_LEG) != 0 ? 1 : 0) + ((mask & Dismember.LEFT_LEG) != 0 ? 1 : 0);
		AttributeInstance speed = target.getAttribute(Attributes.MOVEMENT_SPEED);
		AttributeInstance jump = target.getAttribute(Attributes.JUMP_STRENGTH);
		if (legs == 0) {
			if (speed != null) {
				speed.removeModifier(LIMP_SPEED_ID);
			}
			if (jump != null) {
				jump.removeModifier(LIMP_JUMP_ID);
			}
			return;
		}
		if (speed != null) {
			speed.addOrReplacePermanentModifier(new AttributeModifier(LIMP_SPEED_ID, legs == 2 ? LIMPS_SPEED : LIMP_SPEED,
				AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
		}
		if (jump != null) {
			jump.addOrReplacePermanentModifier(new AttributeModifier(LIMP_JUMP_ID, legs == 2 ? LIMPS_JUMP : LIMP_JUMP,
				AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
		}
		target.setSprinting(false);
	}

	/** The snap: a dull crack and chips of bone. */
	static void crack(ServerLevel level, LivingEntity target, int part) {
		Vec3 at = Dismember.stump(target, part);
		level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.BONE_BLOCK.defaultBlockState()), at.x, at.y, at.z, 16,
			0.12, 0.12, 0.12, 0.15);
		level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 8, 0.1, 0.1, 0.1, 0.2);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.BONE_BLOCK_BREAK, SoundSource.HOSTILE, 1.2F, 0.6F);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.HOSTILE, 0.8F, 0.5F);
	}

	private static void tick(MinecraftServer server) {
		if (HEALING.isEmpty()) {
			return;
		}
		boolean count = server.getTickCount() % MEND_EVERY == 0;
		for (Iterator<LivingEntity> it = HEALING.iterator(); it.hasNext(); ) {
			LivingEntity body = it.next();
			if (body.isRemoved() || !(body.level() instanceof ServerLevel level)) {
				it.remove();
				continue;
			}
			if (!body.isAlive()) {
				continue;
			}
			if (count) {
				// mends faster the healthier the body
				float health = body.getHealth() / Math.max(1.0F, body.getMaxHealth());
				Integer left = body.getAttached(MENDING);
				int now = (left == null ? HEAL_TICKS : left) - Math.round(MEND_EVERY * (1.0F + (HEALTHY_RATE - 1.0F) * health));
				Long by = body.getAttached(MENDED_BY);
				if (now <= 0 || broken(body) == 0 || by != null && level.getGameTime() >= by) {
					it.remove();
					body.removeAttached(BROKEN);
					body.removeAttached(MENDING);
					body.removeAttached(MENDED_BY);
					limp(body, 0);
					continue;
				}
				body.setAttached(MENDING, now);
			}
			int mask = broken(body);
			for (int arm : new int[] {Dismember.RIGHT_ARM, Dismember.LEFT_ARM}) {
				if ((mask & arm) != 0 && !body.getItemInHand(Dismember.hand(body, arm)).isEmpty()) {
					// it can't grip with that hand
					Dismember.emptyHand(level, body, arm);
				}
			}
			if ((mask & (Dismember.RIGHT_LEG | Dismember.LEFT_LEG)) != 0 && body.isSprinting()) {
				body.setSprinting(false);
			}
		}
	}
}
