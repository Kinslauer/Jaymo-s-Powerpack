package com.jaymospowers.rig;

import com.jaymospowers.JaymosPowers;
import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;

/**
 * (0.11.110) The Helm's two switches, from the Rig menu (J): the <b>faceplate</b> (up: your face shows, so the Helm
 * hides nobody and anyone watching sees who's inside; down again: the Rig pilot's identity is back) and <b>night
 * vision</b> (Helm and Core: the visor brightens the dark, drawing a little charge while it's on). Both on the player,
 * Andy's ask: "put the faceplate and nightvision on a key or add them to the menu".
 */
public final class RigHelm {
	/** The faceplate is up (synced to everyone: they see the face). Comes down when the Helm comes off. */
	public static final AttachmentType<Boolean> FACE_UP = AttachmentRegistry.create(JaymosPowers.id("rig_face_up"),
		builder -> builder.persistent(Codec.BOOL).syncWith(ByteBufCodecs.BOOL, AttachmentSyncPredicate.all()));
	/** Night vision is switched on (synced to the wearer, for the menu and the HUD). */
	public static final AttachmentType<Boolean> NIGHT = AttachmentRegistry.create(JaymosPowers.id("rig_night"),
		builder -> builder.persistent(Codec.BOOL).syncWith(ByteBufCodecs.BOOL, AttachmentSyncPredicate.targetOnly()));

	/** Night vision: the effect's length, renewed when it gets this short (ticks; past 200 so it never flickers). */
	static final int NIGHT_TICKS = 400, NIGHT_RENEW = 220;
	/** Night vision's draw: this much charge every {@link #NIGHT_DRAIN_EVERY} ticks. */
	static final int NIGHT_COST = 1, NIGHT_DRAIN_EVERY = 20;

	private RigHelm() {
	}

	public static boolean faceUp(Player player) {
		return Boolean.TRUE.equals(player.getAttached(FACE_UP));
	}

	public static boolean night(Player player) {
		return Boolean.TRUE.equals(player.getAttached(NIGHT));
	}

	/** The menu's Faceplate: up or down. */
	static void toggleFace(ServerPlayer player) {
		if (!Rig.wears(player, Rig.Piece.HELM)) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.rig.needs_helm"));
			return;
		}
		boolean up = !faceUp(player);
		if (up) {
			player.setAttached(FACE_UP, true);
		} else {
			player.removeAttached(FACE_UP);
		}
		player.level().playSound(null, player.getX(), player.getEyeY(), player.getZ(), up ? SoundEvents.PISTON_CONTRACT : SoundEvents.PISTON_EXTEND,
			SoundSource.PLAYERS, 0.4F, 1.6F);
		player.level().playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.ARMOR_EQUIP_IRON.value(), SoundSource.PLAYERS, 0.5F,
			up ? 1.2F : 1.5F);
		// the face is out (or hidden again): who you're seen as follows
		com.jaymospowers.costume.SecretIdentity.update(player);
	}

	/** The menu's Night Vision: on or off. */
	static void toggleNight(ServerPlayer player) {
		if (!Rig.wears(player, Rig.Piece.HELM) || Rig.core(player).isEmpty()) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.rig.night_needs"));
			return;
		}
		boolean on = !night(player);
		if (on) {
			player.setAttached(NIGHT, true);
			RigMind.say(player, "night_on", 1.2F);
		} else {
			player.removeAttached(NIGHT);
			clearNight(player);
		}
		player.level().playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.4F,
			on ? 1.8F : 1.2F);
	}

	/** Every tick, for everyone. */
	static void tick(ServerPlayer player) {
		boolean helm = Rig.wears(player, Rig.Piece.HELM) && !player.isSpectator();
		if (!helm && player.getAttached(FACE_UP) != null) {
			// a Helm put on later starts closed
			player.removeAttached(FACE_UP);
			com.jaymospowers.costume.SecretIdentity.update(player);
		}
		if (!night(player)) {
			return;
		}
		if (!helm || Rig.core(player).isEmpty()) {
			clearNight(player);
			return;
		}
		if (player.tickCount % NIGHT_DRAIN_EVERY == 0 && !Rig.spend(player, NIGHT_COST)) {
			player.removeAttached(NIGHT);
			clearNight(player);
			RigMind.empty(player);
			return;
		}
		MobEffectInstance have = player.getEffect(MobEffects.NIGHT_VISION);
		if (have == null || have.getDuration() < NIGHT_RENEW && !have.isInfiniteDuration()) {
			player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, NIGHT_TICKS, 0, false, false, false));
		}
	}

	/** Takes off the visor's night vision (not a potion's: only an unseen one, as the visor gives it). */
	private static void clearNight(ServerPlayer player) {
		MobEffectInstance have = player.getEffect(MobEffects.NIGHT_VISION);
		if (have != null && !have.isVisible() && !have.showIcon() && have.getDuration() <= NIGHT_TICKS) {
			player.removeEffect(MobEffects.NIGHT_VISION);
		}
	}
}
