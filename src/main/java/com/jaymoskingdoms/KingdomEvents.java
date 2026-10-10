package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * How kingdoms come to be and what moves them: a village's bell becomes a capital the first time someone comes near
 * ({@link #FOUND_RANGE}), with a king, guards, a banner and land around it; deaths shift reputation and standing; a
 * fallen king's kingdom meets one of three fates (a successor, vassalage to the victor's kingdom, or ruin); crossing
 * into a kingdom's land announces it.
 */
public final class KingdomEvents {
	/** A bell this close to a player becomes a capital (if no capital is within {@code CAPITAL_SPACING}). */
	static final int FOUND_RANGE = 48, CAPITAL_SPACING = 160;
	/** Land claimed around a new capital: a square this many chunks out each way. */
	static final int CLAIM_RADIUS = 3;
	static final int GUARDS = 5;
	/** Reputation changes. */
	static final int REP_KILL_GUARD = -40, REP_KILL_KING = -400, REP_KILL_KING_RIVALS = 60, REP_KILL_MONSTER = 2;
	/** A successor takes the throne this long after the king falls (game ticks: one day). */
	static final long SUCCESSION_TICKS = 24000L;

	/** Per player: the kingdom whose land they were last in (for the "entering" message). */
	private static final Map<UUID, Integer> WHERE = new HashMap<>();

	private KingdomEvents() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(KingdomEvents::tick);
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			// a monster cut down on a kingdom's land: they're grateful (members earn standing too)
			if (entity instanceof Enemy && source.getEntity() instanceof ServerPlayer player && entity.level() instanceof ServerLevel level) {
				Kingdom kingdom = Kingdoms.at(level.getServer(), level, entity.blockPosition());
				if (kingdom != null) {
					Reputation.change(player, kingdom, REP_KILL_MONSTER);
					Kingdom.Member member = kingdom.member(player.getUUID());
					if (member != null) {
						standing(level.getServer(), player, kingdom, member.standing() + 3);
					}
				}
			}
		});
	}

	private static void tick(MinecraftServer server) {
		long now = server.overworld().getGameTime();
		if (now % 100 == 0) {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				if (player.level() == server.overworld() && !player.isSpectator()) {
					tryFound(server.overworld(), player);
				}
			}
			for (Kingdom kingdom : Kingdoms.all(server)) {
				if (kingdom.successionAt() >= 0 && now >= kingdom.successionAt()) {
					crown(server.overworld(), kingdom.withSuccessionAt(-1L));
				}
			}
		}
		if (now % 20 == 0) {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				Kingdom here = Kingdoms.at(server, player.level(), player.blockPosition());
				int id = here == null ? -1 : here.id();
				Integer was = WHERE.put(player.getUUID(), id);
				if (was != null && was != id && here != null) {
					player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.entering", here.name(),
						Component.translatable("reputation.jaymoskingdoms." + Reputation.word(Reputation.get(player, here.id()))))
						.withStyle(here.color().getTextColor() == 0 ? ChatFormatting.WHITE : ChatFormatting.GOLD));
				}
			}
		}
	}

	/** A village's bell near {@code player} that isn't a capital yet becomes one. */
	private static void tryFound(ServerLevel level, ServerPlayer player) {
		Optional<BlockPos> bell = level.getPoiManager().findClosest(holder -> holder.is(PoiTypes.MEETING), player.blockPosition(), FOUND_RANGE,
			PoiManager.Occupancy.ANY);
		if (bell.isEmpty() || Kingdoms.near(level.getServer(), bell.get(), CAPITAL_SPACING) != null) {
			return;
		}
		found(level, bell.get());
	}

	/** A new kingdom at {@code capital}: named, coloured, its land claimed, its banner raised, its king and guards. */
	static Kingdom found(ServerLevel level, BlockPos capital) {
		MinecraftServer server = level.getServer();
		var random = level.getRandom();
		DyeColor color = DyeColor.values()[random.nextInt(DyeColor.values().length)];
		List<Long> claims = new ArrayList<>();
		ChunkPos center = ChunkPos.containing(capital);
		for (int dx = -CLAIM_RADIUS; dx <= CLAIM_RADIUS; dx++) {
			for (int dz = -CLAIM_RADIUS; dz <= CLAIM_RADIUS; dz++) {
				ChunkPos chunk = new ChunkPos(center.x() + dx, center.z() + dz);
				boolean taken = false;
				for (Kingdom other : Kingdoms.all(server)) {
					if (other.claims(chunk)) {
						taken = true;
						break;
					}
				}
				if (!taken) {
					claims.add(chunk.pack());
				}
			}
		}
		Kingdom kingdom = new Kingdom(Kingdoms.nextId(server), Names.kingdom(random), color, capital, Optional.empty(), Map.of(), 0L, List.copyOf(claims),
			-1, false, -1L);
		Kingdoms.put(server, kingdom);
		raiseBanner(level, kingdom);
		kingdom = crown(level, kingdom);
		for (int i = 0; i < GUARDS; i++) {
			spawn(level, kingdom, Folk.GUARD);
		}
		JaymosKingdoms.LOGGER.info("Kingdom of {} founded at {}", kingdom.name(), capital);
		return kingdom;
	}

	/** A king takes the throne (a new kingdom's first, or a successor). Returns the kingdom with him in it. */
	static Kingdom crown(ServerLevel level, Kingdom kingdom) {
		Folk king = spawn(level, kingdom, Folk.KING);
		Kingdom crowned = kingdom.withKing(king == null ? Optional.empty() : Optional.of(king.getUUID()));
		Kingdoms.put(level.getServer(), crowned);
		if (king != null && kingdom.successionAt() < 0 && kingdom.king().isPresent()) {
			announce(level.getServer(), Component.translatable("message.jaymoskingdoms.succession", kingdom.name()).withStyle(ChatFormatting.GOLD));
		}
		return crowned;
	}

	/** One of the kingdom's people, standing by the capital. */
	static Folk spawn(ServerLevel level, Kingdom kingdom, int role) {
		var random = level.getRandom();
		for (int tries = 0; tries < 12; tries++) {
			BlockPos at = kingdom.capital().offset(random.nextInt(13) - 6, 0, random.nextInt(13) - 6);
			at = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at);
			if (!level.getBlockState(at.below()).isSolid() || !level.getFluidState(at.below()).isEmpty()) {
				continue;
			}
			Folk folk = JaymosKingdoms.FOLK.create(level, EntitySpawnReason.STRUCTURE);
			if (folk == null) {
				return null;
			}
			folk.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, random.nextFloat() * 360.0F, 0.0F);
			folk.setup(role, kingdom);
			level.addFreshEntity(folk);
			return folk;
		}
		return null;
	}

	/** The kingdom's banner, on the ground beside the bell. */
	private static void raiseBanner(ServerLevel level, Kingdom kingdom) {
		Block banner = BuiltInRegistries.BLOCK.getValue(Identifier.withDefaultNamespace(kingdom.color().getName() + "_banner"));
		for (int tries = 0; tries < 10; tries++) {
			BlockPos at = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
				kingdom.capital().offset(level.getRandom().nextInt(7) - 3, 0, level.getRandom().nextInt(7) - 3));
			if (level.getBlockState(at).isAir() && level.getBlockState(at.below()).isSolid()) {
				if (banner != Blocks.AIR) {
					level.setBlock(at, banner.defaultBlockState(), Block.UPDATE_ALL);
				}
				return;
			}
		}
	}

	/** One of a kingdom's people died: reputation for whoever did it; a king's death decides the kingdom's fate. */
	static void folkDied(ServerLevel level, Folk folk, DamageSource source) {
		MinecraftServer server = level.getServer();
		Kingdom kingdom = Kingdoms.get(server, folk.kingdomId());
		if (kingdom == null) {
			return;
		}
		ServerPlayer killer = source.getEntity() instanceof ServerPlayer p ? p : null;
		if (folk.role() == Folk.GUARD) {
			if (killer != null) {
				Reputation.change(killer, kingdom, REP_KILL_GUARD);
			}
			return;
		}
		// the king has fallen
		kingdom = kingdom.withKing(Optional.empty());
		if (killer != null) {
			Reputation.change(killer, kingdom, REP_KILL_KING);
			// the kingdom's rivals are glad of it (every other standing kingdom)
			for (Kingdom other : Kingdoms.all(server)) {
				if (other.id() != kingdom.id() && !other.fallen() && other.overlord() != kingdom.id()) {
					Reputation.change(killer, other, REP_KILL_KING_RIVALS);
				}
			}
		}
		announce(server, Component.translatable("message.jaymoskingdoms.king_fallen", kingdom.name(),
			killer == null ? Component.translatable("message.jaymoskingdoms.someone") : killer.getDisplayName()).withStyle(ChatFormatting.RED));
		// one of three fates, at random
		Kingdom victor = killer == null ? null : Kingdoms.of(server, killer.getUUID());
		int fate = level.getRandom().nextInt(3);
		if (fate == 1 && victor != null && victor.id() != kingdom.id()) {
			kingdom = kingdom.withOverlord(victor.id()).withSuccessionAt(level.getGameTime() + SUCCESSION_TICKS);
			announce(server, Component.translatable("message.jaymoskingdoms.fate.vassal", kingdom.name(), victor.name()).withStyle(ChatFormatting.GOLD));
		} else if (fate == 2) {
			kingdom = kingdom.withFallen(true);
			for (Folk guard : level.getEntitiesOfClass(Folk.class, folk.getBoundingBox().inflate(96.0), f -> f.kingdomId() == folk.kingdomId())) {
				// with nothing left to guard, they scatter
				guard.discard();
			}
			announce(server, Component.translatable("message.jaymoskingdoms.fate.ruin", kingdom.name()).withStyle(ChatFormatting.DARK_RED));
		} else {
			kingdom = kingdom.withSuccessionAt(level.getGameTime() + SUCCESSION_TICKS);
			announce(server, Component.translatable("message.jaymoskingdoms.fate.successor", kingdom.name()).withStyle(ChatFormatting.YELLOW));
		}
		Kingdoms.put(server, kingdom);
	}

	/** An audience with one of a kingdom's people (right click): what they think of you, and an offer to pledge. */
	static void audience(ServerPlayer player, Folk folk) {
		MinecraftServer server = player.level().getServer();
		Kingdom kingdom = Kingdoms.get(server, folk.kingdomId());
		if (kingdom == null) {
			return;
		}
		int rep = Reputation.get(player, kingdom.id());
		Kingdom.Member member = kingdom.member(player.getUUID());
		player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.audience", kingdom.name(),
			Component.translatable("reputation.jaymoskingdoms." + Reputation.word(rep)), rep).withStyle(ChatFormatting.GOLD));
		if (member != null) {
			Rank rank = Rank.forStanding(member.standing());
			player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.audience.member", Component.translatable(rank.key()), member.standing())
				.withStyle(ChatFormatting.GRAY));
		} else if (folk.role() == Folk.KING && rep >= Reputation.WELCOME && Kingdoms.of(server, player.getUUID()) == null) {
			String command = "/kingdom pledge " + kingdom.name();
			player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.audience.offer").withStyle(style -> style.withColor(ChatFormatting.GREEN)
				.withUnderlined(true).withClickEvent(new ClickEvent.RunCommand(command)).withHoverEvent(new HoverEvent.ShowText(Component.literal(command)))));
		}
		player.level().playSound(null, folk.getX(), folk.getY(), folk.getZ(), SoundEvents.VILLAGER_AMBIENT, SoundSource.NEUTRAL, 1.0F, 0.7F);
	}

	/** Sets a member's standing (and so their rank), telling them of a promotion. */
	static void standing(MinecraftServer server, ServerPlayer player, Kingdom kingdom, int standing) {
		Kingdom.Member member = kingdom.member(player.getUUID());
		if (member == null) {
			return;
		}
		Rank before = Rank.forStanding(member.standing());
		Rank after = Rank.forStanding(standing);
		Kingdoms.put(server, kingdom.withMember(player.getUUID(), new Kingdom.Member(after.ordinal(), standing)));
		if (after.ordinal() > before.ordinal()) {
			player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.promoted", Component.translatable(after.key()), kingdom.name())
				.withStyle(ChatFormatting.GOLD));
			player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1.0F, 0.8F);
		}
	}

	static void announce(MinecraftServer server, Component message) {
		server.getPlayerList().broadcastSystemMessage(message, false);
	}

	static void forget(ServerPlayer player) {
		WHERE.remove(player.getUUID());
	}

	@SuppressWarnings("unused")
	private static boolean living(LivingEntity e) {
		return e.isAlive();
	}
}
