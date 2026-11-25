package com.jaymoskingdoms.mixin.souls;

import com.jaymoskingdoms.SoulJars;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BaseSpawner;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.SpawnData;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A spawner's soul charges ({@link SoulJars}): kept in its own save (so they travel with a cage taken up, and reach the
 * client in its update), each rank's step on its range, delay and count, and the quiet of the last rank (no smoke or
 * flame round it, none when it brings something).
 */
@Mixin(BaseSpawner.class)
abstract class SoulSpawnerMixin implements SoulJars.Charged {
	@Unique
	private static final String CHARGES_KEY = "JaymoSoulCharges";

	@Shadow
	private int minSpawnDelay;
	@Shadow
	private int maxSpawnDelay;
	@Shadow
	private int spawnCount;
	@Shadow
	private int requiredPlayerRange;
	@Shadow
	private @Nullable SpawnData nextSpawnData;

	@Unique
	private int jaymoskingdoms$charges;

	@Override
	public int jaymoskingdoms$charges() {
		return this.jaymoskingdoms$charges;
	}

	@Override
	public void jaymoskingdoms$setCharges(int charges) {
		this.jaymoskingdoms$charges = charges;
	}

	@Override
	public @Nullable Identifier jaymoskingdoms$kind() {
		return this.nextSpawnData == null ? null : this.nextSpawnData.getEntityToSpawn().getString("id").map(Identifier::tryParse).orElse(null);
	}

	@Override
	public void jaymoskingdoms$rankUp() {
		this.requiredPlayerRange += SoulJars.RANGE_STEP;
		this.minSpawnDelay = Math.max(20, Math.round(this.minSpawnDelay * (1.0F - SoulJars.DELAY_CUT)));
		this.maxSpawnDelay = Math.max(this.minSpawnDelay + 1, Math.round(this.maxSpawnDelay * (1.0F - SoulJars.DELAY_CUT)));
		this.spawnCount += SoulJars.COUNT_STEP;
	}

	@Unique
	private boolean jaymoskingdoms$quiet() {
		return this.jaymoskingdoms$charges / SoulJars.PER_RANK >= SoulJars.QUIET_RANK;
	}

	@Inject(method = "save", at = @At("TAIL"))
	private void jaymoskingdoms$saveCharges(ValueOutput output, CallbackInfo ci) {
		if (this.jaymoskingdoms$charges > 0) {
			output.putInt(CHARGES_KEY, this.jaymoskingdoms$charges);
		}
	}

	@Inject(method = "load", at = @At("TAIL"))
	private void jaymoskingdoms$loadCharges(@Nullable Level level, BlockPos pos, ValueInput input, CallbackInfo ci) {
		this.jaymoskingdoms$charges = input.getIntOr(CHARGES_KEY, 0);
	}

	@Redirect(method = "clientTick", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/world/level/Level;addParticle(Lnet/minecraft/core/particles/ParticleOptions;DDDDDD)V"))
	private void jaymoskingdoms$quietSmoke(Level level, ParticleOptions particle, double x, double y, double z, double xd, double yd, double zd) {
		if (!jaymoskingdoms$quiet()) {
			level.addParticle(particle, x, y, z, xd, yd, zd);
		}
	}

	@Redirect(method = "serverTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;levelEvent(ILnet/minecraft/core/BlockPos;I)V"))
	private void jaymoskingdoms$quietBirth(ServerLevel level, int type, BlockPos pos, int data) {
		if (!jaymoskingdoms$quiet()) {
			level.levelEvent(type, pos, data);
		}
	}
}
