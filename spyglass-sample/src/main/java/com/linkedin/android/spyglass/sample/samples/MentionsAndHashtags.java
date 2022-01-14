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

package com.linkedin.android.spyglass.sample.samples;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.linkedin.android.spyglass.sample.R;
import com.linkedin.android.spyglass.sample.data.models.Hashtag;
import com.linkedin.android.spyglass.sample.data.models.Person;
import com.linkedin.android.spyglass.suggestions.SuggestionsResult;
import com.linkedin.android.spyglass.suggestions.interfaces.SuggestionsResultListener;
import com.linkedin.android.spyglass.tokenization.QueryToken;
import com.linkedin.android.spyglass.tokenization.impl.WordTokenizer;
import com.linkedin.android.spyglass.tokenization.impl.WordTokenizerConfig;
import com.linkedin.android.spyglass.tokenization.interfaces.QueryTokenReceiver;
import com.linkedin.android.spyglass.ui.RichEditorView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Simple example showing people mentions and hashtags.
 */

/**
 * TODO: Custom adapters
 * TODO: Fix backspace handling?
 * TODO: Span hashtag even if it was not found in database
 * TODO: Other UI customizations?
 */
public class MentionsAndHashtags extends AppCompatActivity implements QueryTokenReceiver {

    private static final String LOG_TAG = MentionsAndHashtags.class.getSimpleName();

    private static final int PERSON_DELAY = 1000;
    private static final int HASHTAG_DELAY = 1000;

    private static final WordTokenizerConfig tokenizerConfig = new WordTokenizerConfig
            .Builder()
            .setWordBreakChars(", ")
            .setExplicitChars("@#")
            .build();

    private static final String PERSON_BUCKET = "people";
    private static final String HASHTAG_BUCKET = "hashtags";

    private static final char PERSON_EXPLICIT_CHAR = '@';
    private static final char HASHTAG_EXPLICIT_CHAR = '#';

    private RichEditorView editor;

    private Person.PersonLoader people;
    private Hashtag.HashtagLoader hashtags;

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.mentions_and_hashtags);

        editor = findViewById(R.id.editor);

        editor.setTokenizer(new WordTokenizer(tokenizerConfig));
        editor.displayTextCounter(false);
        editor.setQueryTokenReceiver(this);
        editor.setHint(getResources().getString(R.string.type_person_or_hashtag));

        people = new Person.PersonLoader(getResources());
        hashtags = new Hashtag.HashtagLoader(getResources());
    }

    @Override
    public List<String> onQueryReceived(final @NonNull QueryToken queryToken) {
        final List<String> buckets = new ArrayList<>();
        final SuggestionsResultListener listener = editor;
        final Handler handler = new Handler(Looper.getMainLooper());

        if (queryToken.getExplicitChar() == PERSON_EXPLICIT_CHAR) {
            buckets.add(PERSON_BUCKET);
            handler.postDelayed(() -> {
                List<Person> suggestions = people.getSuggestions(queryToken);
                Collections.sort(suggestions, (p1, p2) -> p1.getFullName().compareToIgnoreCase(p2.getFullName()));
                listener.onReceiveSuggestionsResult(new SuggestionsResult(queryToken, suggestions),
                        PERSON_BUCKET);
            }, PERSON_DELAY);
        }

        if (queryToken.getExplicitChar() == HASHTAG_EXPLICIT_CHAR) {
            buckets.add(HASHTAG_BUCKET);
            handler.postDelayed(() -> {
                List<Hashtag> suggestions = hashtags.getSuggestions(queryToken);
                Collections.sort(suggestions, (h1, h2) -> h1.getName().compareToIgnoreCase(h2.getName()));
                listener.onReceiveSuggestionsResult(new SuggestionsResult(queryToken, suggestions),
                        HASHTAG_BUCKET);
            }, HASHTAG_DELAY);
        }

        return buckets;
    }
}
