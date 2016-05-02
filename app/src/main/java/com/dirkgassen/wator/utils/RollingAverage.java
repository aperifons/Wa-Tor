/*
 * RollingAverage.java is part of Wa-Tor (C) 2016 by Dirk Gassen.
 *
 * Wa-Tor is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Wa-Tor is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.dirkgassen.wator.utils;

/**
 * Class to provide a rolling average over some history of values, e.g., the last 60 values (default).
 * New values can be added by calling {@link #add(long)} while values outside of the specified sliding window
 * are disregarded automatically.
 */
public class RollingAverage {

	/**
	 * Stores the values in the average. {@link #valueCurrentNo} is the index in this array where the next
	 * new value is stored. This index is decremented and wrapped around to start at the end of the array again.
	 *
	 * The array must be one element bigger than the history size to accommodate for a spot to save the next value.
	 */
	private long[] valueHistory;

	/** Current sum of all values in {@link #valueHistory}. */
	private long valueHistorySum = 0;

	/**
	 * Index in {@link #valueHistory} where the next new value is going to be stored. The array is filled backwards
	 * (no idea why I made this choice to begin with) and when this index reaches the beginning it wraps around to the
	 * end.
	 */
	private int valueCurrentNo = 0;

	/**
	 * Number of values in {@link #valueHistory}. The numbers stored can be less than the size of the array so we
	 * need to keep track of how full the array is.
	 */
	private int valueCount = 0;

	/** @return average of all n most recent values added (0 if no values have been added) */
	final public float getAverage() {
		if (valueCount == 0) {
			return 0f;
		}
		return (float) valueHistorySum / valueCount;
	}

	/**
	 * Add a new value to the rolling average.
	 *
	 * @param newValue value to add
	 */
	final public void add(long newValue) {
		valueHistory[valueCurrentNo] = newValue;
		if (valueCurrentNo == 0) {
			valueCurrentNo = valueHistory.length - 1;
		} else {
			valueCurrentNo--;
		}
		valueHistorySum += newValue;
		if (valueCount < valueHistory.length-1) {
			valueCount++;
		} else {
			valueHistorySum = valueHistorySum - valueHistory[valueCurrentNo];
		}
	}

	public RollingAverage() {
		valueHistory = new long[61];
	}

	public RollingAverage(int history) {
		valueHistory = new long[history+1];
	}

}
