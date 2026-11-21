package com.jaymoskingdoms;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.SwingAnimation;

/**
 * (gv_kick, after Guard Villagers' archers: its idea only) An archer with a foe on top of them does not stand there plucking
 * at a string. A guard holding a bow or crossbow ({@link Comrades#ranged}) with a foe within {@link #KICK_REACH} kicks it
 * back a block now and then ({@link Shoves#kick}, which also wears a fighter's stance, {@link FighterPosture}: a kicked
 * swordsman is that much nearer to reeling, and a reeling one goes over at once). A guard whose own stance is broken
 * does not kick. If the foe stays on them for {@link #RUSHED_TICKS} they draw a sidearm (a worn iron sword, marked
 * {@link #SIDEARM}) into the hand with the bow put in the off hand, and fight with it until the foe is gone for
 * {@link #CALM_TICKS} or {@link #FAR} blocks off, then the bow is back in hand and the sword away. A guard already holding
 * something in the off hand (a shield) only kicks.
 */
public final class GuardKicks {
	/** How often guards are looked at (ticks). */
	static final int EVERY = 5;
	/** A foe this near (blocks) is kicked; the wait between kicks (ticks). */
	static final double KICK_REACH = 2.4;
	static final int KICK_GAP = 50;
	/** A foe this near (blocks) for this long (ticks) rushes a guard into drawing a sidearm. */
	static final double RUSHED = 3.2;
	static final int RUSHED_TICKS = 30;
	/** The sidearm goes back once the foe has been gone this long (ticks), or is this far off (blocks). */
	static final int CALM_TICKS = 60;
	static final double FAR = 9.0;
	/** Custom-data key marking the drawn sword. */
	static final String SIDEARM = "kingdoms_sidearm";

	private static final Map<UUID, Long> KICKED = new HashMap<>();
	private static final Map<UUID, Integer> RUSHED_FOR = new HashMap<>();
	private static final Map<UUID, Long> LAST_FOE = new HashMap<>();

	private GuardKicks() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(GuardKicks::tick);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			KICKED.clear();
			RUSHED_FOR.clear();
			LAST_FOE.clear();
		});
	}

	private static boolean sidearmed(Mob m) {
		CustomData data = m.getMainHandItem().get(DataComponents.CUSTOM_DATA);
		return data != null && data.copyTag().contains(SIDEARM);
	}

	private static void tick(MinecraftServer server) {
		ServerLevel level = Factions.level(server);
		long now = level.getGameTime();
		if (now % EVERY != 0) {
			return;
		}
		for (Mob guard : Comrades.fighters(level)) {
			if (!guard.isAlive() || !Downed.isGuard(guard) || Downed.isDowned(guard)) {
				continue;
			}
			boolean drawn = sidearmed(guard);
			if (!drawn && !Comrades.ranged(guard)) {
				continue;
			}
			LivingEntity foe = Comrades.target(guard);
			if (foe != null && foe.isAlive()) {
				LAST_FOE.put(guard.getUUID(), now);
				double d = guard.distanceTo(foe);
				if (d <= KICK_REACH && now - KICKED.getOrDefault(guard.getUUID(), 0L) >= KICK_GAP && !(guard.isPassenger())
					&& !FighterPosture.reeling(guard)) {
					KICKED.put(guard.getUUID(), now);
					guard.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
					Shoves.kick(level, guard, foe);
				}
				if (d <= RUSHED && !drawn) {
					int rushed = RUSHED_FOR.merge(guard.getUUID(), EVERY, Integer::sum);
					if (rushed >= RUSHED_TICKS) {
						draw(level, guard);
						RUSHED_FOR.remove(guard.getUUID());
					}
				} else if (d > RUSHED) {
					RUSHED_FOR.remove(guard.getUUID());
				}
				if (drawn && d > FAR) {
					stow(guard);
				}
			} else {
				RUSHED_FOR.remove(guard.getUUID());
				if (drawn && now - LAST_FOE.getOrDefault(guard.getUUID(), 0L) >= CALM_TICKS) {
					stow(guard);
				}
			}
		}
	}

	/** The bow goes to the off hand and a worn iron sword into the hand (if the off hand is free). */
	private static void draw(ServerLevel level, Mob guard) {
		if (!guard.getOffhandItem().isEmpty()) {
			return;
		}
		ItemStack sword = new ItemStack(Items.IRON_SWORD);
		WornGear.wear(sword, level.getRandom());
		CustomData.update(DataComponents.CUSTOM_DATA, sword, tag -> tag.putBoolean(SIDEARM, true));
		guard.setItemSlot(EquipmentSlot.OFFHAND, guard.getMainHandItem());
		guard.setItemSlot(EquipmentSlot.MAINHAND, sword);
	}

	/** The sword is put away and the bow is back in hand. */
	private static void stow(Mob guard) {
		ItemStack bow = guard.getOffhandItem();
		guard.setItemSlot(EquipmentSlot.MAINHAND, bow);
		guard.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
	}
}
