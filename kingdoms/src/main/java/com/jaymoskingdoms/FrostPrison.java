package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

/**
 * (0.7.75) A Frostborn's gift of the day: holding left mouse bare-handed (Jaymo's Powers sends it as the tag
 * {@link #ASK_TAG}; the Frost-born race) locks the living thing they look at in a block
 * of packed ice for a while: a shell of ice round it, frozen through (held still, can't strike out, the cold biting), then
 * the ice falls away. Once a day (a Minecraft day of game time); the ice can be broken out early. (0.7.153) A hold while
 * the day's ice is spent is handed back to Jaymo's Powers as {@link #BEAM_TAG}: a thin white beam instead.
 * <p>
 * (0.7.301) The cold works like 26.4's Potion of Freezing now (Andy, 2026-10-28: "I just really want the way the chilling
 * potion works transferred to frostborn's prison"): the prisoner gets Jaymo's Powers' {@code jaymospowers:freezing} at
 * level {@link #FREEZING_LEVEL} for the whole stay and {@link #LINGER_TICKS} after, instead of being frozen through on the
 * spot. The frost creeps over their screen and they're frozen through in a few seconds, then shake and take a point of
 * freeze damage every two seconds (fire-blooded mobs five times that; leather armour spares the damage), and the chill
 * keeps biting a while after the ice falls before they thaw. Fire and lava keep it off, and things that can't freeze
 * (strays, snow golems, polar bears) are only held. The ice still holds them still, and numb hands break out slowly.
 */
public final class FrostPrison {
	/** How long the ice holds (ticks), how far the Frostborn can freeze (blocks, via {@link Arcana#sighted}), and the wait between (ticks: a day). */
	static final int HOLD_TICKS = 200;
	static final long COOLDOWN_TICKS = 24000L;
	/** (0.7.301) The Freezing level the prison gives (0 = I; II freezes through in 3.5 s), and how long it bites after the ice falls (ticks). */
	static final int FREEZING_LEVEL = 1, LINGER_TICKS = 100;
	/** Jaymo's Powers' Freezing (26.4's potion effect). */
	static final Identifier FREEZING = Identifier.fromNamespaceAndPath("jaymospowers", "freezing");
	/** The biggest thing that fits in a prison: width and height (blocks). */
	static final double MAX_WIDTH = 2.0, MAX_HEIGHT = 3.2;

	/** When each player last froze something (game time). Saved, kept through death. */
	public static final AttachmentType<Long> LAST = AttachmentRegistry.create(JaymosKingdoms.id("frost_prison_last"),
		builder -> builder.persistent(Codec.LONG).copyOnDeath());

	/** Player → server: the heritage key was pressed ({@link Heritage#gift}). */
	public record Cast() implements CustomPacketPayload {
		public static final Type<Cast> TYPE = new Type<>(JaymosKingdoms.id("frost_prison"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Cast> CODEC = StreamCodec.unit(new Cast());

		@Override
		public Type<Cast> type() {
			return TYPE;
		}
	}

	/** A prison standing: who's in it, the ice placed, and when it falls away (game time). */
	private record Prison(ServerLevel level, LivingEntity target, List<BlockPos> ice, long until) {
	}

	private static final List<Prison> PRISONS = new ArrayList<>();
	/** Jaymo's Powers puts this on a Frostborn who held left mouse at something (its RaceMoves); taken off here. */
	static final String ASK_TAG = "jaymoskingdoms_frost_prison";
	/** Put on a Frost-born whose hold came while the day's ice is spent; Jaymo's Powers fires its beam and takes it off. */
	static final String BEAM_TAG = "jaymospowers_frost_beam";

	private FrostPrison() {
	}

	static void register() {
		PayloadTypeRegistry.serverboundPlay().register(Cast.TYPE, Cast.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Cast.TYPE, (payload, context) -> Heritage.gift(context.player()));
		ServerTickEvents.END_SERVER_TICK.register(FrostPrison::tick);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			// (no ice left standing over a restart)
			for (Prison prison : PRISONS) {
				melt(prison);
			}
			PRISONS.clear();
		});
	}

	static void cast(ServerPlayer player) {
		if (!Heritage.is(player, Heritage.Kind.FROSTBORN) || !player.isAlive() || player.isSpectator()) {
			return;
		}
		ServerLevel level = player.level();
		long now = level.getGameTime();
		Long last = player.getAttached(LAST);
		if (last != null && now - last < COOLDOWN_TICKS && now >= last) {
			// (0.7.153) the day's ice is spent: Jaymo's Powers fires its thin white beam instead (FrostBeam)
			player.addTag(BEAM_TAG);
			return;
		}
		LivingEntity target = Arcana.sighted(player);
		if (target == null || jailed(target)) {
			player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.frost_prison.none").withStyle(ChatFormatting.GRAY), true);
			return;
		}
		if (target.getBbWidth() > MAX_WIDTH || target.getBbHeight() > MAX_HEIGHT) {
			player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.frost_prison.big").withStyle(ChatFormatting.GRAY), true);
			return;
		}
		// a shell of ice round where it stands, in the air only (nothing is broken to make it)
		AABB box = target.getBoundingBox();
		BlockPos lo = BlockPos.containing(box.minX, box.minY, box.minZ), hi = BlockPos.containing(box.maxX - 1.0E-4, box.maxY - 1.0E-4, box.maxZ - 1.0E-4);
		List<BlockPos> ice = new ArrayList<>();
		for (BlockPos pos : BlockPos.betweenClosed(lo.offset(-1, -1, -1), hi.offset(1, 1, 1))) {
			boolean inside = pos.getX() >= lo.getX() && pos.getX() <= hi.getX() && pos.getY() >= lo.getY() && pos.getY() <= hi.getY()
				&& pos.getZ() >= lo.getZ() && pos.getZ() <= hi.getZ();
			if (!inside && level.getBlockState(pos).isAir()) {
				level.setBlockAndUpdate(pos, Blocks.PACKED_ICE.defaultBlockState());
				ice.add(pos.immutable());
			}
		}
		player.setAttached(LAST, now);
		PRISONS.add(new Prison(level, target, ice, now + HOLD_TICKS));
		Holder<MobEffect> freezing = BuiltInRegistries.MOB_EFFECT.get(FREEZING).orElse(null);
		if (freezing != null) {
			target.addEffect(new MobEffectInstance(freezing, HOLD_TICKS + LINGER_TICKS, FREEZING_LEVEL), player);
		}
		hold(target);
		target.setDeltaMovement(0.0, Math.min(0.0, target.getDeltaMovement().y), 0.0);
		level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 1.0F, 0.6F);
		level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.PLAYER_HURT_FREEZE, SoundSource.PLAYERS, 1.0F, 0.8F);
	}

	/** Whether {@code entity} is in a prison now. */
	public static boolean jailed(LivingEntity entity) {
		for (Prison prison : PRISONS) {
			if (prison.target == entity) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Held still with numb hands (the cold itself is the Freezing effect). (0.7.301) Without Jaymo's Powers there's no
	 * Freezing, so they're frozen through on the spot, as before.
	 */
	private static void hold(LivingEntity target) {
		target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 30, 9, false, false));
		target.addEffect(new MobEffectInstance(MobEffects.MINING_FATIGUE, 30, 4, false, false));
		if (!BuiltInRegistries.MOB_EFFECT.containsKey(FREEZING)) {
			target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 30, 4, false, false));
			target.setTicksFrozen(Math.max(target.getTicksFrozen(), target.getTicksRequiredToFreeze() + 20));
		}
	}

	private static void tick(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (player.entityTags().contains(ASK_TAG)) {
				player.removeTag(ASK_TAG);
				cast(player);
			}
		}
		for (Iterator<Prison> it = PRISONS.iterator(); it.hasNext(); ) {
			Prison prison = it.next();
			if (!prison.target.isAlive() || prison.target.level() != prison.level || prison.level.getGameTime() >= prison.until) {
				melt(prison);
				it.remove();
				continue;
			}
			hold(prison.target);
		}
	}

	/** The ice falls away (only what's still ice: a block broken or built over stays as it is). */
	private static void melt(Prison prison) {
		for (BlockPos pos : prison.ice) {
			if (prison.level.getBlockState(pos).is(Blocks.PACKED_ICE)) {
				prison.level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
			}
		}
		prison.level.playSound(null, prison.target.getX(), prison.target.getY(), prison.target.getZ(), SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 0.8F, 1.2F);
	}
}
