package com.jaymospowers.power.zfighter;

import com.jaymospowers.JaymosPowers;
import com.mojang.serialization.Codec;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * (0.17.50, Andy's NOEA picks) More Frost-born forms. Past the Golden form (Second, Final, Golden) lies a fifth, the
 * <b>Sovereign</b> ({@link ZFighterBranchForms#SOVEREIGN}, a branch form: C from Golden once it is half-mastered, with 220
 * hardship and 30000 TP): pale platinum, and the cold comes off him ({@link #CHILL_RANGE} blocks round him; foes are slowed
 * every second).
 * <p>And the <b>metal rebuild</b>: a Frost-born whose death blow comes from a strong foe ({@link #STRONG_FOE} health or
 * more, a rogue fighter, or a Z Fighter in Super 2 or better) doesn't die, once. He lies frozen and unhurt for
 * {@link #REBUILD_TICKS} while something under the ice rebuilds him, and gets up in metal ({@link #METAL}, kept through
 * death): {@link #ARMOR} armor and {@link #TOUGHNESS} toughness, {@link #KNOCKBACK} knockback resistance,
 * {@link #HEALTH} more health, a touch slower ({@link #SLOWER}), a steel-plated look on everyone's screen, and whatever
 * stood over him is thrown clear. It is a modifier, not a form: he keeps every form he has.
 */
public final class ZFighterFrostForms {
	/** The Sovereign's cold: blocks, slowness (amplifier, ticks), how often (ticks). */
	static final double CHILL_RANGE = 5.0;
	static final int CHILL_LEVEL = 1;
	static final int CHILL_TICKS = 40;
	static final int CHILL_EVERY = 20;
	/** A foe this strong (max health) can leave him for dead and be survived; so can a rogue fighter or a Z Fighter in Super 2. */
	static final float STRONG_FOE = 60.0F;
	static final int STRONG_FIGHTER_FORM = 2;
	/** The rebuild: how long he lies (ticks), and how far the foes are thrown (blocks) when he rises. */
	static final int REBUILD_TICKS = 100;
	static final double THROW_RANGE = 8.0;
	/** Metal: armor, toughness, knockback resistance, health, speed (added to the multiplier). */
	static final double ARMOR = 8.0;
	static final double TOUGHNESS = 4.0;
	static final double KNOCKBACK = 0.5;
	static final double HEALTH = 10.0;
	static final double SLOWER = -0.03;

	/** Rebuilt in metal. Everyone sees it (the plating). Saved, kept through death. */
	public static final AttachmentType<Boolean> METAL = AttachmentRegistry.create(JaymosPowers.id("zfighter_metal"),
		builder -> builder.persistent(Codec.BOOL).copyOnDeath().syncWith(ByteBufCodecs.BOOL, AttachmentSyncPredicate.all()));

	private static final Identifier ARMOR_ID = JaymosPowers.id("zfighter_metal_armor");
	private static final Identifier TOUGHNESS_ID = JaymosPowers.id("zfighter_metal_toughness");
	private static final Identifier KNOCKBACK_ID = JaymosPowers.id("zfighter_metal_knockback");
	private static final Identifier HEALTH_ID = JaymosPowers.id("zfighter_metal_health");
	private static final Identifier SPEED_ID = JaymosPowers.id("zfighter_metal_speed");

	/** Fighters lying frozen under the rebuild: ticks left. */
	private static final Map<UUID, int[]> REBUILDING = new HashMap<>();

	private ZFighterFrostForms() {
	}

	public static boolean metal(Player player) {
		return Boolean.TRUE.equals(player.getAttached(METAL));
	}

	static void init() {
		ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> {
			if (!(entity instanceof ServerPlayer player) || !ZFighterBranchForms.fighter(player)) {
				return true;
			}
			if (REBUILDING.containsKey(player.getUUID())) {
				return false;
			}
			if (ZFighterLineage.of(player) != ZFighterLineage.FROST || metal(player) || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)
				|| !(source.getEntity() instanceof LivingEntity foe) || !strong(foe)) {
				return true;
			}
			begin(player);
			return false;
		});
		// lying under the rebuild he cannot be hurt (the void and commands still can)
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) ->
			!(entity instanceof ServerPlayer player) || !REBUILDING.containsKey(player.getUUID()) || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> REBUILDING.clear());
	}

	/** A foe worth being rebuilt for. */
	private static boolean strong(LivingEntity foe) {
		if (foe.getMaxHealth() >= STRONG_FOE || foe instanceof ZFighterRogue) {
			return true;
		}
		return foe instanceof ServerPlayer fighter && ZFighterBranchForms.fighter(fighter) && ZFighterPower.live(fighter).form >= STRONG_FIGHTER_FORM;
	}

	private static void begin(ServerPlayer player) {
		REBUILDING.put(player.getUUID(), new int[] {REBUILD_TICKS});
		player.setHealth(1.0F);
		ZFighterPower.Live live = ZFighterPower.live(player);
		ZFighterPower.endSuper(player, live);
		ServerLevel level = player.level();
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 1.5F, 0.5F);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 1.0F, 0.6F);
		level.sendParticles(ParticleTypes.SNOWFLAKE, true, false, player.getX(), player.getY() + 1.0, player.getZ(), 60, 0.5, 0.9, 0.5, 0.15);
		player.sendSystemMessage(Component.translatable("message.jaymospowers.zfighter_metal_begin").withStyle(ChatFormatting.AQUA));
	}

	/** The rebuild, for the debug command: straight into metal (any lineage). */
	static void rebuild(ServerPlayer player) {
		REBUILDING.remove(player.getUUID());
		finish(player);
	}

	private static void finish(ServerPlayer player) {
		player.setAttached(METAL, true);
		ZFighterPower.Live live = ZFighterPower.live(player);
		player.removeEffect(MobEffects.SLOWNESS);
		ZFighterPower.refresh(player, live);
		player.setHealth(player.getMaxHealth());
		live.ki = ZFighterPower.maxKi(player);
		ServerLevel level = player.level();
		for (LivingEntity near : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(THROW_RANGE),
			e -> e != player && e.isAlive() && !e.isSpectator())) {
			Vec3 away = near.position().subtract(player.position());
			ZFighterKi.push(near, away, 1.8 * (1.0 - Math.min(1.0, away.length() / (THROW_RANGE + 1.0))), 0.5);
		}
		LightningBolt bolt = EntityTypes.LIGHTNING_BOLT.create(level, EntitySpawnReason.TRIGGERED);
		if (bolt != null) {
			bolt.snapTo(player.getX(), player.getY(), player.getZ());
			bolt.setVisualOnly(true);
			level.addFreshEntity(bolt);
		}
		level.sendParticles(ParticleTypes.END_ROD, true, false, player.getX(), player.getY() + 1.0, player.getZ(), 60, 0.5, 0.9, 0.5, 0.25);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.IRON_GOLEM_REPAIR, SoundSource.PLAYERS, 1.5F, 0.7F);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.2F, 0.8F);
		com.jaymospowers.core.Shakes.heavy(player, player.position(), 24.0, 1.0F);
		player.sendSystemMessage(Component.translatable("message.jaymospowers.zfighter_metal_done").withStyle(ChatFormatting.AQUA));
	}

	static void clear(ServerPlayer player) {
		player.removeAttached(METAL);
		REBUILDING.remove(player.getUUID());
		ZFighterPower.set(player, Attributes.ARMOR, ARMOR_ID, 0.0, AttributeModifier.Operation.ADD_VALUE);
		ZFighterPower.set(player, Attributes.ARMOR_TOUGHNESS, TOUGHNESS_ID, 0.0, AttributeModifier.Operation.ADD_VALUE);
		ZFighterPower.set(player, Attributes.KNOCKBACK_RESISTANCE, KNOCKBACK_ID, 0.0, AttributeModifier.Operation.ADD_VALUE);
		ZFighterPower.set(player, Attributes.MAX_HEALTH, HEALTH_ID, 0.0, AttributeModifier.Operation.ADD_VALUE);
		ZFighterPower.set(player, Attributes.MOVEMENT_SPEED, SPEED_ID, 0.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
	}

	/** The metal body's hide, refreshed with the rest of his attributes. */
	static void attributes(ServerPlayer player, ZFighterPower.Live live) {
		boolean metal = metal(player);
		ZFighterPower.set(player, Attributes.ARMOR, ARMOR_ID, metal ? ARMOR : 0.0, AttributeModifier.Operation.ADD_VALUE);
		ZFighterPower.set(player, Attributes.ARMOR_TOUGHNESS, TOUGHNESS_ID, metal ? TOUGHNESS : 0.0, AttributeModifier.Operation.ADD_VALUE);
		ZFighterPower.set(player, Attributes.KNOCKBACK_RESISTANCE, KNOCKBACK_ID, metal ? KNOCKBACK : 0.0, AttributeModifier.Operation.ADD_VALUE);
		ZFighterPower.set(player, Attributes.MAX_HEALTH, HEALTH_ID, metal ? HEALTH : 0.0, AttributeModifier.Operation.ADD_VALUE);
		ZFighterPower.set(player, Attributes.MOVEMENT_SPEED, SPEED_ID, metal ? SLOWER : 0.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
	}

	/** Every tick of the power: the rebuild's clock, and the Sovereign's cold. */
	static void tick(ServerPlayer player, ZFighterPower.Live live, int branch) {
		ServerLevel level = player.level();
		int[] left = REBUILDING.get(player.getUUID());
		if (left != null) {
			left[0]--;
			player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 5, 10, false, false));
			live.charging = false;
			if (level.getGameTime() % 3 == 0) {
				level.sendParticles(ParticleTypes.SNOWFLAKE, true, false, player.getX(), player.getY() + 1.0, player.getZ(), 4, 0.4, 0.8, 0.4, 0.02);
				level.sendParticles(ParticleTypes.ELECTRIC_SPARK, true, false, player.getX(), player.getY() + 1.0, player.getZ(), 3, 0.4, 0.8, 0.4, 0.2);
			}
			if (left[0] % 20 == 0) {
				level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ANVIL_USE, SoundSource.PLAYERS, 0.6F, 1.4F);
			}
			if (left[0] <= 0) {
				REBUILDING.remove(player.getUUID());
				finish(player);
			}
			return;
		}
		if (branch == ZFighterBranchForms.SOVEREIGN && level.getGameTime() % CHILL_EVERY == 0) {
			for (LivingEntity near : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(CHILL_RANGE),
				e -> e != player && e.isAlive() && e instanceof Enemy)) {
				near.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, CHILL_TICKS, CHILL_LEVEL, false, true), player);
				level.sendParticles(ParticleTypes.SNOWFLAKE, near.getX(), near.getY() + near.getBbHeight() * 0.5, near.getZ(), 6, 0.3, 0.4, 0.3, 0.02);
			}
			level.sendParticles(ParticleTypes.SNOWFLAKE, true, false, player.getX(), player.getY() + 1.0, player.getZ(), 10, CHILL_RANGE * 0.5, 0.8,
				CHILL_RANGE * 0.5, 0.01);
		}
	}
}
