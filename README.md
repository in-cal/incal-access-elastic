# InCal Access ElasticSearch [![version](https://img.shields.io/badge/version-0.2.0-green.svg)](https://elasticsearch.com)

This is a convenient repo-like access layer for Elastic Search based on Elastic4S library.

#### Installation

All you need is **Scala 2.11**. To pull the library you have to add the following dependency to *build.sbt*

```
"org.in-cal" %% "incal-access-elastic" % "0.2.0"
```

or to *pom.xml* (if you use maven)

```
<dependency>
    <groupId>org.in-cal</groupId>
    <artifactId>incal-access-elastic_2.11</artifactId>
    <version>0.2.0</version>
</dependency>
```

#### DB

Elastic Search **5.6.10** is required. Any other version might not work correctly. The release for Debian-like Linux distributions can be installed as:

```sh
wget https://artifacts.elastic.co/downloads/elasticsearch/elasticsearch-5.6.10.deb
sudo dpkg -i elasticsearch-5.6.10.deb
sudo systemctl enable elasticsearch.service
```