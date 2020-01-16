package com.incepto.quredemo.client;

import org.reactivestreams.Publisher;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.ResolvableType;
import org.springframework.http.HttpEntity;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.http.client.reactive.ClientHttpRequest;
import org.springframework.http.codec.HttpMessageWriter;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserter;
import org.springframework.web.reactive.function.BodyInserters;
import reactor.core.publisher.Mono;

public class DicomRelatedMultipartBodyInserter  implements BodyInserters.MultipartInserter {

    private static final ResolvableType MULTIPART_RELATED_DATA_TYPE = ResolvableType.forClassWithGenerics(MultiValueMap.class, String.class, Object.class);

    private final MultipartBodyBuilder builder;

    DicomRelatedMultipartBodyInserter() {
        this.builder = new MultipartBodyBuilder();
    }

    @Override
    public BodyInserters.FormInserter<Object> with(String key, Object value) {
        this.builder.part(key, value);
        return this;
    }

    @Override
    public BodyInserters.FormInserter<Object> with(MultiValueMap<String, Object> values) {
        return this.withInternal(values);
    }

    private BodyInserters.MultipartInserter withInternal(MultiValueMap<String, ?> values) {
        values.forEach((key, valueList) -> {
            for (Object value : valueList) {
                this.builder.part(key, value);
            }
        });
        return this;
    }

    @Override
    public <T, P extends Publisher<T>> BodyInserters.MultipartInserter withPublisher(String name, P publisher, Class<T> elementClass) {
        this.builder.asyncPart(name, publisher, elementClass);
        return this;
    }

    @Override
    public <T, P extends Publisher<T>> BodyInserters.MultipartInserter withPublisher(String name, P publisher, ParameterizedTypeReference<T> typeReference) {
        this.builder.asyncPart(name, publisher, typeReference);
        return this;
    }

    @Override
    public Mono<Void> insert(ClientHttpRequest outputMessage, BodyInserter.Context context) {
        HttpMessageWriter<MultiValueMap<String, ?>> messageWriter = buildMessageWriter(context);
        MultiValueMap<String, HttpEntity<?>> body = this.builder.build();
        return messageWriter.write(Mono.just(body), MULTIPART_RELATED_DATA_TYPE, DicomMultipartRelatedHttpMessageWriter.MULTIPART_RELATED_MEDIA_TYPE, outputMessage, context.hints());
    }

    protected HttpMessageWriter<MultiValueMap<String, ?>> buildMessageWriter(BodyInserter.Context context) {
        return new DicomMultipartRelatedHttpMessageWriter(context.messageWriters());
    }
}
