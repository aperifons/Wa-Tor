/*
 * WatorDisplay.java is part of Wa-Tor (C) 2016 by Dirk Gassen.
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
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

/**
 * @author dirk.
 */
public class WatorDisplay extends Fragment implements WatorDisplayHost.SimulatorObserver {

	private Handler handler;

	private ImageView watorDisplay;

	int[] fishAgeColors;

	int[] sharkAgeColors;

	private int waterColor;

	private WatorDisplayHost displayHost;

	private Bitmap b;

	private int[] pixels;

	private int[] calculateIndividualAgeColors(int max, int youngColor, int oldColor) {
		final int[] colors = new int[max];
		final float[] youngColorHsv = new float[3];
		final float[] oldColorHsv = new float[3];
		final float[] targetColor = new float[3];
		Color.colorToHSV(youngColor, youngColorHsv);
		Color.colorToHSV(oldColor, oldColorHsv);
		for (int age = 0; age < max; age++) {
			for (int no = 0; no < 3; no++) {
				float proportion = (float) age / (float) max;
				targetColor[no] = (youngColorHsv[no] + ((oldColorHsv[no] - youngColorHsv[no]) * proportion));
				colors[age] = Color.HSVToColor(targetColor);
			}
		}
		return colors;
	}

	@Nullable
	@Override
	public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
		View v = inflater.inflate(R.layout.wator_display, container, false /* attachToRoot */);
		watorDisplay = (ImageView) v.findViewById(R.id.wator_2d_view);
		return v;
	}

	@Override
	public void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		handler = new Handler();
		waterColor = ContextCompat.getColor(this.getContext(), R.color.water);
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		synchronized (this) {
			b.recycle();
		}
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
	public void onAttach(Context context) {
		super.onAttach(context);
		FragmentActivity hostActivity = getActivity();
		if (hostActivity instanceof WatorDisplayHost) {
			displayHost = (WatorDisplayHost) hostActivity;
		} else {
			displayHost = null;
		}
	}

	@Override
	public void worldUpdated(Simulator.WorldInspector world) {
		Log.d("Wa-Tor", "Updating image");
		long startUpdate = System.currentTimeMillis();
		int fishCount = 0;
		int sharkCount = 0;

		int worldWidth = world.getWorldWidth();
		int worldHeight = world.getWorldHeight();
		int fishReproduceAge = world.getFishReproduceAge();
		int sharkMaxHunger = world.getMaxSharkHunger();

		if (b == null || b.getWidth() != worldWidth || b.getHeight() != worldHeight) {
			b = Bitmap.createBitmap(worldWidth, worldHeight, Bitmap.Config.ARGB_8888);
			pixels = new int[worldWidth * worldHeight];
		}
		if (fishAgeColors == null || fishAgeColors.length != fishReproduceAge) {
			fishAgeColors = calculateIndividualAgeColors(
					fishReproduceAge,
					ContextCompat.getColor(getContext(), R.color.fish_young),
					ContextCompat.getColor(getContext(), R.color.fish_old)
			);
		}
		if (sharkAgeColors == null || sharkAgeColors.length != sharkMaxHunger) {
			sharkAgeColors = calculateIndividualAgeColors(
					sharkMaxHunger,
					ContextCompat.getColor(getContext(), R.color.shark_young),
					ContextCompat.getColor(getContext(), R.color.shark_old)
			);
		}

		do {
			if (world.isEmpty()) {
				pixels[world.getCurrentPosition()] = waterColor;
			} else if (world.isFish()) {
				pixels[world.getCurrentPosition()] = fishAgeColors[world.getFishAge() - 1];
				fishCount++;
			} else {
				pixels[world.getCurrentPosition()] = sharkAgeColors[world.getSharkHunger() - 1];
				sharkCount++;
			}
		} while (world.moveToNext() != Simulator.WORLD_INSPECTOR_MOVE_RESULT.RESET);
		Log.d("Wa-Tor", "Generating pixels " + (System.currentTimeMillis() - startUpdate) + " ms");
		synchronized (WatorDisplay.this) {
			if (b != null) {
				int width = b.getWidth();
				int height = b.getHeight();
				b.setPixels(pixels, 0, width, 0, 0, width, height);
			}
		}
		handler.post(new Runnable() {
			@Override
			public void run() {
				watorDisplay.setImageBitmap(b);
			}
		});
		Log.d("Wa-Tor", "Repainting took " + (System.currentTimeMillis() - startUpdate) + " ms");
	}

	@Override
	public void worldSizeUpdated(int width, int height) {
		// TODO: Evaluate if this is still needed?
	}
}
