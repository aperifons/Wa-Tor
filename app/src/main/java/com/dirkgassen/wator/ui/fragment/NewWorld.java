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

package com.dirkgassen.wator.ui.fragment;

import java.util.Locale;

import com.dirkgassen.wator.R;
import com.dirkgassen.wator.simulator.Simulator;
import com.dirkgassen.wator.simulator.WorldHost;
import com.dirkgassen.wator.simulator.WorldParameters;

import android.content.Context;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.annotation.StringRes;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;

/**
 * A fragment that allows the user to enter parameter for a new world and create it (or cancel the process).
 * The fragment must be placed into an activity (or another fragment) that implements {@link NewWorld.WorldCreator}.
 * This fragment does not create a new simulator itself but rather communicates with the host (via the
 * {@link NewWorld.WorldCreator} interface).
 */
public class NewWorld extends Fragment {

	/**
	 * Interface that the host must implement.
	 */
	public interface WorldCreator
		/** @return the world parameters that were previously used to create a world */{
		WorldParameters getPreviousWorldParameters();

		/**
		 * Create a new world with the given parameters
		 *
		 * @param worldParameters parameters for the new world
		 */
		void createWorld(WorldParameters worldParameters);

		/**
		 * Cancel creating the new world. This usually is basically hiding the {@link NewWorld} fragment without
		 * doing anything else
		 */
		void cancelCreateWorld();
	}

	/**
	 * Helper class to implement a {@link TextWatcher} that is not interested in
	 * {@link TextWatcher#beforeTextChanged(CharSequence, int, int, int)} or
	 * {@link TextWatcher#onTextChanged(CharSequence, int, int, int)}. The derived class only needs (and must) implement
	 * {@link TextWatcher#afterTextChanged(Editable)}.
	 */
	abstract class AfterTextWatcher implements TextWatcher {

		/**
		 * Default implementation that does nothing
		 *
		 * @param s ignored
		 * @param start ignored
 		 * @param count ignored
		 * @param after ignored
		 */
		@Override
		public void beforeTextChanged(CharSequence s, int start, int count, int after) {
			// Nothing to do here
		}

		/**
		 * Default implementation that does nothing
		 * @param s ignored
		 * @param start ignored
		 * @param before ignored
		 * @param count ignored
		 */
		@Override
		public void onTextChanged(CharSequence s, int start, int before, int count) {
			// Nothing to do here either
		}

	}

	/** Index in {@link #inputs} for the world width {@link EditText} */
	final private static int WORLD_WIDTH_INPUT = 0;

	/** Index in {@link #inputs} for the world height {@link EditText} */
	final private static int WORLD_HEIGHT_INPUT = 1;

	/** Index in {@link #inputs} for the fish breed time {@link EditText} */
	final private static int FISH_BREED_INPUT = 2;

	/** Index in {@link #inputs} for the shark breed time {@link EditText} */
	final private static int SHARK_BREED_INPUT = 3;

	/** Index in {@link #inputs} for the shark starve time  {@link EditText} */
	final private static int SHARK_STARVE_INPUT = 4;

	/** Index in {@link #inputs} for the initial number of fish {@link EditText} */
	final private static int INITIAL_FISH_COUNT_INPUT = 5;

	/** Index in {@link #inputs} for the initial number of shark {@link EditText} */
	final private static int INITIAL_SHARK_COUNT_INPUT = 6;

	/** Number of elements in the {@link #inputs} array */
	final private static int INPUT_COUNT = 7;

	/** Contains the {@link EditText} fields for the world parameters */
	final private EditText[] inputs = new EditText[INPUT_COUNT];

	/** The button that creates a new world */
	private Button newWorldButton;

	/** Reference to the host of this fragment */
	private WorldCreator worldCreator;

	/**
	 * Checks the given text for validity: the text must not be empty and the value must be between {@code min} and
	 * {@code max}. If the text is not valid one of the given string resources is loaded and set as the error text
	 * on the given {@link EditText}.
	 *
	 * This method can be called to validate the text from a {@link AfterTextWatcher}.
	 *
	 * @param inputNo index into the {@link #inputs} array that specifies which {@link EditText} is being validated
	 * @param s       the text entered and to be verified
	 * @param min     minimum value to check for
	 * @param max     maximum value to check for
	 * @param emptyErrorResourceId string resource ID for the error when the text is empty
	 * @param minErrorResourceId string resource ID for the error when the text is a value that is smaller than {@code min}
	 * @param maxErrorResourceId string resource ID for the error when the text is a value that is larger than {@code max}
	 * @return        {@code true} if the entered text is valid; {@code false} otherwise
	 */
	private boolean doMinMaxCheck(int inputNo, Editable s, int min, int max,
	                              @StringRes int emptyErrorResourceId,
	                              @StringRes int minErrorResourceId,
	                              @StringRes int maxErrorResourceId) {
		if (s.length() == 0) {
			inputs[inputNo].setError(getString(emptyErrorResourceId));
			return false;
		}
		int value = Integer.valueOf(s.toString());
		if (value < min) {
			inputs[inputNo].setError(getString(minErrorResourceId, min));
			return false;
		} else if (value > max) {
			inputs[inputNo].setError(getString(maxErrorResourceId, max));
			return false;
		} else {
			inputs[inputNo].setError(null);
			return true;
		}
	}

	/**
	 * Validate the "world width" {@link EditText}. The text is already provided as a parameter.
	 *
	 * @param  s text of the "world width" {@link EditText}
	 * @return {@code true} if the entered text is valid; {@code false} otherwise
	 */
	private boolean validateWorldWidth(Editable s) {
		return doMinMaxCheck(WORLD_WIDTH_INPUT, s, 1, Simulator.MAX_WORLD_WIDTH, R.string.world_width_empty_error, R.string.world_width_too_small_error, R.string.world_width_too_large_error);
	}

	/**
	 * Validate the "world height" {@link EditText}. The text is already provided as a parameter.
	 *
	 * @param  s text of the "world height" {@link EditText}
	 * @return {@code true} if the entered text is valid; {@code false} otherwise
	 */
	private boolean validateWorldHeight(Editable s) {
		return doMinMaxCheck(WORLD_HEIGHT_INPUT, s, 1, Simulator.MAX_WORLD_HEIGHT, R.string.world_height_empty_error, R.string.world_height_too_small_error, R.string.world_height_too_large_error);
	}

	/**
	 * Validate the "fish breed time" {@link EditText}. The text is already provided as a parameter.
	 *
	 * @param  s text of the "fish breed time" {@link EditText}
	 * @return {@code true} if the entered text is valid; {@code false} otherwise
	 */
	private boolean validateFishBreedTime(Editable s) {
		return doMinMaxCheck(FISH_BREED_INPUT, s, 1, Simulator.MAX_FISH_BREED_TIME, R.string.fish_breed_time_empty_error, R.string.fish_breed_time_too_small_error, R.string.fish_breed_time_too_large_error);
	}

	/**
	 * Validate the "shark breed time" {@link EditText}. The text is already provided as a parameter.
	 *
	 * @param  s text of the "shark breed time" {@link EditText}
	 * @return {@code true} if the entered text is valid; {@code false} otherwise
	 */
	private boolean validateSharkBreedTime(Editable s) {
		return doMinMaxCheck(SHARK_BREED_INPUT, s, 1, Simulator.MAX_SHARK_BREED_TIME, R.string.shark_breed_time_empty_error, R.string.shark_breed_time_too_small_error, R.string.shark_breed_time_too_large_error);
	}

	/**
	 * Validate the "shark breed time" {@link EditText}. The text is already provided as a parameter.
	 *
	 * @param s text of the "shark breed time" {@link EditText}
	 * @return {@code true} if the entered text is valid; {@code false} otherwise
	 */
	private boolean validateSharkStarveTime(Editable s) {
		return doMinMaxCheck(SHARK_STARVE_INPUT, s, 1, Simulator.MAX_SHARK_STARVE_TIME, R.string.shark_starve_time_empty_error, R.string.shark_starve_time_too_small_error, R.string.shark_starve_time_too_large_error);
	}

	/**
	 * Validate the "initial fish count" {@link EditText}. The text is already provided as a parameter.
	 *
	 * If the text is valid the "initial shark count" {@link EditText} is validated as well to eventually remove an
	 * error that previously existed (e.g., number of initial fish was reduced so that the number of shark are now valid).
	 *
	 * Note that the "initial shark count" {@link EditText} is not validated if the text is invalid to not add
	 * an error message to the "initial shark count" {@link EditText} if this text value is invalid.
	 * @param s         text of the "initial fish count" {@link EditText}
	 * @param worldSize size of the world
	 */
	private void validateInitialFishCount(Editable s, int worldSize) {
		if (s.length() == 0) {
			inputs[INITIAL_FISH_COUNT_INPUT].setError(getString(R.string.initial_fish_count_empty_error));
		} else {
			Editable sharkCount = inputs[INITIAL_SHARK_COUNT_INPUT].getText();
			int initialFishValue = Integer.valueOf(s.toString());
			int initialSharkCount = sharkCount.length() == 0 ? 0 : Integer.valueOf(sharkCount.toString());
			int max = worldSize - initialSharkCount;
			if (max < 0) {
				max = 0;
			}
			if (initialFishValue > max) {
				inputs[INITIAL_FISH_COUNT_INPUT].setError(getString(R.string.too_many_fish_error, max));
			} else {
				inputs[INITIAL_FISH_COUNT_INPUT].setError(null);
				if (sharkCount.length() > 0 && initialSharkCount < worldSize - initialFishValue) {
					inputs[INITIAL_SHARK_COUNT_INPUT].setError(null);
				}
			}
		}
	}


	/**
	 * Validate the "initial fish count" {@link EditText}. The text is already provided as a parameter.
	 *
	 * If the text is valid the "initial shark count" {@link EditText} is validated as well to eventually remove an
	 * error that previously existed (e.g., number of initial fish was reduced so that the number of shark are now valid).
	 *
	 * Note that the "initial shark count" {@link EditText} is not validated if the text is invalid to not add
	 * an error message to the "initial shark count" {@link EditText} if this text value is invalid.
	 *
	 * @param s         text of the "initial fish count" {@link EditText}
	 * @param worldSize size of the world
	 */
	private void validateInitialSharkCount(Editable s, int worldSize) {
		if (s.length() == 0) {
			inputs[INITIAL_SHARK_COUNT_INPUT].setError(getString(R.string.initial_shark_count_empty_error));
		} else {
			Editable fishCountEditable = inputs[INITIAL_FISH_COUNT_INPUT].getText();
			int initialSharkValue = Integer.valueOf(s.toString());
			int initialFishValue = fishCountEditable.length() == 0 ? 0 : Integer.valueOf(fishCountEditable.toString());
			int max = worldSize - initialFishValue;
			if (max < 0) {
				max = 0;
			}
			if (initialSharkValue > max) {
				inputs[INITIAL_SHARK_COUNT_INPUT].setError(getString(R.string.too_many_shark_error, max));
			} else {
				inputs[INITIAL_SHARK_COUNT_INPUT].setError(null);
				if (fishCountEditable.length() > 0 && initialFishValue < worldSize - initialSharkValue) {
					inputs[INITIAL_FISH_COUNT_INPUT].setError(null);
				}
			}
		}
	}

	/**
	 * Checks whether we we can enable the "new world" button (no errors present) or not and enable or disable the
	 * button accordingly.
	 */
	private void enDisableNewWorldButton() {
		for (EditText input: inputs) {
			if (input.getError() != null) {
				newWorldButton.setEnabled(false);
				return;
			}
		}
		newWorldButton.setEnabled(true);
	}

	/** Call the {@link #worldCreator} to create the new world with the entered values. */
	private void createWorld() {
		worldCreator.createWorld(
				new WorldParameters()
						.setWidth(Short.valueOf(inputs[WORLD_WIDTH_INPUT].getText().toString()))
						.setHeight(Short.valueOf(inputs[WORLD_HEIGHT_INPUT].getText().toString()))
						.setFishBreedTime(Short.valueOf(inputs[FISH_BREED_INPUT].getText().toString()))
						.setSharkBreedTime(Short.valueOf(inputs[SHARK_BREED_INPUT].getText().toString()))
						.setSharkStarveTime(Short.valueOf(inputs[SHARK_STARVE_INPUT].getText().toString()))
						.setInitialFishCount(Short.valueOf(inputs[INITIAL_FISH_COUNT_INPUT].getText().toString()))
						.setInitialSharkCount(Short.valueOf(inputs[INITIAL_SHARK_COUNT_INPUT].getText().toString()))
		);
	}

	/**
	 * Initialize the view of the fragment
	 * @param inflater    inflater to use to inflate layouts
	 * @param container   container of this fragment
	 * @param savedInstanceState if this parameter is not {@code null} it contains a saved state to which this
	 *                    fragment should be restored
	 * @return inflated and initialized view
	 */
	@Nullable
	@Override
	public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
		View v = inflater.inflate(R.layout.new_world, container, false /* attachToRoot */);
		newWorldButton = (Button) v.findViewById(R.id.create_new_world);
		newWorldButton.setOnClickListener(new View.OnClickListener() {
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

		inputs[WORLD_WIDTH_INPUT] = (EditText) v.findViewById(R.id.world_width);
		inputs[WORLD_HEIGHT_INPUT] = (EditText) v.findViewById(R.id.world_height);
		inputs[FISH_BREED_INPUT] = (EditText) v.findViewById(R.id.fish_breed_time);
		inputs[SHARK_BREED_INPUT] = (EditText) v.findViewById(R.id.shark_breed_time);
		inputs[SHARK_STARVE_INPUT] = (EditText) v.findViewById(R.id.shark_starve);
		inputs[INITIAL_FISH_COUNT_INPUT] = (EditText) v.findViewById(R.id.initial_fish_count);
		inputs[INITIAL_SHARK_COUNT_INPUT] = (EditText) v.findViewById(R.id.initial_shark_count);

		WorldParameters worldParameters = worldCreator.getPreviousWorldParameters();
		if (worldParameters == null) {
			worldParameters = new WorldParameters();
		}
		inputs[WORLD_WIDTH_INPUT].setText(String.format(Locale.getDefault(), "%d", worldParameters.getWidth()));
		inputs[WORLD_HEIGHT_INPUT].setText(String.format(Locale.getDefault(), "%d", worldParameters.getHeight()));
		inputs[FISH_BREED_INPUT].setText(String.format(Locale.getDefault(), "%d", worldParameters.getFishBreedTime()));
		inputs[SHARK_BREED_INPUT].setText(String.format(Locale.getDefault(), "%d", worldParameters.getSharkBreedTime()));
		inputs[SHARK_STARVE_INPUT].setText(String.format(Locale.getDefault(), "%d", worldParameters.getSharkStarveTime()));
		inputs[INITIAL_FISH_COUNT_INPUT].setText(String.format(Locale.getDefault(), "%d", worldParameters.getInitialFishCount()));
		inputs[INITIAL_SHARK_COUNT_INPUT].setText(String.format(Locale.getDefault(), "%d", worldParameters.getInitialSharkCount()));

		inputs[WORLD_WIDTH_INPUT].addTextChangedListener(new AfterTextWatcher() {
			@Override
			public void afterTextChanged(Editable s) {
				if (validateWorldWidth(s) && inputs[WORLD_HEIGHT_INPUT].length() > 0) {
					int worldSize = Integer.valueOf(inputs[WORLD_WIDTH_INPUT].getText().toString())
							* Integer.valueOf(inputs[WORLD_HEIGHT_INPUT].getText().toString());
					validateInitialFishCount(inputs[INITIAL_FISH_COUNT_INPUT].getText(), worldSize);
					validateInitialSharkCount(inputs[INITIAL_SHARK_COUNT_INPUT].getText(), worldSize);
				}
				enDisableNewWorldButton();
			}
		});
		inputs[WORLD_HEIGHT_INPUT].addTextChangedListener(new AfterTextWatcher() {
			@Override
			public void afterTextChanged(Editable s) {
				if (validateWorldHeight(s) && inputs[WORLD_WIDTH_INPUT].length() > 0) {
					int worldSize = Integer.valueOf(inputs[WORLD_WIDTH_INPUT].getText().toString())
							* Integer.valueOf(inputs[WORLD_HEIGHT_INPUT].getText().toString());
					validateInitialFishCount(inputs[INITIAL_FISH_COUNT_INPUT].getText(), worldSize);
					validateInitialSharkCount(inputs[INITIAL_SHARK_COUNT_INPUT].getText(), worldSize);
				}
				enDisableNewWorldButton();
			}
		});
		inputs[FISH_BREED_INPUT].addTextChangedListener(new AfterTextWatcher() {
			@Override
			public void afterTextChanged(Editable s) {
				validateFishBreedTime(s);
				enDisableNewWorldButton();
			}
		});
		inputs[SHARK_BREED_INPUT].addTextChangedListener(new AfterTextWatcher() {
			@Override
			public void afterTextChanged(Editable s) {
				validateSharkBreedTime(s);
				enDisableNewWorldButton();
			}
		});
		inputs[SHARK_STARVE_INPUT].addTextChangedListener(new AfterTextWatcher() {
			@Override
			public void afterTextChanged(Editable s) {
				validateSharkStarveTime(s);
				enDisableNewWorldButton();
			}
		});
		inputs[INITIAL_FISH_COUNT_INPUT].addTextChangedListener(new AfterTextWatcher() {
			@Override
			public void afterTextChanged(Editable s) {
				int worldSize = inputs[WORLD_WIDTH_INPUT].length() == 0 || inputs[WORLD_HEIGHT_INPUT].length() == 0 ?
						-1
						: Integer.valueOf(inputs[WORLD_WIDTH_INPUT].getText().toString())
						* Integer.valueOf(inputs[WORLD_HEIGHT_INPUT].getText().toString());
				validateInitialFishCount(inputs[INITIAL_FISH_COUNT_INPUT].getText(), worldSize);
				enDisableNewWorldButton();
			}
		});
		inputs[INITIAL_SHARK_COUNT_INPUT].addTextChangedListener(new AfterTextWatcher() {
			@Override
			public void afterTextChanged(Editable s) {
				int worldSize = inputs[WORLD_WIDTH_INPUT].length() == 0 || inputs[WORLD_HEIGHT_INPUT].length() == 0 ?
						-1
						: Integer.valueOf(inputs[WORLD_WIDTH_INPUT].getText().toString())
						* Integer.valueOf(inputs[WORLD_HEIGHT_INPUT].getText().toString());
				validateInitialSharkCount(inputs[INITIAL_SHARK_COUNT_INPUT].getText(), worldSize);
				enDisableNewWorldButton();
			}
		});

		return v;
	}

	/**
	 * After the fragment has been attached to the parent we need to get the activity that embedded the fragment.
	 * @param context context to use
	 */
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
