package com.example.inventory.workflow.domain.definition;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.example.inventory.workflow.domain.model.DefinitionKey;

/** Spring 起動時に検出した {@link WorkflowDefinition} 群を {@link DefinitionKey} で索引する。 */
@Component
public class WorkflowDefinitionRegistry {

    private final Map<DefinitionKey, WorkflowDefinition> byKey;

    public WorkflowDefinitionRegistry(List<WorkflowDefinition> definitions) {
        this.byKey =
                definitions.stream()
                        .collect(Collectors.toUnmodifiableMap(WorkflowDefinition::key, d -> d));
    }

    public Optional<WorkflowDefinition> find(DefinitionKey key) {
        return Optional.ofNullable(byKey.get(key));
    }
}
