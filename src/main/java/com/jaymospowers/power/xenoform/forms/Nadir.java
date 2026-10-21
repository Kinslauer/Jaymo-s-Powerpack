package com.jaymospowers.power.xenoform.forms;

import com.jaymospowers.api.PowerKey;
import com.jaymospowers.power.xenoform.XenoForm;
import com.jaymospowers.power.xenoform.XenoformPower;
import com.jaymospowers.power.xenoform.XenoformState;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * Nadir, the void given shape: a dark figure, an imposing silhouette with a face of nothing and only the xeno
 * insignia on its chest to say what it is. No creature carries it: the two voices in the band give it
 * ({@link com.jaymospowers.power.xenoform.XenoVoices}), and only to a band that has ascended and been corrupted.
 * <ul>
 * <li>R, Beacon: tap to pick a player, anywhere in the world (they show through everything, to everyone, for a
 * moment); hold for {@link #PHASE_HOLD} ticks to phase to them, whatever world they're in.</li>
 * <li>G V B: the void hasn't decided yet (Andy will say).</li>
 * </ul>
 * It takes no fall, fire or drowning; its own moves are not yet many, but its body is the strongest the band knows.
 */
public final class Nadir extends XenoForm {
	// ---- tuning ----
	/** Tap R again within this many ticks to go to the next player; hold R this long to phase. */
	public static final int TAP_WINDOW = 10, PHASE_HOLD = 30, PHASE_COOLDOWN = 100;
	/** The beacon's glow (ticks), refreshed while R is held. */
	public static final int GLOW_TICKS = 60;

	/** Flight like creative's (double-tap jump), this fast (creative's is 0.05). Free and endless. */
	public static final float FLY_SPEED = 0.16F;
	/** Creative's flying speed, put back when Nadir goes. */
	private static final float NORMAL_FLY_SPEED = 0.05F;

	public Nadir() {
		super("nadir", 1.15F, 60.0F, 6.0, 0.2, 1.0, 0.6, Items.ENDER_EYE, 0x7A4FD0);
	}

	@Override
	public void onEnter(ServerPlayer player, XenoformPower.Live live) {
		player.getAbilities().mayfly = true;
		player.getAbilities().setFlyingSpeed(FLY_SPEED);
		player.onUpdateAbilities();
	}

	@Override
	public int flight(ServerPlayer player, XenoformPower.Live live) {
		return player.getAbilities().flying ? XenoformState.FLY_VOID : XenoformState.FLY_NONE;
	}

	@Override
	public boolean allowsFloating(ServerPlayer player, XenoformPower.Live live) {
		return true;
	}

	@Override
	public boolean skipsMovementCheck(ServerPlayer player, XenoformPower.Live live) {
		return player.getAbilities().flying;
	}

	@Override
	public boolean scans(Entity creature) {
		return false;
	}

	@Override
	public boolean hidden() {
		return true;
	}

	@Override
	public void onExit(ServerPlayer player, XenoformPower.Live live) {
		live.beaconHeld = 0;
		live.beaconTarget = null;
		// back to the player's own flight: creative and spectator keep theirs
		player.getAbilities().setFlyingSpeed(NORMAL_FLY_SPEED);
		if (!player.isCreative() && !player.isSpectator()) {
			player.getAbilities().mayfly = false;
			player.getAbilities().flying = false;
		}
		player.onUpdateAbilities();
	}

	@Override
	public void onKey(ServerPlayer player, XenoformPower.Live live, PowerKey key, boolean down) {
		if (key == PowerKey.ABILITY_1) {
			if (down) {
				if (!ready(player, live, R)) {
					return;
				}
				long now = player.level().getGameTime();
				boolean again = now - live.beaconTapAt <= TAP_WINDOW + PHASE_HOLD;
				live.beaconTapAt = now;
				live.beaconHeld = 1;
				pick(player, live, again);
			} else {
				live.beaconHeld = 0;
			}
			return;
		}
		if (down && ready(player, live, key == PowerKey.ABILITY_2 ? G : key == PowerKey.ABILITY_3 ? V : B)) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.xenoform.nadir_later").withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.ITALIC));
			cooldown(player, live, key == PowerKey.ABILITY_2 ? G : key == PowerKey.ABILITY_3 ? V : B, 20);
		}
	}

	@Override
	public void tick(ServerPlayer player, XenoformPower.Live live) {
		ServerLevel level = player.level();
		// the void about it
		if (player.tickCount % 2 == 0) {
			level.sendParticles(ParticleTypes.REVERSE_PORTAL, player.getX(), player.getY() + 1.0, player.getZ(), 1, 0.4, 0.7, 0.4, 0.0);
		}
		if (player.tickCount % 7 == 0) {
			level.sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 1.2, player.getZ(), 1, 0.5, 0.8, 0.5, 0.0);
		}
		if (live.beaconHeld > 0) {
			ServerPlayer target = target(player, live);
			if (target == null) {
				live.beaconHeld = 0;
				return;
			}
			live.beaconHeld++;
			if (live.beaconHeld % 20 == 1) {
				target.addEffect(new MobEffectInstance(MobEffects.GLOWING, GLOW_TICKS, 0, false, false));
			}
			int bars = Math.min(10, live.beaconHeld * 10 / PHASE_HOLD);
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.xenoform.beacon_hold", target.getDisplayName(),
				"|".repeat(bars) + ".".repeat(10 - bars)).withStyle(ChatFormatting.LIGHT_PURPLE));
			if (live.beaconHeld >= PHASE_HOLD) {
				phase(player, live, target);
			}
		}
	}

	/** Nothing of the world touches it. */
	@Override
	public boolean allowDamage(ServerPlayer player, XenoformPower.Live live, net.minecraft.world.damagesource.DamageSource source, float amount) {
		return !(source.is(net.minecraft.tags.DamageTypeTags.IS_FALL) || source.is(net.minecraft.tags.DamageTypeTags.IS_FIRE)
			|| source.is(net.minecraft.tags.DamageTypeTags.IS_DROWNING));
	}

	/** Every other player on the server, by name. */
	private static List<ServerPlayer> others(ServerPlayer player) {
		List<ServerPlayer> list = new ArrayList<>(player.level().getServer().getPlayerList().getPlayers());
		list.removeIf(p -> p == player || p.isSpectator());
		list.sort(Comparator.comparing(p -> p.getGameProfile().name()));
		return list;
	}

	private static ServerPlayer target(ServerPlayer player, XenoformPower.Live live) {
		if (live.beaconTarget == null) {
			return null;
		}
		ServerPlayer target = player.level().getServer().getPlayerList().getPlayer(live.beaconTarget);
		return target == null || target == player || target.isSpectator() ? null : target;
	}

	/** Picks the beacon: the next player after the current one ({@code again}), else the first; says who. */
	private static void pick(ServerPlayer player, XenoformPower.Live live, boolean again) {
		List<ServerPlayer> others = others(player);
		if (others.isEmpty()) {
			live.beaconTarget = null;
			live.beaconHeld = 0;
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.xenoform.beacon_alone").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
			return;
		}
		int index = 0;
		if (again && live.beaconTarget != null) {
			for (int i = 0; i < others.size(); i++) {
				if (others.get(i).getUUID().equals(live.beaconTarget)) {
					index = (i + 1) % others.size();
					break;
				}
			}
		}
		ServerPlayer target = others.get(index);
		live.beaconTarget = target.getUUID();
		target.addEffect(new MobEffectInstance(MobEffects.GLOWING, GLOW_TICKS, 0, false, false));
		target.level().sendParticles(ParticleTypes.REVERSE_PORTAL, target.getX(), target.getY() + 1.0, target.getZ(), 20, 0.3, 0.6, 0.3, 0.05);
		sound(player, SoundEvents.AMETHYST_BLOCK_CHIME, 1.0F, 0.5F);
	}

	/** Through the void, to just behind them. */
	private static void phase(ServerPlayer player, XenoformPower.Live live, ServerPlayer target) {
		ServerLevel from = player.level();
		ServerLevel to = target.level();
		Vec3 flat = target.getLookAngle().horizontal();
		flat = flat.lengthSqr() < 1.0E-4 ? new Vec3(0.0, 0.0, 1.0) : flat.normalize();
		Vec3 behind = target.position().subtract(flat.scale(1.5));
		if (!to.noCollision(player, player.getBoundingBox().move(behind.subtract(player.position())))) {
			behind = target.position();
		}
		from.sendParticles(ParticleTypes.REVERSE_PORTAL, player.getX(), player.getY() + 1.0, player.getZ(), 60, 0.4, 0.8, 0.4, 0.2);
		from.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENDERMAN_TELEPORT, net.minecraft.sounds.SoundSource.PLAYERS, 1.0F, 0.5F);
		player.teleportTo(to, behind.x, behind.y, behind.z, Set.of(), target.getYRot(), player.getXRot(), false);
		player.resetFallDistance();
		to.sendParticles(ParticleTypes.REVERSE_PORTAL, behind.x, behind.y + 1.0, behind.z, 60, 0.4, 0.8, 0.4, 0.2);
		to.playSound(null, behind.x, behind.y, behind.z, SoundEvents.ENDERMAN_TELEPORT, net.minecraft.sounds.SoundSource.PLAYERS, 1.0F, 0.5F);
		to.playSound(null, behind.x, behind.y, behind.z, SoundEvents.WARDEN_HEARTBEAT, net.minecraft.sounds.SoundSource.PLAYERS, 1.0F, 0.8F);
		target.sendOverlayMessage(Component.translatable("message.jaymospowers.xenoform.beacon_arrived", player.getDisplayName()).withStyle(ChatFormatting.DARK_PURPLE));
		live.beaconHeld = 0;
		cooldown(player, live, R, PHASE_COOLDOWN);
	}
}
