package com.yugabyte.yw.models.helpers.telemetry;

import static io.swagger.annotations.ApiModelProperty.AccessMode.READ_WRITE;
import static play.mvc.Http.Status.BAD_REQUEST;
import static play.mvc.Http.Status.INTERNAL_SERVER_ERROR;

import com.yugabyte.yw.common.ApiHelper;
import com.yugabyte.yw.common.PlatformServiceException;
import com.yugabyte.yw.common.Util;
import com.yugabyte.yw.common.config.RuntimeConfGetter;
import com.yugabyte.yw.models.helpers.TelemetryProviderService;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;

@Data
@EqualsAndHashCode(callSuper = true)
@ApiModel(description = "Loki Config")
@Slf4j
public class LokiConfig extends TelemetryProviderConfig {

  @ApiModelProperty(value = "End Point", accessMode = READ_WRITE, required = true)
  private String endpoint;

  @ApiModelProperty(value = "Auth Type", accessMode = READ_WRITE, required = true)
  private AuthCredentials.AuthType authType;

  @ApiModelProperty(value = "Organization/Tenant ID", accessMode = READ_WRITE)
  private String organizationID;

  @ApiModelProperty(value = "Basic Auth Credentials", accessMode = READ_WRITE)
  private AuthCredentials.BasicAuthCredentials basicAuth;

  public LokiConfig() {
    setType(ProviderType.LOKI);
  }

  @Override
  public void validate(ApiHelper apiHelper, RuntimeConfGetter confGetter) {

    if (endpoint == null || endpoint.isEmpty()) {
      throw new PlatformServiceException(BAD_REQUEST, "Loki endpoint is required.");
    }

    if (authType == null) {
      throw new PlatformServiceException(BAD_REQUEST, "Loki auth type is required.");
    }

    AuthCredentials.checkBasicAuthCredentials(authType, basicAuth);

    AuthCredentials.bearerTokenNotSupported(authType);

    if (endpoint.endsWith("/")) {
      endpoint = endpoint.substring(0, endpoint.length() - 1);
    }

    if (endpoint.endsWith(TelemetryProviderService.LOKI_PUSH_ENDPOINT)) {
      // remove the suffix
      endpoint =
          endpoint.substring(
              0, endpoint.length() - TelemetryProviderService.LOKI_PUSH_ENDPOINT.length());
    }

    if (TelemetryProviderUtil.skipConnectivityValidation(confGetter)) {
      log.info("Skipping Loki endpoint validation as per config.");
      return;
    }
    HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();

    int maxRetries = 5;
    boolean isReady = false;
    Exception lastException = null;
    int lastStatusCode = -1;

    for (int i = 1; i <= maxRetries; i++) {
      try {
        URL validatedBaseUrl = Util.validateAndGetURL(endpoint, true);
        URI readyUri = validatedBaseUrl.toURI().resolve("/ready");

        HttpRequest.Builder requestBuilder =
            HttpRequest.newBuilder().uri(readyUri).timeout(Duration.ofSeconds(3)).GET();

        if (authType == AuthCredentials.AuthType.BasicAuth) {
          String authHeader =
              "Basic "
                  + Base64.getEncoder()
                      .encodeToString(
                          (basicAuth.getUsername() + ":" + basicAuth.getPassword()).getBytes());
          requestBuilder.header("Authorization", authHeader);
        }

        HttpRequest request = requestBuilder.build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        int statusCode = response.statusCode();
        String body = response.body();

        lastStatusCode = statusCode;

        if (statusCode == 200 && "Ready".equalsIgnoreCase(body.trim())) {
          isReady = true;
          break;
        }

        log.warn(
            "Loki not ready yet (attempt {} of {}). Status: {}, Body: {}",
            i,
            maxRetries,
            statusCode,
            body);
      } catch (Exception e) {
        lastException = e;
        log.warn(
            "Error checking Loki readiness (attempt {} of {}): {}", i, maxRetries, e.getMessage());
      }

      try {
        Thread.sleep(1000);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new PlatformServiceException(
            INTERNAL_SERVER_ERROR,
            "Loki validation thread interrupted during retry delay: " + e.getMessage());
      }
    }

    if (!isReady) {
      StringBuilder errorMsg =
          new StringBuilder("Loki endpoint is not ready after " + maxRetries + " attempts.");
      if (lastStatusCode != -1) {
        errorMsg.append(" Last status code: ").append(lastStatusCode);
      }
      if (lastException != null) {
        errorMsg.append(", error: ").append(lastException.getMessage());
      }

      throw new PlatformServiceException(BAD_REQUEST, errorMsg.toString());
    }
    log.info("Successfully validated Loki endpoint and connectivity.");
  }

  @Override
  public void validate(ApiHelper apiHelper) {
    validate(apiHelper, null);
  }
}
