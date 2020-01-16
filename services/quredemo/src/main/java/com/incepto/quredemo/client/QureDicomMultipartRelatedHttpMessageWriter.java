package com.incepto.quredemo.client;

import org.springframework.core.codec.CharSequenceEncoder;
import org.springframework.http.codec.EncoderHttpMessageWriter;
import org.springframework.http.codec.FormHttpMessageWriter;
import org.springframework.http.codec.HttpMessageWriter;
import org.springframework.http.codec.ResourceHttpMessageWriter;
import org.springframework.lang.Nullable;
import org.springframework.util.MultiValueMap;

import java.util.Arrays;
import java.util.List;

public class QureDicomMultipartRelatedHttpMessageWriter extends DicomMultipartRelatedHttpMessageWriter {

    public QureDicomMultipartRelatedHttpMessageWriter() {
        this(Arrays.asList(
                new EncoderHttpMessageWriter<>(CharSequenceEncoder.textPlainOnly()),
                new ResourceHttpMessageWriter()
        ));
    }

    public QureDicomMultipartRelatedHttpMessageWriter(List<HttpMessageWriter<?>> partWriters) {
        this(partWriters, new FormHttpMessageWriter());
    }

    public QureDicomMultipartRelatedHttpMessageWriter(List<HttpMessageWriter<?>> partWriters, @Nullable HttpMessageWriter<MultiValueMap<String, String>> formWriter) {
        super(partWriters, formWriter);
    }

    @Override
    protected List<String> buildPartContentDisposition(String name) {
        return List.of("form-data; name=\"" + name + "\"; filename=\"" + name + ".dcm\"");
    }
}
