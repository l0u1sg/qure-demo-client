package com.incepto.quredemo.client;


import com.incepto.quredemo.config.QureConfiguration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ClientHttpRequest;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyInserter;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import reactor.netty.tcp.SslProvider;
import reactor.netty.tcp.TcpClient;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeoutException;

import static com.incepto.quredemo.client.DicomMultipartRelatedHttpMessageWriter.MULTIPART_RELATED_MEDIA_TYPE;

@Slf4j
@Service
@RequiredArgsConstructor
public class QureClient {

    private static final String STUDIES_ENDPOINT = "studies/";
    private static final String RESULTS_ENDPOINT = "results/";

    private final QureConfiguration qureConfiguration;

    public static WebClient create(String baseUrl) {
        var tcpClient = TcpClient.create().secure(SslProvider.defaultClientProvider());
        var httpClient = HttpClient.from(tcpClient).followRedirect(true);
        return WebClient.builder()
                .baseUrl(baseUrl)
                .clientConnector(new ReactorClientHttpConnector(httpClient)).build();
    }

    public Mono<QureSeriesResponseDto> upload(Path path, String sopInstanceUid, boolean withFix) {
        try {
            WebClient client = create(qureConfiguration.getBaseUrl());
            byte[] array = Files.readAllBytes(path);

            BodyInserter<?, ? super ClientHttpRequest> bodyInserter;
            if(withFix) {
                bodyInserter = new QureDicomRelatedMultipartBodyInserter().with(sopInstanceUid, array);
            } else {
                bodyInserter = new DicomRelatedMultipartBodyInserter().with(sopInstanceUid, array);
            }

            log.info("uploading");
            return client.post()
                    .uri(STUDIES_ENDPOINT)
                    .contentType(MULTIPART_RELATED_MEDIA_TYPE)
                    .header("Authorization", qureConfiguration.getAuthorization())
                    .body(bodyInserter)
                    .exchange()
                    .flatMap(this::mapUploadClientResponse)
                    .timeout(qureConfiguration.getRequestTimeout())
                    .onErrorMap(TimeoutException.class, QureConnectionException::new)
                    .onErrorMap(IOException.class, QureConnectionException::new);
        } catch (IOException e) {
            return Mono.error(new RuntimeException("Could not read bytes from file when uploading to Qure", e));
        }
    }

    public Mono<Boolean> ping() {
        WebClient client = create(qureConfiguration.getBaseUrl());
        // Returns true on 4xx and 5xx because an error is expected
        // as there is no health endpoint on qure server we do a request to the upload endpoint
        // if the upload request connect the server will return an error
        // we only accept 4xx and 5xx as valid response because another code
        // will indicate the server is not behaving properly / our code won't handle the server
        return client.post()
                .uri(STUDIES_ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromPublisher(Mono.just("ping"), String.class))
                .exchange()
                .timeout(qureConfiguration.getRequestTimeout())
                .map(clientResponse -> clientResponse.statusCode().is4xxClientError()
                        || clientResponse.statusCode().is5xxServerError())
                .onErrorMap(TimeoutException.class, QureConnectionException::new)
                .onErrorMap(IOException.class, QureConnectionException::new);
    }

    public Mono<QureResultResponseDto> getResults(String instanceUid) {
        WebClient client = create(qureConfiguration.getBaseUrl());
        log.info("getResults");
        return client.get()
                .uri(RESULTS_ENDPOINT + instanceUid)
                .header("Authorization", qureConfiguration.getAuthorization())
                .exchange()
                .flatMap(this::mapGetResultClientResponse)
                .timeout(qureConfiguration.getRequestTimeout())
                .onErrorMap(TimeoutException.class, QureConnectionException::new)
                .onErrorMap(IOException.class, QureConnectionException::new);
    }

    public Flux<DataBuffer> downloadObject(String uriStr) {
        WebClient client = create("");
        URI uri = URI.create(uriStr);
        log.info("downloadObject");
        return client.get()
                .uri(uri)
                .exchange()
                .flatMapMany(this::mapDownloadClientResponse)
                .timeout(qureConfiguration.getRequestTimeout())
                .onErrorMap(TimeoutException.class, QureConnectionException::new)
                .onErrorMap(IOException.class, QureConnectionException::new);
    }

    private Mono<QureResultResponseDto> mapGetResultClientResponse(ClientResponse clientResponse) {
        HttpStatus httpStatus = clientResponse.statusCode();
        if (clientResponse.statusCode().equals(HttpStatus.SERVICE_UNAVAILABLE)
                || clientResponse.statusCode().is2xxSuccessful()
                || clientResponse.statusCode().equals(HttpStatus.NOT_FOUND)) {
            return clientResponse.bodyToMono(QureResultResponseDto.class);
        } else if (HttpStatus.UNAUTHORIZED.equals(httpStatus)) {
            return Mono.error(new QureConnectionException(httpStatus, ""));
        } else if(clientResponse.statusCode().is4xxClientError()) {
            return clientResponse.bodyToMono(String.class).map(body -> {
                throw new QureConnectionException(clientResponse.statusCode(), body);
            });
        }
        return Mono.error(new QureConnectionException(httpStatus, ""));
    }

    private Mono<QureSeriesResponseDto> mapUploadClientResponse(ClientResponse clientResponse) {
        HttpStatus httpStatus = clientResponse.statusCode();
        if (httpStatus.is2xxSuccessful()) {
            return clientResponse.bodyToMono(QureSeriesResponseDto.class);
        } else {
            return clientResponse.bodyToMono(String.class)
                    .flatMap(body -> Mono.error(new QureConnectionException(httpStatus, body)));
        }
    }

    private Flux<DataBuffer> mapDownloadClientResponse(ClientResponse clientResponse) {
        HttpStatus httpStatus = clientResponse.statusCode();
        if (httpStatus.is2xxSuccessful()) {
            return clientResponse.bodyToFlux(DataBuffer.class);
        } else {
            return Flux.error(new QureConnectionException(httpStatus, ""));
        }
    }
}
