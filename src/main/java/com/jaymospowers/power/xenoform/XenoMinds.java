package com.jaymospowers.power.xenoform;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.power.xenoform.minds.CortexMind;
import com.jaymospowers.power.xenoform.minds.LightBlocks;
import com.jaymospowers.power.xenoform.minds.LumenShapes;
import com.jaymospowers.power.xenoform.minds.MirageFace;
import com.jaymospowers.power.xenoform.minds.OozeStretch;
import com.jaymospowers.power.xenoform.minds.WispHand;
import io.netty.buffer.ByteBuf;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.Nullable;

/**
 * The shared plumbing of the Xeno-Band's mind and shape moves: Ooze's stretches, Lumen's shapes of light, Mirage's face,
 * Wisp's hand and thinning, Cortex's reading and pushing (each in {@code xenoform/minds/}). It holds what the HUD is
 * sent, the face a Mirage wears (drawn for everyone), a small book of private cooldowns for the variants that sit on
 * a key beside the move already there, and the damage hook that runs before the band's own.
 */
public final class XenoMinds {
	/** Lumen's HUD: the shape picked (index into {@link LumenShapes.Shape}), its charge and heat out of {@link LumenShapes#MAX}. */
	public record LumenHud(int shape, int charge, int heat, boolean over) {
		public static final StreamCodec<ByteBuf, LumenHud> CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, LumenHud::shape,
			ByteBufCodecs.VAR_INT, LumenHud::charge, ByteBufCodecs.VAR_INT, LumenHud::heat, ByteBufCodecs.BOOL, LumenHud::over, LumenHud::new);
	}

	/** Cortex's HUD: whoever last struck you (name, health, whom it is after, what it looks about to do), and the stare is on. */
	public record FoeHud(String name, float health, float max, String target, String intent, boolean staring) {
		public static final StreamCodec<ByteBuf, FoeHud> CODEC = StreamCodec.composite(ByteBufCodecs.STRING_UTF8, FoeHud::name,
			ByteBufCodecs.FLOAT, FoeHud::health, ByteBufCodecs.FLOAT, FoeHud::max, ByteBufCodecs.STRING_UTF8, FoeHud::target,
			ByteBufCodecs.STRING_UTF8, FoeHud::intent, ByteBufCodecs.BOOL, FoeHud::staring, FoeHud::new);
	}

	public static final AttachmentType<LumenHud> LUMEN = AttachmentRegistry.create(JaymosPowers.id("xenoform_lumen_hud"),
		builder -> builder.syncWith(LumenHud.CODEC, AttachmentSyncPredicate.targetOnly()));
	public static final AttachmentType<FoeHud> FOE = AttachmentRegistry.create(JaymosPowers.id("xenoform_foe_hud"),
		builder -> builder.syncWith(FoeHud.CODEC, AttachmentSyncPredicate.targetOnly()));
	/** The face a Mirage wears, for every client: {@code p:<uuid>:<name>} for a player, {@code e:<entity type id>} for a creature. Absent: its own. */
	public static final AttachmentType<String> FACE = AttachmentRegistry.create(JaymosPowers.id("xenoform_face"),
		builder -> builder.syncWith(ByteBufCodecs.STRING_UTF8, AttachmentSyncPredicate.all()));

	private static final Map<String, Long> NEXT = new HashMap<>();

	private XenoMinds() {
	}

	/** Called once from {@link XenoformPower#init()}. */
	static void init() {
		LightBlocks.register();
		// before the band's own damage hook, so a Wisp's hand and the bounce of jelly are settled first
		Identifier early = JaymosPowers.id("xenominds_early");
		ServerLivingEntityEvents.ALLOW_DAMAGE.addPhaseOrdering(early, Event.DEFAULT_PHASE);
		ServerLivingEntityEvents.ALLOW_DAMAGE.register(early, XenoMinds::allowDamage);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> forget(handler.player));
	}

	/** Whether {@code player} is {@code form} right now as the base alien (not in an Ultimate). */
	public static boolean is(ServerPlayer player, XenoForm form) {
		if (!XenoformPower.has(player)) {
			return false;
		}
		XenoformPower.Live live = XenoformPower.live(player);
		return live.form == form && live.ult == null && !live.ultimate;
	}

	private static boolean allowDamage(net.minecraft.world.entity.LivingEntity entity, DamageSource source, float amount) {
		if (source.getEntity() instanceof ServerPlayer attacker && attacker != entity && source.getDirectEntity() == attacker
			&& is(attacker, XenoForms.WISP) && WispHand.strike(attacker, entity, amount)) {
			return false;
		}
		if (!(entity instanceof ServerPlayer player) || !XenoformPower.has(player) || XenoformPower.live(player).form == null) {
			return true;
		}
		if (is(player, XenoForms.OOZE) || is(player, XenoForms.MAGMA_OOZE)) {
			return !OozeStretch.bounce(player, source);
		}
		if (is(player, XenoForms.WISP)) {
			return WispHand.struck(player, source, amount);
		}
		if (is(player, XenoForms.CORTEX)) {
			CortexMind.struck(player, source);
		}
		return true;
	}

	/** Everything a form of these keeps for {@code player} is let go. */
	public static void forget(ServerPlayer player) {
		OozeStretch.forget(player);
		LumenShapes.forget(player);
		MirageFace.forget(player);
		WispHand.forget(player);
		CortexMind.forget(player);
		String prefix = player.getUUID() + "/";
		NEXT.keySet().removeIf(k -> k.startsWith(prefix));
	}

	/** Whether the private cooldown {@code name} of {@code player} is over. */
	public static boolean ready(ServerPlayer player, String name) {
		return NEXT.getOrDefault(player.getUUID() + "/" + name, 0L) <= player.level().getGameTime();
	}

	/** Starts the private cooldown {@code name} of {@code player} for {@code ticks}. */
	public static void cool(ServerPlayer player, String name, int ticks) {
		NEXT.put(player.getUUID() + "/" + name, player.level().getGameTime() + ticks);
	}

	/** Sets {@code type} on {@code player} when it differs, so a tick that changed nothing sends nothing. */
	public static <T> void put(Entity player, AttachmentType<T> type, @Nullable T value) {
		T had = player.getAttached(type);
		if (value == null) {
			if (had != null) {
				player.removeAttached(type);
			}
		} else if (!value.equals(had)) {
			player.setAttached(type, value);
		}
	}
}
