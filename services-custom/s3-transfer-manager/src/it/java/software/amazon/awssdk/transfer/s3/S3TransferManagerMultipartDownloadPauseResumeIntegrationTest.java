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

package software.amazon.awssdk.transfer.s3;

import static org.assertj.core.api.Assertions.assertThat;
import static software.amazon.awssdk.testutils.service.S3BucketUtils.temporaryBucketName;
import static software.amazon.awssdk.transfer.s3.SizeConstant.MB;

import java.io.File;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.retry.backoff.FixedDelayBackoffStrategy;
import software.amazon.awssdk.core.waiters.Waiter;
import software.amazon.awssdk.core.waiters.WaiterAcceptor;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.testutils.RandomTempFile;
import software.amazon.awssdk.transfer.s3.model.DownloadFileRequest;
import software.amazon.awssdk.transfer.s3.model.FileDownload;
import software.amazon.awssdk.transfer.s3.model.ResumableFileDownload;
import software.amazon.awssdk.transfer.s3.progress.TransferProgressSnapshot;

public class S3TransferManagerMultipartDownloadPauseResumeIntegrationTest extends S3IntegrationTestBase {
    private static final String BUCKET = temporaryBucketName(S3TransferManagerMultipartDownloadPauseResumeIntegrationTest.class);
    private static final String KEY = "key";

    private static final long OBJ_SIZE = 32 * MB; // 32mib for 4 parts of 8 mib
    private static File sourceFile;

    @BeforeAll
    public static void setup() throws Exception {
        System.out.println("CREATING BUCKET");
        createBucket(BUCKET);
        sourceFile = new RandomTempFile(OBJ_SIZE);

        // use async client for multipart upload (with default part size)
        System.out.println("UPLOADING TEST OBJECT");
        s3Async.putObject(PutObjectRequest.builder()
                                          .bucket(BUCKET)
                                          .key(KEY)
                                          .build(), sourceFile.toPath())
               .join();
    }

    @AfterAll
    public static void cleanup() {
        deleteBucketAndAllContents(BUCKET);
        sourceFile.delete();
    }

    @Test
    void pauseAndResume_shouldResumeDownload() {
        Path path = RandomTempFile.randomUncreatedFile().toPath();
        DownloadFileRequest request = DownloadFileRequest.builder()
                                                         .getObjectRequest(b -> b.bucket(BUCKET).key(KEY))
                                                         .destination(path)
                                                         .build();
        System.out.println("DOWNLOADING");
        FileDownload download = tmJava.downloadFile(request);

        // wait until we receive enough byte to stop somewhere between part 2 and 3, 18 Mib should do it
        waitUntilAmountTransferred(download, 18 * MB);
        System.out.println("PAUSING");
        ResumableFileDownload resumableFileDownload = download.pause();
        System.out.println("RESUMING");
        FileDownload resumed = tmJava.resumeDownloadFile(resumableFileDownload);
        resumed.completionFuture().join();
        assertThat(path.toFile()).hasSameBinaryContentAs(sourceFile);
    }

    @Test
    void pauseAndResume_whenAlreadyComplete_shouldHandleGracefully() {
        Path path = RandomTempFile.randomUncreatedFile().toPath();
        DownloadFileRequest request = DownloadFileRequest.builder()
                                                         .getObjectRequest(b -> b.bucket(BUCKET).key(KEY))
                                                         .destination(path)
                                                         .build();
        FileDownload download = tmJava.downloadFile(request);
        System.out.println("JOINING");
        download.completionFuture().join();
        System.out.println("PAUSING");
        ResumableFileDownload resume = download.pause();
        System.out.println("RESUMING");
        FileDownload resumedDownload = tmJava.resumeDownloadFile(resume);
        System.out.println("ASSERTING");
        assertThat(resumedDownload.completionFuture()).isCompleted();
        assertThat(path.toFile()).hasSameBinaryContentAs(sourceFile);
    }

    private void waitUntilAmountTransferred(FileDownload download, long amountTransferred) {
        Waiter<TransferProgressSnapshot> waiter =
            Waiter.builder(TransferProgressSnapshot.class)
                  .addAcceptor(WaiterAcceptor.successOnResponseAcceptor(r -> r.transferredBytes() > amountTransferred))
                  .addAcceptor(WaiterAcceptor.retryOnResponseAcceptor(r -> true))
                  .overrideConfiguration(o -> o.waitTimeout(Duration.ofMinutes(5))
                                               .maxAttempts(Integer.MAX_VALUE)
                                               .backoffStrategy(FixedDelayBackoffStrategy.create(Duration.ofMillis(100))))
                  .build();
        waiter.run(() -> download.progress().snapshot());
    }
}