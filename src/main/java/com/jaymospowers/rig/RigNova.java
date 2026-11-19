package com.jaymospowers.rig;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.Shakes;
import com.jaymospowers.core.fx.MagicFx;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * (Rig patch 6) Nova: the shell opens and blasts everything away from you.
 * A panic button: all charge spent, everything near thrown back, breaks a grab.
 */
public final class RigNova {
    /** Minimum charge needed (uses all of it), cooldown (ticks). */
    static final int MIN_CHARGE = 200, COOLDOWN = 600;
    /** The blast radius (blocks) and the knockback strength. */
    static final float RADIUS = 8.0F, KNOCKBACK = 2.5F;
    /** Damage to living entities in the blast. */
    static final float DAMAGE = 12.0F;
    /** The nova's colour (a blinding white-gold). */
    static final int NOVA_COLOR = 0xFFF8CC;

    private static int cooldown;

    private RigNova() {
    }

    static void init() {
        ServerPlayNetworking.registerGlobalReceiver(NovaPacket.TYPE, (payload, context) -> use(context.player()));
    }

    /** Client → server: fire the nova. */
    public record NovaPacket() implements net.minecraft.network.protocol.common.custom.CustomPacketPayload {
        public static final net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type<NovaPacket> TYPE = new net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type<>(JaymosPowers.id("rig_nova"));
        public static final net.minecraft.network.codec.StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf, NovaPacket> CODEC = net.minecraft.network.codec.StreamCodec.unit(new NovaPacket());

        @Override
        public net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type<NovaPacket> type() {
            return TYPE;
        }
    }

    /** Use the nova. */
    	public static void use(ServerPlayer player) {
    		if (!Rig.fullSet(player)) {
    			player.sendOverlayMessage(Component.translatable("message.jaymospowers.rig.incomplete"));
    			return;
    		}
    		if (cooldown > 0) {
    			player.sendOverlayMessage(Component.translatable("message.jaymospowers.rig.nova_cooldown", cooldown / 20));
    			return;
    		}
    		int charge = Rig.charge(player);
    		if (charge < MIN_CHARGE && !player.isCreative()) {
    			RigMind.empty(player);
    			return;
    		}
    		// spend all charge
    		if (!player.isCreative()) {
    			Rig.spend(player, charge);
    		}
    		cooldown = COOLDOWN;
    		ServerLevel level = player.level();
    		Vec3 pos = player.position().add(0.0, 1.0, 0.0);

    		// the shell opening animation (plates fly out)
    		RigMoves.start(player, RigMoves.UNIBEAM); // reuse the unibeam pose for now
    		// the massive blast
    		MagicFx.blast(level, pos, RADIUS * 1.5F, NOVA_COLOR, 30);
    		MagicFx.ring(level, pos, null, RADIUS * 2.0F, NOVA_COLOR, 20);
    		MagicFx.ring(level, pos, player.getLookAngle(), RADIUS * 1.2F, NOVA_COLOR, 16);
    		level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.ENDER_DRAGON_GROWL, SoundSource.PLAYERS, 1.5F, 0.6F);
    		level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 1.0F, 0.8F);
    		Shakes.around(level, pos, RADIUS * 3.0, 2.5F);

    		// blast entities
    		AABB box = new AABB(pos.x - RADIUS, pos.y - RADIUS, pos.z - RADIUS, pos.x + RADIUS, pos.y + RADIUS, pos.z + RADIUS);
    		List<Entity> entities = level.getEntities(player, box, e -> e instanceof LivingEntity && e.isAlive() && !e.isSpectator());
    		for (Entity entity : entities) {
    			if (entity instanceof LivingEntity living) {
    				Vec3 away = entity.position().subtract(pos).normalize();
    				double distance = entity.distanceToSqr(pos);
    				float falloff = (float) Math.max(0.3, 1.0 - distance / (RADIUS * RADIUS));
    				living.hurtServer(level, level.damageSources().playerAttack(player), DAMAGE * falloff);
    				living.knockback(KNOCKBACK * falloff, away.x, away.z, level.damageSources().playerAttack(player), DAMAGE * falloff);
    				living.syncVelocity = true;
    				// visual on each hit
    				MagicFx.blast(level, entity.position().add(0.0, entity.getBbHeight() * 0.5, 0.0), 0.5F, NOVA_COLOR);
    			}
    		}

    		// break grabs (any entity holding the player)
    		// the player's own velocity is cleared
    		player.setDeltaMovement(Vec3.ZERO);
    		player.syncVelocity = true;

    		RigMind.say(player, "nova", 2.0F);
    	}

    /** Every tick: count down cooldown. */
    static void tick() {
        if (cooldown > 0) {
            cooldown--;
        }
    }

    /** Whether the nova is on cooldown. */
    public static boolean onCooldown() {
        return cooldown > 0;
    }

    /** Cooldown remaining in seconds (for HUD). */
    public static int cooldownSeconds() {
        return cooldown / 20;
    }
}