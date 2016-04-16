/*
 * WatorDisplayHost.java is part of Wa-Tor (C) 2016 by Dirk Gassen.
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
 * This interface should be implemented by classes who run a {@link Simulator}. It provides a common interface to
 * allow for registering/unregistering a {@link WorldObserver}.
 */
public interface WorldHost {

	/**
	 * Register a new {@link WorldObserver}.
	 *
	 * @param newObserver new observer
	 */
	void registerSimulatorObserver(WorldObserver newObserver);

	/**
	 * Unregister a {@link WorldObserver}
	 *
	 * @param goneObserver observer to be unregistered
	 */
	void unregisterSimulatorObserver(WorldObserver goneObserver);

	/**
	 * Returns a {@link Simulator.WorldInspector} for painting.
	 * After being done you need to call {@link Simulator.WorldInspector#release()}.
	 * @return {@link Simulator.WorldInspector} of the world
	 */
	Simulator.WorldInspector getWorld();

}
