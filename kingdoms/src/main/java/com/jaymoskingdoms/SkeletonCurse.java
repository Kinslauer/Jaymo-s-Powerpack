package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import java.util.Map;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import org.jspecify.annotations.Nullable;

/**
 * The Skeleton Lord's curse. Andy: "you shouldn't be able to become a skeleton unless you are cursed by the skeleton lord".
 * <p>
 * His spell (and now and then a blow of his staff, {@link SkeletonLord}) leaves it on a player: an effect icon that stays
 * until it's spent or cured, kept by a mark of its own (so milk doesn't wash it off). Die with it on you (anything but the
 * void, lava, losing your head or an execution) and Jaymo's Powers' death by cause asks here first ({@code Mortality.MID_TURNS}):
 * the long black with bone-grey at its edges, and you rise a skeleton Hollow. It's the only way to rise a skeleton: ordinary
 * skeletons turn nobody ({@link Hollows#turnOf}). A golden apple eaten while Weakened lifts it, as it burns out the
 * Hollow's blight; a real death (the end of the character) takes it with them.
 */
public final class SkeletonCurse {
	/** How often (ticks) the cursed are checked: the icon put back if something took it off. */
	static final int CHECK_EVERY = 20;

	/** Whether they carry the curse. Persistent, not kept through a real death. */
	static final AttachmentType<Boolean> CURSED = AttachmentRegistry.create(JaymosKingdoms.id("skeleton_lord_curse"),
		builder -> builder.persistent(Codec.BOOL));

	/** The curse's icon (no particles, no harm of its own). */
	public static Holder<MobEffect> EFFECT;

	private SkeletonCurse() {
	}

	static void register() {
		EFFECT = Registry.registerForHolder(BuiltInRegistries.MOB_EFFECT, JaymosKingdoms.id("skeleton_lord_curse"), new Mark());
		ServerTickEvents.END_SERVER_TICK.register(SkeletonCurse::tick);
		PowersCompat.tellMidTurns(SkeletonCurse::midTurn);
	}

	/** Whether {@code player} carries the Skeleton Lord's curse. */
	public static boolean cursed(ServerPlayer player) {
		return Boolean.TRUE.equals(player.getAttached(CURSED));
	}

	/** Those the curse can't take: already Hollow, an android, in creative or spectating. */
	private static boolean immune(ServerPlayer player) {
		return player.isCreative() || player.isSpectator() || Heritage.of(player) == Heritage.Kind.HOLLOW || PowersCompat.android(player);
	}

	/** The Skeleton Lord's curse falls on {@code player} (nothing if they have it, or can't take it). */
	static void curse(ServerPlayer player) {
		if (cursed(player) || immune(player) || !player.isAlive()) {
			return;
		}
		player.setAttached(CURSED, true);
		// (the curse is the only turning they're on the way to now)
		player.removeAttached(HollowBlight.BLIGHT);
		player.addEffect(icon());
		player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.skeleton_curse.took").withStyle(ChatFormatting.DARK_GRAY,
			ChatFormatting.ITALIC));
		player.level().playSound(null, player.blockPosition(), SoundEvents.SKELETON_AMBIENT, SoundSource.PLAYERS, 0.8F, 0.5F);
		JaymosKingdoms.LOGGER.info("{} was cursed by the Skeleton Lord", player.getScoreboardName());
	}

	/** The curse lifted: the mark and its icon both. */
	static void lift(ServerPlayer player) {
		player.removeAttached(CURSED);
		if (EFFECT != null) {
			player.removeEffect(EFFECT);
		}
	}

	private static MobEffectInstance icon() {
		return new MobEffectInstance(EFFECT, MobEffectInstance.INFINITE_DURATION, 0, false, false, true);
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % CHECK_EVERY != 0) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (!player.isAlive()) {
				continue;
			}
			boolean cursed = cursed(player);
			if (cursed && Heritage.of(player) == Heritage.Kind.HOLLOW) {
				// (risen some other way meanwhile: nothing left for it to make)
				lift(player);
			} else if (cursed && !player.hasEffect(EFFECT)) {
				player.addEffect(icon());
			} else if (!cursed && player.hasEffect(EFFECT)) {
				player.removeEffect(EFFECT);
			}
		}
	}

	/** For Powers' death by cause: one dying cursed rises a skeleton ({@code Mortality.MID_TURNS}). */
	static Map.@Nullable Entry<Integer, Runnable> midTurn(ServerPlayer player) {
		if (!cursed(player) || immune(player)) {
			return null;
		}
		return Map.entry(HollowBlight.SKELETON_TINT, () -> {
			lift(player);
			Hollows.turn(player, new Hollows.Turn(HeritageVariants.Variant.SKELETON, 1.0F, "cursed"));
		});
	}

	/** A golden apple eaten while Weakened lifts the curse ({@code GoldenAppleMixin}). */
	public static void ateGold(ServerPlayer player) {
		if (player.hasEffect(MobEffects.WEAKNESS) && cursed(player)) {
			lift(player);
			player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.skeleton_curse.cured").withStyle(ChatFormatting.GOLD));
			player.level().playSound(null, player.blockPosition(), SoundEvents.ZOMBIE_VILLAGER_CURE, SoundSource.PLAYERS, 0.6F, 1.2F);
		}
	}

	/** The curse's icon: does nothing by itself (the mark, {@link #CURSED}, is what counts). */
	private static final class Mark extends MobEffect {
		Mark() {
			super(MobEffectCategory.HARMFUL, 0xB8B094);
		}
	}
}
