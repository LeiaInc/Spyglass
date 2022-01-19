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

package com.linkedin.android.spyglass.tokenization.interfaces;

import androidx.annotation.NonNull;

import com.linkedin.android.spyglass.suggestions.SuggestionsResult;
import com.linkedin.android.spyglass.suggestions.interfaces.SuggestionsResultListener;
import com.linkedin.android.spyglass.tokenization.QueryToken;

import java.util.List;

/**
 * Interface used to query an object with a {@link QueryToken}. The client is responsible for inserting a mention, even
 * if it is not found in the suggestions
 */
public interface AlwaysInsertQueryReceiver {

    /**
     * Called to the client, expecting the client to insert a mention from the query token
     *
     * @param queryToken the {@link QueryToken} to process
     */
    void onAlwaysInsertQueryReceived(final @NonNull QueryToken queryToken);
}
