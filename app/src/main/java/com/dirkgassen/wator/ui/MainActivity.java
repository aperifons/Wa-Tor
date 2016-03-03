/*
 * MainActivity.java is part of Wa-Tor (C) 2016 by Dirk Gassen.
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.dirkgassen.wator.R;
import com.dirkgassen.wator.simulator.RollingAverage;
import com.dirkgassen.wator.simulator.Simulator;
import com.dirkgassen.wator.simulator.SimulatorRunnable;
import com.dirkgassen.wator.simulator.WorldHost;
import com.dirkgassen.wator.simulator.WorldObserver;
import com.dirkgassen.wator.simulator.WorldParameters;

import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

/**
 * @author dirk.
 */
// Adding the hamburger menu: followed this tutorial:
//     http://codetheory.in/android-navigation-drawer/
public class MainActivity extends AppCompatActivity implements WorldHost, SimulatorRunnable.SimulatorRunnableObserver, NewWorld.WorldCreator {


	class DrawerCommandItem {
		public final int icon;
		public final String title;
		public final String subtitle;

		DrawerCommandItem(int icon, String title, String subtitle) {
			this.icon = icon;
			this.title = title;
			this.subtitle = subtitle;
		}
	}

	class DrawerListAdapter extends BaseAdapter {
		private final List<DrawerCommandItem> drawerCommands;
		private final LayoutInflater inflater;

		@Override
		public int getCount() {
			return drawerCommands.size();
		}

		@Override
		public Object getItem(int position) {
			return drawerCommands.get(position);
		}

		@Override
		public long getItemId(int position) {
			return 0;
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			if (convertView == null) {
				convertView = inflater.inflate(R.layout.drawer_command_item, parent, false);
			}
			DrawerCommandItem item = drawerCommands.get(position);
			((TextView) convertView.findViewById(R.id.drawer_command_title)).setText(item.title);
			((TextView) convertView.findViewById(R.id.drawer_command_subtitle)).setText(item.subtitle);
			((ImageView) convertView.findViewById(R.id.drawer_command_icon)).setImageResource(item.icon);
			return convertView;
		}

		public DrawerListAdapter(List<DrawerCommandItem> drawerCommands) {
			this.drawerCommands = drawerCommands;
			inflater = LayoutInflater.from(MainActivity.this);
		}

	}

	private static final String FISH_AGE_KEY = "fishAge";
	private static final String SHARK_AGE_KEY = "sharkAge";
	private static final String SHARK_HUNGER_KEY = "sharkHunger";
	private static final String FISH_POSITIONS_X_KEY = "fishPositionsX";
	private static final String FISH_POSITIONS_Y_KEY = "fishPositionsY";
	private static final String FISH_BREED_TIME_KEY = "fishReproductionAge";
	private static final String SHARK_BREED_TIME_KEY = "sharkReproductionAge";
	private static final String SHARK_STARVE_TIME_KEY = "fishMaxHunger";
	private static final String SHARK_POSITIONS_X_KEY = "sharkPositionsX";
	private static final String SHARK_POSITIONS_Y_KEY = "sharkPositionsY";
	private static final String WORLD_WIDTH_KEY = "worldWidth";
	private static final String WORLD_HEIGHT_KEY = "worldHeight";
	private static final String INITIAL_FISH_COUNT_KEY = "initialFishCount";
	private static final String INITIAL_SHARK_COUNT_KEY = "initialSharkCount";
	private static final String TARGET_FPS_KEY = "targetFps";


	private final Set<WorldObserver> worldObservers = new HashSet<WorldObserver>();

	private Simulator simulator;

	private SimulatorRunnable simulatorRunnable;

	private Thread worldUpdateNotifierThread;

	private RollingAverage drawingAverageFps;

	private TextView currentDrawFpsLabel;

	private TextView currentSimFpsLabel;

	private TextView currentSimFps;

	private TextView currentDrawFps;


	private RangeSlider desiredFpsSlider;

	private TextView simulatorFpsTextView;

	private TextView drawingFpsTextView;

	private DrawerLayout drawerLayout;

	private View drawerPane;

	private ListView drawerList;

	private Fragment newWorldFragment;

	private ActionBarDrawerToggle drawerToggle;

	private Handler handler;

	private Runnable updateFpsRunnable;

	private WorldParameters previousWorldParameters;

	private void worldUpdated() {
		synchronized (worldObservers) {
			if (worldObservers.size() > 0) {
				Simulator.WorldInspector world = simulator.getWorldToPaint();
				try {
					for (WorldObserver observer : worldObservers) {
						observer.worldUpdated(world);
						world.reset();
					}
					if (Log.isLoggable("Wa-Tor", Log.VERBOSE)) { Log.v("Wa-Tor", "Fish: " + world.getFishCount() + "; sharks: " + world.getSharkCount()); }
					if (world.getSharkCount() + world.getFishCount() == 0 || world.getFishCount() == world.getWorldWidth() * world.getWorldHeight()) {
						simulatorRunnable.stopTicking();
					}
				} finally {
					world.release();
				}
			}
		}
		// Note: not synchronizing here, but rather a rudimentary check to avoid posting if it's not
		// necessary. If the drawer opens while we are executing and one of these fields change to non-null
		// then the frame rate will be update next time. If it's the other way around the updateFpsRunnable
		// should synchronize and check again.
		if (simulatorFpsTextView != null || drawingFpsTextView != null) {
			handler.post(updateFpsRunnable);
		}
	}

	private List<DrawerCommandItem> getDrawerCommands() {
		DrawerCommandItem[] commands = new DrawerCommandItem[] {
				new DrawerCommandItem(0, getString(R.string.create_new_world_command), getString(R.string.create_new_world_description))
		};
		List<DrawerCommandItem> commandList = new ArrayList<DrawerCommandItem>();
		Collections.addAll(commandList, commands);
		return commandList;
	}

	private void drawerCommandSelected(int position) {
		switch (position) {
			case 0: // New world
				drawerLayout.closeDrawers();
				toggleNewWorldFragment();
				break;
		}
	}

	synchronized private void hideNewWorldFragment() {
		if (newWorldFragment != null) {
			FragmentManager fm = getSupportFragmentManager();
			FragmentTransaction ft = fm.beginTransaction();
			ft.remove(newWorldFragment);
			newWorldFragment = null;
			ft.commit();
		}
	}

	synchronized private void toggleNewWorldFragment() {
		FragmentManager fm = getSupportFragmentManager();
		FragmentTransaction ft = fm.beginTransaction();
		if (newWorldFragment == null) {
			ft.setCustomAnimations(R.anim.slide_down, R.anim.slide_down);
			newWorldFragment = new NewWorld();
			ft.add(R.id.new_world_fragment, newWorldFragment);
		} else {
			ft.setCustomAnimations(R.anim.slide_up, R.anim.slide_up);
			ft.remove(newWorldFragment);
			newWorldFragment = null;
		}
		ft.commit();

	}

	@Override
	public WorldParameters getPreviousWorldParameters() {
		if (previousWorldParameters == null) {
			return new WorldParameters();
		}
		return previousWorldParameters;
	}

	@Override
	public void createWorld(WorldParameters worldParameters) {
		int targetFps = simulatorRunnable != null ? simulatorRunnable.getTargetFps() : -1;
		previousWorldParameters = worldParameters;
		simulator = new Simulator(worldParameters);
		simulatorRunnable.stopTicking();
		simulatorRunnable = new SimulatorRunnable(simulator);
		if (targetFps >= 0) {
			simulatorRunnable.setTargetFps(targetFps);
		}
		simulatorRunnable.registerSimulatorRunnableObserver(this);
		startSimulatorThread();
		hideNewWorldFragment();
	}

	@Override
	public void cancelCreateWorld() {
		hideNewWorldFragment();
	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		Simulator.WorldInspector world = simulator.getWorldToPaint();
		try {
			final int fishCount = world.getFishCount();
			final int sharkCount = world.getSharkCount();
			final short[] fishAge;
			final short[] fishPosX;
			final short[] fishPosY;
			if (fishCount == 0) {
				fishAge = fishPosX = fishPosY = null;
			} else {
				fishAge = new short[fishCount];
				fishPosX = new short[fishCount];
				fishPosY = new short[fishCount];
			}
			final short[] sharkAge;
			final short[] sharkHunger;
			final short[] sharkPosX;
			final short[] sharkPosY;
			if (sharkCount == 0) {
				sharkAge = sharkHunger = sharkPosX = sharkPosY = null;
			} else {
				sharkAge = new short[sharkCount];
				sharkHunger = new short[sharkCount];
				sharkPosX = new short[sharkCount];
				sharkPosY = new short[sharkCount];
			}

			int fishNo = 0;
			int sharkNo = 0;
			do {
				if (world.isFish()) {
					//noinspection ConstantConditions
					fishAge[fishNo] = world.getFishAge();
					//noinspection ConstantConditions
					fishPosX[fishNo] = world.getCurrentX();
					//noinspection ConstantConditions
					fishPosY[fishNo++] = world.getCurrentY();
				} else if (world.isShark()) {
					//noinspection ConstantConditions
					sharkAge[sharkNo] = world.getSharkAge();
					//noinspection ConstantConditions
					sharkHunger[sharkNo] = world.getSharkHunger();
					//noinspection ConstantConditions
					sharkPosX[sharkNo] = world.getCurrentX();
					//noinspection ConstantConditions
					sharkPosY[sharkNo++] = world.getCurrentY();
				}
			} while (world.moveToNext() != Simulator.WORLD_INSPECTOR_MOVE_RESULT.RESET);
			if (fishCount > 0) {
				outState.putShortArray(FISH_AGE_KEY, fishAge);
				outState.putShortArray(FISH_POSITIONS_X_KEY, fishPosX);
				outState.putShortArray(FISH_POSITIONS_Y_KEY, fishPosY);
			}
			if (sharkCount > 0) {
				outState.putShortArray(SHARK_AGE_KEY, sharkAge);
				outState.putShortArray(SHARK_HUNGER_KEY, sharkHunger);
				outState.putShortArray(SHARK_POSITIONS_X_KEY, sharkPosX);
				outState.putShortArray(SHARK_POSITIONS_Y_KEY, sharkPosY);
			}
			outState.putShort(WORLD_WIDTH_KEY, world.getWorldWidth());
			outState.putShort(WORLD_HEIGHT_KEY, world.getWorldHeight());
			outState.putShort(FISH_BREED_TIME_KEY, world.getFishBreedTime());
			outState.putShort(SHARK_BREED_TIME_KEY, world.getSharkBreedTime());
			outState.putShort(SHARK_STARVE_TIME_KEY, world.getSharkStarveTime());

			if (previousWorldParameters == null) {
				previousWorldParameters = new WorldParameters();
			}
			outState.putInt(INITIAL_FISH_COUNT_KEY, previousWorldParameters.getInitialFishCount());
			outState.putInt(INITIAL_SHARK_COUNT_KEY, previousWorldParameters.getInitialSharkCount());

			if (simulatorRunnable != null) {
				outState.putInt(TARGET_FPS_KEY, simulatorRunnable.getTargetFps());
			}
		} finally {
			world.release();
		}
		super.onSaveInstanceState(outState);
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main_layout);
		Toolbar myToolbar = (Toolbar) findViewById(R.id.main_toolbar);
		setSupportActionBar(myToolbar);
		// More info: http://codetheory.in/difference-between-setdisplayhomeasupenabled-sethomebuttonenabled-and-setdisplayshowhomeenabled/
		getSupportActionBar().setDisplayHomeAsUpEnabled(true);

		currentSimFps = (TextView) findViewById(R.id.fps_simulator);
		currentSimFpsLabel = (TextView) findViewById(R.id.label_fps_simulator);
		currentDrawFps = (TextView) findViewById(R.id.fps_drawing);
		currentDrawFpsLabel = (TextView) findViewById(R.id.label_fps_drawing);
		desiredFpsSlider = (RangeSlider) findViewById(R.id.desired_fps);
		desiredFpsSlider.setOnTouchListener(new View.OnTouchListener() {
			@Override
			public boolean onTouch(View v, MotionEvent event) {
				switch (event.getAction()) {
					case MotionEvent.ACTION_DOWN:
						v.getParent().requestDisallowInterceptTouchEvent(true);
						break;
					case MotionEvent.ACTION_UP:
						v.getParent().requestDisallowInterceptTouchEvent(false);
						break;
				}
				v.onTouchEvent(event);
				return true;
			}
		});
		desiredFpsSlider.setOnValueChangeListener(new RangeSlider.OnValueChangeListener() {
			@Override
			public void onValueChange(RangeSlider slider, int oldVal, int newVal) {
				if (newVal == 0) {
					currentDrawFps.setVisibility(View.INVISIBLE);
					currentDrawFpsLabel.setVisibility(View.INVISIBLE);
					currentSimFps.setVisibility(View.INVISIBLE);
					currentSimFpsLabel.setVisibility(View.INVISIBLE);
				} else {
					currentDrawFps.setVisibility(View.VISIBLE);
					currentDrawFpsLabel.setVisibility(View.VISIBLE);
					currentSimFps.setVisibility(View.VISIBLE);
					currentSimFpsLabel.setVisibility(View.VISIBLE);
				}
//				if (fromUser) {
				if (simulatorRunnable.setTargetFps(newVal)) {
					startSimulatorThread();
				}
//				}
			}
		});

		handler = new Handler();
		updateFpsRunnable = new Runnable() {
			@Override
			public void run() {
				synchronized (MainActivity.this) {
					if (simulatorFpsTextView != null) {
						simulatorFpsTextView.setText(String.format(Locale.getDefault(), "%d", simulatorRunnable.getAvgFps()));
					}
					if (drawingFpsTextView != null) {
						drawingFpsTextView.setText(String.format(Locale.getDefault(), "%d", drawingAverageFps.getAverage() == 0 ? 0 : 1000 / drawingAverageFps.getAverage()));
					}
				}
			}
		};


		if (savedInstanceState == null) {
			simulator = new Simulator(
					new WorldParameters()
			);
		} else {
			WorldParameters parameters = new WorldParameters()
					.setWidth(savedInstanceState.getShort(WORLD_WIDTH_KEY))
					.setHeight(savedInstanceState.getShort(WORLD_HEIGHT_KEY))
					.setFishBreedTime(savedInstanceState.getShort(FISH_BREED_TIME_KEY))
					.setSharkBreedTime(savedInstanceState.getShort(SHARK_BREED_TIME_KEY))
					.setSharkStarveTime(savedInstanceState.getShort(SHARK_STARVE_TIME_KEY))
					.setInitialFishCount(0)
					.setInitialSharkCount(0);
			simulator = new Simulator(parameters);
			short[] fishAge = savedInstanceState.getShortArray(FISH_AGE_KEY);
			if (fishAge != null) {
				short[] fishPosX = savedInstanceState.getShortArray(FISH_POSITIONS_X_KEY);
				if (fishPosX != null) {
					short[] fishPosY = savedInstanceState.getShortArray(FISH_POSITIONS_Y_KEY);
					if (fishPosY != null) {
						for (int fishNo = 0; fishNo < fishAge.length; fishNo++) {
							simulator.setFish(fishPosX[fishNo], fishPosY[fishNo], fishAge[fishNo]);
						}
					}
				}
			}
			short[] sharkAge = savedInstanceState.getShortArray(SHARK_AGE_KEY);
			if (sharkAge != null) {
				short[] sharkHunger = savedInstanceState.getShortArray(SHARK_HUNGER_KEY);
				if (sharkHunger != null) {
					short[] sharkPosX = savedInstanceState.getShortArray(SHARK_POSITIONS_X_KEY);
					if (sharkPosX != null) {
						short[] sharkPosY = savedInstanceState.getShortArray(SHARK_POSITIONS_Y_KEY);
						if (sharkPosY != null) {
							for (int sharkNo = 0; sharkNo < sharkAge.length; sharkNo++) {
								simulator.setShark(sharkPosX[sharkNo], sharkPosY[sharkNo], sharkAge[sharkNo], sharkHunger[sharkNo]);
							}
						}
					}
				}
			}

			if (savedInstanceState.containsKey(INITIAL_FISH_COUNT_KEY) || savedInstanceState.containsKey(INITIAL_SHARK_COUNT_KEY)) {
				if (savedInstanceState.containsKey(INITIAL_FISH_COUNT_KEY)) {
					parameters.setInitialFishCount(savedInstanceState.getInt(INITIAL_FISH_COUNT_KEY));
				}
				if (savedInstanceState.containsKey(INITIAL_SHARK_COUNT_KEY)) {
					parameters.setInitialSharkCount(savedInstanceState.getInt(INITIAL_SHARK_COUNT_KEY));
				}
				previousWorldParameters = parameters;
			}

		}

		simulatorRunnable = new SimulatorRunnable(simulator);
		if (savedInstanceState != null && savedInstanceState.containsKey(TARGET_FPS_KEY)) {
			simulatorRunnable.setTargetFps(savedInstanceState.getInt(TARGET_FPS_KEY));
		}
		simulatorRunnable.registerSimulatorRunnableObserver(this);

		drawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);
		drawerPane = findViewById(R.id.drawer_pane);
		drawerList = (ListView) findViewById(R.id.drawer_commands);
		drawerList.setAdapter(new DrawerListAdapter(getDrawerCommands()));
		drawerList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
				drawerCommandSelected(position);
			}
		});
		drawerToggle = new ActionBarDrawerToggle(this, drawerLayout, R.string.open_drawer_description, R.string.close_drawer_description) {
			@Override
			public void onDrawerOpened(View drawerView) {
				super.onDrawerOpened(drawerView);
				supportInvalidateOptionsMenu();
				synchronized (MainActivity.this) {
					desiredFpsSlider.setValue(simulatorRunnable.getTargetFps());
					simulatorFpsTextView = (TextView) findViewById(R.id.fps_simulator);
					drawingFpsTextView = (TextView) findViewById(R.id.fps_drawing);
				}
			}

			@Override
			public void onDrawerClosed(View drawerView) {
				super.onDrawerClosed(drawerView);
				supportInvalidateOptionsMenu();
				synchronized (MainActivity.this) {
					simulatorFpsTextView = null;
					drawingFpsTextView = null;
				}
			}
		};
		drawerLayout.setDrawerListener(drawerToggle);
	}

	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
//		// If the nav drawer is open, hide action items related to the content view
//		boolean drawerOpen = mDrawerLayout.isDrawerOpen(mDrawerList);
//		menu.findItem(R.id.action_search).setVisible(!drawerOpen);
		return super.onPrepareOptionsMenu(menu);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Pass the event to ActionBarDrawerToggle
		// If it returns true, then it has handled
		// the nav drawer indicator touch event
		if (drawerToggle.onOptionsItemSelected(item)) {
			return true;
		}

		//TODO: handle own action items

		return super.onOptionsItemSelected(item);
	}

	@Override
	protected void onPostCreate(@Nullable Bundle savedInstanceState) {
		super.onPostCreate(savedInstanceState);
		drawerToggle.syncState();
		FragmentManager fm = getSupportFragmentManager();
		newWorldFragment = fm.findFragmentById(R.id.new_world_fragment);
	}

	@Override
	protected void onResume() {
		super.onResume();

		drawingAverageFps = new RollingAverage();

		worldUpdateNotifierThread = new Thread(getString(R.string.worldUpdateNotifierThreadName)) {
			@Override
			public void run() {
				if (Log.isLoggable("Wa-Tor", Log.DEBUG)) { Log.d("Wa-Tor", "Entering world update notifier thread"); }
				try {
					long lastUpdateFinished = 0;
					while (Thread.currentThread() == worldUpdateNotifierThread) {
						long startUpdate = System.currentTimeMillis();
						if (Log.isLoggable("Wa-Tor", Log.VERBOSE)) { Log.v("Wa-Tor", "Notifying observers of world update"); }
						worldUpdated();
						if (Log.isLoggable("Wa-Tor", Log.VERBOSE)) { Log.v("Wa-Tor", "Notifying observers took " + (System.currentTimeMillis() - startUpdate) + " ms; waiting for next update"); }
						long now = System.currentTimeMillis();
						if (lastUpdateFinished > 0 && drawingAverageFps != null) {
							drawingAverageFps.add(now - lastUpdateFinished);
							if (Log.isLoggable("Wa-Tor", Log.VERBOSE)) {
								Log.v("Wa-Tor", "Duration since last redraw" + (now - lastUpdateFinished) + " ms");
								Log.v("Wa-Tor", "Notifying observers took " + (now - startUpdate) + " ms; waiting for next update");
							}
						}
						lastUpdateFinished = now;
						synchronized (this) {
							wait();
						}
					}
				} catch(InterruptedException e){
					if (Log.isLoggable("Wa-Tor", Log.DEBUG)) { Log.d("Wa-Tor", "World update notifier thread got interrupted"); }
					synchronized (this) {
						worldUpdateNotifierThread = null;
					}
				}
				if (Log.isLoggable("Wa-Tor", Log.DEBUG)) { Log.d("Wa-Tor", "Exiting world update notifier thread"); }
			}
		};
		worldUpdateNotifierThread.start();

		startSimulatorThread();
	}

	private void startSimulatorThread() {
		Thread simulatorThread = new Thread(simulatorRunnable, getString(R.string.simulatorThreadName));
		simulatorThread.start();
	}

	@Override
	protected void onPause() {
		super.onPause();
		synchronized (this) {
			Thread t = worldUpdateNotifierThread;
			worldUpdateNotifierThread = null;
			if (t != null) {
				t.interrupt();
			}
			simulatorRunnable.stopTicking();
		}
	}

	@Override
	public void registerSimulatorObserver(WorldObserver newObserver) {
		synchronized (worldObservers) {
			worldObservers.add(newObserver);
		}

	}

	@Override
	public void unregisterSimulatorObserver(WorldObserver goneObserver) {
		synchronized (worldObservers) {
			worldObservers.remove(goneObserver);
		}
	}

	@Override
	synchronized public void simulatorUpdated(Simulator simulator) {
		if (worldUpdateNotifierThread != null) {
			//noinspection SynchronizeOnNonFinalField
			synchronized (worldUpdateNotifierThread) {
				worldUpdateNotifierThread.notify();
			}
		}
	}

}
