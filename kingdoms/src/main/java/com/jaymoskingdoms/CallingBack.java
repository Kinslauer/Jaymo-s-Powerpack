package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * Calling the freshly dead back (Andy's pick E3). Someone who falls for good (the last death, the one that ends a life) and
 * has a reviver within reach is held a minute before the black screen ({@code Revival} in Jaymo's Powers). Two can answer:
 * <ul>
 * <li><b>A cleric</b> of the settlements, within {@link #CLERIC_REACH}: five seconds of chanting over the fallen, for a
 * diamond or a pinch of diamond dust taken from the fallen's own things. A cleric is spent by it and rests three days.</li>
 * <li><b>Any player</b> who knows the rite of calling back, or carries its scroll, and has a pinch of diamond dust: reading
 * it over the fallen (within {@link #SPELL_REACH}) takes the dust and brings them back.</li>
 * </ul>
 * It is rare and costly on purpose, and it does not count as one of the two wake-ups. The fallen comes back weak and sick
 * (Powers' side); a death that is not whole (beheaded, into the void or the lava) cannot be called back.
 */
public final class CallingBack {
	// ---- tuning ----
	/** A cleric answers from this far (blocks), chants this long (ticks), and rests this long after (ticks, three days). */
	static final double CLERIC_REACH = 28.0;
	static final int CHANT = 100, REST = 24000 * 3;
	/** A player reads the rite over someone this near (blocks); another player's help is looked for this near. */
	static final double SPELL_REACH = 8.0, HELPER_REACH = 12.0;
	static final int COLOUR = 0xF2E8A8;

	/** The game time a cleric rests until. */
	static final AttachmentType<Long> RESTING = AttachmentRegistry.create(JaymosKingdoms.id("cleric_resting"), b -> b.persistent(Codec.LONG));

	private record Chant(UUID cleric, int done) {
	}

	private static final Map<UUID, Chant> CHANTS = new HashMap<>();

	private CallingBack() {
	}

	static void register() {
		DeathCompat.addReviver(CallingBack::someoneCan);
		ServerTickEvents.END_SERVER_TICK.register(CallingBack::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> CHANTS.clear());
	}

	/** Whether anyone could call {@code dying} back just now (Powers asks as they fall and each second after). */
	private static boolean someoneCan(ServerPlayer dying) {
		if (!(dying.level() instanceof ServerLevel level)) {
			return false;
		}
		if (cleric(level, dying) != null) {
			return true;
		}
		for (ServerPlayer p : level.players()) {
			if (p != dying && p.isAlive() && !p.isSpectator() && p.distanceToSqr(dying) <= HELPER_REACH * HELPER_REACH && (NewSpells.knows(p, "calling_back") || has(p, "calling_back"))
				&& count(p, SpellParts.DIAMOND_DUST) > 0) {
				return true;
			}
		}
		return false;
	}

	/** The cleric who would chant over {@code dying}: near, rested, and a fee to be had. */
	private static Villager cleric(ServerLevel level, ServerPlayer dying) {
		if (fee(dying) == null) {
			return null;
		}
		Villager v = BodyKeeping.clericNear(level, dying.position(), CLERIC_REACH);
		return v != null && v.getAttachedOrElse(RESTING, 0L) <= level.getGameTime() ? v : null;
	}

	/** What the fallen can pay with: a diamond, or dust; null if neither. */
	private static Item fee(ServerPlayer dying) {
		if (count(dying, SpellParts.DIAMOND_DUST) > 0) {
			return SpellParts.DIAMOND_DUST;
		}
		return count(dying, Items.DIAMOND) > 0 ? Items.DIAMOND : null;
	}

	private static boolean has(Player player, String spell) {
		Inventory inv = player.getInventory();
		Item scroll = Arcana.NEW_SCROLLS.get(spell);
		for (int i = 0; i < inv.getContainerSize(); i++) {
			if (inv.getItem(i).is(scroll)) {
				return true;
			}
		}
		return false;
	}

	private static int count(Player player, Item item) {
		int n = 0;
		Inventory inv = player.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			if (inv.getItem(i).is(item)) {
				n += inv.getItem(i).getCount();
			}
		}
		return n;
	}

	private static boolean take(Player player, Item item) {
		Inventory inv = player.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack stack = inv.getItem(i);
			if (stack.is(item)) {
				stack.shrink(1);
				return true;
			}
		}
		return false;
	}

	/** The rite of calling back, read by {@code caster}: the one fallen nearest brings back; false if none, or no dust. */
	static boolean cast(ServerLevel level, Player caster) {
		java.util.List<ServerPlayer> near = DeathCompat.dyingNear(level, caster.position(), SPELL_REACH);
		near = near.stream().filter(p -> p != caster).sorted(java.util.Comparator.comparingDouble(p -> p.distanceToSqr(caster))).toList();
		if (near.isEmpty()) {
			return false;
		}
		if (!caster.isCreative() && !take(caster, SpellParts.DIAMOND_DUST)) {
			caster.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.parts.needs", 1, new ItemStack(SpellParts.DIAMOND_DUST).getHoverName()));
			return false;
		}
		ServerPlayer fallen = near.get(0);
		CHANTS.remove(fallen.getUUID());
		return DeathCompat.revive(fallen, caster);
	}

	/** Each second: a cleric near the fallen takes up a chant, keeps it, and ends it. */
	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % 20 != 0) {
			return;
		}
		for (ServerLevel level : server.getAllLevels()) {
			for (ServerPlayer fallen : DeathCompat.dyingNear(level, Vec3.ZERO, 1.0E7)) {
				Chant chant = CHANTS.get(fallen.getUUID());
				Villager cleric = chant == null ? cleric(level, fallen) : level.getEntity(chant.cleric) instanceof Villager v && v.isAlive()
					&& v.distanceToSqr(fallen) <= CLERIC_REACH * CLERIC_REACH ? v : null;
				if (cleric == null || fee(fallen) == null) {
					CHANTS.remove(fallen.getUUID());
					continue;
				}
				int done = chant == null ? 0 : chant.done + 20;
				cleric.getNavigation().moveTo(fallen, 0.6);
				cleric.getLookControl().setLookAt(fallen);
				Vec3 at = fallen.position();
				if (chant == null) {
					Speech.say(level, cleric, Component.translatable("speech.jaymoskingdoms.cleric.calls"), COLOUR, 16.0);
					Fx.sigil(level, at.add(0.0, 0.05, 0.0), 2.5F, COLOUR, CHANT + 10);
				}
				level.sendParticles(Fx.rune(COLOUR, 0.8F), at.x, at.y + 0.2 + done * 0.01, at.z, 2, 0.6, 0.1, 0.6, 0.0);
				if (done < CHANT) {
					CHANTS.put(fallen.getUUID(), new Chant(cleric.getUUID(), done));
					continue;
				}
				CHANTS.remove(fallen.getUUID());
				Item fee = fee(fallen);
				if (fee != null && take(fallen, fee) && DeathCompat.revive(fallen, cleric)) {
					cleric.setAttached(RESTING, level.getGameTime() + REST);
					Speech.say(level, cleric, Component.translatable("speech.jaymoskingdoms.cleric.done"), COLOUR, 16.0);
					level.playSound(null, cleric.blockPosition(), SoundEvents.VILLAGER_YES, SoundSource.NEUTRAL, 1.0F, 0.8F);
				}
			}
		}
	}
}
