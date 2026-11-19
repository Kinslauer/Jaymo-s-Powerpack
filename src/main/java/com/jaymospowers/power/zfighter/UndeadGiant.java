package com.jaymospowers.power.zfighter;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.fx.MagicFx;
import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.ZombieVillager;

/**
 * Andy (2026-11-18): "Undead MCA Verdant should have a similar but higher chance to turn giant." An MCA villager who was
 * Verdant and has turned (its Kingdoms heritage tag is copied onto the zombie) may swell under the moon into a Verdant's
 * Giant form, as tall as a Verdant Z Fighter's Titan ({@link ZFighterLineage#GIANT_SCALE}). Rolled once a moon night (the
 * full moon, or Kingdoms' Blood Moon: {@link VillagerApe#moonNight}) for each grown one out under the open sky, at
 * {@link #TURN_CHANCE}, which is higher than the ape's ({@link VillagerApe#TURN_CHANCE}). It stays a zombie: it hunts as it
 * did, only far bigger and harder hitting, and pushes trees over as it walks into them ({@link com.jaymospowers.core.GiantTrample}).
 * It shrinks back as the ape does: at dawn, out of the moonlight a while, or after {@link ZFighterLineage#WILD_MAX_TICKS}.
 */
final class UndeadGiant {
	/** The chance an undead Verdant turns giant, rolled once a moon night. */
	static final float TURN_CHANCE = 0.25F;
	/** How often the dead are looked over for one to turn (ticks). */
	static final int SCAN_EVERY = 20;
	/** It swells over this long, and shrinks back over this long (ticks). */
	static final int GROW_TICKS = 80, SHRINK_TICKS = 40;
	/** Its size added at full growth: a Titan's. */
	static final double SCALE = ZFighterLineage.GIANT_SCALE[3];
	/** Health, attack and knockback resistance added at full growth. */
	static final double HEALTH = 60.0, ATTACK = 6.0, STEADY = 0.6;
	/** Players this near hear of it. */
	static final double TELL_RANGE = 96.0;

	/** Kingdoms' Verdant heritage tag. */
	static final String VERDANT = "jaymoskingdoms_heritage_verdant";
	/** On it while it's grown (saved with it, so one saved grown wakes as itself again). */
	static final String GROWN = "jaymospowers_undead_giant";

	/** When the moon was last rolled for it (game time). Saved. */
	static final AttachmentType<Long> ROLLED = AttachmentRegistry.create(JaymosPowers.id("undead_giant_rolled"),
		builder -> builder.persistent(Codec.LONG));

	private static final Identifier SCALE_ID = JaymosPowers.id("undead_giant_scale");
	private static final Identifier HEALTH_ID = JaymosPowers.id("undead_giant_health");
	private static final Identifier ATTACK_ID = JaymosPowers.id("undead_giant_attack");
	private static final Identifier STEADY_ID = JaymosPowers.id("undead_giant_steady");

	/** A giant, growing or shrinking: how grown (0..1), whether it's still growing, since when, and how long out of the moon. */
	private static final class Giant {
		final Mob mob;
		final long from;
		float size;
		boolean grown = true;
		int moonless;

		Giant(Mob mob, long from) {
			this.mob = mob;
			this.from = from;
		}
	}

	private static final Map<UUID, Giant> GIANTS = new HashMap<>();

	private UndeadGiant() {
	}

	static void init() {
		ServerTickEvents.END_SERVER_TICK.register(UndeadGiant::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> GIANTS.clear());
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof Mob mob && mob.entityTags().contains(GROWN) && !GIANTS.containsKey(mob.getUUID())) {
				calm(mob);
			}
		});
	}

	/** An undead Verdant, grown (no zombie child turns). */
	static boolean verdant(Entity entity) {
		return entity instanceof ZombieVillager zombie && !zombie.isBaby() && zombie.entityTags().contains(VERDANT);
	}

	private static void tick(MinecraftServer server) {
		if (!GIANTS.isEmpty()) {
			for (Giant giant : new ArrayList<>(GIANTS.values())) {
				step(giant);
			}
		}
		if (server.getTickCount() % SCAN_EVERY != 0) {
			return;
		}
		for (ServerLevel level : server.getAllLevels()) {
			if (!VillagerApe.moonNight(level, true)) {
				continue;
			}
			long now = level.getGameTime();
			List<Mob> turning = new ArrayList<>();
			for (Entity entity : level.getAllEntities()) {
				if (entity instanceof Mob mob && mob.isAlive() && verdant(mob) && !GIANTS.containsKey(mob.getUUID())
					&& level.canSeeSky(BlockPos.containing(mob.getEyePosition()))
					&& now - mob.getAttachedOrElse(ROLLED, Long.MIN_VALUE / 2) >= ZFighterLineage.WILD_ROLL_EVERY) {
					mob.setAttached(ROLLED, now);
					if (mob.getRandom().nextFloat() < TURN_CHANCE) {
						turning.add(mob);
					}
				}
			}
			for (Mob mob : turning) {
				turn(mob, level, now);
			}
		}
	}

	private static void turn(Mob mob, ServerLevel level, long now) {
		GIANTS.put(mob.getUUID(), new Giant(mob, now));
		mob.stopRiding();
		mob.addTag(GROWN);
		level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.ZOMBIE_VILLAGER_CONVERTED, SoundSource.HOSTILE, 2.0F, 0.5F);
		level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 2.0F, 0.7F);
		Component line = Component.translatable("message.jaymospowers.dead_giant_turn", mob.getDisplayName()).withStyle(ChatFormatting.DARK_GREEN);
		for (ServerPlayer player : level.players()) {
			if (player.distanceToSqr(mob) <= TELL_RANGE * TELL_RANGE) {
				player.sendSystemMessage(line);
			}
		}
	}

	/** Every tick: it grows (or shrinks back); the night, the sky or the time ends it. */
	private static void step(Giant giant) {
		Mob mob = giant.mob;
		if (mob.isRemoved() || !mob.isAlive() || !(mob.level() instanceof ServerLevel level)) {
			GIANTS.remove(mob.getUUID());
			if (!mob.isRemoved()) {
				calm(mob);
			}
			return;
		}
		long now = level.getGameTime();
		if (giant.grown) {
			boolean moonlit = VillagerApe.moonNight(level, true) && level.canSeeSky(BlockPos.containing(mob.getEyePosition()));
			giant.moonless = moonlit ? 0 : giant.moonless + 1;
			if (giant.moonless >= ZFighterLineage.APE_MOONLESS || level.isBrightOutside() || now - giant.from >= ZFighterLineage.WILD_MAX_TICKS) {
				giant.grown = false;
			}
		}
		float before = giant.size;
		giant.size = giant.grown ? Math.min(1.0F, giant.size + 1.0F / GROW_TICKS) : Math.max(0.0F, giant.size - 1.0F / SHRINK_TICKS);
		if (giant.size != before) {
			body(mob, giant.size);
			if (giant.grown) {
				level.sendParticles(MagicFx.mote(0x5FA83A, 2.5F), true, false, mob.getX(), mob.getY() + mob.getBbHeight() * 0.5, mob.getZ(), 4,
					mob.getBbWidth() * 0.4, mob.getBbHeight() * 0.35, mob.getBbWidth() * 0.4, 0.0);
				if (giant.size >= 1.0F) {
					mob.setHealth(mob.getMaxHealth());
					level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 4.0F, 0.5F);
				}
			}
		}
		if (!giant.grown && giant.size <= 0.0F) {
			GIANTS.remove(mob.getUUID());
			calm(mob);
			return;
		}
		if (giant.size >= 0.5F) {
			// walking into a tree pushes it over, as every giant does
			com.jaymospowers.core.GiantTrample.tick(mob, now);
		}
	}

	/** Its body at {@code size} grown (eased in and out). */
	private static void body(Mob mob, float size) {
		float t = Mth.clamp(size, 0.0F, 1.0F);
		double grown = t * t * (3.0F - 2.0F * t);
		set(mob, Attributes.SCALE, SCALE_ID, SCALE * grown, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
		set(mob, Attributes.MAX_HEALTH, HEALTH_ID, HEALTH * grown, AttributeModifier.Operation.ADD_VALUE);
		set(mob, Attributes.ATTACK_DAMAGE, ATTACK_ID, ATTACK * grown, AttributeModifier.Operation.ADD_VALUE);
		set(mob, Attributes.KNOCKBACK_RESISTANCE, STEADY_ID, STEADY * grown, AttributeModifier.Operation.ADD_VALUE);
		if (mob.getHealth() > mob.getMaxHealth()) {
			mob.setHealth(mob.getMaxHealth());
		}
	}

	private static void set(Mob mob, Holder<Attribute> attribute, Identifier id, double amount, AttributeModifier.Operation operation) {
		AttributeInstance instance = mob.getAttribute(attribute);
		if (instance == null) {
			return;
		}
		if (amount == 0.0) {
			instance.removeModifier(id);
		} else {
			instance.addOrUpdateTransientModifier(new AttributeModifier(id, amount, operation));
		}
	}

	/** Its own size again. */
	private static void calm(Mob mob) {
		mob.removeTag(GROWN);
		body(mob, 0.0F);
	}
}
