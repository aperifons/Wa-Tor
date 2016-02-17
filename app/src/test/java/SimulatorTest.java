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

		Simulator.WorldInspector world = simulator.getWorldToPaint();
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
		} while(world.moveToNext() != Simulator.WORLD_INSPECTOR_MOVE_RESULT.RESET);
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

		Simulator.WorldInspector world = simulator.getWorldToPaint();
		try {
			int fishCount = 0;
			do {
				if (world.isFish()) {
					fishCount++;
					Assert.assertEquals("Wrong fish reproduction age", world.getFishAge(), 1);
				}
			} while (world.moveToNext() != Simulator.WORLD_INSPECTOR_MOVE_RESULT.RESET);
			Assert.assertEquals("Unexpected number of fish", 8, fishCount);
		} finally {
			simulator.releaseWorldToPaint();
		}
	}

	@Test
	public void testSharkStarve() {
		Simulator simulator = new Simulator(
				3 /* width */,
				3 /* height */,
				(short) 2 /* fish reproduction age */,
				(short) 10 /* shark reproduction age */,
				(short) 3 /* shark max hunger */);
		simulator.setShark(0, 0, (short) 1, (short) 3);
		simulator.setShark(1, 1, (short) 1, (short) 1);
		simulator.setShark(2, 2, (short) 1, (short) 2);

		for (int tickNo = 2; tickNo >= 0; tickNo--) {
			simulator.tick();

			Simulator.WorldInspector world = simulator.getWorldToPaint();
			try {
				int sharkCount = 0;
				do {
					if (world.isFish()) {
						Assert.fail("Didn't expect a fish");
					} else if (world.isShark()) {
						sharkCount++;
					}
				} while (world.moveToNext() != Simulator.WORLD_INSPECTOR_MOVE_RESULT.RESET);
				Assert.assertEquals("Unexpected number of shark", tickNo, sharkCount);
			} finally {
				simulator.releaseWorldToPaint();
			}
		}
	}

	@Test
	public void testSharkCantMoveAndStarve() {
		Simulator simulator = new Simulator(
				3 /* width */,
				3 /* height */,
				(short) 2 /* fish reproduction age */,
				(short) 10 /* shark reproduction age */,
				(short) 1 /* shark max hunger */);
		for (int x = 0; x < 3; x++) {
			for (int y = 0; y < 3; y++) {
				simulator.setShark(x, y, (short) 1, (short) 1);
			}
		}

		simulator.tick();

		Simulator.WorldInspector world = simulator.getWorldToPaint();
		try {
			int sharkCount = 0;
			do {
				if (world.isFish()) {
					Assert.fail("Didn't expect a fish");
				} else if (world.isShark()) {
					sharkCount++;
				}
			} while (world.moveToNext() != Simulator.WORLD_INSPECTOR_MOVE_RESULT.RESET);
			Assert.assertEquals("Unexpected number of shark", 0, sharkCount);
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

		Simulator.WorldInspector world = simulator.getWorldToPaint();
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
			} while (world.moveToNext() != Simulator.WORLD_INSPECTOR_MOVE_RESULT.RESET);
			Assert.assertFalse("There should be one empty cell", emptyCellNo == -1);
			Assert.assertFalse("There should still be one shark", sharkCellNo == -1);
			Assert.assertNotEquals("Shark should have moved", 4, sharkCellNo);
			Assert.assertEquals("Shark should not be hungry", 1, world.getSharkHunger(sharkCellNo));
			Assert.assertEquals("Shark should have aged", 2, world.getSharkAge(sharkCellNo));
		} finally {
			simulator.releaseWorldToPaint();
		}
	}

}
