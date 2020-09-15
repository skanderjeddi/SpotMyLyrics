package com.skanderj.ts4j;

import java.util.concurrent.TimeUnit;

/**
 * Represents a time + an unit.
 *
 * @author Skander J.
 */
public final class TimeValue {
	public final int value;
	public final TimeUnit unit;

	public TimeValue(final int value, final TimeUnit unit) {
		this.value = value;
		this.unit = unit;
	}
}
