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
package org.apache.jackrabbit.core.state;

import org.apache.commons.collections.map.LinkedMap;
import org.apache.commons.collections.MapIterator;
import org.apache.commons.collections.OrderedMapIterator;
import org.apache.jackrabbit.core.NodeId;
import org.apache.jackrabbit.core.util.EmptyLinkedMap;
import org.apache.jackrabbit.spi.Name;

import java.util.List;
import java.util.HashMap;
import java.util.Collections;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Collection;
import java.util.ListIterator;
import java.util.Map;

/**
 * <code>ChildNodeEntries</code> represents an insertion-ordered
 * collection of <code>ChildNodeEntry</code>s that also maintains
 * the index values of same-name siblings on insertion and removal.
 * <p/>
 * <code>ChildNodeEntries</code> also provides an unmodifiable
 * <code>List</code> view.
 */
class ChildNodeEntries implements List, Cloneable {

    /**
     * Insertion-ordered map of entries
     * (key=NodeId, value=entry)
     */
    private LinkedMap entries;

    /**
     * Map used for lookup by name
     * (key=name, value=either a single entry or a list of sns entries)
     */
    private Map nameMap;

    /**
     * Indicates whether the entries and nameMap are shared with another
     * ChildNodeEntries instance.
     */
    private boolean shared;

    ChildNodeEntries() {
        init();
    }

    ChildNodeEntry get(NodeId id) {
        return (ChildNodeEntry) entries.get(id);
    }

    List get(Name nodeName) {
        Object obj = nameMap.get(nodeName);
        if (obj == null) {
            return Collections.EMPTY_LIST;
        }
        if (obj instanceof ArrayList) {
            // map entry is a list of siblings
            return Collections.unmodifiableList((ArrayList) obj);
        } else {
            // map entry is a single child node entry
            return Collections.singletonList(obj);
        }
    }

    ChildNodeEntry get(Name nodeName, int index) {
        if (index < 1) {
            throw new IllegalArgumentException("index is 1-based");
        }

        Object obj = nameMap.get(nodeName);
        if (obj == null) {
            return null;
        }
        if (obj instanceof ArrayList) {
            // map entry is a list of siblings
            ArrayList siblings = (ArrayList) obj;
            if (index <= siblings.size()) {
                return (ChildNodeEntry) siblings.get(index - 1);
            }
        } else {
            // map entry is a single child node entry
            if (index == 1) {
                return (ChildNodeEntry) obj;
            }
        }
        return null;
    }

    ChildNodeEntry add(Name nodeName, NodeId id) {
        ensureModifiable();
        List siblings = null;
        int index = 0;
        Object obj = nameMap.get(nodeName);
        if (obj != null) {
            if (obj instanceof ArrayList) {
                // map entry is a list of siblings
                siblings = (ArrayList) obj;
                if (siblings.size() > 0) {
                    // reuse immutable Name instance from 1st same name sibling
                    // in order to help gc conserving memory
                    nodeName = ((ChildNodeEntry) siblings.get(0)).getName();
                }
            } else {
                // map entry is a single child node entry,
                // convert to siblings list
                siblings = new ArrayList();
                siblings.add(obj);
                nameMap.put(nodeName, siblings);
            }
            index = siblings.size();
        }

        index++;

        ChildNodeEntry entry = new ChildNodeEntry(nodeName, id, index);
        if (siblings != null) {
            siblings.add(entry);
        } else {
            nameMap.put(nodeName, entry);
        }
        entries.put(id, entry);

        return entry;
    }

    void addAll(List entriesList) {
        Iterator iter = entriesList.iterator();
        while (iter.hasNext()) {
            ChildNodeEntry entry = (ChildNodeEntry) iter.next();
            // delegate to add(Name, String) to maintain consistency
            add(entry.getName(), entry.getId());
        }
    }

    public ChildNodeEntry remove(Name nodeName, int index) {
        if (index < 1) {
            throw new IllegalArgumentException("index is 1-based");
        }

        ensureModifiable();
        Object obj = nameMap.get(nodeName);
        if (obj == null) {
            return null;
        }

        if (obj instanceof ChildNodeEntry) {
            // map entry is a single child node entry
            if (index != 1) {
                return null;
            }
            ChildNodeEntry removedEntry = (ChildNodeEntry) obj;
            nameMap.remove(nodeName);
            entries.remove(removedEntry.getId());
            return removedEntry;
        }

        // map entry is a list of siblings
        List siblings = (ArrayList) obj;
        if (index > siblings.size()) {
            return null;
        }

        // remove from siblings list
        ChildNodeEntry removedEntry = (ChildNodeEntry) siblings.remove(index - 1);
        // remove from ordered entries map
        entries.remove(removedEntry.getId());

        // update indices of subsequent same-name siblings
        for (int i = index - 1; i < siblings.size(); i++) {
            ChildNodeEntry oldEntry = (ChildNodeEntry) siblings.get(i);
            ChildNodeEntry newEntry = new ChildNodeEntry(nodeName, oldEntry.getId(), oldEntry.getIndex() - 1);
            // overwrite old entry with updated entry in siblings list
            siblings.set(i, newEntry);
            // overwrite old entry with updated entry in ordered entries map
            entries.put(newEntry.getId(), newEntry);
        }

        // clean up name lookup map if necessary
        if (siblings.size() == 0) {
            // no more entries with that name left:
            // remove from name lookup map as well
            nameMap.remove(nodeName);
        } else if (siblings.size() == 1) {
            // just one entry with that name left:
            // discard siblings list and update name lookup map accordingly
            nameMap.put(nodeName, siblings.get(0));
        }

        // we're done
        return removedEntry;
    }

    /**
     * Removes the child node entry refering to the node with the given id.
     *
     * @param id id of node whose entry is to be removed.
     * @return the removed entry or <code>null</code> if there is no such entry.
     */
    ChildNodeEntry remove(NodeId id) {
        ChildNodeEntry entry = (ChildNodeEntry) entries.get(id);
        if (entry != null) {
            return remove(entry.getName(), entry.getIndex());
        }
        return entry;
    }

    /**
     * Removes the given child node entry.
     *
     * @param entry entry to be removed.
     * @return the removed entry or <code>null</code> if there is no such entry.
     */
    public ChildNodeEntry remove(ChildNodeEntry entry) {
        return remove(entry.getName(), entry.getIndex());
    }

    /**
     * Removes all child node entries
     */
    public void removeAll() {
        init();
    }

    /**
     * Returns a list of <code>ChildNodeEntry</code>s who do only exist in
     * <code>this</code> but not in <code>other</code>.
     * <p/>
     * Note that two entries are considered identical in this context if
     * they have the same name and uuid, i.e. the index is disregarded
     * whereas <code>ChildNodeEntry.equals(Object)</code> also compares
     * the index.
     *
     * @param other entries to be removed
     * @return a new list of those entries that do only exist in
     *         <code>this</code> but not in <code>other</code>
     */
    List removeAll(ChildNodeEntries other) {
        if (entries.isEmpty()) {
            return Collections.EMPTY_LIST;
        }
        if (other.isEmpty()) {
            return this;
        }

        List result = new ArrayList();
        Iterator iter = iterator();
        while (iter.hasNext()) {
            ChildNodeEntry entry = (ChildNodeEntry) iter.next();
            ChildNodeEntry otherEntry = other.get(entry.getId());
            if (entry == otherEntry) {
                continue;
            }
            if (otherEntry == null
                    || !entry.getName().equals(otherEntry.getName())) {
                result.add(entry);
            }
        }

        return result;
    }

    /**
     * Returns a list of <code>ChildNodeEntry</code>s who do exist in
     * <code>this</code> <i>and</i> in <code>other</code>.
     * <p/>
     * Note that two entries are considered identical in this context if
     * they have the same name and uuid, i.e. the index is disregarded
     * whereas <code>ChildNodeEntry.equals(Object)</code> also compares
     * the index.
     *
     * @param other entries to be retained
     * @return a new list of those entries that do exist in
     *         <code>this</code> <i>and</i> in <code>other</code>
     */
    List retainAll(ChildNodeEntries other) {
        if (entries.isEmpty()
                || other.isEmpty()) {
            return Collections.EMPTY_LIST;
        }

        List result = new ArrayList();
        Iterator iter = iterator();
        while (iter.hasNext()) {
            ChildNodeEntry entry = (ChildNodeEntry) iter.next();
            ChildNodeEntry otherEntry = other.get(entry.getId());
            if (entry == otherEntry) {
                result.add(entry);
            } else if (otherEntry != null
                    && entry.getName().equals(otherEntry.getName())) {
                result.add(entry);
            }
        }

        return result;
    }

    //-------------------------------------------< unmodifiable List view >
    public boolean contains(Object o) {
        if (o instanceof ChildNodeEntry) {
            return entries.containsKey(((ChildNodeEntry) o).getId());
        } else {
            return false;
        }
    }

    public boolean containsAll(Collection c) {
        Iterator iter = c.iterator();
        while (iter.hasNext()) {
            if (!contains(iter.next())) {
                return false;
            }
        }
        return true;
    }

    public Object get(int index) {
        return entries.getValue(index);
    }

    public int indexOf(Object o) {
        if (o instanceof ChildNodeEntry) {
            return entries.indexOf(((ChildNodeEntry) o).getId());
        } else {
            return -1;
        }
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    public int lastIndexOf(Object o) {
        // entries are unique
        return indexOf(o);
    }

    public Iterator iterator() {
        return new EntriesIterator();
    }

    public ListIterator listIterator() {
        return new EntriesIterator();
    }

    public ListIterator listIterator(int index) {
        if (index < 0 || index >= entries.size()) {
            throw new IndexOutOfBoundsException();
        }
        ListIterator iter = new EntriesIterator();
        while (index-- > 0) {
            iter.next();
        }
        return iter;
    }

    public int size() {
        return entries.size();
    }

    public List subList(int fromIndex, int toIndex) {
        // @todo FIXME does not fulfill the contract of List.subList(int,int)
        return Collections.unmodifiableList(new ArrayList(this).subList(fromIndex, toIndex));
    }

    public Object[] toArray() {
        ChildNodeEntry[] array = new ChildNodeEntry[size()];
        return toArray(array);
    }

    public Object[] toArray(Object[] a) {
        if (!a.getClass().getComponentType().isAssignableFrom(ChildNodeEntry.class)) {
            throw new ArrayStoreException();
        }
        if (a.length < size()) {
            a = new ChildNodeEntry[size()];
        }
        MapIterator iter = entries.mapIterator();
        int i = 0;
        while (iter.hasNext()) {
            iter.next();
            a[i] = entries.getValue(i);
            i++;
        }
        while (i < a.length) {
            a[i++] = null;
        }
        return a;
    }

    public void add(int index, Object element) {
        throw new UnsupportedOperationException();
    }

    public boolean add(Object o) {
        throw new UnsupportedOperationException();
    }

    public boolean addAll(Collection c) {
        throw new UnsupportedOperationException();
    }

    public boolean addAll(int index, Collection c) {
        throw new UnsupportedOperationException();
    }

    public void clear() {
        throw new UnsupportedOperationException();
    }

    public Object remove(int index) {
        throw new UnsupportedOperationException();
    }

    public boolean remove(Object o) {
        throw new UnsupportedOperationException();
    }

    public boolean removeAll(Collection c) {
        throw new UnsupportedOperationException();
    }

    public boolean retainAll(Collection c) {
        throw new UnsupportedOperationException();
    }

    public Object set(int index, Object element) {
        throw new UnsupportedOperationException();
    }

    //------------------------------------------------< Cloneable support >

    /**
     * Returns a shallow copy of this <code>ChildNodeEntries</code> instance;
     * the entries themselves are not cloned.
     *
     * @return a shallow copy of this instance.
     */
    protected Object clone() {
        try {
            ChildNodeEntries clone = (ChildNodeEntries) super.clone();
            if (nameMap != Collections.EMPTY_MAP) {
                clone.shared = true;
                shared = true;
            }
            return clone;
        } catch (CloneNotSupportedException e) {
            // never happens, this class is cloneable
            throw new InternalError();
        }
    }

    //-------------------------------------------------------------< internal >

    /**
     * Initializes the name and entries map with unmodifiable empty instances.
     */
    private void init() {
        nameMap = Collections.EMPTY_MAP;
        entries = EmptyLinkedMap.INSTANCE;
        shared = false;
    }

    /**
     * Ensures that the {@link #nameMap} and {@link #entries} map are
     * modifiable.
     */
    private void ensureModifiable() {
        if (nameMap == Collections.EMPTY_MAP) {
            nameMap = new HashMap();
            entries = new LinkedMap();
        } else if (shared) {
            entries = (LinkedMap) entries.clone();
            nameMap = (Map) ((HashMap) nameMap).clone();
            for (Iterator it = nameMap.entrySet().iterator(); it.hasNext(); ) {
                Map.Entry entry = (Map.Entry) it.next();
                Object value = entry.getValue();
                if (value instanceof ArrayList) {
                    entry.setValue(((ArrayList) value).clone());
                }
            }
            shared = false;
        }
    }

    //----------------------------------------------------< inner classes >
    class EntriesIterator implements ListIterator {

        private final OrderedMapIterator mapIter;

        EntriesIterator() {
            mapIter = entries.orderedMapIterator();
        }

        public boolean hasNext() {
            return mapIter.hasNext();
        }

        public Object next() {
            mapIter.next();
            return mapIter.getValue();
        }

        public boolean hasPrevious() {
            return mapIter.hasPrevious();
        }

        public int nextIndex() {
            return entries.indexOf(mapIter.getKey()) + 1;
        }

        public Object previous() {
            mapIter.previous();
            return mapIter.getValue();
        }

        public int previousIndex() {
            return entries.indexOf(mapIter.getKey()) - 1;
        }

        public void add(Object o) {
            throw new UnsupportedOperationException();
        }

        public void remove() {
            throw new UnsupportedOperationException();
        }

        public void set(Object o) {
            throw new UnsupportedOperationException();
        }
    }
}
