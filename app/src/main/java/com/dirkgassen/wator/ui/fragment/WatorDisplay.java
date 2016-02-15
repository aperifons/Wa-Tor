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

package com.dirkgassen.wator.ui.fragment;

import com.dirkgassen.wator.R;
import com.dirkgassen.wator.simulator.Simulator;
import com.dirkgassen.wator.simulator.WorldHost;
import com.dirkgassen.wator.simulator.WorldObserver;

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
 * A fragment that displays a 2D view of a {@link Simulator} (world). The fragment must be placed into an activity that
 * implements {@link WorldHost}. It registers itself as a {@link WorldObserver} to that {@link WorldHost} to receive
 * notifcations that the simulator has ticked.
 */
public class WatorDisplay extends Fragment implements WorldObserver {

	/** {@link ImageView} to display the world in */
	private ImageView watorDisplay;

	/** A precalculated color ramp from the "new fish" color (index 0) to "old fish" color (last index) */
	private int[] fishAgeColors;

	/** A precalculated color ramp from the "new shark" color (index 0) to "old shark" color (last index) */
	private int[] sharkAgeColors;

	/** Color of the water */
	private int waterColor;

	/** The hosting activity */
	private WorldHost displayHost;

	/** Bitmap for the world */
	private Bitmap planetBitmap;

	/** Pixel array that is calculated from the world and then dumped into the {@link #planetBitmap} */
	private int[] pixels;

	/** Handler to run stuff on the UI thread */
	private Handler handler;

	/**
	 * A {@link @Runnable} that can be posted to the UI thread to set the bitmap of the {@link #watorDisplay}
	 * {@link ImageView} */
	private Runnable updateImageRunner;

	/**
	 * Calculates a color ramp from {@code youngColor} to {@code oldColor} and returns that array.
	 *
	 * @param max        maximum age; defines the number of individual colors calculated
	 * @param youngColor young (starting) color
	 * @param oldColor   old (ending) color
	 * @return array with the color ramp
	 */
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
		View v = inflater.inflate(R.layout.wator_display, container, false /* attachToRoot */);
		watorDisplay = (ImageView) v.findViewById(R.id.wator_2d_view);
		return v;
	}

	/**
	 * Called to do initial creation of this fragment.
	 * @param savedInstanceState possibly a saved state of this fragment (ignored)
	 */
	@Override
	public void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		handler = new Handler();
		updateImageRunner = new Runnable() {
			@Override
			public void run() {
				synchronized (WatorDisplay.this) {
					if (planetBitmap != null) {
						watorDisplay.setImageBitmap(planetBitmap);
					}
				}
			}
		};
		waterColor = ContextCompat.getColor(this.getContext(), R.color.water);
	}

	/** Called when this framgent is no longer in use */
	@Override
	public void onDestroy() {
		super.onDestroy();
		synchronized (this) {
			planetBitmap.recycle();
			planetBitmap = null;
		}
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
	 * Called when the {@link WorldHost} has updated its simulator. This method repaints the bitmap for the view.
	 *
	 * @param world {@link com.dirkgassen.wator.simulator.Simulator.WorldInspector} of the {@link Simulator} that was
	 *              updated
	 */
	@Override
	public void worldUpdated(Simulator.WorldInspector world) {
		if (Log.isLoggable("Wa-Tor", Log.VERBOSE)) { Log.v("Wa-Tor", "Updating image"); }
		long startUpdate = System.currentTimeMillis();

		int worldWidth = world.getWorldWidth();
		int worldHeight = world.getWorldHeight();
		int fishBreedTime = world.getFishBreedTime();
		int sharkStarveTime = world.getSharkStarveTime();

		if (planetBitmap == null || planetBitmap.getWidth() != worldWidth || planetBitmap.getHeight() != worldHeight) {
			if (Log.isLoggable("Wa-Tor", Log.DEBUG)) { Log.d("Wa-Tor", "(Re)creating bitmap/pixels"); }
			planetBitmap = Bitmap.createBitmap(worldWidth, worldHeight, Bitmap.Config.ARGB_8888);
			pixels = new int[worldWidth * worldHeight];
		}
		if (fishAgeColors == null || fishAgeColors.length != fishBreedTime) {
			if (Log.isLoggable("Wa-Tor", Log.DEBUG)) { Log.d("Wa-Tor", "(Re)creating fish colors"); }
			fishAgeColors = calculateIndividualAgeColors(
					fishBreedTime,
					ContextCompat.getColor(getContext(), R.color.fish_young),
					ContextCompat.getColor(getContext(), R.color.fish_old)
			);
		}
		if (sharkAgeColors == null || sharkAgeColors.length != sharkStarveTime) {
			if (Log.isLoggable("Wa-Tor", Log.DEBUG)) { Log.d("Wa-Tor", "(Re)creating shark colors"); }
			sharkAgeColors = calculateIndividualAgeColors(
					sharkStarveTime,
					ContextCompat.getColor(getContext(), R.color.shark_young),
					ContextCompat.getColor(getContext(), R.color.shark_old)
			);
		}

		do {
			if (world.isEmpty()) {
				pixels[world.getCurrentPosition()] = waterColor;
			} else if (world.isFish()) {
				pixels[world.getCurrentPosition()] = fishAgeColors[world.getFishAge() - 1];
			} else {
				pixels[world.getCurrentPosition()] = sharkAgeColors[world.getSharkHunger() - 1];
			}
		} while (world.moveToNext() != Simulator.WorldInspector.RESET);
		if (Log.isLoggable("Wa-Tor", Log.VERBOSE)) { Log.v("Wa-Tor", "Generating pixels " + (System.currentTimeMillis() - startUpdate) + " ms"); }
		synchronized (WatorDisplay.this) {
			if (planetBitmap != null) {
				int width = planetBitmap.getWidth();
				int height = planetBitmap.getHeight();
				planetBitmap.setPixels(pixels, 0, width, 0, 0, width, height);
			}
		}
		handler.post(updateImageRunner);
		if (Log.isLoggable("Wa-Tor", Log.VERBOSE)) { Log.v("Wa-Tor", "Repainting took " + (System.currentTimeMillis() - startUpdate) + " ms"); }
	}

}
