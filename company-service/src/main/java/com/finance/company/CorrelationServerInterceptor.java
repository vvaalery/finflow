package com.finance.company;

import io.grpc.Context;
import io.grpc.Contexts;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;

import java.util.UUID;
import java.util.regex.Pattern;

public class CorrelationServerInterceptor implements ServerInterceptor {

    public static final Metadata.Key<String> HEADER =
            Metadata.Key.of("x-correlation-id", Metadata.ASCII_STRING_MARSHALLER);
    private static final Context.Key<String> ID = Context.key("correlationId");
    private static final Pattern SAFE = Pattern.compile("[A-Za-z0-9-]{1,64}");

    public static String currentId() {
        String id = ID.get();
        return id == null ? "-" : id;
    }

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {
        String id = headers.get(HEADER);
        if (id == null || !SAFE.matcher(id).matches()) {
            id = UUID.randomUUID().toString();
        }
        return Contexts.interceptCall(Context.current().withValue(ID, id), call, headers, next);
    }
}
