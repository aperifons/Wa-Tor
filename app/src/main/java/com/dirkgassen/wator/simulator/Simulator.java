/*
 * Simulator.java is part of Wa-Tor (C) 2016 by Dirk Gassen.
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

import java.util.Arrays;
import java.util.Random;

/**
 * @author dirk.
 */
public class Simulator {

	private short[] currentWorld;
	private short[] nextWorld;
	private short[] worldToPaint;
	private short[] spareWorld;
	private final boolean[] cellProcessed;

	public int getWorldWidth() {
		return worldWidth;
	}

	public int getWorldHeight() {
		return worldHeight;
	}

	private final int worldWidth;
	private final int worldHeight;
	private final int worldSize;
	private final short fishReproduceAge;
	private final short sharkReproduceAge;
	private final short maxSharkHunger;


	public Simulator(int worldWidth, int worldHeight, short fishReproduceAge, short sharkReproduceAge, short maxSharkHunger) {
		this(worldWidth, worldHeight, fishReproduceAge, sharkReproduceAge, maxSharkHunger, 0, 0);
	}

	public Simulator(int worldWidth, int worldHeight, short fishReproduceAge, short sharkReproduceAge, short maxSharkHunger, int initialFishCount, int initialSharkCount) {
		this.worldWidth = worldWidth;
		this.worldHeight = worldHeight;
		this.worldSize = worldWidth * worldHeight;
		if (initialFishCount + initialSharkCount > worldSize) {
			throw new IllegalArgumentException("Can't have " + initialFishCount + " fish and " + initialSharkCount + " sharks in a world with " + worldSize + " cells");
		}
		if (sharkReproduceAge > Short.MAX_VALUE >> 9) {
			throw new IllegalArgumentException("Shark reproduction age " + sharkReproduceAge + " too large");
		}
		if (maxSharkHunger > Short.MAX_VALUE >> 9) {
			throw new IllegalArgumentException("Shark max hunger " + maxSharkHunger + " too large");
		}
		this.fishReproduceAge = fishReproduceAge;
		this.sharkReproduceAge = sharkReproduceAge;
		this.maxSharkHunger = maxSharkHunger;
		this.currentWorld = new short[worldSize];
		this.nextWorld = new short[worldSize];
		this.spareWorld = new short[worldSize];
		this.cellProcessed = new boolean[worldSize];
		Random random = new Random();
		while (initialFishCount-- > 0) {
			int cellNo;
			do {
				cellNo = random.nextInt(worldSize);
			} while (currentWorld[cellNo] != 0);
			currentWorld[cellNo] = (short) -random.nextInt(fishReproduceAge);
		}
		while (initialSharkCount-- > 0) {
			int cellNo;
			do {
				cellNo = random.nextInt(worldSize);
			} while (currentWorld[cellNo] != 0);
			currentWorld[cellNo] = (short) ((random.nextInt(maxSharkHunger) << 8) | random.nextInt(sharkReproduceAge));
		}
	}

	public void setFish(int x, int y) {
		setFish(x, y, (short) 1);
	}

	public void setShark(int x, int y) {
		setShark(x, y, (short) 1, (short) 1);
	}

	public void setFish(int x, int y, short currentReproduceAge) {
		if (x < 0 || x >= worldWidth) {
			throw new IllegalArgumentException("X coordinate " + x + " is out of bounds (width = " + worldWidth + ")");
		}
		if (y < 0 || y >= worldHeight) {
			throw new IllegalArgumentException("Y coordinate " + y + " is out of bounds (height = " + worldHeight + ")");
		}
		if (currentReproduceAge <= 0) {
			throw new IllegalArgumentException("Fish cannot have negative or zero reproduction age");
		}
		if (currentReproduceAge > fishReproduceAge) {
			throw new IllegalArgumentException("Fish reproduction age " + currentReproduceAge + " too old (max = " + fishReproduceAge + ")");
		}
		currentWorld[x + y * worldWidth] = (short) -currentReproduceAge;
	}

	public void setShark(int x, int y, short currentReproduceAge, short currentHunger) {
		if (x < 0 || x >= worldWidth) {
			throw new IllegalArgumentException("X coordinate " + x + " is out of bounds (width = " + worldWidth + ")");
		}
		if (y < 0 || y >= worldHeight) {
			throw new IllegalArgumentException("Y coordinate " + y + " is out of bounds (height = " + worldHeight + ")");
		}
		if (currentReproduceAge <= 0) {
			throw new IllegalArgumentException("Shark cannot have negative or zero reproduction age");
		}
		if (currentReproduceAge > fishReproduceAge) {
			throw new IllegalArgumentException("Shark reproduction age " + currentReproduceAge + " too old (max = " + sharkReproduceAge + ")");
		}
		currentWorld[x + y * worldWidth] = (short) ((currentHunger << 8) | currentReproduceAge);
	}

	private int calculateNeightbours(int x, int y, int[] neighbours) {
		final int left   = x == 0               ? x - 1 + worldWidth  : x - 1;
		final int right  = x == worldWidth  - 1 ? x + 1 - worldWidth  : x + 1;
		final int top    = y == 0               ? y - 1 + worldHeight : y - 1;
		final int bottom = y == worldHeight - 1 ? y + 1 - worldHeight : y + 1;
		neighbours[0] =  left +      y * worldWidth; // left
		neighbours[1] =  left +    top * worldWidth; // top left
		neighbours[2] =     x +    top * worldWidth; // top
		neighbours[3] = right +    top * worldWidth; // top right
		neighbours[4] = right +      y * worldWidth; // right
		neighbours[5] = right + bottom * worldWidth; // bottom right
		neighbours[6] =     x + bottom * worldWidth; // bottom
		neighbours[7] = left  + bottom * worldWidth; // bottom left
		return x + y * worldWidth;
	}

	private void calculateNextWorld() {
		// 1. age fish and shark
		synchronized(this) {
			System.arraycopy(currentWorld, 0, nextWorld, 0, worldSize);
		}

		Arrays.fill(cellProcessed, false);

		// 2. move (and reproduce)
		Random random = new Random();
		int neighbours[] = new int[8];
		int fishNeighbourPos[] = new int[8];
		int emptyNeighbourPos[] = new int[8];
		for (int x = 0; x < worldWidth; x++) {
			for (int y = 0; y < worldHeight; y++) {
				int no = calculateNeightbours(x, y, neighbours);
				if (!cellProcessed[no]) {
					if (nextWorld[no] < 0) {
						// Fish
						calculateFish(random, no, neighbours, emptyNeighbourPos);
					} else if (nextWorld[no] > 0) {
						// Sharl
						calculateShark(random, no, neighbours, emptyNeighbourPos, fishNeighbourPos);
					}
				}
			}
		}
	}

	private void calculateFish(Random random, int no, int[] neighbours, int emptyNeighbourPos[]) {
		int emptyNeighbours = 0;
		for (int neighbourNo : neighbours) {
			if (nextWorld[neighbourNo] == 0) {
				// empty
				emptyNeighbourPos[emptyNeighbours++] = neighbourNo;
			}
		}
		short fishAge = nextWorld[no];
		if (emptyNeighbours > 0) {
			int newNo = emptyNeighbourPos[
					emptyNeighbours == 1 ? 0 : random.nextInt(emptyNeighbours)
					];
			if (fishAge <= -fishReproduceAge) {
				// reproduce
				nextWorld[newNo] = -1;
				nextWorld[no] = -1;
			} else {
				// just move (and age)
				nextWorld[newNo] = (short) (fishAge - 1);
				nextWorld[no] = 0;
			}
			cellProcessed[newNo] = true;
		} else {
			// can't move but age
			nextWorld[no] = (short) (fishAge <= fishReproduceAge ? -fishReproduceAge : (fishAge - 1));
		}
	}

	private void calculateShark(Random random, int no, int[] neighbours, int emptyNeighbourPos[], int fishNeighbourPos[]) {
		int emptyNeighbours = 0;
		int fishNeighbours = 0;
		for (int neighbourNo : neighbours) {
			if (nextWorld[neighbourNo] == 0) {
				// empty
				emptyNeighbourPos[emptyNeighbours++] = neighbourNo;
			} else if (nextWorld[neighbourNo] < 0) {
				// fish
				fishNeighbourPos[fishNeighbours++] = neighbourNo;
			}
		}
		final short currentCompositeAge = nextWorld[no];
		if (fishNeighbours > 0) {
			// we can eat a fish :) so ignore the hunger
			short reproduceAge = (short) (currentCompositeAge & 255);
			int newNo = fishNeighbourPos[fishNeighbours == 1 ? 0 : random.nextInt(fishNeighbours)];
			// eat fish in target loc: ignore hunger coz this is gonna be reset
			if (reproduceAge > sharkReproduceAge) {
				// eat fish, reproduce and move
				nextWorld[newNo] = 1 + 256;
				nextWorld[no] = 1 + 256;
			} else {
				// just eat the fish, increase reproduce age and move
				nextWorld[newNo] = (short) ((reproduceAge + 1) | (1 << 8));
				nextWorld[no] = 0;
			}
			cellProcessed[newNo] = true;
		} else {
			// can't eat a fish :/ so we need to check if we starve first
			short hunger = (short) ((currentCompositeAge >> 8) & 255);
			if (hunger >= maxSharkHunger) {
				// die
				nextWorld[no] = 0;
			} else {
				hunger = (short) ((hunger + 1) & 127);
				// starve a bit...
				short reproduceAge = (short) (currentCompositeAge & 255);
				if (emptyNeighbours > 0) {
					// ... and move
					int newNo = emptyNeighbourPos[emptyNeighbours == 1 ? 0 : random.nextInt(emptyNeighbours)];
					if (reproduceAge >= sharkReproduceAge) {
						// reproduce and move
						nextWorld[newNo] = (short) ((hunger << 8) | 1);
						nextWorld[no] = (short) ((hunger << 8) | 1);
					} else {
						// just starve
						nextWorld[newNo] = (short) ((hunger << 8) | (reproduceAge + 1));
						nextWorld[no] = 0;
					}
					cellProcessed[newNo] = true;
				} else {
					nextWorld[no] = (short) ((((hunger << 8) & 127)) | (reproduceAge + 1));
				}
			}
		}
	}

	public void tick() {
		calculateNextWorld();
		synchronized(this) {
			if (worldToPaint == currentWorld) {
				currentWorld = nextWorld;
				nextWorld = spareWorld;
				spareWorld = null;
			} else {
				short[] tempWorld = currentWorld;
				currentWorld = nextWorld;
				nextWorld = tempWorld;
			}
		}
	}

	synchronized public WorldInspector getWorldToPaint() {
		if (worldToPaint == null) {
			worldToPaint = currentWorld;
		}
		return new WorldInspector(worldToPaint, worldWidth);
	}

	synchronized public void releaseWorldToPaint() {
		if (spareWorld == null) {
			spareWorld = worldToPaint;
		}
		worldToPaint = null;
	}

}
