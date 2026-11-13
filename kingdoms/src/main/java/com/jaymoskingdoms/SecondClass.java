package com.jaymoskingdoms;

import com.jaymoskingdoms.Classes.PlayerClass;
import com.jaymoskingdoms.money.Money;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;

/**
 * A second class, learned late (fifth edition's multiclassing). Once a player has a first class and has slain the great foe
 * that opens {@link #KNOWLEDGE_NEEDED} (the Skeleton Lord, knowledge level 3), a <b>master</b> of a class (a villager of the trade
 * the class grows from, at profession level {@link #MASTER_LEVEL}, the top rung) will teach it for {@link #FEE_SILVER} silver:
 * sneak and use the master with an empty hand ({@link ClassTeachers}). A class with something to pick (a patron, an oath, a
 * foe, a first spell) opens the class screen on that class alone, and the lesson is paid when the pick is sealed.
 * <p>
 * The second class climbs slower: its perks, pools and spell power are {@link Classes#SECOND_SHARE} of the first class's
 * ({@link Classes#share}). Both classes' abilities work: the class key {@code J} answers the first; held with {@code Alt}
 * it answers the second ({@link Abilities#second}), each with sneak as before. A Sorcerer's gift is born in the blood and
 * is never taught, so it is the one class no master teaches. The Writ of Rebirth unmakes both classes (and the paths and
 * feats with them).
 */
public final class SecondClass {
	/** The knowledge level a player needs before a master will take them on (3: the Skeleton Lord has fallen). */
	public static final int KNOWLEDGE_NEEDED = 3;
	/** The profession level of a master (the top rung of the villager ladder). */
	static final int MASTER_LEVEL = 5;
	/** What the lesson costs. */
	static final int FEE_SILVER = 60;
	/** The trades that grow each class. */
	private static final Map<PlayerClass, Set<ResourceKey<VillagerProfession>>> MASTERS = new EnumMap<>(PlayerClass.class);

	static {
		MASTERS.put(PlayerClass.THIEF, Set.of(VillagerProfession.CARTOGRAPHER, VillagerProfession.LEATHERWORKER));
		MASTERS.put(PlayerClass.WARLORD, Set.of(VillagerProfession.ARMORER, VillagerProfession.WEAPONSMITH));
		MASTERS.put(PlayerClass.MAGE, Set.of(VillagerProfession.LIBRARIAN));
		MASTERS.put(PlayerClass.BRUTE, Set.of(VillagerProfession.BUTCHER, VillagerProfession.TOOLSMITH));
		MASTERS.put(PlayerClass.MARKSMAN, Set.of(VillagerProfession.FLETCHER));
		MASTERS.put(PlayerClass.RIDER, Set.of(VillagerProfession.SHEPHERD, VillagerProfession.LEATHERWORKER));
		MASTERS.put(PlayerClass.CLERIC, Set.of(VillagerProfession.CLERIC));
		MASTERS.put(PlayerClass.DRUID, Set.of(VillagerProfession.FARMER, VillagerProfession.SHEPHERD));
		MASTERS.put(PlayerClass.MONK, Set.of(VillagerProfession.MASON, VillagerProfession.CARTOGRAPHER));
		MASTERS.put(PlayerClass.PALADIN, Set.of(VillagerProfession.CLERIC, VillagerProfession.ARMORER));
		MASTERS.put(PlayerClass.RANGER, Set.of(VillagerProfession.FLETCHER, VillagerProfession.BUTCHER));
		MASTERS.put(PlayerClass.WARLOCK, Set.of(VillagerProfession.LIBRARIAN));
		MASTERS.put(PlayerClass.TINKERER, Set.of(VillagerProfession.TOOLSMITH, VillagerProfession.WEAPONSMITH, VillagerProfession.ARMORER));
	}

	/** The class each player was offered with options to pick, until they seal it (or walk off). */
	private static final Map<UUID, PlayerClass> PENDING = new HashMap<>();

	private SecondClass() {
	}

	/** Player → server: the class key was pressed with Alt held ({@code sneak}: with sneak too): the second class's button. */
	public record Key(boolean sneak) implements CustomPacketPayload {
		public static final Type<Key> TYPE = new Type<>(JaymosKingdoms.id("second_key"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Key> CODEC = CustomPacketPayload.codec(
			(p, buf) -> buf.writeBoolean(p.sneak), buf -> new Key(buf.readBoolean()));

		@Override
		public Type<Key> type() {
			return TYPE;
		}
	}

	static void register() {
		PayloadTypeRegistry.serverboundPlay().register(Key.TYPE, Key.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Key.TYPE, (payload, context) -> context.server().execute(() -> {
			if (context.player().isAlive() && !context.player().isSpectator()) {
				Abilities.second(context.player(), payload.sneak());
			}
		}));
	}

	static void clear() {
		PENDING.clear();
	}

	/** The classes unmade with the character. */
	static void unmake(ServerPlayer player) {
		PENDING.remove(player.getUUID());
	}

	/** Whether {@code player} may take {@code cls} as a second class now. */
	public static boolean may(ServerPlayer player, PlayerClass cls) {
		PlayerClass first = Classes.classOf(player);
		return first != null && first != cls && Classes.second(player) == null && cls != PlayerClass.SORCERER
			&& (player.isCreative() || Knowledge.level(player) >= KNOWLEDGE_NEEDED);
	}

	/** The option kinds ("learn:cleric") {@code master} offers {@code player} now. */
	static List<String> offers(ServerPlayer player, Villager master) {
		List<String> out = new ArrayList<>();
		if (master.isBaby() || master.getVillagerData().level() < MASTER_LEVEL) {
			return out;
		}
		for (Map.Entry<PlayerClass, Set<ResourceKey<VillagerProfession>>> e : MASTERS.entrySet()) {
			if (e.getValue().stream().anyMatch(p -> master.getVillagerData().profession().is(p)) && may(player, e.getKey())) {
				out.add("learn:" + e.getKey().key());
			}
		}
		return out;
	}

	/** The class a "learn:..." kind teaches. */
	static PlayerClass classOf(String kind) {
		return PlayerClass.byKey(kind.substring("learn:".length()));
	}

	/** {@code player} took a master's offer: the class screen opens if there is a pick to make, else the lesson is given. */
	static void accept(ServerPlayer player, LivingEntity master, String kind) {
		PlayerClass cls = classOf(kind);
		if (cls == null || !may(player, cls) || !Money.canPay(player, Money.worth(FEE_SILVER))) {
			return;
		}
		if (cls.options.isEmpty()) {
			teach(player, master, cls, "");
			return;
		}
		PENDING.put(player.getUUID(), cls);
		if (ServerPlayNetworking.canSend(player, Classes.Open.TYPE)) {
			ServerPlayNetworking.send(player, new Classes.Open(false, cls.ordinal() + 1));
		}
	}

	/** The class screen came back with the pick: the lesson is paid and given. */
	static void chosen(ServerPlayer player, PlayerClass cls, String option) {
		if (PENDING.remove(player.getUUID()) != cls || !may(player, cls) || !Money.canPay(player, Money.worth(FEE_SILVER))) {
			return;
		}
		teach(player, null, cls, option);
	}

	private static void teach(ServerPlayer player, LivingEntity master, PlayerClass cls, String option) {
		if (!Money.charge(player, Money.worth(FEE_SILVER))) {
			return;
		}
		learn(player, cls, option);
		ServerLevel level = player.level();
		if (master != null) {
			Speech.tell(level, master, player, Component.translatable("talk.jaymoskingdoms.teacher.learned", Component.translatable("class.jaymoskingdoms." + cls.key())), Speech.GOLD);
		}
	}

	/** Makes {@code cls} {@code player}'s second class (no payment, no check: the master has done both). */
	static void learn(ServerPlayer player, PlayerClass cls, String option) {
		player.setAttached(Classes.SECOND, new Classes.Chosen(cls.key(), option, List.of(), 0));
		// a caster's spells go on the one staff, after the first class's
		List<String> add = Classes.startingSpells(cls, option);
		Classes.Chosen first = Classes.of(player);
		if (!add.isEmpty()) {
			List<String> merged = new ArrayList<>(first.spells());
			for (String spell : add) {
				if (!merged.contains(spell)) {
					merged.add(spell);
				}
			}
			player.setAttached(Classes.CLASS, new Classes.Chosen(first.cls(), first.option(), List.copyOf(merged), first.selected()));
		}
		Classes.refresh(player);
		// one patron at a time: the first one sworn keeps the oath
		boolean patronClass = cls == PlayerClass.CLERIC || cls == PlayerClass.WARLOCK;
		Classes.begin(player, cls, patronClass && Patrons.of(player) != null ? "" : option);
		ServerLevel level = player.level();
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.7F, 0.8F);
		player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.class.second", Component.translatable("class.jaymoskingdoms." + cls.key()),
			Math.round(Classes.SECOND_SHARE * 100)).withStyle(net.minecraft.ChatFormatting.GOLD));
	}
}
