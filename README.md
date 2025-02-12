# Iceberg Hadoop Root Path Bug MVP

MVP demo to show 1.7.1 Apache Iceberg Hadoop catalog is buggy for root level warehouse paths like `s3://mybucketname/`.

## Overview
Path concatenation at e.g. [this line](https://github.com/apache/iceberg/blob/31e9dc768cef47df5eb007577f25ef3bbff86f61/core/src/main/java/org/apache/iceberg/hadoop/HadoopCatalog.java#L194) is buggy for root level paths.
So calling `sparkSession.catalog().listTables("namespace");` with warehouse path `s3a://test-bucket` will wrongly concatenate to `s3a://test-bucketnamespace` and throw!

## Affected Iceberg versions

`org.apache.iceberg:iceberg-core:1.7.1` and earlier

## Prerequisites

- Java 11
- Gradle
- Docker (used for LocalStack S3) 

## JVM dependency
The appearance of the bug seems related to the JVM, I've tested a few,

**confirmed broken (bug occurs):**
* 11.0.19-zulu
* 11.0.26-zulu
* 17.0.12-oracle

**confirmed OK (bug doesn't occur):**
* 17.0.12-zulu
* 21.0.6-oracle

## Running the MVP

execute
```sh
./gradlew clean run
```

## Non-buggy run for non-root-level warehouse paths

If you uncomment this line in Main.java
```
//  folder = folderNameNonEmptyWorks;
```
the warehouse directory will be non-root, and the error won't occur any more, you'll see this line printed twice when running `./gradlew clean run`
```
Expected normal non-buggy behavior for non-existing namespace, NoSuchNamespaceException thrown
```
