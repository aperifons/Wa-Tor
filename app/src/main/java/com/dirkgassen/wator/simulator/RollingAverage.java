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

package com.dirkgassen.wator.simulator;

/**
 * @author dirk.
 */
public class RollingAverage {

	private long[] valueHistory;

	private long valueHistorySum = 0;

	private int valueCurrentNo = 0;

	private int valueCount = 0;

	final public float getAverage() {
		if (valueCount == 0) {
			return 0f;
		}
		return (float) valueHistorySum / valueCount;
	}

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
