package com.jaymospowers.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;

/**
 * An {@code ArrayList<Long>} that answers {@code contains} from a hash set instead of walking the list. The set is kept
 * in step by {@code add}; any other change to the list (removal, clear...) is noticed through the list's modification
 * count and the set is rebuilt the next time it is asked, so the answers are always the list's own.
 */
public final class IndexedPositions extends ArrayList<Long> {
	private HashSet<Long> index;
	/** The list's modCount when {@link #index} last matched it. */
	private int indexedAt;

	public IndexedPositions(Collection<Long> from) {
		super(from);
	}

	@Override
	public boolean contains(Object o) {
		if (this.index == null || this.indexedAt != this.modCount) {
			this.index = new HashSet<>(this);
			this.indexedAt = this.modCount;
		}
		return this.index.contains(o);
	}

	@Override
	public boolean add(Long position) {
		boolean inStep = this.index != null && this.indexedAt == this.modCount;
		boolean added = super.add(position);
		if (inStep) {
			this.index.add(position);
			this.indexedAt = this.modCount;
		}
		return added;
	}
}
