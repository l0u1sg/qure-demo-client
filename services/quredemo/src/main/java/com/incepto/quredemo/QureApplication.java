package com.incepto.quredemo;

import com.incepto.quredemo.service.v1.QureService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.reactive.config.EnableWebFlux;
@SpringBootApplication
@EnableWebFlux
public class QureApplication implements CommandLineRunner {

    @Autowired
    QureService qureService;

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(QureApplication.class);
        app.setWebApplicationType(WebApplicationType.NONE);
        app.run(args);
    }

    @Override
    public void run(String... strings) {
        qureService.process();
    }


}
