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

package se.swedenconnect.ca.headless.ca.db;

import lombok.NoArgsConstructor;
import se.swedenconnect.ca.engine.ca.repository.CertificateRecord;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

import java.math.BigInteger;
import java.util.Date;

/**
 * Description
 *
 * @author Martin Lindström (martin@idsec.se)
 * @author Stefan Santesson (stefan@idsec.se)
 */
@Entity
@Table(name = "dbcertificate_record")
@NoArgsConstructor
public class DBCertificateRecord implements CertificateRecord {

  @Id
  @Column(name = "id")
  protected String serialNumber;
  @Column(name = "certificate", nullable = false, length = 65535)
  protected byte[] certificate;
  @Column(name = "issue_date")
  protected long issueDate;
  @Column(name = "expiry_date")
  protected long expiryDate;
  @Column(name = "revoked")
  protected boolean revoked;
  @Column(name = "reason")
  protected Integer reason;
  @Column(name = "revocation_time")
  protected long revocationTime;
  @Column(name = "instance")
  protected String instance;

  public DBCertificateRecord(byte[] certificate, BigInteger serialNumber, Date issueDate, Date expiryDate, boolean revoked, Integer reason, Date revocationTime, String instance) {
    this.setCertificate(certificate);
    this.setSerialNumber(serialNumber);
    this.setIssueDate(issueDate);
    this.setExpiryDate(expiryDate);
    this.setRevoked(revoked);
    this.setReason(reason);
    this.setRevocationTime(revocationTime);
    this.setInstance(instance);
  }


  public byte[] getCertificate() {
    return this.certificate;
  }

  public BigInteger getSerialNumber() {
    return this.serialNumber == null ? null : new BigInteger("0" + this.serialNumber, 16);
  }

  public Date getIssueDate() {
    return this.getDateOrNull(this.issueDate);
  }

  public Date getExpiryDate() {
    return this.getDateOrNull(this.expiryDate);
  }

  public boolean isRevoked() {
    return this.revoked;
  }

  public Integer getReason() {
    return this.reason;
  }

  public Date getRevocationTime() {
    return this.getDateOrNull(this.revocationTime);
  }

  public void setCertificate(byte[] certificate) {
    this.certificate = certificate;
  }

  public void setSerialNumber(BigInteger serialNumber) {
    this.serialNumber = serialNumber == null ? null : serialNumber.toString(16);
  }

  public void setIssueDate(Date issueDate) {
    this.issueDate = this.parseDateOrNull(issueDate);
  }

  public void setExpiryDate(Date expiryDate) {
    this.expiryDate = this.parseDateOrNull(expiryDate);
  }

  public void setRevoked(boolean revoked) {
    this.revoked = revoked;
  }

  public void setReason(Integer reason) {
    this.reason = reason;
  }

  public void setRevocationTime(Date revocationTime) {
    this.revocationTime = this.parseDateOrNull(revocationTime);
  }

  private long parseDateOrNull(Date revocationTime) {
    return revocationTime == null ? -1L : revocationTime.getTime();
  }

  private Date getDateOrNull(long longTime) {
    return longTime < 0L ? null : new Date(longTime);
  }

  public String getInstance() {
    return instance;
  }

  public void setInstance(String instance) {
    this.instance = instance;
  }
}
