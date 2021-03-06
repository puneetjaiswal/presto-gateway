package com.lyft.data.proxyserver.wrapper;

import static org.testng.Assert.assertEquals;

import com.google.common.io.CharStreams;
import com.lyft.data.proxyserver.ProxyHandler;
import com.lyft.data.proxyserver.ProxyServer;
import com.lyft.data.proxyserver.ProxyServerConfiguration;
import com.lyft.data.proxyserver.TestProxyServer;
import com.squareup.okhttp.mockwebserver.MockResponse;
import com.squareup.okhttp.mockwebserver.MockWebServer;
import com.squareup.okhttp.mockwebserver.RecordedRequest;
import java.util.Random;
import javax.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import org.testng.annotations.Test;

@Slf4j
public class TestMultiReadRequestWrapper {
  private final OkHttpClient httpClient = new OkHttpClient();
  private static final String REQUEST_ORIGINAL_TEXT = "SELECT 1";

  @Test
  public void testMultiReadRequestWrapper() throws Exception {
    int backendPort = 30000 + new Random().nextInt(1000);

    // setting up mocked backend
    MockWebServer backend = new MockWebServer();
    backend.enqueue(new MockResponse().setBody("RCVD"));
    backend.play(backendPort);

    // Setting up proxy in front of a mocked backend
    int serverPort = backendPort + 1;
    ProxyServerConfiguration config =
        TestProxyServer.buildConfig(backend.getUrl("/").toString(), serverPort);

    try (ProxyServer proxyServer =
        new ProxyServer(
            config,
            new ProxyHandler() {
              public void preConnection(HttpServletRequest request,
                                        org.eclipse.jetty.client.api.Request proxyRequest) {
                if (request instanceof MultiReadHttpServletRequest) {
                  MultiReadHttpServletRequest req = (MultiReadHttpServletRequest) request;
                  try {
                    assertEquals(CharStreams.toString(req.getReader()), REQUEST_ORIGINAL_TEXT);
                  } catch (Exception e) {
                    log.error(e.getMessage(), e);
                  }
                }
              }
            })) {
      // starting proxy-server
      proxyServer.start();

      RequestBody requestBody =
          RequestBody.create(
              MediaType.parse("application/json; charset=utf-8"), REQUEST_ORIGINAL_TEXT);
      Request request =
          new Request.Builder().url("http://localhost:" + serverPort).post(requestBody).build();
      httpClient.newCall(request).execute();
      RecordedRequest recordedRequest = backend.takeRequest();
      assertEquals(recordedRequest.getUtf8Body(), REQUEST_ORIGINAL_TEXT);
    } finally {
      backend.shutdown();
    }
  }
}
