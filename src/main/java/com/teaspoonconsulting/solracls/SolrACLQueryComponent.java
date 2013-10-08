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

import java.util.Arrays;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.io.IOException;

import org.apache.lucene.index.DocsEnum;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.SegmentReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.CachingWrapperFilter;
import org.apache.lucene.search.ConstantScoreQuery;
import org.apache.lucene.search.DocIdSet;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.Filter;
import org.apache.lucene.search.Query;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.OpenBitSet;


public class SolrACLQueryComponent extends QueryComponent
{
    private ConstantScoreQuery buildFilterForPrincipals(final String[] principals)
    {
        Filter f =
            new Filter () {
                public DocIdSet
                    getDocIdSet (IndexReader.AtomicReaderContext context)
                {
                    long start = System.currentTimeMillis();

                    IndexReader rdr = context.reader;
                    OpenBitSet bits = new OpenBitSet(rdr.maxDoc());

                    for (String principal : principals) {
                        try {
                            DocsEnum td =
                                rdr.termDocsEnum(null,
                                                 "readers",
                                                 new BytesRef(principal));

                            if (td == null) {
                                continue;
                            }

                            while (td.nextDoc() != DocIdSetIterator.NO_MORE_DOCS) {
                                bits.set(td.docID());
                            }

                        } catch (IOException e) {
                            return null;
                        }
                    }

                    System.out.println("\n\nBuilding " + rdr.maxDoc() + "-bit filter for segment [" + rdr +
                                       "] took: " +
                                       (System.currentTimeMillis() - start) +
                                       " msecs");

                    return bits;
                }
            };

        return new ConstantScoreQuery(new CachingWrapperFilter(f));
    }


    private HashMap<String, Query> memoryLeak = new HashMap<String, Query>();


    @Override
    public void prepare(ResponseBuilder rb) throws IOException
    {
        SolrQueryRequest req = rb.req;
        SolrParams params = req.getParams();

        String principalString = params.get("principals");

        if (principalString != null) {
            String[] principals = principalString.split(", *");
            Arrays.sort(principals);

            String key = Arrays.toString(principals);
            Query f = memoryLeak.get(key);

            if (f == null) {
                f = buildFilterForPrincipals(principals);
                memoryLeak.put(key, f);
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