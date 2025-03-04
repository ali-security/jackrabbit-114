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
package org.apache.jackrabbit.jcr2spi;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.jackrabbit.jcr2spi.state.ItemState;
import org.apache.jackrabbit.jcr2spi.util.Dumpable;
import org.apache.commons.collections.map.LRUMap;

import javax.jcr.Item;
import javax.jcr.RepositoryException;
import java.util.Map;
import java.util.Iterator;
import java.io.PrintStream;

/**
 * <code>ItemCacheImpl</code>...
 */
public class ItemCacheImpl implements ItemCache, Dumpable {

    private static Logger log = LoggerFactory.getLogger(ItemCacheImpl.class);

    private final Map cache;

    ItemCacheImpl(int maxSize) {
        cache = new LRUMap(maxSize);
    }

    //----------------------------------------------------------< ItemCache >---
    /**
     * @see ItemCache#getItem(ItemState)
     */
    public Item getItem(ItemState state) {
        return (Item) cache.get(state);
    }

    /**
     * @see ItemCache#clear()
     */
    public void clear() {
        cache.clear();
    }

    //----------------------------------------------< ItemLifeCycleListener >---
    /**
     * @see ItemLifeCycleListener#itemCreated(Item)
     */
    public void itemCreated(Item item) {
        if (!(item instanceof ItemImpl)) {
            String msg = "Incompatible Item object: " + ItemImpl.class.getName() + " expected.";
            throw new IllegalArgumentException(msg);
        }
        if (log.isDebugEnabled()) {
            log.debug("created item " + item);
        }
        // add instance to cache
        cacheItem(((ItemImpl)item).getItemState(), item);
    }

    /**
     * @see ItemLifeCycleListener#itemInvalidated(Item)
     */
    public void itemInvalidated(Item item) {
        if (!(item instanceof ItemImpl)) {
            String msg = "Incompatible Item object: " + ItemImpl.class.getName() + " expected.";
            throw new IllegalArgumentException(msg);
        }
        if (log.isDebugEnabled()) {
            log.debug("invalidated item " + item);
        }
        // remove instance from cache
        evictItem(((ItemImpl)item).getItemState());
    }

    /**
     * @see ItemLifeCycleListener#itemDestroyed(Item)
     */
    public void itemDestroyed(Item item) {
        if (!(item instanceof ItemImpl)) {
            String msg = "Incompatible Item object: " + ItemImpl.class.getName() + " expected.";
            throw new IllegalArgumentException(msg);
        }
        if (log.isDebugEnabled()) {
            log.debug("destroyed item " + item);
        }
        // we're no longer interested in this item
        ((ItemImpl)item).removeLifeCycleListener(this);
        // remove instance from cache
        evictItem(((ItemImpl)item).getItemState());
    }

    //-------------------------------------------------< item cache methods >---
    /**
     * Puts the reference of an item in the cache with
     * the item's path as the key.
     *
     * @param item the item to cache
     */
    private synchronized void cacheItem(ItemState state, Item item) {
        if (cache.containsKey(state)) {
            log.warn("overwriting cached item " + state);
        }
        if (log.isDebugEnabled()) {
            log.debug("caching item " + state);
        }
        cache.put(state, item);
    }

    /**
     * Removes a cache entry for a specific item.
     *
     * @param itemState state of the item to remove from the cache
     */
    private synchronized void evictItem(ItemState itemState) {
        if (log.isDebugEnabled()) {
            log.debug("removing item " + itemState + " from cache");
        }
        cache.remove(itemState);
    }

    //-----------------------------------------------------------< Dumpable >---
    /**
     * @see Dumpable#dump(PrintStream)
     */
    public void dump(PrintStream ps) {
        Iterator iter = cache.keySet().iterator();
        while (iter.hasNext()) {
            ItemState state = (ItemState) iter.next();
            Item item = (Item) cache.get(state);
            if (item.isNode()) {
                ps.print("Node: ");
            } else {
                ps.print("Property: ");
            }
            if (item.isNew()) {
                ps.print("new ");
            } else if (item.isModified()) {
                ps.print("modified ");
            } else {
                ps.print("- ");
            }
            String path;
            try {
                path = item.getPath();
            } catch (RepositoryException e) {
                path = "-";
            }
            ps.println(state + "\t" + path + " (" + item + ")");
        }
    }
}