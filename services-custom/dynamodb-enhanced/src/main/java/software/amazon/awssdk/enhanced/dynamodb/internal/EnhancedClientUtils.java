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

package software.amazon.awssdk.enhanced.dynamodb.internal;

import static software.amazon.awssdk.enhanced.dynamodb.mapper.StaticImmutableTableSchema.NESTED_OBJECT_UPDATE;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import software.amazon.awssdk.annotations.SdkInternalApi;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClientExtension;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.OperationContext;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.extensions.ReadModification;
import software.amazon.awssdk.enhanced.dynamodb.internal.extensions.DefaultDynamoDbExtensionContext;
import software.amazon.awssdk.enhanced.dynamodb.mapper.AttributeMapping;
import software.amazon.awssdk.enhanced.dynamodb.mapper.MappingConfiguration;
import software.amazon.awssdk.enhanced.dynamodb.model.Page;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ConsumedCapacity;

@SdkInternalApi
public final class EnhancedClientUtils {
    private static final Set<Character> SPECIAL_CHARACTERS = Stream.of(
        '*', '.', '-', '#', '+', ':', '/', '(', ')', ' ',
        '&', '<', '>', '?', '=', '!', '@', '%', '$', '|').collect(Collectors.toSet());
    private static final Pattern PATTERN = Pattern.compile(NESTED_OBJECT_UPDATE);

    private EnhancedClientUtils() {

    }

    /** There is a divergence in what constitutes an acceptable attribute name versus a token used in expression
     * names or values. Since the mapper translates one to the other, it is necessary to scrub out all these
     * 'illegal' characters before adding them to expression values or expression names.
     *
     * @param key A key that may contain non alpha-numeric characters acceptable to a DynamoDb attribute name.
     * @return A key that has all these characters scrubbed and overwritten with an underscore.
     */
    public static String cleanAttributeName(String key) {
        boolean somethingChanged = false;

        char[] chars = key.toCharArray();

        for (int i = 0; i < chars.length; ++i) {
            if (SPECIAL_CHARACTERS.contains(chars[i])) {
                chars[i] = '_';
                somethingChanged = true;
            }
        }

        return somethingChanged ? new String(chars) : key;
    }

    private static boolean isNestedAttribute(String key) {
        return key.contains(NESTED_OBJECT_UPDATE);
    }

    /**
     * Creates a key token to be used with an ExpressionNames map.
     */
    public static String keyRef(String key) {
        String cleanAttributeName = cleanAttributeName(key);
        cleanAttributeName = isNestedAttribute(cleanAttributeName) ?
                             PATTERN.matcher(cleanAttributeName).replaceAll(".#AMZN_MAPPED_")
                                                    : cleanAttributeName;
        return "#AMZN_MAPPED_" + cleanAttributeName;
    }

    /**
     * Creates a value token to be used with an ExpressionValues map.
     */
    public static String valueRef(String value) {
        String cleanAttributeName = cleanAttributeName(value);
        cleanAttributeName = isNestedAttribute(cleanAttributeName) ?
                             PATTERN.matcher(cleanAttributeName).replaceAll("_")
                                                                   : cleanAttributeName;
        return ":AMZN_MAPPED_" + cleanAttributeName;
    }

    public static <T> T readAndTransformSingleItem(Map<String, AttributeValue> itemMap,
                                            TableSchema<T> tableSchema,
                                            OperationContext operationContext,
                                            DynamoDbEnhancedClientExtension dynamoDbEnhancedClientExtension) {
        if (itemMap == null || itemMap.isEmpty()) {
            return null;
        }

        if (dynamoDbEnhancedClientExtension != null) {
            ReadModification readModification = dynamoDbEnhancedClientExtension.afterRead(
                DefaultDynamoDbExtensionContext.builder()
                                               .items(itemMap)
                                               .tableSchema(tableSchema)
                                               .operationContext(operationContext)
                                               .tableMetadata(tableSchema.tableMetadata())
                                               .build());
            if (readModification != null && readModification.transformedItem() != null) {
                return tableSchema.mapToItem(readModification.transformedItem());
            }
        }

        return tableSchema.mapToItem(itemMap);
    }

    public static <ResponseT, ItemT> Page<ItemT> readAndTransformPaginatedItems(
        ResponseT response,
        TableSchema<ItemT> tableSchema,
        OperationContext operationContext,
        DynamoDbEnhancedClientExtension dynamoDbEnhancedClientExtension,
        Function<ResponseT, List<Map<String, AttributeValue>>> getItems,
        Function<ResponseT, Map<String, AttributeValue>> getLastEvaluatedKey,
        Function<ResponseT, Integer> count,
        Function<ResponseT, Integer> scannedCount,
        Function<ResponseT, ConsumedCapacity> consumedCapacity) {

        List<ItemT> collect = getItems.apply(response)
                                      .stream()
                                      .map(itemMap -> readAndTransformSingleItem(itemMap,
                                                                                 tableSchema,
                                                                                 operationContext,
                                                                                 dynamoDbEnhancedClientExtension))
                                      .collect(Collectors.toList());

        Page.Builder<ItemT> pageBuilder = Page.builder(tableSchema.itemType().rawClass())
                                              .items(collect)
                                              .count(count.apply(response))
                                              .scannedCount(scannedCount.apply(response))
                                              .consumedCapacity(consumedCapacity.apply(response));

        if (getLastEvaluatedKey.apply(response) != null && !getLastEvaluatedKey.apply(response).isEmpty()) {
            pageBuilder.lastEvaluatedKey(getLastEvaluatedKey.apply(response));
        }
        return pageBuilder.build();
    }

    public static <T> Key createKeyFromItem(T item, TableSchema<T> tableSchema, String indexName) {
        String partitionKeyName = tableSchema.tableMetadata().indexPartitionKey(indexName);
        Optional<String> sortKeyName = tableSchema.tableMetadata().indexSortKey(indexName);
        AttributeValue partitionKeyValue = tableSchema.attributeValue(item, partitionKeyName);
        Optional<AttributeValue> sortKeyValue = sortKeyName.map(key -> tableSchema.attributeValue(item, key));

        return sortKeyValue.map(
            attributeValue -> Key.builder()
                                 .partitionValue(partitionKeyValue)
                                 .sortValue(attributeValue)
                                 .build())
                           .orElseGet(
                               () -> Key.builder()
                                        .partitionValue(partitionKeyValue).build());
    }

    public static Key createKeyFromMap(Map<String, AttributeValue> itemMap,
                                       TableSchema<?> tableSchema,
                                       String indexName) {
        String partitionKeyName = tableSchema.tableMetadata().indexPartitionKey(indexName);
        Optional<String> sortKeyName = tableSchema.tableMetadata().indexSortKey(indexName);
        AttributeValue partitionKeyValue = itemMap.get(partitionKeyName);
        Optional<AttributeValue> sortKeyValue = sortKeyName.map(itemMap::get);

        return sortKeyValue.map(
            attributeValue -> Key.builder()
                                 .partitionValue(partitionKeyValue)
                                 .sortValue(attributeValue)
                                 .build())
                           .orElseGet(
                               () -> Key.builder()
                                        .partitionValue(partitionKeyValue).build());
    }

    public static <T> List<T> getItemsFromSupplier(List<Supplier<T>> itemSupplierList) {
        if (itemSupplierList == null || itemSupplierList.isEmpty()) {
            return null;
        }
        return Collections.unmodifiableList(itemSupplierList.stream()
                                                            .map(Supplier::get)
                                                            .collect(Collectors.toList()));
    }

    /**
     * A helper method to test if an {@link AttributeValue} is a 'null' constant. This will not test if the
     * AttributeValue object is null itself, and in fact will throw a NullPointerException if you pass in null.
     * @param attributeValue An {@link AttributeValue} to test for null.
     * @return true if the supplied AttributeValue represents a null value, or false if it does not.
     */
    public static boolean isNullAttributeValue(AttributeValue attributeValue) {
        return attributeValue.nul() != null && attributeValue.nul();
    }

    public static MappingConfiguration getMappingConfiguration(boolean ignoreNulls, AttributeMapping attributeMapping) {
        return MappingConfiguration.builder()
                                   .ignoreNulls(ignoreNulls)
                                   .attributeMapping(attributeMapping)
                                   .build();
    }
}
