package com.jaymoskingdoms.mixin.mca;

import com.jaymoskingdoms.Languages;
import com.jaymoskingdoms.McaCompat;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.interaction.EntityCommandHandler;
import net.conczin.mca.entity.interaction.VillagerCommandHandler;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * (0.5.21) MCA's Professions menu: its builder, farmer, miner and woodcutter buttons are the settlement's to answer ({@link McaCompat#promote}).
 * (0.5.29) And the main page's Quests button opens the villager's quests.
 * (0.7.55) And Change Clothes asks a tailor for a fitting ({@link com.jaymoskingdoms.Tailors}).
 * (0.7.69) And a tailor sells a Costume Table and sews costumes ({@link com.jaymoskingdoms.TailorCostumes}).
 * (0.7.109) A gift is remembered ({@link com.jaymoskingdoms.Recollections}): what it was, and whether it was welcome.
 * (0.7.203) Following, staying, being picked up or ridden, their armour and their home are only for folk they're
 * involved with ({@link com.jaymoskingdoms.Strangers}).
 * (0.7.454) And the Interact page's actions ({@link com.jaymoskingdoms.InteractActions}).
 * (0.7.454) And Interact's Ask the Way page asks after a place ({@link com.jaymoskingdoms.Directions}).
 * (0.7.281) Any button pressed holds them where they are for 30 seconds ({@link com.jaymoskingdoms.Attention}).
 * (0.7.20) A villager whose tongue you don't speak ({@link Languages}) only does what needs no words ({@link Languages#SIMPLE_COMMANDS}).
 */
@Mixin(VillagerCommandHandler.class)
abstract class McaCommandMixin extends EntityCommandHandler<VillagerEntityMCA> {
	McaCommandMixin(VillagerEntityMCA entity) {
		super(entity);
	}

	@Inject(method = "handle", at = @At("HEAD"), cancellable = true)
	private void jaymoskingdoms$job(ServerPlayer player, String command, CallbackInfoReturnable<Boolean> cir) {
		// (0.7.281) talking to them keeps them from teleporting off for a while
		com.jaymoskingdoms.Attention.heed(this.entity);
		if (com.jaymoskingdoms.Strangers.refuses(this.entity, player, command)) {
			// (0.7.203) they don't take orders from someone they're not involved with
			cir.setReturnValue(true);
			return;
		}
		if (!Languages.SIMPLE_COMMANDS.contains(command) && !Languages.understands(player, this.entity)) {
			// (0.7.20) asking that of them takes words you don't share
			Languages.shrug(player, this.entity);
			cir.setReturnValue(true);
			return;
		}
		// (0.5.29) the main page's Quests button
		if (command.equals("quests")) {
			com.jaymoskingdoms.Quests.openLater(player, this.entity);
			cir.setReturnValue(true);
			return;
		}
		// (0.7.55) the Change Clothes button: a tailor's fitting
		if (command.equals("tailor")) {
			com.jaymoskingdoms.Tailors.ask(player, this.entity);
			cir.setReturnValue(true);
			return;
		}
		// (0.7.69) a tailor's Costume Table, and a costume sewn to order
		if (command.equals(com.jaymoskingdoms.TailorCostumes.TABLE_COMMAND)) {
			com.jaymoskingdoms.TailorCostumes.sellTable(player, this.entity);
			cir.setReturnValue(true);
			return;
		}
		if (command.equals(com.jaymoskingdoms.TailorCostumes.SEW_COMMAND)) {
			com.jaymoskingdoms.TailorCostumes.sew(player, this.entity);
			cir.setReturnValue(true);
			return;
		}
		// (0.7.454) the Interact page's actions, moved off the talk menu (an audience, a pedlar's wares, tribute, a sworn
		// captain's orders)
		if (command.startsWith(com.jaymoskingdoms.InteractActions.COMMAND)) {
			this.stopInteracting();
			net.minecraft.network.chat.Component said = com.jaymoskingdoms.InteractActions.act(player, this.entity,
				command.substring(com.jaymoskingdoms.InteractActions.COMMAND.length()));
			if (said != null) {
				this.entity.sendChatMessage(said, player);
			}
			cir.setReturnValue(true);
			return;
		}
		// (0.7.454) Interact's Ask the Way: a place asked after
		if (command.startsWith(com.jaymoskingdoms.Directions.COMMAND)) {
			net.minecraft.network.chat.Component said = com.jaymoskingdoms.Directions.offers(this.entity)
				? com.jaymoskingdoms.Directions.answer(player, this.entity, command.substring(com.jaymoskingdoms.Directions.COMMAND.length())) : null;
			if (said != null) {
				this.entity.sendChatMessage(said, player);
				this.stopInteracting();
			}
			cir.setReturnValue(true);
			return;
		}
		if (command.startsWith("profession.") && McaCompat.promote(player, this.entity, command.substring("profession.".length()))) {
			cir.setReturnValue(true);
		}
	}

	/** (0.7.109) What's being given, and their hearts before. */
	@org.spongepowered.asm.mixin.Unique
	private net.minecraft.world.item.ItemStack jaymoskingdoms$gift = net.minecraft.world.item.ItemStack.EMPTY;
	@org.spongepowered.asm.mixin.Unique
	private int jaymoskingdoms$giftHearts;

	@Inject(method = "handle", at = @At("HEAD"))
	private void jaymoskingdoms$giftBefore(ServerPlayer player, String command, CallbackInfoReturnable<Boolean> cir) {
		if (command.equals("gift")) {
			this.jaymoskingdoms$gift = player.getMainHandItem().copy();
			this.jaymoskingdoms$giftHearts = this.entity.getVillagerBrain().getMemoriesForPlayer(player).getHearts();
		}
	}

	@Inject(method = "handle", at = @At("RETURN"))
	private void jaymoskingdoms$giftAfter(ServerPlayer player, String command, CallbackInfoReturnable<Boolean> cir) {
		if (command.equals("gift") && !this.jaymoskingdoms$gift.isEmpty()) {
			int now = this.entity.getVillagerBrain().getMemoriesForPlayer(player).getHearts();
			if (now != this.jaymoskingdoms$giftHearts) {
				com.jaymoskingdoms.Recollections.gifted(this.entity, player, this.jaymoskingdoms$gift, now > this.jaymoskingdoms$giftHearts);
			}
			this.jaymoskingdoms$gift = net.minecraft.world.item.ItemStack.EMPTY;
		}
	}
}
