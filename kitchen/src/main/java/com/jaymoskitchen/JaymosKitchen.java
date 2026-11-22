package com.jaymoskitchen;

import com.jaymoskitchen.cooking.Grill;
import com.jaymoskitchen.cooking.HandsOnSession;
import com.jaymoskitchen.cooking.HandsOnStation;
import com.jaymoskitchen.cooking.Kitchen;
import com.jaymoskitchen.cooking.Minigames;
import com.jaymoskitchen.cooking.Prep;
import com.jaymoskitchen.cooking.Sear;
import com.jaymoskitchen.cooking.Smoke;
import com.jaymoskitchen.cooking.Station;
import com.jaymoskitchen.corpse.CorpseEntity;
import com.jaymoskitchen.corpse.Corpses;
import com.jaymoskitchen.corpse.PlayerCorpses;
import com.jaymoskitchen.delight.CookwareBlock;
import com.jaymoskitchen.delight.CuttingBoardEntity;
import com.jaymoskitchen.horse.Saddlebags;
import com.jaymoskitchen.farm.Sowing;
import com.jaymoskitchen.mill.Mill;
import com.jaymoskitchen.table.Tables;
import com.jaymoskitchen.net.Payloads;
import com.jaymoskitchen.net.payloads.HandsOnAbandonPayload;
import com.jaymoskitchen.net.payloads.HandsOnInputPayload;
import com.jaymoskitchen.quiver.Quivers;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JaymosKitchen implements ModInitializer {
	public static final String MOD_ID = "jaymoskitchen";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	/** Whether hands-on, in-world cooking is enabled (falls back to screen-based when false). */
	public static boolean HANDS_ON_COOKING = true;

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	@Override
	public void onInitialize() {
		// payload types first: Fabric refuses a receiver for a payload it doesn't know yet (Butchers' butcher_deal
		// crashed the game at startup in 0.4.32)
		Payloads.register();
		com.jaymoskitchen.cooking.PlacedSlabs.register();
		com.jaymoskitchen.cooking.hands.HandsOn.register();
		com.jaymoskitchen.ragdoll.RagdollNet.register();
		// (0.5.1) living players going limp (its LIMP attachment is class-loaded here, on both sides)
		com.jaymoskitchen.ragdoll.LivingRagdolls.register();
		// mobs knocked limp by heavy blows and big blades (its LIMP attachment is class-loaded here, on both sides)
		com.jaymoskitchen.ragdoll.MobRagdolls.register();
		// Ragdollified plays a dying body's fall (the corpse lies down where it lands)
		com.jaymoskitchen.compat.RagdollifiedCompat.register();
		ModItems.register();
		// (0.5.29) Pmeh grows wild only in jungles and savannas (no more from tall grass)
		com.jaymoskitchen.herb.PmehFarming.register();
		com.jaymoskitchen.fishing.Bait.register();
		Quivers.register();
		com.jaymoskitchen.archery.LegShots.register();
		// (0.5.84) the risen dead get up from where they lay
		com.jaymoskitchen.corpse.Reanimation.register();
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
		// echoes for the layered sounds
		com.jaymoskitchen.sound.Soundscape.register();
		Mill.register();
		// grapes and the vintner's things first: the brews are made from them
		com.jaymoskitchen.vintner.Vintner.register();
		com.jaymoskitchen.brewing.Brewing.register();
		com.jaymoskitchen.kitchen.KitchenBlocks.register();
		com.jaymoskitchen.cooking.spit.SpitBlock.register();
		// (0.5.28) Farmer's Delight's pot and pan over a fire, cutting board, feasts, Comfort, cabbages and rice
		com.jaymoskitchen.delight.Delight.register();
		// (0.5.33) Farmer's Delight's skillet, stove, cabinets, crates, baskets, canvas, tatami, safety net and canvas signs
		com.jaymoskitchen.delight.DelightHome.register();
		com.jaymoskitchen.delight.HomeComfort.register();
		// (0.5.33) the rest of Farmer's Delight's food: slices, milk bottle, ham, dog food and horse feed, and its pies
		com.jaymoskitchen.delight.DelightDishes.register();
		// (0.5.33) Farmer's Delight's tomatoes, onions, rope, wild crops, rich soil, compost, mushroom colonies, villages
		com.jaymoskitchen.delight.DelightFarm.register();
		com.jaymoskitchen.kitchen.OvenHob.register();
		com.jaymoskitchen.cooking.ExplodingPot.register();
		com.jaymoskitchen.kitchen.HobQueue.register();
		com.jaymoskitchen.kitchen.CookingTableBridge.register();
		Tables.register();
		Sowing.register();
		com.jaymoskitchen.farm.Hemp.register();
		// fat, tallow, bark and tanning (its SOAKS attachment is class-loaded here)
		com.jaymoskitchen.tanning.Tanning.register();
		com.jaymoskitchen.tanning.Hides.register();
		// (0.5.51) meat hooks, blood grates and bottled blood (corpse/Hanging does the butchering)
		com.jaymoskitchen.butchery.Butchery.register();
		// (Butchery picks) the mincer and mince, the butcher's display, head mounts, the barrel of bones
		com.jaymoskitchen.butchery.ButcherShop.register();
		com.jaymoskitchen.butchery.Skeletons.register();
		// rats after food left out, and the traps for them
		com.jaymoskitchen.rats.Rats.register();
		com.jaymoskitchen.farm.Manure.register();
		com.jaymoskitchen.farm.TiredSoil.register();
		// (GT New Horizons picks) slow herds and crowded pens; before Familiarity, so a milked cow gives no extra bucket
		com.jaymoskitchen.farm.Herds.register();
		com.jaymoskitchen.farmwater.FarmWater.register();
		com.jaymoskitchen.farm.Familiarity.register();
		// (GT New Horizons picks) farmland only near water, seeds scratched from dry grass
		com.jaymoskitchen.farm.DryFields.register();
		com.jaymoskitchen.farm.LayingHens.register();
		com.jaymoskitchen.stomach.Stomach.register();
		// (GT New Horizons picks) a heart for a varied diet, its food journal (its attachment is class-loaded here), the lunch box
		com.jaymoskitchen.stomach.Variety.register();
		com.jaymoskitchen.stomach.LunchBox.register();
		com.jaymoskitchen.hardcore.Hardcore.register();
		com.jaymoskitchen.fresh.Freshness.register();
		com.jaymoskitchen.fresh.Curing.register();
		com.jaymoskitchen.fresh.Cheese.register();
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
			// (0.5.31) dragging a carcass: onto a butcher table near it, or let go
			CorpseEntity dragged = CorpseEntity.draggedBy(player);
			if (dragged != null) {
				if (!level.isClientSide()) {
					dragged.dragOnto(player, hit.getBlockPos(), hit.getLocation());
				}
				return InteractionResult.SUCCESS;
			}
			// carrying a corpse: right-clicking a block sets it down there (on top, for a butcher block)
			CorpseEntity carried = CorpseEntity.carriedBy(player);
			if (carried != null) {
				if (!level.isClientSide()) {
					BlockPos clicked = hit.getBlockPos();
					BlockPos hook = com.jaymoskitchen.corpse.Hanging.hookAt(level, clicked, hit.getLocation());
					if (hook != null) {
						// (0.5.51) hung up on a meat hook by its hind legs
						com.jaymoskitchen.corpse.Hanging.hang(carried, player, hook);
						return InteractionResult.SUCCESS;
					}
					BlockState clickedState = level.getBlockState(clicked);
					if (com.jaymoskitchen.cooking.spit.GreatSpitBlock.is(clickedState)) {
						// a dressed carcass heaved onto the great spit, to turn there whole
						com.jaymoskitchen.cooking.spit.GreatSpitBlock.skewer(carried, player, clicked);
						return InteractionResult.SUCCESS;
					}
					if (clickedState.is(net.minecraft.tags.BlockTags.CAMPFIRES) && carried.oneHanded()) {
						// (0.5.77) a small animal laid on the campfire to roast whole (see CorpseEntity#roast)
						carried.putDown(Vec3.atBottomCenterOf(clicked).add(0.0, clickedState.getShape(level, clicked).max(net.minecraft.core.Direction.Axis.Y), 0.0),
							player.getYRot() + 90.0F);
						return InteractionResult.SUCCESS;
					}
					Vec3 at;
					if (Corpses.isButcherBlock(clickedState)) {
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

					// Hands-on cooking: right-click station with correct tool to start in-world session
					if (HANDS_ON_COOKING && !level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
						ItemStack mainHand = player.getMainHandItem();
						BlockPos pos = hit.getBlockPos();
						BlockState state = level.getBlockState(pos);
						Station station = Station.of(level, pos);

						// CHOP: cutting board with knife
						if (station == Station.BOARD && mainHand.is(Delight.KNIVES)) {
							if (level.getBlockEntity(pos) instanceof CuttingBoardEntity board) {
								ItemStack onBoard = board.item();
								if (!onBoard.isEmpty() && Chopping.cut(onBoard).isPresent()) {
									// Find a CHOP dish that uses this ingredient
									Dish dish = findChopDishForIngredient(onBoard.getItem());
									if (dish != null && station.allows(dish)) {
										startHandsOnSession(serverPlayer, pos, station, dish);
										return InteractionResult.SUCCESS;
									}
								}
							}
						}

						// STIR: heated cookware pot with spoon
						if (state.getBlock() instanceof CookwareBlock cookware && cookware.isPot() && CookwareBlock.heated(level, pos)
								&& mainHand.is(Delight.SPOONS)) {
							Station hearthStation = cookware.isPot() ? Station.HEARTH_POT : Station.HEARTH_PAN;
							if (hearthStation.allows(findAnyStirDish())) {
								startHandsOnSession(serverPlayer, pos, hearthStation, findAnyStirDish());
								return InteractionResult.SUCCESS;
							}
						}
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
			if (station == null && sneakingEmptyHanded && state.getBlock() instanceof com.jaymoskitchen.delight.CookwareBlock) {
				// a pot or pan with no fire under it
				if (player instanceof ServerPlayer serverPlayer) {
					serverPlayer.sendOverlayMessage(Component.translatable("message.jaymoskitchen.cookware_cold"));
				}
				return InteractionResult.SUCCESS;
			}
			// cooking by hand: no screen; sneaking empty-handed says what is on the station and what it would make
			if (com.jaymoskitchen.cooking.hands.HandsOn.on(level)) {
				if (sneakingEmptyHanded && (station != null || Prep.isPrepSurface(level, pos, state, hit.getDirection()))) {
					if (player instanceof ServerPlayer serverPlayer) {
						com.jaymoskitchen.cooking.hands.HandsOn.hintAt(serverPlayer, pos);
					}
					return InteractionResult.SUCCESS;
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
			CorpseEntity dragged = CorpseEntity.draggedBy(ctx.player());
			if (dragged != null) {
				// (0.5.31) let go of a carcass being dragged
				dragged.stopDrag();
				return;
			}
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
		ServerPlayNetworking.registerGlobalReceiver(Payloads.HangOn.TYPE, (payload, ctx) -> {
			// a meat hook clicked while carrying or dragging a carcass: hung there, never let go of
			net.minecraft.server.level.ServerPlayer player = ctx.player();
			CorpseEntity body = CorpseEntity.draggedBy(player);
			if (body == null) {
				body = CorpseEntity.carriedBy(player);
			}
			BlockPos hook = payload.pos();
			if (body == null || !com.jaymoskitchen.butchery.Butchery.isHook(player.level().getBlockState(hook))) {
				return;
			}
			if (!player.isWithinBlockInteractionRange(hook, 1.0)) {
				player.sendOverlayMessage(Component.translatable("message.jaymoskitchen.hook.closer"));
				return;
			}
			com.jaymoskitchen.corpse.Hanging.hang(body, player, hook);
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
				ServerPlayNetworking.registerGlobalReceiver(HandsOnInputPayload.TYPE, (payload, ctx) -> {
					if (payload instanceof HandsOnInputPayload.ChopInput ci) {
						handleHandsOnChop(ctx.player(), ci);
					} else if (payload instanceof HandsOnInputPayload.StirInput si) {
						handleHandsOnStir(ctx.player(), si);
					}
				});
				ServerPlayNetworking.registerGlobalReceiver(HandsOnAbandonPayload.TYPE,
						(payload, ctx) -> handleHandsOnAbandon(ctx.player(), payload.sessionId()));
		ServerTickEvents.END_SERVER_TICK.register(Kitchen::tick);
				ServerTickEvents.END_SERVER_TICK.register(Quivers::tick);
				ServerTickEvents.END_SERVER_TICK.register(Grill::tick);

				// Hands-on cooking tick: advance all active sessions on all stations
				ServerTickEvents.END_SERVER_TICK.register(HandsOnTicker::tick);
			}

			/** Handles a CHOP input from the client. */
			private static void handleHandsOnChop(ServerPlayer player, HandsOnInputPayload.ChopInput input) {
				ServerLevel level = player.serverLevel();
				// Find the session by UUID
				for (var entry : getAllHandsOnSessions(level).entrySet()) {
					HandsOnSession session = entry.getValue();
					if (session.player().equals(player.getUUID()) && session.method() == com.jaymoskitchen.cooking.Dish.Method.CHOP) {
						session.applyInput(input);
						// Check if done
						if (session instanceof HandsOnSession.ChopState chop && chop.nextCut() >= com.jaymoskitchen.cooking.Minigames.CUTS) {
							finishHandsOnSession(player, level, session);
						}
						return;
					}
				}
			}

			/** Handles a STIR input from the client. */
			private static void handleHandsOnStir(ServerPlayer player, HandsOnInputPayload.StirInput input) {
				ServerLevel level = player.serverLevel();
				for (var entry : getAllHandsOnSessions(level).entrySet()) {
					HandsOnSession session = entry.getValue();
					if (session.player().equals(player.getUUID()) && session.method() == com.jaymoskitchen.cooking.Dish.Method.STIR) {
						session.applyInput(input);
						if (session instanceof HandsOnSession.StirState stir && stir.stirred() >= com.jaymoskitchen.cooking.Minigames.STIR_TICKS) {
							finishHandsOnSession(player, level, session);
						}
						return;
					}
				}
			}

			/** Handles abandoning a hands-on session (walked away). */
			private static void handleHandsOnAbandon(ServerPlayer player, java.util.UUID sessionId) {
				ServerLevel level = player.serverLevel();
				for (var entry : getAllHandsOnSessions(level).entrySet()) {
					HandsOnSession session = entry.getValue();
					if (session.player().equals(sessionId)) {
						finishHandsOnSession(player, level, session, true);
						return;
					}
				}
			}

			/** Finishes a hands-on session and awards the dish. */
			private static void finishHandsOnSession(ServerPlayer player, ServerLevel level, HandsOnSession session) {
				finishHandsOnSession(player, level, session, false);
			}

			private static void finishHandsOnSession(ServerPlayer player, ServerLevel level, HandsOnSession session, boolean abandoned) {
				Quality quality;
				if (abandoned) {
					quality = session.method() == com.jaymoskitchen.cooking.Dish.Method.CHOP ? Quality.BURNT : Quality.CHARRED;
				} else {
					quality = session.finishQuality();
				}
				// Remove session from station
				removeHandsOnSession(level, session.player(), session.stationPos());
				// Award
				com.jaymoskitchen.cooking.Kitchen.award(player, session.dish(), session.stationPos(), quality);
				// Send finish packet
				ItemStack[] rewards = new ItemStack[]{new ItemStack(com.jaymoskitchen.ModItems.dish(session.dish()), session.dish().batch())};
				quality.apply(rewards[0], session.dish());
				ServerPlayNetworking.send(player, new HandsOnFinishPayload(sessionId(), quality.ordinal(), rewards));
			}

			/** Gets all hands-on sessions across all stations in the level. */
			private static java.util.Map<java.util.UUID, HandsOnSession> getAllHandsOnSessions(ServerLevel level) {
				java.util.Map<java.util.UUID, HandsOnSession> all = new java.util.HashMap<>();
				// Cutting boards
				for (var be : level.getBlockEntities()) {
					if (be instanceof CuttingBoardEntity board) {
						all.putAll(board.getHandsOnSessions());
					} else if (be instanceof com.jaymoskitchen.delight.CookwareBlockEntity cookware) {
						all.putAll(cookware.getHandsOnSessions());
					}
				}
				return all;
			}

			/** Removes a hands-on session from its station. */
			private static void removeHandsOnSession(ServerLevel level, java.util.UUID playerId, BlockPos pos) {
				if (level.getBlockEntity(pos) instanceof CuttingBoardEntity board) {
					board.removeHandsOnSession(playerId);
				} else if (level.getBlockEntity(pos) instanceof com.jaymoskitchen.delight.CookwareBlockEntity cookware) {
					cookware.removeHandsOnSession(playerId);
				}
			}

			/** Returns a unique session ID for the finish packet. */
				private static java.util.UUID sessionId() {
					return java.util.UUID.randomUUID();
				}

				/** Starts a hands-on cooking session for the player. */
				private static void startHandsOnSession(ServerPlayer player, BlockPos pos, Station station, Dish dish) {
					if (!station.allows(dish)) {
						return;
					}
					ServerLevel level = player.serverLevel();
					// Check if player already has a session
					if (getAllHandsOnSessions(level).containsKey(player.getUUID())) {
						player.sendOverlayMessage(Component.translatable("message.jaymoskitchen.already_cooking"));
						return;
					}
					// Check ingredients (for STIR, need ingredients in inventory/kitchen)
					if (dish.method() == Dish.Method.STIR) {
						if (!player.hasInfiniteMaterials()) {
							KitchenPool pool = Kitchen.sources(player, pos);
							if (!Kitchen.hasIngredients(pool, dish.ingredients())) {
								player.sendOverlayMessage(Component.translatable("message.jaymoskitchen.missing_ingredients"));
								return;
							}
							Kitchen.takeIngredients(player, pos, dish.ingredients());
						}
					}
					// Create session
					HandsOnSession session;
					int startTick = level.getServer().getTickCount();
					if (dish.method() == Dish.Method.CHOP) {
						session = new HandsOnSession.ChopState(
								player.getUUID(), dish, pos.immutable(), startTick,
								0, 0, 0,
								java.util.stream.IntStream.range(0, Minigames.CUTS)
										.mapToObj(i -> (i + 0.5F) * Minigames.MARK_GAP)
										.toList(),
								-0.25F, Minigames.CHOP_SPEED);
					} else if (dish.method() == Dish.Method.STIR) {
						session = new HandsOnSession.StirState(
								player.getUUID(), dish, pos.immutable(), startTick,
								0, 0, 0, 0,
								false, 0.0F);
					} else {
						return;
					}
					// Store session on station
					if (level.getBlockEntity(pos) instanceof CuttingBoardEntity board) {
						board.setHandsOnSession(player.getUUID(), session);
					} else if (level.getBlockEntity(pos) instanceof CookwareBlockEntity cookware) {
						cookware.setHandsOnSession(player.getUUID(), session);
					}
					// Send start packet to client
					ServerPlayNetworking.send(player, new HandsOnStartPayload(pos, station.ordinal(), dish.ordinal(), sessionId().hashCode()));
					// Play start sound
					level.playSound(null, pos, dish.method() == Dish.Method.CHOP ? SoundEvents.WOOD_HIT : SoundEvents.BUBBLE_COLUMN_UPWARDS_AMBIENT, SoundSource.BLOCKS, 1.0F, 1.0F);
				}

				/** Finds a CHOP dish that uses the given ingredient as its first ingredient. */
				private static Dish findChopDishForIngredient(Item ingredient) {
					for (Dish dish : Dish.values()) {
						if (dish.method() == Dish.Method.CHOP && dish.available()) {
							List<Step> steps = dish.steps();
							if (!steps.isEmpty() && !steps.get(0).isAction() && steps.get(0).matches(new ItemStack(ingredient))) {
								return dish;
							}
						}
					}
					return null;
				}

				/** Finds any available STIR dish (for HEARTH_POT). */
				private static Dish findAnyStirDish() {
					for (Dish dish : Dish.values()) {
						if (dish.method() == Dish.Method.STIR && dish.available()) {
							return dish;
						}
					}
					return null;
				}
			}
