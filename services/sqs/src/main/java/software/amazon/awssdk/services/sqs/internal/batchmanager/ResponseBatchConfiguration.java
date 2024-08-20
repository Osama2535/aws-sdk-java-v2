/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *  http://aws.amazon.com/apache2.0
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package software.amazon.awssdk.services.sqs.internal.batchmanager;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import software.amazon.awssdk.annotations.SdkInternalApi;
import software.amazon.awssdk.services.sqs.batchmanager.BatchOverrideConfiguration;
import software.amazon.awssdk.services.sqs.model.MessageSystemAttributeName;

@SdkInternalApi
public final class ResponseBatchConfiguration {

    public static final boolean LONG_POLL_DEFAULT = true;
    public static final Duration VISIBILITY_TIMEOUT_SECONDS_DEFAULT = null;
    public static final Duration LONG_POLL_WAIT_TIMEOUT_DEFAULT = Duration.ofSeconds(20);
    public static final Duration MIN_RECEIVE_WAIT_TIME_MS_DEFAULT = Duration.ofMillis(300);
    public static final List<String> RECEIVE_MESSAGE_ATTRIBUTE_NAMES_DEFAULT = Collections.emptyList();
    public static final List<MessageSystemAttributeName> MESSAGE_SYSTEM_ATTRIBUTE_NAMES_DEFAULT = Collections.emptyList();
    public static final boolean ADAPTIVE_PREFETCHING_DEFAULT = false;
    public static final int MAX_BATCH_ITEMS_DEFAULT = 10;
    public static final int MAX_INFLIGHT_RECEIVE_BATCHES_DEFAULT = 10;
    public static final int MAX_DONE_RECEIVE_BATCHES_DEFAULT = 10;

    private final Duration visibilityTimeout;
    private final Duration longPollWaitTimeout;
    private final Duration minReceiveWaitTime;
    private final List<MessageSystemAttributeName> messageSystemAttributeValues;
    private final List<String> receiveMessageAttributeNames;
    private final Boolean adaptivePrefetching;
    private final Integer maxBatchItems;
    private final Integer maxInflightReceiveBatches;
    private final Integer maxDoneReceiveBatches;

    public ResponseBatchConfiguration(BatchOverrideConfiguration overrideConfiguration) {
        this.visibilityTimeout = Optional.ofNullable(overrideConfiguration)
                                         .map(BatchOverrideConfiguration::visibilityTimeout)
                                         .orElse(VISIBILITY_TIMEOUT_SECONDS_DEFAULT);

        this.longPollWaitTimeout = Optional.ofNullable(overrideConfiguration)
                                           .map(BatchOverrideConfiguration::longPollWaitTimeout)
                                           .orElse(LONG_POLL_WAIT_TIMEOUT_DEFAULT);

        this.minReceiveWaitTime = Optional.ofNullable(overrideConfiguration)
                                          .map(BatchOverrideConfiguration::minReceiveWaitTime)
                                          .orElse(MIN_RECEIVE_WAIT_TIME_MS_DEFAULT);

        this.messageSystemAttributeValues = Optional.ofNullable(overrideConfiguration)
                                                    .map(BatchOverrideConfiguration::messageSystemAttributeName)
                                                    .filter(list -> !list.isEmpty())
                                                    .orElse(MESSAGE_SYSTEM_ATTRIBUTE_NAMES_DEFAULT);

        this.receiveMessageAttributeNames = Optional.ofNullable(overrideConfiguration)
                                                    .map(BatchOverrideConfiguration::receiveMessageAttributeNames)
                                                    .filter(list -> !list.isEmpty())
                                                    .orElse(RECEIVE_MESSAGE_ATTRIBUTE_NAMES_DEFAULT);

        this.adaptivePrefetching = Optional.ofNullable(overrideConfiguration)
                                           .map(BatchOverrideConfiguration::adaptivePrefetching)
                                           .orElse(ADAPTIVE_PREFETCHING_DEFAULT);

        this.maxBatchItems = Optional.ofNullable(overrideConfiguration)
                                     .map(BatchOverrideConfiguration::maxBatchItems)
                                     .orElse(MAX_BATCH_ITEMS_DEFAULT);


        this.maxInflightReceiveBatches = Optional.ofNullable(overrideConfiguration)
                                                 .map(BatchOverrideConfiguration::maxInflightReceiveBatches)
                                                 .orElse(MAX_INFLIGHT_RECEIVE_BATCHES_DEFAULT);

        this.maxDoneReceiveBatches = Optional.ofNullable(overrideConfiguration)
                                             .map(BatchOverrideConfiguration::maxDoneReceiveBatches)
                                             .orElse(MAX_DONE_RECEIVE_BATCHES_DEFAULT);
    }


    public Duration visibilityTimeout() {
        return visibilityTimeout;
    }

    public Duration longPollWaitTimeout() {
        return longPollWaitTimeout;
    }

    public Duration minReceiveWaitTime() {
        return minReceiveWaitTime;
    }

    public List<MessageSystemAttributeName> messageSystemAttributeNames() {
        return Collections.unmodifiableList(messageSystemAttributeValues);
    }

    public List<String> receiveMessageAttributeNames() {
        return Collections.unmodifiableList(receiveMessageAttributeNames);
    }

    public boolean adaptivePrefetching() {
        return adaptivePrefetching;
    }

    public int maxBatchItems() {
        return maxBatchItems;
    }

    public int maxInflightReceiveBatches() {
        return maxInflightReceiveBatches;
    }

    public int maxDoneReceiveBatches() {
        return maxDoneReceiveBatches;
    }
}