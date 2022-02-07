/*
 * Copyright (c) 2021. Agency for Digital Government (DIGG)
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

package se.swedenconnect.ca.sigvaltrust.configuration;

import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.cms.CMSException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationEventPublisherAware;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Profile;
import se.swedenconnect.ca.sigvaltrust.ca.P7BCertStore;
import se.swedenconnect.ca.sigvaltrust.ca.HeadlessCAServices;
import se.swedenconnect.ca.engine.ca.repository.CARepository;
import se.swedenconnect.ca.service.base.configuration.BasicServiceConfig;
import se.swedenconnect.ca.service.base.configuration.instance.CAServices;
import se.swedenconnect.ca.service.base.configuration.instance.InstanceConfiguration;
import se.swedenconnect.ca.service.base.configuration.instance.LocalJsonCARepository;
import se.swedenconnect.ca.service.base.configuration.properties.CAConfigData;
import se.swedenconnect.opensaml.pkcs11.PKCS11Provider;

import java.io.File;
import java.io.IOException;
import java.security.cert.CertificateException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Description
 *
 * @author Martin Lindström (martin@idsec.se)
 * @author Stefan Santesson (stefan@idsec.se)
 */
@Slf4j
@Profile("headless")
@Configuration
public class CAServiceConfiguration implements ApplicationEventPublisherAware {

  private ApplicationEventPublisher applicationEventPublisher;

  @Bean CAServices caServices(InstanceConfiguration instanceConfiguration, PKCS11Provider pkcs11Provider,
    BasicServiceConfig basicServiceConfig, Map<String, CARepository> caRepositoryMap, P7BCertStore p7BCertStore
    ) throws IOException, CMSException, CertificateException {
    CAServices caServices = new HeadlessCAServices(instanceConfiguration, pkcs11Provider, basicServiceConfig,
      caRepositoryMap, p7BCertStore, applicationEventPublisher);
    return caServices;
  }

  @Override public void setApplicationEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
    this.applicationEventPublisher = applicationEventPublisher;
  }

  @Profile({"headless"})
  @DependsOn("BasicServiceConfig")
  @Bean Map<String, CARepository> caRepositoryMap (
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

}
