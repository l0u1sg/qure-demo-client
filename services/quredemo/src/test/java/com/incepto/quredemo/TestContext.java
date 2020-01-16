package com.incepto.quredemo;



import com.incepto.quredemo.client.QureClient;
import com.incepto.quredemo.config.QureConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.ComponentScan;

@ComponentScan
@DataMongoTest
@EnableAutoConfiguration
@AutoConfigureWebTestClient
public class TestContext {

    @SpyBean
    private QureClient qureClient;

    @SpyBean
    private QureConfiguration qureConfiguration;

}
