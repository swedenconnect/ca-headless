# Headless CA versions

**Latest current version 1.3.0**

| Version | Comment                                                                              | Date       |
|---------|--------------------------------------------------------------------------------------|------------|
| 1.0.0   | Initial version                                                                      | 2022-02-07 |
| 1.1.0   | Support for DB Certificate repository as the default storage                         | 2022-02-07 |
| 1.1.1   | Fixing daemon bug caused by missing annotation. Added supplementary tools            | 2022-02-14 |
| 1.1.2   | Allowing empty context path for CA                                                   | 2022-02-23 |
| 1.1.3   | Publishing a new CRL directly upon CMC revocation instead of waiting for next update | 2022-02-23 |
| 1.1.4   | Including mitigation of spring core RCE vulnerability                                | 2022-03-31 |
| 1.1.5   | Moving to Spring boot 2.6.6 as a stable mitigation of RCE                            | 2022-04-01 |
| 1.2.0   | Updates to use of the new Credential Support library for key configuration           | 2022-10-02 |
| 1.3.0   | Update to support synchronized CRL metadata for multi server deployment of single CA | 2023-01-02 |



## Important release notes (most recent on top)

Release notes are provided only for updates that require configuration changes or other deployment considerations.

### 1.3.0

This version does not require any configuration update as all new configuration values have sensible defaults.

**However, there is a new Database table that must be created if the CA repository is using the database option! (See below)**

What is new in this version is that CRL issuance operations are influenced by CRL metadata that is synchronized
among multiple servers providing the same CA instance. The CA metadata reflects data about the latest
CRL that was created by any of the servers providing the same CA and contains information about CRL number, issue date,
next update and number of revoked certificates.

The following two configuration parameters can be set for detailed control of CRL issuance. The first
parameter is common to all CA instances and controls the behaviour of the common CRL download request controller
used by all CA instances

> ca-service.config.crl-refresh-margin-seconds

This defines the number of seconds before expiry the controller will consider any CRL too old to use. If the time
before expiry is less than this amount of seconds, the controller will demand a new CRL with current issue time. This
parameter acts as a safety margin taking into account the risk of time skew between the CA and any certificate
using system preventing a CRL to be distributed that may be regarded as expired by the recipient.

The other parameter is set per CA instance (A CA instance in this context is a CA identity with its unique issuing key):

> ca-service.instance.conf.{instance-name}.ca.crl-max-duration-before-upgrade=60s

This parameter is expressed as a Duration resource. The default unit is seconds, but any standard duration format can be used
(see [Duration property formats](https://docs.spring.io/spring-boot/docs/2.1.12.RELEASE/reference/html/boot-features-external-config.html#boot-features-external-config-conversion-duration)).
This time defines the maximum time CRLs will be issued based on the same metadata (Using the same
CRL number and issue time) even if no new certificates has been revoked during this time period. If a certificate is
revoked, this time will be disregarded and a new CRL will be created even if this time has not expired.

A CA where no new certificates have been revoked compared with the CRL metadata, will issue a CRL conforming with the current
CRL metadata (using the same CRL number and issue time) as long as this time has not passed since CRL metadata issue time. When
this time has passed, a CA that is asked to issue a new CRL will first update the metadata with increased number and issue time
before issuing the CRL according to the updated metadata.

This timer has the effect that multiple servers providing the same CA instance, will issue CRL:s with the same CRL number
and issue time as long as the number of revoked certificates align with the metadata.
If this duration is set to a very short duration will increase the load of the servers to issue new CRLs for each
download request. The sensible default (60 seconds) prevents unnecessary workload but still assures provision of fresh and timely information.

**Note:** This time MUST be less than the validity period of CRLs minus the safety margin (`crl-refresh-margin-seconds`) described above.

**Database table:**

A new database table is introduced to store CRL metadata for all configured CA instances. This table use the following structure
(expressed as MySQL create statement):

```
CREATE TABLE `crl_metadata` (
`instance` varchar(255) NOT NULL,
`crl_number` varchar(255) DEFAULT NULL,
`issue_time` bigint DEFAULT NULL,
`next_update` bigint DEFAULT NULL,
`rev_count` int DEFAULT NULL,
PRIMARY KEY (`instance`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3;
```

This table must be created before this version of the application will work if a DB is used to store CA repository data.

### 1.2.0

This version does not require configuration of logotypes and icons. Any use of these graphical images has been removed.

Caused by the update of HSM library, the only option remaining to configure a HSM slot is via a single PKCS#11 configuration file.

### 1.1.0

A new section 2.2.2.8 CA repository configuration is added, describing the configuration settings for setting up
a CA repository using database storage.

