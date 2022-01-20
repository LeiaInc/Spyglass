package com.linkedin.android.spyglass.ui;

import android.content.Context;
import android.content.res.TypedArray;
import android.os.Build;
import android.os.Parcel;
import android.os.Parcelable;
import android.text.Layout;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.SpannedString;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.inputmethod.InputMethodManager;
import android.widget.TextView;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.linkedin.android.spyglass.R;
import com.linkedin.android.spyglass.mentions.MentionSpan;
import com.linkedin.android.spyglass.mentions.MentionSpanConfig;
import com.linkedin.android.spyglass.mentions.Mentionable;
import com.linkedin.android.spyglass.mentions.MentionsEditable;
import com.linkedin.android.spyglass.tokenization.interfaces.MentionClickReceiver;

import java.util.List;

public class MentionsTextView extends TextView {

    private MentionSpanConfig mentionSpanConfig;
    private boolean isLongPressed;
    private CheckLongClickRunnable longClickRunnable;

    public MentionsTextView(@NonNull Context context) {
        super(context);
        init(null, 0);
    }

    public MentionsTextView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(attrs, 0);
    }

    public MentionsTextView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init(attrs, defStyle);
    }

    /**
     * Initialization method called by all constructors.
     */
    private void init(@Nullable AttributeSet attrs, int defStyleAttr) {
        // Get the mention span config from custom attributes
        mentionSpanConfig = parseMentionSpanConfigFromAttributes(attrs, defStyleAttr);
    }

    private MentionSpanConfig parseMentionSpanConfigFromAttributes(@Nullable AttributeSet attrs, int defStyleAttr) {
        final Context context = getContext();
        MentionSpanConfig.Builder builder = new MentionSpanConfig.Builder();
        if (attrs == null) {
            return builder.build();
        }

        TypedArray attributes = context.getTheme().obtainStyledAttributes(attrs, R.styleable.MentionsView, defStyleAttr, 0);
        @ColorInt int normalTextColor = attributes.getColor(R.styleable.MentionsView_mentionTextColor, -1);
        builder.setMentionTextColor(normalTextColor);
        @ColorInt int normalBgColor = attributes.getColor(R.styleable.MentionsView_mentionTextBackgroundColor, -1);
        builder.setMentionTextBackgroundColor(normalBgColor);
        @ColorInt int selectedTextColor = attributes.getColor(R.styleable.MentionsView_selectedMentionTextColor, -1);
        builder.setSelectedMentionTextColor(selectedTextColor);
        @ColorInt int selectedBgColor = attributes.getColor(R.styleable.MentionsView_selectedMentionTextBackgroundColor, -1);
        builder.setSelectedMentionTextBackgroundColor(selectedBgColor);

        attributes.recycle();

        return builder.build();
    }

    public void setTextWithMentions(String text, List<MentionSpan> mentionSpans) {
        SpannableString spannable = new SpannableString(text);
        // Create the same spans but with current span style and click listener
        for (MentionSpan span : mentionSpans) {
            MentionSpan customSpan = createMentionSpan(span.getMention(), mentionSpanConfig, span.getStart(), span.getEnd());
            spannable.setSpan(customSpan, customSpan.getStart(), customSpan.getEnd(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        setText(spannable);
    }

    private MentionSpan createMentionSpan(@NonNull Mentionable mention, @Nullable MentionSpanConfig config, int start, int end) {
        return new MentionSpan(mention, config, start, end) {
            @Override
            public void onClick(@NonNull View widget) {
                if (mMentionClickReceiver == null)
                    super.onClick(widget);
                else {
                    mMentionClickReceiver.onMentionClick(mention);
                }
            }
        };
    }

    @Override
    public boolean onTouchEvent(@NonNull MotionEvent event) {
        final MentionSpan touchedSpan = getTouchedSpan(event);

        // Android 6 occasionally throws a NullPointerException inside Editor.onTouchEvent()
        // for ACTION_UP when attempting to display (uninitialised) text handles.
        boolean superResult;
        if (android.os.Build.VERSION.SDK_INT == Build.VERSION_CODES.M &&
                event.getActionMasked() == MotionEvent.ACTION_UP) {
            try {
                superResult = super.onTouchEvent(event);
            } catch (NullPointerException ignored) {
                // Ignore this (see above) - since we're now in an unknown state let's clear all
                // selection (which is still better than an arbitrary crash that we can't control):
                clearFocus();
                superResult = true;
            }
        } else {
            superResult = super.onTouchEvent(event);
        }

        if (event.getAction() == MotionEvent.ACTION_UP) {
            // Don't call the onclick on mention if MotionEvent.ACTION_UP is for long click action,
            if (!isLongPressed && touchedSpan != null) {
                // Manually click span and show soft keyboard
                touchedSpan.onClick(this);
                Context context = getContext();
                if (context != null) {
                    InputMethodManager imm = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
                    imm.showSoftInput(this, 0);
                }
                return true;
            }
        } else if (event.getAction() == MotionEvent.ACTION_DOWN) {
            isLongPressed = false;
            if (isLongClickable() && touchedSpan != null) {
                if (longClickRunnable == null) {
                    longClickRunnable = new CheckLongClickRunnable();
                }
                removeCallbacks(longClickRunnable);
                postDelayed(longClickRunnable, ViewConfiguration.getLongPressTimeout());
            }
        } else if (event.getAction() == MotionEvent.ACTION_CANCEL) {
            isLongPressed = false;
        }

        return superResult;
    }

    @Nullable
    protected MentionSpan getTouchedSpan(@NonNull MotionEvent event) {
        Layout layout = getLayout();
        // Note: Layout can be null if text or width has recently changed, see MOB-38193
        if (event == null || layout == null) {
            return null;
        }

        int x = (int) event.getX();
        int y = (int) event.getY();

        x -= getTotalPaddingLeft();
        y -= getTotalPaddingTop();

        x += getScrollX();
        y += getScrollY();

        int line = layout.getLineForVertical(y);
        int off = layout.getOffsetForHorizontal(line, x);
        SpannedString text = (SpannedString) getText();
        if (text != null && off >= getText().length()) {
            return null;
        }

        // Get the MentionSpans in the area that the user tapped
        // If one exists, call the onClick method manually
        MentionSpan[] spans = text.getSpans(off, off, MentionSpan.class);
        if (spans.length > 0) {
            return spans[0];
        }
        return null;
    }

    /**
     * Runnable which detects the long click action.
     */
    private class CheckLongClickRunnable implements Runnable {

        @Override
        public void run() {
            if (isPressed()) {
                isLongPressed = true;
            }
        }
    }

    private MentionClickReceiver mMentionClickReceiver;

    public void setMentionClickReceiver(@Nullable final MentionClickReceiver mentionClickReceiver) {
        mMentionClickReceiver = mentionClickReceiver;
    }

    @NonNull
    public MentionsEditable getMentionsText() {
        CharSequence text = super.getText();
        return text instanceof MentionsEditable ? (MentionsEditable) text : new MentionsEditable(text);
    }

    @NonNull
    @Override
    public Parcelable onSaveInstanceState() {
        Parcelable parcelable = super.onSaveInstanceState();
        return new SavedState(parcelable, getMentionsText());
    }

    @Override
    public void onRestoreInstanceState(Parcelable state) {
        if (!(state instanceof SavedState)) {
            super.onRestoreInstanceState(state);
            return;
        }

        SavedState savedState = (SavedState) state;
        super.onRestoreInstanceState(savedState.getSuperState());
        setText(savedState.mentionsEditable);
    }

    /**
     * Convenience class to save/restore the MentionsEditable state.
     */
    protected static class SavedState extends BaseSavedState {
        public MentionsEditable mentionsEditable;

        private SavedState(Parcelable superState, MentionsEditable mentionsEditable) {
            super(superState);
            this.mentionsEditable = mentionsEditable;
        }

        private SavedState(Parcel in) {
            super(in);
            mentionsEditable = in.readParcelable(MentionsEditable.class.getClassLoader());
        }

        @Override
        public void writeToParcel(@NonNull Parcel dest, int flags) {
            super.writeToParcel(dest, flags);
            dest.writeParcelable(mentionsEditable, flags);
        }

        public static final Parcelable.Creator<MentionsTextView.SavedState> CREATOR = new Creator<MentionsTextView.SavedState>() {

            public MentionsTextView.SavedState createFromParcel(Parcel in) {
                return new MentionsTextView.SavedState(in);
            }

            public MentionsTextView.SavedState[] newArray(int size) {
                return new MentionsTextView.SavedState[size];
            }
        };
    }
}
