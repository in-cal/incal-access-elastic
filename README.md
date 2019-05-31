# InCal Access ElasticSearch [![version](https://img.shields.io/badge/version-0.1.10-green.svg)](https://elasticsearch.com)

This is a convenient repo-like access layer for Elastic Search based on Elastic4S library.

#### Installation

All you need is **Scala 2.11**. To pull the library you have to add the following dependency to *build.sbt*

```
"org.in-cal" %% "incal-access-elastic % "0.1.10"
```

or to *pom.xml* (if you use maven)

```
<dependency>
    <groupId>org.in-cal</groupId>
    <artifactId>incal-access-elastic_2.11</artifactId>
    <version>0.1.10</version>
</dependency>
```

#### DB

Elastic Search **2.3.4** is required. Any other versions might not work correctly. The release for Debian-like Linux distributions can be installed as:

```sh
wget https://download.elastic.co/elasticsearch/release/org/elasticsearch/distribution/deb/elasticsearch/2.3.4/elasticsearch-2.3.4.deb
sudo dpkg -i elasticsearch-2.3.4.deb
sudo systemctl enable elasticsearch.service
```