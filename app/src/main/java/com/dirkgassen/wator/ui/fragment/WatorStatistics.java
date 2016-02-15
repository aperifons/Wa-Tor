/*
 * WatorStatistics.java is part of Wa-Tor (C) 2016 by Dirk Gassen.
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

package com.dirkgassen.wator.ui.fragment;

import com.dirkgassen.wator.R;
import com.dirkgassen.wator.simulator.Simulator;
import com.dirkgassen.wator.simulator.WorldObserver;
import com.dirkgassen.wator.simulator.WorldHost;
import com.dirkgassen.wator.ui.view.RollingGraphView;

import android.content.Context;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

/**
 * A fragment that shows a rolling graph of the statistics of fish and shark in a {@link Simulator}. The fragment must
 * be placed into an activity that implements {@link WorldHost}.It registers itself as a {@link WorldObserver} to that {@link WorldHost} to receive
 * notifcations that the simulator has ticked.
 */
public class WatorStatistics extends Fragment implements WorldObserver {

	/** A preallocated array used to add new values to {@link #rollingGraphView} */
	private final float[] newStatsValues = new float[2];

	/** The {@link RollingGraphView} that shows the statistics */
	private RollingGraphView rollingGraphView;

	/** The hosting activity */
	private WorldHost displayHost;

	/**
	 * Called when this fragment is first attached to a {@link Context}.
	 *
	 * @param context context this fragment is attached to
	 */
	@Override
	public void onAttach(Context context) {
		super.onAttach(context);
		FragmentActivity hostActivity = getActivity();
		if (hostActivity instanceof WorldHost) {
			displayHost = (WorldHost) hostActivity;
		} else {
			displayHost = null;
		}
	}

	/**
	 * Called to have the fragment instantiates its user interface view. Inflates the view of this fragment.
	 *
	 * @param inflater           used to inflate the view
	 * @param container          If not {@code null}, this is the parent view that the fragment's UI should be attached to
	 * @param savedInstanceState previous state of the framgent (ignored)
	 * @return view to use for this fragment
	 */
	@Nullable
	@Override
	public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
		View v = inflater.inflate(R.layout.fish_shark_history_graph, container, false /* attachToRoot */);
		rollingGraphView = (RollingGraphView) v.findViewById(R.id.fish_shark_graph);
		return v;
	}

	/** Called when the fragment is paused. */
	@Override
	public void onPause() {
		super.onPause();
		if (displayHost != null) {
			displayHost.unregisterSimulatorObserver(this);
		}
	}

	/** Called when the fragment is resumed. */
	@Override
	public void onResume() {
		super.onResume();
		if (displayHost != null) {
			displayHost.registerSimulatorObserver(this);
		}
	}

	/**
	 * Called when the {@link WorldHost} has updated its simulator. This method repaints the bitmap for the view.
	 *
	 * @param world {@link com.dirkgassen.wator.simulator.Simulator.WorldInspector} of the {@link Simulator} that was
	 */
	 @Override
	public void worldUpdated(Simulator.WorldInspector world) {
		if (rollingGraphView != null) {
			synchronized (newStatsValues) {
				newStatsValues[0] = world.getFishCount();
				newStatsValues[1] = world.getSharkCount();
				rollingGraphView.addData(newStatsValues);
			}
		}
	}

}
