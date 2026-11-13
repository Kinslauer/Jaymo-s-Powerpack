package com.jaymoskingdoms.client;

import com.jaymoskingdoms.Afflictions;
import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.Shoves;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import com.jaymoskingdoms.GasBottle;
import com.jaymoskingdoms.ThrownNet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * The client's half of the conditions (fifth edition's frightened, held fast, deafened and poisoned, {@link Afflictions}):
 * <ul>
 * <li><b>Held fast</b> (and asleep, and a heartbeat's surprise): the keys are locked ({@code LockedInputMixin},
 * {@code LockedActionsMixin}); the eyes stay free.</li>
 * <li><b>Frightened</b>: motion toward what scares you is cut to a crawl, and while it is in view the aim shakes.</li>
 * <li><b>Poisoned</b>: the hands shake, a little ({@link #POISON_SHAKE}).</li>
 * <li><b>Deafened</b>: every sound goes muffled ({@code SoundEngineMixin}) under a high thin whine that fades with it.</li>
 * <li>The shove key (default Insert), pressed to shove or trip ({@link Shoves}).</li>
 * </ul>
 */
public final class FifthClient {
	/** Aim shake (degrees a tick, each way) when frightened and poisoned. */
	static final float FEAR_SHAKE = 1.1F, POISON_SHAKE = 0.35F;
	/** Motion toward the scare keeps this share. */
	static final double CRAWL = 0.15;
	/** The source must be this far inside the view (cosine) to shake the aim. */
	static final double IN_VIEW = 0.2;
	/** Deaf: every other sound keeps this share of its volume at the deepest, and returns over the last {@link #RECOVER} ticks. */
	static final float MUFFLED = 0.15F;
	static final int RECOVER = 60;
	/** The whine's volume at the deepest. */
	static final float WHINE = 0.1F;

	private static KeyMapping shoveKey;
	private static Whine whine;

	private FifthClient() {
	}

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(Afflictions.Sync.TYPE, (payload, context) -> {
			Afflictions.clientLocked = payload.locked();
			Afflictions.clientDeafUntil = payload.deafUntil();
			Afflictions.clientFearSource = payload.fearSource();
			Afflictions.clientFearUntil = payload.fearUntil();
		});
		EntityRenderers.register(ThrownNet.TYPE, context -> new ThrownItemRenderer<>(context, 0.8F, false));
		EntityRenderers.register(GasBottle.TYPE, context -> new ThrownItemRenderer<>(context, 0.8F, false));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> clear());
		shoveKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.jaymoskingdoms.shove", InputConstants.KEY_INSERT,
			KeyMapping.Category.register(JaymosKingdoms.id("fifth"))));
		ClientTickEvents.END_CLIENT_TICK.register(FifthClient::tick);
	}

	private static void clear() {
		Afflictions.clientLocked = false;
		Afflictions.clientDeafUntil = 0L;
		Afflictions.clientFearSource = -1;
		Afflictions.clientFearUntil = 0L;
	}

	/** Whether the keys are locked just now. */
	public static boolean locked() {
		return Afflictions.clientLocked;
	}

	/** How much of its volume a sound keeps: 1 when the ears are clear, down to {@link #MUFFLED} when they ring. */
	public static float muffle(SoundInstance sound) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || sound instanceof Whine) {
			return 1.0F;
		}
		long left = Afflictions.clientDeafUntil - mc.level.getGameTime();
		if (left <= 0L) {
			return 1.0F;
		}
		return Mth.lerp(Mth.clamp(left / (float) RECOVER, 0.0F, 1.0F), 1.0F, MUFFLED);
	}

	private static void tick(Minecraft mc) {
		LocalPlayer player = mc.player;
		if (player == null || mc.level == null) {
			return;
		}
		long now = mc.level.getGameTime();
		if (now < Afflictions.clientFearUntil && Afflictions.clientFearSource >= 0) {
			Entity scare = mc.level.getEntity(Afflictions.clientFearSource);
			if (scare != null) {
				crawl(player, scare);
				Vec3 to = scare.getEyePosition().subtract(player.getEyePosition());
				if (to.lengthSqr() > 1.0E-4 && player.getLookAngle().dot(to.normalize()) > IN_VIEW && player.hasLineOfSight(scare)) {
					shake(player, FEAR_SHAKE);
				}
			}
		}
		if (player.hasEffect(MobEffects.POISON)) {
			shake(player, POISON_SHAKE);
		}
		if (now < Afflictions.clientDeafUntil && (whine == null || whine.isStopped())) {
			whine = new Whine();
			mc.getSoundManager().play(whine);
		}
		while (shoveKey.consumeClick()) {
			if (mc.gui.screen() == null && ClientPlayNetworking.canSend(Shoves.Press.TYPE)) {
				ClientPlayNetworking.send(new Shoves.Press());
			}
		}
	}

	/** Takes most of the way {@code player} is moving toward {@code scare}. */
	private static void crawl(LocalPlayer player, Entity scare) {
		Vec3 to = scare.position().subtract(player.position()).multiply(1.0, 0.0, 1.0);
		if (to.lengthSqr() < 0.01) {
			return;
		}
		Vec3 dir = to.normalize();
		Vec3 v = player.getDeltaMovement();
		double along = v.x * dir.x + v.z * dir.z;
		if (along > 0.0) {
			double cut = along * (1.0 - CRAWL);
			player.setDeltaMovement(v.x - dir.x * cut, v.y, v.z - dir.z * cut);
		}
	}

	private static void shake(LocalPlayer player, float degrees) {
		var random = player.getRandom();
		player.setYRot(player.getYRot() + (random.nextFloat() - 0.5F) * 2.0F * degrees);
		player.setXRot(Mth.clamp(player.getXRot() + (random.nextFloat() - 0.5F) * 2.0F * degrees, -90.0F, 90.0F));
	}

	/** The high thin whine in a deafened player's ears: a quiet held note that fades as the ringing does. */
	static final class Whine extends AbstractTickableSoundInstance {
		Whine() {
			super(SoundEvents.NOTE_BLOCK_BIT.value(), SoundSource.MASTER, Minecraft.getInstance().level.getRandom());
			this.looping = true;
			this.relative = true;
			this.attenuation = SoundInstance.Attenuation.NONE;
			this.pitch = 2.0F;
			this.volume = WHINE;
		}

		@Override
		public void tick() {
			Minecraft mc = Minecraft.getInstance();
			if (mc.level == null) {
				this.stop();
				return;
			}
			long left = Afflictions.clientDeafUntil - mc.level.getGameTime();
			if (left <= 0L) {
				this.stop();
				return;
			}
			this.volume = WHINE * Mth.clamp(left / (float) RECOVER, 0.0F, 1.0F);
		}
	}
}
