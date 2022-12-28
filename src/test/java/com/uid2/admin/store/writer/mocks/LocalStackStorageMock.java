package com.uid2.admin.store.writer.mocks;

import com.amazonaws.HttpMethod;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.*;
import com.uid2.shared.cloud.CloudStorageException;
import com.uid2.shared.cloud.ICloudStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.net.URL;
import java.util.*;

// Most of the code here is copy-pasted from CloudStorageS3.java in Shared
// Ideally we should make it possible to inject an AmazonS3 into CloudStorage instead
public class LocalStackStorageMock implements ICloudStorage {
    private static final Logger LOGGER = LoggerFactory.getLogger(LocalStackStorageMock.class);
    private final AmazonS3 s3;
    public Map<String, String> localFileSystemMock = new HashMap<>();

    private long preSignedUrlExpiryInSeconds = 3600;
    private final String bucket = "unit-test-bucket";
    public LocalStackStorageMock(AmazonS3 s3) {
        this.s3 = s3;
        createTestBucket();
    }

    public void createTestBucket() {
        if (!s3.doesBucketExistV2(bucket)) {
            s3.createBucket(bucket);
        }
    }

    public void clearTestBucket() {
        for (S3ObjectSummary file : s3.listObjects(bucket, "").getObjectSummaries()){
            s3.deleteObject(bucket, file.getKey());
        };
    }

    public void save(byte[] content, String fullPath) {
        localFileSystemMock.put(fullPath, new String(content));
    }

    public byte[] load(String fullPath) {
        return localFileSystemMock.get(fullPath).getBytes();
    }
    @Override
    public void upload(String localPath, String cloudPath) throws CloudStorageException {
        try {
            this.s3.putObject(bucket, cloudPath, localFileSystemMock.get(localPath));
        } catch (Throwable t) {
            throw new CloudStorageException("s3 put error: " + t.getMessage(), t);
        }
    }

    @Override
    public void upload(InputStream input, String cloudPath) throws CloudStorageException {
        try {
            this.s3.putObject(bucket, cloudPath, input, null);
        } catch (Throwable t) {
            throw new CloudStorageException("s3 put error: " + t.getMessage(), t);
        }
    }

    @Override
    public InputStream download(String cloudPath) throws CloudStorageException {
        try {
            S3Object obj = this.s3.getObject(bucket, cloudPath);
            return obj.getObjectContent();
        } catch (Throwable t) {
            throw new CloudStorageException("s3 get error: " + t.getMessage(), t);
        }
    }

    @Override
    public void delete(String cloudPath) throws CloudStorageException {
        try {
            this.s3.deleteObject(bucket, cloudPath);
        } catch (Throwable t) {
            throw new CloudStorageException("s3 get error: " + t.getMessage(), t);
        }
    }

    @Override
    public void delete(Collection<String> cloudPaths) throws CloudStorageException {
        if (cloudPaths.size() == 0) return;
        if (cloudPaths.size() <= 1000) {
            deleteInternal(cloudPaths);
            return;
        }

        List<String> pathList = new ArrayList<>();
        int i = 0;
        final int len = cloudPaths.size();
        for (String p : cloudPaths) {
            pathList.add(p);
            ++i;
            if (pathList.size() == 1000 || i == len) {
                deleteInternal(pathList);
                pathList.clear();
            }
        }

        if (pathList.size() != 0) throw new IllegalStateException();
    }

    @Override
    public List<String> list(String prefix) throws CloudStorageException {
        try {
            ListObjectsV2Request req = new ListObjectsV2Request()
                    .withBucketName(bucket)
                    .withPrefix(prefix);
            ListObjectsV2Result result = null;
            List<S3ObjectSummary> objects = null;

            int reqCount = 0;
            List<String> s3Paths = new ArrayList<>();
            do {
                result = this.s3.listObjectsV2(req);
                objects = result.getObjectSummaries();

                LOGGER.trace("s3 listobjectv2 request for " + prefix + " " + reqCount++ + ", returned keycount " + result.getKeyCount());
                if (objects.size() > 0) {
                    LOGGER.trace("--> 1st key = " + objects.get(0).getKey());
                }

                for (S3ObjectSummary os : objects) {
                    s3Paths.add(os.getKey());
                }

                if (result.isTruncated()) {
                    req.setContinuationToken(result.getNextContinuationToken());
                    LOGGER.trace("--> truncated, continuationtoken: " + req.getContinuationToken());
                }
            } while (result.isTruncated());
            return s3Paths;
        } catch (Throwable t) {
            throw new CloudStorageException("s3 list error: " + t.getMessage(), t);
        }
    }

    @Override
    public URL preSignUrl(String cloudPath) throws CloudStorageException {
        try {
            // Set the presigned URL to expire after one hour.
            java.util.Date expiration = new java.util.Date();
            long expTimeMillis = expiration.getTime();
            expTimeMillis += preSignedUrlExpiryInSeconds * 1000;
            expiration.setTime(expTimeMillis);

            // Generate the presigned URL.
            GeneratePresignedUrlRequest generatePresignedUrlRequest =
                    new GeneratePresignedUrlRequest(this.bucket, cloudPath)
                            .withMethod(HttpMethod.GET)
                            .withExpiration(expiration);
            URL url = this.s3.generatePresignedUrl(generatePresignedUrlRequest);
            return url;
        } catch (Throwable t) {
            throw new CloudStorageException("s3 preSignUrl error: " + t.getMessage(), t);
        }
    }

    @Override
    public void setPreSignedUrlExpiry(long expiryInSeconds) {
        this.preSignedUrlExpiryInSeconds = expiryInSeconds;
    }

    @Override
    public String mask(String cloudPath) {
        return cloudPath;
    }

    private void deleteInternal(Collection<String> cloudPaths) throws CloudStorageException {
        List<DeleteObjectsRequest.KeyVersion> keys = new ArrayList<>();
        for (String p : cloudPaths) {
            keys.add(new DeleteObjectsRequest.KeyVersion(p));
        }
        DeleteObjectsRequest dor = new DeleteObjectsRequest(bucket)
                .withKeys(keys)
                .withQuiet(false);
        try {
            this.s3.deleteObjects(dor);
        } catch (Throwable t) {
            throw new CloudStorageException("s3 get error: " + t.getMessage(), t);
        }
    }
}