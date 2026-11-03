/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.domain.lending.loan.origination.core.evidence.impl;

import com.sun.net.httpserver.HttpServer;
import io.fireflysoftware.commons.ecm.sdk.api.EvidenceReadControllerApi;
import io.fireflysoftware.commons.ecm.sdk.invoker.ApiClient;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;
import org.springframework.http.client.reactive.JdkClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import static org.assertj.core.api.Assertions.*;

/** Actual generated SDK over loopback HTTP; this is a media-contract regression, not business custody proof. */
class ManagedDocumentBinaryNegotiationTest {
    final UUID app=UUID.randomUUID(),action=UUID.randomUUID();
    @Test void genuineGeneratedBinaryReadNegotiatesBothMediaAndPreservesEveryBoundaryByte()throws Exception{
        byte[] bytes=new byte[2097152];for(int i=0;i<bytes.length;i++)bytes[i]=(byte)(17+i*31);
        try(var wire=new DocumentWireFixtureImpl(true,200,MediaType.APPLICATION_OCTET_STREAM_VALUE,bytes,"/owned-document")){
            var response=wire.api.readManagedDocumentEvidenceContentWithHttpInfo(app,action,null).block(Duration.ofSeconds(5));
            assertThat(response).isNotNull();assertThat(response.getStatusCode().value()).isEqualTo(200);assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_OCTET_STREAM);
            try(var stream=Objects.requireNonNull(response.getBody()).getInputStream()){assertThat(stream.readAllBytes()).containsExactly(bytes);}
            assertThat(wire.accept.get()).containsExactly(MediaType.APPLICATION_OCTET_STREAM,MediaType.APPLICATION_JSON);assertThat(wire.authorization.get()).isEqualTo("Bearer "+wire.token);
        }
    }
    @Test void genuineGeneratedBinaryReadRetainsTypedJson503InsteadOfNegotiation406()throws Exception{
        byte[] body=("{\"actionId\":\""+action+"\",\"code\":\"DOCUMENT_EVIDENCE_OBSERVATION_UNAVAILABLE\",\"outcome\":\"OBSERVATION_UNAVAILABLE\",\"recovery\":\"SAME_ACTION_OBSERVE_OR_EXACT_REPLAY\"}").getBytes(StandardCharsets.UTF_8);
        try(var wire=new DocumentWireFixtureImpl(true,503,MediaType.APPLICATION_JSON_VALUE,body)){
            assertThatThrownBy(()->wire.api.readManagedDocumentEvidenceContentWithHttpInfo(app,action,null).block(Duration.ofSeconds(5)))
                .isInstanceOfSatisfying(WebClientResponseException.class,error->{assertThat(error.getStatusCode().value()).isEqualTo(503);assertThat(error.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);assertThat(error.getResponseBodyAsByteArray()).containsExactly(body);});
            assertThat(wire.accept.get()).containsExactly(MediaType.APPLICATION_OCTET_STREAM,MediaType.APPLICATION_JSON);
        }
    }
    @Test void unrelatedGenuineReceiptOperationKeepsItsOriginalJsonAcceptAndBearer()throws Exception{
        byte[] body="{\"outcome\":\"OBSERVATION_UNAVAILABLE\"}".getBytes(StandardCharsets.UTF_8);
        try(var wire=new DocumentWireFixtureImpl(false,503,MediaType.APPLICATION_JSON_VALUE,body)){
            assertThatThrownBy(()->wire.api.observeManagedDocumentEvidenceWithHttpInfo(app,action,null).block(Duration.ofSeconds(5)))
                .isInstanceOfSatisfying(WebClientResponseException.class,error->{assertThat(error.getStatusCode().value()).isEqualTo(503);assertThat(error.getResponseBodyAsByteArray()).containsExactly(body);});
            assertThat(wire.accept.get()).containsExactly(MediaType.APPLICATION_JSON);assertThat(wire.authorization.get()).isEqualTo("Bearer "+wire.token);
        }
    }
    static final class DocumentWireFixtureImpl implements AutoCloseable {
        final HttpServer server;final EvidenceReadControllerApi api;final String token=UUID.randomUUID().toString();
        final AtomicReference<List<MediaType>> accept=new AtomicReference<>();final AtomicReference<String> authorization=new AtomicReference<>();
        DocumentWireFixtureImpl(boolean binary,int status,String contentType,byte[] body)throws Exception{
            this(binary,status,contentType,body,"");
        }
        DocumentWireFixtureImpl(boolean binary,int status,String contentType,byte[] body,String prefix)throws Exception{
            server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
            server.createContext(prefix+"/api/v1/managed-document-evidence/applications/",exchange->{
                var media=MediaType.parseMediaTypes(exchange.getRequestHeaders().getOrDefault(HttpHeaders.ACCEPT,List.of()));accept.set(media);authorization.set(exchange.getRequestHeaders().getFirst(HttpHeaders.AUTHORIZATION));
                boolean accepted=exchange.getRequestMethod().equals("GET")&&exchange.getRequestURI().getPath().endsWith("/content")==binary&&(!binary||media.contains(MediaType.APPLICATION_OCTET_STREAM));
                exchange.getResponseHeaders().set(HttpHeaders.CONTENT_TYPE,contentType);exchange.sendResponseHeaders(accepted?status:406,body.length);try(var stream=exchange.getResponseBody()){stream.write(body);}finally{exchange.close();}
            });server.start();
            var endpoint=java.net.URI.create("http://127.0.0.1:"+server.getAddress().getPort()+prefix);
            var transport=ApiClient.buildWebClientBuilder().clientConnector(new JdkClientHttpConnector(HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).connectTimeout(Duration.ofSeconds(2)).build()))
                .filter(TokenExchangedManagedDocumentEvidencePortImpl.managedContentNegotiation(endpoint)).codecs(codecs->codecs.defaultCodecs().maxInMemorySize(3145728)).build();
            var client=new ApiClient(transport);client.setBasePath(endpoint.toASCIIString());client.setBearerToken(token);api=new EvidenceReadControllerApi(client);
        }
        public void close(){server.stop(0);}
    }
}
