package com.skanderj.ts4j;

import java.util.concurrent.TimeUnit;

/**
 * Represents a task which will be executed by the scheduler.
 *
 * @author Skander J.
 */
public abstract class Task {
	public static final int NO_REPEATS = -1;

	private final TimeValue initialDelay;
	private final TimeValue period;
	private int repeatsCounter;

	public Task(final int initialDelay, final TimeUnit unit) {
		this(new TimeValue(initialDelay, unit));
	}

	public Task(final int initialDelay, final int period, final TimeUnit unit) {
		this(new TimeValue(initialDelay, unit), new TimeValue(period, unit));
	}

	public Task(final TimeValue initialDelay) {
		this.initialDelay = initialDelay;
		this.period = new TimeValue(Task.NO_REPEATS, null);
		this.repeatsCounter = 0;
	}

	public Task(final TimeValue initialDelay, final TimeValue period) {
		this.initialDelay = initialDelay;
		this.period = period;
		this.repeatsCounter = 0;
	}

	public abstract void execute();

	public final Runnable asRunnable() {
		return () -> {
			Task.this.execute();
			Task.this.repeatsCounter += 1;
		};
	}

	public final TimeValue getInitialDelay() {
		return this.initialDelay;
	}

	public final TimeValue getPeriod() {
		return this.period;
	}

	public final int getRepeatsCounter() {
		return this.repeatsCounter;
	}

	public abstract TaskType type();
}
