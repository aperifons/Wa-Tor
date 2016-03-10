/*
 * SimulatorRunnable.java is part of Wa-Tor (C) 2016 by Dirk Gassen.
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

import android.util.Log;

/**
 * A class that "ticks" a simulator. This is a {@link Runnable} object that updates a {@link Simulator}
 * with a desired frame rate (see {@link #getTargetFps()}}. If the frame rate cannot be achieved it tries to run
 * as fast as possible (but leaves a couple of ms between each frame).
 */
public class SimulatorRunnable implements Runnable {

	/** Classes should implement this interface if they are interested whenever a simulator tick is finished. */
	public interface SimulatorRunnableObserver {

		/** This method is called whenever one simulator tick is finished */
		void simulatorUpdated(Simulator simulator);

	}

	/** The simulator that should be ticked */
	private final Simulator simulator;

	/** Keeps track of how long on average it took to calculate one tick */
	private final RollingAverage tickDuration = new RollingAverage();

	/** Desired frame rate */
	private int targetFps = 15;

	/** The thread of this {@link Runnable} */
	private Thread simulatorTickThread;

	/**
	 * A list of objects that want to be notified of a finished tick via the {@link SimulatorRunnableObserver}
	 * interface.
	 *
	 * Observers can be added with {@link #registerSimulatorRunnableObserver(SimulatorRunnableObserver)} and removed
	 * with {@link #unregisterSimulatorObserver(SimulatorRunnableObserver)}.
	 */
	private SimulatorRunnableObserver simulatorObservers[] = new SimulatorRunnableObserver[4];

	/**
	 * Stores the number of observers stored in {@link #simulatorObservers}. Note that elements of that array can be
	 * unused
	 */
	private int simulatorObserverCount = 0;

	/**
	 * A mutex on which we need to synchronize whenever we access the {@link #simulatorObservers}
	 * (adding, removing or iterating over them)
	 */
	final private Object simulatorObserverMutex = new Object();

	/** @return average frame rate of the calculation of simulator ticks */
	final public long getAvgFps() {
		long avgDuration = tickDuration.getAverage();
		if (avgDuration == 0) {
			return 0;
		}
		return 1000 / tickDuration.getAverage();
	}

	/**
	 * Add a new {@link SimulatorRunnableObserver}. The new observer will be notified whenever a tick completes.
	 *
	 * @param newObserver new observer
	 */
	public void registerSimulatorRunnableObserver(SimulatorRunnableObserver newObserver) {
		synchronized(simulatorObserverMutex) {
			if (simulatorObservers.length == simulatorObserverCount - 1) {
				// Time to reallocate
				SimulatorRunnableObserver[] newObservers = new SimulatorRunnableObserver[simulatorObservers.length * 2];
				System.arraycopy(simulatorObservers, 0, newObservers, 0, simulatorObservers.length);
				simulatorObservers = newObservers;
			}
			simulatorObservers[simulatorObserverCount++] = newObserver;
		}
	}

	/**
	 * Remove a {@link SimulatorRunnableObserver}. The observer will no longer be notified when a tick completes.
	 *
	 * @param goneObserver observer to remove
	 */
	public void unregisterSimulatorObserver(SimulatorRunnableObserver goneObserver) {
		synchronized(simulatorObserverMutex) {
			for (int no = 0; no < simulatorObservers.length; no++) {
				if (simulatorObservers[no] == goneObserver) {
					System.arraycopy(simulatorObservers, no + 1, simulatorObservers, no, simulatorObserverCount - 1 - no);
					simulatorObservers[simulatorObserverCount - 1] = null;
					simulatorObserverCount--;
				}
			}
		}
	}

	/** @return desired frame rate */
	public int getTargetFps() {
		return targetFps;
	}

	/**
	 * Changes the desired frame rate.
	 *
	 * @param newTargetFps new desired frame rate
	 * @return {@code true} if the desired frame rate was changed; {@code false} if the desired frame rate
	 *     is already equal to the requested frame rate
	 */
	synchronized public boolean setTargetFps(int newTargetFps) {
		if (newTargetFps == targetFps) {
			return false;
		}
		boolean needToStartNewThread = targetFps == 0;
		targetFps = newTargetFps;
		return needToStartNewThread;
	}

	/** Stop ticking the world. To start over a new {@link Thread} must be created and started. */
	public void stopTicking() {
		Thread originalThread = simulatorTickThread;
		simulatorTickThread = null;
		if (originalThread != null) {
			originalThread.interrupt();
		}
	}

	/** Main loop that ticks the simulator */
	@Override
	public void run() {
		simulatorTickThread = Thread.currentThread();
		try {
			if (Log.isLoggable("Wa-Tor", Log.DEBUG)) { Log.d("Wa-Tor", "Entering simulator thread"); }
			while (Thread.currentThread() == simulatorTickThread) {
				long startUpdate = System.currentTimeMillis();
				simulator.tick(1);
				synchronized(simulatorObserverMutex) {
					for (int observerNo = 0; observerNo < simulatorObserverCount; observerNo++) {
						simulatorObservers[observerNo].simulatorUpdated(simulator);
					}
				}
				long duration = System.currentTimeMillis() - startUpdate;
				tickDuration.add(duration);
				if (Log.isLoggable("Wa-Tor", Log.VERBOSE)) {
					// Calculate some statistics
					Log.v("Wa-Tor", "World tick took " + duration + " ms (avg: " + tickDuration.getAverage() + " ms)");
				}
				synchronized (this) {
					if (targetFps == 0) {
						simulatorTickThread = null;
					} else {
						long sleepTime = 1000 / targetFps - duration;
						if (sleepTime < 10 /* ms */) {
							sleepTime = 10 /* ms */;
							if (Log.isLoggable("Wa-Tor", Log.VERBOSE)) {
								Log.v("Wa-Tor", "World tick took TOO LONG! Sleeping " + sleepTime + " ms");
							}
						}
						Thread.sleep(sleepTime);
					}
				}
			}
		} catch (InterruptedException e) {
			if (Log.isLoggable("Wa-Tor", Log.DEBUG)) {
				Log.d("Wa-Tor", "Simulator thread got interrupted");
			}
			simulatorTickThread = null;
		}
		if (Log.isLoggable("Wa-Tor", Log.DEBUG)) {
			Log.d("Wa-Tor", "Exiting simulator thread");
		}

	}

	/** Creates a new {@link Runnable}. The new object should be started with a new {@link Thread} */
	public SimulatorRunnable(Simulator simulator) {
		this.simulator = simulator;
	}

}
