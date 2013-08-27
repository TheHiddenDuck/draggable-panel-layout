/*
 * Copyright 2013 Alexander Osmanov
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *     
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * 
 */
package com.evilduck.animtest;

import android.animation.Animator;
import android.animation.Animator.AnimatorListener;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.support.v4.view.ViewCompat;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;

public class DraggedPanelLayout extends FrameLayout {

    private static final float PARALLAX_FACTOR = 0.2f;

    private static DecelerateInterpolator sDecelerator = new DecelerateInterpolator();

    private float parallaxFactor;

    private int bottomPanelPeekHeight;

    private float touchY;

    private boolean touching;

    private boolean opened = false;

    private VelocityTracker velocityTracker = null;

    private View bottomPanel;

    private View slidingPanel;

    private Drawable shadowDrawable;

    private boolean animating = false;

    private boolean willDrawShadow = false;

    private int touchSlop;

    private boolean isBeingDragged = false;

    public DraggedPanelLayout(Context context, AttributeSet attrs, int defStyle) {
	super(context, attrs, defStyle);

	initAttrs(context, attrs);
    }

    public DraggedPanelLayout(Context context, AttributeSet attrs) {
	super(context, attrs);

	initAttrs(context, attrs);
    }

    public DraggedPanelLayout(Context context) {
	super(context);

	bottomPanelPeekHeight = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 100, getResources()
		.getDisplayMetrics());
	parallaxFactor = PARALLAX_FACTOR;

	if (!isInEditMode()) {
	    shadowDrawable = getResources().getDrawable(R.drawable.shadow_np);
	    willDrawShadow = true;
	}

	touchSlop = ViewConfiguration.get(getContext()).getScaledTouchSlop();
    }

    public void initAttrs(Context context, AttributeSet attrs) {
	TypedArray a = context.getTheme().obtainStyledAttributes(attrs, R.styleable.DraggedPanelLayout, 0, 0);

	try {
	    parallaxFactor = a.getFloat(R.styleable.DraggedPanelLayout_parallax_factor, PARALLAX_FACTOR);
	    if (parallaxFactor < 0.1 || parallaxFactor > 0.9) {
		parallaxFactor = PARALLAX_FACTOR;
	    }

	    int defaultHeight = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 100, getResources()
		    .getDisplayMetrics());
	    bottomPanelPeekHeight = a.getDimensionPixelSize(R.styleable.DraggedPanelLayout_bottom_panel_height,
		    defaultHeight);
	    int shadowDrawableId = a.getResourceId(R.styleable.DraggedPanelLayout_shadow_drawable, -1);
	    if (shadowDrawableId != -1) {
		shadowDrawable = getResources().getDrawable(shadowDrawableId);
		willDrawShadow = true;
		setWillNotDraw(!willDrawShadow);
	    }
	} finally {
	    a.recycle();
	}

	final ViewConfiguration configuration = ViewConfiguration.get(getContext());
	touchSlop = configuration.getScaledTouchSlop();
    }

    @Override
    public void draw(Canvas canvas) {
	super.draw(canvas);

	if (!isInEditMode() && willDrawShadow) {
	    int top = (int) (slidingPanel.getTop() + slidingPanel.getTranslationY());
	    shadowDrawable.setBounds(0, top - shadowDrawable.getIntrinsicHeight(), getMeasuredWidth(), top);
	    shadowDrawable.draw(canvas);

	}
	if (animating) {
	    ViewCompat.postInvalidateOnAnimation(DraggedPanelLayout.this);
	}
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
	super.onLayout(changed, left, top, right, bottom);

	if (getChildCount() != 2) {
	    throw new IllegalStateException("DraggedPanelLayout must have 2 children!");
	}

	bottomPanel = getChildAt(0);
	bottomPanel.layout(left, top, right, bottom - bottomPanelPeekHeight);

	slidingPanel = getChildAt(1);
	if (!opened) {
	    int panelMeasuredHeight = slidingPanel.getMeasuredHeight();
	    slidingPanel.layout(left, bottom - bottomPanelPeekHeight, right, bottom - bottomPanelPeekHeight
		    + panelMeasuredHeight);
	}
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
	if (event.getAction() == MotionEvent.ACTION_DOWN) {
	    touchY = event.getY();
	} else if (event.getAction() == MotionEvent.ACTION_MOVE) {
	    if (Math.abs(touchY - event.getY()) > touchSlop) {
		isBeingDragged = true;
		startDragging(event);
	    }
	} else if (event.getAction() == MotionEvent.ACTION_UP) {
	    isBeingDragged = false;
	}

	return isBeingDragged;
    }

    public void startDragging(MotionEvent event) {
	touchY = event.getY();
	touching = true;

	bottomPanel.setVisibility(View.VISIBLE);

	obtainVelocityTracker();
	velocityTracker.addMovement(event);
	allowShadow();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
	obtainVelocityTracker();

	if (event.getAction() == MotionEvent.ACTION_DOWN) {
	    startDragging(event);
	} else if (event.getAction() == MotionEvent.ACTION_MOVE) {
	    if (touching) {
		velocityTracker.addMovement(event);

		float translation = event.getY() - touchY;
		translation = boundTranslation(translation);

		slidingPanel.setTranslationY(translation);
		bottomPanel
			.setTranslationY((float) (opened ? -(getMeasuredHeight() - bottomPanelPeekHeight - translation)
				* parallaxFactor : translation * parallaxFactor));

		if (willDrawShadow) {
		    ViewCompat.postInvalidateOnAnimation(this);
		}
	    }
	} else if (event.getAction() == MotionEvent.ACTION_UP) {
	    isBeingDragged = false;
	    touching = false;

	    velocityTracker.addMovement(event);
	    velocityTracker.computeCurrentVelocity(1);
	    float velocityY = velocityTracker.getYVelocity();
	    velocityTracker.recycle();
	    velocityTracker = null;

	    finishAnimateToFinalPosition(velocityY);
	}

	return true;
    }

    public float boundTranslation(float translation) {
	if (!opened) {
	    if (translation > 0) {
		translation = 0;
	    }
	    if (Math.abs(translation) >= slidingPanel.getMeasuredHeight() - bottomPanelPeekHeight) {
		translation = -slidingPanel.getMeasuredHeight() + bottomPanelPeekHeight;
	    }
	} else {
	    if (translation < 0) {
		translation = 0;
	    }
	    if (translation >= slidingPanel.getMeasuredHeight() - bottomPanelPeekHeight) {
		translation = slidingPanel.getMeasuredHeight() - bottomPanelPeekHeight;
	    }
	}
	return translation;
    }

    public void obtainVelocityTracker() {
	if (velocityTracker == null) {
	    velocityTracker = VelocityTracker.obtain();
	}
    }

    public void finishAnimateToFinalPosition(float velocityY) {
	final boolean flinging = Math.abs(velocityY) > 0.5;

	boolean opening;
	float distY;
	long duration;

	if (flinging) {
	    // If fling velocity is fast enough we continue the motion starting
	    // with the current speed

	    opening = velocityY < 0;

	    distY = calculateDistance(opening);
	    duration = Math.abs(Math.round(distY / velocityY));

	    animatePanel(opening, distY, duration);
	} else {
	    // If user motion is slow or stopped we check if half distance is
	    // traveled and based on that complete the motion

	    boolean halfway = Math.abs(slidingPanel.getTranslationY()) >= (getMeasuredHeight() - bottomPanelPeekHeight) / 2;
	    opening = opened ? !halfway : halfway;

	    distY = calculateDistance(opening);
	    duration = Math.round(300 * (double) Math.abs((double) slidingPanel.getTranslationY())
		    / (double) (getMeasuredHeight() - bottomPanelPeekHeight));

	}

	animatePanel(opening, distY, duration);
    }

    public float calculateDistance(boolean opening) {
	float distY;
	if (opened) {
	    distY = opening ? -slidingPanel.getTranslationY() : getMeasuredHeight() - bottomPanelPeekHeight
		    - slidingPanel.getTranslationY();
	} else {
	    distY = opening ? -(getMeasuredHeight() - bottomPanelPeekHeight + slidingPanel.getTranslationY())
		    : -slidingPanel.getTranslationY();
	}

	return distY;
    }

    public void animatePanel(final boolean opening, float distY, long duration) {
	ObjectAnimator slidingPanelAnimator = ObjectAnimator.ofFloat(slidingPanel, View.TRANSLATION_Y,
		slidingPanel.getTranslationY(), slidingPanel.getTranslationY() + distY);
	ObjectAnimator bottomPanelAnimator = ObjectAnimator.ofFloat(bottomPanel, View.TRANSLATION_Y,
		bottomPanel.getTranslationY(), bottomPanel.getTranslationY() + (float) (distY * parallaxFactor));

	AnimatorSet set = new AnimatorSet();
	set.playTogether(slidingPanelAnimator, bottomPanelAnimator);
	set.setDuration(duration);
	set.setInterpolator(sDecelerator);
	set.addListener(new MyAnimListener(opening));
	set.start();
    }

    class MyAnimListener implements AnimatorListener {

	int oldLayerTypeOne;

	int oldLayerTypeTwo;

	boolean opening;

	public MyAnimListener(boolean opening) {
	    super();
	    this.opening = opening;
	}

	@Override
	public void onAnimationStart(Animator animation) {
	    oldLayerTypeOne = slidingPanel.getLayerType();
	    oldLayerTypeOne = bottomPanel.getLayerType();

	    slidingPanel.setLayerType(View.LAYER_TYPE_HARDWARE, null);
	    bottomPanel.setLayerType(View.LAYER_TYPE_HARDWARE, null);

	    bottomPanel.setVisibility(View.VISIBLE);

	    if (willDrawShadow) {
		animating = true;
		ViewCompat.postInvalidateOnAnimation(DraggedPanelLayout.this);
	    }
	}

	@Override
	public void onAnimationRepeat(Animator animation) {
	}

	@Override
	public void onAnimationEnd(Animator animation) {
	    setOpenedState(opening);

	    bottomPanel.setTranslationY(0);
	    slidingPanel.setTranslationY(0);

	    slidingPanel.setLayerType(oldLayerTypeOne, null);
	    bottomPanel.setLayerType(oldLayerTypeTwo, null);

	    requestLayout();

	    if (willDrawShadow) {
		animating = false;
		ViewCompat.postInvalidateOnAnimation(DraggedPanelLayout.this);
	    }
	}

	@Override
	public void onAnimationCancel(Animator animation) {
	    if (willDrawShadow) {
		animating = false;
		ViewCompat.postInvalidateOnAnimation(DraggedPanelLayout.this);
	    }
	}

    };

    private void setOpenedState(boolean opened) {
	this.opened = opened;
	bottomPanel.setVisibility(opened ? View.GONE : View.VISIBLE);
	hideShadowIfNotNeeded();
    }

    private void allowShadow() {
	willDrawShadow = shadowDrawable != null;
	setWillNotDraw(!willDrawShadow);
    }

    private void hideShadowIfNotNeeded() {
	willDrawShadow = shadowDrawable != null && !opened;
	setWillNotDraw(!willDrawShadow);
    }

}
