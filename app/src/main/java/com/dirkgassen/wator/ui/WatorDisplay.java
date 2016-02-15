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
import com.dirkgassen.wator.simulator.WorldInspector;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

/**
 * @author dirk.
 */
public class WatorDisplay extends Fragment {

	private static final short FISH_REPRODUCTION_AGE = 12;
	private static final short SHARK_REPRODUCTION_AGE = 17;
	private static final short SHARK_MAX_HUNGER = 25;
	private static final int WORLD_WIDTH = 400;
	private static final int WORLD_HEIGHT = 300;
	private static final int INITIAL_FISH = 600;
	private static final int INITIAL_SHARK = 200;

	private Simulator simulator;

	private int fps = 30;

	private Thread simulatorThread;

	private Thread painterThread;

	private Handler handler;

	private ImageView watorDisplay;

	Bitmap b;

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
		simulator = new Simulator(
				WORLD_WIDTH, WORLD_HEIGHT,
				FISH_REPRODUCTION_AGE,
				SHARK_REPRODUCTION_AGE, SHARK_MAX_HUNGER,
				INITIAL_FISH, INITIAL_SHARK
		);
		b = Bitmap.createBitmap(simulator.getWorldWidth(), simulator.getWorldHeight(), Bitmap.Config.ARGB_8888);
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		b.recycle();
	}

	@Override
	public void onPause() {
		super.onPause();
		synchronized(this) {
			simulatorThread = null;
			painterThread = null;
		}
	}

	@Override
	public void onResume() {
		super.onResume();
		painterThread = new Thread(getString(R.string.painterThreadName)) {
			@Override
			public void run() {
				Context c = WatorDisplay.this.getContext();
				// Set up the colors... we do some fancy interpolation in HSV color space,
				// however, let's precalculate the colors.
				// See
				//   http://stackoverflow.com/questions/4414673/android-color-between-two-colors-based-on-percentage
				// for details
				final int[] fishAgeColors = calculateIndividualAgeColors(
						FISH_REPRODUCTION_AGE,
						ContextCompat.getColor(c, R.color.fish_young),
						ContextCompat.getColor(c, R.color.fish_old)
				);
				final int[] sharkAgeColors = calculateIndividualAgeColors(
						SHARK_MAX_HUNGER,
						ContextCompat.getColor(c, R.color.shark_young),
						ContextCompat.getColor(c, R.color.shark_old)
				);
				final int[] pixels = new int[simulator.getWorldWidth() * simulator.getWorldHeight()];
				final int waterColor = ContextCompat.getColor(c, R.color.water);
				try {
					while (Thread.currentThread() == painterThread) {
						Log.d("Wa-Tor", "Updating image");
						long startUpdate = System.currentTimeMillis();
						int fishCount = 0;
						int sharkCount = 0;
						WorldInspector world = simulator.getWorldToPaint();
						try {
							do {
								if (world.isEmpty()) {
									pixels[world.getCurrentPosition()] = waterColor;
								} else  if (world.isFish()) {
									pixels[world.getCurrentPosition()] = fishAgeColors[world.getFishAge() - 1];
									fishCount++;
								} else {
									pixels[world.getCurrentPosition()] = sharkAgeColors[world.getSharkHunger() - 1];
									sharkCount++;
								}
							} while (world.moveToNext() != WorldInspector.MOVE_RESULT.RESET);
						} finally {
							simulator.releaseWorldToPaint();
						}
						Log.d("Wa-Tor", "Generating pixels " + (System.currentTimeMillis() - startUpdate) + " ms");
						synchronized(WatorDisplay.this) {
							if (Thread.currentThread() == painterThread) {
								b.setPixels(pixels, 0, simulator.getWorldWidth(), 0, 0, simulator.getWorldWidth(), simulator.getWorldHeight());
							}
						}
						handler.post(new Runnable() {
							@Override
							public void run() {
								watorDisplay.setImageBitmap(b);
							}
						});
						Log.d("Wa-Tor", "Repainting took " + (System.currentTimeMillis() - startUpdate) + " ms");
						Log.d("Wa-Tor", "Fish: " + fishCount + "; sharks: " + sharkCount);
						if (fishCount == simulator.getWorldHeight() * simulator.getWorldWidth() || fishCount + sharkCount == 0) {
							simulatorThread = null;
						}
						Log.d("Wa-Tor", "Waiting for next image update");
						synchronized (this) {
							wait();
						}
					}
				} catch (InterruptedException e) {
					painterThread = null;
				}
			}
		};
		painterThread.start();
		simulatorThread = new Thread(getString(R.string.simulatorThreadName)) {
			@Override
			public void run() {
				try {
					while (Thread.currentThread() == simulatorThread) {
						long startUpdate = System.currentTimeMillis();
						simulator.tick();
						synchronized(WatorDisplay.this) {
							if (painterThread != null) {
								//noinspection SynchronizeOnNonFinalField
								synchronized (painterThread) {
									painterThread.notify();
								}
							}
						}
						long duration = System.currentTimeMillis() - startUpdate;
						Log.d("Wa-Tor", "World tick took " + duration + " ms");
						long sleepTime = 1000 / fps - duration;
						if (sleepTime < 100 /* ms */) {
							sleepTime = 100 /* ms */;
						}
						Thread.sleep(sleepTime);
					}
				} catch (InterruptedException e) {
					simulatorThread = null;
				}
			}
		};
		simulatorThread.start();
	}
}
