package com.lyft.data.proxyserver;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import lombok.extern.slf4j.Slf4j;

import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.api.Response;
import org.eclipse.jetty.proxy.ProxyServlet;
import org.eclipse.jetty.util.Callback;
import org.eclipse.jetty.util.ssl.SslContextFactory;

@Slf4j
public class ProxyServletImpl extends ProxyServlet.Transparent {
  private ProxyHandler proxyHandler;

  public void setProxyHandler(ProxyHandler proxyHandler) {
    this.proxyHandler = proxyHandler;
    // This needs to be high as external clients may take longer to connect.
    this.setTimeout(TimeUnit.MINUTES.toMillis(1));
  }

  // Overriding this method to support ssl
  @Override
  protected HttpClient newHttpClient() {
    SslContextFactory sslFactory = new SslContextFactory.Client();
    sslFactory.setTrustAll(true);
    sslFactory.setStopTimeout(TimeUnit.SECONDS.toMillis(15));
    sslFactory.setSslSessionTimeout((int) TimeUnit.SECONDS.toMillis(15));

    HttpClient httpClient = new HttpClient(sslFactory);
    httpClient.setMaxConnectionsPerDestination(10000);
    httpClient.setConnectTimeout(TimeUnit.SECONDS.toMillis(60));
    return httpClient;
  }

  /** Customize the headers of forwarding proxy requests. */
  @Override
  protected void addProxyHeaders(HttpServletRequest request, Request proxyRequest) {
    super.addProxyHeaders(request, proxyRequest);
    if (proxyHandler != null) {
      proxyHandler.preConnection(request, proxyRequest);
    }
  }

  @Override
  protected String rewriteTarget(HttpServletRequest request) {
    if (proxyHandler != null) {
      Optional<String>  target = proxyHandler.rewriteTarget(request);
      if (target.isPresent()) {
        log.debug("Target : " + target);
        return target.get();
      }
    }
    return super.rewriteTarget(request);
  }

  /**
   * Customize the response returned from remote server.
   *
   * @param request
   * @param response
   * @param proxyResponse
   * @param buffer
   * @param offset
   * @param length
   * @param callback
   */
  protected void onResponseContent(
      HttpServletRequest request,
      HttpServletResponse response,
      Response proxyResponse,
      byte[] buffer,
      int offset,
      int length,
      Callback callback) {
    try {
      if (this._log.isDebugEnabled()) {
        this._log.debug(
            "[{}] proxying content to downstream: [{}] bytes", this.getRequestId(request), length);
      }
      if (this.proxyHandler != null) {
        proxyHandler.postConnection(request, response, buffer, offset, length, callback);
      } else {
        super.onResponseContent(request, response, proxyResponse, buffer, offset, length, callback);
      }
    } catch (Throwable var9) {
      callback.failed(var9);
    }
  }
}
