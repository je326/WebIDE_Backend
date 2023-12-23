package com.goojeans.idemainserver.service.fileprocessing;

import com.goojeans.idemainserver.domain.dto.request.FileRequests.*;
import com.goojeans.idemainserver.domain.dto.response.FileResponses.*;
import com.goojeans.idemainserver.domain.entity.RunCode;
import com.goojeans.idemainserver.repository.fileprocessing.FileProcessRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
@Transactional
@RequiredArgsConstructor
public class FileProcessingServiceImpl implements FileProcessService{

    private final FileProcessRepository repository;
    private final RestTemplate restTemplate = new RestTemplate();

    public static String requestUrl = "http://run:8080";

    @Override
    public FileProcessResponse<SourceCodeResponse> findSourceCode(SourceCodeRequest request) {
        String awsKeyPath = request.getAlgorithmId() + "/" + request.getUserId() + "/" + request.getSourceCodePath();
        String sourceCode;
        File file = null;

        try {
            file = repository.findFile(awsKeyPath);
            log.info("check file={}", file);
            sourceCode = new String(Files.readAllBytes(Paths.get(file.getPath())));
        } catch (Exception e) {
            return new FileProcessResponse<>(6000, null, e.getMessage());
        } finally {
            file.delete();
        }
        SourceCodeResponse data = new SourceCodeResponse(sourceCode);

        return new FileProcessResponse<>(200, List.of(data), null);
    }

    @Override
    public FileProcessResponse<AlgorithmResponse> findAlgoText(Long algorithmId) {
        String awsKeyPath = algorithmId + "/algorithm.txt";
        String algorithmText;
        File file = null;

        try {
            file = repository.findFile(awsKeyPath);
            algorithmText = new String(Files.readAllBytes(Paths.get(file.getPath())));
        } catch (Exception e) {
            return new FileProcessResponse<>(6000, null, e.getMessage());
        } finally {
            file.delete();
        }

        AlgorithmResponse data = new AlgorithmResponse(algorithmText);

        return new FileProcessResponse<>(200, List.of(data), null);
    }

    @Override
    public FileProcessResponse<ExecuteResponse> executeAndSaveCode(ExecuteRequest request) {
        String awsKeyPath = request.getAlgorithmId() + "/" + request.getUserId() + "/" + request.getFilePathSuffix();

        RestExecuteRequest executeRequest = RestExecuteRequest.builder()
                .s3Url(awsKeyPath)
                .algorithmId(request.getAlgorithmId())
                .testCase(request.getTestCase())
                .extension(request.getFileExtension())
                .build();

        FileProcessResponse<ExecuteResponse> response;
        File uploadFile = null;

        try {
            uploadFile = getFile(request.getSourceCode());

            response = restPost(requestUrl + "/execute", executeRequest, ExecuteResponse.class);

            if (response.getStatus() != 200) {
                throw new RuntimeException(response.getError());
            }

            repository.saveFile(awsKeyPath, uploadFile);

        } catch (Exception e) {
            return new FileProcessResponse<>(6000, null, e.getMessage());
        } finally {
            uploadFile.delete();
        }

        return response;
    }


    @Override
    public FileProcessResponse<SubmitResponse> submitAndSaveCode(SubmitRequest request) {
        String awsKeyPath = request.getAlgorithmId() + "/" + request.getUserId() + "/" + request.getFilePathSuffix();

        // not edited and not newly created file
        if (!request.getEdited() && !request.getSourceCode().isEmpty()) {
            RunCode metaData = repository.getMetaData(awsKeyPath).stream()
                    .findAny()
                    .orElseThrow();

            return new FileProcessResponse<>(200, List.of(new SubmitResponse(metaData.getSubmitResult())), null);
        }

        RestSubmitRequest submitRequest = RestSubmitRequest.builder()
                .s3Url(awsKeyPath)
                .algorithmId(request.getAlgorithmId())
                .extension(request.getFileExtension())
                .build();
        FileProcessResponse<SubmitResponse> response;
        File uploadFile = null;

        try {
            uploadFile = getFile(request.getSourceCode());

            response = restPost(requestUrl + "/submit", submitRequest, SubmitResponse.class);

            if (response.getStatus() != 200) {
                throw new RuntimeException(response.getError());
            }

            //TODO: add member_solved feature for submit


            RunCode updateMetaData = RunCode.builder()
                    .submitResult(response.getData().get(0).getResult())
                    .sourceUrl(awsKeyPath)
                    .build();

            repository.saveMetaData(updateMetaData);
            repository.saveFile(awsKeyPath, uploadFile);

        } catch (Exception e) {
            log.error(e.getMessage());
            return new FileProcessResponse<>(6000, null, e.getMessage());
        } finally {
            uploadFile.delete();
        }

        return response;
    }


    @Override
    public FileProcessResponse<FileTreeResponse> modifyFileStructure(ModifyPathRequest request) {
        String beforeKey = request.getAlgorithmId() + "/" + request.getUserId() + "/" + request.getBeforePath();
        String afterKey = request.getAlgorithmId() + "/" + request.getUserId() + "/" + request.getAfterPath();
        String prefix = request.getAlgorithmId() + "/" + request.getUserId();
        String message = null;

        try {
            message = repository.modifyFilePath(beforeKey, afterKey);
            if (message.equals("fail")) {
                throw new RuntimeException("cannot modify file");
            }
            List<FileTreeResponse> fileTrees = repository.findFileTrees(prefix);
            return new FileProcessResponse<>(200, fileTrees, null);
        } catch (Exception e) {
            log.error(e.getMessage());
            return new FileProcessResponse<>(6000, null, e.getMessage());
        }
    }

    @Override
    public FileProcessResponse<FileTreeResponse> deleteFile(DeleteFileRequest request) {
        String awsKeyPath = request.getAlgorithmId() + "/" + request.getUserId() + "/" + request.getDeletePathSuffix();
        String prefix = request.getAlgorithmId() + "/" + request.getUserId();
        String message = null;

        try{
            message = repository.deleteFile(awsKeyPath);

            if (message.equals("fail")) {
                throw new RuntimeException("cannot modify file");
            }

            List<FileTreeResponse> fileTrees = repository.findFileTrees(prefix);

            return new FileProcessResponse<>(200, fileTrees, null);
        } catch (Exception e){
            log.error(e.getMessage());
            return new FileProcessResponse<>(6000, null, e.getMessage());
        }

    }

    @Override
    public FileProcessResponse<FileTreeResponse> findFileTree(FileTreeRequest request) {
        String prefix = request.getAlgorithmId() + "/" + request.getUserId();
        try {
            List<FileTreeResponse> fileTrees = repository.findFileTrees(prefix);
            return new FileProcessResponse<>(200, fileTrees, null);
        } catch (Exception e) {
            log.error(e.getMessage());
            return new FileProcessResponse<>(6000, null, e.getMessage());
        }
    }

    // create new file or folder
    @Override
    public FileProcessResponse<FileTreeResponse> createFileOrFolder(CreateFileRequest request) {
        String filePath = request.getAlgorithmId() + "/" + request.getUserId() + "/" + request.getCreatePath();
        String prefix = request.getAlgorithmId() + "/" + request.getUserId();
        String tempFilePath = "temp_" + UUID.randomUUID();
        File file = null;

        try {
            file = new File(tempFilePath);
            if (!file.exists()) {
                file.createNewFile();
            }
            repository.saveFile(filePath, file);

            List<FileTreeResponse> fileTrees = repository.findFileTrees(prefix);

            return new FileProcessResponse<>(200, fileTrees, null);

        } catch (Exception e) {
            log.error(e.getMessage());
            return new FileProcessResponse<>(6000, null, e.getMessage());
        } finally {
            file.delete();
        }
    }


    public <T> FileProcessResponse<T> restPost(String url, Object requestObject, Class<T> responseDataType) {

        // `FileProcessResponse<T>`의 타입을 지정
        @SuppressWarnings("unchecked")
        Class<FileProcessResponse<T>> responseType = (Class<FileProcessResponse<T>>)(Class<?>)FileProcessResponse.class;

        // postForObject() 메서드 호출
        return restTemplate.postForObject(url, requestObject, responseType);
    }

    public File getFile(String sourceCode) {
        String localPath = "temp_" + UUID.randomUUID();
        File uploadFile = new File(localPath);

        if (!uploadFile.exists()) {
            try {
                FileWriter fw = new FileWriter(uploadFile.getAbsolutePath());
                BufferedWriter bw = new BufferedWriter(fw);
                bw.write(sourceCode);
                bw.close();
            } catch (IOException e) {
                throw new RuntimeException();
            }
        }
        return uploadFile;
    }

}