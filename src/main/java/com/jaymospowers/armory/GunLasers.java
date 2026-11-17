package com.jaymospowers.armory;

import com.jaymospowers.JaymosPowers;
import java.lang.reflect.Method;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import org.jspecify.annotations.Nullable;

/**
 * Laser sights on the future's guns (Andy's Ragdolls and Cover picks, row ad_laser, after TaCZ Additions; nothing of it
 * copied). TaCZ already draws a laser attachment's beam; this adds a switch for it and its dot.
 * <ul>
 * <li>A gun with a laser fitted can have it switched off: {@link #OFF} in its custom data, flipped by the laser key
 * ({@code client/armory/GunLaserClient}) through {@link Toggle}. TaCZ's beam isn't drawn while it's off
 * ({@code TaczLaserBeamMixin}) and nor is the dot. The gun's data travels with it, so everyone sees the same.</li>
 * <li>The future's gunners ({@code power/chrono/future/FutureGuns}) are sometimes handed one ({@link #fit}), and keep it
 * off until they take aim: a red dot on the wall before the shot.</li>
 * </ul>
 * TaCZ is reached by reflection ({@code IGun}, {@code AttachmentType.LASER}, {@code AttachmentItemBuilder}), so it isn't a
 * compile dependency; without it nothing here does anything.
 */
public final class GunLasers {
	/** In a gun's custom data: its laser is switched off. */
	public static final String OFF = "JaymosLaserOff";
	/** TaCZ's own laser attachments (its default gun pack); a gunner gets the first its gun takes, tried in a random order. */
	static final String[] LASERS = {"laser_compact", "laser_lopro", "laser_peq15", "laser_peq6", "laser_nightstick"};
	/** The click of the switch: this loud, this high. */
	static final float CLICK_VOLUME = 0.4F, CLICK_PITCH = 1.8F;

	private static boolean looked;
	private static @Nullable Method getIGun, attachmentId, allow, install;
	private static @Nullable Object laserType;
	private static @Nullable Method builderCreate, builderId, builderBuild;

	/** Client → server: switch the laser on the gun in the main hand. */
	public record Toggle() implements CustomPacketPayload {
		public static final Type<Toggle> TYPE = new Type<>(JaymosPowers.id("laser_toggle"));
		public static final StreamCodec<FriendlyByteBuf, Toggle> CODEC = StreamCodec.unit(new Toggle());

		@Override
		public Type<Toggle> type() {
			return TYPE;
		}
	}

	private GunLasers() {
	}

	public static void init() {
		PayloadTypeRegistry.serverboundPlay().register(Toggle.TYPE, Toggle.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Toggle.TYPE, (payload, ctx) -> toggle(ctx.player()));
	}

	private static void toggle(ServerPlayer player) {
		ItemStack gun = player.getMainHandItem();
		if (!hasLaser(gun)) {
			return;
		}
		boolean off = !off(gun);
		setOff(gun, off);
		player.level().playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.LEVER_CLICK, SoundSource.PLAYERS, CLICK_VOLUME,
			off ? CLICK_PITCH * 0.8F : CLICK_PITCH);
	}

	/** Whether {@code gun}'s laser is switched off. */
	public static boolean off(ItemStack gun) {
		CustomData data = gun.get(DataComponents.CUSTOM_DATA);
		return data != null && data.copyTag().getBooleanOr(OFF, false);
	}

	/** Switches {@code gun}'s laser off or on (does nothing to a gun with no custom data, which isn't a TaCZ gun). */
	public static void setOff(ItemStack gun, boolean off) {
		if (gun.has(DataComponents.CUSTOM_DATA) && off != off(gun)) {
			CustomData.update(DataComponents.CUSTOM_DATA, gun, tag -> {
				if (off) {
					tag.putBoolean(OFF, true);
				} else {
					tag.remove(OFF);
				}
			});
		}
	}

	/** Whether {@code stack} is a TaCZ gun with a laser fitted (or built in). */
	public static boolean hasLaser(ItemStack stack) {
		if (!TaczGuns.LOADED || stack.isEmpty() || TaczGuns.gunId(stack).isEmpty() || !look()) {
			return false;
		}
		try {
			Object gun = getIGun.invoke(null, stack);
			if (gun == null) {
				return false;
			}
			Object id = attachmentId.invoke(gun, stack, laserType);
			return id instanceof Identifier fitted && !"empty".equals(fitted.getPath());
		} catch (ReflectiveOperationException | RuntimeException e) {
			return false;
		}
	}

	/** Fits one of TaCZ's lasers to {@code gun} if it takes one, switched off. Returns whether it did. */
	public static boolean fit(ItemStack gun, RandomSource random) {
		if (!TaczGuns.LOADED || gun.isEmpty() || !look() || builderCreate == null) {
			return false;
		}
		try {
			Object iGun = getIGun.invoke(null, gun);
			if (iGun == null) {
				return false;
			}
			int start = random.nextInt(LASERS.length);
			for (int i = 0; i < LASERS.length; i++) {
				Object builder = builderCreate.invoke(null);
				builderId.invoke(builder, Identifier.fromNamespaceAndPath("tacz", LASERS[(start + i) % LASERS.length]));
				ItemStack laser = (ItemStack) builderBuild.invoke(builder);
				if (!laser.isEmpty() && Boolean.TRUE.equals(allow.invoke(iGun, gun, laser))) {
					install.invoke(iGun, gun, laser);
					setOff(gun, true);
					return true;
				}
			}
		} catch (ReflectiveOperationException | RuntimeException e) {
			JaymosPowers.LOGGER.debug("Couldn't fit a laser: {}", e.toString());
		}
		return false;
	}

	private static boolean look() {
		if (!looked) {
			looked = true;
			try {
				Class<?> iGun = Class.forName("com.tacz.guns.api.item.IGun");
				Class<?> type = Class.forName("com.tacz.guns.api.item.attachment.AttachmentType");
				getIGun = iGun.getMethod("getIGunOrNull", ItemStack.class);
				attachmentId = iGun.getMethod("getAttachmentId", ItemStack.class, type);
				allow = iGun.getMethod("allowAttachment", ItemStack.class, ItemStack.class);
				install = iGun.getMethod("installAttachment", ItemStack.class, ItemStack.class);
				laserType = type.getField("LASER").get(null);
				Class<?> builder = Class.forName("com.tacz.guns.api.item.builder.AttachmentItemBuilder");
				builderCreate = builder.getMethod("create");
				builderId = builder.getMethod("setId", Identifier.class);
				builderBuild = builder.getMethod("build");
			} catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
				JaymosPowers.LOGGER.warn("Couldn't reach TaCZ's lasers: {}", e.toString());
				getIGun = null;
			}
		}
		return getIGun != null;
	}
}
