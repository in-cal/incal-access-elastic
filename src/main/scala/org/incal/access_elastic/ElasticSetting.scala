package org.incal.access_elastic

case class ElasticSetting(
  saveRefresh: Boolean = false,
  saveBulkRefresh: Boolean = false,
  updateRefresh: Boolean = false,
  updateBulkRefresh: Boolean = false,
  scrollBatchSize: Int = 1000
)