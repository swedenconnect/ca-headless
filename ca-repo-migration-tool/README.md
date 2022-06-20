# CA Repository migration tool

----

This folder holds a supplementary CA repository migration tool for migration between a JSON file based CA repository and a Database repository

## Scope
The Headless CA project provides 2 alternative implementations of a CA repository

1) Based on a Database (Using JPA), or
2) Based on JSON data file storage

If a CA is created using one type of repository, and there is a need to migrate to using the other type, then this tool can be used to list the
current differences between configured database and file based storage as well as function to copy records from on type to the other.

## Building the tool

Building source codes referred to here requires maven version 3.3 or higher.

This tool is built using the following command

> mvn clean install

## Usage

The executable file `repobigrate.jar` is located in the target folder.

This tool is a command line tool run using the following basic command:

> java -jar repomigrate.jar [options]

A help menu is available by the following command:

> java -jar repomigrate.jar -help

This produce the following output:

```
usage: java -jar repomigrate.jar [options]
-d <arg>     Configuration directory for the CA service
-dbmerge     Include this argument to merge certificates in the file repository into the database repository
-filemerge   Include this argument to merge certificates in the database repository into the file repository
-help        Print this message
-list        List available certificates in present repositories
-log         Enable display of process logging
-v           Verbose output
```

### Target configuration folder

For this tool to be able to do anything useful the working directory or the `-d` option must be the configuration data directory of
the CA service being migrated. The `application.properties` file of this directory MUST provide configuration data for all relevant CA instances
and MUST contain valid JPA database properties for the Database used in the migration process.

In other words, before running this tool, make sure that the database is properly setup and configured before attempting to run this tool.

### List

It's advisable to first run the -list command in order to make sure that all db connections are setup correctly and that
a valid CA configuration directory is located.

For a CA configured at /opt/ca, the following command executes the -list command:

> java -jar repomigrate.jar -d /opt/ca -list

Example output:

```
CA Repository migration tool version 1.0.0
Configuring CA Repositories ...
Merge status information : Verbose = false

Merge status for instance: ca01
---------------------------------------------------------
File repo certs missing in DB repo (15)
DB repo certs missing in File repo (0)
Duplicate cert records (0): 

Merge status for instance: rot01
---------------------------------------------------------
File repo certs missing in DB repo (4)
DB repo certs missing in File repo (0)
Duplicate cert records (0): 

Merge status for instance: tls-client
---------------------------------------------------------
File repo certs missing in DB repo (11)
DB repo certs missing in File repo (0)
Duplicate cert records (0): 
```

This output illustrates a situation where the Database is empty and all certificate records currently are stored in file storage.

### Merge

To merge certificate records in file storage into Database storage (according to the example above), execute the following command:

> java -jar repomigrate.jar -d /opt/ca -dbmerge

The expected output following the example above is:

```
CA Repository migration tool version 1.0.0
Configuring CA Repositories ...
Merging CA repositories
Merging repository data for instance: ca01
---------------------------------------------------------
Merging file repository certs to DB:
Merged 15 certificates to DB repository

Merging repository data for instance: rot01
---------------------------------------------------------
Merging file repository certs to DB:
Merged 4 certificates to DB repository

Merging repository data for instance: tls-client
---------------------------------------------------------
Merging file repository certs to DB:
Merged 11 certificates to DB repository
```

If on the other hand, the current records resides in a Database, and the target is to merge these records to file storage, the corresponding command is:

> java -jar repomigrate.jar -d /opt/ca -filemerge


**IMPORTANT NOTE:**
Please observe that only non-duplicate records are copied. If the same certificate is present in both repositories, it will not be copied.
If that certificate is revoked in one repository, but not in the other, this revocation status will NOT be copied.

It is therefore advisable to do migration with one empty repository (target) and the other repository holding all certificate records to be copied int the target.


