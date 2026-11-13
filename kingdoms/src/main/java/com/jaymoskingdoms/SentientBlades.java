package com.jaymoskingdoms;

import com.jaymoskingdoms.money.Money;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.illager.AbstractIllager;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import org.jspecify.annotations.Nullable;

/**
 * Weapons with a will. Now and then a found relic blade has a mind of its own: a <b>purpose</b> (slay the undead, guard the
 * weak, or gold), a name and a voice. Bonded and held, it speaks in a bubble only its wielder sees, now and then, and
 * says so when it is pleased. Act against its purpose and it <b>argues</b>; act against it again and again and it
 * <b>fights you for control</b>: a takeover ({@link Seizures.Kind#BLADE}) drives your hand at what it wants, until you tear
 * free (a bar you fight, as with the blood fever) or it has what it wanted. A Possession save may hold it off. With nothing it
 * wants in reach it slips from your hand instead.
 * <ul>
 * <li><b>Undead:</b> against it, striking a living thing that does no harm (a villager, a beast).</li>
 * <li><b>Weak:</b> against it, striking the helpless or someone's own (a villager, a golem, a pet, a baby, another player).</li>
 * <li><b>Gold:</b> against it, parting with coin (it feels your purse lighten).</li>
 * </ul>
 */
public final class SentientBlades {
	/** The key on the item: the purpose; and the name's number. */
	static final String WILL = "jaymos_will", NAME = "jaymos_will_name";
	/** The purposes. */
	static final List<String> PURPOSES = List.of("undead", "weak", "gold");
	/** How many names there are. */
	static final int NAMES = 6;
	/** It speaks every so often (ticks, a base and some more at random). */
	static final int VOICE_EVERY = 20 * 90, VOICE_MORE = 20 * 90;
	/** Grudges: the count at which it argues, and at which it takes the wheel; how long one is held (ticks); the wait after a takeover. */
	static final int ARGUES = 1, TAKES = 3;
	static final int GRUDGE_TICKS = 20 * 120, TAKEOVER_COOLDOWN = 20 * 60 * 5;
	/** A takeover: ticks it lasts, presses to break it, how fast the bar drains; how far it sees what it wants (blocks). */
	static final int HOLD_TICKS = 20 * 20, NEEDED = 22;
	static final float FADE = 0.12F;
	static final double SEES = 40.0;
	/** Gold: a purse that lightens by this much (coin) is a grudge. */
	static final int PURSE_LOSS = 4;
	/** A pleased blade heals this much. */
	static final float PLEASED_HEAL = 2.0F;
	/** After breaking free, the weakness (ticks) and the wait before it will speak again (ticks). */
	static final int SULK_TICKS = 20 * 8, SULK_QUIET = 20 * 60;
	/** The bubble's colour. */
	static final int VOICE = 0x7FD6C8;

	private static final class Mood {
		int grudge;
		long lastGrudge, nextVoice, takeoverAfter, spokeArgument, quietUntil;
		long purse = -1L;
		boolean met;
	}

	private static final Map<UUID, Mood> MOODS = new HashMap<>();

	private SentientBlades() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(SentientBlades::tick);
		ServerLivingEntityEvents.AFTER_DAMAGE.register((victim, source, base, taken, blocked) -> {
			if (!blocked && source.getEntity() instanceof ServerPlayer player && source.getDirectEntity() == player && victim != player) {
				struck(player, victim);
			}
		});
		ServerLivingEntityEvents.AFTER_DEATH.register((victim, source) -> {
			if (source.getEntity() instanceof ServerPlayer player && victim != player) {
				killed(player, victim, source);
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> MOODS.clear());
	}

	// ---- the item ------------------------------------------------------------------------------------------------------

	/** The purpose on {@code stack}, or null. */
	static @Nullable String will(ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		return data == null ? null : data.copyTag().getString(WILL).filter(s -> !s.isEmpty()).orElse(null);
	}

	/** Gives {@code stack} a mind of its own. */
	static void awaken(ItemStack stack, RandomSource random) {
		String purpose = PURPOSES.get(random.nextInt(PURPOSES.size()));
		int name = random.nextInt(NAMES);
		CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
			tag.putString(WILL, purpose);
			tag.putInt(NAME, name);
		});
	}

	/** The lines a known blade's lore adds: its name and what it wants. */
	static List<Component> lore(ItemStack stack) {
		String purpose = will(stack);
		if (purpose == null || ItemBonds.unknown(stack)) {
			return List.of();
		}
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		int name = data == null ? 0 : data.copyTag().getIntOr(NAME, 0);
		return List.of(Component.translatable("will.jaymoskingdoms.lore", Component.translatable("will.jaymoskingdoms.name." + name),
			Component.translatable("will.jaymoskingdoms.purpose." + purpose)).withStyle(style -> style.withItalic(false).withColor(VOICE)));
	}

	private static int nameOf(ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		return data == null ? 0 : data.copyTag().getIntOr(NAME, 0);
	}

	/** Whether {@code player} holds a blade with a will, bonded to them. */
	static boolean wields(ServerPlayer player) {
		ItemStack held = player.getMainHandItem();
		return will(held) != null && ItemBonds.bonded(player, held);
	}

	// ---- what it says ------------------------------------------------------------------------------------------------

	private static void say(ServerPlayer player, ItemStack blade, String key, int variants) {
		String purpose = will(blade);
		int n = player.getRandom().nextInt(variants);
		Speech.privately(player, player, Component.translatable("speech.jaymoskingdoms.will." + purpose + "." + key + "." + n,
			Component.translatable("will.jaymoskingdoms.name." + nameOf(blade))), VOICE);
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % 20 != 0) {
			return;
		}
		long now = server.overworld().getGameTime();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			ItemStack blade = player.getMainHandItem();
			if (will(blade) == null || !ItemBonds.bonded(player, blade) || !player.isAlive()) {
				continue;
			}
			Mood m = MOODS.computeIfAbsent(player.getUUID(), k -> new Mood());
			if (!m.met) {
				m.met = true;
				m.nextVoice = now + 60;
				say(player, blade, "hello", 2);
				continue;
			}
			if (m.grudge > 0 && now - m.lastGrudge > GRUDGE_TICKS) {
				m.grudge--;
				m.lastGrudge = now;
			}
			if (now >= m.nextVoice && now >= m.quietUntil && !Seizures.held(player)) {
				m.nextVoice = now + VOICE_EVERY + player.getRandom().nextInt(VOICE_MORE);
				say(player, blade, "idle", 3);
			}
			if ("gold".equals(will(blade))) {
				long coins = Money.count(player);
				if (m.purse >= 0 && m.purse - coins >= Money.worth(PURSE_LOSS)) {
					grudge(player, blade, m, now);
				}
				m.purse = coins;
			}
		}
	}

	// ---- what it likes and what it hates ---------------------------------------------------------------------

	private static void struck(ServerPlayer player, LivingEntity victim) {
		ItemStack blade = player.getMainHandItem();
		String purpose = will(blade);
		if (purpose == null || !ItemBonds.bonded(player, blade)) {
			return;
		}
		boolean wrong = switch (purpose) {
			case "undead" -> !victim.is(EntityTypeTags.UNDEAD) && !(victim instanceof Enemy) && !(victim instanceof Player);
			case "weak" -> victim instanceof Villager || victim instanceof IronGolem || victim instanceof Player || victim.isBaby()
				|| victim instanceof TamableAnimal pet && pet.isTame();
			default -> false;
		};
		if (wrong) {
			Mood m = MOODS.computeIfAbsent(player.getUUID(), k -> new Mood());
			grudge(player, blade, m, player.level().getGameTime());
		}
	}

	private static void killed(ServerPlayer player, LivingEntity victim, DamageSource source) {
		ItemStack blade = player.getMainHandItem();
		String purpose = will(blade);
		if (purpose == null || !ItemBonds.bonded(player, blade)) {
			return;
		}
		boolean pleasing = switch (purpose) {
			case "undead" -> victim.is(EntityTypeTags.UNDEAD);
			case "weak" -> victim instanceof Enemy;
			default -> victim instanceof Piglin || victim instanceof AbstractIllager;
		};
		if (!pleasing) {
			return;
		}
		Mood m = MOODS.computeIfAbsent(player.getUUID(), k -> new Mood());
		m.grudge = Math.max(0, m.grudge - 1);
		player.heal(PLEASED_HEAL);
		if (player.getRandom().nextInt(3) == 0) {
			say(player, blade, "pleased", 2);
		}
		if (player.level() instanceof ServerLevel level) {
			level.sendParticles(Fx.GLOW, player.getX(), player.getY(1.2), player.getZ(), 6, 0.3, 0.4, 0.3, 0.02);
		}
	}

	private static void grudge(ServerPlayer player, ItemStack blade, Mood m, long now) {
		m.grudge++;
		m.lastGrudge = now;
		if (m.grudge >= TAKES && now >= m.takeoverAfter && !Seizures.held(player)) {
			takeover(player, blade, m, now);
		} else if (m.grudge >= ARGUES && now - m.spokeArgument > 160) {
			m.spokeArgument = now;
			say(player, blade, "argue", 3);
		}
	}

	/** It takes the wheel: at what it wants, or it slips from your hand if nothing it wants is near. */
	private static void takeover(ServerPlayer player, ItemStack blade, Mood m, long now) {
		m.takeoverAfter = now + TAKEOVER_COOLDOWN;
		if (Saves.resist(player, Saves.Kind.POSSESSION)) {
			m.grudge = TAKES - 1;
			say(player, blade, "held_off", 2);
			return;
		}
		LivingEntity want = wanted(player, will(blade));
		if (want == null) {
			slip(player, blade, m);
			return;
		}
		m.grudge = 0;
		say(player, blade, "takes", 2);
		Seizures.start(player, Seizures.Kind.BLADE, want, HOLD_TICKS, NEEDED, FADE, false);
	}

	private static @Nullable LivingEntity wanted(ServerPlayer player, @Nullable String purpose) {
		LivingEntity best = null;
		double bestD = SEES * SEES;
		for (LivingEntity e : player.level().getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(SEES), x -> x.isAlive() && x != player)) {
			boolean fits = switch (String.valueOf(purpose)) {
				case "undead" -> e.is(EntityTypeTags.UNDEAD) && e instanceof Enemy;
				case "weak" -> e instanceof Enemy;
				default -> e instanceof Piglin || e instanceof AbstractIllager;
			};
			double d = e.distanceToSqr(player);
			if (fits && d < bestD) {
				best = e;
				bestD = d;
			}
		}
		return best;
	}

	private static void slip(ServerPlayer player, ItemStack blade, Mood m) {
		m.grudge = 0;
		ItemStack out = blade.copy();
		player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		ItemEntity drop = player.drop(out, false, net.minecraft.util.Prediction.SERVER_ONLY);
		if (drop != null) {
			drop.setPickUpDelay(100);
		}
		say(player, out, "slips", 2);
		player.level().playSound(null, player.blockPosition(), SoundEvents.ITEM_BREAK.value(), SoundSource.PLAYERS, 0.6F, 0.7F);
	}

	/** The takeover ended. */
	static void released(ServerPlayer player, Seizures.Hold hold, Seizures.End how) {
		Mood m = MOODS.computeIfAbsent(player.getUUID(), k -> new Mood());
		ItemStack blade = player.getMainHandItem();
		if (how == Seizures.End.BROKE_FREE) {
			player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, SULK_TICKS, 0));
			m.quietUntil = player.level().getGameTime() + SULK_QUIET;
			if (will(blade) != null) {
				say(player, blade, "sulks", 2);
			}
		} else if (how != Seizures.End.CUT && will(blade) != null) {
			say(player, blade, "sated", 2);
		}
		if (player.level() instanceof ServerLevel level) {
			level.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.7F, 0.6F);
		}
	}

	/** Everything the blades can say, for a test of the lang file. */
	static List<String> lineKeys() {
		List<String> keys = new ArrayList<>();
		for (String purpose : PURPOSES) {
			for (String[] k : new String[][] {{"hello", "2"}, {"idle", "3"}, {"pleased", "2"}, {"argue", "3"}, {"held_off", "2"}, {"takes", "2"}, {"slips", "2"},
				{"sulks", "2"}, {"sated", "2"}}) {
				for (int i = 0; i < Integer.parseInt(k[1]); i++) {
					keys.add("speech.jaymoskingdoms.will." + purpose + "." + k[0] + "." + i);
				}
			}
		}
		return keys;
	}
}
