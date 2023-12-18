package com.goojeans.idemainserver.repository.fileprocessing;

import com.goojeans.idemainserver.domain.entity.RunCode;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.file.Paths;
import java.util.UUID;

@Repository
@Slf4j
@RequiredArgsConstructor
public class FileProcessingRepositoryImpl implements FileProcessRepository{

    private final EntityManager em;
    private final S3Client s3;

    @Value("${BUCKET_NAME}")
    private String bucketName;

    @Override
    public File findSourceCode(String filePath) {
        // use uuid for unique file name to make it safe environment for many requests
        String fileName = "temp_" + UUID.randomUUID();
        File file = new File(fileName);
        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(filePath)
                .build();

        try {
            // find files on bucket and write on local file
            ResponseBytes<GetObjectResponse> getObject = s3.getObjectAsBytes(getObjectRequest);
            byte[] data = getObject.asByteArray();
            OutputStream os = new FileOutputStream(file);
            os.write(data);
            os.close();

        } catch (Exception e) {
            log.error(e.getMessage());
        }
        return file;
    }

    @Override
    public String saveFile(String filePath, File sourceCode) {
        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(filePath)
                .build();
        try {
            s3.putObject(putObjectRequest, RequestBody.fromFile(Paths.get(sourceCode.getAbsolutePath())));

        } catch (Exception e) {
            log.error(e.getMessage());
            return "fail";
        }
        return "success";
    }

    @Override
    public String deleteFile(String filePath) {
        // create delete object request
        DeleteObjectRequest deleteObjectRequest = DeleteObjectRequest.builder()
                .bucket(bucketName)
                .key(filePath)
                .build();
        try {
            s3.deleteObject(deleteObjectRequest);
        } catch (Exception e) {
            // if exception occurs
            log.error(e.getMessage());
            return "fail";
        }
        return "success";
    }

    @Override
    public String modifyFilePath(String beforeFilePath, String afterFilePath) {

        CopyObjectRequest copyReqeust = CopyObjectRequest.builder()
                .sourceBucket(bucketName)
                .sourceKey(beforeFilePath)
                .destinationBucket(bucketName)
                .destinationKey(afterFilePath)
                .build();
        try {
            s3.copyObject(copyReqeust);
        } catch (Exception e) {
            log.error(e.getMessage());
            return "fail";
        }

        DeleteObjectRequest deleteObjectRequest = DeleteObjectRequest.builder()
                .bucket(bucketName)
                .key(beforeFilePath)
                .build();
        try {
            s3.deleteObject(deleteObjectRequest);
        } catch (Exception e) {
            log.error(e.getMessage());
            return "fail";
        }
        return "success";
    }

    @Override
    public RunCode saveMetaData(RunCode runCode) {
        RunCode findCode = em.find(RunCode.class, runCode);
        if (findCode == null) {
            em.persist(runCode);
            return runCode;
        }
        findCode.setRunResult(runCode.getRunResult());
        findCode.setSourceUrl(runCode.getSourceUrl());
        findCode.setSolvedStatus(runCode.getSolvedStatus());

        return findCode;
    }

    @Override
    public RunCode getMetaData(String filePath) {
        return em.find(RunCode.class, filePath);
    }
}
