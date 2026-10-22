package com.routedispatch.plan;

import com.routedispatch.optimization.RouteOptimizer;
import com.routedispatch.optimization.RoutingProblemFactory;
import com.routedispatch.routing.TravelMatrixService;
import java.time.Clock;
import java.util.concurrent.Executor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration(proxyBeanMethods = false)
class PlanningConfiguration {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    RouteOptimizer routeOptimizer(SolverProperties properties) {
        return new RouteOptimizer(properties.initial(), properties.replan());
    }

    @Bean
    RoutingProblemFactory routingProblemFactory(TravelMatrixService matrixService) {
        return new RoutingProblemFactory(matrixService);
    }

    @Bean
    CsvOrderParser csvOrderParser(ServiceArea serviceArea) {
        return new CsvOrderParser(serviceArea);
    }

    @Bean
    ReplanCoordinator replanCoordinator(PlanningEngine engine, @Qualifier("planningExecutor") Executor planningExecutor) {
        return new ReplanCoordinator(engine::run, planningExecutor);
    }

    /** Solver runs are CPU-bound; two concurrent plans per instance keep the API responsive. */
    @Bean(name = "planningExecutor", destroyMethod = "shutdown")
    Executor planningExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("planning-");
        executor.setWaitForTasksToCompleteOnShutdown(false);
        executor.initialize();
        return executor;
    }
}
