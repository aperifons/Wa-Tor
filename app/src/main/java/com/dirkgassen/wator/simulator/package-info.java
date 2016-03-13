/*
 * package-info.java is part of Wa-Tor (C) 2016 by Dirk Gassen.
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

/**
 * This package contains classes related to simulating a Wa-Tor world.
 * <p/>
 * The {@link com.dirkgassen.wator.simulator.Simulator} simulates a Wa-Tor world.
 * {@link com.dirkgassen.wator.simulator.WorldParameters} objects describe a world and can be used to create a new
 * {@link com.dirkgassen.wator.simulator.Simulator}.
 * <p/>
 * A class can implement {@link com.dirkgassen.wator.simulator.WorldObserver} in order to be registered with a {@link com.dirkgassen.wator.simulator.WorldHost} to be called whenever a {@link com.dirkgassen.wator.simulator.Simulator} is ticked (updated).
 * <p/>
 * A {@link com.dirkgassen.wator.simulator.SimulatorRunnable} can be used to periodically tick (update) a {@link com.dirkgassen.wator.simulator.Simulator}.
 */
package com.dirkgassen.wator.simulator;