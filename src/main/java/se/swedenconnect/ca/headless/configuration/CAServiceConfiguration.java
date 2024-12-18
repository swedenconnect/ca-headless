/*
 * Copyright 2024.  Agency for Digital Government (DIGG)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package se.swedenconnect.ca.headless.configuration;

import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.cms.CMSException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationEventPublisherAware;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Profile;
import se.swedenconnect.ca.headless.ca.P7BCertStore;
import se.swedenconnect.ca.headless.ca.HeadlessCAServices;
import se.swedenconnect.ca.engine.ca.repository.CARepository;
import se.swedenconnect.ca.headless.ca.db.DBCARepository;
import se.swedenconnect.ca.headless.ca.db.DBCRLJPARepository;
import se.swedenconnect.ca.headless.ca.db.DBCRLMetadataRepository;
import se.swedenconnect.ca.headless.ca.db.DBJPARepository;
import se.swedenconnect.ca.service.base.configuration.BasicServiceConfig;
import se.swedenconnect.ca.service.base.ca.CAServices;
import se.swedenconnect.ca.service.base.configuration.instance.InstanceConfiguration;
import se.swedenconnect.ca.service.base.ca.LocalJsonCARepository;
import se.swedenconnect.ca.service.base.configuration.keys.PkiCredentialFactory;
import se.swedenconnect.ca.service.base.configuration.properties.CAConfigData;

import java.io.File;
import java.io.IOException;
import java.security.cert.CertificateException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * CA Service configuration class that generates the CA services beans for each CA instance as defined by the configuration properties
 * for each instance.
 *
 * @author Martin Lindström (martin@idsec.se)
 * @author Stefan Santesson (stefan@idsec.se)
 */
@Slf4j
@Configuration
public class CAServiceConfiguration implements ApplicationEventPublisherAware {

  private ApplicationEventPublisher applicationEventPublisher;

  /**
   * The CA services bean provide all CA services as defined by the configuration of each instance
   * @param instanceConfiguration instance configuration properties
   * @param pkiCredentialFactory the pkcs11 provider if such provider is configured (or null)
   * @param basicServiceConfig basic service configuration data
   * @param caRepositoryMap CA repositories for each instance
   * @param p7BCertStore Provider of the CA repository PKCS7 certs only file for each instance
   * @return {@link CAServices}
   * @throws IOException error parsing data
   * @throws CMSException error handling CMS data
   * @throws CertificateException error parsing certificate data
   */
  @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
  @Bean CAServices caServices(InstanceConfiguration instanceConfiguration, PkiCredentialFactory pkiCredentialFactory,
    BasicServiceConfig basicServiceConfig, Map<String, CARepository> caRepositoryMap, P7BCertStore p7BCertStore
    ) throws IOException, CMSException, CertificateException {
    CAServices caServices = new HeadlessCAServices(instanceConfiguration, pkiCredentialFactory, basicServiceConfig,
      caRepositoryMap, p7BCertStore, applicationEventPublisher);
    return caServices;
  }

  @Override public void setApplicationEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
    this.applicationEventPublisher = applicationEventPublisher;
  }

  /**
   * Provides CA repository implementations for each instance
   * @param basicServiceConfig basic service configuration
   * @param instanceConfiguration configuration properties for each instance
   * @return map of {@link CARepository} for each instance
   * @throws IOException error parsing data
   */
  @Profile({"nodb"})
  @DependsOn("BasicServiceConfig")
  @Bean Map<String, CARepository> fileCaRepositoryMap (
    BasicServiceConfig basicServiceConfig,
    InstanceConfiguration instanceConfiguration
  ) throws IOException {
    Map<String, CAConfigData> instanceConfigMap = instanceConfiguration.getInstanceConfigMap();
    Set<String> instances = instanceConfigMap.keySet();
    Map<String, CARepository> caRepositoryMap = new HashMap<>();
    for (String instance: instances) {
      File repositoryDir = new File(basicServiceConfig.getDataStoreLocation(), "instances/"+instance+"/repository");
      log.info("USING A JSON FILE BASED LOCAL REPOSITORY for instance {}", instance);
      File crlFile = new File(repositoryDir, instance + ".crl");
      File repoFile = new File(repositoryDir, instance + "-repo.json");
      CARepository caRepository= new LocalJsonCARepository(crlFile, repoFile);
      caRepositoryMap.put(instance, caRepository);
    }
    return caRepositoryMap;
  }

  /**
   * Provides a DB CA repository implementations for each instance
   * @param basicServiceConfig basic service configuration
   * @param instanceConfiguration configuration properties for each instance
   * @param dbRepository CA repository database table
   * @param dbcrljpaRepository CRL metadata repository database table
   * @return map of {@link CARepository} for each instance
   * @throws IOException error parsing data
   */
  @DependsOn("BasicServiceConfig")
  @Profile({"!nodb"})
  @Bean Map<String, CARepository> dbCaRepositoryMap (
    BasicServiceConfig basicServiceConfig,
    InstanceConfiguration instanceConfiguration,
    DBJPARepository dbRepository,
    DBCRLJPARepository dbcrljpaRepository
  ) throws IOException {
    Map<String, CAConfigData> instanceConfigMap = instanceConfiguration.getInstanceConfigMap();
    Set<String> instances = instanceConfigMap.keySet();
    Map<String, CARepository> caRepositoryMap = new HashMap<>();
    for (String instance: instances) {
      File repositoryDir = new File(basicServiceConfig.getDataStoreLocation(), "instances/"+instance+"/repository");
      log.info("Using a DB repository for instance {}", instance);
      File crlFile = new File(repositoryDir, instance + ".crl");
      CARepository caRepository= new DBCARepository(crlFile, dbRepository, instance, new DBCRLMetadataRepository(dbcrljpaRepository));
      caRepositoryMap.put(instance, caRepository);
    }
    return caRepositoryMap;
  }

}
