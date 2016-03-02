/*
 * WorldParameters.java is part of Wa-Tor (C) 2016 by Dirk Gassen.
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
public class WorldParameters {
	private short width = 300;
	private short height = 200;
	private int initialFishCount = 1000;
	private int initialSharkCount = 600;
	private short fishBreedTime = 14;
	private short sharkBreedTime = 13;
	private short sharkStarveTime = 12;
	private short sharkNewbornHunger = -50;

	public short getWidth() {
		return width;
	}

	public short getHeight() {
		return height;
	}

	public int getInitialFishCount() {
		return initialFishCount;
	}

	public int getInitialSharkCount() {
		return initialSharkCount;
	}

	public short getFishBreedTime() {
		return fishBreedTime;
	}

	public short getSharkBreedTime() {
		return sharkBreedTime;
	}

	public short getSharkStarveTime() {
		return sharkStarveTime;
	}

	public WorldParameters setWidth(short width) {
		this.width = width;
		return this;
	}

	public WorldParameters setHeight(short height) {
		this.height = height;
		return this;
	}

	public WorldParameters setInitialFishCount(int initialFishCount) {
		this.initialFishCount = initialFishCount;
		return this;
	}

	public WorldParameters setInitialSharkCount(int initialSharkCount) {
		this.initialSharkCount = initialSharkCount;
		return this;
	}

	public WorldParameters setFishBreedTime(short fishBreedTime) {
		this.fishBreedTime = fishBreedTime;
		return this;
	}

	public WorldParameters setSharkBreedTime(short sharkBreedTime) {
		this.sharkBreedTime = sharkBreedTime;
		return this;
	}

	public WorldParameters setSharkStarveTime(short sharkStarveTime) {
		this.sharkStarveTime = sharkStarveTime;
		return this;
	}

	public WorldParameters setSharkNewbornHunger(short sharkNewbornHunger) {
		this.sharkNewbornHunger = sharkNewbornHunger;
		return this;
	}

	protected void verify() {
		if (initialFishCount + initialSharkCount > width * height) {
			throw new IllegalArgumentException("Can't have " + initialFishCount + " fish and " + initialSharkCount + " sharks in a world with " + (width * height) + " cells");
		}
		if (fishBreedTime > Simulator.MAX_FISH_BREED_TIME) {
			throw new IllegalArgumentException("Fish breed time " + fishBreedTime + " too large (max " + Simulator.MAX_FISH_BREED_TIME + ")");
		}
		if (sharkBreedTime > Simulator.MAX_SHARK_BREED_TIME) {
			throw new IllegalArgumentException("Shark breed time " + sharkBreedTime + " too large (max " + Simulator.MAX_SHARK_BREED_TIME + ")");
		}
		if (sharkStarveTime > Simulator.MAX_SHARK_STARVE_TIME) {
			throw new IllegalArgumentException("Shark max hunger " + sharkStarveTime + " too large (max " + Simulator.MAX_SHARK_STARVE_TIME + ")");
		}


	}

}
