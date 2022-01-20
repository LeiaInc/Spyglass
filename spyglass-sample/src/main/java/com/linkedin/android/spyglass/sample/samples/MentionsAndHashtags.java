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

import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.linkedin.android.spyglass.mentions.Mentionable;
import com.linkedin.android.spyglass.sample.R;
import com.linkedin.android.spyglass.sample.data.models.Hashtag;
import com.linkedin.android.spyglass.sample.data.models.Person;
import com.linkedin.android.spyglass.suggestions.SuggestionsResult;
import com.linkedin.android.spyglass.suggestions.impl.BasicSuggestionsListBuilder;
import com.linkedin.android.spyglass.suggestions.interfaces.Suggestible;
import com.linkedin.android.spyglass.suggestions.interfaces.SuggestionsResultListener;
import com.linkedin.android.spyglass.tokenization.QueryToken;
import com.linkedin.android.spyglass.tokenization.impl.WordTokenizer;
import com.linkedin.android.spyglass.tokenization.impl.WordTokenizerConfig;
import com.linkedin.android.spyglass.tokenization.interfaces.MentionClickReceiver;
import com.linkedin.android.spyglass.tokenization.interfaces.QueryTokenReceiver;
import com.linkedin.android.spyglass.ui.MentionsEditorView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MentionsAndHashtags extends AppCompatActivity
        implements QueryTokenReceiver, MentionClickReceiver {

    private static final int PERSON_DELAY = 1000;
    private static final int HASHTAG_DELAY = 1000;

    private final WordTokenizerConfig tokenizerConfig = new WordTokenizerConfig
            .Builder()
            .setThreshold(1)
            .setMaxNumKeywords(2)
            .setWordBreakChars(" ,.")
            .setExplicitChars("@#")
            .build();

    private static final String PERSON_BUCKET = "people";
    private static final String HASHTAG_BUCKET = "hashtags";

    private static final char PERSON_EXPLICIT_CHAR = '@';
    private static final char HASHTAG_EXPLICIT_CHAR = '#';

    private MentionsEditorView editor;

    private Person.PersonLoader people;
    private Hashtag.HashtagLoader hashtags;

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.mentions_and_hashtags);

        editor = findViewById(R.id.editor);

        editor.setTokenizer(new WordTokenizer(tokenizerConfig));
        editor.setQueryTokenReceiver(this);
        editor.setMentionClickReceiver(this);
        editor.setHint(getResources().getString(R.string.type_person_or_hashtag));
        editor.setSuggestionsListBuilder(new CustomSuggestionsListBuilder());

        people = new Person.PersonLoader(getResources());
        hashtags = new Hashtag.HashtagLoader(getResources());
    }

    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.mentions_and_hashtags_menu, menu);
        return true;
    }

    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.view) {
            editor.checkCurrentQuery();
            Intent intent = new Intent(this, MentionsAndHashtagsResult.class);
            intent.putExtra(MentionsAndHashtagsResult.EXTRA_MENTIONS_TEXT, editor.getPlainText().trim());
            intent.putParcelableArrayListExtra(MentionsAndHashtagsResult.EXTRA_MENTIONS_SPANS, editor.getMentionSpans());
            startActivity(intent);
            return true;
        } else {
            return false;
        }
    }

    @NonNull
    @Override
    public List<String> onQueryReceived(final @NonNull QueryToken queryToken) {
        final List<String> buckets = new ArrayList<>();
        final SuggestionsResultListener listener = editor;
        final Handler handler = new Handler(Looper.getMainLooper());

        if (queryToken.getExplicitChar() == PERSON_EXPLICIT_CHAR && queryToken.getKeywords().length() > 0) {
            buckets.add(PERSON_BUCKET);
            handler.postDelayed(() -> {
                List<Person> suggestions = people.getSuggestions(queryToken);
                Collections.sort(suggestions, (p1, p2) -> p1.getFullName().compareToIgnoreCase(p2.getFullName()));
                listener.onReceiveSuggestionsResult(new SuggestionsResult(queryToken, suggestions),
                        PERSON_BUCKET);
            }, PERSON_DELAY);
        }

        if (queryToken.getExplicitChar() == HASHTAG_EXPLICIT_CHAR && queryToken.getKeywords().length() > 0) {
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

    @Override
    public Mentionable getSuggestionFromQueryInstantly(@NonNull QueryToken queryToken) {
        String keywords = queryToken.getKeywords();
        if (keywords.isEmpty()) return null;

        if (queryToken.getExplicitChar() == PERSON_EXPLICIT_CHAR) {
            // insert person only if there is a dead sure suggestion
            return people.getExactSuggestion(queryToken);
        } else if (queryToken.getExplicitChar() == HASHTAG_EXPLICIT_CHAR) {
            return new Hashtag(keywords);
        }

        return null;
    }

    @Override
    public void onMentionClick(@NonNull Mentionable mention) {
        if (mention instanceof Person) {
            String message = "Click on person \"" + ((Person) mention).getFullName() + "\"";
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        } else if (mention instanceof Hashtag) {
            String message = "Click on hashtag \"" + ((Hashtag) mention).getName() + "\"";
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        }
    }

    private class CustomSuggestionsListBuilder extends BasicSuggestionsListBuilder {

        @NonNull
        @Override
        public View getView(@NonNull Suggestible suggestion,
                            @Nullable View convertView,
                            ViewGroup parent,
                            @NonNull Context context,
                            @NonNull LayoutInflater inflater,
                            @NonNull Resources resources) {

            View v = super.getView(suggestion, convertView, parent, context, inflater, resources);
            if (!(v instanceof TextView)) {
                return v;
            }

            // Color text depending on the type of mention
            TextView tv = (TextView) v;
            if (suggestion instanceof Person) {
                tv.setTextColor(getResources().getColor(R.color.person_color));
                tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
            } else if (suggestion instanceof Hashtag) {
                tv.setTextColor(getResources().getColor(R.color.hashtag_color));
                tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            }

            return tv;
        }
    }
}
