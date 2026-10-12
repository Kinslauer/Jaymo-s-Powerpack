package com.jaymospowers.tinkering.smeltery;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * The smeltery's pool: several liquids stacked bottom first, sharing one capacity. The bottom one is what the faucets
 * pour; a player can send any of them to the bottom.
 */
public final class Tank {
	/** One liquid and how much of it (mB). */
	public static final class Layer {
		public final Molten molten;
		public int amount;

		Layer(Molten molten, int amount) {
			this.molten = molten;
			this.amount = amount;
		}
	}

	private final List<Layer> layers = new ArrayList<>();
	private int capacity;

	public List<Layer> layers() {
		return layers;
	}

	public int capacity() {
		return capacity;
	}

	public void setCapacity(int capacity) {
		this.capacity = capacity;
	}

	public int total() {
		int total = 0;
		for (Layer layer : layers) {
			total += layer.amount;
		}
		return total;
	}

	public int space() {
		return Math.max(0, capacity - total());
	}

	public int amount(Molten molten) {
		Layer layer = layer(molten);
		return layer == null ? 0 : layer.amount;
	}

	private @Nullable Layer layer(Molten molten) {
		for (Layer layer : layers) {
			if (layer.molten == molten) {
				return layer;
			}
		}
		return null;
	}

	/** Pours some in (on top, or into its layer); returns how much fitted. */
	public int fill(Molten molten, int amount, boolean simulate) {
		int fits = Math.min(amount, space());
		if (fits > 0 && !simulate) {
			Layer layer = layer(molten);
			if (layer == null) {
				layers.add(new Layer(molten, fits));
			} else {
				layer.amount += fits;
			}
		}
		return fits;
	}

	/** Takes some out; returns how much there was. */
	public int drain(Molten molten, int amount, boolean simulate) {
		Layer layer = layer(molten);
		if (layer == null) {
			return 0;
		}
		int taken = Math.min(amount, layer.amount);
		if (!simulate) {
			layer.amount -= taken;
			if (layer.amount <= 0) {
				layers.remove(layer);
			}
		}
		return taken;
	}

	public @Nullable Layer bottom() {
		return layers.isEmpty() ? null : layers.getFirst();
	}

	public void toBottom(int index) {
		if (index > 0 && index < layers.size()) {
			layers.addFirst(layers.remove(index));
		}
	}

	public void save(ValueOutput output) {
		ValueOutput.ValueOutputList list = output.childrenList("molten");
		for (Layer layer : layers) {
			ValueOutput child = list.addChild();
			child.putString("id", layer.molten.id);
			child.putInt("amount", layer.amount);
		}
		output.putInt("capacity", capacity);
	}

	public void load(ValueInput input) {
		layers.clear();
		for (ValueInput child : input.childrenListOrEmpty("molten")) {
			Molten molten = Molten.byId(child.getStringOr("id", ""));
			int amount = child.getIntOr("amount", 0);
			if (molten != null && amount > 0) {
				layers.add(new Layer(molten, amount));
			}
		}
		capacity = input.getIntOr("capacity", 0);
	}
}
