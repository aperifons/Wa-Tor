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

package com.dirkgassen.wator.ui;

import java.security.InvalidParameterException;

import com.dirkgassen.wator.R;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Handler;
import android.util.AttributeSet;
import android.view.View;

/**
 * @author dirk.
 */
public class RollingGraphView extends View {

	private String seriesNames[];
	private int seriesColors[];
	private long[] dataTimes;
	private float[][] dataValues;
	private int oldestValue;
	private int currentValue = -1; // No values yet
	private Bitmap rollingGraphBitmap;
	private Handler handler;
	private int maxValues;
	private boolean horizontal;

	public RollingGraphView(Context context, AttributeSet attrs) {
		super(context, attrs);

		handler = new Handler();

		TypedArray a = context.getTheme().obtainStyledAttributes(
				attrs,
				R.styleable.RollingGraphView,
				0, 0);

		try {
			String series1Name = a.getString(R.styleable.RollingGraphView_series1name);
			if (series1Name == null) {
				seriesNames = null;
			} else {
				String series2Name = a.getString(R.styleable.RollingGraphView_series2name);
				if (series2Name == null) {
					seriesNames = new String[] { series1Name };
				} else {
					String series3Name = a.getString(R.styleable.RollingGraphView_series3name);
					if (series3Name == null) {
						seriesNames = new String[] { series1Name, series2Name };
					} else {
						String series4Name = a.getString(R.styleable.RollingGraphView_series4name);
						if (series4Name == null) {
							seriesNames = new String[] { series1Name, series2Name, series3Name };
						} else {
							seriesNames = new String[] { series1Name, series2Name, series3Name, series4Name };
						}
					}
				}
				maxValues = a.getInt(R.styleable.RollingGraphView_maxValues, -1);
				if (seriesNames != null) {
					seriesColors = new int[seriesNames.length];
					seriesColors[0] = a.getColor(R.styleable.RollingGraphView_series1color, 0xFFFF0000);
					if (seriesColors.length > 1) {
						seriesColors[1] = a.getColor(R.styleable.RollingGraphView_series2color, 0xFF00FF00);
						if (seriesColors.length > 2) {
							seriesColors[2] = a.getColor(R.styleable.RollingGraphView_series3color, 0xFF0000FF);
							if (seriesColors.length > 3) {
								seriesColors[3] = a.getColor(R.styleable.RollingGraphView_series4color, 0xFFFF00FF);
							}
						}
					}
				}
			}

			horizontal = a.getInt(R.styleable.RollingGraphView_android_orientation, 0) == 0;
		} finally {
			a.recycle();
		}
	}

	private void paintSeriesHorizontal(Canvas c, Paint p, Rect nameBounds, float scale, int seriesNo, String seriesName, int width, int height, float maxValue) {
		float x = width - nameBounds.width();
		float y;
		if (currentValue == -1) {
			y = height - height / (seriesNames.length + 1) * (seriesNo + 1);
		} else {
			y = height - dataValues[currentValue][seriesNo] * height / maxValue;
		}
		c.drawText(seriesNames[seriesNo], x, y + nameBounds.height() / 2, p);

		x -= 7 * scale;
		c.drawLine(x, y, x + 5 * scale, y, p);
		c.drawLine(x, y, x + 2 * scale, y + 2 * scale, p);
		c.drawLine(x, y, x + 2 * scale, y - 2 * scale, p);
		if (currentValue != -1) {
			int no = currentValue;
			do {
				if (dataValues[no] == null) {
					break;
				}
				float newX = x - 1;
				float newY = height - dataValues[no][seriesNo] * height / maxValue;
				c.drawLine(x, y, newX, newY, p);
				x = newX;
				y = newY;
				no = no == 0 ? dataValues.length - 1 : no - 1;
			} while (no != oldestValue);
		}
	}

	private void paintSeriesVertical(Canvas c, Paint p, Rect nameBounds, float scale, int seriesNo, String seriesName, int width, int height, float maxValue) {
		float y = nameBounds.height();
		float x;
		if (currentValue == -1) {
			x = width / (seriesNames.length + 1) * (seriesNo + 1);
		} else {
			x = dataValues[currentValue][seriesNo] * width / maxValue;
		}
		c.drawText(seriesNames[seriesNo], x - nameBounds.width() / 2, y, p);

		y += 7 * scale;
		c.drawLine(x, y, x, y - 5 * scale, p);
		c.drawLine(x, y, x - 2 * scale, y - 2 * scale, p);
		c.drawLine(x, y, x + 2 * scale, y - 2 * scale, p);

		if (currentValue != -1) {
			int no = currentValue;
			do {
				if (dataValues[no] == null) {
					break;
				}
				float newX = dataValues[no][seriesNo] * width / maxValue;
				float newY = y + 1;
				c.drawLine(x, y, newX, newY, p);
				x = newX;
				y = newY;
				no = no == 0 ? dataValues.length - 1 : no - 1;
			} while (no != oldestValue);
		}
	}

	private Bitmap drawRollingGraph() {
		int width = getWidth();
		int height = getHeight();
		if (width <= 0 || height <= 0) {
			return null;
		}

		Resources resources = getResources();
		float scale = resources.getDisplayMetrics().density;

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

		Bitmap b = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
		Canvas c = new Canvas(b);

		Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
		p.setTextSize(14 * scale);
		p.setShadowLayer(1f, 0f, 1f, Color.WHITE);
		Rect nameBounds = new Rect();
		for (int seriesNo = 0; seriesNo < seriesNames.length; seriesNo++) {
			p.setColor(seriesColors[seriesNo]);
			p.getTextBounds(seriesNames[seriesNo], 0, seriesNames[seriesNo].length(), nameBounds);
			if (horizontal) {
				paintSeriesHorizontal(c, p, nameBounds, scale, seriesNo, seriesNames[seriesNo], width, height, maxValue);
			} else {
				paintSeriesVertical(c, p, nameBounds, scale, seriesNo, seriesNames[seriesNo], width, height, maxValue);
			}
		}
		return b;
	}

	public void addData(float[] newValues) {
		if (dataValues == null) {
			if (maxValues == -1 && getWidth() == 0) {
				return;
			}
			dataValues = new float[maxValues == -1 ? getWidth() : maxValues][];
			dataTimes = new long[dataValues.length];
		}
		if (newValues.length != seriesNames.length) {
			throw new InvalidParameterException("Invalid number of new series data points: " + newValues.length + " (expected: " + seriesNames.length);
		}
		currentValue = (currentValue + 1) % dataValues.length;
		if (dataValues[currentValue] == null || dataValues[currentValue].length != newValues.length) {
			dataValues[currentValue] = new float[newValues.length];
		}
		System.arraycopy(newValues, 0, dataValues[currentValue], 0, newValues.length);
		if ((oldestValue + 1) % dataValues.length == currentValue) {
			oldestValue = currentValue;
		}
		rollingGraphBitmap = null;
		handler.post(new Runnable() {
			@Override
			public void run() {
				invalidate();
			}
		});
	}

	@Override
	protected void onDraw(Canvas canvas) {
		if (rollingGraphBitmap == null) {
			rollingGraphBitmap = drawRollingGraph();
		}
		if (rollingGraphBitmap != null) {
			canvas.drawBitmap(rollingGraphBitmap, 0, 0, null /* no paint? */);
		}
	}
}
