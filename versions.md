# Headless CA versions

**Latest current version 1.2.0**

| Version | Comment                                                                              | Date       |
|---------|--------------------------------------------------------------------------------------|------------|
| 1.0.0   | Initial version                                                                      | 2022-02-07 |
| 1.1.0   | Support for DB Certificate repository as the default storage                         | 2022-02-07 |
| 1.1.1   | Fixing daemon bug caused by missing annotation. Added supplementary tools            | 2022-02-14 |
| 1.1.2   | Allowing empty context path for CA                                                   | 2022-02-23 |
| 1.1.3   | Publishing a new CRL directly upon CMC revocation instead of waiting for next update | 2022-02-23 |
| 1.1.4   | Including mitigation of spring core RCE vulnerability                                | 2022-03-31 |
| 1.1.5   | Moving to Spring boot 2.6.6 as a stable mitigation of RCE                            | 2022-03-31 |
| 1.2.0   | Updates to use of the new Credential Support library for key configuration           | 2022-03-31 |
| 1.3.0   | Update to support synchronized CRL metadata for multi server deployment of single CA | 2022-03-31 |



## Important release notes (most recent on top)

Release notes are provided only for updates that require configuration changes or other deployment considerations.

### 1.3.0

This version does not require any configuration update as all new configuration values have sensible defaults.

The following parameter can be set in this version:

> ca-service.config.crl-refresh-margin-seconds

This property value in application.properties defines the age of the latest CRL that must pass before it is allowed to bump
the current CRL metadata for all instances of the service. If this time has not passed, the service will make a
CRL that match the metadata (CRL number, issue time, next update time) of the latest current CRL.

This property has a default value of 60 seconds, which is used if this property is not set.

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

