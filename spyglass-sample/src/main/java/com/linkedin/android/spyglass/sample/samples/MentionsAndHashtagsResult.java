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
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.linkedin.android.spyglass.mentions.MentionSpan;
import com.linkedin.android.spyglass.mentions.Mentionable;
import com.linkedin.android.spyglass.sample.R;
import com.linkedin.android.spyglass.sample.data.models.Hashtag;
import com.linkedin.android.spyglass.sample.data.models.Person;
import com.linkedin.android.spyglass.tokenization.interfaces.MentionClickReceiver;
import com.linkedin.android.spyglass.ui.MentionsTextView;

import java.util.ArrayList;

public class MentionsAndHashtagsResult extends AppCompatActivity
        implements MentionClickReceiver {

    public static final String EXTRA_MENTIONS_TEXT = "extra_mentions_text";
    public static final String EXTRA_MENTIONS_SPANS = "extra_mentions_spans";

    private MentionsTextView viewer;

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.mentions_and_hashtags_result);

        viewer = findViewById(R.id.viewer);
        viewer.setMentionClickReceiver(this);

        String text = getIntent().getStringExtra(EXTRA_MENTIONS_TEXT);
        ArrayList<MentionSpan> mentionSpans = getIntent().getParcelableArrayListExtra(EXTRA_MENTIONS_SPANS);

        viewer.setTextWithMentions(text, mentionSpans);
    }

    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.mentions_and_hashtags_result_menu, menu);
        return true;
    }

    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.ok) {
            finish();
            return true;
        } else {
            return false;
        }
    }

    @Override
    public void onMentionClick(@NonNull Mentionable mention) {
        Log.e("AAA", "Click");
        if (mention instanceof Person) {
            String message = "Click on person \"" + ((Person) mention).getFullName() + "\"";
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        } else if (mention instanceof Hashtag) {
            String message = "Click on hashtag \"" + ((Hashtag) mention).getName() + "\"";
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        }
    }
}
