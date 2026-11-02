/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.domain.lending.loan.origination.web.evidence.impl;
import org.springframework.core.io.buffer.*;
import org.springframework.http.*;
import org.springframework.http.server.reactive.*;
import org.springframework.web.server.*;
import reactor.core.publisher.*;

/** Keeps the managed JSON envelope bounded without changing existing legacy route codec limits. */
public final class ManagedEvidenceEnvelopeWebFilterImpl implements WebFilter {
    public static final int MAX_ENVELOPE_BYTES=3145728;
    @Override public Mono<Void> filter(ServerWebExchange exchange,WebFilterChain chain){
        if(exchange.getRequest().getMethod()!=HttpMethod.POST||!exchange.getRequest().getPath().value().startsWith("/api/v1/managed-loan-evidence/"))return chain.filter(exchange);
        if(exchange.getRequest().getHeaders().getContentLength()>MAX_ENVELOPE_BYTES)return Mono.error(tooLarge());
        return DataBufferUtils.join(exchange.getRequest().getBody(),MAX_ENVELOPE_BYTES).map(buffer->{try{byte[] bytes=new byte[buffer.readableByteCount()];buffer.read(bytes);return bytes;}finally{DataBufferUtils.release(buffer);}}).defaultIfEmpty(new byte[0])
            .onErrorMap(DataBufferLimitException.class,error->tooLarge()).flatMap(bytes->{
                var request=new ServerHttpRequestDecorator(exchange.getRequest()){@Override public Flux<DataBuffer> getBody(){return bytes.length==0?Flux.empty():Flux.defer(()->Flux.just(exchange.getResponse().bufferFactory().wrap(bytes)));}};
                return chain.filter(exchange.mutate().request(request).build());
            });
    }
    private static ResponseStatusException tooLarge(){return new ResponseStatusException(HttpStatus.valueOf(413),"Managed evidence JSON envelope exceeds 3145728 bytes");}
}
