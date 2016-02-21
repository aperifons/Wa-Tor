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

	private long[] tickDurationHistory = new long[60];

	private long tickDurationHistorySum = 0;

	private int tickDurationHistoryCurrentNo = 0;

	final public long getAverage() {
		return tickDurationHistorySum / tickDurationHistory.length;
	}

	final public void add(long newValue) {
		tickDurationHistory[tickDurationHistoryCurrentNo] = newValue;
		if (tickDurationHistoryCurrentNo == 0) {
			tickDurationHistoryCurrentNo = tickDurationHistory.length - 1;
		} else {
			tickDurationHistoryCurrentNo--;
		}
		tickDurationHistorySum = tickDurationHistorySum + newValue - tickDurationHistory[tickDurationHistoryCurrentNo];
	}

}
