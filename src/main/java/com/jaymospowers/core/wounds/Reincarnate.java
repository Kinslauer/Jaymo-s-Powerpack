package com.jaymospowers.core.wounds;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.Carry;
import com.jaymospowers.power.zfighter.ZFighterAfterlife;
import io.netty.buffer.ByteBuf;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * Andy, 2026-11-26: "i'd like a reincarnate button to put you right back on the story". The Reincarnate link on the
 * character menu's first page (H) lets a living player end this character by choice, as a final death would
 * ({@link Mortality#chooseToEnd}): the screen goes black, everything is wiped and the life story is told anew, MCA's creator
 * first. Not while dying, dead by our reckoning, carried, or already being made anew.
 */
public final class Reincarnate {
	/** The key {@link Mortality#FINAL} holds for a life ended by choice. */
	public static final String CHOSEN = "chosen";

	/** Client to server: the player confirmed they want to reincarnate. */
	public record Ask() implements CustomPacketPayload {
		public static final Ask INSTANCE = new Ask();
		public static final Type<Ask> TYPE = new Type<>(JaymosPowers.id("reincarnate"));
		public static final StreamCodec<ByteBuf, Ask> CODEC = StreamCodec.unit(INSTANCE);

		@Override
		public Type<Ask> type() {
			return TYPE;
		}
	}

	private Reincarnate() {
	}

	static void register() {
		PayloadTypeRegistry.serverboundPlay().register(Ask.TYPE, Ask.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Ask.TYPE, (payload, ctx) -> asked(ctx.player()));
	}

	/**
	 * Whether {@code player} may choose to reincarnate now, as far as their own client can tell (both sides: these
	 * attachments are synced): alive, on their feet, not carried, and not already between lives.
	 */
	public static boolean can(Player player) {
		return player.isAlive() && !player.isSpectator() && !Downed.isDowned(player) && !Carry.isCarried(player)
			&& !Mortality.isFinal(player) && !player.hasAttached(Mortality.REBORNING) && !Limbo.in(player);
	}

	private static void asked(ServerPlayer player) {
		if (!can(player) || Mortality.dead(player) || Mortality.justWoke(player) || Revival.held(player)
			|| ZFighterAfterlife.among(player) || ZFighterAfterlife.making(player) || !ZFighterAfterlife.storyAhead(player)) {
			player.sendSystemMessage(Component.translatable("message.jaymospowers.reincarnate.not_now"), true);
			return;
		}
		Mortality.chooseToEnd(player);
	}
}
