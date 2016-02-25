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
import java.util.Set;

import com.dirkgassen.wator.R;
import com.dirkgassen.wator.simulator.RollingAverage;
import com.dirkgassen.wator.simulator.Simulator;
import com.dirkgassen.wator.simulator.SimulatorRunnable;
import com.dirkgassen.wator.simulator.WorldHost;
import com.dirkgassen.wator.simulator.WorldObserver;

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
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.SeekBar;
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
	private static final String FISH_REPRODUCTION_AGE_KEY = "fishReproductionAge";
	private static final String SHARK_REPRODUCTION_AGE_KEY = "sharkReproductionAge";
	private static final String SHARK_MAX_HUNGER_KEY = "fishMaxHunger";
	private static final String SHARK_POSITIONS_X_KEY = "sharkPositionsX";
	private static final String SHARK_POSITIONS_Y_KEY = "sharkPositionsY";
	private static final String WORLD_WIDTH_KEY = "worldWidth";
	private static final String WORLD_HEIGHT_KEY = "worldHeight";

	private static final short FISH_REPRODUCTION_AGE = 12;
	private static final short SHARK_REPRODUCTION_AGE = 7;
	private static final short SHARK_MAX_HUNGER = 18;
	private static final short WORLD_WIDTH = 200;
	private static final short WORLD_HEIGHT = 200;
	private static final int INITIAL_FISH = 600;
	private static final int INITIAL_SHARK = 350;

	private final Set<WorldObserver> worldObservers = new HashSet<WorldObserver>();

	private Simulator simulator;

	private SimulatorRunnable simulatorRunnable;

	private Thread worldUpdateNotifierThread;

	private RollingAverage drawingAverageFps;

	private TextView selectedFps;

	private SeekBar fpsSeekBar;

	private TextView simulatorFpsTextView;

	private TextView drawingFpsTextView;

	private DrawerLayout drawerLayout;

	private View drawerPane;

	private ListView drawerList;

	private Fragment newWorldFragment;

	private ActionBarDrawerToggle drawerToggle;

	private Handler handler;

	private Runnable updateFpsRunnable;

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
				new DrawerCommandItem(0, "Create New World", "Creates a new world")
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
			newWorldFragment = new NewWorld();
			ft.add(R.id.new_world_fragment, newWorldFragment);
		} else {
			ft.remove(newWorldFragment);
			newWorldFragment = null;
		}
		ft.commit();

	}

	@Override
	public void createWorld(short width, short height, short fishBreedTime, short sharkBreedTime, short sharkStarveTime, int initialFishCount, int initialSharkCount) {
		simulator = new Simulator(
				width, height,
				fishBreedTime,
				sharkBreedTime, sharkStarveTime,
				initialFishCount, initialSharkCount
		);
		simulatorRunnable.stopTicking();
		simulatorRunnable = new SimulatorRunnable(simulator);
		simulatorRunnable.registerSimulatorRunnableObserver(this);
		Thread simulatorThread = new Thread(simulatorRunnable, getString(R.string.simulatorThreadName));
		simulatorThread.start();
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
			int fishCount = world.getFishCount();
			int sharkCount = world.getSharkCount();
			short[] fishAge = new short[fishCount];
			short[] fishPosX = new short[fishCount];
			short[] fishPosY = new short[fishCount];
			short[] sharkAge = new short[sharkCount];
			short[] sharkHunger = new short[sharkCount];
			short[] sharkPosX = new short[sharkCount];
			short[] sharkPosY = new short[sharkCount];
			int fishNo = 0;
			int sharkNo = 0;
			do {
				if (world.isFish()) {
					fishAge[fishNo] = world.getFishAge();
					fishPosX[fishNo] = world.getCurrentX();
					fishPosY[fishNo++] = world.getCurrentY();
				} else if (world.isShark()) {
					sharkAge[sharkNo] = world.getSharkAge();
					sharkHunger[sharkNo] = world.getSharkHunger();
					sharkPosX[sharkNo] = world.getCurrentX();
					sharkPosY[sharkNo++] = world.getCurrentY();
				}
			} while (world.moveToNext() != Simulator.WORLD_INSPECTOR_MOVE_RESULT.RESET);
			outState.putShortArray(FISH_AGE_KEY, fishAge);
			outState.putShortArray(FISH_POSITIONS_X_KEY, fishPosX);
			outState.putShortArray(FISH_POSITIONS_Y_KEY, fishPosY);
			outState.putShortArray(SHARK_AGE_KEY, sharkAge);
			outState.putShortArray(SHARK_HUNGER_KEY, sharkHunger);
			outState.putShortArray(SHARK_POSITIONS_X_KEY, sharkPosX);
			outState.putShortArray(SHARK_POSITIONS_Y_KEY, sharkPosY);
			outState.putShort(WORLD_WIDTH_KEY, world.getWorldWidth());
			outState.putShort(WORLD_HEIGHT_KEY, world.getWorldHeight());
			outState.putShort(FISH_REPRODUCTION_AGE_KEY, world.getFishBreedTime());
			outState.putShort(SHARK_REPRODUCTION_AGE_KEY, world.getSharkBreedTime());
			outState.putShort(SHARK_MAX_HUNGER_KEY, world.getSharkStarveTime());
			outState.putShort(SHARK_MAX_HUNGER_KEY, world.getSharkStarveTime());
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

		selectedFps = (TextView) findViewById(R.id.selected_fps);
		fpsSeekBar = (SeekBar) findViewById(R.id.desired_fps);

		handler = new Handler();
		updateFpsRunnable = new Runnable() {
			@Override
			public void run() {
				synchronized (MainActivity.this) {
					if (simulatorFpsTextView != null) {
						simulatorFpsTextView.setText(Long.toString(simulatorRunnable.getAvgFps()));
					}
					if (drawingFpsTextView != null) {
						drawingFpsTextView.setText(Long.toString(drawingAverageFps.getAverage() == 0 ? 0 : 1000 / drawingAverageFps.getAverage()));
					}
				}
			}
		};


		if (savedInstanceState == null) {
			simulator = new Simulator(
					WORLD_WIDTH, WORLD_HEIGHT,
					FISH_REPRODUCTION_AGE,
					SHARK_REPRODUCTION_AGE, SHARK_MAX_HUNGER,
					INITIAL_FISH, INITIAL_SHARK
			);
		} else {
			simulator = new Simulator(
					savedInstanceState.getShort(WORLD_WIDTH_KEY), savedInstanceState.getShort(WORLD_HEIGHT_KEY),
					savedInstanceState.getShort(FISH_REPRODUCTION_AGE_KEY),
					savedInstanceState.getShort(SHARK_REPRODUCTION_AGE_KEY),
					savedInstanceState.getShort(SHARK_MAX_HUNGER_KEY)
			);
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
		}

		simulatorRunnable = new SimulatorRunnable(simulator);
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
				invalidateOptionsMenu();
				synchronized (MainActivity.this) {
					int targetFps = simulatorRunnable.getTargetFps();
					fpsSeekBar.setProgress(targetFps);
					if (targetFps == 0) {
						selectedFps.setText("Paused");
					} else {
						selectedFps.setText(Integer.toString(targetFps));
					}
					simulatorFpsTextView = (TextView) findViewById(R.id.fps_simulator);
					drawingFpsTextView = (TextView) findViewById(R.id.fps_drawing);
				}
			}

			@Override
			public void onDrawerClosed(View drawerView) {
				super.onDrawerClosed(drawerView);
				invalidateOptionsMenu();
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
