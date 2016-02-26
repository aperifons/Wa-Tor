/*
 * NewWorld.java is part of Wa-Tor (C) 2016 by Dirk Gassen.
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
import com.dirkgassen.wator.simulator.WorldHost;
import com.dirkgassen.wator.simulator.WorldParameters;

import android.content.Context;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;

/**
 * @author dirk.
 */
public class NewWorld extends Fragment {

	interface WorldCreator {
		WorldParameters getPreviousWorldParameters();
		void createWorld(WorldParameters worldParameters);
		void cancelCreateWorld();
	}

	private EditText worldWidthEdit;
	private EditText worldHeightEdit;
	private EditText fishBreedEdit;
	private EditText sharkBreedEdit;
	private EditText sharkStarveEdit;
	private EditText initialFishEdit;
	private EditText initialSharkEdit;

	private WorldCreator worldCreator;

	private void createWorld() {
		worldCreator.createWorld(
				new WorldParameters()
						.setWidth(Short.valueOf(worldWidthEdit.getText().toString()))
						.setHeight(Short.valueOf(worldHeightEdit.getText().toString()))
						.setFishBreedTime(Short.valueOf(fishBreedEdit.getText().toString()))
						.setSharkBreedTime(Short.valueOf(sharkBreedEdit.getText().toString()))
						.setSharkStarveTime(Short.valueOf(sharkStarveEdit.getText().toString()))
						.setInitialFishCount(Short.valueOf(initialFishEdit.getText().toString()))
						.setInitialSharkCount(Short.valueOf(initialSharkEdit.getText().toString()))
		);
	}

	@Nullable
	@Override
	public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
		View v = inflater.inflate(R.layout.new_world, container, false /* attachToRoot */);
		v.findViewById(R.id.create_new_world).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				createWorld();
			}
		});
		v.findViewById(R.id.cancel_new_world).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				worldCreator.cancelCreateWorld();
			}
		});

		worldWidthEdit = (EditText) v.findViewById(R.id.world_width);
		worldHeightEdit = (EditText) v.findViewById(R.id.world_height);
		fishBreedEdit = (EditText) v.findViewById(R.id.fish_breed_time);
		sharkBreedEdit = (EditText) v.findViewById(R.id.shark_breed_time);
		sharkStarveEdit = (EditText) v.findViewById(R.id.shark_starve);
		initialFishEdit = (EditText) v.findViewById(R.id.initial_fish_count);
		initialSharkEdit = (EditText) v.findViewById(R.id.initial_shark_count);

		WorldParameters worldParameters = worldCreator.getPreviousWorldParameters();
		if (worldParameters == null) {
			worldParameters = new WorldParameters();
		}
		worldWidthEdit.setText(Short.toString(worldParameters.getWidth()));
		worldHeightEdit.setText(Short.toString(worldParameters.getHeight()));
		fishBreedEdit.setText(Short.toString(worldParameters.getFishBreedTime()));
		sharkBreedEdit.setText(Short.toString(worldParameters.getSharkBreedTime()));
		sharkStarveEdit.setText(Short.toString(worldParameters.getSharkStarveTime()));
		initialFishEdit.setText(Integer.toString(worldParameters.getInitialFishCount()));
		initialSharkEdit.setText(Integer.toString(worldParameters.getInitialSharkCount()));

		return v;
	}

	@Override
	public void onAttach(Context context) {
		super.onAttach(context);
		FragmentActivity hostActivity = getActivity();
		if (hostActivity instanceof WorldHost) {
			worldCreator = (WorldCreator) hostActivity;
		} else {
			worldCreator = null;
		}
	}

}
