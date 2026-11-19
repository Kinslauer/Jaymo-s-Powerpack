package com.jaymospowers.power.zfighter;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.KingdomsMoney;
import com.jaymospowers.core.Powers;
import com.jaymospowers.eden.Eden;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.ItemStack;

/**
 * The Patrol's relief runs (Andy's Buder's Lanterns pick, ideas only): errands that aren't fights. A patrolman asks an officer of
 * any rank to carry {@link #LEAVES} dried limphium leaves, the Verdant World's keeping herb, to a town's healer; the officer hands
 * them to any cleric (use the cleric with the leaves in hand) and is paid. One run at a time; each one done goes in their Patrol
 * file ({@link ZFighterPatrol}'s report).
 */
public final class ZFighterPatrolRelief {
	// ---- tuning ----
	/** The dried limphium leaves a run carries, what it pays (silver) and the training it gives (TP). */
	static final int LEAVES = 6, SILVER = 6, TP = 150;

	/** An officer's relief work: whether a run is under way, and how many they've done. */
	public record Relief(boolean open, int done) {
		static final Codec<Relief> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.BOOL.fieldOf("open").forGetter(Relief::open),
			Codec.INT.fieldOf("done").forGetter(Relief::done)).apply(i, Relief::new));
		static final Relief NONE = new Relief(false, 0);
	}

	public static final AttachmentType<Relief> RELIEF = AttachmentRegistry.create(JaymosPowers.id("patrol_relief"),
		builder -> builder.persistent(Relief.CODEC).copyOnDeath());

	private ZFighterPatrolRelief() {
	}

	static void init() {
		UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
			if (hand != InteractionHand.MAIN_HAND || !(player instanceof ServerPlayer sp) || !(entity instanceof Villager healer) || healer.isBaby()
				|| !healer.getVillagerData().profession().is(VillagerProfession.CLERIC)) {
				return InteractionResult.PASS;
			}
			ItemStack held = sp.getMainHandItem();
			if (!held.is(Eden.DRIED_LIMPHIUM_LEAF) || held.getCount() < LEAVES || !of(sp).open()) {
				return InteractionResult.PASS;
			}
			deliver(sp, healer, held);
			return InteractionResult.SUCCESS;
		});
	}

	static Relief of(ServerPlayer player) {
		return player.getAttachedOrElse(RELIEF, Relief.NONE);
	}

	/** "Relief work" with a patrolman: he gives a run, or reminds the officer of the one they're on. */
	static void ask(ServerPlayer player, LivingEntity man, Runnable back) {
		ZFighterNpcs.Option backOption = ZFighterNpcs.Option.of("talk.jaymospowers.zfighter.back", true, back);
		Relief relief = of(player);
		if (relief.open()) {
			ZFighterNpcs.ask(player, man, Component.translatable("talk.jaymospowers.patrol.relief.on_it", LEAVES), List.of(backOption));
			return;
		}
		ZFighterNpcs.ask(player, man, Component.translatable("talk.jaymospowers.patrol.relief.offer", LEAVES), List.of(
			ZFighterNpcs.Option.of("talk.jaymospowers.patrol.opt.relief_take", true, () -> {
				player.setAttached(RELIEF, new Relief(true, of(player).done()));
				ZFighterNpcs.ask(player, man, Component.translatable("talk.jaymospowers.patrol.relief.taken"), List.of(backOption));
			}),
			backOption));
	}

	/** The leaves handed to {@code healer}: the run is done and paid. */
	private static void deliver(ServerPlayer player, Villager healer, ItemStack held) {
		held.shrink(LEAVES);
		Relief relief = of(player);
		player.setAttached(RELIEF, new Relief(false, relief.done() + 1));
		ZFighterSagas.paySilver(player, SILVER);
		if (Powers.has(player, ZFighterPower.INSTANCE)) {
			ZFighterTraining.grant(player, TP);
		}
		healer.getLookControl().setLookAt(player);
		player.level().playSound(null, healer.getX(), healer.getY(), healer.getZ(), SoundEvents.VILLAGER_YES, SoundSource.NEUTRAL, 1.0F, 1.0F);
		player.sendSystemMessage(Component.translatable("message.jaymospowers.patrol.relief_done", healer.getDisplayName(),
			KingdomsMoney.text(player, KingdomsMoney.worth(SILVER)), relief.done() + 1).withStyle(ChatFormatting.AQUA));
	}
}
