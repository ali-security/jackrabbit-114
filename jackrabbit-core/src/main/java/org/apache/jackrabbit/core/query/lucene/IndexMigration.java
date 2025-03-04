/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.jackrabbit.core.query.lucene;

import org.apache.lucene.index.Term;
import org.apache.lucene.index.TermEnum;
import org.apache.lucene.index.FilterIndexReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.CorruptIndexException;
import org.apache.lucene.index.TermPositions;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.store.NoLockFactory;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.FieldSelector;
import org.apache.lucene.document.Fieldable;
import org.apache.lucene.document.Field;
import org.apache.jackrabbit.core.fs.local.FileUtil;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.File;

/**
 * <code>IndexMigration</code> implements a utility that migrates a Jackrabbit
 * 1.4.x index to version 1.5. Until version 1.4.x, indexes used the character
 * '\uFFFF' to separate the name of a property from the value. As of Lucene
 * 2.3 this does not work anymore. See LUCENE-1221. Jackrabbit >= 1.5 uses
 * the character '[' as a separator. Whenever an index is opened from disk, a
 * quick check is run to find out whether a migration is required. See also
 * JCR-1363 for more details.
 */
public class IndexMigration {

    /**
     * The logger instance for this class.
     */
    private static final Logger log = LoggerFactory.getLogger(IndexMigration.class);

    /**
     * Checks if the given <code>index</code> needs to be migrated.
     *
     * @param index the index to check and migration if needed.
     * @param indexDir the directory where the index is stored.
     * @throws IOException if an error occurs while migrating the index.
     */
    public static void migrate(PersistentIndex index, File indexDir) throws IOException {
        log.debug("Checking {} ...", indexDir.getAbsolutePath());
        ReadOnlyIndexReader reader = index.getReadOnlyIndexReader();
        try {
            if (IndexFormatVersion.getVersion(reader).getVersion() >=
                    IndexFormatVersion.V3.getVersion()) {
                // index was created with Jackrabbit 1.5 or higher
                // no need for migration
                log.debug("IndexFormatVersion >= V3, no migration needed");
                return;
            }
            // assert: there is at least one node in the index, otherwise the
            //         index format version would be at least V3
            TermEnum terms = reader.terms(new Term(FieldNames.PROPERTIES, ""));
            try {
                Term t = terms.term();
                if (t.text().indexOf('\uFFFF') == -1) {
                    log.debug("Index already migrated");
                    return;
                }
            } finally {
                terms.close();
            }
        } finally {
            reader.release();
        }

        // if we get here then the index must be migrated
        log.debug("Index requires migration {}", indexDir.getAbsolutePath());

        // make sure readers are closed, otherwise the directory
        // cannot be deleted
        index.releaseWriterAndReaders();

        File migrationDir = new File(indexDir.getAbsoluteFile().getParentFile(), indexDir.getName() + "_v2.3");
        if (migrationDir.exists()) {
            FileUtil.delete(migrationDir);
        }
        if (!migrationDir.mkdirs()) {
            throw new IOException("failed to create directory " +
                    migrationDir.getAbsolutePath());
        }
        FSDirectory fsDir = FSDirectory.getDirectory(migrationDir,
                NoLockFactory.getNoLockFactory());
        try {
            IndexWriter writer = new IndexWriter(fsDir, new JackrabbitAnalyzer());
            try {
                IndexReader r = new MigrationIndexReader(
                        IndexReader.open(index.getDirectory()));
                try {
                    writer.addIndexes(new IndexReader[]{r});
                    writer.close();
                } finally {
                    r.close();
                }
            } finally {
                writer.close();
            }
        } finally {
            fsDir.close();
        }
        FileUtil.delete(indexDir);
        if (!migrationDir.renameTo(indexDir)) {
            throw new IOException("failed to move migrated directory " +
                    migrationDir.getAbsolutePath());
        }
        log.info("Migrated " + indexDir.getAbsolutePath());
    }

    //---------------------------< internal helper >----------------------------

    /**
     * An index reader that migrates stored field values and term text on the
     * fly.
     */
    private static class MigrationIndexReader extends FilterIndexReader {

        public MigrationIndexReader(IndexReader in) {
            super(in);
        }

        public Document document(int n, FieldSelector fieldSelector)
                throws CorruptIndexException, IOException {
            Document doc = super.document(n, fieldSelector);
            Fieldable[] fields = doc.getFieldables(FieldNames.PROPERTIES);
            if (fields != null) {
                doc.removeFields(FieldNames.PROPERTIES);
                for (int i = 0; i < fields.length; i++) {
                    String value = fields[i].stringValue();
                    value = value.replace('\uFFFF', '[');
                    doc.add(new Field(FieldNames.PROPERTIES, value,
                            Field.Store.YES, Field.Index.NO_NORMS));
                }
            }
            return doc;
        }

        public TermEnum terms() throws IOException {
            return new MigrationTermEnum(in.terms());
        }

        public TermPositions termPositions() throws IOException {
            return new MigrationTermPositions(in.termPositions());
        }

        private static class MigrationTermEnum extends FilterTermEnum {

            public MigrationTermEnum(TermEnum in) {
                super(in);
            }

            public Term term() {
                Term t = super.term();
                if (t == null) {
                    return t;
                }
                if (t.field().equals(FieldNames.PROPERTIES)) {
                    String text = t.text();
                    return t.createTerm(text.replace('\uFFFF', '['));
                } else {
                    return t;
                }
            }

            TermEnum unwrap() {
                return in;
            }
        }

        private static class MigrationTermPositions extends FilterTermPositions {

            public MigrationTermPositions(TermPositions in) {
                super(in);
            }

            public void seek(Term term) throws IOException {
                if (term.field().equals(FieldNames.PROPERTIES)) {
                    char[] text = term.text().toCharArray();
                    text[term.text().indexOf('[')] = '\uFFFF';
                    super.seek(term.createTerm(new String(text)));
                } else {
                    super.seek(term);
                }
            }

            public void seek(TermEnum termEnum) throws IOException {
                if (termEnum instanceof MigrationTermEnum) {
                    super.seek(((MigrationTermEnum) termEnum).unwrap());
                } else {
                    super.seek(termEnum);
                }
            }
        }
    }
}
