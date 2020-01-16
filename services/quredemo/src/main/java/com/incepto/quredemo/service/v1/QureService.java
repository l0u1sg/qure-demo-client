package com.incepto.quredemo.service.v1;


import com.incepto.quredemo.client.QureClient;
import com.incepto.quredemo.client.QureResultResponseDto;
import com.incepto.quredemo.client.QureSeriesResponseDto;
import com.incepto.quredemo.config.QureConfiguration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class QureService {

    private final QureClient qureClient;
    private final QureConfiguration qureConfiguration;

    public void process() {
        if(qureConfiguration.getInstanceUid().isEmpty()) {
            log.info("please define instance uid");
            return;
        }

        try(ByteArrayOutputStream baos = new ByteArrayOutputStream()) {


            this.upload(Path.of(qureConfiguration.getInputPath()), qureConfiguration.isFixApplied())
                    .delayElement(Duration.ofSeconds(5))
                    .flatMap(uploadDto -> getResult(qureConfiguration.getInstanceUid()))
                    .flatMapMany(resultDto -> download(resultDto.getFiles().getReports().getDcm()))
                    .collectList().map((List<DataBuffer> dataBuffers) -> {
                dataBuffers.forEach(dataBuffer ->  {
                    try {
                        baos.writeBytes(dataBuffer.asInputStream().readAllBytes());
                    } catch (IOException e) {
                        log.error("an error occured", e);
                    }
                });
                return baos;
            }).then(Mono.defer(() -> {
                try (OutputStream fos = Files.newOutputStream(Path.of(qureConfiguration.getOutputPath()))){
                    fos.write(baos.toByteArray());
                } catch (IOException e) {
                    log.error("an error occured", e);
                }
                return Mono.just(true);
            }))
                    .doOnError(Exception.class, this::doOnError)
                    .block();
        } catch (IOException e) {
            log.error("an error occured", e);
        }
    }

    public Mono<QureSeriesResponseDto> upload(Path path, boolean withFix) {
        return qureClient.upload(path, "filename", withFix);
    }

    public Flux<DataBuffer> download(String fileUri) {
        return qureClient.downloadObject(fileUri);
    }

    public Mono<QureResultResponseDto> getResult(String instanceUid) {
        return qureClient.getResults(instanceUid);
    }

    private void doOnError(Exception e) {
        log.error("An error occured: ", e);
    }
}
