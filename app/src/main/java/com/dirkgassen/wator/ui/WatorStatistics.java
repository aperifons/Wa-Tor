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

package com.dirkgassen.wator.ui;

import com.dirkgassen.wator.R;
import com.dirkgassen.wator.simulator.Simulator;

import android.content.Context;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

/**
 * @author dirk.
 */
public class WatorStatistics extends Fragment implements WatorDisplayHost.SimulatorObserver {

	private RollingGraphView rollingGraphView;

	private WatorDisplayHost displayHost;

	@Override
	public void onAttach(Context context) {
		super.onAttach(context);
		FragmentActivity hostActivity = getActivity();
		if (hostActivity instanceof WatorDisplayHost) {
			displayHost = (WatorDisplayHost) hostActivity;
		} else {
			displayHost = null;
		}
	}

	@Nullable
	@Override
	public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
		View v = inflater.inflate(R.layout.fish_shark_history_graph, container, false /* attachToRoot */);
		rollingGraphView = (RollingGraphView) v.findViewById(R.id.fish_shark_graph);
		return v;
	}

	@Override
	public void onPause() {
		super.onPause();
		if (displayHost != null) {
			displayHost.unregisterSimulatorObserver(this);
		}
	}

	@Override
	public void onResume() {
		super.onResume();
		if (displayHost != null) {
			displayHost.registerSimulatorObserver(this);
		}
	}

	@Override
	public void worldUpdated(Simulator.WorldInspector world) {
		float[] seriesData = new float[] {
				world.fishCount,
				world.sharkCount
		};
		if (rollingGraphView != null) {
			rollingGraphView.addData(seriesData);
		}
	}

	@Override
	public void worldSizeUpdated(int width, int height) {

	}
}
