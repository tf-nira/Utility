package io.mosip.minioutility;

import io.minio.MinioClient;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.PutObjectArgs;
import io.minio.GetObjectArgs;

import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;

public class MinioUtilityApplication {
	public static void main(String[] args) {
        try {
            // Create a MinioClient
            MinioClient minioClient =
                    MinioClient.builder()
                            .endpoint("https://dcniraprdminiosvc01.nsis.nira.go.ug:9000") // your MinIO server
                            .credentials("jAibARRiqFf1130za08N", "1XG35dI1rdC7Wv6i6YgTz9wkVX0MlQ3yfgxQsoc3")
                            .build();

            String bucketName = "test1";
            String objectName = "10054100090000120250417133759_id";
            String localFile = "/home/sowmya/Downloads";
            long fileSize = Files.size(Paths.get(localFile));

            // Check if the bucket exists, create if not
            boolean found = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucketName).build());
            if (!found) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());
            }

            try (InputStream fileInputStream = new FileInputStream(localFile)) {
                // Upload a file
                minioClient.putObject(
                        PutObjectArgs.builder()
                                .bucket(bucketName)
                                .object(objectName)
                                .stream(fileInputStream, fileSize, -1)
                                .build()
                );

            }


            System.out.println("File uploaded successfully.");

            // Download the file
            minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .build()
            );
        } catch (Exception e) {
            System.out.println("Error occurred: " + e);
        }
    }
}
