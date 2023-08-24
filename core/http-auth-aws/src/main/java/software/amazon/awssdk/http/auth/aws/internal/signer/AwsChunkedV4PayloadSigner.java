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

package software.amazon.awssdk.http.auth.aws.internal.signer;

import static software.amazon.awssdk.http.auth.aws.util.SignerUtils.hash;
import static software.amazon.awssdk.utils.BinaryUtils.toHex;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import org.reactivestreams.Publisher;
import software.amazon.awssdk.annotations.SdkInternalApi;
import software.amazon.awssdk.http.ContentStreamProvider;
import software.amazon.awssdk.http.Header;
import software.amazon.awssdk.http.SdkHttpRequest;
import software.amazon.awssdk.http.auth.aws.internal.chunkedencoding.ChunkedEncodedInputStream;
import software.amazon.awssdk.http.auth.aws.signer.CredentialScope;
import software.amazon.awssdk.http.auth.aws.signer.V4Context;
import software.amazon.awssdk.http.auth.aws.signer.V4PayloadSigner;
import software.amazon.awssdk.http.auth.aws.util.SignerConstant;
import software.amazon.awssdk.utils.Pair;
import software.amazon.awssdk.utils.StringInputStream;

/**
 * A default implementation of a payload signer that is a no-op, since payloads are most commonly unsigned.
 */
@SdkInternalApi
public class AwsChunkedV4PayloadSigner implements V4PayloadSigner {

    private final CredentialScope credentialScope;
    private final int chunkSize;

    public AwsChunkedV4PayloadSigner(CredentialScope credentialScope, int chunkSize) {
        this.credentialScope = credentialScope;
        this.chunkSize = chunkSize;
    }

    /**
     * Move `Content-Length` to `x-amz-decoded-content-length` if not already present. If neither header is present,
     * an exception is thrown.
     */
    private static void moveContentLength(SdkHttpRequest.Builder request) {
        if (!request.firstMatchingHeader("x-amz-decoded-content-length").isPresent()) {
            // if the decoded length isn't present, content-length must be there
            String contentLength = request
                .firstMatchingHeader(Header.CONTENT_LENGTH)
                .orElseThrow(() -> new IllegalArgumentException(Header.CONTENT_LENGTH + " must be specified!"));

            request.putHeader("x-amz-decoded-content-length", contentLength)
                   .removeHeader(Header.CONTENT_LENGTH);
        } else {
            // decoded header is already there, so remove content-length just to be sure it's gone
            request.removeHeader(Header.CONTENT_LENGTH);
        }
    }

    @Override
    public ContentStreamProvider sign(ContentStreamProvider payload, V4Context v4Context) {
        SdkHttpRequest.Builder request = v4Context.getSignedRequest();
        moveContentLength(request);

        String checksum = request.firstMatchingHeader("x-amz-content-sha256").orElseThrow(
            () -> new IllegalArgumentException("x-amz-content-sha256 must be set!")
        );

        InputStream inputStream = payload != null ? payload.newStream() : new StringInputStream("");
        ChunkedEncodedInputStream.Builder chunkedEncodedInputStreamBuilder = ChunkedEncodedInputStream
            .builder()
            .inputStream(inputStream)
            .chunkSize(chunkSize)
            .header(chunk -> Integer.toHexString(chunk.length).getBytes(StandardCharsets.UTF_8));

        switch (checksum) {
            case "STREAMING-AWS4-HMAC-SHA256-PAYLOAD": {
                RollingSigner rollingSigner = new RollingSigner(v4Context.getSigningKey(), v4Context.getSignature());
                setupSigExt(chunkedEncodedInputStreamBuilder, rollingSigner);
                break;
            }
            case "STREAMING-UNSIGNED-PAYLOAD-TRAILER":
                setupChecksumTrailer(chunkedEncodedInputStreamBuilder);
                break;
            case "STREAMING-AWS4-HMAC-SHA256-PAYLOAD-TRAILER": {
                RollingSigner rollingSigner = new RollingSigner(v4Context.getSigningKey(), v4Context.getSignature());
                setupSigExt(chunkedEncodedInputStreamBuilder, rollingSigner);
                setupSigTrailer(chunkedEncodedInputStreamBuilder, rollingSigner);
                setupChecksumTrailer(chunkedEncodedInputStreamBuilder);
                break;
            }
            default:
                throw new UnsupportedOperationException();
        }

        return chunkedEncodedInputStreamBuilder::build;
    }

    @Override
    public Publisher<ByteBuffer> signAsync(Publisher<ByteBuffer> payload, V4Context v4Context) {
        throw new UnsupportedOperationException();
    }

    private void setupSigExt(ChunkedEncodedInputStream.Builder builder, RollingSigner rollingSigner) {
        builder.addExtension(
            chunk -> Pair.of(
                "chunk-signature".getBytes(StandardCharsets.UTF_8),
                rollingSigner.sign(
                    previousSignature ->
                        "AWS4-HMAC-SHA256-PAYLOAD" + SignerConstant.LINE_SEPARATOR +
                        credentialScope.getDatetime() + SignerConstant.LINE_SEPARATOR +
                        credentialScope.scope() + SignerConstant.LINE_SEPARATOR +
                        previousSignature + SignerConstant.LINE_SEPARATOR +
                        toHex(hash("")) + SignerConstant.LINE_SEPARATOR +
                        toHex(hash(chunk))
                ).getBytes(StandardCharsets.UTF_8)
            )
        );
    }

    private void setupSigTrailer(ChunkedEncodedInputStream.Builder builder, RollingSigner rollingSigner) {
        builder.addTrailer(
            chunk -> Pair.of(
                "x-amz-trailer-signature".getBytes(StandardCharsets.UTF_8),
                rollingSigner.sign(
                    previousSignature ->
                        "AWS4-HMAC-SHA256-TRAILER" + SignerConstant.LINE_SEPARATOR +
                        credentialScope.getDatetime() + SignerConstant.LINE_SEPARATOR +
                        credentialScope.scope() + SignerConstant.LINE_SEPARATOR +
                        previousSignature + SignerConstant.LINE_SEPARATOR +
                        toHex(hash(chunk))
                ).getBytes(StandardCharsets.UTF_8)
            )
        );
    }

    private void setupChecksumTrailer(ChunkedEncodedInputStream.Builder builder) {
        // TODO: Set up checksumming of chunks and add as a trailer
    }
}