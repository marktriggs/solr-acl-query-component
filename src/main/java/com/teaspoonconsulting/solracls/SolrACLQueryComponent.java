/**
  * Copyright 2012, Mark Triggs
  *
  *  Licensed under the Apache License, Version 2.0 (the "License");
  *  you may not use this file except in compliance with the License.
  *  You may obtain a copy of the License at
  *
  *      http://www.apache.org/licenses/LICENSE-2.0
  *
  *  Unless required by applicable law or agreed to in writing, software
  *  distributed under the License is distributed on an "AS IS" BASIS,
  *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  *  See the License for the specific language governing permissions and
  *  limitations under the License.
  */

package com.teaspoonconsulting.solracls;


import org.apache.solr.handler.component.QueryComponent;
import org.apache.solr.handler.component.ResponseBuilder;

import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.util.ConcurrentLRUCache;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.io.IOException;

import org.apache.lucene.index.AtomicReader;
import org.apache.lucene.index.AtomicReaderContext;
import org.apache.lucene.index.DocsEnum;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.CachingWrapperFilter;
import org.apache.lucene.search.ConstantScoreQuery;
import org.apache.lucene.search.DocIdSet;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.Filter;
import org.apache.lucene.search.Query;
import org.apache.lucene.util.Bits;
import org.apache.lucene.util.OpenBitSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class SolrACLQueryComponent extends QueryComponent
{
    private static final Logger LOGGER = LoggerFactory.getLogger(SolrACLQueryComponent.class);
    private ConcurrentLRUCache<String, Query> filterCache = null;

    private String principalsParameter = null;
    private String principalsField = null;

    private ConstantScoreQuery buildFilterForPrincipals(final String[] principals)
    {
        Filter f =
            new Filter ()
            {
                public DocIdSet getDocIdSet (AtomicReaderContext context, Bits acceptDocs)
                {
                    long start = System.currentTimeMillis();

                    AtomicReader rdr = context.reader();
                    OpenBitSet bits = new OpenBitSet(rdr.maxDoc());

                    for (String principal : principals) {
                        try {
                            DocsEnum td = rdr.termDocsEnum(new Term(principalsField, principal));

                            if (td == null) {
                                continue;
                            }

                            while (td.nextDoc() != DocIdSetIterator.NO_MORE_DOCS) {
                                if (acceptDocs == null || acceptDocs.get(td.docID())) {
                                    bits.set(td.docID());
                                }
                            }

                        } catch (IOException e) {
                            return null;
                        }
                    }

                    LOGGER.info("Building {}-bit filter for segment [{}]" + rdr + "] took {} milliseconds",
                                new Object[] { rdr.maxDoc(), rdr, (System.currentTimeMillis() - start)});

                    return bits;
                }
            };

        return new ConstantScoreQuery(new CachingWrapperFilter(f));
    }


    @Override
    public void init(NamedList args)
    {
        super.init(args);

        principalsParameter = (String)args.get("principalsParameter");
        principalsField = (String)args.get("principalsField");

        if (principalsParameter == null || principalsParameter == null) {
            throw new RuntimeException("Both 'principalsParameter' and 'principalsField' must be set!");
        }

        filterCache = new ConcurrentLRUCache<String, Query>((Integer)args.get("maxCacheEntries"),
                                                            (Integer)args.get("cacheLowWaterMark"));
    }


    @Override
    public NamedList getStatistics()
    {
        NamedList result = super.getStatistics();

        if (filterCache == null) {
            return result;
        }

        if (result == null) {
            result = new NamedList();
        }

        ConcurrentLRUCache.Stats stats = filterCache.getStats();

        result.add("ACLFilterCacheCumulativeEvictions", String.valueOf(stats.getCumulativeEvictions()));
        result.add("ACLFilterCacheCumulativeHits", String.valueOf(stats.getCumulativeHits()));
        result.add("ACLFilterCacheCumulativeLookups", String.valueOf(stats.getCumulativeLookups()));
        result.add("ACLFilterCacheCumulativeMisses", String.valueOf(stats.getCumulativeMisses()));
        result.add("ACLFilterCacheCumulativeNonLivePuts", String.valueOf(stats.getCumulativeNonLivePuts()));
        result.add("ACLFilterCacheCumulativePuts", String.valueOf(stats.getCumulativePuts()));
        result.add("ACLFilterCacheCurrentSize", String.valueOf(stats.getCurrentSize()));

        return result;
    }


    @Override
    public void prepare(ResponseBuilder rb) throws IOException
    {
        SolrQueryRequest req = rb.req;
        SolrParams params = req.getParams();

        String principalString = params.get(principalsParameter);

        if (principalString != null) {
            String[] principals = principalString.split(", *");
            Arrays.sort(principals);

            String key = Arrays.toString(principals);
            Query f = filterCache.get(key);

            if (f == null) {
                f = buildFilterForPrincipals(principals);
                filterCache.put(key, f);
            }

            List<Query> filters = rb.getFilters();

            if (filters == null) {
                filters = new ArrayList<Query>();
            }

            filters.add(f);
            rb.setFilters(filters);
        }

        super.prepare(rb);
    }
}
