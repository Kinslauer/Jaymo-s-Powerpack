package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import org.jspecify.annotations.Nullable;

/**
 * Andy: "at the end of a life before reincarnation, prompt the player to save their character as a preset. so they can
 * reload with those customizations, explained as a doppleganger. some you met in the past life may comment that you look
 * familiar, and will not elaborate."
 * <p>
 * As a life ends ({@link LifeStory#reincarnateAnew}) the player is offered its face ({@link Offer}): MCA's look and name,
 * and the life story's picks. Kept, it is a preset on their computer (client {@code CharacterPresets}) like any other,
 * with the face beside the picks. Loaded while a new character is made, the face comes back ({@link Wear}): the new
 * life is someone else wearing the old face, a doppelganger. Each face has an id ({@link #FACE}); every MCA villager that
 * remembered the old life keeps that id when it forgets them ({@link #knew}), and the first time the doppelganger speaks to
 * one of them it may say they look familiar ({@link #FAMILIAR_CHANCE}), once, and say no more.
 */
public final class Doppelgangers {
	// ---- tuning ----
	/** The chance that someone who knew the face says so, the first time the doppelganger speaks to them. */
	static final float FAMILIAR_CHANCE = 0.5F;
	/** Lines for it ({@code doppel.jaymoskingdoms.familiar.N}). */
	static final int FAMILIAR_LINES = 6;
	/** Faces one villager keeps at most (the oldest go first). */
	static final int KEPT_FACES = 8;
	/** A face id's longest. */
	static final int MAX_ID = 64;

	/** The face a player wears (its id): drawn as a life ends, or the kept face they wore. Saved, kept through death. */
	public static final AttachmentType<String> FACE = AttachmentRegistry.create(JaymosKingdoms.id("doppel_face"),
		builder -> builder.persistent(Codec.STRING).copyOnDeath());
	/** On a villager: the faces of lives now over that it knew. Saved. */
	static final AttachmentType<List<String>> KNEW = AttachmentRegistry.create(JaymosKingdoms.id("doppel_knew"),
		builder -> builder.persistent(Codec.STRING.listOf()));
	/** On the Overworld: player (UUID) to the face their last ended life wore, for villagers that forget them later ({@link #knew}). */
	static final AttachmentType<Map<String, String>> LAST_FACE = AttachmentRegistry.create(JaymosKingdoms.id("doppel_last_face"),
		builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, Codec.STRING)));

	/**
	 * Server → player: the life that just ended, to keep as a preset if they like. {@code face}: its id; {@code look}: MCA's
	 * look and name as its creator edits them; the rest the life story's picks by key ("" none), {@code gender} 0 male,
	 * 1 female, -1 unknown.
	 */
	public record Offer(String face, CompoundTag look, int gender, String heritage, String variant, List<String> story, String cls, String option)
		implements CustomPacketPayload {
		public static final Type<Offer> TYPE = new Type<>(JaymosKingdoms.id("doppel_offer"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Offer> CODEC = CustomPacketPayload.codec(
			(p, buf) -> {
				buf.writeUtf(p.face);
				ByteBufCodecs.COMPOUND_TAG.encode(buf, p.look);
				buf.writeVarInt(p.gender + 1);
				buf.writeUtf(p.heritage);
				buf.writeUtf(p.variant);
				buf.writeVarInt(p.story.size());
				for (String key : p.story) {
					buf.writeUtf(key);
				}
				buf.writeUtf(p.cls);
				buf.writeUtf(p.option);
			}, buf -> {
				String face = buf.readUtf();
				CompoundTag look = ByteBufCodecs.COMPOUND_TAG.decode(buf);
				int gender = buf.readVarInt() - 1;
				String heritage = buf.readUtf(), variant = buf.readUtf();
				int n = Math.min(buf.readVarInt(), 16);
				List<String> story = new ArrayList<>(n);
				for (int i = 0; i < n; i++) {
					story.add(buf.readUtf());
				}
				return new Offer(face, look, gender, heritage, variant, List.copyOf(story), buf.readUtf(), buf.readUtf());
			});

		@Override
		public Type<Offer> type() {
			return TYPE;
		}
	}

	/** Player → server: while making a character, wear kept face {@code face} ({@code look}: as {@link Offer} gave it). "" takes it off. */
	public record Wear(String face, CompoundTag look) implements CustomPacketPayload {
		public static final Type<Wear> TYPE = new Type<>(JaymosKingdoms.id("doppel_wear"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Wear> CODEC = CustomPacketPayload.codec(
			(p, buf) -> {
				buf.writeUtf(p.face, MAX_ID);
				ByteBufCodecs.COMPOUND_TAG.encode(buf, p.look);
			}, buf -> new Wear(buf.readUtf(MAX_ID), ByteBufCodecs.COMPOUND_TAG.decode(buf)));

		@Override
		public Type<Wear> type() {
			return TYPE;
		}
	}

	private Doppelgangers() {
	}

	static void register() {
		PayloadTypeRegistry.clientboundPlay().register(Offer.TYPE, Offer.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(Wear.TYPE, Wear.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Wear.TYPE, (payload, context) -> wear(context.player(), payload));
		UseEntityCallback.EVENT.register(Doppelgangers::spoken);
	}

	/**
	 * (From {@link LifeStory#reincarnateAnew}, before anyone forgets them) {@code player}'s life is over: the face it wore is
	 * marked for those who knew it, and offered to them to keep.
	 */
	static void lifeEnds(ServerPlayer player) {
		String face = player.getAttachedOrElse(FACE, "");
		if (face.isEmpty()) {
			face = UUID.randomUUID().toString();
		}
		player.removeAttached(FACE);
		ServerLevel overworld = Factions.level(player.level().getServer());
		Map<String, String> last = new HashMap<>(overworld.getAttachedOrElse(LAST_FACE, Map.of()));
		last.put(player.getUUID().toString(), face);
		overworld.setAttached(LAST_FACE, Map.copyOf(last));
		CompoundTag look = McaCompat.faceOf(player);
		if (look == null || !ServerPlayNetworking.canSend(player, Offer.TYPE)) {
			return;
		}
		List<String> story = LifeStory.of(player);
		Heritage.Kind kind = Heritage.of(player);
		Classes.PlayerClass cls = Classes.classOf(player);
		String gender = player.getAttachedOrElse(LifeStory.GENDER, "");
		ServerPlayNetworking.send(player, new Offer(face, look, LifeStory.MALE.equals(gender) ? 0 : LifeStory.FEMALE.equals(gender) ? 1 : -1,
			kind == null ? "" : kind.key(), player.getAttachedOrElse(HeritageVariants.VARIANT, ""), story == null ? List.of() : story,
			cls == null ? "" : cls.key(), cls == null ? "" : Classes.optionOf(player, cls)));
	}

	/** (From {@code McaBridge.forget}) {@code villager} forgets player {@code id}, whose life ended: it keeps the face they wore. */
	static void knew(Entity villager, UUID id) {
		if (!(villager.level() instanceof ServerLevel level)) {
			return;
		}
		String face = Factions.level(level.getServer()).getAttachedOrElse(LAST_FACE, Map.of()).get(id.toString());
		if (face == null) {
			return;
		}
		List<String> faces = new ArrayList<>(villager.getAttachedOrElse(KNEW, List.of()));
		if (faces.contains(face)) {
			return;
		}
		faces.add(face);
		while (faces.size() > KEPT_FACES) {
			faces.remove(0);
		}
		villager.setAttached(KNEW, List.copyOf(faces));
	}

	/** A kept face put on (or taken off) while the character is still being made; never once they're living it. */
	private static void wear(ServerPlayer player, Wear wear) {
		if (!LifeStory.making(player) && !McaCompat.destinyPending(player)) {
			return;
		}
		if (wear.face().isBlank()) {
			player.removeAttached(FACE);
			return;
		}
		player.setAttached(FACE, wear.face());
		if (!wear.look().isEmpty()) {
			McaCompat.wearFace(player, wear.look());
		}
	}

	/**
	 * A doppelganger speaks to someone who knew their face: the first time, they may say they look familiar, and nothing
	 * more (the face is gone from them either way).
	 */
	private static InteractionResult spoken(Player player, Level level, InteractionHand hand, Entity target, @Nullable EntityHitResult hit) {
		if (!(player instanceof ServerPlayer sp) || !(level instanceof ServerLevel here) || hand != InteractionHand.MAIN_HAND
			|| !(target instanceof Villager)) {
			return InteractionResult.PASS;
		}
		String face = sp.getAttached(FACE);
		List<String> faces = face == null ? null : target.getAttached(KNEW);
		if (faces == null || !faces.contains(face)) {
			return InteractionResult.PASS;
		}
		List<String> left = new ArrayList<>(faces);
		left.remove(face);
		if (left.isEmpty()) {
			target.removeAttached(KNEW);
		} else {
			target.setAttached(KNEW, List.copyOf(left));
		}
		if (here.getRandom().nextFloat() < FAMILIAR_CHANCE) {
			Speech.sayTo(here, target, sp, Component.translatable("doppel.jaymoskingdoms.familiar." + here.getRandom().nextInt(FAMILIAR_LINES)), Speech.WHITE);
		}
		return InteractionResult.PASS;
	}
}
