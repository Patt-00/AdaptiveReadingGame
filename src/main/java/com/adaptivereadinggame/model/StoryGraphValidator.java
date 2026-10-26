package com.adaptivereadinggame.model;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;

/** Validates that each prepared story path is finite and reaches its reading check. */
public final class StoryGraphValidator {
    private StoryGraphValidator() {}

    public static void validate(List<StoryNode> nodes, String firstNodeId) {
        Objects.requireNonNull(nodes, "nodes");
        if (nodes.isEmpty() || firstNodeId == null || firstNodeId.isBlank())
            throw new IllegalArgumentException("A story needs nodes and one start node.");
        Map<String, StoryNode> byId = new LinkedHashMap<>();
        for (StoryNode node : nodes) {
            if (byId.putIfAbsent(node.storyNodeId(), node) != null)
                throw new IllegalArgumentException("Story node IDs must be unique within a chapter.");
        }
        if (!byId.containsKey(firstNodeId)) throw new IllegalArgumentException("The start node must exist.");
        if (nodes.stream().filter(StoryNode::readingCheck).count() != 1)
            throw new IllegalArgumentException("A prepared chapter needs exactly one reading-check node.");

        Map<String, List<String>> destinations = new HashMap<>();
        Map<String, Integer> indegrees = new HashMap<>();
        byId.keySet().forEach(id -> indegrees.put(id, 0));
        for (StoryNode node : nodes) {
            List<String> next = node.storyChoices().isEmpty()
                    ? (node.nextNodeId().isEmpty() ? List.of() : List.of(node.nextNodeId()))
                    : node.storyChoices().stream().map(StoryChoice::nextNodeId).toList();
            for (String target : next) {
                if (!byId.containsKey(target)) throw new IllegalArgumentException("A story path points to a missing node.");
                indegrees.compute(target, (ignored, count) -> count + 1);
            }
            destinations.put(node.storyNodeId(), next);
        }

        Set<String> reachable = new LinkedHashSet<>();
        Queue<String> pending = new ArrayDeque<>();
        pending.add(firstNodeId);
        while (!pending.isEmpty()) {
            String current = pending.remove();
            if (reachable.add(current)) pending.addAll(destinations.get(current));
        }
        if (reachable.size() != byId.size()) throw new IllegalArgumentException("A story contains nodes that cannot be reached from its start.");

        Queue<String> ready = new ArrayDeque<>();
        indegrees.forEach((id, count) -> { if (count == 0) ready.add(id); });
        List<String> topological = new ArrayList<>(byId.size());
        while (!ready.isEmpty()) {
            String current = ready.remove();
            topological.add(current);
            for (String next : destinations.get(current)) {
                int remaining = indegrees.compute(next, (ignored, count) -> count - 1);
                if (remaining == 0) ready.add(next);
            }
        }
        if (topological.size() != byId.size()) throw new IllegalArgumentException("Story paths must not contain loops.");

        Map<String, Boolean> reachesCheck = new HashMap<>();
        for (int index = topological.size() - 1; index >= 0; index--) {
            String id = topological.get(index);
            StoryNode node = byId.get(id);
            if (node.readingCheck()) {
                reachesCheck.put(id, true);
                continue;
            }
            List<String> next = destinations.get(id);
            if (next.isEmpty() || next.stream().anyMatch(destination -> !reachesCheck.getOrDefault(destination, false)))
                throw new IllegalArgumentException("Every story choice must lead to the chapter reading check.");
            reachesCheck.put(id, true);
        }
    }

    /** Validate a version-2 episode whose checkpoint outcomes return to the story graph. */
    public static void validateAdaptive(List<StoryNode> nodes, String firstNodeId,
                                        List<StoryCheckpoint> checkpoints) {
        Objects.requireNonNull(nodes, "nodes");
        Objects.requireNonNull(checkpoints, "checkpoints");
        if (nodes.isEmpty() || firstNodeId == null || firstNodeId.isBlank() || checkpoints.isEmpty())
            throw new IllegalArgumentException("An adaptive story needs nodes, a start node, and checkpoints.");

        Map<String, StoryNode> byId = new LinkedHashMap<>();
        for (StoryNode node : nodes) {
            if (byId.putIfAbsent(node.storyNodeId(), node) != null)
                throw new IllegalArgumentException("Story node IDs must be unique within a chapter.");
        }
        if (!byId.containsKey(firstNodeId)) throw new IllegalArgumentException("The start node must exist.");

        Map<String, StoryCheckpoint> checkpointsById = new LinkedHashMap<>();
        Map<String, StoryCheckpoint> checkpointsByNode = new LinkedHashMap<>();
        for (StoryCheckpoint checkpoint : checkpoints) {
            if (checkpointsById.putIfAbsent(checkpoint.id(), checkpoint) != null
                    || checkpointsByNode.putIfAbsent(checkpoint.nodeId(), checkpoint) != null)
                throw new IllegalArgumentException("Checkpoint IDs and nodes must be unique.");
            StoryNode node = byId.get(checkpoint.nodeId());
            if (node == null || !node.readingCheck() || !checkpoint.id().equals(node.checkpointId()))
                throw new IllegalArgumentException("Each checkpoint must match one reading-check node.");
        }
        Set<String> markedCheckpoints = nodes.stream().filter(StoryNode::readingCheck)
                .map(StoryNode::checkpointId).collect(java.util.stream.Collectors.toSet());
        if (markedCheckpoints.size() != checkpoints.size() || !markedCheckpoints.equals(checkpointsById.keySet()))
            throw new IllegalArgumentException("Each reading-check node must have exactly one checkpoint definition.");

        Map<String, List<String>> destinations = new LinkedHashMap<>();
        Map<String, Integer> indegrees = new HashMap<>();
        byId.keySet().forEach(id -> indegrees.put(id, 0));
        for (StoryNode node : nodes) {
            List<String> next;
            if (node.storyComplete()) {
                next = List.of();
            } else if (node.readingCheck()) {
                StoryCheckpoint checkpoint = checkpointsById.get(node.checkpointId());
                if (checkpoint == null) throw new IllegalArgumentException("A reading-check node has no checkpoint.");
                next = List.of(checkpoint.correctNextNodeId(), checkpoint.incorrectNextNodeId());
            } else if (!node.storyChoices().isEmpty()) {
                next = node.storyChoices().stream().map(StoryChoice::nextNodeId).toList();
            } else if (!node.nextNodeId().isEmpty()) {
                next = List.of(node.nextNodeId());
            } else {
                throw new IllegalArgumentException("A story path must continue or end at a draft-completion node.");
            }
            for (String target : next) {
                if (!byId.containsKey(target)) throw new IllegalArgumentException("A story path points to a missing node.");
                indegrees.compute(target, (ignored, count) -> count + 1);
            }
            destinations.put(node.storyNodeId(), next);
        }

        Set<String> reachable = new LinkedHashSet<>();
        Queue<String> pending = new ArrayDeque<>();
        pending.add(firstNodeId);
        while (!pending.isEmpty()) {
            String current = pending.remove();
            if (reachable.add(current)) pending.addAll(destinations.get(current));
        }
        if (reachable.size() != byId.size())
            throw new IllegalArgumentException("Every adaptive story node must be reachable from its start.");

        Queue<String> ready = new ArrayDeque<>();
        indegrees.forEach((id, count) -> { if (count == 0) ready.add(id); });
        List<String> topological = new ArrayList<>(byId.size());
        while (!ready.isEmpty()) {
            String current = ready.remove();
            topological.add(current);
            for (String next : destinations.get(current)) {
                int remaining = indegrees.compute(next, (ignored, count) -> count - 1);
                if (remaining == 0) ready.add(next);
            }
        }
        if (topological.size() != byId.size()) throw new IllegalArgumentException("Story paths must not contain loops.");

        Map<String, Boolean> reachesCompletion = new HashMap<>();
        boolean hasCheckpoint = false;
        for (int index = topological.size() - 1; index >= 0; index--) {
            String id = topological.get(index);
            StoryNode node = byId.get(id);
            if (node.storyComplete()) {
                reachesCompletion.put(id, true);
                continue;
            }
            if (node.readingCheck()) hasCheckpoint = true;
            List<String> next = destinations.get(id);
            if (next.isEmpty() || next.stream().anyMatch(destination -> !reachesCompletion.getOrDefault(destination, false)))
                throw new IllegalArgumentException("Every checkpoint outcome and story choice must reach draft completion.");
            reachesCompletion.put(id, true);
        }
        if (!hasCheckpoint || nodes.stream().noneMatch(StoryNode::storyComplete))
            throw new IllegalArgumentException("An adaptive story needs a checkpoint and a draft-completion node.");
    }
}
