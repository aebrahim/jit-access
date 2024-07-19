//
// Copyright 2021 Google LLC
//
// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements.  See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership.  The ASF licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License.  You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.
//

package com.google.solutions.jitaccess.web;

import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonObjectParser;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.GenericData;
import com.google.auth.oauth2.ComputeEngineCredentials;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ImpersonatedCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.common.util.concurrent.UncheckedExecutionException;
import com.google.solutions.jitaccess.ApplicationVersion;
import com.google.solutions.jitaccess.apis.clients.*;
import com.google.solutions.jitaccess.catalog.Catalog;
import com.google.solutions.jitaccess.catalog.Logger;
import com.google.solutions.jitaccess.catalog.auth.GroupMapping;
import com.google.solutions.jitaccess.catalog.auth.Subject;
import com.google.solutions.jitaccess.catalog.auth.UserId;
import com.google.solutions.jitaccess.catalog.legacy.LegacyPolicy;
import com.google.solutions.jitaccess.catalog.legacy.LegacyPolicyLoader;
import com.google.solutions.jitaccess.catalog.policy.EnvironmentPolicy;
import com.google.solutions.jitaccess.catalog.policy.Policy;
import com.google.solutions.jitaccess.catalog.policy.PolicyDocument;
import com.google.solutions.jitaccess.util.NullaryOptional;
import jakarta.enterprise.context.RequestScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;
import jakarta.ws.rs.core.UriBuilder;
import jakarta.ws.rs.core.UriInfo;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.UnknownHostException;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Entry point for the application. Loads configuration and produces CDI beans.
 */
@Singleton
public class Application {
  private static final String CONFIG_IMPERSONATE_SA = "jitaccess.impersonateServiceAccount";
  private static final String CONFIG_DEBUG_MODE = "jitaccess.debug";
  private static final String CONFIG_PROJECT = "jitaccess.project";

  private final String projectId;
  private final String projectNumber;
  private final @NotNull GoogleCredentials applicationCredentials;
  private final @NotNull UserId applicationPrincipal;

  private final @NotNull Logger logger;

  /**
   * Configuration, based on app.yaml environment variables.
   */
  private final @NotNull ApplicationConfiguration configuration = new ApplicationConfiguration(System.getenv());

  /**
   * Cloud Identity/Workspace customer ID.
   */
  private final @NotNull String customerId;

  /**
   * Domain to use for groups.
   */
  private final @NotNull String groupsDomain;

  // -------------------------------------------------------------------------
  // Private helpers.
  // -------------------------------------------------------------------------

  private static HttpResponse getMetadata() throws IOException {
    var genericUrl = new GenericUrl(
      ComputeEngineCredentials.getMetadataServerUrl() +
        "/computeMetadata/v1/project/?recursive=true");

    var request = new NetHttpTransport()
      .createRequestFactory()
      .buildGetRequest(genericUrl);

    request.setParser(new JsonObjectParser(GsonFactory.getDefaultInstance()));
    request.getHeaders().set("Metadata-Flavor", "Google");
    request.setThrowExceptionOnExecuteError(true);

    try {
      return request.execute();
    }
    catch (UnknownHostException exception) {
      throw new IOException(
        "Cannot find the metadata server. This is likely because code is not running on Google Cloud.",
        exception);
    }
  }

  public boolean isRunningOnAppEngine() {
    return System.getenv().containsKey("GAE_SERVICE");
  }

  public boolean isRunningOnCloudRun() {
    return System.getenv().containsKey("K_SERVICE");
  }

  public String getBackendServiceId() {
    throw new RuntimeException("NIY");
  }

  // -------------------------------------------------------------------------
  // Public methods.
  // -------------------------------------------------------------------------

  public Application() {
    //
    // Create a log adapter. We can't rely on injection as we're not in the
    // scope of a specific request here.
    //
    this.logger = new JsonLogger(System.out);

    if (!this.configuration.isSmtpConfigured()) {
      logger.warn(
        EventIds.STARTUP,
        "The SMTP configuration is incomplete");
    }

    if (isRunningOnAppEngine() || isRunningOnCloudRun()) {
      //
      // Initialize using service account attached to AppEngine or Cloud Run.
      //
      try {
        GenericData projectMetadata =
          getMetadata().parseAs(GenericData.class);

        this.projectId = (String) projectMetadata.get("projectId");
        this.projectNumber = projectMetadata.get("numericProjectId").toString();

        var defaultCredentials = (ComputeEngineCredentials)GoogleCredentials.getApplicationDefault();
        this.applicationPrincipal = new UserId(defaultCredentials.getAccount());

        if (defaultCredentials.getScopes().containsAll(this.configuration.requiredOauthScopes())) {
          //
          // Default credential has all the right scopes, use it as-is.
          //
          this.applicationCredentials = defaultCredentials;
        }
        else {
          //
          // Extend the set of scopes to include required non-cloud APIs by
          // letting the service account impersonate itself.
          //
          this.applicationCredentials = ImpersonatedCredentials.create(
            defaultCredentials,
            this.applicationPrincipal.email,
            null,
            this.configuration.requiredOauthScopes().stream().toList(),
            0);
        }

        logger.info(
          EventIds.STARTUP,
          String.format("Running in project %s (%s) as %s, version %s",
            this.projectId,
            this.projectNumber,
            this.applicationPrincipal,
            ApplicationVersion.VERSION_STRING));
      }
      catch (IOException e) {
        logger.error(
          EventIds.STARTUP,
          "Failed to lookup instance metadata", e);
        throw new RuntimeException("Failed to initialize runtime environment", e);
      }
    }
    else if (isDebugModeEnabled()) {
      //
      // Initialize using development settings and credential.
      //
      this.projectId = System.getProperty(CONFIG_PROJECT, "dev");
      this.projectNumber = "0";

      try {
        var defaultCredentials = GoogleCredentials.getApplicationDefault();

        var impersonateServiceAccount = System.getProperty(CONFIG_IMPERSONATE_SA);
        if (impersonateServiceAccount != null && !impersonateServiceAccount.isEmpty()) {
          //
          // Use the application default credentials (ADC) to impersonate a
          // service account. This step is necessary to ensure we have a
          // credential for the right set of scopes, and that we're not running
          // with end-user credentials.
          //
          this.applicationCredentials = ImpersonatedCredentials.create(
            defaultCredentials,
            impersonateServiceAccount,
            null,
            this.configuration.requiredOauthScopes().stream().toList(),
            0);

          //
          // If we lack impersonation permissions, ImpersonatedCredentials
          // will keep retrying until the call timeout expires. The effect
          // is that the application seems hung.
          //
          // To prevent this from happening, force a refresh here. If the
          // refresh fails, fail application startup.
          //
          this.applicationCredentials.refresh();
          this.applicationPrincipal = new UserId(impersonateServiceAccount);
        }
        else if (defaultCredentials instanceof ServiceAccountCredentials) {
          //
          // Use ADC as-is.
          //
          this.applicationCredentials = defaultCredentials;
          this.applicationPrincipal = new UserId(
              ((ServiceAccountCredentials) this.applicationCredentials).getServiceAccountUser());
        }
        else {
          throw new RuntimeException(String.format(
            "You're using user credentials as application default "
              + "credentials (ADC). Use -D%s=<service-account-email> to impersonate "
              + "a service account during development",
            CONFIG_IMPERSONATE_SA));
        }
      }
      catch (IOException e) {
        throw new RuntimeException("Failed to lookup application credentials", e);
      }

      logger.warn(
        EventIds.STARTUP,
        String.format("Running in development mode as %s", this.applicationPrincipal));
    }
    else {
      throw new RuntimeException(
        "Application is not running on AppEngine or Cloud Run, and debug mode is disabled. Aborting startup");
    }

    //
    // Load essential configuration.
    //
    this.customerId = NullaryOptional.ifTrue(configuration.customerId.isValid())
      .map(() -> configuration.customerId.value())
      .orElseThrow(() -> new RuntimeException(
        String.format(
          "The environment variable '%s' must be set to the customer ID " +
            "of a Cloud Identity or Workspace account",
          configuration.customerId.key())));
    this.groupsDomain = NullaryOptional.ifTrue(configuration.groupsDomain.isValid())
      .map(() -> configuration.groupsDomain.value())
      .orElseThrow(() -> new RuntimeException(
        String.format(
          "The environment variable '%s' must contain a (verified) domain name",
          configuration.groupsDomain.key())));
  }

  public boolean isDebugModeEnabled() {
    return Boolean.getBoolean(CONFIG_DEBUG_MODE);
  }

  public UriBuilder createAbsoluteUriBuilder(@NotNull UriInfo uriInfo) {
    return uriInfo
      .getBaseUriBuilder()
      .scheme(isRunningOnAppEngine() || isRunningOnCloudRun() ? "https" : "http");
  }

  public String getProjectId() {
    return projectId;
  }

  public String getProjectNumber() {
    return projectNumber;
  }

  //---------------------------------------------------------------------------
  // Producers.
  //---------------------------------------------------------------------------

  @Produces
  @Singleton
  public @NotNull Diagnosable produceDevModeDiagnosable() {
    final String name = "DevModeIsDisabled";
    return new Diagnosable() {
      @Override
      public Collection<DiagnosticsResult> diagnose() {
        if (!isDebugModeEnabled()) {
          return List.of(new DiagnosticsResult(name));
        }
        else {
          return List.of(
            new DiagnosticsResult(
              name,
              false,
              "Application is running in development mode"));
        }
      }
    };
  }

  @Produces
  public @NotNull CloudIdentityGroupsClient.Options produceCloudIdentityGroupsClientOptions() {
    return new CloudIdentityGroupsClient.Options(this.customerId);
  }

  @Produces
  public GoogleCredentials produceApplicationCredentials() {
    return applicationCredentials;
  }

  @Produces
  public @NotNull HttpTransport.Options produceHttpTransportOptions() {
    return new HttpTransport.Options(
      this.configuration.backendConnectTimeout.value(),
      this.configuration.backendReadTimeout.value(),
      this.configuration.backendWriteTimeout.value());
  }

  @Produces
  public @NotNull RequestContextLogger produceLogger(@NotNull RequestContext context) {
    return new RequestContextLogger(context);
  }

  @Produces
  public @NotNull Subject produceSubject(@NotNull RequestContext context) {
    return context.subject();
  }

  @Produces
  @RequestScoped
  public @NotNull Catalog produceCatalog(
    @NotNull Subject subject,
    @NotNull LazyCatalogSource catalogSource
  ) {
    return new Catalog(
      subject,
      catalogSource);
  }

  @Produces
  @Singleton
  public @NotNull GroupMapping produceGroupMapping() {
    return new GroupMapping(this.groupsDomain);
  }

  @Produces
  @Singleton
  public @NotNull LazyCatalogSource produceEnvironments(
    @NotNull GroupMapping groupMapping,
    @NotNull CloudIdentityGroupsClient groupsClient
    ) {
    var configuredEnvironments = this.configuration.environmentNames()
      .stream()
      .filter(name -> configuration.environment(name).isValid())
      .collect(Collectors.toMap(name -> name, configuration::environment));

    //
    // Prepare configuration for all environments, but don't load their
    // policy yet (because that's expensive).
    //
    final var configurations = new HashMap<String, EnvironmentConfiguration>();
    for (var environment : configuredEnvironments.entrySet()) {
      var value = environment.getValue().value();

      var matcher = ApplicationConfiguration.SERVICE_ACCOUNT_EMAIL_ADDRESS.matcher(value);
      if (value.startsWith("file:")) {
        //
        // Value contains a file path, which is only allowed for development.
        //
        if (!isDebugModeEnabled()) {
          this.logger.warn(
            EventIds.LOAD_ENVIRONMENT,
            "Environment '%s' uses file-based policy, ignoring environment as " +
              "file-based policies are only allowed in debug mode",
            environment.getKey());
          break;
        }

        configurations.put(
          environment.getKey(),
          new EnvironmentConfiguration(
            this.applicationCredentials,
            () -> {
              try {
                return PolicyDocument.fromFile(new File(new URL(value).toURI())).policy();
              }
              catch (Exception e) {
                throw new UncheckedExecutionException(e);
              }
            }
          ));
      }
      else if (matcher.matches()) {
        //
        // Value contains the email address of a service account.
        //
        // Impersonate the service account and use it for:
        //
        //  - Loading the policy from Secret Manager
        //  - Provisioning access
        //
        var environmentServiceAccountEmail = value;
        var environmentCredentials = ImpersonatedCredentials.create(
          this.applicationCredentials,
          environmentServiceAccountEmail,
          null,
          List.of(ResourceManagerClient.OAUTH_SCOPE), // No other scopes needed.
          0);

        //
        // Load policy from secret manager using the environment-specific
        // credentials (not the application credentials!).
        //
        // The secret path is based on a convention and can't be customized.
        //
        var secretPath = String.format(
          "projects/%s/secrets/%s/versions/latest",
          matcher.group(2),
          matcher.group(1));

        configurations.put(
          environment.getKey(),
          new EnvironmentConfiguration(
            environmentCredentials,
            () -> {
              //
              // If we lack impersonation permissions, ImpersonatedCredentials
              // will keep retrying until the call timeout expires. The effect
              // is that the application seems hung.
              //
              // To prevent this from happening, force a refresh here.
              //
              try {
                environmentCredentials.refresh();
              }
              catch (Exception e) {
                this.logger.error(
                  EventIds.LOAD_ENVIRONMENT,
                  "Impersonating service account '%s' of environment '%s' failed, possibly caused " +
                    "by insufficient IAM permissions. Make sure that the service account '%s' has " +
                    "the roles/iam.serviceAccountTokenCreator role on '%s'.",
                  environmentServiceAccountEmail,
                  environment.getKey(),
                  this.applicationPrincipal,
                  environmentServiceAccountEmail);

                throw new RuntimeException("Loading environment configuration failed, check logs for details");
              }

              try {
                var secretClient = new SecretManagerClient(
                  environmentCredentials,
                  produceHttpTransportOptions());

                return PolicyDocument
                  .fromString(
                    secretClient.accessSecret(secretPath),
                    new Policy.Metadata(secretPath, Instant.now()))
                  .policy();
              }
              catch (Exception e) {
                throw new UncheckedExecutionException(e);
              }
            }
          ));
      }
    }

    if (this.configuration.legacyCatalog.isValid() &&
      this.configuration.legacyCatalog.value().equalsIgnoreCase("ASSETINVENTORY") &&
      this.configuration.legacyScope.isValid()) {

      //
      // Load an extra environment that surfaces JIT Access 1.x roles.
      //
      var legacyLoader = new LegacyPolicyLoader(
        () -> new ResourceManagerClient(this.applicationCredentials, produceHttpTransportOptions()),
        () -> new AssetInventoryClient(this.applicationCredentials, produceHttpTransportOptions()));

      configurations.put(
        LegacyPolicy.NAME,
        new EnvironmentConfiguration(
          this.applicationCredentials, // Use app service account, as in 1.x
          () -> {
            try {
              return legacyLoader.load(
                this.configuration.legacyProjectsQuery.value(),
                this.configuration.legacyScope.value(),
                this.configuration.legacyActivationTimeout.value(),
                this.configuration.legacyJustificationPattern.value(),
                this.configuration.legacyJustificationHint.value(),
                logger);
            }
            catch (Exception e) {
              throw new UncheckedExecutionException(e);
            }
          }));
    }

    return new LazyCatalogSource(
      configurations.keySet(),
      envName -> configurations.get(envName).loadPolicy.get(),
      policy -> new ResourceManagerClient(
        configurations.get(policy.name()).resourceCredentials,
        produceHttpTransportOptions()),
      groupMapping,
      groupsClient,
      isDebugModeEnabled()
        ? Duration.ofSeconds(20)
        : this.configuration.environmentCacheTimeout.value(),
      logger);
  }

  /**
   * Configuration for an environment.
   *
   * @param resourceCredentials environment-specific credentials to use for provisioning
   * @param loadPolicy callback for (lazily) loading the policy
   */
  private record EnvironmentConfiguration(
    @NotNull GoogleCredentials resourceCredentials,
    @NotNull Supplier<EnvironmentPolicy> loadPolicy
  ) {}
}
