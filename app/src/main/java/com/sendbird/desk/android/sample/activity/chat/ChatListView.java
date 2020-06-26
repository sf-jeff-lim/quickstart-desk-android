package com.sendbird.desk.android.sample.activity.chat;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.widget.ListView;

public class ChatListView extends ListView {

    private static final float mMinRange = 5.0f;

    private Context mContext;
    private float mListViewX;
    private float mListViewY;

    public ChatListView(Context context) {
        super(context);
        mContext = context;
    }

    public ChatListView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mContext = context;
    }

    public ChatListView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mContext = context;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        switch (ev.getAction()) {
            case MotionEvent.ACTION_DOWN:
                mListViewX = ev.getX();
                mListViewY = ev.getY();
                break;

            case MotionEvent.ACTION_MOVE:
                break;

            case MotionEvent.ACTION_UP:
                if (Math.abs(ev.getX() - mListViewX) <= mMinRange && Math.abs(ev.getY() - mListViewY) <= mMinRange) {
                    if (mContext instanceof ChatActivity) {
                        ((ChatActivity)mContext).closeSoftKeyboard();
                    }
                }
                break;
        }
        return super.onInterceptTouchEvent(ev);
    }
}