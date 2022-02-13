/*
 * Copyright (c) 2021-2022.  Agency for Digital Government (DIGG)
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

package se.swedenconnect.ca.tools.repomigration.configuration;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import se.swedenconnect.ca.engine.ca.repository.CARepository;
import se.swedenconnect.ca.service.base.configuration.properties.CAConfigData;
import se.swedenconnect.ca.tools.repomigration.repo.MergeCARepository;
import se.swedenconnect.ca.tools.repomigration.repo.RepoMergeConfiguration;
import se.swedenconnect.ca.tools.repomigration.repo.RepositoryGroup;
import se.swedenconnect.ca.tools.repomigration.repo.db.MergeDBCARepository;
import se.swedenconnect.ca.tools.repomigration.repo.db.DBJPARepository;
import se.swedenconnect.ca.tools.repomigration.repo.json.MergeJsonCARepository;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * CA Service configuration class that generates the CA services beans for each CA instance as defined by the configuration properties
 * for each instance.
 *
 * @author Martin Lindström (martin@idsec.se)
 * @author Stefan Santesson (stefan@idsec.se)
 */

@Slf4j
@Configuration
public class CARepositoryConfiguration {


  @Bean
  @Profile("!test")
  Map<String, RepositoryGroup> caRepositoryMap (
    RepoMergeConfiguration repoMergeConfiguration, DBJPARepository dbRepositoryEngine) throws IOException {
    List<String> instances = repoMergeConfiguration.getInstanceNameList();
    Map<String, RepositoryGroup> caRepositoryMap = new HashMap<>();
    System.out.println("Configuring CA Repositories ...");
    for (String instance: instances) {
      File repositoryDir = new File(repoMergeConfiguration.getDataLocation(), "instances/"+instance+"/repository");
      File repoFile = new File(repositoryDir, instance + "-repo.json");
      MergeCARepository caRepository= new MergeJsonCARepository(repoFile);
      MergeCARepository dbRepository= new MergeDBCARepository(dbRepositoryEngine, instance);
      caRepositoryMap.put(instance, RepositoryGroup.builder()
          .fileRepository(caRepository)
          .dbRepository(dbRepository)
        .build());
      if (log.isInfoEnabled()){
        log.info("Initializing repository for instance {} ({} JSON file records - {}  DB records", instance, caRepository.getCertificateCount(false), dbRepository.getCertificateCount(false));
      }
    }
    return caRepositoryMap;
  }

  @Bean
  RepoMergeConfiguration repoMergeConfiguration(
    @Value("${ca-service.config.data-directory:#{null}}") String configLocation,
    CAServiceProperties caServiceProperties) {
    RepoMergeConfiguration repoMergeConfiguration = new RepoMergeConfiguration();
    if (StringUtils.isNotBlank(configLocation)) {
      repoMergeConfiguration.setDataLocation(new File(
        configLocation.endsWith("/")
          ? configLocation.substring(0, configLocation.length() - 1)
          : configLocation
      ));
    }
    else {
      repoMergeConfiguration.setDataLocation(new File(System.getProperty("user.dir"), "target"));
      if (!repoMergeConfiguration.getDataLocation().exists()) {
        repoMergeConfiguration.getDataLocation().mkdirs();
      }
    }

    final Map<String, CAConfigData> caConf = caServiceProperties.getConf();
    if (caConf == null || caConf.isEmpty()){
      repoMergeConfiguration.setInstanceNameList(new ArrayList<>());
      return repoMergeConfiguration;
    }
    final List<String> caPropInstanceNameList = caConf.keySet().stream()
      .filter(s -> !s.equalsIgnoreCase("default"))
      .collect(Collectors.toList());

/*

    File instancesDir = new File(repoMergeConfiguration.getDataLocation(), "instances");
    List<String> instanceNameList = new ArrayList<>();
    for (String instanceName : caPropInstanceNameList){
      File instanceDir = new File(instancesDir, instanceName);
      File repoDir = new File(instanceDir, "repository");
      if (Objects.requireNonNull(repoDir.listFiles((dir, name) -> name.equalsIgnoreCase(instanceName + "-repo.json"))).length == 1){
        instanceNameList.add(instanceName);
      }
    }
*/
    repoMergeConfiguration.setInstanceNameList(caPropInstanceNameList);
    return repoMergeConfiguration;
  }

}
