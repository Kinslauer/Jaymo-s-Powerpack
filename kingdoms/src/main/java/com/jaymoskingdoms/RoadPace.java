package com.jaymoskingdoms;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * (0.7.103) The going underfoot, after Better With Mods' movement tweaks (MIT; its idea, our own code). On a road
 * ({@link #ROAD}: dirt paths and path slabs, a realm's road stone, see {@link Roads}) a player on foot goes {@link #ROAD_BONUS} faster,
 * and so does the horse under a rider; in sand, snow or leaves ({@link #SLOW}) a player on foot goes
 * {@link #SLOW_DRAG} slower. Anywhere else nothing changes. It doesn't zoom the view (the client takes it back out of
 * the field of view: {@code client.mixin.tweaks.RoadPaceFovMixin}). Flying, gliding, a Gravity user gone weightless or
 * a Wind user (Jaymo's Powers, read by its entity tag and its gravity modifier's name, no code of theirs) don't feel it.
 */
public final class RoadPace {
	/** Faster on a road, slower in the heavy going (shares of the speed, the whole of it). */
	static final double ROAD_BONUS = 0.15, SLOW_DRAG = -0.15;
	/** How often (ticks) the ground underfoot is looked at again. */
	static final int CHECK_TICKS = 5;

	/** The speed modifier (the client reads its id to keep the view still). */
	public static final Identifier PACE_ID = JaymosKingdoms.id("road_pace");
	public static final TagKey<Block> ROAD = TagKey.create(Registries.BLOCK, JaymosKingdoms.id("road_pace")),
		SLOW = TagKey.create(Registries.BLOCK, JaymosKingdoms.id("slow_going"));
	/** Jaymo's Powers: on every Wind user ({@code WindPower.KINGDOMS_TAG}); the gravity modifier of Gravity's Weightless. */
	static final String WIND_TAG = "jaymospowers_wind";
	static final Identifier WEIGHTLESS = Identifier.fromNamespaceAndPath("jaymospowers", "gravity_weightless");

	/** The horses given the road's pace last time round (to take it off them once they're off the road or unridden). */
	private static final Set<LivingEntity> PACED = Collections.newSetFromMap(new WeakHashMap<>());

	private RoadPace() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(RoadPace::tick);
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % CHECK_TICKS != 0) {
			return;
		}
		Set<LivingEntity> paced = new HashSet<>();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (player.getVehicle() instanceof AbstractHorse horse) {
				pace(player, 0.0);
				// in the air (a jump) the pace it had holds
				AttributeInstance speed = horse.getAttribute(Attributes.MOVEMENT_SPEED);
				double going = horse.onGround() ? going(horse) : speed != null && speed.hasModifier(PACE_ID) ? ROAD_BONUS : 0.0;
				if (going > 0.0) {
					// (0.7.218) a Courier's horse too
					pace(horse, going * Careers.roadPace(player));
					paced.add(horse);
				}
				continue;
			}
			if (player.isPassenger() || immune(player)) {
				pace(player, 0.0);
			} else if (player.onGround()) {
				// in the air (a jump) the pace they had holds; (0.7.218) a Courier's road pace is more
				double going = going(player);
				pace(player, going > 0.0 ? going * Careers.roadPace(player) : going);
			}
		}
		for (LivingEntity horse : PACED) {
			if (!paced.contains(horse)) {
				pace(horse, 0.0);
			}
		}
		PACED.clear();
		PACED.addAll(paced);
	}

	/** The pace of the ground under {@code who}: the road's bonus, the heavy going's drag, or nothing. */
	static double going(LivingEntity who) {
		Level level = who.level();
		// a snow layer or carpet of leaves is the block at the feet; anything else the one under them
		BlockState feet = level.getBlockState(who.blockPosition());
		if (feet.is(SLOW)) {
			return SLOW_DRAG;
		}
		BlockPos on = who.getOnPos();
		BlockState under = level.getBlockState(on);
		return under.is(ROAD) ? ROAD_BONUS : under.is(SLOW) ? SLOW_DRAG : 0.0;
	}

	/** Flying, gliding, weightless or a Wind user: not walking on the ground as others do. */
	static boolean immune(ServerPlayer player) {
		if (player.getAbilities().flying || player.isFallFlying() || player.isSpectator() || player.entityTags().contains(WIND_TAG)) {
			return true;
		}
		AttributeInstance gravity = player.getAttribute(Attributes.GRAVITY);
		return gravity != null && gravity.hasModifier(WEIGHTLESS);
	}

	/** Puts the pace on {@code who} (0: takes it off). Not saved: it's worked out afresh as they go. */
	private static void pace(LivingEntity who, double amount) {
		AttributeInstance speed = who.getAttribute(Attributes.MOVEMENT_SPEED);
		if (speed == null) {
			return;
		}
		if (amount == 0.0) {
			speed.removeModifier(PACE_ID);
			return;
		}
		AttributeModifier now = speed.getModifier(PACE_ID);
		if (now == null || now.amount() != amount) {
			speed.addOrUpdateTransientModifier(new AttributeModifier(PACE_ID, amount, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
		}
	}
}
