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

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.TermEnum;
import org.apache.lucene.search.FilteredTermEnum;

import java.io.IOException;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Iterator;
import java.util.ArrayList;
import java.util.List;

/**
 * Implements a wildcard term enum that optionally supports embedded property
 * names in lucene term texts.
 */
class WildcardTermEnum extends FilteredTermEnum implements TransformConstants {

    /**
     * The pattern matcher.
     */
    private final Matcher pattern;

    /**
     * The lucene field to search.
     */
    private final String field;

    /**
     * The term prefix without wildcards
     */
    private final String prefix;

    /**
     * Flag that indicates the end of the term enum.
     */
    private boolean endEnum = false;

    /**
     * The input for the pattern matcher.
     */
    private final OffsetCharSequence input;

    /**
     * How terms from the index are transformed.
     */
    private final int transform;

    /**
     * Creates a new <code>WildcardTermEnum</code>.
     *
     * @param reader    the index reader.
     * @param field     the lucene field to search.
     * @param propName  the embedded jcr property name or <code>null</code> if
     *                  there is not embedded property name.
     * @param pattern   the pattern to match the values.
     * @param transform the transformation that should be applied to the term
     *                  enum from the index reader.
     * @throws IOException              if an error occurs while reading from
     *                                  the index.
     * @throws IllegalArgumentException if <code>transform</code> is not a valid
     *                                  value.
     */
    public WildcardTermEnum(IndexReader reader,
                            String field,
                            String propName,
                            String pattern,
                            int transform) throws IOException {
        if (transform < TRANSFORM_NONE || transform > TRANSFORM_UPPER_CASE) {
            throw new IllegalArgumentException("invalid transform parameter");
        }
        this.field = field;
        this.transform = transform;

        int idx = 0;

        if (transform == TRANSFORM_NONE) {
            // optimize the term comparison by removing the prefix from the pattern
            // and therefore use a more precise range scan
            while (idx < pattern.length()
                    && Character.isLetterOrDigit(pattern.charAt(idx))) {
                idx++;
            }

            if (propName == null) {
                prefix = pattern.substring(0, idx);
            } else {
                prefix = FieldNames.createNamedValue(propName, pattern.substring(0, idx));
            }
        } else {
            prefix = FieldNames.createNamedValue(propName, "");
        }

        // initialize with prefix as dummy value
        input = new OffsetCharSequence(prefix.length(), prefix, transform);
        this.pattern = createRegexp(pattern.substring(idx)).matcher(input);

        if (transform == TRANSFORM_NONE) {
            setEnum(reader.terms(new Term(field, prefix)));
        } else {
            setEnum(new LowerUpperCaseTermEnum(reader, field, propName, pattern, transform));
        }
    }

    /**
     * @inheritDoc
     */
    protected boolean termCompare(Term term) {
        if (transform == TRANSFORM_NONE) {
            if (term.field() == field && term.text().startsWith(prefix)) {
                input.setBase(term.text());
                return pattern.reset().matches();
            }
            endEnum = true;
            return false;
        } else {
            // pre filtered, no need to check
            return true;
        }
    }

    /**
     * @inheritDoc
     */
    public float difference() {
        return 1.0f;
    }

    /**
     * @inheritDoc
     */
    protected boolean endEnum() {
        return endEnum;
    }

    //--------------------------< internal >------------------------------------

    /**
     * Creates a regexp from <code>likePattern</code>.
     *
     * @param likePattern the pattern.
     * @return the regular expression <code>Pattern</code>.
     */
    private Pattern createRegexp(String likePattern) {
        // - escape all non alphabetic characters
        // - escape constructs like \<alphabetic char> into \\<alphabetic char>
        // - replace non escaped _ % into . and .*
        StringBuffer regexp = new StringBuffer();
        boolean escaped = false;
        for (int i = 0; i < likePattern.length(); i++) {
            if (likePattern.charAt(i) == '\\') {
                if (escaped) {
                    regexp.append("\\\\");
                    escaped = false;
                } else {
                    escaped = true;
                }
            } else {
                if (Character.isLetterOrDigit(likePattern.charAt(i))) {
                    if (escaped) {
                        regexp.append("\\\\").append(likePattern.charAt(i));
                        escaped = false;
                    } else {
                        regexp.append(likePattern.charAt(i));
                    }
                } else {
                    if (escaped) {
                        regexp.append('\\').append(likePattern.charAt(i));
                        escaped = false;
                    } else {
                        switch (likePattern.charAt(i)) {
                            case '_':
                                regexp.append('.');
                                break;
                            case '%':
                                regexp.append(".*");
                                break;
                            default:
                                regexp.append('\\').append(likePattern.charAt(i));
                        }
                    }
                }
            }
        }
        return Pattern.compile(regexp.toString(), Pattern.DOTALL);
    }

    /**
     * Implements a term enum which respects the transformation flag and
     * matches a pattern on the enumerated terms.
     */
    private class LowerUpperCaseTermEnum extends TermEnum {

        /**
         * The matching terms
         */
        private final Map orderedTerms = new LinkedHashMap();

        /**
         * Iterator over all matching terms
         */
        private final Iterator it;

        public LowerUpperCaseTermEnum(IndexReader reader,
                                      String field,
                                      String propName,
                                      String pattern,
                                      int transform) throws IOException {
            if (transform != TRANSFORM_LOWER_CASE && transform != TRANSFORM_UPPER_CASE) {
                throw new IllegalArgumentException("transform");
            }

            // check if pattern never matches
            boolean neverMatches = false;
            for (int i = 0; i < pattern.length() && !neverMatches; i++) {
                if (transform == TRANSFORM_LOWER_CASE) {
                    neverMatches = Character.isUpperCase(pattern.charAt(i));
                } else if (transform == TRANSFORM_UPPER_CASE) {
                    neverMatches = Character.isLowerCase(pattern.charAt(i));
                }
            }

            if (!neverMatches) {
                // create range scans
                List rangeScans = new ArrayList(2);
                try {
                    int idx = 0;
                    while (idx < pattern.length()
                            && Character.isLetterOrDigit(pattern.charAt(idx))) {
                        idx++;
                    }
                    String patternPrefix = pattern.substring(0, idx);
                    if (patternPrefix.length() == 0) {
                        // scan full property range
                        String prefix = FieldNames.createNamedValue(propName, "");
                        String limit = FieldNames.createNamedValue(propName, "\uFFFF");
                        rangeScans.add(new RangeScan(reader,
                                new Term(field, prefix), new Term(field, limit)));
                    } else {
                        // start with initial lower case
                        StringBuffer lowerLimit = new StringBuffer(patternPrefix.toUpperCase());
                        lowerLimit.setCharAt(0, Character.toLowerCase(lowerLimit.charAt(0)));
                        String prefix = FieldNames.createNamedValue(propName, lowerLimit.toString());

                        StringBuffer upperLimit = new StringBuffer(patternPrefix.toLowerCase());
                        upperLimit.append('\uFFFF');
                        String limit = FieldNames.createNamedValue(propName, upperLimit.toString());
                        rangeScans.add(new RangeScan(reader,
                                new Term(field, prefix), new Term(field, limit)));

                        // second scan with upper case start
                        prefix = FieldNames.createNamedValue(propName, patternPrefix.toUpperCase());
                        upperLimit = new StringBuffer(patternPrefix.toLowerCase());
                        upperLimit.setCharAt(0, Character.toUpperCase(upperLimit.charAt(0)));
                        upperLimit.append('\uFFFF');
                        limit = FieldNames.createNamedValue(propName, upperLimit.toString());
                        rangeScans.add(new RangeScan(reader,
                                new Term(field, prefix), new Term(field, limit)));
                    }

                    // do range scans with pattern matcher
                    for (Iterator it = rangeScans.iterator(); it.hasNext(); ) {
                        RangeScan scan = (RangeScan) it.next();
                        do {
                            Term t = scan.term();
                            if (t != null) {
                                input.setBase(t.text());
                                if (WildcardTermEnum.this.pattern.reset().matches()) {
                                    orderedTerms.put(t, new Integer(scan.docFreq()));
                                }
                            }
                        } while (scan.next());
                    }

                } finally {
                    // close range scans
                    Iterator it = rangeScans.iterator();
                    while (it.hasNext()) {
                        RangeScan scan = (RangeScan) it.next();
                        try {
                            scan.close();
                        } catch (IOException e) {
                            // ignore
                        }
                    }
                }
            }

            it = orderedTerms.keySet().iterator();
            getNext();
        }

        /**
         * The current term in this enum.
         */
        private Term current;

        /**
         * {@inheritDoc}
         */
        public boolean next() {
            getNext();
            return current != null;
        }

        /**
         * {@inheritDoc}
         */
        public Term term() {
            return current;
        }

        /**
         * {@inheritDoc}
         */
        public int docFreq() {
            Integer docFreq = (Integer) orderedTerms.get(current);
            return docFreq != null ? docFreq.intValue() : 0;
        }

        /**
         * {@inheritDoc}
         */
        public void close() {
            // nothing to do here
        }

        /**
         * Sets the current field to the next term in this enum or to
         * <code>null</code> if there is no next.
         */
        private void getNext() {
            current = it.hasNext() ? (Term) it.next() : null;
        }
    }
}
