package com.jaymospowers.power.arsenal;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The {@code jaymospowers:arsenal_summoned} item component: this stack was pulled from another dimension and goes back
 * there at game time {@code expires}. Weapons pulled together (a bow and its arrow) share a {@code batch}.
 */
public record Summoned(UUID owner, long expires, int batch) {
	public static final Codec<Summoned> CODEC = RecordCodecBuilder.create(i -> i.group(
			UUIDUtil.CODEC.fieldOf("owner").forGetter(Summoned::owner),
			Codec.LONG.fieldOf("expires").forGetter(Summoned::expires),
			Codec.INT.fieldOf("batch").forGetter(Summoned::batch))
		.apply(i, Summoned::new));
	public static final StreamCodec<ByteBuf, Summoned> STREAM_CODEC = StreamCodec.composite(
		UUIDUtil.STREAM_CODEC, Summoned::owner,
		ByteBufCodecs.VAR_LONG, Summoned::expires,
		ByteBufCodecs.VAR_INT, Summoned::batch,
		Summoned::new);

	/** Registered by {@link ArsenalItems#init()}. */
	public static DataComponentType<Summoned> TYPE;

	public static @Nullable Summoned of(ItemStack stack) {
		return stack.isEmpty() ? null : stack.get(TYPE);
	}

	public static boolean is(ItemStack stack) {
		return of(stack) != null;
	}

	public static boolean expired(ItemStack stack, long now) {
		Summoned summoned = of(stack);
		return summoned != null && now >= summoned.expires;
	}

	public boolean ownedBy(UUID player) {
		return owner.equals(player);
	}

	/** The weapon slipping back into its dimension, seen and heard by everyone near {@code pos}. */
	public static void dissolveEffects(ServerLevel level, Vec3 pos) {
		level.sendParticles(ParticleTypes.REVERSE_PORTAL, pos.x, pos.y, pos.z, 18, 0.2, 0.25, 0.2, 0.02);
		level.sendParticles(ParticleTypes.PORTAL, pos.x, pos.y, pos.z, 14, 0.15, 0.2, 0.15, 0.4);
		level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.CHORUS_FRUIT_TELEPORT, SoundSource.PLAYERS, 0.5F, 1.6F);
	}
}
