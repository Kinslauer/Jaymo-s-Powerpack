package com.jaymospowers.alchemy.infusion;

import com.jaymospowers.alchemy.Corruption;
import com.jaymospowers.alchemy.Essence;
import com.jaymospowers.alchemy.EssenceList;
import com.jaymospowers.alchemy.Flux;
import com.jaymospowers.alchemy.crucible.Crucibles;
import com.jaymospowers.alchemy.crucible.EssenceJars;
import com.jaymospowers.alchemy.crucible.JarEntity;
import com.jaymospowers.alchemy.flux.FluxEffects;
import com.jaymospowers.core.Soundscape;
import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The infusion stone at work. {@link #begin} matches the centre pedestal and the pedestals around it to a
 * {@link Infusion.Recipe}, works out the instability, and then each tick:
 * <ol>
 * <li><b>Draining</b>: every {@link #DRAIN_TICKS} one point of the recipe's essences is drawn from the nearest jar that has
 * it (a stream of motes runs jar to stone). With no jar to give it the stone waits and its instability climbs.</li>
 * <li><b>Taking</b>: every {@link #TAKE_TICKS} one component leaves its pedestal for the stone. If a component has gone
 * missing (thrown off by an instability event), it waits for it to be set back, instability climbing again.</li>
 * <li>When all are in, the centre item becomes the result and the chunk is left some flux.</li>
 * </ol>
 * Every {@link #EVENT_TICKS} the instability may bring an event (see {@link #event}); a calm stone throws nothing. Its
 * colour shows it: motes run from calm blue to angry purple, and the hum climbs.
 */
public class MatrixEntity extends BlockEntity {
	/** Ticks between drawing one point of essence (0.5 s). */
	public static final int DRAIN_TICKS = 10;
	/** Ticks between taking one component off its pedestal (1.5 s). */
	public static final int TAKE_TICKS = 30;
	/** Ticks between rolls for an instability event (1 s). */
	public static final int EVENT_TICKS = 20;
	/** Chance of an event on a roll, per point of instability, and its ceiling. */
	public static final double CHANCE_PER_POINT = 0.015, MAX_CHANCE = 0.7;
	/** From this instability the roll may be a small explosion that ends the infusion; it grows likelier above. */
	public static final int BLAST_AT = 28;
	/** Instability never goes above this. */
	public static final double MAX_INSTABILITY = 60.0;
	/** Instability gained each time the stone has to wait (no essence in the jars, a component missing). */
	public static final double WAIT_RISE = 0.25;
	/** Players this close (blocks) to the stone are zapped, corrupted... */
	public static final double PEOPLE_RANGE = 8.0;
	/** A zap's damage (a heart). */
	public static final float ZAP_DAMAGE = 2.0F;
	/** Lingering corruption a bad event gives each person near; and the blast's. */
	public static final int CORRUPT_EVENT = 1, CORRUPT_BLAST = 3;
	/** Flux a burst event leaves, plus half the instability; and the blast's. */
	public static final int BURST_FLUX = 12, BLAST_FLUX = 50;
	/** A finished infusion leaves this share of its instability as flux. */
	public static final double FLUX_PER_INSTABILITY = 0.5;
	/** Strength of the blast. It hurts people but never breaks blocks, so the altar survives its own failure. */
	public static final float BLAST_POWER = 1.6F;
	/** Ticks a stream of motes takes to run from its source to the stone. */
	public static final int STREAM_TICKS = 14;
	/** The motes' colours: calm to angry. */
	public static final int CALM_COLOUR = 0x4F8CFF, ANGRY_COLOUR = 0xB02BE8;

	private enum Phase {
		IDLE, DRAINING, TAKING
	}

	/** A line of motes running from {@code from} to the stone. */
	private static final class Stream {
		final Vec3 from;
		final int colour;
		int left = STREAM_TICKS;

		Stream(Vec3 from, int colour) {
			this.from = from;
			this.colour = colour;
		}
	}

	private Phase phase = Phase.IDLE;
	private String recipeId = "";
	private double instability;
	private EssenceList paid = EssenceList.EMPTY;
	private List<String> remaining = new ArrayList<>();
	private String chosen = "";
	private String starter = "";
	// not saved: a restart just picks the rhythm up again
	private int wait, age;
	private boolean told;
	private final List<Stream> streams = new ArrayList<>();

	public MatrixEntity(BlockPos pos, BlockState state) {
		super(Infusion.MATRIX_ENTITY, pos, state);
	}

	/** How the stone feels, in a few words (for the player who asks). */
	public Component mood() {
		if (phase == Phase.IDLE) {
			return Infusion.text("mood.idle");
		}
		String word = instability < 8 ? "calm" : instability < 16 ? "uneasy" : instability < 24 ? "restless" : "wild";
		return Infusion.text("mood." + word);
	}

	/** Matches the pedestals to a recipe and starts the work; tells {@code player} why not otherwise. */
	boolean begin(ServerLevel level, ServerPlayer player) {
		if (phase != Phase.IDLE) {
			player.sendOverlayMessage(Infusion.text("busy"));
			return false;
		}
		BlockPos centrePos = Infusion.centreOf(worldPosition);
		if (!(level.getBlockEntity(centrePos) instanceof PedestalEntity centre)) {
			player.sendOverlayMessage(Infusion.text("no_pedestal"));
			return false;
		}
		ItemStack centreItem = centre.stack();
		if (centreItem.isEmpty()) {
			player.sendOverlayMessage(Infusion.text("empty_centre"));
			return false;
		}
		List<PedestalEntity> all = Infusion.pedestals(level, centrePos);
		List<ItemStack> items = Infusion.loaded(all).stream().map(PedestalEntity::stack).toList();
		Infusion.Recipe found = null;
		String foundId = "";
		for (var entry : Infusion.entries()) {
			Infusion.Recipe r = entry.recipe();
			if (r.matchesCentre(centreItem) && Infusion.assign(r.components(), items) != null
				&& (r.result().special().isEmpty() || Infusion.chooseEnchantment(centreItem, items) != null)) {
				found = r;
				foundId = entry.id().toString();
				break;
			}
		}
		if (found == null) {
			player.sendOverlayMessage(Infusion.text("no_recipe"));
			return false;
		}
		if (!Infusion.allowed(player, found)) {
			player.sendOverlayMessage(Infusion.text("not_understood"));
			return false;
		}
		recipeId = foundId;
		chosen = found.result().special().isPresent() ? Infusion.enchantmentId(Infusion.chooseEnchantment(centreItem, items)) : "";
		if (chosen == null) {
			chosen = "";
		}
		instability = Math.max(0, found.instability() + Infusion.ASYMMETRY * Infusion.asymmetry(level, centrePos, all)
			- Infusion.steadiness(level, worldPosition));
		paid = EssenceList.EMPTY;
		remaining = new ArrayList<>(found.components());
		starter = player.getUUID().toString();
		phase = Phase.DRAINING;
		wait = 0;
		age = 0;
		told = false;
		streams.clear();
		level.playSound(null, worldPosition, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 0.8F, 1.4F);
		player.sendOverlayMessage(mood());
		setChanged();
		return true;
	}

	public static void serverTick(Level level, BlockPos pos, BlockState state, MatrixEntity stone) {
		if (stone.phase != Phase.IDLE && level instanceof ServerLevel server) {
			stone.tick(server);
		}
	}

	private void tick(ServerLevel level) {
		Infusion.Recipe recipe = Infusion.recipe(recipeId);
		BlockPos centrePos = Infusion.centreOf(worldPosition);
		if (recipe == null || !(level.getBlockEntity(centrePos) instanceof PedestalEntity centre) || !recipe.matchesCentre(centre.stack())) {
			fizzle(level);
			return;
		}
		age++;
		Vec3 core = Vec3.atCenterOf(worldPosition);
		aura(level, core);
		hum(level, core);
		runStreams(level, core);
		if (age % EVENT_TICKS == 0) {
			event(level, core, centrePos);
			if (phase == Phase.IDLE) {
				return;
			}
		}
		if (phase == Phase.DRAINING) {
			drain(level, recipe);
		} else {
			take(level, recipe, centre, centrePos);
		}
	}

	// ------------------------------------------------------------------ the work

	private void drain(ServerLevel level, Infusion.Recipe recipe) {
		if (--wait > 0) {
			return;
		}
		wait = DRAIN_TICKS;
		Essence need = null;
		for (Map.Entry<Essence, Integer> en : recipe.essences().amounts().entrySet()) {
			if (paid.get(en.getKey()) < en.getValue()) {
				need = en.getKey();
				break;
			}
		}
		if (need == null) {
			phase = Phase.TAKING;
			wait = TAKE_TICKS;
			told = false;
			setChanged();
			return;
		}
		JarEntity jar = nearestJar(level, need);
		if (jar != null && EssenceJars.drain(level, worldPosition, Infusion.JAR_RANGE, need, 1) > 0) {
			paid = paid.plus(need, 1);
			stream(Vec3.atCenterOf(jar.getBlockPos()).add(0, 0.2, 0), need.colour);
			told = false;
		} else {
			instability = Math.min(MAX_INSTABILITY, instability + WAIT_RISE);
			if (!told) {
				told = true;
				tell(level, "dry");
			}
		}
		setChanged();
	}

	private void take(ServerLevel level, Infusion.Recipe recipe, PedestalEntity centre, BlockPos centrePos) {
		if (--wait > 0) {
			return;
		}
		wait = TAKE_TICKS;
		if (remaining.isEmpty()) {
			finish(level, recipe, centre);
			return;
		}
		List<PedestalEntity> loaded = Infusion.loaded(Infusion.pedestals(level, centrePos));
		int[] fit = Infusion.assign(remaining, loaded.stream().map(PedestalEntity::stack).toList());
		if (fit == null) {
			instability = Math.min(MAX_INSTABILITY, instability + WAIT_RISE);
			if (!told) {
				told = true;
				tell(level, "lacking");
			}
			return;
		}
		told = false;
		PedestalEntity from = loaded.get(0);
		from.take();
		remaining.remove(fit[0]);
		stream(Vec3.atCenterOf(from.getBlockPos()).add(0, 0.5, 0), 0xC9B8FF);
		level.playSound(null, from.getBlockPos(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.7F, 0.8F + level.getRandom().nextFloat() * 0.5F);
		setChanged();
	}

	private void finish(ServerLevel level, Infusion.Recipe recipe, PedestalEntity centre) {
		centre.setStack(Infusion.make(level, recipe, centre.stack(), chosen.isEmpty() ? null : chosen));
		Flux.add(level, worldPosition, recipe.flux() + (int) (instability * FLUX_PER_INSTABILITY));
		phase = Phase.IDLE;
		Vec3 core = Vec3.atCenterOf(worldPosition);
		level.sendParticles(ParticleTypes.END_ROD, core.x, core.y, core.z, 30, 0.3, 0.3, 0.3, 0.12);
		level.playSound(null, worldPosition, SoundEvents.PLAYER_LEVELUP, SoundSource.BLOCKS, 0.8F, 0.7F);
		level.playSound(null, worldPosition, SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 0.6F, 1.6F);
		tell(level, "done");
		setChanged();
	}

	/** The structure broke while it worked: what was taken is gone, the rest stays. */
	private void fizzle(ServerLevel level) {
		phase = Phase.IDLE;
		Vec3 core = Vec3.atCenterOf(worldPosition);
		level.sendParticles(ParticleTypes.SMOKE, core.x, core.y, core.z, 16, 0.3, 0.3, 0.3, 0.03);
		level.playSound(null, worldPosition, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.7F, 0.8F);
		Flux.add(level, worldPosition, 5);
		tell(level, "lost");
		setChanged();
	}

	private @Nullable JarEntity nearestJar(ServerLevel level, Essence essence) {
		JarEntity best = null;
		double bestDist = Double.MAX_VALUE;
		int r = Infusion.JAR_RANGE;
		for (BlockPos pos : BlockPos.betweenClosed(worldPosition.offset(-r, -r, -r), worldPosition.offset(r, r, r))) {
			double d = pos.distSqr(worldPosition);
			if (d > (long) r * r || d >= bestDist || !level.hasChunkAt(pos) || !level.getBlockState(pos).is(Crucibles.ESSENCE_JAR)) {
				continue;
			}
			if (level.getBlockEntity(pos) instanceof JarEntity jar && jar.essence() == essence && jar.amount() > 0) {
				best = jar;
				bestDist = d;
			}
		}
		return best;
	}

	// ------------------------------------------------------------------ instability events

	private double calm() {
		return Math.min(1.0, instability / BLAST_AT);
	}

	/**
	 * Maybe something goes wrong. The chance climbs with the instability; what happens is weighted: an item thrown off a
	 * pedestal (dropped, not lost), a zap at someone near, a burst of flux, lingering corruption for everyone near, and,
	 * from {@link #BLAST_AT}, a small explosion that ends the infusion and spills the components.
	 */
	private void event(ServerLevel level, Vec3 core, BlockPos centrePos) {
		RandomSource random = level.getRandom();
		if (random.nextDouble() >= Math.min(MAX_CHANCE, instability * CHANCE_PER_POINT)) {
			return;
		}
		List<ServerPlayer> near = level.getPlayers(p -> !p.isSpectator() && p.distanceToSqr(core) <= PEOPLE_RANGE * PEOPLE_RANGE);
		List<PedestalEntity> loaded = Infusion.loaded(Infusion.pedestals(level, centrePos));
		int fling = loaded.isEmpty() ? 0 : 4, zap = near.isEmpty() ? 0 : 3, burst = 3, corrupt = near.isEmpty() ? 0 : 2;
		int blast = instability >= BLAST_AT ? 1 + (int) ((instability - BLAST_AT) / 2) : 0;
		int roll = random.nextInt(fling + zap + burst + corrupt + blast);
		if ((roll -= fling) < 0) {
			fling(level, loaded.get(random.nextInt(loaded.size())), 0.3);
		} else if ((roll -= zap) < 0) {
			zap(level, core, near.get(random.nextInt(near.size())));
		} else if ((roll -= burst) < 0) {
			FluxEffects.burst(level, worldPosition, BURST_FLUX + (int) (instability / 2));
			level.sendParticles(dust(ANGRY_COLOUR, 1.2F), core.x, core.y, core.z, 24, 0.5, 0.5, 0.5, 0.0);
			level.playSound(null, worldPosition, SoundEvents.ENDER_EYE_DEATH, SoundSource.BLOCKS, 0.8F, 0.6F);
		} else if ((roll -= corrupt) < 0) {
			for (ServerPlayer p : near) {
				Corruption.add(p, Corruption.Kind.LINGERING, CORRUPT_EVENT);
				p.sendOverlayMessage(Infusion.text("chill"));
				level.sendParticles(ParticleTypes.SOUL, p.getX(), p.getY() + 1.0, p.getZ(), 6, 0.3, 0.5, 0.3, 0.02);
			}
		} else {
			blast(level, core, near, loaded);
		}
	}

	/** Throws the item off a pedestal as a dropped item. */
	private void fling(ServerLevel level, PedestalEntity pedestal, double speed) {
		ItemStack stack = pedestal.take();
		if (stack.isEmpty()) {
			return;
		}
		RandomSource random = level.getRandom();
		BlockPos at = pedestal.getBlockPos();
		ItemEntity drop = new ItemEntity(level, at.getX() + 0.5, at.getY() + 1.1, at.getZ() + 0.5, stack,
			(random.nextDouble() - 0.5) * speed, 0.25 + random.nextDouble() * speed, (random.nextDouble() - 0.5) * speed);
		drop.setDefaultPickUpDelay();
		level.addFreshEntity(drop);
		level.sendParticles(ParticleTypes.POOF, at.getX() + 0.5, at.getY() + 1.1, at.getZ() + 0.5, 6, 0.1, 0.1, 0.1, 0.03);
		level.playSound(null, at, SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.BLOCKS, 0.9F, 0.8F);
	}

	/** A line of sparks from the stone to {@code target}, and a sting. */
	private void zap(ServerLevel level, Vec3 core, ServerPlayer target) {
		Vec3 to = target.position().add(0, target.getBbHeight() * 0.6, 0);
		Vec3 line = to.subtract(core);
		int steps = Math.max(2, (int) (line.length() / 0.35));
		RandomSource random = level.getRandom();
		for (int i = 0; i <= steps; i++) {
			Vec3 p = core.add(line.scale(i / (double) steps)).add((random.nextDouble() - 0.5) * 0.25, (random.nextDouble() - 0.5) * 0.25,
				(random.nextDouble() - 0.5) * 0.25);
			level.sendParticles(i % 2 == 0 ? ParticleTypes.ELECTRIC_SPARK : dust(ANGRY_COLOUR, 0.7F), p.x, p.y, p.z, 1, 0.0, 0.0, 0.0, 0.0);
		}
		level.playSound(null, worldPosition, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.BLOCKS, 0.35F, 1.9F);
		target.hurtServer(level, level.damageSources().magic(), ZAP_DAMAGE);
	}

	/** The worst: a small explosion (no block damage), every component thrown off, the infusion over. */
	private void blast(ServerLevel level, Vec3 core, List<ServerPlayer> near, List<PedestalEntity> loaded) {
		level.explode(null, core.x, core.y, core.z, BLAST_POWER, Level.ExplosionInteraction.NONE);
		for (PedestalEntity p : loaded) {
			fling(level, p, 0.6);
		}
		FluxEffects.burst(level, worldPosition, BLAST_FLUX);
		for (ServerPlayer p : near) {
			Corruption.add(p, Corruption.Kind.LINGERING, CORRUPT_BLAST);
		}
		phase = Phase.IDLE;
		tell(level, "blast");
		setChanged();
	}

	// ------------------------------------------------------------------ show and tell

	private static DustParticleOptions dust(int colour, float size) {
		return new DustParticleOptions(colour, size);
	}

	/** Motes round the stone, calm blue to angry purple as the instability rises. */
	private void aura(ServerLevel level, Vec3 core) {
		if (age % 2 != 0) {
			return;
		}
		double t = calm();
		int colour = mix(CALM_COLOUR, ANGRY_COLOUR, t);
		double angle = age * 0.25;
		double radius = 0.55 + 0.25 * t;
		for (int i = 0; i < 2; i++) {
			double a = angle + i * Math.PI;
			level.sendParticles(dust(colour, (float) (0.7 + 0.5 * t)), core.x + Math.cos(a) * radius, core.y + Math.sin(angle * 0.5) * 0.3,
				core.z + Math.sin(a) * radius, 1, 0.0, 0.0, 0.0, 0.0);
		}
		if (t > 0.5 && level.getRandom().nextFloat() < t * 0.4) {
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, core.x, core.y, core.z, 1, 0.4, 0.4, 0.4, 0.05);
		}
	}

	private static int mix(int a, int b, double t) {
		int r = (int) Math.round((a >> 16 & 0xFF) * (1 - t) + (b >> 16 & 0xFF) * t);
		int g = (int) Math.round((a >> 8 & 0xFF) * (1 - t) + (b >> 8 & 0xFF) * t);
		int bl = (int) Math.round((a & 0xFF) * (1 - t) + (b & 0xFF) * t);
		return r << 16 | g << 8 | bl;
	}

	/** The hum: a low body, a chime now and then, and a growl as it turns angry; higher and louder with the instability. */
	private void hum(ServerLevel level, Vec3 core) {
		if (!Soundscape.due(level, worldPosition.asLong(), "infusion_hum", 30, 55)) {
			return;
		}
		float t = (float) calm();
		Soundscape.play(level, core, SoundSource.BLOCKS,
			Soundscape.Layer.of(0.5F, 0.7F, 0.7F, 0.85F, SoundEvents.CONDUIT_AMBIENT_SHORT).louder(1.0F + t * 0.5F).pitched(1.0F + t * 0.5F),
			Soundscape.Layer.of(0.3F, 0.5F, 0.9F, 1.4F, SoundEvents.AMETHYST_BLOCK_RESONATE).chance(0.5F),
			Soundscape.Layer.of(0.3F, 0.5F, 0.5F, 0.7F, SoundEvents.RESPAWN_ANCHOR_AMBIENT).chance(t * t));
	}

	private void stream(Vec3 from, int colour) {
		if (streams.size() < 24) {
			streams.add(new Stream(from, colour));
		}
	}

	/** Each stream is a short comet of motes running from its source to the stone. */
	private void runStreams(ServerLevel level, Vec3 core) {
		streams.removeIf(s -> --s.left <= 0);
		for (Stream s : streams) {
			double progress = 1.0 - s.left / (double) STREAM_TICKS;
			for (int i = 0; i < 3; i++) {
				double at = Math.max(0.0, progress - i * 0.07);
				Vec3 p = s.from.lerp(core, at);
				level.sendParticles(dust(s.colour, 0.8F), p.x, p.y + Math.sin(at * Math.PI) * 0.4, p.z, 1, 0.0, 0.0, 0.0, 0.0);
			}
		}
	}

	private void tell(ServerLevel level, String key) {
		if (starter.isEmpty()) {
			return;
		}
		try {
			ServerPlayer player = level.getServer().getPlayerList().getPlayer(UUID.fromString(starter));
			if (player != null) {
				player.sendOverlayMessage(Infusion.text(key));
			}
		} catch (IllegalArgumentException ignored) {
			// not a uuid: nobody to tell
		}
	}

	// ------------------------------------------------------------------ saving

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		try {
			phase = Phase.valueOf(input.getStringOr("phase", "IDLE"));
		} catch (IllegalArgumentException e) {
			phase = Phase.IDLE;
		}
		recipeId = input.getStringOr("recipe", "");
		instability = input.getDoubleOr("instability", 0.0);
		paid = input.read("paid", EssenceList.CODEC).orElse(EssenceList.EMPTY);
		remaining = new ArrayList<>(input.read("remaining", Codec.STRING.listOf()).orElse(List.of()));
		chosen = input.getStringOr("chosen", "");
		starter = input.getStringOr("starter", "");
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		output.putString("phase", phase.name());
		output.putString("recipe", recipeId);
		output.putDouble("instability", instability);
		output.store("paid", EssenceList.CODEC, paid);
		output.store("remaining", Codec.STRING.listOf(), remaining);
		output.putString("chosen", chosen);
		output.putString("starter", starter);
	}
}
