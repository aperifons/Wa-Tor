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
import com.dirkgassen.wator.simulator.WorldParameters;

/**
 * @author dirk.
 */
public class SimulatorTest {

	@Test
	public void testOneFishMoves() {
		Simulator simulator = new Simulator(
				new WorldParameters()
						.setWidth((short) 5)
						.setHeight((short) 5)
						.setFishBreedTime((short) 2)
						.setSharkBreedTime((short) 2)
						.setSharkStarveTime((short) 2)
						.setInitialFishCount(0)
						.setInitialSharkCount(0)
		);
		simulator.setFish(2, 2, (short) 1);

		simulator.tick(16);

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
		} while(world.moveToNext() != Simulator.WorldInspector.RESET);
		Assert.assertFalse("There should be one fish", fishCellNo == -1);
		Assert.assertEquals("Wrong number of empty cells", 24, emptyCellCount);
	}

	@Test
	public void testFishBreed() {
		Simulator simulator = new Simulator(
				new WorldParameters()
						.setWidth((short) 3)
						.setHeight((short) 3)
						.setFishBreedTime((short) 2)
						.setSharkBreedTime((short) 2)
						.setSharkStarveTime((short) 2)
						.setInitialFishCount(0)
						.setInitialSharkCount(0)
		);
		simulator.setFish(1, 1);

		for (int tickNo = 0; tickNo < 6; tickNo++) {
			simulator.tick(16);
		}

		Simulator.WorldInspector world = simulator.getWorldToPaint();
		try {
			int fishCount = 0;
			do {
				if (world.isFish()) {
					fishCount++;
					Assert.assertEquals("Wrong fish reproduction age", world.getFishAge(), 1);
				}
			} while (world.moveToNext() != Simulator.WorldInspector.RESET);
			Assert.assertEquals("Unexpected number of fish", 8, fishCount);
		} finally {
			world.release();
		}
	}

	@Test
	public void testSharkStarve() {
		Simulator simulator = new Simulator(
				new WorldParameters()
						.setWidth((short) 3)
						.setHeight((short) 3)
						.setFishBreedTime((short) 2)
						.setSharkBreedTime((short) 10)
						.setSharkStarveTime((short) 3)
						.setInitialFishCount(0)
						.setInitialSharkCount(0)
		);
		simulator.setShark(0, 0, (short) 1, (short) 3);
		simulator.setShark(1, 1, (short) 1, (short) 1);
		simulator.setShark(2, 2, (short) 1, (short) 2);

		for (int tickNo = 2; tickNo >= 0; tickNo--) {
			simulator.tick(16);

			Simulator.WorldInspector world = simulator.getWorldToPaint();
			try {
				int sharkCount = 0;
				do {
					if (world.isFish()) {
						Assert.fail("Didn't expect a fish");
					} else if (world.isShark()) {
						sharkCount++;
					}
				} while (world.moveToNext() != Simulator.WorldInspector.RESET);
				Assert.assertEquals("Unexpected number of shark", tickNo, sharkCount);
			} finally {
				world.release();
			}
		}
	}

	@Test
	public void testSharkCantMoveAndStarve() {
		Simulator simulator = new Simulator(
				new WorldParameters()
						.setWidth((short) 3)
						.setHeight((short) 3)
						.setFishBreedTime((short) 2)
						.setSharkBreedTime((short) 10)
						.setSharkStarveTime((short) 1)
						.setInitialFishCount(0)
						.setInitialSharkCount(0)
		);
		for (int x = 0; x < 3; x++) {
			for (int y = 0; y < 3; y++) {
				simulator.setShark(x, y, (short) 1, (short) 1);
			}
		}

		simulator.tick(16);

		Simulator.WorldInspector world = simulator.getWorldToPaint();
		try {
			int sharkCount = 0;
			do {
				if (world.isFish()) {
					Assert.fail("Didn't expect a fish");
				} else if (world.isShark()) {
					sharkCount++;
				}
			} while (world.moveToNext() != Simulator.WorldInspector.RESET);
			Assert.assertEquals("Unexpected number of shark", 0, sharkCount);
		} finally {
			world.release();
		}
	}

	@Test
	public void testOneSharkFullOfFishMoves() {
		Simulator simulator = new Simulator(
				new WorldParameters()
						.setWidth((short) 3)
						.setHeight((short) 3)
						.setFishBreedTime((short) 2)
						.setSharkBreedTime((short) 2)
						.setSharkStarveTime((short) 2)
						.setInitialFishCount(0)
						.setInitialSharkCount(0)
		);
		for (int x = 0; x < 3; x++) {
			for (int y = 0; y < 3; y++) {
				if (x == 1 && y == 1) {
					simulator.setShark(x, y, (short) 1, (short) 1);
				} else {
					simulator.setFish(x, y, (short) 1);
				}
			}
		}

		simulator.tick(16);

		Simulator.WorldInspector world = simulator.getWorldToPaint();
		try {
			int sharkCellNo = -1;
			int emptyCellNo = -1;
			do {
				if (world.isShark()) {
					Assert.assertEquals("Found more than one shark", sharkCellNo, -1);
					sharkCellNo = world.getCurrentPosition();
				} else if (world.isEmpty()) {
					Assert.assertEquals("Found more than one empty cell", emptyCellNo, -1);
					emptyCellNo = world.getCurrentPosition();
				}
			} while (world.moveToNext() != Simulator.WorldInspector.RESET);
			Assert.assertFalse("There should be one empty cell", emptyCellNo == -1);
			Assert.assertFalse("There should still be one shark", sharkCellNo == -1);
			Assert.assertNotEquals("Shark should have moved", 4, sharkCellNo);
			Assert.assertEquals("Shark should not be hungry", 1, world.getSharkHunger(sharkCellNo));
			Assert.assertEquals("Shark should have aged", 2, world.getSharkAge(sharkCellNo));
		} finally {
			world.release();
		}
	}

	@Test
	public void testSharkAndFishCount() throws InterruptedException {
		final Simulator simulator = new Simulator(
				new WorldParameters()
						.setWidth((short) 100)
						.setHeight((short) 100)
						.setFishBreedTime((short) 2)
						.setSharkBreedTime((short) 4)
						.setSharkStarveTime((short) 3)
						.setInitialFishCount(2000)
						.setInitialSharkCount(1500)
		);

		for (int tickNo = 0; tickNo < 100; tickNo++) {
			final Simulator.WorldInspector world = simulator.getWorldToPaint();
			try {
				Thread backgroundTick = new Thread() {
					@Override
					public void run() {
						simulator.tick(16);
					}
				};
				backgroundTick.start();
				int fish = 0;
				int shark = 0;
				do {
					if (world.isFish()) {
						fish++;
					} else if (world.isShark()) {
						shark++;
					}
				} while (world.moveToNext() != Simulator.WorldInspector.RESET);
				Assert.assertEquals("Unexpected number of fish after " + tickNo + " ticks", fish, world.getFishCount());
				Assert.assertEquals("Unexpected number of shark after " + tickNo + " ticks", shark, world.getSharkCount());
				backgroundTick.join();
			} finally {
				world.release();
			}
		}
	}

	@Test
	public void testSharkStarveNoMovement() {
		final Simulator simulator = new Simulator(
				new WorldParameters()
				.setWidth((short) 3)
				.setHeight((short) 3)
				.setFishBreedTime((short) 2)
				.setSharkBreedTime((short) 4)
				.setSharkStarveTime((short) 3)
				.setInitialFishCount(0)
				.setInitialSharkCount(0)
		);

		for (int x = 0; x < 3; x++) {
			for (int y = 0; y < 3; y++) {
				simulator.setShark(x, y, (short) 1, (short) 1);
			}
		}

		simulator.tick(16);
		Simulator.WorldInspector world = simulator.getWorldToPaint();
		try {
			do {
				Assert.assertTrue("World should be full of shark", world.isShark());
				Assert.assertEquals("Wrong shark age", 2, world.getSharkAge());
			} while (world.moveToNext() != Simulator.WorldInspector.RESET);
		} finally {
			world.release();
		}

		simulator.tick(16);
		world = simulator.getWorldToPaint();
		try {
			do {
				Assert.assertTrue("World should be full of shark", world.isShark());
				Assert.assertEquals("Wrong shark age", 3, world.getSharkAge());
			} while (world.moveToNext() != Simulator.WorldInspector.RESET);
		} finally {
			world.release();
		}


		simulator.tick(16);
		world = simulator.getWorldToPaint();
		try {
			do {
				Assert.assertTrue("World should be empty", world.isEmpty());
			} while (world.moveToNext() != Simulator.WorldInspector.RESET);
		} finally {
			world.release();
		}
	}

	@Test
	public void createWorldTest() {
		final Simulator simulator = new Simulator(
				new WorldParameters()
						.setWidth((short) 100)
						.setHeight((short) 100)
						.setFishBreedTime((short) 2)
						.setSharkBreedTime((short) 4)
						.setSharkStarveTime((short) 3)
						.setInitialFishCount(500)
						.setInitialSharkCount(500)
		);

		Simulator.WorldInspector world = simulator.getWorldToPaint();
		try {
			int fishCount = 0;
			int sharkCount = 0;
			int emptyCount = 0;
			do {
				if (world.isFish()) {
					short fishAge = world.getFishAge();
					Assert.assertTrue("Unexpected fish age " + fishAge, fishAge > 0 && fishAge <= 2);
					Assert.assertEquals("Shouldn't have returned a shark age", 0, world.getSharkAge());
					Assert.assertEquals("Shouldn't have returned shark hunger", 0, world.getSharkHunger());
					fishCount++;
				} else if (world.isShark()) {
					short sharkAge = world.getSharkAge();
					short sharkHunger = world.getSharkHunger();
					Assert.assertTrue("Unexpected shark age " + sharkAge, sharkAge > 0 && sharkAge <= 4);
					Assert.assertTrue("Unexpected shark hunger " + sharkHunger, sharkHunger > 0 && sharkHunger <= 3);
					Assert.assertEquals("Shouldn't have returned a fish age", 0, world.getFishAge());
					sharkCount++;
				} else {
					Assert.assertTrue("Hm, cell should be either fish, or shark or empty", world.isEmpty());
					emptyCount++;
				}
			} while (world.moveToNext() != Simulator.WorldInspector.RESET);
			Assert.assertEquals("Total number of individuals + empty cells should have been equal to world size", world.getWorldWidth() * world.getWorldHeight(), fishCount + sharkCount + emptyCount);
			Assert.assertEquals("Unexpected number of fish", 500, fishCount);
			Assert.assertEquals("Unexpected number of shark", 500, sharkCount);
			Assert.assertEquals("Unexpected fish count reported by world", 500, world.getFishCount());
			Assert.assertEquals("Unexpected shark count reported by world", 500, world.getSharkCount());
		} finally {
			world.release();
		}
	}

}
