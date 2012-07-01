/**
 * eobjects.org DataCleaner
 * Copyright (C) 2010 eobjects.org
 *
 * This copyrighted material is made available to anyone wishing to use, modify,
 * copy, or redistribute it subject to the terms and conditions of the GNU
 * Lesser General Public License, as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public License
 * for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this distribution; if not, write to:
 * Free Software Foundation, Inc.
 * 51 Franklin Street, Fifth Floor
 * Boston, MA  02110-1301  USA
 */
package org.eobjects.datacleaner.lucene;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.SimpleAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Fieldable;
import org.apache.lucene.queryParser.ParseException;
import org.apache.lucene.queryParser.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.eobjects.analyzer.beans.api.Configured;
import org.eobjects.analyzer.beans.api.Initialize;
import org.eobjects.analyzer.beans.api.OutputColumns;
import org.eobjects.analyzer.beans.api.Transformer;
import org.eobjects.analyzer.beans.api.TransformerBean;
import org.eobjects.analyzer.data.InputColumn;
import org.eobjects.analyzer.data.InputRow;
import org.eobjects.analyzer.util.StringUtils;

@TransformerBean("Search in Lucene search index")
public class SearchTransformer implements Transformer<Object> {

    @Configured
    InputColumn<String> searchInput;

    // TODO: Add converter
    @Configured
    SearchIndex searchIndex;

    private IndexSearcher indexSearcher;
    private QueryParser queryParser;

    @Override
    public OutputColumns getOutputColumns() {
        final String[] columnNames = new String[] { "Search result", "Score" };
        final Class<?>[] columnTypes = new Class[] { Map.class, Number.class };
        return new OutputColumns(columnNames, columnTypes);
    }

    @Initialize
    public void init() {
        indexSearcher = searchIndex.getSearcher();

        final Analyzer analyzer = new SimpleAnalyzer(Constants.VERSION);
        queryParser = new QueryParser(Constants.VERSION, Constants.SEARCH_FIELD_NAME, analyzer);
    }

    @Override
    public Object[] transform(InputRow row) {
        final Object[] result = new Object[2];
        result[1] = 0;

        final String searchText = row.getValue(searchInput);

        if (StringUtils.isNullOrEmpty(searchText)) {
            return result;
        }

        final Query query;
        try {
            query = queryParser.parse(searchText);
        } catch (ParseException e) {
            throw new IllegalStateException(e);
        }

        final TopDocs searchResult;
        try {
            searchResult = indexSearcher.search(query, 1);
        } catch (IOException e) {
            throw new IllegalStateException("Searching index threw exception", e);
        }

        if (searchResult == null || searchResult.totalHits == 0) {
            return result;
        }

        final ScoreDoc scoreDoc = searchResult.scoreDocs[0];
        // add score
        final Document document;
        try {
            document = indexSearcher.doc(scoreDoc.doc);
        } catch (Exception e) {
            throw new IllegalStateException("Fetching document from index threw exception", e);
        }

        result[0] = toMap(document);
        result[1] = scoreDoc.score;

        return result;
    }

    protected static Map<String, String> toMap(Document document) {
        final Map<String, String> result = new LinkedHashMap<String, String>();
        final List<Fieldable> fields = document.getFields();
        for (Fieldable fieldable : fields) {
            final String name = fieldable.name();
            final String value = document.get(name);
            result.put(name, value);
        }
        return result;
    }

}