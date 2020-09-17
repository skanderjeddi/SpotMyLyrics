package com.skanderj.ts4j;

/**
 * Used to store the two types of repeating tasks:
 *
 * 1) FIXED_RATE: this means that the task will be executed at a constant delay
 * with the countdown starting when the previous cycle is over.
 *
 * 2) FIXED_DELAY: this means that the task will be executed at a constant delay
 * regardless of if it has finished the previous cycle.
 *
 * @author Skander J.
 *
 */
public enum TaskType {
	FIXED_RATE, FIXED_DELAY;
}
