dd-poc-convert-av-dataset
=========================

Converts an exported EASY dataset to an AV deposit.

SYNOPSIS
--------

```shell
dd-poco-convert-av-dataset <in> <out>
```

DESCRIPTION
-----------
PoC for a tool that will help with the migration of AV datasets from EASY to the Data Stations.


INSTALLATION AND CONFIGURATION
------------------------------
Currently, this project is built as an RPM package for RHEL8 compatible OSes and later. The RPM will install the binaries to
`/opt/dans.knaw.nl/dd-poc-convert-av-dataset` and the configuration files to `/etc/opt/dans.knaw.nl/dd-poc-convert-av-dataset`. The configuration options are documented by
comments in the default configuration file `config.yml`.

To install the module on systems that do not support RPM, you can copy and unarchive the tarball to the target host. You will have to take care of placing the
files in the correct locations for your system yourself. For instructions on building the tarball, see next section.

BUILDING FROM SOURCE
--------------------
Prerequisites:

* Java 17 or higher
* Maven 3.6.3 or higher
* RPM

Steps:

```shell 
git clone https://github.com/DANS-KNAW/dd-poc-convert-av-dataset.git
cd dd-poc-convert-av-dataset
mvn clean install
```

If the `rpm` executable is found at `/usr/local/bin/rpm`, the build profile that includes the RPM packaging will be activated. If `rpm` is available, but at a
different path, then activate it by using Maven's `-P` switch: `mvn -Pprm install`.

Alternatively, to build the tarball execute:

```bash
mvn clean install assembly:single
```

