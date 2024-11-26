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

package se.swedenconnect.ca.headless.ca.db;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;
import org.bouncycastle.asn1.x509.CRLNumber;
import org.bouncycastle.asn1.x509.CRLReason;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.cert.X509CRLHolder;
import org.bouncycastle.cert.X509CertificateHolder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import se.swedenconnect.ca.engine.ca.repository.CARepository;
import se.swedenconnect.ca.engine.ca.repository.CertificateRecord;
import se.swedenconnect.ca.engine.ca.repository.SortBy;
import se.swedenconnect.ca.engine.revocation.CertificateRevocationException;
import se.swedenconnect.ca.engine.revocation.crl.CRLMetadata;
import se.swedenconnect.ca.engine.revocation.crl.CRLRevocationDataProvider;
import se.swedenconnect.ca.engine.revocation.crl.RevokedCertificate;

/**
 * Test implementation of a CA repository
 *
 * @author Martin Lindström (martin@idsec.se)
 * @author Stefan Santesson (stefan@idsec.se)
 */
@Slf4j
public class DBCARepository implements CARepository, CRLRevocationDataProvider {

  private final File crlFile;
  private final String instance;
  @Getter private final DBJPARepository dbRepository;
  private final DBCRLMetadataRepository dbcrlMetadataRepository;
  private boolean criticalError = false;
  @Setter private int pageSize = 100;

  public DBCARepository(File crlFile, DBJPARepository dbRepository, String instance,
    DBCRLMetadataRepository dbcrlMetadataRepository) throws IOException {
    this.crlFile = crlFile;
    this.dbRepository = dbRepository;
    this.instance = instance;
    this.dbcrlMetadataRepository = dbcrlMetadataRepository;
    // Load current certs to memory
    log.info("Database based CA repository initialized");
    if (!crlFile.exists()) {
      crlFile.getParentFile().mkdirs();
      if (!crlFile.getParentFile().exists()) {
        log.error("Unable to create crl file directory");
        criticalError = true;
        throw new IOException("Unable to create crl file directory");
      }
    }
  }

  private BigInteger getCRLNumberFromCRL(X509CRLHolder crlHolder) throws IOException {
    Extension crlNumberExtension = crlHolder.getExtension(Extension.cRLNumber);
    CRLNumber crlNumberFromCrl = CRLNumber.getInstance(crlNumberExtension.getParsedValue());
    return crlNumberFromCrl.getCRLNumber();
  }

  @Override public List<BigInteger> getAllCertificates() {
    List<BigInteger> certificateSerialNumberList = new ArrayList<>();
    int page = 0;
    while (true) {
      Page<DBCertificateRecord> records = dbRepository.findByInstance(instance, PageRequest.of(page++, pageSize));
      if (records.hasContent()) {
        records.stream()
          .forEach(dbCertificateRecord -> certificateSerialNumberList.add(dbCertificateRecord.getSerialNumber()));
      }
      else {
        break;
      }
    }
    return certificateSerialNumberList;
  }

  @Override public CertificateRecord getCertificate(BigInteger bigInteger) {
    List<DBCertificateRecord> records = dbRepository.findByInstanceAndSerialNumber(instance, bigInteger.toString(16));
    return !records.isEmpty() ? records.get(0) : null;
  }

  @Override public synchronized void addCertificate(X509CertificateHolder certificate) throws IOException {
    if (criticalError) {
      throw new IOException(
        "This repository encountered a critical error and is not operational - unable to store certificates");
    }
    if (certificate != null) {
      CertificateRecord record = getCertificate(certificate.getSerialNumber());
      if (record != null) {
        throw new IOException("This certificate already exists in the certificate repository");
      }
      dbRepository.save(new DBCertificateRecord(certificate.getEncoded(), certificate.getSerialNumber(),
        certificate.getNotBefore(), certificate.getNotAfter(), false, null, null, instance));
    }
  }

  @Override public void revokeCertificate(BigInteger serialNumber, int reason, Date revocationTime)
    throws CertificateRevocationException {

    if (serialNumber == null) {
      throw new CertificateRevocationException("Null Serial number");
    }
    DBCertificateRecord certificateRecord = (DBCertificateRecord) getCertificate(serialNumber);
    if (certificateRecord == null) {
      throw new CertificateRevocationException("No such certificate (" + serialNumber.toString(16) + ")");
    }
    if (certificateRecord.isRevoked() && CRLReason.aACompromise < certificateRecord.getReason()) {
      throw new CertificateRevocationException("Illegal reason code");
    }

    if (certificateRecord.isRevoked()) {
      if (certificateRecord.getReason() == CRLReason.certificateHold) {
        if (CRLReason.removeFromCRL == reason) {
          // remove this certificate from certificateHold status
          log.debug("Removing certificate from certificateHold");
          certificateRecord.setRevoked(false);
          certificateRecord.setReason(null);
          certificateRecord.setRevocationTime(null);
          // Save revoked certificate
          dbRepository.save(certificateRecord);
          return;
        }
        // This was not a request to remove the revocation, but to permanently revoke.
        log.debug("Modifying revoked certificate from certificateHold to reason {}", reason);
        certificateRecord.setRevoked(true);
        certificateRecord.setReason(reason);
        certificateRecord.setRevocationTime(revocationTime);
        // Save revoked certificate
        dbRepository.save(certificateRecord);
        return;
      }
      else {
        if (CRLReason.removeFromCRL == reason) {
          log.debug("Revocation removal request denied since certificate has already been permanently revoked");
          throw new CertificateRevocationException(
            "Revocation removal request denied since certificate has already been permanently revoked");
        }
        log.debug("Certificate is already revoked with reason other than certificate hold");
        throw new CertificateRevocationException(
          "Revocation request denied since certificate is already revoked with reason other than certificate hold");
      }
    }

    // This certificate was not revoked before. Revoke it
    if (CRLReason.removeFromCRL == reason) {
      log.debug("Revocation removal request denied since certificate is not on hold");
      throw new CertificateRevocationException("Removal request for a certificate that has not been revoked");
    }

    certificateRecord.setRevoked(true);
    certificateRecord.setReason(reason);
    certificateRecord.setRevocationTime(revocationTime);
    // Save revoked certificate
    dbRepository.save(certificateRecord);
  }

  @Override public CRLRevocationDataProvider getCRLRevocationDataProvider() {
    return this;
  }

  /**
   * Get the number of certificates in the certificate repository
   *
   * @param notRevoked true to count only the current not revoked certificates and false for the count of all certificates in the repository
   * @return number of certificates
   */
  @Override public int getCertificateCount(boolean notRevoked) {
    return notRevoked
      ? dbRepository.countByInstanceAndRevoked(instance, false)
      : dbRepository.countByInstance(instance);
  }

  /**
   * Get a range of certificates from the certificate repository
   *
   * @param page the index of the page of certificates to return
   * @param pageSize the size of each page of certificates
   * @param notRevoked true if the pages of certificates holds only not revoked certificates
   * @param sortBy set to define sorting preferences or null if unsorted
   * @param descending set to true to select descending order
   * @return list of certificates in the selected page
   */
  @Override public List<CertificateRecord> getCertificateRange(int page, int pageSize, boolean notRevoked,
    SortBy sortBy,
    boolean descending) {

    Pageable pageable = PageRequest.of(page, pageSize);

    Page<DBCertificateRecord> records;
    if (notRevoked) {
      if (descending) {
        records = sortBy == SortBy.serialNumber
          ? dbRepository.findByInstanceAndRevokedOrderBySerialNumberDesc(instance, false, pageable)
          : dbRepository.findByInstanceAndRevokedOrderByIssueDateDesc(instance, false, pageable);
      }
      else {
        // Ascending
        records = sortBy == SortBy.serialNumber
          ? dbRepository.findByInstanceAndRevokedOrderBySerialNumberAsc(instance, false, pageable)
          : dbRepository.findByInstanceAndRevokedOrderByIssueDateAsc(instance, false, pageable);
      }
    }
    else {
      // All certificates including revoked
      if (descending) {
        records = sortBy == SortBy.serialNumber
          ? dbRepository.findByInstanceOrderBySerialNumberDesc(instance, pageable)
          : dbRepository.findByInstanceOrderByIssueDateDesc(instance, pageable);
      }
      else {
        // Ascending
        records = sortBy == SortBy.serialNumber
          ? dbRepository.findByInstanceOrderBySerialNumberAsc(instance, pageable)
          : dbRepository.findByInstanceOrderByIssueDateAsc(instance, pageable);
      }
    }

    return records.get().collect(Collectors.toList());
  }

  private Sort getSort(SortBy sortBy, boolean descending) {
    String columnName = sortBy == SortBy.serialNumber ? "id" : "issue_date";
    return descending
      ? Sort.by(columnName).descending()
      : Sort.by(columnName).ascending();
  }

  /**
   * Remove all expired certificates that have been expired for at least the specified grace period
   *
   * @param gracePeriodSeconds number of seconds a certificate can be expired without being removed
   * @return list of the serial numbers of the certificates that were removed from the repository
   */
  @Override public List<BigInteger> removeExpiredCerts(int gracePeriodSeconds) throws IOException {
    final List<BigInteger> expiredCertificates = getExpiredCertificates(gracePeriodSeconds);
    List<BigInteger> actuallyDeleted = new ArrayList<>();
    for (BigInteger expiredCertSerial : expiredCertificates) {
      if (dbRepository.deleteByInstanceAndSerialNumber(instance, expiredCertSerial.toString(16)) > 0) {
        actuallyDeleted.add(expiredCertSerial);
      }
    }
    return actuallyDeleted;
  }

  public List<BigInteger> getExpiredCertificates(int gracePeriodSeconds) throws IOException {
    List<BigInteger> expiredCertificates = new ArrayList<>();
    int page = 0;
    long maxExpiryDate = System.currentTimeMillis() - (gracePeriodSeconds * 1000L);

    while (true) {
      Page<DBCertificateRecord> records = dbRepository.findByInstanceAndExpiryDateLessThan(instance, maxExpiryDate,
        PageRequest.of(page++, pageSize));
      if (records.hasContent()) {
        records.stream().forEach(certificateRecord -> expiredCertificates.add(certificateRecord.getSerialNumber()));
      }
      else {
        break;
      }
    }
    return expiredCertificates;
  }

  @Override public List<RevokedCertificate> getRevokedCertificates() {

    List<RevokedCertificate> revokedCertificates = new ArrayList<>();
    int page = 0;

    while (true) {
      Page<DBCertificateRecord> records = dbRepository.findByInstanceAndRevoked(instance, true,
        PageRequest.of(page++, pageSize));
      if (records.hasContent()) {
        records.stream().forEach(certificateRecord -> {
          revokedCertificates.add(new RevokedCertificate(
            certificateRecord.getSerialNumber(),
            certificateRecord.getRevocationTime(),
            certificateRecord.getReason()
          ));
        });
      }
      else {
        break;
      }
    }
    return revokedCertificates;
  }

  @Override public BigInteger getNextCrlNumber() {
    return getCurrentCRLMetadata().getCrlNumber().add(BigInteger.ONE);
  }

  @Override public void publishNewCrl(X509CRLHolder crl) {
    // Check if new CRL has updated the CRL metadata
    CRLMetadata currentCRLMetadata = getCurrentCRLMetadata();

    try {
      BigInteger crlNumberFromCRL = getCRLNumberFromCRL(crl);
      if (crlNumberFromCRL.compareTo(currentCRLMetadata.getCrlNumber()) > 0) {
        // The new CRL has increased the CRL number. Store its metadata for all to use
        CRLMetadata crlMetadata = CRLMetadata.builder()
          .crlNumber(crlNumberFromCRL)
          .issueTime(crl.getThisUpdate().toInstant())
          .nextUpdate(crl.getNextUpdate().toInstant())
          .revokedCertCount(crl.getRevokedCertificates().size())
          .build();
        dbcrlMetadataRepository.storeCrlMetadata(crlMetadata, instance);
      }

      FileUtils.writeByteArrayToFile(crlFile, crl.getEncoded());
    }
    catch (IOException e) {
      throw new RuntimeException("Critical error attempting to store CRL file", e);
    }
  }

  @Override public X509CRLHolder getCurrentCrl() {
    /*
      Here we want to compare the current CRL against CRL metadata
      If metadata indicates that this CRL is not up-to-date, we
      will return null, in order to force a new update
     */
    try {
      return new X509CRLHolder(new FileInputStream(crlFile));
    }
    catch (Exception e) {
      log.debug("No current CRL is available. Returning null");
      return null;
    }
  }

  @Override public CRLMetadata getCurrentCRLMetadata() {

    CRLMetadata crlMetadata = dbcrlMetadataRepository.getCRLMetadata(instance);
    if (crlMetadata != null) {
      return crlMetadata;
    }
    log.warn("CRL Metadata not present in Repository: Attempting to store data from current CRL");

    try {
      X509CRLHolder currentCrl = getCurrentCrl();
      crlMetadata = CRLMetadata.builder()
        .crlNumber(getCRLNumberFromCRL(currentCrl))
        .issueTime(currentCrl.getThisUpdate().toInstant())
        .nextUpdate(currentCrl.getNextUpdate().toInstant())
        .revokedCertCount(currentCrl.getRevokedCertificates().size())
        .build();
      dbcrlMetadataRepository.storeCrlMetadata(crlMetadata, instance);
      log.info("Stored CRL metadata to DB from current available CRL file");
      return crlMetadata;
    }
    catch (Exception ex) {
      log.warn("No current CRL is available in the system");
    }
    // No metadata in DB and no CRL file. Starting fresh with new CRL
    log.info("Starting new CRL sequence with CRL number 0");
    crlMetadata = CRLMetadata.builder()
      .crlNumber(BigInteger.ZERO)
      .issueTime(Instant.ofEpochMilli(0L))
      .nextUpdate(Instant.ofEpochMilli(0L))
      .revokedCertCount(0)
      .build();
    dbcrlMetadataRepository.storeCrlMetadata(crlMetadata, instance);
    return crlMetadata;
  }

/*
  @Override public void afterPropertiesSet() throws Exception {
    CRLMetadata crlMetadata = getCurrentCRLMetadata();
    log.info(
      "For instance {} - Starting CRL with CRL number {}, issued at {}, expiring {}, with {} revoked certificates",
      instance, crlMetadata.getCrlNumber(), crlMetadata.getIssueTime(), crlMetadata.getNextUpdate(),
      crlMetadata.getRevokedCertCount());
  }
*/
}
