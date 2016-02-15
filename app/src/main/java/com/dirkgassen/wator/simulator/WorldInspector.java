/*
 * WorldInspector.java is part of Wa-Tor (C) 2016 by Dirk Gassen.
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
final public class WorldInspector {

	public enum MOVE_RESULT {
		NEXT_CELL,
		NEXT_ROW,
		RESET
	}

	private final short[] world;
	private final int worldWidth;
	private int currentNo;

	final public void moveTo(int x, int y) {
		currentNo = x + y * worldWidth;
	}

	final public MOVE_RESULT moveToNext() {
		if (currentNo == world.length - 1) {
			currentNo = 0;
			return MOVE_RESULT.RESET;
		}
		currentNo++;
		return currentNo % worldWidth == 0 ? MOVE_RESULT.NEXT_ROW : MOVE_RESULT.NEXT_CELL;
	}

	final public int getCurrentPosition() {
		return currentNo;
	}

	final public int getCurrentX() {
		return currentNo % worldWidth;
	}

	final public int getCurrentY() {
		return currentNo / worldWidth;
	}

	final public boolean isFish() {
		return world[currentNo] < 0;
	}

	final public boolean isShark() {
		return world[currentNo] > 0;
	}

	final public boolean isEmpty() {
		return world[currentNo] == 0;
	}

	final public short getFishAge() {
		if (world[currentNo] >= 0) {
			return 0;
		}
		return (short) -world[currentNo];
	}

	final public short getSharkAge() {
		if (world[currentNo] <= 0) {
			return 0;
		}
		return (short) (world[currentNo] & 255);
	}

	final public short getSharkHunger() {
		if (world[currentNo] <= 0) {
			return 0;
		}
		return (short) (world[currentNo] >> 8);
	}

	final public short getFishAge(int no) {
		if (world[no] >= 0) {
			return 0;
		}
		return (short) -world[no];
	}

	final public short getSharkAge(int no) {
		if (world[no] <= 0) {
			return 0;
		}
		return (short) (world[no] & 255);
	}

	final public short getSharkHunger(int no) {
		if (world[no] <= 0) {
			return 0;
		}
		return (short) (world[no] >> 8);
	}

	final public short getFishAge(int x, int y) {
		return getFishAge(x + y * worldWidth);
	}

	final public short getSharkAge(int x, int y) {
		return getSharkAge(x + y * worldWidth);
	}

	final public short getSharkHunger(int x, int y) {
		return getSharkHunger(x + y * worldWidth);
	}

	protected WorldInspector(short[] world, int worldWidth) {
		this.world = world;
		this.worldWidth = worldWidth;
		this.currentNo = 0;
	}

}
