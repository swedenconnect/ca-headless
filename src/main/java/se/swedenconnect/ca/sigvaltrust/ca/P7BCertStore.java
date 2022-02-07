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

package se.swedenconnect.ca.sigvaltrust.ca;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateHolder;
import org.bouncycastle.cms.CMSException;
import org.bouncycastle.cms.CMSProcessableByteArray;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.CMSSignedDataGenerator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import se.swedenconnect.ca.engine.ca.repository.CARepository;
import se.swedenconnect.ca.engine.ca.repository.CertificateRecord;
import se.swedenconnect.ca.engine.utils.CAUtils;
import se.swedenconnect.ca.service.base.configuration.BasicServiceConfig;
import se.swedenconnect.sigvaltrust.service.commons.EquivalentCertProcessor;
import se.swedenconnect.sigvaltrust.service.commons.impl.DefaultEquivalentCertProcessor;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Bean for publishing a file containing a PKCS#7 bag of certs file holding a list of all not revoked and not expired certificates
 * in the CA repository
 *
 * @author Martin Lindström (martin@idsec.se)
 * @author Stefan Santesson (stefan@idsec.se)
 */
@Component
@Slf4j
public class P7BCertStore {

  private final BasicServiceConfig basicServiceConfig;
  private final EquivalentCertProcessor equivalentCertProcessor;
  private Map<String, P7bPublishResources> p7bResourcesMap;
  @Value("${ca-service.p7b.max-age-seconds:30}") private int maxAgeSec;

  @Autowired
  public P7BCertStore(BasicServiceConfig basicServiceConfig) {
    this.basicServiceConfig = basicServiceConfig;
    this.equivalentCertProcessor = new DefaultEquivalentCertProcessor();
    this.p7bResourcesMap = new HashMap<>();
  }

  /**
   * Publish an updates list of issued valid unrevoked and unique certificates in the repository PKCS#7 file for the selected instance.
   * Unique in this context means that if two certificates are equivalent (issued to the same person with the same public key), then
   * only the most recent issued certificate will be published here even if both certificates exists in the CA repository.
   *
   * @param instance The instance CA issuing the published certs
   * @throws IOException Error parsing input data
   * @throws CMSException Error creating PKCS#7 bag of certs
   */
  public void publishIssuedCerts(String instance, CARepository caRepository) throws IOException, CMSException, CertificateException {
    File certStoreFile = new File(basicServiceConfig.getDataStoreLocation(), "instances/"+instance+"/repository/certs.p7b");
    List<BigInteger> allCertSerials = caRepository.getAllCertificates();
    Date currentTime = new Date();
    List<X509CertificateHolder> subjectCertList = allCertSerials.stream()
      .map(serialNumber -> caRepository.getCertificate(serialNumber))
      .filter(certificateRecord -> !certificateRecord.isRevoked())
      .map(certificateRecord -> getCert(certificateRecord))
      .filter(x509CertificateHolder -> currentTime.before(x509CertificateHolder.getNotAfter()))
      .filter(x509CertificateHolder -> currentTime.after(x509CertificateHolder.getNotBefore()))
      .collect(Collectors.toList());

    // Remove any certificate duplicates
    final List<X509CertificateHolder> uniqueCertList = getCertHolderList(equivalentCertProcessor.removeEquivalentCerts(CAUtils.getCertList(subjectCertList)));

    // Create PKCS7 file
    byte[] pkcs7 = getPKCS7(uniqueCertList);
    FileUtils.writeByteArrayToFile(certStoreFile, pkcs7);
    p7bResourcesMap.put(instance, new P7bPublishResources(certStoreFile, caRepository, uniqueCertList.size(), currentTime));
    log.debug("Published CA p7b cert store file with {} certificates for instance {}", uniqueCertList.size(), instance);
  }

  private List<X509CertificateHolder> getCertHolderList(List<X509Certificate> certificateList) throws CertificateEncodingException {
    List<X509CertificateHolder> certificateHolderList = new ArrayList<>();
    for (X509Certificate certificate:certificateList){
      certificateHolderList.add(new JcaX509CertificateHolder(certificate));
    }
    return certificateHolderList;
  }

  public InputStream getCertStoreP7bBytes(String instance) throws IOException, CMSException, CertificateException {
    if (!p7bResourcesMap.containsKey(instance)){
      log.debug("Requested instance () is not registered", instance);
      throw new IOException("Requested instance is not registered");
    }
    P7bPublishResources p7bPublishResources = p7bResourcesMap.get(instance);
    // Check if there is a recent publish
    Date mustBeCreatedAfter = new Date(System.currentTimeMillis() - (1000L * maxAgeSec));
    if (p7bPublishResources.getPublishTime() == null || p7bPublishResources.getPublishTime().before(mustBeCreatedAfter)){
      // last publish does not exist, or is too old
      publishIssuedCerts(instance, p7bPublishResources.caRepository);
    }
    // Reload published resources
    p7bPublishResources = p7bResourcesMap.get(instance);
    return new FileInputStream(p7bPublishResources.getP7bFile());
  }

  private X509CertificateHolder getCert(CertificateRecord certificateRecord) {
    try {
      return new X509CertificateHolder(certificateRecord.getCertificate());
    }
    catch (IOException e) {
      log.error("Illegal Certificate data in CA repository. The repository contains corrupt data", e);
      return null;
    }
  }

  private byte[] getPKCS7(List<X509CertificateHolder> certList) throws CMSException, IOException {
    CMSSignedDataGenerator gen = new CMSSignedDataGenerator();

    certList = certList == null ? new ArrayList<>() : certList;
    for (X509CertificateHolder cert : certList) {
      gen.addCertificate(cert);
    }

    CMSSignedData signedData = gen.generate(new CMSProcessableByteArray((byte[])null), true);
    return signedData.getEncoded();
  }




  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  private static class P7bPublishResources {

    private File p7bFile;
    private CARepository caRepository;
    int validCertCount;
    Date publishTime;

  }


}
