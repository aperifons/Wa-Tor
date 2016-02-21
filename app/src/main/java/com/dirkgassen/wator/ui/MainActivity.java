/*
 * MainActivity.java is part of Wa-Tor (C) 2016 by Dirk Gassen.
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

package com.dirkgassen.wator.ui;

import java.util.HashSet;
import java.util.Set;

import com.dirkgassen.wator.R;
import com.dirkgassen.wator.simulator.Simulator;
import com.dirkgassen.wator.simulator.SimulatorRunnable;
import com.dirkgassen.wator.simulator.WorldObserver;
import com.dirkgassen.wator.simulator.WorldHost;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;

/**
 * @author dirk.
 */
public class MainActivity extends AppCompatActivity implements WorldHost, SimulatorRunnable.SimulatorRunnableObserver {

	private static final String FISH_AGE_KEY = "fishAge";
	private static final String SHARK_AGE_KEY = "sharkAge";
	private static final String SHARK_HUNGER_KEY = "sharkHunger";
	private static final String FISH_POSITIONS_X_KEY = "fishPositionsX";
	private static final String FISH_POSITIONS_Y_KEY = "fishPositionsY";
	private static final String FISH_REPRODUCTION_AGE_KEY = "fishReproductionAge";
	private static final String SHARK_REPRODUCTION_AGE_KEY = "sharkReproductionAge";
	private static final String SHARK_MAX_HUNGER_KEY = "fishMaxHunger";
	private static final String SHARK_POSITIONS_X_KEY = "sharkPositionsX";
	private static final String SHARK_POSITIONS_Y_KEY = "sharkPositionsY";
	private static final String WORLD_WIDTH_KEY = "worldWidth";
	private static final String WORLD_HEIGHT_KEY = "worldHeight";

	private static final short FISH_REPRODUCTION_AGE = 12;
	private static final short SHARK_REPRODUCTION_AGE = 7;
	private static final short SHARK_MAX_HUNGER = 18;
	private static final short WORLD_WIDTH = 200;
	private static final short WORLD_HEIGHT = 200;
	private static final int INITIAL_FISH = 600;
	private static final int INITIAL_SHARK = 350;

	private final Set<WorldObserver> worldObservers = new HashSet<WorldObserver>();

	private Simulator simulator;

	private SimulatorRunnable simulatorRunnable;

	private Thread worldUpdateNotifierThread;

	private void worldUpdated() {
		synchronized (worldObservers) {
			if (worldObservers.size() > 0) {
				Simulator.WorldInspector world = simulator.getWorldToPaint();
				try {
					for (WorldObserver observer : worldObservers) {
						observer.worldUpdated(world);
						world.reset();
					}
					if (Log.isLoggable("Wa-Tor", Log.VERBOSE)) { Log.v("Wa-Tor", "Fish: " + world.getFishCount() + "; sharks: " + world.getSharkCount()); }
					if (world.getSharkCount() + world.getFishCount() == 0 || world.getFishCount() == world.getWorldWidth() * world.getWorldHeight()) {
						simulatorRunnable.stopTicking();
					}
				} finally {
					world.release();
				}
			}
		}
	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		Simulator.WorldInspector world = simulator.getWorldToPaint();
		int fishCount = world.getFishCount();
		int sharkCount = world.getSharkCount();
		short[] fishAge = new short[fishCount];
		short[] fishPosX = new short[fishCount];
		short[] fishPosY = new short[fishCount];
		short[] sharkAge = new short[sharkCount];
		short[] sharkHunger = new short[sharkCount];
		short[] sharkPosX = new short[sharkCount];
		short[] sharkPosY = new short[sharkCount];
		int fishNo = 0;
		int sharkNo = 0;
		do {
			if (world.isFish()) {
				fishAge[fishNo] = world.getFishAge();
				fishPosX[fishNo] = world.getCurrentX();
				fishPosY[fishNo++] = world.getCurrentY();
			} else if (world.isShark()) {
				sharkAge[sharkNo] = world.getSharkAge();
				sharkHunger[sharkNo] = world.getSharkHunger();
				sharkPosX[sharkNo] = world.getCurrentX();
				sharkPosY[sharkNo++] = world.getCurrentY();
			}
		} while (world.moveToNext() != Simulator.WORLD_INSPECTOR_MOVE_RESULT.RESET);
		outState.putShortArray(FISH_AGE_KEY, fishAge);
		outState.putShortArray(FISH_POSITIONS_X_KEY, fishPosX);
		outState.putShortArray(FISH_POSITIONS_Y_KEY, fishPosY);
		outState.putShortArray(SHARK_AGE_KEY, sharkAge);
		outState.putShortArray(SHARK_HUNGER_KEY, sharkHunger);
		outState.putShortArray(SHARK_POSITIONS_X_KEY, sharkPosX);
		outState.putShortArray(SHARK_POSITIONS_Y_KEY, sharkPosY);
		outState.putShort(WORLD_WIDTH_KEY, world.getWorldWidth());
		outState.putShort(WORLD_HEIGHT_KEY, world.getWorldHeight());
		outState.putShort(FISH_REPRODUCTION_AGE_KEY, world.getFishReproduceAge());
		outState.putShort(SHARK_REPRODUCTION_AGE_KEY, world.getSharkReproduceAge());
		outState.putShort(SHARK_MAX_HUNGER_KEY, world.getMaxSharkHunger());
		outState.putShort(SHARK_MAX_HUNGER_KEY, world.getMaxSharkHunger());
		super.onSaveInstanceState(outState);
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main_layout);
		Toolbar myToolbar = (Toolbar) findViewById(R.id.main_toolbar);
		setSupportActionBar(myToolbar);

		if (savedInstanceState == null) {
			simulator = new Simulator(
					WORLD_WIDTH, WORLD_HEIGHT,
					FISH_REPRODUCTION_AGE,
					SHARK_REPRODUCTION_AGE, SHARK_MAX_HUNGER,
					INITIAL_FISH, INITIAL_SHARK
			);
		} else {
			simulator = new Simulator(
					savedInstanceState.getShort(WORLD_WIDTH_KEY), savedInstanceState.getShort(WORLD_HEIGHT_KEY),
					savedInstanceState.getShort(FISH_REPRODUCTION_AGE_KEY),
					savedInstanceState.getShort(SHARK_REPRODUCTION_AGE_KEY),
					savedInstanceState.getShort(SHARK_MAX_HUNGER_KEY)
			);
			short[] fishAge = savedInstanceState.getShortArray(FISH_AGE_KEY);
			if (fishAge != null) {
				short[] fishPosX = savedInstanceState.getShortArray(FISH_POSITIONS_X_KEY);
				if (fishPosX != null) {
					short[] fishPosY = savedInstanceState.getShortArray(FISH_POSITIONS_Y_KEY);
					if (fishPosY != null) {
						for (int fishNo = 0; fishNo < fishAge.length; fishNo++) {
							simulator.setFish(fishPosX[fishNo], fishPosY[fishNo], fishAge[fishNo]);
						}
					}
				}
			}
			short[] sharkAge = savedInstanceState.getShortArray(SHARK_AGE_KEY);
			if (sharkAge != null) {
				short[] sharkHunger = savedInstanceState.getShortArray(SHARK_HUNGER_KEY);
				if (sharkHunger != null) {
					short[] sharkPosX = savedInstanceState.getShortArray(SHARK_POSITIONS_X_KEY);
					if (sharkPosX != null) {
						short[] sharkPosY = savedInstanceState.getShortArray(SHARK_POSITIONS_Y_KEY);
						if (sharkPosY != null) {
							for (int sharkNo = 0; sharkNo < sharkAge.length; sharkNo++) {
								simulator.setShark(sharkPosX[sharkNo], sharkPosY[sharkNo], sharkAge[sharkNo], sharkHunger[sharkNo]);
							}
						}
					}
				}
			}
		}

		simulatorRunnable = new SimulatorRunnable(simulator);
		simulatorRunnable.registerSimulatorRunnableObserver(this);
	}

	@Override
	protected void onResume() {
		super.onResume();

		worldUpdateNotifierThread = new Thread(getString(R.string.worldUpdateNotifierThreadName)) {
			@Override
			public void run() {
				if (Log.isLoggable("Wa-Tor", Log.DEBUG)) { Log.d("Wa-Tor", "Entering world update notifier thread"); }
				try {
					while (Thread.currentThread() == worldUpdateNotifierThread) {
						long startUpdate = System.currentTimeMillis();
						if (Log.isLoggable("Wa-Tor", Log.VERBOSE)) { Log.v("Wa-Tor", "Notifying observers of world update"); }
						worldUpdated();
						if (Log.isLoggable("Wa-Tor", Log.VERBOSE)) { Log.v("Wa-Tor", "Notifying observers took " + (System.currentTimeMillis() - startUpdate) + " ms; waiting for next update"); }
						synchronized (this) {
							wait();
						}
					}
				} catch(InterruptedException e){
					if (Log.isLoggable("Wa-Tor", Log.DEBUG)) { Log.d("Wa-Tor", "World update notifier thread got interrupted"); }
					synchronized (this) {
						worldUpdateNotifierThread = null;
					}
				}
				if (Log.isLoggable("Wa-Tor", Log.DEBUG)) { Log.d("Wa-Tor", "Exiting world update notifier thread"); }
			}
		};
		worldUpdateNotifierThread.start();

		Thread simulatorThread = new Thread(simulatorRunnable, getString(R.string.simulatorThreadName));
		simulatorThread.start();
	}

	@Override
	protected void onPause() {
		super.onPause();
		synchronized (this) {
			Thread t = worldUpdateNotifierThread;
			worldUpdateNotifierThread = null;
			if (t != null) {
				t.interrupt();
			}
			simulatorRunnable.stopTicking();
		}
	}

	@Override
	public void registerSimulatorObserver(WorldObserver newObserver) {
		synchronized (worldObservers) {
			worldObservers.add(newObserver);
		}

	}

	@Override
	public void unregisterSimulatorObserver(WorldObserver goneObserver) {
		synchronized (worldObservers) {
			worldObservers.remove(goneObserver);
		}
	}

	@Override
	synchronized public void simulatorUpdated(Simulator simulator) {
		if (worldUpdateNotifierThread != null) {
			//noinspection SynchronizeOnNonFinalField
			synchronized (worldUpdateNotifierThread) {
				worldUpdateNotifierThread.notify();
			}
		}
	}
}
