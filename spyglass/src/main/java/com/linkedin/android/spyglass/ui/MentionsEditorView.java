/*
 * Copyright 2015 LinkedIn Corp. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 */

package com.linkedin.android.spyglass.ui;

import android.content.Context;
import android.content.res.TypedArray;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.Layout;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.RelativeLayout;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.linkedin.android.spyglass.R;
import com.linkedin.android.spyglass.mentions.MentionSpan;
import com.linkedin.android.spyglass.mentions.MentionSpanConfig;
import com.linkedin.android.spyglass.mentions.Mentionable;
import com.linkedin.android.spyglass.mentions.MentionsEditable;
import com.linkedin.android.spyglass.suggestions.SuggestionsAdapter;
import com.linkedin.android.spyglass.suggestions.SuggestionsResult;
import com.linkedin.android.spyglass.suggestions.impl.BasicSuggestionsListBuilder;
import com.linkedin.android.spyglass.suggestions.interfaces.OnSuggestionsVisibilityChangeListener;
import com.linkedin.android.spyglass.suggestions.interfaces.SuggestionsListBuilder;
import com.linkedin.android.spyglass.suggestions.interfaces.SuggestionsResultListener;
import com.linkedin.android.spyglass.suggestions.interfaces.SuggestionsVisibilityManager;
import com.linkedin.android.spyglass.tokenization.QueryToken;
import com.linkedin.android.spyglass.tokenization.impl.WordTokenizer;
import com.linkedin.android.spyglass.tokenization.impl.WordTokenizerConfig;
import com.linkedin.android.spyglass.tokenization.interfaces.MentionClickReceiver;
import com.linkedin.android.spyglass.tokenization.interfaces.QueryTokenReceiver;
import com.linkedin.android.spyglass.tokenization.interfaces.Tokenizer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Custom view for the RichEditor. Manages three subviews:
 * <p/>
 * 1. EditText - contains text typed by user <br/>
 * 2. TextView - displays count of the number of characters in the EditText <br/>
 * 3. ListView - displays mention suggestions when relevant
 * <p/>
 * <b>XML attributes</b>
 * <p/>
 * See {@link R.styleable#RichEditorView Attributes}
 *
 * @attr ref R.styleable#RichEditorView_mentionTextColor
 * @attr ref R.styleable#RichEditorView_mentionTextBackgroundColor
 * @attr ref R.styleable#RichEditorView_selectedMentionTextColor
 * @attr ref R.styleable#RichEditorView_selectedMentionTextBackgroundColor
 */
public class MentionsEditorView extends RelativeLayout implements TextWatcher, QueryTokenReceiver,
        MentionClickReceiver, SuggestionsResultListener, SuggestionsVisibilityManager {

    private MentionsEditText mMentionsEditText;
    private int mOriginalInputType = InputType.TYPE_CLASS_TEXT; // Default to plain text
    private ListView mSuggestionsList;

    private QueryTokenReceiver mHostQueryTokenReceiver;
    private MentionClickReceiver mHostMentionClickReceiver;
    private SuggestionsAdapter mSuggestionsAdapter;
    private OnSuggestionsVisibilityChangeListener mActionListener;

    private boolean mWaitingForFirstResult = false;

    // --------------------------------------------------
    // Constructors & Initialization
    // --------------------------------------------------

    public MentionsEditorView(@NonNull Context context) {
        super(context);
        init(context, null, 0);
    }

    public MentionsEditorView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs, 0);
    }

    public MentionsEditorView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs, defStyleAttr);
    }

    public void init(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        // Inflate view from XML layout file
        LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        inflater.inflate(R.layout.editor_view, this, true);

        // Get the inner views
        mMentionsEditText = findViewById(R.id.text_editor);
        mSuggestionsList = findViewById(R.id.suggestions_list);

        // Get the MentionSpanConfig from custom XML attributes and set it
        MentionSpanConfig mentionSpanConfig = parseMentionSpanConfigFromAttributes(attrs, defStyleAttr);
        mMentionsEditText.setMentionSpanConfig(mentionSpanConfig);

        // Create the tokenizer to use for the editor
        WordTokenizerConfig tokenizerConfig = new WordTokenizerConfig.Builder().build();
        WordTokenizer tokenizer = new WordTokenizer(tokenizerConfig);
        mMentionsEditText.setTokenizer(tokenizer);

        // Set various delegates on the MentionEditText to the RichEditorView
        mMentionsEditText.setSuggestionsVisibilityManager(this);
        mMentionsEditText.addTextChangedListener(this);
        mMentionsEditText.setQueryTokenReceiver(this);
        mMentionsEditText.setMentionClickReceiver(this);
        mMentionsEditText.setAvoidPrefixOnTap(true);

        // Set the suggestions adapter
        SuggestionsListBuilder listBuilder = new BasicSuggestionsListBuilder();
        mSuggestionsAdapter = new SuggestionsAdapter(context, this, listBuilder);
        mSuggestionsList.setAdapter(mSuggestionsAdapter);

        // Set the item click listener
        mSuggestionsList.setOnItemClickListener((parent, view, position, id) -> {
            Mentionable mention = (Mentionable) mSuggestionsAdapter.getItem(position);
            if (mMentionsEditText != null) mMentionsEditText.insertMention(mention);
            mSuggestionsAdapter.clear();
        });
    }

    private MentionSpanConfig parseMentionSpanConfigFromAttributes(@Nullable AttributeSet attrs, int defStyleAttr) {
        final Context context = getContext();
        MentionSpanConfig.Builder builder = new MentionSpanConfig.Builder();
        if (attrs == null) {
            return builder.build();
        }

        TypedArray attributes = context.getTheme().obtainStyledAttributes(attrs,
                R.styleable.RichEditorView,
                defStyleAttr,
                0);
        @ColorInt int normalTextColor = attributes.getColor(R.styleable.RichEditorView_mentionTextColor, -1);
        builder.setMentionTextColor(normalTextColor);
        @ColorInt int normalBgColor = attributes.getColor(R.styleable.RichEditorView_mentionTextBackgroundColor, -1);
        builder.setMentionTextBackgroundColor(normalBgColor);
        @ColorInt int selectedTextColor = attributes.getColor(R.styleable.RichEditorView_selectedMentionTextColor, -1);
        builder.setSelectedMentionTextColor(selectedTextColor);
        @ColorInt int selectedBgColor = attributes.getColor(R.styleable.RichEditorView_selectedMentionTextBackgroundColor, -1);
        builder.setSelectedMentionTextBackgroundColor(selectedBgColor);

        attributes.recycle();

        return builder.build();
    }

    // --------------------------------------------------
    // Public Span & UI Methods
    // --------------------------------------------------

    /**
     * Allows filters in the input element.
     * <p>
     * Example: obj.setInputFilters(new InputFilter[]{new InputFilter.LengthFilter(30)});
     *
     * @param filters the list of filters to apply
     */
    public void setInputFilters(@Nullable InputFilter... filters) {
        mMentionsEditText.setFilters(filters);


    }

    /**
     * @return a list of {@link MentionSpan} objects currently in the editor
     */
    @NonNull
    public ArrayList<MentionSpan> getMentionSpans() {
        return (mMentionsEditText != null) ? mMentionsEditText.getMentionsText().getMentionSpans() : new ArrayList<>();
    }



    /**
     * @return current line number of the cursor, or -1 if no cursor
     */
    public int getCurrentCursorLine() {
        int selectionStart = mMentionsEditText.getSelectionStart();
        Layout layout = mMentionsEditText.getLayout();
        if (layout != null && !(selectionStart == -1)) {
            return layout.getLineForOffset(selectionStart);
        }
        return -1;
    }

    // --------------------------------------------------
    // TextWatcher Implementation
    // --------------------------------------------------

    /**
     * {@inheritDoc}
     */
    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        // Do nothing
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {
        // Do nothing
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void afterTextChanged(Editable s) {
    }

    // --------------------------------------------------
    // QueryTokenReceiver Implementation
    // --------------------------------------------------

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public List<String> onQueryReceived(@NonNull QueryToken queryToken) {
        // Pass the query token to a host receiver
        if (mHostQueryTokenReceiver != null) {
            List<String> buckets = mHostQueryTokenReceiver.onQueryReceived(queryToken);
            mSuggestionsAdapter.notifyQueryTokenReceived(queryToken, buckets);
        }
        return Collections.emptyList();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Mentionable getSuggestionFromQueryInstantly(@NonNull QueryToken queryToken) {
        if (mHostQueryTokenReceiver != null) {
            return mHostQueryTokenReceiver.getSuggestionFromQueryInstantly(queryToken);
        }
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onMentionClick(@NonNull Mentionable mention) {
        if (mHostMentionClickReceiver != null) {
            mHostMentionClickReceiver.onMentionClick(mention);
        }
    }

    // --------------------------------------------------
    // SuggestionsResultListener Implementation
    // --------------------------------------------------

    /**
     * {@inheritDoc}
     */
    @Override
    public void onReceiveSuggestionsResult(final @NonNull SuggestionsResult result, final @NonNull String bucket) {
        // Add the mentions and notify the editor/dropdown of the changes on the UI thread
        post(() -> {
            if (mSuggestionsAdapter != null) {
                mSuggestionsAdapter.addSuggestions(result, bucket, mMentionsEditText);
            }
            // Make sure the list is scrolled to the top once you receive the first query result
            if (mWaitingForFirstResult && mSuggestionsList != null) {
                mSuggestionsList.setSelection(0);
                mWaitingForFirstResult = false;
            }
        });
    }

    // --------------------------------------------------
    // SuggestionsManager Implementation
    // --------------------------------------------------

    /**
     * {@inheritDoc}
     */
    public void displaySuggestions(boolean display) {

        // If nothing to change, return early
        if (display == isDisplayingSuggestions() || mMentionsEditText == null) {
            return;
        }

        // Change view depending on whether suggestions are being shown or not
        if (display) {
            disableSpellingSuggestions(true);
            mSuggestionsList.setVisibility(View.VISIBLE);
            int cursorLine = getCurrentCursorLine();
            Layout layout = mMentionsEditText.getLayout();
            mMentionsEditText.setVerticalScrollBarEnabled(false);
            if (layout != null) {
                int lineTop = layout.getLineTop(cursorLine);
                mMentionsEditText.scrollTo(0, lineTop);
            }
            // Notify action listener that list was shown
            if (mActionListener != null) {
                mActionListener.onSuggestionsDisplayed();
            }
        } else {
            disableSpellingSuggestions(false);
            mSuggestionsList.setVisibility(View.GONE);
            mMentionsEditText.setVerticalScrollBarEnabled(true);
            // Notify action listener that list was hidden
            if (mActionListener != null) {
                mActionListener.onSuggestionsHidden();
            }
        }

        requestLayout();
        invalidate();
    }


    /**
     * Check current query manually
     */
    public void checkCurrentQuery() {
        if (mMentionsEditText != null) {
            mMentionsEditText.checkCurrentQuery(true);
        }
    }

    /**
     * {@inheritDoc}
     */
    public boolean isDisplayingSuggestions() {
        return mSuggestionsList.getVisibility() == View.VISIBLE;
    }

    /**
     * Disables spelling suggestions from the user's keyboard.
     * This is necessary because some keyboards will replace the input text with
     * spelling suggestions automatically, which changes the suggestion results.
     * This results in a confusing user experience.
     *
     * @param disable {@code true} if spelling suggestions should be disabled, otherwise {@code false}
     */
    private void disableSpellingSuggestions(boolean disable) {
        // toggling suggestions often resets the cursor location, but we don't want that to happen
        int start = mMentionsEditText.getSelectionStart();
        int end = mMentionsEditText.getSelectionEnd();
        // -1 means there is no selection or cursor.
        if (start == -1 || end == -1) {
            return;
        }
        if (disable) {
            // store the previous input type
            mOriginalInputType = mMentionsEditText.getInputType();
        }
        mMentionsEditText.setRawInputType(disable ? InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS : mOriginalInputType);
        mMentionsEditText.setSelection(start, end);
    }

    // --------------------------------------------------
    // Pass-Through Methods to the MentionsEditText
    // --------------------------------------------------

    /**
     * Convenience method for {@link MentionsEditText#getCurrentTokenString()}.
     *
     * @return a string representing currently being considered for a possible query, as the user typed it
     */
    @NonNull
    public String getCurrentTokenString() {
        if (mMentionsEditText == null) {
            return "";
        }
        return mMentionsEditText.getCurrentTokenString();
    }

    /**
     * Convenience method for {@link MentionsEditText#getCurrentKeywordsString()}.
     *
     * @return a String representing current keywords in the underlying {@link EditText}
     */
    @NonNull
    public String getCurrentKeywordsString() {
        if (mMentionsEditText == null) {
            return "";
        }
        return mMentionsEditText.getCurrentKeywordsString();
    }

    /**
     * Resets the given {@link MentionSpan} in the editor, forcing it to redraw with its latest drawable state.
     *
     * @param span the {@link MentionSpan} to update
     */
    public void updateSpan(@NonNull MentionSpan span) {
        if (mMentionsEditText != null) {
            mMentionsEditText.updateSpan(span);
        }
    }

    /**
     * Deselects any spans in the editor that are currently selected.
     */
    public void deselectAllSpans() {
        if (mMentionsEditText != null) {
            mMentionsEditText.deselectAllSpans();
        }
    }

    /**
     * Adds an {@link TextWatcher} to the internal {@link MentionsEditText}.
     *
     * @param hostTextWatcher the {TextWatcher} to add
     */
    public void addTextChangedListener(@NonNull final TextWatcher hostTextWatcher) {
        if (mMentionsEditText != null) {
            mMentionsEditText.addTextChangedListener(hostTextWatcher);
        }
    }

    /**
     * @return the {@link MentionsEditable} within the embedded {@link MentionsEditText}
     */
    @NonNull
    public MentionsEditable getText() {
        return (mMentionsEditText != null) ? ((MentionsEditable) mMentionsEditText.getText()) : new MentionsEditable("");
    }

    @NonNull
    public String getPlainText() {
        return mMentionsEditText.getText().toString();
    }

    /**
     * @return the {@link Tokenizer} in use
     */
    @Nullable
    public Tokenizer getTokenizer() {
        return (mMentionsEditText != null) ? mMentionsEditText.getTokenizer() : null;
    }

    /**
     * Sets the text being displayed within the {@link MentionsEditorView}. Note that this removes the
     * {@link TextWatcher} temporarily to avoid changing the text while listening to text changes
     * (which could result in an infinite loop).
     *
     * @param text the text to display
     */
    public void setText(final @NonNull MentionsEditable text) {
        if (mMentionsEditText != null) {
            mMentionsEditText.setText(text);
        }
    }

    /**
     * Sets the text hint to use within the embedded {@link MentionsEditText}.
     *
     * @param hint the text hint to use
     */
    public void setHint(final @NonNull CharSequence hint) {
        if (mMentionsEditText != null) {
            mMentionsEditText.setHint(hint);
        }
    }

    /**
     * Sets the input type of the embedded {@link MentionsEditText}.
     *
     * @param type the input type of the {@link MentionsEditText}
     */
    public void setInputType(final int type) {
        if (mMentionsEditText != null) {
            mMentionsEditText.setInputType(type);
        }
    }

    /**
     * Sets the selection within the embedded {@link MentionsEditText}.
     *
     * @param index the index of the selection within the embedded {@link MentionsEditText}
     */
    public void setSelection(final int index) {
        if (mMentionsEditText != null) {
            mMentionsEditText.setSelection(index);
        }
    }

    /**
     * Sets the {@link Tokenizer} for the {@link MentionsEditText} to use.
     *
     * @param tokenizer the {@link Tokenizer} to use
     */
    public void setTokenizer(final @NonNull Tokenizer tokenizer) {
        if (mMentionsEditText != null) {
            mMentionsEditText.setTokenizer(tokenizer);
        }
    }

    /**
     * Register a {@link com.linkedin.android.spyglass.ui.MentionsEditText.MentionWatcher} in order to receive callbacks
     * when mentions are changed.
     *
     * @param watcher the {@link com.linkedin.android.spyglass.ui.MentionsEditText.MentionWatcher} to add
     */
    public void addMentionWatcher(@NonNull MentionsEditText.MentionWatcher watcher) {
        if (mMentionsEditText != null) {
            mMentionsEditText.addMentionWatcher(watcher);
        }
    }

    /**
     * Remove a {@link com.linkedin.android.spyglass.ui.MentionsEditText.MentionWatcher} from receiving anymore callbacks
     * when mentions are changed.
     *
     * @param watcher the {@link com.linkedin.android.spyglass.ui.MentionsEditText.MentionWatcher} to remove
     */
    public void removeMentionWatcher(@NonNull MentionsEditText.MentionWatcher watcher) {
        if (mMentionsEditText != null) {
            mMentionsEditText.removeMentionWatcher(watcher);
        }
    }

    // --------------------------------------------------
    // RichEditorView-specific Setters
    // --------------------------------------------------

    /**
     * Sets the receiver of any tokens generated by the embedded {@link MentionsEditText}. The
     * receive should act on the queries as they are received and call
     * {@link #onReceiveSuggestionsResult(SuggestionsResult, String)} once the suggestions are ready.
     *
     * @param client the object that can receive {@link QueryToken} objects and generate suggestions from them
     */
    public void setQueryTokenReceiver(final @Nullable QueryTokenReceiver client) {
        mHostQueryTokenReceiver = client;
    }

    /**
     * Sets the receiver of any mentions clicked
     *
     * @param client the object that can receive {@link Mentionable} objects and insert mention from it
     */
    public void setMentionClickReceiver(final @Nullable MentionClickReceiver client) {
        mHostMentionClickReceiver = client;
    }

    /**
     * Sets a listener for anyone interested in specific actions of the {@link MentionsEditorView}.
     *
     * @param listener the object that wants to listen to specific actions of the {@link MentionsEditorView}
     */
    public void setOnRichEditorActionListener(final @Nullable OnSuggestionsVisibilityChangeListener listener) {
        mActionListener = listener;
    }

    /**
     * Sets the {@link com.linkedin.android.spyglass.suggestions.interfaces.SuggestionsVisibilityManager} to use (determines which and how the suggestions are displayed).
     *
     * @param suggestionsVisibilityManager the {@link com.linkedin.android.spyglass.suggestions.interfaces.SuggestionsVisibilityManager} to use
     */
    public void setSuggestionsManager(final @NonNull SuggestionsVisibilityManager suggestionsVisibilityManager) {
        if (mMentionsEditText != null && mSuggestionsAdapter != null) {
            mMentionsEditText.setSuggestionsVisibilityManager(suggestionsVisibilityManager);
            mSuggestionsAdapter.setSuggestionsManager(suggestionsVisibilityManager);
        }
    }

    /**
     * Sets the {@link SuggestionsListBuilder} to use.
     *
     * @param suggestionsListBuilder the {@link SuggestionsListBuilder} to use
     */
    public void setSuggestionsListBuilder(final @NonNull SuggestionsListBuilder suggestionsListBuilder) {
        if (mSuggestionsAdapter != null) {
            mSuggestionsAdapter.setSuggestionsListBuilder(suggestionsListBuilder);
        }
    }
}
