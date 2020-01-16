package com.incepto.quredemo.config;

import lombok.Data;
import org.hibernate.validator.constraints.time.DurationMax;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotEmpty;
import java.time.Duration;
import java.util.List;
import java.util.regex.Pattern;

@Data
@Validated
@Configuration
@ConfigurationProperties(prefix="incepto.qure")
public class QureConfiguration {

    private static final Pattern VALID_AUTH = Pattern.compile("Token [0-9a-fA-F]+");

    private boolean enabled = true;

    @NotEmpty
    private String baseUrl = "https://defaultbaseurl.incepto-ai.com";

    @NotEmpty
    private String authorization = "UNDEFINED";

    @NotEmpty
    private String inputPath = "inputDcm.dcm";

    private String instanceUid = "";

    private boolean fixApplied;

    @NotEmpty
    private String outputPath = "outputDcm.dcm";

    @Min(20)
    @Max(64)
    private int maxNameAndIdLength = 45;

    @DurationMin(seconds = 1)
    @DurationMax(seconds = 60)
    private Duration requestTimeout = Duration.ofSeconds(30);

    @DurationMin(seconds = 1)
    @DurationMax(minutes = 5)
    private Duration retryRate = Duration.ofSeconds(10);

    private List<QureFileType> resultTypes = List.of(QureFileType.PDF, QureFileType.OVERLAY);

    public boolean isEnabled() {
        return enabled && VALID_AUTH.matcher(authorization).matches();
    }
}
