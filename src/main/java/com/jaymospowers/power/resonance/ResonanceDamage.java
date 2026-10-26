package com.jaymospowers.power.resonance;

import com.jaymospowers.JaymosPowers;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** Resonance's damage types, its sculk-sensitive tag, knockback and the one rule for breaking blocks. */
final class ResonanceDamage {
	/** Pulse and Shatter's sting: armour applies. */
	static final ResourceKey<DamageType> PULSE = ResourceKey.create(Registries.DAMAGE_TYPE, JaymosPowers.id("resonance_pulse"));
	/** Pulse on a sculk-sensitive creature: through armour ({@code #minecraft:bypasses_armor}). */
	static final ResourceKey<DamageType> PULSE_DEEP = ResourceKey.create(Registries.DAMAGE_TYPE, JaymosPowers.id("resonance_pulse_deep"));
	/** Sonic Boom: through armour and shields. */
	static final ResourceKey<DamageType> SONIC_BOOM = ResourceKey.create(Registries.DAMAGE_TYPE, JaymosPowers.id("resonance_sonic_boom"));
	/** Creatures that feel sound in their bones (Pulse ignores their armour): {@code jaymospowers:resonance_sensitive}. */
	static final TagKey<EntityType<?>> SENSITIVE = TagKey.create(Registries.ENTITY_TYPE, JaymosPowers.id("resonance_sensitive"));

	private ResonanceDamage() {
	}

	static DamageSource source(ServerPlayer player, ResourceKey<DamageType> type) {
		return player.damageSources().source(type, player);
	}

	/** (0.17.12) The same, from a creature that isn't a player (a gifted villager's, {@link ResonanceGift}). */
	static DamageSource source(LivingEntity caster, ResourceKey<DamageType> type) {
		return caster.damageSources().source(type, caster);
	}

	static boolean sensitive(LivingEntity entity) {
		return entity.getType().builtInRegistryHolder().is(SENSITIVE);
	}

	/** Pushes {@code entity} along {@code dir} (horizontal strength, then up), less for knockback-resistant creatures. */
	static void push(LivingEntity entity, Vec3 dir, double horizontal, double up) {
		double resist = 1.0 - entity.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);
		if (resist <= 0.0) {
			return;
		}
		Vec3 flat = new Vec3(dir.x, 0.0, dir.z);
		flat = flat.lengthSqr() < 1.0E-6 ? Vec3.ZERO : flat.normalize();
		entity.push(flat.x * horizontal * resist, up * resist, flat.z * horizontal * resist);
		entity.syncVelocity = true;
	}

	/**
	 * Whether {@code player} may break the block at {@code pos}: they can build, the spot isn't spawn-protected or
	 * adventure-restricted, and no claim mod (through Fabric's break event) objects.
	 */
	static boolean mayBreak(ServerPlayer player, ServerLevel level, BlockPos pos, BlockState state) {
		return player.mayBuild() && player.mayInteract(level, pos) && !player.blockActionRestricted(level, pos, player.gameMode())
			&& PlayerBlockBreakEvents.BEFORE.invoker().beforeBlockBreak(level, player, pos, state, level.getBlockEntity(pos));
	}

	/** Breaks it as {@code player} (call {@link #mayBreak} first). */
	static void breakBlock(ServerPlayer player, ServerLevel level, BlockPos pos, BlockState state, boolean drops) {
		level.destroyBlock(pos, drops, player);
		PlayerBlockBreakEvents.AFTER.invoker().afterBlockBreak(level, player, pos, state, null);
	}
}
