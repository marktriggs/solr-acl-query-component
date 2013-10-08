# Building

To build (using Gradle):

     gradle build

This will produce a `.jar` file under:

     build/libs/solr-acl-query-component.jar 


# Installation

You can install this query component into your Solr distribution by
placing it under your `lib` directory (creating it if it doesn't
already exist):

     mkdir -p solr-x.y.z/example/solr/lib
     cp /path/to/solr-acl-query-component.jar solr-x.y.z/example/solr/lib

Then modify your `solrconfig.xml` file to include a new element under
its `<config>` block:

     <searchComponent name="query" class="com.teaspoonconsulting.solracls.SolrACLQueryComponent">
       <str name="principalsField">readers</str>
       <str name="principalsParameter">readers</str>
       <int name="maxCacheEntries">512</int>
       <int name="cacheLowWaterMark">256</int>
     </searchComponent>

The parameters are as follows:

  * **principalsField** -- A multi-valued Solr StringField that contains the list of groups/users allowed to view each document. 

  * **principalsParameter** -- The request parameter that will contain
    the list of users/groups to restrict the current search to.  For
    example:

         GET /solr/select?q=something&readers=shirley,alex,linh
    
  * **maxCacheEntries** -- The maximum number of search filters that
    will be stored in memory at a time.  Each filter will require one
    bit per document in the index, plus some fixed amount of overhead.

  * **cacheLowWaterMark** -- When the cache fills up, the
    least recently used entries will be discarded until the cache has
    this many entries remaining.


# Statistics

This query component reports some statistics to help monitor the
sizing of its internal cache.  You can find these by browsing to your
Solr console:

     http://localhost:8983/solr/
  
Then:

  * Select your Solr core

  * Click `Plugins / Stats`
  
  * Select `OTHER`
  
  * Expand `query`
