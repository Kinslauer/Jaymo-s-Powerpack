package com.jaymoskitchen;

import com.jaymoskitchen.cooking.Grill;
import com.jaymoskitchen.corpse.CorpseEntity;
import com.jaymoskitchen.corpse.Corpses;
import com.jaymoskitchen.corpse.PlayerCorpses;
import com.jaymoskitchen.horse.Saddlebags;
import com.jaymoskitchen.farm.Sowing;
import com.jaymoskitchen.mill.Mill;
import com.jaymoskitchen.table.Tables;
import com.jaymoskitchen.cooking.Kitchen;
import com.jaymoskitchen.cooking.Prep;
import com.jaymoskitchen.cooking.Sear;
import com.jaymoskitchen.cooking.Smoke;
import com.jaymoskitchen.cooking.Station;
import com.jaymoskitchen.net.Payloads;
import com.jaymoskitchen.quiver.Quivers;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JaymosKitchen implements ModInitializer {
	public static final String MOD_ID = "jaymoskitchen";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	@Override
	public void onInitialize() {
		// payload types first: Fabric refuses a receiver for a payload it doesn't know yet (Butchers' butcher_deal
		// crashed the game at startup in 0.4.32)
		Payloads.register();
		com.jaymoskitchen.ragdoll.RagdollNet.register();
		// (0.5.1) living players going limp (its LIMP attachment is class-loaded here, on both sides)
		com.jaymoskitchen.ragdoll.LivingRagdolls.register();
		ModItems.register();
		com.jaymoskitchen.fishing.Bait.register();
		Quivers.register();
		com.jaymoskitchen.archery.LegShots.register();
		com.jaymoskitchen.archery.StuckArrows.register();
		Corpses.register();
		// (0.5.18) spiders crushed flat leave loose legs, not a corpse
		com.jaymoskitchen.corpse.SpiderCrush.register();
		// (0.5.21) bodies blown apart by blasts leave flying chunks, not a corpse
		com.jaymoskitchen.corpse.Gibs.register();
		com.jaymoskitchen.corpse.Butchers.register();
		com.jaymoskitchen.corpse.Scavengers.register();
		PlayerCorpses.register();
		Saddlebags.register();
		Mill.register();
		com.jaymoskitchen.brewing.Brewing.register();
		com.jaymoskitchen.kitchen.KitchenBlocks.register();
		com.jaymoskitchen.kitchen.OvenHob.register();
		com.jaymoskitchen.cooking.ExplodingPot.register();
		com.jaymoskitchen.kitchen.HobQueue.register();
		com.jaymoskitchen.kitchen.CookingTableBridge.register();
		Tables.register();
		Sowing.register();
		com.jaymoskitchen.farm.Hemp.register();
		// fat, tallow, bark and tanning (its SOAKS attachment is class-loaded here)
		com.jaymoskitchen.tanning.Tanning.register();
		com.jaymoskitchen.farm.Manure.register();
		com.jaymoskitchen.farm.LayingHens.register();
		com.jaymoskitchen.stomach.Stomach.register();
		com.jaymoskitchen.season.Seasons.register();
		// (0.5.25) thirst and body heat (their attachments are class-loaded there; its block use goes before the kitchen's)
		com.jaymoskitchen.climate.Climate.register();
		com.jaymoskitchen.stomach.RawFood.register();

		// sneak + right-click with an empty hand: a lit campfire, furnace, smoker or blast furnace opens the kitchen;
		// the top of a wood, stone or slab block opens the prep board (also with Pmeh in hand, to roll a joint)
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (hand != InteractionHand.MAIN_HAND || player.isSpectator()) {
				return InteractionResult.PASS;
			}
			// carrying a corpse: right-clicking a block sets it down there (on top, for a butcher block)
			CorpseEntity carried = CorpseEntity.carriedBy(player);
			if (carried != null) {
				if (!level.isClientSide()) {
					BlockPos clicked = hit.getBlockPos();
					Vec3 at;
					if (Corpses.isButcherBlock(level.getBlockState(clicked))) {
						at = CorpseEntity.tableSpot(level, clicked, hit.getLocation(), carried.isBigCarcass());
						if (at == null) {
							player.sendOverlayMessage(Component.translatable("message.jaymoskitchen.corpse.big_table"));
							return InteractionResult.SUCCESS;
						}
					} else {
						// anywhere else it just drops from the arms
						carried.drop(player);
						return InteractionResult.SUCCESS;
					}
					carried.putDown(at, player.getYRot() + 90.0F);
				}
				return InteractionResult.SUCCESS;
			}
			Station heat = Station.of(level, hit.getBlockPos());
			if (player instanceof ServerPlayer reheater && heat != null && heat.heats()
				&& Kitchen.reheat(reheater, hit.getBlockPos(), player.getMainHandItem())) {
				return InteractionResult.SUCCESS;
			}
			boolean sneakingEmptyHanded = player.isShiftKeyDown() && player.getMainHandItem().isEmpty();
			boolean holdingPmeh = player.getMainHandItem().is(ModItems.PMEH);
			if (!sneakingEmptyHanded && !holdingPmeh) {
				return InteractionResult.PASS;
			}
			BlockPos pos = hit.getBlockPos();
			BlockState state = level.getBlockState(pos);
			Station station = Station.of(level, pos);
			if (station == null && sneakingEmptyHanded && state.getBlock() instanceof net.blay09.mods.cookingforblockheads.block.OvenBlock
				&& hit.getDirection() != net.minecraft.core.Direction.UP) {
				// an oven that's off: fall through to its own sneak-click (the door), with a reminder
				if (player instanceof ServerPlayer serverPlayer) {
					serverPlayer.sendOverlayMessage(net.minecraft.network.chat.Component.translatable("message.jaymoskitchen.oven_off"));
				}
				return InteractionResult.PASS;
			}
			// a cutting board opens the prep screen below, which has the chopping on it
			if (station != null && station != Station.BOARD && sneakingEmptyHanded) {
				if (player instanceof ServerPlayer serverPlayer) {
					ServerPlayNetworking.send(serverPlayer, new Payloads.OpenKitchen(pos, station.ordinal(), Kitchen.stocked(serverPlayer, pos, station)));
				}
				return InteractionResult.SUCCESS;
			}
			if (Prep.isPrepSurface(level, pos, state, hit.getDirection())) {
				if (player instanceof ServerPlayer serverPlayer) {
					// with Pmeh in hand, go straight to rolling
					int recipe = holdingPmeh ? Prep.Recipe.JOINT.ordinal() : -1;
					ServerPlayNetworking.send(serverPlayer, new Payloads.OpenPrep(pos, recipe, Prep.stocked(serverPlayer, pos)));
				}
				return InteractionResult.SUCCESS;
			}
			return InteractionResult.PASS;
		});

		// (0.4.48) carrying a body, right-click a horse (a donkey, mule, llama, camel): it's slung over its back
		net.fabricmc.fabric.api.event.player.UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (hand != InteractionHand.MAIN_HAND || player.isSpectator()
				|| !(entity instanceof net.minecraft.world.entity.animal.equine.AbstractHorse horse)) {
				return InteractionResult.PASS;
			}
			CorpseEntity carried = CorpseEntity.carriedBy(player);
			if (carried == null) {
				return InteractionResult.PASS;
			}
			if (!level.isClientSide()) {
				carried.slingOnto(player, horse);
			}
			return InteractionResult.SUCCESS;
		});
		ServerPlayNetworking.registerGlobalReceiver(Payloads.DropCarried.TYPE, (payload, ctx) -> {
			CorpseEntity carried = CorpseEntity.carriedBy(ctx.player());
			if (carried != null) {
				if (!payload.toss() && ctx.player().getVehicle() instanceof net.minecraft.world.entity.animal.equine.AbstractHorse horse) {
					// (0.4.48) in the saddle: thrown up over your horse's back behind you (or, too big, it stays in your arms)
					carried.slingOnto(ctx.player(), horse);
				} else if (payload.toss()) {
					carried.toss(ctx.player());
				} else {
					carried.drop(ctx.player());
				}
			}
		});
		ServerPlayNetworking.registerGlobalReceiver(Payloads.StartCooking.TYPE,
			(payload, ctx) -> Kitchen.start(ctx.player(), payload.pos(), payload.dish(), payload.station()));
		ServerPlayNetworking.registerGlobalReceiver(Payloads.PressStep.TYPE,
			(payload, ctx) -> Kitchen.press(ctx.player(), payload.step()));
		ServerPlayNetworking.registerGlobalReceiver(Payloads.Abandon.TYPE,
			(payload, ctx) -> Kitchen.abandon(ctx.player()));
		ServerPlayNetworking.registerGlobalReceiver(Payloads.GrillAction.TYPE, (payload, ctx) -> {
			if (payload.action() == Payloads.GrillAction.FLIP) {
				Grill.flip(ctx.player());
			} else if (payload.action() == Payloads.GrillAction.SERVE) {
				Grill.serve(ctx.player());
			}
		});
		ServerPlayNetworking.registerGlobalReceiver(Payloads.StartPrep.TYPE,
			(payload, ctx) -> Prep.start(ctx.player(), payload.pos(), Prep.Recipe.byIndex(payload.recipe())));
		ServerPlayNetworking.registerGlobalReceiver(Payloads.FinishRoll.TYPE, (payload, ctx) ->
			Prep.finishRoll(ctx.player(), payload.ground(), payload.evenness(), payload.tears(), payload.rolled()));
		ServerPlayNetworking.registerGlobalReceiver(Payloads.FinishPrep.TYPE,
			(payload, ctx) -> Prep.finish(ctx.player(), payload.water(), payload.oil(), payload.kneaded()));
		ServerPlayNetworking.registerGlobalReceiver(Payloads.SmokeFinish.TYPE,
			(payload, ctx) -> Smoke.finish(ctx.player(), payload.scorchTicks(), payload.coldTicks(), payload.cured()));
		ServerPlayNetworking.registerGlobalReceiver(Payloads.SearFinish.TYPE,
			(payload, ctx) -> Sear.finish(ctx.player(), payload.perfects(), payload.goods(), payload.misses()));
		ServerPlayNetworking.registerGlobalReceiver(Payloads.MinigameFinish.TYPE,
			(payload, ctx) -> com.jaymoskitchen.cooking.Minigames.finish(ctx.player(), payload.a(), payload.b(), payload.c(), payload.d()));
		ServerTickEvents.END_SERVER_TICK.register(Kitchen::tick);
		ServerTickEvents.END_SERVER_TICK.register(Quivers::tick);
		ServerTickEvents.END_SERVER_TICK.register(Grill::tick);
	}
}
