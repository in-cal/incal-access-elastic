# InCal Access ElasticSearch [![version](https://img.shields.io/badge/version-0.3.0-green.svg)](https://in-cal.org) [![License](https://img.shields.io/badge/License-Apache%202.0-lightgrey.svg)](https://www.apache.org/licenses/LICENSE-2.0) [![Build Status](https://travis-ci.com/in-cal/incal-access-elastic.svg?branch=master)](https://travis-ci.com/in-cal/incal-access-elastic)

This is a convenient repo-like access layer for Elastic Search based on [Elastic4S](https://github.com/sksamuel/elastic4s) library.

#### Installation

All you need is **Scala 2.11** or **2.12**. To pull the library you have to add the following dependency to *build.sbt*

```
"org.in-cal" %% "incal-access-elastic" % "0.3.0"
```

or to *pom.xml* (if you use maven)

```
<dependency>
    <groupId>org.in-cal</groupId>
    <artifactId>incal-access-elastic_2.11</artifactId>
    <version>0.3.0</version>
</dependency>
```

respectively for Scala 2.12 as

```
<dependency>
    <groupId>org.in-cal</groupId>
    <artifactId>incal-access-elastic_2.12</artifactId>
    <version>0.3.0</version>
</dependency>
```

#### DB

Elastic Search **5.6.10** is required. Any other version might not work correctly. The release for Debian-like Linux distributions can be installed as:

```sh
wget https://artifacts.elastic.co/downloads/elasticsearch/elasticsearch-5.6.10.deb
sudo dpkg -i elasticsearch-5.6.10.deb
sudo systemctl enable elasticsearch.service
sudo service elasticsearch start
```
