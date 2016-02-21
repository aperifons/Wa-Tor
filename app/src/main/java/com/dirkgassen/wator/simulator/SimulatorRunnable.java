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

import java.util.HashSet;
import java.util.Set;

import android.util.Log;

/**
 * @author dirk.
 */
public class SimulatorRunnable implements Runnable {

	public interface SimulatorRunnableObserver {

		void simulatorUpdated(Simulator simulator);

	}

	private final Simulator simulator;

	private final RollingAverage tickDuration = new RollingAverage();

	private int targetFps = 30;

	private Thread simulatorTickThread;

	private final Set<SimulatorRunnableObserver> simulatorObservers = new HashSet<SimulatorRunnableObserver>();

	final public long getAvgFps() {
		return 1000 / tickDuration.getAverage();
	}

	public void registerSimulatorRunnableObserver(SimulatorRunnableObserver newObserver) {
		synchronized (simulatorObservers) {
			simulatorObservers.add(newObserver);
		}

	}

	public void unregisterSimulatorObserver(SimulatorRunnableObserver goneObserver) {
		synchronized (simulatorObservers) {
			simulatorObservers.remove(goneObserver);
		}
	}

	public int getTargetFps() {
		return targetFps;
	}

	public void setTargetFps(int newTargetFps) {
		targetFps = newTargetFps;
	}

	public void stopTicking() {
		Thread originalThread = simulatorTickThread;
		simulatorTickThread = null;
		if (originalThread != null) {
			originalThread.interrupt();
		}
	}

	@Override
	public void run() {
		simulatorTickThread = Thread.currentThread();
		try {
			if (Log.isLoggable("Wa-Tor", Log.DEBUG)) { Log.d("Wa-Tor", "Entering simulator thread"); }
			while (Thread.currentThread() == simulatorTickThread) {
				long startUpdate = System.currentTimeMillis();
				simulator.tick(1);
				for (SimulatorRunnableObserver observer: simulatorObservers) {
					observer.simulatorUpdated(simulator);
				}
				long duration = System.currentTimeMillis() - startUpdate;
				if (Log.isLoggable("Wa-Tor", Log.VERBOSE)) {
					// Calculate some statistics
					tickDuration.add(duration);
					Log.v("Wa-Tor", "World tick took " + duration + " ms (avg: " + tickDuration.getAverage() + " ms)");
				}
				long sleepTime = 1000 / targetFps - duration;
				if (sleepTime < 10 /* ms */) {
					sleepTime = 10 /* ms */;
					if (Log.isLoggable("Wa-Tor", Log.VERBOSE)) {
						Log.v("Wa-Tor", "World tick took TOO LONG! Sleeping " + sleepTime + " ms");
					}
				}
				Thread.sleep(sleepTime);
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

	public SimulatorRunnable(Simulator simulator) {
		this.simulator = simulator;
	}

}
