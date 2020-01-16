package com.incepto.quredemo.client;

import com.incepto.quredemo.TestContext;
import com.incepto.quredemo.config.QureConfiguration;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static java.nio.file.StandardOpenOption.CREATE_NEW;
import static java.nio.file.StandardOpenOption.WRITE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(classes = TestContext.class)
class QureClientTest {

    @Autowired
    private QureClient qureClient;

    @Autowired
    private QureConfiguration qureConfiguration;

    @TempDir
    static Path tempDir;

    private final MockWebServer mockWebServer = new MockWebServer();

    private Path createFile() {
        Path file = tempDir.resolve("file");
        if (Files.exists(file)) {
            return file;
        }
        try (var os = Files.newOutputStream(tempDir.resolve("file"), CREATE_NEW, WRITE)) {
            os.write("FILE_SENT".getBytes());
        } catch (IOException e) {
            e.printStackTrace();
        }
        return file;
    }

    @BeforeEach
    void before() {
        qureConfiguration.setRequestTimeout(Duration.ofSeconds(2));
        qureConfiguration.setBaseUrl(mockWebServer.url("/").toString());
        qureConfiguration.setAuthorization("MOCK_AUTHORIZATION");
    }

    @AfterEach
    void after() throws IOException {
        mockWebServer.close();
        qureConfiguration.setRequestTimeout(Duration.ofSeconds(10));
    }

    @Test
    void upload_OK_withoutFix() throws InterruptedException, IOException {
        mockWebServer.enqueue(
                new MockResponse()
                        .setResponseCode(200)
                        .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .setBody("{" +
                                "\"message\": \"ok\"," +
                                "\"result\": 1," +
                                "\"task_created\": 0" +
                                "}")
        );

        QureSeriesResponseDto response = qureClient.upload(createFile(), "dummySopInstanceUid", false).block();
        assertNotNull(response);
        assertEquals(response.getMessage(), "ok");
        assertEquals(response.getResult(), 1);
        assertEquals(response.getTask_created(), 0);

        RecordedRequest recordedRequest = mockWebServer.takeRequest(1, TimeUnit.SECONDS);
        //use method provided by MockWebServer to assert the request header
        assertEquals("MOCK_AUTHORIZATION", recordedRequest.getHeader("Authorization"));

        assertTrue(Objects.requireNonNull(recordedRequest.getHeader("Content-Type")).contains("boundary="));
        assertTrue(Objects.requireNonNull(recordedRequest.getHeader("Content-Type")).contains("charset=UTF-8"));
        assertTrue(Objects.requireNonNull(recordedRequest.getHeader("Content-Type")).contains("type=\"application/dicom\""));

        assertEquals("/studies/", recordedRequest.getPath());

        InputStream requestStream = recordedRequest.getBody().inputStream();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(requestStream))) {
            String body = br.lines().collect(Collectors.joining(System.lineSeparator()));

            assertTrue(body.contains("FILE_SENT"));
            assertTrue(body.contains("Content-Disposition: form-data"));
            assertTrue(body.contains("Content-Type: application/dicom"));
        }

        assertNotNull(recordedRequest.getHeader("Content-Type"));
        assertTrue(recordedRequest.getHeader("Content-Type").contains("multipart/related"));
    }


    @Test
    void upload_OK_withFix() throws InterruptedException, IOException {
        mockWebServer.enqueue(
                new MockResponse()
                        .setResponseCode(200)
                        .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .setBody("{" +
                                "\"message\": \"ok\"," +
                                "\"result\": 1," +
                                "\"task_created\": 0" +
                                "}")
        );

        QureSeriesResponseDto response = qureClient.upload(createFile(), "dummySopInstanceUid", true).block();
        assertNotNull(response);
        assertEquals(response.getMessage(), "ok");
        assertEquals(response.getResult(), 1);
        assertEquals(response.getTask_created(), 0);

        RecordedRequest recordedRequest = mockWebServer.takeRequest(1, TimeUnit.SECONDS);
        //use method provided by MockWebServer to assert the request header
        assertEquals("MOCK_AUTHORIZATION", recordedRequest.getHeader("Authorization"));

        assertTrue(Objects.requireNonNull(recordedRequest.getHeader("Content-Type")).contains("boundary="));
        assertTrue(Objects.requireNonNull(recordedRequest.getHeader("Content-Type")).contains("charset=UTF-8"));
        assertTrue(Objects.requireNonNull(recordedRequest.getHeader("Content-Type")).contains("type=\"application/dicom\""));

        assertEquals("/studies/", recordedRequest.getPath());

        InputStream requestStream = recordedRequest.getBody().inputStream();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(requestStream))) {
            String body = br.lines().collect(Collectors.joining(System.lineSeparator()));

            assertTrue(body.contains("FILE_SENT"));
            assertTrue(body.contains("Content-Disposition: form-data; name=\"dummySopInstanceUid\"; filename=\"dummySopInstanceUid.dcm\""));
            assertTrue(body.contains("Content-Type: application/dicom"));
        }

        assertNotNull(recordedRequest.getHeader("Content-Type"));
        assertTrue(recordedRequest.getHeader("Content-Type").contains("multipart/related"));
    }


    @Test
    void requestResult_OK() throws InterruptedException {
        mockWebServer.enqueue(
                new MockResponse()
                        .setResponseCode(200)
                        .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .setBody("{  " +
                                "   \"success\": true," +
                                "   \"message\": \"Results have been successfully generated.\"," +
                                "   \"tags\":[  " +
                                "      {  " +
                                "         \"results\":\"0.61\"," +
                                "         \"presence\":\"1\"," +
                                "         \"description\":\"Abnormal\"," +
                                "         \"tag\":\"abnormal\"" +
                                "      }," +
                                "      {  " +
                                "         \"results\":\"0.36\"," +
                                "         \"presence\":\"-1\"," +
                                "         \"description\":\"Pleural Effusion\"," +
                                "         \"tag\":\"peffusion\"" +
                                "      }," +
                                "      {  " +
                                "         \"results\":\"0.21\"," +
                                "         \"presence\":\"-1\"," +
                                "         \"description\":\"Tuberculosis Screen\"," +
                                "         \"tag\":\"tuberculosis\"" +
                                "      }" +
                                "   ]," +
                                "   \"files\": {" +
                                "        \"sc\":  \"aaaa\"," +
                                "        \"gsps\": \"aaaa\"," +
                                "        \"reports\": {" +
                                "         \"pdf\":\"aaaa\"," +
                                "         \"dcm\":\"aaaaa\"," +
                                "         \"sr\":\"aaaaa\"" +
                                "      }" +
                                "   }," +
                                "   \"integrity\":true" +
                                "}")
        );

        QureResultResponseDto response = qureClient.getResults("MOCK_UID").block();
        assertNotNull(response);
        assertEquals(response.getMessage(), "Results have been successfully generated.");
        assertEquals(response.getTags().size(), 3);
        assertEquals(response.isSuccess(), true);

        RecordedRequest recordedRequest = mockWebServer.takeRequest(1, TimeUnit.SECONDS);

        assertEquals("/results/MOCK_UID", recordedRequest.getPath());
        assertEquals("MOCK_AUTHORIZATION", recordedRequest.getHeader("Authorization"));
    }
}
