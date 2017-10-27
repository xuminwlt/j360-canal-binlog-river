package me.j360.binlog.canal.client.manager;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.RequestLine;
import org.apache.http.entity.ContentType;
import org.apache.http.nio.entity.NStringEntity;
import org.apache.http.util.EntityUtils;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;


@Component
public class ElasticSearchTemplate {


    @Autowired
    private RestClient restClient;

    private final static String UPDATE_DOC = "{\"doc\" : __source__}";

    public void add(String preTypeURL, String id, String source) {
        Map<String, String> params = Collections.emptyMap();
        // update
        HttpEntity entity = new NStringEntity(source, ContentType.APPLICATION_JSON);
        Response response = null;
        try {
            response = restClient.performRequest("PUT", preTypeURL + id + "/_create", params, entity);
            RequestLine requestLine = response.getRequestLine();
            HttpHost host = response.getHost();
            int statusCode = response.getStatusLine().getStatusCode();
            Header[] headers = response.getHeaders();
            String responseBody = EntityUtils.toString(response.getEntity());

            System.out.println("------");
            System.out.println(responseBody);
            System.out.println("------");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void update(String preTypeURL, String id, String source) {
        Map<String, String> params = Collections.emptyMap();
        // update
        HttpEntity entity = new NStringEntity(UPDATE_DOC.replace("__source__", source), ContentType.APPLICATION_JSON);
        Response response = null;
        try {
            response = restClient.performRequest("POST", preTypeURL + id + "/_update", params, entity);
            RequestLine requestLine = response.getRequestLine();
            HttpHost host = response.getHost();
            int statusCode = response.getStatusLine().getStatusCode();
            Header[] headers = response.getHeaders();
            String responseBody = EntityUtils.toString(response.getEntity());

            System.out.println("------");
            System.out.println(responseBody);
            System.out.println("------");
        } catch (IOException e) {
            e.printStackTrace();
        }


    }

    public void bulk(String request) {
        Map<String, String> params = Collections.emptyMap();
        // bulk
        HttpEntity entity = new NStringEntity(request, ContentType.APPLICATION_JSON);
        Response response = null;
        try {
            response = restClient.performRequest("POST", "/_bulk", params, entity);
            RequestLine requestLine = response.getRequestLine();
            HttpHost host = response.getHost();
            int statusCode = response.getStatusLine().getStatusCode();
            Header[] headers = response.getHeaders();
            String responseBody = EntityUtils.toString(response.getEntity());

            System.out.println("------");
            System.out.println(responseBody);
            System.out.println("------");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
