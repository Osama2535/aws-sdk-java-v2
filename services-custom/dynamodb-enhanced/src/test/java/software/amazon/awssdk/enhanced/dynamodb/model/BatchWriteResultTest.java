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

package software.amazon.awssdk.enhanced.dynamodb.model;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

import com.google.common.collect.ImmutableList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.assertj.core.api.Assertions;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.functionaltests.models.FakeItem;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.ConsumedCapacity;
import software.amazon.awssdk.services.dynamodb.model.DeleteRequest;
import software.amazon.awssdk.services.dynamodb.model.ItemCollectionMetrics;
import software.amazon.awssdk.services.dynamodb.model.PutRequest;
import software.amazon.awssdk.services.dynamodb.model.WriteRequest;
import software.amazon.awssdk.utils.ImmutableMap;


@RunWith(MockitoJUnitRunner.class)
public class BatchWriteResultTest {

    private static final String TABLE_NAME = "table-name";

    @Mock
    private DynamoDbClient mockDynamoDbClient;

    private DynamoDbEnhancedClient enhancedClient;
    private DynamoDbTable<FakeItem> fakeItemMappedTable;

    @Before
    public void setupMappedTables() {
        enhancedClient = DynamoDbEnhancedClient.builder().dynamoDbClient(mockDynamoDbClient).extensions().build();
        fakeItemMappedTable = enhancedClient.table(TABLE_NAME, FakeItem.getTableSchema());
    }

    @Test
    public void builder_minimal() {
        BatchWriteResult builtObject =
            BatchWriteResult.builder().unprocessedRequests(Collections.EMPTY_MAP).consumedCapacity(ImmutableList.of()).build();

        Assertions.assertThat(builtObject.itemCollectionMetrics()).isNull();
        Assertions.assertThat(builtObject.consumedCapacity()).isEmpty();
    }

    @Test
    public void builder_maximal() {
        Map<String, List<WriteRequest>> batchWriteResults = ImmutableMap.of("abcd", ImmutableList.of(
            WriteRequest.builder().putRequest(PutRequest.builder().build()).build(),
            WriteRequest.builder().deleteRequest(DeleteRequest.builder().build()).build()
        ));
        BatchWriteResult builtObject =
            BatchWriteResult.builder().unprocessedRequests(batchWriteResults).consumedCapacity(ImmutableList.of(
                ConsumedCapacity.builder().capacityUnits(0.5).build()
            ))
                .itemCollectionMetrics(ImmutableMap.of(
                    "abcd", ImmutableList.of(ItemCollectionMetrics.builder().build())
                )).build();

        Assertions.assertThat(builtObject.itemCollectionMetrics()).isNotEmpty();
        Assertions.assertThat(builtObject.consumedCapacity()).isNotEmpty();
    }
}