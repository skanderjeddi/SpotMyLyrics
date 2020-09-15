package com.skanderj.ts4j;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;

/**
 * 
 * The main class for the TS4J project. Use this class to schedule and cancel
 * tasks.
 * 
 * @author Skander J.
 *
 */
public final class TaskScheduler {
	private static final ScheduledExecutorService executor = Executors.newScheduledThreadPool(0);
	private static final Map<String, ScheduledFuture<?>> scheduledFutures = new HashMap<>();
	private static final Map<String, Task> tasks = new HashMap<>();

	private TaskScheduler() {
	}

	public static boolean scheduleTask(final String identifier, final Task task) {
		ScheduledFuture<?> future = null;
		if (task.getPeriod().value != Task.NO_REPEATS) {
			switch (task.type()) {
			case FIXED_DELAY:
				future = TaskScheduler.executor.scheduleWithFixedDelay(task.asRunnable(), task.getInitialDelay().value, task.getInitialDelay().unit.convert(task.getPeriod().value, task.getPeriod().unit), task.getInitialDelay().unit);
				break;
			case FIXED_RATE:
				future = TaskScheduler.executor.scheduleAtFixedRate(task.asRunnable(), task.getInitialDelay().value, task.getInitialDelay().unit.convert(task.getPeriod().value, task.getPeriod().unit), task.getInitialDelay().unit);
				break;
			}
		} else {
			future = TaskScheduler.executor.schedule(task.asRunnable(), task.getInitialDelay().value, task.getInitialDelay().unit);
		}
		if (future != null) {
			TaskScheduler.scheduledFutures.put(identifier, future);
			TaskScheduler.tasks.put(identifier, task);
			return true;
		} else {
			return false;
		}
	}

	public static boolean cancelTask(final String identifier, final boolean finish) {
		final ScheduledFuture<?> future = TaskScheduler.scheduledFutures.get(identifier);
		if (future != null) {
			future.cancel(finish);
			return true;
		} else {
			return false;
		}
	}

	public static int getRepeatsCounter(final String identifier) {
		return TaskScheduler.tasks.get(identifier) == null ? -1 : TaskScheduler.tasks.get(identifier).getRepeatsCounter();
	}
}
