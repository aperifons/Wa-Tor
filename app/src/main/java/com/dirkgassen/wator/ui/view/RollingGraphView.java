/*
 * RollingGraphView.java is part of Wa-Tor (C) 2016 by Dirk Gassen.
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

package com.dirkgassen.wator.ui.view;

import java.security.InvalidParameterException;

import com.dirkgassen.wator.R;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Handler;
import android.support.v4.content.ContextCompat;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;

/**
 * A view that shows a rolling graph. The graph can (currently) either roll off to the left (with new values
 * appearing on the right side) or down (with new values appearing at the top).
 */
public class RollingGraphView extends View {

	/** Names of the series */
	private String seriesNames[];

	/** Times at which data values are recorded (not uesed yet) */
	private long[] dataTimes;

	/** Data points. The first index is the index of the data point while the second specifes the series no. */
	private float[][] dataValues;

	/**
	 * Index of the oldest value in {@link #dataValues}. The array can be longer than the number of contained
	 * data points.
	 */
	private int oldestValue;

	/**
	 * Index of the current data point. This index is incremented when new values are added and eventuall rolls back
	 * to zero.
	 */
	private int currentValue = -1; // No values yet

	/**
	 * Bitmap that the current graph is painted to.
	 */
	private Bitmap rollingGraphBitmap;

	/** Canvas for painting the grpah */
	private Canvas rollingGraphCanvas;

	/** Maximum Number of values to store */
	private int maxValues;

	/** Specifies whether the graph should be horizontal or vertical */
	private boolean horizontal;

	/** The {@link Paint} objects for each series */
	private Paint[] seriesPaints;

	/** The {@link Drawable} for drawing the background */
	private Drawable background;

	/** Density of the screen so that we don't always have to get the display metrics */
	private float displayDensity;

	/** If this flag is {@code true} the bitmap {@link #rollingGraphBitmap} needs to be repainted */
	private boolean bitMapIsInvalid;

	/** A rectangle that can hold the bounding box of a series label */
	final private Rect nameBounds = new Rect();

	/** Handler to run stuff on the UI thread */
	final private Handler handler;

	/** A {@link Runnable} that invalidates the view */
	final private Runnable invalidateRunner = new Runnable() {
		@Override
		public void run() {
			invalidate();
		}
	};

	/**
	 * Read out the attributes from the given attribute set and initialize whatever they represent.
	 *
	 * @param attributeArray typed array containing the attribute values from the XML file
	 */
	private void setupAttributes(TypedArray attributeArray) {
		String series1Name = attributeArray.getString(R.styleable.RollingGraphView_series1name);
		if (series1Name == null) {
			seriesNames = null;
		} else {
			String series2Name = attributeArray.getString(R.styleable.RollingGraphView_series2name);
			if (series2Name == null) {
				seriesNames = new String[] { series1Name };
			} else {
				String series3Name = attributeArray.getString(R.styleable.RollingGraphView_series3name);
				if (series3Name == null) {
					seriesNames = new String[] { series1Name, series2Name };
				} else {
					String series4Name = attributeArray.getString(R.styleable.RollingGraphView_series4name);
					if (series4Name == null) {
						seriesNames = new String[] { series1Name, series2Name, series3Name };
					} else {
						seriesNames = new String[] { series1Name, series2Name, series3Name, series4Name };
					}
				}
			}

			maxValues = attributeArray.getInt(R.styleable.RollingGraphView_maxValues, -1);
			seriesPaints = new Paint[seriesNames.length];

			seriesPaints[0] = new Paint(Paint.ANTI_ALIAS_FLAG);
			seriesPaints[0].setColor(attributeArray.getColor(R.styleable.RollingGraphView_series1color, 0xFFFF0000));
			seriesPaints[0].setStrokeWidth(attributeArray.getDimension(R.styleable.RollingGraphView_series1thickness, 1 /* dp */ * displayDensity));
			seriesPaints[0].setTextSize(attributeArray.getDimension(R.styleable.RollingGraphView_label1textSize, 14 * displayDensity));
			if (seriesPaints.length > 1) {
				seriesPaints[1] = new Paint(Paint.ANTI_ALIAS_FLAG);
				seriesPaints[1].setColor(attributeArray.getColor(R.styleable.RollingGraphView_series2color, 0xFFFF0000));
				seriesPaints[1].setStrokeWidth(attributeArray.getDimension(R.styleable.RollingGraphView_series2thickness, 1 /* dp */ * displayDensity));
				seriesPaints[1].setTextSize(attributeArray.getDimension(R.styleable.RollingGraphView_label2textSize, 14 * displayDensity));
				if (seriesPaints.length > 2) {
					seriesPaints[2] = new Paint(Paint.ANTI_ALIAS_FLAG);
					seriesPaints[2].setColor(attributeArray.getColor(R.styleable.RollingGraphView_series3color, 0xFFFF0000));
					seriesPaints[2].setStrokeWidth(attributeArray.getDimension(R.styleable.RollingGraphView_series3thickness, 1 /* dp */ * displayDensity));
					seriesPaints[2].setTextSize(attributeArray.getDimension(R.styleable.RollingGraphView_label3textSize, 14 * displayDensity));
					if (seriesPaints.length > 3) {
						seriesPaints[3] = new Paint(Paint.ANTI_ALIAS_FLAG);
						seriesPaints[3].setColor(attributeArray.getColor(R.styleable.RollingGraphView_series4color, 0xFFFF0000));
						seriesPaints[3].setStrokeWidth(attributeArray.getDimension(R.styleable.RollingGraphView_series4thickness, 1 /* dp */ * displayDensity));
						seriesPaints[3].setTextSize(attributeArray.getDimension(R.styleable.RollingGraphView_label4textSize, 14 * displayDensity));
					}
				}
			}
			background = getBackground();
			if (background == null) {
				background = new ColorDrawable(ContextCompat.getColor(getContext(), android.R.color.white));
			}
		}

		horizontal = attributeArray.getInt(R.styleable.RollingGraphView_android_orientation, 0) == 0;
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
	public RollingGraphView(Context context, AttributeSet attrs) {
		super(context, attrs);

		handler = new Handler();
		displayDensity = getResources().getDisplayMetrics().density;

		TypedArray attributeArray = context.getTheme().obtainStyledAttributes(
				attrs,
				R.styleable.RollingGraphView,
				0, 0);

		try {
			setupAttributes(attributeArray);
		} finally {
			attributeArray.recycle();
		}
	}

	/**
	 * Perform inflation from XML and apply a class-specific base style from a theme attribute or style resource.
	 * This constructor of View allows subclasses to use their own base style when they are inflating.
	 * <p/>
	 * When determining the final value of a particular attribute, there are four inputs that come into play:
	 * <ul>
	 * <li>Any attribute values in the given AttributeSet.</li>
	 * <li>The style resource specified in the {@code AttributeSet} (named "style").</li>
	 * <li>The default style specified by {@code defStyleAttr}.</li>
	 * <li>he default style specified by {@code defStyleRes}.</li>T
	 * <li>The base values in this theme.</li>
	 * </ul>
	 * <p/>
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
	public RollingGraphView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
		super(context, attrs, defStyleAttr, defStyleRes);

		handler = new Handler();
		displayDensity = getResources().getDisplayMetrics().density;

		TypedArray attributeArray = context.getTheme().obtainStyledAttributes(
				attrs,
				R.styleable.RollingGraphView,
				defStyleAttr, defStyleRes);
		try {
			setupAttributes(attributeArray);
		} finally {
			attributeArray.recycle();
		}
	}

	/**
	 * Paints the data points of one series horizontally.
	 *
	 * @param c canvas to paint to
	 * @param nameBounds bounds of the label
	 * @param seriesNo index of the series to paint
	 * @param width width of the view
	 * @param height height of the view
	 * @param maxValue calculated possible maximum data value
	 */
	private void paintSeriesHorizontal(Canvas c, Rect nameBounds, int seriesNo, int width, int height, float maxValue) {
		float paddingLeft = getPaddingLeft();
		float paddingTop = getPaddingTop();
		float paddingRight = getPaddingRight();
		float paddingBottom = getPaddingBottom();

		float x = width - nameBounds.width() - paddingRight;
		float y;
		if (currentValue == -1) {
			y = height - (height - paddingBottom - paddingTop) / (seriesNames.length + 1) * (seriesNo + 1) + paddingTop;
		} else {
			if (dataValues == null) {
				Log.e("Wa-Tor", "NO DATA VALUES although currentValue is " + currentValue);
			} else if (dataValues[currentValue] == null) {
				Log.e("Wa-Tor", "NO SERIES DATA although currentValue is " + currentValue);
			}
			y = height - paddingBottom - dataValues[currentValue][seriesNo] * (height - paddingBottom - paddingTop) / maxValue;
		}
		c.drawText(seriesNames[seriesNo], x, y + nameBounds.height() / 2, seriesPaints[seriesNo]);

		float scale = seriesPaints[seriesNo].getStrokeWidth();
		x -= 6f * scale;
		c.drawLine(x, y, x + 3f * scale, y, seriesPaints[seriesNo]);
		c.drawLine(x, y, x + 1.5f * scale, y + 1.5f * scale, seriesPaints[seriesNo]);
		c.drawLine(x, y, x + 1.5f * scale, y - 1.5f * scale, seriesPaints[seriesNo]);
		if (currentValue != -1) {
			int no = currentValue;
			do {
				if (dataValues[no] == null) {
					break;
				}
				float newX = x - 1;
				float newY = height - paddingBottom - dataValues[no][seriesNo] * (height - paddingBottom - paddingTop) / maxValue;
				c.drawLine(x, y, newX, newY, seriesPaints[seriesNo]);
				x = newX;
				y = newY;
				no = no == 0 ? dataValues.length - 1 : no - 1;
			} while (no != oldestValue && x > paddingLeft);
		}
	}

	/**
	 * Paints the data points of one series vertically.
	 *
	 * @param c          canvas to paint to
	 * @param nameBounds bounds of the label
	 * @param seriesNo   index of the series to paint
	 * @param width      width of the view
	 * @param height     height of the view
	 * @param maxValue   calculated possible maximum data value
	 */
	private void paintSeriesVertical(Canvas c, Rect nameBounds, int seriesNo, int width, @SuppressWarnings("UnusedParameters") int height, float maxValue) {
		float paddingLeft = getPaddingLeft();
		float paddingTop = getPaddingTop();
		float paddingRight = getPaddingRight();
		float paddingBottom = getPaddingBottom();

		float y = nameBounds.height() + paddingTop;
		float x;
		if (currentValue == -1) {
			x = (width - paddingLeft - paddingRight) / (seriesNames.length + 1) * (seriesNo + 1) + paddingLeft;
		} else {
			if (dataValues == null) {
				Log.e("Wa-Tor", "NO DATA VALUES although currentValue is " + currentValue);
			} else if (dataValues[currentValue] == null) {
				Log.e("Wa-Tor", "NO SERIES DATA although currentValue is " + currentValue);
			}
			x = dataValues[currentValue][seriesNo] * (width - paddingLeft - paddingRight) / maxValue + paddingLeft;
		}
		c.drawText(seriesNames[seriesNo], x - nameBounds.width() / 2, y, seriesPaints[seriesNo]);

		float scale = seriesPaints[seriesNo].getStrokeWidth();
		y += 6f * scale;
		c.drawLine(x, y, x, y - 3f * scale, seriesPaints[seriesNo]);
		c.drawLine(x, y, x - 1.5f * scale, y - 1.5f * scale, seriesPaints[seriesNo]);
		c.drawLine(x, y, x + 1.5f * scale, y - 1.5f * scale, seriesPaints[seriesNo]);

		if (currentValue != -1) {
			int no = currentValue;
			do {
				if (dataValues[no] == null) {
					break;
				}
				float newX = dataValues[no][seriesNo] * (width - paddingLeft - paddingRight) / maxValue + paddingLeft;
				float newY = y + 1;
				c.drawLine(x, y, newX, newY, seriesPaints[seriesNo]);
				x = newX;
				y = newY;
				no = no == 0 ? dataValues.length - 1 : no - 1;
			} while (no != oldestValue && y < height - paddingBottom);
		}
	}

	/** Repaints the {@link #rollingGraphBitmap} */
	synchronized private void updateRollingGraph() {
		int width = getWidth();
		int height = getHeight();
		if (width <= 0 || height <= 0) {
			return;
		}

		float maxValue = 0.0f;
		if (currentValue != -1) {
			for (float[] dataValue : dataValues) {
				if (dataValue != null) {
					for (int seriesNo = 0; seriesNo < seriesNames.length; seriesNo++) {
						if (maxValue < dataValue[seriesNo]) {
							maxValue = dataValue[seriesNo];
						}
					}
				}
			}
		}

		if (rollingGraphBitmap == null || rollingGraphBitmap.getWidth() != width || rollingGraphBitmap.getHeight() != height) {
			rollingGraphBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
			rollingGraphCanvas = new Canvas(rollingGraphBitmap);
		}

		background.setBounds(0, 0, width, height);
		background.draw(rollingGraphCanvas);
		for (int seriesNo = 0; seriesNo < seriesNames.length; seriesNo++) {
			seriesPaints[seriesNo].getTextBounds(seriesNames[seriesNo], 0, seriesNames[seriesNo].length(), nameBounds);
			if (horizontal) {
				paintSeriesHorizontal(rollingGraphCanvas, nameBounds, seriesNo, width, height, maxValue);
			} else {
				paintSeriesVertical(rollingGraphCanvas, nameBounds, seriesNo, width, height, maxValue);
			}
		}
	}

	/**
	 * Adds a new data point to each of the series.
	 *
	 * @param newValues one new data point for each of the series
	 */
	public void addData(float[] newValues) {
		if (dataValues == null) {
			if (maxValues == -1 && ((horizontal && getWidth() == 0) || (!horizontal && getHeight() == 0))) {
				return;
			}
			dataValues = new float[maxValues == -1 ? (horizontal ? getWidth() : getHeight()) : maxValues][];
			dataTimes = new long[dataValues.length];
		}
		if (newValues.length != seriesNames.length) {
			throw new InvalidParameterException("Invalid number of new series data points: " + newValues.length + " (expected: " + seriesNames.length);
		}
		synchronized(this) {
			currentValue = (currentValue + 1) % dataValues.length;
			if (dataValues[currentValue] == null || dataValues[currentValue].length != newValues.length) {
				dataValues[currentValue] = new float[newValues.length];
			}
			System.arraycopy(newValues, 0, dataValues[currentValue], 0, newValues.length);
			dataTimes[currentValue] = System.currentTimeMillis();
			if ((oldestValue + 1) % dataValues.length == currentValue) {
				oldestValue = currentValue;
			}
		}
		bitMapIsInvalid = true;
		handler.post(invalidateRunner);
	}

	/**
	 * Draws the view
	 *
	 * @param canvas canvas to paint to
	 */
	@Override
	protected void onDraw(Canvas canvas) {
		if (bitMapIsInvalid) {
			updateRollingGraph();
		}
		if (rollingGraphBitmap != null) {
			canvas.drawBitmap(rollingGraphBitmap, 0, 0, null /* no paint? */);
		}
	}
}
