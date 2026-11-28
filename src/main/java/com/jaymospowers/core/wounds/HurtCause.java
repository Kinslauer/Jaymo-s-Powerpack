package com.jaymospowers.core.wounds;

import com.jaymospowers.JaymosPowers;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Why you're hurt. Andy's clips "stop hitting me" and "what is hitting me here?": hearts fell and nothing on screen said
 * why ("the player needs to always know why they are taking damage so they can prevent it"). Every hurt a player takes
 * goes to their client ({@link Hurt}); the client's {@code HurtCauseHud} decides what to show. Andy (2026-11-28): "the hit
 * indicators should only tell you once if you are hit and that should be at the very start of combat. it should NOT be
 * naming the attackers, nor should it be as descriptive". So a blow from someone ({@code fight}) only says "I'm hit!"
 * at the start of a fight, and never who did it; a hurt nobody dealt (the sun, fire, hunger, a fall) says its short cause
 * once, when it starts.
 *
 * <p>A hurt whose damage type says nothing (vanilla {@code generic}, {@code magic}, {@code genericKill}, or any vanilla
 * type one of our features borrows) is named by the feature that dealt it: the first of our classes on the stack
 * ({@link #from}), whose line is {@code hurt_cause.jaymospowers.from.<Class>}. The server log names the damage type, that
 * class and the attacker, so a hurt nobody can name can still be traced.
 */
public final class HurtCause {
	/** The same cause is logged again after this long (ticks). */
	static final int REPEAT_TICKS = 200;
	/** Our own packages: the first class of these on the stack dealt the hurt. */
	private static final String[] OURS = {"com.jaymospowers.", "com.jaymoskingdoms.", "com.jaymoskitchen."};

	/** The cause last logged for each player, and when. */
	private record Said(String id, long at) {
	}

	private static final Map<ServerPlayer, Said> SAID = new WeakHashMap<>();

	/**
	 * Server → the player hurt: {@code kind} the damage type's message id; {@code from} the class of ours that dealt it
	 * (simple name, "" if none); {@code fight} a blow someone or something dealt (a swing, an arrow, a bite, a blast);
	 * {@code attacker} the one who did it, by entity id (-1: none), for the side mark to follow, never named; {@code at}
	 * where it came from, if anywhere; {@code amount} the health it took.
	 */
	public record Hurt(String kind, String from, boolean fight, int attacker, Optional<Vec3> at, float amount)
		implements CustomPacketPayload {
		public static final Type<Hurt> TYPE = new Type<>(JaymosPowers.id("hurt_cause"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Hurt> CODEC = StreamCodec.composite(
			ByteBufCodecs.STRING_UTF8, Hurt::kind,
			ByteBufCodecs.STRING_UTF8, Hurt::from,
			ByteBufCodecs.BOOL, Hurt::fight,
			ByteBufCodecs.VAR_INT, Hurt::attacker,
			ByteBufCodecs.optional(Vec3.STREAM_CODEC), Hurt::at,
			ByteBufCodecs.FLOAT, Hurt::amount,
			Hurt::new);

		@Override
		public Type<Hurt> type() {
			return TYPE;
		}
	}

	private HurtCause() {
	}

	static void register() {
		PayloadTypeRegistry.clientboundPlay().register(Hurt.TYPE, Hurt.CODEC);
		ServerLivingEntityEvents.AFTER_DAMAGE.register(HurtCause::hurt);
	}

	private static void hurt(LivingEntity victim, DamageSource source, float base, float taken, boolean blocked) {
		if (!(victim instanceof ServerPlayer player) || taken <= 0.0F || player.isCreative() || player.isSpectator()) {
			return;
		}
		Entity by = source.getEntity() != null ? source.getEntity() : source.getDirectEntity();
		if (by == player) {
			// hurt by their own hand (a power's price, a carving): the feature that did it says what
			by = null;
		}
		String id = source.getMsgId();
		String from = by == null ? from() : "";
		Vec3 at = source.getSourcePosition();
		if (at != null && at.distanceToSqr(player.position()) < 0.25) {
			// (a hurt "from" where you stand has no side)
			at = null;
		}
		Entity direct = source.getDirectEntity();
		boolean fight = by != null || direct != null && direct != player || source.is(DamageTypeTags.IS_EXPLOSION);
		log(player, by, id, from, taken);
		if (ServerPlayNetworking.canSend(player, Hurt.TYPE)) {
			ServerPlayNetworking.send(player, new Hurt(id, from, fight, by == null ? -1 : by.getId(), Optional.ofNullable(at), taken));
		} else if (!fight && !Downed.isDowned(player)) {
			// (a client without the mod: the action bar, as before)
			player.sendOverlayMessage(Component.translatableWithFallback("hurt_cause.jaymospowers." + id, "Ugh... " + plain(id) + "!")
				.withStyle(ChatFormatting.RED));
		}
	}

	/** To the server log, once per cause per {@link #REPEAT_TICKS}: what hurt whom, by which type and which of our classes. */
	private static void log(ServerPlayer player, Entity by, String id, String from, float taken) {
		String key = id + "|" + from + "|" + (by == null ? "" : by.getType().toString());
		long now = player.level().getGameTime();
		Said said = SAID.get(player);
		if (said != null && said.id().equals(key) && now - said.at() < REPEAT_TICKS) {
			return;
		}
		SAID.put(player, new Said(key, now));
		if (by == null) {
			JaymosPowers.LOGGER.info("{} hurt by {}{} ({} health), nobody did it", player.getScoreboardName(), id,
				from.isEmpty() ? "" : " from " + from, taken);
		} else {
			JaymosPowers.LOGGER.info("{} hurt by {} ({} health), done by {} ({})", player.getScoreboardName(), id, taken,
				by.getName().getString(), by.getType());
		}
	}

	/**
	 * The simple name of the first of our classes on the stack below the hurt: the feature that dealt it ("" if none of
	 * ours did). Mixins don't show (their code runs as the class they're in); a nested class gives its outer class.
	 */
	static String from() {
		return StackWalker.getInstance().walk(frames -> frames
			.map(StackWalker.StackFrame::getClassName)
			.filter(HurtCause::ours)
			.filter(name -> !name.equals(HurtCause.class.getName()) && !name.contains(".mixin."))
			.findFirst()
			.map(HurtCause::simple)
			.orElse(""));
	}

	private static boolean ours(String name) {
		for (String root : OURS) {
			if (name.startsWith(root)) {
				return true;
			}
		}
		return false;
	}

	/** {@code com.jaymospowers.power.ember.RunningHot$Burn} is "RunningHot". */
	private static String simple(String name) {
		String last = name.substring(name.lastIndexOf('.') + 1);
		int nested = last.indexOf('$');
		return nested > 0 ? last.substring(0, nested) : last;
	}

	/** A damage type's id as words, for one without a line of its own: {@code jaymoskingdoms.old_age} is "old age". */
	public static String plain(String id) {
		String last = id.substring(id.lastIndexOf('.') + 1);
		return last.replaceAll("([a-z])([A-Z])", "$1 $2").replace('_', ' ').toLowerCase(java.util.Locale.ROOT);
	}
}
