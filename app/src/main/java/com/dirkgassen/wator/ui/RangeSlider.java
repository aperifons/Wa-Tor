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

import java.util.Locale;

import com.dirkgassen.wator.R;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewDebug;

/**
 * @author dirk.
 */
public class RangeSlider extends View {

	interface OnValueChangeListener {

		void onValueChange(RangeSlider slider, int oldVal, int newVal);

	}

	final private Paint thumbTextPaint;
	final private Paint thumbBackgroundPaint;
	final private Paint sliderPaint;
	final Path thumbPath;

	private int minValue;
	private int maxValue;
	private boolean logarithmic;
	private float thumbPadding;
	private int value;

	private float thumbSize;

	private OnValueChangeListener onValueChangeListener;

	private float touchOffset;
	private int activePointerId = -1;
	private int valueBeforeDragging;
	private boolean isDragging = false;

	synchronized private void updateValue(int newValue) {
		int oldValue = value;
		value = newValue;
		invalidate();
		if (onValueChangeListener != null) {
			onValueChangeListener.onValueChange(this, oldValue, newValue);
		}
	}

	private float positionFromValue(int paddingLeft, int paddingRight) {
		return (getWidth() - paddingLeft - paddingRight - thumbSize) * ((float) value - minValue) / (maxValue - minValue) + paddingLeft + thumbSize / 2;
	}

	private int valueFromPosition(float position, int paddingLeft, int paddingRight) {
		int newValue = (int) ((position - paddingLeft - thumbSize / 2) * (maxValue - minValue) / (getWidth() - paddingLeft - paddingRight - thumbSize) + minValue);
		if (newValue < minValue) {
			return minValue;
		}
		if (newValue > maxValue) {
			return maxValue;
		}
		return newValue;
	}

	private void calculateThumbSize() {
		final String demoValue;
		if (maxValue < 10) {
			demoValue = "9";
		} else if (maxValue < 100) {
			demoValue = "99";
		} else if (maxValue < 1000) {
			demoValue = "999";
		} else if (maxValue < 10000) {
			demoValue = "9999";
		} else if (maxValue < 100000) {
			demoValue = "99999";
		} else if (maxValue < 1000000) {
			demoValue = "999999";
		} else if (maxValue < 10000000) {
			demoValue = "9999999";
		} else if (maxValue < 100000000) {
			demoValue = "99999999";
		} else if (maxValue < 1000000000) {
			demoValue = "999999999";
		} else {
			demoValue = "9999999999";
		}
		final float valueWidth = (thumbTextPaint.measureText(demoValue, 0, demoValue.length()));
		final float valueTotalHeight = (-thumbTextPaint.ascent() + thumbTextPaint.descent());
		float size = (valueWidth > valueTotalHeight ? valueWidth : valueTotalHeight) + 2 * thumbPadding;
		thumbSize = (float) (2f * Math.sqrt((size / 2f) * (size / 2f)));
	}

	@Override
	protected void onSizeChanged(int newWidth, int newHeight, int oldWidth, int oldHeight) {
		super.onSizeChanged(newWidth, newHeight, oldWidth, oldHeight);
		thumbPath.offset(0, (newHeight - oldHeight) / 2f);
	}

	public RangeSlider(Context context, AttributeSet attrs) {
		super(context, attrs);

		TypedArray a = context.getTheme().obtainStyledAttributes(
				attrs,
				R.styleable.RangeSlider,
				0, 0);

		try {
			value = a.getInt(R.styleable.RangeSlider_android_value, 0);
			minValue = a.getInt(R.styleable.RangeSlider_minValue, 0);
			maxValue = a.getInt(R.styleable.RangeSlider_maxValue, 100);
			logarithmic = a.getBoolean(R.styleable.RangeSlider_logarithmic, false);

			thumbPadding = a.getDimension(R.styleable.RangeSlider_thumbPadding, 3 * getResources().getDisplayMetrics().density);

			thumbTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
			thumbTextPaint.setTextSize(a.getDimension(R.styleable.RangeSlider_android_textSize, 12));
//			thumbTextPaint.density = getResources().getDisplayMetrics().density;
			thumbTextPaint.setColor(a.getColor(R.styleable.RangeSlider_android_textColor, ContextCompat.getColor(getContext(), android.R.color.white)));

			thumbBackgroundPaint = new Paint();
			thumbBackgroundPaint.setStyle(Paint.Style.FILL);
			thumbBackgroundPaint.setColor(a.getColor(R.styleable.RangeSlider_android_color, ContextCompat.getColor(getContext(), android.R.color.black)));

			sliderPaint = new Paint();
			sliderPaint.setStrokeWidth(2);
			sliderPaint.setColor(thumbBackgroundPaint.getColor());
		} finally {
			a.recycle();
		}

		thumbPath = new Path();
		thumbPath.setFillType(Path.FillType.EVEN_ODD);

		calculateThumbSize();
	}

	@Override
	public boolean onTouchEvent(@NonNull MotionEvent event) {
		if (Log.isLoggable("Wa-Tor", Log.DEBUG)) { Log.d("Wa-Tor", "Got touch event: " + event); }
		int paddingLeft = getPaddingLeft();
		int paddingRight = getPaddingRight();
		switch (event.getAction() & MotionEvent.ACTION_MASK) {
			case MotionEvent.ACTION_DOWN:
				valueBeforeDragging = value;
				activePointerId = event.getPointerId(0);
				float valuePos = positionFromValue(paddingLeft, paddingRight);
				float x = event.getX();
				if (x < valuePos - thumbSize || x > valuePos + thumbSize) {
					int newValue = valueFromPosition(x, paddingLeft, paddingRight);
					if (Log.isLoggable("Wa-Tor", Log.DEBUG)) { Log.d("Wa-Tor", "Starting to drag thumb OUTSIDE of thumb"); }
					if (newValue != value) {
						updateValue(newValue);
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
					float current = event.getX(pointerIndex) - touchOffset;
					int newValue = valueFromPosition(current, paddingLeft, paddingRight);
					if (newValue != value) {
						if (Log.isLoggable("Wa-Tor", Log.DEBUG)) { Log.d("Wa-Tor", "Got new value " + newValue + " (old = " + value + ")"); }
						updateValue(newValue);
					}
				}
				return true;
			case MotionEvent.ACTION_UP:
				isDragging = false;
				break;
			case MotionEvent.ACTION_CANCEL:
				isDragging = false;
				updateValue(valueBeforeDragging);
				break;
			case MotionEvent.ACTION_POINTER_UP:
				isDragging = false;
				break;
		}

		return super.onTouchEvent(event);
	}

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

	@Override
	protected void onDraw(Canvas canvas) {
		super.onDraw(canvas);

		int paddingLeft = getPaddingLeft();
		int paddingTop = getPaddingTop();
		int paddingRight = getPaddingRight();
		int paddingBottom = getPaddingBottom();
		int sliderY = (getHeight() - paddingTop - paddingBottom) / 2 + paddingTop;

		canvas.drawLine(paddingLeft + thumbSize / 2, sliderY, getWidth() - paddingRight - thumbSize / 2, sliderY, sliderPaint);

		String valueString = String.format(Locale.getDefault(), "%d", value);
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

	public int getMinValue() {
		return minValue;
	}

	public void setMinValue(int minValue) {
		if (minValue != this.minValue) {
			this.minValue = minValue;
			invalidate();
			calculateThumbSize();
		}
	}

	public int getMaxValue() {
		return maxValue;
	}

	public void setMaxValue(int maxValue) {
		if (maxValue != this.maxValue) {
			this.maxValue = maxValue;
			invalidate();
		}
	}

	public boolean isLogarithmic() {
		return logarithmic;
	}

	public void setLogarithmic(boolean logarithmic) {
		if (logarithmic != this.logarithmic) {
			this.logarithmic = logarithmic;
			invalidate();
		}
	}

	public float getTextSize() {
		return thumbTextPaint.getTextSize();
	}

	public void setTextSize(float textSize) {
		if (textSize != thumbTextPaint.getTextSize()) {
			thumbTextPaint.setTextSize(textSize);
			invalidate();
		}
	}


	@ViewDebug.ExportedProperty(category = "text", mapping = {
			@ViewDebug.IntToString(from = Typeface.NORMAL, to = "NORMAL"),
			@ViewDebug.IntToString(from = Typeface.BOLD, to = "BOLD"),
			@ViewDebug.IntToString(from = Typeface.ITALIC, to = "ITALIC"),
			@ViewDebug.IntToString(from = Typeface.BOLD_ITALIC, to = "BOLD_ITALIC")
	})
	public int getTypefaceStyle() {
		Typeface typeface = thumbTextPaint.getTypeface();
		return typeface != null ? typeface.getStyle() : Typeface.NORMAL;
	}


	public int getValue() {
		return value;
	}

	public void setValue(int value) {
		if (value != this.value) {
			this.value = value;
			invalidate();
		}
	}

	public float getThumbPadding() {
		return thumbPadding;
	}

	public void setThumbPadding(float thumbPadding) {
		this.thumbPadding = thumbPadding;
	}

	synchronized public void setOnValueChangeListener(OnValueChangeListener onValueChangeListener) {
		this.onValueChangeListener = onValueChangeListener;
	}

	public OnValueChangeListener getOnValueChangeListener() {
		return onValueChangeListener;
	}

}
