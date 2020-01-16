package com.incepto.quredemo.client;

import org.reactivestreams.Publisher;
import org.springframework.core.ResolvableType;
import org.springframework.core.ResolvableTypeProvider;
import org.springframework.core.codec.CharSequenceEncoder;
import org.springframework.core.codec.CodecException;
import org.springframework.core.codec.Hints;
import org.springframework.core.io.Resource;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.PooledDataBuffer;
import org.springframework.core.log.LogFormatUtils;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ReactiveHttpOutputMessage;
import org.springframework.http.codec.EncoderHttpMessageWriter;
import org.springframework.http.codec.FormHttpMessageWriter;
import org.springframework.http.codec.HttpMessageWriter;
import org.springframework.http.codec.LoggingCodecSupport;
import org.springframework.http.codec.ResourceHttpMessageWriter;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.MimeTypeUtils;
import org.springframework.util.MultiValueMap;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@SuppressWarnings({"PMD.ExcessiveImports", "PMD.DataClass", "PMD.TooManyMethods", "PMD.DataClass"})
public class DicomMultipartRelatedHttpMessageWriter extends LoggingCodecSupport implements HttpMessageWriter<MultiValueMap<String, ?>> {

    /**
     * THe default charset used by the writer.
     */
    public static final Charset DEFAULT_CHARSET = StandardCharsets.UTF_8;

    /** Suppress logging from individual part writers (full map logged at this level). */
    private static final Map<String, Object> DEFAULT_HINTS = Hints.from(Hints.SUPPRESS_LOGGING_HINT, true);

    static final MediaType MULTIPART_RELATED_MEDIA_TYPE = new MediaType("multipart", "related");
    private static final String APPLICATION_DICOM_CONTENT_TYPE = "\"application/dicom\"";

    private final List<HttpMessageWriter<?>> partWriters;

    private Charset charset = DEFAULT_CHARSET;

    private final List<MediaType> supportedMediaTypes;

    public DicomMultipartRelatedHttpMessageWriter() {
        this(Arrays.asList(
                new EncoderHttpMessageWriter<>(CharSequenceEncoder.textPlainOnly()),
                new ResourceHttpMessageWriter()
        ));
    }

    public DicomMultipartRelatedHttpMessageWriter(List<HttpMessageWriter<?>> partWriters) {
        this(partWriters, new FormHttpMessageWriter());
    }

    public DicomMultipartRelatedHttpMessageWriter(List<HttpMessageWriter<?>> partWriters, @Nullable HttpMessageWriter<MultiValueMap<String, String>> formWriter) {
        this.partWriters = partWriters;
        this.supportedMediaTypes = initMediaTypes(formWriter);
    }

    private static List<MediaType> initMediaTypes(@Nullable HttpMessageWriter<?> formWriter) {
        List<MediaType> result = new ArrayList<>();
        result.add(MULTIPART_RELATED_MEDIA_TYPE);
        if (formWriter != null) {
            result.addAll(formWriter.getWritableMediaTypes());
        }

        return Collections.unmodifiableList(result);
    }

    public List<HttpMessageWriter<?>> getPartWriters() {
        return Collections.unmodifiableList(this.partWriters);
    }

    public void setCharset(Charset charset) {
        Assert.notNull(charset, "Charset must not be null");
        this.charset = charset;
    }

    public Charset getCharset() {
        return this.charset;
    }

    @Override
    public List<MediaType> getWritableMediaTypes() {
        return this.supportedMediaTypes;
    }

    @Override
    public boolean canWrite(ResolvableType elementType, @Nullable MediaType mediaType) {
        return (MultiValueMap.class.isAssignableFrom(elementType.toClass()) &&
                (mediaType == null ||
                        this.supportedMediaTypes.stream().anyMatch(element -> element.isCompatibleWith(mediaType))));
    }

    @Override
    public Mono<Void> write(Publisher<? extends MultiValueMap<String, ?>> inputStream, ResolvableType elementType, @Nullable MediaType mediaType, ReactiveHttpOutputMessage outputMessage, Map<String, Object> hints) {
        return Mono.from(inputStream).flatMap((map) -> this.writeMultipart(map, outputMessage, hints));
    }

    private Mono<Void> writeMultipart(MultiValueMap<String, ?> map, ReactiveHttpOutputMessage outputMessage, Map<String, Object> hints) {
        byte[] boundary = generateMultipartBoundary();

        outputMessage.getHeaders().setContentType(buildContentType(boundary));

        LogFormatUtils.traceDebug(logger, traceOn -> Hints.getLogPrefix(hints) + "Encoding " +
                (isEnableLoggingRequestDetails() ?
                        LogFormatUtils.formatValue(map, !traceOn) :
                        "parts " + map.keySet() + " (content masked)"));

        DataBufferFactory bufferFactory = outputMessage.bufferFactory();

        Flux<DataBuffer> body = Flux.fromIterable(map.entrySet())
                .concatMap(entry -> encodePartValues(boundary, entry.getKey(), entry.getValue(), bufferFactory))
                .concatWith(generateLastLine(boundary, bufferFactory))
                .doOnDiscard(PooledDataBuffer.class, PooledDataBuffer::release);

        return outputMessage.writeWith(body);
    }

    protected byte[] generateMultipartBoundary() {
        return MimeTypeUtils.generateMultipartBoundary();
    }

    private Flux<DataBuffer> encodePartValues(
            byte[] boundary, String name, List<?> values, DataBufferFactory bufferFactory) {

        return Flux.concat(values.stream().map(v ->
                encodePart(boundary, name, v, bufferFactory)).collect(Collectors.toList()));
    }

    protected List<String> buildPartContentDisposition(String name) {
        return List.of("form-data");
    }

    protected List<String> buildPartContentType() {
        return List.of("application/dicom");
    }

    protected MediaType buildContentType(byte[] boundary) {
        Map<String, String> params = new HashMap<>(2);
        params.put("boundary", new String(boundary, StandardCharsets.US_ASCII));
        params.put("charset", getCharset().name());
        params.put("type", APPLICATION_DICOM_CONTENT_TYPE);
        return new MediaType(MULTIPART_RELATED_MEDIA_TYPE, params);
    }

    @SuppressWarnings("unchecked")
    private <T> Flux<DataBuffer> encodePart(byte[] boundary, String name, T value, DataBufferFactory bufferFactory) {
        MultipartHttpOutputMessage outputMessage = new MultipartHttpOutputMessage(bufferFactory, this.getCharset());
        HttpHeaders outputHeaders = outputMessage.getHeaders();

        T body;
        ResolvableType resolvableType = null;
        if (value instanceof HttpEntity) {
            HttpEntity<T> httpEntity = (HttpEntity<T>)value;
            outputHeaders.putAll(httpEntity.getHeaders());

            outputHeaders.put("Content-Disposition", buildPartContentDisposition(name));
            outputHeaders.put("Content-Type", buildPartContentType());
            body = httpEntity.getBody();
            Assert.state(body != null, "MultipartHttpMessageWriter only supports HttpEntity with body");
            if (httpEntity instanceof ResolvableTypeProvider) {
                resolvableType = ((ResolvableTypeProvider)httpEntity).getResolvableType();
            }
        } else {
            body = value;
        }
        if (resolvableType == null) {
            resolvableType = ResolvableType.forClass(body.getClass());
        }

        if (!outputHeaders.containsKey(HttpHeaders.CONTENT_DISPOSITION)) {
            if (body instanceof Resource) {
                outputHeaders.setContentDispositionFormData(name, ((Resource) body).getFilename());
            }
            else if (resolvableType.resolve() == Resource.class) {
                body = (T) Mono.from((Publisher<?>) body).doOnNext(o -> outputHeaders
                        .setContentDispositionFormData(name, ((Resource) o).getFilename()));
            }
            else {
                outputHeaders.setContentDispositionFormData(name, null);
            }
        }

        MediaType contentType = outputHeaders.getContentType();

        final ResolvableType finalBodyType = resolvableType;
        Optional<HttpMessageWriter<?>> writer = this.partWriters.stream()
                .filter(partWriter -> partWriter.canWrite(finalBodyType, contentType))
                .findFirst();

        if (writer.isEmpty()) {
            return Flux.error(new CodecException("No suitable writer found for part: " + name));
        }

        Publisher<T> bodyPublisher =
                body instanceof Publisher ? (Publisher<T>) body : Mono.just(body);

        // The writer will call MultipartHttpOutputMessage#write which doesn't actually write
        // but only stores the body Flux and returns Mono.empty().

        Mono<Void> partContentReady = ((HttpMessageWriter<T>) writer.get())
                .write(bodyPublisher, resolvableType, contentType, outputMessage, DEFAULT_HINTS);

        // After partContentReady, we can access the part content from MultipartHttpOutputMessage
        // and use it for writing to the actual request body

        Flux<DataBuffer> partContent = partContentReady.thenMany(Flux.defer(outputMessage::getBody));

        return Flux.concat(
                generateBoundaryLine(boundary, bufferFactory),
                partContent,
                generateNewLine(bufferFactory));
    }

    private Mono<DataBuffer> generateBoundaryLine(byte[] boundary, DataBufferFactory bufferFactory) {
        return Mono.fromCallable(() -> {
            DataBuffer buffer = bufferFactory.allocateBuffer(boundary.length + 4);
            buffer.write((byte)'-');
            buffer.write((byte)'-');
            buffer.write(boundary);
            buffer.write((byte)'\r');
            buffer.write((byte)'\n');
            return buffer;
        });
    }

    private Mono<DataBuffer> generateNewLine(DataBufferFactory bufferFactory) {
        return Mono.fromCallable(() -> {
            DataBuffer buffer = bufferFactory.allocateBuffer(2);
            buffer.write((byte)'\r');
            buffer.write((byte)'\n');
            return buffer;
        });
    }

    private Mono<DataBuffer> generateLastLine(byte[] boundary, DataBufferFactory bufferFactory) {
        return Mono.fromCallable(() -> {
            DataBuffer buffer = bufferFactory.allocateBuffer(boundary.length + 6);
            buffer.write((byte)'-');
            buffer.write((byte)'-');
            buffer.write(boundary);
            buffer.write((byte)'-');
            buffer.write((byte)'-');
            buffer.write((byte)'\r');
            buffer.write((byte)'\n');
            return buffer;
        });
    }

    private static class MultipartHttpOutputMessage implements ReactiveHttpOutputMessage {
        private final DataBufferFactory dataBufferFactory;
        private final Charset charset;
        private final HttpHeaders headers = new HttpHeaders();
        private final AtomicBoolean committed = new AtomicBoolean();
        @Nullable
        private Flux<DataBuffer> body;

        public MultipartHttpOutputMessage(DataBufferFactory dataBufferFactory, Charset charset) {
            this.dataBufferFactory = dataBufferFactory;
            this.charset = charset;
        }

        @Override
        public HttpHeaders getHeaders() {
            return this.body != null ? HttpHeaders.readOnlyHttpHeaders(this.headers) : this.headers;
        }

        @Override
        public DataBufferFactory bufferFactory() {
            return this.dataBufferFactory;
        }

        @Override
        public void beforeCommit(Supplier<? extends Mono<Void>> action) {
            this.committed.set(true);
        }

        @Override
        public boolean isCommitted() {
            return this.committed.get();
        }

        @Override
        public Mono<Void> writeWith(Publisher<? extends DataBuffer> body) {
            if (this.body != null) {
                return Mono.error(new IllegalStateException("Multiple calls to writeWith() not supported"));
            } else {
                this.body = this.generateHeaders().concatWith(body);
                return Mono.empty();
            }
        }

        private Mono<DataBuffer> generateHeaders() {
            return Mono.fromCallable(() -> {
                DataBuffer buffer = this.dataBufferFactory.allocateBuffer();
                for (Map.Entry<String, List<String>> entry : this.headers.entrySet()) {
                    byte[] headerName = entry.getKey().getBytes(this.charset);
                    for (String headerValueString : entry.getValue()) {
                        byte[] headerValue = headerValueString.getBytes(this.charset);
                        buffer.write(headerName);
                        buffer.write((byte)':');
                        buffer.write((byte)' ');
                        buffer.write(headerValue);
                        buffer.write((byte)'\r');
                        buffer.write((byte)'\n');
                    }
                }
                buffer.write((byte)'\r');
                buffer.write((byte)'\n');
                return buffer;
            });
        }

        @Override
        public Mono<Void> writeAndFlushWith(Publisher<? extends Publisher<? extends DataBuffer>> body) {
            return Mono.error(new UnsupportedOperationException());
        }

        public Flux<DataBuffer> getBody() {
            return this.body != null ? this.body : Flux.error(new IllegalStateException("Body has not been written yet"));
        }

        @Override
        public Mono<Void> setComplete() {
            return Mono.error(new UnsupportedOperationException());
        }
    }
}