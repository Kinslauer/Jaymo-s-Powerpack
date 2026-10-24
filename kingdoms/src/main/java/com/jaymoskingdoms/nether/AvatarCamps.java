package com.jaymoskingdoms.nether;

import com.jaymoskingdoms.Heritage;
import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.McaCompat;
import com.jaymoskingdoms.VillagerHeritage;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.monster.piglin.AbstractPiglin;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (Kingdoms 0.7.187) The Nether's third side: camps of <b>Flame Avatars</b>, Emberborn the fire consumed (Jaymo's Powers'
 * {@code FlameAvatar} is a player who went the same way). Andy: "there should be mca avatar in the nether seen fighting
 * the other two factions. They will not speak to the player unless they are emberborn, and will not be hostile unless
 * you are near one of their encampments."
 * <ul>
 * <li>Near players in the Nether, now and then an avatar camp rises out of sight ({@link FortBuildings#avatarCamp}: a
 * scorched ring of fire pillars round a hearth), kept apart from the Hornkin and piglin war forts.</li>
 * <li>Its people are MCA villagers (guards, in MCA's guard brain), of Emberborn blood (fire never hurts them), marked
 * {@link #AVATAR} (synced: everyone sees them burn, the char and the lava veins), tougher than a guard.</li>
 * <li>They go for Hornkin and piglins on sight ({@link #prey}, through {@code McaCompat.foe}), and every few minutes a
 * war band of them walks out at the nearest standing fort, so they're seen fighting both.</li>
 * <li>Players: left alone, except a stranger (anyone not Emberborn) near one of their camps
 * ({@link #GUARD_RANGE}). They won't talk to anyone not Emberborn ({@link #use}).</li>
 * </ul>
 * Each camp takes in a new avatar now and then while a player is near, up to {@link #MOST}. Without MCA, the camps
 * rise empty. Ops: {@code /kingdom avatarcamp found | info}.
 */
public final class AvatarCamps {
	// ---- tuning ----
	/** How often a camp may rise near each player in the Nether (ticks), and the chance it does. */
	static final int FOUND_EVERY = 20 * 60 * 3;
	static final float FOUND_CHANCE = 0.25F;
	/** No new camp while one is this near the player; camps keep this far apart (blocks). */
	static final double CAMP_AREA = 256.0, CAMP_SPACING = 200.0;
	/** A camp rises this far from the player. */
	static final int SITE_MIN = 64, SITE_MAX = 128, SITE_TRIES = 10;
	/** A new camp's avatars, and the most it keeps; it takes one in this often (ticks) while a player is within {@link #ACTIVE_RANGE}. */
	static final int FIRST = 4, MOST = 6, RECRUIT_EVERY = 20 * 60 * 4;
	static final double ACTIVE_RANGE = 160.0;
	/** A war band: this often (ticks), this many, at the nearest standing fort within this far. */
	static final int BAND_EVERY = 20 * 60 * 4, BAND_SIZE = 3;
	static final double BAND_REACH = 320.0;
	/** A stranger this near a camp is fair game (blocks). */
	public static final double GUARD_RANGE = 24.0;
	/** At home they keep within this of the hearth. */
	static final int HOME_RADIUS = 12;
	/** An avatar's health (MCA's guards are frail for the Nether). */
	static final double HEALTH = 40.0;

	/** A camp: its hearth (floor), name, when it last took one in and sent a band. */
	public record Camp(int id, BlockPos center, String name, long lastRecruit, long lastBand) {
		public static final Codec<Camp> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.INT.fieldOf("id").forGetter(Camp::id),
				BlockPos.CODEC.fieldOf("center").forGetter(Camp::center),
				Codec.STRING.fieldOf("name").forGetter(Camp::name),
				Codec.LONG.fieldOf("last_recruit").forGetter(Camp::lastRecruit),
				Codec.LONG.fieldOf("last_band").forGetter(Camp::lastBand))
			.apply(i, Camp::new));

		Camp withRecruit(long t) {
			return new Camp(id, center, name, t, lastBand);
		}

		Camp withBand(long t) {
			return new Camp(id, center, name, lastRecruit, t);
		}
	}

	/** Every camp (on the Nether). */
	public static final AttachmentType<List<Camp>> CAMPS = AttachmentRegistry.create(JaymosKingdoms.id("avatar_camps"),
		builder -> builder.persistent(Camp.CODEC.listOf()));
	/** On every avatar: its camp's id. Saved, seen by all (the look). */
	public static final AttachmentType<Integer> AVATAR = AttachmentRegistry.create(JaymosKingdoms.id("flame_avatar"),
		builder -> builder.persistent(Codec.INT).syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.all()));

	private static final String[] FIRST_NAME = {"Cinder", "Pyre", "Ash", "Ember", "Scorch", "Brand", "Kindle", "Char", "Blaze", "Smoulder"};
	private static final String[] SECOND_NAME = {"ring", "hearth", "circle", "pyre", "rest", "brand", "throne", "watch"};
	/** Avatars loaded now. */
	private static final Set<Mob> LOADED = new HashSet<>();
	private static final net.minecraft.resources.Identifier TOUGH = JaymosKingdoms.id("flame_avatar_health");

	private AvatarCamps() {
	}

	static void register() {
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof Mob mob && avatar(mob)) {
				LOADED.add(mob);
			}
		});
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> LOADED.remove(entity));
		ServerTickEvents.END_SERVER_TICK.register(AvatarCamps::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> LOADED.clear());
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> use(player, entity));
		CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) -> dispatcher.register(Commands.literal("kingdom")
			.then(Commands.literal("avatarcamp").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(Commands.literal("found").executes(AvatarCamps::foundHere))
				.then(Commands.literal("info").executes(AvatarCamps::info)))));
	}

	// ---- who's who -----------------------------------------------------------------------------------------------------

	/** One of the avatar camps' people (either side). */
	public static boolean avatar(Entity entity) {
		return entity.hasAttached(AVATAR);
	}

	/** {@code target} is what {@code avatar} goes for: Hornkin, piglins, and anyone not Emberborn near its camp. */
	public static boolean prey(LivingEntity avatar, LivingEntity target) {
		if (target instanceof Hornkin || target instanceof AbstractPiglin) {
			return true;
		}
		if (!(target instanceof Player player) || player.isCreative() || player.isSpectator() || Heritage.is(player, Heritage.Kind.EMBERBORN)
			|| !(avatar.level() instanceof ServerLevel level)) {
			return false;
		}
		Integer id = avatar.getAttached(AVATAR);
		Camp camp = id == null ? null : camp(level, id);
		return camp != null && player.blockPosition().closerThan(camp.center(), GUARD_RANGE);
	}

	/** Anyone not Emberborn gets nothing out of an avatar: no talk, no menu. */
	private static InteractionResult use(Player player, Entity entity) {
		if (!avatar(entity) || player.isSpectator() || Heritage.is(player, Heritage.Kind.EMBERBORN)) {
			return InteractionResult.PASS;
		}
		if (!player.level().isClientSide()) {
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.avatar.silent").withStyle(ChatFormatting.GOLD, ChatFormatting.ITALIC));
		}
		return InteractionResult.FAIL;
	}

	static List<Camp> camps(ServerLevel nether) {
		List<Camp> all = nether.getAttached(CAMPS);
		return all == null ? List.of() : all;
	}

	static @Nullable Camp camp(ServerLevel nether, int id) {
		for (Camp c : camps(nether)) {
			if (c.id() == id) {
				return c;
			}
		}
		return null;
	}

	private static void put(ServerLevel nether, Camp camp) {
		List<Camp> all = new ArrayList<>(camps(nether));
		all.removeIf(c -> c.id() == camp.id());
		all.add(camp);
		nether.setAttached(CAMPS, List.copyOf(all));
	}

	// ---- each tick -----------------------------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		ServerLevel nether = server.getLevel(Level.NETHER);
		if (nether == null) {
			return;
		}
		long now = nether.getGameTime();
		if (now % 40 == 0) {
			for (Mob mob : List.copyOf(LOADED)) {
				if (mob.isAlive() && mob.level() == nether) {
					keep(nether, mob);
				}
			}
		}
		if (now % 200 == 0) {
			for (Camp camp : camps(nether)) {
				tend(nether, camp, now);
			}
		}
		if (now % FOUND_EVERY == 0) {
			for (ServerPlayer player : nether.players()) {
				if (!player.isSpectator() && nether.getRandom().nextFloat() < FOUND_CHANCE) {
					tryFound(nether, player);
				}
			}
		}
	}

	/** An avatar at home that's strayed, with nothing to fight, walks back to its hearth. */
	private static void keep(ServerLevel nether, Mob mob) {
		if (mob.getTarget() != null) {
			return;
		}
		Integer id = mob.getAttached(AVATAR);
		Camp camp = id == null ? null : camp(nether, id);
		if (camp == null || mob.entityTags().contains(BAND_TAG)) {
			return;
		}
		walk(mob, camp.center(), HOME_RADIUS);
	}

	/** Walks {@code mob} towards {@code goal} in strides (MCA's brain takes a walk target as a piglin's does), unless within {@code within}. */
	private static void walk(Mob mob, BlockPos goal, int within) {
		Vec3 to = Vec3.atBottomCenterOf(goal).subtract(mob.position());
		if (to.horizontalDistance() <= within) {
			return;
		}
		Vec3 step = to.horizontalDistance() > Hornkin.MARCH_STRIDE ? mob.position().add(to.normalize().scale(Hornkin.MARCH_STRIDE)) : Vec3.atBottomCenterOf(goal);
		mob.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(step, 0.8F, 2));
	}

	/** Marks a war band's avatar (it walks at a fort, not home). */
	static final String BAND_TAG = "jaymoskingdoms_avatar_band";

	/** Every 10 s for each camp: recruits and war bands while a player is near; a band on the march walks on. */
	private static void tend(ServerLevel nether, Camp camp, long now) {
		boolean near = false;
		for (ServerPlayer p : nether.players()) {
			if (!p.isSpectator() && p.blockPosition().closerThan(camp.center(), ACTIVE_RANGE)) {
				near = true;
				break;
			}
		}
		List<Mob> people = members(camp.id());
		NetherForts.Fort target = nearestFort(nether, camp.center());
		for (Mob m : people) {
			if (m.entityTags().contains(BAND_TAG) && m.getTarget() == null) {
				if (target == null || m.blockPosition().closerThan(target.center(), NetherForts.HOLD_RADIUS)) {
					// the fort is down or reached: home again
					m.removeTag(BAND_TAG);
				} else {
					walk(m, target.center(), NetherForts.HOLD_RADIUS);
				}
			}
		}
		if (!near) {
			return;
		}
		if (people.size() < MOST && now - camp.lastRecruit() >= RECRUIT_EVERY) {
			spawnAvatar(nether, camp, camp.center().above().offset(nether.getRandom().nextInt(7) - 3, 0, nether.getRandom().nextInt(7) - 3));
			camp = camp.withRecruit(now);
			put(nether, camp);
		}
		if (target != null && now - camp.lastBand() >= BAND_EVERY) {
			int sent = 0;
			for (Mob m : people) {
				if (sent >= BAND_SIZE || m.entityTags().contains(BAND_TAG) || m.getTarget() != null) {
					continue;
				}
				m.addTag(BAND_TAG);
				walk(m, target.center(), NetherForts.HOLD_RADIUS);
				sent++;
			}
			if (sent > 0) {
				put(nether, camp.withBand(now));
				nether.sendParticles(ParticleTypes.FLAME, camp.center().getX() + 0.5, camp.center().getY() + 3.0, camp.center().getZ() + 0.5, 40, 1.0, 1.5, 1.0, 0.05);
			}
		}
	}

	private static List<Mob> members(int camp) {
		List<Mob> out = new ArrayList<>();
		for (Mob m : LOADED) {
			Integer id = m.getAttached(AVATAR);
			if (m.isAlive() && id != null && id == camp) {
				out.add(m);
			}
		}
		return out;
	}

	/** The nearest standing Hornkin fort or piglin war camp within {@link #BAND_REACH}, or null. */
	private static NetherForts.@Nullable Fort nearestFort(ServerLevel nether, BlockPos from) {
		NetherForts.Fort best = null;
		double bestD = BAND_REACH * BAND_REACH;
		for (NetherForts.Fort f : NetherForts.standing(nether)) {
			double d = f.center().distSqr(from);
			if (d < bestD) {
				bestD = d;
				best = f;
			}
		}
		return best;
	}

	// ---- founding ------------------------------------------------------------------------------------------------------

	private static void tryFound(ServerLevel nether, ServerPlayer player) {
		for (Camp c : camps(nether)) {
			if (c.center().closerThan(player.blockPosition(), CAMP_AREA)) {
				return;
			}
		}
		RandomSource random = nether.getRandom();
		for (int i = 0; i < SITE_TRIES; i++) {
			double angle = random.nextDouble() * Math.PI * 2.0;
			int dist = SITE_MIN + random.nextInt(SITE_MAX - SITE_MIN);
			BlockPos at = player.blockPosition().offset((int)(Math.cos(angle) * dist), 0, (int)(Math.sin(angle) * dist));
			if (found(nether, at, player.getBlockY(), false) != null) {
				return;
			}
		}
	}

	/** A camp near {@code at}, built and peopled; null if there's no fit ground. {@code anywhere}: an op's, not hidden. */
	static @Nullable Camp found(ServerLevel nether, BlockPos at, int nearY, boolean anywhere) {
		BlockPos site = NetherForts.nearSite(nether, at.getX(), at.getZ(), nearY, FortBuildings.AVATAR_RADIUS, anywhere, false);
		if (site == null) {
			return null;
		}
		for (Camp c : camps(nether)) {
			if (c.center().closerThan(site, CAMP_SPACING)) {
				return null;
			}
		}
		RandomSource random = nether.getRandom();
		int id = camps(nether).stream().mapToInt(Camp::id).max().orElse(0) + 1;
		Camp camp = new Camp(id, site, FIRST_NAME[random.nextInt(FIRST_NAME.length)] + SECOND_NAME[random.nextInt(SECOND_NAME.length)],
			nether.getGameTime(), nether.getGameTime());
		put(nether, camp);
		FortBuildings.avatarCamp(nether, site, Direction.from2DDataValue(random.nextInt(4)), random);
		for (int i = 0; i < FIRST; i++) {
			spawnAvatar(nether, camp, site.above().offset(random.nextInt(7) - 3, 0, random.nextInt(7) - 3));
		}
		JaymosKingdoms.LOGGER.info("A Flame Avatars' camp, {}, rose at {}", camp.name(), site);
		return camp;
	}

	/** One avatar at {@code at}: an MCA guard of Emberborn blood, marked, tougher, armed (null without MCA). */
	static @Nullable Mob spawnAvatar(ServerLevel nether, Camp camp, BlockPos at) {
		Villager villager = McaCompat.spawnGuard(nether, Vec3.atBottomCenterOf(at));
		if (villager == null) {
			return null;
		}
		VillagerHeritage.born(villager, Heritage.Kind.EMBERBORN, null);
		villager.setAttached(AVATAR, camp.id());
		AttributeInstance health = villager.getAttribute(Attributes.MAX_HEALTH);
		if (health != null && HEALTH > health.getBaseValue()) {
			health.addOrReplacePermanentModifier(new AttributeModifier(TOUGH, HEALTH - health.getBaseValue(), AttributeModifier.Operation.ADD_VALUE));
			villager.setHealth(villager.getMaxHealth());
		}
		RandomSource random = nether.getRandom();
		Item[] weapons = {Items.GOLDEN_AXE, Items.IRON_AXE, Items.IRON_SWORD, Items.GOLDEN_SWORD};
		ItemStack weapon = new ItemStack(weapons[random.nextInt(weapons.length)]);
		villager.setItemSlot(EquipmentSlot.MAINHAND, weapon.copy());
		villager.getInventory().addItem(weapon);
		villager.setDropChance(EquipmentSlot.MAINHAND, 0.0F);
		villager.setHomeTo(camp.center(), HOME_RADIUS);
		villager.setPersistenceRequired();
		LOADED.add(villager);
		nether.sendParticles(ParticleTypes.FLAME, villager.getX(), villager.getY() + 1.0, villager.getZ(), 30, 0.3, 0.8, 0.3, 0.05);
		return villager;
	}

	// ---- commands (operators; debug: plain text) -----------------------------------------------------------------------

	private static int foundHere(CommandContext<CommandSourceStack> c) {
		ServerLevel level = c.getSource().getLevel();
		if (level.dimension() != Level.NETHER) {
			c.getSource().sendFailure(Component.literal("Avatar camps rise in the Nether."));
			return 0;
		}
		BlockPos at = BlockPos.containing(c.getSource().getPosition().add(c.getSource().getRotation().y == 0 ? Vec3.ZERO
			: Vec3.directionFromRotation(0.0F, c.getSource().getRotation().y).scale(24.0)));
		Camp camp = found(level, at, at.getY(), true);
		if (camp == null) {
			c.getSource().sendFailure(Component.literal("No fit ground near here for an avatar camp."));
			return 0;
		}
		c.getSource().sendSuccess(() -> Component.literal("Avatar camp " + camp.name() + " at " + camp.center().toShortString()), true);
		return 1;
	}

	private static int info(CommandContext<CommandSourceStack> c) {
		ServerLevel nether = c.getSource().getServer().getLevel(Level.NETHER);
		if (nether == null) {
			return 0;
		}
		for (Camp camp : camps(nether)) {
			int people = members(camp.id()).size();
			c.getSource().sendSuccess(() -> Component.literal(camp.name() + " #" + camp.id() + " at " + camp.center().toShortString() + ": " + people
				+ " loaded"), false);
		}
		return camps(nether).size();
	}
}
