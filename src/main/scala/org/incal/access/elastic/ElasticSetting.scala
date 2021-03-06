package org.incal.access.elastic

case class ElasticSetting(
  saveRefresh: RefreshPolicy.Value = RefreshPolicy.None,
  saveBulkRefresh: RefreshPolicy.Value = RefreshPolicy.None,
  updateRefresh: RefreshPolicy.Value = RefreshPolicy.None,
  updateBulkRefresh: RefreshPolicy.Value = RefreshPolicy.None,
  scrollBatchSize: Int = 1000,
  useDocScrollSort: Boolean = true,
  indexFieldsLimit: Int = 10000,
  indexSingleTypeMapping: Boolean = true, // will be enforced in 6.x
  shards: Int = 5,
  replicas: Int = 0
)

object RefreshPolicy extends Enumeration {
  val None = Value("false")
  val Immediate = Value("true")
  val WaitUtil = Value("wait_for")
}