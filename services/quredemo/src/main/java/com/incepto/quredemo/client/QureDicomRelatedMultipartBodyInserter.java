package com.incepto.quredemo.client;

import org.springframework.http.codec.HttpMessageWriter;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserter;

public class QureDicomRelatedMultipartBodyInserter extends DicomRelatedMultipartBodyInserter {

    @Override
    protected HttpMessageWriter<MultiValueMap<String, ?>> buildMessageWriter(BodyInserter.Context context) {
        return new QureDicomMultipartRelatedHttpMessageWriter(context.messageWriters());
    }
}
