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
 * Class to store parameters for a world ({@link Simulator}). Objects of this class can be used to create a new
 * {@link Simulator}.
 */
public class WorldParameters {

	/** Width of a world */
	private short width = 300;

	/** Height of a height */
	private short height = 200;

	/** Initial number of fish in a world */
	private int initialFishCount = 1000;

	/** Initial number of shark in a world */
	private int initialSharkCount = 600;

	/** Ticks until a fish reaches maturity and reproduces */
	private short fishBreedTime = 14;

	/** Ticks until a shark reaches maturity and reproduces */
	private short sharkBreedTime = 13;

	/** Ticks a shark can go without eating a fish before it dies */
	private short sharkStarveTime = 12;

	/** @return width of the described world */
	public short getWidth() {
		return width;
	}

	/** @return height of the described world */
	public short getHeight() {
		return height;
	}

	/** @return initial number of fish in the described world */
	public int getInitialFishCount() {
		return initialFishCount;
	}

	/** @return initial number of shark in the described world */
	public int getInitialSharkCount() {
		return initialSharkCount;
	}

	/** @return ticks until a fish reaches maturity and reproduces */
	public short getFishBreedTime() {
		return fishBreedTime;
	}

	/** @return ticks until a shark reaches maturity and reproduces */
	public short getSharkBreedTime() {
		return sharkBreedTime;
	}

	/** @return ticks a shark can survive without eating a fish */
	public short getSharkStarveTime() {
		return sharkStarveTime;
	}

	/**
	 * Sets the width of the described world.
	 *
	 * @param width new width
	 * @return {@code this}
	 */
	public WorldParameters setWidth(short width) {
		this.width = width;
		return this;
	}

	/**
	 * Sets the height of the described world.
	 *
	 * @param height new height
	 * @return {@code this}
	 */
	public WorldParameters setHeight(short height) {
		this.height = height;
		return this;
	}

	/**
	 * Sets the initial number of fish the described world.
	 *
	 * @param initialFishCount initial number of fish
	 * @return {@code this}
	 */
	public WorldParameters setInitialFishCount(int initialFishCount) {
		this.initialFishCount = initialFishCount;
		return this;
	}

	/**
	 * Sets the initial number of shark the described world.
	 *
	 * @param initialSharkCount initial number of shark
	 * @return {@code this}
	 */
	public WorldParameters setInitialSharkCount(int initialSharkCount) {
		this.initialSharkCount = initialSharkCount;
		return this;
	}

	/**
	 * Sets the number of ticks until a fish reaches maturity and reproduces.
	 *
	 * @param fishBreedTime ticks until a fish reaches maturity and reproduces
	 * @return {@code this}
	 */
	public WorldParameters setFishBreedTime(short fishBreedTime) {
		this.fishBreedTime = fishBreedTime;
		return this;
	}

	/**
	 * Sets the number of ticks until a shark reaches maturity and reproduces.
	 *
	 * @param sharkBreedTime ticks until a shark reaches maturity and reproduces
	 * @return {@code this}
	 */
	public WorldParameters setSharkBreedTime(short sharkBreedTime) {
		this.sharkBreedTime = sharkBreedTime;
		return this;
	}

	/**
	 * Sets the number of ticks a shark can go without eating a fish.
	 *
	 * @param sharkStarveTime ticks a shark can go without eating a fish
	 * @return {@code this}
	 */
	public WorldParameters setSharkStarveTime(short sharkStarveTime) {
		this.sharkStarveTime = sharkStarveTime;
		return this;
	}

	/**
	 * Verify the sanity of the parameters. Throws {@link IllegalArgumentException} if the parameters are not
	 * consistent.
	 */
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
