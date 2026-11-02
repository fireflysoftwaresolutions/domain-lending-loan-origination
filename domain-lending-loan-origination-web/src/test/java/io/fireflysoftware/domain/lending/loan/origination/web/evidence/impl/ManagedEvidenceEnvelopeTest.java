/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.domain.lending.loan.origination.web.evidence.impl;
import io.fireflysoftware.domain.lending.loan.origination.core.evidence.ManagedEvidenceUploadRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.buffer.*;
import org.springframework.http.*;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.*;
import reactor.core.publisher.*;
import reactor.test.StepVerifier;
import static org.assertj.core.api.Assertions.*;

/** Real bounded JSON transport only. No owner or business success is simulated. */
class ManagedEvidenceEnvelopeTest {
    @Test void actualTwoMiBContentPassesAsBase64EnvelopeWithoutCodecTruncation()throws Exception{
        byte[] content=new byte[2097152];Arrays.fill(content,(byte)0x8a);var request=new ManagedEvidenceUploadRequest(UUID.randomUUID(),"a".repeat(200),"IDENTITY","application/pdf",content);byte[] wire=new ObjectMapper().writeValueAsBytes(request);
        assertThat(wire.length).isGreaterThan(2097152).isLessThan(3145728);var captured=new AtomicReference<byte[]>();var calls=new AtomicInteger();
        StepVerifier.create(new ManagedEvidenceEnvelopeWebFilterImpl().filter(exchange(wire),current->{calls.incrementAndGet();return DataBufferUtils.join(current.getRequest().getBody()).doOnNext(buffer->{try{byte[] bytes=new byte[buffer.readableByteCount()];buffer.read(bytes);captured.set(bytes);}finally{DataBufferUtils.release(buffer);}}).then();})).verifyComplete();
        assertThat(calls.get()).isEqualTo(1);assertThat(captured.get()).isEqualTo(wire);assertThat(new ObjectMapper().readValue(captured.get(),ManagedEvidenceUploadRequest.class).content()).isEqualTo(content);
    }
    @Test void chunkedEnvelopeOverThreeMiBCannotReachController(){var calls=new AtomicInteger();StepVerifier.create(new ManagedEvidenceEnvelopeWebFilterImpl().filter(exchange(new byte[3145729]),current->{calls.incrementAndGet();return Mono.empty();})).expectErrorSatisfies(error->{assertThat(error).isInstanceOf(ResponseStatusException.class);assertThat(((ResponseStatusException)error).getStatusCode().value()).isEqualTo(413);}).verify();assertThat(calls.get()).isZero();}
    @Test void emptyRecoveryBodyInvokesChainExactlyOnce(){var calls=new AtomicInteger();var exchange=MockServerWebExchange.from(MockServerHttpRequest.post("/api/v1/managed-loan-evidence/applications/"+UUID.randomUUID()+"/actions/"+UUID.randomUUID()+"/recovery").build());StepVerifier.create(new ManagedEvidenceEnvelopeWebFilterImpl().filter(exchange,current->{calls.incrementAndGet();return Mono.empty();})).verifyComplete();assertThat(calls.get()).isEqualTo(1);}
    private static MockServerWebExchange exchange(byte[] bytes){return MockServerWebExchange.from(MockServerHttpRequest.post("/api/v1/managed-loan-evidence/applications/"+UUID.randomUUID()+"/actions/"+UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON).body(Flux.just(DefaultDataBufferFactory.sharedInstance.wrap(bytes))));}
}
