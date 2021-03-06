package com.sendbird.desk.android.sample.utils.image;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.os.Build;
import android.support.v7.widget.AppCompatImageView;
import android.util.AttributeSet;

import com.sendbird.desk.android.sample.R;

public class TopCornerRoundedImageView extends AppCompatImageView {

    private Path mClipPath;
    private Path mBackgroundPath;
    private Paint mBackgroundPaint;
    private int mCornerRadius;
    private int mBorderWidth;
    private int mBorderColor;

    private RectF mRect = new RectF();

    public TopCornerRoundedImageView(Context context) {
        super(context);
    }

    public TopCornerRoundedImageView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public TopCornerRoundedImageView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.RoundedImageView, defStyleAttr, 0);

        mCornerRadius = a.getDimensionPixelSize(R.styleable.RoundedImageView_deskCornerRadius, 0);
        mBorderWidth = a.getDimensionPixelSize(R.styleable.RoundedImageView_deskBorderWidth, 0);
        mBorderColor = a.getColor(R.styleable.RoundedImageView_deskBorderColor, 0);

        a.recycle();

        // Below Jelly Bean, clipPath on canvas would not work because lack of hardware acceleration
        // support. Hence, we should explicitly say to use software acceleration.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR2
                && Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            setLayerType(LAYER_TYPE_SOFTWARE, null);
        }

        mBackgroundPaint = new Paint();
        mBackgroundPaint.setAntiAlias(true);
        mBackgroundPaint.setStrokeWidth(mBorderWidth);
        mBackgroundPaint.setColor(mBorderColor);
        mBackgroundPaint.setStyle(Paint.Style.STROKE);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        mRect.set(0, 0, getWidth(), getHeight());
        mClipPath = getPath(mCornerRadius, true, true, false, false);
        mBackgroundPath = getPath(mCornerRadius, true, true, false, false);

        canvas.clipPath(mClipPath);
        super.onDraw(canvas);
        canvas.drawPath(mBackgroundPath, mBackgroundPaint);
    }

    private Path getPath(float radius, boolean topLeft, boolean topRight, boolean bottomRight, boolean bottomLeft) {
        final Path path = new Path();
        final float[] radii = new float[8];

        if (topLeft) {
            radii[0] = radius;
            radii[1] = radius;
        }

        if (topRight) {
            radii[2] = radius;
            radii[3] = radius;
        }

        if (bottomRight) {
            radii[4] = radius;
            radii[5] = radius;
        }

        if (bottomLeft) {
            radii[6] = radius;
            radii[7] = radius;
        }

        path.addRoundRect(mRect, radii, Path.Direction.CW);
        return path;
    }
}
