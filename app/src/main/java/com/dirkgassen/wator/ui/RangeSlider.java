/*
 * RangeSlider.java is part of Wa-Tor (C) 2016 by Dirk Gassen.
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

import java.util.Arrays;
import java.util.Locale;

import com.dirkgassen.wator.R;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.text.InputType;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

/**
 * A "range slider" that allows the user to select an integer value by dragging a thumb. The slider can have one of
 * two modes:
 * <dl>
 *     <dt>Min to Max</dt>
 *     <dd>The user can drag across a range between a minimum value and a maximum value</dd>
 *
 *     <dt>A set of values</dt>
 *     <dd>The user can select from a set of (ordered) values</dd>
 * </ol>
 */
public class RangeSlider extends View {

	/**
	 * Maximum for a click to have happened on the thumb. If the user touches the thumb and releases the finger in
	 * this time frame without changing the value a click has happened and the user can enter the value in an alert.
	 */
	private static final int MAX_CLICK_DURATION = 1000;

	/**  Interface to implement to listen for changes of the current value. */
	interface OnValueChangeListener {

		/**
		 * Called upon a change of the current value.
		 *
		 * @param slider The {@link RangeSlider} associated with this listener
		 * @param oldVal The previous value
		 * @param newVal The new value
		 * @param fromUser {@code true} if the user initiated the change from the UI; {@code false} if the change
		 *                             was done programmatically (e.g., by calling {@link #setValue(int)}})
		 */
		void onValueChange(RangeSlider slider, int oldVal, int newVal, boolean fromUser);

	}

	/** Paint for the text inside the thumb */
	final private Paint thumbTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

	/** Paint for the background of the thumb */
	final private Paint thumbBackgroundPaint = new Paint();

	/** Paint for the slider along which the thumb is dragged */
	final private Paint sliderPaint = new Paint();

	/** Path for the thumb */
	final Path thumbPath = new Path();

	/** Minimum selectable value (ignored if {@link #valueSet} is not {@code null}) */
	private int minValue;

	/** Maximum selectable value (ignored if {@link #valueSet} is not {@code null}) */
	private int maxValue;

	/** If this field is non-null then it is a sorted array of values that the user can pick from by sliding the thumb */
	private int[] valueSet;

	/** Current value of this slider */
	private int value;

	/** Padding between the outside of the thumb and the text of the thumb */
	private float thumbPadding;

	/** Formatting string for the thumb (the value should be represented by {@code %d}) */
	private String thumbFormat;

	private float thumbSize;
	/** (Precalculated) size of the thumb (width/height of the enclosing rectangle) */

	/** Density of the screen so that we don't always have to get the display metrics */
	private float displayDensity;

	/**
	 * The {@link RangeSlider.OnValueChangeListener} that should be called upon if the value changes. Can be set with
	 * {@link #setOnValueChangeListener(OnValueChangeListener)}).
	 */
	private OnValueChangeListener onValueChangeListener;

	/**
	 * When the user taps on the thumb and drags this field stores the offset between the original center of the
	 * thumb and where the user tapped.
	 */
	private float touchOffset;

	/**
	 * When a touch occurs this field will be set to the index of the pointer of the touch. While dragging we need
	 * to get the coordinates of that pointer for calculations.
	 */
	private int activePointerId = -1;

	/** Stores the value before the user started dragging so that we can restore the value in case dragging is canceled */
	private int valueBeforeDragging;

	/** Flag that we are dragging (currently not really used, may be in the future) */
	private boolean isDragging = false;

	/**
	 * Wall time of the start of a touch. This member will be reset if the thumb moves. It is being used to distinguish
	 * between a "click" and dragging.
	 */
	private long touchEventStartTime;

	/**
	 * Changes the value, invalidates the view and calls upon the {@link RangeSlider.OnValueChangeListener} (if set).
	 *
	 * @param newValue new value to set
	 * @param fromUser should be {@code true} if the new value is coming from the UI (user); otherwise {@code false}
	 */
	synchronized private void updateValue(int newValue, boolean fromUser) {
		int oldValue = value;
		value = newValue;
		invalidate();
		if (onValueChangeListener != null) {
			onValueChangeListener.onValueChange(this, oldValue, newValue, fromUser);
		}
	}

	/**
	 * Calculates the Euclidean distance between wo points
	 *
	 * @param x1 x coordinate of the first point
	 * @param y1 y coordinate of the first point
	 * @param x2 x coordinate of the second point
	 * @param y2 y coordinate of the second point
	 * @return euclidean distance between the two points
	 */
	private float distance(float x1, float y1, float x2, float y2) {
		float dx = x1 - x2;
		float dy = y1 - y2;
		float distanceInPx = (float) Math.sqrt(dx * dx + dy * dy);
		return pxToDp(distanceInPx);
	}

	/**
	 * Converts real pixels into density independent pixels.
	 *
	 * @param px pixels to convert
	 * @return converted pixels
	 */
	private float pxToDp(float px) {
		return px / displayDensity;
	}

	/**
	 * Returns the position of the center of the thumb that correlates to the current {@link #value}.
	 *
	 * @param paddingLeft left padding of this slider
	 * @param paddingRight right padding of this slider
	 * @return position of the center of the thumb
	 */
	private float positionFromValue(int paddingLeft, int paddingRight) {
		if (valueSet != null) {
			// Calculate for a value set: find the index of the value. We are finding the index of the first entry
			// which is larger than or equal to the value. The value _should_ always be an entry of the index, though.
			for (int no = 0; no < valueSet.length; no++) {
				if (value <= valueSet[no]) {
					if (Log.isLoggable("Wa-Tor", Log.DEBUG)) { Log.d("Wa-Tor", "Found current value " + value + " @ position " + no); }
					return (getWidth() - paddingLeft - paddingRight - thumbSize) * ((float) no) / (valueSet.length - 1) + paddingLeft + thumbSize / 2;
				}
			}
			return paddingLeft + thumbSize / 2;
		}

		// Calculate for a linear range
		return (getWidth() - paddingLeft - paddingRight - thumbSize) * ((float) value - minValue) / (maxValue - minValue) + paddingLeft + thumbSize / 2;
	}

	/**
	 * Calculates the value corresponding to the given position of a thumb
	 * @param position position to calculate the value from
	 * @param paddingLeft left padding of this slider
	 * @param paddingRight right padding of this slider
	 * @return value corresponding to the given position
	 */
	private int valueFromPosition(float position, int paddingLeft, int paddingRight) {
		if (valueSet != null) {
			// Calculate for a value setL first, figure out to which index the position corresponds to.
			// This is pretty much the calculation for a linear range from 0 to valueSet.length
			int value = (int) ((position - paddingLeft - thumbSize / 2) * (valueSet.length - 1) / (getWidth() - paddingLeft - paddingRight - thumbSize));
			// Limit to the bounds of the array
			if (value < 0) {
				return valueSet[0];
			} else if (value >= valueSet.length) {
				return valueSet[valueSet.length - 1];
			} else {
				return valueSet[value];
			}
		}

		// Calculate for a linear range
		int value = (int) ((position - paddingLeft - thumbSize / 2) * (maxValue - minValue) / (getWidth() - paddingLeft - paddingRight - thumbSize) + minValue);
		if (value < minValue) {
			return minValue;
		}
		if (value > maxValue) {
			return maxValue;
		}
		return value;
	}

	/**
	 * Quick'n'Dirty hack to return the number of the digits in the maximum possible value. This is probably possible
	 * with a simple calculation but I want to avoid logarithmic.
	 * @return number of digits in the maximum value
	 */
	private int getMaxDigits() {
		final int maxValue;
		if (valueSet != null) {
			maxValue = valueSet[valueSet.length-1];
		} else {
			maxValue = this.value;
		}
		return maxValue < 10 ? 1
				: maxValue < 100 ? 2
				: maxValue < 1000 ? 3
				: maxValue < 10000 ? 4
				: maxValue < 100000 ? 5
				: maxValue < 1000000 ? 6
				: maxValue < 10000000 ? 7
				: maxValue < 100000000 ? 8
				: 9;
	}

	/** (Pre)Calculates the size of the thumb and updates {@link #thumbSize} */
	private void calculateThumbSize() {
		final int maxValue;
		if (valueSet != null) {
			maxValue = valueSet[valueSet.length-1];
		} else {
			maxValue = this.maxValue;
		}
		// Ok, here's another Quick'n'Dirty hack: I don't know what the widest digit is in the different locales. For
		// now I assume it's the "9", Maybe it doesn't even matter. More likely this needs to be done a lot smarter.
		// We generate a string formatted according to "thumbFormat" with a number that has as many digits as the
		// maximum value but with all digits "9"
		final String demoValue = String.format(Locale.getDefault(), thumbFormat,
				maxValue < 10 ? 9
						: maxValue < 100 ? 99
						: maxValue < 1000 ? 999
						: maxValue < 10000 ? 9999
						: maxValue < 100000 ? 99999
						: maxValue < 1000000 ? 999999
						: maxValue < 10000000 ? 9999999
						: maxValue < 100000000 ? 99999999
						: 999999999
		);

		// Calculate the width of that string + padding on each side
		final float valueWidth = (thumbTextPaint.measureText(demoValue, 0, demoValue.length())) + 2 * thumbPadding;

		// Calculate the height of that string + padding on top and bottom
		final float valueTotalHeight = (-thumbTextPaint.ascent() + thumbTextPaint.descent());

		// The thumb size is the diameter of the circle that encloses the rectangle of the box
		thumbSize = (float) (Math.sqrt(valueWidth * valueWidth + valueTotalHeight + valueTotalHeight));
	}

	/**
	 * Read out the attributes from the given attribute set and initialize whatever they represent.
	 *
	 * @param attributeArray typed array containing the attribute values from the XML file
	 */
	private void setupAttributes(TypedArray attributeArray) {
		minValue = attributeArray.getInt(R.styleable.RangeSlider_minValue, 0);
		maxValue = attributeArray.getInt(R.styleable.RangeSlider_maxValue, 100);

		thumbPadding = attributeArray.getDimension(R.styleable.RangeSlider_thumbPadding, 6 /* dp */ * displayDensity);

		thumbFormat = attributeArray.getString(R.styleable.RangeSlider_thumbFormat);
		if (thumbFormat == null) {
			thumbFormat = "%d";
		}

		String valueSetString = attributeArray.getString(R.styleable.RangeSlider_valueSet);
		if (valueSetString == null) {
			value = attributeArray.getInt(R.styleable.RangeSlider_android_value, minValue);
		} else {
			String[] values = valueSetString.split(",");
			valueSet = new int[values.length];
			for (int no = 0; no < values.length; no++) {
				valueSet[no] = Integer.valueOf(values[no]);
			}
			Arrays.sort(valueSet);
			value = attributeArray.getInt(R.styleable.RangeSlider_android_value, valueSet[0]);
		}

		thumbTextPaint.setTextSize(attributeArray.getDimension(R.styleable.RangeSlider_android_textSize, 12));
		thumbTextPaint.setColor(attributeArray.getColor(R.styleable.RangeSlider_android_textColor, ContextCompat.getColor(getContext(), android.R.color.white)));

		thumbBackgroundPaint.setStyle(Paint.Style.FILL);
		thumbBackgroundPaint.setColor(attributeArray.getColor(R.styleable.RangeSlider_android_color, ContextCompat.getColor(getContext(), android.R.color.black)));

		sliderPaint.setStrokeWidth(attributeArray.getDimension(R.styleable.RangeSlider_sliderThickness, 2));
		sliderPaint.setStrokeCap(Paint.Cap.ROUND);
		sliderPaint.setColor(thumbBackgroundPaint.getColor());

	}

	/**
	 * Constructor that is called when this view is created from code.
	 *
	 * @param context context the view is running in, through which it can access the current theme, resources, etc.
	 */
	public RangeSlider(Context context) {
		super(context);

		thumbPath.setFillType(Path.FillType.EVEN_ODD);

		calculateThumbSize();
	}

	/**
	 * Constructor that is called when inflating a view from XML.
	 * This is called when a view is being constructed from an XML file, supplying attributes that were specified in the
	 * XML file. This version uses a default style of 0, so the only attribute values applied are those in the Context's
	 * Theme and the given AttributeSet.
	 *
	 * @param context context the view is running in, through which it can access the current theme, resources, etc.
	 * @param attrs   the attributes of the XML tag that is inflating the vie
	 */
	public RangeSlider(Context context, AttributeSet attrs) {
		super(context, attrs);

		thumbPath.setFillType(Path.FillType.EVEN_ODD);
		displayDensity = getResources().getDisplayMetrics().density;

		TypedArray attributeArray = context.getTheme().obtainStyledAttributes(
				attrs,
				R.styleable.RangeSlider,
				0, 0);
		try {
			setupAttributes(attributeArray);
		} finally {
			attributeArray.recycle();
		}

		calculateThumbSize();
	}

	/**
	 * Perform inflation from XML and apply a class-specific base style from a theme attribute or style resource.
	 * This constructor of View allows subclasses to use their own base style when they are inflating.
	 *
	 * When determining the final value of a particular attribute, there are four inputs that come into play:
	 * <ul>
	 *   <li>Any attribute values in the given AttributeSet.</li>
	 *   <li>The style resource specified in the {@code AttributeSet} (named "style").</li>
	 *   <li>The default style specified by {@code defStyleAttr}.</li>
	 *   <li>he default style specified by {@code defStyleRes}.</li>T
	 *   <li>The base values in this theme.</li>
	 * </ul>
	 *
	 * Each of these inputs is considered in-order, with the first listed taking precedence over the following ones.
	 *
	 * @param context      context the view is running in, through which it can access the current theme, resources, etc.
	 * @param attrs        the attributes of the XML tag that is inflating the vie
	 * @param defStyleAttr An attribute in the current theme that contains a reference to a style resource that supplies
	 *                     default values for the view. Can be 0 to not look for defaults.
	 * @param defStyleRes  A resource identifier of a style resource that supplies default values for the view, used
	 *                     only if {@code defStyleAttr} is 0 or can not be found in the theme. Can be 0 to not look for
	 *                     defaults.
	 */
	@TargetApi(Build.VERSION_CODES.LOLLIPOP)
	public RangeSlider(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
		super(context, attrs, defStyleAttr, defStyleRes);

		thumbPath.setFillType(Path.FillType.EVEN_ODD);
		displayDensity = getResources().getDisplayMetrics().density;

		TypedArray attributeArray = context.getTheme().obtainStyledAttributes(
				attrs,
				R.styleable.RangeSlider,
				defStyleAttr, defStyleRes);
		try {
			setupAttributes(attributeArray);
		} finally {
			attributeArray.recycle();
		}

		calculateThumbSize();
	}

	/**
	 * Called whenever the size of this view has changed.
	 *
	 * We are merely resetting the thumb path so that it gets redone when we draw next time.
	 *
	 * @param newWidth  width of the view after the size changed
	 * @param newHeight height of the view after the size changed
	 * @param oldWidth  width of the view before the size changed
	 * @param oldHeight height of the view before the size changed
	 */
	@Override
	protected void onSizeChanged(int newWidth, int newHeight, int oldWidth, int oldHeight) {
		super.onSizeChanged(newWidth, newHeight, oldWidth, oldHeight);
		thumbPath.reset();
	}

	/**
	 * Allows the user to click on the thumb. Clicking shows an alert that allows the user to enter a value. The
	 * entered value is adjusted to the minimum/maximum (or to be one of the value set if one is active).
	 *
	 * @return {@code true} to indicate that the click was handled.
	 */
	@Override
	public boolean performClick() {
		super.performClick();

		final Context c = this.getContext();
		final AlertDialog.Builder alert = new AlertDialog.Builder(c);
		final EditText input = new EditText(c);
		input.setInputType(InputType.TYPE_CLASS_NUMBER);
		input.setEms(getMaxDigits());
		alert
				.setView(input)
				.setTitle(getContext().getString(R.string.enter_new_value_title))
				.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int whichButton) {
						try {
							int enteredValue = Integer.valueOf(input.getText().toString().trim());
							int newValue;
							if (valueSet != null) {
								newValue = valueSet[valueSet.length-1];
								for (int searchValue : valueSet) {
									if (enteredValue <= searchValue) {
										newValue = searchValue;
										break;
									}
								}
							} else {
								newValue = enteredValue < minValue ? minValue
										: enteredValue > maxValue ? maxValue
										: enteredValue;
							}
							if (newValue != enteredValue) {
								Toast.makeText(c, c.getString(R.string.entered_value_adjusted, newValue), Toast.LENGTH_LONG).show();
							}
							if (newValue != value) {
								updateValue(newValue, true /* from user */);
							}
						} catch (NumberFormatException e) {
							Toast.makeText(c, R.string.invalid_value, Toast.LENGTH_SHORT).show();
						}
					}
				})
				.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int whichButton) {
						dialog.cancel();
					}
				})
				.show();

		return true;
	}

	/**
	 * Handle touch events: drag the thumb to new values. This method also detects clicks.
	 *
	 * @param event the touch event
	 * @return {@code true} if the event was handled; {@code false} otherwise
	 */
	@Override
	public boolean onTouchEvent(@NonNull MotionEvent event) {
		if (Log.isLoggable("Wa-Tor", Log.DEBUG)) { Log.d("Wa-Tor", "Got touch event: " + event); }
		int paddingLeft = getPaddingLeft();
		int paddingRight = getPaddingRight();
		switch (event.getAction() & MotionEvent.ACTION_MASK) {
			case MotionEvent.ACTION_DOWN:
				touchEventStartTime = System.currentTimeMillis();
				valueBeforeDragging = value;
				activePointerId = event.getPointerId(0);
				float valuePos = positionFromValue(paddingLeft, paddingRight);
				float x = event.getX();
				if (x < valuePos - thumbSize / 2 || x > valuePos + thumbSize / 2) {
					int newValue = valueFromPosition(x, paddingLeft, paddingRight);
					if (Log.isLoggable("Wa-Tor", Log.DEBUG)) { Log.d("Wa-Tor", "Starting to drag thumb OUTSIDE of thumb"); }
					if (newValue != value) {
						touchEventStartTime = 0L; // Not a click
						updateValue(newValue, true /* from user */);
					}
					touchOffset = 0f;
				} else {
					touchOffset = x - valuePos;
					if (Log.isLoggable("Wa-Tor", Log.DEBUG)) { Log.d("Wa-Tor", "Starting to drag thumb INSIDE of thumb; offset = " + touchOffset); }
				}
				isDragging = true;
				return true;
			case MotionEvent.ACTION_MOVE:
				if (activePointerId != -1) {
					final int pointerIndex = event.findPointerIndex(activePointerId);
					float currentPos = event.getX(pointerIndex) - touchOffset;
					int newValue = valueFromPosition(currentPos, paddingLeft, paddingRight);
					if (newValue != value) {
						if (Log.isLoggable("Wa-Tor", Log.DEBUG)) { Log.d("Wa-Tor", "Got new value " + newValue + " (old = " + value + ")"); }
						touchEventStartTime = 0L; // Not a click
						updateValue(newValue, true /* from user */);
					}
				}
				return true;
			case MotionEvent.ACTION_UP:
				if (touchEventStartTime > 0L && System.currentTimeMillis() - touchEventStartTime < MAX_CLICK_DURATION) {
					performClick();
				}
				isDragging = false;
				break;
			case MotionEvent.ACTION_CANCEL:
				isDragging = false;
				updateValue(valueBeforeDragging, true /* from user */);
				break;
			case MotionEvent.ACTION_POINTER_UP:
				isDragging = false;
				break;
		}

		return super.onTouchEvent(event);
	}

	/**
	 * Implement custom measuring of the view.
	 *
	 * @param widthMeasureSpec  horizontal space requirements as imposed by the parent. The requirements are encoded with
	 *                          {@link android.view.View.MeasureSpec}.
	 * @param heightMeasureSpec vertical space requirements as imposed by the parent. The requirements are encoded with
	 *                          {@link android.view.View.MeasureSpec}.
	 */
	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		int widthSize = MeasureSpec.getSize(widthMeasureSpec);
		int heightMode = MeasureSpec.getMode(heightMeasureSpec);
		int heightSize = MeasureSpec.getSize(heightMeasureSpec);

		final int height;
		if (heightMode == MeasureSpec.EXACTLY) {
			height = heightSize;
		} else {
			int paddingTop = getPaddingTop();
			int paddingBottom = getPaddingBottom();
			final float totalHeight = thumbSize + paddingTop + paddingBottom;
			if (heightMode == MeasureSpec.UNSPECIFIED || totalHeight < heightSize) {
				height = (int) (totalHeight + 1f); // round up
			} else {
				height = heightSize;
			}
		}
		setMeasuredDimension(widthSize, height);
	}

	/**
	 * Draws the view
	 * @param canvas canvas to paint on
	 */
	@Override
	protected void onDraw(Canvas canvas) {
		int paddingLeft = getPaddingLeft();
		int paddingTop = getPaddingTop();
		int paddingRight = getPaddingRight();
		int paddingBottom = getPaddingBottom();
		int sliderY = (getHeight() - paddingTop - paddingBottom) / 2 + paddingTop;

		canvas.drawLine(paddingLeft + thumbSize / 2, sliderY, getWidth() - paddingRight - thumbSize / 2, sliderY, sliderPaint);

		String valueString = String.format(Locale.getDefault(), thumbFormat, value);
		final float valueWidth = (thumbTextPaint.measureText(valueString, 0, valueString.length()));

		float thumbTip = positionFromValue(paddingLeft, paddingRight);
		if (thumbPath.isEmpty()) {
			thumbPath.addCircle(thumbTip, sliderY, thumbSize / 2, Path.Direction.CW);
		} else {
			thumbPath.offset(thumbTip, 0);
		}

		canvas.drawPath(thumbPath, thumbBackgroundPaint);

		canvas.drawText(valueString, thumbTip - valueWidth / 2, sliderY + (-thumbTextPaint.ascent()) / 2, thumbTextPaint);

		thumbPath.offset(-thumbTip, 0);
	}

	/** @return minimum value of this slider */
	public int getMinValue() {
		return minValue;
	}

	/**
	 * Changes the minimum value of this slider. Note: if the current value is smaller than the new minimum value
	 * the value is adjusted accordingly.
	 *
	 * @param minValue new minimum value
	 */
	public void setMinValue(int minValue) {
		if (minValue != this.minValue) {
			this.minValue = minValue;
			calculateThumbSize();
			if (value < minValue) {
				updateValue(minValue, false /* from user */);
			} else {
				invalidate();
			}
		}
	}

	/**
	 * @return maximum value of this slider
	 */
	public int getMaxValue() {
		return maxValue;
	}

	/**
	 * Changes the maximum value of this slider. Note: if the current value is smaller than the new minimum value
	 * the value is adjusted accordingly.
	 *
	 * @param maxValue new maximum value
	 */
	public void setMaxValue(int maxValue) {
		if (maxValue != this.maxValue) {
			this.maxValue = maxValue;
			if (value > maxValue) {
				updateValue(maxValue, true /* from user */);
			}
			invalidate();
		}
	}

	/** @return text size used to draw the thumb value */
	public float getTextSize() {
		return thumbTextPaint.getTextSize();
	}

	/**
	 * Changes the text size that is used to draw the thumb value
	 *
	 * @param textSize new text size for the thumb value
	 */
	public void setTextSize(float textSize) {
		if (textSize != thumbTextPaint.getTextSize()) {
			thumbTextPaint.setTextSize(textSize);
			invalidate();
		}
	}

	/** @return current value of the slider */
	public int getValue() {
		return value;
	}

	/**
	 * Changes the value of the slider. Note: the value is adusted to be in between the minimum and the maximum
	 * (or to be one of the value set values if a set is active)
	 *
	 * @param value new value for the slider
	 */
	public void setValue(int value) {
		if (valueSet != null) {

			// Value set

			if (value != this.value) {
				// Set the value to one of the value set
				int foundValue = valueSet[valueSet.length - 1];
				// Make sure that the final value is one in our value set
				for (int valueFromSet : valueSet) {
					if (value <= valueFromSet) {
						foundValue = valueFromSet;
						break;
					}
				}
				if (foundValue != this.value) {
					updateValue(foundValue, false /* from user */);
				}
			}
		} else {

			// Linear range: limit to the range

			if (value < minValue) {
				value = minValue;
			} else if (value > maxValue) {
				value = maxValue;
			}
			if (value != this.value) {
				updateValue(value, false /* from user */);
			}
		}
	}

	/** @return padding of the thumb */
	public float getThumbPadding() {
		return thumbPadding / displayDensity;
	}

	/**
	 * Changes the padding of the thumb
	 *
	 * @param thumbPadding new padding (on each side)
	 */
	public void setThumbPadding(float thumbPadding) {
		this.thumbPadding = thumbPadding * displayDensity;
	}

	/**
	 * Sets an {@link RangeSlider.OnValueChangeListener} that is called whenever the value of this slider changes. Calling this
	 * method replaces a previously set listener.
	 *
	 * @param onValueChangeListener new listener
	 */
	synchronized public void setOnValueChangeListener(OnValueChangeListener onValueChangeListener) {
		this.onValueChangeListener = onValueChangeListener;
	}

	/** @return currently active {@link RangeSlider.OnValueChangeListener} */
	public OnValueChangeListener getOnValueChangeListener() {
		return onValueChangeListener;
	}

}
