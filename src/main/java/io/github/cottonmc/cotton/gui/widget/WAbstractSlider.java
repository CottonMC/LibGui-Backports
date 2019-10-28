package io.github.cottonmc.cotton.gui.widget;

import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;

import javax.annotation.Nullable;
import java.util.function.IntConsumer;

/**
 * A base class for slider widgets that can be used to select int values.
 *
 * <p>You can set two listeners on a slider:
 * <ul>
 *     <li>
 *         A value change listener that gets all value changes.
 *     </li>
 *     <li>
 *         A dragging finished listener that gets called when the player stops dragging the slider
 *         or modifies the value with the keyboard.
 *         For example, this can be used for sending sync packets to the server
 *         when the player has selected a value.
 *     </li>
 * </ul>
 */
public abstract class WAbstractSlider extends WWidget {
	/**
	 * The minimum time between two draggingFinished events caused by scrolling ({@link #onMouseScroll}).
	 */
	private static final int DRAGGING_FINISHED_RATE_LIMIT_FOR_SCROLLING = 10;

	protected int min, max;
	protected final Axis axis;

	protected int value;

	/**
	 * True if the user is currently dragging the thumb.
	 * Used for visuals.
	 */
	protected boolean dragging = false;

	/**
	 * A value:coordinate ratio. Used for converting user input into values.
	 */
	protected float valueToCoordRatio;

	/**
	 * A coordinate:value ratio. Used for rendering the thumb.
	 */
	protected float coordToValueRatio;

	/**
	 * True if there is a pending dragging finished event caused by the keyboard.
	 */
	private boolean pendingDraggingFinishedFromKeyboard = false;
	private int draggingFinishedFromScrollingTimer = 0;
	private boolean pendingDraggingFinishedFromScrolling = false;

	@Nullable private IntConsumer valueChangeListener = null;
	@Nullable private IntConsumer draggingFinishedListener = null;

	protected WAbstractSlider(int min, int max, Axis axis) {
		if (max <= min)
			throw new IllegalArgumentException("Minimum value must be smaller than the maximum!");

		this.min = min;
		this.max = max;
		this.axis = axis;
		this.value = min;
	}

	/**
	 * @return the thumb size along the slider axis
	 */
	protected abstract int getThumbWidth();

	/**
	 * Checks if the mouse cursor is close enough to the slider that the user can start dragging.
	 *
	 * @param x the mouse x position
	 * @param y the mouse y position
	 * @return if the cursor is inside dragging bounds
	 */
	protected abstract boolean isMouseInsideBounds(int x, int y);

	@Override
	public void setSize(int x, int y) {
		super.setSize(x, y);
		int trackHeight = (axis == Axis.HORIZONTAL ? x : y) - getThumbWidth();
		valueToCoordRatio = (float) (max - min) / trackHeight;
		coordToValueRatio = 1 / valueToCoordRatio;
	}

	@Override
	public boolean canResize() {
		return true;
	}

	@Override
	public boolean canFocus() {
		return true;
	}

	@Override
	public WWidget onMouseDown(int x, int y, int button) {
		// Check if cursor is inside or <=2px away from track
		if (isMouseInsideBounds(x, y)) {
			requestFocus();
		}
		return super.onMouseDown(x, y, button);
	}

	@Override
	public void onMouseDrag(int x, int y, int button) {
		if (isFocused()) {
			dragging = true;
			moveSlider(x, y);
		}
	}

	@Override
	public void onClick(int x, int y, int button) {
		moveSlider(x, y);
		if (draggingFinishedListener != null) draggingFinishedListener.accept(value);
	}

	private void moveSlider(int x, int y) {
		int pos = (axis == Axis.VERTICAL ? (height - y) : x) - getThumbWidth() / 2;
		int rawValue = min + Math.round(valueToCoordRatio * pos);
		int previousValue = value;
		value = MathHelper.clamp(rawValue, min, max);
		if (value != previousValue) onValueChanged(value);
	}

	@Override
	public WWidget onMouseUp(int x, int y, int button) {
		dragging = false;
		if (draggingFinishedListener != null) draggingFinishedListener.accept(value);
		return super.onMouseUp(x, y, button);
	}

	@Override
	public void onMouseScroll(int x, int y, double amount) {
		int previous = value;
		value = MathHelper.clamp(value + (int) (valueToCoordRatio * amount * 2), min, max);

		if (previous != value) {
			onValueChanged(value);
			pendingDraggingFinishedFromScrolling = true;
		}
	}

//	@Override
	// TODO: Ticking widgets
	public void tick() {
		if (draggingFinishedFromScrollingTimer > 0) {
			draggingFinishedFromScrollingTimer--;
		}

		if (pendingDraggingFinishedFromScrolling && draggingFinishedFromScrollingTimer <= 0) {
			if (draggingFinishedListener != null) draggingFinishedListener.accept(value);
			pendingDraggingFinishedFromScrolling = false;
			draggingFinishedFromScrollingTimer = DRAGGING_FINISHED_RATE_LIMIT_FOR_SCROLLING;
		}
	}

	public int getValue() {
		return value;
	}

	/**
	 * Sets the slider value without calling listeners.
	 * @param value the new value
	 */
	public void setValue(int value) {
		setValue(value, false);
	}

	/**
	 * Sets the slider value.
	 *
	 * @param value the new value
	 * @param callListeners if true, call all slider listeners
	 */
	public void setValue(int value, boolean callListeners) {
		int previous = this.value;
		this.value = MathHelper.clamp(value, min, max);
		if (callListeners && previous != this.value) {
			onValueChanged(this.value);
			if (draggingFinishedListener != null) draggingFinishedListener.accept(value);
		}
	}

	@Nullable
	public IntConsumer getValueChangeListener() {
		return valueChangeListener;
	}

	public void setValueChangeListener(@Nullable IntConsumer valueChangeListener) {
		this.valueChangeListener = valueChangeListener;
	}

	@Nullable
	public IntConsumer getDraggingFinishedListener() {
		return draggingFinishedListener;
	}

	public void setDraggingFinishedListener(@Nullable IntConsumer draggingFinishedListener) {
		this.draggingFinishedListener = draggingFinishedListener;
	}

	public int getMinValue() {
		return min;
	}

	public int getMaxValue() {
		return max;
	}

	public void setMinValue(int min) {
		this.min = min;
		if (value < min) {
			value = min;
			onValueChanged(value);
			if (draggingFinishedListener != null) draggingFinishedListener.accept(value);
		}
	}

	public void setMaxValue(int max) {
		this.max = max;
		if (value > max) {
			value = max;
			onValueChanged(value);
			if (draggingFinishedListener != null) draggingFinishedListener.accept(value);
		}
	}

	public Axis getAxis() {
		return axis;
	}

	protected void onValueChanged(int value) {
		if (valueChangeListener != null) valueChangeListener.accept(value);
	}

	@Override
	public void onKeyPressed(int ch, int key, int modifiers) {
		boolean valueChanged = false;
		if (modifiers == 0) {
			if (isDecreasingKey(ch) && value > min) {
				value--;
				valueChanged = true;
			} else if (isIncreasingKey(ch) && value < max) {
				value++;
				valueChanged = true;
			}
		} else if (modifiers == GLFW.GLFW_MOD_CONTROL) {
			if (isDecreasingKey(ch) && value != min) {
				value = min;
				valueChanged = true;
			} else if (isIncreasingKey(ch) && value != max) {
				value = max;
				valueChanged = true;
			}
		}

		if (valueChanged) {
			onValueChanged(value);
			pendingDraggingFinishedFromKeyboard = true;
		}
	}

	@Override
	public void onKeyReleased(int ch, int key, int modifiers) {
		if (pendingDraggingFinishedFromKeyboard && (isDecreasingKey(ch) || isIncreasingKey(ch))) {
			if (draggingFinishedListener != null) draggingFinishedListener.accept(value);
			pendingDraggingFinishedFromKeyboard = false;
		}
	}

	private static boolean isDecreasingKey(int ch) {
		return ch == GLFW.GLFW_KEY_LEFT || ch == GLFW.GLFW_KEY_DOWN;
	}

	private static boolean isIncreasingKey(int ch) {
		return ch == GLFW.GLFW_KEY_RIGHT || ch == GLFW.GLFW_KEY_UP;
	}
}
