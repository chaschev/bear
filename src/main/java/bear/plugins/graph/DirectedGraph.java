/*
 * Copyright (C) 2013 Andrey Chaschev.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package bear.plugins.graph;

/*****************************************************************************
 * File: DirectedGraph.java
 * Author: Keith Schwarz (htiek@cs.stanford.edu)
 *
 * A class representing a directed graph.  Internally, the class is represented
 * by an adjacency list.
 */

import com.google.common.collect.Lists;

import java.util.*; // For HashMap, HashSet

public final class DirectedGraph<T> implements Iterable<T> {
    /* A map from nodes in the graph to sets of outgoing edges.  Each
     * set of edges is represented by a map from edges to doubles.
     */
    private final Map<T, Set<T>> graph = new HashMap<T, Set<T>>();

    /**
     * Adds a new node to the graph.  If the node already exists, this
     * function is a no-op.
     *
     * @param node The node to add.
     * @return Whether or not the node was added.
     */
    public boolean addNode(T node) {
        /* If the node already exists, don't do anything. */
        if (graph.containsKey(node))
            return false;

        /* Otherwise, add the node with an empty set of outgoing edges. */
        graph.put(node, new LinkedHashSet<T>());
        return true;
    }

    /**
     * Given a start node, and a destination, adds an arc from the start node
     * to the destination.  If an arc already exists, this operation is a
     * no-op.  If either endpoint does not exist in the graph, throws a
     * NoSuchElementException.
     *
     * @param start The start node.
     * @param dest  The destination node.
     * @throws NoSuchElementException If either the start or destination nodes
     *                                do not exist.
     */
    public void addEdge(T start, T dest) {
        /* Confirm both endpoints exist. */
        if (!graph.containsKey(start) || !graph.containsKey(dest)) {
            throw new NoSuchElementException("Both nodes must be in the graph.");
        }

        /* Add the edge. */
        graph.get(start).add(dest);
    }

    /**
     * Removes the edge from start to dest from the graph.  If the edge does
     * not exist, this operation is a no-op.  If either endpoint does not
     * exist, this throws a NoSuchElementException.
     *
     * @param start The start node.
     * @param dest  The destination node.
     * @throws NoSuchElementException If either node is not in the graph.
     */
    public void removeEdge(T start, T dest) {
        /* Confirm both endpoints exist. */
        if (!graph.containsKey(start) || !graph.containsKey(dest))
            throw new NoSuchElementException("Both nodes must be in the graph.");

        graph.get(start).remove(dest);
    }

    /**
     * Given two nodes in the graph, returns whether there is an edge from the
     * first node to the second node.  If either node does not exist in the
     * graph, throws a NoSuchElementException.
     *
     * @param start The start node.
     * @param end   The destination node.
     * @return Whether there is an edge from start to end.
     * @throws NoSuchElementException If either endpoint does not exist.
     */
    public boolean edgeExists(T start, T end) {
        /* Confirm both endpoints exist. */
        if (!graph.containsKey(start) || !graph.containsKey(end))
            throw new NoSuchElementException("Both nodes must be in the graph.");

        return graph.get(start).contains(end);
    }

    /**
     * Given a node in the graph, returns an immutable view of the edges
     * leaving that node as a set of endpoints.
     *
     * @param node The node whose edges should be queried.
     * @return An immutable view of the edges leaving that node.
     * @throws NoSuchElementException If the node does not exist.
     */
    public Set<T> edgesFrom(T node) {
        /* Check that the node exists. */
        Set<T> arcs = graph.get(node);
        if (arcs == null){
            return Collections.emptySet();
        }

        return Collections.unmodifiableSet(arcs);
    }

    /**
     * Returns an iterator that can traverse the nodes in the graph.
     *
     * @return An iterator that traverses the nodes in the graph.
     */
    public Iterator<T> iterator() {
        return graph.keySet().iterator();
    }

    /**
     * Returns the number of nodes in the graph.
     *
     * @return The number of nodes in the graph.
     */
    public int size() {
        return graph.size();
    }

    /**
     * Returns whether the graph is empty.
     *
     * @return Whether the graph is empty.
     */
    public boolean isEmpty() {
        return graph.isEmpty();
    }

    public static class CycleResult extends RuntimeException{
        List<Object> cycle;

        public CycleResult(List<Object> cycle) {
            super("graph contains a cycle with an edge: " + cycle);
            this.cycle = cycle;
        }
    }

    public void findFirstCycle() throws CycleResult {
        Map<T, T> previousNodesTree = new HashMap<T, T>();

        for (Map.Entry<T, Set<T>> entry : graph.entrySet()) {
            firstCycleVisit(entry, previousNodesTree);
        }
    }

    private void firstCycleVisit(Map.Entry<T, Set<T>> entries, Map<T, T> previousNodesTree)
        throws CycleResult {

        T fromNode = entries.getKey();

        for (T node : entries.getValue()) {
            if(previousNodesTree.containsKey(node)){
                //todo implement cycle retrieval
                throw new CycleResult(Lists.<Object>newArrayList(entries.getKey(), node));
            }

            previousNodesTree.put(node, fromNode);
        }
    }
}