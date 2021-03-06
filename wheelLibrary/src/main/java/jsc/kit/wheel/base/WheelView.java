package jsc.kit.wheel.base;

import android.animation.Animator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Camera;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.support.annotation.ColorInt;
import android.support.annotation.Nullable;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.util.Log;
import android.util.Pair;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.animation.Interpolator;
import android.view.animation.LinearInterpolator;
import android.widget.OverScroller;

import jsc.kit.wheel.R;

/**
 * Wheel view without selected mask.
 *
 * <br><br>
 * {@code attrs:}
 * <br>{@link R.styleable#WheelView_wheelTextColor}
 * <br>{@link R.styleable#WheelView_wheelTextSize}
 * <br>{@link R.styleable#WheelView_wheelShowCount}
 * <br>{@link R.styleable#WheelView_wheelTotalOffsetX}
 * <br>{@link R.styleable#WheelView_wheelItemVerticalSpace}
 *
 * <br>Email:1006368252@qq.com
 * <br>QQ:1006368252
 * <br><a href="https://github.com/JustinRoom/WheelViewDemo" target="_blank">https://github.com/JustinRoom/WheelViewDemo</a>
 *
 * @author jiangshicheng
 */
public class WheelView extends View implements IWheelViewSetting {

    private final String TAG = "WheelView";
    private TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private Camera camera = new Camera();
    private Matrix matrix = new Matrix();
    private float textBaseLine = 0;

    private IWheel[] items = null;
    /**
     * The color of show text.
     */
    private int textColor = Color.BLACK;
    /**
     * The size of showing text.
     * Default value is 14dp.
     */
    private float textSize = 0.0f;
    /**
     * The offset pixel from x coordination.
     * <br>text align {@code right} with a positive value
     * <br>text align {@code center} with 0 value
     * <br>text align {@code left} with a negative value
     */
    private int totalOffsetX = 0;
    /**
     * the average pixel length of show text.
     */
    private float averageShowTextLength = 0;
    /**
     * The most showing item count.
     * use it to measure view's height
     */
    private int showCount = 5;
    /**
     * The most draw item count.
     */
    private int drawCount = showCount + 2;
    private Rect[] defaultRectArray = null;
    private Rect[] drawRectArray = null;
    private int offsetY = 0;
    private int totalMoveY = 0;//

    /**
     * the space width of two items
     */
    private int itemVerticalSpace = 0;
    /**
     * the height of every item
     */
    private int itemHeight = 0;
    private float lastX = 0.0f;
    private float lastY = 0.0f;
    private int selectedIndex = 0;//the selected index position
    private OnSelectedListener onSelectedListener = null;
    private ValueAnimator animator = null;
    private boolean isScrolling = false;
    private boolean isAnimatorCanceledForwardly = false;//whether cancel auto scroll animation forwardly

    private static final long CLICK_EVENT_INTERNAL_TIME = 2000;
    private RectF clickRectF = new RectF();
    private boolean isClickEvent = false;
    private long touchDownTimeStamp = 0;

    //about fling action
    private int mMinimumVelocity;
    private int mMaximumVelocity;
    private int scaledTouchSlop;
    private VelocityTracker mVelocityTracker = null;
    private OverScroller mOverScroller;

    public WheelView(Context context) {
        super(context);
        initAttr(context, null, 0);
    }

    public WheelView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        initAttr(context, attrs, 0);
    }

    public WheelView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initAttr(context, attrs, defStyleAttr);
    }

    private void initAttr(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        mOverScroller = new OverScroller(context);
        final ViewConfiguration viewConfiguration = ViewConfiguration.get(context);
        mMinimumVelocity = viewConfiguration.getScaledMinimumFlingVelocity();
        mMaximumVelocity = viewConfiguration.getScaledMaximumFlingVelocity();
        scaledTouchSlop = viewConfiguration.getScaledTouchSlop();

        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.WheelView, defStyleAttr, 0);
        float defaultTextSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 14, getResources().getDisplayMetrics());
        textColor = a.getColor(R.styleable.WheelView_wheelTextColor, 0xFF333333);
        textSize = a.getDimension(R.styleable.WheelView_wheelTextSize, defaultTextSize);
        showCount = a.getInt(R.styleable.WheelView_wheelShowCount, 5);
        totalOffsetX = a.getDimensionPixelSize(R.styleable.WheelView_wheelTotalOffsetX, 0);
        itemVerticalSpace = a.getDimensionPixelSize(R.styleable.WheelView_wheelItemVerticalSpace, 32);
        a.recycle();

        initConfig();
        if (isInEditMode()) {
            IWheel[] items = new IWheel[50];
            for (int i = 0; i < items.length; i++) {
                items[i] = new WheelItem("菜单选项" + (i < 10 ? "0" + i : String.valueOf(i)));
            }
            setItems(items);
        }
    }

    private void initConfig() {
        textPaint.setColor(textColor);
        textPaint.setTextSize(textSize);
        Paint.FontMetrics fontMetrics = textPaint.getFontMetrics();

        String testText = "菜单选项";
        Rect rect = new Rect();
        textPaint.getTextBounds(testText, 0, testText.length(), rect);
        itemHeight = rect.height() + itemVerticalSpace;
        textBaseLine = -itemHeight / 2.0f + (itemHeight - fontMetrics.bottom + fontMetrics.top) / 2 - fontMetrics.top;

        if (showCount < 5) {
            showCount = 5;
        }
        if (showCount % 2 == 0) {
            showCount++;
        }
        drawCount = showCount + 2;
        defaultRectArray = new Rect[drawCount];
        drawRectArray = new Rect[drawCount];
        for (int i = 0; i < drawCount; i++) {
            defaultRectArray[i] = new Rect();
            drawRectArray[i] = new Rect();
        }
    }

    @Override
    public void setTextSize(float textSize) {
        this.textSize = textSize;
        initConfig();
        requestLayout();
    }

    @Override
    public void setTextColor(@ColorInt int textColor) {
        this.textColor = textColor;
        textPaint.setColor(textColor);
        invalidate();
    }

    @Override
    public void setShowCount(int showCount) {
        this.showCount = showCount;
        initConfig();
        requestLayout();
    }

    @Override
    public void setTotalOffsetX(int totalOffsetX) {
        this.totalOffsetX = totalOffsetX;
        invalidate();
    }

    @Override
    public void setItemVerticalSpace(int itemVerticalSpace) {
        this.itemVerticalSpace = itemVerticalSpace;
        initConfig();
        requestLayout();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int top = 0 - itemHeight;
        for (int i = 0; i < drawCount; i++) {
            defaultRectArray[i].set(0, top, 0, top + itemHeight);
            top += itemHeight;
        }
        heightMeasureSpec = MeasureSpec.makeMeasureSpec(itemHeight * showCount, MeasureSpec.EXACTLY);
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (isEmpty())
            return super.onTouchEvent(event);

        initVelocityTrackerIfNotExists();
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                //add event into velocity tracker.
                mVelocityTracker.clear();

                if (animator != null && animator.isRunning()) {
                    isAnimatorCanceledForwardly = true;
                    animator.cancel();
                }
                lastX = event.getX();
                lastY = event.getY();

                //Make it as a click event when touch down,
                //and record touch down timestamp.
                isClickEvent = true;
                touchDownTimeStamp = System.currentTimeMillis();
                break;
            case MotionEvent.ACTION_MOVE:
                //add event into velocity tracker and compute velocity.
                mVelocityTracker.addMovement(event);

                float currentX = event.getX();
                float currentY = event.getY();
                int distance = (int) (currentY - lastY);
                if (Math.abs(distance) >= scaledTouchSlop) {
                    //Cancel click event.
                    isClickEvent = false;
                    touchDownTimeStamp = 0;
                }
                //invalid move event if out of touch area
                if (currentY >= 0 && currentY <= getHeight()) {
                    lastX = currentX;
                    lastY = currentY;
                    totalMoveY += distance;
                    updateByTotalMoveY();
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                //Execute click event if it's a click event.
                if (executeClickEvent(event.getX(), event.getY())) {
                    break;
                }

                final VelocityTracker velocityTracker = mVelocityTracker;
                velocityTracker.computeCurrentVelocity(200, mMaximumVelocity);
                float currentVelocity = velocityTracker.getYVelocity();
                recycleVelocityTracker();
                int extraDistance = (int) currentVelocity;
                int tempTotalMoveY = totalMoveY + extraDistance;
                //limit fling area
                tempTotalMoveY = Math.max(tempTotalMoveY, -(getItemCount() + showCount / 2) * itemHeight);
                tempTotalMoveY = Math.min(tempTotalMoveY, (showCount / 2) * itemHeight);
                Pair<Integer, Integer> pair = calculateSelectedIndex(tempTotalMoveY);
                int tempSelectedIndex = pair.first;
                runAutoScrollAnimation(
                        null,
                        totalMoveY,
                        tempTotalMoveY,
                        0 - tempSelectedIndex * itemHeight
                );
                break;

        }
        return true;
    }

    public void setOnSelectedListener(OnSelectedListener onSelectedListener) {
        this.onSelectedListener = onSelectedListener;
    }

    public void setItems(IWheel[] items) {
        this.items = items;
        if (!isEmpty()) {
            averageShowTextLength = calAverageShowTextLength();
            invalidate();
        }
    }

    public int getItemHeight() {
        return itemHeight;
    }

    public int getShowCount() {
        return showCount;
    }

    /**
     * Get the current selected index position.
     *
     * @return the current selected index position
     */
    public int getSelectedIndex() {
        return selectedIndex;
    }

    /**
     * Scroll to fixed index position with animation.
     *
     * @param targetIndexPosition the target index position
     */
    public void setSelectedIndex(int targetIndexPosition) {
        setSelectedIndex(targetIndexPosition, true);
    }

    /**
     * Scroll to fixed index position.
     *
     * @param targetIndexPosition the target index position
     * @param withAnimation       true, scroll with animation
     */
    public void setSelectedIndex(int targetIndexPosition, boolean withAnimation) {
        if (targetIndexPosition < 0 || targetIndexPosition >= getItemCount())
            throw new IndexOutOfBoundsException("Out of array bounds.");
        if (withAnimation) {
            runAutoScrollAnimation(null, totalMoveY, 0 - itemHeight * targetIndexPosition);
        } else {
            selectedIndex = targetIndexPosition;
            offsetY = 0;
            invalidate();
            if (onSelectedListener != null)
                onSelectedListener.onSelected(getContext(), selectedIndex);
        }
    }

    @Override
    public boolean isScrolling() {
        return isScrolling;
    }

    /**
     * Calculate average pixel length of show text.
     *
     * @return the average pixel length of show text
     */
    private float calAverageShowTextLength() {
        float totalLength = 0;
        String showText = null;
        for (IWheel wheel : items) {
            showText = wheel.getShowText();
            if (showText == null || showText.length() == 0)
                continue;
            totalLength += textPaint.measureText(showText);
        }
        return totalLength / getItemCount();
    }

    /**
     * Execute click event.
     *
     * @return true, valid click event, else invalid.
     */
    private boolean executeClickEvent(float upX, float upY) {
        if (!isClickEvent
                || System.currentTimeMillis() - touchDownTimeStamp > CLICK_EVENT_INTERNAL_TIME)
            return false;

        boolean isValidTempSelectedIndex = false;
        int tempSelectedIndex = selectedIndex - drawCount / 2;
        for (int i = 0; i < drawCount; i++) {
            clickRectF.set(drawRectArray[i]);
            if (clickRectF.contains(upX, upY)) {
                isValidTempSelectedIndex = true;
                break;
            }
            tempSelectedIndex++;
        }
        if (isValidTempSelectedIndex
                && tempSelectedIndex >= 0
                && tempSelectedIndex < getItemCount()) {
            //Move to target selected index
            setSelectedIndex(tempSelectedIndex);
            return true;
        }
        return false;
    }

    private int getItemCount() {
        return items == null ? 0 : items.length;
    }

    private IWheel getItemAt(int position) {
        if (isEmpty() || position < 0 || position >= getItemCount())
            return null;
        return items[position];
    }

    private boolean isEmpty(){
        return getItemCount() == 0;
    }

    /**
     * Execute auto-scroll animation.
     */
    private void runAutoScrollAnimation(@Nullable Interpolator interpolator, int... values) {
        if (values.length < 2) {
            if (onSelectedListener != null)
                onSelectedListener.onSelected(getContext(), selectedIndex);
            return;
        }
        int duration = 0;
        for (int i = 0; i < values.length; i++) {
            if (i > 0) {
                duration += Math.abs(values[i] - values[i - 1]);
            }
        }
        if (duration == 0) {
            if (onSelectedListener != null)
                onSelectedListener.onSelected(getContext(), selectedIndex);
            return;
        }
        createAnimatorIfNecessary();
        if (animator.isRunning()) {
            isAnimatorCanceledForwardly = true;
            animator.cancel();
        }
        animator.setInterpolator(interpolator == null ? new LinearInterpolator() : interpolator);
        animator.setIntValues(values);
        animator.setDuration(calSuitableDuration(duration));
        animator.start();
    }

    private int calSuitableDuration(int duration) {
        int result = duration;
        while (result > 1200) {
            result = result / 2;
        }
        return result;
    }

    /**
     * Create auto-scroll animation.
     */
    private void createAnimatorIfNecessary() {
        if (animator == null) {
            animator = new ValueAnimator();
            animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator animation) {
                    int tempTotalMoveY = (int) animation.getAnimatedValue();
                    setTotalMoveY(tempTotalMoveY);
                }
            });
            animator.setInterpolator(new LinearInterpolator());
            animator.addListener(new Animator.AnimatorListener() {

                @Override
                public void onAnimationStart(Animator animation) {
                    isScrolling = true;
                }

                @Override
                public void onAnimationEnd(Animator animation) {
                    isScrolling = false;
                    if (isAnimatorCanceledForwardly) {
                        Log.i(TAG, "onAnimationEnd: Cancel animation forwardly.");
                        isAnimatorCanceledForwardly = false;
                        return;
                    }

                    if (onSelectedListener != null)
                        onSelectedListener.onSelected(getContext(), selectedIndex);
                }

                @Override
                public void onAnimationCancel(Animator animation) {

                }

                @Override
                public void onAnimationRepeat(Animator animation) {

                }
            });
        }
    }

    public int getTotalMoveY() {
        return totalMoveY;
    }

    public void setTotalMoveY(int totalMoveY) {
        this.totalMoveY = totalMoveY;
        updateByTotalMoveY();
    }

    private void updateByTotalMoveY() {
        Pair<Integer, Integer> pair = calculateSelectedIndex(totalMoveY);
        this.selectedIndex = pair.first;
        this.offsetY = pair.second;
        invalidate();
    }

    private Pair<Integer, Integer> calculateSelectedIndex(int totalMoveY) {
        int selectedIndex;
        int offsetY;
        if (totalMoveY > 0) {
            selectedIndex = 0;
            offsetY = totalMoveY;
        } else {
            selectedIndex = -totalMoveY / itemHeight;
            if (selectedIndex >= getItemCount()) {
                selectedIndex = getItemCount() - 1;
            }
            offsetY = totalMoveY + selectedIndex * itemHeight;
        }
        return new Pair<>(selectedIndex, offsetY);
    }


    @Override
    protected void onDraw(Canvas canvas) {
        if (isEmpty())
            return;

        int tempStartSelectedIndex = selectedIndex - drawCount / 2;
        for (int i = 0; i < drawCount; i++) {
            Rect rect = drawRectArray[i];
            rect.set(defaultRectArray[i]);
            //record touch area for click event
            rect.left = 0;
            rect.right = getWidth();
            if (tempStartSelectedIndex >= 0 && tempStartSelectedIndex < getItemCount()) {
                drawItem(canvas, rect, getItemAt(tempStartSelectedIndex), offsetY, textPaint);
            }
            tempStartSelectedIndex++;
        }
    }

    private void drawItem(Canvas canvas, Rect rect, IWheel item, int offsetY, TextPaint textPaint) {
        String text = item == null ? "" : item.getShowText();
        if (text == null || text.trim().length() == 0)
            return;

        rect.offset(0, offsetY);
        textPaint.setAlpha(calAlpha(rect));
        final int offsetX = totalOffsetX == 0 ? 0 : calOffsetX(totalOffsetX, rect);
        final float w = textPaint.measureText(text);

        float startX = 0;
        if (totalOffsetX > 0) {
            //show text align right
            final float rightAlignPosition = (getWidth() + averageShowTextLength) / 2.0f;
            startX = rightAlignPosition - w + offsetX;
        } else if (totalOffsetX < 0) {
            //show text align left
            final float leftAlignPosition = (getWidth() - averageShowTextLength) / 2.0f;
            startX = leftAlignPosition + offsetX;
        } else {
            //show text align center_horizontal
            startX = (getWidth() - w) / 2.0f + offsetX;
        }
        float centerX = getWidth() / 2.0f;
        float centerY = rect.exactCenterY();
        float baseLine = centerY + textBaseLine;
        matrix.reset();
        camera.save();
        camera.rotateX(calRotationX(rect, totalOffsetX == 0 ? 45 : 75));
        camera.getMatrix(matrix);
        camera.restore();
        matrix.preTranslate(-centerX, -centerY);
        matrix.postTranslate(centerX, centerY);
        if (totalOffsetX > 0) {
            float skewX = 0 - calSkewX(rect);
            centerX = (startX + w) / 2.0f;
            matrix.setSkew(skewX, 0, centerX, centerY);
        } else if (totalOffsetX < 0) {
            float skewX = calSkewX(rect);
            centerX = (startX + w) / 2.0f;
            matrix.setSkew(skewX, 0, centerX, centerY);
        }
        canvas.save();
        canvas.concat(matrix);
        canvas.drawText(text, startX, baseLine, textPaint);
        canvas.restore();
    }

    private int calAlpha(Rect rect) {
        int centerY = getHeight() / 2;
        int distance = Math.abs(centerY - rect.centerY());
        int totalDistance = itemHeight * (showCount / 2);
        float alpha = 0.6f * distance / totalDistance;
        return (int) ((1 - alpha) * 0xFF);
    }

    private float calRotationX(Rect rect, float baseRotationX) {
        int centerY = getHeight() / 2;
        int distance = centerY - rect.centerY();
        int totalDistance = itemHeight * (showCount / 2);
        return baseRotationX * distance * 1.0f / totalDistance;
    }

    private float calScaleY(Rect rect) {
        int centerY = getHeight() / 2;
        int distance = Math.abs(centerY - rect.centerY());
        int totalDistance = itemHeight * (showCount / 2);
        distance = totalDistance - distance;
        return 1 + 0.4f * distance / totalDistance;
    }
    private float calSkewX(Rect rect) {
        int centerY = getHeight() / 2;
        int distance = centerY - rect.centerY();
        int totalDistance = itemHeight * (showCount / 2);
        return 0.3f * distance / totalDistance;
    }

    private int calOffsetX(int totalOffsetX, Rect rect) {
        int centerY = getHeight() / 2;
        int distance = Math.abs(centerY - rect.centerY());
        int totalDistance = itemHeight * (showCount / 2);
        return totalOffsetX * distance / totalDistance;
    }

    private void initVelocityTrackerIfNotExists() {
        if (mVelocityTracker == null) {
            mVelocityTracker = VelocityTracker.obtain();
        }
    }

    private void recycleVelocityTracker() {
        if (mVelocityTracker != null) {
            mVelocityTracker.recycle();
            mVelocityTracker = null;
        }
    }

    public interface OnSelectedListener {
        void onSelected(Context context, int selectedIndex);
    }
}
