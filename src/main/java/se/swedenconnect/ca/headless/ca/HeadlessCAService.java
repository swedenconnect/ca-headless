/*
 * Copyright (c) 2021-2023.  Agency for Digital Government (DIGG)
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
import org.apache.commons.lang3.StringUtils;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.x509.AuthorityKeyIdentifier;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.SubjectKeyIdentifier;
import org.bouncycastle.cert.X509CertificateHolder;

import se.swedenconnect.ca.engine.ca.issuer.CertificateIssuanceException;
import se.swedenconnect.ca.engine.ca.issuer.CertificateIssuerModel;
import se.swedenconnect.ca.engine.ca.models.cert.CertNameModel;
import se.swedenconnect.ca.engine.ca.models.cert.CertificateModel;
import se.swedenconnect.ca.engine.ca.models.cert.extension.ExtensionModel;
import se.swedenconnect.ca.engine.ca.models.cert.extension.impl.simple.AuthorityKeyIdentifierModel;
import se.swedenconnect.ca.engine.ca.models.cert.impl.DefaultCertificateModelBuilder;
import se.swedenconnect.ca.engine.ca.repository.CARepository;
import se.swedenconnect.ca.engine.revocation.crl.CRLIssuerModel;
import se.swedenconnect.ca.service.base.ca.impl.AbstractBasicCA;
import se.swedenconnect.security.credential.PkiCredential;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.cert.CertificateEncodingException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * The implementation of a CA instance
 *
 * @author Martin Lindström (martin@idsec.se)
 * @author Stefan Santesson (stefan@idsec.se)
 */
@Slf4j
public class HeadlessCAService extends AbstractBasicCA {

  public HeadlessCAService(PkiCredential issuerCredential,
    CARepository caRepository, CertificateIssuerModel certIssuerModel,
    CRLIssuerModel crlIssuerModel, List<String> crlDistributionPoints)
    throws NoSuchAlgorithmException, IOException, CertificateEncodingException {
    super(issuerCredential, caRepository, certIssuerModel, crlIssuerModel, crlDistributionPoints);
    log.info("Instantiated Headless CA service instance");
  }

  @Override protected DefaultCertificateModelBuilder getBaseCertificateModelBuilder(CertNameModel subject, PublicKey publicKey,
    X509CertificateHolder issuerCertificate, CertificateIssuerModel certificateIssuerModel) {
    DefaultCertificateModelBuilder certModelBuilder = DefaultCertificateModelBuilder.getInstance(publicKey, getCaCertificate(),
      certificateIssuerModel);
    certModelBuilder
      .subject(subject)
      .includeAki(true)
      .crlDistributionPoints(crlDistributionPoints.isEmpty() ? null : crlDistributionPoints)
      .ocspServiceUrl(StringUtils.isBlank(ocspResponderUrl) ? null : ocspResponderUrl);
    return certModelBuilder;
  }

  @Override
  public X509CertificateHolder issueCertificate(final CertificateModel certificateModel)
    throws CertificateIssuanceException {
    checkIncomingRequest(certificateModel);
    final X509CertificateHolder certificate = this.getCertificateIssuer().issueCertificate(certificateModel);
    try {
      getCaRepository().addCertificate(certificate);
    }
    catch (final IOException e) {
      throw new CertificateIssuanceException(e);
    }
    return certificate;
  }

  private void checkIncomingRequest(CertificateModel certificateModel) throws CertificateIssuanceException {
    List<ExtensionModel> extensionModels = certificateModel.getExtensionModels();
    List<ExtensionModel> updatedExtensionModels = new ArrayList<>();
    for (ExtensionModel extensionModel : extensionModels) {
      if (extensionModelMatch(extensionModel, Extension.authorityKeyIdentifier)) {
        updatedExtensionModels.add(checkAki(extensionModel));
      } else {
        updatedExtensionModels.add(extensionModel);
      }
    }
    certificateModel.setExtensionModels(updatedExtensionModels);
  }

  private boolean extensionModelMatch(ExtensionModel extensionModel, ASN1ObjectIdentifier extOid)
    throws CertificateIssuanceException {
    return extensionModel.getExtensions().stream()
      .anyMatch(extension -> extension.getExtnId().equals(extOid));
  }

  private ExtensionModel checkAki(ExtensionModel extensionModel) throws CertificateIssuanceException {
    AuthorityKeyIdentifier aki = AuthorityKeyIdentifier.getInstance(extensionModel.getExtensions().get(0).getParsedValue());
    byte[] requestedKeyIdentifier = aki.getKeyIdentifier();
    Extension issuerSkiExt = getCaCertificate().getExtension(Extension.subjectKeyIdentifier);
    Objects.requireNonNull(issuerSkiExt, "Issuer subject key identifier must not be null");
    SubjectKeyIdentifier issuerSki = SubjectKeyIdentifier.getInstance(issuerSkiExt.getParsedValue());
    if (!Arrays.equals(issuerSki.getKeyIdentifier(), requestedKeyIdentifier)) {
      log.warn("Requested AKI does not match Issuer SKI. Changing to Issuer SKI match");
      aki = new AuthorityKeyIdentifier(issuerSki.getKeyIdentifier());
      return new AuthorityKeyIdentifierModel(aki);
    }
    return extensionModel;
  }

}
