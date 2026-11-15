package com.jaymoskitchen.mill.power;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.Containers;
import net.minecraft.world.level.Level;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * A windmill's or waterwheel's workings (after Better With Mods, MIT; our own code). About once a second it checks
 * whether it can turn ({@link Rotor#speed}), follows its shaft ({@link Shafts#follow}), sets the shaft turning and
 * keeps the machine at the end working ({@link ShaftDriven#turnFromShaft}). Clients just spin it at the synced speed.
 *
 * <p>(GT New Horizons picks, patch 8) A windmill turns on its four sails ({@link SailKind}), each fitted by hand. Each
 * pulls its share of the wind, wears as it turns, and can tear in a thunderstorm or a gale (sooner when it's worn);
 * worn through or torn, it hangs in rags and pulls nothing until a new sail goes on that spar. A worn sail is patched
 * with its own stuff on the hub. Windmills from before sails had kinds (and the village windmills) carry four hemp sails.
 */
public class RotorEntity extends BlockEntity {
	/** Ticks between checks; the machine is kept working a little longer than this so it doesn't stutter. */
	static final int CHECK_TICKS = 20, DRIVE_TICKS = CHECK_TICKS + 10;
	/** One in this many checks while turning, a creak. */
	static final int CREAK_CHANCE = 6;
	/** Below this speed the sails don't get it going. */
	static final float MIN_TURN = 0.15F;
	/** A windmill's sails in a gale: above this wind strength each sail can tear even without thunder, more the harder it blows. */
	static final float GALE = 1.15F, GALE_TEAR = 2.0F;
	/** A sail with less than this share of its life left tears this many times as easily. */
	static final float WORN = 0.5F, WORN_TEAR = 2.0F;
	/** A patch puts back this share of the sail's life; a sail takes one once it's below this share of it. */
	static final float PATCH = 0.25F, PATCH_BELOW = 0.95F;
	/** Clients hear of a sail's wear in steps of this share of its life (so they know when it can take a patch). */
	static final int WEAR_STEPS = 20;
	public static final int SAILS = 4;

	/** One spar's sail: its kind (null: bare), its life left (seconds of turning), and whether it's torn. */
	record Sail(@Nullable SailKind kind, float life, boolean torn) {
		static final Sail BARE = new Sail(null, 0.0F, false);
		static final Codec<Sail> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.STRING.fieldOf("kind").forGetter(s -> s.kind == null ? "" : s.kind.id()),
			Codec.FLOAT.fieldOf("life").forGetter(Sail::life),
			Codec.BOOL.fieldOf("torn").forGetter(Sail::torn)).apply(i, (k, l, t) -> new Sail(SailKind.byId(k), l, t)));

		static Sail fresh(SailKind kind) {
			return new Sail(kind, kind.life, false);
		}

		boolean pulling() {
			return kind != null && !torn;
		}

		boolean patchable(ItemStack material) {
			return pulling() && material.is(kind.patch) && life < kind.life * PATCH_BELOW;
		}

		/** Its wear in {@link #WEAR_STEPS} steps, as clients hear of it. */
		int step() {
			return kind == null ? -1 : (int) (life / kind.life * WEAR_STEPS);
		}

		/** What the renderer shows: -1 bare, else kind * 3 + (0 whole, 1 worn, 2 in rags). */
		int look() {
			return kind == null ? -1 : kind.ordinal() * 3 + (torn ? 2 : life < kind.life * WORN ? 1 : 0);
		}
	}

	/** Signed speed (0 = still), synced to clients. */
	private float speed;
	private int ticks;
	/** The shaft pieces set turning last time, to set still again when the run changes or it stops. */
	private List<BlockPos> turning = new ArrayList<>();

	/** A windmill's four sails' colours (Better With Mods': dyed one at a time, in turn), and the next one a dye goes to. */
	private final DyeColor[] sails = {DyeColor.WHITE, DyeColor.WHITE, DyeColor.WHITE, DyeColor.WHITE};
	private int nextSail;
	/** The four sails (a windmill from before sails had kinds keeps these: hemp, as good as new). */
	private final Sail[] fitted = {Sail.fresh(SailKind.CLOTH), Sail.fresh(SailKind.CLOTH), Sail.fresh(SailKind.CLOTH), Sail.fresh(SailKind.CLOTH)};

	/** Client side: the angle shown, now and last tick. */
	public float angle, lastAngle;

	public RotorEntity(BlockPos pos, BlockState state) {
		super(Power.ROTOR_ENTITY, pos, state);
		// spread the checks out over the second
		ticks = (int) Math.floorMod(pos.asLong(), (long) CHECK_TICKS);
	}

	public Rotor rotor() {
		return getBlockState().getBlock() instanceof RotorBlock block ? block.rotor : Rotor.WINDMILL;
	}

	/** The way its shaft runs from the hub. */
	public Direction facing() {
		return getBlockState().hasProperty(RotorBlock.FACING) ? getBlockState().getValue(RotorBlock.FACING) : Direction.NORTH;
	}

	public float speed() {
		return speed;
	}

	public DyeColor sail(int sail) {
		return sails[sail];
	}

	/** Sail {@code sail}'s look: -1 bare, else its kind's ordinal times 3 plus 0 whole, 1 worn, 2 in rags. */
	public int look(int sail) {
		return fitted[sail].look();
	}

	/** Dyes the next sail in turn; false (and no dye used) when that one is already this colour. */
	public boolean dye(DyeColor colour) {
		int sail = nextSail;
		nextSail = (nextSail + 1) % sails.length;
		if (sails[sail] == colour) {
			setChanged();
			return false;
		}
		sails[sail] = colour;
		changed();
		return true;
	}

	/** Just set up from the item: bare spars, waiting for their sails. */
	void bare() {
		for (int i = 0; i < SAILS; i++) {
			fitted[i] = Sail.BARE;
		}
		changed();
	}

	/** Fits a sail (as worn as the item is) to the first bare or torn spar; false if every spar has a sail. */
	public boolean fit(ItemStack stack) {
		if (!(stack.getItem() instanceof SailItem item)) {
			return false;
		}
		for (int i = 0; i < SAILS; i++) {
			if (!fitted[i].pulling()) {
				fitted[i] = new Sail(item.kind, Math.max(1.0F, item.kind.life - stack.getDamageValue()), false);
				sails[i] = DyeColor.WHITE;
				changed();
				return true;
			}
		}
		return false;
	}

	/** Whether {@code material} would patch one of its sails (both sides: the wear is synced in steps). */
	public boolean needsPatch(ItemStack material) {
		return mostWorn(material) >= 0;
	}

	private int mostWorn(ItemStack material) {
		int best = -1;
		for (int i = 0; i < SAILS; i++) {
			Sail sail = fitted[i];
			if (sail.patchable(material) && (best < 0 || sail.life / sail.kind.life < fitted[best].life / fitted[best].kind.life)) {
				best = i;
			}
		}
		return best;
	}

	/** Patches the most worn whole sail that {@code material} mends; false if none needs it. */
	public boolean patch(ItemStack material) {
		int best = mostWorn(material);
		if (best < 0) {
			return false;
		}
		Sail sail = fitted[best];
		fitted[best] = new Sail(sail.kind, Math.min(sail.kind.life, sail.life + sail.kind.life * PATCH), false);
		changed();
		return true;
	}

	/**
	 * Takes down the last sail (its item, as worn as it is), or cuts away the rags of a torn one (nothing back). Empty
	 * when there's no sail on it.
	 */
	public ItemStack takeDown() {
		for (int i = SAILS - 1; i >= 0; i--) {
			Sail sail = fitted[i];
			if (sail.kind != null) {
				fitted[i] = Sail.BARE;
				sails[i] = DyeColor.WHITE;
				changed();
				return sail.torn ? ItemStack.EMPTY : item(sail);
			}
		}
		return ItemStack.EMPTY;
	}

	/** Whether any spar has a sail or rags on it, to take down. */
	public boolean hasSail() {
		for (Sail sail : fitted) {
			if (sail.kind != null) {
				return true;
			}
		}
		return false;
	}

	private static ItemStack item(Sail sail) {
		ItemStack stack = new ItemStack(sail.kind.item());
		stack.setDamageValue(Math.max(0, Math.min(stack.getMaxDamage() - 1, Math.round(sail.kind.life - sail.life))));
		return stack;
	}

	/** The sails' pull together: 1 for four good hemp sails, 0 with none whole. */
	private float pull() {
		float pull = 0.0F;
		for (Sail sail : fitted) {
			if (sail.pulling()) {
				pull += sail.kind.pull / SAILS;
			}
		}
		return pull;
	}

	private void changed() {
		setChanged();
		if (level != null && !level.isClientSide()) {
			level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
		}
	}

	public static void serverTick(Level level, BlockPos pos, BlockState state, RotorEntity rotor) {
		// a counter, not the clock, so extra ticks (Jaymo's Powers' Time Bloom) just make it check sooner
		if (++rotor.ticks >= CHECK_TICKS && level instanceof ServerLevel server) {
			rotor.ticks = 0;
			rotor.check(server);
		}
	}

	public static void clientTick(Level level, BlockPos pos, BlockState state, RotorEntity rotor) {
		rotor.lastAngle = rotor.angle;
		rotor.angle += rotor.speed * rotor.rotor().degreesPerTick;
		if (rotor.angle >= 360.0F || rotor.angle <= -360.0F) {
			rotor.angle %= 360.0F;
			rotor.lastAngle = rotor.angle - rotor.speed * rotor.rotor().degreesPerTick;
		}
	}

	private void check(ServerLevel level) {
		boolean windmill = rotor() == Rotor.WINDMILL;
		float now = rotor().speed(level, worldPosition, facing());
		if (windmill) {
			float wind = now;
			now *= pull();
			if (now < MIN_TURN) {
				now = 0.0F;
			}
			// the sails wear only while they turn; a storm can tear them still
			weather(level, now != 0.0F ? wind : 0.0F);
		}
		List<BlockPos> run = new ArrayList<>();
		ShaftDriven machine = Shafts.follow(level, worldPosition, facing(), run);
		if (now != 0.0F && machine != null) {
			machine.turnFromShaft(Math.abs(now), DRIVE_TICKS);
		}
		List<BlockPos> turnNow = now != 0.0F ? run : List.of();
		for (BlockPos old : turning) {
			if (!turnNow.contains(old)) {
				Shafts.setTurning(level, old, false);
			}
		}
		for (BlockPos piece : turnNow) {
			Shafts.setTurning(level, piece, true);
		}
		if (!turning.equals(turnNow)) {
			turning = new ArrayList<>(turnNow);
			setChanged();
		}
		if (now != speed) {
			speed = now;
			setChanged();
			level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
		}
		if (now != 0.0F && level.getRandom().nextInt(CREAK_CHANCE) == 0) {
			level.playSound(null, worldPosition, SoundEvents.WOOD_STEP, SoundSource.BLOCKS, 0.4F, 0.5F + level.getRandom().nextFloat() * 0.2F);
		}
	}

	/**
	 * A second of weather on the sails: each whole one wears by how fast the wind would turn it ({@code wind}, before the
	 * sails' own pull), and out in a thunderstorm or a gale it may tear.
	 */
	private void weather(ServerLevel level, float wind) {
		if (!Rotor.exposed(level, worldPosition, facing())) {
			return;
		}
		float storm = Wind.storm(level, worldPosition) + Math.max(0.0F, Wind.strength(level, worldPosition) - GALE) * GALE_TEAR;
		boolean changed = false, worn = false;
		for (int i = 0; i < SAILS; i++) {
			Sail sail = fitted[i];
			if (!sail.pulling()) {
				continue;
			}
			int lookWas = sail.look(), stepWas = sail.step();
			float life = sail.life - wind;
			float tear = sail.kind.tear * storm * (life < sail.kind.life * WORN ? WORN_TEAR : 1.0F);
			boolean torn = life <= 0.0F || tear > 0.0F && level.getRandom().nextFloat() < tear;
			fitted[i] = new Sail(sail.kind, Math.max(0.0F, life), torn);
			if (torn) {
				// it goes with a rip and a flap of rags
				level.playSound(null, worldPosition, SoundEvents.WOOL_BREAK, SoundSource.BLOCKS, 1.2F, 0.6F);
				level.sendParticles(ParticleTypes.POOF, worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5,
					6, Rotor.WINDMILL.radius / 2.0, Rotor.WINDMILL.radius / 2.0, Rotor.WINDMILL.radius / 2.0, 0.02);
			}
			changed |= fitted[i].look() != lookWas || fitted[i].step() != stepWas;
			worn |= life != sail.life;
		}
		if (changed) {
			changed();
		} else if (worn) {
			setChanged();
		}
	}

	/** Taken down: its shaft stops, and its whole sails come down with it. */
	@Override
	public void preRemoveSideEffects(BlockPos pos, BlockState state) {
		if (level != null && !level.isClientSide()) {
			for (BlockPos piece : turning) {
				Shafts.setTurning(level, piece, false);
			}
			turning.clear();
			if (rotor() == Rotor.WINDMILL) {
				for (int i = 0; i < SAILS; i++) {
					if (fitted[i].pulling()) {
						Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, item(fitted[i]));
					}
					fitted[i] = Sail.BARE;
				}
			}
		}
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		speed = input.getFloatOr("Speed", 0.0F);
		turning = new ArrayList<>(input.read("Turning", BlockPos.CODEC.listOf()).orElse(List.of()));
		loadSails(input);
		nextSail = Math.floorMod(input.getIntOr("NextSail", 0), sails.length);
		// a windmill saved before sails had kinds has none of this: it keeps four hemp sails
		input.read("Fitted", Sail.CODEC.listOf()).ifPresent(saved -> {
			for (int i = 0; i < SAILS; i++) {
				fitted[i] = i < saved.size() ? saved.get(i) : Sail.BARE;
			}
		});
	}

	private void loadSails(ValueInput input) {
		List<DyeColor> saved = input.read("Sails", DyeColor.CODEC.listOf()).orElse(List.of());
		for (int i = 0; i < sails.length; i++) {
			sails[i] = i < saved.size() ? saved.get(i) : DyeColor.WHITE;
		}
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		output.putFloat("Speed", speed);
		output.store("Turning", BlockPos.CODEC.listOf(), turning);
		output.store("Sails", DyeColor.CODEC.listOf(), List.of(sails));
		output.putInt("NextSail", nextSail);
		output.store("Fitted", Sail.CODEC.listOf(), List.of(fitted));
	}

	@Override
	public ClientboundBlockEntityDataPacket getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this);
	}

	@Override
	public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
		TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, registries);
		output.putFloat("Speed", speed);
		output.store("Sails", DyeColor.CODEC.listOf(), List.of(sails));
		output.store("Fitted", Sail.CODEC.listOf(), List.of(fitted));
		return output.buildResult();
	}
}
