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

package software.amazon.awssdk.migration.internal.utils;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;
import software.amazon.awssdk.annotations.SdkInternalApi;
import software.amazon.awssdk.utils.ImmutableMap;

/**
 * Type creation and checking utilities.
 */
@SdkInternalApi
public final class SdkTypeUtils {
    /**
     * V2 core classes with a static factory method
     */
    public static final Map<String, Integer> V2_CORE_CLASSES_WITH_STATIC_FACTORY =
        ImmutableMap.<String, Integer>builder()
                    .put("software.amazon.awssdk.auth.credentials.EnvironmentVariableCredentialsProvider", 0)
                    .put("software.amazon.awssdk.auth.credentials.InstanceProfileCredentialsProvider", 0)
                    .put("software.amazon.awssdk.auth.credentials.AwsBasicCredentials", 2)
                    .put("software.amazon.awssdk.auth.credentials.AwsSessionCredentials", 3)
                    .put("software.amazon.awssdk.auth.credentials.StaticCredentialsProvider", 1)
                    .build();

    private static final Pattern V1_SERVICE_CLASS_PATTERN =
        Pattern.compile("com\\.amazonaws\\.services\\.[a-zA-Z0-9]+\\.[a-zA-Z0-9]+");
    private static final Pattern V1_SERVICE_MODEL_CLASS_PATTERN =
        Pattern.compile("com\\.amazonaws\\.services\\.[a-zA-Z0-9]+\\.model\\.[a-zA-Z0-9]+");

    private static final Pattern V1_SERVICE_CLIENT_CLASS_PATTERN =
        Pattern.compile("com\\.amazonaws\\.services\\.[a-zA-Z0-9]+\\.[a-zA-Z0-9]+");
    private static final Pattern V2_MODEL_BUILDER_PATTERN =
        Pattern.compile("software\\.amazon\\.awssdk\\.services\\.[a-zA-Z0-9]+\\.model\\.[a-zA-Z0-9]+\\.Builder");
    private static final Pattern V2_MODEL_CLASS_PATTERN = Pattern.compile(
        "software\\.amazon\\.awssdk\\.services\\.[a-zA-Z0-9]+\\.model\\..[a-zA-Z0-9]+");
    private static final Pattern V2_CLIENT_CLASS_PATTERN = Pattern.compile(
        "software\\.amazon\\.awssdk\\.services\\.[a-zA-Z0-9]+\\.[a-zA-Z0-9]+");

    /**
     * V2 core classes with a builder
     */
    private static final Set<String> V2_CORE_CLASSES_WITH_BUILDER =
        new HashSet<>(Arrays.asList("software.amazon.awssdk.core.client.ClientOverrideConfiguration",
                                    "software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider",
                                    "software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider",
                                    "software.amazon.awssdk.auth.credentials.ContainerCredentialsProvider",
                                    "software.amazon.awssdk.auth.credentials.InstanceProfileCredentialsProvider",
                                    "software.amazon.awssdk.services.sts.auth.StsAssumeRoleCredentialsProvider",
                                    "software.amazon.awssdk.services.sts.auth.StsGetSessionTokenCredentialsProvider",
                                    "software.amazon.awssdk.services.sts.auth.StsAssumeRoleWithWebIdentityCredentialsProvider",
                                    "software.amazon.awssdk.auth.credentials.ProcessCredentialsProvider"));

    private static final Pattern V2_CLIENT_BUILDER_PATTERN = Pattern.compile(
        "software\\.amazon\\.awssdk\\.services\\.[a-zA-Z0-9]+\\.[a-zA-Z0-9]+Builder");

    private SdkTypeUtils() {
    }

    public static boolean isV1Class(JavaType type) {
        return type != null && type.isAssignableFrom(V1_SERVICE_CLASS_PATTERN);
    }

    public static boolean isV1ModelClass(JavaType type) {
        return type != null
                && type instanceof JavaType.FullyQualified
                && type.isAssignableFrom(V1_SERVICE_MODEL_CLASS_PATTERN);
    }

    public static boolean isV1ClientClass(JavaType type) {
        return type != null
               && type instanceof JavaType.FullyQualified
               && type.isAssignableFrom(V1_SERVICE_CLIENT_CLASS_PATTERN);
    }

    public static boolean isV2ModelBuilder(JavaType type) {
        return type != null
                && type.isAssignableFrom(V2_MODEL_BUILDER_PATTERN);
    }

    public static boolean isV2ModelClass(JavaType type) {
        return type != null
                && type.isAssignableFrom(V2_MODEL_CLASS_PATTERN);
    }

    public static boolean isV2ClientClass(JavaType type) {
        return type != null
               && type.isAssignableFrom(V2_CLIENT_CLASS_PATTERN);
    }

    public static boolean isV2ClientBuilder(JavaType type) {
        return type != null
               && type.isAssignableFrom(V2_CLIENT_BUILDER_PATTERN);
    }

    public static boolean isEligibleToConvertToBuilder(JavaType.FullyQualified type) {
        return isV2ModelClass(type) || isV2ClientClass(type) || isV2CoreClassesWithBuilder(type.getFullyQualifiedName());
    }

    public static boolean isEligibleToConvertToStaticFactory(JavaType.FullyQualified type) {
        return type != null && V2_CORE_CLASSES_WITH_STATIC_FACTORY.containsKey(type.getFullyQualifiedName());
    }

    private static boolean isV2CoreClassesWithBuilder(String fqcn) {
        return V2_CORE_CLASSES_WITH_BUILDER.contains(fqcn);
    }

    public static JavaType.FullyQualified v2Builder(JavaType.FullyQualified type) {
        if (!isEligibleToConvertToBuilder(type)) {
            throw new IllegalArgumentException(String.format("%s cannot be converted to builder", type));
        }
        String fqcn;
        if (isV2ModelClass(type)) {
            fqcn = String.format("%s.%s", type.getFullyQualifiedName(), "Builder");
        } else {
            fqcn = String.format("%s%s", type.getFullyQualifiedName(), "Builder");
        }
        
        return TypeUtils.asFullyQualified(JavaType.buildType(fqcn));
    }
}