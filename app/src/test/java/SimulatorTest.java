/*
 * SimulatorTest.java is part of Wa-Tor (C) 2016 by Dirk Gassen.
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

import org.junit.Assert;
import org.junit.Test;

import com.dirkgassen.wator.simulator.Simulator;
import com.dirkgassen.wator.simulator.WorldInspector;

/**
 * @author dirk.
 */
public class SimulatorTest {

	@Test
	public void testOneFishMoves() {
		Simulator simulator = new Simulator(
				5 /* width */,
				5 /* height */,
				(short) 2 /* fish reproduction age */,
				(short) 2 /* shark reproduction age */,
				(short) 2 /* shark max hunger */);
		simulator.setFish(2, 2, (short) 1);

		simulator.tick();

		//   0   1   2   3   4
		// +---+---+---+---+---+
		// |   |   |   |   |   | 0
		// +---+---+---+---+---+
		// |   | F'| F'| F'|   | 1
		// +---+---+---+---+---+
		// |   | F'| F | F'|   | 2
		// +---+---+---+---+---+
		// |   | F'| F'| F'|   | 3
		// +---+---+---+---+---+
		// |   |   |   |   |   | 4
		// +---+---+---+---+---+

		WorldInspector world = simulator.getWorldToPaint();
		int emptyCellCount = 0;
		int fishCellNo = -1;
		do {
			if (world.isShark()) {
				Assert.fail("Didn't expect a shark in the waters");
			} else if (world.isFish()) {
				int no = world.getCurrentPosition();
				Assert.assertEquals("Found more than one fish", fishCellNo, -1);
				Assert.assertTrue("Found fish in wrong location",
						   no ==  6 || no ==  7 || no ==  8
						|| no == 11 || no == 12 || no == 13
					    || no == 16 || no == 17 || no == 18
				);
				fishCellNo = no;
			} else {
				emptyCellCount++;
			}
		} while(world.moveToNext() != WorldInspector.MOVE_RESULT.RESET);
		Assert.assertFalse("There should be one fish", fishCellNo == -1);
		Assert.assertEquals("Wrong number of empty cells", 24, emptyCellCount);
	}

	@Test
	public void testFishReproduce() {
		Simulator simulator = new Simulator(
				3 /* width */,
				3 /* height */,
				(short) 2 /* fish reproduction age */,
				(short) 2 /* shark reproduction age */,
				(short) 2 /* shark max hunger */);
		simulator.setFish(1, 1);

		for (int tickNo = 0; tickNo < 6; tickNo++) {
			simulator.tick();
		}

		WorldInspector world = simulator.getWorldToPaint();
		int fishCount = 0;
		do {
			if (world.isFish()) {
				fishCount++;
				Assert.assertEquals("Wrong fish reproduction age", world.getFishAge(), 1);
			}
		} while (world.moveToNext() != WorldInspector.MOVE_RESULT.RESET);
		Assert.assertEquals("Unexpected number of fish", 8, fishCount);
		try {
		} finally {
			simulator.releaseWorldToPaint();
		}
	}

	@Test
	public void testOneSharkFullOfFishMoves() {
		Simulator simulator = new Simulator(
				3 /* width */,
				3 /* height */,
				(short) 2 /* fish reproduction age */,
				(short) 2 /* shark reproduction age */,
				(short) 2 /* shark max hunger */);
		for (int x = 0; x < 3; x++) {
			for (int y = 0; y < 3; y++) {
				if (x == 1 && y == 1) {
					simulator.setShark(x, y, (short) 1, (short) 1);
				} else {
					simulator.setFish(x, y, (short) 1);
				}
			}
		}

		simulator.tick();

		WorldInspector world = simulator.getWorldToPaint();
		try {
			int sharkCellNo = -1;
			int emptyCellNo = -1;
			do {
				if (world.isShark()) {
					Assert.assertEquals("Found more than one shark", sharkCellNo, -1);
					sharkCellNo = world.getCurrentPosition();
				} else if (world.isFish()) {
					Assert.assertTrue("Fish should have aged", world.getFishAge() > 1);
				} else {
					Assert.assertEquals("Found more than one empty cell", emptyCellNo, -1);
					emptyCellNo = world.getCurrentPosition();
				}
			} while (world.moveToNext() != WorldInspector.MOVE_RESULT.RESET);
			Assert.assertFalse("There should be one empty cell", emptyCellNo == -1);
			Assert.assertFalse("There should still be one shark", sharkCellNo == -1);
			Assert.assertNotEquals("Shark should have moved", 4, sharkCellNo);
			Assert.assertEquals("Shark should not be hungry", 1, world.getSharkHunger(sharkCellNo));
			Assert.assertEquals("Shark should have aged", 2, world.getSharkAge(sharkCellNo));
		} finally {
			simulator.releaseWorldToPaint();
		}
	}

//	private int calculateNeightbours(int x, int y, int[] neighbours, int worldWidth, int worldHeight, int worldSize) {
//		int no = x + y * worldWidth;
//		neighbours[0] = no - 1; // left
//		neighbours[1] = neighbours[0] - worldWidth; // top left
//		neighbours[2] = neighbours[1] + 1;           // top
//		neighbours[3] = neighbours[2] + 1;           // top right
//		neighbours[4] = neighbours[3] + worldWidth; // right
//		neighbours[5] = neighbours[4] + worldWidth; // bottom right
//		neighbours[6] = neighbours[5] - 1;           // bottom
//		neighbours[7] = neighbours[6] - 1;           // bottom left
//		if (x == 0) {
//			neighbours[0] += worldWidth;
//			neighbours[1] += worldWidth;
//			neighbours[7] += worldWidth;
//		} else if (x == worldWidth - 1) {
//			neighbours[3] -= worldWidth;
//			neighbours[4] -= worldWidth;
//			neighbours[5] -= worldWidth;
//		}
//		if (y == 0) {
//			neighbours[1] += worldSize;
//			neighbours[2] += worldSize;
//			neighbours[3] += worldSize;
//		} else if (y == worldHeight - 1) {
//			neighbours[5] -= worldSize;
//			neighbours[6] -= worldSize;
//			neighbours[7] -= worldSize;
//		}
//		return no;
//	}
//
//	private int calculateNeightbours2(int x, int y, int[] neighbours, int worldWidth, int worldHeight, int worldSize) {
//		int left   = x == 0               ? x - 1 + worldWidth  : x - 1;
//		int right  = x == worldWidth  - 1 ? x + 1 - worldWidth  : x + 1;
//		int top    = y == 0               ? y - 1 + worldHeight : y - 1;
//		int bottom = y == worldHeight - 1 ? y + 1 - worldHeight : y + 1;
//		neighbours[0] =  left +      y * worldWidth; // left
//		neighbours[1] =  left +    top * worldWidth; // top left
//		neighbours[2] =     x +    top * worldWidth; // top
//		neighbours[3] = right +    top * worldWidth; // top right
//		neighbours[4] = right +      y * worldWidth; // right
//		neighbours[5] = right + bottom * worldWidth; // bottom right
//		neighbours[6] =     x + bottom * worldWidth; // bottom
//		neighbours[7] = left  + bottom * worldWidth; // bottom left
//		return x + y * worldWidth;
//	}
//
//	private int calculateNeightbours4(int x, int y, int[] neighbours, int worldWidth, int worldHeight, int worldSize) {
//		final int left;
//		final int right;
//		final int top;
//		final int bottom;
//		if (x == 0) {
//			left = x - 1 + worldWidth;
//			right = x + 1;
//		} else if (x == worldWidth - 1) {
//			left = x - 1;
//			right = x + 1 - worldWidth;
//		} else {
//			left = x - 1;
//			right = x + 1;
//		}
//		if (y == 0) {
//			top = y - worldWidth + worldSize;
//			bottom = y + worldWidth;
//		} else if (y == worldHeight - 1) {
//			top = y - worldWidth;
//			bottom = y + worldWidth - worldSize;
//		} else {
//			top = y - worldWidth;
//			bottom = y + worldWidth;
//		}
//		neighbours[0] = left + y * worldWidth;       // left
//		neighbours[1] = left + top * worldWidth;     // top left
//		neighbours[2] = x + top * worldWidth;        // top
//		neighbours[3] = right + top * worldWidth;    // top right
//		neighbours[4] = right + y * worldWidth;      // right
//		neighbours[5] = right + bottom * worldWidth; // bottom right
//		neighbours[6] = x + bottom * worldWidth;     // bottom
//		neighbours[7] = left + bottom * worldWidth;  // bottom left
//		return x + y * worldWidth;
//	}
//
//
//	@Test
//	public void runCalcNeightbours1() {
//		int neighbours[] = new int[8];
//		int width = 100;
//		int height = 100;
//		int size = width * height;
//		for (int no = 0; no < 10000; no++) {
//			for (int x = 0; x < width; x++) {
//				for (int y = 0; y < height; y++) {
//					calculateNeightbours(x, y, neighbours, width, height, size);
//				}
//			}
//		}
//	}
//
//	@Test
//	public void runCalcNeightbours2() {
//		int neighbours[] = new int[8];
//		int width = 100;
//		int height = 100;
//		int size = width * height;
//		for (int no = 0; no < 10000; no++) {
//			for (int x = 0; x < width; x++) {
//				for (int y = 0; y < height; y++) {
//					calculateNeightbours2(x, y, neighbours, width, height, size);
//				}
//			}
//		}
//	}
//
//	@Test
//	public void runCalcNeightbours4() {
//		int neighbours[] = new int[8];
//		int width = 100;
//		int height = 100;
//		int size = width * height;
//		for (int no = 0; no < 10000; no++) {
//			for (int x = 0; x < width; x++) {
//				for (int y = 0; y < height; y++) {
//					calculateNeightbours4(x, y, neighbours, width, height, size);
//				}
//			}
//		}
//	}

}
