package com.jaymospowers.power.zfighter.pod;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.power.zfighter.ZFighterPodTrip;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Prediction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * (0.18.21, Andy's SevTech picks: "Wreck Part Sets") What the Maw's wrecked pods hold. Each wreck deck has a fixed set of
 * parts ({@link #SETS}: the three fins; the tank and the bell; the four hull plates; the canopy and the mast), not whatever
 * the dice gave, and the wreck's pod is drawn with that set on. Hitting a wreck with a hammer salvages it for the hitter
 * alone: the set their deck holds, or if they already hold that one the next set they haven't, or once every set is held only
 * plain scrap. A wreck gives each player one salvage ({@link #SALVAGED} remembers which sets and which wrecks). Parts come
 * out worn ({@link #WORN_EVERY}): they want mending at a salvage rack. A wreck can't be built on or flown ({@link PodEntity#locked}).
 * The keel is never in a set; it comes from raider bands.
 */
public final class PodWrecks {
	// ---- tuning ----
	/** The sets, one for each kind of deck; a deck holds {@code SETS[deck % SETS.size()]}. */
	static final List<List<PodPart>> SETS = List.of(
		List.of(PodPart.FIN, PodPart.FIN, PodPart.FIN),
		List.of(PodPart.TANK, PodPart.BELL),
		List.of(PodPart.PLATE, PodPart.PLATE, PodPart.PLATE, PodPart.PLATE),
		List.of(PodPart.CANOPY, PodPart.MAST));
	/** Every this-many parts of a set (the first, the third...) are worn; the others are sound. */
	static final int WORN_EVERY = 2;
	/** The iron nuggets that all-sets-held scrap is worth. */
	static final int PLAIN_SCRAP = 6;
	/** The tag a wreck's pod carries, with its deck's number after it. */
	static final String TAG_PREFIX = "jaymospowers.wreck_deck.";
	/** The most wreck decks there are to remember (the Maw builds {@code ZFighterSpace.MAW_WRECKS}). */
	static final int MAX_DECKS = 30;

	/** What a player has salvaged: the sets they hold and the wrecks they have been through (bit masks). */
	public record Salvaged(int sets, int wrecks) {
		public static final Salvaged NONE = new Salvaged(0, 0);
		public static final Codec<Salvaged> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.INT.optionalFieldOf("sets", 0).forGetter(Salvaged::sets),
			Codec.INT.optionalFieldOf("wrecks", 0).forGetter(Salvaged::wrecks)).apply(i, Salvaged::new));
	}

	public static final AttachmentType<Salvaged> SALVAGED = AttachmentRegistry.create(JaymosPowers.id("pod_wreck_salvage"),
		builder -> builder.persistent(Salvaged.CODEC).copyOnDeath());

	private PodWrecks() {
	}

	/** The deck {@code pod} is the wreck of, or -1. */
	static int deckOf(PodEntity pod) {
		for (String tag : pod.entityTags()) {
			if (tag.startsWith(TAG_PREFIX)) {
				try {
					return Integer.parseInt(tag.substring(TAG_PREFIX.length()));
				} catch (NumberFormatException e) {
					return -1;
				}
			}
		}
		return -1;
	}

	/** The set deck {@code deck} holds (an index into {@link #SETS}). */
	static int setOf(int deck) {
		return Math.floorMod(deck, SETS.size());
	}

	/** Whether the {@code index}th part of a set is worn. */
	static boolean worn(int index) {
		return index % WORN_EVERY == 0;
	}

	/** A wrecked pod for deck {@code deck} at {@code at}: its keel and its deck's set, worn in part, no fuel. */
	public static PodEntity build(ServerLevel level, Vec3 at, int deck, float yaw) {
		List<PodPart> set = SETS.get(setOf(deck));
		int filled = PodPart.KEEL.bits();
		int damaged = 0;
		int[] used = new int[PodPart.values().length];
		for (int i = 0; i < set.size(); i++) {
			PodPart part = set.get(i);
			int bit = part.bit(used[part.ordinal()]++);
			filled |= bit;
			if (worn(i)) {
				damaged |= bit;
			}
		}
		PodEntity pod = PodEntity.spawn(level, at, false, 0);
		pod.setParts(filled, damaged);
		pod.setYRot(yaw);
		pod.addTag(TAG_PREFIX + deck);
		return pod;
	}

	/** The set {@code player} would get from deck {@code deck}: the deck's own, else the next they lack; -1 when they hold them all. */
	static int setFor(Salvaged got, int deck) {
		for (int k = 0; k < SETS.size(); k++) {
			int set = (setOf(deck) + k) % SETS.size();
			if ((got.sets() & 1 << set) == 0) {
				return set;
			}
		}
		return -1;
	}

	/** A hammer on a wreck: {@code player} salvages it, once. */
	static void salvage(ServerLevel level, ServerPlayer player, PodEntity pod) {
		int deck = pod.wreckDeck();
		Salvaged got = player.getAttachedOrElse(SALVAGED, Salvaged.NONE);
		Vec3 at = pod.getBoundingBox().getCenter();
		if (deck < 0 || deck >= MAX_DECKS || (got.wrecks() & 1 << deck) != 0) {
			level.playSound(null, at.x, at.y, at.z, SoundEvents.ANVIL_LAND, SoundSource.BLOCKS, 0.4F, 0.6F);
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.pod.wreck_bare").withStyle(ChatFormatting.GRAY));
			return;
		}
		int set = setFor(got, deck);
		List<ItemStack> stacks = new ArrayList<>();
		if (set < 0) {
			stacks.add(PodParts.scrapOf(null, PLAIN_SCRAP));
		} else {
			List<PodPart> parts = SETS.get(set);
			for (int i = 0; i < parts.size(); i++) {
				stacks.add(PodParts.itemOf(parts.get(i), worn(i)));
			}
		}
		player.setAttached(SALVAGED, new Salvaged(set < 0 ? got.sets() : got.sets() | 1 << set, got.wrecks() | 1 << deck));
		if (set >= 0) {
			// (0.18.22) a part set taken from a wreck: the deed that opens Legend
			ZFighterPodTrip.deed(player, "maw_salvage");
			// (0.18.55) and the dust the wreck's hull has gathered
			stacks.add(new ItemStack(com.jaymospowers.power.zfighter.backhome.BackHome.MAW_DUST, com.jaymospowers.power.zfighter.backhome.BackHome.WRECK_DUST));
		}
		for (ItemStack stack : stacks) {
			player.getInventory().placeItemBackInInventory(stack, Prediction.SERVER_ONLY);
		}
		level.playSound(null, at.x, at.y, at.z, SoundEvents.IRON_DOOR_OPEN, SoundSource.BLOCKS, 0.9F, 0.8F);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.ITEM_BREAK.value(), SoundSource.BLOCKS, 0.6F, 0.9F);
		level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 14, 0.5, 0.7, 0.5, 0.25);
		player.sendOverlayMessage(Component.translatable(set < 0 ? "message.jaymospowers.pod.wreck_scrap" : "message.jaymospowers.pod.wreck_salvaged")
			.withStyle(ChatFormatting.GRAY));
	}
}
