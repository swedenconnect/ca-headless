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

package se.swedenconnect.ca.tools.repomigration.repo.db;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.cert.X509CertificateHolder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import se.swedenconnect.ca.engine.ca.repository.CARepository;
import se.swedenconnect.ca.engine.ca.repository.CertificateRecord;
import se.swedenconnect.ca.engine.ca.repository.SortBy;
import se.swedenconnect.ca.engine.revocation.CertificateRevocationException;
import se.swedenconnect.ca.engine.revocation.crl.CRLRevocationDataProvider;
import se.swedenconnect.ca.service.base.configuration.keys.BasicX509Utils;
import se.swedenconnect.ca.tools.repomigration.repo.MergeCARepository;

import java.io.IOException;
import java.math.BigInteger;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Test implementation of a CA repository
 *
 * @author Martin Lindström (martin@idsec.se)
 * @author Stefan Santesson (stefan@idsec.se)
 */
@Slf4j
public class MergeDBCARepository implements CARepository, MergeCARepository {

  private final String instance;
  @Getter private final DBJPARepository dbRepository;
  @Setter private int pageSize = 1000;

  public MergeDBCARepository(DBJPARepository dbRepository, String instance) throws IOException {
    this.dbRepository = dbRepository;
    this.instance = instance;
  }

  @Override public List<BigInteger> getAllCertificates() {
    List<BigInteger> certificateSerialNumberList = new ArrayList<>();
    int page = 0;
    while (true) {
      Page<DBCertificateRecord> records = dbRepository.findByInstance(instance, PageRequest.of(page++, pageSize));
      if (records.hasContent()) {
        records.stream().forEach(dbCertificateRecord -> certificateSerialNumberList.add(dbCertificateRecord.getSerialNumber()));
      }
      else {
        break;
      }
    }
    return certificateSerialNumberList;
  }

  @Override public CertificateRecord getCertificate(BigInteger bigInteger) {
    List<DBCertificateRecord> records = new ArrayList<>();
    long startTime = System.currentTimeMillis();
    // This is a protection against exception caused by too many DP connections if this function is iterated in a loop over many certs
    while (System.currentTimeMillis() < startTime + 3000) {
      try {
        records = dbRepository.findByInstanceAndSerialNumber(instance, bigInteger.toString(16));
        break;
      }
      catch (Exception ex) {
        // request for a record caused exception (we expect that the number of connections was exceeded). Let's wait and try again in 50 ms
        try {
          Thread.sleep(50);
        }
        catch (InterruptedException e) {
          // Unrecoverable error.
          throw new RuntimeException("Error obtaining certificate from database", e);
        }
      }
    }
    return !records.isEmpty() ? records.get(0) : null;
  }

  @Override public void addCertificateRecord(CertificateRecord certificateRecord) throws IOException {
    try {
      final X509Certificate certificate = BasicX509Utils.getCertificate(certificateRecord.getCertificate());
      CertificateRecord record = getCertificate(certificate.getSerialNumber());
      if (record != null) {
        throw new IOException("This certificate already exists in the certificate repository");
      }
      dbRepository.save(new DBCertificateRecord(
        certificateRecord.getCertificate(),
        certificateRecord.getSerialNumber(),
        certificateRecord.getIssueDate(),
        certificateRecord.getExpiryDate(),
        certificateRecord.isRevoked(),
        certificateRecord.getReason(),
        certificateRecord.getRevocationTime(),
        instance
      ));
    } catch (Exception ex) {
      throw (ex instanceof IOException) ? (IOException) ex : new IOException(ex);
    }

  }

  @Override public synchronized void addCertificate(X509CertificateHolder certificate) throws IOException {
    if (certificate != null) {
      CertificateRecord record = getCertificate(certificate.getSerialNumber());
      if (record != null) {
        throw new IOException("This certificate already exists in the certificate repository");
      }
      dbRepository.save(new DBCertificateRecord(certificate.getEncoded(), certificate.getSerialNumber(),
        certificate.getNotBefore(), certificate.getNotAfter(), false, null, null, instance));
    }
  }

  @Override public void revokeCertificate(BigInteger serialNumber, int reason, Date revocationTime) throws CertificateRevocationException {
    throw new CertificateRevocationException("Unsupported action");
  }

  @Override public CRLRevocationDataProvider getCRLRevocationDataProvider() {
    return null;
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
   * @param page       the index of the page of certificates to return
   * @param pageSize   the size of each page of certificates
   * @param notRevoked true if the pages of certificates holds only not revoked certificates
   * @param sortBy     set to define sorting preferences or null if unsorted
   * @param descending set to true to select descending order
   * @return list of certificates in the selected page
   */
  @Override public List<CertificateRecord> getCertificateRange(int page, int pageSize, boolean notRevoked, SortBy sortBy,
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

}
