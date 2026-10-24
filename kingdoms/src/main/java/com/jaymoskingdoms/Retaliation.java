package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.SwingAnimation;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.121) Villagers fight back, our own take on the idea of the Villager Retaliation mod (which is all rights reserved:
 * nothing of its code is used). Strike a grown villager or one of a settlement's civilians and they square up and hit
 * back with their fists; whoever of their people sees it ({@link Theft#witnesses}) joins in, up to {@link #ASSIST_MOST}.
 * Seen, it's an assault, a light crime ({@link Theft.Crime#ASSAULT}). A brawler who gets badly hurt runs off; the rest
 * leave you be once you've stopped hitting them for {@link #COOL_TICKS}, or once you're down.
 * <p>
 * Someone who fears you (reputation at or under {@link #FEARED}, or an outlaw there) runs instead of fighting, and so do
 * those who see it. Emeralds in hand, use a brawler to pay them off ({@link #price}): that one stops, and paying the
 * one you first struck ends the whole fight. It doesn't make them forget it ({@link Recollections}), and those who
 * think you no better than an enemy ({@link Reputation#HOSTILE} or worse) won't take your coin.
 */
final class Retaliation {
	/** At or under this reputation they're afraid of you: they run rather than fight. */
	static final int FEARED = -600;
	/** Most of the onlookers who join in. */
	static final int ASSIST_MOST = 5;
	/** How long their anger lasts after your last blow on one of them. */
	static final long COOL_TICKS = 20L * 30;
	/** They give up on you this far off. */
	static final double GIVE_UP = 32.0;
	/** A punch: its reach, damage, how often one brawler may throw one, and how often the crowd may land one. */
	static final double REACH = 2.0;
	static final float DAMAGE = 1.0F;
	static final int PUNCH_TICKS = 25, CROWD_TICKS = 10;
	/** Walking at you: a villager's speed (brain), a civilian's (navigation). */
	static final float VILLAGER_SPEED = 0.75F;
	static final double FOLK_SPEED = 1.15;
	/** A brawler under this share of their health runs off. */
	static final float FLEE_SHARE = 0.3F;
	/** Paying one off: emeralds to start with, more for each blow you've landed in the fight, and never more than this. */
	static final int PRICE_BASE = 3, PRICE_PER_BLOW = 3, PRICE_MOST = 32;
	/** Lines for squaring up, running, being paid off and refusing the coin. */
	static final int LINES = 3;
	static final int EVERY = 5;
	static final double SPEECH_RANGE = 16.0;

	/** The people after one player: whom they first struck, when they last struck one of them, and how often. */
	private static final class Brawl {
		final @Nullable Faction faction;
		final UUID first;
		long lastBlow;
		int blows;
		long nextPunch;
		/** Brawler → when they may next punch. */
		final Map<UUID, Long> brawlers = new HashMap<>();

		Brawl(@Nullable Faction faction, UUID first, long now) {
			this.faction = faction;
			this.first = first;
			this.lastBlow = now;
		}
	}

	/** Player → the brawl they started. */
	private static final Map<UUID, Brawl> BRAWLS = new HashMap<>();

	private Retaliation() {
	}

	static void register() {
		UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
			if (world.isClientSide() || hand != InteractionHand.MAIN_HAND || !(player instanceof ServerPlayer sp) || !(world instanceof ServerLevel level)
				|| !(entity instanceof LivingEntity who) || !player.getItemInHand(hand).is(Items.EMERALD)) {
				return InteractionResult.PASS;
			}
			return payOff(sp, level, who) ? InteractionResult.SUCCESS : InteractionResult.PASS;
		});
		ServerTickEvents.END_SERVER_TICK.register(Retaliation::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> BRAWLS.clear());
	}

	/** {@code who} is in a brawl with someone just now (they leave chores, fires and tending to others). */
	static boolean brawling(LivingEntity who) {
		for (Brawl b : BRAWLS.values()) {
			if (b.brawlers.containsKey(who.getUUID())) {
				return true;
			}
		}
		return false;
	}

	/** One who'd stand up for themselves: a grown villager, or a settlement's civilian (guards fight anyway; rulers don't brawl). */
	private static boolean fighter(LivingEntity e) {
		if (!e.isAlive() || e.isBaby() || e.isSleeping() || Downed.isDowned(e) || Thralls.isThrall(e) || e.hasAttached(Captives.CAPTIVE)) {
			return false;
		}
		if (e instanceof Villager) {
			return true;
		}
		return e instanceof Folk folk && !folk.combatant() && !folk.leader() && !Orcs.isOrc(folk);
	}

	private static @Nullable Faction factionOf(ServerLevel level, LivingEntity e) {
		if (e instanceof Folk folk) {
			Faction f = Factions.get(level.getServer(), folk.factionId());
			return f == null || f.fallen() || f.type() == FactionType.BARBARIAN ? null : f;
		}
		return Theft.lawAt(level, e.blockPosition());
	}

	/** (From {@link Theft}) {@code player} struck {@code victim} (not one of a crowd after an arsonist). */
	static void struck(ServerPlayer player, ServerLevel level, LivingEntity victim) {
		if (player.isCreative() || player.isSpectator() || level != Factions.level(level.getServer())) {
			return;
		}
		long now = level.getGameTime();
		Brawl brawl = BRAWLS.get(player.getUUID());
		if (brawl != null && brawl.brawlers.containsKey(victim.getUUID())) {
			// already at it: it only keeps them angry, and raises the price of peace
			brawl.lastBlow = now;
			brawl.blows++;
			return;
		}
		if (!fighter(victim) || Theft.rioting(victim)) {
			return;
		}
		if (Crooks.fairGame(victim, player)) {
			// (0.7.126) a known criminal, or the crook who robbed you: nobody takes their side, and it's no assault
			return;
		}
		Faction f = factionOf(level, victim);
		if (f != null && Wars.enemy(player, f)) {
			// (an enemy soldier: they run, as from any raider)
			return;
		}
		List<LivingEntity> seen = f == null ? List.of() : Theft.witnesses(level, f, player);
		if (f != null && (Reputation.get(player, f.id()) <= FEARED || Theft.outlaw(player, f.id())) && !Temperaments.bold(victim)
			|| Temperaments.runs(victim)) {
			// they know what you are: they run; ((0.7.186) the brave and the hotheaded stand anyway, and the timid always run)
			say(level, victim, "flee");
			return;
		}
		if (brawl == null) {
			brawl = new Brawl(f, victim.getUUID(), now);
			BRAWLS.put(player.getUUID(), brawl);
			player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.retaliation.start").withStyle(ChatFormatting.RED));
		}
		brawl.lastBlow = now;
		brawl.blows++;
		brawl.brawlers.put(victim.getUUID(), now + PUNCH_TICKS / 2);
		say(level, victim, "square");
		int joined = 0;
		for (LivingEntity w : seen) {
			if (joined >= ASSIST_MOST) {
				break;
			}
			if (w != victim && fighter(w) && !Theft.rioting(w) && !brawl.brawlers.containsKey(w.getUUID()) && !Downed.helping(w)
				&& !Temperaments.keepsOut(w)) {
				brawl.brawlers.put(w.getUUID(), now + PUNCH_TICKS);
				joined++;
			}
		}
		if (f != null && !Theft.exempt(player, f)) {
			// the one struck saw it, whichever way they faced
			List<LivingEntity> all = new ArrayList<>(seen);
			if (!all.contains(victim)) {
				all.addFirst(victim);
			}
			Theft.commit(player, level, f, Theft.Crime.ASSAULT, null, false, all);
		}
	}

	/** What it costs {@code player} to buy their way out of this fight. */
	private static int price(Brawl brawl) {
		return Math.min(PRICE_MOST, PRICE_BASE + PRICE_PER_BLOW * Math.max(0, brawl.blows - 1));
	}

	/** {@code player}, emeralds in hand, used {@code who}: if they're after them, they try to pay them off. */
	private static boolean payOff(ServerPlayer player, ServerLevel level, LivingEntity who) {
		Brawl brawl = BRAWLS.get(player.getUUID());
		if (brawl == null || !brawl.brawlers.containsKey(who.getUUID())) {
			return false;
		}
		// ((0.7.186) the greedy take anyone's coin; the proud no one's)
		Boolean takes = Temperaments.takesBribe(who);
		if (takes == Boolean.FALSE || takes == null && brawl.faction != null
			&& (Reputation.get(player, brawl.faction.id()) <= Reputation.HOSTILE || Theft.outlaw(player, brawl.faction.id()))) {
			say(level, who, "refuse");
			return true;
		}
		int price = price(brawl);
		ItemStack held = player.getMainHandItem();
		if (held.getCount() < price) {
			player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.retaliation.price", price).withStyle(ChatFormatting.GRAY));
			return true;
		}
		held.shrink(price);
		level.playSound(null, who.getX(), who.getY(), who.getZ(), SoundEvents.VILLAGER_YES, SoundSource.NEUTRAL, 1.0F, 1.0F);
		say(level, who, "paid");
		if (Recollections.rememberer(who)) {
			Recollections.remember(who, player, Recollections.Kind.PAID_OFF, Component.empty());
		}
		if (who.getUUID().equals(brawl.first)) {
			end(level, player, brawl, "message.jaymoskingdoms.retaliation.paid_all");
		} else {
			brawl.brawlers.remove(who.getUUID());
			calm(level, who.getUUID());
			player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.retaliation.paid", price).withStyle(ChatFormatting.GRAY));
		}
		return true;
	}

	private static void say(ServerLevel level, LivingEntity who, String what) {
		Speech.say(level, who, Component.translatable("message.jaymoskingdoms.retaliation." + what + "." + level.getRandom().nextInt(LINES)),
			what.equals("paid") ? Speech.WHITE : what.equals("flee") ? Speech.YELLOW : Speech.RED, SPEECH_RANGE);
	}

	/** {@code id} lets the fight go: back to their day. */
	private static void calm(ServerLevel level, UUID id) {
		if (level.getEntity(id) instanceof Villager v) {
			v.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
			v.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);
		} else if (level.getEntity(id) instanceof Mob mob) {
			mob.getNavigation().stop();
		}
	}

	private static void end(ServerLevel level, @Nullable ServerPlayer player, Brawl brawl, @Nullable String message) {
		for (UUID id : brawl.brawlers.keySet()) {
			calm(level, id);
		}
		brawl.brawlers.clear();
		if (player != null) {
			BRAWLS.remove(player.getUUID());
			if (message != null) {
				player.sendSystemMessage(Component.translatable(message).withStyle(ChatFormatting.GRAY));
			}
		}
	}

	/** The brawlers close on whoever started it and hit them; the hurt run, and the rest cool off in time. */
	private static void tick(MinecraftServer server) {
		if (BRAWLS.isEmpty()) {
			return;
		}
		ServerLevel level = Factions.level(server);
		long now = level.getGameTime();
		if (now % EVERY != 0) {
			return;
		}
		for (Iterator<Map.Entry<UUID, Brawl>> it = BRAWLS.entrySet().iterator(); it.hasNext();) {
			var e = it.next();
			Brawl brawl = e.getValue();
			ServerPlayer player = server.getPlayerList().getPlayer(e.getKey());
			boolean over = player == null || !player.isAlive() || player.isCreative() || player.isSpectator() || player.level() != level;
			if (!over && (Downed.isDowned(player) || now - brawl.lastBlow > COOL_TICKS)) {
				// down, or long enough since the last blow: they leave you be
				for (UUID id : brawl.brawlers.keySet()) {
					calm(level, id);
				}
				player.sendSystemMessage(Component.translatable(Downed.isDowned(player) ? "message.jaymoskingdoms.retaliation.downed"
					: "message.jaymoskingdoms.retaliation.cooled").withStyle(ChatFormatting.GRAY));
				over = true;
			}
			if (over) {
				if (player == null) {
					for (UUID id : brawl.brawlers.keySet()) {
						calm(level, id);
					}
				}
				it.remove();
				continue;
			}
			for (Iterator<Map.Entry<UUID, Long>> b = brawl.brawlers.entrySet().iterator(); b.hasNext();) {
				var be = b.next();
				if (!(level.getEntity(be.getKey()) instanceof Mob mob) || !fighter(mob) || mob.distanceTo(player) > GIVE_UP) {
					b.remove();
					continue;
				}
				if (mob.getHealth() < mob.getMaxHealth() * Temperaments.fleeShare(mob, FLEE_SHARE)) {
					// badly hurt: they run (and vanilla's panic takes them)
					b.remove();
					say(level, mob, "flee");
					continue;
				}
				if (mob instanceof Villager v) {
					// standing their ground: no panicking from the blows they've taken
					v.getBrain().eraseMemory(MemoryModuleType.HURT_BY);
					v.getBrain().eraseMemory(MemoryModuleType.HURT_BY_ENTITY);
				}
				mob.getLookControl().setLookAt(player);
				if (mob.distanceTo(player) <= REACH) {
					if (now >= be.getValue() && now >= brawl.nextPunch && mob.hasLineOfSight(player)) {
						be.setValue(now + PUNCH_TICKS);
						brawl.nextPunch = now + CROWD_TICKS;
						mob.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
						player.hurtServer(level, level.damageSources().mobAttack(mob), DAMAGE);
					}
				} else if (mob instanceof Villager v) {
					BehaviorUtils.setWalkAndLookTargetMemories(v, new EntityTracker(player, true), VILLAGER_SPEED, 1);
				} else {
					mob.getNavigation().moveTo(player, FOLK_SPEED);
				}
			}
			if (brawl.brawlers.isEmpty()) {
				it.remove();
			}
		}
	}
}
