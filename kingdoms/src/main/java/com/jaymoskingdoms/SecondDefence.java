package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.ZombifiedPiglin;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The Second Defence: the Retaking's last stand ({@link Retaking}, Act V). The gate-warden's page asks whoever keeps the
 * crossing to keep a light in the keep at night ({@code the_wardens_page}); the dead of the old legion answer it.
 * <ul>
 * <li><b>When:</b> at night, with a light burning in the keep ({@link #lit}), and a player whose Act V has begun at the ford.
 * It comes once in a world: held, it is over ({@link #HELD}).</li>
 * <li><b>The legion:</b> as in <i>The Long Defence</i>, the dead come up a few at a time, here and there across the water,
 * each group under a stroke of lightning: risen orcs ({@link RisenOrc}) and zombified piglins, never zombies or skeletons.
 * {@link #WAVES} waves, each larger, more with more defenders; then the one at the back, a shade in the sorcerer's colour
 * standing beyond the ford ({@link #shade}). When it falls, what is left of the legion goes back into the ground.</li>
 * <li><b>Held:</b> everyone who stood at the ford through it gets the milestone (if their Act V has begun).</li>
 * <li><b>Lost:</b> nobody standing near the ford for {@link #GIVE_UP} ticks: the legion sinks back and comes again another
 * night.</li>
 * </ul>
 * The fight isn't saved: a server stopping mid-fight leaves it to come again (leftover dead are cleared at the ford).
 */
public final class SecondDefence {
	/** How often (ticks) the ford is looked at when nothing is happening, and how often a running defence thinks. */
	static final int CHECK_TICKS = 40, FIGHT_TICKS = 10;
	/** How near (blocks) the ford a player must be to start it, to count as a defender, and to keep it going. */
	static final double START_NEAR = 24.0, DEFENDER_NEAR = 48.0, KEEP_NEAR = 96.0;
	/** How near the keep's gate (blocks, any direction) a burning light counts as kept in the keep. */
	static final int LIGHT_REACH = 9, LIGHT_BELOW = 2, LIGHT_ABOVE = 12;
	/** The waves, and the dead in the first (each later wave brings {@link #WAVE_GROWTH} more). */
	static final int WAVES = 4, FIRST_WAVE = 8, WAVE_GROWTH = 4;
	/** Extra dead per defender beyond the first (fraction of a wave). */
	static final double PER_DEFENDER = 0.4;
	/** A group rises every this many ticks, of this many; never more than {@link #MOST_STANDING} up at once. */
	static final int GROUP_EVERY = 50, GROUP_LEAST = 2, GROUP_MOST = 4, MOST_STANDING = 24;
	/** Where they rise: this far past the ford, away from the keep, and this far to either side. */
	static final int RISE_NEAR = 10, RISE_FAR = 26, RISE_WIDE = 14;
	/** The share of risen orcs (the rest are zombified piglins). */
	static final float ORC_SHARE = 0.6F;
	/** The shade at the back: its health, and how far past the ford it stands. */
	static final double SHADE_HEALTH = 180.0;
	static final int SHADE_BACK = 22;
	/** Ticks with no defender near before the legion sinks back. */
	static final int GIVE_UP = 600;
	/** Every legion body carries this tag. */
	static final String TAG = "jaymoskingdoms.second_defence";

	/** Held: the Second Defence is over in this world. Saved on the overworld. */
	static final AttachmentType<Boolean> HELD = AttachmentRegistry.create(JaymosKingdoms.id("second_defence_held"),
		builder -> builder.persistent(Codec.BOOL));

	/** The fight under way, or null. */
	private static @Nullable Fight fight;

	private static final class Fight {
		final Ford.Site site;
		final Vec3 away;
		final Set<UUID> defenders = new HashSet<>();
		final List<Mob> standing = new ArrayList<>();
		int wave = 1, risen, empty;
		long nextGroup;
		@Nullable Mob shade;

		Fight(Ford.Site site) {
			this.site = site;
			Vec3 d = Vec3.atCenterOf(site.ford()).subtract(Vec3.atCenterOf(site.keep())).multiply(1.0, 0.0, 1.0);
			this.away = d.lengthSqr() < 1.0E-4 ? new Vec3(1.0, 0.0, 0.0) : d.normalize();
		}

		int waveSize() {
			int size = FIRST_WAVE + (this.wave - 1) * WAVE_GROWTH;
			return (int)Math.round(size * (1.0 + PER_DEFENDER * Math.max(0, this.defenders.size() - 1)));
		}
	}

	private SecondDefence() {
	}

	static void register() {
		RisenOrc.register();
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			int now = server.getTickCount();
			if (fight != null) {
				if (now % FIGHT_TICKS == 0) {
					fight(server);
				}
			} else if (now % CHECK_TICKS == 0) {
				look(server);
			}
		});
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPED.register(server -> fight = null);
	}

	/** Whether the Second Defence has been held in this world. */
	public static boolean held(MinecraftServer server) {
		return Factions.level(server).getAttachedOrElse(HELD, false);
	}

	// ---- waiting for it ---------------------------------------------------------------------------------------------

	private static void look(MinecraftServer server) {
		ServerLevel level = Factions.level(server);
		Ford.Site site = Ford.site(server);
		if (site == null || !level.hasChunkAt(site.ford()) || !level.hasChunkAt(site.keep())) {
			return;
		}
		clearLeftovers(level, site);
		if (!level.isDarkOutside() || !lit(level, site.keep())) {
			return;
		}
		ServerPlayer starter = null;
		for (ServerPlayer player : level.players()) {
			if (!defending(player, site, DEFENDER_NEAR)) {
				continue;
			}
			// the light kept: an Act V milestone of its own
			PackAdvancements.award(player, "light", "done");
			if (starter == null && Retaking.open(player, "second_defence") && player.blockPosition().closerThan(site.ford(), START_NEAR)) {
				starter = player;
			}
		}
		if (starter != null && !held(server)) {
			start(level, site);
		}
	}

	/** Whether a light burns in the keep: any block giving light near its gate (not a fluid). */
	static boolean lit(ServerLevel level, BlockPos gate) {
		for (BlockPos pos : BlockPos.betweenClosed(gate.offset(-LIGHT_REACH, -LIGHT_BELOW, -LIGHT_REACH), gate.offset(LIGHT_REACH, LIGHT_ABOVE, LIGHT_REACH))) {
			BlockState state = level.getBlockState(pos);
			if (state.getLightEmission() > 0 && state.getFluidState().isEmpty()) {
				return true;
			}
		}
		return false;
	}

	private static boolean defending(ServerPlayer player, Ford.Site site, double near) {
		return player.isAlive() && !player.isSpectator() && player.blockPosition().closerThan(site.ford(), near);
	}

	/** Leftover dead from a fight cut short (a server stop): back into the ground. */
	private static void clearLeftovers(ServerLevel level, Ford.Site site) {
		for (Mob mob : level.getEntitiesOfClass(Mob.class, new AABB(site.ford()).inflate(KEEP_NEAR), m -> m.entityTags().contains(TAG))) {
			sink(level, mob);
		}
	}

	// ---- the fight --------------------------------------------------------------------------------------------------

	static void start(ServerLevel level, Ford.Site site) {
		Fight f = new Fight(site);
		f.nextGroup = level.getGameTime() + GROUP_EVERY;
		fight = f;
		BlockPos at = site.ford();
		level.playSound(null, at, SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 1.4F, 0.5F);
		level.playSound(null, at, SoundEvents.AMBIENT_CAVE.value(), SoundSource.AMBIENT, 2.0F, 0.6F);
		for (ServerPlayer player : level.players()) {
			if (defending(player, site, DEFENDER_NEAR)) {
				f.defenders.add(player.getUUID());
			}
		}
	}

	private static void fight(MinecraftServer server) {
		Fight f = fight;
		ServerLevel level = Factions.level(server);
		if (f == null) {
			return;
		}
		boolean anyone = false;
		for (ServerPlayer player : level.players()) {
			if (defending(player, f.site, KEEP_NEAR)) {
				anyone = true;
				if (player.blockPosition().closerThan(f.site.ford(), DEFENDER_NEAR)) {
					f.defenders.add(player.getUUID());
				}
			}
		}
		f.empty = anyone ? 0 : f.empty + FIGHT_TICKS;
		if (f.empty >= GIVE_UP || !level.hasChunkAt(f.site.ford())) {
			lost(level, f);
			return;
		}
		f.standing.removeIf(m -> !m.isAlive() || m.isRemoved());
		if (f.shade != null) {
			if (!f.shade.isAlive()) {
				held(level, f);
			}
			return;
		}
		long now = level.getGameTime();
		if (f.risen < f.waveSize()) {
			if (now >= f.nextGroup && f.standing.size() < MOST_STANDING) {
				group(level, f);
				f.nextGroup = now + GROUP_EVERY;
			}
		} else if (f.standing.isEmpty()) {
			if (f.wave < WAVES) {
				f.wave++;
				f.risen = 0;
				f.nextGroup = now + GROUP_EVERY * 2;
				level.playSound(null, f.site.ford(), SoundEvents.AMBIENT_CAVE.value(), SoundSource.AMBIENT, 2.0F, 0.5F + 0.1F * f.wave);
			} else {
				shade(level, f);
			}
		}
	}

	/** A few of the dead come up together, somewhere past the ford, under a stroke of lightning. */
	private static void group(ServerLevel level, Fight f) {
		RandomSource random = level.getRandom();
		BlockPos at = risePlace(level, f, RISE_NEAR + random.nextInt(RISE_FAR - RISE_NEAR + 1), random.nextInt(2 * RISE_WIDE + 1) - RISE_WIDE);
		if (at == null) {
			return;
		}
		strike(level, at);
		int count = Math.min(GROUP_LEAST + random.nextInt(GROUP_MOST - GROUP_LEAST + 1), f.waveSize() - f.risen);
		for (int i = 0; i < count; i++) {
			Vec3 spot = Vec3.atBottomCenterOf(at).add(random.nextGaussian() * 1.5, 0.0, random.nextGaussian() * 1.5);
			Mob mob = random.nextFloat() < ORC_SHARE ? orc(level, random) : piglin(level);
			if (mob == null) {
				continue;
			}
			BlockPos ground = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, BlockPos.containing(spot));
			mob.snapTo(spot.x, ground.getY(), spot.z, random.nextFloat() * 360.0F, 0.0F);
			raise(level, f, mob);
			f.risen++;
		}
	}

	/** A spot {@code out} blocks past the ford (away from the keep) and {@code side} to one side, on dry ground if it can. */
	private static @Nullable BlockPos risePlace(ServerLevel level, Fight f, int out, int side) {
		Vec3 across = new Vec3(-f.away.z, 0.0, f.away.x);
		for (int tries = 0; tries < 6; tries++) {
			Vec3 at = Vec3.atBottomCenterOf(f.site.ford()).add(f.away.scale(out + tries * 2)).add(across.scale(side));
			BlockPos column = BlockPos.containing(at);
			if (!level.hasChunkAt(column)) {
				continue;
			}
			BlockPos ground = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column);
			if (level.getFluidState(ground.below()).isEmpty() && level.getFluidState(ground).isEmpty()) {
				return ground;
			}
		}
		return null;
	}

	private static @Nullable Mob orc(ServerLevel level, RandomSource random) {
		RisenOrc orc = RisenOrc.TYPE.create(level, EntitySpawnReason.EVENT);
		if (orc == null) {
			return null;
		}
		orc.setBaby(false);
		// what they were buried with: old iron, mostly rust now
		ItemStack[] arms = {new ItemStack(Items.IRON_AXE), new ItemStack(Items.STONE_AXE), new ItemStack(Items.IRON_SWORD), ItemStack.EMPTY};
		orc.setItemSlot(EquipmentSlot.MAINHAND, arms[random.nextInt(arms.length)]);
		if (random.nextInt(3) == 0) {
			orc.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.CHAINMAIL_CHESTPLATE));
		}
		return orc;
	}

	private static @Nullable Mob piglin(ServerLevel level) {
		ZombifiedPiglin piglin = EntityTypes.ZOMBIFIED_PIGLIN.create(level, EntitySpawnReason.EVENT);
		if (piglin != null) {
			piglin.setBaby(false);
			piglin.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.GOLDEN_SWORD));
		}
		return piglin;
	}

	/** One of the legion up: nothing dropped, held to the field, and at the nearest defender. */
	private static void raise(ServerLevel level, Fight f, Mob mob) {
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			mob.setDropChance(slot, 0.0F);
		}
		mob.setCanPickUpLoot(false);
		mob.setPersistenceRequired();
		mob.addTag(TAG);
		mob.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(KEEP_NEAR);
		level.addFreshEntity(mob);
		level.sendParticles(ParticleTypes.SOUL, mob.getX(), mob.getY() + 0.3, mob.getZ(), 12, 0.4, 0.2, 0.4, 0.02);
		level.sendParticles(ParticleTypes.LARGE_SMOKE, mob.getX(), mob.getY() + 0.2, mob.getZ(), 8, 0.4, 0.1, 0.4, 0.01);
		ServerPlayer target = level.getNearestPlayer(mob, KEEP_NEAR) instanceof ServerPlayer p && !p.isCreative() && !p.isSpectator() ? p : null;
		if (target != null) {
			mob.setTarget(target);
			if (mob instanceof ZombifiedPiglin piglin) {
				piglin.setPersistentAngerTarget(EntityReference.of(target));
				piglin.startPersistentAngerTimer();
			}
		}
		f.standing.add(mob);
	}

	/** The one at the back: a shade in the sorcerer's colour, standing past the ford with the last of them. */
	private static void shade(ServerLevel level, Fight f) {
		BlockPos at = risePlace(level, f, SHADE_BACK, 0);
		if (at == null) {
			held(level, f);
			return;
		}
		Mob shade = EntityTypes.EVOKER.create(level, EntitySpawnReason.EVENT);
		if (shade == null) {
			held(level, f);
			return;
		}
		shade.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, (float)(Mth.atan2(-f.away.x, f.away.z) * Mth.RAD_TO_DEG) + 180.0F, 0.0F);
		shade.getAttribute(Attributes.MAX_HEALTH).setBaseValue(SHADE_HEALTH);
		shade.setHealth((float)SHADE_HEALTH);
		shade.setCustomName(Component.translatable("entity.jaymoskingdoms.grue_shade").withStyle(s -> s.withColor(com.jaymoskingdoms.spirits.Grue.COLOR)));
		strike(level, at);
		raise(level, f, shade);
		f.shade = shade;
		// the last of them come up round it
		f.risen = 0;
		for (int i = 0; i < 2; i++) {
			group(level, f);
		}
	}

	private static void strike(ServerLevel level, BlockPos at) {
		LightningBolt bolt = EntityTypes.LIGHTNING_BOLT.create(level, EntitySpawnReason.TRIGGERED);
		if (bolt != null) {
			bolt.snapTo(Vec3.atBottomCenterOf(at));
			bolt.setVisualOnly(true);
			level.addFreshEntity(bolt);
		}
	}

	/** The shade fell: the rest go back into the ground, the ford is held, and everyone who stood there has it. */
	private static void held(ServerLevel level, Fight f) {
		fight = null;
		for (Mob mob : f.standing) {
			sink(level, mob);
		}
		clearLeftovers(level, f.site);
		level.setAttached(HELD, true);
		level.playSound(null, f.site.ford(), SoundEvents.BELL_RESONATE, SoundSource.AMBIENT, 2.0F, 0.8F);
		for (UUID id : f.defenders) {
			ServerPlayer player = level.getServer().getPlayerList().getPlayer(id);
			if (player != null) {
				PackAdvancements.award(player, "second_defence", "done");
				player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.second_defence.held").withStyle(ChatFormatting.GOLD));
				// and the morning after, without a word
				com.jaymoskingdoms.films.LoreFilms.play(player, com.jaymoskingdoms.films.LoreFilms.SECOND_DEFENCE);
			}
		}
	}

	/** Nobody stood: they go back down, and come again another night. */
	private static void lost(ServerLevel level, Fight f) {
		fight = null;
		for (Mob mob : f.standing) {
			sink(level, mob);
		}
		clearLeftovers(level, f.site);
	}

	private static void sink(ServerLevel level, Mob mob) {
		if (mob.isRemoved()) {
			return;
		}
		level.sendParticles(ParticleTypes.SOUL, mob.getX(), mob.getY() + 0.5, mob.getZ(), 10, 0.3, 0.4, 0.3, 0.02);
		mob.discard();
	}

	/** (Ops) Starts it now at the ford, held or not, for testing. False if the world has no keep yet. */
	static boolean force(MinecraftServer server) {
		Ford.Site site = Ford.site(server);
		if (site == null || fight != null) {
			return false;
		}
		start(Factions.level(server), site);
		return true;
	}
}
