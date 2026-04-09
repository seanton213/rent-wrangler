package com.rentwrangler.strategy;

import com.rentwrangler.domain.enums.MaintenanceCategory;
import com.rentwrangler.exception.UnsupportedMaintenanceCategoryException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Factory that routes a {@link MaintenanceCategory} to the correct
 * {@link MaintenanceStrategy} implementation.
 *
 * <p>Spring injects all {@link MaintenanceStrategy} beans into the constructor,
 * so adding a new category only requires implementing the interface and annotating
 * the class with {@code @Component} — no changes to this factory.
 */
@Component
public class MaintenanceStrategyFactory {

    private final Map<MaintenanceCategory, MaintenanceStrategy> strategies;

    public MaintenanceStrategyFactory(List<MaintenanceStrategy> strategyList) {
        this.strategies = strategyList.stream()
                .collect(Collectors.toUnmodifiableMap(
                        MaintenanceStrategy::getCategory,
                        Function.identity()));
    }

    /**
     * Returns the strategy for the given category.
     *
     * @throws UnsupportedMaintenanceCategoryException if no strategy is registered
     */
    public MaintenanceStrategy getStrategy(MaintenanceCategory category) {
        MaintenanceStrategy strategy = strategies.get(category);
        if (strategy == null) {
            throw new UnsupportedMaintenanceCategoryException(category);
        }
        return strategy;
    }
}
