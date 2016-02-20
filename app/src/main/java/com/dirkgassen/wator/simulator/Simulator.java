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
final public class Simulator {

	public enum WORLD_INSPECTOR_MOVE_RESULT {
		NEXT_CELL,
		NEXT_ROW,
		RESET
	}

	/**
	 * @author dirk.
	 */
	public class WorldInspector {

		public final int fishCount;
		public final int sharkCount;
		private final short[] world;
		private int currentNo;

		final public void moveTo(int x, int y) {
			currentNo = x + y * worldWidth;
		}

		final public WORLD_INSPECTOR_MOVE_RESULT moveToNext() {
			if (currentNo == world.length - 1) {
				currentNo = 0;
				return WORLD_INSPECTOR_MOVE_RESULT.RESET;
			}
			currentNo++;
			return currentNo % worldWidth == 0 ? WORLD_INSPECTOR_MOVE_RESULT.NEXT_ROW : WORLD_INSPECTOR_MOVE_RESULT.NEXT_CELL;
		}

		final public int getCurrentPosition() {
			return currentNo;
		}

		final public short getCurrentX() {
			return (short) (currentNo % worldWidth);
		}

		final public short getCurrentY() {
			return (short) (currentNo / worldWidth);
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

		final public void reset() {
			currentNo = 0;
		}

		final public short getWorldWidth() {
			return worldWidth;
		}

		final public short getWorldHeight() {
			return worldHeight;
		}

		final public short getFishReproduceAge() {
			return fishReproduceAge;
		}

		final public short getSharkReproduceAge() {
			return sharkReproduceAge;
		}

		final public short getMaxSharkHunger() {
			return maxSharkHunger;
		}

		protected WorldInspector(short[] world, int fishCount, int sharkCount) {
			this.world = world;
			this.currentNo = 0;
			this.fishCount = fishCount;
			this.sharkCount = sharkCount;
		}

		public void release() {
			synchronized(Simulator.this) {
				if (worldPainters == 0) {
					throw new IllegalAccessError("Nothing to release");
				}
				worldPainters--;
				if (worldPainters == 0) {
					if (spareWorld == null) {
						spareWorld = worldToPaint;
					}
					worldToPaint = null;
				}
			}
		}
	}

	class TickWorker implements Runnable {
		private boolean active;
		private int no;
		private int end;
		private Thread tickWorkerThread;

		public synchronized boolean isActive() {
			return active;
		}

		public synchronized void stop() {
			tickWorkerThread = null;
		}

		public synchronized void work(int no, int end) {
			this.no = no;
			this.end = end;
			notify();
		}

		@Override
		public void run() {
			try {
				tickWorkerThread = Thread.currentThread();
				while (tickWorkerThread == Thread.currentThread()) {
					synchronized (this) {
						wait();
						active = true;
					}
					calculateNextWorld(no, end);
				}
			} catch (InterruptedException e) {
				// Do nothing
			}

		}
	}

	private short[] currentWorld;
	private short[] nextWorld;
	private short[] worldToPaint;
	private short[] spareWorld;
	private int worldPainters;
	private final boolean[] cellProcessed;
	private TickWorker[] tickWorkers;

	private final short worldWidth;
	private final short worldHeight;
	private final short fishReproduceAge;
	private final short sharkReproduceAge;
	private final short maxSharkHunger;

	private int currentFishCount;
	private int currentSharkCount;

	final public int getWorldWidth() {
		return worldWidth;
	}

	final public int getWorldHeight() {
		return worldHeight;
	}


	public Simulator(short worldWidth, short worldHeight, short fishReproduceAge, short sharkReproduceAge, short maxSharkHunger) {
		this(worldWidth, worldHeight, fishReproduceAge, sharkReproduceAge, maxSharkHunger, 0, 0);
	}

	public Simulator(short worldWidth, short worldHeight, short fishReproduceAge, short sharkReproduceAge, short maxSharkHunger, int initialFishCount, int initialSharkCount) {
		int worldSize = worldWidth * worldHeight;
		this.worldWidth = worldWidth;
		this.worldHeight = worldHeight;
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
			// Note: age is 1-based!
			currentWorld[cellNo] = (short) (-random.nextInt(fishReproduceAge) - 1);
		}
		while (initialSharkCount-- > 0) {
			int cellNo;
			do {
				cellNo = random.nextInt(worldSize);
			} while (currentWorld[cellNo] != 0);
			// Note: age and hunger are 1-based!
			currentWorld[cellNo] = (short) (((random.nextInt(maxSharkHunger) + 1) << 8) | (random.nextInt(sharkReproduceAge) + 1));
		}
	}

	final public void setFish(int x, int y) {
		setFish(x, y, (short) 1);
	}

	final public void setShark(int x, int y) {
		setShark(x, y, (short) 1, (short) 1);
	}

	final public void setFish(int x, int y, short currentReproduceAge) {
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

	final public void setShark(int x, int y, short currentReproduceAge, short currentHunger) {
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

	private void calculateNeightbours(int no, int[] neighbours) {
		final int x = no % worldWidth;
		final int y = no / worldWidth;
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
	}

	private void calculateNextWorld(int no, int end) {
		Random random = new Random();
		int neighbours[] = new int[8];
		int fishNeighbourPos[] = new int[8];
		int emptyNeighbourPos[] = new int[8];
		for (; no < end; no++)  {
			calculateNeightbours(no, neighbours);
			if (!cellProcessed[no]) {
				if (nextWorld[no] < 0) {
					// Fish
					calculateFish(random, no, neighbours, emptyNeighbourPos);
				} else if (nextWorld[no] > 0) {
					// Sharl
					calculateShark(random, no, neighbours, emptyNeighbourPos, fishNeighbourPos);
				}
				cellProcessed[no] = true;
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
			if (reproduceAge > sharkReproduceAge) {
				// eat fish, reproduce and move
				nextWorld[newNo] = (1 << 8) + 1;
				nextWorld[no] = (1 << 8) + 1;
			} else {
				// just eat the fish, increase reproduce age and move
				nextWorld[newNo] = (short) ((1 << 8) | (reproduceAge + 1));
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
						// just move
						nextWorld[newNo] = (short) ((hunger << 8) | (reproduceAge + 1));
						nextWorld[no] = 0;
					}
					cellProcessed[newNo] = true;
				} else {
					// can't move, just age
					if (reproduceAge < sharkReproduceAge) {
						reproduceAge++;
					}
					nextWorld[no] = (short) ((hunger << 8) | reproduceAge);
				}
			}
		}
	}

	final public void tick() {
		tick(1);
	}

	final public void tick(int threads) {
		long start = System.currentTimeMillis();
		// Copy from current to next
		synchronized (this) {
			System.arraycopy(currentWorld, 0, nextWorld, 0, currentWorld.length);
		}

		// Mark all cells as unprocessed
		Arrays.fill(cellProcessed, false);

		// Do the tick
		if (threads == 1) {
			calculateNextWorld(0, nextWorld.length);
		} else if (threads == 2) {
			try {
				final int bounds[] = new int[] {
						0,
						nextWorld.length / 4,
						nextWorld.length / 2,
						nextWorld.length / 4 * 3
				};
				Thread otherThread = new Thread() {
					@Override
					public void run() {
						calculateNextWorld(bounds[2], bounds[3]);
					}
				};
				otherThread.start();
				calculateNextWorld(0, bounds[1]);
				otherThread.join();

				otherThread = new Thread() {
					@Override
					public void run() {
						calculateNextWorld(bounds[3], nextWorld.length);
					}
				};
				otherThread.start();
				calculateNextWorld(bounds[1], bounds[2]);
				otherThread.join();
			} catch (InterruptedException e) {
				// TODO: Handle?
			}
		}

		synchronized(this) {
			currentFishCount = currentSharkCount = 0;
			for (short worldCell : nextWorld) {
				if (worldCell < 0) {
					currentFishCount++;
				} else if (worldCell > 0) {
					currentSharkCount++;
				}
			}
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

	final synchronized public WorldInspector getWorldToPaint() {
		worldPainters++;
		if (worldToPaint == null) {
			worldToPaint = currentWorld;
		}
		return new WorldInspector(worldToPaint, currentFishCount, currentSharkCount);
	}

}
