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

package se.swedenconnect.ca.headless.ca;

import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cms.CMSException;
import org.springframework.context.ApplicationEventPublisher;
import se.swedenconnect.ca.engine.ca.issuer.CertificateIssuanceException;
import se.swedenconnect.ca.engine.ca.issuer.CertificateIssuer;
import se.swedenconnect.ca.engine.ca.issuer.CertificateIssuerModel;
import se.swedenconnect.ca.engine.ca.issuer.impl.SelfIssuedCertificateIssuer;
import se.swedenconnect.ca.engine.ca.models.cert.CertificateModel;
import se.swedenconnect.ca.engine.ca.models.cert.extension.impl.CertificatePolicyModel;
import se.swedenconnect.ca.engine.ca.models.cert.extension.impl.simple.BasicConstraintsModel;
import se.swedenconnect.ca.engine.ca.models.cert.extension.impl.simple.KeyUsageModel;
import se.swedenconnect.ca.engine.ca.models.cert.impl.DefaultCertificateModelBuilder;
import se.swedenconnect.ca.engine.ca.models.cert.impl.SelfIssuedCertificateModelBuilder;
import se.swedenconnect.ca.engine.ca.repository.CARepository;
import se.swedenconnect.ca.engine.revocation.crl.CRLIssuerModel;
import se.swedenconnect.ca.service.base.configuration.BasicServiceConfig;
import se.swedenconnect.ca.service.base.configuration.instance.InstanceConfiguration;
import se.swedenconnect.ca.service.base.ca.impl.AbstractBasicCA;
import se.swedenconnect.ca.service.base.ca.impl.AbstractDefaultCAServices;
import se.swedenconnect.ca.service.base.configuration.keys.PkiCredentialFactory;
import se.swedenconnect.ca.service.base.configuration.properties.CAConfigData;
import se.swedenconnect.ca.service.base.utils.GeneralCAUtils;
import se.swedenconnect.security.credential.PkiCredential;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.util.List;
import java.util.Map;

/**
 * This class creates CA Service instances for the generic headless CA.
 *
 * <p>It is highly recommended for production environments to implement a CA repository
 * based an a real database implementation with appropriate management of backup and protection
  * against conflicts and simultaneous storage and/or revocation requests</p>
 *
 * @author Martin Lindström (martin@idsec.se)
 * @author Stefan Santesson (stefan@idsec.se)
 */
@Slf4j
public class HeadlessCAServices extends AbstractDefaultCAServices {
  public HeadlessCAServices(InstanceConfiguration instanceConfiguration,
    PkiCredentialFactory pkiCredentialFactory, BasicServiceConfig basicServiceConfig,
    Map<String, CARepository> caRepositoryMap, P7BCertStore p7BCertStore, ApplicationEventPublisher applicationEventPublisher)
    throws CertificateException, IOException, CMSException {
    super(instanceConfiguration, pkiCredentialFactory, basicServiceConfig, caRepositoryMap, applicationEventPublisher);

    // Publish issued certs
    List<String> caServiceKeys = this.getCAServiceKeys();
    for (String instance: caServiceKeys) {
      p7BCertStore.publishIssuedCerts(instance, caRepositoryMap.get(instance));
    }

  }

  /** {@inheritDoc} */
  @Override protected AbstractBasicCA getBasicCaService(String instance, String type, PkiCredential issuerCredential,
    CARepository caRepository, CertificateIssuerModel certIssuerModel, CRLIssuerModel crlIssuerModel, List<String> crlDistributionPoints)
    throws NoSuchAlgorithmException, IOException, CertificateEncodingException {

    log.info("Creating a CA service for instance {}", instance);
    return new HeadlessCAService(issuerCredential, caRepository, certIssuerModel, crlIssuerModel, crlDistributionPoints);
  }

  /** {@inheritDoc} */
  @Override protected void customizeOcspCertificateModel(DefaultCertificateModelBuilder certModelBuilder, String instance) {
    // We don't add any custom content of OCSP service certificates
  }

  /** {@inheritDoc} */
  @Override protected X509CertificateHolder generateSelfIssuedCaCert(PkiCredential caKeySource, CAConfigData caConfigData, String instance, String baseUrl)
    throws NoSuchAlgorithmException, CertificateIssuanceException {
    // We implement our own Self issued profile in order to add the SubjectInfoAccess URL to self issued certificates
    CAConfigData.CaConfig caConfig = caConfigData.getCa();
    if (caConfig.getSelfIssuedValidYears() == null) {
      log.error("Illegal self issued validity configuration");
      throw new RuntimeException("Illegal self issued validity configuration - null");
    }

    CertificateIssuerModel certificateIssuerModel = new CertificateIssuerModel(
      caConfig.getAlgorithm(),
      GeneralCAUtils.getDurationFromTypeAndValue(CAConfigData.ValidityUnit.Y, caConfig.getSelfIssuedValidYears())
    );
    CertificateIssuer issuer = new SelfIssuedCertificateIssuer(certificateIssuerModel);
    CertificateModel certModel = SelfIssuedCertificateModelBuilder.getInstance(
        caKeySource.getPrivateKey(),
        caKeySource.getCertificate().getPublicKey(),
        certificateIssuerModel)
      .subject(getSubjectNameModel(caConfig.getName()))
      .basicConstraints(new BasicConstraintsModel(true, true))
      .keyUsage(new KeyUsageModel(KeyUsage.keyCertSign + KeyUsage.cRLSign, true))
      .includeSki(true)
      .certificatePolicy(new CertificatePolicyModel(true))
      .caRepositoryUrl(baseUrl + "/certs/"+instance +".p7b")
      .build();
    return issuer.issueCertificate(certModel);
  }

}
