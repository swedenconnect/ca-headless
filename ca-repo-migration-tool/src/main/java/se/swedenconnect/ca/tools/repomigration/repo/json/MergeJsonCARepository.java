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

package se.swedenconnect.ca.tools.repomigration.repo.json;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.bouncycastle.asn1.x509.CRLNumber;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.cert.X509CRLHolder;
import org.bouncycastle.cert.X509CertificateHolder;
import se.swedenconnect.ca.engine.ca.repository.CARepository;
import se.swedenconnect.ca.engine.ca.repository.CertificateRecord;
import se.swedenconnect.ca.engine.ca.repository.SortBy;
import se.swedenconnect.ca.engine.ca.repository.impl.SerializableCertificateRecord;
import se.swedenconnect.ca.engine.revocation.CertificateRevocationException;
import se.swedenconnect.ca.engine.revocation.crl.CRLRevocationDataProvider;
import se.swedenconnect.ca.engine.revocation.crl.RevokedCertificate;
import se.swedenconnect.ca.service.base.configuration.keys.BasicX509Utils;
import se.swedenconnect.ca.tools.repomigration.repo.MergeCARepository;
import se.swedenconnect.ca.tools.repomigration.repo.db.DBCertificateRecord;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.security.cert.X509Certificate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Test implementation of a CA repository
 *
 * @author Martin Lindström (martin@idsec.se)
 * @author Stefan Santesson (stefan@idsec.se)
 */
@Slf4j
public class MergeJsonCARepository implements CARepository, MergeCARepository {

  private static final ObjectMapper mapper = new ObjectMapper();
  private final File certificateRecordsFile;
  private List<SerializableCertificateRecord> issuedCerts;
  private boolean criticalError = false;

  public MergeJsonCARepository(File certificateRecordsFile) throws IOException {
    this.certificateRecordsFile = certificateRecordsFile;

    if (!certificateRecordsFile.exists()) {
      issuedCerts = new ArrayList<>();
      certificateRecordsFile.getParentFile().mkdirs();
      // Save the empty issued certs file using the synchronized certificate storage and save function
      addCertificate(null);
      System.out.println("Created new CA repository");
    }

    // Load current certs to memory
    issuedCerts = mapper.readValue(certificateRecordsFile,new TypeReference<>() {
      });
  }

  /** {@inheritDoc} */
  @Override public List<BigInteger> getAllCertificates() {
    return issuedCerts.stream()
      .map(certificateRecord -> certificateRecord.getSerialNumber())
      .collect(Collectors.toList());
  }

  /** {@inheritDoc} */
  @Override public CertificateRecord getCertificate(BigInteger bigInteger) {
    Optional<SerializableCertificateRecord> recordOptional = issuedCerts.stream()
      .filter(certificateRecord -> certificateRecord.getSerialNumber().equals(bigInteger))
      .findFirst();
    return recordOptional.isPresent() ? recordOptional.get() : null;
  }

  /** {@inheritDoc} */
  @Override public CRLRevocationDataProvider getCRLRevocationDataProvider() {
    return null;
  }

  /** {@inheritDoc} */
  @Override public int getCertificateCount(boolean notRevoked) {
    if (notRevoked) {
      return (int) issuedCerts.stream()
        .filter(certificateRecord -> !certificateRecord.isRevoked())
        .count();
    }
    return issuedCerts.size();
  }

  /** {@inheritDoc} */
  @Override public List<CertificateRecord> getCertificateRange(int page, int pageSize, boolean notRevoked, SortBy sortBy, boolean descending) {

    List<CertificateRecord> records = issuedCerts.stream()
      .filter(certificateRecord -> {
        if (notRevoked) {
          return !certificateRecord.isRevoked();
        }
        return true;
      })
      .collect(Collectors.toList());

    if (sortBy != null) {
      switch (sortBy) {
      case serialNumber:
        records.sort(Comparator.comparing(CertificateRecord::getSerialNumber));
        break;
      case issueDate:
        records.sort(Comparator.comparing(CertificateRecord::getIssueDate));
        break;
      }
    }

    if (descending) {
      Collections.reverse(records);
    }

    page = page < 0 ? 0 : page;

    int startIdx = page * pageSize;
    int endIdx = startIdx + pageSize;

    if (startIdx > records.size()){
      return new ArrayList<>();
    }

    if (endIdx > records.size()) {
      endIdx = records.size();
    }

    List<CertificateRecord> resultCertList = new ArrayList<>();
    for (int i = startIdx; i<endIdx;i++) {
      resultCertList.add(records.get(i));
    }

    return resultCertList;
  }


  /**
   * From this point we only deal with functions that updates the repository
   */

  @Override public void addCertificateRecord(CertificateRecord certificateRecord) throws IOException {
    try {
      internalRepositoryUpdate(UpdateType.addCertRecord, new Object[]{certificateRecord});
    } catch (Exception ex) {
      throw ex instanceof IOException ? (IOException) ex : new IOException(ex);
    }
  }


  /** {@inheritDoc} */
  @Override public void addCertificate(X509CertificateHolder certificate) throws IOException {
    try {
      internalRepositoryUpdate(UpdateType.addCert, new Object[]{certificate});
    }
    catch (Exception e) {
      throw e instanceof IOException ? (IOException) e : new IOException(e);
    }
  }

  private void internalAddCertificateRecord(CertificateRecord certificateRecord) throws IOException {
    try {
      final X509Certificate certificate = BasicX509Utils.getCertificate(certificateRecord.getCertificate());
      CertificateRecord record = getCertificate(certificate.getSerialNumber());
      if (record != null) {
        throw new IOException("This certificate already exists in the certificate repository");
      }
      issuedCerts.add(new SerializableCertificateRecord(
        certificateRecord.getCertificate(),
        certificateRecord.getSerialNumber(),
        certificateRecord.getIssueDate(),
        certificateRecord.getExpiryDate(),
        certificateRecord.isRevoked(),
        certificateRecord.getReason(),
        certificateRecord.getRevocationTime()
      ));
      if (!saveRepositoryData()){
        throw new IOException("Unable to save issued certificate");
      }
    } catch (Exception ex) {
      throw (ex instanceof IOException) ? (IOException) ex : new IOException(ex);
    }
  }

  private void internalAddCertificate(X509CertificateHolder certificate) throws IOException {
    if (criticalError){
      throw new IOException("This repository encountered a critical error and is not operational - unable to store certificates");
    }
    if (certificate != null) {
      CertificateRecord record = getCertificate(certificate.getSerialNumber());
      if (record != null) {
        throw new IOException("This certificate already exists in the certificate repository");
      }
      issuedCerts.add(new SerializableCertificateRecord(certificate.getEncoded(), certificate.getSerialNumber(),
        certificate.getNotBefore(), certificate.getNotAfter(), false, null, null));
    }
    if (!saveRepositoryData()){
      throw new IOException("Unable to save issued certificate");
    }
  }

  /** {@inheritDoc} */
  @Override public void revokeCertificate(BigInteger serialNumber, int reason, Date revocationTime) throws CertificateRevocationException {
    try {
      internalRepositoryUpdate(UpdateType.revokeCert, new Object[]{serialNumber, reason, revocationTime});
    }
    catch (Exception e) {
      throw e instanceof CertificateRevocationException
        ? (CertificateRevocationException) e
        : new CertificateRevocationException(e);
    }
  }

  private void internalRevokeCertificate(BigInteger serialNumber, int reason, Date revocationTime) throws CertificateRevocationException {
    if (serialNumber == null) {
      throw new CertificateRevocationException("Null Serial number");
    }
    CertificateRecord certificateRecord = getCertificate(serialNumber);
    if (certificateRecord == null) {
      throw new CertificateRevocationException("No such certificate (" + serialNumber.toString(16) + ")");
    }
    certificateRecord.setRevoked(true);
    certificateRecord.setReason(reason);
    certificateRecord.setRevocationTime(revocationTime);
    // Save revoked certificate
    if (!saveRepositoryData()){
      throw new CertificateRevocationException("Unable to save revoked status data");
    }
  }

  /** {@inheritDoc} */
  @Override public List<BigInteger> removeExpiredCerts(int gracePeriodSeconds) throws IOException{
    try {
      return (List<BigInteger>) internalRepositoryUpdate(UpdateType.removeExpiredCerts, new Object[]{gracePeriodSeconds});
    }
    catch (Exception e) {
      throw e instanceof IOException
        ? (IOException) e
        : new IOException(e);
    }
  }

  private List<BigInteger> internalRemoveExpiredCerts(int gracePeriodSeconds) throws IOException {
    List<BigInteger> removedSerialList = new ArrayList<>();
    Date notBefore = new Date(System.currentTimeMillis() - (1000 * gracePeriodSeconds));
    issuedCerts = issuedCerts.stream()
      .filter(certificateRecord -> {
        final Date expiryDate = certificateRecord.getExpiryDate();
        // Check if certificate expired before the current time minus grace period
        if (expiryDate.before(notBefore)){
          // Yes - Remove certificate
          removedSerialList.add(certificateRecord.getSerialNumber());
          return false;
        }
        // No - keep certificate on repository
        return true;
      })
      .collect(Collectors.toList());
    if (!saveRepositoryData()){
      throw new IOException("Unable to save consolidated certificate list");
    }
    return removedSerialList;
  }

  /**
   * All requests to modify the CA repository must go through this function to ensure that all updates are thread safe
   * @param updateType type of repository update
   * @param args input arguments to the update request
   * @return the return object of this update request
   * @throws Exception On errors performing the update request
   */
  private synchronized Object internalRepositoryUpdate(UpdateType updateType, Object[] args) throws Exception {
    switch (updateType){
    case addCert:
      internalAddCertificate((X509CertificateHolder) args[0]);
      return null;
    case addCertRecord:
      internalAddCertificateRecord((CertificateRecord) args[0]);
      return null;
    case revokeCert:
      internalRevokeCertificate((BigInteger) args[0], (int) args[1], (Date) args[2]);
      return null;
    case removeExpiredCerts:
      return internalRemoveExpiredCerts((int) args[0]);
    }
    throw new IOException("Unsupported action");
  }

  private boolean saveRepositoryData(){
    try {
      // Attempt to save repository data
      mapper.writeValue(certificateRecordsFile, issuedCerts);
      return true;
    }
    catch (IOException e) {
      log.error("Error writing to the ca repository storage file", e);
      criticalError = true;
    }
    return false;
  }


  public enum UpdateType {
    //TODO Fill in rest
    addCert, addCertRecord , revokeCert, removeExpiredCerts
  }



}
