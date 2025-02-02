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

package com.linkedin.android.spyglass.sample.data.models;

import android.content.res.Resources;
import android.os.Parcel;
import android.util.Log;

import androidx.annotation.NonNull;

import com.linkedin.android.spyglass.mentions.Mentionable;
import com.linkedin.android.spyglass.sample.R;
import com.linkedin.android.spyglass.sample.data.MentionsLoader;
import com.linkedin.android.spyglass.tokenization.QueryToken;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.List;

/**
 * Model representing a basic, mentionable city.
 */
public class Hashtag implements Mentionable {

    private final String mName;

    public Hashtag(String name) {
        mName = name;
    }

    public String getName() {
        return mName;
    }

    // --------------------------------------------------
    // Mentionable Implementation
    // --------------------------------------------------

    @NonNull
    @Override
    public String getTextForDisplayMode(MentionDisplayMode mode) {
        String text;
        switch (mode) {
            case FULL:
                text = mName;
                break;
            case PARTIAL:
            case NONE:
            default:
                text = "";
                break;
        }
        return "#" + text;
    }

    @NonNull
    @Override
    public MentionDeleteStyle getDeleteStyle() {
        return MentionDeleteStyle.FULL_DELETE;
    }

    @Override
    public int getSuggestibleId() {
        return mName.hashCode();
    }

    @Override
    public String getSuggestiblePrimaryText() {
        return "#" + mName;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(mName);
    }

    public Hashtag(Parcel in) {
        mName = in.readString();
    }

    public static final Creator<Hashtag> CREATOR
            = new Creator<Hashtag>() {
        public Hashtag createFromParcel(Parcel in) {
            return new Hashtag(in);
        }

        public Hashtag[] newArray(int size) {
            return new Hashtag[size];
        }
    };

    // --------------------------------------------------
    // CityLoader Class (loads cities from JSON file)
    // --------------------------------------------------

    public static class HashtagLoader extends MentionsLoader<Hashtag> {
        private static final String TAG = HashtagLoader.class.getSimpleName();

        public HashtagLoader(Resources res) {
            super(res, R.raw.hashtags);
        }

        @Override
        public Hashtag[] loadData(JSONArray arr) {
            Hashtag[] data = new Hashtag[arr.length()];
            try {
                for (int i = 0; i < arr.length(); i++) {
                    data[i] = new Hashtag(arr.getString(i));
                }
            } catch (Exception e) {
                Log.e(TAG, "Unhandled exception while parsing city JSONArray", e);
            }
            return data;
        }

        @Override
        public List<Hashtag> getSuggestions(QueryToken queryToken) {
            String prefix = queryToken.getTokenString().toLowerCase();
            List<Hashtag> suggestions = new ArrayList<>();
            if (mData != null) {
                for (Hashtag suggestion : mData) {
                    String name = suggestion.getSuggestiblePrimaryText().toLowerCase();
                    if (name.startsWith(prefix)) {
                        suggestions.add(suggestion);
                    }
                }
            }
            return suggestions;
        }
    }

}
