package com.facebook.presto.dispatcher;

import com.facebook.airlift.http.client.HttpClient;
import com.facebook.airlift.http.client.Request;
import com.facebook.airlift.http.client.jetty.JettyHttpClient;
import com.facebook.airlift.json.JsonCodec;
import com.facebook.presto.Session;
import com.facebook.presto.client.QueryResults;
import com.google.common.base.Joiner;
import com.google.common.util.concurrent.ListenableFuture;

import javax.inject.Inject;
import javax.ws.rs.core.UriBuilder;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLEncoder;

import static com.facebook.airlift.http.client.FullJsonResponseHandler.createFullJsonResponseHandler;
import static com.facebook.airlift.http.client.Request.Builder.preparePost;
import static com.facebook.airlift.http.client.StaticBodyGenerator.createStaticBodyGenerator;
import static com.facebook.airlift.json.JsonCodec.jsonCodec;
import static com.facebook.presto.client.PrestoHeaders.PRESTO_CATALOG;
import static com.facebook.presto.client.PrestoHeaders.PRESTO_CLIENT_INFO;
import static com.facebook.presto.client.PrestoHeaders.PRESTO_CLIENT_TAGS;
import static com.facebook.presto.client.PrestoHeaders.PRESTO_EXTRA_CREDENTIAL;
import static com.facebook.presto.client.PrestoHeaders.PRESTO_LANGUAGE;
import static com.facebook.presto.client.PrestoHeaders.PRESTO_PREPARED_STATEMENT;
import static com.facebook.presto.client.PrestoHeaders.PRESTO_QUERY_ID;
import static com.facebook.presto.client.PrestoHeaders.PRESTO_SCHEMA;
import static com.facebook.presto.client.PrestoHeaders.PRESTO_SESSION;
import static com.facebook.presto.client.PrestoHeaders.PRESTO_SLUG;
import static com.facebook.presto.client.PrestoHeaders.PRESTO_SOURCE;
import static com.facebook.presto.client.PrestoHeaders.PRESTO_TIME_ZONE;
import static com.facebook.presto.client.PrestoHeaders.PRESTO_TRACE_TOKEN;
import static com.facebook.presto.client.PrestoHeaders.PRESTO_TRANSACTION_ID;
import static com.facebook.presto.client.PrestoHeaders.PRESTO_USER;
import static java.nio.charset.StandardCharsets.UTF_8;

public class QueryForwarder
{
    private static final JsonCodec<QueryResults> QUERY_RESULTS_CODEC = jsonCodec(QueryResults.class);

    private final HttpClient httpClient;

    @Inject
    public QueryForwarder()
    {
        this.httpClient = new JettyHttpClient();
    }

    public ListenableFuture<?> waitForForwarding(URI forwardingUri, Session session, String slug, String queryText)
    {
        forwardingUri = UriBuilder.fromUri(forwardingUri).replacePath("v1/statement").build();
        return httpClient.executeAsync(buildForwardingRequest(forwardingUri, session, slug, queryText), createFullJsonResponseHandler(QUERY_RESULTS_CODEC));
    }

    private static Request buildForwardingRequest(URI uri, Session session, String slug, String queryText)
    {
        Request.Builder builder = preparePost()
                .setUri(uri)
                .setBodyGenerator(createStaticBodyGenerator(queryText, UTF_8))
                .addHeader(PRESTO_QUERY_ID, session.getQueryId().toString())
                .addHeader(PRESTO_SLUG, slug)
                .addHeader(PRESTO_USER, session.getUser())
                .addHeader(PRESTO_TIME_ZONE, session.getTimeZoneKey().getId())
                .addHeader(PRESTO_LANGUAGE, session.getLocale().toLanguageTag());

        if (session.getSource().isPresent()) {
            builder.addHeader(PRESTO_SOURCE, session.getSource().get());
        }
        if (session.getCatalog().isPresent()) {
            builder.addHeader(PRESTO_CATALOG, session.getCatalog().get());
        }
        if (session.getSchema().isPresent()) {
            builder.addHeader(PRESTO_SCHEMA, session.getSchema().get());
        }
        if (session.getTraceToken().isPresent()) {
            builder.addHeader(PRESTO_TRACE_TOKEN, session.getTraceToken().get());
        }
        session.getSystemProperties().forEach((k, v) -> {
           builder.addHeader(PRESTO_SESSION, k + "=" + v);
        });
        session.getPreparedStatements().forEach((k, v) -> {
           builder.addHeader(PRESTO_PREPARED_STATEMENT, urlEncode(k + "=" + v));
        });
        if (session.getClientInfo().isPresent()) {
            builder.addHeader(PRESTO_CLIENT_INFO, session.getClientInfo().get());
        }
        if (!session.getClientTags().isEmpty()) {
            builder.addHeader(PRESTO_CLIENT_TAGS, Joiner.on(",").join(session.getClientTags()));
        }
        session.getIdentity().getExtraCredentials().forEach((k, v) -> {
            builder.addHeader(PRESTO_EXTRA_CREDENTIAL, k + "=" + v);
        });
        // TODO: Implement Transactions
        // TODO: Implement Session Functions
        // TODO: Implement Resource Estimates
        // TODO: Implement Identity and Role Headers

        return builder.build();
    }

    private static String urlEncode(String value)
    {
        try {
            return URLEncoder.encode(value, "UTF-8");
        }
        catch (UnsupportedEncodingException e) {
            throw new AssertionError(e);
        }
    }

}
