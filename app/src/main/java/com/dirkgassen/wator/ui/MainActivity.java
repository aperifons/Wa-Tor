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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.dirkgassen.wator.R;
import com.dirkgassen.wator.simulator.RollingAverage;
import com.dirkgassen.wator.simulator.Simulator;
import com.dirkgassen.wator.simulator.SimulatorRunnable;
import com.dirkgassen.wator.simulator.WorldHost;
import com.dirkgassen.wator.simulator.WorldObserver;
import com.dirkgassen.wator.simulator.WorldParameters;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.Nullable;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

/**
 * Main activity for the app.
 */
// Adding the hamburger menu: followed this tutorial:
//     http://codetheory.in/android-navigation-drawer/
public class MainActivity extends AppCompatActivity implements WorldHost, SimulatorRunnable.SimulatorRunnableObserver, NewWorld.WorldCreator {


	/** Stores information about one commad in the drawer. */
	abstract class DrawerCommandItem {

		/** ID that uniquely identifies the command */
		public final int id;

		/** ID of the icon of the command. */
		public final int icon;

		/** Command title. */
		public final String title;

		/** A description of the command. */
		public final String subtitle;

		/**
		 * Creates a new command
		 *
		 * @param id       ID for the new command
		 * @param icon     ID of the icon to use
		 * @param title    title for the command
		 * @param subtitle description of th ecommand
		 */
		DrawerCommandItem(int id, int icon, String title, String subtitle) {
			this.id = id;
			this.icon = icon;
			this.title = title;
			this.subtitle = subtitle;
		}

		abstract public void execute();
	}

	/** A {@link android.widget.ListAdapter} for our drawer commands */
	class DrawerListAdapter extends BaseAdapter {

		/** List of commands available */
		private final List<DrawerCommandItem> drawerCommands;

		/** Layout inflater to use to inflate the views for the items in the list */
		private final LayoutInflater inflater;

		/** @return number of commands in this adapter */
		@Override
		public int getCount() {
			return drawerCommands.size();
		}

		/**
		 * Returns a given command in the list.
		 *
		 * @param position position of the command in the list
		 * @return desired command
		 */
		@Override
		public Object getItem(int position) {
			return drawerCommands.get(position);
		}

		/**
		 * Returns the ID of the command at a certain position (if there is a command)
		 *
		 * @param position position of the command
		 * @return ID of the command
		 */
		@Override
		public long getItemId(int position) {
			DrawerCommandItem item = drawerCommands.get(position);
			if (item == null) {
				return 0;
			}
			return item.id;
		}

		/**
		 * Returns the view for the given position.
		 *
		 * @param position    index of the row
		 * @param convertView an existing view that should be recycled
		 * @param parent      The parent that this view will eventually be attached to
		 * @return            view for the position
		 */
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

		/**
		 * Creates a new drawer command adapter
		 *
		 * @param drawerCommands list of commands for this adapter
		 */
		public DrawerListAdapter(List<DrawerCommandItem> drawerCommands) {
			this.drawerCommands = drawerCommands;
			inflater = LayoutInflater.from(MainActivity.this);
		}

	}

	/** Groups the command IDs together */
	static class Commands {
		/** ID of the command to create a new world */
		private static final int NEW_WORLD_COMMAND = 1;

		/** Id of the "about" command */
		private static final int ABOUT_COMMAND = 2;
	}


	/** Groups the keys for saving world data in a Bundle */
	static class WorldKeys {
		/** Key for the fish age array */
		private static final String FISH_AGE_KEY = "fishAge";

		/** Key for the shark age array */
		private static final String SHARK_AGE_KEY = "sharkAge";

		/** Key for the fish age */
		private static final String SHARK_HUNGER_KEY = "sharkHunger";

		/** Key for the array containinng the x coordinates of the fish positions */
		private static final String FISH_POSITIONS_X_KEY = "fishPositionsX";

		/** Key for the array containing the y coordinates of the fish positions */
		private static final String FISH_POSITIONS_Y_KEY = "fishPositionsY";

		/** Key for the fish breed time */
		private static final String FISH_BREED_TIME_KEY = "fishBreedTime";

		/** Key for the shark breed time */
		private static final String SHARK_BREED_TIME_KEY = "sharkBreedTime";

		/** Key for the shark starve time */
		private static final String SHARK_STARVE_TIME_KEY = "sharkStarveTime";

		/** Key for the array containing the x coordinates of the shark positions */
		private static final String SHARK_POSITIONS_X_KEY = "sharkPositionsX";

		/** Key for the array containing the y coordinates of the shark positions */
		private static final String SHARK_POSITIONS_Y_KEY = "sharkPositionsY";

		/** Key for the width of the world */
		private static final String WORLD_WIDTH_KEY = "worldWidth";

		/** Key for the height of the world */
		private static final String WORLD_HEIGHT_KEY = "worldHeight";

		/** Key for the initial number of fish */
		private static final String INITIAL_FISH_COUNT_KEY = "initialFishCount";

		/** Key for the initial number of shark */
		private static final String INITIAL_SHARK_COUNT_KEY = "initialSharkCount";

		/** Key for the simulation FPS  */
		private static final String TARGET_FPS_KEY = "targetFps";
	}


	/**
	 * Set of {@link com.dirkgassen.wator.simulator.WorldObserver} objects that are notified whenever the simulator
	 * ticked.
	 */
	private final Set<WorldObserver> worldObservers = new HashSet<WorldObserver>();

	/** Simulator object that runs the world */
	private Simulator simulator;

	/** {@link java.lang.Runnable} for the thread that ticks the world */
	private SimulatorRunnable simulatorRunnable;

	/** Thread that updates the {@link #worldObservers} */
	private Thread worldUpdateNotifierThread;

	/** Displays the statistics of how many fish and shark exist in the world */
	private RollingAverage drawingAverageTime;

	private View newWorldView;

	/** Label for the "current frame rates" display */
	private TextView fpsLabel;

	/** Contains the current simulation frame rate */
	private TextView currentSimFps;

	/** Displays the current frame rate of the drawing */
	private TextView currentDrawFps;

	/** Slider for the desired frame rate */
	private RangeSlider desiredFpsSlider;

	/** The {@link DrawerLayout} of the main view */
	private DrawerLayout drawerLayout;

	/** Ties together the {@link android.support.v7.app.ActionBar} and our {@link #drawerLayout}*/
	private ActionBarDrawerToggle drawerToggle;

	/** Handler to run stuff on the UI thread */
	private Handler handler;

	/** A {@link Runnable} that updates the FPS in the drawer */
	private Runnable updateFpsRunnable;

	/** Stores the parameters of the most recently created world */
	private WorldParameters previousWorldParameters;

	/** The FPS indicators will be colored with this color when the FPS is lower than desired */
	private int fpsWarningColor;

	/** The FPS indicators will be colored with this color when the FPS is higher or equal to the desired FPS */
	private int fpsOkColor;

	/** Notifies all {@link #worldObservers} of a world change */
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
					if (world.getSharkCount() == 0) {
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
		if (currentSimFps != null || currentDrawFps != null) {
			handler.post(updateFpsRunnable);
		}
	}

	/**
	 * Creates a list with commands for our drawer.
	 *
	 * @return command list
	 */
	private List<DrawerCommandItem> getDrawerCommands() {
		List<DrawerCommandItem> commandList = new ArrayList<DrawerCommandItem>();
		commandList.add(
				new DrawerCommandItem(Commands.NEW_WORLD_COMMAND, 0, getString(R.string.create_new_world_command), getString(R.string.create_new_world_description)) {
					@Override
					public void execute() {
						drawerLayout.closeDrawers();
						toggleNewWorldFragment();
					}
				}
		);
		commandList.add(
				new DrawerCommandItem(Commands.ABOUT_COMMAND, 0, getString(R.string.about_command), getString(R.string.about_description)) {
					@Override
					public void execute() {
						showAbout();
					}
				}
		);
		return commandList;
	}

	/** Shows an alert with fancy info about this App. */
	@SuppressLint("InflateParams")
	private void showAbout() {
		View aboutView = getLayoutInflater().inflate(R.layout.about, null /* root */, true /* attachToRoot */);
		TextView versionView = (TextView) aboutView.findViewById(R.id.version);
		PackageInfo packageInfo;
		try {
			packageInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
			String version = getString(R.string.version, packageInfo.versionName, packageInfo.versionCode);
			versionView.setText(version);
		} catch (PackageManager.NameNotFoundException e) {
			versionView.setText(R.string.unknown_version);
		}

		new AlertDialog.Builder(this)
				//TODO: Integrate a pretty icon
//				.setIcon(R.drawable.)
				.setTitle(R.string.app_name)
				.setView(aboutView)
				.setCancelable(true)
				.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface d, int c) {
						d.dismiss();
					}
				})
				.create()
				.show();
	}

	/**
	 * Hides the "new world" fragment if it was showing.
	 * @return {@code true} if the fragment was visible; {@code false} otherwise
	 */
	synchronized private boolean hideNewWorldFragment() {
		if (newWorldView.getVisibility() != View.GONE) {
			newWorldView.startAnimation(AnimationUtils.loadAnimation(this, R.anim.slide_out_up));
			newWorldView.setVisibility(View.GONE);
			return true;
		}
		return false;
	}

	/** Toggles the state of the "new world" fragment: if it is currently open then hide it, otherwise show it. */
	synchronized private void toggleNewWorldFragment() {
		if (newWorldView.getVisibility() != View.GONE) {
			newWorldView.startAnimation(AnimationUtils.loadAnimation(this, R.anim.slide_out_up));
			newWorldView.setVisibility(View.GONE);
		} else {
			newWorldView.startAnimation(AnimationUtils.loadAnimation(this, R.anim.slide_in_down));
			newWorldView.setVisibility(View.VISIBLE);
		}
	}

	/**
	 * Return the parameters from which the current world was created
	 * @return parameters that created the current world
	 */
	@Override
	public WorldParameters getPreviousWorldParameters() {
		if (previousWorldParameters == null) {
			return new WorldParameters();
		}
		return previousWorldParameters;
	}

	/**
	 * Creates a new world with the given parameters. Depending on the current desired FPS the simulator thread
	 * is started (or not, if the desired FPS is 0).
	 * @param worldParameters parameters for the new world.
	 */
	@Override
	synchronized public void createWorld(WorldParameters worldParameters) {
		int targetFps = simulatorRunnable != null ? simulatorRunnable.getTargetFps() : -1;
		previousWorldParameters = worldParameters;
		if (simulatorRunnable != null) {
			simulatorRunnable.stopTicking();
		}
		simulator = new Simulator(worldParameters);
		simulatorRunnable = new SimulatorRunnable(simulator);
		if (targetFps >= 0) {
			simulatorRunnable.setTargetFps(targetFps);
		}
		simulatorRunnable.registerSimulatorRunnableObserver(this);
		startSimulatorThread();
		hideNewWorldFragment();
	}

	/** Cancels creating a new world: simply hide the "new world" fragment. */
	@Override
	public void cancelCreateWorld() {
		hideNewWorldFragment();
	}

	/**
	 * Saves the state of this instance:
	 * <ul>
	 *     <li>current state of the simulator: fish positions and age, shark positions and age and hunger</li>
	 *     <li>world parameters</li>
	 *     <li>desired fps</li>
	 * </ul>
	 * @param outState Bundle to save the state to
	 */
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
				outState.putShortArray(WorldKeys.FISH_AGE_KEY, fishAge);
				outState.putShortArray(WorldKeys.FISH_POSITIONS_X_KEY, fishPosX);
				outState.putShortArray(WorldKeys.FISH_POSITIONS_Y_KEY, fishPosY);
			}
			if (sharkCount > 0) {
				outState.putShortArray(WorldKeys.SHARK_AGE_KEY, sharkAge);
				outState.putShortArray(WorldKeys.SHARK_HUNGER_KEY, sharkHunger);
				outState.putShortArray(WorldKeys.SHARK_POSITIONS_X_KEY, sharkPosX);
				outState.putShortArray(WorldKeys.SHARK_POSITIONS_Y_KEY, sharkPosY);
			}
			outState.putShort(WorldKeys.WORLD_WIDTH_KEY, world.getWorldWidth());
			outState.putShort(WorldKeys.WORLD_HEIGHT_KEY, world.getWorldHeight());
			outState.putShort(WorldKeys.FISH_BREED_TIME_KEY, world.getFishBreedTime());
			outState.putShort(WorldKeys.SHARK_BREED_TIME_KEY, world.getSharkBreedTime());
			outState.putShort(WorldKeys.SHARK_STARVE_TIME_KEY, world.getSharkStarveTime());

			if (previousWorldParameters == null) {
				previousWorldParameters = new WorldParameters();
			}
			outState.putInt(WorldKeys.INITIAL_FISH_COUNT_KEY, previousWorldParameters.getInitialFishCount());
			outState.putInt(WorldKeys.INITIAL_SHARK_COUNT_KEY, previousWorldParameters.getInitialSharkCount());

			if (simulatorRunnable != null) {
				outState.putInt(WorldKeys.TARGET_FPS_KEY, simulatorRunnable.getTargetFps());
			}
		} finally {
			world.release();
		}
		super.onSaveInstanceState(outState);
	}

	/**
	 * Initializes this activity
	 * @param savedInstanceState if this parameter is not {@code null} the activity state is restored to the information
	 *                           in this Bundle
	 */
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main_layout);
		Toolbar myToolbar = (Toolbar) findViewById(R.id.main_toolbar);
		setSupportActionBar(myToolbar);
		// More info: http://codetheory.in/difference-between-setdisplayhomeasupenabled-sethomebuttonenabled-and-setdisplayshowhomeenabled/
		getSupportActionBar().setDisplayHomeAsUpEnabled(true);

		newWorldView = findViewById(R.id.new_world_fragment_container);
		fpsOkColor = ContextCompat.getColor(this, R.color.fps_ok_color);
		fpsWarningColor = ContextCompat.getColor(this, R.color.fps_warning_color);
		fpsLabel = (TextView) findViewById(R.id.label_fps);
		desiredFpsSlider = (RangeSlider) findViewById(R.id.desired_fps);
		desiredFpsSlider.setOnTouchListener(new View.OnTouchListener() {
			@SuppressLint("ClickableViewAccessibility")
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
			public void onValueChange(RangeSlider slider, int oldVal, int newVal, boolean fromUser) {
				synchronized (MainActivity.this) {
					if (newVal == 0) {
						fpsLabel.setVisibility(View.INVISIBLE);
						if (currentDrawFps != null) {
							currentDrawFps.setVisibility(View.INVISIBLE);
						}
						if (currentSimFps != null) {
							currentSimFps.setVisibility(View.INVISIBLE);
						}
					} else {
						fpsLabel.setVisibility(View.VISIBLE);
						if (currentDrawFps != null) {
							currentDrawFps.setVisibility(View.VISIBLE);
						}
						if (currentSimFps != null) {
							currentSimFps.setVisibility(View.VISIBLE);
						}
					}
					if (fromUser) {
						if (simulatorRunnable.setTargetFps(newVal)) {
							startSimulatorThread();
						}
					}
				}
			}
		});

		handler = new Handler();
		updateFpsRunnable = new Runnable() {
			@Override
			public void run() {
				synchronized (MainActivity.this) {
					if (currentSimFps != null) {
						float fps = simulatorRunnable.getAvgFps();
						currentSimFps.setText(getString(R.string.current_simulation_fps, (int) fps));
						int newTextColor = fps < simulatorRunnable.getTargetFps() ? fpsWarningColor : fpsOkColor;
						currentSimFps.setTextColor(newTextColor);
					}
					if (currentDrawFps != null) {
						float fps = drawingAverageTime.getAverage();
						if (fps != 0f) {
							fps = 1000 / fps;
						}
						currentDrawFps.setText(getString(R.string.current_drawing_fps, (int) fps));
						int newTextColor = fps < simulatorRunnable.getTargetFps() ? fpsWarningColor : fpsOkColor;
						currentDrawFps.setTextColor(newTextColor);
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
					.setWidth(savedInstanceState.getShort(WorldKeys.WORLD_WIDTH_KEY))
					.setHeight(savedInstanceState.getShort(WorldKeys.WORLD_HEIGHT_KEY))
					.setFishBreedTime(savedInstanceState.getShort(WorldKeys.FISH_BREED_TIME_KEY))
					.setSharkBreedTime(savedInstanceState.getShort(WorldKeys.SHARK_BREED_TIME_KEY))
					.setSharkStarveTime(savedInstanceState.getShort(WorldKeys.SHARK_STARVE_TIME_KEY))
					.setInitialFishCount(0)
					.setInitialSharkCount(0);
			simulator = new Simulator(parameters);
			short[] fishAge = savedInstanceState.getShortArray(WorldKeys.FISH_AGE_KEY);
			if (fishAge != null) {
				short[] fishPosX = savedInstanceState.getShortArray(WorldKeys.FISH_POSITIONS_X_KEY);
				if (fishPosX != null) {
					short[] fishPosY = savedInstanceState.getShortArray(WorldKeys.FISH_POSITIONS_Y_KEY);
					if (fishPosY != null) {
						for (int fishNo = 0; fishNo < fishAge.length; fishNo++) {
							simulator.setFish(fishPosX[fishNo], fishPosY[fishNo], fishAge[fishNo]);
						}
					}
				}
			}
			short[] sharkAge = savedInstanceState.getShortArray(WorldKeys.SHARK_AGE_KEY);
			if (sharkAge != null) {
				short[] sharkHunger = savedInstanceState.getShortArray(WorldKeys.SHARK_HUNGER_KEY);
				if (sharkHunger != null) {
					short[] sharkPosX = savedInstanceState.getShortArray(WorldKeys.SHARK_POSITIONS_X_KEY);
					if (sharkPosX != null) {
						short[] sharkPosY = savedInstanceState.getShortArray(WorldKeys.SHARK_POSITIONS_Y_KEY);
						if (sharkPosY != null) {
							for (int sharkNo = 0; sharkNo < sharkAge.length; sharkNo++) {
								simulator.setShark(sharkPosX[sharkNo], sharkPosY[sharkNo], sharkAge[sharkNo], sharkHunger[sharkNo]);
							}
						}
					}
				}
			}

			if (savedInstanceState.containsKey(WorldKeys.INITIAL_FISH_COUNT_KEY) || savedInstanceState.containsKey(WorldKeys.INITIAL_SHARK_COUNT_KEY)) {
				if (savedInstanceState.containsKey(WorldKeys.INITIAL_FISH_COUNT_KEY)) {
					parameters.setInitialFishCount(savedInstanceState.getInt(WorldKeys.INITIAL_FISH_COUNT_KEY));
				}
				if (savedInstanceState.containsKey(WorldKeys.INITIAL_SHARK_COUNT_KEY)) {
					parameters.setInitialSharkCount(savedInstanceState.getInt(WorldKeys.INITIAL_SHARK_COUNT_KEY));
				}
				previousWorldParameters = parameters;
			}

		}

		simulatorRunnable = new SimulatorRunnable(simulator);
		if (savedInstanceState != null && savedInstanceState.containsKey(WorldKeys.TARGET_FPS_KEY)) {
			simulatorRunnable.setTargetFps(savedInstanceState.getInt(WorldKeys.TARGET_FPS_KEY));
		}
		simulatorRunnable.registerSimulatorRunnableObserver(this);

		drawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);
		final ListView drawerList = (ListView) findViewById(R.id.drawer_commands);
		drawerList.setAdapter(new DrawerListAdapter(getDrawerCommands()));
		drawerList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
				DrawerCommandItem command = (DrawerCommandItem) drawerList.getItemAtPosition(position);
				if (command != null) {
					command.execute();
				}
			}
		});
		drawerToggle = new ActionBarDrawerToggle(this, drawerLayout, R.string.open_drawer_description, R.string.close_drawer_description) {
			@Override
			public void onDrawerOpened(View drawerView) {
				super.onDrawerOpened(drawerView);
				supportInvalidateOptionsMenu();
				synchronized (MainActivity.this) {
					desiredFpsSlider.setValue(simulatorRunnable.getTargetFps());
					currentSimFps = (TextView) findViewById(R.id.fps_simulator);
					currentDrawFps = (TextView) findViewById(R.id.fps_drawing);
				}
			}

			@Override
			public void onDrawerClosed(View drawerView) {
				super.onDrawerClosed(drawerView);
				supportInvalidateOptionsMenu();
				synchronized (MainActivity.this) {
					currentSimFps = null;
					currentDrawFps= null;
				}
			}
		};
		drawerLayout.setDrawerListener(drawerToggle);
	}

	/**
	 * Intercept the back press. We want to not perform the default action if
	 * <ul>
	 *     <li>the drawer is showing (instead, hide the drawer)</li>
	 *     <li>the "new world" fragment is showing (hide it)</li>
	 * </ul>
	 * Otherwise call through to {@code super}.
	 */
	@Override
	public void onBackPressed() {
		if (drawerLayout.isDrawerVisible(GravityCompat.START)) {
			drawerLayout.closeDrawers();
		} else if (!hideNewWorldFragment()) {
			super.onBackPressed();
		}
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

	/**
	 * Handle post-create initialization. We cannot find fragments in the activity in {@link #onCreate(Bundle)}.
	 * We also need to synchronize the state of the {@link #drawerToggle} to get the hamburger menu.
	 * @param savedInstanceState Bundle with state to restore (ignored)
	 */
	@Override
	protected void onPostCreate(@Nullable Bundle savedInstanceState) {
		super.onPostCreate(savedInstanceState);
		drawerToggle.syncState();
	}

	@Override
	protected void onResume() {
		super.onResume();

		drawingAverageTime = new RollingAverage();

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
						if (lastUpdateFinished > 0 && drawingAverageTime != null) {
							drawingAverageTime.add(now - lastUpdateFinished);
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

	/**
	 * Add another {@link WorldObserver} to our list of observers.
	 * @param newObserver observer to add
	 */
	@Override
	public void registerSimulatorObserver(WorldObserver newObserver) {
		synchronized (worldObservers) {
			worldObservers.add(newObserver);
		}

	}

	/**
	 * Remove a {@link WorldObserver} from our list of observers.
	 * @param goneObserver observer to remove
	 */
	@Override
	public void unregisterSimulatorObserver(WorldObserver goneObserver) {
		synchronized (worldObservers) {
			worldObservers.remove(goneObserver);
		}
	}

	/**
	 * Called whenever the {@link SimulatorRunnable} has finished calculating a new world. We need to tell the
	 * registered {@link WorldObserver} objects about this fact.
	 * @param simulator simulator that has ticked
	 */
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
