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
 * Simulator for a Wa-Tor world. The simulator contains data structures for the world, the parameters with which fish
 * and shark move and reproduce and it contains methods to advance the simulation by one.
 *
 * This simulator stores the state of the world in an array of {@code short}. Each cell contains the information
 * about the state of the world cell:
 * <ul>
 *     <li>If the value is zero then the cell is empty.</li>
 *     <li>If the value is positive then the cell contains a shark. The upper half of the value is the shark hunger and
 *         the lower half is the maturity of the shark (time since last reproduction in ticks).</li>
 *     <li>If the value is negative then the cell contains a fish. The absolute value is the maturity of the fish
 *          (time since last reproduction in ticks).</li>
 * </ul>
 *
 * The time in the world is measured in ticks. To progress the world to the next tick call {@link #tick()} or
 * {@link #tick(int)}.
 *
 * To access the current world a {@link WorldInspector} can be requested via {@link #getWorldToPaint()}. A world
 * inspector stores a snapshot of the world at the time it was requested. The world can progress while the inspector
 * is in use but the inspector won't be affected.
 *
 * Note that to avoid excessive object creation (which also puts strain on the garbage collector) {@link WorldInspector#release()}
 * when the world inspector is no longer being used.
 */
// Note that this class can tick the world with multiple threads. The idea behind it is to separate access to the data
// instead of synchronizing the threads. The world is processed in multiple chunks. The chunks are divided into two
// sets such that each chunk in each set does not affect any other chunk in the same set.
//
// Imagine this:
// For two threads divide the world into four chunks, e.g.:
//   A: cells 0 to 99,
//   B: cells 100 to 199,
//   C: cells 200 to 299,
//   and D: cells 300 to 399.
// The first set would contain chunks A and C while the second set would contain B and D. Now chunks A and C can
// be calculated simultaneously since none of the cells affected would be anywhere near chunk C. The same is true
// for chunks B and D. Therefore, we can let two threads calculate chunks A and B, then wait until both are done and
// then let two threads calculate chunks B and D.
//
// To minimize memory allocation we use WorldCalculatorState objects for each thread and a calculatorThread array.
// The calculatorThread array contains CalculatorThread thread objects that in their "run" loop block until they receive
// a world chunk to calculate. Details see in the CalculatorThread class documentation.
final public class Simulator {

	/**
	 * Allows to inspect the state of the world of the simulator. A world inspector must be requested with
	 * {@link Simulator#getWorldToPaint()} and must be released after usage with {@link #release()}.
	 *
	 * A world inspector has a current position, which can either be set with {@link #moveTo(int, int)} or can be
	 * advanced to the next cell with {@link #moveToNext()}. The inspector can progress through the world with
	 * {@link #moveToNext()}, which moves the current position to the next cell in the world. If there are no more
	 * cells in the current row the current position is moved to the first cell in the next row and if there are no
	 * more rows the inspector resets to the first cell in the first row.
	 */
	public class WorldInspector {

		/** Indicates that {@link #moveToNext()} moved to the next cell in the same row */
		public static final int NEXT_CELL = 0;

		/** Indicates that {@link #moveToNext()} moved to the first cell in the next row */
		public static final int NEXT_ROW = 1;

		/** Indicates that {@link #moveToNext()} moved to the first cell in the first row */
		public static final int RESET = 2;

		/** Number of fish in this world */
		private int fishCount;

		/** Number of shark in this world */
		private int sharkCount;

		/** Refers to the world */
		private short[] world;

		/** Stores the current position in the world */
		private int currentNo;

		/**
		 * Set the current position of this inspector to a particular location
		 *
		 * @param x horizontal coordinate (column) of the new current position
		 * @param y vertical coordinate (row) of the new current position
		 */
		final public void moveTo(int x, int y) {
			currentNo = x + y * worldWidth;
		}

		/**
		 * Moves the current position to the next cell. If there are no more cells in the current row the current
		 * position is set to the first cell in the next row. If there are no more rows the current position is reset
		 * to the first cell in the first row
		 * @return <ul>
		 *     <li>{@link #NEXT_CELL}</li> if the current position was set the next cell in a row
		 *     <li>{@link #NEXT_ROW}</li> if the current position was moved to the first cell in the next row
		 *     <li>{@link #RESET}</li> if the current position was reset to the first cell in the first row
		 * </ul>
		 */
		final public int moveToNext() {
			if (currentNo == world.length - 1) {
				currentNo = 0;
				return RESET;
			}
			currentNo++;
			return currentNo % worldWidth == 0 ? NEXT_ROW : NEXT_CELL;
		}

		/**
		 * Returns the current position in the world. All cells are numbered sequentially starting with zero
		 * @return current position the world
		 */
		final public int getCurrentPosition() {
			return currentNo;
		}

		/** @return horizontal coordinate (row) of the current position */
		final public short getCurrentX() {
			return (short) (currentNo % worldWidth);
		}

		/** @return vertical coordinate (row) of the current position */
		final public short getCurrentY() {
			return (short) (currentNo / worldWidth);
		}

		/** @return {@code true} if there is a fish at the current position */
		final public boolean isFish() {
			return world[currentNo] < 0;
		}

		/** @return {@code true} if there is a shark at the current position */
		final public boolean isShark() {
			return world[currentNo] > 0;
		}

		/** @return {@code true} if the cell at the current position is empty */
		final public boolean isEmpty() {
			return world[currentNo] == 0;
		}

		/** @return {@code true} if there is a fish at the current position */
		final public int getFishCount() {
			return fishCount;
		}

		/** @return {@code true} if there is a shark at the current position */
		final public int getSharkCount() {
			return sharkCount;
		}

		/**
		 * @return age (maturity) of the fish at the current location
		 * (or 0 if there is no fish at the current location)
		 */
		final public short getFishAge() {
			if (world[currentNo] >= 0) {
				return 0;
			}
			return (short) -world[currentNo];
		}

		/**
		 * @return age (maturity) of the shark at the current location
		 * (or 0 if there is no shark at the current location)
		 */
		final public short getSharkAge() {
			if (world[currentNo] <= 0) {
				return 0;
			}
			return (short) (world[currentNo] & 255);
		}

		/**
		 * @return hunger of the shark at the current location
		 * (or 0 if there is no shark at the current location)
		 */
		final public short getSharkHunger() {
			if (world[currentNo] <= 0) {
				return 0;
			}
			return (short) (world[currentNo] >> 8);
		}

		/**
		 * Returns the age (maturity) of a fish at a given location
		 * @param no location
		 * @return age (maturity) of the fish at the specified location
		 * (or 0 if there is no fish at the current location)
		 */
		final public short getFishAge(int no) {
			if (world[no] >= 0) {
				return 0;
			}
			return (short) -world[no];
		}

		/**
		 * Returns the age (maturity) of a shark at a given location
		 * @param no location
		 * @return age (maturity) of the shark at the specified location
		 * (or 0 if there is no shark at the current location)
		 */
		final public short getSharkAge(int no) {
			if (world[no] <= 0) {
				return 0;
			}
			return (short) (world[no] & 255);
		}

		/**
		 * Returns the hunger of a shark at a given location
		 * @param no location
		 * @return hunger of the shark at the specified location
		 * (or 0 if there is no shark at the current location)
		 */
		final public short getSharkHunger(int no) {
			if (world[no] <= 0) {
				return 0;
			}
			return (short) (world[no] >> 8);
		}

		/**
		 * @param x horizontal coordinate (column)
		 * @param y vertical coordinate (row)
		 * @return age (maturity) of the fish at the specified location
		 * (or 0 if there is no fish at the current location)
		 */
		final public short getFishAge(int x, int y) {
			return getFishAge(x + y * worldWidth);
		}

		/**
		 * @param x horizontal coordinate (column)
		 * @param y vertical coordinate (row)
		 * @return age (maturity) of the shark at the specified location
		 * (or 0 if there is no shark at the current location)
		 */
		final public short getSharkAge(int x, int y) {
			return getSharkAge(x + y * worldWidth);
		}

		/**
		 * @param x horizontal coordinate (column)
		 * @param y vertical coordinate (row)
		 * @return hunger of the shark at the specified location
		 * (or 0 if there is no shark at the current location)
		 */
		final public short getSharkHunger(int x, int y) {
			return getSharkHunger(x + y * worldWidth);
		}

		/** Resets the current position to the first cell in the first row. */
		final public void reset() {
			currentNo = 0;
		}

		/** @return width of the world */
		final public short getWorldWidth() {
			return worldWidth;
		}

		/** @return height of the world */
		final public short getWorldHeight() {
			return worldHeight;
		}

		/** @return number of ticks until a fish breeds (reproduces) */
		final public short getFishBreedTime() {
			return fishBreedTime;
		}

		/** @return number of ticks until a shark breeds (reproduces) */
		final public short getSharkBreedTime() {
			return sharkBreedTime;
		}

		/** @return starve time (maximum hunger) of a shark */
		final public short getSharkStarveTime() {
			return sharkStarveTime;
		}

		/**
		 * Initializes this inspector with the given world
		 * @param newWorld new world to set this inspector to
		 */
		private void setWorldToPaint(short[] newWorld) {
			if (world == null || world.length != newWorld.length) {
				world = new short[newWorld.length];
			}
			System.arraycopy(newWorld, 0, world, 0, newWorld.length);
			fishCount = sharkCount = 0;
			for (short worldCell : world) {
				if (worldCell < 0) {
					fishCount++;
				} else if (worldCell > 0) {
					sharkCount++;
				}
			}
			this.currentNo = 0;
		}

		/**
		 * Releases this inspector. This method must be called whenever the inspector is no longer being used.
		 */
		public void release() {
			synchronized (Simulator.this) {
				for (int no = 0; no < worldInspectors.length; no++) {
					if (worldInspectors[no] == null) {
						worldInspectors[no] = this;
						return;
					}
				}
			}
		}

	}

	/**
	 * Stores information that can be used when calculating the next world state.
	 *
	 * In the future this class may be used in threads that do calculations in parallel.
	 */
	final class WorldCalculatorState {
		/** Start of the chunk of the world to be calculated */
		private int start;

		/**
		 * End of the chunk of the world to be calculated. This field stores the index of the cell after the last one
		 * to be calculated.
		 */
		private int end;

		/** A random number generator */
		public final Random random;

		/** An array that contains the indices of the neighbor cells of a current cell */
		public final int neighbours[];

		/** An array that can store all indices of the neighbor cells that contain fish */
		public final int fishNeighbourPos[];

		/** An array that can store all indices of the neighbor cells that contain shark */
		public final int emptyNeighbourPos[];

		/**
		 * Creates a new initialized object
		 * @param allowDiagonally allow for diagonal movement ({@code true}) or only for horizontal and vertical
		 *                        movement (@code false)?
		 */
		WorldCalculatorState(boolean allowDiagonally) {
			random = new Random();
			neighbours = new int[allowDiagonally ? 8 : 4];
			fishNeighbourPos = new int[neighbours.length];
			emptyNeighbourPos = new int[neighbours.length];
		}

		/**
		 * Sets the chunk bounds.
		 * @param start first cell to work on
		 * @param end cell after the last cell to work on
		 */
		public void setChunk(int start, int end) {
			this.start = start;
			this.end = end;
		}
	}

	/**
	 * Internal counter to nicely identify the calculator threads
	 */
	static private int calculatorThreadCounter = 0;

	/**
	 * Special {@link Thread} that calculates chunks of the next world. Each of these threads has a
	 * {@link WorldCalculatorState} that defines the range of the chunk this particular thread should work on.
	 *
	 * To make a thread work on a chunk call {@link #startCalculatingWorld(int, int)}. To wait for the calculation
	 * to be complete call {@link #waitForWorkDone()}.
	 *
	 * The class has one of the states: {@link #STATE_STARTING}, {@link #STATE_WAITING_FOR_WORK},
	 * {@link #STATE_WORKING} or {@link #STATE_DEAD}.
	 *
	 * The {@link #run()} loop is a never ending loop that blocks at the beginning (transitioning from
	 * {@link #STATE_STARTING} to {@link #STATE_WAITING_FOR_WORK}. Calling {@link #startCalculatingWorld(int, int)}
	 * changes the internal {@link WorldCalculatorState} to the chunk to calculate and then resumes the {@link #run()}
	 * loop, which makes the thread transition to {@link #STATE_WORKING}. Once the chunk is calculated the state changes
	 * to {@link #STATE_WAITING_FOR_WORK} again. The {@link #run()} loop exits when an exception happens (including
	 * {@link InterruptedException}).
	 */
	class CalculatorThread extends Thread {

		/** The thread is in this state if it just started up but isn't waiting for work yet */
		final public static int STATE_STARTING = 0;

		/** The thread is blocked and waiting to work on the next chunk */
		final public static int STATE_WAITING_FOR_WORK = 1;

		/** The thread is working on a chunk of the world */
		final public static int STATE_WORKING = 2;

		/** For some reason the thread has excited (aka is dead). The thread will never work again. */
		final public static int STATE_DEAD = 3;

		/** Defines the next chunk/current chunk to work on */
		final WorldCalculatorState worldCalculatorState;

		/** State of this thread */
		private int state = STATE_STARTING;

		/** Mutex to organize access to {@link #state}. */
		final private Object stateMutex = new Object();

		/**
		 * Schedule to work on a chunk.
		 *
		 * @param start first cell to work on
		 * @param end cell after the last cell to work on
		 * @return {@code true} if the work has been scheduled; {@code false} if this thread is {@link #STATE_DEAD}
		 * @throws InterruptedException if the thread got interrupted while waiting
		 */
		public boolean startCalculatingWorld(int start, int end) throws InterruptedException {
			synchronized (stateMutex) {
				if (state == STATE_DEAD) {
					return false;
				}
				while (state != STATE_WAITING_FOR_WORK) {
					stateMutex.wait();
				}
				worldCalculatorState.setChunk(start, end);
				state = STATE_WORKING;
				stateMutex.notifyAll();
				return true;
			}
		}

		/**
		 * Waits until the thread is no longer working. This method also waits if the thread is currently in the
		 * state {@link #STATE_STARTING} (which is threaded by this method like {@link #STATE_WORKING}.
		 *
		 * @return {@code true} if the thread is waiting for more work; {@code false} if the thread is
		 * {@link #STATE_DEAD}
		 * @throws InterruptedException if the thread got interrupted while waiting
		 */
		public boolean waitForWorkDone() throws InterruptedException {
			synchronized (stateMutex) {
				if (state == STATE_DEAD) {
					return false;
				}
				while (state == STATE_STARTING || state == STATE_WORKING) {
					stateMutex.wait();
				}
			}
			return true;
		}

		/** Main loop of this thread. This thread loops forever (or better until the thread is interrupted). */
		@Override
		public void run() {
			try {
				while (true) {
					synchronized (stateMutex) {
						state = STATE_WAITING_FOR_WORK;
						stateMutex.notifyAll();
						while (state != STATE_WORKING) {
							stateMutex.wait();
						}
					}
					calculateNextWorld(worldCalculatorState);
				}
			} catch (InterruptedException e) {
				e.printStackTrace();
				// Nothing to do here
			} finally {
				synchronized (stateMutex) {
					state = STATE_DEAD;
					stateMutex.notifyAll();
				}
			}
		}

		/**
		 * Creates a new calculator thread.
		 *
		 * @param allowDiagonally should fish/shark be allowed to move diagonally?
		 */
		public CalculatorThread(boolean allowDiagonally) {
			super("Wa - Tor World Tick Thread" + calculatorThreadCounter);
			calculatorThreadCounter++;
			worldCalculatorState = new WorldCalculatorState(allowDiagonally);
		}
	}

	/** Maximum possible width of a world */
	public static final short MAX_WORLD_WIDTH = Short.MAX_VALUE;

	/** Maximum possible height of a world */
	public static final short MAX_WORLD_HEIGHT = Short.MAX_VALUE;

	/** Maximum possible breed time for a fish */
	public static final short MAX_FISH_BREED_TIME = Short.MAX_VALUE;

	/** Maximum possible breed time for a shark */
	public static final short MAX_SHARK_BREED_TIME = Short.MAX_VALUE >> 9;

	/** Maximum possible starve time for a shark */
	public static final short MAX_SHARK_STARVE_TIME = Short.MAX_VALUE >> 9;

	/** Current state of the world */
	private short[] currentWorld;

	/** Stores the state of the world while calculating the next state */
	private short[] nextWorld;

	/** Stores a flag whether a cell in {@link #nextWorld} has been processed during world calculation */
	private final boolean[] cellProcessed;

	/** World calculators that can be used during world calculation */
	private WorldCalculatorState mainThreadWorldCalculatorState = new WorldCalculatorState(true /* allow diagonally */);

	/** The additional threads that perform world calculations besides the thread that calls {@link #tick(int)} */
	private CalculatorThread calculatorThreads[];

	/**
	 * Sets up the array of threads for world calculation.
	 *
	 * @param threads number of threads to use to calculate the next world
	 */
	private void setupCalculatorThreads(int threads) {
		if (calculatorThreads == null || calculatorThreads.length != (threads-1)) {
			if (calculatorThreads != null) {
				for (Thread t : calculatorThreads) {
					if (t != null) {
						t.interrupt();
					}
				}
			}

			calculatorThreads = new CalculatorThread[threads - 1];
			for (int no = 0; no < calculatorThreads.length; no++) {
				calculatorThreads[no] = new CalculatorThread(true /* allow diagonally */);
				calculatorThreads[no].start();
			}
		}
	}

	/**
	 * Cache of allocated {@link WorldInspector} objects. Whenever a world inspector is requested one from this
	 * array is returned (after being initialized appropriately) and the index in this array that contained a reference
	 * to it is set to {@code null}. When {@link WorldInspector#release()} is called that inspector is returned into
	 * this array.
	 */
	final private WorldInspector worldInspectors[] = new WorldInspector[] {
			new WorldInspector(),
			null,
			null,
			null,
			null,
			null,
			null,
			null
	};

	/** Width of the world */
	private final short worldWidth;

	/** Height of the world */
	private final short worldHeight;

	/** Ticks until a fish breeds (reproduces) */
	private final short fishBreedTime;

	/** Ticks until a shark breeds (reproducess) */
	private final short sharkBreedTime;

	/** Ticks until a shark must eat before it starves */
	private final short sharkStarveTime;

	/** @return width of the world */
	final public int getWorldWidth() {
		return worldWidth;
	}

	/** @return height of the world */
	final public int getWorldHeight() {
		return worldHeight;
	}


	/**
	 * Creates a new simulator with the given parameters
	 *
	 * @param worldParameters describes the parameters for the new simulator
	 */
	public Simulator(WorldParameters worldParameters) {
		worldParameters.verify();
		this.worldWidth = worldParameters.getWidth();
		this.worldHeight = worldParameters.getHeight();
		int worldSize = worldWidth * worldHeight;
		this.fishBreedTime = worldParameters.getFishBreedTime();
		this.sharkBreedTime = worldParameters.getSharkBreedTime();
		this.sharkStarveTime = worldParameters.getSharkStarveTime();

		this.currentWorld = new short[worldSize];
		this.nextWorld = new short[worldSize];
		this.cellProcessed = new boolean[worldSize];

		Random random = new Random();
		int count = worldParameters.getInitialFishCount();
		while (count-- > 0) {
			int cellNo;
			do {
				cellNo = random.nextInt(worldSize);
			} while (currentWorld[cellNo] != 0);
			// Note: age is 1-based!
			currentWorld[cellNo] = (short) (-random.nextInt(fishBreedTime) - 1);
		}
		count = worldParameters.getInitialSharkCount();
		while (count-- > 0) {
			int cellNo;
			do {
				cellNo = random.nextInt(worldSize);
			} while (currentWorld[cellNo] != 0);
			// Note: age and hunger are 1-based!
			currentWorld[cellNo] = (short) (((random.nextInt(sharkStarveTime) + 1) << 8) | (random.nextInt(sharkBreedTime) + 1));
		}
	}

	/**
	 * Puts a fish into the world at the specified location. The fish will have an age (maturity) of 1.
	 *
	 * @param x horizontal coordinate (column) of the location of the new fish
	 * @param y vertical coordinate (row) of the location of the new fish
	 */
	final public void setFish(int x, int y) {
		setFish(x, y, (short) 1);
	}

	/**
	 * Puts a shark into the world at the specified location. The shark will have an age (maturity) of 1 and a hunger of
	 * 1.
	 *
	 * @param x horizontal coordinate (column) of the location of the new shark
	 * @param y vertical coordinate (row) of the location of the new shark
	 */
	final public void setShark(int x, int y) {
		setShark(x, y, (short) 1, (short) 1);
	}

	/**
	 * Puts a fish into the world at the specified location with a specific age (maturity).
	 *
	 * @param x        horizontal coordinate (column) of the location of the new fish
	 * @param y        vertical coordinate (row) of the location of the new fish
	 * @param breedAge age (maturity) of the new fish
	 */
	synchronized final public void setFish(int x, int y, short breedAge) {
		if (x < 0 || x >= worldWidth) {
			throw new IllegalArgumentException("X coordinate " + x + " is out of bounds (width = " + worldWidth + ")");
		}
		if (y < 0 || y >= worldHeight) {
			throw new IllegalArgumentException("Y coordinate " + y + " is out of bounds (height = " + worldHeight + ")");
		}
		if (breedAge <= 0) {
			throw new IllegalArgumentException("Fish cannot have negative or zero reproduction age");
		}
		if (breedAge > fishBreedTime) {
			throw new IllegalArgumentException("Fish reproduction age " + breedAge + " too old (max = " + fishBreedTime + ")");
		}
		currentWorld[x + y * worldWidth] = (short) -breedAge;
	}

	/**
	 * Puts a shark into the world at the specified location with a specific age (maturity) and hunger.
	 *
	 * @param x             horizontal coordinate (column) of the location of the new shark
	 * @param y             vertical coordinate (row) of the location of the new shark
	 * @param breedAge      age (maturity) of the new shark
	 * @param currentHunger age hunger of the new shark
	 */
	synchronized final public void setShark(int x, int y, short breedAge, short currentHunger) {
		if (x < 0 || x >= worldWidth) {
			throw new IllegalArgumentException("X coordinate " + x + " is out of bounds (width = " + worldWidth + ")");
		}
		if (y < 0 || y >= worldHeight) {
			throw new IllegalArgumentException("Y coordinate " + y + " is out of bounds (height = " + worldHeight + ")");
		}
		if (breedAge <= 0) {
			throw new IllegalArgumentException("Shark cannot have negative or zero breed age");
		}
		if (currentHunger <= 0) {
			throw new IllegalArgumentException("Shark cannot have negative or zero hunger");
		}
		if (breedAge > sharkBreedTime + 1) {
			throw new IllegalArgumentException("Shark breed time " + breedAge + " too old (max = " + sharkBreedTime + ")");
		}
		currentWorld[x + y * worldWidth] = (short) ((currentHunger << 8) | breedAge);
	}


	/**
	 * Updates the {@link WorldCalculatorState#neighbours} array for the given cell number.
	 * @param calculatorState calculator state to update
	 * @param no cell number
	 */
	private void calculateNeighbours(WorldCalculatorState calculatorState, int no) {
		final int x = no % worldWidth;
		final int y = no / worldWidth;
		final int left   = x == 0               ? x - 1 + worldWidth  : x - 1;
		final int right  = x == worldWidth  - 1 ? x + 1 - worldWidth  : x + 1;
		final int top    = y == 0               ? y - 1 + worldHeight : y - 1;
		final int bottom = y == worldHeight - 1 ? y + 1 - worldHeight : y + 1;
		if (calculatorState.neighbours.length == 4) {
			calculatorState.neighbours[0] = left + y * worldWidth; // left
			calculatorState.neighbours[1] = x + top * worldWidth; // top
			calculatorState.neighbours[2] = right + y * worldWidth; // right
			calculatorState.neighbours[3] = x + bottom * worldWidth; // bottom
		} else {
			calculatorState.neighbours[0] = left + y * worldWidth; // left
			calculatorState.neighbours[1] = left + top * worldWidth; // top left
			calculatorState.neighbours[2] = x + top * worldWidth; // top
			calculatorState.neighbours[3] = right + top * worldWidth; // top right
			calculatorState.neighbours[4] = right + y * worldWidth; // right
			calculatorState.neighbours[5] = right + bottom * worldWidth; // bottom right
			calculatorState.neighbours[6] = x + bottom * worldWidth; // bottom
			calculatorState.neighbours[7] = left + bottom * worldWidth; // bottom left
		}
	}

	/**
	 * Calculates the next state of a chunk of the world as specified by by the {@code calculatorState}.
	 *
	 * @param calculatorState defines the chunk of the world to calculate
	 */
	private void calculateNextWorld(WorldCalculatorState calculatorState) {
		int chunkSize = calculatorState.end - calculatorState.start;
		int offset = calculatorState.random.nextInt(chunkSize);
		int delta = calculatorState.random.nextInt(4) + 11;
		while (true) {
			int startOffset = offset;
			while (cellProcessed[calculatorState.start + offset]) {
				offset = (offset + 1) % chunkSize;
				if (offset == startOffset) {
					return; // all cells in our range processed
				}
			}
			int no = calculatorState.start + offset;
			if (nextWorld[no] < 0) {
				// Fish
				calculateNeighbours(calculatorState, no);
				calculateFish(calculatorState, no);
			} else if (nextWorld[no] > 0) {
				// Shark
				calculateNeighbours(calculatorState, no);
				calculateShark(calculatorState, no);
			}
			cellProcessed[no] = true;
			offset = (offset + delta) % chunkSize;
		}
	}

	/**
	 * Handle a fish at the given location.
	 *
	 * @param calculatorState {@link WorldCalculatorState} to use
	 * @param no location of the fish to calculate
	 */
	private void calculateFish(WorldCalculatorState calculatorState, int no) {
		int emptyNeighbours = 0;
		for (int neighbourNo : calculatorState.neighbours) {
			if (nextWorld[neighbourNo] == 0) {
				// empty
				calculatorState.emptyNeighbourPos[emptyNeighbours++] = neighbourNo;
			}
		}
		short fishAge = nextWorld[no];
		if (emptyNeighbours > 0) {
			int newNo = calculatorState.emptyNeighbourPos[
					emptyNeighbours == 1 ? 0 : calculatorState.random.nextInt(emptyNeighbours)
					];
			if (fishAge <= -fishBreedTime) {
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
			nextWorld[no] = (short) (fishAge <= fishBreedTime ? -1 : (fishAge - 1));
		}
	}

	/**
	 * Handle a shark at the given location.
	 *
	 * @param calculatorState {@link WorldCalculatorState} to use
	 * @param no              location of the shark to calculate
	 */
	private void calculateShark(WorldCalculatorState calculatorState, int no) {
		int emptyNeighbours = 0;
		int fishNeighbours = 0;
		for (int neighbourNo : calculatorState.neighbours) {
			if (nextWorld[neighbourNo] == 0) {
				// empty
				calculatorState.emptyNeighbourPos[emptyNeighbours++] = neighbourNo;
			} else if (nextWorld[neighbourNo] < 0) {
				// fish
				calculatorState.fishNeighbourPos[fishNeighbours++] = neighbourNo;
			}
		}
		if (fishNeighbours > 0) {
			// we can eat a fish :) so ignore the hunger
			short currentBreedTime = (short) (nextWorld[no] & 255);
			int newNo = calculatorState.fishNeighbourPos[fishNeighbours == 1 ? 0 : calculatorState.random.nextInt(fishNeighbours)];
			final short compositeHunger = 1 << 8;
			if (currentBreedTime > sharkBreedTime) {
				// eat fish, reproduce and move
				nextWorld[newNo] = compositeHunger | 1;
				nextWorld[no] = compositeHunger | 1;
			} else {
				// just eat the fish, increase current breed time and move
				nextWorld[newNo] = (short) (compositeHunger | (currentBreedTime + 1));
				nextWorld[no] = 0;
			}
			cellProcessed[newNo] = true;
		} else {
			// can't eat a fish :/ so we need to check if we starve first
			short hunger = (short) (nextWorld[no] >> 8);
			if (hunger >= sharkStarveTime) {
				// die
				nextWorld[no] = 0;
			} else {
				// starve a bit...
				hunger++;
				short currentBreedTime = (short) (nextWorld[no] & 255);
				if (emptyNeighbours > 0) {
					// ... and move
					int newNo = calculatorState.emptyNeighbourPos[emptyNeighbours == 1 ? 0 : calculatorState.random.nextInt(emptyNeighbours)];
					if (currentBreedTime >= sharkBreedTime) {
						// reproduce and move
						nextWorld[newNo] = (short) ((hunger << 8) | 1);
						nextWorld[no] = (short) ((1 << 8) | 1);
					} else {
						// just move
						nextWorld[newNo] = (short) ((hunger << 8) | (currentBreedTime + 1));
						nextWorld[no] = 0;
					}
					cellProcessed[newNo] = true;
				} else {
					// can't move, just age
					if (currentBreedTime < sharkBreedTime) {
						currentBreedTime++;
					} else {
						currentBreedTime = 1;
					}
					nextWorld[no] = (short) ((hunger << 8) | currentBreedTime);
				}
			}
		}
	}

	/** Calculate the next tick of the world */
	final public void tick() {
		tick(1);
	}

	/**
	 * Calculate the next tick of the world with the specified number of threads.
	 *
	 * Note: this method currently ignores the parameter {@code threads} and only uses one thread.
	 * @param threads number of threads to use to calculate the tick (currently ignored)
	 */
	final public void tick(int threads) {
		// Copy from current to next
		synchronized (this) {
			System.arraycopy(currentWorld, 0, nextWorld, 0, currentWorld.length);
		}

		// Mark all cells as unprocessed
		Arrays.fill(cellProcessed, false);


		// Calculate work chunk size
		int chunkSize = nextWorld.length / (2 * threads);
		if (chunkSize < 100) {
			chunkSize = 100;
			threads = 1 / chunkSize * nextWorld.length / 2;
			if (threads < 1) {
				threads = 1;
				chunkSize = nextWorld.length;
			} else {
				chunkSize = nextWorld.length / (2 * threads);
			}
		}

		if (threads == 1) {

			// Single threaded: just calculate the whole world start to end
			mainThreadWorldCalculatorState.setChunk(0, nextWorld.length);
			calculateNextWorld(mainThreadWorldCalculatorState);

		} else {

			// Multithreaded: need to calculate the chunk size and then schedule the work to all the threads
			//                we have set up

			// Set up calculator threads
			setupCalculatorThreads(threads);

			// Do the tick
			try {
				for (int passNo = 0; passNo < 2; passNo++) {
					for (int chunkNo = 0; chunkNo < threads; chunkNo++) {
						int chunkStart = (2 * chunkNo + passNo) * chunkSize;
						int chunkEnd = (passNo == 1 && chunkNo == threads - 1) ? nextWorld.length : chunkStart + chunkSize;

						if (chunkNo == calculatorThreads.length) {
							// Run on this thread
							mainThreadWorldCalculatorState.setChunk(chunkStart, chunkEnd);
							calculateNextWorld(mainThreadWorldCalculatorState);
						} else {
							calculatorThreads[chunkNo].startCalculatingWorld(chunkStart, chunkEnd);
						}
					}
					for (CalculatorThread t: calculatorThreads) {
						t.waitForWorkDone();
					}
				}
			} catch (InterruptedException e) {
				// Nothing to do here
			}
		}

		synchronized(this) {
			short[] tempWorld = currentWorld;
			currentWorld = nextWorld;
			nextWorld = tempWorld;
		}
	}

	/**
	 * @return a snapshot of the current world in a {@link WorldInspector}.
	 */
	final synchronized public WorldInspector getWorldToPaint() {
		for (int no = 0; no < worldInspectors.length; no++) {
			if (worldInspectors[no] != null) {
				worldInspectors[no].setWorldToPaint(currentWorld);
				try {
					return worldInspectors[no];
				} finally {
					worldInspectors[no] = null;
				}
			}
		}
		WorldInspector newInspector = new WorldInspector();
		newInspector.setWorldToPaint(currentWorld);
		return newInspector;
	}

}
