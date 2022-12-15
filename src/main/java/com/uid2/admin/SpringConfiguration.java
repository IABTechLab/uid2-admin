package com.uid2.admin;

import com.uid2.shared.cloud.CloudStorageS3;
import com.uid2.shared.cloud.ICloudStorage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SpringConfiguration {

    @Bean
    public int portOffset(@Value("${port_offset:0}") int portOffset) {
        return portOffset;
    }

    @Bean
    public ICloudStorage cloudStorage(
            @Value("${aws.access_key_id}") String awsAccessKeyId,
            @Value("${aws.secret_access_key}") String awsSecretAccessKey,
            @Value("${aws.region}") String awsRegion,
            @Value("${aws.s3.bucket}") String s3Bucket,
            @Value("${aws.s3.endpoint:}") String s3Endpoint) {
        return new CloudStorageS3(awsAccessKeyId, awsSecretAccessKey, awsRegion, s3Bucket, s3Endpoint);
    }

}
