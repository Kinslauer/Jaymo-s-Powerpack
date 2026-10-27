package com.jaymospowers.power.zfighter;

import com.jaymospowers.core.Powers;
import com.jaymospowers.core.wounds.Downed;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.monster.Vex;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.17.60, Andy's NOEA picks) Themed invasions: the tyrant raid ({@link ZFighterRaids}) grown into a siege. Every raid
 * night an army of one of four {@link Theme}s (a Frost tyrant's armada, an android legion, an ape horde, a demon host) lands
 * round a kingdom's seat: {@link #WAVES} waves of its soldiers, one after the last is mostly beaten
 * ({@link #WAVE_CLEAR}), and then its tyrant ({@link ZFighterRogue}) with an escort.
 * <ul>
 * <li><b>The line:</b> everyone inside {@link #BOUNDARY} of the seat when the army lands, or who comes in later, is a defender.
 * A particle wall marks the line. A defender who stays outside it for {@link #LEAVE_GRACE} ticks forfeits his share.</li>
 * <li><b>The bar:</b> a boss bar shows the siege's progress, soldiers beaten out of all there will be.</li>
 * <li><b>The prize</b> (listed to everyone near when the army lands, given when the tyrant falls): {@link #TOTAL_TP} TP (a Z
 * Fighter; anyone else {@link #TOTAL_XP} levels of experience), {@link #TOTAL_EMERALDS} emeralds, the theme's items and some
 * hermit beans, shared by the damage each defender did to the invaders (at least a floor each, if he did any), and the
 * tyrant's chance of a pair of bonding earrings ({@link ZFighterFusionItems#tyrantDrop}) for whoever hurt him most.</li>
 * <li><b>The fall:</b> the kingdom falls if the time ({@link #INVASION_TICKS}) runs out, if its people are cut down to
 * {@link #FALL_PEOPLE} of what they were, or if no defender is left standing for {@link #GONE_TICKS}. Then the army goes
 * home, and every defender loses {@link #STANDING_LOSS} reputation with the realm, told to Jaymo's Kingdoms through
 * {@link ZFighterKingdoms#changeReputation}.</li>
 * </ul>
 */
public final class ZFighterInvasions {
	/** Off: raids go back to a lone tyrant. */
	static final boolean ENABLED = true;
	/** The line: blocks from the seat. */
	static final double BOUNDARY = 56.0;
	/** Ticks outside the line before a defender forfeits. */
	static final int LEAVE_GRACE = 100;
	/** Waves of soldiers before the tyrant's. */
	static final int WAVES = 3;
	/** Ticks before the first wave; between a wave being beaten down and the next; the longest a wave may hold the next back. */
	static final int FIRST_DELAY = 200;
	static final int WAVE_BREAK = 160;
	static final int WAVE_MAX = 3600;
	/** The next wave comes when this share or less of the last is left. */
	static final float WAVE_CLEAR = 0.2F;
	/** The siege's length (ticks) before the kingdom falls. */
	static final int INVASION_TICKS = 24000;
	/** The kingdom falls when its people are this share or fewer of what they were (of at least {@code MIN_PEOPLE}). */
	static final float FALL_PEOPLE = 0.25F;
	static final int MIN_PEOPLE = 4;
	/** It falls when no defender has been standing in the line for this long (ticks). */
	static final int GONE_TICKS = 1200;
	/** Reputation each defender loses with the realm when it falls. */
	static final int STANDING_LOSS = 120;
	/** Soldiers: a squad's size is its base times this plus this per defender past the first; no more than {@code MAX_SQUAD} a squad. */
	static final double SOLO_SCALE = 0.6, PER_DEFENDER = 0.4;
	static final int MAX_SQUAD = 16;
	/** The prize, shared by damage: TP, experience levels, emeralds, and the least each defender who did any damage gets. */
	static final int TOTAL_TP = 2000, TOTAL_XP = 40, TOTAL_EMERALDS = 64;
	static final int MIN_TP = 125, MIN_XP = 5, MIN_EMERALDS = 8;
	/** Every rewarded defender also gets this many hermit beans. */
	static final int BEANS = 2;
	/** Damage dealt to the invaders under this earns no share (unless nobody did any). */
	static final float MIN_DAMAGE = 5.0F;
	/** The line's wall: how far along it a player sees (blocks), the gap between motes, and its height. */
	static final double WALL_SEEN = 24.0, WALL_STEP = 2.0;
	static final int WALL_HEIGHT = 4;
	static final String TAG = "jaymospowers_invader";

	/** One kind of soldier in a wave: what it is, how many (base), the armour (0 none, 1 leather, 2 chain, 3 iron), its size and strength. */
	private record Squad(EntityType<? extends Mob> type, int count, int armour, double scale, int strength, int speed, @Nullable Item weapon) {
	}

	/** An army: its colour, the soldiers of each wave, the tyrant's size, and what it pays besides TP and emeralds. */
	private enum Theme {
		FROST("frost", BossEvent.BossBarColor.BLUE, 0xA8E8FF, 1.0, Items.DIAMOND, 3, new Squad[][] {
			{new Squad(EntityTypes.STRAY, 6, 1, 1.0, 0, 0, Items.BOW)},
			{new Squad(EntityTypes.STRAY, 6, 2, 1.0, 0, 0, Items.BOW), new Squad(EntityTypes.HUSK, 3, 2, 1.0, 0, 0, Items.STONE_SWORD)},
			{new Squad(EntityTypes.STRAY, 6, 3, 1.0, 1, 0, Items.BOW), new Squad(EntityTypes.RAVAGER, 1, 0, 0.9, 0, 0, null)},
		}),
		ANDROID("android", BossEvent.BossBarColor.WHITE, 0xC8D0D8, 1.1, Items.NETHERITE_SCRAP, 2, new Squad[][] {
			{new Squad(EntityTypes.PILLAGER, 5, 2, 1.0, 0, 0, Items.CROSSBOW)},
			{new Squad(EntityTypes.VINDICATOR, 5, 3, 1.0, 0, 1, Items.IRON_AXE)},
			{new Squad(EntityTypes.VINDICATOR, 4, 3, 1.0, 1, 1, Items.IRON_AXE), new Squad(EntityTypes.PILLAGER, 4, 3, 1.0, 0, 0, Items.CROSSBOW),
				new Squad(EntityTypes.RAVAGER, 1, 0, 1.0, 0, 0, null)},
		}),
		APE("ape", BossEvent.BossBarColor.RED, 0xB06030, 1.6, Items.GOLDEN_APPLE, 3, new Squad[][] {
			{new Squad(EntityTypes.ZOMBIE, 8, 0, 1.25, 0, 0, null)},
			{new Squad(EntityTypes.ZOMBIE, 6, 0, 1.35, 1, 0, null), new Squad(EntityTypes.RAVAGER, 1, 0, 0.9, 0, 0, null)},
			{new Squad(EntityTypes.ZOMBIE, 5, 0, 1.5, 2, 0, null), new Squad(EntityTypes.RAVAGER, 2, 0, 1.0, 0, 0, null)},
		}),
		DEMON("demon", BossEvent.BossBarColor.PURPLE, 0xB040FF, 1.2, Items.BLAZE_ROD, 6, new Squad[][] {
			{new Squad(EntityTypes.VEX, 6, 0, 1.0, 0, 0, Items.IRON_SWORD)},
			{new Squad(EntityTypes.WITHER_SKELETON, 5, 2, 1.0, 0, 0, Items.STONE_SWORD), new Squad(EntityTypes.VEX, 3, 0, 1.0, 0, 0, Items.IRON_SWORD)},
			{new Squad(EntityTypes.EVOKER, 2, 2, 1.0, 0, 0, null), new Squad(EntityTypes.WITHER_SKELETON, 5, 3, 1.0, 1, 0, Items.IRON_SWORD),
				new Squad(EntityTypes.VEX, 3, 0, 1.0, 0, 0, Items.IRON_SWORD)},
		});

		final String key;
		final BossEvent.BossBarColor color;
		final int dust;
		final double bossScale;
		final Item item;
		final int itemCount;
		final Squad[][] waves;

		Theme(String key, BossEvent.BossBarColor color, int dust, double bossScale, Item item, int itemCount, Squad[][] waves) {
			this.key = key;
			this.color = color;
			this.dust = dust;
			this.bossScale = bossScale;
			this.item = item;
			this.itemCount = itemCount;
			this.waves = waves;
		}

		/** The escort that comes with the tyrant: the last wave's first squad, thinned. */
		Squad escort() {
			Squad s = waves[WAVES - 1][0];
			return new Squad(s.type(), Math.max(2, s.count() / 2), s.armour(), s.scale(), s.strength(), s.speed(), s.weapon());
		}

		static @Nullable Theme byKey(String key) {
			for (Theme theme : values()) {
				if (theme.key.equals(key)) {
					return theme;
				}
			}
			return null;
		}
	}

	private static final class Defender {
		final Component name;
		float damage;
		int outside;
		boolean forfeited;

		Defender(Component name) {
			this.name = name;
		}
	}

	private static final class Invasion {
		final ServerLevel level;
		final String kingdom;
		final BlockPos seat;
		final ZFighterKingdoms.@Nullable Settlement town;
		final Theme theme;
		final ServerBossEvent bar;
		final Set<UUID> invaders = new LinkedHashSet<>();
		final Map<UUID, BlockPos> seen = new HashMap<>();
		final Map<UUID, Defender> defenders = new LinkedHashMap<>();
		/** The wave being fought (-1 before the first, {@code WAVES} the tyrant's). */
		int wave = -1;
		int defeated;
		int waveSize;
		long waveAt;
		long nextAt;
		final long endAt;
		int people;
		long lastDefenderAt;
		@Nullable UUID boss;
		List<LivingEntity> targets = List.of();

		Invasion(ServerLevel level, String kingdom, BlockPos seat, ZFighterKingdoms.@Nullable Settlement town, Theme theme, ServerBossEvent bar) {
			this.level = level;
			this.kingdom = kingdom;
			this.seat = seat;
			this.town = town;
			this.theme = theme;
			this.bar = bar;
			this.endAt = level.getGameTime() + INVASION_TICKS;
		}
	}

	private static @Nullable Invasion current;

	private ZFighterInvasions() {
	}

	static void init() {
		ServerTickEvents.END_SERVER_TICK.register(ZFighterInvasions::tick);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			if (current != null) {
				disband(current);
			}
			current = null;
		});
		// soldiers left over from a restart have no army: they go
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity.entityTags().contains(TAG) && (current == null || !current.invaders.contains(entity.getUUID()))) {
				entity.discard();
			}
		});
		// what the defenders do to the army is their share
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damageTaken, blocked) -> {
			Invasion inv = current;
			if (inv == null || damageTaken <= 0.0F || !inv.invaders.contains(entity.getUUID())) {
				return;
			}
			Entity attacker = source.getEntity();
			Defender defender = attacker == null ? null : inv.defenders.get(attacker.getUUID());
			if (defender != null && !defender.forfeited) {
				defender.damage += damageTaken;
			}
		});
	}

	/** Whether an invasion is on. */
	static boolean active() {
		return current != null;
	}

	// ---------------------------------------------------------------- landing

	/** An army of a random theme lands round this kingdom. False if one is already on. */
	static boolean begin(ServerLevel level, BlockPos seat, String kingdom) {
		Theme[] themes = Theme.values();
		return begin(level, seat, kingdom, themes[level.getRandom().nextInt(themes.length)]);
	}

	/** The same, of the theme with this key (the debug command). */
	static boolean begin(ServerLevel level, BlockPos seat, String kingdom, @Nullable String theme) {
		Theme picked = theme == null ? null : Theme.byKey(theme);
		return begin(level, seat, kingdom, picked != null ? picked : Theme.values()[level.getRandom().nextInt(Theme.values().length)]);
	}

	/** The key of every theme (for the debug command). */
	static List<String> themes() {
		List<String> keys = new ArrayList<>();
		for (Theme theme : Theme.values()) {
			keys.add(theme.key);
		}
		return keys;
	}

	private static boolean begin(ServerLevel level, BlockPos seat, String kingdom, Theme theme) {
		if (current != null) {
			return false;
		}
		ZFighterKingdoms.Settlement town = null;
		for (ZFighterKingdoms.Settlement s : ZFighterKingdoms.settlements(level.getServer())) {
			if (s.seat().equals(seat)) {
				town = s;
			}
		}
		ServerBossEvent bar = new ServerBossEvent(UUID.randomUUID(), title(theme, kingdom, 0), theme.color, BossEvent.BossBarOverlay.NOTCHED_10);
		bar.setProgress(0.0F);
		Invasion inv = new Invasion(level, kingdom, seat, town, theme, bar);
		inv.nextAt = level.getGameTime() + FIRST_DELAY;
		inv.lastDefenderAt = level.getGameTime();
		inv.people = town == null ? 0 : ZFighterKingdoms.people(level, town).size();
		current = inv;
		Vec3 c = Vec3.atBottomCenterOf(seat);
		level.playSound(null, seat, SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 4.0F, 1.2F);
		LightningBolt bolt = EntityTypes.LIGHTNING_BOLT.create(level, EntitySpawnReason.TRIGGERED);
		if (bolt != null) {
			Vec3 flash = c.add(BOUNDARY * 0.5, 0.0, 0.0);
			bolt.snapTo(flash.x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int)flash.x, (int)flash.z), flash.z);
			bolt.setVisualOnly(true);
			level.addFreshEntity(bolt);
		}
		level.getServer().getPlayerList().broadcastSystemMessage(Component.translatable("message.jaymospowers.zfighter_invasion",
			Component.translatable("invasion.jaymospowers.zfighter." + theme.key), kingdom).withStyle(ChatFormatting.RED), false);
		for (ServerPlayer player : level.players()) {
			if (player.distanceToSqr(c) <= (BOUNDARY + 96.0) * (BOUNDARY + 96.0)) {
				for (Component line : rewards(inv)) {
					player.sendSystemMessage(line);
				}
			}
		}
		return true;
	}

	private static Component title(Theme theme, String kingdom, int wave) {
		return Component.translatable("bossbar.jaymospowers.zfighter_invasion", Component.translatable("invasion.jaymospowers.zfighter." + theme.key),
			kingdom, Math.min(wave + 1, WAVES + 1), WAVES + 1);
	}

	/** The prize and the rules, as the lines said to everyone near when the army lands (and to anyone who asks). */
	private static List<Component> rewards(Invasion inv) {
		List<Component> lines = new ArrayList<>();
		lines.add(Component.translatable("message.jaymospowers.zfighter_invasion_rules", (int)BOUNDARY, LEAVE_GRACE / 20).withStyle(ChatFormatting.GOLD));
		lines.add(Component.translatable("message.jaymospowers.zfighter_invasion_prize_tp", TOTAL_TP, TOTAL_XP).withStyle(ChatFormatting.YELLOW));
		lines.add(Component.translatable("message.jaymospowers.zfighter_invasion_prize_items", TOTAL_EMERALDS, inv.theme.itemCount,
			new ItemStack(inv.theme.item).getHoverName(), BEANS).withStyle(ChatFormatting.YELLOW));
		lines.add(Component.translatable("message.jaymospowers.zfighter_invasion_prize_earrings",
			Math.round(ZFighterFusionItems.TYRANT_DROP_CHANCE * 100.0F)).withStyle(ChatFormatting.LIGHT_PURPLE));
		if (inv.town != null) {
			lines.add(Component.translatable("message.jaymospowers.zfighter_invasion_stake", inv.kingdom, STANDING_LOSS).withStyle(ChatFormatting.RED));
		}
		return lines;
	}

	/** What {@code /zfighter invasion} says: the siege's state and its prize, or that there is none. */
	static List<Component> describe() {
		Invasion inv = current;
		if (inv == null) {
			return List.of(Component.translatable("message.jaymospowers.zfighter_invasion_none").withStyle(ChatFormatting.GRAY));
		}
		List<Component> lines = new ArrayList<>();
		lines.add(Component.translatable("message.jaymospowers.zfighter_invasion_state", Component.translatable("invasion.jaymospowers.zfighter." + inv.theme.key),
			inv.kingdom, Math.min(inv.wave + 1, WAVES + 1), WAVES + 1, inv.invaders.size(), inv.defenders.size()).withStyle(ChatFormatting.WHITE));
		lines.addAll(rewards(inv));
		return lines;
	}

	// ---------------------------------------------------------------- the siege

	private static void tick(MinecraftServer server) {
		Invasion inv = current;
		if (inv == null) {
			return;
		}
		ServerLevel level = inv.level;
		long now = level.getGameTime();
		if (now % 10 == 0) {
			wall(inv);
		}
		if (now % 20 != 0) {
			return;
		}
		tally(inv);
		watchLine(inv, now);
		bar(inv);
		target(inv);
		if (inv.wave < WAVES && now >= inv.nextAt && (inv.wave < 0 || waveBeaten(inv) || now - inv.waveAt >= WAVE_MAX)) {
			spawnWave(inv, now);
		}
		if (inv.boss != null && !inv.invaders.contains(inv.boss)) {
			// the tyrant is gone without a death to tell (removed): it counts as won
			win(inv);
			return;
		}
		if (fell(inv, now)) {
			fall(inv);
		}
	}

	/** Counts the soldiers still standing; those that are gone are beaten. */
	private static void tally(Invasion inv) {
		var it = inv.invaders.iterator();
		while (it.hasNext()) {
			UUID id = it.next();
			Entity entity = inv.level.getEntity(id);
			if (entity != null && entity.isAlive()) {
				inv.seen.put(id, entity.blockPosition());
				continue;
			}
			BlockPos last = inv.seen.get(id);
			if (entity == null && last != null && !inv.level.hasChunkAt(last)) {
				// asleep in an unloaded chunk: not beaten yet
				continue;
			}
			it.remove();
			inv.seen.remove(id);
			if (!id.equals(inv.boss)) {
				inv.defeated++;
			}
		}
	}

	private static boolean waveBeaten(Invasion inv) {
		int left = 0;
		for (UUID id : inv.invaders) {
			if (!id.equals(inv.boss)) {
				left++;
			}
		}
		return left <= inv.waveSize * WAVE_CLEAR;
	}

	/** Defenders come in with the line, and are told when they are leaving it. */
	private static void watchLine(Invasion inv, long now) {
		Vec3 c = Vec3.atBottomCenterOf(inv.seat);
		boolean standing = false;
		for (ServerPlayer player : inv.level.players()) {
			if (player.isSpectator() || !player.isAlive()) {
				continue;
			}
			double dx = player.getX() - c.x, dz = player.getZ() - c.z;
			boolean inside = dx * dx + dz * dz <= BOUNDARY * BOUNDARY;
			Defender defender = inv.defenders.get(player.getUUID());
			if (inside) {
				if (defender == null) {
					defender = new Defender(player.getName());
					inv.defenders.put(player.getUUID(), defender);
					player.sendSystemMessage(Component.translatable("message.jaymospowers.zfighter_invasion_joined", inv.kingdom).withStyle(ChatFormatting.GOLD));
					for (Component line : rewards(inv)) {
						player.sendSystemMessage(line);
					}
				}
				defender.outside = 0;
				if (!defender.forfeited && !Downed.isDowned(player)) {
					standing = true;
				}
			} else if (defender != null && !defender.forfeited) {
				defender.outside += 20;
				if (defender.outside >= LEAVE_GRACE) {
					defender.forfeited = true;
					player.sendSystemMessage(Component.translatable("message.jaymospowers.zfighter_invasion_forfeit").withStyle(ChatFormatting.RED));
				} else {
					player.sendOverlayMessage(Component.translatable("message.jaymospowers.zfighter_invasion_leaving", (LEAVE_GRACE - defender.outside) / 20 + 1)
						.withStyle(ChatFormatting.RED));
				}
			}
		}
		// one who logged out is outside the line too
		for (Map.Entry<UUID, Defender> entry : inv.defenders.entrySet()) {
			if (inv.level.getServer().getPlayerList().getPlayer(entry.getKey()) == null && !entry.getValue().forfeited) {
				entry.getValue().outside += 20;
				if (entry.getValue().outside >= LEAVE_GRACE) {
					entry.getValue().forfeited = true;
				}
			}
		}
		if (standing) {
			inv.lastDefenderAt = now;
		}
	}

	/** The line, drawn in motes for each player near it (only he sees his stretch of it). */
	private static void wall(Invasion inv) {
		Vec3 c = Vec3.atBottomCenterOf(inv.seat);
		DustParticleOptions dust = new DustParticleOptions(inv.theme.dust, 2.0F);
		for (ServerPlayer player : inv.level.players()) {
			double dx = player.getX() - c.x, dz = player.getZ() - c.z;
			double distance = Math.sqrt(dx * dx + dz * dz);
			if (Math.abs(distance - BOUNDARY) > WALL_SEEN) {
				continue;
			}
			double centre = Math.atan2(dz, dx);
			double half = WALL_SEEN / BOUNDARY;
			int steps = (int)(half * 2.0 * BOUNDARY / WALL_STEP);
			for (int i = 0; i <= steps; i++) {
				double angle = centre - half + i * (half * 2.0 / steps);
				double x = c.x + Math.cos(angle) * BOUNDARY, z = c.z + Math.sin(angle) * BOUNDARY;
				double ground = inv.level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int)Math.floor(x), (int)Math.floor(z));
				double y = Math.abs(ground - player.getY()) < 24.0 ? ground : player.getY();
				for (int h = 0; h < WALL_HEIGHT; h++) {
					inv.level.sendParticles(player, dust, true, false, x, y + 0.3 + h, z, 1, 0.0, 0.0, 0.0, 0.0);
				}
			}
		}
	}

	/** The bar: soldiers beaten out of all there are and will be, and the wave. */
	private static void bar(Invasion inv) {
		int alive = 0;
		for (UUID id : inv.invaders) {
			if (!id.equals(inv.boss)) {
				alive++;
			}
		}
		int ahead = 0;
		for (int w = Math.max(0, inv.wave + 1); w < WAVES; w++) {
			ahead += size(inv.theme.waves[w], Math.max(1, inv.defenders.size()));
		}
		if (inv.wave < WAVES) {
			ahead += size(new Squad[] {inv.theme.escort()}, Math.max(1, inv.defenders.size())) + 1;
		}
		int total = inv.defeated + alive + ahead + (inv.boss != null ? 1 : 0);
		float progress = total <= 0 ? 0.0F : (inv.defeated + (inv.boss != null && !inv.invaders.contains(inv.boss) ? 1 : 0)) / (float)total;
		inv.bar.setProgress(Math.max(0.0F, Math.min(1.0F, progress)));
		inv.bar.setName(title(inv.theme, inv.kingdom, inv.wave));
		Vec3 c = Vec3.atBottomCenterOf(inv.seat);
		for (ServerPlayer player : inv.level.players()) {
			boolean near = player.distanceToSqr(c) <= (BOUNDARY + 48.0) * (BOUNDARY + 48.0);
			if (near && !inv.bar.getPlayers().contains(player)) {
				inv.bar.addPlayer(player);
			} else if (!near && inv.bar.getPlayers().contains(player)) {
				inv.bar.removePlayer(player);
			}
		}
	}

	/** Soldiers with no quarry take the nearest defender; with none in sight they march on the seat. */
	private static void target(Invasion inv) {
		Vec3 c = Vec3.atBottomCenterOf(inv.seat);
		AABB box = new AABB(c, c).inflate(BOUNDARY, 32.0, BOUNDARY);
		inv.targets = inv.level.getEntitiesOfClass(LivingEntity.class, box, e -> e.isAlive() && !e.isSpectator()
			&& (e instanceof Player p && !p.isCreative() || e instanceof AbstractVillager || e instanceof IronGolem));
		for (UUID id : inv.invaders) {
			if (!(inv.level.getEntity(id) instanceof Mob mob) || mob instanceof ZFighterRogue) {
				continue;
			}
			LivingEntity target = mob.getTarget();
			if (target != null && target.isAlive()) {
				continue;
			}
			LivingEntity nearest = null;
			double best = Double.MAX_VALUE;
			for (LivingEntity candidate : inv.targets) {
				double d = mob.distanceToSqr(candidate);
				if (d < best) {
					best = d;
					nearest = candidate;
				}
			}
			if (nearest != null) {
				mob.setTarget(nearest);
			} else if (mob instanceof Vex) {
				mob.getMoveControl().setWantedPosition(c.x, c.y + 6.0, c.z, 1.0);
			} else {
				mob.getNavigation().moveTo(c.x, c.y, c.z, 1.0);
			}
		}
	}

	// ---------------------------------------------------------------- the waves

	private static int size(Squad[] squads, int defenders) {
		int n = 0;
		for (Squad squad : squads) {
			n += squadSize(squad, defenders);
		}
		return n;
	}

	private static int squadSize(Squad squad, int defenders) {
		return Math.min(MAX_SQUAD, (int)Math.round(squad.count() * (SOLO_SCALE + PER_DEFENDER * Math.max(1, defenders))));
	}

	private static void spawnWave(Invasion inv, long now) {
		inv.wave++;
		inv.waveAt = now;
		inv.nextAt = now + WAVE_BREAK;
		int defenders = 0;
		for (Defender d : inv.defenders.values()) {
			if (!d.forfeited) {
				defenders++;
			}
		}
		defenders = Math.max(1, defenders);
		int spawned = 0;
		if (inv.wave < WAVES) {
			for (Squad squad : inv.theme.waves[inv.wave]) {
				spawned += spawn(inv, squad, squadSize(squad, defenders));
			}
			inv.waveSize = spawned;
			say(inv, Component.translatable("message.jaymospowers.zfighter_invasion_wave", inv.wave + 1, WAVES + 1,
				Component.translatable("invasion.jaymospowers.zfighter." + inv.theme.key)), ChatFormatting.RED);
		} else {
			spawnBoss(inv);
			spawn(inv, inv.theme.escort(), squadSize(inv.theme.escort(), defenders));
			inv.waveSize = 0;
		}
		inv.level.playSound(null, inv.seat, SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 16.0F, 1.0F);
	}

	/** {@code count} of a squad, scattered along the line's edge. Returns how many came. */
	private static int spawn(Invasion inv, Squad squad, int count) {
		int made = 0;
		for (int i = 0; i < count; i++) {
			Mob mob = squad.type().create(inv.level, EntitySpawnReason.EVENT);
			if (mob == null) {
				continue;
			}
			Vec3 at = landing(inv);
			mob.snapTo(at.x, at.y, at.z, inv.level.getRandom().nextFloat() * 360.0F, 0.0F);
			equip(mob, squad);
			inv.invaders.add(mob.getUUID());
			inv.level.addFreshEntity(mob);
			inv.level.sendParticles(ParticleTypes.CLOUD, at.x, at.y + 1.0, at.z, 6, 0.3, 0.5, 0.3, 0.03);
			made++;
		}
		return made;
	}

	private static void spawnBoss(Invasion inv) {
		ZFighterRogue tyrant = ZFighterRaids.ROGUE.create(inv.level, EntitySpawnReason.EVENT);
		if (tyrant == null) {
			return;
		}
		RandomSource random = inv.level.getRandom();
		Vec3 at = landing(inv);
		tyrant.snapTo(at.x, at.y, at.z, random.nextFloat() * 360.0F, 0.0F);
		tyrant.become(ZFighterRogue.TYRANT, Component.translatable("entity.jaymospowers.rogue_fighter.tyrant." + ZFighterRaids.TYRANTS[random.nextInt(ZFighterRaids.TYRANTS.length)]));
		tyrant.kingdom = inv.kingdom;
		tyrant.getAttribute(Attributes.SCALE).setBaseValue(inv.theme.bossScale);
		tyrant.addTag(TAG);
		inv.boss = tyrant.getUUID();
		inv.invaders.add(tyrant.getUUID());
		inv.level.addFreshEntity(tyrant);
		LightningBolt bolt = EntityTypes.LIGHTNING_BOLT.create(inv.level, EntitySpawnReason.TRIGGERED);
		if (bolt != null) {
			bolt.snapTo(at.x, at.y, at.z);
			bolt.setVisualOnly(true);
			inv.level.addFreshEntity(bolt);
		}
		inv.level.playSound(null, BlockPos.containing(at), SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 6.0F, 1.0F);
		say(inv, Component.translatable("message.jaymospowers.zfighter_invasion_boss", tyrant.getDisplayName()), ChatFormatting.DARK_RED);
	}

	/** A spot on the ground a good way in from the line. */
	private static Vec3 landing(Invasion inv) {
		RandomSource random = inv.level.getRandom();
		Vec3 c = Vec3.atBottomCenterOf(inv.seat);
		for (double share : new double[] {0.8, 0.65, 0.5, 0.3}) {
			double angle = random.nextDouble() * Math.PI * 2.0;
			int x = (int)Math.floor(c.x + Math.cos(angle) * BOUNDARY * share), z = (int)Math.floor(c.z + Math.sin(angle) * BOUNDARY * share);
			if (inv.level.hasChunkAt(new BlockPos(x, inv.seat.getY(), z))) {
				return new Vec3(x + 0.5, inv.level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z + 0.5);
			}
		}
		return c.add(0.0, 1.0, 0.0);
	}

	/** The soldier's gear, size and strength; it stays until it is beaten and drops no gear. */
	private static void equip(Mob mob, Squad squad) {
		Item[][] sets = {
			{Items.LEATHER_HELMET, Items.LEATHER_CHESTPLATE, Items.LEATHER_LEGGINGS, Items.LEATHER_BOOTS},
			{Items.CHAINMAIL_HELMET, Items.CHAINMAIL_CHESTPLATE, Items.CHAINMAIL_LEGGINGS, Items.CHAINMAIL_BOOTS},
			{Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS},
		};
		EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
		if (squad.armour() > 0) {
			// 1 leather, 2 chainmail, 3 iron
			Item[] set = sets[squad.armour() - 1];
			for (int i = 0; i < slots.length; i++) {
				mob.setItemSlot(slots[i], new ItemStack(set[i]));
				mob.setDropChance(slots[i], 0.0F);
			}
		}
		if (squad.weapon() != null) {
			mob.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(squad.weapon()));
			mob.setDropChance(EquipmentSlot.MAINHAND, 0.0F);
		}
		if (squad.scale() != 1.0) {
			mob.getAttribute(Attributes.SCALE).setBaseValue(squad.scale());
		}
		if (squad.strength() > 0) {
			mob.addEffect(new MobEffectInstance(MobEffects.STRENGTH, MobEffectInstance.INFINITE_DURATION, squad.strength() - 1, false, false));
		}
		if (squad.speed() > 0) {
			mob.addEffect(new MobEffectInstance(MobEffects.SPEED, MobEffectInstance.INFINITE_DURATION, squad.speed() - 1, false, false));
		}
		// the dawn doesn't burn them
		mob.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, MobEffectInstance.INFINITE_DURATION, 0, false, false));
		mob.addTag(TAG);
		mob.setPersistenceRequired();
	}

	// ---------------------------------------------------------------- the end

	/** The tyrant fell (called from {@link ZFighterRaids#defeated}). True if he was the army's, so the invasion pays out. */
	static boolean tyrantFell(ServerLevel level, ZFighterRogue tyrant) {
		Invasion inv = current;
		if (inv == null || !tyrant.getUUID().equals(inv.boss)) {
			return false;
		}
		win(inv, tyrant);
		return true;
	}

	private static void win(Invasion inv) {
		win(inv, inv.boss != null && inv.level.getEntity(inv.boss) instanceof ZFighterRogue boss ? boss : null);
	}

	/** The army is beaten: what is left of it flees, and the defenders who held the line are paid. */
	private static void win(Invasion inv, @Nullable ZFighterRogue tyrant) {
		current = null;
		ServerLevel level = inv.level;
		disband(inv);
		float total = 0.0F;
		int sharing = 0;
		for (Defender d : inv.defenders.values()) {
			if (!d.forfeited && d.damage >= MIN_DAMAGE) {
				total += d.damage;
				sharing++;
			}
		}
		int paid = 0;
		for (Map.Entry<UUID, Defender> entry : inv.defenders.entrySet()) {
			ServerPlayer player = level.getServer().getPlayerList().getPlayer(entry.getKey());
			Defender d = entry.getValue();
			if (player == null) {
				continue;
			}
			if (d.forfeited) {
				player.sendSystemMessage(Component.translatable("message.jaymospowers.zfighter_invasion_forfeited_no_share").withStyle(ChatFormatting.RED));
				continue;
			}
			// nobody did a share of damage (the town's folk did it all): an even share for those who stayed
			float share = total > 0.0F ? (d.damage >= MIN_DAMAGE ? d.damage / total : 0.0F) : 1.0F / Math.max(1, inv.defenders.size());
			if (share <= 0.0F) {
				player.sendSystemMessage(Component.translatable("message.jaymospowers.zfighter_invasion_no_share").withStyle(ChatFormatting.GRAY));
				continue;
			}
			paid++;
			int emeralds = Math.max(MIN_EMERALDS, Math.round(TOTAL_EMERALDS * share));
			give(player, new ItemStack(Items.EMERALD, emeralds));
			give(player, new ItemStack(inv.theme.item, inv.theme.itemCount));
			if (ZFighterItems.HERMIT_BEAN != null) {
				give(player, new ItemStack(ZFighterItems.HERMIT_BEAN, BEANS));
			}
			if (Powers.has(player, ZFighterPower.INSTANCE)) {
				int tp = Math.max(MIN_TP, Math.round(TOTAL_TP * share));
				ZFighterTraining.grant(player, tp);
				player.sendSystemMessage(Component.translatable("message.jaymospowers.zfighter_invasion_paid_tp", tp, emeralds, inv.theme.itemCount,
					new ItemStack(inv.theme.item).getHoverName(), BEANS).withStyle(ChatFormatting.GOLD));
			} else {
				int levels = Math.max(MIN_XP, Math.round(TOTAL_XP * share));
				player.giveExperienceLevels(levels);
				player.sendSystemMessage(Component.translatable("message.jaymospowers.zfighter_invasion_paid", levels, emeralds, inv.theme.itemCount,
					new ItemStack(inv.theme.item).getHoverName(), BEANS).withStyle(ChatFormatting.GOLD));
			}
		}
		// the tyrant's rare pair of bonding earrings
		if (tyrant != null) {
			ZFighterFusionItems.tyrantDrop(level, tyrant);
		}
		level.getServer().getPlayerList().broadcastSystemMessage(Component.translatable("message.jaymospowers.zfighter_invasion_won",
			Component.translatable("invasion.jaymospowers.zfighter." + inv.theme.key), inv.kingdom, paid).withStyle(ChatFormatting.GOLD), false);
		level.playSound(null, inv.seat, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.NEUTRAL, 4.0F, 1.0F);
	}

	private static void give(ServerPlayer player, ItemStack stack) {
		if (!player.getInventory().add(stack)) {
			player.drop(stack, false, net.minecraft.util.Prediction.SERVER_ONLY);
		}
	}

	/** Whether the kingdom has been beaten: time out, its people cut down, or no defender standing for too long. */
	private static boolean fell(Invasion inv, long now) {
		if (now >= inv.endAt) {
			return true;
		}
		if (inv.wave >= 0 && now - inv.lastDefenderAt >= GONE_TICKS) {
			return true;
		}
		if (inv.town != null && inv.people >= MIN_PEOPLE && now % 100 == 0) {
			return ZFighterKingdoms.people(inv.level, inv.town).size() <= inv.people * FALL_PEOPLE;
		}
		return false;
	}

	/** The army has beaten the defenders: it goes home with its prize, and every defender loses standing with the realm. */
	private static void fall(Invasion inv) {
		current = null;
		disband(inv);
		ServerLevel level = inv.level;
		level.getServer().getPlayerList().broadcastSystemMessage(Component.translatable("message.jaymospowers.zfighter_invasion_fell",
			inv.kingdom, Component.translatable("invasion.jaymospowers.zfighter." + inv.theme.key)).withStyle(ChatFormatting.DARK_RED), false);
		level.playSound(null, inv.seat, SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 8.0F, 0.6F);
		if (inv.town == null) {
			return;
		}
		for (UUID id : inv.defenders.keySet()) {
			ServerPlayer player = level.getServer().getPlayerList().getPlayer(id);
			if (player != null && ZFighterKingdoms.changeReputation(player, inv.town, -STANDING_LOSS)) {
				player.sendSystemMessage(Component.translatable("message.jaymospowers.zfighter_invasion_standing", inv.kingdom).withStyle(ChatFormatting.RED));
			}
		}
	}

	/** The army leaves: its bar goes, its soldiers vanish in smoke. */
	private static void disband(Invasion inv) {
		inv.bar.removeAllPlayers();
		for (UUID id : new ArrayList<>(inv.invaders)) {
			Entity entity = inv.level.getEntity(id);
			if (entity != null && entity.isAlive()) {
				inv.level.sendParticles(ParticleTypes.POOF, entity.getX(), entity.getY() + 1.0, entity.getZ(), 8, 0.3, 0.5, 0.3, 0.05);
				entity.discard();
			}
		}
		inv.invaders.clear();
	}

	private static void say(Invasion inv, Component message, ChatFormatting style) {
		Vec3 c = Vec3.atBottomCenterOf(inv.seat);
		for (ServerPlayer player : inv.level.players()) {
			if (player.distanceToSqr(c) <= (BOUNDARY + 96.0) * (BOUNDARY + 96.0)) {
				player.sendSystemMessage(message.copy().withStyle(style));
			}
		}
	}
}
